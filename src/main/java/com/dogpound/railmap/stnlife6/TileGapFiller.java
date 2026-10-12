package com.dogpound.railmap.stnlife6;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;

import java.util.ArrayList;
import java.util.List;

/**
 * Platform gap filler (batch 6c #15): a steel plate set into the platform edge that slides out toward the track (south of the model: place it standing on the platform facing the track)
 * over the gap when a train is stopped alongside, and back in before it moves. Walk on it - it is solid. MODE AUTO /
 * OUT (hold out) / IN (locked in). Retracting with someone standing on the plate stops it (OBSTRUCTED, retries in 3 s).
 * Interlocks: proved-in / proved-out limit switches (the lamps; the boarding lift's track gate needs it proved out);
 * a train that moves off with the plate not proved in is a CRITICAL alarm, and with config gapStrike the plate is
 * struck (FAULT until RESET with the track clear). 12 V + mains (drive motor).
 */
public class TileGapFiller extends MovingTile {
    double ext;
    boolean fault, obstructed, trainAlarm;
    private int cycles, retry;

    public boolean out() { return ext >= 0.99; }
    public boolean in() { return ext <= 0.01; }
    @Override public int level() { return ext < 0.25 ? 0 : ext < 0.75 ? 1 : 2; }

    @Override
    public List<double[]> boxes() {
        List<double[]> b = new ArrayList<>();
        b.add(new double[]{0, 0, 0, 16, 2, 12});                              // housing (north = platform side, it faces you)
        b.add(new double[]{0, 0, 0, 5, 10, 5});                               // control cabinet
        if (ext > 0.01) b.add(new double[]{0, 0, 12, 16, 1.5, 12 + 18 * ext});  // plate out over the gap (south, up to 14 px past the edge)
        return b;
    }

    @Override public String wiredName() { return "Gap filler " + pos.getX() + "," + pos.getZ(); }
    @Override public String gearState() { return !live() ? "NO SUPPLY - plate stays where it is" : (out() ? "OUT (proved)" : in() ? "IN (proved)" : "MOVING") + (fault ? " · FAULT" : "") + (obstructed ? " · OBSTRUCTED" : "") + " · " + cycles + " cycles"; }
    @Override protected long load() { return in() || out() ? 4 : 20; }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (id.startsWith("k_") || id.startsWith("s_")) return null;
        String d = dead();
        if (d != null) return d;
        if (id.equals("reset")) {
            if (TrainNear.at(world, pos, 4)[0] == 1) return "§cNot with a train moving alongside";
            fault = false; obstructed = false;
            return "§aGap filler reset";
        }
        return null;
    }

    boolean onPlate() {
        if (ext < 0.05) return false;
        AxisAlignedBB plate = BlockLiftDevice.rotatedBox(world.getBlockState(pos), 0, 0, 12, 16, 4, 12 + 18 * ext).offset(pos);
        for (EntityLivingBase e : world.getEntitiesWithinAABB(EntityLivingBase.class, plate)) if (!e.isRiding()) return true;
        return false;
    }

    @Override
    protected void work() {
        double[] t = TrainNear.at(world, pos, 4);
        int mode = ctl.get("s_mode");                                  // 0 AUTO, 1 OUT, 2 IN
        boolean want = !fault && (mode == 1 || (mode == 0 && t[0] == 2));
        double was = ext;
        if (live() && !fault) {
            if (want && ext < 1) ext = Math.min(1, ext + 0.05);
            else if (!want && ext > 0) {
                if (onPlate()) {
                    if (!obstructed) { obstructed = true; Recorder.event(world, wiredName() + ": OBSTRUCTED - someone on the plate"); }
                    retry = 60;
                } else if (--retry <= 0) { obstructed = false; ext = Math.max(0, ext - 0.05); }
            }
        }
        if (ext != was) {
            if (age % 2 == 0 || in() || out()) dirty();
            if (in() || out()) { if (out()) cycles++; world.playSound(null, pos, com.dogpound.railmap.ModSounds.RELAY, SoundCategory.BLOCKS, 0.5f, 1.0f); }
        }
        if (age % 10 != 3) return;
        String ak = "gapfill:" + pos.toLong();
        boolean bad = t[0] == 1 && !in();
        if (bad && !trainAlarm) {
            trainAlarm = true;
            if (LifeConfig.gapStrike) fault = true;
            AlarmManager.get(world).raise(world, ak, AlarmManager.Cat.INCIDENT, AlarmManager.Sev.CRITICAL, "GAP FILLER NOT RETRACTED - TRAIN MOVING at " + pos.getX() + "," + pos.getZ() + (fault ? " - plate struck" : ""), pos, true);
            Recorder.event(world, wiredName() + ": train moved with the plate out");
        } else if (!bad && trainAlarm && !fault) { trainAlarm = false; AlarmManager.get(world).clear(ak); }
        put("l_ext", out());
        put("l_ret", in());
        put("l_moving", !in() && !out());
        put("l_obst", obstructed);
        put("l_fault", fault);
        put("l_train", t[0] == 2);
        text("msg", fault ? "STRUCK - RESET" : obstructed ? "OBSTRUCTED" : out() ? "PLATE OUT" : in() ? "PLATE IN" : "MOVING");
        text("cnt", "CYCLES " + cycles);
    }

    @Override protected void write(NBTTagCompound t) { t.setDouble("ext", ext); t.setBoolean("fault", fault); t.setBoolean("obst", obstructed); t.setBoolean("ta", trainAlarm); t.setInteger("cyc", cycles); }
    @Override protected void read(NBTTagCompound t) { ext = t.getDouble("ext"); fault = t.getBoolean("fault"); obstructed = t.getBoolean("obst"); trainAlarm = t.getBoolean("ta"); cycles = t.getInteger("cyc"); }
}
