package com.dogpound.railmap.infra;

import com.dogpound.railmap.grid.Bench;
import com.dogpound.railmap.grid.Elec;
import com.dogpound.railmap.grid.GridData;
import com.dogpound.railmap.grid.GridKind;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Relay Test Set (#40): a three-phase secondary-injection test set on its trolley (an OMICRON / Doble style unit),
 * plugged into a 120 / 240 V AC supply (a grid cable at that level touching it - its current amplifiers draw 2 kW
 * while injecting) and 12 V control power. SELECT steps through the protection IEDs within 16 blocks; the TEST BLOCK
 * key isolates the IED's trip circuit (ISOL) or leaves it LIVE (then an operation really trips the breaker - harsh
 * switch testSetTripsLive). Modes:
 * <ul>
 *   <li>PICKUP - ramps the current from 0.8x to 1.3x the setting until the relay operates: pick-up error;</li>
 *   <li>TIMING - injects 5x the setting: operating time against the curve the settings call for;</li>
 *   <li>FULL - the whole commissioning certificate (grid.Bench.test: CT, VT, curve points, breaker, TCS, calibration);</li>
 *   <li>CALIB - needs the test block ISOL: re-calibrates the measuring chain inside ±0.1 % (30 s).</li>
 * </ul>
 * IEC 60255 tolerances: ±5 % pick-up, ±5 % or 40 ms time. PRINT hands over the last certificate as a book.
 */
public class TileRelayTestSet extends InfraTile {
    static final String[] MODES = {"PICKUP", "TIMING", "FULL", "CALIB"};
    static final int[] SECS = {8, 6, 12, 30};
    private long target, runUntil, runStart;
    private int runMode = -1;
    private String result = "", who = "";
    private final List<String> lines = new ArrayList<>();

    public TileRelayTestSet() { super(new Supply(Elec.Level.AC120, Elec.Level.AC240)); }

    @Override public String wiredName() { return "Relay test set " + pos.getX() + "," + pos.getZ(); }

    List<Long> ieds() {
        List<Long> l = new ArrayList<>();
        for (Map.Entry<Long, GridData.Node> en : GridData.get(world).nodes().entrySet())
            if (en.getValue().kind == GridKind.IED && BlockPos.fromLong(en.getKey()).distanceSq(pos) <= 16 * 16) l.add(en.getKey());
        l.sort(Comparator.comparingDouble(p -> BlockPos.fromLong(p).distanceSq(pos)));
        return l;
    }

    private boolean running() { return runMode >= 0; }

    @Override
    public String gearState() {
        return mains.status().replaceAll("§.", "") + " · " + (target == 0 ? "no IED selected" : "IED " + xyz(target)) + (running() ? " · RUNNING " + MODES[runMode] : result.isEmpty() ? "" : " · " + result);
    }

    @Override public int wiredLamp() { return !live() ? 3 : result.startsWith("FAIL") ? 2 : 1; }

    static String xyz(long p) { BlockPos b = BlockPos.fromLong(p); return b.getX() + "," + b.getY() + "," + b.getZ(); }

    @Override
    protected void serverTick() {
        long now = world.getTotalWorldTime();
        if (age % 20 == 7) mainsTick(running() ? 2000 : 150);
        if (age % 5 != 1) return;
        if (target != 0 && GridData.get(world).node(BlockPos.fromLong(target)) == null) target = 0;
        if (target == 0) { List<Long> l = ieds(); if (!l.isEmpty()) target = l.get(0); }
        double[] s = target == 0 ? null : Bench.settings(world, BlockPos.fromLong(target));
        int mode = ctl.get("s_mode");
        if (running()) {
            if (!live()) { abort("supply lost - the amplifiers dropped out"); }
            else {
                double f = Math.min(1, (now - runStart) / (double) (runUntil - runStart));
                double m = runMode == 0 ? 0.8 + 0.5 * f : runMode == 1 ? 5 : runMode == 2 ? 2 + 8 * f : 1;
                put("g_amp", (int) Math.round(Math.min(100, m * 10)));
                text("inj", s == null ? "-" : String.format(Locale.ROOT, "I %.0f A %.2fX", s[0] * m, m));
                put("op", runMode == 0 && f > 0.4 || runMode == 1 && f > 0.6 || runMode == 2 && f > 0.3);
                if (now >= runUntil) finish(s);
            }
        } else { put("g_amp", 0); put("op", false); }
        put("run", running());
        put("pass", !running() && result.startsWith("PASS"));
        put("fail", !running() && result.startsWith("FAIL"));
        put("isol", ctl.get("k_block") == 1);
        put("livet", ctl.get("k_block") == 0);
        put("sup", mains.ok);
        put("pwr", powered());
        text("ied", target == 0 ? "NO IED IN REACH" : "IED " + xyz(target));
        text("set", s == null ? "NO BREAKER" : String.format(Locale.ROOT, "SET %.0f A TMS %.2f", s[0], s[2]));
        text("res", running() ? "RUNNING " + MODES[runMode] : result.isEmpty() ? MODES[mode] + " READY" : result);
        if (!running() && target != 0) {
            double due = RelayCal.dueDays(world, target);
            text("cal", due == Double.MAX_VALUE ? "CAL: NOT TRACKED" : due < 0 ? String.format(Locale.ROOT, "CAL OVERDUE %.0f D", -due) : String.format(Locale.ROOT, "CAL DUE %.0f D", due));
        }
        if (!running()) text("inj", mains.ok ? mains.level.label.toUpperCase(Locale.ROOT).replace("φ", "").replace("Φ", "") : "NO MAINS");
    }

    private void abort(String why) {
        runMode = -1;
        result = "ABORTED";
        text("time", ""); text("err", "");
        tell("§c" + wiredName() + ": test aborted - " + why);
    }

    private void finish(double[] s) {
        int mode = runMode;
        runMode = -1;
        lines.clear();
        BlockPos ied = BlockPos.fromLong(target);
        boolean pass;
        String what;
        if (s == null || !Bench.alive(world, ied)) {
            pass = false; what = MODES[mode];
            result = s == null ? "FAIL NO BREAKER" : "FAIL IED DEAD";
            lines.add(result + " - " + (s == null ? "no breaker touching the IED" : "the IED has no 12 V aux supply"));
            text("time", ""); text("err", "");
        } else if (mode == 0) {
            double err = RelayCal.pickupErr(world, target);
            pass = Math.abs(err) <= 0.05;
            result = (pass ? "PASS" : "FAIL") + String.format(Locale.ROOT, " PU %.3fX", 1 + err);
            what = "pick-up test";
            text("time", String.format(Locale.ROOT, "PU %.0f A", s[0] * (1 + err)));
            text("err", String.format(Locale.ROOT, "ERR %+.1f%%", err * 100));
            lines.add(String.format(Locale.ROOT, "Pick-up: set %.0f A, operated at %.0f A (%+.2f%%) - tolerance ±5%%: %s", s[0], s[0] * (1 + err), err * 100, pass ? "PASS" : "FAIL"));
        } else if (mode == 1) {
            double pe = RelayCal.pickupErr(world, target), te = RelayCal.timeErr(world, target);
            double exp = Bench.expected(s, 5), act = Bench.expected(s, 5 / (1 + pe)) * (1 + te);
            pass = Math.abs(act - exp) <= Math.max(0.04, exp * 0.05);
            result = (pass ? "PASS" : "FAIL") + String.format(Locale.ROOT, " %.3fS", act);
            what = "timing test 5x";
            text("time", String.format(Locale.ROOT, "T %.3f S EXP %.3f", act, exp));
            text("err", String.format(Locale.ROOT, "ERR %+.1f%%", (act / exp - 1) * 100));
            lines.add(String.format(Locale.ROOT, "Timing at 5x (%.0f A): expected %.3f s, operated %.3f s - tolerance ±5%% / 40 ms: %s", s[0] * 5, exp, act, pass ? "PASS" : "FAIL"));
        } else if (mode == 2) {
            Object[] r = Bench.test(world, ied);
            pass = (Boolean) r[0];
            what = "secondary injection (full)";
            @SuppressWarnings("unchecked") List<String> l = (List<String>) r[1];
            lines.addAll(l);
            result = pass ? "PASS CERTIFIED" : "FAIL SEE PRINT";
            text("time", l.size() > 3 ? l.get(3).substring(0, Math.min(24, l.get(3).length())).toUpperCase(Locale.ROOT) : "");
            text("err", String.format(Locale.ROOT, "PU %+.1f%% T %+.1f%%", RelayCal.pickupErr(world, target) * 100, RelayCal.timeErr(world, target) * 100));
        } else {
            if (ctl.get("k_block") != 1) { result = "REFUSED: TEST BLOCK LIVE"; lines.add(result); return; }
            String msg = RelayCal.calibrate(world, target, who);
            lines.add(msg.replaceAll("§.", ""));
            result = "PASS CALIBRATED";
            text("time", ""); text("err", "ERR 0.0%");
            world.playSound(null, pos, com.dogpound.railmap.ModSounds.BEEP_OK, SoundCategory.BLOCKS, 0.6f, 1f);
            com.dogpound.railmap.api.QuestEvents.fire(world, null, pos, "relay_calibrated", "ied", xyz(target));
            return;
        }
        RelayCal.tested(world, target, pass, what, who);
        world.playSound(null, pos, pass ? com.dogpound.railmap.ModSounds.BEEP_OK : com.dogpound.railmap.ModSounds.BEEP_ERROR, SoundCategory.BLOCKS, 0.6f, 1f);
        // the relay operated with its trip circuit connected: the breaker goes too
        if (mode < 3 && ctl.get("k_block") == 0 && s != null) {
            if (InfraConfig.testSetTripsLive) {
                int n = Bench.liveTrip(world, ied, who);
                if (n > 0) { lines.add("TEST BLOCK WAS LIVE: " + n + " breaker(s) TRIPPED by the injection"); tell("§c§l" + wiredName() + ": the test block was LIVE - the injection tripped " + n + " breaker(s)!"); }
            } else lines.add("Warning: test block LIVE - on a real panel this injection trips the breaker");
        }
        com.dogpound.railmap.api.QuestEvents.fire(world, null, pos, "relay_tested", "ied", xyz(target), "mode", MODES[mode], "pass", pass);
    }

    @Override
    public String controlPress(String id, EntityPlayer p) {
        switch (id) {
            case "test": return lampTest();
            case "s_mode": if (running()) return "§cStop the test before changing the mode"; return null;
            case "k_block":
                if (running()) return "§cNever move the test block during an injection";
                ctl.set("k_block", 1 - ctl.get("k_block"));
                return ctl.get("k_block") == 1 ? "§aTest block ISOLATED - trip circuit open, CT shorted" : "§6Test block LIVE - the relay's trips reach the breaker";
            case "select": {
                if (running()) return "§cStop the test first";
                List<Long> l = ieds();
                if (l.isEmpty()) { target = 0; return "§7No protection IED within 16 blocks"; }
                int i = l.indexOf(target);
                target = l.get((i + 1) % l.size());
                result = "";
                return "§eTest leads to IED " + xyz(target) + " (" + l.size() + " in reach)";
            }
            case "start": {
                if (running()) return "§7Already running";
                if (!live()) return "§cNo supply: " + (powered() ? mains.what : "no 12 V");
                if (target == 0) return "§cSELECT an IED first";
                int m = ctl.get("s_mode");
                if (m == 3 && ctl.get("k_block") != 1) return "§cCalibration needs the TEST BLOCK isolated";
                runMode = m; runStart = world.getTotalWorldTime(); runUntil = runStart + SECS[m] * 20L; who = who(p); result = "";
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.RELAY, SoundCategory.BLOCKS, 0.6f, 1f);
                return "§b" + MODES[m] + " test started on IED " + xyz(target) + " (" + SECS[m] + " s)";
            }
            case "stop": if (!running()) return "§7Nothing running"; abort("stopped by " + who(p)); return "§eTest stopped";
            case "print": {
                if (p == null) return "§7Printing is done at the set";
                if (lines.isEmpty()) return "§7No result to print yet";
                List<String> out = new ArrayList<>();
                out.add("RELAY TEST CERTIFICATE");
                out.add("IED " + xyz(target));
                out.add(com.dogpound.railmap.alarm.Recorder.clock(world, world.getTotalWorldTime()) + " by " + who);
                out.addAll(lines);
                if (!p.inventory.addItemStackToInventory(com.dogpound.railmap.depot.Diagnostics.book("Relay test " + xyz(target), "Relay test set", out))) p.dropItem(com.dogpound.railmap.depot.Diagnostics.book("Relay test " + xyz(target), "Relay test set", out), false);
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.PRINTER, SoundCategory.BLOCKS, 0.6f, 1f);
                return "§aCertificate printed";
            }
            default: return "§7" + gearState();
        }
    }

    @Override
    protected void store(NBTTagCompound t) {
        t.setLong("target", target); t.setString("result", result);
        NBTTagCompound l = new NBTTagCompound();
        for (int i = 0; i < lines.size(); i++) l.setString(String.valueOf(i), lines.get(i));
        t.setTag("lines", l);
    }

    @Override
    protected void restore(NBTTagCompound t) {
        target = t.getLong("target"); result = t.getString("result");
        lines.clear();
        NBTTagCompound l = t.getCompoundTag("lines");
        for (int i = 0; l.hasKey(String.valueOf(i)); i++) lines.add(l.getString(String.valueOf(i)));
    }
}
