package com.dogpound.railmap.factory.client;

import com.dogpound.railmap.factory.block.BlockPipe;
import com.dogpound.railmap.factory.block.TilePipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;

/** The flow meter's LCDs: the live flow on all four dials. */
public class PipeRenderer extends TileEntitySpecialRenderer<TilePipe> {
    @Override
    public void render(TilePipe te, double x, double y, double z, float pt, int destroy, float alpha) {
        if (te.getWorld() == null || te.kind() != BlockPipe.Kind.METER) return;
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        String s = te.flow * 20 + " L/s";
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        GlStateManager.disableLighting();
        for (int i = 0; i < 4; i++) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5);
            GlStateManager.rotate(90f * i + 180f, 0, 1, 0);                    // turn this dial to face +z
            GlStateManager.translate(0, -0.105, 5 / 16.0 + 0.004);
            float k = 1 / 160f;
            GlStateManager.scale(k, -k, k);
            fr.drawString(s, -fr.getStringWidth(s) / 2, 0, 0x40FF60);
            GlStateManager.popMatrix();
        }
        GlStateManager.enableLighting();
    }
}
