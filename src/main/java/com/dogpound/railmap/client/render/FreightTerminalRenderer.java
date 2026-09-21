package com.dogpound.railmap.client.render;

import com.dogpound.railmap.block.TileFreightTerminal;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;

/**
 * Draws a resized loading gantry / unloading pit. At normal size the chunk renderer draws it and
 * this does nothing; dialled to any other size with the Signal Wrench, the chunk model is swapped
 * for an empty one and the structure is drawn here instead — so the gantry can be matched to
 * whatever gauge of stock is running under it.
 */
public class FreightTerminalRenderer extends TileEntitySpecialRenderer<TileFreightTerminal> {

    @Override
    public void render(TileFreightTerminal te, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        if (te.getWorld() == null) {
            return;
        }
        LinesideRenderer.drawScaled(te.getWorld(), te.getPos(), te.scale(), x, y, z);
    }

    @Override
    public boolean isGlobalRenderer(TileFreightTerminal te) {
        return true;   // it reaches well past its own block
    }
}
