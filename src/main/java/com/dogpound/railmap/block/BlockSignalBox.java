package com.dogpound.railmap.block;

import com.dogpound.railmap.RailMap;

/**
 * The Signal Box Computer: the same live network map as the dispatcher board, opened straight into
 * the program editor. Click signals and switches on the map, write WHEN / THEN rules, flip the 64
 * wireless channels, read the alarm log.
 */
public class BlockSignalBox extends BlockDispatcherBoard {
    public BlockSignalBox() {
        super("signal_box");
    }

    @Override
    public net.minecraft.tileentity.TileEntity createTileEntity(net.minecraft.world.World world, net.minecraft.block.state.IBlockState state) {
        return new TileSignalBox();
    }

    @Override
    protected int gui() { return RailMap.GUI_SIGNAL_BOX; }
}
