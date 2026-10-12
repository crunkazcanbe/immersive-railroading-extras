package com.dogpound.railmap.client.gui;

import net.minecraft.client.gui.FontRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * SAFETY > Macros (batch-3 #19): the sixteen route macros - routes, mode, trigger channel, state (idle / running /
 * SET / FAILED), last result, runs and failures - with PLAY, CANCEL, RECORD and MODE for the selected one; below it,
 * the selected macro's routes in order and the macro log.
 */
final class SigPageMMacros extends SigPage {
    @Override String name() { return "Macros"; }

    @Override
    void draw(FontRenderer fr, int mx, int my) {
        if (sel.isEmpty()) sel = "1";
        buttonsRight(fr, mx, my, "MODE", 1083, sel, "RECORD", 1082, sel, "CANCEL", 1081, sel, "PLAY", 1080, sel);
        fr.drawString("Route macros: record on a Route Macro Panel (key REC) or /irextras sig macro", x + 6, top - 12, C_DIM);
        String[] cols = {"#", "Name", "Routes", "Mode", "Ch", "State", "Last result", "Runs/fails"};
        int[] at = {0, 4, 22, 30, 37, 42, 54, 88};
        head(fr, cols, at);
        List<String[]> rs = new ArrayList<>();
        for (String[] r : rows("m.mac")) if (!r[3].equals("0") || !r[2].startsWith("MACRO ") || r[1].equals(sel) || r[11].equals("1")) rs.add(r);
        int maxRows = Math.max(3, rowsVisible / 2);
        int ry = top + 13, i = 0;
        String[] selected = null;
        for (String[] r : rs) if (r[1].equals(sel)) selected = r;
        if (rs.isEmpty()) fr.drawString("No macros yet", x + 6, ry, C_DIM);
        for (String[] r : rs.subList(0, Math.min(rs.size(), maxRows))) {
            stripe(ry, i++, r[1].equals(sel));
            int st = i(r[6]);
            int col = st == 2 ? C_GOOD : st == 1 ? C_HEAD : st == 3 ? C_BAD : C_TEXT;
            cell(fr, r[1], at, 0, ry, C_DIM);
            cell(fr, r[2] + (r[11].equals("1") ? "  ● REC" : ""), at, 1, ry, r[11].equals("1") ? C_BAD : C_TEXT);
            cell(fr, r[3], at, 2, ry, C_TEXT);
            cell(fr, r[4], at, 3, ry, C_DIM);
            cell(fr, r[5].equals("0") ? "-" : r[5], at, 4, ry, C_DIM);
            cell(fr, new String[]{"idle", "RUNNING", "SET", "FAILED"}[Math.max(0, Math.min(3, st))], at, 5, ry, col);
            cell(fr, r[7], at, 6, ry, col);
            cell(fr, r[8] + "/" + r[9], at, 7, ry, C_DIM);
            rowHit(ry, r[1]);
            ry += ROW;
        }
        ry += 4;
        List<Object[]> l = new ArrayList<>();
        if (selected != null) {
            l.add(new Object[]{"Macro " + selected[1] + " " + selected[2] + " - routes in order:", C_HEAD});
            if (selected[10].isEmpty()) l.add(new Object[]{"   (none: key to REC on a panel, press its button, then set the routes)", C_DIM});
            int k = 1;
            for (String s : selected[10].split("\\n")) if (!s.isEmpty()) l.add(new Object[]{"   " + (k++) + ".  " + s, C_TEXT});
        }
        l.add(new Object[]{"Log", C_HEAD});
        for (String[] g : rows("m.mlog")) l.add(new Object[]{"   " + g[1], g[1].contains("FAILED") ? C_BAD : C_DIM});
        int keep = rowsVisible;
        rowsVisible = Math.max(1, (y + h - ry - 4) / ROW);
        lines(fr, l, ry);
        rowsVisible = keep;
    }
}
