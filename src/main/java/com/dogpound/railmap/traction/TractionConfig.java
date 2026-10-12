package com.dogpound.railmap.traction;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.common.config.Config;

/** Battery-electric and hydrogen trains (batch 5 #6) - config/irextras_traction.cfg */
@Config(modid = RailMap.MODID, name = "irextras_traction", category = "traction")
public final class TractionConfig {
    private TractionConfig() {}

    @Config.Comment("Battery-electric (BEMU) and hydrogen fuel-cell traction: stock tagged by name or rebuilt at the Loco Design Workshop (STORAGE = BEMU / HYDROGEN)")
    public static boolean enabled = true;

    @Config.Comment("Names (definition / tag, lower case) that make a loco battery-electric")
    public static String[] batteryNames = {"battery", "bemu", "akku", "accu", "class 230", "flirt akku"};

    @Config.Comment("Names (definition / tag, lower case) that make a loco a hydrogen fuel-cell train")
    public static String[] hydrogenNames = {"hydrogen", "hydrail", "fuel cell", "ilint", "hydroflex", "mireo plus h"};

    @Config.Comment("Traction battery size: kWh per kW of rated power (a 1 MW BEMU gets 600 kWh)")
    @Config.RangeDouble(min = 0.05, max = 5)
    public static double kwhPerKw = 0.6;

    @Config.Comment("Hydrogen tank: kg per kW of rated power (a 1 MW unit carries 180 kg at 350 bar)")
    @Config.RangeDouble(min = 0.01, max = 2)
    public static double h2KgPerKw = 0.18;

    @Config.Comment("Fuel cell rating as a share of the unit's rated power (the buffer battery covers the peaks)")
    @Config.RangeDouble(min = 0.1, max = 1.5)
    public static double fuelCellShare = 0.6;

    @Config.Comment("kW a battery train charges at from live contact wire (OHLE charging sections) while it runs under it")
    @Config.RangeInt(min = 0, max = 5000)
    public static int wireChargeKw = 400;

    @Config.Comment("Fuel-cell electrical output per kg of hydrogen (kWh) - about 50 % of its 33.3 kWh/kg")
    @Config.RangeDouble(min = 5, max = 33.3)
    public static double kwhPerKgH2 = 16.7;
}
