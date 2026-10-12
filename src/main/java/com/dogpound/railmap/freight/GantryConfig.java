package com.dogpound.railmap.freight;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.common.config.Config;

/** Yard gantry cranes (batch 5 #1), config/irextras_gantry.cfg */
@Config(modid = RailMap.MODID, name = "irextras_gantry", category = "gantry")
public final class GantryConfig {
    private GantryConfig() {}

    @Config.Comment("Over this wind speed (m/s, gusts included) the crane stops and its storm pins go in; over 80 % it runs at half speed")
    @Config.RangeDouble(min = 5, max = 80)
    public static double windLimitMs = 25;

    @Config.Comment("Stop the crane in high wind at all (off = the wind is only shown)")
    public static boolean windStop = true;

    @Config.Comment("Safe working load under the spreader in tonnes, the spreader's own 10.5 t included (the load cell refuses more unless the OVERRIDE key is turned)")
    @Config.RangeDouble(min = 15, max = 100)
    public static double swlT = 50;

    @Config.Comment("Put each gantry's spreader camera on the CCTV system")
    public static boolean camera = true;
}
