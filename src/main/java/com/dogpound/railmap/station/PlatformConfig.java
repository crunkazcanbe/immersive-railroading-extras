package com.dogpound.railmap.station;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.common.config.Config;

/**
 * Platform operations (config/irextras_platform.cfg, ChatGPT batch 3 #27 #29 #32 #33). Nothing changes until one of the
 * blocks is placed; the harsh options are off.
 */
@Config(modid = RailMap.MODID, name = "irextras_platform", category = "platform")
public final class PlatformConfig {
    private PlatformConfig() {}

    @Config.Comment("Ticket gate throughput, passengers a minute per gate (queues form above it)")
    @Config.RangeInt(min = 5, max = 80)
    public static int gatePaxPerMin = 25;

    @Config.Comment("Ticket machine throughput, passengers a minute per machine; share of people who buy at a machine")
    @Config.RangeInt(min = 1, max = 20)
    public static int machinePaxPerMin = 4;
    @Config.RangeDouble(min = 0, max = 1)
    public static double machineShare = 0.3;

    @Config.Comment("Crowd monitors raise overcrowding alarms (Fruin level of service E = minor, F = major)")
    public static boolean crowdAlarms = true;

    @Config.Comment("Most extra dwell (s) a crowd monitor's DWELL key may give a train at a crowded platform")
    @Config.RangeInt(min = 0, max = 300)
    public static int crowdDwellMaxSec = 45;

    @Config.Comment("A train that stops more than this many metres past the platform end is an OVERRUN: the door controller locks its doors")
    @Config.RangeDouble(min = 0.5, max = 20)
    public static double overrunM = 3;

    @Config.Comment("Strict door safety: with SDO OFF a coach off the platform still stays shut (real trains). false = SDO OFF only warns. Harsh: off")
    public static boolean strictDoors = false;

    @Config.Comment("Security panels raise an INTRUDER alarm for players in a closed, armed station")
    public static boolean intruderAlarm = true;

    @Config.Comment("How fast the people waiting at a closed station go home (share per second)")
    @Config.RangeDouble(min = 0, max = 0.5)
    public static double closedLeaveRate = 0.03;
}
