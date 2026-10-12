package com.dogpound.railmap.infra;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.common.config.Config;

/**
 * Electrification + permanent-way maintenance settings (config/irextras_infra.cfg) - her ChatGPT list batch 3, #36-50.
 * Safe by default: an old world behaves as before on first load; everything harsh has a switch here and is OFF.
 */
@Config(modid = RailMap.MODID, name = "irextras_infra", category = "infra")
public final class InfraConfig {
    private InfraConfig() {}

    // ---- 36 neutral sections -------------------------------------------------------------------------------------------
    @Config.Comment("On-board APC: the cab opens the main breaker (VCB) by itself through a neutral section even with the pantograph in MANUAL. Off = a manually worked pantograph must be shut off by the driver or it flashes over.")
    public static boolean cabAutoVcb = true;

    @Config.Comment("Warn the driver this far ahead of a neutral section (cab display / DMI planning area), metres. 0 = off.")
    @Config.RangeInt(min = 0, max = 4000)
    public static int neutralWarnM = 600;

    // ---- 37 parallel feeding ------------------------------------------------------------------------------------------
    @Config.Comment("Parallel (double-end) feeding: a train between two Track Feeders draws from both in proportion to how close and how stiff each one is (off = the first feeder found carries it all, as before).")
    public static boolean loadSharing = true;

    @Config.Comment("Feeders on one OHLE section whose voltages differ by more than this (%) drive a circulating current between them - a tap mismatch alarm.")
    @Config.RangeDouble(min = 0.5, max = 20)
    public static double circulatingAlarmPct = 3.0;

    // ---- 38 wayside energy storage ------------------------------------------------------------------------------------
    @Config.Comment("Wayside storage ages: cycling, heat and high state of charge wear the cells (state of health).")
    public static boolean essDegradation = true;

    @Config.Comment("HARSH: a badly worn lithium store run over 80 C can go into thermal runaway (fire, destroyed).")
    public static boolean essThermalRunaway = false;

    // ---- 39 fault location ------------------------------------------------------------------------------------------
    @Config.Comment("Inspection crew road speed and search walking speed, m/s.")
    @Config.RangeInt(min = 1, max = 40)
    public static int crewDriveMps = 8;
    @Config.RangeInt(min = 1, max = 10)
    public static int crewSearchMps = 1;

    @Config.Comment("A crew that finds torn-down contact wire renews it itself once the section is isolated and earthed.")
    public static boolean crewsRepairWire = true;

    // ---- 40 relay testing --------------------------------------------------------------------------------------------
    @Config.Comment("Protection relays drift out of calibration: % per game day (random walk) of pick-up and timing.")
    @Config.RangeDouble(min = 0, max = 5)
    public static double relayDriftPctPerDay = 0.15;

    @Config.Comment("HARSH: the drift acts on the live protection (a drifted relay trips late or early). Off = only the test set sees it.")
    public static boolean relayDriftAffectsProtection = false;

    @Config.Comment("Calibration interval in game days: older calibrations raise a 'calibration due' alarm. 0 = no alarm.")
    @Config.RangeInt(min = 0, max = 3650)
    public static int relayCalDays = 30;

    @Config.Comment("HARSH: a relay test run with the test block LIVE (trip not isolated) trips the real breaker.")
    public static boolean testSetTripsLive = false;

    // ---- 41 rail flaws -----------------------------------------------------------------------------------------------
    @Config.Comment("Rail defects get real UIC classes (tache ovale, head checks, squats, bolt-hole and weld defects) with their own growth and remedy.")
    public static boolean railFlawClasses = true;

    @Config.Comment("An ultrasonic test train (IR stock tagged UTU / ULTRASONIC / SPERRY) loses probe coupling above this speed, km/h.")
    @Config.RangeInt(min = 5, max = 200)
    public static int testTrainMaxKmh = 40;

    // ---- 42 grinding + welding ------------------------------------------------------------------------------------
    @Config.Comment("On-track machines (grinder, welder) refuse to work unless a possession covers them (planned possession or a Track Blocking Switch).")
    public static boolean worksNeedPossession = false;

    @Config.Comment("HARSH: grinding sparks set dry grass / leaves alight unless the water spray is on.")
    public static boolean grinderFires = false;

    // ---- 43 drainage -------------------------------------------------------------------------------------------------
    @Config.Comment("Flood sensors: ALERT at this much water in the formation (mm), ALARM at the rail head.")
    @Config.RangeInt(min = 5, max = 1000)
    public static int floodAlertMm = 60;
    @Config.RangeInt(min = 10, max = 2000)
    public static int floodAlarmMm = 150;

    // ---- 45 possessions ----------------------------------------------------------------------------------------------
    @Config.Comment("Work gangs a planned possession has (tasks run this many at a time).")
    @Config.RangeInt(min = 1, max = 8)
    public static int possessionGangs = 2;

    @Config.Comment("Possession task durations x this (0.25 = four times quicker).")
    @Config.RangeDouble(min = 0.05, max = 10)
    public static double possessionTaskScale = 1.0;

    // ---- 50 expansion advisor -----------------------------------------------------------------------------------------
    @Config.Comment("Cost of a new feeder substation (traction transformer + feeder breakers), $.")
    @Config.RangeInt(min = 0, max = 100_000_000)
    public static int costSubstation = 15_000;
    @Config.Comment("Cost of a wayside energy store, $.")
    @Config.RangeInt(min = 0, max = 100_000_000)
    public static int costEss = 9_000;
    @Config.Comment("A passing loop costs this share of doubling the whole stretch.")
    @Config.RangeDouble(min = 0.05, max = 1)
    public static double loopCostShare = 0.3;
}
