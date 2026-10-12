package com.dogpound.railmap.drone;

import cam72cam.immersiverailroading.tile.TileRail;
import cam72cam.mod.math.Vec3i;
import com.dogpound.railmap.auto.TrackPath;
import com.dogpound.railmap.track.Rails;
import net.minecraft.block.material.Material;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * A drone's mission route: the track nearest its dock (within 16 blocks), followed the way the points lie for the
 * mission length, set off in the dock's facing (DIR A) or the other way (DIR B). Waypoints every 3 m at the mission
 * height over the rail - lowered under bridges and inside tunnels to keep 1.5 m under the roof. A TUNNEL mission flies
 * to the first portal and through to the far one (portal to portal), then comes back.
 */
public final class DroneRoute {
    private DroneRoute() {}

    /** waypoints {x, y, z, ...} or the reason there is no route */
    public static Object plan(World w, BlockPos dock, EnumFacing facing, int mission, int lengthM, boolean dirB, double alt) {
        try {
            TileRail rail = null;
            double rx = 0, rz = 0;
            search:
            for (int r = 0; r <= 16; r++)
                for (int dx = -r; dx <= r; dx++)
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                        for (int dy = -4; dy <= 3; dy++) {
                            TileRail t = Rails.pieceAt(w, dock.getX() + dx + 0.5, dock.getY() + dy + 0.5, dock.getZ() + dz + 0.5);
                            if (t != null) { rail = t; rx = dock.getX() + dx + 0.5; rz = dock.getZ() + dz + 0.5; break search; }
                        }
                    }
            if (rail == null) return "no track within 16 blocks of the dock";
            EnumFacing f = dirB ? facing.getOpposite() : facing;
            double fx = f.getXOffset(), fz = f.getZOffset();
            cam72cam.mod.world.World umc = cam72cam.mod.world.World.get(w);
            Vec3i from = TrackPath.behind(rail, rx, rail.getPos().y, rz, fx, fz);
            TrackPath path = TrackPath.ahead(umc, rail, from, lengthM);
            List<double[]> pts = new ArrayList<>();
            for (TrackPath.Step s : path.steps) {
                if (s.points != null && s.points.length >= 3) {
                    // the stored points may run against travel: orient by the last point we kept
                    boolean rev = false;
                    if (!pts.isEmpty()) {
                        double[] last = pts.get(pts.size() - 1);
                        rev = sq(s.points[0] - last[0], s.points[2] - last[2]) > sq(s.points[s.points.length - 3] - last[0], s.points[s.points.length - 1] - last[2]);
                    }
                    for (int k = 0; k < s.points.length; k += 3) {
                        int i = rev ? s.points.length - 3 - k : k;
                        add(pts, s.points[i], s.points[i + 1], s.points[i + 2]);
                    }
                } else add(pts, s.pos.x + 0.5, s.pos.y, s.pos.z + 0.5);
            }
            if (pts.size() < 2) return "the track by the dock goes nowhere (route under 6 m)";
            int end = pts.size();
            if (mission == 3) {                                                // TUNNEL: to the far portal
                int in = -1, out = -1;
                for (int i = 0; i < pts.size(); i++) {
                    boolean cov = covered(w, pts.get(i));
                    if (in < 0 && cov) in = i;
                    else if (in >= 0 && !cov) { out = i; break; }
                }
                if (in < 0) return "no tunnel on this route (" + lengthM + " m " + (dirB ? "B" : "A") + ")";
                end = Math.min(pts.size(), (out < 0 ? pts.size() : out) + 3);
            }
            double[] way = new double[end * 3];
            for (int i = 0; i < end; i++) {
                double[] p = pts.get(i);
                way[i * 3] = p[0];
                way[i * 3 + 1] = p[1] + headroom(w, p, alt);
                way[i * 3 + 2] = p[2];
            }
            return way;
        } catch (LinkageError | RuntimeException e) {
            return "route planner failed: " + e;
        }
    }

    private static double sq(double a, double b) { return a * a + b * b; }

    static void add(List<double[]> pts, double x, double y, double z) {
        if (!pts.isEmpty()) { double[] l = pts.get(pts.size() - 1); if (sq(x - l[0], z - l[2]) < 9) return; }
        pts.add(new double[]{x, y, z});
    }

    /** a roof over the rail here (tunnel, bridge, station canopy) */
    static boolean covered(World w, double[] p) {
        BlockPos b = new BlockPos(p[0], p[1], p[2]);
        return !w.canSeeSky(b.up(2)) && !w.canSeeSky(b.up(3));
    }

    /** the height to fly over the rail: the mission's, or 1.5 m under a roof (at least 2 m over the rail) */
    static double headroom(World w, double[] p, double alt) {
        BlockPos b = new BlockPos(p[0], p[1], p[2]);
        for (int h = 2; h <= Math.ceil(alt) + 2; h++) {
            BlockPos q = b.up(h);
            if (!w.isBlockLoaded(q)) break;
            Material m = w.getBlockState(q).getMaterial();
            if (m.isSolid() && m != Material.LEAVES) return Math.max(2, Math.min(alt, h - 1.5));
        }
        return alt;
    }
}
