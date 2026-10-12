package com.dogpound.railmap.infra;

import com.dogpound.railmap.planning.PlanData;
import com.dogpound.railmap.safety.TilePossessionSwitch;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Is this spot inside a possession - a planned engineering possession in force, or a Track Blocking Switch granted? */
public final class Possessions {
    private Possessions() {}

    /** "" = not covered, else what covers it */
    public static String cover(World w, BlockPos p) {
        for (PlanData.Work k : PlanData.get(w).works)
            if (k.kind == PlanData.Kind.POSSESSION && k.live() && Math.hypot(p.getX() + 0.5 - k.x, p.getZ() + 0.5 - k.z) <= k.r) return "possession #" + k.id + " " + k.name;
        for (TileEntity t : w.loadedTileEntityList) {
            if (!(t instanceof TilePossessionSwitch s) || !s.granted()) continue;
            BlockPos q = t.getPos();
            if (q.distanceSq(p) <= (double) s.zone() * s.zone() && Math.abs(q.getY() - p.getY()) < 12) return "line blocked by " + s.holder();
        }
        return "";
    }
}
