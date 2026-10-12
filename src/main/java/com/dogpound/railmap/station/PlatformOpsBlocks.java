package com.dogpound.railmap.station;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.depot.DepotBlock;
import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.Arrays;
import java.util.List;

/** platform operations (ChatGPT batch 3 #27 #29 #32 #33); stn.StnBlocks lists them */
public final class PlatformOpsBlocks {
    private PlatformOpsBlocks() {}

    public static List<Block> create() {
        return Arrays.asList(
                new DepotBlock("crowd_monitor", TileCrowdMonitor::new, new double[]{0, 0, 9, 16, 16, 16},
                        "Platform Crowd Monitor: people counting, density, Fruin level of service A-F, gate + ticket machine queues",
                        "METERING, CLOSE ENTRANCE and DWELL really act; overcrowding alarms. 12 V"),
                new DepotBlock("zone_marker", TileZoneMarker::new, new double[]{6, 0, 6, 10, 16, 10},
                        "Platform Zone Marker: lettered A-H coach zone sign - one every coach length along the platform",
                        "Trains due are announced by zone; ACCESS key = accessible boarding point, STOP key = the stopping end. 12 V"),
                new DepotBlock("door_controller", TileDoorController::new, new double[]{3, 0, 3, 13, 16, 13},
                        "Platform Door Safety Controller: platform length limits, selective door opening, overrun lock-out",
                        "Wrench the two platform-end rails onto END A / END B. Governs the Station Door Controllers near it. 12 V"),
                new DepotBlock("station_security", TileStationSecurity::new, new double[]{0, 0, 9, 16, 16, 16},
                        "Station Security + Opening Hours Panel: opening hours, night mode, gates lock, intruder alarm",
                        "Closed: gates exit-only, no new passengers, lamps to security lighting, PIS + PA notices. 12 V"));
    }

    public static void registerTiles() {
        reg(TileCrowdMonitor.class, "crowd_monitor");
        reg(TileZoneMarker.class, "zone_marker");
        reg(TileDoorController.class, "door_controller");
        reg(TileStationSecurity.class, "station_security");
    }

    private static void reg(Class<? extends net.minecraft.tileentity.TileEntity> c, String id) {
        GameRegistry.registerTileEntity(c, new ResourceLocation(RailMap.MODID, id));
    }
}
