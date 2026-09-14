package com.dogpound.railmap.signal;

/**
 * American signal aspects, in the order a train meets them approaching an occupied block.
 * <p>
 * Each aspect says what the lamps do; how that is shown depends on the {@link SignalStyle}
 * (a colour-light paints three lamps, a position light paints rows of amber, a semaphore
 * swings a blade). {@link #lamps} is the canonical colour-light rendering, head by head:
 * index 0 = top head, 1 = middle, 2 = bottom.
 */
public enum Aspect {
    /** Green. Track is clear for at least three blocks. */
    CLEAR("Clear", new Lamp[]{ Lamp.GREEN, Lamp.RED, Lamp.RED }, 999),
    /** Flashing yellow. Two blocks clear — start thinking about slowing. */
    ADVANCE_APPROACH("Advance Approach", new Lamp[]{ Lamp.YELLOW_FLASH, Lamp.RED, Lamp.RED }, 999),
    /** Red over green. Diverging route ahead, take it at medium speed. */
    MEDIUM_CLEAR("Medium Clear", new Lamp[]{ Lamp.RED, Lamp.GREEN, Lamp.RED }, 45),
    /** Yellow over green. Approach the next signal at medium speed. */
    APPROACH_MEDIUM("Approach Medium", new Lamp[]{ Lamp.YELLOW, Lamp.GREEN, Lamp.RED }, 60),
    /** Red over flashing yellow. Diverging, next signal is restrictive. */
    MEDIUM_APPROACH("Medium Approach", new Lamp[]{ Lamp.RED, Lamp.YELLOW, Lamp.RED }, 45),
    /** Yellow. Next block is occupied — be ready to stop at the next signal. */
    APPROACH("Approach", new Lamp[]{ Lamp.YELLOW, Lamp.RED, Lamp.RED }, 60),
    /** Red over lunar. Proceed at restricted speed, prepared to stop short of anything. */
    RESTRICTING("Restricting", new Lamp[]{ Lamp.RED, Lamp.RED, Lamp.LUNAR }, 24),
    /** Red, permissive (number plate). Stop, then proceed at restricted speed. */
    STOP_AND_PROCEED("Stop and Proceed", new Lamp[]{ Lamp.RED, Lamp.RED, Lamp.RED }, 24),
    /** Red, absolute. Stop. Do not pass. */
    STOP("Stop", new Lamp[]{ Lamp.RED, Lamp.RED, Lamp.RED }, 0),
    /** No power / not yet computed. Every lamp dark. */
    DARK("Dark", new Lamp[]{ Lamp.OFF, Lamp.OFF, Lamp.OFF }, 0);

    /** A single lamp state. */
    public enum Lamp {
        OFF(0x14161A, false),
        RED(0xFF2E2E, false),
        YELLOW(0xFFC63B, false),
        YELLOW_FLASH(0xFFC63B, true),
        GREEN(0x3BFF6E, false),
        LUNAR(0xE8F0FF, false);

        public final int rgb;
        public final boolean flashing;

        Lamp(int rgb, boolean flashing) {
            this.rgb = rgb;
            this.flashing = flashing;
        }

        /** Lamp colour right now; a flashing lamp is dark for part of each second. */
        public int rgbAt(long ticks) {
            return flashing && (ticks % 20) < 8 ? OFF.rgb : rgb;
        }

        public boolean litAt(long ticks) {
            return this != OFF && !(flashing && (ticks % 20) < 8);
        }
    }

    public final String label;
    private final Lamp[] lamps;
    /** Speed this aspect authorises, km/h. 0 = stop, 999 = maximum authorised. */
    public final int speedKmh;

    Aspect(String label, Lamp[] lamps, int speedKmh) {
        this.label = label;
        this.lamps = lamps;
        this.speedKmh = speedKmh;
    }

    /** Lamp on {@code head} (0 = top). Heads a signal doesn't physically have are ignored. */
    public Lamp lamp(int head) {
        return head >= 0 && head < lamps.length ? lamps[head] : Lamp.OFF;
    }

    public boolean isStop() {
        return this == STOP || this == STOP_AND_PROCEED;
    }

    /** More restrictive of two aspects (later in the enum = more restrictive). */
    public Aspect worst(Aspect other) {
        if (other == null || other == DARK) return this;
        return ordinal() >= other.ordinal() ? this : other;
    }

    /** The aspect a signal shows when the block beyond it is clear for {@code n} blocks. */
    public static Aspect forClearBlocks(int n, boolean threeBlock) {
        if (n <= 0) return STOP;
        if (n == 1) return APPROACH;
        if (n == 2 && threeBlock) return ADVANCE_APPROACH;
        return CLEAR;
    }
}
