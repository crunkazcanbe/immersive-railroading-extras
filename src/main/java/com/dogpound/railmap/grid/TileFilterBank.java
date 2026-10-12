package com.dogpound.railmap.grid;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.safety.SafetyTile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;

import java.util.Locale;
import java.util.Map;

/**
 * Harmonic Filter Bank (her "next 48" list #20): a tuned LC filter (capacitor cans + an iron-core reactor) in its own
 * cubicle with a vacuum contactor, placed touching the bus it filters. Harmonics.tick puts it into the network's
 * impedance at every harmonic, so closing it really pulls its harmonic down - and shifts the resonance.
 * <ul>
 *   <li>TUNING: 5th (4.7, detuned below the 5th like real ones), 7th (6.7), 11th (10.7), 13th (12.7), HIGH-PASS (17, damped);</li>
 *   <li>SIZE: 250 / 500 / 1000 / 2000 kvar;</li>
 *   <li>CLOSE / OPEN (key LOCAL, or remote from the console with the key in REMOTE), RESET after a trip; it refuses to
 *       reclose for 10 s after opening while the capacitors discharge;</li>
 *   <li>its own protection: harmonic overload (over 135 % of its harmonic rating for 10 s), over-temperature, and
 *       capacitor unbalance - overloads age the cans, losing capacitance detunes the filter UP towards the harmonic it
 *       should sit below, and below 92 % an element has failed: it trips. REPLACE CANS (open for 30 s) renews them.</li>
 * </ul>
 * Placed OPEN, so an old grid is unchanged until someone closes it. 12 V for the contactor and relays.
 */
public class TileFilterBank extends SafetyTile {
    public static final double[] TUNE = {4.7, 6.7, 10.7, 12.7, 17};
    public static final String[] TUNE_NAME = {"5TH", "7TH", "11TH", "13TH", "HP"};
    public static final int[] SIZE = {250, 500, 1000, 2000};
    private boolean closed;
    private String trip = "";
    private double cap = 1, temp = 25;
    private int overSecs;
    private long openedAt = -10000;

    public String id() { return "HF" + Math.floorMod(pos.getX() * 17 + pos.getZ() * 3, 100); }
    @Override public String wiredName() { return "Harmonic filter bank " + id(); }
    public boolean closed() { return closed; }
    double tuned() { return TUNE[Math.min(4, ctl.get("s_tune"))]; }
    int kvar() { return SIZE[Math.min(3, ctl.get("s_size"))]; }

    @Override public String gearState() {
        Harmonics.Filter f = Harmonics.filter(world, pos.toLong());
        return !trip.isEmpty() ? "TRIPPED: " + trip : !closed ? "open" : String.format(Locale.ROOT, "IN SERVICE %d kvar tuned h%.2f · duty %.0f%% · %.0f °C · C %.1f%%", kvar(), f.actualTune(), f.ampsPct, temp, cap * 100);
    }
    @Override public int wiredLamp() { return !powered() || !trip.isEmpty() ? 3 : closed ? 1 : 2; }
    @Override public void gearInfo(Map<String, Object> m) { Harmonics.Filter f = Harmonics.filter(world, pos.toLong()); m.put("closed", closed); m.put("kvar", kvar()); m.put("tuned", tuned()); m.put("actualTune", Math.round(f.actualTune() * 100) / 100.0); m.put("dutyPct", Math.round(f.ampsPct)); m.put("tempC", Math.round(temp)); m.put("capacitancePct", Math.round(cap * 1000) / 10.0); m.put("trip", trip); }

    @Override
    protected void serverTick() {
        if (age % 20 != 7) return;
        Harmonics.Filter f = Harmonics.filter(world, pos.toLong());
        f.seen = world.getTotalWorldTime();
        f.kvar = kvar(); f.tuned = tuned(); f.cap = cap; f.highPass = ctl.get("s_tune") == 4;
        GridData.Net n = PowerQuality.measured(GridData.get(world), pos.toLong());
        boolean live = n != null && n.level != null && n.level.ac && n.problem.isEmpty();
        if (closed && !live) { closed = false; openedAt = world.getTotalWorldTime(); Recorder.event(world, "FILTER " + id() + ": contactor dropped out - bus dead"); }
        if (closed && !powered()) { closed = false; openedAt = world.getTotalWorldTime(); Recorder.event(world, "FILTER " + id() + ": contactor dropped out - lost 12 V"); }
        f.closed = closed;
        double duty = closed ? f.ampsPct : 0;
        // thermal: I^2 heating against natural cooling, ~1 min time constant
        double rise = 55 * Math.pow(duty / 100, 2);
        temp += (25 + rise - temp) * 0.05;
        if (closed && duty > 100) cap = Math.max(0.7, cap - 0.0004 * (duty / 100) * (temp > 70 ? 2 : 1));   // overload ages the cans
        overSecs = closed && duty > 135 ? overSecs + 1 : 0;
        if (closed && overSecs >= 10) trip("HARMONIC OVERLOAD " + Math.round(duty) + "% (h" + (int) f.thIh + ")");
        else if (closed && temp > 100) trip(String.format(Locale.ROOT, "OVER-TEMPERATURE %.0f °C", temp));
        else if (closed && cap < 0.92) trip(String.format(Locale.ROOT, "CAPACITOR UNBALANCE - element failed (C %.1f%%)", cap * 100));
        put("closed", closed);
        put("openl", !closed);
        put("trip", !trip.isEmpty());
        put("ovl", closed && duty > 100);
        put("detune", cap < 0.97);
        put("pwr", powered());
        put("live", live);
        put("g_ih", (int) Math.min(150, Math.round(duty)));
        put("g_temp", (int) Math.min(120, Math.round(temp)));
        text("kvar", kvar() + " KVAR " + TUNE_NAME[Math.min(4, ctl.get("s_tune"))]);
        text("tune", String.format(Locale.ROOT, "TUNED H%.2f NOW H%.2f", tuned(), f.actualTune()));
        text("ih", closed ? String.format(Locale.ROOT, "DUTY %.0f%% WORST H%d", duty, (int) f.thIh) : "OUT OF SERVICE");
        text("cap", String.format(Locale.ROOT, "CAP %.1f%% %.0f C", cap * 100, temp));
        text("state", !trip.isEmpty() ? "TRIP " + trip : closed ? "IN SERVICE" : n == null ? "NO BUS TOUCHING" : "OPEN" + (discharging() ? " DISCHARGING" : ""));
    }

    /** next-48 #44: a chaos scenario trips it (and resets it afterwards) */
    public void chaosTrip() { if (closed) trip("CHAOS TEST - contactor forced open"); }
    public void chaosReset() { if (trip.startsWith("CHAOS")) { trip = ""; com.dogpound.railmap.alarm.AlarmManager.get(world).clear("hf:" + pos.toLong()); } }

    private boolean discharging() { return world.getTotalWorldTime() - openedAt < 200; }

    private void trip(String why) {
        closed = false;
        trip = why;
        openedAt = world.getTotalWorldTime();
        Harmonics.filter(world, pos.toLong()).closed = false;
        world.playSound(null, pos, com.dogpound.railmap.ModSounds.CONTACTOR, SoundCategory.BLOCKS, 0.9f, 1.0f);
        Recorder.event(world, "FILTER " + id() + " TRIPPED: " + why);
        AlarmManager.get(world).raise(world, "hf:" + pos.toLong(), AlarmManager.Cat.POWER, AlarmManager.Sev.MAJOR, "Harmonic filter " + id() + " at " + pos.getX() + "," + pos.getZ() + " TRIPPED: " + why, pos, true);
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        boolean remote = who == null;
        switch (id) {
            case "test": return lampTest();
            case "s_tune": case "s_size":
                if (closed) return "§cOpen the filter before changing its " + (id.equals("s_tune") ? "tuning taps" : "steps");
                return null;
            case "k_lr": return null;
            default: break;
        }
        if (remote && ctl.get("k_lr") == 0) return "§cKey in LOCAL - turn it to REMOTE for console control";
        if (!remote && ctl.get("k_lr") == 1 && !id.equals("recan")) return "§cKey in REMOTE - the console has control";
        switch (id) {
            case "close": {
                if (!powered()) return "§cNo 12 V for the closing coil";
                if (!trip.isEmpty()) return "§cTRIPPED (" + trip + ") - RESET first";
                if (closed) return "§7Already in service";
                if (discharging()) return "§cCapacitors still discharging - wait " + (200 - (world.getTotalWorldTime() - openedAt)) / 20 + " s";
                GridData.Net n = PowerQuality.measured(GridData.get(world), pos.toLong());
                if (n == null || n.level == null || !n.level.ac || !n.problem.isEmpty()) return "§cNo live AC bus touching the filter";
                if (com.dogpound.railmap.admin.Approvals.blocked(world, "filter", pos, who) != null) return com.dogpound.railmap.admin.Approvals.blocked(world, "filter", pos, who);
                closed = true;
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.CONTACTOR, SoundCategory.BLOCKS, 0.8f, 1.0f);
                Recorder.event(world, "FILTER " + id() + " closed (" + who(who) + ") " + kvar() + " kvar " + TUNE_NAME[Math.min(4, ctl.get("s_tune"))]);
                return "§aFilter " + id() + " IN SERVICE";
            }
            case "open":
                if (!closed) return "§7Already open";
                closed = false; openedAt = world.getTotalWorldTime();
                Recorder.event(world, "FILTER " + id() + " opened (" + who(who) + ")");
                return "§eFilter open - capacitors discharging";
            case "reset":
                if (trip.isEmpty()) return "§7Nothing to reset";
                if (cap < 0.92) return "§cA can has failed - REPLACE CANS before resetting";
                trip = ""; overSecs = 0; AlarmManager.get(world).clear("hf:" + pos.toLong());
                return "§aTrip reset - ready to close";
            case "recan":
                if (closed || discharging() || world.getTotalWorldTime() - openedAt < 600) return "§cThe bank must be OPEN and discharged for 30 s before anyone touches the cans";
                cap = 1; temp = 25; trip = ""; AlarmManager.get(world).clear("hf:" + pos.toLong());
                History.log(world, pos, "MAINT", "filter " + id() + ": capacitor cans replaced by " + who(who));
                return "§aCapacitor cans replaced - filter back on its tuning";
            default: return null;
        }
    }

    @Override public void invalidate() { super.invalidate(); if (world != null && !world.isRemote) Harmonics.forget(world, pos.toLong()); }

    @Override protected void save(NBTTagCompound t) { t.setBoolean("closed", closed); t.setString("trip", trip); t.setDouble("cap", cap); t.setDouble("temp", temp); }
    @Override protected void load(NBTTagCompound t) { closed = t.getBoolean("closed"); trip = t.getString("trip"); cap = t.hasKey("cap") ? t.getDouble("cap") : 1; temp = t.hasKey("temp") ? t.getDouble("temp") : 25; }
}
