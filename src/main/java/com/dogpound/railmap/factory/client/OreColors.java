package com.dogpound.railmap.factory.client;

import com.dogpound.railmap.factory.ore.OreRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/** Each ore's processed forms take the average colour of that ore's own ingot texture (worked out once, then cached). */
public final class OreColors {
    private OreColors() {}
    private static final Map<Integer, Integer> CACHE = new HashMap<>();

    public static int color(ItemStack s, int tint) {
        if (tint != 0) return 0xFFFFFF;
        return CACHE.computeIfAbsent(s.getMetadata(), OreColors::compute);
    }

    /** colour by ore name (for the fluids) */
    public static int colorOf(String ore) {
        int m = OreRegistry.meta(ore);
        return m < 0 ? 0xC8C8C8 : CACHE.computeIfAbsent(m, OreColors::compute);
    }

    private static int compute(int meta) {
        try {
            ItemStack ingot = OreRegistry.ingot(OreRegistry.name(meta), 1);
            if (ingot.isEmpty()) return 0xB0B0B0;
            TextureAtlasSprite sp = Minecraft.getMinecraft().getRenderItem().getItemModelWithOverrides(ingot, null, null).getParticleTexture();
            if (sp == null || sp.getFrameCount() == 0) return 0xB0B0B0;
            int[] px = sp.getFrameTextureData(0)[0];
            long r = 0, g = 0, b = 0, n = 0;
            for (int c : px) {
                if ((c >>> 24) < 128) continue;
                r += c >> 16 & 255; g += c >> 8 & 255; b += c & 255; n++;
            }
            if (n == 0) return 0xB0B0B0;
            int rr = (int) (r / n), gg = (int) (g / n), bb = (int) (b / n);
            // lift dark ingots so the ore texture's shading still shows
            int max = Math.max(rr, Math.max(gg, bb));
            if (max < 160 && max > 0) { float k = 160f / max; rr = Math.min(255, (int) (rr * k)); gg = Math.min(255, (int) (gg * k)); bb = Math.min(255, (int) (bb * k)); }
            return rr << 16 | gg << 8 | bb;
        } catch (Throwable t) {
            return 0xB0B0B0;
        }
    }
}
