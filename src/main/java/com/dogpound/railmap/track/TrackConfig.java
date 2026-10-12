package com.dogpound.railmap.track;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.common.config.Config;

/**
 * Track infrastructure settings (config/irextras_track.cfg) - her ChatGPT list section 3, #13-18. Safe by default: an
 * old world's track starts brand new, so nothing is restricted on first load; the harsh parts have switches here.
 */
@Config(modid = RailMap.MODID, name = "irextras_track", category = "track")
public final class TrackConfig {
    private TrackConfig() {}

    // ---- 13 track quality ----------------------------------------------------------------------------------------
    @Config.Comment("Track wears with traffic: rail head, rail surface, sleepers, ballast fouling, geometry (top, line, twist, gauge).")
    public static boolean trackWear = true;

    @Config.Comment("How fast track deteriorates. 1.0 = a busy heavy-freight main line needs tamping after a few hundred trains.")
    @Config.RangeDouble(min = 0.01, max = 50)
    public static double trackWearRate = 1.0;

    @Config.Comment("Poor track gets an automatic temporary speed restriction (TSR) that drivers and driverless trains obey.")
    public static boolean trackSpeedRestrictions = true;

    @Config.Comment("Manually driven trains running more than this over a TSR get their throttle cut (0 = only warn the driver).")
    @Config.RangeInt(min = 0, max = 100)
    public static int tsrEnforceMarginKmh = 10;

    // ---- 14 point machines ---------------------------------------------------------------------------------------
    @Config.Comment("Chance (per 10,000 throws) that a point machine motor fails on a throw. 0 = only obstruction / ice / no power fail points.")
    @Config.RangeInt(min = 0, max = 10000)
    public static int pointMotorFailPer10k = 8;

    @Config.Comment("Tunnel jet fans need a real grid supply on a touching Power Cable (400 V - 11 kV). Off = they run off thin air.")
    public static boolean heatersAndFansNeedGrid = true;

    // ---- 15 structures -------------------------------------------------------------------------------------------
    @Config.Comment("Overweight or out-of-gauge trains are stopped before a structure that can't take them (off = alarm only).")
    public static boolean structuresStopTrains = true;

    @Config.Comment("Minecraft days between required structure examinations.")
    @Config.RangeInt(min = 1, max = 365)
    public static int structureInspectDays = 14;

    @Config.Comment("Structures deteriorate (corrosion, spalling, scour) over time and with every overload.")
    public static boolean structureDecay = true;

    // ---- 16 tunnels ----------------------------------------------------------------------------------------------
    @Config.Comment("A tunnel emergency (fire / manual) holds every signal into the tunnel at danger.")
    public static boolean tunnelEmergencyHoldsSignals = true;

    // ---- 17 crossings --------------------------------------------------------------------------------------------
    @Config.Comment("Simulated road traffic at predictor-controlled crossings can stall on the crossing (obstacle detection then holds the protecting signals).")
    public static boolean crossingStalls = true;

    @Config.Comment("Chance (per 1,000 closures) that the barriers fail to lower (a real barrier-failure case). 0 = never.")
    @Config.RangeInt(min = 0, max = 1000)
    public static int barrierFailPer1000 = 3;

    // ---- batch 3: #10 construction standards, #9 buffer stops, #7 single-line sections ------------------------------
    @Config.Comment("A track construction standard's line speed is also a permanent speed restriction trains obey (ballasted 160, metro 100, heavy haul 110 km/h...). Off = only extra wear and the overspeed count.")
    public static boolean trackStandardLimits = false;

    @Config.Comment("Building / upgrading track to a standard with the Track Maintenance Kit also costs Pride coins (cost per metre x piece length) when Pride Realms is installed.")
    public static boolean trackStandardCostsCoins = true;

    @Config.Comment("An intelligent buffer stop struck harder than it can absorb derails the leading vehicle (a real overrun). Off = emergency brake, damage and a MAJOR alarm only.")
    public static boolean bufferOverrunDerails = false;

    @Config.Comment("Single-line / gauntlet section controllers stop driverless trains that would enter against the section's direction even with no signal linked (they are held at the entry). Off = only the linked entry signals hold.")
    public static boolean singleLineHoldsAutopilot = true;

    // ---- batch 3 #3 / #8 turntables + movable bridges ---------------------------------------------------------------
    @Config.Comment("A train that runs onto an open / unlocked BASCULE span drops into the gap (IR wreck). Off = emergency brake + MAJOR alarm only.")
    public static boolean bridgeOpenDerails = false;

    @Config.Comment("Boats and ships approaching a movable bridge that isn't open are held 40 m off until it opens.")
    public static boolean boatsWaitAtBridges = true;
}
