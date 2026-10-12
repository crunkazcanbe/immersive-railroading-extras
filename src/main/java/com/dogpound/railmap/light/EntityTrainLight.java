package com.dogpound.railmap.light;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/**
 * Client-only, invisible point of light for a moving train (a headlight beam spot, a lit carriage). It exists only so a
 * dynamic-lights mod (Celeritas Dynamic Lights) can light the world around it: its handler reads `lum`. Never saved,
 * never sent, killed at once if one ever appears on a server.
 */
public class EntityTrainLight extends Entity {
    public int lum;

    public EntityTrainLight(World w) {
        super(w);
        setSize(0.05f, 0.05f);
        noClip = true;
        isImmuneToFire = true;
        setSilent(true);
        setInvisible(true);
        setEntityInvulnerable(true);
    }

    @Override protected void entityInit() {}
    @Override protected void readEntityFromNBT(NBTTagCompound t) {}
    @Override protected void writeEntityToNBT(NBTTagCompound t) {}
    @Override public boolean writeToNBTOptional(NBTTagCompound t) { return false; }
    @Override public boolean writeToNBTAtomically(NBTTagCompound t) { return false; }
    @Override public boolean isInRangeToRenderDist(double d) { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean canBePushed() { return false; }

    /** onEntityUpdate is where the dynamic-lights mod tracks its light sources */
    @Override
    public void onUpdate() {
        if (!world.isRemote) { setDead(); return; }
        onEntityUpdate();
    }
}
