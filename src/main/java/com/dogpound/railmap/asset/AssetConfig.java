package com.dogpound.railmap.asset;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.common.config.Config;

/**
 * Assets, maintenance, procurement, inspection and commissioning (config/irextras_assets.cfg). Her ChatGPT list (48
 * next): #2 lifecycle, #5 fleet monitoring, #10 / #45 commissioning, #16 spares procurement, #19 power test bench,
 * #22 thermal modelling, #24 remanufacturing, #27 non-destructive inspection, #28 locomotive rebuilds.
 * Safe by default: old worlds load with every existing piece of gear COMMISSIONED (as built) and nothing harsh happens
 * until a switch below says so.
 */
@Config(modid = RailMap.MODID, name = "irextras_assets", category = "assets")
public final class AssetConfig {
    private AssetConfig() {}

    // ---- #2 asset register + lifecycle ---------------------------------------------------------------------------------
    @Config.Comment("Keep the asset register: track, point machines, signals, transformers, breakers, motors, insulators, platforms, bridges, tunnels and linings")
    public static boolean assetRegister = true;

    @Config.Comment("Assets age with real use: gross tonnes and curve speed (track), throws (points), lamp hours (signals), hot-spot temperature (transformers),"
            + " operations and fault currents (breakers), running hours and starts (motors), surges and wet days (insulators), axle loads (structures)")
    public static boolean ageing = true;

    @Config.Comment("Speed of asset ageing: 1 = a well-used asset reaches the end of its service life in roughly 60 game days")
    @Config.RangeDouble(min = 0, max = 50)
    public static double ageingRate = 1.0;

    @Config.Comment("Raise INFO alarms when an asset's inspection is overdue")
    public static boolean inspectionAlarms = true;

    @Config.Comment("Deteriorating assets grow hidden (latent) defects - rail cracks, axle cracks, hot joints, tracking insulation, spalling - for the inspection tools to find")
    public static boolean latentDefects = true;

    @Config.Comment("HARSH: a defect that grows past its critical size does real damage (a broken rail gets a 20 km/h TSR, a point machine fails, a breaker sticks). Off = alarm only")
    public static boolean defectConsequences = false;

    // ---- #5 fleet monitoring --------------------------------------------------------------------------------------------
    @Config.Comment("Record locomotive telemetry every 30 s (temperatures, oil pressure, battery, couplers, faults) and look for trends")
    public static boolean telemetry = true;

    @Config.Comment("Predictive trend codes (P-codes) raise INFO alarms")
    public static boolean telemetryAlarms = true;

    // ---- #16 procurement --------------------------------------------------------------------------------------------------
    @Config.Comment("Depot stores raise purchase orders by themselves for spares under their reorder level and for job cards waiting on parts")
    public static boolean autoProcure = true;

    @Config.Comment("Lead time multiplier for purchase orders (1 = catalogue 2 days, express half a day)")
    @Config.RangeDouble(min = 0.05, max = 20)
    public static double leadTimeScale = 1.0;

    @Config.Comment("A job card short of a part may use a substitute that is in stock (a bogie from wheelsets + bearings + brake blocks, a remanufactured motor...)")
    public static boolean substitutions = true;

    // ---- #24 remanufacturing -----------------------------------------------------------------------------------------------
    @Config.Comment("Worn parts taken off in the depot come back to the stores as cores (used components) for the Remanufacturing Bench")
    public static boolean cores = true;

    // ---- #22 thermal --------------------------------------------------------------------------------------------------------
    @Config.Comment("Transformer / motor / cable thermal model: top-oil + hot-spot with thermal inertia, cooling failures, insulation loss of life (IEC 60076-7)")
    public static boolean thermalModel = true;

    @Config.Comment("HARSH: thermal overload (49) trips breakers on the inverse-time curve, and hot-spot loss of life eats the insulation. Off = alarms only")
    public static boolean thermalTrips = false;

    // ---- #10 / #45 commissioning ------------------------------------------------------------------------------------------
    @Config.Comment("New gear placed from now on starts UNCOMMISSIONED (an INFO alarm reminds you). Old gear is registered as COMMISSIONED (as built)")
    public static boolean commissioning = true;

    @Config.Comment("HARSH: an UNCOMMISSIONED circuit breaker refuses to close until its acceptance tests pass and it is signed off")
    public static boolean commissioningRequired = false;

    // ---- #28 rebuilds ------------------------------------------------------------------------------------------------------
    @Config.Comment("A rebuilt locomotive is held to 25 km/h until it passes its acceptance test run")
    public static boolean rebuildAcceptance = true;
}
