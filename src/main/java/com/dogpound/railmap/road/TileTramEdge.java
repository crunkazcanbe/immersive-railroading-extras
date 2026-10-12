package com.dogpound.railmap.road;

/**
 * Lit platform edge marker (batch 5 #4): platform paving with the yellow safety line, tactile blisters and a row of
 * flush LED edge lights. Fed from the nearest Tram Stop within 16 blocks: the LEDs flash while a tram is due or
 * standing at it (keep back from the edge), dark otherwise or when the stop has no supply.
 */
public class TileTramEdge extends RoadTile {
    private long stopPos = Long.MIN_VALUE;

    @Override
    protected void serverTick() {
        if (age % 10 != 3) return;
        if (age % 200 == 3 || stopPos == Long.MIN_VALUE) {
            TileTramStop s = TileTramStop.nearest(world, pos, 16);
            stopPos = s == null ? Long.MIN_VALUE : s.getPos().toLong();
        }
        boolean on = stopPos != Long.MIN_VALUE && world.getTileEntity(net.minecraft.util.math.BlockPos.fromLong(stopPos)) instanceof TileTramStop s && s.live() && s.approaching();
        for (int i = 1; i <= 6; i++) put("led" + i, on);
    }
}
