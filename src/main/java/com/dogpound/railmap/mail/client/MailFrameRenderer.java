package com.dogpound.railmap.mail.client;

import com.dogpound.railmap.client.render.Prims;
import com.dogpound.railmap.client.render.ShelfRenderer;
import com.dogpound.railmap.mail.TileSortingOffice;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;

/**
 * The Sorting Office's live contents (batch 5 #8): the letters lying in each pigeonhole of the sorting frame (as many
 * as are really sorted there, up to ten drawn), the pile in the franked-mail tray, the sealed bags hanging on the
 * DESPATCH and ARRIVED rails, and whatever stands on the counter shelf.
 */
public class MailFrameRenderer extends TileEntitySpecialRenderer<TileSortingOffice> {
    private static final ShelfRenderer SHELF = new ShelfRenderer();
    private static final double P = 1 / 16.0;

    @Override
    public void render(TileSortingOffice te, double x, double y, double z, float pt, int destroy, float alpha) {
        if (te.getWorld() == null) return;
        SHELF.render(te, x, y, z, pt, destroy, alpha);
        if (x * x + y * y + z * z > 40 * 40) return;
        EnumFacing f = te.controlFacing();
        int turns = f.getHorizontalIndex() == 2 ? 0 : f.getHorizontalIndex() == 3 ? 1 : f.getHorizontalIndex() == 0 ? 2 : 3;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.rotate(-90f * turns, 0, 1, 0);
        GlStateManager.translate(-0.5, 0, -0.5);
        GlStateManager.disableLighting();
        int l = te.getWorld().getCombinedLight(te.getPos().up(), 0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, l & 0xFFFF, l >> 16);
        Prims.begin();
        // letters in the pigeonholes
        for (int i = 0; i < TileSortingOffice.HOLES; i++) {
            int n = Math.min(10, te.holes[i]);
            if (n == 0) continue;
            int c = i % TileSortingOffice.COLS, r = TileSortingOffice.ROWS - 1 - i / TileSortingOffice.COLS;
            double x0 = TileSortingOffice.HX0 + c * TileSortingOffice.HW, y0 = TileSortingOffice.HY0 + r * TileSortingOffice.HH + 0.7;
            for (int k = 0; k < n; k++) letter(x0 + 0.35 + (k % 3) * 0.06, y0 + k * 0.16, TileSortingOffice.HZ + 0.5 + (k % 2) * 0.15, TileSortingOffice.HW - 0.75, 2.4, k);
        }
        // franked mail waiting in the tray on the desk
        for (int k = 0; k < Math.min(12, te.tray); k++) letter(1.3 + (k % 3) * 0.08, 9.15 + k * 0.15, 9.8 + (k % 2) * 0.2, 2.6, 2.0, k);
        // bags on the despatch (upper) and arrived (lower) rails
        for (int k = 0; k < Math.min(6, te.bagsOut); k++) bag(19.4 + k * 1.75, 26.0, 0xB8B09A, 0xC02020);
        for (int k = 0; k < Math.min(6, te.bagsIn); k++) bag(19.4 + k * 1.75, 18.4, 0x9AA4B8, 0x2050C0);
        Prims.end();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    /** an envelope lying flat (model px): front-left corner x, bottom y, front z, width, depth */
    private static void letter(double x, double y, double z, double w, double d, int k) {
        int paper = k % 4 == 3 ? 0xD8C8A0 : k % 5 == 2 ? 0xE8E0F0 : 0xF2EFE6;
        Prims.box(x * P, y * P, z * P, (x + w) * P, (y + 0.12) * P, (z + d) * P, paper);
        Prims.box((x + w - 0.45) * P, (y + 0.12) * P, (z + 0.15) * P, (x + w - 0.1) * P, (y + 0.14) * P, (z + 0.55) * P, k % 2 == 0 ? 0xC02030 : 0x2050A0);   // stamp
        Prims.box((x + 0.2) * P, (y + 0.12) * P, (z + 0.9) * P, (x + w * 0.6) * P, (y + 0.13) * P, (z + 1.0) * P, 0x404040);                                  // address line
    }

    /** a sealed canvas mail bag hanging from a hook at (x, top) on the rack */
    private static void bag(double x, double top, int canvas, int tag) {
        double z0 = 12.4, z1 = 14.2;
        Prims.box((x - 0.15) * P, (top - 0.9) * P, (z0 + 0.75) * P, (x + 0.15) * P, top * P, (z0 + 1.05) * P, 0x606060);               // hook strap
        Prims.box((x - 0.35) * P, (top - 1.4) * P, (z0 + 0.6) * P, (x + 0.35) * P, (top - 0.9) * P, (z0 + 1.2) * P, Prims.shade(canvas, 0.8f));   // gathered neck
        Prims.box((x - 0.75) * P, (top - 5.0) * P, z0 * P, (x + 0.75) * P, (top - 1.4) * P, z1 * P, canvas);                      // the sack
        Prims.box((x - 0.8) * P, (top - 4.0) * P, (z0 - 0.02) * P, (x + 0.8) * P, (top - 3.8) * P, (z1 + 0.02) * P, Prims.shade(canvas, 0.7f));   // stitched band
        Prims.box((x + 0.2) * P, (top - 2.2) * P, (z0 - 0.08) * P, (x + 0.7) * P, (top - 1.5) * P, (z0 - 0.02) * P, tag);          // destination label
        Prims.box((x - 0.1) * P, (top - 1.55) * P, (z0 + 0.5) * P, (x + 0.1) * P, (top - 1.35) * P, (z0 + 0.6) * P, 0xC8A040);   // brass seal
    }

    @Override public boolean isGlobalRenderer(TileSortingOffice te) { return true; }
}
