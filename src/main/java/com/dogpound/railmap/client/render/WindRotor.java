package com.dogpound.railmap.client.render;

import com.dogpound.railmap.grid.TileGrid;
import com.dogpound.railmap.grid.Wind;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.world.World;

/**
 * A tower wind turbine's nacelle and rotor (requested feature), drawn on top of its stacked tower: the
 * nacelle yaws slowly round to face the wind, the three blades turn at the real rpm (slow, a big rotor barely 12 rpm)
 * and pitch to feathered (edge-on) when it's stopped, braked or riding out a storm. White, red tips for aircraft.
 */
final class WindRotor {
    private WindRotor() {}

    private static final double[] NACELLE_L = {2.0, 2.8, 3.6}, NACELLE_H = {0.9, 1.2, 1.5};

    static void render(TileGrid te, double x, double y, double z, float pt) {
        World w = te.getWorld();
        int secs = te.towerSections();
        if (secs == 0) return;                                                     // a compact turbine: its own block model
        int sz = te.rotorSize();
        double r = TileGrid.ROTOR_R[sz], len = NACELLE_L[sz], h = NACELLE_H[sz], wd = h * 0.85;
        double now = w.getTotalWorldTime() + pt;

        // yaw: the drive turns it slowly to face where the wind comes from
        float[] yaw = te.anim().computeIfAbsent("windYaw", k -> new float[]{(float) Wind.from(w), (float) now});
        double target = Wind.from(w), d = ((target - yaw[0]) % 360 + 540) % 360 - 180;
        double dt = Math.max(0, Math.min(10, now - yaw[1]));
        yaw[1] = (float) now;
        yaw[0] += (float) Math.max(-0.3 * dt, Math.min(0.3 * dt, d));
        // rotor: eases up to speed and coasts down; pitch: feathers when it isn't turning
        float[] rot = te.anim().computeIfAbsent("windRot", k -> new float[]{0, 0, 80});    // angle, deg per tick, pitch
        double want = te.rotorRpm() * 360 / 1200.0;
        rot[1] += (float) ((want - rot[1]) * Math.min(1, 0.01 * dt));
        rot[0] = (float) ((rot[0] + rot[1] * dt) % 360);
        double wantPitch = te.rotorTurning() ? 4 : 82;
        rot[2] += (float) Math.max(-0.6 * dt, Math.min(0.6 * dt, wantPitch - rot[2]));

        int l = w.getCombinedLight(te.getPos().up(secs + 1), 0);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y + 1 + secs, z + 0.5);
        GlStateManager.disableCull();
        GlStateManager.disableLighting();                                              // true white paint, lit by the world light only
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, l & 0xFFFF, l >> 16);
        Prims.begin();
        Prims.box(-0.42, 0, -0.42, 0.42, 0.18, 0.42, 0x6E7276);                       // yaw bearing on top of the tower
        GlStateManager.rotate((float) -yaw[0], 0, 1, 0);
        // nacelle: front (rotor) toward +z, which the yaw turns into the wind
        double zb = -len * 0.4, zf = len * 0.6, yb = 0.18;
        Prims.box(-wd / 2, yb, zb, wd / 2, yb + h, zf, 0xE9EAEC);                     // the housing
        Prims.box(-wd / 2 - 0.02, yb + h * 0.62, zb + 0.15, wd / 2 + 0.02, yb + h * 0.66, zf - 0.2, 0xC9CBCE);   // panel seam
        Prims.box(-wd / 2 + 0.1, yb + h, zb + 0.2, wd / 2 - 0.1, yb + h + 0.06, zb + len * 0.35, 0xCFD2D5);     // cooler on the roof
        Prims.box(-0.05, yb + h + 0.06, zb + 0.25, 0.05, yb + h + 0.45, zb + 0.33, 0x8A8E92);  // anemometer mast
        Prims.box(-0.12, yb + h + 0.45, zb + 0.24, 0.12, yb + h + 0.5, zb + 0.34, 0x5A5E62);
        Prims.emissive(true);
        boolean blink = ((long) now / 30 & 1) == 0;                                    // red aviation light on the roof
        Prims.box(-0.08, yb + h + 0.06, zb + 0.02, 0.08, yb + h + 0.2, zb + 0.16, blink ? 0xFF2020 : 0x501010);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, l & 0xFFFF, l >> 16);
        // hub + spinner
        double hy = yb + h * 0.5;
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, hy, zf);
        Prims.box(-h * 0.36, -h * 0.36, 0, h * 0.36, h * 0.36, 0.3, 0xE2E3E5);             // hub
        Prims.box(-h * 0.26, -h * 0.26, 0.3, h * 0.26, h * 0.26, 0.52, 0xEDEEF0);          // spinner nose
        Prims.box(-h * 0.14, -h * 0.14, 0.52, h * 0.14, h * 0.14, 0.62, 0xF2F3F4);
        GlStateManager.translate(0, 0, 0.16);
        GlStateManager.rotate(rot[0], 0, 0, 1);
        for (int k = 0; k < 3; k++) {
            GlStateManager.pushMatrix();
            GlStateManager.rotate(120f * k, 0, 0, 1);
            GlStateManager.rotate(rot[2], 0, 1, 0);                                      // blade pitch
            double r0 = h * 0.3, chord = Math.max(0.28, r * 0.11);
            for (int i = 0; i < 6; i++) {                                                // tapered blade, red tip
                double a = r0 + (r - r0) * i / 6.0, b = r0 + (r - r0) * (i + 1) / 6.0, c = chord * (1 - i * 0.13), t = 0.09 * (1 - i * 0.1);
                Prims.box(-c * 0.35, a, -t, c * 0.65, b, t, i == 5 ? 0xC42222 : 0xEEEFF1);
            }
            GlStateManager.popMatrix();
        }
        GlStateManager.popMatrix();
        Prims.end();
        GlStateManager.enableLighting();
        GlStateManager.enableCull();
        GlStateManager.popMatrix();
    }
}
