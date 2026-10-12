package com.dogpound.railmap.client.render;

import com.dogpound.railmap.block.TileFreightOffice;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;

/** what you put on the Freight Office's shelf, standing on it (requested feature) */
public class FreightOfficeRenderer extends TileEntitySpecialRenderer<TileFreightOffice> {
    @Override
    public void render(TileFreightOffice te, double x, double y, double z, float pt, int destroy, float alpha) {
        if (te.getWorld() == null || x * x + y * y + z * z > 48 * 48) return;
        EnumFacing f = te.controlFacing();
        int turns = f.getHorizontalIndex() == 2 ? 0 : f.getHorizontalIndex() == 3 ? 1 : f.getHorizontalIndex() == 0 ? 2 : 3;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.rotate(-90f * turns, 0, 1, 0);
        GlStateManager.translate(-0.5, 0, -0.5);
        RenderHelper.enableStandardItemLighting();
        for (int i = 0; i < TileFreightOffice.SHELF; i++) {
            ItemStack s = te.shelf.getStackInSlot(i);
            if (s.isEmpty()) continue;
            GlStateManager.pushMatrix();
            GlStateManager.translate((19.3 + i * 1.3) / 16.0, 7.0 / 16.0 + 0.16, 13.4 / 16.0);
            GlStateManager.scale(0.34, 0.34, 0.34);
            GlStateManager.rotate(180f, 0, 1, 0);                               // facing the room (north)
            Minecraft.getMinecraft().getRenderItem().renderItem(s, ItemCameraTransforms.TransformType.FIXED);
            GlStateManager.popMatrix();
        }
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }

    @Override public boolean isGlobalRenderer(TileFreightOffice te) { return true; }
}
