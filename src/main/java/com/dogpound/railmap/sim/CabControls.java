package com.dogpound.railmap.sim;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.entity.LocomotiveDiesel;
import cam72cam.immersiverailroading.entity.LocomotiveSteam;
import cam72cam.immersiverailroading.library.Permissions;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapConfig;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.DriverAids;
import com.dogpound.railmap.ohle.OhleData;
import com.dogpound.railmap.safety.RadioNet;
import com.dogpound.railmap.safety.SafetyData;
import com.dogpound.railmap.server.Electric;
import com.dogpound.railmap.server.RailProps;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.*;

/**
 * Working locomotive cab controls (requested feature): the server side of the cab desk (client.render.CabDeskRenderer)
 * and of any locomotive model that wires a WIDGET to one of the CAB_* control groups below.
 * <p>
 * Every switch and handle lives in IR's own per-locomotive control-group map, so it is saved with the loco and synced
 * to every client by IR (multiplayer: everyone in the cab sees the same handle positions). Twice a second the server
 * mirrors those groups both ways with the real systems: AuxSim (headlights, cab light), the OHLE pantograph, IR's bell,
 * the automatic brake valve, the isolate switch, the driver aids lamps and the cab radio. A model author gets a
 * working switch by naming a group e.g. {@code WIDGET_1_CG_CAB_HEADLIGHT}.
 * <ul>
 *   <li>CAB_AUTOBRAKE: 0 LAP (default, old saves), 0.25 RELEASE, 0.5 SERVICE, 1 EMERGENCY. RELEASE bleeds the train
 *   brake off, SERVICE makes a graduated application (first reduction, then 15 %/s to full service), LAP holds,
 *   EMERGENCY dumps the pipe. Any brake change the handle didn't make (keyboard, hand controller, protection, radio)
 *   puts the handle back to LAP so it never fights them.</li>
 *   <li>CAB_HEADLIGHT 0/0.5/1 OFF/DIM/BRIGHT, CAB_LIGHT, CAB_BELL, CAB_ISOLATE: 0/1; CAB_PANTO 0 AUTO, 0.5 DOWN, 1 UP.</li>
 *   <li>read-only lamps written by the server: CAB_ELEC, CAB_ALERT (0.5 vigilance / AWS horn, 1 penalty / SPAD),
 *   CAB_AWS (sunflower), CAB_SANDL, CAB_SLIP, CAB_FLT, CAB_PANST (0.5 raising, 1 up + VCB closed), CAB_RADIO
 *   (0.5 call placed, 1 instruction waiting), CAB_RPM (rpm / 2000).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class CabControls {
    private CabControls() {}

    public static final String AUTOBRAKE = "CAB_AUTOBRAKE", HEADLIGHT = "CAB_HEADLIGHT", LIGHT = "CAB_LIGHT", BELL = "CAB_BELL",
            ISOLATE = "CAB_ISOLATE", PANTO = "CAB_PANTO", ELEC = "CAB_ELEC", ALERT = "CAB_ALERT", AWS = "CAB_AWS", SANDL = "CAB_SANDL",
            SLIP = "CAB_SLIP", FLT = "CAB_FLT", PANST = "CAB_PANST", RADIO = "CAB_RADIO", RPM = "CAB_RPM";
    public static final float LAP = 0f, REL = 0.25f, SVC = 0.5f, EMG = 1f;
    /** diesel / electric throttle notches (IDLE + 8) */
    public static final int NOTCHES = 8;

    /** per loco: what the mirror last wrote / saw in each two-way group, the brake value the handle last set, timers */
    private static final class St {
        final Map<String, Float> seen = new HashMap<>();
        float wroteBrake = Float.NaN;
        boolean emgByUs;
        long lastCall, isoWarned;
    }

    private static final Map<UUID, St> STATE = new HashMap<>();

    public static void clear() { STATE.clear(); }

    // =============================================================================================== desk keys

    /**
     * A cab desk key from a client ({@code d:<action>@<entityId>}, via PacketCabKey / CabKeys). The player has to be on
     * that loco's train; an MU trailing unit's desk commands the lead.
     */
    public static String press(World mc, EntityPlayer p, String key) {
        if (!RailMapConfig.cabControls) return "§7Cab controls are switched off (config cabControls)";
        int at = key.lastIndexOf('@');
        if (at < 3) return "";
        String action = key.substring(2, at);
        int id;
        try { id = Integer.parseInt(key.substring(at + 1)); } catch (NumberFormatException e) { return ""; }
        Locomotive loco = null;
        for (Locomotive l : cam72cam.mod.world.World.get(mc).getEntities(Locomotive.class))
            if (!l.isDead() && l.internal.getEntityId() == id) { loco = l; break; }
        if (loco == null) return "";
        if (!onTrain(loco, p)) return "§7You have to be in the cab to work the controls";
        if (p.getDistanceSq(loco.internal) > 40 * 40) return "";
        return apply(mc, lead(mc, loco), p, action);
    }

    static boolean onTrain(Locomotive loco, EntityPlayer p) {
        List<EntityCoupleableRollingStock> cars = new ArrayList<>(loco.getTrain(false));
        if (cars.isEmpty()) cars.add(loco);
        for (EntityCoupleableRollingStock s : cars)
            for (cam72cam.mod.entity.Entity e : s.getPassengers()) if (e.internal == p) return true;
        return false;
    }

    static Locomotive lead(World mc, Locomotive l) {
        MultiUnit.Group g = MultiUnit.group(l.getUUID());
        if (g == null || g.lead.equals(l.getUUID())) return l;
        for (Locomotive x : cam72cam.mod.world.World.get(mc).getEntities(Locomotive.class)) if (x.getUUID().equals(g.lead)) return x;
        return l;
    }

    private static boolean may(EntityPlayer p, cam72cam.mod.entity.Player.PermissionAction a) {
        if (p == null) return true;
        try { return new cam72cam.mod.entity.Player(p).hasPermission(a); } catch (RuntimeException e) { return true; }
    }

    public static boolean electric(World mc, Locomotive l) {
        return l instanceof LocomotiveDiesel && Electric.isElectric(l, RailProps.get(mc).of(l.getUUID()));
    }

    /** one action on one loco (the desk, the self-test and /irextras cab key all come through here). p may be null. */
    public static String apply(World mc, Locomotive l, EntityPlayer p, String action) {
        boolean steam = l instanceof LocomotiveSteam;
        LocomotiveDiesel d = l instanceof LocomotiveDiesel x ? x : null;
        boolean brakeKey = action.startsWith("abrk") || action.startsWith("ibrk") || action.startsWith("dyn");
        if (brakeKey ? !may(p, Permissions.BRAKE_CONTROL) : !may(p, Permissions.LOCOMOTIVE_CONTROL)) return "§cYou may not drive this locomotive";
        String r;
        switch (action) {
            // ---- power
            case "thr+": case "thr-": case "thr0": {
                if (l.getControlPosition(ISOLATE) > 0.5f && !action.equals("thr0")) return "§eUnit ISOLATED - put the isolate switch to RUN first";
                float t = l.getThrottle();
                if (steam) {                                   // the regulator opens smoothly, 5 % a click
                    t = action.equals("thr0") ? 0 : clamp(t + (action.equals("thr+") ? 0.05f : -0.05f), 0, 1);
                    l.setThrottle(t);
                    r = "Regulator " + Math.round(t * 100) + " %";
                } else {
                    int n = Math.round(t * NOTCHES);
                    n = action.equals("thr0") ? 0 : Math.max(0, Math.min(NOTCHES, n + (action.equals("thr+") ? 1 : -1)));
                    if (d != null && n > 0 && d.getDynamicBrake() > 0.01f) return "§eTake the dynamic brake off before you notch up";
                    l.setThrottle(n / (float) NOTCHES);
                    r = n == 0 ? "Throttle IDLE" : "Throttle notch " + n;
                }
                sound(mc, l, "cab.notch", "lever_clack", 0.5f);
                return "§7" + r;
            }
            case "rev+": case "rev-": {
                float rv = l.getReverser();
                if (steam) {                                   // cut-off: 10 % a turn of the screw
                    rv = clamp(Math.round((rv + (action.equals("rev+") ? 0.1f : -0.1f)) * 10) / 10f, -1, 1);
                    l.setReverser(rv);
                    r = rv == 0 ? "Reverser mid-gear" : (rv > 0 ? "Forward " : "Reverse ") + Math.round(Math.abs(rv) * 100) + " % cut-off";
                } else {
                    if (speed(l) > 3 && l.getThrottle() > 0.01f) return "§eReverser locked - throttle to IDLE and stop first";
                    int pos = Math.round(rv) + (action.equals("rev+") ? 1 : -1);
                    pos = Math.max(-1, Math.min(1, pos));
                    l.setReverser(pos);
                    r = "Reverser " + new String[]{"REVERSE", "NEUTRAL", "FORWARD"}[pos + 1];
                }
                sound(mc, l, "cab.reverser", "lever_heavy", 0.5f);
                return "§7" + r;
            }
            // ---- brakes
            case "abrk+": case "abrk-": case "abrk:rel": case "abrk:lap": case "abrk:svc": case "abrk:emg": {
                float[] order = {REL, LAP, SVC, EMG};
                float cur = l.getControlPosition(AUTOBRAKE);
                int i = 1;
                for (int k = 0; k < 4; k++) if (Math.abs(order[k] - cur) < 0.05f) i = k;
                if (action.equals("abrk+")) i = Math.min(3, i + 1);
                else if (action.equals("abrk-")) i = Math.max(0, i - 1);
                else i = Arrays.asList("abrk:rel", "abrk:lap", "abrk:svc", "abrk:emg").indexOf(action);
                setAutoBrake(mc, l, order[i], p);
                return "§7Automatic brake " + new String[]{"RELEASE", "LAP", "SERVICE", "EMERGENCY"}[i];
            }
            case "ibrk+": case "ibrk-": case "ibrk0": {
                float v = action.equals("ibrk0") ? 0 : clamp(l.getIndependentBrake() + (action.equals("ibrk+") ? 0.1f : -0.1f), 0, 1);
                l.setIndependentBrake(v);
                sound(mc, l, v > 0 ? "cab.brake_apply" : "cab.brake_release", "relay_click", 0.4f);
                return "§7Independent brake " + Math.round(v * 100) + " %";
            }
            case "dyn+": case "dyn-": {
                if (d == null) return "§7No dynamic brake on a steam locomotive";
                if (action.equals("dyn+") && d.getThrottle() > 0.01f) return "§eThrottle to IDLE before setting up the dynamic brake";
                float v = clamp(d.getDynamicBrake() + (action.equals("dyn+") ? 0.125f : -0.125f), 0, 1);
                d.setDynamicBrake(v);
                sound(mc, l, "cab.notch", "lever_clack", 0.4f);
                return "§7Dynamic brake " + (v <= 0 ? "OFF" : "notch " + Math.round(v * 8));
            }
            // ---- horn / bell / sand
            case "horn":
                l.setHorn(10, p == null ? null : p.getUniqueID());
                return "";
            case "bell": {
                boolean on = l.getControlPosition(BELL) < 0.5f;
                l.setControlPosition(BELL, on ? 1 : 0);
                l.setBell(on ? 20 : 0);
                st(l).seen.put(BELL, on ? 1f : 0f);
                sound(mc, l, "cab.switch", "selector_turn", 0.4f);
                return "§7Bell " + (on ? "ON" : "OFF");
            }
            case "sand": {
                SimData.Car c = SimData.get(mc).of(l.getUUID());
                c.sandHold = 3;
                sound(mc, l, "cab.button", "button_press", 0.4f);
                return "§7Sanders on for 3 s";
            }
            // ---- lights
            case "hl": {
                String s = AuxSim.toggle(mc, l, "hl");
                l.setControlPosition(HEADLIGHT, SimData.get(mc).of(l.getUUID()).headlight / 2f);
                st(l).seen.put(HEADLIGHT, l.getControlPosition(HEADLIGHT));
                sound(mc, l, "cab.switch", "selector_turn", 0.4f);
                return s;
            }
            case "cabl": {
                String s = AuxSim.toggle(mc, l, "cabl");
                l.setControlPosition(LIGHT, SimData.get(mc).of(l.getUUID()).cabLights ? 1 : 0);
                st(l).seen.put(LIGHT, l.getControlPosition(LIGHT));
                sound(mc, l, "cab.switch", "selector_turn", 0.4f);
                return s;
            }
            // ---- engine, isolate, pantograph, fault reset
            case "eng": {
                if (d == null) return "§7Steam: raise steam at the firebox";
                if (electric(mc, l)) return "§7Electric: use the pantograph and main breaker";
                sound(mc, l, "cab.button", "button_press", 0.4f);
                return MultiUnit.group(l.getUUID()) != null ? MultiUnit.engines(mc, l, !d.isTurnedOn()) : startStop(d);
            }
            case "iso": {
                boolean iso = l.getControlPosition(ISOLATE) < 0.5f;
                l.setControlPosition(ISOLATE, iso ? 1 : 0);
                if (iso) l.setThrottle(0);
                sound(mc, l, "cab.key", "key_switch", 0.5f);
                Recorder.event(mc, EngineSim.label(l) + ": unit " + (iso ? "ISOLATED" : "back to RUN") + (p == null ? "" : " by " + p.getName()));
                return iso ? "§eUnit ISOLATED - engine stays running, no traction" : "§aUnit to RUN - traction available";
            }
            case "pan": {
                if (!electric(mc, l)) return "§7No pantograph on this locomotive";
                float cur = l.getControlPosition(PANTO);
                float next = cur < 0.25f ? 1f : cur > 0.75f ? 0.5f : 0f;   // AUTO -> UP -> DOWN -> AUTO
                l.setControlPosition(PANTO, next);
                applyPanto(mc, l, next);
                st(l).seen.put(PANTO, next);
                sound(mc, l, "cab.switch", "selector_turn", 0.5f);
                return "§7Pantograph " + (next == 0 ? "AUTO" : next == 1 ? "UP (manual)" : "DOWN (manual)");
            }
            case "flt":
                sound(mc, l, "cab.button", "button_press", 0.4f);
                return p == null ? "" : CabKeys.press(mc, p, "fltreset");
            // ---- train protection + radio
            case "ack":
                sound(mc, l, "cab.button", "ack_beep", 0.5f);
                return p == null ? "" : DriverAids.ack(mc, p);
            case "radio":
                return radio(mc, l, p);
            default:
                return "";
        }
    }

    private static String startStop(LocomotiveDiesel d) {
        d.setTurnedOn(!d.isTurnedOn());
        return d.isTurnedOn() ? "§aEngine start" : "§eEngine stop";
    }

    static void setAutoBrake(World mc, Locomotive l, float v, EntityPlayer p) {
        float was = l.getControlPosition(AUTOBRAKE);
        l.setControlPosition(AUTOBRAKE, v);
        St s = st(l);
        s.wroteBrake = l.getTrainBrakePos();
        if (v == EMG && was != EMG) {
            l.setTrainBrake(1);
            l.setThrottle(0);
            l.setEmergency(true);
            s.emgByUs = true;
            s.wroteBrake = 1;
            sound(mc, l, "cab.brake_emergency", "air_release", 1f);
            Recorder.event(mc, EngineSim.label(l) + ": EMERGENCY brake from the cab" + (p == null ? "" : " by " + p.getName()) + " at "
                    + Math.round(speed(l)) + " km/h");
        } else if (v != EMG && s.emgByUs) {
            l.setEmergency(false);
            s.emgByUs = false;
        }
        if (v == REL) sound(mc, l, "cab.brake_release", "air_release", 0.4f);
        else if (v != was) sound(mc, l, "cab.lever", "lever_clack", 0.5f);
    }

    private static void applyPanto(World mc, Locomotive l, float v) {
        OhleData.Pan pan = OhleData.get(mc).pan(l.getUUID());
        if (v < 0.25f) { pan.manual = false; pan.downZone = false; }
        else { pan.manual = true; pan.wantUp = v > 0.75f; }
        OhleData.get(mc).markDirty();
    }

    // =============================================================================================== radio handset

    private static String radio(World mc, Locomotive l, EntityPlayer p) {
        SafetyData d = SafetyData.get(mc);
        SafetyData.TrainRec r = train(d, l);
        sound(mc, l, "cab.radio", "ack_beep", 0.6f);
        if (r == null) return "§7Radio: train not registered (no train describer / tracking yet)";
        // an instruction waiting for this train: lifting the handset acknowledges it
        for (SafetyData.Comm c : d.comms)
            if (r.id.equals(c.train) && c.kind == 0 && c.ack == 0)
                return RadioNet.acknowledge(mc, c, p == null ? "self-test" : p.getName(), true);
        if (r.radio != 1) return r.radio == 2 ? "§cRadio: NO NETWORK - link lost" : "§7Radio: no network here (outside radio territory)";
        St s = st(l);
        long now = mc.getTotalWorldTime();
        boolean emergency = now - s.lastCall < 60;                     // pressed again within 3 s: emergency call
        s.lastCall = now;
        SafetyData.Comm c = d.log(mc, r.headcode.isEmpty() ? EngineSim.label(l) : r.headcode, "SIGNALLER",
                emergency ? "EMERGENCY CALL FROM THE CAB" : "DRIVER REQUESTS CONTACT" + (r.berth.isEmpty() ? "" : " - at " + r.berth), emergency ? 1 : 2, 0);
        c.train = r.id;
        c.pos = new BlockPos(l.getPosition().x, l.getPosition().y, l.getPosition().z).toLong();
        return emergency ? "§c[RADIO] EMERGENCY CALL placed to the signaller" : "§e[RADIO] Call placed to the signaller (press twice quickly for an EMERGENCY call)";
    }

    static SafetyData.TrainRec train(SafetyData d, Locomotive l) {
        for (SafetyData.TrainRec r : d.trains.values())
            if (!r.gone && (l.getUUID().equals(r.lead) || r.members.contains(l.getUUID()))) return r;
        return null;
    }

    // =============================================================================================== the mirror

    private static St st(Locomotive l) { return STATE.computeIfAbsent(l.getUUID(), k -> new St()); }

    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote || !RailMapConfig.cabControls) return;
        World mc = e.world;
        long now = mc.getTotalWorldTime();
        if (now % 4 != 0) return;
        try {
            for (Locomotive l : cam72cam.mod.world.World.get(mc).getEntities(Locomotive.class)) {
                if (l.isDead()) continue;
                if (!hasPlayer(l) && !STATE.containsKey(l.getUUID())) continue;   // nobody aboard and never touched
                mirror(mc, l, now);
            }
        } catch (LinkageError | RuntimeException ex) {
            RailMap.LOG.warn("[irextras] cab controls: {}", ex.toString());
        }
    }

    private static boolean hasPlayer(Locomotive l) {
        for (cam72cam.mod.entity.Entity e : l.getPassengers()) if (e.internal instanceof EntityPlayer) return true;
        return false;
    }

    /** one pass of the two-way mirror for one loco (every 4 ticks; the lamps every 10 ticks) */
    static void mirror(World mc, Locomotive l, long now) {
        St s = st(l);
        brakeValve(mc, l, s);
        // isolate: no traction while isolated, whoever moves the throttle
        if (l.getControlPosition(ISOLATE) > 0.5f && l.getThrottle() > 0.001f) {
            l.setThrottle(0);
            if (now - s.isoWarned > 200) { s.isoWarned = now; tell(l, "§eUnit ISOLATED - no traction (isolate switch in the cab)"); }
        }
        // bell switch held on
        if (l.getControlPosition(BELL) > 0.5f && l.getBell() < 5) l.setBell(20);
        SimData data = SimData.get(mc);
        SimData.Car c = data.of(l.getUUID());
        // headlights / cab light: a model switch moved -> the sim; else the sim -> the switch
        two(l, s, HEADLIGHT, c.headlight / 2f, v -> { c.headlight = Math.round(v * 2); data.markDirty(); });
        two(l, s, LIGHT, c.cabLights ? 1f : 0f, v -> { c.cabLights = v > 0.5f; data.markDirty(); });
        boolean elec = electric(mc, l);
        set(l, ELEC, elec ? 1 : 0);
        if (elec) {
            OhleData.Pan pan = OhleData.get(mc).pan(l.getUUID());
            float simPan = !pan.manual ? 0f : pan.wantUp ? 1f : 0.5f;
            two(l, s, PANTO, simPan, v -> applyPanto(mc, l, v));
            set(l, PANST, pan.up && pan.vcb ? 1f : pan.up || pan.raiseAt != 0 ? 0.5f : 0f);
        }
        // lamps
        int b = DriverAids.bits(l.getUUID(), now);
        set(l, ALERT, (b & DriverAids.PENALTIES) != 0 ? 1f : (b & DriverAids.WARNINGS) != 0 ? 0.5f : 0f);   // incl. batch-3 cab signal + trip cock
        set(l, AWS, (b & DriverAids.AWS_SUNFLOWER) != 0 ? 1 : 0);
        set(l, SANDL, c.sanding ? 1 : 0);
        set(l, SLIP, c.slip || c.slide ? 1 : 0);
        set(l, FLT, c.faults.isEmpty() ? 0 : 1);
        set(l, RPM, (float) Math.min(1, c.rpm / 2000.0));
        SafetyData sd = SafetyData.get(mc);
        SafetyData.TrainRec r = train(sd, l);
        float rad = 0;
        if (r != null) {
            for (SafetyData.Comm cm : sd.comms) {
                if (!r.id.equals(cm.train)) continue;
                if (cm.kind == 0 && cm.ack == 0) { rad = 1; break; }
                if (cm.kind != 0 && now - cm.tick < 600 && rad == 0) rad = 0.5f;
            }
        }
        set(l, RADIO, rad);
    }

    /** the automatic brake valve: RELEASE / SERVICE move the train brake, LAP holds, outside changes put it to LAP */
    private static void brakeValve(World mc, Locomotive l, St s) {
        float h = l.getControlPosition(AUTOBRAKE);
        float tb = l.getTrainBrakePos();
        if (!Float.isNaN(s.wroteBrake) && Math.abs(tb - s.wroteBrake) > 0.02f && !(h == EMG && tb >= 0.99f)) {
            if (h != LAP) l.setControlPosition(AUTOBRAKE, LAP);                // keyboard / protection / radio took over
            if (s.emgByUs) { l.setEmergency(false); s.emgByUs = false; }
            s.wroteBrake = tb;
            return;
        }
        float want = tb;
        if (h == REL) want = Math.max(0, tb - 0.05f);                         // 0.25 / s release
        else if (h == SVC) want = tb < 0.15f ? 0.15f : Math.min(0.85f, tb + 0.03f);   // first reduction, then 15 %/s to full service
        else if (h == EMG) want = 1f;
        if (want != tb) l.setTrainBrake(want);
        s.wroteBrake = want;
    }

    private interface Apply { void to(float v); }

    private static void two(Locomotive l, St s, String g, float sim, Apply apply) {
        float grp = l.getControlPosition(g);
        Float seen = s.seen.get(g);
        if (seen != null && Math.abs(grp - seen) > 0.01f) {           // a model widget moved it
            apply.to(grp);
            s.seen.put(g, grp);
            return;
        }
        if (Math.abs(grp - sim) > 0.01f) l.setControlPosition(g, sim);
        s.seen.put(g, sim);
    }

    private static void set(Locomotive l, String g, float v) {
        if (Math.abs(l.getControlPosition(g) - v) > 0.004f) l.setControlPosition(g, v);
    }

    // =============================================================================================== helpers

    static double speed(Locomotive l) { return l.getCurrentSpeed() == null ? 0 : Math.abs(l.getCurrentSpeed().metric()); }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    private static void tell(Locomotive l, String msg) {
        for (cam72cam.mod.entity.Entity e : l.getPassengers())
            if (e.internal instanceof EntityPlayer p) p.sendStatusMessage(new TextComponentString(msg), true);
    }

    /**
     * a cab sound: the sound pack's irextras:cab.* event when it exists, else our own named sound, else vanilla
     * (no hard link to ModSounds fields: the sound pack is being reworked alongside)
     */
    static void sound(World mc, Locomotive l, String name, String fallback, float vol) {
        SoundEvent ev = SoundEvent.REGISTRY.getObject(new ResourceLocation(RailMap.MODID, name));
        if (ev == null) ev = SoundEvent.REGISTRY.getObject(new ResourceLocation(RailMap.MODID, fallback));
        if (ev == null) ev = fallback.equals("air_release") ? net.minecraft.init.SoundEvents.ENTITY_GENERIC_EXTINGUISH_FIRE : net.minecraft.init.SoundEvents.BLOCK_LEVER_CLICK;
        cam72cam.mod.math.Vec3d v = l.getPosition();
        mc.playSound(null, v.x, v.y + 1.5, v.z, ev, SoundCategory.NEUTRAL, vol, 1f);
    }

    // =============================================================================================== command + self-test

    public static final String USAGE = "cab [status|test|key <action>]";

    /** /irextras cab status | test | key <action>: on the loco you ride, else the nearest within 32 blocks */
    public static boolean command(World mc, ICommandSender sender, String[] a) {
        if (a.length == 0 || !a[0].equalsIgnoreCase("cab")) return false;
        EntityPlayer p = sender instanceof EntityPlayer x ? x : null;
        Locomotive l = null;
        if (p != null) l = CabKeys.driven(mc, p);
        if (l == null) {
            double bd = 32 * 32;
            net.minecraft.util.math.Vec3d at = sender.getPositionVector();
            for (Locomotive x : cam72cam.mod.world.World.get(mc).getEntities(Locomotive.class)) {
                double dd = x.getPosition().distanceToSquared(new cam72cam.mod.math.Vec3d(at.x, at.y, at.z));
                if (dd < bd) { bd = dd; l = x; }
            }
        }
        if (l == null) { say(sender, "§cNo locomotive within 32 blocks"); return true; }
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "status";
        switch (sub) {
            case "key":
                if (a.length < 3) { say(sender, "§c/irextras cab key <thr+|thr-|thr0|rev+|rev-|abrk+|abrk-|abrk:rel|abrk:lap|abrk:svc|abrk:emg|ibrk+|ibrk-|dyn+|dyn-|horn|bell|sand|hl|cabl|eng|iso|pan|flt|ack|radio>"); return true; }
                String r = apply(mc, l, p, a[2]);
                say(sender, r.isEmpty() ? "§7(no effect)" : r);
                return true;
            case "test":
                for (String line : selfTest(mc, l)) say(sender, line);
                return true;
            default:
                say(sender, "§b" + EngineSim.label(l) + " cab: throttle " + Math.round(l.getThrottle() * 100) + " % · reverser " + l.getReverser()
                        + " · train brake " + Math.round(l.getTrainBrakePos() * 100) + " % (handle " + handle(l.getControlPosition(AUTOBRAKE)) + ") · ind "
                        + Math.round(l.getIndependentBrake() * 100) + " %" + (l instanceof LocomotiveDiesel dd ? " · dyn " + Math.round(dd.getDynamicBrake() * 100) + " %" : ""));
                StringBuilder sb = new StringBuilder("§7");
                for (String g : new String[]{HEADLIGHT, LIGHT, BELL, ISOLATE, PANTO, ELEC, PANST, ALERT, AWS, SANDL, SLIP, FLT, RADIO, RPM})
                    sb.append(g.substring(4)).append('=').append(String.format(Locale.ROOT, "%.2f", l.getControlPosition(g))).append(' ');
                say(sender, sb.toString());
                return true;
        }
    }

    static String handle(float v) { return v == REL ? "REL" : v == SVC ? "SVC" : v == EMG ? "EMG" : "LAP"; }

    /**
     * Drives every desk function through the real code paths on this loco and checks the result, then puts everything
     * back. Run it with the train stood (it moves no wheels: reverser stays where it was at the end).
     */
    static List<String> selfTest(World mc, Locomotive l) {
        List<String> out = new ArrayList<>();
        int[] score = {0, 0};
        float thr = l.getThrottle(), rev = l.getReverser(), tb = l.getTrainBrakePos(), ib = l.getIndependentBrake();
        float hdl = l.getControlPosition(AUTOBRAKE), iso = l.getControlPosition(ISOLATE), pan = l.getControlPosition(PANTO);
        SimData.Car c = SimData.get(mc).of(l.getUUID());
        int hl = c.headlight;
        boolean cabl = c.cabLights;
        boolean steam = l instanceof LocomotiveSteam;
        long now = mc.getTotalWorldTime();
        try {
            l.setThrottle(0);
            apply(mc, l, null, "thr+");
            apply(mc, l, null, "thr+");
            check(out, score, "throttle up two " + (steam ? "regulator steps" : "notches"), Math.abs(l.getThrottle() - (steam ? 0.10f : 2f / NOTCHES)) < 0.02f, l.getThrottle());
            apply(mc, l, null, "thr0");
            check(out, score, "throttle to idle", l.getThrottle() == 0, l.getThrottle());
            l.setReverser(0);
            apply(mc, l, null, "rev+");
            check(out, score, "reverser forward", l.getReverser() > 0.05f, l.getReverser());
            apply(mc, l, null, "rev-");
            apply(mc, l, null, "rev-");
            check(out, score, "reverser reverse", l.getReverser() < -0.05f, l.getReverser());
            // automatic brake valve
            l.setTrainBrake(0);
            st(l).wroteBrake = 0;
            apply(mc, l, null, "abrk:svc");
            for (int i = 0; i < 5; i++) mirror(mc, l, now + 4 * i + 1);
            float svc = l.getTrainBrakePos();
            check(out, score, "SERVICE applies the train brake", svc > 0.15f && svc <= 0.85f, svc);
            apply(mc, l, null, "abrk:lap");
            mirror(mc, l, now + 41);
            check(out, score, "LAP holds it", Math.abs(l.getTrainBrakePos() - svc) < 0.001f, l.getTrainBrakePos());
            apply(mc, l, null, "abrk:rel");
            for (int i = 0; i < 12; i++) mirror(mc, l, now + 45 + 4 * i);
            check(out, score, "RELEASE releases it", l.getTrainBrakePos() < 0.01f, l.getTrainBrakePos());
            apply(mc, l, null, "abrk:emg");
            check(out, score, "EMERGENCY dumps the pipe", l.getTrainBrakePos() >= 0.99f && l.getEmergency(), l.getTrainBrakePos());
            apply(mc, l, null, "abrk:lap");
            check(out, score, "emergency reset at LAP", !l.getEmergency(), l.getEmergency() ? 1 : 0);
            apply(mc, l, null, "abrk:rel");
            l.setTrainBrake(0.5f);                                               // as if the keyboard / protection moved it
            mirror(mc, l, now + 101);
            check(out, score, "outside brake change -> handle to LAP", l.getControlPosition(AUTOBRAKE) == LAP && Math.abs(l.getTrainBrakePos() - 0.5f) < 0.001f, l.getControlPosition(AUTOBRAKE));
            // independent + dynamic
            l.setIndependentBrake(0);
            apply(mc, l, null, "ibrk+");
            check(out, score, "independent brake applies", l.getIndependentBrake() > 0.05f, l.getIndependentBrake());
            apply(mc, l, null, "ibrk0");
            if (l instanceof LocomotiveDiesel d) {
                float dyn = d.getDynamicBrake();
                apply(mc, l, null, "dyn+");
                check(out, score, "dynamic brake notch", d.getDynamicBrake() > dyn, d.getDynamicBrake());
                d.setDynamicBrake(dyn);
            }
            // lights through AuxSim + the two-way mirror
            c.headlight = 0;
            apply(mc, l, null, "hl");
            check(out, score, "headlight switch -> sim", c.headlight != 0 && Math.abs(l.getControlPosition(HEADLIGHT) - c.headlight / 2f) < 0.01f, c.headlight);
            l.setControlPosition(HEADLIGHT, 0f);                                  // as if a model widget turned it off
            mirror(mc, l, now + 121);
            check(out, score, "model widget -> sim (headlight off)", c.headlight == 0, c.headlight);
            boolean was = c.cabLights;
            apply(mc, l, null, "cabl");
            check(out, score, "cab light", c.cabLights != was, c.cabLights ? 1 : 0);
            // isolate
            l.setControlPosition(ISOLATE, 0);
            apply(mc, l, null, "iso");
            l.setThrottle(0.5f);
            mirror(mc, l, now + 141);
            check(out, score, "isolated unit takes no power", l.getThrottle() == 0, l.getThrottle());
            apply(mc, l, null, "iso");
            check(out, score, "isolate back to RUN", l.getControlPosition(ISOLATE) == 0, l.getControlPosition(ISOLATE));
            if (electric(mc, l)) {
                l.setControlPosition(PANTO, 0);
                apply(mc, l, null, "pan");
                OhleData.Pan pp = OhleData.get(mc).pan(l.getUUID());
                check(out, score, "pantograph UP (manual)", pp.manual && pp.wantUp, pp.wantUp ? 1 : 0);
                apply(mc, l, null, "pan");
                check(out, score, "pantograph DOWN (manual)", pp.manual && !pp.wantUp, pp.wantUp ? 1 : 0);
            }
        } catch (RuntimeException ex) {
            out.add("§cself-test threw " + ex);
        } finally {
            if (l.getEmergency()) l.setEmergency(false);
            st(l).emgByUs = false;
            l.setThrottle(thr);
            l.setReverser(rev);
            l.setTrainBrake(tb);
            st(l).wroteBrake = tb;
            l.setIndependentBrake(ib);
            l.setControlPosition(AUTOBRAKE, hdl);
            l.setControlPosition(ISOLATE, iso);
            l.setControlPosition(PANTO, pan);
            if (electric(mc, l)) applyPanto(mc, l, pan);
            c.headlight = hl;
            c.cabLights = cabl;
            l.setControlPosition(HEADLIGHT, hl / 2f);
            l.setControlPosition(LIGHT, cabl ? 1 : 0);
            st(l).seen.put(HEADLIGHT, hl / 2f);
            st(l).seen.put(LIGHT, cabl ? 1f : 0f);
        }
        out.add(0, (score[1] == score[0] ? "§a" : "§c") + "Cab self-test on " + EngineSim.label(l) + ": " + score[1] + "/" + score[0] + " passed (state restored)");
        return out;
    }

    private static void check(List<String> out, int[] score, String what, boolean ok, float v) {
        score[0]++;
        if (ok) score[1]++;
        out.add((ok ? "§a PASS " : "§c FAIL ") + "§7" + what + (ok ? "" : " (got " + String.format(Locale.ROOT, "%.3f", v) + ")"));
    }

    private static void say(ICommandSender s, String m) { s.sendMessage(new TextComponentString(m)); }
}
