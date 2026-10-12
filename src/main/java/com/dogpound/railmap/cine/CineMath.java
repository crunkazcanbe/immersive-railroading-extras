package com.dogpound.railmap.cine;

import java.util.List;

/**
 * The camera maths shared by the client camera and the director desk (and checked by /irextras cine selftest):
 * look-at angles, framing zoom, Catmull-Rom camera paths through recorded waypoints, letterbox bars, the trackside
 * auto-cut choice, and the follow-cam placements around a train.
 */
public final class CineMath {
    private CineMath() {}

    /** sources a director desk / photo mode can show */
    public static final int AUTO = 9, CHASE = 10, SIDE = 11, CAB = 12, ORBIT = 13, FLYBY = 14, FREE = 15, PATH = 16, REPLAY = 17;
    public static String name(int src) {
        if (src >= 1 && src <= 8) return "CAM " + src;
        return switch (src) {
            case AUTO -> "TRACKSIDE AUTO"; case CHASE -> "CHASE"; case SIDE -> "SIDE TRACK"; case CAB -> "CAB VIEW"; case ORBIT -> "HELI ORBIT";
            case FLYBY -> "FLYBY"; case FREE -> "FREE CAM"; case PATH -> "PATH"; case REPLAY -> "REPLAY"; default -> "-";
        };
    }

    /** letterbox ratios: off, 2.39, 2.00, 1.85 */
    public static final double[] LETTERBOX = {0, 2.39, 2.0, 1.85};
    public static final String[] LETTERBOX_NAME = {"OFF", "2.39:1", "2.00:1", "1.85:1"};

    /** {yaw, pitch} (Minecraft) to look from a to b */
    public static double[] look(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        return new double[]{Math.toDegrees(Math.atan2(-dx, dz)), -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)))};
    }

    /** a vertical field of view that fills ~fill of the frame height with something `size` m big at `dist` m (clamped 8..75) */
    public static double frameFov(double size, double dist, double fill) {
        double f = Math.toDegrees(2 * Math.atan(size / fill / 2 / Math.max(0.5, dist)));
        return Math.max(8, Math.min(75, f));
    }

    /** letterbox bar height (each, top and bottom) for a w x h screen */
    public static int bar(int w, int h, double ratio) {
        if (ratio <= 0 || (double) w / h >= ratio) return 0;
        return (int) Math.round((h - w / ratio) / 2);
    }

    /** Catmull-Rom between p1 and p2 */
    public static double cr(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t, t3 = t2 * t;
        return 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }

    /**
     * A waypoint path at t (0..1, eased in and out): keys {x, y, z, yaw, pitch, fov}, passing through every key.
     * Yaw is unwrapped key to key so the camera turns the short way.
     */
    public static double[] path(List<double[]> keys, double t) {
        int n = keys.size();
        if (n == 0) return null;
        if (n == 1) return keys.get(0).clone();
        t = Math.max(0, Math.min(1, t));
        t = t * t * (3 - 2 * t);                                               // ease in / out
        double[][] k = new double[n][];
        for (int i = 0; i < n; i++) {
            k[i] = keys.get(i).clone();
            if (i > 0) { while (k[i][3] - k[i - 1][3] > 180) k[i][3] -= 360; while (k[i][3] - k[i - 1][3] < -180) k[i][3] += 360; }
        }
        double f = t * (n - 1);
        int i = Math.min(n - 2, (int) Math.floor(f));
        double u = f - i;
        double[] o = new double[6];
        for (int c = 0; c < 6; c++) o[c] = cr(k[Math.max(0, i - 1)][c], k[i][c], k[i + 1][c], k[Math.min(n - 1, i + 2)][c], u);
        return o;
    }

    /**
     * Trackside auto-cut: which tripod covers a train at (x, z) now. cams: {x, y, z, number} of the working tripods
     * (with line of sight already checked); current = the one on air, heldFor = seconds it has been on.
     * Keeps the current shot until the train is out of range or another tripod is clearly nearer (75 %), and never
     * cuts before minShot seconds. Returns the tripod number, or 0.
     */
    public static int autoCut(List<double[]> cams, double x, double y, double z, int current, double heldFor, double range, double minShot) {
        double curD = Double.MAX_VALUE, bestD = Double.MAX_VALUE;
        int best = 0;
        for (double[] c : cams) {
            double d = Math.sqrt((c[0] - x) * (c[0] - x) + (c[1] - y) * (c[1] - y) + (c[2] - z) * (c[2] - z));
            if (d > range) continue;
            if ((int) c[3] == current) curD = d;
            if (d < bestD) { bestD = d; best = (int) c[3]; }
        }
        if (best == 0) return 0;
        if (curD == Double.MAX_VALUE) return best;                               // current lost the train: cut now
        if (heldFor < minShot) return current;
        return bestD < curD * 0.75 ? best : current;
    }

    /**
     * Follow-cam placement around a train (centre x y z, heading yaw deg, half length m, time s):
     * {camX, camY, camZ, lookX, lookY, lookZ, roll}
     */
    public static double[] follow(int mode, double x, double y, double z, double yaw, double half, double time) {
        double fx = -Math.sin(Math.toRadians(yaw)), fz = Math.cos(Math.toRadians(yaw)), rx = -fz, rz = fx;
        return switch (mode) {
            case CHASE -> new double[]{x - fx * (half + 14), y + 4.5, z - fz * (half + 14), x + fx * half * 0.5, y + 2, z + fz * half * 0.5, 0};
            case SIDE -> new double[]{x + rx * 16 - fx * 3, y + 2.6, z + rz * 16 - fz * 3, x, y + 1.6, z, 0};
            case ORBIT -> {
                double a = Math.toRadians(time * 6), r = 40 + half * 0.5;
                yield new double[]{x + Math.cos(a) * r, y + 24, z + Math.sin(a) * r, x, y + 1, z, -8};
            }
            default -> new double[]{x, y + 3, z, x + fx, y + 3, z + fz, 0};
        };
    }

    /** where a flyby camera waits for the train: ahead along its heading, off to one side, at a person's eye height */
    public static double[] flybySpot(double x, double y, double z, double yaw, double speedMs) {
        double fx = -Math.sin(Math.toRadians(yaw)), fz = Math.cos(Math.toRadians(yaw)), rx = -fz, rz = fx;
        double ahead = Math.max(40, Math.min(160, Math.abs(speedMs) * 6));
        return new double[]{x + fx * ahead + rx * 9, y + 1.7, z + fz * ahead + rz * 9};
    }
}
