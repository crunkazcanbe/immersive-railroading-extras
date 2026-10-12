package com.dogpound.railmap.road;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.ems.RoadPath;
import com.dogpound.railmap.signal.SignalRegistry;
import com.dogpound.railmap.signal.TileCrossing;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.*;

/**
 * The road traffic manager (batch 5 #3): once a second it removes vehicles far from every player, spawns new ones on
 * road blocks 28-72 blocks from players (never in sight, never over the per-player and world caps), keeps each level
 * crossing's road statistics (queue now, longest today, vehicles delayed, closures, longest closure) and raises / clears
 * the ROAD QUEUE alarm for a barrier down too long with traffic waiting. It also rations the A* road path searches to a
 * few per tick for all vehicles together, so a busy town can't stall the server.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class RoadTraffic {
    private RoadTraffic() {}

    private static final Map<Integer, Integer> BUDGET = new HashMap<>();
    private static final Random RNG = new Random();
    private static long searches;

    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side.isClient()) return;
        World w = e.world;
        BUDGET.put(w.provider.getDimension(), RoadConfig.pathsPerTick);
        if (w.getTotalWorldTime() % 20 != 13) return;
        try {
            manage(w);
            crossings(w);
        } catch (RuntimeException | LinkageError ex) {
            RailMap.LOG.warn("[IR Extras] road traffic tick failed: {}", ex.toString());
        }
    }

    /** may a vehicle run an A* search this tick? */
    static boolean takePath(World w) {
        int dim = w.provider.getDimension();
        int b = BUDGET.getOrDefault(dim, RoadConfig.pathsPerTick);
        if (b <= 0) return false;
        BUDGET.put(dim, b - 1);
        searches++;
        return true;
    }

    static List<EntityRoadVehicle> vehicles(World w) {
        List<EntityRoadVehicle> l = new ArrayList<>();
        for (Entity e : w.loadedEntityList) if (e instanceof EntityRoadVehicle v && !v.isDead) l.add(v);
        return l;
    }

    private static List<EntityPlayer> players(World w) {
        List<EntityPlayer> l = new ArrayList<>();
        for (EntityPlayer p : w.playerEntities) if (!p.isSpectator()) l.add(p);
        return l;
    }

    /** a player close enough to see it */
    static boolean seen(World w, Entity v) {
        for (EntityPlayer p : w.playerEntities) if (p.getDistanceSq(v) < 24 * 24) return true;
        return false;
    }

    private static void manage(World w) {
        RoadData d = RoadData.get(w);
        List<EntityRoadVehicle> vs = vehicles(w);
        List<EntityPlayer> ps = players(w);
        double far = (double) RoadConfig.despawn * RoadConfig.despawn;
        for (EntityRoadVehicle v : vs) {
            boolean near = false;
            for (EntityPlayer p : ps) if (p.getDistanceSq(v) < far) { near = true; break; }
            if (!near) { v.setDead(); d.removed++; }
        }
        if (!RoadConfig.traffic || d.paused) return;
        vs.removeIf(v -> v.isDead);
        int total = vs.size();
        for (EntityPlayer p : ps) {
            if (total >= RoadConfig.maxVehicles) return;
            int mine = 0;
            for (EntityRoadVehicle v : vs) if (v.getDistanceSq(p) < (RoadConfig.spawnMax + 16.0) * (RoadConfig.spawnMax + 16.0)) mine++;
            if (mine >= RoadConfig.perPlayer) continue;
            EntityRoadVehicle v = spawnNear(w, p.getPosition(), ps, vs, null);
            if (v != null) { vs.add(v); total++; d.spawned++; }
        }
    }

    /** try to put a vehicle on a road near `at` (kind null = by the configured mix); null if no road found */
    static EntityRoadVehicle spawnNear(World w, BlockPos at, List<EntityPlayer> ps, List<EntityRoadVehicle> vs, EntityRoadVehicle.Kind kind) {
        int lo = Math.min(RoadConfig.spawnMin, RoadConfig.spawnMax - 4), hi = Math.max(RoadConfig.spawnMin + 4, RoadConfig.spawnMax);
        for (int tries = 0; tries < 10; tries++) {
            double a = RNG.nextDouble() * Math.PI * 2, r = lo + RNG.nextDouble() * (hi - lo);
            BlockPos g = roadAt(w, at.getX() + (int) (Math.cos(a) * r), at.getZ() + (int) (Math.sin(a) * r), at.getY());
            if (g == null) continue;
            boolean bad = false;
            for (EntityPlayer p : ps) if (p.getDistanceSq(g) < (double) lo * lo) { bad = true; break; }
            for (EntityRoadVehicle v : vs) if (v.getDistanceSq(g) < 10 * 10) { bad = true; break; }
            if (bad) continue;
            BlockPos dest = destination(w, g);
            if (dest == null) continue;
            EntityRoadVehicle.Kind k = kind != null ? kind : pickKind();
            int col = RNG.nextInt(k == EntityRoadVehicle.Kind.BUS ? EntityRoadVehicle.BUS_COLOURS.length : EntityRoadVehicle.COLOURS.length);
            EntityRoadVehicle v = new EntityRoadVehicle(w, k, col, dest);
            v.setLocationAndAngles(g.getX() + 0.5, g.getY(), g.getZ() + 0.5, (float) Math.toDegrees(Math.atan2(-(dest.getX() - g.getX()), dest.getZ() - g.getZ())), 0);
            if (w.spawnEntity(v)) return v;
        }
        return null;
    }

    static EntityRoadVehicle.Kind pickKind() {
        int[] m = RoadConfig.mix;
        int sum = 0;
        for (int i = 0; i < Math.min(4, m.length); i++) sum += Math.max(0, m[i]);
        if (sum <= 0) return EntityRoadVehicle.Kind.CAR;
        int r = RNG.nextInt(sum);
        for (int i = 0; i < Math.min(4, m.length); i++) { r -= Math.max(0, m[i]); if (r < 0) return EntityRoadVehicle.Kind.values()[i]; }
        return EntityRoadVehicle.Kind.CAR;
    }

    /** feet position on a road block at column x,z near height y, or null */
    static BlockPos roadAt(World w, int x, int z, int y) {
        BlockPos col = new BlockPos(x, y, z);
        if (!w.isBlockLoaded(col)) return null;
        int top = w.getHeight(x, z);
        BlockPos g = RoadPath.ground(w, new BlockPos(x, Math.abs(top - y) < 12 ? top : y + 1, z), 6);
        if (g == null || !RoadPath.isRoad(w.getBlockState(g.down()))) return null;
        return g;
    }

    /** somewhere on the road 40-140 blocks away to drive to */
    static BlockPos destination(World w, BlockPos from) {
        for (int tries = 0; tries < 12; tries++) {
            double a = RNG.nextDouble() * Math.PI * 2, r = 40 + RNG.nextDouble() * 100;
            BlockPos g = roadAt(w, from.getX() + (int) (Math.cos(a) * r), from.getZ() + (int) (Math.sin(a) * r), from.getY());
            if (g != null) return g;
        }
        return null;
    }

    // ---- level crossing statistics + ROAD QUEUE alarm ----------------------------------------------------------------
    private static void crossings(World w) {
        List<TileCrossing> xs = SignalRegistry.crossings(w.provider.getDimension());
        RoadData d = RoadData.get(w);
        Map<Long, Integer> q = new HashMap<>();
        for (EntityRoadVehicle v : vehicles(w)) if (v.queued()) q.merge(v.queuedFor, 1, Integer::sum);
        long now = w.getTotalWorldTime(), day = w.getWorldTime() / 24000;
        boolean dirty = false;
        for (TileCrossing x : xs) {
            if (x.isInvalid()) continue;
            long key = x.getPos().toLong();
            RoadData.Crossing c = d.crossing(key);
            if (c.day != day) { c.day = day; c.maxToday = 0; c.delayedToday = 0; dirty = true; }
            c.queue = q.getOrDefault(key, 0);
            if (c.queue > c.maxToday) { c.maxToday = c.queue; dirty = true; }
            if (x.active()) {
                if (c.closedSince < 0) { c.closedSince = now; c.closures++; dirty = true; }
                long closed = now - c.closedSince;
                if (closed > c.longestClose) c.longestClose = closed;
            } else c.closedSince = -1;
            boolean cond = RoadConfig.queueAlarmSeconds > 0 && x.active() && now - c.closedSince >= RoadConfig.queueAlarmSeconds * 20L && c.queue >= RoadConfig.queueAlarmVehicles;
            String ak = "roadq:" + key;
            if (cond && !c.alarm) {
                c.alarm = true; dirty = true;
                AlarmManager.get(w).raise(w, ak, AlarmManager.Cat.SIGNAL, AlarmManager.Sev.MAJOR, String.format(Locale.ROOT,
                        "ROAD QUEUE at level crossing %d,%d: %d vehicles waiting, barriers down %d min", x.getPos().getX(), x.getPos().getZ(), c.queue, (now - c.closedSince) / 1200), x.getPos(), true);
            } else if (!cond && c.alarm && (!x.active() || c.queue == 0)) {
                c.alarm = false; dirty = true;
                AlarmManager.get(w).clear(ak);
            }
        }
        if (dirty) d.markDirty();
    }

    /** a vehicle has just joined the queue at a crossing */
    static void delayed(World w, long crossing) {
        RoadData.Crossing c = RoadData.get(w).crossing(crossing);
        c.delayedToday++; c.delayedTotal++;
        RoadData.get(w).markDirty();
    }

    /** vehicles queued at this crossing now (the crossing panel's QUEUE digits) */
    public static int queue(World w, BlockPos crossing) { RoadData.Crossing c = RoadData.get(w).crossings.get(crossing.toLong()); return c == null ? 0 : c.queue; }
    public static boolean queueAlarm(World w, BlockPos crossing) { RoadData.Crossing c = RoadData.get(w).crossings.get(crossing.toLong()); return c != null && c.alarm; }
    public static String crossingInfo(World w, BlockPos crossing) {
        RoadData.Crossing c = RoadData.get(w).crossings.get(crossing.toLong());
        return c == null ? "no road traffic seen" : c.queue + " queued now, " + c.maxToday + " max today, " + c.delayedToday + " delayed today, longest closure " + c.longestClose / 20 + " s" + (c.alarm ? " - ROAD QUEUE ALARM" : "");
    }

    static long searches() { return searches; }

    // ---- pure geometry (self-tested) ----------------------------------------------------------------------------------
    /**
     * distance from a vehicle front (vx,vz, travelling fx,fz) to the stop line of a signal at (lx,lz) whose lenses face
     * (nx,nz), or -1 when the signal isn't for it (not facing it, already past, or more than `half` blocks to the side)
     */
    static double stopLine(double vx, double vz, double fx, double fz, double lx, double lz, double nx, double nz, double half) {
        if (fx * nx + fz * nz > -0.6) return -1;
        double rx = vx - lx, rz = vz - lz;
        double front = rx * nx + rz * nz;
        if (front < -0.5) return -1;
        if (Math.abs(rx * -nz + rz * nx) > half) return -1;
        return Math.max(0, front);
    }

    /** +1 the road turns right from heading a to heading b, -1 left, 0 within 30 degrees of straight */
    static int turnSide(double ax, double az, double bx, double bz) {
        double la = Math.hypot(ax, az), lb = Math.hypot(bx, bz);
        if (la < 1e-6 || lb < 1e-6) return 0;
        double cos = (ax * bx + az * bz) / (la * lb);
        if (cos > 0.866) return 0;
        return ax * bz - az * bx > 0 ? 1 : -1;
    }

    /** /irextras road selftest: [] = pass */
    static List<String> selfTest() {
        List<String> f = new ArrayList<>(TileTrafficController.selfTest());
        // a signal at 0,0 facing north (lenses toward -z): a car at z = -10 driving south (+z) must stop 10 short
        double d = stopLine(0.5, -10, 0, 1, 0, 0, 0, -1, 6);
        if (Math.abs(d - 10) > 0.01) f.add("stop line: expected 10, got " + d);
        if (stopLine(0.5, 10, 0, -1, 0, 0, 0, -1, 6) >= 0) f.add("stop line applied to traffic going away");
        if (stopLine(0.5, -10, 1, 0, 0, 0, 0, -1, 6) >= 0) f.add("stop line applied to cross traffic");
        if (stopLine(9, -10, 0, 1, 0, 0, 0, -1, 6) >= 0) f.add("stop line applied 9 blocks to the side");
        if (stopLine(0.5, 2, 0, 1, 0, 0, 0, -1, 6) >= 0) f.add("stop line applied after passing it");
        if (turnSide(0, 1, -1, 0) != 1) f.add("south then west must be a right turn");
        if (turnSide(0, 1, 1, 0) != -1) f.add("south then east must be a left turn");
        if (turnSide(0, 1, 0.1, 1) != 0) f.add("a slight bend is not a turn");
        double v = EntityRoadVehicle.stopV(10);
        if (v <= 0 || EntityRoadVehicle.stopV(0) != 0 || EntityRoadVehicle.stopV(2) >= v) f.add("stopping speed curve");
        f.addAll(TramMode.selfTest());
        return f;
    }
}
