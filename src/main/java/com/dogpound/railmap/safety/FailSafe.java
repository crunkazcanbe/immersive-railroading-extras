package com.dogpound.railmap.safety;

import net.minecraft.world.World;

/**
 * Fail-safe mode configuration (her ChatGPT list #27): what each subsystem does when its power, control wiring,
 * communications or a sensor fails. Option 0 is always the safe state and the default; safety-critical subsystems only
 * offer safe-side choices (a failed track detector can never read "clear"). Every option here is enforced by the code
 * named in its comment - none of it is decoration. Set on the console's SAFETY / Fail-safe page or
 * {@code /irextras safety failsafe <name> <option>}.
 */
public enum FailSafe {
    /** TileSignalMast.shownAspect: what a signal with no 12 V shows */
    SIGNAL_POWER("Signal loses 12 V", true, "Dark - drivers treat a dark signal as STOP", "Battery: red lamp kept lit (STOP)"),
    /** SignalEngine.compute: a signal whose lamp has failed */
    LAMP_FAILURE("Signal lamp fails", true, "Lamp proving: the signal in rear is held at STOP", "Failed signal goes dark only"),
    /** TileTrackCircuit: no 12 V / dropped detector */
    TRACK_DETECTION("Track circuit fails", true, "Shows OCCUPIED"),
    /** TileAxleEvaluator: a head or the evaluator loses power or fails */
    AXLE_FAULT("Axle counter fails", true, "Section DISTURBED - supervised reset needed", "Section DISTURBED - auto prep-reset, next train sweeps it"),
    /** RouteCheck: points not detected in either position */
    POINT_DETECTION("Points lose detection", true, "Routes over them denied, signals stay at STOP"),
    /** RadioNet: a train inside radio territory loses its link */
    RADIO_LOSS("Train radio link lost", true, "Brake the train to a stand", "Hold the signal ahead at STOP", "Continue on lineside signals (driver warned)"),
    /** TileEmergencyPlunger: the plunger circuit loses 12 V */
    ESP_WIRING("Emergency plunger loses 12 V", true, "Treat as OPERATED - all stop in its zone", "Raise an alarm only"),
    /** SafetyHolds / RouteCheck: divided train, missing wagon, rear end not confirmed */
    INTEGRITY("Train integrity lost / unconfirmed", true, "Signals at STOP + routes behind it denied", "Raise an alarm only"),
    /** TileCrossing (existing behaviour): crossing controller loses 12 V */
    CROSSING_POWER("Level crossing loses 12 V", true, "Barriers down, lights flashing"),
    /** TileTrainDescriber: describer loses 12 V */
    DESCRIBER_POWER("Train describer loses 12 V", false, "Berths blank", "Battery: hold the last headcodes");

    public final String label;
    public final boolean critical;
    public final String[] options;

    FailSafe(String label, boolean critical, String... options) {
        this.label = label;
        this.critical = critical;
        this.options = options;
    }

    public static int mode(World w, FailSafe f) {
        int v = SafetyData.get(w).failsafe[f.ordinal()];
        return v >= 0 && v < f.options.length ? v : 0;
    }

    public static boolean safe(World w, FailSafe f) { return mode(w, f) == 0; }

    public static FailSafe byName(String s) {
        for (FailSafe f : values()) if (f.name().equalsIgnoreCase(s) || f.name().replace("_", "").equalsIgnoreCase(s)) return f;
        return null;
    }
}
