package com.dogpound.railmap.stnlife6;

import com.dogpound.railmap.ems.RoadPath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** road spots for buses and taxis: the road cell (feet position) nearest a block, and long trips cut into legs */
public final class Roads {
    private Roads() {}

    /** feet position on a road block within r blocks of `at` (nearest first), or null */
    public static BlockPos near(World w, BlockPos at, int r) {
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                double d = dx * dx + dz * dz;
                if (d >= bd || d > r * r) continue;
                BlockPos c = at.add(dx, 0, dz);
                if (!w.isBlockLoaded(c)) continue;
                BlockPos g = RoadPath.ground(w, c.up(2), 5);
                if (g == null || !RoadPath.isRoad(w.getBlockState(g.down()))) continue;
                best = g; bd = d;
            }
        return best;
    }

    /** the next target on the way from `from` to `to`: `to` itself when within 110 blocks, else a road spot ~100 along */
    public static BlockPos leg(World w, BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ(), d = Math.sqrt(dx * dx + dz * dz);
        if (d <= 110) return to;
        for (double k = 100; k > 40; k -= 15) {
            BlockPos mid = new BlockPos(from.getX() + dx / d * k, (from.getY() + to.getY()) / 2, from.getZ() + dz / d * k);
            BlockPos g = near(w, mid, 10);
            if (g != null) return g;
        }
        return to;
    }

    /** blocks of road between stops, straight-line x 1.3 (an ETA estimate) */
    public static double run(BlockPos a, BlockPos b) { return Math.sqrt(a.distanceSq(b)) * 1.3; }

    /** seconds to drive `blocks` at road speed `bpt` blocks/tick */
    public static int secs(double blocks, double bpt) { return (int) Math.ceil(blocks / Math.max(0.05, bpt) / 20); }
}
