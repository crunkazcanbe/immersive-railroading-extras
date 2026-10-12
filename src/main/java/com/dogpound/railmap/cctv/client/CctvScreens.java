package com.dogpound.railmap.cctv.client;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.cctv.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.GL11;

import java.util.Locale;

/**
 * Draws the CCTV pictures on the glass of every monitor, video wall, ceiling monitor and the desk's spot monitor + 2 x 2
 * preview wall (live framebuffers, snapshots or playback through the CCTV shader, privacy masks, the on-screen overlay:
 * camera name, time, REC dot, mode), and the PTZ cameras' moving pan yoke + head turned to where the motors are.
 * Only screens within CctvConfig.liveRange and in view ask for pictures.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID, value = Side.CLIENT)
public final class CctvScreens {
    private CctvScreens() {}

    /** glass rects in the north-facing model (px): x0, y0, x1, y1, z */
    static final double[] WALL = {0.5, 2.2, 15.5, 15.5, 12.4}, CEIL = {1.0, 5.0, 15.0, 13.0, 6.9}, SPOT = {1.0, 16.0, 15.0, 26.0, 10.4}, QUAD = {-14.5, 15.0, -1.5, 28.0, 10.4};
    private static long lastNanos;
    private static IBakedModel panModel, headModel;

    @SubscribeEvent
    public static void render(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.getRenderViewEntity() == null) return;
        Entity v = mc.getRenderViewEntity();
        float pt = e.getPartialTicks();
        double cx = v.lastTickPosX + (v.posX - v.lastTickPosX) * pt, cy = v.lastTickPosY + (v.posY - v.lastTickPosY) * pt, cz = v.lastTickPosZ + (v.posZ - v.lastTickPosZ) * pt;
        Frustum fr = new Frustum();
        fr.setPosition(cx, cy, cz);
        long nanos = System.nanoTime();
        float dt = lastNanos == 0 || CctvFeeds.rendering ? 0 : Math.min(0.25f, (nanos - lastNanos) / 1e9f);
        if (!CctvFeeds.rendering) lastNanos = nanos;
        double range = CctvConfig.liveRange;
        try {
            for (TileEntity t : mc.world.loadedTileEntityList) {
                if (t.isInvalid()) continue;
                BlockPos p = t.getPos();
                double x = p.getX() - cx, y = p.getY() - cy, z = p.getZ() - cz, d2 = x * x + y * y + z * z;
                if (t instanceof TileCamera c) { if (d2 < 64 * 64 && c.type() == CamType.PTZ) ptz(mc, c, x, y, z, dt); continue; }
                if (d2 > range * range) continue;
                if (t instanceof TileMonitor m && m.isMaster()) {
                    int[] wl = TileMonitor.wall(mc.world, p);
                    AxisAlignedBB bb = new AxisAlignedBB(p).grow(wl[3] + 1, wl[4] + 1, wl[3] + 1);
                    if (fr.isBoundingBoxInFrustum(bb)) begin(m, x, y, z, () -> wall(mc, m, wl));
                } else if (t instanceof TileCctvDesk k && fr.isBoundingBoxInFrustum(new AxisAlignedBB(p).grow(2))) begin(k, x, y, z, () -> desk(mc, k));
            }
        } finally {
            GlStateManager.enableLighting();
            GlStateManager.enableCull();
            GlStateManager.enableAlpha();
            GlStateManager.disableBlend();
            GlStateManager.color(1, 1, 1, 1);
        }
    }

    /** into the tile's north-facing model frame (px), like ControlsRenderer */
    private static void begin(CctvTile t, double x, double y, double z, Runnable draw) {
        EnumFacing f = t.facing();
        int turns = f.getHorizontalIndex() == 2 ? 0 : f.getHorizontalIndex() == 3 ? 1 : f.getHorizontalIndex() == 0 ? 2 : 3;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.rotate(-90f * turns, 0, 1, 0);
        GlStateManager.translate(-0.5, 0, -0.5);
        GlStateManager.scale(1 / 16.0, 1 / 16.0, 1 / 16.0);
        GlStateManager.disableCull();
        GlStateManager.disableLighting();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        try { draw.run(); } finally { GlStateManager.popMatrix(); }
    }

    // ---- monitors / video walls ------------------------------------------------------------------------------------
    private static void wall(Minecraft mc, TileMonitor m, int[] wl) {
        double[] g = m.ceiling() ? CEIL : WALL;
        int cols = wl[3], rows = wl[4], s = m.split == 9 ? 3 : m.split == 4 ? 2 : 1;
        EnumFacing right = m.facing().rotateYCCW();
        BlockPos o = m.getPos();
        CctvClient.Pic[] pics = new CctvClient.Pic[s * s];
        boolean any = false;
        for (int k = 0; k < pics.length; k++) {
            int cam = m.ch[k];
            pics[k] = m.mode == 1 && k == m.sel ? CctvClient.playback(cam, m.pbT) : CctvClient.pic(cam, m.net());
        }
        for (int c = 0; c < cols; c++)
            for (int r = 0; r < rows; r++) {
                TileEntity te = mc.world.getTileEntity(o.offset(right, c).up(r));
                if (!(te instanceof TileMonitor b) || !b.running()) continue;
                any = true;
                double dx = -16 * c, dy = 16 * r;
                double bu0 = c / (double) cols, bu1 = (c + 1) / (double) cols, bv0 = (rows - 1 - r) / (double) rows, bv1 = (rows - r) / (double) rows;
                for (int k = 0; k < pics.length; k++) {
                    int tx = k % s, ty = k / s;
                    double tu0 = tx / (double) s, tu1 = (tx + 1) / (double) s, tv0 = ty / (double) s, tv1 = (ty + 1) / (double) s;
                    double u0 = Math.max(bu0, tu0), u1 = Math.min(bu1, tu1), v0 = Math.max(bv0, tv0), v1 = Math.min(bv1, tv1);
                    if (u1 <= u0 || v1 <= v0) continue;
                    // block glass coordinates of this piece
                    double X1 = g[2] - (u0 - bu0) * cols * (g[2] - g[0]) + dx, X0 = g[2] - (u1 - bu0) * cols * (g[2] - g[0]) + dx;
                    double Y1 = g[3] - (v0 - bv0) * rows * (g[3] - g[1]) + dy, Y0 = g[3] - (v1 - bv0) * rows * (g[3] - g[1]) + dy;
                    picture(pics[k], X0, Y0, X1, Y1, g[4], (u0 - tu0) * s, (v0 - tv0) * s, (u1 - tu0) * s, (v1 - tv0) * s, m.dewarp);
                }
            }
        if (!any || !m.overlay) return;
        // overlay per tile, drawn from its top-left corner (wall coordinates mapped across the glass)
        double wx1 = g[2], wx0 = g[0] - 16 * (cols - 1), wy1 = g[3] + 16 * (rows - 1), wy0 = g[1];
        for (int k = 0; k < pics.length; k++) {
            int tx = k % s, ty = k / s;
            double left = wx1 - (wx1 - wx0) * tx / s, top = wy1 - (wy1 - wy0) * ty / s, w = (wx1 - wx0) / s, h = (wy1 - wy0) / s;
            overlay(mc, pics[k], m.ch[k], left, top, w, h, g[4] - 0.08, m.mode == 1 && k == m.sel ? m : null, k == m.sel && s > 1);
        }
    }

    // ---- desk ---------------------------------------------------------------------------------------------------------
    private static void desk(Minecraft mc, TileCctvDesk k) {
        if (!k.running()) return;
        CctvClient.Pic sp = CctvClient.pic(k.spot, k.net());
        picture(sp, SPOT[0], SPOT[1], SPOT[2], SPOT[3], SPOT[4], 0, 0, 1, 1, false);
        overlay(mc, sp, k.spot, SPOT[2], SPOT[3], SPOT[2] - SPOT[0], SPOT[3] - SPOT[1], SPOT[4] - 0.08, null, false);
        double qw = (QUAD[2] - QUAD[0]) / 2, qh = (QUAD[3] - QUAD[1]) / 2;
        for (int i = 0; i < 4; i++) {
            int tx = i % 2, ty = i / 2;
            double x1 = QUAD[2] - tx * qw, x0 = x1 - qw + 0.25, y1 = QUAD[3] - ty * qh, y0 = y1 - qh + 0.25;
            CctvClient.Pic p = CctvClient.pic(k.quad[i], k.net());
            picture(p, x0, y0, x1, y1, QUAD[4], 0, 0, 1, 1, false);
            overlay(mc, p, k.quad[i], x1, y1, x1 - x0, y1 - y0, QUAD[4] - 0.08, null, k.quad[i] == k.spot);
        }
    }

    // ---- one picture -------------------------------------------------------------------------------------------------
    /** the picture's crop (cu0, cv0)-(cu1, cv1) of the image (u left->right, v top->bottom) onto glass x0..x1 (x1 = viewer's left), y0..y1 */
    static void picture(CctvClient.Pic p, double x0, double y0, double x1, double y1, double z, double cu0, double cv0, double cu1, double cv1, boolean dewarp) {
        Tessellator ts = Tessellator.getInstance();
        BufferBuilder b = ts.getBuffer();
        GlStateManager.disableAlpha();
        GlStateManager.disableBlend();
        if (p.tex < 0) {                                                      // NO SIGNAL: the recorder's blue screen
            GlStateManager.disableTexture2D();
            b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            quadC(b, x0, y0, x1, y1, z, 0x10, 0x20, 0x58);
            ts.draw();
            GlStateManager.enableTexture2D();
            return;
        }
        GlStateManager.enableTexture2D();
        GlStateManager.bindTexture(p.tex);
        float tr = 1, tg = 1, tb = 1;
        int prog = CctvShader.begin(p.look, p.gain, p.fisheye && !dewarp, (p.fx & 1) != 0 ? 0.12f : 0f, (p.fx & 2) != 0 ? 1.5f : 0f, p.size);
        if (prog == -2) { if (p.look == 1) { tr = 0.5f; tb = 0.6f; } else if (p.look == 2) { tg = 0.55f; tb = 0.3f; } }
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        double ta = p.flip ? 1 - cv0 : cv0, tbm = p.flip ? 1 - cv1 : cv1;
        b.pos(x1, y1, z).tex(cu0, ta).color(tr, tg, tb, 1f).endVertex();
        b.pos(x0, y1, z).tex(cu1, ta).color(tr, tg, tb, 1f).endVertex();
        b.pos(x0, y0, z).tex(cu1, tbm).color(tr, tg, tb, 1f).endVertex();
        b.pos(x1, y0, z).tex(cu0, tbm).color(tr, tg, tb, 1f).endVertex();
        ts.draw();
        CctvShader.end(prog);
        if (p.live && p.masks.length > 0) {                                   // privacy masks over the live picture
            GlStateManager.disableTexture2D();
            b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            for (int[] m : p.masks) {
                double a0 = Math.max(cu0, m[0] / 1000.0), a1 = Math.min(cu1, m[2] / 1000.0), c0 = Math.max(cv0, m[1] / 1000.0), c1 = Math.min(cv1, m[3] / 1000.0);
                if (a1 <= a0 || c1 <= c0) continue;
                double fx = (x1 - x0) / (cu1 - cu0), fy = (y1 - y0) / (cv1 - cv0);
                quadC(b, x1 - (a1 - cu0) * fx, y1 - (c1 - cv0) * fy, x1 - (a0 - cu0) * fx, y1 - (c0 - cv0) * fy, z - 0.02, 0, 0, 0);
            }
            ts.draw();
            GlStateManager.enableTexture2D();
        }
    }

    private static void quadC(BufferBuilder b, double x0, double y0, double x1, double y1, double z, int r, int g, int bl) {
        b.pos(x1, y1, z).color(r, g, bl, 255).endVertex();
        b.pos(x0, y1, z).color(r, g, bl, 255).endVertex();
        b.pos(x0, y0, z).color(r, g, bl, 255).endVertex();
        b.pos(x1, y0, z).color(r, g, bl, 255).endVertex();
    }

    /** camera name, time, REC dot, status - from the tile's top-left (left = the larger x) */
    private static void overlay(Minecraft mc, CctvClient.Pic p, int cam, double left, double top, double w, double h, double z, TileMonitor pb, boolean selected) {
        FontRenderer f = mc.fontRenderer;
        double sc = Math.max(0.045, Math.min(0.25, h / 150.0));
        CctvData.Cam c = p.cam;
        String name = cam <= 0 ? "--" : "CAM " + cam + (c == null ? "" : " " + c.name);
        text(f, name, left - 0.4, top - 0.4, z, sc, 0xFFFFFFFF);
        long t = pb != null && p.t > 0 ? p.t + (mc.world.getWorldTime() - mc.world.getTotalWorldTime()) : mc.world.getWorldTime();
        long day = (t + 6000) % 24000, d = t / 24000 + 1;
        String time = String.format(Locale.ROOT, "D%d %02d:%02d:%02d", d, day / 1000, day % 1000 * 60 / 1000, day % 1000 * 3600 / 1000 % 60);
        text(f, time, left - 0.4, top - h + 0.4 + 9 * sc, z, sc, 0xFFE0E0E0);
        if (pb != null) {
            String sp = TileMonitor.SPEEDS[pb.pbSpeed] > 0 ? "> x" + TileMonitor.SPEEDS[pb.pbSpeed] : "< x" + -TileMonitor.SPEEDS[pb.pbSpeed];
            text(f, pb.pbPause ? "PB || PAUSE" : "PB " + sp, left - w + 0.4 + f.getStringWidth("PB || PAUSE") * sc, top - h + 0.4 + 9 * sc, z, sc, 0xFFFFD040);
        } else if (c != null && c.rec && (System.currentTimeMillis() / 500) % 2 == 0) {
            text(f, "● REC", left - w + 0.4 + f.getStringWidth("● REC") * sc, top - 0.4, z, sc, 0xFFFF2020);
        }
        if (c != null && c.streams() && !p.live && p.tex >= 0 && pb == null) text(f, "SNAPSHOT", left - w + 0.4 + f.getStringWidth("SNAPSHOT") * sc, top - h + 0.4 + 9 * sc, z, sc, 0xFF80C0FF);
        if (c != null && c.ir && c.kind() != CamType.THERMAL) text(f, "IR", left - 0.4, top - 0.4 - 10 * sc, z, sc, 0xFFFF6060);
        if (selected) text(f, "[" + (pb == null ? "SEL" : "PB") + "]", left - w / 2 + 2, top - 0.4, z, sc, 0xFFFFFF60);
        if (p.tex < 0) {
            String m = cam <= 0 ? "NO CAMERA" : "NO SIGNAL";
            double s2 = sc * 1.6;
            text(f, m, left - w / 2 + f.getStringWidth(m) * s2 / 2, top - h / 2 + 4 * s2, z, s2, 0xFFFFFFFF);
            if (!p.msg.isEmpty() && !p.msg.equals(m)) text(f, p.msg, left - w / 2 + f.getStringWidth(p.msg) * sc / 2, top - h / 2 - 6 * s2, z, sc, 0xFFC0D0FF);
        }
    }

    /** text whose top-left (as you look at the screen) is at model (x, y) on the plane z */
    static void text(FontRenderer f, String s, double x, double y, double z, double sc, int col) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.scale(-sc, -sc, sc);
        GlStateManager.enableTexture2D();
        f.drawString(s, 0, 0, col);
        GlStateManager.popMatrix();
    }

    // ---- PTZ moving parts ------------------------------------------------------------------------------------------
    private static void ptz(Minecraft mc, TileCamera c, double x, double y, double z, float dt) {
        if (c.tiltR < -500) { c.panR = c.pan; c.tiltR = c.tilt; c.zoomR = c.zoom; }
        float rate = new float[]{1.5f, 3f, 6f}[Math.max(0, Math.min(2, c.speed))] * 20 * dt;   // the motor speed the server runs at
        float dp = wrapf(c.pan - c.panR);
        c.panR = wrapf(c.panR + Math.max(-rate, Math.min(rate, dp)));
        c.tiltR += Math.max(-rate, Math.min(rate, c.tilt - c.tiltR));
        c.zoomR += Math.max(-12 * dt, Math.min(12 * dt, c.zoom - c.zoomR));
        if (panModel == null) {
            net.minecraft.client.renderer.block.model.ModelManager mm = mc.getRenderItem().getItemModelMesher().getModelManager();
            panModel = mm.getModel(new ModelResourceLocation(RailMap.MODID + ":cctv_ptz_pan", "inventory"));
            headModel = mm.getModel(new ModelResourceLocation(RailMap.MODID + ":cctv_ptz_head", "inventory"));
        }
        EnumFacing f = c.facing();
        int turns = f.getHorizontalIndex() == 2 ? 0 : f.getHorizontalIndex() == 3 ? 1 : f.getHorizontalIndex() == 0 ? 2 : 3;
        int light = mc.world.getCombinedLight(c.getPos(), 0);
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light % 65536, light / 65536f);
        GlStateManager.enableTexture2D();
        GlStateManager.enableCull();
        GlStateManager.disableLighting();
        GlStateManager.color(1, 1, 1, 1);
        double px = CamType.PIV_X / 16, py = CamType.PIV_Y / 16, pz = CamType.PIV_Z / 16;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.rotate(-90f * turns, 0, 1, 0);
        GlStateManager.translate(-0.5, 0, -0.5);
        GlStateManager.translate(px, py, pz);
        GlStateManager.rotate(-c.panR, 0, 1, 0);
        GlStateManager.translate(-px, -py, -pz);
        BlockModelRenderer r = mc.getBlockRendererDispatcher().getBlockModelRenderer();
        r.renderModelBrightnessColor(panModel, 1f, 1f, 1f, 1f);
        GlStateManager.translate(px, py, pz);
        GlStateManager.rotate(-c.tiltR, 1, 0, 0);
        GlStateManager.translate(-px, -py, -pz);
        r.renderModelBrightnessColor(headModel, 1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();
    }

    private static float wrapf(float a) { a %= 360; if (a > 180) a -= 360; if (a < -180) a += 360; return a; }
}
