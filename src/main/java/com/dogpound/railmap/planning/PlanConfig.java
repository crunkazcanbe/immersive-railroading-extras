package com.dogpound.railmap.planning;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.common.config.Config;

/**
 * Operations planning + simulation (her "next 48" list #1, 3, 4, 6, 7, 8, 9, 40, 42, 46, 48), config/irextras_planning.cfg.
 * Safe by default: studies and simulations never touch the live railway; nothing below acts on an old world until
 * somebody sets it up (a temporary configuration, a dispatcher region, a split plan, a yard departure).
 */
@Config(modid = RailMap.MODID, name = "irextras_planning", category = "planning")
public final class PlanConfig {
    private PlanConfig() {}

    @Config.Comment("Temporary railway configurations (TSRs, single-line working, hand-worked crossings, length limits, possessions) act on driverless trains once their work crew has installed them")
    public static boolean temporaryWorks = true;

    @Config.Comment("Seconds a work crew takes to install / take out a temporary configuration (plus 1 s per 4 m of zone)")
    @Config.RangeInt(min = 0, max = 1800)
    public static int crewSeconds = 45;

    @Config.Comment("Regional AI dispatchers may stretch or cut station dwells (each region is still off until switched on)")
    public static boolean aiDispatchers = true;

    @Config.Comment("Splitting a train needs it standing (< 1 km/h) at a station stop of its line, brakes applied")
    public static boolean splitNeedsPlatform = true;

    @Config.Comment("The yard panel refuses START when the yard planner predicts a deadlock (a class track overflowing before its departure can leave)")
    public static boolean yardDeadlockGuard = false;

    @Config.Comment("Record configuration changes automatically (lines, train settings, automation rules, power settings, temporary works, dispatchers) every this many seconds; 0 = only on commit")
    @Config.RangeInt(min = 0, max = 3600)
    public static int versionWatchSeconds = 30;

    @Config.Comment("Configuration versions kept")
    @Config.RangeInt(min = 5, max = 200)
    public static int versionsKept = 60;

    @Config.Comment("What-if cost assumptions: contact wire per km, double track per km, a new train, CBTC per line, an extra platform (in-game $)")
    public static int costWirePerKm = 1800, costDoublePerKm = 2600, costTrain = 4000, costCbtcPerLine = 6000, costPlatform = 1500;

    @Config.Comment("Energy prices for the twin's running costs: $ per kWh, $ per litre of diesel")
    public static double priceKwh = 0.12, priceLitre = 1.10;

    @Config.Comment("Minimum interchange time the twin and the timetable robustness study use (s)")
    @Config.RangeInt(min = 10, max = 900)
    public static int minConnectSeconds = 60;

    @Config.Comment("Default route axle-load limit when no structure board says otherwise (t per axle)")
    public static double routeAxleTonnes = 22.5;

    @Config.Comment("Default route loading gauge (m): height, width; structure boards' clearances override")
    public static double gaugeHeightM = 4.65, gaugeWidthM = 3.15;

    // ---- batch 3: #1 changeover, #2 banking, #4 coupling points, #5 turnback ---------------------------------------
    @Config.Comment("Banking: the most buff (push) force a banker may put through the coupler / buffers before its crew backs off (kN)")
    @Config.RangeInt(min = 50, max = 2000)
    public static int bankingBuffLimitKn = 350;

    @Config.Comment("Banking: a banker that overloads the coupler for longer than the limit derails the light vehicle next to it (harsh)")
    public static boolean bankingOverloadDerails = false;

    @Config.Comment("Turnback: driver's cab shut-down + start-up time on top of walking the train (s)")
    @Config.RangeInt(min = 0, max = 600)
    public static int turnbackCabSeconds = 45;

    @Config.Comment("Turnback: crew walking speed along the platform (m/s)")
    @Config.RangeDouble(min = 0.3, max = 5)
    public static double turnbackWalkMs = 1.2;

    @Config.Comment("Changeover / coupling / turnback points refuse to hold a train past this hard limit (min) - it is released with an alarm")
    @Config.RangeInt(min = 1, max = 120)
    public static int opsHardLimitMinutes = 20;
}
