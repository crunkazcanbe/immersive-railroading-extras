package com.dogpound.railmap.stnlife6;

import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;

import java.util.List;

/** a platform device with a moving part: the blockstate LEVEL that picks its model, and its live collision boxes */
public abstract class MovingTile extends LifeTile {
    /** 0-9: which model of the moving part to draw (blockstate LEVEL) */
    public abstract int level();
    /** collision boxes in the north-facing model frame, px {x0,y0,z0,x1,y1,z1} */
    public abstract List<double[]> boxes();

    /** the client redraws the block when the moving part reaches another model step (same meta: no block change) */
    @Override
    public void onDataPacket(NetworkManager n, SPacketUpdateTileEntity p) {
        int was = level();
        super.onDataPacket(n, p);
        if (world != null && level() != was) world.markBlockRangeForRenderUpdate(pos, pos);
    }
}
