package com.dogpound.railmap.alarm;

import com.dogpound.railmap.block.BlockStationDevice;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/** The Event Recorder rack (see TileEventRecorder) */
public class BlockEventRecorder extends BlockStationDevice {
    public BlockEventRecorder() {
        super("event_recorder", Material.IRON,
                "The railway black box: records every train and every alarm, replay on the console, prints incident reports",
                "Records while it has 12 V DC");
        setHardness(3f);
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) { return new TileEventRecorder(); }
}
