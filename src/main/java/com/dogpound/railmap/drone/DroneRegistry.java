package com.dogpound.railmap.drone;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.safety.SafetyBlock;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;
import net.minecraftforge.fml.common.registry.GameRegistry;

/** Registers the drone gear on its own (her IR Extras list #2): the dock, the drone items and the drone entity. */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class DroneRegistry {
    private DroneRegistry() {}

    public static Block dock;
    public static DroneItems.Airframe airframe;
    public static DroneItems.Battery battery;
    public static DroneItems.Controller controller;

    @SubscribeEvent
    public static void blocks(RegistryEvent.Register<Block> e) {
        e.getRegistry().register(dock = new SafetyBlock("drone_dock", TileDroneDock::new, new double[]{0, 0, 0, 16, 11, 16},
                "Drone Dock: landing + charging pad, 6-pack battery bay, airframe hangar, wind / rain mast, mission panel",
                "Turn ARM, pick the mission and press LAUNCH; the drone streams to CCTV. 12 V + 120 / 240 V AC to charge"));
        GameRegistry.registerTileEntity(TileDroneDock.class, new ResourceLocation(RailMap.MODID, "drone_dock"));
    }

    @SubscribeEvent
    public static void items(RegistryEvent.Register<Item> e) {
        e.getRegistry().register(new ItemBlock(dock).setRegistryName(dock.getRegistryName()));
        e.getRegistry().registerAll(airframe = new DroneItems.Airframe(), battery = new DroneItems.Battery(), controller = new DroneItems.Controller());
    }

    @SubscribeEvent
    public static void entities(RegistryEvent.Register<EntityEntry> e) {
        e.getRegistry().register(EntityEntryBuilder.create().entity(EntityDrone.class).id(new ResourceLocation(RailMap.MODID, "inspection_drone"), 7743)
                .name(RailMap.MODID + ".inspection_drone").tracker(160, 2, true).build());
    }
}
