package com.dogpound.railmap.track;

import com.dogpound.railmap.alarm.Recorder;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;

import java.util.Map;

/**
 * Cross-passage / refuge door (her ChatGPT list #16): the green fire door between a running tunnel and its twin bore or
 * a refuge. OPEN swings it (self-closing after 15 s, like a real fire door); in an emergency the panel releases it
 * (held open) and starts the refuge pressurisation fan so smoke can't follow people in. Counts the people sheltering
 * behind it, so the control room knows. Exit signs measure their distances to these doors.
 */
public class TileCrossPassage extends TrackGear {
    private int openSecs, occupied;
    private boolean pressurised;

    public String id() { return "XP" + Math.floorMod(pos.getX() * 13 + pos.getZ() * 17, 100); }
    @Override public String wiredName() { return "Cross-passage " + id(); }
    public boolean open() { return openSecs > 0 || pressurised; }
    public int occupied() { return occupied; }

    @Override public String gearState() { return (open() ? "door OPEN" : "door closed") + (pressurised ? " · refuge PRESSURISED" : "") + " · " + occupied + " sheltering"; }
    @Override public int wiredLamp() { return !powered() ? 3 : pressurised ? 2 : 1; }
    @Override public void gearInfo(Map<String, Object> m) { m.put("open", open()); m.put("pressurised", pressurised); m.put("sheltering", occupied); }

    @Override
    protected void tickGear() {
        if (age % 20 != 0) return;
        if (openSecs > 0 && --openSecs == 0) world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("station.door_close"), SoundCategory.BLOCKS, 0.9f, 1.0f);
        TileTunnelControl c = TileTunnelControl.of(world, pos);
        boolean em = c != null && c.emergency();
        if (em != pressurised) { pressurised = em && powered(); if (pressurised) Recorder.event(world, "CROSS-PASSAGE " + id() + " released and pressurised"); }
        EnumFacing back = controlFacing().getOpposite();
        AxisAlignedBB refuge = new AxisAlignedBB(pos.offset(back)).union(new AxisAlignedBB(pos.offset(back, 5))).grow(2, 1, 2);
        occupied = world.getEntitiesWithinAABB(EntityPlayer.class, refuge).size();
        put("open", open());
        put("shut", !open());
        put("press", pressurised);
        put("occ", occupied > 0);
        put("emerg", em);
        put("pwr", powered());
        text("id", id());
        text("occn", occupied + " IN REFUGE");
    }

    @Override public int controlValue(String id) { return id.equals("open") || id.equals("shut") ? ctl.get(id) : super.controlValue(id); }
    @Override protected boolean keepTextUnpowered() { return true; }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (id.equals("test")) return lampTest();
        if (id.equals("p_open")) {
            openSecs = 15;
            world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("station.door_open"), SoundCategory.BLOCKS, 0.9f, 1.0f);
            controlsChanged();
            return "§aDoor open - it closes itself in 15 s";
        }
        return null;
    }

    @Override protected void save(NBTTagCompound t) { t.setBoolean("press", pressurised); }
    @Override protected void load(NBTTagCompound t) { pressurised = t.getBoolean("press"); }
}
