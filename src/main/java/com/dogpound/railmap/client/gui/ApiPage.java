package com.dogpound.railmap.client.gui;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.api.PacketApiOp;
import com.dogpound.railmap.client.ClientRailway;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import java.util.ArrayList;
import java.util.List;

/**
 * The Control Center's API tab (her ChatGPT list #96-105): the mod-compatibility report (#103), every vehicle from every
 * transport adapter (#96), the signal-box program's watchdog / variables and the logic modules (#98, #99), train orders
 * (#101), scenario objectives (#105), the automation audit log (#102) and the export / HTTP status (#104).
 * Everything is live from the server (PacketRailState.api, see api.ApiConsoleData).
 */
final class ApiPage {
    private static final String[] PAGES = {"Compat", "Vehicles", "Program + Logic", "Orders", "Objectives", "Audit", "Export"};
    private static final int ROW = 11;
    private static final int C_TEXT = 0xFFE8ECF0, C_DIM = 0xFF8A94A0, C_HEAD = 0xFF5BCEFA, C_GOOD = 0xFF7CF29A, C_WARN = 0xFFFFC44D, C_BAD = 0xFFFF5252, C_ROW = 0x22FFFFFF;

    private int page, scroll, rowsVisible, rowsTotal;
    private final List<int[]> hits = new ArrayList<>();
    private final List<String> args = new ArrayList<>();
    private int x, y, w, h;

    void wheel(int dir) { scroll = Math.max(0, Math.min(Math.max(0, rowsTotal - rowsVisible), scroll + dir)); }

    boolean click(int mx, int my) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            int[] b = hits.get(i);
            if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) {
                if (b[4] >= 100) { page = b[4] - 100; scroll = 0; }
                else RailMap.NETWORK.sendToServer(new PacketApiOp(b[4], args.get(i)));
                return true;
            }
        }
        return false;
    }

    private void hit(int bx, int by, int bw, int bh, int action, String arg) { hits.add(new int[]{bx, by, bw, bh, action}); args.add(arg); }

    private static List<String[]> rows(String tag) {
        List<String[]> o = new ArrayList<>();
        for (String[] r : ClientRailway.get().api) if (r.length > 0 && r[0].equals(tag)) o.add(r);
        return o;
    }

    private static String g(String[] r, int i) { return r.length > i ? r[i] : ""; }

    private void button(FontRenderer fr, int bx, int by, String label, int op, String arg, int col, int mx, int my) {
        int bw = fr.getStringWidth(label) + 8;
        boolean over = mx >= bx && mx < bx + bw && my >= by && my < by + 11;
        Gui.drawRect(bx, by, bx + bw, by + 11, over ? 0xFF4A3E70 : 0xFF2A2240);
        Gui.drawRect(bx, by + 10, bx + bw, by + 11, col);
        fr.drawString(label, bx + 4, by + 2, 0xFFFFFFFF);
        hit(bx, by, bw, 11, op, arg);
    }

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
        int top = y + 19;
        rowsVisible = Math.max(1, (y + h - top - 30) / ROW);
        switch (page) {
            case 0 -> compat(fr, top, mx, my);
            case 1 -> table(fr, top, "V", new String[]{"Vehicle", "Id", "Mod", "Kind", "km/h", "To / line", "Cargo", "Status", "At"}, new int[]{0, 16, 26, 34, 44, 49, 63, 72, 90},
                    r -> new String[]{r[2], r[1], r[3], r[4], r[5], r[6], r[7], r[8], r[9]}, r -> C_TEXT, "No vehicles loaded (Immersive Railroading, Traincraft and PrideCarts trains all show here)");
            case 2 -> program(fr, top, mx, my);
            case 3 -> orders(fr, top, mx, my);
            case 4 -> objectives(fr, top, mx, my);
            case 5 -> table(fr, top, "A", new String[]{"Time", "Source", "Who", "Target", "Action", "Before > after", "Result"}, new int[]{0, 9, 17, 29, 45, 60, 74},
                    r -> new String[]{r[1], r[2], r[3], r[4], r[5], r[6], r[7]}, r -> r[8].equals("1") ? C_BAD : C_TEXT,
                    "Nothing logged yet - remote, computer, program, logic, order and API commands land here");
            default -> export(fr, top, mx, my);
        }
        if (rowsTotal > rowsVisible) PrideFrame.scrollbar(x + w - 4, top + 12, rowsVisible * ROW, scroll, rowsVisible, rowsTotal);
    }

    // ---- shared table drawing ---------------------------------------------------------------------------------------
    private int col(int[] at, int k) { return x + 6 + at[k] * (w - 12) / 100; }

    private void head(FontRenderer fr, int top, String[] cols, int[] at) {
        for (int k = 0; k < cols.length; k++) fr.drawString(cols[k], col(at, k), top, C_HEAD);
    }

    private void cell(FontRenderer fr, String s, int[] at, int k, int ry, int colour) {
        int right = k + 1 < at.length ? col(at, k + 1) - 3 : x + w - 8;
        fr.drawString(fr.trimStringToWidth(s == null ? "" : s, Math.max(4, right - col(at, k))), col(at, k), ry, colour);
    }

    private List<String[]> page(List<String[]> rs, int visible) {
        rowsTotal = rs.size();
        rowsVisible = visible;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rs.size() - visible)));
        return rs.subList(Math.min(scroll, rs.size()), Math.min(rs.size(), scroll + visible));
    }

    private void table(FontRenderer fr, int top, String tag, String[] cols, int[] at, java.util.function.Function<String[], String[]> cells,
                       java.util.function.ToIntFunction<String[]> colour, String empty) {
        head(fr, top, cols, at);
        int ry = top + 13, i = 0;
        List<String[]> rs = rows(tag);
        if (rs.isEmpty()) fr.drawString(empty, x + 6, ry, C_DIM);
        for (String[] r : page(rs, rowsVisible)) {
            if (i++ % 2 == 0) Gui.drawRect(x + 4, ry - 1, x + w - 6, ry + ROW - 1, C_ROW);
            String[] c = cells.apply(r);
            int cc = colour.applyAsInt(r);
            for (int k = 0; k < c.length; k++) cell(fr, c[k], at, k, ry, cc);
            ry += ROW;
        }
    }

    // ---- pages ------------------------------------------------------------------------------------------------------
    private void compat(FontRenderer fr, int top, int mx, int my) {
        table(fr, top, "C", new String[]{"Area", "Integration", "State", "Detail"}, new int[]{0, 16, 46, 54},
                r -> new String[]{r[1], r[2], r[3], r[4]}, r -> r[3].equals("OK") ? C_GOOD : r[3].equals("ERROR") ? C_BAD : r[3].equals("OFF") ? C_DIM : C_WARN,
                "No data yet");
        int by = y + h - 14;
        button(fr, x + 6, by, "RUN COMPATIBILITY SELF-TEST", 3, "", C_GOOD, mx, my);
        fr.drawString("A missing mod only switches its adapter off - the railway never needs it", x + 180, by + 2, C_DIM);
    }

    private void program(FontRenderer fr, int top, int mx, int my) {
        List<String[]> ps = rows("P");
        int ry = top;
        if (!ps.isEmpty()) {
            String[] p = ps.get(0);
            boolean halted = !p[1].isEmpty();
            Gui.drawRect(x + 4, ry - 1, x + w - 6, ry + 10, halted ? 0xFF5A1414 : 0xFF143A1E);
            fr.drawString(fr.trimStringToWidth(halted ? "PROGRAM HALTED by the watchdog: " + p[1] : "Program running: " + p[5] + " rules, last pass " + p[2] + " steps / "
                    + p[3] + " µs (worst " + p[4] + " µs)", w - 120), x + 8, ry + 1, 0xFFFFFFFF);
            if (halted) button(fr, x + w - 110, ry - 1, "RESET WATCHDOG", 1, "", C_WARN, mx, my);
            ry += 12;
            fr.drawString(fr.trimStringToWidth("Variables: " + (p[6].isEmpty() ? "-" : p[6]), w - 12), x + 6, ry, C_TEXT); ry += ROW;
            fr.drawString(fr.trimStringToWidth("Timers: " + (p[7].isEmpty() ? "-" : p[7]) + "    Events: " + (p[8].isEmpty() ? "-" : p[8]), w - 12), x + 6, ry, C_DIM); ry += ROW + 3;
        }
        String[] cols = {"Module", "Kind", "Function", "Q", "Display", "At", ""};
        int[] at = {0, 14, 34, 48, 52, 80, 92};
        head(fr, ry, cols, at);
        ry += 13;
        List<String[]> ls = rows("L");
        if (ls.isEmpty()) fr.drawString("No logic modules loaded - place a Logic Gate / Timer / Counter / Comparator / Latch / Arithmetic / Sequencer module", x + 6, ry, C_DIM);
        int i = 0;
        for (String[] r : page(ls, Math.max(1, (y + h - ry - 4) / ROW))) {
            if (i++ % 2 == 0) Gui.drawRect(x + 4, ry - 1, x + w - 6, ry + ROW - 1, C_ROW);
            int c = r[7].equals("3") ? C_BAD : r[4].equals("1") ? C_GOOD : C_TEXT;
            cell(fr, r[1], at, 0, ry, c); cell(fr, r[2], at, 1, ry, C_DIM); cell(fr, r[3], at, 2, ry, C_TEXT);
            cell(fr, r[4].equals("1") ? "ON" : "off", at, 3, ry, c); cell(fr, r[5], at, 4, ry, C_TEXT); cell(fr, r[6], at, 5, ry, C_DIM);
            button(fr, col(at, 6), ry - 1, "RESET", 6, r[6] + ":reset", C_WARN, mx, my);
            ry += ROW;
        }
    }

    private void orders(FontRenderer fr, int top, int mx, int my) {
        String[] cols = {"Train", "Template", "Step", "Laps", "Now", "Parameters", ""};
        int[] at = {0, 14, 26, 31, 36, 70, 92};
        head(fr, top, cols, at);
        int ry = top + 13, i = 0;
        List<String[]> js = rows("J");
        if (js.isEmpty()) fr.drawString("No trains on orders - /irextras api orders assign <train> <template> origin=A dest=B load=80 cargo=coal window=06:00-18:00", x + 6, ry, C_DIM);
        for (String[] r : js) {
            if (ry > y + h / 2) break;
            if (i++ % 2 == 0) Gui.drawRect(x + 4, ry - 1, x + w - 6, ry + ROW - 1, C_ROW);
            int c = r[6].startsWith("STOPPED") || r[6].startsWith("error") ? C_BAD : C_TEXT;
            cell(fr, r[2], at, 0, ry, c); cell(fr, r[3], at, 1, ry, C_TEXT); cell(fr, r[4], at, 2, ry, C_TEXT); cell(fr, r[5], at, 3, ry, C_DIM);
            cell(fr, r[6], at, 4, ry, c); cell(fr, r[7], at, 5, ry, C_DIM);
            button(fr, col(at, 6), ry - 1, "CANCEL", 4, r[1], C_BAD, mx, my);
            ry += ROW;
        }
        ry += 6;
        fr.drawString("Templates (steps: GOTO, LOAD pct cargo, UNLOAD, WAIT s, WINDOW hh:mm-hh:mm, WAITCH n, SET n on|off, EMIT, REPEAT, END; $params)", x + 6, ry, C_HEAD);
        ry += 13;
        for (String[] r : page(rows("T"), Math.max(1, (y + h - ry - 4) / ROW))) {
            fr.drawString(fr.trimStringToWidth(r[1] + ":  " + r[2], w - 14), x + 6, ry, C_TEXT);
            ry += ROW;
        }
    }

    private void objectives(FontRenderer fr, int top, int mx, int my) {
        String[] cols = {"Objective", "Mode", "Expression", "State", "Left", ""};
        int[] at = {0, 16, 23, 70, 80, 90};
        head(fr, top, cols, at);
        int ry = top + 13, i = 0;
        List<String[]> os = rows("O");
        if (os.isEmpty()) fr.drawString("No objectives - /irextras api objective <name> <minutes> reach|hold <expression>, or a quest / event mod through the API", x + 6, ry, C_DIM);
        for (String[] r : page(os, rowsVisible - 2)) {
            if (i++ % 2 == 0) Gui.drawRect(x + 4, ry - 1, x + w - 6, ry + ROW - 1, C_ROW);
            int c = r[4].equals("MET") ? C_GOOD : r[4].equals("FAILED") ? C_BAD : C_WARN;
            cell(fr, r[1], at, 0, ry, C_TEXT); cell(fr, r[2], at, 1, ry, C_DIM); cell(fr, r[3], at, 2, ry, C_TEXT); cell(fr, r[4], at, 3, ry, c);
            cell(fr, r[5].equals("-1") ? "-" : r[5] + " s", at, 4, ry, C_DIM);
            button(fr, col(at, 5), ry - 1, "REMOVE", 5, r[1], C_BAD, mx, my);
            ry += ROW;
        }
        int by = y + h - 14;
        button(fr, x + 6, by, "DEMAND BACK TO NORMAL", 7, "", C_WARN, mx, my);
        button(fr, x + 140, by, "CLEAR LAB FAULTS", 8, "", C_WARN, mx, my);
    }

    private void export(FontRenderer fr, int top, int mx, int my) {
        List<String[]> xs = rows("X");
        if (xs.isEmpty()) return;
        String[] r = xs.get(0);
        int ry = top;
        String[][] lines = {
                {"API version", r[7]},
                {"JSON outbox", r[1].equals("on") ? "<world>/pride-web/irextras/{trains,lines,alarms,stats,telemetry,compat}.json" : "off (exportJson = false)"},
                {"Last written", r[2]},
                {"Last error", r[3].isEmpty() ? "none" : r[3]},
                {"HTTP endpoint", r[4].equals("off") ? "off (set httpPort + exportToken in irextras_api.cfg)" : "http://" + r[4] + "/api/v1/<name>  (Bearer token, read only)"},
                {"Requests", r[5] + " served, " + r[6] + " refused (bad token / rate limit)"}};
        for (String[] l : lines) {
            fr.drawString(l[0], x + 6, ry, C_HEAD);
            fr.drawString(fr.trimStringToWidth(l[1], w - 110), x + 100, ry, l[0].equals("Last error") && !r[3].isEmpty() ? C_BAD : C_TEXT);
            ry += ROW + 2;
        }
        button(fr, x + 6, ry + 4, "WRITE SNAPSHOT NOW", 2, "", C_GOOD, mx, my);
    }
}
