package com.dogpound.railmap.depot;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.common.config.Config;

/**
 * Depots, rolling-stock management and station facilities (config/irextras_depot.cfg). Her ChatGPT list section 8
 * (#51-62 + condition-based operating restrictions) and section 12 (#81-85). Safe by default: nothing here stops a
 * train that is running in an old world - a part has to wear past CRITICAL before it is restricted at all.
 */
@Config(modid = RailMap.MODID, name = "irextras_depot", category = "depot")
public final class DepotConfig {
    private DepotConfig() {}

    // ---- condition-based operating restrictions ----------------------------------------------------------------
    @Config.Comment({"Condition curve per part (wheels, brakes, engine, bearings, electrics) from its wear:",
            "HEALTHY under 50 %, DEGRADED 50-80 % (less power / weaker brakes / hotter running),",
            "CRITICAL 80-100 % (warning + speed restriction), FAILED 100 %+ (the part no longer works properly)"})
    public static boolean conditionEffects = true;

    @Config.Comment("A FAILED part on a driverless train makes it 'failed in service': it stays at the next station until rescued (a spare takes over). Off by default (harsh)")
    public static boolean failedHoldsTrain = false;

    @Config.Comment("Speed restrictions (km/h): CRITICAL wheels / bearings / brakes, FAILED wheels / bearings / brakes")
    @Config.RangeInt(min = 5, max = 300)
    public static int critWheelsKmh = 70, critBearingsKmh = 60, critBrakesKmh = 80, failWheelsKmh = 30, failBearingsKmh = 20, failBrakesKmh = 30;

    // ---- maintenance plans ---------------------------------------------------------------------------------------
    @Config.Comment("Default exam plan for stock with no plan of its own: km, operating hours (game hours), days, faults - 0 = not used")
    public static int planKm = 50, planHours = 24, planDays = 7, planFaults = 5;

    @Config.Comment("Raise an EXAM DUE alarm when a unit's plan runs out")
    public static boolean examAlarms = true;

    @Config.Comment("Predictive maintenance: alarm when a part's estimated remaining life drops under this many km")
    @Config.RangeInt(min = 0, max = 10000)
    public static int predictAlarmKm = 15;

    // ---- workshop -------------------------------------------------------------------------------------------------
    @Config.Comment("Heavy depot machines (wheel lathe, bogie drop, wash plant, lift, escalator...) need mains power from an IR Extras grid cable or machine beside them (120-480 V AC). false = they run without mains")
    public static boolean mainsRequired = true;

    @Config.Comment("A unit that had brake work (brake blocks, bogie exchange) must pass a Brake Test Rig test before RETURN TO SERVICE")
    public static boolean brakeTestAfterBrakeWork = true;

    @Config.Comment("Seconds a depot fitter takes for each job card (exam, repair, wheels, bogie) when no player works it")
    @Config.RangeInt(min = 5, max = 3600)
    public static int fitterSeconds = 60;

    @Config.Comment("Wheel diameter (mm): new, and the scrap limit below which a wheelset can't be turned again")
    public static int wheelNewMm = 1000, wheelScrapMm = 920;

    // ---- servicing + crew -------------------------------------------------------------------------------------------
    @Config.Comment("Passenger stock gets dirty, uses its water, fills its toilet tanks and sells its catering as people ride")
    public static boolean servicing = true;

    @Config.Comment("Crew rules: max duty (game hours), a break due after (game hours) of driving, break length (game minutes)")
    public static int dutyMaxHours = 12, breakAfterHours = 6, breakMinutes = 30;

    @Config.Comment("Driverless trains on lines marked ATTENDED at a Crew Booking-On Desk wait at stations without a qualified driver on duty")
    public static boolean crewRequired = true;

    // ---- stations -----------------------------------------------------------------------------------------------------
    @Config.Comment("Share of passengers who need step-free access (they avoid trips to or from stations that are not step-free)")
    @Config.RangeDouble(min = 0, max = 0.5)
    public static double stepFreeShare = 0.12;

    @Config.Comment("Station facilities cost their operator a daily running cost and earn concession / vending income")
    public static boolean facilityCosts = true;

    @Config.Comment("Platform allocation panels choose the platform for arriving driverless trains")
    public static boolean platformAllocation = true;
}
