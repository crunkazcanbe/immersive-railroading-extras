package com.dogpound.railmap.safety;

import com.dogpound.railmap.auto.TrackPath;
import com.dogpound.railmap.signal.TrackFollower;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/** small geometry helpers shared by the safety systems */
final class Geo {
    private Geo() {}

    /** a point lies on a planned path (within r blocks of its centre-line) */
    static boolean onPath(TrackPath path, double x, double y, double z, double r) {
        for (TrackPath.Step s : path.steps) {
            if (s.points == null) {
                double dx = s.pos.x + 0.5 - x, dz = s.pos.z + 0.5 - z;
                if (dx * dx + dz * dz <= r * r + 1 && Math.abs(s.pos.y - y) < 4) return true;
                continue;
            }
            for (int i = 0; i + 2 < s.points.length; i += 3) {
                double dx = s.points[i] - x, dz = s.points[i + 2] - z;
                if (dx * dx + dz * dz <= r * r && Math.abs(s.points[i + 1] - y) < 4) return true;
            }
        }
        return false;
    }

    /** the path comes within r blocks of a circle's edge (a zone of radius zr around cx, cz) */
    static boolean pathInZone(TrackPath path, double cx, double cz, double zr) {
        for (TrackPath.Step s : path.steps) {
            if (s.points == null) { if (d2(s.pos.x + 0.5, s.pos.z + 0.5, cx, cz) <= zr * zr) return true; continue; }
            for (int i = 0; i + 2 < s.points.length; i += 3) if (d2(s.points[i], s.points[i + 2], cx, cz) <= zr * zr) return true;
        }
        return false;
    }

    static boolean spanInZone(TrackFollower.Span span, double cx, double cz, double zr) {
        for (double[] p : span.points) for (int i = 0; i + 2 < p.length; i += 3) if (d2(p[i], p[i + 2], cx, cz) <= zr * zr) return true;
        return false;
    }

    static boolean spanHas(TrackFollower.Span span, List<double[]> cars) {
        for (double[] c : cars) if (span.distSqTo(c[0], c[1]) <= 2.5 * 2.5) return true;
        return false;
    }

    static double d2(double ax, double az, double bx, double bz) { double dx = ax - bx, dz = az - bz; return dx * dx + dz * dz; }

    static String signalName(BlockPos p) { return p.getX() + "," + p.getZ(); }

    static String xz(long pos) { BlockPos p = BlockPos.fromLong(pos); return p.getX() + "," + p.getY() + "," + p.getZ(); }
}
