package com.dogpound.railmap.depot;

import net.minecraft.world.World;

/** game time for the depot paperwork: day + hh:mm (a game hour is 1000 ticks) */
public final class DepotClock {
    private DepotClock() {}

    public static final double TICKS_PER_HOUR = 1000.0;

    public static int day(World w) { return (int) ((w.getWorldTime() + 6000) / 24000) + 1; }
    public static int hour(World w) { return (int) (((w.getWorldTime() + 6000) % 24000) / 1000); }
    public static int minute(World w) { return (int) (((w.getWorldTime() + 6000) % 1000) * 60 / 1000); }

    public static String hhmm(World w) { return String.format(java.util.Locale.ROOT, "%02d:%02d", hour(w), minute(w)); }
    public static String stamp(World w) { return "D" + day(w) + " " + hhmm(w); }

    /** game hours between two total-world-times */
    public static double hours(long from, long to) { return (to - from) / TICKS_PER_HOUR; }
}
