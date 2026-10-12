package com.dogpound.railmap.newrail6;

import com.dogpound.railmap.RailMap;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.ArrayList;
import java.util.List;

/** Registers batch 6 #16-20 on its own (rack, ferry, motorail, garden railway, avalanche): no shared proxy lists touched. */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class NR6Registry {
    private NR6Registry() {}

    public static final List<Block> BLOCKS = new ArrayList<>();
    public static Block rackRail, rackEntry, rackPanel, ferryDock, motorailRamp, gardenTrack, gardenStation, gallery, avalancheDetector;
    public static ItemGardenTrain gardenSet;

    @SubscribeEvent
    public static void blocks(RegistryEvent.Register<Block> e) {
        NR6Net.init();
        BLOCKS.add(rackRail = new NR6Blocks.Piece("rack_rail", Material.IRON, Block.FULL_BLOCK_AABB,
                "Rack rail (Abt system): two staggered toothed bars on chairs - lay it in the ground UNDER the track",
                "A cog locomotive on rack gets full grip: no slip on steep grades, its brakes hold. Facing = along the track"));
        BLOCKS.add(rackEntry = new NR6Blocks.Piece("rack_entry", Material.IRON, Block.FULL_BLOCK_AABB,
                "Rack entry section: sprung, tapered rack where the pinion meshes - the first length of every rack",
                "Run on slowly (the Rack Panel's ENTRY speed); running on anywhere else strikes the tooth tips"));
        BLOCKS.add(rackPanel = new NR6Blocks.Part("rack_panel", TileRackPanel::new, new double[]{3, 0, 4, 13, 16, 12},
                "Rack section supervision panel: speed / entry limits, overspeed brake, lubricator, cog loco tagging, E-stop",
                "Supervises rack within 128 blocks. 12 V + 120 / 240 V AC (lubricator, heaters)"));
        BLOCKS.add(ferryDock = new NR6Blocks.Part("ferry_dock", TileFerryDock::new, new double[]{0, 0, 0, 16, 16, 16},
                "Train ferry dock: link span control cabin + hydraulic power pack, at the water's edge beside the dock track",
                "Two docks on the same ROUTE pair up. 12 V + 480 / 208 V 3-phase (span hydraulics)"));
        BLOCKS.add(motorailRamp = new NR6Blocks.Part("motorail_ramp", TileMotorailRamp::new, new double[]{0, 0, 0, 16, 10, 16},
                "Motorail end-loading ramp: levelling deck, bridge plate, wheel chocks, control pedestal",
                "Roadway in front (Immersive Vehicles cars), track end behind. 12 V + 240 / 480 V AC (hydraulics)"));
        BLOCKS.add(gardenTrack = new NR6Blocks.GardenTrack());
        BLOCKS.add(gardenStation = new NR6Blocks.Part("garden_station", TileGardenStation::new, new double[]{0, 0, 0, 16, 6, 16},
                "Garden railway station: platform, station master's pedestal, starter signal, bell",
                "Place beside Garden Track - trains stop at it while the starter is red. 12 V + 120 / 240 V AC (lights, bell)"));
        BLOCKS.add(gallery = new NR6Blocks.Piece("avalanche_gallery", Material.ROCK, Block.FULL_BLOCK_AABB,
                "Avalanche gallery (snow shed): reinforced concrete wall and roof slab - place beside the track, roof toward it",
                "Track under the roof (1-2 blocks out) is safe from avalanches and rockfall. A row of them makes the gallery"));
        BLOCKS.add(avalancheDetector = new NR6Blocks.Part("avalanche_detector", TileAvalancheDetector::new, new double[]{4, 0, 4, 12, 16, 12},
                "Avalanche / landslide detector: snow depth sensor, radar, geophone, slide fence, gas exploder control",
                "Watches the line within its range; ARMED it holds signals at danger. 12 V + 120 / 240 V AC (sensor heaters)"));
        for (Block b : BLOCKS) e.getRegistry().register(b);
        reg(TileRackPanel.class, "rack_panel");
        reg(TileFerryDock.class, "ferry_dock");
        reg(TileMotorailRamp.class, "motorail_ramp");
        reg(TileGardenStation.class, "garden_station");
        reg(TileAvalancheDetector.class, "avalanche_detector");
    }

    private static void reg(Class<? extends net.minecraft.tileentity.TileEntity> c, String id) { GameRegistry.registerTileEntity(c, new ResourceLocation(RailMap.MODID, id)); }

    @SubscribeEvent
    public static void items(RegistryEvent.Register<Item> e) {
        for (Block b : BLOCKS) e.getRegistry().register(new ItemBlock(b).setRegistryName(b.getRegistryName()));
        e.getRegistry().register(gardenSet = new ItemGardenTrain());
    }

    @SubscribeEvent
    public static void entities(RegistryEvent.Register<EntityEntry> e) {
        e.getRegistry().register(EntityEntryBuilder.create().entity(EntityTrainFerry.class).id(new ResourceLocation(RailMap.MODID, "train_ferry"), 7961)
                .name(RailMap.MODID + ".train_ferry").tracker(384, 1, true).build());
        e.getRegistry().register(EntityEntryBuilder.create().entity(EntityGardenTrain.class).id(new ResourceLocation(RailMap.MODID, "garden_train"), 7962)
                .name(RailMap.MODID + ".garden_train").tracker(128, 1, true).build());
    }
}
