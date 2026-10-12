package com.dogpound.railmap.weather;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.block.StationControls;
import com.dogpound.railmap.block.TileFurniture;
import com.dogpound.railmap.grid.Elec;
import com.dogpound.railmap.infra.Supply;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import java.util.*;

/**
 * Weather Train Depot desk (her IR Extras list #4): the winter / autumn controller's desk, three blocks wide. The wall
 * board lists the depot's runs (kind, state, train, cells done / to do) with RUN / CALLED lamps; the forecast panel
 * lights SNOW / ICE / LEAF / WIND / COLD SNAP / RAIN and shows the risks; gauges read the worst snow, the worst wire ice,
 * the leafy track and the sandite stock. On the desk: PLAN PLOUGH / PLAN SANDITE / PLAN DE-ICE, DISPATCH (calls the
 * nearest free train of that kind - a sandite run takes sand from the SANDITE STOCK cupboard), CANCEL, PREV / NEXT run,
 * PRINT (the plan as a book), ACK, SCAN, LAMP TEST; the AUTO key (plans and calls runs itself when the forecast passes
 * the THRESHOLD knob), RANGE and VIEW knobs; six drawers and a cupboard of real storage and a shelf that shows what
 * stands on it. 12 V for the lamps and buttons, a 120 / 240 V AC supply for the planning computer.
 */
public class TileWxDepot extends TileFurniture {
    static final String[] IDS = {"cards", "radios", "ppe", "forms", "keys", "spares", "sand"};
    static final String[] NAMES = {"Route cards", "Radios + handlamps", "PPE (hi-vis, boots)", "Forms + logbooks", "Train keys", "Spares (plough blades, nozzles)", "Sandite stock (sand)"};
    static final int[] SIZES = {9, 9, 18, 9, 9, 18, 27};
    static final int[] RANGES = {128, 256, 512, 1024};
    static final int[] THRESH = {40, 60, 80};
    /** shelf spots (model px) - tools/gen_bb_wxtrain.py */
    static final double[][] SPOTS = {{19.933, 7.0, 13.4}, {21.6, 7.0, 13.4}, {23.267, 7.0, 13.4}, {24.933, 7.0, 13.4}, {26.6, 7.0, 13.4}, {28.267, 7.0, 13.4}};

    private final Supply mains = new Supply(Elec.Level.AC120, Elec.Level.AC240);
    private int sel;
    private long lastAuto;
    private WxTrains.Forecast fc;

    public TileWxDepot() { super(IDS, NAMES, SIZES, 6); }

    @Override public double[][] shelfSpots() { return SPOTS; }
    @Override public String wiredName() { return "Weather train depot desk " + pos.getX() + "," + pos.getZ(); }
    @Override public String gearState() { return (term.powered() ? "" : "no 12 V · ") + mains.status().replaceAll("§.", "") + " · " + openRuns().size() + " open run(s)" + (auto() ? " · AUTO" : ""); }
    @Override protected String refuse(int k, EntityPlayer who) { return ctl.get("k_lock") == 1 ? "§cLocked - the DESK key is at LOCK" : null; }
    @Override protected boolean mechanical(String id) { return id.startsWith("k_") || id.startsWith("s_"); }

    public int range() { return RANGES[Math.max(0, Math.min(3, ctl.get("s_range")))]; }
    public boolean auto() { return ctl.get("k_auto") == 1 && term.powered() && mains.ok; }
    private boolean computer() { return term.powered() && mains.ok; }

    @Override
    public void gearChanged() {
        if (!term.burnt && mains.burnt) { mains.burnt = false; AlarmManager.get(world).clear("wxdeskburn:" + pos.toLong()); }
        super.gearChanged();
    }

    List<WxTrainData.Run> openRuns() {
        List<WxTrainData.Run> o = new ArrayList<>();
        if (world == null) return o;
        for (WxTrainData.Run r : WxTrainData.get(world).runs) if (r.depot == pos.toLong() && (r.open() || world.getTotalWorldTime() - r.ended < 6000)) o.add(r);
        return o;
    }

    private WxTrainData.Run selected() { List<WxTrainData.Run> l = openRuns(); return l.isEmpty() ? null : l.get(Math.max(0, Math.min(l.size() - 1, sel))); }

    // ---- tick ------------------------------------------------------------------------------------------------------------
    @Override
    protected void serverTick() {
        if (age % 20 == 9) {
            mains.tick(world, pos, 220, this::burn, m -> { for (EntityPlayer p : world.playerEntities) if (p.getDistanceSq(pos) < 576) p.sendStatusMessage(new TextComponentString(m), true); });
        }
        if (age % 100 == 21 && computer()) fc = WxTrains.forecast(world, pos, range());
        long now = world.getTotalWorldTime();
        if (auto() && fc != null && now - lastAuto >= 600) {
            lastAuto = now;
            int th = THRESH[Math.max(0, Math.min(2, ctl.get("s_thresh")))];
            for (int k = 0; k < 3; k++) if (fc.risk[k] >= th) autoRun(k, "forecast " + WxTrains.SHORT[k] + " " + fc.risk[k] + "%" + (fc.why[k].isEmpty() ? "" : ": " + fc.why[k]));
        }
    }

    private void burn() {
        term.burnt = true;
        String msg = wiredName() + " BURNT OUT: " + mains.what + " - screwdriver to repair once the supply is right";
        AlarmManager.get(world).raise(world, "wxdeskburn:" + pos.toLong(), AlarmManager.Cat.FIRE, AlarmManager.Sev.MAJOR, msg, pos, true);
        Recorder.event(world, msg);
        world.playSound(null, pos, com.dogpound.railmap.ModSounds.FUSE_BLOW, SoundCategory.BLOCKS, 1f, 0.8f);
        controlsChanged();
    }

    /** the AUTO key's work (and a drone's request): plan, take sand for sandite, call the train */
    void autoRun(int kind, String why) {
        if (!auto()) return;
        WxTrainData.Run r = WxTrains.plan(world, pos, range(), kind, why, true);
        if (r == null || r.state != WxTrainData.PLANNED) return;
        if (kind == WxTrains.SANDITE) {
            String no = takeSand();
            if (no != null) { AlarmManager.get(world).raise(world, "wxsand:" + pos.toLong(), AlarmManager.Cat.GENERAL, AlarmManager.Sev.MINOR, wiredName() + ": SANDITE STOCK LOW - run #" + r.id + " waits", pos, true); return; }
        }
        WxTrains.dispatch(world, r);
    }

    /** take a sandite run's sand from the SANDITE STOCK cupboard: null = done, else why not */
    String takeSand() {
        int need = WxTrainConfig.sanditeSand;
        if (sand() < need) return "§cSANDITE STOCK: " + sand() + " sand, a run needs " + need + " - fill the cupboard";
        net.minecraft.inventory.IInventory inv = storage(6);
        Item s = Item.getItemFromBlock(Blocks.SAND);
        for (int i = 0; i < inv.getSizeInventory() && need > 0; i++) {
            ItemStack st = inv.getStackInSlot(i);
            if (st.getItem() != s) continue;
            int t = Math.min(need, st.getCount());
            inv.decrStackSize(i, t);
            need -= t;
        }
        AlarmManager.get(world).clear("wxsand:" + pos.toLong());
        return null;
    }

    int sand() {
        net.minecraft.inventory.IInventory inv = storage(6);
        Item s = Item.getItemFromBlock(Blocks.SAND);
        int n = 0;
        for (int i = 0; i < inv.getSizeInventory(); i++) if (inv.getStackInSlot(i).getItem() == s) n += inv.getStackInSlot(i).getCount();
        return n;
    }

    String planFromConsole(int kind, String who) {
        if (!computer()) return "§cThe depot's planning computer is down: " + (term.powered() ? mains.what : "no 12 V");
        WxTrainData.Run r = WxTrains.plan(world, pos, range(), kind, "planned by " + who, false);
        if (r == null) return "§7No track in range has had trains on it yet - nothing to plan";
        sel = openRuns().indexOf(r);
        return "§aRun #" + r.id + " " + WxTrains.KINDS[kind] + " " + WxTrainData.STATES[r.state] + ": " + r.targets.size() + " cell(s) - DISPATCH to call a train";
    }

    String toggleAuto(String who) {
        ctl.set("k_auto", ctl.get("k_auto") == 1 ? 0 : 1);
        controlsChanged();
        Recorder.event(world, wiredName() + " AUTO " + (ctl.get("k_auto") == 1 ? "ON" : "OFF") + " by " + who);
        return ctl.get("k_auto") == 1 ? "§aAUTO scheduling ON" : "§7AUTO scheduling OFF";
    }

    // ---- controls --------------------------------------------------------------------------------------------------------
    @Override
    public int controlValue(String id) {
        if (term.powered() && world != null && ctl.get("lamptest") == 1 && world.getTotalWorldTime() - ctl.pressAt < 60 && (id.startsWith("l_") || id.startsWith("f_") || id.startsWith("ra") || id.startsWith("rc"))) return 1;
        return super.controlValue(id);
    }

    @Override
    protected String press(String id, EntityPlayer who) {
        String by = who == null ? "console" : who.getName();
        if (id.equals("test")) { ctl.set("lamptest", 1); return "§7Lamp test"; }
        if (id.equals("ack")) {
            AlarmManager am = AlarmManager.get(world);
            for (AlarmManager.Alarm a : am.all()) if ((a.key.startsWith("wx") || a.key.startsWith("wxrun:")) && !a.acked) am.ack(a.id, by);
            world.playSound(null, pos, com.dogpound.railmap.ModSounds.ACK_BEEP, SoundCategory.BLOCKS, 0.5f, 1f);
            return "§eWeather alarms acknowledged";
        }
        if (ctl.get("k_lock") == 1) return "§cThe desk is LOCKED - turn the DESK key to OPEN";
        if (id.equals("prev") || id.equals("next")) { int n = openRuns().size(); sel = n == 0 ? 0 : (sel + (id.equals("next") ? 1 : n - 1)) % n; return "§7Run " + (n == 0 ? "-" : (sel + 1) + " of " + n); }
        if (!computer()) return "§cThe planning computer is down: " + (term.powered() ? mains.what : "no 12 V");
        switch (id) {
            case "p_plough": return planFromConsole(WxTrains.PLOUGH, by);
            case "p_sandite": return planFromConsole(WxTrains.SANDITE, by);
            case "p_deice": return planFromConsole(WxTrains.DEICE, by);
            case "scan": fc = WxTrains.forecast(world, pos, range()); return "§7Forecast: snow " + fc.risk[0] + "% · leaves " + fc.risk[1] + "% · wire ice " + fc.risk[2] + "%";
            case "dispatch": {
                WxTrainData.Run r = selected();
                if (r == null) return "§7No run - plan one first";
                if (r.state != WxTrainData.PLANNED) return "§7Run #" + r.id + " is " + WxTrainData.STATES[r.state];
                if (r.kind == WxTrains.SANDITE) { String no = takeSand(); if (no != null) return no; }
                return WxTrains.dispatch(world, r);
            }
            case "cancel": { WxTrainData.Run r = selected(); return r == null ? "§7No run selected" : WxTrains.cancel(world, r, by); }
            case "print": {
                if (who == null) return "§7Printing is done at the desk";
                List<String> l = new ArrayList<>();
                WxTrains.Forecast f = WxTrains.forecast(world, pos, range());
                l.add("WEATHER TRAIN PLAN " + pos.getX() + "," + pos.getZ());
                l.add(String.format(Locale.ROOT, "Air %.0f C, rain %s", f.air, f.raining ? "now" : f.rainIn < 0 ? "not soon" : "in " + f.rainIn + " min"));
                for (int k = 0; k < 3; k++) l.add(WxTrains.KINDS[k] + " risk " + f.risk[k] + "% " + f.why[k]);
                for (WxTrainData.Run r : openRuns()) l.add("#" + r.id + " " + WxTrains.SHORT[r.kind] + " " + WxTrainData.STATES[r.state] + " " + r.locoName + " " + r.done.size() + "/" + r.targets.size());
                ItemStack b = com.dogpound.railmap.depot.Diagnostics.book("Weather train plan", "Depot desk", l);
                if (!who.inventory.addItemStackToInventory(b)) who.dropItem(b, false);
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.PRINTER, SoundCategory.BLOCKS, 0.6f, 1f);
                return "§aPlan printed";
            }
            default: return null;
        }
    }

    private boolean put(String id, int v) { return StationControls.put(ctl, id, v); }
    private boolean put(String id, boolean v) { return StationControls.put(ctl, id, v); }
    private boolean text(String id, String t) { return StationControls.text(ctl, id, t == null ? "" : t.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9 .:>$/%+-]", "")); }

    @Override
    protected boolean refresh() {
        boolean ch = false;
        boolean on = computer();
        WxTrains.Forecast f = on ? fc : null;
        ch |= put("l_pwr", term.powered());
        ch |= put("l_mains", mains.ok);
        ch |= put("l_auto", auto());
        ch |= put("l_alarm", world.getTotalWorldTime() % 20 < 10 && AlarmManager.get(world).all().stream().anyMatch(a -> a.key.startsWith("wx") && !a.acked && a.active));
        ch |= put("l_lock", ctl.get("k_lock") == 1);
        ch |= put("l_sand", sand() < WxTrainConfig.sanditeSand);
        ch |= text("title", "WEATHER TRAINS " + pos.getX() + "," + pos.getZ() + " RANGE " + range() + "M");
        List<WxTrainData.Run> runs = openRuns();
        for (int i = 0; i < 6; i++) {
            WxTrainData.Run r = i < runs.size() ? runs.get(i) : null;
            ch |= text("r" + (i + 1), r == null ? "" : String.format(Locale.ROOT, "%s%d %s %s %s %d/%d", i == sel ? ">" : " ", r.id, WxTrains.SHORT[r.kind], WxTrainData.STATES[r.state], r.locoName, r.done.size(), r.targets.size()));
            ch |= put("ra" + (i + 1), r != null && r.state == WxTrainData.RUNNING);
            ch |= put("rc" + (i + 1), r != null && (r.state == WxTrainData.CALLED || r.state == WxTrainData.PLANNED));
        }
        int th = THRESH[Math.max(0, Math.min(2, ctl.get("s_thresh")))];
        ch |= put("f_snow", f != null && f.risk[0] >= th);
        ch |= put("f_leaf", f != null && f.risk[1] >= th);
        ch |= put("f_ice", f != null && f.risk[2] >= th);
        ch |= put("f_cold", f != null && f.coldSnap);
        ch |= put("f_rain", f != null && (f.raining || f.rainIn >= 0 && f.rainIn < 15));
        double wind = Weather.on() ? Weather.cell(world, pos).wind : 0;
        ch |= put("f_wind", f != null && wind > 15);
        ch |= text("fc1", f == null ? (term.powered() ? mains.what : "") : String.format(Locale.ROOT, "AIR %.0f C RAIN %s", f.air, f.raining ? "NOW" : f.rainIn < 0 ? "NOT SOON" : "IN " + f.rainIn + " MIN"));
        ch |= text("fc2", f == null ? "" : "SNOW " + f.risk[0] + "% " + f.why[0]);
        ch |= text("fc3", f == null ? "" : "LEAF " + f.risk[1] + "% " + f.why[1]);
        ch |= text("fc4", f == null ? "" : "ICE " + f.risk[2] + "% " + f.why[2]);
        ch |= put("g_snow", f == null ? 0 : (int) Math.min(100, f.worstSnow / 5));
        ch |= put("g_ice", f == null ? 0 : (int) Math.min(100, f.worstIce * 10));
        ch |= put("g_leaf", f == null ? 0 : Math.min(100, f.leafy * 10));
        ch |= put("g_sand", (int) Math.min(100, sand() * 100L / Math.max(1, WxTrainConfig.sanditeSand * 4)));
        WxTrainData.Run s = selected();
        String[] m = new String[6];
        if (!on) Arrays.fill(m, "");
        else if (s == null) { m[0] = "NO RUNS"; m[1] = "PLAN PLOUGH / SANDITE / DE-ICE"; m[2] = "AUTO KEY SCHEDULES ON FORECAST"; m[3] = "SAND IN STOCK " + sand(); m[4] = ""; m[5] = ""; }
        else {
            m[0] = "RUN " + s.id + " " + WxTrains.KINDS[s.kind];
            m[1] = WxTrainData.STATES[s.state] + (s.auto ? " AUTO" : "");
            m[2] = "TRAIN " + (s.locoName.isEmpty() ? "NOT CALLED" : s.locoName);
            m[3] = "CELLS " + s.done.size() + " OF " + s.targets.size() + " TREATED";
            m[4] = "AGE " + (world.getTotalWorldTime() - s.created) / 1200 + " MIN";
            m[5] = s.why;
        }
        for (int i = 0; i < 6; i++) ch |= text("m" + (i + 1), m[i]);
        int view = Math.max(0, Math.min(2, ctl.get("s_view")));
        List<String> p = new ArrayList<>();
        if (on) {
            WxTrainData td = WxTrainData.get(world);
            if (view == 0 && f != null) { p.add("FORECAST " + range() + "M"); for (int k = 0; k < 3; k++) p.add(WxTrains.SHORT[k] + " " + f.risk[k] + "% " + f.why[k]); p.add("COLD SNAP " + (f.coldSnap ? "YES" : "NO")); }
            else if (view == 1) { p.add("WEATHER TRAINS"); List<String[]> rows = new ArrayList<>(); WxTrains.env(world, rows); for (String[] r : rows) if (r[0].equals("WV") && p.size() < 6) p.add(r[2] + " " + r[1] + " " + r[4] + "KMH " + r[6]); }
            else { p.add("LOG"); for (String l : td.log) { if (p.size() >= 6) break; p.add(l); } }
        }
        for (int i = 0; i < 6; i++) ch |= text("p" + (i + 1), i < p.size() ? p.get(i) : "");
        return ch;
    }

    // ---- save ------------------------------------------------------------------------------------------------------------
    @Override protected void writeExtra(NBTTagCompound t) { mains.write(t); t.setInteger("sel", sel); t.setLong("lastAuto", lastAuto); }
    @Override protected void readExtra(NBTTagCompound t) { mains.read(t); sel = t.getInteger("sel"); lastAuto = t.getLong("lastAuto"); }

    public BlockPos at() { return pos; }
}
