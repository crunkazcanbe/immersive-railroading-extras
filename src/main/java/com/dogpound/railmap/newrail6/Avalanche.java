package com.dogpound.railmap.newrail6;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Avalanche protection, batch 6 #20: the hazard the detector works out (snowpack depth on the slope above the line, slope
 * angle - avalanches start on 30-45 degree slopes, thaw and fresh loading make it worse), which track the concrete
 * galleries cover, and what an avalanche leaves on the line. Pure functions where possible (self-tested).
 */
public final class Avalanche {
    private Avalanche() {}

    public static final String[] LEVEL = {"LOW", "MODERATE", "CONSIDERABLE", "HIGH", "VERY HIGH"};

    /** 0..1 avalanche hazard */
    public static double hazard(double snowMm, double slopeDeg, double airC, boolean loading) {
        double snow = Math.max(0, Math.min(1, (snowMm - 80) / 700));
        double slope = slopeDeg < 22 ? 0 : slopeDeg < 30 ? (slopeDeg - 22) / 8 * 0.7 : slopeDeg <= 45 ? 1 : slopeDeg <= 60 ? 1 - (slopeDeg - 45) / 15 * 0.6 : 0.3;
        double thaw = airC > 0 ? 1 + Math.min(0.5, airC / 10) : 1;
        double load = loading ? 1.25 : 1;
        return Math.max(0, Math.min(1, snow * slope * thaw * load));
    }

    /** the five-step danger scale */
    public static int level(double h) { return h < 0.2 ? 0 : h < 0.4 ? 1 : h < 0.6 ? 2 : h < 0.8 ? 3 : 4; }

    /** natural release chance per 10 s check */
    public static double releaseChance(double h) { return h <= 0.55 ? 0 : (h - 0.55) * 0.02; }

    /** a gallery roof covers the rail at p (galleries overhang toward their front, 1-2 blocks) */
    public static boolean covered(World w, BlockPos p) {
        for (EnumFacing f : EnumFacing.HORIZONTALS)
            for (int k = 1; k <= 2; k++)
                for (int dy = -1; dy <= 2; dy++) {
                    BlockPos g = p.offset(f.getOpposite(), k).up(dy);
                    if (!w.isBlockLoaded(g)) continue;
                    IBlockState s = w.getBlockState(g);
                    if (s.getBlock() == NR6Registry.gallery && s.getValue(NR6Blocks.Piece.FACING) == f) return true;
                }
        return false;
    }

    /** something lying on the line over this rail (snow, rock, earth - anything solid) */
    public static boolean obstructed(World w, BlockPos rail) {
        BlockPos q = rail.up();
        if (!w.isBlockLoaded(q)) return false;
        IBlockState s = w.getBlockState(q);
        Block b = s.getBlock();
        if (b == Blocks.AIR || b == NR6Registry.gardenTrack) return false;
        if (b == Blocks.SNOW_LAYER) return s.getValue(net.minecraft.block.BlockSnow.LAYERS) >= 3;
        return s.getMaterial().isSolid() || b == Blocks.SNOW;
    }

    /** snow (mm) a vanilla world shows on the slope: the share of snow-capped columns, and how many layers they carry */
    public static double vanillaSnow(int columns, int snowy, int layers) {
        if (columns == 0 || snowy == 0) return 0;
        return (double) snowy / columns * 350 + (double) layers / snowy * 25;
    }
}
