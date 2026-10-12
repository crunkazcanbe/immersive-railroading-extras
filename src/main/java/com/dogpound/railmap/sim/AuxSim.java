package com.dogpound.railmap.sim;

import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.entity.LocomotiveDiesel;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.server.Electric;
import com.dogpound.railmap.server.RailProps;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Locale;

/**
 * Locomotive auxiliary systems (her ChatGPT list item 108), four times a second per diesel / electric unit:
 * <ul>
 *   <li><b>supply</b>: the main alternator's auxiliary winding while the engine runs (120 kW switcher, 200 kW road),
 *       the auxiliary converter on an electric with line (or traction battery) power (250 kW), else only the 74 V
 *       control battery - which sheds the big loads (HVAC, compressor, blowers). The battery switch kills everything;</li>
 *   <li><b>loads</b> (kW): control 0.6, headlights 0.5 bright / 0.2 dim, cab lights 0.1, wipers 0.15, cab HVAC up to
 *       8, compressor 25 while pumping, radiator fans 15 / 30 and traction motor blowers 20, fuel heater 0.5, layover
 *       heater 0.3 (diesel-fired). More than the alternator / converter gives = the battery makes up the rest (an
 *       overloaded converter trips: AUX RESET on the driver's display);</li>
 *   <li><b>battery</b>: 74 V, 36 kWh; charged by the alternator / converter, drained by what it carries alone;</li>
 *   <li><b>compressor + main reservoir</b>: IR's own governor (cut in 8.5, out 10 bar) only pumps with the compressor
 *       switch on AUTO and auxiliary power; a compressor pumping without a break heats up and trips until it cools
 *       (config); low main reservoir is alarmed;</li>
 *   <li><b>cab HVAC</b>: the cab follows the weather through its walls; HVAC heats / cools toward the set point;</li>
 *   <li><b>lights</b>: OFF / DIM / BRIGHT, dark with no battery (the fork's prideLights);</li>
 *   <li><b>wipers</b>: in rain under open sky, no wipers = a visibility warning;</li>
 *   <li><b>heaters</b>: below -10 °C without the fuel heater the fuel waxes and the engine won't start; the layover
 *       heater keeps a shut-down engine warm (it cools a fifth as fast), so it is ready to load sooner.</li>
 * </ul>
 * Everything shows on the driver's display (AUX page, soft keys), the Loco Diagnostic Panel and /irextras aux.
 */
public final class AuxSim {
    private AuxSim() {}

    static final double BATT_KWH = 36;

    /** bits for the driver's display (PacketCab.auxBits) */
    public static final int B_BATT = 1, B_HL_DIM = 2, B_HL_BRIGHT = 4, B_CABL = 8, B_HVAC = 16, B_WIPERS = 32, B_COMP = 64, B_COMP_RUN = 128,
            B_COMP_TRIP = 256, B_LAYOVER = 512, B_FUELH = 1024, B_CONV_TRIP = 2048, B_RAIN = 4096, B_SHED = 8192;

    static void tick(World mc, SimData data, long now, double dt) {
        if (!SimConfig.auxSystems) return;
        cam72cam.mod.world.World w = cam72cam.mod.world.World.get(mc);
        RailProps props = RailProps.get(mc);
        for (LocomotiveDiesel l : w.getEntities(LocomotiveDiesel.class)) {
            if (l.isDead() || l.getDefinition() == null) continue;
            SimData.Car c = data.of(l.getUUID());
            RailProps.Props pr = props.of(l.getUUID());
            boolean electric = Electric.isElectric(l, pr);
            try {
                unit(mc, l, c, electric, pr, now, dt);
            } catch (LinkageError | RuntimeException ex) {
                com.dogpound.railmap.RailMap.LOG.warn("[IR Extras] aux systems: {}", ex.toString());
            }
        }
    }

    /** before the engine model: a start that can't happen (battery isolated, waxed fuel) is refused */
    static void preStart(World mc, SimData data, long now) {
        if (!SimConfig.auxSystems) return;
        for (LocomotiveDiesel l : cam72cam.mod.world.World.get(mc).getEntities(LocomotiveDiesel.class)) {
            if (l.isDead() || l.getDefinition() == null || !l.isTurnedOn()) continue;
            SimData.Car c = data.peek(l.getUUID());
            if (c == null || c.phase != EngineSim.OFF || !c.init) continue;
            if (Electric.isElectric(l, RailProps.get(mc).of(l.getUUID()))) continue;
            if (!c.batteryOn) {
                l.setTurnedOn(false);
                EngineSim.tell(l, "§cBattery switch is OFF - nothing on the starter. Turn the battery on (driver's display AUX page)");
            } else if (l.ambientTemperature() < -10 && !c.fuelHeater) {
                l.setTurnedOn(false);
                EngineSim.fault(mc, l, c, now, "E130", "START FAILED - FUEL WAXED (FUEL HEATER OFF)", AlarmManager.Sev.MINOR);
            }
        }
    }

    private static void unit(World mc, LocomotiveDiesel l, SimData.Car c, boolean electric, RailProps.Props pr, long now, double dt) {
        double ambient = l.ambientTemperature();
        boolean running = electric ? l.isTurnedOn() || pr.source == 1 || pr.source == 3 : c.phase == EngineSim.RUN || !SimConfig.engineManagement && l.isTurnedOn();
        boolean line = electric && (pr.source == 1 || pr.source == 2 || pr.source == 3);
        double cap;
        String src;
        if (!c.batteryOn) { cap = 0; src = "BATT OFF"; }
        else if (electric && line && !c.convTrip) { cap = 250; src = pr.source == 2 ? "CONV TB" : "CONV"; }
        else if (!electric && running) { cap = EngineSim.family(l) == EngineSim.Family.SWITCHER ? 120 : 200; src = "ALT"; }
        else { cap = 0; src = "BATT"; }
        boolean big = cap > 0;                     // the big loads only run off the alternator / converter
        boolean battAlive = c.batteryOn && c.battery > 0.02f;

        // ---- loads
        double kw = 0;
        if (battAlive || big) {
            kw += 0.6;
            kw += c.headlight == 2 ? 0.5 : c.headlight == 1 ? 0.2 : 0;
            if (c.cabLights) kw += 0.1;
            if (c.wipers) kw += 0.15;
            if (c.fuelHeater && ambient < 0) kw += 0.5;
            if (c.layoverHeater && !running && !electric) kw += 0.3;
        }
        // cab HVAC
        if (Double.isNaN(c.cabC)) c.cabC = ambient;
        double hvacKw = 0;
        if (c.hvac && big) {
            double err = c.cabSet - c.cabC;
            hvacKw = Math.min(8, Math.abs(err) * 1.6);
            c.cabC += Math.signum(err) * hvacKw * 0.004 * dt;
        }
        c.cabC += (ambient - c.cabC) * dt / 900.0;
        kw += hvacKw;
        // radiator fans / traction motor blowers
        if (big) {
            if (c.fanHigh) kw += 30; else if (c.fanLow) kw += 15;
            if (electric && Math.abs(l.getThrottle()) > 0.05f) kw += 20;
        }
        // compressor: IR's governor asks (isLowAir), we decide whether it can run
        if (Double.isNaN(c.compC)) c.compC = ambient;
        boolean wants = l.getDefinition().hasCompressor() && !l.getDefinition().isCabCar();
        boolean can = wants && c.compressor == 1 && big && !c.compTrip;
        l.compressorActive = !wants || can;
        c.compRun = can && l.isLowAir();
        if (c.compRun) { kw += 25; c.compC += (150 - c.compC) * dt / 600.0; }
        else c.compC += (ambient - c.compC) * dt / 300.0;
        if (SimConfig.compressorTrip && c.compRun && c.compC > 125) {
            c.compTrip = true;
            EngineSim.fault(mc, l, c, now, "A510", "COMPRESSOR THERMAL TRIP", AlarmManager.Sev.MINOR);
        }
        if (c.compTrip && c.compC < 80) { c.compTrip = false; clear(mc, l, "A510"); }
        float mr = l.getMainAirReservoir();
        if (wants && mr < 0.5f && cfgRealistic() && Math.abs(l.getReverser()) > 0.05f) EngineSim.fault(mc, l, c, now, "A511", String.format(Locale.ROOT, "LOW MAIN RESERVOIR %.1f BAR", mr * 10), AlarmManager.Sev.MINOR);
        else if (mr >= 0.8f) clear(mc, l, "A511");

        // shed the big loads on battery alone
        boolean shed = !big && (hvacKw > 0 || c.compRun);
        c.auxKw = kw;
        c.auxCap = cap;
        c.auxSrc = src;
        c.auxOk = battAlive || big;

        // converter overload trips (electric); an alternator just leans on the battery
        double over = Math.max(0, kw - cap);
        if (electric && big && over > 0 && !c.convTrip) {
            c.convTrip = true;
            EngineSim.fault(mc, l, c, now, "A520", String.format(Locale.ROOT, "AUX CONVERTER OVERLOAD %d/%d KW - TRIPPED", Math.round(kw), Math.round(cap)), AlarmManager.Sev.MINOR);
        }

        // ---- battery: charge from the alternator / converter, carry what they can't (EngineSim already drains a
        // shut-down diesel's standing 3 kW and charges a running one)
        double net;
        if (!c.batteryOn) net = 0;
        else if (big) net = electric ? Math.min(12, cap - kw) : -over;
        else net = -(electric ? kw : Math.max(0, kw - 3.2));
        if (electric && big && c.battery < 1f) net = Math.max(net, 6);
        c.batKw = net;
        if (electric || net < 0 || !big) c.battery = (float) Math.max(0, Math.min(1, c.battery + net * dt / 3600.0 / BATT_KWH));
        if (electric && c.batteryOn && c.battery < 0.3f) EngineSim.fault(mc, l, c, now, "A400", "CONTROL BATTERY LOW", AlarmManager.Sev.MINOR);
        else if (electric && c.battery >= 0.35f) clear(mc, l, "A400");

        // ---- layover heater: a shut-down engine keeps its heat
        if (!electric && c.layoverHeater && !running && battAlive && SimConfig.engineManagement) l.prideCooling = 0.2f;

        // ---- lights
        int lights = !c.auxOk || c.headlight == 0 ? 1 : c.headlight == 1 ? 2 : 0;
        if (l.prideLights != lights) l.prideLights = lights;

        // ---- wipers
        Vec3d p = l.getPosition();
        BlockPos bp = new BlockPos(p.x, p.y + 3, p.z);
        boolean rain = mc.isRaining() && mc.canSeeSky(bp) && mc.getBiome(bp).canRain();
        c.rainNoWipers = rain && !c.wipers;
        if (shed && now % 200 == 3) EngineSim.tell(l, "§eOn battery only - HVAC and compressor shed until the engine / line is back");
    }

    /** IR only empties the main reservoir in its realistic brake mode */
    private static boolean cfgRealistic() {
        return cam72cam.immersiverailroading.Config.ImmersionConfig.brakeMode == cam72cam.immersiverailroading.library.BrakeMode.REALISTIC;
    }

    private static void clear(World mc, Locomotive l, String code) {
        AlarmManager.get(mc).clear("engine:" + l.getUUID() + ":" + code);
        SimData.Car c = SimData.get(mc).peek(l.getUUID());
        if (c != null) c.faults.removeIf(f -> f.startsWith(code + " "));
    }

    public static int bits(SimData.Car c, Locomotive l) {
        int b = 0;
        if (c.batteryOn) b |= B_BATT;
        if (c.headlight == 1) b |= B_HL_DIM;
        if (c.headlight == 2) b |= B_HL_BRIGHT;
        if (c.cabLights) b |= B_CABL;
        if (c.hvac) b |= B_HVAC;
        if (c.wipers) b |= B_WIPERS;
        if (c.compressor == 1) b |= B_COMP;
        if (c.compRun) b |= B_COMP_RUN;
        if (c.compTrip) b |= B_COMP_TRIP;
        if (c.layoverHeater) b |= B_LAYOVER;
        if (c.fuelHeater) b |= B_FUELH;
        if (c.convTrip) b |= B_CONV_TRIP;
        if (c.rainNoWipers) b |= B_RAIN;
        if (c.auxCap <= 0 && c.batteryOn) b |= B_SHED;
        return b;
    }

    public static double volts(SimData.Car c) {
        if (!c.batteryOn) return 0;
        double v = EngineSim.volts(c);
        if (c.auxCap > 0) v = Math.max(v, 74.5);
        return v;
    }

    /** "AUX ALT 64/200 kW · MR 9.2 bar COMP · BAT 74.1 V +2 kW · CAB 21 °C" */
    public static String line(Locomotive l, SimData.Car c) {
        if (c == null || !SimConfig.auxSystems) return "";
        return String.format(Locale.ROOT, "AUX %s %d/%d kW · MR %.1f bar%s · BAT %.1f V %+.0f kW · CAB %d °C%s", c.auxSrc, Math.round(c.auxKw), Math.round(c.auxCap),
                l.getMainAirReservoir() * 10, c.compTrip ? " TRIP" : c.compRun ? " COMP" : "", volts(c), c.batKw, Math.round(Double.isNaN(c.cabC) ? 0 : c.cabC),
                c.convTrip ? " · CONV TRIP" : "");
    }

    /** a driver's switch (display soft key / command / diag panel); returns the message */
    public static String toggle(World mc, Locomotive l, String what) {
        SimData data = SimData.get(mc);
        SimData.Car c = data.of(l.getUUID());
        String r;
        switch (what) {
            case "hl": c.headlight = (c.headlight + 2) % 3; r = "Headlights " + new String[]{"OFF", "DIM", "BRIGHT"}[c.headlight]; break;
            case "cabl": c.cabLights = !c.cabLights; r = "Cab lights " + on(c.cabLights); break;
            case "hvac": c.hvac = !c.hvac; r = "Cab HVAC " + on(c.hvac) + " (set " + c.cabSet + " °C)"; break;
            case "set+": c.cabSet = Math.min(28, c.cabSet + 1); r = "HVAC set " + c.cabSet + " °C"; break;
            case "set-": c.cabSet = Math.max(14, c.cabSet - 1); r = "HVAC set " + c.cabSet + " °C"; break;
            case "wip": c.wipers = !c.wipers; r = "Wipers " + on(c.wipers); break;
            case "comp": c.compressor = c.compressor == 1 ? 0 : 1; r = "Compressor " + (c.compressor == 1 ? "AUTO" : "OFF"); break;
            case "bat":
                c.batteryOn = !c.batteryOn;
                if (!c.batteryOn && l instanceof LocomotiveDiesel d && d.isTurnedOn() && !Electric.isElectric(d, RailProps.get(mc).of(d.getUUID()))) d.setTurnedOn(false);
                r = c.batteryOn ? "§aBattery switch ON" : "§eBattery switch OFF - unit dead";
                break;
            case "lay": c.layoverHeater = !c.layoverHeater; r = "Layover heater " + on(c.layoverHeater); break;
            case "fuh": c.fuelHeater = !c.fuelHeater; r = "Fuel heater " + on(c.fuelHeater); break;
            case "reset":
                c.convTrip = false;
                clear(mc, l, "A520");
                r = c.compTrip ? "Aux converter reset - compressor still too hot to reset" : "Aux converter reset";
                break;
            default: return "";
        }
        data.markDirty();
        return "§7" + r;
    }

    static String on(boolean b) { return b ? "ON" : "OFF"; }

    /** /irextras aux [hl|cabl|hvac|set+|set-|wip|comp|bat|lay|fuh|reset] */
    public static void command(World world, net.minecraft.command.ICommandSender sender, String[] args) {
        Locomotive l = TrainSim.nearestLoco(world, sender);
        if (l == null) { TrainSim.say(sender, "§7No locomotive within 16 blocks."); return; }
        if (args.length > 1) { TrainSim.say(sender, toggle(world, l, args[1].toLowerCase(Locale.ROOT))); return; }
        SimData.Car c = SimData.get(world).of(l.getUUID());
        TrainSim.say(sender, "§b" + EngineSim.label(l) + " auxiliaries: §f" + line(l, c));
        TrainSim.say(sender, String.format(Locale.ROOT, "§7battery %s %d%% · headlights %s · cab lights %s · HVAC %s %d °C · wipers %s · compressor %s%s · layover heater %s · fuel heater %s",
                on(c.batteryOn), Math.round(c.battery * 100), new String[]{"OFF", "DIM", "BRIGHT"}[c.headlight], on(c.cabLights), on(c.hvac), c.cabSet, on(c.wipers),
                c.compressor == 1 ? "AUTO" : "OFF", c.compTrip ? " (TRIPPED " + Math.round(c.compC) + " °C)" : "", on(c.layoverHeater), on(c.fuelHeater)));
        TrainSim.say(sender, "§8/irextras aux hl | cabl | hvac | set+ | set- | wip | comp | bat | lay | fuh | reset");
    }
}
