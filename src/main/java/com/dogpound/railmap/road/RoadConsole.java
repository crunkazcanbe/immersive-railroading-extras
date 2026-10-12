package com.dogpound.railmap.road;

import com.dogpound.railmap.controls.IControlled;
import com.dogpound.railmap.signal.SignalRegistry;
import com.dogpound.railmap.signal.TileCrossing;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

import static com.dogpound.railmap.stn.StnConsole.*;

/** the console STN tab's Road traffic, Signals and Trams pages ("road.*" ops) */
public final class RoadConsole {
    private RoadConsole() {}

    public static void fill(World w, List<String[]> out) {
        RoadData d = RoadData.get(w);
        page(out, "Road traffic", "Level crossings: road queues, delays, closures, ROAD QUEUE alarms; the traffic itself");
        cols(out, "Crossing:0", "State:14", "Queue:28", "Max today:37", "Delayed:49", "Closed:60", "Longest:71", "Alarm:84");
        long now = w.getTotalWorldTime();
        for (TileCrossing x : SignalRegistry.crossings(w.provider.getDimension())) {
            if (x.isInvalid()) continue;
            RoadData.Crossing c = d.crossing(x.getPos().toLong());
            row(out, String.valueOf(x.getPos().toLong()), x.getPos().getX() + "," + x.getPos().getZ(), x.active() ? "~CLOSED" : "+open",
                    (c.queue >= RoadConfig.queueAlarmVehicles ? "!" : c.queue > 0 ? "~" : ".") + c.queue, String.valueOf(c.maxToday), c.delayedToday + " / " + c.delayedTotal,
                    x.active() && c.closedSince >= 0 ? (now - c.closedSince) / 20 + " s" : "-", c.longestClose / 20 + " s", c.alarm ? "!ROAD QUEUE" : ".");
        }
        btn(out, d.paused ? "RESUME TRAFFIC" : "PAUSE TRAFFIC", "road.pause", false);
        btn(out, "CLEAR TRAFFIC", "road.clear", false);
        btn(out, "SPAWN 5 HERE", "road.spawn", false);
        List<EntityRoadVehicle> vs = RoadTraffic.vehicles(w);
        int[] k = new int[4];
        int q = 0, stopped = 0;
        for (EntityRoadVehicle v : vs) { k[v.kind().ordinal()]++; if (v.queued()) q++; if (v.speed < 0.02) stopped++; }
        txt(out, !RoadConfig.traffic ? "!Road traffic switched off (config)" : d.paused ? "~Traffic paused from the console" :
                String.format(Locale.ROOT, "%d vehicles (cars %d, vans %d, trucks %d, buses %d) · %d standing · %d queued at crossings · cap %d per player, %d total",
                        vs.size(), k[0], k[1], k[2], k[3], stopped, q, RoadConfig.perPlayer, RoadConfig.maxVehicles));
        txt(out, ".Spawned " + d.spawned + " · removed " + d.removed + " · road path searches " + RoadTraffic.searches() + " (max " + RoadConfig.pathsPerTick + " a tick) · alarm after "
                + RoadConfig.queueAlarmSeconds + " s with " + RoadConfig.queueAlarmVehicles + "+ waiting");

        page(out, "Signals", "Traffic signal controllers: mode, stage, demands, tram priority; work them from here");
        cols(out, "Junction:0", "Mode:12", "Stage:28", "T s:50", "Demand:56", "Tram:66", "Cycles:78", "Groups:88");
        for (TileTrafficController c : TileTrafficController.all(w)) row(out, String.valueOf(c.getPos().toLong()), c.row());
        btn(out, "ADV", "road.adv", true);
        btn(out, "ALL RED", "road.allred", true);
        btn(out, "PED CALL", "road.pedtest", true);
        btn(out, "TRAM CALL", "road.tramtest", true);
        btn(out, "TIMING +", "road.tup", true);
        btn(out, "TIMING -", "road.tdn", true);
        txt(out, ".ADV works in MANUAL (mode selector or POLICE key at the cabinet). TIMING +/- change the timing picked on the cabinet's TIMING selector.");

        page(out, "Trams", "Tram-mode trains (IR stock tagged TRAM) and tram stops: next trams, why a tram is held");
        cols(out, "Tram:0", "Line:20", "Next stop:32", "km/h:52", "Cap:59", "Driving:66");
        for (String[] t : TramMode.trams(w)) row(out, t[0], Arrays.copyOfRange(t, 1, t.length));
        btn(out, "UNTAG (not a tram)", "road.untag", true);
        btn(out, "CLEAR TAG OVERRIDE", "road.retag", true);
        txt(out, RoadConfig.tramMode ? String.format(Locale.ROOT, "Tram mode on · sight %d m · street %d km/h · elsewhere %d km/h · dwell %d s · dead OHLE stop %s · tag stock with an IR name tag containing TRAM or /irextras tram tag",
                RoadConfig.tramSightM, RoadConfig.tramStreetKmh, RoadConfig.tramMaxKmh, RoadConfig.tramDwell, RoadConfig.tramDeadSection ? "on" : "off") : "!Tram mode switched off (config)");

        page(out, "Tram stops", "Tram stops: request / all stop, the next tram on the display, arrivals, validations");
        cols(out, "Stop:0", "Mode:26", "Next:38", "Due:70", "Trams:78", "Validated:88");
        for (TileTramStop s : TileTramStop.all(w)) row(out, "", s.row());
    }

    public static String op(EntityPlayer p, String op, String arg) {
        World w = p.world;
        RoadData d = RoadData.get(w);
        switch (op) {
            case "pause": d.paused = !d.paused; d.markDirty(); return d.paused ? "§eRoad traffic paused" : "§aRoad traffic resumed";
            case "clear": { int n = 0; for (EntityRoadVehicle v : RoadTraffic.vehicles(w)) { v.setDead(); n++; } return "§e" + n + " road vehicles cleared"; }
            case "spawn": return spawn(p, 5, null);
            case "untag": case "retag": {
                UUID u;
                try { u = UUID.fromString(arg); } catch (IllegalArgumentException e) { return "§7Pick a tram first"; }
                d.tramOn.remove(u);
                if (op.equals("untag")) d.tramOff.add(u); else d.tramOff.remove(u);
                d.markDirty();
                return op.equals("untag") ? "§eNo longer runs as a tram" : "§eTag override cleared - the name decides";
            }
            default: {
                long k;
                try { k = Long.parseLong(arg); } catch (NumberFormatException e) { return "§7Pick a junction first"; }
                BlockPos at = BlockPos.fromLong(k);
                if (!(w.isBlockLoaded(at) && w.getTileEntity(at) instanceof TileTrafficController c)) return "§7That controller isn't loaded";
                String r = IControlled.operate(c, op, null);
                return r == null || r.isEmpty() ? "§7done" : r;
            }
        }
    }

    static String spawn(EntityPlayer p, int n, EntityRoadVehicle.Kind kind) {
        World w = p.world;
        if (!RoadConfig.traffic) return "§cRoad traffic is switched off (config)";
        int made = 0;
        List<EntityRoadVehicle> vs = RoadTraffic.vehicles(w);
        for (int i = 0; i < n; i++) {
            EntityRoadVehicle v = RoadTraffic.spawnNear(w, p.getPosition(), Collections.emptyList(), vs, kind);
            if (v != null) { vs.add(v); made++; RoadData.get(w).spawned++; }
        }
        return made == 0 ? "§cNo road found 28-72 blocks around you (road-like blocks: path, gravel, concrete, stone bricks, anything named road/asphalt)" : "§a" + made + " vehicle(s) spawned on the roads round you";
    }
}
