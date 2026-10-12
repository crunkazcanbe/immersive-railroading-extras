package com.dogpound.railmap.ride.client;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.ride.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.GL11;

/**
 * Everything of a line that is not a block or a car: the ropes hanging between the supports (haul ropes; an aerial tram's
 * track ropes too), the pylon heads and station bullwheel frames turned to the rope's bearing, the station bullwheels and
 * the machine room's bullwheel + motor coupling / brake disc turning at the real rope speed, the tension weight riding
 * its carriage, and the control desk's mimic screen (the line, its stations and pylons, every car live).
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID, value = Side.CLIENT)
public final class RideRender {
    private RideRender() {}

    /** the desk's mimic glass in its north-facing model (px): x0, y0, x1, y1, z (bridge face) */
    public static final double[] MIMIC = {-13.8, 18.2, 11.8, 28.4, 9.3};
    /** machine room pivots (px): bullwheel centre + height, rotor shaft (x axis) centre */
    static final double WX = 8, WZ = 8, WY = 23.0, RY = 8, RZ = 8;

    @SubscribeEvent
    public static void render(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.getRenderViewEntity() == null || RideClient.LINES.isEmpty() || mc.world.provider.getDimension() != RideClient.dim) return;
        if (com.dogpound.railmap.cctv.client.CctvFeeds.rendering && RideClient.LINES.size() > 4) return;
        Entity v = mc.getRenderViewEntity();
        float pt = e.getPartialTicks();
        double cx = v.lastTickPosX + (v.posX - v.lastTickPosX) * pt, cy = v.lastTickPosY + (v.posY - v.lastTickPosY) * pt, cz = v.lastTickPosZ + (v.posZ - v.lastTickPosZ) * pt;
        try {
            for (RideData.Line l : RideClient.LINES.values()) {
                RideGeom g = l.geom();
                if (g == null) continue;
                ropes(l, g, cx, cy, cz);
                parts(mc, l, g, cx, cy, cz, pt);
            }
            for (TileEntity t : mc.world.loadedTileEntityList) {
                if (t.isInvalid()) continue;
                BlockPos p = t.getPos();
                double x = p.getX() - cx, y = p.getY() - cy, z = p.getZ() - cz;
                if (x * x + y * y + z * z > 64 * 64) continue;
                if (t instanceof TileRideMachine m) machine(mc, m, x, y, z, pt);
                else if (t instanceof TileRideTension te) tension(mc, te, x, y, z);
                else if (t instanceof TileRideDesk d && x * x + y * y + z * z < 32 * 32) mimic(d, x, y, z);
            }
        } catch (RuntimeException ex) {
            RailMap.LOG.debug("[IR Extras] ropeway render: {}", ex.toString());
        }
    }

    // ---- ropes -----------------------------------------------------------------------------------------------------------
    static void ropes(RideData.Line l, RideGeom g, double cx, double cy, double cz) {
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        Tessellator ts = Tessellator.getInstance();
        BufferBuilder b = ts.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        if (l.kind == RideGeom.FUNICULAR) strand(b, g, 0, 0.12, 0.025, 0x4a4d50, cx, cy, cz);
        else {
            for (int side = -1; side <= 1; side += 2) {
                if (l.kind == RideGeom.TRAM) { strand(b, g, side * RideGeom.LAT, 0.25, 0.045, 0x2e3033, cx, cy, cz); strand(b, g, side * RideGeom.LAT, -0.35, 0.025, 0x4a4d50, cx, cy, cz); }
                else strand(b, g, side * RideGeom.LAT, 0, 0.03, 0x3a3d40, cx, cy, cz);
            }
        }
        ts.draw();
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
    }

    /** one rope: a thin cross of two ribbons along the sampled path, offset sideways / up */
    static void strand(BufferBuilder b, RideGeom g, double off, double up, double r, int rgb, double cx, double cy, double cz) {
        int n = g.samples();
        double px = 0, py = 0, pz = 0;
        boolean have = false;
        int cr = (rgb >> 16) & 255, cg = (rgb >> 8) & 255, cb = rgb & 255;
        for (int i = 0; i < n; i++) {
            double[] lat = g.lateral(g.su(i));
            double x = g.sx(i) + lat[0] * off - cx, y = g.sy(i) + up - cy, z = g.sz(i) + lat[1] * off - cz;
            if (have && x * x + y * y + z * z < 192 * 192) {
                b.pos(px, py - r, pz).color(cr, cg, cb, 255).endVertex(); b.pos(px, py + r, pz).color(cr, cg, cb, 255).endVertex();
                b.pos(x, y + r, z).color(cr, cg, cb, 255).endVertex(); b.pos(x, y - r, z).color(cr, cg, cb, 255).endVertex();
                b.pos(px - lat[0] * r, py, pz - lat[1] * r).color(cr, cg, cb, 255).endVertex(); b.pos(px + lat[0] * r, py, pz + lat[1] * r).color(cr, cg, cb, 255).endVertex();
                b.pos(x + lat[0] * r, y, z + lat[1] * r).color(cr, cg, cb, 255).endVertex(); b.pos(x - lat[0] * r, y, z - lat[1] * r).color(cr, cg, cb, 255).endVertex();
            }
            px = x; py = y; pz = z;
            have = true;
        }
    }

    // ---- pylon heads, station frames + bullwheels ---------------------------------------------------------------------------
    static void parts(Minecraft mc, RideData.Line l, RideGeom g, double cx, double cy, double cz, float pt) {
        if (l.kind == RideGeom.FUNICULAR) return;
        BlockModelRenderer r = begin(mc);
        for (int i = 1; i + 1 < l.supports.size(); i++) {
            double[] s = l.supports.get(i);
            if (sq(s[0] - cx, s[1] - cy, s[2] - cz) > 160 * 160) continue;
            double[] a = l.supports.get(i - 1), c = l.supports.get(i + 1);
            light(mc, new BlockPos(s[0], s[1], s[2]));
            draw(r, "ropeway_pylon_head", s[0] - cx, s[1] - 1.0 - cy, s[2] - cz, bearing(c[0] - a[0], c[2] - a[2]), 0);
        }
        double spin = Math.toDegrees((l.ropeRun + l.v * 0.05 * pt) / RideGeom.LAT);
        for (int k = 0; k < 2; k++) {
            double[] s = l.supports.get(k == 0 ? 0 : l.supports.size() - 1), o = l.supports.get(k == 0 ? 1 : l.supports.size() - 2);
            if (sq(s[0] - cx, s[1] - cy, s[2] - cz) > 128 * 128) continue;
            double brg = bearing(s[0] - o[0], s[2] - o[2]);
            light(mc, new BlockPos(s[0], s[1] - 3, s[2]));
            draw(r, "ropeway_station_frame", s[0] - cx, s[1] - 3.1 + 1.0 - cy, s[2] - cz, brg, 0);
            draw(r, "ropeway_bullwheel", s[0] - cx, s[1] - 0.25 - cy, s[2] - cz, brg, k == 0 ? -spin : spin);
        }
        end();
    }

    static BlockModelRenderer begin(Minecraft mc) {
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.enableTexture2D();
        GlStateManager.enableCull();
        GlStateManager.disableLighting();
        GlStateManager.color(1, 1, 1, 1);
        return mc.getBlockRendererDispatcher().getBlockModelRenderer();
    }

    static void end() { GlStateManager.enableLighting(); }

    static void light(Minecraft mc, BlockPos p) {
        int light = mc.world.getCombinedLight(p, 0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light % 65536, light / 65536f);
    }

    /** a north-facing baked part centred on (x, z), its front turned to bearing deg, spun spin deg about its centre */
    static void draw(BlockModelRenderer r, String part, double x, double y, double z, double bearing, double spin) {
        IBakedModel m = RideClient.baked(part);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.rotate((float) bearing, 0, 1, 0);
        if (spin != 0) GlStateManager.rotate((float) spin, 0, 1, 0);
        GlStateManager.translate(-0.5, 0, -0.5);
        r.renderModelBrightnessColor(m, 1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();
    }

    /** GL rotation that turns a model's north (-z) to point along (dx, dz) */
    static double bearing(double dx, double dz) { return Math.toDegrees(Math.atan2(-dx, -dz)); }

    static double sq(double a, double b, double c) { return a * a + b * b + c * c; }

    static int turns(EnumFacing f) { return f.getHorizontalIndex() == 2 ? 0 : f.getHorizontalIndex() == 3 ? 1 : f.getHorizontalIndex() == 0 ? 2 : 3; }

    // ---- machine room: bullwheel + motor rotor turning ----------------------------------------------------------------------
    static void machine(Minecraft mc, TileRideMachine m, double x, double y, double z, float pt) {
        RideData.Line l = RideClient.line(m.lineId);
        double wheel = l == null ? 0 : Math.toDegrees((l.ropeRun + Math.abs(l.v) * 0.05 * pt) / RideGeom.LAT);
        double rotor = (wheel * 72) % 360;                                     // 72:1 gearbox
        BlockModelRenderer r = begin(mc);
        light(mc, m.getPos().up());
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.rotate(-90f * turns(m.controlFacing()), 0, 1, 0);
        GlStateManager.translate(-0.5, 0, -0.5);
        GlStateManager.pushMatrix();
        GlStateManager.translate(WX / 16, 0, WZ / 16);
        GlStateManager.rotate((float) -wheel, 0, 1, 0);
        GlStateManager.translate(-WX / 16, 0, -WZ / 16);
        r.renderModelBrightnessColor(RideClient.baked("ropeway_mach_wheel"), 1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();
        GlStateManager.translate(0, RY / 16, RZ / 16);
        GlStateManager.rotate((float) rotor, 1, 0, 0);
        GlStateManager.translate(0, -RY / 16, -RZ / 16);
        r.renderModelBrightnessColor(RideClient.baked("ropeway_mach_rotor"), 1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();
        end();
    }

    static void tension(Minecraft mc, TileRideTension t, double x, double y, double z) {
        RideData.Line l = RideClient.line(t.lineId);
        float c = l == null ? 0.5f : (float) Math.max(0, Math.min(1, l.carriage / 100));
        BlockModelRenderer r = begin(mc);
        light(mc, t.getPos().up());
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.rotate(-90f * turns(t.controlFacing()), 0, 1, 0);
        GlStateManager.translate(-0.5, 0.25 + (1 - c) * 0.9, -0.5);             // the weight hangs lower as the carriage runs out
        r.renderModelBrightnessColor(RideClient.baked("ropeway_tension_weight"), 1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();
        end();
    }

    // ---- the desk's mimic screen --------------------------------------------------------------------------------------------
    static void mimic(TileRideDesk d, double x, double y, double z) {
        RideData.Line l = RideClient.line(d.lineId);
        if (!d.powered()) return;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.rotate(-90f * turns(d.controlFacing()), 0, 1, 0);
        GlStateManager.translate(-0.5, 0, -0.5);
        GlStateManager.scale(1 / 16.0, 1 / 16.0, 1 / 16.0);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        Tessellator ts = Tessellator.getInstance();
        BufferBuilder b = ts.getBuffer();
        double x0 = MIMIC[0], y0 = MIMIC[1], x1 = MIMIC[2], y1 = MIMIC[3], zf = MIMIC[4] - 0.02;
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        rect(b, x0, y0, x1, y1, zf, 0x061018);
        if (l == null || l.geom() == null) { ts.draw(); finish(); return; }
        RideGeom g = l.geom();
        // the line (screen x runs from station A on the left - model x1 is the viewer's left on a north face)
        double ya = y0 + 6.2, yb = y0 + 3.8, ym = (ya + yb) / 2, mx0 = x1 - 1.2, mx1 = x0 + 1.2;
        int lineCol = l.faults != 0 ? 0xc03030 : l.running ? 0x30c060 : 0x8090a0;
        rect(b, mx1, ya - 0.08, mx0, ya + 0.08, zf - 0.01, lineCol);
        rect(b, mx1, yb - 0.08, mx0, yb + 0.08, zf - 0.01, lineCol);
        // stations + pylons (ticks at their place along the rope)
        rect(b, mx0 - 0.4, yb - 0.6, mx0 + 0.4, ya + 0.6, zf - 0.015, 0xd0d0d0);
        rect(b, mx1 - 0.4, yb - 0.6, mx1 + 0.4, ya + 0.6, zf - 0.015, 0xd0d0d0);
        double acc = 0;
        for (int i = 1; i + 1 < l.supports.size(); i++) {
            acc += span(l, i);
            double sx = mx0 + (mx1 - mx0) * Math.min(1, acc / g.length);
            rect(b, sx - 0.12, yb - 0.4, sx + 0.12, ya + 0.4, zf - 0.012, 0x7a8088);
        }
        // the height profile underneath
        double hmin = 1e9, hmax = -1e9;
        for (double[] s : l.supports) { hmin = Math.min(hmin, s[1]); hmax = Math.max(hmax, s[1]); }
        int n = 40;
        for (int i = 0; i < n; i++) {
            double u = g.length * i / n, h = g.at(u)[1];
            double sx = mx0 + (mx1 - mx0) * i / n, bh = 0.3 + 2.2 * (h - hmin) / Math.max(1, hmax - hmin);
            rect(b, sx, y0 + 0.4, sx + (mx1 - mx0) / n, y0 + 0.4 + bh, zf - 0.01, 0x1c3c2c);
        }
        // cars
        for (int i = 0; i < l.cars(); i++) {
            double u, row;
            boolean in;
            if (l.shuttle()) { u = l.carCoord(i); row = i == 0 ? ya : yb; in = l.atEnd(); }
            else {
                double[] pl = g.loopPlace(l.carCoord(i));
                u = pl[0];
                row = pl[2] >= 0 ? ym : pl[1] > 0 ? ya : yb;
                in = pl[3] >= 0;
            }
            double sx = mx0 + (mx1 - mx0) * u / g.length;
            int col = l.faults != 0 ? 0xff4040 : in ? 0xffd040 : 0x40ff80;
            rect(b, sx - 0.35, row - 0.35, sx + 0.35, row + 0.35, zf - 0.02, col);
        }
        // speed bar + wind bar along the bottom-right
        double sp = Math.min(1, Math.abs(l.v) / 6), wd = Math.min(1, l.wind / Math.max(1, l.windLimit));
        rect(b, x1 - 1.0, y1 - 1.2, x1 - 1.0 - 10 * sp, y1 - 0.7, zf - 0.01, 0x40c0ff);
        rect(b, x0 + 1.0 + 10 * wd, y1 - 1.2, x0 + 1.0, y1 - 0.7, zf - 0.01, wd > 1 ? 0xff4040 : wd > 0.75 ? 0xffb020 : 0x80e080);
        ts.draw();
        finish();
    }

    static void finish() {
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    /** a flat rectangle on a north face at z (stands toward -z) */
    static void rect(BufferBuilder b, double xa, double ya, double xb, double yb, double z, int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, bl = rgb & 255;
        double lx = Math.min(xa, xb), hx = Math.max(xa, xb), ly = Math.min(ya, yb), hy = Math.max(ya, yb);
        b.pos(lx, ly, z).color(r, g, bl, 255).endVertex();
        b.pos(lx, hy, z).color(r, g, bl, 255).endVertex();
        b.pos(hx, hy, z).color(r, g, bl, 255).endVertex();
        b.pos(hx, ly, z).color(r, g, bl, 255).endVertex();
    }

    /** the rope between support i-1 and i (straight) */
    static double span(RideData.Line l, int i) {
        double[] a = l.supports.get(i - 1), c = l.supports.get(i);
        return Math.sqrt(sq(a[0] - c[0], a[1] - c[1], a[2] - c[2]));
    }
}
