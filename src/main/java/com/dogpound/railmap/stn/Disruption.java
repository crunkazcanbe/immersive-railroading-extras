package com.dogpound.railmap.stn;

import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.Disruptions;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.incident.Announcer;
import com.dogpound.railmap.server.StationData;
import net.minecraft.world.World;

import java.util.*;

/**
 * Passenger disruption management (ChatGPT batch 3 #35), on top of the PIS feed (station.Pis) and the announcer: when a
 * service is cancelled or held, work out how its passengers can still get where they are going - another line from
 * here that reaches the same stations (direct), or one change, avoiding lines that are themselves held - and walking
 * times at the change (Interchange). Pis.tick posts the results as ALT messages on every board at the station (and
 * speaks them at the announce priority); the Disruption Control Desk shows them, posts them on demand, and runs ticket
 * acceptance and rail replacement buses.
 */
public final class Disruption {
    private Disruption() {}

    /** lines with a driverless train held by an incident right now */
    public static Set<String> heldLines(World w) {
        Set<String> o = new HashSet<>();
        int dim = w.provider.getDimension();
        for (RailwayData.AutoTrain a : RailwayData.get(w).trains().values()) if (!a.line.isEmpty() && Disruptions.isHeld(dim, a.loco)) o.add(a.line);
        return o;
    }

    /** the stations a train still has to call at after `here` (in its running direction) */
    static List<Long> beyond(World w, Announcer.Due d, long here) {
        RailwayData rd = RailwayData.get(w);
        RailwayData.Line l = rd.line(d.line);
        RailwayData.AutoTrain a = rd.train(d.loco);
        if (l == null || l.stations.isEmpty()) return Collections.emptyList();
        int i = l.stations.indexOf(here);
        List<Long> o = new ArrayList<>();
        int dir = a == null ? 1 : a.direction;
        if (i < 0) { o.addAll(l.stations); return o; }
        for (int k = i + dir; k >= 0 && k < l.stations.size(); k += dir) o.add(l.stations.get(k));
        return o;
    }

    /** travel alternatives for the passengers of a disrupted service at this station (at most 3 lines of advice) */
    public static List<String> alternatives(World w, long here, String hereName, Announcer.Due d) {
        RailwayData rd = RailwayData.get(w);
        StationData sd = StationData.get(w);
        Set<String> held = heldLines(w);
        held.add(d.line);
        Map<String, List<String>> advice = new LinkedHashMap<>();
        List<Long> dests = beyond(w, d, here);
        int looked = 0;
        for (long x : dests) {
            if (looked++ >= 6) break;
            String xn = Autopilot.nameOf(sd, x);
            String adv = null;
            for (RailwayData.Line m : rd.lines().values())
                if (!held.contains(m.name) && m.stations.contains(here) && m.stations.contains(x)) { adv = "use the " + m.name + " line"; break; }
            if (adv == null)
                outer:
                for (RailwayData.Line m : rd.lines().values()) {
                    if (held.contains(m.name) || !m.stations.contains(here)) continue;
                    for (long c : m.stations) {
                        if (c == here) continue;
                        for (RailwayData.Line n : rd.lines().values())
                            if (n != m && !held.contains(n.name) && n.stations.contains(c) && n.stations.contains(x)) {
                                String cn = Autopilot.nameOf(sd, c);
                                adv = "take the " + m.name + " line and change at " + cn + " for the " + n.name + " line (allow " + Interchange.walkMin(w, cn, m.name, n.name) + " min)";
                                break outer;
                            }
                    }
                }
            if (adv == null) adv = "no rail alternative - please ask staff";
            advice.computeIfAbsent(adv, k -> new ArrayList<>()).add(xn);
        }
        List<String> o = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : advice.entrySet()) {
            if (o.size() >= 3) break;
            List<String> xs = e.getValue();
            String to = xs.size() <= 3 ? String.join(", ", xs) : xs.get(0) + ", " + xs.get(1) + " and " + (xs.size() - 2) + " more";
            o.add("Disruption on the " + d.line + " line: for " + to + ", " + e.getKey());
        }
        return o;
    }

    /** every disrupted service calling at a station, with its alternatives (the desk + the console) */
    public static Map<Announcer.Due, List<String>> at(World w, long key, String name) {
        Map<Announcer.Due, List<String>> o = new LinkedHashMap<>();
        List<Announcer.Due> due;
        try { due = Announcer.due(w, key, name); } catch (RuntimeException | LinkageError e) { return o; }
        for (Announcer.Due d : due)
            if (d.held || d.lateMin >= com.dogpound.railmap.incident.IncidentConfig.cancelMinutes) o.put(d, alternatives(w, key, name, d));
        return o;
    }
}
