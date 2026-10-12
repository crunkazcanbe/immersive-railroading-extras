package com.dogpound.railmap.client.gui;

import com.dogpound.railmap.client.ClientRailway;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import java.util.ArrayList;
import java.util.List;

/**
 * The Control Center's TRACK tab (her ChatGPT list section 3, #13-18), live from the server (PacketRailState.track,
 * see track.TrackConsole): CONDITION is the track-quality overlay (every piece of track on record coloured healthy /
 * alert / intervention / immediate action, TSRs ringed) with the defect list; POINTS the point machines; STRUCTURES the
 * bridges and tunnels as assets; TUNNELS the tunnel control panels; CROSSINGS the predictors; SURVEY the surveyed routes.
 */
final class TrackPage {
    private static final String[] PAGES = {"Condition", "Points", "Structures", "Tunnels", "Crossings", "Survey",
            "Turntables", "Bridges", "Buffers", "Single line", "Retarders", "Standards", "Changeover", "Banking", "Coupling", "Turnback"};
    /** batch-3 asset kinds (her ChatGPT batch 3 #1-10, #25): page index -> {kind, title, 11 column heads}; rows are {"A", kind, 11 columns} */
    private static final String[][] KINDS = {
            {"TT", "Turntables + traversers (#3) - IR's own table driven by an operator's panel: pins, alignment, interlocks", "Table", "Type", "Road", "Position", "State", "Pins", "Stock t", "Motor kW", "Moves", "Mode", "12 V"},
            {"MB", "Movable bridges (#8) - swing / bascule: position detection, locks, approach locking, waterway", "Bridge", "Type", "State", "Position", "Locks", "Boats", "Next open", "Motor kW", "Openings", "Mode", "12 V"},
            {"BS", "Intelligent buffer stops (#9) - energy absorbing, approach-speed supervision", "Buffer", "Type", "State", "Ram %", "Approach", "Last kJ", "Cap kJ", "Hits", "Overruns", "Reset", "12 V"},
            {"GT", "Gauntlet / single-line sections (#7) - directional occupancy, entry reservation", "Section", "Ends", "Direction", "Occupied", "Queue A", "Queue B", "Entries", "Length m", "Refusals", "Mode", "12 V"},
            {"RT", "Yard retarders (#25) - speed radar, air, shoe wear, target exit speed", "Retarder", "Type", "Mode", "Air bar", "Target", "In km/h", "Out km/h", "Cars", "Shoes %", "State", "12 V"},
            {"TS", "Track construction standards (#10) - per standard: what is laid, limits, upkeep", "Standard", "Pieces", "Length m", "Axle t", "Line km/h", "Wear x", "Cost $/m", "Overloads", "Overspeeds", "TQI avg", "TSRs"},
            {"CO", "Locomotive changeover points (#1) - traction change, brake test, max dwell", "Point", "Mode", "Stage", "Train", "Old loco", "New loco", "Brake", "Dwell s", "Done", "Over-dwell", "12 V"},
            {"BK", "Banking / helper engines (#2) - attach at the foot, shared TE, drop at the summit", "Bank", "Mode", "Stage", "Train", "Banker", "TE share", "Coupler kN", "Assists", "Summit", "Stress", "12 V"},
            {"CP", "Automatic coupling / uncoupling (#4) - compatibility, length, brake continuity, order", "Point", "Mode", "Stage", "Train", "Length m", "Platform m", "Compat", "Brake", "Joins", "Splits", "12 V"},
            {"TB", "Automatic turnback (#5) - change ends, verify the route, depart", "Terminus", "Mode", "Stage", "Train", "Turn s", "Cab", "Route", "Brake", "Turnbacks", "Late", "12 V"}};
    private static final int ROW = 11;
    private static final int C_TEXT = 0xFFE8ECF0, C_DIM = 0xFF8A94A0, C_HEAD = 0xFF5BCEFA, C_GOOD = 0xFF7CF29A, C_WARN = 0xFFFFC44D, C_BAD = 0xFFFF5252, C_ROW = 0x22FFFFFF;
    private static final int[] BAND = {0xFF4CD964, 0xFFFFE066, 0xFFFF9F1A, 0xFFFF3A3A};

    private int page, scroll, rowsTotal, rowsVisible;
    private final List<int[]> hits = new ArrayList<>();
    String hint = "";

    void wheel(int dir) { scroll = Math.max(0, Math.min(Math.max(0, rowsTotal - rowsVisible), scroll + dir)); }

    boolean click(int mx, int my) {
        for (int[] b : hits) if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) { page = b[4]; scroll = 0; return true; }
        return false;
    }

    private static List<String[]> rows(String tag) {
        List<String[]> o = new ArrayList<>();
        for (String[] r : ClientRailway.get().track) if (r.length > 0 && r[0].equals(tag)) o.add(r);
        return o;
    }

    private static List<String[]> assets(String kind) {
        List<String[]> o = new ArrayList<>();
        for (String[] r : rows("A")) if (r.length > 1 && r[1].equals(kind)) o.add(r);
        return o;
    }

    void draw(FontRenderer fr, int x, int y, int w, int h, int mx, int my) {
        hits.clear();
        hint = "";
        int cx = x + 4;
        for (int i = 0; i < PAGES.length; i++) {
            int cw = fr.getStringWidth(PAGES[i]) + 10;
            boolean on = i == page, over = mx >= cx && mx < cx + cw && my >= y + 3 && my < y + 14;
            if (cx + cw > x + w - 4) { cx = x + 4; y += 12; h -= 12; over = mx >= cx && mx < cx + cw && my >= y + 3 && my < y + 14; }   // wrap the tabs
            Gui.drawRect(cx, y + 3, cx + cw, y + 14, on ? 0xFF3A2E5C : over ? 0xFF2A2240 : 0xFF181226);
            Gui.drawRect(cx, y + 13, cx + cw, y + 14, PrideFrame.RAINBOW[i % PrideFrame.RAINBOW.length]);
            fr.drawString(PAGES[i], cx + 5, y + 5, on ? 0xFFFFFFFF : C_DIM);
            hits.add(new int[]{cx, y + 3, cw, 11, i});
            cx += cw + 2;
        }
        int top = y + 20;
        switch (page) {
            case 0 -> condition(fr, x, top, w, y + h - top, mx, my);
            case 1 -> table(fr, x, top, w, y + h - top, "Point machines (her list #14) - wrench a switch onto each machine's POSITION display",
                    new String[]{"Machine", "Switch", "Drive", "Detected", "State", "Blades", "Heater", "Throws", "Fails", "Mode", "12 V"}, assets("PM"), new int[]{2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12}, 6);
            case 2 -> table(fr, x, top, w, y + h - top, "Structures (her list #15) - bridges, tunnels, culverts as assets",
                    new String[]{"Asset", "Kind", "Material", "Cond", "RA t", "Clearance", "TSR", "Exam", "Hazard", "Overloads", "W/O"}, assets("ST"), new int[]{2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12}, 10);
            case 3 -> table(fr, x, top, w, y + h - top, "Tunnels (her list #16) - one row per tunnel control panel",
                    new String[]{"Tunnel", "Zone m", "State", "CO ppm", "Vis m", "Air m/s", "Fans kW", "Trains", "Persons", "Vent", "12 V"}, assets("TN"), new int[]{2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12}, 4);
            case 4 -> table(fr, x, top, w, y + h - top, "Level crossings (her list #17) - predictors, road traffic, reliability",
                    new String[]{"Crossing", "LX/SIG", "State", "ETA s", "WT s", "Queue", "Vehicles", "Acts", "Health", "Avail %", "Traffic"}, assets("LX"), new int[]{2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12}, 10);
            case 5 -> table(fr, x, top, w, y + h - top, "Surveyed routes (her list #18) - exported to irextras/survey/*.geojson",
                    new String[]{"Route", "Points", "Length m", "Cut m3", "Fill m3", "Ruling", "Tightest", "Starts", "Updated"}, rows("V"), new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9}, -1);
            default -> {
                String[] k = KINDS[Math.min(KINDS.length - 1, page - 6)];
                table(fr, x, top, w, y + h - top, k[1], java.util.Arrays.copyOfRange(k, 2, 13), assets(k[0]), new int[]{2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12}, -1);
            }
        }
    }

    private void condition(FontRenderer fr, int x, int y, int w, int h, int mx, int my) {
        List<String[]> segs = rows("S");
        List<String[]> m = rows("M");
        String[] sum = m.isEmpty() ? new String[]{"M", "0", "0", "0", "0", "0", "0", "100"} : m.get(0);
        int mapW = w * 3 / 5 - 8, mapH = h - 6;
        Gui.drawRect(x + 4, y, x + 4 + mapW, y + mapH, 0xFF0A0712);
        fr.drawString("TRACK QUALITY OVERLAY", x + 8, y + 3, C_HEAD);
        int lx = x + 8;
        String[] lab = {"healthy", "alert", "intervention", "immediate"};
        for (int i = 0; i < 4; i++) { Gui.drawRect(lx, y + mapH - 9, lx + 6, y + mapH - 3, BAND[i]); fr.drawString(lab[i], lx + 8, y + mapH - 10, C_DIM); lx += fr.getStringWidth(lab[i]) + 16; }
        if (segs.isEmpty()) fr.drawString("No track on record yet - run a train, or walk the line with a Track Geometry Trolley.", x + 8, y + 16, C_DIM);
        else {
            double minX = 1e9, maxX = -1e9, minZ = 1e9, maxZ = -1e9;
            for (String[] s : segs) { double sx = num(s[1]), sz = num(s[2]); minX = Math.min(minX, sx); maxX = Math.max(maxX, sx); minZ = Math.min(minZ, sz); maxZ = Math.max(maxZ, sz); }
            double span = Math.max(32, Math.max(maxX - minX, maxZ - minZ));
            int pw = mapW - 16, ph = mapH - 30;
            double sc = Math.min(pw, ph) / span;
            double ox = x + 12 + (pw - (maxX - minX) * sc) / 2 - minX * sc, oz = y + 14 + (ph - (maxZ - minZ) * sc) / 2 - minZ * sc;
            for (int i = segs.size() - 1; i >= 0; i--) {                // worst drawn last (on top)
                String[] s = segs.get(i);
                int px = (int) (ox + num(s[1]) * sc), pz = (int) (oz + num(s[2]) * sc);
                int b = (int) num(s[4]);
                if (num(s[5]) > 0) Gui.drawRect(px - 3, pz - 3, px + 3, pz + 3, 0xFFFFFFFF);
                Gui.drawRect(px - 2, pz - 2, px + 2, pz + 2, BAND[Math.max(0, Math.min(3, b))]);
                if (Math.abs(mx - px) <= 3 && Math.abs(my - pz) <= 3) hint = s[1] + "," + s[2] + " TQI " + s[3] + " · " + s[6] + " " + s[7] + (num(s[5]) > 0 ? " · TSR " + s[5] + " km/h" : "") + " · " + s[8] + " t · inspected " + s[10] + " · " + s[11];
            }
        }
        // right: summary + defect list
        int rx = x + 8 + mapW, rw = w - mapW - 12;
        int ry = y;
        fr.drawString("NETWORK", rx, ry, C_HEAD); ry += ROW;
        fr.drawString(sum[1] + " pieces · average TQI " + sum[7], rx, ry, C_TEXT); ry += ROW;
        fr.drawString("§a" + sum[2] + " §e" + sum[3] + " §6" + sum[4] + " §c" + sum[5] + " §7(by band)", rx, ry, C_TEXT); ry += ROW;
        fr.drawString(num(sum[6]) > 0 ? "§c" + sum[6] + " TSRs in force" : "§aNo TSRs in force", rx, ry, C_TEXT); ry += ROW + 3;
        fr.drawString("WORST PIECES", rx, ry, C_HEAD); ry += ROW;
        rowsVisible = Math.max(1, (y + h - ry - 4) / ROW);
        rowsTotal = segs.size();
        scroll = Math.min(scroll, Math.max(0, rowsTotal - rowsVisible));
        for (int i = scroll; i < Math.min(segs.size(), scroll + rowsVisible); i++) {
            String[] s = segs.get(i);
            int b = (int) num(s[4]);
            if ((i & 1) == 0) Gui.drawRect(rx - 2, ry - 1, rx + rw, ry + ROW - 1, C_ROW);
            Gui.drawRect(rx, ry + 1, rx + 5, ry + 6, BAND[Math.max(0, Math.min(3, b))]);
            String t = s[1] + "," + s[2] + " " + s[12] + " TQI " + s[3] + " " + s[6] + " " + s[7] + (num(s[5]) > 0 ? " TSR " + s[5] : "");
            fr.drawString(fr.trimStringToWidth(t, rw - 10), rx + 8, ry, b >= 3 ? C_BAD : b == 2 ? C_WARN : C_TEXT);
            ry += ROW;
        }
    }

    private void table(FontRenderer fr, int x, int y, int w, int h, String title, String[] head, List<String[]> data, int[] cols, int badCol) {
        fr.drawString(title, x + 6, y, C_HEAD);
        int ty = y + ROW + 2;
        int cw = (w - 12) / head.length;
        for (int i = 0; i < head.length; i++) fr.drawString(fr.trimStringToWidth(head[i], cw - 2), x + 6 + i * cw, ty, C_DIM);
        ty += ROW;
        rowsVisible = Math.max(1, (y + h - ty - 4) / ROW);
        rowsTotal = data.size();
        scroll = Math.min(scroll, Math.max(0, rowsTotal - rowsVisible));
        if (data.isEmpty()) fr.drawString("Nothing here yet.", x + 6, ty, C_DIM);
        for (int r = scroll; r < Math.min(data.size(), scroll + rowsVisible); r++) {
            String[] d = data.get(r);
            if ((r & 1) == 0) Gui.drawRect(x + 4, ty - 1, x + w - 6, ty + ROW - 1, C_ROW);
            boolean bad = false;
            for (String v : d) if (v != null && (v.startsWith("FAILED") || v.equals("FIRE") || v.equals("EMERGENCY") || v.startsWith("FLOOD") || v.startsWith("ROCK") || v.equals("NO 12V") || v.equals("STALL") || v.equals("OVERDUE") || v.startsWith("OVERRUN") || v.startsWith("TRIPPED") || v.startsWith("FAULT"))) bad = true;
            for (int i = 0; i < cols.length; i++) {
                String v = cols[i] < d.length ? d[cols[i]] : "";
                fr.drawString(fr.trimStringToWidth(v == null ? "" : v, cw - 2), x + 6 + i * cw, ty, bad && i == 0 ? C_BAD : i == 0 ? C_GOOD : C_TEXT);
            }
            ty += ROW;
        }
    }

    private static double num(String s) { try { return Double.parseDouble(s); } catch (RuntimeException e) { return 0; } }
}
