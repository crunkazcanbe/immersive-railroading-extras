package com.dogpound.railmap.station;

import com.dogpound.railmap.block.BlockStationDevice;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class BlockStationMaster extends BlockStationDevice {
    public BlockStationMaster() {
        super("station_master", Material.WOOD, "Names the station and runs it: platforms, announcements, the day's log",
                "Sneak-right-click with the Signal Wrench to set it up");
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return rotated(state.getValue(FACING), 0, 0, 3.5, 16, 16, 14.5);
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) { return new TileStationMaster(); }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing f,
                                    float hx, float hy, float hz) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof TileStationMaster t)
            player.sendStatusMessage(new net.minecraft.util.text.TextComponentString(t.statusLine()), true);
        return true;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (world.getTileEntity(pos) instanceof TileStationMaster t) t.unregister();
        super.breakBlock(world, pos, state);
    }
}
