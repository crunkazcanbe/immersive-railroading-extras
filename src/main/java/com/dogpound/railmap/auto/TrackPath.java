package com.dogpound.railmap.auto;

import cam72cam.immersiverailroading.library.SwitchState;
import cam72cam.immersiverailroading.library.TrackItems;
import cam72cam.immersiverailroading.tile.TileRail;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.immersiverailroading.util.SwitchUtil;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.world.World;
import com.dogpound.railmap.signal.TrackFollower;

import java.util.ArrayList;
import java.util.PriorityQueue;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A route along real track: the pieces in order, the distance to each, and how every switch on
 * the way has to lie. Built two ways:
 * <ul>
 *   <li>{@link #find} — search every leg at every junction for a way to a target (a station,
 *       a signal). This is what lets a driverless train, or a dispatcher's route, get
 *       anywhere on the railroad.</li>
 *   <li>{@link #ahead} — follow the switches as they lie right now, the way a train would
 *       actually go. Train protection uses this for trains a person is driving.</li>
 * </ul>
 * Distances are in blocks, which in Immersive Railroading are metres.
 */
public final class TrackPath {
    /** One piece of the route. */
    public static final class Step {
        public final Vec3i pos;
        public final double[] points;
        /** Distance from the start of the route to the far end of this piece. */
        public final double distance;

        Step(Vec3i pos, double[] points, double distance) {
            this.pos = pos;
            this.points = points;
            this.distance = distance;
        }
    }

    public final List<Step> steps = new ArrayList<>();
    /** Switch piece position → the way it must lie for this route. */
    public final Map<Vec3i, SwitchState> switches = new LinkedHashMap<>();
    public double length;
    /** Set by {@link #find}: the step index where the target was reached, else -1. */
    public int targetIndex = -1;

    /** Distance along the route to the point nearest (x,z), or -1 if the route never passes within {@code radius}. */
    public double distanceTo(double x, double y, double z, double radius) {
        double r2 = radius * radius;
        double before = 0;
        for (Step s : steps) {
            if (s.points != null) {
                for (int i = 0; i < s.points.length; i += 3) {
                    if (Math.abs(s.points[i + 1] - y) > 4) continue;
                    double dx = s.points[i] - x, dz = s.points[i + 2] - z;
                    if (dx * dx + dz * dz <= r2) {
                        double frac = s.points.length <= 3 ? 1 : (double) i / (s.points.length - 3);
                        return before + (s.distance - before) * frac;
                    }
                }
            }
            before = s.distance;
        }
        return -1;
    }

    public boolean contains(Vec3i pos) {
        for (Step s : steps) if (s.pos.equals(pos)) return true;
        return false;
    }

    /** Index of the step for this piece, or -1. */
    public int indexOf(Vec3i pos) {
        for (int i = 0; i < steps.size(); i++) if (steps.get(i).pos.equals(pos)) return i;
        return -1;
    }

    /** Direction of travel (unit x,z) through step {@code i}. */
    public double[] headingAt(int i) {
        Step s = steps.get(i);
        double[] p = s.points;
        if (p == null || p.length < 6) {
            if (i + 1 < steps.size()) {
                Vec3i a = s.pos, b = steps.get(i + 1).pos;
                return norm(b.x - a.x, b.z - a.z);
            }
            return new double[]{ 0, 0 };
        }
        // Points are stored in piece order, which may run against travel: orient by the next piece.
        double fx = p[p.length - 3] - p[0], fz = p[p.length - 1] - p[2];
        if (i + 1 < steps.size()) {
            Vec3i n = steps.get(i + 1).pos;
            double toNextA = sq(p[0] - n.x - 0.5, p[2] - n.z - 0.5);
            double toNextB = sq(p[p.length - 3] - n.x - 0.5, p[p.length - 1] - n.z - 0.5);
            if (toNextA < toNextB) { fx = -fx; fz = -fz; }
        } else if (i > 0) {
            Vec3i b = steps.get(i - 1).pos;
            double fromA = sq(p[0] - b.x - 0.5, p[2] - b.z - 0.5);
            double fromB = sq(p[p.length - 3] - b.x - 0.5, p[p.length - 1] - b.z - 0.5);
            if (fromB < fromA) { fx = -fx; fz = -fz; }
        }
        return norm(fx, fz);
    }

    // ---- building ------------------------------------------------------------------------

    /** Follow the track the way the points lie now, up to {@code budget} blocks. */
    public static TrackPath ahead(World world, TileRail start, Vec3i cameFrom, double budget) {
        TrackFollower f = new TrackFollower(world, budget);
        TrackPath out = new TrackPath();
        TileRail cur = start;
        Vec3i from = cameFrom;
        Set<Long> seen = new HashSet<>();
        while (cur != null && out.length < budget && seen.add(cur.getPos().toLong())) {
            out.add(cur);
            List<TileRail> next = f.candidatesAhead(cur, from);
            if (next.isEmpty()) break;
            TileRail pick = next.size() == 1 ? next.get(0) : byPoints(next);
            from = cur.getPos();
            cur = pick;
        }
        return out;
    }

    /**
     * Search for a way from {@code start} (heading away from {@code cameFrom}) to any piece for
     * which {@code goal} says yes. Breadth-first on distance, so the shortest route along the
     * track wins; every junction is tried both ways. Returns null if nothing within
     * {@code budget} blocks reaches it.
     */
    public static TrackPath find(World world, TileRail start, Vec3i cameFrom, double budget, Goal goal) {
        TrackFollower f = new TrackFollower(world, budget);
        PriorityQueue<Node> open = new PriorityQueue<>((x, y) -> Double.compare(x.dist, y.dist));
        Map<Long, Double> best = new HashMap<>();
        open.add(new Node(start, cameFrom, null, pieceLength(start)));
        int expanded = 0;
        while (!open.isEmpty() && expanded++ < 6000) {
            Node n = open.poll();
            long key = n.rail.getPos().toLong() * 31 + (n.from == null ? 0 : n.from.toLong());
            Double b = best.get(key);
            if (b != null && b <= n.dist) continue;
            best.put(key, n.dist);
            if (goal.reached(n.rail)) return n.build();
            if (n.dist > budget) continue;
            List<TileRail> next = f.candidatesAhead(n.rail, n.from);
            for (TileRail t : next) {
                open.add(new Node(t, n.rail.getPos(), n, n.dist + pieceLength(t)));
            }
        }
        return null;
    }

    /** What a route is looking for. */
    public interface Goal {
        boolean reached(TileRail rail);
    }

    /** Goal: a piece that passes within {@code radius} blocks of a point. */
    public static Goal near(double x, double y, double z, double radius) {
        double r2 = radius * radius;
        return rail -> {
            double[] p = TrackFollower.worldPath(rail);
            if (p == null) {
                Vec3i v = rail.getPos();
                return sq(v.x + 0.5 - x, v.z + 0.5 - z) <= r2 && Math.abs(v.y - y) <= 4;
            }
            for (int i = 0; i < p.length; i += 3) {
                if (Math.abs(p[i + 1] - y) <= 4 && sq(p[i] - x, p[i + 2] - z) <= r2) return true;
            }
            return false;
        };
    }

    /** Goal: exactly this piece. */
    public static Goal piece(Vec3i pos) {
        return rail -> rail.getPos().equals(pos);
    }

    private void add(TileRail rail) {
        double[] p = TrackFollower.worldPath(rail);
        length += p == null ? 1 : Math.max(0.5, polyLength(p));
        steps.add(new Step(rail.getPos(), p, length));
    }

    private static final class Node {
        final TileRail rail;
        final Vec3i from;
        final Node prev;
        final double dist;

        Node(TileRail rail, Vec3i from, Node prev, double dist) {
            this.rail = rail;
            this.from = from;
            this.prev = prev;
            this.dist = dist;
        }

        TrackPath build() {
            List<TileRail> order = new ArrayList<>();
            for (Node n = this; n != null; n = n.prev) order.add(0, n.rail);
            TrackPath out = new TrackPath();
            for (TileRail r : order) out.add(r);
            out.targetIndex = out.steps.size() - 1;
            for (int i = 0; i + 1 < order.size(); i++) requireSwitch(out, order.get(i), order.get(i + 1));
            return out;
        }
    }

    /**
     * Record how a switch must lie for a train to go from {@code a} to {@code b}. Facing moves
     * (into the points) choose the leg; trailing moves (back through from a leg) need the points
     * set for the leg we arrive on, or IR takes the train down the wrong one.
     */
    private static void requireSwitch(TrackPath out, TileRail a, TileRail b) {
        TileRail swA = switchOf(a), swB = switchOf(b);
        if (swB != null) {
            SwitchState want = isTurnLeg(b) ? SwitchState.TURN : SwitchState.STRAIGHT;
            if (swA != null && swA.getPos().equals(swB.getPos())) return;   // moving within one switch
            out.switches.putIfAbsent(swB.getPos(), want);
        }
        if (swA != null && (swB == null || !swA.getPos().equals(swB.getPos()))) {
            SwitchState want = isTurnLeg(a) ? SwitchState.TURN : SwitchState.STRAIGHT;
            out.switches.putIfAbsent(swA.getPos(), want);
        }
    }

    /** The switch this piece belongs to (itself, or the switch whose turn leg it is). */
    static TileRail switchOf(TileRail rail) {
        try {
            if (rail.info != null && rail.info.settings.type == TrackItems.SWITCH) return rail;
            TileRail sw = rail.findSwitchParent();
            if (sw != null && sw.info != null && sw.info.settings.type == TrackItems.SWITCH) return sw;
        } catch (RuntimeException | LinkageError ignored) {
            // older IR: no switch parent lookup, treat as plain track
        }
        return null;
    }

    private static boolean isTurnLeg(TileRail rail) {
        return rail.info != null && rail.info.settings.type == TrackItems.TURN;
    }

    private static TileRail byPoints(List<TileRail> options) {
        for (TileRail t : options) {
            try {
                if (SwitchUtil.getSwitchState(t) == SwitchState.TURN) return t;
            } catch (RuntimeException ignored) {
                // fall through
            }
        }
        for (TileRail t : options) if (!isTurnLeg(t)) return t;
        return options.get(0);
    }

    private static double pieceLength(TileRail rail) {
        double[] p = TrackFollower.worldPath(rail);
        return p == null ? 1 : Math.max(0.5, polyLength(p));
    }

    static double polyLength(double[] p) {
        double L = 0;
        for (int i = 3; i < p.length; i += 3) {
            double dx = p[i] - p[i - 3], dz = p[i + 2] - p[i - 1];
            L += Math.sqrt(dx * dx + dz * dz);
        }
        return L;
    }

    private static double sq(double a, double b) {
        return a * a + b * b;
    }

    private static double[] norm(double x, double z) {
        double l = Math.sqrt(x * x + z * z);
        return l < 1e-9 ? new double[]{ 0, 0 } : new double[]{ x / l, z / l };
    }
}
