package com.dogpound.railmap.client.render;

import com.dogpound.railmap.signal.TileCrossing;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;

/**
 * Grade crossing hardware: the crossbuck and its alternating flashers, the gate arm coming
 * down with its own three lamps, and the cantilever reaching out over the road.
 */
public class CrossingRenderer extends TileEntitySpecialRenderer<TileCrossing> {
    private static final int POST = 0xC8CCD0;       // galvanised, near-white
    private static final int POST_DARK = 0x8A9096;
    private static final int BLACK = 0x15181C;
    private static final int WHITE = 0xF0F2F4;
    private static final int RED = 0xE02424;
    private static final int LAMP_OFF = 0x2A1010;

    @Override
    public void render(TileCrossing te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        if (te.getWorld() == null) return;
        EnumFacing f = te.facing();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.rotate(-f.getHorizontalIndex() * 90f, 0, 1, 0);
        GlStateManager.scale(te.scale(), te.scale(), te.scale());
        GlStateManager.disableCull();
        Prims.begin();

        switch (te.kind()) {
            case SIGNAL -> renderSignal(te, 2.6f);
            case GATE -> { renderSignal(te, 2.2f); renderGate(te, partialTicks); }
            case CANTILEVER -> renderCantilever(te);
        }

        Prims.emissive(false);
        GlStateManager.enableLighting();
        GlStateManager.enableCull();
        Prims.end();
        GlStateManager.popMatrix();
    }

    /** Mast, crossbuck X, and the pair of alternating flashers under it. */
    private void renderSignal(TileCrossing te, float top) {
        Prims.box(-0.22, 0, -0.22, 0.22, 0.12, 0.22, 0x6E6E6E);            // footing
        Prims.box(-0.08, 0.12, -0.08, 0.08, top, 0.08, POST);              // mast

        // Crossbuck: two white boards crossed, "RAILROAD CROSSING" implied by the shape.
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, top - 0.05, -0.09);
        for (int s = -1; s <= 1; s += 2) {
            GlStateManager.pushMatrix();
            GlStateManager.rotate(45f * s, 0, 0, 1);
            Prims.plate(-0.55, -0.075, 0.55, 0.075, 0, 0.03, WHITE);
            Prims.plate(-0.55, -0.075, -0.40, 0.075, -0.004, 0.006, BLACK);
            Prims.plate(0.40, -0.075, 0.55, 0.075, -0.004, 0.006, BLACK);
            GlStateManager.popMatrix();
        }
        GlStateManager.popMatrix();

        // Flashers: two lamps on a bar, alternating, each with a black hood behind.
        float ly = top - 0.72f;
        Prims.box(-0.46, ly - 0.05, -0.06, 0.46, ly + 0.05, 0.04, POST_DARK);   // lamp bar
        drawFlasher(-0.34, ly, te.lampLeftOn());
        drawFlasher(0.34, ly, te.lampRightOn());
    }

    private void drawFlasher(double lx, double ly, boolean on) {
        Prims.box(lx - 0.15, ly - 0.15, -0.10, lx + 0.15, ly + 0.15, -0.04, BLACK);   // hood
        int col = on ? RED : LAMP_OFF;
        Prims.emissive(on);
        Prims.disc(lx, ly, -0.115, 0.105, col, 1f);
        if (on) Prims.glow(lx, ly, -0.14, 0.42, 0xFF3A3A);
        Prims.emissive(false);
        GlStateManager.enableLighting();
    }

    /** The arm: pivots down over ~3 seconds, red and white stripes, three lamps along it. */
    private void renderGate(TileCrossing te, float partial) {
        float p = te.gateProgress(partial);
        Prims.box(-0.16, 1.05, -0.16, 0.16, 1.45, 0.16, POST_DARK);              // gate mechanism housing
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 1.25, -0.10);
        // Arm is modelled lying along +X (horizontal). 90deg up when open (p=0), swinging down
        // to horizontal across the road when closed (p=1): rotate(90*(1-p)). Was rotate(-90*p),
        // which had it backwards -- horizontal when OPEN and pointing straight down when a train
        // was coming, so it never actually blocked the crossing.
        GlStateManager.rotate(90f * (1f - p), 0, 0, 1);
        double len = 4.2;
        // Striped arm, red/white in 0.6 block bands like the real thing.
        for (double s = 0; s < len; s += 0.6) {
            int col = ((int) (s / 0.6)) % 2 == 0 ? RED : WHITE;
            Prims.box(0.12 + s, -0.07, -0.05, 0.12 + Math.min(s + 0.6, len), 0.07, 0.05, col);
        }
        // Three lamps along the arm: outer pair flash with the mast, the tip stays lit.
        boolean l = te.lampLeftOn(), r = te.lampRightOn();
        drawArmLamp(0.55, l);
        drawArmLamp(len * 0.55, r);
        drawArmLamp(len - 0.15, te.active());
        GlStateManager.popMatrix();
    }

    private void drawArmLamp(double ax, boolean on) {
        int col = on ? RED : LAMP_OFF;
        Prims.emissive(on);
        Prims.disc(ax, 0, -0.065, 0.055, col, 1f);
        if (on) Prims.glow(ax, 0, -0.085, 0.22, 0xFF3A3A);
        Prims.emissive(false);
        GlStateManager.enableLighting();
    }

    /** Cantilever: mast, a truss arm over the road, and two flasher pairs hanging from it. */
    private void renderCantilever(TileCrossing te) {
        renderSignal(te, 4.2f);
        double reach = 4.0;
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 3.9, 0);
        Prims.box(0.06, -0.07, -0.07, reach, 0.07, 0.07, POST);                  // top chord
        Prims.box(0.06, -0.42, -0.07, reach, -0.28, 0.07, POST);                 // bottom chord
        for (double s = 0.3; s < reach; s += 0.55) {                              // diagonals
            Prims.box(s, -0.30, -0.05, s + 0.09, -0.05, 0.05, POST_DARK);
        }
        for (double s : new double[]{ reach * 0.45, reach * 0.85 }) {             // hanging flashers
            Prims.box(s - 0.05, -0.75, -0.05, s + 0.05, -0.40, 0.05, POST_DARK);
            Prims.box(s - 0.40, -0.92, -0.06, s + 0.40, -0.82, 0.04, POST_DARK);
            drawFlasher(s - 0.28, -0.87, te.lampLeftOn());
            drawFlasher(s + 0.28, -0.87, te.lampRightOn());
        }
        GlStateManager.popMatrix();
    }

    @Override
    public boolean isGlobalRenderer(TileCrossing te) {
        return false;
    }
}
