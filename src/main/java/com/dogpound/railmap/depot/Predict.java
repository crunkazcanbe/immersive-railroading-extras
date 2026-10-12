package com.dogpound.railmap.depot;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.server.WearData;
import com.dogpound.railmap.sim.SimData;
import net.minecraft.world.World;

import java.util.Locale;

/**
 * Scheduled and predictive maintenance (her ChatGPT list #51, #52). An exam falls due by whichever of its plan's limits
 * runs out first: km, operating hours, calendar days or faults since the last exam. The remaining useful life of each
 * part is a least-squares line through its wear trend (wear against km, a sample every half km), steepened by how it
 * is being used: an engine or traction motors running hot wear faster, a wheel flat hammers wheels and bearings, a
 * fault-prone unit's electrics are suspect. RUL = what is left to 100 % over that rate.
 */
public final class Predict {
    private Predict() {}

    public static void rul(World w, FleetData.Unit u, WearData.Record r, SimData.Car c) {
        if (u.rul == null || u.rul.length != 5) u.rul = new double[5];
        java.util.Arrays.fill(u.rul, -1);
        if (r == null || u.trend.size() < 3) return;
        double[] k = factors(u, c);
        for (int p = 0; p < 5; p++) {
            double n = 0, sx = 0, sy = 0, sxx = 0, sxy = 0;
            for (float[] t : u.trend) { n++; sx += t[0]; sy += t[p + 1]; sxx += t[0] * (double) t[0]; sxy += t[0] * (double) t[p + 1]; }
            double den = n * sxx - sx * sx;
            if (Math.abs(den) < 1e-9) continue;
            double slope = (n * sxy - sx * sy) / den * k[p];          // % per km
            if (slope <= 1e-6) continue;
            u.rul[p] = Math.max(0, (100 - r.wear[p]) / slope);
        }
        alarm(w, u, r);
    }

    /** how hard it is being used: wear-rate multipliers per part */
    public static double[] factors(FleetData.Unit u, SimData.Car c) {
        double[] k = {1, 1, 1, 1, 1};
        if (c != null) {
            if (!Double.isNaN(c.tmC) && c.tmC > 110) { k[Condition.EL] *= 1.4; k[Condition.E] *= 1.2; }
            if (c.oilKpa > 0 && c.oilKpa < 150) k[Condition.E] *= 1.3;
            if (c.flatMm >= 15) { k[Condition.W] *= 1.5; k[Condition.BR] *= 1.4; }
        }
        int sinceExam = u.faults - u.examFaults;
        if (sinceExam >= 3) { k[Condition.EL] *= 1.4; k[Condition.E] *= 1.2; }
        return k;
    }

    private static void alarm(World w, FleetData.Unit u, WearData.Record r) {
        if (DepotConfig.predictAlarmKm <= 0) return;
        AlarmManager am = AlarmManager.get(w);
        for (int p = 0; p < 5; p++) {
            String key = "pred:" + u.name + ":" + p;
            if (u.rul[p] >= 0 && u.rul[p] < DepotConfig.predictAlarmKm && r.wear[p] < 100)
                am.raise(w, key, AlarmManager.Cat.MACHINE, AlarmManager.Sev.INFO, String.format(Locale.ROOT, "PREDICTED: %s %s fails in about %.1f km (now %d%%) - plan a repair",
                        u.name, WearData.Part.values()[p].label, u.rul[p], Math.round(r.wear[p])), null, true);
            else if (u.rul[p] < 0 || u.rul[p] > DepotConfig.predictAlarmKm * 2 || r.wear[p] >= 100) am.clear(key);
        }
    }

    /** "" = not due, else why */
    public static String due(World w, FleetData f, FleetData.Unit u) {
        int[] p = f.plan(u);
        StringBuilder b = new StringBuilder();
        double km = u.km - u.examKm, hrs = u.hours - u.examHours, days = (w.getTotalWorldTime() - u.examTick) / 24000.0;
        int faults = u.faults - u.examFaults;
        if (p[0] > 0 && km >= p[0]) b.append(String.format(Locale.ROOT, "%.0f km", km));
        if (p[1] > 0 && hrs >= p[1]) b.append(b.length() > 0 ? ", " : "").append(String.format(Locale.ROOT, "%.0f h", hrs));
        if (p[2] > 0 && days >= p[2]) b.append(b.length() > 0 ? ", " : "").append(String.format(Locale.ROOT, "%.0f days", days));
        if (p[3] > 0 && faults >= p[3]) b.append(b.length() > 0 ? ", " : "").append(faults).append(" faults");
        return b.length() == 0 ? "" : b + " since last exam";
    }

    /** how much of the plan is used, 0..1+ (the worst limit) */
    public static double used(World w, FleetData f, FleetData.Unit u) {
        int[] p = f.plan(u);
        double m = 0;
        if (p[0] > 0) m = Math.max(m, (u.km - u.examKm) / p[0]);
        if (p[1] > 0) m = Math.max(m, (u.hours - u.examHours) / p[1]);
        if (p[2] > 0 && u.examTick > 0) m = Math.max(m, (w.getTotalWorldTime() - u.examTick) / 24000.0 / p[2]);
        if (p[3] > 0) m = Math.max(m, (u.faults - u.examFaults) / (double) p[3]);
        return m;
    }

    /** the part that will fail first and in how many km: {part, km} or null */
    public static double[] first(FleetData.Unit u) {
        if (u.rul == null) return null;
        double[] best = null;
        for (int p = 0; p < u.rul.length; p++) if (u.rul[p] >= 0 && (best == null || u.rul[p] < best[1])) best = new double[]{p, u.rul[p]};
        return best;
    }
}
