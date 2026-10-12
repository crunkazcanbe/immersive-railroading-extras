package com.dogpound.railmap.infra;

import cam72cam.immersiverailroading.tile.TileRail;
import com.dogpound.railmap.asset.AssetData;
import com.dogpound.railmap.asset.AssetKind;
import com.dogpound.railmap.track.Rails;
import com.dogpound.railmap.track.TrackData;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Rail flaw detection (her ChatGPT list batch 3, #41 - extends asset.Ndt and the asset register's hidden defects). With
 * InfraConfig.railFlawClasses a rail's hidden defect is a real UIC 712 class, chosen from what that piece of track
 * carries: tight curves grow head checks (gauge-corner RCF), fast straight track grows squats, heavy tonnage grows
 * transverse defects (tache ovale) from inside the head, and there are thermite / flash-butt weld defects and bolt-hole
 * star cracks. Each class grows at its own rate, is found by the ultrasonic tester (walking stick or test train) with
 * its own difficulty and has its own REMEDY: shallow head checks and squats are GROUND out (rail grinder, #42), squats,
 * weld and bolt-hole defects are cut out and WELDED (rail welder), a deep transverse defect means RE-RAILING.
 * <p>
 * The ultrasonic TEST TRAIN: any IR stock tagged UTU, ULTRASONIC or SPERRY tests every track piece it rolls over (the
 * same probes and probability of detection as the walking stick) - but only below InfraConfig.testTrainMaxKmh, above
 * that the probe wheels lose coupling and the run reports the untested stretch.
 */
public final class RailFlaws {
    private RailFlaws() {}

    public enum Flaw {
        TRANSVERSE("UIC 211", "transverse defect (tache ovale)", 25, 1.0, "RERAIL"),
        HEAD_CHECKS("UIC 2223", "head checks (gauge-corner RCF)", 6, 1.6, "GRIND"),
        SQUAT("UIC 227", "squat", 8, 1.3, "WELD"),
        BOLT_HOLE("UIC 135", "bolt-hole star crack", 15, 0.9, "WELD"),
        WELD("UIC 421", "weld defect (lack of fusion)", 12, 1.1, "WELD");
        public final String code, label, remedy;
        public final double crit, growth;
        Flaw(String code, String label, double crit, double growth, String remedy) { this.code = code; this.label = label; this.crit = crit; this.growth = growth; this.remedy = remedy; }
        public String type() { return code + " " + label; }
    }

    public static Flaw of(String type) {
        if (type == null) return null;
        for (Flaw f : Flaw.values()) if (type.startsWith(f.code + " ")) return f;
        return null;
    }

    /** AssetTicker: the class a new hidden defect in this track piece takes (null: the asset kind's default) */
    public static Flaw pick(World w, AssetData.Asset a, Random r) {
        if (!InfraConfig.railFlawClasses || a.kind != AssetKind.TRACK) return null;
        TrackData.Seg s = TrackData.get(w).peek(a.at());
        double[] wt = {1, 1, 1, 0.4, 0.6};
        if (s != null) {
            if (s.radius > 0 && s.radius < 800) wt[1] += 3 * 800.0 / Math.max(150, s.radius);
            if (s.radius == 0 && s.maxKmh > 80) wt[2] += 2;
            wt[0] += Math.min(4, s.tonnes / 2e6);
            wt[2] += s.get(TrackData.Metric.SURFACE) / 30;
        }
        double sum = 0;
        for (double v : wt) sum += v;
        double x = r.nextDouble() * sum;
        for (int i = 0; i < wt.length; i++) { x -= wt[i]; if (x <= 0) return Flaw.values()[i]; }
        return Flaw.TRANSVERSE;
    }

    /** the open (not yet repaired) rail defects in the track piece at this rail block */
    public static List<AssetData.Defect> openAt(World w, BlockPos rail) {
        List<AssetData.Defect> out = new ArrayList<>();
        TileRail piece = Rails.pieceAt(w, rail);
        if (piece == null) return out;
        AssetData d = AssetData.get(w);
        AssetData.Asset a = d.at(AssetKind.TRACK, Rails.pos(piece).toLong());
        if (a == null) return out;
        for (AssetData.Defect df : d.defectsOf(a.id)) if (df.stage < AssetData.REPAIRED) out.add(df);
        return out;
    }

    /** a repair done on the track (grinding, welding): REPAIRED; the machine's own ultrasonic check verifies it */
    public static void repaired(World w, AssetData.Defect df, String how, boolean verified) {
        df.step(w, AssetData.REPAIRED, how);
        if (verified) df.step(w, AssetData.VERIFIED, "ultrasonic check after the repair: clean");
        com.dogpound.railmap.alarm.AlarmManager.get(w).clear("defect:" + df.id);
        AssetData.get(w).markDirty();
    }

    // ---- the ultrasonic test train --------------------------------------------------------------------------------------
    private static final Map<UUID, Object[]> RUN = new HashMap<>();   // car -> {Set<Long> tested, int indications, int skipped, long last tick, String name}

    public static boolean isTestTrain(String tag) { return tag.contains("UTU") || tag.contains("ULTRASONIC") || tag.contains("SPERRY"); }

    /** once a second for each tagged car: test the piece under it */
    @SuppressWarnings("unchecked")
    static void testCar(World w, UUID car, String name, BlockPos rail, double kmh, long now) {
        Object[] r = RUN.computeIfAbsent(car, k -> new Object[]{new HashSet<Long>(), 0, 0, now, name});
        if (now - (Long) r[3] > 1200) { finishRun(w, car, r); r = new Object[]{new HashSet<Long>(), 0, 0, now, name}; RUN.put(car, r); }
        r[3] = now;
        TileRail piece = Rails.pieceAt(w, rail);
        if (piece == null || kmh < 0.5) return;
        long key = Rails.pos(piece).toLong();
        Set<Long> done = (Set<Long>) r[0];
        if (!done.add(key)) return;
        if (kmh > InfraConfig.testTrainMaxKmh) { r[2] = (Integer) r[2] + 1; return; }
        List<String> out = com.dogpound.railmap.asset.Ndt.testTrainPass(w, Rails.pos(piece), name + " (test train)");
        if (out == null) return;
        for (String s : out) if (s.contains("INDICATION") || s.contains("CONFIRMED")) { r[1] = (Integer) r[1] + 1; tell(w, rail, "§b[UTU] " + name + ": " + s.replaceAll("§.", "")); }
    }

    /** cars that stopped testing: write up their runs */
    static void sweep(World w, long now) {
        for (Iterator<Map.Entry<UUID, Object[]>> it = RUN.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Object[]> e = it.next();
            if (now - (Long) e.getValue()[3] > 1200) { finishRun(w, e.getKey(), e.getValue()); it.remove(); }
        }
    }

    @SuppressWarnings("unchecked")
    private static void finishRun(World w, UUID car, Object[] r) {
        int n = ((Set<Long>) r[0]).size(), ind = (Integer) r[1], skip = (Integer) r[2];
        if (n == 0) return;
        String line = com.dogpound.railmap.alarm.Recorder.clock(w, w.getTotalWorldTime()) + " " + r[4] + ": " + (n - skip) + " pieces tested, " + ind + " indication(s)"
                + (skip > 0 ? ", " + skip + " NOT TESTED (over " + InfraConfig.testTrainMaxKmh + " km/h)" : "");
        InfraData d = InfraData.get(w);
        d.utLog.addFirst(line);
        while (d.utLog.size() > 30) d.utLog.removeLast();
        d.markDirty();
        com.dogpound.railmap.alarm.Recorder.event(w, "Ultrasonic test train " + line);
    }

    static void tell(World w, BlockPos p, String msg) {
        for (net.minecraft.entity.player.EntityPlayer pl : w.playerEntities)
            if (pl.getDistanceSq(p) < 48 * 48) pl.sendMessage(new net.minecraft.util.text.TextComponentString(msg));
    }

    // ---- console ---------------------------------------------------------------------------------------------------------
    /** RF id · where x,z · class · size mm ± · critical · stage · remedy · found by ; UT run log lines */
    public static void rows(World w, List<String[]> out) {
        AssetData d = AssetData.get(w);
        int n = 0;
        for (AssetData.Defect df : d.defects) {
            AssetData.Asset a = d.byId(df.asset);
            if (a == null || a.kind != AssetKind.TRACK || df.stage == AssetData.LATENT || df.stage == AssetData.VERIFIED) continue;
            if (n++ > 120) break;
            Flaw f = of(df.type);
            BlockPos p = a.at();
            out.add(new String[]{"RF", String.valueOf(df.id), p.getX() + "," + p.getZ(), f == null ? df.type : f.code + " " + f.label, String.format(Locale.ROOT, "%.1f ±%.1f", df.seen, df.unc),
                    String.format(Locale.ROOT, "%.0f", df.crit), AssetData.STAGES[df.stage], f == null ? "REPAIR" : f.remedy, df.by + (df.method.isEmpty() ? "" : " / " + df.method)});
        }
        for (String s : InfraData.get(w).utLog) out.add(new String[]{"UT", s});
    }
}
