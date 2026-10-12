package com.dogpound.railmap.planning;

import com.dogpound.railmap.incident.TwinSim;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.*;

/**
 * /irextras plan ... - the planning tools for testing and for computers (her "next 48" list #1, 3, 4, 6, 7, 8, 9, 40, 42, 46, 48).
 * Every sub-command runs the same code as the console's PLAN tab and the Network Planning Desk.
 */
public final class PlanCommand {
    private PlanCommand() {}

    private static final String USAGE = "§8/irextras plan twin [set <knob> <+-n> | run | promote | case] | whatif <question> | robust | resil | works [add <kind> <value> <radius> <minutes> [name] | out <id> | auth <id> [n]] | "
            + "compat [line] | split <cars> [line] | join | splitplan <train> <at> <cars> <rearLine|-> <joinAt> | yard [strategy | dep <name> <road> <takes> <min> <cars> <len> | undep <id>] | "
            + "versions [diff <v> | commit <part> [why] | rollback <part> <v> [force]] | dispatch [add <name> <goal> <radius> [lines] | on|goal|prio|del <name>] | trends | tracks <a> <b> <1|2> | ops [list | step <x> <y> <z> | selftest] | selftest";

    public static void run(ICommandSender s, World w, String[] a) {
        if (a.length < 2) { say(s, USAGE); return; }
        EntityPlayer p = s.getCommandSenderEntity() instanceof EntityPlayer pl ? pl : null;
        String sub = a[1].toLowerCase(Locale.ROOT);
        PlanData d = PlanData.get(w);
        switch (sub) {
            case "twin": {
                String op = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "case";
                if (op.equals("set") && a.length > 4) say(s, TwinLab.set(w, a[3], Integer.parseInt(a[4].replace("+", ""))));
                else if (op.equals("run")) { say(s, TwinLab.run(w, s.getName())); for (String[] r : d.lastTwin) if (r[0].equals("TC")) say(s, String.format(Locale.ROOT, "§7 %-28s %8s -> %8s  %s", r[1], r[2], r[3], r[4])); }
                else if (op.equals("promote")) say(s, TwinLab.promote(w, s.getName()));
                else { say(s, TwinLab.describe(w)); say(s, "§8knobs: " + String.join(" ", TwinLab.KNOBS) + " reset"); }
                return;
            }
            case "whatif": say(s, WhatIf.ask(w, String.join(" ", Arrays.copyOfRange(a, 2, a.length)), s.getName())); for (String[] r : d.lastWhatIf) say(s, "§7 " + String.join(" | ", Arrays.copyOfRange(r, 1, r.length))); return;
            case "robust": say(s, Robustness.study(w, s.getName())); for (String[] r : d.lastRobust) say(s, "§7 " + String.join(" | ", r)); return;
            case "resil": { Resilience.Report r = Resilience.study(w); Resilience.store(w, r); say(s, "§bResilience " + r.score + "/100"); for (String[] x : d.lastResil) say(s, "§7 " + String.join(" | ", x)); return; }
            case "works": {
                if (a.length > 2 && a[2].equalsIgnoreCase("add") && a.length > 6) {
                    PlanData.Kind k = PlanData.Kind.valueOf(a[3].toUpperCase(Locale.ROOT));
                    say(s, PlanConsole.op(w, 8, k.name() + ":" + a[4] + ":" + a[5] + ":" + a[6] + ":" + (a.length > 7 ? String.join(" ", Arrays.copyOfRange(a, 7, a.length)) : ""), p));
                } else if (a.length > 3 && a[2].equalsIgnoreCase("out")) say(s, TempWorks.remove(w, Integer.parseInt(a[3]), s.getName()));
                else if (a.length > 3 && a[2].equalsIgnoreCase("auth")) say(s, TempWorks.authorise(w, Integer.parseInt(a[3]), a.length > 4 ? Integer.parseInt(a[4]) : 1, s.getName()));
                for (PlanData.Work k : d.works) say(s, "§7#" + k.id + " " + TempWorks.label(k) + " §f" + k.phase + (k.kind == PlanData.Kind.SINGLE_LINE ? " dir " + k.dir + " in " + k.inSection : k.kind == PlanData.Kind.CROSSING ? " auth " + k.authorised : ""));
                if (d.works.isEmpty()) say(s, "§7No temporary configurations. Kinds: TSR SINGLE_LINE CROSSING LENGTH POSSESSION");
                return;
            }
            case "compat": { say(s, Compat.run(w, s.getPositionVector().x, s.getPositionVector().z, a.length > 2 ? String.join(" ", Arrays.copyOfRange(a, 2, a.length)) : "")); for (String l : d.lastCompat) say(s, (l.startsWith("P") ? "§c" : l.startsWith("W") ? "§6" : l.startsWith("O") ? "§a" : "§7") + l.substring(2)); return; }
            case "split": say(s, PlanConsole.op(w, 25, (a.length > 2 ? a[2] : "1") + ":" + (a.length > 3 ? a[3] : ""), p)); return;
            case "join": say(s, PlanConsole.op(w, 26, "", p)); return;
            case "splitplan": if (a.length > 6) say(s, PlanConsole.op(w, 23, a[2] + ":" + a[3].replace('_', ' ') + ":" + a[4] + ":" + (a[5].equals("-") ? "" : a[5].replace('_', ' ')) + ":" + a[6].replace('_', ' '), p)); for (PlanData.SplitPlan x : d.splits) say(s, "§7#" + x.id + " " + x.train + " " + x.state); return;
            case "yard": {
                if (a.length > 2 && a[2].equalsIgnoreCase("strategy")) say(s, PlanConsole.op(w, 20, "", p));
                else if (a.length > 8 && a[2].equalsIgnoreCase("dep")) say(s, PlanConsole.op(w, 21, a[3] + ":" + a[4] + ":" + a[5].replace('_', ' ') + ":" + a[6] + ":" + a[7] + ":" + a[8] + ":" + (a.length > 9 ? a[9] : ""), p));
                else if (a.length > 3 && a[2].equalsIgnoreCase("undep")) say(s, PlanConsole.op(w, 22, a[3], p));
                List<String[]> rows = new ArrayList<>();
                PlanConsole.fill(w, rows);
                for (String[] r : rows) if (r[0].startsWith("Y")) say(s, "§7" + String.join(" | ", r));
                return;
            }
            case "versions": {
                if (a.length > 3 && a[2].equalsIgnoreCase("diff")) { int v = Integer.parseInt(a[3]); for (PlanData.Version x : d.versions) if (x.id == v) { say(s, "§bv" + v + " " + x.cat + " by " + x.who + ": " + x.why); for (String l : x.diff) say(s, "§7 " + l); } return; }
                if (a.length > 3 && a[2].equalsIgnoreCase("commit")) { PlanData.Version v = Versions.commit(w, a[3], s.getName(), a.length > 4 ? String.join(" ", Arrays.copyOfRange(a, 4, a.length)) : "committed by command"); say(s, v == null ? "§7No change" : "§aCommitted v" + v.id); return; }
                if (a.length > 4 && a[2].equalsIgnoreCase("rollback")) { say(s, Versions.rollback(w, a[3], Integer.parseInt(a[4]), a.length > 5 && a[5].equalsIgnoreCase("force"), s.getName())); return; }
                for (int i = Math.max(0, d.versions.size() - 20); i < d.versions.size(); i++) { PlanData.Version v = d.versions.get(i); say(s, "§7v" + v.id + " " + v.cat + " " + v.who + ": " + v.why + " (" + v.diff.size() + ")"); }
                return;
            }
            case "dispatch": {
                if (a.length > 5 && a[2].equalsIgnoreCase("add")) say(s, PlanConsole.op(w, 14, a[3] + ":" + a[4].toUpperCase(Locale.ROOT) + ":" + a[5] + ":" + (a.length > 6 ? a[6] : ""), p));
                else if (a.length > 3) { int op = switch (a[2].toLowerCase(Locale.ROOT)) { case "on" -> 15; case "goal" -> 16; case "prio" -> 17; case "del" -> 18; default -> 0; }; if (op > 0) say(s, PlanConsole.op(w, op, a[3], p)); }
                for (PlanData.Region r : d.regions) say(s, "§7" + r.name + " " + r.goal.label + " " + (r.on ? "§aON" : "§8off") + " §7prio " + r.priority + " decisions " + r.decisions + " overruled " + r.overruled);
                for (int i = 0; i < Math.min(10, d.decisions.size()); i++) say(s, "§8" + d.decisions.get(i));
                return;
            }
            case "trends": { List<String[]> o = new ArrayList<>(); Trends.fill(w, o); for (String[] r : o) if (r[0].equals("HC") || r[0].equals("HN")) say(s, "§7" + String.join(" | ", r)); return; }
            case "tracks": if (a.length > 4) { String x = a[2].replace('_', ' '), y = a[3].replace('_', ' '); String k = x.compareTo(y) <= 0 ? x + "~" + y : y + "~" + x; d.tracks.put(k, Integer.parseInt(a[4]) <= 1 ? 1 : 2); d.markDirty(); say(s, "§b" + k + " = " + d.tracks.get(k) + " track(s)"); } return;
            case "ops": OpsGear.command(s, w, a); return;   // batch-3 #1 #2 #4 #5 station operations gear
            case "selftest": selftest(s, w); return;
            default: say(s, USAGE);
        }
    }

    /** checks the planning maths on made-up networks: nothing in the world is touched */
    static void selftest(ICommandSender s, World w) {
        int ok = 0, bad = 0;
        // 1: twin on a two-stop shuttle: departures happen, a closure holds trains, single track costs throughput
        TwinSim.Model m = new TwinSim.Model();
        long a = net.minecraft.util.math.BlockPos.ORIGIN.toLong(), b = new net.minecraft.util.math.BlockPos(600, 64, 0).toLong(), c = new net.minecraft.util.math.BlockPos(1200, 64, 0).toLong();
        m.names.put(a, "A"); m.names.put(b, "B"); m.names.put(c, "C");
        m.lines.put("L", new long[]{a, b, c});
        for (int i = 0; i < 3; i++) { TwinSim.Train t = new TwinSim.Train(); t.name = "T" + i; t.line = "L"; t.st = m.lines.get("L"); t.idx = i; t.shuttle = true; t.left = 10 + i * 40; m.trains.add(t); }
        TwinSim.Case base = new TwinSim.Case();
        TwinSim.Result r0 = TwinSim.run(m, base);
        bad += check(s, r0.deps > 10, "twin runs trains (" + Math.round(r0.deps) + " departures)") ? 0 : 1;
        TwinSim.Case closed = TwinSim.copy(base);
        closed.closeSeg.put("A~B", new double[]{0, 1800});
        TwinSim.Result r1 = TwinSim.run(m, closed);
        bad += check(s, r1.held > 0 && r1.deps < r0.deps, "a closure holds trains (" + Math.round(r1.held / 60) + " train-min)") ? 0 : 1;
        TwinSim.Case single = TwinSim.copy(base);
        single.segTracks.put("A~B", 1); single.segTracks.put("B~C", 1);
        TwinSim.Result r2 = TwinSim.run(m, single);
        bad += check(s, r2.deps <= r0.deps, "single-line working never adds capacity (" + Math.round(r2.deps) + " <= " + Math.round(r0.deps) + ")") ? 0 : 1;
        TwinSim.Case hit = TwinSim.copy(base);
        hit.delayTrain = "T0"; hit.delaySec = 300; hit.delayAt = 120; hit.recovery = 0;
        double[] k = TwinSim.knockOn(m, hit);
        bad += check(s, k[1] > 0, "a 5 min delay knocks on (" + String.format(Locale.ROOT, "%.1f", k[1]) + " extra train-min)") ? 0 : 1;
        // 2: resilience maths on a line A-B-C plus a loop C-D-E-C: A-B and B-C are bridges, B and C articulation points
        Map<String, Set<String>> adj = new TreeMap<>();
        String[][] edges = {{"A", "B"}, {"B", "C"}, {"C", "D"}, {"D", "E"}, {"E", "C"}};
        for (String[] e : edges) { adj.computeIfAbsent(e[0], x -> new TreeSet<>()).add(e[1]); adj.computeIfAbsent(e[1], x -> new TreeSet<>()).add(e[0]); }
        bad += check(s, Resilience.disjointPaths(adj, "C", "D", 3) == 2, "loop stretch has 2 independent paths") ? 0 : 1;
        bad += check(s, Resilience.disjointPaths(adj, "A", "B", 3) == 1, "branch stretch has only 1") ? 0 : 1;
        // 3: version diff
        Map<String, String> v1 = new TreeMap<>(), v2 = new TreeMap<>();
        v1.put("x", "1"); v1.put("y", "2"); v2.put("x", "1"); v2.put("y", "3"); v2.put("z", "4");
        List<String> df = Versions.diff(v1, v2);
        bad += check(s, df.size() == 2 && df.get(0).startsWith("~ y") && df.get(1).startsWith("+ z"), "config diff finds a change and an addition") ? 0 : 1;
        // 4: temporary-works geometry
        bad += check(s, Math.abs(TempWorks.distToSegment(5, 3, 0, 0, 10, 0) - 3) < 1e-9, "single-line corridor distance") ? 0 : 1;
        ok = 8 - bad;
        say(s, (bad == 0 ? "§aPlanning self-test PASSED " : "§cPlanning self-test FAILED ") + ok + "/8");
    }

    private static boolean check(ICommandSender s, boolean pass, String what) { say(s, (pass ? "§a ✔ " : "§c ✖ ") + what); return pass; }

    static void say(ICommandSender s, String m) { if (m != null && !m.isEmpty()) for (String l : m.split("\n")) s.sendMessage(new TextComponentString(l)); }
}
