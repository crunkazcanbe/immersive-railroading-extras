package com.dogpound.railmap.client.gui;

import net.minecraft.client.gui.FontRenderer;

import java.util.ArrayList;
import java.util.List;

/** SAFETY tab pages of signalling batch 3 part C (see safety.SigC): Drivers, Diagnosis, Panels. */
final class SigPagesC {
    private SigPagesC() {}

    static List<SigPage> pages() {
        List<SigPage> l = new ArrayList<>();
        l.add(new Drivers());
        l.add(new Diag());
        l.add(new Panels());
        return l;
    }

    /** #46 competence records + recent incidents */
    static final class Drivers extends SigPage {
        @Override String name() { return "Drivers"; }

        @Override
        void draw(FontRenderer fr, int mx, int my) {
            buttonsRight(fr, mx, my, "REINSTATE", 1060, sel);
            fr.drawString("Driver competence: grade, hours D/E/S, signals known, theory, SPAD/TRIP/AWS/OVSP", x + 6, top - 12, C_DIM);
            String[] cols = {"Driver", "Grade", "Rank", "Hours", "D/E/S", "Known", "Theory", "Incidents", "Next"};
            int[] at = {0, 13, 24, 35, 41, 53, 59, 66, 76};
            head(fr, cols, at);
            List<Object[]> rows = new ArrayList<>();
            for (String[] r : rows("c.d")) rows.add(new Object[]{r});
            for (String[] r : rows("c.i")) rows.add(new Object[]{r});
            int ry = top + 13, i = 0;
            if (rows.isEmpty()) fr.drawString("No competence records yet - drive a train or IDENT at a Driver Competency Assessor", x + 6, ry, C_DIM);
            for (Object[] o : page(rows)) {
                String[] r = (String[]) o[0];
                if (r[0].equals("c.d")) {
                    stripe(ry, i++, r[10].equals(sel));
                    boolean sus = r[9].equals("1");
                    cell(fr, r[1], at, 0, ry, sus ? C_BAD : C_TEXT);
                    cell(fr, r[2], at, 1, ry, sus ? C_BAD : r[2].equals("TRAINEE") ? C_WARN : C_GOOD);
                    cell(fr, r[3].isEmpty() ? "-" : r[3], at, 2, ry, C_HEAD);
                    cell(fr, r[4], at, 3, ry, C_TEXT);
                    cell(fr, r[5], at, 4, ry, C_DIM);
                    cell(fr, r[6], at, 5, ry, C_TEXT);
                    cell(fr, r[7] + "%", at, 6, ry, i(r[7]) >= 80 ? C_GOOD : C_DIM);
                    cell(fr, r[8], at, 7, ry, r[8].equals("0/0/0/0") ? C_DIM : C_WARN);
                    cell(fr, r[12], at, 8, ry, C_DIM);
                    rowHit(ry, r[10]);
                } else {
                    stripe(ry, i++, false);
                    fr.drawString(fr.trimStringToWidth("   " + r[1] + "  " + r[2] + "  " + r[3] + "  " + r[4], w - 14), x + 6, ry, C_BAD);
                }
                ry += ROW;
            }
        }
    }

    /** #48 fault jobs and open diagnosis sessions */
    static final class Diag extends SigPage {
        @Override String name() { return "Diagnosis"; }

        @Override
        void draw(FontRenderer fr, int mx, int my) {
            buttonsRight(fr, mx, my, "ABORT JOB", 1061, sel);
            fr.drawString("Live faults on 12 V signalling gear, and the Fault Diagnosis Terminals working them", x + 6, top - 12, C_DIM);
            List<Object[]> l = new ArrayList<>();
            List<String[]> ss = rows("c.s");
            for (String[] s : ss) {
                l.add(new Object[]{"TERMINAL " + s[1] + "  " + (s[2].isEmpty() ? "idle" : s[2] + "  step " + s[3] + "  score " + s[4] + "  " + s[6] + " s  tech " + s[5]), s[2].isEmpty() ? C_DIM : C_HEAD, s[8]});
                if (!s[7].isEmpty()) for (String x : s[7].split("\n")) l.add(new Object[]{"     " + x, C_TEXT, s[8]});
            }
            List<String[]> js = rows("c.j");
            l.add(new Object[]{js.isEmpty() ? "No faults on 12 V signalling gear" : js.size() + " FAULT(S):", js.isEmpty() ? C_GOOD : C_WARN, ""});
            for (String[] j : js) l.add(new Object[]{"  " + j[1] + "  " + j[2] + "  [" + j[3] + "]  " + j[4], C_BAD, ""});
            int ry = top;
            for (Object[] o : page(l)) {
                boolean selected = !((String) o[2]).isEmpty() && o[2].equals(sel);
                stripe(ry, 0, selected);
                fr.drawString(fr.trimStringToWidth((String) o[0], w - 14), x + 6, ry, (Integer) o[1]);
                if (!((String) o[2]).isEmpty()) rowHit(ry, (String) o[2]);
                ry += ROW;
            }
        }
    }

    /** #49 control panel elements and what they are bound to */
    static final class Panels extends SigPage {
        @Override String name() { return "Panels"; }

        @Override
        void draw(FontRenderer fr, int mx, int my) {
            buttonsRight(fr, mx, my, "UNBIND", 1062, sel);
            fr.drawString("Control panel elements: bind with the Signal Wrench (pick a device, click the element)", x + 6, top - 12, C_DIM);
            String[] cols = {"Element", "At", "Bound to", "Value", "12 V"};
            int[] at = {0, 14, 30, 72, 84};
            head(fr, cols, at);
            int ry = top + 13, i = 0;
            List<String[]> rs = rows("c.p");
            if (rs.isEmpty()) fr.drawString("No panel elements loaded", x + 6, ry, C_DIM);
            for (String[] r : page(rs)) {
                stripe(ry, i++, r[6].equals(sel));
                cell(fr, r[1], at, 0, ry, C_TEXT);
                cell(fr, r[2], at, 1, ry, C_DIM);
                cell(fr, r[3], at, 2, ry, r[3].equals("UNBOUND") ? C_DIM : C_HEAD);
                cell(fr, r[4], at, 3, ry, C_TEXT);
                cell(fr, r[5].equals("1") ? "ok" : "DARK", at, 4, ry, r[5].equals("1") ? C_GOOD : C_BAD);
                rowHit(ry, r[6]);
                ry += ROW;
            }
        }
    }
}
