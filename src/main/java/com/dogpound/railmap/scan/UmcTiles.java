package com.dogpound.railmap.scan;

import cam72cam.mod.block.BlockEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * IR and LandOfSignals are both UniversalModCore mods: their real tile logic lives in a
 * {@link BlockEntity} wrapped by one generic {@code cam72cam.mod.block.tile.TileEntity}.
 * One pass over the world's loaded tile list, unwrapped, is the cheapest way to find them
 * without touching chunks.
 */
final class UmcTiles {
    private UmcTiles() {}

    static List<BlockEntity> loaded(World world) {
        List<BlockEntity> out = new ArrayList<>();
        for (TileEntity te : world.loadedTileEntityList) {
            if (te instanceof cam72cam.mod.block.tile.TileEntity umc && !te.isInvalid()) {
                BlockEntity be = umc.instance();
                if (be != null) out.add(be);
            }
        }
        return out;
    }
}
