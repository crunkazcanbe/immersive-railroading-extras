package com.dogpound.railmap.freight;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.common.config.Config;

/**
 * Cargo care (ChatGPT batch 3 #21-24: reefers + cold chain, container tracking, contamination + washout, axle loads),
 * config/irextras_cargo.cfg. Safe by default: nothing here stops a train or refuses a load unless you turn it on.
 */
@Config(modid = RailMap.MODID, name = "irextras_cargo", category = "cargo")
public final class CargoConfig {
    private CargoConfig() {}

    @Config.Comment("Refrigerated wagons (name contains reefer / refrigerat / frigo / chiller) and reefer containers keep a temperature and a cargo quality")
    public static boolean reefers = true;

    @Config.Comment("Reefer gensets: litres of fuel a full tank holds, and litres burnt per minute running")
    @Config.RangeDouble(min = 10, max = 5000)
    public static double gensetTankL = 400;
    @Config.RangeDouble(min = 0, max = 50)
    public static double gensetLPerMin = 0.6;

    @Config.Comment("How fast an unpowered reefer warms toward the outside air (°C per minute) and a running one pulls down (°C per minute)")
    @Config.RangeDouble(min = 0, max = 10)
    public static double driftPerMin = 0.35, pullDownPerMin = 1.2;

    @Config.Comment("Perishable quality lost per minute for each °C above the setpoint + 2 °C tolerance (%)")
    @Config.RangeDouble(min = 0, max = 10)
    public static double decayPerDegMin = 0.08;

    @Config.Comment("Quality under this (%) is a cold-chain finding at delivery (opens a cargo investigation, like a dirty box)")
    @Config.RangeInt(min = 0, max = 100)
    public static int coldChainFindingPct = 80;

    @Config.Comment("Food loaded into a wagon / box whose last load was hazardous needs a STEAM wash certificate, or it is a contamination finding")
    public static boolean contamination = true;

    @Config.Comment("Wash certificate validity (game days)")
    @Config.RangeInt(min = 1, max = 30)
    public static int certDays = 2;

    @Config.Comment("Default route axle-load limit in tonnes (the weighbridge's LIMIT selector overrides it at that site)")
    @Config.RangeDouble(min = 5, max = 45)
    public static double axleLimitT = 22.5;

    @Config.Comment("Weighbridge imbalance limits: side-to-side (%) and bogie-to-bogie (%)")
    @Config.RangeInt(min = 1, max = 90)
    public static int lateralPct = 15, longitudinalPct = 33;

    @Config.Comment("HARSH: a train with an overloaded or badly unbalanced wagon is stopped by the weighbridge (throttle off, brakes on). Off by default")
    public static boolean overloadStopsTrain = false;
}
