package com.dogpound.railmap.client.gui;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.asset.PacketAssetOp;
import com.dogpound.railmap.client.ClientRailway;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import java.util.ArrayList;
import java.util.List;

/**
 * The Control Center's ASSETS tab (her 48-next list #2 #5 #10 #16 #19 #22 #24 #27 #28 #45), live from the server
 * (PacketRailState.assets, see asset.AssetConsole): REGISTER - every asset with condition, life used, days left,
 * commissioning state and inspection (select one: its history and dependencies; KIND filters); DEFECTS - discovery to
 * verified repair (ORDER / REPAIR); FLEET - telemetry trends, P-codes, remaining life, rebuild state; THERMAL -
 * transformer hot spots, loss of life, 49 replicas; SPARES - stock, on order, shortages (ORDER / EXPRESS / FACTORY /
 * REMAN); ORDERS - purchase orders (CANCEL / EXPEDITE); CERTS - commissioning, injection and acceptance certificates;
 * LOG - the register's log.
 */
final class AssetPage {
    private static final String[] PAGES = {"Register", "Defects", "Fleet", "Thermal", "Spares", "Orders", "Certs", "Log", "Rail"};
    private static final String[] KINDS = {"ALL", "Track piece", "Point machine", "Signal", "Transformer", "Circuit breaker", "Electric motor", "Insulator", "Platform", "Bridge / viaduct", "Tunnel", "Tunnel lining", "Culvert / wall / cutting"};
    private static final int ROW = 11;
    private static final int C_TEXT = 0xFFE8ECF0, C_DIM = 0xFF8A94A0, C_HEAD = 0xFF5BCEFA, C_GOOD = 0xFF7CF29A, C_WARN = 0xFFFFC44D, C_BAD = 0xFFFF5252,
            C_ROW = 0x22FFFFFF, C_SEL = 0x553A6FD8;

    private int page, scroll, rowsTotal, rowsVisible, kind;
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
        if (a >= 100 && a < 200) { page = a - 100; scroll = 0; sel = ""; }
        else if (a == 1) sel = arg;
        else if (a == 2) { kind = (kind + 1) % KINDS.length; scroll = 0; }
        else if (a >= 1000) RailMap.NETWORK.sendToServer(new PacketAssetOp(a - 1000, arg));
    }

    private void hit(int bx, int by, int bw, int bh, int action, String arg) { hits.add(new int[]{bx, by, bw, bh, action}); args.add(arg); }

    private List<String[]> rows(String tag) {
        List<String[]> o = new ArrayList<>();
        for (String[] r : ClientRailway.get().assets) if (r.length > 0 && r[0].equals(tag)) o.add(r);
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
        List<String[]> k = rows("K");
        if (!k.isEmpty()) {
            String[] s = k.get(0);
            String sum = s[1] + " assets · " + s[2] + " uncommissioned · " + s[3] + " overdue · " + s[4] + " open defects · " + s[5] + " POs $" + s[6];
            fr.drawString(fr.trimStringToWidth(sum, w - 12), x + 6, y + 18, C_DIM);
        }
        int top = y + 31;
        rowsVisible = Math.max(1, (y + h - top - 14) / ROW);
        switch (page) {
            case 0 -> register(fr, top, mx, my);
            case 1 -> defects(fr, top, mx, my);
            case 2 -> fleet(fr, top);
            case 3 -> thermal(fr, top);
            case 4 -> spares(fr, top, mx, my);
            case 5 -> orders(fr, top, mx, my);
            case 6 -> certs(fr, top);
            case 7 -> log(fr, top);
            default -> rail(fr, top);
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

    private void buttons(FontRenderer fr, int top, String[][] bs, int mx, int my) {
        int bx = x + w - 8;
        for (String[] b : bs) {
            bx -= fr.getStringWidth(b[0]) + 13;
            button(fr, bx, top - 16 + 2, b[0], Integer.parseInt(b[1]), b.length > 2 ? b[2] : sel, mx, my);
        }
    }

    private void head(FontRenderer fr, int top, String[] cols, int[] at, int width) {
        Gui.drawRect(x + 2, top, x + 2 + width, top + 11, 0x33FFFFFF);
        for (int i = 0; i < cols.length; i++) fr.drawString(cols[i], x + 4 + at[i] * (width - 4) / 100, top + 2, C_HEAD);
    }

    private void cell(FontRenderer fr, String s, int[] at, int i, int ry, int col, int width) {
        int cx = x + 4 + at[i] * (width - 4) / 100, nx = i + 1 < at.length ? x + 4 + at[i + 1] * (width - 4) / 100 : x + width;
        fr.drawString(fr.trimStringToWidth(s == null ? "" : s, Math.max(4, nx - cx - 3)), cx, ry, col);
    }

    private <T> List<T> page(List<T> all) {
        rowsTotal = all.size();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rowsTotal - rowsVisible)));
        return all.subList(Math.min(scroll, all.size()), Math.min(all.size(), scroll + rowsVisible));
    }

    private void stripe(int ry, int i, boolean s, int width) {
        if (i % 2 == 0) Gui.drawRect(x + 2, ry - 1, x + 2 + width, ry + ROW - 1, C_ROW);
        if (s) Gui.drawRect(x + 2, ry - 1, x + 2 + width, ry + ROW - 1, C_SEL);
    }

    private static int cond(int c) { return c >= 80 ? C_GOOD : c >= 60 ? 0xFFB8E86A : c >= 40 ? C_WARN : C_BAD; }

    /** the detail panel on the right: the selected row's text lines */
    private void detail(FontRenderer fr, int top, String title, String text) {
        int px = x + w * 64 / 100, pw = w - (px - x) - 8;
        Gui.drawRect(px, top, px + pw, y + h - 6, 0x44000000);
        fr.drawString(fr.trimStringToWidth(title, pw - 6), px + 4, top + 2, C_HEAD);
        int ry = top + 14;
        for (String l : text.split("\n")) {
            for (String part : fr.listFormattedStringToWidth(l, pw - 8)) {
                if (ry > y + h - 16) return;
                fr.drawString(part, px + 4, ry, l.startsWith("PASS") || l.startsWith("VERIFIED") ? C_GOOD : l.startsWith("FAIL") ? C_BAD : C_TEXT);
                ry += 10;
            }
        }
    }

    private void register(FontRenderer fr, int top, int mx, int my) {
        button(fr, x + w - 8 - fr.getStringWidth("KIND: " + KINDS[kind]) - 10, top - 14, "KIND: " + KINDS[kind], 2, "", mx, my);
        int tw = w * 63 / 100;
        String[] cols = {"Id", "Asset", "Cond", "Life", "Days left", "Commission", "Insp h", "Def"};
        int[] at = {0, 13, 50, 59, 66, 76, 90, 96};
        head(fr, top, cols, at, tw);
        int ry = top + 13, i = 0;
        List<String[]> rs = rows("A");
        if (kind > 0) rs.removeIf(r -> !r[2].equals(KINDS[kind]));
        if (rs.isEmpty()) fr.drawString("Nothing registered yet - gear is found every 10 s", x + 6, ry, C_DIM);
        String[] show = null;
        for (String[] r : rs) if (r[1].equals(sel)) show = r;
        for (String[] r : page(rs)) {
            stripe(ry, i++, r[1].equals(sel), tw);
            int c = Integer.parseInt(r[4]);
            boolean overdue = r[9].equals("1");
            cell(fr, r[1], at, 0, ry, C_TEXT, tw);
            cell(fr, r[3], at, 1, ry, C_DIM, tw);
            cell(fr, c + "% " + r[5], at, 2, ry, cond(c), tw);
            cell(fr, r[6] + "%", at, 3, ry, Integer.parseInt(r[6]) >= 100 ? C_BAD : Integer.parseInt(r[6]) >= 75 ? C_WARN : C_DIM, tw);
            cell(fr, r[7], at, 4, ry, C_DIM, tw);
            cell(fr, r[8], at, 5, ry, r[8].equals("COMMISSIONED") ? C_GOOD : r[8].equals("FAILED TESTS") ? C_BAD : C_WARN, tw);
            cell(fr, r[10], at, 6, ry, overdue ? C_WARN : C_DIM, tw);
            cell(fr, r[11].equals("0") ? "" : r[11], at, 7, ry, C_BAD, tw);
            hit(x + 2, ry - 1, tw, ROW, 1, r[1]);
            ry += ROW;
        }
        if (show != null) detail(fr, top, show[1] + " " + show[2], show[3] + "\nat " + show[13] + "\nuse " + show[12] + "\n" + (show[14].isEmpty() ? "" : "depends on " + show[14] + "\n") + "\n" + show[15]);
        else detail(fr, top, "Select an asset", "Condition = life curve, the asset's own measurements and its open defects.\nInspect with the instruments (asset tool), commission at a Commissioning Test Set.");
    }

    private void defects(FontRenderer fr, int top, int mx, int my) {
        buttons(fr, top, new String[][]{{"REPAIR", "1002"}, {"ORDER REPAIR", "1001"}}, mx, my);
        int tw = w * 63 / 100;
        String[] cols = {"#", "Asset", "Defect", "Stage", "Measured", "Critical", "Spare"};
        int[] at = {0, 5, 20, 46, 64, 80, 90};
        head(fr, top, cols, at, tw);
        int ry = top + 13, i = 0;
        List<String[]> rs = rows("D");
        if (rs.isEmpty()) fr.drawString("No defects found - hidden ones wait for an inspection", x + 6, ry, C_DIM);
        String[] show = null;
        for (String[] r : rs) if (r[1].equals(sel)) show = r;
        for (String[] r : page(rs)) {
            stripe(ry, i++, r[1].equals(sel), tw);
            boolean crit = r[10].equals("1");
            cell(fr, r[1], at, 0, ry, C_DIM, tw);
            cell(fr, r[2], at, 1, ry, C_TEXT, tw);
            cell(fr, r[3], at, 2, ry, crit ? C_BAD : C_TEXT, tw);
            cell(fr, r[4], at, 3, ry, r[4].equals("VERIFIED") ? C_GOOD : r[4].equals("REPAIRED") ? 0xFFB8E86A : r[4].equals("CONFIRMED") ? C_BAD : C_WARN, tw);
            cell(fr, r[5], at, 4, ry, crit ? C_BAD : C_TEXT, tw);
            cell(fr, r[6], at, 5, ry, C_DIM, tw);
            cell(fr, r[9], at, 6, ry, C_DIM, tw);
            hit(x + 2, ry - 1, tw, ROW, 1, r[1]);
            ry += ROW;
        }
        if (show != null) detail(fr, top, "Defect #" + show[1] + " " + show[4], show[3] + " on " + show[2] + "\nfound by " + show[8] + ", " + show[7] + " h ago\n\n" + show[11]);
        else detail(fr, top, "Select a defect", "DISCOVERED -> CONFIRMED (found again) -> REPAIR ORDERED -> REPAIRED (the spare went in) -> VERIFIED (re-test clean).");
    }

    private void fleet(FontRenderer fr, int top) {
        String[] cols = {"Locomotive", "P-codes", "Fails first", "RUL h", "Trends (last, per op. hour)", "Samples", "Rebuild", "Worst %"};
        int[] at = {0, 14, 36, 50, 56, 84, 89, 96};
        head(fr, top, cols, at, w - 8);
        int ry = top + 13, i = 0;
        List<String[]> rs = rows("F");
        if (rs.isEmpty()) fr.drawString("No locomotives seen yet", x + 6, ry, C_DIM);
        for (String[] r : page(rs)) {
            stripe(ry, i++, false, w - 8);
            cell(fr, r[2], at, 0, ry, C_TEXT, w - 8);
            cell(fr, r[3].isEmpty() ? "-" : r[3], at, 1, ry, r[3].isEmpty() ? C_DIM : C_WARN, w - 8);
            cell(fr, r[4], at, 2, ry, C_DIM, w - 8);
            cell(fr, r[5], at, 3, ry, C_DIM, w - 8);
            cell(fr, r[6], at, 4, ry, C_DIM, w - 8);
            cell(fr, r[7], at, 5, ry, C_DIM, w - 8);
            cell(fr, r[8], at, 6, ry, r[8].equals("NOT ACCEPTED") || r[8].equals("FAILED") ? C_BAD : r[8].equals("ACCEPTED") ? C_GOOD : C_DIM, w - 8);
            cell(fr, r[9], at, 7, ry, C_DIM, w - 8);
            ry += ROW;
        }
    }

    private void thermal(FontRenderer fr, int top) {
        String[] cols = {"Machine", "Where", "Hot spot °C", "Top oil °C", "Ageing x", "Aged h", "49 %", "Radiator %", "Fans", "Amb °C", "Insul %"};
        int[] at = {0, 16, 30, 40, 50, 58, 66, 72, 81, 88, 94};
        head(fr, top, cols, at, w - 8);
        int ry = top + 13, i = 0;
        List<String[]> rs = rows("T");
        if (rs.isEmpty()) fr.drawString("No transformers or motors on the grid", x + 6, ry, C_DIM);
        for (String[] r : page(rs)) {
            stripe(ry, i++, false, w - 8);
            double hs = r[3].equals("-") ? 0 : Double.parseDouble(r[3]);
            int rep = Integer.parseInt(r[7]);
            cell(fr, r[1], at, 0, ry, C_TEXT, w - 8);
            cell(fr, r[2], at, 1, ry, C_DIM, w - 8);
            cell(fr, r[3], at, 2, ry, hs >= 140 ? C_BAD : hs >= 120 ? C_WARN : C_TEXT, w - 8);
            cell(fr, r[4], at, 3, ry, C_DIM, w - 8);
            cell(fr, r[5], at, 4, ry, !r[5].equals("-") && Double.parseDouble(r[5]) > 2 ? C_WARN : C_DIM, w - 8);
            cell(fr, r[6], at, 5, ry, C_DIM, w - 8);
            cell(fr, r[7], at, 6, ry, rep >= 100 ? C_BAD : rep >= 80 ? C_WARN : C_DIM, w - 8);
            cell(fr, r[8], at, 7, ry, C_DIM, w - 8);
            cell(fr, r[9], at, 8, ry, r[9].equals("FAILED") ? C_BAD : r[9].equals("OFF") ? C_WARN : C_DIM, w - 8);
            cell(fr, r[10], at, 9, ry, C_DIM, w - 8);
            cell(fr, r[11], at, 10, ry, cond(Integer.parseInt(r[11])), w - 8);
            ry += ROW;
        }
    }

    private void spares(FontRenderer fr, int top, int mx, int my) {
        String a = sel.isEmpty() ? "" : sel;
        buttons(fr, top, new String[][]{{"REMAN", "1003", a + ":1:3"}, {"FACTORY", "1003", a + ":2:2"}, {"EXPRESS", "1003", a + ":1:1"}, {"ORDER 2", "1003", a + ":2:0"}}, mx, my);
        String[] cols = {"Spare", "Type", "Stock", "On order", "Short", "For", "Price"};
        int[] at = {0, 30, 36, 44, 53, 60, 92};
        head(fr, top, cols, at, w - 8);
        int ry = top + 13, i = 0;
        for (String[] r : page(rows("S"))) {
            stripe(ry, i++, r[1].equals(sel), w - 8);
            int stock = Integer.parseInt(r[3]), shortN = Integer.parseInt(r[5]);
            cell(fr, r[2], at, 0, ry, C_TEXT, w - 8);
            cell(fr, r[8].equals("R") ? "rolling stock" : "infrastructure", at, 1, ry, C_DIM, w - 8);
            cell(fr, String.valueOf(stock), at, 2, ry, stock == 0 ? C_BAD : stock < 2 ? C_WARN : C_GOOD, w - 8);
            cell(fr, r[4], at, 3, ry, C_DIM, w - 8);
            cell(fr, shortN == 0 ? "" : String.valueOf(shortN), at, 4, ry, C_BAD, w - 8);
            cell(fr, r[6], at, 5, ry, C_DIM, w - 8);
            cell(fr, "$" + r[7], at, 6, ry, C_DIM, w - 8);
            hit(x + 2, ry - 1, w - 8, ROW, 1, r[1]);
            ry += ROW;
        }
    }

    private void orders(FontRenderer fr, int top, int mx, int my) {
        buttons(fr, top, new String[][]{{"EXPEDITE", "1005"}, {"CANCEL", "1004"}}, mx, my);
        String[] cols = {"PO", "Spare", "Qty", "Supplier", "State", "ETA", "Cost", "Why", "By"};
        int[] at = {0, 5, 28, 32, 42, 52, 62, 69, 90};
        head(fr, top, cols, at, w - 8);
        int ry = top + 13, i = 0;
        List<String[]> rs = rows("P");
        if (rs.isEmpty()) fr.drawString("No purchase orders - the stores order by themselves when short (config autoProcure)", x + 6, ry, C_DIM);
        for (String[] r : page(rs)) {
            stripe(ry, i++, r[1].equals(sel), w - 8);
            cell(fr, "#" + r[1], at, 0, ry, C_DIM, w - 8);
            cell(fr, r[2], at, 1, ry, C_TEXT, w - 8);
            cell(fr, r[3], at, 2, ry, C_TEXT, w - 8);
            cell(fr, r[4], at, 3, ry, r[4].equals("EXPRESS") ? C_WARN : C_DIM, w - 8);
            cell(fr, r[5], at, 4, ry, r[5].equals("OPEN") ? C_WARN : r[5].equals("DELIVERED") ? C_GOOD : C_DIM, w - 8);
            cell(fr, r[6], at, 5, ry, C_DIM, w - 8);
            cell(fr, "$" + r[7], at, 6, ry, C_DIM, w - 8);
            cell(fr, r[8], at, 7, ry, C_DIM, w - 8);
            cell(fr, r[9], at, 8, ry, C_DIM, w - 8);
            hit(x + 2, ry - 1, w - 8, ROW, 1, r[1]);
            ry += ROW;
        }
    }

    private void certs(FontRenderer fr, int top) {
        int tw = w * 63 / 100;
        String[] cols = {"#", "Certificate", "For", "Result", "By", "h ago"};
        int[] at = {0, 6, 34, 64, 74, 92};
        head(fr, top, cols, at, tw);
        int ry = top + 13, i = 0;
        List<String[]> rs = rows("C");
        if (rs.isEmpty()) fr.drawString("No certificates - commission gear, inject IEDs, accept rebuilds", x + 6, ry, C_DIM);
        String[] show = null;
        for (String[] r : rs) if (r[1].equals(sel)) show = r;
        for (String[] r : page(rs)) {
            stripe(ry, i++, r[1].equals(sel), tw);
            cell(fr, r[1], at, 0, ry, C_DIM, tw);
            cell(fr, r[2], at, 1, ry, C_TEXT, tw);
            cell(fr, r[3], at, 2, ry, C_TEXT, tw);
            cell(fr, r[4], at, 3, ry, r[4].equals("PASS") ? C_GOOD : C_BAD, tw);
            cell(fr, r[5], at, 4, ry, C_DIM, tw);
            cell(fr, r[6], at, 5, ry, C_DIM, tw);
            hit(x + 2, ry - 1, tw, ROW, 1, r[1]);
            ry += ROW;
        }
        if (show != null) detail(fr, top, "#" + show[1] + " " + show[2], show[7]);
        else detail(fr, top, "Select a certificate", "Commissioning certificates list each acceptance test and every connection as found (discovered) or VERIFIED by a test.");
    }

    private void log(FontRenderer fr, int top) {
        int ry = top;
        List<String[]> rs = rows("L");
        if (rs.isEmpty()) fr.drawString("Nothing logged yet", x + 6, ry, C_DIM);
        for (String[] r : page(rs)) { fr.drawString(fr.trimStringToWidth(r[1], w - 12), x + 6, ry, C_TEXT); ry += ROW; }
    }

    // ---- RAIL (batch 3 #41 / #42): rail flaws by UIC class, ultrasonic test-train runs, on-track machines ---------------
    private void rail(FontRenderer fr, int top) {
        List<String[]> rows = new ArrayList<>();
        for (String[] r : rows("RF")) rows.add(r);
        rows.add(new String[]{"H", "ULTRASONIC TEST TRAIN RUNS (IR stock tagged UTU / ULTRASONIC / SPERRY)"});
        for (String[] r : rows("UT")) rows.add(r);
        rows.add(new String[]{"H", "ON-TRACK MACHINES (rail grinder / flash-butt welder)"});
        for (String[] r : rows("TM")) rows.add(r);
        String[] cols = {"#", "Where", "Class", "Size mm", "Crit", "Stage", "Remedy", "Found by"};
        int[] at = {0, 5, 15, 45, 56, 62, 76, 84};
        head(fr, top, cols, at, w);
        int ry = top + 13, i = 0;
        if (rows("RF").isEmpty()) { fr.drawString("No rail flaws found yet - test rails with the Ultrasonic Rail Tester or run a test train", x + 6, ry, C_DIM); ry += ROW; }
        for (String[] r : page(rows)) {
            if (ry > y + h - 12) break;
            if (r[0].equals("RF")) {
                stripe(ry, i++, false, w);
                for (int k = 1; k <= 8 && k < r.length; k++) cell(fr, r[k], at, k - 1, ry, k == 6 && r[6].equals("CONFIRMED") ? C_BAD : k == 7 ? C_WARN : C_TEXT, w);
            } else if (r[0].equals("H")) fr.drawString(r[1], x + 6, ry, C_HEAD);
            else if (r[0].equals("UT")) fr.drawString(fr.trimStringToWidth(r[1], w - 12), x + 10, ry, r[1].contains("NOT TESTED") ? C_WARN : C_TEXT);
            else fr.drawString(fr.trimStringToWidth(r[1] + " " + r[2] + ": " + r[3], w - 12), x + 10, ry, C_TEXT);
            ry += ROW;
        }
    }
}
