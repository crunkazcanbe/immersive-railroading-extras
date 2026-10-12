package com.dogpound.railmap.build6a;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.physics.MovementTrack;
import cam72cam.immersiverailroading.thirdparty.trackapi.IRPathingData;
import cam72cam.immersiverailroading.thirdparty.trackapi.ITrack;
import cam72cam.immersiverailroading.tile.TileRail;
import cam72cam.immersiverailroading.util.VecUtil;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.sound.PackSounds;
import com.dogpound.railmap.track.Rails;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.Locale;

/**
 * Road-rail hi-rail truck (batch 6a #5): a crew-cab flatbed you drive on the road (W/S throttle and brake, A/D steer)
 * and, stopped square over Immersive Railroading standard-gauge track, lower its hydraulic rail guide wheels - the
 * rubber tyres stay down for traction and the steel guide wheels hold it to the rails, so on rail it follows the IR
 * track itself (curves, switches as set, slopes) the same way IR's own stock finds its next position (MovementTrack /
 * ITrack.getNextPosition). Rail speed is capped (40 km/h), and it brakes itself for rolling stock ahead. Raise the
 * guide wheels (stopped) to drive off at a level crossing. Cab controls: right-click while driving (or sneak + right-click it) opens the cab panel
 * (ignition, guide wheels, park brake, beacons, work lights, horn, speedometer, rail status). It is not a train: signals
 * and axle counters do not see it - a real hi-rail runs under a possession.
 */
public class EntityHiRail extends Entity {
    private static final DataParameter<Integer> GEAR = EntityDataManager.createKey(EntityHiRail.class, DataSerializers.VARINT);
    private static final DataParameter<Byte> FLAGS = EntityDataManager.createKey(EntityHiRail.class, DataSerializers.BYTE);
    private static final DataParameter<Float> SPEED = EntityDataManager.createKey(EntityHiRail.class, DataSerializers.FLOAT);
    public static final int F_IGN = 1, F_PARK = 2, F_BEACON = 4, F_WORK = 8, F_WANT_RAIL = 16, F_OBST = 32, F_HEAD = 64;
    public static final double GAUGE = 1.435;

    /** blocks per tick, + = forward */
    public double speed;
    private int lerpSteps;
    private double lx, ly, lz, lyaw;
    public String status = "";
    /** the driving side: on rail last tick / stock ahead now */
    public boolean wasRail, obst;
    private int hornCool;

    public EntityHiRail(World w) {
        super(w);
        setSize(2.4f, 2.6f);
        stepHeight = 1.0f;
        preventEntitySpawning = true;
    }

    public EntityHiRail(World w, double x, double y, double z, float yaw) {
        this(w);
        setPosition(x, y, z);
        rotationYaw = prevRotationYaw = yaw;
        set(F_PARK, true);
    }

    @Override
    protected void entityInit() {
        dataManager.register(GEAR, 0);
        dataManager.register(FLAGS, (byte) F_PARK);
        dataManager.register(SPEED, 0f);
    }

    public int gear() { return dataManager.get(GEAR); }
    public boolean has(int f) { return (dataManager.get(FLAGS) & f) != 0; }
    void set(int f, boolean on) { int v = dataManager.get(FLAGS); dataManager.set(FLAGS, (byte) (on ? v | f : v & ~f)); }
    public boolean onRail() { return gear() >= 100; }
    public float kmh() { return dataManager.get(SPEED); }

    // ---- driving ----------------------------------------------------------------------------------------------------
    @Override public boolean canBeCollidedWith() { return !isDead; }
    @Override public boolean canBePushed() { return false; }
    @Override public AxisAlignedBB getCollisionBoundingBox() { return null; }
    @Override public double getMountedYOffset() { return 1.05; }
    @Override public Entity getControllingPassenger() { return getPassengers().isEmpty() ? null : getPassengers().get(0); }
    @Override protected boolean canFitPassenger(Entity p) { return getPassengers().size() < 2; }

    @Override
    public void updatePassenger(Entity p) {
        if (!isPassenger(p)) return;
        int i = getPassengers().indexOf(p);
        double side = i == 0 ? 0.45 : -0.45, fwd = 1.6;
        double s = Math.sin(Math.toRadians(rotationYaw)), c = Math.cos(Math.toRadians(rotationYaw));
        p.setPosition(posX - s * fwd + c * side * -1, posY + getMountedYOffset() + p.getYOffset(), posZ + c * fwd + s * side * -1);
        if (p instanceof EntityLivingBase l) { l.renderYawOffset = rotationYaw; }
    }

    @Override
    public void applyOrientationToEntity(Entity p) {
        p.setRenderYawOffset(rotationYaw);
        float d = MathHelper.wrapDegrees(p.rotationYaw - rotationYaw), c = MathHelper.clamp(d, -120f, 120f);
        p.prevRotationYaw += c - d;
        p.rotationYaw += c - d;
        p.setRotationYawHead(p.rotationYaw);
    }

    @Override
    public boolean processInitialInteract(EntityPlayer player, EnumHand hand) {
        if (player.isSneaking()) {
            if (world.isRemote) B6Net.openCab.accept(this);   // the cab panel (client)
            return true;
        }
        if (!world.isRemote && !isPassenger(player)) player.startRiding(this);
        return true;
    }

    @Override
    public boolean attackEntityFrom(DamageSource src, float amount) {
        if (world.isRemote || isDead) return true;
        if (src.getTrueSource() instanceof EntityPlayer p && p.isSneaking() && getPassengers().isEmpty()) {
            if (!p.capabilities.isCreativeMode) world.spawnEntity(new EntityItem(world, posX, posY + 0.5, posZ, new ItemStack(B6Blocks.hiRail)));
            setDead();
        }
        return true;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        prevPosX = posX; prevPosY = posY; prevPosZ = posZ;
        if (hornCool > 0) hornCool--;
        if (!world.isRemote) gearWork();
        if (canPassengerSteer()) {
            drive();
            lerpSteps = 0;
        } else if (lerpSteps > 0) {
            double nx = posX + (lx - posX) / lerpSteps, ny = posY + (ly - posY) / lerpSteps, nz = posZ + (lz - posZ) / lerpSteps;
            rotationYaw = (float) (rotationYaw + MathHelper.wrapDegrees(lyaw - rotationYaw) / lerpSteps);
            lerpSteps--;
            setPosition(nx, ny, nz);
        }
        if (!world.isRemote && ticksExisted % 5 == 0) dataManager.set(SPEED, (float) Math.abs(speedFromMotion() * 72 / 5));
        if (!world.isRemote) set(F_HEAD, world.getWorldTime() % 24000 > 12500 || world.isRaining());
    }

    private double lastX = Double.NaN, lastZ;
    private double speedFromMotion() {
        double v = Double.isNaN(lastX) ? 0 : Math.sqrt((posX - lastX) * (posX - lastX) + (posZ - lastZ) * (posZ - lastZ));
        lastX = posX; lastZ = posZ;
        return v;
    }

    @Override
    public void setPositionAndRotationDirect(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {
        lx = x; ly = y; lz = z; lyaw = yaw; lerpSteps = 5;
    }

    /** the controlling side (the driver's client, or the server with nobody aboard) moves it */
    void drive() {
        Entity d = getControllingPassenger();
        float fwd = d instanceof EntityLivingBase l ? l.moveForward : 0, strafe = d instanceof EntityLivingBase l2 ? l2.moveStrafing : 0;
        boolean ign = has(F_IGN), park = has(F_PARK);
        boolean rail = onRail();
        if (rail && !wasRail) snap();
        wasRail = rail;
        double max = (rail ? B6Config.hiRailRailKmh : B6Config.hiRailRoadKmh) / 72.0;
        int g = gear();
        if (g > 0 && g < 100) { fwd = 0; speed *= 0.5; }                         // guide wheels moving: hold still
        if (!ign || park) fwd = 0;
        if (park) speed *= 0.6;
        if (fwd > 0) speed += speed < 0 ? 0.03 : 0.006;
        else if (fwd < 0) speed -= speed > 0 ? 0.03 : 0.004;
        else speed *= rail ? 0.995 : 0.97;
        obst = rail && obstructed();
        if (obst) { speed = Math.signum(speed) * Math.max(0, Math.abs(speed) - 0.04); }
        speed = MathHelper.clamp(speed, -max / 3, max);
        if (Math.abs(speed) < 0.0005 && fwd == 0) speed = 0;
        if (rail) railMove();
        else roadMove(strafe);
    }

    boolean obstructed() {
        try {
            double s = Math.sin(Math.toRadians(rotationYaw)), c = Math.cos(Math.toRadians(rotationYaw));
            double dir = speed >= 0 ? 1 : -1;
            for (EntityRollingStock r : cam72cam.mod.world.World.get(world).getEntities(EntityRollingStock.class)) {
                cam72cam.mod.math.Vec3d v = r.getPosition();
                double dx = v.x - posX, dz = v.z - posZ;
                double ahead = (-s * dx + c * dz) * dir, lat = Math.abs(c * dx + s * dz);
                double half = 8;
                try { half = r.getDefinition().getLength(r.gauge) / 2; } catch (RuntimeException ignored) { }
                if (ahead > 0 && ahead < half + 8 && lat < 2.5) { if (!world.isRemote) set(F_OBST, true); return true; }
            }
        } catch (LinkageError | RuntimeException ignored) { }
        if (!world.isRemote) set(F_OBST, false);
        return false;
    }

    void railMove() {
        if (speed == 0) return;
        try {
            cam72cam.mod.world.World cw = cam72cam.mod.world.World.get(world);
            cam72cam.mod.math.Vec3d cur = new cam72cam.mod.math.Vec3d(posX, posY, posZ);
            ITrack t = MovementTrack.findTrack(cw, cur, rotationYaw, GAUGE);
            if (t == null) { derail("ran off the end of the track"); return; }
            IRPathingData p = new IRPathingData(cur, 0);
            t.getNextPosition(p, VecUtil.fromWrongYaw(speed, rotationYaw), GAUGE);
            cam72cam.mod.math.Vec3d nxt = p.getUMCPos();
            double dx = nxt.x - cur.x, dz = nxt.z - cur.z;
            if (dx * dx + dz * dz > 1e-8) {
                float y = VecUtil.toWrongYaw(speed > 0 ? nxt.subtract(cur) : cur.subtract(nxt));
                rotationYaw = y;
            }
            setPosition(nxt.x, nxt.y, nxt.z);
        } catch (LinkageError | RuntimeException e) { derail("lost the rails (" + e.getClass().getSimpleName() + ")"); }
    }

    void derail(String why) {
        speed = 0;
        status = "OFF THE RAILS: " + why;
        if (!world.isRemote) { dataManager.set(GEAR, 0); set(F_WANT_RAIL, false); Recorder.event(world, "Hi-rail truck at " + (int) posX + "," + (int) posZ + ": " + why + " - guide wheels up"); }
    }

    void roadMove(float strafe) {
        if (onGround || Math.abs(speed) > 0) rotationYaw -= strafe * 3.2f * (float) Math.min(1, Math.abs(speed) / 0.12) * (speed >= 0 ? 1 : -1);
        motionX = -Math.sin(Math.toRadians(rotationYaw)) * speed;
        motionZ = Math.cos(Math.toRadians(rotationYaw)) * speed;
        motionY = onGround ? -0.04 : Math.max(-1.5, motionY - 0.08);
        move(MoverType.SELF, motionX, motionY, motionZ);
        if (collidedHorizontally) speed *= 0.3;
    }

    // ---- guide wheels (server) ----------------------------------------------------------------------------------------
    void gearWork() {
        int g = gear();
        boolean want = has(F_WANT_RAIL);
        if (want && g < 100) dataManager.set(GEAR, Math.min(100, g + 4));
        if (!want && g > 0) dataManager.set(GEAR, Math.max(0, g - 4));
        if (want && g + 4 >= 100 && g < 100) { snap(); status = "ON RAIL"; PackSounds.play(world, posX, posY, posZ, "ctl.lever", 0.8f, 0.6f); }
    }

    /** null = it may lower its guide wheels here, else why not; on success it is squared up onto the rail */
    String canRailOn() {
        if (Math.abs(speed) > 0.01 || kmh() > 0.5) return "Stop first";
        TileRail r = Rails.pieceAt(world, posX, posY, posZ);
        if (r == null) return "No track under the truck - drive onto a level crossing or a rail-access ramp";
        if (Math.abs(Rails.gaugeMm(r) - 1435) > 20) return "The guide wheels are set for 1435 mm - this track is " + Rails.gaugeMm(r) + " mm";
        try {
            cam72cam.mod.world.World cw = cam72cam.mod.world.World.get(world);
            cam72cam.mod.math.Vec3d cur = new cam72cam.mod.math.Vec3d(posX, posY, posZ);
            ITrack t = MovementTrack.findTrack(cw, cur, rotationYaw, GAUGE);
            if (t == null) return "Not square over the rails";
            IRPathingData p = new IRPathingData(cur, 0);
            t.getNextPosition(p, VecUtil.fromWrongYaw(0.5, rotationYaw), GAUGE);
            cam72cam.mod.math.Vec3d nxt = p.getUMCPos();
            float ty = VecUtil.toWrongYaw(nxt.subtract(cur));
            if (Math.abs(MathHelper.wrapDegrees(ty - rotationYaw)) > 25) return "Not lined up with the track (" + Math.round(Math.abs(MathHelper.wrapDegrees(ty - rotationYaw))) + "° off) - square it up";
            if (nxt.distanceTo(cur) > 2.5) return "Too far off the rail centre";
        } catch (LinkageError | RuntimeException e) { return "Can't find the rails here"; }
        return null;
    }

    /** guide wheels down: centre on the rail */
    void snap() {
        try {
            cam72cam.mod.world.World cw = cam72cam.mod.world.World.get(world);
            cam72cam.mod.math.Vec3d cur = new cam72cam.mod.math.Vec3d(posX, posY, posZ);
            ITrack t = MovementTrack.findTrack(cw, cur, rotationYaw, GAUGE);
            if (t == null) return;
            IRPathingData p = new IRPathingData(cur, 0);
            t.getNextPosition(p, VecUtil.fromWrongYaw(0.05, rotationYaw), GAUGE);
            cam72cam.mod.math.Vec3d n = p.getUMCPos();
            rotationYaw = VecUtil.toWrongYaw(n.subtract(cur));
            setPosition(n.x, n.y, n.z);
        } catch (LinkageError | RuntimeException ignored) { }
    }

    /** a cab panel button (server, from B6Net) */
    public String op(String what, EntityPlayer who) {
        switch (what) {
            case "ign" -> { set(F_IGN, !has(F_IGN)); if (has(F_IGN)) PackSounds.play(world, posX, posY, posZ, "engine_start", 0.8f, 1f); else PackSounds.play(world, posX, posY, posZ, "engine_stop", 0.8f, 1f); return has(F_IGN) ? "§aEngine running" : "§7Engine off"; }
            case "park" -> { set(F_PARK, !has(F_PARK)); return has(F_PARK) ? "§eParking brake ON" : "§aParking brake off"; }
            case "beacon" -> { set(F_BEACON, !has(F_BEACON)); return has(F_BEACON) ? "§6Beacons ON" : "§7Beacons off"; }
            case "work" -> { set(F_WORK, !has(F_WORK)); return has(F_WORK) ? "§eWork lights ON" : "§7Work lights off"; }
            case "horn" -> { if (hornCool == 0) { hornCool = 15; PackSounds.play(world, posX, posY, posZ, "cab.horn", 1.2f, 1.25f); } return null; }
            case "gear" -> {
                if (has(F_WANT_RAIL)) {
                    if (Math.abs(speed) > 0.01 || kmh() > 0.5) return "§cStop before raising the guide wheels";
                    set(F_WANT_RAIL, false); status = "ROAD";
                    Recorder.event(world, "Hi-rail truck at " + (int) posX + "," + (int) posZ + ": off track (guide wheels up)" + (who == null ? "" : " - " + who.getName()));
                    return "§eGuide wheels raising - road mode";
                }
                String why = canRailOn();
                if (why != null) return "§c" + why;
                set(F_WANT_RAIL, true); status = "LOWERING";
                Recorder.event(world, "Hi-rail truck at " + (int) posX + "," + (int) posZ + ": on track (guide wheels down)" + (who == null ? "" : " - " + who.getName()));
                return "§aGuide wheels lowering onto the rails";
            }
            default -> { return null; }
        }
    }

    public String statusLine() {
        if (gear() > 0 && gear() < 100) return has(F_WANT_RAIL) ? "GUIDE WHEELS LOWERING" : "GUIDE WHEELS RAISING";
        if (onRail()) return has(F_OBST) || obst ? "RAIL - STOCK AHEAD, BRAKING" : "RAIL MODE - ON TRACK";
        return "ROAD MODE";
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound t) {
        dataManager.set(GEAR, t.getInteger("gear"));
        dataManager.set(FLAGS, t.getByte("flags"));
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound t) {
        t.setInteger("gear", gear());
        t.setByte("flags", dataManager.get(FLAGS));
    }

    @Override public boolean shouldRenderInPass(int pass) { return pass == 0; }
    @Override public boolean isInRangeToRenderDist(double d) { return d < 256 * 256; }

    public String hud() { return String.format(Locale.ROOT, "%d km/h · %s", Math.round(kmh()), statusLine()); }

    public void sayTo(EntityPlayer p, String m) { if (m != null && !m.isEmpty()) p.sendStatusMessage(new TextComponentString(m), true); }
}
