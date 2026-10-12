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
        POWER("Power", "electric traction"), PROTECT("Protect", "protection and grid automation: relays, breakers, transformer derating, UPS, transfer switches, black start, the last protection study and the grid's log books"), WIRED("Wired", "the 12 V control bus: every machine and piece of gear wired to this desk, live, with its real controls"), FREIGHT("Freight", "freight contracts: what the industries need moved, who is hauling it, deadlines and pay"), COMPANY("Company", "railway companies: accounts, profit, reputation, share price, fleet (run them at a Company HQ)"), MARKET("Market", "commodity prices: supply, demand, 24-hour price history (trade at a Commodity Exchange)"), PAX("Pax", "passengers: demand, waiting, satisfaction per station and line, containers in the yards"), ALARMS("Alarms", "the alarm manager: every alarm, new / standing / cleared, acknowledge, shelve, silence, reset"), SAFETY("Safety", "signalling safety: train tracking + describer, radio log, axle counters, held signals, sighting, self-tests, fail-safe modes, route safety case, fault lab"), OPS("Ops", "incident command, alarm root causes, train recorders, work orders, announcements, KPIs, digital twin, reports, scenario lab"), ENV("OLE/Wx", "overhead line sections, feeder breakers, isolators, pantographs, the energy ledger + tariff, and the railway weather: stations, speed restrictions, snow work orders"), TRACK("Track", "track infrastructure: the track-quality overlay + TSRs, point machines, bridges and tunnels as assets, tunnel ventilation, level crossings, surveyed routes"), REPLAY("Rewind", "the event recorder: rewind the last 15 minutes and watch the trains"), MAINT("Depot", "depots and rolling stock: fleet condition + restrictions, job cards, line allocation + spares, crew duty, diagnostic downloads, station accessibility + facility costs"), ASSETS("Assets", "assets + maintenance: the infrastructure asset register and lifecycle, defects from discovery to repair, fleet health trends, transformer thermal model, spares + purchase orders, certificates"), FINANCE("Money", "finance: income per train"),
        API("API", "the railway platform API: mod compatibility, every mod's vehicles, program watchdog + logic modules, train orders, objectives, audit log, export"),
        PLAN("Plan", "operations planning: digital twin + shadow simulation, what-if, timetable robustness, network resilience, temporary works, stock compatibility, split / join, yard planner, config versions, AI dispatchers, trends"),
        WORLD("World", "world + business: construction projects, capacity auctions, towns, passenger journeys, ports + cargo custody, environment, research, insurance, regulator, deals, stock exchange, trophies, PSO, scenarios"),
        CTRL("Ctrl", "grid, control + comms: harmonic spectrum + filters, microgrids, production plan, comms trust (STALE data), passenger information feed, root-cause graph, your dashboard, the visual rule designer, approvals, chaos tests, tunnel air, GIS"),
        STN("Stn", "station operations + cargo care: reefers + cold chain, container tracking, washout, axle weighbridge, platform crowds, zones, door safety, opening hours, formation by demand, service diagrams, interchanges, emergency power, lost property + parcels, disruption"),
        CCTV("CCTV", "CCTV: every camera live (or its server snapshot), status, network, recording, motion + tamper events, recorders and monitors"),
        STATS("Stats", "statistics: the whole network in numbers"), EVENTS("Log", "events: arrivals and departures"), SETTINGS("Setup", "settings: board options");

        final String label, help;

        Tab(String label, String help) { this.label = label; this.help = help; }
    }

    static final int TAB_H = 15;
    private static final int ROW = 11, HEAD = 13;
    private static final int C_TEXT = 0xFFE8ECF0, C_DIM = 0xFF8A94A0, C_HEAD = 0xFF5BCEFA, C_GOOD = 0xFF7CF29A,
            C_WARN = 0xFFFFC44D, C_BAD = 0xFFFF5252, C_ROW = 0x22FFFFFF, C_SEL = 0x553A6FD8, C_PANEL = 0xFF0C0816;

    Tab tab = Tab.MAP;
    private final SafetyPage safety = new SafetyPage();
    private final DepotPage depot = new DepotPage();          // DEPOT tab (depot/, station/)
    private final AssetPage assets = new AssetPage();         // ASSETS tab (asset/, 48-next list)
    private final TrackPage track = new TrackPage();
    private final IncidentPage ops = new IncidentPage();
    private final OhlePage env = new OhlePage();
    private final ApiPage api = new ApiPage();                // API tab (api/, logic/, #96-105)
    private final CtlPage ctrl = new CtlPage();               // CTRL tab (ctl/, next-48 grid / control / comms)
    private final PlanPage plan = new PlanPage();
    private final StnPage stnPage = new StnPage();
    private final CctvPage cctvPage = new CctvPage();       // CCTV tab (cctv/)
    private final BizPage biz = new BizPage();                // WORLD tab (biz/, next-48 #11-15 17 18 32-38 47)             // PLAN tab (planning/, next-48 #1 3 4 6 7 8 9 40 42 46 48)
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
                case WIRED -> wired(fr, mx, my);
                case FREIGHT -> freight(fr, mx, my);
                case COMPANY -> companies(fr, mx, my);
                case MARKET -> market(fr, mx, my);
                case PAX -> passengers(fr, mx, my);
                case ALARMS -> alarms(fr, mx, my);
                case SAFETY -> safety.draw(fr, x, y, w, h, mx, my);
                case TRACK -> { track.draw(fr, x, y, w, h, mx, my); if (!track.hint.isEmpty()) hint = track.hint; }
                case OPS -> ops.draw(fr, x, y, w, h, mx, my);
                case ENV -> env.draw(fr, x, y, w, h, mx, my);
                case API -> api.draw(fr, x, y, w, h, mx, my);
                case CTRL -> { ctrl.board = board; ctrl.draw(fr, x, y, w, h, mx, my); if (!ctrl.hint.isEmpty()) hint = ctrl.hint; }
                case PLAN -> { plan.draw(fr, x, y, w, h, mx, my); if (!plan.hint.isEmpty()) hint = plan.hint; }
                case WORLD -> biz.draw(fr, x, y, w, h, mx, my);
                case STN -> { stnPage.draw(fr, x, y, w, h, mx, my); if (!stnPage.hint.isEmpty()) hint = stnPage.hint; }
                case CCTV -> { cctvPage.draw(fr, x, y, w, h, mx, my); if (!cctvPage.hint.isEmpty()) hint = cctvPage.hint; }
                case REPLAY -> replay(fr, net, mx, my);
                case MAINT -> depot.draw(fr, x, y, w, h, mx, my);
                case ASSETS -> assets.draw(fr, x, y, w, h, mx, my);
                case FINANCE -> finance(fr, mx, my);
                case STATS -> stats(fr, net, trains);
                case EVENTS -> events(fr, net, mx, my);
                case PROTECT -> protect(fr, mx, my);
                case SETTINGS -> settings(fr, mx, my);
                default -> { }
            }
        } finally { PrideFrame.unclip(); }
        if (rowsTotal > rowsVisible) PrideFrame.scrollbar(x + w - 4, y + HEAD + 18, rowsVisible * ROW, scroll, rowsVisible, rowsTotal);
    }

    /** mouse button up (drag-and-drop on the CTRL tab's dashboard + rule designer) */
    void release(int mx, int my) { if (tab == Tab.CTRL) ctrl.release(mx, my); }

    void wheel(int dir) { if (tab == Tab.CCTV) { cctvPage.wheel(dir); return; } if (tab == Tab.STN) { stnPage.wheel(dir); return; } if (tab == Tab.CTRL) { ctrl.wheel(dir); return; } if (tab == Tab.WORLD) { biz.wheel(dir); return; } if (tab == Tab.ASSETS) { assets.wheel(dir); return; } if (tab == Tab.MAINT) { depot.wheel(dir); return; } if (tab == Tab.SAFETY) { safety.wheel(dir); return; } if (tab == Tab.TRACK) { track.wheel(dir); return; } if (tab == Tab.OPS) { ops.wheel(dir); return; } if (tab == Tab.ENV) { env.wheel(dir); return; } if (tab == Tab.API) { api.wheel(dir); return; } if (tab == Tab.PLAN) { plan.wheel(dir); return; } scroll = Math.max(0, Math.min(Math.max(0, rowsTotal - rowsVisible), scroll + dir)); }

    /** typed keys for a page with a command line (PLAN); true = taken */
    boolean keyTyped(char c, int key) { return tab == Tab.PLAN && plan.keyTyped(c, key); }

    /** returns true when handled */
    boolean click(int mx, int my) {
        if (tab == Tab.SAFETY && safety.click(mx, my)) return true;
        if (tab == Tab.MAINT && depot.click(mx, my)) return true;
        if (tab == Tab.ASSETS && assets.click(mx, my)) return true;
        if (tab == Tab.TRACK && track.click(mx, my)) return true;
        if (tab == Tab.OPS && ops.click(mx, my)) return true;
        if (tab == Tab.ENV && env.click(mx, my)) return true;
        if (tab == Tab.API && api.click(mx, my)) return true;
        if (tab == Tab.CTRL && ctrl.click(mx, my)) return true;
        if (tab == Tab.PLAN && plan.click(mx, my)) return true;
        if (tab == Tab.WORLD && biz.click(mx, my)) return true;
        if (tab == Tab.STN && stnPage.click(mx, my)) return true;
        if (tab == Tab.CCTV && cctvPage.click(mx, my)) return true;
        for (int i = hits.size() - 1; i >= 0; i--) {
            int[] b = hits.get(i);
            if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) { act(b[4]); return true; }
        }
        return false;
    }

    // actions: 0..99 sort column, 100..199 filter, 1000+id pick train, 2000.. settings
    private void act(int a) {
        if (a >= 20000 && a < 21000) { econSel = a - 20000; return; }
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
        else if (a >= 4000 && a < 5000) wiredSel = wiredRows.get(a - 4000);
        else if (a >= 5000 && a < 6000 && board != null && wiredSel != 0)
            com.dogpound.railmap.RailMap.NETWORK.sendToServer(com.dogpound.railmap.network.PacketWired.press(board, BlockPos.fromLong(wiredSel), wiredCtl.get(a - 5000)));
        else if (a >= 8000 && a < 9000) replayAct(a - 8000);
        else if (a >= 9000 && a < 10000) { replayT = a - 9000; replayPlay = false; }
        else if (a >= 6000 && a < 6100) com.dogpound.railmap.RailMap.NETWORK.sendToServer(new com.dogpound.railmap.network.PacketAlarmOp(a - 6000, alarmSel));
        else if (a >= 7000 && a < 8000) alarmSel = alarmRows.get(a - 7000);
        else if (a >= 3000) pickedTrain = a - 3000;    // finance row -> train id
    }

    // ------------------------------------------------------------------ REPLAY (the event recorder)
    private long replayAsked;
    /** position in the buffer, 0..999 (per mille of the recorded span); -1 = live */
    private int replayT = -1;
    private boolean replayPlay;
    private int replaySpeed = 1;
    private long replayLast;

    private void replayAct(int k) {
        switch (k) {
            case 0 -> { replayT = 0; replayPlay = false; }
            case 1 -> replayT = Math.max(0, (replayT < 0 ? 999 : replayT) - 25);
            case 2 -> { if (replayT < 0) replayT = 0; replayPlay = !replayPlay; }
            case 3 -> replayT = Math.min(999, (replayT < 0 ? 999 : replayT) + 25);
            case 4 -> { replayT = -1; replayPlay = false; }
            case 5 -> replaySpeed = replaySpeed == 1 ? 4 : replaySpeed == 4 ? 16 : 1;
            case 6 -> replayAsked = 0;
            default -> { }
        }
    }

    private void replay(FontRenderer fr, RailNetwork net, int mx, int my) {
        long ms = System.currentTimeMillis();
        if (ms - replayAsked > (replayT < 0 ? 2000 : 15000)) { replayAsked = ms; com.dogpound.railmap.RailMap.NETWORK.sendToServer(com.dogpound.railmap.network.PacketReplay.request()); }
        com.dogpound.railmap.network.PacketReplay d = com.dogpound.railmap.network.PacketReplay.last;
        if (d == null || d.sampleTicks.isEmpty()) {
            header(fr, "Replay", d == null ? "asking the recorder…" : d.recording ? "recording - nothing in the buffer yet" : "§cno Event Recorder running§r - place one and give it 12 V DC");
            return;
        }
        int n = d.sampleTicks.size();
        long t0 = d.sampleTicks.get(0)[0], t1 = d.sampleTicks.get(n - 1)[0];
        if (replayPlay && replayT >= 0) {
            long dt = ms - replayLast;
            double span = Math.max(1, t1 - t0) / 20.0;                     // seconds recorded
            replayT = (int) Math.min(999, replayT + dt / 1000.0 * replaySpeed / span * 999);
            if (replayT >= 999) replayPlay = false;
        }
        replayLast = ms;
        long at = replayT < 0 ? t1 : t0 + (t1 - t0) * replayT / 999;
        int si = 0;
        for (int i = 0; i < n; i++) if (d.sampleTicks.get(i)[0] <= at) si = i;
        int sj = Math.min(n - 1, si + 1);
        long ta = d.sampleTicks.get(si)[0], tb = d.sampleTicks.get(sj)[0];
        double f = tb > ta ? Math.max(0, Math.min(1, (at - ta) / (double) (tb - ta))) : 0;
        long ago = (d.now - at) / 20;
        header(fr, "Replay", (replayT < 0 ? "§aLIVE§r" : (replayPlay ? "§e▶ " + replaySpeed + "x§r" : "§7paused§r") + " · " + (ago / 60) + " min " + (ago % 60) + " s ago")
                + " · " + (d.recording ? "recording" : "§crecorder off§r") + " · " + n + " samples, " + (t1 - t0) / 1200 + " min");
        // ---- plot area
        int px = x + 6, py = y + 18, pw = w * 70 / 100, ph = h - 52;
        Gui.drawRect(px, py, px + pw, py + ph, 0xFF07090C);
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        if (net != null && !net.nodes.isEmpty()) { minX = net.minX; maxX = net.maxX; minZ = net.minZ; maxZ = net.maxZ; }
        for (float[][] s : d.sampleTrains) for (float[] tr : s) { minX = Math.min(minX, tr[0]); maxX = Math.max(maxX, tr[0]); minZ = Math.min(minZ, tr[1]); maxZ = Math.max(maxZ, tr[1]); }
        if (minX > maxX) { minX = -50; maxX = 50; minZ = -50; maxZ = 50; }
        double pad = 12, sx = (pw - 2 * pad) / Math.max(1, maxX - minX), sz = (ph - 2 * pad) / Math.max(1, maxZ - minZ), sc = Math.min(sx, sz);
        final double fMinX = minX, fMinZ = minZ;
        java.util.function.DoubleUnaryOperator X = v -> px + pad + (v - fMinX) * sc, Z = v -> py + pad + (v - fMinZ) * sc;
        if (net != null) {
            java.util.Map<Integer, RailNode> byId = new java.util.HashMap<>();
            for (RailNode rn : net.nodes) byId.put(rn.id, rn);
            for (RailSegment s : net.segments) {
                RailNode a = byId.get(s.a()), b = byId.get(s.b());
                if (a == null || b == null) continue;
                line(X.applyAsDouble(a.pos.getX()), Z.applyAsDouble(a.pos.getZ()), X.applyAsDouble(b.pos.getX()), Z.applyAsDouble(b.pos.getZ()), 0xFF3A4450);
            }
        }
        float[][] A = d.sampleTrains.get(si), B = d.sampleTrains.get(sj);
        String[] names = d.sampleNames.get(si);
        for (int k = 0; k < A.length; k++) {
            float[] a = A[k], b = a;
            for (float[] c : B) if (c[3] == a[3]) b = c;
            double tx = a[0] + (b[0] - a[0]) * f, tz = a[1] + (b[1] - a[1]) * f;
            int ix = (int) X.applyAsDouble(tx), iz = (int) Z.applyAsDouble(tz);
            int col = Math.abs(a[2]) < 1 ? 0xFFFF5252 : Math.abs(a[2]) < 30 ? 0xFFFFC44D : 0xFF7CF29A;
            Gui.drawRect(ix - 2, iz - 2, ix + 3, iz + 3, col);
            fr.drawString(fr.trimStringToWidth(names[k], 70) + " " + Math.round(Math.abs(a[2])), ix + 4, iz - 4, 0xFFE8ECF0);
        }
        // ---- timeline + transport buttons
        int ty = py + ph + 6, tw = pw;
        Gui.drawRect(px, ty, px + tw, ty + 6, 0xFF2A2240);
        int cur = (int) (tw * (replayT < 0 ? 1.0 : replayT / 999.0));
        Gui.drawRect(px, ty, px + cur, ty + 6, 0xFF5BCEFA);
        for (Object[] e : d.events) {                                       // event ticks on the timeline
            long et = (Long) e[0];
            if (et < t0 || et > t1) continue;
            int ex = px + (int) (tw * (et - t0) / (double) Math.max(1, t1 - t0));
            Gui.drawRect(ex, ty - 2, ex + 1, ty + 8, ((String) e[1]).startsWith("BOOKMARK") ? 0xFFF0D020 : 0xFFFF5252);
        }
        Gui.drawRect(px + cur - 1, ty - 3, px + cur + 2, ty + 9, 0xFFFFFFFF);
        for (int i = 0; i < 40; i++) hits.add(new int[]{px + tw * i / 40, ty - 3, tw / 40 + 1, 12, 9000 + i * 999 / 39});
        String[] btn = {"|<", "<<", replayPlay ? "PAUSE" : "PLAY", ">>", "LIVE", replaySpeed + "x", "REFRESH"};
        int bx = px;
        for (int i = 0; i < btn.length; i++) {
            int bw = fr.getStringWidth(btn[i]) + 10;
            boolean over = mx >= bx && mx < bx + bw && my >= ty + 12 && my < ty + 24;
            Gui.drawRect(bx, ty + 12, bx + bw, ty + 24, over ? 0xFF5A3A80 : 0xFF2A2240);
            fr.drawString(btn[i], bx + 5, ty + 14, 0xFFFFFFFF);
            hits.add(new int[]{bx, ty + 12, bw, 12, 8000 + i});
            bx += bw + 3;
        }
        // ---- events around this moment
        int ex0 = px + pw + 8, ey = py;
        fr.drawString("EVENTS", ex0, ey, C_HEAD);
        ey += 11;
        for (int i = d.events.size() - 1; i >= 0 && ey < y + h - 10; i--) {
            Object[] e = d.events.get(i);
            long et = (Long) e[0];
            boolean near = Math.abs(et - at) < 600;
            long eago = (d.now - et) / 20;
            fr.drawString(fr.trimStringToWidth((eago / 60) + "m" + (eago % 60) + "s " + e[1], x + w - ex0 - 8), ex0, ey, near ? 0xFFFFE066 : et > at ? 0xFF606870 : C_DIM);
            ey += 10;
        }
    }

    /** a thin line from (x0, y0) to (x1, y1) made of small rects */
    private static void line(double x0, double y0, double x1, double y1, int col) {
        int n = (int) Math.max(1, Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)));
        for (int i = 0; i <= n; i += 1) {
            int xx = (int) (x0 + (x1 - x0) * i / n), yy = (int) (y0 + (y1 - y0) * i / n);
            Gui.drawRect(xx, yy, xx + 1, yy + 1, col);
        }
    }

    // ------------------------------------------------------------------ ALARMS (the alarm manager)
    private int alarmSel;
    private final List<Integer> alarmRows = new ArrayList<>();

    private void alarms(FontRenderer fr, int mx, int my) {
        List<String[]> all = ClientRailway.get().alarms;
        int nNew = 0, nStd = 0, nClr = 0;
        for (String[] a : all) switch (a[1]) { case "1" -> nNew++; case "2" -> nStd++; case "3" -> nClr++; default -> { } }
        header(fr, "Alarms", (nNew > 0 ? "§c" + nNew + " NEW§r" : "0 new") + " · " + nStd + " standing · " + nClr + " cleared (reset) · click a row to select");
        int cy = chips(fr, new String[]{"Live", "New", "Standing", "Cleared", "History"}, y + 16, mx, my);
        // action buttons on the chip row, right side
        String[] acts = {"ACK", "ACK ALL", "SILENCE", "RESET", "SHELVE 1h"};
        int[] ops = {0, 1, 2, 3, 4};
        int bx = x + w - 8;
        for (int i = acts.length - 1; i >= 0; i--) {
            int bw = fr.getStringWidth(acts[i]) + 10;
            bx -= bw + 3;
            boolean over = mx >= bx && mx < bx + bw && my >= y + 16 && my < y + 27;
            Gui.drawRect(bx, y + 16, bx + bw, y + 27, over ? 0xFF5A3A80 : i == 0 || i == 4 ? 0xFF2A2240 : 0xFF3A2440);
            fr.drawString(acts[i], bx + 5, y + 18, 0xFFFFFFFF);
            hits.add(new int[]{bx, y + 16, bw, 11, 6000 + ops[i]});
        }
        List<String[]> rows = new ArrayList<>();
        for (String[] a : all) {
            String s = a[1];
            if (filter == 0 && s.equals("0") || filter == 1 && !s.equals("1") || filter == 2 && !s.equals("2") || filter == 3 && !s.equals("3")) continue;
            rows.add(a);
        }
        String[] cols = {"", "#", "Sev", "Category", "Alarm", "x", "Ago", "Ack"};
        int[] cx = scaled(0, 3, 8, 17, 33, 86, 90, 95);
        int ry = columns(fr, cols, cx, cy, mx, my);
        alarmRows.clear();
        long t = System.currentTimeMillis();
        boolean fast = (t / 200) % 2 == 0, slow = (t / 700) % 2 == 0;
        if (rows.isEmpty()) fr.drawString(filter == 0 ? "All normal - no live alarms" : "Nothing here", x + 6, ry + 2, C_GOOD);
        int i = 0;
        for (String[] a : page(rows, ry)) {
            int id = Integer.parseInt(a[0]);
            boolean over = mx >= x && mx < x + w - 6 && my >= ry && my < ry + ROW;
            if (i++ % 2 == 0) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, C_ROW);
            if (over || id == alarmSel) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, C_SEL);
            int sevCol = switch (a[2]) { case "CRITICAL" -> 0xFFFF3A3A; case "MAJOR" -> 0xFFFFB020; case "MINOR" -> 0xFFFFE066; default -> 0xFF8AD4FF; };
            boolean lit = a[1].equals("2") || a[1].equals("1") && fast || a[1].equals("3") && slow;
            Gui.drawRect(x + cx[0] + 1, ry + 1, x + cx[0] + 8, ry + 8, a[1].equals("0") ? 0xFF303030 : lit ? sevCol : 0xFF402020);
            cell(fr, a[0], cx, 1, ry, C_DIM);
            cell(fr, a[2], cx, 2, ry, sevCol);
            cell(fr, a[3], cx, 3, ry, C_TEXT);
            cell(fr, a[4], cx, 4, ry, a[1].equals("0") ? C_DIM : C_TEXT);
            cell(fr, a[5].equals("1") ? "" : a[5], cx, 5, ry, C_DIM);
            cell(fr, a[6] + "m", cx, 6, ry, C_DIM);
            cell(fr, a[7], cx, 7, ry, C_DIM);
            hits.add(new int[]{x + 2, ry - 1, w - 8, ROW, 7000 + alarmRows.size()});
            alarmRows.add(id);
            ry += ROW;
        }
    }

    // ------------------------------------------------------------------ COMPANY / MARKET / PAX (her list #10, #46-50)
    private int econSel = -1;

    /** FREIGHT tab, PLANTS view (her list section 13): every factory / depot store / fuel point / construction site the
     *  railway serves - its state, what it has ready, what it has on order, its grid draw and supply (shed / PQ trips) */
    private void plants(FontRenderer fr, int cy, int mx, int my) {
        List<String[]> rows = econ("in");
        double kw = 0;
        for (String[] r : rows) try { kw += r[5].isEmpty() ? 0 : Double.parseDouble(r[5]); } catch (NumberFormatException ignored) { }
        fr.drawString(rows.size() + " plants · industrial load " + Math.round(kw) + " kW (shed first when the grid sags)", x + 6, cy, C_DIM);
        cy += 11;
        String[] cols = {"Plant", "State", "Ready to ship", "Short of (orders)", "kW", "Supply", "Where"};
        int[] cx = scaled(0, 18, 30, 52, 74, 80, 93);
        int ry = columns(fr, cols, cx, cy, mx, my);
        if (rows.isEmpty()) fr.drawString("No plants yet - industries, factory plants, depot stores, fuel points and construction sites show here", x + 6, ry + 2, C_DIM);
        int i = 0;
        for (String[] r : page(rows, ry)) {
            if (r.length < 8) continue;
            if (i++ % 2 == 0) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, C_ROW);
            String st = r[2];
            int col = st.contains("FAULT") || st.contains("TRIP") || st.contains("SHED") || st.contains("NO ") || st.contains("LOW") ? C_BAD
                    : st.contains("RUN") || st.contains("OK") || st.contains("STOCKED") || st.contains("DONE") ? C_GOOD : C_WARN;
            cell(fr, r[1], cx, 0, ry, C_TEXT);
            cell(fr, st + (r.length > 8 && !r[8].isEmpty() ? " · batch " + r[8] + "s" : ""), cx, 1, ry, col);
            cell(fr, r[3].isEmpty() ? "-" : r[3], cx, 2, ry, C_DIM);
            cell(fr, r[4].isEmpty() ? "-" : r[4], cx, 3, ry, r[4].isEmpty() ? C_DIM : C_WARN);
            cell(fr, r[5].isEmpty() ? "-" : r[5], cx, 4, ry, C_TEXT);
            cell(fr, r[6].isEmpty() ? "-" : r[6], cx, 5, ry, r[6].startsWith("SHED") ? C_BAD : C_DIM);
            cell(fr, r[7], cx, 6, ry, C_DIM);
            ry += ROW;
        }
    }

    private static List<String[]> econ(String tag) {
        List<String[]> out = new ArrayList<>();
        for (String[] r : ClientRailway.get().economy) if (r.length > 1 && r[0].equals(tag)) out.add(r);
        return out;
    }

    private static int num(String s) { try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 0; } }

    private void companies(FontRenderer fr, int mx, int my) {
        List<String[]> all = econ("co");
        header(fr, "Companies", all.size() + " railway companies · found or run one at a Company HQ · /irextras company");
        String[] cols = {"Company", "Staff", "Operating", "Maint.", "Month P/L", "Last month", "Rep", "Share", "Fleet", "Loan"};
        int[] cx = scaled(0, 22, 28, 39, 49, 59, 69, 78, 86, 94);
        int ry = columns(fr, cols, cx, y + 18, mx, my);
        if (all.isEmpty()) fr.drawString("No companies yet. Place a Company HQ and press FOUND, then claim lines, trains and stations on its FLEET board.", x + 6, ry + 2, C_DIM);
        int i = 0;
        for (String[] c : page(all, ry)) {
            if (i++ % 2 == 0) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, C_ROW);
            Gui.drawRect(x + cx[0], ry, x + cx[0] + 6, ry + 7, 0xFF000000 | num(c[16]));
            cell(fr, "  " + c[2] + " (" + c[3] + ")" + (num(c[15]) > 0 ? " §e+" + c[15] + " applying" : ""), cx, 0, ry, C_TEXT);
            cell(fr, c[4], cx, 1, ry, C_TEXT);
            cell(fr, c[5], cx, 2, ry, 0xFFFFD86B);
            cell(fr, c[6], cx, 3, ry, C_GOOD);
            long pl = 0;
            try { pl = Long.parseLong(c[17]); } catch (NumberFormatException ignored) { }
            cell(fr, c[7], cx, 4, ry, pl >= 0 ? C_GOOD : C_BAD);
            cell(fr, c[8], cx, 5, ry, C_DIM);
            int rep = num(c[9]);
            bar(x + cx[6], ry + 1, cx[7] - cx[6] - 18, rep / 100f, rep >= 60 ? 0xFF7CF29A : rep >= 40 ? 0xFFFFC44D : 0xFFFF5252);
            fr.drawString(String.valueOf(rep), x + cx[7] - 16, ry, C_DIM);
            cell(fr, c[10], cx, 7, ry, 0xFF8AD4FF);
            cell(fr, c[11] + "T " + c[12] + "L " + c[13] + "S", cx, 8, ry, C_TEXT);
            cell(fr, c[14], cx, 9, ry, c[14].equals("$0.00") ? C_DIM : C_WARN);
            ry += ROW;
        }
    }

    private void market(FontRenderer fr, int mx, int my) {
        List<String[]> all = econ("px");
        int up = 0, down = 0;
        for (String[] p : all) { int c = num(p[5]); if (c > 0) up++; else if (c < 0) down++; }
        header(fr, "Market", all.size() + " commodities · §a" + up + " up§r · §c" + down + " down§r · prices move hourly with supply and demand · click a row for its chart");
        String[] cols = {"Commodity", "Price", "24h", "Bid / Ask", "Supply", "Demand", "Traded", "24 hours"};
        int[] cx = scaled(0, 24, 33, 41, 56, 64, 72, 80);
        int ry = columns(fr, cols, cx, y + 18, mx, my);
        int chartH = econSel >= 0 && econSel < all.size() ? 60 : 0;
        int i = 0, top = ry;
        List<String[]> shown = all;
        rowsVisible = rowsTotal = 0;                                            // no scrolling: the board fits a screen
        for (int k = 0; k < shown.size(); k++) {
            String[] p = shown.get(k);
            if (chartH > 0 && ry > y + h - chartH - 10 - ROW) break;
            if (chartH == 0 && ry > y + h - ROW - 4) break;
            if (i++ % 2 == 0 || k == econSel) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, k == econSel ? C_SEL : C_ROW);
            int ch = num(p[5]);
            cell(fr, p[2], cx, 0, ry, C_TEXT);
            cell(fr, p[3], cx, 1, ry, 0xFFFFD86B);
            cell(fr, String.format(Locale.ROOT, "%s%.1f%%", ch > 0 ? "▲" : ch < 0 ? "▼" : " ", Math.abs(ch) / 10.0), cx, 2, ry, ch > 0 ? C_GOOD : ch < 0 ? C_BAD : C_DIM);
            cell(fr, p[6] + " / " + p[7], cx, 3, ry, C_DIM);
            cell(fr, p[8], cx, 4, ry, C_TEXT);
            cell(fr, p[9], cx, 5, ry, C_TEXT);
            cell(fr, p[10], cx, 6, ry, C_DIM);
            spark(p[11], x + cx[7], ry, w - 10 - cx[7], ROW - 2, ch >= 0 ? 0xFF7CF29A : 0xFFFF5252);
            hits.add(new int[]{x, ry - 1, w - 6, ROW, 20000 + k});
            ry += ROW;
        }
        if (all.isEmpty()) fr.drawString("The market opens once the world has ticked an hour.", x + 6, top + 2, C_DIM);
        if (chartH > 0) {
            String[] p = all.get(econSel);
            int cy = y + h - chartH - 4;
            Gui.drawRect(x + 4, cy, x + w - 8, y + h - 4, 0xFF140E22);
            fr.drawString(p[2] + "  " + p[3] + "  (base " + p[4] + ")  last 24 hours", x + 8, cy + 2, 0xFFF5A9B8);
            spark(p[11], x + 8, cy + 13, w - 20, chartH - 18, 0xFF5BCEFA);
        }
    }

    /** a little line chart of comma-separated cents, drawn as columns */
    private void spark(String csv, int sx, int sy, int sw, int sh, int col) {
        String[] v = csv.split(",");
        if (v.length < 2 || sw < 10) return;
        long lo = Long.MAX_VALUE, hi = Long.MIN_VALUE;
        long[] n = new long[v.length];
        for (int i = 0; i < v.length; i++) { try { n[i] = Long.parseLong(v[i]); } catch (NumberFormatException e) { n[i] = 0; } lo = Math.min(lo, n[i]); hi = Math.max(hi, n[i]); }
        int bw = Math.max(1, sw / n.length);
        for (int i = 0; i < n.length; i++) {
            int bh = hi == lo ? sh / 2 : (int) (1 + (n[i] - lo) * (sh - 1) / (hi - lo));
            Gui.drawRect(sx + i * bw, sy + sh - bh, sx + i * bw + Math.max(1, bw - 1), sy + sh, i == n.length - 1 ? 0xFFFFFFFF : col);
        }
    }

    private void passengers(FontRenderer fr, int mx, int my) {
        List<String[]> st = econ("ps"), ln = econ("pl"), ct = econ("ct");
        double wait = 0, board = 0;
        for (String[] s : st) { wait += num(s[4]); board += num(s[5]); }
        header(fr, "Passengers", Math.round(wait) + " waiting · " + Math.round(board) + " boarded today · " + st.size() + " stations · " + ln.size() + " lines · " + ct.size() + " containers in yards");
        int cy = chips(fr, new String[]{"Stations", "Lines", "Containers"}, y + 16, mx, my);
        int i = 0;
        if (filter == 0) {
            String[] cols = {"Station", "Zone", "Catchment", "Waiting", "Board/Off", "Satisfied", "On time", "Space", "Facil.", "Clean", "Complaints", "Fares"};
            int[] cx = scaled(0, 17, 22, 29, 36, 45, 58, 65, 71, 77, 83, 93);
            int ry = columns(fr, cols, cx, cy, mx, my);
            if (st.isEmpty()) fr.drawString("Name stations (Station Master's Desk) and put them on lines: passengers appear by time of day, catchment and service.", x + 6, ry + 2, C_DIM);
            for (String[] s : page(st, ry)) {
                if (i++ % 2 == 0) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, C_ROW);
                int sat = num(s[7]);
                cell(fr, s[1], cx, 0, ry, C_TEXT);
                cell(fr, "Z" + s[2], cx, 1, ry, 0xFF8AD4FF);
                cell(fr, s[3], cx, 2, ry, C_DIM);
                cell(fr, s[4] + (num(s[16]) > 0 ? " §c(" + s[16] + " left)" : ""), cx, 3, ry, num(s[4]) > 150 ? C_WARN : C_TEXT);
                cell(fr, s[5] + "/" + s[6], cx, 4, ry, C_TEXT);
                bar(x + cx[5], ry + 1, cx[6] - cx[5] - 20, sat / 100f, sat >= 65 ? 0xFF7CF29A : sat >= 45 ? 0xFFFFC44D : 0xFFFF5252);
                fr.drawString(sat + "%", x + cx[6] - 18, ry, C_DIM);
                cell(fr, s[8] + "%", cx, 6, ry, num(s[8]) >= 90 ? C_GOOD : num(s[8]) >= 60 ? C_WARN : C_BAD);
                cell(fr, s[9] + "%", cx, 7, ry, num(s[9]) >= 75 ? C_GOOD : C_WARN);
                cell(fr, s[11] + "%", cx, 8, ry, C_DIM);
                cell(fr, s[12] + "%", cx, 9, ry, num(s[12]) >= 60 ? C_DIM : C_WARN);
                cell(fr, s[13] + (s[19].isEmpty() ? "" : " " + s[19]), cx, 10, ry, num(s[13]) > 5 ? C_BAD : C_DIM);
                cell(fr, s[14], cx, 11, ry, 0xFFFFD86B);
                ry += ROW;
            }
        } else if (filter == 1) {
            String[] cols = {"Line", "Operator", "Riders", "Yesterday", "Satisfied", "On time", "Load", "Fares / upkeep", "Refunds", "Late arr.", "Left behind"};
            int[] cx = scaled(0, 16, 26, 34, 43, 56, 64, 71, 80, 88, 94);
            int ry = columns(fr, cols, cx, cy, mx, my);
            if (ln.isEmpty()) fr.drawString("No lines yet - make one on the Map tab.", x + 6, ry + 2, C_DIM);
            for (String[] l : page(ln, ry)) {
                if (i++ % 2 == 0) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, C_ROW);
                int sat = num(l[4]);
                cell(fr, l[1], cx, 0, ry, C_TEXT);
                cell(fr, l[9] + (l[10].isEmpty() ? "" : " " + l[10]), cx, 1, ry, l[10].isEmpty() ? C_DIM : 0xFFC08AFF);
                cell(fr, l[2], cx, 2, ry, C_TEXT);
                cell(fr, l[3], cx, 3, ry, C_DIM);
                bar(x + cx[4], ry + 1, cx[5] - cx[4] - 20, sat / 100f, sat >= 65 ? 0xFF7CF29A : sat >= 45 ? 0xFFFFC44D : 0xFFFF5252);
                fr.drawString(sat + "%", x + cx[5] - 18, ry, C_DIM);
                cell(fr, l[5] + "%", cx, 5, ry, num(l[5]) >= 90 ? C_GOOD : num(l[5]) >= 60 ? C_WARN : C_BAD);
                cell(fr, l[6] + "%", cx, 6, ry, num(l[6]) > 95 ? C_BAD : num(l[6]) > 80 ? C_WARN : C_TEXT);
                cell(fr, l[7] + (l.length > 13 ? " §7/ -" + l[13] : ""), cx, 7, ry, 0xFFFFD86B);
                cell(fr, l[8], cx, 8, ry, l[8].equals("$0.00") ? C_DIM : C_BAD);
                cell(fr, l[11], cx, 9, ry, num(l[11]) > 0 ? C_WARN : C_DIM);
                cell(fr, l[12], cx, 10, ry, num(l[12]) > 0 ? C_BAD : C_DIM);
                ry += ROW;
            }
        } else {
            String[] cols = {"Container", "Where", "Destination", "Contents", "Days", "Fees due", "Terminal"};
            int[] cx = scaled(0, 15, 30, 44, 76, 82, 90);
            int ry = columns(fr, cols, cx, cy, mx, my);
            if (ct.isEmpty()) fr.drawString("No containers in any loaded Container Terminal.", x + 6, ry + 2, C_DIM);
            for (String[] b : page(ct, ry)) {
                if (i++ % 2 == 0) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, C_ROW);
                double days = 0;
                try { days = Double.parseDouble(b[5]); } catch (NumberFormatException ignored) { }
                cell(fr, b[1], cx, 0, ry, C_TEXT);
                cell(fr, b[2], cx, 1, ry, C_DIM);
                cell(fr, b[3].isEmpty() ? "-" : b[3], cx, 2, ry, C_TEXT);
                cell(fr, b[4], cx, 3, ry, C_DIM);
                cell(fr, b[5], cx, 4, ry, days > 2 ? C_BAD : C_DIM);
                cell(fr, b[6], cx, 5, ry, b[6].equals("$0.00") ? C_DIM : C_WARN);
                cell(fr, b[7], cx, 6, ry, C_DIM);
                ry += ROW;
            }
        }
    }

    // ------------------------------------------------------------------ PROTECT (grid protection + automation, grid.GridConsole)
    private void protect(FontRenderer fr, int mx, int my) {
        com.dogpound.railmap.network.PacketRailState st = ClientRailway.get();
        List<String[]> all = st.protection;
        int trip = 0, warn = 0, ied = 0;
        for (String[] r : all) { if (r[6].equals("2")) trip++; else if (r[6].equals("1")) warn++; if (r[0].contains("IED")) ied++; }
        header(fr, "Protection", ied + " IEDs · " + (trip > 0 ? "§c" + trip + " in fault§r" : "0 in fault") + " · " + warn + " warnings · laptop = study · multimeter (sneak) = log book");
        int cy = chips(fr, new String[]{"Devices", "Faults", "Last study", "Log books"}, y + 16, mx, my);
        if (filter == 2) {
            List<String> lines = st.study;
            if (lines.isEmpty()) { fr.drawString("No study yet - right-click a grid part with the Protection Engineer's Laptop (sneak = earth fault)", x + 6, cy + 2, C_DIM); return; }
            int ry = cy;
            for (String l : page(lines, cy)) { fr.drawString(fr.trimStringToWidth(l, w - 16), x + 6, ry, C_TEXT); ry += ROW; }
            return;
        }
        if (filter == 3) {
            String[] cols = {"When", "Part", "Where", "Event", "What"};
            int[] cx = scaled(0, 9, 24, 36, 44);
            int ry = columns(fr, cols, cx, cy, mx, my);
            int i = 0;
            if (st.gridLog.isEmpty()) fr.drawString("Nothing logged yet", x + 6, ry + 2, C_DIM);
            for (String[] r : page(st.gridLog, ry)) {
                if (i++ % 2 == 0) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, C_ROW);
                int col = r[3].equals("TRIP") || r[3].equals("FAULT") || r[3].equals("FAIL") || r[3].equals("BURN") ? C_BAD : r[3].equals("PERMIT") || r[3].equals("MAINT") || r[3].equals("REPAIR") ? C_GOOD : C_TEXT;
                cell(fr, r[0], cx, 0, ry, C_DIM); cell(fr, r[1], cx, 1, ry, C_TEXT); cell(fr, r[2], cx, 2, ry, C_DIM); cell(fr, r[3], cx, 3, ry, col); cell(fr, r[4], cx, 4, ry, col);
                ry += ROW;
            }
            return;
        }
        String[] cols = {"Device", "Where", "State", "Reading", "Last event", "Counters"};
        int[] cx = scaled(0, 15, 27, 42, 66, 90);
        int ry = columns(fr, cols, cx, cy, mx, my);
        List<String[]> rows = new ArrayList<>();
        for (String[] r : all) if (filter != 1 || !r[6].equals("0")) rows.add(r);
        if (rows.isEmpty()) fr.drawString(filter == 1 ? "No faults - every protection device healthy" : "No protection gear - place a Protection IED touching a breaker", x + 6, ry + 2, filter == 1 ? C_GOOD : C_DIM);
        int i = 0;
        for (String[] r : page(rows, ry)) {
            if (i++ % 2 == 0) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, C_ROW);
            int col = r[6].equals("2") ? C_BAD : r[6].equals("1") ? C_WARN : C_GOOD;
            Gui.drawRect(x + cx[0] - 3, ry + 1, x + cx[0] - 1, ry + 7, col);
            cell(fr, r[0], cx, 0, ry, C_TEXT); cell(fr, r[1], cx, 1, ry, C_DIM); cell(fr, r[2], cx, 2, ry, col);
            cell(fr, r[3], cx, 3, ry, C_TEXT); cell(fr, r[4], cx, 4, ry, C_DIM); cell(fr, r[5], cx, 5, ry, C_DIM);
            ry += ROW;
        }
    }

    // ------------------------------------------------------------------ FREIGHT (contracts)
    private void freight(FontRenderer fr, int mx, int my) {
        List<String[]> all = ClientRailway.get().contracts;
        int open = 0, run = 0, done = 0, late = 0;
        for (String[] c : all) {
            switch (c[1]) { case "OPEN" -> open++; case "ACCEPTED" -> { run++; if (c[9].startsWith("LATE")) late++; } case "DONE" -> done++; default -> { } }
        }
        header(fr, "Freight", open + " open · " + run + " in progress" + (late > 0 ? " · §c" + late + " LATE§r" : "") + " · " + done + " delivered · accept them at a Freight Office");
        int cy = chips(fr, new String[]{"All", "Open", "In progress", "Delivered", "Failed", "Plants"}, y + 16, mx, my);
        if (filter == 5) { plants(fr, cy, mx, my); return; }
        List<String[]> rows = new ArrayList<>();
        for (String[] c : all) {
            String s = c[1];
            if (filter == 1 && !s.equals("OPEN") || filter == 2 && !s.equals("ACCEPTED") || filter == 3 && !s.equals("DONE")
                    || filter == 4 && !(s.equals("EXPIRED") || s.equals("DECLINED"))) continue;
            rows.add(c);
        }
        String[] cols = {"#", "Goods", "Progress", "From", "To", "Pay", "Due", "Hauler", "State"};
        int[] cx = scaled(0, 5, 21, 37, 55, 73, 81, 88, 95);
        int ry = columns(fr, cols, cx, cy, mx, my);
        int i = 0;
        if (rows.isEmpty()) fr.drawString("No contracts yet - industries post them when one makes what another needs (place industries with loaders / unloaders)", x + 6, ry + 2, C_DIM);
        for (String[] c : page(rows, ry)) {
            if (i++ % 2 == 0) Gui.drawRect(x + 2, ry - 1, x + w - 6, ry + ROW - 1, C_ROW);
            String st = c[1];
            boolean isLate = st.equals("ACCEPTED") && c[9].startsWith("LATE");
            int col = st.equals("OPEN") ? C_GOOD : st.equals("ACCEPTED") ? (isLate ? C_BAD : C_WARN) : st.equals("DONE") ? 0xFF8AD4FF : C_DIM;
            cell(fr, c[0], cx, 0, ry, C_TEXT);
            cell(fr, c[3] + " " + c[2], cx, 1, ry, C_TEXT);
            int qty = Math.max(1, Integer.parseInt(c[3])), ld = Integer.parseInt(c[4]), dl = Integer.parseInt(c[5]);
            int bw = cx[3] - cx[2] - 26;
            bar(x + cx[2], ry + 1, bw, ld / (float) qty, 0xFF5BCEFA);           // loaded (billed)
            Gui.drawRect(x + cx[2], ry + 5, x + cx[2] + Math.max(0, Math.min(bw, (int) (bw * dl / (float) qty))), ry + 7, 0xFF7CF29A);   // delivered
            fr.drawString(dl + "/" + qty, x + cx[2] + bw + 2, ry, C_DIM);
            cell(fr, c[6], cx, 3, ry, C_TEXT);
            cell(fr, "→ " + c[7], cx, 4, ry, C_TEXT);
            cell(fr, "$" + c[8], cx, 5, ry, 0xFFFFD86B);
            cell(fr, c[9], cx, 6, ry, isLate ? C_BAD : C_DIM);
            cell(fr, c[10], cx, 7, ry, C_DIM);
            cell(fr, st.equals("ACCEPTED") ? "hauling" : st.toLowerCase(Locale.ROOT), cx, 8, ry, col);
            ry += ROW;
        }
    }

    // ------------------------------------------------------------------ WIRED (the 12 V control bus)
    /** the console this page belongs to (null = a board with no bus) */
    BlockPos board;
    private long wiredSel, wiredAsked;
    private final List<Long> wiredRows = new ArrayList<>();
    private final List<String> wiredCtl = new ArrayList<>();
    private static final String[] T_NAMES = {"PUSH", "MUSHROOM", "SELECTOR", "KEY", "LAMP", "GAUGE", "DIGITS", "LEVER", "FLAG", "SYNCHRO", "LABEL"};

    private void wired(FontRenderer fr, int mx, int my) {
        long now = System.currentTimeMillis();
        if (board != null && now - wiredAsked > 1000) { wiredAsked = now; com.dogpound.railmap.RailMap.NETWORK.sendToServer(com.dogpound.railmap.network.PacketWired.request(board)); }
        net.minecraft.nbt.NBTTagCompound data = board == null ? null : com.dogpound.railmap.network.PacketWired.LISTS.get(board);
        if (board == null) { header(fr, "Wired", "only the Control Center console has a 12 V control bus"); return; }
        if (data == null) { header(fr, "Wired", "reading the bus…"); return; }
        if (data.getBoolean("dead")) {
            header(fr, "Wired", "§cDESK DEAD - no 12 V DC instrument power");
            fr.drawString("Run the 12 V power pair (red + black Instrument Wire) to the desk from a 12 V DC supply", x + 6, y + 20, C_WARN);
            fr.drawString("(a DC-DC converter set to 12 V, with an Earthing Switch on that network).", x + 6, y + 31, C_DIM);
            return;
        }
        net.minecraft.nbt.NBTTagList list = data.getTagList("list", 10);
        List<net.minecraft.nbt.NBTTagCompound> rows = new ArrayList<>();
        int faults = 0;
        for (int i = 0; i < list.tagCount(); i++) {
            net.minecraft.nbt.NBTTagCompound e = list.getCompoundTagAt(i);
            if (e.getInteger("l") == 3) faults++;
            if (filter == 1 && e.getInteger("l") != 3 || filter == 2 && e.getTagList("k", 10).tagCount() == 0) continue;
            rows.add(e);
        }
        header(fr, "Wired", list.tagCount() + " on the bus · " + (faults > 0 ? "§c" + faults + " FAULT" + (faults > 1 ? "S" : "") + "§r" : "no faults") + " · land a colour on any machine to add it");
        int cy = chips(fr, new String[]{"All", "Faults", "Has controls"}, y + 16, mx, my);
        rows.sort(sortCol == 1 ? Comparator.comparingInt((net.minecraft.nbt.NBTTagCompound e) -> e.getInteger("c")) : sortCol == 3 ? Comparator.comparing((net.minecraft.nbt.NBTTagCompound e) -> e.getString("s"))
                : sortCol == 0 ? Comparator.comparingInt((net.minecraft.nbt.NBTTagCompound e) -> -e.getInteger("l")) : Comparator.comparing((net.minecraft.nbt.NBTTagCompound e) -> e.getString("n"), String.CASE_INSENSITIVE_ORDER));
        int listW = w * 52 / 100;
        int[] cx = {6, 22, 40, listW * 62 / 100};
        int ry = columns(fr, new String[]{"●", "Wire", "Machine", "State"}, cx, cy, mx, my);
        wiredRows.clear();
        int i = 0;
        net.minecraft.nbt.NBTTagCompound sel = null;
        for (net.minecraft.nbt.NBTTagCompound e : rows) if (e.getLong("p") == wiredSel) sel = e;
        if (sel == null && !rows.isEmpty()) { sel = rows.get(0); wiredSel = sel.getLong("p"); }
        boolean blink = now / 400 % 2 == 0;
        for (net.minecraft.nbt.NBTTagCompound e : page(rows, ry)) {
            long p = e.getLong("p");
            boolean over = mx >= x && mx < x + listW && my >= ry && my < ry + ROW;
            if (i % 2 == 0) Gui.drawRect(x + 2, ry - 1, x + listW, ry + ROW - 1, C_ROW);
            if (over || p == wiredSel) Gui.drawRect(x + 2, ry - 1, x + listW, ry + ROW - 1, C_SEL);
            int l = e.getInteger("l");
            int lc = l == 1 ? 0xFF2ECC40 : l == 2 ? 0xFFE03030 : l == 3 ? (blink ? 0xFFFFB020 : 0xFF503000) : 0xFF404040;
            Gui.drawRect(x + 8, ry + 1, x + 15, ry + 8, 0xFF101010);
            Gui.drawRect(x + 9, ry + 2, x + 14, ry + 7, lc);
            int c = e.getInteger("c");
            Gui.drawRect(x + 22, ry + 1, x + 30, ry + 8, 0xFF000000 | com.dogpound.railmap.wire.WireNet.rgb(c));
            fr.drawString(fr.trimStringToWidth(e.getString("n"), cx[3] - cx[2] - 3), x + cx[2], ry, C_TEXT);
            fr.drawString(fr.trimStringToWidth(e.getString("s"), listW - cx[3] - 3), x + cx[3], ry, l == 3 ? C_WARN : C_DIM);
            hits.add(new int[]{x + 2, ry - 1, listW - 2, ROW, 4000 + wiredRows.size()});
            wiredRows.add(p);
            ry += ROW;
            i++;
        }
        if (rows.isEmpty()) fr.drawString("Nothing wired yet: right-click a machine or signal with Instrument Wire, lay that colour back here", x + 6, ry + 2, C_DIM);
        // ---- right: the selected machine, its readings and its real controls
        int px = x + listW + 6, pw = w - listW - 14, py = cy;
        Gui.drawRect(px - 2, py - 1, px + pw + 2, y + h - 3, 0xFF140E22);
        wiredCtl.clear();
        if (sel == null) return;
        BlockPos sp = BlockPos.fromLong(sel.getLong("p"));
        fr.drawStringWithShadow(fr.trimStringToWidth(sel.getString("n"), pw), px, py + 1, 0xFFFFFFFF);
        fr.drawString(com.dogpound.railmap.wire.WireNet.name(sel.getInteger("c")) + " wire · " + sp.getX() + ", " + sp.getY() + ", " + sp.getZ(), px, py + 11, C_DIM);
        py += 23;
        net.minecraft.nbt.NBTTagList ks = sel.getTagList("k", 10);
        if (ks.tagCount() == 0) { fr.drawString(fr.trimStringToWidth("No controls of its own (the real one has none) - monitored", pw), px, py, C_DIM); py += 12; }
        else { fr.drawString("CONTROLS", px, py, C_HEAD); py += 11; }
        int bw = Math.max(40, (pw - 6) / 3), bh = 22, col = 0;
        for (int k = 0; k < ks.tagCount(); k++) {
            net.minecraft.nbt.NBTTagCompound ct = ks.getCompoundTagAt(k);
            int t = ct.getInteger("t"), st = ct.getInteger("st");
            String type = t >= 0 && t < T_NAMES.length ? T_NAMES[t] : "";
            int bx = px + col * (bw + 3);
            if (py + bh > y + h - 4) break;
            boolean click = type.equals("PUSH") || type.equals("MUSHROOM") || type.equals("SELECTOR") || type.equals("KEY") || type.equals("LEVER");
            boolean over = click && mx >= bx && mx < bx + bw && my >= py && my < py + bh;
            int capCol = 0xFF000000 | ct.getInteger("col");
            Gui.drawRect(bx, py, bx + bw, py + bh, over ? 0xFF3A2E5C : 0xFF221A33);
            String lb = fr.trimStringToWidth(ct.getString("lb"), bw - 4), val;
            String[] pos = ct.getString("pos").isEmpty() ? new String[0] : ct.getString("pos").split("\\|");
            switch (type) {
                case "PUSH", "MUSHROOM" -> { Gui.drawRect(bx + 2, py + 2, bx + 8, py + bh - 2, capCol); val = type.equals("MUSHROOM") ? (st != 0 ? "LATCHED - click to release" : "STOP") : "press"; }
                case "SELECTOR", "KEY", "LEVER" -> val = (st >= 0 && st < pos.length ? pos[st] : String.valueOf(st)) + " ›";
                case "LAMP" -> { boolean lit = st != 0; Gui.drawRect(bx + 2, py + 6, bx + 8, py + 12, lit ? capCol : 0xFF303030); val = lit ? "ON" : "off"; }
                case "GAUGE" -> { int mxv = Math.max(1, ct.getInteger("max")); bar(bx + 2, py + 13, bw - 4, Math.min(1f, st / (float) mxv), 0xFF5BCEFA); val = st + " / " + mxv; }
                case "DIGITS" -> {
                    String f = ct.getString("fmt");
                    double v = st * ct.getDouble("sc");
                    try { val = f.contains("%d") ? String.format(Locale.ROOT, f, Math.round(v)) : String.format(Locale.ROOT, f, v); } catch (RuntimeException ex) { val = String.valueOf(v); }
                }
                case "FLAG" -> val = st != 0 ? "ON" : "OFF";
                case "SYNCHRO" -> val = st + "°";
                default -> val = String.valueOf(st);
            }
            int tx = type.equals("PUSH") || type.equals("MUSHROOM") || type.equals("LAMP") ? bx + 11 : bx + 3;
            fr.drawString(fr.trimStringToWidth(lb, bx + bw - tx - 2), tx, py + 2, C_HEAD);
            if (!type.equals("GAUGE")) fr.drawString(fr.trimStringToWidth(val, bx + bw - tx - 2), tx, py + 12, click ? 0xFFFFFFFF : C_TEXT);
            else fr.drawString(val, bx + bw - 3 - fr.getStringWidth(val), py + 2, C_DIM);
            if (click) { hits.add(new int[]{bx, py, bw, bh, 5000 + wiredCtl.size()}); wiredCtl.add(ct.getString("id")); if (over) hint = "Click: " + lb + " on the " + sel.getString("n"); }
            if (++col == 3) { col = 0; py += bh + 3; }
        }
        if (col != 0) py += bh + 3;
        fr.drawString("READINGS", px, py, C_HEAD);
        py += 11;
        net.minecraft.nbt.NBTTagList info = sel.getTagList("i", 8);
        int half = pw / 2;
        for (int k = 0; k < info.tagCount() && py + (k / 2) * 10 < y + h - 12; k++) {
            fr.drawString(fr.trimStringToWidth(info.getStringTagAt(k), half - 4), px + (k % 2) * half, py + (k / 2) * 10, C_TEXT);
        }
        py += (info.tagCount() + 1) / 2 * 10 + 4;
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
