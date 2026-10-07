package com.dogpound.railmap.client.render;

import com.dogpound.railmap.program.BlockControlDesk;
import com.dogpound.railmap.program.TileControlDesk;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;

/** The desk's live top: 12 push buttons (lit when their lamp is on), lamps, labels, and two green screens. */
public class ControlDeskRenderer extends TileEntitySpecialRenderer<TileControlDesk> {
    private static final float[] COLS = {0.2f, 0.4f, 0.6f, 0.8f}, ROWS = {0.16f, 0.32f, 0.48f};
    private static final double TOP = 14 / 16.0 + 0.003;

    private static int colour(String c) {
        return switch (c) { case "Green" -> 0x2FCB5A; case "Yellow" -> 0xF2C318; case "Blue" -> 0x2F7CE0; case "White" -> 0xE8E8E8; case "Black" -> 0x26282C; default -> 0xD8322C; };
    }

    @Override
    public void render(TileControlDesk te, double x, double y, double z, float pt, int destroy, float alpha) {
        if (te.getWorld() == null) return;
        IBlockState st = te.getWorld().getBlockState(te.getPos());
        if (!(st.getBlock() instanceof BlockControlDesk)) return;
        int yRot = switch (st.getValue(BlockControlDesk.FACING)) { case EAST -> 90; case SOUTH -> 180; case WEST -> 270; default -> 0; };
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.rotate(-yRot, 0, 1, 0);
        GlStateManager.translate(-0.5, 0, -0.5);
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        Prims.begin();
        int lamps = te.lampBits();
        long t = te.getWorld().getTotalWorldTime();
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 4; c++) {
                int i = r * 4 + c + 1;
                boolean lit = (lamps & (1 << (i - 1))) != 0;
                int col = colour(te.colour(i));
                double cx = COLS[c], cz = ROWS[r], h = 0.045;
                int face = lit ? col : darker(col);
                Prims.box(cx - h, TOP, cz - h, cx + h, TOP + 0.03, cz + h, face, 1f);              // the push button
                Prims.box(cx - 0.012, TOP, cz + 0.06, cx + 0.012, TOP + 0.008, cz + 0.084,        // its indicator lamp
                        lit ? 0x7CFF9A : 0x203024, 1f);
            }
        }
        // screens (back half): dark glass with a faint glow line
        Prims.box(0.06, TOP + 0.02, 0.61, 0.47, TOP + 0.021, 0.96, 0x041208, 1f);
        Prims.box(0.53, TOP + 0.02, 0.61, 0.94, TOP + 0.021, 0.96, 0x041208, 1f);
        if ((t / 10) % 2 == 0 && te.screen(1).contains("ALARMS") && !te.screen(1).endsWith("ALARMS 0"))
            Prims.box(0.43, TOP + 0.022, 0.62, 0.46, TOP + 0.023, 0.65, 0xFF3030, 1f);       // flashing alarm pip
        Prims.end();

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 4; c++) {
                int i = r * 4 + c + 1;
                flat(font, te.label(i), COLS[c], TOP + 0.031, ROWS[r] - 0.07, 0.0025, 0xFFFFFF, true);
            }
        screen(font, te.screen(1), 0.08, 0.63);
        screen(font, te.screen(2), 0.55, 0.63);
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private static int darker(int c) {
        return (((c >> 16) & 255) * 2 / 5 << 16) | (((c >> 8) & 255) * 2 / 5 << 8) | ((c & 255) * 2 / 5);
    }

    private static void screen(FontRenderer font, String text, double x0, double z0) {
        String[] lines = text.split("\n");
        for (int k = 0; k < lines.length && k < 4; k++)
            flat(font, lines[k], x0, TOP + 0.024, z0 + 0.075 + k * 0.075, 0.0018, 0x6CFF9C, false);
    }

    /** text lying flat on the desk, readable from the front edge */
    private static void flat(FontRenderer font, String s, double x, double y, double z, double k, int colour, boolean centred) {
        if (s == null || s.isEmpty()) return;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(-90, 1, 0, 0);
        GlStateManager.scale(k, -k, k);
        int w = font.getStringWidth(s);
        double maxW = centred ? 0.18 / k : 0.38 / k;
        if (w > maxW) { double f = maxW / w; GlStateManager.scale(f, f, 1); }
        font.drawString(s, centred ? -w / 2 : 0, centred ? -4 : 0, colour);
        GlStateManager.popMatrix();
    }
}
