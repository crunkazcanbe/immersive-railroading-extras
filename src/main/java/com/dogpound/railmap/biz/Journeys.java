package com.dogpound.railmap.biz;

import com.dogpound.railmap.depot.DepotConfig;
import com.dogpound.railmap.economy.PassengerData;
import com.dogpound.railmap.server.StationData;
import com.dogpound.railmap.station.StationFacilities;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.world.World;

import java.util.*;

/**
 * Passenger journeys and missed connections (her "next 48" #14). The passenger model (economy/PassengerData) moves
 * crowds; this follows them as journeys:
 * <ul>
 *   <li>every group that changes trains is queued at the interchange with the time it got off; when a train takes them
 *   on, their wait is measured: up to connectionMinutes is a connection MADE, longer is MISSED;</li>
 *   <li>people still waiting after abandonMinutes give up - ABANDONED trips (taken off the platform);</li>
 *   <li>the share of travellers who need step-free access (DepotConfig) can't change at an audited station that isn't
 *   step-free: they REROUTE via another interchange if there is one, else abandon;</li>
 *   <li>one group in twenty is followed as a sample journey from first boarding to arrival: each leg, each wait, the
 *   outcome and the door-to-door time.</li>
 * </ul>
 * Metrics per interchange and for the whole network go to the console (Journeys page) and /irextras biz journeys.
 */
public final class Journeys implements BizData.Part {
    public static final class Ix {                       // one interchange's day
        public double transfers, made, missed, abandoned, rerouted, waitSum, waited, stepFreeLost;
        public double madeY, missedY, abandonedY;
    }

    public static final class Trip {
        public int id;
        public long from, to, start, end;
        public String state = "TRAVELLING";              // TRAVELLING, CHANGING, ARRIVED, ABANDONED
        public UUID train;
        public long waitingAt = Long.MIN_VALUE, waitFrom;
        public boolean stepFree;
        public final List<String> legs = new ArrayList<>();
    }

    private final Map<Long, Ix> ix = new LinkedHashMap<>();
    /** station -> destination -> FIFO of {passengers, tick they got off, step-free share}
     *  ponytail: not saved - after a restart people already changing count as made connections; save it if that skews the numbers */
    private final Map<Long, Map<Long, Deque<double[]>>> queues = new HashMap<>();
    public final LinkedList<Trip> trips = new LinkedList<>();
    public double completedDay, journeyMinSum, journeys, abandonedDay, abandonedTotal;
    private int nextTrip = 1, sampler;

    @Override public String key() { return "journeys"; }

    public Ix ix(long st) { return ix.computeIfAbsent(st, k -> new Ix()); }
    public Map<Long, Ix> interchanges() { return ix; }

    private static String name(World w, long st) { return StationData.get(w).names().getOrDefault(st, "?"); }
    private static long mins(long ticks) { return ticks * 60 / 1000; }

    /** people got off at st to change for dest: c = PassengerData's {passengers, fare owed} for them (may be trimmed) */
    public void transferIn(World w, long st, long dest, double n, double[] c, long now, String line) {
        if (!BizConfig.enabled || n <= 0) return;
        Ix x = ix(st);
        x.transfers += n;
        double sf = n * DepotConfig.stepFreeShare;
        StationFacilities.Audit a = StationFacilities.peek(w, st);
        if (a != null && a.audited && !a.stepFree && sf > 0) {
            if (altStepFree(w, st, dest)) x.rerouted += sf;                     // they ride on to a step-free interchange
            else {
                x.abandoned += sf; x.stepFreeLost += sf; abandonedDay += sf; abandonedTotal += sf;
                double frac = Math.min(1, sf / Math.max(1e-6, c[0]));
                c[1] -= c[1] * frac; c[0] -= sf;
            }
            n -= sf; sf = 0;
        }
        if (n > 0) queues.computeIfAbsent(st, k -> new HashMap<>()).computeIfAbsent(dest, k -> new ArrayDeque<>()).add(new double[]{n, now, sf});
        for (Trip t : trips) if (t.state.equals("TRAVELLING") && t.to == dest && t.waitingAt == Long.MIN_VALUE && t.train != null && line != null && t.legs.size() > 0 && t.legs.get(t.legs.size() - 1).contains(line)) {
            t.state = "CHANGING"; t.waitingAt = st; t.waitFrom = now; t.train = null;
            t.legs.add(BizData.hhmm(now) + " off at " + name(w, st) + ", changing");
            break;
        }
    }

    /** a train took `take` of the changers for dest at st */
    public void transferOut(World w, long st, long dest, double take, long now, String line, UUID train) {
        if (!BizConfig.enabled || take <= 0) return;
        Ix x = ix(st);
        Deque<double[]> q = queues.getOrDefault(st, Collections.emptyMap()).get(dest);
        double left = take, limit = BizConfig.connectionMinutes * 1000 / 60.0;
        while (q != null && left > 1e-6 && !q.isEmpty()) {
            double[] g = q.peekFirst();
            double t = Math.min(left, g[0]);
            double wait = now - g[1];
            x.waitSum += wait * t; x.waited += t;
            if (wait <= limit) x.made += t; else x.missed += t;
            g[0] -= t; left -= t;
            if (g[0] < 1e-6) q.pollFirst();
        }
        if (left > 1e-6) x.made += left;                                          // changers from before the layer existed
        for (Trip t : trips) if (t.state.equals("CHANGING") && t.waitingAt == st && t.to == dest) {
            long wait = now - t.waitFrom;
            t.state = "TRAVELLING"; t.train = train; t.waitingAt = Long.MIN_VALUE;
            t.legs.add(BizData.hhmm(now) + " boards " + line + " (waited " + mins(wait) + " min" + (wait > limit ? " - MISSED connection" : "") + ")");
            break;
        }
    }

    /** a fresh trip boarded: one group in twenty becomes a followed sample journey */
    public void board(World w, UUID train, long st, long dest, double take, long now, String line) {
        if (!BizConfig.enabled || take < 1 || ++sampler % 20 != 0) return;
        Trip t = new Trip();
        t.id = nextTrip++; t.from = st; t.to = dest; t.start = now; t.train = train;
        t.stepFree = w.rand.nextDouble() < DepotConfig.stepFreeShare;
        t.legs.add(BizData.hhmm(now) + " boards " + line + " at " + name(w, st) + " for " + name(w, dest) + (t.stepFree ? " (needs step-free)" : ""));
        trips.addFirst(t);
        while (trips.size() > 40) trips.removeLast();
    }

    /** people reached their destination st from train */
    public void arrived(World w, UUID train, long st, double n, long now) {
        if (!BizConfig.enabled || n <= 0) return;
        completedDay += n;
        for (Trip t : trips) if (t.state.equals("TRAVELLING") && train.equals(t.train) && t.to == st) {
            t.state = "ARRIVED"; t.end = now;
            t.legs.add(BizData.hhmm(now) + " arrives " + name(w, st) + " - " + mins(now - t.start) + " min door to door");
            journeys++; journeyMinSum += mins(now - t.start);
            break;
        }
    }

    private static boolean altStepFree(World w, long st, long dest) {
        for (StationFacilities.Audit a : StationFacilities.audits(w).values()) if (a.station != st && a.station != dest && a.stepFree) return true;
        return false;
    }

    /** every 5 s: whoever has waited too long gives up */
    @Override
    public void tick(World w, BizData d, long now) {
        if (queues.isEmpty()) return;
        PassengerData pd = PassengerData.get(w);
        long limit = BizConfig.abandonMinutes * 1000L / 60;
        for (Map.Entry<Long, Map<Long, Deque<double[]>>> e : queues.entrySet()) {
            PassengerData.Stn s = pd.stn(e.getKey());
            for (Map.Entry<Long, Deque<double[]>> q : e.getValue().entrySet()) {
                while (!q.getValue().isEmpty() && now - q.getValue().peekFirst()[1] > limit) {
                    double[] g = q.getValue().pollFirst();
                    double[] c = s == null ? null : s.changing.get(q.getKey());
                    double gone = c == null ? 0 : Math.min(g[0], c[0]);
                    if (c != null && gone > 0) { c[1] -= c[1] * gone / Math.max(1e-6, c[0]); c[0] -= gone; if (c[0] < 0.01) s.changing.remove(q.getKey()); }
                    ix(e.getKey()).abandoned += gone; abandonedDay += gone; abandonedTotal += gone;
                    if (s != null) { s.complaintsDay += gone * 0.1; s.reasons[2] += gone * 0.05; }
                }
            }
            e.getValue().values().removeIf(Deque::isEmpty);
        }
        queues.values().removeIf(Map::isEmpty);
        for (Trip t : trips) if (t.state.equals("CHANGING") && now - t.waitFrom > limit) {
            t.state = "ABANDONED"; t.end = now;
            t.legs.add(BizData.hhmm(now) + " gave up at " + name(w, t.waitingAt) + " after " + mins(now - t.waitFrom) + " min");
        }
    }

    @Override
    public void daily(World w, BizData d, long day) {
        for (Ix x : ix.values()) {
            x.madeY = x.made; x.missedY = x.missed; x.abandonedY = x.abandoned;
            x.transfers = x.made = x.missed = x.abandoned = x.rerouted = x.waitSum = x.waited = x.stepFreeLost = 0;
        }
        completedDay = abandonedDay = 0;
    }

    /** the network's connection rate (made / (made + missed)) today, %, or -1 */
    public double connectionPct() {
        double m = 0, x = 0;
        for (Ix i : ix.values()) { m += i.made; x += i.missed; }
        return m + x < 1 ? -1 : m * 100 / (m + x);
    }

    public double avgJourneyMin() { return journeys <= 0 ? -1 : journeyMinSum / journeys; }

    // ---- save -------------------------------------------------------------------------------------------------------
    @Override
    public void read(NBTTagCompound t) {
        ix.clear(); trips.clear();
        NBTTagCompound x = t.getCompoundTag("ix");
        for (String k : x.getKeySet()) {
            NBTTagCompound c = x.getCompoundTag(k);
            Ix i = new Ix();
            i.transfers = c.getDouble("t"); i.made = c.getDouble("m"); i.missed = c.getDouble("x"); i.abandoned = c.getDouble("a"); i.rerouted = c.getDouble("r");
            i.waitSum = c.getDouble("ws"); i.waited = c.getDouble("wn"); i.stepFreeLost = c.getDouble("sf");
            i.madeY = c.getDouble("my"); i.missedY = c.getDouble("xy"); i.abandonedY = c.getDouble("ay");
            try { ix.put(Long.parseLong(k), i); } catch (NumberFormatException ignored) { }
        }
        NBTTagList l = t.getTagList("trips", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            Trip tr = new Trip();
            tr.id = c.getInteger("id"); tr.from = c.getLong("f"); tr.to = c.getLong("to"); tr.start = c.getLong("s"); tr.end = c.getLong("e"); tr.state = c.getString("st");
            if (c.hasUniqueId("tr")) tr.train = c.getUniqueId("tr");
            tr.waitingAt = c.getLong("wa"); tr.waitFrom = c.getLong("wf"); tr.stepFree = c.getBoolean("sfr");
            NBTTagList lg = c.getTagList("legs", 8);
            for (int k = 0; k < lg.tagCount(); k++) tr.legs.add(lg.getStringTagAt(k));
            trips.add(tr);
        }
        completedDay = t.getDouble("cd"); journeyMinSum = t.getDouble("jm"); journeys = t.getDouble("jn"); abandonedDay = t.getDouble("ad"); abandonedTotal = t.getDouble("at");
        nextTrip = Math.max(1, t.getInteger("next"));
    }

    @Override
    public NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        NBTTagCompound x = new NBTTagCompound();
        for (Map.Entry<Long, Ix> e : ix.entrySet()) {
            Ix i = e.getValue();
            NBTTagCompound c = new NBTTagCompound();
            c.setDouble("t", i.transfers); c.setDouble("m", i.made); c.setDouble("x", i.missed); c.setDouble("a", i.abandoned); c.setDouble("r", i.rerouted);
            c.setDouble("ws", i.waitSum); c.setDouble("wn", i.waited); c.setDouble("sf", i.stepFreeLost);
            c.setDouble("my", i.madeY); c.setDouble("xy", i.missedY); c.setDouble("ay", i.abandonedY);
            x.setTag(String.valueOf(e.getKey()), c);
        }
        t.setTag("ix", x);
        NBTTagList l = new NBTTagList();
        for (Trip tr : trips) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("id", tr.id); c.setLong("f", tr.from); c.setLong("to", tr.to); c.setLong("s", tr.start); c.setLong("e", tr.end); c.setString("st", tr.state);
            if (tr.train != null) c.setUniqueId("tr", tr.train);
            c.setLong("wa", tr.waitingAt); c.setLong("wf", tr.waitFrom); c.setBoolean("sfr", tr.stepFree);
            NBTTagList lg = new NBTTagList();
            for (String s : tr.legs) lg.appendTag(new NBTTagString(s));
            c.setTag("legs", lg);
            l.appendTag(c);
        }
        t.setTag("trips", l);
        t.setDouble("cd", completedDay); t.setDouble("jm", journeyMinSum); t.setDouble("jn", journeys); t.setDouble("ad", abandonedDay); t.setDouble("at", abandonedTotal);
        t.setInteger("next", nextTrip);
        return t;
    }
}
