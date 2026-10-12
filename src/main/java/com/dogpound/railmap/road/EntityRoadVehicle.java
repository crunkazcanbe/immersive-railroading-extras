package com.dogpound.railmap.road;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.ems.RoadPath;
import com.dogpound.railmap.signal.SignalRegistry;
import com.dogpound.railmap.signal.TileCrossing;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;

import java.util.List;
import java.util.Locale;

/**
 * A car, van, box truck or bus in the ambient road traffic (batch 5 #3). It drives the roads with the emergency
 * vehicles' A* road follower (ems.RoadPath: roads cheap, open ground dear, kerbs, no corner cutting), keeps to its side
 * of the road, and every few ticks looks ahead:
 * <ul>
 * <li>a closed level crossing (lights flashing / barriers down) on its way: it stops short of it and queues - the
 *     crossing panel and the console count the queue, and a long closure raises a ROAD QUEUE alarm;</li>
 * <li>a traffic signal facing it at red / red + amber / amber (if it can stop): it stops at the stop line, and calls its
 *     phase group; arriving on green it holds the green on (vehicle actuation); flashing amber = slow, give way;</li>
 * <li>a vehicle in front going the same way: it keeps its distance and queues behind it (and joins its queue);</li>
 * <li>a person or animal in front: it waits.</li>
 * </ul>
 * Brake lights when slowing or standing, indicators before a turn, headlights at night and in rain. Never saved: the
 * traffic manager (RoadTraffic) spawns it near players and removes it far from them.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public class EntityRoadVehicle extends Entity {
    public enum Kind {
        CAR("Car", 2f, 4.4, 1.0), VAN("Van", 2f, 5.4, 0.95), TRUCK("Truck", 3f, 8.0, 0.8), BUS("Bus", 4f, 11.5, 0.8);
        public final String label;
        public final float scale;
        public final double length, speedK;
        Kind(String label, float scale, double length, double speedK) { this.label = label; this.scale = scale; this.length = length; this.speedK = speedK; }
    }

    /** body colours (rgb), picked at random; buses keep the Pride livery colours */
    public static final int[] COLOURS = {0xF2F2F0, 0x1C1C1E, 0xA8ADB2, 0xB81E22, 0x1F4E9A, 0x1E5A34, 0xE8C21A, 0xE06A1A, 0x6A1A2A, 0x6AA8D8, 0x4A4E52, 0xC86AA8};
    public static final int[] BUS_COLOURS = {0xC8202A, 0x1E7A3A, 0x2050A8, 0xE04A8A};

    public static final int L_BRAKE = 1, L_LEFT = 2, L_RIGHT = 4, L_HEAD = 8;
    static final double ACCEL = 0.008, BRAKE = 0.014;

    private static final DataParameter<Byte> KIND = EntityDataManager.createKey(EntityRoadVehicle.class, DataSerializers.BYTE);
    private static final DataParameter<Byte> COLOUR = EntityDataManager.createKey(EntityRoadVehicle.class, DataSerializers.BYTE);
    private static final DataParameter<Byte> LIGHTS = EntityDataManager.createKey(EntityRoadVehicle.class, DataSerializers.BYTE);

    private List<BlockPos> path;
    private int pathIdx, stuck, waitPeople, noPath, idle;
    private BlockPos dest;
    double speed, limit = 1;
    /** the crossing (pos) whose closure it is queued for, Long.MIN_VALUE = none */
    long queuedFor = Long.MIN_VALUE;
    String why = "";
    // client smoothing
    private double lx, ly, lz;
    private float lyaw;
    private int lerpSteps;

    public EntityRoadVehicle(World w) {
        super(w);
        setSize(1.0f, 1.5f);
        stepHeight = 1.05f;
        ignoreFrustumCheck = true;
    }

    protected EntityRoadVehicle(World w, Kind k, int colour, BlockPos dest) {
        this(w);
        dataManager.set(KIND, (byte) k.ordinal());
        dataManager.set(COLOUR, (byte) colour);
        this.dest = dest;
    }

    @SubscribeEvent
    public static void register(RegistryEvent.Register<EntityEntry> e) {
        e.getRegistry().register(EntityEntryBuilder.create().entity(EntityRoadVehicle.class).id(new ResourceLocation(RailMap.MODID, "road_vehicle"), 7747)
                .name(RailMap.MODID + ".road_vehicle").tracker(128, 2, true).build());
    }

    @Override
    protected void entityInit() {
        dataManager.register(KIND, (byte) 0);
        dataManager.register(COLOUR, (byte) 0);
        dataManager.register(LIGHTS, (byte) 0);
    }

    public Kind kind() { return Kind.values()[Math.floorMod(dataManager.get(KIND), Kind.values().length)]; }
    public int colour() {
        int c = dataManager.get(COLOUR);
        return kind() == Kind.BUS ? BUS_COLOURS[Math.floorMod(c, BUS_COLOURS.length)] : COLOURS[Math.floorMod(c, COLOURS.length)];
    }
    public int lights() { return dataManager.get(LIGHTS); }
    public boolean queued() { return queuedFor != Long.MIN_VALUE; }
    double cruise() { return RoadConfig.speed * kind().speedK; }
    Vec3d fwd() { return new Vec3d(-MathHelper.sin(rotationYaw * 0.017453292f), 0, MathHelper.cos(rotationYaw * 0.017453292f)); }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (world.isRemote) { clientTick(); return; }
        if (ambient() && (!RoadConfig.traffic || RoadData.get(world).paused)) { setDead(); return; }
        if (ticksExisted % 4 == (getEntityId() & 3)) look();
        drive();
    }

    private void clientTick() {
        if (lerpSteps > 0) {
            double x = posX + (lx - posX) / lerpSteps, y = posY + (ly - posY) / lerpSteps, z = posZ + (lz - posZ) / lerpSteps;
            rotationYaw += MathHelper.wrapDegrees(lyaw - rotationYaw) / lerpSteps;
            lerpSteps--;
            setPosition(x, y, z);
        }
    }

    @Override
    public void setPositionAndRotationDirect(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {
        lx = x; ly = y; lz = z; lyaw = yaw;
        lerpSteps = teleport ? 1 : steps + 2;
    }

    private void physics(double mx, double mz) {
        motionY = onGround ? -0.04 : Math.max(-1.5, (motionY - 0.08) * 0.98);
        move(MoverType.SELF, mx, motionY, mz);
        if (onGround && motionY < 0) motionY = 0;
        if (posY < -32) setDead();
    }

    // ---- driving ----------------------------------------------------------------------------------------------------
    private void drive() {
        if (hold()) { speed = 0; physics(0, 0); return; }
        if (dest == null || getDistanceSq(dest) < 9) {
            dest = nextDest();
            path = null;
            if (dest == null) { physics(0, 0); if (++idle > 200) setDead(); return; }
        }
        if (path == null || pathIdx >= path.size()) {
            if (!RoadTraffic.takePath(world)) { speed = 0; physics(0, 0); return; }
            path = RoadPath.find(world, new BlockPos(posX, posY + 0.2, posZ), dest, (int) Math.min(160, Math.sqrt(getDistanceSq(dest)) + 32));
            pathIdx = path != null && path.size() > 1 ? 1 : 0;
            if (path == null || path.size() < 2) { path = null; dest = null; if (++noPath > 4) setDead(); speed = 0; physics(0, 0); return; }
            noPath = 0;
        }
        BlockPos wp = path.get(pathIdx);
        double tx = wp.getX() + 0.5 - posX, tz = wp.getZ() + 0.5 - posZ, flat = Math.sqrt(tx * tx + tz * tz);
        if (flat < 0.6) { pathIdx++; physics(0, 0); return; }
        // keep to our side of the road where there is room
        double side = RoadConfig.keepRight ? 0.55 : -0.55;
        double ox = -tz / flat * side, oz = tx / flat * side;
        BlockPos offCell = new BlockPos(wp.getX() + 0.5 + ox, wp.getY(), wp.getZ() + 0.5 + oz);
        if (RoadPath.standable(world, offCell)) { tx += ox; tz += oz; flat = Math.max(0.01, Math.sqrt(tx * tx + tz * tz)); }
        float want = (float) Math.toDegrees(Math.atan2(-tx, tz));
        float diff = MathHelper.wrapDegrees(want - rotationYaw);
        double turn = Math.abs(diff) > 60 ? 0.3 : Math.abs(diff) > 25 ? 0.6 : 1.0;
        double target = Math.min(cruise() * turn, limit);
        speed += MathHelper.clamp(target - speed, -BRAKE * 2, ACCEL);
        if (speed < 0.004) speed = 0;
        rotationYaw += MathHelper.clamp(diff, -10f, 10f) * (float) Math.min(1, 0.3 + speed * 3);
        Vec3d before = getPositionVector();
        double step = Math.min(flat, speed);
        physics(tx / flat * step, tz / flat * step);
        if (speed > 0.05 && getPositionVector().squareDistanceTo(before) < 0.0004) {
            if (++stuck % 60 == 0) { path = null; }
            if (stuck > 600 && !RoadTraffic.seen(world, this)) setDead();
        } else stuck = Math.max(0, stuck - 2);
    }

    /** look ahead (every 4 ticks): crossings, signals, the vehicle in front, people; set the speed limit and lamps */
    private void look() {
        Vec3d f = fwd();
        double lim = cruise();
        String w = "";
        long q = Long.MIN_VALUE;
        // ---- the vehicle in front going our way
        double len = kind().length;
        double reach = len / 2 + 14;
        for (EntityRoadVehicle o : world.getEntitiesWithinAABB(EntityRoadVehicle.class, getEntityBoundingBox().grow(reach, 2, reach), e -> e != this)) {
            double rx = o.posX - posX, rz = o.posZ - posZ;
            double along = rx * f.x + rz * f.z, lat = Math.abs(rx * f.z - rz * f.x);
            if (along <= 0 || lat > 1.4 || o.fwd().dotProduct(f) < 0.3) continue;
            double gap = along - (len + o.kind().length) / 2;
            double v = stopV(gap - 1.2);
            if (v < lim) {
                lim = v; w = "queueing behind a " + o.kind().label.toLowerCase(Locale.ROOT);
                if (o.queued() && gap < 8) q = o.queuedFor;
            }
        }
        // ---- people / animals right in front
        AxisAlignedBB ahead = getEntityBoundingBox().offset(f.x * (len / 2 + 0.8), 0, f.z * (len / 2 + 0.8)).grow(0.5, 0, 0.5);
        boolean person = false;
        for (Entity e : world.getEntitiesWithinAABBExcludingEntity(this, ahead))
            if (e instanceof EntityLivingBase && !e.isRiding() && !(e instanceof net.minecraft.entity.monster.IMob)) person = true;
        if (person) { if (++waitPeople < 20 * 30 / 4) { lim = 0; w = "waiting for someone in the road"; } }
        else waitPeople = 0;
        // ---- closed level crossings on the way
        double[] cx = crossingAhead(len);
        if (cx != null) {
            double v = stopV(cx[0] - 1.5);
            if (v < lim) { lim = v; w = "level crossing closed"; }
            if (cx[0] < 8) q = (long) cx[1];
        }
        // ---- traffic signals facing us
        for (TileTrafficSignal h : TileTrafficSignal.REG.all(world)) {
            BlockPos hp = h.getPos();
            if (hp.distanceSq(posX, posY, posZ) > 30 * 30 || Math.abs(hp.getY() - posY) > 4) continue;
            EnumFacing hf = h.facing();
            double d = RoadTraffic.stopLine(posX + f.x * len / 2, posZ + f.z * len / 2, f.x, f.z, hp.getX() + 0.5, hp.getZ() + 0.5, hf.getXOffset(), hf.getZOffset(), 6.5);
            if (d < 0) continue;
            int a = h.aspect();
            TileTrafficController c = h.controller();
            if (a == TileTrafficController.Phasing.GREEN) { if (c != null && d < 25) c.extend(h.group()); continue; }
            if (a == TileTrafficController.Phasing.FLASH_AMBER) { if (d < 10 && lim > cruise() * 0.5) { lim = cruise() * 0.5; w = "flashing amber - give way"; } continue; }
            if (!h.stopAspect()) continue;
            if (a == TileTrafficController.Phasing.AMBER && d < 2.5 && speed > 0.2) continue;   // too close to stop: clear the junction
            double v = stopV(d - 0.6);
            if (v < lim) { lim = v; w = "signal at " + (a == TileTrafficController.Phasing.AMBER ? "amber" : "red"); }
            if (c != null && d < 14) c.vehCall(h.group());
        }
        limit = lim;
        why = w;
        if (q != queuedFor) {
            if (q != Long.MIN_VALUE && queuedFor == Long.MIN_VALUE) RoadTraffic.delayed(world, q);
            queuedFor = q;
        }
        // ---- lamps
        int l = 0;
        if (lim < speed - 0.01 || speed < 0.02) l |= L_BRAKE;
        int turn = turnAhead();
        if (turn < 0) l |= L_LEFT;
        if (turn > 0) l |= L_RIGHT;
        if (!world.isDaytime() || world.isRaining()) l |= L_HEAD;
        if ((byte) l != dataManager.get(LIGHTS)) dataManager.set(LIGHTS, (byte) l);
    }

    /** {distance to the first path cell inside a closed crossing's zone, crossing pos} or null */
    private double[] crossingAhead(double len) {
        if (path == null) return null;
        List<TileCrossing> xs = SignalRegistry.crossings(world.provider.getDimension());
        if (xs.isEmpty()) return null;
        double dist = len / 2 * -1;
        double px = posX, pz = posZ;
        BlockPos me = getPosition();
        boolean onRail = com.dogpound.railmap.scan.SwitchControl.isRail(world, me) || com.dogpound.railmap.scan.SwitchControl.isRail(world, me.down());   // on the tracks: drive off them
        for (int i = pathIdx; i < Math.min(path.size(), pathIdx + 14); i++) {
            BlockPos c = path.get(i);
            dist += Math.sqrt((c.getX() + 0.5 - px) * (c.getX() + 0.5 - px) + (c.getZ() + 0.5 - pz) * (c.getZ() + 0.5 - pz));
            px = c.getX() + 0.5; pz = c.getZ() + 0.5;
            boolean rail = !onRail && (com.dogpound.railmap.scan.SwitchControl.isRail(world, c) || com.dogpound.railmap.scan.SwitchControl.isRail(world, c.down()));
            for (TileCrossing x : xs) {
                if (!x.active() || x.isInvalid()) continue;
                BlockPos xp = x.getPos();
                if (Math.abs(xp.getY() - c.getY()) > 4) continue;
                double dx = xp.getX() - c.getX(), dz = xp.getZ() - c.getZ(), d2 = dx * dx + dz * dz;
                double zone = rail ? 20 : 4.5;
                if (d2 > zone * zone) continue;
                double mx = xp.getX() + 0.5 - posX, mz = xp.getZ() + 0.5 - posZ;
                if (!rail && mx * mx + mz * mz < 4.5 * 4.5) continue;                     // already in it: drive out
                return new double[]{Math.max(0, dist), xp.toLong()};
            }
        }
        return null;
    }

    /** -1 a left turn coming up, +1 right, 0 straight on */
    private int turnAhead() {
        if (path == null || pathIdx + 4 >= path.size()) return 0;
        BlockPos a = path.get(pathIdx), b = path.get(Math.min(path.size() - 1, pathIdx + 3)), c = path.get(Math.min(path.size() - 1, pathIdx + 7));
        return RoadTraffic.turnSide(b.getX() - a.getX(), b.getZ() - a.getZ(), c.getX() - b.getX(), c.getZ() - b.getZ());
    }

    // ---- hooks for scheduled vehicles (stnlife6: timetabled buses, taxis) ----------------------------------------------
    /** where to drive next once the last destination is reached (ambient traffic: anywhere on the road) */
    protected BlockPos nextDest() { return RoadTraffic.destination(world, getPosition()); }
    /** stand still now (a bus dwelling at a bay, a taxi waiting at the rank) */
    protected boolean hold() { return false; }
    /** ambient traffic dies with the traffic switch / pause; scheduled vehicles don't */
    protected boolean ambient() { return true; }
    public double speed() { return speed; }
    /** forget the current destination + path: nextDest() is asked again */
    protected void reroute() { dest = null; path = null; }

    static double stopV(double d) { return d <= 0 ? 0 : Math.sqrt(2 * BRAKE * d); }

    // ---- interaction / persistence ----------------------------------------------------------------------------------
    @Override
    public boolean processInitialInteract(EntityPlayer p, EnumHand hand) {
        if (world.isRemote) return true;
        String to = dest == null ? "parking" : dest.getX() + "," + dest.getZ();
        p.sendStatusMessage(new TextComponentString(String.format(Locale.ROOT, "§b%s §7- to %s - %.0f km/h%s", kind().label, to, speed * 72, why.isEmpty() ? "" : " - " + why)), true);
        return true;
    }

    @Override public boolean attackEntityFrom(DamageSource s, float amount) {
        if (!world.isRemote && s.getTrueSource() instanceof EntityPlayer pl && pl.capabilities.isCreativeMode && pl.isSneaking()) { setDead(); return true; }
        return false;
    }
    @Override public boolean canBeCollidedWith() { return !isDead; }
    @Override public boolean canBePushed() { return false; }
    @Override public boolean isInRangeToRenderDist(double d2) { return d2 < 192 * 192; }
    /** ambient traffic is never saved: it can't pile up in unloaded chunks */
    @Override public boolean writeToNBTOptional(NBTTagCompound t) { return false; }
    @Override public boolean writeToNBTAtomically(NBTTagCompound t) { return false; }
    @Override protected void readEntityFromNBT(NBTTagCompound t) { dataManager.set(KIND, t.getByte("kind")); dataManager.set(COLOUR, t.getByte("col")); }
    @Override protected void writeEntityToNBT(NBTTagCompound t) { t.setByte("kind", dataManager.get(KIND)); t.setByte("col", dataManager.get(COLOUR)); }
}
