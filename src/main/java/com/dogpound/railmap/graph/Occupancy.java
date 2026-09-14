package com.dogpound.railmap.graph;

import java.util.BitSet;
import java.util.List;

/**
 * Which track pieces have a train on them right now. Pure geometry: a piece is occupied when
 * some stock's position is within {@link #MAX_DIST} blocks (xz) and 3 blocks (y) of its
 * centre-line. Same code runs on the client (board colours) and the server (Dynmap).
 */
public final class Occupancy {
    public static final double MAX_DIST = 2.2;

    private Occupancy() {}

    public static BitSet compute(RailNetwork net, List<TrainNode> trains) {
        BitSet out = new BitSet(net.nodes.size());
        if (trains.isEmpty()) return out;
        for (TrainNode t : trains) {
            int id = nearestNode(net, t.x, t.y, t.z);
            if (id >= 0) out.set(id);
        }
        return out;
    }

    /** Nearest piece to a world point within MAX_DIST, or -1. */
    public static int nearestNode(RailNetwork net, double x, double y, double z) {
        double best = MAX_DIST * MAX_DIST;
        int bestId = -1;
        // Bounds reject first: most trains are nowhere near most boards' networks.
        if (x < net.minX - MAX_DIST || x > net.maxX + MAX_DIST || z < net.minZ - MAX_DIST || z > net.maxZ + MAX_DIST) {
            return -1;
        }
        for (RailNode n : net.nodes) {
            float[] p = n.points;
            if (Math.abs(p[1] - y) > 3) continue;
            double d;
            if (p.length < 6) {
                double dx = p[0] - x, dz = p[2] - z;
                d = dx * dx + dz * dz;
            } else {
                d = Double.MAX_VALUE;
                for (int i = 3; i < p.length; i += 3) {
                    d = Math.min(d, segDistSq(x, z, p[i - 3], p[i - 1], p[i], p[i + 2]));
                }
            }
            if (d < best) {
                best = d;
                bestId = n.id;
            }
        }
        return bestId;
    }

    static double segDistSq(double px, double pz, double x0, double z0, double x1, double z1) {
        double dx = x1 - x0, dz = z1 - z0;
        double len = dx * dx + dz * dz;
        double t = len == 0 ? 0 : Math.max(0, Math.min(1, ((px - x0) * dx + (pz - z0) * dz) / len));
        double ex = x0 + t * dx - px, ez = z0 + t * dz - pz;
        return ex * ex + ez * ez;
    }
}
