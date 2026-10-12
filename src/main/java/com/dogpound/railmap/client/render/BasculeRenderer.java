package com.dogpound.railmap.client.render;

import com.dogpound.railmap.track.TileMovableBridge;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;

/**
 * The bascule leaf of a Movable Bridge desk (her ChatGPT batch 3 #8), drawn live between the HEEL and TOE rails it was
 * wrenched to: two plate girders with stiffeners, cross girders, the deck plate, the running rails, handrails, the
 * trunnion bearings and the counterweight below the heel that swings down as the leaf rises to 80 degrees, plus the
 * marine lights on the nose (red closed / green open). Block units; the leaf's own frame is x along the span.
 */
public class BasculeRenderer extends TileEntitySpecialRenderer<TileMovableBridge> {
    private static final int STEEL = 0x4E6A7A, DARK = 0x2A3238, DECK = 0x5A5F63, RAIL = 0x8A8C8E, CONC = 0x9A9A94, YEL = 0xE8C020;

    @Override
    public void render(TileMovableBridge te, double x, double y, double z, float pt, int destroy, float alpha) {
        if (te.getWorld() == null || !te.bascule()) return;
        double[] s = te.span();
        double dx = s[3] - s[0], dz = s[4] - s[2], L = Math.hypot(dx, dz);
        if (L < 2 || L > 48) return;
        te.drawFrac += (te.posFrac - te.drawFrac) * 0.2;
        double tilt = 80 * te.drawFrac;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + s[0] - te.getPos().getX(), y + s[1] - te.getPos().getY() + 0.08, z + s[2] - te.getPos().getZ());
        GlStateManager.rotate((float) Math.toDegrees(Math.atan2(-dz, dx)), 0, 1, 0);
        GlStateManager.disableLighting();
        Prims.begin();
        // fixed: trunnion piers and bearings at the heel
        for (double side : new double[]{-1.9, 1.9}) {
            Prims.box(-1.2, -3.0, side - 0.45, 0.6, -0.55, side + 0.45, CONC);
            Prims.box(-0.45, -0.85, side - 0.5, 0.45, -0.1, side + 0.5, DARK);
        }
        GlStateManager.rotate((float) tilt, 0, 0, 1);
        // the leaf
        for (double side : new double[]{-1.55, 1.55}) {
            Prims.box(0, -0.9, side - 0.12, L, 0.25, side + 0.12, STEEL);                 // main plate girder
            Prims.box(0, 0.25, side - 0.3, L, 0.35, side + 0.3, STEEL);                    // top flange
            Prims.box(0, -1.0, side - 0.3, L, -0.9, side + 0.3, STEEL);                    // bottom flange
            for (double d = 0.8; d < L - 0.2; d += 1.2) Prims.box(d, -0.9, side - 0.22, d + 0.08, 0.25, side + 0.22, DARK);   // stiffeners
            Prims.box(0, 0.35, side + (side > 0 ? 0.05 : -0.1), L, 1.25, side + (side > 0 ? 0.1 : -0.05), YEL);   // handrail post line
            Prims.box(0, 1.2, side - 0.08, L, 1.3, side + 0.08, YEL);                      // handrail
        }
        for (double d = 0.5; d < L; d += 1.5) Prims.box(d, -0.6, -1.45, d + 0.25, -0.15, 1.45, DARK);   // cross girders
        Prims.box(0, -0.15, -1.43, L, -0.05, 1.43, DECK);                                // deck plate
        for (double r : new double[]{-0.72, 0.72}) {
            Prims.box(0, -0.05, r - 0.04, L, 0.1, r + 0.04, RAIL);                         // running rails
            Prims.box(0, -0.05, r - 0.09, L, -0.02, r + 0.09, DARK);                       // rail base plates
        }
        Prims.box(L - 0.25, -1.0, -1.7, L, 0.35, 1.7, STEEL);                            // nose end plate
        // tail + counterweight below the heel (swings down as the leaf rises)
        Prims.box(-3.2, -0.9, -1.6, 0, 0.25, 1.6, STEEL);
        Prims.box(-3.4, -2.6, -1.5, -1.0, -0.9, 1.5, CONC);
        Prims.box(-3.45, -2.0, -1.55, -0.95, -1.8, 1.55, YEL);                           // painted band on the counterweight
        // marine lights on the nose
        boolean open = te.isOpen();
        Prims.emissive(true);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        for (double side : new double[]{-1.7, 1.7}) Prims.box(L - 0.4, -0.7, side - 0.15, L - 0.1, -0.4, side + 0.15, open ? 0x30FF50 : 0xFF2020);
        Prims.end();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    @Override public boolean isGlobalRenderer(TileMovableBridge te) { return true; }
}
