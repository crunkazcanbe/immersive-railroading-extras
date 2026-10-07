package com.dogpound.railmap.client.render;

import com.dogpound.railmap.client.ClientTrains;
import com.dogpound.railmap.graph.RailNetwork;
import com.dogpound.railmap.graph.RailNode;
import com.dogpound.railmap.graph.SignalNode;
import com.dogpound.railmap.graph.TrainNode;
import com.dogpound.railmap.program.BlockNXDesk;
import com.dogpound.railmap.program.NXLayout;
import com.dogpound.railmap.program.TileNXDesk;
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

/**
 * The NX desk's lit mimic diagram: the real track as glowing lines, every signal a push button in
 * its live aspect colour (the entrance you pressed flashes white, set routes light their buttons
 * white), switches amber, and trains as moving lights with their names, like a train describer.
 */
public class NXDeskRenderer extends TileEntitySpecialRenderer<TileNXDesk> {
    @Override
    public void render(TileNXDesk te, double x, double y, double z, float pt, int destroy, float alpha) {
        if (te.getWorld() == null) return;
        IBlockState st = te.getWorld().getBlockState(te.getPos());
        if (!(st.getBlock() instanceof BlockNXDesk)) return;
        RailNetwork net = te.getNetwork();
        EnumFacing f = st.getValue(BlockNXDesk.FACING);
        NXLayout L = new NXLayout(net, f);
        double top = NXLayout.TOP + 0.012;
        long now = te.getWorld().getTotalWorldTime();

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        Prims.begin();
        // panel glass
        double[] a = NXLayout.toLocal(NXLayout.X0, NXLayout.Z0, f), b = NXLayout.toLocal(NXLayout.X1, NXLayout.Z1, f);
        Prims.box(Math.min(a[0], b[0]), top - 0.003, Math.min(a[1], b[1]), Math.max(a[0], b[0]), top - 0.001, Math.max(a[1], b[1]), 0x06101C, 1f);
        if (!L.empty) {
            // track
            Tessellator tess = Tessellator.getInstance();
            BufferBuilder buf = tess.getBuffer();
            GL11.glLineWidth(3f);
            for (RailNode n : net.nodes) {
                int col = n.kind == RailNode.Kind.SWITCH ? 0xFFC44D : 0xB4C4D4;
                buf.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
                for (int i = 0; i + 2 < n.points.length; i += 3) {
                    double[] p = L.onDesk(n.points[i], n.points[i + 2]);
                    buf.pos(p[0], top, p[1]).color((col >> 16) & 255, (col >> 8) & 255, col & 255, 255).endVertex();
                }
                tess.draw();
            }
            GL11.glLineWidth(1f);
            // signal buttons
            java.util.Set<Long> routed = new java.util.HashSet<>();
            for (long r : te.routeStarts()) routed.add(r);
            for (long r : te.routeEnds()) routed.add(r);
            for (SignalNode s : net.signals) {
                double[] p = L.onDesk(s.pos.getX() + 0.5, s.pos.getZ() + 0.5);
                if (!L.onTop(p[0], p[1])) continue;
                int col = switch (s.aspect) {
                    case RED -> 0xFF3030; case YELLOW -> 0xFFC400; case GREEN -> 0x30E060; default -> 0x7A7F86;
                };
                boolean ent = te.entrance() == s.pos.toLong();
                if (ent && (now / 5) % 2 == 0) col = 0xFFFFFF;
                if (routed.contains(s.pos.toLong())) col = 0xF0F0FF;
                double h = 0.022;
                Prims.box(p[0] - h - 0.006, top, p[1] - h - 0.006, p[0] + h + 0.006, top + 0.008, p[1] + h + 0.006, 0x202326, 1f);
                Prims.box(p[0] - h, top, p[1] - h, p[0] + h, top + 0.02, p[1] + h, col, 1f);
            }
            // trains: moving lights (white = loco)
            for (TrainNode t : ClientTrains.get()) {
                double[] p = L.onDesk(t.x, t.z);
                if (!L.onTop(p[0], p[1])) continue;
                int col = t.lead ? 0xFFFFFF : 0x5BCEFA;
                Prims.box(p[0] - 0.012, top + 0.002, p[1] - 0.012, p[0] + 0.012, top + 0.014, p[1] + 0.012, col, 1f);
            }
        }
        Prims.end();
        // train describer labels
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        if (!L.empty) for (TrainNode t : ClientTrains.get()) {
            if (!t.lead) continue;
            double[] p = L.onDesk(t.x, t.z);
            if (!L.onTop(p[0], p[1])) continue;
            String name = t.tag != null && !t.tag.isEmpty() ? t.tag : t.name;
            if (name.length() > 14) name = name.substring(0, 14);
            GlStateManager.pushMatrix();
            GlStateManager.translate(p[0], top + 0.016, p[1] - 0.035);
            GlStateManager.rotate(-90, 1, 0, 0);
            GlStateManager.rotate(-(f.getHorizontalIndex() * 90f + 180f), 0, 0, 1);
            GlStateManager.scale(0.0022, -0.0022, 0.0022);
            font.drawString(name, -font.getStringWidth(name) / 2, -4, 0x9CFFC0);
            GlStateManager.popMatrix();
        }
        if (L.empty) {
            GlStateManager.pushMatrix();
            double[] c = NXLayout.toLocal(0.5, 1.0, f);
            GlStateManager.translate(c[0], top + 0.01, c[1]);
            GlStateManager.rotate(-90, 1, 0, 0);
            GlStateManager.scale(0.006, -0.006, 0.006);
            String m = "NO TRACK MAPPED - place near the railway";
            font.drawString(m, -font.getStringWidth(m) / 2, -4, 0xFF6B6B);
            GlStateManager.popMatrix();
        }
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    @Override
    public boolean isGlobalRenderer(TileNXDesk te) {
        return true;
    }
}
