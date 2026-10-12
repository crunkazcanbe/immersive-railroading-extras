package com.dogpound.railmap.stn;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.common.config.Config;

/**
 * Station services (ChatGPT batch 3 #26 #28 #30 #31 #34 #35), config/irextras_stn.cfg. Safe by default: nothing here
 * moves, splits or holds a train in an old world until a block is placed and its key / switch is turned.
 */
@Config(modid = RailMap.MODID, name = "irextras_stn", category = "stn")
public final class StnConfig {
    private StnConfig() {}

    // ---- #26 formation planning ------------------------------------------------------------------------------------
    @Config.Comment("Formation planner target load factor (%): recommend enough cars that the busiest train is at most this full")
    @Config.RangeInt(min = 40, max = 150)
    public static int targetLoadPct = 85;

    @Config.Comment("A Formation Planner with its AUTO key on may split surplus cars off over-formed trains at the ends of its line (they are secured and stabled). false = it only advises")
    public static boolean formationAutoSplit = false;

    // ---- #28 interchanges --------------------------------------------------------------------------------------------
    @Config.Comment("Default walking time (minutes) between two lines' platforms at a station with no Interchange Board")
    @Config.RangeInt(min = 0, max = 30)
    public static int defaultWalkMinutes = 3;

    @Config.Comment("Longest a connection hold may extend a dwelling train's stop (seconds)")
    @Config.RangeInt(min = 0, max = 900)
    public static int maxConnectionHoldSeconds = 300;

    // ---- #30 emergency power -------------------------------------------------------------------------------------------
    @Config.Comment("A Station Emergency Power panel feeds the station's mains machines (lamps, lifts, escalators, heaters...) from its battery / standby generator when their grid supply fails")
    public static boolean emergencyFeed = true;

    @Config.Comment("How far (blocks) a Station Emergency Power panel's emergency bus reaches")
    @Config.RangeInt(min = 8, max = 128)
    public static int emergencyRadius = 48;

    // ---- #31 service diagrams -----------------------------------------------------------------------------------------
    @Config.Comment("A unit whose diagram has hours set and ENFORCE turned on is sent to depot after its diagram ends and returned to service at its start (needs a Depot Office with a road)")
    public static boolean diagramHours = true;

    // ---- #34 lost property + parcels -----------------------------------------------------------------------------------
    @Config.Comment("Seconds an item must lie on the ground near a staffed Lost Property Office (SWEEP on) before staff hand it in")
    @Config.RangeInt(min = 10, max = 280)
    public static int lostAfterSeconds = 60;

    @Config.Comment("Game days a found item is kept before it moves to the unclaimed store")
    @Config.RangeInt(min = 1, max = 100)
    public static int lostKeepDays = 3;

    @Config.Comment("Parcel carriage charge per parcel (cents), paid by the sender when Pride Realms money is present")
    @Config.RangeInt(min = 0, max = 100000)
    public static int parcelFee = 200;

    // ---- #35 disruption ---------------------------------------------------------------------------------------------
    @Config.Comment("The PIS works out and shows travel alternatives (other lines, changes) for cancelled / held services")
    public static boolean disruptionAlternatives = true;
}
