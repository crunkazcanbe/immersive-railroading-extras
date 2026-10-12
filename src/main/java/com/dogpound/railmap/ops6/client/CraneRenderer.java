package com.dogpound.railmap.ops6.client;

import com.dogpound.railmap.client.render.Prims;
import com.dogpound.railmap.ops6.TileRailCrane;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The 250 t crane's moving parts, live (model px, north-facing like its block model): the four outrigger beams sliding
 * out of the carrier with their jacks and pads, and the slewing superstructure - turntable, cab, engine house,
 * counterweight, A-frame and luffing ropes - with the telescopic jib at its luffing angle, the hoist rope and the hook block
 * hanging from the jib head (and the chains when it is rigged).
 */
final class CraneRenderer extends TileEntitySpecialRenderer<TileRailCrane> {
    private static final Map<TileRailCrane, float[]> EASE = new WeakHashMap<>();

    static int turns(net.minecraft.util.EnumFacing f) { return f.getHorizontalIndex() == 2 ? 0 : f.getHorizontalIndex() == 3 ? 1 : f.getHorizontalIndex() == 0 ? 2 : 3; }

    @Override
    public void render(TileRailCrane te, double x, double y, double z, float pt, int destroy, float alpha) {
        if (te.getWorld() == null || x * x + y * y + z * z > 160 * 160) return;
        float[] e = EASE.computeIfAbsent(te, k -> new float[]{te.slew, te.luff, te.drop, te.outr});
        e[0] += wrap(te.slew - e[0]) * 0.3f;
        e[1] += (te.luff - e[1]) * 0.3f;
        e[2] += (te.drop - e[2]) * 0.3f;
        e[3] += (te.outr - e[3]) * 0.3f;
        int l = te.getWorld().getCombinedLight(te.getPos().up(), 0);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.rotate(-90f * turns(te.controlFacing()), 0, 1, 0);
        GlStateManager.translate(-0.5, 0, -0.5);
        GlStateManager.scale(1 / 16.0, 1 / 16.0, 1 / 16.0);
        GlStateManager.disableCull();
        GlStateManager.disableLighting();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, l & 0xFFFF, l >> 16);
        Prims.begin();
        // outriggers: beams slide out sideways (z) from the carrier at x = -8 and 24, jacks down onto pads
        double ext = e[3] * 22;
        for (double bx : new double[]{-9, 23}) {
            for (int side = -1; side <= 1; side += 2) {
                double z0 = side < 0 ? 2 - ext : 14, z1 = side < 0 ? 2 : 14 + ext;
                Prims.box(bx, 4.2, z0, bx + 2.4, 6.2, z1, 0xE8B818);
                double jz = side < 0 ? z0 : z1 - 2.6;
                if (ext > 2) {
                    Prims.box(bx - 0.2, 0.6, jz, bx + 2.6, 4.2, jz + 2.6, 0x3A3F44);   // jack cylinder
                    Prims.box(bx - 1.2, 0, jz - 1, bx + 3.6, 0.6, jz + 3.6, 0x2A2A2A);  // pad
                }
            }
        }
        // the superstructure, slewing about the block centre on top of the carrier deck (y 14)
        GlStateManager.translate(8, 14, 8);
        GlStateManager.rotate(-e[0], 0, 1, 0);
        Prims.box(-7, 0, -7, 7, 1.2, 7, 0x2A2A2A);                               // slew ring
        Prims.box(-9, 1.2, -10, 9, 3.0, 12, 0xE8B818);                           // turntable deck (jib points -z)
        Prims.box(-9, 3.0, 4, 9, 12, 14, 0xE0B010);                              // engine / hydraulic house
        for (double lz = 5; lz < 13; lz += 1.6) Prims.box(9, 6, lz, 9.3, 10, lz + 0.9, 0x303030);   // louvres
        Prims.box(-8.5, 1.2, 14, 8.5, 9, 19, 0x3A3F44);                          // counterweight slabs
        for (double cy = 2.8; cy < 9; cy += 2) Prims.box(-8.6, cy, 18.9, 8.6, cy + 0.2, 19.1, 0x22262A);
        Prims.box(-9, 3.0, -9, -3, 11, -2, 0xE8B818);                            // operator's cab
        Prims.box(-8.9, 6.5, -9.1, -3.1, 10.4, -8.9, 0x203040);                  // cab windscreen
        Prims.box(-3.1, 6.5, -8.8, -2.9, 10.4, -2.5, 0x203040);
        Prims.box(3, 12, 6, 4.5, 26, 7.5, 0xD0A010); Prims.box(-4.5, 12, 6, -3, 26, 7.5, 0xD0A010);   // A-frame legs
        Prims.box(-4.5, 26, 5.5, 4.5, 27.6, 8, 0xD0A010);                         // A-frame head + sheaves
        Prims.emissive(true);
        Prims.box(-6, 11.2, -2, -5, 12, -1, 0xFF8A00);                            // beacon
        Prims.emissive(false);
        // the jib: foot pin at (0, 6, -4), luffing up toward -z
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 6, -4);
        GlStateManager.rotate(-e[1], 1, 0, 0);
        double L = TileRailCrane.JIB * 16;
        Prims.box(-2.6, -1.8, -L * 0.55, 2.6, 2.2, 2, 0xE8B818);                  // base section
        Prims.box(-2.1, -1.4, -L * 0.82, 2.1, 1.8, -L * 0.5, 0xF0C020);             // 2nd section
        Prims.box(-1.7, -1.1, -L, 1.7, 1.4, -L * 0.78, 0xF8C828);                  // fly section
        for (double s = -L * 0.5; s > -L * 0.55; s -= 1) Prims.box(-2.7, 2.2, s, 2.7, 2.4, s + 0.4, 0x202020);
        Prims.box(-2.0, -2.6, -L - 1.6, 2.0, 2.6, -L + 0.6, 0x3A3F44);             // jib head + sheaves
        Prims.box(-0.6, -3.4, -L - 0.4, 0.6, -2.6, -L + 0.4, 0x8A8E92);
        GlStateManager.popMatrix();
        // luffing rope from the A-frame head to the jib head (straight line approximated by boxes)
        double lr = Math.toRadians(e[1]);
        double hx = 0, hy = 6 + Math.sin(lr) * L, hz = -4 - Math.cos(lr) * L;
        rope(0, 27, 6.6, hx, hy + 2.4, hz, 0x9A9A9A);
        // the hoist rope + hook block (vertical from the jib head)
        double drop = e[2] * 16;
        double hookTop = hy - 3.4, hookY = hookTop - Math.max(4, drop);
        Prims.box(-0.15, hookY + 3, hz - 0.15, 0.15, hookTop, hz + 0.15, 0x9A9A9A);
        Prims.box(-0.25, hookY + 3, hz - 0.6, 0.25, hookTop, hz - 0.3, 0x9A9A9A);
        Prims.box(-1.6, hookY, hz - 1.4, 1.6, hookY + 3.2, hz + 1.4, 0xE8B818);       // hook block
        Prims.box(-0.4, hookY - 1.6, hz - 0.4, 0.4, hookY, hz + 0.4, 0x404448);       // hook
        if (te.controls().get("rig") == 1) {
            for (int sx = -1; sx <= 1; sx += 2) Prims.box(sx * 0.3 - 0.1, hookY - 14, hz - 0.1, sx * 0.3 + 0.1, hookY - 1.6, hz + 0.1, 0x6A6A6A);   // chains to the car
        }
        Prims.end();
        GlStateManager.enableLighting();
        GlStateManager.enableCull();
        GlStateManager.popMatrix();
    }

    /** a rope between two points (y/z plane) as short boxes */
    static void rope(double x0, double y0, double z0, double x1, double y1, double z1, int col) {
        int n = 24;
        for (int i = 0; i < n; i++) {
            double a = (double) i / n, b = (double) (i + 1) / n;
            double ya = y0 + (y1 - y0) * a, za = z0 + (z1 - z0) * a, yb = y0 + (y1 - y0) * b, zb = z0 + (z1 - z0) * b;
            Prims.box(x0 - 0.12, Math.min(ya, yb) - 0.12, Math.min(za, zb) - 0.12, x0 + 0.12, Math.max(ya, yb) + 0.12, Math.max(za, zb) + 0.12, col);
        }
    }

    static float wrap(float a) { a %= 360; if (a > 180) a -= 360; if (a < -180) a += 360; return a; }

    @Override public boolean isGlobalRenderer(TileRailCrane te) { return true; }
}
