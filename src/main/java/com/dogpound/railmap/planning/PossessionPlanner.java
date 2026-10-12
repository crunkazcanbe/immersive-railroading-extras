package com.dogpound.railmap.planning;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.asset.AssetData;
import com.dogpound.railmap.incident.Analytics;
import com.dogpound.railmap.incident.TwinSim;
import com.dogpound.railmap.infra.InfraConfig;
import com.dogpound.railmap.infra.InfraData;
import com.dogpound.railmap.infra.RailFlaws;
import com.dogpound.railmap.ohle.Ohle;
import com.dogpound.railmap.ohle.OhleData;
import com.dogpound.railmap.track.TrackData;
import com.dogpound.railmap.weather.TileCulvert;
import com.dogpound.railmap.weather.TilePumpStation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Maintenance possession planner (her ChatGPT list batch 3, #45 - extends the temporary-works POSSESSION of #7). PLAN
 * a possession over a zone and the planner books the work that is due inside it, worst first: tamping, grinding,
 * ballast cleaning, re-sleepering and re-gauging from the track-condition model, rail flaws to grind / weld / re-rail and
 * other asset defects from the register, worn or torn contact wire to renew (OHLE - needs the section isolated and
 * earthed), blocked culverts and pumps due a service. It totals the gang-hours against the window, asks the digital
 * twin what closing the stretches through the zone costs the timetable (delay, lost departures, lines hit) and - BEST
 * WINDOW - picks the start hour with the least traffic from the analytics' hour-of-day profile.
 * <p>
 * When the possession is IN FORCE the gangs (InfraConfig.possessionGangs at a time) work the list: each task takes its
 * time and then really does its job to the railway; OHLE tasks wait (BLOCKED) until their section is dead and earthed.
 * If the remaining work can't fit the window, an OVERRUN RISK alarm goes up; whatever isn't done at hand-back is
 * DEFERRED. Plans live in InfraData by possession id; the console's PLAN > Possession page shows them.
 */
public final class PossessionPlanner {
    private PossessionPlanner() {}

    static final String[] STATES = {"waiting", "WORKING", "done", "BLOCKED", "deferred"};

    // ---- planning --------------------------------------------------------------------------------------------------------
    public static String plan(World w, double x, double y, double z, double r, long minutes, boolean best, String who) {
        r = Math.max(8, Math.min(256, r));
        minutes = Math.max(5, Math.min(1440, minutes));
        List<InfraData.Task> tasks = gather(w, x, z, r);
        long startIn = 0;
        String window = "now";
        if (best) { long[] b = bestWindow(w, minutes); startIn = b[0]; window = String.format(Locale.ROOT, "%02d:00 (least traffic: %d deps/h avg)", b[1], b[2]); }
        PlanData.Work k = TempWorks.add(w, PlanData.Kind.POSSESSION, "maint " + Math.round(x) + "," + Math.round(z), x, y, z, r, 0, startIn, minutes, who);
        InfraData d = InfraData.get(w);
        InfraData.Plan p = new InfraData.Plan();
        p.work = k.id;
        p.made = w.getTotalWorldTime();
        p.window = window;
        p.tasks.addAll(tasks);
        impact(w, p, x, z, r, minutes);
        d.plans.put(k.id, p);
        while (d.plans.size() > 12) d.plans.remove(d.plans.keySet().iterator().next());
        d.markDirty();
        double need = needSeconds(p) / InfraConfig.possessionGangs;
        String fit = need > minutes * 60 ? String.format(Locale.ROOT, "§c work needs %.0f min - it will NOT fit, %.0f min short", need / 60, need / 60 - minutes) : String.format(Locale.ROOT, "§a work %.0f of %d min", need / 60, minutes);
        Recorder.event(w, "Possession #" + k.id + " planned by " + who + ": " + tasks.size() + " tasks, window " + window);
        com.dogpound.railmap.api.QuestEvents.fire(w, null, new BlockPos(x, y, z), "possession_planned", "tasks", tasks.size(), "minutes", (int) minutes);
        return String.format(Locale.ROOT, "§bPossession #%d planned (%s): %d tasks,%s§b · timetable cost +%.0f s delay, %.0f deps lost%s", k.id, window, tasks.size(), fit, p.impactDelay, p.impactDeps,
                p.lines.isEmpty() ? "" : " (" + p.lines + ")");
    }

    static double needSeconds(InfraData.Plan p) { double s = 0; for (InfraData.Task t : p.tasks) if (t.state < 2 || t.state == 3) s += Math.max(0, t.secs - t.done); return s; }

    private static InfraData.Task task(String kind, String what, long pos, double secs, boolean ohle) {
        InfraData.Task t = new InfraData.Task();
        t.kind = kind; t.what = what; t.pos = pos; t.secs = secs * InfraConfig.possessionTaskScale; t.ohle = ohle;
        return t;
    }

    /** the work due inside the zone, worst first, capped at 40 tasks */
    static List<InfraData.Task> gather(World w, double x, double z, double r) {
        List<Object[]> found = new ArrayList<>();               // {priority, task}
        double r2 = r * r;
        for (Map.Entry<Long, TrackData.Seg> e : TrackData.get(w).segs.entrySet()) {
            BlockPos p = BlockPos.fromLong(e.getKey());
            if (sq(p.getX() + 0.5 - x) + sq(p.getZ() + 0.5 - z) > r2) continue;
            TrackData.Seg s = e.getValue();
            String at = p.getX() + "," + p.getZ();
            int geo = Math.max(TrackData.Metric.TOP.band(s.get(TrackData.Metric.TOP)), Math.max(TrackData.Metric.LINE.band(s.get(TrackData.Metric.LINE)), TrackData.Metric.TWIST.band(s.get(TrackData.Metric.TWIST))));
            if (geo >= 1) found.add(new Object[]{geo * 10.0, task("TAMP", "tamp " + at, e.getKey(), 40, false)});
            int b;
            if ((b = TrackData.Metric.SURFACE.band(s.get(TrackData.Metric.SURFACE))) >= 1) found.add(new Object[]{b * 9.0, task("GRIND", "grind " + at, e.getKey(), 30, false)});
            if ((b = TrackData.Metric.FOULING.band(s.get(TrackData.Metric.FOULING))) >= 1) found.add(new Object[]{b * 8.0, task("BALLAST", "clean ballast " + at, e.getKey(), 60, false)});
            if ((b = TrackData.Metric.SLEEPERS.band(s.get(TrackData.Metric.SLEEPERS))) >= 1) found.add(new Object[]{b * 8.5, task("RESLEEPER", "re-sleeper " + at, e.getKey(), 90, false)});
            if ((b = TrackData.Metric.GAUGE.band(s.get(TrackData.Metric.GAUGE))) >= 1) found.add(new Object[]{b * 9.5, task("REGAUGE", "re-gauge " + at, e.getKey(), 45, false)});
        }
        AssetData ad = AssetData.get(w);
        for (AssetData.Defect df : ad.defects) {
            if (df.stage < AssetData.DISCOVERED || df.stage >= AssetData.REPAIRED) continue;
            AssetData.Asset a = ad.byId(df.asset);
            if (a == null || sq(a.at().getX() + 0.5 - x) + sq(a.at().getZ() + 0.5 - z) > r2) continue;
            RailFlaws.Flaw f = RailFlaws.of(df.type);
            String kind = f == null ? "REPAIR" : f.remedy.equals("WELD") ? "WELD" : f.remedy.equals("GRIND") && df.seen <= 2.5 ? "GRIND OUT" : f.remedy.equals("GRIND") ? "WELD" : "RERAIL";
            double secs = kind.equals("RERAIL") ? 240 : kind.equals("WELD") ? 120 : kind.equals("GRIND OUT") ? 30 : 120;
            InfraData.Task t = task(kind, kind.toLowerCase(Locale.ROOT) + " defect #" + df.id + " (" + df.type + ")", a.pos, secs, false);
            t.note = "defect " + df.id;
            found.add(new Object[]{20 + df.seen / Math.max(1, df.crit) * 20, t});
        }
        OhleData od = OhleData.get(w);
        Set<Long> wires = new HashSet<>(od.damaged);
        for (Map.Entry<Long, Float> e : od.wear.entrySet()) if (e.getValue() >= 0.5f) wires.add(e.getKey());
        for (long l : wires) {
            BlockPos p = BlockPos.fromLong(l);
            if (sq(p.getX() + 0.5 - x) + sq(p.getZ() + 0.5 - z) > r2) continue;
            boolean down = od.damaged.contains(l);
            found.add(new Object[]{down ? 40.0 : 15 + od.wear.getOrDefault(l, 0f) * 10, task("RENEW WIRE", (down ? "replace torn wire " : "renew worn wire ") + Ohle.xyz(p), l, 25, true)});
        }
        for (TileCulvert c : TileCulvert.all(w))
            if (c.blocked() > 0.3 && sq(c.getPos().getX() + 0.5 - x) + sq(c.getPos().getZ() + 0.5 - z) <= r2)
                found.add(new Object[]{12 + c.blocked() * 10, task("CLEAR CULVERT", "clear culvert screen " + c.getPos().getX() + "," + c.getPos().getZ(), c.getPos().toLong(), 60, false)});
        for (TilePumpStation ps : TilePumpStation.all(w))
            if ((ps.minutes(0) >= 100 || ps.minutes(1) >= 100 || ps.faulted(0) || ps.faulted(1)) && sq(ps.getPos().getX() + 0.5 - x) + sq(ps.getPos().getZ() + 0.5 - z) <= r2)
                found.add(new Object[]{14.0, task("SERVICE PUMPS", "service pumps " + ps.getPos().getX() + "," + ps.getPos().getZ(), ps.getPos().toLong(), 120, false)});
        found.sort((a, b) -> Double.compare((Double) b[0], (Double) a[0]));
        List<InfraData.Task> out = new ArrayList<>();
        for (Object[] o : found) { if (out.size() >= 40) break; out.add((InfraData.Task) o[1]); }
        return out;
    }

    static double sq(double v) { return v * v; }

    /** {ticks until the best start, its clock hour, avg departures/h in it} from the hour-of-day traffic profile */
    static long[] bestWindow(World w, long minutes) {
        double[] dep = new double[24];
        int[] n = new int[24];
        for (Map.Entry<Long, Map<String, Double>> e : Analytics.get(w).hours().entrySet()) {
            int h = (int) ((e.getKey() + 6) % 24);
            dep[h] += Analytics.v(e.getValue(), "dep");
            n[h]++;
        }
        for (int h = 0; h < 24; h++) dep[h] = n[h] == 0 ? 0 : dep[h] / n[h];
        int hours = (int) Math.max(1, Math.min(24, Math.ceil(minutes * 1200 / 1000.0)));     // a game hour is 1000 ticks
        int best = 0;
        double bestSum = Double.MAX_VALUE;
        for (int s = 0; s < 24; s++) {
            double sum = 0;
            for (int i = 0; i < hours; i++) sum += dep[(s + i) % 24];
            if (sum < bestSum - 1e-9) { bestSum = sum; best = s; }
        }
        long wt = w.getWorldTime() % 24000, startWt = (long) Math.floorMod(best - 6, 24) * 1000;
        long in = Math.floorMod(startWt - wt, 24000L);
        return new long[]{in, best, Math.round(bestSum / hours)};
    }

    /** the twin's verdict on closing every stretch that runs through the zone for the window */
    static void impact(World w, InfraData.Plan p, double x, double z, double r, long minutes) {
        try {
            TwinSim.Model m = TwinSim.live(w);
            if (m.trains.isEmpty()) return;
            TwinSim.Case base = new TwinSim.Case(), c = TwinSim.copy(base);
            Set<String> lines = new TreeSet<>();
            for (Map.Entry<String, long[]> e : m.segments().entrySet()) {
                BlockPos a = BlockPos.fromLong(e.getValue()[0]), b = BlockPos.fromLong(e.getValue()[1]);
                if (TempWorks.distToSegment(x, z, a.getX(), a.getZ(), b.getX(), b.getZ()) > r + 8) continue;
                c.closeSeg.put(e.getKey(), new double[]{0, Math.min(minutes, base.minutes) * 60.0});
                for (Map.Entry<String, long[]> l : m.lines.entrySet()) for (long s : l.getValue()) if (s == e.getValue()[0] || s == e.getValue()[1]) lines.add(l.getKey());
            }
            if (c.closeSeg.isEmpty()) { p.lines = "no timetabled line through the zone"; return; }
            TwinSim.Result r0 = TwinSim.run(m, base), r1 = TwinSim.run(m, c);
            p.impactDelay = Math.max(0, r1.avgDelay() - r0.avgDelay());
            p.impactDeps = Math.max(0, r0.deps - r1.deps);
            p.lines = String.join(", ", lines);
        } catch (RuntimeException e) { p.lines = "twin unavailable"; }
    }

    // ---- the gangs at work ---------------------------------------------------------------------------------------------
    /** once a second (PlanTicker) */
    static void tick(World w, long now) {
        InfraData d = InfraData.get(w);
        if (d.plans.isEmpty()) return;
        PlanData pd = PlanData.get(w);
        boolean ch = false;
        for (InfraData.Plan p : d.plans.values()) {
            PlanData.Work k = null;
            for (PlanData.Work x : pd.works) if (x.id == p.work) k = x;
            if (k == null || k.phase == PlanData.Phase.REMOVING || k.phase == PlanData.Phase.CLEARED) {
                int def = 0;
                for (InfraData.Task t : p.tasks) if (t.state != 2 && t.state != 4) { t.state = 4; def++; }
                if (def > 0) { Recorder.event(w, "Possession #" + p.work + " handed back - " + def + " task(s) DEFERRED"); AlarmManager.get(w).clear("possover:" + p.work); ch = true; }
                continue;
            }
            if (!k.live()) continue;
            int working = 0;
            for (InfraData.Task t : p.tasks) if (t.state == 1) working++;
            for (InfraData.Task t : p.tasks) {
                if (t.state == 2 || t.state == 4) continue;
                if (t.ohle) {
                    boolean ok = ohleSafe(w, BlockPos.fromLong(t.pos));
                    if (!ok) { if (t.state != 3) { if (t.state == 1) working--; t.state = 3; t.note = "isolate + earth the OHLE section first"; ch = true; } continue; }
                    if (t.state == 3) { t.state = 0; t.note = ""; ch = true; }
                }
                if (t.state == 0 && working < InfraConfig.possessionGangs) { t.state = 1; working++; ch = true; }
                if (t.state != 1) continue;
                t.done += 1;
                if (t.done >= t.secs) { t.state = 2; t.note = apply(w, t); working--; ch = true; }
            }
            if (k.end > 0 && !p.overrunWarned && needSeconds(p) / InfraConfig.possessionGangs > (k.end - now) / 20.0) {
                p.overrunWarned = true;
                AlarmManager.get(w).raise(w, "possover:" + p.work, AlarmManager.Cat.GENERAL, AlarmManager.Sev.MINOR, String.format(Locale.ROOT,
                        "POSSESSION #%d OVERRUN RISK: %.0f min of work left, %.0f min of window - cut tasks or extend", p.work, needSeconds(p) / InfraConfig.possessionGangs / 60, (k.end - now) / 1200.0), new BlockPos(k.x, k.y, k.z), true);
                ch = true;
            }
        }
        if (ch) d.markDirty();
    }

    static boolean ohleSafe(World w, BlockPos wire) {
        boolean dead = true, earthed = false;
        for (Ohle.Section s : Ohle.around(w, wire)) { dead &= !s.energised; earthed |= s.earthed; }
        return dead && earthed;
    }

    /** the task's real effect on the railway */
    static String apply(World w, InfraData.Task t) {
        BlockPos p = BlockPos.fromLong(t.pos);
        TrackData td = TrackData.get(w);
        String done = "done";
        switch (t.kind) {
            case "TAMP": { TrackData.Seg s = td.seg(p); for (TrackData.Metric m : new TrackData.Metric[]{TrackData.Metric.TOP, TrackData.Metric.LINE, TrackData.Metric.TWIST}) s.set(m, m.fresh + (s.get(m) - m.fresh) * 0.25); s.lastWork = "TAMPED (possession)"; break; }
            case "GRIND": { TrackData.Seg s = td.seg(p); s.set(TrackData.Metric.SURFACE, Math.max(0, s.get(TrackData.Metric.SURFACE) - 25)); s.add(TrackData.Metric.RAIL, 0.05); s.lastWork = "RAIL GROUND (possession)"; break; }
            case "BALLAST": { TrackData.Seg s = td.seg(p); s.set(TrackData.Metric.FOULING, TrackData.Metric.FOULING.fresh); s.lastWork = "BALLAST CLEANED (possession)"; break; }
            case "RESLEEPER": { TrackData.Seg s = td.seg(p); s.set(TrackData.Metric.SLEEPERS, 0); s.lastWork = "RE-SLEEPERED (possession)"; break; }
            case "REGAUGE": { TrackData.Seg s = td.seg(p); s.set(TrackData.Metric.GAUGE, 0); s.lastWork = "RE-GAUGED (possession)"; break; }
            case "WELD": case "GRIND OUT": case "RERAIL": case "REPAIR": {
                int id = -1;
                try { id = Integer.parseInt(t.note.replace("defect ", "").trim()); } catch (NumberFormatException ignored) { }
                AssetData.Defect df = AssetData.get(w).defect(id);
                if (df != null && df.stage < AssetData.REPAIRED) RailFlaws.repaired(w, df, t.kind.toLowerCase(Locale.ROOT) + " by a possession gang", !t.kind.equals("REPAIR"));
                if (t.kind.equals("RERAIL")) { TrackData.Seg s = td.seg(p); s.set(TrackData.Metric.RAIL, 0); s.set(TrackData.Metric.SURFACE, 0); s.lastWork = "RE-RAILED (possession)"; }
                done = "defect " + id + " closed";
                break;
            }
            case "RENEW WIRE": {
                OhleData od = OhleData.get(w);
                od.wear.remove(t.pos);
                boolean was = od.damaged.remove(t.pos);
                od.faults.removeIf(f -> { boolean x = f.persistent && f.pos == t.pos; if (x) AlarmManager.get(w).clear("ohlef:" + f.pos); return x; });
                AlarmManager.get(w).clear("ohlew:" + t.pos);
                od.log(w, "possession gang renewed the contact wire at " + Ohle.xyz(p) + (was ? " (was torn down)" : ""));
                od.markDirty();
                Ohle.dirty();
                break;
            }
            case "CLEAR CULVERT":
                for (TileCulvert c : TileCulvert.all(w)) if (c.getPos().toLong() == t.pos) c.cleared("possession gang");
                break;
            case "SERVICE PUMPS":
                for (TilePumpStation ps : TilePumpStation.all(w)) if (ps.getPos().toLong() == t.pos) ps.serviced("possession gang");
                break;
            default:
        }
        td.markDirty();
        Recorder.event(w, "Possession work: " + t.what + " - " + done);
        return done;
    }

    /** re-scan the zone: tasks done / in hand stay, newly due work is added */
    public static String rescan(World w, int id) {
        InfraData d = InfraData.get(w);
        InfraData.Plan p = d.plans.get(id);
        if (p == null) return "§cNo possession plan #" + id;
        PlanData.Work k = null;
        for (PlanData.Work x : PlanData.get(w).works) if (x.id == id) k = x;
        if (k == null) return "§cPossession #" + id + " is gone";
        Set<String> have = new HashSet<>();
        for (InfraData.Task t : p.tasks) have.add(t.kind + "@" + t.pos);
        int added = 0;
        for (InfraData.Task t : gather(w, k.x, k.z, k.r)) if (have.add(t.kind + "@" + t.pos) && p.tasks.size() < 60) { p.tasks.add(t); added++; }
        d.markDirty();
        return "§b" + added + " new task(s) added to possession #" + id;
    }

    // ---- console ---------------------------------------------------------------------------------------------------------
    /** PP id · name · phase · window · tasks · done · blocked · work min left / window min · delay s · deps lost · lines ; PT id · kind · what · state · % · note */
    public static void rows(World w, List<String[]> out) {
        InfraData d = InfraData.get(w);
        long now = w.getTotalWorldTime();
        for (InfraData.Plan p : d.plans.values()) {
            PlanData.Work k = null;
            for (PlanData.Work x : PlanData.get(w).works) if (x.id == p.work) k = x;
            int done = 0, blocked = 0;
            for (InfraData.Task t : p.tasks) { if (t.state == 2) done++; if (t.state == 3) blocked++; }
            double left = k == null || k.end == 0 ? -1 : Math.max(0, (k.end - Math.max(now, k.start)) / 1200.0);
            out.add(new String[]{"PP", String.valueOf(p.work), k == null ? "-" : k.name, k == null ? "GONE" : k.phase.name(), p.window, String.valueOf(p.tasks.size()), String.valueOf(done), String.valueOf(blocked),
                    String.format(Locale.ROOT, "%.0f / %s", needSeconds(p) / InfraConfig.possessionGangs / 60, left < 0 ? "-" : String.format(Locale.ROOT, "%.0f", left)),
                    String.format(Locale.ROOT, "%.0f", p.impactDelay), String.format(Locale.ROOT, "%.0f", p.impactDeps), p.lines});
            for (InfraData.Task t : p.tasks)
                out.add(new String[]{"PT", String.valueOf(p.work), t.kind, t.what, STATES[t.state], String.valueOf(Math.round(Math.min(1, t.done / Math.max(1, t.secs)) * 100)), t.note});
        }
    }
}
