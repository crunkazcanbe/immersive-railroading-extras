package com.dogpound.railmap.auto;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapConfig;
import com.dogpound.railmap.network.PacketCab;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import java.util.*;

/**
 * Moving-block signalling (CBTC), Requested (first item off her big list): instead of one train per fixed block
 * between signals, every train's safe stopping point is worked out live. The controller follows the track ahead of
 * each train to the TAIL of whatever is in front of it (another train, a wreck, the end of the line), keeps a safety
 * margin back from it, and gives the train a Limit of Movement Authority (LMA) there. Its braking curve to that point
 * gives the speed it may do right now. The limit slides along behind the train in front, so trains follow at their
 * braking distance instead of a whole block apart.
 *
 * - Lines can be switched to CBTC (Lines tab, or /irextras cbtc): their driverless trains ignore lineside block
 *   signals and run on their authority (speed signs, switch locks and stations still count).
 * - Every train a player drives is protected by it too (rear-end protection), with a shunting allowance so you can
 *   still creep up and couple.
 * - Riders get a cab display (PacketCab -> client CabDisplay): speed, permitted speed, distance-to-go, why.
 * - The Dispatcher Board map draws every train's authority envelope.
 */
public final class MovingBlock {
    private MovingBlock() {}

    /** One train's authority this moment (distances in metres along its path). */
    public static final class Auth {
        public double head;             // front of the train
        public double eoa;              // end of authority (where it must stand by)
        public double distanceToGo;     // eoa - head
        public double permittedKmh;     // braking curve to the eoa
        public String limitedBy = "";   // "", "train ahead: ...", "end of track", ...
        public double gapM = -1;        // to the obstacle's tail (before the margin), -1 = nothing in sight
        public TrackPath path;
        public long tick;
        public boolean cbtc;            // the train runs on its authority (not lineside signals)
    }

    /** latest authority per locomotive (server) */
    private static final Map<UUID, Auth> LAST = new HashMap<>();

    public static Auth last(UUID loco) { return LAST.get(loco); }

    public static Collection<Map.Entry<UUID, Auth>> all() { return LAST.entrySet(); }

    /** forget trains we haven't worked out for a while (gone, parked, unloaded) */
    static void prune(long now) {
        LAST.values().removeIf(a -> now - a.tick > 100);
    }

    /** is this driverless train's line run on moving block? */
    public static boolean onCbtcLine(RailwayData data, RailwayData.AutoTrain a) {
        if (a == null || a.line == null || a.line.isEmpty()) return false;
        RailwayData.Line l = data.lines().get(a.line);
        return l != null && l.movingBlock;
    }

    /**
     * Work out a train's authority along {@code path}.
     *
     * @param cur       where the lead loco is along the path
     * @param deadEnd   true when the path runs to wherever the track ends (TrackPath.ahead), false when it was cut
     *                  at a destination (a planned route): only a real dead end gets an end-of-track limit
     * @param budget    how far the path was asked to reach
     */
    public static Auth compute(World world, cam72cam.mod.world.World umc, Locomotive loco, TrackPath path, double cur,
                               double speedKmh, double decel, double lag, boolean deadEnd, double budget, long now) {
        Auth a = new Auth();
        a.path = path;
        a.tick = now;

        double me = along(path, loco.getPosition().x, loco.getPosition().y, loco.getPosition().z, 2.5);
        if (me >= 0) cur = me;
        // our own train: every car coupled to this loco
        Set<UUID> mine = new HashSet<>();
        List<EntityCoupleableRollingStock> own = new ArrayList<>();
        if (loco instanceof EntityCoupleableRollingStock) own.addAll(((EntityCoupleableRollingStock) loco).getTrain());
        for (EntityCoupleableRollingStock c : own) mine.add(c.getUUID());
        mine.add(loco.getUUID());

        // the head: the furthest point of our own train along the path (a pushed train's front is a car, not the loco)
        double head = cur;
        for (EntityCoupleableRollingStock c : own) {
            Vec3d p = c.getPosition();
            double d = along(path, p.x, p.y, p.z, 2.5);
            if (d >= 0) head = Math.max(head, d + half(c));
        }
        if (own.isEmpty()) head = cur + half(loco);
        a.head = head;

        // the nearest tail of anything else standing on our path ahead
        double nearest = Double.MAX_VALUE;
        String what = "";
        Vec3d lp = loco.getPosition();
        double reach = path.length + 30;
        for (EntityMoveableRollingStock s : umc.getEntities(EntityMoveableRollingStock.class)) {
            if (s.isDead() || mine.contains(s.getUUID())) continue;
            Vec3d p = s.getPosition();
            double dx = p.x - lp.x, dz = p.z - lp.z;
            if (dx * dx + dz * dz > reach * reach) continue;
            double d = along(path, p.x, p.y, p.z, 2.5);
            if (d < 0) continue;
            double tail = d - half(s);
            if (tail <= head - 1) continue;          // beside or behind our front (a passing loop, our own siding)
            if (tail < nearest) {
                nearest = tail;
                what = s.wreck != 0 ? "a wreck on the line" : "train ahead: " + s.getDefinition().name();
            }
        }

        double margin = RailMapConfig.movingBlockMarginM;
        double eoa = path.length;                     // as far as we can see
        if (nearest < Double.MAX_VALUE) {
            a.gapM = nearest - head;
            eoa = nearest - margin;
            a.limitedBy = what;
        } else if (deadEnd && path.length < budget - 1) {
            eoa = path.length - 3;                    // the track ends: stand short of the buffers
            a.limitedBy = "end of track";
        }
        a.eoa = eoa;
        a.distanceToGo = Math.max(0, eoa - head);
        a.permittedKmh = a.limitedBy.isEmpty() ? Double.MAX_VALUE
                : Wayside.allowed(Math.max(0, a.distanceToGo - lag), 0, decel);
        LAST.put(loco.getUUID(), a);
        return a;
    }

    /**
     * Distance along the path (metres) to the point of the track nearest (x,z): a real projection onto the track's
     * centre-line, segment by segment, so it's exact anywhere along a long straight piece (whose points can be far
     * apart). -1 if the path never passes within {@code radius}.
     */
    public static double along(TrackPath path, double x, double y, double z, double radius) {
        double best = -1, bestOff = radius * radius;
        double before = 0;
        double[] prevEnd = null;
        java.util.List<TrackPath.Step> steps = path.steps;
        for (int si = 0; si < steps.size(); si++) {
            TrackPath.Step st = steps.get(si);
            double[] p = st.points;
            double len = st.distance - before;
            if (p != null && p.length >= 3) {
                int n = p.length / 3;
                // which way do this piece's points run? (they're stored in piece order, which may be against travel)
                boolean rev = false;
                if (prevEnd != null) {
                    rev = sq(p[(n - 1) * 3] - prevEnd[0], p[(n - 1) * 3 + 2] - prevEnd[1]) < sq(p[0] - prevEnd[0], p[2] - prevEnd[1]);
                } else if (si + 1 < steps.size() && steps.get(si + 1).points != null && steps.get(si + 1).points.length >= 3) {
                    double[] q = steps.get(si + 1).points;
                    double qx = q[0], qz = q[2];
                    double qx2 = q[q.length - 3], qz2 = q[q.length - 1];
                    double dFirst = Math.min(sq(p[0] - qx, p[2] - qz), sq(p[0] - qx2, p[2] - qz2));
                    double dLast = Math.min(sq(p[(n - 1) * 3] - qx, p[(n - 1) * 3 + 2] - qz), sq(p[(n - 1) * 3] - qx2, p[(n - 1) * 3 + 2] - qz2));
                    rev = dFirst < dLast;
                }
                // arc length of the polyline, then project onto each segment
                double total = 0;
                double[] cum = new double[n];
                for (int i = 1; i < n; i++) {
                    total += Math.sqrt(sq(p[i * 3] - p[(i - 1) * 3], p[i * 3 + 2] - p[(i - 1) * 3 + 2]));
                    cum[i] = total;
                }
                if (n == 1) {
                    double o = sq(p[0] - x, p[2] - z);
                    if (o <= bestOff && Math.abs(p[1] - y) <= 4) { bestOff = o; best = st.distance; }
                } else {
                    for (int i = 1; i < n; i++) {
                        double ax = p[(i - 1) * 3], az = p[(i - 1) * 3 + 2], bx = p[i * 3], bz = p[i * 3 + 2];
                        double vx = bx - ax, vz = bz - az, l2 = vx * vx + vz * vz;
                        double t = l2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((x - ax) * vx + (z - az) * vz) / l2));
                        double px = ax + vx * t, pz = az + vz * t;
                        double o = sq(px - x, pz - z);
                        if (o <= bestOff && Math.abs(p[i * 3 + 1] - y) <= 4) {
                            double arc = cum[i - 1] + Math.sqrt(l2) * t;
                            double frac = total < 1e-9 ? 1 : arc / total;
                            if (rev) frac = 1 - frac;
                            bestOff = o;
                            best = before + len * frac;
                        }
                    }
                }
                prevEnd = rev ? new double[]{p[0], p[2]} : new double[]{p[(n - 1) * 3], p[(n - 1) * 3 + 2]};
            }
            before = st.distance;
        }
        return best;
    }

    private static double sq(double a, double b) { return a * a + b * b; }

    static double half(EntityMoveableRollingStock s) {
        try {
            return s.getDefinition().getLength(s.gauge) / 2;
        } catch (RuntimeException e) {
            return 6;
        }
    }

    /** the stretch of the path from the head to the end of authority, as x,z polylines (for the board's map) */
    public static List<double[]> envelope(Auth a) {
        List<double[]> out = new ArrayList<>();
        if (a == null || a.path == null) return out;
        double from = a.head, to = Math.min(a.eoa, a.path.length);
        double before = 0;
        for (TrackPath.Step s : a.path.steps) {
            if (s.points != null && s.distance >= from && before <= to) out.add(s.points);
            before = s.distance;
            if (before > to) break;
        }
        return out;
    }

    // ---------------------------------------------------------------------------------- the cab display

    /**
     * Send the driver's display to everyone riding this train.
     *
     * @param mode       what's driving: "CBTC · ATO" (driverless on its authority), "CBTC · ATP" (a player driving,
     *                   protected), "ATO" (driverless on lineside signals)
     * @param targetKmh  the speed the train is aiming for / may do now (the lower of authority and every other limit)
     * @param limitKmh   the line / sign speed limit
     * @param nextStop   next station name ("" for none) and its distance (-1 none)
     */
    public static void sendCab(Locomotive loco, Auth a, String mode, double speedKmh, double targetKmh, double limitKmh,
                               String nextStop, double stopM, String note) {
        List<EntityPlayer> riders = Autopilot.riders(loco);
        if (riders.isEmpty()) return;
        if (com.dogpound.railmap.sim.MultiUnit.isFollower(loco.getUUID())) return;   // MU: only the lead cab's display is sent
        PacketCab p = new PacketCab();
        p.mode = mode;
        p.speed = (float) speedKmh;
        p.target = (float) Math.min(999, Math.max(0, targetKmh));
        p.limit = (float) Math.min(999, limitKmh);
        p.distanceToGo = a == null || a.limitedBy.isEmpty() ? -1 : (float) a.distanceToGo;
        p.gap = a == null ? -1 : (float) a.gapM;
        p.limitedBy = a == null ? "" : a.limitedBy;
        p.nextStop = nextStop == null ? "" : nextStop;
        p.stopM = (float) stopM;
        p.note = note == null ? "" : note;
        com.dogpound.railmap.sim.CabData.fill(loco, p);
        for (EntityPlayer pl : riders) {
            if (pl instanceof EntityPlayerMP) RailMap.NETWORK.sendTo(p, (EntityPlayerMP) pl);
        }
    }
}
