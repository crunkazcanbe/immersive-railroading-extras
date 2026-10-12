package com.dogpound.railmap.incident;

import com.dogpound.railmap.infra.InfraData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import java.util.*;

/**
 * Graded operating scenarios (her ChatGPT list batch 3, #47 - extends the scenario lab, #80, and runs on the digital
 * twin's shadow simulation, TwinSim). Five standard drills, each a real disruption put on the LIVE railway's model:
 * <ul>
 *   <li>PEAK - the evening rush: every dwell +25 s (crowds at the doors), more small delays;</li>
 *   <li>POWER FAIL - the traction supply to the busiest stretch is lost for 20 min (electric trains stand, diesels run);</li>
 *   <li>BLOCKED JUNCTION - the station served by the most lines is closed for 15 min;</li>
 *   <li>FREIGHT SURGE - two extra trains on the busiest line, all of its trains 15 % slower (heavy freight);</li>
 *   <li>WEATHER - a storm: everything 30 % slower, dwells +10 s, more variation and failures.</li>
 * </ul>
 * Each drill runs three times: NORMAL (no disruption), DISRUPTED with no response, and DISRUPTED with YOUR RESPONSE -
 * the dwell / speed / train changes set on the Scenario page's knobs. The performance index (punctuality 50 %,
 * departures kept 30 %, average delay 20 %) of the response against normal is graded A (95 %+) to F; the share of the
 * lost performance your response won back is shown too. Grades are kept (best per drill, history) and fire a quest
 * event. The live world is never touched.
 */
public final class GradedDrills {
    private GradedDrills() {}

    public static final String[] DRILLS = {"PEAK", "POWER FAIL", "BLOCKED JUNCTION", "FREIGHT SURGE", "WEATHER"};

    static double index(TwinSim.Result r, TwinSim.Result base) {
        double keep = base.deps <= 0 ? 100 : Math.min(100, r.deps / base.deps * 100);
        return 0.5 * r.punctual() + 0.3 * keep + 0.2 * Math.max(0, 100 - r.avgDelay() / 3);
    }

    public static String grade(double pct) { return pct >= 95 ? "A" : pct >= 88 ? "B" : pct >= 80 ? "C" : pct >= 70 ? "D" : pct >= 60 ? "E" : "F"; }

    /** the disruption of drill d on the model */
    static String disrupt(TwinSim.Model m, TwinSim.Result base, TwinSim.Case c, int d) {
        switch (d) {
            case 0: c.dwellDelta.put("*", 25); c.failChance = 0.05; return "every dwell +25 s, 5 % small delays";
            case 1: {
                String seg = busiest(base.segBusy, m.segments().keySet());
                if (seg == null) return "no stretch to cut";
                c.powerOff.put(seg, new double[]{0, 20 * 60});
                return "traction power lost on " + seg + " for 20 min" + (m.electrified.contains(seg) ? "" : " (not electrified - no electric trains stop)");
            }
            case 2: {
                Map<Long, Integer> served = new HashMap<>();
                for (long[] st : m.lines.values()) { Set<Long> s = new HashSet<>(); for (long k : st) s.add(k); for (long k : s) served.merge(k, 1, Integer::sum); }
                long best = 0; int most = -1;
                for (Map.Entry<Long, Integer> e : served.entrySet()) if (e.getValue() > most) { most = e.getValue(); best = e.getKey(); }
                if (most < 0) return "no station";
                c.closeStop.put(m.name(best), new double[]{0, 15 * 60});
                return m.name(best) + " (" + most + " lines) closed for 15 min";
            }
            case 3: {
                String line = busiest(base.perLine, m.lines.keySet());
                if (line == null) return "no line";
                c.trainsDelta.merge(line, 2, Integer::sum);
                c.speedPct.merge(line, -15, Integer::sum);
                return "2 extra heavy trains on " + line + ", its trains 15 % slower";
            }
            default: c.speedPct.put("*", -30); c.dwellDelta.merge("*", 10, Integer::sum); c.variance = 0.12; c.failChance = 0.06; return "storm: 30 % slower, dwells +10 s, more failures";
        }
    }

    private static String busiest(Map<String, Double> m, Collection<String> fallback) {
        String best = null; double v = -1;
        for (Map.Entry<String, Double> e : m.entrySet()) if (e.getValue() > v) { v = e.getValue(); best = e.getKey(); }
        if (best == null && !fallback.isEmpty()) best = fallback.iterator().next();
        return best;
    }

    /** your response: the scenario knobs (dwell / speed / trains on a line) */
    static String respond(TwinSim.Model m, TwinSim.Case c, NBTTagCompound p) {
        List<String> said = new ArrayList<>();
        if (p.getInteger("dwell") != 0) { c.dwellDelta.merge("*", p.getInteger("dwell"), Integer::sum); said.add("dwell " + p.getInteger("dwell") + " s"); }
        if (p.getInteger("speed") != 0) { c.speedPct.merge("*", p.getInteger("speed"), Integer::sum); said.add("speed " + p.getInteger("speed") + " %"); }
        List<String> ln = new ArrayList<>(m.lines.keySet());
        if (p.getInteger("trains") != 0 && !ln.isEmpty()) {
            String l = ln.get(Math.min(ln.size() - 1, Math.max(0, p.getInteger("line"))));
            c.trainsDelta.merge(l, p.getInteger("trains"), Integer::sum);
            said.add("trains " + p.getInteger("trains") + " on " + l);
        }
        return said.isEmpty() ? "none (set the knobs as your response)" : String.join(", ", said);
    }

    public static String run(World w, int d, EntityPlayer who) {
        if (d < 0 || d >= DRILLS.length) return "§cNo such drill";
        TwinSim.Model m = TwinSim.live(w);
        if (m.trains.isEmpty()) return "§7The twin has no driverless trains on lines to drill with";
        TwinSim.Case base = new TwinSim.Case();
        base.minutes = IncidentConfig.scenarioMinutes;
        base.headway = IncidentConfig.scenarioHeadwaySeconds;
        TwinSim.Result rn = TwinSim.run(m, base);
        TwinSim.Case dis = TwinSim.copy(base);
        String what = disrupt(m, rn, dis, d);
        TwinSim.Case resp = TwinSim.copy(dis);
        String how = respond(m, resp, Scenario.params(w));
        TwinSim.Result rd = TwinSim.run(m, dis), rr = TwinSim.run(m, resp);
        double pn = index(rn, rn), pd = index(rd, rn), pr = index(rr, rn);
        double pct = pn <= 0 ? 0 : pr / pn * 100, won = pn - pd <= 0.5 ? 100 : Math.max(-100, (pr - pd) / (pn - pd) * 100);
        String g = grade(pct);
        // the scenario page's result table: as is (= normal) / scenario (= with your response)
        Analytics an = Analytics.get(w);
        NBTTagList res = new NBTTagList();
        row(res, "Punctuality % (disrupted " + f(rd.punctual()) + ")", rn.punctual(), rr.punctual());
        row(res, "Departures (disrupted " + f(rd.deps) + ")", rn.deps, rr.deps);
        row(res, "Average delay s (disrupted " + f(rd.avgDelay()) + ")", rn.avgDelay(), rr.avgDelay());
        row(res, "Worst delay s", rn.maxDelay, rr.maxDelay);
        row(res, "Train-minutes held", (rn.held + rn.platformWait) / 60, (rr.held + rr.platformWait) / 60);
        row(res, "Connections kept %", rn.connKept(), rr.connKept());
        row(res, "Performance index", pn, pr);
        NBTTagCompound gr = new NBTTagCompound();
        gr.setString("w", "GRADE " + g + " - " + DRILLS[d]); gr.setString("a", "100"); gr.setString("b", f(pct)); gr.setString("d", String.format(Locale.ROOT, "won back %.0f%%", won));
        res.appendTag(gr);
        an.snapshot.setTag("res", res);
        an.snapshot.setString("resFor", "DRILL " + DRILLS[d] + ": " + what + " | response: " + how);
        an.snapshot.setLong("ranAt", w.getTotalWorldTime());
        an.markDirty();
        InfraData id = InfraData.get(w);
        String prev = id.bestGrade.get(DRILLS[d]);
        if (prev == null || g.compareTo(prev) < 0) id.bestGrade.put(DRILLS[d], g);
        id.grades.addFirst(new String[]{com.dogpound.railmap.alarm.Recorder.clock(w, w.getTotalWorldTime()), DRILLS[d], g, f(pct), how});
        while (id.grades.size() > 20) id.grades.removeLast();
        id.markDirty();
        com.dogpound.railmap.alarm.Recorder.event(w, "Graded drill " + DRILLS[d] + ": grade " + g + " (" + f(pct) + " % of normal) - response " + how);
        com.dogpound.railmap.api.QuestEvents.fire(w, who, null, "scenario_graded", "drill", DRILLS[d], "grade", g, "percent", (int) Math.round(pct));
        return String.format(Locale.ROOT, "§bDrill %s: §fGRADE %s§b - %.0f%% of normal (disrupted with no response %.0f%%), your response won back %.0f%% of the loss", DRILLS[d], g, pct, pn <= 0 ? 0 : pd / pn * 100, won);
    }

    static String f(double v) { return String.format(Locale.ROOT, "%.1f", v); }

    private static void row(NBTTagList l, String what, double a, double b) {
        NBTTagCompound c = new NBTTagCompound();
        c.setString("w", what); c.setString("a", f(a)); c.setString("b", f(b));
        double d = b - a;
        c.setString("d", (d > 0 ? "+" : "") + f(d));
        l.appendTag(c);
    }

    /** SG drill · best grade ; SH time · drill · grade · % · response */
    public static void rows(World w, List<String[]> out) {
        InfraData d = InfraData.get(w);
        for (String dr : DRILLS) out.add(new String[]{"SG", dr, d.bestGrade.getOrDefault(dr, "-")});
        for (String[] g : d.grades) out.add(new String[]{"SH", g[0], g[1], g[2], g[3], g[4]});
    }
}
