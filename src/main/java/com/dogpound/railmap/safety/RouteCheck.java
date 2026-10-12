package com.dogpound.railmap.safety;

import cam72cam.immersiverailroading.library.SwitchState;
import cam72cam.immersiverailroading.tile.TileRail;
import cam72cam.mod.math.Vec3i;
import com.dogpound.railmap.auto.Interlocking;
import com.dogpound.railmap.auto.TrackPath;
import com.dogpound.railmap.graph.TrainNode;
import com.dogpound.railmap.server.TrainTracker;
import com.dogpound.railmap.signal.TileSignalMast;
import com.dogpound.railmap.signal.TileTrackCircuit;
import com.dogpound.railmap.signal.TrackFollower;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * The interlocking's safety case (her ChatGPT list #28): before a route is lined, every condition a real interlocking
 * proves is checked and written down, so the signaller can see exactly why a route was refused - or why it was granted:
 * <ul>
 *   <li>POWER - entrance and exit signals have their 12 V;</li>
 *   <li>POINT DETECTION - every set of points on the route is detected in a position (none jammed mid-stroke);</li>
 *   <li>CONFLICTS - no other route is set over the same points the other way or over the same track;</li>
 *   <li>OCCUPANCY - no train on the route, no occupied track circuit on it, no axle counter section on it that
 *   isn't CLEAR (occupied or disturbed);</li>
 *   <li>OVERLAP - the safety margin past the exit signal ({@code overlap} m) is clear of trains;</li>
 *   <li>LINE BLOCKED - no possession or operated emergency plunger covering the route;</li>
 *   <li>INTEGRITY - no divided train / unconfirmed rear end on the route (fail-safe INTEGRITY).</li>
 * </ul>
 * The first failed check is the refusal the signaller sees; the full list is on the console's SAFETY / Diagnostics
 * page, in {@code /irextras safety diag}, and printed by the Fault Lab.
 */
public final class RouteCheck {
    private RouteCheck() {}

    /** null = every check passed; else the first failure (the route is refused) */
    public static String check(World w, TileSignalMast from, TileSignalMast to, TrackPath path) {
        SafetyData.Decision dec = new SafetyData.Decision();
        dec.tick = w.getTotalWorldTime();
        dec.route = SignalSighting.name(from) + " > " + SignalSighting.name(to);
        dec.from = from.getPos().toLong();
        dec.to = to.getPos().toLong();
        dec.path = ConflictView.line(path);
        List<String> fails = new ArrayList<>();
        // power
        check(dec, fails, from.term().powered(), "POWER: entrance signal has 12 V", "MISSING POWER: entrance signal " + SignalSighting.name(from) + " has no 12 V (" + from.term().what + ")");
        if (!from.term().powered()) ConflictView.blocker(dec, "POWER", from.getPos(), "entrance signal " + SignalSighting.name(from) + " dark: " + from.term().what);
        dec.lines.add(to.term().powered() ? "+ POWER: exit signal has 12 V" : "~ POWER: exit signal " + SignalSighting.name(to) + " is dark (no 12 V) - it will show STOP");
        // batch-3 #18: signals isolated for maintenance
        String iso = SigHooks.routeRefusal(w, from.getPos(), to.getPos());
        if (iso != null) {
            fails.add("SIGNAL ISOLATED: " + iso);
            dec.lines.add("- " + fails.get(fails.size() - 1));
            ConflictView.blocker(dec, "ISOLATED", iso.contains(SignalSighting.name(to)) && !iso.contains(SignalSighting.name(from)) ? to.getPos() : from.getPos(), iso);
        }
        // points
        if (path.switches.isEmpty()) dec.lines.add("~ POINTS: none on this route");
        for (Map.Entry<Vec3i, SwitchState> e : path.switches.entrySet()) {
            BlockPos p = new BlockPos(e.getKey().x, e.getKey().y, e.getKey().z);
            String bad = pointCheck(w, p);
            check(dec, fails, bad == null, "POINTS " + xz(p) + " detected, wanted " + e.getValue().name(), "FAILED POINT DETECTION: points at " + xz(p) + " " + bad);
            if (bad != null) ConflictView.blocker(dec, "POINTS", p, "points " + xz(p) + " " + bad);
        }
        // conflicting routes
        Set<Vec3i> mine = new HashSet<>();
        for (int i = 1; i < path.steps.size() - 1; i++) mine.add(path.steps.get(i).pos);   // routes may meet end to end at a signal
        boolean conflict = false;
        for (Interlocking.Route r : Interlocking.routes(w)) {
            String other = Geo.signalName(r.start) + " > " + Geo.signalName(r.end);
            for (Map.Entry<Vec3i, SwitchState> e : path.switches.entrySet()) {
                SwitchState theirs = r.path.switches.get(e.getKey());
                if (theirs != null && theirs != e.getValue()) {
                    fails.add("CONFLICTING ROUTE: " + other + " holds the points at " + e.getKey().x + "," + e.getKey().z + " " + theirs.name());
                    dec.lines.add("- " + fails.get(fails.size() - 1));
                    ConflictView.blocker(dec, "ROUTE", new BlockPos(e.getKey().x, e.getKey().y, e.getKey().z), "route " + other + " holds these points " + theirs.name(), ConflictView.line(r.path));
                    conflict = true;
                }
            }
            for (TrackPath.Step s : r.path.steps) if (mine.contains(s.pos)) {
                fails.add("CONFLICTING ROUTE: " + other + " is set over the same track at " + s.pos.x + "," + s.pos.z);
                dec.lines.add("- " + fails.get(fails.size() - 1));
                ConflictView.blocker(dec, "ROUTE", new BlockPos(s.pos.x, s.pos.y, s.pos.z), "route " + other + " set over this track", ConflictView.line(r.path));
                conflict = true;
                break;
            }
        }
        // switches locked the other way by something that isn't a lined route (a driverless train's claim, a desk)
        for (Map.Entry<Vec3i, SwitchState> e : path.switches.entrySet()) {
            String owner = Interlocking.lockedAgainst(w, e.getKey(), e.getValue());
            if (owner == null || owner.startsWith("route@")) continue;
            String who = ConflictView.owner(w, owner);
            fails.add("LOCKED POINTS: points at " + e.getKey().x + "," + e.getKey().z + " locked the other way by " + who);
            dec.lines.add("- " + fails.get(fails.size() - 1));
            ConflictView.blocker(dec, "LOCKED", new BlockPos(e.getKey().x, e.getKey().y, e.getKey().z), "locked the other way by " + who);
            conflict = true;
        }
        if (!conflict) dec.lines.add("+ CONFLICTS: no other route or lock over this track or its points");
        // occupancy
        boolean occ = false;
        for (TrainNode t : FaultLab.withPhantoms(w, TrainTracker.latest(w))) {
            if (Geo.onPath(path, t.x, t.y, t.z, 1.0)) {
                fails.add("OCCUPIED SECTION: " + (t.id < 0 ? "false occupancy (fault lab)" : t.displayName()) + " at " + Math.round(t.x) + "," + Math.round(t.z));
                dec.lines.add("- " + fails.get(fails.size() - 1));
                ConflictView.blocker(dec, "SECTION", t.x, t.z, (t.id < 0 ? "false occupancy (fault lab)" : t.displayName()) + " on the route");
                occ = true;
                break;
            }
        }
        for (TileTrackCircuit tc : SafetyTicker.circuits(w)) {
            BlockPos p = tc.getPos();
            if (!Geo.onPath(path, p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, 2.5)) continue;
            if (!tc.term().powered() && !FaultLab.has(w, FaultLab.Kind.TC_DROP, p)) { dec.lines.add("~ track circuit at " + xz(p) + " has no 12 V - not in service, not proved"); continue; }
            if (tc.occupied()) {
                fails.add("OCCUPIED SECTION: track circuit at " + xz(p) + " shows occupied");
                dec.lines.add("- " + fails.get(fails.size() - 1));
                ConflictView.blocker(dec, "SECTION", p, "track circuit " + xz(p) + " occupied");
                occ = true;
            }
        }
        for (TileAxleEvaluator e : SafetyTicker.evaluators(w)) {
            for (int i = 0; i < TileAxleEvaluator.N; i++) {
                if (e.sec[i].heads.isEmpty() || e.clear(i)) continue;
                for (long h : e.sec[i].heads.keySet()) {
                    BlockPos p = BlockPos.fromLong(h);
                    if (Geo.onPath(path, p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 3)) {
                        fails.add("OCCUPIED SECTION: " + e.sectionLine(i));
                        dec.lines.add("- " + fails.get(fails.size() - 1));
                        ConflictView.blocker(dec, "SECTION", p, "axle section " + (i + 1) + ": " + e.sectionLine(i));
                        occ = true;
                        break;
                    }
                }
            }
        }
        if (!occ) dec.lines.add("+ OCCUPANCY: route clear (trains, track circuits, axle sections)");
        // overlap past the exit signal
        lastOverlapAt = null;
        String ov = overlap(w, to);
        check(dec, fails, ov == null || ov.startsWith("~"), "OVERLAP: " + SafetyData.get(w).overlap + " m past the exit signal is clear", ov == null ? "" : "INSUFFICIENT OVERLAP: " + ov);
        if (ov != null && ov.startsWith("~")) dec.lines.add(ov);
        if (ov != null && !ov.startsWith("~")) {
            if (lastOverlapAt != null) ConflictView.blocker(dec, "OVERLAP", lastOverlapAt[0], lastOverlapAt[1], "in the overlap: " + ov);
            else ConflictView.blocker(dec, "OVERLAP", to.getPos(), "overlap past " + SignalSighting.name(to) + ": " + ov);
        }
        // possessions / plungers
        boolean blocked = false;
        for (TilePossessionSwitch p : SafetyTicker.possessions(w))
            if (p.blocking() && Geo.pathInZone(path, p.getPos().getX() + 0.5, p.getPos().getZ() + 0.5, p.zone())) {
                fails.add("LINE BLOCKED: possession by " + p.holder() + " at " + xz(p.getPos()));
                dec.lines.add("- " + fails.get(fails.size() - 1));
                ConflictView.blocker(dec, "LINE", p.getPos(), "possession by " + p.holder() + " (" + p.zone() + " m)");
                blocked = true;
            }
        for (TileEmergencyPlunger p : SafetyTicker.plungers(w))
            if (p.operated() && Geo.pathInZone(path, p.getPos().getX() + 0.5, p.getPos().getZ() + 0.5, p.zone())) {
                fails.add("LINE BLOCKED: emergency stop plunger operated at " + xz(p.getPos()));
                dec.lines.add("- " + fails.get(fails.size() - 1));
                ConflictView.blocker(dec, "LINE", p.getPos(), "emergency plunger operated (" + p.zone() + " m)");
                blocked = true;
            }
        if (!blocked) dec.lines.add("+ LINE: no possession or emergency stop on the route");
        // integrity
        boolean integ = false;
        if (FailSafe.safe(w, FailSafe.INTEGRITY)) {
            Map<String, List<double[]>> cars = ConsistWatch.cars(w);
            for (SafetyData.TrainRec r : SafetyData.get(w).trains.values()) {
                if (r.gone || r.integrity == 0) continue;
                for (double[] c : cars.getOrDefault(r.id, Collections.emptyList())) {
                    if (!Geo.onPath(path, c[0], c[2], c[1], 4)) continue;
                    fails.add("TRAIN INTEGRITY: " + r.headcode + " " + (r.integrity == 2 ? "is divided / incomplete" : "rear end not confirmed") + " on the route");
                    dec.lines.add("- " + fails.get(fails.size() - 1));
                    ConflictView.blocker(dec, "INTEGRITY", c[0], c[1], r.headcode + " " + (r.integrity == 2 ? "divided" : "rear not confirmed"));
                    integ = true;
                    break;
                }
            }
        }
        if (!integ) dec.lines.add("+ INTEGRITY: no incomplete train on the route");
        SafetyData d = SafetyData.get(w);
        if (d.testMode) dec.lines.add("~ NOTE: the fault lab is in TEST MODE - injected faults count as real");
        dec.granted = fails.isEmpty();
        dec.id = ConflictView.nextId++;
        d.decisions.add(0, dec);
        while (d.decisions.size() > 24) d.decisions.remove(d.decisions.size() - 1);
        if (!dec.granted) com.dogpound.railmap.alarm.Recorder.event(w, "ROUTE REFUSED " + dec.route + ": " + fails.get(0));
        return fails.isEmpty() ? null : "Route refused - " + fails.get(0) + (fails.size() > 1 ? " (+" + (fails.size() - 1) + " more: console SAFETY > Routes, shown on the map)" : " (shown on the map)");
    }

    /** batch-3 #15: where overlap() found something in the overlap (x, z), memory only */
    private static double[] lastOverlapAt;

    private static void check(SafetyData.Decision dec, List<String> fails, boolean ok, String pass, String fail) {
        if (ok) dec.lines.add("+ " + pass);
        else { fails.add(fail); dec.lines.add("- " + fail); }
    }

    /** null when the points are detected in a position; else why not */
    public static String pointCheck(World w, BlockPos p) {
        if (FaultLab.stuck(w, p)) return "jammed mid-stroke (fault lab) - no detection";
        String pm = com.dogpound.railmap.track.TrackHooks.detection(w, p);   // #14 the point machine's own detection
        if (pm != null) return pm;
        if (!w.isBlockLoaded(p)) return "not loaded - no detection";
        return Interlocking.stateAt(w, p) == SwitchState.NONE ? "not detected in either position" : null;
    }

    /** null = overlap clear; "~ note" = shortened but acceptable; else what is in it */
    static String overlap(World w, TileSignalMast to) {
        int need = SafetyData.get(w).overlap;
        if (need <= 0) return null;
        Vec3i g = to.governedRail();
        if (g == null) return "~ OVERLAP: exit signal not beside track - not checked";
        cam72cam.mod.world.World umc = cam72cam.mod.world.World.get(w);
        TileRail rail = umc.getBlockEntity(g, TileRail.class);
        if (rail == null) return "~ OVERLAP: track past the exit not loaded";
        EnumFacing f = to.facing();
        TrackFollower tf = new TrackFollower(umc, need);
        TrackFollower.Span s = tf.follow(rail, new Vec3i(g.x - f.getXOffset(), g.y, g.z - f.getZOffset()));
        double len = 0;
        List<TrainNode> trains = FaultLab.withPhantoms(w, TrainTracker.latest(w));
        while (s != null) {
            for (TrainNode t : trains) if (s.distSqTo(t.x, t.z) <= 2.2 * 2.2) { lastOverlapAt = new double[]{t.x, t.z}; return (t.id < 0 ? "false occupancy" : t.displayName()) + " within " + Math.round(len + s.length) + " m past the exit signal (need " + need + " m clear)"; }
            len += s.length;
            if (len >= need) return null;
            if (s.end == TrackFollower.Span.End.DEAD_END) return "~ OVERLAP: shortened to " + Math.round(len) + " m by the end of the line";
            TrackFollower.Span n = tf.continueFrom(s);
            if (n == null || n.length < 0.5) return "~ OVERLAP: shortened to " + Math.round(len) + " m by the end of the line";
            s = n;
        }
        return null;
    }

    static String xz(BlockPos p) { return p.getX() + "," + p.getZ(); }
}
