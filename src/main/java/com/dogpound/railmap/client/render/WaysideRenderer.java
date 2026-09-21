package com.dogpound.railmap.client.render;

import com.dogpound.railmap.block.TileWayside;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;

/** Draws a resized weighbridge / AEI reader. Normal size is left to the chunk renderer. */
public class WaysideRenderer extends TileEntitySpecialRenderer<TileWayside> {
    @Override
    public void render(TileWayside te, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        if (te.getWorld() == null) {
            return;
        }
        LinesideRenderer.drawScaled(te.getWorld(), te.getPos(), te.scale(), x, y, z);
    }

    @Override
    public boolean isGlobalRenderer(TileWayside te) {
        return true;
    }
}
