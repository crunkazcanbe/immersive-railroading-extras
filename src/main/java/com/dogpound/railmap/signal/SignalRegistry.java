package com.dogpound.railmap.signal;

import cam72cam.mod.math.Vec3i;
import cam72cam.mod.world.World;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where the signalling hardware is, per dimension. Tiles add themselves on load and drop out
 * on unload, so the engine never has to search chunks to find a signal or a joint.
 */
public final class SignalRegistry {
    private static final Map<Integer, Set<Long>> JOINTS = new HashMap<>();
    private static final Map<Integer, Map<Long, TileSignalMast>> MASTS = new HashMap<>();
    private static final Map<Integer, List<TileCrossing>> CROSSINGS = new HashMap<>();
    private static final Map<Integer, List<TileSignalBridge>> BRIDGES = new HashMap<>();

    private SignalRegistry() {}

    // ---- insulated joints ----------------------------------------------------------------

    public static void addJoint(int dim, BlockPos pos) {
        JOINTS.computeIfAbsent(dim, d -> new HashSet<>()).add(pos.toLong());
    }

    public static void removeJoint(int dim, BlockPos pos) {
        Set<Long> s = JOINTS.get(dim);
        if (s != null) s.remove(pos.toLong());
    }

    /**
     * True when a joint sits beside this track piece. Joints go on the ground NEXT to the rail:
     * replacing the block under Immersive Railroading track breaks the track. Standard gauge is
     * three blocks wide and rolling stock clears small blocks inside its own width, so the joint goes
     * in the first free row outside the train: up to three blocks from the centre line.
     */
    public static boolean hasJoint(World world, Vec3i railPos) {
        Set<Long> s = JOINTS.get(world.getId());
        if (s == null || s.isEmpty()) return false;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (s.contains(new BlockPos(railPos.x + dx, railPos.y + dy, railPos.z + dz).toLong())) return true;
                }
            }
        }
        return false;
    }

    // ---- signal masts --------------------------------------------------------------------

    public static void addMast(int dim, TileSignalMast mast) {
        MASTS.computeIfAbsent(dim, d -> new HashMap<>()).put(mast.getPos().toLong(), mast);
    }

    public static void removeMast(int dim, BlockPos pos) {
        Map<Long, TileSignalMast> m = MASTS.get(dim);
        if (m != null) m.remove(pos.toLong());
    }

    public static int joints(int dim) {
        Set<Long> s = JOINTS.get(dim);
        return s == null ? 0 : s.size();
    }

    public static List<TileSignalMast> masts(int dim) {
        Map<Long, TileSignalMast> m = MASTS.get(dim);
        return m == null ? Collections.<TileSignalMast>emptyList() : new ArrayList<>(m.values());
    }

    /** A mast governing this track piece, if any — used to end a block at a facing signal. */
    public static TileSignalMast mastAt(int dim, Vec3i railPos) {
        Map<Long, TileSignalMast> m = MASTS.get(dim);
        if (m == null || m.isEmpty()) return null;
        for (TileSignalMast mast : m.values()) {
            Vec3i g = mast.governedRail();
            if (g != null && g.equals(railPos)) return mast;
        }
        return null;
    }

    // ---- crossings -----------------------------------------------------------------------

    public static void addCrossing(int dim, TileCrossing c) {
        CROSSINGS.computeIfAbsent(dim, d -> new ArrayList<>()).add(c);
    }

    public static void removeCrossing(int dim, TileCrossing c) {
        List<TileCrossing> l = CROSSINGS.get(dim);
        if (l != null) l.remove(c);
    }

    public static List<TileCrossing> crossings(int dim) {
        List<TileCrossing> l = CROSSINGS.get(dim);
        return l == null ? Collections.<TileCrossing>emptyList() : new ArrayList<>(l);
    }

    // ---- signal bridges ------------------------------------------------------------------

    public static void addBridge(int dim, TileSignalBridge b) {
        BRIDGES.computeIfAbsent(dim, d -> new ArrayList<>()).add(b);
    }

    public static void removeBridge(int dim, TileSignalBridge b) {
        List<TileSignalBridge> l = BRIDGES.get(dim);
        if (l != null) l.remove(b);
    }

    public static List<TileSignalBridge> bridges(int dim) {
        List<TileSignalBridge> l = BRIDGES.get(dim);
        return l == null ? Collections.<TileSignalBridge>emptyList() : new ArrayList<>(l);
    }

    // ---- speed signs ---------------------------------------------------------------------

    private static final Map<Integer, List<TileSpeedSign>> SPEED_SIGNS = new HashMap<>();

    public static void addSpeedSign(int dim, TileSpeedSign s) {
        List<TileSpeedSign> l = SPEED_SIGNS.computeIfAbsent(dim, d -> new ArrayList<>());
        if (!l.contains(s)) l.add(s);
    }

    public static void removeSpeedSign(int dim, TileSpeedSign s) {
        List<TileSpeedSign> l = SPEED_SIGNS.get(dim);
        if (l != null) l.remove(s);
    }

    public static List<TileSpeedSign> speedSigns(int dim) {
        List<TileSpeedSign> l = SPEED_SIGNS.get(dim);
        return l == null ? Collections.<TileSpeedSign>emptyList() : new ArrayList<>(l);
    }

    public static void clear() {
        JOINTS.clear();
        MASTS.clear();
        CROSSINGS.clear();
        BRIDGES.clear();
        SPEED_SIGNS.clear();
    }
}
