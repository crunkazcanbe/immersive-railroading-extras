package com.dogpound.railmap.traction;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * The hydrogen fuelling station's shared safety system (batch 5 #6): the emergency shutdown (ESD) trips every compressor,
 * storage bank valve and dispenser within 24 blocks; gas detectors (% of the lower explosive limit, LEL) on each part
 * alarm at 20 % and trip the ESD at 40 %. 1 kg of hydrogen = 2 000 mB of the factory's pi_hydrogen gas.
 */
public final class H2Station {
    private H2Station() {}

    public static final int MB_PER_KG = 2000, REACH = 24;

    /** a part of the station: compressor, storage, dispenser */
    public interface Part {
        void esdTrip(String why);
        boolean esd();
        float lel();
    }

    public static List<TileEntity> parts(World w, BlockPos at) {
        List<TileEntity> out = new ArrayList<>();
        for (TileEntity t : new ArrayList<>(w.loadedTileEntityList))
            if (t instanceof Part && !t.isInvalid() && t.getPos().distanceSq(at) <= REACH * REACH) out.add(t);
        return out;
    }

    public static void esd(World w, BlockPos at, String why) {
        boolean any = false;
        for (TileEntity t : parts(w, at)) { Part p = (Part) t; if (!p.esd()) { p.esdTrip(why); any = true; } }
        if (!any) return;
        AlarmManager.get(w).raise(w, "h2esd:" + (at.getX() >> 5) + "," + (at.getZ() >> 5), AlarmManager.Cat.FIRE, AlarmManager.Sev.CRITICAL,
                "HYDROGEN STATION ESD at " + at.getX() + "," + at.getZ() + ": " + why, at, true);
        Recorder.event(w, "Hydrogen station ESD: " + why);
        w.playSound(null, at, com.dogpound.railmap.ModSounds.BEEP_OK, net.minecraft.util.SoundCategory.BLOCKS, 1.0f, 0.5f);
    }

    /** the ESD is reset part by part; the alarm clears when none is tripped */
    public static void reset(World w, BlockPos at) {
        for (TileEntity t : parts(w, at)) if (((Part) t).esd()) return;
        AlarmManager.get(w).clear("h2esd:" + (at.getX() >> 5) + "," + (at.getZ() >> 5));
    }

    /** a gas detector reading: alarms at 20 % LEL, ESD at 40 % */
    public static void detector(World w, BlockPos at, String part, float lel) {
        String key = "h2gas:" + at.toLong();
        if (lel >= 40) esd(w, at, part + " gas detector " + Math.round(lel) + " % LEL");
        if (lel >= 20) AlarmManager.get(w).raise(w, key, AlarmManager.Cat.FIRE, AlarmManager.Sev.MAJOR, "Hydrogen LEAK at the " + part + " (" + at.getX() + "," + at.getZ() + "): " + Math.round(lel) + " % LEL", at, true);
        else if (lel < 10) AlarmManager.get(w).clear(key);
    }

    public static TileH2Storage storage(World w, BlockPos at, int reach) {
        TileH2Storage best = null;
        double bd = reach * reach;
        for (TileEntity t : new ArrayList<>(w.loadedTileEntityList))
            if (t instanceof TileH2Storage s && !t.isInvalid() && t.getPos().distanceSq(at) < bd) { bd = t.getPos().distanceSq(at); best = s; }
        return best;
    }
}
