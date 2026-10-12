package com.dogpound.railmap.infra;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.grid.GridData;
import com.dogpound.railmap.grid.GridKind;
import com.dogpound.railmap.grid.History;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Relay calibration (her ChatGPT list batch 3, #40 - extends the commissioning test bench, grid.Bench). A numerical
 * protection IED's measuring chain drifts with age and temperature: its pick-up and operating time wander off the
 * setting (a random walk, InfraConfig.relayDriftPctPerDay). The Relay Test Set finds it (pick-up ramp, timing at 5x,
 * or the full secondary-injection certificate through Bench.test, which now includes the drift) and CALIBRATES it back
 * inside ±0.1 %; every test and calibration is certified and logged in the IED's history. Calibrations older than
 * InfraConfig.relayCalDays raise a 'calibration due' alarm. Whether the drift acts on the live protection is the harsh
 * switch relayDriftAffectsProtection (off: the drift is real for the tests and certificates only).
 */
public final class RelayCal {
    private RelayCal() {}

    public static final long DAY = 24000;
    /** pick-up drift for the live protection (only filled when the harsh switch is on) */
    private static final Map<Long, Double> LIVE = new HashMap<>();

    /** the IED's calibration record, its drift brought up to date */
    public static InfraData.Cal of(World w, long ied) {
        InfraData d = InfraData.get(w);
        InfraData.Cal c = d.cal(ied);
        long now = w.getTotalWorldTime();
        if (c.lastDrift == 0) { c.lastDrift = now; c.calAt = now; d.markDirty(); return c; }   // first seen: as delivered, calibrated
        double days = (now - c.lastDrift) / (double) DAY;
        if (days >= 0.05) {
            Random r = new Random(ied ^ now);
            double sd = InfraConfig.relayDriftPctPerDay / 100 * Math.sqrt(days);
            c.pick = clamp(c.pick + r.nextGaussian() * sd);
            c.time = clamp(c.time + r.nextGaussian() * sd * 1.5);
            c.lastDrift = now;
            d.markDirty();
        }
        return c;
    }

    private static double clamp(double v) { return Math.max(-0.25, Math.min(0.25, v)); }

    public static double pickupErr(World w, long ied) { return of(w, ied).pick; }
    public static double timeErr(World w, long ied) { return of(w, ied).time; }

    /** grid.Bench.setting: the live protection's pick-up factor (1 unless the harsh switch is on) */
    public static double livePickup(long ied) {
        if (!InfraConfig.relayDriftAffectsProtection) return 1;
        Double v = LIVE.get(ied);
        return v == null ? 1 : 1 + v;
    }

    /** a test result for the certificate log */
    public static void tested(World w, long ied, boolean pass, String what, String who) {
        InfraData d = InfraData.get(w);
        InfraData.Cal c = of(w, ied);
        c.testAt = w.getTotalWorldTime(); c.lastPass = pass; c.tests++;
        BlockPos p = BlockPos.fromLong(ied);
        String line = com.dogpound.railmap.alarm.Recorder.clock(w, c.testAt) + " IED " + p.getX() + "," + p.getY() + "," + p.getZ() + " " + what + " " + (pass ? "PASS" : "FAIL") + " by " + who;
        d.certs.addFirst(line);
        while (d.certs.size() > 40) d.certs.removeLast();
        History.log(w, p, "TEST", what + " " + (pass ? "PASSED" : "FAILED") + " (relay test set, " + who + ")");
        d.markDirty();
    }

    /** bring the measuring chain back inside ±0.1 % and certify it */
    public static String calibrate(World w, long ied, String who) {
        InfraData.Cal c = of(w, ied);
        double was = Math.max(Math.abs(c.pick), Math.abs(c.time));
        Random r = new Random(ied * 7 + w.getTotalWorldTime());
        c.pick = (r.nextDouble() - 0.5) * 0.002;
        c.time = (r.nextDouble() - 0.5) * 0.002;
        c.calAt = w.getTotalWorldTime();
        c.cals++;
        tested(w, ied, true, String.format(Locale.ROOT, "CALIBRATION (was %.1f%% out)", was * 100), who);
        AlarmManager.get(w).clear("relaycal:" + ied);
        return String.format(Locale.ROOT, "§aCalibrated - was %.1f%% out, now inside ±0.1%%. Next due in %d days", was * 100, InfraConfig.relayCalDays);
    }

    /** days until the calibration is due (negative = overdue; MAX = never) */
    public static double dueDays(World w, long ied) {
        if (InfraConfig.relayCalDays <= 0) return Double.MAX_VALUE;
        return InfraConfig.relayCalDays - (w.getTotalWorldTime() - of(w, ied).calAt) / (double) DAY;
    }

    /** once a minute: drift every IED, refresh the live table, raise calibration-due alarms */
    static void tick(World w) {
        GridData g = GridData.get(w);
        AlarmManager am = AlarmManager.get(w);
        for (Map.Entry<Long, GridData.Node> en : g.nodes().entrySet()) {
            if (en.getValue().kind != GridKind.IED) continue;
            InfraData.Cal c = of(w, en.getKey());
            if (InfraConfig.relayDriftAffectsProtection) LIVE.put(en.getKey(), c.pick); else LIVE.remove(en.getKey());
            String k = "relaycal:" + en.getKey();
            double due = dueDays(w, en.getKey());
            if (due < 0) {
                boolean on = false;
                for (AlarmManager.Alarm a : am.all()) if (a.key.equals(k) && a.active) on = true;
                BlockPos p = BlockPos.fromLong(en.getKey());
                if (!on) am.raise(w, k, AlarmManager.Cat.POWER, AlarmManager.Sev.INFO, "Protection IED " + p.getX() + "," + p.getY() + "," + p.getZ() + " CALIBRATION DUE ("
                        + Math.round(-due) + " days overdue) - test + calibrate it with the Relay Test Set", p, true);
            } else am.clear(k);
        }
    }

    /** RC ied · pick-up err % · time err % · last cal (days ago) · due in days · last test · tests · cals */
    public static void rows(World w, List<String[]> out) {
        GridData g = GridData.get(w);
        long now = w.getTotalWorldTime();
        for (Map.Entry<Long, GridData.Node> en : g.nodes().entrySet()) {
            if (en.getValue().kind != GridKind.IED) continue;
            InfraData.Cal c = of(w, en.getKey());
            BlockPos p = BlockPos.fromLong(en.getKey());
            double due = dueDays(w, en.getKey());
            out.add(new String[]{"RC", p.getX() + "," + p.getY() + "," + p.getZ(), String.format(Locale.ROOT, "%+.2f", c.pick * 100), String.format(Locale.ROOT, "%+.2f", c.time * 100),
                    String.format(Locale.ROOT, "%.1f", (now - c.calAt) / (double) DAY), due == Double.MAX_VALUE ? "-" : String.format(Locale.ROOT, "%.1f", due),
                    c.tests == 0 ? "never" : c.lastPass ? "PASS" : "FAIL", String.valueOf(c.tests), String.valueOf(c.cals)});
        }
        for (String s : InfraData.get(w).certs) { if (out.size() > 500) break; out.add(new String[]{"RCC", s}); }
    }
}
