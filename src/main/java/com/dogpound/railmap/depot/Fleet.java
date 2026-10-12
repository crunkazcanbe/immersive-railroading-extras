package com.dogpound.railmap.depot;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.economy.PassengerData;
import com.dogpound.railmap.server.WearData;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Rolling-stock management (her ChatGPT list #59 automatic depot routing, #60 fleet allocation, #61 spare locomotives).
 * A driverless train is sent to a depot by making it a "sent" train to the depot's stabling road (what it was doing is
 * kept and given back on RETURN TO SERVICE). Every five seconds:
 * <ul>
 *   <li><b>spares</b>: a train in service that has failed (an engine, electrics, brakes or bearings FAILED, or wrecked)
 *   is covered by the nearest standby spare, which takes over its line and place in it; the failed one limps to the
 *   depot (or waits for rescue if it can't);</li>
 *   <li><b>routing</b>: trains with an exam due, a CRITICAL part or badly in need of servicing go to the nearest depot
 *   that can do the work, when its office has AUTO ROUTE on; with NIGHT STABLING on its trains stable at 22:00 and go
 *   back out at 05:00; with AUTO RETURN on, a train whose cards are all closed and is fit to run goes back out;</li>
 *   <li><b>allocation</b>: each line with a target number of trains gets spares allocated (best condition, most exam
 *   headroom, enough seats for the line's riders) or its worst extra train stood down to spare.</li>
 * </ul>
 */
public final class Fleet {
    private Fleet() {}

    public static TileDepotOffice nearestOffice(World w, BlockPos at, boolean needRoad) {
        TileDepotOffice best = null;
        double bd = Double.MAX_VALUE;
        for (TileDepotOffice o : TileDepotOffice.loaded(w)) {
            if (needRoad && o.road() == 0) continue;
            double d = o.getPos().distanceSq(at);
            if (d < bd) { bd = d; best = o; }
        }
        return best;
    }

    public static TileDepotOffice office(World w, long pos) {
        for (TileDepotOffice o : TileDepotOffice.loaded(w)) if (o.getPos().toLong() == pos) return o;
        return null;
    }

    static BlockPos at(Locomotive l) { Vec3d p = l.getPosition(); return new BlockPos(p.x, p.y, p.z); }

    // ---- routing -------------------------------------------------------------------------------------------------------
    public static String sendToDepot(World w, UUID loco, TileDepotOffice o, String why) {
        RailwayData rd = RailwayData.get(w);
        RailwayData.AutoTrain a = rd.train(loco);
        FleetData f = FleetData.get(w);
        FleetData.Unit u = f.unit(loco);
        if (o == null) return "No depot office";
        if (o.road() == 0) return "The depot has no stabling road - wrench a rail, then click the office's ROAD plate";
        if (a == null) return u.name + " is not a driverless train - drive it in by hand";
        if (u.role == FleetData.DEPOT) return u.name + " is already going to / at the depot";
        if (u.role != FleetData.SPARE || u.retMode < 0) {
            u.retMode = a.mode.ordinal(); u.retLine = a.line; u.retIndex = a.index; u.retDir = a.direction; u.retHome = a.home;
        }
        a.calls.clear();
        a.mode = RailwayData.Mode.SEND;
        a.home = o.road();
        u.role = FleetData.DEPOT;
        u.depot = o.getPos().toLong();
        rd.markDirty();
        f.markDirty();
        u.note(w, "sent to " + o.depotName() + " - " + why);
        Recorder.event(w, "Depot: " + u.name + " routed to " + o.depotName() + " (" + why + ")");
        return "§a" + u.name + " routed to " + o.depotName() + " - " + why;
    }

    /** "" = fit to run, else why not (open cards, failed / critical parts, brake test) - across the whole train */
    public static String unfit(World w, FleetData f, Locomotive l) {
        WearData wd = WearData.get(w);
        List<EntityCoupleableRollingStock> train;
        try { train = l.getTrain(); } catch (RuntimeException e) { train = Collections.singletonList(l); }
        for (EntityCoupleableRollingStock c : train) {
            FleetData.Unit u = f.peek(c.getUUID());
            if (u == null) continue;
            if (u.role == FleetData.STOPPED) return u.name + " STOPPED (out of service)";
            List<FleetData.Job> open = f.openJobs(c.getUUID());
            if (!open.isEmpty()) return u.name + ": " + open.size() + " job card(s) open (#" + open.get(0).id + " " + open.get(0).task + ")";
            if (DepotConfig.brakeTestAfterBrakeWork && u.brakeWork && !u.brakeOk) return u.name + ": brake test needed after brake work";
            if (!u.brakeOk) return u.name + ": failed its brake test";
            WearData.Record r = wd.peek(c.getUUID());
            if (r != null && Condition.worst(r) >= Condition.CRITICAL) return u.name + ": " + r.worstPart().label + " " + Condition.NAMES[Condition.worst(r)];
        }
        return "";
    }

    public static String returnToService(World w, UUID loco, boolean force) {
        RailwayData rd = RailwayData.get(w);
        RailwayData.AutoTrain a = rd.train(loco);
        FleetData f = FleetData.get(w);
        FleetData.Unit u = f.unit(loco);
        if (a == null) return u.name + " is not a driverless train";
        Locomotive l = Autopilot.find(w, loco);
        if (l != null && !force) {
            String no = unfit(w, f, l);
            if (!no.isEmpty()) return "§cNOT FIT TO RUN: " + no;
        }
        if (u.retMode < 0) { u.role = FleetData.SERVICE; return u.name + " has nothing to go back to - give it a line"; }
        a.mode = RailwayData.Mode.byOrdinal(u.retMode);
        a.line = u.retLine; a.index = u.retIndex; a.direction = u.retDir; a.home = u.retHome;
        u.retMode = -1;
        u.role = FleetData.SERVICE;
        u.failed = false;
        rd.markDirty();
        f.markDirty();
        u.note(w, "returned to service" + (a.line.isEmpty() ? "" : " on " + a.line));
        Recorder.event(w, "Depot: " + u.name + " returned to service");
        AlarmManager.get(w).clear("failed:" + loco);
        return "§a" + u.name + " returned to service" + (a.line.isEmpty() ? "" : " on " + a.line);
    }

    public static String makeSpare(World w, UUID loco, TileDepotOffice o) {
        RailwayData rd = RailwayData.get(w);
        FleetData f = FleetData.get(w);
        FleetData.Unit u = f.unit(loco);
        if (o == null || o.road() == 0) return "The depot needs a stabling road for its spares";
        RailwayData.AutoTrain a = rd.setTrain(loco);
        a.calls.clear();
        a.mode = RailwayData.Mode.SEND;
        a.home = o.road();
        u.role = FleetData.SPARE;
        u.depot = o.getPos().toLong();
        u.retMode = -1;
        rd.markDirty();
        f.markDirty();
        u.note(w, "made a standby spare at " + o.depotName());
        return "§a" + u.name + " is a standby spare at " + o.depotName();
    }

    public static String allocate(World w, UUID loco, String line) {
        RailwayData rd = RailwayData.get(w);
        if (rd.line(line) == null) return "No line " + line;
        FleetData f = FleetData.get(w);
        FleetData.Unit u = f.unit(loco);
        Locomotive l = Autopilot.find(w, loco);
        if (l != null) { String no = unfit(w, f, l); if (!no.isEmpty()) return "§cNot available: " + no; }
        RailwayData.AutoTrain a = rd.setTrain(loco);
        a.calls.clear();
        a.mode = RailwayData.Mode.LINE;
        a.line = line;
        a.index = 0;
        u.role = FleetData.SERVICE;
        u.retMode = -1;
        rd.markDirty();
        f.markDirty();
        u.note(w, "allocated to " + line);
        Recorder.event(w, "Fleet: " + u.name + " allocated to " + line);
        return "§a" + u.name + " allocated to " + line;
    }

    // ---- the five-second fleet manager ---------------------------------------------------------------------------------
    static void tick(World w, long now) {
        FleetData f = FleetData.get(w);
        RailwayData rd = RailwayData.get(w);
        WearData wd = WearData.get(w);
        AlarmManager am = AlarmManager.get(w);
        int hour = DepotClock.hour(w);
        Servicing.lines(w, f);
        for (RailwayData.AutoTrain a : new ArrayList<>(rd.trains().values())) {
            Locomotive l = Autopilot.find(w, a.loco);
            if (l == null) continue;
            FleetData.Unit u = f.unit(a.loco);
            // 1. failed in service: a spare covers it
            String failed = failedHere(wd, f, l);
            if (!failed.isEmpty() && (a.mode == RailwayData.Mode.LINE || a.mode == RailwayData.Mode.SHUTTLE) && !u.failed) {
                u.failed = true;
                u.note(w, "FAILED IN SERVICE - " + failed);
                am.raise(w, "failed:" + a.loco, AlarmManager.Cat.INCIDENT, AlarmManager.Sev.MAJOR, "FAILED IN SERVICE: " + u.name + (a.line.isEmpty() ? "" : " on " + a.line) + " - " + failed, at(l), true);
                Recorder.event(w, "Fleet: " + u.name + " failed in service (" + failed + ")");
                String cover = coverWithSpare(w, f, rd, a, l);
                TileDepotOffice o = nearestOffice(w, at(l), true);
                if (o != null && !failed.startsWith("wreck")) sendToDepot(w, a.loco, o, "failed in service");
                am.raise(w, "spare:" + a.loco, AlarmManager.Cat.GENERAL, AlarmManager.Sev.INFO, cover, at(l), false);
                continue;
            }
            // 2. depot routing
            if (u.role == FleetData.SERVICE) {
                String need = needsDepot(w, f, wd, l);
                boolean night = hour >= 22 || hour < 5;
                TileDepotOffice o = need.isEmpty() ? null : best(w, f, l, need);
                if (o != null && o.autoRoute()) { sendToDepot(w, a.loco, o, need); continue; }
                if (night && !a.line.isEmpty()) {
                    TileDepotOffice n = nearestOffice(w, at(l), true);
                    if (n != null && n.nightStabling()) { sendToDepot(w, a.loco, n, "night stabling"); u.note(w, "stabled for the night"); }
                }
            } else if (u.role == FleetData.DEPOT && u.retMode >= 0) {
                TileDepotOffice o = office(w, u.depot);
                boolean day = hour >= 5 && hour < 22;
                boolean stabledOnly = u.log.size() > 0 && u.log.getFirst().contains("stabled for the night");
                if (o != null && o.autoReturn() && unfit(w, f, l).isEmpty() && needsDepot(w, f, wd, l).isEmpty() && (day || !o.nightStabling()))
                    returnToService(w, a.loco, false);
                else if (stabledOnly && day && unfit(w, f, l).isEmpty()) returnToService(w, a.loco, false);
            }
        }
        // 3. line targets (not overnight while trains are stabled)
        boolean stabling = false;
        for (TileDepotOffice o : TileDepotOffice.loaded(w)) stabling |= o.nightStabling();
        if (stabling && (hour >= 22 || hour < 5)) { f.markDirty(); return; }
        for (Map.Entry<String, Integer> e : f.lineTarget.entrySet()) {
            if (e.getValue() <= 0 || rd.line(e.getKey()) == null) continue;
            List<RailwayData.AutoTrain> on = new ArrayList<>();
            for (RailwayData.AutoTrain a : rd.trains().values())
                if (e.getKey().equals(a.line) && (a.mode == RailwayData.Mode.LINE || a.mode == RailwayData.Mode.SHUTTLE) && f.unit(a.loco).role == FleetData.SERVICE) on.add(a);
            if (on.size() < e.getValue()) {
                UUID pick = bestSpare(w, f, rd, e.getKey(), null);
                if (pick != null) allocate(w, pick, e.getKey());
                else am.raise(w, "short:" + e.getKey(), AlarmManager.Cat.GENERAL, AlarmManager.Sev.INFO, "Fleet: " + e.getKey() + " is short of trains (" + on.size() + " of " + e.getValue() + ") and no spare is fit", null, true);
            } else {
                am.clear("short:" + e.getKey());
                if (on.size() > e.getValue()) {
                    RailwayData.AutoTrain worst = null;
                    float ww = -1;
                    for (RailwayData.AutoTrain a : on) { WearData.Record r = wd.peek(a.loco); float x = r == null ? 0 : r.worst(); if (x > ww) { ww = x; worst = a; } }
                    Locomotive l = worst == null ? null : Autopilot.find(w, worst.loco);
                    TileDepotOffice o = l == null ? null : nearestOffice(w, at(l), true);
                    if (o != null) makeSpare(w, worst.loco, o);
                }
            }
        }
        f.markDirty();
    }

    /** why this train is failed in service, or "" */
    static String failedHere(WearData wd, FleetData f, Locomotive l) {
        if (l.wreck != 0) return "wrecked";
        try {
            for (EntityCoupleableRollingStock c : l.getTrain()) {
                int p = Condition.failedPart(wd.peek(c.getUUID()));
                if (p >= 0) return f.unit(c.getUUID()).name + " " + WearData.Part.values()[p].label + " failed";
            }
        } catch (RuntimeException ignored) { }
        return "";
    }

    /** why this train should go to a depot, or "" (exam due, a critical part, badly needs servicing) */
    static String needsDepot(World w, FleetData f, WearData wd, Locomotive l) {
        try {
            for (EntityCoupleableRollingStock c : l.getTrain()) {
                FleetData.Unit u = f.peek(c.getUUID());
                if (u == null) continue;
                WearData.Record r = wd.peek(c.getUUID());
                if (r != null && Condition.worst(r) >= Condition.CRITICAL) return u.name + " " + r.worstPart().label + " critical";
                String due = Predict.due(w, f, u);
                if (!due.isEmpty()) return u.name + " exam due";
                if (u.passenger && Servicing.need(u) >= 85) return u.name + " needs servicing";
            }
        } catch (RuntimeException ignored) { }
        return "";
    }

    /** the nearest office whose depot has the machine the work needs */
    static TileDepotOffice best(World w, FleetData f, Locomotive l, String need) {
        int cap = need.contains("Wheels") ? TileDepotOffice.CAP_LATHE : need.contains("servicing") ? TileDepotOffice.CAP_SERVICE : 0;
        TileDepotOffice best = null;
        double bd = Double.MAX_VALUE;
        BlockPos at = at(l);
        for (TileDepotOffice o : TileDepotOffice.loaded(w)) {
            if (o.road() == 0 || cap != 0 && (o.caps() & cap) == 0) continue;
            double d = o.getPos().distanceSq(at);
            if (d < bd) { bd = d; best = o; }
        }
        return best != null ? best : nearestOffice(w, at, true);
    }

    /** the best standby spare for a line: fit to run, best condition, most exam headroom, enough seats */
    static UUID bestSpare(World w, FleetData f, RailwayData rd, String line, BlockPos near) {
        WearData wd = WearData.get(w);
        PassengerData.LineStat ls = PassengerData.get(w).line(line);
        double riders = ls == null ? 0 : Math.max(ls.ridersDay, ls.ridersYesterday);
        UUID best = null;
        double bs = -1e9;
        for (RailwayData.AutoTrain a : rd.trains().values()) {
            FleetData.Unit u = f.peek(a.loco);
            if (u == null || u.role != FleetData.SPARE) continue;
            Locomotive l = Autopilot.find(w, a.loco);
            if (l == null || !unfit(w, f, l).isEmpty() || !failedHere(wd, f, l).isEmpty()) continue;
            WearData.Record r = wd.peek(a.loco);
            double score = 100 - (r == null ? 0 : r.worst());
            score += 40 * (1 - Math.min(1, Predict.used(w, f, u)));
            int seats = PassengerData.get(w).seats(a.loco);
            if (riders > 0) score += seats >= riders / 20 ? 20 : seats * 20.0 / Math.max(1, riders / 20);
            if (near != null) score -= Math.sqrt(at(l).distanceSq(near)) / 32;
            if (score > bs) { bs = score; best = a.loco; }
        }
        return best;
    }

    /** a spare takes over a failed train's service */
    static String coverWithSpare(World w, FleetData f, RailwayData rd, RailwayData.AutoTrain failed, Locomotive l) {
        UUID pick = bestSpare(w, f, rd, failed.line, at(l));
        if (pick == null) return "No standby spare fit to cover " + (failed.line.isEmpty() ? "it" : failed.line) + " - rescue needed";
        RailwayData.AutoTrain s = rd.train(pick);
        FleetData.Unit su = f.unit(pick);
        s.calls.clear();
        s.mode = failed.mode;
        s.line = failed.line;
        s.index = failed.index;
        s.direction = failed.direction;
        su.role = FleetData.SERVICE;
        su.note(w, "spare dispatched to cover " + f.unit(failed.loco).name + (failed.line.isEmpty() ? "" : " on " + failed.line));
        rd.markDirty();
        String msg = "Spare " + su.name + " dispatched to cover " + f.unit(failed.loco).name + (failed.line.isEmpty() ? "" : " on " + failed.line);
        Recorder.event(w, "Fleet: " + msg);
        return msg;
    }
}
