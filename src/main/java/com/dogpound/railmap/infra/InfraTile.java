package com.dogpound.railmap.infra;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.safety.SafetyTile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;

/**
 * Shared base of the batch-3 gear (#36-50): SafetyTile's working controls + 12 V terminal, plus (where the machine is
 * more than a 12 V instrument) its real mains supply from the grid (Supply). A mains burn-out also burns the 12 V
 * terminal, so the electrician's usual screwdriver click on the gear repairs the machine.
 */
public abstract class InfraTile extends SafetyTile {
    /** null = a 12 V instrument: the terminal is its whole supply */
    protected final Supply mains;

    protected InfraTile(Supply mains) { this.mains = mains; }

    /** 12 V control power AND (if it has one) a healthy mains supply */
    public boolean live() { return powered() && (mains == null || mains.ok); }

    @Override
    public void gearChanged() {
        if (mains != null && !term.burnt && mains.burnt) {
            mains.burnt = false;
            Recorder.event(world, wiredName() + " repaired (screwdriver) - mains section rewired");
            AlarmManager.get(world).clear("infraburn:" + pos.toLong());
        }
        super.gearChanged();
    }

    /** draw from the mains for one second (call once a second) */
    protected double mainsTick(double watts) {
        if (mains == null) return 1;
        return mains.tick(world, pos, watts, this::burn, this::tell);
    }

    /** overvoltage destroyed it */
    protected void burn() {
        term.burnt = true;
        if (world instanceof WorldServer ws) ws.spawnParticle(EnumParticleTypes.SMOKE_LARGE, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 30, 0.3, 0.4, 0.3, 0.02);
        world.playSound(null, pos, com.dogpound.railmap.ModSounds.FUSE_BLOW, SoundCategory.BLOCKS, 1f, 0.8f);
        String msg = wiredName() + " BURNT OUT: " + (mains == null ? "" : mains.what) + " - screwdriver to repair once the supply is right";
        AlarmManager.get(world).raise(world, "infraburn:" + pos.toLong(), AlarmManager.Cat.FIRE, AlarmManager.Sev.MAJOR, msg, pos, true);
        Recorder.event(world, msg);
        tell("§c" + msg);
        controlsChanged();
    }

    protected void tell(String msg) {
        for (EntityPlayer p : world.playerEntities) if (p.getDistanceSq(pos) < 24 * 24) p.sendStatusMessage(new TextComponentString(msg), true);
    }

    protected static String f1(double v) { return String.format(java.util.Locale.ROOT, "%.1f", v); }

    @Override
    public String gearState() { return mains == null ? term.status() : mains.status(); }

    protected abstract void store(NBTTagCompound t);
    protected abstract void restore(NBTTagCompound t);

    @Override protected final void save(NBTTagCompound t) { if (mains != null) mains.write(t); store(t); }
    @Override protected final void load(NBTTagCompound t) { if (mains != null) mains.read(t); restore(t); }
}
