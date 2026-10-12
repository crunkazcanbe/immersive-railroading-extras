package com.dogpound.railmap.stnlife6;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.stn.StnUtil;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Accessibility boarding lift (batch 6c #15): a platform lift that raises a wheelchair user (anyone) from the platform
 * to the train's floor. Platform side (north, its controls - place it from the platform) and track side (south) gates, handrails, a deck that travels 0-12 px.
 * AUTO mode rises to the floor of the train stopped alongside (rail top + LifeConfig.trainFloor, TRIM -2..+2 px);
 * MANUAL rises the full 12 px. Interlocks: UP/DOWN need the STAFF key, the gates shut and nobody past the 2-person
 * rated load; the track-side GATE opens only at the top with a train STOPPED alongside and every gap filler within 3
 * blocks proved out (or at the bottom, to get on); E-STOP latches everything until reset; a train that moves off with
 * the gate open is a MAJOR alarm. No power: HAND LOWER (brake release key) lets it down slowly. 12 V + mains (motor).
 */
public class TileBoardingLift extends MovingTile {
    static final double SPEED = 0.15;           // px per tick
    double deck, target;
    boolean gate, fault;
    private int trips;
    private String msg = "";
    private boolean gateAlarm;

    public double top() { return deck + 2; }
    @Override public int level() { return (int) Math.round(deck / 3) + (gate ? 5 : 0); }   // 5 deck steps x gates shut / open

    @Override
    public List<double[]> boxes() {
        List<double[]> b = new ArrayList<>();
        b.add(new double[]{1, 0, 1, 15, top(), 15});                    // deck (+ scissor pack under it)
        b.add(new double[]{0, 0, 0, 1, 16, 16});                        // handrail sides
        b.add(new double[]{15, 0, 0, 16, 16, 16});
        if (!gate) b.add(new double[]{1, top(), 15, 15, top() + 15, 16});                  // track-side gate (south: the model faces the platform)
        if (!gate || deck >= 0.5) b.add(new double[]{1, top(), 0, 9.5, top() + 15, 1});   // platform gate: open only at the bottom
        b.add(new double[]{9.5, 0, 0, 15, 16, 1.4});                                       // control post
        return b;
    }

    @Override public String wiredName() { return "Boarding lift " + pos.getX() + "," + pos.getZ(); }
    @Override public String gearState() { return !live() ? "NO SUPPLY" + (ctl.get("k_hand") == 1 ? " - hand lowering" : "") : String.format(Locale.ROOT, "deck +%.2f m%s%s · %d trips", deck / 16, gate ? " · GATE OPEN" : "", fault ? " · FAULT" : "", trips); }
    @Override protected long load() { return deck != target ? 40 : 4; }

    boolean staff() { return ctl.get("k_staff") == 1; }
    boolean auto() { return ctl.get("s_mode") == 0; }
    double trim() { return ctl.get("s_trim") - 2; }

    int riders() { return world.getEntitiesWithinAABB(EntityLivingBase.class, new AxisAlignedBB(pos).grow(-0.05, 0, -0.05).expand(0, 1.5, 0)).size(); }

    /** every gap filler within 3 blocks proved out (or none) */
    boolean gapOk() { for (TileGapFiller g : StnUtil.near(world, TileGapFiller.class, pos, 3)) if (!g.out()) return false; return true; }

    /** train floor height above the lift base in px, or NaN */
    double trainPx() {
        double[] t = TrainNear.at(world, pos, 4);
        if (t[0] != 2) return Double.NaN;
        return Math.max(0, Math.min(12, ((t[1] + LifeConfig.trainFloor) - pos.getY()) * 16 - 2 + trim()));
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (id.equals("m_estop")) { ctl.set(id, ctl.get(id) == 1 ? 0 : 1); target = deck; if (ctl.get(id) == 1) { fault = true; msg = "E-STOP"; Recorder.event(world, wiredName() + ": EMERGENCY STOP by " + who(who)); } return ctl.get(id) == 1 ? "§cEMERGENCY STOP - latched" : "§eE-stop released - press RESET"; }
        if (id.startsWith("k_") || id.startsWith("s_")) return null;
        String d = dead();
        if (d != null) return d;
        switch (id) {
            case "reset" -> {
                if (ctl.get("m_estop") == 1) return "§cRelease the E-STOP first";
                fault = false; msg = ""; return "§aLift reset";
            }
            case "up" -> {
                if (!staff()) return "§cSTAFF key off - lift operated by staff";
                if (fault) return "§cLift in FAULT - RESET";
                if (gate) return "§cClose the gates first";
                if (riders() > 2) { msg = "OVERLOAD"; return "§cOVERLOAD - rated for 2 persons"; }
                double t = auto() ? trainPx() : 12 + trim();
                if (Double.isNaN(t)) return "§cAUTO: no train stopped alongside";
                target = Math.max(0, Math.min(12, t));
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.PSD_MOVE, SoundCategory.BLOCKS, 0.6f, 0.8f);
                return String.format(Locale.ROOT, "§aRising to +%.2f m", target / 16);
            }
            case "down" -> {
                if (!staff()) return "§cSTAFF key off - lift operated by staff";
                if (fault) return "§cLift in FAULT - RESET";
                if (gate) return "§cClose the gates first";
                target = 0;
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.PSD_MOVE, SoundCategory.BLOCKS, 0.6f, 0.7f);
                return "§aLowering";
            }
            case "gate" -> {
                if (gate) { gate = false; world.playSound(null, pos, com.dogpound.railmap.ModSounds.DOOR_CLOSE, SoundCategory.BLOCKS, 0.6f, 1.2f); return "§eGates shut"; }
                if (deck != target) return "§cNot while moving";
                if (deck > 0.5) {
                    double tp = trainPx();
                    if (Double.isNaN(tp)) return "§cInterlock: no train STOPPED alongside - the track gate stays shut";
                    if (Math.abs(tp - deck) > 1.5) return "§cInterlock: deck not level with the train floor (" + String.format(Locale.ROOT, "%.1f px off", tp - deck) + ")";
                    if (!gapOk()) return "§cInterlock: gap filler not proved out";
                }
                gate = true;
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.DOOR_OPEN, SoundCategory.BLOCKS, 0.6f, 1.2f);
                return deck > 0.5 ? "§aTrack gate open - board the train" : "§aPlatform gate open - step on";
            }
            case "call" -> {
                if (gate || fault) return "§7The lift is in use";
                target = 0;
                return "§aLift called to the platform";
            }
            default -> { return null; }
        }
    }

    @Override
    protected void work() {
        double was = deck;
        boolean powered = live() && ctl.get("m_estop") == 0 && !fault;
        if (ctl.get("k_hand") == 1 && !powered && deck > 0) target = 0;                     // brake release: lowers by gravity
        double sp = powered ? SPEED : ctl.get("k_hand") == 1 ? SPEED / 3 : 0;
        if (gate) sp = 0;
        if (deck < target) deck = Math.min(target, deck + sp);
        else if (deck > target) deck = Math.max(target, deck - sp);
        if (deck != was) {
            if (age % 2 == 0 || deck == target) dirty();
            if (deck == target) { trips++; world.playSound(null, pos, com.dogpound.railmap.ModSounds.DOOR_CHIME, SoundCategory.BLOCKS, 0.5f, 1.0f); }
        }
        if (age % 10 != 1) return;
        double[] t = TrainNear.at(world, pos, 4);
        boolean moving = t[0] == 1;
        String ak = "liftgate:" + pos.toLong();
        if (gate && deck > 0.5 && moving && !gateAlarm) { gateAlarm = true; AlarmManager.get(world).raise(world, ak, AlarmManager.Cat.INCIDENT, AlarmManager.Sev.MAJOR, "BOARDING LIFT GATE OPEN - TRAIN MOVING at " + pos.getX() + "," + pos.getZ(), pos, true); Recorder.event(world, wiredName() + ": train moved with the track gate open"); }
        else if (gateAlarm && (!gate || !moving)) { gateAlarm = false; AlarmManager.get(world).clear(ak); }
        int r = riders();
        double tp = trainPx();
        put("l_up", deck < target);
        put("l_dn", deck > target);
        put("l_gate", gate);
        put("l_train", t[0] == 2);
        put("l_over", r > 2);
        put("l_gap", gapOk());
        put("l_fault", fault || ctl.get("m_estop") == 1);
        put("l_ready", !Double.isNaN(tp) && Math.abs(tp - deck) <= 1.5 && deck > 0.5);
        put("g_ht", (int) Math.round(deck));
        text("ht", String.format(Locale.ROOT, "DECK +%.2f M", deck / 16));
        text("tr", Double.isNaN(tp) ? t[0] == 1 ? "TRAIN MOVING" : "NO TRAIN" : String.format(Locale.ROOT, "TRAIN +%.2f M", tp / 16));
        text("msg", !msg.isEmpty() ? msg : r > 2 ? "OVERLOAD" : deck != target ? (deck < target ? "RISING" : "LOWERING") : gate ? "GATE OPEN" : !staff() ? "STAFF KEY OFF" : "READY " + r + " ON");
    }

    @Override protected void write(NBTTagCompound t) { t.setDouble("deck", deck); t.setDouble("tgt", target); t.setBoolean("gate", gate); t.setBoolean("fault", fault); t.setInteger("trips", trips); t.setString("msg", msg); t.setBoolean("ga", gateAlarm); }
    @Override protected void read(NBTTagCompound t) { deck = t.getDouble("deck"); target = t.getDouble("tgt"); gate = t.getBoolean("gate"); fault = t.getBoolean("fault"); trips = t.getInteger("trips"); msg = t.getString("msg"); gateAlarm = t.getBoolean("ga"); }
}
