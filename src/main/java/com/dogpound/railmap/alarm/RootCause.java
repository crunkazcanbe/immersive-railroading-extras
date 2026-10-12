package com.dogpound.railmap.alarm;

import com.dogpound.railmap.grid.GridData;
import com.dogpound.railmap.incident.IncidentConfig;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Automated root-cause investigation (her "next 48" list #31): the Correlator's one-level "probable root" grown into an
 * incident DEPENDENCY GRAPH. Every alarm of the last half hour is a node; an edge says "this could have caused that",
 * with the evidence written out:
 * <ul>
 *   <li>TIME - the cause came first, and how long before;</li>
 *   <li>PLACE - how far apart they are (or that one has no location);</li>
 *   <li>RULE - the physical reason this kind of failure causes that kind (a power trip darkens signals, a fire burns
 *       cables...);</li>
 *   <li>NETWORK - both sit on the same grid network (the strongest proof a power fault is behind it);</li>
 *   <li>CORRELATOR - the live correlator already folded it under that root.</li>
 * </ul>
 * Each edge gets a confidence; the best incoming edge decides what an alarm is: the ORIGINAL FAULT (nothing explains it
 * but it explains others), a SYMPTOM (explained, with the chain back to the original), or UNRELATED. The console (Ctrl
 * tab, RCA page) draws the tree and the evidence; /irextras ctl rca prints it.
 */
public final class RootCause {
    private RootCause() {}

    public static final class Edge {
        public final AlarmManager.Alarm cause, effect;
        public final List<String> evidence = new ArrayList<>();
        public double confidence;
        Edge(AlarmManager.Alarm c, AlarmManager.Alarm e) { cause = c; effect = e; }
    }

    public static final class Graph {
        public final List<AlarmManager.Alarm> nodes = new ArrayList<>();
        public final List<Edge> edges = new ArrayList<>();
        public final Map<Integer, Edge> best = new HashMap<>();             // effect id -> its best explanation
        public final Map<Integer, String> role = new HashMap<>();           // ORIGINAL / SYMPTOM / UNRELATED
        public final Map<Integer, Integer> origin = new HashMap<>();        // alarm -> the original fault behind it
        public List<AlarmManager.Alarm> originals() { List<AlarmManager.Alarm> l = new ArrayList<>(); for (AlarmManager.Alarm a : nodes) if ("ORIGINAL".equals(role.get(a.id))) l.add(a); return l; }
        public List<AlarmManager.Alarm> children(AlarmManager.Alarm a) { List<AlarmManager.Alarm> l = new ArrayList<>(); for (Edge e : best.values()) if (e.cause == a) l.add(e.effect); l.sort(Comparator.comparingLong(x -> x.first)); return l; }
    }

    private static final Map<String, String> WHY = new HashMap<>();
    static {
        WHY.put("FIRE>POWER", "fire burns cables and trips the protection feeding them");
        WHY.put("FIRE>FAULT", "fire breaks down insulation - short circuits / earth faults");
        WHY.put("FIRE>MACHINE", "fire stops motors and generators near it");
        WHY.put("FIRE>WIRED", "fire burns the 12 V control wiring");
        WHY.put("FIRE>SIGNAL", "fire takes out lineside signalling");
        WHY.put("FIRE>AUTO", "trains are held / stopped away from a fire");
        WHY.put("FIRE>INCIDENT", "a fire becomes a train incident");
        WHY.put("FAULT>POWER", "a short circuit or earth fault makes the protection trip the breaker");
        WHY.put("FAULT>MACHINE", "a fault takes the supply off machines");
        WHY.put("FAULT>WIRED", "a faulted supply drops 12 V chargers");
        WHY.put("FAULT>SIGNAL", "signals lose their supply when the fault is cleared");
        WHY.put("FAULT>AUTO", "trains lose traction when the fault is cleared");
        WHY.put("POWER>MACHINE", "a tripped supply stops every machine on it");
        WHY.put("POWER>WIRED", "12 V chargers lose their AC - gear runs on battery, then dies");
        WHY.put("POWER>AUTO", "electric trains lose traction power and stall");
        WHY.put("POWER>SIGNAL", "signal heads go dark when their supply goes");
        WHY.put("POWER>INCIDENT", "stranded trains become incidents");
        WHY.put("ALLSTOP>AUTO", "an all-stop brakes every train");
        WHY.put("ALLSTOP>SIGNAL", "an all-stop puts signals to danger");
        WHY.put("ALLSTOP>INCIDENT", "an all-stop strands trains");
        WHY.put("WIRED>SIGNAL", "signals fail safe when their 12 V goes");
        WHY.put("WIRED>AUTO", "trains are held when signalling loses 12 V");
        WHY.put("INCIDENT>AUTO", "trains are held behind an incident");
        WHY.put("INCIDENT>SIGNAL", "signals are held at danger round an incident");
        WHY.put("CONSOLE>AUTO", "a control-room failure stops automatic train control");
    }

    /** the grid network an alarm's position belongs to (the plant at or touching it), -1 = none */
    static int net(GridData d, long pos) {
        if (pos == 0) return -1;
        BlockPos p = BlockPos.fromLong(pos);
        GridData.Net n = d.netOf(p);
        if (n != null) return n.id;
        for (EnumFacing f : EnumFacing.values()) { n = d.netOf(p.offset(f)); if (n != null) return n.id; }
        return -1;
    }

    public static Graph build(World w) {
        Graph g = new Graph();
        AlarmManager m = AlarmManager.get(w);
        long now = w.getTotalWorldTime();
        for (AlarmManager.Alarm a : m.all()) if (now - a.first < 36000 || a.seq(now) != 0) g.nodes.add(a);
        g.nodes.sort(Comparator.comparingLong(a -> a.first));
        GridData gd = GridData.get(w);
        Map<Integer, Integer> nets = new HashMap<>();
        for (AlarmManager.Alarm a : g.nodes) nets.put(a.id, net(gd, a.pos));
        long window = IncidentConfig.correlationSeconds * 20L * 3;
        double radius = IncidentConfig.correlationRadius * 2.0;
        for (AlarmManager.Alarm e : g.nodes) {
            for (AlarmManager.Alarm c : g.nodes) {
                if (c == e || c.first > e.first || e.first - c.first > window) continue;
                boolean linked = e.parent == c.id;
                String rule = WHY.get(c.cat + ">" + e.cat);
                if (rule == null && !linked) continue;
                Edge ed = new Edge(c, e);
                double dt = (e.first - c.first) / 20.0;
                ed.evidence.add(String.format(Locale.ROOT, "TIME: #%d came first, %.1f s before #%d", c.id, dt, e.id));
                double conf = 0.25 + 0.15 * Math.max(0, 1 - dt / (window / 20.0));
                if (c.pos != 0 && e.pos != 0) {
                    double dist = Math.sqrt(BlockPos.fromLong(c.pos).distanceSq(BlockPos.fromLong(e.pos)));
                    if (dist > radius && !linked) continue;
                    ed.evidence.add(String.format(Locale.ROOT, "PLACE: %.0f m apart", dist));
                    conf += 0.15 * Math.max(0, 1 - dist / radius);
                } else ed.evidence.add("PLACE: " + (c.pos == 0 ? "#" + c.id : "#" + e.id) + " has no location - time only");
                if (rule != null) { ed.evidence.add("RULE: " + c.cat + " -> " + e.cat + ": " + rule); conf += 0.1; }
                int na = nets.get(c.id), nb = nets.get(e.id);
                if (na >= 0 && na == nb) { ed.evidence.add("NETWORK: both on grid network #" + na); conf += 0.25; }
                else if (na >= 0 && nb >= 0) { ed.evidence.add("NETWORK: different grid networks (#" + na + ", #" + nb + ") - weaker"); conf -= 0.1; }
                if (linked) { ed.evidence.add("CORRELATOR: folded under #" + c.id + " when it came in"); conf += 0.2; }
                if (c.cat == e.cat) conf -= 0.15;
                ed.confidence = Math.max(0.05, Math.min(0.99, conf));
                g.edges.add(ed);
                Edge was = g.best.get(e.id);
                if (was == null || ed.confidence > was.confidence) g.best.put(e.id, ed);
            }
        }
        // weak explanations don't count; then roles and the chain back to the original
        g.best.values().removeIf(ed -> ed.confidence < 0.45);
        Set<Integer> causes = new HashSet<>();
        for (Edge ed : g.best.values()) causes.add(ed.cause.id);
        for (AlarmManager.Alarm a : g.nodes) {
            if (g.best.containsKey(a.id)) g.role.put(a.id, "SYMPTOM");
            else g.role.put(a.id, causes.contains(a.id) ? "ORIGINAL" : "UNRELATED");
            AlarmManager.Alarm cur = a;
            Set<Integer> seen = new HashSet<>();
            while (g.best.containsKey(cur.id) && seen.add(cur.id)) cur = g.best.get(cur.id).cause;
            g.origin.put(a.id, cur.id);
        }
        return g;
    }

    /** text report: every original fault with its tree of symptoms (indented), confidence and evidence count */
    public static List<String> report(World w) {
        Graph g = build(w);
        List<String> out = new ArrayList<>();
        List<AlarmManager.Alarm> roots = g.originals();
        out.add(roots.size() + " original fault(s), " + g.best.size() + " symptom(s) explained, " + (g.nodes.size() - roots.size() - g.best.size()) + " unrelated alarm(s)");
        for (AlarmManager.Alarm r : roots) tree(w, g, r, 0, out, new HashSet<>());
        return out;
    }

    private static void tree(World w, Graph g, AlarmManager.Alarm a, int depth, List<String> out, Set<Integer> seen) {
        if (!seen.add(a.id) || out.size() > 200) return;
        Edge e = g.best.get(a.id);
        out.add(String.join("", Collections.nCopies(depth, "  ")) + (depth == 0 ? "ORIGINAL #" : "-> #") + a.id + " " + a.cat + " " + Recorder.clock(w, a.first) + " " + a.text
                + (e == null ? "" : String.format(Locale.ROOT, "  [%.0f%%, %d proofs]", e.confidence * 100, e.evidence.size())));
        for (AlarmManager.Alarm c : g.children(a)) tree(w, g, c, depth + 1, out, seen);
    }

    /** the evidence behind one alarm: its role, its chain back to the original, every proof */
    public static List<String> explain(World w, int id) {
        Graph g = build(w);
        List<String> out = new ArrayList<>();
        AlarmManager.Alarm a = null;
        for (AlarmManager.Alarm x : g.nodes) if (x.id == id) a = x;
        if (a == null) { out.add("No alarm #" + id + " in the last half hour"); return out; }
        out.add("#" + a.id + " " + a.cat + " " + a.sev + ": " + a.text);
        out.add("ROLE: " + g.role.get(a.id) + (g.origin.get(a.id) != a.id ? " - original fault #" + g.origin.get(a.id) : ""));
        StringBuilder chain = new StringBuilder("CHAIN: #" + a.id);
        AlarmManager.Alarm cur = a;
        Set<Integer> seen = new HashSet<>();
        while (g.best.containsKey(cur.id) && seen.add(cur.id)) { cur = g.best.get(cur.id).cause; chain.append(" <- #").append(cur.id).append(" ").append(cur.cat); }
        out.add(chain.toString());
        Edge e = g.best.get(a.id);
        if (e != null) { out.add(String.format(Locale.ROOT, "BEST EXPLANATION: #%d (%.0f%% confidence)", e.cause.id, e.confidence * 100)); out.addAll(e.evidence); }
        for (Edge o : g.edges) if (o.effect == a && o != e) out.add(String.format(Locale.ROOT, "also possible: #%d %s (%.0f%%)", o.cause.id, o.cause.cat, o.confidence * 100));
        List<AlarmManager.Alarm> kids = g.children(a);
        if (!kids.isEmpty()) { StringBuilder k = new StringBuilder("EXPLAINS: "); for (AlarmManager.Alarm c : kids) k.append("#").append(c.id).append(" "); out.add(k.toString()); }
        return out;
    }
}
