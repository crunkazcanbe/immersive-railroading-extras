package com.dogpound.railmap.doors;

import com.dogpound.railmap.block.BlockStationDevice;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/** The platform-edge box that opens the train doors (and the platform screen doors) when a train stops. */
public class BlockStationDoors extends BlockStationDevice {

    public BlockStationDoors() {
        super("station_doors", Material.IRON,
                "Opens the doors of trains that stop beside it: chime, open, dwell, beeps, close.",
                "Platform screen doors nearby follow it · door interlock holds the train",
                "Sneak + Signal Wrench: program it · redstone in / out");
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) { return new TileStationDoors(); }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof TileStationDoors t)
            player.sendStatusMessage(new TextComponentString(t.statusLine()), true);
        return true;
    }

    @Override @SuppressWarnings("deprecation")
    public boolean canProvidePower(IBlockState state) { return true; }

    @Override @SuppressWarnings("deprecation")
    public int getWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return world.getTileEntity(pos) instanceof TileStationDoors t ? t.redstoneOut() : 0;
    }

    @Override @SuppressWarnings("deprecation")
    public boolean isOpaqueCube(IBlockState state) { return false; }

    @Override @SuppressWarnings("deprecation")
    public boolean isFullCube(IBlockState state) { return false; }
}
