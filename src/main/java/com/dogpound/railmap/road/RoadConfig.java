package com.dogpound.railmap.road;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.common.config.Config;

/**
 * Road traffic + trams (batch 5 #3, #4), config/irextras_road.cfg. Gentle by default on an old world: traffic only
 * spawns on road-like blocks near players and never saves to disk (it can't pile up), nothing is ever broken, trams only
 * change behaviour for stock tagged TRAM (IR name tag or /irextras tram tag) and only on the autopilot.
 */
@Config(modid = RailMap.MODID, name = "irextras_road", category = "road")
public final class RoadConfig {
    private RoadConfig() {}

    @Config.Comment("Cars, vans, trucks and buses drive the roads near players")
    public static boolean traffic = true;

    @Config.Comment("Most road vehicles around one player")
    @Config.RangeInt(min = 0, max = 64)
    public static int perPlayer = 10;

    @Config.Comment("Most road vehicles in one world (hard cap)")
    @Config.RangeInt(min = 0, max = 256)
    public static int maxVehicles = 40;

    @Config.Comment("Vehicles spawn at least this far from every player (blocks)")
    @Config.RangeInt(min = 8, max = 128)
    public static int spawnMin = 28;

    @Config.Comment("... and at most this far")
    @Config.RangeInt(min = 16, max = 192)
    public static int spawnMax = 72;

    @Config.Comment("Vehicles farther than this from every player are removed (blocks)")
    @Config.RangeInt(min = 32, max = 256)
    public static int despawn = 112;

    @Config.Comment("Road path searches per server tick, all vehicles together (each is an A* of up to 9000 nodes)")
    @Config.RangeInt(min = 1, max = 20)
    public static int pathsPerTick = 2;

    @Config.Comment("Cruising speed on the road, blocks per tick (0.55 = 40 km/h); trucks and buses go 80 % of it")
    @Config.RangeDouble(min = 0.1, max = 1.2)
    public static double speed = 0.55;

    @Config.Comment("Drive on the right (false = keep left, UK / Japan)")
    public static boolean keepRight = true;

    @Config.Comment("Vehicle mix in percent: car, van, truck, bus")
    public static int[] mix = {60, 20, 10, 10};

    @Config.Comment("UK style red + amber before green")
    public static boolean redAmber = true;

    @Config.Comment("A level crossing closed this long (seconds) with road vehicles queueing raises a ROAD QUEUE alarm; 0 = never")
    @Config.RangeInt(min = 0, max = 3600)
    public static int queueAlarmSeconds = 180;

    @Config.Comment("... and only with at least this many vehicles queued")
    @Config.RangeInt(min = 1, max = 64)
    public static int queueAlarmVehicles = 3;

    @Config.Comment("Tram-mode autopilot for stock tagged TRAM: line-of-sight speed, tram signals, stopping at every tram stop, dead OHLE ahead")
    public static boolean tramMode = true;

    @Config.Comment("Stock whose IR name tag, definition name or id contains one of these runs as a tram (lower case)")
    public static String[] tramNames = {"tram", "streetcar", "strassenbahn", "trolley", "light rail", "lrv", "flexity", "citadis", "combino"};

    @Config.Comment("How far a tram driver can see ahead (m): it can always stop within this, and stops for road vehicles, people and animals on the track")
    @Config.RangeInt(min = 20, max = 300)
    public static int tramSightM = 90;

    @Config.Comment("Speed limit for trams on street track (tram road, or road blocks beside the rails), km/h")
    @Config.RangeInt(min = 10, max = 120)
    public static int tramStreetKmh = 50;

    @Config.Comment("Speed limit for trams anywhere else, km/h")
    @Config.RangeInt(min = 10, max = 160)
    public static int tramMaxKmh = 70;

    @Config.Comment("Seconds a tram stands at a tram stop that is not on its line (an ALL STOP stop, or a REQUEST stop someone is waiting at)")
    @Config.RangeInt(min = 3, max = 120)
    public static int tramDwell = 12;

    @Config.Comment("Trams stop short of a dead / earthed / faulted overhead line section ahead")
    public static boolean tramDeadSection = true;
}
