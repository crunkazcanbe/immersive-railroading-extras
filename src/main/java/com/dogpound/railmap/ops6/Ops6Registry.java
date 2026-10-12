package com.dogpound.railmap.ops6;

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

import java.util.ArrayList;
import java.util.List;

/**
 * Batch 6 E (#21-25) registers itself (no shared proxy lists touched): the 250 t Breakdown Crane, the Flange Lubricator,
 * the Master Clock, the Driver Training Simulator desk and the RCO Beltpack - and its own network channel.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class Ops6Registry {
    private Ops6Registry() {}

    public static final List<Block> BLOCKS = new ArrayList<>();
    public static ItemBeltpack beltpack;

    @SubscribeEvent
    public static void blocks(RegistryEvent.Register<Block> e) {
        Ops6Net.init();
        BLOCKS.add(new SafetyBlock("rail_crane_250", TileRailCrane::new, null,
                "250 t Breakdown Crane: telescopic-jib rail crane on its carrier, 4 outriggers, slew / luff / hoist, load moment indicator",
                "Place it beside the line. 12 V + 480 V 3-phase. Outriggers OUT, TARGET a wrecked car, AIM, HOOK ON, hoist it up and set it down"));
        BLOCKS.add(new SafetyBlock("flange_lubricator", TileFlangeLube::new, new double[]{0, 0, 0, 16, 9, 16},
                "Flange Lubricator: trackside grease tank, electric pump, wheel detector and gauge-face wiping bars",
                "Place it beside a curve. 12 V + 24 V DC / 120-240 V AC. FILL with slime balls (grease). Cuts rail + flange wear, stops squeal"));
        BLOCKS.add(new SafetyBlock("master_clock", TileMasterClock::new, new double[]{2, 0, 3, 14, 16, 13},
                "Master Clock: GPS / radio-time master with crystal holdover, slave impulse driver and status panel",
                "12 V + 120-240 V AC. Syncs every station clock, arrivals board and PIS sign within range; clocks out of range drift"));
        BLOCKS.add(new BlockWideDesk("driver_sim", Material.IRON, TileDriverSim::new,
                "Driver Training Simulator: full cab desk, master controller, brake, AWS / DSD, big route screen and instructor panel",
                "Three blocks wide. 12 V + 120-240 V AC. Pick a scenario, START, SCREEN to sit at the desk and drive. Scores go on the driver record") {});
        for (Block b : BLOCKS) e.getRegistry().register(b);
        reg(TileRailCrane.class, "rail_crane_250");
        reg(TileFlangeLube.class, "flange_lubricator");
        reg(TileMasterClock.class, "master_clock");
        reg(TileDriverSim.class, "driver_sim");
    }

    private static void reg(Class<? extends net.minecraft.tileentity.TileEntity> c, String id) { GameRegistry.registerTileEntity(c, new ResourceLocation(RailMap.MODID, id)); }

    @SubscribeEvent
    public static void items(RegistryEvent.Register<Item> e) {
        for (Block b : BLOCKS) e.getRegistry().register(new ItemBlock(b).setRegistryName(b.getRegistryName()));
        e.getRegistry().register(beltpack = new ItemBeltpack());
    }

}
