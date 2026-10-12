package com.dogpound.railmap.client;

import com.dogpound.railmap.RailMap;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/** Block-atlas sprites only code draws (no model names them since the bbkit models), so the atlas must be told. */
@Mod.EventBusSubscriber(modid = RailMap.MODID, value = Side.CLIENT)
public final class ExtraSprites {
    private ExtraSprites() {}

    @SubscribeEvent
    public static void stitch(TextureStitchEvent.Pre e) {
        e.getMap().registerSprite(new ResourceLocation(RailMap.MODID, "blocks/grid_solar_cells"));   // SolarDeck
    }
}
