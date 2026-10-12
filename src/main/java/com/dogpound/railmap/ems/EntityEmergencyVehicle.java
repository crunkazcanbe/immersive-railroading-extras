package com.dogpound.railmap.ems;

import com.dogpound.railmap.RailMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
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
 * A fire engine, ambulance or police car on a run (Ems): drives the roads to its target with an A* road follower
 * (RoadPath: roads cheap, open ground dear, one-block kerbs, no corner cutting), steering rather than spinning, slowing
 * for bends, stopping for people in front. Stuck for long (a wall, a river) it finds the next bit of ground along the
 * way. Lights flash and the siren sounds while it is responding or carrying a patient. It belongs to its run: a copy
 * that loads from an old chunk after the run moved on removes itself. It can't be hurt; right-click for its status
 * (an ambulance on scene takes you aboard if you are hurt or sneaking).
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public class EntityEmergencyVehicle extends Entity {
    private static final DataParameter<Byte> KIND = EntityDataManager.createKey(EntityEmergencyVehicle.class, DataSerializers.BYTE);
    private static final DataParameter<Byte> STAGE = EntityDataManager.createKey(EntityEmergencyVehicle.class, DataSerializers.BYTE);
    private static final DataParameter<String> NAME = EntityDataManager.createKey(EntityEmergencyVehicle.class, DataSerializers.STRING);

    private int runId = -1, pathIdx, repaths, stuckTicks, waitTicks, retargetIn, jumps;
    private List<BlockPos> path;
    private BlockPos goal;
    private double lastGoalDist = Double.MAX_VALUE;
    // client smoothing
    private double lx, ly, lz;
    private float lyaw;
    private int lerpSteps;
    public float wheelSpin, prevWheelSpin;

    public EntityEmergencyVehicle(World w) {
        super(w);
        setSize(1.2f, 1.7f);
        stepHeight = 1.05f;
        isImmuneToFire = true;
        ignoreFrustumCheck = true;
    }

    EntityEmergencyVehicle(World w, EmsData.Run r) {
        this(w);
        runId = r.id;
        dataManager.set(KIND, (byte) r.kind.ordinal());
        dataManager.set(STAGE, (byte) r.stage.ordinal());
        dataManager.set(NAME, r.vehicle);
    }

    @SubscribeEvent
    public static void register(RegistryEvent.Register<EntityEntry> e) {
        e.getRegistry().register(EntityEntryBuilder.create().entity(EntityEmergencyVehicle.class).id(new ResourceLocation(RailMap.MODID, "emergency_vehicle"), 7745)
                .name(RailMap.MODID + ".emergency_vehicle").tracker(160, 2, true).build());
    }

    @Override
    protected void entityInit() {
        dataManager.register(KIND, (byte) 0);
        dataManager.register(STAGE, (byte) 0);
        dataManager.register(NAME, "");
    }

    public Ems.Kind kind() { return Ems.Kind.values()[Math.floorMod(dataManager.get(KIND), Ems.Kind.values().length)]; }
    public EmsData.Stage stage() { return EmsData.Stage.values()[Math.floorMod(dataManager.get(STAGE), EmsData.Stage.values().length)]; }
    public String vehicleName() { return dataManager.get(NAME); }
    /** blue lights / sirens on: responding or carrying a patient; on scene the lights flash, siren off */
    public boolean lights() { EmsData.Stage s = stage(); return s == EmsData.Stage.RESPONDING || s == EmsData.Stage.TRANSPORT || s == EmsData.Stage.ON_SCENE; }

    private EmsData.Run run() {
        EmsData.Run r = runId < 0 ? null : EmsData.get(world).byId(runId);
        return r != null && r.active() && getUniqueID().equals(r.entity) ? r : null;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        prevWheelSpin = wheelSpin;
        if (world.isRemote) { clientTick(); return; }
        EmsData.Run r = run();
        if (r == null) { removePassengers(); setDead(); return; }
        if (dataManager.get(STAGE) != r.stage.ordinal()) dataManager.set(STAGE, (byte) r.stage.ordinal());
        switch (r.stage) {
            case RESPONDING, TRANSPORT, RETURNING -> drive(r);
            default -> physics(0, 0);
        }
        if (EmsConfig.sirens && !r.silent && (r.stage == EmsData.Stage.RESPONDING || r.stage == EmsData.Stage.TRANSPORT) && ticksExisted % 40 == 1)
            world.playSound(null, posX, posY + 1, posZ, siren(), SoundCategory.NEUTRAL, (float) EmsConfig.sirenVolume, 1.0f);
    }

    private net.minecraft.util.SoundEvent siren() {
        return switch (kind()) { case FIRE -> EmsSounds.SIREN_FIRE; case AMBULANCE -> EmsSounds.SIREN_AMBULANCE; default -> EmsSounds.SIREN_POLICE; };
    }

    private void clientTick() {
        if (lerpSteps > 0) {
            double x = posX + (lx - posX) / lerpSteps, y = posY + (ly - posY) / lerpSteps, z = posZ + (lz - posZ) / lerpSteps;
            rotationYaw += MathHelper.wrapDegrees(lyaw - rotationYaw) / lerpSteps;
            lerpSteps--;
            setPosition(x, y, z);
        }
        double dx = posX - prevPosX, dz = posZ - prevPosZ;
        wheelSpin += (float) Math.sqrt(dx * dx + dz * dz) * 90f;
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

    private void drive(EmsData.Run r) {
        if (retargetIn-- <= 0) {
            retargetIn = 20;
            BlockPos g = RoadPath.ground(world, new BlockPos(r.tx, r.ty + 1, r.tz), 6);
            BlockPos want = g != null ? g : new BlockPos(r.tx, r.ty, r.tz);
            if (goal == null || want.distanceSq(goal) > 4) { goal = want; path = null; repaths = 0; lastGoalDist = Double.MAX_VALUE; }
        }
        double dx = r.tx - posX, dz = r.tz - posZ, d = Math.sqrt(dx * dx + dz * dz);
        if (d < 3.5 && Math.abs(r.ty - posY) < 3.5) { physics(0, 0); Ems.arrived(world, r); path = null; goal = null; return; }
        if (repaths > 8 || stuckTicks > 300) { jump(r); return; }
        if (path == null || pathIdx >= path.size()) {
            if (path != null && d > lastGoalDist - 2) repaths++;
            lastGoalDist = d;
            int range = (int) Math.min(160, d + 32);
            path = RoadPath.find(world, new BlockPos(posX, posY + 0.2, posZ), goal, range);
            pathIdx = path != null && path.size() > 1 ? 1 : 0;
            if (path == null) { repaths++; physics(0, 0); return; }
        }
        if (ticksExisted % 4 == 0 && blocked()) { if (++waitTicks > 20 * 8) waitTicks = 0; else { physics(0, 0); return; } }
        BlockPos wp = path.get(pathIdx);
        double tx = wp.getX() + 0.5 - posX, tz = wp.getZ() + 0.5 - posZ, flat = Math.sqrt(tx * tx + tz * tz);
        if (flat < 0.45) { pathIdx++; physics(0, 0); return; }
        float want = (float) Math.toDegrees(Math.atan2(-tx, tz));
        float diff = MathHelper.wrapDegrees(want - rotationYaw);
        rotationYaw += MathHelper.clamp(diff, -12f, 12f);
        double speed = Math.min(flat, EmsConfig.speed) * (Math.abs(diff) > 60 ? 0.3 : Math.abs(diff) > 25 ? 0.65 : 1.0);
        Vec3d before = getPositionVector();
        physics(tx / flat * speed, tz / flat * speed);
        if (getPositionVector().squareDistanceTo(before) < 0.0004) {
            if (++stuckTicks % 40 == 0) { path = null; repaths++; }
        } else stuckTicks = Math.max(0, stuckTicks - 2);
    }

    /** somebody standing right in front (not a passenger): wait for them (but never forever) */
    private boolean blocked() {
        Vec3d fwd = new Vec3d(-MathHelper.sin(rotationYaw * 0.017453292f), 0, MathHelper.cos(rotationYaw * 0.017453292f));
        net.minecraft.util.math.AxisAlignedBB ahead = getEntityBoundingBox().offset(fwd.x * 1.6, 0, fwd.z * 1.6).grow(0.3, 0, 0.3);
        for (Entity e : world.getEntitiesWithinAABBExcludingEntity(this, ahead))
            if (e instanceof net.minecraft.entity.EntityLivingBase && !isPassenger(e) && !(e instanceof net.minecraft.entity.monster.IMob)) return true;
        return false;
    }

    /** hopelessly stuck: find the next bit of ground ~10 blocks along the straight line to the target */
    private void jump(EmsData.Run r) {
        repaths = 0; stuckTicks = 0; path = null; jumps++;
        double dx = r.tx - posX, dz = r.tz - posZ, d = Math.max(1, Math.sqrt(dx * dx + dz * dz)), step = Math.min(d, 10);
        for (int k = 0; k < 6; k++, step += 3) {
            BlockPos p = new BlockPos(posX + dx / d * Math.min(d, step), posY + 1, posZ + dz / d * Math.min(d, step));
            if (!world.isBlockLoaded(p)) break;
            BlockPos g = RoadPath.ground(world, p, 8);
            if (g == null) continue;
            setPositionAndUpdate(g.getX() + 0.5, g.getY(), g.getZ() + 0.5);
            for (Entity rider : getPassengers()) rider.setPosition(posX, posY + getMountedYOffset() + rider.getYOffset(), posZ);
            return;
        }
        if (jumps > 30) { r.vx = posX; r.vy = posY; r.vz = posZ; removePassengers(); r.entity = null; setDead(); }   // carry on off screen
    }

    @Override
    public boolean processInitialInteract(EntityPlayer p, EnumHand hand) {
        if (world.isRemote) return true;
        EmsData.Run r = run();
        if (r == null) return true;
        if (r.kind == Ems.Kind.AMBULANCE && (r.stage == EmsData.Stage.ON_SCENE || r.stage == EmsData.Stage.RESPONDING) && (p.isSneaking() || p.getHealth() < p.getMaxHealth()) && !isPassenger(p)) {
            if (Ems.board(world, r, p)) return true;
        }
        String to = r.incident >= 0 ? "incident #" + r.incident : String.format(Locale.ROOT, "%.0f, %.0f", r.tx, r.tz);
        p.sendStatusMessage(new TextComponentString("§b" + r.vehicle + " §7(" + r.stationName + ") - " + r.stage.name().toLowerCase(Locale.ROOT).replace('_', ' ') + " - " + to
                + (r.onScene > 0 ? " - response " + com.dogpound.railmap.incident.IncidentLog.mins(r.response()) : "")), true);
        return true;
    }

    @Override public boolean attackEntityFrom(DamageSource s, float amount) { return false; }
    @Override public boolean canBeCollidedWith() { return !isDead; }
    @Override public boolean canBePushed() { return false; }
    @Override public double getMountedYOffset() { return 0.7; }
    @Override public boolean shouldRiderSit() { return true; }
    @Override protected boolean canFitPassenger(Entity e) { return getPassengers().isEmpty(); }
    @Override public boolean isInRangeToRenderDist(double d2) { return d2 < 256 * 256; }

    @Override protected void readEntityFromNBT(NBTTagCompound t) { runId = t.hasKey("run") ? t.getInteger("run") : -1; dataManager.set(KIND, t.getByte("kind")); dataManager.set(NAME, t.getString("name")); }
    @Override protected void writeEntityToNBT(NBTTagCompound t) { t.setInteger("run", runId); t.setByte("kind", dataManager.get(KIND)); t.setString("name", dataManager.get(NAME)); }
}
