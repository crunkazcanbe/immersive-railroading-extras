package com.dogpound.railmap.industry;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.depot.DepotBlock;
import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.Collections;
import java.util.List;

/** the industry-integration blocks (her ChatGPT list section 13); CommonProxy registers them */
public final class IndustryBlocks {
    private IndustryBlocks() {}

    public static List<Block> create() {
        return Collections.singletonList(
                new DepotBlock("construction_site", TileConstructionSite::new, new double[]{0, 0, 0, 16, 16, 16},
                        "Construction Site Office: build a new mine, factory or power station from materials delivered by train",
                        "PREV / NEXT picks the project, the key authorises it (freight orders go out), BUILD once it is all here and a siding is laid. 12 V"));
    }

    public static void registerTiles() {
        GameRegistry.registerTileEntity(TileConstructionSite.class, new ResourceLocation(RailMap.MODID, "construction_site"));
    }
}
