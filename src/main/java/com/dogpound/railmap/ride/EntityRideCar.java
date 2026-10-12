package com.dogpound.railmap.ride;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.List;

/**
 * A gondola cabin, aerial tram cabin or funicular car riding its line. It is only a view of the line's state (RideData):
 * the server places it from the line every tick, the client glides it along the same rope geometry (RideClient) from its
 * synced coordinate + speed, so it follows the sag and swings round the bullwheels exactly. Never saved - RideTicker
 * spawns the cars near players. Right-click to board (doors open), sneak to leave (doors open, or anywhere if
 * RideConfig.doorsLocked is off).
 */
public class EntityRideCar extends Entity {
    static final DataParameter<Integer> LINE = EntityDataManager.createKey(EntityRideCar.class, DataSerializers.VARINT);
    static final DataParameter<Integer> IDX = EntityDataManager.createKey(EntityRideCar.class, DataSerializers.VARINT);
    static final DataParameter<Byte> KIND = EntityDataManager.createKey(EntityRideCar.class, DataSerializers.BYTE);
    static final DataParameter<Float> COORD = EntityDataManager.createKey(EntityRideCar.class, DataSerializers.FLOAT);
    static final DataParameter<Float> SPEED = EntityDataManager.createKey(EntityRideCar.class, DataSerializers.FLOAT);
    static final DataParameter<Byte> FLAGS = EntityDataManager.createKey(EntityRideCar.class, DataSerializers.BYTE);   // 1 doors open, 2 gripped, 4 fault

    /** client: own coordinate, run forward from the synced one */
    public double coord = Double.NaN;
    public float swing, swingV, prevSwing;
    public double lastV;
    /** server tick this car last ran (a car whose chunk unloaded stops ticking and is replaced) */
    long seenAt;

    public EntityRideCar(World w) {
        super(w);
        setSize(1.8f, 2.4f);
        noClip = true;
        ignoreFrustumCheck = true;
    }

    public EntityRideCar(World w, int line, int idx, int kind) {
        this(w);
        dataManager.set(LINE, line);
        dataManager.set(IDX, idx);
        dataManager.set(KIND, (byte) kind);
        if (kind != RideGeom.GONDOLA) setSize(2.6f, 3.0f);
    }

    @Override
    protected void entityInit() {
        dataManager.register(LINE, 0);
        dataManager.register(IDX, 0);
        dataManager.register(KIND, (byte) 0);
        dataManager.register(COORD, 0f);
        dataManager.register(SPEED, 0f);
        dataManager.register(FLAGS, (byte) 0);
    }

    public int line() { return dataManager.get(LINE); }
    public int idx() { return dataManager.get(IDX); }
    public int kind() { return dataManager.get(KIND); }
    public boolean doorsOpen() { return (dataManager.get(FLAGS) & 1) != 0; }
    public boolean gripped() { return (dataManager.get(FLAGS) & 2) != 0; }
    public float speed() { return dataManager.get(SPEED); }
    public float synced() { return dataManager.get(COORD); }
    public boolean faulted() { return (dataManager.get(FLAGS) & 4) != 0; }

    /** seats a car holds */
    public int seats() { return kind() == RideGeom.GONDOLA ? 8 : kind() == RideGeom.TRAM ? 24 : 16; }

    @Override
    public void onUpdate() {
        prevPosX = posX; prevPosY = posY; prevPosZ = posZ;
        prevRotationYaw = rotationYaw; prevRotationPitch = rotationPitch;
        if (world.isRemote) { com.dogpound.railmap.ride.client.RideClient.carTick(this); return; }
        RideData.Line l = RideData.get(world).line(line());
        if (l == null || idx() >= l.cars() || l.geom() == null) { eject(); setDead(); return; }
        double[] p = l.carPose(idx());
        if (p == null) return;
        setPosition(p[0], p[1], p[2]);
        rotationYaw = (float) p[3];
        rotationPitch = l.kind == RideGeom.FUNICULAR ? (float) p[4] : 0;
        int i = idx();
        dataManager.set(COORD, (float) l.carCoord(i));
        dataManager.set(SPEED, (float) (l.shuttle() ? l.v : i < l.carV.length ? l.carV[i] : l.v));
        byte fl = (byte) ((l.doorsOpen(i) ? 1 : 0) | (!l.shuttle() && l.geom().gripped(l.carCoord(i)) ? 2 : 0) | (l.faults != 0 ? 4 : 0));
        dataManager.set(FLAGS, fl);
        if (i < l.carRiders.length) l.carRiders[i] = getPassengers().size();
        seenAt = world.getTotalWorldTime();
        RideTicker.seen(this);
    }

    boolean ejecting;

    void eject() { ejecting = true; for (Entity e : new java.util.ArrayList<>(getPassengers())) e.dismountRidingEntity(); }

    @Override
    public boolean processInitialInteract(EntityPlayer p, EnumHand hand) {
        if (world.isRemote) return true;
        if (isPassenger(p)) return true;
        if (!doorsOpen() && !p.capabilities.isCreativeMode) { p.sendStatusMessage(new TextComponentString("§cDoors closed - board at a station"), true); return true; }
        if (getPassengers().size() >= seats()) { p.sendStatusMessage(new TextComponentString("§cFull"), true); return true; }
        p.startRiding(this);
        return true;
    }

    @Override protected boolean canFitPassenger(Entity e) { return getPassengers().size() < seats(); }
    @Override public boolean canBeCollidedWith() { return !isDead; }
    @Override public boolean canBePushed() { return false; }
    @Override public AxisAlignedBB getCollisionBoundingBox() { return null; }
    @Override public boolean shouldRiderSit() { return kind() == RideGeom.GONDOLA; }

    @Override
    public void updatePassenger(Entity e) {
        if (!isPassenger(e)) return;
        List<Entity> ps = getPassengers();
        int k = ps.indexOf(e);
        double[] seat = seat(k);
        double yr = Math.toRadians(rotationYaw), pr = Math.toRadians(rotationPitch);
        double fx = -Math.sin(yr), fz = Math.cos(yr), rx = -fz, rz = fx;               // forward + right in the world
        double along = seat[1], up = seat[2] - Math.sin(pr) * along;
        e.setPosition(posX + fx * along * Math.cos(pr) + rx * seat[0], posY + up + e.getYOffset(), posZ + fz * along * Math.cos(pr) + rz * seat[0]);
    }

    /** seat k: {across, along, height} in metres from the floor centre */
    double[] seat(int k) {
        switch (kind()) {
            case RideGeom.GONDOLA: return new double[]{-0.6 + (k % 4) * 0.4, k % 8 < 4 ? -0.5 : 0.5, 0.35};                                         // two benches facing each other
            case RideGeom.TRAM: return new double[]{-0.9 + (k % 4) * 0.6, -2.0 + (k / 4) * 0.7, 0.1};                                   // standing
            default: return new double[]{k % 2 == 0 ? -0.5 : 0.5, -2.8 + (k / 2) * 0.75, 0.15 + (k / 2) * 0.12};                            // stepped compartments
        }
    }

    @Override public double getMountedYOffset() { return 0.35; }

    /** the client places the car itself from the line (RideClient.carTick): ignore the tracker's coarse moves */
    @Override
    public void setPositionAndRotationDirect(double x, double y, double z, float yaw, float pitch, int inc, boolean teleport) {
        if (world.isRemote && !Double.isNaN(coord)) return;
        super.setPositionAndRotationDirect(x, y, z, yaw, pitch, inc, teleport);
    }
    @Override public boolean isInRangeToRenderDist(double d) { return d < 256 * 256; }

    // never saved: the line owns the state
    @Override public boolean writeToNBTOptional(NBTTagCompound t) { return false; }
    @Override public boolean writeToNBTAtomically(NBTTagCompound t) { return false; }
    @Override protected void readEntityFromNBT(NBTTagCompound t) {}
    @Override protected void writeEntityToNBT(NBTTagCompound t) {}
}
