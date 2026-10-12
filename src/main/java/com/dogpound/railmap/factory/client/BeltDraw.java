package com.dogpound.railmap.factory.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Moving belt surfaces: the belt texture scrolls along a path (any polyline - straight, round a curve, up a ramp) by
 * exactly how far the belt has run, so it creeps at the real belt speed and stands still when the belt stops.
 */
public final class BeltDraw {
    private BeltDraw() {}
    public static final ResourceLocation STRIP = new ResourceLocation(com.dogpound.railmap.RailMap.MODID, "blocks/belt_strip");
    public static final double TILE = 0.5;                    // blocks of belt per texture repeat (one cleat)

    public static TextureAtlasSprite sprite() { return Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(STRIP.toString()); }

    /** begin a belt batch (block units, GL already translated); light = packed combined light */
    public static BufferBuilder begin(int light) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.color(1f, 1f, 1f, 1f);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light & 0xFFFF, light >> 16 & 0xFFFF);
        BufferBuilder bb = Tessellator.getInstance().getBuffer();
        bb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        return bb;
    }

    public static void end() {
        Tessellator.getInstance().draw();
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
    }

    /**
     * A belt along points pts[i] = {x,y,z}, side[i] = half-width vector {x,y,z} at that point; `run` = how far the belt
     * has travelled (blocks). The texture moves from the first point towards the last.
     */
    public static void path(BufferBuilder bb, double[][] pts, double[][] side, double run, int shade) {
        TextureAtlasSprite sp = sprite();
        double s = 0;
        for (int i = 0; i + 1 < pts.length; i++) {
            double[] a = pts[i], b = pts[i + 1];
            double len = Math.sqrt(sq(b[0] - a[0]) + sq(b[1] - a[1]) + sq(b[2] - a[2]));
            if (len < 1e-6) continue;
            double t = 0;
            while (t < len - 1e-6) {
                double v = frac((s + t - run) / TILE);
                double t1 = Math.min(len, t + (1 - v) * TILE);
                double v1 = v + (t1 - t) / TILE;
                double k0 = t / len, k1 = t1 / len;
                double[] p0 = lerp(a, b, k0), p1 = lerp(a, b, k1), s0 = lerp(side[i], side[i + 1], k0), s1 = lerp(side[i], side[i + 1], k1);
                float u0 = sp.getMinU(), u1 = sp.getMaxU();
                float va = sp.getInterpolatedV(v * 16), vb = sp.getInterpolatedV(Math.min(16, v1 * 16));
                bb.pos(p0[0] - s0[0], p0[1] - s0[1], p0[2] - s0[2]).tex(u0, va).color(shade, shade, shade, 255).endVertex();
                bb.pos(p1[0] - s1[0], p1[1] - s1[1], p1[2] - s1[2]).tex(u0, vb).color(shade, shade, shade, 255).endVertex();
                bb.pos(p1[0] + s1[0], p1[1] + s1[1], p1[2] + s1[2]).tex(u1, vb).color(shade, shade, shade, 255).endVertex();
                bb.pos(p0[0] + s0[0], p0[1] + s0[1], p0[2] + s0[2]).tex(u1, va).color(shade, shade, shade, 255).endVertex();
                t = t1;
            }
            s += len;
        }
    }

    /** a straight belt from a to b, `half` wide either side, lying flat across (side vector horizontal) */
    public static void straight(BufferBuilder bb, double[] a, double[] b, double half, double run, int shade) {
        double dx = b[0] - a[0], dz = b[2] - a[2], h = Math.sqrt(dx * dx + dz * dz);
        double[] sd = h < 1e-6 ? new double[]{half, 0, 0} : new double[]{-dz / h * half, 0, dx / h * half};
        path(bb, new double[][]{a, b}, new double[][]{sd, sd}, run, shade);
    }

    /** a flat-shaded box (block units) in one colour: hood plates, mouths */
    public static void box(BufferBuilder bb, TextureAtlasSprite sp, double x0, double y0, double z0, double x1, double y1, double z1, int r, int g, int b) {
        float u0 = sp.getMinU(), u1 = sp.getMaxU(), v0 = sp.getMinV(), v1 = sp.getMaxV();
        quad(bb, u0, v0, u1, v1, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, b, 1.0f);   // up
        quad(bb, u0, v0, u1, v1, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, 0.5f);   // down
        quad(bb, u0, v0, u1, v1, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b, 0.8f);   // north
        quad(bb, u0, v0, u1, v1, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, r, g, b, 0.8f);   // south
        quad(bb, u0, v0, u1, v1, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, r, g, b, 0.6f);   // west
        quad(bb, u0, v0, u1, v1, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b, 0.6f);   // east
    }

    static void quad(BufferBuilder bb, float u0, float v0, float u1, float v1,
                     double ax, double ay, double az, double bx, double by, double bz, double cx, double cy, double cz, double dx, double dy, double dz,
                     int r, int g, int b, float k) {
        int R = (int) (r * k), G = (int) (g * k), B = (int) (b * k);
        bb.pos(ax, ay, az).tex(u0, v0).color(R, G, B, 255).endVertex();
        bb.pos(bx, by, bz).tex(u0, v1).color(R, G, B, 255).endVertex();
        bb.pos(cx, cy, cz).tex(u1, v1).color(R, G, B, 255).endVertex();
        bb.pos(dx, dy, dz).tex(u1, v0).color(R, G, B, 255).endVertex();
    }

    private static double sq(double v) { return v * v; }
    private static double frac(double v) { return v - Math.floor(v); }
    private static double[] lerp(double[] a, double[] b, double k) { return new double[]{a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k}; }
}
