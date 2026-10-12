package com.dogpound.railmap.light;

import com.dogpound.railmap.asset.AssetData;
import com.dogpound.railmap.asset.AssetKind;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Random;

/**
 * A luminaire's health from the asset register (asset/: it is a LUMINAIRE there, aged by its lamp hours). A latent
 * driver fault that has grown past half its critical size makes the lamp flicker, more and more as it grows; past
 * critical the lamp has failed: it flickers badly, and with the asset register's defectConsequences on it goes dark (the
 * odd dying flash) until the defect is repaired.
 */
public final class LampHealth {
    private LampHealth() {}

    /** 0 = healthy, 0.5..1 = flickering (worse as it grows), >= 1 = failed */
    public static double of(World w, BlockPos p) {
        if (!LightConfig.lampFailures || w == null || w.isRemote) return 0;
        try {
            AssetData d = AssetData.get(w);
            AssetData.Asset a = d.at(AssetKind.LUMINAIRE, p.toLong());
            if (a == null) return 0;
            double worst = 0;
            for (AssetData.Defect df : d.defectsOf(a.id))
                if (df.stage < AssetData.REPAIRED && df.crit > 0) worst = Math.max(worst, df.size / df.crit);
            if (a.cond < 15) worst = Math.max(worst, 0.6);                    // worn out: end-of-life flicker
            if (worst < 0.5) return 0;
            return worst;
        } catch (LinkageError | RuntimeException e) {
            return 0;
        }
    }

    /** is a lamp that should be on actually showing light this second */
    public static boolean visible(double health, boolean on, Random r) {
        if (!on) return false;
        if (health >= 1) return r.nextFloat() < (com.dogpound.railmap.asset.AssetConfig.defectConsequences ? 0.07f : 0.35f);   // failed: dying flashes (harsh) / bad flicker (default)
        if (health > 0) return r.nextFloat() >= 0.1f + 0.5f * (float) (health - 0.5) * 2;
        return true;
    }

    /** may the real block light change now (a flickering lamp only every flickerLightTicks; a steady change always) */
    public static boolean mayChange(long now, long last, boolean steadyChange) {
        if (steadyChange) return true;
        int t = LightConfig.flickerLightTicks;
        return t > 0 && now - last >= t;
    }
}
