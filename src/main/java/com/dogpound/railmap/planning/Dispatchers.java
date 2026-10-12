package com.dogpound.railmap.planning;

import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.Disruptions;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.server.StationData;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Operating policies and AI dispatchers (her "next 48" list #46). Each REGION (an area, or a set of lines) has a
 * dispatcher with one GOAL, and acts only through the levers a real regulator has - how long a driverless train stands
 * at the stop it's at:
 * <ul>
 *   <li><b>PUNCTUALITY</b> - regularity: a train about to leave less than 70 % of the line's even headway behind the
 *   one before is HELD to even the gaps; a train that is late, or leaving a gap over 130 %, has its dwell CUT to the
 *   minimum to make time up;</li>
 *   <li><b>FREIGHT THROUGHPUT</b> - a passenger train about to leave in front of a freight train closing on the same
 *   stop is HELD so the freight keeps its path; freight trains' calls that aren't loading / unloading are CUT short;</li>
 *   <li><b>PROTECT MAINTENANCE</b> - a train whose next stretch runs through a possession / work window starting within
 *   3 minutes, or already in force, is HELD until it is over (so a late train can't eat into the engineers' time).</li>
 * </ul>
 * The SUPERVISOR collects every region's proposals each second; a train two regions disagree about (one holds, one
 * cuts) goes to the region with the higher priority (maintenance 3, punctuality 2, freight 1 by default); equal
 * priorities hold - the safe side. Every decision, and every one overruled, is logged with WHY. One decision per train per
 * stop. Regions are created switched OFF.
 */
public final class Dispatchers {
    private Dispatchers() {}

    private static final class Prop {
        final PlanData.Region region;
        final RailwayData.AutoTrain train;
        final int seconds;
        final String why;
        Prop(PlanData.Region r, RailwayData.AutoTrain t, int s, String w) { region = r; train = t; seconds = s; why = w; }
    }

    /** train -> the stop tick it was last decided at (one decision per stop) */
    private static final Map<UUID, Long> DECIDED = new HashMap<>();
    /** line + stop + direction -> when the last train left */
    private static final Map<String, Long> LAST_DEP = new HashMap<>();
    private static final Map<UUID, Boolean> WAS_DWELLING = new HashMap<>();

    static boolean covers(PlanData.Region r, RailwayData.AutoTrain a, double x, double z) {
        if (!r.lines.isEmpty()) {
            boolean hit = false;
            for (String l : r.lines.split(",")) hit |= l.trim().equalsIgnoreCase(a.line);
            if (!hit) return false;
        }
        return r.r <= 0 || Math.hypot(x - r.x, z - r.z) <= r.r;
    }

    static void tick(World w, long now) {
        PlanData d = PlanData.get(w);
        RailwayData data = RailwayData.get(w);
        // departures, for the headways
        for (RailwayData.AutoTrain a : data.trains().values()) {
            Autopilot.Run r = Autopilot.run(a.loco);
            boolean dw = r != null && r.dwelling;
            Boolean was = WAS_DWELLING.put(a.loco, dw);
            if (was != null && was && !dw && r != null) LAST_DEP.put(a.line + "|" + r.dwellStation + "|" + a.direction, now);
        }
        if (!PlanConfig.aiDispatchers || d.regions.isEmpty()) return;
        boolean any = false;
        for (PlanData.Region r : d.regions) any |= r.on;
        if (!any) return;
        // per line: how many trains, the even headway (from the twin's view of the line)
        Map<String, Integer> perLine = new HashMap<>();
        for (RailwayData.AutoTrain a : data.trains().values()) if (!a.line.isEmpty()) perLine.merge(a.line, 1, Integer::sum);
        List<Prop> props = new ArrayList<>();
        StationData sd = StationData.get(w);
        for (RailwayData.AutoTrain a : data.trains().values()) {
            Autopilot.Run run = Autopilot.run(a.loco);
            if (run == null || !run.dwelling) continue;
            Long dec = DECIDED.get(a.loco);
            if (dec != null && dec == run.dwellStation) continue;                       // already decided at this stop
            long left = Autopilot.dwellLeft(a.loco, now);
            if (left < 0 || left > 20 * 8) continue;                                    // decide in the last 8 s of the stop
            Locomotive l = Autopilot.find(w, a.loco);
            if (l == null) continue;
            Vec3d p = l.getPosition();
            String here = Autopilot.nameOf(sd, run.dwellStation);
            boolean freight = isFreight(data, a, l);
            for (PlanData.Region rg : d.regions) {
                if (!rg.on || !covers(rg, a, p.x, p.z)) continue;
                switch (rg.goal) {
                    case PUNCTUALITY: {
                        int n = perLine.getOrDefault(a.line, 1);
                        if (n < 2) break;
                        double cycle = cycleSeconds(w, data, a);
                        double even = cycle / n;
                        Long last = LAST_DEP.get(a.line + "|" + run.dwellStation + "|" + a.direction);
                        int late = Disruptions.delaySeconds(a.loco);
                        if (late > 30) { props.add(new Prop(rg, a, -60, String.format(Locale.ROOT, "%s is %d s late: dwell cut at %s to make time up", label(a), late, here))); break; }
                        if (last == null) break;
                        double gap = (now - last) / 20.0;
                        if (gap < even * 0.7) props.add(new Prop(rg, a, (int) Math.min(rg.maxHold, Math.round(even * 0.85 - gap)), String.format(Locale.ROOT,
                                "%s held at %s: only %.0f s behind the train before, even headway is %.0f s (%d trains)", label(a), here, gap, even, n)));
                        else if (gap > even * 1.3) props.add(new Prop(rg, a, -60, String.format(Locale.ROOT, "%s at %s: %.0f s gap (even %.0f s) - dwell cut to close it", label(a), here, gap, even)));
                        break;
                    }
                    case FREIGHT: {
                        if (freight) {
                            RailwayData.Order o = run.dwellStation == 0 ? RailwayData.Order.STOP : orderAt(data, a, run.dwellStation);
                            if (o == RailwayData.Order.STOP || o == RailwayData.Order.VIA)
                                props.add(new Prop(rg, a, -60, label(a) + " (freight) at " + here + ": no loading here - stop cut short"));
                            break;
                        }
                        RailwayData.AutoTrain f = freightClosing(w, data, a, run.dwellStation);
                        if (f != null) props.add(new Prop(rg, a, Math.min(rg.maxHold, 40), label(a) + " held at " + here + " so freight " + label(f) + " (closing on " + here + ") keeps its path"));
                        break;
                    }
                    case MAINTENANCE: {
                        PlanData.Work k = workAhead(w, d, data, a, run.dwellStation, now);
                        if (k != null) props.add(new Prop(rg, a, rg.maxHold, label(a) + " held at " + here + ": next stretch runs into " + TempWorks.label(k) + (k.live() ? " (in force)" : " (window starting)")));
                        break;
                    }
                }
            }
        }
        if (props.isEmpty()) return;
        // the supervisor
        Map<UUID, List<Prop>> by = new LinkedHashMap<>();
        for (Prop p : props) by.computeIfAbsent(p.train.loco, k -> new ArrayList<>()).add(p);
        for (List<Prop> ps : by.values()) {
            Prop win = ps.get(0);
            for (Prop p : ps) {
                int c = Integer.compare(p.region.priority, win.region.priority);
                if (c > 0 || c == 0 && p.seconds > win.seconds) win = p;               // equal priority: the longer hold (safe side)
            }
            Autopilot.Run run = Autopilot.run(win.train.loco);
            if (run == null) continue;
            DECIDED.put(win.train.loco, run.dwellStation);
            if (!Autopilot.adjustDwell(win.train.loco, now, win.seconds)) continue;
            win.region.decisions++;
            d.decide(w, "[" + win.region.name + " " + win.region.goal.label + "] " + (win.seconds > 0 ? "HOLD +" + win.seconds + " s: " : "CUT: ") + win.why);
            for (Prop p : ps) {
                if (p == win || p.seconds == win.seconds && p.region == win.region) continue;
                if (Integer.signum(p.seconds) == Integer.signum(win.seconds)) continue;
                p.region.overruled++;
                d.decide(w, "  supervisor overruled [" + p.region.name + " " + p.region.goal.label + "] (" + (p.seconds > 0 ? "hold" : "cut") + ": " + p.why + ") - priority " + p.region.priority + " < " + win.region.priority);
            }
        }
        d.markDirty();
    }

    private static String label(RailwayData.AutoTrain a) { return a.label.isEmpty() ? a.loco.toString().substring(0, 6) : a.label; }

    private static boolean isFreight(RailwayData data, RailwayData.AutoTrain a, Locomotive l) {
        RailwayData.Line ln = data.line(a.line);
        if (ln != null) for (int k = 0; k < ln.stations.size(); k++) { RailwayData.Order o = ln.order(k); if (o == RailwayData.Order.LOAD || o == RailwayData.Order.UNLOAD || o == RailwayData.Order.FULL_LOAD) return true; }
        try { return Autopilot.cargoUnits(l) > 0; } catch (RuntimeException e) { return false; }
    }

    private static RailwayData.Order orderAt(RailwayData data, RailwayData.AutoTrain a, long station) {
        RailwayData.Line ln = data.line(a.line);
        if (ln == null) return RailwayData.Order.STOP;
        int i = ln.stations.indexOf(station);
        return i < 0 ? RailwayData.Order.STOP : ln.order(i);
    }

    /** the line's round trip, seconds: booked runs + dwells */
    private static double cycleSeconds(World w, RailwayData data, RailwayData.AutoTrain a) {
        RailwayData.Line ln = data.line(a.line);
        if (ln == null || ln.stations.size() < 2) return 600;
        Map<String, Long> booked = com.dogpound.railmap.incident.Analytics.get(w).booked();
        double s = 0;
        int n = ln.stations.size();
        boolean shuttle = a.mode == RailwayData.Mode.SHUTTLE;
        for (int i = 0; i < (shuttle ? n - 1 : n); i++) {
            long f = ln.stations.get(i), t = ln.stations.get((i + 1) % n);
            Long b = booked.get(f + ">" + t);
            BlockPos p = BlockPos.fromLong(f), q = BlockPos.fromLong(t);
            s += (b != null ? b / 20.0 : Math.hypot(p.getX() - q.getX(), p.getZ() - q.getZ()) * 1.25 / Math.max(1, a.maxKmh / 3.6 * 0.7)) + a.dwellSeconds;
        }
        return shuttle ? s * 2 : s;
    }

    /** a freight train heading for this same stop within ~600 m */
    private static RailwayData.AutoTrain freightClosing(World w, RailwayData data, RailwayData.AutoTrain pax, long station) {
        for (RailwayData.AutoTrain f : data.trains().values()) {
            if (f == pax) continue;
            Autopilot.Run r = Autopilot.run(f.loco);
            if (r == null || r.dwelling || r.metresToStop < 0 || r.metresToStop > 600) continue;
            Locomotive l = Autopilot.find(w, f.loco);
            if (l == null || !isFreight(data, f, l)) continue;
            Long next = com.dogpound.railmap.auto.Dispatcher.nextStop(data, f);
            if (next != null && next == station) return f;
        }
        return null;
    }

    /** a possession / work window on the stretch to this train's next stop */
    private static PlanData.Work workAhead(World w, PlanData d, RailwayData data, RailwayData.AutoTrain a, long station, long now) {
        RailwayData.Line ln = data.line(a.line);
        if (ln == null) return null;
        int i = ln.stations.indexOf(station);
        if (i < 0) return null;
        int n = ln.stations.size();
        int j = a.mode == RailwayData.Mode.SHUTTLE ? Math.max(0, Math.min(n - 1, i + a.direction)) : Math.floorMod(i + a.direction, n);
        BlockPos p = BlockPos.fromLong(station), q = BlockPos.fromLong(ln.stations.get(j));
        for (PlanData.Work k : d.works) {
            if (k.kind != PlanData.Kind.POSSESSION) continue;
            boolean soon = k.phase == PlanData.Phase.PLANNED && k.start - now < 20 * 180 || k.phase == PlanData.Phase.INSTALLING || k.live();
            if (soon && TempWorks.distToSegment(k.x, k.z, p.getX(), p.getZ(), q.getX(), q.getZ()) <= k.r + 8) return k;
        }
        return null;
    }

    public static PlanData.Region region(World w, String name, PlanData.Goal goal, double x, double z, double r, String lines, String who) {
        PlanData d = PlanData.get(w);
        PlanData.Region g = null;
        for (PlanData.Region e : d.regions) if (e.name.equalsIgnoreCase(name)) g = e;
        if (g == null) { g = new PlanData.Region(); g.name = name; d.regions.add(g); }
        g.goal = goal; g.x = x; g.z = z; g.r = r; g.lines = lines == null ? "" : lines;
        g.priority = goal == PlanData.Goal.MAINTENANCE ? 3 : goal == PlanData.Goal.PUNCTUALITY ? 2 : 1;
        d.markDirty();
        Versions.commit(w, "dispatch", who, "region " + name + " set to " + goal.label);
        return g;
    }

    public static void clear() { DECIDED.clear(); LAST_DEP.clear(); WAS_DWELLING.clear(); }
}
