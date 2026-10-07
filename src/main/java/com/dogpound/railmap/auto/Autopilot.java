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
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
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
        long planTick = -1_000_000L;   // not Long.MIN_VALUE: "now - planTick" would overflow negative
        List<Wayside.Limit> limits = new ArrayList<>();
        long limitsTick = -1_000_000L; // not Long.MIN_VALUE: "now - limitsTick >= 20" would never be true
        double signKmh = Double.MAX_VALUE;
        public boolean dwelling, idle;
        long dwellUntil;
        public long dwellStation;
        /** The order being carried out at this stop, and the cargo aboard when it arrived (for profit). */
        RailwayData.Order order = RailwayData.Order.STOP;
        int cargoAtArrive;
        long dwellStart;
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
            if (r.order == RailwayData.Order.FULL_LOAD && now >= r.dwellUntil) {
                int fill = fillPercent(loco);
                if (fill < 98 && now - r.dwellStart < RailMapConfig.fullLoadMaxMinutes * 1200L) {
                    r.dwellUntil = now + 40;     // OpenTTD "full load": keep the loaders running until every car is full
                    r.status = "At " + nameOf(stations, r.dwellStation) + " — full load: " + fill + "% full";
                    return;
                }
            }
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
            r.limitsTick = -1_000_000L;
        }

        double remaining = r.stopAt - cur;
        r.metresToStop = Math.max(0, remaining);

        // "Go via": don't brake for this stop - once it's inside braking range, aim for the next one instead.
        com.dogpound.railmap.station.TileStationMaster desk = com.dogpound.railmap.station.TileStationMaster.at(world, sp);
        boolean requestSkip = desk != null && desk.skipRequestStop(riders(loco));
        if (orderNow(data, a, stop) == RailwayData.Order.VIA || requestSkip) {
            double ms = speed / 3.6;
            if (remaining < ms * ms / (2 * RailMapConfig.autopilotBraking) + 20) {
                Dispatcher.advance(data, a, stop);
                lapCheck(a);
                r.path = null;
                data.markDirty();
                return;
            }
        }

        // Arrived? Air brakes can let a train slide a few metres past the mark; that still counts
        // (otherwise it replans the whole loop and never stops at all).
        if (remaining < 2.5 && remaining > -12 && speed < 2.5) {
            arrive(world, data, stations, a, r, loco, stop, now);
            return;
        }

        double decel = RailMapConfig.autopilotBraking;
        // Immersive Railroading's air brakes take a moment to build cylinder pressure: plan every
        // stop as if the train keeps rolling at full speed for that long before it starts slowing.
        double lag = speed / 3.6 * RailMapConfig.autopilotBrakeLagSeconds;
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
            double allow = l.stop ? Wayside.allowed(dd - 5 - lag, 0, decel) : Wayside.allowed(dd - lag, l.kmh, decel);
            if (allow < target) {
                target = allow;
                why = l.what;
            }
        }
        if (blockedAt < Double.MAX_VALUE) {
            double allow = Wayside.allowed(blockedAt - cur - lag, 0, decel);
            if (allow < target) { target = allow; why = blockedWhy; }
        }
        double atStation = Wayside.allowed(remaining - 1.0 - lag, 0, decel);
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
        r.order = orderNow(data, a, station);
        r.dwellStart = now;
        com.dogpound.railmap.station.TileStationMaster desk = com.dogpound.railmap.station.TileStationMaster.at(world, BlockPos.fromLong(station));
        int dwell = desk != null && desk.dwell() > 0 ? desk.dwell() : a.dwellSeconds;
        r.dwellUntil = now + (r.order == RailwayData.Order.WAIT ? 60 : Math.max(5, dwell)) * 20L;
        r.cargoAtArrive = cargoUnits(loco);
        r.path = null;
        loco.setBell(60);
        boolean load = r.order != RailwayData.Order.UNLOAD, unload = r.order != RailwayData.Order.LOAD && r.order != RailwayData.Order.FULL_LOAD;
        setAugmentsNear(world, r, BlockPos.fromLong(station), load, unload);
        String here = nameOf(stations, station);
        String label = label(a, loco);
        Dispatcher.arrived(world, data, a, station, label, here);
        ArrivalEvents.arrived(world, station, label);
    }

    private static void depart(World world, RailwayData data, StationData stations, RailwayData.AutoTrain a,
                               Run r, Locomotive loco) {
        r.dwelling = false;
        setAugments(world, r, false);
        pay(world, stations, a, r, loco);
        Dispatcher.departed(world, data, a, r.dwellStation);
        Dispatcher.advance(data, a, r.dwellStation);
        lapCheck(a);
        loco.setHorn(20, 1f);
        loco.setBell(0);
        data.markDirty();
    }

    // ---- loaders -------------------------------------------------------------------------

    /** Run the IR loaders and unloaders around a station while the train stands there. */
    private static void setAugmentsNear(World world, Run r, BlockPos station, boolean load, boolean unload) {
        boolean on = load || unload;
        cam72cam.mod.world.World umc = cam72cam.mod.world.World.get(world);
        for (int dx = -12; dx <= 12; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -12; dz <= 12; dz++) {
                    Vec3i v = new Vec3i(station.getX() + dx, station.getY() + dy, station.getZ() + dz);
                    if (!umc.isBlockLoaded(v)) continue;
                    TileRailBase base = umc.getBlockEntity(v, TileRailBase.class);
                    if (base == null) continue;
                    Augment aug = base.getAugment();
                    boolean isLoad = aug == Augment.ITEM_LOADER || aug == Augment.FLUID_LOADER;
                    boolean isUnload = aug == Augment.ITEM_UNLOADER || aug == Augment.FLUID_UNLOADER;
                    if (!(isLoad && load) && !(isUnload && unload)) continue;   // the stop's order picks which ones run
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

    // ---- orders + profit (OpenTTD style) ---------------------------------------------------

    /** The order for the stop the train is heading to: its line's order there, plain STOP otherwise. */
    static RailwayData.Order orderNow(RailwayData data, RailwayData.AutoTrain a, long stop) {
        if (!a.calls.isEmpty() || (a.mode != RailwayData.Mode.LINE && a.mode != RailwayData.Mode.SHUTTLE)) return RailwayData.Order.STOP;
        RailwayData.Line line = data.line(a.line);
        if (line == null || a.index < 0 || a.index >= line.stations.size() || line.stations.get(a.index) != stop) return RailwayData.Order.STOP;
        return line.order(a.index);
    }

    /** A line train back at its first stop: one lap done - roll its lap income over. */
    private static void lapCheck(RailwayData.AutoTrain a) {
        if (a.mode == RailwayData.Mode.LINE && a.index == 0 && a.calls.isEmpty()) {
            a.trips++;
            a.lastLap = a.earnedLap;
            a.earnedLap = 0;
        }
    }

    /** Cargo dropped here pays by amount x distance from where it was loaded; cargo picked up here starts a new trip. */
    private static void pay(World world, StationData stations, RailwayData.AutoTrain a, Run r, Locomotive loco) {
        int now = cargoUnits(loco), dropped = r.cargoAtArrive - now;
        if (dropped > 0) {
            BlockPos from = a.loadedAt == 0 ? null : BlockPos.fromLong(a.loadedAt), to = BlockPos.fromLong(r.dwellStation);
            double dist = from == null ? 0 : Math.sqrt(from.distanceSq(to));
            long money = Math.round(dropped * (1 + dist / RailMapConfig.profitBlocksPerCoin) * RailMapConfig.profitPerItem);
            a.earned += money;
            a.earnedLap += money;
            a.delivered += dropped;
            for (EntityPlayer p : riders(loco)) tell(p, "[TRAIN] Delivered " + dropped + " at " + nameOf(stations, r.dwellStation) + " · +$" + money);
        }
        if (now > Math.max(0, r.cargoAtArrive - Math.max(0, dropped))) a.loadedAt = r.dwellStation;
    }

    private static List<Entity> consist(Locomotive loco) {
        List<Entity> out = new ArrayList<>();
        try {
            for (EntityCoupleableRollingStock c : loco.getTrain()) out.add(c.internal);
        } catch (RuntimeException e) {
            out.add(loco.internal);
        }
        return out;
    }

    /** Items aboard + fluid buckets aboard, over the whole consist. */
    static int cargoUnits(Locomotive loco) {
        int n = 0;
        for (Entity e : consist(loco)) {
            if (e == null) continue;
            if (e.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null)) {
                IItemHandler h = e.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
                if (h != null) for (int i = 0; i < h.getSlots(); i++) n += h.getStackInSlot(i).getCount();
            }
            if (e.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null)) {
                IFluidHandler f = e.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null);
                if (f != null) for (IFluidTankProperties t : f.getTankProperties()) if (t.getContents() != null) n += t.getContents().amount / 1000;
            }
        }
        return n;
    }

    /** How full the emptiest cargo car is (100 when the train carries no cargo cars at all). */
    static int fillPercent(Locomotive loco) {
        int worst = 100;
        for (Entity e : consist(loco)) {
            if (e == null || e == loco.internal) continue;
            if (e.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null)) {
                IItemHandler h = e.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
                if (h != null && h.getSlots() > 0) {
                    long used = 0, cap = 0;
                    for (int i = 0; i < h.getSlots(); i++) {
                        net.minecraft.item.ItemStack st = h.getStackInSlot(i);
                        used += st.getCount();
                        cap += st.isEmpty() ? h.getSlotLimit(i) : Math.min(h.getSlotLimit(i), st.getMaxStackSize());
                    }
                    if (cap > 0) worst = Math.min(worst, (int) (used * 100 / cap));
                }
            }
            if (e.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null)) {
                IFluidHandler f = e.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null);
                if (f != null) for (IFluidTankProperties t : f.getTankProperties()) {
                    if (t.getCapacity() <= 0) continue;
                    worst = Math.min(worst, (t.getContents() == null ? 0 : t.getContents().amount) * 100 / t.getCapacity());
                }
            }
        }
        return worst;
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
