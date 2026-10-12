package com.dogpound.railmap.track;

import cam72cam.immersiverailroading.library.SwitchState;
import cam72cam.immersiverailroading.tile.TileRail;
import com.dogpound.railmap.auto.TrackPath;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * The few places the rest of IR Extras asks the track package (her list #13-18). Every hook is cheap when nothing of
 * ours is placed, and can only make the railway MORE restrictive - never clear a signal or move points by itself.
 * <ul>
 *   <li>{@link #throwVeto} - SwitchControl / Interlocking before they force a switch: a point machine on it decides;</li>
 *   <li>{@link #detection} - RouteCheck: the machine's own detection (no detection while moving, failed, cranked);</li>
 *   <li>{@link #crossingDemand} - TileCrossing: a constant-warning-time predictor drives the crossings it controls;</li>
 *   <li>{@link #holds} - SafetyHolds: tunnel emergencies, failed / obstructed crossings, structure hazards hold signals;</li>
 *   <li>{@link #restrictions} - Wayside: TSRs on poor track and restricted structures, for driverless trains;</li>
 *   <li>{@link #tractionIsolated} - for server.Electric to call: a tunnel emergency asks for its traction to go off.</li>
 * </ul>
 */
public final class TrackHooks {
    private TrackHooks() {}

    private static final Map<Integer, Set<TileEntity>> LIVE = new HashMap<>();

    static void add(TileEntity t) { LIVE.computeIfAbsent(t.getWorld().provider.getDimension(), k -> Collections.newSetFromMap(new IdentityHashMap<>())).add(t); }
    static void remove(TileEntity t) { Set<TileEntity> s = LIVE.get(t.getWorld().provider.getDimension()); if (s != null) s.remove(t); }

    /** loaded track tiles of one kind in this world */
    public static <T> List<T> live(World w, Class<T> c) {
        Set<TileEntity> s = LIVE.get(w.provider.getDimension());
        if (s == null || s.isEmpty()) return Collections.emptyList();
        List<T> out = new ArrayList<>();
        for (TileEntity t : s) if (c.isInstance(t) && !t.isInvalid()) out.add(c.cast(t));
        return out;
    }

    // ---- 14 point machines ------------------------------------------------------------------------------------------

    /**
     * Before anything forces a switch: null = go ahead (a machine, if fitted, has started its stroke and will report
     * no detection until it ends); else why the points won't move. remote = the interlocking / console / lever frame.
     */
    public static String throwVeto(World w, BlockPos anyRail, SwitchState want, boolean remote) {
        if (w == null || w.isRemote || want == null || want == SwitchState.NONE) return null;
        List<TilePointMachine> ms = live(w, TilePointMachine.class);
        if (ms.isEmpty()) return null;
        TileRail sw = Rails.switchOf(w, anyRail);
        if (sw == null) return null;
        TilePointMachine m = TilePointMachine.on(ms, Rails.pos(sw));
        return m == null ? null : m.requestThrow(want, remote, null);
    }

    /**
     * batch-3 #6 movable-point diamonds: the interlocking wants this diamond made for a road (1 east-west, 2 north-south).
     * null = no machine on it, or its noses are set and detected for that road; else why not yet (a stroke starts).
     */
    public static String diamondVeto(World w, BlockPos diamond, int road) {
        if (w == null || w.isRemote) return null;
        List<TilePointMachine> ms = live(w, TilePointMachine.class);
        if (ms.isEmpty()) return null;
        TilePointMachine m = TilePointMachine.on(ms, diamond);
        if (m == null || !m.diamond()) return null;
        String r = m.requestThrow(road == 2 ? SwitchState.TURN : SwitchState.STRAIGHT, true, null);
        if (r != null) return r;
        return m.detectionFault();
    }

    /** RouteCheck: null = the points are detected where they lie; else the machine's reason (no detection) */
    public static String detection(World w, BlockPos anyRail) {
        if (w == null || w.isRemote) return null;
        List<TilePointMachine> ms = live(w, TilePointMachine.class);
        if (ms.isEmpty()) return null;
        TileRail sw = Rails.switchOf(w, anyRail);
        if (sw == null) return null;
        TilePointMachine m = TilePointMachine.on(ms, Rails.pos(sw));
        return m == null ? null : m.detectionFault();
    }

    // ---- 17 crossings -----------------------------------------------------------------------------------------------

    /** null = the crossing works on its own approach distance; else a predictor's verdict (true = warn / lower) */
    public static Boolean crossingDemand(World w, BlockPos crossing) {
        if (w == null || w.isRemote) return null;
        if (com.dogpound.railmap.ems.Ems.crossingClosed(w, crossing)) return true;   // police on scene closed it (#6)
        for (TileCrossingPredictor p : live(w, TileCrossingPredictor.class)) {
            Boolean d = p.demand(crossing);
            if (d != null) return d;
        }
        return null;
    }

    // ---- signal holds (SafetyHolds.tick, once a second) -------------------------------------------------------------

    /** add our holds: per-signal (hold: signal pos -> why) and zones ({x, z, radius, why}) */
    public static void holds(World w, Map<Long, String> hold, List<Object[]> zones) {
        if (w == null || w.isRemote) return;
        for (TileTunnelControl t : live(w, TileTunnelControl.class)) t.holds(hold, zones);
        for (TileCrossingPredictor p : live(w, TileCrossingPredictor.class)) p.holds(hold, zones);
        for (TileStructureBoard s : live(w, TileStructureBoard.class)) s.holds(hold, zones);
        for (TileSingleLine s : live(w, TileSingleLine.class)) s.holds(hold, zones);              // batch-3 #7 single line / gauntlet
        for (TileTurntableControl t : live(w, TileTurntableControl.class)) t.holds(hold, zones);   // batch 3 #3: signals round the pit
        for (TileMovableBridge b : live(w, TileMovableBridge.class)) b.holds(hold, zones);         // batch 3 #8: bridge not closed + locked
        com.dogpound.railmap.newrail6.NR6Ticker.holds(w, hold, zones);                            // batch 6: ferry link span, motorail plate, avalanche detectors
    }

    // ---- speed restrictions -----------------------------------------------------------------------------------------

    /** TSR (km/h) on this piece of track from its condition or a structure, 0 = none */
    public static int tsrAt(World w, BlockPos piece) {
        int lim = 0;
        if (TrackConfig.trackSpeedRestrictions) {
            TrackData.Seg s = TrackData.get(w).peek(piece);
            if (s != null) lim = s.tsr();
        }
        if (TrackConfig.trackStandardLimits) {                                // batch-3 #10: the standard's line speed (PSR)
            TrackData.Seg s = TrackData.get(w).peek(piece);
            int ls = s == null ? TrackData.Standard.BALLASTED.lineKmh : s.standard().lineKmh;
            if (lim == 0 || ls < lim) lim = ls;
        }
        for (TileStructureBoard b : live(w, TileStructureBoard.class)) {
            int l = b.limitAt(piece);
            if (l > 0 && (lim == 0 || l < lim)) lim = l;
        }
        return lim;
    }

    public static String tsrWhy(World w, BlockPos piece) {
        for (TileStructureBoard b : live(w, TileStructureBoard.class)) if (b.limitAt(piece) > 0) return b.limitWhy();
        TrackData.Seg s = TrackData.get(w).peek(piece);
        if (TrackConfig.trackStandardLimits && (s == null || s.tsr() == 0)) { TrackData.Standard st = s == null ? TrackData.Standard.BALLASTED : s.standard(); return st.label.toLowerCase(java.util.Locale.ROOT) + " track line speed"; }
        if (s == null) return "restriction";
        TrackData.Metric m = s.worst();
        return "poor track: " + m.label + " " + TrackCondition.fmt(s.get(m)) + " " + m.unit;
    }

    /** Wayside: {distance, kmh, what, where} for every TSR along a driverless train's path */
    public static List<Object[]> restrictions(World w, TrackPath path) {
        if (w == null || w.isRemote || path == null || path.steps.isEmpty()) return Collections.emptyList();
        TrackData d = TrackData.get(w);
        boolean structures = !live(w, TileStructureBoard.class).isEmpty();
        if (d.segs.isEmpty() && !structures) return Collections.emptyList();
        List<Object[]> out = new ArrayList<>();
        int last = -1;
        for (int i = 0; i < path.steps.size() && out.size() < 24; i++) {
            TrackPath.Step st = path.steps.get(i);
            BlockPos p = new BlockPos(st.pos.x, st.pos.y, st.pos.z);
            int lim = tsrAt(w, p);
            if (lim <= 0) { last = -1; continue; }
            if (lim == last) continue;                                   // same restriction carries on
            last = lim;
            double dist = i == 0 ? 0 : path.steps.get(i - 1).distance;
            out.add(new Object[]{dist, (double) lim, "TSR " + lim + " km/h - " + tsrWhy(w, p), p});
        }
        return out;
    }

    /** Wayside: places a driverless train must stand short of (batch-3 #7 single-line sections): {distance, 0.0, true, why, where} */
    public static List<Object[]> stops(World w, TrackPath path) {
        if (w == null || w.isRemote || path == null || path.steps.isEmpty()) return Collections.emptyList();
        List<TileSingleLine> sl = live(w, TileSingleLine.class);
        if (sl.isEmpty()) return Collections.emptyList();
        List<Object[]> out = new ArrayList<>();
        for (TileSingleLine s : sl) s.stops(path, out);
        return out;
    }

    // ---- 16 tunnel emergencies --------------------------------------------------------------------------------------

    /** true when a tunnel emergency has asked for the traction supply at (x, z) to be isolated (for server.Electric) */
    public static boolean tractionIsolated(World w, double x, double z) {
        if (w == null || w.isRemote) return false;
        for (TileTunnelControl t : live(w, TileTunnelControl.class)) if (t.isolating() && t.inZone(x, z)) return true;
        return false;
    }
}
