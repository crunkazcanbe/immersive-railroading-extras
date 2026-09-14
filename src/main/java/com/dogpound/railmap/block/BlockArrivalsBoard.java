package com.dogpound.railmap.block;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/** The arrivals board block. Hangs on a wall or under a canopy; the tile does the work. */
public class BlockArrivalsBoard extends BlockStationDevice {
    public BlockArrivalsBoard() {
        super("arrivals_board", Material.IRON,
                "Shows the driverless trains due at the nearest station.",
                "Place several side by side for one wide board",
                "Chimes when a train pulls in");
        setLightLevel(0.4f);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileArrivalsBoard();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof TileArrivalsBoard b) {
            String s = b.stationName().isEmpty()
                    ? "No named station nearby — name one on the Dispatcher Board"
                    : "Arrivals board for " + b.stationName() + " · " + b.rows().size() + " train(s) listed";
            player.sendStatusMessage(new TextComponentString(s), true);
        }
        return true;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return rotated(state.getValue(FACING), 0, 3, 11, 16, 16, 16);
    }
}
