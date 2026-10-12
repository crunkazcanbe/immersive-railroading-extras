package com.dogpound.railmap.infra;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.ohle.Ohle;
import com.dogpound.railmap.ohle.OhleData;
import com.dogpound.railmap.signal.BlockCatenary;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Contact-wire fault location (her ChatGPT list batch 3, #39). When an OHLE feeder breaker trips (ohle.Ohle.trip), the
 * feeder's distance relay has measured the loop impedance to the fault: line resistance and reactance per metre of
 * contact + return circuit (0.17 + j0.44 Ω/km, 25 kV) out to the fault, plus the FAULT RESISTANCE (a flashover ~0.5 Ω,
 * wire on the ground 5-40 Ω, an earth switch ~0). The REACTANCE METHOD reads the distance from X alone, so fault
 * resistance doesn't spoil it - unless the far end also feeds the fault (double-end feeding, #37): the remote infeed
 * through the fault resistance adds an apparent reactance and the single-ended estimate is off. When both ends trip
 * (both feeders record it), the DOUBLE-ENDED method combines the two and the fault resistance cancels: ±1 %.
 * <p>
 * The true distance is the length of contact wire between the feeder and the fault (walked along the wire). An
 * INSPECTION CREW dispatched from the Fault Locator panel drives to the estimate, searches out from it (the worse the
 * estimate, the longer the search), finds the real fault and - for torn-down wire, once the section is isolated and
 * earthed - renews it (InfraConfig.crewsRepairWire).
 */
public final class FaultLocator {
    private FaultLocator() {}

    static final double R_PER_M = 0.17e-3, X_PER_M = 0.44e-3, RS = 0.5, XS = 6.0, KV = 25.0;
    public static final String[] CREW = {"-", "DRIVING OUT", "SEARCHING", "FOUND", "WAITING ISOLATION + EARTH", "RENEWING WIRE", "DONE"};

    /** Ohle.trip: one feeder's distance relay has seen a fault */
    public static void onTrip(World w, long feeder, OhleData.Fault f, Ohle.Section s) {
        InfraData d = InfraData.get(w);
        long now = w.getTotalWorldTime();
        for (InfraData.FaultRec r : d.faults) {
            if (r.pos != f.pos || now - r.at > 100) continue;
            if (r.feeder == feeder || r.feeder2 == feeder) return;
            if (r.feeder2 == 0) { r.feeder2 = feeder; doubleEnded(w, r, s, feeder); d.markDirty(); }
            return;
        }
        InfraData.FaultRec r = new InfraData.FaultRec();
        r.id = d.nextFault++;
        r.at = now; r.feeder = feeder; r.pos = f.pos; r.kind = f.kind; r.why = f.why; r.persistent = f.persistent; r.section = s == null ? "" : s.name();
        List<Long> path = path(w, s, BlockPos.fromLong(feeder), BlockPos.fromLong(f.pos));
        BlockPos fp = BlockPos.fromLong(f.pos), fd = BlockPos.fromLong(feeder);
        r.dist = path.size() > 1 ? path.size() - 1 : Math.hypot(fp.getX() - fd.getX(), fp.getZ() - fd.getZ());
        r.total = Math.max(r.dist, s == null ? r.dist : reach(w, s, fd));
        Random rnd = new Random(now ^ f.pos);
        r.rf = faultResistance(f.kind, f.why, rnd);
        boolean remote = s != null && s.feeders.size() > 1;
        double k = remote ? Math.min(4, r.dist / Math.max(5, r.total - r.dist)) : 0;          // remote infeed ratio I_far / I_near
        double[] m = measure(r.dist, r.rf, k, rnd);
        r.r = m[0]; r.x = m[1]; r.ika = m[2]; r.estD = m[3]; r.unc = m[4];
        r.est = point(path, r.estD, f.pos);
        d.faults.add(0, r);
        while (d.faults.size() > 30) d.faults.remove(d.faults.size() - 1);
        String msg = String.format(Locale.ROOT, "FAULT LOCATOR #%d: %s fault %.0f m (±%.0f) from feeder %s along the wire - Z %.2f Ω (R %.2f X %.3f), %.1f kA%s",
                r.id, r.kind, r.estD, r.unc, Ohle.xz(fd), Math.hypot(r.r, r.x), r.r, r.x, r.ika, remote ? " - double-end fed: single-ended estimate distorted by remote infeed" : "");
        d.log(w, msg);
        Recorder.event(w, msg);
        AlarmManager.get(w).raise(w, "floc:" + r.id, AlarmManager.Cat.FAULT, AlarmManager.Sev.INFO, msg + " - dispatch a crew from the Fault Locator", BlockPos.fromLong(r.est), false);
        d.markDirty();
    }

    /** {R, X, kA, estimated m, uncertainty m}: the reactance method with remote infeed and instrument error */
    static double[] measure(double dist, double rf, double k, Random rnd) {
        double rl = R_PER_M * dist, xl = X_PER_M * dist;
        double rm = rl + rf * (1 + k), xm = xl + rf * k * 0.003;                       // remote infeed: apparent extra reactance (load angle)
        double ika = KV / Math.hypot(RS + rl + rf, XS + xl);
        double est = Math.max(0, xm / X_PER_M * (1 + rnd.nextGaussian() * 0.01));       // CT / VT / filter error ~1 %
        double unc = Math.max(5, est * 0.02 + (k > 0 ? rf * k * 0.003 / X_PER_M * 0.5 : 0));
        return new double[]{rm, xm, ika, est, unc};
    }

    static double faultResistance(String kind, String why, Random rnd) {
        String y = why.toUpperCase(Locale.ROOT);
        if (y.contains("EARTHED SECTION") || y.contains("EARTH SWITCH")) return 0.05;
        if (kind.equals("SHORT")) return 0.3 + rnd.nextDouble() * 0.7;                  // an arc / flashover
        if (y.contains("DEWIRE") || y.contains("SNAPPED") || y.contains("DOWN")) return 5 + rnd.nextDouble() * 35;   // wire lying on ballast
        if (y.contains("FLOOD")) return 1 + rnd.nextDouble() * 3;
        return 2 + rnd.nextDouble() * 10;
    }

    /** the other end's relay: combine both - the fault resistance cancels */
    static void doubleEnded(World w, InfraData.FaultRec r, Ohle.Section s, long feeder2) {
        Random rnd = new Random(r.at * 31 + feeder2);
        r.unc2 = 0.01 * r.total + 2;
        r.estD2 = Math.max(0, r.dist + rnd.nextGaussian() * r.unc2 * 0.5);
        List<Long> path = path(w, s, BlockPos.fromLong(r.feeder), BlockPos.fromLong(r.pos));
        r.est2 = point(path, r.estD2, r.pos);
        InfraData.get(w).log(w, String.format(Locale.ROOT, "FAULT LOCATOR #%d: double-ended (feeder %s too) -> %.0f m ±%.0f", r.id, Ohle.xz(BlockPos.fromLong(feeder2)), r.estD2, r.unc2));
    }

    /** the best estimate and its uncertainty: double-ended when there is one (method 0 auto, 1 single, 2 double) */
    public static double[] best(InfraData.FaultRec r, int method) {
        boolean dbl = r.estD2 >= 0 && method != 1;
        if (method == 2 && r.estD2 < 0) return new double[]{-1, 0};
        return dbl ? new double[]{r.estD2, r.unc2} : new double[]{r.estD, r.unc};
    }

    /** where that estimate puts it */
    public static long bestPos(InfraData.FaultRec r, int method) { return r.estD2 >= 0 && method != 1 ? r.est2 : r.est; }

    // ---- walking the wire ---------------------------------------------------------------------------------------------
    private static final int[][] STEPS = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1}, {1, -1, 0}, {-1, -1, 0}, {0, -1, 1}, {0, -1, -1}};

    static boolean line(World w, BlockPos p) {
        if (!w.isBlockLoaded(p)) return false;
        Block b = w.getBlockState(p).getBlock();
        return b instanceof BlockCatenary c && c.kind().line();
    }

    /** the contact-line blocks nearest the feeder's clamp (within 4 blocks, as Ohle feeds a section) */
    static List<BlockPos> starts(World w, BlockPos feeder) {
        List<BlockPos> out = new ArrayList<>();
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) for (int dy = -1; dy <= 10; dy++) {
            BlockPos p = feeder.add(dx, dy, dz);
            if (line(w, p)) out.add(p);
        }
        out.sort(Comparator.comparingDouble(p -> p.distanceSq(feeder)));
        return out.size() > 6 ? out.subList(0, 6) : out;
    }

    /** the wire from the feeder's clamp to the fault, block by block (empty if not connected) */
    public static List<Long> path(World w, Ohle.Section s, BlockPos feeder, BlockPos fault) {
        Map<Long, Long> prev = new HashMap<>();
        Deque<BlockPos> q = new ArrayDeque<>();
        for (BlockPos st : starts(w, feeder)) { prev.put(st.toLong(), Long.MIN_VALUE); q.add(st); }
        long goal = fault.toLong();
        boolean hit = false;
        while (!q.isEmpty() && prev.size() < 12000) {
            BlockPos p = q.poll();
            if (p.toLong() == goal) { hit = true; break; }
            for (int[] st : STEPS) {
                BlockPos n = p.add(st[0], st[1], st[2]);
                if (prev.containsKey(n.toLong()) || !line(w, n)) continue;
                prev.put(n.toLong(), p.toLong());
                q.add(n);
            }
        }
        List<Long> out = new ArrayList<>();
        if (!hit) return out;
        for (long c = goal; c != Long.MIN_VALUE; c = prev.get(c)) out.add(0, c);
        return out;
    }

    /** the longest wire run from this feeder (the section's far end) */
    static double reach(World w, Ohle.Section s, BlockPos feeder) {
        Map<Long, Integer> dist = new HashMap<>();
        Deque<BlockPos> q = new ArrayDeque<>();
        for (BlockPos st : starts(w, feeder)) { dist.put(st.toLong(), 0); q.add(st); }
        int far = 0;
        while (!q.isEmpty() && dist.size() < 12000) {
            BlockPos p = q.poll();
            int dp = dist.get(p.toLong());
            far = Math.max(far, dp);
            for (int[] st : STEPS) {
                BlockPos n = p.add(st[0], st[1], st[2]);
                if (dist.containsKey(n.toLong()) || !line(w, n)) continue;
                dist.put(n.toLong(), dp + 1);
                q.add(n);
            }
        }
        return far;
    }

    static long point(List<Long> path, double d, long fallback) {
        if (path.isEmpty()) return fallback;
        return path.get((int) Math.max(0, Math.min(path.size() - 1, Math.round(d))));
    }

    // ---- the inspection crew -----------------------------------------------------------------------------------------
    public static String dispatch(World w, int id, BlockPos from, int method, String who) {
        InfraData d = InfraData.get(w);
        InfraData.FaultRec r = d.fault(id);
        if (r == null) return "§cNo fault record #" + id;
        if (r.crew > 0 && r.crew < 6) return "§7A crew is already on #" + id + " (" + CREW[r.crew] + ")";
        double[] b = best(r, method);
        if (b[0] < 0) return "§cNo double-ended measurement for #" + id + " - switch the method to AUTO / SINGLE";
        long estPos = bestPos(r, method);
        BlockPos est = BlockPos.fromLong(estPos);
        double road = Math.hypot(est.getX() - from.getX(), est.getZ() - from.getZ()) * 1.3;          // roads wind
        r.crew = 1;
        r.crewAt = w.getTotalWorldTime();
        r.crewDue = r.crewAt + 200 + Math.round(road / InfraConfig.crewDriveMps * 20);
        r.crewNote = String.format(Locale.ROOT, "to %d,%d (%.0f m ±%.0f, %s)", est.getX(), est.getZ(), b[0], b[1], r.estD2 >= 0 && method != 1 ? "double-ended" : "single-ended");
        r.est = estPos;
        r.acked = true;
        AlarmManager.get(w).clear("floc:" + r.id);
        d.log(w, "fault #" + id + ": inspection crew dispatched by " + who + " " + r.crewNote);
        Recorder.event(w, "OHLE fault #" + id + ": crew dispatched " + r.crewNote);
        d.markDirty();
        return String.format(Locale.ROOT, "§aCrew dispatched to fault #%d - on site in %d s", id, (r.crewDue - r.crewAt) / 20);
    }

    /** once a second: crews drive, search, find, wait for the isolation, renew */
    static void tick(World w, long now) {
        InfraData d = InfraData.get(w);
        boolean ch = false;
        for (InfraData.FaultRec r : d.faults) {
            switch (r.crew) {
                case 1: {
                    if (now < r.crewDue) break;
                    r.crew = 2;
                    double off = Math.hypot(BlockPos.fromLong(r.est).getX() - BlockPos.fromLong(r.pos).getX(), BlockPos.fromLong(r.est).getZ() - BlockPos.fromLong(r.pos).getZ());
                    r.crewDue = now + Math.round((off / InfraConfig.crewSearchMps + 20) * 20);
                    r.crewNote = "on site, walking the line out from the estimate";
                    ch = true;
                    break;
                }
                case 2: {
                    if (now < r.crewDue) break;
                    BlockPos e = BlockPos.fromLong(r.est), p = BlockPos.fromLong(r.pos);
                    r.found = r.pos;
                    double miss = Math.hypot(e.getX() - p.getX(), e.getZ() - p.getZ());
                    boolean down = OhleData.get(w).damaged.contains(r.pos) || near(OhleData.get(w).damaged, p, 4);
                    r.crewNote = String.format(Locale.ROOT, "FOUND at %d,%d - %.0f m from the estimate: %s", p.getX(), p.getZ(), miss,
                            down ? "contact wire DOWN" : r.kind.equals("SHORT") ? "flashover marks, no lasting damage" : r.persistent ? "standing earth fault" : "no damage - transient");
                    r.crew = down && InfraConfig.crewsRepairWire ? 4 : 6;
                    d.log(w, "fault #" + r.id + ": " + r.crewNote);
                    Recorder.event(w, "OHLE fault #" + r.id + " " + r.crewNote);
                    for (net.minecraft.entity.player.EntityPlayer pl : w.playerEntities)
                        if (pl.getDistanceSq(p) < 96 * 96) pl.sendMessage(new net.minecraft.util.text.TextComponentString("§b[OHLE crew] fault #" + r.id + " " + r.crewNote));
                    ch = true;
                    break;
                }
                case 4: {
                    BlockPos p = BlockPos.fromLong(r.pos);
                    boolean dead = true, earthed = false;
                    for (Ohle.Section s : Ohle.around(w, p)) { dead &= !s.energised; earthed |= s.earthed; }
                    if (dead && earthed) { r.crew = 5; r.crewDue = now + 3600; r.crewNote = "section dead + earthed: renewing the wire (3 min)"; d.log(w, "fault #" + r.id + ": " + r.crewNote); ch = true; }
                    else if (!r.crewNote.startsWith("WAITING")) { r.crewNote = "WAITING: open the feeder + earth the section (isolator earth switch) so the crew can work"; ch = true; }
                    break;
                }
                case 5: {
                    BlockPos p = BlockPos.fromLong(r.pos);
                    boolean live = false;
                    for (Ohle.Section s : Ohle.around(w, p)) live |= s.energised;
                    if (live) { r.crew = 4; r.crewNote = "WITHDRAWN - the section was re-energised under the crew!"; AlarmManager.get(w).raise(w, "flocsafe:" + r.id, AlarmManager.Cat.INCIDENT, AlarmManager.Sev.CRITICAL, "OHLE crew at fault #" + r.id + " withdrawn: section re-energised while they worked", p, false); ch = true; break; }
                    if (now < r.crewDue) break;
                    int n = renew(w, p);
                    r.crew = 6;
                    r.crewNote = "wire renewed (" + n + " m) - section can be restored";
                    d.log(w, "fault #" + r.id + ": " + r.crewNote);
                    Recorder.event(w, "OHLE fault #" + r.id + ": crew renewed " + n + " m of contact wire");
                    ch = true;
                    break;
                }
                default:
            }
        }
        if (ch) d.markDirty();
    }

    static boolean near(Set<Long> s, BlockPos p, int r) {
        for (long l : s) if (BlockPos.fromLong(l).distanceSq(p) <= r * r) return true;
        return false;
    }

    /** the crew's repair: torn / worn wire within 4 blocks renewed, its standing faults cleared */
    static int renew(World w, BlockPos p) {
        OhleData od = OhleData.get(w);
        int n = 0;
        for (Iterator<Long> it = od.damaged.iterator(); it.hasNext(); ) { long l = it.next(); if (BlockPos.fromLong(l).distanceSq(p) <= 16) { it.remove(); od.wear.remove(l); n++; } }
        for (Iterator<OhleData.Fault> it = od.faults.iterator(); it.hasNext(); ) {
            OhleData.Fault f = it.next();
            if (f.persistent && BlockPos.fromLong(f.pos).distanceSq(p) <= 64) { it.remove(); AlarmManager.get(w).clear("ohlef:" + f.pos); }
        }
        od.log(w, "inspection crew renewed " + n + " m of contact wire at " + Ohle.xyz(p));
        od.markDirty();
        Ohle.dirty();
        return n;
    }

    // ---- console --------------------------------------------------------------------------------------------------------
    /** FL id · age · feeder · kind · section · single m ± · double m ± · true m (after found) · |Z| · R · X · kA · crew · note · Rf ; FLM x z unc crew for the map */
    public static void rows(World w, List<String[]> out) {
        long now = w.getTotalWorldTime();
        for (InfraData.FaultRec r : InfraData.get(w).faults) {
            out.add(new String[]{"FL", String.valueOf(r.id), ((now - r.at) / 1200) + " min", Ohle.xz(BlockPos.fromLong(r.feeder)) + (r.feeder2 != 0 ? " + " + Ohle.xz(BlockPos.fromLong(r.feeder2)) : ""),
                    r.kind, r.section, String.format(Locale.ROOT, "%.0f ±%.0f", r.estD, r.unc), r.estD2 < 0 ? "-" : String.format(Locale.ROOT, "%.0f ±%.0f", r.estD2, r.unc2),
                    r.found != 0 ? String.format(Locale.ROOT, "%.0f", r.dist) : "?", String.format(Locale.ROOT, "%.2f", Math.hypot(r.r, r.x)), String.format(Locale.ROOT, "%.2f", r.r),
                    String.format(Locale.ROOT, "%.3f", r.x), String.format(Locale.ROOT, "%.1f", r.ika), CREW[r.crew], r.crewNote, String.valueOf(r.acked ? 1 : 0)});
            if (r.crew < 6 && now - r.at < 24000) {
                BlockPos e = BlockPos.fromLong(r.est);
                out.add(new String[]{"FLM", String.valueOf(e.getX()), String.valueOf(e.getZ()), String.valueOf(Math.round(r.estD2 >= 0 ? r.unc2 : r.unc)), r.crew > 0 ? "1" : "0", String.valueOf(r.id)});
            }
        }
    }
}
