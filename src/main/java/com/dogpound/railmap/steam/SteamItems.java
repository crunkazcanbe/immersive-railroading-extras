package com.dogpound.railmap.steam;

import com.dogpound.railmap.RailMap;
import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Locomotive Ash (from the Ash Pit) - registered here, not in CommonProxy; the client model + renderers in SteamClient */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class SteamItems {
    private SteamItems() {}

    public static Item ash;

    @SubscribeEvent
    public static void items(RegistryEvent.Register<Item> e) {
        ash = new Item().setRegistryName(RailMap.MODID, "loco_ash").setTranslationKey(RailMap.MODID + ".loco_ash").setCreativeTab(com.dogpound.railmap.RailMapTab.INSTANCE);
        e.getRegistry().register(ash);
    }
}
