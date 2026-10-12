package com.dogpound.railmap.ops6.client;

import com.dogpound.railmap.ops6.PacketOps6;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sitting at the Driver Training Simulator (batch 6 E #25): the big route screen - the line ahead in perspective (rails
 * and sleepers running under you, signals with their aspect, TSR boards, the platform and its stop board, leaf fall,
 * gradient posts; dusk for the night run) - over the cab desk: speed with the limit, power and brake handles, brake
 * cylinder pressure, the AWS sunflower, DSD, EMERGENCY, SLIP / WSP lamps, the distance to the stop, the clock against the
 * timetable and the instructor's running score. Keys: W / S power, R / F brake apply / release, X emergency,
 * Q AWS acknowledge, SPACE DSD vigilance, H horn. The run pauses when you leave the desk (Esc).
 */
public class GuiDriverSim extends GuiScreen {
    final BlockPos pos;
    private NBTTagCompound s = new NBTTagCompound();
    private int tick;
    private double drawPos, lastPos;
    private final List<Object[]> hits = new ArrayList<>();

    GuiDriverSim(BlockPos pos) { this.pos = pos; }

    void update(NBTTagCompound n) { lastPos = s.getDouble("pos"); s = n; }

    @Override public boolean doesGuiPauseGame() { return false; }
    @Override public void initGui() { Ops6Client.send(PacketOps6.of("simpoll").pos(pos)); }

    @Override
    public void updateScreen() {
        if (++tick % 2 == 0) Ops6Client.send(PacketOps6.of("simpoll").pos(pos));
        if (s.getBoolean("aws") && tick % 10 == 0) mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.getMasterRecord(net.minecraft.init.SoundEvents.BLOCK_NOTE_BASS, 0.6f));
        if (mc.player != null && mc.player.getDistanceSq(pos) > 10 * 10) mc.displayGuiScreen(null);
    }

    void cmd(String c) { Ops6Client.send(PacketOps6.of("sim").pos(pos).str("c", c)); }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        hits.clear();
        int W = width, H = height;
        int viewH = (int) (H * 0.58);
        boolean night = s.getBoolean("night");
        double kmh = s.getDouble("kmh");
        drawPos += (s.getDouble("pos") - drawPos) * 0.5;
        if (Math.abs(s.getDouble("pos") - drawPos) > 50) drawPos = s.getDouble("pos");
        // ---- the windscreen
        int hz = (int) (viewH * 0.42);
        drawGradientRect(0, 0, W, hz, night ? 0xFF05081A : 0xFF5C8FD0, night ? 0xFF1A2040 : 0xFFB8D4F0);
        drawGradientRect(0, hz, W, viewH, night ? 0xFF0C140C : 0xFF4A6A30, night ? 0xFF16200E : 0xFF6A8A40);
        double f = W * 0.55, camH = 3.0;
        int cx = W / 2;
        // ballast + rails + sleepers (perspective: screen y = hz + f * camH / d)
        for (double d = 400; d > 3; d *= 0.93) {
            double d2 = d * 0.93;
            int y1 = hz + (int) (f * camH / d), y2 = hz + (int) (f * camH / d2);
            int bw1 = (int) (f * 2.2 / d), bw2 = (int) (f * 2.2 / d2);
            trapezoid(cx, y1, bw1, y2, bw2, night ? 0xFF2A2A26 : 0xFF6E685E);
        }
        double off = drawPos % 0.65;
        for (double d = 0.65 - off + 3; d < 200; d += 0.65) {                // sleepers scroll under the cab
            int y = hz + (int) (f * camH / d), w = (int) (f * 1.3 / d), h = Math.max(1, (int) (f * 0.12 / d));
            Gui.drawRect(cx - w, y, cx + w, y + h, night ? 0xFF2A1E14 : 0xFF5A4028);
        }
        for (int side = -1; side <= 1; side += 2) {
            for (double d = 400; d > 3; d *= 0.93) {
                double d2 = d * 0.93;
                int x1 = cx + (int) (f * 0.75 * side / d), x2 = cx + (int) (f * 0.75 * side / d2);
                int y1 = hz + (int) (f * camH / d), y2 = hz + (int) (f * camH / d2);
                line(x1, y1, x2, y2, Math.max(1, (int) (f * 0.07 / d2)), night ? 0xFF8A8A90 : 0xFFC8C8D0);
            }
        }
        NBTTagList ahead = s.getTagList("ahead", 8);
        List<String[]> objs = new ArrayList<>();
        for (int i = 0; i < ahead.tagCount(); i++) objs.add(ahead.getStringTagAt(i).split("\\|"));
        objs.sort((a, b) -> Double.compare(Double.parseDouble(b[1]), Double.parseDouble(a[1])));   // far first
        for (String[] o : objs) {
            double d = Math.max(4, Double.parseDouble(o[1]) - (s.getDouble("pos") - drawPos));
            int gy = hz + (int) (f * camH / d);
            switch (o[0]) {
                case "S" -> {
                    int x = cx + (int) (f * 2.6 / d), top = hz + (int) (f * (camH - 5.0) / d);
                    int pw = Math.max(1, (int) (f * 0.18 / d));
                    Gui.drawRect(x - pw, top, x + pw, gy, 0xFF303030);
                    int hw = Math.max(2, (int) (f * 0.45 / d)), hh = Math.max(4, (int) (f * 1.3 / d));
                    Gui.drawRect(x - hw, top - hh, x + hw, top, 0xFF101010);
                    int asp = Integer.parseInt(o[2]);
                    int col = asp == 0 ? 0xFFFF2020 : asp == 3 ? 0xFF20FF40 : 0xFFFFC020;
                    int r = Math.max(1, (int) (f * 0.14 / d));
                    int ly = top - hh / 4;
                    Gui.drawRect(x - r, ly - r, x + r, ly + r, col);
                    if (asp == 2) Gui.drawRect(x - r, top - hh * 3 / 4 - r, x + r, top - hh * 3 / 4 + r, col);
                    if (d < 600) fontRenderer.drawStringWithShadow(Math.round(d) + "m", x + hw + 2, top - hh, 0xFFFFFF);
                }
                case "L" -> {
                    int x = cx - (int) (f * 2.4 / d), top = hz + (int) (f * (camH - 2.5) / d), bw = Math.max(3, (int) (f * 0.6 / d));
                    Gui.drawRect(x - 1, top, x + 1, gy, 0xFF404040);
                    Gui.drawRect(x - bw, top - bw * 2, x + bw, top, 0xFFF0D020);
                    if (d < 500) fontRenderer.drawString(o[2], x - fontRenderer.getStringWidth(o[2]) / 2, top - bw - 4, 0x101010);
                }
                case "P" -> {
                    for (double q = d + 150; q > Math.max(4, d - 10); q -= 4) {         // platform on the left, its stop board at the mark
                        double q2 = Math.max(4, q - 4);
                        int y1 = hz + (int) (f * camH / q), y2 = hz + (int) (f * camH / q2);
                        int xa = cx - (int) (f * 1.8 / q), xb = cx - (int) (f * 5.0 / q);
                        Gui.drawRect(xb, y1 - (int) (f * 1.0 / q), xa, y2, night ? 0xFF505058 : 0xFF9A9A9E);
                    }
                    int x = cx - (int) (f * 1.9 / d), top = hz + (int) (f * (camH - 2.8) / d), bw = Math.max(3, (int) (f * 0.5 / d));
                    Gui.drawRect(x - bw, top - bw, x + bw, top + bw, 0xFFFFFFFF);
                    if (d < 300) fontRenderer.drawString("STOP", x - fontRenderer.getStringWidth("STOP") / 2, top - 3, 0x101010);
                }
                case "F" -> { int y = hz + (int) (f * camH / Math.max(4, d)); Gui.drawRect(cx - W / 3, y, cx + W / 3, y + 2, 0xC0804018); fontRenderer.drawStringWithShadow("§6LEAF FALL", cx + W / 3 - 60, y - 10, 0xFFFFFF); }
                case "G" -> { int x = cx + (int) (f * 3.2 / d), top = hz + (int) (f * (camH - 1.8) / d); Gui.drawRect(x - 1, top, x + 1, gy, 0xFF404040); fontRenderer.drawStringWithShadow(o[2] + "‰", x - 6, top - 8, 0xFFFFFF); }
                default -> { }
            }
        }
        if (s.getBoolean("wet") || night) Gui.drawRect(0, 0, W, viewH, night ? 0x40000010 : 0x20304050);
        Gui.drawRect(0, viewH - 6, W, viewH, 0xFF1A1A1E);                   // cab window frame
        // ---- the desk
        Gui.drawRect(0, viewH, W, H, 0xFF22262C);
        int dy = viewH + 8;
        if (!s.getBoolean("has")) { fontRenderer.drawStringWithShadow("§7No run - START at the desk", 12, dy, 0xFFFFFF); }
        double lim = s.getDouble("lim");
        // speedometer: big digits + a bar with the limit mark
        String sp = String.format(Locale.ROOT, "%.0f", kmh);
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.scale(3, 3, 1);
        fontRenderer.drawStringWithShadow(sp, 12 / 3f, dy / 3f, kmh > lim + 3 ? 0xFF4040 : 0xFFFFFF);
        net.minecraft.client.renderer.GlStateManager.popMatrix();
        fontRenderer.drawString("KM/H", 14 + fontRenderer.getStringWidth(sp) * 3, dy + 12, 0xA0A0A0);
        int bx = 12, by = dy + 30, bw = 200;
        Gui.drawRect(bx, by, bx + bw, by + 8, 0xFF101010);
        Gui.drawRect(bx, by, bx + (int) (bw * Math.min(1, kmh / 160)), by + 8, kmh > lim + 3 ? 0xFFE03030 : 0xFF30C050);
        int lx = bx + (int) (bw * Math.min(1, lim / 160));
        Gui.drawRect(lx - 1, by - 3, lx + 1, by + 11, 0xFFFFD020);
        fontRenderer.drawString("LIMIT " + Math.round(lim), bx, by + 12, 0xFFD020);
        // power + brake handles
        int hx = 230;
        fontRenderer.drawString("POWER", hx, dy, 0xA0A0A0);
        for (int i = 0; i <= 8; i++) Gui.drawRect(hx + i * 9, dy + 12, hx + i * 9 + 7, dy + 22, i <= s.getInteger("notch") && i > 0 ? 0xFF30C050 : i <= Math.round(s.getDouble("notchEff")) && i > 0 ? 0xFF206030 : 0xFF404448);
        fontRenderer.drawString("BRAKE", hx, dy + 28, 0xA0A0A0);
        for (int i = 1; i <= 8; i++) Gui.drawRect(hx + (i - 1) * 9, dy + 40, hx + (i - 1) * 9 + 7, dy + 50, i <= s.getInteger("brake") ? (i == 8 ? 0xFFFF2020 : 0xFFE09020) : 0xFF404448);
        fontRenderer.drawString(String.format(Locale.ROOT, "BC %.1f BAR", s.getDouble("bc") * 3.8), hx, dy + 54, 0xC0C0C0);
        // lamps
        int lpx = 330;
        lamp(lpx, dy, "AWS", s.getBoolean("aws") ? ((tick / 4) % 2 == 0 ? 0xFFFFD020 : 0xFF101010) : 0xFF101010, s.getBoolean("aws"));
        lamp(lpx + 46, dy, "DSD", s.getBoolean("vig") ? 0xFFFF8020 : 0xFF303030, s.getBoolean("vig"));
        lamp(lpx, dy + 30, "EMERG", s.getBoolean("eb") ? 0xFFFF2020 : 0xFF303030, s.getBoolean("eb"));
        lamp(lpx + 46, dy + 30, s.getBoolean("slide") ? "WSP" : "SLIP", s.getBoolean("slip") || s.getBoolean("slide") ? 0xFFFFD020 : 0xFF303030, false);
        // info
        int ix = 430;
        fontRenderer.drawStringWithShadow("§f" + s.getString("name"), ix, dy, 0xFFFFFF);
        fontRenderer.drawString(String.format(Locale.ROOT, "STOP IN %.0f M", Math.max(0, s.getDouble("stop"))), ix, dy + 12, 0xFFFFFF);
        fontRenderer.drawString(String.format(Locale.ROOT, "TIME %.0f / %.0f S", s.getDouble("t"), s.getDouble("target")), ix, dy + 22, s.getDouble("t") > s.getDouble("target") ? 0xFF8060 : 0xC0C0C0);
        fontRenderer.drawString(String.format(Locale.ROOT, "RAIL %s  GRADE %+.0f‰", s.getDouble("mu") < 0.1 ? "LEAVES" : s.getDouble("mu") < 0.25 ? "WET" : "DRY", s.getDouble("grade")), ix, dy + 32, 0xC0C0C0);
        fontRenderer.drawStringWithShadow(String.format(Locale.ROOT, "SCORE %.0f", s.getDouble("score")), ix, dy + 44, s.getDouble("score") >= 75 ? 0x60FF80 : 0xFF6060);
        NBTTagList ev = s.getTagList("ev", 8);
        for (int i = 0; i < Math.min(6, ev.tagCount()); i++) fontRenderer.drawString(fontRenderer.trimStringToWidth(ev.getStringTagAt(i), W - ix - 150), ix, dy + 58 + i * 10, 0x909090);
        // buttons
        int by2 = H - 26, bx2 = 12;
        String[][] b = {{"pu", "POWER + (W)"}, {"pd", "POWER - (S)"}, {"bu", "BRAKE + (R)"}, {"bd", "BRAKE - (F)"}, {"eb", "EMERG (X)"}, {"aws", "AWS (Q)"}, {"vig", "DSD (SPACE)"}, {"horn", "HORN (H)"}};
        int bw2 = Math.min(84, (W - 24 - 2 * 70) / b.length - 4);
        for (String[] x : b) { button(bx2, by2, bw2, 18, x[1], x[0].equals("eb") ? 0xFFA01818 : x[0].equals("aws") ? 0xFF202020 : 0xFF3A4048, mx, my, x[0]); bx2 += bw2 + 4; }
        button(W - 140, by2, 64, 18, "START", 0xFF1F7A3A, mx, my, "start");
        button(W - 72, by2, 60, 18, "ABORT", 0xFF7A1F1F, mx, my, "abort");
        // overlays
        if (s.getBoolean("fin")) banner((s.getBoolean("pass") ? "§a" : "§c") + s.getString("result"), viewH / 2);
        else if (s.getBoolean("eb")) banner("§cEMERGENCY BRAKE (" + s.getString("ebWhy") + ") - STOP, BRAKE TO EMERG (X) TO RESET", viewH / 2);
        else if (!s.getBoolean("live")) banner("§cSIMULATOR DARK - DESK KEY / POWER", viewH / 2);
        else if (s.getBoolean("aws")) banner("§eAWS WARNING - ACKNOWLEDGE (Q)", viewH / 3);
        else if (s.getBoolean("vig")) banner("§6DSD - PRESS SPACE", viewH / 3);
        fontRenderer.drawStringWithShadow("§7Trainee: §f" + s.getString("who") + "   §7Esc leaves the desk (the run pauses)", 8, 6, 0xFFFFFF);
        super.drawScreen(mx, my, pt);
    }

    void banner(String t, int y) {
        int w = fontRenderer.getStringWidth(t) + 20;
        Gui.drawRect((width - w) / 2, y - 6, (width + w) / 2, y + 12, 0xC0000000);
        fontRenderer.drawStringWithShadow(t, (width - fontRenderer.getStringWidth(t)) / 2f, y, 0xFFFFFF);
    }

    void lamp(int x, int y, String label, int col, boolean glow) {
        if (glow) Gui.drawRect(x - 2, y - 2, x + 42, y + 20, (col & 0x00FFFFFF) | 0x50000000);
        Gui.drawRect(x, y, x + 40, y + 18, col);
        fontRenderer.drawString(label, x + (40 - fontRenderer.getStringWidth(label)) / 2, y + 5, glow ? 0x101010 : 0xA0A0A0);
    }

    void button(int x, int y, int w, int h, String label, int col, int mx, int my, String c) {
        boolean over = mx >= x && my >= y && mx < x + w && my < y + h;
        Gui.drawRect(x, y, x + w, y + h, over ? GuiBeltpack.brighten(col, 30) : col);
        fontRenderer.drawString(fontRenderer.trimStringToWidth(label, w - 2), x + (w - Math.min(w - 2, fontRenderer.getStringWidth(label))) / 2, y + 5, 0xFFFFFF);
        hits.add(new Object[]{x, y, w, h, c});
    }

    /** a horizontal band from (y1, half-width w1) to (y2, w2) around cx, drawn as rows */
    void trapezoid(int cx, int y1, int w1, int y2, int w2, int col) {
        if (y2 <= y1) { Gui.drawRect(cx - w1, y1, cx + w1, y1 + 1, col); return; }
        for (int y = y1; y < y2; y++) { int w = w1 + (w2 - w1) * (y - y1) / (y2 - y1); Gui.drawRect(cx - w, y, cx + w, y + 1, col); }
    }

    /** a rail: from (x1, y1) far to (x2, y2) near, t px wide, drawn row by row */
    void line(int x1, int y1, int x2, int y2, int t, int col) {
        if (y2 <= y1) { Gui.drawRect(x1 - t / 2, y1, x1 + (t + 1) / 2, y1 + 1, col); return; }
        for (int y = y1; y < y2; y++) { int x = x1 + (x2 - x1) * (y - y1) / (y2 - y1); Gui.drawRect(x - t / 2, y, x + (t + 1) / 2, y + 1, col); }
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws java.io.IOException {
        super.mouseClicked(mx, my, button);
        for (Object[] h : hits) {
            int x = (int) h[0], y = (int) h[1], w = (int) h[2], hh = (int) h[3];
            if (mx >= x && my >= y && mx < x + w && my < y + hh) { cmd((String) h[4]); mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.getMasterRecord(net.minecraft.init.SoundEvents.UI_BUTTON_CLICK, 1f)); return; }
        }
    }

    @Override
    protected void keyTyped(char c, int key) throws java.io.IOException {
        switch (key) {
            case Keyboard.KEY_W -> cmd("pu");
            case Keyboard.KEY_S -> cmd("pd");
            case Keyboard.KEY_R -> cmd("bu");
            case Keyboard.KEY_F -> cmd("bd");
            case Keyboard.KEY_X -> cmd("eb");
            case Keyboard.KEY_Q -> cmd("aws");
            case Keyboard.KEY_SPACE -> cmd("vig");
            case Keyboard.KEY_H -> cmd("horn");
            default -> super.keyTyped(c, key);
        }
    }
}
