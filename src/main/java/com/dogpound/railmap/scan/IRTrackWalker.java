package com.dogpound.railmap.scan;

import cam72cam.immersiverailroading.library.SwitchState;
import cam72cam.immersiverailroading.library.TrackItems;
import cam72cam.immersiverailroading.tile.TileRail;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.immersiverailroading.track.BuilderBase;
import cam72cam.immersiverailroading.track.IIterableTrack;
import cam72cam.immersiverailroading.track.VecYPR;
import cam72cam.immersiverailroading.util.RailInfo;
import cam72cam.immersiverailroading.util.SwitchUtil;
import cam72cam.mod.block.BlockEntity;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.world.World;
import com.dogpound.railmap.graph.RailNode;
import com.dogpound.railmap.graph.RailSegment;
import net.minecraft.util.math.BlockPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Walks Immersive Railroading's track graph outward from a board.
 * <p>
 * IR stores a track piece as one {@code TileRail} (holding {@code RailInfo}) plus many
 * {@code TileRailGag} blocks that each point back to it via {@code getParentTile()}. There is
 * no "next piece" pointer; pieces connect purely because their centre-lines meet end to end.
 * So the walk is:
 * <ol>
 *   <li>seed with every loaded {@code TileRail} within {@code seedRadius} (one pass over the
 *       world's loaded tile list, nearest first, no chunk loading);</li>
 *   <li>for each piece, ask IR for its centre-line
 *       ({@code info.getBuilder(world)} → {@link IIterableTrack#getPath}) — the same call
 *       IR's own train physics uses, so geometry matches what trains actually follow;</li>
 *   <li>probe just past each end of that line for a rail block, follow its parent chain
 *       (including {@code getReplacedTile()} for overlapping junctions) to the neighbouring
 *       {@code TileRail}, and enqueue it;</li>
 *   <li>stop at {@code budget} pieces.</li>
 * </ol>
 * Only loaded chunks are touched: a {@code getTileEntity} on an unloaded position would make
 * the server load (or generate!) that chunk, which is exactly the memory blow-up we avoid.
 */
public final class IRTrackWalker {
    private static final Logger LOG = LogManager.getLogger("RailMap");

    /** Sample spacing (blocks) asked of IR; then thinned to RailNode.MAX_POINTS. */
    private static final double PATH_STEP = 1.0;
    /** Distances past an endpoint to probe for the next piece; second one covers a 1-block gap. */
    private static final double[] PROBE_DIST = {0.6, 1.5};
    /** Slopes/embankments put the next piece a block up or down. */
    private static final int[] PROBE_DY = {0, 1, -1};
    /** Overlapping-junction "replaced" chains are short; this just stops a malformed loop. */
    private static final int MAX_REPLACED_DEPTH = 4;

    private final World world;
    private final int budget;
    private final Map<Long, Integer> idByPos = new HashMap<>();
    private final Set<Long> edgeKeys = new HashSet<>();
    private final List<RailNode> nodes = new ArrayList<>();
    private final List<RailSegment> segments = new ArrayList<>();
    private final ArrayDeque<TileRail> queue = new ArrayDeque<>();
    /** Edges whose far node had no id yet: [nodeId, farPosKey]. Resolved in finish(). */
    private final List<long[]> pending = new ArrayList<>();
    private boolean truncated;

    private IRTrackWalker(World world, int budget) {
        this.world = world;
        this.budget = budget;
    }

    public static final class Result {
        public final List<RailNode> nodes;
        public final List<RailSegment> segments;
        public final boolean truncated;
        /** TileRail position → node id, so the stop scanner can test "is this gag's parent on the map". */
        public final Map<Long, Integer> idByPos;

        Result(List<RailNode> nodes, List<RailSegment> segments, boolean truncated, Map<Long, Integer> idByPos) {
            this.nodes = nodes;
            this.segments = segments;
            this.truncated = truncated;
            this.idByPos = idByPos;
        }
    }

    public static Result walk(net.minecraft.world.World mcWorld, BlockPos origin, int seedRadius, int budget) {
        IRTrackWalker w = new IRTrackWalker(World.get(mcWorld), budget);
        w.seed(mcWorld, origin, seedRadius);
        w.run();
        return w.finish();
    }

    private void seed(net.minecraft.world.World mcWorld, BlockPos origin, int radius) {
        List<TileRail> seeds = new ArrayList<>();
        double r2 = (double) radius * radius;
        for (BlockEntity be : UmcTiles.loaded(mcWorld)) {
            if (be instanceof TileRail rail && rail.info != null
                    && distSq(rail.getPos(), origin) <= r2) {
                seeds.add(rail);
            }
        }
        // Nearest first so the budget is spent on the track around the board, not a far corner.
        seeds.sort(Comparator.comparingDouble(t -> distSq(t.getPos(), origin)));
        queue.addAll(seeds);
    }

    private static double distSq(Vec3i p, BlockPos o) {
        double dx = p.x - o.getX(), dy = p.y - o.getY(), dz = p.z - o.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private void run() {
        while (!queue.isEmpty()) {
            TileRail rail = queue.poll();
            long key = rail.getPos().toLong();
            if (idByPos.containsKey(key)) continue;
            if (nodes.size() >= budget) {
                truncated = true;
                break;
            }
            int before = nodes.size();
            try {
                visit(rail, key);
            } catch (Exception e) {
                // One odd tile (half-loaded chunk, custom pack quirk) must not kill the whole map.
                LOG.debug("[RailMap] skipping rail at {}: {}", rail.getPos(), e.toString());
                if (nodes.size() == before) idByPos.remove(key);
            }
        }
    }

    private void visit(TileRail rail, long key) {
        RailInfo info = rail.info;
        int id = nodes.size();
        idByPos.put(key, id);

        TrackItems type = info.settings.type;
        RailNode.Kind kind = kindOf(type);
        float[] points;
        BuilderBase builder = info.getBuilder(world);
        if (builder instanceof IIterableTrack iterable) {
            points = worldPath(rail, iterable.getPath(PATH_STEP));
        } else {
            points = null;
        }
        if (points == null || points.length < 3) {
            // Tables, crossings, anything exotic: a single marker at the piece's own position.
            Vec3i p = rail.getPos();
            points = new float[]{p.x + 0.5f, p.y, p.z + 0.5f};
        }

        // A TURN/CUSTOM whose parent is a SWITCH is that switch's diverging leg.
        int switchId = -1;
        byte swState = RailNode.SWITCH_NONE;
        TileRail parent = loadedParent(rail);
        boolean isLeg = parent != null && parent != rail && !parent.getPos().equals(rail.getPos())
                && parent.info != null && parent.info.settings.type == TrackItems.SWITCH
                && (type == TrackItems.TURN || type == TrackItems.CUSTOM);
        if (isLeg) {
            swState = stateOf(rail);
            Integer pid = idByPos.get(parent.getPos().toLong());
            if (pid == null) {
                queue.add(parent);
                // Parent not visited yet: the leg's switchId is patched when the parent is visited.
            } else {
                switchId = pid;
            }
            link(id, pid);
        }

        nodes.add(new RailNode(id, new BlockPos(rail.getPos().x, rail.getPos().y, rail.getPos().z),
                kind, info.placementInfo.yaw, info.settings.length,
                (int) Math.round(info.settings.gauge.value() * 1000), swState, switchId, points));

        if (kind == RailNode.Kind.SWITCH) {
            findSwitchLeg(rail, id);
        }

        // Neighbours past each end of the centre-line.
        if (points.length >= 6) {
            probeEnd(id, rail, points, true);
            probeEnd(id, rail, points, false);
        }
    }

    /**
     * The switch's turn leg starts at the same point as the straight leg, so end-probing never
     * finds it from the switch itself. Sample the leg's own centre-line (built from the switch's
     * RailInfo re-typed as TURN/CUSTOM, exactly how BuilderSwitch does it) and look there.
     */
    private void findSwitchLeg(TileRail sw, int swId) {
        RailInfo info = sw.info;
        boolean simpleTurn = info.customInfo.placementPosition.equals(info.placementInfo.placementPosition);
        RailInfo legInfo = info.withSettings(b -> b.type = simpleTurn ? TrackItems.TURN : TrackItems.CUSTOM);
        BuilderBase b = legInfo.getBuilder(world);
        if (!(b instanceof IIterableTrack iterable)) return;
        float[] leg = worldPath(sw, iterable.getPath(PATH_STEP));
        if (leg == null) return;
        int n = leg.length / 3;
        for (double frac : new double[]{0.5, 0.75, 0.25}) {
            int i = Math.min(n - 1, (int) (n * frac));
            TileRail t = railAt(new Vec3i(Math.floor(leg[i * 3]), Math.floor(leg[i * 3 + 1]), Math.floor(leg[i * 3 + 2])), sw);
            if (t == null) t = railAt(new Vec3i(Math.floor(leg[i * 3]), Math.floor(leg[i * 3 + 1]) - 1, Math.floor(leg[i * 3 + 2])), sw);
            if (t != null) {
                TileRail tp = loadedParent(t);
                if (tp != null && tp.getPos().equals(sw.getPos())) {
                    // Found the leg. Record the switch's state now so the SWITCH node carries it too.
                    byte state = stateOf(t);
                    RailNode me = nodes.get(swId);
                    nodes.set(swId, new RailNode(me.id, me.pos, me.kind, me.yaw, me.length, me.gaugeMm,
                            state, me.switchId, me.points));
                    Integer legId = idByPos.get(t.getPos().toLong());
                    if (legId != null) {
                        RailNode ln = nodes.get(legId);
                        nodes.set(legId, new RailNode(ln.id, ln.pos, ln.kind, ln.yaw, ln.length, ln.gaugeMm,
                                state, swId, ln.points));
                        link(swId, legId);
                    } else {
                        queue.add(t);
                    }
                    return;
                }
            }
        }
    }

    /** First TileRail reachable from the rail block at {@code p} (via parent chain) that isn't {@code self}. */
    private TileRail railAt(Vec3i p, TileRail self) {
        if (!world.isBlockLoaded(p)) return null;
        TileRailBase base = world.getBlockEntity(p, TileRailBase.class);
        if (base == null) return null;
        TileRail t = base instanceof TileRail tr ? tr : loadedParent(base);
        return t != null && t.info != null && !t.getPos().equals(self.getPos()) ? t : null;
    }

    private void probeEnd(int id, TileRail self, float[] pts, boolean start) {
        int n = pts.length / 3;
        int e = start ? 0 : n - 1;
        int inner = start ? 1 : n - 2;
        double ex = pts[e * 3], ey = pts[e * 3 + 1], ez = pts[e * 3 + 2];
        double dx = ex - pts[inner * 3], dz = ez - pts[inner * 3 + 2];
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-6) return;
        dx /= len;
        dz /= len;
        for (double d : PROBE_DIST) {
            for (int dy : PROBE_DY) {
                Vec3i p = new Vec3i(Math.floor(ex + dx * d), Math.floor(ey) + dy, Math.floor(ez + dz * d));
                if (!world.isBlockLoaded(p)) continue;
                TileRailBase base = world.getBlockEntity(p, TileRailBase.class);
                if (base == null) continue;
                boolean found = false;
                int depth = 0;
                for (TileRailBase cur = base; cur != null && depth < MAX_REPLACED_DEPTH; depth++) {
                    TileRail t = cur instanceof TileRail tr ? tr : loadedParent(cur);
                    if (t != null && t.info != null && !t.getPos().equals(self.getPos())) {
                        connect(id, t);
                        found = true;
                    }
                    cur = cur.getReplaced() != null ? cur.getReplacedTile() : null;
                }
                if (found) return; // nearest hit wins; further probes would only add false links
            }
        }
    }

    private void connect(int id, TileRail other) {
        Integer oid = idByPos.get(other.getPos().toLong());
        if (oid == null) {
            queue.add(other);
            // Edge is added when the other side is visited and probes back; if it can't (its own
            // probe misses) we still want the link, so remember it by position.
            pending.add(new long[]{id, other.getPos().toLong()});
        } else {
            link(id, oid);
        }
    }

    private void link(int a, Integer b) {
        if (b == null || a == b) return;
        RailSegment s = new RailSegment(a, b);
        if (edgeKeys.add(s.key())) segments.add(s);
    }

    private Result finish() {
        for (long[] p : pending) link((int) p[0], idByPos.get(p[1]));
        // Legs visited before their switch got switchId patched inside findSwitchLeg; legs whose
        // switch was never reached (budget) keep -1, which the GUI just draws as a plain curve.
        return new Result(nodes, segments, truncated, idByPos);
    }

    /** Parent tile, but only if its chunk is loaded (IR's getParentTile would load it). */
    private TileRail loadedParent(TileRailBase base) {
        Vec3i pp = base.getParent();
        if (pp == null || !world.isBlockLoaded(pp)) return null;
        return base.getParentTile();
    }

    private static byte stateOf(TileRail leg) {
        SwitchState s;
        try {
            s = SwitchUtil.getSwitchState(leg);
        } catch (RuntimeException e) {
            return RailNode.SWITCH_NONE; // IR's findSwitchParent NPEs on a half-loaded parent chain
        }
        return s == SwitchState.TURN ? RailNode.SWITCH_TURN
                : s == SwitchState.STRAIGHT ? RailNode.SWITCH_STRAIGHT : RailNode.SWITCH_NONE;
    }

    private static RailNode.Kind kindOf(TrackItems t) {
        switch (t) {
            case SWITCH: return RailNode.Kind.SWITCH;
            case SLOPE: return RailNode.Kind.SLOPE;
            case CROSSING: return RailNode.Kind.CROSSING;
            case TURNTABLE:
            case TRANSFERTABLE: return RailNode.Kind.TABLE;
            case STRAIGHT: return RailNode.Kind.STRAIGHT;
            default: return RailNode.Kind.CURVE; // TURN, TURN_V2, CUBICPARABOLA, CUSTOM
        }
    }

    /**
     * IR's path is relative to {@code placementPosition + tilePos} (see MovementTrack.nextPositionDirect).
     * Thinned to at most MAX_POINTS by index; straights only ever need their two ends.
     */
    private static float[] worldPath(TileRail rail, List<VecYPR> path) {
        if (path == null || path.isEmpty()) return null;
        Vec3d center = rail.info.placementInfo.placementPosition.add(rail.getPos());
        TrackItems type = rail.info.settings.type;
        boolean straight = type == TrackItems.STRAIGHT || type == TrackItems.SLOPE || type == TrackItems.SWITCH;
        int n = path.size();
        int keep = straight ? Math.min(2, n) : Math.min(RailNode.MAX_POINTS, n);
        float[] out = new float[keep * 3];
        for (int k = 0; k < keep; k++) {
            int i = keep == 1 ? 0 : (int) Math.round((double) k * (n - 1) / (keep - 1));
            VecYPR v = path.get(i);
            out[k * 3] = (float) (center.x + v.x);
            out[k * 3 + 1] = (float) (center.y + v.y);
            out[k * 3 + 2] = (float) (center.z + v.z);
        }
        return out;
    }
}
