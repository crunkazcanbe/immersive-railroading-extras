package com.dogpound.railmap.client.gui;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.client.ClientRailway;
import com.dogpound.railmap.stn.PacketStnOp;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import java.util.ArrayList;
import java.util.List;

/**
 * The Control Center's STN tab (ChatGPT batch 3, #21-35: cargo care, platforms, station services), drawn from rows the
 * server describes (PacketRailState.stn, see stn.StnConsole): one sub-page per PAGE row, its COLS header, ROWs (click to
 * select), BTN buttons that act on the selected row (PacketStnOp) and TXT notes.
 */
final class StnPage {
    private static final int ROW = 11;
    private static final int C_TEXT = 0xFFE8ECF0, C_DIM = 0xFF8A94A0, C_HEAD = 0xFF5BCEFA, C_GOOD = 0xFF7CF29A, C_WARN = 0xFFFFC44D, C_BAD = 0xFFFF5252,
            C_ROW = 0x22FFFFFF, C_SEL = 0x553A6FD8;

    private int page, scroll, rowsTotal, rowsVisible;
    private String sel = "";
    private final List<int[]> hits = new ArrayList<>();
    private final List<String> args = new ArrayList<>();
    private int x, y, w, h;
    String hint = "";

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
        else if (a == 2) RailMap.NETWORK.sendToServer(new PacketStnOp(arg, sel));
    }

    private void hit(int bx, int by, int bw, int bh, int action, String arg) { hits.add(new int[]{bx, by, bw, bh, action}); args.add(arg); }

    /** the server's pages: each a list of its rows (PAGE row first) */
    private static List<List<String[]>> pages() {
        List<List<String[]>> o = new ArrayList<>();
        for (String[] r : ClientRailway.get().stn) {
            if (r.length == 0) continue;
            if (r[0].equals("PAGE")) o.add(new ArrayList<>());
            if (!o.isEmpty()) o.get(o.size() - 1).add(r);
        }
        return o;
    }

    private static String g(String[] r, int i) { return i < r.length ? r[i] : ""; }

    void draw(FontRenderer fr, int x, int y, int w, int h, int mx, int my) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        hits.clear();
        args.clear();
        hint = "";
        List<List<String[]>> ps = pages();
        if (ps.isEmpty()) { fr.drawString("No station operations data yet", x + 6, y + 8, C_DIM); return; }
        page = Math.min(page, ps.size() - 1);
        int cx = x + 4, cy = y + 3;
        for (int i = 0; i < ps.size(); i++) {
            String name = g(ps.get(i).get(0), 1);
            int cw = fr.getStringWidth(name) + 10;
            if (cx + cw > x + w - 4) { cx = x + 4; cy += 13; }
            boolean on = i == page, over = mx >= cx && mx < cx + cw && my >= cy && my < cy + 11;
            Gui.drawRect(cx, cy, cx + cw, cy + 11, on ? 0xFF3A2E5C : over ? 0xFF2A2240 : 0xFF181226);
            Gui.drawRect(cx, cy + 10, cx + cw, cy + 11, PrideFrame.RAINBOW[i % PrideFrame.RAINBOW.length]);
            fr.drawString(name, cx + 5, cy + 2, on ? 0xFFFFFFFF : C_DIM);
            if (over) hint = g(ps.get(i).get(0), 2);
            hit(cx, cy, cw, 11, 100 + i, "");
            cx += cw + 2;
        }
        List<String[]> rows = ps.get(page);
        int top = cy + 30;
        // buttons (right-aligned, above the table)
        int bx = x + w - 8;
        List<String[]> btns = new ArrayList<>();
        for (String[] r : rows) if (r[0].equals("BTN")) btns.add(r);
        for (int i = btns.size() - 1; i >= 0; i--) {
            String[] b = btns.get(i);
            String label = g(b, 1);
            int bw = fr.getStringWidth(label) + 10;
            bx -= bw + 3;
            boolean need = g(b, 3).equals("1"), ok = !need || !sel.isEmpty();
            boolean over = mx >= bx && mx < bx + bw && my >= top - 15 && my < top - 4;
            Gui.drawRect(bx, top - 15, bx + bw, top - 4, !ok ? 0xFF241C2A : over ? 0xFF5A3A80 : 0xFF3A2440);
            fr.drawString(label, bx + 5, top - 13, ok ? 0xFFFFFFFF : C_DIM);
            if (ok) hit(bx, top - 15, bw, 11, 2, g(b, 2));
        }
        fr.drawString(fr.trimStringToWidth(g(rows.get(0), 2), Math.max(10, bx - x - 10)), x + 6, top - 13, C_DIM);
        // header
        String[] cols = null;
        for (String[] r : rows) if (r[0].equals("COLS")) { cols = r; break; }
        int n = cols == null ? 0 : cols.length - 1;
        int[] at = new int[n];
        String[] names = new String[n];
        for (int i = 0; i < n; i++) {
            String c = cols[i + 1];
            int k = c.lastIndexOf(':');
            names[i] = k < 0 ? c : c.substring(0, k);
            try { at[i] = k < 0 ? i * 100 / Math.max(1, n) : Integer.parseInt(c.substring(k + 1)); } catch (NumberFormatException e) { at[i] = i * 100 / Math.max(1, n); }
        }
        if (n > 0) {
            Gui.drawRect(x + 2, top, x + w - 6, top + 11, 0x33FFFFFF);
            for (int i = 0; i < n; i++) fr.drawString(names[i], x + 4 + at[i] * (w - 10) / 100, top + 2, C_HEAD);
        }
        List<String[]> data = new ArrayList<>(), txt = new ArrayList<>();
        for (String[] r : rows) if (r[0].equals("ROW")) data.add(r); else if (r[0].equals("TXT")) txt.add(r);
        int txtLines = Math.min(txt.size(), 6);
        rowsVisible = Math.max(1, (y + h - top - 14 - txtLines * 10) / ROW);
        rowsTotal = data.size();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rowsTotal - rowsVisible)));
        int ry = top + 13;
        if (data.isEmpty() && n > 0) { fr.drawString("Nothing here yet", x + 6, ry, C_DIM); ry += ROW; }
        for (int i = scroll; i < Math.min(data.size(), scroll + rowsVisible); i++) {
            String[] r = data.get(i);
            String key = g(r, 1);
            if ((i - scroll) % 2 == 0) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, C_ROW);
            if (!key.isEmpty() && key.equals(sel)) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, C_SEL);
            for (int c = 0; c < Math.max(n, 1) && c + 2 < r.length; c++) {
                String s = r[c + 2] == null ? "" : r[c + 2];
                int col = C_TEXT;
                if (!s.isEmpty()) switch (s.charAt(0)) {
                    case '!' -> { col = C_BAD; s = s.substring(1); }
                    case '~' -> { col = C_WARN; s = s.substring(1); }
                    case '+' -> { col = C_GOOD; s = s.substring(1); }
                    case '.' -> { col = C_DIM; s = s.substring(1); }
                    default -> { }
                }
                int cx0 = x + 4 + (n == 0 ? 0 : at[c]) * (w - 10) / 100, nx = c + 1 < n ? x + 4 + at[c + 1] * (w - 10) / 100 : x + w - 8;
                fr.drawString(fr.trimStringToWidth(s, Math.max(4, nx - cx0 - 3)), cx0, ry, col);
            }
            if (!key.isEmpty()) hit(x + 2, ry - 1, w - 8, ROW, 1, key);
            ry += ROW;
        }
        if (rowsTotal > rowsVisible) PrideFrame.scrollbar(x + w - 4, top + 12, rowsVisible * ROW, scroll, rowsVisible, rowsTotal);
        int ty = y + h - 4 - txtLines * 10;
        for (int i = 0; i < txtLines; i++) fr.drawString(fr.trimStringToWidth(g(txt.get(i), 1), w - 12), x + 6, ty + i * 10, C_DIM);
    }
}
