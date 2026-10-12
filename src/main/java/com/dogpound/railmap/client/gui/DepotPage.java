package com.dogpound.railmap.client.gui;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.client.ClientRailway;
import com.dogpound.railmap.depot.PacketDepotOp;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import java.util.ArrayList;
import java.util.List;

/**
 * The Control Center's DEPOT tab (her ChatGPT list section 8 + #81-85), live from the server (PacketRailState.depot,
 * see depot.DepotConsole): FLEET - every vehicle's role, condition, exam plan, remaining life, servicing need and speed
 * restriction (select one: TO DEPOT / RETURN / SPARE / STOP / EXAM); JOBS - the workshop job cards; LINES - fleet
 * targets per line (+ / -) and ATTENDED lines; CREW - the roster and duty log; DOWNLOADS - saved diagnostics;
 * STATIONS - accessibility ratings and facility costs.
 */
final class DepotPage {
    private static final String[] PAGES = {"Fleet", "Jobs", "Lines", "Crew", "Downloads", "Stations", "Steam", "Traction"};
    private static final int ROW = 11;
    private static final int C_TEXT = 0xFFE8ECF0, C_DIM = 0xFF8A94A0, C_HEAD = 0xFF5BCEFA, C_GOOD = 0xFF7CF29A, C_WARN = 0xFFFFC44D, C_BAD = 0xFFFF5252,
            C_ROW = 0x22FFFFFF, C_SEL = 0x553A6FD8;

    private int page, scroll, rowsTotal, rowsVisible, selDl = -1;
    private String sel = "";
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
        else if (a == 1) sel = arg;
        else if (a == 2) selDl = Integer.parseInt(arg);
        else if (a >= 1000) RailMap.NETWORK.sendToServer(new PacketDepotOp(a - 1000, arg));
    }

    private void hit(int bx, int by, int bw, int bh, int action, String arg) { hits.add(new int[]{bx, by, bw, bh, action}); args.add(arg); }

    private List<String[]> rows(String tag) {
        List<String[]> o = new ArrayList<>();
        for (String[] r : ClientRailway.get().depot) if (r.length > 0 && r[0].equals(tag)) o.add(r);
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
            case 0 -> fleet(fr, top, mx, my);
            case 1 -> jobs(fr, top);
            case 2 -> lines(fr, top, mx, my);
            case 3 -> crew(fr, top);
            case 4 -> downloads(fr, top);
            case 6 -> steam(fr, top);
            case 7 -> traction(fr, top);
            default -> stations(fr, top);
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

    private <T> List<T> page(List<T> all) {
        rowsTotal = all.size();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rowsTotal - rowsVisible)));
        return all.subList(Math.min(scroll, all.size()), Math.min(all.size(), scroll + rowsVisible));
    }

    private void stripe(int ry, int i, boolean s) {
        if (i % 2 == 0) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, C_ROW);
        if (s) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, C_SEL);
    }

    private static int cond(String c) { return c.equals("FAILED") ? C_BAD : c.equals("CRITICAL") ? C_BAD : c.equals("DEGRADED") ? C_WARN : C_GOOD; }

    private void fleet(FontRenderer fr, int top, int mx, int my) {
        int bx = x + w - 8;
        for (String[] b : new String[][]{{"EXAM", "1008"}, {"STOP", "1004"}, {"SPARE", "1003"}, {"RETURN", "1002"}, {"TO DEPOT", "1001"}}) {
            bx -= fr.getStringWidth(b[0]) + 13;
            button(fr, bx, top - 16 + 2, b[0], Integer.parseInt(b[1]), sel, mx, my);
        }
        String[] cols = {"Vehicle", "Role", "Worst", "Condition", "Wheel/Brk/Eng/Brg/Elec %", "Plan", "Fails first", "km", "h", "Svc", "Restriction"};
        int[] at = {0, 17, 24, 30, 39, 55, 61, 75, 80, 85, 89};
        head(fr, top, cols, at);
        int ry = top + 13, i = 0;
        List<String[]> rs = rows("U");
        if (rs.isEmpty()) fr.drawString("No vehicles seen yet - they report once they are loaded", x + 6, ry, C_DIM);
        for (String[] r : page(rs)) {
            stripe(ry, i++, r[1].equals(sel));
            boolean failed = r[11].equals("1");
            cell(fr, (r[13].equals("1") ? "" : "  ") + r[2], at, 0, ry, failed ? C_BAD : C_TEXT);
            cell(fr, r[3], at, 1, ry, r[3].equals("SERVICE") ? C_GOOD : r[3].equals("STOPPED") ? C_BAD : C_WARN);
            cell(fr, r[4] + "%", at, 2, ry, cond(r[5]));
            cell(fr, failed ? "FAILED IN SVC" : r[5], at, 3, ry, failed ? C_BAD : cond(r[5]));
            cell(fr, r[12].replace(",", " / "), at, 4, ry, C_DIM);
            int plan = Integer.parseInt(r[6]);
            cell(fr, plan + "%", at, 5, ry, plan >= 100 ? C_BAD : plan >= 80 ? C_WARN : C_TEXT);
            cell(fr, r[7].isEmpty() ? "-" : r[7], at, 6, ry, C_DIM);
            cell(fr, r[8], at, 7, ry, C_DIM);
            cell(fr, r[9], at, 8, ry, C_DIM);
            cell(fr, r[10].isEmpty() ? "" : r[10] + "%", at, 9, ry, r[10].isEmpty() ? C_DIM : Integer.parseInt(r[10]) >= 60 ? C_WARN : C_DIM);
            cell(fr, r[14], at, 10, ry, C_BAD);
            hit(x + 2, ry - 1, w - 8, ROW, 1, r[1]);
            ry += ROW;
        }
    }

    private void jobs(FontRenderer fr, int top) {
        String[] cols = {"#", "Vehicle", "Job", "Assigned", "State", "Work"};
        int[] at = {0, 6, 30, 52, 70, 84};
        head(fr, top, cols, at);
        int ry = top + 13, i = 0;
        List<String[]> rs = rows("J");
        if (rs.isEmpty()) fr.drawString("No job cards - raise them at a Depot Control Office (or EXAM on the Fleet page)", x + 6, ry, C_DIM);
        for (String[] r : page(rs)) {
            stripe(ry, i++, false);
            cell(fr, r[1], at, 0, ry, C_DIM);
            cell(fr, r[2], at, 1, ry, C_TEXT);
            cell(fr, r[3], at, 2, ry, C_TEXT);
            cell(fr, r[4].isEmpty() ? "-" : r[4], at, 3, ry, C_DIM);
            cell(fr, r[5], at, 4, ry, r[5].equals("DONE") ? C_GOOD : r[5].equals("IN WORK") ? C_WARN : r[5].equals("CANCELLED") ? C_DIM : C_TEXT);
            cell(fr, r[5].equals("IN WORK") ? r[6] + "%" : "", at, 5, ry, C_DIM);
            ry += ROW;
        }
    }

    private void lines(FontRenderer fr, int top, int mx, int my) {
        String[] cols = {"Line", "Target", "Running", "", "Attended"};
        int[] at = {0, 30, 42, 54, 74};
        head(fr, top, cols, at);
        int ry = top + 13, i = 0;
        List<String[]> rs = rows("L");
        if (rs.isEmpty()) fr.drawString("No lines", x + 6, ry, C_DIM);
        for (String[] r : page(rs)) {
            stripe(ry, i++, false);
            int t = Integer.parseInt(r[2]), run = Integer.parseInt(r[3]);
            cell(fr, r[1], at, 0, ry, C_TEXT);
            cell(fr, t == 0 ? "not managed" : String.valueOf(t), at, 1, ry, t == 0 ? C_DIM : C_TEXT);
            cell(fr, String.valueOf(run), at, 2, ry, t > 0 && run < t ? C_BAD : C_GOOD);
            int bx = x + 4 + 54 * (w - 10) / 100;
            bx += button(fr, bx, ry - 1, "-", 1006, r[1], mx, my);
            bx += button(fr, bx, ry - 1, "+", 1005, r[1], mx, my);
            cell(fr, r[4].equals("1") ? "YES - driver needed" : "no", at, 4, ry, r[4].equals("1") ? C_WARN : C_DIM);
            button(fr, x + w - 60, ry - 1, "TOGGLE", 1007, r[1], mx, my);
            ry += ROW + 1;
        }
    }

    private void crew(FontRenderer fr, int top) {
        String[] cols = {"Driver", "State", "Duty h", "Since break", "Train", "Passed", "Routes"};
        int[] at = {0, 18, 26, 34, 44, 64, 72};
        head(fr, top, cols, at);
        int ry = top + 13, i = 0;
        List<String[]> rs = rows("C");
        if (rs.isEmpty()) fr.drawString("No drivers - sign on at a Crew Booking-On Desk", x + 6, ry, C_DIM);
        for (String[] r : rs) {
            stripe(ry, i++, false);
            cell(fr, r[1], at, 0, ry, C_TEXT);
            cell(fr, r[2], at, 1, ry, r[2].equals("ON") ? C_GOOD : r[2].equals("BREAK") ? C_WARN : C_DIM);
            cell(fr, r[3], at, 2, ry, C_TEXT);
            cell(fr, r[4], at, 3, ry, C_TEXT);
            cell(fr, r[5].isEmpty() ? "-" : r[5], at, 4, ry, C_DIM);
            cell(fr, r[6], at, 5, ry, C_DIM);
            cell(fr, r[7], at, 6, ry, C_DIM);
            ry += ROW;
        }
        ry += 4;
        List<String[]> ops = rows("O");                                           // operators online, with their career rank badge
        if (!ops.isEmpty()) {
            fr.drawString("Operators online", x + 6, ry, C_HEAD);
            ry += ROW;
            StringBuilder b = new StringBuilder();
            for (String[] r : ops) b.append(b.length() == 0 ? "" : "§7 · ").append(r.length > 2 && !r[2].isEmpty() ? "§6[" + r[2] + "]§r " : "§f").append(r[1]);
            for (String l : fr.listFormattedStringToWidth(b.toString(), w - 12)) { fr.drawString(l, x + 6, ry, C_TEXT); ry += ROW; }
            ry += 4;
        }
        ry = crewPeople(fr, ry);                                                 // batch 5 #7 #9: crew NPCs + revenue protection
        fr.drawString("Duty log", x + 6, ry, C_HEAD);
        ry += ROW;
        for (String[] r : rows("H")) { if (ry > y + h - 12) break; fr.drawString(fr.trimStringToWidth(r[1], w - 12), x + 6, ry, C_DIM); ry += ROW; }
    }

    /** crew people (N rows: name, role, phase, doing, diagram, fatigue %, train / beat) and revenue protection (R rows) */
    private int crewPeople(FontRenderer fr, int ry) {
        List<String[]> ns = rows("N");
        if (!ns.isEmpty()) {
            fr.drawString("Crew on the ground", x + 6, ry, C_HEAD);
            ry += ROW;
            int[] at = {0, 16, 26, 40, 70, 78, 86};
            head(fr, ry - 2, new String[]{"Name", "Role", "Now", "Doing", "Diagram", "Tired", "Train / beat"}, at);
            ry += 11;
            int i = 0;
            for (String[] r : ns) {
                if (ry > y + h - 40) break;
                stripe(ry, i++, false);
                int fat = Integer.parseInt(r[6]);
                cell(fr, r[1], at, 0, ry, C_TEXT);
                cell(fr, r[2], at, 1, ry, C_DIM);
                cell(fr, r[3], at, 2, ry, r[3].startsWith("OFF") ? C_DIM : C_GOOD);
                cell(fr, r[4], at, 3, ry, C_DIM);
                cell(fr, r[5], at, 4, ry, C_TEXT);
                cell(fr, fat + "%", at, 5, ry, fat > 100 ? C_BAD : fat > 80 ? C_WARN : C_GOOD);
                cell(fr, r[7], at, 6, ry, C_DIM);
                ry += ROW;
            }
            ry += 4;
        }
        for (String[] r : rows("R")) {
            if (ry > y + h - 24) break;
            fr.drawString(fr.trimStringToWidth(r[1], w - 12), x + 6, ry, r[1].startsWith("Revenue") ? C_HEAD : r[1].startsWith("#") ? C_DIM : C_TEXT);
            ry += ROW;
        }
        return ry + 4;
    }

    private void downloads(FontRenderer fr, int top) {
        List<String[]> rs = rows("D");
        if (rs.isEmpty()) { fr.drawString("No diagnostic downloads - DOWNLOAD at a Depot Control Office", x + 6, top + 2, C_DIM); return; }
        int lx = x + 4, ry = top;
        String[] show = rs.get(0);
        for (String[] r : rs) {
            boolean on = selDl == Integer.parseInt(r[1]) || selDl < 0 && r == rs.get(0);
            if (on) show = r;
            Gui.drawRect(lx, ry - 1, lx + 120, ry + ROW - 1, on ? C_SEL : C_ROW);
            fr.drawString(fr.trimStringToWidth("#" + r[1] + " " + r[2] + " " + r[3] + "m", 116), lx + 2, ry, C_TEXT);
            hit(lx, ry - 1, 120, ROW, 2, r[1]);
            ry += ROW;
        }
        int tx = x + 130;
        ry = top;
        for (String line : show[4].split("\n")) { fr.drawString(fr.trimStringToWidth(line, w - 140), tx, ry, line.startsWith("ACTIVE") || line.startsWith("PREDICTED") ? C_WARN : C_TEXT); ry += ROW; }
    }

    private void stations(FontRenderer fr, int top) {
        String[] cols = {"Station", "Access", "Rating", "Step-free", "Lifts", "Tactile", "Facilities", "Cost/day", "Income yday"};
        int[] at = {0, 22, 30, 38, 48, 56, 64, 76, 88};
        head(fr, top, cols, at);
        int ry = top + 13, i = 0;
        List<String[]> rs = rows("S");
        if (rs.isEmpty()) fr.drawString("No stations audited yet (once a minute, loaded stations)", x + 6, ry, C_DIM);
        for (String[] r : page(rs)) {
            stripe(ry, i++, false);
            cell(fr, r[1], at, 0, ry, C_TEXT);
            cell(fr, r[2], at, 1, ry, r[2].equals("A") || r[2].equals("B") ? C_GOOD : r[2].equals("C") ? C_WARN : C_BAD);
            cell(fr, r[3], at, 2, ry, C_TEXT);
            cell(fr, r[4].equals("1") ? "yes" : "NO", at, 3, ry, r[4].equals("1") ? C_GOOD : C_BAD);
            cell(fr, r[5], at, 4, ry, C_DIM);
            cell(fr, r[6], at, 5, ry, C_DIM);
            cell(fr, r[7], at, 6, ry, C_DIM);
            cell(fr, r[8], at, 7, ry, C_WARN);
            cell(fr, r[9], at, 8, ry, C_GOOD);
            ry += ROW;
        }
    }

    // ---- batch 5 #2 + #6: the steam servicing depot and battery / hydrogen traction ---------------------------------------
    private void steam(FontRenderer fr, int top) {
        String[] cols = {"Engine", "Boiler", "Water", "Scale mm", "ppm", "Ash pan", "Coal +", "h steam", "Washouts", "State"};
        int[] at = {0, 22, 30, 37, 46, 52, 60, 67, 75, 83};
        head(fr, top, cols, at);
        int ry = top + 13, i = 0;
        List<String[]> rs = new ArrayList<>(rows("SB"));
        for (String[] p : rows("SP")) rs.add(p);
        for (String[] p : rows("SL")) rs.add(p);
        if (rs.isEmpty()) fr.drawString("No steam engines or servicing plant loaded", x + 6, ry, C_DIM);
        for (String[] r : page(rs)) {
            stripe(ry, i++, false);
            if (r[0].equals("SB")) {
                for (int c = 0; c < 9; c++) cell(fr, r[c + 1], at, c, ry, c == 0 ? C_TEXT : C_DIM);
                cell(fr, r[10], at, 9, ry, r[10].equals("OK") ? C_GOOD : r[10].startsWith("TUBES") ? C_BAD : C_WARN);
            } else if (r[0].equals("SP")) {
                fr.drawString(fr.trimStringToWidth("\u00a7b" + r[1] + "\u00a77 " + r[2] + " \u00b7 " + r[3], w - 16), x + 6, ry, C_TEXT);
            } else fr.drawString(fr.trimStringToWidth(r[1], w - 16), x + 6, ry, C_DIM);
            ry += ROW;
        }
    }

    private void traction(FontRenderer fr, int top) {
        String[] cols = {"Unit", "Type", "SoC", "kWh", "H2 kg", "Range km", "Source", "kW", "Regen kW", "Out/regen/charged kWh"};
        int[] at = {0, 20, 30, 36, 43, 52, 60, 69, 75, 83};
        head(fr, top, cols, at);
        int ry = top + 13, i = 0;
        List<String[]> rs = new ArrayList<>(rows("TU"));
        for (String[] p : rows("TP")) rs.add(p);
        if (rs.isEmpty()) fr.drawString("No battery / hydrogen units or charging / hydrogen plant (tag a loco or rebuild it at the Loco Design Workshop)", x + 6, ry, C_DIM);
        for (String[] r : page(rs)) {
            stripe(ry, i++, false);
            if (r[0].equals("TU")) {
                int soc = 0;
                try { soc = Integer.parseInt(r[3].replace("%", "")); } catch (NumberFormatException ignored) { }
                for (int c = 0; c < 10; c++) cell(fr, r[c + 1], at, c, ry, c == 0 ? C_TEXT : c == 2 ? (soc < 15 ? C_BAD : soc < 30 ? C_WARN : C_GOOD) : c == 6 && r[7].equals("FLAT") ? C_BAD : C_DIM);
            } else fr.drawString(fr.trimStringToWidth("\u00a7b" + r[1] + "\u00a77 " + r[2] + " \u00b7 " + r[3], w - 16), x + 6, ry, C_TEXT);
            ry += ROW;
        }
    }
}
