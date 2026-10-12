package com.dogpound.railmap.yard;

import com.dogpound.railmap.block.BlockStationDevice;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/** The Locomotive Diagnostic Panel (see TileLocoDiag) */
public class BlockLocoDiag extends BlockStationDevice {
    public BlockLocoDiag() {
        super("loco_diag", Material.IRON,
                "Depot engine test set: plugs into the nearest locomotive (12 blocks) - gauges, fault codes, start / stop, AESS, isolate, sanders, traction control, WSP",
                "SELECT cycles the locomotives in reach. Needs 12 V DC");
        setHardness(3f);
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) { return new TileLocoDiag(); }
}
