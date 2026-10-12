package com.dogpound.railmap.economy;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import java.util.*;

/**
 * One railway company (her ChatGPT list #46): a name and reporting mark, staff with roles and daily wages, two accounts
 * (operating + maintenance budget), a share register, a loan, what it owns (lines, driverless trains, stations) and its
 * books: a ledger and income / costs per kind for this month and last. Money in cents.
 */
public final class Company {
    public enum Role {
        OWNER("Owner", true, true, true), DIRECTOR("Director", true, true, true), DISPATCHER("Dispatcher", false, true, false),
        DRIVER("Driver", false, false, false), CLERK("Clerk", false, false, false);
        public final String label;
        public final boolean finance, operations, staff;
        Role(String label, boolean finance, boolean operations, boolean staff) { this.label = label; this.finance = finance; this.operations = operations; this.staff = staff; }
    }

    /** what money moved for: income kinds first */
    public enum Kind {
        FARES("Fares", true), FREIGHT("Train freight", true), CONTRACTS("Contract share", true), SUBSIDY("PSO subsidy", true),
        HANDLING("Terminal fees", true), TRADING("Trading", true), DEPOSIT("Owner deposits", true), LOAN("Loans", true),
        WAGES("Wages", false), MAINT("Maintenance", false), INTEREST("Interest", false), REFUNDS("Delay refunds", false),
        CLEANING("Cleaning", false), DIVIDEND("Dividends", false), WITHDRAW("Withdrawals", false), REPAY("Loan repayments", false),
        // her list #112-114 (admin/Commercial): leases, track access charges, SLA performance payments - appended, old saves keep their indexes
        LEASE_IN("Lease income", true), ACCESS_IN("Track access income", true), SLA_IN("SLA compensation", true),
        LEASE_OUT("Lease payments", false), ACCESS_OUT("Track access charges", false), SLA_OUT("SLA penalties", false),
        // world + business layer (biz/, her "next 48" list #11-38) - appended, old saves keep their indexes
        CLAIMS("Insurance claims", true), BOND_ISSUE("Bonds + share issues", true), ASSET_SALE("Asset sales", true),
        PREMIUM("Insurance premiums", false), FINES("Regulator fines", false), SLOTS("Capacity slots", false), COUPON("Bond coupons", false),
        RESEARCH("Research", false), MITIGATION("Environment works", false), WORKS("Construction", false), ACQUIRE("Acquisitions", false), LIABILITY("Liability payouts", false);
        public final String label;
        public final boolean income;
        Kind(String label, boolean income) { this.label = label; this.income = income; }
    }

    public static final class Member {
        public String name = "";
        public Role role = Role.CLERK;
        public long wage;            // cents per in-game day
        public long owed;            // wages not yet paid out (offline, or the company was broke)
        public long paid;            // all wages ever paid
    }

    public static final class Entry {
        public long tick, amount;
        public Kind kind;
        public String memo;
        public boolean maint;        // the maintenance account (else operating)
    }

    public int id;
    public String name = "", mark = "";
    public int colour = 0x8E24AA;
    public UUID owner;
    public long founded;
    public final Map<UUID, Member> members = new LinkedHashMap<>();
    public final Map<UUID, Integer> shares = new LinkedHashMap<>();   // of 1000 (= 100.0 %)
    public long operating, maintenance, loan;
    public int loanRatePct = 2;                                        // per in-game week
    public int reputation = 60;                                        // 0..100
    public int contractSharePct = 20;
    public final Set<String> lines = new LinkedHashSet<>();
    public final Set<UUID> trains = new LinkedHashSet<>();
    public final Set<Long> stations = new LinkedHashSet<>();
    public final Set<String> pso = new LinkedHashSet<>();             // lines run under a public-service contract
    public final List<UUID> applicants = new ArrayList<>();
    public final List<Entry> ledger = new ArrayList<>();
    public long[] month = new long[Kind.values().length], lastMonth = new long[Kind.values().length];
    public int monthNo;
    public final Map<UUID, Long> seenEarned = new HashMap<>();

    public Member member(UUID u) { return u == null ? null : members.get(u); }
    public boolean can(UUID u, java.util.function.Predicate<Role> what) { Member m = member(u); return m != null && what.test(m.role); }

    public long profit(long[] m) {
        long s = 0;
        Kind[] k = Kind.values();
        for (int i = 0; i < k.length; i++) {
            if (k[i] == Kind.DEPOSIT || k[i] == Kind.LOAN || k[i] == Kind.WITHDRAW || k[i] == Kind.DIVIDEND || k[i] == Kind.REPAY
                    || k[i] == Kind.BOND_ISSUE || k[i] == Kind.ASSET_SALE || k[i] == Kind.ACQUIRE) continue;   // capital, not trading
            s += k[i].income ? m[i] : -m[i];
        }
        return s;
    }

    public long dailyWages() { long s = 0; for (Member m : members.values()) s += m.wage; return s; }
    public long owedWages() { long s = 0; for (Member m : members.values()) s += m.owed; return s; }

    /** book value: rolling stock, stations and cash, less the loan */
    public long assets(int cars) { return trains.size() * 2_000_000L + cars * 400_000L + stations.size() * 1_000_000L + operating + maintenance; }
    public long sharePrice(int cars) { return Math.max(0, (assets(cars) - loan) / 1000); }

    void book(long tick, Kind k, long amount, String memo, boolean maint) {
        Entry e = new Entry();
        e.tick = tick; e.kind = k; e.amount = amount; e.memo = memo; e.maint = maint;
        ledger.add(e);
        while (ledger.size() > 120) ledger.remove(0);
        month[k.ordinal()] += Math.abs(amount);
    }

    // ---- save ---------------------------------------------------------------------------------------------------
    public NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger("id", id); t.setString("name", name); t.setString("mark", mark); t.setInteger("col", colour);
        if (owner != null) t.setUniqueId("owner", owner);
        t.setLong("founded", founded); t.setLong("op", operating); t.setLong("mt", maintenance); t.setLong("loan", loan);
        t.setInteger("rate", loanRatePct); t.setInteger("rep", reputation); t.setInteger("cut", contractSharePct); t.setInteger("monthNo", monthNo);
        NBTTagList ms = new NBTTagList();
        for (Map.Entry<UUID, Member> e : members.entrySet()) {
            NBTTagCompound c = new NBTTagCompound();
            c.setUniqueId("u", e.getKey()); c.setString("n", e.getValue().name); c.setString("r", e.getValue().role.name());
            c.setLong("w", e.getValue().wage); c.setLong("o", e.getValue().owed); c.setLong("p", e.getValue().paid);
            ms.appendTag(c);
        }
        t.setTag("members", ms);
        NBTTagCompound sh = new NBTTagCompound();
        for (Map.Entry<UUID, Integer> e : shares.entrySet()) sh.setInteger(e.getKey().toString(), e.getValue());
        t.setTag("shares", sh);
        t.setTag("lines", strings(lines)); t.setTag("pso", strings(pso));
        NBTTagList tr = new NBTTagList();
        for (UUID u : trains) tr.appendTag(new NBTTagString(u.toString()));
        t.setTag("trains", tr);
        t.setTag("stations", longList(stations));
        NBTTagList ap = new NBTTagList();
        for (UUID u : applicants) ap.appendTag(new NBTTagString(u.toString()));
        t.setTag("apply", ap);
        NBTTagList lg = new NBTTagList();
        for (Entry e : ledger) {
            NBTTagCompound c = new NBTTagCompound();
            c.setLong("t", e.tick); c.setLong("a", e.amount); c.setString("k", e.kind.name()); c.setString("m", e.memo); c.setBoolean("mt", e.maint);
            lg.appendTag(c);
        }
        t.setTag("ledger", lg);
        t.setTag("month", longList(toList(month)));
        t.setTag("last", longList(toList(lastMonth)));
        NBTTagCompound se = new NBTTagCompound();
        for (Map.Entry<UUID, Long> e : seenEarned.entrySet()) se.setLong(e.getKey().toString(), e.getValue());
        t.setTag("seen", se);
        return t;
    }

    private static NBTTagList strings(Collection<String> c) { NBTTagList l = new NBTTagList(); for (String s : c) l.appendTag(new NBTTagString(s)); return l; }

    public static Company read(NBTTagCompound t) {
        Company c = new Company();
        c.id = t.getInteger("id"); c.name = t.getString("name"); c.mark = t.getString("mark"); c.colour = t.getInteger("col");
        if (t.hasUniqueId("owner")) c.owner = t.getUniqueId("owner");
        c.founded = t.getLong("founded"); c.operating = t.getLong("op"); c.maintenance = t.getLong("mt"); c.loan = t.getLong("loan");
        c.loanRatePct = t.getInteger("rate"); c.reputation = t.getInteger("rep"); c.contractSharePct = t.getInteger("cut"); c.monthNo = t.getInteger("monthNo");
        NBTTagList ms = t.getTagList("members", 10);
        for (int i = 0; i < ms.tagCount(); i++) {
            NBTTagCompound x = ms.getCompoundTagAt(i);
            Member m = new Member();
            m.name = x.getString("n");
            try { m.role = Role.valueOf(x.getString("r")); } catch (IllegalArgumentException e) { m.role = Role.CLERK; }
            m.wage = x.getLong("w"); m.owed = x.getLong("o"); m.paid = x.getLong("p");
            c.members.put(x.getUniqueId("u"), m);
        }
        NBTTagCompound sh = t.getCompoundTag("shares");
        for (String k : sh.getKeySet()) try { c.shares.put(UUID.fromString(k), sh.getInteger(k)); } catch (IllegalArgumentException ignored) { }
        NBTTagList l = t.getTagList("lines", 8);
        for (int i = 0; i < l.tagCount(); i++) c.lines.add(l.getStringTagAt(i));
        l = t.getTagList("pso", 8);
        for (int i = 0; i < l.tagCount(); i++) c.pso.add(l.getStringTagAt(i));
        l = t.getTagList("trains", 8);
        for (int i = 0; i < l.tagCount(); i++) try { c.trains.add(UUID.fromString(l.getStringTagAt(i))); } catch (IllegalArgumentException ignored) { }
        c.stations.addAll(longs(t.getTagList("stations", 4)));
        l = t.getTagList("apply", 8);
        for (int i = 0; i < l.tagCount(); i++) try { c.applicants.add(UUID.fromString(l.getStringTagAt(i))); } catch (IllegalArgumentException ignored) { }
        NBTTagList lg = t.getTagList("ledger", 10);
        for (int i = 0; i < lg.tagCount(); i++) {
            NBTTagCompound x = lg.getCompoundTagAt(i);
            Entry e = new Entry();
            e.tick = x.getLong("t"); e.amount = x.getLong("a"); e.memo = x.getString("m"); e.maint = x.getBoolean("mt");
            try { e.kind = Kind.valueOf(x.getString("k")); } catch (IllegalArgumentException ex) { e.kind = Kind.DEPOSIT; }
            c.ledger.add(e);
        }
        copy(longs(t.getTagList("month", 4)), c.month);
        copy(longs(t.getTagList("last", 4)), c.lastMonth);
        NBTTagCompound se = t.getCompoundTag("seen");
        for (String k : se.getKeySet()) try { c.seenEarned.put(UUID.fromString(k), se.getLong(k)); } catch (IllegalArgumentException ignored) { }
        return c;
    }

    private static void copy(List<Long> from, long[] to) { for (int i = 0; i < Math.min(from.size(), to.length); i++) to[i] = from.get(i); }
    private static List<Long> toList(long[] a) { List<Long> l = new ArrayList<>(); for (long v : a) l.add(v); return l; }

    static NBTTagList longList(Collection<Long> c) { NBTTagList l = new NBTTagList(); for (long v : c) l.appendTag(new net.minecraft.nbt.NBTTagLong(v)); return l; }

    static List<Long> longs(NBTTagList l) {
        List<Long> out = new ArrayList<>();
        for (int i = 0; i < l.tagCount(); i++) if (l.get(i) instanceof net.minecraft.nbt.NBTTagLong v) out.add(v.getLong());
        return out;
    }
}
