package com.dogpound.railmap.depot;

import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.entity.LocomotiveSteam;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.server.Electric;
import com.dogpound.railmap.server.RailProps;
import net.minecraft.world.World;

import java.util.Locale;
import java.util.UUID;

/**
 * Crew scheduling and duty management (her ChatGPT list #84). Drivers - players who sign on at a Crew Booking-On Desk,
 * or depot drivers hired there - are assigned to trains, sign on and off, take breaks, and hand trains over (logged).
 * A line marked ATTENDED is not authorised for unattended operation: its driverless trains only leave a station with
 * a driver assigned who is on duty, not on a break, inside their hours, passed on the traction (diesel / electric /
 * steam) and knows the route. Driving time counts while their train moves; after the break interval a break is due,
 * at the duty limit they are over hours and must be relieved.
 */
public final class Crew {
    private Crew() {}

    public static final int DIESEL = 1, ELECTRIC = 2, STEAM = 4;
    public static final String[] TRACTION = {"DIESEL", "ELECTRIC", "STEAM"};

    public static int traction(World w, Locomotive l) {
        if (l instanceof LocomotiveSteam) return STEAM;
        return Electric.isElectric(l, RailProps.get(w).of(l.getUUID())) ? ELECTRIC : DIESEL;
    }

    public static FleetData.Driver driverOf(FleetData f, UUID train) {
        for (FleetData.Driver d : f.drivers.values()) if (train.equals(d.train)) return d;
        return null;
    }

    public static double dutyHours(World w, FleetData.Driver d) { return d.onDuty ? DepotClock.hours(d.dutyStart, w.getTotalWorldTime()) : 0; }
    public static double sinceBreak(World w, FleetData.Driver d) { return d.onDuty && d.drivingSince > 0 ? DepotClock.hours(d.drivingSince, w.getTotalWorldTime()) : 0; }
    public static boolean onBreak(World w, FleetData.Driver d) { return d.breakUntil > w.getTotalWorldTime(); }

    /** null = this driver may take this train now, else why not */
    public static String cannot(World w, FleetData.Driver d, Locomotive l, String line) {
        if (d == null) return "NO DRIVER";
        if (!d.onDuty) return d.name + " NOT SIGNED ON";
        if (onBreak(w, d)) return d.name + " ON BREAK";
        if (dutyHours(w, d) >= DepotConfig.dutyMaxHours) return d.name + " OVER HOURS - relief needed";
        if (sinceBreak(w, d) >= DepotConfig.breakAfterHours) return d.name + " BREAK DUE";
        if (l != null && (d.quals & traction(w, l)) == 0) return d.name + " NOT PASSED ON " + TRACTION[Integer.numberOfTrailingZeros(traction(w, l))];
        if (!line.isEmpty() && !d.routes.contains(line)) return d.name + " NO ROUTE KNOWLEDGE (" + line + ")";
        return null;
    }

    /** a train on an attended line about to leave: null = it may go */
    public static String departure(World w, RailwayData.AutoTrain a, Locomotive l) {
        if (!DepotConfig.crewRequired || a.line.isEmpty()) return null;
        FleetData f = FleetData.get(w);
        if (!f.attended.contains(a.line)) return null;
        return cannot(w, driverOf(f, a.loco), l, a.line);
    }

    /** put a driver on a train (a handover if someone else had it) */
    public static String assign(World w, FleetData f, FleetData.Driver d, UUID train, String where) {
        FleetData.Driver had = driverOf(f, train);
        FleetData.Unit u = f.unit(train);
        if (had == d) return d.name + " already has " + u.name;
        if (had != null) {
            had.train = null;
            f.crewNote(w, String.format(Locale.ROOT, "HANDOVER %s%s: %s -> %s", u.name, where.isEmpty() ? "" : " at " + where, had.name, d.name));
        } else f.crewNote(w, d.name + " takes " + u.name + (where.isEmpty() ? "" : " at " + where));
        d.train = train;
        if (d.onDuty && d.drivingSince == 0) d.drivingSince = w.getTotalWorldTime();
        f.markDirty();
        return "§a" + d.name + " is driving " + u.name;
    }

    public static String signOn(World w, FleetData f, FleetData.Driver d) {
        if (d.onDuty) return d.name + " is already on duty";
        d.onDuty = true;
        d.dutyStart = w.getTotalWorldTime();
        d.drivingSince = d.train != null ? d.dutyStart : 0;
        d.drivenToday = 0;
        d.breakUntil = 0;
        f.crewNote(w, d.name + " SIGNED ON");
        f.markDirty();
        return "§a" + d.name + " signed on at " + DepotClock.hhmm(w);
    }

    public static String signOff(World w, FleetData f, FleetData.Driver d) {
        if (!d.onDuty) return d.name + " is not on duty";
        f.crewNote(w, String.format(Locale.ROOT, "%s SIGNED OFF after %.1f h (%.1f h driving)%s", d.name, dutyHours(w, d), d.drivenToday,
                d.train == null ? "" : " - " + f.unit(d.train).name + " left without a driver"));
        d.onDuty = false;
        d.train = null;
        d.drivingSince = 0;
        f.markDirty();
        return "§e" + d.name + " signed off";
    }

    public static String takeBreak(World w, FleetData f, FleetData.Driver d) {
        if (!d.onDuty) return d.name + " is not on duty";
        d.breakUntil = w.getTotalWorldTime() + Math.round(DepotConfig.breakMinutes * DepotClock.TICKS_PER_HOUR / 60);
        d.drivingSince = 0;
        f.crewNote(w, d.name + " on a " + DepotConfig.breakMinutes + " min break");
        f.markDirty();
        return "§e" + d.name + " on a " + DepotConfig.breakMinutes + " minute break";
    }

    /** once a second: driving time, breaks over, hours alarms */
    static void tick(World w, FleetData f, long now) {
        if (f.drivers.isEmpty()) return;
        AlarmManager am = AlarmManager.get(w);
        for (FleetData.Driver d : f.drivers.values()) {
            if (!d.onDuty) { am.clear("crew:" + d.name); continue; }
            if (d.breakUntil > 0 && d.breakUntil <= now) { d.breakUntil = 0; d.drivingSince = d.train != null ? now : 0; f.crewNote(w, d.name + " back from break"); }
            if (d.train != null && !onBreak(w, d)) {
                Locomotive l = Autopilot.find(w, d.train);
                if (l != null && l.getCurrentSpeed() != null && Math.abs(l.getCurrentSpeed().metric()) > 1) {
                    d.drivenToday += 20 / DepotClock.TICKS_PER_HOUR;
                    if (d.drivingSince == 0) d.drivingSince = now;
                }
            }
            double duty = dutyHours(w, d), drive = sinceBreak(w, d);
            if (duty >= DepotConfig.dutyMaxHours)
                am.raise(w, "crew:" + d.name, AlarmManager.Cat.GENERAL, AlarmManager.Sev.MINOR, "CREW: " + d.name + " OVER HOURS (" + Math.round(duty) + " h on duty) - relieve and sign off", null, true);
            else if (drive >= DepotConfig.breakAfterHours)
                am.raise(w, "crew:" + d.name, AlarmManager.Cat.GENERAL, AlarmManager.Sev.INFO, "CREW: " + d.name + " BREAK DUE (" + Math.round(drive) + " h driving)", null, true);
            else am.clear("crew:" + d.name);
        }
    }
}
