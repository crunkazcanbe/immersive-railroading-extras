package com.dogpound.railmap.wire;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.safety.SafetyTicker;
import com.dogpound.railmap.safety.SafetyTile;
import com.dogpound.railmap.safety.TileRadioMast;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Locale;
import java.util.Map;

/**
 * Field RTU (remote terminal unit, her "next 48" list #25): the cabinet that polls every piece of wired gear within its
 * coverage and carries the telemetry back to the control room over two independent paths - A: fibre (8-20 ms, ~0.1 %
 * loss) and B: GSM-R radio through a healthy radio mast in range (300-900 ms, 2-15 % loss, more in thunderstorms).
 * <ul>
 *   <li>LINK A / LINK B selectors take a path in or out of service; PREFER picks the main path; failover to the other
 *       takes 3 s with no telemetry;</li>
 *   <li>COVER: 32 / 48 / 64 / 96 m; POLL forces a poll; RESET clears the counters; RESTART reboots a failed CPU (20 s);</li>
 *   <li>lamps: CPU run / fail, link A / B up / down, which path is active, TX / RX, STALE (some gear's data is stale on
 *       the console), LOCAL (the interlocking and gear always keep working locally - this is the remote view only);</li>
 *   <li>a dead RTU (no 12 V, CPU failure from the fault lab / chaos) makes every value it carries STALE on the console
 *       and refuses console commands to that gear - operate it on site.</li>
 * </ul>
 */
public class TileRtu extends SafetyTile {
    public static final int[] COVER = {32, 48, 64, 96};
    private long rebootUntil;
    private boolean failedLast;

    public String id() { return "RTU" + Math.floorMod(pos.getX() * 23 + pos.getZ() * 7, 100); }
    @Override public String wiredName() { return "Field RTU " + id(); }

    public Comms.Rtu rec() { return Comms.rtu(world, pos.toLong()); }

    @Override public String gearState() {
        Comms.Rtu r = rec();
        return !powered() ? "DEAD - no 12 V (covered gear STALE)" : !r.cpuOk ? "CPU FAILED - " + r.why : r.path == 0 ? "NO PATH - " + r.why : String.format(Locale.ROOT, "on %s · %.0f ms · %.1f%% loss", r.path == 1 ? "A fibre" : "B radio", r.latencyMs, r.loss * 100);
    }
    @Override public int wiredLamp() { Comms.Rtu r = rec(); return !powered() || !r.cpuOk || r.path == 0 ? 3 : r.path != r.prefer ? 2 : 1; }
    @Override public void gearInfo(Map<String, Object> m) { Comms.Rtu r = rec(); m.put("cpu", r.cpuOk); m.put("path", r.path); m.put("latencyMs", Math.round(r.latencyMs)); m.put("lossPct", Math.round(r.loss * 1000) / 10.0); m.put("tx", r.tx); m.put("rx", r.rx); m.put("lost", r.lost); m.put("cover", r.radius); }

    @Override
    protected void serverTick() {
        if (age % 20 != 5) return;
        Comms.Rtu r = rec();
        long now = world.getTotalWorldTime();
        long me = pos.toLong();
        r.seen = now;
        r.id = id();
        r.radius = COVER[Math.min(3, ctl.get("s_cover"))];
        r.aService = ctl.get("s_a") == 0;
        r.bService = ctl.get("s_b") == 0;
        r.prefer = ctl.get("s_pref") == 0 ? 1 : 2;
        r.cpuOk = powered() && now >= rebootUntil && !Comms.cut(world, "CPU", me);
        // link A: fibre
        r.aUp = r.cpuOk && r.aService && !Comms.cut(world, "A", me);
        // link B: radio through a healthy mast in range
        TileRadioMast mast = null;
        double md = Double.MAX_VALUE;
        for (TileRadioMast m : SafetyTicker.radioMasts(world)) {
            double d = Math.sqrt(m.getPos().distanceSq(pos));
            if (m.healthy() && d <= m.radius() && d < md) { md = d; mast = m; }
        }
        r.bUp = r.cpuOk && r.bService && mast != null && !Comms.cut(world, "B", me);
        int want = r.prefer == 1 ? (r.aUp ? 1 : r.bUp ? 2 : 0) : (r.bUp ? 2 : r.aUp ? 1 : 0);
        if (want != r.path) {
            if (r.path != 0 && want != 0) r.failoverUntil = now + 60;
            if (want == 0) { r.why = !r.cpuOk ? "CPU" : "link A " + (r.aService ? "cut" : "out of service") + ", link B " + (!r.bService ? "out of service" : mast == null ? "no radio mast in range" : "cut"); alarm(r, "NO COMMS PATH: " + r.why); }
            else if (r.path == 0) AlarmManager.get(world).clear("rtu:" + me);
            Recorder.event(world, "COMMS " + id() + ": path " + name(r.path) + " -> " + name(want));
            r.path = want;
        }
        if (!r.cpuOk && !failedLast) { r.why = !powered() ? "no 12 V" : now < rebootUntil ? "rebooting" : "CPU fault"; alarm(r, "FIELD CONTROLLER FAILED (" + r.why + ") - gear it covers shows STALE"); }
        if (r.cpuOk && failedLast) AlarmManager.get(world).clear("rtu:" + me);
        failedLast = !r.cpuOk;
        // the poll
        if (r.path == 1) { r.latencyMs = 8 + world.rand.nextInt(12); r.loss = 0.001; }
        else if (r.path == 2) {
            double f = mast == null ? 1 : md / Math.max(1, mast.radius());
            r.latencyMs = 300 + 600 * f + world.rand.nextInt(80);
            r.loss = 0.02 + 0.08 * f + (world.isThundering() ? 0.05 : 0);
        } else { r.latencyMs = 0; r.loss = 1; }
        boolean polled = false;
        if (r.cpuOk && r.path != 0 && now >= r.failoverUntil) {
            r.tx++;
            if (world.rand.nextDouble() >= r.loss) { r.rx++; r.lastGood = now - Math.round(r.latencyMs / 50); polled = true; }
            else r.lost++;
        }
        int stale = 0, fresh = 0;
        for (net.minecraft.tileentity.TileEntity t : world.loadedTileEntityList) {
            if (!(t instanceof IWiredMachine) || t == this || t.getPos().distanceSq(pos) > (double) r.radius * r.radius) continue;
            if (Comms.stale(world, r)) stale++; else fresh++;
        }
        put("pwr", powered());
        put("cpu", r.cpuOk);
        put("cpuf", !r.cpuOk);
        put("a_ok", r.aUp); put("a_bad", !r.aUp);
        put("b_ok", r.bUp); put("b_bad", !r.bUp);
        put("act_a", r.path == 1); put("act_b", r.path == 2);
        put("tx", polled); put("rx", polled);
        put("stale", stale > 0);
        put("local", powered());
        put("g_lat", (int) Math.min(1000, Math.round(r.latencyMs)));
        put("g_q", r.path == 0 ? 0 : (int) Math.round((1 - r.loss) * 100));
        text("id", id() + " COVER " + r.radius + "M");
        text("path", r.path == 0 ? "NO PATH" : (r.path == 1 ? "PATH A FIBRE" : "PATH B RADIO") + (now < r.failoverUntil ? " FAILOVER" : ""));
        text("lat", String.format(Locale.ROOT, "LAT %.0f MS LOSS %.1f%%", r.latencyMs, r.path == 0 ? 100 : r.loss * 100));
        text("gear", "GEAR " + (fresh + stale) + " FRESH " + fresh + " STALE " + stale);
        text("ctr", "TX " + r.tx + " RX " + r.rx + " LOST " + r.lost);
        text("last", r.lastGood < 0 ? "NO GOOD POLL YET" : String.format(Locale.ROOT, "LAST GOOD %.0f S", (now - r.lastGood) / 20.0));
    }

    private static String name(int p) { return p == 1 ? "A (fibre)" : p == 2 ? "B (radio)" : "NONE"; }

    private void alarm(Comms.Rtu r, String text) {
        AlarmManager.get(world).raise(world, "rtu:" + pos.toLong(), AlarmManager.Cat.WIRED, AlarmManager.Sev.MAJOR, "Field RTU " + id() + " at " + pos.getX() + "," + pos.getZ() + ": " + text, pos, true);
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        Comms.Rtu r = rec();
        switch (id) {
            case "test": return lampTest();
            case "poll":
                if (!r.cpuOk || r.path == 0) return "§cCan't poll - " + gearState();
                r.tx++; if (world.rand.nextDouble() >= r.loss) { r.rx++; r.lastGood = world.getTotalWorldTime(); return "§aPoll OK"; }
                r.lost++; return "§6Poll lost on " + name(r.path);
            case "reset": r.tx = r.rx = r.lost = 0; return "§7Counters cleared";
            case "restart":
                if (!powered()) return "§cNo 12 V";
                rebootUntil = world.getTotalWorldTime() + 400;
                Comms.cuts(world).remove("CPU:" + pos.toLong());
                Recorder.event(world, "COMMS " + id() + ": CPU restart by " + who(who));
                return "§eRTU restarting (20 s) - covered gear STALE meanwhile";
            default: return null;
        }
    }

    @Override public void invalidate() { super.invalidate(); if (world != null && !world.isRemote) Comms.forget(world, pos.toLong()); }
    @Override public void onChunkUnload() { super.onChunkUnload(); if (world != null && !world.isRemote) Comms.forget(world, pos.toLong()); }

    @Override protected void save(NBTTagCompound t) { }
    @Override protected void load(NBTTagCompound t) { }
}
