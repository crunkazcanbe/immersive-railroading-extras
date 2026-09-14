package com.dogpound.railmap.signal;

import cam72cam.immersiverailroading.library.SwitchState;
import cam72cam.immersiverailroading.library.TrackItems;
import cam72cam.immersiverailroading.tile.TileRail;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.immersiverailroading.track.BuilderBase;
import cam72cam.immersiverailroading.track.IIterableTrack;
import cam72cam.immersiverailroading.track.VecYPR;
import cam72cam.immersiverailroading.util.RailInfo;
import cam72cam.immersiverailroading.util.SwitchUtil;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.world.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Walks Immersive Railroading track in ONE direction — the thing signalling needs and
 * {@link com.dogpound.railmap.scan.IRTrackWalker} (which floods outward) doesn't do.
 * <p>
 * Starting from a track piece and a heading, it follows the centre-line piece to piece,
 * taking the route a train would take: at a switch it obeys the current points position.
 * It stops at a block boundary — an {@link BlockInsulatedJoint insulated joint}, a facing
 * signal, the end of track, or a length budget.
 */
public final class TrackFollower {
    /** Probe distances past a piece end, mirroring IRTrackWalker's neighbour search. */
    private static final double[] PROBE_DIST = { 0.6, 1.5 };
    private static final int[] PROBE_DY = { 0, 1, -1 };
    private static final double PATH_STEP = 1.0;

    /** One block's worth of followed track. */
    public static final class Span {
        /** Track piece positions in order, for occupancy testing. */
        public final List<Vec3i> pieces = new ArrayList<>();
        /** Centre-line points [x,y,z,...] over the whole span, world coords. */
        public final List<double[]> points = new ArrayList<>();
        /** Total length walked, blocks. */
        public double length;
        /** True if the span ended because a switch ahead is set against / diverging. */
        public boolean diverging;
        /** Where the walk stopped, so the next span can continue from here. */
        public TileRail endRail;
        public Vec3i endFrom;
        /** Reason the span ended. */
        public End end = End.BUDGET;

        public enum End { JOINT, SIGNAL, DEAD_END, BUDGET, LOOP }

        public boolean containsPiece(Vec3i p) {
            for (Vec3i q : pieces) if (q.equals(p)) return true;
            return false;
        }

        /** Nearest distance (squared, xz) from a world point to this span's centre-line. */
        public double distSqTo(double x, double z) {
            double best = Double.MAX_VALUE;
            for (double[] p : points) {
                for (int i = 3; i < p.length; i += 3) {
                    best = Math.min(best, segDistSq(x, z, p[i - 3], p[i - 1], p[i], p[i + 2]));
                }
                if (p.length == 3) {
                    double dx = p[0] - x, dz = p[2] - z;
                    best = Math.min(best, dx * dx + dz * dz);
                }
            }
            return best;
        }
    }

    private final World world;
    private final double budget;
    private final Set<Long> visited = new HashSet<>();

    public TrackFollower(World world, double budget) {
        this.world = world;
        this.budget = budget;
    }

    /**
     * Follow from {@code rail}, leaving the end nearest {@code awayFrom} behind, until a block
     * boundary. Returns the span; {@link Span#endRail} carries on where it left off.
     */
    public Span follow(TileRail rail, Vec3i cameFrom) {
        Span span = new Span();
        TileRail cur = rail;
        Vec3i from = cameFrom;
        while (cur != null && span.length < budget) {
            long key = cur.getPos().toLong();
            if (!visited.add(key)) {
                span.end = Span.End.LOOP;
                break;
            }
            double[] path = worldPath(cur);
            if (path != null) {
                span.points.add(path);
                span.length += pathLength(path);
            }
            span.pieces.add(cur.getPos());
            span.endRail = cur;
            span.endFrom = from;

            // A joint or a facing signal on this piece closes the block.
            if (SignalRegistry.hasJoint(world, cur.getPos())) {
                span.end = Span.End.JOINT;
                break;
            }

            TileRail next = nextPiece(cur, from, path, span);
            if (next == null) {
                if (span.end == Span.End.BUDGET) span.end = Span.End.DEAD_END;
                break;
            }
            from = cur.getPos();
            cur = next;
        }
        return span;
    }

    /** Continue past a boundary into the following block. */
    public Span continueFrom(Span prev) {
        if (prev.endRail == null) return null;
        double[] path = worldPath(prev.endRail);
        Span probe = new Span();
        TileRail next = nextPiece(prev.endRail, prev.endFrom, path, probe);
        if (next == null) return null;
        return follow(next, prev.endRail.getPos());
    }

    // ---- stepping -----------------------------------------------------------------------

    /**
     * The piece a train would enter next: probe past the far end of this piece's centre-line
     * (the end away from where we came from), then at a switch pick the leg the points allow.
     */
    private TileRail nextPiece(TileRail rail, Vec3i cameFrom, double[] path, Span span) {
        if (path == null || path.length < 6) return probeAround(rail, cameFrom);
        int n = path.length / 3;
        // Which end did we come from? Take the far one.
        boolean fromStart = cameFrom == null
                || dist2(path[0], path[2], cameFrom.x + 0.5, cameFrom.z + 0.5)
                   <= dist2(path[(n - 1) * 3], path[(n - 1) * 3 + 2], cameFrom.x + 0.5, cameFrom.z + 0.5);
        int e = fromStart ? n - 1 : 0;
        int inner = fromStart ? n - 2 : 1;
        double ex = path[e * 3], ey = path[e * 3 + 1], ez = path[e * 3 + 2];
        double dx = ex - path[inner * 3], dz = ez - path[inner * 3 + 2];
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-6) return probeAround(rail, cameFrom);
        dx /= len;
        dz /= len;

        List<TileRail> found = new ArrayList<>(2);
        for (double d : PROBE_DIST) {
            for (int dy : PROBE_DY) {
                Vec3i p = new Vec3i(Math.floor(ex + dx * d), Math.floor(ey) + dy, Math.floor(ez + dz * d));
                TileRail t = railAt(p, rail, cameFrom);
                if (t != null && !contains(found, t)) found.add(t);
            }
            if (!found.isEmpty()) break;
        }
        if (found.isEmpty()) return null;
        if (found.size() == 1) return found.get(0);
        return pickByPoints(found, span);
    }

    /**
     * Every piece a train could enter after {@code rail}, leaving {@code cameFrom} behind —
     * both legs at a junction, regardless of which way the points lie. The route finder
     * uses this to try each leg; {@link #follow} uses the points instead.
     */
    public List<TileRail> candidatesAhead(TileRail rail, Vec3i cameFrom) {
        double[] path = worldPath(rail);
        List<TileRail> found = new ArrayList<>(2);
        if (path == null || path.length < 6) {
            TileRail t = probeAround(rail, cameFrom);
            if (t != null) found.add(t);
            return found;
        }
        int n = path.length / 3;
        boolean fromStart = cameFrom == null
                || dist2(path[0], path[2], cameFrom.x + 0.5, cameFrom.z + 0.5)
                   <= dist2(path[(n - 1) * 3], path[(n - 1) * 3 + 2], cameFrom.x + 0.5, cameFrom.z + 0.5);
        int e = fromStart ? n - 1 : 0;
        int inner = fromStart ? n - 2 : 1;
        double ex = path[e * 3], ey = path[e * 3 + 1], ez = path[e * 3 + 2];
        double dx = ex - path[inner * 3], dz = ez - path[inner * 3 + 2];
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-6) return found;
        dx /= len;
        dz /= len;
        for (double d : PROBE_DIST) {
            for (int dy : PROBE_DY) {
                Vec3i p = new Vec3i(Math.floor(ex + dx * d), Math.floor(ey) + dy, Math.floor(ez + dz * d));
                TileRail t = railAt(p, rail, cameFrom);
                if (t != null && !contains(found, t)) found.add(t);
            }
            if (!found.isEmpty()) break;
        }
        return found;
    }

    /** The piece a loaded train is standing on, searching a little below its position. */
    public static TileRail railUnder(World world, double x, double y, double z) {
        for (int dy = 0; dy >= -2; dy--) {
            for (int[] o : new int[][]{ {0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1} }) {
                Vec3i p = new Vec3i(Math.floor(x) + o[0], Math.floor(y) + dy, Math.floor(z) + o[1]);
                if (!world.isBlockLoaded(p)) continue;
                TileRailBase base = world.getBlockEntity(p, TileRailBase.class);
                if (base == null) continue;
                TileRail t = base instanceof TileRail tr ? tr : (base.getParent() != null && world.isBlockLoaded(base.getParent()) ? base.getParentTile() : null);
                if (t != null && t.info != null) return t;
            }
        }
        return null;
    }

    /** Fallback for point-only pieces (crossings, tables): look at the six neighbours. */
    private TileRail probeAround(TileRail rail, Vec3i cameFrom) {
        Vec3i c = rail.getPos();
        int[][] d = { {1,0,0}, {-1,0,0}, {0,0,1}, {0,0,-1} };
        for (int[] o : d) {
            for (int dy : PROBE_DY) {
                TileRail t = railAt(new Vec3i(c.x + o[0], c.y + dy, c.z + o[2]), rail, cameFrom);
                if (t != null) return t;
            }
        }
        return null;
    }

    /** At a junction, take the leg the switch points allow; note that we diverged. */
    private TileRail pickByPoints(List<TileRail> options, Span span) {
        TileRail straight = null, turn = null;
        for (TileRail t : options) {
            TrackItems type = t.info != null ? t.info.settings.type : null;
            SwitchState st;
            try {
                st = SwitchUtil.getSwitchState(t);
            } catch (RuntimeException e) {
                st = SwitchState.NONE;
            }
            if (st == SwitchState.TURN || type == TrackItems.TURN) turn = t;
            else straight = t;
        }
        // Points thrown to the turn leg? Then that is the route.
        if (turn != null) {
            try {
                if (SwitchUtil.getSwitchState(turn) == SwitchState.TURN) {
                    span.diverging = true;
                    return turn;
                }
            } catch (RuntimeException ignored) {
                // fall through to the straight leg
            }
        }
        return straight != null ? straight : options.get(0);
    }

    private TileRail railAt(Vec3i p, TileRail self, Vec3i cameFrom) {
        if (!world.isBlockLoaded(p)) return null;
        TileRailBase base = world.getBlockEntity(p, TileRailBase.class);
        if (base == null) return null;
        TileRail t = base instanceof TileRail tr ? tr : parentOf(base);
        if (t == null || t.info == null) return null;
        if (t.getPos().equals(self.getPos())) return null;
        if (cameFrom != null && t.getPos().equals(cameFrom)) return null;
        return t;
    }

    private TileRail parentOf(TileRailBase base) {
        Vec3i pp = base.getParent();
        if (pp == null || !world.isBlockLoaded(pp)) return null;
        return base.getParentTile();
    }

    private static boolean contains(List<TileRail> list, TileRail t) {
        for (TileRail o : list) if (o.getPos().equals(t.getPos())) return true;
        return false;
    }

    // ---- geometry -----------------------------------------------------------------------

    /**
     * Centre-lines by piece position. Track is rebuilt rarely and IR builds a whole builder per
     * lookup, so the autopilot and protection (which walk hundreds of pieces a second) read from
     * here. Entries older than {@link #PATH_TTL} ticks are recomputed, so a relaid piece heals.
     * ponytail: global map, trimmed wholesale at 20k entries; per-dimension LRU if a server needs it.
     */
    private static final java.util.Map<Long, double[]> PATH_CACHE = new java.util.HashMap<>();
    private static final java.util.Map<Long, Long> PATH_TIME = new java.util.HashMap<>();
    private static final long PATH_TTL = 600;

    public static double[] worldPath(TileRail rail) {
        long key = rail.getPos().toLong() ^ ((long) rail.getWorld().getId() << 58);
        long now = rail.getWorld().getTicks();
        Long t = PATH_TIME.get(key);
        if (t != null && now - t < PATH_TTL && PATH_CACHE.containsKey(key)) return PATH_CACHE.get(key);
        double[] p = computePath(rail);
        if (PATH_CACHE.size() > 20000) { PATH_CACHE.clear(); PATH_TIME.clear(); }
        PATH_CACHE.put(key, p);
        PATH_TIME.put(key, now);
        return p;
    }

    /** The piece's centre-line in world coordinates, or null if IR can't build it. */
    private static double[] computePath(TileRail rail) {
        try {
            RailInfo info = rail.info;
            if (info == null) return null;
            BuilderBase b = info.getBuilder(cam72cam.mod.world.World.get(rail.getWorld().internal));
            if (!(b instanceof IIterableTrack iterable)) return null;
            List<VecYPR> path = iterable.getPath(PATH_STEP);
            if (path == null || path.isEmpty()) return null;
            Vec3d center = info.placementInfo.placementPosition.add(rail.getPos());
            int n = Math.min(path.size(), 12);
            double[] out = new double[n * 3];
            for (int k = 0; k < n; k++) {
                int i = n == 1 ? 0 : (int) Math.round((double) k * (path.size() - 1) / (n - 1));
                VecYPR v = path.get(i);
                out[k * 3] = center.x + v.x;
                out[k * 3 + 1] = center.y + v.y;
                out[k * 3 + 2] = center.z + v.z;
            }
            return out;
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    private static double pathLength(double[] p) {
        double L = 0;
        for (int i = 3; i < p.length; i += 3) {
            double dx = p[i] - p[i - 3], dz = p[i + 2] - p[i - 1];
            L += Math.sqrt(dx * dx + dz * dz);
        }
        return L;
    }

    private static double dist2(double ax, double az, double bx, double bz) {
        double dx = ax - bx, dz = az - bz;
        return dx * dx + dz * dz;
    }

    static double segDistSq(double px, double pz, double x0, double z0, double x1, double z1) {
        double dx = x1 - x0, dz = z1 - z0;
        double len = dx * dx + dz * dz;
        double t = len == 0 ? 0 : Math.max(0, Math.min(1, ((px - x0) * dx + (pz - z0) * dz) / len));
        double ex = x0 + t * dx - px, ez = z0 + t * dz - pz;
        return ex * ex + ez * ez;
    }
}
