package com.dogpound.railmap.client.gui;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import java.util.ArrayList;
import java.util.List;

/** SAFETY tab pages of signalling batch 3 part B (see safety.SigB): battery cabinets, isolations, the radio coverage map. */
final class SigPagesB {
    private SigPagesB() {}

    static List<SigPage> pages() {
        List<SigPage> l = new ArrayList<>();
        l.add(new Power());
        l.add(new Isolations());
        l.add(new RadioMap());
        return l;
    }

    /** #14 every signal battery cabinet: state, charge, health, what it feeds, runtime; TEST per row */
    static final class Power extends SigPage {
        @Override String name() { return "Batteries"; }

        @Override void draw(FontRenderer fr, int mx, int my) {
            fr.drawString("Signal battery cabinets - click a row, then TEST (30 s discharge on the load bank, from 90% charge)", x + 6, top - 12, C_DIM);
            buttonsRight(fr, mx, my, "TEST", 1040, sel);
            String[] cols = {"Cabinet", "Ah", "Charge", "Health", "State", "Feeding", "Covers", "Load", "Runtime", "Cycles", "Tested", "Mains"};
            int[] at = {0, 9, 14, 21, 28, 37, 44, 51, 57, 66, 72, 81};
            head(fr, cols, at);
            int ry = top + 13, i = 0;
            List<String[]> rs = rows("b.bat");
            if (rs.isEmpty()) fr.drawString("No signal battery cabinets loaded", x + 6, ry, C_DIM);
            for (String[] r : page(rs)) {
                stripe(ry, i++, r[13].equals(sel));
                int soc = i(r[3]), hl = i(r[4]);
                boolean bat = r[5].equals("BATTERY"), flat = r[5].equals("FLAT");
                cell(fr, r[1], at, 0, ry, C_TEXT);
                cell(fr, r[2], at, 1, ry, C_DIM);
                cell(fr, soc + "%", at, 2, ry, soc < 20 ? C_BAD : soc < 60 ? C_WARN : C_GOOD);
                cell(fr, hl + "%", at, 3, ry, hl < 60 ? C_BAD : hl < 80 ? C_WARN : C_GOOD);
                cell(fr, r[5], at, 4, ry, flat ? C_BAD : bat ? C_WARN : r[5].equals("TEST") ? C_HEAD : C_GOOD);
                cell(fr, r[6], at, 5, ry, bat ? C_WARN : C_DIM);
                cell(fr, r[7], at, 6, ry, C_DIM);
                cell(fr, r[8] + " W", at, 7, ry, C_DIM);
                cell(fr, r[9], at, 8, ry, bat ? C_WARN : C_DIM);
                cell(fr, r[10], at, 9, ry, C_DIM);
                cell(fr, r[11], at, 10, ry, C_DIM);
                cell(fr, r[12], at, 11, ry, bat || flat ? C_BAD : C_DIM);
                rowHit(ry, r[13]);
                ry += ROW;
            }
        }
    }

    /** #18 every isolation box slot with a signal: its place in the procedure */
    static final class Isolations extends SigPage {
        @Override String name() { return "Isolations"; }

        @Override void draw(FontRenderer fr, int mx, int my) {
            fr.drawString("REQUEST > GRANTED (signaller answers) > ISOLATED > RESTORE tests > signaller ACCEPTS > key to NORMAL", x + 6, top - 12, C_DIM);
            String[] cols = {"Box", "Slot", "Signal", "State", "Holder", "Min", "Test / why"};
            int[] at = {0, 10, 15, 30, 46, 60, 66};
            head(fr, cols, at);
            int ry = top + 13, i = 0;
            List<String[]> rs = rows("b.iso");
            if (rs.isEmpty()) fr.drawString("No signals on isolation boxes", x + 6, ry, C_DIM);
            for (String[] r : page(rs)) {
                stripe(ry, i++, false);
                int st = i(r[4]);
                int col = st >= 3 && st <= 5 ? C_BAD : st == 6 ? C_GOOD : st == 0 ? C_DIM : C_WARN;
                cell(fr, r[1], at, 0, ry, C_DIM);
                cell(fr, r[2], at, 1, ry, C_TEXT);
                cell(fr, r[3], at, 2, ry, C_TEXT);
                cell(fr, r[5], at, 3, ry, col);
                cell(fr, r[6], at, 4, ry, C_DIM);
                cell(fr, r[7], at, 5, ry, C_DIM);
                cell(fr, r[8].isEmpty() ? r[9] : r[8], at, 6, ry, r[9].isEmpty() ? C_HEAD : C_BAD);
                ry += ROW;
            }
        }
    }

    /** #17 the radio coverage / dead-zone map, cells coloured by level, masts and repeaters marked */
    static final class RadioMap extends SigPage {
        @Override String name() { return "Radio map"; }

        @Override void draw(FontRenderer fr, int mx, int my) {
            String[] hd = row("b.covh");
            fr.drawString(hd == null ? "No coverage data" : hd[2] + " cells of " + hd[1] + " m mapped · " + hd[3] + " dead", x + 6, top - 12, C_DIM);
            buttonsRight(fr, mx, my, "CLEAR", 1042, "", "SURVEY", 1041, "");
            List<int[]> cells = new ArrayList<>();
            for (String[] r : rows("b.cov"))
                for (String c : r[1].split(";")) {
                    String[] p = c.split(",");
                    if (p.length == 4) cells.add(new int[]{i(p[0]), i(p[1]), i(p[2]), i(p[3])});
                }
            List<String[]> masts = rows("b.mast");
            int size = hd == null ? 16 : Math.max(1, i(hd[1]));
            // bounds in cells
            int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
            for (int[] c : cells) { x0 = Math.min(x0, c[0]); z0 = Math.min(z0, c[1]); x1 = Math.max(x1, c[0]); z1 = Math.max(z1, c[1]); }
            for (String[] m : masts) { int cx = Math.floorDiv(i(m[1]), size), cz = Math.floorDiv(i(m[2]), size); x0 = Math.min(x0, cx); z0 = Math.min(z0, cz); x1 = Math.max(x1, cx); z1 = Math.max(z1, cz); }
            List<String[]> dz = rows("b.dz");
            int listH = Math.min(dz.size(), 6) * ROW + 14;
            int mapX = x + 6, mapY = top, mapW = w - 16, mapH = Math.max(40, y + h - top - 12 - listH);
            Gui.drawRect(mapX, mapY, mapX + mapW, mapY + mapH, 0xFF0C0A14);
            if (x0 == Integer.MAX_VALUE) {
                fr.drawString("No cells yet - trains map coverage as they run; SURVEY predicts it along every surveyed signal approach", mapX + 4, mapY + 4, C_DIM);
            } else {
                int nx = x1 - x0 + 1, nz = z1 - z0 + 1;
                double px = Math.max(1, Math.min((double) mapW / nx, (double) mapH / nz));
                for (int[] c : cells) {
                    int sx = mapX + (int) ((c[0] - x0) * px), sz = mapY + (int) ((c[1] - z0) * px);
                    Gui.drawRect(sx, sz, sx + Math.max(1, (int) Math.ceil(px)), sz + Math.max(1, (int) Math.ceil(px)), colour(c[2], c[3] == 1));
                }
                for (String[] m : masts) {
                    double wx = i(m[1]) / (double) size, wz = i(m[2]) / (double) size;
                    int sx = mapX + (int) ((wx - x0) * px), sz = mapY + (int) ((wz - z0) * px);
                    int col = m[4].equals("1") ? 0xFF5BCEFA : 0xFFFFFFFF;
                    if (m[3].equals("0")) col = 0xFFFF5252;
                    Gui.drawRect(sx - 2, sz - 2, sx + 3, sz + 3, col);
                    fr.drawString(m[4].equals("1") ? "R" : "M", sx + 4, sz - 3, col);
                }
                // legend
                int lx = mapX + 4, ly = mapY + mapH - 10;
                for (Object[] lg : new Object[][]{{-60, "> -70"}, {-80, "-70..-85"}, {-90, "-85..-95"}, {-100, "DEAD"}}) {
                    Gui.drawRect(lx, ly, lx + 8, ly + 8, colour((Integer) lg[0], (Integer) lg[0] < -95));
                    fr.drawString((String) lg[1], lx + 10, ly, C_DIM);
                    lx += fr.getStringWidth((String) lg[1]) + 18;
                }
                fr.drawString("M mast  R repeater  red = off air", lx, ly, C_DIM);
            }
            int ry = mapY + mapH + 4;
            fr.drawString("Worst dead zones", x + 6, ry, C_HEAD);
            ry += ROW;
            for (int k = 0; k < Math.min(6, dz.size()); k++) { fr.drawString(fr.trimStringToWidth(dz.get(k)[1] + "  " + dz.get(k)[2], w - 14), x + 6, ry, C_BAD); ry += ROW; }
            rowsTotal = 0;
        }

        static int colour(int dbm, boolean dead) {
            if (dead || dbm < -95) return 0xFF7A1010;
            if (dbm < -85) return 0xFFFFB300;
            if (dbm < -70) return 0xFF9CCC65;
            return 0xFF2E9E4A;
        }
    }
}
