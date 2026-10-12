package com.dogpound.railmap.incident;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.server.Electric;
import com.dogpound.railmap.server.RailProps;
import com.dogpound.railmap.server.StationData;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * The digital twin's predictive SHADOW SIMULATION (her ChatGPT "next 48" list #1, also the engine behind #6 timetable
 * robustness, #48 what-if queries and the older scenario lab #80): a copy of the network - lines, their stops, every
 * driverless train where it is now, its dwell, top speed, traction (electric / diesel), freight or passenger - played
 * forward second by second, many times faster than real time, without touching the live railway.
 * <p>
 * What the shadow model obeys, like the real one does:
 * <ul>
 *   <li><b>A timetable.</b> Each run is booked at its best observed time (the analytics learn them) plus a recovery
 *   allowance; a train never leaves before its booked time, so the allowance soaks up small delays - set it to 0 for a
 *   "tight" timetable.</li>
 *   <li><b>Signalling.</b> A train may not enter a stretch less than the headway behind the one ahead in the same
 *   direction (fixed block; CBTC lines run closer); on SINGLE TRACK nobody enters while a train is coming the other way.</li>
 *   <li><b>Platforms.</b> A stop rail holds one train; a train can't leave for a platform that's still occupied (platform
 *   conflicts are counted, and two trains each waiting for the other's platform is reported as a DEADLOCK).</li>
 *   <li><b>Faults.</b> Closed stretches / stops, temporary speed restrictions and broken points, power cuts (electric
 *   trains stop, diesels carry on), an injected delay to one train.</li>
 *   <li><b>Real variation.</b> Run times vary (seeded, so the "as is" and the "what if" see the same luck - common
 *   random numbers) and now and then a run picks up a minor delay (signal check, slow loading); every case is run with
 *   several seeds and averaged.</li>
 * </ul>
 * Out come departures and departures per hour per line, punctuality, average / worst delay, train-minutes held,
 * platform conflicts, connections kept / missed and their margins, every stretch's waiting time and occupancy (the
 * BOTTLENECKS), train-km, energy and fuel.
 */
public final class TwinSim {
    private TwinSim() {}

    // ---- the model ---------------------------------------------------------------------------------------------------
    public static final class Train {
        public String name = "", line = "";
        public long[] st = new long[0];
        public int idx, dir = 1, dwell = 20, cars = 6;
        public boolean shuttle, at = true, electric, freight;
        public double kmh = 60, left;
        Train copy() {
            Train t = new Train();
            t.name = name; t.line = line; t.st = st; t.idx = idx; t.dir = dir; t.dwell = dwell; t.cars = cars;
            t.shuttle = shuttle; t.at = at; t.electric = electric; t.freight = freight; t.kmh = kmh; t.left = left;
            return t;
        }
    }

    public static final class Model {
        public final Map<String, long[]> lines = new LinkedHashMap<>();
        public final Set<String> cbtc = new HashSet<>();
        /** "from>to" stop keys -> booked ticks (best seen x 1.15, from the analytics) */
        public final Map<String, Long> booked = new HashMap<>();
        public final List<Train> trains = new ArrayList<>();
        /** stop rail -> station name (several rails of one name = one station's platforms) */
        public final Map<Long, String> names = new HashMap<>();
        /** stretch (seg key) -> running lines 1 single / 2 double (default 2) */
        public final Map<String, Integer> tracks = new HashMap<>();
        /** stretches with contact wire / third rail */
        public final Set<String> electrified = new HashSet<>();

        public String name(long k) { String n = names.get(k); if (n != null) return n; BlockPos p = BlockPos.fromLong(k); return p.getX() + "," + p.getZ(); }
        public String seg(long a, long b) { String x = name(a), y = name(b); return x.compareTo(y) <= 0 ? x + "~" + y : y + "~" + x; }
        public int tracks(String seg) { return tracks.getOrDefault(seg, 2); }

        /** every stretch with the stops at its ends, as the lines run them */
        public Map<String, long[]> segments() {
            Map<String, long[]> o = new LinkedHashMap<>();
            for (long[] st : lines.values()) for (int i = 0; i + 1 < st.length; i++) o.putIfAbsent(seg(st[i], st[i + 1]), new long[]{st[i], st[i + 1]});
            for (Map.Entry<String, long[]> e : lines.entrySet()) {
                long[] st = e.getValue();
                if (st.length > 2 && loops(e.getKey())) o.putIfAbsent(seg(st[st.length - 1], st[0]), new long[]{st[st.length - 1], st[0]});
            }
            return o;
        }

        /** a line that loops (its trains run LINE, last stop back to the first) */
        public boolean loops(String line) {
            for (Train t : trains) if (t.line.equals(line)) return !t.shuttle;
            return false;
        }

        public double km(long a, long b) {
            BlockPos p = BlockPos.fromLong(a), q = BlockPos.fromLong(b);
            return Math.hypot(p.getX() - q.getX(), p.getZ() - q.getZ()) * 1.25 / 1000.0;
        }

        /** the ideal run (no allowance), seconds */
        public double ideal(long from, long to, double kmh) {
            Long b = booked.get(from + ">" + to);
            if (b != null) return b / 20.0 / 1.15;
            return km(from, to) * 1000 / Math.max(1, kmh / 3.6 * 0.7);
        }
    }

    /** the live railway as a model: every line, every driverless train on a line, the stretches and their wiring */
    public static Model live(World w) {
        Model m = new Model();
        RailwayData data = RailwayData.get(w);
        StationData sd = StationData.get(w);
        for (RailwayData.Line l : data.lines().values()) {
            if (l.stations.size() < 2) continue;
            long[] st = new long[l.stations.size()];
            for (int i = 0; i < st.length; i++) { st[i] = l.stations.get(i); m.names.put(st[i], Autopilot.nameOf(sd, st[i])); }
            m.lines.put(l.name, st);
            if (l.movingBlock) m.cbtc.add(l.name);
        }
        m.booked.putAll(Analytics.get(w).booked);
        Map<UUID, EntityRollingStock> stock = new HashMap<>();
        try { for (EntityRollingStock s : cam72cam.mod.world.World.get(w).getEntities(EntityRollingStock.class)) stock.put(s.getUUID(), s); } catch (RuntimeException | LinkageError ignored) { }
        for (RailwayData.AutoTrain a : data.trains().values()) {
            if (a.mode != RailwayData.Mode.LINE && a.mode != RailwayData.Mode.SHUTTLE || a.line.isEmpty()) continue;
            long[] st = m.lines.get(a.line);
            if (st == null) continue;
            Autopilot.Run r = Autopilot.run(a.loco);
            Train t = new Train();
            t.name = a.label.isEmpty() ? a.loco.toString().substring(0, 6) : a.label;
            t.line = a.line; t.st = st; t.idx = Math.floorMod(a.index, st.length); t.dir = a.direction < 0 ? -1 : 1;
            t.shuttle = a.mode == RailwayData.Mode.SHUTTLE; t.dwell = a.dwellSeconds; t.kmh = a.maxKmh;
            t.at = r == null || r.dwelling;
            t.left = t.at ? t.dwell : r.metresToStop >= 0 ? r.metresToStop / Math.max(1, t.kmh / 3.6 * 0.7) : 30;
            EntityRollingStock s = stock.get(a.loco);
            if (s instanceof Locomotive l) {
                RailProps.Props pr = RailProps.get(w).peek(a.loco);
                t.electric = pr != null && Electric.isElectric(l, pr);
                try { t.cars = Math.max(1, Autopilot.consist(l).size()); } catch (RuntimeException ignored) { }
                t.freight = Autopilot.cargoUnits(l) > 0;
            }
            RailwayData.Line ln = data.lines().get(a.line);
            if (ln != null) for (int k = 0; k < ln.stations.size(); k++) { RailwayData.Order o = ln.order(k); if (o == RailwayData.Order.LOAD || o == RailwayData.Order.UNLOAD || o == RailwayData.Order.FULL_LOAD) t.freight = true; }
            m.trains.add(t);
        }
        for (Map.Entry<String, long[]> e : m.segments().entrySet()) {
            BlockPos a = BlockPos.fromLong(e.getValue()[0]), b = BlockPos.fromLong(e.getValue()[1]);
            boolean wa = w.isBlockLoaded(a) && Electric.wiredAt(w, a), wb = w.isBlockLoaded(b) && Electric.wiredAt(w, b);
            if (wa && wb) m.electrified.add(e.getKey());
        }
        com.dogpound.railmap.planning.PlanData.get(w).applyTracks(m);     // single / double track as surveyed on the planning desk
        return m;
    }

    // ---- a case (the "what if") ----------------------------------------------------------------------------------------
    public static final class Case {
        public String label = "as is";
        public int minutes = 60, seeds = 3;
        /** timetable recovery allowance over the ideal run, 0.10 = 10 % */
        public double recovery = 0.10;
        /** run-time spread (standard deviation, fraction) and the chance a run picks up a minor 30-120 s delay */
        public double variance = 0.05, failChance = 0.02;
        public int headway = 90, cbtcHeadway = 45, minConnect = 60;
        public final Map<String, Integer> dwellDelta = new HashMap<>(), speedPct = new HashMap<>(), trainsDelta = new HashMap<>();
        public final Set<String> cbtcLines = new HashSet<>();
        /** stretch / station name -> {from s, until s} */
        public final Map<String, double[]> closeSeg = new HashMap<>(), closeStop = new HashMap<>(), powerOff = new HashMap<>();
        /** stretch -> speed factor (0.5 = runs take twice as long): TSRs, a broken set of points worked by hand */
        public final Map<String, Double> segSpeed = new HashMap<>();
        /** stretch -> running lines (1 = single-line working, 2 = doubled) */
        public final Map<String, Integer> segTracks = new HashMap<>();
        /** stretches given contact wire ("*" = all) */
        public final Set<String> electrify = new HashSet<>();
        public String delayTrain = "";
        public double delayAt = 300, delaySec;

        public List<String> describe() {
            List<String> o = new ArrayList<>();
            o.add(String.format(Locale.ROOT, "%d min x %d seeds, recovery allowance %.0f %%, run spread %.0f %%, minor-delay chance %.0f %%/run",
                    minutes, seeds, recovery * 100, variance * 100, failChance * 100));
            o.add("headway " + headway + " s (CBTC " + cbtcHeadway + " s), connection time " + minConnect + " s");
            dwellDelta.forEach((k, v) -> o.add("dwell " + (v > 0 ? "+" : "") + v + " s on " + k));
            speedPct.forEach((k, v) -> o.add("speed " + (v > 0 ? "+" : "") + v + " % on " + k));
            trainsDelta.forEach((k, v) -> o.add((v > 0 ? "+" : "") + v + " trains on " + k));
            cbtcLines.forEach(k -> o.add("CBTC moving block on " + k));
            closeSeg.forEach((k, v) -> o.add("CLOSED " + k + win(v)));
            closeStop.forEach((k, v) -> o.add("STATION CLOSED " + k + win(v)));
            powerOff.forEach((k, v) -> o.add("POWER OFF " + k + win(v)));
            segSpeed.forEach((k, v) -> o.add(String.format(Locale.ROOT, "%s at %.0f %% speed", k, v * 100)));
            segTracks.forEach((k, v) -> o.add(k + (v == 1 ? " SINGLE-LINE WORKING" : " DOUBLE TRACK")));
            electrify.forEach(k -> o.add("ELECTRIFIED " + k));
            if (delaySec > 0) o.add(String.format(Locale.ROOT, "%.0f s delay to %s at %.0f s", delaySec, delayTrain.isEmpty() ? "the first train" : delayTrain, delayAt));
            return o;
        }

        private static String win(double[] v) { return v[1] >= 1e8 ? " from " + Math.round(v[0] / 60) + " min" : " " + Math.round(v[0] / 60) + "-" + Math.round(v[1] / 60) + " min"; }
    }

    // ---- results ---------------------------------------------------------------------------------------------------------
    public static final class Result {
        public double deps, ontime, delay, maxDelay, held, platformWait, platformConflicts, deadlocks, connections, connMissed, connMargin, trainKm, kwh, litres, trains;
        public double minMargin = Double.MAX_VALUE, minutes;
        public final Map<String, Double> perLine = new TreeMap<>(), lineDelay = new TreeMap<>(), segWait = new TreeMap<>(), segBusy = new TreeMap<>(), stopWait = new TreeMap<>(), segTrips = new TreeMap<>();
        /** per interchange station: {connections, missed, margin sum, smallest margin} */
        public final Map<String, double[]> connAt = new TreeMap<>();
        /** per train, the delay at each departure (for knock-on comparisons; single runs only) */
        public final Map<String, List<double[]>> trace = new LinkedHashMap<>();
        public final Set<String> deadlockAt = new TreeSet<>();
        int n;

        void add(Result o) {
            deps += o.deps; ontime += o.ontime; delay += o.delay; maxDelay = Math.max(maxDelay, o.maxDelay); held += o.held; platformWait += o.platformWait;
            platformConflicts += o.platformConflicts; deadlocks += o.deadlocks; connections += o.connections; connMissed += o.connMissed; connMargin += o.connMargin;
            trainKm += o.trainKm; kwh += o.kwh; litres += o.litres; trains = o.trains; minutes = o.minutes; minMargin = Math.min(minMargin, o.minMargin);
            o.perLine.forEach((k, v) -> perLine.merge(k, v, Double::sum));
            o.lineDelay.forEach((k, v) -> lineDelay.merge(k, v, Double::sum));
            o.segWait.forEach((k, v) -> segWait.merge(k, v, Double::sum));
            o.segBusy.forEach((k, v) -> segBusy.merge(k, v, Double::sum));
            o.stopWait.forEach((k, v) -> stopWait.merge(k, v, Double::sum));
            o.segTrips.forEach((k, v) -> segTrips.merge(k, v, Double::sum));
            o.connAt.forEach((k, v) -> { double[] x = connAt.computeIfAbsent(k, q -> new double[]{0, 0, 0, Double.MAX_VALUE}); x[0] += v[0]; x[1] += v[1]; x[2] += v[2]; x[3] = Math.min(x[3], v[3]); });
            deadlockAt.addAll(o.deadlockAt);
            n++;
        }

        void average() {
            if (n <= 1) return;
            double f = 1.0 / n;
            deps *= f; ontime *= f; delay *= f; held *= f; platformWait *= f; platformConflicts *= f; deadlocks *= f; connections *= f; connMissed *= f; connMargin *= f;
            trainKm *= f; kwh *= f; litres *= f;
            for (Map<String, Double> m : Arrays.asList(perLine, lineDelay, segWait, segBusy, stopWait, segTrips)) m.replaceAll((k, v) -> v * f);
            for (double[] v : connAt.values()) { v[0] *= f; v[1] *= f; v[2] *= f; }
        }

        public double avgDelay() { return deps <= 0 ? 0 : delay / deps; }
        public double punctual() { return deps <= 0 ? 100 : ontime * 100 / deps; }
        public double perHour(String line) { return perLine.getOrDefault(line, 0.0) * 60 / Math.max(1, minutes); }
        public double connKept() { return connections <= 0 ? 100 : (connections - connMissed) * 100 / connections; }
        /** the worst stretches: {seg, waiting train-minutes, occupancy %} */
        public List<String[]> bottlenecks(Model m, int max) {
            List<Map.Entry<String, Double>> l = new ArrayList<>(segWait.entrySet());
            l.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            List<String[]> o = new ArrayList<>();
            for (Map.Entry<String, Double> e : l) {
                if (o.size() >= max || e.getValue() < 30) break;
                double occ = segBusy.getOrDefault(e.getKey(), 0.0) / Math.max(1, minutes * 60 * m.tracks(e.getKey())) * 100;
                o.add(new String[]{e.getKey(), String.format(Locale.ROOT, "%.1f", e.getValue() / 60), String.format(Locale.ROOT, "%.0f", occ)});
            }
            return o;
        }
    }

    /** run a case with several seeds and average */
    public static Result run(Model m, Case c) {
        Map<String, List<Double>> tt = timetable(m, c);
        Result r = new Result();
        for (int s = 0; s < Math.max(1, c.seeds); s++) r.add(once(m, c, 1000L + s * 7919L, tt));
        r.average();
        return r;
    }

    /**
     * The working timetable for a case: what the planners would book - a clean run (no variation, no minor delays) of
     * everything PLANNED in it (dwells, speeds, trains, CBTC, wire, doubling, single-line working, the recovery
     * allowance), without the things nobody plans (closures, power cuts, speed restrictions, the injected delay).
     * Every departure is then late against this.
     */
    public static Map<String, List<Double>> timetable(Model m, Case c) {
        Case p = copyWithoutDelay(c);
        p.closeSeg.clear(); p.closeStop.clear(); p.powerOff.clear(); p.segSpeed.clear();
        p.variance = 0; p.failChance = 0;
        Result r = once(m, p, 0, null);
        Map<String, List<Double>> tt = new HashMap<>();
        for (Map.Entry<String, List<double[]>> e : r.trace.entrySet()) { List<Double> l = new ArrayList<>(); for (double[] d : e.getValue()) l.add(d[0]); tt.put(e.getKey(), l); }
        return tt;
    }

    // ---- one run -----------------------------------------------------------------------------------------------------------
    private static final class S {
        Train t;
        double sched, schedArr;
        String seg;
        boolean upSeg;
        double waitSince = -1;
        boolean waitedPlatform;
        /** the platform this train stands at / is booked into: stop rail + the direction it came in on (a double-track
         *  stop has a platform each way; on single track both directions share one) */
        String plat;
        final List<double[]> deps = new ArrayList<>();
    }

    private static boolean active(Map<String, double[]> m, String k, double now) {
        double[] v = m.get(k);
        if (v == null) v = m.get("*");
        return v != null && now >= v[0] && now < v[1];
    }

    private static <T> T pick(Map<String, T> m, String line, T dflt) { T v = m.get(line); if (v == null) v = m.get("*"); return v == null ? dflt : v; }

    public static Result once(Model m0, Case c, long seed) { return once(m0, c, seed, timetable(m0, c)); }

    static Result once(Model m0, Case c, long seed, Map<String, List<Double>> tt) {
        Random rnd = new Random(seed);
        Result r = new Result();
        r.minutes = c.minutes;
        double end = c.minutes * 60.0;
        // trains, with the case's changes
        List<S> ss = new ArrayList<>();
        for (Train t0 : m0.trains) {
            Train t = t0.copy();
            t.dwell = Math.max(5, t.dwell + pick(c.dwellDelta, t.line, 0));
            t.kmh = t.kmh * (1 + pick(c.speedPct, t.line, 0) / 100.0);
            S s = new S(); s.t = t; ss.add(s);
        }
        for (Map.Entry<String, Integer> e : c.trainsDelta.entrySet()) {
            long[] st = m0.lines.get(e.getKey());
            if (st == null) continue;
            int add = e.getValue();
            if (add < 0) { for (int k = ss.size() - 1; k >= 0 && add < 0; k--) if (ss.get(k).t.line.equals(e.getKey())) { ss.remove(k); add++; } continue; }
            Train like = null;
            for (S s : ss) if (s.t.line.equals(e.getKey())) { like = s.t; break; }
            for (int k = 0; k < add; k++) {
                Train t = like == null ? new Train() : like.copy();
                t.name = "extra " + e.getKey() + " " + (k + 1); t.line = e.getKey(); t.st = st; t.dir = 1; t.at = true;
                t.idx = (st.length * (k + 1) / (add + 1)) % st.length;                  // spread along the line
                if (like == null) { t.dwell = Math.max(5, 20 + pick(c.dwellDelta, e.getKey(), 0)); t.kmh = 60 * (1 + pick(c.speedPct, e.getKey(), 0) / 100.0); }
                t.left = t.dwell + k * c.headway;
                S s = new S(); s.t = t; ss.add(s);
            }
        }
        r.trains = ss.size();
        // where everyone is now: platforms occupied, stretches occupied
        Map<String, S> platform = new HashMap<>();
        Map<String, int[]> occ = new HashMap<>();             // seg -> {trains running "up" (a->b in key order), "down"}
        Map<String, Double> lastEntry = new HashMap<>();      // seg + direction -> time the last train entered
        for (S s : ss) {
            Train t = s.t;
            if (t.st.length < 2) continue;
            if (t.at) {
                int prev = prevIdx(t);
                String pk = platKey(m0, c, t.st[prev], t.st[t.idx]);
                if (!platform.containsKey(pk)) { platform.put(pk, s); s.plat = pk; }
                s.sched = Math.max(0, t.left);
            } else {
                int prev = prevIdx(t);
                String pk = platKey(m0, c, t.st[prev], t.st[t.idx]);
                if (!platform.containsKey(pk)) { platform.put(pk, s); s.plat = pk; }
                s.seg = m0.seg(t.st[prev], t.st[t.idx]);
                s.upSeg = m0.name(t.st[prev]).compareTo(m0.name(t.st[t.idx])) <= 0;
                occ.computeIfAbsent(s.seg, k -> new int[2])[s.upSeg ? 0 : 1]++;
                s.schedArr = t.left;
                s.sched = t.left + t.dwell;
            }
        }
        // events for connections: station -> list of {line hash, sched, actual} arrivals / departures
        Map<String, List<Object[]>> arrs = new HashMap<>(), deps = new HashMap<>();
        boolean delayDone = c.delaySec <= 0;
        double lastDep = 0;
        boolean gridlock = false;
        for (double now = 0; now < end; now += 1) {
            if (!gridlock && now - lastDep > 900 && now % 60 == 0) {                 // nothing has moved for 15 min: trains locked on each other's platforms
                Set<String> at = new TreeSet<>();
                for (S x : ss) if (x.waitedPlatform && x.t.at) at.add(m0.name(x.t.st[x.t.idx]));
                if (!at.isEmpty()) { r.deadlockAt.add("gridlock: " + String.join(", ", at)); gridlock = true; }
            }
            for (S s : ss) {
                Train t = s.t;
                if (t.st.length < 2) continue;
                if (!delayDone && now >= c.delayAt && (c.delayTrain.isEmpty() ? s == ss.get(0) : t.name.equalsIgnoreCase(c.delayTrain))) { t.left += c.delaySec; delayDone = true; }
                if (!t.at) {
                    if (t.electric && s.seg != null && active(c.powerOff, s.seg, now)) { r.held++; continue; }   // no power: standing in section
                    if (s.seg != null) r.segBusy.merge(s.seg, 1.0, Double::sum);
                    t.left -= 1;
                    if (t.left > 0) continue;
                    // arrive
                    if (s.seg != null) { int[] o = occ.get(s.seg); if (o != null) o[s.upSeg ? 0 : 1] = Math.max(0, o[s.upSeg ? 0 : 1] - 1); }
                    s.seg = null;
                    t.at = true;
                    t.left = t.dwell;
                    arrs.computeIfAbsent(m0.name(t.st[t.idx]), k -> new ArrayList<>()).add(new Object[]{t.line, s.schedArr, now, t.name});
                    continue;
                }
                t.left -= 1;
                if (t.left > 0) continue;
                // ready to leave stop idx
                List<Double> book = tt == null ? null : tt.get(t.name);
                double booked = book != null && s.deps.size() < book.size() ? book.get(s.deps.size()) : s.sched;
                if (now < booked) continue;                                           // early: wait for the booked time
                int nxt = nextIdx(t, false);
                long from = t.st[t.idx], to = t.st[nxt];
                String fromN = m0.name(from), toN = m0.name(to), seg = m0.seg(from, to);
                boolean up = fromN.compareTo(toN) <= 0;
                String why = null;
                if (active(c.closeStop, fromN, now) || active(c.closeStop, toN, now)) why = "stop";
                else if (active(c.closeSeg, seg, now)) why = "closed";
                else if (t.electric && active(c.powerOff, seg, now)) why = "power";
                if (why != null) { r.held++; continue; }
                int tracks = c.segTracks.getOrDefault(seg, m0.tracks(seg));
                int[] o = occ.computeIfAbsent(seg, k -> new int[2]);
                boolean cbtc = m0.cbtc.contains(t.line) || c.cbtcLines.contains(t.line) || c.cbtcLines.contains("*");
                Double last = lastEntry.get(seg + (up ? ">" : "<"));
                boolean signal = last != null && now - last < (cbtc ? c.cbtcHeadway : c.headway);
                boolean single = tracks <= 1 && o[up ? 1 : 0] > 0;
                if (signal || single) { r.segWait.merge(seg, 1.0, Double::sum); continue; }
                String pk = platKey(m0, c, from, to);
                S onPlat = platform.get(pk);
                if (onPlat != null && onPlat != s) {
                    r.platformWait++;
                    r.stopWait.merge(toN, 1.0, Double::sum);
                    if (!s.waitedPlatform) { r.platformConflicts++; s.waitedPlatform = true; s.waitSince = now; }
                    if (now - s.waitSince > 600 && onPlat.t.at && onPlat.waitedPlatform && onPlat.waitSince <= s.waitSince + 600) r.deadlockAt.add(fromN + " / " + toN);
                    continue;
                }
                // depart
                s.waitedPlatform = false;
                double late = Math.max(0, now - booked);
                lastDep = now;
                r.deps++;
                r.delay += late;
                r.maxDelay = Math.max(r.maxDelay, late);
                if (late <= IncidentConfig.onTimeSeconds) r.ontime++;
                r.perLine.merge(t.line, 1.0, Double::sum);
                r.lineDelay.merge(t.line, late, Double::sum);
                s.deps.add(new double[]{now, late, from});
                deps.computeIfAbsent(fromN, k -> new ArrayList<>()).add(new Object[]{t.line, booked, now, t.name});
                if (s.plat != null && platform.get(s.plat) == s) platform.remove(s.plat);
                platform.put(pk, s);
                s.plat = pk;
                o[up ? 0 : 1]++;
                lastEntry.put(seg + (up ? ">" : "<"), now);
                r.segTrips.merge(seg, 1.0, Double::sum);
                double ideal = m0.ideal(from, to, t.kmh);
                double f = c.segSpeed.getOrDefault(seg, 1.0);
                double run = ideal / Math.max(0.05, f);
                if (!t.electric && electrified(c, seg, false)) run *= 0.93;          // newly wired: an electric's better acceleration
                run *= Math.max(0.7, 1 + rnd.nextGaussian() * c.variance);
                if (rnd.nextDouble() < c.failChance) run += 30 + rnd.nextInt(91);
                double km = m0.km(from, to);
                r.trainKm += km;
                boolean wired = t.electric || electrified(c, seg, false);
                if (wired) r.kwh += km * (8 + 2.2 * t.cars) * (t.freight ? 1.6 : 1.0);
                else r.litres += km * (2.2 + 0.55 * t.cars) * (t.freight ? 1.6 : 1.0);
                s.seg = seg;
                s.upSeg = up;
                t.idx = nextIdx(t, true);
                t.at = false;
                t.left = run;
                s.schedArr = booked + ideal * (1 + c.recovery);
                s.sched = s.schedArr + t.dwell;
            }
        }
        // connections: an arrival on one line and the first booked departure of another line at least minConnect later
        for (Map.Entry<String, List<Object[]>> e : arrs.entrySet()) {
            List<Object[]> ds = deps.get(e.getKey());
            if (ds == null) continue;
            for (Object[] a : e.getValue()) {
                Object[] best = null;
                for (Object[] d : ds) {
                    if (d[0].equals(a[0])) continue;
                    double gap = (double) d[1] - (double) a[1];
                    if (gap < c.minConnect || gap > c.minConnect + 900) continue;
                    if (best == null || (double) d[1] < (double) best[1]) best = d;
                }
                if (best == null) continue;
                r.connections++;
                double margin = (double) best[2] - (double) a[2];
                r.connMargin += margin;
                r.minMargin = Math.min(r.minMargin, margin);
                if (margin < c.minConnect) r.connMissed++;
                double[] at = r.connAt.computeIfAbsent(e.getKey(), q -> new double[]{0, 0, 0, Double.MAX_VALUE});
                at[0]++; at[2] += margin; at[3] = Math.min(at[3], margin);
                if (margin < c.minConnect) at[1]++;
            }
        }
        r.deadlocks = r.deadlockAt.size();
        for (S s : ss) r.trace.put(s.t.name, s.deps);
        return r;
    }

    /** the platform a train running from -> to arrives at */
    private static String platKey(Model m, Case c, long from, long to) {
        String seg = m.seg(from, to);
        int tracks = c.segTracks.getOrDefault(seg, m.tracks(seg));
        return tracks <= 1 ? String.valueOf(to) : to + (m.name(from).compareTo(m.name(to)) <= 0 ? ">" : "<");
    }

    private static boolean electrified(Case c, String seg, boolean already) { return already || c.electrify.contains("*") || seg != null && c.electrify.contains(seg); }

    private static int prevIdx(Train t) {
        int n = t.st.length, k = t.idx - t.dir;
        if (!t.shuttle) return Math.floorMod(k, n);
        return Math.max(0, Math.min(n - 1, k < 0 || k >= n ? t.idx + t.dir : k));
    }

    /** the stop after this one (turning a shuttle round at the ends when {@code turn}) */
    static int nextIdx(Train t, boolean turn) {
        int n = t.st.length;
        if (!t.shuttle) return Math.floorMod(t.idx + t.dir, n);
        int k = t.idx + t.dir;
        if (k < 0 || k >= n) { if (turn) t.dir = -t.dir; k = t.idx - (k < 0 ? -1 : 1); }
        return Math.max(0, Math.min(n - 1, k));
    }

    /** the knock-on of an injected delay: compares a case with and without it, seed by seed */
    public static double[] knockOn(Model m, Case withDelay) {
        double trainsHit = 0, extraMin = 0, stops = 0, recoverDeps = 0;
        Set<String> reached = new HashSet<>();
        int seeds = Math.max(1, withDelay.seeds);
        Case base = copyWithoutDelay(withDelay);
        Map<String, List<Double>> tt = timetable(m, base);
        for (int s = 0; s < seeds; s++) {
            Result a = once(m, base, 1000L + s * 7919L, tt), b = once(m, withDelay, 1000L + s * 7919L, tt);
            for (Map.Entry<String, List<double[]>> e : b.trace.entrySet()) {
                List<double[]> x = a.trace.getOrDefault(e.getKey(), Collections.emptyList()), y = e.getValue();
                boolean hit = false;
                int lastHit = -1;
                for (int i = 0; i < Math.min(x.size(), y.size()); i++) {
                    double extra = y.get(i)[1] - x.get(i)[1];
                    if (extra > 30) { hit = true; lastHit = i; extraMin += extra / 60; reached.add(m.name((long) y.get(i)[2])); }
                }
                boolean victim = withDelay.delayTrain.isEmpty() ? e.getKey().equals(b.trace.keySet().iterator().next()) : e.getKey().equalsIgnoreCase(withDelay.delayTrain);
                if (hit && !victim) trainsHit++;
                if (victim) recoverDeps += lastHit + 1;
            }
        }
        stops = reached.size();
        return new double[]{trainsHit / seeds, extraMin / seeds, stops, recoverDeps / seeds};
    }

    static Case copyWithoutDelay(Case c) {
        Case o = new Case();
        o.label = c.label; o.minutes = c.minutes; o.seeds = c.seeds; o.recovery = c.recovery; o.variance = c.variance; o.failChance = c.failChance;
        o.headway = c.headway; o.cbtcHeadway = c.cbtcHeadway; o.minConnect = c.minConnect;
        o.dwellDelta.putAll(c.dwellDelta); o.speedPct.putAll(c.speedPct); o.trainsDelta.putAll(c.trainsDelta); o.cbtcLines.addAll(c.cbtcLines);
        o.closeSeg.putAll(c.closeSeg); o.closeStop.putAll(c.closeStop); o.powerOff.putAll(c.powerOff); o.segSpeed.putAll(c.segSpeed); o.segTracks.putAll(c.segTracks);
        o.electrify.addAll(c.electrify);
        return o;
    }

    public static Case copy(Case c) { Case o = copyWithoutDelay(c); o.delayTrain = c.delayTrain; o.delayAt = c.delayAt; o.delaySec = c.delaySec; return o; }
}
