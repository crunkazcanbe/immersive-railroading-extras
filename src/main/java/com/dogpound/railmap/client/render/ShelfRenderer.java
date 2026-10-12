package com.dogpound.railmap.client.render;

import com.dogpound.railmap.block.TileFurniture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;

/** what you put on an office's shelf (trophies, samples, leaflets), standing on it - like the Freight Office's */
public class ShelfRenderer extends TileEntitySpecialRenderer<TileFurniture> {
    @Override
    public void render(TileFurniture te, double x, double y, double z, float pt, int destroy, float alpha) {
        double[][] spots = te.shelfSpots();
        if (te.getWorld() == null || spots.length == 0 || x * x + y * y + z * z > 48 * 48) return;
        EnumFacing f = te.controlFacing();
        int turns = f.getHorizontalIndex() == 2 ? 0 : f.getHorizontalIndex() == 3 ? 1 : f.getHorizontalIndex() == 0 ? 2 : 3;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.rotate(-90f * turns, 0, 1, 0);
        GlStateManager.translate(-0.5, 0, -0.5);
        RenderHelper.enableStandardItemLighting();
        for (int i = 0; i < Math.min(spots.length, te.shelf.getSizeInventory()); i++) {
            ItemStack s = te.shelf.getStackInSlot(i);
            if (s.isEmpty()) continue;
            GlStateManager.pushMatrix();
            GlStateManager.translate(spots[i][0] / 16.0, spots[i][1] / 16.0 + 0.16, spots[i][2] / 16.0);
            GlStateManager.scale(0.34, 0.34, 0.34);
            GlStateManager.rotate(180f, 0, 1, 0);
            Minecraft.getMinecraft().getRenderItem().renderItem(s, ItemCameraTransforms.TransformType.FIXED);
            GlStateManager.popMatrix();
        }
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }

    @Override public boolean isGlobalRenderer(TileFurniture te) { return true; }
}
