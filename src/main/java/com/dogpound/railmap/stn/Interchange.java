package com.dogpound.railmap.stn;

import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.incident.Announcer;
import net.minecraft.world.World;

import java.util.*;

/**
 * Station interchange management (ChatGPT batch 3 #28): which connections between lines at a station can be made on
 * foot. Each pair of lines has a walking time (an Interchange Board sets it per pair; otherwise the config default),
 * a step-free route adds the lift. A connection is MAKE (2+ minutes to spare), TIGHT (0-1) or MISS. The PIS uses the
 * walking time for its CONNECTION AT RISK messages; a board with HOLD set stretches a dwelling train's stop so a late
 * connection can still be made (never more than the hold window or the config cap).
 */
public final class Interchange {
    private Interchange() {}

    public static final class Conn {
        public Announcer.Due arr, dep;
        public double arrMin, depMin;
        public int walk;
        public double margin;
        public String status() { return margin >= 2 ? "MAKE" : margin >= 0 ? "TIGHT" : "MISS"; }
    }

    /** the board at a station, powered (null = none) */
    public static TileInterchangeBoard board(World w, String station) {
        for (TileInterchangeBoard t : StnUtil.loaded(w, TileInterchangeBoard.class))
            if (t.live() && station != null && station.equalsIgnoreCase(t.stationName())) return t;
        return null;
    }

    /** walking minutes from line a's platform to line b's at a station */
    public static int walkMin(World w, String station, String a, String b) {
        TileInterchangeBoard t = board(w, station);
        return t == null ? StnConfig.defaultWalkMinutes : t.walk(a, b);
    }

    /** the lines calling at a station */
    public static List<String> lines(World w, long station) {
        List<String> o = new ArrayList<>();
        for (RailwayData.Line l : RailwayData.get(w).lines().values()) if (l.stations.contains(station)) o.add(l.name);
        Collections.sort(o);
        return o;
    }

    /** minutes until a due train leaves: dwelling = what is left of its stop, else its arrival (+ a 30 s stop) */
    static double depMin(World w, Announcer.Due d) {
        if (d.dwelling) return Math.max(0, Autopilot.dwellLeft(d.loco, w.getTotalWorldTime())) / 1200.0;
        return d.etaMin + d.lateMin + 0.5;
    }

    /** every connection between two lines at this station, soonest first */
    public static List<Conn> connections(World w, long key, String name) {
        List<Conn> o = new ArrayList<>();
        List<Announcer.Due> due;
        try { due = Announcer.due(w, key, name); } catch (RuntimeException | LinkageError e) { return o; }
        for (Announcer.Due a : due) {
            if (a.dwelling || a.line.isEmpty()) continue;                   // arriving trains
            for (Announcer.Due d : due) {
                if (d == a || d.line.isEmpty() || d.line.equals(a.line)) continue;
                Conn c = new Conn();
                c.arr = a; c.dep = d;
                c.arrMin = a.etaMin + a.lateMin;
                c.depMin = depMin(w, d);
                c.walk = walkMin(w, name, a.line, d.line);
                c.margin = c.depMin - c.arrMin - c.walk;
                if (c.margin < -15) continue;                                // long gone: the next one is the connection
                o.add(c);
            }
        }
        o.sort(Comparator.comparingDouble(c -> c.arrMin));
        return o;
    }
}
