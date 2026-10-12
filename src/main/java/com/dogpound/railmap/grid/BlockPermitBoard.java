package com.dogpound.railmap.grid;

import com.dogpound.railmap.block.BlockStationDevice;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** The Permit-to-Work Board (see TilePermitBoard) */
public class BlockPermitBoard extends BlockStationDevice {
    public BlockPermitBoard() {
        super("permit_board", Material.IRON,
                "Permit-to-work board: key safe for the lockout keys, permit printer, register display, ISSUE / CANCEL",
                "HV work needs a permit from here: isolate, earth, prove dead, lock off, keys in the safe. Printer + display need 12 V DC");
        setHardness(3f);
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (world.getTileEntity(pos) instanceof TilePermitBoard b) b.dropAll();
        super.breakBlock(world, pos, state);
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) { return new TilePermitBoard(); }
}
