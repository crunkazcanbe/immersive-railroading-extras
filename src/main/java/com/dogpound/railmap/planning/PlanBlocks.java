package com.dogpound.railmap.planning;

import com.dogpound.railmap.RailMap;
import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.Collections;
import java.util.List;

/** Operations planning gear (her "next 48" list #1, 3, 6, 7, 40, 42, 46, 48); CommonProxy registers them. */
public final class PlanBlocks {
    private PlanBlocks() {}

    public static List<Block> create() { return Collections.singletonList(new BlockPlanningDesk()); }

    public static void registerTiles() { GameRegistry.registerTileEntity(TilePlanningDesk.class, new ResourceLocation(RailMap.MODID, "planning_desk")); }
}
