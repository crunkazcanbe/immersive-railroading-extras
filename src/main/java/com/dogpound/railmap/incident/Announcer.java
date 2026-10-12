package com.dogpound.railmap.incident;

import cam72cam.immersiverailroading.entity.Locomotive;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.Disruptions;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.block.TilePaSpeaker;
import com.dogpound.railmap.server.StationData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.*;

/**
 * Railway voice and text announcements (her ChatGPT list #76): templates (config) for delays, platform alterations,
 * cancellations, connections, the next arrival, emergencies, evacuation and all-clear, filled from live data -
 * where each driverless train is, how far from its next stop and how fast, whether an incident is holding it and why,
 * which other lines call at the stop. Spoken on the station's PA speakers (12 V) and shown as text to the people on
 * the station; every one goes into the announcement log (console OPS > PA). Stations with an Announcement Control
 * Unit in AUTO announce by themselves; emergencies and evacuations always go out on every speaker near the incident.
 */
public final class Announcer {
    private Announcer() {}

    public static final String[] KINDS = {"DELAY", "PLATFORM", "CANCEL", "CONNECT", "NEXT", "EMERG", "EVAC", "CLEAR"};

    public static final class Said {
        public final long tick;
        public final String station, kind, text;
        public final int speakers;
        Said(long tick, String station, String kind, String text, int speakers) { this.tick = tick; this.station = station; this.kind = kind; this.text = text; this.speakers = speakers; }
    }

    private static final Map<Integer, ArrayDeque<Said>> LOG = new HashMap<>();
    private static final Map<String, Long> LAST = new HashMap<>();

    public static List<Said> log(World w) { return new ArrayList<>(LOG.getOrDefault(w.provider.getDimension(), new ArrayDeque<>())); }

    public static String template(int kind) {
        switch (kind) {
            case 0: return IncidentConfig.tDelay;
            case 1: return IncidentConfig.tPlatform;
            case 2: return IncidentConfig.tCancel;
            case 3: return IncidentConfig.tConnection;
            case 4: return IncidentConfig.tNext;
            case 5: return IncidentConfig.tEmergency;
            case 6: return IncidentConfig.tEvacuation;
            default: return IncidentConfig.tAllClear;
        }
    }

    public static String fill(String t, Map<String, String> v) {
        String s = t;
        for (Map.Entry<String, String> e : v.entrySet()) s = s.replace("{" + e.getKey() + "}", e.getValue());
        return s.replaceAll("\\{[a-z]+}", "-");
    }

    /** speak at a station: its PA speakers (with 12 V) + text to the people there; returns how many speakers spoke */
    public static int say(World w, long station, String stationName, String kind, String text) {
        int n = 0;
        BlockPos at = station == Long.MIN_VALUE ? null : BlockPos.fromLong(station);
        for (TileEntity te : new ArrayList<>(w.loadedTileEntityList)) {
            if (!(te instanceof TilePaSpeaker sp) || te.isInvalid()) continue;
            boolean mine = sp.station() == station || !stationName.isEmpty() && stationName.equals(sp.stationName());
            if (mine && sp.page(text)) n++;
        }
        if (at != null)
            for (EntityPlayer p : w.playerEntities)
                if (p.getDistanceSq(at) < 64 * 64) p.sendStatusMessage(new TextComponentString("§e[" + (stationName.isEmpty() ? "PA" : stationName) + "] §f" + text), false);
        ArrayDeque<Said> l = LOG.computeIfAbsent(w.provider.getDimension(), k -> new ArrayDeque<>());
        l.addFirst(new Said(w.getTotalWorldTime(), stationName, kind, text, n));
        while (l.size() > 80) l.removeLast();
        Recorder.event(w, "PA " + stationName + " (" + kind + "): " + text);
        return n;
    }

    /** an announcement near a spot (emergencies / evacuations): every station within reach */
    public static int sayNear(World w, double x, double z, double reach, String kind, String text) {
        int n = 0;
        for (Map.Entry<Long, String> e : StationData.get(w).names().entrySet()) {
            BlockPos p = BlockPos.fromLong(e.getKey());
            if (Math.hypot(p.getX() - x, p.getZ() - z) <= reach) { say(w, e.getKey(), e.getValue(), kind, text); n++; }
        }
        if (n == 0) {                                                            // no named station near: the speakers in reach say it
            for (TileEntity te : new ArrayList<>(w.loadedTileEntityList))
                if (te instanceof TilePaSpeaker sp && Math.hypot(te.getPos().getX() - x, te.getPos().getZ() - z) <= reach && sp.page(text)) n++;
            ArrayDeque<Said> l = LOG.computeIfAbsent(w.provider.getDimension(), k -> new ArrayDeque<>());
            l.addFirst(new Said(w.getTotalWorldTime(), (int) x + "," + (int) z, kind, text, n));
        }
        return n;
    }

    // ---- live data -----------------------------------------------------------------------------------------------
    public static final class Due {
        public UUID loco;
        public String train = "", dest = "", line = "", reason = "";
        public int etaMin, lateMin;
        public boolean held, dwelling;
    }

    /** every driverless train heading for (or standing at) this station, soonest first */
    public static List<Due> due(World w, long station, String name) {
        List<Due> o = new ArrayList<>();
        RailwayData data = RailwayData.get(w);
        StationData sd = StationData.get(w);
        int dim = w.provider.getDimension();
        cam72cam.mod.world.World umc = cam72cam.mod.world.World.get(w);
        for (RailwayData.AutoTrain a : data.trains().values()) {
            Autopilot.Run r = Autopilot.run(a.loco);
            if (r == null) continue;
            boolean here = r.dwelling && r.dwellStation == station;
            if (!here && (name.isEmpty() || !name.equals(r.nextStopName))) continue;
            Due d = new Due();
            d.loco = a.loco;
            Locomotive l = Autopilot.find(w, a.loco);
            d.train = l == null ? (a.label.isEmpty() ? "train" : a.label) : Autopilot.label(a, l);
            d.line = a.line;
            RailwayData.Line line = data.lines().get(a.line);
            if (line != null && !line.stations.isEmpty())
                d.dest = Autopilot.nameOf(sd, a.direction < 0 ? line.stations.get(0) : line.stations.get(line.stations.size() - 1));
            else d.dest = a.home == 0 ? "-" : Autopilot.nameOf(sd, a.home);
            d.dwelling = here;
            double v = Math.max(Math.abs(r.speedKmh), 20) / 3.6;
            d.etaMin = here ? 0 : (int) Math.ceil(Math.max(0, r.metresToStop) / v / 60.0);
            d.held = Disruptions.isHeld(dim, a.loco);
            d.lateMin = Disruptions.delaySeconds(a.loco) / 60;
            if (d.held) for (Disruptions.Incident in : Disruptions.incidents(dim)) if (in.held.contains(a.loco)) { d.reason = reason(in); break; }
            if (d.reason.isEmpty()) d.reason = "operational reasons";
            o.add(d);
        }
        o.sort(Comparator.comparingInt(x -> x.etaMin + x.lateMin));
        return o;
    }

    static String reason(Disruptions.Incident in) {
        switch (in.kind) {
            case WRECK: return "a derailment";
            case FAILED: return "a failed train";
            default: return in.what.contains("fire") ? "a fire near the line" : in.what.contains("evac") ? "an emergency evacuation" : "an emergency on the line";
        }
    }

    public static Map<String, String> vars(World w, String stationName, Due d, int plat) {
        Map<String, String> v = new HashMap<>();
        v.put("station", stationName.isEmpty() ? "this station" : stationName);
        v.put("time", Recorder.clock(w, w.getTotalWorldTime()));
        v.put("plat", String.valueOf(plat));
        if (d != null) {
            v.put("train", d.train); v.put("dest", d.dest); v.put("line", d.line.isEmpty() ? "local" : d.line);
            v.put("min", String.valueOf(Math.max(1, d.held ? Math.max(d.lateMin, IncidentConfig.announceDelayMinutes) : d.etaMin + d.lateMin)));
            v.put("reason", d.reason);
        }
        return v;
    }

    /** an Announcement Control Unit in AUTO: what this station should hear now (rate-limited per train and kind) */
    static void auto(World w, long station, String name) {
        if (name.isEmpty()) return;
        long now = w.getTotalWorldTime();
        RailwayData data = RailwayData.get(w);
        List<Due> due = due(w, station, name);
        for (Due d : due) {
            String k = station + "|" + d.loco + "|";
            if (d.held && d.lateMin >= IncidentConfig.cancelMinutes) {
                if (once(k + "C", now, Long.MAX_VALUE)) { say(w, station, name, "CANCEL", fill(IncidentConfig.tCancel, vars(w, name, d, 0))); Analytics.count(w, "cancel", 1); }
                continue;
            }
            if ((d.held || d.lateMin >= IncidentConfig.announceDelayMinutes) && d.lateMin >= IncidentConfig.announceDelayMinutes && once(k + "D", now, 3600))
                say(w, station, name, "DELAY", fill(IncidentConfig.tDelay, vars(w, name, d, 0)));
            if (d.dwelling) {                                                    // connections for the train standing here
                for (RailwayData.Line other : data.lines().values()) {
                    if (other.name.equals(d.line) || !other.stations.contains(station)) continue;
                    Due best = null;
                    for (Due x : due) if (x.line.equals(other.name) && !x.dwelling && (best == null || x.etaMin < best.etaMin)) best = x;
                    if (best != null && once(k + "X" + other.name, now, 1200)) say(w, station, name, "CONNECT", fill(IncidentConfig.tConnection, vars(w, name, best, 0)));
                }
            }
        }
        Due next = null;
        for (Due d : due) if (!d.dwelling && !d.held && d.etaMin <= 10) { next = d; break; }
        if (next != null && once(station + "|NEXT", now, 3600)) say(w, station, name, "NEXT", fill(IncidentConfig.tNext, vars(w, name, next, 0)));
        if (LAST.size() > 2000) LAST.clear();
    }

    private static boolean once(String key, long now, long gap) {
        Long t = LAST.get(key);
        if (t != null && (gap == Long.MAX_VALUE || now - t < gap)) return false;
        LAST.put(key, now);
        return true;
    }
}
