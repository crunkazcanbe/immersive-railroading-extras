package com.dogpound.railmap.client.gui;

import net.minecraft.client.gui.FontRenderer;

import java.util.ArrayList;
import java.util.List;

/** SAFETY tab pages of signalling batch 3 part A (see safety.SigA): trip cocks + balises, and the safety supervisor. */
final class SigPagesA {
    private SigPagesA() {}

    static List<SigPage> pages() {
        List<SigPage> l = new ArrayList<>();
        l.add(new Trips());
        l.add(new Sup());
        return l;
    }

    /** train stops, tripped trains (RESET), balise groups and the passage log */
    static final class Trips extends SigPage {
        @Override String name() { return "Trips+Balises"; }

        @Override
        void draw(FontRenderer fr, int mx, int my) {
            buttonsRight(fr, mx, my, "RESET ALL TRIPS", 1021, "", "RESET TRIP", 1021, sel);
            fr.drawString("Train stops (trip cocks), tripped trains, balise groups and what trains read from them", x + 6, top - 12, C_DIM);
            List<Object[]> l = new ArrayList<>();
            List<String[]> tr = rows("a.trip");
            l.add(new Object[]{"TRIPPED TRAINS (" + tr.size() + ") - click one, then RESET TRIP once it stands", C_HEAD, null});
            for (String[] r : tr) l.add(new Object[]{"  " + r[2] + (r[6].equals("1") ? " (driverless)" : "") + "  at " + r[3] + "  " + r[4] + "  " + r[5] + " s ago", r[1].equals(sel) ? C_WARN : C_BAD, r[1]});
            List<String[]> tc = rows("a.tc");
            l.add(new Object[]{"TRAIN STOPS (" + tc.size() + ")   at · signal · arm · mode · trips", C_HEAD, null});
            for (String[] r : tc) l.add(new Object[]{"  " + r[1] + "  " + r[2] + "  arm " + r[3] + "  " + r[4] + "  " + r[6] + " trip(s)  " + r[5], r[7].equals("1") ? C_WARN : r[3].equals("UP") ? C_BAD : C_GOOD, null});
            List<String[]> bg = rows("a.bg");
            l.add(new Object[]{"BALISE GROUPS (" + bg.size() + ")   id · at · mode · telegram · passages", C_HEAD, null});
            for (String[] r : bg) l.add(new Object[]{"  " + r[1] + "  " + r[2] + "  " + r[3] + "  " + r[4] + "  " + r[5], r[6].equals("1") ? C_TEXT : C_BAD, null});
            l.add(new Object[]{"PASSAGES (newest first)", C_HEAD, null});
            for (String[] r : rows("a.ps")) l.add(new Object[]{"  " + r[1], r[1].contains("SILENT") ? C_BAD : C_DIM, null});
            int ry = top;
            for (Object[] o : page(l)) {
                if (o[2] != null) rowHit(ry, (String) o[2]);
                fr.drawString(fr.trimStringToWidth((String) o[0], w - 14), x + 6, ry, (Integer) o[1]);
                ry += ROW;
            }
        }
    }

    /** the independent safety supervisor: racks, supervised trains vs their envelope, the log */
    static final class Sup extends SigPage {
        @Override String name() { return "Supervisor"; }

        @Override
        void draw(FontRenderer fr, int mx, int my) {
            String[] n = row("a.svn");
            buttonsRight(fr, mx, my, "RESET ALL", 1020, "", "RESET", 1020, sel);
            fr.drawString("Independent 2oo2 supervisor · " + (n == null ? "0" : n[1]) + " intervention(s) · click a train, RESET releases a hold", x + 6, top - 12, C_DIM);
            String[] cols = {"Train", "km/h", "Env A", "Env B", "State", "Limited by"};
            int[] at = {0, 20, 27, 34, 41, 75};
            head(fr, cols, at);
            int ry = top + 13, i = 0;
            List<Object[]> l = new ArrayList<>();
            for (String[] r : rows("a.sup")) l.add(new Object[]{r});
            for (String[] r : rows("a.rack")) l.add(new Object[]{"RACK " + r[1] + "  " + (r[2].equals("1") ? "ON LINE" : "DOWN") + "  " + r[3] + "  " + r[4] + "  " + r[5], r[2].equals("1") ? C_GOOD : C_BAD});
            for (String[] r : rows("a.sv")) l.add(new Object[]{r[1], r[1].contains("VIOLATION") || r[1].contains("ENFORCE") ? C_WARN : C_DIM});
            if (l.isEmpty()) fr.drawString("No safety supervisor rack, or no driverless train in its coverage", x + 6, ry, C_DIM);
            for (Object[] o : page(l)) {
                if (o.length == 1) {
                    String[] r = (String[]) o[0];
                    stripe(ry, i++, r[1].equals(sel));
                    int col = r[7].equals("1") ? C_BAD : r[7].equals("2") ? C_WARN : C_GOOD;
                    cell(fr, r[2], at, 0, ry, C_TEXT);
                    cell(fr, r[3], at, 1, ry, col);
                    cell(fr, r[4], at, 2, ry, C_TEXT);
                    cell(fr, r[5] + (r[9].equals("1") ? " XC" : ""), at, 3, ry, r[9].equals("1") ? C_WARN : C_DIM);
                    cell(fr, (r[7].equals("1") ? "HELD - " : "") + r[6], at, 4, ry, col);
                    cell(fr, r[8], at, 5, ry, C_DIM);
                    rowHit(ry, r[1]);
                } else fr.drawString(fr.trimStringToWidth((String) o[0], w - 14), x + 6, ry, (Integer) o[1]);
                ry += ROW;
            }
        }
    }
}
