package com.dogpound.railmap.safety;

import cam72cam.immersiverailroading.tile.TileRail;
import cam72cam.mod.math.Vec3i;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.signal.SignalRegistry;
import com.dogpound.railmap.signal.TileSignalMast;
import com.dogpound.railmap.signal.TrackFollower;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;

/**
 * Signal sighting (her ChatGPT list #24): how far back along the track a driver can actually see each signal. The
 * approach is walked back from the signal along the real track (curves and all) and a sight line is cast from a cab
 * eye 2.6 m above the rail to the signal head every 4 m; the first point where trees, terrain, a cutting, a bridge
 * pier or an OLE mast is in the way ends the continuous sighting. Rain, thunder and swamp fog shorten it further.
 * A signal must be sighted for {@code sightSeconds} at {@code sightKmh} (default 7 s at 80 km/h = 155 m); if not:
 * <ul>
 *   <li>a Banner Repeater linked to it, placed further back where it can be seen, counts its own sighting;</li>
 *   <li>otherwise the shortfall is published, and trains approaching that signal while it is not showing clear are
 *   told to brake early - down to caution speed where the signal comes into view (Wayside "poor sighting" limit).</li>
 * </ul>
 * The approach lines double as the train describer's berths: a train on a signal's approach is in its berth.
 */
public final class SignalSighting {
    private SignalSighting() {}

    public static final class Result {
        public long pos, at;
        public String name = "", obstruction = "";
        public double sight, banner = -1;
        /** batch-3 #16: the block in the way (0 = none) and a suggested banner repeater spot {x, y, z, metres from the signal} (null = none needed / none works) */
        public long obstructionPos;
        public double[] bannerAt;
        /** approach centre-line, nearest the signal first: x, y, z, distance from the signal */
        public final List<double[]> approach = new ArrayList<>();
    }

    public static final class Berth {
        public final long pos;
        public final String name;
        public final double dist;
        Berth(long pos, String name, double dist) { this.pos = pos; this.name = name; this.dist = dist; }
    }

    /** how fast a train may approach a signal it can't see yet (km/h) */
    public static final double CAUTION_KMH = 30;

    private static final Map<Integer, Map<Long, Result>> RES = new HashMap<>();
    private static final Map<Integer, Integer> CURSOR = new HashMap<>();

    public static void clear() { RES.clear(); CURSOR.clear(); }

    public static Map<Long, Result> all(World w) { return RES.getOrDefault(w.provider.getDimension(), Collections.emptyMap()); }

    public static Result get(World w, BlockPos mast) { return all(w).get(mast.toLong()); }

    public static void store(World w, Result r) { RES.computeIfAbsent(w.provider.getDimension(), k -> new HashMap<>()).put(r.pos, r); }

    /** the line speed x reaction time a signal must be in view for (m) */
    public static double required(World w) { SafetyData d = SafetyData.get(w); return d.sightKmh / 3.6 * d.sightSeconds; }

    /** batch-3 #16: the line speed this signal is approached at (its own override, else the railway's) */
    public static int kmhAt(World w, BlockPos mast) { SafetyData d = SafetyData.get(w); Integer k = d.sightKmhAt.get(mast.toLong()); return k == null ? d.sightKmh : k; }

    /** the sighting this signal needs: its line speed x the reaction time (m) */
    public static double requiredAt(World w, BlockPos mast) { return kmhAt(w, mast) / 3.6 * SafetyData.get(w).sightSeconds; }

    /** what weather and fog leave of the view, 0..1 */
    public static double visibility(World w, BlockPos at) {
        double wx = com.dogpound.railmap.weather.Weather.visibilityFactor(w, at);   // #66: the railway weather's fog / rain / snow
        if (!Double.isNaN(wx)) return wx;
        double f = w.isThundering() ? 0.45 : w.isRaining() ? 0.65 : 1.0;
        String b = w.getBiome(at).getBiomeName().toLowerCase(Locale.ROOT);
        if (b.contains("swamp") || b.contains("roofed") || b.contains("marsh") || b.contains("bog")) f *= 0.7;
        return f;
    }

    /** sighting now, with today's weather (m) */
    public static double sightNow(World w, Result r) {
        double cap = 600 * visibility(w, BlockPos.fromLong(r.pos));
        double s = Math.min(r.sight, cap);
        if (r.banner > 0) s = Math.max(s, Math.min(r.banner, cap));
        return s;
    }

    /** how many metres short of the required sighting a signal is now (0 = fine, also when not yet surveyed) */
    public static double deficit(World w, BlockPos mast) {
        Result r = get(w, mast);
        return r == null ? 0 : Math.max(0, requiredAt(w, mast) - sightNow(w, r));
    }

    /** once a second: survey a couple of signals (each signal about every 30 s) */
    public static void tick(World w) {
        int dim = w.provider.getDimension();
        List<TileSignalMast> masts = SignalRegistry.masts(dim);
        Map<Long, Result> res = RES.computeIfAbsent(dim, k -> new HashMap<>());
        if (masts.isEmpty()) { res.clear(); return; }
        Set<Long> live = new HashSet<>();
        for (TileSignalMast m : masts) live.add(m.getPos().toLong());
        res.keySet().retainAll(live);
        int c = CURSOR.getOrDefault(dim, 0), n = Math.min(masts.size(), Math.max(2, masts.size() / 30));
        long now = w.getTotalWorldTime();
        for (int i = 0; i < n; i++) {
            TileSignalMast m = masts.get((c + i) % masts.size());
            Result old = res.get(m.getPos().toLong());
            if (old != null && now - old.at < 600 || m.isInvalid()) continue;
            try { res.put(m.getPos().toLong(), survey(w, m)); }
            catch (LinkageError | RuntimeException ex) { RailMap.LOG.debug("[irextras] sighting survey failed at {}: {}", m.getPos(), ex.toString()); }
        }
        CURSOR.put(dim, (c + n) % Math.max(1, masts.size()));
    }

    /** re-survey one signal now (command / lab) */
    public static Result survey(World w, TileSignalMast m) {
        Result r = new Result();
        r.pos = m.getPos().toLong();
        r.at = w.getTotalWorldTime();
        r.name = name(m);
        Vec3i g = m.governedRail();
        if (g == null) { r.obstruction = "not beside any track"; return r; }
        cam72cam.mod.world.World umc = cam72cam.mod.world.World.get(w);
        TileRail start = umc.getBlockEntity(g, TileRail.class);
        if (start == null) { r.obstruction = "track not loaded"; return r; }
        EnumFacing f = m.facing();
        double req = requiredAt(w, m.getPos());
        double need = Math.min(400, req + 80);
        // the protected block is walked leaving (rail - facing) behind (SignalEngine); the approach is the other way
        TrackFollower tf = new TrackFollower(umc, need);
        TrackFollower.Span span = tf.follow(start, new Vec3i(g.x + f.getXOffset(), g.y, g.z + f.getZOffset()));
        double sx = m.getPos().getX() + 0.5, sz = m.getPos().getZ() + 0.5;
        double[] last = {g.x + 0.5, g.y, g.z + 0.5};
        double dist = 0;
        while (span != null && dist < need) {
            for (double[] piece : span.points) {
                int n = piece.length / 3;
                if (n == 0) continue;
                boolean rev = Geo.d2(piece[0], piece[2], last[0], last[2]) > Geo.d2(piece[(n - 1) * 3], piece[(n - 1) * 3 + 2], last[0], last[2]);
                for (int k = 0; k < n; k++) {
                    int j = rev ? n - 1 - k : k;
                    double x = piece[j * 3], y = piece[j * 3 + 1], z = piece[j * 3 + 2];
                    double step = Math.sqrt(Geo.d2(x, z, last[0], last[2]));
                    if (step > 6) continue;                                        // a stray point from the other end of a long piece
                    dist += step;
                    last = new double[]{x, y, z};
                    if (r.approach.isEmpty() || dist - r.approach.get(r.approach.size() - 1)[3] >= 2) r.approach.add(new double[]{x, y, z, dist});
                }
            }
            if (dist >= need) break;
            TrackFollower.Span next = tf.continueFrom(span);
            if (next == null || next.length < 0.5) break;
            span = next;
        }
        // continuous sighting from the signal outward
        double headY = m.getPos().getY() + 2.4 * Math.max(0.5, m.scale());
        Vec3d head = new Vec3d(sx, headY, sz);
        r.sight = 0;
        double lastChecked = 0;
        for (double[] p : r.approach) {
            if (p[3] - lastChecked < 4 && p != r.approach.get(r.approach.size() - 1)) continue;
            lastChecked = p[3];
            String blocked = blocked(w, new Vec3d(p[0], p[1] + 2.6, p[2]), head, m.getPos());
            if (blocked != null) { r.obstruction = blocked + " at " + Math.round(p[3]) + " m"; r.obstructionPos = lastHit == null ? 0 : lastHit.toLong(); break; }
            r.sight = p[3];
        }
        if (r.obstruction.isEmpty()) r.obstruction = r.approach.isEmpty() ? "no approach track" : r.sight >= need - 5 ? "clear view" : "end of track / survey limit";
        // a banner repeater for this signal, further back: the driver sees the banner, then the signal
        for (TileBannerRepeater b : SafetyTicker.banners(w)) {
            if (b.signal() != r.pos || !b.powered()) continue;
            double bd = -1;
            for (double[] p : r.approach) if (Geo.d2(p[0], p[2], b.getPos().getX() + 0.5, b.getPos().getZ() + 0.5) < 5 * 5) { bd = p[3]; break; }
            if (bd < 0) continue;
            Vec3d bh = new Vec3d(b.getPos().getX() + 0.5, b.getPos().getY() + 1.6, b.getPos().getZ() + 0.5);
            double bs = bd;
            for (double[] p : r.approach) {
                if (p[3] <= bd) continue;
                if (blocked(w, new Vec3d(p[0], p[1] + 2.6, p[2]), bh, b.getPos()) != null) break;
                bs = p[3];
            }
            if (bs > r.banner) r.banner = bs;
        }
        if (Math.max(r.sight, r.banner) < req && !r.approach.isEmpty()) r.bannerAt = suggestBanner(w, r, req);
        return r;
    }

    /**
     * batch-3 #16: where a banner repeater would fix the shortfall - an approach point (2.5 m to the side of the track,
     * banner head 1.6 m up) whose own view along the approach reaches the required distance, tested with the same sight
     * line as the survey. Nearest the signal first (fewest metres of cable). null = no spot on the surveyed approach works.
     */
    static double[] suggestBanner(World w, Result r, double req) {
        int tries = 0;
        for (int i = 0; i + 1 < r.approach.size() && tries < 14; i++) {
            double[] p = r.approach.get(i), q = r.approach.get(i + 1);
            if (p[3] < 15 || (int) p[3] / 10 == (int) q[3] / 10) continue;             // a candidate every ~10 m from 15 m out
            if (p[3] > req) break;
            tries++;
            double dx = q[0] - p[0], dz = q[2] - p[2], l = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
            double bx = p[0] - dz / l * 2.5, bz = p[2] + dx / l * 2.5;
            BlockPos bp = new BlockPos(bx, p[1], bz);
            Vec3d head = new Vec3d(bx, p[1] + 1.6, bz);
            double bs = p[3];
            for (double[] a : r.approach) {
                if (a[3] <= p[3]) continue;
                if (blocked(w, new Vec3d(a[0], a[1] + 2.6, a[2]), head, bp) != null) break;
                bs = a[3];
            }
            if (bs >= req) return new double[]{bx, p[1], bz, p[3]};
        }
        return null;
    }

    /** the block the last blocked() call hit (server thread only) - ponytail: a static slot, return a hit record if surveys go multi-threaded */
    private static BlockPos lastHit;

    /** what blocks the line of sight, or null when the signal head is in view */
    static String blocked(World w, Vec3d eye, Vec3d head, BlockPos target) {
        RayTraceResult hit = w.rayTraceBlocks(eye, head, false, true, false);
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) return null;
        BlockPos h = hit.getBlockPos();
        lastHit = h;
        if (Math.abs(h.getX() - target.getX()) <= 0 && Math.abs(h.getZ() - target.getZ()) <= 0) return null;   // the mast itself
        if (hit.hitVec != null && hit.hitVec.squareDistanceTo(head) < 1.5 * 1.5) return null;                // at the head
        IBlockState s = w.getBlockState(h);
        TileEntity te = w.getTileEntity(h);
        if (te instanceof TileSignalMast) return null;                                                       // another signal's post: thin
        String n = s.getBlock().getLocalizedName();
        return n.toLowerCase(Locale.ROOT).contains("leaves") ? "trees (" + n + ")" : n;
    }

    public static String name(TileSignalMast m) {
        String n = m.settings().text("plate").trim();
        return n.isEmpty() ? m.getPos().getX() + "," + m.getPos().getZ() : n;
    }

    /** the berth a train standing at x, y, z occupies: the signal whose approach it is on, nearest that signal */
    public static Berth berthOf(World w, double x, double y, double z) {
        Berth best = null;
        for (Result r : all(w).values()) {
            for (double[] p : r.approach) {
                if (best != null && p[3] >= best.dist) break;
                if (Geo.d2(p[0], p[2], x, z) <= 3.0 * 3.0 && Math.abs(p[1] - y) < 4) { best = new Berth(r.pos, r.name, p[3]); break; }
            }
        }
        return best;
    }
}
