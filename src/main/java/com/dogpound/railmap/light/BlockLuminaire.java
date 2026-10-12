package com.dogpound.railmap.light;

import com.dogpound.railmap.depot.DepotBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.function.Supplier;

/** a luminaire block: gives the block light its tile says (real light), and takes a floodlight's ground light with it when broken */
public class BlockLuminaire extends DepotBlock {
    public BlockLuminaire(String id, Supplier<TileEntity> tile, double[] box, String... tips) {
        super(id, tile, box, tips);
        setLightOpacity(0);
    }

    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        return world.getTileEntity(pos) instanceof TileLuminaire t ? Math.max(0, Math.min(15, t.level())) : 0;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof TileLuminaire t) t.removed();
        super.breakBlock(world, pos, state);
    }
}
