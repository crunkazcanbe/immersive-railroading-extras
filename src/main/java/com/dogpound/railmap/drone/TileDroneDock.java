package com.dogpound.railmap.drone;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.block.IFurniture;
import com.dogpound.railmap.grid.Elec;
import com.dogpound.railmap.infra.InfraTile;
import com.dogpound.railmap.infra.Supply;
import com.dogpound.railmap.sound.PackSounds;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * Drone Dock (her IR Extras list #2): the weatherproof base an inspection drone lives in. On top a landing / charging
 * pad with edge lights; inside a six-pack battery bay (real storage: the charger charges flight batteries in it) and
 * the airframe hangar (the drone item); a wind / rain mast; and the control panel: screen, mission selectors (TRACK /
 * OHLE / STRUCTURES / TUNNEL, length, direction, height), ARM key, AUTO patrol key + interval, charge mode, LAUNCH /
 * RETURN / HOLD / RESUME, E-STOP (emergency landing), PRINT (the last flight's report), a lamp per battery slot and
 * gauges. Electronics on 12 V; the charger and pad on a 120 / 240 V AC supply (a grid cable touching it): no 12 V =
 * dead, no mains = no charging, overvoltage burns it (screwdriver repairs).
 */
public class TileDroneDock extends InfraTile implements IFurniture {
    /** the pad top, blocks above the dock's base */
    public static final double PAD_Y = 11 / 16.0;
    static final String[] LENS = {"100", "250", "500", "1000", "2000"};
    static final String[] ALTS = {"AUTO", "5", "10", "20", "40"};
    static final String[] EVERY = {"OFF", "6H", "12H", "24H"};

    private final InventoryBasic bay = new InventoryBasic("Battery bay", true, 6), hangar = new InventoryBasic("Airframe hangar", true, 1);
    private UUID droneId;
    public int camNo;
    private int flightId;
    private String serial = "";
    private long lastAuto, autoFail;
    private double kw;
    private int charging;

    public TileDroneDock() {
        super(new Supply(Elec.Level.AC120, Elec.Level.AC240));
        bay.addInventoryChangeListener(i -> markDirty());
        hangar.addInventoryChangeListener(i -> markDirty());
    }

    public String serial() { return serial.isEmpty() ? "DOCK" : serial; }
    public boolean flying() { return droneId != null; }
    public EntityDrone drone() { return droneId == null || world == null ? null : Drones.byUuid(world, droneId.toString()); }
    public int missionSel() { return Math.max(0, Math.min(3, ctl.get("s_mission"))); }

    @Override public String wiredName() { return "Drone dock " + serial(); }

    @Override
    public String gearState() {
        EntityDrone d = drone();
        return mains.status().replaceAll("§.", "") + " · " + (d != null ? EntityDrone.MODES[d.mode()] + String.format(Locale.ROOT, " %.0f%%", d.battery()) : flying() ? "drone out of range" : ready() == null ? "ready" : ready()) + " · " + bayLine();
    }

    @Override public int wiredLamp() { return !powered() ? 3 : ctl.get("m_estop") == 1 || mains.burnt ? 3 : flying() ? 2 : 1; }

    @Override
    public void gearInfo(Map<String, Object> m) {
        EntityDrone d = drone();
        m.put("flying", d != null);
        m.put("battery", d == null ? -1 : d.battery());
        m.put("bayAvg", bayAvg());
        m.put("chargerKw", kw);
    }

    // ---- storage ---------------------------------------------------------------------------------------------------------
    @Override public IInventory storage(int k) { return k == 0 ? bay : k == 1 ? hangar : null; }
    @Override public String storeName(int k) { return k == 0 ? "Battery bay (charger)" : "Airframe hangar"; }
    @Override public int storeSize(int k) { return k == 0 ? 6 : 1; }

    @Override
    public void dropAll() {
        InventoryHelper.dropInventoryItems(world, pos, bay);
        InventoryHelper.dropInventoryItems(world, pos, hangar);
    }

    String bayLine() {
        int n = 0;
        for (int i = 0; i < 6; i++) if (DroneItems.Battery.is(bay.getStackInSlot(i))) n++;
        return n == 0 ? "no packs" : String.format(Locale.ROOT, "%d pack%s %.0f%%", n, n == 1 ? "" : "s", bayAvg());
    }

    private double bayAvg() {
        double s = 0;
        int n = 0;
        for (int i = 0; i < 6; i++) { ItemStack b = bay.getStackInSlot(i); if (DroneItems.Battery.is(b)) { s += DroneItems.Battery.pct(b); n++; } }
        return n == 0 ? 0 : s / n;
    }

    private int bestPack() {
        int best = -1;
        float bp = 0;
        for (int i = 0; i < 6; i++) { ItemStack b = bay.getStackInSlot(i); if (DroneItems.Battery.is(b) && DroneItems.Battery.pct(b) > bp) { bp = DroneItems.Battery.pct(b); best = i; } }
        return best;
    }

    /** null = ready to launch, else why not */
    String ready() {
        if (!DroneConfig.drones) return "drones off (config)";
        if (!powered()) return "no 12 V";
        if (ctl.get("m_estop") == 1) return "E-STOP latched";
        if (flying()) return "drone flying";
        if (!(hangar.getStackInSlot(0).getItem() instanceof DroneItems.Airframe)) return "hangar empty";
        int b = bestPack();
        if (b < 0) return "no battery";
        if (DroneItems.Battery.pct(bay.getStackInSlot(b)) < 40) return "batteries below 40%";
        return null;
    }

    // ---- tick ------------------------------------------------------------------------------------------------------------
    @Override
    protected void serverTick() {
        if (serial.isEmpty() && age > 2) { DroneData d = DroneData.get(world); serial = String.format(Locale.ROOT, "DK-%03d", d.nextDrone++); d.markDirty(); markDirty(); }
        if (age % 20 == 7) second();
        if (age % 10 == 3) readouts();
    }

    private void second() {
        // the charger: up to two packs at once, NORMAL or FAST, on the mains it is fed
        int mode = Math.max(0, Math.min(2, ctl.get("s_charge")));
        List<Integer> todo = new ArrayList<>();
        for (int i = 0; i < 6 && todo.size() < 2; i++) { ItemStack b = bay.getStackInSlot(i); if (mode > 0 && DroneItems.Battery.is(b) && DroneItems.Battery.pct(b) < 99.9f) todo.add(i); }
        boolean mcb = com.dogpound.railmap.cabinet.Cabinet.ok(ctl, "main");      // the charger cabinet's mains MCB / fuse
        double watts = !mcb ? 0 : 60 + (powered() && mode > 0 ? todo.size() * (mode == 2 ? 700 : 350) : 0);
        double share = mainsTick(watts);
        kw = mains.ok && mcb ? watts / 1000.0 * share : 0;
        charging = 0;
        if (powered() && mains.ok && mcb && share > 0) {
            double rate = 100.0 / (DroneConfig.chargeMin * 60.0) * (mode == 2 ? 2 : 1) * share;
            for (int i : todo) {
                ItemStack b = bay.getStackInSlot(i);
                float before = DroneItems.Battery.pct(b);
                DroneItems.Battery.set(b, (float) (before + rate * (before > 80 ? 0.5 : 1)));   // CV phase above 80 %: slower
                DroneItems.Battery.age(b, rate / 100.0 * (mode == 2 ? 1.6 : 1));
                charging++;
            }
            if (charging > 0) markDirty();
        }
        // a drone that ended its flight elsewhere (crashed, picked up) frees the dock
        if (droneId != null && drone() == null) {
            DroneData.Flight f = DroneData.get(world).flight(flightId);
            if (f == null || f.end != 0) droneId = null;
        }
        // AUTO patrols
        int every = new int[]{0, 6000, 12000, 24000}[Math.max(0, Math.min(3, ctl.get("s_every")))];
        long now = world.getTotalWorldTime();
        if (ctl.get("k_auto") == 1 && every > 0 && droneId == null && now - lastAuto >= every && now - autoFail > 1200) {
            String r = launch(null, true);
            if (r.startsWith("§a")) lastAuto = now;
            else autoFail = now;
        }
        // standing alarms
        AlarmManager am = AlarmManager.get(world);
        boolean dead = ctl.get("m_estop") == 1 || mains.burnt;
        if (dead) am.raise(world, "dronedock:" + pos.toLong(), AlarmManager.Cat.MACHINE, AlarmManager.Sev.MINOR, wiredName() + (mains.burnt ? " BURNT OUT" : " E-STOP latched"), pos, true);
        else am.clear("dronedock:" + pos.toLong());
    }

    private static String clean(String s) { return s.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9 .:>$/%+-]", ""); }

    private void readouts() {
        boolean on = powered();
        EntityDrone d = drone();
        String g = Drones.grounded(world, pos);
        double wind = Drones.wind(world, pos)[0];
        put("l_pwr", on); put("l_mains", mains.ok); put("l_chg", charging > 0);
        put("l_ready", ready() == null); put("l_air", d != null); put("l_rth", d != null && (d.mode() == EntityDrone.RTH || d.mode() == EntityDrone.LAND));
        put("l_wx", g != null); put("l_fault", ctl.get("m_estop") == 1 || mains.burnt || !mains.ok && mains.connected);
        put("l_low", d != null && d.battery() < 30); put("l_auto", ctl.get("k_auto") == 1 && ctl.get("s_every") > 0);
        put("l_pad", on && (d != null || ctl.get("pad") == 1));
        for (int i = 0; i < 6; i++) {
            ItemStack b = bay.getStackInSlot(i);
            boolean has = DroneItems.Battery.is(b);
            put("bf" + (i + 1), has && DroneItems.Battery.pct(b) >= 99.5f);
            put("bc" + (i + 1), has && DroneItems.Battery.pct(b) < 99.5f && charging > 0 && mains.ok);
        }
        put("g_bat", d == null ? 0 : Math.round(d.battery()));
        put("g_bay", (int) Math.round(bayAvg()));
        put("g_wind", (int) Math.round(Math.min(100, wind / 25 * 100)));
        put("g_kw", (int) Math.round(Math.min(100, kw / 1.5 * 100)));
        ItemStack af = hangar.getStackInSlot(0);
        text("s1", clean(serial() + " " + (ctl.get("k_arm") == 1 ? "ARMED" : "SAFE") + (ctl.get("m_estop") == 1 ? " ESTOP" : "")));
        text("s2", clean(d != null ? d.droneName() + " " + EntityDrone.MODES[d.mode()] + String.format(Locale.ROOT, " %.0f%% %dM", d.battery(), Math.round(d.posY - pos.getY()))
                : flying() ? "DRONE OUT OF RANGE" : af.isEmpty() ? "HANGAR EMPTY" : "HANGAR " + (DroneItems.Airframe.serial(af).isEmpty() ? "NEW DRONE" : DroneItems.Airframe.serial(af)) + (ready() == null ? " READY" : "")));
        text("s3", clean("MSN " + DroneData.MISSIONS[missionSel()] + " " + LENS[Math.max(0, Math.min(4, ctl.get("s_len")))] + "M DIR " + (ctl.get("s_dir") == 1 ? "B" : "A") + " ALT " + ALTS[Math.max(0, Math.min(4, ctl.get("s_alt")))]));
        text("s4", clean(g != null ? "WX HOLD: " + g : String.format(Locale.ROOT, "WIND %.0f M/S FLY OK", wind)));
        text("s5", clean("BAY " + bayLine() + (charging > 0 ? " CHG " + charging : "")));
        text("s6", clean(mains.ok ? String.format(Locale.ROOT, "SUPPLY %s %.0fW", mains.level == null ? "" : mains.level.label, kw * 1000) : mains.what));
        DroneData.Flight f = DroneData.get(world).flight(flightId);
        text("s7", clean(f == null ? "NO FLIGHTS YET" : String.format(Locale.ROOT, "FLT %d %.0fM FOUND %d", f.id, f.metres, f.findings)));
        text("s8", clean(ctl.get("k_auto") == 1 ? "AUTO PATROL EVERY " + EVERY[Math.max(0, Math.min(3, ctl.get("s_every")))] : "AUTO OFF"));
    }

    // ---- controls --------------------------------------------------------------------------------------------------------
    @Override
    public String controlPress(String id, EntityPlayer p) {
        switch (id) {
            case "bay": case "hangar": {
                if (p == null) return "§7The hatches are opened by hand";
                p.openGui(RailMap.instance, RailMap.GUI_OFFICE_STORE + (id.equals("bay") ? 0 : 1), world, pos.getX(), pos.getY(), pos.getZ());
                PackSounds.play(world, pos, "ui.drawer_open", 0.6f, id.equals("bay") ? 0.9f : 0.7f);
                return "";
            }
            case "m_estop": {
                int v = ctl.get(id) == 1 ? 0 : 1;
                ctl.set(id, v);
                EntityDrone d = drone();
                if (v == 1 && d != null && d.mode() != EntityDrone.LANDED_OUT) d.landOut("E-STOP at the dock");
                Recorder.event(world, wiredName() + (v == 1 ? " E-STOP" : " E-STOP reset") + " by " + who(p));
                return v == 1 ? "§cE-STOP: dock locked out" + (d != null ? ", drone making an emergency landing" : "") : "§aE-STOP reset";
            }
            case "test": return lampTest();
            case "pad": ctl.set("pad", ctl.get("pad") == 1 ? 0 : 1); return ctl.get("pad") == 1 ? "§ePad lights ON" : "§7Pad lights auto";
            case "print": {
                if (p == null) return "§7Printing is done at the dock";
                if (!powered()) return "§cThe dock is dark - no 12 V";
                DroneData dd = DroneData.get(world);
                DroneData.Flight f = dd.flight(flightId);
                if (f == null) return "§7No flight to report";
                List<String> lines = new ArrayList<>();
                lines.add("DRONE FLIGHT " + f.id + " - " + f.drone);
                lines.add("Mission " + DroneData.MISSIONS[Math.max(0, Math.min(3, f.mission))] + String.format(Locale.ROOT, ", %.0f m, max %.0f m up", f.metres, f.maxAlt));
                lines.add(String.format(Locale.ROOT, "Lowest battery %.0f%%", f.minBattery));
                lines.add(f.end == 0 ? "IN FLIGHT" : f.result);
                lines.add(f.findings + " finding(s):");
                for (DroneData.Finding x : dd.findings) if (x.flight == f.id) lines.add("#" + x.id + " " + x.kind + ": " + x.text);
                ItemStack b = com.dogpound.railmap.depot.Diagnostics.book("Drone flight " + f.id, serial(), lines);
                if (!p.inventory.addItemStackToInventory(b)) p.dropItem(b, false);
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.PRINTER, SoundCategory.BLOCKS, 0.6f, 1f);
                return "§aFlight " + f.id + " report printed";
            }
            case "launch": case "rth": case "hold": case "resume": return remote(id, p);
            default: return null;                                                   // selectors and keys turn
        }
    }

    /** launch / rth / hold / resume, from the panel, the console or a handheld */
    public String remote(String op, EntityPlayer p) {
        if (!powered()) return "§cThe dock is dark - no 12 V";
        EntityDrone d = drone();
        switch (op) {
            case "launch": return launch(p, false);
            case "rth": if (d == null) return "§7Nothing flying"; d.rth("RETURN pressed by " + who(p)); return "§e" + d.droneName() + " returning home";
            case "hold": if (d == null) return "§7Nothing flying"; d.hold(); return "§e" + d.droneName() + " holding position";
            case "resume": if (d == null) return "§7Nothing flying"; d.resumeMission(); return "§a" + d.droneName() + " " + EntityDrone.MODES[d.mode()].toLowerCase(Locale.ROOT);
            default: return "§7?";
        }
    }

    String launch(EntityPlayer who, boolean auto) {
        String no = ready();
        if (no != null) return "§c" + no;
        if (ctl.get("k_arm") != 1) return "§cTurn the ARM key first";
        String g = Drones.grounded(world, pos);
        if (g != null) return "§cWEATHER HOLD: " + g;
        int mission = missionSel();
        int len = Integer.parseInt(LENS[Math.max(0, Math.min(4, ctl.get("s_len")))]);
        int altSel = Math.max(0, Math.min(4, ctl.get("s_alt")));
        double alt = altSel == 0 ? new double[]{6, 10, 8, 3}[mission] : Double.parseDouble(ALTS[altSel]);
        Object r = DroneRoute.plan(world, pos, controlFacing(), mission, len, ctl.get("s_dir") == 1, alt);
        if (r instanceof String s) return "§c" + s;
        double[] way = (double[]) r;
        // the drone takes the fullest pack; the airframe leaves the hangar
        int b = bestPack();
        ItemStack pack = bay.removeStackFromSlot(b), af = hangar.removeStackFromSlot(0);
        DroneData dd = DroneData.get(world);
        if (DroneItems.Airframe.serial(af).isEmpty()) DroneItems.Airframe.serial(af, String.format(Locale.ROOT, "D-%02d", dd.nextDrone++));
        EntityDrone d = new EntityDrone(world);
        Vec3d pad = new Vec3d(pos.getX() + 0.5, pos.getY() + PAD_Y + 0.02, pos.getZ() + 0.5);
        d.setPosition(pad.x, pad.y, pad.z);
        d.rotationYaw = controlFacing().getHorizontalAngle();
        d.dock = pos;
        d.mission = mission;
        d.alt = alt;
        d.way = way;
        d.airframe = af;
        d.pack = pack;
        d.droneName(DroneItems.Airframe.serial(af));
        d.battery(DroneItems.Battery.pct(pack));
        d.gimbal(new float[]{60, 25, 40, 15}[mission]);
        d.mode(EntityDrone.TAKEOFF);
        DroneData.Flight f = dd.newFlight(world, pos.toLong(), mission, d.droneName());
        d.flight = f.id;
        world.spawnEntity(d);
        d.camNo = Drones.registerCam(world, d, camNo);
        camNo = d.camNo;
        droneId = d.getUniqueID();
        flightId = f.id;
        String msg = String.format(Locale.ROOT, "%s launched from %s: %s mission, %d m, %.0f m up%s", d.droneName(), serial(), DroneData.MISSIONS[mission], len, alt, auto ? " (AUTO patrol)" : "");
        dd.log(world, msg);
        Recorder.event(world, "DRONE " + msg);
        PackSounds.play(world, pos, "machine.servo", 0.6f, 1.4f);
        markDirty();
        return "§a" + msg + " - CAM " + camNo;
    }

    /** the drone came home: airframe back in the hangar, pack back in the bay */
    void recover(EntityDrone d) {
        DroneItems.Airframe.addHours(d.airframe, d.ticksExisted / 72000.0);
        if (!d.airframe.isEmpty()) { if (hangar.getStackInSlot(0).isEmpty()) hangar.setInventorySlotContents(0, d.airframe.copy()); else InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY() + 1, pos.getZ(), d.airframe.copy()); }
        if (!d.pack.isEmpty()) {
            DroneItems.Battery.age(d.pack, Math.max(0, DroneItems.Battery.pct(d.pack) - d.battery()) / 100.0);
            DroneItems.Battery.set(d.pack, d.battery());
            boolean put = false;
            for (int i = 0; i < 6 && !put; i++) if (bay.getStackInSlot(i).isEmpty()) { bay.setInventorySlotContents(i, d.pack.copy()); put = true; }
            if (!put) InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY() + 1, pos.getZ(), d.pack.copy());
        }
        d.airframe = ItemStack.EMPTY; d.pack = ItemStack.EMPTY;
        droneId = null;
        PackSounds.play(world, pos, "machine.clunk", 0.5f, 1.2f);
        markDirty();
    }

    // ---- save ------------------------------------------------------------------------------------------------------------
    @Override
    protected void store(NBTTagCompound t) {
        t.setTag("bay", com.dogpound.railmap.block.TileFurniture.save(bay));
        t.setTag("hangar", com.dogpound.railmap.block.TileFurniture.save(hangar));
        if (droneId != null) t.setUniqueId("drone", droneId);
        t.setInteger("cam", camNo); t.setInteger("flight", flightId); t.setString("serial", serial); t.setLong("lastAuto", lastAuto);
    }

    @Override
    protected void restore(NBTTagCompound t) {
        com.dogpound.railmap.block.TileFurniture.load(bay, t.getCompoundTag("bay"));
        com.dogpound.railmap.block.TileFurniture.load(hangar, t.getCompoundTag("hangar"));
        droneId = t.hasUniqueId("drone") ? t.getUniqueId("drone") : null;
        camNo = t.getInteger("cam"); flightId = t.getInteger("flight"); serial = t.getString("serial"); lastAuto = t.getLong("lastAuto");
    }

    @Override public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() { return new net.minecraft.util.math.AxisAlignedBB(pos).grow(1, 3, 1); }

    public EnumFacing facing() { return controlFacing(); }
}
