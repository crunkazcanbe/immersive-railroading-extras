package com.dogpound.railmap.client.render;

import com.dogpound.railmap.controls.Layout;
import com.dogpound.railmap.controls.Surface;
import com.dogpound.railmap.grid.Panel;
import com.dogpound.railmap.grid.TilePermitBoard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;

/** what hangs in the Permit Board's key safe, on the hook you hung it on (positions come from the model's hook zones) */
public class PermitBoardRenderer extends TileEntitySpecialRenderer<TilePermitBoard> {
    @Override
    public void render(TilePermitBoard te, double x, double y, double z, float pt, int destroy, float alpha) {
        if (te.getWorld() == null || x * x + y * y + z * z > 32 * 32) return;
        Layout l = te.layout();
        if (l.isEmpty()) return;
        EnumFacing f = te.controlFacing();
        int turns = f.getHorizontalIndex() == 2 ? 0 : f.getHorizontalIndex() == 3 ? 1 : f.getHorizontalIndex() == 0 ? 2 : 3;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.rotate(-90f * turns, 0, 1, 0);
        GlStateManager.translate(-0.5, 0, -0.5);
        RenderHelper.enableStandardItemLighting();
        double face = 0;
        for (Surface s : l.surfaces) for (Panel.C c : s.panel.cs) if (c.id.equals("k1")) face = s.panel.face;   // the hooks' plane
        for (int i = 0; i < TilePermitBoard.HOOKS; i++) {
            ItemStack s = te.hooks.getStackInSlot(i);
            if (s.isEmpty()) continue;
            Panel.C c = l.control("k" + (i + 1));
            if (c == null) continue;
            GlStateManager.pushMatrix();
            GlStateManager.translate((16 - c.u) / 16.0, (c.v - 0.7) / 16.0, (face + 2.0) / 16.0);       // hanging off its peg, behind the glass
            GlStateManager.scale(0.3, 0.3, 0.3);
            GlStateManager.rotate(180f, 0, 1, 0);
            GlStateManager.rotate((float) Math.sin((te.getWorld().getTotalWorldTime() + pt) * 0.05 + i) * 4f, 0, 0, 1);   // swings a little on its hook
            Minecraft.getMinecraft().getRenderItem().renderItem(s, ItemCameraTransforms.TransformType.FIXED);
            GlStateManager.popMatrix();
        }
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }

    @Override public boolean isGlobalRenderer(TilePermitBoard te) { return true; }
}
