package com.dogpound.railmap.logic;

import com.dogpound.railmap.block.BlockStationDevice;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/** One railway logic module (her ChatGPT list #99); see TileLogic. Redstone in on the sides and underneath, out of the back. */
public class BlockLogic extends BlockStationDevice {
    public final TileLogic.Kind kind;

    public BlockLogic(TileLogic.Kind kind, String tip) {
        super(kind.id, Material.IRON, tip,
                "Inputs A/B/C: channel, Instrument Wire, redstone (left / right / under), variable or expression. Q: channel, redstone (back), variable",
                "Sneak-right-click with the Signal Wrench to wire it up. 12 V DC");
        this.kind = kind;
        setHardness(2f);
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) { return new TileLogic(kind); }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return rotated(state.getValue(FACING), 1, 0, 6, 15, 15, 16);
    }

    @Override public boolean canProvidePower(IBlockState state) { return true; }
    @Override public boolean canConnectRedstone(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) { return true; }

    @Override
    public int getWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        TileEntity te = world.getTileEntity(pos);
        return te instanceof TileLogic t ? t.redstoneOut(side) : 0;
    }

    @Override
    public int getStrongPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return getWeakPower(state, world, pos, side);
    }
}
