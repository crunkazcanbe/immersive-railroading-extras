package com.dogpound.railmap.station;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.depot.DepotBlock;
import com.dogpound.railmap.depot.DepotDesk;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.Arrays;
import java.util.List;

/** station accessibility, platform allocation and passenger facilities (her ChatGPT list #81, #82, #85) */
public final class StationFxBlocks {
    private StationFxBlocks() {}

    public static List<Block> create() {
        return Arrays.asList(
                new DepotBlock("platform_panel", TilePlatformPanel::new, new double[]{0, 0, 9, 16, 16, 16},
                        "Platform Allocation Panel: gives arriving driverless trains the best of up to six platforms",
                        "Wrench a rail, click a row to link it. Length, step-free, direction, conflict, CLOSE. 12 V"),
                new DepotBlock("station_lift", TileStationLift::new, new double[]{0, 0, 8, 16, 16, 16},
                        "Station Lift landing: one per level in the same column - the car carries you between them",
                        "CALL and floor buttons. Out of service = no step-free access. Mains + 12 V"),
                new DepotBlock("help_point", TileHelpPoint::new, new double[]{5, 0, 5, 11, 16, 11},
                        "Help Point: HELP calls staff, INFO tells you the next train, MIND THE GAP warning while a train stands",
                        "Counts for the station's accessibility rating. 12 V"),
                new BlockTactile("tactile_blister", "Tactile paving, blister (crossings and the top of steps)"),
                new BlockTactile("tactile_edge", "Tactile paving, platform edge (offset blister, safety yellow)"),
                new DepotBlock("station_toilet", TileStationToilet::new, new double[]{0, 0, 2, 16, 16, 16},
                        "Accessible Toilet: wide cubicle, grab rails, emergency cord, cleaning log",
                        "Closed for cleaning under 20 % clean - CLEANED logs it. 12 V"),
                new DepotBlock("vending_machine", TileVending::new, new double[]{1, 0, 4, 15, 16, 15},
                        "Vending Machine: sells whatever you stock it with, to you (coins) and to the station's passengers",
                        "Takings go to the station's operators daily. 12 V"),
                new DepotDesk("retail_kiosk", Material.WOOD, TileKiosk::new,
                        "Retail Kiosk: a concession counter - display shelf, stock cupboards, shutter",
                        "Open and stocked it pays its rent to the station's operators. 12 V"),
                new BlockPlatformLamp(),
                new DepotBlock("platform_heater", TilePlatformHeater::new, new double[]{2, 6, 8, 14, 16, 16},
                        "Waiting Shelter Heater: overhead radiant heater with a thermostat",
                        "AUTO heats when it is cold. Mains + 12 V"),
                new BlockEscalator(),
                new DepotBlock("facilities_panel", TileFacilitiesPanel::new, new double[]{0, 0, 10, 16, 16, 16},
                        "Station Facilities Panel: accessibility rating, facilities in service, running costs and income",
                        "LEVEL ACCESS key: a station with no steps. 12 V"));
    }

    public static void registerTiles() {
        reg(TilePlatformPanel.class, "platform_panel");
        reg(TileStationLift.class, "station_lift");
        reg(TileHelpPoint.class, "help_point");
        reg(TileStationToilet.class, "station_toilet");
        reg(TileVending.class, "vending_machine");
        reg(TileKiosk.class, "retail_kiosk");
        reg(TilePlatformLamp.class, "platform_lamp");
        reg(TilePlatformHeater.class, "platform_heater");
        reg(TileEscalator.class, "escalator");
        reg(TileFacilitiesPanel.class, "facilities_panel");
    }

    private static void reg(Class<? extends net.minecraft.tileentity.TileEntity> c, String id) {
        GameRegistry.registerTileEntity(c, new ResourceLocation(RailMap.MODID, id));
    }
}
