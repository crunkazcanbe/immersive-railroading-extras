package com.dogpound.railmap.steam;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;

/**
 * Coaling Tower bunker (batch 5 #2): the elevated reinforced-concrete coal bunker with its chute gate, tipping horns and
 * head sheave. Part of the coaling plant multiblock: set it 2-14 blocks straight above a Coaling Tower base (any
 * supports between, or none - the tower's legs and skip guides are drawn up to it), high enough for the chute to clear
 * your engines; the chute apron swings down on its south side. No electrics of its own - the base works it.
 */
public class BlockCoalBunker extends com.dogpound.railmap.block.BlockStationDevice {
    public BlockCoalBunker() {
        super("coal_bunker", Material.ROCK, "Coaling Tower Bunker: the elevated coal bunker with chute gate and head sheave",
                "Place it 2-14 blocks straight above a Coaling Tower base; its chute coals engines under its south side");
        setHardness(3.0f);
    }

    @Override public boolean isOpaqueCube(IBlockState s) { return false; }
    @Override public boolean isFullCube(IBlockState s) { return false; }
    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT; }
}
