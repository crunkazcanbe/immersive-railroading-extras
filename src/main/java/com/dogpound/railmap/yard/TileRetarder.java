package com.dogpound.railmap.yard;

import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.tile.TileRail;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.sound.PackSounds;
import com.dogpound.railmap.track.GridFeed;
import com.dogpound.railmap.track.Rails;
import com.dogpound.railmap.track.TrackGear;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Yard retarder (her ChatGPT batch 3 #25): a pneumatic rail brake beside a hump / class track. Its brake beams squeeze
 * the wheels of cars rolling through it (in Immersive Railroading: the car's brake is worked while it is in the
 * retarder, so it really slows), worked by
 * <ul>
 *   <li>a <b>speed radar</b> on each car entering, and <b>weigh-in-motion</b> (the car's mass);</li>
 *   <li>a <b>target exit speed</b>: fixed (4-15 km/h) or CALCULATED - the free distance to the last car standing on the
 *       track beyond it and the car's rolling resistance (heavy cars roll better) give the speed that brings it to
 *       coupling speed (6 km/h) just as it reaches them;</li>
 *   <li><b>compressed air</b>: an electric compressor (22 kW, 400 V from a touching grid Power Cable) keeps the
 *       reservoir at 7-8.5 bar; every application uses air, and the beams can't close properly below 3.5 bar;</li>
 *   <li><b>brake shoes</b> that wear with the energy they take (replace them with iron ingots); worn shoes brake weaker;</li>
 *   <li>MODE: AUTO, MANUAL (the operator's STEP selector), OPEN, CLOSED. No 12 V = the beams spring CLOSED (fail safe:
 *       nothing runs away).</li>
 * </ul>
 * The Hump Yard Control Panel's own (virtual) retarding defers to a real retarder for the cars it holds.
 */
public class TileRetarder extends TrackGear {
    public static final String[] TYPES = {"BEAM (master)", "CLASP (group)"};
    public static final String[] MODES = {"AUTO", "MANUAL", "OPEN", "CLOSED"};
    static final int[] TARGETS = {4, 6, 8, 10, 12, 15, -1};     // -1 = calculated
    static final double COUPLE_KMH = 6;

    /** car -> brake demand from the retarder holding it this tick (0..1); read by the yard panel */
    private static final Map<UUID, Float> GRIP = new HashMap<>();

    /** the brake a real retarder is putting on this car now, or null if none has it */
    public static Float grip(UUID car) { return GRIP.get(car); }

    private long rail;                         // linked rail (0 = in front of the block)
    private double bar = 7.5, shoes = 100;     // reservoir pressure, shoe life %
    private boolean compressor;
    private String supply = "";
    private int cars, fastExits;
    private double lastIn, lastOut, lastTarget, lastTonnes, lastFree = -1;
    private final Map<UUID, double[]> inZone = new HashMap<>();   // car -> {entry km/h, tonnes, target}
    private float beam;                        // 0..1 how hard the beams are closed (display)
    private long squealAt;

    public String id() { return "RET " + Math.floorMod(pos.getX() * 19 + pos.getZ() * 3, 1000); }
    @Override public String wiredName() { return "Yard retarder " + id(); }
    private int mode() { return Math.max(0, Math.min(3, ctl.get("s_mode"))); }
    private int type() { return ctl.get("s_type") == 1 ? 1 : 0; }
    private int target() { return TARGETS[Math.max(0, Math.min(TARGETS.length - 1, ctl.get("s_target")))]; }
    private double lengthM() { return type() == 0 ? 12 : 7; }
    private double strength() { return (type() == 0 ? 1.0 : 0.7) * Math.min(1, Math.max(0, (bar - 2.0) / 3.5)) * (shoes > 0 ? 0.5 + shoes / 200 : 0.25); }

    /** the zone centre (the linked rail, else just in front of the block) and the track's axis there */
    private Vec3d centre() {
        if (rail != 0) { BlockPos r = BlockPos.fromLong(rail); return new Vec3d(r.getX() + 0.5, r.getY(), r.getZ() + 0.5); }
        EnumFacing f = controlFacing();
        return new Vec3d(pos.getX() + 0.5 + f.getXOffset() * 1.5, pos.getY(), pos.getZ() + 0.5 + f.getZOffset() * 1.5);
    }
    private EnumFacing axis() { return controlFacing().rotateY(); }

    @Override public String gearState() {
        if (!powered()) return "NO 12 V - beams sprung CLOSED (fail safe)";
        return MODES[mode()] + " · " + String.format(Locale.ROOT, "%.1f bar · shoes %.0f%% · ", bar, shoes) + (target() < 0 ? "target CALCULATED" : "target " + target() + " km/h") + (inZone.isEmpty() ? "" : " · " + inZone.size() + " car(s) in");
    }
    @Override public int wiredLamp() { return !powered() || bar < 3.5 || shoes <= 0 ? 3 : shoes < 25 ? 2 : 1; }
    @Override public void gearInfo(Map<String, Object> m) { m.put("bar", Math.round(bar * 10) / 10.0); m.put("shoesPct", Math.round(shoes)); m.put("cars", cars); m.put("lastInKmh", Math.round(lastIn)); m.put("lastOutKmh", Math.round(lastOut)); m.put("supply", supply); }

    @Override
    public String link(String control, BlockPos pick, EntityPlayer who) {
        TileRail r = Rails.pieceAt(world, pick);
        if (r == null) return "§cPick the rail the retarder brakes (Signal Wrench), then click the RAIL display";
        if (pick.distanceSq(pos) > 12 * 12) return "§cThat rail is too far - the retarder sits beside its track (12 blocks)";
        rail = pick.toLong();
        markDirty();
        return "§a" + id() + " brakes cars on the rail at " + Rails.xz(pick) + " (" + Math.round(lengthM()) + " m of beams)";
    }

    // ---- the tick ------------------------------------------------------------------------------------------------------

    @Override
    protected void tickGear() {
        if (age % 2 == 0) brake();
        if (age % 20 != 0) { putLive(); return; }
        // compressor: 22 kW on the grid while it pumps (cut in 7.0, cut out 8.5 bar)
        if (bar < 7.0) compressor = true;
        if (bar >= 8.5) compressor = false;
        if (compressor && powered()) {
            GridFeed.Result r = GridFeed.draw(world, pos, 22);
            supply = r.what;
            if (r.ok) bar = Math.min(8.6, bar + 0.35);
            else compressor = bar < 7.0;
        } else supply = GridFeed.draw(world, pos, 0).what;
        bar = Math.max(0, bar - 0.002);                                  // leaks
        com.dogpound.railmap.alarm.AlarmManager am = AlarmManager.get(world);
        if (bar < 3.5 && powered()) am.raise(world, "retair:" + pos.toLong(), AlarmManager.Cat.MACHINE, AlarmManager.Sev.MAJOR, "RETARDER " + id() + ": air " + String.format(Locale.ROOT, "%.1f", bar) + " bar - can't brake (" + supply + ")", pos, true);
        else am.clear("retair:" + pos.toLong());
        if (shoes <= 10) am.raise(world, "retshoe:" + pos.toLong(), AlarmManager.Cat.MACHINE, AlarmManager.Sev.MINOR, "RETARDER " + id() + ": brake shoes " + Math.round(shoes) + "% - replace them", pos, true);
        else am.clear("retshoe:" + pos.toLong());
        putLive();
    }

    private void brake() {
        Vec3d c = centre();
        EnumFacing ax = axis();
        double half = lengthM() / 2;
        Set<UUID> seen = new HashSet<>();
        float maxBeam = 0;
        List<EntityMoveableRollingStock> all;
        try { all = cam72cam.mod.world.World.get(world).getEntities(EntityMoveableRollingStock.class); } catch (LinkageError | RuntimeException e) { return; }
        for (EntityMoveableRollingStock s : all) {
            if (s.isDead() || s instanceof Locomotive) continue;          // a retarder brakes cars, not engines
            Vec3d p = s.getPosition();
            double rx = p.x - c.x, rz = p.z - c.z;
            double along = rx * ax.getXOffset() + rz * ax.getZOffset(), lat = Math.abs(rx * ax.getZOffset() - rz * ax.getXOffset());
            if (Math.abs(along) > half || lat > 3 || Math.abs(p.y - c.y) > 3) continue;
            UUID u = s.getUUID();
            seen.add(u);
            double kmh = Math.abs(s.getCurrentSpeed().metric());
            double t = Rails.tonnes(s);
            double[] rec = inZone.get(u);
            if (rec == null) {                                            // radar + weigh-in-motion as it enters
                double tgt = target() >= 0 ? target() : calcTarget(s, t);
                rec = new double[]{kmh, t, tgt};
                inZone.put(u, rec);
                cars++;
                lastIn = kmh; lastTonnes = t; lastTarget = tgt;
            }
            float hb;
            if (!powered()) hb = (float) Math.min(1, strength() * 1.2);   // sprung closed
            else switch (mode()) {
                case 1 -> hb = (float) (Math.max(0, Math.min(4, ctl.get("s_step"))) / 4.0 * strength());
                case 2 -> hb = 0;
                case 3 -> hb = (float) Math.min(1, strength() * 1.2);
                default -> hb = kmh > rec[2] + 0.4 ? (float) Math.min(1, (0.25 + (kmh - rec[2]) / 4) * strength()) : 0;
            }
            if (hb > 0) {
                bar = Math.max(0, bar - 0.004 * hb);                      // each application uses air
                shoes = Math.max(0, shoes - 0.0004 * hb * kmh * Math.max(1, t / 30));
                if (world.getTotalWorldTime() - squealAt > 20 && kmh > 2) { squealAt = world.getTotalWorldTime(); world.playSound(null, pos, PackSounds.ev("train.flange_squeal"), SoundCategory.BLOCKS, 0.6f + hb * 0.6f, 1.3f); }
            }
            GRIP.put(u, hb);
            if (Math.abs(s.getHandBrake() - hb) > 0.04f) s.setHandBrake(hb);
            maxBeam = Math.max(maxBeam, hb);
        }
        // cars that left: release what we put on, record the exit speed
        for (Iterator<Map.Entry<UUID, double[]>> it = inZone.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, double[]> e = it.next();
            if (seen.contains(e.getKey())) continue;
            it.remove();
            GRIP.remove(e.getKey());
            EntityMoveableRollingStock s = null;
            for (EntityMoveableRollingStock x : all) if (x.getUUID().equals(e.getKey())) { s = x; break; }
            if (s == null) continue;
            if (s.getHandBrake() < 0.99f) s.setHandBrake(0);
            lastOut = Math.abs(s.getCurrentSpeed().metric());
            if (lastOut > e.getValue()[2] + 3 && mode() == 0 && powered()) {
                fastExits++;
                Recorder.event(world, String.format(Locale.ROOT, "RETARDER %s: %s left at %.0f km/h (target %.0f) - %s", id(), Rails.name(s), lastOut, e.getValue()[2], bar < 3.5 ? "no air" : shoes < 20 ? "shoes worn" : "too heavy / too fast"));
                if (lastOut > e.getValue()[2] + 6) AlarmManager.get(world).raise(world, "retfast:" + pos.toLong(), AlarmManager.Cat.AUTO, AlarmManager.Sev.MINOR,
                        String.format(Locale.ROOT, "RETARDER %s: %s ran out at %.0f km/h (target %.0f) - expect a hard coupling", id(), Rails.name(s), lastOut, e.getValue()[2]), pos, false);
            }
        }
        beam = maxBeam;
    }

    /** CALCULATED target: free distance to the last car standing beyond the retarder, and this car's rolling resistance */
    private double calcTarget(EntityMoveableRollingStock car, double tonnes) {
        Vec3d c = centre(), v = car.getVelocity();
        EnumFacing ax = axis();
        double dir = v.x * ax.getXOffset() + v.z * ax.getZOffset() >= 0 ? 1 : -1;
        double free = 400;
        try {
            for (EntityMoveableRollingStock s : cam72cam.mod.world.World.get(world).getEntities(EntityMoveableRollingStock.class)) {
                if (s == car || Math.abs(s.getCurrentSpeed().metric()) > 1) continue;
                Vec3d p = s.getPosition();
                double rx = p.x - c.x, rz = p.z - c.z;
                double along = (rx * ax.getXOffset() + rz * ax.getZOffset()) * dir, lat = Math.abs(rx * ax.getZOffset() - rz * ax.getXOffset());
                if (along > lengthM() / 2 && along < free && lat < 3 && Math.abs(p.y - c.y) < 6) free = along;
            }
        } catch (LinkageError | RuntimeException ignored) { }
        lastFree = free;
        return targetFor(free, tonnes);
    }

    /** the exit speed that rolls a car of this mass `free` metres and arrives at coupling speed (km/h, 3..18) */
    static double targetFor(double freeM, double tonnes) {
        double aRoll = 0.035 + 0.9 / Math.max(10, tonnes);                // m/s2: light empties roll worse (more resistance per tonne)
        double vc = COUPLE_KMH / 3.6;
        double v = Math.sqrt(vc * vc + 2 * aRoll * Math.max(0, freeM - 10));
        return Math.max(3, Math.min(18, v * 3.6));
    }

    /** pure checks for /irextras track retarder selftest */
    public static List<String> selftest() {
        List<String> o = new ArrayList<>();
        int[] n = new int[2];
        java.util.function.BiConsumer<Boolean, String> c = (ok, w) -> { o.add((ok ? "§aPASS " : "§cFAIL ") + w); n[ok ? 0 : 1]++; };
        c.accept(Math.abs(targetFor(10, 60) - COUPLE_KMH) < 0.01, "no free room: leave at coupling speed");
        c.accept(targetFor(300, 60) > targetFor(50, 60), "more free track = faster exit");
        c.accept(targetFor(200, 20) > targetFor(200, 90), "a light empty rolls worse, so it may leave faster");
        c.accept(targetFor(5000, 90) <= 18 && targetFor(0, 5) >= 3, "exit speed held to 3..18 km/h");
        o.add((n[1] == 0 ? "§a" : "§c") + "Retarder self-test: " + n[0] + " pass, " + n[1] + " fail");
        return o;
    }

    private void putLive() {
        put("car", !inZone.isEmpty());
        put("brk", beam > 0.05f);
        put("air", bar >= 5);
        put("lowair", bar < 3.5);
        put("comp", compressor && supply.contains("kW"));
        put("shoe", shoes < 25);
        put("fast", fastExits > 0 && ctl.get("ack") == 0);
        put("closed", mode() == 3 || !powered());
        put("open", mode() == 2 && powered());
        put("pwr", powered());
        put("sup", supply.contains("available") || supply.contains("kW") || supply.contains("config"));
        put("g_bar", (int) Math.round(bar * 10));
        put("g_beam", Math.round(beam * 100));
        put("cars", cars);
        text("in", String.format(Locale.ROOT, "IN %.1f KMH", lastIn));
        text("out", String.format(Locale.ROOT, "OUT %.1f KMH", lastOut));
        text("tgt", String.format(Locale.ROOT, "TGT %.1f%s", target() >= 0 ? target() : lastTarget, target() < 0 ? " C" : ""));
        text("wim", String.format(Locale.ROOT, "%.0f T", lastTonnes));
        text("free", lastFree < 0 ? "FREE --" : String.format(Locale.ROOT, "FREE %.0f M", lastFree));
        text("shoes", String.format(Locale.ROOT, "SHOES %.0f%%", shoes));
        text("bar", String.format(Locale.ROOT, "%.1f BAR", bar));
        text("rail", rail == 0 ? "IN FRONT" : Rails.xz(BlockPos.fromLong(rail)));
        text("id", id());
        text("sup", supply.isEmpty() ? "--" : supply.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9 .:/%+-]", ""));
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        switch (id) {
            case "test": return lampTest();
            case "p_ack": { ctl.set("ack", 1); fastExits = 0; AlarmManager.get(world).clear("retfast:" + pos.toLong()); ctl.set("ack", 0); return "§aFast-exit warnings acknowledged"; }
            case "p_shoes": {
                if (!inZone.isEmpty()) return "§cA car is in the retarder";
                if (mode() != 2) return "§7Set the retarder OPEN before changing shoes (beams must be released)";
                if (who != null && !who.capabilities.isCreativeMode) {
                    int got = 0;
                    for (net.minecraft.item.ItemStack s : who.inventory.mainInventory) if (s.getItem() == net.minecraft.init.Items.IRON_INGOT) got += s.getCount();
                    if (got < 4) return "§cNew brake shoes need 4 iron ingots";
                    int n = 4;
                    for (net.minecraft.item.ItemStack s : who.inventory.mainInventory) if (n > 0 && s.getItem() == net.minecraft.init.Items.IRON_INGOT) { int t = Math.min(n, s.getCount()); s.shrink(t); n -= t; }
                }
                shoes = 100;
                Recorder.event(world, "RETARDER " + id() + ": brake shoes renewed by " + who(who));
                return "§aBrake shoes renewed";
            }
            case "p_drain": { bar = Math.max(0, bar - 2); return "§eReservoir drained to " + String.format(Locale.ROOT, "%.1f bar", bar); }
            default: return null;
        }
    }

    @Override protected String[] assetRow() {
        return new String[]{"RT", id(), TYPES[type()], MODES[mode()], String.format(Locale.ROOT, "%.1f", bar), target() < 0 ? String.format(Locale.ROOT, "calc %.0f", lastTarget) : target() + " km/h",
                String.format(Locale.ROOT, "%.1f", lastIn), String.format(Locale.ROOT, "%.1f", lastOut), String.valueOf(cars), String.format(Locale.ROOT, "%.0f", shoes),
                bar < 3.5 ? "FAULT low air" : shoes <= 10 ? "FAULT shoes" : fastExits > 0 ? fastExits + " fast exits" : "ok", powered() ? "12V" : "NO 12V"};
    }

    public List<String> report() {
        List<String> l = new ArrayList<>();
        l.add("§b" + id() + " §7at " + Rails.xz(pos) + " · " + gearState());
        l.add(String.format(Locale.ROOT, "§7  %d cars · last in %.1f out %.1f (target %.1f, %.0f t, free %.0f m) · %d fast exits · air %s", cars, lastIn, lastOut, lastTarget, lastTonnes, lastFree, fastExits, supply));
        return l;
    }

    @Override protected void save(NBTTagCompound t) { t.setLong("rail", rail); t.setDouble("bar", bar); t.setDouble("shoes", shoes); t.setInteger("cars", cars); t.setInteger("fast", fastExits); }
    @Override protected void load(NBTTagCompound t) { rail = t.getLong("rail"); bar = t.hasKey("bar") ? t.getDouble("bar") : 7.5; shoes = t.hasKey("shoes") ? t.getDouble("shoes") : 100; cars = t.getInteger("cars"); fastExits = t.getInteger("fast"); }

    @Override
    public void invalidate() {
        super.invalidate();
        for (UUID u : inZone.keySet()) GRIP.remove(u);                   // broken / unloaded: let go of whatever we held
    }
}
