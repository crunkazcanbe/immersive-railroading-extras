package com.dogpound.railmap.ems;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.common.config.Config;

/**
 * Emergency services (fire, ambulance, police), config/irextras_ems.cfg. On by default but gentle: nothing turns out
 * until a Fire / Ambulance / Police Station block is placed; the police cordon only goes on empty air over solid ground
 * (never on track, never replacing a block) and is always taken away again; nothing is ever broken.
 */
@Config(modid = RailMap.MODID, name = "irextras_ems", category = "ems")
public final class EmsConfig {
    private EmsConfig() {}

    @Config.Comment("Emergency services respond to railway incidents at all")
    public static boolean enabled = true;

    @Config.Comment("Stations with their AUTO key on turn out by themselves when an incident needs them (false = only the DISPATCH button / console)")
    public static boolean autoDispatch = true;

    @Config.Comment("Farthest (blocks) a station will send a vehicle")
    @Config.RangeInt(min = 32, max = 4096)
    public static int maxRange = 768;

    @Config.Comment("Vehicle speed on the road (blocks per tick; 0.5 = 36 km/h)")
    @Config.RangeDouble(min = 0.1, max = 1.5)
    public static double speed = 0.5;

    @Config.Comment("Fire service turns out to derailments of at least this level (1-3); fires and dangerous goods always")
    @Config.RangeInt(min = 1, max = 4)
    public static int fireWreckLevel = 2;

    @Config.Comment("Police turn out to derailments of at least this level (1-3); evacuations and dangerous goods always")
    @Config.RangeInt(min = 1, max = 4)
    public static int policeWreckLevel = 2;

    @Config.Comment("Firefighters put out fire blocks within this many blocks of the incident")
    @Config.RangeInt(min = 4, max = 48)
    public static int fireRadius = 14;

    @Config.Comment("Police put up a temporary cordon (barrier blocks on empty ground) around the incident")
    public static boolean cordon = true;

    @Config.Comment("Most cordon barriers one police car puts up")
    @Config.RangeInt(min = 0, max = 64)
    public static int cordonMax = 20;

    @Config.Comment("Police close level crossings within this many blocks of the incident (barriers down) while on scene; 0 = never")
    @Config.RangeInt(min = 0, max = 256)
    public static int crossingRadius = 96;

    @Config.Comment("Longest a crew stays on scene (seconds) before going back to base")
    @Config.RangeInt(min = 30, max = 3600)
    public static int maxOnSceneSeconds = 600;

    @Config.Comment("Ambulances take an injured player (under this % health, or who boards it) to their bed or the nearest station")
    @Config.RangeInt(min = 0, max = 100)
    public static int carryBelowPct = 40;

    @Config.Comment("Sirens sound while responding (lights always flash)")
    public static boolean sirens = true;

    @Config.Comment("Siren volume")
    @Config.RangeDouble(min = 0, max = 4)
    public static double sirenVolume = 2.0;
}
