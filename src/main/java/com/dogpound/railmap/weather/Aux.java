package com.dogpound.railmap.weather;

import com.dogpound.railmap.block.TilePowerPost;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * Lineside auxiliary power for heaters and pumps: any mod's energy cable / battery right beside the cabinet first, else
 * the overhead line through an auxiliary transformer (a Track Feeder covering it, as German points heating is fed),
 * else a live Substation in range. Once a second; 1 FE/s = 100 W.
 */
final class Aux {
    private Aux() {}

    /** draw this many watts for one second: returns the share delivered (0..1); src[0] = where it came from */
    static double draw(World w, BlockPos p, double watts, String[] src) {
        long fe = Math.max(1, Math.round(watts / 100));
        for (EnumFacing f : EnumFacing.values()) {
            TileEntity t = w.getTileEntity(p.offset(f));
            if (t == null || !t.hasCapability(CapabilityEnergy.ENERGY, f.getOpposite())) continue;
            IEnergyStorage es = t.getCapability(CapabilityEnergy.ENERGY, f.getOpposite());
            if (es == null || !es.canExtract()) continue;
            int got = es.extractEnergy((int) Math.min(Integer.MAX_VALUE, fe), false);
            if (got > 0) { src[0] = "cable (FE)"; return Math.min(1, got / (double) fe); }
        }
        long got = com.dogpound.railmap.grid.GridTicker.supply(w, p, true, false, fe);
        if (got > 0) { src[0] = "OHLE aux transformer"; return Math.min(1, got / (double) fe); }
        for (TilePowerPost t : TilePowerPost.all(w.provider.getDimension())) {
            if (t.kind() != TilePowerPost.Kind.SUBSTATION || !t.live() || t.where().distanceSq(p) > (double) t.range() * t.range()) continue;
            if (t.draw((int) Math.min(Integer.MAX_VALUE, fe))) { src[0] = "substation"; return 1; }
        }
        src[0] = "NO SUPPLY";
        return 0;
    }
}
