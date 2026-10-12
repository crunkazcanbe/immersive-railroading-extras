package com.dogpound.railmap.client.gui;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.client.ClientRailway;
import com.dogpound.railmap.planning.PacketPlanOp;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

/**
 * The Control Center's PLAN tab (her "next 48" list #1, 3, 4, 6, 7, 8, 9, 40, 42, 46, 48): the digital twin lab with its
 * working case and PROMOTE, what-if queries, timetable robustness, network resilience, temporary works, rolling-stock
 * compatibility, split / join, the yard planner, configuration versions, the AI dispatchers and the historical trends.
 * Live from the server (PacketRailState.plan, see planning.PlanConsole); buttons go back as PacketPlanOp. Pages with a
 * command line take typed input (click the box, type, ENTER).
 */
final class PlanPage {
    private static final String[] PAGES = {"Twin", "What-if", "Robust", "Resilience", "Works", "Compat", "Split/Join", "Yard", "Versions", "Dispatch", "Trends", "Possession"};
    private static final int ROW = 11;
    private static final int C_TEXT = 0xFFE8ECF0, C_DIM = 0xFF8A94A0, C_HEAD = 0xFF5BCEFA, C_GOOD = 0xFF7CF29A, C_WARN = 0xFFFFC44D, C_BAD = 0xFFFF5252, C_ROW = 0x22FFFFFF;

    private int page, scroll, rowsVisible, rowsTotal, trendView;
    private final List<int[]> hits = new ArrayList<>();
    private final List<String> args = new ArrayList<>();
    private int x, y, w, h;
    String hint = "";
    // works page settings + split cars
    private int wKind, wValue = 30, wRadius = 32, wMinutes = 60, splitCars = 2, verCat;
    private String verSel = "";
    // the command line
    private final StringBuilder text = new StringBuilder();
    private boolean typing;
    private int textX, textY, textW;

    private static final String[] KINDS = {"TSR", "SINGLE_LINE", "CROSSING", "LENGTH", "POSSESSION"};
    private static final String[] CATS = {"lines", "signalling", "automation", "power", "works", "dispatch"};

    void wheel(int dir) { scroll = Math.max(0, Math.min(Math.max(0, rowsTotal - rowsVisible), scroll + dir)); }

    boolean keyTyped(char c, int key) {
        if (!typing) return false;
        if (key == Keyboard.KEY_ESCAPE) { typing = false; return true; }
        if (key == Keyboard.KEY_RETURN) { submit(); typing = false; return true; }
        if (key == Keyboard.KEY_BACK) { if (text.length() > 0) text.setLength(text.length() - 1); return true; }
        if (c >= 32 && c != 127 && text.length() < 200) text.append(c);
        return true;
    }

    private void submit() {
        String t = text.toString().trim();
        if (t.isEmpty()) return;
        switch (page) {
            case 1 -> send(6, t);
            case 5 -> send(7, t);
            case 6 -> send(23, t.replace(";", ":"));
            case 7 -> send(21, t.replace(";", ":"));
            case 9 -> send(14, t.replace(";", ":"));
            case 8 -> send(11, CATS[verCat] + ":" + t);
            default -> { }
        }
        text.setLength(0);
    }

    private static void send(int op, String arg) { RailMap.NETWORK.sendToServer(new PacketPlanOp(op, arg)); }

    boolean click(int mx, int my) {
        if (textW > 0 && mx >= textX && mx < textX + textW && my >= textY && my < textY + 12) { typing = true; return true; }
        typing = false;
        for (int i = hits.size() - 1; i >= 0; i--) {
            int[] b = hits.get(i);
            if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) {
                if (b[4] >= 100 && b[4] < 200) { page = b[4] - 100; scroll = 0; text.setLength(0); }
                else if (b[4] >= 200) local(b[4] - 200, args.get(i));
                else send(b[4], args.get(i));
                return true;
            }
        }
        return false;
    }

    /** page-local knobs (no server round trip) */
    private void local(int what, String arg) {
        int d = arg.equals("-") ? -1 : 1;
        switch (what) {
            case 0 -> wKind = Math.floorMod(wKind + d, KINDS.length);
            case 1 -> wValue = Math.max(5, Math.min(2000, wValue + d * (KINDS[wKind].equals("LENGTH") ? 20 : 5)));
            case 2 -> wRadius = Math.max(4, Math.min(512, wRadius + d * 8));
            case 3 -> wMinutes = Math.max(0, Math.min(1440, wMinutes + d * 15));
            case 4 -> splitCars = Math.max(1, Math.min(60, splitCars + d));
            case 5 -> verCat = Math.floorMod(verCat + d, CATS.length);
            case 6 -> verSel = arg;
            case 7 -> trendView = Math.floorMod(trendView + d, 6);
            default -> { }
        }
    }

    private void hit(int bx, int by, int bw, int bh, int action, String arg) { hits.add(new int[]{bx, by, bw, bh, action}); args.add(arg); }

    private static List<String[]> rows(String tag) {
        List<String[]> o = new ArrayList<>();
        for (String[] r : ClientRailway.get().plan) if (r.length > 0 && r[0].equals(tag)) o.add(r);
        return o;
    }

    private static String[] first(String tag) { List<String[]> l = rows(tag); return l.isEmpty() ? null : l.get(0); }

    private static String g(String[] r, int i) { return r != null && r.length > i ? r[i] : ""; }

    private int button(FontRenderer fr, int bx, int by, String label, int op, String arg, int col, int mx, int my) {
        int bw = fr.getStringWidth(label) + 8;
        boolean over = mx >= bx && mx < bx + bw && my >= by && my < by + 11;
        Gui.drawRect(bx, by, bx + bw, by + 11, over ? 0xFF4A3E70 : 0xFF2A2240);
        Gui.drawRect(bx, by + 10, bx + bw, by + 11, col);
        fr.drawString(label, bx + 4, by + 2, 0xFFFFFFFF);
        hit(bx, by, bw, 11, op, arg);
        return bx + bw + 3;
    }

    private int small(FontRenderer fr, int bx, int by, String label, int op, String arg, int mx, int my) {
        int bw = fr.getStringWidth(label) + 4;
        boolean over = mx >= bx && mx < bx + bw && my >= by && my < by + 10;
        Gui.drawRect(bx, by, bx + bw, by + 10, over ? 0xFF4A3E70 : 0xFF2A2240);
        fr.drawString(label, bx + 2, by + 1, 0xFFFFFFFF);
        hit(bx, by, bw, 10, op, arg);
        return bx + bw + 2;
    }

    private void commandLine(FontRenderer fr, int by, String help, int mx, int my) {
        textX = x + 6; textY = by; textW = w - 14;
        Gui.drawRect(textX, textY, textX + textW, textY + 12, typing ? 0xFF1E2A44 : 0xFF141020);
        Gui.drawRect(textX, textY + 11, textX + textW, textY + 12, typing ? C_HEAD : 0xFF3A2E5C);
        String s = text.length() == 0 && !typing ? help : text + (typing && System.currentTimeMillis() / 400 % 2 == 0 ? "_" : "");
        fr.drawString(fr.trimStringToWidth(s, textW - 6), textX + 3, textY + 2, text.length() == 0 && !typing ? C_DIM : C_TEXT);
    }

    void draw(FontRenderer fr, int x, int y, int w, int h, int mx, int my) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        hits.clear(); args.clear(); hint = ""; textW = 0;
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
        rowsTotal = 0;
        switch (page) {
            case 0 -> twin(fr, top, mx, my);
            case 1 -> whatIf(fr, top, mx, my);
            case 2 -> robust(fr, top, mx, my);
            case 3 -> resilience(fr, top, mx, my);
            case 4 -> works(fr, top, mx, my);
            case 5 -> compat(fr, top, mx, my);
            case 6 -> splitJoin(fr, top, mx, my);
            case 7 -> yard(fr, top, mx, my);
            case 8 -> versions(fr, top, mx, my);
            case 9 -> dispatch(fr, top, mx, my);
            case 10 -> trends(fr, top, mx, my);
            default -> possessions(fr, top, mx, my);
        }
        if (rowsTotal > rowsVisible) PrideFrame.scrollbar(x + w - 4, top + 12, rowsVisible * ROW, scroll, rowsVisible, rowsTotal);
    }

    // ---- table helpers ------------------------------------------------------------------------------------------------
    private int col(int[] at, int k) { return x + 6 + at[k] * (w - 12) / 100; }

    private void head(FontRenderer fr, int top, String[] cols, int[] at) { for (int k = 0; k < cols.length; k++) fr.drawString(cols[k], col(at, k), top, C_HEAD); }

    private void cell(FontRenderer fr, String s, int[] at, int k, int ry, int colour) {
        int right = k + 1 < at.length ? col(at, k + 1) - 3 : x + w - 8;
        fr.drawString(fr.trimStringToWidth(s == null ? "" : s, Math.max(4, right - col(at, k))), col(at, k), ry, colour);
    }

    /** a table of rows tagged {@code tag}, cells from index 1, drawn from ry, at most maxRows (scrolls if it's the main table) */
    private int table(FontRenderer fr, int ry, String tag, String[] cols, int[] at, int maxRows, boolean scrolls, java.util.function.ToIntFunction<String[]> colour, String empty) {
        head(fr, ry, cols, at);
        ry += 12;
        List<String[]> rs = rows(tag);
        if (rs.isEmpty()) { fr.drawString(empty, x + 6, ry, C_DIM); return ry + ROW; }
        int from = 0;
        if (scrolls) { rowsTotal = rs.size(); rowsVisible = maxRows; scroll = Math.max(0, Math.min(scroll, Math.max(0, rs.size() - maxRows))); from = scroll; }
        int i = 0;
        for (int k = from; k < rs.size() && i < maxRows; k++, i++) {
            String[] r = rs.get(k);
            if (i % 2 == 0) Gui.drawRect(x + 4, ry - 1, x + w - 6, ry + ROW - 1, C_ROW);
            int c = colour.applyAsInt(r);
            for (int j = 0; j < cols.length; j++) cell(fr, g(r, j + 1), at, j, ry, c);
            ry += ROW;
        }
        return ry;
    }

    private static int good(String s) { return s.equals("+") ? C_GOOD : s.equals("-") ? C_BAD : C_TEXT; }

    // ---- TWIN (#1) ------------------------------------------------------------------------------------------------------
    private void twin(FontRenderer fr, int top, int mx, int my) {
        String[] k = first("K");
        if (k == null) { fr.drawString("No data yet", x + 6, top, C_DIM); return; }
        int lx = x + 6, ry = top;
        fr.drawString("WORKING CASE  §7(" + g(k, 18) + " trains, " + g(k, 19) + " lines, " + g(k, 20) + " stretches)", lx, ry, C_HEAD);
        ry += 12;
        Object[][] knobs = {
                {"Line", g(k, 1), "line"}, {"Stretch", g(k, 2) + (g(k, 22).equals("1") ? " (single)" : ""), "seg"}, {"Station", g(k, 3), "stop"},
                {"Dwell on line", sign(g(k, 4)) + " s", "dwell"}, {"Speed on line", sign(g(k, 5)) + " %", "speed"}, {"Trains on line", sign(g(k, 6)), "trains"},
                {"CBTC on line", onoff(g(k, 7)), "cbtc"}, {"Close stretch", mins(g(k, 8)), "close"}, {"Close station", mins(g(k, 9)), "closestop"},
                {"TSR / broken point", g(k, 10).equals("0") ? "-" : "-" + g(k, 10) + " % speed", "tsr"}, {"Single-line working", onoff(g(k, 11)), "single"},
                {"Power cut stretch", mins(g(k, 12)), "power"}, {"Electrify stretch", onoff(g(k, 13)), "elec"}, {"Double stretch", onoff(g(k, 14)), "double"},
                {"Recovery allowance", g(k, 15) + " %", "rec"}, {"Delay to a train", g(k, 16).equals("0") ? "-" : g(k, 16) + " s", "delay"}, {"Simulate", g(k, 17) + " min x 3", "minutes"}};
        int colW = Math.min(250, (w - 12) / 2 - 4);
        for (Object[] kn : knobs) {
            fr.drawString((String) kn[0], lx, ry + 1, C_DIM);
            boolean flag = ((String) kn[1]).equals("ON") || ((String) kn[1]).equals("off");
            String val = fr.trimStringToWidth((String) kn[1], colW - 130);
            fr.drawString(val, lx + 92, ry + 1, ((String) kn[1]).equals("ON") ? C_WARN : C_TEXT);
            int bx = lx + colW - 30;
            if (flag) small(fr, bx + 6, ry, "SET", 1, kn[2] + ":1", mx, my);
            else { small(fr, bx, ry, "-", 1, kn[2] + ":-1", mx, my); small(fr, bx + 12, ry, "+", 1, kn[2] + ":1", mx, my); }
            ry += ROW;
        }
        int by = y + h - 14, bx = x + 6;
        bx = button(fr, bx, by, "RUN TWIN", 2, "", C_GOOD, mx, my);
        bx = button(fr, bx, by, "PROMOTE TO LIVE", 3, "", g(k, 21).equals("1") ? C_GOOD : C_BAD, mx, my);
        bx = button(fr, bx, by, "RESET CASE", 1, "reset:0", C_WARN, mx, my);
        button(fr, bx, by, "SURVEY STRETCH 1/2 TRACK", 27, "", C_HEAD, mx, my);
        // right: results
        int rx = x + 6 + colW + 8;
        int rw = x + w - 8 - rx;
        int saveX = this.x, saveW = this.w;
        this.x = rx - 6; this.w = rw + 12;
        int yy = top;
        String[] tv = first("TV");
        fr.drawString(tv == null ? "AS IS vs WORKING CASE - press RUN TWIN" : "AS IS vs WORKING CASE - " + g(tv, 1), rx, yy, tv == null ? C_DIM : g(tv, 1).startsWith("PROMOTABLE") ? C_GOOD : C_BAD);
        yy += 12;
        yy = table(fr, yy, "TC", new String[]{"Measure", "As is", "Case", "Change"}, new int[]{0, 46, 62, 80}, 14, false, r -> good(g(r, 5)), "-");
        yy += 3;
        yy = table(fr, yy, "TB", new String[]{"Bottleneck", "Train-min wait", "Occupied %", "Track"}, new int[]{0, 46, 70, 86}, 4, false, r -> C_WARN, "No bottlenecks");
        for (String[] d : rows("TD")) { fr.drawString(fr.trimStringToWidth("DEADLOCK: " + g(d, 1), rw), rx, yy, C_BAD); yy += ROW; }
        yy += 2;
        for (String[] p : rows("TP")) { fr.drawString(fr.trimStringToWidth("PROMOTE > " + g(p, 1), rw), rx, yy, g(p, 1).startsWith("LIVE") || g(p, 1).startsWith("WORKS") ? C_GOOD : C_DIM); yy += ROW - 1; }
        yy += 3;
        fr.drawString("PREDICTED vs ACTUAL", rx, yy, C_HEAD);
        yy += 11;
        for (String[] p : rows("PL")) { fr.drawString(fr.trimStringToWidth("live: " + g(p, 1) + " " + g(p, 2) + " trains/h, " + g(p, 3) + " s delay - checked in " + g(p, 4) + " min", rw), rx, yy, C_DIM); yy += ROW - 1; }
        table(fr, yy, "PA", new String[]{"At", "Line", "Pred/h", "Actual/h", "Pred s", "Error"}, new int[]{0, 16, 46, 60, 76, 88}, Math.max(1, (y + h - 20 - yy) / ROW - 1), false,
                r -> Math.abs(num(g(r, 6))) <= 15 ? C_GOOD : Math.abs(num(g(r, 6))) <= 40 ? C_WARN : C_BAD, "no prediction checked yet (RUN TWIN, then wait)");
        this.x = saveX; this.w = saveW;
        rowsTotal = 0;
    }

    private static String sign(String v) { return v.startsWith("-") || v.equals("0") ? v : "+" + v; }
    private static String onoff(String v) { return v.equals("1") ? "ON" : "off"; }
    private static String mins(String v) { return v.equals("0") ? "-" : v + " min"; }
    private static double num(String s) { try { return Double.parseDouble(s.replace("%", "").replace("+", "").trim()); } catch (NumberFormatException e) { return 0; } }

    // ---- WHAT-IF (#48) ------------------------------------------------------------------------------------------------
    private void whatIf(FontRenderer fr, int top, int mx, int my) {
        String[] k = first("K");
        String line = g(k, 1).replace(' ', '_'), seg = g(k, 2);
        String[] ends = seg.split("~");
        String a = ends.length == 2 ? ends[0].replace(' ', '_') : "", b = ends.length == 2 ? ends[1].replace(' ', '_') : "";
        int bx = x + 6, ry = top;
        bx = button(fr, bx, ry, "ELECTRIFY " + g(k, 1), 6, "electrify " + line, C_HEAD, mx, my);
        bx = button(fr, bx, ry, "ELECTRIFY ALL", 6, "electrify *", C_HEAD, mx, my);
        bx = button(fr, bx, ry, "DOUBLE CAPACITY " + seg.replace("~", "-"), 6, "capacity " + a + " " + b, C_GOOD, mx, my);
        button(fr, bx, ry, "CLOSE 30 MIN", 6, "close " + a + " " + b + " 30", C_BAD, mx, my);
        ry += 13; bx = x + 6;
        bx = button(fr, bx, ry, "SINGLE-LINE", 6, "single " + a + " " + b, C_WARN, mx, my);
        bx = button(fr, bx, ry, "POWER CUT 20", 6, "power " + a + " " + b + " 20", C_BAD, mx, my);
        bx = button(fr, bx, ry, "+1 TRAIN", 6, "trains " + line + " 1", C_GOOD, mx, my);
        bx = button(fr, bx, ry, "DWELL -10", 6, "dwell " + line + " -10", C_GOOD, mx, my);
        bx = button(fr, bx, ry, "SPEED +10", 6, "speed " + line + " 10", C_GOOD, mx, my);
        button(fr, bx, ry, "CBTC", 6, "cbtc " + line, C_HEAD, mx, my);
        ry += 14;
        commandLine(fr, ry, "type a question: electrify <line|*> | capacity <A> <B> | close <A> <B> [min] | single <A> <B> | power <A> <B> [min] | trains/dwell/speed <line> <n> | cbtc <line>  (spaces in names: _)", mx, my);
        ry += 16;
        String[] qh = first("QH");
        if (qh != null) {
            Gui.drawRect(x + 4, ry - 1, x + w - 6, ry + 10, g(qh, 2).startsWith("WORTH") || g(qh, 2).startsWith("CHEAPEST") ? 0xFF143A1E : g(qh, 2).startsWith("HARMFUL") || g(qh, 2).startsWith("NOT") || g(qh, 2).startsWith("NOTHING") ? 0xFF5A1414 : 0xFF3A3010);
            fr.drawString(fr.trimStringToWidth("Q: " + g(qh, 1) + "   ->   " + g(qh, 2), w - 16), x + 8, ry + 1, 0xFFFFFFFF);
            ry += 14;
        }
        fr.drawString("ASSUMPTIONS", x + 6, ry, C_HEAD);
        ry += 11;
        for (String[] r : rows("QA")) { fr.drawString(fr.trimStringToWidth("· " + g(r, 1), w - 14), x + 8, ry, C_DIM); ry += ROW - 1; }
        ry += 3;
        ry = table(fr, ry, "QR", new String[]{"Result", "As is", "If", "Change"}, new int[]{0, 40, 56, 72}, 9, false, r -> good(g(r, 5)), "Ask a question");
        ry += 3;
        table(fr, ry, "QT", new String[]{"Trade-off / option", "Cost", "What you get", "Verdict"}, new int[]{0, 34, 44, 88}, Math.max(1, (y + h - ry - 18) / ROW), false,
                r -> g(r, 4).startsWith("DOUBLES") || g(r, 4).equals("GOOD") ? C_GOOD : g(r, 4).equals("NO") || g(r, 4).equals("AVOID") ? C_BAD : C_WARN, "-");
    }

    // ---- ROBUSTNESS (#6) ----------------------------------------------------------------------------------------------
    private void robust(FontRenderer fr, int top, int mx, int my) {
        int ry = table(fr, top, "XT", new String[]{"Timetable", "Recov %", "Punct %", "Delay s", "Conn kept %", "Min margin s", "Plat. confl", "Knock-on trains", "Extra min", "Stations hit", "Deps to recover"},
                new int[]{0, 10, 18, 26, 34, 44, 54, 63, 74, 83, 92}, 3, false, r -> g(r, 1).equals("RESILIENT") ? C_GOOD : g(r, 1).equals("TIGHT") ? C_WARN : C_TEXT, "Press RUN STUDY");
        for (String[] a : rows("XA")) { fr.drawString(fr.trimStringToWidth(g(a, 1), w - 12), x + 6, ry + 2, C_DIM); ry += ROW + 2; }
        ry += 4;
        int half = (w - 12) / 2;
        int sx = x, sw = w;
        this.w = half;
        int ly = table(fr, ry, "XC", new String[]{"Interchange", "Connections", "Missed", "Avg margin s", "Min s"}, new int[]{0, 40, 60, 74, 90}, Math.max(1, (y + h - ry - 30) / ROW), false,
                r -> num(g(r, 3)) > 0 ? C_WARN : C_TEXT, "No interchanges (stations two lines share)");
        this.x = sx + half;
        table(fr, ry, "XP", new String[]{"Platform conflicts at", "Train-min waiting"}, new int[]{0, 70}, Math.max(1, (y + h - ry - 30) / ROW), false, r -> C_WARN, "No platform conflicts");
        this.x = sx; this.w = sw;
        button(fr, x + 6, y + h - 14, "RUN ROBUSTNESS STUDY (tight / as run / resilient + a 3 min delay)", 4, "", C_GOOD, mx, my);
    }

    // ---- RESILIENCE (#3) ----------------------------------------------------------------------------------------------
    private void resilience(FontRenderer fr, int top, int mx, int my) {
        String[] s = first("RS");
        int ry = top;
        if (s != null) {
            int score = (int) num(g(s, 1));
            int c = score >= 75 ? C_GOOD : score >= 50 ? C_WARN : C_BAD;
            Gui.drawRect(x + 6, ry, x + 6 + (w - 12) * score / 100, ry + 9, c & 0x88FFFFFF);
            fr.drawString("RESILIENCE " + score + "/100   " + g(s, 2) + " towns, " + g(s, 3) + " stretches, " + g(s, 4) + " network(s), " + g(s, 5) + " single points of failure, " + g(s, 6) + " towns cut off now", x + 8, ry + 1, 0xFFFFFFFF);
            ry += 13;
        }
        ry = table(fr, ry, "RE", new String[]{"Stretch", "km", "Track", "Alt paths", "Detour km", "Trains/h", "Capacity/h", "Spare %", "Now"}, new int[]{0, 34, 41, 49, 58, 67, 76, 86, 94},
                Math.max(3, (h - 150) / ROW / 2), true, r -> g(r, 9).equals("CUT") ? C_BAD : g(r, 4).equals("SPOF") ? C_WARN : num(g(r, 8)) < 20 ? C_WARN : C_TEXT, "Press STUDY");
        ry += 3;
        for (String[] r : rows("RI")) { fr.drawString("CUT OFF NOW: " + g(r, 1), x + 6, ry, C_BAD); ry += ROW; }
        ry = table(fr, ry, "RC", new String[]{"If this fails", "Towns cut off", "#"}, new int[]{0, 34, 94}, 4, false, r -> C_WARN, "No single points of failure");
        ry += 3;
        table(fr, ry, "RU", new String[]{"#", "Recommended upgrade", "Why", "Cost"}, new int[]{0, 4, 40, 90}, Math.max(1, (y + h - ry - 18) / ROW), false, r -> C_GOOD, "-");
        button(fr, x + 6, y + h - 14, "STUDY THE NETWORK NOW", 5, "", C_GOOD, mx, my);
    }

    // ---- WORKS (#7) ---------------------------------------------------------------------------------------------------
    private void works(FontRenderer fr, int top, int mx, int my) {
        int ry = table(fr, top, "W", new String[]{"#", "Kind", "Name", "Phase", "Value", "Where", "Time", "State", "By"}, new int[]{0, 4, 16, 34, 46, 55, 72, 84, 94},
                Math.max(3, (h - 80) / ROW), true, r -> g(r, 4).equals("ACTIVE") ? C_WARN : g(r, 4).equals("CLEARED") ? C_DIM : g(r, 4).equals("INSTALLING") || g(r, 4).equals("REMOVING") ? C_HEAD : C_TEXT,
                "No temporary configurations - plan one below (it is placed where you stand)");
        int i = 0;
        for (String[] r : rows("W")) {
            if (i >= scroll && i < scroll + rowsVisible) {
                int yy = top + 12 + (i - scroll) * ROW;
                int bx = x + w - 92;
                if (!g(r, 4).equals("CLEARED") && !g(r, 4).equals("REMOVING")) bx = small(fr, bx, yy - 1, "TAKE OUT", 9, g(r, 1), mx, my);
                if (g(r, 2).equals("CROSSING") && g(r, 4).equals("ACTIVE")) small(fr, bx, yy - 1, "AUTH", 10, g(r, 1) + ":1", mx, my);
            }
            i++;
        }
        int by = y + h - 28, bx = x + 6;
        fr.drawString("NEW:", bx, by + 2, C_HEAD);
        bx += 26;
        bx = small(fr, bx, by, "<", 200, "-", mx, my);
        fr.drawString(KINDS[wKind].replace('_', ' '), bx, by + 1, C_WARN); bx += fr.getStringWidth("POSSESSION ") + 2;
        bx = small(fr, bx, by, ">", 200, "+", mx, my) + 6;
        String vl = KINDS[wKind].equals("LENGTH") ? "max " + wValue + " m" : KINDS[wKind].equals("POSSESSION") ? "-" : wValue + " km/h";
        bx = small(fr, bx, by, "-", 201, "-", mx, my); fr.drawString(vl, bx, by + 1, C_TEXT); bx += 56; bx = small(fr, bx, by, "+", 201, "+", mx, my) + 6;
        bx = small(fr, bx, by, "-", 202, "-", mx, my); fr.drawString((KINDS[wKind].equals("SINGLE_LINE") ? "len " : "r ") + wRadius + " m", bx, by + 1, C_TEXT); bx += 50; bx = small(fr, bx, by, "+", 202, "+", mx, my) + 6;
        bx = small(fr, bx, by, "-", 203, "-", mx, my); fr.drawString(wMinutes == 0 ? "until out" : wMinutes + " min", bx, by + 1, C_TEXT); bx += 50; small(fr, bx, by, "+", 203, "+", mx, my);
        button(fr, x + 6, y + h - 14, "PLAN IT HERE (crew goes out now)", 8, KINDS[wKind] + ":" + wValue + ":" + wRadius + ":" + wMinutes + ":", C_GOOD, mx, my);
        fr.drawString("Single-line working runs from where you stand the way you face; the crossing keeper AUTHorises each train over", x + 200, y + h - 12, C_DIM);
    }

    // ---- COMPAT (#8) --------------------------------------------------------------------------------------------------
    private void compat(FontRenderer fr, int top, int mx, int my) {
        int ry = top;
        List<String[]> cs = rows("C");
        if (cs.isEmpty()) fr.drawString("Stand by a train and press CHECK (optionally type the line to check it against)", x + 6, ry, C_DIM);
        rowsTotal = cs.size();
        rowsVisible = Math.max(1, (y + h - top - 36) / ROW);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rowsTotal - rowsVisible)));
        for (int i = scroll; i < cs.size() && i < scroll + rowsVisible; i++) {
            String[] r = cs.get(i);
            String t = g(r, 1);
            int c = t.equals("P") ? C_BAD : t.equals("W") ? C_WARN : t.equals("O") ? C_GOOD : t.equals("V") ? (g(r, 2).startsWith("COMPATIBLE") ? C_GOOD : C_BAD) : t.equals("H") ? C_HEAD : C_TEXT;
            String pre = t.equals("P") ? "✖ " : t.equals("W") ? "! " : t.equals("O") ? "✔ " : "";
            fr.drawString(fr.trimStringToWidth(pre + g(r, 2), w - 14), x + 8, ry, c);
            ry += ROW;
        }
        commandLine(fr, y + h - 30, "line to check against (blank = the train's own line), ENTER = check", mx, my);
        button(fr, x + 6, y + h - 14, "CHECK THE NEAREST TRAIN", 7, "", C_GOOD, mx, my);
    }

    // ---- SPLIT / JOIN (#9) --------------------------------------------------------------------------------------------
    private void splitJoin(FontRenderer fr, int top, int mx, int my) {
        int ry = table(fr, top, "SP", new String[]{"#", "Train", "Splits at", "Rear cars", "Rear runs", "Rejoins at", "State", "Last"}, new int[]{0, 4, 16, 30, 38, 50, 62, 80},
                Math.max(3, (h - 90) / ROW), true, r -> g(r, 7).startsWith("FAULT") ? C_BAD : g(r, 7).startsWith("SPLIT") || g(r, 7).startsWith("JOIN") ? C_WARN : C_TEXT, "No through services that split and rejoin");
        int i = 0;
        for (String[] r : rows("SP")) { if (i >= scroll && i < scroll + rowsVisible) small(fr, x + w - 48, top + 12 + (i - scroll) * ROW - 1, "REMOVE", 24, g(r, 1), mx, my); i++; }
        String[] sj = first("SJ");
        fr.drawString("Joins in progress: " + g(sj, 1) + "   Uncoupling only at a platform, standing, brakes on: " + (g(sj, 2).equals("1") ? "YES" : "no (config)"), x + 6, ry + 4, C_DIM);
        commandLine(fr, y + h - 30, "new plan:  train ; split at station ; rear cars ; rear runs line ; rejoins at station   (ENTER)", mx, my);
        int bx = x + 6, by = y + h - 14;
        bx = small(fr, bx, by, "-", 204, "-", mx, my);
        fr.drawString(splitCars + " rear", bx, by + 1, C_TEXT); bx += 34;
        bx = small(fr, bx, by, "+", 204, "+", mx, my) + 4;
        bx = button(fr, bx, by, "SPLIT NEAREST TRAIN", 25, String.valueOf(splitCars), C_WARN, mx, my);
        button(fr, bx, by, "JOIN NEAREST TWO PORTIONS", 26, "", C_GOOD, mx, my);
    }

    // ---- YARD (#4) ----------------------------------------------------------------------------------------------------
    private void yard(FontRenderer fr, int top, int mx, int my) {
        String[] yn = first("YN"), yp = first("YP");
        if (yn == null) { fr.drawString("No Hump Yard Control Panel loaded", x + 6, top, C_DIM); return; }
        int bx = x + 6, ry = top;
        bx = small(fr, bx, ry, "<", 19, "-1", mx, my);
        fr.drawString("Yard " + g(yn, 1) + "/" + g(yn, 2) + "  " + g(yp, 1) + "  " + g(yp, 2), bx + 2, ry + 1, C_HEAD);
        small(fr, x + w - 20, ry, ">", 19, "1", mx, my);
        ry += 12;
        String dl = g(yp, 9);
        fr.drawString("Strategy " + g(yp, 3) + " · " + g(yp, 4) + " wagons · " + g(yp, 5) + " departures (" + g(yp, 6) + " ready) · " + g(yp, 7) + " unassigned · deadlock: " + dl, x + 6, ry, dl.equals("none") ? C_TEXT : C_BAD);
        ry += 12;
        for (String[] k : rows("YK")) { Gui.drawRect(x + 4, ry - 1, x + w - 6, ry + 10, 0xFF5A1414); fr.drawString(fr.trimStringToWidth(g(k, 1), w - 14), x + 8, ry + 1, 0xFFFFFFFF); ry += 13; }
        ry = table(fr, ry, "YD", new String[]{"#", "Departure", "Road", "Load", "Takes", "Deadline", "Cars", "On road", "Length m", "Engine", "Status"},
                new int[]{0, 4, 16, 23, 29, 46, 56, 63, 70, 82, 89}, 5, false, r -> g(r, 11).equals("READY") ? C_GOOD : g(r, 11).equals("LATE") ? C_BAD : C_WARN, "No departures booked - type one below");
        int i = 0;
        for (String[] r : rows("YD")) { if (i < 5) small(fr, x + w - 18, ry - (Math.min(5, rows("YD").size()) - i) * ROW - 1, "X", 22, g(r, 1), mx, my); i++; }
        ry += 2;
        ry = table(fr, ry, "YR", new String[]{"Road", "Reserved for", "When", ""}, new int[]{0, 8, 34, 54}, 3, false, r -> g(r, 4).isEmpty() ? C_TEXT : C_BAD, "No roads reserved");
        ry = table(fr, ry, "YE", new String[]{"Finish the job by", "Energy", "Time", ""}, new int[]{0, 30, 48, 64}, 2, false, r -> C_TEXT, "-");
        ry += 2;
        table(fr, ry, "YC", new String[]{"Wagon", "Destination", "Cargo", "Where", "Departure", "Due", "Plan"}, new int[]{0, 18, 34, 48, 56, 70, 78}, Math.max(1, (y + h - ry - 44) / ROW), true,
                r -> g(r, 5).equals("HELD") ? C_WARN : C_TEXT, "No wagons in the yard");
        commandLine(fr, y + h - 30, "book a departure:  name ; road 1-8 ; takes (Steelworks, item:coal, empty, *) ; minutes ; max cars ; max length m ; loading road   (ENTER)", mx, my);
        button(fr, x + 6, y + h - 14, "STRATEGY: " + g(yp, 3), 20, "", C_HEAD, mx, my);
    }

    // ---- VERSIONS (#40) -----------------------------------------------------------------------------------------------
    private void versions(FontRenderer fr, int top, int mx, int my) {
        int half = (w - 12) * 55 / 100;
        int sw = w;
        this.w = half;
        List<String[]> vs = rows("V");
        int ry = top;
        head(fr, ry, new String[]{"v", "Time", "Part", "Who", "Why", "Δ"}, new int[]{0, 6, 18, 32, 48, 92});
        ry += 12;
        rowsTotal = vs.size();
        rowsVisible = Math.max(1, (y + h - ry - 34) / ROW);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rowsTotal - rowsVisible)));
        int[] at = {0, 6, 18, 32, 48, 92};
        for (int i = scroll; i < vs.size() && i < scroll + rowsVisible; i++) {
            String[] r = vs.get(i);
            boolean sel = g(r, 7).equals("1");
            boolean over = mx >= x + 4 && mx < x + w - 4 && my >= ry - 1 && my < ry + ROW - 1;
            Gui.drawRect(x + 4, ry - 1, x + w - 6, ry + ROW - 1, sel ? 0x553A6FD8 : over ? 0x33FFFFFF : (i % 2 == 0 ? C_ROW : 0));
            hit(x + 4, ry - 1, w - 10, ROW, 13, g(r, 1));
            int c = g(r, 5).startsWith("ROLLBACK") ? C_WARN : g(r, 5).startsWith("PROMOTED") ? C_GOOD : C_TEXT;
            for (int k = 0; k < 6; k++) cell(fr, g(r, k + 1), at, k, ry, k == 2 ? C_HEAD : c);
            ry += ROW;
        }
        this.w = sw;
        // the diff
        int dx = x + 6 + half + 6, dy = top;
        String[] vsel = first("VS");
        fr.drawString(vsel == null ? "DIFF" : "DIFF v" + g(vsel, 1) + " (" + g(vsel, 2) + ") against the version before", dx, dy, C_HEAD);
        dy += 12;
        for (String[] d : rows("VD")) {
            if (dy > y + h - 60) break;
            String s = g(d, 1);
            fr.drawString(fr.trimStringToWidth(s, x + w - dx - 8), dx, dy, s.startsWith("+") ? C_GOOD : s.startsWith("-") ? C_BAD : C_WARN);
            dy += ROW - 1;
        }
        List<String[]> bad = rows("VX");
        dy = Math.max(dy + 4, y + h - 56);
        fr.drawString(bad.isEmpty() ? "Compatibility: this version fits the railway as it is now" : "Compatibility: " + bad.size() + " part(s) no longer fit:", dx, dy, bad.isEmpty() ? C_GOOD : C_BAD);
        dy += 11;
        for (int i = 0; i < Math.min(3, bad.size()); i++) { fr.drawString(fr.trimStringToWidth(" - " + g(bad.get(i), 1), x + w - dx - 8), dx, dy, C_WARN); dy += 10; }
        int by = y + h - 14, bx = x + 6;
        bx = small(fr, bx, by, "<", 205, "-", mx, my);
        fr.drawString(CATS[verCat], bx + 1, by + 1, C_HEAD); bx += 62;
        bx = small(fr, bx, by, ">", 205, "+", mx, my) + 4;
        bx = button(fr, bx, by, "COMMIT NOW", 11, CATS[verCat] + ":committed from the console", C_GOOD, mx, my);
        if (vsel != null) {
            bx = button(fr, bx, by, "ROLLBACK " + g(vsel, 2) + " TO v" + g(vsel, 1), 12, g(vsel, 2) + ":" + g(vsel, 1), C_WARN, mx, my);
            button(fr, bx, by, "FORCE (skip what doesn't fit)", 12, g(vsel, 2) + ":" + g(vsel, 1) + ":force", C_BAD, mx, my);
        }
        commandLine(fr, y + h - 30, "commit message for " + CATS[verCat] + " (ENTER commits)", mx, my);
    }

    // ---- DISPATCH (#46) -----------------------------------------------------------------------------------------------
    private void dispatch(FontRenderer fr, int top, int mx, int my) {
        String[] gc = first("GC");
        int ry = top;
        if (gc != null && g(gc, 1).equals("0")) { fr.drawString("AI dispatchers are switched off in irextras_planning.cfg (aiDispatchers)", x + 6, ry, C_BAD); ry += 12; }
        List<String[]> rs = rows("G");
        ry = table(fr, ry, "G", new String[]{"Region", "Goal", "", "Prio", "Area", "Lines", "Max hold", "Decisions", "Overruled"}, new int[]{0, 13, 31, 36, 41, 58, 72, 80, 89}, 6, false,
                r -> g(r, 3).equals("ON") ? C_GOOD : C_DIM, "No regions - type one below (it centres where you stand; radius 0 = whole railway)");
        int i = 0;
        for (String[] r : rs) {
            if (i >= 6) break;
            int yy = ry - (Math.min(6, rs.size()) - i) * ROW - 1;
            int bx = x + w - 112;
            bx = small(fr, bx, yy, g(r, 3).equals("ON") ? "OFF" : "ON", 15, g(r, 1), mx, my);
            bx = small(fr, bx, yy, "GOAL", 16, g(r, 1), mx, my);
            bx = small(fr, bx, yy, "PRIO", 17, g(r, 1), mx, my);
            small(fr, bx, yy, "DEL", 18, g(r, 1), mx, my);
            if (mx >= x + 4 && mx < x + w - 116 && my >= yy && my < yy + ROW) hint = g(r, 11);
            i++;
        }
        ry += 4;
        fr.drawString("DECISIONS (newest first) - every one explained; the supervisor's overrulings indented", x + 6, ry, C_HEAD);
        ry += 12;
        List<String[]> ls = rows("GL");
        rowsTotal = ls.size();
        rowsVisible = Math.max(1, (y + h - ry - 34) / ROW);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rowsTotal - rowsVisible)));
        for (int k = scroll; k < ls.size() && k < scroll + rowsVisible; k++) {
            String s = g(ls.get(k), 1);
            fr.drawString(fr.trimStringToWidth(s, w - 14), x + 8, ry, s.contains("overruled") ? C_WARN : s.contains("HOLD") ? C_HEAD : C_TEXT);
            ry += ROW;
        }
        commandLine(fr, y + h - 30, "new region:  name ; PUNCTUALITY | FREIGHT | MAINTENANCE ; radius (0 = all) ; lines (comma, blank = all)   (ENTER)", mx, my);
        if (!hint.isEmpty()) fr.drawString(fr.trimStringToWidth(hint, w - 12), x + 6, y + h - 12, C_DIM);
    }

    // ---- TRENDS (#42) -------------------------------------------------------------------------------------------------
    private static final String[] TV = {"Last 24 hours", "Hour of day (week)", "Days", "Weeks", "Seasons", "Compare periods"};
    private static final String[] TT = {"HH", "HP", "HD", "HW", "HS", "HC"};

    private void trends(FontRenderer fr, int top, int mx, int my) {
        String[] hn = first("HN");
        int bx = x + 6, ry = top;
        bx = small(fr, bx, ry, "<", 207, "-", mx, my);
        fr.drawString(TV[trendView], bx + 2, ry + 1, C_HEAD);
        bx += 110;
        small(fr, bx, ry, ">", 207, "+", mx, my);
        if (hn != null) fr.drawString("worst hour of day " + g(hn, 1) + " (" + g(hn, 2) + " s avg delay) · season " + g(hn, 3) + " · kept " + g(hn, 4) + " h, " + g(hn, 5) + " weeks, " + g(hn, 6) + " seasons", bx + 18, ry + 1, C_DIM);
        ry += 14;
        if (trendView == 5) {
            table(fr, ry, "HC", new String[]{"Comparison", "Measure", "This", "That", "Change"}, new int[]{0, 28, 50, 64, 78}, rowsVisible, true, r -> good(g(r, 6)), "Not enough history yet");
            return;
        }
        String[] cols = {"Period", "Deps", "Punct %", "Delay s", "Worst s", "Faults", "kWh", "Regen", "Freight", "km", "Moving %"};
        int[] at = {0, 20, 28, 37, 45, 53, 60, 68, 76, 84, 92};
        int ny = table(fr, ry, TT[trendView], cols, at, Math.max(3, rowsVisible - 8), true, r -> num(g(r, 3)) >= 90 ? C_GOOD : num(g(r, 3)) >= 75 || g(r, 3).equals("-") ? C_TEXT : C_WARN,
                "No history yet - the analytics count as trains run");
        // a bar chart of punctuality and departures for the shown rows
        List<String[]> rs = rows(TT[trendView]);
        int chartTop = ny + 4, chartH = y + h - chartTop - 6;
        if (chartH < 24 || rs.isEmpty()) return;
        int n = Math.min(rs.size(), 48);
        int bw = Math.max(2, (w - 16) / n);
        double maxDep = 1;
        for (int i = 0; i < n; i++) maxDep = Math.max(maxDep, num(g(rs.get(i), 2)));
        Gui.drawRect(x + 6, chartTop, x + w - 6, chartTop + chartH, 0xFF0A0812);
        for (int i = 0; i < n; i++) {
            String[] r = rs.get(n - 1 - i);
            int cx = x + 8 + i * bw;
            int dh = (int) (num(g(r, 2)) / maxDep * (chartH - 4));
            Gui.drawRect(cx, chartTop + chartH - 2 - dh, cx + bw - 1, chartTop + chartH - 2, 0x665BCEFA);
            if (!g(r, 3).equals("-")) {
                int py = chartTop + chartH - 2 - (int) (num(g(r, 3)) / 100 * (chartH - 4));
                Gui.drawRect(cx, py, cx + bw - 1, py + 2, num(g(r, 3)) >= 90 ? C_GOOD : num(g(r, 3)) >= 75 ? C_WARN : C_BAD);
            }
            if (mx >= cx && mx < cx + bw && my >= chartTop && my < chartTop + chartH) hint = g(r, 1) + ": " + g(r, 2) + " departures, " + g(r, 3) + " % punctual, " + g(r, 4) + " s delay, " + g(r, 6) + " faults, " + g(r, 7) + " kWh";
        }
        fr.drawString("bars: departures · line: punctuality %", x + 10, chartTop + 2, C_DIM);
    }

    // ---- POSSESSION (batch 3 #45): planned maintenance possessions and their gangs' task lists --------------------------
    private void possessions(FontRenderer fr, int top, int mx, int my) {
        int ry = top;
        List<String[]> pp = rows("PP");
        fr.drawString("Maintenance possessions: the planner books the work due in the zone, prices the timetable cost on the twin, gangs work it while it is IN FORCE", x + 6, ry, C_DIM);
        ry += 12;
        if (pp.isEmpty()) { fr.drawString("No planned possession - plan one below (centred where you stand)", x + 6, ry, C_DIM); ry += ROW; }
        String[] cols = {"#", "Name", "Phase", "Window", "Tasks", "Done", "Blocked", "Work / left min", "+delay s", "-deps", "Lines"};
        int[] at = {0, 4, 18, 28, 46, 51, 56, 62, 76, 83, 88};
        List<String[]> lines = new ArrayList<>();
        for (String[] p : pp) {
            lines.add(p);
            for (String[] t : rows("PT")) if (g(t, 1).equals(g(p, 1))) lines.add(t);
        }
        rowsTotal = lines.size();
        rowsVisible = Math.max(1, (y + h - ry - 44) / ROW);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rowsTotal - rowsVisible)));
        head(fr, ry, cols, at);
        ry += 12;
        int i = 0;
        for (int k = scroll; k < lines.size() && i < rowsVisible; k++, i++) {
            String[] r = lines.get(k);
            if (r[0].equals("PP")) {
                Gui.drawRect(x + 4, ry - 1, x + w - 6, ry + ROW - 1, 0x33FFFFFF);
                for (int j = 0; j < cols.length; j++) cell(fr, g(r, j + 1), at, j, ry, j == 2 && g(r, 3).equals("ACTIVE") ? C_WARN : j == 6 && !g(r, 7).equals("0") ? C_BAD : C_TEXT);
                if (!g(r, 3).equals("CLEARED") && !g(r, 3).equals("GONE")) {
                    int bx = x + w - 8 - fr.getStringWidth("RESCAN") - fr.getStringWidth("HAND BACK") - 14;
                    bx = small(fr, bx, ry - 1, "RESCAN", 62, g(r, 1), mx, my);
                    if (!g(r, 3).equals("REMOVING")) small(fr, bx, ry - 1, "HAND BACK", 9, g(r, 1), mx, my);
                }
            } else {
                String st = g(r, 4);
                fr.drawString(fr.trimStringToWidth("   " + g(r, 2) + "  " + g(r, 3) + "  - " + st + (st.equals("WORKING") ? " " + g(r, 5) + "%" : "") + (g(r, 6).isEmpty() ? "" : "  (" + g(r, 6) + ")"), w - 16), x + 6, ry,
                        st.equals("done") ? C_GOOD : st.equals("BLOCKED") ? C_BAD : st.equals("WORKING") ? C_WARN : st.equals("deferred") ? C_DIM : C_TEXT);
            }
            ry += ROW;
        }
        int by = y + h - 28, bx = x + 6;
        fr.drawString("ZONE:", bx, by + 2, C_HEAD);
        bx += 30;
        bx = small(fr, bx, by, "-", 202, "-", mx, my); fr.drawString("r " + wRadius + " m", bx, by + 1, C_TEXT); bx += 50; bx = small(fr, bx, by, "+", 202, "+", mx, my) + 6;
        int mins = Math.max(5, wMinutes == 0 ? 30 : wMinutes);
        bx = small(fr, bx, by, "-", 203, "-", mx, my); fr.drawString(mins + " min", bx, by + 1, C_TEXT); bx += 50; small(fr, bx, by, "+", 203, "+", mx, my);
        int b2 = button(fr, x + 6, y + h - 14, "PLAN POSSESSION HERE NOW", 60, wRadius + ":" + mins, C_GOOD, mx, my);
        button(fr, b2, y + h - 14, "PLAN AT THE BEST WINDOW (least traffic)", 61, wRadius + ":" + mins, C_HEAD, mx, my);
    }
}
