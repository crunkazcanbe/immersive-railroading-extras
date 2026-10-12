package com.dogpound.railmap.depot;

import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.server.WearData;
import com.dogpound.railmap.sim.SimData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;

import java.util.Locale;
import java.util.UUID;

/**
 * Bogie Drop / inspection pit (her ChatGPT list #56): changes a defective bogie (or its wheelsets) without lifting the
 * whole vehicle. Park it over the pit, pick the end (A / B) and BOGIE or WHEELSET, then LOCK (body supports and lock
 * pins), LOWER (the hydraulic table drops the bogie out), TRAVERSE (it rolls into the side bay - a defective bogie is
 * a real item with its serial and mileage), FIT (a serviceable bogie / two wheelsets from the bay or the Stores goes on
 * the table), RAISE, UNLOCK. Both ends done renews the wheels, bearings and brakes; one end halves their wear. Brake
 * work: it then needs a brake test. OVERHAUL rebuilds a defective bogie in the bay with a brake block set, two bearings
 * and a wheelset. Runs on mains; E-STOP freezes the table.
 */
public class TileBogieDrop extends DepotTile {
    private static final int REACH = 6;
    private static final String[] STAGES = {"READY - LOCK", "LOCKED - LOWER", "DOWN - TRAVERSE", "EMPTY - FIT", "NEW ON TABLE - RAISE", "FITTED - UNLOCK"};
    private UUID unit;
    private int stage, table, moving, done, overhaul;   // moving: +1 lowering, -1 raising; done: bit per end this visit
    private String serial = "";

    public TileBogieDrop() { super(new String[]{"bay"}, new String[]{"Bogie bay"}, new int[]{9}); }

    @Override public String wiredName() { return "Bogie drop"; }
    @Override protected boolean usesMains() { return true; }
    @Override protected long load() { return moving != 0 || overhaul > 0 ? 50 : 3; }
    @Override public String gearState() { return !powered() ? "NO 12 V" : STAGES[stage] + (unit == null ? "" : " · " + FleetData.get(world).unit(unit).name) + (mains.ok() ? "" : " · " + mains.what); }

    boolean wheelset() { return Math.floorMod(ctl.get("s_mode"), 2) == 1; }
    int end() { return Math.floorMod(ctl.get("s_end"), 2); }

    EntityRollingStock over() {
        EntityRollingStock best = null;
        double bd = REACH * REACH;
        for (EntityRollingStock s : cam72cam.mod.world.World.get(world).getEntities(EntityRollingStock.class)) {
            Vec3d p = s.getPosition();
            double d = pos.distanceSqToCenter(p.x, pos.getY() + 0.5, p.z);
            if (s.isDead() || Math.abs(p.y - pos.getY()) > 3 || d > bd) continue;
            if (s instanceof EntityMoveableRollingStock m && m.getCurrentSpeed() != null && Math.abs(m.getCurrentSpeed().metric()) > 0.5) continue;
            bd = d; best = s;
        }
        return best;
    }

    private ItemStack fromBayOrStores(ItemSpare.Spare p, int n) {
        int have = Stores.count(store(0), p);
        if (have >= n) {
            ItemStack first = Stores.first(store(0), p).copy();
            Stores.take(store(0), p, n);
            first.setCount(n);
            return first;
        }
        int[] need = new int[ItemSpare.Spare.values().length];
        need[p.ordinal()] = n;
        if (!Stores.shortOf(world, pos, need).isEmpty()) return ItemStack.EMPTY;
        Stores.take(world, pos, need);
        return ItemSpare.stack(p, n);
    }

    @Override
    protected String press(String id, EntityPlayer who) {
        FleetData f = FleetData.get(world);
        if (ctl.get("m_estop") == 1 && !id.equals("overhaul")) return "§cE-STOP is in";
        if (!mainsOk()) return "§cNo mains for the hydraulics (" + mains.what + ")";
        if (moving != 0) return "§7The table is moving";
        switch (id) {
            case "lock" -> {
                if (stage != 0) return "§7Not ready to lock";
                EntityRollingStock s = over();
                if (s == null) return "§cNo vehicle standing over the pit";
                if (!s.getUUID().equals(unit)) done = 0;
                unit = s.getUUID(); stage = 1;
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("machine.clunk"), SoundCategory.BLOCKS, 0.8f, 0.9f);
                return "§aBody supports set, lock pins in - " + f.unit(unit).name + " end " + (end() == 0 ? "A" : "B");
            }
            case "lower" -> { if (stage != 1) return "§7Lock first"; moving = 1; return "§eLowering the " + (wheelset() ? "wheelsets" : "bogie"); }
            case "traverse" -> {
                if (stage != 2) return "§7Lower the table first";
                FleetData.Unit u = f.unit(unit);
                if (!wheelset()) {
                    FleetData.Comp c = f.comp(u, end() == 0 ? FleetData.Kind.BOGIE_A : FleetData.Kind.BOGIE_B, world.getTotalWorldTime());
                    if (!Stores.put(store(0), ItemSpare.bogie(false, c.serial, c.life(u.km)))) return "§cThe bogie bay is full - clear it first";
                    serial = c.serial;
                }
                stage = 3;
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.LOOP_CONVEYOR, SoundCategory.BLOCKS, 0.5f, 1.0f);
                return wheelset() ? "§aOld wheelsets off to the scrap road" : "§aDefective bogie " + serial + " rolled into the bay";
            }
            case "fit" -> {
                if (stage != 3) return "§7Traverse the old one out first";
                ItemStack s = fromBayOrStores(wheelset() ? ItemSpare.Spare.WHEELSET : ItemSpare.Spare.BOGIE, wheelset() ? 2 : 1);
                if (s.isEmpty()) return "§cNo serviceable " + (wheelset() ? "wheelsets (2)" : "bogie") + " in the bay or the Stores";
                serial = s.hasTagCompound() ? s.getTagCompound().getString("serial") : "";
                ctl.set("newkm", s.hasTagCompound() ? (int) Math.round(s.getTagCompound().getDouble("km") * 10) : 0);
                stage = 4;
                return "§aNew " + (wheelset() ? "wheelsets" : "bogie " + (serial.isEmpty() ? "" : serial)) + " on the table";
            }
            case "raise" -> { if (stage != 4) return "§7Fit the new one first"; moving = -1; return "§eRaising"; }
            case "unlock" -> {
                if (stage != 5 && stage != 1) return "§7Finish the change (or don't lower) before unlocking";
                stage = 0;
                return "§aUnlocked - " + (unit == null ? "" : f.unit(unit).name) + " is free to move";
            }
            case "overhaul" -> {
                if (overhaul > 0) return "§7Already overhauling";
                if (Stores.count(store(0), ItemSpare.Spare.BOGIE_BAD) == 0) return "§cNo defective bogie in the bay";
                int[] need = new int[ItemSpare.Spare.values().length];
                need[ItemSpare.Spare.BRAKE_BLOCKS.ordinal()] = 1; need[ItemSpare.Spare.AXLE_BEARING.ordinal()] = 2; need[ItemSpare.Spare.WHEELSET.ordinal()] = 1;
                String sh = Stores.shortOf(world, pos, need);
                if (!sh.isEmpty()) return "§cOverhaul needs " + sh;
                Stores.take(world, pos, need);
                overhaul = 1;
                return "§aOverhaul started (brake blocks, bearings and a wheelset from the Stores)";
            }
            default -> { return null; }
        }
    }

    @Override
    protected String mechanical(String id, EntityPlayer who) {
        if (id.equals("m_estop")) { ctl.set(id, ctl.get(id) == 1 ? 0 : 1); if (ctl.get(id) == 1) moving = 0; return ctl.get(id) == 1 ? "§cE-STOP - table stopped" : "§aE-stop released"; }
        if ((id.equals("s_end") || id.equals("s_mode")) && stage != 0) return "§cChange the end / mode before locking";
        return null;
    }

    @Override
    protected void work() {
        if (age % 10 != 0) return;
        if (moving != 0 && powered() && mainsOk() && ctl.get("m_estop") == 0) {
            table = Math.max(0, Math.min(100, table + moving * 10));
            if (table == 100 && moving > 0) { moving = 0; stage = 2; world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("machine.jack"), SoundCategory.BLOCKS, 0.8f, 1.0f); }
            if (table == 0 && moving < 0) { moving = 0; stage = 5; fitted(); }
        }
        if (overhaul > 0 && powered() && mainsOk()) {
            if (++overhaul >= 40) {
                ItemStack bad = Stores.first(store(0), ItemSpare.Spare.BOGIE_BAD);
                if (!bad.isEmpty()) {
                    String s = bad.hasTagCompound() ? bad.getTagCompound().getString("serial") : FleetData.get(world).serial(FleetData.Kind.BOGIE_A);
                    double km = bad.hasTagCompound() ? bad.getTagCompound().getDouble("km") : 0;
                    Stores.take(store(0), ItemSpare.Spare.BOGIE_BAD, 1);
                    Stores.put(store(0), ItemSpare.bogie(true, s, km));
                    Recorder.event(world, "Bogie drop: bogie " + s + " overhauled");
                }
                overhaul = 0;
            }
        }
        FleetData.Unit u = unit == null ? null : FleetData.get(world).peek(unit);
        put("pwr", powered()); put("mainsl", mains.ok()); put("locked", stage >= 1); put("down", table >= 100);
        put("out", stage == 3); put("newin", stage >= 4); put("moving", moving != 0); put("ovh", overhaul > 0);
        put("table", table);
        put("load", stage == 1 || stage == 5 ? 0 : stage >= 2 && stage != 3 ? 85 : 0);
        put("nbay", Stores.count(store(0), ItemSpare.Spare.BOGIE));
        put("nbad", Stores.count(store(0), ItemSpare.Spare.BOGIE_BAD));
        text("line1", u == null ? (over() == null ? "NO VEHICLE" : "VEHICLE OVER PIT") : cut(u.name.toUpperCase(Locale.ROOT), 20));
        text("line2", moving != 0 ? (moving > 0 ? "LOWERING " : "RAISING ") + table + "%" : overhaul > 0 ? "OVERHAUL " + overhaul * 100 / 40 + "%" : STAGES[stage]);
        text("serial", serial.isEmpty() ? "" : serial);
    }

    private void fitted() {
        FleetData f = FleetData.get(world);
        FleetData.Unit u = f.unit(unit);
        WearData wd = WearData.get(world);
        WearData.Record r = wd.of(unit);
        int e = end();
        done |= 1 << e;
        boolean both = done == 3;
        if (wheelset()) {
            f.replace(world, u, FleetData.Kind.WHEELS, null, 0);
            r.wear[Condition.W] = both ? 0 : r.wear[Condition.W] * 0.5f;
            if (both) { SimData.get(world).of(unit).flatMm = 0; SimData.get(world).markDirty(); AlarmManager.get(world).clear("flat:" + unit); Jobs.machineDone(world, unit, Jobs.WHEELS, "bogie drop"); }
        } else {
            f.replace(world, u, e == 0 ? FleetData.Kind.BOGIE_A : FleetData.Kind.BOGIE_B, serial.isEmpty() ? null : serial, ctl.get("newkm") / 10.0);
            for (int p : new int[]{Condition.W, Condition.B, Condition.BR}) r.wear[p] = both ? 0 : r.wear[p] * 0.5f;
            if (DepotConfig.brakeTestAfterBrakeWork) { u.brakeWork = true; u.brakeOk = false; }
            if (both) {
                SimData.get(world).of(unit).flatMm = 0; SimData.get(world).markDirty();
                AlarmManager.get(world).clear("flat:" + unit);
                Jobs.machineDone(world, unit, Jobs.BOGIE, "bogie drop");
            }
        }
        for (int p = 0; p < 5; p++) {
            if (r.wear[p] < 80) r.warned &= ~(1 << p);
            u.level[p] = Condition.level(r.wear[p]);
            if (u.level[p] < Condition.CRITICAL) AlarmManager.get(world).clear("cond:" + unit + ":" + p);
        }
        u.trend.clear();
        wd.markDirty();
        f.markDirty();
        Recorder.event(world, "Bogie drop: " + u.name + " end " + (e == 0 ? "A" : "B") + " " + (wheelset() ? "wheelsets changed" : "bogie changed (" + serial + ")"));
        world.playSound(null, pos, com.dogpound.railmap.ModSounds.BEEP_OK, SoundCategory.BLOCKS, 0.6f, 1.0f);
    }

    @Override protected void saveExtra(NBTTagCompound t) {
        if (unit != null) t.setUniqueId("unit", unit);
        t.setInteger("stage", stage); t.setInteger("table", table); t.setInteger("moving", moving); t.setInteger("done", done); t.setInteger("ovh", overhaul); t.setString("serial", serial);
    }

    @Override protected void loadExtra(NBTTagCompound t) {
        unit = t.hasUniqueId("unit") ? t.getUniqueId("unit") : null;
        stage = t.getInteger("stage"); table = t.getInteger("table"); moving = t.getInteger("moving"); done = t.getInteger("done"); overhaul = t.getInteger("ovh"); serial = t.getString("serial");
    }
}
