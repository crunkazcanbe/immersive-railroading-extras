package com.dogpound.railmap.ops6;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.common.config.Config;

/**
 * Batch 6 E (#21-25) - config/irextras_ops6.cfg: RCO beltpacks, the 250 t breakdown crane, flange lubricators, the master
 * clock system and the driver training simulator. Safe for old worlds: nothing here changes a world until the gear is
 * placed (slave clocks only start to drift once a master clock has been installed somewhere in the world).
 */
@Config(modid = RailMap.MODID, name = "irextras_ops6", category = "ops6")
public final class Ops6Config {
    private Ops6Config() {}

    @Config.Comment("RCO beltpack radio range in blocks: further than this from its loco = COMMS LOST = emergency brake")
    @Config.RangeInt(min = 16, max = 1000)
    public static int rcoRange = 120;

    @Config.Comment("RCO vigilance (dead-man): seconds of no beltpack input while the loco moves before the warning; 5 s later the penalty brake")
    @Config.RangeInt(min = 10, max = 600)
    public static int rcoVigilanceS = 45;

    @Config.Comment("RCO speed limit (km/h) in SPEED mode and the overspeed penalty in THROTTLE mode (real yard RCO: 10 mph = 16 km/h)")
    @Config.RangeInt(min = 5, max = 60)
    public static int rcoMaxKmh = 25;

    @Config.Comment("Flange lubricators cut rail side wear and wheel flange wear on the track around them, and stop curve squeal")
    public static boolean lubeEffect = true;

    @Config.Comment("How far along the track (blocks) a lubricator's grease is carried by the wheels")
    @Config.RangeInt(min = 4, max = 128)
    public static int lubeRange = 24;

    @Config.Comment("Master clock radio / impulse range in blocks (slave clocks further away run free)")
    @Config.RangeInt(min = 32, max = 100000)
    public static int clockRange = 512;

    @Config.Comment("Slave clocks with no master clock in range drift (only once a master clock exists somewhere in the world)")
    public static boolean clockDrift = true;

    @Config.Comment("How far a free-running slave clock drifts, game minutes per game day at most (each clock gets its own rate up to this)")
    @Config.RangeDouble(min = 0, max = 60)
    public static double clockDriftMinPerDay = 3.0;

    @Config.Comment("Training simulator hours count toward the driver competency record at this fraction (real schemes credit sim time), 0 = never")
    @Config.RangeDouble(min = 0, max = 1)
    public static double simHoursCredit = 0.5;

    @Config.Comment("Most simulator hours (per traction) that can ever be credited to a driver's record")
    @Config.RangeDouble(min = 0, max = 50)
    public static double simHoursCap = 2.0;

    @Config.Comment("Breakdown crane LMI (load moment indicator) cut-out at 100 % of the duty chart (off = it only warns)")
    public static boolean craneLmiCut = true;
}
