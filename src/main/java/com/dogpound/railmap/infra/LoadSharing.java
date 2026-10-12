package com.dogpound.railmap.infra;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.grid.GridData;
import com.dogpound.railmap.grid.GridKind;
import com.dogpound.railmap.ohle.Ohle;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Parallel substation load sharing (her ChatGPT list batch 3, #37). A train between two Track Feeders that both reach it
 * is fed from both ends (double-end feeding), and the current divides the way it does on a real line: inversely with
 * each path's impedance (the substation's own source impedance plus the line from the feeder to the train) and in
 * proportion to each end's voltage - a feeder whose transformer sits on a higher tap picks up more of the load.
 * GridTicker.supply asks {@link #shares} for the split (the first pass takes each feeder's share, a second pass makes
 * up anything a weak end couldn't give). Off (InfraConfig.loadSharing) = the old first-feeder-found behaviour.
 * <p>
 * Feeders of the same OHLE section at different voltages drive a CIRCULATING CURRENT through the wire between them
 * even with no train about (a tap mismatch) - the console shows it and an alarm says so. Feeders of one section fed
 * from different grid networks are a phase clash waiting to happen: that is what neutral sections are for (#36).
 */
public final class LoadSharing {
    private LoadSharing() {}

    /** source impedance of a feeder substation, per unit of its section's line impedance */
    static final double Z_SOURCE = 0.15;

    private static final Map<Integer, Map<Long, double[]>> NOW = new HashMap<>(), LAST = new HashMap<>();
    private static final Map<Integer, Long> SEC = new HashMap<>();

    private static Map<Long, double[]> now(World w) {
        int dim = w.provider.getDimension();
        long s = w.getTotalWorldTime() / 20;
        if (!Objects.equals(SEC.get(dim), s)) {
            LAST.put(dim, NOW.getOrDefault(dim, new HashMap<>()));
            NOW.put(dim, new HashMap<>());
            SEC.put(dim, s);
        }
        return NOW.get(dim);
    }

    /** the split of one train's demand across the feeders that can reach it (null: one feeder, or sharing off) */
    public static double[] shares(World w, GridData d, List<Map.Entry<Long, GridData.Node>> cands, BlockPos at, boolean ac) {
        now(w);
        if (!InfraConfig.loadSharing || cands.size() < 2) return null;
        double[] wt = new double[cands.size()];
        double sum = 0;
        for (int i = 0; i < wt.length; i++) {
            GridData.Node n = cands.get(i).getValue();
            BlockPos p = BlockPos.fromLong(cands.get(i).getKey());
            GridData.Net net = d.netOf(p);
            double v = net == null || !GridData.why(net, ac).isEmpty() ? 0 : volts(n, net);
            wt[i] = v / impedance(Math.hypot(p.getX() - at.getX(), p.getZ() - at.getZ()), n.range);
            sum += wt[i];
        }
        if (sum <= 0) return null;
        for (int i = 0; i < wt.length; i++) wt[i] /= sum;
        return wt;
    }

    /** path impedance, per unit: the substation plus the line out to the train (the section's reach = 1) */
    public static double impedance(double dist, int range) { return Z_SOURCE + dist / Math.max(1, range); }

    static double volts(GridData.Node n, GridData.Net net) { return net.level != null ? Math.max(0, n.pu) : Math.max(0, net.volt); }

    /** GridTicker.supply: this feeder delivered fe to a train this second (share = the split it was given, 1 = alone) */
    public static void record(World w, long feeder, long fe, double share) {
        double[] r = now(w).computeIfAbsent(feeder, k -> new double[3]);
        r[0] += fe; r[1]++; r[2] += share;
    }

    /** last second: {FE delivered, trains, sum of shares} */
    static double[] last(World w, long feeder) {
        now(w);
        return LAST.getOrDefault(w.provider.getDimension(), Collections.emptyMap()).getOrDefault(feeder, new double[3]);
    }

    // ---- sections fed from more than one end ---------------------------------------------------------------------------
    public static final class Group {
        public String name = "";
        public final List<Long> feeders = new ArrayList<>();
        public double spreadPct, circPct;
        public boolean mixedNets;
    }

    /** every OHLE section with two or more feeders, with its voltage spread and circulating current */
    public static List<Group> groups(World w) {
        GridData d = GridData.get(w);
        List<Group> out = new ArrayList<>();
        for (Ohle.Section s : Ohle.known(w)) {
            if (s.feeders.size() < 2) continue;
            Group g = new Group();
            g.name = s.name();
            g.feeders.addAll(s.feeders);
            double lo = 9, hi = 0, far = 0;
            Set<Integer> nets = new HashSet<>();
            for (long f : s.feeders) {
                GridData.Node n = d.nodes().get(f);
                GridData.Net net = d.netOf(BlockPos.fromLong(f));
                if (n == null || net == null) continue;
                nets.add(net.id);
                double v = volts(n, net);
                lo = Math.min(lo, v); hi = Math.max(hi, v);
                for (long o : s.feeders) far = Math.max(far, BlockPos.fromLong(f).getDistance(BlockPos.fromLong(o).getX(), BlockPos.fromLong(o).getY(), BlockPos.fromLong(o).getZ()));
            }
            g.mixedNets = nets.size() > 1;
            g.spreadPct = hi > lo ? (hi - lo) * 100 : 0;
            int range = 64;
            for (long f : s.feeders) { GridData.Node n = d.nodes().get(f); if (n != null) range = Math.max(range, n.range); }
            g.circPct = g.spreadPct / (2 * Z_SOURCE + far / range);           // I = dV / (Zs1 + Zs2 + Zline), % of a feeder's full load
            out.add(g);
        }
        return out;
    }

    /** every 5 s: tap-mismatch and mixed-supply alarms */
    static void check(World w) {
        AlarmManager am = AlarmManager.get(w);
        Set<String> live = new HashSet<>();
        for (Group g : groups(w)) {
            String k = "lshare:" + g.name;
            boolean bad = g.spreadPct > InfraConfig.circulatingAlarmPct || g.mixedNets;
            if (bad) {
                live.add(k);
                boolean on = false;
                for (AlarmManager.Alarm a : am.all()) if (a.key.equals(k) && a.active) on = true;
                if (!on) am.raise(w, k, AlarmManager.Cat.POWER, AlarmManager.Sev.MINOR, g.mixedNets
                        ? "PARALLEL FEED " + g.name + ": fed from different grid networks - a phase clash risk, split it with a neutral section"
                        : String.format(Locale.ROOT, "PARALLEL FEED %s: feeder voltages differ %.1f%% - circulating current ~%.0f%% of a feeder's load (tap mismatch)", g.name, g.spreadPct, g.circPct), null, true);
            } else am.clear(k);
        }
    }

    // ---- console ----------------------------------------------------------------------------------------------------------
    /** LS section · feeder · kW · share % · volts pu · network · trains · spread % · circulating % · note */
    public static void rows(World w, List<String[]> out) {
        GridData d = GridData.get(w);
        Set<Long> grouped = new HashSet<>();
        for (Group g : groups(w)) {
            double tot = 0;
            for (long f : g.feeders) tot += last(w, f)[0];
            for (long f : g.feeders) {
                grouped.add(f);
                out.add(row(w, d, g.name, f, tot, g));
            }
        }
        for (Map.Entry<Long, GridData.Node> en : d.nodes().entrySet()) {
            if (en.getValue().kind != GridKind.FEEDER || grouped.contains(en.getKey())) continue;
            if (out.size() > 400) break;
            out.add(row(w, d, "single-end", en.getKey(), last(w, en.getKey())[0], null));
        }
    }

    private static String[] row(World w, GridData d, String sec, long f, double secTotal, Group g) {
        GridData.Node n = d.nodes().get(f);
        GridData.Net net = d.netOf(BlockPos.fromLong(f));
        double[] l = last(w, f);
        double kw = l[0] * 100 / 1000;                                        // 1 FE/s = 100 W
        String note = g == null ? "" : g.mixedNets ? "MIXED SUPPLIES" : g.spreadPct > InfraConfig.circulatingAlarmPct ? "TAP MISMATCH" : "paralleled";
        return new String[]{"LS", sec, Ohle.xz(BlockPos.fromLong(f)), f1(kw), secTotal > 0 ? String.valueOf(Math.round(l[0] * 100 / secTotal)) : "-",
                n == null || net == null ? "-" : String.format(Locale.ROOT, "%.3f", volts(n, net)), net == null ? "-" : "N" + net.id, String.valueOf((int) l[1]),
                g == null ? "-" : f1(g.spreadPct), g == null ? "-" : f1(g.circPct), note};
    }

    static String f1(double v) { return String.format(Locale.ROOT, "%.1f", v); }
}
