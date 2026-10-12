package com.dogpound.railmap.planning;

import com.dogpound.railmap.block.StationControls;
import com.dogpound.railmap.block.TileFurniture;
import com.dogpound.railmap.incident.TileIncidentDesk;
import com.dogpound.railmap.incident.TwinSim;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import java.util.*;

/**
 * The Network Planning Desk (her "next 48" list #1, 3, 6, 7, 40, 42, 46, 48): the digital twin's desk in the planning
 * office. On the wall the NETWORK BOARD lists the six busiest stretches - trains per hour against capacity, spare
 * capacity, a SPOF lamp for a single point of failure and a CUT lamp for a stretch blocked right now - with the
 * resilience score and the twin's last verdict. The desk's three button rows build the WORKING CASE (LINE / STRETCH /
 * STATION pick what it applies to; DWELL, TRAINS; CLOSE, TSR, 1-LINE, POWER cut, WIRE, DOUBLE, CBTC, DELAY - each press
 * steps through its settings), RUN it on the shadow simulation, PROMOTE it to the live railway (only with the PROMOTE key
 * at AUTH), run the ROBUSTness and RESILience studies and two what-if questions (WI WIRE: electrify the line; WI CAP:
 * cheapest way to double the stretch), COMMIT the configuration as a version and PRINT the view. RECOV sets the
 * timetable recovery allowance; the AI DISP key switches every regional AI dispatcher on or off; SURVEY marks the
 * stretch single / double track. The left monitor shows the case against "as is"; the right one the VIEW: twin
 * promotion list, resilience, robustness, works, AI decisions, versions, trends, the yard plan. Real storage: six desk
 * drawers, the plan chest and the binders cupboard; the shelf shows what's on it. Needs 12 V.
 */
public class TilePlanningDesk extends TileFurniture {
    public static final String[] STORE_IDS = {"dl1", "dl2", "dl3", "dr1", "dr2", "dr3", "plans", "binders"};
    public static final String[] STORE_NAMES = {"Timetables Drawer", "Survey Drawer", "Stationery Drawer", "Notices Drawer", "Permits Drawer", "Keys Drawer", "Plan Chest", "Binders Cupboard"};
    public static final int[] STORE_SIZES = {9, 9, 9, 9, 9, 9, 27, 18};
    private static final double[][] SPOTS = {{19.933, 7.0, 13.4}, {21.6, 7.0, 13.4}, {23.267, 7.0, 13.4}, {24.933, 7.0, 13.4}, {26.6, 7.0, 13.4}, {28.267, 7.0, 13.4}};
    private static final int[] REC = {0, 5, 10, 15, 20, 30};
    @Override public double[][] shelfSpots() { return SPOTS; }

    private int lastRec = -1, lastAi = -1;
    /** the twin model behind the case line and the view are heavier: rebuilt every 2 s or after a press */
    private String caseLine = "";
    private List<String> viewLines = new ArrayList<>();
    private long heavyAt = -1000;
    private boolean force;
    private int shownView = -1;

    public TilePlanningDesk() { super(STORE_IDS, STORE_NAMES, STORE_SIZES, 6); }

    @Override protected boolean mechanical(String id) { return id.equals("auth") || id.equals("ai"); }

    private static int next(int cur, int[] steps) { for (int s : steps) if (s > cur) return s; return steps[0]; }

    @Override
    protected String press(String id, EntityPlayer who) {
        String name = who == null ? "planning desk" : who.getName();
        force = true;
        NBTTagCompound p = TwinLab.params(world);
        switch (id) {
            case "line": case "seg": case "stop": return TwinLab.set(world, id, 1);
            case "dwp": return TwinLab.set(world, "dwell", 1);
            case "dwm": return TwinLab.set(world, "dwell", -1);
            case "trp": return TwinLab.set(world, "trains", 1);
            case "trm": return TwinLab.set(world, "trains", -1);
            case "reset": return TwinLab.set(world, "reset", 0);
            case "close": return TwinLab.set(world, "close", (next(p.getInteger("close"), new int[]{0, 15, 30, 60}) - p.getInteger("close")) / 5);
            case "power": return TwinLab.set(world, "power", (next(p.getInteger("power"), new int[]{0, 10, 30}) - p.getInteger("power")) / 5);
            case "tsr": return TwinLab.set(world, "tsr", (next(p.getInteger("tsr"), new int[]{0, 30, 50, 70}) - p.getInteger("tsr")) / 10);
            case "delay": return TwinLab.set(world, "delay", (next(p.getInteger("delay"), new int[]{0, 120, 300, 600}) - p.getInteger("delay")) / 30);
            case "single": case "elec": case "double": case "cbtc": return TwinLab.set(world, id, 1);
            case "run": return TwinLab.run(world, name);
            case "promote":
                if (ctl.get("auth") != 1) { world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ui.beep_error"), net.minecraft.util.SoundCategory.BLOCKS, 0.6f, 1.0f); return "§cTurn the PROMOTE key to AUTH first"; }
                return TwinLab.promote(world, name);
            case "robust": return Robustness.study(world, name);
            case "resil": { Resilience.Report r = Resilience.study(world); Resilience.store(world, r); return "§bResilience " + r.score + "/100"; }
            case "wiwire": return WhatIf.ask(world, "electrify " + TwinLab.line(world, TwinSim.live(world)).replace(' ', '_'), name);
            case "wicap": {
                String[] e = TwinLab.seg(world, TwinSim.live(world)).split("~");
                return e.length == 2 ? WhatIf.ask(world, "capacity " + e[0].replace(' ', '_') + " " + e[1].replace(' ', '_'), name) : "§7No stretch selected";
            }
            case "commit": {
                int n = 0;
                for (String c : Versions.CATS) if (Versions.commit(world, c, name, "committed at the planning desk") != null) n++;
                return n == 0 ? "§7Nothing changed since the last version" : "§a" + n + " part(s) committed";
            }
            case "survey": return PlanConsole.op(world, 27, "", who);
            case "print": {
                List<String> lines = new ArrayList<>();
                lines.add("N|Network Planning - " + VIEWS[Math.floorMod(ctl.get("view"), VIEWS.length)]);
                for (int i = 1; i <= 6; i++) lines.add(ctl.text("m" + i));
                lines.add("");
                lines.addAll(view(Math.floorMod(ctl.get("view"), VIEWS.length), 40));
                TileIncidentDesk.printBook(world, pos, controlFacing(), "Planning " + VIEWS[Math.floorMod(ctl.get("view"), VIEWS.length)], "Planning Office", lines);
                return "§7Printing...";
            }
            default: return null;
        }
    }

    @Override
    protected void serverTick() {
        if (age % 10 != 5 || !term.powered()) return;
        int rec = Math.floorMod(ctl.get("rec"), REC.length);
        if (lastRec >= 0 && rec != lastRec) {
            NBTTagCompound p = TwinLab.params(world);
            int cur = p.hasKey("rec") ? p.getInteger("rec") : 10;
            TwinLab.set(world, "rec", REC[rec] - cur);
        }
        lastRec = rec;
        int ai = ctl.get("ai");
        if (lastAi >= 0 && ai != lastAi) {
            PlanData d = PlanData.get(world);
            for (PlanData.Region r : d.regions) r.on = ai == 1;
            d.markDirty();
            d.decide(world, "AI dispatchers " + (ai == 1 ? "switched ON" : "switched OFF") + " at the planning desk key (" + d.regions.size() + " regions)");
            Versions.commit(world, "dispatch", "planning desk key", "all regions " + (ai == 1 ? "ON" : "off"));
        }
        lastAi = ai;
    }

    static final String[] VIEWS = {"TWIN", "RESILIENCE", "ROBUSTNESS", "WORKS", "AI DISPATCH", "VERSIONS", "TRENDS", "YARD"};

    /** the right monitor's lines for a view */
    private List<String> view(int v, int max) {
        PlanData d = PlanData.get(world);
        List<String> o = new ArrayList<>();
        switch (v) {
            case 0: o.addAll(d.promotable.isEmpty() ? Collections.singletonList("RUN THE CASE - PROMOTE LIST APPEARS HERE") : d.promotable);
                for (String[] a : d.accuracy) if (o.size() < max) o.add("PRED " + a[1] + " " + a[2] + "/H ACT " + a[3] + " ERR " + a[5] + "%"); break;
            case 1: for (String[] r : d.lastResil) {
                    if (r[0].equals("RS")) o.add(0, "SCORE " + r[1] + "/100 TOWNS " + r[2] + " SPOF " + r[5] + " CUT " + r[6]);
                    if (r[0].equals("RU")) o.add(r[1] + " " + r[2] + " " + r[4]);
                    if (r[0].equals("RI")) o.add("CUT OFF " + r[1]);
                } break;
            case 2: for (String[] r : d.lastRobust) if (r[0].equals("XT")) o.add(r[1] + " P" + r[3] + "% D" + r[4] + "S KNOCK " + r[8] + "T " + r[9] + "M"); else if (r[0].equals("XA")) o.add(r[1]); break;
            case 3: for (PlanData.Work k : d.works) o.add("#" + k.id + " " + TempWorks.label(k) + " " + k.phase.name()); break;
            case 4: for (PlanData.Region r : d.regions) o.add(r.name + " " + r.goal.name() + " " + (r.on ? "ON" : "OFF") + " P" + r.priority + " " + r.decisions + "/" + r.overruled);
                for (String s : d.decisions) if (o.size() < max) o.add(s); break;
            case 5: for (int i = d.versions.size() - 1; i >= 0 && o.size() < max; i--) { PlanData.Version x = d.versions.get(i); o.add("V" + x.id + " " + x.cat + " " + x.who + " " + x.diff.size()); } break;
            case 6: { List<String[]> rows = new ArrayList<>(); Trends.fill(world, rows);
                for (String[] r : rows) if (r[0].equals("HC") && r.length > 6 && r[1].startsWith("Today") && o.size() < max) o.add(r[2] + " " + r[3] + " VS " + r[4] + " " + r[5]);
                if (o.isEmpty()) o.add("NOT ENOUGH HISTORY YET"); break; }
            default: { List<com.dogpound.railmap.yard.TileYardPanel> ys = com.dogpound.railmap.yard.YardPlanner.panels(world);
                if (ys.isEmpty()) { o.add("NO YARD PANEL LOADED"); break; }
                for (String[] r : com.dogpound.railmap.yard.YardPlanner.plan(world, ys.get(0)).rows) {
                    if (r[0].equals("YP")) o.add(0, r[3] + " " + r[4] + " WAGONS " + r[6] + " READY DL " + r[8]);
                    if (r[0].equals("YD")) o.add(r[2] + " " + r[3] + " " + r[7] + " " + r[11]);
                    if (r[0].equals("YK")) o.add(r[1]);
                } }
        }
        if (o.isEmpty()) o.add("NOTHING YET");
        return o.size() > max ? o.subList(0, max) : o;
    }

    /** the board's lettering has A-Z 0-9 - . : > $ / % + */
    static String up(String s, int n) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toUpperCase(Locale.ROOT).toCharArray()) {
            if (c == '~') c = '-';
            b.append(c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || " -.:>$/%+".indexOf(c) >= 0 ? c : ' ');
        }
        String o = b.toString().replaceAll(" +", " ").trim();
        return o.length() > n ? o.substring(0, n) : o;
    }

    private static String g(String[] r, int i) { return r != null && r.length > i ? r[i] : ""; }

    @Override
    protected boolean refresh() {
        boolean ch = false;
        PlanData d = PlanData.get(world);
        // network board: the six least-spare stretches
        List<String[]> segs = new ArrayList<>();
        String score = "-";
        boolean spof = false, iso = false;
        for (String[] r : d.lastResil) { if (r[0].equals("RE")) segs.add(r); if (r[0].equals("RS")) score = r[1]; if (r[0].equals("RC")) spof = true; if (r[0].equals("RI")) iso = true; }
        segs.sort(Comparator.comparingDouble(r -> num(g(r, 8))));
        ch |= txt("title", up(segs.isEmpty() ? "NETWORK PLANNING - PRESS RESIL FOR A STUDY" : "NETWORK PLANNING - RESILIENCE " + score + "/100", 44));
        for (int k = 1; k <= 6; k++) {
            String[] r = k - 1 < segs.size() ? segs.get(k - 1) : null;
            ch |= put("r" + k + "s", r != null && g(r, 4).equals("SPOF"));
            ch |= put("r" + k + "c", r != null && g(r, 9).equals("CUT"));
            ch |= txt("r" + k + "a", r == null ? "" : up(g(r, 1) + " " + g(r, 6) + "/H OF " + g(r, 7) + " " + g(r, 3), 40));
            ch |= txt("r" + k + "b", r == null ? "" : up("SPARE " + g(r, 8) + "%", 12));
        }
        String verdict = "", worse = "";
        for (String[] r : d.lastTwin) if (r[0].equals("TV")) verdict = r[1];
        ch |= txt("verdict", up(verdict.isEmpty() ? "TWIN: NO CASE RUN YET" : "TWIN: " + verdict, 48));
        // left monitor: the case against as-is
        String[] m = new String[6];
        Arrays.fill(m, "");
        long now = world.getTotalWorldTime();
        if (force || now - heavyAt >= 40 || shownView != Math.floorMod(ctl.get("view"), VIEWS.length)) {
            shownView = Math.floorMod(ctl.get("view"), VIEWS.length);
            caseLine = up("CASE " + TwinLab.describe(world).replace("Working case: ", ""), 40);
            viewLines = view(Math.floorMod(ctl.get("view"), VIEWS.length), 6);
            heavyAt = now;
            force = false;
        }
        m[0] = caseLine;
        String punct = "-", delay = "-";
        int i = 1;
        for (String[] r : d.lastTwin) {
            if (!r[0].equals("TC") || i > 4) continue;
            if (r[1].startsWith("Punctuality")) punct = r[3];
            if (r[1].startsWith("Average delay")) delay = r[3];
            if (r[1].startsWith("Punctuality") || r[1].startsWith("Average delay") || r[1].startsWith("Departures") || r[1].startsWith("Connections")) m[i++] = up(r[1] + " " + r[2] + " > " + r[3], 40);
        }
        for (String[] r : d.lastTwin) if (r[0].equals("TB")) { m[5] = up("BOTTLENECK " + r[1] + " " + r[2] + " MIN", 40); break; }
        if (m[5].isEmpty()) m[5] = up(verdict, 40);
        for (int k = 0; k < 6; k++) ch |= txt("m" + (k + 1), m[k]);
        List<String> pl = viewLines;
        ch |= txt("p1", up(VIEWS[Math.floorMod(ctl.get("view"), VIEWS.length)] + ": " + (pl.isEmpty() ? "" : pl.get(0)), 44));
        for (int k = 1; k < 6; k++) ch |= txt("p" + (k + 1), k < pl.size() ? up(pl.get(k), 44) : "");
        // lamps + readouts
        boolean works = false, crew = false, ai = false, dead = false, predOk = !d.accuracy.isEmpty() && Math.abs(num(d.accuracy.get(0)[5])) <= 15;
        for (PlanData.Work k : d.works) { works |= k.live(); crew |= k.phase == PlanData.Phase.INSTALLING || k.phase == PlanData.Phase.REMOVING; }
        for (PlanData.Region r : d.regions) ai |= r.on;
        for (String[] r : d.lastTwin) dead |= r[0].equals("TD");
        ch |= put("power", true);
        ch |= put("promotable", d.twinOk);
        ch |= put("worse", verdict.contains("WORSE"));
        ch |= put("deadlock", dead);
        ch |= put("spof", spof);
        ch |= put("isolated", iso);
        ch |= put("works", works);
        ch |= put("crew", crew);
        ch |= put("aion", ai && PlanConfig.aiDispatchers);
        ch |= put("predok", predOk);
        ch |= txt("resil", d.resilienceScore < 0 ? "--" : String.valueOf(d.resilienceScore));
        ch |= txt("punct", punct.equals("-") ? "-- %" : punct + " %");
        ch |= txt("delay", delay.equals("-") ? "-- S" : delay + " S");
        ch |= txt("ver", d.versions.isEmpty() ? "V--" : "V" + d.versions.get(d.versions.size() - 1).id);
        return ch;
    }

    private static double num(String s) { try { return Double.parseDouble(s.replace("+", "").replace("%", "").trim()); } catch (NumberFormatException e) { return 0; } }

    private boolean put(String id, boolean v) { return StationControls.put(ctl, id, v); }
    private boolean txt(String id, String t) { return StationControls.text(ctl, id, t); }

    @Override public String wiredName() { return "Network planning desk"; }
    @Override public String gearState() {
        if (!term.powered()) return "NO 12 V - desk dark";
        PlanData d = PlanData.get(world);
        return "resilience " + (d.resilienceScore < 0 ? "-" : d.resilienceScore) + ", " + d.works.size() + " temporary works, twin " + (d.twinOk ? "promotable" : "no promotable case");
    }
}
