package com.dogpound.railmap.freight;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.depot.DepotBlock;
import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.Arrays;
import java.util.List;

/** cargo care blocks (ChatGPT batch 3 #21-24); stn.StnBlocks registers them */
public final class CargoBlocks {
    private CargoBlocks() {}

    public static List<Block> create() {
        return Arrays.asList(
                new DepotBlock("reefer_plug", TileReeferPlug::new, new double[]{2, 0, 3, 14, 16, 13},
                        "Reefer Plug Post: four reefer container sockets + a trackside shore supply for reefer wagons",
                        "Containers go in the RACK; socket keys ON. CAR SUPPLY feeds the wagon standing within 8 blocks. SETPOINT + APPLY, PTI, FUEL, ACK. Mains + 12 V"),
                new DepotBlock("washout_plant", TileWashoutPlant::new, new double[]{0, 0, 0, 16, 16, 16},
                        "Wagon + Container Washout Plant: RINSE, DETERGENT or STEAM (certifies food-grade after hazardous loads)",
                        "Stop a wagon beside it or fill the BOX BAY, pick PROGRAM, WASH. Water main / bucket, slime-ball detergent, coal for the boiler, DRAIN the effluent. Mains + 12 V"),
                new DepotBlock("axle_weighbridge", TileAxleWeighbridge::new, new double[]{1, 0, 4, 15, 16, 14},
                        "Axle-Load Weighbridge: weighs every vehicle axle by axle and checks side-to-side and end-to-end balance",
                        "Place it beside the rail (within 2 blocks). Not the Track Scale (that weighs whole trains). LIMIT, MODE, CAL, HOLD key, PRINT ticket. 12 V"),
                new DepotBlock("container_tracker", TileContainerTracker::new, new double[]{3, 0, 4, 13, 16, 13},
                        "Container Tracking Terminal: any box's ISO number, check digit, status, seal, last place and holder, condition, trace",
                        "SCAN the box in the tray, FIND the one you hold, or PREV / NEXT through the yard by FILTER. PRINT the trace. 12 V"),
                new com.dogpound.railmap.depot.DepotDesk("yard_gantry", net.minecraft.block.material.Material.IRON, TileYardGantry::new,   // batch 5 #1
                        "Yard Gantry Crane (RMG): operator's cab desk; the gantry spans a truck lane, a rail lane and a 4 x 4 x 4 stack behind it",
                        "Joysticks (click / sneak-click), LOCK / UNLOCK, PLAN + AUTO job list, TRACKER, anti-sway, load cell, wind, CCTV spreader camera. 12 V + 480 V / 4.16 kV / 13.8 kV grid"));
    }

    public static void registerTiles() {
        reg(TileReeferPlug.class, "reefer_plug");
        reg(TileWashoutPlant.class, "washout_plant");
        reg(TileAxleWeighbridge.class, "axle_weighbridge");
        reg(TileContainerTracker.class, "container_tracker");
        reg(TileYardGantry.class, "yard_gantry");   // batch 5 #1
    }

    private static void reg(Class<? extends net.minecraft.tileentity.TileEntity> c, String id) {
        GameRegistry.registerTileEntity(c, new ResourceLocation(RailMap.MODID, id));
    }
}
