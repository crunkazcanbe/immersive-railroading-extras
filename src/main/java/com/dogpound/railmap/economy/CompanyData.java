package com.dogpound.railmap.economy;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.RailwayData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.*;

/**
 * Every railway company in a world (her ChatGPT list #46). Income arrives on its own: driverless trains of the company's
 * lines earn freight money (credited as they earn it), passengers pay fares (PassengerData), member-hauled freight
 * contracts give the company its cut, terminals charge handling fees. Once an in-game day the company pays its staff,
 * its maintenance bill (per train and station) and the interest on its loan; every 7 days the books close a month.
 */
public final class CompanyData extends WorldSavedData {
    private static final String NAME = "irextras_companies";
    public static final long DAY = 24000;
    public static final int MONTH_DAYS = 7;
    /** daily upkeep: per driverless train, per station */
    public static final long MAINT_TRAIN = 600, MAINT_STATION = 250;

    private final List<Company> companies = new ArrayList<>();
    private int nextId = 1;
    private long lastDay = -1;

    public CompanyData() { super(NAME); }
    public CompanyData(String n) { super(n); }

    public static CompanyData get(World w) {
        MapStorage s = w.getPerWorldStorage();
        CompanyData d = (CompanyData) s.getOrLoadData(CompanyData.class, NAME);
        if (d == null) { d = new CompanyData(); s.setData(NAME, d); }
        return d;
    }

    public List<Company> all() { return companies; }
    public Company byId(int id) { for (Company c : companies) if (c.id == id) return c; return null; }
    public Company of(UUID member) { if (member == null) return null; for (Company c : companies) if (c.members.containsKey(member)) return c; return null; }

    public Company byName(String n) {
        for (Company c : companies) if (c.name.equalsIgnoreCase(n) || c.mark.equalsIgnoreCase(n)) return c;
        try { return byId(Integer.parseInt(n)); } catch (NumberFormatException e) { return null; }
    }

    public Company ownerOfLine(String line) {
        if (line == null || line.isEmpty()) return null;
        for (Company c : companies) if (c.lines.contains(line)) return c;
        return null;
    }

    public Company ownerOfTrain(UUID loco, String line) {
        for (Company c : companies) if (c.trains.contains(loco)) return c;
        return ownerOfLine(line);
    }

    /** the companies running trains through a station (its own owner first): fares there are shared between them */
    public List<Company> operatorsAt(World w, long station) {
        List<Company> out = new ArrayList<>();
        for (Company c : companies) if (c.stations.contains(station)) out.add(c);
        for (RailwayData.Line l : RailwayData.get(w).lines().values())
            if (l.stations.contains(station)) { Company c = ownerOfLine(l.name); if (c != null && !out.contains(c)) out.add(c); }
        return out;
    }

    // ---- money ----------------------------------------------------------------------------------------------------
    public void credit(World w, Company c, Company.Kind k, long cents, String memo) {
        if (c == null || cents <= 0) return;
        c.operating += cents;
        c.book(w.getTotalWorldTime(), k, cents, memo, false);
        markDirty();
    }

    /** take from the operating (or maintenance) account; false if it can't cover it */
    public boolean debit(World w, Company c, Company.Kind k, long cents, String memo, boolean maint) {
        if (c == null || cents <= 0) return cents <= 0;
        if ((maint ? c.maintenance : c.operating) < cents) return false;
        if (maint) c.maintenance -= cents; else c.operating -= cents;
        c.book(w.getTotalWorldTime(), k, -cents, memo, maint);
        markDirty();
        return true;
    }

    /** split fares between the companies running at a station */
    public void creditShared(World w, List<Company> ops, Company.Kind k, long cents, String memo) {
        if (ops.isEmpty() || cents <= 0) return;
        long each = cents / ops.size();
        for (Company c : ops) credit(w, c, k, each, memo);
    }

    /** a member delivered a freight contract: the company keeps its cut; returns what the hauler still gets */
    public long contractPaid(World w, UUID hauler, long paid, int id) {
        Company c = of(hauler);
        if (c == null || c.contractSharePct <= 0) return paid;
        long cut = paid * c.contractSharePct / 100;
        credit(w, c, Company.Kind.CONTRACTS, cut, "Contract #" + id + " (" + c.contractSharePct + "% share)");
        c.reputation = Math.min(100, c.reputation + 1);
        return paid - cut;
    }

    public Company found(World w, EntityPlayer p, String name) {
        Company c = new Company();
        c.id = nextId++;
        c.name = name;
        c.mark = mark(name);
        c.owner = p.getUniqueID();
        c.founded = w.getTotalWorldTime();
        Company.Member m = new Company.Member();
        m.name = p.getName(); m.role = Company.Role.OWNER;
        c.members.put(p.getUniqueID(), m);
        c.shares.put(p.getUniqueID(), 1000);
        c.colour = PALETTE[(c.id - 1) % PALETTE.length];
        companies.add(c);
        markDirty();
        Recorder.event(w, "Railway company founded: " + c.name + " (" + c.mark + ") by " + p.getName());
        return c;
    }

    private static final int[] PALETTE = {0xE53935, 0x1E88E5, 0x43A047, 0xFDD835, 0x8E24AA, 0xFB8C00, 0x00ACC1, 0xD81B60};

    /** reporting mark: initials of the name + "R" (Ashford Valley Railway -> AVR) */
    static String mark(String name) {
        StringBuilder b = new StringBuilder();
        for (String s : name.trim().split("\\s+")) if (!s.isEmpty() && Character.isLetter(s.charAt(0))) b.append(Character.toUpperCase(s.charAt(0)));
        String m = b.length() == 0 ? "PRR" : b.toString();
        if (m.length() == 1) m += "RR";
        return m.length() > 4 ? m.substring(0, 4) : m;
    }

    public void dissolve(World w, Company c) {
        companies.remove(c);
        markDirty();
        Recorder.event(w, "Railway company dissolved: " + c.name);
    }

    // ---- the clock ------------------------------------------------------------------------------------------------
    public void tick(World w) {
        long now = w.getTotalWorldTime();
        RailwayData rd = RailwayData.get(w);
        // driverless train income, as the trains earn it (Autopilot keeps "$" earned per train)
        for (RailwayData.AutoTrain a : rd.trains().values()) {
            Company c = ownerOfTrain(a.loco, a.line);
            if (c == null) continue;
            Long seen = c.seenEarned.get(a.loco);
            if (seen == null || a.earned < seen) { c.seenEarned.put(a.loco, a.earned); markDirty(); continue; }
            long delta = a.earned - seen;
            if (delta > 0) {
                c.seenEarned.put(a.loco, a.earned);
                credit(w, c, Company.Kind.FREIGHT, delta * 100, (a.label.isEmpty() ? "Train" : a.label) + " delivered cargo");
            }
        }
        // wages owed to staff who are online now
        for (Company c : companies)
            for (Map.Entry<UUID, Company.Member> e : c.members.entrySet()) {
                if (e.getValue().owed <= 0) continue;
                EntityPlayerMP p = online(w, e.getKey());
                if (p != null && Coins.pay(p, e.getValue().owed, c.name + " wages")) {
                    p.sendMessage(new TextComponentString("§a💼 " + c.name + " paid your wages: §6" + Coins.fmt(e.getValue().owed)));
                    e.getValue().paid += e.getValue().owed;
                    e.getValue().owed = 0;
                    markDirty();
                }
            }
        long day = now / DAY;
        if (lastDay < 0) { lastDay = day; markDirty(); return; }
        if (day == lastDay) return;
        lastDay = day;
        for (Company c : companies) daily(w, c, rd, day);
        markDirty();
    }

    private void daily(World w, Company c, RailwayData rd, long day) {
        // wages: from the operating account into owed (paid out when they are online)
        for (Company.Member m : c.members.values()) {
            if (m.wage <= 0) continue;
            if (debit(w, c, Company.Kind.WAGES, m.wage, "Wages: " + m.name, false)) m.owed += m.wage;
            else { c.reputation = Math.max(0, c.reputation - 2); alarm(w, c, "WAGES", c.name + ": can't pay " + m.name + "'s wages"); }
        }
        // maintenance: trains on its lines + its own trains, and its stations
        int trains = 0;
        for (RailwayData.AutoTrain a : rd.trains().values()) {
            if (ownerOfTrain(a.loco, a.line) != c) continue;
            trains++;
            PassengerData.LineStat ls = PassengerData.get(w).line(a.line);
            if (ls != null) ls.costDay += MAINT_TRAIN;                           // line-by-line operating costs (her list #50)
        }
        long bill = trains * MAINT_TRAIN + c.stations.size() * MAINT_STATION;
        if (bill > 0 && !debit(w, c, Company.Kind.MAINT, bill, "Daily upkeep: " + trains + " trains, " + c.stations.size() + " stations", true)) {
            long part = c.maintenance;
            if (part > 0) debit(w, c, Company.Kind.MAINT, part, "Upkeep (part paid)", true);
            c.reputation = Math.max(0, c.reputation - 3);
            alarm(w, c, "MAINT", c.name + ": maintenance budget exhausted - " + Coins.fmt(bill - part) + " of upkeep deferred");
        }
        // loan interest (rate is per week)
        if (c.loan > 0) {
            long interest = Math.max(1, c.loan * c.loanRatePct / 100 / MONTH_DAYS);
            if (!debit(w, c, Company.Kind.INTEREST, interest, "Loan interest", false)) { c.loan += interest; alarm(w, c, "LOAN", c.name + ": interest unpaid, added to the loan"); }
        }
        // public-service contracts: the county pays per rider on loss-making lines it wants kept
        PassengerData pd = PassengerData.get(w);
        for (String line : c.pso) {
            PassengerData.LineStat ls = pd.line(line);
            long sub = ls == null ? 0 : Math.min(50_000, Math.round(ls.ridersYesterday * 12));
            if (sub > 0) credit(w, c, Company.Kind.SUBSIDY, sub, "PSO subsidy: " + line + " (" + ls.ridersYesterday + " riders)");
        }
        // reputation follows the satisfaction at its stations and on its lines
        double sat = pd.companySatisfaction(w, c);
        if (sat >= 0) c.reputation = (int) Math.round(c.reputation * 0.7 + sat * 0.3);
        if (day % MONTH_DAYS == 0) closeMonth(w, c);
    }

    private void closeMonth(World w, Company c) {
        c.lastMonth = c.month.clone();
        c.month = new long[Company.Kind.values().length];
        c.monthNo++;
        long pl = c.profit(c.lastMonth);
        Recorder.event(w, c.name + " month " + c.monthNo + " closed: " + (pl >= 0 ? "profit " : "loss ") + Coins.fmt(Math.abs(pl)));
        for (UUID u : c.members.keySet()) {
            EntityPlayerMP p = online(w, u);
            if (p != null) for (String s : report(c, true)) p.sendMessage(new TextComponentString(s));
        }
    }

    /** the monthly report, as chat lines */
    public static List<String> report(Company c, boolean last) {
        long[] m = last ? c.lastMonth : c.month;
        List<String> out = new ArrayList<>();
        out.add("§6📊 " + c.name + " (" + c.mark + ") - " + (last ? "month " + c.monthNo + " report" : "this month so far"));
        for (Company.Kind k : Company.Kind.values()) if (m[k.ordinal()] != 0) out.add("§7  " + (k.income ? "§a+ " : "§c- ") + k.label + ": §f" + Coins.fmt(m[k.ordinal()]));
        long pl = c.profit(m);
        out.add("§7  Operating result: " + (pl >= 0 ? "§a" : "§c") + Coins.fmt(pl) + " §7· accounts " + Coins.fmt(c.operating) + " / maint " + Coins.fmt(c.maintenance)
                + (c.loan > 0 ? " · loan " + Coins.fmt(c.loan) : "") + " · reputation " + c.reputation);
        return out;
    }

    private static void alarm(World w, Company c, String what, String text) {
        AlarmManager.get(w).raise(w, "company:" + c.id + ":" + what, AlarmManager.Cat.GENERAL, AlarmManager.Sev.MINOR, text, null, false);
    }

    public static EntityPlayerMP online(World w, UUID u) {
        return w.getMinecraftServer() == null || u == null ? null : w.getMinecraftServer().getPlayerList().getPlayerByUUID(u);
    }

    // ---- save -----------------------------------------------------------------------------------------------------
    @Override
    public void readFromNBT(NBTTagCompound t) {
        companies.clear();
        NBTTagList l = t.getTagList("companies", 10);
        for (int i = 0; i < l.tagCount(); i++) companies.add(Company.read(l.getCompoundTagAt(i)));
        nextId = Math.max(1, t.getInteger("next"));
        lastDay = t.hasKey("day") ? t.getLong("day") : -1;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        NBTTagList l = new NBTTagList();
        for (Company c : companies) l.appendTag(c.write());
        t.setTag("companies", l);
        t.setInteger("next", nextId);
        t.setLong("day", lastDay);
        return t;
    }
}
