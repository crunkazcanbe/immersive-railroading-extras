package com.dogpound.railmap.safety;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.auto.TrackPath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Where signalling batch 3 plugs into the existing railway (one line at each call site, all guarded here so a fault in
 * the new gear can never break the old systems):
 * <ul>
 *   <li>auto.Wayside.read: speed restrictions along a path (balise telegrams, route-knowledge limits);</li>
 *   <li>auto.Protection / Autopilot: the cab display note (the last balise telegram);</li>
 *   <li>SafetyHolds.tick: signals held at STOP (maintenance isolation);</li>
 *   <li>RouteCheck.check: routes refused (isolated entrance / exit signal);</li>
 *   <li>RadioNet.tick: a train's radio reading this second (the coverage / dead-zone map);</li>
 *   <li>auto.Interlocking.setRoute: a route was set (route macro recording).</li>
 * </ul>
 */
public final class SigHooks {
    private SigHooks() {}

    private static boolean warned;

    private static void fail(String where, Throwable t) {
        if (!warned) { warned = true; RailMap.LOG.warn("[irextras] signalling hook {} failed: {}", where, t.toString()); }
    }

    /** {distance along the path, kmh, stop?, what, BlockPos where, signal?} */
    public static List<Object[]> restrictions(World w, TrackPath path) {
        List<Object[]> out = new ArrayList<>();
        try { SigA.restrictions(w, path, out); } catch (LinkageError | RuntimeException e) { fail("balise", e); }
        try { SigC.restrictions(w, path, out); } catch (LinkageError | RuntimeException e) { fail("route knowledge", e); }
        return out;
    }

    /** a line for the cab display of this loco (null = nothing to say) */
    public static String cabNote(UUID loco, long now) {
        try { return SigA.cabNote(loco, now); } catch (LinkageError | RuntimeException e) { fail("cab note", e); return null; }
    }

    public static void holds(World w, Map<Long, String> hold) {
        try { SigB.holds(w, hold); } catch (LinkageError | RuntimeException e) { fail("isolation holds", e); }
    }

    /** null = no objection; else why the route is refused */
    public static String routeRefusal(World w, BlockPos from, BlockPos to) {
        try { return SigB.routeRefusal(w, from, to); } catch (LinkageError | RuntimeException e) { fail("isolation routes", e); return null; }
    }

    public static void radio(World w, SafetyData.TrainRec r) {
        try { SigB.radio(w, r); } catch (LinkageError | RuntimeException e) { fail("radio coverage", e); }
    }

    /** a driver incident (SPAD, TRIP, AWS, OVERSPEED) for the competency records (#46) */
    public static void incident(World w, List<net.minecraft.entity.player.EntityPlayer> drivers, String kind, String text) {
        try { SigC.incident(w, drivers, kind, text); } catch (LinkageError | RuntimeException e) { fail("competency incident", e); }
    }

    public static void routeSet(World w, BlockPos start, BlockPos end) {
        try { SigM.routeSet(w, start, end); } catch (LinkageError | RuntimeException e) { fail("macro recording", e); }
    }
}
