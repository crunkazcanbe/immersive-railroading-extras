package com.dogpound.railmap.steam;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.common.config.Config;

/**
 * The steam servicing depot (batch 5 #2) - config/irextras_steam.cfg. Safe for old worlds: every steam loco starts with a
 * clean boiler and an empty ash pan, and scale builds up over many hours of running.
 */
@Config(modid = RailMap.MODID, name = "irextras_steam", category = "steam")
public final class SteamConfig {
    private SteamConfig() {}

    @Config.Comment("Boiler scale builds up from the hardness of the water the loco takes (hard water = faster). Scale costs fuel (8 % per mm)")
    public static boolean boilerScale = true;

    @Config.Comment("How fast scale builds up (1 = about 0.5 mm per game-hour of steaming at half regulator on 250 ppm water)")
    @Config.RangeDouble(min = 0, max = 20)
    public static double scaleRate = 1.0;

    @Config.Comment("Scale (mm) at which the boiler is DUE A WASHOUT (cab fault + alarm)")
    @Config.RangeDouble(min = 0.2, max = 10)
    public static double washoutDueMm = 1.5;

    @Config.Comment("Heavy scale (mm) overheats the tube ends and the tubes start LEAKING (the boiler loses water). 0 = never")
    @Config.RangeDouble(min = 0, max = 20)
    public static double tubeLeakMm = 3.0;

    @Config.Comment("Hardness (ppm CaCO3) assumed for water that did not come from a Water Column (IR's own filling, pipes)")
    @Config.RangeInt(min = 0, max = 600)
    public static int defaultPpm = 180;

    @Config.Comment("Ash builds up in the ash pan while coal burns; a full ash pan chokes the fire (less steam) until it is dropped at an Ash Pit")
    public static boolean ashPan = true;

    @Config.Comment("Ash pan capacity in kg (one coal item = about 5 kg of ash)")
    @Config.RangeInt(min = 50, max = 5000)
    public static int ashPanKg = 400;

    @Config.Comment("Coaling tower bunker capacity in coal items")
    @Config.RangeInt(min = 64, max = 100000)
    public static int bunkerItems = 4096;
}
