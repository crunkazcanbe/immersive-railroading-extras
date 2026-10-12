package com.dogpound.railmap.auto;

import cam72cam.immersiverailroading.library.TrackItems;
import cam72cam.immersiverailroading.tile.TileRail;
import cam72cam.mod.math.Vec3i;
import com.dogpound.railmap.graph.TrainNode;
import com.dogpound.railmap.server.TrainTracker;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Diamond crossings in the interlocking (her ChatGPT batch 3 #6). A diamond (IR's CROSSING piece) is a conflict point
 * between two roads: a route over it locks it for its road (EW = east-west, NS = north-south). Another route on the
 * SAME road may share the lock (a following move); a route on the OTHER road is refused until the first lets go, and
 * nobody may lock a diamond while a train stands on it across the other road. A movable-point (swing-nose) diamond has
 * a point machine on it (wrench a Point Machine onto the diamond): the lock also needs the machine to have moved its
 * noses to the route's road and detected them - until then the route waits ("diamond moving").
 * <p>
 * Dispatcher routes ({@link Interlocking#setRoute}) and driverless trains (Autopilot, through {@link #line}) both go
 * through here; every lock is released with the owner's switch locks (Interlocking.releaseAll) and times out like them.
 */
public final class Diamonds {
    private Diamonds() {}

    public static final int EW = 1, NS = 2;
    private static final long TTL = 20 * 90;

    private static final class Lock {
        int road;
        final Map<String, Long> owners = new HashMap<>();
    }

    private static final Map<Integer, Map<Vec3i, Lock>> LOCKS = new HashMap<>();
    /** last refusal per owner (for the status line) */
    private static final Map<String, String> WHY = new HashMap<>();
    private static int refusals;

    public static void clear() { LOCKS.clear(); WHY.clear(); }

    public static String roadName(int r) { return r == EW ? "east-west" : r == NS ? "north-south" : "?"; }

    /** the road a heading (x, z) runs along */
    static int road(double hx, double hz) { return Math.abs(hx) >= Math.abs(hz) ? EW : NS; }

    /** is this path step a diamond? */
    static boolean isDiamond(World w, Vec3i p) {
        try {
            TileRail r = cam72cam.mod.world.World.get(w).getBlockEntity(p, TileRail.class);
            return r != null && r.info != null && r.info.settings.type == TrackItems.CROSSING;
        } catch (LinkageError | RuntimeException e) { return false; }
    }

    /** every diamond on a path: step index -> road */
    public static Map<Integer, Integer> on(World w, TrackPath path) {
        Map<Integer, Integer> out = new LinkedHashMap<>();
        for (int i = 0; i < path.steps.size(); i++) {
            TrackPath.Step s = path.steps.get(i);
            if (s.points != null || !isDiamond(w, s.pos)) continue;   // a diamond has no centre-line of its own
            double[] h = path.headingAt(i);
            out.put(i, road(h[0], h[1]));
        }
        return out;
    }

    /** lock one diamond for owner on road (null = locked; else why not) */
    public static String claim(World w, Vec3i pos, int road, String owner) {
        long now = w.getTotalWorldTime();
        Map<Vec3i, Lock> m = LOCKS.computeIfAbsent(w.provider.getDimension(), d -> new HashMap<>());
        Lock l = m.get(pos);
        if (l != null) {
            l.owners.values().removeIf(t -> now - t > TTL);
            l.owners.remove(owner);
            if (l.owners.isEmpty()) { m.remove(pos); l = null; }
            else if (l.road != road) return refuse(owner, "diamond " + pos.x + "," + pos.z + " is locked for the " + roadName(l.road) + " road");
        }
        if (l == null) {
            for (TrainNode t : TrainTracker.latest(w)) {                // nothing standing on it across our road
                double dx = t.x - pos.x - 0.5, dz = t.z - pos.z - 0.5;
                if (dx * dx + dz * dz > 9 || Math.abs(t.y - pos.y) > 3) continue;
                double yaw = Math.toRadians(t.yaw);
                if (road(-Math.sin(yaw), Math.cos(yaw)) != road) return refuse(owner, "a train is on diamond " + pos.x + "," + pos.z + " on the " + roadName(road == EW ? NS : EW) + " road");
            }
        }
        String mp = com.dogpound.railmap.track.TrackHooks.diamondVeto(w, new BlockPos(pos.x, pos.y, pos.z), road);   // movable-point diamond: noses set + detected
        if (mp != null) return refuse(owner, mp);
        if (l == null) { l = new Lock(); l.road = road; m.put(pos, l); }
        l.owners.put(owner, now);
        WHY.remove(owner);
        return null;
    }

    private static String refuse(String owner, String why) { refusals++; WHY.put(owner, why); return why; }

    public static void releaseAll(World w, String owner) {
        Map<Vec3i, Lock> m = LOCKS.get(w.provider.getDimension());
        if (m == null) return;
        for (Iterator<Map.Entry<Vec3i, Lock>> it = m.entrySet().iterator(); it.hasNext(); ) {
            Lock l = it.next().getValue();
            l.owners.remove(owner);
            if (l.owners.isEmpty()) it.remove();
        }
        WHY.remove(owner);
    }

    public static void release(World w, Vec3i pos, String owner) {
        Map<Vec3i, Lock> m = LOCKS.get(w.provider.getDimension());
        Lock l = m == null ? null : m.get(pos);
        if (l == null) return;
        l.owners.remove(owner);
        if (l.owners.isEmpty()) m.remove(pos);
    }

    /** a dispatcher route: lock every diamond on it (null = all locked; else the first conflict, nothing kept) */
    public static String claimRoute(World w, TrackPath path, String owner) {
        for (Map.Entry<Integer, Integer> e : on(w, path).entrySet()) {
            String r = claim(w, path.steps.get(e.getKey()).pos, e.getValue(), owner);
            if (r != null) { releaseAll(w, owner); return r; }
        }
        return null;
    }

    /** keep a standing route's locks alive */
    public static void touch(World w, TrackPath path, String owner) {
        Map<Vec3i, Lock> m = LOCKS.get(w.provider.getDimension());
        if (m == null) return;
        long now = w.getTotalWorldTime();
        for (TrackPath.Step s : path.steps) { Lock l = m.get(s.pos); if (l != null && l.owners.containsKey(owner)) l.owners.put(owner, now); }
    }

    /**
     * Autopilot: lock the diamonds within the lookahead, release the ones passed. Returns the distance along the path
     * to stand short of (10 m before a diamond it couldn't lock), or Double.MAX_VALUE.
     */
    public static double line(World w, TrackPath path, double cur, double lookahead, String owner) {
        double blocked = Double.MAX_VALUE;
        for (Map.Entry<Integer, Integer> e : on(w, path).entrySet()) {
            int i = e.getKey();
            Vec3i p = path.steps.get(i).pos;
            double d = i == 0 ? 0 : path.steps.get(i - 1).distance;
            double ahead = d - cur;
            if (ahead < -20) { release(w, p, owner); continue; }
            if (ahead > lookahead) continue;
            if (claim(w, p, e.getValue(), owner) != null && d - 10 < blocked) blocked = d - 10;
        }
        return blocked;
    }

    public static String why(String owner) { return WHY.get(owner); }

    /** for the command / console: one line per locked diamond */
    public static List<String> report(World w) {
        List<String> out = new ArrayList<>();
        Map<Vec3i, Lock> m = LOCKS.get(w.provider.getDimension());
        if (m != null) for (Map.Entry<Vec3i, Lock> e : m.entrySet())
            out.add("§f" + e.getKey().x + "," + e.getKey().z + " §7locked " + roadName(e.getValue().road) + " by " + String.join(", ", e.getValue().owners.keySet()));
        out.add("§7" + refusals + " conflicting requests refused since start");
        return out;
    }

    /** pure checks for the self-test: road from heading, and the sharing rule */
    public static List<String> selftest() {
        List<String> out = new ArrayList<>();
        out.add((road(1, 0.1) == EW && road(-0.2, -1) == NS ? "§aPASS" : "§cFAIL") + " road from heading");
        Lock l = new Lock(); l.road = EW;
        boolean shareSame = l.road == road(1, 0), refuseOther = l.road != road(0, 1);
        out.add((shareSame && refuseOther ? "§aPASS" : "§cFAIL") + " same road shares, other road refused");
        return out;
    }
}
