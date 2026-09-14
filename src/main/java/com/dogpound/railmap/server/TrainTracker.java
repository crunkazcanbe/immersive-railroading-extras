package com.dogpound.railmap.server;

import cam72cam.immersiverailroading.entity.CarPassenger;
import cam72cam.immersiverailroading.entity.CarTank;
import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Freight;
import cam72cam.immersiverailroading.entity.FreightTank;
import cam72cam.immersiverailroading.entity.HandCar;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.entity.LocomotiveDiesel;
import cam72cam.immersiverailroading.entity.LocomotiveSteam;
import cam72cam.immersiverailroading.entity.Tender;
import cam72cam.immersiverailroading.registry.EntityRollingStockDefinition;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.graph.LogEntry;
import com.dogpound.railmap.graph.TrainNode;
import com.dogpound.railmap.network.PacketTrains;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server tick: every {@link #PERIOD} ticks, snapshot every loaded IR rolling stock into
 * {@link TrainNode}s, push them to whoever is watching a board, and keep the station
 * timetable (arrive/depart edges per train, judged by its lead locomotive).
 * <p>
 * IR classes are only touched inside {@link #snapshot}, which is skipped when IR is absent.
 */
public final class TrainTracker {
    private static final int PERIOD = 10;
    /** A loco within this many blocks of a named station "is at" it. */
    private static final double STATION_RADIUS = 6;
    private static final boolean IR = Loader.isModLoaded("immersiverailroading");

    /** Latest snapshot per dimension, for Dynmap and the station log. */
    private static final Map<Integer, List<TrainNode>> latest = new HashMap<>();
    /** Lead-loco entity id → station key it is currently at (edge detection). */
    private static final Map<Integer, Long> atStation = new HashMap<>();

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side.isClient() || !IR) return;
        World world = e.world;
        if (world.getTotalWorldTime() % PERIOD != 0) return;
        List<EntityPlayerMP> viewers = Viewers.active(world);
        StationData stations = StationData.get(world);
        boolean anyStations = !stations.names().isEmpty();
        boolean dynmap = RailMap.dynmap != null && RailMap.dynmap.wantsTrains();
        if (viewers.isEmpty() && !anyStations && !dynmap) return;

        List<TrainNode> trains;
        try {
            trains = snapshot(world, stations);
        } catch (LinkageError | RuntimeException ex) {
            RailMap.LOG.warn("[RailMap] train snapshot failed: {}", ex.toString());
            return;
        }
        latest.put(world.provider.getDimension(), trains);
        if (!viewers.isEmpty()) {
            PacketTrains pkt = new PacketTrains(world.provider.getDimension(), trains);
            for (EntityPlayerMP p : viewers) RailMap.NETWORK.sendTo(pkt, p);
        }
        if (dynmap && world.getTotalWorldTime() % 40 == 0) RailMap.dynmap.trains(world, trains);
    }

    public static List<TrainNode> latest(World world) {
        List<TrainNode> l = latest.get(world.provider.getDimension());
        return l == null ? Collections.<TrainNode>emptyList() : l;
    }

    public static void clear() {
        latest.clear();
        atStation.clear();
    }

    // ---- IR access below this line ------------------------------------------------------

    private List<TrainNode> snapshot(World mcWorld, StationData stations) {
        cam72cam.mod.world.World world = cam72cam.mod.world.World.get(mcWorld);
        List<EntityRollingStock> all = world.getEntities(EntityRollingStock.class);
        List<TrainNode> out = new ArrayList<>(all.size());
        // Station positions once, as doubles, for the heading guess and the timetable.
        List<long[]> stationKeys = new ArrayList<>();
        for (Long k : stations.names().keySet()) stationKeys.add(new long[]{k});
        long now = mcWorld.getTotalWorldTime();

        for (EntityRollingStock stock : all) {
            if (stock.isDead()) continue;
            EntityRollingStockDefinition def = stock.getDefinition();
            String name = def == null ? stock.getDefinitionID() : def.name();
            Vec3d p = stock.getPosition();
            Vec3d v = stock.getVelocity();

            float speed = 0;
            if (stock instanceof EntityMoveableRollingStock m && m.getCurrentSpeed() != null) {
                speed = (float) m.getCurrentSpeed().metric();
            }
            // Map-space heading: angle of the motion vector, else the entity's facing.
            // MC yaw 0 = +Z, 90 = -X, so facing = (-sin, cos).
            double dx, dz;
            if (v.x * v.x + v.z * v.z > 1e-4) {
                dx = v.x; dz = v.z;
            } else {
                double yaw = Math.toRadians(stock.getRotationYaw());
                dx = -Math.sin(yaw); dz = Math.cos(yaw);
                if (speed < 0) { dx = -dx; dz = -dz; }
            }
            float mapYaw = (float) Math.toDegrees(Math.atan2(dz, dx));

            TrainNode.Kind kind = kindOf(stock);
            int cargo = -1;
            if (stock instanceof FreightTank ft) cargo = ft.getPercentLiquidFull();
            else if (stock instanceof Freight f) cargo = f.getPercentCargoFull();
            int pax = stock.getPassengerCount();

            int consist = 1;
            boolean lead = true;
            if (stock instanceof EntityCoupleableRollingStock c && c.isCoupled()) {
                List<EntityCoupleableRollingStock> train = c.getTrain();
                consist = train.size();
                lead = leadOf(train) == stock;
            }

            float th = 0, rv = 0, br = 0;
            if (stock instanceof Locomotive loco) {
                th = loco.getThrottle();
                rv = loco.getReverser();
                br = loco.getTrainBrakePos();
            }

            String heading = "";
            boolean moving = Math.abs(speed) > 0.5;
            if (moving && !stationKeys.isEmpty()) heading = headingGuess(p, dx, dz, stations);

            out.add(new TrainNode(stock.getId(), (float) p.x, (float) p.y, (float) p.z, mapYaw, speed, kind,
                    name, stock.tag, cargo, pax, consist, lead, th, rv, br, heading));

            // Timetable: judged once per train, by its lead loco (a handcar counts).
            if (lead && kind.isLoco() && !stationKeys.isEmpty()) {
                timetable(stations, stock, p, moving, name, now);
            }
        }
        return out;
    }

    /** The unit that names the train: the lowest-id locomotive, else the lowest-id car. */
    private static EntityCoupleableRollingStock leadOf(List<EntityCoupleableRollingStock> train) {
        EntityCoupleableRollingStock best = null;
        for (EntityCoupleableRollingStock s : train) {
            boolean loco = s instanceof Locomotive;
            boolean bestLoco = best instanceof Locomotive;
            if (best == null || (loco && !bestLoco) || (loco == bestLoco && s.getId() < best.getId())) best = s;
        }
        return best;
    }

    /**
     * Nearest named station that lies roughly ahead (within 50° of the motion vector). It is a
     * guess, not a route: IR has no schedules, and the real answer depends on switches ahead.
     * ponytail: cone heuristic; walk the graph through switch states if this misleads anyone.
     */
    private static String headingGuess(Vec3d p, double dx, double dz, StationData stations) {
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-6) return "";
        dx /= len; dz /= len;
        String best = "";
        double bestD = 400 * 400;
        for (Map.Entry<Long, String> e : stations.names().entrySet()) {
            BlockPos s = BlockPos.fromLong(e.getKey());
            double sx = s.getX() + 0.5 - p.x, sz = s.getZ() + 0.5 - p.z;
            double d = sx * sx + sz * sz;
            if (d < 9 || d > bestD) continue; // already there / too far
            double dist = Math.sqrt(d);
            double cos = (sx * dx + sz * dz) / dist;
            if (cos < 0.64) continue; // > ~50° off the nose
            bestD = d;
            best = e.getValue();
        }
        return best;
    }

    private static void timetable(StationData stations, EntityRollingStock loco, Vec3d p, boolean moving,
                                  String name, long now) {
        Long here = null;
        String hereName = null;
        double bd = STATION_RADIUS * STATION_RADIUS;
        for (Map.Entry<Long, String> e : stations.names().entrySet()) {
            BlockPos s = BlockPos.fromLong(e.getKey());
            double dx = s.getX() + 0.5 - p.x, dy = s.getY() - p.y, dz = s.getZ() + 0.5 - p.z;
            if (Math.abs(dy) > 4) continue;
            double d = dx * dx + dz * dz;
            if (d < bd) { bd = d; here = e.getKey(); hereName = e.getValue(); }
        }
        String label = loco.tag == null || loco.tag.isEmpty() ? name : loco.tag;
        Long was = atStation.get(loco.getId());
        if (here != null && !moving && (was == null || !was.equals(here))) {
            atStation.put(loco.getId(), here);
            stations.addLog(new LogEntry(now, label, hereName, true));
        } else if (was != null && (here == null || !was.equals(here)) && moving) {
            String from = stations.names().get(was);
            atStation.remove(loco.getId());
            if (from != null) stations.addLog(new LogEntry(now, label, from, false));
        }
    }

    private static TrainNode.Kind kindOf(EntityRollingStock s) {
        if (s instanceof LocomotiveSteam) return TrainNode.Kind.LOCO_STEAM;
        if (s instanceof LocomotiveDiesel) return TrainNode.Kind.LOCO_DIESEL;
        if (s instanceof HandCar) return TrainNode.Kind.HANDCAR;
        if (s instanceof Tender) return TrainNode.Kind.TENDER;
        if (s instanceof CarPassenger) return TrainNode.Kind.PASSENGER;
        if (s instanceof CarTank) return TrainNode.Kind.TANK;
        if (s instanceof Freight) return TrainNode.Kind.FREIGHT;
        return TrainNode.Kind.OTHER;
    }
}
