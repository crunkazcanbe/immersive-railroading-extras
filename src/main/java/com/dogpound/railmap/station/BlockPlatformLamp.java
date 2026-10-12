package com.dogpound.railmap.station;

import com.dogpound.railmap.depot.DepotBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/** the platform lamp column: gives light 15 while its lantern is lit */
public class BlockPlatformLamp extends DepotBlock {
    public BlockPlatformLamp() {
        super("platform_lamp", TilePlatformLamp::new, new double[]{5, 0, 5, 11, 16, 11},
                "Platform lamp column: really lights the platform, dusk to dawn on its photocell",
                "AUTO / ON / OFF. Mains + 12 V. Counts as station lighting");
    }

    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        return world.getTileEntity(pos) instanceof TilePlatformLamp t && t.lampOn() ? 15 : 0;
    }
}
