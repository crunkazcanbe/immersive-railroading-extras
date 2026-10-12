package com.dogpound.railmap.client.gui;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.client.ClientRailway;
import com.dogpound.railmap.incident.PacketIncidentOp;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import java.util.ArrayList;
import java.util.List;

/**
 * The Control Center's OPS tab (her ChatGPT list #67-80): incident command (the incident list, the chosen one's
 * response, sections, plan, train-recorder download and timeline, with the commander's orders as buttons), alarm
 * root-cause groups (#72), the per-train recorders with a speed / throttle / brake trace (#74), the remote work
 * orders (#75), the announcement log (#76), performance KPIs (#77), the digital twin (#78), reports (#79) and the
 * scenario lab (#80). Everything is live from the server (PacketRailState.incident, see incident.IncidentConsoleData).
 */
final class IncidentPage {
    private static final String[] PAGES = {"Incidents", "Root cause", "Recorder", "Work orders", "PA", "KPIs", "Twin", "Reports", "Scenario"};
    private static final int ROW = 11;
    private static final int C_TEXT = 0xFFE8ECF0, C_DIM = 0xFF8A94A0, C_HEAD = 0xFF5BCEFA, C_GOOD = 0xFF7CF29A, C_WARN = 0xFFFFC44D, C_BAD = 0xFFFF5252,
            C_ROW = 0x22FFFFFF, C_SEL = 0x553A6FD8;
    private static final String[][] ORDERS = {{"ACCEPT", "accept"}, {"LEVEL", "level"}, {"CREW", "crew"}, {"RESCUE", "rescue"}, {"FIRE", "fire"}, {"MEDIC", "medical"},
            {"PROTECT", "protect"}, {"ISOLATE", "isolate"}, {"EVAC", "evac"}, {"ALL CLEAR", "allclear"}, {"REOPEN", "reopen"}, {"CLOSE", "close"}};

    private int page, scroll, rowsTotal, rowsVisible, detail;
    private final List<int[]> hits = new ArrayList<>();
    private final List<String> args = new ArrayList<>();
    private int x, y, w, h;

    void wheel(int dir) { scroll = Math.max(0, Math.min(Math.max(0, rowsTotal - rowsVisible), scroll + dir)); }

    boolean click(int mx, int my) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            int[] b = hits.get(i);
            if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) { act(b[4], args.get(i)); return true; }
        }
        return false;
    }

    private void act(int a, String arg) {
        if (a >= 100 && a < 200) { page = a - 100; scroll = 0; }
        else if (a >= 200 && a < 210) detail = a - 200;
        else if (a >= 1000) RailMap.NETWORK.sendToServer(new PacketIncidentOp(a - 1000, arg));
    }

    private void hit(int bx, int by, int bw, int bh, int action, String arg) { hits.add(new int[]{bx, by, bw, bh, action}); args.add(arg); }

    private static List<String[]> rows(String tag) {
        List<String[]> o = new ArrayList<>();
        for (String[] r : ClientRailway.get().incident) if (r.length > 0 && r[0].equals(tag)) o.add(r);
        return o;
    }

    private static String g(String[] r, int i) { return r.length > i ? r[i] : ""; }

    void draw(FontRenderer fr, int x, int y, int w, int h, int mx, int my) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        hits.clear();
        args.clear();
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
        int top = y + 31;
        rowsVisible = Math.max(1, (y + h - top - 14) / ROW);
        switch (page) {
            case 0 -> incidents(fr, top, mx, my);
            case 1 -> rca(fr, top, mx, my);
            case 2 -> recorder(fr, top, mx, my);
            case 3 -> table(fr, top, "WO", new String[]{"#", "Raised", "Equipment", "At", "Fault", "By", "State"}, new int[]{0, 4, 11, 30, 40, 72, 84},
                    r -> new String[]{r[1], g(r, 9), r[2], r[3], r[4], r[5], r[6].equals("OPEN") ? (r[8].equals("1") ? "OPEN - LOCAL" : "OPEN") : "DONE: " + r[7]},
                    r -> r[6].equals("OPEN") ? C_WARN : C_GOOD, "No work orders - raise them at a Remote Maintenance Terminal");
            case 4 -> table(fr, top, "PA", new String[]{"Time", "Station", "Kind", "Spk", "Announcement"}, new int[]{0, 6, 20, 28, 33},
                    r -> new String[]{r[1], r[2], r[3], r[5], r[4]}, r -> r[3].equals("EVAC") || r[3].equals("EMERG") ? C_BAD : r[3].equals("DELAY") || r[3].equals("CANCEL") ? C_WARN : C_TEXT,
                    "Nothing announced yet (Announcement Control Units in AUTO, emergencies, /irextras incident pa)");
            case 5 -> kpis(fr, top);
            case 6 -> twin(fr, top);
            case 7 -> reports(fr, top, mx, my);
            default -> scenario(fr, top, mx, my);
        }
        if (rowsTotal > rowsVisible) PrideFrame.scrollbar(x + w - 4, top + 12, rowsVisible * ROW, scroll, rowsVisible, rowsTotal);
    }

    private int button(FontRenderer fr, int bx, int by, String label, int action, String arg, int mx, int my) {
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

    private <T> List<T> page(List<T> all, int visible) {
        rowsTotal = all.size();
        rowsVisible = visible;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rowsTotal - visible)));
        return all.subList(Math.min(scroll, all.size()), Math.min(all.size(), scroll + visible));
    }

    private void stripe(int ry, int i, boolean sel) {
        if (i % 2 == 0) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, C_ROW);
        if (sel) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, C_SEL);
    }

    private void table(FontRenderer fr, int top, String tag, String[] cols, int[] at, java.util.function.Function<String[], String[]> cells,
                       java.util.function.ToIntFunction<String[]> colour, String empty) {
        head(fr, top, cols, at);
        int ry = top + 13, i = 0;
        List<String[]> rs = rows(tag);
        if (rs.isEmpty()) fr.drawString(empty, x + 6, ry, C_DIM);
        for (String[] r : page(rs, rowsVisible)) {
            stripe(ry, i++, false);
            String[] c = cells.apply(r);
            int col = colour.applyAsInt(r);
            for (int k = 0; k < c.length; k++) cell(fr, c[k], at, k, ry, k == c.length - 1 ? col : C_TEXT);
            ry += ROW;
        }
    }

    // ---- incidents --------------------------------------------------------------------------------------------------
    private void incidents(FontRenderer fr, int top, int mx, int my) {
        String sel = rows("IS").isEmpty() ? "" : rows("IS").get(0)[1];
        String[] cols = {"#", "Type", "Stage", "L", "What", "Where", "Open", "Resp", "Needs (+ sent)", "Pax", "Held", "Sect"};
        int[] at = {0, 3, 15, 26, 28, 50, 58, 65, 71, 88, 92, 96};
        head(fr, top, cols, at);
        int ry = top + 13, i = 0;
        List<String[]> rs = rows("I");
        int listRows = Math.max(3, Math.min(8, (h - 60) / ROW / 3));
        if (rs.isEmpty()) fr.drawString("No incidents. Wrecks, failed trains, fires and evacuations open one here.", x + 6, ry, C_GOOD);
        for (String[] r : page(rs, listRows)) {
            boolean open = !r[3].equals("CLOSED");
            stripe(ry, i++, r[1].equals(sel));
            int col = !open ? C_DIM : r[3].equals("REPORTED") ? C_BAD : r[3].equals("REOPENING") ? C_GOOD : C_WARN;
            cell(fr, r[1], at, 0, ry, col);
            cell(fr, r[2], at, 1, ry, col);
            cell(fr, r[3], at, 2, ry, col);
            cell(fr, r[4], at, 3, ry, r[4].equals("3") ? C_BAD : C_TEXT);
            cell(fr, r[5], at, 4, ry, C_TEXT);
            cell(fr, r[6], at, 5, ry, C_DIM);
            cell(fr, r[7], at, 6, ry, C_DIM);
            cell(fr, r[8], at, 7, ry, C_DIM);
            cell(fr, r[9], at, 8, ry, C_TEXT);
            cell(fr, r[11] + "/" + r[12], at, 9, ry, C_TEXT);
            cell(fr, r[13], at, 10, ry, C_WARN);
            cell(fr, r[15], at, 11, ry, C_TEXT);
            hit(x + 2, ry - 1, w - 8, ROW, 1001, r[1]);
            ry += ROW;
        }
        // the chosen incident
        String[] me = null;
        for (String[] r : rs) if (r[1].equals(sel)) me = r;
        int dy = top + 13 + listRows * ROW + 4;
        Gui.drawRect(x + 2, dy, x + w - 6, dy + 1, 0x55FFFFFF);
        if (me == null) return;
        dy += 3;
        fr.drawString("#" + me[1] + " " + me[2] + " - " + me[5] + (me[14].isEmpty() ? "" : " - command: " + me[14]) + (me[10].isEmpty() ? "" : " - " + me[10]) + (me[17].isEmpty() ? "" : " - rescue: " + me[17]),
                x + 6, dy, C_HEAD);
        dy += 12;
        int bx = x + 6;
        for (String[] o : ORDERS) {
            int bw = fr.getStringWidth(o[0]) + 13;
            if (bx + bw > x + w - 8) { bx = x + 6; dy += 13; }
            bx += button(fr, bx, dy, o[0], 1002, me[1] + ":" + o[1], mx, my);
        }
        dy += 14;
        String[] views = {"Timeline", "Plan", "Sections", "Recorder", "Report"};
        bx = x + 6;
        for (int k = 0; k < views.length; k++) {
            int bw = fr.getStringWidth(views[k]) + 10;
            Gui.drawRect(bx, dy, bx + bw, dy + 11, detail == k ? 0xFF3A2E5C : 0xFF181226);
            fr.drawString(views[k], bx + 5, dy + 2, detail == k ? 0xFFFFFFFF : C_DIM);
            hit(bx, dy, bw, 11, 200 + k, "");
            bx += bw + 2;
        }
        dy += 14;
        List<String[]> lines = rows(new String[]{"IL", "IP", "IX", "IB", "IR"}[detail]);
        if (lines.isEmpty()) { fr.drawString(new String[]{"No entries", "No plan yet: RESCUE makes a rescue plan, EVAC an evacuation plan", "No line sections closed", "No train recorder download", "The final report is written when the incident closes"}[detail], x + 6, dy, C_DIM); return; }
        for (String[] l : lines) {
            if (dy > y + h - 12) break;
            String s = detail == 0 ? l[1] + "  " + l[2] : detail == 2 ? l[1] + " (" + l[2] + " m): " + l[3] : l[1];
            int col = detail == 2 ? (l[3].startsWith("OPEN") ? C_GOOD : C_BAD) : s.contains("EVAC") || s.contains("OVERSPEED") || s.startsWith("NO ") ? C_WARN : C_TEXT;
            fr.drawString(fr.trimStringToWidth(s, w - 16), x + 6, dy, col);
            dy += 10;
        }
    }

    // ---- root cause --------------------------------------------------------------------------------------------------
    private void rca(FontRenderer fr, int top, int mx, int my) {
        String[] n = rows("GN").isEmpty() ? new String[]{"GN", "0", "0"} : rows("GN").get(0);
        fr.drawString(n[1] + " of " + n[2] + " showing alarms are consequences of a root cause (they don't sound the horn)", x + 6, top - 14 + 2, C_DIM);
        int ry = top + 2;
        List<String[]> gs = rows("G");
        List<String[]> kids = rows("GC");
        if (gs.isEmpty()) { fr.drawString("No correlated alarms: every showing alarm stands on its own", x + 6, ry, C_GOOD); return; }
        List<String[]> lines = new ArrayList<>();
        for (String[] gr : gs) {
            lines.add(gr);
            for (String[] k : kids) if (k[1].equals(gr[1])) lines.add(k);
        }
        rowsVisible = Math.max(1, (y + h - top - 14) / ROW);
        int i = 0;
        for (String[] l : page(lines, rowsVisible)) {
            stripe(ry, i++, false);
            if (l[0].equals("G")) {
                fr.drawString(fr.trimStringToWidth("PROBABLE ROOT CAUSE #" + l[1] + " [" + l[3] + " " + l[4] + "] " + l[2] + "  (" + l[5] + " consequential, " + l[7] + " s ago)", w - 80), x + 6, ry, C_BAD);
                button(fr, x + w - 70, ry - 1, "ACK GROUP", 1005, l[1], mx, my);
            } else {
                fr.drawString(fr.trimStringToWidth("    -> #" + l[2] + " " + l[6] + " [" + l[4] + "] " + l[3], w - 16), x + 6, ry, l[5].equals("1") ? C_WARN : C_DIM);
            }
            ry += ROW;
        }
    }

    // ---- train recorder ----------------------------------------------------------------------------------------------
    private void recorder(FontRenderer fr, int top, int mx, int my) {
        String[] br = rows("BR").isEmpty() ? new String[]{"BR", "1", "10", ""} : rows("BR").get(0);
        fr.drawString("Every engine's own recorder: sample every " + br[1] + " s, keeps " + br[2] + " min", x + 6, top - 14 + 2, C_DIM);
        button(fr, x + w - 80, top - 14, "RATE " + br[1] + " S", 1004, "", mx, my);
        int lw = Math.max(90, w / 4);
        int ry = top;
        List<String[]> bs = rows("B");
        if (bs.isEmpty()) { fr.drawString("No engines in the world", x + 6, ry, C_DIM); return; }
        int i = 0;
        for (String[] b : page(bs, Math.max(1, (y + h - top - 14) / ROW))) {
            boolean on = b[1].equals(br[3]);
            if (on) Gui.drawRect(x + 2, ry - 1, x + lw, ry + ROW - 1, C_SEL); else if (i % 2 == 0) Gui.drawRect(x + 2, ry - 1, x + lw, ry + ROW - 1, C_ROW);
            fr.drawString(fr.trimStringToWidth(b[2] + "  " + b[5] + " km/h", lw - 6), x + 4, ry, on ? 0xFFFFFFFF : C_TEXT);
            hit(x + 2, ry - 1, lw - 2, ROW, 1003, b[1]);
            ry += ROW;
            i++;
        }
        // the chart: speed (green), throttle (amber), brake (red); oldest left
        int gx = x + lw + 8, gw = x + w - 10 - gx, gy = top, gh = Math.max(40, (h - 50) / 2);
        Gui.drawRect(gx, gy, gx + gw, gy + gh, 0xFF06080C);
        for (int k = 1; k < 4; k++) Gui.drawRect(gx, gy + gh * k / 4, gx + gw, gy + gh * k / 4 + 1, 0x22FFFFFF);
        List<String[]> t = rows("BT");
        double vmax = 20;
        for (String[] s : t) vmax = Math.max(vmax, Math.abs(Double.parseDouble(s[2])));
        int span = 1;
        for (String[] s : t) span = Math.max(span, Integer.parseInt(s[1]));
        for (String[] s : t) {
            int px = gx + gw - 2 - (int) ((long) Integer.parseInt(s[1]) * (gw - 4) / span);
            int vs = (int) (Math.abs(Double.parseDouble(s[2])) / vmax * (gh - 2)), th = Integer.parseInt(s[3]) * (gh - 2) / 100, bk = Integer.parseInt(s[4]) * (gh - 2) / 100;
            Gui.drawRect(px, gy + gh - 1 - th, px + 2, gy + gh - th, 0xFFFFC44D);
            Gui.drawRect(px, gy + gh - 1 - bk, px + 2, gy + gh - bk, 0xFFFF5252);
            Gui.drawRect(px, gy + gh - 1 - vs, px + 2, gy + gh - vs + 1, 0xFF7CF29A);
        }
        fr.drawString(String.format("%.0f km/h", vmax), gx + 2, gy + 2, C_GOOD);
        fr.drawString("speed  throttle  brake   " + span + " s", gx + gw - fr.getStringWidth("speed  throttle  brake   " + span + " s") - 2, gy + 2, C_DIM);
        int ey = gy + gh + 4;
        for (String[] e : rows("BE")) {
            if (ey > y + h - 12) break;
            String s = e[1] + "  " + e[2];
            fr.drawString(fr.trimStringToWidth(s, gw), gx, ey, e[2].contains("SPAD") || e[2].contains("PENALTY") || e[2].contains("EMERGENCY") || e[2].contains("LOST") || e[2].contains("DERAIL") ? C_BAD : e[2].contains("AWS") ? C_WARN : C_TEXT);
            ey += 10;
        }
    }

    // ---- KPIs --------------------------------------------------------------------------------------------------------
    private void kpis(FontRenderer fr, int top) {
        String[] cols = {"Measure", "Today", "Last 7 days"};
        int[] at = {0, 45, 70};
        head(fr, top, cols, at);
        List<String[]> t = rows("K"), wk = rows("K7");
        int ry = top + 13, i = 0;
        List<String[]> all = new ArrayList<>(t);
        for (String[] k : wk) { boolean has = false; for (String[] a : t) has |= a[1].equals(k[1]); if (!has) all.add(new String[]{"K", k[1], "-"}); }
        for (String[] k : page(all, rowsVisible)) {
            stripe(ry, i++, false);
            String week = "-";
            for (String[] a : wk) if (a[1].equals(k[1])) week = a[2];
            cell(fr, k[1], at, 0, ry, C_TEXT);
            cell(fr, k[2], at, 1, ry, C_HEAD);
            cell(fr, week, at, 2, ry, C_DIM);
            ry += ROW;
        }
    }

    // ---- digital twin ------------------------------------------------------------------------------------------------
    private void twin(FontRenderer fr, int top) {
        List<String> l = new ArrayList<>();
        List<Integer> c = new ArrayList<>();
        String[] wc = rows("WC").isEmpty() ? new String[]{"WC", "0"} : rows("WC").get(0);
        l.add("PREDICTED CONFLICTS (next 2 min): " + wc[1]); c.add(wc[1].equals("0") ? C_GOOD : C_BAD);
        for (String[] r : rows("WX")) { l.add("  " + r[1] + " x " + r[2] + " at " + r[3] + " in " + r[4] + " s - " + r[5]); c.add(C_BAD); }
        l.add("LINES"); c.add(C_HEAD);
        for (String[] r : rows("WL")) { l.add("  " + r[1] + ": " + r[2] + " trains, " + r[3] + " moving, " + r[4] + " held, worst delay " + r[5] + " min, " + r[6] + " stops, " + r[7]); c.add(r[4].equals("0") ? C_TEXT : C_WARN); }
        l.add("TRACTION SECTIONS"); c.add(C_HEAD);
        for (String[] r : rows("WF")) { l.add("  feeder " + r[1] + " (" + r[2] + ", " + r[3] + " m): " + r[4] + (r[5].isEmpty() ? "" : " - " + r[5])); c.add(r[4].equals("LIVE") ? C_GOOD : C_BAD); }
        l.add("POINTS ON ROUTES AHEAD"); c.add(C_HEAD);
        for (String[] r : rows("WS")) { l.add("  " + r[1] + " for " + r[2] + ": needs " + r[3] + ", lies " + r[4] + (r[6].isEmpty() ? "" : " (" + r[6] + " m ahead)")); c.add(r[5].equals("1") ? C_GOOD : C_WARN); }
        String[] ww = rows("WW").isEmpty() ? new String[]{"WW", "0"} : rows("WW").get(0);
        l.add("MAINTENANCE - " + ww[1] + " open work order(s)"); c.add(C_HEAD);
        for (String[] r : rows("WM")) { l.add("  " + r[1] + " wear " + r[2] + " % at " + r[3]); c.add(C_WARN); }
        int ry = top, i = 0;
        rowsVisible = Math.max(1, (y + h - top - 14) / ROW);
        List<String> pl = page(l, rowsVisible);
        for (String s : pl) { fr.drawString(fr.trimStringToWidth(s, w - 16), x + 6, ry, c.get(scroll + i)); ry += ROW; i++; }
    }

    // ---- reports -----------------------------------------------------------------------------------------------------
    private void reports(FontRenderer fr, int top, int mx, int my) {
        button(fr, x + w - 8 - fr.getStringWidth("REPORT NOW") - 13, top - 14, "REPORT NOW", 1007, "", mx, my);
        fr.drawString("Daily at midnight, weekly every 7th day - also as JSON in <world>/data/irextras_reports", x + 6, top - 12, C_DIM);
        int lw = Math.max(110, w / 4), ry = top;
        for (String[] r : rows("R")) {
            if (ry > y + h - 12) break;
            if (r[3].equals("1")) Gui.drawRect(x + 2, ry - 1, x + lw, ry + ROW - 1, C_SEL);
            fr.drawString(fr.trimStringToWidth(r[2], lw - 6), x + 4, ry, r[3].equals("1") ? 0xFFFFFFFF : C_TEXT);
            hit(x + 2, ry - 1, lw - 2, ROW, 1006, r[1]);
            ry += ROW;
        }
        if (rows("R").isEmpty()) fr.drawString("No reports yet - the first comes at midnight (or REPORT NOW)", x + 6, ry, C_DIM);
        List<String[]> t = rows("RT");
        rowsVisible = Math.max(1, (y + h - top - 14) / 10);
        int ty = top, i = 0;
        for (String[] l : page(t, rowsVisible)) {
            fr.drawString(fr.trimStringToWidth(l[1], w - lw - 20), x + lw + 8, ty, l[1].startsWith("==") ? C_HEAD : l[1].startsWith("#") ? C_WARN : C_TEXT);
            ty += 10; i++;
        }
    }

    // ---- scenario ----------------------------------------------------------------------------------------------------
    private void scenario(FontRenderer fr, int top, int mx, int my) {
        String[] s = rows("S").isEmpty() ? new String[]{"S", "0", "-", "0", "0", "", "", "60"} : rows("S").get(0);
        int ry = top - 14;
        fr.drawString(s[1].equals("1") ? "Saved state: " + s[3] + " lines, " + s[4] + " trains, saved " + s[2] : "No saved state - SAVE takes a snapshot of the live railway", x + 6, ry + 2, s[1].equals("1") ? C_TEXT : C_WARN);
        ry += 14;
        int bx = x + 6;
        bx += button(fr, bx, ry, "SAVE STATE", 1008, "", mx, my);
        bx += button(fr, bx, ry, "RUN " + s[7] + " MIN", 1010, "", mx, my);
        bx += button(fr, bx, ry, "RESET", 1009, "reset:0", mx, my);
        ry += 14;
        fr.drawString("Scenario: " + s[5], x + 6, ry, C_HEAD);
        ry += 12;
        String[][] knobs = {{"FAULT AT", "fault", "1"}, {"FAULT MIN", "faultmin", "5"}, {"DWELL S", "dwell", "10"}, {"SPEED %", "speed", "10"}, {"LINE", "line", "1"}, {"TRAINS", "trains", "1"}};
        bx = x + 6;
        for (String[] k : knobs) {
            int bw = fr.getStringWidth(k[0]) + 34;
            if (bx + bw > x + w - 8) { bx = x + 6; ry += 13; }
            bx += button(fr, bx, ry, "-", 1009, k[1] + ":-" + k[2], mx, my);
            fr.drawString(k[0], bx, ry + 2, C_TEXT);
            bx += fr.getStringWidth(k[0]) + 3;
            bx += button(fr, bx, ry, "+", 1009, k[1] + ":" + k[2], mx, my) + 6;
        }
        ry += 16;
        // infra #47 graded drills: the knobs above are your response
        fr.drawString("GRADED DRILLS:", x + 6, ry + 2, C_HEAD);
        bx = x + 6 + fr.getStringWidth("GRADED DRILLS: ");
        String[] drills = {"PEAK", "POWER FAIL", "BLOCKED JUNCTION", "FREIGHT SURGE", "WEATHER"};
        java.util.Map<String, String> best = new java.util.HashMap<>();
        for (String[] g : rows("SG")) if (g.length > 2) best.put(g[1], g[2]);
        for (int i = 0; i < drills.length; i++) {
            String lab = drills[i] + " [" + best.getOrDefault(drills[i], "-") + "]";
            if (bx + fr.getStringWidth(lab) + 8 > x + w - 8) { bx = x + 6; ry += 13; }
            bx += button(fr, bx, ry, lab, 1011, String.valueOf(i), mx, my) + 2;
        }
        ry += 16;
        List<String[]> res = rows("SR");
        if (res.isEmpty()) { fr.drawString("No run yet. The live world is never changed: the saved state is played forward twice (as is / with the scenario).", x + 6, ry, C_DIM); return; }
        fr.drawString("Last run: " + s[6], x + 6, ry, C_DIM);
        ry += 12;
        String[] cols = {"Measure", "As is", "Scenario", "Change"};
        int[] at = {0, 40, 58, 76};
        head(fr, ry, cols, at);
        ry += 13;
        int i = 0;
        for (String[] r : res) {
            if (ry > y + h - 12) break;
            stripe(ry, i++, false);
            cell(fr, r[1], at, 0, ry, C_TEXT);
            cell(fr, r[2], at, 1, ry, C_DIM);
            cell(fr, r[3], at, 2, ry, C_HEAD);
            boolean up = r[4].startsWith("+") && !r[4].matches("\\+0(\\.0)?");
            boolean worse = r[1].contains("delay") || r[1].contains("held") || r[1].contains("Wait") ? up : !up && !r[4].matches("[+-]?0(\\.0)?");
            cell(fr, r[4], at, 3, ry, r[4].matches("[+-]?0(\\.0)?") ? C_DIM : worse ? C_BAD : C_GOOD);
            ry += ROW;
        }
    }
}
