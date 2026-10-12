package com.dogpound.railmap.ride;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.sound.PackSounds;
import com.dogpound.railmap.weather.Weather;
import com.dogpound.railmap.weather.WeatherData;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Locale;

import static com.dogpound.railmap.ride.RideData.*;

/**
 * The drive and control logic of one line, every tick (her batch 5 #5: "machine rooms (motors, brakes, controls)"):
 * <ul>
 *   <li>Speed: the desk's setpoint, ramped at 0.3 m/s² up / 0.5 m/s² service braking / 1.6 m/s² on the emergency
 *   (bullwheel rim) brake; the motor's power limit caps acceleration and climbing speed.</li>
 *   <li>Shuttle (aerial tram, funicular): two counterbalanced cars; dwell with doors open, door warning, run on a braking
 *   curve to a creep at the far end, stop, reverse. MANUAL waits for START (and both stations READY) every trip.</li>
 *   <li>Gondola: the rope runs at constant speed; cabins detach at stations (slow to 0.25 m/s round the bullwheel with
 *   doors open), wait for their launch spacing, re-grip.</li>
 *   <li>Motor: rope force from the uneven loads on the slope (riders 80 kg each), friction and acceleration; kW, amps on
 *   the machine's supply, motor and gearbox oil temperature, regenerating when the downhill side is heavier.</li>
 *   <li>Safety: E-stops (desk, machine room, both stations), wind (restricted at 75 % of the limit, stopped above it),
 *   rope position detectors on the pylons (rope swing in the wind), overspeed, drive / supply loss (brakes fall),
 *   motor overheating, tension carriage at its limits, door interlocks. Faults latch until RESET.</li>
 * </ul>
 */
public final class RideSim {
    private RideSim() {}

    static final double DT = 0.05, G = 9.81;
    static final double[] CAR_KG = {850, 9000, 14000}, RATED_KW = {320, 700, 900}, TENSION_KN = {140, 380, 0}, ACC = {0.3, 0.3, 0.25}, EDEC = {1.6, 1.2, 1.4};
    /** the faults that drop the emergency brake (the rest stop the line on the service brake) */
    static final int EMERGENCY = F_ESTOP | F_RPD | F_OVERSPEED | F_DRIVE | F_SUPPLY | F_MOTOR;

    public static void tick(World w, Line l, long now) {
        RideGeom g = l.geom();
        if (g == null) return;
        int n = l.cars();
        if (!l.shuttle() && l.w.length != n) {                                // cabins added / taken off: spread them round the loop
            l.w = new double[n];
            for (int i = 0; i < n; i++) l.w[i] = g.wrap(i * g.loop / n + RideGeom.ZONE + 0.5);
        }
        if (l.carV.length != n) { l.carV = new double[n]; l.carRiders = new int[n]; }
        boolean machineUp = now - l.machineSeen < 40;
        boolean evac = l.mode == 2;
        if (!machineUp) { l.driveOk = false; l.driveWhy = l.machine == 0 ? "no machine room linked" : "machine room not loaded"; }
        if (now % 20 == 0) { wind(w, l, now); tension(w, l, g); }
        rpd(l, g);

        // ---- faults ----------------------------------------------------------------------------------------------
        int f = 0;
        if (l.estops != 0) f |= F_ESTOP;
        if (!evac && (l.running || Math.abs(l.v) > 0.01) && !l.driveOk) f |= machineUp && l.driveWhy.startsWith("supply") ? F_SUPPLY : F_DRIVE;
        if (!evac && l.running && !l.hpu) { l.driveWhy = "brake HPU stopped - brakes applied"; f |= F_DRIVE; }
        if (RideConfig.windStops && l.wind > l.windLimit) f |= F_WIND;
        if (RideConfig.windStops && l.rpdMm > RideConfig.rpdTripMm) f |= F_RPD;
        if (Math.abs(l.v) > Math.max(0.6, l.vMax * 1.15 + 0.3)) f |= F_OVERSPEED;
        if (l.motorC > 140) f |= F_MOTOR;
        if (l.hasTension() && (l.carriage < 3 || l.carriage > 97)) f |= F_TENSION;
        if (Math.abs(l.v) > 0.05 && (l.gate[0] || l.gate[1])) f |= F_DOORS;
        int fresh = f & ~l.faults;
        if (fresh != 0) trip(w, l, fresh);

        // ---- speed target ----------------------------------------------------------------------------------------
        double vmax = evac ? 0.5 : l.setV;
        if (!l.hasTension()) vmax = Math.min(vmax, 2.0);
        if (RideConfig.windStops && l.wind > 0.75 * l.windLimit) vmax *= 0.5;
        l.vMax = vmax;
        boolean canRun = l.running && !l.ebrake && machineUp && (evac ? l.brakeRel : l.driveOk && l.hpu) && l.faults == 0;
        if (!canRun && l.running && l.faults != 0) l.running = false;
        double before = l.v;
        if (l.shuttle()) shuttle(w, l, g, canRun, vmax);
        else loop(l, g, canRun, vmax);
        l.accel = (l.v - before) / DT;
        l.sbrake = Math.abs(l.v) < 0.01 && !canRun || !l.hpu && !evac;
        motor(l, g);
    }

    // ---- shuttle: aerial tram + funicular ----------------------------------------------------------------------------
    static void shuttle(World w, Line l, RideGeom g, boolean canRun, double vmax) {
        double L = g.length;
        switch (l.phase) {
            case DWELL -> {
                l.v = 0;
                if (canRun) {
                    if (l.mode == 2) { l.gate[0] = l.gate[1] = false; l.phase = RUN; break; }
                    if (l.dwell > 0) l.dwell--;
                    boolean ready = l.mode != 1 || l.ready[0] && l.ready[1];
                    if (l.dwell <= 0 && ready) {
                        l.phase = CLOSING; l.dwell = 60;
                        chime(w, l, "door_warn");
                    }
                }
            }
            case CLOSING -> {
                l.v = 0;
                l.gate[0] = l.gate[1] = false;
                if (!canRun) break;
                if (--l.dwell <= 0) {
                    l.phase = RUN;
                    l.ready[0] = l.ready[1] = false;
                    Recorder.event(w, l.label() + " departs " + (l.dir > 0 ? "A -> B" : "B -> A"));
                }
            }
            default -> {
                double d = l.dir > 0 ? L - l.s : l.s;
                double cap = 0.3 + Math.sqrt(2 * 0.35 * Math.max(0, d - 0.2));
                double target = canRun ? Math.min(vmax, cap) * l.dir : 0;
                l.v = ramp(l, l.v, target, power(l, g));
                l.s += l.v * DT;
                l.ropeRun += Math.abs(l.v) * DT;
                if (l.s <= 0 || l.s >= L) { l.s = Math.max(0, Math.min(L, l.s)); l.v = 0; }
                d = l.dir > 0 ? L - l.s : l.s;
                if (d < 0.03 && Math.abs(l.v) < 0.4) arrive(w, l, L);
            }
        }
    }

    /** the self-test's trip: one tick of the shuttle with no world (no sounds, no recorder) */
    static void shuttleTest(Line l) {
        double vmax = l.hasTension() ? l.setV : Math.min(l.setV, 2.0);
        l.vMax = vmax;
        shuttle(null, l, l.geom(), true, vmax);
    }

    static void arrive(World w, Line l, double L) {
        l.s = l.dir > 0 ? L : 0;
        l.v = 0;
        l.dir = -l.dir;
        l.phase = DWELL;
        l.dwell = RideConfig.dwellSeconds * 20;
        l.gate[0] = l.gate[1] = true;
        if (l.mode != 0) l.running = false;                                  // MANUAL: START every trip; EVAC: home
        chime(w, l, "station.chime_dingdong");
        Recorder.event(w, l.label() + " cars in stations");
    }

    // ---- gondola: detachable loop -----------------------------------------------------------------------------------
    static void loop(Line l, RideGeom g, boolean canRun, double vmax) {
        double target = canRun ? vmax : 0;
        l.v = ramp(l, l.v, target, power(l, g));
        if (l.v < 0) l.v = 0;
        l.ropeRun += l.v * DT;
        int n = l.w.length;
        double spacing = g.loop / n, slow = Math.min(l.v, 0.25), Z = RideGeom.ZONE, arc = g.arc(), zl = 2 * Z + arc;
        for (int i = 0; i < n; i++) {
            double wi = l.w[i];
            double[] pl = g.loopPlace(wi);
            double sp = l.v;
            if (pl[3] >= 0) {                                              // detached in a station zone
                double start = pl[3] == 1 ? g.length - Z : 2 * g.length + arc - Z;
                double k = g.wrap(wi - start);
                if (k > zl) k = 0;
                if (k < Z) sp = slow + (l.v - slow) * (1 - k / Z);
                else if (k < Z + arc) sp = slow;
                else sp = slow + (l.v - slow) * ((k - Z - arc) / Z);
                int st = (int) pl[3];
                if (k >= Z + arc - 0.3 && k < Z + arc) {                   // the launch gate: spacing + attendant hold
                    if (l.hold[st] || gapAhead(l, g, i) < spacing * 0.7) sp = 0;
                }
            }
            double gap = gapAhead(l, g, i);
            if (gap < 2.5) sp = Math.min(sp, Math.max(0, (gap - 1.5)) * 0.5);   // never run into the cabin ahead
            l.carV[i] = sp;
        }
        for (int i = 0; i < n; i++) l.w[i] = g.wrap(l.w[i] + l.carV[i] * DT);
    }

    static double gapAhead(Line l, RideGeom g, int i) {
        double best = g.loop;
        for (int j = 0; j < l.w.length; j++) if (j != i) best = Math.min(best, g.wrap(l.w[j] - l.w[i]));
        return best;
    }

    /** speed toward target within the limits: emergency brake, service brake, motor power */
    static double ramp(Line l, double v, double target, double[] pw) {
        if (l.ebrake) { double d = EDEC[l.kind] * DT; return Math.abs(v) <= d ? 0 : v - Math.signum(v) * d; }
        double dv = target - v;
        boolean speedingUp = Math.abs(target) > Math.abs(v) && Math.signum(target) * Math.signum(v) >= 0;
        double lim = speedingUp ? ACC[l.kind] : 0.5;
        if (speedingUp && pw != null) {                                       // the motor's power limit: P = (F + m a) v
            double vv = Math.max(0.3, Math.abs(v));
            double aMax = (RATED_KW[l.kind] * 1100 / vv - pw[0] * Math.signum(target == 0 ? 1 : target)) / pw[1];
            lim = Math.max(0.0, Math.min(lim, aMax));
        }
        return v + Math.max(-lim * DT, Math.min(lim * DT, dv));
    }

    /** {rope force needed to move forward (N), total moving mass (kg)} */
    static double[] power(Line l, RideGeom g) {
        double m0 = CAR_KG[l.kind], F = 0, mass = 0;
        int n = l.cars();
        for (int i = 0; i < n; i++) {
            double m = m0 + 80.0 * (i < l.carRiders.length ? l.carRiders[i] : 0);
            double gr;
            if (l.shuttle()) gr = i == 0 ? g.grade(l.s) : -g.grade(g.length - l.s);
            else {
                double[] p = g.loopPlace(l.w[i]);
                if (p[3] >= 0) continue;                                       // detached: not on the rope
                gr = p[1] > 0 ? g.grade(p[0]) : -g.grade(p[0]);
            }
            F += m * G * gr;
            mass += m;
        }
        double rope = (l.kind == RideGeom.FUNICULAR ? 6 : 8) * 2 * g.length;
        mass += rope;
        F += 0.018 * mass * G;                                                 // sheave + bearing + rolling friction
        return new double[]{F, mass};
    }

    static void motor(Line l, RideGeom g) {
        double[] pw = power(l, g);
        double F = l.shuttle() ? pw[0] * Math.signum(l.v == 0 ? 1 : l.v) : pw[0];
        double p = (F + pw[1] * l.accel * Math.signum(l.v == 0 ? 1 : l.v)) * Math.abs(l.v);
        double elec = p >= 0 ? p / 0.93 : p * 0.9;
        boolean evac = l.mode == 2;
        l.kw = evac ? 0 : elec / 1000;
        double rated = RATED_KW[l.kind];
        l.amps = evac || l.supplyV <= 0 ? 0 : Math.abs(elec) / (Math.sqrt(3) * l.supplyV * 0.88);
        double load = Math.abs(l.kw) / rated;
        l.motorC += ((20 + 95 * load * load) - l.motorC) * 0.002;
        l.oilC += ((25 + 45 * load) - l.oilC) * 0.0008;
        l.riders = 0;
        for (int r : l.carRiders) l.riders += r;
    }

    static void wind(World w, Line l, long now) {
        if (l.windSet >= 0) { l.wind = l.windSet; return; }
        double top = -1;
        double[] at = l.supports.get(l.supports.size() / 2);
        for (double[] p : l.supports) if (p[1] > top) { top = p[1]; at = p; }
        BlockPos p = new BlockPos(at[0], at[1], at[2]);
        double ms = w.isThundering() ? 18 : w.isRaining() ? 9 : 3;
        if (Weather.on()) {
            WeatherData.Cell c = WeatherData.get(w).cells.get(WeatherData.key(p.getX() >> 6, p.getZ() >> 6));
            if (c != null && c.lastTick != 0) ms = c.wind;
        }
        ms *= 1 + Math.max(0, (top - 64) / 200.0);                             // stronger higher up
        ms *= 1 + 0.15 * Math.sin(now / 37.0 + l.id) + 0.08 * Math.sin(now / 11.0);   // gusts
        l.wind = Math.max(0, ms);
    }

    /** rope swing at the pylons: the worst one */
    static void rpd(Line l, RideGeom g) {
        l.rpdMm = 0;
        l.rpdTower = -1;
        if (l.kind == RideGeom.FUNICULAR) return;
        for (int i = 1; i + 1 < l.supports.size(); i++) {
            double span = (RideGeom.dist(l.supports.get(i - 1), l.supports.get(i)) + RideGeom.dist(l.supports.get(i), l.supports.get(i + 1))) / 2;
            double mm = 0.9 * l.wind * l.wind * (0.5 + span / 120) * (l.hasTension() ? 1 : 1.4);
            if (mm > l.rpdMm) { l.rpdMm = mm; l.rpdTower = i; }
        }
    }

    static void tension(World w, Line l, RideGeom g) {
        if (!l.hasTension()) { l.tensionKn = 0; l.carriage = 50; return; }
        if (w.getTotalWorldTime() - l.tensionSeen > 60 || !l.tensionPowered) { l.tensionKn = 0; l.carriage = -1; return; }   // monitor signal lost: fails safe
        double airC = 15;
        try { airC = Weather.airC(w, BlockPos.fromLong(l.tension)); } catch (RuntimeException | LinkageError ignored) { }
        double cap = l.cars() * 80.0 * (l.kind == RideGeom.GONDOLA ? 8 : 40);
        double loadFrac = cap <= 0 ? 0 : l.riders * 80.0 / cap;
        l.tensionKn = TENSION_KN[l.kind] * (1 + 0.04 * loadFrac) + Math.abs(l.accel) * 4;
        l.carriage = 50 + (airC - 15) * 0.9 + loadFrac * 22 - Math.abs(l.v) * 1.2 + g.length / 400.0;   // the rope stretches warm and loaded
    }

    // ---- trips + commands --------------------------------------------------------------------------------------------
    static void trip(World w, Line l, int bits) {
        l.faults |= bits;
        l.running = false;
        boolean emergency = (bits & EMERGENCY) != 0;
        if (emergency && !l.ebrake) { l.ebrake = true; chime(w, l, "cab.brake_emergency"); }
        for (int i = 0; i < F_NAMES.length; i++) {
            if ((bits & (1 << i)) == 0) continue;
            String text = l.label() + " " + (emergency ? "EMERGENCY STOP" : "STOPPED") + ": " + F_NAMES[i] + " - " + detail(l, 1 << i);
            AlarmManager.get(w).raise(w, "ride:" + l.id + ":" + i, AlarmManager.Cat.MACHINE, emergency ? AlarmManager.Sev.MAJOR : AlarmManager.Sev.MINOR, text, l.station(0), true);
            Recorder.event(w, text);
        }
    }

    static String detail(Line l, int bit) {
        return switch (bit) {
            case F_ESTOP -> "E-stop pushed in at " + ((l.estops & 1) != 0 ? "the desk " : "") + ((l.estops & 2) != 0 ? "the machine room " : "") + ((l.estops & 4) != 0 ? "station A " : "") + ((l.estops & 8) != 0 ? "station B" : "");
            case F_WIND -> String.format(Locale.ROOT, "wind %.0f m/s over the %.0f m/s limit", l.wind, l.windLimit);
            case F_RPD -> String.format(Locale.ROOT, "rope swing %.0f mm at pylon %d", l.rpdMm, l.rpdTower);
            case F_SUPPLY, F_DRIVE -> l.driveWhy;
            case F_DOORS -> "a station gate opened with the line moving";
            case F_OVERSPEED -> String.format(Locale.ROOT, "%.1f m/s against %.1f allowed", Math.abs(l.v), l.vMax);
            case F_TENSION -> l.carriage < 0 ? "tension monitor signal lost (no 12 V / not loaded)" : String.format(Locale.ROOT, "tension carriage at %.0f %%", l.carriage);
            case F_MOTOR -> String.format(Locale.ROOT, "motor winding %.0f C", l.motorC);
            default -> "";
        };
    }

    /** START from the desk (or a command): null = started, else why not */
    public static String start(World w, Line l) {
        if (l.geom() == null) return "line has no rope";
        if (l.local) return "machine room is on LOCAL - desk locked out";
        if (l.faults != 0) return "faults standing: " + faults(l.faults) + " - RESET first";
        if (l.estops != 0) return "an E-stop is pushed in";
        if (l.mode != 2 && !l.driveOk) return "drive not ready: " + l.driveWhy;
        if (l.mode == 2 && !l.brakeRel) return "EVAC: release the brakes (BRAKE REL) first";
        if (!l.shuttle() && (l.gate[0] || l.gate[1])) return "a station gate is open (door interlock)";
        if (l.setV <= 0 && l.mode != 2) return "speed setpoint is 0";
        l.running = true;
        l.hpu = true;
        Recorder.event(w, l.label() + " START (" + new String[]{"AUTO", "MANUAL", "EVAC"}[l.mode] + ")");
        if (!l.shuttle()) chime(w, l, "cab.horn");
        return null;
    }

    public static void stop(World w, Line l) {
        if (!l.running) return;
        l.running = false;
        Recorder.event(w, l.label() + " normal stop");
    }

    /** RESET: clears what has gone; the emergency brake lifts once stopped */
    public static String reset(World w, Line l) {
        int keep = 0;
        if (l.estops != 0) keep |= F_ESTOP;
        if (l.wind > l.windLimit * 0.9 && RideConfig.windStops) keep |= l.faults & F_WIND;
        if (l.rpdMm > RideConfig.rpdTripMm * 0.8 && RideConfig.windStops) keep |= l.faults & F_RPD;
        if (l.mode != 2 && !l.driveOk) keep |= l.faults & (F_DRIVE | F_SUPPLY);
        if (l.motorC > 110) keep |= l.faults & F_MOTOR;
        if (l.hasTension() && (l.carriage < 3 || l.carriage > 97)) keep |= l.faults & F_TENSION;
        if (l.gate[0] && l.v != 0 || l.gate[1] && l.v != 0) keep |= l.faults & F_DOORS;
        int cleared = l.faults & ~keep;
        l.faults = keep;
        for (int i = 0; i < F_NAMES.length; i++) if ((cleared & (1 << i)) != 0) AlarmManager.get(w).clear("ride:" + l.id + ":" + i);
        if (Math.abs(l.v) < 0.01 && (keep & EMERGENCY) == 0) l.ebrake = false;
        if (cleared != 0) Recorder.event(w, l.label() + " reset: " + faults(cleared) + " cleared");
        return keep == 0 ? "§aReset - line healthy" + (l.ebrake ? " (emergency brake lifts when stopped)" : "") : "§6Still standing: " + faults(keep);
    }

    public static String faults(int bits) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < F_NAMES.length; i++) if ((bits & (1 << i)) != 0) b.append(b.length() == 0 ? "" : ", ").append(F_NAMES[i]);
        return b.length() == 0 ? "none" : b.toString();
    }

    static void chime(World w, Line l, String id) {
        if (w == null) return;
        for (int k = 0; k < 2; k++) PackSounds.play(w, l.station(k), id, 1f, 1f);
    }

    public static String state(Line l) {
        if (l.geom() == null) return "NO ROPE";
        if (l.faults != 0) return (l.ebrake ? "E-BRAKE " : "STOPPED ") + faults(l.faults);
        if (l.mode == 2) return l.running ? "EVAC DRIVE" : "EVAC READY";
        if (!l.running) return Math.abs(l.v) > 0.01 ? "STOPPING" : "STOPPED";
        if (l.shuttle()) return l.phase == DWELL ? "DWELL " + (l.dwell / 20) + " S" : l.phase == CLOSING ? "DOORS CLOSING" : "RUNNING";
        return "RUNNING";
    }
}
