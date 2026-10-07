package com.dogpound.railmap.client.render;

import com.dogpound.railmap.station.BlockStationPiece;
import com.dogpound.railmap.station.TileStationPiece;
import net.minecraft.block.state.IBlockState;
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

/** Live faces of the station pieces: the station name, the platform number (+ the train standing there), clock hands. */
public class StationPieceRenderer extends TileEntitySpecialRenderer<TileStationPiece> {

    @Override
    public void render(TileStationPiece te, double x, double y, double z, float pt, int destroy, float alpha) {
        if (te.getWorld() == null) return;
        IBlockState st = te.getWorld().getBlockState(te.getPos());
        if (!(st.getBlock() instanceof BlockStationPiece b)) return;
        EnumFacing f = st.getValue(BlockStationPiece.FACING);
        int yRot = switch (f) { case EAST -> 90; case SOUTH -> 180; case WEST -> 270; default -> 0; };
        int ink = "victorian".equals(b.style) ? 0xEFE3C2 : 0xFFFFFF;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.rotate(-yRot, 0, 1, 0);
        GlStateManager.disableLighting();
        boolean lit = te.cfg().bool("lit");
        if (lit) OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        for (int side = 0; side < 2; side++) {
            GlStateManager.pushMatrix();
            if (side == 0) GlStateManager.rotate(180, 0, 1, 0);       // the front (north) face, then the back
            switch (te.kind()) {
                case "name_sign" -> nameSign(te, ink);
                case "platform_sign" -> platformSign(te, ink);
                case "clock" -> clock(te, pt);
                default -> { }
            }
            GlStateManager.popMatrix();
        }
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private static void text(String s, double cx, double cy, double z, double maxW, double maxH, int colour) {
        if (s.isEmpty()) return;
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int w = Math.max(1, font.getStringWidth(s));
        double k = Math.min(maxW / w, maxH / 8.0);
        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, cy, z);
        GlStateManager.scale(k, -k, k);
        font.drawString(s, -w / 2, -4, colour);
        GlStateManager.popMatrix();
    }

    private static void nameSign(TileStationPiece te, int ink) {
        String s = te.station();
        if (te.cfg().bool("upper")) s = s.toUpperCase();
        double size = te.cfg().num("size") / 100.0;
        text(s.isEmpty() ? "-" : s, 0, 1.0, 1.0 / 16 + 0.004, 1.85 * Math.min(1, size), 0.36 * size, ink);
    }

    private static void platformSign(TileStationPiece te, int ink) {
        String n = te.cfg().num("number") + te.cfg().text("suffix");
        String train = te.train();
        if (train.isEmpty()) {
            text(n, 0, 1.375, 1.0 / 16 + 0.004, 0.5, 0.36, ink);
        } else {
            text(n, 0, 1.47, 1.0 / 16 + 0.004, 0.5, 0.22, ink);
            text(train, 0, 1.24, 1.0 / 16 + 0.004, 0.56, 0.07, 0xFFD23A);
        }
    }

    private static void clock(TileStationPiece te, float pt) {
        double hours, minutes, seconds;
        if ("Real".equals(te.cfg().text("source"))) {
            java.time.LocalTime t = java.time.LocalTime.now();
            hours = t.getHour() % 12 + t.getMinute() / 60.0;
            minutes = t.getMinute() + t.getSecond() / 60.0;
            seconds = t.getSecond();
        } else {
            double ticks = (te.getWorld().getWorldTime() + 6000 + pt) % 24000;   // 0 = 6 am in Minecraft
            double h = ticks / 1000.0;
            hours = h % 12;
            minutes = (h * 60) % 60;
            seconds = (h * 3600) % 60;
        }
        double cz = 1.0 / 16 + 0.004, cy = 1.75;
        GlStateManager.disableTexture2D();
        hand(cy, cz, hours / 12 * 360, 0.15, 0.022, 0x151515);
        hand(cy, cz + 0.001, minutes / 60 * 360, 0.22, 0.016, 0x151515);
        if (te.cfg().bool("seconds")) hand(cy, cz + 0.002, seconds / 60 * 360, 0.23, 0.006, 0xC8102E);
        GlStateManager.enableTexture2D();
    }

    /** a clock hand: thin quad from the centre, angle clockwise from 12 */
    private static void hand(double cy, double z, double deg, double len, double w, int rgb) {
        double a = Math.toRadians(deg), sx = Math.sin(a), sy = Math.cos(a), px = -sy * w, py = sx * w;
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        Tessellator t = Tessellator.getInstance();
        BufferBuilder buf = t.getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        buf.pos(-px - sx * 0.03, cy - py - sy * 0.03, z).color(r, g, b, 1f).endVertex();
        buf.pos(px - sx * 0.03, cy + py - sy * 0.03, z).color(r, g, b, 1f).endVertex();
        buf.pos(px + sx * len, cy + py + sy * len, z).color(r, g, b, 1f).endVertex();
        buf.pos(-px + sx * len, cy - py + sy * len, z).color(r, g, b, 1f).endVertex();
        t.draw();
    }
}
