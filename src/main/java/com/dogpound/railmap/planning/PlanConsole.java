package com.dogpound.railmap.planning;

import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.incident.TwinSim;
import com.dogpound.railmap.yard.TileYardPanel;
import com.dogpound.railmap.yard.YardPlanner;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import java.util.*;

/**
 * What the Control Center's PLAN tab shows (rows into PacketRailState.plan, row[0] = which table) and its buttons.
 * Twin: K knobs, TA / TC / TB / TD / TV last run, TP promotable, PA prediction accuracy, PL live prediction.
 * Robustness X*, resilience R*, what-if Q*, compatibility C, works W, versions V / VD, dispatchers G / GL,
 * split / join SP / SJ, yard Y*, trends H*.
 */
public final class PlanConsole {
    private PlanConsole() {}

    private static final Map<Integer, Integer> SEL_VERSION = new HashMap<>(), SEL_YARD = new HashMap<>();
    private static final int MAX_ROWS = 900;
    private static final Map<Integer, Object[]> MODEL = new HashMap<>();

    public static void fill(World w, List<String[]> out) {
        int dim = w.provider.getDimension();
        PlanData d = PlanData.get(w);
        long now = w.getTotalWorldTime();
        // twin knobs
        Object[] cm = MODEL.get(dim);
        if (cm == null || now - (long) cm[0] > 200 || now < (long) cm[0]) { cm = new Object[]{now, TwinSim.live(w)}; MODEL.put(dim, cm); }   // the knobs' names: a 10 s old model will do
        TwinSim.Model m = (TwinSim.Model) cm[1];
        NBTTagCompound p = d.twinCase;
        out.add(new String[]{"K", TwinLab.line(w, m), TwinLab.seg(w, m), TwinLab.stop(w, m), String.valueOf(p.getInteger("dwell")), String.valueOf(p.getInteger("speed")),
                String.valueOf(p.getInteger("trains")), b(p.getBoolean("cbtc")), String.valueOf(p.getInteger("close")), String.valueOf(p.getInteger("closestop")), String.valueOf(p.getInteger("tsr")),
                b(p.getBoolean("single")), String.valueOf(p.getInteger("power")), b(p.getBoolean("elec")), b(p.getBoolean("double")), String.valueOf(p.hasKey("rec") ? p.getInteger("rec") : 10),
                String.valueOf(p.getInteger("delay")), String.valueOf(p.hasKey("minutes") ? p.getInteger("minutes") : 60), String.valueOf(m.trains.size()), String.valueOf(m.lines.size()),
                String.valueOf(m.segments().size()), d.twinOk ? "1" : "0", String.valueOf(m.tracks(TwinLab.seg(w, m)))});
        out.addAll(d.lastTwin);
        for (String s : d.promotable) out.add(new String[]{"TP", s});
        for (String[] a : d.accuracy) out.add(prefix("PA", a));
        for (String[] a : d.predictions) out.add(new String[]{"PL", a[2], a[3], a[4], String.valueOf(Math.max(0, (Long.parseLong(a[0]) + Long.parseLong(a[1]) * 1200L - now) / 1200))});
        out.addAll(d.lastRobust);
        out.addAll(d.lastResil);
        out.addAll(d.lastWhatIf);
        try { PossessionPlanner.rows(w, out); } catch (RuntimeException ignored) { }      // infra #45 possession plans (PP / PT)
        for (String s : d.lastCompat) out.add(new String[]{"C", s.substring(0, 1), s.length() > 2 ? s.substring(2) : ""});
        // works
        for (PlanData.Work k : d.works)
            out.add(new String[]{"W", String.valueOf(k.id), k.kind.name(), k.name, k.phase.name(), k.kind == PlanData.Kind.LENGTH ? k.maxLenM + " m" : k.kmh + " km/h",
                    String.format(Locale.ROOT, "%.0f,%.0f r%.0f", k.x, k.z, k.r) + (k.kind == PlanData.Kind.SINGLE_LINE ? String.format(Locale.ROOT, " to %.0f,%.0f", k.x2, k.z2) : ""),
                    k.phase == PlanData.Phase.PLANNED ? "in " + Math.max(0, (k.start - now) / 1200) + " min" : k.phase == PlanData.Phase.INSTALLING || k.phase == PlanData.Phase.REMOVING ? Math.max(0, (k.phaseAt + TempWorks.crewTicks(k) - now) / 20) + " s crew" : k.end > 0 ? Math.max(0, (k.end - now) / 1200) + " min left" : "until taken out",
                    k.kind == PlanData.Kind.SINGLE_LINE ? (k.dir == 0 ? "free" : (k.dir > 0 ? "A>B " : "B>A ") + k.inSection) : k.kind == PlanData.Kind.CROSSING ? k.authorised + " authorised" : "", k.by});
        // versions
        int sv = SEL_VERSION.getOrDefault(dim, -1);
        PlanData.Version shown = null;
        for (int i = d.versions.size() - 1, n = 0; i >= 0 && n < 60; i--, n++) {
            PlanData.Version v = d.versions.get(i);
            if (sv == v.id || sv < 0 && shown == null) shown = v;
            out.add(new String[]{"V", String.valueOf(v.id), Recorder.clock(w, v.tick), v.cat, v.who, v.why, String.valueOf(v.diff.size()), v == shown ? "1" : "0"});
        }
        if (shown != null) {
            out.add(new String[]{"VS", String.valueOf(shown.id), shown.cat});
            for (String s : shown.diff) out.add(new String[]{"VD", s});
            List<String> bad = Versions.check(w, shown.cat, Versions.unpack(shown.snap));
            for (String s : bad) out.add(new String[]{"VX", s});
        }
        // dispatchers
        for (PlanData.Region r : d.regions)
            out.add(new String[]{"G", r.name, r.goal.label, r.on ? "ON" : "off", String.valueOf(r.priority), r.r <= 0 ? "whole railway" : String.format(Locale.ROOT, "%.0f,%.0f r%.0f", r.x, r.z, r.r),
                    r.lines.isEmpty() ? "all lines" : r.lines, String.valueOf(r.maxHold), String.valueOf(r.decisions), String.valueOf(r.overruled), r.goal.help});
        int gl = 0;
        for (String s : d.decisions) { if (gl++ >= 60) break; out.add(new String[]{"GL", s}); }
        out.add(new String[]{"GC", PlanConfig.aiDispatchers ? "1" : "0"});
        // split / join
        for (PlanData.SplitPlan s : d.splits) out.add(new String[]{"SP", String.valueOf(s.id), s.train, s.splitAt, String.valueOf(s.rearCars), s.rearLine.isEmpty() ? "(same)" : s.rearLine, s.joinAt, s.state, s.log});
        out.add(new String[]{"SJ", String.valueOf(SplitJoin.activeJoins()), PlanConfig.splitNeedsPlatform ? "1" : "0"});
        // yard
        List<TileYardPanel> ys = YardPlanner.panels(w);
        if (!ys.isEmpty()) {
            int sel = Math.floorMod(SEL_YARD.getOrDefault(dim, 0), ys.size());
            out.add(new String[]{"YN", String.valueOf(sel + 1), String.valueOf(ys.size())});
            try { out.addAll(YardPlanner.plan(w, ys.get(sel)).rows); } catch (RuntimeException | LinkageError e) { out.add(new String[]{"YK", "planner error: " + e}); }
        }
        // trends
        Trends.fill(w, out);
        out.add(new String[]{"RSC", String.valueOf(d.resilienceScore)});
        while (out.size() > MAX_ROWS) out.remove(out.size() - 1);
    }

    private static String b(boolean v) { return v ? "1" : "0"; }

    private static String[] prefix(String tag, String[] a) { String[] o = new String[a.length + 1]; o[0] = tag; System.arraycopy(a, 0, o, 1, a.length); return o; }

    /** console buttons; who = the player at the console (for positions + names) */
    public static String op(World w, int op, String arg, EntityPlayer who) {
        int dim = w.provider.getDimension();
        String name = who == null ? "console" : who.getName();
        double px = who == null ? 0 : who.posX, py = who == null ? 64 : who.posY, pz = who == null ? 0 : who.posZ;
        String[] a = arg.split(":", -1);
        try {
            switch (op) {
                // infra #45 possession planner: 60 plan here now "r:min", 61 plan at the best window "r:min", 62 re-scan id
                case 60: case 61: return PossessionPlanner.plan(w, px, py, pz, Double.parseDouble(a[0]), Long.parseLong(a[1]), op == 61, name);
                case 62: return PossessionPlanner.rescan(w, Integer.parseInt(arg));
                case 1: return TwinLab.set(w, a[0], a.length > 1 ? Integer.parseInt(a[1]) : 1);
                case 2: return TwinLab.run(w, name);
                case 3: return TwinLab.promote(w, name);
                case 4: return Robustness.study(w, name);
                case 5: { Resilience.Report r = Resilience.study(w); Resilience.store(w, r); return "§bResilience score §f" + r.score + "§b/100 - " + r.cutsOff.size() + " single points of failure, " + r.upgrades.size() + " upgrade(s) recommended"; }
                case 6: return WhatIf.ask(w, arg, name);
                case 7: return Compat.run(w, px, pz, arg);
                case 8: {   // kind:value:radius:minutes:name   (single-line: radius = section length along the way the player faces)
                    PlanData.Kind k = PlanData.Kind.valueOf(a[0]);
                    int value = Integer.parseInt(a[1]);
                    double r = Double.parseDouble(a[2]);
                    PlanData.Work wk;
                    if (k == PlanData.Kind.SINGLE_LINE) {
                        double yaw = Math.toRadians(who == null ? 0 : who.rotationYaw);
                        wk = TempWorks.add(w, k, a.length > 4 ? a[4] : "", px, py, pz, 6, value, 0, Long.parseLong(a[3]), name);
                        wk.x2 = px - Math.sin(yaw) * r; wk.z2 = pz + Math.cos(yaw) * r;
                    } else wk = TempWorks.add(w, k, a.length > 4 ? a[4] : "", px, py, pz, r, value, 0, Long.parseLong(a[3]), name);
                    return "§aPlanned " + TempWorks.label(wk) + " - the work crew goes out now";
                }
                case 9: return TempWorks.remove(w, Integer.parseInt(arg), name);
                case 10: return TempWorks.authorise(w, Integer.parseInt(a[0]), a.length > 1 ? Integer.parseInt(a[1]) : 1, name);
                case 11: { PlanData.Version v = Versions.commit(w, a[0], name, a.length > 1 && !a[1].isEmpty() ? a[1] : "committed from the console"); return v == null ? "§7No change to commit in " + a[0] : "§aCommitted v" + v.id + " (" + v.diff.size() + " changes)"; }
                case 12: return Versions.rollback(w, a[0], Integer.parseInt(a[1]), a.length > 2 && a[2].equals("force"), name);
                case 13: SEL_VERSION.put(dim, Integer.parseInt(arg)); return null;
                case 14: {  // name:goal:radius:lines
                    PlanData.Region r = Dispatchers.region(w, a[0], PlanData.Goal.valueOf(a[1]), px, pz, Double.parseDouble(a[2]), a.length > 3 ? a[3] : "", name);
                    return "§aRegion " + r.name + " (" + r.goal.label + ") set up - switched OFF until you turn it on";
                }
                case 15: case 16: case 17: case 18: {
                    for (PlanData.Region r : PlanData.get(w).regions) {
                        if (!r.name.equalsIgnoreCase(a[0])) continue;
                        if (op == 15) r.on = !r.on;
                        if (op == 16) { PlanData.Goal[] g = PlanData.Goal.values(); r.goal = g[(r.goal.ordinal() + 1) % g.length]; }
                        if (op == 17) r.priority = r.priority % 5 + 1;
                        if (op == 18) { PlanData.get(w).regions.remove(r); PlanData.get(w).markDirty(); Versions.commit(w, "dispatch", name, "region " + a[0] + " removed"); return "§eRegion " + a[0] + " removed"; }
                        PlanData.get(w).markDirty();
                        Versions.commit(w, "dispatch", name, "region " + r.name + (op == 15 ? r.on ? " ON" : " off" : op == 16 ? " goal " + r.goal.label : " priority " + r.priority));
                        return "§bRegion " + r.name + ": " + (r.on ? "ON" : "off") + ", " + r.goal.label + ", priority " + r.priority;
                    }
                    return "§cNo region " + a[0];
                }
                case 19: SEL_YARD.put(dim, SEL_YARD.getOrDefault(dim, 0) + Integer.parseInt(arg)); return null;
                case 20: case 21: case 22: {
                    List<TileYardPanel> ys = YardPlanner.panels(w);
                    if (ys.isEmpty()) return "§cNo yard control panel loaded";
                    TileYardPanel y = ys.get(Math.floorMod(SEL_YARD.getOrDefault(dim, 0), ys.size()));
                    if (op == 20) return YardPlanner.cycleStrategy(w, y);
                    if (op == 21) return YardPlanner.addDeparture(w, y, a[0], Integer.parseInt(a[1]) - 1, a[2], Integer.parseInt(a[3]), Integer.parseInt(a[4]), Integer.parseInt(a[5]), a.length > 6 && !a[6].isEmpty() ? Integer.parseInt(a[6]) - 1 : -1);
                    return YardPlanner.removeDeparture(w, y, Integer.parseInt(arg));
                }
                case 23: { PlanData.SplitPlan s = SplitJoin.plan(w, a[0], a[1], Integer.parseInt(a[2]), a[3], a[4]); return "§aSplit/join plan #" + s.id + ": " + s.train + " splits " + s.rearCars + " at " + s.splitAt + ", rejoins at " + s.joinAt; }
                case 24: { boolean r = PlanData.get(w).splits.removeIf(s -> s.id == Integer.parseInt(arg)); PlanData.get(w).markDirty(); return r ? "§ePlan removed" : "§cNo plan #" + arg; }
                case 25: {
                    Locomotive l = Compat.nearest(w, px, pz, 32);
                    if (l == null) return "§cNo locomotive within 32 blocks";
                    return SplitJoin.split(w, l, Integer.parseInt(a[0]), a.length > 1 ? a[1] : "", name);
                }
                case 26: {
                    Locomotive l1 = Compat.nearest(w, px, pz, 48);
                    if (l1 == null) return "§cNo locomotive within 48 blocks";
                    Locomotive l2 = null;
                    double bd = 120 * 120;
                    for (Locomotive l : cam72cam.mod.world.World.get(w).getEntities(Locomotive.class)) {
                        if (l1.getTrain().contains(l)) continue;
                        Vec3d q = l.getPosition(), r = l1.getPosition();
                        double dd = (q.x - r.x) * (q.x - r.x) + (q.z - r.z) * (q.z - r.z);
                        if (dd < bd) { bd = dd; l2 = l; }
                    }
                    if (l2 == null) return "§cNo second portion within 120 blocks";
                    return SplitJoin.join(w, l1, l2, name);
                }
                case 27: {  // survey a stretch single / double: the working case's stretch
                    TwinSim.Model mm = TwinSim.live(w);
                    String seg = TwinLab.seg(w, mm);
                    PlanData d = PlanData.get(w);
                    int t = d.tracks.getOrDefault(seg, 2) == 2 ? 1 : 2;
                    d.tracks.put(seg, t);
                    d.markDirty();
                    return "§b" + seg + " surveyed as " + (t == 1 ? "SINGLE" : "DOUBLE") + " track";
                }
                default: return null;
            }
        } catch (RuntimeException e) {
            return "§cCouldn't do that: " + e.getMessage();
        }
    }
}
