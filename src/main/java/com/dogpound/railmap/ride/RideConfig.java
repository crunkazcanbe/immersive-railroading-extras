package com.dogpound.railmap.ride;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.common.config.Config;

/**
 * Funiculars, aerial tramways and gondolas (batch 5 #5), config/irextras_ride.cfg. Nothing changes in an old world until
 * ropeway blocks are placed and a line is strung with the Haul Rope Spool.
 */
@Config(modid = RailMap.MODID, name = "irextras_ride", category = "ride")
public final class RideConfig {
    private RideConfig() {}

    @Config.Comment("Ropeway / funicular drives need their real supply (480 V, 4.16 kV or 13.8 kV 3-phase grid cable touching the machine room). Off = 12 V is enough")
    public static boolean needSupply = true;
    @Config.Comment("Riders can't get out of a cabin / car between stations (doors locked). Off = sneak gets you out anywhere")
    public static boolean doorsLocked = true;
    @Config.Comment("Wind stops the line (restriction at 75 % of the desk's wind limit, stop above it, rope position detectors trip on rope swing). Off = wind is shown only")
    public static boolean windStops = true;
    @Config.Comment("Lateral rope swing (mm) at which a pylon's rope position detector trips the line")
    @Config.RangeInt(min = 100, max = 2000)
    public static int rpdTripMm = 400;
    @Config.Comment("Seconds the cars / cabins stand at a station with doors open (automatic mode)")
    @Config.RangeInt(min = 5, max = 120)
    public static int dwellSeconds = 15;
    @Config.Comment("Most cars / cabins a single line may have (gondolas)")
    @Config.RangeInt(min = 2, max = 64)
    public static int maxCabins = 24;
    @Config.Comment("Longest line (metres along the rope)")
    @Config.RangeInt(min = 32, max = 4000)
    public static int maxLength = 1500;
    @Config.Comment("Cabins / cars are only spawned while a player is within this many blocks of them")
    @Config.RangeInt(min = 32, max = 512)
    public static int spawnRange = 192;
}
