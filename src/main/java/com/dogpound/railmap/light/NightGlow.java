package com.dogpound.railmap.light;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.client.render.Prims;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * How dark it is where the player stands (0 = bright day in the open, 1 = night or underground), worked out once a
 * frame. Every lamp halo grows and strengthens with it (Prims.glowBoost / glowAlpha), so signals and panel lamps glow
 * at night and stay modest by day.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID, value = Side.CLIENT)
public final class NightGlow {
    private NightGlow() {}

    /** 0 day .. 1 night, this frame */
    public static float night;

    @SubscribeEvent
    public static void frame(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) { Prims.glowBoost = 1f; Prims.glowAlpha = 0.55f; return; }
        float sky = 1 - Math.max(0, Math.min(1, (mc.world.getSunBrightness(e.renderTickTime) - 0.2f) / 0.8f));
        Entity v = mc.getRenderViewEntity();
        float under = 0;
        if (v != null) {
            BlockPos p = new BlockPos(v.posX, v.posY + v.getEyeHeight(), v.posZ);
            if (mc.world.isBlockLoaded(p)) under = 1 - mc.world.getLightFor(EnumSkyBlock.SKY, p) / 15f;
        }
        night = Math.max(sky, under);
        Prims.glowBoost = (float) (LightConfig.haloScale * (1 + 0.6 * night));
        Prims.glowAlpha = 0.45f + 0.3f * night;
    }

    /** halo multiplier for a signal drawn at (x, y, z) from the camera: grows with distance so it stays visible */
    public static float signalBoost(double x, double y, double z) {
        double d = Math.sqrt(x * x + y * y + z * z);
        return (float) (Prims.glowBoost * Math.max(1, Math.min(4.5, d / 36.0)) * (1 + 0.4 * night));
    }
}
