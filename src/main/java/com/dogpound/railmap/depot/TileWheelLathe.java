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
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;

import java.util.Locale;
import java.util.UUID;

/**
 * Underfloor Wheel Lathe (her ChatGPT list #55): reprofiles a vehicle's wheels where it stands instead of changing the
 * wheelset. Park the vehicle over it, CLAMP (rail sections drop, drive rollers and axlebox clamps come up), MEASURE
 * (tread diameter, flange thickness and height, flat length), then CUT with the coolant on: the tool takes off what
 * the wear and flats need (a flat of length L needs about L^2/2D of the diameter), one axle or all four, at the
 * spindle's pace on mains power. Turning clears the flats and the wheel wear, and costs diameter: under the scrap
 * limit it refuses, and the wheelset has to be changed at the Bogie Drop. Closes the vehicle's WHEELS job card.
 */
public class TileWheelLathe extends DepotTile {
    private static final int REACH = 5;
    private UUID unit;
    private int phase;            // 0 free, 1 clamped, 2 measured, 3 cutting, 4 done
    private double removeMm, cutMm, flangeMm, heightMm;
    private int axlesDone;

    @Override public String wiredName() { return "Wheel lathe"; }
    @Override protected boolean usesMains() { return true; }
    @Override protected long load() { return phase == 3 && ctl.get("m_estop") == 0 ? 60 : 4; }

    @Override public String gearState() {
        return !powered() ? "NO 12 V" : new String[]{"free", "clamped", "measured", "cutting", "done"}[phase] + (unit == null ? "" : " · " + FleetData.get(world).unit(unit).name)
                + (usesMains() && !mains.ok() ? " · " + mains.what : "");
    }

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

    int axles() { int s = Math.floorMod(ctl.get("s_axle"), 5); return s == 4 ? 4 : 1; }

    @Override
    protected String press(String id, EntityPlayer who) {
        FleetData f = FleetData.get(world);
        switch (id) {
            case "clamp" -> {
                if (phase != 0) return "§7Already clamped";
                EntityRollingStock s = over();
                if (s == null) return "§cNo vehicle standing over the lathe";
                if (!mainsOk()) return "§cNo mains - the clamps are hydraulic (" + mains.what + ")";
                unit = s.getUUID(); phase = 1; axlesDone = 0;
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("machine.jack"), SoundCategory.BLOCKS, 0.8f, 1.0f);
                return "§aClamped " + f.unit(unit).name + " - drive rollers up";
            }
            case "unclamp" -> {
                if (phase == 3) return "§cStop the cut first (E-STOP)";
                if (phase == 0) return "§7Nothing clamped";
                phase = 0;
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("machine.jack"), SoundCategory.BLOCKS, 0.8f, 0.9f);
                return "§aUnclamped - the vehicle can move";
            }
            case "measure" -> {
                if (phase < 1 || unit == null) return "§cClamp a vehicle first";
                WearData.Record r = WearData.get(world).of(unit);
                SimData.Car c = SimData.get(world).of(unit);
                FleetData.Comp ws = f.comp(f.unit(unit), FleetData.Kind.WHEELS, world.getTotalWorldTime());
                double dia = ws.diameter <= 0 ? DepotConfig.wheelNewMm : ws.diameter;
                double wearMm = r.wear[Condition.W] * 0.08;
                removeMm = Math.max(1.0, 1.5 + wearMm + c.flatMm * (double) c.flatMm / (2 * dia));
                flangeMm = 32.5 - r.wear[Condition.W] * 0.07;
                heightMm = 28 + r.wear[Condition.W] * 0.05;
                phase = Math.max(phase, 2);
                text("dia", String.format(Locale.ROOT, "%.0f", dia));
                return String.format(Locale.ROOT, "§aMeasured: tread %.0f mm, flange %.1f mm thick / %.1f mm high, flat %.0f mm - cut %.1f mm off the diameter",
                        dia, flangeMm, heightMm, c.flatMm, removeMm);
            }
            case "cut" -> {
                if (phase < 2) return "§cMEASURE first";
                if (phase == 3) return "§7Already cutting";
                if (ctl.get("m_estop") == 1) return "§cE-STOP is in";
                if (ctl.get("s_cool") == 0) return "§cCoolant OFF - the tool would burn; turn COOLANT on";
                if (!mainsOk()) return "§cNo mains for the spindle (" + mains.what + ")";
                FleetData.Comp ws = f.comp(f.unit(unit), FleetData.Kind.WHEELS, world.getTotalWorldTime());
                if (ws.diameter - removeMm < DepotConfig.wheelScrapMm)
                    return String.format(Locale.ROOT, "§cBELOW SCRAP: %.0f - %.1f mm < %d mm - change the wheelset at a Bogie Drop", ws.diameter, removeMm, DepotConfig.wheelScrapMm);
                phase = 3; cutMm = 0;
                return "§aSpindle running - cutting " + (axles() == 4 ? "all axles" : "axle " + (Math.floorMod(ctl.get("s_axle"), 5) + 1));
            }
            default -> { return null; }
        }
    }

    @Override
    protected String mechanical(String id, EntityPlayer who) {
        if (id.equals("m_estop")) {
            ctl.set(id, ctl.get(id) == 1 ? 0 : 1);
            if (ctl.get(id) == 1 && phase == 3) { phase = 2; Recorder.event(world, "Wheel lathe: E-STOP during a cut"); }
            return ctl.get(id) == 1 ? "§cE-STOP - spindle stopped" : "§aE-stop released";
        }
        return null;
    }

    @Override
    protected void work() {
        if (age % 10 != 0) return;
        FleetData f = FleetData.get(world);
        if (phase > 0 && phase != 3 && (unit == null || over() == null || !unit.equals(over().getUUID()))) { phase = 0; unit = null; }
        if (phase == 3) {
            if (!powered() || !mainsOk() || ctl.get("m_estop") == 1) phase = 2;
            else {
                double total = removeMm * axles();
                cutMm += 0.25;                                  // 0.5 mm a second
                if (age % 40 == 0) world.playSound(null, pos, com.dogpound.railmap.ModSounds.LOOP_CRUSHER, SoundCategory.BLOCKS, 0.35f, 1.0f);
                if (cutMm >= total) finish(f);
            }
        }
        double total = Math.max(0.01, removeMm * axles());
        put("pwr", powered());
        put("mainsl", mains.ok());
        put("clampl", phase >= 1);
        put("cutting", phase == 3);
        put("ready", phase == 2 && mainsOk());
        put("fault", phase > 0 && !mainsOk());
        put("rpm", phase == 3 ? 62 : 0);
        put("feed", phase == 3 ? 50 : 0);
        put("prog", phase == 3 ? (int) Math.round(cutMm / total * 100) : phase == 4 ? 100 : 0);
        put("cool", ctl.get("s_cool") == 1 && phase == 3);
        FleetData.Unit u = unit == null ? null : f.peek(unit);
        FleetData.Comp ws = u == null ? null : u.comps[FleetData.Kind.WHEELS.ordinal()];
        text("line1", u == null ? (over() == null ? "NO VEHICLE" : "READY - CLAMP") : cut(u.name.toUpperCase(Locale.ROOT), 20));
        text("line2", new String[]{"FREE", "CLAMPED - MEASURE", String.format(Locale.ROOT, "CUT %.1f MM", removeMm), String.format(Locale.ROOT, "CUTTING %.1f/%.1f", cutMm, total), "DONE - UNCLAMP"}[phase]);
        text("dia", ws == null ? "" : String.format(Locale.ROOT, "%.0f", ws.diameter));
        text("flg", phase >= 2 ? String.format(Locale.ROOT, "%.1f", flangeMm) : "");
    }

    private void finish(FleetData f) {
        FleetData.Unit u = f.unit(unit);
        FleetData.Comp ws = f.comp(u, FleetData.Kind.WHEELS, world.getTotalWorldTime());
        ws.diameter -= (float) removeMm;
        ws.repairs++;
        WearData wd = WearData.get(world);
        WearData.Record r = wd.of(unit);
        r.wear[Condition.W] = axles() == 4 ? 0 : r.wear[Condition.W] * 0.75f;    // one axle of four
        if (r.wear[Condition.W] < 80) r.warned &= ~(1 << Condition.W);
        if (axles() == 4) {
            SimData.Car c = SimData.get(world).of(unit);
            c.flatMm = 0;
            SimData.get(world).markDirty();
            AlarmManager.get(world).clear("flat:" + unit);
        }
        u.level[Condition.W] = Condition.level(r.wear[Condition.W]);
        if (u.level[Condition.W] < Condition.CRITICAL) AlarmManager.get(world).clear("cond:" + unit + ":" + Condition.W);
        wd.markDirty();
        u.note(world, String.format(Locale.ROOT, "wheels reprofiled on the lathe: %.1f mm off, now %.0f mm (turning %d)", removeMm, ws.diameter, ws.repairs));
        if (axles() == 4) Jobs.machineDone(world, unit, Jobs.WHEELS, "wheel lathe");
        Recorder.event(world, "Wheel lathe: " + u.name + " reprofiled to " + Math.round(ws.diameter) + " mm");
        f.markDirty();
        phase = 4;
        world.playSound(null, pos, com.dogpound.railmap.ModSounds.BEEP_OK, SoundCategory.BLOCKS, 0.6f, 1.0f);
    }

    @Override protected void saveExtra(NBTTagCompound t) {
        if (unit != null) t.setUniqueId("unit", unit);
        t.setInteger("phase", phase); t.setDouble("rm", removeMm); t.setDouble("cut", cutMm); t.setDouble("fl", flangeMm); t.setDouble("fh", heightMm);
    }

    @Override protected void loadExtra(NBTTagCompound t) {
        unit = t.hasUniqueId("unit") ? t.getUniqueId("unit") : null;
        phase = t.getInteger("phase"); removeMm = t.getDouble("rm"); cutMm = t.getDouble("cut"); flangeMm = t.getDouble("fl"); heightMm = t.getDouble("fh");
    }
}
