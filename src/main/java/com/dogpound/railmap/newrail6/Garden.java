package com.dogpound.railmap.newrail6;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import java.util.Locale;
import java.util.function.Predicate;

/**
 * Ride-on garden railway geometry, batch 6 #19 (7 1/4 inch gauge, about 1:8): each Garden Track block lays itself
 * straight or as a quarter-circle curve to the garden track beside it, and a train runs block to block along those
 * pieces. Pure maths (no world writes), shared by the server, the client renderer and the self-test.
 */
public final class Garden {
    private Garden() {}

    public enum Shape implements IStringSerializable {
        NS(EnumFacing.NORTH, EnumFacing.SOUTH), EW(EnumFacing.EAST, EnumFacing.WEST),
        NE(EnumFacing.NORTH, EnumFacing.EAST), NW(EnumFacing.NORTH, EnumFacing.WEST), SE(EnumFacing.SOUTH, EnumFacing.EAST), SW(EnumFacing.SOUTH, EnumFacing.WEST);

        public final EnumFacing a, b;

        Shape(EnumFacing a, EnumFacing b) { this.a = a; this.b = b; }

        public boolean has(EnumFacing f) { return f == a || f == b; }
        public EnumFacing other(EnumFacing f) { return f == a ? b : f == b ? a : null; }
        public boolean curve() { return a.getAxis() != b.getAxis(); }

        @Override public String getName() { return name().toLowerCase(Locale.ROOT); }

        public static Shape of(EnumFacing x, EnumFacing y) {
            for (Shape s : values()) if (s.has(x) && s.has(y) && x != y) return s;
            return NS;
        }
    }

    /** how a piece lays itself from which sides have garden track beside it: straight through first, else a curve, else toward its one neighbour */
    public static Shape shapeFor(Predicate<EnumFacing> track) {
        boolean n = track.test(EnumFacing.NORTH), s = track.test(EnumFacing.SOUTH), e = track.test(EnumFacing.EAST), w = track.test(EnumFacing.WEST);
        if (n && s) return Shape.NS;
        if (e && w) return Shape.EW;
        if (n && e) return Shape.NE;
        if (n && w) return Shape.NW;
        if (s && e) return Shape.SE;
        if (s && w) return Shape.SW;
        if (e || w) return Shape.EW;
        return Shape.NS;
    }

    /** the piece at p, or null when it is not garden track */
    public static Shape at(IBlockAccess w, BlockPos p) {
        if (w.getBlockState(p).getBlock() != NR6Registry.gardenTrack) return null;
        return shapeFor(f -> w.getBlockState(p.offset(f)).getBlock() == NR6Registry.gardenTrack);
    }

    static double[] mid(EnumFacing f) {
        return switch (f) {
            case NORTH -> new double[]{0.5, 0};
            case SOUTH -> new double[]{0.5, 1};
            case WEST -> new double[]{0, 0.5};
            default -> new double[]{1, 0.5};
        };
    }

    /** length of the piece run from side in to side out, metres */
    public static double len(EnumFacing in, EnumFacing out) { return in.getAxis() == out.getAxis() ? 1 : Math.PI / 4; }

    /** {x, z} inside the block (0..1) a fraction t along the piece from side in to side out */
    public static double[] local(EnumFacing in, EnumFacing out, double t) {
        double[] p0 = mid(in), p1 = mid(out);
        if (in.getAxis() == out.getAxis()) return new double[]{p0[0] + (p1[0] - p0[0]) * t, p0[1] + (p1[1] - p0[1]) * t};
        double cx = in == EnumFacing.WEST || out == EnumFacing.WEST ? 0 : 1, cz = in == EnumFacing.NORTH || out == EnumFacing.NORTH ? 0 : 1;
        double a0 = Math.atan2(p0[1] - cz, p0[0] - cx), a1 = Math.atan2(p1[1] - cz, p1[0] - cx);
        double d = a1 - a0;
        while (d > Math.PI) d -= 2 * Math.PI;
        while (d < -Math.PI) d += 2 * Math.PI;
        double a = a0 + d * t;
        return new double[]{cx + 0.5 * Math.cos(a), cz + 0.5 * Math.sin(a)};
    }

    /** where a train is on the line: the block, the sides it came in by and goes out by, the fraction along */
    public static final class State {
        public BlockPos cur;
        public EnumFacing in, out;
        public double t;
        /** walking stopped at a buffer (no more track) */
        public boolean end;

        public State(BlockPos cur, EnumFacing in, EnumFacing out, double t) { this.cur = cur; this.in = in; this.out = out; this.t = t; }

        public State copy() { return new State(cur, in, out, t); }

        /** {x, y, z, yaw} in the world */
        public double[] pose() {
            double[] a = local(in, out, Math.max(0, t - 0.02)), b = local(in, out, Math.min(1, t + 0.02)), p = local(in, out, t);
            return new double[]{cur.getX() + p[0], cur.getY() + RAIL_TOP, cur.getZ() + p[1], Ferry.yaw(b[0] - a[0], b[1] - a[1])};
        }
    }

    /** height of the rail tops above the block bottom */
    public static final double RAIL_TOP = 3.5 / 16;

    /** run a state forward d metres along the track (stops at a buffer: s.end) */
    public static State forward(IBlockAccess w, State s, double d) {
        State o = s.copy();
        o.end = false;
        double L = len(o.in, o.out);
        o.t += d / L;
        while (o.t >= 1) {
            BlockPos n = o.cur.offset(o.out);
            EnumFacing in = o.out.getOpposite();
            Shape sh = at(w, n);
            if (sh == null || !sh.has(in)) { o.t = 1; o.end = true; return o; }
            double over = (o.t - 1) * L;
            o.cur = n; o.in = in; o.out = sh.other(in);
            L = len(o.in, o.out);
            o.t = over / L;
        }
        return o;
    }

    /** walk a state back d metres (where the cars behind the locomotive are) */
    public static State back(IBlockAccess w, State s, double d) {
        State o = s.copy();
        double L = len(o.in, o.out);
        o.t -= d / L;
        while (o.t < 0) {
            BlockPos p = o.cur.offset(o.in);
            EnumFacing out = o.in.getOpposite();
            Shape sh = at(w, p);
            if (sh == null || !sh.has(out)) { o.t = 0; o.end = true; return o; }
            double under = -o.t * L;
            o.cur = p; o.out = out; o.in = sh.other(out);
            L = len(o.in, o.out);
            o.t = 1 - under / L;
        }
        return o;
    }
}
