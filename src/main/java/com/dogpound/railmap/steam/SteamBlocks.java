package com.dogpound.railmap.steam;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.depot.DepotBlock;
import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.Arrays;
import java.util.List;

/** the steam servicing depot (batch 5 #2); CommonProxy registers them */
public final class SteamBlocks {
    private SteamBlocks() {}

    public static List<Block> create() {
        return Arrays.asList(
                new DepotBlock("coaling_tower", TileCoalingTower::new, new double[]{0, 0, 0, 16, 16, 16},
                        "Coaling Tower: concrete bunker, skip hoist and chute - coals tenders and tank engines standing under it",
                        "Multiblock: a Coal Receiving Pit beside it + the Bunker 2-14 blocks above it. HOIST / AUTO fill the bunker, CHUTE + DISCHARGE coal the engine. Mains + 12 V"),
                new BlockCoalBunker(),
                new DepotBlock("coal_pit", TileCoalPit::new, new double[]{0, 0, 0, 16, 8, 16},
                        "Coal Receiving Pit: grated ground hopper at the foot of a Coaling Tower (hoppers, pipes and wagons tip into it)",
                        "Place it beside the tower to form the coaling plant. 12 V for its level indicator"),
                new DepotBlock("water_column", TileWaterColumn::new, new double[]{3, 0, 3, 13, 16, 13},
                        "Water Column: swinging delivery arm and leather bag, 30 000 L tank, electric feed pump, phosphate dosing",
                        "Swing the ARM out over the tender, open the VALVE (works by hand). Pipe water in or run the PUMP beside water. Mains + 12 V"),
                new DepotBlock("ash_pit", TileAshPit::new, new double[]{0, 0, 0, 16, 10, 16},
                        "Ash Pit: fire cleaning and ash-pan dropping, quench sprays, grab hoist and ash wagon",
                        "DROP ASH from the engine standing at it, QUENCH, then HOIST the ash into the wagon. Mains + 12 V"),
                new DepotBlock("sand_tower", TileSandTower::new, new double[]{2, 0, 2, 14, 16, 14},
                        "Sand Drier and Tower: damp-sand bins, electric drier, air lift and elevated sand hopper with delivery hose",
                        "Drier ON (120 °C+), AIR LIFT key, then the SAND VALVE over the engine's sandboxes. Mains + 12 V"),
                new DepotBlock("boiler_washout", TileBoilerWashout::new, new double[]{0, 0, 2, 16, 16, 14},
                        "Boiler Washout Plant: hot-water washout pump, hot well, heater and the step-by-step washout sequence panel",
                        "BLOW DOWN, ISOLATE, PLUGS, WASH, INSPECT, PLUGS, TEST, CERTIFY. Clears boiler scale and leaking tubes. Mains + 12 V"));
    }

    public static void registerTiles() {
        reg(TileCoalingTower.class, "coaling_tower");
        reg(TileCoalPit.class, "coal_pit");
        reg(TileWaterColumn.class, "water_column");
        reg(TileAshPit.class, "ash_pit");
        reg(TileSandTower.class, "sand_tower");
        reg(TileBoilerWashout.class, "boiler_washout");
    }

    static void reg(Class<? extends net.minecraft.tileentity.TileEntity> c, String id) { GameRegistry.registerTileEntity(c, new ResourceLocation(RailMap.MODID, id)); }
}
