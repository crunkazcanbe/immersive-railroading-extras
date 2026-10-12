package com.dogpound.railmap.sim;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.common.config.Config;

/**
 * Train simulation settings (config/irextras_trainsim.cfg): consist physics, engine management, adhesion, driver aids
 * and the yard. Safe by default: consist physics starts LIGHT (forces measured and shown, nothing breaks) so an old
 * world never loads into a pile of wrecks; turn FULL on per world with /irextras physics full.
 */
@Config(modid = RailMap.MODID, name = "irextras_trainsim", category = "trainsim")
public final class SimConfig {
    private SimConfig() {}

    // ---- 7. consist physics --------------------------------------------------------------------------------------
    @Config.Comment({"Consist physics default for worlds that never chose (a world's own choice: /irextras physics off|light|full):",
            "off   = not even measured",
            "light = coupler forces, braking %, grade and tonnage worked out and shown in the cab / console, overload warnings; nothing breaks",
            "full  = couplers wear and break (the train parts, the rear runs away with its air brakes going on), buff forces can derail light cars"})
    public static String consistPhysics = "light";

    @Config.Comment("Coupler strength multiplier (1 = AAR E-type knuckle: works to 1.0 MN, breaks at about 2.25 MN new, less when worn)")
    @Config.RangeDouble(min = 0.2, max = 10)
    public static double couplerStrength = 1.0;

    @Config.Comment("Buff (compression) force multiplier a car takes before it is squeezed off the track (1 = realistic L/V)")
    @Config.RangeDouble(min = 0.2, max = 10)
    public static double buffStrength = 1.0;

    @Config.Comment("Seconds after a train is first loaded before its couplers can be damaged (settling after a world load)")
    @Config.RangeInt(min = 0, max = 600)
    public static int physicsGraceSeconds = 15;

    // ---- 8. engine management -------------------------------------------------------------------------------------
    @Config.Comment("Diesel engine management: start sequence (prelube, cranking), turbo, oil, coolant fans, battery, traction motor heating, faults, AESS")
    public static boolean engineManagement = true;

    @Config.Comment("Automatic engine start-stop (AESS): minutes of idling, parked with the brakes on, before an engine shuts itself down (0 = never)")
    @Config.RangeInt(min = 0, max = 120)
    public static int aessMinutes = 10;

    @Config.Comment("Traction motors heat up under heavy current and derate when hot")
    public static boolean tractionMotorHeating = true;

    // ---- 12. adhesion ----------------------------------------------------------------------------------------------
    @Config.Comment("Rail-head condition: leaves (worse when wet), frost, dew lower adhesion; tunnels stay dry")
    public static boolean railConditions = true;

    @Config.Comment("Wheels that slide locked under braking wear flat spots (thump, wheel wear, alarm; turned off at a depot)")
    public static boolean wheelFlats = true;

    // ---- 9. driver aids ----------------------------------------------------------------------------------------------
    @Config.Comment("Vigilance device (DSD): seconds without any driver action before it asks for an acknowledgement (0 = off)")
    @Config.RangeInt(min = 0, max = 600)
    public static int vigilanceSeconds = 60;

    @Config.Comment("Vigilance: seconds to acknowledge before the penalty brake")
    @Config.RangeInt(min = 2, max = 60)
    public static int vigilanceAckSeconds = 7;

    @Config.Comment("AWS: warning horn at a restrictive signal (bell at a clear one), acknowledge within this many seconds or the brakes go on (0 = AWS off)")
    @Config.RangeDouble(min = 0, max = 30)
    public static double awsAckSeconds = 2.7;

    @Config.Comment("AWS: distance before a signal the AWS magnet sits, metres")
    @Config.RangeInt(min = 20, max = 1000)
    public static int awsMagnetM = 180;

    @Config.Comment("SPAD detection: passing a signal at danger raises a critical alarm and an emergency brake (held until stopped and acknowledged)")
    public static boolean spadDetection = true;

    @Config.Comment("Driver advisory (energy-efficient driving): POWER / CRUISE / COAST / BRAKE advice in the cab")
    public static boolean driverAdvisory = true;

    // ---- 11. yard ----------------------------------------------------------------------------------------------------
    @Config.Comment("Yard: hump / flat-shove speed a yard panel drives its pusher at, km/h")
    @Config.RangeDouble(min = 1, max = 25)
    public static double humpKmh = 4.0;

    // ---- 106. multiple units / distributed power ---------------------------------------------------------------------
    @Config.Comment({"Multiple-unit control: locomotives and powered cars coupled to the unit being driven follow its throttle, reverser,",
            "independent brake and sanders over the MU train lines; units further down the train work as distributed power (radio).",
            "Off = Immersive Railroading's own copy-the-throttle behaviour"})
    public static boolean multipleUnit = true;

    @Config.Comment("Units whose Immersive Railroading definition is not multi-unit capable may still be wired in (true) or never answer the lead (false)")
    public static boolean muAllUnits = false;

    @Config.Comment("Distributed power radio range, metres (in tunnels the link needs line of sight: 250 m)")
    @Config.RangeInt(min = 100, max = 20000)
    public static int dpRadioM = 3000;

    @Config.Comment("Seconds without the DP radio link before a remote unit drops to idle (real systems: 45)")
    @Config.RangeInt(min = 5, max = 600)
    public static int dpCommLossSeconds = 45;

    // ---- 108. auxiliary systems --------------------------------------------------------------------------------------
    @Config.Comment("Locomotive auxiliary systems: compressor + main reservoir, battery loads and charging, cab HVAC, lights, wipers, fuel and layover heaters, aux converter")
    public static boolean auxSystems = true;

    @Config.Comment("Compressor thermal trip (a compressor pumping without a break overheats and trips until it cools)")
    public static boolean compressorTrip = true;

    // ---- 110. appearance ---------------------------------------------------------------------------------------------
    @Config.Comment("Vehicles show dirt (from the depot's exterior cleanliness), paint weathering, body damage and the company livery")
    public static boolean appearance = true;

    @Config.Comment("Kilometres from a fresh repaint to fully weathered paint")
    @Config.RangeInt(min = 1000, max = 1000000)
    public static int weatheringKm = 30000;

    /** the mode for a world: 0 off, 1 light, 2 full */
    public static int modeOf(String s) {
        if (s == null) return 1;
        switch (s.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "off": return 0;
            case "full": return 2;
            default: return 1;
        }
    }
}
