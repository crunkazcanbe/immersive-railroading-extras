package com.dogpound.railmap.freight;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.ModSounds;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.track.Rails;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.SoundCategory;

import java.util.*;

/**
 * Axle-Load Weighbridge (ChatGPT batch 3 #24): load cells under the rail with their instrument cabinet beside it. Unlike
 * the plain Track Scale (whole-train weight), it weighs each vehicle axle by axle as it rolls over (IN MOTION, up to
 * 15 km/h) or stands on it (STATIC): the tare spread evenly and the cargo where it really sits in the wagon's slots
 * (CargoRules.load) - so a wagon loaded all at one end or down one side shows it. Each vehicle gets a verdict:
 * OK, OVERLOAD (an axle over the route limit from the LIMIT selector, or the config limit on CFG), or UNBALANCED (side
 * to side or bogie to bogie past the config limits). A bad one lights the lamps, sounds the buzzer, raises a FREIGHT
 * alarm, goes in the recorder and the log (console STN > Weigh); with the HOLD key at STOP TRAIN (or the harsh config
 * switch) its train is stopped where it stands. CAL trims the load cells +-2 %. PRINT puts a weigh ticket in your hand.
 */
public class TileAxleWeighbridge extends CargoTile {
    static final String[] LIMITS = {"CFG", "16T", "18T", "20T", "22.5T", "25T", "30T", "32.5T"};
    static final double[] LIMIT_T = {0, 16, 18, 20, 22.5, 25, 30, 32.5};
    static final double[] CAL = {0, 0.01, 0.02, -0.02, -0.01};
    /** recent weighings, newest first: time|vehicle|axles|max t|limit|lateral %|long %|verdict|axle list */
    public final LinkedList<String> log = new LinkedList<>();
    private final Map<UUID, Long> seen = new HashMap<>();
    private CargoRules.Load last;
    private String lastName = "", verdict = "";
    private boolean tooFast, held;
    private int weighed, bad;

    @Override protected long load() { return 2; }                    // load-cell amplifiers, indicator, printer
    @Override public String wiredName() { return "Axle weighbridge"; }
    @Override public String gearState() { return !powered() ? "NO 12 V - not weighing" : !mainsOk() ? mains.what : weighed + " weighed, " + bad + " bad · limit " + String.format(Locale.ROOT, "%.1f t", limitT()); }

    public double limitT() { int i = Math.floorMod(ctl.get("s_limit"), LIMITS.length); return i == 0 ? CargoRules.axleLimitT() : LIMIT_T[i]; }
    double cal() { return CAL[Math.floorMod(ctl.get("s_cal"), CAL.length)]; }
    boolean inMotion() { return ctl.get("s_mode") == 1; }

    @Override
    protected String act(String id, EntityPlayer who) {
        switch (id) {
            case "reset" -> {
                verdict = ""; held = false;
                AlarmManager.get(world).clear("axle:" + pos.toLong());
                return "§eVerdict reset";
            }
            case "print" -> {
                if (who == null) return "§7The ticket printer is at the cabinet";
                if (log.isEmpty()) return "§7Nothing weighed yet";
                String[] p = log.getFirst().split("\\|");
                ItemStack t = new ItemStack(Items.PAPER);
                t.setStackDisplayName("§fWeigh Ticket: " + p[1]);
                NBTTagList lore = new NBTTagList();
                lore.appendTag(new NBTTagString("§7" + p[0] + " · " + p[2] + " axles · limit " + p[4] + " t"));
                lore.appendTag(new NBTTagString("§7Axles (t): " + (p.length > 8 ? p[8] : "")));
                lore.appendTag(new NBTTagString("§7Side " + p[5] + "% · end " + p[6] + "%"));
                lore.appendTag(new NBTTagString((p[7].equals("OK") ? "§a" : "§c") + p[7]));
                t.getOrCreateSubCompound("display").setTag("Lore", lore);
                if (!who.inventory.addItemStackToInventory(t)) who.dropItem(t, false);
                world.playSound(null, pos, ModSounds.PRINTER, SoundCategory.BLOCKS, 0.6f, 1f);
                return "§aWeigh ticket printed";
            }
            default -> { return null; }
        }
    }

    @Override
    protected void work() {
        if (age % 5 != 0) return;
        if (!live()) { readout(); return; }
        long now = world.getTotalWorldTime();
        tooFast = false;
        boolean busy = false;
        for (EntityRollingStock s : cam72cam.mod.world.World.get(world).getEntities(EntityRollingStock.class)) {
            if (s.isDead()) continue;
            Vec3d p = s.getPosition();
            if (Math.abs(p.y - pos.getY()) > 3 || Math.hypot(p.x - pos.getX() - 0.5, p.z - pos.getZ() - 0.5) > 2.5) continue;
            busy = true;
            double kmh = s instanceof EntityMoveableRollingStock m && m.getCurrentSpeed() != null ? Math.abs(m.getCurrentSpeed().metric()) : 0;
            if (kmh > (inMotion() ? 15 : 0.8)) { tooFast = true; continue; }
            Long t = seen.get(s.getUUID());
            if (t != null && now - t < 400) continue;
            seen.put(s.getUUID(), now);
            weigh(s, now);
        }
        seen.values().removeIf(t -> now - t > 1200);
        put("inmot", busy);
        readout();
    }

    private void weigh(EntityRollingStock s, long now) {
        CargoRules.Load l = CargoRules.load(s);
        double k = 1 + cal();
        for (int i = 0; i < l.axleT.length; i++) l.axleT[i] *= k;
        l.maxAxleT *= k; l.totalT *= k;
        last = l; lastName = Rails.name(s);
        weighed++;
        double lim = limitT();
        List<String> bads = new ArrayList<>();
        if (l.maxAxleT > lim) bads.add(String.format(Locale.ROOT, "OVERLOAD %.1f t > %.1f t", l.maxAxleT, lim));
        if (l.lateral * 100 > CargoConfig.lateralPct) bads.add(String.format(Locale.ROOT, "SIDE %.0f%% %s", l.lateral * 100, l.left ? "LEFT" : "RIGHT"));
        if (l.longitudinal * 100 > CargoConfig.longitudinalPct) bads.add(String.format(Locale.ROOT, "END %.0f%% %s", l.longitudinal * 100, l.front ? "FRONT" : "REAR"));
        verdict = bads.isEmpty() ? "OK" : String.join(" ", bads);
        StringBuilder ax = new StringBuilder();
        for (double a : l.axleT) ax.append(ax.length() > 0 ? " " : "").append(String.format(Locale.ROOT, "%.1f", a));
        log.addFirst(String.join("|", Recorder.clock(world, now), lastName, String.valueOf(l.axleT.length), String.format(Locale.ROOT, "%.1f", l.maxAxleT),
                String.format(Locale.ROOT, "%.1f", lim), String.valueOf(Math.round(l.lateral * 100)), String.valueOf(Math.round(l.longitudinal * 100)), verdict, ax.toString()));
        while (log.size() > 24) log.removeLast();
        if (bads.isEmpty()) return;
        bad++;
        world.playSound(null, pos, ModSounds.BUZZER, SoundCategory.BLOCKS, 0.8f, 1f);
        AlarmManager.get(world).raise(world, "axle:" + pos.toLong(), AlarmManager.Cat.FREIGHT, AlarmManager.Sev.MINOR, "Weighbridge " + pos.getX() + "," + pos.getZ() + ": " + lastName + " " + verdict, pos, false);
        Recorder.event(world, "Weighbridge: " + lastName + " " + verdict + " (axles " + ax + " t)");
        com.dogpound.railmap.api.QuestEvents.fire(world, null, pos, "axle_overload", "vehicle", lastName, "max", String.format(Locale.ROOT, "%.1f", l.maxAxleT), "verdict", verdict);
        if ((ctl.get("k_hold") == 1 || CargoConfig.overloadStopsTrain) && s instanceof EntityCoupleableRollingStock c) {
            for (EntityCoupleableRollingStock x : c.getTrain()) if (x instanceof Locomotive loco) { loco.setThrottle(0); loco.setTrainBrake(1f); held = true; }
            if (held) Recorder.event(world, "Weighbridge stopped the train of " + lastName);
        }
    }

    private void readout() {
        boolean ok = verdict.equals("OK");
        put("pwr", powered()); put("ok", ok); put("over", verdict.contains("OVERLOAD")); put("imbal", verdict.contains("SIDE") || verdict.contains("END"));
        put("fast", tooFast); put("stopl", held); put("holdl", ctl.get("k_hold") == 1 || CargoConfig.overloadStopsTrain);
        put("maxax", last == null ? 0 : (int) Math.round(last.maxAxleT));
        put("latg", last == null ? 0 : (int) Math.round(last.lateral * 100)); put("long", last == null ? 0 : (int) Math.round(last.longitudinal * 100));
        text("car", last == null ? "NO VEHICLE YET" : cut(lastName, 14));
        text("tot", last == null ? "" : String.format(Locale.ROOT, "%.1fT %dAX MAX %.1f", last.totalT, last.axleT.length, last.maxAxleT));
        StringBuilder ax = new StringBuilder();
        if (last != null) for (double a : last.axleT) ax.append(ax.length() > 0 ? " " : "").append(String.format(Locale.ROOT, "%.1f", a));
        text("ax", ax.toString());
        text("lat", last == null ? "" : String.format(Locale.ROOT, "SIDE %s %.0f%%", last.left ? "L" : "R", last.lateral * 100));
        text("lon", last == null ? "" : String.format(Locale.ROOT, "END %s %.0f%%", last.front ? "F" : "R", last.longitudinal * 100));
        text("verd", tooFast ? "TOO FAST" : verdict.isEmpty() ? "READY" : cut(verdict, 18));
        text("limit", String.format(Locale.ROOT, "LIMIT %.1fT", limitT()));
    }


    @Override protected void saveExtra(NBTTagCompound t) {
        NBTTagList l = new NBTTagList();
        for (String s : log) l.appendTag(new NBTTagString(s));
        t.setTag("wlog", l); t.setString("verd", verdict); t.setString("lname", lastName); t.setInteger("weighed", weighed); t.setInteger("bad", bad); t.setBoolean("held", held);
    }
    @Override protected void loadExtra(NBTTagCompound t) {
        log.clear();
        NBTTagList l = t.getTagList("wlog", 8);
        for (int i = 0; i < l.tagCount(); i++) log.add(l.getStringTagAt(i));
        verdict = t.getString("verd"); lastName = t.getString("lname"); weighed = t.getInteger("weighed"); bad = t.getInteger("bad"); held = t.getBoolean("held");
    }
}
