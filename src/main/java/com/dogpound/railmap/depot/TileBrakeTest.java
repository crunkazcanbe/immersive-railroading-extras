package com.dogpound.railmap.depot;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.server.WearData;
import com.dogpound.railmap.sim.SimData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;

import java.util.*;

/**
 * Brake Test Rig (her ChatGPT list #57): plugged into the train standing at it (its locomotive within 10 blocks), it
 * works the train's own brakes through a full test before it goes back into service:
 * <ol>
 *   <li>CHARGE - release the train brake (the loco held on its independent brake) and see every vehicle release;</li>
 *   <li>APPLY - a service application: each vehicle's brake cylinder must come on (the response time of the last one is
 *   timed) - CONTINUITY passes when the rear vehicle responds;</li>
 *   <li>LEAK - with the brake applied the brake pipe may drop no more than 0.35 bar a minute (worn brake gear and worn
 *   coupler hoses leak);</li>
 *   <li>PARK - every handbrake applied and checked on, then put back;</li>
 *   <li>RESULT - released; PASS needs all vehicles responding, the leak in limit and the brake force (condition) at
 *   85 % or more. A pass certifies the brakes (closes BRAKE TEST cards, clears brake work); a fail stops the vehicles
 *   that failed and raises a brake repair.</li>
 * </ol>
 */
public class TileBrakeTest extends DepotTile {
    private static final String[] STEPS = {"IDLE", "CHARGE", "APPLY", "LEAK", "PARK", "RESULT"};
    private static final int[] SECS = {0, 10, 8, 20, 6, 3};
    private UUID loco;
    private int step, secs, cars, responded, force, slowest, result;     // result 0 none, 1 pass, 2 fail
    private double leak, bp = 0;
    private final Map<UUID, Integer> respAt = new HashMap<>();
    private final Map<UUID, Float> handWas = new HashMap<>();
    private final List<String> report = new ArrayList<>();

    @Override public String wiredName() { return "Brake test rig"; }
    @Override public String gearState() { return !powered() ? "NO 12 V" : STEPS[step] + (result == 1 ? " · last test PASS" : result == 2 ? " · last test FAIL" : ""); }

    Locomotive loco() {
        if (loco != null) { Locomotive l = com.dogpound.railmap.auto.Autopilot.find(world, loco); if (l != null) return l; }
        Locomotive best = null;
        double bd = 100;
        for (Locomotive l : cam72cam.mod.world.World.get(world).getEntities(Locomotive.class)) {
            Vec3d p = l.getPosition();
            double d = pos.distanceSqToCenter(p.x, p.y, p.z);
            if (!l.isDead() && d < bd) { bd = d; best = l; }
        }
        return best;
    }

    List<EntityCoupleableRollingStock> train(Locomotive l) { try { return l.getTrain(); } catch (RuntimeException e) { return Collections.singletonList(l); } }

    @Override
    protected String press(String id, EntityPlayer who) {
        switch (id) {
            case "start" -> {
                if (step != 0) return "§7A test is running";
                if (ctl.get("m_estop") == 1) return "§cE-STOP is in";
                Locomotive l = loco();
                if (l == null) return "§cNo train at the rig (locomotive within 10 blocks)";
                if (l.getCurrentSpeed() != null && Math.abs(l.getCurrentSpeed().metric()) > 0.5) return "§cThe train must be standing";
                loco = l.getUUID();
                respAt.clear(); handWas.clear(); report.clear();
                cars = train(l).size(); responded = 0; slowest = 0; result = 0; leak = 0;
                l.setThrottle(0);
                l.setIndependentBrake(1);
                begin(1);
                report.add("BRAKE TEST " + FleetData.get(world).unit(loco).name + " " + DepotClock.stamp(world) + " - " + cars + " vehicles");
                return "§aBrake test started on " + FleetData.get(world).unit(loco).name + " (" + cars + " vehicles)";
            }
            case "abort" -> { if (step == 0) return "§7No test running"; abort("aborted"); return "§eTest aborted - brakes released"; }
            case "print" -> {
                if (who == null || report.isEmpty()) return "§7Nothing to print";
                who.inventory.addItemStackToInventory(Diagnostics.book("Brake test", "Brake test rig", report));
                return "§aTest certificate printed";
            }
            default -> { return null; }
        }
    }

    @Override
    protected String mechanical(String id, EntityPlayer who) {
        if (id.equals("m_estop")) { ctl.set(id, ctl.get(id) == 1 ? 0 : 1); if (ctl.get(id) == 1 && step != 0) abort("E-STOP"); return ctl.get(id) == 1 ? "§cE-STOP" : "§aE-stop released"; }
        return null;
    }

    private void begin(int s) { step = s; secs = 0; }

    private void abort(String why) {
        Locomotive l = loco();
        if (l != null) {
            l.setTrainBrake(1);
            for (EntityCoupleableRollingStock c : train(l)) if (handWas.containsKey(c.getUUID())) c.setHandBrake(handWas.get(c.getUUID()));
        }
        report.add("ABORTED: " + why);
        step = 0;
    }

    @Override
    protected void work() {
        if (age % 20 != 0) return;
        Locomotive l = step == 0 ? null : loco();
        if (step != 0 && (l == null || !powered() || l.getCurrentSpeed() != null && Math.abs(l.getCurrentSpeed().metric()) > 1)) abort(l == null ? "train gone" : !powered() ? "rig lost 12 V" : "train moved");
        if (step != 0) {
            secs++;
            List<EntityCoupleableRollingStock> t = train(l);
            FleetData f = FleetData.get(world);
            switch (step) {
                case 1 -> {
                    l.setTrainBrake(0);
                    bp = Math.min(5.0, bp + 0.6);
                    if (secs >= SECS[1]) {
                        int held = 0;
                        for (EntityCoupleableRollingStock c : t) if (c.getBrakePressure() > 0.15f) held++;
                        report.add(String.format(Locale.ROOT, "CHARGE: pipe %.1f bar, %d of %d released", bp, t.size() - held, t.size()));
                        begin(2);
                    }
                }
                case 2 -> {
                    l.setTrainBrake(0.7f);
                    bp = Math.max(3.5, bp - 0.4);
                    for (EntityCoupleableRollingStock c : t) {
                        FleetData.Unit u = f.unit(c.getUUID());
                        boolean failed = WearData.get(world).of(c.getUUID()).wear[Condition.B] >= 100;
                        if (!failed && c.getBrakePressure() > 0.3f) respAt.putIfAbsent(c.getUUID(), secs);
                    }
                    if (secs >= SECS[2]) {
                        responded = respAt.size();
                        slowest = respAt.values().stream().mapToInt(i -> i).max().orElse(0);
                        EntityCoupleableRollingStock rear = t.get(t.size() - 1);
                        boolean cont = respAt.containsKey(rear.getUUID());
                        report.add(String.format(Locale.ROOT, "APPLY: %d of %d responded, slowest %d s; CONTINUITY %s", responded, t.size(), slowest, cont ? "OK (rear responded)" : "FAIL"));
                        for (EntityCoupleableRollingStock c : t) if (!respAt.containsKey(c.getUUID())) report.add("  NO RESPONSE: " + f.unit(c.getUUID()).name);
                        begin(3);
                    }
                }
                case 3 -> {
                    if (secs == 1) {
                        double worst = 0.05;
                        for (EntityCoupleableRollingStock c : t) {
                            float wear = WearData.get(world).of(c.getUUID()).wear[Condition.B];
                            SimData.Car sc = SimData.get(world).of(c.getUUID());
                            worst += wear * 0.0025 + (Math.min(sc.coupler[0], sc.coupler[1]) < 0.6f ? 0.12 : 0);
                        }
                        leak = worst;
                    }
                    bp = Math.max(0, 3.5 - leak * secs / 60.0);
                    if (secs >= SECS[3]) { report.add(String.format(Locale.ROOT, "LEAK: %.2f bar/min (limit 0.35) %s", leak, leak <= 0.35 ? "OK" : "FAIL")); begin(4); }
                }
                case 4 -> {
                    if (secs == 1) for (EntityCoupleableRollingStock c : t) { handWas.put(c.getUUID(), c.getHandBrake()); c.setHandBrake(1); }
                    if (secs >= SECS[4]) {
                        int on = 0;
                        for (EntityCoupleableRollingStock c : t) { if (c.getHandBrake() > 0.9f) on++; if (handWas.containsKey(c.getUUID())) c.setHandBrake(handWas.get(c.getUUID())); }
                        report.add("PARK: " + on + " of " + t.size() + " handbrakes on " + (on == t.size() ? "OK" : "FAIL"));
                        ctl.set("parkok", on == t.size() ? 1 : 0);
                        begin(5);
                    }
                }
                case 5 -> {
                    l.setTrainBrake(0);
                    if (secs >= SECS[5]) finish(f, t);
                }
                default -> { }
            }
        }
        put("pwr", powered());
        for (int i = 1; i <= 5; i++) put("l" + i, step == i);
        put("run", step != 0);
        put("pass", step == 0 && result == 1);
        put("fail", step == 0 && result == 2);
        put("bp", (int) Math.round(bp * 10));
        put("bc", step == 2 || step == 3 ? 35 : 0);
        put("mr", 90 + (int) (age / 20 % 10));
        put("leakg", (int) Math.round(leak * 100));
        put("cars", cars); put("resp", responded); put("force", force);
        text("line1", step == 0 ? (result == 1 ? "PASS" : result == 2 ? "FAIL" : "READY") : STEPS[step] + " " + secs + "/" + SECS[step] + "S");
        text("line2", loco == null ? "NO TRAIN" : cut(FleetData.get(world).unit(loco).name.toUpperCase(Locale.ROOT), 20));
        text("leakd", leak > 0 ? String.format(Locale.ROOT, "%.2f", leak) : "");
    }

    private void finish(FleetData f, List<EntityCoupleableRollingStock> t) {
        double k = 0;
        for (EntityCoupleableRollingStock c : t) { FleetData.Unit u = f.unit(c.getUUID()); Condition.work(u, WearData.get(world).peek(c.getUUID())); k += u.brakeK; }
        force = (int) Math.round(k / Math.max(1, t.size()) * 100);
        boolean cont = respAt.containsKey(t.get(t.size() - 1).getUUID());
        boolean ok = responded == t.size() && cont && leak <= 0.35 && force >= 85 && ctl.get("parkok") == 1;
        result = ok ? 1 : 2;
        report.add(String.format(Locale.ROOT, "BRAKE FORCE %d%% (min 85) - RESULT %s", force, ok ? "PASS" : "FAIL"));
        long now = world.getTotalWorldTime();
        for (EntityCoupleableRollingStock c : t) {
            FleetData.Unit u = f.unit(c.getUUID());
            boolean mine = respAt.containsKey(c.getUUID()) && u.brakeK >= 0.85f;
            if (ok || mine && leak <= 0.35) {
                u.brakeOk = true; u.brakeWork = false; u.brakeCert = now;
                u.note(world, "brake test PASSED");
                Jobs.machineDone(world, c.getUUID(), Jobs.BRAKE_TEST, "brake test rig");
            } else {
                u.brakeOk = false;
                u.note(world, "brake test FAILED");
                Jobs.raise(world, c.getUUID(), u.name, Jobs.REPAIR, Condition.B, u.depot, "failed the brake test");
            }
        }
        f.markDirty();
        Recorder.event(world, "Brake test " + (ok ? "PASS" : "FAIL") + ": " + f.unit(loco).name);
        if (!ok) AlarmManager.get(world).raise(world, "braketest:" + loco, AlarmManager.Cat.MACHINE, AlarmManager.Sev.MINOR, "BRAKE TEST FAILED: " + f.unit(loco).name + " - not fit to run", pos, false);
        world.playSound(null, pos, ok ? com.dogpound.railmap.ModSounds.BEEP_OK : com.dogpound.railmap.ModSounds.BEEP_ERROR, SoundCategory.BLOCKS, 0.8f, ok ? 1.5f : 0.6f);
        step = 0;
    }

    @Override protected void saveExtra(NBTTagCompound t) {
        if (loco != null) t.setUniqueId("loco", loco);
        t.setInteger("cars", cars); t.setInteger("resp", responded); t.setInteger("force", force); t.setInteger("result", result); t.setDouble("leak", leak);
        net.minecraft.nbt.NBTTagList l = new net.minecraft.nbt.NBTTagList();
        for (String s : report) l.appendTag(new net.minecraft.nbt.NBTTagString(s));
        t.setTag("report", l);
    }

    @Override protected void loadExtra(NBTTagCompound t) {
        loco = t.hasUniqueId("loco") ? t.getUniqueId("loco") : null;
        cars = t.getInteger("cars"); responded = t.getInteger("resp"); force = t.getInteger("force"); result = t.getInteger("result"); leak = t.getDouble("leak");
        report.clear();
        net.minecraft.nbt.NBTTagList l = t.getTagList("report", net.minecraftforge.common.util.Constants.NBT.TAG_STRING);
        for (int i = 0; i < l.tagCount(); i++) report.add(l.getStringTagAt(i));
        step = 0;    // a test never survives a reload
    }
}
