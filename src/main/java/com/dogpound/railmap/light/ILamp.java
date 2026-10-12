package com.dogpound.railmap.light;

/**
 * Anything that is a luminaire (the IR Extras lighting blocks, the platform lamp column, tunnel lights): what the
 * contactor panel switches, what the asset register ages as a LUMINAIRE, and what the halo renderer draws.
 */
public interface ILamp {
    /** the lighting circuit it is wired on, 1..4 (the contactor panel's K1..K4); 0 = not switchable (tunnel lights) */
    default int lampCircuit() { return 1; }
    /** giving light now */
    boolean lampOn();
    /** failed (asset defect past critical): dark or flickering */
    default boolean lampFailed() { return false; }
    String lampName();

    /**
     * client: halo points in block-local px of the north-facing model (x, y, z, radius px, rgb), rotated by the
     * renderer to the block's facing; null = none
     */
    default double[][] haloPoints() { return null; }
    /** client: 0..1 brightness of the halo now (flicker, dim on battery) */
    default float haloLevel() { return lampOn() ? 1f : 0f; }
}
