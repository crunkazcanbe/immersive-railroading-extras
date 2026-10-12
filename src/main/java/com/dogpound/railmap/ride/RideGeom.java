package com.dogpound.railmap.ride;

import java.util.List;

/**
 * Where a ropeway's rope runs and where a car / cabin is on it. Pure maths, the same on server and client (cars are
 * placed from it on both sides, so they glide exactly along the rope).
 * <ul>
 *   <li>The rope hangs between its supports (stations and pylon heads) in a parabola: sag = sagK x horizontal span at
 *   mid-span (2 % with a tension weight keeping it taut, 3.5 % without). A funicular runs on its track: no sag.</li>
 *   <li>Two rope sides LAT metres either side of the line centre: an aerial tram's two track ropes (one cabin each), a
 *   gondola's outbound (+1) and return (-1) strands, joined round each station's bullwheel (radius LAT).</li>
 *   <li>Funicular: a single track with an Abt passing loop in the middle third (the cars swing out to their side).</li>
 * </ul>
 * Coordinates: u = metres along the line from station A (0..length). Gondola loop coordinate w: 0..length out on the +1
 * side, round B's bullwheel, back on the -1 side, round A's bullwheel, 0..loop.
 */
public final class RideGeom {
    public static final int GONDOLA = 0, TRAM = 1, FUNICULAR = 2;
    public static final String[] KIND = {"GONDOLA", "AERIAL TRAM", "FUNICULAR"};
    /** half the distance between the two rope sides (m) */
    public static final double LAT = 1.4;
    /** a hanging cabin's floor is this far below the rope (grip + hanger + roof) */
    public static final double HANG = 2.9;
    /** the straight run either side of a bullwheel where a detached gondola slows / speeds up */
    public static final double ZONE = 6.0;

    public final int kind;
    final double[] xs, ys, zs, cum;
    public final double length, loop;

    public RideGeom(int kind, List<double[]> supports, double sagK) {
        this.kind = kind;
        int n = 1;
        for (int i = 0; i + 1 < supports.size(); i++) n += Math.max(1, (int) Math.ceil(dist(supports.get(i), supports.get(i + 1))));
        xs = new double[n]; ys = new double[n]; zs = new double[n]; cum = new double[n];
        int k = 0;
        double[] p0 = supports.get(0);
        xs[0] = p0[0]; ys[0] = p0[1]; zs[0] = p0[2];
        for (int i = 0; i + 1 < supports.size(); i++) {
            double[] a = supports.get(i), b = supports.get(i + 1);
            int m = Math.max(1, (int) Math.ceil(dist(a, b)));
            double h = Math.hypot(b[0] - a[0], b[2] - a[2]), sag = kind == FUNICULAR ? 0 : sagK * h;
            for (int j = 1; j <= m; j++) {
                double t = j / (double) m;
                k++;
                xs[k] = a[0] + (b[0] - a[0]) * t;
                zs[k] = a[2] + (b[2] - a[2]) * t;
                ys[k] = a[1] + (b[1] - a[1]) * t - sag * 4 * t * (1 - t);
            }
        }
        for (int i = 1; i < n; i++) cum[i] = cum[i - 1] + Math.sqrt(sq(xs[i] - xs[i - 1]) + sq(ys[i] - ys[i - 1]) + sq(zs[i] - zs[i - 1]));
        length = cum[n - 1];
        loop = 2 * length + 2 * Math.PI * LAT;
    }

    private static double sq(double v) { return v * v; }
    static double dist(double[] a, double[] b) { return Math.sqrt(sq(a[0] - b[0]) + sq(a[1] - b[1]) + sq(a[2] - b[2])); }

    public int samples() { return xs.length; }
    public double sx(int i) { return xs[i]; }
    public double sy(int i) { return ys[i]; }
    public double sz(int i) { return zs[i]; }
    public double su(int i) { return cum[i]; }

    /** the rope centre at u: {x, y, z, dx, dy, dz} (unit tangent toward B) */
    public double[] at(double u) {
        u = Math.max(0, Math.min(length, u));
        int lo = 0, hi = cum.length - 1;
        while (hi - lo > 1) { int mid = (lo + hi) >>> 1; if (cum[mid] <= u) lo = mid; else hi = mid; }
        double seg = cum[hi] - cum[lo], t = seg <= 1e-9 ? 0 : (u - cum[lo]) / seg;
        double dx = xs[hi] - xs[lo], dy = ys[hi] - ys[lo], dz = zs[hi] - zs[lo], l = Math.max(1e-9, Math.sqrt(dx * dx + dy * dy + dz * dz));
        return new double[]{xs[lo] + dx * t, ys[lo] + dy * t, zs[lo] + dz * t, dx / l, dy / l, dz / l};
    }

    /** rise per metre of rope at u (+ = uphill toward B) */
    public double grade(double u) { return at(u)[4]; }

    /** the horizontal unit vector to the +1 side at u: {lx, lz} */
    public double[] lateral(double u) {
        double[] a = at(u);
        double h = Math.max(1e-9, Math.hypot(a[3], a[5]));
        return new double[]{-a[5] / h, a[3] / h};
    }

    /**
     * A car / cabin pose: {x, y, z, yaw, pitch, speed sign} (Minecraft yaw / pitch, facing its direction of travel when the
     * rope runs forward). For hanging kinds y is the cabin floor.
     */
    public double[] carPose(double u, int side, int dirSign) {
        double[] a = at(u);
        double[] l = lateral(u);
        double off = kind == FUNICULAR ? side * LAT * passingLoop(u / Math.max(1e-9, length)) : side * LAT;
        double x = a[0] + l[0] * off, z = a[2] + l[1] * off, y = a[1] - (kind == FUNICULAR ? 0 : HANG);
        double fx = a[3] * dirSign, fy = a[4] * dirSign, fz = a[5] * dirSign;
        return new double[]{x, y, z, yaw(fx, fz), -Math.toDegrees(Math.atan2(fy, Math.hypot(fx, fz))), dirSign};
    }

    /** Minecraft yaw for a horizontal direction */
    public static double yaw(double fx, double fz) { return Math.toDegrees(Math.atan2(-fx, fz)); }

    /** the funicular's passing loop: 0 on single track, 1 in the middle third, smooth turnouts between */
    public static double passingLoop(double t) {
        if (t <= 0.28 || t >= 0.72) return 0;
        if (t >= 0.38 && t <= 0.62) return 1;
        double k = t < 0.5 ? (t - 0.28) / 0.10 : (0.72 - t) / 0.10;
        return 0.5 - 0.5 * Math.cos(Math.PI * Math.max(0, Math.min(1, k)));
    }

    // ---- gondola loop ------------------------------------------------------------------------------------------------
    public double wrap(double w) { w %= loop; return w < 0 ? w + loop : w; }

    /** the arc length of a bullwheel turn */
    public double arc() { return Math.PI * LAT; }

    /**
     * Where loop coordinate w is: {u, side, arcFraction (-1 = on a straight), station (0 = A, 1 = B, -1 = line)}.
     * On an arc u is the station end and arcFraction runs 0..1 from the arriving strand to the leaving one.
     */
    public double[] loopPlace(double w) {
        w = wrap(w);
        double L = length, a = arc();
        if (w < L) return new double[]{w, 1, -1, w < ZONE ? 0 : w > L - ZONE ? 1 : -1};
        if (w < L + a) return new double[]{L, 0, (w - L) / a, 1};
        if (w < 2 * L + a) { double u = L - (w - L - a); return new double[]{u, -1, -1, u > L - ZONE ? 1 : u < ZONE ? 0 : -1}; }
        return new double[]{0, 0, (w - 2 * L - a) / a, 0};
    }

    /** a gondola cabin's pose at loop coordinate w (floor position, facing its travel) */
    public double[] loopPose(double w) {
        double[] p = loopPlace(w);
        if (p[2] < 0) return carPose(p[0], (int) p[1], p[1] > 0 ? 1 : -1);
        boolean atB = p[3] == 1;
        double[] c = at(p[0]);
        double[] l = lateral(p[0]);
        double fx = c[3], fz = c[5], h = Math.max(1e-9, Math.hypot(fx, fz));
        fx /= h; fz /= h;
        if (!atB) { fx = -fx; fz = -fz; }                                    // "ahead" = beyond the station end
        double th = Math.PI * p[2];
        // from the arriving side (B: +1, A: -1) round the wheel to the leaving side
        double sIn = atB ? 1 : -1;
        double ox = (l[0] * Math.cos(th) * sIn + fx * Math.sin(th)) * LAT, oz = (l[1] * Math.cos(th) * sIn + fz * Math.sin(th)) * LAT;
        double tx = (-l[0] * Math.sin(th) * sIn + fx * Math.cos(th)), tz = (-l[1] * Math.sin(th) * sIn + fz * Math.cos(th));
        return new double[]{c[0] + ox, c[1] - HANG, c[2] + oz, yaw(tx, tz), 0, 1};
    }

    /** what a gondola sees of the rope at w: true = gripped to the haul rope (on the line), false = detached in a station */
    public boolean gripped(double w) { return loopPlace(w)[3] < 0; }
}
