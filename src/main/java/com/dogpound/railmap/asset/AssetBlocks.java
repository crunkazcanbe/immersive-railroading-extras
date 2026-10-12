package com.dogpound.railmap.asset;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.depot.DepotBlock;
import com.dogpound.railmap.depot.DepotDesk;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.Arrays;
import java.util.List;

/** assets + maintenance blocks (her 48-next list #10 #19 #24 #28 #45); CommonProxy registers them */
public final class AssetBlocks {
    private AssetBlocks() {}

    public static net.minecraft.item.Item ndt;

    public static List<Block> create() {
        return Arrays.asList(
                new DepotBlock("commissioning_set", TileCommissioning::new, new double[]{1, 0, 2, 15, 16, 14},
                        "Commissioning Test Set: scans the gear round it, proposes acceptance tests, runs them, signs assets off; secondary injection bench",
                        "SCAN, PLAN, RUN, SIGN OFF (AUTHORISED key), PRINT. Wrench an IED onto TARGET, INJECT. 12 V"),
                new DepotBlock("reman_bench", TileReman::new, new double[]{0, 0, 1, 16, 16, 15},
                        "Remanufacturing Bench: wash, strip and grade used parts (cores) into remanufactured spares, parts, metals and scrap",
                        "Cores come back from depot repairs. START / AUTO, WASH temperature, KITS. Mains + 12 V"),
                new DepotDesk("loco_workshop", Material.IRON, TileLocoWorkshop::new,
                        "Locomotive Design + Rebuild Workshop: traction, gearing, cooling, storage, bogies and brakes, with weight, power and cost",
                        "SELECT a loco within 16 blocks, set the six selectors, REBUILD, then ACCEPT for its test run. 12 V"));
    }

    public static void registerTiles() {
        GameRegistry.registerTileEntity(TileCommissioning.class, new ResourceLocation(RailMap.MODID, "commissioning_set"));
        GameRegistry.registerTileEntity(TileReman.class, new ResourceLocation(RailMap.MODID, "reman_bench"));
        GameRegistry.registerTileEntity(TileLocoWorkshop.class, new ResourceLocation(RailMap.MODID, "loco_workshop"));
    }

    public static net.minecraft.item.Item[] items() {
        ndt = new ItemNdt();
        return new net.minecraft.item.Item[]{ndt};
    }
}
