package com.dogpound.railmap.build6a;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.common.config.Config;

/**
 * Batch 6a construction machines - config/irextras_build.cfg. All new machines: nothing here touches an existing world
 * until a player places one.
 */
@Config(modid = RailMap.MODID, name = "irextras_build", category = "build")
public final class B6Config {
    private B6Config() {}

    @Config.Comment("Machines that need grid power (TBM 4.16-13.8 kV, Locomotive Works 208-480 V) must be fed from an IR Extras grid cable. false = they run without it")
    public static boolean supplyRequired = true;

    @Config.Comment("Ticks the track layer takes per metre of panel (threading, sleeper spacing, clipping)")
    @Config.RangeInt(min = 1, max = 100)
    public static int layTicksPerMetre = 6;

    @Config.Comment("The track layer also needs Rail Clips + Baseplates (1 per 4 sleepers); false = clips optional (used when loaded)")
    public static boolean layerNeedsClips = false;

    @Config.Comment("Diesel (mB) the track layer / tamper burns per second with the engine running at idle; working burns 4x")
    @Config.RangeInt(min = 0, max = 200)
    public static int dieselIdleMb = 2;

    @Config.Comment("TBM boring speed multiplier (1 = a 7 m ring in about 20-40 s of hard rock)")
    @Config.RangeDouble(min = 0.1, max = 20)
    public static double tbmSpeed = 1.0;

    @Config.Comment("Length (blocks) of the TBM's trailing supply cable from its launch shaft; past it the TBM stops")
    @Config.RangeInt(min = 16, max = 4096)
    public static int tbmCableM = 512;

    @Config.Comment("The TBM never bores through these (tile entities and unbreakable blocks are always refused)")
    public static String[] tbmNeverBore = {"minecraft:bedrock", "minecraft:end_portal_frame", "minecraft:barrier"};

    @Config.Comment("Tamper: how much of the geometry error one pass removes at the longest squeeze (0.85 = 85 %)")
    @Config.RangeDouble(min = 0.1, max = 1.0)
    public static double tampEffect = 0.8;

    @Config.Comment("Locomotive Works build time multiplier (1 = about 30 s + 2 s per tonne)")
    @Config.RangeDouble(min = 0.05, max = 20)
    public static double worksTime = 1.0;

    @Config.Comment("Hi-rail truck top speed on rail / on road, km/h")
    @Config.RangeInt(min = 5, max = 120)
    public static int hiRailRailKmh = 40;
    @Config.RangeInt(min = 5, max = 160)
    public static int hiRailRoadKmh = 80;
}
