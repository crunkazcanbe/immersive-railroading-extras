package com.dogpound.railmap.safety;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.common.config.Config;

/**
 * Signalling batch 3 (her ChatGPT batch-3 list #11-20, #46, #48, #49), config/irextras_signalling.cfg. Safe by default:
 * nothing changes in an old world until the new gear is placed, and the harsh parts (route-knowledge speed limits,
 * suspending drivers after a SPAD, the supervisor braking trains on its own) stay off until switched on.
 */
@Config(modid = RailMap.MODID, name = "irextras_signalling", category = "signalling")
public final class SigConfig {
    private SigConfig() {}

    // ---- #11 trip cocks ------------------------------------------------------------------------------------------
    @Config.Comment("A train passing a raised train-stop trip arm gets an emergency brake (held until it stands and the trip valve is reset)")
    public static boolean tripCockBrake = true;

    // ---- #12 / #13 balises and cab signalling --------------------------------------------------------------------
    @Config.Comment("Seconds the driver has to acknowledge a restrictive balise telegram / cab signal downgrade (0 = no acknowledgement needed)")
    @Config.RangeDouble(min = 0, max = 30)
    public static double cabAckSeconds = 5;
    @Config.Comment("An unacknowledged balise / cab signal warning applies the brakes (held until stopped and acknowledged)")
    public static boolean cabAckPenalty = true;
    @Config.Comment("Continuous cab signalling everywhere: every downgrade of the next signal's aspect sounds the cab alerter and must be acknowledged (off = only at balises)")
    public static boolean cabSignals = false;

    // ---- #14 signal power backup ----------------------------------------------------------------------------------
    @Config.Comment("Signal battery cabinets keep the 12 V gear they back up working through a power cut while their batteries last")
    public static boolean batteryBackup = true;

    // ---- #16 sighting ---------------------------------------------------------------------------------------------
    @Config.Comment("Raise a standing alarm for every signal whose sighting is short of the required distance")
    public static boolean sightingAlarms = false;

    // ---- #17 radio coverage ---------------------------------------------------------------------------------------
    @Config.Comment("Size (m) of one cell of the radio coverage / dead-zone map")
    @Config.RangeInt(min = 8, max = 64)
    public static int coverageCell = 16;

    // ---- #18 isolation --------------------------------------------------------------------------------------------
    @Config.Comment("Isolating a signal for maintenance needs the signaller to answer the request first")
    public static boolean isolationNeedsSignaller = true;

    // ---- #20 safety supervisor ------------------------------------------------------------------------------------
    @Config.Comment("Margin (km/h) over the supervisor's own authority envelope before it calls a violation")
    @Config.RangeDouble(min = 1, max = 40)
    public static double supervisorMarginKmh = 5;

    // ---- #46 driver competency ------------------------------------------------------------------------------------
    @Config.Comment("Times a driver has to pass a signal before they 'know' it (route knowledge)")
    @Config.RangeInt(min = 1, max = 20)
    public static int routeKnowledgePasses = 3;
    @Config.Comment("Route knowledge lapses after this many game days without driving past the signal (0 = never)")
    @Config.RangeInt(min = 0, max = 365)
    public static int routeKnowledgeLapseDays = 0;
    @Config.Comment("HARSH: drivers without route knowledge are held to 40 km/h past unknown signals, and a SPAD / trip suspends a driver until reassessed")
    public static boolean competencyEnforce = false;

    // ---- #49 control panels ---------------------------------------------------------------------------------------
    @Config.Comment("How far (m) a control panel element may be from the device it is bound to")
    @Config.RangeInt(min = 16, max = 2048)
    public static int panelBindRange = 512;
}
