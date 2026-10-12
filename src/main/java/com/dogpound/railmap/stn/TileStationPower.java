package com.dogpound.railmap.stn;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.SoundCategory;

import java.util.*;

/**
 * Station Emergency Power panel (ChatGPT batch 3 #30): the station's incoming mains (an IR Extras grid cable or machine
 * touching it, 120-480 V AC), a 240 kFE UPS battery, a standby diesel generator (fuel in its day tank: anything that
 * burns) and an automatic transfer switch. When the incoming supply fails the ATS goes to the battery at once, the
 * generator cranks after the START DELAY, runs up and takes the load (and recharges the battery); when the mains
 * comes back and holds for 10 s it transfers back and the generator runs on 30 s to cool down. Station machines within
 * reach (lamps, lifts, escalators, heaters...) are fed from this emergency bus by priority up to the SHED LEVEL (see
 * EmergencyPower); the rest stay dead. MODE: AUTO / MANUAL (START, STOP, TRANSFER by hand) / OFF. MAINT key: bypass
 * (held on mains, no emergency supply). GEN TEST runs the generator off load for a minute; BATT TEST shows the
 * runtime at the present load. E-STOP stops the generator. 12 V + mains.
 */
public class TileStationPower extends StnPanel {
    public static final long CAP = 240000;
    private static final long GEN_MAX = 600;
    static final String[] GEN = {"OFF", "CRANKING", "RUNNING", "COOLDOWN", "FAILED"};
    static final String[] ATS = {"NORMAL", "BATTERY", "GENERATOR"};
    private long soc = CAP / 2, lostAt, backAt, genT, burnLeft, testUntil;
    private double health = 100;
    private int gen, ats, fedN, shedN, fedShow, shedShow, cycles;
    private long genLoad, batLoad, genLoadShow, batLoadShow;
    private boolean deep;
    private String station = "";

    public TileStationPower() { super(new String[]{"fuel"}, new String[]{"Generator day tank (fuel)"}, new int[]{9}); }

    @Override public String wiredName() { return "Station emergency power"; }
    @Override public String gearState() { return !powered() ? "NO 12 V" : (mains.ok() ? "mains OK" : "MAINS FAILED") + " · ATS " + ATS[ats] + " · gen " + GEN[gen] + " · battery " + pct() + "%"; }
    @Override protected long load() { return 3 + (mains.ok() && soc < CAP ? 200 : 0); }

    int pct() { return (int) (soc * 100 / CAP); }
    int mode() { return Math.floorMod(ctl.get("s_mode"), 3); }
    int shedLevel() { return 1 + Math.floorMod(ctl.get("s_shed"), 4); }
    boolean maint() { return ctl.get("k_maint") == 1; }
    boolean emergency() { return ats != 0; }

    /** a station machine with no grid asks for its load: label = fed; "!why" = refused; null = this panel has no say */
    String supply(int prio, long load, String name) {
        if (!emergency()) return null;
        if (prio > shedLevel()) { shedN++; return "!load shed by station emergency power (" + EmergencyPower.LEVELS[shedLevel()] + " only)"; }
        if (ats == 2) {
            if (genLoad + load > GEN_MAX) { shedN++; return "!standby generator at full load"; }
            genLoad += load; fedN++;
            return "station standby generator";
        }
        if (soc < load) { shedN++; return "!UPS battery flat"; }
        soc -= load; batLoad += load; fedN++;
        return "station UPS battery " + pct() + "%";
    }

    private int fuelItems() { int n = 0; for (int i = 0; i < store(0).getSizeInventory(); i++) { ItemStack s = store(0).getStackInSlot(i); if (TileEntityFurnace.getItemBurnTime(s) > 0) n += s.getCount(); } return n; }

    private boolean refuel() {
        for (int i = 0; i < store(0).getSizeInventory(); i++) {
            ItemStack s = store(0).getStackInSlot(i);
            int b = TileEntityFurnace.getItemBurnTime(s);
            if (b <= 0) continue;
            burnLeft += b;
            ItemStack left = s.getItem().getContainerItem(s);
            s.shrink(1);
            if (s.isEmpty()) store(0).setInventorySlotContents(i, left == null ? ItemStack.EMPTY : left);
            return true;
        }
        return false;
    }

    @Override protected boolean screensOn() { return mainsOk() || soc > 0; }

    private boolean ctrl() { return powered() || soc > 0; }   // the ATS controller runs off the UPS when the 12 V supply is down too

    @Override
    protected String act(String id, EntityPlayer who) {
        switch (id) {
            case "gstart" -> {
                if (mode() != 1) return "§7MANUAL mode only (AUTO starts it by itself)";
                return crank("manual start by " + who(who));
            }
            case "gstop" -> { if (gen == 1 || gen == 2) { gen = 3; genT = 0; if (ats == 2) ats = mains.ok() ? 0 : soc > 0 ? 1 : 0; } return "§eGenerator stopping - cooldown"; }
            case "xfer" -> {
                if (mode() != 1) return "§7MANUAL mode only";
                if (gen == 2) { ats = ats == 2 ? (mains.ok() ? 0 : 1) : 2; } else ats = ats == 0 && !mains.ok() ? 1 : mains.ok() ? 0 : ats;
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.BREAKER, SoundCategory.BLOCKS, 0.9f, 1.0f);
                return "§eTransfer switch: " + ATS[ats];
            }
            case "gtest" -> { testUntil = world.getTotalWorldTime() + 1200; return gen == 2 ? "§eGenerator already running - test logged" : crank("weekly test run by " + who(who)); }
            case "btest" -> {
                long draw = Math.max(1, batLoadShow + genLoadShow + 20);
                return String.format(Locale.ROOT, "§eBattery test: %d%% charge, %.0f%% health - about %d min at %d FE/s", pct(), health, soc * health / 100 / draw / 60, draw);
            }
            case "reset" -> {
                if (gen == 4) gen = 0;
                AlarmManager am = AlarmManager.get(world);
                am.clear("epow:gen:" + pos.toLong()); am.clear("epow:low:" + pos.toLong());
                return "§aFaults reset";
            }
            default -> { return null; }
        }
    }

    @Override
    protected String mechanical(String id, EntityPlayer who) {
        if (id.equals("m_estop")) {
            ctl.set(id, ctl.get(id) == 1 ? 0 : 1);
            if (ctl.get(id) == 1 && (gen == 1 || gen == 2)) { gen = 0; if (ats == 2) ats = mains.ok() ? 0 : soc > 0 ? 1 : 0; Recorder.event(world, "Standby generator E-STOP at " + station + " by " + who(who)); }
            return ctl.get(id) == 1 ? "§cGENERATOR E-STOP" : "§aE-stop released";
        }
        return null;
    }

    private String crank(String why) {
        if (ctl.get("m_estop") == 1) return "§cE-STOP is in";
        if (gen == 1 || gen == 2) return "§7Already " + GEN[gen].toLowerCase(Locale.ROOT);
        if (burnLeft <= 0 && !refuel()) {
            gen = 4;
            AlarmManager.get(world).raise(world, "epow:gen:" + pos.toLong(), AlarmManager.Cat.MACHINE, AlarmManager.Sev.MAJOR, "Standby generator FAILED TO START at " + station + ": day tank empty", pos, true);
            return "§cNo fuel in the day tank - generator failed to start";
        }
        gen = 1; genT = 0;
        world.playSound(null, pos, com.dogpound.railmap.ModSounds.DIESEL_START, SoundCategory.BLOCKS, 1.0f, 1.0f);
        Recorder.event(world, "Standby generator cranking at " + station + " (" + why + ")");
        return "§aGenerator cranking";
    }

    @Override
    protected void work() {
        if (age % 20 != 0) return;
        long now = world.getTotalWorldTime();
        if (age % 200 == 0) { Map.Entry<Long, String> st = StnData.station(world, pos, 96); station = st == null ? "station" : st.getValue(); }
        fedShow = fedN; shedShow = shedN; genLoadShow = genLoad; batLoadShow = batLoad;
        fedN = shedN = 0; genLoad = batLoad = 0;
        boolean grid = mains.ok();
        AlarmManager am = AlarmManager.get(world);
        String kFail = "epow:fail:" + pos.toLong();
        if (!ctrl() || maint() || mode() == 2) {
            if (ats != 0 && (maint() || mode() == 2)) ats = 0;
        } else if (!grid) {
            backAt = 0;
            if (lostAt == 0) {
                lostAt = now;
                if (soc > 0) ats = 1;
                am.raise(world, kFail, AlarmManager.Cat.POWER, AlarmManager.Sev.MAJOR, "STATION POWER FAILURE at " + station + " - emergency supply on " + (soc > 0 ? "UPS battery" : "NOTHING (battery flat)"), pos, true);
                Recorder.event(world, "Station power failure at " + station + ": ATS to " + ATS[ats]);
                com.dogpound.railmap.api.QuestEvents.fire(world, null, pos, "station_power", "station", station, "state", "FAILED");
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.BREAKER_TRIP, SoundCategory.BLOCKS, 1.0f, 1.0f);
            }
            int delay = new int[]{5, 10, 30}[Math.floorMod(ctl.get("s_delay"), 3)];
            if (mode() == 0 && gen == 0 && (now - lostAt) / 20 >= delay) crank("mains failed " + (now - lostAt) / 20 + " s ago");
            if (gen == 2 && mode() == 0) ats = 2;
            if (ats == 1 && soc <= 0 && gen != 2) ats = 1;               // stays on the (flat) battery: loads are refused
        } else if (lostAt != 0) {
            if (backAt == 0) backAt = now;
            if (now - backAt >= 200 && mode() == 0) {
                ats = 0; lostAt = 0; backAt = 0;
                if (gen == 2) { gen = 3; genT = 0; }
                am.clear(kFail);
                Recorder.event(world, "Station mains restored at " + station + ": retransferred to NORMAL");
                com.dogpound.railmap.api.QuestEvents.fire(world, null, pos, "station_power", "station", station, "state", "RESTORED");
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.BREAKER_CLOSE, SoundCategory.BLOCKS, 1.0f, 1.0f);
            }
        }
        // the generator
        genT++;
        if (gen == 1 && genT >= 5) { gen = 2; genT = 0; Recorder.event(world, "Standby generator RUNNING at " + station); }
        if (gen == 2) {
            if (ctl.get("m_estop") == 1) gen = 0;
            burnLeft -= Math.round(20 * (0.25 + 0.75 * genLoadShow / (double) GEN_MAX));
            if (burnLeft <= 0 && !refuel()) {
                gen = 4;
                if (ats == 2) ats = soc > 0 ? 1 : 0;
                am.raise(world, "epow:gen:" + pos.toLong(), AlarmManager.Cat.MACHINE, AlarmManager.Sev.MAJOR, "Standby generator at " + station + " RAN OUT OF FUEL", pos, true);
            }
            if (gen == 2) soc = Math.min(CAP, soc + 300);                 // charging from the generator
            if (grid && lostAt == 0 && testUntil > 0 && now > testUntil && mode() == 0) { gen = 3; genT = 0; testUntil = 0; Recorder.event(world, "Generator test run complete at " + station); }
        }
        if (gen == 3 && genT >= 30) { gen = 0; world.playSound(null, pos, com.dogpound.railmap.ModSounds.DIESEL_STOP, SoundCategory.BLOCKS, 0.8f, 1.0f); }
        // the battery
        if (grid && ats == 0 && live()) soc = Math.min(CAP, soc + 200);
        if (pct() < 20) {
            if (!deep) { deep = true; cycles++; health = Math.max(40, health - 0.5); }
            if (emergency()) am.raise(world, "epow:low:" + pos.toLong(), AlarmManager.Cat.POWER, AlarmManager.Sev.MINOR, "Station UPS battery LOW at " + station + ": " + pct() + "%", pos, true);
        } else if (pct() > 50) { deep = false; am.clear("epow:low:" + pos.toLong()); }
        // the panel
        put("gridl", grid); put("battl", ats == 1); put("genl", gen == 2); put("crankl", gen == 1); put("faill", gen == 4); put("lowb", pct() < 20);
        put("shedl", shedShow > 0); put("atsn", ats == 0); put("atse", ats != 0); put("maintl", maint()); put("estopl", ctl.get("m_estop") == 1);
        put("socg", pct()); put("fuelg", Math.min(100, fuelItems() * 100 / 64 + (burnLeft > 0 ? 1 : 0))); put("loadg", (int) (genLoadShow * 100 / GEN_MAX)); put("healthg", (int) health);
        long draw = Math.max(1, batLoadShow + 1);
        text("line1", ats == 0 ? (grid ? "NORMAL SUPPLY" : "MAINS FAILED") : ats == 1 ? "ON UPS BATTERY " + pct() + "%" : "ON GENERATOR " + genLoadShow * 100 / GEN_MAX + "%");
        text("line2", "FED " + fedShow + "  SHED " + shedShow + "  GEN " + GEN[gen]);
        text("rt", ats == 1 ? "RUNTIME " + Math.min(999, soc / draw / 60) + " MIN" : "BATT " + pct() + "% H" + Math.round(health));
        text("lvl", EmergencyPower.LEVELS[shedLevel()]);
        text("stn", StnUtil.up(StnUtil.cut(station, 18)));
    }

    // console
    public String summary() { return station + "|" + ATS[ats] + "|" + GEN[gen] + "|" + pct() + "|" + fedShow + "|" + shedShow + "|" + EmergencyPower.LEVELS[shedLevel()] + "|" + (mains.ok() ? "OK" : "FAILED"); }
    public String startGen(String who) { return crank("console start by " + who); }

    @Override
    protected void saveExtra(NBTTagCompound t) {
        t.setLong("soc", soc); t.setLong("lost", lostAt); t.setLong("back", backAt); t.setLong("burn", burnLeft); t.setDouble("health", health);
        t.setInteger("gen", gen); t.setInteger("ats", ats); t.setInteger("cycles", cycles); t.setBoolean("deep", deep); t.setString("stn", station);
    }

    @Override
    protected void loadExtra(NBTTagCompound t) {
        soc = t.hasKey("soc") ? t.getLong("soc") : CAP / 2; lostAt = t.getLong("lost"); backAt = t.getLong("back"); burnLeft = t.getLong("burn");
        health = t.hasKey("health") ? t.getDouble("health") : 100; gen = t.getInteger("gen"); ats = t.getInteger("ats"); cycles = t.getInteger("cycles"); deep = t.getBoolean("deep"); station = t.getString("stn");
    }
}
