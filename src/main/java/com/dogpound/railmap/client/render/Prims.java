package com.dogpound.railmap.client.render;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/**
 * Untextured coloured boxes and discs for the signal hardware.
 * <p>
 * Signals are painted steel and glass: flat colour with a little face shading reads better
 * than a stretched 16px texture, it costs no atlas space, and it lets a lamp be genuinely
 * emissive. Everything here is drawn in block space (1.0 = one block) around the caller's
 * current matrix.
 */
public final class Prims {
    private static final Tessellator TESS = Tessellator.getInstance();

    private Prims() {}

    public static void begin() {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
    }

    public static void end() {
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
    }

    /** Full-bright: for anything that is its own light source. */
    public static void emissive(boolean on) {
        if (on) {
            GlStateManager.disableLighting();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        }
    }

    /**
     * Axis-aligned box from (x0,y0,z0) to (x1,y1,z1) in {@code rgb}, with the top face lightened
     * and the bottom darkened so edges read without a texture.
     */
    public static void box(double x0, double y0, double z0, double x1, double y1, double z1, int rgb, float alpha) {
        BufferBuilder b = TESS.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        int top = shade(rgb, 1.15f), bot = shade(rgb, 0.62f);
        int ns = shade(rgb, 0.88f), ew = shade(rgb, 0.78f);
        // top
        quad(b, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, top, alpha);
        // bottom
        quad(b, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, bot, alpha);
        // north (-z) / south (+z)
        quad(b, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, ns, alpha);
        quad(b, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, ns, alpha);
        // west (-x) / east (+x)
        quad(b, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, ew, alpha);
        quad(b, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, ew, alpha);
        TESS.draw();
    }

    public static void box(double x0, double y0, double z0, double x1, double y1, double z1, int rgb) {
        box(x0, y0, z0, x1, y1, z1, rgb, 1f);
    }

    /** A flat disc in the XY plane at {@code z}, for lamp lenses. */
    public static void disc(double cx, double cy, double z, double r, int rgb, float alpha) {
        BufferBuilder b = TESS.getBuffer();
        b.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        vert(b, cx, cy, z, rgb, alpha);
        int segs = 18;
        for (int i = 0; i <= segs; i++) {
            double a = i * Math.PI * 2 / segs;
            vert(b, cx + Math.cos(a) * r, cy + Math.sin(a) * r, z, rgb, alpha);
        }
        TESS.draw();
    }

    /** Soft halo around a lit lamp, so it reads at distance and at night. */
    public static void glow(double cx, double cy, double z, double r, int rgb) {
        BufferBuilder b = TESS.getBuffer();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO);
        b.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        vert(b, cx, cy, z, rgb, 0.55f);
        int segs = 18;
        for (int i = 0; i <= segs; i++) {
            double a = i * Math.PI * 2 / segs;
            vert(b, cx + Math.cos(a) * r, cy + Math.sin(a) * r, z, rgb, 0f);
        }
        TESS.draw();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
    }

    /** A thin plate in the XY plane — sign faces, backing boards, semaphore blades. */
    public static void plate(double x0, double y0, double x1, double y1, double z, double thick, int rgb) {
        box(x0, y0, z, x1, y1, z + thick, rgb);
    }

    // ---- internals -----------------------------------------------------------------------

    private static void quad(BufferBuilder b, double ax, double ay, double az, double bx, double by, double bz,
                             double cx, double cy, double cz, double dx, double dy, double dz, int rgb, float a) {
        vert(b, ax, ay, az, rgb, a);
        vert(b, bx, by, bz, rgb, a);
        vert(b, cx, cy, cz, rgb, a);
        vert(b, dx, dy, dz, rgb, a);
    }

    private static void vert(BufferBuilder b, double x, double y, double z, int rgb, float a) {
        b.pos(x, y, z).color((rgb >> 16) & 0xff, (rgb >> 8) & 0xff, rgb & 0xff, (int) (a * 255)).endVertex();
    }

    public static int shade(int rgb, float f) {
        int r = Math.min(255, (int) (((rgb >> 16) & 0xff) * f));
        int g = Math.min(255, (int) (((rgb >> 8) & 0xff) * f));
        int b = Math.min(255, (int) ((rgb & 0xff) * f));
        return (r << 16) | (g << 8) | b;
    }
}
