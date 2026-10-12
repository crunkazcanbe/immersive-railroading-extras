package com.dogpound.railmap.cine.client;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.cctv.client.CctvFeeds;
import com.dogpound.railmap.cine.CineConfig;
import com.dogpound.railmap.cine.CineMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.GL11;

/**
 * Photo mode's lens effects: depth-of-field blur (the frame is copied to a texture and laid back over itself in a ring
 * of offsets, faded in by a focus mask - TILT-SHIFT keeps a band across the middle sharp, PORTRAIT a centre oval; no
 * shaders, any GPU), cinema letterbox bars, and the clean screenshot (HUD off for that one frame, bars and blur kept).
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID, value = Side.CLIENT)
public final class CineFx {
    private CineFx() {}

    public static final String[] DOF = {"OFF", "TILT-SHIFT", "PORTRAIT", "STRONG"};
    private static int tex = -1, tw, th;
    private static int shot;
    private static boolean hideWas;
    public static boolean broken;

    /** take a screenshot of the next frame without the HUD */
    public static void screenshot() {
        Minecraft mc = Minecraft.getMinecraft();
        hideWas = mc.gameSettings.hideGUI;
        mc.gameSettings.hideGUI = true;
        shot = 2;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void world(RenderWorldLastEvent e) {
        if (!CineCam.active || CineCam.dof == 0 || !CineConfig.depthOfField || broken || CctvFeeds.rendering) return;
        try { blur(CineCam.dof); } catch (RuntimeException ex) { broken = true; RailMap.LOG.warn("[IR Extras] photo mode depth of field off: {}", ex.toString()); }
    }

    static void blur(int mode) {
        Minecraft mc = Minecraft.getMinecraft();
        Framebuffer fb = mc.getFramebuffer();
        int w = fb.framebufferWidth, h = fb.framebufferHeight;
        if (tex < 0 || tw != w || th != h) {
            if (tex >= 0) GL11.glDeleteTextures(tex);
            tex = GL11.glGenTextures();
            GlStateManager.bindTexture(tex);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            tw = w; th = h;
        }
        GlStateManager.bindTexture(tex);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);
        GlStateManager.matrixMode(GL11.GL_PROJECTION); GlStateManager.pushMatrix(); GlStateManager.loadIdentity();
        GlStateManager.ortho(0, 1, 0, 1, -1, 1);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW); GlStateManager.pushMatrix(); GlStateManager.loadIdentity();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.disableFog();
        GlStateManager.disableLighting();
        GlStateManager.disableAlpha();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1, 1, 1, 1);
        double r = (mode == 3 ? 7 : 4) * Math.max(1, h / 720.0);
        int taps = 8;
        for (int i = 0; i < taps; i++) {
            double a = i * Math.PI * 2 / taps + (i % 2) * 0.4, rr = r * (i % 2 == 0 ? 1 : 0.55);
            mesh(mode, Math.cos(a) * rr / w, Math.sin(a) * rr / h, 1.0 / (i + 2));
        }
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.matrixMode(GL11.GL_PROJECTION); GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW); GlStateManager.popMatrix();
    }

    /** the frame shifted by (du, dv), faded in by the focus mask: alpha = weight x mask */
    static void mesh(int mode, double du, double dv, double weight) {
        Tessellator ts = Tessellator.getInstance();
        BufferBuilder b = ts.getBuffer();
        int nx = 24, ny = 14;
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {
            double u0 = i / (double) nx, u1 = (i + 1) / (double) nx, v0 = j / (double) ny, v1 = (j + 1) / (double) ny;
            vtx(b, mode, u0, v0, du, dv, weight); vtx(b, mode, u1, v0, du, dv, weight);
            vtx(b, mode, u1, v1, du, dv, weight); vtx(b, mode, u0, v1, du, dv, weight);
        }
        ts.draw();
    }

    static void vtx(BufferBuilder b, int mode, double u, double v, double du, double dv, double weight) {
        b.pos(u, v, 0).tex(u + du, v + dv).color(1f, 1f, 1f, (float) (weight * mask(mode, u, v))).endVertex();
    }

    /** 0 = in focus, 1 = fully blurred */
    static double mask(int mode, double u, double v) {
        double m;
        if (mode == 1) m = Math.abs(v - 0.5) * 2;                                   // tilt-shift: sharp band across the middle
        else { double dx = (u - 0.5) * 1.6, dy = (v - 0.5) * 2; m = Math.sqrt(dx * dx + dy * dy); }   // portrait / strong: centre oval
        double lo = 0.25, hi = 0.8;
        double t = Math.max(0, Math.min(1, (m - lo) / (hi - lo)));
        return t * t * (3 - 2 * t);
    }

    /** letterbox bars over everything, and the screenshot once the frame (with bars, without HUD) is done */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void end(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (CineCam.active && CineCam.letterbox > 0 && mc.world != null) {
            ScaledResolution sr = new ScaledResolution(mc);
            int bar = CineMath.bar(mc.displayWidth, mc.displayHeight, CineMath.LETTERBOX[CineCam.letterbox]) * sr.getScaledHeight() / Math.max(1, mc.displayHeight);
            if (bar > 0) {
                mc.entityRenderer.setupOverlayRendering();
                GlStateManager.disableDepth();
                Gui.drawRect(0, 0, sr.getScaledWidth(), bar, 0xFF000000);
                Gui.drawRect(0, sr.getScaledHeight() - bar, sr.getScaledWidth(), sr.getScaledHeight(), 0xFF000000);
                GlStateManager.enableDepth();
            }
        }
        if (shot > 0 && --shot == 0) {
            ITextComponent msg = ScreenShotHelper.saveScreenshot(mc.gameDir, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
            mc.gameSettings.hideGUI = hideWas;
            if (mc.player != null) mc.player.sendMessage(msg);
        }
    }
}
