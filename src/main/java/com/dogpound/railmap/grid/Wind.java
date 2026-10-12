package com.dogpound.railmap.grid;

import net.minecraft.world.World;

/**
 * The world's wind (requested feature): a speed that drifts over the day with gusts, stronger in rain and
 * much stronger in a thunderstorm, and a direction that swings slowly round. Worked out the same way on the server and
 * the client from the world clock, so the rotors everyone sees turn with the power the grid gets. Speeds are at 10 m;
 * higher up it blows harder (the wind-shear power law, alpha 0.14).
 */
public final class Wind {
    private Wind() {}

    private static double wave(double t, double period, double phase) { return Math.sin(t / period * Math.PI * 2 + phase); }

    /** m/s at 10 m above the ground */
    public static double base(World w) {
        double t = w.getTotalWorldTime();
        double v = 6.5 + 2.6 * wave(t, 31000, 0.7) + 1.6 * wave(t, 7300, 2.1) + 0.9 * wave(t, 1900, 4.4);
        double gust = 0.8 * wave(t, 173, 1.3) + 0.5 * wave(t, 61, 0.2);
        if (w.isThundering()) v += 13;
        else if (w.isRaining()) v += 3.5;
        return Math.max(0, v + gust);
    }

    /** m/s at a hub `heightM` metres above the ground (1 block = 1 m) */
    public static double at(World w, double heightM) {
        return base(w) * Math.pow(Math.max(2, heightM) / 10.0, 0.14);
    }

    /** where the wind blows FROM, degrees (0 = from the south, 90 = from the west...; Minecraft yaw) */
    public static double from(World w) {
        double t = w.getTotalWorldTime();
        return (220 + 140 * wave(t, 52000, 0.3) + 25 * wave(t, 9100, 1.9) + 360) % 360;
    }

    /** a turbine's power curve: cut in 3 m/s, rated at 12 m/s, cut out (feather) above 25 m/s; fraction of rated */
    public static double curve(double v) {
        if (v < 3 || v > 25) return 0;
        if (v >= 12) return 1;
        double k = (v - 3) / 9.0;
        return k * k * k;
    }

    public static String compass(double fromDeg) {
        String[] n = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        return n[(int) Math.round(((fromDeg % 360) + 360) % 360 / 45.0) % 8];
    }
}
