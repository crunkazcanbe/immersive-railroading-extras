package com.dogpound.railmap.travel6;

import com.dogpound.railmap.RailMap;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** registers the travel item and the (itemless) barrier boom block on their own - no shared proxy lists touched */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class TravelRegistry {
    private TravelRegistry() {}

    public static ItemTravel item;
    public static BlockParkArm arm;

    @SubscribeEvent
    public static void blocks(RegistryEvent.Register<Block> e) { e.getRegistry().register(arm = new BlockParkArm()); }

    @SubscribeEvent
    public static void items(RegistryEvent.Register<Item> e) { e.getRegistry().register(item = new ItemTravel()); }
}
