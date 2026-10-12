package com.dogpound.railmap.build6a;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.block.BlockStationDevice;
import com.dogpound.railmap.depot.DepotBlock;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.Arrays;
import java.util.List;

/** batch 6a construction + works machines; CommonProxy registers the blocks / tiles, this class the truck item + entity */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class B6Blocks {
    private B6Blocks() {}

    public static Block bay;
    public static Item hiRail;

    /** an Erecting Bay: part of the Locomotive Works multiblock (no tile - the works desk counts them) */
    public static final class BlockBay extends BlockStationDevice {
        public BlockBay() {
            super("loco_works_bay", Material.IRON, "Erecting Bay: pit road, frame jacks, wheeling gantry and a 50 t overhead crane",
                    "Place in a row either side of the Locomotive Works desk (up to 2 each side): one vehicle builds in each bay");
            setHardness(3f);
        }
    }

    public static List<Block> create() {
        bay = new BlockBay();
        return Arrays.asList(
                new DepotBlock("track_layer", TileTrackLayer::new, new double[]{0, 0, 0, 16, 16, 16},
                        "Track Laying Machine: rail threader, sleeper conveyor, clip gun and ballast hopper on a diesel-hydraulic power car",
                        "Stand on the track end looking along it and place it on the formation. Battery ON, engine key RUN, load rails + sleepers, LAY (or AUTO). Lays real IR track and drives onto it"),
                new DepotBlock("tbm", TileTbm::new, new double[]{0, 0, 0, 16, 16, 16},
                        "Tunnel Boring Machine: cutterhead, thrust rams, segment erector, muck conveyor; 4.16-13.8 kV trailing cable",
                        "Place it at the launch face facing you. MV cable to it, main key CLOSED, segments in, BORE. It lines each ring and shoves itself forward"),
                new DepotBlock("ballast_tamper", TileTamper::new, new double[]{0, 0, 0, 16, 16, 16},
                        "Ballast Tamper: 09-3X tamping-lining unit - lifts, lines and tamps the track either side of it",
                        "Set it down on the track (on top of the rail), facing you along the line. Engine RUN, MEASURE, heads DOWN, START: before / after TQI on the screen. Gravel in the hopper"),
                new DepotBlock("loco_works", TileLocoWorks::new, new double[]{0, 0, 0, 16, 16, 16},
                        "Locomotive Works: erecting-shop manager's desk - builds Immersive Railroading rolling stock from parts",
                        "Erecting Bays in a row beside it. Pick stock, ADD, SHOP key ON, START. Parts in the stores, stock out of the despatch bay. 208-480 V + 12 V"),
                bay);
    }

    public static void registerTiles() {
        reg(TileTrackLayer.class, "track_layer");
        reg(TileTbm.class, "tbm");
        reg(TileTamper.class, "ballast_tamper");
        reg(TileLocoWorks.class, "loco_works");
    }

    static void reg(Class<? extends net.minecraft.tileentity.TileEntity> c, String id) { GameRegistry.registerTileEntity(c, new ResourceLocation(RailMap.MODID, id)); }

    @SubscribeEvent
    public static void items(RegistryEvent.Register<Item> e) {
        hiRail = new ItemHiRail();
        e.getRegistry().register(hiRail);
    }

    @SubscribeEvent
    public static void entities(RegistryEvent.Register<EntityEntry> e) {
        e.getRegistry().register(EntityEntryBuilder.create().entity(EntityHiRail.class).id(new ResourceLocation(RailMap.MODID, "hirail_truck"), 7791)
                .name(RailMap.MODID + ".hirail_truck").tracker(160, 1, true).build());
    }
}
