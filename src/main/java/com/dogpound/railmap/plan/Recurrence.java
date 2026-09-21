package com.dogpound.railmap.plan;

/**
 * When does a repeating service next leave?
 *
 * <p>Deliberately its own class with NO Minecraft imports. The arithmetic is small but it is
 * exactly the kind that goes wrong quietly — an off-by-one here books a train into the past,
 * or makes an every-20-minutes service skip an hour — and it cannot be unit-checked while it
 * lives inside a class that imports NBT. {@link Timetable.Service} delegates here.
 */
public final class Recurrence {

    /** 20 ticks a second, 1200 a minute. Every time in the planner is in ticks.
     *  These live here rather than in Timetable so the pure logic stays free of
     *  Minecraft imports and can be checked on a bare JDK. */
    public static final int TICKS_PER_SECOND = 20;
    public static final int TICKS_PER_MINUTE = 20 * 60;

    private Recurrence() {}

    /**
     * The first departure at or after {@code now}.
     *
     * @param first    absolute tick of the first departure
     * @param headway  repeat interval in ticks; 0 or less means it runs once
     * @param enabled  a service kept on the books but not running
     * @return the departure tick, or -1 if it will never depart again
     */
    public static long next(long first, int headway, boolean enabled, long now) {
        if (!enabled) return -1L;
        if (now <= first) return first;
        if (headway <= 0) return -1L;          // one-off, already gone
        long elapsed = now - first;
        long missed = (elapsed + headway - 1) / headway;   // ceil division
        return first + missed * headway;
    }
}
