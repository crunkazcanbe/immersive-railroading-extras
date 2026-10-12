package com.dogpound.railmap.incident;

import com.dogpound.railmap.RailMap;
import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.Arrays;
import java.util.List;

/** Emergency response + control-room gear (her ChatGPT list #67-70, #74-80); CommonProxy registers them. */
public final class IncidentBlocks {
    private IncidentBlocks() {}

    public static List<Block> create() {
        return Arrays.asList(
                new BlockIncidentDesk(),
                new BlockIncident("breakdown_depot", TileBreakdownDepot::new, new double[]{0, 0, 1, 16, 16, 16},
                        "Breakdown Crew Depot: the rerailing gang on call; turns out to wrecks, its kit lockers set how fast they work",
                        "Lockers: pistons = jacks (2), planks / logs = packing (16), leads = slings (2). 12 V"),
                new BlockIncident("fire_panel", TileFirePanel::new, new double[]{1, 0, 9, 15, 16, 16},
                        "Fire alarm + water-mist suppression panel: detects fire / overheating in its zone, alarms, isolates power, discharges",
                        "Refill the agent with water buckets. Reads dangerous-goods placards in its zone. 12 V"),
                new BlockIncident("evac_sign", TileEvacSign::new, new double[]{1, 6, 12, 15, 16, 16},
                        "Emergency exit sign with battery emergency lighting: lights on mains failure and during evacuations",
                        "ARROW switch; TEST = 30 s functional test on battery"),
                new BlockIncident("assembly_point", TileAssemblyPoint::new, new double[]{5, 0, 5, 11, 16, 11},
                        "Emergency assembly point sign + warden's roll-call box: counts people here during an evacuation",
                        "ROLL CALL logs names, ALL IN tells control everyone is accounted for. 12 V for the box"),
                new BlockIncident("maint_terminal", TileMaintTerminal::new, new double[]{0, 0, 1, 16, 15, 15},
                        "Remote maintenance terminal: every machine on the control bus, readings, ACK, remote test / operate, work orders",
                        "Safety-critical operations are refused remotely: LOCAL ACTION REQUIRED. 12 V"),
                new BlockIncident("announce_unit", TileAnnounceUnit::new, new double[]{1, 0, 3, 15, 15, 15},
                        "Announcement control unit: delays, platform changes, cancellations, connections, emergencies from live data",
                        "AUTO key announces by itself on the station's PA speakers. 12 V"));
    }

    public static void registerTiles() {
        reg(TileIncidentDesk.class, "incident_desk");
        reg(TileBreakdownDepot.class, "breakdown_depot");
        reg(TileFirePanel.class, "fire_panel");
        reg(TileEvacSign.class, "evac_sign");
        reg(TileAssemblyPoint.class, "assembly_point");
        reg(TileMaintTerminal.class, "maint_terminal");
        reg(TileAnnounceUnit.class, "announce_unit");
    }

    private static void reg(Class<? extends net.minecraft.tileentity.TileEntity> c, String id) {
        GameRegistry.registerTileEntity(c, new ResourceLocation(RailMap.MODID, id));
    }
}
