package com.dogpound.railmap.ops6;

import cam72cam.immersiverailroading.entity.Locomotive;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.sound.PackSounds;
import com.dogpound.railmap.track.Rails;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.*;

/**
 * RCO - remote control operation of a yard locomotive from a beltpack (batch 6 E #21). One session per paired loco:
 * <ul>
 *   <li>SPEED mode (the real yard way): direction F / N / R and a speed selector STOP - COAST - COUPLE (2 mph) - 4 - 7 -
 *   10 mph; the on-board controller works throttle and train brake to hold it (never over Ops6Config.rcoMaxKmh);</li>
 *   <li>THROTTLE mode: notches 0-8 and train brake 0-10 by hand, overspeed = penalty brake;</li>
 *   <li>independent brake lever, horn, bell, sand, and the safety: VIGILANCE (no input for rcoVigilanceS while moving -
 *   5 s of warning beeps - penalty brake), TILT (operator falling, lying down, under water: 2 s - emergency), RANGE
 *   (further than rcoRange from the loco: comms lost - emergency), BELTPACK gone from the operator / operator gone -
 *   emergency. A penalty or emergency latches until RESET, with the speed selector at STOP.</li>
 * </ul>
 * Every stop raises an alarm and goes on the event recorder. Locos left under remote control when the server stopped
 * get a penalty brake on the next start (RcoData).
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class Rco {
    private Rco() {}

    public static final String[] SPEED = {"STOP", "COAST", "COUPLE", "4 MPH", "7 MPH", "10 MPH"};
    static final double[] SPEED_KMH = {0, 0, 3.2, 6.4, 11.3, 16.1};
    static final String[] DIR = {"REV", "NEUT", "FWD"};

    static final class Session {
        UUID loco, player;
        String playerName = "", locoName = "";
        int serial, mode, dir, spd, notch, brake, indep, tiltT;
        long lastInput, hornUntil;
        boolean bell, sand, vigWarn;
        String latched = "";           // "" running, else "EMERGENCY: why" / "PENALTY: why"
        double integ, kmh, dist;
        final Deque<String> log = new ArrayDeque<>();
        void note(World w, String s) { log.addFirst(com.dogpound.railmap.depot.DepotClock.hhmm(w) + " " + s); while (log.size() > 10) log.removeLast(); }
    }

    private static final Map<UUID, Session> BY_LOCO = new HashMap<>();

    public static String owner(UUID loco) { Session s = BY_LOCO.get(loco); return s == null ? null : s.playerName; }

    static void paired(World w, Locomotive l, EntityPlayerMP p, int serial) {
        Session s = BY_LOCO.computeIfAbsent(l.getUUID(), k -> new Session());
        s.loco = l.getUUID(); s.player = p.getUniqueID(); s.playerName = p.getName(); s.locoName = Rails.name(l); s.serial = serial;
        s.lastInput = w.getTotalWorldTime(); s.dir = 1; s.spd = 0; s.mode = 0; s.latched = ""; s.notch = 0; s.brake = 0; s.indep = 0;
        s.note(w, "PAIRED " + p.getName());
        RcoData.get(w).locos.add(l.getUUID()); RcoData.get(w).markDirty();
        Recorder.event(w, "RCO: beltpack " + serial + " paired to " + s.locoName + " by " + p.getName());
    }

    static void release(World w, UUID loco, EntityPlayer p, String why) {
        Session s = BY_LOCO.remove(loco);
        RcoData d = RcoData.get(w);
        if (d.locos.remove(loco)) d.markDirty();
        Locomotive l = com.dogpound.railmap.auto.Autopilot.find(w, loco);
        if (l != null) { l.setThrottle(0); l.setTrainBrake(Math.max(l.getTrainBrakePos(), 0.6f)); l.setIndependentBrake(1); }
        if (s != null) Recorder.event(w, "RCO: " + s.locoName + " released from remote control (" + why + ") - brakes applied");
    }

    /** the beltpack in this player's inventory paired to that loco */
    static ItemStack pack(EntityPlayer p, UUID loco) {
        for (ItemStack s : p.inventory.mainInventory) if (s.getItem() instanceof ItemBeltpack && loco.equals(ItemBeltpack.loco(s))) return s;
        for (ItemStack s : p.inventory.offHandInventory) if (s.getItem() instanceof ItemBeltpack && loco.equals(ItemBeltpack.loco(s))) return s;
        return null;
    }

    static ItemStack anyPack(EntityPlayer p) {
        ItemStack h = p.getHeldItemMainhand();
        if (h.getItem() instanceof ItemBeltpack) return h;
        h = p.getHeldItemOffhand();
        if (h.getItem() instanceof ItemBeltpack) return h;
        for (ItemStack s : p.inventory.mainInventory) if (s.getItem() instanceof ItemBeltpack && ItemBeltpack.loco(s) != null) return s;
        return null;
    }

    /** the player's session (made from their paired beltpack after a restart, if the loco is free) */
    static Session session(EntityPlayerMP p) {
        ItemStack st = anyPack(p);
        UUID loco = st == null ? null : ItemBeltpack.loco(st);
        if (loco == null) return null;
        Session s = BY_LOCO.get(loco);
        if (s != null) return p.getUniqueID().equals(s.player) ? s : null;
        Locomotive l = com.dogpound.railmap.auto.Autopilot.find(p.world, loco);
        if (l == null || p.getDistanceSq(l.getPosition().x, l.getPosition().y, l.getPosition().z) > (double) Ops6Config.rcoRange * Ops6Config.rcoRange) return null;
        paired(p.world, l, p, ItemBeltpack.serial(st));
        return BY_LOCO.get(loco);
    }

    // ---- beltpack buttons ----------------------------------------------------------------------------------------------
    static void command(EntityPlayerMP p, String c, int v) {
        Session s = session(p);
        if (s == null) { p.sendStatusMessage(new TextComponentString(anyPack(p) == null ? "§7No paired beltpack" : "§cNo link: the loco is not loaded or out of range"), true); return; }
        World w = p.world;
        s.lastInput = w.getTotalWorldTime();
        s.vigWarn = false;
        String say = null;
        switch (c) {
            case "dir" -> { int d = Math.max(-1, Math.min(1, v)); if (d != s.dir && s.kmh > 0.5) say = "§cStop first - direction changes at standstill"; else s.dir = d; }
            case "spd" -> s.spd = Math.max(0, Math.min(SPEED.length - 1, v));
            case "mode" -> { if (s.kmh > 0.5) say = "§cStop first to change mode"; else { s.mode = v == 1 ? 1 : 0; s.notch = 0; s.integ = 0; } }
            case "notch" -> s.notch = Math.max(0, Math.min(8, v));
            case "brake" -> s.brake = Math.max(0, Math.min(10, v));
            case "indep" -> s.indep = Math.max(0, Math.min(4, v));
            case "horn" -> s.hornUntil = w.getTotalWorldTime() + 30;
            case "bell" -> s.bell = !s.bell;
            case "sand" -> s.sand = !s.sand;
            case "vig" -> say = "§7Vigilance acknowledged";
            case "estop" -> latch(w, s, "EMERGENCY: operator E-STOP", AlarmManager.Sev.MINOR);
            case "reset" -> {
                if (s.latched.isEmpty()) say = "§7Nothing to reset";
                else if (s.spd != 0 || s.notch != 0) say = "§cSpeed selector to STOP and notch 0 to reset";
                else if (s.dist > Ops6Config.rcoRange) say = "§cStill out of range";
                else { s.note(w, "RESET"); Recorder.event(w, "RCO: " + s.locoName + " reset by " + s.playerName + " after " + s.latched); s.latched = ""; s.integ = 0; say = "§aReset - remote control live"; }
            }
            case "release" -> { release(w, s.loco, p, "released by " + p.getName()); ItemStack st = anyPack(p); if (st != null && st.getTagCompound() != null) { st.getTagCompound().removeTag("locoMost"); st.getTagCompound().removeTag("locoLeast"); } say = "§eBeltpack released the loco"; }
            default -> { }
        }
        if (say != null) p.sendStatusMessage(new TextComponentString(say), true);
    }

    static void latch(World w, Session s, String why, AlarmManager.Sev sev) {
        if (!s.latched.isEmpty()) return;
        s.latched = why;
        s.note(w, why);
        Locomotive l = com.dogpound.railmap.auto.Autopilot.find(w, s.loco);
        BlockPos at = l == null ? BlockPos.ORIGIN : new BlockPos(l.getPosition().x, l.getPosition().y, l.getPosition().z);
        Alarms6.trip(w, "rco:" + s.loco, AlarmManager.Cat.INCIDENT, sev, "RCO " + s.locoName + ": " + why + " (operator " + s.playerName + ")", at);
        if (l != null) PackSounds.play(w, l.getPosition().x, l.getPosition().y, l.getPosition().z, why.startsWith("EMERGENCY") ? "cab.brake_emergency" : "cab.brake_apply", 1.2f, 1f);
        EntityPlayer p = w.getPlayerEntityByUUID(s.player);
        if (p != null) { p.sendStatusMessage(new TextComponentString("§c[RCO] " + why + " - speed selector to STOP, then RESET"), true); PackSounds.local(p, "beep_error", 1f, 1f); }
    }

    // ---- the on-board controller + safety, every 2 ticks ---------------------------------------------------------------
    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        net.minecraft.server.MinecraftServer srv = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (srv == null || srv.getTickCounter() % 2 != 0) return;
        if (srv.getTickCounter() % 200 == 0) orphans(srv);
        if (BY_LOCO.isEmpty()) return;
        for (Session s : new ArrayList<>(BY_LOCO.values())) {
            try { step(srv, s); } catch (LinkageError | RuntimeException ex) { RailMap.LOG.warn("[IR Extras] RCO step failed: {}", ex.toString()); }
        }
    }

    static void step(net.minecraft.server.MinecraftServer srv, Session s) {
        EntityPlayerMP p = srv.getPlayerList().getPlayerByUUID(s.player);
        World w = null;
        Locomotive l = null;
        for (World ws : srv.worlds) { l = com.dogpound.railmap.auto.Autopilot.find(ws, s.loco); if (l != null) { w = ws; break; } }
        if (l == null || l.isDead()) { BY_LOCO.remove(s.loco); return; }
        long now = w.getTotalWorldTime();
        s.kmh = l.getCurrentSpeed() == null ? 0 : Math.abs(l.getCurrentSpeed().metric());
        s.locoName = Rails.name(l);
        if (p == null || p.world != w) { latch(w, s, "EMERGENCY: comms lost (operator gone)", AlarmManager.Sev.MAJOR); s.dist = 1e9; }
        else {
            s.dist = Math.sqrt(p.getDistanceSq(l.getPosition().x, l.getPosition().y, l.getPosition().z));
            if (s.dist > Ops6Config.rcoRange) latch(w, s, "EMERGENCY: out of radio range (" + Math.round(s.dist) + " m)", AlarmManager.Sev.MAJOR);
            if (pack(p, s.loco) == null) latch(w, s, "EMERGENCY: beltpack not on the operator", AlarmManager.Sev.MAJOR);
            boolean tilt = p.fallDistance > 3 || p.isPlayerSleeping() || p.isElytraFlying() || p.isInsideOfMaterial(Material.WATER) || p.isDead;
            s.tiltT = tilt ? s.tiltT + 2 : 0;
            if (s.tiltT == 20) PackSounds.local(p, "beep_error", 1f, 1.4f);
            if (s.tiltT >= 40) latch(w, s, "EMERGENCY: tilt switch (operator down)", AlarmManager.Sev.MAJOR);
        }
        long idle = now - s.lastInput, vig = Ops6Config.rcoVigilanceS * 20L;
        if (s.kmh > 0.5 && idle > vig && s.latched.isEmpty()) {
            s.vigWarn = true;
            if (p != null && idle % 20 < 2) PackSounds.local(p, "buzzer", 1f, 1.2f);
            if (idle > vig + 100) latch(w, s, "PENALTY: vigilance not acknowledged", AlarmManager.Sev.MINOR);
        } else if (s.kmh <= 0.5) { s.lastInput = Math.max(s.lastInput, now - vig / 2); s.vigWarn = false; }
        // the controls
        float throttle, brake, indep = s.indep / 4f;
        if (!s.latched.isEmpty()) {
            throttle = 0;
            brake = s.latched.startsWith("EMERGENCY") ? 1f : 0.8f;
            indep = 1;
        } else if (s.mode == 1) {
            throttle = s.dir == 0 ? 0 : s.notch / 8f;
            brake = s.brake / 10f;
            if (s.kmh > Ops6Config.rcoMaxKmh + 3) latch(w, s, "PENALTY: overspeed " + Math.round(s.kmh) + " km/h", AlarmManager.Sev.MINOR);
        } else {
            double target = Math.min(Ops6Config.rcoMaxKmh, SPEED_KMH[s.spd]);
            boolean wrongWay = s.kmh > 0.5 && Math.signum(l.getReverser()) != s.dir && s.dir != 0;
            if (s.spd == 0 || s.dir == 0 || wrongWay) {
                throttle = 0; s.integ = 0;
                brake = s.kmh > 0.5 ? (float) Math.min(0.7, 0.15 + s.kmh / 30) : 0.3f;
                if (s.kmh <= 0.5) indep = Math.max(indep, 1);
            } else if (s.spd == 1) {
                throttle = 0; brake = 0; s.integ = 0;
            } else {
                double err = target - s.kmh;
                s.integ = Math.max(0, Math.min(0.6, s.integ + err * 0.004));
                throttle = (float) Math.max(0, Math.min(1, err * 0.08 + s.integ));
                brake = err < -1.5 ? (float) Math.min(0.6, -err * 0.08) : 0;
                if (brake > 0) throttle = 0;
            }
        }
        if (s.kmh <= 0.5 && s.dir != 0 && s.latched.isEmpty()) l.setReverser(s.dir);
        else if (s.dir == 0 && s.kmh <= 0.5) l.setReverser(0);
        l.setThrottle(throttle);
        l.setTrainBrake(brake);
        l.setIndependentBrake(indep);
        if (now < s.hornUntil) l.setHorn(4, 1f);
        if (s.bell && l.getBell() <= 0) l.setBell(200); else if (!s.bell && l.getBell() > 0) l.setBell(0);
        try { l.setSanding(s.sand); } catch (LinkageError | RuntimeException ignored) { }
    }

    /** locos left under remote control by a server stop: penalty brake them (once per 10 s check) */
    static void orphans(net.minecraft.server.MinecraftServer srv) {
        for (World w : srv.worlds) {
            RcoData d = RcoData.get(w);
            if (d.locos.isEmpty()) continue;
            for (UUID u : new ArrayList<>(d.locos)) {
                if (BY_LOCO.containsKey(u)) continue;
                Locomotive l = com.dogpound.railmap.auto.Autopilot.find(w, u);
                if (l == null) continue;
                l.setThrottle(0); l.setTrainBrake(0.8f); l.setIndependentBrake(1);
                d.locos.remove(u); d.markDirty();
                Recorder.event(w, "RCO: " + Rails.name(l) + " had no beltpack link after a restart - penalty brake");
            }
        }
    }

    // ---- what the beltpack face shows -------------------------------------------------------------------------------
    static NBTTagCompound state(EntityPlayerMP p) {
        NBTTagCompound t = new NBTTagCompound();
        ItemStack st = anyPack(p);
        t.setBoolean("has", st != null);
        if (st == null) return t;
        t.setInteger("serial", ItemBeltpack.serial(st));
        Session s = session(p);
        t.setBoolean("paired", ItemBeltpack.loco(st) != null);
        t.setString("loco", ItemBeltpack.locoName(st));
        t.setBoolean("link", s != null);
        if (s == null) return t;
        Locomotive l = com.dogpound.railmap.auto.Autopilot.find(p.world, s.loco);
        t.setInteger("mode", s.mode); t.setInteger("dir", s.dir); t.setInteger("spd", s.spd); t.setInteger("notch", s.notch); t.setInteger("brake", s.brake); t.setInteger("indep", s.indep);
        t.setBoolean("bell", s.bell); t.setBoolean("sand", s.sand); t.setBoolean("vig", s.vigWarn); t.setBoolean("tilt", s.tiltT >= 20);
        t.setString("latched", s.latched);
        t.setDouble("kmh", s.kmh); t.setDouble("dist", s.dist); t.setInteger("range", Ops6Config.rcoRange);
        long idle = p.world.getTotalWorldTime() - s.lastInput;
        t.setInteger("vigLeft", (int) Math.max(0, (Ops6Config.rcoVigilanceS * 20L + 100 - idle) / 20));
        if (l != null) {
            t.setFloat("thr", l.getThrottle()); t.setFloat("tb", l.getTrainBrakePos()); t.setFloat("ib", l.getIndependentBrake());
            try { t.setFloat("air", l.getMainAirReservoir()); } catch (LinkageError | RuntimeException ignored) { }
            t.setFloat("rev", l.getReverser());
        }
        t.setDouble("target", Math.min(Ops6Config.rcoMaxKmh, SPEED_KMH[s.spd]));
        NBTTagList log = new NBTTagList();
        for (String x : s.log) log.appendTag(new NBTTagString(x));
        t.setTag("log", log);
        return t;
    }

    /** /irextras rco list */
    static List<String> list() {
        List<String> l = new ArrayList<>();
        for (Session s : BY_LOCO.values()) l.add(String.format(Locale.ROOT, "%s ← %s #%d · %s %s · %.1f km/h · %.0f m · %s", s.locoName, s.playerName, s.serial, s.mode == 0 ? "SPEED " + SPEED[s.spd] : "THROTTLE N" + s.notch,
                DIR[s.dir + 1], s.kmh, s.dist, s.latched.isEmpty() ? "LIVE" : s.latched));
        return l;
    }

    /** /irextras rco estop: every session */
    static int estopAll(World w) { int n = 0; for (Session s : BY_LOCO.values()) { latch(w, s, "EMERGENCY: control room stop", AlarmManager.Sev.MINOR); n++; } return n; }

    /** remembers which locos are under remote control, so a restart doesn't leave one running */
    public static final class RcoData extends WorldSavedData {
        static final String NAME = "irextras_rco";
        final Set<UUID> locos = new HashSet<>();
        public RcoData() { super(NAME); }
        public RcoData(String n) { super(n); }
        @Override public void readFromNBT(NBTTagCompound t) { NBTTagList l = t.getTagList("l", 8); for (int i = 0; i < l.tagCount(); i++) try { locos.add(UUID.fromString(l.getStringTagAt(i))); } catch (IllegalArgumentException ignored) { } }
        @Override public NBTTagCompound writeToNBT(NBTTagCompound t) { NBTTagList l = new NBTTagList(); for (UUID u : locos) l.appendTag(new NBTTagString(u.toString())); t.setTag("l", l); return t; }
        static RcoData get(World w) {
            MapStorage s = w.getPerWorldStorage();
            RcoData d = (RcoData) s.getOrLoadData(RcoData.class, NAME);
            if (d == null) { d = new RcoData(); s.setData(NAME, d); }
            return d;
        }
    }
}
