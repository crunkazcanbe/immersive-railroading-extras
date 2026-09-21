package com.dogpound.railmap.client.render;

import com.dogpound.railmap.signal.TileRailSign;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import org.lwjgl.opengl.GL11;

/**
 * Draws a rail sign entirely in code: a steel post, a coloured board, a border, and the face's
 * text (or the player's custom text). No per-sign texture — the whole catalogue is just data on
 * {@link TileRailSign.Face}. The board faces the block's FACING and scales with the tile.
 */
public class RailSignRenderer extends TileEntitySpecialRenderer<TileRailSign> {

    @Override
    public void render(TileRailSign te, double x, double y, double z, float partial, int stage, float alpha) {
        if (te.getWorld() == null) {
            return;
        }
        TileRailSign.Face face = te.face();
        EnumFacing f = te.facing();
        int yRot = switch (f) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
        String[] lines = face == TileRailSign.Face.CUSTOM
                ? splitCustom(te.customText())
                : face.lines;

        float s = te.scale();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.scale(s, s, s);
        GlStateManager.rotate(-yRot, 0, 1, 0);
        GlStateManager.disableLighting();
        GlStateManager.disableCull();

        // --- post (grey), from the ground up to the board ---
        quadBox(-1.5 / 16.0, 0, -1.5 / 16.0, 1.5 / 16.0, 11.0 / 16.0, 1.5 / 16.0, 0x585d63);

        // --- board: a flat panel near the top, facing +Z (south of centre after rotation) ---
        double bw = boardWidth(lines);          // half-width in block units
        double by0 = 11.0 / 16.0, by1 = by0 + boardHeight(lines);
        double bz = 1.6 / 16.0;
        // border (slightly larger, behind)
        panel(-bw - 0.03, by0 - 0.03, bw + 0.03, by1 + 0.03, bz - 0.002, face.border);
        // face
        panel(-bw, by0, bw, by1, bz, face.bg);

        // --- text ---
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        GlStateManager.enableTexture2D();
        double cy = (by0 + by1) / 2.0;
        int n = lines.length;
        double lineH = 0.11;
        double startY = cy + (n - 1) * lineH / 2.0;
        double px = 1 / 16.0 / 8.0;   // font scale
        for (int i = 0; i < n; i++) {
            String ln = lines[i];
            if (ln.isEmpty()) continue;
            GlStateManager.pushMatrix();
            GlStateManager.translate(0, startY - i * lineH, bz + 0.002);
            GlStateManager.scale(px, -px, px);
            int w = font.getStringWidth(ln);
            font.drawString(ln, Math.round(-w / 2f), 0, face.fg);
            GlStateManager.popMatrix();
        }
        GlStateManager.disableTexture2D();

        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private static String[] splitCustom(String text) {
        if (text == null || text.isEmpty()) return new String[]{"( blank )"};
        return text.split("\\|", -1);
    }

    /** board half-width from the widest line */
    private static double boardWidth(String[] lines) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int max = 6;
        for (String l : lines) max = Math.max(max, font.getStringWidth(l));
        double px = 1 / 16.0 / 8.0;
        return Math.max(3.5 / 16.0, max * px / 2.0 + 0.06);
    }

    private static double boardHeight(String[] lines) {
        return Math.max(0.28, lines.length * 0.11 + 0.08);
    }

    /** A flat coloured quad in the XY plane at depth z (double-sided). */
    private static void panel(double x0, double y0, double x1, double y1, double z, int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        GlStateManager.disableTexture2D();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        buf.pos(x0, y0, z).color(r, g, b, 1f).endVertex();
        buf.pos(x1, y0, z).color(r, g, b, 1f).endVertex();
        buf.pos(x1, y1, z).color(r, g, b, 1f).endVertex();
        buf.pos(x0, y1, z).color(r, g, b, 1f).endVertex();
        // back face
        buf.pos(x0, y1, z).color(r, g, b, 1f).endVertex();
        buf.pos(x1, y1, z).color(r, g, b, 1f).endVertex();
        buf.pos(x1, y0, z).color(r, g, b, 1f).endVertex();
        buf.pos(x0, y0, z).color(r, g, b, 1f).endVertex();
        tess.draw();
    }

    /** A simple solid-colour box (the post). */
    private static void quadBox(double x0, double y0, double z0, double x1, double y1, double z1, int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        GlStateManager.disableTexture2D();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        double[][] faces = {
            {x0, y0, z1, x1, y1, z1}, {x1, y0, z0, x0, y1, z0},
            {x0, y0, z0, x0, y1, z1}, {x1, y0, z1, x1, y1, z0},
        };
        for (double[] q : faces) {
            buf.pos(q[0], q[1], q[2]).color(r, g, b, 1f).endVertex();
            buf.pos(q[3], q[1], q[5]).color(r, g, b, 1f).endVertex();
            buf.pos(q[3], q[4], q[5]).color(r, g, b, 1f).endVertex();
            buf.pos(q[0], q[4], q[2]).color(r, g, b, 1f).endVertex();
        }
        // top
        buf.pos(x0, y1, z0).color(r, g, b, 1f).endVertex();
        buf.pos(x1, y1, z0).color(r, g, b, 1f).endVertex();
        buf.pos(x1, y1, z1).color(r, g, b, 1f).endVertex();
        buf.pos(x0, y1, z1).color(r, g, b, 1f).endVertex();
        tess.draw();
    }
}
