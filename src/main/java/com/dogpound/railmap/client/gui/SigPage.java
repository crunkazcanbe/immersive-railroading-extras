package com.dogpound.railmap.client.gui;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.client.ClientRailway;
import com.dogpound.railmap.safety.PacketSafetyOp;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import java.util.ArrayList;
import java.util.List;

/**
 * One extra page on the Control Center's SAFETY tab (signalling batch 3, see SigPages). Same look and helpers as the
 * built-in SafetyPage pages: tables of the server's rows (ClientRailway safety rows, tagged), buttons that send
 * PacketSafetyOp ops, scrolling. A subclass draws below {@code top} and has the strip above it for buttons.
 */
abstract class SigPage {
    static final int ROW = 11;
    static final int C_TEXT = 0xFFE8ECF0, C_DIM = 0xFF8A94A0, C_HEAD = 0xFF5BCEFA, C_GOOD = 0xFF7CF29A, C_WARN = 0xFFFFC44D, C_BAD = 0xFFFF5252,
            C_ROW = 0x22FFFFFF, C_SEL = 0x553A6FD8;

    protected int x, y, w, h, top, scroll, rowsTotal, rowsVisible;
    private final List<int[]> hits = new ArrayList<>();
    private final List<String> args = new ArrayList<>();
    /** the selected row's key (a page's own use) */
    protected String sel = "";

    /** the chip name on the sub-page strip */
    abstract String name();

    /** draw the page; x/w/h are the tab's area, top the first table line */
    abstract void draw(FontRenderer fr, int mx, int my);

    final void render(FontRenderer fr, int x, int y, int w, int h, int top, int mx, int my) {
        this.x = x; this.y = y; this.w = w; this.h = h; this.top = top;
        hits.clear();
        args.clear();
        rowsVisible = Math.max(1, (y + h - top - 14) / ROW);
        draw(fr, mx, my);
        if (rowsTotal > rowsVisible) PrideFrame.scrollbar(x + w - 4, top + 12, rowsVisible * ROW, scroll, rowsVisible, rowsTotal);
    }

    void wheel(int dir) { scroll = Math.max(0, Math.min(Math.max(0, rowsTotal - rowsVisible), scroll + dir)); }

    boolean click(int mx, int my) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            int[] b = hits.get(i);
            if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) { act(b[4], args.get(i)); return true; }
        }
        return false;
    }

    /** a click: action >= 1000 sends PacketSafetyOp(action - 1000, arg); 1 selects a row (sel = arg); others are the page's own */
    protected void act(int a, String arg) {
        if (a == 1) sel = arg;
        else if (a >= 1000) RailMap.NETWORK.sendToServer(new PacketSafetyOp(a - 1000, arg));
        else local(a, arg);
    }

    /** a page's own click action (a < 1000, not 1) */
    protected void local(int a, String arg) { }

    protected void hit(int bx, int by, int bw, int bh, int action, String arg) { hits.add(new int[]{bx, by, bw, bh, action}); args.add(arg); }

    static List<String[]> rows(String tag) {
        List<String[]> o = new ArrayList<>();
        for (String[] r : ClientRailway.get().safety) if (r.length > 0 && r[0].equals(tag)) o.add(r);
        return o;
    }

    static String[] row(String tag) { List<String[]> l = rows(tag); return l.isEmpty() ? null : l.get(0); }

    /** a button; returns its width + gap */
    protected int button(FontRenderer fr, int bx, int by, String label, int action, String arg, int mx, int my) {
        int bw = fr.getStringWidth(label) + 10;
        boolean over = mx >= bx && mx < bx + bw && my >= by && my < by + 11;
        Gui.drawRect(bx, by, bx + bw, by + 11, over ? 0xFF5A3A80 : 0xFF3A2440);
        fr.drawString(label, bx + 5, by + 2, 0xFFFFFFFF);
        hit(bx, by, bw, 11, action, arg);
        return bw + 3;
    }

    /** buttons right-aligned in the strip above the table, right to left */
    protected void buttonsRight(FontRenderer fr, int mx, int my, Object... labelActionArg) {
        int bx = x + w - 8;
        for (int i = 0; i + 2 < labelActionArg.length; i += 3) {
            String l = (String) labelActionArg[i];
            bx -= fr.getStringWidth(l) + 13;
            button(fr, bx, top - 14, l, (Integer) labelActionArg[i + 1], (String) labelActionArg[i + 2], mx, my);
        }
    }

    protected void head(FontRenderer fr, String[] cols, int[] at) {
        Gui.drawRect(x + 2, top, x + w - 6, top + 11, 0x33FFFFFF);
        for (int i = 0; i < cols.length; i++) fr.drawString(cols[i], x + 4 + at[i] * (w - 10) / 100, top + 2, C_HEAD);
    }

    protected void cell(FontRenderer fr, String s, int[] at, int i, int ry, int col) {
        int cx = x + 4 + at[i] * (w - 10) / 100, nx = i + 1 < at.length ? x + 4 + at[i + 1] * (w - 10) / 100 : x + w - 8;
        fr.drawString(fr.trimStringToWidth(s == null ? "" : s, Math.max(4, nx - cx - 3)), cx, ry, col);
    }

    /** the rows of a table that fit, after the scroll */
    protected <T> List<T> page(List<T> all) {
        rowsTotal = all.size();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rowsTotal - rowsVisible)));
        return all.subList(Math.min(scroll, all.size()), Math.min(all.size(), scroll + rowsVisible));
    }

    protected void stripe(int ry, int i, boolean selected) {
        if (i % 2 == 0) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, C_ROW);
        if (selected) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, C_SEL);
    }

    /** a clickable table row (selects arg) */
    protected void rowHit(int ry, String arg) { hit(x + 2, ry - 1, w - 8, ROW, 1, arg); }

    /** text lines (Object[]{text, colour}) scrolled into the page area from `from` */
    protected void lines(FontRenderer fr, List<Object[]> l, int from) {
        int ry = from;
        for (Object[] o : page(l)) { fr.drawString(fr.trimStringToWidth((String) o[0], w - 14), x + 6, ry, (Integer) o[1]); ry += ROW; }
    }

    static int i(String s) { try { return Integer.parseInt(s.trim()); } catch (RuntimeException e) { return 0; } }
    static double d(String s) { try { return Double.parseDouble(s.trim()); } catch (RuntimeException e) { return 0; } }
}
