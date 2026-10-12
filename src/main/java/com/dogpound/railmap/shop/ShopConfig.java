package com.dogpound.railmap.shop;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.common.config.Config;

/**
 * Station shops (newsstand, coffee kiosk, ticket office counter, vending upgrade), config/irextras_shops.cfg.
 * Nothing happens in an old world until a shop block is placed and stocked.
 */
@Config(modid = RailMap.MODID, name = "irextras_shops", category = "shops")
public final class ShopConfig {
    private ShopConfig() {}

    @Config.Comment("Station passengers buy from the shops (one in N of the people boarding at the station); false = only players buy")
    public static boolean paxBuy = true;

    @Config.Comment("One newspaper sold per this many passengers boarding at the station")
    @Config.RangeInt(min = 5, max = 1000)
    public static int paxPerPaper = 30;

    @Config.Comment("One coffee sold per this many passengers boarding at the station")
    @Config.RangeInt(min = 5, max = 1000)
    public static int paxPerCoffee = 25;

    @Config.Comment("Print a fresh daily newspaper each Minecraft morning (the edition is built from the day's real server events)")
    public static boolean dailyPaper = true;

    @Config.Comment("Copies a Newsstand with its AUTO PRINT key on prints each morning (paper + ink from its stock)")
    @Config.RangeInt(min = 0, max = 27)
    public static int autoPrintCopies = 6;

    @Config.Comment("Ticket Office: Pride coins (cents) charged per fare item when you pay by coin")
    @Config.RangeInt(min = 1, max = 100000)
    public static int centsPerFareItem = 100;

    @Config.Comment("Coffee kiosk boiler: seconds from cold to working pressure with mains on")
    @Config.RangeInt(min = 5, max = 600)
    public static int boilerWarmSeconds = 60;

    @Config.Comment("Drinks a coffee machine makes before it asks to be cleaned (backflush)")
    @Config.RangeInt(min = 5, max = 1000)
    public static int drinksBetweenCleans = 40;
}
