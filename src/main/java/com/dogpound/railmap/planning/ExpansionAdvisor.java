package com.dogpound.railmap.planning;

import com.dogpound.railmap.grid.GridData;
import com.dogpound.railmap.grid.GridKind;
import com.dogpound.railmap.incident.TwinSim;
import com.dogpound.railmap.infra.InfraConfig;
import com.dogpound.railmap.ohle.Ohle;
import com.dogpound.railmap.ohle.OhleData;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Automatic railway expansion advisor (her ChatGPT list batch 3, #50 - extends the what-if desk, WhatIf, with the verb
 * {@code expand}). It runs the digital twin of today's railway, finds where it hurts and tries the cures, each run on
 * the twin and costed with the planning prices:
 * <ul>
 *   <li>TRACK - every bottleneck stretch: double it if single (or a passing loop at InfraConfig.loopCostShare of the
 *   cost), CBTC for the lines through it if already double;</li>
 *   <li>SERVICES - one more train on the line with the worst delay, and on the busiest line;</li>
 *   <li>POWER - Track Feeders whose network sags (voltage under 0.92 pu), OHLE sections fed from one end only that are
 *   longer than the feeder's reach (a new feeder substation at the far end, InfraConfig.costSubstation), and DC
 *   networks that burn their regenerative braking in resistors (a wayside energy store, #38, InfraConfig.costEss).</li>
 * </ul>
 * Each recommendation gets its train-minutes of delay saved per hour (power: the undervoltage or energy it fixes), its
 * cost and its value per $1000; the best is DO FIRST. The answer lands in the what-if table like any other question.
 */
public final class ExpansionAdvisor {
    private ExpansionAdvisor() {}

    private static final class Rec { String what, why, verdict = ""; double cost, value; }

    /** delay (train-minutes) saved per hour by r against r0, departures gained counting 2 train-minutes each */
    static double saved(TwinSim.Result r0, TwinSim.Result r) {
        double before = r0.avgDelay() * r0.deps / 60, after = r.avgDelay() * r.deps / 60;
        return (before - after + 2 * (r.deps - r0.deps)) * 60 / Math.max(1, r.minutes);
    }

    static String advise(World w, TwinSim.Model m, PlanData d, TwinSim.Case base) {
        TwinSim.Result r0 = TwinSim.run(m, base);
        List<Rec> recs = new ArrayList<>();
        // ---- track: the bottlenecks
        for (String[] bn : r0.bottlenecks(m, 4)) {
            String seg = bn[0];
            long[] e = m.segments().get(seg);
            if (e == null) continue;
            double km = m.km(e[0], e[1]);
            if (m.tracks(seg) <= 1) {
                TwinSim.Case c = TwinSim.copy(base);
                c.segTracks.put(seg, 2);
                double s = saved(r0, TwinSim.run(m, c));
                Rec dbl = rec("double track " + seg, String.format(Locale.ROOT, "bottleneck: %s train-min waiting, %s%% occupied; %.2f km", bn[1], bn[2], km), km * PlanConfig.costDoublePerKm, s);
                recs.add(dbl);
                // ponytail: a mid-section loop is taken as 60 % of doubling's benefit, run the full twin per loop site if it matters
                recs.add(rec("passing loop on " + seg, "a crossing loop mid-way instead of doubling it all", km * PlanConfig.costDoublePerKm * InfraConfig.loopCostShare, s * 0.6));
            } else {
                Set<String> lines = new TreeSet<>();
                for (Map.Entry<String, long[]> l : m.lines.entrySet()) for (int i = 0; i + 1 < l.getValue().length; i++) if (m.seg(l.getValue()[i], l.getValue()[i + 1]).equals(seg)) lines.add(l.getKey());
                lines.removeAll(m.cbtc);
                if (lines.isEmpty()) continue;
                TwinSim.Case c = TwinSim.copy(base);
                c.cbtcLines.addAll(lines);
                recs.add(rec("CBTC on " + String.join(", ", lines), "double-track bottleneck " + seg + ": shorter headways instead of more track", lines.size() * PlanConfig.costCbtcPerLine, saved(r0, TwinSim.run(m, c))));
            }
        }
        // ---- services
        String worst = null, busiest = null;
        double wd = -1, bd = -1;
        for (Map.Entry<String, Double> e : r0.lineDelay.entrySet()) { double v = e.getValue() / Math.max(1, r0.perLine.getOrDefault(e.getKey(), 1.0)); if (v > wd) { wd = v; worst = e.getKey(); } }
        for (Map.Entry<String, Double> e : r0.perLine.entrySet()) if (e.getValue() > bd) { bd = e.getValue(); busiest = e.getKey(); }
        for (String line : new LinkedHashSet<>(Arrays.asList(worst, busiest))) {
            if (line == null) continue;
            TwinSim.Case c = TwinSim.copy(base);
            c.trainsDelta.put(line, 1);
            recs.add(rec("one more train on " + line, line.equals(worst) ? String.format(Locale.ROOT, "worst delays (%.0f s a departure)", wd) : "the busiest line", PlanConfig.costTrain, saved(r0, TwinSim.run(m, c))));
        }
        // ---- power
        power(w, recs);
        // ---- rank
        for (Rec r : recs) r.value = r.cost <= 0 ? r.value : r.value / (r.cost / 1000.0);
        recs.sort((a, b) -> Double.compare(b.value, a.value));
        d.lastWhatIf.add(new String[]{"QA", "today: punctuality " + f(r0.punctual()) + " %, " + f(r0.deps) + " departures, average delay " + f(r0.avgDelay()) + " s over " + Math.round(r0.minutes) + " min"});
        d.lastWhatIf.add(new String[]{"QA", "track / services: train-minutes of delay saved per hour on the twin (2 per extra departure); power: % undervoltage or kWh/h fixed"});
        d.lastWhatIf.add(new String[]{"QA", "prices: double track $" + PlanConfig.costDoublePerKm + "/km, loop " + Math.round(InfraConfig.loopCostShare * 100) + " % of that, train $" + PlanConfig.costTrain
                + ", CBTC $" + PlanConfig.costCbtcPerLine + "/line, substation $" + InfraConfig.costSubstation + ", wayside ESS $" + InfraConfig.costEss});
        int n = 0;
        for (Rec r : recs) {
            if (n++ >= 12) break;
            String v = n == 1 && r.value > 0 ? "DO FIRST" : r.value > 0.5 ? "GOOD" : r.value > 0 ? "MARGINAL" : "NO";
            d.lastWhatIf.add(new String[]{"QT", r.what, "$" + Math.round(r.cost), r.why + String.format(Locale.ROOT, " - value %.2f per $1000", r.value), v});
        }
        String head = recs.isEmpty() || recs.get(0).value <= 0 ? "NOTHING WORTH BUILDING YET" : "DO FIRST: " + recs.get(0).what;
        d.lastWhatIf.add(0, new String[]{"QH", "expand", head});
        return "§bExpansion advisor: §f" + head + " §7(" + recs.size() + " options costed - console PLAN > What-if)";
    }

    private static Rec rec(String what, String why, double cost, double value) {
        Rec r = new Rec();
        r.what = what; r.why = why + String.format(Locale.ROOT, "; saves %.1f train-min/h", value); r.cost = cost; r.value = value;
        return r;
    }

    /** sagging feeders, one-end-fed long sections, burnt regen */
    static void power(World w, List<Rec> recs) {
        GridData g = GridData.get(w);
        for (Map.Entry<Long, GridData.Node> en : g.nodes().entrySet()) {
            GridData.Node n = en.getValue();
            if (n.kind != GridKind.FEEDER) continue;
            GridData.Net net = g.netOf(BlockPos.fromLong(en.getKey()));
            if (net == null || net.level == null || n.pu >= 0.92 || n.drawLast <= 0) continue;
            Rec r = rec("feeder substation beside " + Ohle.xz(BlockPos.fromLong(en.getKey())), String.format(Locale.ROOT, "the network sags to %.2f pu under load (%.0f kW)", n.pu, n.drawLast / 10.0),
                    InfraConfig.costSubstation, (1 - n.pu) * 100);
            r.why = r.why.replaceAll("; saves.*", String.format(Locale.ROOT, "; lifts it %.0f%%", (1 - n.pu) * 100));
            recs.add(r);
        }
        for (Ohle.Section s : Ohle.known(w)) {
            if (s.feeders.size() != 1 || s.legacy()) continue;
            GridData.Node n = g.nodes().get(s.feeders.get(0));
            if (n == null || s.wires.size() < n.range * 1.5) continue;
            BlockPos fp = BlockPos.fromLong(s.feeders.get(0));
            long far = s.anchor;
            double fd = -1;
            for (long l : s.wires) { double dd = BlockPos.fromLong(l).distanceSq(fp); if (dd > fd) { fd = dd; far = l; } }
            double drop = 0.4 * Math.min(1, Math.sqrt(fd) / Math.max(1, n.range));
            Rec r = rec("second feeder at the far end of " + s.name() + " (" + Ohle.xz(BlockPos.fromLong(far)) + ")",
                    String.format(Locale.ROOT, "%d m of wire fed from one end: trains at the far end get %.0f%% less; double-end feeding shares the load (#37)", s.wires.size(), drop * 100), InfraConfig.costSubstation, drop * 100);
            r.why = r.why.replaceAll("; saves.*", "");
            recs.add(r);
        }
        OhleData od = OhleData.get(w);
        double burnt = 0, hours = 0;
        for (OhleData.Period p : od.periods) { burnt += p.kwhRes; hours += Math.max(1, ((p.end == 0 ? w.getTotalWorldTime() : p.end) - p.start)) / 72000.0; }
        if (burnt > 20) {
            double perH = burnt / Math.max(0.1, hours);
            Rec r = rec("wayside energy store (#38) on the busiest DC network", String.format(Locale.ROOT, "%.0f kWh/h of braking energy burnt in resistors - a store catches it and gives it back", perH),
                    InfraConfig.costEss, perH * PlanConfig.priceKwh * 10);
            r.why = r.why.replaceAll("; saves.*", String.format(Locale.ROOT, "; saves $%.1f/h", perH * PlanConfig.priceKwh));
            recs.add(r);
        }
    }

    static String f(double v) { return String.format(Locale.ROOT, "%.1f", v); }
}
