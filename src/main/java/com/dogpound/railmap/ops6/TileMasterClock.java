package com.dogpound.railmap.ops6;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.grid.Elec;
import com.dogpound.railmap.infra.InfraTile;
import com.dogpound.railmap.infra.Supply;
import com.dogpound.railmap.sound.PackSounds;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Master Clock (batch 6 E #24): a GPS / radio-time receiver with an oven-controlled crystal and a slave impulse driver.
 * <ul>
 *   <li>SOURCE GPS needs the antenna to see the sky (nothing above the block): 30 s to a first fix, 4-11 satellites;
 *   RADIO (long-wave time signal) works indoors but needs two full minute frames (60 s) and is wiped out by
 *   thunderstorms; INTERNAL runs on the crystal only and is set by hand (ADV / RET, one game minute a press).</li>
 *   <li>Locked, its own time is held to the source; without a lock it is in HOLDOVER and its crystal drifts slowly
 *   (much slower than a slave's quartz movement).</li>
 *   <li>RUN key + live = OUTPUT: every slave clock in range is pulled onto the master's time at each impulse (SEC / 30S /
 *   MIN, the PULSE selector); SYNC pulses them all at once.</li>
 * </ul>
 * Alarms: master down (slaves free-running), holdover, slave faults (dark boards in range). 12 V + 120 / 240 V AC.
 */
public class TileMasterClock extends InfraTile implements PacketOps6.Screen {
    static final String[] SRC = {"GPS", "RADIO", "INTERNAL"}, PULSE = {"SEC", "30S", "MIN"};
    static final int[] PULSE_T = {20, 600, 1200};
    /** the master's own error, ticks (+ = fast) */
    float offset;
    int lockT;                 // seconds of continuous reception (a lock needs 30 s GPS / 60 s radio)
    boolean locked;
    long holdSince = -1;
    /** last second: slaves in range / in sync / dark (MasterClocks fills it) */
    final int[] count = new int[3];
    private final Deque<String> log = new ArrayDeque<>();

    public TileMasterClock() { super(new Supply(Elec.Level.AC120, Elec.Level.AC240)); }

    @Override public String wiredName() { return "Master clock " + pos.getX() + "," + pos.getZ(); }
    @Override public String gearState() { return !powered() ? "dark - no 12 V" : !live() ? "no supply: " + mains.what : SRC[src()] + " " + (locked ? "LOCKED" : src() == 2 ? "FREE" : "HOLDOVER") + " · " + time() + " · " + (outputting() ? "OUTPUT ON" : "output off") + " · slaves " + count[1] + "/" + count[0]; }
    @Override public int wiredLamp() { return !live() ? 3 : !locked && src() != 2 || count[2] > 0 ? 2 : 1; }

    int src() { return Math.floorMod(ctl.get("s_src"), 3); }
    int pulse() { return Math.floorMod(ctl.get("s_pulse"), 3); }
    boolean run() { return ctl.get("k_run") == 1; }
    public boolean outputting() { return live() && run(); }
    int pulseTicks() { return PULSE_T[pulse()]; }

    /** can the antenna hear the source right now (the sky / thunder) */
    boolean reception() {
        return switch (src()) {
            case 0 -> world.canSeeSky(pos.up());
            case 1 -> !world.isThundering();
            default -> false;
        };
    }

    int sats() { return reception() && src() == 0 ? 4 + (int) ((world.getTotalWorldTime() / 1200 + pos.toLong()) % 8) : 0; }

    String time() { return hms(world.getWorldTime() + Math.round(offset)); }

    static String hms(long worldTime) {
        long t = Math.floorMod(worldTime + 6000, 24000L);
        long secs = t * 3600 / 1000;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", secs / 3600, secs / 60 % 60, secs % 60);
    }

    void note(String s) { log.addFirst(time() + " " + s); while (log.size() > 14) log.removeLast(); }

    @Override
    protected void serverTick() {
        if (age % 20 != 5) return;
        mainsTick(live() ? 45 : 5);
        boolean wasLocked = locked;
        if (live() && reception()) lockT++; else lockT = 0;
        locked = live() && src() != 2 && lockT >= (src() == 0 ? 30 : 60);
        if (locked) { offset *= 0.5f; if (Math.abs(offset) < 0.05f) offset = 0; holdSince = -1; }
        else {
            offset += (float) (MasterClocks.rate(pos.toLong() * 31 + 7) * 20 * 0.08);   // OCXO: ~1/12 of a slave's drift
            if (holdSince < 0 && src() != 2 && live()) holdSince = world.getTotalWorldTime();
        }
        if (locked != wasLocked) {
            String m = "Master clock " + pos.getX() + "," + pos.getZ() + (locked ? " LOCKED to " + SRC[src()] : " lost " + SRC[src()] + " - HOLDOVER");
            Recorder.event(world, m);
            note(locked ? SRC[src()] + " LOCK" : "LOCK LOST");
            if (locked) PackSounds.play(world, pos, "ui.beep_ok", 0.5f, 1.2f);
        }
        long holdMin = holdSince < 0 ? 0 : (world.getTotalWorldTime() - holdSince) / 1200;
        String k = "mclock:" + pos.toLong();
        Alarms6.set(world, k + ":down", !outputting() && run(), AlarmManager.Cat.SIGNAL, AlarmManager.Sev.MAJOR,
                "MASTER CLOCK " + pos.getX() + "," + pos.getZ() + " DOWN (" + (powered() ? mains.what : "no 12 V") + ") - slave clocks running free", pos);
        Alarms6.set(world, k + ":hold", outputting() && src() != 2 && !locked && holdMin >= 2, AlarmManager.Cat.SIGNAL, AlarmManager.Sev.MINOR,
                "MASTER CLOCK " + pos.getX() + "," + pos.getZ() + " IN HOLDOVER - no " + SRC[src()] + (src() == 0 ? " (antenna can't see the sky)" : " (thunderstorm static)"), pos);
        Alarms6.set(world, k + ":slave", outputting() && count[2] > 0, AlarmManager.Cat.WIRED, AlarmManager.Sev.MINOR,
                count[2] + " SLAVE CLOCK(S) DARK near master clock " + pos.getX() + "," + pos.getZ() + " - no 12 V", pos);
        boolean on = powered();
        boolean blink = (world.getTotalWorldTime() / 20) % 2 == 0;
        put("pwr", on); put("sup", mains.ok);
        put("gps", on && src() == 0 && (locked || lockT > 0 && blink)); put("radio", on && src() == 1 && (locked || lockT > 0 && blink));
        put("lock", on && locked); put("hold", on && live() && src() != 2 && !locked); put("out", on && outputting() && blink);
        put("flt", on && count[2] > 0); put("int", on && src() == 2);
        put("g_sig", !live() ? 0 : src() == 0 ? sats() * 9 : src() == 1 ? (reception() ? 55 + (int) (world.getTotalWorldTime() / 20 % 30) : 3) : 0);
        put("g_osc", 50 + Math.max(-50, Math.min(50, Math.round(offset * 3))));
        text("time", time());
        text("date", "DAY " + com.dogpound.railmap.depot.DepotClock.day(world));
        text("srcl", !live() ? "" : src() == 2 ? "INTERNAL XTAL" : locked ? (src() == 0 ? "GPS LOCK " + sats() + " SATS" : "RADIO TIME LOCK") : lockT > 0 ? "ACQUIRING " + lockT + " S" : "HOLDOVER " + holdMin + " MIN");
        text("slv", "SLAVES " + count[0] + " SYNC " + count[1] + " FLT " + count[2]);
        text("err", String.format(Locale.ROOT, "ERR %+.1f S", offset * 3.6));
    }

    @Override
    public String controlPress(String id, EntityPlayer p) {
        switch (id) {
            case "test": return lampTest();
            case "screen": PacketOps6.open(p, this, 0); return "";
            case "sync": {
                if (!outputting()) return "§cOutput is off (RUN key, 12 V and supply)";
                int n = syncAll();
                note("MANUAL SYNC " + n + " SLAVES");
                Recorder.event(world, "Master clock " + pos.getX() + "," + pos.getZ() + ": manual sync of " + n + " slave clocks by " + who(p));
                PackSounds.play(world, pos, "grid.relay_click", 0.7f, 1f);
                return "§a" + n + " slave clocks set to " + time();
            }
            case "adv": case "ret": {
                if (!powered()) return "§cNo 12 V";
                if (src() != 2) return "§7Set by hand in INTERNAL only - GPS / radio set it themselves";
                offset += id.equals("adv") ? 1000f / 60 : -1000f / 60;
                note((id.equals("adv") ? "ADVANCE" : "RETARD") + " 1 MIN");
                Recorder.event(world, "Master clock " + pos.getX() + "," + pos.getZ() + " set by hand to " + time() + " by " + who(p));
                return "§e" + time();
            }
            case "ack":
                for (AlarmManager.Alarm a : AlarmManager.get(world).all()) if (a.key.startsWith("mclock:" + pos.toLong()) && !a.acked) AlarmManager.get(world).ack(a.id, who(p));
                return "§7Alarms acknowledged";
            case "s_src": {
                ctl.set("s_src", (src() + 1) % 3);
                lockT = 0; locked = false; holdSince = -1;
                note("SOURCE " + SRC[src()]);
                Recorder.event(world, "Master clock " + pos.getX() + "," + pos.getZ() + " source -> " + SRC[src()]);
                return "§eSource " + SRC[src()];
            }
            case "k_run": {
                ctl.set("k_run", run() ? 0 : 1);
                note(run() ? "OUTPUT RUN" : "OUTPUT OFF");
                Recorder.event(world, "Master clock " + pos.getX() + "," + pos.getZ() + " output " + (run() ? "RUN" : "OFF") + " by " + who(p));
                return run() ? "§aOutput RUN" : "§eOutput OFF - slaves will run free";
            }
            default: return null;
        }
    }

    /** pulse every slave in range now; returns how many */
    int syncAll() {
        MasterClocks.Data d = MasterClocks.data(world);
        double r2 = (double) Ops6Config.clockRange * Ops6Config.clockRange;
        int n = 0;
        for (Map.Entry<Long, MasterClocks.Slave> e : d.slaves.entrySet())
            if (BlockPos.fromLong(e.getKey()).distanceSq(pos) <= r2 && !e.getValue().fault) { e.getValue().drift = offset; e.getValue().syncAt = world.getTotalWorldTime(); e.getValue().master = pos.toLong(); n++; }
        d.markDirty();
        return n;
    }

    @Override
    public NBTTagCompound screen(EntityPlayer p, int page) {
        Scr s = new Scr("Master Clock " + pos.getX() + "," + pos.getZ(), (live() ? "§f" : "§c") + time(), page, "STATUS", "SLAVES", "SETUP", "LOG");
        switch (page) {
            case 1 -> {
                MasterClocks.Data d = MasterClocks.data(world);
                double r2 = (double) Ops6Config.clockRange * Ops6Config.clockRange;
                List<Map.Entry<Long, MasterClocks.Slave>> l = new ArrayList<>();
                for (Map.Entry<Long, MasterClocks.Slave> e : d.slaves.entrySet()) if (BlockPos.fromLong(e.getKey()).distanceSq(pos) <= r2) l.add(e);
                l.sort(Comparator.comparingDouble(e -> BlockPos.fromLong(e.getKey()).distanceSq(pos)));
                s.line("§7" + l.size() + " slave clocks within " + Ops6Config.clockRange + " blocks");
                for (int i = 0; i < Math.min(14, l.size()); i++) {
                    BlockPos b = BlockPos.fromLong(l.get(i).getKey());
                    MasterClocks.Slave x = l.get(i).getValue();
                    double err = (x.drift - offset) * 3.6;
                    s.line(String.format(Locale.ROOT, "%s%-15s §7%d,%d,%d  §f%s  %s", x.fault ? "§c" : Math.abs(err) < 60 ? "§a" : "§e", x.kind, b.getX(), b.getY(), b.getZ(),
                            hms(world.getWorldTime() + Math.round(x.drift)), x.fault ? "DARK" : String.format(Locale.ROOT, "%+.0f s", err)));
                }
                s.button("sync", "SYNC ALL", Scr.GREEN);
            }
            case 2 -> {
                s.line("§7Source: §f" + SRC[src()] + "   §7Impulse: §f" + PULSE[pulse()] + "   §7Output: §f" + (run() ? "RUN" : "OFF"));
                s.line("§7Range: §f" + Ops6Config.clockRange + " blocks   §7Supply: §f" + mains.what);
                s.line("§7Free slaves drift up to §f" + Ops6Config.clockDriftMinPerDay + " min / game day");
                for (int i = 0; i < 3; i++) s.button("src" + i, "SOURCE " + SRC[i], src() == i ? Scr.GREEN : Scr.GREY);
                for (int i = 0; i < 3; i++) s.button("pulse" + i, "PULSE " + PULSE[i], pulse() == i ? Scr.GREEN : Scr.GREY);
                s.button("k_run", run() ? "OUTPUT OFF" : "OUTPUT RUN", run() ? Scr.RED : Scr.GREEN).button("adv", "ADV +1 MIN", Scr.BLUE).button("ret", "RET -1 MIN", Scr.BLUE);
            }
            case 3 -> { for (String l : log) s.line("§7" + l); if (log.isEmpty()) s.line("§8(no events yet)"); }
            default -> {
                s.line("§f§l" + time() + "   §7" + "DAY " + com.dogpound.railmap.depot.DepotClock.day(world));
                s.line("§7Source: §f" + SRC[src()] + (locked ? " §aLOCKED" : src() == 2 ? " §efree-running crystal" : lockT > 0 ? " §eacquiring " + lockT + " s" : " §cHOLDOVER"));
                if (src() == 0) s.line("§7Antenna: §f" + (world.canSeeSky(pos.up()) ? "sky view, " + sats() + " satellites" : "§cNO SKY VIEW - put the master under open sky"));
                if (src() == 1) s.line("§7Radio: §f" + (world.isThundering() ? "§cthunderstorm static" : "time signal received"));
                s.line(String.format(Locale.ROOT, "§7Master error: §f%+.1f s   §7impulse every §f%s", offset * 3.6, PULSE[pulse()]));
                s.line("§7Slaves: §f" + count[0] + " in range, " + count[1] + " in sync, " + count[2] + " dark");
                s.bar("SIGNAL", controls().get("g_sig"), 100, locked ? 0x30A050 : 0xC09020, locked ? "LOCK" : "NO LOCK");
                s.button("sync", "SYNC ALL", Scr.GREEN).button("ack", "ACK ALARMS", Scr.AMBER);
            }
        }
        return s.build();
    }

    @Override
    public String screenAction(EntityPlayer p, int page, String a) {
        if (a.startsWith("src")) { int want = a.charAt(3) - '0'; String r = null; while (src() != want) r = controlPress("s_src", p); controlsChanged(); return r; }
        if (a.startsWith("pulse")) { ctl.set("s_pulse", a.charAt(5) - '0'); note("PULSE " + PULSE[pulse()]); controlsChanged(); return "§eImpulse " + PULSE[pulse()]; }
        return com.dogpound.railmap.controls.IControlled.operate(this, a, p);
    }

    @Override protected void store(NBTTagCompound t) { t.setFloat("off", offset); t.setInteger("lockT", lockT); t.setBoolean("locked", locked); t.setLong("hold", holdSince); }
    @Override protected void restore(NBTTagCompound t) { offset = t.getFloat("off"); lockT = t.getInteger("lockT"); locked = t.getBoolean("locked"); holdSince = t.hasKey("hold") ? t.getLong("hold") : -1; }
}
