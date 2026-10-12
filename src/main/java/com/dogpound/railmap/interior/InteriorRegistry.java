package com.dogpound.railmap.interior;

import com.dogpound.railmap.RailMap;
import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Registers the interior items on their own (no shared proxy lists touched). */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class InteriorRegistry {
    private InteriorRegistry() {}

    public static ItemFittingKit kit;

    @SubscribeEvent
    public static void items(RegistryEvent.Register<Item> e) {
        e.getRegistry().register(kit = new ItemFittingKit());
    }
}
