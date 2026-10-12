package com.dogpound.railmap.planning;

import com.dogpound.railmap.incident.TwinSim;
import net.minecraft.world.World;

import java.util.*;

/**
 * Timetable robustness + delay propagation (her "next 48" list #6), on the twin's shadow simulation. The live
 * timetable is played three ways - TIGHT (no recovery allowance), AS RUN (the allowance the analytics book against) and
 * RESILIENT (twice that) - and each is hit with the same 3-minute delay to the first train of the busiest line. For each:
 * punctuality, average delay, connections kept and the smallest connection margin, platform conflicts, and how far the
 * small delay CASCADES: how many other trains it knocked on to, the extra train-minutes, how many stations it reached and
 * how many departures the late train needed to get back on time. Per interchange: connections planned / missed and the
 * margins; per station: platform conflicts.
 */
public final class Robustness {
    private Robustness() {}

    public static String study(World w, String who) {
        TwinSim.Model m = TwinSim.live(w);
        if (m.trains.isEmpty()) return "§7No driverless trains on lines - nothing to test";
        PlanData d = PlanData.get(w);
        Map<String, Integer> n = new HashMap<>();
        for (TwinSim.Train t : m.trains) n.merge(t.line, 1, Integer::sum);
        String busiest = Collections.max(n.entrySet(), Map.Entry.comparingByValue()).getKey();
        String victim = "";
        for (TwinSim.Train t : m.trains) if (t.line.equals(busiest)) { victim = t.name; break; }
        double[] recs = {0.0, 0.10, 0.20};
        String[] names = {"TIGHT", "AS RUN", "RESILIENT"};
        d.lastRobust.clear();
        TwinSim.Result asRun = null;
        double bestScore = -1;
        String best = "";
        for (int i = 0; i < recs.length; i++) {
            TwinSim.Case c = new TwinSim.Case();
            c.label = names[i]; c.recovery = recs[i]; c.minutes = 60; c.minConnect = PlanConfig.minConnectSeconds;
            TwinSim.Result r = TwinSim.run(m, c);
            TwinSim.Case hit = TwinSim.copy(c);
            hit.delayTrain = victim; hit.delayAt = 300; hit.delaySec = 180;
            double[] k = TwinSim.knockOn(m, hit);
            d.lastRobust.add(new String[]{"XT", names[i], String.format(Locale.ROOT, "%.0f", recs[i] * 100), String.format(Locale.ROOT, "%.1f", r.punctual()), String.format(Locale.ROOT, "%.0f", r.avgDelay()),
                    String.format(Locale.ROOT, "%.1f", r.connKept()), r.minMargin == Double.MAX_VALUE ? "-" : String.format(Locale.ROOT, "%.0f", r.minMargin), String.format(Locale.ROOT, "%.1f", r.platformConflicts),
                    String.format(Locale.ROOT, "%.1f", k[0]), String.format(Locale.ROOT, "%.1f", k[1]), String.format(Locale.ROOT, "%.0f", k[2]), String.format(Locale.ROOT, "%.1f", k[3]),
                    String.format(Locale.ROOT, "%.1f", r.deps)});
            double score = r.punctual() - k[1] * 2 + r.connKept() * 0.3 + r.deps * 0.05;
            if (score > bestScore) { bestScore = score; best = names[i]; }
            if (i == 1) asRun = r;
        }
        if (asRun != null) {
            for (Map.Entry<String, double[]> e : asRun.connAt.entrySet()) {
                double[] v = e.getValue();
                d.lastRobust.add(new String[]{"XC", e.getKey(), String.format(Locale.ROOT, "%.1f", v[0]), String.format(Locale.ROOT, "%.1f", v[1]),
                        String.format(Locale.ROOT, "%.0f", v[0] <= 0 ? 0 : v[2] / v[0]), v[3] == Double.MAX_VALUE ? "-" : String.format(Locale.ROOT, "%.0f", v[3])});
            }
            List<Map.Entry<String, Double>> pw = new ArrayList<>(asRun.stopWait.entrySet());
            pw.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            for (int i = 0; i < Math.min(8, pw.size()); i++) d.lastRobust.add(new String[]{"XP", pw.get(i).getKey(), String.format(Locale.ROOT, "%.1f", pw.get(i).getValue() / 60)});
        }
        d.lastRobust.add(new String[]{"XA", "3 min delay to " + victim + " (busiest line " + busiest + ") 5 min in; 60 min x 3 seeds; connection time " + PlanConfig.minConnectSeconds + " s; most robust: " + best});
        d.markDirty();
        com.dogpound.railmap.alarm.Recorder.event(w, "Timetable robustness study by " + who + ": most robust " + best);
        return "§bTimetable robustness: tight / as run / resilient compared - most robust: §f" + best;
    }
}
