package com.dogpound.railmap.light;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.controls.IControlled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.GL11;

/**
 * Night lighting, drawn: a soft camera-facing halo round every lit luminaire lens (bigger and stronger at night, held
 * at a readable size with distance, out to haloDistance), the faint light cone of a yard floodlight in the dark, and the
 * station name on a lit name sign. One pass for all lamps (LampRegistry), additive blending, no depth writes.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID, value = Side.CLIENT)
public final class LampHaloRenderer {
    private LampHaloRenderer() {}

    @SubscribeEvent
    public static void render(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.getRenderViewEntity() == null) return;
        Entity v = mc.getRenderViewEntity();
        float pt = e.getPartialTicks();
        double cx = v.lastTickPosX + (v.posX - v.lastTickPosX) * pt, cy = v.lastTickPosY + (v.posY - v.lastTickPosY) * pt + v.getEyeHeight(), cz = v.lastTickPosZ + (v.posZ - v.lastTickPosZ) * pt;
        double camY = cy - v.getEyeHeight();
        float night = NightGlow.night;
        double maxD = LightConfig.haloDistance;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.depthMask(false);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        java.util.List<TileLuminaire> signs = new java.util.ArrayList<>();
        for (TileEntity t : LampRegistry.all()) {
            if (t.isInvalid() || t.getWorld() != mc.world || !(t instanceof ILamp l)) continue;
            double bx = t.getPos().getX() + 0.5, by = t.getPos().getY(), bz = t.getPos().getZ() + 0.5;
            double d = Math.sqrt((bx - cx) * (bx - cx) + (by - cy) * (by - cy) + (bz - cz) * (bz - cz));
            if (d > maxD) continue;
            if (t instanceof TileLuminaire tl && tl.kind == TileLuminaire.Kind.SIGN && d < 48) signs.add(tl);
            float lv = l.haloLevel();
            double[][] pts = l.haloPoints();
            if (lv <= 0 || pts == null || LightConfig.haloScale <= 0) continue;
            EnumFacing f = t instanceof IControlled c ? c.controlFacing() : EnumFacing.NORTH;
            for (double[] p : pts) {
                double[] w = world(t, f, p[0], p[1], p[2]);
                double dd = Math.sqrt((w[0] - cx) * (w[0] - cx) + (w[1] - cy) * (w[1] - cy) + (w[2] - cz) * (w[2] - cz));
                double r = p[3] / 16.0 * LightConfig.haloScale * (0.45 + 1.1 * night) * Math.max(1, Math.min(3.5, dd / 40.0));
                float a = lv * (0.18f + 0.42f * night);
                halo(w[0] - cx, w[1] - camY, w[2] - cz, cy - camY, r, (int) p[4], a);
            }
            if (t instanceof TileLuminaire tl && tl.kind == TileLuminaire.Kind.FLOOD && LightConfig.floodBeams && night > 0.25f)
                for (double[] p : pts) beam(t, f, p, cx, camY, cz, lv * 0.07f * night);
        }
        GlStateManager.depthMask(true);
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.enableTexture2D();
        for (TileLuminaire s : signs) signText(mc.fontRenderer, s, cx, camY, cz);
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.popMatrix();
    }

    /** model px (north-facing) -> world coordinates, turned the way the block faces (same turn as ControlsRenderer) */
    static double[] world(TileEntity t, EnumFacing f, double x, double y, double z) {
        int i = f.getHorizontalIndex(), turns = i == 2 ? 0 : i == 3 ? 1 : i == 0 ? 2 : 3;
        double a = Math.toRadians(-90.0 * turns), c = Math.cos(a), s = Math.sin(a);
        double lx = x / 16.0 - 0.5, lz = z / 16.0 - 0.5;
        double rx = lx * c + lz * s, rz = -lx * s + lz * c;
        return new double[]{t.getPos().getX() + 0.5 + rx, t.getPos().getY() + y / 16.0, t.getPos().getZ() + 0.5 + rz};
    }

    /** a soft disc facing the camera (x, y, z relative to the render origin; the eye is `eye` above it) */
    private static void halo(double x, double y, double z, double eye, double r, int rgb, float a) {
        double len = Math.sqrt(x * x + (y - eye) * (y - eye) + z * z);
        if (len < 1e-3) return;
        double vx = -x / len, vy = -(y - eye) / len, vz = -z / len;          // toward the camera
        double rx = vz, rz = -vx, rl = Math.sqrt(rx * rx + rz * rz);          // right = view x up
        if (rl < 1e-4) { rx = 1; rz = 0; rl = 1; }
        rx /= rl; rz /= rl;
        double ux = vy * rz, uy = vz * rx - vx * rz, uz = -vy * rx;           // up = right x view (sign irrelevant for a disc)
        double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul; uy /= ul; uz /= ul;
        int R = (rgb >> 16) & 255, G = (rgb >> 8) & 255, B = rgb & 255;
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        b.pos(x + vx * 0.05, y + vy * 0.05, z + vz * 0.05).color(R, G, B, (int) (255 * Math.min(1, a))).endVertex();
        for (int k = 0; k <= 16; k++) {
            double t = k * Math.PI * 2 / 16, cs = Math.cos(t) * r, sn = Math.sin(t) * r;
            b.pos(x + rx * cs + ux * sn, y + uy * sn, z + rz * cs + uz * sn).color(R, G, B, 0).endVertex();
        }
        Tessellator.getInstance().draw();
    }

    /** the floodlight's light cone: from the lens out along its facing, tipped 35 degrees down */
    private static void beam(TileEntity t, EnumFacing f, double[] p, double cx, double cy, double cz, float a) {
        if (a <= 0.003f) return;
        double[] o = world(t, f, p[0], p[1], p[2]);
        double dx = f.getXOffset(), dz = f.getZOffset();
        double len = LightConfig.floodReach * 0.85, down = Math.sin(Math.toRadians(35)), fw = Math.cos(Math.toRadians(35));
        double ex = o[0] + dx * fw * len, ey = o[1] - down * len, ez = o[2] + dz * fw * len;
        double rad = len * 0.32;
        double sx = -dz, sz = dx;                                              // sideways
        double upx = dx * down, upy = fw, upz = dz * down;                    // perpendicular in the vertical plane
        int R = (p.length > 4 ? (int) p[4] : 0xFFFFFF);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        b.pos(o[0] - cx, o[1] - cy, o[2] - cz).color((R >> 16) & 255, (R >> 8) & 255, R & 255, (int) (255 * a)).endVertex();
        for (int k = 0; k <= 16; k++) {
            double th = k * Math.PI * 2 / 16, c = Math.cos(th) * rad, s = Math.sin(th) * rad * 0.55;
            b.pos(ex + sx * c + upx * s - cx, ey + upy * s - cy, ez + sz * c + upz * s - cz).color((R >> 16) & 255, (R >> 8) & 255, R & 255, 0).endVertex();
        }
        Tessellator.getInstance().draw();
    }

    /** the station's name on both faces of the sign board, full-bright when the sign is lit */
    private static void signText(FontRenderer fr, TileLuminaire s, double cx, double cy, double cz) {
        String name = s.signName();
        boolean lit = s.lampOn();
        EnumFacing f = s.controlFacing();
        int col = lit ? 0xFFFFFF : 0x8A93A8;
        for (int side = 0; side < 2; side++) {
            double[] w = world(s, f, 8, 11.5, side == 0 ? 6.9 : 9.1);
            GlStateManager.pushMatrix();
            GlStateManager.translate(w[0] - cx, w[1] - cy, w[2] - cz);
            float yaw = -f.getHorizontalAngle() + (side == 0 ? 180 : 0);
            GlStateManager.rotate(yaw, 0, 1, 0);
            int width = Math.max(1, fr.getStringWidth(name));
            double sc = Math.min(0.0125, 1.8 / width);                         // the board is 2 blocks wide: fit the name
            GlStateManager.scale(-sc, -sc, sc);
            if (lit) OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
            else {
                int l = s.getWorld().getCombinedLight(s.getPos(), 0);
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, l & 0xFFFF, l >> 16);
            }
            fr.drawString(name, -width / 2, -4, col);
            GlStateManager.popMatrix();
        }
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
    }
}
