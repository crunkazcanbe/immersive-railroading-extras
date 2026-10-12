package com.dogpound.railmap.stnlife6.client;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.stnlife6.ItemPubFood;
import com.dogpound.railmap.stnlife6.ItemRoomKey;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/** batch 6c item models: the room key card and the pub food (the vehicles reuse the road traffic renderer) */
@Mod.EventBusSubscriber(modid = RailMap.MODID, value = Side.CLIENT)
public final class LifeClient {
    private LifeClient() {}

    @SubscribeEvent
    public static void models(ModelRegistryEvent e) {
        if (ItemRoomKey.INSTANCE != null) ModelLoader.setCustomModelResourceLocation(ItemRoomKey.INSTANCE, 0, new ModelResourceLocation(RailMap.MODID + ":room_key", "inventory"));
        if (ItemPubFood.INSTANCE != null)
            for (int i = 0; i < ItemPubFood.NAMES.length; i++) ModelLoader.setCustomModelResourceLocation(ItemPubFood.INSTANCE, i, new ModelResourceLocation(RailMap.MODID + ":pub_" + ItemPubFood.NAMES[i], "inventory"));
    }
}
