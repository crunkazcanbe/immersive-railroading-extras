package com.dogpound.railmap.client.render;

import com.dogpound.railmap.block.ConsoleGeometry;
import com.dogpound.railmap.block.ConsolePanel;
import com.dogpound.railmap.block.TileDispatcherBoard;
import com.dogpound.railmap.client.ClientRailway;
import com.dogpound.railmap.client.ClientTrains;
import com.dogpound.railmap.graph.RailNetwork;
import com.dogpound.railmap.graph.TrainNode;
import com.dogpound.railmap.network.PacketRailState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * Draws the live parts of the Control Center console on top of its block model: every control on the sloped desk
 * (through the grid panel renderer, tilted onto the desk), the big screen (the live network map with trains and their
 * moving-block envelopes, or the TRAINS / POWER / ALARMS pages), the nameplate, and the stack light on the screen
 * housing (green healthy, amber alarm, red ALL STOP). Dark when it has no power; smoking when burnt out.
 */
public class ConsoleRenderer extends TileEntitySpecialRenderer<TileDispatcherBoard> {
    private final MapRenderer map = new MapRenderer();
    private static final int K = 8;                 // screen resolution: units per model pixel

    @Override
    public void render(TileDispatcherBoard te, double x, double y, double z, float pt, int destroyStage, float alpha) {
        if (te.getWorld() == null || !te.isConsole()) return;
        double dist2 = (x + 0.5) * (x + 0.5) + (y + 1) * (y + 1) + (z + 0.5) * (z + 0.5);
        if (dist2 > 64 * 64) return;
        EnumFacing f = te.facing();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.rotate(-90f * ConsoleGeometry.turns(f), 0, 1, 0);
        GlStateManager.translate(-0.5, 0, -0.5);
        GlStateManager.scale(1 / 16.0, 1 / 16.0, 1 / 16.0);
        GlStateManager.disableCull();

        // ---- the desk: every control, tilted onto the sloped top
        GlStateManager.pushMatrix();
        GlStateManager.translate(ConsoleGeometry.DESK_X, ConsoleGeometry.DESK_Y, ConsoleGeometry.DESK_Z);
        GlStateManager.rotate((float) ConsoleGeometry.TILT, 1, 0, 0);
        GridPanelRenderer.drawInFrame(te, ConsolePanel.DESK, te.getWorld(), te.getPos().up(), pt, dist2 < 14 * 14, true);
        if (dist2 < 14 * 14) {
            // section dividers + silk-screen headers' underline
            GlStateManager.disableLighting();
            GridPanelRenderer.label("TRAIN CONTROL", 8, 0.55, 0.45, 0x303030, false);
            GridPanelRenderer.label("SCREEN + MAP", 24, 0.55, 0.45, 0x303030, false);
            GridPanelRenderer.label(te.burnt ? "BURNT OUT" : !te.deskOn() ? "NO 12 V" : "SYSTEM", 40, 0.55, 0.45, te.deskOn() ? 0x303030 : 0xC01010, false);
            GlStateManager.enableLighting();
        }
        GlStateManager.popMatrix();

        // ---- the big screen
        drawScreen(te, dist2);

        // ---- stack light on the screen housing (viewer's right end)
        drawStackLight(te);

        GlStateManager.enableCull();
        GlStateManager.popMatrix();

        if (te.burnt && te.getWorld().rand.nextInt(6) == 0) {
            net.minecraft.util.math.BlockPos p = te.getPos();
            te.getWorld().spawnParticle(net.minecraft.util.EnumParticleTypes.SMOKE_NORMAL, p.getX() + 0.5, p.getY() + 1.2, p.getZ() + 0.5, 0, 0.03, 0);
        }
    }

    private void drawScreen(TileDispatcherBoard te, double dist2) {
        double x0 = ConsoleGeometry.SCREEN_X0, x1 = ConsoleGeometry.SCREEN_X1, y0 = ConsoleGeometry.SCREEN_Y0, y1 = ConsoleGeometry.SCREEN_Y1;
        double w = x0 - x1, h = y1 - y0;
        int W = (int) Math.round(w * K), H = (int) Math.round(h * K);
        GlStateManager.pushMatrix();
        // top-left of the glass (the operator's left = +x), local +X -> operator's right, +Y -> down, facing the operator
        GlStateManager.translate(x0, y1, ConsoleGeometry.SCREEN_Z - 0.03);
        GlStateManager.rotate(180f, 0, 0, 1);
        GlStateManager.scale(1.0 / K, 1.0 / K, 1.0 / K);
        GlStateManager.disableLighting();
        GlStateManager.disableTexture2D();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        if (!te.screenOn()) {
            rect(0, 0, W, H, te.burnt ? 0xFF0D0D0D : 0xFF050708);       // dead glass
            GlStateManager.enableTexture2D();
            if (te.burnt && dist2 < 20 * 20) text("NO SIGNAL", W / 2, H / 2 - 4, 0xFF5A1A1A, true, 1.2f);
            GlStateManager.enableLighting();
            GlStateManager.popMatrix();
            return;
        }
        rect(0, 0, W, H, 0xFF06121F);
        rect(0, 0, W, 12, 0xFF103A66);                                     // title bar
        GlStateManager.enableTexture2D();
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        String title = te.screenText("scrTitle");
        fr.drawString("PRIDE RAIL CONTROL", 4, 2, 0xFFE3F2FD);
        fr.drawString(title, W - 4 - fr.getStringWidth(title), 2, 0xFFFFD54F);
        String clock = te.screenText("clock");
        fr.drawString(clock, W / 2 - fr.getStringWidth(clock) / 2, 2, 0xFF80CBC4);

        int page = te.pv("page");
        if (page == 0) {
            drawMap(te, W, H - 12);
        } else if (dist2 < 24 * 24) {
            for (int i = 0; i < TileDispatcherBoard.LINES; i++) {
                String s = te.screenText("scr" + i);
                if (s.isEmpty()) continue;
                int col = s.startsWith("!") ? 0xFFFF8A80 : s.startsWith(">") ? 0xFFFFD54F : 0xFFCFD8DC;
                fr.drawString(fr.trimStringToWidth(s, W - 8), 4, 15 + i * 10, col);
            }
        }
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private void drawMap(TileDispatcherBoard te, int W, int H) {
        RailNetwork net = te.getNetwork();
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 12, 0);
        map.w = W;
        map.h = H;
        map.labels = true;
        map.grid = false;
        map.origin = null;
        map.fit(net, te.getPos(), 2);
        map.zoom *= Math.pow(1.4, te.pv("zoom"));
        List<TrainNode> trains = ClientTrains.get();
        GL11.glPushAttrib(GL11.GL_LINE_BIT);
        map.draw(net, trains, -1, -1);
        // moving-block envelopes, as on the board's map
        PacketRailState st = ClientRailway.get();
        if (st != null && !st.envelopes.isEmpty()) {
            GlStateManager.disableTexture2D();
            GlStateManager.enableBlend();
            GL11.glLineWidth(3f);
            Tessellator tess = Tessellator.getInstance();
            BufferBuilder buf = tess.getBuffer();
            for (PacketRailState.Envelope ev : st.envelopes) {
                int c = ev.color == 0 ? 0x9043A047 : ev.color == 1 ? 0xB0FFB300 : 0xD0E53935;
                for (double[] pts : ev.points) {
                    buf.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
                    for (int i = 0; i + 2 < pts.length; i += 3)
                        buf.pos(map.toScreenX(pts[i]), map.toScreenY(pts[i + 2]), -0.1)
                                .color((c >> 16) & 255, (c >> 8) & 255, c & 255, (c >>> 24) & 255).endVertex();
                    tess.draw();
                }
            }
            GlStateManager.enableTexture2D();
        }
        GL11.glPopAttrib();
        GlStateManager.popMatrix();
    }

    private void drawStackLight(TileDispatcherBoard te) {
        boolean on = te.deskOn() || te.screenOn();
        long now = te.getWorld().getTotalWorldTime();
        boolean blink = ((now / 8) & 1) == 0;
        boolean red = on && te.pv("allStop") == 1, amber = on && te.pv("lpAlarm") == 1 && blink, green = on && te.pv("lpOk") == 1;
        double cx = -13.2, cz = 13.8, yb = 31.2;
        Prims.begin();
        Prims.box(cx - 0.35, yb, cz - 0.35, cx + 0.35, yb + 1.6, cz + 0.35, 0x2A2A2A);                // pole
        int[] cols = {0x1FA34A, 0xF0A020, 0xD02020};
        boolean[] lit = {green, amber, red};
        for (int i = 0; i < 3; i++) {
            double a = yb + 1.6 + i * 1.3;
            if (lit[i]) Prims.emissive(true);
            Prims.box(cx - 0.75, a, cz - 0.75, cx + 0.75, a + 1.15, cz + 0.75, lit[i] ? cols[i] : Prims.shade(cols[i], 0.35f));
            if (lit[i]) {
                Prims.emissive(false);
                Prims.glow(cx, a + 0.6, cz - 0.9, 2.2, cols[i]);
            }
        }
        Prims.box(cx - 0.6, yb + 1.6 + 3 * 1.3, cz - 0.6, cx + 0.6, yb + 1.6 + 3 * 1.3 + 0.4, cz + 0.6, 0x2A2A2A); // cap
        Prims.end();
    }

    private static void rect(int x0, int y0, int x1, int y1, int argb) {
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        GlStateManager.enableBlend();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        int a = (argb >>> 24) & 255, r = (argb >> 16) & 255, g = (argb >> 8) & 255, bl = argb & 255;
        b.pos(x0, y1, 0).color(r, g, bl, a).endVertex();
        b.pos(x1, y1, 0).color(r, g, bl, a).endVertex();
        b.pos(x1, y0, 0).color(r, g, bl, a).endVertex();
        b.pos(x0, y0, 0).color(r, g, bl, a).endVertex();
        t.draw();
    }

    private static void text(String s, int cx, int y, int col, boolean centre, float scale) {
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, y, -0.1);
        GlStateManager.scale(scale, scale, 1);
        fr.drawString(s, centre ? -fr.getStringWidth(s) / 2 : 0, 0, col);
        GlStateManager.popMatrix();
    }

    @Override
    public boolean isGlobalRenderer(TileDispatcherBoard te) { return true; }
}
