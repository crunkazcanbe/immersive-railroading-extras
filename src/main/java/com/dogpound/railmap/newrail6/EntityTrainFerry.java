package com.dogpound.railmap.newrail6;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

/**
 * The train ferry (batch 6 #17): an 80 m roll-on stern-loader drawn at 1/6 scale from its bbkit model. Only a view of its
 * route (NR6Data.Route): the server places it each tick, the client glides to it. Never saved - Ferry spawns it while
 * someone is near. Right-click to go aboard while it is berthed (16 on the passenger deck); sneak to go ashore at a berth.
 * Shows how many cars are chained down on the deck.
 */
public class EntityTrainFerry extends Entity {
    static final DataParameter<Integer> CH = EntityDataManager.createKey(EntityTrainFerry.class, DataSerializers.VARINT);
    static final DataParameter<Integer> CARS = EntityDataManager.createKey(EntityTrainFerry.class, DataSerializers.VARINT);
    static final DataParameter<Byte> FLAGS = EntityDataManager.createKey(EntityTrainFerry.class, DataSerializers.BYTE);   // 1 berthed, 2 door open, 4 under way

    private double lx, ly, lz;
    private float lyaw;
    private int steps;
    /** client: the stern door's angle 0..1 and the screws' turn */
    public float door, prevDoor, screw;

    public EntityTrainFerry(World w) {
        super(w);
        setSize(5.5f, 3.5f);
        ignoreFrustumCheck = true;
        noClip = true;
    }

    public EntityTrainFerry(World w, int ch) { this(w); dataManager.set(CH, ch); }

    @Override
    protected void entityInit() {
        dataManager.register(CH, 0);
        dataManager.register(CARS, 0);
        dataManager.register(FLAGS, (byte) 0);
    }

    public int ch() { return dataManager.get(CH); }
    public int cars() { return dataManager.get(CARS); }
    public boolean berthed() { return (dataManager.get(FLAGS) & 1) != 0; }
    public boolean doorOpen() { return (dataManager.get(FLAGS) & 2) != 0; }
    public boolean underWay() { return (dataManager.get(FLAGS) & 4) != 0; }

    @Override
    public void onUpdate() {
        prevPosX = posX; prevPosY = posY; prevPosZ = posZ;
        prevRotationYaw = rotationYaw;
        if (world.isRemote) {
            prevDoor = door;
            door += (doorOpen() ? 1 : -1) * 0.02f;
            door = MathHelper.clamp(door, 0, 1);
            if (underWay()) screw += 0.4f;
            if (steps > 0) {
                double x = posX + (lx - posX) / steps, y = posY + (ly - posY) / steps, z = posZ + (lz - posZ) / steps;
                rotationYaw += MathHelper.wrapDegrees(lyaw - rotationYaw) / steps;
                steps--;
                setPosition(x, y, z);
            }
            return;
        }
        NR6Data.Route r = NR6Data.get(world).routes.get(ch());
        double[] p = r == null || !r.paired() ? null : Ferry.pose(r);
        if (p == null) { eject(); setDead(); return; }
        setPosition(p[0], p[1] + Math.sin(ticksExisted * 0.05) * 0.04, p[2]);
        rotationYaw = (float) p[3];
        dataManager.set(CARS, r.deck.size());
        dataManager.set(FLAGS, (byte) ((r.phase == NR6Data.BERTHED ? 1 : 0) | (r.doorOpen && r.phase == NR6Data.BERTHED ? 2 : 0) | (r.phase == NR6Data.SAILING ? 4 : 0)));
    }

    boolean ejecting;

    void eject() { ejecting = true; for (Entity e : new java.util.ArrayList<>(getPassengers())) e.dismountRidingEntity(); }

    @Override
    public void setPositionAndRotationDirect(double x, double y, double z, float yaw, float pitch, int inc, boolean teleport) {
        lx = x; ly = y; lz = z; lyaw = yaw; steps = teleport ? 1 : 4;
    }

    @Override
    public boolean processInitialInteract(EntityPlayer p, EnumHand hand) {
        if (world.isRemote || isPassenger(p)) return true;
        if (!berthed() && !p.capabilities.isCreativeMode) { p.sendStatusMessage(new TextComponentString("§cThe ferry is under way - board at a berth"), true); return true; }
        if (getPassengers().size() >= 16) { p.sendStatusMessage(new TextComponentString("§cPassenger deck full"), true); return true; }
        p.startRiding(this);
        p.sendStatusMessage(new TextComponentString("§bAboard the train ferry (route " + ch() + ") - " + cars() + " cars on the deck · sneak to go ashore at a berth"), true);
        return true;
    }

    @Override protected boolean canFitPassenger(Entity e) { return getPassengers().size() < 16; }
    @Override public boolean canBeCollidedWith() { return !isDead; }
    @Override public boolean canBePushed() { return false; }
    @Override public AxisAlignedBB getCollisionBoundingBox() { return null; }
    @Override public boolean shouldRiderSit() { return false; }

    @Override
    public void updatePassenger(Entity e) {
        if (!isPassenger(e)) return;
        int k = getPassengers().indexOf(e);
        double yr = Math.toRadians(rotationYaw);
        double fx = -Math.sin(yr), fz = Math.cos(yr), rx = -fz, rz = fx;
        double along = 3.0 - (k / 4) * 1.0, across = -1.5 + (k % 4) * 1.0;      // passenger deck rail, forward of the funnel
        e.setPosition(posX + fx * along + rx * across, posY + 3.2 + e.getYOffset(), posZ + fz * along + rz * across);
    }

    @Override public boolean isInRangeToRenderDist(double d) { return d < 384 * 384; }
    @Override public boolean writeToNBTOptional(NBTTagCompound t) { return false; }
    @Override public boolean writeToNBTAtomically(NBTTagCompound t) { return false; }
    @Override protected void readEntityFromNBT(NBTTagCompound t) {}
    @Override protected void writeEntityToNBT(NBTTagCompound t) {}
}
