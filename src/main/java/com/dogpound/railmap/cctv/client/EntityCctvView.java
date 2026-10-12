package com.dogpound.railmap.cctv.client;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/** The invisible "eye" a camera view is rendered from (never spawned: only set as the render view entity for one pass). */
public class EntityCctvView extends Entity {
    public EntityCctvView(World w) {
        super(w);
        setSize(0.01f, 0.01f);
        noClip = true;
    }

    public void place(double x, double y, double z, float yaw, float pitch) {
        posX = prevPosX = lastTickPosX = x;
        posY = prevPosY = lastTickPosY = y;
        posZ = prevPosZ = lastTickPosZ = z;
        rotationYaw = prevRotationYaw = yaw;
        rotationPitch = prevRotationPitch = pitch;
        chunkCoordX = (int) Math.floor(x) >> 4;
        chunkCoordY = (int) Math.floor(y) >> 4;
        chunkCoordZ = (int) Math.floor(z) >> 4;
        setEntityBoundingBox(new net.minecraft.util.math.AxisAlignedBB(x - 0.005, y - 0.005, z - 0.005, x + 0.005, y + 0.005, z + 0.005));
    }

    @Override public float getEyeHeight() { return 0; }
    @Override protected void entityInit() {}
    @Override protected void readEntityFromNBT(NBTTagCompound t) {}
    @Override protected void writeEntityToNBT(NBTTagCompound t) {}
}
