package com.dogpound.railmap.program;

import com.dogpound.railmap.block.BlockStationDevice;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BlockControlDesk extends BlockStationDevice {
    public BlockControlDesk() {
        super("control_desk", Material.IRON,
                "A signalling desk: 12 push buttons you program, lamps and two little screens",
                "Sneak-right-click with the Signal Wrench to program the buttons");
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, net.minecraft.world.IBlockAccess world, BlockPos pos) {
        return rotated(state.getValue(FACING), 0, 0, 0, 16, 14, 16);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileControlDesk();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (facing != EnumFacing.UP) return false;
        if (world.isRemote) return true;           // the server presses it; the client just swings the hand
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileControlDesk desk)) return false;
        EnumFacing f = state.getValue(FACING);
        float u, v;
        switch (f) {
            case SOUTH: u = 1 - hitX; v = 1 - hitZ; break;
            case EAST: u = hitZ; v = 1 - hitX; break;      // model is turned 90 for east (blockstate y=90)
            case WEST: u = 1 - hitZ; v = hitX; break;
            default: u = hitX; v = hitZ;
        }
        int n = -1;
        float[] cols = {0.2f, 0.4f, 0.6f, 0.8f};
        float[] rows = {0.16f, 0.32f, 0.48f};          // front three rows; the screens sit at the back
        for (int r = 0; r < 3 && n < 0; r++) {
            for (int c = 0; c < 4; c++) {
                if (Math.abs(u - cols[c]) <= 0.08f && Math.abs(v - rows[r]) <= 0.07f) { n = r * 4 + c + 1; break; }
            }
        }
        if (n > 0) desk.press(n, player);
        return true;
    }
}
