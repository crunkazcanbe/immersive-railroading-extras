package com.dogpound.railmap.stn;

import cam72cam.immersiverailroading.entity.Locomotive;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.Disruptions;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.depot.Fleet;
import com.dogpound.railmap.economy.PassengerData;
import com.dogpound.railmap.server.StationData;
import com.dogpound.railmap.track.Rails;
import net.minecraft.world.World;

import java.util.*;

/**
 * Train service diagrams (ChatGPT batch 3 #31): every driverless unit gets a diagram number (e.g. 1R03) and its daily
 * workings are recorded as it runs - each arrival and departure with the station, time, line and lateness; trips (line
 * ends reached), first departure, last arrival, punctuality. Yesterday's totals are kept. A diagram may have planned
 * hours; with ENFORCE on (config diagramHours), the unit is sent to depot when its diagram ends and returned to
 * service when it starts again.
 */
public final class Diagrams {
    private Diagrams() {}

    private static final Map<UUID, Long> DWELL = new HashMap<>();

    static void track(World w) {
        StnData d = StnData.get(w);
        RailwayData rd = RailwayData.get(w);
        StationData sd = StationData.get(w);
        long now = w.getTotalWorldTime(), day = now / 24000;
        int dim = w.provider.getDimension(), hour = PassengerData.hour(w);
        for (RailwayData.AutoTrain a : rd.trains().values()) {
            Autopilot.Run r = Autopilot.run(a.loco);
            String name = a.label;
            if (name.isEmpty()) { Locomotive l = Autopilot.find(w, a.loco); if (l != null) name = Rails.name(l); }
            StnData.Diagram g = d.diagram(a.loco, name, a.line);
            if (g.day != day) {
                if (g.day >= 0) { g.callsY = g.calls; g.lateY = g.lateCalls; }
                g.day = day; g.work.clear(); g.trips = g.calls = g.lateCalls = 0; g.firstDep = g.lastArr = 0; g.lateSum = 0;
                d.markDirty();
            }
            Long was = DWELL.get(a.loco);
            long here = r != null && r.dwelling ? r.dwellStation : Long.MIN_VALUE;
            if (was == null || was != here) {
                int late = Disruptions.delaySeconds(a.loco);
                if (was != null && was != Long.MIN_VALUE) {                      // departed
                    g.work.add(now + "|" + Autopilot.nameOf(sd, was) + "|DEP|" + late + "|" + a.line);
                    if (g.firstDep == 0) g.firstDep = now;
                }
                if (here != Long.MIN_VALUE) {                                    // arrived
                    g.work.add(now + "|" + Autopilot.nameOf(sd, here) + "|ARR|" + late + "|" + a.line);
                    g.calls++; g.lastArr = now; g.lateSum += late;
                    if (late >= 120) g.lateCalls++;
                    RailwayData.Line l = rd.line(a.line);
                    if (l != null && !l.stations.isEmpty() && (l.stations.get(0) == here || l.stations.get(l.stations.size() - 1) == here)) g.trips++;
                }
                while (g.work.size() > 160) g.work.remove(0);
                DWELL.put(a.loco, here);
                d.markDirty();
            }
            // diagram hours
            if (StnConfig.diagramHours && g.enforce && g.start >= 0 && g.end >= 0 && g.start != g.end) {
                boolean in = g.start < g.end ? hour >= g.start && hour < g.end : hour >= g.start || hour < g.end;
                if (!in && !g.offDiagram) {
                    Locomotive l = Autopilot.find(w, a.loco);
                    String res = Fleet.sendToDepot(w, a.loco, l == null ? null : Fleet.nearestOffice(w, new net.minecraft.util.math.BlockPos(l.getPosition().x, l.getPosition().y, l.getPosition().z), true), "diagram " + g.code + " finished");
                    g.offDiagram = true; d.markDirty();
                    Recorder.event(w, "Diagram " + g.code + " (" + g.unit + ") finished at " + String.format(Locale.ROOT, "%02d:00", g.end) + ": " + res.replaceAll("§.", ""));
                } else if (in && g.offDiagram) {
                    String res = Fleet.returnToService(w, a.loco, false);
                    g.offDiagram = false; d.markDirty();
                    Recorder.event(w, "Diagram " + g.code + " (" + g.unit + ") starts at " + String.format(Locale.ROOT, "%02d:00", g.start) + ": " + res.replaceAll("§.", ""));
                }
            }
        }
        DWELL.keySet().removeIf(u -> !rd.trains().containsKey(u));
    }

    /** units in diagram order */
    public static List<Map.Entry<UUID, StnData.Diagram>> list(World w) {
        RailwayData rd = RailwayData.get(w);
        List<Map.Entry<UUID, StnData.Diagram>> o = new ArrayList<>();
        for (Map.Entry<UUID, StnData.Diagram> e : StnData.get(w).diagrams.entrySet()) if (rd.trains().containsKey(e.getKey())) o.add(e);
        o.sort(Comparator.comparing(e -> e.getValue().code));
        return o;
    }

    public static String status(World w, UUID u, StnData.Diagram g) {
        if (g.offDiagram) return "OFF DIAGRAM";
        com.dogpound.railmap.depot.FleetData.Unit f = com.dogpound.railmap.depot.FleetData.get(w).peek(u);
        if (f != null && f.role != com.dogpound.railmap.depot.FleetData.SERVICE) return com.dogpound.railmap.depot.FleetData.ROLES[f.role];
        Autopilot.Run r = Autopilot.run(u);
        if (r == null) return "NOT RUNNING";
        int late = Disruptions.delaySeconds(u) / 60;
        return (r.dwelling ? "AT STN" : "RUNNING") + (late >= 2 ? " +" + late + "M" : "");
    }

    /** the day's workings as printable lines */
    public static List<String> book(World w, StnData.Diagram g) {
        List<String> o = new ArrayList<>();
        o.add("DIAGRAM " + g.code + " - " + g.unit);
        o.add("hours " + (g.start < 0 ? "-" : String.format(Locale.ROOT, "%02d:00-%02d:00", g.start, g.end)) + (g.enforce ? " ENFORCED" : ""));
        o.add("trips " + g.trips + ", calls " + g.calls + ", late " + g.lateCalls);
        for (String s : g.work) {
            String[] p = s.split("\\|", -1);
            if (p.length < 5) continue;
            int late = Integer.parseInt(p[3]);
            o.add(Recorder.clock(w, Long.parseLong(p[0])) + " " + p[2] + " " + p[1] + (late >= 60 ? " +" + late / 60 + "m" : ""));
        }
        return o;
    }
}
