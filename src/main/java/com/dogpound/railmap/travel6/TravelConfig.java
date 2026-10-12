package com.dogpound.railmap.travel6;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.common.config.Config;

/**
 * Passenger travel (batch 6b #6-10): Pride Card, Pride Rail phone, left luggage, baggage, park & ride.
 * config/irextras_travel.cfg. Safe by default: nothing here charges anyone or holds a train in an old world - it all
 * starts when someone buys a card, rents a locker, checks a bag or drives into a garage.
 */
@Config(modid = RailMap.MODID, name = "irextras_travel", category = "travel")
public final class TravelConfig {
    private TravelConfig() {}

    // ---- Pride Card ----------------------------------------------------------------------------------------------------
    @Config.Comment("Card deposit (cents) charged when a Top-Up Machine issues a new Pride Card; refunded when the card is handed back")
    @Config.RangeInt(min = 0, max = 100000)
    public static int cardDeposit = 500;

    @Config.Comment("Least balance (cents) a card needs to tap in (the gate says TOP UP below it)")
    @Config.RangeInt(min = 0, max = 100000)
    public static int minTapIn = 150;

    @Config.Comment("Daily fare cap: once a card has paid this share (percent) of the day pass for the zones it touched today, the rest of the day's journeys are free")
    @Config.RangeInt(min = 50, max = 400)
    public static int capPct = 100;

    @Config.Comment("Taps in and out at the same station within this many seconds cost nothing (you changed your mind)")
    @Config.RangeInt(min = 0, max = 600)
    public static int sameStationGrace = 120;

    @Config.Comment("An incomplete journey (tapped in, never tapped out) is charged the maximum fare when the card next taps in, or after this many game hours")
    @Config.RangeInt(min = 1, max = 72)
    public static int incompleteHours = 6;

    // ---- left luggage --------------------------------------------------------------------------------------------------
    @Config.Comment("Left-luggage rent per game hour, small locker (cents); large lockers cost double")
    @Config.RangeInt(min = 0, max = 100000)
    public static int lockerPerHour = 25;

    @Config.Comment("Overstay charge per started game hour past the paid time (cents)")
    @Config.RangeInt(min = 0, max = 100000)
    public static int lockerOverstayPerHour = 50;

    @Config.Comment("Wrong PINs in a row before a locker locks out for lockoutSeconds and raises a tamper alarm")
    @Config.RangeInt(min = 1, max = 20)
    public static int pinTries = 3;

    @Config.RangeInt(min = 5, max = 3600)
    public static int lockoutSeconds = 120;

    // ---- baggage -------------------------------------------------------------------------------------------------------
    @Config.Comment("Bags only travel on trains with a baggage car (a car whose name says baggage / luggage / brake / guard / parcels / van / mail). Off = any driverless train on a useful line takes them")
    public static boolean needBaggageCar = false;

    @Config.Comment("Checked bag fee (cents) - the first stack in a bag is free, each extra stack costs this")
    @Config.RangeInt(min = 0, max = 100000)
    public static int bagPerStack = 20;

    @Config.Comment("Belt speed, blocks per second")
    @Config.RangeDouble(min = 0.2, max = 4)
    public static double beltSpeed = 1.0;

    // ---- park & ride ---------------------------------------------------------------------------------------------------
    @Config.Comment("Parking tariff per started game hour (cents)")
    @Config.RangeInt(min = 0, max = 100000)
    public static int parkPerHour = 100;

    @Config.Comment("Park & ride: percent of the tariff a driver pays when their Pride Card made a rail journey since they parked (0 = free with a rail journey)")
    @Config.RangeInt(min = 0, max = 100)
    public static int parkRidePct = 25;

    @Config.Comment("Seconds (of game time) a paid parking ticket has to leave through the exit barrier")
    @Config.RangeInt(min = 10, max = 3600)
    public static int exitGraceSeconds = 600;
}
