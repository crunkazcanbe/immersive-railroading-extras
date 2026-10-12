package com.dogpound.railmap.weather;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.common.config.Config;

/** Snowploughs and weather trains (her IR Extras list #4), config/irextras_wxtrains.cfg. */
@Config(modid = RailMap.MODID, name = "irextras_wxtrains", category = "wxtrains")
public final class WxTrainConfig {
    private WxTrainConfig() {}

    @Config.Comment("Ploughs, railhead treatment (sandite) and de-icing trains work (stock tagged PLOUGH / SNOWPLOW / SANDITE / RHTT / DEICER, or a loco with the plough attachment)")
    public static boolean weatherTrains = true;
    @Config.Comment("A plough also shovels physical snow-layer blocks off the track and its cess (2 blocks each side)")
    public static boolean ploughClearsBlocks = true;
    @Config.Comment("Blocks around a Weather Train Depot desk it plans runs for")
    @Config.RangeInt(min = 64, max = 4096)
    public static int depotRange = 512;
    @Config.Comment("Sand a sandite (railhead treatment) run takes from the depot desk's SANDITE STOCK drawer")
    @Config.RangeInt(min = 0, max = 64)
    public static int sanditeSand = 16;
    @Config.Comment("Game ticks a sandite coat lasts on the rail head (24000 = a day)")
    @Config.RangeInt(min = 1200, max = 240000)
    public static int sanditeTicks = 24000;
    @Config.Comment("Game ticks de-icing fluid keeps ice off the contact wire")
    @Config.RangeInt(min = 1200, max = 240000)
    public static int antiIceTicks = 12000;
}
