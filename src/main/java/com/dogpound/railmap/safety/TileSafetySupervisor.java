package com.dogpound.railmap.safety;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.depot.Mains;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;

import java.util.Map;

/**
 * Independent Safety Supervisor rack (her batch-3 list #20): a 2-out-of-2 vital processor in a 19" rack - two processor
 * channels with their own power supplies, a cross-compare unit and a watchdog relay. 230 V AC mains (grid cable touching
 * the rack, a real load) runs the processors; the 12 V terminal runs the controls and puts it on the WIRED page. With
 * either supply missing it is DOWN and supervises nothing (holds it already made stay; a critical alarm in ENFORCE).
 * The checks themselves are in {@link Supervisor}.
 */
public class TileSafetySupervisor extends SafetyTile {
    static final long LOAD_FE = 60;
    private final Mains mains = new Mains();
    private int supervised, worstPct, interventions, violations;
    private long cycleNs, beat;
    private String last = "";
    private boolean wasOnline = true;

    @Override public String wiredName() { return "Safety supervisor " + pos.getX() + "," + pos.getZ(); }

    public boolean online() { return powered() && mains.ok() && !term.burnt; }
    public boolean enforce() { return ctl.get("k_mode") == 1; }
    public int coverage() { return new int[]{256, 1024, Integer.MAX_VALUE}[Math.min(2, Math.max(0, ctl.get("s_cov")))]; }
    public String coverName() { return new String[]{"256 M", "1024 M", "ALL"}[Math.min(2, Math.max(0, ctl.get("s_cov")))]; }

    boolean covers(double x, double z) {
        int c = coverage();
        return c == Integer.MAX_VALUE || Geo.d2(x, z, pos.getX() + 0.5, pos.getZ() + 0.5) <= (double) c * c;
    }

    /** Supervisor: the result of one pass */
    void pass(int n, int worst, long ns) { supervised = n; worstPct = worst; cycleNs = ns; beat++; }
    void violation(String text) { violations++; last = text; }
    void intervened(String text) {
        interventions++;
        last = "HELD " + text;
        markDirty();
        world.playSound(null, pos, com.dogpound.railmap.ModSounds.RELAY, SoundCategory.BLOCKS, 0.8f, 0.7f);
        world.playSound(null, pos, com.dogpound.railmap.ModSounds.BUZZER, SoundCategory.BLOCKS, 0.6f, 1.0f);
    }

    /** once a second: mains, health alarms */
    void second() {
        boolean on = online();
        String key = "supdown:" + pos.toLong();
        AlarmManager am = AlarmManager.get(world);
        if (!on) am.raise(world, key, AlarmManager.Cat.AUTO, enforce() ? AlarmManager.Sev.CRITICAL : AlarmManager.Sev.MAJOR,
                "Safety supervisor at " + pos.getX() + "," + pos.getZ() + " DOWN (" + (!mains.ok() ? mains.what : term.what) + ") - driverless trains in its area are unsupervised", pos, true);
        else am.clear(key);
        if (on != wasOnline) { wasOnline = on; com.dogpound.railmap.alarm.Recorder.event(world, "SAFETY SUPERVISOR " + pos.getX() + "," + pos.getZ() + (on ? " back on line" : " DOWN")); }
    }

    @Override
    public String gearState() {
        if (!mains.ok()) return "DOWN - " + mains.what;
        if (!online()) return "DOWN - " + term.what;
        return (enforce() ? "ENFORCE" : "MONITOR") + " · " + coverName() + " · " + supervised + " train(s) · " + interventions + " intervention(s)";
    }

    @Override public int wiredLamp() { return !online() ? 3 : violations > 0 && !last.isEmpty() ? 2 : 1; }
    @Override public void gearInfo(Map<String, Object> m) { m.put("online", online()); m.put("mode", enforce() ? "ENFORCE" : "MONITOR"); m.put("trains", supervised); m.put("interventions", interventions); m.put("last", last); m.put("mains", mains.what); }

    @Override
    protected void serverTick() {
        if (mains.tick(world, pos, term, wiredName(), powered() ? LOAD_FE : 0)) controlsChanged();
        if (age % 10 != 0) return;
        boolean on = online();
        boolean viol = false, held = false, xc = true;
        for (String[] r : rowsOfMine()) { if (r[7].equals("2")) viol = true; if (r[7].equals("1")) held = true; if (r[9].equals("1")) xc = false; }
        put("mains", mains.ok());
        put("pwr", powered());
        put("ok", on);
        put("cha", on);
        put("chb", on);
        put("wd", on && beat % 2 == 0);
        put("xc", on && xc);
        put("xcf", on && !xc);
        put("vio", on && viol);
        put("enf", on && enforce());
        put("held", held);
        put("n", on ? supervised : 0);
        put("iv", interventions);
        put("margin", on ? Math.min(150, worstPct) : 0);
        put("cyc", on ? (int) Math.min(100, cycleNs / 10_000) : 0);            // 0.01 ms per unit, 1 ms full scale
        text("last", last.isEmpty() ? "NO VIOLATIONS" : last.toUpperCase());
        text("mode", on ? (enforce() ? "ENFORCE " : "MONITOR ") + coverName() : "DOWN");
    }

    private java.util.List<String[]> rowsOfMine() {
        java.util.List<String[]> l = new java.util.ArrayList<>();
        Supervisor.fill(world, l, world.getTotalWorldTime());
        l.removeIf(r -> !r[0].equals("a.sup") || !r[10].equals(String.valueOf(pos.toLong())));
        return l;
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        switch (id) {
            case "lamptest": return lampTest();
            case "rst": {
                String r = Supervisor.reset(world, null, who(who));
                if (r.startsWith("§a")) { last = ""; violations = 0; }
                return r;
            }
            case "ack": last = ""; violations = 0; return "§7Violation display cleared";
            case "k_mode": {
                ctl.set("k_mode", enforce() ? 0 : 1);
                Supervisor.log(world, "mode " + (enforce() ? "ENFORCE" : "MONITOR") + " at rack " + pos.getX() + "," + pos.getZ() + " by " + who(who));
                return enforce() ? "§6ENFORCE - the supervisor brakes and holds trains that break their envelope" : "§aMONITOR - violations are logged and alarmed only";
            }
            case "s_cov": ctl.set("s_cov", (ctl.get("s_cov") + 1) % 3); return "§eCoverage " + coverName();
            default: return "§7" + gearState();
        }
    }

    @Override protected void save(NBTTagCompound t) { mains.write(t); t.setInteger("iv", interventions); t.setString("last", last); }
    @Override protected void load(NBTTagCompound t) { mains.read(t); interventions = t.getInteger("iv"); last = t.getString("last"); }
}
