package com.dogpound.railmap.light;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.common.config.Config;

/**
 * Night lighting (config/irextras_lighting.cfg): station / yard / tunnel luminaires, the photocell and contactor panel,
 * glowing signals and lit trains. Everything that costs frame time or light updates can be scaled down here.
 */
@Config(modid = RailMap.MODID, name = "irextras_lighting", category = "lighting")
public final class LightConfig {
    private LightConfig() {}

    // ---- glow and halos (client) -----------------------------------------------------------------------------------
    @Config.Comment("Halo size multiplier for lit signal aspects, panel lamps and luminaires (0 = no halos, 1 = normal, 2 = big)")
    @Config.RangeDouble(min = 0, max = 3)
    public static double haloScale = 1.0;

    @Config.Comment("How far (blocks) signal masts and gantries are drawn, so a lit aspect and its halo read far down the line (vanilla: 64)")
    @Config.RangeInt(min = 64, max = 512)
    public static int signalDistance = 192;

    @Config.Comment("How far (blocks) luminaire halos and floodlight beams are drawn")
    @Config.RangeInt(min = 16, max = 512)
    public static int haloDistance = 160;

    @Config.Comment("Draw the soft light cone of yard floodlights at night")
    public static boolean floodBeams = true;

    // ---- area light (server) -----------------------------------------------------------------------------------------
    @Config.Comment({"Yard floodlights light the ground they point at with invisible light blocks (real block light, stops mob spawns).",
            "false = only the head itself gives light"})
    public static boolean floodHelpers = true;

    @Config.Comment("How far (blocks) a floodlight throws its light")
    @Config.RangeInt(min = 6, max = 40)
    public static int floodReach = 20;

    @Config.Comment("Light-block placements / removals done per world tick (light updates are expensive in 1.12; they are queued and batched)")
    @Config.RangeInt(min = 1, max = 64)
    public static int helperOpsPerTick = 6;

    // ---- failures ---------------------------------------------------------------------------------------------------------
    @Config.Comment("Luminaires age through the asset register: a lamp with a growing defect flickers, a failed one goes dark")
    public static boolean lampFailures = true;

    @Config.Comment("A flickering lamp also flickers its real block light (at most once per this many ticks). 0 = only the lens flickers")
    @Config.RangeInt(min = 0, max = 1200)
    public static int flickerLightTicks = 60;

    @Config.Comment("Emergency luminaires: hours of light from their battery after the supply is lost")
    @Config.RangeDouble(min = 0.1, max = 24)
    public static double emergencyHours = 3.0;

    // ---- trains (client, needs a dynamic-lights mod: Celeritas Dynamic Lights, or OptiFine for IR's own) -----------------
    @Config.Comment("Lit trains: headlight beams light the track ahead, powered or running passenger cars light the platform")
    public static boolean trainLights = true;

    @Config.Comment("Light sources along each lit headlight beam (0 = none)")
    @Config.RangeInt(min = 0, max = 6)
    public static int headlightBeams = 3;

    @Config.Comment("Most moving train light sources at once (nearest first) - each moving light rebuilds chunks, keep this modest")
    @Config.RangeInt(min = 0, max = 128)
    public static int maxTrainLights = 20;

    @Config.Comment("Light level of a lit passenger car's windows (0 = off)")
    @Config.RangeInt(min = 0, max = 15)
    public static int carLightLevel = 10;

    @Config.Comment("Trains further than this (blocks) cast no light")
    @Config.RangeInt(min = 16, max = 256)
    public static int trainLightRange = 96;

    public static double signalRenderSq() { return (double) signalDistance * signalDistance; }
}
