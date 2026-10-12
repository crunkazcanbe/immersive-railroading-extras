package com.dogpound.railmap.biz;

import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.economy.CompanyData;
import com.dogpound.railmap.economy.PassengerData;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import java.util.*;

/**
 * Mergers, acquisitions and franchising (her "next 48" #35). A company's directors OFFER a deal; the other side's
 * directors ACCEPT or REJECT it within two days. On completion everything transfers WITH the deal:
 * <ul>
 *   <li><b>MERGER</b> (share swap): the target's lines, trains, stations, cash, its LOAN (debt), its public-service
 *   contracts, capacity slots, bonds it issued, open corrective actions, insurance claims and research go to the
 *   acquirer; the target's shareholders get acquirer shares in proportion to the two companies' book values; its staff
 *   join; the target is dissolved;</li>
 *   <li><b>ACQUISITION</b> of one asset (a line, a train or a station) for a price: the asset and its obligations (the
 *   line's PSO contract and its capacity slots) move to the buyer;</li>
 *   <li><b>FRANCHISE</b>: the owner lets the franchisee run one of its lines for N weeks, for a fee of X % of the
 *   line's fares, with a punctuality target - missed three days running and the franchise is terminated early; at
 *   expiry the line goes back.</li>
 * </ul>
 */
public final class Deals implements BizData.Part {
    public enum Kind { MERGER, ACQUIRE_LINE, ACQUIRE_TRAIN, ACQUIRE_STATION, FRANCHISE }

    public static final class Deal {
        public int id, from, to;              // from = acquirer / franchisee, to = target / owner
        public Kind kind = Kind.MERGER;
        public String asset = "";             // line name / train UUID / station key
        public long price, offered, until;
        public int feePct = 10, weeks = 2, punctTarget = 80;
        public String state = "OFFERED";      // OFFERED, DONE, REJECTED, EXPIRED, ACTIVE (franchise), ENDED, TERMINATED
        public int missedDays;
        public String result = "";
    }

    public final List<Deal> deals = new ArrayList<>();
    private int nextId = 1;

    @Override public String key() { return "deals"; }

    public Deal deal(int id) { for (Deal d : deals) if (d.id == id) return d; return null; }

    public String offer(World w, Company from, Company to, Kind k, String asset, long price, int feePct, int weeks) {
        if (from == to) return "§cA company can't deal with itself";
        if (Research.locked(w, from, Research.Tech.CORPORATE_LAW)) return Research.why(Research.Tech.CORPORATE_LAW);
        switch (k) {
            case ACQUIRE_LINE: case FRANCHISE: if (!to.lines.contains(asset)) return "§c" + to.mark + " doesn't own the line " + asset; break;
            case ACQUIRE_TRAIN: if (to.trains.stream().noneMatch(u -> u.toString().startsWith(asset))) return "§c" + to.mark + " owns no train " + asset; break;
            case ACQUIRE_STATION: if (to.stations.stream().noneMatch(s -> String.valueOf(s).equals(asset) || stationName(w, s).equalsIgnoreCase(asset))) return "§c" + to.mark + " owns no station " + asset; break;
            default: break;
        }
        Deal d = new Deal();
        d.id = nextId++; d.from = from.id; d.to = to.id; d.kind = k; d.asset = asset; d.price = price; d.feePct = feePct; d.weeks = Math.max(1, weeks);
        d.offered = w.getTotalWorldTime(); d.until = d.offered + 2 * BizData.DAY;
        deals.add(d);
        while (deals.size() > 100) deals.remove(0);
        BizData.get(w).note(w, "Deal D" + d.id + " offered: " + from.mark + " -> " + to.mark + " " + describe(w, d));
        return "§aDeal D" + d.id + " offered to " + to.name + ": " + describe(w, d) + " - their directors ACCEPT or REJECT within two days";
    }

    public static String describe(World w, Deal d) {
        switch (d.kind) {
            case MERGER: return "merger (share swap)";
            case FRANCHISE: return "franchise of " + d.asset + " for " + d.weeks + " weeks at " + d.feePct + "% of fares, " + d.punctTarget + "% punctual";
            default: return d.kind.name().replace('_', ' ').toLowerCase(Locale.ROOT) + " " + d.asset + " for " + Coins.fmt(d.price);
        }
    }

    static String stationName(World w, long k) { return com.dogpound.railmap.server.StationData.get(w).names().getOrDefault(k, String.valueOf(k)); }

    public String reject(World w, Company by, int id) {
        Deal d = deal(id);
        if (d == null || d.to != by.id || !d.state.equals("OFFERED")) return "§7No open offer D" + id + " to " + by.mark;
        d.state = "REJECTED";
        return "§eDeal D" + id + " rejected";
    }

    /** the target accepts: execute */
    public String accept(World w, BizData bd, Company by, int id) {
        Deal d = deal(id);
        if (d == null || d.to != by.id || !d.state.equals("OFFERED")) return "§7No open offer D" + id + " to " + by.mark;
        CompanyData cd = CompanyData.get(w);
        Company a = cd.byId(d.from), t = by;
        if (a == null) { d.state = "EXPIRED"; return "§7The bidder no longer exists"; }
        String r;
        switch (d.kind) {
            case MERGER: r = merge(w, bd, cd, a, t); break;
            case FRANCHISE: {
                t.lines.remove(d.asset); a.lines.add(d.asset);
                d.state = "ACTIVE"; d.until = w.getTotalWorldTime() + d.weeks * 7L * BizData.DAY;
                r = a.mark + " now runs " + d.asset + " under franchise from " + t.mark + " until day " + d.until / BizData.DAY;
                break;
            }
            default: {
                if (!BizData.pay(w, a, Company.Kind.ACQUIRE, d.price, "Bought " + d.asset + " from " + t.mark)) return "§c" + a.name + " can't pay " + Coins.fmt(d.price);
                cd.credit(w, t, Company.Kind.ASSET_SALE, d.price, "Sold " + d.asset + " to " + a.mark);
                if (d.kind == Kind.ACQUIRE_LINE) {
                    t.lines.remove(d.asset); a.lines.add(d.asset);
                    if (t.pso.remove(d.asset)) a.pso.add(d.asset);                          // the line's PSO obligation goes with it
                    bd.pso.transfer(t.id, a.id, d.asset);
                    for (Capacity.Alloc x : bd.capacity.allocs) if (x.company == t.id && x.res.equals("C:" + d.asset)) x.company = a.id;
                } else if (d.kind == Kind.ACQUIRE_TRAIN) {
                    UUID u = null;
                    for (UUID x : t.trains) if (x.toString().startsWith(d.asset)) u = x;
                    if (u != null) { t.trains.remove(u); a.trains.add(u); }
                } else {
                    Long s = null;
                    for (long x : t.stations) if (String.valueOf(x).equals(d.asset) || stationName(w, x).equalsIgnoreCase(d.asset)) s = x;
                    if (s != null) { t.stations.remove(s); a.stations.add(s); }
                }
                r = a.mark + " bought " + d.asset + " from " + t.mark + " for " + Coins.fmt(d.price);
            }
        }
        if (!d.state.equals("ACTIVE")) d.state = "DONE";
        d.result = r;
        cd.markDirty();
        bd.note(w, "Deal D" + d.id + " completed: " + r);
        Recorder.event(w, "Deal D" + d.id + ": " + r);
        bd.mastery.count(w, a, null, "deals", 1);
        return "§a" + r;
    }

    private String merge(World w, BizData bd, CompanyData cd, Company a, Company t) {
        int cars = 0;
        long va = Math.max(1, a.assets(cars) - a.loan), vt = Math.max(1, t.assets(cars) - t.loan);
        double share = vt / (double) (va + vt);
        Map<UUID, Integer> reg = new LinkedHashMap<>();
        for (Map.Entry<UUID, Integer> e : a.shares.entrySet()) reg.merge(e.getKey(), (int) Math.round(e.getValue() * (1 - share)), Integer::sum);
        for (Map.Entry<UUID, Integer> e : t.shares.entrySet()) reg.merge(e.getKey(), (int) Math.round(e.getValue() * share), Integer::sum);
        a.shares.clear(); a.shares.putAll(reg);
        a.lines.addAll(t.lines); a.trains.addAll(t.trains); a.stations.addAll(t.stations); a.pso.addAll(t.pso);
        a.operating += t.operating; a.maintenance += t.maintenance; a.loan += t.loan;                // the debt comes too
        for (Map.Entry<UUID, Company.Member> e : t.members.entrySet())
            if (!a.members.containsKey(e.getKey())) { Company.Member m = e.getValue(); if (m.role == Company.Role.OWNER) m.role = Company.Role.DIRECTOR; a.members.put(e.getKey(), m); }
        a.reputation = (a.reputation + t.reputation) / 2;
        // obligations follow the company
        for (Capacity.Alloc x : bd.capacity.allocs) if (x.company == t.id) x.company = a.id;
        for (Capacity.Bid x : bd.capacity.bids) if (x.company == t.id) x.company = a.id;
        for (Regulator.Ca x : bd.regulator.cas) if (x.company == t.id) x.company = a.id;
        for (Insurance.Claim x : bd.insurance.claims) if (x.company == t.id) x.company = a.id;
        bd.securities.transfer(t.id, a.id);
        bd.pso.transferAll(t.id, a.id);
        Research.Prog ra = bd.research.prog(a.id), rt = bd.research.prog(t.id);
        for (int i = 0; i < ra.done.length; i++) ra.done[i] |= rt.done[i];
        ra.grandfathered |= rt.grandfathered;
        cd.dissolve(w, t);
        return a.mark + " merged with " + t.mark + " (" + Math.round(share * 100) + "% of the new company to " + t.mark + "'s shareholders, debt " + Coins.fmt(t.loan) + " assumed)";
    }

    @Override
    public void daily(World w, BizData bd, long day) {
        CompanyData cd = CompanyData.get(w);
        long now = w.getTotalWorldTime();
        for (Deal d : deals) {
            if (d.state.equals("OFFERED") && now > d.until) d.state = "EXPIRED";
            if (!d.state.equals("ACTIVE")) continue;
            Company f = cd.byId(d.from), o = cd.byId(d.to);
            if (f == null || o == null) { d.state = "ENDED"; continue; }
            PassengerData.LineStat ls = PassengerData.get(w).line(d.asset);
            long fee = ls == null ? 0 : ls.revenueYesterday * d.feePct / 100;
            if (fee > 0 && BizData.pay(w, f, Company.Kind.ACCESS_OUT, fee, "Franchise fee " + d.asset + " (" + d.feePct + "%)")) cd.credit(w, o, Company.Kind.ACCESS_IN, fee, "Franchise fee from " + f.mark);
            if (ls != null && ls.punct < d.punctTarget) d.missedDays++; else d.missedDays = 0;
            boolean end = now > d.until, term = d.missedDays >= 3;
            if (end || term) {
                f.lines.remove(d.asset); o.lines.add(d.asset);
                d.state = term ? "TERMINATED" : "ENDED";
                bd.note(w, "Franchise D" + d.id + " " + d.asset + (term ? " TERMINATED - punctuality target missed three days running" : " ended") + "; the line returns to " + o.mark);
            }
        }
    }

    // ---- save -------------------------------------------------------------------------------------------------------
    @Override
    public void read(NBTTagCompound t) {
        deals.clear();
        NBTTagList l = t.getTagList("d", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            Deal d = new Deal();
            d.id = c.getInteger("id"); d.from = c.getInteger("f"); d.to = c.getInteger("t");
            try { d.kind = Kind.valueOf(c.getString("k")); } catch (IllegalArgumentException e) { continue; }
            d.asset = c.getString("a"); d.price = c.getLong("p"); d.offered = c.getLong("o"); d.until = c.getLong("u");
            d.feePct = c.getInteger("fee"); d.weeks = c.getInteger("wk"); d.punctTarget = c.getInteger("pt"); d.state = c.getString("s"); d.missedDays = c.getInteger("md"); d.result = c.getString("r");
            deals.add(d);
        }
        nextId = Math.max(1, t.getInteger("next"));
    }

    @Override
    public NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        NBTTagList l = new NBTTagList();
        for (Deal d : deals) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("id", d.id); c.setInteger("f", d.from); c.setInteger("t", d.to); c.setString("k", d.kind.name()); c.setString("a", d.asset);
            c.setLong("p", d.price); c.setLong("o", d.offered); c.setLong("u", d.until); c.setInteger("fee", d.feePct); c.setInteger("wk", d.weeks);
            c.setInteger("pt", d.punctTarget); c.setString("s", d.state); c.setInteger("md", d.missedDays); c.setString("r", d.result);
            l.appendTag(c);
        }
        t.setTag("d", l);
        t.setInteger("next", nextId);
        return t;
    }
}
