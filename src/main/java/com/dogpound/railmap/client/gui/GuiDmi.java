package com.dogpound.railmap.client.gui;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.auto.DriverAids;
import com.dogpound.railmap.client.CabDisplay;
import com.dogpound.railmap.network.PacketCab;
import com.dogpound.railmap.network.PacketCabKey;
import com.dogpound.railmap.sim.AuxSim;
import com.dogpound.railmap.sim.CabData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The full driver's display (her ChatGPT list item 109), laid out like an ETCS DMI: on the left the distance-to-target
 * bar, the circular speedometer with the permitted-speed hook, the target speed and the release; in the middle the
 * planning area (speed profile, signals at danger and the next stop on a log distance scale, the gradient profile
 * beside it); along the bottom the brake and air gauges, notch and reverser; on the right the soft keys you click.
 * Pages: MAIN, AUX (auxiliary systems switches), MU (the consist: lead, MU, distributed power), FAULT. Every key sends
 * PacketCabKey to the server, which acts on the train you ride (sim.CabKeys). The game keeps running while it is open.
 */
public class GuiDmi extends GuiScreen {
    private static final int BG = 0xFF031026, PANEL = 0xFF0A1A3A, GRID = 0xFF223A66, WHITE = 0xFFF0F4F8, GREY = 0xFF8FA0B8,
            YELLOW = 0xFFFFD000, ORANGE = 0xFFFF8C00, RED = 0xFFE81E1E, GREEN = 0xFF2EC24B, BLUE = 0xFF5BB0FF;
    private static int page;
    private static final String[] PAGES = {"MAIN", "AUX", "MU", "FAULT"};
    private PrideFrame f;
    private final List<int[]> keyBox = new ArrayList<>();
    private final List<String> keyId = new ArrayList<>();

    @Override public boolean doesGuiPauseGame() { return false; }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        f = PrideFrame.sized(width, height, 640, 400);
        PacketCab p = CabDisplay.current();
        f.draw(this, "Driver's Display", p == null ? "no data" : p.unit + " · " + (p.mode.isEmpty() ? "ATP" : p.mode));
        keyBox.clear();
        keyId.clear();
        FontRenderer fr = fontRenderer;
        int x = f.cx, y = f.cy, w = f.cw, h = f.ch;
        Gui.drawRect(x, y, x + w, y + h, BG);
        // soft keys column (right) + page tabs (bottom right)
        int kx = x + w - 58, kw = 56;
        if (p == null) {
            fr.drawStringWithShadow("No display data from the train.", x + 12, y + 14, WHITE);
            fr.drawStringWithShadow("Ride a locomotive with train protection or driver aids on", x + 12, y + 28, GREY);
            fr.drawStringWithShadow("(config: trainProtection / cabDisplay).", x + 12, y + 40, GREY);
            return;
        }
        int tabW = (w - 64) / PAGES.length;
        for (int i = 0; i < PAGES.length; i++)
            key(x + 2 + i * tabW, y + h - 16, tabW - 2, 14, PAGES[i], "page" + i, i == page ? 0xFF2E4C8C : PANEL, mx, my);
        int bodyH = h - 20;
        switch (page) {
            case 1 -> aux(p, x, y, w - 62, bodyH, kx, kw, mx, my);
            case 2 -> mu(p, x, y, w - 62, bodyH, kx, kw, mx, my);
            case 3 -> faults(p, x, y, w - 62, bodyH, kx, kw, mx, my);
            default -> main(p, x, y, w - 62, bodyH, kx, kw, mx, my);
        }
        // lamp strip along the top of the key column
        boolean blink = (mc.world.getTotalWorldTime() / 6) % 2 == 0;
        int ly = y + h - 18;
        lamp(fr, kx, ly, "FLT", (p.aids & CabData.FAULT) != 0 || p.faultCount > 0, RED, blink);
        lamp(fr, kx + 29, ly, "SLIP", (p.aids & (CabData.SLIP | CabData.SLIDE)) != 0, YELLOW, blink);
        super.drawScreen(mx, my, pt);
    }

    // ---- MAIN: speedometer, planning, gauges -------------------------------------------------------------------------

    private void main(PacketCab p, int x, int y, int w, int h, int kx, int kw, int mx, int my) {
        FontRenderer fr = fontRenderer;
        // A: distance to target bar
        int ax = x + 4, ay = y + 20, aw = 14, ah = h - 70;
        Gui.drawRect(ax, ay, ax + aw, ay + ah, PANEL);
        if (p.distanceToGo >= 0) {
            float frac = (float) (Math.log10(1 + Math.min(1000, p.distanceToGo)) / Math.log10(1001));
            int fill = (int) (ah * frac);
            Gui.drawRect(ax + 3, ay + ah - fill, ax + aw - 3, ay + ah, GREY);
            String d = String.valueOf(Math.round(p.distanceToGo));
            fr.drawString(d, ax + aw / 2 - fr.getStringWidth(d) / 2, ay - 10, WHITE);
        }
        for (int m : new int[]{0, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000}) {
            int ty = ay + ah - (int) (ah * Math.log10(1 + m) / Math.log10(1001));
            Gui.drawRect(ax + aw, ty, ax + aw + (m % 500 == 0 ? 5 : 3), ty + 1, GREY);
        }
        // B: the speedometer
        int r = Math.min((h - 60) / 2, 72);
        int cx = ax + aw + 14 + r, cy = y + 10 + r;
        float max = Math.max(Math.abs(p.speed), Math.max(p.target < 998 ? p.target : 0, p.limit < 998 ? p.limit : 0));
        float scale = max < 135 ? 150 : max < 230 ? 250 : 400;
        Gui.drawRect(cx - r - 2, cy - r - 2, cx + r + 2, cy + r + 2, BG);
        ring(cx, cy, r - 4, r, -144, 144, GRID);
        for (int v = 0; v <= scale; v += scale > 250 ? 20 : 10) {
            double a = Math.toRadians(-144 + 288 * v / scale);
            boolean big = v % (scale > 250 ? 100 : 50) == 0;
            line(cx + Math.sin(a) * (r - (big ? 12 : 8)), cy - Math.cos(a) * (r - (big ? 12 : 8)), cx + Math.sin(a) * (r - 4), cy - Math.cos(a) * (r - 4), WHITE, big ? 2 : 1);
            if (big) {
                String t = String.valueOf(v);
                fr.drawString(t, (int) (cx + Math.sin(a) * (r - 22)) - fr.getStringWidth(t) / 2, (int) (cy - Math.cos(a) * (r - 22)) - 4, WHITE);
            }
        }
        boolean over = Math.abs(p.speed) > p.target + 3, near = !over && Math.abs(p.speed) > p.target - 5;
        // the circular speed gauge (CSG): grey up to target, yellow to permitted, orange/red overspeed
        if (p.target < 998) {
            float tgt = Math.min(scale, p.target);
            ring(cx, cy, r + 1, r + 6, -144, -144 + 288 * tgt / scale, near ? YELLOW : WHITE);
            if (p.limit < 998 && p.limit > p.target) ring(cx, cy, r + 1, r + 6, -144 + 288 * tgt / scale, -144 + 288 * Math.min(scale, p.limit) / scale, GRID);
            if (over) ring(cx, cy, r + 1, r + 6, -144 + 288 * tgt / scale, -144 + 288 * Math.min(scale, Math.abs(p.speed)) / scale, Math.abs(p.speed) > p.target + 8 ? RED : ORANGE);
        }
        // the needle and the digital speed in the hub
        double na = Math.toRadians(-144 + 288 * Math.min(scale, Math.abs(p.speed)) / scale);
        int needle = over ? RED : near ? YELLOW : WHITE;
        line(cx, cy, cx + Math.sin(na) * (r - 10), cy - Math.cos(na) * (r - 10), needle, 3);
        Gui.drawRect(cx - 16, cy - 8, cx + 16, cy + 8, needle);
        String sp = String.valueOf(Math.round(Math.abs(p.speed)));
        fr.drawString(sp, cx - fr.getStringWidth(sp) / 2, cy - 4, 0xFF000000);
        fr.drawString("km/h", cx - fr.getStringWidth("km/h") / 2, cy + 22, GREY);
        String tg = p.target < 998 ? String.valueOf(Math.round(p.target)) : "--";
        fr.drawString("TARGET " + tg, cx - fr.getStringWidth("TARGET " + tg) / 2, cy + 34, p.target < 5 ? RED : YELLOW);
        if (p.limit < 998) fr.drawString("LINE " + Math.round(p.limit), cx - fr.getStringWidth("LINE " + Math.round(p.limit)) / 2, cy + 44, GREY);

        // D: the planning area: distance (log) down the left, speed profile + indications, gradient column
        int dx = cx + r + 16, dw = x + w - dx - 2, dy = y + 4, dh = h - 70;
        Gui.drawRect(dx, dy, dx + dw, dy + dh, PANEL);
        fr.drawString("PLANNING", dx + 3, dy + 2, GREY);
        int gx = dx + 4, gw = 14;                                      // gradient column
        int px0 = gx + gw + 26, pw = dw - (px0 - dx) - 4;               // profile area
        java.util.function.DoubleToIntFunction yOf = m -> dy + dh - 4 - (int) ((dh - 16) * Math.log10(1 + Math.min(4000, m) / 25.0) / Math.log10(1 + 4000 / 25.0));
        for (int m : new int[]{0, 250, 500, 1000, 2000, 4000}) {
            int ty = yOf.applyAsInt(m);
            Gui.drawRect(gx + gw, ty, dx + dw - 2, ty + 1, GRID);
            fr.drawString(m >= 1000 ? (m / 1000) + "k" : String.valueOf(m), gx + gw + 3, ty - 4, GREY);
        }
        for (int i = 0; i + 1 < p.grade.length; i += 2) {             // gradient: + uphill (grey), - downhill (dark)
            int y0 = yOf.applyAsInt(p.grade[i]), y1 = yOf.applyAsInt(p.grade[i] + 100);
            float pm = p.grade[i + 1];
            Gui.drawRect(gx, y1, gx + gw, y0, pm >= 0 ? 0xFF9AA6B8 : 0xFF3A4660);
            if (Math.abs(pm) >= 1 && y0 - y1 >= 9) {
                String t = (pm > 0 ? "+" : "") + Math.round(pm);
                GlStateManager.pushMatrix();
                GlStateManager.translate(gx + 1, y1 + 1, 0);
                GlStateManager.scale(0.5, 0.5, 1);
                fr.drawString(t, 0, 0, pm >= 0 ? 0xFF000000 : WHITE);
                GlStateManager.popMatrix();
            }
        }
        // speed profile: bars whose width is the speed, stepping down at each restriction (PASP)
        float lastD = 0;
        float lastKmh = p.limit < 998 ? p.limit : scale;
        for (int i = 0; i <= p.planD.length; i++) {
            float d = i < p.planD.length ? p.planD[i] : 4000;
            int y0 = yOf.applyAsInt(lastD), y1 = yOf.applyAsInt(d);
            int bw = (int) (pw * Math.min(1, lastKmh / Math.max(40, scale)));
            Gui.drawRect(px0, y1, px0 + bw, y0, 0xFF31507F);
            if (i == p.planD.length) break;
            byte k = p.planKind[i];
            int ty = yOf.applyAsInt(d);
            if (k == 1) {                                                   // signal at danger: red bar, profile to 0
                Gui.drawRect(px0, ty - 2, px0 + pw, ty + 1, RED);
                fr.drawString("STOP", px0 + pw - fr.getStringWidth("STOP") - 1, ty - 11, RED);
                lastKmh = 0;
            } else if (k == 2) {
                Gui.drawRect(px0, ty - 1, px0 + pw, ty, BLUE);
                fr.drawString("STN", px0 + pw - fr.getStringWidth("STN") - 1, ty - 10, BLUE);
            } else if (k == 3) {                                            // neutral section (infra #36): track condition "lower / coast"
                Gui.drawRect(px0, ty - 3, px0 + pw, ty, 0xFFD040E0);
                fr.drawString("NS", px0 + pw - fr.getStringWidth("NS") - 1, ty - 12, 0xFFE070F0);
            } else {
                String t = String.valueOf(Math.round(p.planKmh[i]));
                fr.drawString(t, px0 + 2, ty - 9, p.planKmh[i] < lastKmh ? YELLOW : WHITE);
                lastKmh = p.planKmh[i];
            }
            lastD = d;
        }
        // E: the message line
        int ey = y + h - 62;
        Gui.drawRect(x + 2, ey, x + w - 2, ey + 22, PANEL);
        String msg = p.note.isEmpty() ? (p.advice.isEmpty() ? (p.limitedBy.isEmpty() ? "" : p.limitedBy) : "DAS: " + p.advice) : p.note;
        fr.drawString(fr.trimStringToWidth(msg, w - 10), x + 6, ey + 3, p.note.isEmpty() ? WHITE : ORANGE);
        String ns = p.nextStop.isEmpty() ? "" : "Next: " + p.nextStop + (p.stopM >= 0 ? String.format(Locale.ROOT, " %.1f km", p.stopM / 1000) : "");
        fr.drawString(fr.trimStringToWidth(ns + (p.mu.isEmpty() ? "" : "   " + p.mu.split("\n")[0]), w - 10), x + 6, ey + 12, BLUE);
        // G: gauges along the bottom
        int by = y + h - 36;
        bar(fr, x + 4, by, 90, "MR " + fmt(p.mrBar) + " bar", p.mrBar / 10f, p.mrBar < 6.5f ? RED : GREEN);
        bar(fr, x + 100, by, 90, "BC " + fmt(p.bcBar) + " bar", p.bcBar / 5f, ORANGE);
        bar(fr, x + 196, by, 70, "NOTCH " + Math.round(p.throttle * 100) + "%", p.throttle, GREEN);
        bar(fr, x + 272, by, 70, "BRAKE " + Math.round(p.brake * 100) + "%", p.brake, ORANGE);
        String rv = p.reverser > 0.05 ? "FWD" : p.reverser < -0.05 ? "REV" : "N";
        fr.drawString("REV " + rv, x + 348, by + 2, WHITE);
        if (p.batV >= 0) fr.drawString(String.format(Locale.ROOT, "BAT %.1fV", p.batV), x + 348, by + 12, p.batV < 66 ? RED : GREY);
        // soft keys
        int ky = y + 2;
        boolean alert = (p.aids & (DriverAids.WARNINGS | DriverAids.PENALTIES)) != 0;
        boolean blink = (mc.world.getTotalWorldTime() / 5) % 2 == 0;
        ky = key(kx, ky, kw, 22, "ACK", "ack", alert && blink ? 0xFFB08000 : PANEL, mx, my);
        ky = key(kx, ky, kw, 22, "SAND", "sand", (p.aids & CabData.SAND) != 0 ? 0xFF7A6230 : PANEL, mx, my);
        ky = key(kx, ky, kw, 22, "START", "start", PANEL, mx, my);
        ky = key(kx, ky, kw, 22, "STOP", "stop", PANEL, mx, my);
        ky = key(kx, ky, kw, 22, "FLT RST", "fltreset", p.faultCount > 0 ? 0xFF6A1A1A : PANEL, mx, my);
        ky = key(kx, ky, kw, 22, "HEADLT", "aux:hl", PANEL, mx, my);
        key(kx, ky, kw, 22, "WIPER", "aux:wip", (p.auxBits & AuxSim.B_RAIN) != 0 && blink ? 0xFFB08000 : PANEL, mx, my);
    }

    // ---- AUX ---------------------------------------------------------------------------------------------------------

    private void aux(PacketCab p, int x, int y, int w, int h, int kx, int kw, int mx, int my) {
        FontRenderer fr = fontRenderer;
        int b = p.auxBits;
        fr.drawString("AUXILIARY SYSTEMS", x + 6, y + 4, GREY);
        fr.drawString(fr.trimStringToWidth(p.aux.isEmpty() ? "Auxiliary systems off (config auxSystems)" : p.aux, w - 12), x + 6, y + 16, WHITE);
        String[][] rows = {
                {"Battery switch", (b & AuxSim.B_BATT) != 0 ? "ON" : "OFF"},
                {"Headlights", (b & AuxSim.B_HL_BRIGHT) != 0 ? "BRIGHT" : (b & AuxSim.B_HL_DIM) != 0 ? "DIM" : "OFF"},
                {"Cab lights", on(b, AuxSim.B_CABL)},
                {"Cab HVAC", on(b, AuxSim.B_HVAC) + (p.cabC > -998 ? String.format(Locale.ROOT, "  cab %.0f °C", p.cabC) : "")},
                {"Wipers", on(b, AuxSim.B_WIPERS) + ((b & AuxSim.B_RAIN) != 0 ? "  RAIN - VISIBILITY" : "")},
                {"Compressor", ((b & AuxSim.B_COMP) != 0 ? "AUTO" : "OFF") + ((b & AuxSim.B_COMP_TRIP) != 0 ? "  THERMAL TRIP" : (b & AuxSim.B_COMP_RUN) != 0 ? "  PUMPING" : "")},
                {"Layover heater", on(b, AuxSim.B_LAYOVER)},
                {"Fuel heater", on(b, AuxSim.B_FUELH)},
                {"Aux converter", (b & AuxSim.B_CONV_TRIP) != 0 ? "TRIPPED" : (b & AuxSim.B_SHED) != 0 ? "ON BATTERY - LOADS SHED" : "OK"},
        };
        int ry = y + 32;
        for (String[] r : rows) {
            Gui.drawRect(x + 4, ry, x + w - 4, ry + 13, PANEL);
            fr.drawString(r[0], x + 8, ry + 3, GREY);
            boolean bad = r[1].contains("TRIP") || r[1].contains("SHED") || r[1].contains("RAIN") || r[0].startsWith("Battery") && r[1].equals("OFF");
            fr.drawString(r[1], x + 110, ry + 3, bad ? ORANGE : WHITE);
            ry += 15;
        }
        int by = ry + 6;
        bar(fr, x + 6, by, 140, String.format(Locale.ROOT, "AUX LOAD %.0f / %.0f kW", p.auxKw, p.auxCap), p.auxCap <= 0 ? 1 : p.auxKw / p.auxCap, p.auxKw > p.auxCap ? RED : GREEN);
        bar(fr, x + 156, by, 110, "MR " + fmt(p.mrBar) + " bar", p.mrBar / 10f, p.mrBar < 6.5f ? RED : GREEN);
        bar(fr, x + 276, by, 90, String.format(Locale.ROOT, "BAT %.1f V", Math.max(0, p.batV)), (p.batV - 60) / 15f, p.batV < 66 ? RED : GREEN);
        int ky = y + 2;
        ky = key(kx, ky, kw, 19, "BATT", "aux:bat", (b & AuxSim.B_BATT) != 0 ? 0xFF1E5A2E : 0xFF5A1E1E, mx, my);
        ky = key(kx, ky, kw, 19, "HEADLT", "aux:hl", PANEL, mx, my);
        ky = key(kx, ky, kw, 19, "CAB LT", "aux:cabl", (b & AuxSim.B_CABL) != 0 ? 0xFF2E4C8C : PANEL, mx, my);
        ky = key(kx, ky, kw, 19, "HVAC", "aux:hvac", (b & AuxSim.B_HVAC) != 0 ? 0xFF2E4C8C : PANEL, mx, my);
        ky = key(kx, ky, kw / 2 - 1, 19, "-", "aux:set-", PANEL, mx, my) - 21;
        ky = key(kx + kw / 2 + 1, ky, kw / 2 - 1, 19, "+", "aux:set+", PANEL, mx, my);
        ky = key(kx, ky, kw, 19, "WIPER", "aux:wip", (b & AuxSim.B_WIPERS) != 0 ? 0xFF2E4C8C : PANEL, mx, my);
        ky = key(kx, ky, kw, 19, "COMP", "aux:comp", (b & AuxSim.B_COMP) != 0 ? 0xFF2E4C8C : PANEL, mx, my);
        ky = key(kx, ky, kw, 19, "LAYOVER", "aux:lay", (b & AuxSim.B_LAYOVER) != 0 ? 0xFF2E4C8C : PANEL, mx, my);
        ky = key(kx, ky, kw, 19, "FUEL HT", "aux:fuh", (b & AuxSim.B_FUELH) != 0 ? 0xFF2E4C8C : PANEL, mx, my);
        key(kx, ky, kw, 19, "AUX RST", "aux:reset", (b & (AuxSim.B_CONV_TRIP | AuxSim.B_COMP_TRIP)) != 0 ? 0xFF6A1A1A : PANEL, mx, my);
    }

    // ---- MU ------------------------------------------------------------------------------------------------------------

    private void mu(PacketCab p, int x, int y, int w, int h, int kx, int kw, int mx, int my) {
        FontRenderer fr = fontRenderer;
        fr.drawString("MULTIPLE UNIT / DISTRIBUTED POWER", x + 6, y + 4, GREY);
        String[] l = p.mu.isEmpty() ? new String[]{""} : p.mu.split("\n");
        fr.drawString(l[0].isEmpty() ? "Single unit - nothing to work in multiple" : l[0], x + 6, y + 16, WHITE);
        int ry = y + 32;
        for (int i = 1; i < l.length; i++) {
            boolean lead = l[i].contains(" LEAD ");
            boolean bad = l[i].contains("INCOMPATIBLE") || l[i].contains("CUT OUT") || l[i].contains("NO COMM") || l[i].contains(" F:");
            boolean over = !lead && mx >= x + 4 && mx < x + w - 4 && my >= ry && my < ry + 14;
            Gui.drawRect(x + 4, ry, x + w - 4, ry + 14, over ? 0xFF1C3460 : PANEL);
            fr.drawString(fr.trimStringToWidth(l[i], w - 16), x + 8, ry + 3, lead ? BLUE : bad ? ORANGE : WHITE);
            if (!lead) { keyBox.add(new int[]{x + 4, ry, w - 8, 14}); keyId.add("cut" + i); }
            ry += 16;
        }
        if (l.length > 1) fr.drawString("Click a unit to cut it out of / back into the MU", x + 6, ry + 4, GREY);
        int ky = y + 2;
        ky = key(kx, ky, kw, 22, "START ALL", "start", PANEL, mx, my);
        ky = key(kx, ky, kw, 22, "STOP ALL", "stop", PANEL, mx, my);
        ky = key(kx, ky, kw, 22, "DP MODE", "dp", PANEL, mx, my);
        ky = key(kx, ky, kw, 22, "DP +", "dp+", PANEL, mx, my);
        key(kx, ky, kw, 22, "DP -", "dp-", PANEL, mx, my);
    }

    // ---- FAULT -----------------------------------------------------------------------------------------------------------

    private void faults(PacketCab p, int x, int y, int w, int h, int kx, int kw, int mx, int my) {
        FontRenderer fr = fontRenderer;
        fr.drawString("ACTIVE FAULTS (" + p.faultCount + ")", x + 6, y + 4, GREY);
        int ry = y + 18;
        if (p.faults.isEmpty()) fr.drawString("No faults latched in the consist", x + 8, ry, GREEN);
        else for (String s : p.faults.split("\n")) {
            if (ry > y + h - 20) break;
            fr.drawString(fr.trimStringToWidth(s, w - 16), x + 8, ry, RED);
            ry += 11;
        }
        if (!p.eng1.isEmpty()) fr.drawString(fr.trimStringToWidth(p.eng1, w - 12), x + 6, y + h - 22, WHITE);
        if (!p.eng2.isEmpty()) fr.drawString(fr.trimStringToWidth(p.eng2, w - 12), x + 6, y + h - 11, WHITE);
        int ky = y + 2;
        ky = key(kx, ky, kw, 22, "FLT RST", "fltreset", p.faultCount > 0 ? 0xFF6A1A1A : PANEL, mx, my);
        key(kx, ky, kw, 22, "ACK", "ack", PANEL, mx, my);
    }

    // ---- drawing helpers -----------------------------------------------------------------------------------------------

    /** a soft key; returns the y below it. id null = draw nothing */
    private int key(int x, int y, int w, int h, String label, String id, int col, int mx, int my) {
        if (id == null) return y;
        boolean over = mx >= x && my >= y && mx < x + w && my < y + h;
        Gui.drawRect(x, y, x + w, y + h, 0xFF5A6E92);
        Gui.drawRect(x + 1, y + 1, x + w - 1, y + h - 1, over ? PrideFrame.brighten(col) : col);
        fontRenderer.drawString(label, x + (w - fontRenderer.getStringWidth(label)) / 2, y + (h - 8) / 2, WHITE);
        keyBox.add(new int[]{x, y, w, h});
        keyId.add(id);
        return y + h + 2;
    }

    private void lamp(FontRenderer fr, int x, int y, String t, boolean lit, int col, boolean blink) {
        Gui.drawRect(x, y, x + 27, y + 14, lit && blink ? col : 0xFF1A2333);
        fr.drawString(t, x + 14 - fr.getStringWidth(t) / 2, y + 3, lit ? 0xFF000000 : GREY);
    }

    private static void bar(FontRenderer fr, int x, int y, int w, String label, float frac, int col) {
        Gui.drawRect(x, y + 11, x + w, y + 17, 0xFF1A2333);
        Gui.drawRect(x, y + 11, x + (int) (w * Math.max(0, Math.min(1, frac))), y + 17, col);
        fr.drawString(label, x, y, WHITE);
    }

    private static String on(int b, int bit) { return (b & bit) != 0 ? "ON" : "OFF"; }

    private static String fmt(float v) { return v < 0 ? "--" : String.format(Locale.ROOT, "%.1f", v); }

    private static void ring(int cx, int cy, float r0, float r1, float a0, float a1, int col) {
        if (a1 <= a0) return;
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.color(((col >> 16) & 255) / 255f, ((col >> 8) & 255) / 255f, (col & 255) / 255f, 1f);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION);
        int n = Math.max(2, (int) ((a1 - a0) / 3));
        for (int i = 0; i <= n; i++) {
            double a = Math.toRadians(a0 + (a1 - a0) * i / n);
            b.pos(cx + Math.sin(a) * r1, cy - Math.cos(a) * r1, 0).endVertex();
            b.pos(cx + Math.sin(a) * r0, cy - Math.cos(a) * r0, 0).endVertex();
        }
        Tessellator.getInstance().draw();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.enableTexture2D();
    }

    private static void line(double x0, double y0, double x1, double y1, int col, float width) {
        GlStateManager.disableTexture2D();
        GlStateManager.color(((col >> 16) & 255) / 255f, ((col >> 8) & 255) / 255f, (col & 255) / 255f, 1f);
        GL11.glLineWidth(width * 2);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION);
        b.pos(x0, y0, 0).endVertex();
        b.pos(x1, y1, 0).endVertex();
        Tessellator.getInstance().draw();
        GL11.glLineWidth(1);
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.enableTexture2D();
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws java.io.IOException {
        super.mouseClicked(mx, my, button);
        if (button != 0) return;
        for (int i = keyBox.size() - 1; i >= 0; i--) {
            int[] r = keyBox.get(i);
            if (mx < r[0] || my < r[1] || mx >= r[0] + r[2] || my >= r[1] + r[3]) continue;
            String id = keyId.get(i);
            mc.player.playSound(com.dogpound.railmap.sound.PackSounds.ev("ui.keypad"), 0.5f, 1.0f);
            if (id.startsWith("page")) page = Integer.parseInt(id.substring(4));
            else RailMap.NETWORK.sendToServer(new PacketCabKey(id));
            return;
        }
    }

    @Override
    protected void keyTyped(char c, int code) throws java.io.IOException {
        if (CabDisplay.DMI != null && code == CabDisplay.DMI.getKeyCode()) { mc.displayGuiScreen(null); return; }
        if (CabDisplay.ACK != null && code == CabDisplay.ACK.getKeyCode()) { RailMap.NETWORK.sendToServer(new PacketCabKey("ack")); return; }
        super.keyTyped(c, code);
    }

    @Override
    public void updateScreen() {
        if (mc.player == null || mc.player.getRidingEntity() == null) mc.displayGuiScreen(null);
    }
}
