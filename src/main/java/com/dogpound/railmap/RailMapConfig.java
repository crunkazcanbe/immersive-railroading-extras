package com.dogpound.railmap;

import net.minecraftforge.common.config.Config;

/** Forge-managed config (config/railmap.cfg). All values are read on the server at scan time. */
@Config(modid = RailMap.MODID)
public final class RailMapConfig {
    @Config.Comment("Max IR track pieces one board will walk. Each piece costs a few tile lookups; " +
            "2000 covers a large network without hurting an OOM-prone client.")
    @Config.RangeInt(min = 50, max = 20000)
    public static int nodeBudget = 2000;

    @Config.Comment("Radius (blocks) around the board in which to look for track to start walking from.")
    @Config.RangeInt(min = 4, max = 256)
    public static int seedRadius = 48;

    @Config.Comment("Ticks between automatic rescans while a player is within 48 blocks of the board.")
    @Config.RangeInt(min = 20, max = 6000)
    public static int rescanIntervalTicks = 200;

    @Config.Comment("A signal further than this (blocks) from any walked track piece is ignored.")
    @Config.RangeInt(min = 1, max = 32)
    public static int signalTrackDistance = 6;

    @Config.Comment("How far a signal will follow the track looking for the end of its block, in blocks. " +
            "A block normally ends at an insulated joint or the next facing signal; this is the safety stop.")
    @Config.RangeInt(min = 16, max = 2048)
    public static int maxBlockLength = 400;

    @Config.Comment("Three-block signalling: two blocks clear shows flashing yellow (Advance Approach) " +
            "instead of green. Turn off for simple two-block territory.")
    public static boolean threeBlockSignalling = true;

    @Config.Comment("Draw RailMap's own signals on the board and wall screens.")
    public static boolean showOwnSignals = true;

    // ---- driverless trains, routes, protection ------------------------------------------

    @Config.Comment("How far (blocks of track) a route search may go looking for a station or signal.")
    @Config.RangeInt(min = 100, max = 20000)
    public static int routeSearchBlocks = 4000;

    @Config.Comment("Braking a driverless train plans for, m/s². Lower = gentler stops that start earlier.")
    @Config.RangeDouble(min = 0.1, max = 3.0)
    public static double autopilotBraking = 0.6;

    @Config.Comment("Default top speed for a new driverless train, km/h (you can change it per train on the board).")
    @Config.RangeInt(min = 5, max = 250)
    public static int autopilotDefaultKmh = 60;

    @Config.Comment("Train protection: brake any train that would run a Stop signal or overspeed a sign.")
    public static boolean trainProtection = true;

    @Config.Comment("Apply train protection to trains a player is driving, not just driverless ones.")
    public static boolean protectManualTrains = true;

    @Config.Comment("How far over a limit (km/h) a driven train may go before protection brakes it.")
    @Config.RangeInt(min = 0, max = 50)
    public static int protectionMarginKmh = 8;

    // ---- tickets -------------------------------------------------------------------------

    @Config.Comment("Item a ticket costs, as modid:name (empty = tickets are free).")
    public static String fareItem = "minecraft:iron_nugget";

    @Config.Comment("Fare: one fare item per this many blocks between stations (minimum one).")
    @Config.RangeInt(min = 10, max = 100000)
    public static int fareBlocksPerItem = 200;

    @Config.Comment("A ticket machine belongs to the nearest named station within this many blocks.")
    @Config.RangeInt(min = 4, max = 256)
    public static int stationReach = 48;

    // ---- defect detectors ----------------------------------------------------------------

    @Config.Comment("How far (blocks) a defect detector's radio announcement carries.")
    @Config.RangeInt(min = 16, max = 1024)
    public static int detectorRadioRange = 160;

    private RailMapConfig() {}
}
