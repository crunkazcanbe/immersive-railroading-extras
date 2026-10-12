package com.dogpound.railmap.cine;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.block.BlockWideDesk;
import com.dogpound.railmap.safety.SafetyBlock;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

/** Registers the cinematic camera gear on its own (batch 5 #10): trackside TV camera tripod + director desk. */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class CineRegistry {
    private CineRegistry() {}

    public static Block tripod, desk;

    public static final class BlockDirectorDesk extends BlockWideDesk {
        public BlockDirectorDesk() {
            super("director_desk", Material.IRON, TileDirectorDesk::new,
                    "Director desk: vision mixer for train coverage - preview / program buses, tally lamps, CUT, shot list, ON AIR, live preview + program monitors",
                    "Three blocks wide. WATCH puts your screen on the program output. 12 V + 120 / 240 V AC");
        }
    }

    @SubscribeEvent
    public static void blocks(RegistryEvent.Register<Block> e) {
        e.getRegistry().register(tripod = new SafetyBlock("camera_tripod", TileCamTripod::new, new double[]{2, 0, 2, 14, 16, 14},
                "Trackside TV camera: broadcast camera on a tripod with a robotic pan / tilt head, zoom lens, viewfinder, tally lamps",
                "Set its CAM number (1-8) on the thumbwheel. Photo mode's TRACKSIDE view and the director desk cut to it as trains pass. 12 V"));
        e.getRegistry().register(desk = new BlockDirectorDesk());
        GameRegistry.registerTileEntity(TileCamTripod.class, new ResourceLocation(RailMap.MODID, "camera_tripod"));
        GameRegistry.registerTileEntity(TileDirectorDesk.class, new ResourceLocation(RailMap.MODID, "director_desk"));
    }

    @SubscribeEvent
    public static void items(RegistryEvent.Register<Item> e) {
        e.getRegistry().register(new ItemBlock(tripod).setRegistryName(tripod.getRegistryName()));
        e.getRegistry().register(new ItemBlock(desk).setRegistryName(desk.getRegistryName()));
    }
}
