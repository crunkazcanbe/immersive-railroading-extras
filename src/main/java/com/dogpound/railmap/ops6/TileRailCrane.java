package com.dogpound.railmap.ops6;

import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.wreck.Wrecks;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.grid.Elec;
import com.dogpound.railmap.grid.Wind;
import com.dogpound.railmap.infra.InfraTile;
import com.dogpound.railmap.infra.Supply;
import com.dogpound.railmap.sound.PackSounds;
import com.dogpound.railmap.track.Rails;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

import java.util.*;

/**
 * 250 t Breakdown Crane (batch 6 E #22): a telescopic-jib rail crane on its carrier, worked from the carrier console.
 * HYD key starts the 480 V hydraulic power pack; OUTRIGGERS out (8 s) for the full duty chart. SLEW / LUFF / HOIST
 * selectors run the motions (or the screen's jog buttons); TARGET picks a derailed / overturned / destroyed IR car
 * within reach, AIM puts the jib head over it, lower the hook onto it and HOOK ON. Then hoist: an overturned car is
 * stood up (3 m), a derailed one lifted (1 m) and set back down on the nearest track, a write-off lifted out and cut up
 * - IR's own wreck recovery (Wrecks.crewStep) when the load lands.
 * <p>
 * The load moment indicator works the real duty chart: on outriggers min(250 t, 1500 t·m / radius), free on rails
 * min(60 t, 300 t·m / radius), over the side of the carrier 75 % (on rails 50 %) of over the end. The load is the car's
 * mass (an overturned car is righted on its edge: half of it) + 4 t of hook block and chains. At 90 % it warns, at 100 %
 * it cuts out every motion that would make it worse (BYPASS key: to 110 %, alarmed); wind over 20 m/s at the jib head
 * stops lifting. 12 V + 480 V 3-phase.
 */
public class TileRailCrane extends InfraTile implements PacketOps6.Screen {
    public static final double JIB = 14, HOOK_T = 4;
    static final String[] PHASE = {"IDLE", "RIGGED", "LIFTED", "LANDING"};
    public float slew = 90, luff = 20, drop = 0, outr = 0;   // deg (0 = the console side, 90 = along the carrier), deg, m below the jib head, 0..1
    UUID target;
    int phase;                  // 0 idle, 1 rigged, 2 lifted (lift reached), 3 landing
    float lift, need;           // m the load is off the ground / to go
    int jogS, jogL, jogH;       // screen jog ticks (+/-)
    boolean aiming, cut;
    double lmi;
    private final Deque<String> log = new ArrayDeque<>();

    public TileRailCrane() { super(new Supply(Elec.Level.AC480)); }

    @Override public String wiredName() { return "250 t breakdown crane " + pos.getX() + "," + pos.getZ(); }
    @Override public String gearState() { return !powered() ? "dark - no 12 V" : !live() ? "no supply: " + mains.what : String.format(Locale.ROOT, "%s · R %.1f m · LMI %.0f %% · %s", PHASE[phase], radius(), lmi, outr >= 1 ? "on outriggers" : "free on rails"); }
    @Override public int wiredLamp() { return !live() ? 3 : cut || lmi >= 90 ? 2 : 1; }

    boolean hyd() { return ctl.get("k_hyd") == 1 && live() && ctl.get("m_estop") == 0; }
    boolean bypass() { return ctl.get("k_byp") == 1; }
    int sel(String id) { return Math.floorMod(ctl.get(id), 3); }

    // ---- geometry (model frame: north-facing, +x along the carrier, jib slews about the block centre) ---------------------
    public double radius() { return radius(luff); }
    static double radius(double luffDeg) { return 0.3 + JIB * Math.cos(Math.toRadians(luffDeg)); }
    public double headY() { return 1.25 + JIB * Math.sin(Math.toRadians(luff)); }
    public double hookY() { return headY() - drop; }

    /** a world offset from the block centre -> the model frame (as IControlled.aimed turns it) */
    double[] local(double wx, double wz) {
        EnumFacing f = controlFacing();
        int turns = f.getHorizontalIndex() == 2 ? 0 : f.getHorizontalIndex() == 3 ? 1 : f.getHorizontalIndex() == 0 ? 2 : 3;
        double x = wx, z = wz;
        for (int i = 0; i < turns; i++) { double nx = z, nz = -x; x = nx; z = nz; }
        return new double[]{x, z};
    }

    /** bearing (deg, 0 = north / the console side, 90 = +x) and radius (m) of a world point in the model frame */
    double[] polar(double wx, double wz) {
        double[] l = local(wx - pos.getX() - 0.5, wz - pos.getZ() - 0.5);
        return new double[]{(Math.toDegrees(Math.atan2(l[0], -l[1])) + 360) % 360, Math.hypot(l[0], l[1])};
    }

    static double angDiff(double a, double b) { double d = (a - b) % 360; if (d > 180) d -= 360; if (d < -180) d += 360; return d; }

    // ---- the duty chart ------------------------------------------------------------------------------------------------
    /** rated capacity (t) at this radius, slew and outrigger state */
    public static double rated(double r, double slewDeg, boolean outriggers) {
        double end = Math.abs(Math.sin(Math.toRadians(slewDeg)));            // 1 = over the end (along the carrier)
        double cap = outriggers ? Math.min(250, 1500 / Math.max(1, r)) : Math.min(60, 300 / Math.max(1, r));
        return cap * (outriggers ? 0.75 + 0.25 * end : 0.5 + 0.5 * end);
    }

    /** the load on the hook now (t) */
    double load(EntityMoveableRollingStock s) {
        if (phase == 0 || s == null) return 0;
        double m = Rails.tonnes(s);
        double f = s.wreck == 2 ? 0.5 : s.wreck == 3 ? 0.4 : 1.0;
        return (lift > 0.02f || phase >= 2 ? m * f : m * f * 0.2) + HOOK_T;
    }

    double lmiAt(double r, double slewDeg, EntityMoveableRollingStock s) { double L = load(s); return L <= 0 ? 0 : L / rated(r, slewDeg, outr >= 1) * 100; }

    double limit() { return Ops6Config.craneLmiCut ? (bypass() ? 110 : 100) : 1e9; }

    EntityMoveableRollingStock stock() {
        if (target == null) return null;
        try {
            EntityMoveableRollingStock s = cam72cam.mod.world.World.get(world).getEntity(target, EntityMoveableRollingStock.class);
            return s == null || s.isDead() ? null : s;
        } catch (RuntimeException e) { return null; }
    }

    /** wrecked cars within reach (16 m), nearest first */
    List<EntityMoveableRollingStock> wrecks() {
        List<EntityMoveableRollingStock> l = new ArrayList<>();
        try {
            for (EntityMoveableRollingStock s : cam72cam.mod.world.World.get(world).getEntities(EntityMoveableRollingStock.class)) {
                if (s.isDead() || s.wreck == 0) continue;
                cam72cam.mod.math.Vec3d p = s.getPosition();
                if (polar(p.x, p.z)[1] <= JIB + 2 && Math.abs(p.y - pos.getY()) < 6) l.add(s);
            }
        } catch (LinkageError | RuntimeException ignored) { }
        l.sort(Comparator.comparingDouble(s -> polar(s.getPosition().x, s.getPosition().z)[1]));
        return l;
    }

    /** is the hook over the target car (within 1.2 m) */
    boolean over(EntityMoveableRollingStock s) {
        if (s == null) return false;
        double[] pr = polar(s.getPosition().x, s.getPosition().z);
        double r = radius();
        return Math.abs(r - pr[1]) < 1.2 && Math.abs(Math.toRadians(angDiff(slew, pr[0]))) * pr[1] < 1.2;
    }

    /** hook height above the car's floor (m, local y) */
    double hookAbove(EntityMoveableRollingStock s) { return pos.getY() + hookY() - (s.getPosition().y + 2.0); }

    void note(String s) { log.addFirst(com.dogpound.railmap.depot.DepotClock.hhmm(world) + " " + s); while (log.size() > 14) log.removeLast(); }

    // ---- motion ----------------------------------------------------------------------------------------------------------
    @Override
    protected void serverTick() {
        EntityMoveableRollingStock s = stock();
        if (target != null && s == null) { if (phase > 0) note("TARGET GONE - UNRIGGED"); target = null; phase = 0; lift = 0; }
        if (s != null && s.wreck == 0 && phase == 0) target = null;
        boolean go = hyd();
        double wind = Wind.at(world, headY() + 2);
        boolean windCut = wind > 20;
        int ds = 0, dl = 0, dh = 0;
        if (go) {
            ds = sel("s_slew") == 1 ? -1 : sel("s_slew") == 2 ? 1 : Integer.signum(jogS);
            dl = sel("s_luff") == 1 ? 1 : sel("s_luff") == 2 ? -1 : Integer.signum(jogL);
            dh = sel("s_hoist") == 1 ? 1 : sel("s_hoist") == 2 ? -1 : Integer.signum(jogH);   // +1 = hook UP
            if (aiming && s != null) {
                double[] pr = polar(s.getPosition().x, s.getPosition().z);
                double d = angDiff(pr[0], slew);
                ds = Math.abs(d) > 0.5 ? (int) Math.signum(d) : 0;
                double wantLuff = Math.max(8, Math.min(78, Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, (pr[1] - 0.3) / JIB))))));
                dl = Math.abs(wantLuff - luff) > 0.3 ? (int) Math.signum(wantLuff - luff) : 0;
                if (ds == 0 && dl == 0) { aiming = false; note("AIMED AT TARGET"); PackSounds.play(world, pos, "ui.beep_ok", 0.6f, 1f); }
            }
            // outriggers: only with the hook unloaded
            float wantO = sel("s_outr") == 1 ? 1 : 0;
            if (outr != wantO && phase == 0) outr = Math.max(0, Math.min(1, outr + (wantO > outr ? 1 : -1) / 160f));
        } else aiming = false;
        if (jogS != 0) jogS -= Integer.signum(jogS);
        if (jogL != 0) jogL -= Integer.signum(jogL);
        if (jogH != 0) jogH -= Integer.signum(jogH);
        double now = lmiAt(radius(), slew, s);
        cut = false;
        if (ds != 0) {
            float ns = (slew + ds * 0.3f + 360) % 360;                       // 6 deg/s
            if (lmiAt(radius(), ns, s) > limit() && lmiAt(radius(), ns, s) > now) cut = true; else slew = ns;
        }
        if (dl != 0) {
            float nl = Math.max(8, Math.min(78, luff + dl * 0.15f));         // 3 deg/s
            if (lmiAt(radius(nl), slew, s) > limit() && lmiAt(radius(nl), slew, s) > now) cut = true; else luff = nl;
        }
        if (dh != 0) {
            float step = 0.03f;                                              // 0.6 m/s
            if (dh > 0) {
                if (phase >= 1 && phase <= 2 && s != null) {
                    if (windCut) cut = true;
                    else if (lmiAt(radius(), slew, s) > limit() || (phase == 1 && lift <= 0.02f && Rails.tonnes(s) * (s.wreck == 2 ? 0.5 : s.wreck == 3 ? 0.4 : 1) + HOOK_T > rated(radius(), slew, outr >= 1) * limit() / 100)) cut = true;
                    else { lift = Math.min(need + 0.5f, lift + step); drop = Math.max(0, drop - step); }
                } else drop = Math.max(0, drop - step);
            } else {
                if (phase >= 1 && s != null) {
                    if (lift > 0) { lift = Math.max(0, lift - step); drop += step; if (phase == 2) phase = 3; }
                } else drop = (float) Math.min(headY() + 3, drop + step);
            }
        }
        if (s != null && phase == 1 && lift >= need) { phase = 2; note("LOAD LIFTED " + String.format(Locale.ROOT, "%.1f M", lift)); Recorder.event(world, wiredName() + ": " + Rails.name(s) + " lifted clear"); PackSounds.play(world, pos, "ui.beep_ok", 0.6f, 0.8f); }
        if (s != null && phase == 3 && lift <= 0) land(s);
        if (s != null && phase >= 1 && lift > 0 && age % 10 == 0) Wrecks.crewEffects(s, s.wreck >= 2);
        lmi = lmiAt(radius(), slew, s);
        if ((ds != 0 || dl != 0 || dh != 0) && !cut && age % 30 == 0) PackSounds.play(world, pos, "grid.motor_run", 0.5f, 0.8f);
        if (cut && age % 20 == 0) PackSounds.play(world, pos, "buzzer", 0.8f, 1f);
        if (age % 20 == 7) second(s, wind, windCut);
        if (age % 4 == 0) display(s, wind);
    }

    void second(EntityMoveableRollingStock s, double wind, boolean windCut) {
        mainsTick(!hyd() ? 800 : (ctl.get("s_slew") != 0 || ctl.get("s_luff") != 0 || ctl.get("s_hoist") != 0 || aiming || jogS != 0 || jogL != 0 || jogH != 0) ? 75000 : 15000);
        String k = "crane:" + pos.toLong();
        Alarms6.set(world, k + ":lmi", phase > 0 && lmi >= 100, AlarmManager.Cat.MACHINE, bypass() ? AlarmManager.Sev.MAJOR : AlarmManager.Sev.MINOR,
                String.format(Locale.ROOT, "BREAKDOWN CRANE %d,%d LMI %.0f %%%s - motions cut out", pos.getX(), pos.getZ(), lmi, bypass() ? " ON BYPASS" : ""), pos);
        Alarms6.set(world, k + ":byp", bypass(), AlarmManager.Cat.MACHINE, AlarmManager.Sev.MAJOR, "BREAKDOWN CRANE " + pos.getX() + "," + pos.getZ() + " LMI BYPASS KEY IN", pos);
        Alarms6.set(world, k + ":wind", windCut && hyd(), AlarmManager.Cat.MACHINE, AlarmManager.Sev.MINOR, String.format(Locale.ROOT, "BREAKDOWN CRANE %d,%d WIND %.0f M/S - lifting stopped", pos.getX(), pos.getZ(), wind), pos);
    }

    void display(EntityMoveableRollingStock s, double wind) {
        boolean on = powered();
        boolean blink = (world.getTotalWorldTime() / 10) % 2 == 0;
        double rr = rated(radius(), slew, outr >= 1);
        put("pwr", on); put("sup", mains.ok); put("hyd", on && hyd()); put("outr", on && outr >= 1); put("outrm", on && outr > 0 && outr < 1 && blink);
        put("rig", on && phase >= 1); put("lifted", on && phase >= 2); put("lmi90", on && lmi >= 90); put("lmicut", on && cut && blink); put("byp", on && bypass());
        put("wind", on && wind > 20); put("tgtl", on && s != null && over(s));
        put("g_lmi", (int) Math.min(120, Math.round(lmi)));
        put("g_wind", (int) Math.min(100, Math.round(wind * 100 / 30)));
        put("g_hyd", hyd() ? (cut ? 100 : 60 + (int) (lmi / 4)) : 0);
        put("g_outr", Math.round(outr * 100));
        text("lmi", String.format(Locale.ROOT, "%.0f", lmi));
        text("rad", String.format(Locale.ROOT, "%.1f", radius()));
        text("load", String.format(Locale.ROOT, "%.1f", load(s)));
        text("rated", String.format(Locale.ROOT, "%.0f", rr));
        text("ang", String.format(Locale.ROOT, "SLEW %03d LUFF %02d HOOK %.1f", Math.round(slew) % 360, Math.round(luff), hookY()));
        text("tline", s == null ? "NO TARGET - PRESS TARGET" : String.format(Locale.ROOT, "%s %s %.0fT", cut(Rails.name(s).toUpperCase(Locale.ROOT), 12), s.wreck == 1 ? "DERAILED" : s.wreck == 2 ? "ON SIDE" : "WRECK", Rails.tonnes(s)));
    }

    static String cut(String s, int n) { return s.length() <= n ? s : s.substring(0, n); }

    /** the load is down: IR's own recovery does the rest */
    void land(EntityMoveableRollingStock s) {
        String name = Rails.name(s);
        String did = Wrecks.crewStep(s);
        String msg = switch (did) {
            case "upright" -> name + " stood back up (still derailed - lift it onto the track next)";
            case "rerailed" -> name + " set back on the track";
            case "scrapped" -> name + " lifted out and cut up for scrap";
            case "no_track" -> name + " set down - NO TRACK within 5 blocks to put it on";
            case "moving" -> name + " is moving - set down, wait";
            default -> name + " set down";
        };
        note(msg.toUpperCase(Locale.ROOT));
        Recorder.event(world, wiredName() + ": " + msg);
        PackSounds.play(world, pos, "machine.clunk", 1f, 0.7f);
        for (EntityPlayer p : world.playerEntities) if (p.getDistanceSq(pos) < 32 * 32) p.sendStatusMessage(new net.minecraft.util.text.TextComponentString("§a[CRANE] " + msg), true);
        phase = 0; lift = 0;
        if (!"upright".equals(did)) target = null;
        controlsChanged();
    }

    // ---- controls ----------------------------------------------------------------------------------------------------
    @Override
    public String controlPress(String id, EntityPlayer p) {
        if (id.equals("test")) return lampTest();
        if (id.equals("screen")) { PacketOps6.open(p, this, 0); return ""; }
        if (id.equals("k_hyd")) {
            if (ctl.get("k_hyd") == 0 && !live()) return "§cNo power for the hydraulic pack: " + (powered() ? mains.what : "no 12 V");
            ctl.set("k_hyd", 1 - ctl.get("k_hyd"));
            note(ctl.get("k_hyd") == 1 ? "HYDRAULICS ON" : "HYDRAULICS OFF");
            if (ctl.get("k_hyd") == 1) PackSounds.play(world, pos, "grid.motor_start", 0.8f, 0.9f);
            return ctl.get("k_hyd") == 1 ? "§aHydraulic power pack running" : "§eHydraulics off";
        }
        if (id.equals("k_byp")) {
            ctl.set("k_byp", 1 - ctl.get("k_byp"));
            note(bypass() ? "LMI BYPASS IN" : "LMI BYPASS OUT");
            Recorder.event(world, wiredName() + ": LMI bypass key " + (bypass() ? "IN" : "OUT") + " by " + who(p));
            return bypass() ? "§cLMI BYPASS - the crane will go to 110 % of its chart" : "§aLMI normal";
        }
        if (id.equals("s_outr") && phase > 0) return "§cNot with a load rigged";
        if (id.startsWith("s_") || id.startsWith("k_") || id.equals("m_estop")) { if (id.equals("m_estop")) aiming = false; return null; }
        if (!powered()) return "§cThe console is dark - no 12 V";
        switch (id) {
            case "tgt": {
                if (phase > 0) return "§cUnhook first";
                List<EntityMoveableRollingStock> l = wrecks();
                if (l.isEmpty()) { target = null; return "§7No derailed or wrecked car within " + (int) (JIB + 2) + " m"; }
                int i = 0;
                for (int k = 0; k < l.size(); k++) if (l.get(k).getUUID().equals(target)) i = k + 1;
                EntityMoveableRollingStock s = l.get(i % l.size());
                target = s.getUUID();
                double[] pr = polar(s.getPosition().x, s.getPosition().z);
                note("TARGET " + Rails.name(s).toUpperCase(Locale.ROOT));
                return String.format(Locale.ROOT, "§eTarget: %s (%s, %.0f t) at %.1f m, bearing %03d", Rails.name(s), s.wreck == 1 ? "derailed" : s.wreck == 2 ? "on its side" : "destroyed", Rails.tonnes(s), pr[1], Math.round(pr[0]));
            }
            case "aim": {
                if (!hyd()) return "§cHydraulics off";
                if (stock() == null) return "§7Pick a TARGET first";
                if (phase > 0) return "§cNot with a load rigged";
                double r = polar(stock().getPosition().x, stock().getPosition().z)[1];
                if (r > radius(8) + 0.5) return String.format(Locale.ROOT, "§cOut of reach: %.1f m (the jib reaches %.1f m)", r, radius(8));
                aiming = true;
                return "§eAiming the jib head over the target";
            }
            case "hook": {
                EntityMoveableRollingStock s = stock();
                if (phase > 0) {
                    if (lift > 0.05f) return "§cSet the load down before unhooking";
                    phase = 0; note("UNHOOKED"); return "§eChains off";
                }
                if (s == null) return "§7Pick a TARGET first";
                if (!over(s)) return "§cThe hook is not over the car - AIM or slew / luff";
                double above = hookAbove(s);
                if (above > 1.0) return String.format(Locale.ROOT, "§cLower the hook: %.1f m above the car", above);
                if (above < -1.5) return "§cThe hook is down past the car - hoist up a little";
                if (s.getCurrentSpeed() != null && Math.abs(s.getCurrentSpeed().metric()) > 0.5) return "§cThe car is still moving";
                phase = 1; lift = 0;
                need = s.wreck == 2 ? 3.0f : s.wreck == 3 ? 1.5f : 1.0f;
                note("HOOKED ON " + Rails.name(s).toUpperCase(Locale.ROOT));
                Recorder.event(world, wiredName() + ": rigged to " + Rails.name(s) + String.format(Locale.ROOT, " (%.0f t, R %.1f m, chart %.0f t)", Rails.tonnes(s), radius(), rated(radius(), slew, outr >= 1)));
                PackSounds.play(world, pos, "machine.jack", 0.8f, 1f);
                return String.format(Locale.ROOT, "§aRigged. HOIST UP %.1f m%s", need, s.wreck == 2 ? " to stand it up" : s.wreck == 3 ? " to lift it out" : " to lift it");
            }
            case "horn": PackSounds.play(world, pos, "cab.horn", 1.5f, 0.9f); return "§7Horn";
            case "ack":
                for (AlarmManager.Alarm a : AlarmManager.get(world).all()) if (a.key.startsWith("crane:" + pos.toLong()) && !a.acked) AlarmManager.get(world).ack(a.id, who(p));
                return "§7Alarms acknowledged";
            default: return null;
        }
    }

    @Override
    public NBTTagCompound screen(EntityPlayer p, int page) {
        EntityMoveableRollingStock s = stock();
        Scr c = new Scr("250 t Breakdown Crane " + pos.getX() + "," + pos.getZ(), live() ? (hyd() ? "§aHYD ON" : "§eHYD OFF") : "§cNO POWER", page, "LMI", "TARGETS", "MOTION", "LOG");
        double rr = rated(radius(), slew, outr >= 1);
        switch (page) {
            case 1 -> {
                List<EntityMoveableRollingStock> l = wrecks();
                c.line("§7Wrecked cars within " + (int) (JIB + 2) + " m: §f" + l.size());
                for (EntityMoveableRollingStock w : l) {
                    double[] pr = polar(w.getPosition().x, w.getPosition().z);
                    double needT = Rails.tonnes(w) * (w.wreck == 2 ? 0.5 : w.wreck == 3 ? 0.4 : 1) + HOOK_T;
                    double cap = rated(pr[1], pr[0], outr >= 1);
                    c.line(String.format(Locale.ROOT, "%s%-16s §7%s %.0f t  R %.1f m  brg %03d  §f%s", w.getUUID().equals(target) ? "§b> " : "§f", cut(Rails.name(w), 16),
                            w.wreck == 1 ? "DERAILED" : w.wreck == 2 ? "ON SIDE " : "WRECK   ", Rails.tonnes(w), pr[1], Math.round(pr[0]), needT <= cap ? "§aCAN LIFT" : "§cOVER CHART (" + Math.round(cap) + " t)"));
                }
                c.button("tgt", "NEXT TARGET", Scr.BLUE).button("aim", "AIM", Scr.AMBER);
            }
            case 2 -> {
                c.line(String.format(Locale.ROOT, "§7Slew §f%03d°  §7Luff §f%.0f°  §7Radius §f%.1f m  §7Jib head §f%.1f m  §7Hook §f%.1f m", Math.round(slew) % 360, luff, radius(), headY(), hookY()));
                c.line("§7Outriggers: §f" + Math.round(outr * 100) + " %" + (outr >= 1 ? " SET" : outr > 0 ? " MOVING" : " IN (free on rails)"));
                c.line("§7Jog: one press = one second of motion. Panel selectors run until STOP.");
                c.button("jsl", "SLEW LEFT", Scr.BLUE).button("jsr", "SLEW RIGHT", Scr.BLUE).button("jlu", "LUFF UP", Scr.BLUE).button("jld", "LUFF DOWN", Scr.BLUE)
                        .button("jhu", "HOIST UP", Scr.GREEN).button("jhd", "HOIST DOWN", Scr.GREEN).button("hook", phase > 0 ? "UNHOOK" : "HOOK ON", Scr.AMBER).button("stop", "STOP ALL", Scr.RED);
            }
            case 3 -> { for (String l : log) c.line("§7" + l); if (log.isEmpty()) c.line("§8(no events yet)"); }
            default -> {
                c.bar("LOAD MOMENT", Math.min(120, lmi), 120, lmi >= 100 ? 0xC02020 : lmi >= 90 ? 0xD09010 : 0x30A050, String.format(Locale.ROOT, "%.0f %%%s", lmi, cut ? " CUT OUT" : ""));
                c.bar("WIND AT JIB HEAD", Math.min(30, Wind.at(world, headY() + 2)), 30, Wind.at(world, headY() + 2) > 20 ? 0xC02020 : 0x3080C0, String.format(Locale.ROOT, "%.1f m/s (limit 20)", Wind.at(world, headY() + 2)));
                c.line(String.format(Locale.ROOT, "§7Radius §f%.1f m   §7Load §f%.1f t   §7Chart §f%.0f t   §7(%s, %s)", radius(), load(s), rr, outr >= 1 ? "outriggers" : "free on rails",
                        Math.abs(Math.sin(Math.toRadians(slew))) > 0.7 ? "over the end" : "over the side"));
                c.line("§7State: §f" + PHASE[phase] + (phase >= 1 ? String.format(Locale.ROOT, "   §7lift §f%.1f / %.1f m", lift, need) : "") + (bypass() ? "   §cBYPASS" : ""));
                c.line(s == null ? "§7Target: §fnone" : String.format(Locale.ROOT, "§7Target: §f%s §7(%s, %.0f t) %s", Rails.name(s), s.wreck == 1 ? "derailed" : s.wreck == 2 ? "on its side" : s.wreck == 3 ? "destroyed" : "on the rails", Rails.tonnes(s), over(s) ? "§aHOOK OVER IT" : "§enot under the hook"));
                c.line("§7Chart: on outriggers min(250 t, 1500 t·m / R), free on rails min(60 t, 300 t·m / R), over the side 75 % / 50 %");
                c.button("tgt", "TARGET", Scr.BLUE).button("aim", "AIM", Scr.AMBER).button("hook", phase > 0 ? "UNHOOK" : "HOOK ON", Scr.AMBER).button("ack", "ACK", Scr.GREY);
            }
        }
        return c.build();
    }

    @Override
    public String screenAction(EntityPlayer p, int page, String a) {
        if (a.startsWith("j")) {
            if (!hyd()) return "§cHydraulics off (HYD key, 12 V and 480 V)";
            switch (a) {
                case "jsl" -> jogS = -20; case "jsr" -> jogS = 20; case "jlu" -> jogL = 20; case "jld" -> jogL = -20; case "jhu" -> jogH = 20; case "jhd" -> jogH = -20;
                default -> { }
            }
            return null;
        }
        if (a.equals("stop")) { jogS = jogL = jogH = 0; aiming = false; ctl.set("s_slew", 0); ctl.set("s_luff", 0); ctl.set("s_hoist", 0); controlsChanged(); return "§eAll motions stopped"; }
        return com.dogpound.railmap.controls.IControlled.operate(this, a, p);
    }

    @Override protected void store(NBTTagCompound t) {
        t.setFloat("slew", slew); t.setFloat("luff", luff); t.setFloat("drop", drop); t.setFloat("outr", outr);
        if (target != null) t.setUniqueId("tgt", target);
        t.setInteger("phase", phase); t.setFloat("lift", lift); t.setFloat("need", need);
    }

    @Override protected void restore(NBTTagCompound t) {
        if (t.hasKey("slew")) { slew = t.getFloat("slew"); luff = t.getFloat("luff"); }
        drop = t.getFloat("drop"); outr = t.getFloat("outr");
        target = t.hasUniqueId("tgt") ? t.getUniqueId("tgt") : null;
        phase = t.getInteger("phase"); lift = t.getFloat("lift"); need = t.getFloat("need");
    }

    @Override public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() { return new net.minecraft.util.math.AxisAlignedBB(pos).grow(18, 16, 18); }
}
