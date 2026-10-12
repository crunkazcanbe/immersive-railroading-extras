package com.dogpound.railmap.asset;

import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.entity.LocomotiveDiesel;
import cam72cam.immersiverailroading.entity.LocomotiveSteam;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.depot.DepotTicker;
import com.dogpound.railmap.depot.FleetData;
import com.dogpound.railmap.sim.SimData;
import net.minecraft.world.World;

import java.util.*;

/**
 * Locomotive design and rebuild (her 48-next list #28). A design picks, per slot, one of these packages (or keeps the
 * as-built one). What it really changes, through the hooks the IR fork already has:
 * <ul>
 *   <li>power: the traction package's rating x how well the cooling keeps up with its heat x the gear's tractive-effort
 *   factor -> {@code prideDerate} (via the depot's condition factors, so engine management sees it too);</li>
 *   <li>cooling: the radiator / blower package against the motors' heat -> {@code prideCooling};</li>
 *   <li>brakes: block / disc / disc + rheostatic -> {@code prideBrakeFactor};</li>
 *   <li>top speed: the gear ratio and the bogie -> the depot's speed governor holds the train to it;</li>
 *   <li>mass: tanks, batteries, bogies, coolers -> every axle weighed by the track-wear model and the structure boards'
 *   axle-load checks; the bogie's curving behaviour -> track wear in curves.</li>
 * </ul>
 * A rebuilt locomotive is NOT ACCEPTED until its acceptance test: static checks at the workshop (no faults, brake test
 * certificate, insulation) and a test run of 2 km reaching 60 % of its design speed without a fault or overheating.
 * Until then (config rebuildAcceptance) it is held to 25 km/h.
 */
public final class Design {
    private Design() {}

    public static final String[] SLOTS = {"TRACTION", "GEAR", "COOLING", "STORAGE", "BOGIES", "BRAKES"};
    public static final String[][] NAMES = {
            {"AS BUILT", "DC SERIES REWOUND", "AC INDUCTION IGBT", "PM SYNCHRONOUS"},
            {"AS BUILT", "4.93 HEAVY FREIGHT", "4.21 MIXED TRAFFIC", "3.62 PASSENGER", "3.12 HIGH SPEED"},
            {"AS BUILT", "NATURAL AIR", "FORCED AIR", "FORCED + OIL COOLER"},
            {"AS BUILT", "SMALL TANK", "LARGE TANK", "BATTERY HYBRID", "BATTERY-ELECTRIC", "HYDROGEN FUEL CELL"},   // batch 5 #6: 4 BEMU, 5 hydrogen (traction.Traction)
            {"AS BUILT", "RIGID CO-CO", "STEERING RADIAL", "HIGH-SPEED FLEXICOIL"},
            {"AS BUILT", "COMPOSITION BLOCKS", "DISC", "DISC + RHEOSTATIC"}};
    // per option: traction {power, heat}, gear {speed, TE}, cooling {capacity}, storage {}, bogie {speed, track wear}, brake {force}
    static final double[] M_POWER = {1, 0.92, 1, 1}, M_HEAT = {1, 1.15, 0.95, 0.8};
    static final double[] G_SPEED = {1, 0.65, 0.8, 0.95, 1.0}, G_TE = {1, 1, 1, 0.95, 0.85};
    static final double[] C_CAP = {1, 0.75, 1.1, 1.35};
    static final double[] B_SPEED = {1, 0.9, 1, 1}, B_WEAR = {1, 1.25, 0.65, 0.9};
    static final double[] K_BRAKE = {1, 1, 1.12, 1.2};
    /** tonnes added per slot option */
    static final double[][] MASS = {{0, 1.5, -2, -3.5}, {0, 0.4, 0.2, 0, -0.2}, {0, -0.5, 0.4, 1.2}, {0, -3, 6, 12, 18, 9}, {0, 4, 2, -1}, {0, 0, 1, 2}};
    /** coins per slot option */
    static final int[][] COST = {{0, 8000, 26000, 38000}, {0, 6000, 6000, 6000, 7500}, {0, 1500, 4000, 9000}, {0, 2500, 5000, 22000, 60000, 85000}, {0, 30000, 42000, 36000}, {0, 3000, 12000, 18000}};

    static int opt(FleetData.Unit u, int slot) { int v = u.design[slot]; return v < 0 ? 0 : Math.min(NAMES[slot].length - 1, v); }

    public static boolean rebuilt(FleetData.Unit u) { for (int v : u.design) if (v > 0) return true; return false; }

    // ---- effects (Condition.work calls apply once a second per vehicle) ---------------------------------------------------
    public static void apply(FleetData.Unit u) {
        if (u.rebuild == 0 || !rebuilt(u)) return;
        double heat = M_HEAT[opt(u, 0)], cool = C_CAP[opt(u, 2)];
        double suff = Math.min(1, cool / heat);
        u.derateK *= (float) (M_POWER[opt(u, 0)] * suff * G_TE[opt(u, 1)]);
        u.coolK *= (float) (cool / heat);
        u.brakeK *= (float) K_BRAKE[opt(u, 5)];
        double v = speedKmh(u);
        if (v > 0 && v < u.cap) { u.cap = (float) v; u.capWhy = "design speed (" + NAMES[1][opt(u, 1)].toLowerCase(Locale.ROOT) + ")"; }
        if (AssetConfig.rebuildAcceptance && (u.rebuild == 1 || u.rebuild == 4) && 25 < u.cap) { u.cap = 25; u.capWhy = "rebuild NOT ACCEPTED - acceptance test due"; }
    }

    /** the design's top speed, 0 = as built / unknown */
    public static double speedKmh(FleetData.Unit u) {
        double k = G_SPEED[opt(u, 1)] * B_SPEED[opt(u, 4)];
        return u.baseKmh > 0 && k < 0.999 ? u.baseKmh * k : 0;
    }

    /** extra tonnes the design adds to the vehicle (track wear, axle-load checks) */
    public static double massT(UUID id) {
        FleetData.Unit u = DepotTicker.live(id);
        if (u == null || u.rebuild == 0) return 0;
        double t = 0;
        for (int s = 0; s < 6; s++) t += MASS[s][opt(u, s)];
        return t;
    }

    /** how hard the vehicle's bogies are on the track (curving) */
    public static double trackFactor(UUID id) {
        FleetData.Unit u = DepotTicker.live(id);
        return u == null || u.rebuild == 0 ? 1 : B_WEAR[opt(u, 4)];
    }

    // ---- the sheet: weight, cost, power for a design on a locomotive ---------------------------------------------------
    public static final class Sheet {
        public double massT, axleT, powerKw, powerPct, teKn, vmaxKmh, brakePct, coolPct;
        public int cost;
        public final List<String> notes = new ArrayList<>();
    }

    public static Sheet sheet(Locomotive l, int[] d) {
        Sheet s = new Sheet();
        double hp = 1000, te = 200_000, kmh = 100, kg = 80_000;
        try {
            hp = l.getDefinition().getHorsePower(l.gauge);
            te = l.getDefinition().getStartingTractionNewtons(l.gauge);
            kmh = l.getDefinition().getMaxSpeed(l.gauge).metric();
            kg = l.getDefinition().getWeight(l.gauge);
        } catch (LinkageError | RuntimeException ignored) { }
        int[] o = new int[6];
        for (int i = 0; i < 6; i++) o[i] = d[i] < 0 ? 0 : Math.min(NAMES[i].length - 1, d[i]);
        double heat = M_HEAT[o[0]], cool = C_CAP[o[2]], suff = Math.min(1, cool / heat);
        s.powerPct = M_POWER[o[0]] * suff * G_TE[o[1]] * 100;
        s.powerKw = hp * 0.7457 * s.powerPct / 100;
        s.teKn = te / 1000 * G_TE[o[1]] * (G_SPEED[o[1]] < 1 ? 1 / Math.max(0.65, G_SPEED[o[1]]) * 0.85 : 1) * suff;
        s.vmaxKmh = kmh * G_SPEED[o[1]] * B_SPEED[o[4]];
        double add = 0;
        for (int i = 0; i < 6; i++) { add += MASS[i][o[i]]; s.cost += COST[i][o[i]]; }
        s.massT = kg / 1000.0 + add;
        s.axleT = s.massT / com.dogpound.railmap.track.Rails.axles(l);
        s.brakePct = K_BRAKE[o[5]] * 100;
        s.coolPct = cool / heat * 100;
        if (cool < heat) s.notes.add("COOLING TOO SMALL FOR THE MOTORS");
        if (s.axleT > 25) s.notes.add("AXLE LOAD OVER 25 T - CHECK ROUTE");
        if (l instanceof LocomotiveSteam) s.notes.add("STEAM: TRACTION + COOLING DO NOT APPLY");
        return s;
    }

    /** the spares + parts a rebuild to this design takes (from the workshop drawers and the depot stores) */
    public static Map<String, Integer> parts(FleetData.Unit u, int[] d, boolean electric) {
        Map<String, Integer> m = new LinkedHashMap<>();
        if (d[0] > 0 && d[0] != u.design[0]) m.put(electric ? "spare:TRACTION_MOTOR" : "spare:POWER_KIT", 2);   // diesel: the power unit is re-engineered with the motors
        if (d[1] > 0 && d[1] != u.design[1]) m.put("part:GEARBOX", 4);
        if (d[2] > 0 && d[2] != u.design[2]) m.put("part:FAN", 2);
        if (d[3] == 3 && u.design[3] != 3) m.put("part:BATTERY", 8);
        else if (d[3] == 4 && u.design[3] != 4) { m.put("part:BATTERY", 24); m.put("part:DIODE", 6); m.put("part:CONTACTOR", 4); }    // traction battery + charger
        else if (d[3] == 5 && u.design[3] != 5) { m.put("part:BATTERY", 6); m.put("part:PLATE", 8); m.put("part:CAPACITOR", 6); m.put("part:HEAT_SINK", 4); }   // H2 tanks + fuel-cell stacks + buffer
        else if (d[3] > 0 && d[3] != u.design[3]) m.put("part:PLATE", 4);
        if (d[4] > 0 && d[4] != u.design[4]) m.put("spare:BOGIE", 2);
        if (d[5] == 1 && u.design[5] != 1) m.put("spare:BRAKE_BLOCKS", 2);
        else if (d[5] > 1 && d[5] != u.design[5]) m.put("part:PLATE", m.getOrDefault("part:PLATE", 0) + 4);
        return m;
    }

    // ---- the acceptance test run (once a second) -----------------------------------------------------------------------
    static void tick(World w, long now) {
        FleetData f = FleetData.get(w);
        boolean any = false;
        for (FleetData.Unit u : f.units().values()) if (u.rebuild == 2) { any = true; break; }
        if (!any) return;
        for (Locomotive l : cam72cam.mod.world.World.get(w).getEntities(Locomotive.class)) {
            if (l.isDead()) continue;
            FleetData.Unit u = f.peek(l.getUUID());
            if (u == null || u.rebuild != 2) continue;
            float kmh = l.getCurrentSpeed() != null ? Math.abs((float) l.getCurrentSpeed().metric()) : 0;
            u.testPeak = Math.max(u.testPeak, kmh);
            double run = u.km - u.testKm;
            double target = Math.min(60, Math.max(20, (speedKmh(u) > 0 ? speedKmh(u) : u.baseKmh > 0 ? u.baseKmh : 80) * 0.6));
            SimData.Car c = SimData.get(w).peek(l.getUUID());
            String fail = null;
            if (c != null && !c.faults.isEmpty()) fail = "fault during the run: " + c.faults.iterator().next();
            else if (l instanceof LocomotiveDiesel d && d.getEngineTemperature() > 110) fail = String.format(Locale.ROOT, "engine overheated (%.0f °C)", d.getEngineTemperature());
            else if (c != null && !Double.isNaN(c.tmC) && c.tmC > 180) fail = String.format(Locale.ROOT, "traction motors overheated (%.0f °C)", c.tmC);
            else if (now - u.testStart > 12000) fail = String.format(Locale.ROOT, "not completed in time (%.1f km, peak %.0f km/h)", run, u.testPeak);
            if (fail != null) { finish(w, f, u, false, fail, run, target); continue; }
            if (run >= 2.0 && u.testPeak >= target) finish(w, f, u, true, "", run, target);
        }
    }

    static void finish(World w, FleetData f, FleetData.Unit u, boolean pass, String why, double run, double target) {
        u.rebuild = pass ? 3 : 4;
        List<String> l = new ArrayList<>();
        l.add("REBUILD ACCEPTANCE TEST - " + u.name);
        for (int s = 0; s < 6; s++) l.add(SLOTS[s] + ": " + NAMES[s][opt(u, s)]);
        l.add(String.format(Locale.ROOT, "Test run %.2f km, peak %.0f km/h (target %.0f)", run, u.testPeak, target));
        l.add(pass ? "RESULT: PASS - accepted into service" : "RESULT: FAIL - " + why);
        AssetData.get(w).addCert(w, u.name, "REBUILD ACCEPTANCE", pass, l, "test run");
        u.note(w, pass ? "rebuild ACCEPTED after its test run" : "rebuild acceptance test FAILED: " + why);
        Recorder.event(w, "REBUILD: " + u.name + (pass ? " accepted" : " failed acceptance - " + why));
        if (!pass) AssetTicker.standing(w, "rebuild:" + u.name, AlarmManager.Cat.MACHINE, AlarmManager.Sev.MINOR, "REBUILD ACCEPTANCE FAILED: " + u.name + " - " + why + " (held to 25 km/h)", null);
        else AlarmManager.get(w).clear("rebuild:" + u.name);
        f.markDirty();
    }
}
