package com.dogpound.railmap.client.gui;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.biz.PacketBizOp;
import com.dogpound.railmap.client.ClientRailway;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import java.util.ArrayList;
import java.util.List;

/**
 * The Control Center's WORLD tab (her "next 48" #11-15, #17, #18, #32-38, #47), live from the server
 * (PacketRailState.biz, see biz.BizConsole). One sub-page per system: construction projects, capacity slots, towns,
 * passenger journeys, intermodal (ports, terminals, truck appointments), cargo custody, environment, research,
 * insurance, regulator, deals, stock exchange, trophies, public service and the scenario. Buttons act through
 * PacketBizOp (directors act for their own company; operators can close auctions and settle claims early).
 */
final class BizPage {
    private static final String[] PAGES = {"Projects", "Slots", "Towns", "Journeys", "Ports", "Cargo", "Env", "Research", "Insure", "Regulator", "Deals", "Stocks", "Trophies", "PSO", "Scenario"};
    private static final int ROW = 11;
    private static final int C_TEXT = 0xFFE8ECF0, C_DIM = 0xFF8A94A0, C_HEAD = 0xFF5BCEFA, C_GOOD = 0xFF7CF29A, C_WARN = 0xFFFFC44D, C_BAD = 0xFFFF5252, C_ROW = 0x22FFFFFF;
    private static final String[] TECHS = {"SURVEYING", "HEAVY_CIVILS", "ELECTRIFICATION", "CAPACITY_PLANNING", "CONTAINERISATION", "PORT_LOGISTICS", "GREEN_TECH", "SAFETY_MGMT", "CORPORATE_FINANCE", "CORPORATE_LAW"};
    private static final String[] MEASURES = {"NOISE_BARRIER", "SOLAR_DEPOT", "DPF", "TREES", "WATER"};

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
        else if (a >= 1000) RailMap.NETWORK.sendToServer(new PacketBizOp(a - 1000, arg));
    }

    private void hit(int bx, int by, int bw, int bh, int action, String arg) { hits.add(new int[]{bx, by, bw, bh, action}); args.add(arg); }

    private static List<String[]> rows(String tag) {
        List<String[]> o = new ArrayList<>();
        for (String[] r : ClientRailway.get().biz) if (r.length > 0 && r[0].equals(tag)) o.add(r);
        return o;
    }

    private static String g(String[] r, int i) { return i < r.length ? r[i] : ""; }

    void draw(FontRenderer fr, int x, int y, int w, int h, int mx, int my) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        hits.clear();
        args.clear();
        int cx = x + 4, cy = y + 3;
        for (int i = 0; i < PAGES.length; i++) {
            int cw = fr.getStringWidth(PAGES[i]) + 8;
            if (cx + cw > x + w - 4) { cx = x + 4; cy += 12; }
            boolean on = i == page, over = mx >= cx && mx < cx + cw && my >= cy && my < cy + 11;
            Gui.drawRect(cx, cy, cx + cw, cy + 11, on ? 0xFF3A2E5C : over ? 0xFF2A2240 : 0xFF181226);
            Gui.drawRect(cx, cy + 10, cx + cw, cy + 11, PrideFrame.RAINBOW[i % PrideFrame.RAINBOW.length]);
            fr.drawString(PAGES[i], cx + 4, cy + 2, on ? 0xFFFFFFFF : C_DIM);
            hit(cx, cy, cw, 11, 100 + i, "");
            cx += cw + 2;
        }
        int top = cy + 16;
        rowsVisible = Math.max(1, (y + h - top - 4) / ROW);
        List<Line> l = new ArrayList<>();
        switch (page) {
            case 0 -> projects(fr, l, mx, my);
            case 1 -> slots(fr, l, mx, my);
            case 2 -> towns(fr, l);
            case 3 -> journeys(fr, l);
            case 4 -> ports(fr, l);
            case 5 -> cargo(fr, l);
            case 6 -> env(fr, l, mx, my);
            case 7 -> research(fr, l, mx, my);
            case 8 -> insure(fr, l, mx, my);
            case 9 -> regulator(fr, l, mx, my);
            case 10 -> deals(fr, l, mx, my);
            case 11 -> stocks(fr, l, mx, my);
            case 12 -> trophies(fr, l);
            case 13 -> pso(fr, l);
            default -> scenario(fr, l);
        }
        list(top, l);
        if (rowsTotal > rowsVisible) PrideFrame.scrollbar(x + w - 4, top, rowsVisible * ROW, scroll, rowsVisible, rowsTotal);
    }

    // ---- drawing helpers ------------------------------------------------------------------------------------------
    private interface Line { void draw(int ry, int i); }

    private void list(int top, List<Line> lines) {
        rowsTotal = lines.size();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rowsTotal - rowsVisible)));
        int ry = top;
        for (int i = scroll; i < Math.min(lines.size(), scroll + rowsVisible); i++) { lines.get(i).draw(ry, i); ry += ROW; }
    }

    private Line title(FontRenderer fr, String t) { return (ry, i) -> { Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, 0x33FFFFFF); fr.drawString(fr.trimStringToWidth(t, w - 12), x + 4, ry + 1, C_HEAD); }; }
    private Line text(FontRenderer fr, String t, int col) { return (ry, i) -> fr.drawString(fr.trimStringToWidth(t, w - 14), x + 6, ry + 1, col); }

    /** a table row: cells at % columns, optional buttons at the right */
    private Line row(FontRenderer fr, String[] cells, int[] at, int[] cols, int mx, int my, Object... buttons) {
        return (ry, i) -> {
            if (i % 2 == 0) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, C_ROW);
            int bw = 0;
            for (int k = 0; k + 2 < buttons.length; k += 3) bw += fr.getStringWidth((String) buttons[k]) + 10;
            int right = x + w - 8 - bw;
            for (int k = 0; k < cells.length && k < at.length; k++) {
                int cx = x + 4 + at[k] * (w - 10) / 100, nx = k + 1 < at.length ? x + 4 + at[k + 1] * (w - 10) / 100 : right;
                fr.drawString(fr.trimStringToWidth(cells[k] == null ? "" : cells[k], Math.max(4, Math.min(nx, right) - cx - 3)), cx, ry + 1, cols[Math.min(k, cols.length - 1)]);
            }
            int bx = right;
            for (int k = 0; k + 2 < buttons.length; k += 3) bx += button(fr, bx, ry, (String) buttons[k], (Integer) buttons[k + 1], (String) buttons[k + 2], mx, my);
        };
    }

    private int button(FontRenderer fr, int bx, int by, String label, int action, String arg, int mx, int my) {
        int bw = fr.getStringWidth(label) + 8;
        boolean over = mx >= bx && mx < bx + bw && my >= by && my < by + 10;
        Gui.drawRect(bx, by, bx + bw, by + 10, over ? 0xFF5A3A80 : 0xFF3A2440);
        fr.drawString(label, bx + 4, by + 1, 0xFFFFFFFF);
        hit(bx, by, bw, 10, action, arg);
        return bw + 2;
    }

    private Line buttons(FontRenderer fr, String lead, int mx, int my, Object... b) {
        return (ry, i) -> {
            fr.drawString(lead, x + 6, ry + 1, C_DIM);
            int bx = x + 10 + fr.getStringWidth(lead);
            for (int k = 0; k + 2 < b.length; k += 3) bx += button(fr, bx, ry, (String) b[k], (Integer) b[k + 1], (String) b[k + 2], mx, my);
        };
    }

    private static int[] c(int... v) { return v; }

    // ---- pages -------------------------------------------------------------------------------------------------------
    private void projects(FontRenderer fr, List<Line> l, int mx, int my) {
        l.add(title(fr, "CONSTRUCTION PROJECTS  (survey > earthworks > foundations > track > electrical > testing > opening)"));
        List<String[]> rs = rows("pm");
        if (rs.isEmpty()) l.add(text(fr, "No construction site office loaded. Place one, pick a project, turn AUTHORISE, deliver by train, press BUILD.", C_DIM));
        int[] at = {0, 16, 27, 33, 39, 51, 62, 72, 83};
        l.add(row(fr, new String[]{"Project", "Stage", "Done", "Crews", "Plant", "Budget", "Spent", "Delays h", "Forecast / held"}, at, c(C_HEAD), mx, my));
        for (String[] r : rs) l.add(row(fr, new String[]{r[1], r[2], r[3] + "%", r[4], r[5], r[6], r[7], r[9], r[8].isEmpty() ? r[10] : "HELD: " + r[8]}, at,
                c(C_TEXT, r[8].isEmpty() ? C_GOOD : C_BAD, C_TEXT, C_TEXT, C_DIM, C_GOOD, C_WARN, C_WARN, r[8].isEmpty() ? C_DIM : C_BAD), mx, my));
        l.add(text(fr, "Materials are built in stage by stage as trains deliver them; POSSESSION holds the nearby lines' driverless trains for the track stages.", C_DIM));
    }

    private void slots(FontRenderer fr, List<Line> l, int mx, int my) {
        l.add(buttons(fr, "CAPACITY AUCTIONS - sealed bids close daily.", mx, my, "CLOSE NOW", 1001, ""));
        l.add(title(fr, "SLOTS HELD"));
        int[] at = {0, 6, 13, 38, 50, 56, 66, 78, 86};
        List<String[]> sl = rows("sl");
        if (sl.isEmpty()) l.add(text(fr, "No capacity sold yet - bid at a Regulator Office (capacity register) or /irextras biz slot bid", C_DIM));
        for (String[] r : sl) l.add(row(fr, new String[]{"#" + r[1], r[2], r[3], r[4], "x" + r[5], r[6], r[7], "used " + r[8], r[10] + (r[9].equals("0") ? "" : " unauth " + r[9])}, at,
                c(C_DIM, C_TEXT, C_TEXT, C_HEAD, C_TEXT, C_GOOD, C_DIM, C_DIM, r[10].equals("ACTIVE") ? C_GOOD : C_DIM), mx, my, "GIVE BACK", 1008, r[1]));
        l.add(title(fr, "SEALED BIDS"));
        for (String[] r : rows("sb")) l.add(row(fr, new String[]{"#" + r[1], r[2], r[3], r[4], "x" + r[5], r[6], r[7], r[8]}, at,
                c(C_DIM, C_TEXT, C_TEXT, C_HEAD, C_TEXT, C_GOOD, C_DIM, r[8].equals("WON") ? C_GOOD : r[8].equals("LOST") ? C_BAD : C_WARN), mx, my, "WITHDRAW", 1008, r[1]));
        l.add(title(fr, "DECISIONS (why each bid won or lost)"));
        for (String[] r : rows("sd")) l.add(text(fr, r[1], r[1].contains("LOST") ? C_WARN : C_GOOD));
        l.add(title(fr, "RESOURCES"));
        for (String[] r : rows("sr")) l.add(text(fr, r[3] + " " + r[2] + ": " + r[4] + " paths per window, reserve " + r[5], C_DIM));
    }

    private void towns(FontRenderer fr, List<Line> l) {
        l.add(title(fr, "TOWNS - grow with rail access, jobs and goods; shrink without (never below founding size)"));
        int[] at = {0, 14, 25, 33, 41, 48, 55, 62, 70, 78};
        l.add(row(fr, new String[]{"Town", "Tier", "People", "%/day", "Access", "Jobs %", "Goods", "Noise dB", "14 days", "Why"}, at, c(C_HEAD), 0, 0));
        List<String[]> rs = rows("tw");
        if (rs.isEmpty()) l.add(text(fr, "No towns yet - place a Town Hall Planning Board (it founds a town), or serve a Town Market", C_DIM));
        for (String[] r : rs) {
            double g = Double.parseDouble(r[4]);
            l.add(row(fr, new String[]{r[1], r[2], r[3], r[4], r[5], r[6], r[7], r[8] + (r[9].equals("0") ? "" : " (" + r[9] + " barr.)"), r[12], r[11]}, at,
                    c(C_TEXT, C_HEAD, C_TEXT, g > 0 ? C_GOOD : g < 0 ? C_BAD : C_DIM, C_TEXT, C_TEXT, C_TEXT, Double.parseDouble(r[8]) > 55 ? C_WARN : C_DIM, C_GOOD, C_DIM), 0, 0));
        }
        l.add(text(fr, "Hall towns feed the station catchments within reach (split by distance) - that is where passenger demand comes from.", C_DIM));
    }

    private void journeys(FontRenderer fr, List<Line> l) {
        List<String[]> s = rows("js"), S = rows("S");
        String[] j = s.isEmpty() ? new String[]{"js", "0", "0", "0", "-", "0"} : s.get(0);
        l.add(title(fr, "JOURNEYS - arrived today " + j[1] + " · abandoned today " + j[2] + " (all time " + j[3] + ") · connections made " + (S.isEmpty() ? "-" : S.get(0)[7]) + "% · avg sampled trip " + j[4] + " min"));
        int[] at = {0, 20, 30, 39, 48, 58, 67, 77, 87};
        l.add(row(fr, new String[]{"Interchange", "Changing", "Made", "Missed", "Abandoned", "Rerouted", "Step-free lost", "Avg wait min", "Yday made/missed"}, at, c(C_HEAD), 0, 0));
        List<String[]> rs = rows("jx");
        if (rs.isEmpty()) l.add(text(fr, "Nobody has changed trains yet - lines that share a station make interchanges", C_DIM));
        for (String[] r : rs) l.add(row(fr, new String[]{r[1], r[2], r[3], r[4], r[5], r[6], r[7], r[8], r[9] + "/" + r[10]}, at, c(C_TEXT, C_TEXT, C_GOOD, C_WARN, C_BAD, C_TEXT, C_BAD, C_TEXT, C_DIM), 0, 0));
        l.add(title(fr, "SAMPLE JOURNEYS (one group in twenty, followed door to door)"));
        for (String[] r : rows("jt")) {
            l.add(text(fr, r[1] + " " + r[2] + ": " + r[3] + " -> " + r[4], r[2].equals("ABANDONED") ? C_BAD : r[2].equals("ARRIVED") ? C_GOOD : C_WARN));
            for (String leg : r[5].split(" > ")) l.add(text(fr, "    " + leg, leg.contains("MISSED") || leg.contains("gave up") ? C_BAD : C_DIM));
        }
    }

    private void ports(FontRenderer fr, List<Line> l) {
        List<String[]> f = rows("if");
        l.add(title(fr, "PORTS + TERMINALS - through freight paid " + (f.isEmpty() ? "$0.00" : f.get(0)[1])));
        int[] at = {0, 22, 31, 38, 46, 53, 60, 67, 74, 81, 91};
        l.add(row(fr, new String[]{"Place", "Kind", "Yard", "Ship mv", "Gate mv", "Rail mv", "Kept", "Walk-in", "No-show", "Fees", "Demurrage"}, at, c(C_HEAD), 0, 0));
        List<String[]> rs = rows("ix");
        if (rs.isEmpty()) l.add(text(fr, "No port berth or container terminal reporting yet", C_DIM));
        for (String[] r : rs) l.add(row(fr, new String[]{r[1], r[2], r[3], r[4], r[5], r[6], r[7], r[8], r[9], r[10], r[11]}, at, c(C_TEXT, C_HEAD, C_TEXT, C_TEXT, C_TEXT, C_TEXT, C_GOOD, C_WARN, C_BAD, C_GOOD, C_WARN), 0, 0));
        l.add(title(fr, "TRUCK APPOINTMENTS"));
        for (String[] r : rows("ia")) l.add(text(fr, r[1] + "  " + r[2] + " at " + r[4] + " - " + r[3], r[3].equals("NO-SHOW") ? C_BAD : r[3].equals("KEPT") ? C_GOOD : C_TEXT));
        l.add(title(fr, "PORT LOG"));
        for (String[] r : rows("il")) l.add(text(fr, r[1], C_DIM));
    }

    private void cargo(FontRenderer fr, List<Line> l) {
        l.add(title(fr, "CARGO CUSTODY - every box's seal, condition and handling history (/irextras biz cargo <number> for the full chain)"));
        int[] at = {0, 13, 22, 32, 42, 54, 61, 68, 74, 84};
        l.add(row(fr, new String[]{"Box", "Status", "Owner", "To", "Seal", "Clean", "Moist", "°C", "Goods", "Last event"}, at, c(C_HEAD), 0, 0));
        for (String[] r : rows("cu")) l.add(row(fr, new String[]{r[1], r[2], r[3], r[4], r[5], r[6] + "%", r[7] + "%", r[8], r[9], r[10]}, at,
                c(C_TEXT, r[2].equals("OK") || r[2].equals("CLEARED") ? C_GOOD : C_BAD, C_DIM, C_DIM, r[5].contains("BROKEN") ? C_BAD : C_TEXT, Double.parseDouble(r[6]) < 60 ? C_WARN : C_TEXT,
                        Double.parseDouble(r[7]) > 14 ? C_WARN : C_TEXT, C_DIM, r[9].contains("DG") ? C_BAD : C_TEXT, C_DIM), 0, 0));
        l.add(title(fr, "INVESTIGATIONS"));
        List<String[]> cc = rows("cc");
        if (cc.isEmpty()) l.add(text(fr, "None - every delivery inspected clean", C_GOOD));
        for (String[] r : cc) l.add(text(fr, "#" + r[1] + " " + r[2] + " " + r[3] + ": " + r[4] + " - liable " + r[5] + ", goods " + r[6], r[3].equals("OPEN") ? C_WARN : C_DIM));
    }

    private void env(FontRenderer fr, List<Line> l, int mx, int my) {
        List<String[]> g = rows("eg");
        String[] e = g.isEmpty() ? new String[]{"eg", "-", "-", "0", "0", "0", "0", "250"} : g.get(0);
        l.add(title(fr, "ENVIRONMENT - diesel " + e[1] + " vs electric " + e[2] + " kg CO2 per train-km · regen braking saved " + e[3] + " kg (" + e[4] + "% back) · land " + e[5] + " ha · waste " + e[6] + " t · grid " + e[7] + " g/kWh"));
        int[] at = {0, 10, 20, 28, 38, 47, 56, 65, 72, 79, 85};
        l.add(row(fr, new String[]{"Company", "Diesel kWh", "Fuel L", "Elec kWh", "Regen kWh", "CO2 kg", "Yday kg", "NOx g", "PM g", "km", "Measures"}, at, c(C_HEAD), 0, 0));
        for (String[] r : rows("en")) l.add(row(fr, new String[]{r[1], r[2], r[3], r[4], r[5], r[6], r[7], r[8], r[9], r[10], r[11]}, at, c(C_TEXT, C_TEXT, C_WARN, C_TEXT, C_GOOD, C_WARN, C_DIM, C_DIM, C_DIM, C_DIM, C_GOOD), 0, 0));
        Object[] b = new Object[MEASURES.length * 3];
        for (int i = 0; i < MEASURES.length; i++) { b[i * 3] = MEASURES[i].replace('_', ' '); b[i * 3 + 1] = 1014; b[i * 3 + 2] = MEASURES[i]; }
        l.add(buttons(fr, "Invest for your company:", mx, my, b));
        l.add(text(fr, "Noise barriers are built for a town at its Planning Board. Measures: barrier -10 dB, solar -15% grid CO2, DPF -70% PM, trees 120 kg/day, water -60% washings.", C_DIM));
    }

    private void research(FontRenderer fr, List<Line> l, int mx, int my) {
        List<String[]> S = rows("S");
        boolean on = !S.isEmpty() && S.get(0)[1].equals("true");
        l.add(title(fr, "RESEARCH TREE - " + (on ? "ON: technologies unlock by investment, experience, experiments and components" : "OFF (config): everything is unlocked")));
        for (String[] r : rows("rs")) {
            String[] p = r[2].split(",");
            StringBuilder b = new StringBuilder(r[1] + (r[4].equals("true") ? " (grandfathered)" : "") + " focus " + r[3] + ": ");
            for (int i = 0; i < Math.min(p.length, TECHS.length); i++) b.append(TECHS[i], 0, Math.min(6, TECHS[i].length())).append(p[i].equals("D") ? "✔ " : " " + p[i] + " ");
            l.add(text(fr, b.toString(), C_TEXT));
        }
        for (String t : TECHS) l.add(buttons(fr, t, mx, my, "EXPERIMENT", 1009, t, "FOCUS", 1010, t));
    }

    private void insure(FontRenderer fr, List<Line> l, int mx, int my) {
        l.add(buttons(fr, "Your company's policy:", mx, my, "NONE", 1011, "NONE,1", "THIRD PARTY", 1011, "THIRD_PARTY,1", "COMPREHENSIVE", 1011, "COMPREHENSIVE,1", "COMP. LOW EXCESS", 1011, "COMPREHENSIVE,0"));
        int[] at = {0, 8, 22, 30, 40, 50, 58, 66, 76, 87};
        l.add(row(fr, new String[]{"Co.", "Cover", "Excess", "Premium/d", "Compliance", "Overdue", "Brake F", "Paid", "Claims paid", "Docs"}, at, c(C_HEAD), 0, 0));
        for (String[] r : rows("ip")) l.add(row(fr, new String[]{r[1], r[2], r[3], r[4], r[5] + "%", r[6], r[7], r[8], r[9], r[10]}, at,
                c(C_TEXT, r[2].equals("NONE") ? C_DIM : C_GOOD, C_TEXT, C_WARN, Double.parseDouble(r[5]) < 90 ? C_WARN : C_GOOD, C_TEXT, r[7].equals("0") ? C_TEXT : C_BAD, C_DIM, C_GOOD, C_TEXT), 0, 0));
        l.add(title(fr, "CLAIMS"));
        List<String[]> cl = rows("ic");
        if (cl.isEmpty()) l.add(text(fr, "No claims. Closed incidents involving an insured company's vehicles are claimed automatically.", C_DIM));
        for (String[] r : cl) {
            l.add(row(fr, new String[]{"C" + r[1], r[2], r[3], r[4], "value " + r[5], "liab " + r[6], "paid " + r[7], "evid " + r[8]}, c(0, 6, 12, 20, 50, 62, 73, 85),
                    c(C_DIM, C_TEXT, r[3].equals("PAID") ? C_GOOD : r[3].equals("DENIED") ? C_BAD : C_WARN, C_TEXT, C_TEXT, C_TEXT, C_GOOD, C_TEXT), mx, my, "SETTLE", 1003, r[1]));
            if (!r[9].isEmpty()) l.add(text(fr, "    " + r[9], C_DIM));
        }
    }

    private void regulator(FontRenderer fr, List<Line> l, int mx, int my) {
        List<String[]> S = rows("S");
        l.add(title(fr, "REGULATOR " + (S.isEmpty() || S.get(0)[2].equals("true") ? "ON" : "OFF (server)") + (S.isEmpty() || !S.get(0)[3].equals("true") ? " - overdue actions cost reputation" : " - overdue actions are FINED")));
        for (String[] r : rows("ra")) {
            int sc = Integer.parseInt(r[2]);
            l.add(row(fr, new String[]{r[1], r[2] + "/100", r[3], r[4].isEmpty() ? "all checks passed" : r[4]}, c(0, 8, 18, 30), c(C_TEXT, sc >= 80 ? C_GOOD : sc >= 50 ? C_WARN : C_BAD, C_DIM, r[4].isEmpty() ? C_GOOD : C_WARN), mx, my, "AUDIT NOW", 1002, r[5]));
        }
        l.add(title(fr, "CORRECTIVE ACTIONS"));
        List<String[]> rc = rows("rc");
        if (rc.isEmpty()) l.add(text(fr, "None open", C_GOOD));
        for (String[] r : rc) l.add(row(fr, new String[]{"CA" + r[1], r[2], r[3], r[4], r[5], "by " + r[6]}, c(0, 7, 13, 27, 64, 76),
                c(C_DIM, C_TEXT, C_HEAD, C_TEXT, r[5].equals("OVERDUE") ? C_BAD : r[5].equals("CLOSED") ? C_GOOD : C_WARN, C_DIM), mx, my, "ACCEPT", 1004, r[1], "APPEAL", 1005, r[1]));
    }

    private void deals(FontRenderer fr, List<Line> l, int mx, int my) {
        l.add(title(fr, "MERGERS, ACQUISITIONS, FRANCHISES - assets, debt, contracts and obligations go with the deal (/irextras biz deal offer ...)"));
        List<String[]> rs = rows("dd");
        if (rs.isEmpty()) l.add(text(fr, "No deals yet", C_DIM));
        for (String[] r : rs) l.add(row(fr, new String[]{"D" + r[1], r[2] + " > " + r[3], r[5], r[6], r[7]}, c(0, 6, 18, 60, 72),
                c(C_DIM, C_TEXT, C_TEXT, r[6].equals("OFFERED") ? C_WARN : r[6].equals("DONE") || r[6].equals("ACTIVE") ? C_GOOD : C_DIM, C_DIM), mx, my,
                r[6].equals("OFFERED") ? new Object[]{"ACCEPT", 1006, r[1], "REJECT", 1007, r[1]} : new Object[0]));
    }

    private void stocks(FontRenderer fr, List<Line> l, int mx, int my) {
        List<String[]> S = rows("S");
        l.add(title(fr, "RAILWAY STOCK EXCHANGE - index " + (S.isEmpty() ? "-" : S.get(0)[6]) + "  (Pride coins only; trade at a Stock Exchange Board)"));
        int[] at = {0, 7, 28, 38, 46, 56, 66, 76, 83};
        l.add(row(fr, new String[]{"Mark", "Company", "Last", "Chg %", "Fair", "Bid", "Ask", "Vol", "24 days"}, at, c(C_HEAD), 0, 0));
        for (String[] r : rows("sx")) l.add(row(fr, new String[]{r[1], r[2], r[3], r[4], r[5], r[6], r[7], r[8], r[9]}, at,
                c(C_TEXT, C_DIM, C_TEXT, r[4].startsWith("-") ? C_BAD : C_GOOD, C_DIM, C_GOOD, C_WARN, C_DIM, C_GOOD), 0, 0));
        l.add(title(fr, "ORDER BOOK"));
        for (String[] r : rows("so")) l.add(row(fr, new String[]{"#" + r[1], r[2], r[3], r[4] + "/1000", r[5], r[6]}, c(0, 8, 16, 24, 36, 50), c(C_DIM, C_TEXT, r[3].equals("BUY") ? C_GOOD : C_WARN, C_TEXT, C_TEXT, C_DIM), mx, my, "CANCEL", 1015, r[1]));
        l.add(title(fr, "BONDS"));
        for (String[] r : rows("sn")) l.add(row(fr, new String[]{"B" + r[1], r[2], r[3], r[4] + " x $100", r[5] + "%/wk", r[6] + " wk", "matures " + r[7]}, c(0, 6, 14, 24, 40, 52, 62),
                c(C_DIM, C_TEXT, r[3].equals("DEFAULTED") ? C_BAD : C_GOOD, C_TEXT, C_TEXT, C_DIM, C_DIM), mx, my, "BUY 1", 1016, r[1]));
        l.add(title(fr, "TAPE"));
        for (String[] r : rows("st")) l.add(text(fr, r[1], C_DIM));
    }

    private void trophies(FontRenderer fr, List<Line> l) {
        l.add(title(fr, "OPERATIONAL MASTERY - cosmetic trophies (engraved trophy items go to members online)"));
        for (String[] r : rows("mt")) l.add(text(fr, r[1] + "  level " + r[2] + " (" + r[3] + " xp)  🏆 " + (r[4].isEmpty() ? "-" : r[4]), r[4].isEmpty() ? C_DIM : C_WARN));
        l.add(title(fr, "LATEST AWARDS"));
        for (String[] r : rows("mf")) l.add(text(fr, r[1], C_GOOD));
    }

    private void pso(FontRenderer fr, List<Line> l) {
        l.add(title(fr, "PUBLIC-SERVICE TENDERS (lowest daily subsidy wins; bid at a Town Hall Planning Board or /irextras biz pso bid)"));
        List<String[]> pt = rows("pt");
        if (pt.isEmpty()) l.add(text(fr, "No tenders - the county tenders lines serving towns with poor rail access", C_DIM));
        for (String[] r : pt) l.add(text(fr, "T" + r[1] + " " + r[2] + " " + r[3] + " - " + r[4] + " · needs " + r[5] + " · bids: " + (r[6].isEmpty() ? "none" : r[6]) + " · closes " + r[7], r[3].equals("OPEN") ? C_WARN : C_DIM));
        l.add(title(fr, "CONTRACTS (requirements checked at the end of every day)"));
        for (String[] r : rows("pc")) l.add(text(fr, "P" + r[1] + " " + r[2] + " " + r[3] + " " + r[4] + "/day " + r[5] + " · met/missed " + r[6] + " · " + r[7] + " · paid " + r[8] + " · ends " + r[9], r[7].equals("MET") ? C_GOOD : C_WARN));
    }

    private void scenario(FontRenderer fr, List<Line> l) {
        List<String[]> S = rows("S");
        String name = S.isEmpty() ? "" : g(S.get(0), 8);
        l.add(title(fr, name.isEmpty() ? "SCENARIO - none. /irextras biz scenario generate <name>, then apply it to a BRAND-NEW world (existing worlds are never modified)" : "SCENARIO \"" + name + "\""));
        for (String[] r : rows("sc")) {
            boolean done = Integer.parseInt(r[2]) >= Integer.parseInt(r[3]);
            l.add(text(fr, (done ? "✔ " : "· ") + r[1] + " - " + r[2] + "/" + r[3] + " (by day " + r[4] + ")", done ? C_GOOD : C_TEXT));
        }
        l.add(title(fr, "WORLD + BUSINESS LOG"));
        for (String[] r : rows("lg")) l.add(text(fr, r[1], C_DIM));
    }
}
