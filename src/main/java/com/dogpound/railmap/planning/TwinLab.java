package com.dogpound.railmap.planning;

import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.incident.Analytics;
import com.dogpound.railmap.incident.TwinSim;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * The digital twin's laboratory (her "next 48" list #1): the WORKING CASE - a timetable change (dwell, speed, trains
 * per line, recovery allowance), a closure, a broken set of points (speed restriction / single-line working), a power
 * cut, a delay, CBTC, contact wire, doubling - built on the planning desk or the console, run on the shadow simulation
 * against the railway exactly as it is now, compared side by side with "as is", with the bottlenecks of the proposed
 * layout. The live railway is never touched until the case is PROMOTED.
 * <p>
 * PREDICTED vs ACTUAL: every run also records what the twin predicts the live railway will do next (departures per hour
 * and average delay per line); once that time has passed the prediction is checked against what the analytics counted,
 * so you can see how far to trust it.
 * <p>
 * PROMOTE puts a successful case live: dwell / speed changes into the driverless trains on the line, CBTC onto the line,
 * a closure / broken point / single-line working as a planned temporary configuration (its work crew goes out at the
 * start of the window). Capital works (wire, a second track, more trains) can't be conjured up - they are listed for the
 * engineers. A case is only promoted if it ran on the railway as it is now, is not worse on punctuality and has no
 * predicted deadlock; every promotion is a configuration version (#40) and can be rolled back.
 */
public final class TwinLab {
    private TwinLab() {}

    public static final String[] KNOBS = {"line", "seg", "stop", "dwell", "speed", "trains", "cbtc", "close", "closestop", "tsr", "single", "power", "elec", "double", "rec", "delay", "minutes"};

    public static NBTTagCompound params(World w) { return PlanData.get(w).twinCase; }

    static List<String> lines(TwinSim.Model m) { return new ArrayList<>(new TreeSet<>(m.lines.keySet())); }
    static List<String> segs(TwinSim.Model m) { return new ArrayList<>(new TreeSet<>(m.segments().keySet())); }
    static List<String> stops(TwinSim.Model m) { return new ArrayList<>(new TreeSet<>(m.names.values())); }

    private static String pick(List<String> l, int i) { return l.isEmpty() ? "-" : l.get(Math.floorMod(i, l.size())); }

    /** turn a knob: wraps / clamps like the desk's selectors and thumbwheels */
    public static String set(World w, String what, int delta) {
        NBTTagCompound p = params(w);
        switch (what) {
            case "line": case "seg": case "stop": p.setInteger(what, p.getInteger(what) + delta); break;
            case "dwell": p.setInteger(what, clamp(p.getInteger(what) + delta * 5, -60, 300)); break;
            case "speed": p.setInteger(what, clamp(p.getInteger(what) + delta * 5, -50, 100)); break;
            case "trains": p.setInteger(what, clamp(p.getInteger(what) + delta, -10, 10)); break;
            case "close": case "closestop": case "power": p.setInteger(what, clamp(p.getInteger(what) + delta * 5, 0, 240)); break;
            case "tsr": p.setInteger(what, clamp(p.getInteger(what) + delta * 10, 0, 90)); break;
            case "rec": p.setInteger(what, clamp((p.hasKey("rec") ? p.getInteger("rec") : 10) + delta, 0, 40)); break;
            case "delay": p.setInteger(what, clamp(p.getInteger(what) + delta * 30, 0, 1800)); break;
            case "minutes": p.setInteger(what, clamp((p.hasKey("minutes") ? p.getInteger("minutes") : 60) + delta * 15, 15, 480)); break;
            case "cbtc": case "single": case "elec": case "double": p.setBoolean(what, !p.getBoolean(what)); break;
            case "reset": for (String k : new ArrayList<>(p.getKeySet())) p.removeTag(k); break;
            default: return null;
        }
        PlanData.get(w).twinOk = false;
        PlanData.get(w).markDirty();
        return describe(w);
    }

    private static int clamp(int v, int a, int b) { return Math.max(a, Math.min(b, v)); }

    public static String line(World w, TwinSim.Model m) { return pick(lines(m), params(w).getInteger("line")); }
    public static String seg(World w, TwinSim.Model m) { return pick(segs(m), params(w).getInteger("seg")); }
    public static String stop(World w, TwinSim.Model m) { return pick(stops(m), params(w).getInteger("stop")); }

    /** the working case as the simulator's Case */
    public static TwinSim.Case build(World w, TwinSim.Model m) {
        NBTTagCompound p = params(w);
        TwinSim.Case c = new TwinSim.Case();
        c.label = "working case";
        c.minutes = p.hasKey("minutes") ? p.getInteger("minutes") : 60;
        c.recovery = (p.hasKey("rec") ? p.getInteger("rec") : 10) / 100.0;
        c.minConnect = PlanConfig.minConnectSeconds;
        String line = line(w, m), seg = seg(w, m), stop = stop(w, m);
        if (p.getInteger("dwell") != 0) c.dwellDelta.put(line, p.getInteger("dwell"));
        if (p.getInteger("speed") != 0) c.speedPct.put(line, p.getInteger("speed"));
        if (p.getInteger("trains") != 0) c.trainsDelta.put(line, p.getInteger("trains"));
        if (p.getBoolean("cbtc")) c.cbtcLines.add(line);
        if (p.getInteger("close") > 0) c.closeSeg.put(seg, new double[]{0, p.getInteger("close") * 60.0});
        if (p.getInteger("closestop") > 0) c.closeStop.put(stop, new double[]{0, p.getInteger("closestop") * 60.0});
        if (p.getInteger("tsr") > 0) c.segSpeed.put(seg, 1 - p.getInteger("tsr") / 100.0);
        if (p.getBoolean("single")) c.segTracks.put(seg, 1);
        if (p.getBoolean("double")) c.segTracks.put(seg, 2);
        if (p.getInteger("power") > 0) c.powerOff.put(seg, new double[]{0, p.getInteger("power") * 60.0});
        if (p.getBoolean("elec")) c.electrify.add(seg);
        if (p.getInteger("delay") > 0) {
            c.delaySec = p.getInteger("delay"); c.delayAt = 120;
            for (TwinSim.Train t : m.trains) if (t.line.equals(line)) { c.delayTrain = t.name; break; }
        }
        return c;
    }

    public static TwinSim.Case asIs(TwinSim.Case c) {
        TwinSim.Case b = new TwinSim.Case();
        b.minutes = c.minutes; b.recovery = c.recovery; b.minConnect = c.minConnect; b.seeds = c.seeds;
        return b;
    }

    public static String describe(World w) {
        TwinSim.Model m = TwinSim.live(w);
        List<String> d = build(w, m).describe();
        return "Working case: " + String.join("; ", d.subList(1, d.size()).isEmpty() ? Collections.singletonList("no changes (as is)") : d.subList(1, d.size()));
    }

    // ---- run ---------------------------------------------------------------------------------------------------------------
    public static String run(World w, String who) {
        PlanData d = PlanData.get(w);
        TwinSim.Model m = TwinSim.live(w);
        if (m.trains.isEmpty()) return "§7The twin has no driverless trains on lines to simulate";
        TwinSim.Case c = build(w, m);
        TwinSim.Result a = TwinSim.run(m, asIs(c)), b = TwinSim.run(m, c);
        d.lastTwin.clear();
        for (String s : c.describe()) d.lastTwin.add(new String[]{"TA", s});
        row(d, "Departures", a.deps, b.deps, "%.0f", true);
        row(d, "Punctuality %", a.punctual(), b.punctual(), "%.1f", true);
        row(d, "Average delay s", a.avgDelay(), b.avgDelay(), "%.0f", false);
        row(d, "Worst delay s", a.maxDelay, b.maxDelay, "%.0f", false);
        row(d, "Train-min held (closures / power)", a.held / 60, b.held / 60, "%.1f", false);
        row(d, "Platform conflicts", a.platformConflicts, b.platformConflicts, "%.1f", false);
        row(d, "Platform wait min", a.platformWait / 60, b.platformWait / 60, "%.1f", false);
        row(d, "Connections kept %", a.connKept(), b.connKept(), "%.1f", true);
        row(d, "Deadlocks", a.deadlocks, b.deadlocks, "%.0f", false);
        row(d, "Train-km", a.trainKm, b.trainKm, "%.1f", true);
        row(d, "Energy kWh", a.kwh, b.kwh, "%.0f", false);
        row(d, "Diesel litres", a.litres, b.litres, "%.0f", false);
        row(d, "Running cost $", a.kwh * PlanConfig.priceKwh + a.litres * PlanConfig.priceLitre, b.kwh * PlanConfig.priceKwh + b.litres * PlanConfig.priceLitre, "%.0f", false);
        Set<String> ls = new TreeSet<>(a.perLine.keySet()); ls.addAll(b.perLine.keySet());
        for (String l : ls) row(d, l + " trains/h", a.perHour(l), b.perHour(l), "%.1f", true);
        for (String[] bn : b.bottlenecks(m, 6)) d.lastTwin.add(new String[]{"TB", bn[0], bn[1], bn[2], c.segTracks.getOrDefault(bn[0], m.tracks(bn[0])) <= 1 ? "single" : "double"});
        for (String x : b.deadlockAt) d.lastTwin.add(new String[]{"TD", x});
        // what PROMOTE would do
        d.promotable.clear();
        NBTTagCompound p = params(w);
        String line = line(w, m), seg = seg(w, m);
        if (p.getInteger("dwell") != 0) d.promotable.add("LIVE: dwell " + sign(p.getInteger("dwell")) + " s on line " + line);
        if (p.getInteger("speed") != 0) d.promotable.add("LIVE: top speed " + sign(p.getInteger("speed")) + " % on line " + line);
        if (p.getBoolean("cbtc")) d.promotable.add("LIVE: CBTC moving block on line " + line);
        if (p.getInteger("close") > 0) d.promotable.add("WORKS: possession of " + seg + " for " + p.getInteger("close") + " min");
        if (p.getInteger("tsr") > 0) d.promotable.add("WORKS: TSR on " + seg + " (" + (100 - p.getInteger("tsr")) + " % speed)");
        if (p.getBoolean("single")) d.promotable.add("WORKS: single-line working over " + seg);
        if (p.getInteger("trains") > 0) d.promotable.add("ENGINEERS: " + p.getInteger("trains") + " more train(s) needed on " + line);
        if (p.getBoolean("elec")) d.promotable.add("ENGINEERS: contact wire over " + seg);
        if (p.getBoolean("double")) d.promotable.add("ENGINEERS: second track over " + seg);
        if (p.getInteger("power") > 0) d.promotable.add("TEST ONLY: power cut (not promoted)");
        if (p.getInteger("delay") > 0) d.promotable.add("TEST ONLY: injected delay (not promoted)");
        d.twinOk = b.punctual() >= a.punctual() - 2 && b.deadlocks == 0;
        d.lastTwin.add(new String[]{"TV", d.twinOk ? "PROMOTABLE" : b.deadlocks > 0 ? "NOT PROMOTABLE - DEADLOCK PREDICTED" : "NOT PROMOTABLE - WORSE PUNCTUALITY", String.valueOf(w.getTotalWorldTime())});
        // the as-is run is the twin's prediction of the next hour of the live railway
        predict(w, d, a, c.minutes);
        d.markDirty();
        Recorder.event(w, "Twin run by " + who + ": " + String.format(Locale.ROOT, "punctuality %.1f%% -> %.1f%%, delay %.0f -> %.0f s", a.punctual(), b.punctual(), a.avgDelay(), b.avgDelay()));
        return String.format(Locale.ROOT, "§bTwin (%d min x %d): punctuality %.1f%% -> %.1f%%, avg delay %.0f -> %.0f s, departures %.0f -> %.0f%s", c.minutes, c.seeds,
                a.punctual(), b.punctual(), a.avgDelay(), b.avgDelay(), a.deps, b.deps, d.twinOk ? " §a[promotable]" : " §c[not promotable]");
    }

    private static String sign(int v) { return v > 0 ? "+" + v : String.valueOf(v); }

    private static void row(PlanData d, String what, double a, double b, String fmt, boolean higherBetter) {
        double dl = b - a;
        String good = Math.abs(dl) < 1e-6 ? "0" : (dl > 0) == higherBetter ? "+" : "-";
        d.lastTwin.add(new String[]{"TC", what, String.format(Locale.ROOT, fmt, a), String.format(Locale.ROOT, fmt, b), (dl > 0 ? "+" : "") + String.format(Locale.ROOT, fmt, dl), good});
    }

    // ---- predicted vs actual ------------------------------------------------------------------------------------------------
    private static void predict(World w, PlanData d, TwinSim.Result a, int minutes) {
        Map<String, Double> today = Analytics.get(w).today(w);
        long now = w.getTotalWorldTime();
        d.predictions.removeIf(r -> true);                                            // one live prediction at a time (the newest)
        for (String l : a.perLine.keySet())
            d.predictions.add(new String[]{String.valueOf(now), String.valueOf(minutes), l, String.format(Locale.ROOT, "%.2f", a.perHour(l)),
                    String.format(Locale.ROOT, "%.1f", a.lineDelay.getOrDefault(l, 0.0) / Math.max(1, a.perLine.get(l))),
                    String.valueOf(Analytics.v(today, "dep:" + l)), String.valueOf(Analytics.day(w))});
    }

    /** once a minute: predictions whose time has come are scored against the analytics */
    static void checkPredictions(World w) {
        PlanData d = PlanData.get(w);
        if (d.predictions.isEmpty()) return;
        long now = w.getTotalWorldTime();
        Map<String, Double> today = Analytics.get(w).today(w);
        for (Iterator<String[]> it = d.predictions.iterator(); it.hasNext(); ) {
            String[] p = it.next();
            long t0 = Long.parseLong(p[0]);
            int mins = Integer.parseInt(p[1]);
            if (now - t0 < mins * 1200L) continue;
            it.remove();
            if (Integer.parseInt(p[6]) != Analytics.day(w)) continue;                // the day rolled over: counters reset, can't score it
            double deps = Analytics.v(today, "dep:" + p[2]) - Double.parseDouble(p[5]);
            double actual = deps * 60 / Math.max(1, (now - t0) / 1200.0);
            double pred = Double.parseDouble(p[3]);
            double err = pred <= 0 ? 0 : (actual - pred) / pred * 100;
            d.accuracy.addFirst(new String[]{Recorder.clock(w, t0), p[2], p[3], String.format(Locale.ROOT, "%.2f", actual), p[4], String.format(Locale.ROOT, "%+.0f", err)});
            while (d.accuracy.size() > 30) d.accuracy.removeLast();
            d.markDirty();
        }
    }

    // ---- promote -----------------------------------------------------------------------------------------------------------
    public static String promote(World w, String who) {
        PlanData d = PlanData.get(w);
        if (!d.twinOk) return "§cNothing to promote - RUN the working case first (it must not be worse and must have no deadlock)";
        TwinSim.Model m = TwinSim.live(w);
        NBTTagCompound p = params(w);
        String line = line(w, m), seg = seg(w, m);
        RailwayData data = RailwayData.get(w);
        List<String> did = new ArrayList<>();
        int dwell = p.getInteger("dwell"), speed = p.getInteger("speed");
        if (dwell != 0 || speed != 0) {
            int n = 0;
            for (RailwayData.AutoTrain a : data.trains().values()) {
                if (!line.equals(a.line)) continue;
                if (dwell != 0) a.dwellSeconds = Math.max(5, a.dwellSeconds + dwell);
                if (speed != 0) a.maxKmh = Math.max(10, (int) Math.round(a.maxKmh * (1 + speed / 100.0)));
                n++;
            }
            data.markDirty();
            did.add(n + " train(s) on " + line + " retimed");
            Versions.commit(w, "lines", who, "PROMOTED from twin: " + line + (dwell != 0 ? " dwell " + sign(dwell) + " s" : "") + (speed != 0 ? " speed " + sign(speed) + " %" : ""));
        }
        if (p.getBoolean("cbtc")) {
            RailwayData.Line l = data.line(line);
            if (l != null) { l.movingBlock = true; data.markDirty(); did.add("CBTC on " + line); Versions.commit(w, "signalling", who, "PROMOTED from twin: CBTC on " + line); }
        }
        long[] ends = m.segments().get(seg);
        if (ends != null && (p.getInteger("close") > 0 || p.getInteger("tsr") > 0 || p.getBoolean("single"))) {
            BlockPos a = BlockPos.fromLong(ends[0]), b = BlockPos.fromLong(ends[1]);
            double mx = (a.getX() + b.getX()) / 2.0, mz = (a.getZ() + b.getZ()) / 2.0, half = Math.max(16, Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ()) / 2 - 12);
            if (p.getInteger("close") > 0) { TempWorks.add(w, PlanData.Kind.POSSESSION, seg, mx, a.getY(), mz, half, 0, 0, p.getInteger("close"), who); did.add("possession planned"); }
            if (p.getInteger("tsr") > 0) { TempWorks.add(w, PlanData.Kind.TSR, seg, mx, a.getY(), mz, half, Math.max(10, 60 * (100 - p.getInteger("tsr")) / 100), 0, 0, who); did.add("TSR planned"); }
            if (p.getBoolean("single")) {
                PlanData.Work k = TempWorks.add(w, PlanData.Kind.SINGLE_LINE, seg, a.getX(), a.getY(), a.getZ(), 6, 40, 0, 0, who);
                k.x2 = b.getX(); k.z2 = b.getZ();
                did.add("single-line working planned");
            }
        }
        for (String s : d.promotable) if (s.startsWith("ENGINEERS")) did.add(s.toLowerCase(Locale.ROOT));
        d.twinOk = false;
        d.markDirty();
        Recorder.event(w, "Twin case PROMOTED by " + who + ": " + String.join(", ", did));
        return did.isEmpty() ? "§7The working case changes nothing that can be promoted" : "§aPromoted: " + String.join(", ", did);
    }
}
