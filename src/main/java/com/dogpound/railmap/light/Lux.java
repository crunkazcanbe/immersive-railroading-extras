package com.dogpound.railmap.light;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;

/**
 * Illuminance on a photocell, in lux, from the sun's real elevation in the Minecraft sky: ~100 000 lx at noon, a few
 * hundred at sunset, tens in civil twilight, a fraction of a lux under the moon. Cloud (rain, storms) cuts it, and a
 * cell under a roof only sees the sky light that reaches it.
 */
public final class Lux {
    private Lux() {}

    /** lux at a photocell at p (it looks at the sky above p) */
    public static double at(World w, BlockPos p) {
        float sky = w.isBlockLoaded(p) ? w.getLightFor(EnumSkyBlock.SKY, p) / 15f : 1f;
        return model(w.getCelestialAngle(1f), w.getMoonPhase(), w.getRainStrength(1f), w.getThunderStrength(1f), sky, w.provider.hasSkyLight());
    }

    /**
     * celestialAngle: Minecraft's sun angle (0 = noon), moon phase 0..7 (0 full), rain / thunder 0..1, sky = sky light
     * reaching the cell 0..1. Pure maths, so the self-test can check it.
     */
    public static double model(float celestialAngle, int moonPhase, float rain, float thunder, float sky, boolean hasSky) {
        if (!hasSky) return 0.05;                                              // nether / end / caves of a skyless world
        double s = Math.cos(celestialAngle * Math.PI * 2);                  // sine of the sun's elevation
        double sun = s > 0 ? 110000 * Math.pow(s, 1.25) : 0;                // direct + sky light by day
        double twilight = 400 * Math.exp(Math.min(0.05, s) / 0.032);        // ~400 lx at sunset, ~1 lx at the end of civil twilight
        int ph = Math.floorMod(moonPhase, 8);
        double moon = 0.25 * (1 - Math.min(ph, 8 - ph) / 4.0);                // full moon 0.25 lx, new moon none
        double lx = sun + twilight + moon + 0.002;                           // starlight
        lx *= 1 - 0.75 * rain;                                               // overcast rain ~ a quarter
        lx *= 1 - 0.6 * thunder;                                             // thunderhead: much darker again
        return lx * sky * sky;
    }

    /** "1234 LX" for a display: whole lux, one decimal under 10 */
    public static String show(double lx) {
        if (lx >= 100000) return String.format(java.util.Locale.ROOT, "%.0fK LX", lx / 1000);
        if (lx >= 10) return String.format(java.util.Locale.ROOT, "%.0f LX", lx);
        return String.format(java.util.Locale.ROOT, "%.1f LX", lx);
    }
}
