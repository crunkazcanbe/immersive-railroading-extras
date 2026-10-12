package com.dogpound.railmap.client.gui;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.client.ClientRailway;
import com.dogpound.railmap.ctl.PacketCtlOp;
import com.dogpound.railmap.program.PacketProgram;
import com.dogpound.railmap.program.ProgramData;
import com.dogpound.railmap.program.Rule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * The Control Center's CTRL tab (her "next 48" grid / control / comms items), live from PacketRailState.ctl
 * (ctl.CtlConsole): PQ (harmonic spectrum per network + filters), MICROGRID, PLAN (production plan), COMMS (RTUs + which
 * telemetry can be trusted), PIS (the live feed), RCA (the incident dependency graph + evidence), DASH (drag-and-drop
 * dashboard designer, saved per user / per role), RULES (the visual automation designer over the signal-box program),
 * APPROVE (requests, approve / reject, audit trail), CHAOS (scenarios + report), TUNNEL (sections, airflow) and GIS.
 */
final class CtlPage {
    private static final String[] PAGES = {"PQ", "Microgrid", "Plan", "Comms", "PIS", "RCA", "Dash", "Rules", "Approve", "Chaos", "Tunnel", "GIS"};
    private static final int ROW = 11;
    private static final int C_TEXT = 0xFFE8ECF0, C_DIM = 0xFF8A94A0, C_HEAD = 0xFF5BCEFA, C_GOOD = 0xFF7CF29A, C_WARN = 0xFFFFC44D, C_BAD = 0xFFFF5252,
            C_ROW = 0x22FFFFFF, C_SEL = 0x553A6FD8;

    private int page, scroll, rowsTotal, rowsVisible, selNet = -1, selAlarm = -1;
    private final List<int[]> hits = new ArrayList<>();
    private final List<String> args = new ArrayList<>();
    private int x, y, w, h, mx, my;
    BlockPos board;
    String hint = "";

    void wheel(int dir) { scroll = Math.max(0, Math.min(Math.max(0, rowsTotal - rowsVisible), scroll + dir)); }

    boolean click(int mx, int my) {
        if (page == 6 && dashPress(mx, my)) return true;
        if (page == 7 && rulePress(mx, my)) return true;
        for (int i = hits.size() - 1; i >= 0; i--) {
            int[] b = hits.get(i);
            if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) { act(b[4], args.get(i)); return true; }
        }
        return false;
    }

    void release(int mx, int my) {
        if (page == 6) dashRelease(mx, my);
        if (page == 7) ruleRelease(mx, my);
    }

    private void act(int a, String arg) {
        if (a >= 100 && a < 200) { page = a - 100; scroll = 0; dragType = null; ruleDrag = null; }
        else if (a == 1) selNet = Integer.parseInt(arg);
        else if (a == 2) selAlarm = Integer.parseInt(arg);
        else if (a == 3) dashAction(arg);
        else if (a == 4) ruleAction(arg);
        else if (a >= 1000) RailMap.NETWORK.sendToServer(new PacketCtlOp(a - 1000, arg));
    }

    private void hit(int bx, int by, int bw, int bh, int action, String arg) { hits.add(new int[]{bx, by, bw, bh, action}); args.add(arg); }

    private static List<String[]> rows(String tag) {
        List<String[]> o = new ArrayList<>();
        for (String[] r : ClientRailway.get().ctl) if (r.length > 0 && r[0].equals(tag)) o.add(r);
        return o;
    }

    private static String c(String[] r, int i) { return i < r.length ? r[i] : ""; }
    private static double d(String s) { try { return Double.parseDouble(s); } catch (RuntimeException e) { return 0; } }

    void draw(FontRenderer fr, int x, int y, int w, int h, int mx, int my) {
        this.x = x; this.y = y; this.w = w; this.h = h; this.mx = mx; this.my = my;
        hits.clear();
        args.clear();
        hint = "";
        int cx = x + 4;
        for (int i = 0; i < PAGES.length; i++) {
            int cw = fr.getStringWidth(PAGES[i]) + 10;
            boolean on = i == page, over = mx >= cx && mx < cx + cw && my >= y + 3 && my < y + 14;
            Gui.drawRect(cx, y + 3, cx + cw, y + 14, on ? 0xFF3A2E5C : over ? 0xFF2A2240 : 0xFF181226);
            Gui.drawRect(cx, y + 13, cx + cw, y + 14, PrideFrame.RAINBOW[i % PrideFrame.RAINBOW.length]);
            fr.drawString(PAGES[i], cx + 5, y + 5, on ? 0xFFFFFFFF : C_DIM);
            hit(cx, y + 3, cw, 11, 100 + i, "");
            cx += cw + 2;
        }
        int top = y + 20;
        rowsVisible = Math.max(1, (y + h - top - 14) / ROW);
        rowsTotal = 0;
        switch (page) {
            case 0 -> pq(fr, top);
            case 1 -> microgrid(fr, top);
            case 2 -> plan(fr, top);
            case 3 -> comms(fr, top);
            case 4 -> pis(fr, top);
            case 5 -> rca(fr, top);
            case 6 -> dash(fr, top);
            case 7 -> rules(fr, top);
            case 8 -> approvals(fr, top);
            case 9 -> chaos(fr, top);
            case 10 -> tunnel(fr, top);
            default -> gis(fr, top);
        }
        if (rowsTotal > rowsVisible) PrideFrame.scrollbar(x + w - 4, top + 12, rowsVisible * ROW, scroll, rowsVisible, rowsTotal);
    }

    // ---- helpers ------------------------------------------------------------------------------------------------------
    private int button(FontRenderer fr, int bx, int by, String label, int action, String arg) {
        int bw = fr.getStringWidth(label) + 10;
        boolean over = mx >= bx && mx < bx + bw && my >= by && my < by + 11;
        Gui.drawRect(bx, by, bx + bw, by + 11, over ? 0xFF5A3A80 : 0xFF3A2440);
        fr.drawString(label, bx + 5, by + 2, 0xFFFFFFFF);
        hit(bx, by, bw, 11, action, arg);
        return bw + 3;
    }

    private void head(FontRenderer fr, int top, String[] cols, int[] at) {
        Gui.drawRect(x + 2, top, x + w - 6, top + 11, 0x33FFFFFF);
        for (int i = 0; i < cols.length; i++) fr.drawString(cols[i], x + 4 + at[i] * (w - 10) / 100, top + 2, C_HEAD);
    }

    private void cell(FontRenderer fr, String s, int[] at, int i, int ry, int col) {
        int cx = x + 4 + at[i] * (w - 10) / 100, nx = i + 1 < at.length ? x + 4 + at[i + 1] * (w - 10) / 100 : x + w - 8;
        fr.drawString(fr.trimStringToWidth(s == null ? "" : s, Math.max(4, nx - cx - 3)), cx, ry, col);
    }

    private <T> List<T> pageOf(List<T> all, int vis) {
        rowsTotal = all.size();
        rowsVisible = vis;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rowsTotal - vis)));
        return all.subList(Math.min(scroll, all.size()), Math.min(all.size(), scroll + vis));
    }

    private void stripe(int ry, int i, boolean sel) {
        if (i % 2 == 0) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, C_ROW);
        if (sel) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, C_SEL);
    }

    private void text(FontRenderer fr, String s, int tx, int ty, int col) { fr.drawString(fr.trimStringToWidth(s, x + w - 8 - tx), tx, ty, col); }

    // ---- PQ: the spectrum ---------------------------------------------------------------------------------------------
    private void pq(FontRenderer fr, int top) {
        List<String[]> q = rows("Q");
        if (q.isEmpty()) { text(fr, "No AC network with a voltage level is live (or harmonicSpectrum is off). Spectrum Meters and Filter Banks work on real grid networks.", x + 6, top + 2, C_DIM); return; }
        if (selNet < 0 || q.stream().noneMatch(r -> c(r, 1).equals(String.valueOf(selNet)))) selNet = Integer.parseInt(c(q.get(0), 1));
        int bx = x + 4;
        for (String[] r : q) {
            String l = "#" + c(r, 1) + " " + c(r, 3) + "%";
            int bw = fr.getStringWidth(l) + 8;
            boolean on = c(r, 1).equals(String.valueOf(selNet));
            Gui.drawRect(bx, top, bx + bw, top + 11, on ? 0xFF3A6FD8 : 0xFF241A36);
            fr.drawString(l, bx + 4, top + 2, d(c(r, 3)) > 5 ? C_BAD : d(c(r, 3)) > 3 ? C_WARN : C_TEXT);
            hit(bx, top, bw, 11, 1, c(r, 1));
            bx += bw + 2;
            if (bx > x + w - 60) break;
        }
        String[] r = null;
        for (String[] t : q) if (c(t, 1).equals(String.valueOf(selNet))) r = t;
        if (r == null) return;
        int gy = top + 16;
        text(fr, "Network #" + c(r, 1) + " " + c(r, 2) + " · THDv " + c(r, 3) + "% · THDi " + c(r, 4) + "% · resonance peak h" + c(r, 7) + " x" + c(r, 8), x + 6, gy, C_HEAD);
        if (!c(r, 5).isEmpty()) text(fr, c(r, 5), x + 6, gy + 10, C_BAD);
        // bar chart h2..h25 with limit lines at 3 % and 5 %
        String[] v = c(r, 6).split(",");
        int chartTop = gy + 24, chartH = Math.max(40, Math.min(110, h / 3)), chartW = w - 50, cx0 = x + 30;
        double fs = 6;
        for (String s : v) fs = Math.max(fs, d(s) * 1.2);
        Gui.drawRect(cx0, chartTop, cx0 + chartW, chartTop + chartH, 0xFF0A0E12);
        for (double lim : new double[]{3, 5}) { int ly = chartTop + chartH - (int) (lim / fs * chartH); if (ly > chartTop) { Gui.drawRect(cx0, ly, cx0 + chartW, ly + 1, lim == 3 ? 0x88FFC44D : 0x88FF5252); fr.drawString(lim + "%", x + 4, ly - 4, C_DIM); } }
        int n = v.length, bw = Math.max(2, chartW / Math.max(1, n) - 2);
        for (int i = 0; i < n; i++) {
            double val = d(v[i]);
            int bh = (int) Math.min(chartH, val / fs * chartH);
            int bx0 = cx0 + 2 + i * (chartW / n);
            int col = val > 5 ? 0xFFFF5252 : val > 3 ? 0xFFFFC44D : 0xFF7CF29A;
            Gui.drawRect(bx0, chartTop + chartH - bh, bx0 + bw, chartTop + chartH, col);
            int hNo = i + 2;
            if (hNo % 2 == 1 || n < 14) fr.drawString(String.valueOf(hNo), bx0 + bw / 2 - fr.getStringWidth(String.valueOf(hNo)) / 2, chartTop + chartH + 2, C_DIM);
            if (mx >= bx0 && mx < bx0 + bw && my >= chartTop && my < chartTop + chartH) hint = String.format(Locale.ROOT, "h%d (%d Hz on 60 Hz): V %.2f%%", hNo, hNo * 60, val);
        }
        int fy = chartTop + chartH + 14;
        List<String[]> f = rows("F");
        head(fr, fy, new String[]{"Filter bank", "Net", "kvar", "Tuned", "Now", "Duty", "C", "State"}, new int[]{0, 22, 30, 40, 50, 60, 70, 80});
        int ry = fy + 13, i = 0;
        for (String[] t : pageOf(f, Math.max(1, (y + h - ry - 4) / ROW))) {
            stripe(ry, i++, false);
            int[] at = {0, 22, 30, 40, 50, 60, 70, 80};
            cell(fr, c(t, 1), at, 0, ry, C_TEXT); cell(fr, "#" + c(t, 2), at, 1, ry, C_TEXT); cell(fr, c(t, 3), at, 2, ry, C_TEXT);
            cell(fr, "h" + c(t, 4), at, 3, ry, C_TEXT); cell(fr, "h" + c(t, 5), at, 4, ry, d(c(t, 5)) > d(c(t, 4)) + 0.1 ? C_WARN : C_TEXT);
            cell(fr, c(t, 6) + "%", at, 5, ry, d(c(t, 6)) > 100 ? C_BAD : C_TEXT); cell(fr, c(t, 8) + "%", at, 6, ry, d(c(t, 8)) < 95 ? C_WARN : C_TEXT);
            cell(fr, c(t, 7).equals("1") ? "IN SERVICE" : "OPEN", at, 7, ry, c(t, 7).equals("1") ? C_GOOD : C_DIM);
            ry += ROW;
        }
        if (f.isEmpty()) text(fr, "No filter banks. Place a Harmonic Filter Bank touching the bus and close it.", x + 6, ry, C_DIM);
    }

    // ---- microgrids ---------------------------------------------------------------------------------------------------
    private void microgrid(FontRenderer fr, int top) {
        List<String[]> m = rows("M");
        if (m.isEmpty()) { text(fr, "No Microgrid Controller loaded. Place one touching the local plant, wrench-link the intertie (POI) and SHED A/B/C breakers.", x + 6, top + 2, C_DIM); return; }
        int ry = top;
        for (String[] r : m) {
            String st = c(r, 3);
            int col = st.equals("TIED") ? C_GOOD : st.equals("BLACKOUT") ? C_BAD : C_WARN;
            Gui.drawRect(x + 2, ry, x + w - 6, ry + 12, 0x33FFFFFF);
            text(fr, c(r, 1) + " @ " + c(r, 2) + "   " + st + "   mode " + c(r, 4) + "   " + c(r, 5) + " Hz   POI " + c(r, 6) + " kW   shed " + c(r, 7) + " stage(s)", x + 4, ry + 2, col);
            int bx = x + w - 230;
            for (String[] b : new String[][]{{"ISLAND", "island"}, {"SYNC", "dosync"}, {"SHED", "shed"}, {"RESTORE", "restore"}, {"RESET", "reset"}}) bx += button(fr, bx, ry + 14, b[0], 1009, c(r, 2) + "|" + b[1]);
            text(fr, c(r, 8), x + 6, ry + 15, C_TEXT);
            ry += 28;
            for (String l : c(r, 9).split("\n")) { if (l.isEmpty()) continue; text(fr, l, x + 12, ry, C_DIM); ry += 10; }
            ry += 4;
            if (ry > y + h - 20) break;
        }
        hint = "Buttons work the controller remotely (through its field RTU if one covers it)";
    }

    // ---- production plan ----------------------------------------------------------------------------------------------
    private void plan(FontRenderer fr, int top) {
        List<String[]> px = rows("PX");
        if (px.isEmpty()) { text(fr, "No Production Planner running. Place a Production Planning Console, give it 12 V and set MODE to ADVISE or AUTO.", x + 6, top + 2, C_DIM); return; }
        String[] p = px.get(0);
        text(fr, c(p, 1), x + 6, top, C_HEAD);
        text(fr, "Bottleneck: " + c(p, 2) + " (" + c(p, 3) + "%) · held " + c(p, 4) + " · departures at risk " + c(p, 5) + " · clearing price $" + c(p, 6) + "/MWh", x + 6, top + 10, d(c(p, 5)) > 0 ? C_WARN : C_TEXT);
        int ty = top + 24;
        int[] at = {0, 22, 32, 44, 56, 86};
        head(fr, ty, new String[]{"Stage", "Plants", "Need /h", "Can /h", "Utilisation", "Stock"}, at);
        int ry = ty + 13, i = 0;
        for (String[] r : rows("PL")) {
            stripe(ry, i++, c(r, 7).equals("1"));
            cell(fr, c(r, 1), at, 0, ry, c(r, 7).equals("1") ? C_BAD : C_TEXT);
            cell(fr, c(r, 2), at, 1, ry, C_TEXT); cell(fr, c(r, 3), at, 2, ry, C_TEXT); cell(fr, c(r, 4), at, 3, ry, C_TEXT);
            double u = d(c(r, 5));
            int ux = x + 4 + at[4] * (w - 10) / 100, uw = (at[5] - at[4]) * (w - 10) / 100 - 30;
            Gui.drawRect(ux, ry, ux + uw, ry + 7, 0xFF20242A);
            Gui.drawRect(ux, ry, ux + (int) (uw * Math.min(1, u / 150)), ry + 7, u > 100 ? 0xFFFF5252 : u > 80 ? 0xFFFFC44D : 0xFF7CF29A);
            fr.drawString(c(r, 5) + "%", ux + uw + 2, ry, C_TEXT);
            cell(fr, c(r, 6), at, 5, ry, C_TEXT);
            ry += ROW;
        }
        ry += 4;
        fr.drawString("FREIGHT DEPARTURES", x + 6, ry, C_HEAD); ry += 10;
        for (String[] r : rows("PF")) { text(fr, c(r, 1), x + 8, ry, c(r, 1).contains("AT RISK") ? C_BAD : c(r, 1).contains("READY") ? C_GOOD : C_TEXT); ry += 10; if (ry > y + h - 60) break; }
        fr.drawString("POWER BIDS", x + 6, ry + 2, C_HEAD); ry += 12;
        for (String[] r : rows("PW")) { text(fr, c(r, 1), x + 8, ry, c(r, 1).contains("LOST") ? C_WARN : C_TEXT); ry += 10; if (ry > y + h - 30) break; }
        for (String[] r : rows("PP")) { if (ry > y + h - 12) break; if (c(r, 5).isEmpty() && !c(r, 4).equals("LOST")) continue; text(fr, c(r, 1) + " @ " + c(r, 6) + " $" + c(r, 3) + " " + c(r, 4) + " " + c(r, 5), x + 8, ry, C_WARN); ry += 10; }
    }

    // ---- comms --------------------------------------------------------------------------------------------------------
    private void comms(FontRenderer fr, int top) {
        List<String[]> rt = rows("C");
        int bx = x + w - 120;
        button(fr, bx, top, "HEAL INJECTIONS", 1008, "");
        if (rt.isEmpty()) { text(fr, "No Field RTUs: every piece of gear is direct-wired (always FRESH). Place a Field RTU to put gear on a comms path.", x + 6, top + 2, C_DIM); return; }
        int[] at = {0, 12, 26, 36, 46, 56, 70, 80};
        head(fr, top + 14, new String[]{"RTU", "At", "Path", "Latency", "Loss", "TX/RX/lost", "CPU", "Data"}, at);
        int ry = top + 27, i = 0;
        for (String[] r : rt) {
            stripe(ry, i++, false);
            cell(fr, c(r, 1), at, 0, ry, C_TEXT); cell(fr, c(r, 2), at, 1, ry, C_DIM);
            String path = c(r, 3).equals("1") ? "A fibre" : c(r, 3).equals("2") ? "B radio" : "NONE";
            cell(fr, path + (c(r, 13).equals("1") ? "" : " A✗") + (c(r, 14).equals("1") ? "" : " B✗"), at, 2, ry, c(r, 3).equals("0") ? C_BAD : c(r, 3).equals("2") ? C_WARN : C_GOOD);
            cell(fr, c(r, 4) + " ms", at, 3, ry, C_TEXT); cell(fr, c(r, 5) + "%", at, 4, ry, C_TEXT);
            cell(fr, c(r, 6) + "/" + c(r, 7) + "/" + c(r, 8), at, 5, ry, C_DIM);
            cell(fr, c(r, 9).equals("1") ? "RUN" : "FAILED", at, 6, ry, c(r, 9).equals("1") ? C_GOOD : C_BAD);
            cell(fr, c(r, 10).equals("1") ? "STALE" : "FRESH", at, 7, ry, c(r, 10).equals("1") ? C_BAD : C_GOOD);
            ry += ROW;
        }
        for (String[] r : rows("CX")) { text(fr, "injected: " + c(r, 1), x + 6, ry, C_WARN); ry += 10; }
        ry += 4;
        fr.drawString("WHICH DATA CAN BE TRUSTED", x + 6, ry, C_HEAD);
        int[] at2 = {0, 40, 55, 65, 75};
        head(fr, ry + 10, new String[]{"Gear", "Trust", "Age", "Via", "Path"}, at2);
        ry += 23;
        i = 0;
        for (String[] r : pageOf(rows("G"), Math.max(1, (y + h - ry - 4) / ROW))) {
            stripe(ry, i++, false);
            cell(fr, c(r, 1), at2, 0, ry, C_TEXT);
            cell(fr, c(r, 2), at2, 1, ry, c(r, 2).equals("STALE") ? C_BAD : c(r, 2).equals("DELAYED") ? C_WARN : C_GOOD);
            cell(fr, c(r, 3) + " s", at2, 2, ry, C_TEXT); cell(fr, c(r, 4), at2, 3, ry, C_DIM); cell(fr, c(r, 5), at2, 4, ry, C_DIM);
            ry += ROW;
        }
        hint = "STALE values on remote panels are the last good reading - the gear and interlocking keep working locally";
    }

    // ---- PIS ----------------------------------------------------------------------------------------------------------
    private int pisTpl;
    private void pis(FontRenderer fr, int top) {
        int bx = x + 4;
        bx += button(fr, bx, top, "TEMPLATE " + (pisTpl + 1), 3, "pistpl");
        bx += button(fr, bx, top, "PUBLISH TO ALL", 1002, "*|" + pisTpl);
        button(fr, bx, top, "CLEAR OPERATOR MSGS", 1003, "*");
        text(fr, com.dogpound.railmap.station.Pis.INFO_TEMPLATES[pisTpl], x + 6, top + 13, C_DIM);
        int[] at = {0, 6, 22, 32, 38, 82, 92};
        head(fr, top + 25, new String[]{"#", "Station", "Kind", "P", "Message", "Source", "Age"}, at);
        int ry = top + 38, i = 0;
        List<String[]> s = rows("S");
        s.sort((a, b) -> Integer.compare(Integer.parseInt(c(b, 4)), Integer.parseInt(c(a, 4))));
        for (String[] r : pageOf(s, Math.max(1, (y + h - ry - 4) / ROW))) {
            stripe(ry, i++, false);
            int p = Integer.parseInt(c(r, 4));
            int col = p >= 8 ? C_BAD : p >= 5 ? C_WARN : C_TEXT;
            cell(fr, c(r, 1), at, 0, ry, C_DIM); cell(fr, c(r, 2), at, 1, ry, C_TEXT); cell(fr, c(r, 3), at, 2, ry, col);
            cell(fr, c(r, 4), at, 3, ry, col); cell(fr, c(r, 5), at, 4, ry, col); cell(fr, c(r, 6), at, 5, ry, C_DIM); cell(fr, c(r, 7) + "s", at, 6, ry, C_DIM);
            if (mx >= x && my >= ry - 1 && my < ry + ROW - 1) hint = c(r, 5);
            ry += ROW;
        }
        if (s.isEmpty()) text(fr, "The feed is empty (or no PIS server is online - every sign shows the fallback message).", x + 6, ry, C_DIM);
    }

    // ---- RCA ----------------------------------------------------------------------------------------------------------
    private void rca(FontRenderer fr, int top) {
        List<String[]> r = rows("R");
        if (r.isEmpty()) { text(fr, "No alarms in the last half hour.", x + 6, top + 2, C_DIM); return; }
        int split = selAlarm >= 0 ? y + h - 80 : y + h - 4;
        int ry = top, i = 0;
        for (String[] t : pageOf(r, Math.max(1, (split - top) / ROW))) {
            int depth = Integer.parseInt(c(t, 1));
            boolean sel = c(t, 2).equals(String.valueOf(selAlarm));
            stripe(ry, i++, sel);
            String role = c(t, 3);
            int col = role.equals("ORIGINAL") ? C_BAD : role.equals("SYMPTOM") ? C_WARN : C_DIM;
            int ix = x + 6 + depth * 12;
            if (depth > 0) fr.drawString("└", ix - 9, ry, C_DIM);
            text(fr, (role.equals("ORIGINAL") ? "◆ ORIGINAL " : role.equals("SYMPTOM") ? "→ " : "· ") + "#" + c(t, 2) + " " + c(t, 4) + " " + c(t, 7) + "  " + c(t, 5) + (c(t, 6).isEmpty() ? "" : "  [" + c(t, 6) + "%]"), ix, ry, col);
            hit(x + 2, ry - 1, w - 8, ROW, 2, c(t, 2));
            ry += ROW;
        }
        if (selAlarm >= 0) {
            Gui.drawRect(x + 2, split, x + w - 6, y + h - 2, 0xFF101622);
            int ey = split + 3;
            fr.drawString("EVIDENCE for #" + selAlarm, x + 6, ey, C_HEAD);
            ey += 11;
            boolean any = false;
            for (String[] e : rows("E")) {
                if (!c(e, 1).equals(String.valueOf(selAlarm))) continue;
                any = true;
                text(fr, "explained by #" + c(e, 2) + " with " + c(e, 3) + "% confidence:", x + 8, ey, C_TEXT);
                ey += 10;
                for (String l : c(e, 4).split("\n")) { if (ey > y + h - 10) break; text(fr, "  " + l, x + 8, ey, C_DIM); ey += 9; }
            }
            if (!any) text(fr, "Nothing explains it: it is an original fault (or unrelated). Click its symptoms to see why they follow from it.", x + 8, ey, C_DIM);
        }
        hint = "◆ = original fault · → = symptom (indented under its cause) · click a row for the evidence · /irextras ctl rca <id>";
    }

    // ---- approvals ----------------------------------------------------------------------------------------------------
    private void approvals(FontRenderer fr, int top) {
        int[] at = {0, 5, 16, 30, 42, 54, 62, 70};
        head(fr, top, new String[]{"#", "Action", "Where", "By", "State", "Appr.", "Left", ""}, at);
        int ry = top + 13, i = 0;
        for (String[] r : rows("A")) {
            stripe(ry, i++, false);
            String st = c(r, 5);
            int col = st.equals("APPROVED") ? C_GOOD : st.equals("REQUESTED") ? C_WARN : C_DIM;
            cell(fr, c(r, 1), at, 0, ry, C_DIM); cell(fr, c(r, 2), at, 1, ry, C_TEXT); cell(fr, c(r, 3), at, 2, ry, C_DIM);
            cell(fr, c(r, 4), at, 3, ry, C_TEXT); cell(fr, st, at, 4, ry, col); cell(fr, c(r, 6) + (c(r, 7).equals("1") ? " DUAL" : ""), at, 5, ry, C_TEXT);
            cell(fr, c(r, 8).isEmpty() ? "" : c(r, 8) + "s", at, 6, ry, C_TEXT);
            if (st.equals("REQUESTED") || st.equals("APPROVED")) {
                int bx = x + 4 + at[7] * (w - 10) / 100;
                if (st.equals("REQUESTED")) bx += button(fr, bx, ry - 1, "APPROVE", 1004, c(r, 1));
                button(fr, bx, ry - 1, "REJECT", 1005, c(r, 1));
            }
            if (mx >= x && my >= ry - 1 && my < ry + ROW - 1) hint = c(r, 10) + (c(r, 9).isEmpty() ? "" : " · approved by " + c(r, 9));
            ry += ROW + 1;
            if (ry > y + h / 2) break;
        }
        ry += 6;
        fr.drawString("AUDIT TRAIL", x + 6, ry, C_HEAD);
        ry += 11;
        for (String[] r : rows("AT")) { if (ry > y + h - 12) break; text(fr, c(r, 1), x + 8, ry, C_DIM); ry += 10; }
        for (String[] r : rows("AQ")) { if (ry > y + h - 12) break; text(fr, "under approval control: " + c(r, 1) + (c(r, 2).equals("1") ? " (dual)" : ""), x + 8, ry, C_WARN); ry += 10; }
        if (hint.isEmpty()) hint = "You can't approve your own request · DUAL needs two different people (or the two keys on an Approval Panel)";
    }

    // ---- chaos --------------------------------------------------------------------------------------------------------
    private void chaos(FontRenderer fr, int top) {
        String run = rows("XR").isEmpty() ? "" : c(rows("XR").get(0), 1);
        text(fr, run.isEmpty() ? "No scenario running. Start one at a Fault Lab console in TEST MODE (SCENARIO selector + CHAOS), or /irextras ctl chaos run <n>." : "RUNNING: " + run, x + 6, top, run.isEmpty() ? C_DIM : C_WARN);
        if (!run.isEmpty()) button(fr, x + w - 70, top - 1, "ABORT", 1006, "");
        int ry = top + 14;
        for (int i = 0; i < com.dogpound.railmap.safety.Chaos.NAMES.length; i++) { text(fr, i + "  " + com.dogpound.railmap.safety.Chaos.NAMES[i], x + 8, ry, C_DIM); ry += 9; }
        ry += 4;
        fr.drawString("LAST REPORT", x + 6, ry, C_HEAD);
        ry += 11;
        for (String[] r : pageOf(rows("X"), Math.max(1, (y + h - ry - 4) / 10))) {
            String l = c(r, 1);
            text(fr, l, x + 8, ry, l.startsWith("FAIL") || l.startsWith("ABORT") ? C_BAD : l.startsWith("PASS") ? C_GOOD : l.startsWith("RESULT") ? C_HEAD : C_TEXT);
            ry += 10;
        }
    }

    // ---- tunnel -------------------------------------------------------------------------------------------------------
    private void tunnel(FontRenderer fr, int top) {
        List<String[]> t = rows("T");
        if (t.isEmpty()) { text(fr, "No tunnel control panel with sectioned air (tunnelAirSections) is loaded.", x + 6, top + 2, C_DIM); return; }
        int ry = top;
        for (String[] r : t) {
            String[] cells = c(r, 2).split(";");
            int n = cells.length;
            boolean bl = c(r, 5).equals("1");
            text(fr, String.format(Locale.ROOT, "%s  air %s m/s  critical %s m/s%s  emergency bus %s kWh  %d x %s m sections%s", c(r, 1), c(r, 3), c(r, 4), bl ? "  BACK-LAYERING" : "", c(r, 7), n, c(r, 9), c(r, 8).equals("1") ? "  EMERGENCY" : ""), x + 6, ry, bl || c(r, 8).equals("1") ? C_BAD : C_HEAD);
            ry += 12;
            int bw = (w - 20) / Math.max(1, n);
            int inc = Integer.parseInt(c(r, 6));
            for (int i = 0; i < n; i++) {
                String[] v = cells[i].split(",");
                double smoke = d(v[0]), co = d(v.length > 1 ? v[1] : "0"), temp = d(v.length > 2 ? v[2] : "15");
                int bx0 = x + 8 + i * bw;
                int sh = (int) Math.min(30, smoke * 3);
                Gui.drawRect(bx0, ry, bx0 + bw - 2, ry + 30, 0xFF0A0E12);
                Gui.drawRect(bx0, ry + 30 - sh, bx0 + bw - 2, ry + 30, 0xCC888888);
                int tc = temp > 200 ? 0xFFFF3020 : temp > 60 ? 0xFFFFA020 : temp > 30 ? 0xFFFFE060 : 0xFF3060A0;
                Gui.drawRect(bx0, ry + 31, bx0 + bw - 2, ry + 34, tc);
                if (i == inc) fr.drawString("FIRE", bx0 + 1, ry + 1, C_BAD);
                if (mx >= bx0 && mx < bx0 + bw && my >= ry && my < ry + 34) hint = String.format(Locale.ROOT, "section %d: smoke %.1f %%/m (visibility %d m), CO %.0f ppm, %.0f °C", i + 1, smoke, com.dogpound.railmap.track.TunnelAir.visibility(smoke), co, temp);
            }
            double u = d(c(r, 3));
            String arrow = u > 0.2 ? "air  ───────────►" : u < -0.2 ? "◄───────────  air" : "still air";
            fr.drawString(arrow, x + 8 + (w - 20) / 2 - fr.getStringWidth(arrow) / 2, ry + 37, bl ? C_BAD : C_TEXT);
            ry += 52;
            if (ry > y + h - 50) break;
        }
        if (hint.isEmpty()) hint = "grey = smoke in each section, colour strip = temperature; below the critical velocity smoke back-layers upstream";
    }

    // ---- GIS ----------------------------------------------------------------------------------------------------------
    private void gis(FontRenderer fr, int top) {
        int[] at = {0, 30, 40, 88};
        head(fr, top, new String[]{"Route", "Length", "Verdict", ""}, at);
        int ry = top + 13, i = 0;
        for (String[] r : rows("GI")) {
            stripe(ry, i++, false);
            cell(fr, c(r, 1), at, 0, ry, C_TEXT); cell(fr, c(r, 3) + " m", at, 1, ry, C_DIM);
            String v = c(r, 2);
            cell(fr, v, at, 2, ry, v.startsWith("REJECTED") ? C_BAD : v.startsWith("ACCEPTABLE") ? C_WARN : v.startsWith("APPROVED") ? C_GOOD : C_DIM);
            button(fr, x + 4 + at[3] * (w - 10) / 100, ry - 1, "VALIDATE", 1007, c(r, 1));
            ry += ROW + 1;
        }
        if (i == 0) text(fr, "No surveyed routes yet. Survey one with the Surveyor's Level (waypoints, then set it out).", x + 6, ry, C_DIM);
        ry += 8;
        fr.drawString("BOUNDARIES (/irextras ctl gis boundary add name PROPERTY|PROTECTED|RESERVED|FLOOD x1 z1 x2 z2)", x + 6, ry, C_HEAD);
        ry += 11;
        for (String[] r : rows("GB")) { text(fr, c(r, 2) + "  " + c(r, 1) + "  " + c(r, 3), x + 8, ry, c(r, 2).equals("PROTECTED") ? C_BAD : C_TEXT); ry += 10; }
        hint = "VALIDATE checks gradients, curves, earthworks, drainage, utilities, existing railway and boundaries - the full report goes to chat";
    }

    // ================================================================================================================
    // #39 the dashboard designer
    // ================================================================================================================
    static final String[] WIDGETS = {"TRAINS", "ALARMS", "POWER", "FACTORY", "FREIGHT", "MAINT", "FINANCE", "GRID", "COMMS", "PQ", "PIS", "CHAOS"};
    private List<int[]> layout;          // {widget index, x, y, w, h}
    private String layoutFrom = "";
    private boolean editing, dirty;
    private String dragType;              // "new:i" / "move:k" / "size:k"
    private int dragDX, dragDY;
    private int gx, gy, cw, chh;          // grid origin + cell size

    private String myName() { return Minecraft.getMinecraft().player == null ? "" : Minecraft.getMinecraft().player.getName(); }
    private String myRole() { for (String[] r : rows("DR")) if (c(r, 1).equals(myName())) return c(r, 2); return "ALL"; }

    private void loadLayout() {
        String src = null, from = "default";
        for (String[] r : rows("D")) if (c(r, 1).equals("user:" + myName())) { src = c(r, 2); from = "yours"; }
        if (src == null) for (String[] r : rows("D")) if (c(r, 1).equals("role:" + myRole())) { src = c(r, 2); from = "role " + myRole(); }
        if (src == null) src = com.dogpound.railmap.ctl.CtlData.DEFAULT;
        layout = parse(src);
        layoutFrom = from;
        dirty = false;
    }

    private static List<int[]> parse(String s) {
        List<int[]> l = new ArrayList<>();
        if (s == null || s.isEmpty()) return l;
        for (String p : s.split(";")) {
            String[] q = p.split(",");
            if (q.length != 5) continue;
            int wi = Arrays.asList(WIDGETS).indexOf(q[0]);
            if (wi < 0) continue;
            try { l.add(new int[]{wi, Integer.parseInt(q[1]), Integer.parseInt(q[2]), Integer.parseInt(q[3]), Integer.parseInt(q[4])}); } catch (NumberFormatException ignored) { }
        }
        return l;
    }

    private String serialise() {
        StringBuilder b = new StringBuilder();
        for (int[] e : layout) b.append(b.length() == 0 ? "" : ";").append(WIDGETS[e[0]]).append(',').append(e[1]).append(',').append(e[2]).append(',').append(e[3]).append(',').append(e[4]);
        return b.toString();
    }

    private boolean free(int skip, int cx, int cy, int ww, int hh) {
        if (cx < 0 || cy < 0 || ww < 1 || hh < 1 || cx + ww > 12 || cy + hh > 8) return false;
        for (int k = 0; k < layout.size(); k++) {
            if (k == skip) continue;
            int[] e = layout.get(k);
            if (cx < e[1] + e[3] && cx + ww > e[1] && cy < e[2] + e[4] && cy + hh > e[2]) return false;
        }
        return true;
    }

    private void dash(FontRenderer fr, int top) {
        if (layout == null || !dirty && !editing) loadLayout();
        int pal = editing ? 62 : 0;
        int bx = x + 4;
        bx += button(fr, bx, top, editing ? "DONE" : "EDIT LAYOUT", 3, "edit");
        if (editing) {
            bx += button(fr, bx, top, "SAVE MINE", 3, "saveuser");
            bx += button(fr, bx, top, "SAVE FOR ROLE " + myRole(), 3, "saverole");
            bx += button(fr, bx, top, "RESET MINE", 3, "reset");
        }
        text(fr, "layout: " + layoutFrom + (dirty ? " (unsaved)" : ""), bx + 4, top + 2, dirty ? C_WARN : C_DIM);
        gx = x + 4 + pal; gy = top + 14;
        cw = (w - 10 - pal) / 12; chh = Math.max(14, (y + h - gy - 4) / 8);
        if (editing) {
            for (int i = 0; i < WIDGETS.length; i++) {
                int py = gy + i * 13;
                boolean over = mx >= x + 4 && mx < x + 4 + pal - 4 && my >= py && my < py + 12;
                Gui.drawRect(x + 4, py, x + pal, py + 12, over ? 0xFF5A3A80 : 0xFF2A2240);
                Gui.drawRect(x + 4, py, x + 6, py + 12, PrideFrame.RAINBOW[i % PrideFrame.RAINBOW.length]);
                fr.drawString(WIDGETS[i], x + 9, py + 2, C_TEXT);
            }
            for (int i = 0; i <= 12; i++) Gui.drawRect(gx + i * cw, gy, gx + i * cw + 1, gy + 8 * chh, 0x22FFFFFF);
            for (int j = 0; j <= 8; j++) Gui.drawRect(gx, gy + j * chh, gx + 12 * cw, gy + j * chh + 1, 0x22FFFFFF);
        }
        String[] k = rows("K").isEmpty() ? new String[30] : rows("K").get(0);
        for (int n = 0; n < layout.size(); n++) {
            int[] e = layout.get(n);
            boolean moving = dragType != null && dragType.equals("move:" + n);
            int wx = gx + e[1] * cw, wy = gy + e[2] * chh, ww = e[3] * cw - 2, wh = e[4] * chh - 2;
            if (moving) { wx = mx - dragDX; wy = my - dragDY; }
            if (dragType != null && dragType.equals("size:" + n)) { ww = Math.max(cw - 2, mx - wx); wh = Math.max(chh - 2, my - wy); }
            widget(fr, e[0], wx, wy, ww, wh, k);
            if (editing) {
                Gui.drawRect(wx + ww - 9, wy + 1, wx + ww - 1, wy + 9, 0xFF8A3030);
                fr.drawString("x", wx + ww - 7, wy, 0xFFFFFFFF);
                Gui.drawRect(wx + ww - 5, wy + wh - 5, wx + ww, wy + wh, 0xFFFFC44D);
            }
        }
        if (dragType != null && dragType.startsWith("new:")) {
            int wi = Integer.parseInt(dragType.substring(4));
            Gui.drawRect(mx - 20, my - 8, mx + 40, my + 12, 0xAA3A6FD8);
            fr.drawString(WIDGETS[wi], mx - 16, my - 4, 0xFFFFFFFF);
        }
        if (editing) hint = "drag a widget from the palette onto the grid · drag a widget to move it · drag its yellow corner to resize · x removes · SAVE MINE / SAVE FOR ROLE";
        else hint = "your dashboard (" + layoutFrom + ") · EDIT LAYOUT to change it";
    }

    private void widget(FontRenderer fr, int type, int wx, int wy, int ww, int wh, String[] k) {
        Gui.drawRect(wx, wy, wx + ww, wy + wh, 0xFF141B26);
        Gui.drawRect(wx, wy, wx + ww, wy + 10, 0xFF22304A);
        Gui.drawRect(wx, wy + 10, wx + ww, wy + 11, PrideFrame.RAINBOW[type % PrideFrame.RAINBOW.length]);
        fr.drawString(fr.trimStringToWidth(WIDGETS[type], ww - 12), wx + 3, wy + 1, C_HEAD);
        List<String> lines = new ArrayList<>();
        List<Integer> cols = new ArrayList<>();
        String big = "";
        int bigCol = C_TEXT;
        switch (type) {
            case 0 -> { big = s(k, 2) + " / " + s(k, 1); lines.add("moving / trains"); cols.add(C_DIM); }
            case 1 -> {
                big = s(k, 9) + " new"; bigCol = d(s(k, 9)) > 0 ? C_BAD : C_GOOD;
                lines.add(s(k, 10) + " standing · " + s(k, 11) + " critical"); cols.add(d(s(k, 11)) > 0 ? C_BAD : C_TEXT);
                for (String[] a : ClientRailway.get().alarms) { if (lines.size() > 6) break; if (!"0".equals(c(a, 1))) { lines.add(c(a, 2) + " " + c(a, 4)); cols.add("CRITICAL".equals(c(a, 2)) ? C_BAD : C_WARN); } }
            }
            case 2 -> { big = s(k, 3) + " kW"; lines.add("traction now"); cols.add(C_DIM); lines.add("grid " + s(k, 4) + " kW · " + s(k, 19) + "/" + s(k, 18) + " nets dead"); cols.add(d(s(k, 19)) > 0 ? C_WARN : C_TEXT); }
            case 3 -> { big = s(k, 8) + "/min"; lines.add(s(k, 7) + " of " + s(k, 6) + " plants running"); cols.add(C_TEXT); lines.add(s(k, 5) + " kW industrial"); cols.add(C_DIM); }
            case 4 -> {
                big = s(k, 12) + " due"; bigCol = d(s(k, 13)) > 0 ? C_BAD : C_TEXT;
                lines.add(s(k, 13) + " late · " + s(k, 14) + " open"); cols.add(d(s(k, 13)) > 0 ? C_BAD : C_TEXT);
                for (String[] ct : ClientRailway.get().contracts) { if (lines.size() > 6) break; if ("ACCEPTED".equals(c(ct, 1))) { lines.add(c(ct, 2) + " x" + c(ct, 3) + " " + c(ct, 9)); cols.add(C_DIM); } }
            }
            case 5 -> { big = s(k, 15); bigCol = d(s(k, 15)) > 0 ? C_WARN : C_GOOD; lines.add("items due for maintenance"); cols.add(C_DIM); }
            case 6 -> { big = "$" + money(s(k, 16)); lines.add("this month " + (d(s(k, 17)) >= 0 ? "+" : "") + "$" + money(s(k, 17))); cols.add(d(s(k, 17)) >= 0 ? C_GOOD : C_BAD); }
            case 7 -> { big = s(k, 18) + " nets"; lines.add(s(k, 19) + " dead · worst THD " + s(k, 20) + "%"); cols.add(d(s(k, 20)) > 5 ? C_WARN : C_TEXT); }
            case 8 -> { big = s(k, 21) + " stale"; bigCol = d(s(k, 21)) > 0 ? C_BAD : C_GOOD; for (String[] r : rows("C")) { if (lines.size() > 5) break; lines.add(c(r, 1) + " " + (c(r, 10).equals("1") ? "STALE" : "fresh")); cols.add(c(r, 10).equals("1") ? C_BAD : C_TEXT); } }
            case 9 -> { big = s(k, 20) + "% THD"; bigCol = d(s(k, 20)) > 5 ? C_BAD : C_GOOD; for (String[] r : rows("Q")) { if (lines.size() > 5) break; if (!c(r, 5).isEmpty()) { lines.add("#" + c(r, 1) + " resonance"); cols.add(C_BAD); } } }
            case 10 -> { big = rows("S").size() + " msgs"; for (String[] r : rows("S")) { if (lines.size() > 5) break; lines.add(c(r, 2) + ": " + c(r, 5)); cols.add(d(c(r, 4)) >= 8 ? C_BAD : C_TEXT); } }
            default -> { String run = rows("XR").isEmpty() ? "" : c(rows("XR").get(0), 1); big = run.isEmpty() ? "idle" : "RUN"; bigCol = run.isEmpty() ? C_DIM : C_WARN; for (String[] r : rows("X")) if (c(r, 1).startsWith("RESULT")) { lines.add(c(r, 1)); cols.add(C_TEXT); } }
        }
        int ty = wy + 14;
        if (wh > 30) {
            float sc = Math.min(2f, Math.max(1f, (wh - 20) / 28f));
            net.minecraft.client.renderer.GlStateManager.pushMatrix();
            net.minecraft.client.renderer.GlStateManager.translate(wx + 4, ty, 0);
            net.minecraft.client.renderer.GlStateManager.scale(sc, sc, 1);
            fr.drawString(fr.trimStringToWidth(big, (int) ((ww - 8) / sc)), 0, 0, bigCol);
            net.minecraft.client.renderer.GlStateManager.popMatrix();
            ty += (int) (10 * sc) + 2;
        } else { fr.drawString(fr.trimStringToWidth(big, ww - 6), wx + 4, ty, bigCol); ty += 10; }
        for (int i = 0; i < lines.size() && ty + 9 < wy + wh; i++) { fr.drawString(fr.trimStringToWidth(lines.get(i), ww - 6), wx + 4, ty, cols.get(i)); ty += 9; }
    }

    private static String s(String[] k, int i) { return k == null || i >= k.length || k[i] == null ? "0" : k[i]; }
    private static String money(String cents) { long c = (long) d(cents); return String.format(Locale.ROOT, "%,d", c / 100); }

    private void dashAction(String a) {
        switch (a) {
            case "edit": editing = !editing; if (!editing && !dirty) loadLayout(); break;
            case "saveuser": RailMap.NETWORK.sendToServer(new PacketCtlOp(1, "user|" + serialise())); dirty = false; layoutFrom = "yours"; break;
            case "saverole": RailMap.NETWORK.sendToServer(new PacketCtlOp(1, "role|" + serialise())); dirty = false; break;
            case "reset": RailMap.NETWORK.sendToServer(new PacketCtlOp(1, "user|")); dirty = false; layout = null; break;
            case "pistpl": pisTpl = (pisTpl + 1) % com.dogpound.railmap.station.Pis.INFO_TEMPLATES.length; break;
            default: break;
        }
    }

    private boolean dashPress(int mx, int my) {
        if (!editing || layout == null) return false;
        int pal = 62;
        for (int i = 0; i < WIDGETS.length; i++) { int py = gy + i * 13; if (mx >= x + 4 && mx < x + pal && my >= py && my < py + 12) { dragType = "new:" + i; return true; } }
        for (int n = layout.size() - 1; n >= 0; n--) {
            int[] e = layout.get(n);
            int wx = gx + e[1] * cw, wy = gy + e[2] * chh, ww = e[3] * cw - 2, wh = e[4] * chh - 2;
            if (mx < wx || mx >= wx + ww || my < wy || my >= wy + wh) continue;
            if (mx >= wx + ww - 9 && my < wy + 9) { layout.remove(n); dirty = true; return true; }
            if (mx >= wx + ww - 6 && my >= wy + wh - 6) { dragType = "size:" + n; return true; }
            dragType = "move:" + n; dragDX = mx - wx; dragDY = my - wy;
            return true;
        }
        return false;
    }

    private void dashRelease(int mx, int my) {
        if (dragType == null || layout == null) return;
        String t = dragType;
        dragType = null;
        if (t.startsWith("new:")) {
            int wi = Integer.parseInt(t.substring(4));
            int cx = (mx - gx) / Math.max(1, cw), cy = (my - gy) / Math.max(1, chh);
            for (int[] sz : new int[][]{{3, 2}, {2, 2}, {2, 1}, {1, 1}}) if (free(-1, cx, cy, sz[0], sz[1])) { layout.add(new int[]{wi, cx, cy, sz[0], sz[1]}); dirty = true; return; }
            hint = "no room there";
        } else if (t.startsWith("move:")) {
            int n = Integer.parseInt(t.substring(5));
            if (n >= layout.size()) return;
            int[] e = layout.get(n);
            int cx = Math.round((float) (mx - dragDX - gx) / Math.max(1, cw)), cy = Math.round((float) (my - dragDY - gy) / Math.max(1, chh));
            if (free(n, cx, cy, e[3], e[4])) { e[1] = cx; e[2] = cy; dirty = true; }
        } else if (t.startsWith("size:")) {
            int n = Integer.parseInt(t.substring(5));
            if (n >= layout.size()) return;
            int[] e = layout.get(n);
            int nw = Math.max(1, Math.round((float) (mx - (gx + e[1] * cw)) / Math.max(1, cw))), nh = Math.max(1, Math.round((float) (my - (gy + e[2] * chh)) / Math.max(1, chh)));
            if (free(n, e[1], e[2], nw, nh)) { e[3] = nw; e[4] = nh; dirty = true; }
        }
    }

    // ================================================================================================================
    // #41 the visual automation designer: WHEN -> IF -> DO / ELSE / ON ERROR blocks over the signal-box program
    // ================================================================================================================
    private static final String[] EVENT_PRESETS = {"alarm", "alarm:power", "alarm:fire", "arrive:*", "channel:1:on", "logic:*", "chaos:*", "my:event"};
    private static final String[] EXPR_PRESETS = {"trains >= 3", "ch1 && !ch2", "trains == 0", "x > 5"};
    private static final String[] TIMER_PRESETS = {"t1", "t2", "t3"};
    private static final String[] ACT_TEXT = {"x = x + 1", "t1 30", "t1", "routine1", "my:event", "Rule failed: {error}", "start"};
    private long lastReq;
    private String ruleDrag;                 // "c:<Cond>" or "a:<Act>"
    private final List<int[]> slots = new ArrayList<>();      // x, y, w, h, ruleId (0 = new), slot (0 WHEN, 1 IF, 2 DO, 3 ELSE, 4 ERR)
    private int palTop;

    private BlockPos box() { return board != null ? board : Minecraft.getMinecraft().player == null ? BlockPos.ORIGIN : Minecraft.getMinecraft().player.getPosition(); }

    private void rules(FontRenderer fr, int top) {
        if (System.currentTimeMillis() - lastReq > 1000) { lastReq = System.currentTimeMillis(); RailMap.NETWORK.sendToServer(PacketProgram.request(box())); }
        ProgramData d = PacketProgram.client;
        slots.clear();
        int pal = 96;
        palTop = top;
        // palette: WHEN / IF blocks and DO blocks
        int py = top;
        fr.drawString("WHEN / IF", x + 4, py, C_HEAD); py += 10;
        for (Rule.Cond c : Rule.Cond.values()) { chip(fr, x + 4, py, pal - 6, c.label, 0xFF24408E, "c:" + c.name()); py += 10; }
        py += 4;
        fr.drawString("DO / ELSE / ERROR", x + 4, py, C_HEAD); py += 10;
        for (Rule.Act a : Rule.Act.values()) { if (py > y + h - 10) break; chip(fr, x + 4, py, pal - 6, a.label, 0xFF008026, "a:" + a.name()); py += 10; }
        // canvas
        int cx0 = x + pal + 4, cwid = w - pal - 12;
        int col = (cwid - 120) / 4;
        int ry = top;
        fr.drawString("PRIO", cx0, ry, C_DIM); fr.drawString("WHEN", cx0 + 40, ry, C_HEAD); fr.drawString("IF", cx0 + 40 + col, ry, C_HEAD); fr.drawString("DO  /  ELSE", cx0 + 40 + 2 * col, ry, C_HEAD); fr.drawString("ON ERROR", cx0 + 40 + 3 * col, ry, C_HEAD); fr.drawString("HISTORY", cx0 + 40 + 4 * col, ry, C_DIM);
        ry += 11;
        List<Rule> rs = new ArrayList<>(d.rules);
        rs.sort((a, b) -> Integer.compare(b.priority, a.priority));
        int rowH = 24;
        List<Rule> vis = pageOf(rs, Math.max(1, (y + h - ry - 30) / rowH));
        for (Rule r : vis) {
            boolean over = my >= ry && my < ry + rowH - 2 && mx >= cx0;
            Gui.drawRect(cx0 - 2, ry, x + w - 6, ry + rowH - 2, over ? 0x22FFFFFF : 0x11FFFFFF);
            // priority + toggles
            fr.drawString("P" + r.priority, cx0, ry + 2, C_TEXT);
            miniButton(fr, cx0 + 18, ry + 1, "▲", "up:" + r.id); miniButton(fr, cx0 + 18, ry + 11, "▼", "dn:" + r.id);
            miniButton(fr, cx0, ry + 11, r.enabled ? "ON" : "--", "tg:" + r.id);
            block(fr, cx0 + 40, ry, col - 8, r.cond.label + (r.cond.needsText() ? " " + r.condText : r.cond.needsChannel() ? " " + r.condChannel : ""), 0xFF24408E, r.id, 0, r.enabled);
            arrow(fr, cx0 + 40 + col - 8, ry + 10);
            block(fr, cx0 + 40 + col, ry, col - 8, r.cond2 == null ? "(always)" : r.cond2.label + (r.cond2.needsText() ? " " + r.cond2Text : ""), r.cond2 == null ? 0xFF2A2A36 : 0xFF24408E, r.id, 1, r.enabled);
            arrow(fr, cx0 + 40 + 2 * col - 8, ry + 10);
            block(fr, cx0 + 40 + 2 * col, ry, col - 8, r.act.label + (r.act.needsText() ? " " + r.text : r.act.needsChannel() ? " " + r.actChannel : ""), 0xFF008026, r.id, 2, r.enabled);
            blockHalf(fr, cx0 + 40 + 2 * col, ry + 11, col - 8, "ELSE " + r.elseAct.label, r.elseAct == Rule.Act.NOTHING ? 0xFF2A2A36 : 0xFF806000, r.id, 3);
            block(fr, cx0 + 40 + 3 * col, ry, col - 8, r.errAct == Rule.Act.NOTHING ? "(disable after errors)" : r.errAct.label + " " + r.errText, r.errAct == Rule.Act.NOTHING ? 0xFF2A2A36 : 0xFF8A3030, r.id, 4, r.enabled);
            // history: last 8 firings
            int hx = cx0 + 40 + 4 * col;
            for (int i = 0; i < 8; i++) {
                int colr = 0xFF20242A;
                String tip = "";
                if (i < r.history.size()) { String hs = r.history.get(i); colr = hs.contains("|ERROR") ? 0xFFFF5252 : hs.contains("|ELSE") ? 0xFFFFC44D : 0xFF7CF29A; tip = hs; }
                Gui.drawRect(hx + i * 6, ry + 2, hx + i * 6 + 5, ry + 9, colr);
                if (!tip.isEmpty() && mx >= hx + i * 6 && mx < hx + i * 6 + 5 && my >= ry + 2 && my < ry + 9) hint = tip;
            }
            fr.drawString(r.fired + "x " + (r.errors > 0 ? "§c" + r.errors + "err" : ""), hx, ry + 12, C_DIM);
            miniButton(fr, x + w - 22, ry + 1, "DEL", "del:" + r.id);
            if (over && hint.isEmpty()) hint = "#" + r.id + " " + r.summary() + " · click a block's text to cycle its preset · drop palette blocks on WHEN / IF / DO / ELSE / ON ERROR";
            ry += rowH;
        }
        // the new-rule drop row
        Gui.drawRect(cx0 - 2, ry, x + w - 6, ry + 18, 0x223A6FD8);
        fr.drawString("+ NEW RULE: drop a WHEN block here", cx0 + 4, ry + 5, C_DIM);
        slots.add(new int[]{cx0 - 2, ry, w - pal - 8, 18, 0, 0});
        if (!d.halted.isEmpty()) fr.drawString("PROGRAM HALTED by the watchdog: " + d.halted, cx0, ry + 22, C_BAD);
        if (ruleDrag != null) {
            String lbl = ruleDrag.startsWith("c:") ? Rule.Cond.valueOf(ruleDrag.substring(2)).label : Rule.Act.valueOf(ruleDrag.substring(2)).label;
            Gui.drawRect(mx - 4, my - 6, mx + fr.getStringWidth(lbl) + 6, my + 6, ruleDrag.startsWith("c:") ? 0xCC24408E : 0xCC008026);
            fr.drawString(lbl, mx, my - 4, 0xFFFFFFFF);
        }
        if (hint.isEmpty()) hint = "the signal-box program as blocks: drag WHEN/IF conditions and DO/ELSE/ON ERROR actions onto a rule; ▲▼ priority; map positions are picked in the MAP tab's Program panel";
    }

    private void chip(FontRenderer fr, int cx, int cy, int cwid, String label, int col, String id) {
        boolean over = mx >= cx && mx < cx + cwid && my >= cy && my < cy + 9;
        Gui.drawRect(cx, cy, cx + cwid, cy + 9, over ? PrideFrame.brighten(col) : col);
        fr.drawString(fr.trimStringToWidth(label, cwid - 4), cx + 2, cy + 1, 0xFFFFFFFF);
        hit(cx, cy, cwid, 9, 4, "drag:" + id);
    }

    private void miniButton(FontRenderer fr, int bx, int by, String label, String arg) {
        int bw = fr.getStringWidth(label) + 4;
        boolean over = mx >= bx && mx < bx + bw && my >= by && my < by + 9;
        Gui.drawRect(bx, by, bx + bw, by + 9, over ? 0xFF5A3A80 : 0xFF3A2440);
        fr.drawString(label, bx + 2, by + 1, 0xFFFFFFFF);
        hit(bx, by, bw, 9, 4, arg);
    }

    private void block(FontRenderer fr, int bx, int by, int bw, String label, int col, int ruleId, int slot, boolean on) {
        Gui.drawRect(bx, by + 1, bx + bw, by + 10, on ? col : 0xFF303036);
        fr.drawString(fr.trimStringToWidth(label, bw - 4), bx + 2, by + 2, 0xFFFFFFFF);
        slots.add(new int[]{bx, by, bw, 11, ruleId, slot});
        hit(bx, by + 1, bw, 9, 4, "cyc:" + ruleId + ":" + slot);
    }

    private void blockHalf(FontRenderer fr, int bx, int by, int bw, String label, int col, int ruleId, int slot) {
        Gui.drawRect(bx, by + 1, bx + bw, by + 10, col);
        fr.drawString(fr.trimStringToWidth(label, bw - 4), bx + 2, by + 2, 0xFFE0E0E0);
        slots.add(new int[]{bx, by, bw, 11, ruleId, slot});
    }

    private void arrow(FontRenderer fr, int ax, int ay) { fr.drawString("›", ax + 1, ay - 6, C_DIM); }

    private boolean rulePress(int mx, int my) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            int[] b = hits.get(i);
            if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3] && b[4] == 4 && args.get(i).startsWith("drag:")) { ruleDrag = args.get(i).substring(5); return true; }
        }
        return false;
    }

    private void ruleRelease(int mx, int my) {
        if (ruleDrag == null) return;
        String drag = ruleDrag;
        ruleDrag = null;
        for (int[] s : slots) {
            if (mx < s[0] || mx >= s[0] + s[2] || my < s[1] || my >= s[1] + s[3]) continue;
            Rule r = s[4] == 0 ? new Rule() : copy(PacketProgram.client.rule(s[4]));
            if (r == null) return;
            boolean cond = drag.startsWith("c:");
            String name = drag.substring(2);
            int slot = s[4] == 0 ? (cond ? 0 : 2) : s[5];
            if (cond && slot <= 1) {
                Rule.Cond c = Rule.Cond.valueOf(name);
                if (slot == 0) { r.cond = c; if (c.needsText() && r.condText.isEmpty()) r.condText = preset(c, ""); }
                else { r.cond2 = c == Rule.Cond.ALWAYS ? null : c; if (c.needsText() && r.cond2Text.isEmpty()) r.cond2Text = preset(c, ""); }
            } else if (!cond && slot >= 2) {
                Rule.Act a = Rule.Act.valueOf(name);
                if (slot == 2) { r.act = a; if (a.needsText() && r.text.isEmpty()) r.text = actPreset(a, ""); }
                else if (slot == 3) { r.elseAct = a; if (a.needsText() && r.elseText.isEmpty()) r.elseText = actPreset(a, ""); }
                else { r.errAct = a; if (a.needsText() && r.errText.isEmpty()) r.errText = actPreset(a, ""); }
            } else { hint = cond ? "conditions go in WHEN / IF" : "actions go in DO / ELSE / ON ERROR"; return; }
            save(r);
            return;
        }
    }

    private static Rule copy(Rule r) { return r == null ? null : Rule.read(r.write()); }

    private static String preset(Rule.Cond c, String cur) {
        String[] p = c == Rule.Cond.EVENT ? EVENT_PRESETS : c == Rule.Cond.EXPR ? EXPR_PRESETS : TIMER_PRESETS;
        int i = Arrays.asList(p).indexOf(cur);
        return p[(i + 1) % p.length];
    }

    private static String actPreset(Rule.Act a, String cur) {
        String[] p;
        switch (a) {
            case SET_VAR -> p = new String[]{"x = x + 1", "x = 0", "load = trains * 2"};
            case TIMER_START -> p = new String[]{"t1 30", "t2 60", "t3 10"};
            case TIMER_STOP -> p = TIMER_PRESETS;
            case CALL -> p = new String[]{"routine1", "routine2"};
            case EMIT -> p = new String[]{"my:event", "chaos:check", "rule:done"};
            case PRESS -> p = new String[]{"start", "stop", "reset", "close", "open"};
            default -> p = new String[]{"Rule failed: {error}", "Check the railway", "Program alarm"};
        }
        int i = Arrays.asList(p).indexOf(cur);
        return p[(i + 1) % p.length];
    }

    private void save(Rule r) {
        RailMap.NETWORK.sendToServer(PacketProgram.op(PacketProgram.SAVE_RULE, r.write(), box()));
        lastReq = 0;
    }

    private void ruleAction(String a) {
        if (a.startsWith("drag:")) return;
        String[] p = a.split(":");
        Rule r = copy(PacketProgram.client.rule(Integer.parseInt(p[1])));
        if (r == null) return;
        switch (p[0]) {
            case "up": r.priority++; save(r); break;
            case "dn": r.priority--; save(r); break;
            case "tg": { NBTTagCompound t = new NBTTagCompound(); t.setInteger("id", r.id); RailMap.NETWORK.sendToServer(PacketProgram.op(PacketProgram.TOGGLE_RULE, t, box())); lastReq = 0; break; }
            case "del": { NBTTagCompound t = new NBTTagCompound(); t.setInteger("id", r.id); RailMap.NETWORK.sendToServer(PacketProgram.op(PacketProgram.DELETE_RULE, t, box())); lastReq = 0; break; }
            case "cyc": {
                int slot = Integer.parseInt(p[2]);
                if (slot == 0) { if (r.cond.needsText()) r.condText = preset(r.cond, r.condText); else if (r.cond.needsChannel()) r.condChannel = r.condChannel % 16 + 1; else return; }
                else if (slot == 1) { if (r.cond2 != null && r.cond2.needsText()) r.cond2Text = preset(r.cond2, r.cond2Text); else if (r.cond2 != null && r.cond2.needsChannel()) r.cond2Channel = r.cond2Channel % 16 + 1; else { r.cond2 = null; } }
                else if (slot == 2) { if (r.act.needsText()) r.text = actPreset(r.act, r.text); else if (r.act.needsChannel()) r.actChannel = r.actChannel % 16 + 1; else return; }
                else if (slot == 4) { if (r.errAct.needsText()) r.errText = actPreset(r.errAct, r.errText); else r.errAct = Rule.Act.NOTHING; }
                save(r);
                break;
            }
            default: break;
        }
    }
}
