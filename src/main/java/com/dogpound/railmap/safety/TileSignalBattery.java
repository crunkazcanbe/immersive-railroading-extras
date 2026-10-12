package com.dogpound.railmap.safety;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.depot.Mains;
import com.dogpound.railmap.wire.IWiredGear;
import com.dogpound.railmap.wire.Terminal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Signal Power Backup Cabinet (her ChatGPT batch-3 #14): a lineside cabinet with a 12 V lead-acid battery string and a
 * charger. The CHARGER runs from 230 V AC mains (an IR Extras grid cable touching the cabinet, depot.Mains) and draws a
 * real load: the charge current while recharging, a float trickle when full, plus the gear it carries. While the mains
 * is there the backed gear runs on its own 12 V supply. When the grid goes, every piece of 12 V gear the cabinet covers
 * (up to 8 wrench-linked: signals, track circuits, safety gear; or everything in 16 / 32 m) whose own supply died is fed
 * from the battery (wire.Terminal.backup), so signals stay lit through the cut - for as long as the battery lasts:
 * 12 W per device + the cabinet's own 8 W, on the Minecraft clock like the grid UPS. Flat = low-voltage disconnect,
 * everything it fed goes dark (safe). Batteries age: every deep discharge and every flat costs health; BATTERY TEST
 * runs a timed discharge and measures the real capacity. The cabinet's own 12 V terminal is its control and alarm
 * interface (WIRED page); its lamps and screen run on the battery when the 12 V is gone too.
 */
public class TileSignalBattery extends SafetyTile {
    public static final int[] AH = {24, 65, 100, 200};
    static final double W_DEVICE = 12, W_SELF = 8;
    /** mWh per watt-second on the Minecraft clock (72x real time), like grid.Ups */
    static final double HOUR = 1000 * 72 / 3600.0;
    static final int MAX_LINKS = 8, TEST_SECONDS = 30;

    private final Mains mains = new Mains();
    private final List<Long> links = new ArrayList<>();
    private double energy = -1, health = 1, minSoc = 1, measured = -1;
    private int cycles, flats, fed, covered, testLeft;
    private boolean onBatt, lvd, wasLow, wasFault;
    private long lastTest;

    // ---- the battery maths (static so the self-test can prove it) --------------------------------------------------
    static double capacity(int ah, double health) { return ah * 12.0 * 1000 * health; }
    /** terminal volts: 13.6 V float on charge, 12.7 (full) .. 11.5 (empty) on battery */
    static double volts(boolean charging, double soc) { return charging ? 13.6 : 11.5 + 1.2 * Math.max(0, Math.min(1, soc)); }
    /** seconds (real) left at this load */
    static double runtime(double energy, double watts) { return watts <= 0 ? Double.POSITIVE_INFINITY : energy / (watts * HOUR); }
    /** what one discharge costs the battery: deeper = worse; flat = a lot */
    static double wear(double minSoc, boolean flat) { return flat ? 0.03 : minSoc < 0.5 ? 0.005 + (0.5 - minSoc) * 0.02 : 0.001; }

    int ah() { return AH[Math.min(3, Math.max(0, ctl.get("s_ah")))]; }
    double cap() { return capacity(ah(), health); }
    double soc() { return Math.max(0, Math.min(1, energy / Math.max(1, cap()))); }
    boolean mainsOk() { return mains.ok() && !term.burnt; }
    double loadW() { return W_SELF + W_DEVICE * fed; }

    /** the cabinet runs its own lamps and screen off the battery too */
    @Override public boolean powered() { return term.powered() || energy > 0 && !lvd && !term.burnt; }

    @Override public String wiredName() { return "Signal battery cabinet"; }
    @Override public String gearState() {
        if (term.burnt) return "BURNT OUT - screwdriver";
        String s = Math.round(soc() * 100) + "% " + ah() + " Ah, health " + Math.round(health * 100) + "%";
        if (lvd) return "FLAT - low-voltage disconnect, backed gear dark";
        if (onBatt) return "ON BATTERY - feeding " + fed + " device(s), " + minutes(runtime(energy, loadW())) + " left · " + s;
        return (testLeft > 0 ? "BATTERY TEST " + testLeft + " s · " : "on mains (" + mains.what + ") · ") + covered + " device(s) covered · " + s;
    }
    @Override public int wiredLamp() { return term.burnt || lvd ? 3 : onBatt || health < 0.6 ? 2 : 1; }
    @Override public void gearInfo(Map<String, Object> m) {
        m.put("soc", Math.round(soc() * 100)); m.put("health", Math.round(health * 100)); m.put("onBattery", onBatt); m.put("mains", mains.what);
        m.put("fed", fed); m.put("covered", covered); m.put("ah", ah()); m.put("cycles", cycles);
    }

    static String minutes(double s) { return Double.isInfinite(s) ? "--" : s >= 3600 ? String.format(Locale.ROOT, "%.1f H", s / 3600) : Math.round(s / 60) + " MIN"; }

    /** the gear this cabinet covers now */
    List<IWiredGear> gear() {
        List<IWiredGear> out = new ArrayList<>();
        int mode = ctl.get("s_cov");
        if (mode == 0) {
            for (long l : links) {
                BlockPos p = BlockPos.fromLong(l);
                if (world.isBlockLoaded(p) && world.getTileEntity(p) instanceof IWiredGear g) out.add(g);
            }
        } else {
            double r = mode == 1 ? 16 : 32;
            for (TileEntity t : world.loadedTileEntityList)
                if (t instanceof IWiredGear g && !(t instanceof TileSignalBattery) && !t.isInvalid() && t.getPos().distanceSq(pos) <= r * r) out.add(g);
        }
        return out;
    }

    @Override
    protected void serverTick() {
        if (energy < 0) energy = cap();                                         // a new cabinet comes charged
        boolean charging = mainsOk() && testLeft == 0;
        double chargeW = charging && energy < cap() ? ah() * 12 * 0.1 : charging ? 2 : 0;   // C/10 charge, then a float trickle
        if (mains.tick(world, pos, term, wiredName(), (long) Math.ceil(chargeW + (charging ? loadW() : 0)))) controlsChanged();
        if (age % 20 != 5) return;
        List<IWiredGear> g = gear();
        covered = g.size();
        boolean battery = (!mainsOk() || testLeft > 0) && energy > 0 && !lvd && SigConfig.batteryBackup && !term.burnt;
        fed = 0;
        if (battery) for (IWiredGear x : g) {
            Terminal t = x.term();
            if (t.supply != 1 && !t.burnt && !t.testCut) { t.backup = 3; fed++; }
        }
        // energy in / out this second
        if (charging) {
            energy = Math.min(cap(), energy + chargeW * HOUR * 0.85);          // 85 % charge efficiency
            if (lvd && soc() > 0.25) { lvd = false; Recorder.event(world, "Battery cabinet " + where() + ": recharged, low-voltage disconnect reset"); }
        } else if (!lvd && !term.burnt) {
            energy -= (testLeft > 0 ? loadW() + 120 : loadW()) * HOUR;         // a test puts its 120 W load bank on
            if (energy <= 0) {
                energy = 0; lvd = true; flats++;
                health = Math.max(0.2, health - wear(0, true));
                AlarmManager.get(world).raise(world, "batt:low:" + pos.toLong(), AlarmManager.Cat.WIRED, AlarmManager.Sev.MAJOR,
                        "Signal battery cabinet at " + where() + " FLAT - low-voltage disconnect, " + fed + " device(s) dark", pos, true);
                Recorder.event(world, "Battery cabinet " + where() + " FLAT - low-voltage disconnect");
            }
        }
        minSoc = Math.min(minSoc, soc());
        // on battery / back on mains
        boolean nowOn = !mainsOk() && testLeft == 0;
        if (nowOn != onBatt) {
            onBatt = nowOn;
            AlarmManager am = AlarmManager.get(world);
            if (onBatt) {
                am.raise(world, "batt:mains:" + pos.toLong(), AlarmManager.Cat.WIRED, AlarmManager.Sev.MINOR, "Signal battery cabinet at " + where() + ": MAINS FAIL - on battery (" + mains.what + ")", pos, true);
                Recorder.event(world, "Battery cabinet " + where() + " ON BATTERY - " + mains.what);
                minSoc = soc();
            } else {
                am.clear("batt:mains:" + pos.toLong());
                endDischarge("mains restored");
            }
        }
        boolean low = !lvd && soc() < 0.2 && !mainsOk();
        if (low != wasLow) { wasLow = low; if (low) AlarmManager.get(world).raise(world, "batt:low:" + pos.toLong(), AlarmManager.Cat.WIRED, AlarmManager.Sev.MAJOR, "Signal battery cabinet at " + where() + " LOW BATTERY - " + minutes(runtime(energy, loadW())) + " left", pos, true); }
        if (!low && !lvd) AlarmManager.get(world).clear("batt:low:" + pos.toLong());
        boolean fault = health < 0.6;
        if (fault != wasFault) {
            wasFault = fault;
            if (fault) AlarmManager.get(world).raise(world, "batt:fault:" + pos.toLong(), AlarmManager.Cat.WIRED, AlarmManager.Sev.MAJOR, "Signal battery cabinet at " + where() + " BATTERY FAULT - health " + Math.round(health * 100) + "%, replace the string", pos, true);
            else AlarmManager.get(world).clear("batt:fault:" + pos.toLong());
        }
        // the battery test
        if (testLeft > 0 && --testLeft == 0) {
            measured = health * (0.97 + world.rand.nextDouble() * 0.06);        // a real test reads within a few %
            lastTest = world.getTotalWorldTime();
            endDischarge("battery test");
            String res = "Battery test " + where() + ": capacity " + Math.round(measured * 100) + "% (" + Math.round(ah() * measured) + " Ah of " + ah() + ") - " + (measured >= 0.8 ? "PASS" : measured >= 0.6 ? "MARGINAL" : "FAIL");
            Recorder.event(world, res);
            SafetyData.get(world).history(world, pos, wiredName(), "battery cabinet", res);
            if (com.dogpound.railmap.api.QuestEvents.active())
                com.dogpound.railmap.api.QuestEvents.fire(world, null, pos, "battery_test", "capacity", Math.round(measured * 100), "pass", measured >= 0.8);
            world.playSound(null, pos, com.dogpound.railmap.ModSounds.BEEP_OK, SoundCategory.BLOCKS, 0.6f, 1.0f);
        }
        put("pwr", powered());
        put("mainsl", mainsOk());
        put("onbat", onBatt || testLeft > 0);
        put("low", wasLow || lvd);
        put("fault", fault || term.burnt);
        put("chg", charging && energy < cap());
        put("lvd", lvd);
        put("testl", testLeft > 0);
        put("soc", (int) Math.round(soc() * 100));
        put("health", (int) Math.round(health * 100));
        put("load", (int) Math.round(onBatt || testLeft > 0 ? loadW() : charging ? chargeW + loadW() : 0));
        put("fedn", fed);
        put("covn", covered);
        text("volts", String.format(Locale.ROOT, "%.1fV", volts(charging, soc())));
        text("run", onBatt || testLeft > 0 ? minutes(runtime(energy, loadW())) : "CHARGER");
        text("cap", ah() + "AH " + (measured < 0 ? "UNTESTED" : Math.round(measured * 100) + "% TESTED"));
        text("cov", new String[]{"LINKED " + links.size() + "/8", "ALL 16M", "ALL 32M"}[Math.min(2, ctl.get("s_cov"))]);
        text("state", lvd ? "FLAT - LVD" : testLeft > 0 ? "TEST " + testLeft + "S" : onBatt ? "ON BATTERY" : energy < cap() - 1 ? "CHARGING" : "FLOAT");
    }

    private void endDischarge(String why) {
        if (minSoc < 0.98) {
            cycles++;
            health = Math.max(0.2, health - wear(minSoc, false));
            Recorder.event(world, "Battery cabinet " + where() + ": discharge cycle " + cycles + " ended (" + why + ") down to " + Math.round(minSoc * 100) + "%, health " + Math.round(health * 100) + "%");
        }
        minSoc = 1;
    }

    String where() { return pos.getX() + "," + pos.getZ(); }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        switch (id) {
            case "test": return lampTest();
            case "s_ah": {
                double s = soc();
                ctl.set("s_ah", (ctl.get("s_ah") + 1) % AH.length);
                energy = cap() * s;
                health = Math.max(health, 1.0);                                // a new string of a different size
                measured = -1;
                return "§eBattery string " + ah() + " Ah fitted (new: health 100%)";
            }
            case "s_cov": ctl.set("s_cov", (ctl.get("s_cov") + 1) % 3); return "§eCovers: " + new String[]{"the linked gear (Signal Wrench: pick, then click the cabinet)", "all 12 V gear within 16 m", "all 12 V gear within 32 m"}[ctl.get("s_cov")];
            case "btest": return startTest(who(who));
            case "bstop": if (testLeft == 0) return "§7No test running"; testLeft = 0; endDischarge("test aborted"); return "§eBattery test aborted";
            case "renew":
                if (ctl.get("k_door") != 1) return "§cOpen the cabinet door (key) to change the battery string";
                health = 1; cycles = 0; flats = 0; measured = -1; energy = cap() * 0.5; lvd = false;
                SafetyData.get(world).history(world, pos, wiredName(), "battery cabinet", "new battery string fitted by " + who(who));
                return "§aNew " + ah() + " Ah string fitted (half charged)";
            case "k_door": return null;
            default: return "§7" + gearState();
        }
    }

    String startTest(String who) {
        if (testLeft > 0) return "§7A test is already running";
        if (!mainsOk()) return "§cNo test on battery - the mains is off";
        if (soc() < 0.9) return "§cCharge to 90% before a capacity test (" + Math.round(soc() * 100) + "% now)";
        testLeft = TEST_SECONDS;
        minSoc = soc();
        Recorder.event(world, "Battery test started at " + where() + " by " + who);
        return "§eBattery test: " + TEST_SECONDS + " s discharge on the load bank, the gear rides on the battery";
    }

    @Override
    public String link(String control, BlockPos pick, EntityPlayer who) {
        if (pick.equals(pos)) return null;
        if (!(world.getTileEntity(pick) instanceof IWiredGear)) return "§7Pick a piece of 12 V gear (a signal, track circuit, safety gear)";
        if (links.remove(pick.toLong())) { controlsChanged(); return "§7Unlinked " + pick.getX() + "," + pick.getZ() + " from the battery cabinet"; }
        if (links.size() >= MAX_LINKS) return "§cThe cabinet carries 8 devices - unlink one first";
        links.add(pick.toLong());
        controlsChanged();
        return "§aBattery backs up " + ((IWiredGear) world.getTileEntity(pick)).wiredName() + " (" + links.size() + "/8)" + (ctl.get("s_cov") != 0 ? " §7- set COVER to LINKED" : "");
    }

    @Override protected void save(NBTTagCompound t) {
        t.setDouble("e", energy); t.setDouble("h", health); t.setDouble("ms", minSoc); t.setDouble("meas", measured);
        t.setInteger("cy", cycles); t.setInteger("fl", flats); t.setInteger("tl", testLeft); t.setBoolean("ob", onBatt); t.setBoolean("lvd", lvd); t.setLong("lt", lastTest);
        net.minecraft.nbt.NBTTagList l = new net.minecraft.nbt.NBTTagList();
        for (long v : links) l.appendTag(new net.minecraft.nbt.NBTTagLong(v));
        t.setTag("links", l);
        NBTTagCompound m = new NBTTagCompound(); mains.write(m); t.setTag("feed", m);
    }

    @Override protected void load(NBTTagCompound t) {
        energy = t.hasKey("e") ? t.getDouble("e") : -1; health = t.hasKey("h") ? t.getDouble("h") : 1; minSoc = t.hasKey("ms") ? t.getDouble("ms") : 1; measured = t.hasKey("meas") ? t.getDouble("meas") : -1;
        cycles = t.getInteger("cy"); flats = t.getInteger("fl"); testLeft = t.getInteger("tl"); onBatt = t.getBoolean("ob"); lvd = t.getBoolean("lvd"); lastTest = t.getLong("lt");
        links.clear();
        net.minecraft.nbt.NBTTagList l = t.getTagList("links", 4);
        for (int i = 0; i < l.tagCount(); i++) links.add(((net.minecraft.nbt.NBTTagLong) l.get(i)).getLong());
        mains.read(t.getCompoundTag("feed"));
    }

    // ---- console row ---------------------------------------------------------------------------------------------------
    String[] row() {
        return new String[]{"b.bat", where(), String.valueOf(ah()), String.valueOf(Math.round(soc() * 100)), String.valueOf(Math.round(health * 100)),
                lvd ? "FLAT" : testLeft > 0 ? "TEST" : onBatt ? "BATTERY" : "MAINS", String.valueOf(fed), String.valueOf(covered),
                String.valueOf(Math.round(loadW())), onBatt || testLeft > 0 ? minutes(runtime(energy, loadW())) : "-", String.valueOf(cycles),
                measured < 0 ? "untested" : Math.round(measured * 100) + "%", mains.what, String.valueOf(pos.toLong())};
    }
}
