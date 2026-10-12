package com.dogpound.railmap.drone;

import com.dogpound.railmap.ModSounds;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.cctv.CctvData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.*;

/**
 * An inspection drone in flight (her IR Extras list #2): a quadcopter with a zoom + thermal camera gimbal, launched from
 * a Drone Dock. It flies the dock's mission along the track it found (TRACK / OHLE / STRUCTURES / TUNNEL portal to
 * portal) at height, inspects as it goes (DroneInspect), and comes home along the same route. It is a CCTV camera
 * (CamType.DRONE): its picture goes to monitors and the desk like a train camera; the desk joystick or a handheld
 * controller fly it by hand. A 500 Wh pack (scaled to game time) drains with speed, climb and wind; it returns home
 * with a reserve, won't fight heavy rain, snow, thunder or too much wind, avoids what is ahead of it and climbs clear
 * of players in station no-fly zones. Run flat it lands where it is (or, with crashes on, falls and breaks).
 */
public class EntityDrone extends Entity {
    public static final int MISSION = 0, RTH = 1, LAND = 2, MANUAL = 3, HOLD = 4, LANDED_OUT = 5, TAKEOFF = 6;
    public static final String[] MODES = {"MISSION", "RETURN HOME", "LANDING", "MANUAL", "HOLD", "LANDED OUT", "TAKE-OFF"};

    private static final DataParameter<Integer> MODE = EntityDataManager.createKey(EntityDrone.class, DataSerializers.VARINT);
    private static final DataParameter<Float> BATTERY = EntityDataManager.createKey(EntityDrone.class, DataSerializers.FLOAT);
    private static final DataParameter<Float> GIMBAL = EntityDataManager.createKey(EntityDrone.class, DataSerializers.FLOAT);
    private static final DataParameter<String> NAME = EntityDataManager.createKey(EntityDrone.class, DataSerializers.STRING);

    // ---- server state (saved) ------------------------------------------------------------------------------------------
    public BlockPos dock = BlockPos.ORIGIN;
    public int camNo, flight, mission, wp, resume = MISSION;
    public double alt = 6, metres;
    public double[] way = new double[0];
    public ItemStack airframe = ItemStack.EMPTY, pack = ItemStack.EMPTY;
    public String why = "";
    public UUID controller;
    public long manualAt;
    private Vec3d manualTarget;
    // ---- live, not saved -----------------------------------------------------------------------------------------------
    public double windMs, windDir, noFlyFloor = Double.NaN, loadW;
    public String note = "";
    public final Set<Long> seen = new HashSet<>();
    private int sec;
    // client
    public float spin, spinPrev;
    private int lerpSteps;
    private double lx, ly, lz;
    private float lyaw;

    public EntityDrone(World w) {
        super(w);
        setSize(1.0f, 0.45f);
        noClip = false;
    }

    @Override
    protected void entityInit() {
        dataManager.register(MODE, TAKEOFF);
        dataManager.register(BATTERY, 100f);
        dataManager.register(GIMBAL, 45f);
        dataManager.register(NAME, "DRONE");
    }

    public int mode() { return dataManager.get(MODE); }
    public void mode(int m) { if (mode() != m) dataManager.set(MODE, m); }
    public float battery() { return dataManager.get(BATTERY); }
    public void battery(float b) { dataManager.set(BATTERY, Math.max(0, Math.min(100, b))); }
    public float gimbal() { return dataManager.get(GIMBAL); }
    public void gimbal(float g) { dataManager.set(GIMBAL, Math.max(-10, Math.min(90, g))); }
    public String droneName() { return dataManager.get(NAME); }
    public void droneName(String n) { dataManager.set(NAME, n); }
    public boolean flying() { return mode() != LANDED_OUT; }

    /** where the gimbal camera is and where it looks: {x, y, z, yaw, pitch} */
    public double[] camPose(float pt) {
        double x = lastTickPosX + (posX - lastTickPosX) * pt, y = lastTickPosY + (posY - lastTickPosY) * pt, z = lastTickPosZ + (posZ - lastTickPosZ) * pt;
        float yaw = prevRotationYaw + (rotationYaw - prevRotationYaw) * pt;
        double r = Math.toRadians(yaw);
        return new double[]{x - Math.sin(r) * 0.37, y + 0.07, z + Math.cos(r) * 0.37, yaw, gimbal()};   // just ahead of the gimbal's lens
    }

    // ---- tick ----------------------------------------------------------------------------------------------------------
    @Override
    public void onUpdate() {
        super.onUpdate();
        Drones.track(this);
        if (world.isRemote) { clientTick(); return; }
        prevRotationYaw = rotationYaw;
        try { serverTick(); } catch (RuntimeException ex) { com.dogpound.railmap.RailMap.LOG.warn("[IR Extras] drone tick failed: {}", ex.toString()); }
    }

    private void clientTick() {
        spinPrev = spin;
        boolean on = mode() != LANDED_OUT;
        spin += on ? 62f : 0f;
        if (lerpSteps > 0) {
            double f = 1.0 / lerpSteps;
            setPosition(posX + (lx - posX) * f, posY + (ly - posY) * f, posZ + (lz - posZ) * f);
            float dy = lyaw - rotationYaw;
            while (dy > 180) dy -= 360;
            while (dy < -180) dy += 360;
            rotationYaw += dy * f;
            lerpSteps--;
        }
        if (on && ticksExisted % 3 == 0 && world.rand.nextInt(3) == 0) world.spawnParticle(EnumParticleTypes.CLOUD, posX, posY - 0.3, posZ, 0, -0.05, 0);
    }

    @Override
    public void setPositionAndRotationDirect(double x, double y, double z, float yaw, float pitch, int inc, boolean teleport) {
        lx = x; ly = y; lz = z; lyaw = yaw;
        lerpSteps = teleport ? 1 : 3;
    }

    private void serverTick() {
        int m = mode();
        if (m == LANDED_OUT) {                                                   // sitting where it came down
            motionX = motionZ = 0;
            motionY = onGround ? 0 : Math.max(-0.4, motionY - 0.04);
            move(MoverType.SELF, 0, motionY, 0);
            return;
        }
        if (++sec >= 20) { sec = 0; everySecond(); m = mode(); if (isDead) return; }
        Vec3d tgt = target(m);
        if (tgt == null) return;
        steer(tgt, m);
        avoid(m);
        // the wind pushes; the flight controller pulls back toward the target next tick
        double wb = windMs / 20.0 * 0.006;
        motionX += Math.cos(windDir) * wb;
        motionZ += Math.sin(windDir) * wb;
        double before = posY;
        move(MoverType.SELF, motionX, motionY, motionZ);
        double h = Math.sqrt(motionX * motionX + motionZ * motionZ);
        metres += h;
        if (h > 0.02) {
            float want = (float) Math.toDegrees(Math.atan2(-motionX, motionZ));
            float d = want - rotationYaw;
            while (d > 180) d -= 360;
            while (d < -180) d += 360;
            if (m != MANUAL || controller != null) rotationYaw += Math.max(-9, Math.min(9, d));
        }
        if (collidedHorizontally && DroneConfig.crashes && h > 0.2) { crash("flew into an obstacle"); return; }
        // the battery
        double cruise = DroneConfig.cruiseMs / 20.0, climb = Math.max(0, posY - before);
        double load = 1 + 0.35 * Math.pow(h / cruise, 2) + 0.03 * windMs + climb * 3;
        loadW = 620 * load;
        float fade = (float) DroneItems.Battery.fade(pack);
        battery((float) (battery() - 100.0 / (DroneConfig.enduranceMin * 1200.0) * load / fade));
        if (battery() <= 0) { if (DroneConfig.crashes) crash("battery ran flat"); else landOut("battery ran flat"); return; }
        if (m == LAND && dockReached()) land();
        if (ticksExisted % 30 == 0) world.playSound(null, posX, posY, posZ, ModSounds.WHINE_INV, SoundCategory.NEUTRAL, 0.35f, 1.9f + world.rand.nextFloat() * 0.1f);
    }

    /** where it is flying to now */
    private Vec3d target(int m) {
        Vec3d pad = pad();
        switch (m) {
            case TAKEOFF: {
                if (posY >= pad.y + 3.5) { mode(MISSION); wp = 0; }
                return new Vec3d(pad.x, pad.y + 4, pad.z);
            }
            case MISSION: {
                if (wp >= way.length / 3) { rth("mission complete"); return pad; }
                Vec3d t = wpt(wp);
                if (dist(t) < 1.6) { wp++; return t; }
                return floor(t);
            }
            case RTH: {
                if (wp <= 0 || way.length == 0) { mode(LAND); return pad.add(0, 3, 0); }
                Vec3d t = wpt(Math.min(wp, way.length / 3) - 1);
                if (dist(t) < 1.6) wp--;
                return floor(t);
            }
            case LAND: {
                double hx = posX - pad.x, hz = posZ - pad.z;
                if (hx * hx + hz * hz > 0.3) return new Vec3d(pad.x, Math.max(posY, pad.y + 1.5), pad.z);
                return pad;
            }
            case MANUAL: {
                if (controller != null) {
                    EntityPlayer p = world.getPlayerEntityByUUID(controller);
                    if (p == null || !DroneItems.Controller.holding(p) || p.getDistanceSq(this) > Math.pow(DroneConfig.controllerRange, 2)) {
                        controller = null;
                        rth(p == null || p.getDistanceSq(this) > Math.pow(DroneConfig.controllerRange, 2) ? "controller link lost" : "controller put away");
                        return pad;
                    }
                    if (p.isSneaking()) return new Vec3d(posX, posY, posZ);
                    Vec3d look = p.getLookVec();
                    double hl = Math.max(0.2, Math.sqrt(look.x * look.x + look.z * look.z));
                    return floor(new Vec3d(posX + look.x / hl * 4, posY + look.y * 3, posZ + look.z / hl * 4));
                }
                if (world.getTotalWorldTime() - manualAt > 600) { mode(resume == RTH ? RTH : MISSION); note = "manual control timed out - " + MODES[mode()].toLowerCase(Locale.ROOT); }
                return manualTarget == null ? new Vec3d(posX, posY, posZ) : floor(manualTarget);
            }
            default: return new Vec3d(posX, posY, posZ);
        }
    }

    private Vec3d wpt(int i) { return new Vec3d(way[i * 3], way[i * 3 + 1], way[i * 3 + 2]); }
    private double dist(Vec3d t) { return Math.sqrt(getDistanceSq(t.x, t.y, t.z)); }
    /** a target lifted clear of the no-fly floor */
    private Vec3d floor(Vec3d t) { return Double.isNaN(noFlyFloor) || t.y >= noFlyFloor ? t : new Vec3d(t.x, noFlyFloor, t.z); }

    /** the landing point on the dock's pad */
    public Vec3d pad() { return new Vec3d(dock.getX() + 0.5, dock.getY() + TileDroneDock.PAD_Y, dock.getZ() + 0.5); }

    private boolean dockReached() {
        Vec3d p = pad();
        return Math.abs(posX - p.x) < 0.35 && Math.abs(posZ - p.z) < 0.35 && posY - p.y < 0.25;
    }

    private void steer(Vec3d t, int m) {
        double cruise = DroneConfig.cruiseMs / 20.0 * (m == LAND ? 0.25 : m == MANUAL && controller == null ? 0.6 : 1);
        double dx = t.x - posX, dy = t.y - posY, dz = t.z - posZ;
        double d = Math.sqrt(dx * dx + dz * dz);
        double v = Math.min(cruise, d * 0.18);
        double wx = d < 1e-4 ? 0 : dx / d * v, wz = d < 1e-4 ? 0 : dz / d * v;
        double acc = 0.035;
        motionX += Math.max(-acc, Math.min(acc, wx - motionX));
        motionZ += Math.max(-acc, Math.min(acc, wz - motionZ));
        double vz = Math.max(m == LAND ? -0.06 : -0.2, Math.min(0.22, dy * 0.2));
        motionY += Math.max(-0.04, Math.min(0.04, vz - motionY));
    }

    /** look ahead along the flight: climb over what is in the way, keep off the ground, give people room */
    private void avoid(int m) {
        if (m == LAND) return;
        Vec3d at = new Vec3d(posX, posY + 0.2, posZ);
        double h = Math.sqrt(motionX * motionX + motionZ * motionZ);
        if (h > 0.01) {
            double look = Math.max(2.5, h * 14);
            Vec3d ahead = at.add(motionX / h * look, 0, motionZ / h * look);
            RayTraceResult r = world.rayTraceBlocks(at, ahead, false, true, false);
            if (r != null && r.typeOfHit == RayTraceResult.Type.BLOCK) {
                boolean roof = world.rayTraceBlocks(at, at.add(0, 1.6, 0), false, true, false) != null;
                if (roof) { motionX *= 0.5; motionZ *= 0.5; note = "OBSTACLE AHEAD - holding"; }
                else { motionY = Math.max(motionY, 0.16); motionX *= 0.6; motionZ *= 0.6; note = "OBSTACLE AHEAD - climbing over"; }
            }
        }
        if (world.rayTraceBlocks(at, at.add(0, -1.4, 0), false, true, false) != null) motionY = Math.max(motionY, 0.08);
        for (EntityPlayer p : world.getEntitiesWithinAABB(EntityPlayer.class, getEntityBoundingBox().grow(2.5, 2.5, 2.5))) {
            if (p.isSpectator()) continue;
            double dx = posX - p.posX, dz = posZ - p.posZ, d = Math.max(0.3, Math.sqrt(dx * dx + dz * dz));
            motionX += dx / d * 0.04; motionZ += dz / d * 0.04;
            motionY = Math.max(motionY, 0.06);
        }
    }

    // ---- once a second -------------------------------------------------------------------------------------------------
    private void everySecond() {
        int m = mode();
        // wind (Weather2 / railway weather, else the world's rain) and a slow-turning direction with gusts
        double[] wx = Drones.wind(world, getPosition());
        windMs = wx[0] * (0.8 + world.rand.nextDouble() * 0.4);
        windDir = wx[1];
        String ground = Drones.grounded(world, getPosition());
        if (ground != null && (m == MISSION || m == TAKEOFF || m == HOLD || m == MANUAL)) rth(ground);
        // no-fly zone over station crowds
        noFlyFloor = Drones.noFlyFloor(world, posX, posY, posZ);
        if (!Double.isNaN(noFlyFloor) && posY < noFlyFloor) note = "NO-FLY ZONE: climbing clear of people at the station";
        // the reserve to get home
        double need = reserve(Math.sqrt(getDistanceSq(dock)), DroneItems.Battery.fade(pack));
        if (battery() < need && (m == MISSION || m == HOLD || m == MANUAL || m == TAKEOFF)) rth(String.format(Locale.ROOT, "LOW BATTERY %.0f%% (needs %.0f%% to get home)", battery(), need));
        // inspection along the way
        if (m == MISSION || m == MANUAL || m == HOLD) DroneInspect.scan(this);
        DroneData d = DroneData.get(world);
        DroneData.Flight f = d.flight(flight);
        if (f != null) {
            f.metres = metres;
            f.maxAlt = Math.max(f.maxAlt, posY - dock.getY());
            f.minBattery = Math.min(f.minBattery, battery());
        }
        // the CCTV picture follows it (status ONLINE while it flies)
        CctvData cd = CctvData.get(world);
        CctvData.Cam c = cd.cam(camNo);
        if (c != null && c.train.equals(getUniqueID().toString())) {
            String what = MODES[mode()] + String.format(Locale.ROOT, " %.0f%% %dM", battery(), Math.round(posY - dock.getY()));
            if (!what.equals(c.what)) { c.what = what; cd.changed(); }
        }
        // the handheld's operator sees the flight
        if (controller != null) {
            EntityPlayer p = world.getPlayerEntityByUUID(controller);
            if (p != null) p.sendStatusMessage(new TextComponentString(Drones.statusLine(this)), true);
        }
        if (m == HOLD && world.getTotalWorldTime() - manualAt > 2400) { mode(resume == RTH ? RTH : MISSION); note = "hold released"; }
    }

    /** battery % it needs to get home from `dist` blocks away: the way back (x1.35 for the route), 30 % margin, 8 % to land */
    static double reserve(double dist, double fade) {
        return dist * 1.35 / DroneConfig.cruiseMs / (DroneConfig.enduranceMin * 60.0) * 100 * 1.3 / fade + 8;
    }

    // ---- commands ------------------------------------------------------------------------------------------------------
    public void rth(String reason) {
        int m = mode();
        if (m == RTH || m == LAND || m == LANDED_OUT) return;
        why = reason;
        // back along the route from the nearest waypoint at or behind it
        int best = 0;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < Math.min(way.length / 3, Math.max(wp + 1, 1)); i++) {
            double dd = getDistanceSq(way[i * 3], way[i * 3 + 1], way[i * 3 + 2]);
            if (dd < bd) { bd = dd; best = i; }
        }
        wp = way.length == 0 ? 0 : best + 1;
        mode(RTH);
        controller = null;
        DroneData.get(world).log(world, droneName() + " RETURNING HOME: " + reason);
        Recorder.event(world, "DRONE " + droneName() + " returning home - " + reason);
        note = "";
    }

    /** the desk joystick / console: nudge it by hand (fwd blocks, right blocks, up blocks, yaw degrees) */
    public String nudge(double fwd, double right, double up, float yaw) {
        int m = mode();
        if (m == LANDED_OUT) return "§c" + droneName() + " has landed out - recover it";
        if (m == LAND) return "§7" + droneName() + " is landing";
        if (m != MANUAL) resume = m;
        mode(MANUAL);
        controller = null;
        manualAt = world.getTotalWorldTime();
        rotationYaw += yaw;
        double r = Math.toRadians(rotationYaw);
        double fx = -Math.sin(r), fz = Math.cos(r);
        Vec3d base = manualTarget != null && manualTarget.squareDistanceTo(posX, posY, posZ) < 64 ? manualTarget : new Vec3d(posX, posY, posZ);
        manualTarget = base.add(fx * fwd - fz * right, up, fz * fwd + fx * right);
        double maxY = dock.getY() + 120;
        if (manualTarget.y > maxY) manualTarget = new Vec3d(manualTarget.x, maxY, manualTarget.z);
        return String.format(Locale.ROOT, "§a%s MANUAL · hdg %03d · %.0f m up · %.0f%%", droneName(), Math.round((rotationYaw % 360 + 360) % 360), posY - dock.getY(), battery());
    }

    public void hold() { if (mode() != HOLD && mode() != MANUAL) resume = mode(); mode(HOLD); manualAt = world.getTotalWorldTime(); }

    public void resumeMission() { if (mode() == MANUAL || mode() == HOLD) { mode(resume == RTH ? RTH : MISSION); controller = null; } }

    /** the handheld controller takes it (fly where the operator looks; sneak = hover) */
    public void takeControl(EntityPlayer p) { if (mode() != MANUAL) resume = mode(); mode(MANUAL); controller = p.getUniqueID(); manualAt = world.getTotalWorldTime(); }

    // ---- ending a flight -----------------------------------------------------------------------------------------------
    private void land() {
        TileDroneDock dk = world.isBlockLoaded(dock) && world.getTileEntity(dock) instanceof TileDroneDock t ? t : null;
        if (dk == null) { landOut("its dock is gone"); return; }
        dk.recover(this);                                                     // first: the pack is aged by what this flight used
        end("landed at the dock" + (why.isEmpty() ? "" : " (" + why + ")"));
        setDead();
    }

    public void landOut(String reason) {
        mode(LANDED_OUT);
        motionX = motionZ = 0;
        end("LANDED OUT: " + reason);
        String msg = "DRONE " + droneName() + " landed out at " + getPosition().getX() + "," + getPosition().getZ() + " (" + reason + ") - recover it by hand";
        AlarmManager.get(world).raise(world, "drone:out:" + getUniqueID(), AlarmManager.Cat.MACHINE, AlarmManager.Sev.MAJOR, msg, getPosition(), true);
        Recorder.event(world, msg);
    }

    private void crash(String reason) {
        end("CRASHED: " + reason);
        if (world instanceof WorldServer ws) ws.spawnParticle(EnumParticleTypes.EXPLOSION_NORMAL, posX, posY, posZ, 12, 0.4, 0.3, 0.4, 0.05);
        world.playSound(null, posX, posY, posZ, ModSounds.FUSE_BLOW, SoundCategory.NEUTRAL, 1f, 0.7f);
        String msg = "DRONE " + droneName() + " CRASHED at " + getPosition().getX() + "," + getPosition().getZ() + ": " + reason;
        AlarmManager.get(world).raise(world, "drone:crash:" + getUniqueID(), AlarmManager.Cat.INCIDENT, AlarmManager.Sev.MAJOR, msg, getPosition(), true);
        Recorder.event(world, msg);
        if (!pack.isEmpty()) entityDropItem(pack, 0.2f);                    // the airframe is wrecked; the pack survives
        setDead();
    }

    private void end(String result) {
        DroneData d = DroneData.get(world);
        DroneData.Flight f = d.flight(flight);
        if (f != null && f.end == 0) { f.end = world.getTotalWorldTime(); f.result = result; f.metres = metres; }
        d.log(world, droneName() + " " + result + String.format(Locale.ROOT, " - %.0f m, %.0f%% left", metres, battery()));
        DroneItems.Battery.set(pack, battery());
        Drones.unregisterCam(world, this);
        d.markDirty();
    }

    // ---- by hand -------------------------------------------------------------------------------------------------------
    @Override
    public boolean processInitialInteract(EntityPlayer p, EnumHand hand) {
        if (world.isRemote) return true;
        if (mode() != LANDED_OUT) { p.sendStatusMessage(new TextComponentString(Drones.statusLine(this)), true); return true; }
        pickUp(p);
        return true;
    }

    @Override
    public boolean attackEntityFrom(DamageSource src, float amount) {
        if (world.isRemote || !(src.getTrueSource() instanceof EntityPlayer p)) return false;
        if (mode() == LANDED_OUT || p.capabilities.isCreativeMode && p.isSneaking()) { if (mode() != LANDED_OUT) end("recovered by hand"); pickUp(p); }
        return true;
    }

    private void pickUp(EntityPlayer p) {
        for (ItemStack s : new ItemStack[]{airframe, pack}) if (!s.isEmpty() && !p.addItemStackToInventory(s.copy())) p.entityDropItem(s.copy(), 0.3f);
        airframe = ItemStack.EMPTY; pack = ItemStack.EMPTY;
        AlarmManager.get(world).clear("drone:out:" + getUniqueID());
        p.sendStatusMessage(new TextComponentString("§aDrone " + droneName() + " recovered - put it back in its dock's hangar"), true);
        Drones.unregisterCam(world, this);
        setDead();
    }

    @Override public boolean canBeCollidedWith() { return !isDead; }
    @Override public boolean canBePushed() { return false; }
    @Override public boolean isInRangeToRenderDist(double d2) { return d2 < 160 * 160; }
    @Override public boolean hasNoGravity() { return mode() != LANDED_OUT; }

    // ---- save ----------------------------------------------------------------------------------------------------------
    @Override
    protected void readEntityFromNBT(NBTTagCompound t) {
        dock = BlockPos.fromLong(t.getLong("dock"));
        camNo = t.getInteger("cam"); flight = t.getInteger("flight"); mission = t.getInteger("mission"); wp = t.getInteger("wp"); resume = t.getInteger("resume");
        alt = t.getDouble("alt"); metres = t.getDouble("metres");
        NBTTagList l = t.getTagList("way", 6);
        way = new double[l.tagCount()];
        for (int i = 0; i < way.length; i++) way[i] = l.getDoubleAt(i);
        airframe = new ItemStack(t.getCompoundTag("airframe"));
        pack = new ItemStack(t.getCompoundTag("pack"));
        why = t.getString("why");
        controller = null;
        mode(t.getInteger("mode"));
        battery(t.getFloat("battery"));
        gimbal(t.getFloat("gimbal"));
        droneName(t.getString("name"));
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound t) {
        t.setLong("dock", dock.toLong());
        t.setInteger("cam", camNo); t.setInteger("flight", flight); t.setInteger("mission", mission); t.setInteger("wp", wp); t.setInteger("resume", resume);
        t.setDouble("alt", alt); t.setDouble("metres", metres);
        NBTTagList l = new NBTTagList();
        for (double v : way) l.appendTag(new net.minecraft.nbt.NBTTagDouble(v));
        t.setTag("way", l);
        t.setTag("airframe", airframe.writeToNBT(new NBTTagCompound()));
        t.setTag("pack", pack.writeToNBT(new NBTTagCompound()));
        t.setString("why", why);
        t.setInteger("mode", mode());
        t.setFloat("battery", battery());
        t.setFloat("gimbal", gimbal());
        t.setString("name", droneName());
    }
}
