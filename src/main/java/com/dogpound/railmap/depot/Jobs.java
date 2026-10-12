package com.dogpound.railmap.depot;

import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.server.WearData;
import com.dogpound.railmap.sim.SimData;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Locale;
import java.util.UUID;

/**
 * Workshop job cards (her ChatGPT list #54): a work order for one vehicle - what to do, who it is assigned to (a player,
 * or the depot's fitters), the spares it needs from the Stores, and the record of its completion. EXAM, REPAIR and
 * COUPLERS are worked at the Depot Control Office's shop floor (the vehicle parked within the depot's reach); WHEELS,
 * BOGIE, BRAKE TEST and SERVICE are closed by the machine that does them (wheel lathe, bogie drop, brake test rig,
 * servicing point / wash).
 */
public final class Jobs {
    private Jobs() {}

    public static final int EXAM = 0, REPAIR = 1, WHEELS = 2, BOGIE = 3, BRAKE_TEST = 4, SERVICE = 5, COUPLERS = 6;
    public static final String[] KINDS = {"EXAM", "REPAIR", "WHEELS", "BOGIE", "BRAKE TEST", "SERVICE", "COUPLERS"};
    public static final String[] STATES = {"OPEN", "ASSIGNED", "IN WORK", "DONE", "CANCELLED"};
    /** km of running a depot counts as "within reach" of its office */
    public static final int REACH = 48;

    public static boolean shopFloor(int kind) { return kind == EXAM || kind == REPAIR || kind == COUPLERS; }

    /** the spares a job card lists */
    static void spares(FleetData.Job j) {
        java.util.Arrays.fill(j.spares, 0);
        switch (j.kind) {
            case EXAM -> j.spares[ItemSpare.Spare.FILTER_SET.ordinal()] = 1;
            case COUPLERS -> j.spares[ItemSpare.Spare.COUPLER.ordinal()] = 2;
            case REPAIR -> {
                if (j.part == Condition.B) { j.spares[ItemSpare.Spare.BRAKE_BLOCKS.ordinal()] = 1; j.spares[ItemSpare.Spare.AIR_HOSE.ordinal()] = 1; }
                else if (j.part == Condition.BR) j.spares[ItemSpare.Spare.AXLE_BEARING.ordinal()] = 2;
                else if (j.part == Condition.EL) j.spares[ItemSpare.Spare.CONTROL_CARD.ordinal()] = 1;
                else if (j.part == Condition.E) j.spares[j.note.equals("electric") ? ItemSpare.Spare.TRACTION_MOTOR.ordinal() : ItemSpare.Spare.POWER_KIT.ordinal()] = 1;
            }
            default -> { }
        }
    }

    public static String title(int kind, int part) {
        if (kind == REPAIR && part >= 0) return "REPAIR " + WearData.Part.values()[part].label.toUpperCase(Locale.ROOT);
        return KINDS[kind];
    }

    /** raise a job card for a vehicle unless the same one is already open; returns it */
    public static FleetData.Job raise(World w, UUID unit, String name, int kind, int part, long depot, String why) {
        FleetData f = FleetData.get(w);
        for (FleetData.Job j : f.openJobs(unit)) if (j.kind == kind && j.part == part) return j;
        FleetData.Unit u = f.unit(unit);
        FleetData.Job j = f.addJob(w, unit, name, kind, part, title(kind, part), depot);
        if (kind == REPAIR && part == Condition.E) { j.note = u.electric ? "electric" : ""; spares(j); }
        u.note(w, "job card #" + j.id + " " + j.task + (why.isEmpty() ? "" : " - " + why));
        Recorder.event(w, "Depot: job card #" + j.id + " " + j.task + " for " + name);
        return j;
    }

    /** a machine finished this kind of work on the vehicle: close its open card of that kind */
    public static void machineDone(World w, UUID unit, int kind, String who) {
        FleetData f = FleetData.get(w);
        for (FleetData.Job j : f.openJobs(unit))
            if (j.kind == kind) {
                j.state = 3; j.done = w.getTotalWorldTime();
                if (j.assignee.isEmpty()) j.assignee = who;
                f.unit(unit).note(w, "job card #" + j.id + " " + j.task + " completed (" + who + ")");
            }
        f.markDirty();
    }

    /** shop-floor work: one second of it. Returns a status for the office screen. */
    static String work(World w, FleetData f, FleetData.Job j, BlockPos office, boolean unitHere, boolean playerAtIt) {
        if (j.state != 2) return "";
        if (!unitHere) return "UNIT NOT IN DEPOT";
        String short_ = Stores.shortOf(w, office, j.spares);
        if (!short_.isEmpty()) {                                                // her 48-next #16: a substitute set that is in stock
            String sub = com.dogpound.railmap.asset.Procurement.substitute(w, office, j.spares);
            if (!sub.isEmpty()) { f.unit(j.unit).note(w, "job card #" + j.id + " SUBSTITUTED " + sub); Recorder.event(w, "Depot: job card #" + j.id + " substitutes " + sub); short_ = Stores.shortOf(w, office, j.spares); }
        }
        if (!short_.isEmpty()) return "WAITING PARTS: " + short_;
        j.seconds += playerAtIt ? 3 : 1;
        if (j.seconds < DepotConfig.fitterSeconds) return "IN WORK " + j.seconds * 100 / DepotConfig.fitterSeconds + "%";
        Stores.take(w, office, j.spares);
        complete(w, f, j);
        return "DONE";
    }

    /** what the finished card does to the vehicle */
    static void complete(World w, FleetData f, FleetData.Job j) {
        FleetData.Unit u = f.unit(j.unit);
        WearData wd = WearData.get(w);
        WearData.Record r = wd.of(j.unit);
        long now = w.getTotalWorldTime();
        j.state = 3;
        j.done = now;
        switch (j.kind) {
            case EXAM -> {
                u.examKm = u.km; u.examHours = u.hours; u.examTick = now; u.examFaults = u.faults; u.dueAlarm = false;
                StringBuilder found = new StringBuilder();
                for (WearData.Part p : WearData.Part.values()) {
                    float wear = r.wear[p.ordinal()];
                    if (wear < 50) continue;
                    int kind = p == WearData.Part.WHEELS ? WHEELS : REPAIR;
                    raise(w, j.unit, u.name, kind, kind == REPAIR ? p.ordinal() : -1, j.depot, "found at exam: " + Math.round(wear) + "% worn");
                    found.append(found.length() > 0 ? ", " : "").append(p.label);
                }
                SimData.Car c = SimData.get(w).of(j.unit);
                if (c.coupler[0] < 0.6f || c.coupler[1] < 0.6f) raise(w, j.unit, u.name, COUPLERS, -1, j.depot, "worn knuckles found at exam");
                if (u.loco && !u.steam) { c.oil = 1f; c.coolant = 1f; SimData.get(w).markDirty(); }
                u.note(w, "EXAM passed" + (found.length() > 0 ? " - defects: " + found : " - no defects"));
                com.dogpound.railmap.alarm.AlarmManager.get(w).clear("exam:" + j.unit);
            }
            case REPAIR -> {
                if (j.part >= 0) {
                    float worn = r.wear[j.part];
                    r.wear[j.part] = 0;
                    r.warned &= ~(1 << j.part);
                    FleetData.Kind k = FleetData.Kind.values()[j.part];
                    FleetData.Comp off = f.replace(w, u, k, null, 0);
                    com.dogpound.railmap.asset.Reman.coreBack(w, j, worn, off == null ? "" : off.serial + " off " + u.name);   // her 48-next #24: the old part is a core
                    if (j.part == Condition.B && DepotConfig.brakeTestAfterBrakeWork) { u.brakeWork = true; u.brakeOk = false; }
                    u.trend.clear();
                    com.dogpound.railmap.alarm.AlarmManager.get(w).clear("cond:" + j.unit + ":" + j.part);
                    u.level[j.part] = 0;
                }
                if (r.worst() == 0) { r.kmSinceService = 0; r.servicedAt = now; }
                wd.markDirty();
            }
            case COUPLERS -> {
                SimData.Car c = SimData.get(w).of(j.unit);
                c.coupler[0] = 1f; c.coupler[1] = 1f;
                SimData.get(w).markDirty();
                f.replace(w, u, FleetData.Kind.COUPLERS, null, 0);
            }
            default -> { }
        }
        u.note(w, "job card #" + j.id + " " + j.task + " completed" + (j.assignee.isEmpty() ? "" : " by " + j.assignee));
        Recorder.event(w, "Depot: job card #" + j.id + " " + j.task + " on " + u.name + " completed");
        f.markDirty();
    }

    public static String line(FleetData.Job j) {
        return String.format(Locale.ROOT, "#%d %s %s %s %s", j.id, cut(j.unitName, 10), j.task, j.assignee.isEmpty() ? "-" : cut(j.assignee, 8), STATES[j.state]);
    }

    static String cut(String s, int n) { return s.length() <= n ? s : s.substring(0, n - 1) + "."; }
}
