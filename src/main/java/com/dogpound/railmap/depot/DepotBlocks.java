package com.dogpound.railmap.depot;

import com.dogpound.railmap.RailMap;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.Arrays;
import java.util.List;

/** the depot / rolling-stock management blocks (her ChatGPT list section 8 + #83 + #84); CommonProxy registers them */
public final class DepotBlocks {
    private DepotBlocks() {}

    public static List<Block> create() {
        return Arrays.asList(
                new DepotDesk("depot_office", Material.WOOD, TileDepotOffice::new,
                        "Depot Control Office: job board, fleet, exam plans, routing, allocation, spares, diagnostics downloads",
                        "Wrench a rail + click ROAD for the stabling road. AUTO ROUTE / NIGHT STABLING / AUTO RETURN keys. 12 V"),
                new DepotDesk("depot_stores", Material.IRON, TileDepotStores::new,
                        "Depot Stores: six spare-part bins and a stock board with reorder lamps",
                        "Job cards within 32 blocks take their spares from here. 12 V for the stock board"),
                new DepotDesk("tool_crib", Material.IRON, TileToolCrib::new,
                        "Tool Crib: caged tool store with a pegboard, drawers, cabinet and a sign-out register",
                        "The cage key locks it to whoever placed it. 12 V for the register"),
                new DepotDesk("crew_desk", Material.WOOD, TileCrewDesk::new,
                        "Crew Booking-On Desk: driver roster, sign on / off, breaks, qualifications, handovers",
                        "ATTENDED lines need a qualified driver on duty to leave a station. 12 V"),
                new DepotBlock("wheel_lathe", TileWheelLathe::new, new double[]{0, 0, 0, 16, 14, 16},
                        "Underfloor Wheel Lathe: reprofiles a vehicle's wheels where it stands - clears flats and wear, costs diameter",
                        "Beside the track. CLAMP, MEASURE, coolant ON, CUT. Mains + 12 V"),
                new DepotBlock("bogie_drop", TileBogieDrop::new, new double[]{0, 0, 0, 16, 14, 16},
                        "Bogie Drop: drops a defective bogie (or its wheelsets) out from under a vehicle and fits a serviceable one",
                        "LOCK, LOWER, TRAVERSE, FIT, RAISE, UNLOCK. OVERHAUL rebuilds bogies in its bay. Mains + 12 V"),
                new DepotBlock("brake_test", TileBrakeTest::new, new double[]{1, 0, 3, 15, 16, 14},
                        "Brake Test Rig: charge, application, continuity, leak and parking-brake tests on a standing train",
                        "A pass certifies the brakes before RETURN TO SERVICE. 12 V"),
                new DepotBlock("fuel_point", TileFuelPoint::new, new double[]{1, 0, 2, 15, 16, 14},
                        "Fuelling and Sanding Point: 40 000 L tank, metered pump and nozzle, sand hopper and hose",
                        "Fill it by pipe or bucket; PUMP fuels the diesel standing at it, SAND fills its sandbox. Mains + 12 V"),
                new DepotBlock("train_wash", TileTrainWash::new, new double[]{0, 0, 0, 16, 16, 16},
                        "Train Wash Plant (half gantry): brushes, arches and a driver's signal - place one each side of the track",
                        "Run trains through at 1-8 km/h. Water main valve / bucket, detergent (slime balls). Mains + 12 V"),
                new DepotBlock("servicing_point", TileServicingPoint::new, new double[]{3, 0, 3, 13, 16, 13},
                        "Carriage Servicing Point: fresh water, toilet (CET) emptying, interior cleaning and catering restock",
                        "Services passenger stock standing within 12 blocks; AUTO during layovers. Water beside it. Mains + 12 V"));
    }

    public static void registerTiles() {
        reg(TileDepotOffice.class, "depot_office");
        reg(TileDepotStores.class, "depot_stores");
        reg(TileToolCrib.class, "tool_crib");
        reg(TileCrewDesk.class, "crew_desk");
        reg(TileWheelLathe.class, "wheel_lathe");
        reg(TileBogieDrop.class, "bogie_drop");
        reg(TileBrakeTest.class, "brake_test");
        reg(TileFuelPoint.class, "fuel_point");
        reg(TileTrainWash.class, "train_wash");
        reg(TileServicingPoint.class, "servicing_point");
        com.dogpound.railmap.station.StationFxBlocks.registerTiles();
    }

    static void reg(Class<? extends net.minecraft.tileentity.TileEntity> c, String id) {
        GameRegistry.registerTileEntity(c, new ResourceLocation(RailMap.MODID, id));
    }
}
