package com.dogpound.railmap.client.gui;

import com.dogpound.railmap.track.ItemSurveyor;
import com.dogpound.railmap.track.Survey;
import com.dogpound.railmap.track.SurveyMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Locale;

/**
 * The Surveyor's instrument screen (her list #18 "a surveyor's handheld instrument could display elevation profiles and
 * proposed speed limits"): the route in plan, its elevation profile against the real ground with cut and fill shaded,
 * and the numbers - length, gradients, radii, proposed speed per section, earthworks and track materials. Live: it
 * re-reads the instrument every frame, so add waypoints with it open in a second window... or just reopen it.
 */
public class GuiSurvey extends GuiScreen {
    private PrideFrame f;
    private int scroll;

    public static void open(ItemStack st) { Minecraft.getMinecraft().displayGuiScreen(new GuiSurvey()); }

    private NBTTagCompound srv() {
        ItemStack st = Minecraft.getMinecraft().player.getHeldItemMainhand();
        if (!(st.getItem() instanceof ItemSurveyor)) st = Minecraft.getMinecraft().player.getHeldItemOffhand();
        if (!(st.getItem() instanceof ItemSurveyor) || st.getTagCompound() == null) return new NBTTagCompound();
        return st.getTagCompound().getCompoundTag("srv");
    }

    private String name() {
        ItemStack st = Minecraft.getMinecraft().player.getHeldItemMainhand();
        return st.getItem() instanceof ItemSurveyor ? ItemSurveyor.routeName(st) : "ROUTE";
    }

    @Override public void initGui() { f = PrideFrame.fit(width, height); }
    @Override public boolean doesGuiPauseGame() { return false; }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        int d = org.lwjgl.input.Mouse.getEventDWheel();
        if (d != 0) scroll = Math.max(0, scroll + (d > 0 ? -1 : 1));
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        drawDefaultBackground();
        NBTTagCompound s = srv();
        List<int[]> pts = Survey.points(s);
        double len = Survey.length(pts);
        f.draw(this, "Surveyor - " + name(), String.format(Locale.ROOT, "§7%d waypoints · %.0f m", pts.size(), len));
        int lw = f.cw * 3 / 5, gap = 6;
        int planH = f.ch * 11 / 20, profH = f.ch - planH - gap;
        PrideFrame.card(f.cx, f.cy, lw, planH, PrideFrame.BLUE);
        PrideFrame.card(f.cx, f.cy + planH + gap, lw, profH, PrideFrame.PINK);
        PrideFrame.card(f.cx + lw + gap, f.cy, f.cw - lw - gap, f.ch, PrideFrame.DIM);
        fontRenderer.drawString("PLAN", f.cx + 4, f.cy + 3, PrideFrame.BLUE);
        fontRenderer.drawString("ELEVATION PROFILE §7(green ground · orange formation · brown cut · tan fill)", f.cx + 4, f.cy + planH + gap + 3, PrideFrame.PINK);
        if (pts.size() < 1) {
            fontRenderer.drawString("Right-click the ground with the instrument to place the first waypoint.", f.cx + 8, f.cy + 20, 0xFFCCCCCC);
            super.drawScreen(mx, my, pt);
            return;
        }
        plan(pts, f.cx + 8, f.cy + 14, lw - 16, planH - 22);
        profile(s, pts, len, f.cx + 30, f.cy + planH + gap + 14, lw - 38, profH - 26);
        numbers(s, pts, len, f.cx + lw + gap + 6, f.cy + 6, f.cw - lw - gap - 12, f.ch - 12);
        super.drawScreen(mx, my, pt);
    }

    private void plan(List<int[]> pts, int x, int y, int w, int h) {
        double minX = 1e9, maxX = -1e9, minZ = 1e9, maxZ = -1e9;
        double px = Minecraft.getMinecraft().player.posX, pz = Minecraft.getMinecraft().player.posZ;
        for (int[] p : pts) { minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]); minZ = Math.min(minZ, p[2]); maxZ = Math.max(maxZ, p[2]); }
        minX = Math.min(minX, px); maxX = Math.max(maxX, px); minZ = Math.min(minZ, pz); maxZ = Math.max(maxZ, pz);
        double span = Math.max(16, Math.max(maxX - minX, maxZ - minZ));
        double sc = Math.min(w, h) / span;
        double ox = x + (w - (maxX - minX) * sc) / 2 - minX * sc, oz = y + (h - (maxZ - minZ) * sc) / 2 - minZ * sc;
        // grid every 50 m
        for (double g = Math.floor(minX / 50) * 50; g <= maxX; g += 50) line(ox + g * sc, y, ox + g * sc, y + h, 0x30FFFFFF);
        for (double g = Math.floor(minZ / 50) * 50; g <= maxZ; g += 50) line(x, oz + g * sc, x + w, oz + g * sc, 0x30FFFFFF);
        double[] r = Survey.radii(pts);
        for (int i = 1; i < pts.size(); i++) line(ox + pts.get(i - 1)[0] * sc, oz + pts.get(i - 1)[2] * sc, ox + pts.get(i)[0] * sc, oz + pts.get(i)[2] * sc, 0xFFFFC44D);
        for (int i = 0; i < pts.size(); i++) {
            int sx = (int) (ox + pts.get(i)[0] * sc), sz = (int) (oz + pts.get(i)[2] * sc);
            Gui.drawRect(sx - 2, sz - 2, sx + 2, sz + 2, i == 0 ? 0xFF7CF29A : i == pts.size() - 1 ? 0xFFFF5252 : 0xFFFFFFFF);
            String lab = (i + 1) + (r[i] > 0 ? " R" + Math.round(r[i]) : "");
            fontRenderer.drawString(lab, sx + 4, sz - 4, 0xFFE8ECF0);
        }
        int ppx = (int) (ox + px * sc), ppz = (int) (oz + pz * sc);
        Gui.drawRect(ppx - 1, ppz - 3, ppx + 1, ppz + 3, 0xFF5BCEFA);
        Gui.drawRect(ppx - 3, ppz - 1, ppx + 3, ppz + 1, 0xFF5BCEFA);
        fontRenderer.drawString("N ^  50 m grid", x + w - 70, y + h - 8, 0xFF8A94A0);
    }

    private void profile(NBTTagCompound s, List<int[]> pts, double len, int x, int y, int w, int h) {
        int[] g = s.getIntArray("gnd");
        if (pts.size() < 2 || g.length < 2) { fontRenderer.drawString("Two waypoints make a profile.", x, y + 4, 0xFFCCCCCC); return; }
        double lo = 1e9, hi = -1e9;
        for (int v : g) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
        for (int[] p : pts) { lo = Math.min(lo, p[1]); hi = Math.max(hi, p[1]); }
        lo -= 2; hi += 2;
        double sx = w / Math.max(1, len), sy = h / Math.max(4, hi - lo);
        for (int i = 0; i < g.length; i++) {                          // cut / fill shading
            double ch = i * Survey.STEP, fy = Survey.at(pts, ch)[2];
            int cx = (int) (x + ch * sx);
            int yg = (int) (y + h - (g[i] - lo) * sy), yf = (int) (y + h - (fy - lo) * sy);
            if (g[i] > fy) Gui.drawRect(cx, yg, cx + Math.max(1, (int) (Survey.STEP * sx)), yf, 0x808B5A2B);
            else if (g[i] < fy) Gui.drawRect(cx, yf, cx + Math.max(1, (int) (Survey.STEP * sx)), yg, 0x80D9B26F);
        }
        for (int i = 1; i < g.length; i++) line(x + (i - 1) * Survey.STEP * sx, y + h - (g[i - 1] - lo) * sy, x + i * Survey.STEP * sx, y + h - (g[i] - lo) * sy, 0xFF5FBF4A);
        double[] ch = Survey.chainages(pts);
        for (int i = 1; i < pts.size(); i++) line(x + ch[i - 1] * sx, y + h - (pts.get(i - 1)[1] - lo) * sy, x + ch[i] * sx, y + h - (pts.get(i)[1] - lo) * sy, 0xFFFF8C00);
        for (int i = 0; i < pts.size(); i++) {
            int px = (int) (x + ch[i] * sx);
            line(px, y, px, y + h, 0x30FFFFFF);
            if (i > 0) {
                double pm = SurveyMath.gradePermille(pts.get(i)[1] - pts.get(i - 1)[1], ch[i] - ch[i - 1]);
                String t = SurveyMath.oneIn(pm);
                int mid = (int) (x + (ch[i - 1] + ch[i]) / 2 * sx) - fontRenderer.getStringWidth(t) / 2;
                fontRenderer.drawString(t, mid, y + 2, Math.abs(pm) > 25 ? 0xFFFF5252 : Math.abs(pm) > 10 ? 0xFFFFC44D : 0xFFE8ECF0);
            }
        }
        fontRenderer.drawString(String.valueOf((int) hi), x - 24, y, 0xFF8A94A0);
        fontRenderer.drawString(String.valueOf((int) lo), x - 24, y + h - 8, 0xFF8A94A0);
        fontRenderer.drawString(Survey.chain(0), x, y + h + 2, 0xFF8A94A0);
        String end = Survey.chain(len);
        fontRenderer.drawString(end, x + w - fontRenderer.getStringWidth(end), y + h + 2, 0xFF8A94A0);
    }

    private void numbers(NBTTagCompound s, List<int[]> pts, double len, int x, int y, int w, int h) {
        double[] ew = Survey.earthworks(s), r = Survey.radii(pts), ch = Survey.chainages(pts);
        double maxG = 0, minR = 0;
        for (int i = 1; i < pts.size(); i++) maxG = Math.max(maxG, Math.abs(SurveyMath.gradePermille(pts.get(i)[1] - pts.get(i - 1)[1], ch[i] - ch[i - 1])));
        for (double v : r) if (v > 0 && (minR == 0 || v < minR)) minR = v;
        int ly = y;
        String[] head = {
                "§b" + name(),
                String.format(Locale.ROOT, "§7Length §f%.0f m §7(%s)", len, Survey.chain(len)),
                "§7Ruling grade §f" + SurveyMath.oneIn(maxG) + (maxG > 25 ? " §c(steep)" : ""),
                "§7Tightest curve §f" + (minR > 0 ? "R" + Math.round(minR) + " m · " + Survey.speedText(SurveyMath.curveKmh(minR)) : "none"),
                String.format(Locale.ROOT, "§7Cut §f%.0f m3 §7(deepest %.1f m)", ew[0], ew[2]),
                String.format(Locale.ROOT, "§7Fill §f%.0f m3 §7(highest %.1f m)", ew[1], ew[3]),
                ew[2] > 15 ? "§eDeep cut: a TUNNEL is advised" : ew[3] > 10 ? "§eHigh bank: a VIADUCT is advised" : "§aEarthworks reasonable",
                String.format(Locale.ROOT, "§7Rail §f%.0f m§7 · sleepers §f%d", len * 2, (int) Math.ceil(len / 0.65)),
                String.format(Locale.ROOT, "§7Ballast §f%.0f m3§7 · fastenings §f%d", len * 2.6, (int) Math.ceil(len / 0.65) * 4),
                "",
                "§dSECTIONS §7(grade · curve · proposed speed)"};
        for (String l : head) { fontRenderer.drawString(fontRenderer.trimStringToWidth(l, w), x, ly, 0xFFE8ECF0); ly += 10; }
        List<String> sec = Survey.sections(s);
        int rows = Math.max(1, (y + h - ly - 22) / 10);
        scroll = Math.min(scroll, Math.max(0, sec.size() - rows));
        for (int i = scroll; i < Math.min(sec.size(), scroll + rows); i++) { fontRenderer.drawString(fontRenderer.trimStringToWidth(sec.get(i), w), x, ly, 0xFFCFD8DC); ly += 10; }
        fontRenderer.drawString("§8Sneak-right-click ground: pegs + blueprint", x, y + h - 18, 0xFF8A94A0);
        fontRenderer.drawString("§8Sneak-right-click air: undo · wheel: scroll", x, y + h - 8, 0xFF8A94A0);
    }

    private static void line(double x0, double y0, double x1, double y1, int argb) {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GL11.glLineWidth(2f);
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        int a = argb >>> 24, r = argb >> 16 & 255, g = argb >> 8 & 255, bl = argb & 255;
        b.pos(x0, y0, 0).color(r, g, bl, a).endVertex();
        b.pos(x1, y1, 0).color(r, g, bl, a).endVertex();
        t.draw();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }
}
