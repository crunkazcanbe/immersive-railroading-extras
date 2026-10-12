package com.dogpound.railmap.client;

import com.dogpound.railmap.network.PacketAppearance;

import java.util.HashMap;
import java.util.Map;

/** Client: the colour each vehicle is drawn with (sim.Appearance -> PacketAppearance), handed to the IR fork's PrideTint. */
public final class StockTint {
    private StockTint() {}

    private static final Map<Integer, float[]> TINT = new HashMap<>();
    private static long at;

    /** client init: hook into the fork's stock renderer (an older IR jar without PrideTint just gets no tint) */
    public static void init() {
        try {
            cam72cam.immersiverailroading.model.PrideTint.provider = s -> {
                if (System.currentTimeMillis() - at > 10_000) return null;
                return TINT.get(s.getId());
            };
        } catch (LinkageError ignored) {
        }
    }

    public static void receive(PacketAppearance p) {
        net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> accept(p));
    }

    static void accept(PacketAppearance p) {
        TINT.clear();
        for (int i = 0; i < p.ids.size(); i++) {
            int c = p.rgb.get(i);
            TINT.put(p.ids.get(i), new float[]{((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f});
        }
        at = System.currentTimeMillis();
    }
}
