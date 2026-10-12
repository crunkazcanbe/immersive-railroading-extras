package com.dogpound.railmap.drone;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.common.config.Config;

/**
 * Inspection drones (her IR Extras list #2), config/irextras_drones.cfg. Nothing happens in an old world until a Drone
 * Dock is placed. The harsh part (a drone that runs flat or hits something falls and breaks) is off by default: it
 * lands where it is instead and waits to be picked up.
 */
@Config(modid = RailMap.MODID, name = "irextras_drones", category = "drones")
public final class DroneConfig {
    private DroneConfig() {}

    @Config.Comment("Inspection drones fly at all (off = docks stay grounded)")
    public static boolean drones = true;
    @Config.Comment("Hover endurance of a full, new battery, in real minutes (the 500 Wh pack is scaled to game time)")
    @Config.RangeInt(min = 2, max = 60)
    public static int enduranceMin = 12;
    @Config.Comment("Minutes for the dock charger to take a flat battery to full (NORMAL; FAST is twice as quick and ages the cells)")
    @Config.RangeInt(min = 1, max = 60)
    public static int chargeMin = 6;
    @Config.Comment("Cruise speed, m/s")
    @Config.RangeDouble(min = 2, max = 20)
    public static double cruiseMs = 8;
    @Config.Comment("Highest wind it may launch / keep flying in, m/s (above it: return to home)")
    @Config.RangeDouble(min = 4, max = 30)
    public static double maxWindMs = 12;
    @Config.Comment("Heavy rain, snow and thunder ground the drones (they return home and won't launch)")
    public static boolean rainGrounds = true;
    @Config.Comment("No-fly zone: within this many blocks of a named station, drones climb clear of players")
    @Config.RangeInt(min = 0, max = 256)
    public static int noFlyRadius = 48;
    @Config.Comment("Height (blocks) a drone keeps above players inside a station no-fly zone")
    @Config.RangeInt(min = 4, max = 60)
    public static int noFlyClearance = 15;
    @Config.Comment("Handheld controller radio range (blocks); beyond it the link is lost and the drone returns home")
    @Config.RangeInt(min = 32, max = 4096)
    public static int controllerRange = 600;
    @Config.Comment("HARSH: a drone that runs flat or flies into something falls and breaks (off = it lands safely where it is)")
    public static boolean crashes = false;
}
