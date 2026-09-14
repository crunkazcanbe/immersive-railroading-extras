package com.dogpound.railmap.auto;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.library.Augment;
import cam72cam.immersiverailroading.library.SwitchState;
import cam72cam.immersiverailroading.tile.TileRail;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapConfig;
import com.dogpound.railmap.server.StationData;
import com.dogpound.railmap.signal.TileSignalMast;
import com.dogpound.railmap.signal.TrackFollower;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The driverless train: a locomotive that runs its own line, answers tickets, and obeys the
 * railroad like a careful engineer.
 * <p>
 * Every quarter second, for each train handed to it:
 * <ol>
 *   <li>work out the next stop (a ticket's stop first, then its line, shuttle or home station);</li>
 *   <li>find a way there along the real track — every junction tried both ways, forward or
 *       backward, so it can reverse out of a terminus;</li>
 *   <li>line the route ahead: lock and throw each switch before reaching it (never under
 *       another train, never against another route) and clear any CTC signal it owns;</li>
 *   <li>read the signals and speed signs facing it and brake on a proper curve to stop short
 *       of a red, slow for a yellow, obey the posted limit;</li>
 *   <li>drive with the ordinary throttle, brake and reverser — no teleporting, physics
 *       applies — then stop at the platform, ring the bell, run the station's loaders, wait,
 *       and whistle off.</li>
 * </ol>
 */
public final class Autopilot {
    private static final boolean IR = Loader.isModLoaded("immersiverailroading");
    private static final int PERIOD = 5;

    /** Live, unsaved state for one driverless train. */
    public static final class Run {
        TrackPath path;
        long pathTarget = Long.MIN_VALUE;
        /** +1: travel is along the loco's nose; -1: backwards. */
        int sign = 1;
        double stopAt;
        long planTick = Long.MIN_VALUE;
        List<Wayside.Limit> limits = new ArrayList<>();
        long limitsTick = Long.MIN_VALUE;
        double signKmh = Double.MAX_VALUE;
        public boolean dwelling, idle;
        long dwellUntil;
        public long dwellStation;
        final Set<Long> augmentsOn = new HashSet<>();
        final Set<Vec3i> claimed = new HashSet<>();
        final Set<BlockPos> ctcCleared = new HashSet<>();
        public String status = "Starting";
        public String nextStopName = "";
        public double metresToStop = -1;
        public double speedKmh;
        public int entityId = -1;
    }

    private static final Map<UUID, Run> RUNS = new HashMap<>();

    public static Run run(UUID loco) {
        return RUNS.get(loco);
    }

    public static void clear() {
        RUNS.clear();
    }

    /** Stop driving this train: controls to a safe state, locks and CTC routes released. */
    public static void release(World world, UUID loco) {
        Run r = RUNS.remove(loco);
        if (r == null) return;
        Interlocking.releaseAll(world, owner(loco));
        for (BlockPos p : r.ctcCleared) {
            if (world.isBlockLoaded(p) && world.getTileEntity(p) instanceof TileSignalMast m) m.setRoute(false);
        }
        setAugments(world, r, false);
        Locomotive l = find(world, loco);
        if (l != null) {
            l.setThrottle(0);
            l.setTrainBrake(1);
        }
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side.isClient() || !IR) return;
        World world = e.world;
        long now = world.getTotalWorldTime();
        if (now % PERIOD != 0) return;
        RailwayData data = RailwayData.get(world);
        if (now % 20 == 0) {
            Interlocking.tick(world);
            Dispatcher.tick(world, data);
            java.util.List<net.minecraft.entity.player.EntityPlayerMP> viewers = com.dogpound.railmap.server.Viewers.active(world);
            if (!viewers.isEmpty()) {
                com.dogpound.railmap.network.PacketRailState state = com.dogpound.railmap.network.PacketRailState.build(world);
                for (net.minecraft.entity.player.EntityPlayerMP p : viewers) com.dogpound.railmap.RailMap.NETWORK.sendTo(state, p);
            }
        }
        if (data.trains().isEmpty()) return;
        try {
            Map<UUID, Locomotive> locos = new HashMap<>();
            for (Locomotive l : cam72cam.mod.world.World.get(world).getEntities(Locomotive.class)) {
                if (!l.isDead()) locos.put(l.getUUID(), l);
            }
            StationData stations = StationData.get(world);
            for (RailwayData.AutoTrain a : new ArrayList<>(data.trains().values())) {
                Run r = RUNS.computeIfAbsent(a.loco, u -> new Run());
                Locomotive loco = locos.get(a.loco);
                if (loco == null) {
                    r.status = "Not loaded — no one is near this train";
                    r.entityId = -1;
                    continue;
                }
                r.entityId = loco.getId();
                drive(world, data, stations, a, r, loco, now);
            }
        } catch (LinkageError | RuntimeException ex) {
            RailMap.LOG.warn("[irextras] autopilot pass failed: {}", ex.toString());
        }
    }

    // ---- one train -----------------------------------------------------------------------

    private static void drive(World world, RailwayData data, StationData stations, RailwayData.AutoTrain a,
                              Run r, Locomotive loco, long now) {
        double speed = Math.abs(loco.getCurrentSpeed().metric());
        r.speedKmh = speed;
        Long stop = Dispatcher.nextStop(data, a);
        r.nextStopName = stop == null ? "" : nameOf(stations, stop);

        if (r.dwelling) {
            hold(loco);
            long left = (r.dwellUntil - now) / 20;
            r.status = "At " + nameOf(stations, r.dwellStation) + " — departing in " + Math.max(0, left) + "s";
            if (now >= r.dwellUntil) depart(world, data, stations, a, r, loco);
            return;
        }
        if (stop == null) {
            hold(loco);
            r.status = a.mode == RailwayData.Mode.ON_CALL || a.mode == RailwayData.Mode.SEND
                    ? "No home station set" : "No stops — give it a line";
            return;
        }
        BlockPos sp = BlockPos.fromLong(stop);
        Vec3d pos = loco.getPosition();

        // Nothing to do but wait at home for a ticket.
        boolean waitsHome = (a.mode == RailwayData.Mode.ON_CALL || a.mode == RailwayData.Mode.SEND)
                && a.calls.isEmpty() && stop == a.home;
        if (waitsHome && flat(pos, sp) < 10 && speed < 2) {
            hold(loco);
            if (!r.idle) Interlocking.releaseAll(world, owner(a.loco));
            r.idle = true;
            r.path = null;
            r.metresToStop = 0;
            r.status = a.mode == RailwayData.Mode.ON_CALL
                    ? "Waiting at " + r.nextStopName + " for a ticket" : "Arrived at " + r.nextStopName;
            return;
        }
        r.idle = false;

        cam72cam.mod.world.World umc = cam72cam.mod.world.World.get(world);
        double cur = r.path == null ? -1 : r.path.distanceTo(pos.x, pos.y, pos.z, 3.5);
        if (r.path == null || r.pathTarget != stop || cur < 0 || now - r.planTick > 200) {
            if (now - r.planTick < 40 && r.path == null && r.pathTarget == stop) {
                hold(loco);      // a failed search: don't hammer the track every tick
                return;
            }
            plan(world, umc, a, r, loco, sp, speed, now);
            r.pathTarget = stop;
            if (r.path == null) {
                hold(loco);
                r.status = "No route to " + r.nextStopName + " (track missing, unloaded, or switched away)";
                return;
            }
            cur = r.path.distanceTo(pos.x, pos.y, pos.z, 3.5);
            if (cur < 0) cur = 0;
            r.limitsTick = Long.MIN_VALUE;
        }

        double remaining = r.stopAt - cur;
        r.metresToStop = Math.max(0, remaining);

        // Arrived?
        if (remaining < 2.5 && speed < 2.5) {
            arrive(world, data, stations, a, r, loco, stop, now);
            return;
        }

        double decel = RailMapConfig.autopilotBraking;
        double brakingDist = speed / 3.6 * speed / 3.6 / (2 * decel);
        double lookahead = Math.max(80, brakingDist + 60);

        // Line the route ahead: switches, then CTC signals once their switches are ours.
        double blockedAt = Double.MAX_VALUE;
        String blockedWhy = null;
        String me = owner(a.loco);
        for (Map.Entry<Vec3i, SwitchState> e : r.path.switches.entrySet()) {
            Vec3i sw = e.getKey();
            double d = r.path.distanceTo(sw.x + 0.5, sw.y, sw.z + 0.5, 6);
            if (d < 0) continue;
            double ahead = d - cur;
            if (ahead < -20) {
                if (r.claimed.remove(sw)) Interlocking.release(world, sw, me);
                continue;
            }
            if (ahead > lookahead) continue;
            if (Interlocking.claim(world, sw, e.getValue(), me)) {
                r.claimed.add(sw);
            } else if (d - 10 < blockedAt) {
                blockedAt = d - 10;
                blockedWhy = "Waiting for a switch (locked by another route or a train on it)";
            }
        }

        if (now - r.limitsTick >= 20) {
            r.limits = Wayside.read(world, r.path);
            r.limitsTick = now;
            lineCtcSignals(world, r, cur, lookahead, blockedAt);
        }

        // Speed target from every restriction ahead.
        double target = Math.min(a.maxKmh, r.signKmh);
        String why = null;
        for (Wayside.Limit l : r.limits) {
            double dd = l.distance - cur;
            if (!l.stop && l.kmh < 1e6 && dd <= 0.5 && dd > -4) r.signKmh = l.kmh;   // passing a speed sign
            if (dd <= 0.5) continue;
            double allow = l.stop ? Wayside.allowed(dd - 5, 0, decel) : Wayside.allowed(dd, l.kmh, decel);
            if (allow < target) {
                target = allow;
                why = l.what;
            }
        }
        if (blockedAt < Double.MAX_VALUE) {
            double allow = Wayside.allowed(blockedAt - cur, 0, decel);
            if (allow < target) { target = allow; why = blockedWhy; }
        }
        double atStation = Wayside.allowed(remaining - 1.0, 0, decel);
        if (atStation < target) target = atStation;

        control(loco, r, speed, target);

        if (target < 1 && speed < 1 && why != null) {
            r.status = "Stopped: " + why;
        } else {
            r.status = String.format("To %s · %d m · %d km/h (target %d)", r.nextStopName,
                    Math.round(remaining), Math.round(speed), Math.round(Math.min(target, 999)));
        }
    }

    /** Find the way to the stop, trying the direction we're already going first. */
    private static void plan(World world, cam72cam.mod.world.World umc, RailwayData.AutoTrain a, Run r,
                             Locomotive loco, BlockPos stop, double speed, long now) {
        r.planTick = now;
        r.path = null;
        Vec3d p = loco.getPosition();
        TileRail rail = TrackFollower.railUnder(umc, p.x, p.y, p.z);
        if (rail == null) return;
        double yaw = Math.toRadians(loco.getRotationYaw());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        int moving = 0;
        Vec3d v = loco.getVelocity();
        if (speed > 1 && v.x * v.x + v.z * v.z > 1e-6) moving = v.x * fx + v.z * fz >= 0 ? 1 : -1;
        int first = moving != 0 ? moving : loco.getReverser() < -0.05 ? -1 : r.sign;
        TrackPath.Goal goal = TrackPath.near(stop.getX() + 0.5, stop.getY(), stop.getZ() + 0.5, 3.0);
        for (int s : new int[]{ first, -first }) {
            Vec3i from = new Vec3i(Math.floor(p.x - fx * s * 2.5), Math.floor(p.y), Math.floor(p.z - fz * s * 2.5));
            TrackPath path = TrackPath.find(umc, rail, from, RailMapConfig.routeSearchBlocks, goal);
            if (path == null) continue;
            double at = path.distanceTo(stop.getX() + 0.5, stop.getY(), stop.getZ() + 0.5, 3.0);
            if (at < 0) at = path.length;
            // Changing direction releases whatever was lined the other way.
            if (s != r.sign) Interlocking.releaseAll(world, owner(a.loco));
            r.path = path;
            r.sign = s;
            r.stopAt = at;
            r.claimed.clear();
            return;
        }
    }

    /** Clear the CTC signals ahead that belong to our lined route; drop the ones we've passed. */
    private static void lineCtcSignals(World world, Run r, double cur, double lookahead, double blockedAt) {
        for (Wayside.Limit l : r.limits) {
            if (l.where == null || !world.isBlockLoaded(l.where)) continue;
            TileEntity te = world.getTileEntity(l.where);
            if (!(te instanceof TileSignalMast m) || m.mode() != TileSignalMast.Mode.CTC) continue;
            double dd = l.distance - cur;
            if (dd < lookahead + 150 && l.distance < blockedAt && !m.routeSet()) {
                m.setRoute(true);
                r.ctcCleared.add(l.where);
            }
        }
        for (Iterator<BlockPos> it = r.ctcCleared.iterator(); it.hasNext(); ) {
            BlockPos p = it.next();
            double d = r.path.distanceTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 6);
            if (d < 0 || d - cur < -4) {
                if (world.isBlockLoaded(p) && world.getTileEntity(p) instanceof TileSignalMast m) m.setRoute(false);
                it.remove();
            }
        }
    }

    /** Throttle and brake towards a target speed; reverse only from a stand. */
    private static void control(Locomotive loco, Run r, double speed, double target) {
        float want = r.sign;
        if (loco.getReverser() * want <= 0.05f) {
            if (speed > 1) {
                loco.setThrottle(0);
                loco.setTrainBrake(1);
                return;
            }
            loco.setReverser(want);
        }
        if (target < 1) {
            loco.setThrottle(0);
            loco.setTrainBrake(speed > 0.5 ? 1f : 0.7f);
        } else if (speed > target + 2) {
            loco.setThrottle(0);
            loco.setTrainBrake(clamp((float) ((speed - target) / 12 + 0.25)));
        } else if (speed < target - 3) {
            loco.setTrainBrake(0);
            float step = speed < 5 ? 0.15f : 0.08f;
            loco.setThrottle(Math.min(0.9f, loco.getThrottle() + step));
        } else {
            loco.setTrainBrake(0);
            loco.setThrottle(Math.max(0.15f, loco.getThrottle() * 0.97f));
        }
    }

    // ---- stations ------------------------------------------------------------------------

    private static void arrive(World world, RailwayData data, StationData stations, RailwayData.AutoTrain a,
                               Run r, Locomotive loco, long station, long now) {
        hold(loco);
        r.dwelling = true;
        r.dwellStation = station;
        r.dwellUntil = now + Math.max(5, a.dwellSeconds) * 20L;
        r.path = null;
        loco.setBell(60);
        setAugmentsNear(world, r, BlockPos.fromLong(station), true);
        String here = nameOf(stations, station);
        String label = label(a, loco);
        Dispatcher.arrived(world, data, a, station, label, here);
        ArrivalEvents.arrived(world, station, label);
    }

    private static void depart(World world, RailwayData data, StationData stations, RailwayData.AutoTrain a,
                               Run r, Locomotive loco) {
        r.dwelling = false;
        setAugments(world, r, false);
        Dispatcher.departed(world, data, a, r.dwellStation);
        Dispatcher.advance(data, a, r.dwellStation);
        loco.setHorn(20, 1f);
        loco.setBell(0);
        data.markDirty();
    }

    // ---- loaders -------------------------------------------------------------------------

    /** Run the IR loaders and unloaders around a station while the train stands there. */
    private static void setAugmentsNear(World world, Run r, BlockPos station, boolean on) {
        cam72cam.mod.world.World umc = cam72cam.mod.world.World.get(world);
        for (int dx = -12; dx <= 12; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -12; dz <= 12; dz++) {
                    Vec3i v = new Vec3i(station.getX() + dx, station.getY() + dy, station.getZ() + dz);
                    if (!umc.isBlockLoaded(v)) continue;
                    TileRailBase base = umc.getBlockEntity(v, TileRailBase.class);
                    if (base == null) continue;
                    Augment aug = base.getAugment();
                    if (aug != Augment.ITEM_LOADER && aug != Augment.ITEM_UNLOADER
                            && aug != Augment.FLUID_LOADER && aug != Augment.FLUID_UNLOADER) continue;
                    try {
                        base.setRedstoneLevel(on ? 15 : 0);
                        base.markDirty();
                        r.augmentsOn.add(v.toLong());
                    } catch (RuntimeException ignored) {
                        // an augment that refuses redstone is simply left alone
                    }
                }
            }
        }
    }

    private static void setAugments(World world, Run r, boolean on) {
        cam72cam.mod.world.World umc = cam72cam.mod.world.World.get(world);
        for (long k : r.augmentsOn) {
            BlockPos b = BlockPos.fromLong(k);
            Vec3i v = new Vec3i(b.getX(), b.getY(), b.getZ());
            if (!umc.isBlockLoaded(v)) continue;
            TileRailBase base = umc.getBlockEntity(v, TileRailBase.class);
            if (base != null) {
                try {
                    base.setRedstoneLevel(on ? 15 : 0);
                    base.markDirty();
                } catch (RuntimeException ignored) {
                    // see setAugmentsNear
                }
            }
        }
        if (!on) r.augmentsOn.clear();
    }

    // ---- helpers -------------------------------------------------------------------------

    static String owner(UUID loco) {
        return "auto:" + loco;
    }

    private static void hold(Locomotive loco) {
        loco.setThrottle(0);
        loco.setTrainBrake(1);
    }

    public static Locomotive find(World world, UUID id) {
        try {
            return cam72cam.mod.world.World.get(world).getEntity(id, Locomotive.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static String label(RailwayData.AutoTrain a, EntityRollingStock loco) {
        if (a.label != null && !a.label.isEmpty()) return a.label;
        if (loco.tag != null && !loco.tag.isEmpty()) return loco.tag;
        return loco.getDefinition() == null ? "Train" : loco.getDefinition().name();
    }

    public static String nameOf(StationData stations, long key) {
        String n = stations.names().get(key);
        if (n != null) return n;
        BlockPos p = BlockPos.fromLong(key);
        return p.getX() + "," + p.getZ();
    }

    /** Every player riding anywhere in this train's consist. */
    static List<EntityPlayer> riders(EntityRollingStock stock) {
        List<EntityPlayer> out = new ArrayList<>();
        List<EntityRollingStock> cars = new ArrayList<>();
        if (stock instanceof EntityCoupleableRollingStock c) cars.addAll(c.getTrain());
        else cars.add(stock);
        for (EntityRollingStock car : cars) {
            for (cam72cam.mod.entity.Entity e : car.getPassengers()) {
                if (e.internal instanceof EntityPlayer p) out.add(p);
            }
        }
        return out;
    }

    static void tell(EntityPlayer p, String msg) {
        if (p instanceof EntityPlayerMP mp) mp.sendStatusMessage(new TextComponentString(msg), true);
    }

    private static double flat(Vec3d p, BlockPos b) {
        double dx = p.x - b.getX() - 0.5, dz = p.z - b.getZ() - 0.5;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
