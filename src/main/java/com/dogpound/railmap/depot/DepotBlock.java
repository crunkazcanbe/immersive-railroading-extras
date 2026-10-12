package com.dogpound.railmap.depot;

import com.dogpound.railmap.block.IFurniture;
import com.dogpound.railmap.safety.SafetyBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.function.Supplier;

/**
 * A depot / station machine: faces the player who places it, the Signal Wrench links picked rails to its controls
 * (SafetyBlock), and whatever is in its storages drops when it is broken.
 */
public class DepotBlock extends SafetyBlock {
    public DepotBlock(String id, Supplier<TileEntity> tile, double[] box, String... tips) { super(id, tile, box, tips); }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof IFurniture f) f.dropAll();
        super.breakBlock(world, pos, state);
    }
}
