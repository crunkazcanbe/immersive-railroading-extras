package com.dogpound.railmap.program;

import com.dogpound.railmap.graph.RailNetwork;
import net.minecraft.util.EnumFacing;

/**
 * Where the railway sits on an NX desk's top. The diagram is a miniature of the real layout lined
 * up with the world (north on the map is north in the world), shrunk to fit the desk's 2.8 x 1.7 block
 * top, which turns with the desk. All coordinates here are block-local: (0,0) is the desk block's corner.
 */
public final class NXLayout {
    /** the desk top in the desk's own (north-facing) model space, in blocks */
    public static final double X0 = -0.85, X1 = 1.85, Z0 = 0.2, Z1 = 1.8;
    /** height of the desk top above the block's floor */
    public static final double TOP = 13.0 / 16.0;

    public final EnumFacing facing;
    public final double scale, netCx, netCz, areaCx, areaCz;
    public final boolean empty;

    public NXLayout(RailNetwork net, EnumFacing facing) {
        this.facing = facing;
        double[] c = toLocal((X0 + X1) / 2, (Z0 + Z1) / 2, facing);
        areaCx = c[0];
        areaCz = c[1];
        boolean sideways = facing == EnumFacing.EAST || facing == EnumFacing.WEST;
        double w = sideways ? Z1 - Z0 : X1 - X0, d = sideways ? X1 - X0 : Z1 - Z0;
        empty = net == null || net.nodes.isEmpty();
        double nx = empty ? 1 : Math.max(1, net.maxX - net.minX), nz = empty ? 1 : Math.max(1, net.maxZ - net.minZ);
        scale = Math.min(w / nx, d / nz) * 0.92;
        netCx = empty ? 0 : (net.minX + net.maxX) / 2;
        netCz = empty ? 0 : (net.minZ + net.maxZ) / 2;
    }

    /** desk model space -> block-local world-aligned (the same turn the blockstate gives the model) */
    public static double[] toLocal(double mx, double mz, EnumFacing f) {
        return switch (f) {
            case EAST -> new double[]{1 - mz, mx};
            case SOUTH -> new double[]{1 - mx, 1 - mz};
            case WEST -> new double[]{mz, 1 - mx};
            default -> new double[]{mx, mz};
        };
    }

    /** block-local world-aligned -> desk model space */
    public static double[] toModel(double lx, double lz, EnumFacing f) {
        return switch (f) {
            case EAST -> new double[]{lz, 1 - lx};
            case SOUTH -> new double[]{1 - lx, 1 - lz};
            case WEST -> new double[]{1 - lz, lx};
            default -> new double[]{lx, lz};
        };
    }

    /** a world position -> where it is drawn on the desk (block-local x, z) */
    public double[] onDesk(double wx, double wz) {
        return new double[]{areaCx + (wx - netCx) * scale, areaCz + (wz - netCz) * scale};
    }

    /** a point on the desk (block-local x, z) -> the world position it stands for */
    public double[] inWorld(double lx, double lz) {
        return new double[]{netCx + (lx - areaCx) / scale, netCz + (lz - areaCz) / scale};
    }

    /** is this block-local point on the desk top? */
    public boolean onTop(double lx, double lz) {
        double[] m = toModel(lx, lz, facing);
        return m[0] >= X0 && m[0] <= X1 && m[1] >= Z0 && m[1] <= Z1;
    }
}
