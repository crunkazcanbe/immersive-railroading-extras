package com.dogpound.railmap.client.render;

import com.dogpound.railmap.block.TileArrivalsBoard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;

import java.util.List;

/**
 * Draws the arrivals board's display: an amber split-flap board on black, the station name in
 * the header, one row per train (TRAIN · TO · WHEN), and a flashing "NOW ARRIVING" banner.
 * Joined boards draw once, across their full width, from the left-most block.
 */
public class ArrivalsBoardRenderer extends TileEntitySpecialRenderer<TileArrivalsBoard> {
    /** Texels per block of board face. */
    private static final int PX = 160;
    /** In front of the bezel, so joined boards read as one continuous display across the seams. */
    private static final double FACE_Z = (10.45 - 8) / 16.0;
    private static final double TOP = 14.5 / 16.0, BOTTOM = 4.5 / 16.0, SIDE = 1.0 / 16.0;

    private static final int AMBER = 0xFFFFB02E, AMBER_DIM = 0xFF6E4A12, WHITE = 0xFFF4F1E8;
    private static final int BG = 0xFF07080A, CELL = 0xFF121418, HEADER = 0xFF0B2A55, LINE = 0xFF1D2128;
    private static final int GREEN = 0xFF55E07A, RED = 0xFFFF5A4E;

    @Override
    public void render(TileArrivalsBoard te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        if (te.getWorld() == null || !te.isController()) return;
        EnumFacing f = te.facing();
        EnumFacing r = te.right();
        int blocks = te.width();

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.translate(-f.getXOffset() * FACE_Z, 0, -f.getZOffset() * FACE_Z);
        GlStateManager.translate(-r.getXOffset() * (0.5 - SIDE), TOP, -r.getZOffset() * (0.5 - SIDE));
        GlStateManager.rotate(-f.getHorizontalIndex() * 90f, 0, 1, 0);
        GlStateManager.scale(1, -1, 1);
        double s = 1.0 / PX;
        GlStateManager.scale(s, s, s);

        int w = (int) Math.round((blocks - 2 * SIDE) * PX);
        int h = (int) Math.round((TOP - BOTTOM) * PX);

        GlStateManager.disableLighting();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        GlStateManager.disableCull();
        draw(te, w, h);
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private void draw(TileArrivalsBoard te, int w, int h) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        long t = te.getWorld().getTotalWorldTime();
        Gui.drawRect(0, 0, w, h, BG);

        // Header: station name, white on transit blue, a clock at the right.
        int head = 14;
        Gui.drawRect(0, 0, w, head, HEADER);
        String name = te.stationName().isEmpty() ? "NO STATION NEARBY" : te.stationName().toUpperCase();
        text(font, fit(font, name, w - 44), 3, 3, WHITE);
        long day = te.getWorld().getWorldTime() % 24000;
        int hh = (int) ((day / 1000 + 6) % 24), mm = (int) (day % 1000 * 60 / 1000);
        String clock = String.format("%02d:%02d", hh, mm);
        text(font, clock, w - font.getStringWidth(clock) - 3, 3, AMBER);

        String arriving = te.arriving();
        int y = head + 2;
        if (!arriving.isEmpty()) {
            boolean on = (t / 8) % 2 == 0;
            Gui.drawRect(0, y, w, y + 12, on ? 0xFF3A2400 : 0xFF140C00);
            text(font, fit(font, "NOW ARRIVING  " + arriving.toUpperCase(), w - 6), 3, y + 2, on ? AMBER : AMBER_DIM);
            y += 14;
        }

        // Column heads.
        int colTo = (int) (w * 0.42), colWhen = w - 44;
        text(font, "TRAIN", 3, y, AMBER_DIM);
        text(font, "TO", colTo, y, AMBER_DIM);
        text(font, "DUE", colWhen, y, AMBER_DIM);
        y += 10;
        Gui.drawRect(2, y - 1, w - 2, y, LINE);

        List<String[]> rows = te.rows();
        int rowH = 12;
        if (rows.isEmpty()) {
            text(font, "NO TRAINS SCHEDULED", 3, y + 3, AMBER_DIM);
        }
        for (String[] row : rows) {
            if (y + rowH > h - 12) break;
            // Split-flap cells: a darker tile behind every character slot.
            for (int cx = 2; cx < w - 2; cx += 7) Gui.drawRect(cx, y + 1, cx + 6, y + rowH - 1, CELL);
            Gui.drawRect(2, y + rowH / 2, w - 2, y + rowH / 2 + 1, BG);   // the hinge line
            text(font, fit(font, row[0].toUpperCase(), colTo - 6), 3, y + 2, AMBER);
            text(font, fit(font, row[1].toUpperCase(), colWhen - colTo - 4), colTo, y + 2, AMBER);
            int c = row[2].equals("BOARDING") ? ((t / 10) % 2 == 0 ? GREEN : AMBER) : row[2].equals("DUE") ? RED : AMBER;
            text(font, row[2], colWhen, y + 2, c);
            y += rowH;
        }

        // Footer: passengers waiting.
        Gui.drawRect(0, h - 11, w, h, 0xFF0A0C10);
        int waiting = te.waiting();
        String foot = waiting == 0 ? "BUY TICKETS AT THE MACHINE" : waiting + (waiting == 1 ? " PASSENGER WAITING" : " PASSENGERS WAITING");
        text(font, fit(font, foot, w - 6), 3, h - 9, waiting == 0 ? AMBER_DIM : GREEN);
    }

    private static void text(FontRenderer font, String s, int x, int y, int color) {
        GlStateManager.enableTexture2D();
        font.drawString(s, x, y, color);
    }

    private static String fit(FontRenderer font, String s, int width) {
        if (font.getStringWidth(s) <= width) return s;
        return font.trimStringToWidth(s, Math.max(0, width - font.getStringWidth("…"))) + "…";
    }

    @Override
    public boolean isGlobalRenderer(TileArrivalsBoard te) {
        return true;
    }
}
