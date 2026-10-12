package com.dogpound.railmap.freight6;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.common.config.Config;

/**
 * Batch 6 freight + fun (#26-30) - config/irextras_freight6.cfg. Everything new starts safe on an old world: empty silos,
 * empty tanks, no fires, decorations only where somebody fits them.
 */
@Config(modid = RailMap.MODID, name = "irextras_freight6", category = "freight6")
public final class F6Config {
    private F6Config() {}

    // ---- #26 grain elevator ----
    @Config.Comment("Grain one silo bin holds, in items (1 item = 50 kg, so 20000 = 1000 t)")
    @Config.RangeInt(min = 1000, max = 1000000)
    public static int siloItems = 20000;

    @Config.Comment("Grain stored wetter than 15 % moisture heats up in the bin (hot-spot alarm, then spoils to rotten grain) unless it is dried or aerated")
    public static boolean grainSpoils = true;

    @Config.Comment("Bucket elevator (leg) capacity, items per second")
    @Config.RangeInt(min = 1, max = 64)
    public static int legRate = 8;

    // ---- #27 oil tank farm ----
    @Config.Comment("Storage tank capacity, litres (mB)")
    @Config.RangeInt(min = 16000, max = 10000000)
    public static int tankLitres = 400000;

    @Config.Comment("Loading rack pump rate, litres per second")
    @Config.RangeInt(min = 50, max = 20000)
    public static int rackRate = 600;

    @Config.Comment("Vapour above 50 % LEL at a loading rack can be ignited (a real fire the foam system has to put out). Off = gas alarms and ESD only")
    public static boolean vapourIgnition = false;

    // ---- #28 warehouse ----
    @Config.Comment("Items one forklift carries per trip (one pallet)")
    @Config.RangeInt(min = 1, max = 256)
    public static int palletItems = 64;

    @Config.Comment("Forklift speed, blocks per second")
    @Config.RangeDouble(min = 0.5, max = 12)
    public static double forkliftSpeed = 3.0;

    // ---- #29 parcel lockers ----
    @Config.Comment("Sorting offices put parcels and letters for players without a RealmCoin mailbox into the station's parcel lockers (code by chat) instead of the pickup shelf")
    public static boolean mailToLockers = true;

    @Config.Comment("Days (game days) a compartment holds before it is flagged UNCOLLECTED on the console")
    @Config.RangeInt(min = 1, max = 365)
    public static int lockerHoldDays = 7;

    @Config.Comment("A player's RealmCoin orders that are waiting for them (no mailbox / away) are dropped into the parcel locker bank they registered at")
    public static boolean robotOrdersToLockers = true;

    // ---- #30 holidays ----
    @Config.Comment("Seasonal events by the real date (Christmas 1 Dec - 6 Jan, Halloween 15 Oct - 2 Nov): presents and treats, ghost trains, station light shows")
    public static boolean holidays = true;

    @Config.Comment("Force a season regardless of the date: auto, christmas, halloween or none")
    public static String forceSeason = "auto";
}
