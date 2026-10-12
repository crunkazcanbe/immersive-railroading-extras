package com.dogpound.railmap.infra;

import com.dogpound.railmap.grid.GridData;
import net.minecraft.world.World;

import java.util.List;

/** Wayside energy storage glue for the grid's traction second (#38): GridTicker.settleRegen / supply call these. */
public final class Ess {
    private Ess() {}

    /** regen the trains on this network couldn't use: the stores wired to it take what they can (FE) */
    public static long absorb(World w, GridData.Net net, long fe) {
        if (fe <= 0 || net == null) return 0;
        long took = 0;
        for (TileWaysideEss e : TileWaysideEss.REG.all(w)) {
            if (took >= fe) break;
            if (e.net() != null && e.net().id == net.id) took += e.absorb(fe - took);
        }
        return took;
    }

    /** a train fed by this feeder wants fe more: the stores on its network give per their mode */
    public static long give(World w, GridData.Net net, long feeder, GridData.Node n, long fe, boolean gridOk) {
        if (fe <= 0) return 0;
        long gave = 0;
        double feederKw = n == null ? 0 : n.drawLast / 10.0;
        for (TileWaysideEss e : TileWaysideEss.REG.all(w)) {
            if (gave >= fe) break;
            if (e.net() != null && e.net().id == net.id) gave += e.give(fe - gave, gridOk, feederKw);
        }
        return gave;
    }

    public static void rows(World w, List<String[]> out) {
        for (TileWaysideEss e : TileWaysideEss.REG.all(w)) out.add(e.row());
    }
}
