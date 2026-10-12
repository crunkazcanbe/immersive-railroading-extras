package com.dogpound.railmap.planning;

import com.dogpound.railmap.incident.TwinSim;
import net.minecraft.world.World;

import java.util.*;

/**
 * What-if queries in the Control Center (her "next 48" list #48): a question in plain words, answered by a constrained
 * run of the digital twin with the ASSUMPTIONS it made, the RESULTS against "as is" and the TRADE-OFFS.
 * <ul>
 *   <li>{@code electrify <line|*>} - contact wire over every stretch the line runs: time saved, fuel no longer bought,
 *   electricity bought instead, capital cost and the payback in hours of operation;</li>
 *   <li>{@code capacity <A> <B>} - the cheapest way to DOUBLE the trains per hour between two stations: more trains,
 *   CBTC, doubling single track, shorter dwells, and combinations - each run, costed, the cheapest that gets there
 *   recommended;</li>
 *   <li>{@code close <A> <B> [min]}, {@code single <A> <B>}, {@code power <A> <B> [min]} - what a closure, single-line
 *   working or a power cut on that stretch would do;</li>
 *   <li>{@code trains <line> <n>}, {@code dwell <line> <s>}, {@code speed <line> <%>}, {@code cbtc <line>}.</li>
 * </ul>
 */
public final class WhatIf {
    private WhatIf() {}

    public static final String[] PRESETS = {"electrify *", "capacity", "close", "single", "power", "trains +1", "dwell -10", "speed +10", "cbtc", "expand"};

    public static String ask(World w, String query, String who) {
        TwinSim.Model m = TwinSim.live(w);
        PlanData d = PlanData.get(w);
        d.lastWhatIf.clear();
        if (m.trains.isEmpty()) { d.lastWhatIf.add(new String[]{"QH", query, "NO TRAINS TO SIMULATE"}); d.markDirty(); return "§7The twin has no driverless trains on lines"; }
        String[] a = query.trim().split("\\s+");
        String verb = a[0].toLowerCase(Locale.ROOT);
        TwinSim.Case base = new TwinSim.Case();
        base.minConnect = PlanConfig.minConnectSeconds;
        String answer;
        try {
            switch (verb) {
                case "electrify": answer = electrify(m, d, base, a.length > 1 ? a[1].replace('_', ' ') : "*"); break;
                case "expand": case "advise": case "advisor": answer = ExpansionAdvisor.advise(w, m, d, base); break;   // infra #50
                case "capacity": case "double": answer = capacity(m, d, base, arg(a, 1, m, w, true), arg(a, 2, m, w, false)); break;
                case "close": case "single": case "power": {
                    String seg = segBetween(m, arg(a, 1, m, w, true), arg(a, 2, m, w, false));
                    if (seg == null) return "§cNo stretch between those stations";
                    TwinSim.Case c = TwinSim.copy(base);
                    double mins = a.length > 3 ? Double.parseDouble(a[3]) : 30;
                    if (verb.equals("close")) c.closeSeg.put(seg, new double[]{0, mins * 60});
                    else if (verb.equals("single")) c.segTracks.put(seg, 1);
                    else c.powerOff.put(seg, new double[]{0, mins * 60});
                    answer = compare(m, d, base, c, query, 0);
                    break;
                }
                case "trains": case "dwell": case "speed": case "cbtc": {
                    String line = a.length > 1 && m.lines.containsKey(a[1].replace('_', ' ')) ? a[1].replace('_', ' ') : TwinLab.line(w, m);
                    int v = a.length > 2 ? Integer.parseInt(a[a.length - 1].replace("+", "")) : verb.equals("trains") ? 1 : verb.equals("dwell") ? -10 : 10;
                    TwinSim.Case c = TwinSim.copy(base);
                    double cost = 0;
                    if (verb.equals("trains")) { c.trainsDelta.put(line, v); cost = Math.max(0, v) * PlanConfig.costTrain; }
                    if (verb.equals("dwell")) c.dwellDelta.put(line, v);
                    if (verb.equals("speed")) c.speedPct.put(line, v);
                    if (verb.equals("cbtc")) { c.cbtcLines.add(line); cost = PlanConfig.costCbtcPerLine; }
                    answer = compare(m, d, base, c, query + " (" + line + ")", cost);
                    break;
                }
                default:
                    return "§cAsk: expand (advisor) | electrify <line|*> | capacity <A> <B> | close <A> <B> [min] | single <A> <B> | power <A> <B> [min] | trains <line> <n> | dwell <line> <s> | speed <line> <%> | cbtc <line>";
            }
        } catch (NumberFormatException e) {
            return "§cA number didn't read: " + e.getMessage();
        }
        d.markDirty();
        com.dogpound.railmap.alarm.Recorder.event(w, "What-if by " + who + ": " + query);
        return answer;
    }

    /** a station from the query, or the working case's stretch ends when not given */
    private static String arg(String[] a, int i, TwinSim.Model m, World w, boolean first) {
        if (a.length > i) {
            String want = a[i].replace('_', ' ');
            for (String s : m.names.values()) if (s.equalsIgnoreCase(want)) return s;
            for (String s : m.names.values()) if (s.toLowerCase(Locale.ROOT).startsWith(want.toLowerCase(Locale.ROOT))) return s;
        }
        String seg = TwinLab.seg(w, m);
        String[] p = seg.split("~");
        return p.length == 2 ? p[first ? 0 : 1] : seg;
    }

    private static String segBetween(TwinSim.Model m, String a, String b) {
        String k = a.compareTo(b) <= 0 ? a + "~" + b : b + "~" + a;
        return m.segments().containsKey(k) ? k : null;
    }

    /** the stretches of the shortest way from a to b (station names) */
    private static List<String> route(TwinSim.Model m, String a, String b) {
        Map<String, List<String>> adj = new HashMap<>();
        for (String k : m.segments().keySet()) { String[] p = k.split("~"); if (p.length != 2) continue; adj.computeIfAbsent(p[0], x -> new ArrayList<>()).add(p[1]); adj.computeIfAbsent(p[1], x -> new ArrayList<>()).add(p[0]); }
        Map<String, String> prev = new HashMap<>();
        Deque<String> q = new ArrayDeque<>();
        q.add(a); prev.put(a, null);
        while (!q.isEmpty()) { String u = q.poll(); if (u.equals(b)) break; for (String v : adj.getOrDefault(u, Collections.emptyList())) if (!prev.containsKey(v)) { prev.put(v, u); q.add(v); } }
        List<String> o = new ArrayList<>();
        if (!prev.containsKey(b)) return o;
        for (String v = b; prev.get(v) != null; v = prev.get(v)) { String u = prev.get(v); o.add(u.compareTo(v) <= 0 ? u + "~" + v : v + "~" + u); }
        return o;
    }

    private static void results(PlanData d, TwinSim.Result a, TwinSim.Result b) {
        res(d, "Punctuality %", a.punctual(), b.punctual(), true);
        res(d, "Average delay s", a.avgDelay(), b.avgDelay(), false);
        res(d, "Departures", a.deps, b.deps, true);
        res(d, "Connections kept %", a.connKept(), b.connKept(), true);
        res(d, "Platform conflicts", a.platformConflicts, b.platformConflicts, false);
        res(d, "Energy kWh", a.kwh, b.kwh, false);
        res(d, "Diesel litres", a.litres, b.litres, false);
        res(d, "Running cost $/h", cost(a), cost(b), false);
    }

    private static double cost(TwinSim.Result r) { return (r.kwh * PlanConfig.priceKwh + r.litres * PlanConfig.priceLitre) * 60 / Math.max(1, r.minutes); }

    private static void res(PlanData d, String what, double a, double b, boolean higherBetter) {
        double dl = b - a;
        d.lastWhatIf.add(new String[]{"QR", what, String.format(Locale.ROOT, "%.1f", a), String.format(Locale.ROOT, "%.1f", b), String.format(Locale.ROOT, "%+.1f", dl),
                Math.abs(dl) < 0.05 ? "0" : (dl > 0) == higherBetter ? "+" : "-"});
    }

    private static void assumptions(PlanData d, TwinSim.Case c, String... more) {
        for (String s : c.describe()) d.lastWhatIf.add(new String[]{"QA", s});
        for (String s : more) d.lastWhatIf.add(new String[]{"QA", s});
    }

    private static String compare(TwinSim.Model m, PlanData d, TwinSim.Case base, TwinSim.Case c, String query, double capex) {
        TwinSim.Result a = TwinSim.run(m, base), b = TwinSim.run(m, c);
        assumptions(d, c, capex > 0 ? "capital cost $" + Math.round(capex) + " (planning config prices)" : "no capital cost");
        results(d, a, b);
        String verdict = b.punctual() >= a.punctual() - 0.5 && b.deps >= a.deps * 0.98 ? "WORTH IT" : b.punctual() < a.punctual() - 5 || b.deadlocks > 0 ? "HARMFUL" : "MARGINAL";
        if (b.deadlocks > 0) d.lastWhatIf.add(new String[]{"QT", "deadlock predicted", "-", String.join(", ", b.deadlockAt), "AVOID"});
        for (String[] bn : b.bottlenecks(m, 4)) d.lastWhatIf.add(new String[]{"QT", "bottleneck " + bn[0], "-", bn[1] + " train-min waiting, " + bn[2] + " % occupied", "WATCH"});
        d.lastWhatIf.add(0, new String[]{"QH", query, verdict});
        return String.format(Locale.ROOT, "§bWhat if %s: §f%s §7(punctuality %.1f -> %.1f %%, departures %.0f -> %.0f)", query, verdict, a.punctual(), b.punctual(), a.deps, b.deps);
    }

    private static String electrify(TwinSim.Model m, PlanData d, TwinSim.Case base, String line) {
        TwinSim.Case c = TwinSim.copy(base);
        double km = 0;
        int n = 0;
        for (Map.Entry<String, long[]> e : m.segments().entrySet()) {
            boolean used = line.equals("*");
            if (!used) { long[] st = m.lines.get(line); if (st != null) for (long k : st) used |= m.name(k).equals(e.getKey().split("~")[0]) || m.name(k).equals(e.getKey().split("~")[1]); }
            if (!used || m.electrified.contains(e.getKey())) continue;
            c.electrify.add(e.getKey());
            km += m.km(e.getValue()[0], e.getValue()[1]) * m.tracks(e.getKey());
            n++;
        }
        if (n == 0) { d.lastWhatIf.add(new String[]{"QH", "electrify " + line, "ALREADY WIRED"}); return "§7Every stretch of " + line + " already has contact wire"; }
        int dieselTrains = 0;
        for (TwinSim.Train t : m.trains) if (!t.electric && (line.equals("*") || t.line.equals(line))) dieselTrains++;
        // once wired, the diesels on it are replaced by electrics of the same size
        TwinSim.Model em = copyElectric(m, line);
        TwinSim.Result a = TwinSim.run(m, base), b = TwinSim.run(em, c);
        double capex = km * PlanConfig.costWirePerKm;
        double saving = cost(a) - cost(b);
        assumptions(d, c, String.format(Locale.ROOT, "%.2f km of wire (track-km) over %d stretch(es) at $%d/km", km, n, PlanConfig.costWirePerKm),
                dieselTrains + " diesel train(s) replaced by electrics of the same length (rolling stock not costed)",
                "energy $" + PlanConfig.priceKwh + "/kWh, diesel $" + PlanConfig.priceLitre + "/L; electrics accelerate harder: -7 % run time");
        results(d, a, b);
        double payback = saving <= 0 ? -1 : capex / saving;
        d.lastWhatIf.add(new String[]{"QT", "contact wire", "$" + Math.round(capex), String.format(Locale.ROOT, "saves $%.1f/h running, %s", saving, payback < 0 ? "never pays back" : String.format(Locale.ROOT, "pays back in %.0f h of operation", payback)),
                payback > 0 && payback < 200 ? "GOOD" : payback > 0 ? "SLOW" : "NO"});
        d.lastWhatIf.add(new String[]{"QT", "time", "-", String.format(Locale.ROOT, "%.0f s average delay -> %.0f s; %.0f -> %.0f departures", a.avgDelay(), b.avgDelay(), a.deps, b.deps), b.deps >= a.deps ? "GOOD" : "NO"});
        d.lastWhatIf.add(new String[]{"QT", "risk", "-", "electric trains stop in a power cut - diesels don't (test it: power <A> <B>)", "NOTE"});
        String verdict = payback > 0 && payback < 200 ? "WORTH IT" : payback > 0 ? "MARGINAL" : "NOT WORTH IT";
        d.lastWhatIf.add(0, new String[]{"QH", "electrify " + line, verdict});
        return String.format(Locale.ROOT, "§bElectrify %s: §f%s §7($%.0f, saves $%.1f/h%s)", line, verdict, capex, saving, payback > 0 ? String.format(Locale.ROOT, ", payback %.0f h", payback) : "");
    }

    private static TwinSim.Model copyElectric(TwinSim.Model m, String line) {
        TwinSim.Model o = new TwinSim.Model();
        o.lines.putAll(m.lines); o.cbtc.addAll(m.cbtc); o.booked.putAll(m.booked); o.names.putAll(m.names); o.tracks.putAll(m.tracks); o.electrified.addAll(m.electrified);
        for (TwinSim.Train t : m.trains) {
            TwinSim.Train c = new TwinSim.Train();
            c.name = t.name; c.line = t.line; c.st = t.st; c.idx = t.idx; c.dir = t.dir; c.dwell = t.dwell; c.cars = t.cars; c.shuttle = t.shuttle; c.at = t.at; c.freight = t.freight; c.kmh = t.kmh; c.left = t.left;
            c.electric = t.electric || line.equals("*") || t.line.equals(line);
            o.trains.add(c);
        }
        return o;
    }

    /** cheapest way to double trains per hour between a and b */
    private static String capacity(TwinSim.Model m, PlanData d, TwinSim.Case base, String a, String b) {
        List<String> route = route(m, a, b);
        if (route.isEmpty()) { d.lastWhatIf.add(new String[]{"QH", "capacity " + a + " - " + b, "NO ROUTE"}); return "§cNo route between " + a + " and " + b; }
        Set<String> lines = new TreeSet<>();
        for (Map.Entry<String, long[]> e : m.lines.entrySet())
            for (int i = 0; i + 1 < e.getValue().length; i++) if (route.contains(m.seg(e.getValue()[i], e.getValue()[i + 1]))) lines.add(e.getKey());
        TwinSim.Result r0 = TwinSim.run(m, base);
        double tph0 = tph(r0, route);
        Map<String, Integer> perLine = new HashMap<>();
        for (TwinSim.Train t : m.trains) if (lines.contains(t.line)) perLine.merge(t.line, 1, Integer::sum);
        int trainsNow = 0;
        for (int v : perLine.values()) trainsNow += v;
        double singleKm = 0;
        for (String s : route) if (m.tracks(s) <= 1) { long[] e = m.segments().get(s); singleKm += m.km(e[0], e[1]); }
        List<Object[]> opts = new ArrayList<>();          // name, case, capex
        TwinSim.Case more = TwinSim.copy(base);
        for (String l : lines) more.trainsDelta.put(l, Math.max(1, perLine.getOrDefault(l, 1)));
        opts.add(new Object[]{"double the trains (" + trainsNow + " more)", more, (double) trainsNow * PlanConfig.costTrain});
        TwinSim.Case cbtc = TwinSim.copy(base);
        cbtc.cbtcLines.addAll(lines);
        opts.add(new Object[]{"CBTC on " + lines.size() + " line(s)", cbtc, (double) lines.size() * PlanConfig.costCbtcPerLine});
        TwinSim.Case dwell = TwinSim.copy(base);
        for (String l : lines) dwell.dwellDelta.put(l, -10);
        opts.add(new Object[]{"cut dwells by 10 s", dwell, 0.0});
        TwinSim.Case moreCbtc = TwinSim.copy(more);
        moreCbtc.cbtcLines.addAll(lines);
        opts.add(new Object[]{"double the trains + CBTC", moreCbtc, trainsNow * (double) PlanConfig.costTrain + lines.size() * PlanConfig.costCbtcPerLine});
        if (singleKm > 0) {
            TwinSim.Case dbl = TwinSim.copy(more);
            for (String s : route) dbl.segTracks.put(s, 2);
            opts.add(new Object[]{String.format(Locale.ROOT, "double the trains + double %.2f km single track", singleKm), dbl, trainsNow * (double) PlanConfig.costTrain + singleKm * PlanConfig.costDoublePerKm});
        }
        assumptions(d, base, "route " + a + " - " + b + ": " + route.size() + " stretch(es), lines " + String.join(", ", lines),
                String.format(Locale.ROOT, "now %.1f trains/h over the route; target %.1f", tph0, tph0 * 2),
                "prices: train $" + PlanConfig.costTrain + ", CBTC $" + PlanConfig.costCbtcPerLine + "/line, double track $" + PlanConfig.costDoublePerKm + "/km");
        Object[] best = null;
        TwinSim.Result bestR = null;
        for (Object[] o : opts) {
            TwinSim.Result r = TwinSim.run(m, (TwinSim.Case) o[1]);
            double tph = tph(r, route);
            boolean ok = tph >= tph0 * 1.95 && r.deadlocks == 0;
            d.lastWhatIf.add(new String[]{"QT", (String) o[0], "$" + Math.round((double) o[2]), String.format(Locale.ROOT, "%.1f trains/h (x%.2f), punctuality %.1f %%%s", tph, tph0 <= 0 ? 0 : tph / tph0, r.punctual(), r.deadlocks > 0 ? ", DEADLOCK" : ""),
                    ok ? "DOUBLES" : tph > tph0 * 1.2 ? "PARTIAL" : "NO"});
            if (ok && (best == null || (double) o[2] < (double) best[2])) { best = o; bestR = r; }
        }
        if (bestR != null) results(d, r0, bestR);
        String verdict = best == null ? "NOTHING TRIED DOUBLES IT" : "CHEAPEST: " + best[0];
        d.lastWhatIf.add(0, new String[]{"QH", "capacity " + a + " - " + b, verdict});
        return "§bDouble capacity " + a + " - " + b + ": §f" + verdict + (best == null ? "" : " §7($" + Math.round((double) best[2]) + ")");
    }

    private static double tph(TwinSim.Result r, List<String> route) {
        double worst = Double.MAX_VALUE;
        for (String s : route) worst = Math.min(worst, r.segTrips.getOrDefault(s, 0.0));
        return worst == Double.MAX_VALUE ? 0 : worst * 60 / Math.max(1, r.minutes);
    }
}
