package com.dogpound.railmap.stn;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.depot.DepotBlock;
import com.dogpound.railmap.depot.DepotDesk;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.Arrays;
import java.util.List;

/** station services blocks (ChatGPT batch 3 #26 #28 #30 #31 #34 #35) */
public final class StnServiceBlocks {
    private StnServiceBlocks() {}

    public static List<Block> create() {
        return Arrays.asList(
                new DepotBlock("formation_planner", TileFormationPlanner::new, new double[]{0, 0, 9, 16, 16, 16},
                        "Formation Planner: demand profile per line, recommended cars per train now / peak / off-peak",
                        "APPLY (or the AUTO key) splits surplus cars off at the line ends. 12 V + mains"),
                new DepotBlock("diagram_board", TileDiagramBoard::new, new double[]{0, 0, 9, 16, 16, 16},
                        "Service Diagram Board: every unit's daily workings, diagram hours, swaps",
                        "MARK + SWAP exchange two units' workings; ENFORCE sends a unit to depot outside its hours. 12 V + mains"),
                new DepotBlock("interchange_board", TileInterchangeBoard::new, new double[]{0, 0, 9, 16, 16, 16},
                        "Interchange Board: walking times between lines' platforms, live connections MAKE / TIGHT / MISS",
                        "HOLD keeps a train for a late connection; ANNOUNCE tells passengers where to change. 12 V + mains"),
                new DepotBlock("station_power", TileStationPower::new, new double[]{0, 0, 2, 16, 16, 16},
                        "Station Emergency Power: UPS battery, standby generator and transfer switch for the station",
                        "Feeds lamps / lifts / escalators by priority when the grid fails. Fuel in the day tank. 12 V + mains"),
                new DepotBlock("disruption_desk", TileDisruptionDesk::new, new double[]{0, 0, 3, 16, 16, 16},
                        "Disruption Control Desk: disrupted services per station and their travel alternatives",
                        "POST / SAY alternatives, ticket acceptance and replacement bus keys. 12 V + mains"),
                new DepotDesk("lost_property", Material.WOOD, TileLostProperty::new,
                        "Lost Property Office: found racks, unclaimed store, register, display shelf",
                        "SWEEP key: staff hand in what is left lying around. CLAIM returns yours. 12 V + mains"),
                new DepotDesk("parcels_office", Material.WOOD, TileParcelsOffice::new,
                        "Parcels Office: consign parcels to another station, they travel on driverless trains",
                        "Destination, CONSIGN, COLLECT; COUNTER key opens it. 12 V + mains"),
                new DepotDesk("sorting_office", Material.WOOD, com.dogpound.railmap.mail.TileSortingOffice::new,   // batch 5 #8 mail trains
                        "Sorting Office: post letters + parcels, sort them into the town pigeonholes, bag them for the mail trains",
                        "FRANK, SORT, BAG, OPEN BAGS, COLLECT, REGISTER (PO box), WRAP, PAPER; AUTO key. RealmCoin robots deliver to mailboxes. 12 V + mains"));
    }

    public static void registerTiles() {
        reg(TileFormationPlanner.class, "formation_planner");
        reg(TileDiagramBoard.class, "diagram_board");
        reg(TileInterchangeBoard.class, "interchange_board");
        reg(TileStationPower.class, "station_power");
        reg(TileDisruptionDesk.class, "disruption_desk");
        reg(TileLostProperty.class, "lost_property");
        reg(TileParcelsOffice.class, "parcels_office");
        reg(com.dogpound.railmap.mail.TileSortingOffice.class, "sorting_office");   // batch 5 #8
    }

    private static void reg(Class<? extends net.minecraft.tileentity.TileEntity> c, String id) {
        GameRegistry.registerTileEntity(c, new ResourceLocation(RailMap.MODID, id));
    }
}
