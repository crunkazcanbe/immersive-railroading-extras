package com.dogpound.railmap.biz;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.common.config.Config;

/**
 * The railway world + business layer (her ChatGPT "next 48" list #11-15, #17, #18, #32-38, #47), config/irextras_biz.cfg.
 * Everything that can cost a player money or refuse them something starts OFF or in its gentle mode, so an old world
 * loads exactly as it played before: you see the new numbers on the console, the desks and boards, and switch the harsh
 * parts on when you want them.
 */
@Config(modid = RailMap.MODID, name = "irextras_biz", category = "biz")
public final class BizConfig {
    private BizConfig() {}

    @Config.Comment("Master switch for the whole world + business layer (towns, journeys, capacity, cargo custody, environment, insurance, regulator, deals, stock exchange, achievements, PSO)")
    public static boolean enabled = true;

    // ---- #11 construction ---------------------------------------------------------------------------------------------
    @Config.Comment("Construction projects pay their crews and plant hire out of the project budget (a county grant covers the estimate; overruns need FUND)")
    public static boolean constructionCosts = true;

    @Config.Comment("County grant towards a newly authorised project, % of its cost estimate")
    @Config.RangeInt(min = 0, max = 200)
    public static int constructionGrantPct = 100;

    @Config.Comment("Track, electrical and testing works stop while a train passes within this many blocks, unless a possession is booked (0 = never)")
    @Config.RangeInt(min = 0, max = 128)
    public static int constructionTrafficRadius = 24;

    // ---- #12 capacity --------------------------------------------------------------------------------------------------
    @Config.Comment("HARSH: a driverless train running a corridor in a slot window another company holds all of is held at its next stop (off = alarm + record only)")
    public static boolean enforceSlots = false;

    @Config.Comment("Default train paths per slot window on a corridor / terminal / siding when none is set")
    @Config.RangeInt(min = 1, max = 64)
    public static int defaultPaths = 4;

    // ---- #13 towns ---------------------------------------------------------------------------------------------------
    @Config.Comment("Towns grow with transport access, jobs and goods; with none of those they shrink (never below their founding size)")
    public static boolean townGrowth = true;

    @Config.Comment("Town population counted into a station catchment within this many blocks")
    @Config.RangeInt(min = 32, max = 1024)
    public static int townCatchment = 256;

    // ---- #14 journeys ------------------------------------------------------------------------------------------------
    @Config.Comment("A change waited longer than this (minutes, game time) is a missed connection")
    @Config.RangeInt(min = 1, max = 120)
    public static int connectionMinutes = 10;

    @Config.Comment("Travellers stuck at a change longer than this (minutes) abandon the trip")
    @Config.RangeInt(min = 5, max = 600)
    public static int abandonMinutes = 60;

    // ---- #15 intermodal ----------------------------------------------------------------------------------------------
    @Config.Comment("Port: in-game days between scheduled ship calls at a berth (0 = ships only come when called with the CALL SHIP button)")
    @Config.RangeInt(min = 0, max = 30)
    public static int shipEveryDays = 2;

    @Config.Comment("Port: free days on the quay before demurrage (cents a day) is charged on a box")
    @Config.RangeInt(min = 0, max = 30)
    public static int quayFreeDays = 2;

    @Config.RangeInt(min = 0, max = 1000000)
    public static int quayDemurrage = 600;

    // ---- #17 cargo quality ---------------------------------------------------------------------------------------------
    @Config.Comment("HARSH: a consignee REJECTS a box whose seal is broken, or that is contaminated, wet, spoiled or wrongly segregated (the goods stay in the box). Off = it is accepted with an investigation opened")
    public static boolean rejectBadCargo = false;

    // ---- #18 environment -----------------------------------------------------------------------------------------------
    @Config.Comment("Grid electricity carbon intensity, g CO2 per kWh (regenerated energy counts back)")
    @Config.RangeInt(min = 0, max = 2000)
    public static int gridGramsPerKwh = 250;

    @Config.Comment("Noise complaints from towns lower their growth (off = noise is only reported)")
    public static boolean noiseAffectsTowns = true;

    // ---- #32 research ------------------------------------------------------------------------------------------------
    @Config.Comment("Optional research tree. OFF by default: every feature is unlocked, exactly as before. On: new companies research technologies to unlock them (existing companies get everything they already use)")
    public static boolean researchTree = false;

    // ---- #33 insurance -----------------------------------------------------------------------------------------------
    @Config.Comment("Insurance premiums are charged daily from a company's operating account (only companies that took out a policy)")
    public static boolean insurance = true;

    // ---- #34 regulator -----------------------------------------------------------------------------------------------
    @Config.Comment("The railway regulator audits companies and issues corrective actions with deadlines. Server owners may switch it off")
    public static boolean regulator = true;

    @Config.Comment("HARSH: a corrective action past its deadline costs a fine (off = reputation only)")
    public static boolean regulatorFines = false;

    @Config.Comment("Days between routine audits of each company")
    @Config.RangeInt(min = 1, max = 60)
    public static int auditEveryDays = 7;

    // ---- #36 stock exchange ------------------------------------------------------------------------------------------
    @Config.Comment("Railway stock exchange: shares, dividends, bonds and project loans in Pride coins")
    public static boolean stockExchange = true;

    // ---- #47 scenarios -----------------------------------------------------------------------------------------------
    @Config.Comment("Name of a scenario (config/irextras_scenarios/<name>.json, generated if missing) to set up every BRAND-NEW world from. Empty = off. Existing worlds are never modified")
    public static String scenarioOnNewWorld = "";

    // ---- #38 PSO -----------------------------------------------------------------------------------------------------
    @Config.Comment("HARSH: a public-service contract whose service requirements were missed this month is clawed back (off = the subsidy is just withheld for the day)")
    public static boolean psoClawback = false;
}
