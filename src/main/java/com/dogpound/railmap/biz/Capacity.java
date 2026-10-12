package com.dogpound.railmap.biz;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.ConsoleControl;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.economy.CompanyData;
import com.dogpound.railmap.economy.PassengerData;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.world.World;

import java.util.*;

/**
 * Capacity auctions (her "next 48" #12). The scarce things on a railway - a busy CORRIDOR (a line), a TERMINAL (a
 * station's platforms) or a SIDING - are sold as train paths in six daily windows (night, early, AM peak, midday, PM
 * peak, evening). Companies bid cents per path per day, for a RECURRING slot (one 7-day timetable period, renewed by
 * bidding again) or a SEASONAL one (only while that season of the year runs). Every day the sealed bids close: per
 * window the free paths go to the highest bids; ties go to a public-service operator, then the incumbent, then the
 * better reputation, then the earliest bid - each decision and why is recorded. Winners pay daily to the corridor's
 * owner (or the regulator, where nobody owns it). Driverless trains are watched: running in a window where other
 * companies hold every path is UNAUTHORISED PATH (alarm, and held at the next stop only if enforceSlots is on).
 */
public final class Capacity implements BizData.Part {
    public static final String[] WINDOWS = {"NIGHT", "EARLY", "AM PEAK", "MIDDAY", "PM PEAK", "EVENING"};
    public static final String[] SEASONS = {"SPRING", "SUMMER", "AUTUMN", "WINTER"};
    public enum Kind { CORRIDOR, TERMINAL, SIDING }

    public static final class Resource {
        public Kind kind = Kind.CORRIDOR;
        public String name = "";
        public int paths = BizConfig.defaultPaths;
        public long reserve = 200;          // cents per path per day, the lowest bid accepted
        public String key() { return kind.name().charAt(0) + ":" + name; }
    }

    public static final class Bid {
        public int id, company, window, paths, season = -1;   // season -1 = recurring
        public long cents, placed;
        public String res = "", state = "OPEN";              // OPEN, WON, LOST, PART, WITHDRAWN
    }

    public static final class Alloc {
        public int id, company, window, paths, season = -1;
        public long cents, from, until;                      // until: tick it lapses (recurring) - seasonal: while the season runs
        public String res = "";
        public long paidTotal;
        public int used, unauthorisedSeen;
    }

    public final Map<String, Resource> resources = new LinkedHashMap<>();
    public final List<Bid> bids = new ArrayList<>();
    public final List<Alloc> allocs = new ArrayList<>();
    public final LinkedList<String> decisions = new LinkedList<>();
    private int nextId = 1;
    private final transient Map<UUID, Boolean> held = new HashMap<>();

    @Override public String key() { return "capacity"; }

    public static int window(int hour) {
        if (hour >= 22 || hour < 5) return 0;
        if (hour < 7) return 1;
        if (hour < 9) return 2;
        if (hour < 16) return 3;
        if (hour < 19) return 4;
        return 5;
    }

    public static int season(World w) { return (int) ((w.getTotalWorldTime() / BizData.DAY / 7) % 4); }

    /** the resource for a key "C:Red Line", created (with default paths) for any line / station that exists */
    public Resource resource(World w, String key) {
        Resource r = resources.get(key);
        if (r != null) return r;
        if (key.length() < 3 || key.charAt(1) != ':') return null;
        Kind k = key.charAt(0) == 'C' ? Kind.CORRIDOR : key.charAt(0) == 'T' ? Kind.TERMINAL : key.charAt(0) == 'S' ? Kind.SIDING : null;
        if (k == null) return null;
        String name = key.substring(2);
        if (k == Kind.CORRIDOR && RailwayData.get(w).line(name) == null) return null;
        if (k == Kind.TERMINAL && !com.dogpound.railmap.server.StationData.get(w).names().containsValue(name)) return null;
        r = new Resource();
        r.kind = k; r.name = name;
        resources.put(r.key(), r);
        return r;
    }

    public int pathsHeld(String res, int window, int season, int exceptCompany) {
        int n = 0;
        for (Alloc a : allocs) if (a.res.equals(res) && a.window == window && a.company != exceptCompany && (a.season < 0 || a.season == season)) n += a.paths;
        return n;
    }

    public Alloc holding(int company, String res, int window, int season) {
        for (Alloc a : allocs) if (a.company == company && a.res.equals(res) && a.window == window && (a.season < 0 || a.season == season)) return a;
        return null;
    }

    public String bid(World w, Company c, String res, int window, int paths, long cents, int season) {
        Resource r = resource(w, res);
        if (r == null) return "§cNo such corridor / terminal / siding: " + res + " (C:<line>, T:<station>, S:<siding>)";
        if (window < 0 || window >= WINDOWS.length) return "§cWindow 0-5: " + String.join(", ", WINDOWS);
        if (paths < 1 || paths > r.paths) return "§cPaths 1-" + r.paths + " on " + r.name;
        if (cents < r.reserve) return "§cThe reserve price on " + r.name + " is " + Coins.fmt(r.reserve) + " a path a day";
        if (Research.locked(w, c, Research.Tech.CAPACITY_PLANNING)) return Research.why(Research.Tech.CAPACITY_PLANNING);
        Bid b = new Bid();
        b.id = nextId++; b.company = c.id; b.res = r.key(); b.window = window; b.paths = paths; b.cents = cents; b.season = season; b.placed = w.getTotalWorldTime();
        bids.add(b);
        return "§aBid #" + b.id + ": " + c.mark + " " + paths + " path(s) " + WINDOWS[window] + " on " + r.name + " at " + Coins.fmt(cents) + "/path/day "
                + (season < 0 ? "(recurring)" : "(" + SEASONS[season] + " season)") + " - sealed until the auction closes at day end";
    }

    public String withdraw(Company c, int id) {
        for (Bid b : bids) if (b.id == id && b.company == c.id && b.state.equals("OPEN")) { b.state = "WITHDRAWN"; return "§eBid #" + id + " withdrawn"; }
        for (Iterator<Alloc> it = allocs.iterator(); it.hasNext(); ) {
            Alloc a = it.next();
            if (a.id == id && a.company == c.id) { it.remove(); return "§eSlot #" + id + " handed back"; }
        }
        return "§7No open bid or slot #" + id + " of " + c.mark;
    }

    /** the sealed-bid round: per resource and window, free paths to the best bids */
    public int close(World w, BizData d) {
        long now = w.getTotalWorldTime();
        CompanyData cd = CompanyData.get(w);
        allocs.removeIf(a -> a.season < 0 && a.until > 0 && a.until <= now);
        Map<String, List<Bid>> groups = new LinkedHashMap<>();
        for (Bid b : bids) if (b.state.equals("OPEN")) groups.computeIfAbsent(b.res + "#" + b.window + "#" + b.season, k -> new ArrayList<>()).add(b);
        int decided = 0;
        for (List<Bid> g : groups.values()) {
            Bid f = g.get(0);
            Resource r = resources.get(f.res);
            if (r == null) continue;
            int free = r.paths - pathsHeld(f.res, f.window, f.season < 0 ? season(w) : f.season, -1);
            g.sort((a, b) -> {
                if (a.cents != b.cents) return Long.compare(b.cents, a.cents);
                int pa = d.pso.priority(w, cd.byId(a.company), r), pb = d.pso.priority(w, cd.byId(b.company), r);
                if (pa != pb) return Integer.compare(pb, pa);
                boolean ia = holding(a.company, a.res, a.window, a.season) != null, ib = holding(b.company, b.res, b.window, b.season) != null;
                if (ia != ib) return ia ? -1 : 1;
                Company ca = cd.byId(a.company), cb = cd.byId(b.company);
                int ra = ca == null ? 0 : ca.reputation, rb = cb == null ? 0 : cb.reputation;
                if (ra != rb) return Integer.compare(rb, ra);
                return Long.compare(a.placed, b.placed);
            });
            Bid prev = null;
            for (Bid b : g) {
                Company c = cd.byId(b.company);
                String tie = prev != null && prev.cents == b.cents ? " (tie broken: " + tieWhy(w, d, cd, prev, b, r) + ")" : "";
                if (c == null) { b.state = "LOST"; continue; }
                if (free <= 0) { b.state = "LOST"; decision(w, d, "Bid #" + b.id + " " + c.mark + " LOST " + r.name + " " + WINDOWS[b.window] + " - no paths left" + tie); prev = b; decided++; continue; }
                int got = Math.min(free, b.paths);
                free -= got;
                Alloc a = holding(b.company, b.res, b.window, b.season);
                if (a == null || a.season != b.season) { a = new Alloc(); a.id = b.id; a.company = b.company; a.res = b.res; a.window = b.window; a.season = b.season; a.from = now; allocs.add(a); a.paths = 0; }
                a.paths += got;
                a.cents = b.cents;
                a.until = b.season < 0 ? now + 7 * BizData.DAY : 0;
                b.state = got == b.paths ? "WON" : "PART";
                if (c != null) com.dogpound.railmap.api.QuestEvents.fireFor(w, c.members.keySet(), null, "auction_won", "bid", b.id, "paths", got, "full", got == b.paths ? 1 : 0, "window", WINDOWS[b.window], "resource", r.name);
                decision(w, d, "Bid #" + b.id + " " + c.mark + " " + b.state + " " + got + "/" + b.paths + " path(s) " + r.name + " " + WINDOWS[b.window] + " at " + Coins.fmt(b.cents) + tie);
                prev = b;
                decided++;
            }
        }
        bids.removeIf(b -> !b.state.equals("OPEN") && now - b.placed > 3 * BizData.DAY);
        return decided;
    }

    private static String tieWhy(World w, BizData d, CompanyData cd, Bid a, Bid b, Resource r) {
        if (d.pso.priority(w, cd.byId(a.company), r) != d.pso.priority(w, cd.byId(b.company), r)) return "public-service priority";
        return "incumbent / reputation / first bid";
    }

    private void decision(World w, BizData d, String s) {
        decisions.addFirst(com.dogpound.railmap.depot.DepotClock.stamp(w) + " " + s);
        while (decisions.size() > 60) decisions.removeLast();
        Recorder.event(w, "Capacity auction: " + s);
    }

    @Override
    public void daily(World w, BizData d, long day) {
        close(w, d);
        CompanyData cd = CompanyData.get(w);
        int season = season(w);
        for (Alloc a : allocs) {
            if (a.season >= 0 && a.season != season) continue;
            Company c = cd.byId(a.company);
            Resource r = resources.get(a.res);
            if (c == null || r == null) continue;
            long due = a.paths * a.cents;
            if (BizData.pay(w, c, Company.Kind.SLOTS, due, "Capacity " + r.name + " " + WINDOWS[a.window] + " x" + a.paths)) {
                a.paidTotal += due;
                Company owner = r.kind == Kind.CORRIDOR ? cd.ownerOfLine(r.name) : null;
                if (owner != null && owner != c) cd.credit(w, owner, Company.Kind.ACCESS_IN, due, "Capacity sold: " + c.mark + " " + r.name + " " + WINDOWS[a.window]);
            } else c.reputation = Math.max(0, c.reputation - 1);
        }
        allocs.removeIf(a -> cd.byId(a.company) == null);
    }

    /** every 5 s: which company's trains run in whose windows */
    @Override
    public void tick(World w, BizData d, long now) {
        if (allocs.isEmpty()) return;
        CompanyData cd = CompanyData.get(w);
        int win = window(PassengerData.hour(w)), season = season(w), dim = w.provider.getDimension();
        for (RailwayData.AutoTrain at : RailwayData.get(w).trains().values()) {
            Autopilot.Run run = Autopilot.run(at.loco);
            if (run == null) continue;
            Company op = cd.ownerOfTrain(at.loco, at.line);
            String res = "C:" + at.line;
            Resource r = resources.get(res);
            if (op == null || r == null) continue;
            Alloc mine = holding(op.id, res, win, season);
            boolean full = pathsHeld(res, win, season, op.id) >= r.paths;
            String key = "slot:" + at.loco;
            if (mine != null) { if (run.dwelling) mine.used++; AlarmManager.get(w).clear(key); release(dim, at.loco); continue; }
            if (!full) { AlarmManager.get(w).clear(key); release(dim, at.loco); continue; }
            AlarmManager.get(w).raise(w, key, AlarmManager.Cat.AUTO, AlarmManager.Sev.MINOR,
                    "UNAUTHORISED PATH: " + op.mark + "'s " + (at.label.isEmpty() ? "train" : at.label) + " on " + r.name + " in the " + WINDOWS[win] + " window - every path is sold to others", null, true);
            if (BizConfig.enforceSlots && run.dwelling && held.put(at.loco, true) == null) ConsoleControl.hold(dim, at.loco, true);
            for (Alloc a : allocs) if (a.res.equals(res) && a.window == win) a.unauthorisedSeen++;
        }
    }

    private void release(int dim, UUID loco) { if (held.remove(loco) != null) ConsoleControl.hold(dim, loco, false); }

    // ---- save -------------------------------------------------------------------------------------------------------
    @Override
    public void read(NBTTagCompound t) {
        resources.clear(); bids.clear(); allocs.clear(); decisions.clear();
        NBTTagList l = t.getTagList("res", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            Resource r = new Resource();
            try { r.kind = Kind.valueOf(c.getString("k")); } catch (IllegalArgumentException e) { continue; }
            r.name = c.getString("n"); r.paths = c.getInteger("p"); r.reserve = c.getLong("r");
            resources.put(r.key(), r);
        }
        l = t.getTagList("bids", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            Bid b = new Bid();
            b.id = c.getInteger("id"); b.company = c.getInteger("c"); b.window = c.getInteger("w"); b.paths = c.getInteger("p"); b.season = c.getInteger("s");
            b.cents = c.getLong("ct"); b.placed = c.getLong("t"); b.res = c.getString("r"); b.state = c.getString("st");
            bids.add(b);
        }
        l = t.getTagList("alloc", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            Alloc a = new Alloc();
            a.id = c.getInteger("id"); a.company = c.getInteger("c"); a.window = c.getInteger("w"); a.paths = c.getInteger("p"); a.season = c.getInteger("s");
            a.cents = c.getLong("ct"); a.from = c.getLong("f"); a.until = c.getLong("u"); a.res = c.getString("r"); a.paidTotal = c.getLong("pd");
            a.used = c.getInteger("us"); a.unauthorisedSeen = c.getInteger("un");
            allocs.add(a);
        }
        l = t.getTagList("dec", 8);
        for (int i = 0; i < l.tagCount(); i++) decisions.add(l.getStringTagAt(i));
        nextId = Math.max(1, t.getInteger("next"));
    }

    @Override
    public NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        NBTTagList l = new NBTTagList();
        for (Resource r : resources.values()) {
            NBTTagCompound c = new NBTTagCompound();
            c.setString("k", r.kind.name()); c.setString("n", r.name); c.setInteger("p", r.paths); c.setLong("r", r.reserve);
            l.appendTag(c);
        }
        t.setTag("res", l);
        l = new NBTTagList();
        for (Bid b : bids) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("id", b.id); c.setInteger("c", b.company); c.setInteger("w", b.window); c.setInteger("p", b.paths); c.setInteger("s", b.season);
            c.setLong("ct", b.cents); c.setLong("t", b.placed); c.setString("r", b.res); c.setString("st", b.state);
            l.appendTag(c);
        }
        t.setTag("bids", l);
        l = new NBTTagList();
        for (Alloc a : allocs) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("id", a.id); c.setInteger("c", a.company); c.setInteger("w", a.window); c.setInteger("p", a.paths); c.setInteger("s", a.season);
            c.setLong("ct", a.cents); c.setLong("f", a.from); c.setLong("u", a.until); c.setString("r", a.res); c.setLong("pd", a.paidTotal);
            c.setInteger("us", a.used); c.setInteger("un", a.unauthorisedSeen);
            l.appendTag(c);
        }
        t.setTag("alloc", l);
        l = new NBTTagList();
        for (String s : decisions) l.appendTag(new NBTTagString(s));
        t.setTag("dec", l);
        t.setInteger("next", nextId);
        return t;
    }
}
