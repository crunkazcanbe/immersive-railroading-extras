package com.dogpound.railmap.incident;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.common.config.Config;

/**
 * Emergency response + control-room tools (her ChatGPT list #67-70, #72, #74-80), config/irextras_incident.cfg.
 * Safe by default: nothing here wrecks, burns or blocks anything on its own in an old world; incidents only open from
 * things that already happened (a wreck, a failed train, a fire the panel can see), and closed lines reopen by
 * themselves in stages unless a commander holds them.
 */
@Config(modid = RailMap.MODID, name = "irextras_incident", category = "incident")
public final class IncidentConfig {
    private IncidentConfig() {}

    // ---- 67 incident command -----------------------------------------------------------------------------------
    @Config.Comment("Open an incident record for every wreck, failed train, fire and evacuation (timeline, response times, final report)")
    public static boolean incidentCommand = true;

    @Config.Comment("Serious incidents (level 2+) automatically protect the adjacent lines (a wider line block) until reopened")
    public static boolean autoProtectAdjacent = false;

    @Config.Comment("How much wider than the incident the adjacent-line protection reaches (blocks)")
    @Config.RangeInt(min = 2, max = 64)
    public static int adjacentMargin = 8;

    @Config.Comment("Reopen closed sections by themselves, one stage at a time, once each is safe (off = only the commander reopens them)")
    public static boolean autoReopen = true;

    @Config.Comment("Seconds between automatic reopening stages")
    @Config.RangeInt(min = 5, max = 600)
    public static int reopenStageSeconds = 30;

    // ---- 68 rescue and towing ----------------------------------------------------------------------------------
    @Config.Comment("Towing speed limit, failed traction (the failed train's own brakes work), km/h")
    @Config.RangeInt(min = 5, max = 120)
    public static int towTractionKmh = 40;

    @Config.Comment("Towing speed limit, failed / isolated brakes (only the rescue engine brakes), km/h")
    @Config.RangeInt(min = 5, max = 120)
    public static int towBrakeKmh = 15;

    @Config.Comment("Towing speed limit, damaged vehicles after a derailment, km/h")
    @Config.RangeInt(min = 5, max = 120)
    public static int towDamagedKmh = 10;

    @Config.Comment("A failed train whose braked weight is below this % counts as a brake failure (different rescue rules)")
    @Config.RangeInt(min = 5, max = 100)
    public static int brakeFailPct = 40;

    @Config.Comment("Shut the rescue engine's throttle when the tow runs over its speed limit (off = alarm only)")
    public static boolean towEnforce = true;

    // ---- 69 fire and dangerous goods -----------------------------------------------------------------------------
    @Config.Comment("Fire panels may open the traction feeder breakers covering their zone (ISOLATE button, or AUTO)")
    public static boolean fireIsolatesPower = true;

    @Config.Comment("Seconds of pre-discharge warning before a fire suppression system releases (ABORT holds it)")
    @Config.RangeInt(min = 5, max = 120)
    public static int preDischargeSeconds = 30;

    @Config.Comment("Clean-up seconds before a line reopens after: an ordinary fire, a fuel spill, a chemical, a gas, radioactive cargo")
    public static int[] cleanupSeconds = {60, 180, 300, 240, 600};

    // ---- 70 evacuation -----------------------------------------------------------------------------------------
    @Config.Comment("Seconds between repeats of the evacuation message on the PA")
    @Config.RangeInt(min = 10, max = 300)
    public static int evacRepeatSeconds = 30;

    @Config.Comment("Release the doors of stopped trains inside an evacuation zone")
    public static boolean evacReleaseDoors = true;

    // ---- 72 alarm correlation ----------------------------------------------------------------------------------
    @Config.Comment("Alarms within this many seconds after a root-cause alarm can be its consequences")
    @Config.RangeInt(min = 5, max = 600)
    public static int correlationSeconds = 90;

    @Config.Comment("...and within this many blocks of it (alarms with no location: time only)")
    @Config.RangeInt(min = 16, max = 4096)
    public static int correlationRadius = 256;

    @Config.Comment("Consequential alarms do not sound the horn (they still list, flash and count)")
    public static boolean quietConsequences = true;

    // ---- 74 train recorder -------------------------------------------------------------------------------------
    @Config.Comment("Minutes each locomotive's train recorder keeps")
    @Config.RangeInt(min = 1, max = 60)
    public static int recorderMinutes = 10;

    // ---- 76 announcements --------------------------------------------------------------------------------------
    @Config.Comment("Minutes a train must be late / held before the station announces the delay")
    @Config.RangeInt(min = 1, max = 60)
    public static int announceDelayMinutes = 2;

    @Config.Comment("Minutes a train held by an incident before the station announces it cancelled")
    @Config.RangeInt(min = 2, max = 240)
    public static int cancelMinutes = 15;

    @Config.Comment("Announcement templates: {train} {dest} {station} {line} {min} {plat} {reason} {place} {time}")
    public static String tDelay = "We are sorry to announce that the {train} to {dest} is delayed by approximately {min} minutes. This is due to {reason}.";
    public static String tPlatform = "Platform alteration. The {train} to {dest} will now depart from platform {plat}.";
    public static String tCancel = "We are sorry to announce that the {train} to {dest} has been cancelled. This is due to {reason}.";
    public static String tConnection = "Passengers for the {line}, please change here. The next {line} train is due in {min} minutes.";
    public static String tNext = "The next train to arrive at {station} is the {train} to {dest}, expected in {min} minutes.";
    public static String tEmergency = "Attention please. Due to an emergency near {place}, services are suspended. Please follow the instructions of station staff.";
    public static String tEvacuation = "This is an emergency. Please leave the station now by the nearest exit and go to the assembly point. Do not return for belongings.";
    public static String tAllClear = "The emergency is now over. Services will resume shortly. Thank you for your patience.";

    // ---- 77 / 79 analytics and reports -------------------------------------------------------------------------
    @Config.Comment("A departure up to this many seconds late counts as on time")
    @Config.RangeInt(min = 0, max = 600)
    public static int onTimeSeconds = 60;

    @Config.Comment("Write each daily / weekly report as JSON to <world>/data/irextras_reports/ (for websites and spreadsheets)")
    public static boolean exportReports = true;

    // ---- 80 scenario testing -----------------------------------------------------------------------------------
    @Config.Comment("Minutes a scenario run simulates ahead")
    @Config.RangeInt(min = 10, max = 480)
    public static int scenarioMinutes = 60;

    @Config.Comment("Least seconds between two trains leaving the same stop in the same direction (scenario model)")
    @Config.RangeInt(min = 10, max = 900)
    public static int scenarioHeadwaySeconds = 90;
}
