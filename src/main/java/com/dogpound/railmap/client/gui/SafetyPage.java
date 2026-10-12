package com.dogpound.railmap.client.gui;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.client.ClientRailway;
import com.dogpound.railmap.safety.PacketSafetyOp;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import java.util.ArrayList;
import java.util.List;

/**
 * The Control Center's SAFETY tab (her ChatGPT list section 4, #19-28 + the fault lab): one page per system, all live
 * from the server (PacketRailState.safety, see safety.SafetyConsoleData). TRAINS is the train-tracking table and
 * describer; RADIO the communications log; AXLE the evaluator sections; HELD every signal the safety systems hold and
 * why; SIGHTING each signal's surveyed sighting; TESTS the self-test results and maintenance history; FAIL-SAFE the
 * per-subsystem failure modes (click a row to change it); ROUTES the interlocking's safety case for each route
 * request; LAB the fault lab's state, faults and last report.
 */
final class SafetyPage {
    private static final String[] PAGES = {"Trains", "Radio", "Axle", "Held", "Sighting", "Tests", "Fail-safe", "Routes", "Lab"};
    private static final int ROW = 11;
    private static final int C_TEXT = 0xFFE8ECF0, C_DIM = 0xFF8A94A0, C_HEAD = 0xFF5BCEFA, C_GOOD = 0xFF7CF29A, C_WARN = 0xFFFFC44D, C_BAD = 0xFFFF5252,
            C_ROW = 0x22FFFFFF, C_SEL = 0x553A6FD8;

    private int page, scroll, rowsTotal, rowsVisible;
    private String selTrain = "";
    private final List<int[]> hits = new ArrayList<>();
    private final List<String> args = new ArrayList<>();
    private int x, y, w, h;
    /** signalling batch 3: more pages after the built-in ones (SigPages) */
    private final List<SigPage> extra = SigPages.all();

    void wheel(int dir) { if (page >= PAGES.length && page - PAGES.length < extra.size()) { extra.get(page - PAGES.length).wheel(dir); return; } scroll = Math.max(0, Math.min(Math.max(0, rowsTotal - rowsVisible), scroll + dir)); }

    boolean click(int mx, int my) {
        if (page >= PAGES.length && page - PAGES.length < extra.size() && extra.get(page - PAGES.length).click(mx, my)) return true;
        for (int i = hits.size() - 1; i >= 0; i--) {
            int[] b = hits.get(i);
            if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) { act(b[4], args.get(i)); return true; }
        }
        return false;
    }

    private void act(int a, String arg) {
        if (a >= 100 && a < 200) { page = a - 100; scroll = 0; }
        else if (a == 1) selTrain = arg;
        else if (a == 300) selDec = arg;
        else if (a >= 1000) RailMap.NETWORK.sendToServer(new PacketSafetyOp(a - 1000, arg));
    }

    private void hit(int bx, int by, int bw, int bh, int action, String arg) { hits.add(new int[]{bx, by, bw, bh, action}); args.add(arg); }

    private List<String[]> rows(String tag) {
        List<String[]> o = new ArrayList<>();
        for (String[] r : ClientRailway.get().safety) if (r.length > 0 && r[0].equals(tag)) o.add(r);
        return o;
    }

    void draw(FontRenderer fr, int x, int y, int w, int h, int mx, int my) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        hits.clear();
        args.clear();
        // sub-page chips
        int cx = x + 4, cy = y + 3;
        for (int i = 0; i < PAGES.length + extra.size(); i++) {
            String name = i < PAGES.length ? PAGES[i] : extra.get(i - PAGES.length).name();
            int cw = fr.getStringWidth(name) + 10;
            if (cx + cw > x + w - 4) { cx = x + 4; cy += 13; }                   // chips wrap onto a second row
            boolean on = i == page, over = mx >= cx && mx < cx + cw && my >= cy && my < cy + 11;
            Gui.drawRect(cx, cy, cx + cw, cy + 11, on ? 0xFF3A2E5C : over ? 0xFF2A2240 : 0xFF181226);
            Gui.drawRect(cx, cy + 10, cx + cw, cy + 11, PrideFrame.RAINBOW[i % PrideFrame.RAINBOW.length]);
            fr.drawString(name, cx + 5, cy + 2, on ? 0xFFFFFFFF : C_DIM);
            hit(cx, cy, cw, 11, 100 + i, "");
            cx += cw + 2;
        }
        int top = cy + 28;
        if (page >= PAGES.length && page - PAGES.length < extra.size()) {
            extra.get(page - PAGES.length).render(fr, x, y, w, h, top, mx, my);
            return;
        }
        rowsVisible = Math.max(1, (y + h - top - 14) / ROW);
        switch (page) {
            case 0 -> trains(fr, top, mx, my);
            case 1 -> radio(fr, top, mx, my);
            case 2 -> axle(fr, top);
            case 3 -> held(fr, top);
            case 4 -> sighting(fr, top);
            case 5 -> tests(fr, top, mx, my);
            case 6 -> failsafe(fr, top, mx, my);
            case 7 -> routes(fr, top, mx, my);
            default -> lab(fr, top, mx, my);
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

    /** the rows of a table that fit, after the scroll */
    private <T> List<T> page(List<T> all) {
        rowsTotal = all.size();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rowsTotal - rowsVisible)));
        return all.subList(Math.min(scroll, all.size()), Math.min(all.size(), scroll + rowsVisible));
    }

    private void stripe(int ry, int i, boolean sel) {
        if (i % 2 == 0) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, C_ROW);
        if (sel) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, C_SEL);
    }

    // ---- pages ------------------------------------------------------------------------------------------------------
    private void trains(FontRenderer fr, int top, int mx, int my) {
        int bx = x + w - 8;
        bx -= fr.getStringWidth("RELEASE") + 13;
        button(fr, bx, top - 16 + 2, "RELEASE", 1005, selTrain, mx, my);
        bx -= fr.getStringWidth("CONFIRM INTEGRITY") + 13;
        button(fr, bx, top - 16 + 2, "CONFIRM INTEGRITY", 1004, selTrain, mx, my);
        String[] cols = {"Headcode", "RID", "Locos", "Veh/Ax", "Len", "Where", "km/h", "Berth", "To / route", "Radio", "Integrity"};
        int[] at = {0, 8, 13, 28, 35, 40, 50, 55, 64, 80, 90};
        head(fr, top, cols, at);
        int ry = top + 13, i = 0;
        List<String[]> rs = rows("T");
        if (rs.isEmpty()) fr.drawString("No trains tracked", x + 6, ry, C_DIM);
        for (String[] r : page(rs)) {
            stripe(ry, i++, r[19].equals(selTrain));
            boolean bad = !r[16].equals("0");
            cell(fr, r[1] + (r[18].equals("1") ? " (auto)" : ""), at, 0, ry, bad ? C_BAD : C_TEXT);
            cell(fr, r[2], at, 1, ry, C_DIM);
            cell(fr, r[3], at, 2, ry, C_TEXT);
            cell(fr, r[4] + "/" + r[5], at, 3, ry, C_TEXT);
            cell(fr, r[6] + "m", at, 4, ry, C_DIM);
            cell(fr, r[7] + "," + r[8], at, 5, ry, C_DIM);
            cell(fr, r[9], at, 6, ry, C_TEXT);
            cell(fr, r[10].isEmpty() ? "-" : r[10], at, 7, ry, C_HEAD);
            cell(fr, r[12].isEmpty() ? r[11] : r[12], at, 8, ry, C_DIM);
            cell(fr, r[13].equals("1") ? r[15] + " " + r[14] + "dBm rpt " + (r.length > 22 ? r[22] : "?") + "s" : r[13].equals("2") ? "LOST" + (r[21].equals("1") ? " BRAKED" : "") : "-", at, 9, ry, r[13].equals("2") ? C_BAD : r[13].equals("1") ? C_GOOD : C_DIM);
            cell(fr, r[16].equals("0") ? "intact" : r[17], at, 10, ry, r[16].equals("2") ? C_BAD : r[16].equals("1") ? C_WARN : C_GOOD);
            hit(x + 2, ry - 1, w - 8, ROW, 1, r[19]);
            ry += ROW;
        }
    }

    private void radio(FontRenderer fr, int top, int mx, int my) {
        button(fr, x + w - 8 - fr.getStringWidth("ANSWER CALL") - 13, top - 14, "ANSWER CALL", 1006, "", mx, my);
        String[] cols = {"#", "Time", "From", "To", "Message", "Ack"};
        int[] at = {0, 4, 11, 30, 48, 86};
        head(fr, top, cols, at);
        int ry = top + 13, i = 0;
        List<String[]> rs = rows("C");
        if (rs.isEmpty()) fr.drawString("Nothing on the radio yet", x + 6, ry, C_DIM);
        for (String[] r : page(rs)) {
            stripe(ry, i++, false);
            boolean call = r[8].equals("1");
            int ack = Integer.parseInt(r[6]);
            cell(fr, r[1], at, 0, ry, C_DIM);
            cell(fr, r[2], at, 1, ry, C_DIM);
            cell(fr, r[3], at, 2, ry, call ? C_BAD : C_TEXT);
            cell(fr, r[4], at, 3, ry, C_TEXT);
            cell(fr, r[5], at, 4, ry, call ? C_WARN : C_TEXT);
            cell(fr, ack == 0 ? (call ? "RINGING" : "waiting") : ack == 1 ? r[7] : ack == 2 ? "NO CONTACT" : "-", at, 5, ry, ack == 0 ? C_WARN : ack == 2 ? C_BAD : C_GOOD);
            ry += ROW;
        }
    }

    private void axle(FontRenderer fr, int top) {
        String[] cols = {"Evaluator", "Sect", "State", "Count", "In / out", "Heads", "Signals", "Why"};
        int[] at = {0, 12, 18, 32, 40, 52, 59, 67};
        head(fr, top, cols, at);
        int ry = top + 13, i = 0;
        List<String[]> rs = rows("A");
        if (rs.isEmpty()) fr.drawString("No axle counter sections set up (wrench heads onto an evaluator's section windows)", x + 6, ry, C_DIM);
        for (String[] r : page(rs)) {
            stripe(ry, i++, false);
            int col = r[3].equals("CLEAR") ? C_GOOD : r[3].equals("OCCUPIED") ? C_WARN : r[3].startsWith("PREP") ? C_HEAD : C_BAD;
            cell(fr, r[1], at, 0, ry, C_DIM);
            cell(fr, r[2], at, 1, ry, C_TEXT);
            cell(fr, r[3], at, 2, ry, col);
            cell(fr, r[4], at, 3, ry, C_TEXT);
            cell(fr, r[8] + " / " + r[9], at, 4, ry, C_DIM);
            cell(fr, r[6], at, 5, ry, C_DIM);
            cell(fr, r[7], at, 6, ry, C_DIM);
            cell(fr, r[5], at, 7, ry, C_BAD);
            ry += ROW;
        }
    }

    private void held(FontRenderer fr, int top) {
        String[] cols = {"Signal", "Held at STOP by"};
        int[] at = {0, 18};
        head(fr, top, cols, at);
        int ry = top + 13, i = 0;
        List<String[]> rs = rows("H");
        if (rs.isEmpty()) fr.drawString("No signal is held by the safety systems", x + 6, ry, C_GOOD);
        for (String[] r : page(rs)) { stripe(ry, i++, false); cell(fr, r[1], at, 0, ry, C_TEXT); cell(fr, r[2], at, 1, ry, C_BAD); ry += ROW; }
    }

    private void sighting(FontRenderer fr, int top) {
        String[] cols = {"Signal", "At", "Sighting", "Needed", "Short", "Banner", "Limited by"};
        int[] at = {0, 14, 28, 37, 46, 54, 62};
        head(fr, top, cols, at);
        int ry = top + 13, i = 0;
        List<String[]> rs = rows("S");
        if (rs.isEmpty()) fr.drawString("Signals are surveyed about every 30 s", x + 6, ry, C_DIM);
        for (String[] r : page(rs)) {
            stripe(ry, i++, false);
            boolean bad = !r[7].equals("0");
            cell(fr, r[1], at, 0, ry, C_TEXT);
            cell(fr, r[2], at, 1, ry, C_DIM);
            cell(fr, r[3] + " m", at, 2, ry, bad ? C_BAD : C_GOOD);
            cell(fr, r[4] + " m", at, 3, ry, C_DIM);
            cell(fr, bad ? r[7] + " m" : "-", at, 4, ry, bad ? C_BAD : C_DIM);
            cell(fr, r[6].isEmpty() ? "-" : r[6] + " m", at, 5, ry, C_HEAD);
            cell(fr, r[5], at, 6, ry, C_DIM);
            ry += ROW;
        }
    }

    private void tests(FontRenderer fr, int top, int mx, int my) {
        String[] l = rows("L").isEmpty() ? null : rows("L").get(0);
        fr.drawString("Last self-test: " + (l == null ? "?" : l[12]) + " · every " + (l == null ? "?" : l[11]) + " min", x + 6, top - 14 + 2, C_DIM);
        button(fr, x + w - 8 - fr.getStringWidth("RUN SELF-TEST") - 13, top - 14, "RUN SELF-TEST", 1002, "", mx, my);
        String[] cols = {"Gear", "Kind", "At", "Result", "Pass/fail", "Flips", "Last history"};
        int[] at = {0, 18, 30, 40, 60, 68, 74};
        head(fr, top, cols, at);
        int ry = top + 13, i = 0;
        List<String[]> rs = rows("G");
        rs.sort((a, b) -> Integer.compare(score(b), score(a)));
        if (rs.isEmpty()) fr.drawString("No self-test has run yet", x + 6, ry, C_DIM);
        for (String[] r : page(rs)) {
            stripe(ry, i++, false);
            boolean inter = r[7].equals("1");
            int col = r[4].equals("2") ? C_BAD : inter ? C_WARN : C_GOOD;
            cell(fr, r[1], at, 0, ry, C_TEXT);
            cell(fr, r[2], at, 1, ry, C_DIM);
            cell(fr, r[3], at, 2, ry, C_DIM);
            cell(fr, (inter ? "INTERMITTENT " : "") + r[8], at, 3, ry, col);
            cell(fr, r[5] + "/" + r[6], at, 4, ry, C_DIM);
            cell(fr, r[10], at, 5, ry, C_DIM);
            cell(fr, r[9], at, 6, ry, C_DIM);
            ry += ROW;
        }
    }

    private static int score(String[] r) { return (r[4].equals("2") ? 2 : 0) + (r[7].equals("1") ? 1 : 0); }

    private void failsafe(FontRenderer fr, int top, int mx, int my) {
        fr.drawString("Click a row to step its mode. Option 1 is the safe default; red = safety-critical (only safe-side choices).", x + 6, top - 14 + 2, C_DIM);
        String[] cols = {"Subsystem", "When it fails...", "Options"};
        int[] at = {0, 30, 82};
        head(fr, top, cols, at);
        int ry = top + 13, i = 0;
        for (String[] r : page(rows("F"))) {
            boolean over = mx >= x + 2 && mx < x + w - 6 && my >= ry - 1 && my < ry + ROW - 1;
            stripe(ry, i++, over);
            String[] opts = r[5].split("\\|");
            int m = Integer.parseInt(r[4]);
            cell(fr, r[3], at, 0, ry, r[6].equals("1") ? C_BAD : C_TEXT);
            cell(fr, (m + 1) + ". " + opts[Math.min(m, opts.length - 1)], at, 1, ry, m == 0 ? C_GOOD : C_WARN);
            cell(fr, opts.length + (opts.length == 1 ? " (fixed)" : ""), at, 2, ry, C_DIM);
            if (opts.length > 1) hit(x + 2, ry - 1, w - 8, ROW, 1001, r[2]);
            ry += ROW;
        }
    }

    /** batch-3 #15: which request the conflict mimic shows (its id; "" = the newest refused one) */
    private String selDec = "";

    private void routes(FontRenderer fr, int top, int mx, int my) {
        List<ConflictMimic.D> ds = ConflictMimic.all();
        ConflictMimic.D show = null;
        for (ConflictMimic.D d : ds) if (d.id.equals(selDec)) show = d;
        if (show == null) for (ConflictMimic.D d : ds) if (!d.granted) { show = d; break; }
        if (show == null && !ds.isEmpty()) show = ds.get(0);
        // request chips over the mimic: click one to show it
        int cx = x + 6;
        fr.drawString("Requests:", cx, top - 14 + 2, C_DIM);
        cx += fr.getStringWidth("Requests:") + 4;
        for (ConflictMimic.D d : ds) {
            String s = "#" + d.id + (d.granted ? " OK" : " REFUSED");
            int cw = fr.getStringWidth(s) + 8;
            if (cx + cw > x + w - 8) break;
            boolean on = d == show;
            Gui.drawRect(cx, top - 14, cx + cw, top - 3, on ? 0xFF3A2E5C : 0xFF181226);
            fr.drawString(s, cx + 4, top - 12, d.granted ? C_GOOD : C_BAD);
            hit(cx, top - 14, cw, 11, 300, d.id);
            cx += cw + 2;
        }
        int mh = Math.max(60, Math.min(130, (h - (top - y)) / 2));
        int mw = Math.min(w - 14, mh * 2 + 60);
        String hover = null;
        List<Object[]> lines = new ArrayList<>();
        if (show != null) {
            hover = ConflictMimic.mimic(fr, show, x + 6, top, x + 6 + mw, top + mh, mx, my);
            int lx = x + 12 + mw, ly = top + 2;
            fr.drawString(fr.trimStringToWidth((show.granted ? "GRANTED  " : "REFUSED  ") + show.route, x + w - lx - 8), lx, ly, show.granted ? C_GOOD : C_BAD);
            ly += ROW;
            fr.drawString(show.time + " · " + show.ago + " s ago · " + show.blockers.size() + " blocker(s)", lx, ly, C_DIM);
            ly += ROW + 2;
            for (String[] b : show.blockers) {
                if (ly > top + mh - ROW) break;
                int c = ConflictMimic.colour(b[0]);
                Gui.drawRect(lx, ly - 1, lx + 9, ly + 8, c);
                fr.drawString(String.valueOf(ConflictMimic.letter(b[0])), lx + 2, ly, 0xFF101010);
                fr.drawString(fr.trimStringToWidth(b[0] + " " + b[1] + "," + b[2] + "  " + b[3], x + w - lx - 22), lx + 12, ly, C_TEXT);
                ly += ROW;
            }
            if (show.blockers.isEmpty()) fr.drawString(show.granted ? "Nothing in the way - every check passed" : "No geometry recorded", lx, ly, show.granted ? C_GOOD : C_DIM);
        } else {
            Gui.drawRect(x + 6, top, x + 6 + mw, top + mh, 0xFF0A0E14);
            fr.drawString("No route requests yet (NX desk, control desk, board, macros)", x + 12, top + 6, C_DIM);
        }
        for (String[] r : rows("D")) {
            lines.add(new Object[]{(r[3].equals("1") ? "GRANTED  " : "REFUSED  ") + r[2] + "   " + r[1], r[3].equals("1") ? C_GOOD : C_BAD});
            for (String l : r[4].split("\n")) lines.add(new Object[]{"    " + l, l.startsWith("+") ? 0xFF6FBF80 : l.startsWith("-") ? C_BAD : C_DIM});
        }
        int ry = top + mh + 4;
        fr.drawString("The interlocking's safety case for each route request, newest first", x + 6, ry, C_HEAD);
        ry += ROW + 1;
        rowsVisible = Math.max(1, (y + h - ry - 4) / ROW);
        for (Object[] l : page(lines)) { fr.drawString(fr.trimStringToWidth((String) l[0], w - 14), x + 6, ry, (Integer) l[1]); ry += ROW; }
        if (hover != null) {
            int tw = fr.getStringWidth(hover) + 8;
            int hx = Math.min(mx + 8, x + w - tw - 4);
            Gui.drawRect(hx, my - 14, hx + tw, my - 2, 0xF0101018);
            fr.drawString(hover, hx + 4, my - 12, 0xFFFFE0E0);
        }
    }

    private void lab(FontRenderer fr, int top, int mx, int my) {
        String[] l = rows("L").isEmpty() ? null : rows("L").get(0);
        if (l == null) return;
        boolean test = l[1].equals("1");
        fr.drawString(test ? "TEST MODE at " + l[2] + " · zone " + l[3] + " m · " + l[4] + " fault(s) in" : "Normal operation - no lab in test mode", x + 6, top - 14 + 2, test ? C_WARN : C_GOOD);
        button(fr, x + w - 8 - fr.getStringWidth("CLEAR FAULTS") - 13, top - 14, "CLEAR FAULTS", 1003, "", mx, my);
        List<Object[]> lines = new ArrayList<>();
        lines.add(new Object[]{"Settings: sighting " + l[8] + " km/h x " + l[9] + " s · overlap " + l[10] + " m · self-test every " + l[11] + " min  (/irextras safety set ...)", C_DIM});
        for (String[] f : rows("X")) lines.add(new Object[]{"FAULT  " + f[1] + "  (" + f[2] + " s)", C_WARN});
        lines.add(new Object[]{l[7].isEmpty() ? "No automated sequence run yet" : "Last sequence " + l[7] + ": " + l[5] + " PASS, " + l[6] + " FAIL", l[7].isEmpty() ? C_DIM : l[6].equals("0") ? C_GOOD : C_BAD});
        for (String[] r : rows("R")) lines.add(new Object[]{"  " + r[1], r[1].startsWith("FAIL") ? C_BAD : r[1].startsWith("PASS") ? C_GOOD : C_TEXT});
        for (String[] in : rows("I")) {
            int s = Integer.parseInt(in[4]);
            lines.add(new Object[]{"INCIDENT #" + in[1] + " " + in[2] + " by " + in[3] + " at " + in[5] + (in[6].isEmpty() ? " OPEN" : " closed after " + in[6]) + " · signals " + ((s & 1) != 0 ? "OK" : "--")
                    + " · trains stopped " + ((s & 2) != 0 ? "OK" : "--") + " · answered " + ((s & 4) != 0 ? "OK" : "--") + " · possession " + ((s & 8) != 0 ? "OK" : "--"), in[6].isEmpty() ? C_BAD : C_DIM});
        }
        int ry = top;
        for (Object[] o : page(lines)) { fr.drawString(fr.trimStringToWidth((String) o[0], w - 14), x + 6, ry, (Integer) o[1]); ry += ROW; }
    }
}
