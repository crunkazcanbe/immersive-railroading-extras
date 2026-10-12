package com.dogpound.railmap.steam.client;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.steam.SteamItems;
import com.dogpound.railmap.steam.TileCoalingTower;
import com.dogpound.railmap.steam.TileWaterColumn;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/** batch 5 #2 client side: the ash item's model and the moving parts (coaling skip + chute apron, water crane arm) */
@Mod.EventBusSubscriber(modid = RailMap.MODID, value = Side.CLIENT)
public final class SteamClient {
    private SteamClient() {}

    @SubscribeEvent
    public static void models(ModelRegistryEvent e) {
        if (SteamItems.ash != null) ModelLoader.setCustomModelResourceLocation(SteamItems.ash, 0, new ModelResourceLocation(RailMap.MODID + ":loco_ash", "inventory"));
        ClientRegistry.bindTileEntitySpecialRenderer(TileCoalingTower.class, new SteamRenderer.Tower());
        ClientRegistry.bindTileEntitySpecialRenderer(TileWaterColumn.class, new SteamRenderer.Column());
    }
}
