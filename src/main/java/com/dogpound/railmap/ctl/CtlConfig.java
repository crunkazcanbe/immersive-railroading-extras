package com.dogpound.railmap.ctl;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.common.config.Config;

/**
 * Grid, control and comms (her ChatGPT "next 48" list #20 #21 #23 #25 #26 #29 #30 #31 #39 #41 #43 #44),
 * config/irextras_control.cfg. Safe by default: nothing changes in an old world until new blocks are placed (a filter
 * bank, a microgrid controller, a field RTU, a production planner...) and the harsh parts (chargers tripping on
 * harmonics, approvals gating permits and padlocks) are off until switched on.
 */
@Config(modid = RailMap.MODID, name = "irextras_control", category = "control")
public final class CtlConfig {
    private CtlConfig() {}

    // ---- 20 harmonics -------------------------------------------------------------------------------------------
    @Config.Comment("Work out the harmonic spectrum of every AC network (sources, capacitor banks, filter banks, resonance) and use it for THD")
    public static boolean harmonicSpectrum = true;
    @Config.Comment("12 V chargers / UPS trip their output after 20 s over their harmonic tolerance (off = they only alarm)")
    public static boolean harmonicTrips = false;
    @Config.Comment("Voltage THD % a 12 V charger / UPS tolerates")
    @Config.RangeDouble(min = 1, max = 50)
    public static double thdUps = 8;
    @Config.Comment("Voltage THD % a protection IED tolerates before its measurements are suspect")
    @Config.RangeDouble(min = 1, max = 50)
    public static double thdIed = 10;
    @Config.Comment("Voltage THD % an induction motor tolerates before it runs hot")
    @Config.RangeDouble(min = 1, max = 50)
    public static double thdMotor = 5;

    // ---- 21 microgrids ------------------------------------------------------------------------------------------
    @Config.Comment("Seconds the utility must be back and healthy before a microgrid controller in AUTO starts to resynchronise")
    @Config.RangeInt(min = 5, max = 600)
    public static int microgridReturnSeconds = 30;

    // ---- 23 production planning ---------------------------------------------------------------------------------
    @Config.Comment("A Production Planner in AUTO may pause plants that lose their power bid or are overstocked, and switch their recipes")
    public static boolean plannerMayPause = true;

    // ---- 25 distributed control ---------------------------------------------------------------------------------
    @Config.Comment("Telemetry from gear covered by a Field RTU travels over its comms links (latency, loss, partitions). Gear no RTU covers is direct-wired as before")
    public static boolean commsSimulation = true;
    @Config.Comment("Seconds without a good poll before a remote panel marks a value STALE")
    @Config.RangeInt(min = 2, max = 300)
    public static int commsStaleSeconds = 10;

    // ---- 29 tunnel air ------------------------------------------------------------------------------------------
    @Config.Comment("Tunnels with a control panel are split into ventilation sections along the fans' axis: airflow (fans, piston effect, fire buoyancy), smoke and heat move between sections")
    public static boolean tunnelAirSections = true;
    @Config.RangeInt(min = 3, max = 16)
    public static int tunnelSections = 8;

    // ---- 30 passenger information -------------------------------------------------------------------------------
    @Config.Comment("What every PIS sign shows when it can't reach a PIS server")
    public static String pisFallback = "INFORMATION SYSTEM UNAVAILABLE - PLEASE LISTEN FOR ANNOUNCEMENTS";
    @Config.Comment("Messages at or above this priority are also announced on the station's PA speakers")
    @Config.RangeInt(min = 1, max = 9)
    public static int pisAnnouncePriority = 5;

    // ---- 43 approvals -------------------------------------------------------------------------------------------
    @Config.Comment("Issuing a permit to work at a Permit Board needs an approved request (request -> approve -> execute)")
    public static boolean approvalsForPermits = false;
    @Config.Comment("Removing a lockout padlock needs an approved request")
    public static boolean approvalsForPadlocks = false;
    @Config.Comment("Minutes an approval stays valid before it expires unused")
    @Config.RangeInt(min = 1, max = 1440)
    public static int approvalMinutes = 10;

    // ---- 44 chaos engineering -----------------------------------------------------------------------------------
    @Config.Comment("Chaos scenarios only run from a Fault Lab console in TEST MODE (and only inside its zone)")
    public static boolean chaosNeedsTestMode = true;
}
