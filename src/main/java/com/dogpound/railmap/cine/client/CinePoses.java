package com.dogpound.railmap.cine.client;

import com.dogpound.railmap.cine.CineConfig;
import com.dogpound.railmap.cine.CineMath;
import com.dogpound.railmap.cine.CineTrains;
import com.dogpound.railmap.cine.TileCamTripod;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where a camera source is right now on this client: {x, y, z, yaw, pitch, fov, roll}. Used by photo mode, the director
 * desk's preview / program monitors and WATCH. Each viewer (a key) keeps its own smoothing, flyby spot and auto-cut state
 * so the cameras move like operated cameras, not glued to the train.
 */
public final class CinePoses {
    private CinePoses() {}

    static final class St {
        double cx, cy, cz, lx, ly, lz;
        boolean init;
        double[] spot;
        int tri;
        long triSince, last;
    }

    private static final Map<String, St> ST = new HashMap<>();

    static St st(String key) { return ST.computeIfAbsent(key, k -> new St()); }

    /** the working tripods loaded on this client: {lensX, lensY, lensZ, number} */
    static List<double[]> tripods() {
        List<double[]> o = new ArrayList<>();
        Minecraft mc = Minecraft.getMinecraft();
        for (TileEntity t : mc.world.loadedTileEntityList)
            if (t instanceof TileCamTripod c && !t.isInvalid() && c.powered()) { Vec3d l = c.lens(); o.add(new double[]{l.x, l.y, l.z, c.number()}); }
        return o;
    }

    static boolean sees(double ax, double ay, double az, double bx, double by, double bz) {
        Minecraft mc = Minecraft.getMinecraft();
        Vec3d a = new Vec3d(ax, ay, az), b = new Vec3d(bx, by, bz);
        RayTraceResult r = mc.world.rayTraceBlocks(a.add(b.subtract(a).normalize().scale(0.8)), b, false, true, false);
        return r == null || r.typeOfHit != RayTraceResult.Type.BLOCK;
    }

    /**
     * src = CineMath source, subject = {x, y, z, heading, speed m/s, half length} (a live train or a replay ghost), uuid =
     * the live train (cab view), autoCam = a director desk's own auto-cut result (-1 = decide here), zoom = virtual cams' fov.
     */
    public static double[] pose(String key, int src, double[] subject, String uuid, int autoCam, double zoom, float pt) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) return null;
        St s = st(key);
        long now = System.nanoTime();
        double dt = s.last == 0 ? 0.05 : Math.min(0.25, (now - s.last) / 1e9);
        s.last = now;
        if (src == CineMath.CAB) {
            double[] p = com.dogpound.railmap.cctv.TrainCams.pose(mc.world, uuid, pt);
            return p == null ? null : new double[]{p[0], p[1], p[2], p[3], p[4], 70, 0};
        }
        if (src == CineMath.AUTO || src >= 1 && src <= 8) {
            int n = src;
            if (src == CineMath.AUTO) {
                if (autoCam >= 0) n = autoCam;
                else if (subject != null) {
                    List<double[]> cams = new ArrayList<>();
                    for (double[] c : tripods()) if (sees(c[0], c[1], c[2], subject[0], subject[1] + 1.5, subject[2])) cams.add(c);
                    int was = s.tri;
                    s.tri = CineMath.autoCut(cams, subject[0], subject[1], subject[2], s.tri, (now - s.triSince) / 1e9, CineConfig.tripodRange, CineConfig.minShotSeconds);
                    if (s.tri != was) { s.triSince = now; s.init = false; }
                    n = s.tri;
                } else n = 0;
            }
            for (double[] c : tripods()) {
                if ((int) c[3] != n) continue;
                if (subject == null) return new double[]{c[0], c[1], c[2], 0, 0, 60, 0};
                double tx = subject[0], ty = subject[1] + 1.6, tz = subject[2];
                smoothLook(s, tx, ty, tz, dt, 6);
                double[] lk = CineMath.look(c[0], c[1], c[2], s.lx, s.ly, s.lz);
                double dist = Math.sqrt((c[0] - tx) * (c[0] - tx) + (c[1] - ty) * (c[1] - ty) + (c[2] - tz) * (c[2] - tz));
                return new double[]{c[0], c[1], c[2], lk[0], lk[1], CineMath.frameFov(subject[5] * 2 + 4, dist, 0.7), 0};
            }
            return null;
        }
        if (subject == null) return null;
        if (src == CineMath.FLYBY) {
            double fx = -Math.sin(Math.toRadians(subject[3])), fz = Math.cos(Math.toRadians(subject[3]));
            boolean passed = s.spot != null && ((subject[0] - s.spot[0]) * fx + (subject[2] - s.spot[2]) * fz) > 30;
            boolean far = s.spot != null && Math.hypot(subject[0] - s.spot[0], subject[2] - s.spot[2]) > 220;
            if (s.spot == null || passed || far) { s.spot = CineMath.flybySpot(subject[0], subject[1], subject[2], subject[3], subject[4]); s.init = false; }
            smoothLook(s, subject[0], subject[1] + 1.5, subject[2], dt, 8);
            double[] lk = CineMath.look(s.spot[0], s.spot[1], s.spot[2], s.lx, s.ly, s.lz);
            double dist = Math.hypot(subject[0] - s.spot[0], subject[2] - s.spot[2]);
            return new double[]{s.spot[0], s.spot[1], s.spot[2], lk[0], lk[1], Math.max(zoom * 0.6, CineMath.frameFov(subject[5] * 2 + 6, dist, 0.8)), 0};
        }
        double[] f = CineMath.follow(src, subject[0], subject[1], subject[2], subject[3], subject[5], mc.world.getTotalWorldTime() / 20.0 + pt / 20.0);
        if (!s.init) { s.cx = f[0]; s.cy = f[1]; s.cz = f[2]; s.lx = f[3]; s.ly = f[4]; s.lz = f[5]; s.init = true; }
        double k = 1 - Math.exp(-dt * (src == CineMath.ORBIT ? 1.5 : 3.0));
        s.cx += (f[0] - s.cx) * k; s.cy += (f[1] - s.cy) * k; s.cz += (f[2] - s.cz) * k;
        smoothLook(s, f[3], f[4], f[5], dt, 5);
        double[] lk = CineMath.look(s.cx, s.cy, s.cz, s.lx, s.ly, s.lz);
        return new double[]{s.cx, s.cy, s.cz, lk[0], lk[1], zoom, f[6]};
    }

    static void smoothLook(St s, double x, double y, double z, double dt, double rate) {
        if (!s.init) { s.lx = x; s.ly = y; s.lz = z; s.init = true; return; }
        double k = 1 - Math.exp(-dt * rate);
        s.lx += (x - s.lx) * k; s.ly += (y - s.ly) * k; s.lz += (z - s.lz) * k;
    }

    /** a live train as a camera subject */
    public static double[] live(String uuid, float pt) {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.world == null ? null : CineTrains.pose(mc.world, uuid, pt);
    }

    public static void forget(String key) { ST.remove(key); }
}
