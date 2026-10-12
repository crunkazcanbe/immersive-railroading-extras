package com.dogpound.railmap.infra;

import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.ohle.Ohle;
import com.dogpound.railmap.ohle.OhleData;
import com.dogpound.railmap.signal.BlockCatenary;
import com.dogpound.railmap.track.Rails;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Catenary inspection vehicle (her ChatGPT list batch 3, #44). Any IR stock tagged OHLE, CATENARY or OLR (an overhead
 * line recording car - any loco or coach, electric or not) measures the contact wire it runs under, every metre, while
 * it travels: the wire HEIGHT above the rail (laser), its STAGGER off the track centre line (camera) and its WEAR
 * (remaining cross-section, from the wear model ohle.Ohle builds up). A run starts by itself when a recording car moves
 * under wire and ends a minute after it stops recording (or END RUN on the OHLE Recording Station). Every metre outside
 * the limits of the run's class - STANDARD 4.7-6.2 m / stagger 0.40 m / wear 30 %, HIGH SPEED 5.0-5.5 m / 0.30 m / 20 % -
 * is an EXCEEDANCE, listed by run with its place and value; the station raises them as work orders, prints the report
 * and shows the latest measurements, which the console's RECORDING page draws as traces.
 */
public final class OhleRecording {
    private OhleRecording() {}

    public static final String[] CLASSES = {"STANDARD", "HIGH SPEED"};
    static final double[][] LIM = {{4.7, 6.2, 0.40, 0.30}, {5.0, 5.5, 0.30, 0.20}};   // h min, h max, stagger, wear
    /** per car: {last x, last z, last seen tick, run id}; the class the next run takes (per dimension) */
    private static final Map<UUID, double[]> CAR = new HashMap<>();
    private static final Map<Integer, Integer> ARMED = new HashMap<>();

    public static boolean isRecorder(String tag) { return tag.contains("OHLE") || tag.contains("CATENARY") || tag.contains("OLR"); }

    static BlockCatenary.Kind kind(World w, BlockPos p) {
        if (!w.isBlockLoaded(p)) return null;
        Block b = w.getBlockState(p).getBlock();
        return b instanceof BlockCatenary c ? c.kind() : null;
    }

    public static void arm(World w, int cls) { ARMED.put(w.provider.getDimension(), Math.max(0, Math.min(1, cls))); }

    /** once a second for a tagged car */
    static void measure(World w, EntityRollingStock s, long now) {
        Vec3d p = s.getPosition();
        double kmh = s instanceof EntityMoveableRollingStock m && m.getCurrentSpeed() != null ? Math.abs(m.getCurrentSpeed().metric()) : 0;
        double[] c = CAR.get(s.getUUID());
        InfraData d = InfraData.get(w);
        if (kmh < 0.5) return;
        // which run
        InfraData.Run run = c == null ? null : d.run((int) c[3]);
        if (run == null || run.end != 0) {
            run = new InfraData.Run();
            run.id = d.nextRun++;
            run.vehicle = Rails.name(s);
            run.start = now;
            run.cls = CLASSES[ARMED.getOrDefault(w.provider.getDimension(), 0)];
            d.runs.add(0, run);
            while (d.runs.size() > 20) d.runs.remove(d.runs.size() - 1);
            int rid = run.id;
            d.exc.removeIf(e -> d.run(e.run) == null && e.run != rid);
            c = new double[]{p.x, p.z, now, run.id};
            CAR.put(s.getUUID(), c);
            d.log(w, "OHLE recording run #" + run.id + " started: " + run.vehicle + " (" + run.cls + " limits)");
        }
        int cls = run.cls.equals(CLASSES[1]) ? 1 : 0;
        run.vMax = Math.max(run.vMax, kmh);
        // every block between last second's position and now
        int steps = (int) Math.min(80, Math.ceil(Math.hypot(p.x - c[0], p.z - c[1])));
        OhleData od = OhleData.get(w);
        int baseY = (int) Math.floor(p.y);
        Set<Long> seen = new HashSet<>();
        for (int i = 1; i <= Math.max(1, steps); i++) {
            double fx = c[0] + (p.x - c[0]) * i / Math.max(1, steps), fz = c[1] + (p.z - c[1]) * i / Math.max(1, steps);
            BlockPos best = null;
            double bestOff = 9;
            for (int dy = 2; dy <= 8; dy++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos q = new BlockPos(Math.floor(fx) + dx, baseY + dy, Math.floor(fz) + dz);
                BlockCatenary.Kind k = kind(w, q);
                if (k != BlockCatenary.Kind.WIRE) continue;
                double off = Math.hypot(q.getX() + 0.5 - fx, q.getZ() + 0.5 - fz);
                if (off < bestOff) { bestOff = off; best = q; }
            }
            if (best == null || bestOff > 2.2 || !seen.add(best.toLong())) continue;
            double h = best.getY() + 0.125 - p.y, wear = od.wear.getOrDefault(best.toLong(), 0f);
            if (od.damaged.contains(best.toLong())) wear = 1;
            boolean fresh = !d.rec.containsKey(best.toLong()) || d.rec.get(best.toLong())[4] != run.id;
            d.rec.put(best.toLong(), new double[]{h, bestOff, wear, now, run.id});
            if (fresh) run.metres++;
            run.hMin = Math.min(run.hMin, h); run.hMax = Math.max(run.hMax, h);
            run.stgMax = Math.max(run.stgMax, bestOff); run.wearMax = Math.max(run.wearMax, wear);
            if (!fresh) continue;
            double[] l = LIM[cls];
            String what = h < l[0] ? "LOW WIRE" : h > l[1] ? "HIGH WIRE" : bestOff > l[2] ? "STAGGER" : wear > l[3] ? "WEAR" : null;
            if (what != null) {
                InfraData.Exc e = new InfraData.Exc();
                e.pos = best.toLong(); e.run = run.id; e.what = what; e.value = what.equals("STAGGER") ? bestOff : what.equals("WEAR") ? wear * 100 : h;
                d.exc.add(e);
                while (d.exc.size() > 400) d.exc.remove(0);
                run.exceed++;
            }
        }
        c[0] = p.x; c[1] = p.z; c[2] = now;
        d.markDirty();
    }

    /** runs whose car stopped recording a minute ago end */
    static void sweep(World w, long now) {
        InfraData d = InfraData.get(w);
        for (Iterator<Map.Entry<UUID, double[]>> it = CAR.entrySet().iterator(); it.hasNext(); ) {
            double[] c = it.next().getValue();
            if (now - (long) c[2] < 1200) continue;
            InfraData.Run r = d.run((int) c[3]);
            if (r != null && r.end == 0) end(w, r);
            it.remove();
        }
    }

    public static void end(World w, InfraData.Run r) {
        InfraData d = InfraData.get(w);
        r.end = w.getTotalWorldTime();
        String msg = String.format(Locale.ROOT, "OHLE recording run #%d (%s) ended: %.0f m measured, height %.2f-%.2f m, stagger max %.2f m, wear max %.0f%%, %d exceedance(s)",
                r.id, r.vehicle, r.metres, r.hMin > 50 ? 0 : r.hMin, r.hMax, r.stgMax, r.wearMax * 100, r.exceed);
        d.log(w, msg);
        Recorder.event(w, msg);
        if (r.exceed > 0) AlarmManager.get(w).raise(w, "ohlerun:" + r.id, AlarmManager.Cat.MACHINE, AlarmManager.Sev.MINOR, msg + " - review on the OHLE Recording Station", null, false);
        com.dogpound.railmap.api.QuestEvents.fire(w, null, null, "ohle_recording_run", "metres", (int) r.metres, "exceedances", r.exceed);
        d.markDirty();
    }

    /** RAISE ORDERS: every exceedance of a run becomes a maintenance alarm at its place */
    public static int orders(World w, int run) {
        int n = 0;
        AlarmManager am = AlarmManager.get(w);
        for (InfraData.Exc e : InfraData.get(w).exc) {
            if (e.run != run) continue;
            BlockPos p = BlockPos.fromLong(e.pos);
            am.raise(w, "ohlerec:" + e.pos, AlarmManager.Cat.MACHINE, AlarmManager.Sev.MINOR, String.format(Locale.ROOT, "OHLE work order (run #%d): %s %.2f at %s - %s", run, e.what, e.value, Ohle.xyz(p),
                    e.what.contains("WIRE") ? "re-register the wire height at the drop-arms" : e.what.equals("STAGGER") ? "adjust the registration arm" : "renew the contact wire"), p, true);
            n++;
        }
        return n;
    }

    public static List<String> report(World w, InfraData.Run r) {
        List<String> out = new ArrayList<>();
        out.add("OHLE RECORDING RUN #" + r.id);
        out.add(r.vehicle + " - " + r.cls + " limits");
        out.add(String.format(Locale.ROOT, "%.0f m, max %.0f km/h", r.metres, r.vMax));
        out.add(String.format(Locale.ROOT, "height %.2f - %.2f m", r.hMin > 50 ? 0 : r.hMin, r.hMax));
        out.add(String.format(Locale.ROOT, "stagger max %.2f m", r.stgMax));
        out.add(String.format(Locale.ROOT, "wear max %.0f %%", r.wearMax * 100));
        out.add(r.exceed + " exceedance(s):");
        for (InfraData.Exc e : InfraData.get(w).exc) if (e.run == r.id) out.add(e.what + " " + String.format(Locale.ROOT, "%.2f", e.value) + " @ " + Ohle.xz(BlockPos.fromLong(e.pos)));
        return out;
    }

    /** OR run rows · OX exceedances of the newest runs · OT the newest run's trace (height, stagger, wear in measuring order) */
    public static void rows(World w, List<String[]> out) {
        InfraData d = InfraData.get(w);
        for (InfraData.Run r : d.runs)
            out.add(new String[]{"OR", String.valueOf(r.id), r.vehicle, r.cls, r.end == 0 ? "RECORDING" : "done", String.format(Locale.ROOT, "%.0f", r.metres),
                    String.format(Locale.ROOT, "%.2f-%.2f", r.hMin > 50 ? 0 : r.hMin, r.hMax), String.format(Locale.ROOT, "%.2f", r.stgMax), String.format(Locale.ROOT, "%.0f", r.wearMax * 100),
                    String.valueOf(r.exceed), String.format(Locale.ROOT, "%.0f", r.vMax)});
        int n = 0;
        for (int i = d.exc.size() - 1; i >= 0 && n < 120; i--, n++) {
            InfraData.Exc e = d.exc.get(i);
            out.add(new String[]{"OX", String.valueOf(e.run), e.what, String.format(Locale.ROOT, "%.2f", e.value), Ohle.xyz(BlockPos.fromLong(e.pos))});
        }
        if (!d.runs.isEmpty()) {
            int id = d.runs.get(0).id;
            List<double[]> tr = new ArrayList<>();
            for (double[] v : d.rec.values()) if ((int) v[4] == id) tr.add(v);
            tr.sort(Comparator.comparingDouble(v -> v[3]));
            int step = Math.max(1, tr.size() / 160);
            for (int i = 0; i < tr.size(); i += step)
                out.add(new String[]{"OT", String.format(Locale.ROOT, "%.2f", tr.get(i)[0]), String.format(Locale.ROOT, "%.2f", tr.get(i)[1]), String.format(Locale.ROOT, "%.2f", tr.get(i)[2])});
        }
    }
}
