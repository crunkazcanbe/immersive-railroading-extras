package com.dogpound.railmap.track;

import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.LocomotiveDiesel;
import cam72cam.immersiverailroading.entity.LocomotiveSteam;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.graph.TrainNode;
import com.dogpound.railmap.safety.TileCallPoint;
import com.dogpound.railmap.server.TrainTracker;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import java.util.*;

/**
 * Tunnel control panel (her ChatGPT list #16): the SCADA mimic for one tunnel - every jet fan, smoke detector,
 * cross-passage, light, radio and emergency telephone inside its zone, and the tunnel's air:
 * <ul>
 *   <li>air model: CO from diesel and steam locomotives (by throttle), smoke from fires (detectors see fire, lava and
 *       burning things), cleared by natural draught and by the fans' airflow;</li>
 *   <li>AUTO ventilation: fans to half speed with diesel trains in the tunnel or CO over 25 ppm, full over 50 ppm, run-on
 *       after; on FIRE the emergency sequence: all fans full, blowing the smoke the way the incident train is heading so
 *       people walk out behind it into clean air;</li>
 *   <li>EMERGENCY (fire alarm, or the red mushroom): signals into the tunnel held at danger, traction isolation
 *       requested, lights to full, exit signs flashing, cross-passage doors released and refuges pressurised, everyone
 *       in the tunnel told to evacuate, persons-in-tunnel count, the rescue portal named for the fire brigade;</li>
 *   <li>fans draw real power from the grid; 12 V for the panel itself.</li>
 * </ul>
 */
public class TileTunnelControl extends TrackGear {
    public static final int[] ZONE = {64, 128, 256, 512};
    private double co, smoke, air;           // ppm, smoke (obscuration %/m), net airflow m/s
    private boolean emergency, fire, isolating, ventRun;
    private int runOn, persons, trains, stage;
    private double kw;
    private String rescue = "", fireAt = "";
    private int dir = 1;
    private long emergAt;
    /** next-48 #29: the bore in ventilation sections (null = the old single-zone air) + the emergency fan bus */
    private TunnelAir sec;
    private double emergKwh = 60;
    public TunnelAir sections() { return sec; }
    public double emergKwh() { return emergKwh; }
    /** smoke / CO where p is (its section), or the tunnel's when there are no sections */
    public double smokeAt(BlockPos p) { return sec == null ? smoke : sec.smoke[sec.cell(p.getX() + 0.5, p.getZ() + 0.5)]; }
    public double coAt(BlockPos p) { return sec == null ? co : sec.co[sec.cell(p.getX() + 0.5, p.getZ() + 0.5)]; }

    public String id() { return "TUN " + Math.floorMod(pos.getX() * 19 + pos.getZ() * 3, 1000); }
    @Override public String wiredName() { return "Tunnel control " + id(); }
    public int zone() { return ZONE[Math.min(3, Math.max(0, ctl.get("s_zone")))]; }
    public boolean inZone(double x, double z) { double dx = x - pos.getX() - 0.5, dz = z - pos.getZ() - 0.5; return dx * dx + dz * dz <= (double) zone() * zone(); }
    private boolean inZone(BlockPos p) { return inZone(p.getX() + 0.5, p.getZ() + 0.5) && Math.abs(p.getY() - pos.getY()) < 32; }
    public boolean emergency() { return emergency; }
    public boolean isolating() { return isolating; }
    public double smoke() { return smoke; }
    public double co() { return co; }
    /** what the lights in the zone should do: 0 off, 1 normal, 2 full */
    public int lightMode() { return !powered() ? 1 : emergency ? 2 : ctl.get("s_light") == 0 ? 0 : ctl.get("s_light") == 2 ? 2 : 1; }

    /** the control panel whose zone holds p (nearest), or null */
    public static TileTunnelControl of(net.minecraft.world.World w, BlockPos p) {
        TileTunnelControl best = null;
        double bd = Double.MAX_VALUE;
        for (TileTunnelControl c : TrackHooks.live(w, TileTunnelControl.class)) {
            if (!c.inZone(p)) continue;
            double d = c.getPos().distanceSq(p);
            if (d < bd) { bd = d; best = c; }
        }
        return best;
    }

    @Override public String gearState() { return !powered() ? "NO 12 V - panel dark, lights on battery" : (emergency ? "EMERGENCY" + (fire ? " - FIRE " + fireAt : "") : "normal") + String.format(Locale.ROOT, " · CO %.0f ppm · air %+.1f m/s · %d train(s)", co, air, trains); }
    @Override public int wiredLamp() { return !powered() || emergency ? 3 : co > 50 ? 2 : 1; }
    @Override public void gearInfo(Map<String, Object> m) { m.put("emergency", emergency); m.put("fire", fire); m.put("coPpm", Math.round(co)); m.put("visibilityM", visibility()); m.put("airflow", Math.round(air * 10) / 10.0); m.put("fansKw", Math.round(kw)); m.put("trains", trains); m.put("persons", persons); }

    private int visibility() { return smoke <= 0.05 ? 999 : (int) Math.min(999, 150 / smoke); }

    private <T extends TileEntity> List<T> mine(Class<T> c) {
        List<T> out = new ArrayList<>();
        for (T t : TrackHooks.live(world, c)) if (inZone(t.getPos())) out.add(t);
        out.sort(Comparator.comparingLong(t -> t.getPos().toLong()));
        return out;
    }

    @Override
    protected void tickGear() {
        if (age % 20 != 0) return;
        List<TileTunnelFan> fans = mine(TileTunnelFan.class);
        List<TileSmokeDetector> dets = mine(TileSmokeDetector.class);
        // ---- what is in the tunnel
        double emis = 0;
        List<double[]> emitters = new ArrayList<>();
        trains = 0;
        TrainNode nearFire = null;
        cam72cam.mod.world.World umc = cam72cam.mod.world.World.get(world);
        for (EntityRollingStock s : umc.getEntities(EntityRollingStock.class)) {
            Vec3d p = s.getPosition();
            if (!inZone(p.x, p.z) || Math.abs(p.y - pos.getY()) > 24) continue;
            double e0 = emis;
            if (s instanceof LocomotiveDiesel d && !d.prideElectric) emis += 0.4 + 2.4 * Math.abs(d.getThrottle());
            else if (s instanceof LocomotiveSteam st) emis += 1.2 + 2.0 * Math.abs(st.getThrottle());
            if (emis > e0) emitters.add(new double[]{p.x, p.z, emis - e0});
        }
        for (TrainNode t : TrainTracker.latest(world)) if (inZone(t.x, t.z) && Math.abs(t.y - pos.getY()) < 24 && t.lead) trains++;
        persons = 0;
        for (EntityPlayer pl : world.playerEntities) if (inZone(pl.posX, pl.posZ) && Math.abs(pl.posY - pos.getY()) < 24) persons++;
        // ---- fire: any detector seeing fire
        double fireLoad = 0;
        fire = false;
        TileSmokeDetector hot = null;
        for (TileSmokeDetector d : dets) {
            fireLoad += d.fireLoad();
            if (d.alarm() && !d.isolated()) { fire = true; if (hot == null) hot = d; }
        }
        if (hot != null) {
            fireAt = Rails.xz(hot.getPos());
            double bd = Double.MAX_VALUE;
            for (TrainNode t : TrainTracker.latest(world)) { double d = hot.getPos().distanceSq(t.x, t.y, t.z); if (d < bd && d < 64 * 64) { bd = d; nearFire = t; } }
        }
        // ---- air
        double vol = Math.max(1, zone() / 64.0);
        double move = 0, flowSum = 0;
        kw = 0;
        for (TileTunnelFan f : fans) { move += Math.abs(f.thrust()); flowSum += f.thrust(); kw += f.kw(); }
        if (TunnelAir.enabled()) {                                     // next-48 #29: sections, piston effect, back-layering
            if (sec == null || sec.n != com.dogpound.railmap.ctl.CtlConfig.tunnelSections) sec = new TunnelAir(com.dogpound.railmap.ctl.CtlConfig.tunnelSections);
            sec.begin(fans.isEmpty() ? controlFacing().rotateYCCW() : fans.get(0).axis(), pos, zone());
            for (TileTunnelFan f : fans) sec.fan(f, world.canSeeSky(f.getPos().up()));
            for (TrainNode t : TrainTracker.latest(world)) if (t.lead && inZone(t.x, t.z) && Math.abs(t.y - pos.getY()) < 24 && Math.abs(t.speedKmh) > 1) sec.train(t.x, t.z, t.yaw, t.speedKmh);
            for (double[] e : emitters) sec.emit(e[0], e[1], e[2] * 1.4 * 8 / sec.n);
            for (TileSmokeDetector d : dets) sec.fire(d.getPos(), d.fireLoad());
            sec.step();
            air = sec.u;
            co = sec.maxCo();
            smoke = sec.maxSmoke();
        } else {
            sec = null;
            air = Math.max(-12, Math.min(12, flowSum * 1.6 / vol));
            double k = 0.012 + Math.abs(air) * 0.035 / vol;
            co = Math.max(0, co * (1 - k) + emis * 1.4 / vol);
            smoke = Math.max(0, smoke * (1 - k) + fireLoad * 0.8 / vol);
        }
        // emergency fan bus (UPS-backed): fans with no grid supply keep running in an emergency while it lasts
        boolean ebus = emergency && emergKwh > 0 && powered();
        double onEbus = 0;
        for (TileTunnelFan f : fans) { f.emergencyFeed(ebus); if (f.onEmergencyBus()) onEbus += f.kw(); }
        emergKwh = Math.max(0, Math.min(60, emergKwh - onEbus / 3600 + (emergency ? 0 : 0.05)));
        // ---- emergency
        boolean manual = ctl.get("m_emerg") == 1;
        if ((fire || manual) && !emergency && powered()) startEmergency(fire ? "FIRE detected at " + fireAt : "manual EMERGENCY at the tunnel control panel");
        isolating = emergency;
        if (isolating)                                                   // traction isolated: electric trains in the tunnel lose line power and coast
            for (EntityRollingStock s : umc.getEntities(EntityRollingStock.class)) {
                Vec3d p = s.getPosition();
                if (s instanceof LocomotiveDiesel d && d.prideElectric && inZone(p.x, p.z) && Math.abs(p.y - pos.getY()) <= 24 && d.getThrottle() > 0) d.setThrottle(0);
            }
        // ---- ventilation command
        int mode = ctl.get("s_mode");                                  // 0 OFF, 1 AUTO, 2 MANUAL
        int speed = 0;
        if (emergency && fire) {
            speed = 100;
            if (mode == 1 && nearFire != null && hot != null) {          // push smoke the way the incident train heads
                double hx = Math.cos(Math.toRadians(nearFire.yaw)), hz = Math.sin(Math.toRadians(nearFire.yaw));
                dir = axisSign(fans, hx, hz);
            } else dir = ctl.get("s_dir") == 0 ? 1 : -1;
        } else if (mode == 1) {
            if (co > 50) stage = 2; else if (co > 25 || emis > 0.5) stage = Math.max(stage, 1); else if (co < 10 && emis <= 0.5 && runOn <= 0) stage = 0;
            if (stage > 0 && (co > 10 || emis > 0.5)) runOn = 60; else if (runOn > 0) runOn--;
            speed = stage == 2 ? 100 : stage == 1 ? 50 : 0;
            dir = ctl.get("s_dir") == 0 ? 1 : -1;
        } else if (mode == 2) {
            speed = ventRun ? 100 : 0;
            dir = ctl.get("s_dir") == 0 ? 1 : -1;
        }
        if (powered()) for (TileTunnelFan f : fans) f.command(speed, dir);
        // ---- alarms
        Alarms.state(world, "tun:" + pos.toLong() + ":co", co > 50, AlarmManager.Cat.GENERAL, co > 100 ? AlarmManager.Sev.MAJOR : AlarmManager.Sev.MINOR, String.format(Locale.ROOT, "TUNNEL %s: CO %.0f ppm (ventilation %s)", id(), co, speed > 0 ? "running" : "OFF"), pos);
        for (TileTunnelFan f : fans) Alarms.state(world, "tun:" + f.getPos().toLong() + ":fan", f.faulty(), AlarmManager.Cat.MACHINE, AlarmManager.Sev.MINOR, "TUNNEL " + id() + " fan " + f.id() + ": " + f.gearState(), f.getPos());
        live(fans, dets);
    }

    /** +1 if heading (hx, hz) runs along the fans' forward direction, -1 if against */
    private int axisSign(List<TileTunnelFan> fans, double hx, double hz) {
        if (fans.isEmpty()) return 1;
        net.minecraft.util.EnumFacing f = fans.get(0).axis();
        double fx = f.getXOffset(), fz = f.getZOffset();                 // the way the fans blow when FORWARD
        return hx * fx + hz * fz >= 0 ? 1 : -1;
    }

    private void startEmergency(String why) {
        emergency = true;
        emergAt = world.getTotalWorldTime();
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (TileTunnelPortal p : TrackHooks.live(world, TileTunnelPortal.class)) { double d = p.getPos().distanceSq(pos); if (d < bd && d < 2048 * 2048) { bd = d; best = p.getPos(); rescue = p.name(); } }
        if (best == null) rescue = "NEAREST PORTAL";
        AlarmManager.get(world).raise(world, "tun:" + pos.toLong() + ":em", AlarmManager.Cat.FIRE, AlarmManager.Sev.CRITICAL, "TUNNEL EMERGENCY " + id() + ": " + why + " - signals held, traction isolation requested, evacuate", pos, true);
        Recorder.event(world, "TUNNEL EMERGENCY " + id() + ": " + why);
        for (EntityPlayer pl : world.playerEntities)
            if (inZone(pl.posX, pl.posZ) && Math.abs(pl.posY - pos.getY()) < 24)
                pl.sendMessage(new TextComponentString("§c§l⚠ TUNNEL EMERGENCY§r§c - " + why + ". Leave by the nearest cross-passage: follow the green EXIT signs. Do not walk into the smoke."));
        markDirty();
    }

    private void live(List<TileTunnelFan> fans, List<TileSmokeDetector> dets) {
        for (int i = 0; i < 6; i++) {
            put("f" + (i + 1), i < fans.size() && fans.get(i).running());
            put("d" + (i + 1), i < dets.size() && dets.get(i).alarm());
        }
        List<TileCrossPassage> xs = mine(TileCrossPassage.class);
        for (int i = 0; i < 4; i++) put("x" + (i + 1), i < xs.size() && (xs.get(i).open() || xs.get(i).occupied() > 0));
        boolean radio = false;
        for (TileLeakyFeeder r : mine(TileLeakyFeeder.class)) radio |= r.healthy();
        boolean phone = false;
        for (TileEntity t : world.loadedTileEntityList) if (t instanceof TileCallPoint c && inZone(t.getPos()) && c.controlValue("calling") == 1) { phone = true; break; }
        boolean lights = true;
        for (TileTunnelLight l : mine(TileTunnelLight.class)) lights &= l.light() > 0;
        put("fire", fire);
        put("co_hi", co > 50);
        put("auto", ctl.get("s_mode") == 1);
        put("emerg", emergency);
        put("trac", isolating);
        put("held", emergency && TrackConfig.tunnelEmergencyHoldsSignals);
        put("phone", phone);
        put("radio", radio);
        put("lt", lights);
        put("train", trains > 0);
        put("pwr", powered());
        put("g_co", (int) Math.min(200, Math.round(co)));
        text("co", String.format(Locale.ROOT, "CO %.0f PPM", co));
        text("vis", "VIS " + visibility() + " M");
        text("air", sec != null && sec.uc > 0 ? String.format(Locale.ROOT, "AIR %+.1f UC %.1f%s", air, sec.uc, sec.backlayer ? " BACKLAYER" : "") : String.format(Locale.ROOT, "AIR %+.1f M/S", air));
        text("kw", String.format(Locale.ROOT, "FANS %.0f KW", kw));
        text("trains", "TRAINS " + trains);
        text("pers", "PERSONS " + persons);
        text("rescue", emergency ? "RESCUE " + rescue : "NORMAL");
        text("zone", id() + " R" + zone());
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        switch (id) {
            case "test": return lampTest();
            case "m_emerg": {
                ctl.set("m_emerg", ctl.get("m_emerg") == 1 ? 0 : 1);
                return ctl.get("m_emerg") == 1 ? "§cEMERGENCY latched" : "§7Emergency button released - press RESET to end the emergency";
            }
            case "p_vent": ventRun = !ventRun; return ventRun ? "§eManual ventilation: fans running (MANUAL mode)" : "§7Manual ventilation stopped";
            case "p_sil": AlarmManager.get(world).clear("tun:" + pos.toLong() + ":co"); return "§7Silenced";
            case "p_reset": {
                if (!emergency) return "§7No emergency";
                if (fire) return "§cA detector still sees fire - can't reset";
                if (ctl.get("m_emerg") == 1) return "§cRelease the emergency button first";
                emergency = false;
                isolating = false;
                AlarmManager.get(world).clear("tun:" + pos.toLong() + ":em");
                Recorder.event(world, "TUNNEL " + id() + " emergency reset by " + who(who) + " after " + (world.getTotalWorldTime() - emergAt) / 20 + " s");
                return "§aTunnel emergency reset - signals released, traction may be restored";
            }
            default: return null;
        }
    }

    /** SafetyHolds: an emergency holds every signal into the tunnel */
    void holds(Map<Long, String> hold, List<Object[]> zones) {
        if (emergency && TrackConfig.tunnelEmergencyHoldsSignals) zones.add(new Object[]{pos.getX() + 0.5, pos.getZ() + 0.5, (double) zone(), "tunnel emergency " + id()});
    }

    @Override
    protected String[] assetRow() {
        return new String[]{"TN", id(), String.valueOf(zone()), emergency ? (fire ? "FIRE" : "EMERGENCY") : "normal", String.format(Locale.ROOT, "%.0f", co), String.valueOf(visibility()),
                String.format(Locale.ROOT, "%+.1f", air), String.format(Locale.ROOT, "%.0f", kw), String.valueOf(trains), String.valueOf(persons), new String[]{"OFF", "AUTO", "MANUAL"}[Math.min(2, ctl.get("s_mode"))], powered() ? "12V" : "NO 12V", xyz()};
    }

    @Override
    protected void save(NBTTagCompound t) {
        t.setDouble("co", co); t.setDouble("smoke", smoke); t.setBoolean("em", emergency); t.setBoolean("vent", ventRun); t.setString("rescue", rescue); t.setLong("emAt", emergAt); t.setInteger("stage", stage);
    }

    @Override
    protected void load(NBTTagCompound t) {
        co = t.getDouble("co"); smoke = t.getDouble("smoke"); emergency = t.getBoolean("em"); ventRun = t.getBoolean("vent"); rescue = t.getString("rescue"); emergAt = t.getLong("emAt"); stage = t.getInteger("stage");
        isolating = emergency;
    }
}
