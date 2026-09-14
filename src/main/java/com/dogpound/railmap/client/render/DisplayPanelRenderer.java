package com.dogpound.railmap.client.render;

import com.dogpound.railmap.block.TileDisplayPanel;
import com.dogpound.railmap.client.ClientTrains;
import com.dogpound.railmap.graph.LogEntry;
import com.dogpound.railmap.graph.RailNetwork;
import com.dogpound.railmap.graph.TrainNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * Draws the merged wall screen. Only the controller panel (viewer's bottom-left) renders,
 * covering the whole cols×rows rectangle in one pass at {@link #PX} pixels per block, so a
 * bigger wall is literally a higher-resolution screen. The map auto-fits the network; a
 * status strip along the bottom shows train count and the last timetable line.
 * <p>
 * The quad sits at local z = 13.15/16, in front of every model part (bars 13.4, screws 13.25,
 * LED 13.2) so interior bezels vanish; a 1/16 inset keeps the outer bezel visible.
 */
public class DisplayPanelRenderer extends TileEntitySpecialRenderer<TileDisplayPanel> {
    public static final int PX = 64;
    private static final double PLANE = (13.15 - 8) / 16.0;   // from block centre toward the back
    private static final double INSET = 1 / 16.0;

    private final MapRenderer map = new MapRenderer();

    @Override
    public void render(TileDisplayPanel te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        if (te.getWorld() == null || !te.isController()) return;
        int cols = te.cols(), rows = te.rows();
        EnumFacing f = te.facing();
        EnumFacing r = TileDisplayPanel.right(f);
        RailNetwork net = te.getNetwork();
        List<TrainNode> trains = ClientTrains.get();

        GlStateManager.pushMatrix();
        // Block centre, then back to the screen plane, then to the viewer's top-left corner.
        GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5);
        GlStateManager.translate(-f.getXOffset() * PLANE, 0, -f.getZOffset() * PLANE);
        GlStateManager.translate(-r.getXOffset() * 0.5, rows - 0.5, -r.getZOffset() * 0.5);
        GlStateManager.rotate(-f.getHorizontalIndex() * 90f, 0, 1, 0); // local +X -> r
        GlStateManager.scale(1, -1, 1);                                // local +Y -> down
        GlStateManager.translate(INSET, INSET, 0);
        double scale = 1.0 / PX;
        GlStateManager.scale(scale, scale, scale);

        int w = (int) Math.round((cols - 2 * INSET) * PX), h = (int) Math.round((rows - 2 * INSET) * PX);
        map.w = w;
        map.h = h;
        map.labels = cols >= 2 || rows >= 2;
        map.grid = true;
        map.origin = null;
        map.fit(net, te.getPos(), 6);

        // Screens glow: full-bright, no lighting, draw on top of the model face.
        GlStateManager.disableLighting();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        GlStateManager.disableCull();
        GlStateManager.depthMask(true);
        GL11.glPushAttrib(GL11.GL_LINE_BIT);

        // Keep a status strip at the bottom out of the map area.
        int strip = h >= 3 * PX / 2 ? 12 : 0;
        map.h = h - strip;
        map.draw(net, trains, -1, -1);
        map.h = h;
        if (strip > 0) drawStatus(net, trains, w, h, strip);
        map.drawChrome();

        GL11.glPopAttrib();
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private void drawStatus(RailNetwork net, List<TrainNode> trains, int w, int h, int strip) {
        map.rect(0, h - strip, w, h, 0xFF000000);
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int leads = 0;
        for (TrainNode t : trains) if (t.lead) leads++;
        String left = leads + (leads == 1 ? " train" : " trains") + "  " + net.signals.size() + " signals  " + net.stops.size() + " stops";
        String right = "";
        if (!net.log.isEmpty()) {
            LogEntry e = net.log.get(net.log.size() - 1);
            right = e.train + (e.arrive ? " arrived " : " left ") + e.station + "  " + LogEntry.clock(e.time);
        } else if (!ClientTrains.live()) {
            right = "no train feed";
        }
        GlStateManager.enableTexture2D();
        font.drawString(left, 3, h - strip + 2, MapRenderer.COL_DIM);
        int rw = font.getStringWidth(right);
        if (rw + 3 + font.getStringWidth(left) + 6 < w) font.drawString(right, w - rw - 3, h - strip + 2, MapRenderer.COL_STATION);
        GlStateManager.disableTexture2D();
    }

    @Override
    public boolean isGlobalRenderer(TileDisplayPanel te) {
        return false;
    }
}
