package com.dogpound.railmap.road;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import com.dogpound.railmap.auto.TrackPath;
import com.dogpound.railmap.auto.Wayside;
import com.dogpound.railmap.ems.RoadPath;
import com.dogpound.railmap.ohle.Ohle;
import com.dogpound.railmap.signal.BlockCatenary;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Tram mode for the driverless-train autopilot (batch 5 #4). A tram is IR stock tagged TRAM: its IR name tag, its
 * definition name or id contains one of RoadConfig.tramNames, or /irextras tram tag set it by hand (untag overrides).
 * For a tram the autopilot also:
 * <ul>
 * <li>drives on line of sight: never faster than it can stop within what the driver sees (tramSightM), and stops for
 *     road vehicles, people and animals on the track ahead;</li>
 * <li>keeps to the street limit on street track (tram road or road blocks beside the rails) and the tram limit elsewhere;</li>
 * <li>obeys tram signals (white bars): stops at STOP and PREPARE;</li>
 * <li>stops at every tram stop on its way that is not one of its line's stops (ALL STOP stops, or REQUEST stops someone is
 *     waiting at), stands tramDwell seconds, then goes on;</li>
 * <li>stops short of a dead, earthed or faulted overhead line section (ohle sections).</li>
 * </ul>
 * Autopilot.drive calls limit() each control cycle; heavy look-ups are cached for half a second per tram.
 */
public final class TramMode {
    private TramMode() {}

    private static final class St {
        long scanAt = -1_000_000L, dwellUntil = -1;
        long dwellStop = Long.MIN_VALUE;
        double obstacle = -1, dead = -1;
        String obstacleWhy = "", deadWhy = "";
        boolean street;
        final Map<Long, Long> served = new HashMap<>();
        String last = "";
        double lastKmh = -1;
    }

    private static final Map<UUID, St> ST = new HashMap<>();

    public static boolean isTram(String name, String tag) {
        String n = ((name == null ? "" : name) + " " + (tag == null ? "" : tag)).toLowerCase(Locale.ROOT);
        for (String k : RoadConfig.tramNames) if (!k.isEmpty() && n.contains(k.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    public static boolean isTram(World w, EntityRollingStock s) {
        RoadData d = RoadData.get(w);
        if (d.tramOff.contains(s.getUUID())) return false;
        if (d.tramOn.contains(s.getUUID())) return true;
        return isTram((s.getDefinition() == null ? "" : s.getDefinition().name()) + " " + s.getDefinitionID(), s.tag);
    }

    /** what the console / PIS show for a tram: the reason its speed is held, "" = running free */
    public static String note(UUID loco) { St s = ST.get(loco); return s == null ? "" : s.last; }
    public static double cap(UUID loco) { St s = ST.get(loco); return s == null ? -1 : s.lastKmh; }
    public static boolean dwellingAt(UUID loco, BlockPos stop, long now) { St s = ST.get(loco); return s != null && s.dwellStop == stop.toLong() && now < s.dwellUntil; }

    /**
     * the tram's speed cap now {kmh, why}, or null (not a tram / tram mode off). stopPos = the autopilot's own next
     * stop (its line stop is left to the autopilot).
     */
    public static Object[] limit(World w, Locomotive loco, TrackPath path, double cur, double speed, double decel, double lag, BlockPos stopPos, long now) {
        if (!RoadConfig.tramMode || path == null || !isTram(w, loco)) return null;
        St s = ST.computeIfAbsent(loco.getUUID(), k -> new St());
        cam72cam.mod.math.Vec3d p = loco.getPosition();
        if (now - s.scanAt >= 10) { s.scanAt = now; scan(w, s, path, cur); }
        double target = s.street ? RoadConfig.tramStreetKmh : RoadConfig.tramMaxKmh;
        String why = s.street ? "street running " + RoadConfig.tramStreetKmh + " km/h" : null;
        // line of sight: stop within what the driver can see, and for whatever is on the track
        double free = RoadConfig.tramSightM;
        String fw = "line of sight";
        if (s.obstacle >= 0 && s.obstacle - cur < free) { free = s.obstacle - cur - 4; fw = s.obstacleWhy; }
        double a = Wayside.allowed(free - lag, 0, decel);
        if (a < target) { target = a; why = fw; }
        // dead overhead line ahead
        if (s.dead >= 0) { a = Wayside.allowed(s.dead - cur - 10 - lag, 0, decel); if (a < target) { target = a; why = s.deadWhy; } }
        // tram signals
        for (TileTramSignal g : TileTramSignal.all(w)) {
            BlockPos gp = g.getPos();
            if (!g.stopForTram() || gp.distanceSq(p.x, p.y, p.z) > 200 * 200) continue;
            EnumFacing f = g.facing();
            if ((p.x - gp.getX() - 0.5) * f.getXOffset() + (p.z - gp.getZ() - 0.5) * f.getZOffset() < 0) continue;   // behind it: not ours
            double d = path.distanceTo(gp.getX() + 0.5, gp.getY(), gp.getZ() + 0.5, 4.5);
            if (d < 0 || d - cur < 0.5) continue;
            a = Wayside.allowed(d - cur - 3 - lag, 0, decel);
            if (a < target) { target = a; why = "tram signal " + g.aspectName(); }
        }
        // tram stops that are not the line's own next stop
        for (TileTramStop t : TileTramStop.all(w)) {
            BlockPos tp = t.getPos();
            long key = tp.toLong();
            if (tp.distanceSq(p.x, p.y, p.z) > 250 * 250) { s.served.remove(key); continue; }
            if (stopPos != null && tp.distanceSq(stopPos) < 12 * 12) continue;
            double d = path.distanceTo(tp.getX() + 0.5, tp.getY(), tp.getZ() + 0.5, 5.5);
            if (d < 0) { if (tp.distanceSq(p.x, p.y, p.z) > 40 * 40) s.served.remove(key); continue; }
            double ahead = d - cur;
            if (s.dwellStop == key) {
                if (now < s.dwellUntil) { target = 0; why = "at tram stop " + t.stopName() + " " + Math.max(0, (s.dwellUntil - now) / 20) + " s"; continue; }
                s.dwellStop = Long.MIN_VALUE; s.served.put(key, now); t.departed(loco.getUUID());
                continue;
            }
            if (s.served.containsKey(key) || ahead < -3 || !t.wantsTram()) continue;
            if (ahead <= 2.5 && speed < 2.5) {                                    // arrived: open up and stand
                s.dwellStop = key; s.dwellUntil = now + RoadConfig.tramDwell * 20L; t.arrived(loco.getUUID());
                target = 0; why = "at tram stop " + t.stopName();
                continue;
            }
            a = Wayside.allowed(ahead - 1 - lag, 0, decel);
            if (a < target) { target = a; why = "stopping at " + t.stopName(); }
        }
        s.last = why == null ? "" : why;
        s.lastKmh = target;
        return new Object[]{target, why == null ? "tram" : "tram: " + why};
    }

    /** the slow look-ups along the path: what is on the track, street running, dead OHLE */
    private static void scan(World w, St s, TrackPath path, double cur) {
        s.obstacle = -1; s.dead = -1; s.street = false;
        double sight = cur + RoadConfig.tramSightM + 10, deadReach = cur + 160;
        double before = 0;
        double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE, minY = 999, maxY = -999;
        List<double[]> pts = new ArrayList<>();
        for (TrackPath.Step st : path.steps) {
            if (st.points != null && st.distance >= cur - 2) {
                for (int i = 0; i < st.points.length; i += 3) {
                    double frac = st.points.length <= 3 ? 1 : (double) i / (st.points.length - 3);
                    double pd = before + (st.distance - before) * frac;
                    if (pd < cur + 3 || pd > deadReach) continue;
                    double x = st.points[i], y = st.points[i + 1], z = st.points[i + 2];
                    pts.add(new double[]{x, y, z, pd});
                    if (pd <= sight) { minX = Math.min(minX, x); maxX = Math.max(maxX, x); minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z); minY = Math.min(minY, y); maxY = Math.max(maxY, y); }
                }
            }
            before = st.distance;
            if (before > deadReach) break;
        }
        pts.sort(Comparator.comparingDouble(q -> q[3]));
        if (minX <= maxX) {
            List<Entity> es = w.getEntitiesWithinAABB(Entity.class, new AxisAlignedBB(minX - 2, minY - 1, minZ - 2, maxX + 2, maxY + 3, maxZ + 2),
                    e -> !e.isRiding() && (e instanceof EntityRoadVehicle || e instanceof EntityLivingBase && !(e instanceof IMob) && !(e instanceof com.dogpound.railmap.pax.EntityPax)));
            outer:
            for (double[] q : pts) {
                if (q[3] > sight) break;
                for (Entity e : es) {
                    double dx = e.posX - q[0], dz = e.posZ - q[2];
                    if (dx * dx + dz * dz < (e instanceof EntityRoadVehicle ? 2.0 : 1.2) * (e instanceof EntityRoadVehicle ? 2.0 : 1.2) && Math.abs(e.posY - q[1]) < 1.5) {
                        s.obstacle = q[3];
                        s.obstacleWhy = (e instanceof EntityRoadVehicle v ? v.kind().label.toLowerCase(Locale.ROOT) : e.getName()) + " on the track";
                        break outer;
                    }
                }
            }
        }
        // street running: road surface beside / under the rails just ahead
        int n = 0;
        for (double[] q : pts) {
            if (q[3] > cur + 30) break;
            if (n++ % 3 != 0) continue;
            BlockPos b = new BlockPos(q[0], q[1], q[2]);
            for (int dy = -1; dy <= 0 && !s.street; dy++)
                for (int dx = -2; dx <= 2 && !s.street; dx += 2)
                    for (int dz = -2; dz <= 2 && !s.street; dz += 2)
                        if (RoadPath.isRoad(w.getBlockState(b.add(dx, dy, dz)))) s.street = true;
        }
        if (!RoadConfig.tramDeadSection) return;
        double lastSample = -100;
        for (double[] q : pts) {
            if (q[3] - lastSample < 8) continue;
            lastSample = q[3];
            BlockPos b = new BlockPos(q[0], q[1], q[2]);
            Ohle.Section sec = null;
            search:
            for (int dy = 3; dy <= 7; dy++)
                for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos c = b.add(dx, dy, dz);
                        if (!w.isBlockLoaded(c) || !(w.getBlockState(c).getBlock() instanceof BlockCatenary k) || k.kind() != BlockCatenary.Kind.WIRE) continue;
                        sec = Ohle.section(w, c);
                        break search;
                    }
            if (sec == null) continue;
            if (sec.faulted || sec.earthed || !sec.legacy() && !sec.energised) {
                s.dead = q[3];
                s.deadWhy = "OHLE " + sec.name() + " " + sec.state() + " ahead";
                return;
            }
        }
    }

    /** /irextras tram list + the console: every tram loaded, [label, line, next stop, speed, cap, note] */
    public static List<String[]> trams(World w) {
        List<String[]> out = new ArrayList<>();
        com.dogpound.railmap.auto.RailwayData rd = com.dogpound.railmap.auto.RailwayData.get(w);
        for (Locomotive l : cam72cam.mod.world.World.get(w).getEntities(Locomotive.class)) {
            if (!isTram(w, l)) continue;
            com.dogpound.railmap.auto.RailwayData.AutoTrain a = rd.train(l.getUUID());
            com.dogpound.railmap.auto.Autopilot.Run r = com.dogpound.railmap.auto.Autopilot.run(l.getUUID());
            double kmh = l.getCurrentSpeed() == null ? 0 : Math.abs(l.getCurrentSpeed().metric());
            double cap = cap(l.getUUID());
            out.add(new String[]{l.getUUID().toString(), a != null ? com.dogpound.railmap.auto.Autopilot.label(a, l) : l.tag != null && !l.tag.isEmpty() ? l.tag : l.getDefinition() == null ? "Tram" : l.getDefinition().name(), a == null ? ".manual" : a.line.isEmpty() ? "-" : a.line,
                    r == null || r.nextStopName.isEmpty() ? "-" : r.nextStopName, String.format(Locale.ROOT, "%.0f", kmh), cap < 0 ? "-" : String.format(Locale.ROOT, "%.0f", Math.min(cap, 999)),
                    note(l.getUUID()).isEmpty() ? (a == null ? ".no autopilot" : "+running") : "~" + note(l.getUUID())});
        }
        return out;
    }

    /** self-test of the pure parts */
    static List<String> selfTest() {
        List<String> f = new ArrayList<>();
        if (!isTram("Siemens Combino", null) || !isTram("x", "TRAM 7") || isTram("EMD SD40", "freight")) f.add("tram name match");
        double v = Wayside.allowed(90, 0, 1.0);
        if (v < 46 || v > 50) f.add("line of sight speed for 90 m at 1 m/s2 should be ~48 km/h, is " + v);
        return f;
    }
}
