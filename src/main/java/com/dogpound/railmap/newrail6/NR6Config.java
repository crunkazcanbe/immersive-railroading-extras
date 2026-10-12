package com.dogpound.railmap.newrail6;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.common.config.Config;

/**
 * Batch 6 #16-20 (config/irextras_newrail6.cfg): rack railways, train ferries, motorail, garden railways, avalanche
 * protection. Nothing changes in an old world until these blocks are placed.
 */
@Config(modid = RailMap.MODID, name = "irextras_newrail6", category = "newrail6")
public final class NR6Config {
    private NR6Config() {}

    @Config.Comment("Rack railway: a cog locomotive on rack rail gets full grip (no wheel slip, brakes hold) - the rack takes the force")
    public static boolean rackGrip = true;
    @Config.Comment("Pinions wear on rack rail (faster without the lubricator, a lot on a hard entry); a worn-out pinion no longer holds")
    public static boolean pinionWear = true;
    @Config.Comment("Most train cars one ferry carries")
    @Config.RangeInt(min = 1, max = 40)
    public static int ferryCapacity = 12;
    @Config.Comment("Ferry cruising speed, m/s (6 = about 12 knots)")
    @Config.RangeDouble(min = 1, max = 20)
    public static double ferrySpeed = 6;
    @Config.Comment("Ferries need a water route: the straight line between the two berths must be open water. Off = they sail over anything")
    public static boolean ferryNeedsWater = true;
    @Config.Comment("Avalanches release by themselves when the hazard is high (snow deposited over unprotected track near a detector). Off = only the detector's controlled release (BLAST) brings snow down")
    public static boolean naturalAvalanches = true;
    @Config.Comment("Garden railway trains: top speed, m/s")
    @Config.RangeDouble(min = 0.5, max = 6)
    public static double gardenTopSpeed = 3;
    @Config.Comment("Garden trains, ferries and their riders are only spawned / kept while a player is within this many blocks")
    @Config.RangeInt(min = 32, max = 512)
    public static int spawnRange = 160;
}
