package com.dogpound.railmap.client.render;

import com.dogpound.railmap.signal.TileSpeedSign;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;

/**
 * Paints the posted limit onto the speed sign's plate: "SPEED LIMIT" over the number, black on
 * the white plate, the way the MUTCD sign reads. The plate itself comes from the block model.
 */
public class SpeedSignRenderer extends TileEntitySpecialRenderer<TileSpeedSign> {
    /** Plate centre and front surface in the model (north-facing variant), in block units from centre. */
    private static final double PLATE_Y = 11.5 / 16.0, FRONT_Z = (10.0 - 8) / 16.0 + 0.004;

    @Override
    public void render(TileSpeedSign te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        if (te.getWorld() == null) return;
        if (Minecraft.getMinecraft().player != null
                && Minecraft.getMinecraft().player.getDistanceSq(te.getPos()) > 48 * 48) return;
        EnumFacing f = te.facing();
        int yRot = switch (f) { case EAST -> 90; case SOUTH -> 180; case WEST -> 270; default -> 0; };
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y + PLATE_Y, z + 0.5);
        GlStateManager.rotate(-yRot, 0, 1, 0);
        GlStateManager.translate(0, 0, FRONT_Z);
        GlStateManager.disableLighting();
        GlStateManager.disableCull();

        // "SPEED" / "LIMIT" small at the top of the plate.
        GlStateManager.pushMatrix();
        double small = 1 / 16.0 / 11.0;
        GlStateManager.translate(0, 0.17, 0);
        GlStateManager.scale(small, -small, small);
        center(font, "SPEED", 0);
        center(font, "LIMIT", 9);
        GlStateManager.popMatrix();

        // The number, big.
        GlStateManager.pushMatrix();
        String n = String.valueOf(te.mph());
        double big = 1 / 16.0 / (n.length() > 2 ? 4.2 : 3.2);
        GlStateManager.translate(0, -0.06, 0);
        GlStateManager.scale(big, -big, big);
        center(font, n, -4);
        GlStateManager.popMatrix();

        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private static void center(FontRenderer font, String s, int y) {
        font.drawString(s, -font.getStringWidth(s) / 2, y, 0xFF101010);
    }
}
