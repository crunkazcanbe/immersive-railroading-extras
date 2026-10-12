package com.dogpound.railmap.track;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.sound.PackSounds;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Intelligent buffer stop (her ChatGPT batch 3 #9) at the end of a dead-end road. It knows how much energy it can take
 * (its TYPE: a FIXED rail-built stop ~0.3 MJ, a HYDRAULIC stop ~1.2 MJ over a 1.2 m ram stroke that recovers by itself,
 * a FRICTION stop ~3.5 MJ that slides along the rails on its shoes and has to be reset), and it watches every train
 * coming down the road:
 * <ul>
 *   <li><b>approach supervision</b> (Doppler radar on the headstock): for the approaching train's real mass it works out
 *       the fastest it may hit the stop and still be stopped (E = 1/2 m v^2 within the capacity left), and the braking
 *       curve back from there; a train above the curve gets an EMERGENCY BRAKE (MODE SUPERVISE) or an alarm (WARN);</li>
 *   <li><b>impact</b>: the energy of the hit is absorbed - the hydraulic rams go in, the friction shoes slide, a fixed
 *       stop takes damage - and the train is braked to a stand; a hit beyond what is left is an OVERRUN: the stop is
 *       wrecked (DAMAGED until rebuilt), a MAJOR alarm, and with config bufferOverrunDerails the leading vehicle comes
 *       off the end;</li>
 *   <li>shunting locos that buffer up on purpose: the INHIBIT key turns supervision off for 5 minutes (logged).</li>
 * </ul>
 * The red stop lamp is lit (battery backed); 12 V runs the radar and the panel - with no 12 V there is no supervision
 * (the stop still absorbs: it is a mechanical thing).
 */
public class TileBufferStop extends TrackGear implements TrackBlock.Lit {
    public static final String[] TYPES = {"FIXED", "HYDRAULIC", "FRICTION"};
    static final double[] CAP_KJ = {300, 1200, 3500};
    static final double SLIDE_MAX_M = 6.0, FRICTION_KN = 600, BRAKE_MS2 = 0.6;

    private double rampPct;                 // hydraulic: ram pushed in 0..100 %
    private double slidM;                   // friction: how far the stop has been shoved along the rails
    private double damagePct;               // fixed: damage 0..100 (100 = wrecked)
    private boolean wrecked;
    private double lastKJ, lastKmh;
    private int hits, overruns, interventions;
    private long inhibitUntil;
    // live
    private UUID approaching;
    private String approachName = "";
    private double appKmh, appDist = -1, appAllowed, appTonnes;
    private boolean overspeed, intervened, contact;
    private final Set<UUID> struck = new HashSet<>();

    public String id() { return "BS " + Math.floorMod(pos.getX() * 13 + pos.getZ() * 7, 1000); }
    @Override public String wiredName() { return "Buffer stop " + id(); }
    private int type() { return Math.max(0, Math.min(2, ctl.get("s_type"))); }
    private int mode() { return ctl.get("s_mode"); }                 // 0 SUPERVISE, 1 WARN ONLY, 2 OFF
    private boolean inhibited() { return world.getTotalWorldTime() < inhibitUntil; }

    /** energy the stop can still take (kJ) */
    double capacityLeft() {
        if (wrecked) return 0;
        return switch (type()) {
            case 1 -> CAP_KJ[1] * (1 - rampPct / 100.0);
            case 2 -> FRICTION_KN * Math.max(0, SLIDE_MAX_M - slidM);
            default -> CAP_KJ[0] * (1 - damagePct / 100.0);
        };
    }

    /** kinetic energy of a train (kJ) */
    static double energyKJ(double tonnes, double kmh) { double v = kmh / 3.6; return 0.5 * tonnes * 1000 * v * v / 1000; }

    /** the fastest a train of this mass may be going this far from the stop and still be stopped by it (km/h) */
    static double allowedKmh(double capKJ, double tonnes, double distM) {
        double vImp = Math.sqrt(2 * Math.max(0, capKJ) * 1000 / Math.max(1, tonnes * 1000));    // m/s at contact
        return 3.6 * Math.sqrt(vImp * vImp + 2 * BRAKE_MS2 * Math.max(0, distM - 2));
    }

    @Override public int light() { return wrecked ? 0 : 9; }

    /** the red stop lamp is battery backed: lit with or without 12 V until the stop is wrecked */
    @Override public int controlValue(String id) { return id.equals("lamp") ? (wrecked ? 0 : 1) : super.controlValue(id); }

    @Override public String gearState() {
        if (wrecked) return "DAMAGED - overrun, needs rebuilding (RESET with 6 iron ingots)";
        String s = TYPES[type()] + String.format(Locale.ROOT, " · %.0f kJ left", capacityLeft());
        if (!powered()) return s + " · NO 12 V - no approach supervision";
        if (approaching != null) s += String.format(Locale.ROOT, " · %s %.0f km/h at %.0f m (allowed %.0f)", approachName, appKmh, appDist, appAllowed);
        return s + (inhibited() ? " · INHIBITED" : "");
    }
    @Override public int wiredLamp() { return wrecked || !powered() ? 3 : overspeed ? 2 : 1; }
    @Override public void gearInfo(Map<String, Object> m) {
        m.put("type", TYPES[type()]); m.put("capKJ", Math.round(capacityLeft())); m.put("hits", hits); m.put("overruns", overruns); m.put("wrecked", wrecked);
        m.put("approachKmh", Math.round(appKmh)); m.put("approachM", Math.round(appDist));
    }

    // ---- the tick ------------------------------------------------------------------------------------------------------

    @Override
    protected void tickGear() {
        if (age % 4 == 0) watch();
        if (age % 20 != 0) { putLive(); return; }
        if (type() == 1 && rampPct > 0 && !contact) rampPct = Math.max(0, rampPct - 3);   // gas spring pushes the rams back out
        Alarms.state(world, "bufferov:" + pos.toLong(), overspeed && mode() != 2 && powered(), AlarmManager.Cat.SIGNAL, AlarmManager.Sev.MAJOR,
                String.format(Locale.ROOT, "BUFFER STOP %s: %s approaching at %.0f km/h, %.0f m out (allowed %.0f)%s", id(), approachName, appKmh, appDist, appAllowed, intervened ? " - EMERGENCY BRAKE" : ""), pos);
        putLive();
    }

    /** where a train touches the stop, and the way trains come from */
    private Vec3d face() { EnumFacing f = controlFacing(); return new Vec3d(pos.getX() + 0.5 + f.getXOffset() * 0.5, pos.getY(), pos.getZ() + 0.5 + f.getZOffset() * 0.5); }

    private void watch() {
        EnumFacing f = controlFacing();
        Vec3d b = face();
        EntityMoveableRollingStock near = null;
        double nearD = Double.MAX_VALUE;
        try {
            for (EntityMoveableRollingStock s : cam72cam.mod.world.World.get(world).getEntities(EntityMoveableRollingStock.class)) {
                if (s.isDead()) continue;
                Vec3d p = s.getPosition();
                double rx = p.x - b.x, rz = p.z - b.z;
                double along = rx * f.getXOffset() + rz * f.getZOffset();
                double lat = Math.abs(rx * f.getZOffset() - rz * f.getXOffset());
                if (along < -3 || along > 120 || lat > 3 || Math.abs(p.y - b.y) > 4) continue;
                double d = along - half(s);
                if (d < nearD) { nearD = d; near = s; }
            }
        } catch (LinkageError | RuntimeException e) { near = null; }
        if (near == null) { clearApproach(); return; }
        Vec3d v = near.getVelocity();
        double toward = -(v.x * f.getXOffset() + v.z * f.getZOffset());
        double kmh = Math.abs(near.getCurrentSpeed().metric());
        List<EntityRollingStock> train = new ArrayList<>();
        if (near instanceof EntityCoupleableRollingStock c) train.addAll(c.getTrain()); else train.add(near);
        double t = 0;
        for (EntityRollingStock s : train) t += Rails.tonnes(s);
        approaching = near.getUUID();
        approachName = Rails.name(near);
        appTonnes = t;
        appDist = Math.max(0, nearD);
        appKmh = toward > 0 ? kmh : 0;
        appAllowed = allowedKmh(capacityLeft(), t, appDist);
        // contact
        contact = nearD < 0.6;
        if (contact && appKmh > 0.3 && struck.add(near.getUUID())) impact(near, train, t, appKmh);
        if (nearD > 3 || appKmh < 0.1 && !contact) struck.remove(near.getUUID());
        // supervision (needs the radar = 12 V)
        overspeed = powered() && appKmh > 1 && appKmh > appAllowed * 1.05 + 1 && !inhibited();
        if (overspeed && mode() == 0 && !intervened) {
            intervened = true;
            interventions++;
            brake(train, false);
            Recorder.event(world, String.format(Locale.ROOT, "BUFFER STOP %s INTERVENED: %s at %.0f km/h %.0f m out, allowed %.0f for %.0f t - emergency brake", id(), approachName, appKmh, appDist, appAllowed, t));
            world.playSound(null, pos, PackSounds.ev("cab.brake_emergency"), net.minecraft.util.SoundCategory.BLOCKS, 1f, 1f);
        }
        if (intervened && kmh < 0.3) intervened = false;
    }

    private void clearApproach() { approaching = null; approachName = ""; appKmh = 0; appDist = -1; appAllowed = 0; overspeed = false; intervened = false; contact = false; }

    private static double half(EntityRollingStock s) {
        try { return s.getDefinition().getLength(s.gauge) / 2; } catch (RuntimeException e) { return 6; }
    }

    private void brake(List<EntityRollingStock> train, boolean handbrakes) {
        for (EntityRollingStock s : train) {
            if (s instanceof Locomotive l) { l.setThrottle(0); l.setTrainBrake(1); try { l.setIndependentBrake(1); } catch (RuntimeException ignored) { } }
            if (handbrakes && s instanceof EntityMoveableRollingStock m) m.setHandBrake(1f);
        }
    }

    private void impact(EntityMoveableRollingStock lead, List<EntityRollingStock> train, double tonnes, double kmh) {
        double kj = energyKJ(tonnes, kmh), left = capacityLeft();
        lastKJ = kj;
        lastKmh = kmh;
        hits++;
        brake(train, true);
        world.playSound(null, pos, PackSounds.ev("machine.clunk"), net.minecraft.util.SoundCategory.BLOCKS, (float) Math.min(2.0, 0.6 + kj / 400), 0.7f);
        if (kj <= left) {
            switch (type()) {
                case 1 -> rampPct = Math.min(100, rampPct + kj / CAP_KJ[1] * 100);
                case 2 -> slidM = Math.min(SLIDE_MAX_M, slidM + kj / FRICTION_KN);
                default -> damagePct = Math.min(100, damagePct + kj / CAP_KJ[0] * 35);
            }
            String txt = String.format(Locale.ROOT, "BUFFER STOP %s struck by %s at %.1f km/h: %.0f kJ absorbed (%.0f%% of what was left)", id(), Rails.name(lead), kmh, kj, left <= 0 ? 100 : kj * 100 / left);
            Recorder.event(world, txt);
            if (kj > left * 0.5) AlarmManager.get(world).raise(world, "bufferhit:" + pos.toLong(), AlarmManager.Cat.INCIDENT, AlarmManager.Sev.MINOR, txt, pos, false);
            if (type() == 1) world.playSound(null, pos, PackSounds.ev("air_release"), net.minecraft.util.SoundCategory.BLOCKS, 0.8f, 0.8f);
        } else {
            overruns++;
            wrecked = true;
            String txt = String.format(Locale.ROOT, "BUFFER STOP OVERRUN %s: %s hit it at %.1f km/h - %.0f kJ, it could take %.0f kJ. Stop destroyed", id(), Rails.name(lead), kmh, kj, left);
            Recorder.event(world, txt);
            AlarmManager.get(world).raise(world, "bufferrun:" + pos.toLong(), AlarmManager.Cat.INCIDENT, AlarmManager.Sev.MAJOR, txt, pos, true);
            world.playSound(null, pos, PackSounds.ev("machine.explosion"), net.minecraft.util.SoundCategory.BLOCKS, 0.8f, 1.3f);
            if (TrackConfig.bufferOverrunDerails) {
                try { cam72cam.immersiverailroading.wreck.Wrecks.forceWreck(lead, 2, "overran the buffer stop at " + Math.round(kmh) + " km/h"); } catch (LinkageError | RuntimeException ignored) { }
            }
        }
        markDirty();
    }

    private void putLive() {
        double cap = CAP_KJ[type()];
        put("app", approaching != null && appKmh > 0.5);
        put("ovs", overspeed);
        put("int", intervened);
        put("hit", contact);
        put("run", wrecked);
        put("inh", inhibited());
        put("rec", type() == 1 && rampPct > 0.5 && !contact);
        put("ok", !wrecked && capacityLeft() > cap * 0.5);
        put("pwr", powered());
        put("lamp", !wrecked);
        put("g_kmh", (int) Math.round(Math.min(60, appKmh)));
        put("g_cap", (int) Math.round(capacityLeft() * 100 / cap));
        text("speed", approaching == null ? "CLEAR" : String.format(Locale.ROOT, "%.0f KM/H %.0f M", appKmh, appDist));
        text("allow", approaching == null ? "--" : String.format(Locale.ROOT, "MAX %.0f KM/H", appAllowed));
        text("mass", approaching == null ? "--" : String.format(Locale.ROOT, "%.0f T", appTonnes));
        text("cap", String.format(Locale.ROOT, "%.0f KJ LEFT", capacityLeft()));
        text("last", hits == 0 ? "NO HITS" : String.format(Locale.ROOT, "LAST %.0f KJ %.0f KMH", lastKJ, lastKmh));
        text("stroke", switch (type()) { case 1 -> String.format(Locale.ROOT, "RAM %.0f%%", rampPct); case 2 -> String.format(Locale.ROOT, "SLID %.1f M", slidM); default -> String.format(Locale.ROOT, "DAMAGE %.0f%%", damagePct); });
        text("id", id());
        put("hits", hits);
    }

    // ---- controls --------------------------------------------------------------------------------------------------------

    @Override
    public String controlPress(String id, EntityPlayer who) {
        switch (id) {
            case "test": return lampTest();
            case "s_type": {
                if (hits > 0 && !wrecked && (rampPct > 0 || slidM > 0 || damagePct > 0)) return "§7Reset the stop before changing its type";
                ctl.set("s_type", (type() + 1) % 3);
                rampPct = slidM = damagePct = 0;
                return "§eBuffer stop type: " + TYPES[type()] + String.format(Locale.ROOT, " (%.0f kJ)", CAP_KJ[type()]);
            }
            case "k_inh": {
                if (inhibited()) { inhibitUntil = 0; ctl.set("k_inh", 0); Recorder.event(world, "BUFFER STOP " + id() + ": supervision restored by " + who(who)); return "§aSupervision back on"; }
                inhibitUntil = world.getTotalWorldTime() + 20 * 300;
                ctl.set("k_inh", 1);
                Recorder.event(world, "BUFFER STOP " + id() + ": supervision INHIBITED for shunting by " + who(who));
                return "§6Supervision inhibited for 5 minutes (shunting moves may buffer up)";
            }
            case "p_reset": {
                if (approaching != null && appDist < 10) return "§cStand the train back first (" + Math.round(appDist) + " m from the stop)";
                if (wrecked) {
                    if (who != null && !who.capabilities.isCreativeMode && !takeIron(who, 6)) return "§cRebuilding the stop needs 6 iron ingots";
                    wrecked = false; rampPct = slidM = damagePct = 0;
                    AlarmManager.get(world).clear("bufferrun:" + pos.toLong());
                    Recorder.event(world, "BUFFER STOP " + id() + " rebuilt by " + who(who));
                    return "§aBuffer stop rebuilt";
                }
                if (type() == 2 && slidM > 0) { slidM = 0; Recorder.event(world, "BUFFER STOP " + id() + ": friction stop jacked back into position by " + who(who)); world.playSound(null, pos, PackSounds.ev("machine.jack"), net.minecraft.util.SoundCategory.BLOCKS, 0.8f, 1f); return "§aFriction stop reset (shoes re-clamped)"; }
                if (type() == 0 && damagePct > 0) { if (who != null && !who.capabilities.isCreativeMode && !takeIron(who, 2)) return "§cRepairing needs 2 iron ingots"; damagePct = 0; return "§aFixed stop repaired"; }
                AlarmManager.get(world).clear("bufferhit:" + pos.toLong());
                return "§7Nothing to reset - " + gearState();
            }
            default: return null;
        }
    }

    private static boolean takeIron(EntityPlayer p, int n) {
        int got = 0;
        for (net.minecraft.item.ItemStack s : p.inventory.mainInventory) if (s.getItem() == net.minecraft.init.Items.IRON_INGOT) got += s.getCount();
        if (got < n) return false;
        for (net.minecraft.item.ItemStack s : p.inventory.mainInventory) if (n > 0 && s.getItem() == net.minecraft.init.Items.IRON_INGOT) { int t = Math.min(n, s.getCount()); s.shrink(t); n -= t; }
        return true;
    }

    @Override
    protected String[] assetRow() {
        return new String[]{"BS", id(), TYPES[type()], wrecked ? "OVERRUN - DAMAGED" : overspeed ? "OVERSPEED" : approaching != null ? "TRAIN NEAR" : "ok",
                switch (type()) { case 1 -> String.format(Locale.ROOT, "%.0f", rampPct); case 2 -> String.format(Locale.ROOT, "slid %.1f m", slidM); default -> String.format(Locale.ROOT, "dmg %.0f", damagePct); },
                approaching == null ? "-" : String.format(Locale.ROOT, "%.0f km/h %.0f m", appKmh, appDist), String.format(Locale.ROOT, "%.0f", lastKJ),
                String.format(Locale.ROOT, "%.0f", capacityLeft()), String.valueOf(hits), String.valueOf(overruns), inhibited() ? "INHIBIT" : mode() == 0 ? "SUPERVISE" : mode() == 1 ? "WARN" : "OFF", powered() ? "12V" : "NO 12V"};
    }

    /** for /irextras track buffer */
    public List<String> report() {
        List<String> l = new ArrayList<>();
        l.add("§b" + id() + " §7at " + Rails.xz(pos) + " · " + gearState());
        l.add(String.format(Locale.ROOT, "§7  hits %d · overruns %d · interventions %d · last %.0f kJ at %.1f km/h", hits, overruns, interventions, lastKJ, lastKmh));
        return l;
    }

    @Override
    protected void save(NBTTagCompound t) {
        t.setDouble("ram", rampPct); t.setDouble("slid", slidM); t.setDouble("dmg", damagePct); t.setBoolean("wreck", wrecked);
        t.setDouble("lastKJ", lastKJ); t.setDouble("lastKmh", lastKmh); t.setInteger("hits", hits); t.setInteger("ovr", overruns); t.setInteger("intv", interventions); t.setLong("inh", inhibitUntil);
    }

    @Override
    protected void load(NBTTagCompound t) {
        rampPct = t.getDouble("ram"); slidM = t.getDouble("slid"); damagePct = t.getDouble("dmg"); wrecked = t.getBoolean("wreck");
        lastKJ = t.getDouble("lastKJ"); lastKmh = t.getDouble("lastKmh"); hits = t.getInteger("hits"); overruns = t.getInteger("ovr"); interventions = t.getInteger("intv"); inhibitUntil = t.getLong("inh");
    }
}
