package com.dogpound.railmap.travel6;

import com.dogpound.railmap.depot.DepotBlock;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/** Car park barrier (batch 6b #10): boom down / up (blockstate open), the lowered boom blocks its own cell too. */
public class BlockParkBarrier extends DepotBlock {
    public static final PropertyBool OPEN = PropertyBool.create("open");

    public BlockParkBarrier() {
        super("park_barrier", TileParkBarrier::new, new double[]{11, 0, 4, 16, 16, 12},
                "Car park barrier: ENTRY issues parking tickets, EXIT takes paid ones (or a Pride Card)",
                "Drive up riding anything; MODE key ENTRY / EXIT; the boom reaches over the block to its right. 12 V (fails up)");
    }

    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING, OPEN); }

    @Override
    @SuppressWarnings("deprecation")
    public IBlockState getActualState(IBlockState s, IBlockAccess w, BlockPos p) {
        return s.withProperty(OPEN, w.getTileEntity(p) instanceof TileParkBarrier b && b.isOpen());
    }

    @Override
    @SuppressWarnings("deprecation")
    public AxisAlignedBB getCollisionBoundingBox(IBlockState s, IBlockAccess w, BlockPos p) {
        boolean open = w.getTileEntity(p) instanceof TileParkBarrier b && b.isOpen();
        return open ? rotated(s.getValue(FACING), 11, 0, 4, 16, 16, 12) : rotated(s.getValue(FACING), 0, 0, 4, 16, 16, 12);
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity t = world.getTileEntity(pos);
        if (!world.isRemote && t instanceof TileParkBarrier b && world.getBlockState(b.armPos()).getBlock() == TravelRegistry.arm) world.setBlockToAir(b.armPos());
        super.breakBlock(world, pos, state);
    }
}
