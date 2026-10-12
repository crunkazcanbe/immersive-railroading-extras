package com.dogpound.railmap.block;

import com.dogpound.railmap.grid.Panel;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/**
 * Where things are on the Control Center console, in the master block's model pixels with the console facing north
 * (front toward -z, the operator's left toward +x, the desk spanning x -16..32 over three blocks, 2 blocks tall).
 * The renderer (ConsoleRenderer) and the click test here use the same numbers, so what you see is what you press.
 */
public final class ConsoleGeometry {
    private ConsoleGeometry() {}

    /** the desk top: its front edge, and how far it leans back (a real control desk slopes ~22.5 degrees) */
    public static final double DESK_Y = 11.5, DESK_Z = 1.0;
    public static final double TILT = 67.5;                    // panel frame -> desk: rotate about x (90 - 22.5)
    /** the panel frame's x is 16 - u; the desk's u 0..48 runs x 32..-16, so the frame sits 16 px over */
    public static final double DESK_X = 16;

    /** the big screen: glass face plane and its rectangle (x from the operator's left edge = high x) */
    public static final double SCREEN_Z = 11.9, SCREEN_X0 = 30.6, SCREEN_X1 = -14.6, SCREEN_Y0 = 17.4, SCREEN_Y1 = 30.6;

    public static int turns(EnumFacing f) {
        int i = f.getHorizontalIndex();
        return i == 2 ? 0 : i == 3 ? 1 : i == 0 ? 2 : 3;   // N 0, E 90, S 180, W 270
    }

    /** the player's eye ray in the master block's north-facing model pixels: {ex, ey, ez, dx, dy, dz} */
    static double[] ray(BlockPos pos, EnumFacing facing, EntityPlayer player) {
        net.minecraft.util.math.Vec3d eye = player.getPositionEyes(1f), look = player.getLook(1f);
        double ex = eye.x - pos.getX() - 0.5, ey = eye.y - pos.getY(), ez = eye.z - pos.getZ() - 0.5, dx = look.x, dy = look.y, dz = look.z;
        for (int i = 0; i < turns(facing); i++) {           // undo the blockstate's turns (as BlockGrid.panelHit)
            double nx = ez, nz = -ex;
            ex = nx;
            ez = nz;
            nx = dz;
            nz = -dx;
            dx = nx;
            dz = nz;
        }
        return new double[]{(ex + 0.5) * 16, ey * 16, (ez + 0.5) * 16, dx, dy, dz};
    }

    /** the desk control the player points at, or null */
    public static Panel.C hit(TileDispatcherBoard te, EntityPlayer player) {
        double[] uv = deskUV(te.getPos(), te.facing(), player);
        return uv == null ? null : ConsolePanel.DESK.hitAt(uv[0], uv[1]);
    }

    /** (u, v) on the desk where the player's look meets it, or null if it misses the desk */
    public static double[] deskUV(BlockPos pos, EnumFacing facing, EntityPlayer player) {
        double[] r = ray(pos, facing, player);
        double th = Math.toRadians(TILT), c = Math.cos(th), s = Math.sin(th);
        // into the panel frame: move to the pivot, then undo the tilt (rotate -TILT about x)
        double px = r[0] - DESK_X, py = r[1] - DESK_Y, pz = r[2] - DESK_Z;
        double fy = py * c + pz * s, fz = -py * s + pz * c;
        double dy = r[4] * c + r[5] * s, dz = -r[4] * s + r[5] * c, dx = r[3];
        if (Math.abs(dz) < 1e-6) return null;
        double t = (ConsolePanel.FACE - fz) / dz;
        if (t < 0 || t > 6 * 16) return null;
        double hx = px + dx * t, hy = fy + dy * t;
        double u = 16 - hx, v = hy;
        if (u < -1 || u > 49 || v < -1 || v > 13) return null;
        return new double[]{u, v};
    }

    /** does the player's look land on the big screen? */
    public static boolean onScreen(BlockPos pos, EnumFacing facing, EntityPlayer player) {
        double[] r = ray(pos, facing, player);
        if (Math.abs(r[5]) < 1e-6) return false;
        double t = (SCREEN_Z - r[2]) / r[5];
        if (t < 0) return false;
        double x = r[0] + r[3] * t, y = r[1] + r[4] * t;
        return x <= SCREEN_X0 + 1 && x >= SCREEN_X1 - 1 && y >= SCREEN_Y0 - 1 && y <= SCREEN_Y1 + 1;
    }
}
