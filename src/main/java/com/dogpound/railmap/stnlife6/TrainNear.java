package com.dogpound.railmap.stnlife6;

import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;

/** an Immersive Railroading car alongside a platform device: is one there, is it stopped, where is its rail top */
public final class TrainNear {
    private TrainNear() {}

    private static final boolean IR = Loader.isModLoaded("immersiverailroading");

    /** {0 none / 1 moving / 2 stopped, rail-top y} of the nearest car within `r` blocks across and 3 up/down */
    public static double[] at(World w, BlockPos p, double r) {
        if (!IR) return new double[]{0, 0};
        try {
            double best = r * r, y = 0;
            int state = 0;
            for (EntityRollingStock s : cam72cam.mod.world.World.get(w).getEntities(EntityRollingStock.class)) {
                if (s.isDead()) continue;
                cam72cam.mod.math.Vec3d v = s.getPosition();
                if (Math.abs(v.y - p.getY()) > 3) continue;
                double d = p.distanceSqToCenter(v.x, p.getY() + 0.5, v.z);
                double half = 8;
                try { half = Math.max(4, s.getDefinition().getLength(s.gauge) / 2); } catch (RuntimeException ignored) { }
                if (d > (r + half) * (r + half)) continue;
                boolean stopped = !(s instanceof EntityMoveableRollingStock m) || m.getCurrentSpeed() == null || Math.abs(m.getCurrentSpeed().metric()) < 0.5;
                int st = stopped ? 2 : 1;
                if (st > state || d < best) { state = Math.max(state, st); best = Math.min(best, d); y = v.y; }
            }
            return new double[]{state, y};
        } catch (RuntimeException | LinkageError e) {
            return new double[]{0, 0};
        }
    }
}
