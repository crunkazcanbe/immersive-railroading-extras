package com.dogpound.railmap.client.gui;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.client.ClientRailway;
import com.dogpound.railmap.ohle.PacketOhleOp;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import java.util.ArrayList;
import java.util.List;

/**
 * The Control Center's OLE/WX tab (her ChatGPT list #31-33, #63-66), live from the server (PacketRailState.env, see
 * ohle.OhleConsole). SECTIONS: every OHLE section, its feeding and state, the feeder breakers (CLOSE / OPEN) and the
 * isolators (OPERATE). PANTOGRAPHS: every electric loco's pantograph, breaker, contact force, carbon, arcs, notes.
 * ENERGY: the tariff now and the energy ledger per operating period (NEW PERIOD, PEAK SHIFT). WEATHER: the weather
 * stations' readings, every weather speed restriction (also on the map), snow work orders and the logs.
 */
final class OhlePage {
    private static final String[] PAGES = {"Sections", "Pantographs", "Energy", "Weather", "Feeding", "Faults", "Recording", "Drainage", "Wx Trains"};
    private static final int ROW = 11;
    private static final int C_TEXT = 0xFFE8ECF0, C_DIM = 0xFF8A94A0, C_HEAD = 0xFF5BCEFA, C_GOOD = 0xFF7CF29A, C_WARN = 0xFFFFC44D, C_BAD = 0xFFFF5252, C_ROW = 0x22FFFFFF;

    private int page, scroll, rowsTotal, rowsVisible;
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
        else if (a >= 1000) RailMap.NETWORK.sendToServer(new PacketOhleOp(a - 1000, arg));
    }

    private void hit(int bx, int by, int bw, int bh, int action, String arg) { hits.add(new int[]{bx, by, bw, bh, action}); args.add(arg); }

    private static List<String[]> rows(String tag) {
        List<String[]> o = new ArrayList<>();
        for (String[] r : ClientRailway.get().env) if (r.length > 0 && r[0].equals(tag)) o.add(r);
        return o;
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
        int top = y + 31;
        rowsVisible = Math.max(1, (y + h - top - 14) / ROW);
        switch (page) {
            case 0 -> sections(fr, top, mx, my);
            case 1 -> pans(fr, top);
            case 2 -> energy(fr, top, mx, my);
            case 3 -> weather(fr, top);
            case 4 -> feeding(fr, top, mx, my);
            case 5 -> faults(fr, top, mx, my);
            case 6 -> recording(fr, top, mx, my);
            case 7 -> drainage(fr, top, mx, my);
            default -> wxtrains(fr, top, mx, my);
        }
        if (rowsTotal > rowsVisible) PrideFrame.scrollbar(x + w - 4, top + 12, rowsVisible * ROW, scroll, rowsVisible, rowsTotal);
    }

    private int button(FontRenderer fr, int bx, int by, String label, int action, String arg, int mx, int my) {
        int bw = fr.getStringWidth(label) + 8;
        boolean over = mx >= bx && mx < bx + bw && my >= by && my < by + 10;
        Gui.drawRect(bx, by, bx + bw, by + 10, over ? 0xFF5A3A80 : 0xFF3A2440);
        fr.drawString(label, bx + 4, by + 1, 0xFFFFFFFF);
        hit(bx, by, bw, 10, action, arg);
        return bw + 2;
    }

    private void head(FontRenderer fr, int top, String[] cols, int[] at) {
        Gui.drawRect(x + 2, top, x + w - 6, top + 11, 0x33FFFFFF);
        for (int i = 0; i < cols.length; i++) fr.drawString(cols[i], x + 4 + at[i] * (w - 10) / 100, top + 2, C_HEAD);
    }

    private void cell(FontRenderer fr, String s, int[] at, int i, int ry, int col) {
        int cx = x + 4 + at[i] * (w - 10) / 100, nx = i + 1 < at.length ? x + 4 + at[i + 1] * (w - 10) / 100 : x + w - 8;
        fr.drawString(fr.trimStringToWidth(s == null ? "" : s, Math.max(4, nx - cx - 3)), cx, ry, col);
    }

    /** one long list of mixed rows (section headers + tables), scrolled as a whole */
    private interface Line { void draw(int ry, int i); }

    private void list(FontRenderer fr, int top, List<Line> lines) {
        rowsTotal = lines.size();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rowsTotal - rowsVisible)));
        int ry = top + 2;
        for (int i = scroll; i < Math.min(lines.size(), scroll + rowsVisible + 1); i++) { lines.get(i).draw(ry, i); ry += ROW; }
    }

    private Line title(FontRenderer fr, String t) { return (ry, i) -> { Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, 0x33FFFFFF); fr.drawString(t, x + 4, ry + 1, C_HEAD); }; }

    private Line text(FontRenderer fr, String t, int col) { return (ry, i) -> fr.drawString(fr.trimStringToWidth(t, w - 14), x + 6, ry + 1, col); }

    // ---- pages ------------------------------------------------------------------------------------------------------
    private void sections(FontRenderer fr, int top, int mx, int my) {
        List<Line> l = new ArrayList<>();
        int[] sa = {0, 18, 30, 37, 44, 52, 66};
        l.add(title(fr, "SECTIONS          state        wire m   feeders   SI/NS   tension       why"));
        List<String[]> ss = rows("S");
        if (ss.isEmpty()) l.add(text(fr, "No OHLE section known yet - put a Track Feeder (Wire) within 4 blocks of the contact wire, or a Motorised Isolator / Tensioner on it", C_DIM));
        for (String[] r : ss) l.add((ry, i) -> {
            if (i % 2 == 0) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, C_ROW);
            int c = r[2].equals("LIVE") ? C_BAD : r[2].equals("EARTHED") ? C_WARN : r[2].equals("FAULT") ? C_BAD : C_DIM;   // UK mimic: red = live
            cell(fr, r[1], sa, 0, ry + 1, C_TEXT); cell(fr, r[2], sa, 1, ry + 1, c); cell(fr, r[3], sa, 2, ry + 1, C_DIM); cell(fr, r[4], sa, 3, ry + 1, C_DIM);
            cell(fr, r[5] + "/" + r[6], sa, 4, ry + 1, C_DIM); cell(fr, r[7] + " kN", sa, 5, ry + 1, C_TEXT); cell(fr, r[8], sa, 6, ry + 1, C_WARN);
        });
        l.add(title(fr, "FEEDER BREAKERS"));
        String[] cbn = {"CLOSED", "TRIPPED - reclosing", "LOCKOUT", "OPEN"};
        for (String[] r : rows("F")) l.add((ry, i) -> {
            int st = Integer.parseInt(r[3]);
            fr.drawString("Feeder " + r[1] + " (" + r[6] + ")", x + 6, ry + 1, C_TEXT);
            fr.drawString(cbn[st], x + 6 + (w - 10) * 22 / 100, ry + 1, st == 0 ? C_BAD : st == 2 ? C_WARN : C_DIM);
            fr.drawString(r[7].equals("1") ? "supply ok" : "no supply", x + 6 + (w - 10) * 40 / 100, ry + 1, r[7].equals("1") ? C_GOOD : C_DIM);
            fr.drawString(fr.trimStringToWidth(r[4] + " trips " + r[5], (w - 10) * 30 / 100), x + 6 + (w - 10) * 50 / 100, ry + 1, C_DIM);
            int bx = x + w - 8 - fr.getStringWidth("CLOSE") - fr.getStringWidth("OPEN") - 20;
            bx += button(fr, bx, ry, "CLOSE", 1001, r[2], mx, my);
            button(fr, bx, ry, "OPEN", 1002, r[2], mx, my);
        });
        l.add(title(fr, "ISOLATORS"));
        for (String[] r : rows("I")) l.add((ry, i) -> {
            fr.drawString("Isolator " + r[1], x + 6, ry + 1, C_TEXT);
            fr.drawString(fr.trimStringToWidth(r[8], (w - 10) * 55 / 100), x + 6 + (w - 10) * 22 / 100, ry + 1, r[5].equals("1") ? C_WARN : r[3].equals("1") ? C_BAD : C_GOOD);
            button(fr, x + w - 8 - fr.getStringWidth("OPERATE") - 10, ry, "OPERATE", 1003, r[2], mx, my);
        });
        l.add(title(fr, "OHLE LOG"));
        for (String[] r : rows("L")) l.add(text(fr, r[1], r[1].contains("TRIP") || r[1].contains("FAULT") ? C_BAD : C_DIM));
        list(fr, top - 16, l);
    }

    private void pans(FontRenderer fr, int top) {
        String[] cols = {"Loco", "Pan", "VCB", "Mode", "Force", "Carbon", "Arcs", "kWh in", "regen", "resist.", "cost", "Note"};
        int[] at = {0, 16, 23, 28, 34, 40, 46, 51, 58, 64, 70, 75};
        head(fr, top - 16, cols, at);
        List<String[]> rs = rows("P");
        rowsTotal = rs.size();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rowsTotal - rowsVisible)));
        int ry = top - 3, i = 0;
        if (rs.isEmpty()) fr.drawString("No electric locos have run under the wire yet", x + 6, ry, C_DIM);
        for (String[] r : rs.subList(Math.min(scroll, rs.size()), Math.min(rs.size(), scroll + rowsVisible))) {
            if (i++ % 2 == 0) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, C_ROW);
            int arcs = Integer.parseInt(r[7]);
            cell(fr, r[1], at, 0, ry, C_TEXT);
            cell(fr, r[2], at, 1, ry, r[2].equals("BROKEN") ? C_BAD : r[2].equals("UP") ? C_GOOD : C_DIM);
            cell(fr, r[3].equals("1") ? "closed" : "open", at, 2, ry, r[3].equals("1") ? C_GOOD : C_DIM);
            cell(fr, r[4], at, 3, ry, C_DIM);
            cell(fr, r[5] + " N", at, 4, ry, C_TEXT);
            cell(fr, r[6] + "%", at, 5, ry, Integer.parseInt(r[6]) < 20 ? C_BAD : C_TEXT);
            cell(fr, r[7], at, 6, ry, arcs >= 5 ? C_BAD : arcs > 0 ? C_WARN : C_DIM);
            cell(fr, r[9], at, 7, ry, C_TEXT);
            cell(fr, r[10], at, 8, ry, C_GOOD);
            cell(fr, r[11], at, 9, ry, C_WARN);
            cell(fr, r[12], at, 10, ry, C_TEXT);
            cell(fr, r[8], at, 11, ry, r[8].contains("ARC") || r[8].contains("NO ") ? C_BAD : C_DIM);
            ry += ROW;
        }
    }

    private void energy(FontRenderer fr, int top, int mx, int my) {
        List<String[]> n = rows("N");
        String[] now = n.isEmpty() ? new String[]{"N", "--", "", "0", "0"} : n.get(0);
        fr.drawString("Tariff " + now[1] + ": " + now[2] + " " + now[3] + " coins/MWh · peak shift " + (now[4].equals("1") ? "ON" : "off"), x + 6, top - 14, now[2].equals("peak") ? C_BAD : now[2].equals("shoulder") ? C_WARN : C_GOOD);
        int bx = x + w - 8 - fr.getStringWidth("NEW PERIOD") - fr.getStringWidth("PEAK SHIFT") - 22;
        bx += button(fr, bx, top - 15, "NEW PERIOD", 1004, "", mx, my);
        button(fr, bx, top - 15, "PEAK SHIFT", 1005, "", mx, my);
        String[] cols = {"Period", "kWh", "kWh/h", "regen", "resistors", "cost", "peak %", "departures", "held", "avg hold s"};
        int[] at = {0, 12, 22, 32, 42, 54, 63, 72, 84, 91};
        head(fr, top, cols, at);
        List<String[]> rs = rows("E");
        rowsTotal = rs.size();
        int ry = top + 13, i = 0;
        if (rs.isEmpty()) fr.drawString("No electric running measured yet", x + 6, ry, C_DIM);
        for (String[] r : rs) {
            if (i++ % 2 == 0) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, C_ROW);
            boolean cur = r[11].equals("1");
            cell(fr, r[1] + (cur ? " (now)" : ""), at, 0, ry, cur ? C_HEAD : C_TEXT);
            for (int k = 2; k <= 10; k++) cell(fr, r[k], at, k - 1, ry, k == 4 ? C_GOOD : k == 5 ? C_WARN : C_TEXT);
            ry += ROW;
        }
        fr.drawString("Mark a new period after changing a timetable, then compare: kWh/h, regen share and cost per period.", x + 6, ry + 4, C_DIM);
    }

    private void weather(FontRenderer fr, int top) {
        List<Line> l = new ArrayList<>();
        List<String[]> hh = rows("H");
        String ev = hh.isEmpty() ? "" : hh.get(0)[1];
        boolean w2 = !hh.isEmpty() && hh.get(0)[2].equals("1");
        l.add(title(fr, "WEATHER STATIONS" + (w2 ? "  (Weather2)" : "") + (ev.isEmpty() ? "" : "   " + ev)));
        List<String[]> ws = rows("W");
        if (ws.isEmpty()) l.add(text(fr, "No weather station feeding the control center - place a Weather Station by the line (12 V)", C_DIM));
        for (String[] r : ws) l.add(text(fr, (r[2].equals("1") ? "" : "[NO 12 V] ") + r[1] + ": air " + r[3] + " °C, rail " + r[4] + " °C (SFT " + r[5] + "), wind " + r[6] + " m/s, " + r[7]
                + ", vis " + r[8] + " m " + r[9] + ", snow " + r[10] + " mm, wire ice " + r[11] + " mm, flood " + r[12] + " mm, track " + r[13] + "%", r[2].equals("1") ? C_TEXT : C_DIM));
        l.add(title(fr, "WEATHER SPEED RESTRICTIONS (on the map too)"));
        List<String[]> rr = rows("R");
        if (rr.isEmpty()) l.add(text(fr, "None in force", C_GOOD));
        for (String[] r : rr) l.add(text(fr, "x " + r[1] + ".." + (Integer.parseInt(r[1]) + Integer.parseInt(r[3])) + ", z " + r[2] + ".." + (Integer.parseInt(r[2]) + Integer.parseInt(r[3])) + ": "
                + (r[4].equals("0") ? "LINE CLOSED" : r[4] + " km/h") + " - " + r[5] + (r[6].equals("1") ? "" : " (lineside signalling only)"), r[4].equals("0") ? C_BAD : C_WARN));
        l.add(title(fr, "SNOW WORK ORDERS (by track priority)"));
        List<String[]> os = rows("O");
        if (os.isEmpty()) l.add(text(fr, "No snow on the railway", C_GOOD));
        for (String[] r : os) l.add(text(fr, r[1] + ": " + r[2] + " mm, " + r[3] + " trains/h, priority " + r[4] + (r[5].isEmpty() ? " - waiting for a gang" : " - gang " + r[5] + " working"), r[5].isEmpty() ? C_WARN : C_GOOD));
        l.add(title(fr, "WEATHER LOG"));
        for (String[] r : rows("X")) l.add(text(fr, r[1], C_DIM));
        list(fr, top - 16, l);
    }

    // ---- batch 3 (#36-50, infra.InfraConsole) ---------------------------------------------------------------------------
    private static String g(String[] r, int i) { return r != null && i < r.length && r[i] != null ? r[i] : ""; }

    /** neutral sections (#36), parallel feeding (#37), wayside energy storage (#38) */
    private void feeding(FontRenderer fr, int top, int mx, int my) {
        List<Line> l = new ArrayList<>();
        l.add(title(fr, "NEUTRAL SECTIONS (#36) - also on the Rail Map (magenta; a cross = phase break) and the cab DMI"));
        List<String[]> ns = rows("NS");
        if (ns.isEmpty()) l.add(text(fr, "No neutral section on a known OHLE section - put a Neutral Section block in the contact wire between two feeds", C_DIM));
        for (String[] r : ns) l.add(text(fr, g(r, 1) + "," + g(r, 2) + "," + g(r, 3) + "  " + g(r, 6) + " · A " + g(r, 4) + " · B " + g(r, 5) + " · coasted " + g(r, 7) + " · drew power " + g(r, 8) + " · " + g(r, 9),
                g(r, 8).equals("0") ? (g(r, 6).startsWith("PHASE") ? C_WARN : C_TEXT) : C_BAD));
        l.add(title(fr, "PARALLEL FEEDING (#37)   section        feeder     kW     share   volts    net   trains  spread%  circ%"));
        int[] at = {0, 14, 26, 34, 41, 48, 56, 62, 70, 78, 86};
        List<String[]> ls = rows("LS");
        if (ls.isEmpty()) l.add(text(fr, "No Track Feeders", C_DIM));
        for (String[] r : ls) l.add((ry, i) -> {
            if (i % 2 == 0) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, C_ROW);
            int c = g(r, 10).startsWith("MIXED") || g(r, 10).startsWith("TAP") ? C_WARN : C_TEXT;
            for (int k = 1; k <= 10; k++) cell(fr, g(r, k), at, k - 1, ry + 1, k == 1 ? C_HEAD : k == 10 ? c : C_TEXT);
        });
        l.add(title(fr, "WAYSIDE ENERGY STORAGE (#38)"));
        List<String[]> es = rows("ES");
        if (es.isEmpty()) l.add(text(fr, "No energy store - place a Wayside ESS touching a traction network cable (600 V-3 kV DC or 13.8 kV AC)", C_DIM));
        for (String[] r : es) {
            l.add((ry, i) -> {
                boolean bad = g(r, 11).startsWith("TRIP") || g(r, 11).startsWith("NO");
                fr.drawString(fr.trimStringToWidth(g(r, 1) + " " + g(r, 2) + " " + g(r, 3) + " · SOC " + g(r, 4) + "% of " + g(r, 5) + " kWh · SOH " + g(r, 6) + "% · " + g(r, 7) + " C · in " + g(r, 8) + " kW out " + g(r, 9)
                        + " kW · " + g(r, 10) + " cycles · " + g(r, 11), w - 110), x + 6, ry + 1, bad ? C_BAD : C_TEXT);
                int bx = x + w - 8 - fr.getStringWidth("MODE") - fr.getStringWidth("RESET") - 20;
                bx += button(fr, bx, ry, "MODE", 1022, g(r, 14) + ":s_mode", mx, my);
                button(fr, bx, ry, "RESET", 1022, g(r, 14) + ":reset", mx, my);
            });
            l.add(text(fr, "     PCS supply: " + g(r, 12) + " · energy in/out " + g(r, 13) + " kWh", C_DIM));
        }
        list(fr, top - 16, l);
    }

    /** OHLE fault locator (#39) and relay calibration (#40) */
    private void faults(FontRenderer fr, int top, int mx, int my) {
        List<Line> l = new ArrayList<>();
        l.add((ry, i) -> { Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, 0x33FFFFFF); fr.drawString("OHLE FAULT LOCATOR (#39)  - distance to fault from the relay's impedance; crews from the Fault Locator panel", x + 4, ry + 1, C_HEAD);
            button(fr, x + w - 8 - fr.getStringWidth("ACK ALL") - 10, ry, "ACK ALL", 1021, "", mx, my); });
        List<String[]> fl = rows("FL");
        if (fl.isEmpty()) l.add(text(fr, "No OHLE feeder has tripped on a fault yet", C_GOOD));
        for (String[] r : fl) {
            l.add((ry, i) -> {
                boolean fresh = g(r, 15).equals("0");
                fr.drawString(fr.trimStringToWidth("#" + g(r, 1) + " " + g(r, 2) + " ago · " + g(r, 4) + " · feeder " + g(r, 3) + " · single " + g(r, 6) + " m · double " + g(r, 7) + " m · true " + g(r, 8) + " m · |Z| " + g(r, 9)
                        + " (R " + g(r, 10) + " X " + g(r, 11) + ") · " + g(r, 12) + " kA", w - 90), x + 6, ry + 1, fresh ? C_BAD : C_TEXT);
                if (g(r, 13).equals("-") || g(r, 13).equals("DONE")) button(fr, x + w - 8 - fr.getStringWidth("DISPATCH") - 10, ry, "DISPATCH", 1020, g(r, 1) + ":0", mx, my);
            });
            l.add(text(fr, "     crew: " + g(r, 13) + (g(r, 14).isEmpty() ? "" : " - " + g(r, 14)), g(r, 13).startsWith("WAIT") ? C_WARN : g(r, 13).equals("DONE") ? C_GOOD : C_DIM));
        }
        l.add(title(fr, "RELAY CALIBRATION (#40)   IED        pick-up %   time %   calibrated (d ago)   due in (d)   last test   tests   cals"));
        int[] at = {0, 16, 28, 38, 54, 66, 78, 86};
        List<String[]> rc = rows("RC");
        if (rc.isEmpty()) l.add(text(fr, "No Protection IEDs", C_DIM));
        for (String[] r : rc) l.add((ry, i) -> {
            if (i % 2 == 0) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, C_ROW);
            double pe = num(g(r, 2)), te = num(g(r, 3)), due = num(g(r, 5));
            for (int k = 1; k <= 8; k++) cell(fr, g(r, k), at, k - 1, ry + 1, k == 2 && Math.abs(pe) > 5 || k == 3 && Math.abs(te) > 5 || k == 5 && due < 0 && !g(r, 5).equals("-") ? C_BAD : k == 6 && g(r, 6).equals("FAIL") ? C_BAD : C_TEXT);
        });
        l.add(title(fr, "TEST CERTIFICATES"));
        for (String[] r : rows("RCC")) l.add(text(fr, g(r, 1), g(r, 1).contains("FAIL") ? C_BAD : C_DIM));
        l.add(title(fr, "INFRASTRUCTURE LOG"));
        for (String[] r : rows("IL")) l.add(text(fr, g(r, 1), C_DIM));
        list(fr, top - 16, l);
    }

    private static double num(String s) { try { return Double.parseDouble(s); } catch (RuntimeException e) { return 0; } }

    /** OHLE recording runs (#44) with the newest run's trace */
    private void recording(FontRenderer fr, int top, int mx, int my) {
        List<Line> l = new ArrayList<>();
        List<String[]> runs = rows("OR"), tr = rows("OT");
        l.add(title(fr, "OHLE RECORDING (#44) - IR stock tagged OHLE / CATENARY / OLR measures the wire as it runs"));
        if (!tr.isEmpty()) {
            l.add((ry, i) -> {
                int cx = x + 6, cw = w - 20, ch = ROW * 5 - 4, cy = ry;
                Gui.drawRect(cx, cy, cx + cw, cy + ch, 0xFF0A0712);
                // limits: height 4.7 / 6.2 m on a 4.0-7.0 m scale, stagger 0.4 m on 0-0.8
                int y47 = cy + ch - (int) ((4.7 - 4.0) / 3.0 * ch), y62 = cy + ch - (int) ((6.2 - 4.0) / 3.0 * ch), ys = cy + ch - (int) (0.4 / 0.8 * ch);
                Gui.drawRect(cx, y47, cx + cw, y47 + 1, 0x60FF5252); Gui.drawRect(cx, y62, cx + cw, y62 + 1, 0x60FF5252); Gui.drawRect(cx, ys, cx + cw, ys + 1, 0x60FFC44D);
                for (int k = 0; k < tr.size(); k++) {
                    int px = cx + (int) ((double) k / Math.max(1, tr.size() - 1) * (cw - 2));
                    double hh = num(g(tr.get(k), 1)), sg = num(g(tr.get(k), 2)), we = num(g(tr.get(k), 3));
                    int py = cy + ch - (int) (Math.max(0, Math.min(3, hh - 4.0)) / 3.0 * ch), sy = cy + ch - (int) (Math.min(0.8, sg) / 0.8 * ch), wy = cy + ch - (int) (Math.min(1, we) * ch);
                    Gui.drawRect(px, py - 1, px + 2, py + 1, hh < 4.7 || hh > 6.2 ? C_BAD : C_GOOD);
                    Gui.drawRect(px, sy, px + 2, sy + 1, sg > 0.4 ? C_WARN : 0xFF5BCEFA);
                    if (we > 0.05) Gui.drawRect(px, wy, px + 2, wy + 1, 0xFFE070F0);
                }
                fr.drawString("height (green)  stagger (blue)  wear (magenta)  limits red / amber", cx + 4, cy + 2, C_DIM);
            });
            for (int k = 0; k < 4; k++) l.add((ry, i) -> { });
        }
        String[] cols = {"Run", "Car", "Limits", "State", "m", "Height m", "Stagger", "Wear %", "Exceed", "km/h"};
        int[] at = {0, 6, 22, 34, 44, 50, 62, 70, 78, 86};
        l.add((ry, i) -> { Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, 0x33FFFFFF); for (int k = 0; k < cols.length; k++) cell(fr, cols[k], at, k, ry + 1, C_HEAD); });
        if (runs.isEmpty()) l.add(text(fr, "No recording runs yet - tag a car OHLE (IR's tag tool) and run it under the wire", C_DIM));
        for (int n = 0; n < runs.size(); n++) {
            String[] r = runs.get(n);
            boolean first = n == 0;
            l.add((ry, i) -> {
                if (i % 2 == 0) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, C_ROW);
                for (int k = 1; k <= 10; k++) cell(fr, g(r, k), at, k - 1, ry + 1, k == 9 && num(g(r, 9)) > 0 ? C_WARN : k == 4 && g(r, 4).equals("RECORDING") ? C_GOOD : C_TEXT);
                if (first && num(g(r, 9)) > 0) button(fr, x + w - 8 - fr.getStringWidth("ORDERS") - 10, ry, "ORDERS", 1023, g(r, 1), mx, my);
            });
        }
        l.add(title(fr, "EXCEEDANCES (newest first)"));
        for (String[] r : rows("OX")) l.add(text(fr, "run " + g(r, 1) + "  " + g(r, 2) + " " + g(r, 3) + " at " + g(r, 4), g(r, 2).contains("WIRE") ? C_BAD : C_WARN));
        list(fr, top - 16, l);
    }

    /** snowploughs and weather trains (IR Extras #4, weather.WxTrains): forecast per depot desk, runs, the trains, the log */
    private void wxtrains(FontRenderer fr, int top, int mx, int my) {
        List<Line> l = new ArrayList<>();
        l.add(title(fr, "WEATHER TRAIN DEPOTS - forecast risk (%) and why; PLAN a run, AUTO plans + calls trains itself"));
        List<String[]> wf = rows("WF");
        if (wf.isEmpty()) l.add(text(fr, "No Weather Train Depot desk loaded (or railway weather is off)", C_DIM));
        for (String[] r : wf) {
            l.add((ry, i) -> {
                fr.drawString(fr.trimStringToWidth("Depot " + g(r, 1) + "  air " + g(r, 10) + " C  rain " + (g(r, 11).equals("0") ? "now" : g(r, 11).equals("-") ? "not soon" : "in " + g(r, 11) + " min")
                        + "  ·  SNOW " + g(r, 2) + "%  LEAVES " + g(r, 3) + "%  WIRE ICE " + g(r, 4) + "%", w - 250), x + 6, ry + 1, num(g(r, 2)) >= 60 || num(g(r, 3)) >= 60 || num(g(r, 4)) >= 60 ? C_WARN : C_TEXT);
                int bx = x + w - 8 - fr.getStringWidth("PLOUGH") - fr.getStringWidth("SANDITE") - fr.getStringWidth("DE-ICE") - fr.getStringWidth(g(r, 8).equals("1") ? "AUTO ON" : "AUTO OFF") - 44;
                bx += button(fr, bx, ry, "PLOUGH", 1040, g(r, 9), mx, my);
                bx += button(fr, bx, ry, "SANDITE", 1041, g(r, 9), mx, my);
                bx += button(fr, bx, ry, "DE-ICE", 1042, g(r, 9), mx, my);
                button(fr, bx, ry, g(r, 8).equals("1") ? "AUTO ON" : "AUTO OFF", 1045, g(r, 9), mx, my);
            });
            l.add(text(fr, "     snow: " + (g(r, 5).isEmpty() ? "-" : g(r, 5)) + " · leaves: " + (g(r, 6).isEmpty() ? "-" : g(r, 6)) + " · ice: " + (g(r, 7).isEmpty() ? "-" : g(r, 7)), C_DIM));
        }
        String[] cols = {"Run", "Kind", "State", "Train", "Cells", "Min", "Why"};
        int[] at = {0, 6, 16, 27, 46, 54, 60};
        l.add((ry, i) -> { Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, 0x33FFFFFF); for (int k = 0; k < cols.length; k++) cell(fr, cols[k], at, k, ry + 1, C_HEAD); });
        List<String[]> wt = rows("WT");
        if (wt.isEmpty()) l.add(text(fr, "No runs yet", C_DIM));
        for (String[] r : wt) l.add((ry, i) -> {
            if (i % 2 == 0) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, C_ROW);
            String st = g(r, 3);
            int c = st.equals("RUNNING") ? C_GOOD : st.equals("CALLED") || st.equals("PLANNED") ? C_WARN : C_DIM;
            for (int k = 1; k <= 7; k++) cell(fr, k == 7 ? g(r, 7) + (g(r, 8).isEmpty() ? "" : " [" + g(r, 8) + "]") : g(r, k), at, k - 1, ry + 1, k == 3 ? c : C_TEXT);
            if (st.equals("PLANNED")) { int bx = x + w - 8 - fr.getStringWidth("DISPATCH") - fr.getStringWidth("CANCEL") - 20; bx += button(fr, bx, ry, "DISPATCH", 1043, g(r, 1), mx, my); button(fr, bx, ry, "CANCEL", 1044, g(r, 1), mx, my); }
            else if (st.equals("CALLED") || st.equals("RUNNING")) button(fr, x + w - 8 - fr.getStringWidth("CANCEL") - 10, ry, "CANCEL", 1044, g(r, 1), mx, my);
        });
        l.add(title(fr, "WEATHER TRAINS - IR stock tagged PLOUGH / SNOWPLOW, SANDITE / RHTT, DEICER (IR tag tool), or a loco with the Snowplough Attachment"));
        List<String[]> wv = rows("WV");
        if (wv.isEmpty()) l.add(text(fr, "None loaded", C_DIM));
        for (String[] r : wv) l.add(text(fr, g(r, 2) + "  " + g(r, 1) + "  (" + g(r, 3) + ")  " + g(r, 4) + " km/h at " + g(r, 5) + (g(r, 6).isEmpty() ? "" : "  on run " + g(r, 6)), num(g(r, 4)) > 5 ? C_GOOD : C_TEXT));
        l.add(title(fr, "WEATHER TRAIN LOG"));
        for (String[] r : rows("WL")) l.add(text(fr, g(r, 1), C_DIM));
        list(fr, top - 16, l);
    }

    /** the drainage network (#43): cells, flood sensors, pump stations, culverts */
    private void drainage(FontRenderer fr, int top, int mx, int my) {
        List<Line> l = new ArrayList<>();
        l.add(title(fr, "DRAINAGE CELLS (#43)   cell          water mm   over rail   risk   culverts      pumps run   sensor"));
        int[] at = {0, 18, 28, 38, 46, 62, 74};
        List<String[]> dc = rows("DC");
        if (dc.isEmpty()) l.add(text(fr, "Dry - no water, culverts, pumps or sensors anywhere (or railway weather is off)", C_GOOD));
        for (String[] r : dc) l.add((ry, i) -> {
            if (i % 2 == 0) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, C_ROW);
            int c = num(g(r, 3)) > 0 ? C_BAD : num(g(r, 2)) > 60 ? C_WARN : C_TEXT;
            for (int k = 1; k <= 7; k++) cell(fr, g(r, k) + (k == 7 && !g(r, 8).isEmpty() ? " · TSR" : ""), at, k - 1, ry + 1, k <= 3 ? c : C_TEXT);
        });
        l.add(title(fr, "FLOOD SENSORS"));
        List<String[]> ds = rows("DS");
        if (ds.isEmpty()) l.add(text(fr, "No flood sensor - place a Flood Warning Outstation by the line (12 V)", C_DIM));
        for (String[] r : ds) l.add((ry, i) -> {
            boolean bad = g(r, 4).equals("ALARM") || g(r, 4).equals("FLOOD");
            fr.drawString(fr.trimStringToWidth((g(r, 7).equals("1") ? "" : "[NO 12 V] ") + g(r, 1) + ": " + g(r, 4) + " · water " + g(r, 2) + " mm · rising " + g(r, 3) + " mm/min · rail head in " + g(r, 5) + " min " + g(r, 6), w - 120),
                    x + 6, ry + 1, bad ? C_BAD : g(r, 4).equals("ALERT") ? C_WARN : C_TEXT);
            int bx = x + w - 8 - fr.getStringWidth("TSR") - fr.getStringWidth("PUMPS") - 20;
            bx += button(fr, bx, ry, "TSR", 1022, g(r, 8) + ":tsr", mx, my);
            button(fr, bx, ry, "PUMPS", 1022, g(r, 8) + ":pumps", mx, my);
        });
        l.add(title(fr, "PUMP STATIONS"));
        for (String[] r : rows("DP")) l.add(text(fr, g(r, 1) + ": " + g(r, 2) + " · P1 " + g(r, 3) + " · P2 " + g(r, 4) + " · running min " + g(r, 5) + " · " + g(r, 6), g(r, 3).equals("FAULT") || g(r, 4).equals("FAULT") ? C_BAD : C_TEXT));
        l.add(title(fr, "CULVERTS"));
        for (String[] r : rows("DCV")) l.add(text(fr, g(r, 1) + ": screen " + g(r, 2) + "% blocked · " + g(r, 3) + " mm / 5 s", num(g(r, 2)) > 50 ? C_WARN : C_TEXT));
        list(fr, top - 16, l);
    }
}
