package com.dogpound.railmap.track;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.tile.TileRail;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.MovingBlock;
import com.dogpound.railmap.auto.TrackPath;
import com.dogpound.railmap.signal.TileSignalMast;
import com.dogpound.railmap.signal.TrackFollower;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;

import java.util.*;

/**
 * Single-line / gauntlet section controller (her ChatGPT batch 3 #7): a stretch of track - one line worked in both
 * directions, or a gauntlet where two tracks interlace through a narrow place (a bridge, a tunnel, a street) - that
 * only one direction may use at a time. Like tokenless block / an interlocked direction lever:
 * <ul>
 *   <li>the two ENDS are wrench-linked rails; the controller walks the track between them (the section) and watches
 *       every vehicle on it (GAUNTLET mode watches 3.5 m either side, so the interlaced track counts too);</li>
 *   <li><b>directional occupancy</b>: which way the section is worked (A&gt;B / B&gt;A / none), how many trains are in it,
 *       which end each came in by - a train entering against the direction is a CONFLICT (MAJOR alarm);</li>
 *   <li><b>entry reservation</b>: a train standing at or running towards an end asks for the section; requests queue
 *       first come, first served; once the section is empty (3 s) the next request gets the direction. Unused grants
 *       lapse after 60 s. FOLLOW lets further trains from the same end follow in, ONE TRAIN keeps it to one at a time;</li>
 *   <li>the entry signals (wrench-linked, two per end) are held at danger unless their end has the direction; with
 *       config singleLineHoldsAutopilot driverless trains also stand 8 m short of an end they may not enter;</li>
 *   <li>MODE: AUTO, FIXED A&gt;B, FIXED B&gt;A, CLOSED (both ends held: engineering work).</li>
 * </ul>
 * Needs 12 V (fail safe: no 12 V = both ends held).
 */
public class TileSingleLine extends TrackGear {
    public static final String[] MODES = {"AUTO", "FIXED A>B", "FIXED B>A", "CLOSED"};
    static final int NONE = 0, AB = 1, BA = 2;
    static final int GRANT_LAPSE_S = 60, CLEAR_S = 3, REQUEST_M = 150;

    private long endA, endB;                         // TileRail piece positions (0 = unlinked)
    private final long[] sigA = new long[2], sigB = new long[2];
    private int dir;                                  // NONE / AB / BA
    private long grantAt, clearSince = -1;
    private int entries, refusals, conflicts;
    private double lengthM;
    /** trains in the section: train key -> end they came in by (1 A, 2 B) */
    private final Map<String, Integer> inside = new LinkedHashMap<>();
    /** requests: train key -> {end, first seen tick} */
    private final Map<String, long[]> requests = new LinkedHashMap<>();
    private final Map<String, String> names = new HashMap<>();
    private final boolean[] occ = new boolean[5];
    // not saved
    private TrackPath path;
    private long pathAt = -1_000_000;

    public String id() { return "SL " + Math.floorMod(pos.getX() * 11 + pos.getZ() * 23, 1000); }
    @Override public String wiredName() { return (gauntlet() ? "Gauntlet section " : "Single-line section ") + id(); }
    private int mode() { return Math.max(0, Math.min(3, ctl.get("s_mode"))); }
    private boolean follow() { return ctl.get("s_follow") == 1; }
    private boolean gauntlet() { return ctl.get("s_kind") == 1; }
    private double lateral() { return gauntlet() ? 3.5 : 2.5; }

    static String dirName(int d) { return d == AB ? "A>B" : d == BA ? "B>A" : "NONE"; }

    @Override public String gearState() {
        if (endA == 0 || endB == 0) return "ends not linked (Signal Wrench: pick a rail, click END A / END B)";
        if (!powered()) return "NO 12 V - both ends held";
        return MODES[mode()] + " · direction " + dirName(dir) + " · " + inside.size() + " train(s) in · queue A " + queue(1) + " B " + queue(2);
    }
    @Override public int wiredLamp() { return !powered() || conflicts > 0 && ctl.get("cfl") == 1 ? 3 : dir == NONE ? 1 : 2; }
    @Override public void gearInfo(Map<String, Object> m) { m.put("direction", dirName(dir)); m.put("inside", inside.size()); m.put("entries", entries); m.put("refusals", refusals); m.put("conflicts", conflicts); m.put("lengthM", Math.round(lengthM)); }

    private int queue(int end) { int n = 0; for (long[] r : requests.values()) if (r[0] == end) n++; return n; }

    // ---- links --------------------------------------------------------------------------------------------------------

    @Override
    public String link(String control, BlockPos pick, EntityPlayer who) {
        switch (control) {
            case "end_a": case "end_b": {
                TileRail r = Rails.pieceAt(world, pick);
                if (r == null) return "§cPick a rail of the section's end with the Signal Wrench";
                long k = Rails.pos(r).toLong();
                if (control.equals("end_a")) endA = k; else endB = k;
                path = null; pathAt = -1_000_000;
                markDirty();
                return "§aEnd " + (control.equals("end_a") ? "A" : "B") + " = " + Rails.xz(Rails.pos(r)) + (endA != 0 && endB != 0 ? " - section set, walking it..." : "");
            }
            case "sig_a": case "sig_b": {
                if (!(world.getTileEntity(pick) instanceof TileSignalMast)) return "§cPick the entry signal (a signal mast) with the Signal Wrench";
                long[] s = control.equals("sig_a") ? sigA : sigB;
                long k = pick.toLong();
                if (s[0] == k || s[1] == k) { if (s[0] == k) s[0] = 0; if (s[1] == k) s[1] = 0; markDirty(); return "§7Signal unlinked"; }
                if (s[0] == 0) s[0] = k; else s[1] = k;
                markDirty();
                return "§aEntry signal at end " + (control.equals("sig_a") ? "A" : "B") + " linked (held unless that end has the direction)";
            }
            default: return null;
        }
    }

    private TrackPath path() {
        if (endA == 0 || endB == 0) return null;
        long now = world.getTotalWorldTime();
        if (path != null && now - pathAt < 1200) return path;
        if (path == null && now - pathAt < 200) return null;
        pathAt = now;
        path = null;
        try {
            cam72cam.mod.world.World umc = cam72cam.mod.world.World.get(world);
            BlockPos a = BlockPos.fromLong(endA), b = BlockPos.fromLong(endB);
            TileRail ra = Rails.pieceAt(world, a);
            if (ra == null) return null;
            double[] wp = TrackFollower.worldPath(ra);
            Vec3i bv = new Vec3i(b.getX(), b.getY(), b.getZ());
            List<Vec3i> starts = new ArrayList<>();
            starts.add(null);
            if (wp != null && wp.length >= 6) { starts.add(new Vec3i(Math.floor(wp[0]), Math.floor(wp[1]), Math.floor(wp[2]))); starts.add(new Vec3i(Math.floor(wp[wp.length - 3]), Math.floor(wp[wp.length - 2]), Math.floor(wp[wp.length - 1]))); }
            for (Vec3i from : starts) {
                TrackPath p = TrackPath.find(umc, ra, from, 3000, TrackPath.piece(bv));
                if (p != null && (path == null || p.length < path.length)) path = p;
            }
        } catch (LinkageError | RuntimeException e) { path = null; }
        lengthM = path == null ? 0 : path.length;
        return path;
    }

    // ---- the tick ------------------------------------------------------------------------------------------------------

    @Override
    protected void tickGear() {
        if (age % 20 != 0) return;
        long now = world.getTotalWorldTime();
        TrackPath p = path();
        Arrays.fill(occ, false);
        if (p == null || p.steps.isEmpty()) { inside.clear(); requests.clear(); putLive(now); return; }
        Vec3d pa = endPoint(p, true), pb = endPoint(p, false);
        Set<String> seen = new HashSet<>();
        Map<String, Integer> firstSide = new HashMap<>();
        try {
            for (EntityMoveableRollingStock s : cam72cam.mod.world.World.get(world).getEntities(EntityMoveableRollingStock.class)) {
                if (s.isDead()) continue;
                Vec3d q = s.getPosition();
                if (Math.min(d2(q, pa), d2(q, pb)) > (lengthM + REQUEST_M) * (lengthM + REQUEST_M) && !near(q, p)) continue;
                String key = trainKey(s);
                double al = MovingBlock.along(p, q.x, q.y, q.z, lateral());
                if (al >= 0) {
                    seen.add(key);
                    occ[Math.max(0, Math.min(4, (int) (al / Math.max(1, lengthM) * 5)))] = true;
                    firstSide.merge(key, al < lengthM / 2 ? 1 : 2, (x, y) -> x);
                    names.put(key, Rails.name(s));
                } else if (s instanceof Locomotive l) request(l, key, q, pa, pb, now);
            }
        } catch (LinkageError | RuntimeException ignored) { }
        // entries and exits
        for (String k : seen) {
            if (inside.containsKey(k)) continue;
            int side = firstSide.getOrDefault(k, 1);
            inside.put(k, side);
            requests.remove(k);
            entries++;
            int against = side == 1 ? BA : AB;
            if (dir == against || mode() == 3 || !powered()) {                  // past a held entry signal
                conflicts++;
                ctl.set("cfl", 1);
                String txt = "SINGLE LINE " + id() + ": " + names.getOrDefault(k, "a train") + " entered from end " + (side == 1 ? "A" : "B") + " AGAINST the direction (" + dirName(dir) + ")";
                Recorder.event(world, txt);
                AlarmManager.get(world).raise(world, "slconf:" + pos.toLong(), AlarmManager.Cat.SIGNAL, AlarmManager.Sev.MAJOR, txt, pos, true);
            } else if (dir == NONE) { dir = side == 1 ? AB : BA; grantAt = now; }
            Recorder.event(world, "SINGLE LINE " + id() + ": " + names.getOrDefault(k, "train") + " in from " + (side == 1 ? "A" : "B") + " (" + dirName(dir) + ")");
        }
        inside.keySet().removeIf(k -> !seen.contains(k));
        requests.entrySet().removeIf(e -> now - e.getValue()[2] > 100);   // not seen asking for 5 s
        if (!inside.isEmpty()) clearSince = -1;
        else if (clearSince < 0) clearSince = now;
        grant(now);
        putLive(now);
    }

    /** a loco near an end, outside the section, heading in: a request from that end */
    private void request(Locomotive l, String key, Vec3d q, Vec3d pa, Vec3d pb, long now) {
        double da = Math.sqrt(d2(q, pa)), db = Math.sqrt(d2(q, pb));
        int end = da <= db ? 1 : 2;
        double d = Math.min(da, db);
        if (d > REQUEST_M) { requests.remove(key); return; }
        Vec3d e = end == 1 ? pa : pb;
        double tx, tz;
        Vec3d v = l.getVelocity();
        if (Math.abs(l.getCurrentSpeed().metric()) > 1 && v.x * v.x + v.z * v.z > 1e-6) { tx = v.x; tz = v.z; }
        else {                                                           // standing: the way it's set to go
            double yaw = Math.toRadians(l.getRotationYaw());
            double sgn = l.getReverser() < -0.05 ? -1 : 1;
            tx = -Math.sin(yaw) * sgn; tz = Math.cos(yaw) * sgn;
        }
        boolean toward = (e.x - q.x) * tx + (e.z - q.z) * tz > 0 || d < 12;
        if (!toward) { requests.remove(key); return; }
        long[] r = requests.get(key);
        if (r == null) { requests.put(key, new long[]{end, now, now}); names.put(key, Rails.name(l)); }
        else { r[0] = end; r[2] = now; }
    }

    private void grant(long now) {
        int m = mode();
        if (m == 1) { dir = AB; return; }
        if (m == 2) { dir = BA; return; }
        if (m == 3) { return; }
        if (!inside.isEmpty()) return;                                    // worked in its direction until it clears
        if (clearSince < 0 || now - clearSince < CLEAR_S * 20L) return;
        if (dir != NONE) {
            boolean waiting = false;
            for (long[] r : requests.values()) if (r[0] == (dir == AB ? 1 : 2)) waiting = true;
            if (waiting && now - grantAt < GRANT_LAPSE_S * 20L) return;   // the granted train is still coming
            if (now - grantAt >= GRANT_LAPSE_S * 20L && waiting) refusals++;   // lapsed: it never came in
            dir = NONE;
        }
        long first = Long.MAX_VALUE;
        int end = 0;
        for (long[] r : requests.values()) if (r[1] < first) { first = r[1]; end = (int) r[0]; }
        if (end != 0) {
            dir = end == 1 ? AB : BA;
            grantAt = now;
            world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("signal.block_bell"), net.minecraft.util.SoundCategory.BLOCKS, 0.6f, 1f);
        }
    }

    /** may a train enter from this end now (1 A, 2 B) */
    boolean mayEnter(int end) {
        if (!powered() || mode() == 3) return false;
        int want = end == 1 ? AB : BA;
        if (dir != want) return false;
        if (!follow()) for (int side : inside.values()) if (side == end) return false;   // ONE TRAIN: the one in there first
        return true;
    }

    private static double d2(Vec3d a, Vec3d b) { return (a.x - b.x) * (a.x - b.x) + (a.z - b.z) * (a.z - b.z); }

    private boolean near(Vec3d q, TrackPath p) { return MovingBlock.along(p, q.x, q.y, q.z, 6) >= 0; }

    private static Vec3d endPoint(TrackPath p, boolean first) {
        TrackPath.Step s = p.steps.get(first ? 0 : p.steps.size() - 1);
        if (s.points != null && s.points.length >= 3) {
            double[] pt = s.points;
            if (p.steps.size() > 1) {                                    // the end of this piece away from the rest of the section
                TrackPath.Step o = p.steps.get(first ? 1 : p.steps.size() - 2);
                double ox = o.pos.x + 0.5, oz = o.pos.z + 0.5;
                double a = (pt[0] - ox) * (pt[0] - ox) + (pt[2] - oz) * (pt[2] - oz);
                double b = (pt[pt.length - 3] - ox) * (pt[pt.length - 3] - ox) + (pt[pt.length - 1] - oz) * (pt[pt.length - 1] - oz);
                return a > b ? new Vec3d(pt[0], pt[1], pt[2]) : new Vec3d(pt[pt.length - 3], pt[pt.length - 2], pt[pt.length - 1]);
            }
            return new Vec3d(pt[first ? 0 : pt.length - 3], pt[first ? 1 : pt.length - 2], pt[first ? 2 : pt.length - 1]);
        }
        return new Vec3d(s.pos.x + 0.5, s.pos.y, s.pos.z + 0.5);
    }

    static String trainKey(EntityMoveableRollingStock s) {
        String best = s.getUUID().toString();
        if (s instanceof EntityCoupleableRollingStock c) {
            try { for (EntityCoupleableRollingStock o : c.getTrain()) { String u = o.getUUID().toString(); if (u.compareTo(best) < 0) best = u; } } catch (RuntimeException ignored) { }
        }
        return best;
    }

    // ---- what the rest of the railway asks ------------------------------------------------------------------------------

    /** SafetyHolds (via TrackHooks): entry signals of an end without the direction are held */
    void holds(Map<Long, String> hold, List<Object[]> zones) {
        if (endA == 0 || endB == 0) return;
        for (int end = 1; end <= 2; end++) {
            if (mayEnter(end)) continue;
            String why = "single line " + id() + ": " + (!powered() ? "controller has no 12 V" : mode() == 3 ? "section CLOSED" : "worked " + dirName(dir) + (inside.isEmpty() ? "" : ", " + inside.size() + " train(s) in"));
            for (long k : end == 1 ? sigA : sigB) if (k != 0) hold.putIfAbsent(k, why);
        }
    }

    /** Wayside (via TrackHooks.stops): stand driverless trains 8 m short of an end they may not enter: {dist, kmh 0, stop, why, where} */
    void stops(TrackPath route, List<Object[]> out) {
        if (endA == 0 || endB == 0 || !TrackConfig.singleLineHoldsAutopilot) return;
        Vec3i a = vec(endA), b = vec(endB);
        int ia = route.indexOf(a), ib = route.indexOf(b);
        if (ia < 0 || ib < 0 || ia == ib) return;
        int end = ia < ib ? 1 : 2, first = Math.min(ia, ib);
        if (mayEnter(end)) return;
        double d = first == 0 ? 0 : route.steps.get(first - 1).distance;
        out.add(new Object[]{Math.max(0, d - 8), 0.0, true, "single line " + id() + ": waiting for the section (" + dirName(dir) + ")", pos});
    }

    private static Vec3i vec(long k) { BlockPos p = BlockPos.fromLong(k); return new Vec3i(p.getX(), p.getY(), p.getZ()); }

    // ---- display -----------------------------------------------------------------------------------------------------------

    private void putLive(long now) {
        boolean linked = endA != 0 && endB != 0 && path != null;
        put("ab", dir == AB);
        put("ba", dir == BA);
        put("occ", !inside.isEmpty());
        put("reqa", queue(1) > 0);
        put("reqb", queue(2) > 0);
        put("enta", linked && mayEnter(1));
        put("entb", linked && mayEnter(2));
        put("clsd", mode() == 3);
        put("link", linked);
        put("pwr", powered());
        for (int i = 0; i < 5; i++) put("t" + (i + 1), occ[i]);
        put("end_a", endA != 0);
        put("end_b", endB != 0);
        put("sig_a", sigA[0] != 0 || sigA[1] != 0);
        put("sig_b", sigB[0] != 0 || sigB[1] != 0);
        put("cflx", ctl.get("cfl") == 1);
        put("g_dir", dir);                                                // the direction needle: 0 centre, 1 A>B, 2 B>A
        put("trains", inside.size());
        text("dir", !linked ? "NOT SET" : MODES[mode()].startsWith("FIXED") ? "FIXED " + dirName(dir) : mode() == 3 ? "CLOSED" : dirName(dir));
        text("len", linked ? String.format(Locale.ROOT, "%.0f M", lengthM) : "--");
        text("qa", "A Q" + queue(1));
        text("qb", "B Q" + queue(2));
        text("grant", dir == NONE ? "--" : (now - grantAt) / 20 + " S");
        text("ends", (endA == 0 ? "-" : Rails.xz(BlockPos.fromLong(endA))) + " / " + (endB == 0 ? "-" : Rails.xz(BlockPos.fromLong(endB))));
        text("id", id());
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        switch (id) {
            case "test": return lampTest();
            case "s_mode": {
                ctl.set("s_mode", (mode() + 1) % 4);
                if (mode() == 0 && inside.isEmpty()) dir = NONE;
                Recorder.event(world, "SINGLE LINE " + id() + " mode " + MODES[mode()] + " by " + who(who));
                return "§eSection mode: " + MODES[mode()] + (mode() != 0 && !inside.isEmpty() ? " §6(trains still in the section)" : "");
            }
            case "p_cancel": {
                if (!inside.isEmpty()) return "§cCan't cancel - " + inside.size() + " train(s) in the section";
                dir = NONE; grantAt = 0;
                Recorder.event(world, "SINGLE LINE " + id() + ": direction cancelled by " + who(who));
                return "§eDirection cancelled - next request gets the section";
            }
            case "p_reset": {
                ctl.set("cfl", 0);
                AlarmManager.get(world).clear("slconf:" + pos.toLong());
                return "§aConflict alarm reset";
            }
            default: return null;
        }
    }

    @Override protected String[] assetRow() {
        return new String[]{"GT", id() + (gauntlet() ? " GAUNTLET" : ""), (endA == 0 ? "-" : Rails.xz(BlockPos.fromLong(endA))) + " / " + (endB == 0 ? "-" : Rails.xz(BlockPos.fromLong(endB))), dirName(dir),
                inside.isEmpty() ? "clear" : inside.size() + " in", String.valueOf(queue(1)), String.valueOf(queue(2)), String.valueOf(entries), String.format(Locale.ROOT, "%.0f", lengthM),
                conflicts > 0 ? "FAULT " + conflicts + " conflicts, " + refusals + " lapsed" : String.valueOf(refusals), MODES[mode()] + (follow() ? " FOLLOW" : ""), powered() ? "12V" : "NO 12V"};
    }

    public List<String> report() {
        List<String> l = new ArrayList<>();
        l.add("§b" + wiredName() + " §7at " + Rails.xz(pos) + " · " + gearState());
        l.add(String.format(Locale.ROOT, "§7  %.0f m · %d entries · %d conflicts · %d lapsed grants · in: %s", lengthM, entries, conflicts, refusals, inside.isEmpty() ? "-" : String.join(", ", inside.keySet().stream().map(k -> names.getOrDefault(k, "?")).toArray(String[]::new))));
        return l;
    }

    @Override
    protected void save(NBTTagCompound t) {
        t.setLong("a", endA); t.setLong("b", endB);
        t.setTag("sa", longs(sigA)); t.setTag("sb", longs(sigB));
        t.setInteger("dir", dir); t.setLong("grant", grantAt); t.setInteger("ent", entries); t.setInteger("ref", refusals); t.setInteger("cfl", conflicts); t.setDouble("len", lengthM);
        NBTTagCompound in = new NBTTagCompound();
        for (Map.Entry<String, Integer> e : inside.entrySet()) in.setInteger(e.getKey(), e.getValue());
        t.setTag("in", in);
    }

    @Override
    protected void load(NBTTagCompound t) {
        endA = t.getLong("a"); endB = t.getLong("b");
        unlongs(t.getTagList("sa", Constants.NBT.TAG_LONG), sigA); unlongs(t.getTagList("sb", Constants.NBT.TAG_LONG), sigB);
        dir = t.getInteger("dir"); grantAt = t.getLong("grant"); entries = t.getInteger("ent"); refusals = t.getInteger("ref"); conflicts = t.getInteger("cfl"); lengthM = t.getDouble("len");
        inside.clear();
        NBTTagCompound in = t.getCompoundTag("in");
        for (String k : in.getKeySet()) inside.put(k, in.getInteger(k));
        path = null;
    }

    private static NBTTagList longs(long[] a) { NBTTagList l = new NBTTagList(); for (long v : a) l.appendTag(new NBTTagLong(v)); return l; }
    private static void unlongs(NBTTagList l, long[] a) { for (int i = 0; i < a.length; i++) a[i] = i < l.tagCount() ? ((NBTTagLong) l.get(i)).getLong() : 0; }
}
