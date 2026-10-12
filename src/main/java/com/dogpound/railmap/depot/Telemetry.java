package com.dogpound.railmap.depot;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.LocomotiveDiesel;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.server.WearData;
import com.dogpound.railmap.sim.EngineSim;
import com.dogpound.railmap.sim.SimData;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fleet condition monitoring (her 48-next list #5): every 30 s each locomotive's telemetry is recorded with its
 * operating hours - coolant, traction-motor temperature, oil pressure, battery volts, the worst coupler knuckle and the
 * active fault count (the last 48 samples are kept with the fleet records). A least-squares trend over the last twelve
 * samples (per operating hour) catches what is drifting before it trips, and gives the predictive P-codes:
 * <pre>
 *   P101 coolant temperature rising        P102 traction-motor temperature rising
 *   P201 oil pressure falling               P301 battery voltage declining
 *   P401 coupler knuckle wear accelerating  P501 fault rate rising
 *   P6xx a part's remaining life short (from the wear trend, Predict) in km and operating hours
 * </pre>
 * They show on the Loco Diagnostic Panel (TREND / P-CODES pages), the console's ASSETS > Fleet health view and as one
 * INFO alarm per unit. Engine fault codes (E1xx, A5xx...) come from the engine and auxiliary models as before.
 */
public final class Telemetry {
    private Telemetry() {}

    public static final int KEEP = 48, WINDOW = 12;
    public static final String[] CH = {"COOLANT C", "TM C", "OIL KPA", "BATT V", "COUPLER %", "FAULTS"};

    /** DepotTicker.second: a sample every 30 s (staggered per unit) */
    static void sample(World w, FleetData.Unit u, EntityRollingStock s, long now) {
        if (!com.dogpound.railmap.asset.AssetConfig.telemetry || !u.loco) return;
        if (((now / 20) + (s.getUUID().hashCode() & 31)) % 30 != 0) return;
        SimData.Car c = SimData.get(w).peek(s.getUUID());
        float eng = Float.NaN, tm = Float.NaN, oil = Float.NaN, bat = Float.NaN, cpl = 100, flt = 0;
        if (s instanceof LocomotiveDiesel d) { eng = d.getEngineTemperature(); }
        if (c != null) {
            if (!Double.isNaN(c.tmC)) tm = (float) c.tmC;
            if (c.oilKpa > 0) oil = (float) c.oilKpa;
            try { bat = (float) EngineSim.volts(c); } catch (RuntimeException ignored) { }
            cpl = Math.round(Math.min(c.coupler[0], c.coupler[1]) * 100);
            flt = c.faults.size();
        }
        u.tele.add(new float[]{(float) u.hours, eng, tm, oil, bat, cpl, flt});
        while (u.tele.size() > KEEP) u.tele.remove(0);
        u.pcodes = codes(u, WearData.get(w).peek(s.getUUID()));
        alarm(w, u);
    }

    /** least-squares slope of a channel over the window, per operating hour (NaN = not enough data) */
    public static double slope(FleetData.Unit u, int ch) {
        int n0 = Math.max(0, u.tele.size() - WINDOW);
        double n = 0, sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (int i = n0; i < u.tele.size(); i++) {
            float[] f = u.tele.get(i);
            if (f.length <= ch + 1 || Float.isNaN(f[ch + 1])) continue;
            n++; sx += f[0]; sy += f[ch + 1]; sxx += (double) f[0] * f[0]; sxy += (double) f[0] * f[ch + 1];
        }
        double den = n * sxx - sx * sx;
        if (n < 4 || Math.abs(den) < 1e-6) return Double.NaN;
        return (n * sxy - sx * sy) / den;
    }

    /** the latest value of a channel (NaN = none) */
    public static double last(FleetData.Unit u, int ch) {
        for (int i = u.tele.size() - 1; i >= 0; i--) { float[] f = u.tele.get(i); if (f.length > ch + 1 && !Float.isNaN(f[ch + 1])) return f[ch + 1]; }
        return Double.NaN;
    }

    public static List<String> codes(FleetData.Unit u, WearData.Record r) {
        List<String> o = new ArrayList<>();
        double s;
        if (!Double.isNaN(s = slope(u, 0)) && s > 3 && last(u, 0) > 70) o.add(String.format(Locale.ROOT, "P101 COOLANT RISING +%.1f C/H", s));
        if (!Double.isNaN(s = slope(u, 1)) && s > 6 && last(u, 1) > 90) o.add(String.format(Locale.ROOT, "P102 TM TEMP RISING +%.1f C/H", s));
        if (!Double.isNaN(s = slope(u, 2)) && s < -8) o.add(String.format(Locale.ROOT, "P201 OIL PRESS FALLING %.0f KPA/H", s));
        if (!Double.isNaN(s = slope(u, 3)) && s < -0.15) o.add(String.format(Locale.ROOT, "P301 BATTERY DECLINING %.2f V/H", s));
        if (!Double.isNaN(s = slope(u, 4)) && s < -1) o.add(String.format(Locale.ROOT, "P401 KNUCKLE WEAR %.1f %%/H", s));
        if (!Double.isNaN(s = slope(u, 5)) && s > 0.3) o.add(String.format(Locale.ROOT, "P501 FAULT RATE RISING +%.1f/H", s));
        if (u.rul != null && r != null) {
            double kmPerH = u.hours > 0.5 ? u.km / u.hours : 20;
            for (int p = 0; p < Math.min(5, u.rul.length); p++)
                if (u.rul[p] >= 0 && u.rul[p] < DepotConfig.predictAlarmKm * 2 && r.wear[p] < 100)
                    o.add(String.format(Locale.ROOT, "P6%02d %s LIFE %.1f KM ~%.1f H", p, WearData.Part.values()[p].label.toUpperCase(Locale.ROOT), u.rul[p], u.rul[p] / Math.max(1, kmPerH)));
        }
        return o;
    }

    /** remaining life of the part that fails first, in operating hours (-1 = unknown) */
    public static double rulHours(FleetData.Unit u) {
        double[] f = Predict.first(u);
        if (f == null) return -1;
        double kmPerH = u.hours > 0.5 ? u.km / u.hours : 20;
        return f[1] / Math.max(1, kmPerH);
    }

    private static void alarm(World w, FleetData.Unit u) {
        String key = "pcode:" + u.name;
        if (com.dogpound.railmap.asset.AssetConfig.telemetryAlarms && !u.pcodes.isEmpty())
            com.dogpound.railmap.asset.AssetTicker.standing(w, key, AlarmManager.Cat.MACHINE, AlarmManager.Sev.INFO, "TREND " + u.name + ": " + String.join(" · ", u.pcodes), null);
        else AlarmManager.get(w).clear(key);
    }
}
