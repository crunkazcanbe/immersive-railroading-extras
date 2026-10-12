package com.dogpound.railmap.travel6.client;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.travel6.ItemTravel;
import com.dogpound.railmap.travel6.TileBagHall;
import com.dogpound.railmap.travel6.TravelRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/** travel item models, the phone app and the baggage carousel renderer (batch 6b #6-10) */
@Mod.EventBusSubscriber(modid = RailMap.MODID, value = Side.CLIENT)
public final class TravelClient {
    private TravelClient() {}

    @SubscribeEvent
    public static void models(ModelRegistryEvent e) {
        if (TravelRegistry.item != null)
            for (int i = 0; i < ItemTravel.VARIANTS.length; i++)
                ModelLoader.setCustomModelResourceLocation(TravelRegistry.item, i, new ModelResourceLocation(RailMap.MODID + ":" + ItemTravel.VARIANTS[i], "inventory"));
        ClientRegistry.bindTileEntitySpecialRenderer(TileBagHall.class, new CarouselRenderer());
    }

    @SubscribeEvent
    public static void use(PlayerInteractEvent.RightClickItem e) {
        if (!e.getWorld().isRemote || e.getEntityPlayer() != Minecraft.getMinecraft().player) return;
        if (ItemTravel.is(e.getItemStack(), ItemTravel.PHONE)) Minecraft.getMinecraft().displayGuiScreen(new GuiPhone());
    }
}
