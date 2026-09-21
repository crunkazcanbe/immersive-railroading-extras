package com.dogpound.railmap.client.render;

import com.dogpound.railmap.signal.Aspect;
import com.dogpound.railmap.signal.TileSignalBridge;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;

/**
 * The gantry: two lattice legs, a trussed span between them, and a hooded head hanging over
 * each track underneath. Only the controlling leg draws — the far leg draws nothing, so the
 * structure is never doubled.
 */
public class SignalBridgeRenderer extends TileEntitySpecialRenderer<TileSignalBridge> {
    private static final int STEEL = 0x565B62;
    private static final int STEEL_DARK = 0x3B4046;
    private static final int HOOD = 0x191C20;
    private static final float DECK = 5.6f;   // height of the span above the leg base

    @Override
    public void render(TileSignalBridge te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        if (te.getWorld() == null) return;
        boolean owner = te.isController();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        float sc = te.scale();
        GlStateManager.scale(sc, sc, sc);   // wrench-set size: raises the whole gantry to clear tall stock
        EnumFacing f = te.facing();
        GlStateManager.rotate(-f.getHorizontalIndex() * 90f, 0, 1, 0);
        GlStateManager.disableCull();
        Prims.begin();

        // Every leg draws itself again. This was briefly conditional, back when a paired
        // gantry stamped real tower blocks and the drawn lattice would have doubled them --
        // that auto-build is gone, so without this a paired gantry would float with no legs.
        drawLeg();

        if (owner) {
            int span = te.span();
            drawSpan(span);
            long ticks = te.getWorld().getTotalWorldTime();
            for (TileSignalBridge.Head h : te.heads()) {
                GlStateManager.pushMatrix();
                // +X is the viewer's right, which is the direction the span runs.
                GlStateManager.translate(h.along, DECK - 0.55, 0);
                drawHead(h.aspect, ticks);
                GlStateManager.popMatrix();
            }
        }

        Prims.emissive(false);
        GlStateManager.enableLighting();
        GlStateManager.enableCull();
        Prims.end();
        GlStateManager.popMatrix();
    }

    /** Lattice leg: footing, four corner angles, cross bracing, and a cap plate. */
    private void drawLeg() {
        Prims.box(-0.34, 0, -0.34, 0.34, 0.14, 0.34, 0x6E6E6E);           // concrete footing
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                double cx = sx * 0.20, cz = sz * 0.20;
                Prims.box(cx - 0.045, 0.14, cz - 0.045, cx + 0.045, DECK, cz + 0.045, STEEL);
            }
        }
        // Bracing every ~0.7 blocks, alternating diagonal so it reads as a lattice.
        boolean flip = false;
        for (double h = 0.45; h < DECK - 0.3; h += 0.68) {
            double t = 0.035;
            if (flip) {
                Prims.box(-0.24, h, -0.24, 0.24, h + t * 2, -0.16, STEEL_DARK);
                Prims.box(-0.24, h + 0.3, 0.16, 0.24, h + 0.3 + t * 2, 0.24, STEEL_DARK);
            } else {
                Prims.box(-0.24, h, 0.16, 0.24, h + t * 2, 0.24, STEEL_DARK);
                Prims.box(-0.24, h + 0.3, -0.24, 0.24, h + 0.3 + t * 2, -0.16, STEEL_DARK);
            }
            Prims.box(-0.24, h, -0.045, 0.24, h + t * 2, 0.045, STEEL_DARK);   // through brace
            flip = !flip;
        }
        Prims.box(-0.28, DECK, -0.28, 0.28, DECK + 0.10, 0.28, STEEL_DARK);    // cap plate
    }

    /** Trussed span: top and bottom chords, verticals and diagonals, plus a walkway rail. */
    private void drawSpan(int span) {
        double x0 = 0.26, x1 = span - 0.26;
        if (x1 <= x0) return;
        double top = DECK + 0.62, bot = DECK + 0.06;
        for (int sz = -1; sz <= 1; sz += 2) {
            double cz = sz * 0.17;
            Prims.box(x0, top, cz - 0.05, x1, top + 0.10, cz + 0.05, STEEL);       // top chord
            Prims.box(x0, bot, cz - 0.05, x1, bot + 0.10, cz + 0.05, STEEL);       // bottom chord
        }
        for (double s = x0; s < x1 - 0.05; s += 0.85) {                             // verticals
            Prims.box(s, bot, -0.20, s + 0.075, top + 0.10, 0.20, STEEL_DARK);
        }
        for (double s = x0; s < x1 - 0.5; s += 0.85) {                              // diagonals (stepped)
            for (int k = 0; k < 4; k++) {
                double sx = s + 0.1 + k * 0.18;
                double sy = bot + 0.10 + k * 0.115;
                if (sx + 0.16 > x1) break;
                Prims.box(sx, sy, -0.185, sx + 0.16, sy + 0.085, 0.185, STEEL_DARK);
            }
        }
        // Walkway deck + handrail along the back, where a signal maintainer would stand.
        Prims.box(x0, bot - 0.10, 0.17, x1, bot - 0.04, 0.40, STEEL_DARK);
        Prims.box(x0, bot + 0.45, 0.38, x1, bot + 0.51, 0.42, STEEL);
    }

    /** A hooded three-lens head slung under the span, facing the oncoming train (−Z). */
    private void drawHead(Aspect aspect, long ticks) {
        Prims.box(-0.05, 0.50, -0.05, 0.05, 0.62, 0.05, STEEL_DARK);         // hanger
        Prims.box(-0.20, -0.02, -0.06, 0.20, 0.52, 0.07, STEEL_DARK);        // casting
        Prims.plate(-0.27, -0.08, 0.27, 0.58, 0.07, 0.025, HOOD);            // backing plate

        Aspect.Lamp lamp = aspect.lamp(0);
        for (int i = 0; i < 3; i++) {
            double ly = 0.40 - i * 0.19;
            boolean lit = lensFor(lamp, i) && lamp.litAt(ticks);
            int col = lit ? lamp.rgb : 0x14161A;
            Prims.box(-0.14, ly - 0.08, -0.12, 0.14, ly + 0.08, -0.055, HOOD);   // hood
            Prims.emissive(lit);
            Prims.disc(0, ly, -0.125, 0.072, col, 1f);
            if (lit) Prims.glow(0, ly, -0.145, 0.28, col);
            Prims.emissive(false);
            GlStateManager.enableLighting();
        }
    }

    /** Green top, yellow middle, red bottom — standard US lens order. */
    private static boolean lensFor(Aspect.Lamp lamp, int lens) {
        switch (lamp) {
            case GREEN: return lens == 0;
            case YELLOW:
            case YELLOW_FLASH:
            case LUNAR: return lens == 1;
            case RED: return lens == 2;
            default: return false;
        }
    }

    /** The span can be long, so keep drawing it while the controller leg is off-screen. */
    @Override
    public boolean isGlobalRenderer(TileSignalBridge te) {
        return true;
    }
}
