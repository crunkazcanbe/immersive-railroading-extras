package com.dogpound.railmap.ride;

import com.dogpound.railmap.RailMap;
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

import java.util.ArrayList;
import java.util.List;

/** Registers the ropeway / funicular gear on its own (batch 5 #5): no shared proxy lists touched. */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class RideRegistry {
    private RideRegistry() {}

    public static final List<Block> BLOCKS = new ArrayList<>();
    public static Block station, funStation, machine, desk, tension, pylon, track;
    public static ItemRopeSpool spool;

    @SubscribeEvent
    public static void blocks(RegistryEvent.Register<Block> e) {
        RideNet.init();
        BLOCKS.add(station = new RideBlocks.Part("ropeway_station", TileRideStation::new, new double[]{0, 0, 0, 16, 3, 16},
                "Ropeway station: boarding platform, bullwheel (drawn turned to the rope), attendant pedestal with gates / ready / hold / E-stop",
                "A line runs between two stations: string it with the Haul Rope Spool. 12 V + 120 / 240 V AC (gates, lights)"));
        BLOCKS.add(funStation = new RideBlocks.Part("funicular_station", TileRideStation::new, new double[]{0, 0, 0, 16, 4, 16},
                "Funicular station: stepped platform, buffer stop, return sheave, platform gates, attendant pedestal",
                "Lay Funicular Track from the bottom station to the top one, then click both with the spool (FUNICULAR mode). 12 V + 120 / 240 V AC"));
        BLOCKS.add(machine = new RideBlocks.Wide("ropeway_machine", TileRideMachine::new,
                "Ropeway machine room: drive motor, service brake disc, planetary gearbox, drive bullwheel with emergency rim brakes, brake HPU, evacuation drive",
                "Three blocks wide. 480 V / 4.16 kV / 13.8 kV 3-phase grid cable touching it + 12 V. Place it near a station"));
        BLOCKS.add(desk = new RideBlocks.Wide("ropeway_desk", TileRideDesk::new,
                "Ropeway control desk: START / STOP / E-STOP / RESET, speed, wind limit, AUTO-MANUAL-EVAC, cabins, doors, fault annunciator, mimic screen",
                "Three blocks wide, 12 V. Place it near a station of the line"));
        BLOCKS.add(tension = new RideBlocks.Part("ropeway_tension", TileRideTension::new, null,
                "Ropeway tension weight: concrete counterweight on the tension carriage, position scale, LIMIT lamps",
                "Keeps the rope taut (less sag; without it the line is held to 2 m/s). 12 V for its monitor. Place near a station"));
        BLOCKS.add(pylon = new RideBlocks.Pylon());
        BLOCKS.add(track = new RideBlocks.Track());
        for (Block b : BLOCKS) e.getRegistry().register(b);
        reg(TileRideStation.class, "ropeway_station");
        reg(TileRideMachine.class, "ropeway_machine");
        reg(TileRideDesk.class, "ropeway_desk");
        reg(TileRideTension.class, "ropeway_tension");
    }

    private static void reg(Class<? extends net.minecraft.tileentity.TileEntity> c, String id) { GameRegistry.registerTileEntity(c, new ResourceLocation(RailMap.MODID, id)); }

    @SubscribeEvent
    public static void items(RegistryEvent.Register<Item> e) {
        for (Block b : BLOCKS) e.getRegistry().register(new ItemBlock(b).setRegistryName(b.getRegistryName()));
        e.getRegistry().register(spool = new ItemRopeSpool());
    }

    @SubscribeEvent
    public static void entities(RegistryEvent.Register<EntityEntry> e) {
        e.getRegistry().register(EntityEntryBuilder.create().entity(EntityRideCar.class).id(new ResourceLocation(RailMap.MODID, "ride_car"), 7787)
                .name(RailMap.MODID + ".ride_car").tracker(256, 1, false).build());
    }
}
