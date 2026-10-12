package com.dogpound.railmap.auto;

import cam72cam.immersiverailroading.library.SwitchState;
import cam72cam.immersiverailroading.tile.TileRail;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.immersiverailroading.util.SwitchUtil;
import cam72cam.mod.math.Vec3i;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapConfig;
import com.dogpound.railmap.graph.TrainNode;
import com.dogpound.railmap.server.TrainTracker;
import com.dogpound.railmap.signal.SignalRegistry;
import com.dogpound.railmap.signal.TileSignalMast;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The interlocking: the part of a real control point that makes it impossible to line two
 * routes across each other.
 * <p>
 * Every switch a route needs is <b>locked</b> to its owner (a dispatcher route, or a driverless
 * train) in the position that route wants. Anyone else may share a lock that wants the same
 * position — two trains following each other through a straight switch — but nobody may
 * throw it the other way until the lock is released. Locks release themselves once the train
 * that needed them has gone by, and time out if it never comes.
 * <p>
 * Dispatcher <b>routes</b> ("entrance-exit" in real CTC: click the signal you start at, then the
 * signal you want to reach) live here too.
 */
public final class Interlocking {
    /** A lock that nobody has refreshed for this many ticks falls off. */
    private static final long LOCK_TTL = 20 * 90;

    private static final class Lock {
        SwitchState state;
        final Map<String, Long> owners = new HashMap<>();
    }

    /** One lined dispatcher route. */
    public static final class Route {
        public final BlockPos start, end;
        public final TrackPath path;
        public final String owner;
        /** Set once a train has entered: the entrance signal has dropped behind it. */
        public boolean entered;
        public long lastOccupied;

        Route(BlockPos start, BlockPos end, TrackPath path) {
            this.start = start;
            this.end = end;
            this.path = path;
            this.owner = "route@" + start.toLong();
        }
    }

    private static final Map<Integer, Map<Vec3i, Lock>> LOCKS = new HashMap<>();
    private static final Map<Integer, List<Route>> ROUTES = new HashMap<>();

    private Interlocking() {}

    public static void clear() {
        LOCKS.clear();
        ROUTES.clear();
        Diamonds.clear();
    }

    // ---- switch locks --------------------------------------------------------------------

    /**
     * Lock the switch at {@code pos} for {@code owner} in {@code want} and throw it if needed.
     * Returns false if another owner holds it the other way, or a train is standing on it.
     */
    public static boolean claim(World world, Vec3i pos, SwitchState want, String owner) {
        int dim = world.provider.getDimension();
        Map<Vec3i, Lock> locks = LOCKS.computeIfAbsent(dim, d -> new HashMap<>());
        long now = world.getTotalWorldTime();
        Lock lock = locks.get(pos);
        if (lock != null) {
            lock.owners.values().removeIf(t -> now - t > LOCK_TTL);
            lock.owners.remove(owner);
            if (lock.owners.isEmpty()) {
                locks.remove(pos);
                lock = null;
            } else if (lock.state != want) {
                return false;                  // someone else holds it the other way
            }
        }
        cam72cam.mod.world.World umc = cam72cam.mod.world.World.get(world);
        TileRail sw = switchAt(umc, pos);
        if (sw == null) return true;           // switch gone: nothing to hold
        SwitchState cur = safeState(sw);
        if (cur != want) {
            if (occupied(world, pos, 4)) return false;   // never throw points under a train
            if (com.dogpound.railmap.safety.FaultLab.stuck(world, new BlockPos(pos.x, pos.y, pos.z))) return false;   // jammed points (fault lab)
            if (com.dogpound.railmap.track.TrackHooks.throwVeto(world, new BlockPos(pos.x, pos.y, pos.z), want, true) != null) return false;   // #14 point machine refuses / failed
            sw.setSwitchForced(want);
            sw.markDirty();
        }
        if (lock == null) {
            lock = new Lock();
            lock.state = want;
            locks.put(pos, lock);
        }
        lock.owners.put(owner, now);
        return true;
    }

    /** Release every switch {@code owner} holds (a train that finished, a cancelled route). */
    public static void releaseAll(World world, String owner) {
        Diamonds.releaseAll(world, owner);                               // batch-3 #6: diamond locks go with the switch locks
        Map<Vec3i, Lock> locks = LOCKS.get(world.provider.getDimension());
        if (locks == null) return;
        Iterator<Map.Entry<Vec3i, Lock>> it = locks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Vec3i, Lock> e = it.next();
            e.getValue().owners.remove(owner);
            if (e.getValue().owners.isEmpty()) it.remove();
        }
    }

    public static void release(World world, Vec3i pos, String owner) {
        Map<Vec3i, Lock> locks = LOCKS.get(world.provider.getDimension());
        if (locks == null) return;
        Lock l = locks.get(pos);
        if (l == null) return;
        l.owners.remove(owner);
        if (l.owners.isEmpty()) locks.remove(pos);
    }

    /** Hand a switch back to redstone (IR's "auto"), unless something has it locked. */
    public static boolean setAuto(World world, BlockPos pos) {
        if (isLocked(world, pos)) return false;
        TileRail sw = switchAt(cam72cam.mod.world.World.get(world), new Vec3i(pos.getX(), pos.getY(), pos.getZ()));
        if (sw == null) return false;
        sw.setSwitchForced(SwitchState.NONE);
        sw.markDirty();
        return true;
    }

    /** batch-3 #15: who holds the switch at pos locked the other way from `want` (null = free, or locked the same way) */
    public static String lockedAgainst(World world, Vec3i pos, SwitchState want) {
        Map<Vec3i, Lock> locks = LOCKS.get(world.provider.getDimension());
        Lock l = locks == null ? null : locks.get(pos);
        if (l == null || l.state == want) return null;
        long now = world.getTotalWorldTime();
        for (Map.Entry<String, Long> e : l.owners.entrySet()) if (now - e.getValue() <= LOCK_TTL) return e.getKey();
        return null;
    }

    /** True when the board may not throw this switch by hand: something has it locked. */
    public static boolean isLocked(World world, BlockPos pos) {
        Map<Vec3i, Lock> locks = LOCKS.get(world.provider.getDimension());
        if (locks == null || locks.isEmpty()) return false;
        long now = world.getTotalWorldTime();
        for (Map.Entry<Vec3i, Lock> e : locks.entrySet()) {
            Vec3i v = e.getKey();
            if (Math.abs(v.x - pos.getX()) <= 1 && Math.abs(v.y - pos.getY()) <= 1 && Math.abs(v.z - pos.getZ()) <= 1) {
                for (long t : e.getValue().owners.values()) if (now - t <= LOCK_TTL) return true;
            }
        }
        return false;
    }

    // ---- dispatcher routes ---------------------------------------------------------------

    public static List<Route> routes(World world) {
        List<Route> l = ROUTES.get(world.provider.getDimension());
        return l == null ? new ArrayList<>() : new ArrayList<>(l);
    }

    /**
     * Line a route from the signal at {@code start} to the signal at {@code end}: find the way
     * along the track the entrance signal faces, lock and throw every switch, then clear the
     * entrance signal. Returns a line for the dispatcher.
     */
    public static String setRoute(World world, BlockPos start, BlockPos end) {
        TileEntity a = world.getTileEntity(start), b = world.getTileEntity(end);
        if (!(a instanceof TileSignalMast from)) return "Pick a signal to start the route at";
        if (!(b instanceof TileSignalMast to)) return "Pick a signal to end the route at";
        if (start.equals(end)) return "A route needs two different signals";
        for (Route r : routes(world)) if (r.start.equals(start)) return "A route is already set from that signal — cancel it first";

        Vec3i g = from.governedRail(), h = to.governedRail();
        if (g == null) return "The entrance signal is not beside any track";
        if (h == null) return "The exit signal is not beside any track";
        cam72cam.mod.world.World umc = cam72cam.mod.world.World.get(world);
        TileRail rail = umc.getBlockEntity(g, TileRail.class);
        if (rail == null) return "The entrance track is not loaded";
        EnumFacing f = from.facing();
        Vec3i behind = new Vec3i(g.x - f.getXOffset(), g.y, g.z - f.getZOffset());
        TrackPath path = TrackPath.find(umc, rail, behind, RailMapConfig.routeSearchBlocks, TrackPath.piece(h));
        if (path == null) return "No way along the track from that signal to the other one";
        String unsafe = com.dogpound.railmap.safety.RouteCheck.check(world, from, to, path);   // the safety case (#28): why a route is refused
        if (unsafe != null) return unsafe;

        Route route = new Route(start, end, path);
        // Every switch first; if any refuses, undo so a half-lined route never exists.
        for (Map.Entry<Vec3i, SwitchState> e : path.switches.entrySet()) {
            if (!claim(world, e.getKey(), e.getValue(), route.owner)) {
                releaseAll(world, route.owner);
                return "Conflict: a switch on that route is locked or occupied";
            }
        }
        String dia = Diamonds.claimRoute(world, path, route.owner);      // batch-3 #6: diamond crossings on the route
        if (dia != null) {
            releaseAll(world, route.owner);
            return "Conflict: " + dia;
        }
        for (TrainNode t : TrainTracker.latest(world)) {
            if (onRoute(path, t, 1)) {
                releaseAll(world, route.owner);
                return "Route is occupied by a train";
            }
        }
        ROUTES.computeIfAbsent(world.provider.getDimension(), d -> new ArrayList<>()).add(route);
        if (from.mode() != TileSignalMast.Mode.CTC) from.cycleModeTo(TileSignalMast.Mode.CTC);
        from.setRoute(true);
        com.dogpound.railmap.safety.SigHooks.routeSet(world, start, end);          // batch-3 #19 route macro recording
        return "Route set: " + Math.round(path.length) + " m, " + path.switches.size() + " switch(es) locked";
    }

    public static String cancelRoute(World world, BlockPos start) {
        List<Route> l = ROUTES.get(world.provider.getDimension());
        if (l == null) return "No route from that signal";
        for (Iterator<Route> it = l.iterator(); it.hasNext(); ) {
            Route r = it.next();
            if (!r.start.equals(start)) continue;
            it.remove();
            drop(world, r);
            return "Route cancelled";
        }
        return "No route from that signal";
    }

    private static void drop(World world, Route r) {
        releaseAll(world, r.owner);
        if (world.isBlockLoaded(r.start) && world.getTileEntity(r.start) instanceof TileSignalMast m) m.setRoute(false);
    }

    /**
     * Once a second: a train entering the route drops the entrance signal to Stop behind it (one
     * train per route, exactly like the real thing); the locks come off once the route is empty
     * again for a few seconds.
     */
    public static void tick(World world) {
        List<Route> l = ROUTES.get(world.provider.getDimension());
        if (l == null || l.isEmpty()) return;
        long now = world.getTotalWorldTime();
        List<TrainNode> trains = TrainTracker.latest(world);
        for (Iterator<Route> it = l.iterator(); it.hasNext(); ) {
            Route r = it.next();
            boolean any = false;
            for (TrainNode t : trains) if (onRoute(r.path, t, 2.5)) { any = true; break; }
            // Keep the locks alive while the route stands.
            for (Map.Entry<Vec3i, SwitchState> e : r.path.switches.entrySet()) touch(world, e.getKey(), r.owner, now);
            Diamonds.touch(world, r.path, r.owner);
            if (any) {
                r.lastOccupied = now;
                if (!r.entered) {
                    r.entered = true;
                    if (world.isBlockLoaded(r.start) && world.getTileEntity(r.start) instanceof TileSignalMast m) m.setRoute(false);
                }
            } else if (r.entered && now - r.lastOccupied > 60) {
                it.remove();
                drop(world, r);
            }
        }
    }

    private static void touch(World world, Vec3i pos, String owner, long now) {
        Map<Vec3i, Lock> locks = LOCKS.get(world.provider.getDimension());
        if (locks == null) return;
        Lock lock = locks.get(pos);
        if (lock != null && lock.owners.containsKey(owner)) lock.owners.put(owner, now);
    }

    // ---- helpers -------------------------------------------------------------------------

    static boolean onRoute(TrackPath path, TrainNode t, double radius) {
        for (TrackPath.Step s : path.steps) {
            if (s.points == null) {
                double dx = s.pos.x + 0.5 - t.x, dz = s.pos.z + 0.5 - t.z;
                if (dx * dx + dz * dz <= radius * radius + 1) return true;
                continue;
            }
            for (int i = 0; i < s.points.length; i += 3) {
                double dx = s.points[i] - t.x, dz = s.points[i + 2] - t.z;
                if (dx * dx + dz * dz <= radius * radius && Math.abs(s.points[i + 1] - t.y) < 4) return true;
            }
        }
        return false;
    }

    static boolean occupied(World world, Vec3i pos, double radius) {
        for (TrainNode t : TrainTracker.latest(world)) {
            double dx = t.x - pos.x - 0.5, dz = t.z - pos.z - 0.5;
            if (dx * dx + dz * dz <= radius * radius && Math.abs(t.y - pos.y) < 4) return true;
        }
        return false;
    }

    /** what a switch is showing right now (NONE if there is no switch there) */
    public static SwitchState stateAt(World world, BlockPos p) {
        TileRail sw = switchAt(cam72cam.mod.world.World.get(world), new Vec3i(p.getX(), p.getY(), p.getZ()));
        return sw == null ? SwitchState.NONE : safeState(sw);
    }

    static TileRail switchAt(cam72cam.mod.world.World umc, Vec3i pos) {
        if (!umc.isBlockLoaded(pos)) return null;
        TileRailBase base = umc.getBlockEntity(pos, TileRailBase.class);
        if (base == null) return null;
        TileRail t = base instanceof TileRail tr ? tr : base.getParentTile();
        return t == null ? null : TrackPath.switchOf(t);
    }

    static SwitchState safeState(TileRail sw) {
        try {
            return SwitchUtil.getSwitchState(sw);
        } catch (RuntimeException e) {
            RailMap.LOG.debug("[irextras] switch state unreadable at {}", sw.getPos());
            return SwitchState.NONE;
        }
    }
}
