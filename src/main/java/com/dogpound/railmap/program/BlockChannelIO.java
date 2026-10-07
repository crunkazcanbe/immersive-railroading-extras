package com.dogpound.railmap.program;

import com.dogpound.railmap.block.BlockStationDevice;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * The Redstone Interface block: a full solid block that joins redstone / Project Red bundled cables to
 * the signal box's 64 wireless channels.
 */
public class BlockChannelIO extends BlockStationDevice {
    public BlockChannelIO() {
        super("channel_io", Material.IRON,
                "Joins redstone and Project Red bundled cables to the signal box channels",
                "Sneak-right-click with the Signal Wrench to set it up");
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return true;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return true;
    }

    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return BlockFaceShape.SOLID;
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileChannelIO();
    }

    @Override
    public boolean canProvidePower(IBlockState state) {
        return true;
    }

    @Override
    public int getWeakPower(IBlockState state, net.minecraft.world.IBlockAccess world, BlockPos pos, EnumFacing side) {
        TileEntity te = world.getTileEntity(pos);
        return te instanceof TileChannelIO t ? t.redstoneOut() : 0;
    }

    @Override
    public int getStrongPower(IBlockState state, net.minecraft.world.IBlockAccess world, BlockPos pos, EnumFacing side) {
        TileEntity te = world.getTileEntity(pos);
        return te instanceof TileChannelIO t ? t.redstoneOut() : 0;
    }

    @Override
    public boolean canConnectRedstone(IBlockState state, net.minecraft.world.IBlockAccess world, BlockPos pos, EnumFacing side) {
        return true;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing side, float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileChannelIO t) {
                player.sendStatusMessage(new TextComponentString(t.statusLine()), true);
            }
        }
        return true;
    }
}
