package com.dogpound.railmap.graph;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Merges pieces into long polylines for Dynmap: 2000 one-piece markers would choke the web
 * UI, ~50 chains do not. Walks from every node of degree != 2 along degree-2 nodes until it
 * hits another junction/end; closed loops of degree-2 nodes are picked up in a second pass.
 */
public final class Chains {
    private Chains() {}

    public static List<float[]> build(RailNetwork net) {
        int n = net.nodes.size();
        List<List<Integer>> adj = new ArrayList<>(n);
        for (int i = 0; i < n; i++) adj.add(new ArrayList<>(2));
        for (RailSegment s : net.segments) {
            if (s.a() < n && s.b() < n) {
                adj.get(s.a()).add(s.b());
                adj.get(s.b()).add(s.a());
            }
        }
        List<float[]> out = new ArrayList<>();
        BitSet done = new BitSet(n);
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < n; i++) {
                if (done.get(i)) continue;
                boolean junction = adj.get(i).size() != 2;
                if (pass == 0 && !junction) continue; // loops handled in pass 1
                if (adj.get(i).isEmpty()) {
                    done.set(i);
                    out.add(net.nodes.get(i).points);
                    continue;
                }
                for (int next : new ArrayList<>(adj.get(i))) {
                    if (done.get(next) && adj.get(next).size() == 2) continue;
                    List<Integer> chain = new ArrayList<>();
                    chain.add(i);
                    int prev = i, cur = next;
                    while (true) {
                        chain.add(cur);
                        if (adj.get(cur).size() != 2 || done.get(cur)) break;
                        done.set(cur);
                        int a = adj.get(cur).get(0), b = adj.get(cur).get(1);
                        int nx = a == prev ? b : a;
                        prev = cur;
                        cur = nx;
                        if (cur == i) break;
                    }
                    if (chain.size() > 1) out.add(stitch(net, chain));
                }
                done.set(i);
            }
        }
        return out;
    }

    /** Concatenate the pieces' centre-lines, flipping each so consecutive ends meet. */
    private static float[] stitch(RailNetwork net, List<Integer> ids) {
        List<float[]> pts = new ArrayList<>();
        float lx = Float.NaN, lz = Float.NaN;
        for (int id : ids) {
            float[] p = net.nodes.get(id).points;
            if (p.length < 6) continue;
            boolean flip = false;
            if (!Float.isNaN(lx)) {
                double dStart = dist(lx, lz, p[0], p[2]);
                double dEnd = dist(lx, lz, p[p.length - 3], p[p.length - 1]);
                flip = dEnd < dStart;
            }
            float[] q = p;
            if (flip) {
                q = new float[p.length];
                int m = p.length / 3;
                for (int k = 0; k < m; k++) {
                    q[k * 3] = p[(m - 1 - k) * 3];
                    q[k * 3 + 1] = p[(m - 1 - k) * 3 + 1];
                    q[k * 3 + 2] = p[(m - 1 - k) * 3 + 2];
                }
            }
            pts.add(q);
            lx = q[q.length - 3];
            lz = q[q.length - 1];
        }
        int total = 0;
        for (float[] q : pts) total += q.length;
        float[] out = new float[total];
        int o = 0;
        for (float[] q : pts) {
            System.arraycopy(q, 0, out, o, q.length);
            o += q.length;
        }
        return out;
    }

    private static double dist(double ax, double az, double bx, double bz) {
        double dx = ax - bx, dz = az - bz;
        return dx * dx + dz * dz;
    }
}
