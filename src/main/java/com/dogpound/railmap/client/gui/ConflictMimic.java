package com.dogpound.railmap.client.gui;

import com.dogpound.railmap.client.ClientRailway;
import com.dogpound.railmap.client.render.MapRenderer;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * Route conflict visualizer (her ChatGPT batch-3 list #15), client side: draws a route request and exactly what
 * blocks it - from the server's "m.v" rows (safety.ConflictView). Two places: a mimic on the console's SAFETY > Routes
 * page ({@link #mimic}), and on the real track of the dispatcher / signal box map ({@link #onMap}). Blockers are
 * lettered squares: P points · R conflicting route (its own line drawn orange) · L locked points · S occupied
 * section · O overlap · B line blocked · I integrity · X isolated signal · E no power.
 */
final class ConflictMimic {
    private ConflictMimic() {}

    /** one decision row parsed */
    static final class D {
        String id = "", time = "", route = "", ago = "";
        boolean granted;
        final List<double[]> path = new ArrayList<>();
        final List<List<double[]>> others = new ArrayList<>();
        final List<String[]> blockers = new ArrayList<>();      // kind, x, z, label
        double[] from, to;
    }

    static List<D> all() {
        List<D> out = new ArrayList<>();
        for (String[] r : ClientRailway.get().safety) {
            if (r.length < 11 || !r[0].equals("m.v")) continue;
            D d = new D();
            d.id = r[1]; d.time = r[2]; d.route = r[3]; d.granted = r[4].equals("1"); d.ago = r[10];
            d.path.addAll(pts(r[5]));
            for (String b : r[6].split("\n")) { String[] p = b.split("\\|", 4); if (p.length == 4) d.blockers.add(p); }
            for (String o : r[7].split("/")) if (!o.isEmpty()) d.others.add(pts(o));
            d.from = pt(r[8]); d.to = pt(r[9]);
            out.add(d);
        }
        return out;
    }

    private static List<double[]> pts(String s) {
        List<double[]> l = new ArrayList<>();
        for (String p : s.split(";")) { double[] v = pt(p); if (v != null) l.add(v); }
        return l;
    }

    private static double[] pt(String p) {
        String[] xz = p.split(",");
        if (xz.length != 2) return null;
        try { return new double[]{Double.parseDouble(xz[0]), Double.parseDouble(xz[1])}; } catch (NumberFormatException e) { return null; }
    }

    static int colour(String kind) {
        switch (kind) {
            case "POINTS": return 0xFFFF5252;
            case "ROUTE": return 0xFFFFA040;
            case "LOCKED": return 0xFFFF80C0;
            case "SECTION": return 0xFFFF3030;
            case "OVERLAP": return 0xFFFFC44D;
            case "LINE": return 0xFFB060FF;
            case "INTEGRITY": return 0xFFFF7070;
            case "ISOLATED": return 0xFF40A0FF;
            default: return 0xFF9AA0A8;
        }
    }

    static char letter(String kind) { return com.dogpound.railmap.safety.ConflictView.letter(kind); }

    // ---- the console mimic ----------------------------------------------------------------------------------------
    /** draws d into the box; returns the label of the blocker under the mouse (or null) */
    static String mimic(FontRenderer fr, D d, int x0, int y0, int x1, int y1, int mx, int my) {
        Gui.drawRect(x0, y0, x1, y1, 0xFF0A0E14);
        Gui.drawRect(x0, y0, x1, y0 + 1, 0xFF3A4A5A);
        Gui.drawRect(x0, y1 - 1, x1, y1, 0xFF3A4A5A);
        double minX = 1e18, maxX = -1e18, minZ = 1e18, maxZ = -1e18;
        List<double[]> every = new ArrayList<>(d.path);
        for (List<double[]> o : d.others) every.addAll(o);
        for (String[] b : d.blockers) every.add(new double[]{SigPage.d(b[1]), SigPage.d(b[2])});
        if (d.from != null) every.add(d.from);
        if (d.to != null) every.add(d.to);
        for (double[] p : every) { minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]); minZ = Math.min(minZ, p[1]); maxZ = Math.max(maxZ, p[1]); }
        if (every.isEmpty()) { fr.drawString("no geometry for this request", x0 + 6, y0 + 6, SigPage.C_DIM); return null; }
        double span = Math.max(16, Math.max(maxX - minX, maxZ - minZ)) * 1.15;
        double cx = (minX + maxX) / 2, cz = (minZ + maxZ) / 2;
        double scale = Math.min(x1 - x0 - 16, y1 - y0 - 16) / span;
        double ox = (x0 + x1) / 2.0, oy = (y0 + y1) / 2.0;
        java.util.function.ToDoubleFunction<double[]> sx = p -> ox + (p[0] - cx) * scale, sy = p -> oy + (p[1] - cz) * scale;
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        for (List<double[]> o : d.others) strip(o, sx, sy, 3f, 0xC0FFA040);
        strip(d.path, sx, sy, 4f, d.granted ? 0xFF3BFF6E : 0xFFD8DCE0);
        GlStateManager.enableTexture2D();
        String hover = null;
        if (d.from != null) tag(fr, (int) sx.applyAsDouble(d.from), (int) sy.applyAsDouble(d.from), "ENT", 0xFF3BFF6E);
        if (d.to != null) tag(fr, (int) sx.applyAsDouble(d.to), (int) sy.applyAsDouble(d.to), "EXIT", 0xFFFFD040);
        long blink = System.currentTimeMillis() / 400 % 2;
        for (String[] b : d.blockers) {
            int bx = (int) sx.applyAsDouble(new double[]{SigPage.d(b[1]), 0}), by = (int) sy.applyAsDouble(new double[]{0, SigPage.d(b[2])});
            int c = colour(b[0]);
            Gui.drawRect(bx - 5, by - 5, bx + 6, by + 6, blink == 0 ? c : 0xFF000000 | (c & 0xFFFFFF) >> 1 & 0x7F7F7F);
            Gui.drawRect(bx - 4, by - 4, bx + 5, by + 5, 0xFF101010);
            fr.drawString(String.valueOf(letter(b[0])), bx - 2, by - 3, c);
            if (mx >= bx - 5 && mx <= bx + 6 && my >= by - 5 && my <= by + 6) hover = b[0] + " at " + b[1] + "," + b[2] + ": " + b[3];
        }
        fr.drawString(Math.round(span) + " m", x1 - 6 - fr.getStringWidth(Math.round(span) + " m"), y1 - 10, SigPage.C_DIM);
        return hover;
    }

    private static void tag(FontRenderer fr, int x, int y, String s, int c) {
        Gui.drawRect(x - 2, y - 2, x + 3, y + 3, c);
        fr.drawString(s, x + 4, y - 8, c);
    }

    private static void strip(List<double[]> pts, java.util.function.ToDoubleFunction<double[]> sx, java.util.function.ToDoubleFunction<double[]> sy, float width, int c) {
        if (pts.size() < 2) return;
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        GL11.glLineWidth(width);
        buf.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        for (double[] p : pts) buf.pos(sx.applyAsDouble(p), sy.applyAsDouble(p), 0).color((c >> 16) & 255, (c >> 8) & 255, c & 255, (c >>> 24) & 255).endVertex();
        tess.draw();
        GL11.glLineWidth(1f);
    }

    // ---- on the dispatcher / signal box map ---------------------------------------------------------------------
    /** the newest refused request of the last 60 s, drawn on the map's real track (call with texture2D disabled) */
    static void onMap(MapRenderer map, FontRenderer fr) {
        D d = null;
        for (D x : all()) if (!x.granted && SigPage.i(x.ago) <= 60) { d = x; break; }
        if (d == null) return;
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        long blink = System.currentTimeMillis() / 400 % 2;
        GL11.glLineWidth(3f);
        for (List<double[]> o : d.others) line(map, buf, tess, o, 0xC0FFA040);
        line(map, buf, tess, d.path, blink == 0 ? 0xE0FF5252 : 0x80FF5252);
        GL11.glLineWidth(1f);
        for (String[] b : d.blockers) {
            double sx = map.toScreenX(SigPage.d(b[1]) + 0.5), sy = map.toScreenY(SigPage.d(b[2]) + 0.5);
            int c = colour(b[0]);
            map.rect(sx - 6, sy - 6, sx + 7, sy + 7, blink == 0 ? c : 0xFF101010);
            map.rect(sx - 4, sy - 4, sx + 5, sy + 5, 0xFF101010);
        }
        GlStateManager.enableTexture2D();
        for (String[] b : d.blockers) {
            int sx = (int) map.toScreenX(SigPage.d(b[1]) + 0.5), sy = (int) map.toScreenY(SigPage.d(b[2]) + 0.5);
            fr.drawStringWithShadow(String.valueOf(letter(b[0])), sx - 2, sy - 3, colour(b[0]));
            fr.drawStringWithShadow(fr.trimStringToWidth(b[3], 180), sx + 9, sy - 3, 0xFFFFE0E0);
        }
        GlStateManager.disableTexture2D();
    }

    private static void line(MapRenderer map, BufferBuilder buf, Tessellator tess, List<double[]> pts, int c) {
        if (pts.size() < 2) return;
        buf.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        for (double[] p : pts) buf.pos(map.toScreenX(p[0]), map.toScreenY(p[1]), 0).color((c >> 16) & 255, (c >> 8) & 255, c & 255, (c >>> 24) & 255).endVertex();
        tess.draw();
    }
}
