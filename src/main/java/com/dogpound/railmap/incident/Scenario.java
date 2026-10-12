package com.dogpound.railmap.incident;

import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.server.StationData;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Operations scenario testing (her ChatGPT list #80): SAVE the railway's state (every driverless train: its line,
 * where it is along it, which way, its dwell and top speed; every line's stops; the booked stop-to-stop running times
 * the analytics learned), then try an alternative - a fault closing a stop for N minutes, longer / shorter dwells,
 * faster / slower running, trains added to or taken off a line - and RUN it: the saved state is played forward
 * scenarioMinutes twice, once as it is and once with the changes, and the results compared (departures, average and
 * worst delay, minutes trains spent held, departures per hour per line). The live world is never touched.
 * The model: trains run stop to stop on their booked times, dwell, and may not leave a stop less than
 * scenarioHeadwaySeconds behind the train before them in the same direction (the signalling); a closed stop holds
 * every train that wants to leave it or run into it. Replay of what really happened is the console's REWIND tab.
 */
public final class Scenario {
    private Scenario() {}

    /** snapshot the live railway into the saved slot */
    public static String save(World w) {
        Analytics an = Analytics.get(w);
        RailwayData data = RailwayData.get(w);
        NBTTagCompound s = new NBTTagCompound();
        NBTTagList lines = new NBTTagList();
        for (RailwayData.Line l : data.lines().values()) {
            if (l.stations.size() < 2) continue;
            NBTTagCompound c = new NBTTagCompound();
            c.setString("n", l.name);
            StringBuilder st = new StringBuilder();
            for (long k : l.stations) st.append(st.length() == 0 ? "" : ",").append(k);
            c.setString("s", st.toString());
            lines.appendTag(c);
        }
        s.setTag("lines", lines);
        NBTTagList trains = new NBTTagList();
        for (RailwayData.AutoTrain a : data.trains().values()) {
            if (a.mode != RailwayData.Mode.LINE && a.mode != RailwayData.Mode.SHUTTLE || a.line.isEmpty()) continue;
            Autopilot.Run r = Autopilot.run(a.loco);
            NBTTagCompound c = new NBTTagCompound();
            c.setString("line", a.line); c.setInteger("idx", a.index); c.setInteger("dir", a.direction); c.setBoolean("shuttle", a.mode == RailwayData.Mode.SHUTTLE);
            c.setInteger("dwell", a.dwellSeconds); c.setInteger("kmh", a.maxKmh);
            c.setBoolean("at", r != null && r.dwelling);
            c.setDouble("toGo", r == null ? -1 : r.metresToStop);
            c.setString("name", a.label.isEmpty() ? a.loco.toString().substring(0, 6) : a.label);
            trains.appendTag(c);
        }
        s.setTag("trains", trains);
        NBTTagCompound b = new NBTTagCompound();
        for (Map.Entry<String, Long> e : an.booked.entrySet()) b.setLong(e.getKey(), e.getValue());
        s.setTag("booked", b);
        s.setLong("tick", w.getTotalWorldTime());
        s.setTag("p", an.snapshot.getCompoundTag("p"));                         // keep the scenario settings
        s.setTag("res", new NBTTagList());
        an.snapshot = s;
        an.markDirty();
        return "Railway state saved: " + lines.tagCount() + " lines, " + trains.tagCount() + " trains";
    }

    // ---- scenario settings ---------------------------------------------------------------------------------------
    public static NBTTagCompound params(World w) {
        Analytics an = Analytics.get(w);
        if (!an.snapshot.hasKey("p")) an.snapshot.setTag("p", new NBTTagCompound());
        return an.snapshot.getCompoundTag("p");
    }

    public static String set(World w, String what, int delta) {
        NBTTagCompound p = params(w);
        List<Long> stops = stops(w);
        switch (what) {
            case "fault": { int n = stops.size() + 1; p.setInteger("fault", Math.floorMod(p.getInteger("fault") + delta, n)); break; }
            case "faultmin": p.setInteger("faultmin", Math.max(0, Math.min(240, p.getInteger("faultmin") + delta))); break;
            case "dwell": p.setInteger("dwell", Math.max(-60, Math.min(300, p.getInteger("dwell") + delta))); break;
            case "speed": p.setInteger("speed", Math.max(-50, Math.min(100, p.getInteger("speed") + delta))); break;
            case "line": { int n = Math.max(1, lineNames(w).size()); p.setInteger("line", Math.floorMod(p.getInteger("line") + delta, n)); break; }
            case "trains": p.setInteger("trains", Math.max(-10, Math.min(10, p.getInteger("trains") + delta))); break;
            case "reset": for (String k : new ArrayList<>(p.getKeySet())) p.removeTag(k); break;
            default: return null;
        }
        Analytics.get(w).markDirty();
        return describe(w);
    }

    public static String describe(World w) {
        NBTTagCompound p = params(w);
        List<Long> stops = stops(w);
        List<String> ln = lineNames(w);
        int f = p.getInteger("fault");
        String stop = f == 0 || f > stops.size() ? "none" : Autopilot.nameOf(StationData.get(w), stops.get(f - 1));
        return "fault at " + stop + (f == 0 ? "" : " for " + p.getInteger("faultmin") + " min") + ", dwell " + sign(p.getInteger("dwell")) + " s, speed " + sign(p.getInteger("speed"))
                + " %, trains " + sign(p.getInteger("trains")) + " on " + (ln.isEmpty() ? "-" : ln.get(Math.min(ln.size() - 1, p.getInteger("line"))));
    }

    private static String sign(int v) { return v > 0 ? "+" + v : String.valueOf(v); }

    private static List<Long> stops(World w) {
        Set<Long> s = new LinkedHashSet<>();
        NBTTagList lines = Analytics.get(w).snapshot.getTagList("lines", 10);
        for (int i = 0; i < lines.tagCount(); i++) for (long k : longs(lines.getCompoundTagAt(i).getString("s"))) s.add(k);
        return new ArrayList<>(s);
    }

    private static List<String> lineNames(World w) {
        List<String> o = new ArrayList<>();
        NBTTagList lines = Analytics.get(w).snapshot.getTagList("lines", 10);
        for (int i = 0; i < lines.tagCount(); i++) o.add(lines.getCompoundTagAt(i).getString("n"));
        return o;
    }

    // ---- the run -------------------------------------------------------------------------------------------------
    private static final class Result {
        int deps;
        int trains;
        double delay, held;
        final Map<String, Integer> perLine = new TreeMap<>();
    }

    public static String run(World w) {
        Analytics an = Analytics.get(w);
        NBTTagCompound s = an.snapshot;
        if (!s.hasKey("trains")) return "Save the railway state first";
        NBTTagCompound p = params(w);
        Result base = sim(w, s, new NBTTagCompound());
        Result alt = sim(w, s, p);
        NBTTagList res = new NBTTagList();
        int mins = IncidentConfig.scenarioMinutes;
        addRow(res, "Departures", base.deps, alt.deps, "%.0f");
        addRow(res, "Average delay (s)", base.deps == 0 ? 0 : base.delay / base.deps, alt.deps == 0 ? 0 : alt.delay / alt.deps, "%.0f");
        addRow(res, "Wait per train (s)", base.held / Math.max(1, base.trains), alt.held / Math.max(1, alt.trains), "%.0f");
        addRow(res, "Train-minutes held", base.held / 60, alt.held / 60, "%.1f");
        Set<String> ls = new TreeSet<>(base.perLine.keySet());
        ls.addAll(alt.perLine.keySet());
        for (String l : ls) addRow(res, l + " deps/hour", base.perLine.getOrDefault(l, 0) * 60.0 / mins, alt.perLine.getOrDefault(l, 0) * 60.0 / mins, "%.1f");
        s.setTag("res", res);
        s.setString("resFor", describe(w));
        s.setLong("ranAt", w.getTotalWorldTime());
        an.markDirty();
        return String.format(Locale.ROOT, "Scenario run over %d min: delay %.0f s -> %.0f s, departures %d -> %d", mins,
                base.deps == 0 ? 0 : base.delay / base.deps, alt.deps == 0 ? 0 : alt.delay / alt.deps, base.deps, alt.deps);
    }

    private static void addRow(NBTTagList l, String what, double a, double b, String fmt) {
        NBTTagCompound c = new NBTTagCompound();
        c.setString("w", what); c.setString("a", String.format(Locale.ROOT, fmt, a)); c.setString("b", String.format(Locale.ROOT, fmt, b));
        double d = b - a;
        c.setString("d", (d > 0 ? "+" : "") + String.format(Locale.ROOT, fmt, d));
        l.appendTag(c);
    }

    /** the saved state and the scenario settings, run on the digital twin's shadow simulation (TwinSim, one engine for both) */
    private static Result sim(World w, NBTTagCompound s, NBTTagCompound p) {
        TwinSim.Model m = new TwinSim.Model();
        NBTTagList ll = s.getTagList("lines", 10);
        for (int i = 0; i < ll.tagCount(); i++) m.lines.put(ll.getCompoundTagAt(i).getString("n"), longs(ll.getCompoundTagAt(i).getString("s")));
        NBTTagCompound b = s.getCompoundTag("booked");
        for (String k : b.getKeySet()) m.booked.put(k, b.getLong(k));
        StationData sd = StationData.get(w);
        for (long[] st : m.lines.values()) for (long k : st) m.names.put(k, Autopilot.nameOf(sd, k));
        NBTTagList tl = s.getTagList("trains", 10);
        for (int i = 0; i < tl.tagCount(); i++) {
            NBTTagCompound c = tl.getCompoundTagAt(i);
            long[] st = m.lines.get(c.getString("line"));
            if (st == null) continue;
            TwinSim.Train t = new TwinSim.Train();
            t.line = c.getString("line"); t.name = c.getString("name"); t.st = st;
            t.idx = Math.floorMod(c.getInteger("idx"), st.length); t.dir = c.getInteger("dir") < 0 ? -1 : 1; t.shuttle = c.getBoolean("shuttle");
            t.dwell = c.getInteger("dwell"); t.kmh = c.getInteger("kmh"); t.at = c.getBoolean("at");
            double toGo = c.getDouble("toGo");
            t.left = t.at ? t.dwell : toGo >= 0 ? toGo / Math.max(1, t.kmh / 3.6 * 0.7) : 30;
            m.trains.add(t);
        }
        com.dogpound.railmap.planning.PlanData.get(w).applyTracks(m);
        TwinSim.Case c = new TwinSim.Case();
        c.minutes = IncidentConfig.scenarioMinutes;
        c.headway = IncidentConfig.scenarioHeadwaySeconds;
        if (p.getInteger("dwell") != 0) c.dwellDelta.put("*", p.getInteger("dwell"));
        if (p.getInteger("speed") != 0) c.speedPct.put("*", p.getInteger("speed"));
        List<String> ln = new ArrayList<>(m.lines.keySet());
        if (p.getInteger("trains") != 0 && !ln.isEmpty()) c.trainsDelta.put(ln.get(Math.min(ln.size() - 1, p.getInteger("line"))), p.getInteger("trains"));
        List<Long> stops = new ArrayList<>(new LinkedHashSet<Long>() {{ for (long[] a : m.lines.values()) for (long k : a) add(k); }});
        int f = p.getInteger("fault");
        if (f > 0 && f <= stops.size() && p.getInteger("faultmin") > 0) c.closeStop.put(m.name(stops.get(f - 1)), new double[]{0, p.getInteger("faultmin") * 60.0});
        TwinSim.Result tr = TwinSim.run(m, c);
        Result r = new Result();
        r.deps = (int) Math.round(tr.deps);
        r.trains = (int) tr.trains;
        r.delay = tr.delay;
        r.held = tr.held + tr.platformWait;
        for (Map.Entry<String, Double> e : tr.perLine.entrySet()) r.perLine.put(e.getKey(), (int) Math.round(e.getValue()));
        return r;
    }

    private static long[] longs(String s) {
        if (s.isEmpty()) return new long[0];
        String[] p = s.split(",");
        long[] o = new long[p.length];
        for (int i = 0; i < p.length; i++) try { o[i] = Long.parseLong(p[i].trim()); } catch (NumberFormatException ignored) { }
        return o;
    }

}
