package com.dogpound.railmap.auto;

import cam72cam.mod.math.Vec3i;
import com.dogpound.railmap.signal.Aspect;
import com.dogpound.railmap.signal.SignalRegistry;
import com.dogpound.railmap.signal.TileSignalBridge;
import com.dogpound.railmap.signal.TileSignalMast;
import com.dogpound.railmap.signal.TileSpeedSign;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * What the engineer sees out of the window: every signal facing the train and every speed sign
 * along a route, turned into speed restrictions at a distance.
 * <p>
 * A signal or sign only counts if it faces the oncoming train — a signal for the other track
 * direction must not stop us — which is judged against the route's heading at that piece.
 */
public final class Wayside {
    /** One restriction: from {@code distance} blocks ahead, go no faster than {@code kmh}. */
    public static final class Limit {
        public final double distance;
        public final double kmh;
        /** True for a signal at Stop: the train must stand short of it. */
        public final boolean stop;
        public final String what;
        public final BlockPos where;

        Limit(double distance, double kmh, boolean stop, String what, BlockPos where) {
            this.distance = distance;
            this.kmh = kmh;
            this.stop = stop;
            this.what = what;
            this.where = where;
        }
    }

    private Wayside() {}

    /** Signals and signs along {@code path}, nearest first. */
    public static List<Limit> read(net.minecraft.world.World world, TrackPath path) {
        List<Limit> out = new ArrayList<>();
        if (path.steps.isEmpty()) return out;
        int dim = world.provider.getDimension();

        for (TileSignalMast mast : SignalRegistry.masts(dim)) {
            if (mast.isInvalid()) continue;
            Vec3i g = mast.governedRail();
            if (g == null) continue;
            int i = path.indexOf(g);
            if (i < 0 || !facesTrain(mast.facing(), path.headingAt(i))) continue;
            double d = i == 0 ? 0 : path.steps.get(i - 1).distance;
            add(out, d, mast.aspect(), mast.getPos(), mast.style().label);
        }
        for (TileSignalBridge b : SignalRegistry.bridges(dim)) {
            if (b.isInvalid() || !b.isController()) continue;
            for (TileSignalBridge.Head h : b.heads()) {
                int i = path.indexOf(new Vec3i(h.rail.getX(), h.rail.getY(), h.rail.getZ()));
                if (i < 0 || !facesTrain(b.facing(), path.headingAt(i))) continue;
                double d = i == 0 ? 0 : path.steps.get(i - 1).distance;
                add(out, d, h.aspect, b.getPos(), "Signal bridge");
            }
        }
        for (TileSpeedSign sign : SignalRegistry.speedSigns(dim)) {
            if (sign.isInvalid()) continue;
            BlockPos p = sign.getPos();
            double d = path.distanceTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 4.5);
            if (d < 0) continue;
            int i = nearestStep(path, d);
            if (!facesTrain(sign.facing(), path.headingAt(i))) continue;
            out.add(new Limit(d, sign.kmh(), false, "Speed limit " + sign.mph() + " mph", p));
        }
        out.sort((a, b) -> Double.compare(a.distance, b.distance));
        return out;
    }

    private static void add(List<Limit> out, double d, Aspect a, BlockPos pos, String label) {
        if (a == Aspect.DARK) return;                // not signalled / not computed: no restriction
        boolean stop = a.isStop();
        double kmh = stop ? 0 : a.speedKmh >= 999 ? Double.MAX_VALUE : a.speedKmh;
        out.add(new Limit(d, kmh, stop, label + ": " + a.label, pos));
    }

    /** The signal's face points back along the track, towards the train that reads it. */
    static boolean facesTrain(EnumFacing facing, double[] heading) {
        double dot = facing.getXOffset() * heading[0] + facing.getZOffset() * heading[1];
        return dot < -0.3;
    }

    private static int nearestStep(TrackPath path, double d) {
        for (int i = 0; i < path.steps.size(); i++) if (path.steps.get(i).distance >= d) return i;
        return path.steps.size() - 1;
    }

    /**
     * Braking curve: the fastest a train may go now and still get down to {@code targetKmh}
     * within {@code distance} blocks at {@code decel} m/s².
     */
    public static double allowed(double distance, double targetKmh, double decel) {
        double vt = Math.max(0, targetKmh) / 3.6;
        double v = Math.sqrt(vt * vt + 2 * decel * Math.max(0, distance));
        return v * 3.6;
    }
}
