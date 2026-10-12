package com.dogpound.railmap.freight6;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * A warehouse forklift and its driver (batch 6 #28). Only a view of a TileWarehouseOffice worker: the office moves the
 * worker, this entity follows it (and shows the pallet on its forks). Never saved - the office spawns them near players.
 */
public class EntityForklift extends Entity {
    static final DataParameter<ItemStack> LOAD = EntityDataManager.createKey(EntityForklift.class, DataSerializers.ITEM_STACK);
    static final DataParameter<Float> FORKS = EntityDataManager.createKey(EntityForklift.class, DataSerializers.FLOAT);
    private BlockPos office;
    private int idx;
    public float forkY, prevForkY;

    public EntityForklift(World w) {
        super(w);
        setSize(1.2f, 2.2f);
        noClip = true;
    }

    public EntityForklift(World w, BlockPos office, int idx) {
        this(w);
        this.office = office;
        this.idx = idx;
    }

    @Override protected void entityInit() { dataManager.register(LOAD, ItemStack.EMPTY); dataManager.register(FORKS, 0f); }

    public ItemStack load() { return dataManager.get(LOAD); }
    public float forks() { return dataManager.get(FORKS); }

    @Override
    public void onUpdate() {
        prevPosX = posX; prevPosY = posY; prevPosZ = posZ; prevRotationYaw = rotationYaw;
        prevForkY = forkY;
        if (world.isRemote) { forkY += (forks() - forkY) * 0.2f; return; }
        if (office == null || !(world.getTileEntity(office) instanceof TileWarehouseOffice o) || idx >= o.workers().size()) { setDead(); return; }
        TileWarehouseOffice.Worker w = o.workers().get(idx);
        if (w.entity != getEntityId() && w.entity >= 0) { setDead(); return; }
        float yaw = w.yaw;
        setPositionAndRotation(w.x, w.y, w.z, yaw, 0);
        if (!ItemStack.areItemStacksEqual(load(), w.fork)) dataManager.set(LOAD, w.fork.copy());
        float fy = w.phase == 2 || w.phase == 4 || w.phase == 6 || w.phase == 8 ? (w.phase == 4 || w.phase == 6 ? 1.4f : 0.5f) : w.fork.isEmpty() ? 0.05f : 0.25f;
        if (Math.abs(fy - forks()) > 0.01f) dataManager.set(FORKS, fy);
    }

    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean canBePushed() { return false; }
    @Override public AxisAlignedBB getCollisionBoundingBox() { return null; }
    @Override public boolean isInRangeToRenderDist(double d) { return d < 96 * 96; }
    @Override public void setPositionAndRotationDirect(double x, double y, double z, float yaw, float pitch, int inc, boolean teleport) { setPosition(x, y, z); setRotation(yaw, pitch); }
    @Override public boolean writeToNBTOptional(NBTTagCompound t) { return false; }
    @Override public boolean writeToNBTAtomically(NBTTagCompound t) { return false; }
    @Override protected void readEntityFromNBT(NBTTagCompound t) {}
    @Override protected void writeEntityToNBT(NBTTagCompound t) {}
}
