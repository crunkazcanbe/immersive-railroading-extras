package com.dogpound.railmap.sim;

import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.entity.LocomotiveDiesel;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.server.Electric;
import com.dogpound.railmap.server.RailProps;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.Locale;

/**
 * Detailed locomotive engine management (her ChatGPT list item 8), on top of IR's own diesel (which already has an
 * engine on/off, an RPM that follows the throttle, fuel burn, a coolant temperature it must reach before it gives
 * power, and an overheat cut-out). Four times a second, per diesel locomotive:
 * <ul>
 *   <li><b>start sequence</b>: turning the engine on (IR's start key / switch) runs PRELUBE (big engines) and CRANK off
 *       the 74 V battery before it fires; a flat battery or an empty tank fails the start (fault code), cranking pulls
 *       the battery down, the alternator charges it back while running and the auxiliaries drain it while shut down;</li>
 *   <li><b>warm-up and cooling</b>: shutters closed while cold, radiator fans in two stages when hot (IR's
 *       coolant temperature, through the fork's prideCooling); low coolant cools worse and an engine run hot boils it
 *       off; above 100 °C the governor derates, IR still cuts out at its overheat;</li>
 *   <li><b>oil</b>: pressure from RPM, oil temperature and level; low oil pressure is a protective shutdown;</li>
 *   <li><b>turbocharger</b>: boost lags the notch, and until it builds the engine can't make full power (notch response);</li>
 *   <li><b>traction motors</b> (diesel-electric and electric): heat with current squared, blown cooler while running,
 *       derate when hot, cut out at the limit;</li>
 *   <li><b>fuel</b>: the burn rate in litres an hour from IR's tank;</li>
 *   <li><b>AESS</b>: parked, idling, brakes on for N minutes (config) = it shuts itself down; it restarts on its own when
 *       the battery or coolant gets low or the driver opens the throttle / releases the brakes;</li>
 *   <li><b>fault codes</b>, latched until FAULT RESET (diagnostic panel / /irextras engine reset), logged, alarmed.</li>
 * </ul>
 * Families: a switcher (under 1.2 MW) has no turbo or prelube and warms up quickly; road units (1.2-3 MW) and heavy
 * units (over 3 MW) prelube, crank longer and are turbocharged. Everything acts through IR's own controls plus the
 * fork's derate and cooling factors - no physics hacks.
 */
public final class EngineSim {
    private EngineSim() {}

    public static final int OFF = 0, PRELUBE = 1, CRANK = 2, RUN = 3;
    static final String[] PHASE = {"SHUT DOWN", "PRELUBE", "CRANKING", "RUNNING"};

    /** engine family from its rated power */
    public enum Family {
        SWITCHER("switcher", 400, 1500, 0, 0, 3.0, 0),
        ROAD("road", 318, 904, 4.0, 5.0, 5.0, 210),
        HEAVY("heavy road", 300, 1050, 7.0, 7.0, 6.5, 250);
        public final String label;
        public final double idle, max, prelube, crankS, warmFactor, maxBoostKpa;
        Family(String label, double idle, double max, double prelube, double crankS, double warmFactor, double maxBoostKpa) {
            this.label = label; this.idle = idle; this.max = max; this.prelube = prelube; this.crankS = crankS;
            this.warmFactor = warmFactor; this.maxBoostKpa = maxBoostKpa;
        }
    }

    public static Family family(LocomotiveDiesel l) {
        double w;
        try { w = l.getDefinition().getScriptedWatt(l.gauge, l) / Math.max(0.05, l.gauge.scale()); } catch (RuntimeException e) { w = 2e6; }
        return w < 1.2e6 ? Family.SWITCHER : w < 3.0e6 ? Family.ROAD : Family.HEAVY;
    }

    static void tick(World mc, SimData data, long now, double dt) {
        if (!SimConfig.engineManagement && !SimConfig.tractionMotorHeating) return;
        cam72cam.mod.world.World w = cam72cam.mod.world.World.get(mc);
        RailProps props = RailProps.get(mc);
        for (LocomotiveDiesel l : w.getEntities(LocomotiveDiesel.class)) {
            if (l.isDead() || l.getDefinition() == null) continue;
            SimData.Car c = data.of(l.getUUID());
            boolean electric = Electric.isElectric(l, props.of(l.getUUID()));
            float derate = 1f;
            String why = "";
            if (SimConfig.engineManagement && !electric && !l.getDefinition().isCabCar()) {
                float[] d = diesel(mc, l, c, now, dt);
                derate = d[0];
                why = c.derateWhy;
            } else {
                l.prideCooling = 1f;
                c.phase = l.isTurnedOn() ? RUN : OFF;
            }
            if (SimConfig.tractionMotorHeating) {
                float tm = motors(mc, l, c, now, dt);
                if (tm < derate) { derate = tm; why = "traction motors hot"; }
            }
            if (c.isolated) { derate = 0; why = "isolated"; }
            float cond = com.dogpound.railmap.depot.Condition.derate(l);   // worn engine / traction (her list: condition-based restrictions)
            if (cond < derate) { derate = cond; why = "worn - condition restriction"; }
            derate = Math.round(derate * 50) / 50f;              // steps of 2 % (each change re-simulates the train)
            l.prideDerate = derate;
            c.derate = derate;
            c.derateWhy = derate < 1 ? why : "";
        }
    }

    /** one diesel's engine; returns {derate} */
    private static float[] diesel(World mc, LocomotiveDiesel l, SimData.Car c, long now, double dt) {
        Family fam = family(l);
        boolean fuelReq = Config.isFuelRequired(l.gauge);
        boolean on = l.isTurnedOn();
        double coolant = l.getEngineTemperature();
        double ambient = l.ambientTemperature();
        float derate = 1f;
        String why = "";

        // first look at this engine this session: one already running stays running (no restart on world load)
        if (!c.init) {
            c.init = true;
            if (on) { c.phase = RUN; c.phaseTime = 60; c.rpm = fam.idle; c.oilKpa = 250; }
        }
        // the driver (or AESS) switched it on / off
        if (on && c.phase == OFF) {
            c.aessStopped = false;
            if (c.battery < 0.2f) {
                l.setTurnedOn(false);
                fault(mc, l, c, now, "E401", "START FAILED - BATTERY LOW", AlarmManager.Sev.MINOR);
                return new float[]{0};
            }
            if (fuelReq && l.getLiquidAmount() <= 0) {
                l.setTurnedOn(false);
                fault(mc, l, c, now, "E120", "START FAILED - NO FUEL", AlarmManager.Sev.MINOR);
                return new float[]{0};
            }
            if (c.isolated) {
                l.setTurnedOn(false);
                tell(l, "§eEngine isolated - set the RUN / ISOLATE switch to RUN to start it");
                return new float[]{0};
            }
            c.phase = fam.prelube > 0 ? PRELUBE : CRANK;
            c.phaseTime = 0;
            c.starts++;
            tell(l, fam.prelube > 0 ? "§7Engine start: prelube pump running..." : "§7Engine start: cranking...");
        } else if (!on && c.phase != OFF) {
            c.phase = OFF;
            c.phaseTime = 0;
        }

        c.phaseTime += dt;
        double load = Math.abs(l.getThrottle());
        double rpmTarget;
        switch (c.phase) {
            case PRELUBE:
                rpmTarget = 0;
                c.oilKpa = Math.min(140, c.oilKpa + 60 * dt);
                c.battery -= (float) (0.002 * dt);
                derate = 0; why = "prelube";
                if (c.phaseTime >= fam.prelube) { c.phase = CRANK; c.phaseTime = 0; }
                break;
            case CRANK:
                rpmTarget = 110;
                c.battery -= (float) (0.012 * dt);
                derate = 0; why = "cranking";
                if (c.battery < 0.05f) {
                    l.setTurnedOn(false);
                    c.phase = OFF;
                    fault(mc, l, c, now, "E402", "CRANK ABORTED - BATTERY FLAT", AlarmManager.Sev.MINOR);
                    break;
                }
                if (c.phaseTime >= fam.crankS) {
                    c.phase = RUN; c.phaseTime = 0;
                    tell(l, String.format(Locale.ROOT, "§aEngine fired - idling at %d rpm, warming up (%d °C)", Math.round(fam.idle), Math.round(coolant)));
                }
                break;
            case RUN:
                rpmTarget = fam.idle + (fam.max - fam.idle) * l.getRelativeRPM();
                break;
            default:
                rpmTarget = 0;
        }
        // rpm spools toward its target (fast up on a governor, slower run-down)
        double tau = rpmTarget > c.rpm ? 1.2 : 2.5;
        c.rpm += (rpmTarget - c.rpm) * Math.min(1, dt / tau);
        if (c.rpm < 1) c.rpm = 0;
        double rpmFrac = fam.max > 0 ? c.rpm / fam.max : 0;

        // ---- oil: pressure from rpm, thicker when cold, falls with the level
        double oilTemp = Math.max(ambient, coolant - 5);
        double visc = 1 + Math.max(0, 70 - oilTemp) / 60;      // cold oil reads high
        double level = Math.max(0, Math.min(1, (c.oil - 0.25) / 0.45));
        if (c.phase == RUN) c.oilKpa = (90 + 470 * rpmFrac) * visc * level;
        else if (c.phase == OFF) c.oilKpa = Math.max(0, c.oilKpa - 80 * dt);
        if (c.phase == RUN) {
            c.oil = Math.max(0, c.oil - (float) (0.000004 * (1 + 3 * load) * dt));    // ~1.4 %/hour idling, ~6 %/hour flat out
            c.hours += dt / 3600.0;
            if (c.phaseTime > 6 && c.oilKpa < 100) {
                l.setTurnedOn(false);
                c.phase = OFF;
                fault(mc, l, c, now, "E101", String.format(Locale.ROOT, "LOW LUBE OIL PRESSURE %d kPa - PROTECTIVE SHUTDOWN", Math.round(c.oilKpa)), AlarmManager.Sev.MAJOR);
            } else if (c.oil < 0.45f) fault(mc, l, c, now, "E102", "LOW OIL LEVEL - TOP UP AT DEPOT", AlarmManager.Sev.MINOR);
        }

        // ---- cooling: shutters, two fan stages, coolant level
        boolean running = c.phase == RUN;
        c.shutters = coolant < 78;
        c.fanLow = running && coolant >= 84;
        c.fanHigh = running && coolant >= 90;
        float k = c.shutters ? (float) (0.5 / fam.warmFactor * 3) : c.fanHigh ? 4.5f : c.fanLow ? 2.5f : 1f;
        if (!running) k = 1f;
        k *= Math.max(0.15f, c.coolant);
        l.prideCooling = k * com.dogpound.railmap.depot.Condition.cooling(l);   // worn parts run hotter
        if (coolant > 118) {
            c.coolant = Math.max(0, c.coolant - (float) (0.01 * dt));          // boiling off through the relief cap
            fault(mc, l, c, now, "E211", "COOLANT BOILING", AlarmManager.Sev.MAJOR);
        }
        if (c.coolant < 0.6f) fault(mc, l, c, now, "E210", "LOW COOLANT LEVEL", AlarmManager.Sev.MINOR);
        if (running && coolant > 100) {
            float hot = (float) Math.max(0, 1 - (coolant - 100) / 15);
            if (hot < derate) { derate = hot; why = String.format(Locale.ROOT, "hot engine %d °C", Math.round(coolant)); }
            fault(mc, l, c, now, "E204", "HOT ENGINE - POWER REDUCED", AlarmManager.Sev.MINOR);
        }
        if (running && coolant < 75 && fuelReq) {
            if (0 < derate) { derate = 0; why = String.format(Locale.ROOT, "warming up %d/75 °C", Math.round(coolant)); }
        }

        // ---- turbo: boost lags the notch; no boost = no full power
        if (fam.maxBoostKpa > 0) {
            double target = running ? fam.maxBoostKpa * Math.pow(load * Math.min(1, rpmFrac / 0.95), 1.3) : 0;
            double t = target > c.boostKpa ? 2.8 : 1.6;
            c.boostKpa += (target - c.boostKpa) * Math.min(1, dt / t);
            if (running && load > 0.05) {
                double needed = target <= 1 ? 1 : c.boostKpa / target;
                float turbo = (float) Math.min(1, 0.55 + 0.45 * needed);
                if (turbo < derate) { derate = turbo; why = String.format(Locale.ROOT, "turbo spooling %d kPa", Math.round(c.boostKpa)); }
            }
        } else c.boostKpa = 0;

        // ---- low oil pressure derate (before the shutdown point)
        if (running && c.oilKpa < 160 && c.phaseTime > 6) {
            float o = (float) Math.max(0.3, (c.oilKpa - 100) / 60);
            if (o < derate) { derate = o; why = "low oil pressure"; }
        }

        // ---- battery: alternator charges while running, auxiliaries drain while shut down
        if (running) c.battery = Math.min(1f, c.battery + (float) (0.0012 * dt));
        else if (c.phase == OFF && c.batteryOn) c.battery = Math.max(0f, c.battery - (float) (0.000025 * dt));   // battery switch off: no drain
        if (c.battery < 0.3f) fault(mc, l, c, now, "E400", "BATTERY LOW", AlarmManager.Sev.MINOR);

        // ---- fuel use, litres an hour
        double fuel = l.getLiquidAmount();
        if (c.lastFuel >= 0 && dt > 0) {
            double used = Math.max(0, c.lastFuel - fuel);
            c.fuelLph = c.fuelLph * 0.9 + used * 3600 / dt / 1000.0 * 0.1;     // mB -> L
        }
        c.lastFuel = fuel;
        if (fuelReq && running && fuel <= 0) fault(mc, l, c, now, "E121", "OUT OF FUEL", AlarmManager.Sev.MAJOR);

        // ---- AESS: idle shutdown and automatic restart
        aess(mc, l, c, fam, coolant, now, dt);

        // conditions gone: standing faults clear their alarm (they stay latched until FAULT RESET)
        if (c.battery >= 0.35f) clearAlarm(mc, l, "E400");
        if (coolant < 98) clearAlarm(mc, l, "E204");
        c.derateWhy = why;
        return new float[]{derate};
    }

    private static void aess(World mc, LocomotiveDiesel l, SimData.Car c, Family fam, double coolant, long now, double dt) {
        if (SimConfig.aessMinutes <= 0 || !c.aess) { c.idleSec = 0; return; }
        double kmh = Math.abs(l.getCurrentSpeed().metric());
        boolean braked = l.getTrainBrakePos() > 0.3f || l.getIndependentBrake() > 0.3f || l.getHandBrake() > 0.3f;
        if (c.phase == RUN) {
            boolean idle = Math.abs(l.getThrottle()) < 0.01f && kmh < 0.5 && braked && c.battery > 0.6f && coolant > 50;
            c.idleSec = idle ? c.idleSec + dt : 0;
            if (c.idleSec >= SimConfig.aessMinutes * 60.0) {
                c.idleSec = 0;
                c.aessStopped = true;
                l.setTurnedOn(false);
                c.phase = OFF;
                tell(l, "§7AESS: engine shut down to save fuel (it restarts by itself when needed)");
                Recorder.event(mc, label(l) + ": AESS idle shutdown");
            }
        } else if (c.phase == OFF && c.aessStopped) {
            String why = c.battery < 0.45f ? "battery low" : coolant < 35 ? "engine cooling down" : Math.abs(l.getThrottle()) > 0.01f ? "throttle opened"
                    : l.getTrainBrakePos() < 0.05f && l.getIndependentBrake() < 0.05f && l.getHandBrake() < 0.05f ? "brakes released" : null;
            if (why != null) {
                c.aessStopped = false;
                l.setTurnedOn(true);
                tell(l, "§eAESS: engine restarting (" + why + ") - stand clear");
            }
        }
    }

    /** traction motors: I²R heating, blown cooling; returns a derate */
    private static float motors(World mc, LocomotiveDiesel l, SimData.Car c, long now, double dt) {
        double ambient = l.ambientTemperature();
        if (Double.isNaN(c.tmC)) c.tmC = ambient;
        double f = 0;
        try {
            double start = l.getDefinition().getScriptedStartingTractionNewtons(l.gauge, l);
            double te = Math.abs(l.getAppliedTractiveEffort(l.getRealSpeed()));
            double db = l.getDynamicBrakeMultiplier() * l.getDynamicBrakeNewton();
            f = start > 0 ? Math.max(te, db) / start : 0;
        } catch (RuntimeException ignored) { }
        boolean blown = l.isTurnedOn() || l.prideElectric;
        double kmh = Math.abs(l.getCurrentSpeed().metric());
        double cool = (c.tmC - ambient) * (0.004 + (blown ? 0.009 : 0) + Math.min(0.004, kmh / 25000));
        c.tmC += (2.5 * f * f - cool) * dt;                 // full tractive effort: ~1.5 min to the 170 °C derate
        float d = 1f;
        if (c.tmC > 170) d = (float) Math.max(0.2, 1 - (c.tmC - 170) / 40);
        if (c.tmC > 200) fault(mc, l, c, now, "E310", String.format(Locale.ROOT, "TRACTION MOTOR OVERTEMP %d °C", Math.round(c.tmC)), AlarmManager.Sev.MAJOR);
        if (c.tmC < 150) clearAlarm(mc, l, "E310");
        return d;
    }

    // ---- faults ------------------------------------------------------------------------------------------------------

    static void fault(World mc, LocomotiveDiesel l, SimData.Car c, long now, String code, String text, AlarmManager.Sev sev) {
        if (c.hasFault(code)) return;
        c.fault(code, text, Recorder.clock(mc, now));
        SimData.get(mc).markDirty();
        Vec3d p = l.getPosition();
        boolean standing = code.equals("E400") || code.equals("E204") || code.equals("E310") || code.equals("E102") || code.equals("E210");
        AlarmManager.get(mc).raise(mc, key(l, code), AlarmManager.Cat.MACHINE, sev, label(l) + " " + code + " " + text, new BlockPos(p.x, p.y, p.z), standing);
        tell(l, "§c" + code + " " + text);
    }

    private static void clearAlarm(World mc, LocomotiveDiesel l, String code) {
        AlarmManager.get(mc).clear(key(l, code));
    }

    private static String key(LocomotiveDiesel l, String code) { return "engine:" + l.getUUID() + ":" + code; }

    /** FAULT RESET: drop latched faults whose condition is gone */
    public static String reset(World mc, LocomotiveDiesel l) {
        SimData data = SimData.get(mc);
        SimData.Car c = data.of(l.getUUID());
        int n = c.faults.size();
        c.faults.clear();
        for (String code : new String[]{"E101", "E102", "E120", "E121", "E130", "E204", "E210", "E211", "E310", "E400", "E401", "E402", "A400", "A510", "A511", "A520"}) clearAlarm(mc, l, code);
        data.markDirty();
        return n == 0 ? "§7No faults latched" : "§aFault reset - " + n + " cleared (they come back if the condition is still there)";
    }

    /** depot service: oil, coolant topped up, battery charged */
    public static void service(SimData.Car c) {
        c.oil = 1f;
        c.coolant = 1f;
        c.battery = Math.max(c.battery, 0.95f);
        c.flatMm = 0;
        c.coupler[0] = 1f;
        c.coupler[1] = 1f;
    }

    static String label(Locomotive l) {
        if (l.tag != null && !l.tag.isEmpty()) return l.tag;
        return l.getDefinition() == null ? "Loco" : l.getDefinition().name();
    }

    static void tell(Locomotive l, String msg) {
        for (EntityPlayer p : ConsistSim.riders(l)) if (p instanceof EntityPlayerMP mp) mp.sendStatusMessage(new TextComponentString(msg), true);
    }

    // ---- readouts ----------------------------------------------------------------------------------------------------

    public static int notch(LocomotiveDiesel l) {
        int n = Math.max(1, l.getDefinition().getThrottleNotches());
        return Math.round(Math.abs(l.getThrottle()) * n);
    }

    /** "RUN 742 rpm N5 · 88 °C · oil 420 kPa · boost 180 kPa" */
    public static String line1(LocomotiveDiesel l, SimData.Car c) {
        if (c == null) return "";
        String ph = c.aessStopped ? "AESS STOP" : PHASE[Math.max(0, Math.min(3, c.phase))];
        return String.format(Locale.ROOT, "%s %d rpm N%d · %d °C%s · oil %d kPa%s", ph, Math.round(c.rpm), notch(l), Math.round(l.getEngineTemperature()),
                c.fanHigh ? " FAN2" : c.fanLow ? " FAN1" : c.shutters ? " SHUT" : "", Math.round(c.oilKpa),
                c.boostKpa > 1 ? String.format(Locale.ROOT, " · boost %d kPa", Math.round(c.boostKpa)) : "");
    }

    /** "fuel 2,340 L 312 L/h · bat 73.8 V · TM 112 °C · derate 80% turbo" */
    public static String line2(LocomotiveDiesel l, SimData.Car c) {
        if (c == null) return "";
        return String.format(Locale.ROOT, "fuel %,d L %d L/h · bat %.1f V · TM %d °C%s", Math.round(l.getLiquidAmount() / 1000.0), Math.round(c.fuelLph),
                volts(c), Double.isNaN(c.tmC) ? 0 : Math.round(c.tmC),
                c.derate < 1 ? String.format(Locale.ROOT, " · power %d%% (%s)", Math.round(c.derate * 100), c.derateWhy) : "");
    }

    public static double volts(SimData.Car c) {
        double v = 64 + 10 * c.battery;
        if (c.phase == RUN) v = Math.max(v, 74.5);
        if (c.phase == CRANK) v -= 9;
        return v;
    }

    /** short status for the console's train list */
    public static String status(World mc, LocomotiveDiesel l) {
        SimData.Car c = SimData.get(mc).peek(l.getUUID());
        if (c == null || !SimConfig.engineManagement) return "";
        String s = c.aessStopped ? "AESS stop" : PHASE[Math.max(0, Math.min(3, c.phase))].toLowerCase(Locale.ROOT);
        if (c.phase == RUN) s += String.format(Locale.ROOT, " %d rpm %d °C", Math.round(c.rpm), Math.round(l.getEngineTemperature()));
        if (!c.faults.isEmpty()) s += " · §c" + c.faults.size() + " fault" + (c.faults.size() > 1 ? "s" : "") + "§r";
        if (c.derate < 1) s += " · power " + Math.round(c.derate * 100) + "%";
        return s;
    }
}
