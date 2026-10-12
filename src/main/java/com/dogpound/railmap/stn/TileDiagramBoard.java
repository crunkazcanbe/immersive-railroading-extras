package com.dogpound.railmap.stn;

import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.RailwayData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import java.util.*;

/**
 * Service Diagram Board (ChatGPT batch 3 #31): the daily workings of every unit, like a depot's diagram board. Ten
 * rows show diagram, unit, line, trips, calls and status, each with an ON TIME / LATE / OFF lamp. PREV / NEXT select a
 * unit (its detail on the lower screens); START - / + and END - / + set its diagram hours, the ENFORCE key sends it to
 * depot outside them (config diagramHours); MARK then SWAP on another unit exchanges their workings (line, position,
 * direction and diagram hours); PRINT gives the selected unit's day of workings as a book. 12 V + mains.
 */
public class TileDiagramBoard extends StnPanel {
    private int selI;
    private UUID marked;
    private UUID sel;

    @Override public String wiredName() { return "Service diagram board"; }
    @Override public String gearState() { return !powered() ? "NO 12 V" : !mainsOk() ? "NO MAINS" : Diagrams.list(world).size() + " diagrams"; }

    private StnData.Diagram selected() { return sel == null ? null : StnData.get(world).diagrams.get(sel); }

    @Override
    protected String act(String id, EntityPlayer who) {
        List<Map.Entry<UUID, StnData.Diagram>> l = Diagrams.list(world);
        StnData.Diagram g = selected();
        switch (id) {
            case "prev", "next" -> {
                if (l.isEmpty()) return "§7No driverless units yet";
                selI = Math.floorMod(selI + (id.equals("next") ? 1 : -1), l.size());
                sel = l.get(selI).getKey();
                ctl.set("k_enf", l.get(selI).getValue().enforce ? 1 : 0);
                return "§eDiagram " + l.get(selI).getValue().code + " - " + l.get(selI).getValue().unit;
            }
            case "stm", "stp", "enm", "enp" -> {
                if (g == null) return "§7Select a unit";
                boolean st = id.startsWith("st");
                int v = st ? g.start : g.end;
                v = v < 0 ? (st ? 6 : 22) : Math.floorMod(v + (id.endsWith("p") ? 1 : -1), 24);
                if (st) g.start = v; else g.end = v;
                if (g.start >= 0 && g.end < 0) g.end = 22;
                if (g.end >= 0 && g.start < 0) g.start = 6;
                StnData.get(world).markDirty();
                return String.format(Locale.ROOT, "§eDiagram %s: %02d:00 - %02d:00", g.code, g.start, g.end);
            }
            case "mark" -> { if (sel == null) return "§7Select a unit"; marked = sel; return "§eMarked " + (g == null ? "" : g.code) + " - select another unit and press SWAP"; }
            case "swap" -> {
                if (sel == null || marked == null || sel.equals(marked)) return "§7MARK one unit, select another, then SWAP";
                RailwayData rd = RailwayData.get(world);
                RailwayData.AutoTrain a = rd.train(marked), b = rd.train(sel);
                if (a == null || b == null) return "§cOne of them is no longer a driverless train";
                String ln = a.line; a.line = b.line; b.line = ln;
                int ix = a.index; a.index = b.index; b.index = ix;
                int dr = a.direction; a.direction = b.direction; b.direction = dr;
                rd.markDirty();
                StnData d = StnData.get(world);
                StnData.Diagram da = d.diagrams.get(marked), db = d.diagrams.get(sel);
                if (da != null && db != null) {
                    String c = da.code; da.code = db.code; db.code = c;
                    int s = da.start; da.start = db.start; db.start = s;
                    int e = da.end; da.end = db.end; db.end = e;
                    boolean en = da.enforce; da.enforce = db.enforce; db.enforce = en;
                    d.markDirty();
                }
                String txt = "Diagrams swapped: " + (da == null ? "?" : da.unit) + " now works " + (da == null ? "" : da.code) + " (" + a.line + "), " + (db == null ? "?" : db.unit) + " works " + (db == null ? "" : db.code) + " (" + b.line + ")";
                Recorder.event(world, txt + " - by " + who(who));
                marked = null;
                return "§a" + txt;
            }
            case "print" -> {
                if (g == null) return "§7Select a unit";
                StnUtil.print(world, pos, controlFacing(), "Diagram " + g.code, "Diagram board", Diagrams.book(world, g));
                return "§aPrinted diagram " + g.code;
            }
            default -> { return null; }
        }
    }

    @Override
    protected String mechanical(String id, EntityPlayer who) {
        if (!id.equals("k_enf")) return null;
        StnData.Diagram g = selected();
        if (g == null) return "§7Select a unit first";
        g.enforce = !g.enforce;
        ctl.set(id, g.enforce ? 1 : 0);
        StnData.get(world).markDirty();
        return g.enforce ? (g.start < 0 ? "§eENFORCE on - set the diagram hours" : "§aDiagram hours enforced") : "§eDiagram hours not enforced";
    }

    @Override
    protected void work() {
        if (age % 40 != 17) return;
        List<Map.Entry<UUID, StnData.Diagram>> l = Diagrams.list(world);
        if (sel == null && !l.isEmpty()) sel = l.get(0).getKey();
        for (int i = 0; i < 10; i++) {
            Map.Entry<UUID, StnData.Diagram> e = i < l.size() ? l.get(i) : null;
            String n = "r" + (i + 1);
            if (e == null) { text(n, ""); put(n + "g", false); put(n + "a", false); put(n + "r", false); continue; }
            StnData.Diagram g = e.getValue();
            RailwayData.AutoTrain a = RailwayData.get(world).train(e.getKey());
            String st = Diagrams.status(world, e.getKey(), g);
            text(n, String.format(Locale.ROOT, "%s %s %s T%d C%d %s", g.code, StnUtil.cut(StnUtil.up(g.unit), 9), StnUtil.cut(StnUtil.up(a == null ? "" : a.line), 6), g.trips, g.calls, st));
            boolean off = !st.startsWith("RUNNING") && !st.startsWith("AT STN"), late = st.contains("+");
            put(n + "g", !off && !late); put(n + "a", !off && late); put(n + "r", off);
            put(n + "s", e.getKey().equals(sel));
        }
        StnData.Diagram g = selected();
        if (g != null) {
            text("code", g.code + " " + StnUtil.up(StnUtil.cut(g.unit, 14)));
            text("hours", g.start < 0 ? "HOURS NOT SET" : String.format(Locale.ROOT, "%02d:00 - %02d:00%s", g.start, g.end, g.enforce ? " ENF" : ""));
            text("stats", String.format(Locale.ROOT, "T%d C%d LATE %d AVG %dS", g.trips, g.calls, g.lateCalls, g.calls == 0 ? 0 : Math.round(g.lateSum / g.calls)));
            text("days", "FIRST " + (g.firstDep == 0 ? "--:--" : com.dogpound.railmap.alarm.Recorder.clock(world, g.firstDep)) + " LAST " + (g.lastArr == 0 ? "--:--" : com.dogpound.railmap.alarm.Recorder.clock(world, g.lastArr)));
            text("yest", "YESTERDAY C" + g.callsY + " LATE " + g.lateY);
            put("enfl", g.enforce);
            ctl.set("k_enf", g.enforce ? 1 : 0);
        } else { text("code", "NO UNITS"); text("hours", ""); text("stats", ""); text("days", ""); text("yest", ""); }
        put("pwr", powered()); put("markl", marked != null);
        put("nunits", l.size());
    }

    @Override protected void saveExtra(NBTTagCompound t) { t.setInteger("selI", selI); if (sel != null) t.setUniqueId("sel", sel); }
    @Override protected void loadExtra(NBTTagCompound t) { selI = t.getInteger("selI"); sel = t.hasUniqueId("sel") ? t.getUniqueId("sel") : null; }
}
