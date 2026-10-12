package com.dogpound.railmap.stnlife6;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.common.config.Config;

/** batch 6c station life (bus station, taxi rank, hotel, pub, boarding lift + gap filler): config/irextras_stnlife.cfg */
@Config(modid = RailMap.MODID, name = "irextras_stnlife", category = "stnlife")
public final class LifeConfig {
    private LifeConfig() {}

    @Config.Comment("Bus fare, cents, taken when you board (creative rides free; no Pride Realms = free)")
    @Config.RangeInt(min = 0, max = 100000) public static int busFare = 150;
    @Config.Comment("Seconds a bus stands at each bay with its doors open")
    @Config.RangeInt(min = 3, max = 120) public static int busDwell = 12;
    @Config.Comment("Bays and buses belong to the departure board within this many blocks")
    @Config.RangeInt(min = 16, max = 512) public static int busReach = 192;
    @Config.Comment("Taxi flag fall, cents")
    @Config.RangeInt(min = 0, max = 100000) public static int taxiFlag = 300;
    @Config.Comment("Taxi fare per block driven, cents (tariff 2 night x1.5, tariff 3 holiday x2)")
    @Config.RangeInt(min = 0, max = 10000) public static int taxiPerBlock = 4;
    @Config.Comment("Seconds a called taxi waits at the rank before it leaves")
    @Config.RangeInt(min = 10, max = 1200) public static int taxiWait = 120;
    @Config.Comment("Hotel room, cents per night (standard; superior x1.6, suite x2.5)")
    @Config.RangeInt(min = 0, max = 1000000) public static int hotelRate = 4500;
    @Config.Comment("Hotel rooms (room locks) belong to the reception desk within this many blocks")
    @Config.RangeInt(min = 8, max = 256) public static int hotelReach = 48;
    @Config.Comment("Price of a pint, cents")
    @Config.RangeInt(min = 0, max = 100000) public static int pintPrice = 450;
    @Config.Comment("Pints in a keg")
    @Config.RangeInt(min = 1, max = 200) public static int kegPints = 22;
    @Config.Comment("Boarding lift: the train floor stands this many blocks above the rail top (IR stock); the lift's TRIM knob adds -2..+2 px")
    @Config.RangeDouble(min = 0, max = 2) public static double trainFloor = 0.62;
    @Config.Comment("Gap filler still out when a train moves off: true = it is struck (damaged, needs RESET + screwdriver-free repair) - false = alarm only")
    public static boolean gapStrike = false;
}
