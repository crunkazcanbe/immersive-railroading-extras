package com.dogpound.railmap.client.render;

import com.dogpound.railmap.signal.Aspect;
import com.dogpound.railmap.signal.SignalStyle;
import com.dogpound.railmap.signal.TileSignalMast;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;

/**
 * Draws every American signal family. The mast, ladder and head castings are painted steel;
 * the lamps are emissive with a halo, so a red board is visible down the line at night the way
 * it should be.
 * <p>
 * Local space after the setup below: +X is the signal's right, +Y up, −Z toward the train
 * the signal is talking to (its facing). One unit = one block.
 */
public class SignalMastRenderer extends TileEntitySpecialRenderer<TileSignalMast> {
    private static final int STEEL = 0x54585E;
    private static final int STEEL_DARK = 0x3A3E44;
    private static final int HOOD = 0x1C1F23;
    private static final int PLATE_WHITE = 0xD8DCE0;
    private static final int BLADE_RED = 0xC62828;
    private static final int BLADE_WHITE = 0xF2F2F2;

    @Override
    public void render(TileSignalMast te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        if (te.getWorld() == null) return;
        SignalStyle style = te.style();
        Aspect aspect = te.aspect();
        long ticks = te.getWorld().getTotalWorldTime();
        EnumFacing f = te.facing();

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.rotate(-f.getHorizontalIndex() * 90f, 0, 1, 0);
        GlStateManager.disableCull();
        Prims.begin();

        float mastTop = style.isDwarf() ? 0.55f : style.mastHeight;
        if (!style.isDwarf()) {
            drawMast(mastTop);
        } else {
            Prims.box(-0.22, 0, -0.16, 0.22, 0.12, 0.16, STEEL_DARK);   // ballast base
        }

        int heads = te.heads();
        float headY = style.isDwarf() ? 0.18f : mastTop - 0.75f;
        for (int h = 0; h < heads; h++) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(0, headY - h * 0.85f, 0);
            switch (style.render) {
                case LAMPS -> drawColorLightHead(aspect, h, ticks, style.isDwarf());
                case SEARCHLIGHT -> drawSearchlightHead(aspect, h, ticks);
                case POSITION -> drawPositionHead(aspect, h, ticks);
                case COLOR_POSITION -> drawColorPositionHead(aspect, h, ticks);
                case SEMAPHORE -> drawSemaphoreHead(aspect, h, ticks, partialTicks);
            }
            GlStateManager.popMatrix();
        }

        // Number plate: the mark of a permissive signal a train may pass at restricted speed.
        if (te.permissive() && !style.isDwarf()) {
            Prims.plate(-0.12, headY - heads * 0.85f - 0.18, 0.12, headY - heads * 0.85f + 0.02, -0.10, 0.02, PLATE_WHITE);
        }

        Prims.emissive(false);
        GlStateManager.enableLighting();
        GlStateManager.enableCull();
        Prims.end();
        GlStateManager.popMatrix();
    }

    /** Tapered mast with a ladder up the back and a concrete footing. */
    private void drawMast(float top) {
        Prims.box(-0.20, 0, -0.20, 0.20, 0.10, 0.20, 0x6E6E6E);           // footing
        Prims.box(-0.09, 0.10, -0.09, 0.09, top, 0.09, STEEL);            // mast
        for (float r = 0.35f; r < top - 0.2f; r += 0.28f) {                // ladder rungs
            Prims.box(-0.16, r, 0.09, 0.16, r + 0.045, 0.13, STEEL_DARK);
        }
        Prims.box(-0.02, 0.10, 0.11, 0.02, top - 0.2, 0.15, STEEL_DARK);   // ladder rail
    }

    /** Three lamps in a vertical row behind a hood, on a round backing plate. */
    private void drawColorLightHead(Aspect aspect, int head, long ticks, boolean dwarf) {
        double s = dwarf ? 0.72 : 1.0;
        double w = 0.19 * s, hgt = 0.60 * s;
        Prims.box(-w, -hgt / 2, -0.05, w, hgt / 2, 0.06, STEEL_DARK);                  // casting
        Prims.plate(-w - 0.06, -hgt / 2 - 0.05, w + 0.06, hgt / 2 + 0.05, 0.06, 0.02, HOOD);  // backing plate

        Aspect.Lamp[] lamps = { aspect.lamp(head), null, null };
        // A colour-light head shows one colour at a time; the other two lenses stay dark.
        Aspect.Lamp shown = lamps[0];
        for (int i = 0; i < 3; i++) {
            double ly = hgt / 2 - 0.12 * s - i * 0.19 * s;
            boolean lit = matchesLens(shown, i) && shown.litAt(ticks);
            int col = lit ? shown.rgb : 0x14161A;
            Prims.box(-0.13 * s, ly - 0.075 * s, -0.11, 0.13 * s, ly + 0.075 * s, -0.05, HOOD);  // hood over the lens
            Prims.emissive(lit);
            Prims.disc(0, ly, -0.115, 0.072 * s, col, 1f);
            if (lit) Prims.glow(0, ly, -0.13, 0.26 * s, col);
            Prims.emissive(false);
            GlStateManager.enableLighting();
        }
    }

    /** Lens row order on a US colour-light head: green top, yellow middle, red bottom. */
    private static boolean matchesLens(Aspect.Lamp lamp, int lens) {
        switch (lamp) {
            case GREEN: return lens == 0;
            case YELLOW:
            case YELLOW_FLASH: return lens == 1;
            case RED: return lens == 2;
            case LUNAR: return lens == 1;
            default: return false;
        }
    }

    /** One lens that changes colour: a moving roundel behind the glass. Classic searchlight. */
    private void drawSearchlightHead(Aspect aspect, int head, long ticks) {
        Aspect.Lamp lamp = aspect.lamp(head);
        boolean lit = lamp.litAt(ticks);
        int col = lit ? lamp.rgb : 0x14161A;
        Prims.box(-0.17, -0.17, -0.05, 0.17, 0.17, 0.10, STEEL_DARK);                // round-ish casting
        Prims.plate(-0.30, -0.30, 0.30, 0.30, 0.10, 0.02, HOOD);                     // big backing plate
        Prims.box(-0.19, -0.02, -0.16, 0.19, 0.19, -0.05, HOOD);                     // visor
        Prims.emissive(lit);
        Prims.disc(0, 0, -0.16, 0.125, col, 1f);
        if (lit) Prims.glow(0, 0, -0.18, 0.42, col);
        Prims.emissive(false);
        GlStateManager.enableLighting();
    }

    /** PRR position light: amber lamps on a black disc; the ROW angle carries the meaning. */
    private void drawPositionHead(Aspect aspect, int head, long ticks) {
        Aspect.Lamp lamp = aspect.lamp(head);
        boolean lit = lamp != Aspect.Lamp.OFF && lamp.litAt(ticks);
        double angle = positionAngle(aspect);
        Prims.plate(-0.34, -0.34, 0.34, 0.34, 0.04, 0.03, 0x101215);                  // black disc
        Prims.box(-0.30, -0.30, -0.04, 0.30, 0.30, 0.20, STEEL_DARK);   // deep enough to read from the side
        int amber = 0xFFB300;
        double ca = Math.cos(Math.toRadians(angle)), sa = Math.sin(Math.toRadians(angle));
        Prims.emissive(lit);
        for (int i = -1; i <= 1; i++) {
            double lx = ca * i * 0.20, ly = sa * i * 0.20;
            int col = lit ? amber : 0x14161A;
            Prims.disc(lx, ly, -0.06, 0.055, col, 1f);
            if (lit) Prims.glow(lx, ly, -0.08, 0.17, col);
        }
        Prims.emissive(false);
        GlStateManager.enableLighting();
    }

    /** Row angle: vertical = clear, 45° = approach, horizontal = stop. Pure PRR. */
    private static double positionAngle(Aspect a) {
        switch (a) {
            case CLEAR:
            case MEDIUM_CLEAR: return 90;
            case APPROACH:
            case ADVANCE_APPROACH:
            case APPROACH_MEDIUM:
            case MEDIUM_APPROACH: return 45;
            default: return 0;
        }
    }

    /** B&amp;O colour position light: coloured pairs at angles, plus the orbital marker lamp. */
    private void drawColorPositionHead(Aspect aspect, int head, long ticks) {
        Aspect.Lamp lamp = aspect.lamp(head);
        boolean lit = lamp.litAt(ticks);
        double angle = positionAngle(aspect);
        int col = lit ? lamp.rgb : 0x14161A;
        Prims.plate(-0.32, -0.32, 0.32, 0.32, 0.04, 0.03, 0x101215);
        Prims.box(-0.28, -0.28, -0.04, 0.28, 0.28, 0.20, STEEL_DARK);   // deep enough to read from the side
        double ca = Math.cos(Math.toRadians(angle)), sa = Math.sin(Math.toRadians(angle));
        Prims.emissive(lit);
        for (int i = -1; i <= 1; i += 2) {
            double lx = ca * i * 0.19, ly = sa * i * 0.19;
            Prims.disc(lx, ly, -0.06, 0.06, col, 1f);
            if (lit) Prims.glow(lx, ly, -0.08, 0.20, col);
        }
        // Orbital: the small white lamp above, lit on the most favourable aspects.
        boolean orbital = aspect == Aspect.CLEAR || aspect == Aspect.MEDIUM_CLEAR;
        Prims.emissive(orbital);
        Prims.disc(0, 0.40, -0.06, 0.045, orbital ? 0xE8F0FF : 0x14161A, 1f);
        Prims.emissive(false);
        GlStateManager.enableLighting();
    }

    /** Upper-quadrant semaphore: the blade angle IS the aspect, with a spectacle lens behind. */
    private void drawSemaphoreHead(Aspect aspect, int head, long ticks, float partial) {
        double target = semaphoreAngle(aspect);
        Prims.box(-0.06, -0.10, -0.10, 0.06, 0.34, 0.06, STEEL_DARK);           // bearing casting
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0.12, -0.06);
        GlStateManager.rotate((float) target, 0, 0, 1);
        // Blade: red with a white stripe, notched end, in the plane facing the train.
        Prims.plate(0.02, -0.055, 0.86, 0.055, 0, 0.025, BLADE_RED);
        Prims.plate(0.62, -0.038, 0.80, 0.038, -0.004, 0.004, BLADE_WHITE);
        // Spectacle: the coloured lens that sits in front of the lamp as the blade moves.
        Aspect.Lamp lamp = aspect.lamp(head);
        boolean lit = lamp.litAt(ticks);
        int col = lit ? lamp.rgb : 0x14161A;
        Prims.emissive(lit);
        Prims.disc(-0.16, 0, -0.02, 0.075, col, 1f);
        if (lit) Prims.glow(-0.16, 0, -0.04, 0.24, col);
        Prims.emissive(false);
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    /** Horizontal = stop, 45° up = approach, 60° up = clear. Upper quadrant practice. */
    private static double semaphoreAngle(Aspect a) {
        switch (a) {
            case CLEAR:
            case MEDIUM_CLEAR: return 60;
            case APPROACH:
            case ADVANCE_APPROACH:
            case APPROACH_MEDIUM:
            case MEDIUM_APPROACH: return 45;
            default: return 0;
        }
    }

    @Override
    public boolean isGlobalRenderer(TileSignalMast te) {
        return false;
    }
}
