package com.dogpound.railmap.client.gui;

import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.client.ClientRailway;
import com.dogpound.railmap.graph.LogEntry;
import com.dogpound.railmap.graph.RailNetwork;
import com.dogpound.railmap.graph.RailNode;
import com.dogpound.railmap.graph.RailSegment;
import com.dogpound.railmap.graph.SignalNode;
import com.dogpound.railmap.graph.StopNode;
import com.dogpound.railmap.graph.TrainNode;
import com.dogpound.railmap.network.PacketRailState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Railway Control Center: the tabs across the top of the dispatcher board (requested feature).
 * The MAP tab is the board she already had, untouched; every other tab is a console page drawn over the map area.
 * Everything here reads data the board already receives (trains, network scan, lines, driverless trains), so no new
 * server traffic.
 */
final class ControlCenter {
    enum Tab {
        MAP("Map", "the live network map"), TRAINS("Trains", "every train, live"), LINES("Lines", "lines and their orders"),
        STATIONS("Stops", "stations, loaders and lineside"), SIGNALS("Signals", "every signal and its aspect"),
        POWER("Power", "electric traction"), MAINT("Repair", "maintenance: wear and repairs"), FINANCE("Money", "finance: income per train"),
        STATS("Stats", "statistics: the whole network in numbers"), EVENTS("Log", "events: arrivals and departures"), SETTINGS("Setup", "settings: board options");

        final String label, help;

        Tab(String label, String help) { this.label = label; this.help = help; }
    }

    static final int TAB_H = 15;
    private static final int ROW = 11, HEAD = 13;
    private static final int C_TEXT = 0xFFE8ECF0, C_DIM = 0xFF8A94A0, C_HEAD = 0xFF5BCEFA, C_GOOD = 0xFF7CF29A,
            C_WARN = 0xFFFFC44D, C_BAD = 0xFFFF5252, C_ROW = 0x22FFFFFF, C_SEL = 0x553A6FD8, C_PANEL = 0xFF0C0816;

    Tab tab = Tab.MAP;
    int scroll;
    int sortCol, filter;
    boolean sortDesc = true;
    String hint = "";
    /** Settings, kept for the session (and the map reads them every frame). */
    static boolean mph, showLabels = true, showGrid = true, showTrainNames = true, flashAlarms = true;
    static int wearAlarm = 70, refreshSlow;

    /** Row the player clicked on a TRAINS-style table: the train's entity id, for opening it on the map. */
    int pickedTrain = -1;

    private final List<int[]> hits = new ArrayList<>();       // x, y, w, h, action
    private int x, y, w, h, rowsVisible, rowsTotal;

    // ------------------------------------------------------------------ tab strip
    void drawTabs(FontRenderer fr, int left, int top, int width, int mx, int my) {
        Tab[] all = Tab.values();
        int tw = width / all.length;
        for (int i = 0; i < all.length; i++) {
            int tx = left + i * tw, ww = i == all.length - 1 ? width - i * tw : tw - 2;
            boolean on = all[i] == tab, over = mx >= tx && mx < tx + ww && my >= top && my < top + TAB_H;
            Gui.drawRect(tx, top, tx + ww, top + TAB_H, on ? 0xFF3A2E5C : over ? 0xFF2A2240 : 0xFF181226);
            Gui.drawRect(tx, top + TAB_H - 2, tx + ww, top + TAB_H, PrideFrame.RAINBOW[i % PrideFrame.RAINBOW.length] & (on ? 0xFFFFFFFF : 0x80FFFFFF));
            String s = fr.trimStringToWidth(all[i].label, ww - 4);
            fr.drawString(s, tx + (ww - fr.getStringWidth(s)) / 2, top + 3, on ? 0xFFFFFFFF : C_DIM);
            if (over) hint = all[i].label + ": " + all[i].help;
        }
    }

    /** true if the click hit a tab */
    boolean clickTabs(int left, int top, int width, int mx, int my) {
        if (my < top || my >= top + TAB_H || mx < left || mx >= left + width) return false;
        Tab[] all = Tab.values();
        int i = Math.min(all.length - 1, (mx - left) / Math.max(1, width / all.length));
        if (tab != all[i]) { tab = all[i]; scroll = 0; sortCol = 0; filter = 0; }
        return true;
    }

    // ------------------------------------------------------------------ pages
    void draw(FontRenderer fr, RailNetwork net, List<TrainNode> trains, int x, int y, int w, int h, int mx, int my) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        hits.clear();
        Gui.drawRect(x, y, x + w, y + h, C_PANEL);
        PrideFrame.clip(x, y, w, h);
        try {
            switch (tab) {
                case TRAINS -> trains(fr, trains, mx, my);
                case LINES -> lines(fr, net, mx, my);
                case STATIONS -> stations(fr, net, trains, mx, my);
                case SIGNALS -> signals(fr, net, mx, my);
                case POWER -> power(fr, trains, mx, my);
                case MAINT -> maintenance(fr, trains, mx, my);
                case FINANCE -> finance(fr, mx, my);
                case STATS -> stats(fr, net, trains);
                case EVENTS -> events(fr, net, mx, my);
                case SETTINGS -> settings(fr, mx, my);
                default -> { }
            }
        } finally { PrideFrame.unclip(); }
        if (rowsTotal > rowsVisible) PrideFrame.scrollbar(x + w - 4, y + HEAD + 18, rowsVisible * ROW, scroll, rowsVisible, rowsTotal);
    }

    void wheel(int dir) { scroll = Math.max(0, Math.min(Math.max(0, rowsTotal - rowsVisible), scroll + dir)); }

    /** returns true when handled */
    boolean click(int mx, int my) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            int[] b = hits.get(i);
            if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) { act(b[4]); return true; }
        }
        return false;
    }

    // actions: 0..99 sort column, 100..199 filter, 1000+id pick train, 2000.. settings
    private void act(int a) {
        if (a < 100) { if (sortCol == a) sortDesc = !sortDesc; else { sortCol = a; sortDesc = true; } }
        else if (a < 200) { filter = a - 100; scroll = 0; }
        else if (a >= 1000 && a < 2000) pickedTrain = a - 1000;
        else if (a == 2000) mph = !mph;
        else if (a == 2001) showLabels = !showLabels;
        else if (a == 2002) showGrid = !showGrid;
        else if (a == 2003) showTrainNames = !showTrainNames;
        else if (a == 2004) flashAlarms = !flashAlarms;
        else if (a == 2005) wearAlarm = Math.max(10, wearAlarm - 5);
        else if (a == 2006) wearAlarm = Math.min(100, wearAlarm + 5);
        else if (a == 2007) refreshSlow = (refreshSlow + 1) % 3;
        else if (a >= 3000) pickedTrain = a - 3000;    // finance row -> train id
    }

    // ------------------------------------------------------------------ helpers
    private String speed(float kmh) {
        return mph ? String.format(Locale.ROOT, "%.0f mph", kmh * 0.621371f) : String.format(Locale.ROOT, "%.0f km/h", kmh);
    }

    private void header(FontRenderer fr, String title, String sub) {
        fr.drawStringWithShadow(title, x + 6, y + 4, 0xFFF5A9B8);
        fr.drawString(fr.trimStringToWidth(sub, w - 20 - fr.getStringWidth(title)), x + 12 + fr.getStringWidth(title), y + 4, C_DIM);
    }

    private int chips(FontRenderer fr, String[] names, int cy, int mx, int my) {
        int cx = x + 6;
        for (int i = 0; i < names.length; i++) {
            int cw = fr.getStringWidth(names[i]) + 8;
            boolean on = filter == i, over = mx >= cx && mx < cx + cw && my >= cy && my < cy + 11;
            Gui.drawRect(cx, cy, cx + cw, cy + 11, on ? 0xFF6A3FA0 : over ? 0xFF3A2E52 : 0xFF241C33);
            fr.drawString(names[i], cx + 4, cy + 2, on ? 0xFFFFFFFF : 0xFFC8C0D8);
            hits.add(new int[]{cx, cy, cw, 11, 100 + i});
            cx += cw + 3;
        }
        return cy + 14;
    }

    /** table header with clickable sort columns; returns y of the first row */
    private int columns(FontRenderer fr, String[] cols, int[] cx, int cy, int mx, int my) {
        Gui.drawRect(x + 2, cy - 1, x + w - 6, cy + HEAD - 2, 0xFF1C1530);
        for (int i = 0; i < cols.length; i++) {
            int next = i + 1 < cx.length ? cx[i + 1] : w - 8;
            String c = cols[i] + (sortCol == i ? (sortDesc ? " v" : " ^") : "");
            fr.drawString(fr.trimStringToWidth(c, next - cx[i] - 2), x + cx[i], cy + 1, sortCol == i ? 0xFFFFFFFF : C_HEAD);
            hits.add(new int[]{x + cx[i], cy - 1, next - cx[i], HEAD - 1, i});
        }
        return cy + HEAD;
    }

    private void cell(FontRenderer fr, String s, int[] cx, int i, int ry, int color) {
        int next = i + 1 < cx.length ? cx[i + 1] : w - 8;
        fr.drawString(fr.trimStringToWidth(s, next - cx[i] - 3), x + cx[i], ry, color);
    }

    private void bar(int bx, int by, int bw, float frac, int color) {
        Gui.drawRect(bx, by, bx + bw, by + 6, 0xFF2A2240);
        Gui.drawRect(bx, by, bx + Math.max(0, Math.min(bw, (int) (bw * frac))), by + 6, color);
    }

    private int[] scaled(int... fr) {
        int[] out = new int[fr.length];
        for (int i = 0; i < fr.length; i++) out[i] = 6 + (w - 14) * fr[i] / 100;
        return out;
    }

    private <T> List<T> page(List<T> all, int firstY) {
        rowsVisible = Math.max(1, (y + h - firstY - 4) / ROW);
        rowsTotal = all.size();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rowsTotal - rowsVisible)));
        return all.subList(scroll, Math.min(all.size(), scroll + rowsVisible));
    }

    private static String kindName(TrainNode t) {
        return t.kind == null ? "?" : t.kind.name().charAt(0) + t.kind.name().substring(1).toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String dir(TrainNode t) {
        return t.reverser > 0.1f ? "Fwd" : t.reverser < -0.1f ? "Rev" : "N";
    }

    // ------------------------------------------------------------------ TRAINS
    private void trains(FontRenderer fr, List<TrainNode> all, int mx, int my) {
        List<TrainNode> list = new ArrayList<>();
        int moving = 0, driverless = 0;
        for (TrainNode t : all) {
            if (!t.lead && t.consist > 1 && !t.kind.isLoco()) continue;   // one row per train: its lead / loco
            boolean auto = ClientRailway.train(t.id) != null;
            if (t.moving()) moving++;
            if (auto) driverless++;
            if (filter == 1 && !t.moving() || filter == 2 && t.moving() || filter == 3 && !auto || filter == 4 && auto
                    || filter == 5 && !t.kind.isLoco()) continue;
            list.add(t);
        }
        header(fr, "Trains", list.size() + " shown · " + moving + " moving · " + driverless + " driverless · click a row to open it on the map");
        int cy = chips(fr, new String[]{"All", "Moving", "Stopped", "Driverless", "Manual", "Locomotives"}, y + 16, mx, my);
        String[] cols = {"Train", "Type", "Speed", "Thr", "Brk", "Dir", "Cars", "Cargo", "Pax", "Wear", "Heading", "Status"};
        int[] cx = scaled(0, 20, 30, 39, 44, 49, 53, 58, 65, 70, 77, 84);
        Comparator<TrainNode> cmp = switch (sortCol) {
            case 1 -> Comparator.comparing(ControlCenter::kindName);
            case 2 -> Comparator.comparingDouble(t -> t.speedKmh);
            case 3 -> Comparator.comparingDouble(t -> t.throttle);
            case 4 -> Comparator.comparingDouble(t -> t.brake);
            case 6 -> Comparator.comparingInt(t -> t.consist);
            case 7 -> Comparator.comparingInt(t -> t.cargoPct);
            case 8 -> Comparator.comparingInt(t -> t.passengers);
            case 9 -> Comparator.comparingInt(t -> t.wear);
            default -> Comparator.comparing(TrainNode::displayName, String.CASE_INSENSITIVE_ORDER);
        };
        list.sort(sortDesc && sortCol != 0 ? cmp.reversed() : cmp);
        int ry = columns(fr, cols, cx, cy, mx, my);
        int i = 0;
        for (TrainNode t : page(list, ry)) {
            boolean over = mx >= x && mx < x + w - 6 && my >= ry && my < ry + ROW;
            if (i++ % 2 == 0) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, C_ROW);
            if (over) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, C_SEL);
            PacketRailState.TrainInfo ai = ClientRailway.train(t.id);
            cell(fr, t.displayName(), cx, 0, ry, t.kind.isLoco() ? 0xFFFFE066 : C_TEXT);
            cell(fr, kindName(t), cx, 1, ry, C_DIM);
            cell(fr, speed(Math.abs(t.speedKmh)), cx, 2, ry, t.moving() ? C_GOOD : C_DIM);
            cell(fr, Math.round(t.throttle * 100) + "%", cx, 3, ry, C_TEXT);
            cell(fr, Math.round(t.brake * 100) + "%", cx, 4, ry, t.brake > 0.5f ? C_WARN : C_TEXT);
            cell(fr, dir(t), cx, 5, ry, C_TEXT);
            cell(fr, String.valueOf(t.consist), cx, 6, ry, C_TEXT);
            cell(fr, t.cargoPct < 0 ? "-" : t.cargoPct + "%", cx, 7, ry, C_TEXT);
            cell(fr, String.valueOf(t.passengers), cx, 8, ry, C_TEXT);
            cell(fr, t.wear < 0 ? "-" : t.wear + "%", cx, 9, ry, t.wear >= wearAlarm ? C_BAD : t.wear >= wearAlarm - 20 ? C_WARN : C_GOOD);
            cell(fr, t.heading == null ? "" : t.heading, cx, 10, ry, C_DIM);
            cell(fr, ai != null ? "AUTO " + ai.status : (t.power == null || t.power.isEmpty() ? "manual" : t.power), cx, 11, ry, ai != null ? C_GOOD : C_DIM);
            hits.add(new int[]{x, ry - 1, w - 6, ROW, 1000 + t.id});
            ry += ROW;
        }
        if (list.isEmpty()) fr.drawString("No trains match. Trains show up here once the board can see them.", x + 6, ry + 2, C_DIM);
    }

    // ------------------------------------------------------------------ LINES
    private void lines(FontRenderer fr, RailNetwork net, int mx, int my) {
        List<PacketRailState.LineInfo> lines = ClientRailway.get().lines;
        header(fr, "Lines & orders", lines.size() + " lines · edit them on the Map tab (Lines button)");
        List<String[]> rows = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        for (PacketRailState.LineInfo l : lines) {
            int on = 0;
            for (PacketRailState.TrainInfo t : ClientRailway.get().trains) if (l.name.equals(t.line)) on++;
            rows.add(new String[]{l.name, String.valueOf(l.stations.size()), String.valueOf(on), "", ""});
            colors.add(0xFF000000 | l.color);
            for (int k = 0; k < l.stations.size(); k++) {
                RailwayData.Order o = RailwayData.Order.byCode(k < l.orders.size() ? l.orders.get(k) : 0);
                rows.add(new String[]{"   " + (k + 1) + ". " + stationName(net, l.stations.get(k)), "", "", o.label, o.help});
                colors.add(o == RailwayData.Order.STOP ? C_TEXT : C_WARN);
            }
        }
        int[] cx = scaled(0, 45, 55, 64, 78);
        int ry = columns(fr, new String[]{"Line / stop", "Stops", "Trains", "Order", "What it does"}, cx, y + 18, mx, my);
        List<String[]> shown = page(rows, ry);
        for (int i = 0; i < shown.size(); i++) {
            String[] r = shown.get(i);
            int col = colors.get(scroll + i);
            boolean line = !r[0].startsWith("   ");
            if (line) { Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, 0x33FFFFFF); Gui.drawRect(x + 2, ry - 1, x + 5, ry + ROW - 1, col); }
            for (int c = 0; c < r.length; c++) cell(fr, r[c], cx, c, ry, line ? 0xFFFFFFFF : c == 0 ? C_TEXT : col);
            ry += ROW;
        }
        if (lines.isEmpty()) fr.drawString("No lines yet. On the Map tab press Lines, then New line, and click stations in order.", x + 6, ry + 2, C_DIM);
    }

    static String stationName(RailNetwork net, long key) {
        for (StopNode s : net.stops) if (s.named && s.pos.toLong() == key) return s.name;
        BlockPos p = BlockPos.fromLong(key);
        return p.getX() + "," + p.getZ();
    }

    // ------------------------------------------------------------------ STATIONS
    private void stations(FontRenderer fr, RailNetwork net, List<TrainNode> trains, int mx, int my) {
        List<StopNode> list = new ArrayList<>();
        int named = 0;
        for (StopNode s : net.stops) {
            if (s.named) named++;
            if (filter == 1 && !s.named || filter == 2 && s.kind != StopNode.Kind.LOADER && s.kind != StopNode.Kind.UNLOADER
                    || filter == 3 && s.kind != StopNode.Kind.DETECTOR && s.kind != StopNode.Kind.CONTROL) continue;
            list.add(s);
        }
        header(fr, "Stations & lineside", net.stops.size() + " stops · " + named + " named stations");
        int cy = chips(fr, new String[]{"All", "Stations", "Loaders / unloaders", "Detectors / control"}, y + 16, mx, my);
        int[] cx = scaled(0, 30, 45, 62, 76, 88);
        int ry = columns(fr, new String[]{"Name", "Kind", "Position", "Nearest train", "Distance", "Next for"}, cx, cy, mx, my);
        List<Object[]> rows = new ArrayList<>();
        for (StopNode s : list) {
            TrainNode best = null;
            double bd = Double.MAX_VALUE;
            for (TrainNode t : trains) {
                double dx = t.x - s.pos.getX(), dz = t.z - s.pos.getZ(), d = Math.sqrt(dx * dx + dz * dz);
                if (d < bd) { bd = d; best = t; }
            }
            int heading = 0;
            for (PacketRailState.TrainInfo t : ClientRailway.get().trains) if (s.named && s.name.equals(t.next)) heading++;
            rows.add(new Object[]{s, best, bd, heading});
        }
        Comparator<Object[]> cmp = switch (sortCol) {
            case 1 -> Comparator.comparing(r -> ((StopNode) r[0]).kind.name());
            case 4 -> Comparator.comparingDouble(r -> (Double) r[2]);
            case 5 -> Comparator.comparingInt(r -> (Integer) r[3]);
            default -> Comparator.comparing(r -> ((StopNode) r[0]).name == null ? "" : ((StopNode) r[0]).name, String.CASE_INSENSITIVE_ORDER);
        };
        rows.sort(sortDesc && sortCol != 0 ? cmp.reversed() : cmp);
        int i = 0;
        for (Object[] r : page(rows, ry)) {
            StopNode s = (StopNode) r[0];
            TrainNode t = (TrainNode) r[1];
            if (i++ % 2 == 0) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, C_ROW);
            cell(fr, s.named ? s.name : "(unnamed)", cx, 0, ry, s.named ? C_GOOD : C_DIM);
            cell(fr, s.kind.name().toLowerCase(Locale.ROOT), cx, 1, ry, C_DIM);
            cell(fr, s.pos.getX() + ", " + s.pos.getY() + ", " + s.pos.getZ(), cx, 2, ry, C_TEXT);
            cell(fr, t == null ? "-" : t.displayName(), cx, 3, ry, C_TEXT);
            double d = (Double) r[2];
            cell(fr, t == null ? "-" : d < 1000 ? Math.round(d) + " m" : String.format(Locale.ROOT, "%.1f km", d / 1000), cx, 4, ry, d < 50 ? C_GOOD : C_TEXT);
            int n = (Integer) r[3];
            cell(fr, n == 0 ? "-" : n + " train" + (n == 1 ? "" : "s"), cx, 5, ry, n > 0 ? C_WARN : C_DIM);
            ry += ROW;
        }
    }

    // ------------------------------------------------------------------ SIGNALS
    private void signals(FontRenderer fr, RailNetwork net, int mx, int my) {
        int[] count = new int[SignalNode.Aspect.values().length];
        for (SignalNode s : net.signals) count[s.aspect.ordinal()]++;
        StringBuilder sub = new StringBuilder(net.signals.size() + " signals");
        for (SignalNode.Aspect a : SignalNode.Aspect.values()) sub.append(" · ").append(count[a.ordinal()]).append(' ').append(a.name().toLowerCase(Locale.ROOT));
        header(fr, "Signals", sub.toString());
        String[] chipNames = new String[SignalNode.Aspect.values().length + 1];
        chipNames[0] = "All";
        for (SignalNode.Aspect a : SignalNode.Aspect.values()) chipNames[a.ordinal() + 1] = a.name().charAt(0) + a.name().substring(1).toLowerCase(Locale.ROOT);
        int cy = chips(fr, chipNames, y + 16, mx, my);
        List<SignalNode> list = new ArrayList<>();
        for (SignalNode s : net.signals) if (filter == 0 || s.aspect.ordinal() == filter - 1) list.add(s);
        Comparator<SignalNode> cmp = switch (sortCol) {
            case 0 -> Comparator.comparingInt(s -> s.aspect.ordinal());
            case 2 -> Comparator.comparing(s -> s.source == null ? "" : s.source);
            default -> Comparator.comparingInt(s -> s.pos.getX());
        };
        list.sort(sortDesc ? cmp.reversed() : cmp);
        int[] cx = scaled(0, 16, 42, 62);
        int ry = columns(fr, new String[]{"Aspect", "Position", "Source", "Raw state"}, cx, cy, mx, my);
        int i = 0;
        for (SignalNode s : page(list, ry)) {
            if (i++ % 2 == 0) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, C_ROW);
            int col = aspectColor(s.aspect);
            Gui.drawRect(x + cx[0], ry, x + cx[0] + 7, ry + 7, col);
            cell(fr, "   " + s.aspect.name().toLowerCase(Locale.ROOT), cx, 0, ry, col);
            cell(fr, s.pos.getX() + ", " + s.pos.getY() + ", " + s.pos.getZ(), cx, 1, ry, C_TEXT);
            cell(fr, s.source == null ? "" : s.source, cx, 2, ry, C_DIM);
            cell(fr, s.rawState == null ? "" : s.rawState, cx, 3, ry, C_DIM);
            ry += ROW;
        }
    }

    private static int aspectColor(SignalNode.Aspect a) {
        String n = a.name();
        return n.contains("STOP") || n.contains("RED") || n.contains("DANGER") ? C_BAD
                : n.contains("CAUTION") || n.contains("YELLOW") || n.contains("APPROACH") ? C_WARN
                : n.contains("CLEAR") || n.contains("GREEN") || n.contains("PROCEED") ? C_GOOD : C_DIM;
    }

    // ------------------------------------------------------------------ POWER
    private void power(FontRenderer fr, List<TrainNode> trains, int mx, int my) {
        List<TrainNode> list = new ArrayList<>();
        int wired = 0, battery = 0, other = 0;
        for (TrainNode t : trains) {
            if (!t.kind.isLoco()) continue;
            String p = t.power == null ? "" : t.power.toLowerCase(Locale.ROOT);
            if (p.contains("wire") || p.contains("catenary") || p.contains("third")) wired++;
            else if (p.contains("batt")) battery++;
            else other++;
            list.add(t);
        }
        header(fr, "Power & traction", list.size() + " locomotives · " + wired + " on the wire · " + battery + " on battery · " + other + " own fuel");
        int by = y + 18, bw = (w - 40) / 3;
        meter(fr, x + 6, by, bw, "On the wire", wired, list.size(), C_HEAD);
        meter(fr, x + 14 + bw, by, bw, "Battery", battery, list.size(), C_GOOD);
        meter(fr, x + 22 + 2 * bw, by, bw, "Own fuel / other", other, list.size(), C_WARN);
        int[] cx = scaled(0, 30, 45, 60, 72);
        int ry = columns(fr, new String[]{"Locomotive", "Power", "Speed", "Throttle", "Draw (est.)"}, cx, by + 26, mx, my);
        int i = 0;
        for (TrainNode t : page(list, ry)) {
            if (i++ % 2 == 0) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, C_ROW);
            cell(fr, t.displayName(), cx, 0, ry, 0xFFFFE066);
            cell(fr, t.power == null || t.power.isEmpty() ? "-" : t.power, cx, 1, ry, C_TEXT);
            cell(fr, speed(Math.abs(t.speedKmh)), cx, 2, ry, C_TEXT);
            bar(x + cx[3], ry + 1, (cx[4] - cx[3]) - 6, t.throttle, C_HEAD);
            cell(fr, Math.round(t.throttle * Math.max(1, Math.abs(t.speedKmh)) * 12) + " kW", cx, 4, ry, C_DIM);
            ry += ROW;
        }
    }

    private void meter(FontRenderer fr, int mx, int my, int mw, String label, int n, int total, int col) {
        Gui.drawRect(mx, my, mx + mw, my + 22, 0xFF181226);
        fr.drawString(label, mx + 4, my + 2, C_DIM);
        String v = n + " / " + total;
        fr.drawString(v, mx + mw - 4 - fr.getStringWidth(v), my + 2, col);
        bar(mx + 4, my + 13, mw - 8, total == 0 ? 0 : n / (float) total, col);
    }

    // ------------------------------------------------------------------ MAINTENANCE
    private void maintenance(FontRenderer fr, List<TrainNode> trains, int mx, int my) {
        List<TrainNode> list = new ArrayList<>();
        int due = 0;
        for (TrainNode t : trains) {
            if (t.wear < 0) continue;
            if (t.wear >= wearAlarm) due++;
            if (filter == 1 && t.wear < wearAlarm) continue;
            list.add(t);
        }
        list.sort(Comparator.comparingInt((TrainNode t) -> t.wear).reversed());
        header(fr, "Maintenance", list.size() + " tracked · " + due + " MAINTENANCE REQUIRED (alarm at " + wearAlarm + "%, change it in Settings)");
        int cy = chips(fr, new String[]{"All", "Needs service"}, y + 16, mx, my);
        int[] cx = scaled(0, 30, 70, 82);
        int ry = columns(fr, new String[]{"Unit", "Wear", "", "State"}, cx, cy, mx, my);
        long blink = Minecraft.getSystemTime() / 400 % 2;
        int i = 0;
        for (TrainNode t : page(list, ry)) {
            if (i++ % 2 == 0) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, C_ROW);
            int col = t.wear >= wearAlarm ? C_BAD : t.wear >= wearAlarm - 20 ? C_WARN : C_GOOD;
            cell(fr, t.displayName(), cx, 0, ry, C_TEXT);
            bar(x + cx[1], ry + 1, cx[2] - cx[1] - 6, t.wear / 100f, col);
            cell(fr, t.wear + "%", cx, 2, ry, col);
            boolean alarm = t.wear >= wearAlarm;
            cell(fr, alarm ? (flashAlarms && blink == 0 ? "" : "MAINTENANCE REQUIRED") : t.wear >= wearAlarm - 20 ? "service soon" : "ok", cx, 3, ry, col);
            hits.add(new int[]{x, ry - 1, w - 6, ROW, 1000 + t.id});
            ry += ROW;
        }
        if (list.isEmpty()) fr.drawString("No wear readings yet - trains report wear once they've run a while.", x + 6, ry + 2, C_DIM);
    }

    // ------------------------------------------------------------------ FINANCE
    private void finance(FontRenderer fr, int mx, int my) {
        List<PacketRailState.TrainInfo> list = new ArrayList<>(ClientRailway.get().trains);
        long total = 0;
        for (PacketRailState.TrainInfo t : list) total += money(t.money);
        list.sort(Comparator.comparingLong((PacketRailState.TrainInfo t) -> money(t.money)).reversed());
        header(fr, "Finance", "$" + total + " earned by " + list.size() + " driverless trains (cargo pays by amount x distance)");
        long best = list.isEmpty() ? 1 : Math.max(1, money(list.get(0).money));
        int[] cx = scaled(0, 28, 44, 70);
        int ry = columns(fr, new String[]{"Train", "Line", "Income", "Details"}, cx, y + 18, mx, my);
        int i = 0;
        for (PacketRailState.TrainInfo t : page(list, ry)) {
            if (i++ % 2 == 0) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, C_ROW);
            long m = money(t.money);
            cell(fr, t.label == null || t.label.isEmpty() ? "Train" : t.label, cx, 0, ry, C_TEXT);
            cell(fr, t.line == null || t.line.isEmpty() ? "-" : t.line, cx, 1, ry, C_DIM);
            bar(x + cx[2], ry + 1, cx[3] - cx[2] - 40, m / (float) best, 0xFFFFD54F);
            fr.drawString("$" + m, x + cx[3] - 36, ry, 0xFFFFD54F);
            cell(fr, t.money.isEmpty() ? "no deliveries yet" : t.money, cx, 3, ry, C_DIM);
            if (t.entityId >= 0) hits.add(new int[]{x, ry - 1, w - 6, ROW, 3000 + t.entityId});
            ry += ROW;
        }
        if (list.isEmpty()) fr.drawString("No driverless trains yet. Make a train driverless on its card, give it a line with orders.", x + 6, ry + 2, C_DIM);
    }

    private static long money(String s) {
        if (s == null || !s.startsWith("$")) return 0;
        int e = s.indexOf(' ');
        try { return Long.parseLong(s.substring(1, e < 0 ? s.length() : e)); } catch (NumberFormatException ex) { return 0; }
    }

    // ------------------------------------------------------------------ STATISTICS
    private void stats(FontRenderer fr, RailNetwork net, List<TrainNode> trains) {
        header(fr, "Statistics", "the whole network at a glance");
        double metres = 0;
        for (RailSegment s : net.segments) {
            if (s.a() < 0 || s.b() < 0 || s.a() >= net.nodes.size() || s.b() >= net.nodes.size()) continue;
            RailNode a = net.nodes.get(s.a()), b = net.nodes.get(s.b());
            metres += Math.sqrt(a.pos.distanceSq(b.pos));
        }
        int switches = 0, crossings = 0;
        for (RailNode n : net.nodes) {
            if (n.kind == RailNode.Kind.SWITCH) switches++;
            if (n.kind.name().contains("CROSS")) crossings++;
        }
        int locos = 0, cars = 0, moving = 0, pax = 0, cargoCars = 0, cargoSum = 0;
        float top = 0, sum = 0;
        for (TrainNode t : trains) {
            if (t.kind.isLoco()) locos++; else cars++;
            if (t.moving()) { moving++; sum += Math.abs(t.speedKmh); }
            top = Math.max(top, Math.abs(t.speedKmh));
            pax += t.passengers;
            if (t.cargoPct >= 0) { cargoCars++; cargoSum += t.cargoPct; }
        }
        int named = 0;
        for (StopNode s : net.stops) if (s.named) named++;
        long earned = 0;
        for (PacketRailState.TrainInfo t : ClientRailway.get().trains) earned += money(t.money);
        String[][] boxes = {
                {"Track", metres < 1000 ? Math.round(metres) + " m" : String.format(Locale.ROOT, "%.2f km", metres / 1000)},
                {"Junctions", String.valueOf(switches)}, {"Crossings", String.valueOf(crossings)},
                {"Signals", String.valueOf(net.signals.size())}, {"Stations", named + " named / " + net.stops.size()},
                {"Lines", String.valueOf(ClientRailway.get().lines.size())}, {"Routes set", String.valueOf(ClientRailway.get().routes.size())},
                {"Locomotives", String.valueOf(locos)}, {"Cars", String.valueOf(cars)}, {"Moving", moving + " / " + trains.size()},
                {"Avg speed", moving == 0 ? "-" : speed(sum / moving)}, {"Top speed now", speed(top)},
                {"Passengers aboard", String.valueOf(pax)}, {"Avg cargo load", cargoCars == 0 ? "-" : cargoSum / cargoCars + "%"},
                {"Driverless", String.valueOf(ClientRailway.get().trains.size())}, {"Earned", "$" + earned},
                {"Log events", String.valueOf(net.log.size())}, {"Scan radius", net.seedRadius + " blocks" + (net.truncated ? " (cut short)" : "")}};
        int cols = Math.max(3, (w - 12) / 120), bw = (w - 12 - (cols - 1) * 4) / cols, bh = 24;
        int rowsAll = (boxes.length + cols - 1) / cols, fit = Math.max(1, (h - 20) / (bh + 3));
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rowsAll - fit)));
        for (int i = scroll * cols; i < boxes.length; i++) {
            int bx = x + 6 + (i % cols) * (bw + 4), by = y + 18 + (i / cols - scroll) * (bh + 3);
            if (by + bh > y + h) break;
            Gui.drawRect(bx, by, bx + bw, by + bh, 0xFF181226);
            Gui.drawRect(bx, by, bx + 2, by + bh, PrideFrame.RAINBOW[i % PrideFrame.RAINBOW.length]);
            fr.drawString(fr.trimStringToWidth(boxes[i][0], bw - 10), bx + 6, by + 3, C_DIM);
            fr.drawStringWithShadow(fr.trimStringToWidth(boxes[i][1], bw - 10), bx + 6, by + 13, 0xFFFFFFFF);
        }
        rowsTotal = rowsAll;
        rowsVisible = fit;
    }

    // ------------------------------------------------------------------ EVENTS
    private void events(FontRenderer fr, RailNetwork net, int mx, int my) {
        List<LogEntry> list = new ArrayList<>();
        for (int i = net.log.size() - 1; i >= 0; i--) {
            LogEntry e = net.log.get(i);
            if (filter == 1 && !e.arrive || filter == 2 && e.arrive) continue;
            list.add(e);
        }
        header(fr, "Events", net.log.size() + " logged arrivals and departures (newest first)");
        int cy = chips(fr, new String[]{"All", "Arrivals", "Departures"}, y + 16, mx, my);
        int[] cx = scaled(0, 18, 30, 60);
        int ry = columns(fr, new String[]{"Time", "Event", "Train", "Station"}, cx, cy, mx, my);
        long now = Minecraft.getMinecraft().world == null ? 0 : Minecraft.getMinecraft().world.getTotalWorldTime();
        int i = 0;
        for (LogEntry e : page(list, ry)) {
            if (i++ % 2 == 0) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, C_ROW);
            long ago = Math.max(0, (now - e.time) / 20);
            cell(fr, ago < 60 ? ago + "s ago" : ago < 3600 ? ago / 60 + "m ago" : ago / 3600 + "h ago", cx, 0, ry, C_DIM);
            cell(fr, e.arrive ? "ARRIVED" : "DEPARTED", cx, 1, ry, e.arrive ? C_GOOD : C_WARN);
            cell(fr, e.train, cx, 2, ry, C_TEXT);
            cell(fr, e.station, cx, 3, ry, C_TEXT);
            ry += ROW;
        }
    }

    // ------------------------------------------------------------------ SETTINGS
    private void settings(FontRenderer fr, int mx, int my) {
        header(fr, "Settings", "board options (this session)");
        String[][] rows = {
                {"Speed units", mph ? "mph" : "km/h"}, {"Map labels", showLabels ? "ON" : "off"}, {"Map grid", showGrid ? "ON" : "off"},
                {"Train names on the map", showTrainNames ? "ON" : "off"}, {"Flash maintenance alarms", flashAlarms ? "ON" : "off"},
                {"Wear alarm: lower (-5%)", wearAlarm + "%"}, {"Wear alarm: raise (+5%)", wearAlarm + "%"},
                {"Board refresh", new String[]{"live", "every 2 s", "every 5 s"}[refreshSlow]}};
        int per = Math.max(1, (h - 22) / 16), colW = (w - 16) / ((rows.length + per - 1) / per);
        colW = Math.min(colW, 300);
        for (int i = 0; i < rows.length; i++) {
            int rx = x + 6 + (i / per) * (colW + 4), ry = y + 20 + (i % per) * 16;
            boolean over = mx >= rx && mx < rx + colW && my >= ry && my < ry + 14;
            Gui.drawRect(rx, ry, rx + colW, ry + 14, over ? 0xFF2A2240 : 0xFF181226);
            fr.drawString(fr.trimStringToWidth(rows[i][0], colW - 50), rx + 5, ry + 3, C_TEXT);
            fr.drawString(rows[i][1], rx + colW - 5 - fr.getStringWidth(rows[i][1]), ry + 3, C_GOOD);
            hits.add(new int[]{rx, ry, colW, 14, 2000 + i});
        }
        rowsTotal = rowsVisible = 0;
    }
}
