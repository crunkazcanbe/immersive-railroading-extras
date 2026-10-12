package com.dogpound.railmap.interior;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.common.config.Config;

/**
 * Walk-in train interiors (requested feature), config/irextras_interior.cfg.
 * Nothing changes in an old world unless a car's definition name says dining / sleeper / coach (autoByName) or a car
 * is fitted with the Interior Fitting Kit.
 */
@Config(modid = RailMap.MODID, name = "irextras_interior", category = "interior")
public final class InteriorConfig {
    private InteriorConfig() {}

    @Config.Comment("Cars whose definition name says dining / buffet / cafe / restaurant, sleeper / couchette / berth, or coach / passenger get that interior automatically (until fitted otherwise). Off = only fitted cars")
    public static boolean autoByName = true;

    @Config.Comment("Interiors are drawn only within this many blocks of you")
    @Config.RangeInt(min = 8, max = 128)
    public static int renderRange = 40;
    @Config.Comment("At most this many cars' interiors drawn per frame (nearest first)")
    @Config.RangeInt(min = 1, max = 64)
    public static int maxCars = 8;
    @Config.Comment("Items on racks / the counter are drawn only within this many blocks")
    @Config.RangeInt(min = 2, max = 64)
    public static int itemRange = 16;

    @Config.Comment("Ticks between interior state updates sent to nearby players (screens, lamps, racks)")
    @Config.RangeInt(min = 2, max = 100)
    public static int syncTicks = 10;

    @Config.Comment("Sleeping in a berth at night skips the night when EVERY player in the dimension is asleep (bed or berth), like vanilla. Off = berths only restore you")
    public static boolean berthSkipsNight = true;
    @Config.Comment("Seconds asleep in a berth before it counts for skipping the night")
    @Config.RangeInt(min = 1, max = 60)
    public static int berthSleepSeconds = 5;
    @Config.Comment("Half-hearts a berth restores every 5 seconds")
    @Config.RangeInt(min = 0, max = 10)
    public static int berthHeal = 1;

    @Config.Comment("Car fresh-water tank (litres). Sinks, the kettle, the coffee machine and toilet flushes use it; a water bucket on a sink refills 10 L")
    @Config.RangeInt(min = 20, max = 2000)
    public static int waterTankLitres = 400;
    @Config.Comment("Water is used up (off = endless water)")
    public static boolean waterUsed = true;

    @Config.Comment("Riding height correction (blocks) for interior seats: the cushion top is the seat, this moves the sitting player down onto it")
    public static double seatSitDrop = 0.75;

    @Config.Comment("Base price (cents) of an item sold at the café counter; food adds foodPerShank cents per half-shank it restores")
    @Config.RangeInt(min = 0, max = 100000)
    public static int basePrice = 150;
    @Config.RangeInt(min = 0, max = 100000)
    public static int foodPerShank = 40;
    @Config.Comment("Brewed drinks (coffee, tea, hot chocolate) price in cents")
    @Config.RangeInt(min = 0, max = 100000)
    public static int drinkPrice = 350;

    @Config.Comment("Passenger alarm handles and help-call buttons raise Alarm Manager alarms")
    public static boolean alarms = true;
}
