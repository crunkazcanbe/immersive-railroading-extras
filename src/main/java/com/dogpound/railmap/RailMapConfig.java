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
    public static double autopilotBraking = 0.45;

    @Config.Comment("Seconds a driverless train allows for air-brake pressure to build before it starts slowing")
    @Config.RangeDouble(min = 0, max = 10)
    public static double autopilotBrakeLagSeconds = 2.5;

    @Config.Comment("Orders: the longest a 'Full load' stop waits for every car to fill, in minutes")
    @Config.RangeInt(min = 1, max = 120)
    public static int fullLoadMaxMinutes = 10;

    @Config.Comment("Profit: money per item (or bucket) delivered, before the distance bonus")
    @Config.RangeDouble(min = 0, max = 100)
    public static double profitPerItem = 1.0;

    @Config.Comment("Profit: every this many blocks between loading and unloading adds one more times the base pay")
    @Config.RangeInt(min = 1, max = 10000)
    public static int profitBlocksPerCoin = 100;

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

    // ---- maintenance and wear ------------------------------------------------------------

    @Config.Comment("Rolling stock wears out with use (wheels, brakes, engine, bearings, electrics) and needs a Maintenance Depot.")
    public static boolean wearEnabled = true;

    @Config.Comment("How fast things wear. 1.0 = wheels last about 200 km; 2.0 = twice as fast.")
    @Config.RangeDouble(min = 0.05, max = 20)
    public static double wearRate = 1.0;

    @Config.Comment("Worn-out parts limit the train (worn engine = half throttle, worn brakes/wheels/bearings = speed cap). Off = warnings only.")
    public static boolean wearEffects = true;

    @Config.Comment("Speed cap (km/h) for a train whose brakes, wheels or bearings are worn out.")
    @Config.RangeInt(min = 5, max = 200)
    public static int wornSpeedCapKmh = 40;

    @Config.Comment("Percent of wear per second a Maintenance Depot repairs on each part of a stopped train beside it.")
    @Config.RangeDouble(min = 0.1, max = 100)
    public static double depotRepairPerSecond = 4.0;

    @Config.Comment("Blocks around a Maintenance Depot in which stopped rolling stock gets serviced.")
    @Config.RangeInt(min = 2, max = 32)
    public static int depotReach = 8;

    // ---- electrification and batteries ----------------------------------------------------

    @Config.Comment("Electric locomotives under live contact wire (or beside a live third rail) run without fuel; off the wire they run on their battery.")
    public static boolean electrification = true;

    @Config.Comment("A locomotive counts as electric when its name contains any of these (lower-case). Override per loco with /irextras electric on|off.")
    public static String[] electricNames = {"electric", "emu", "metro", "subway", "tram", "gg1", "ae ", "acela", "tgv", "ice ", "shinkansen", "bullet", "pantograph", "battery", "e44", "e60", "class 9", "eurostar", "sprinter", "monorail", "maglev", "pride rail"};

    @Config.Comment("Substations must hold Forge Energy (FE) to power the wire. Off = free power (the breaker still works). Pride default: on, trains run on RF.")
    public static boolean powerNeedsEnergy = true;

    @Config.Comment("FE a running electric loco draws from its substation each second, at full throttle.")
    @Config.RangeInt(min = 0, max = 100000)
    public static int fePerSecond = 400;

    @Config.Comment("How far (blocks) a substation feeds its contact wire and third rail.")
    @Config.RangeInt(min = 16, max = 4096)
    public static int substationRange = 512;

    @Config.Comment("Thunderstorms can trip substations exposed to the sky (right-click to reset).")
    public static boolean stormOutages = true;

    @Config.Comment("Battery size: millibuckets of fuel the battery can stand in for when off the wire (bigger = longer range).")
    @Config.RangeInt(min = 0, max = 1000000)
    public static int batteryFuelMb = 24000;

    @Config.Comment("Battery % per second a Charging Station (or live wire) puts back.")
    @Config.RangeDouble(min = 0.1, max = 100)
    public static double chargePerSecond = 2.0;

    private RailMapConfig() {}
}
