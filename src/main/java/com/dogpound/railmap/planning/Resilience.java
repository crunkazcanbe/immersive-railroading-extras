package com.dogpound.railmap.planning;

import com.dogpound.railmap.auto.Disruptions;
import com.dogpound.railmap.incident.TwinSim;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Network resilience + alternative-route planning (her "next 48" list #3). The network is the stations (towns) and the
 * stretches the lines run between them. The study finds:
 * <ul>
 *   <li><b>single points of failure</b> - stretches that are the only link (graph bridges) and stations everything
 *   passes through (articulation points), and which towns each one would cut off;</li>
 *   <li><b>alternative paths</b> per stretch - how many independent routes join its ends (edge-disjoint paths) and how
 *   far the detour is when it is closed;</li>
 *   <li><b>spare capacity</b> per stretch - trains per hour the timetable puts over it against what its signalling and
 *   track (single / double, CBTC) can carry;</li>
 *   <li><b>towns isolated right now</b> by blocking incidents and possessions;</li>
 *   <li>a <b>resilience score</b> 0-100 and <b>recommended upgrades</b>, most valuable first, with a cost.</li>
 * </ul>
 */
public final class Resilience {
    private Resilience() {}

    public static final class Edge {
        public final String a, b, key;
        public double km, demand, capacity;
        public int tracks = 2, alt;
        public double detourKm = -1;
        public boolean bridge, cutNow, cbtc;
        Edge(String a, String b, String key) { this.a = a; this.b = b; this.key = key; }
        public double spare() { return capacity <= 0 ? 0 : Math.max(-1, 1 - demand / capacity); }
    }

    public static final class Report {
        public int score;
        public final List<Edge> edges = new ArrayList<>();
        public final Map<String, Set<String>> cutsOff = new LinkedHashMap<>();       // bridge key / station -> towns cut off
        public final Set<String> articulation = new TreeSet<>();
        public final Set<String> isolatedNow = new TreeSet<>();
        public final List<String[]> upgrades = new ArrayList<>();                     // {priority, what, why, cost}
        public int towns, components;
    }

    public static Report study(World w) {
        TwinSim.Model m = TwinSim.live(w);
        Report r = new Report();
        Map<String, long[]> segs = m.segments();
        Map<String, Set<String>> adj = new TreeMap<>();
        Map<String, Edge> byKey = new LinkedHashMap<>();
        for (Map.Entry<String, long[]> e : segs.entrySet()) {
            String a = m.name(e.getValue()[0]), b = m.name(e.getValue()[1]);
            if (a.equals(b)) continue;
            Edge ed = new Edge(a, b, e.getKey());
            ed.km = m.km(e.getValue()[0], e.getValue()[1]);
            ed.tracks = m.tracks(e.getKey());
            byKey.put(e.getKey(), ed);
            adj.computeIfAbsent(a, k -> new TreeSet<>()).add(b);
            adj.computeIfAbsent(b, k -> new TreeSet<>()).add(a);
        }
        r.edges.addAll(byKey.values());
        r.towns = adj.size();
        r.components = components(adj, Collections.emptySet(), null).size();
        // demand: trains per hour each line puts over each stretch
        Map<String, Integer> perLine = new HashMap<>();
        for (TwinSim.Train t : m.trains) perLine.merge(t.line, 1, Integer::sum);
        for (Map.Entry<String, long[]> le : m.lines.entrySet()) {
            long[] st = le.getValue();
            int n = perLine.getOrDefault(le.getKey(), 0);
            if (n == 0 || st.length < 2) continue;
            boolean loop = m.loops(le.getKey());
            double cycle = 0;
            int dwell = 20;
            for (TwinSim.Train t : m.trains) if (t.line.equals(le.getKey())) { dwell = t.dwell; break; }
            List<long[]> legs = new ArrayList<>();
            for (int i = 0; i + 1 < st.length; i++) legs.add(new long[]{st[i], st[i + 1]});
            if (loop && st.length > 2) legs.add(new long[]{st[st.length - 1], st[0]});
            for (long[] l : legs) cycle += m.ideal(l[0], l[1], 60) * 1.1 + dwell;
            if (!loop) cycle *= 2;
            double perHour = n * 3600 / Math.max(60, cycle) * (loop ? 1 : 2);   // a shuttle passes each stretch twice a round trip
            for (long[] l : legs) { Edge e = byKey.get(m.seg(l[0], l[1])); if (e != null) { e.demand += perHour; e.cbtc |= m.cbtc.contains(le.getKey()); } }
        }
        for (Edge e : r.edges) {
            double run = 0;
            Long b1 = null;
            for (long[] s : segs.values()) if (m.seg(s[0], s[1]).equals(e.key)) { run = m.ideal(s[0], s[1], 60); b1 = s[0]; break; }
            double head = e.cbtc ? 45 : 90;
            e.capacity = e.tracks <= 1 ? 3600 / Math.max(60, run + 60) : 2 * 3600 / head;
        }
        // bridges + articulation points (Tarjan)
        Set<String> bridges = new HashSet<>();
        tarjan(adj, bridges, r.articulation);
        for (Edge e : r.edges) {
            e.bridge = bridges.contains(e.key);
            Set<String> cut = Collections.singleton(e.key);
            List<Set<String>> comps = components(adj, cut, null);
            if (e.bridge) r.cutsOff.put(e.a + " - " + e.b, smallest(comps));
            e.alt = disjointPaths(adj, e.a, e.b, 3);
            e.detourKm = e.bridge ? -1 : detour(adj, byKey, e);
        }
        for (String s : r.articulation) {
            List<Set<String>> comps = components(adj, Collections.emptySet(), s);
            Set<String> off = smallest(comps);
            r.cutsOff.put("station " + s, off);
        }
        // live: blocking incidents and possessions on a stretch's straight line
        Set<String> cutNow = new HashSet<>();
        int dim = w.provider.getDimension();
        for (Map.Entry<String, long[]> e : segs.entrySet()) {
            BlockPos a = BlockPos.fromLong(e.getValue()[0]), b = BlockPos.fromLong(e.getValue()[1]);
            for (Disruptions.Incident in : Disruptions.incidents(dim))
                if (in.blocking() && TempWorks.distToSegment(in.x, in.z, a.getX(), a.getZ(), b.getX(), b.getZ()) <= in.radius + 8) cutNow.add(e.getKey());
        }
        for (Edge e : r.edges) e.cutNow = cutNow.contains(e.key);
        if (!cutNow.isEmpty()) {
            List<Set<String>> comps = components(adj, cutNow, null);
            comps.sort((x, y) -> y.size() - x.size());
            for (int i = 1; i < comps.size(); i++) r.isolatedNow.addAll(comps.get(i));
        }
        // score
        double ne = Math.max(1, r.edges.size());
        int worstCut = 0;
        for (Set<String> s : r.cutsOff.values()) worstCut = Math.max(worstCut, s.size());
        int tight = 0, busySingle = 0;
        for (Edge e : r.edges) { if (e.spare() < 0.2) tight++; if (e.tracks <= 1 && e.demand > e.capacity * 0.5) busySingle++; }
        double s = 100 - 35 * bridges.size() / ne - 25.0 * worstCut / Math.max(1, r.towns) - 25 * tight / ne - 15 * busySingle / ne - (r.components > 1 ? 10 : 0);
        r.score = (int) Math.round(Math.max(0, Math.min(100, s)));
        // upgrades
        for (Edge e : r.edges) {
            if (e.bridge) {
                Set<String> off = r.cutsOff.get(e.a + " - " + e.b);
                int n = off == null ? 0 : off.size();
                r.upgrades.add(new String[]{String.valueOf(1000 + n * 10 + Math.round(e.demand)), "Second route to " + join(off, 3), "only link is " + e.a + " - " + e.b + ": closing it cuts off " + n + " town" + (n == 1 ? "" : "s"),
                        "$" + Math.round(e.km * 1.4 * PlanConfig.costDoublePerKm)});
            }
            if (e.spare() < 0.2 && e.tracks <= 1)
                r.upgrades.add(new String[]{String.valueOf(800 + Math.round(100 * (1 - e.spare()))), "Double " + e.a + " - " + e.b, String.format(Locale.ROOT, "single track at %.0f %% of capacity", 100 * (1 - e.spare())),
                        "$" + Math.round(e.km * PlanConfig.costDoublePerKm)});
            else if (e.spare() < 0.2)
                r.upgrades.add(new String[]{String.valueOf(600 + Math.round(100 * (1 - e.spare()))), (e.cbtc ? "Third track / passing loops " : "CBTC moving block on ") + e.a + " - " + e.b,
                        String.format(Locale.ROOT, "%.1f trains/h over a capacity of %.1f", e.demand, e.capacity), "$" + (e.cbtc ? Math.round(e.km * PlanConfig.costDoublePerKm) : PlanConfig.costCbtcPerLine)});
            else if (e.tracks <= 1 && e.demand > e.capacity * 0.5)
                r.upgrades.add(new String[]{String.valueOf(400 + Math.round(e.demand)), "Passing loop on " + e.a + " - " + e.b, "single track over half used: one late train delays the other way",
                        "$" + Math.round(Math.min(e.km, 0.6) * PlanConfig.costDoublePerKm)});
        }
        for (String st : r.articulation) {
            Set<String> off = r.cutsOff.get("station " + st);
            r.upgrades.add(new String[]{String.valueOf(500 + (off == null ? 0 : off.size()) * 10), "Avoiding line round " + st, "everything to " + join(off, 3) + " runs through it", "$" + Math.round(1.5 * PlanConfig.costDoublePerKm)});
        }
        r.upgrades.sort((x, y) -> Long.compare(Long.parseLong(y[0]), Long.parseLong(x[0])));
        return r;
    }

    /** rows for the console / desk, also kept in PlanData */
    public static void store(World w, Report r) {
        PlanData d = PlanData.get(w);
        d.lastResil.clear();
        d.lastResil.add(new String[]{"RS", String.valueOf(r.score), String.valueOf(r.towns), String.valueOf(r.edges.size()), String.valueOf(r.components), String.valueOf(r.cutsOff.size()), String.valueOf(r.isolatedNow.size())});
        for (Edge e : r.edges)
            d.lastResil.add(new String[]{"RE", e.a + " - " + e.b, String.format(Locale.ROOT, "%.2f", e.km), e.tracks <= 1 ? "single" : "double", e.bridge ? "SPOF" : String.valueOf(e.alt),
                    e.detourKm < 0 ? "-" : String.format(Locale.ROOT, "+%.1f", e.detourKm), String.format(Locale.ROOT, "%.1f", e.demand), String.format(Locale.ROOT, "%.1f", e.capacity),
                    String.format(Locale.ROOT, "%.0f", e.spare() * 100), e.cutNow ? "CUT" : ""});
        for (Map.Entry<String, Set<String>> e : r.cutsOff.entrySet()) d.lastResil.add(new String[]{"RC", e.getKey(), join(e.getValue(), 12), String.valueOf(e.getValue().size())});
        for (String s : r.isolatedNow) d.lastResil.add(new String[]{"RI", s});
        for (int i = 0; i < Math.min(12, r.upgrades.size()); i++) { String[] u = r.upgrades.get(i); d.lastResil.add(new String[]{"RU", String.valueOf(i + 1), u[1], u[2], u[3]}); }
        if (d.resilienceScore >= 0 && r.score < d.resilienceScore - 10)
            com.dogpound.railmap.alarm.AlarmManager.get(w).raise(w, "plan:resil", com.dogpound.railmap.alarm.AlarmManager.Cat.CONSOLE, com.dogpound.railmap.alarm.AlarmManager.Sev.MINOR,
                    "RESILIENCE fell to " + r.score + " (was " + d.resilienceScore + ")" + (r.isolatedNow.isEmpty() ? "" : ": " + r.isolatedNow.size() + " town(s) cut off"), null, false);
        d.resilienceScore = r.score;
        d.markDirty();
    }

    // ---- graph helpers -------------------------------------------------------------------------------------------------
    private static String key(String a, String b) { return a.compareTo(b) <= 0 ? a + "~" + b : b + "~" + a; }

    private static List<Set<String>> components(Map<String, Set<String>> adj, Set<String> cutEdges, String cutNode) {
        List<Set<String>> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (cutNode != null) seen.add(cutNode);
        for (String s : adj.keySet()) {
            if (seen.contains(s)) continue;
            Set<String> comp = new TreeSet<>();
            Deque<String> q = new ArrayDeque<>();
            q.add(s); seen.add(s);
            while (!q.isEmpty()) {
                String u = q.poll();
                comp.add(u);
                for (String v : adj.getOrDefault(u, Collections.emptySet())) {
                    if (seen.contains(v) || cutEdges.contains(key(u, v))) continue;
                    seen.add(v); q.add(v);
                }
            }
            out.add(comp);
        }
        return out;
    }

    private static Set<String> smallest(List<Set<String>> comps) {
        if (comps.size() < 2) return Collections.emptySet();
        comps.sort((x, y) -> y.size() - x.size());
        Set<String> o = new TreeSet<>();
        for (int i = 1; i < comps.size(); i++) o.addAll(comps.get(i));
        return o;
    }

    private static void tarjan(Map<String, Set<String>> adj, Set<String> bridges, Set<String> art) {
        Map<String, Integer> disc = new HashMap<>(), low = new HashMap<>();
        int[] time = {0};
        for (String s : adj.keySet()) if (!disc.containsKey(s)) dfs(adj, s, null, disc, low, time, bridges, art);
    }

    private static void dfs(Map<String, Set<String>> adj, String u, String parent, Map<String, Integer> disc, Map<String, Integer> low, int[] time, Set<String> bridges, Set<String> art) {
        disc.put(u, time[0]); low.put(u, time[0]); time[0]++;
        int children = 0;
        for (String v : adj.getOrDefault(u, Collections.emptySet())) {
            if (!disc.containsKey(v)) {
                children++;
                dfs(adj, v, u, disc, low, time, bridges, art);
                low.put(u, Math.min(low.get(u), low.get(v)));
                if (low.get(v) > disc.get(u)) bridges.add(key(u, v));
                if (parent != null && low.get(v) >= disc.get(u)) art.add(u);
            } else if (!v.equals(parent)) low.put(u, Math.min(low.get(u), disc.get(v)));
        }
        if (parent == null && children > 1) art.add(u);
    }

    /** edge-disjoint paths between a and b (unit capacities, up to max) */
    static int disjointPaths(Map<String, Set<String>> adj, String a, String b, int max) {
        Map<String, Integer> flow = new HashMap<>();             // "u>v" -> flow
        int n = 0;
        while (n < max) {
            Map<String, String> prev = new HashMap<>();
            Deque<String> q = new ArrayDeque<>();
            q.add(a); prev.put(a, null);
            while (!q.isEmpty() && !prev.containsKey(b)) {
                String u = q.poll();
                for (String v : adj.getOrDefault(u, Collections.emptySet())) {
                    if (prev.containsKey(v)) continue;
                    int f = flow.getOrDefault(u + ">" + v, 0);
                    if (f >= 1) continue;                                  // residual: capacity 1 each way, minus flow
                    prev.put(v, u); q.add(v);
                }
            }
            if (!prev.containsKey(b)) break;
            for (String v = b; prev.get(v) != null; v = prev.get(v)) {
                String u = prev.get(v);
                flow.merge(u + ">" + v, 1, Integer::sum);
                flow.merge(v + ">" + u, -1, Integer::sum);
            }
            n++;
        }
        return n;
    }

    /** extra km round the shortest other way when this stretch is closed */
    private static double detour(Map<String, Set<String>> adj, Map<String, Edge> byKey, Edge cut) {
        Map<String, Double> dist = new HashMap<>();
        PriorityQueue<Object[]> pq = new PriorityQueue<>(Comparator.comparingDouble(o -> (double) o[1]));
        pq.add(new Object[]{cut.a, 0.0});
        while (!pq.isEmpty()) {
            Object[] o = pq.poll();
            String u = (String) o[0];
            double d = (double) o[1];
            if (dist.containsKey(u)) continue;
            dist.put(u, d);
            if (u.equals(cut.b)) return d - cut.km;
            for (String v : adj.getOrDefault(u, Collections.emptySet())) {
                String k = key(u, v);
                if (k.equals(cut.key) || dist.containsKey(v)) continue;
                Edge e = byKey.get(k);
                pq.add(new Object[]{v, d + (e == null ? 1 : e.km)});
            }
        }
        return -1;
    }

    private static String join(Collection<String> s, int max) {
        if (s == null || s.isEmpty()) return "-";
        StringBuilder b = new StringBuilder();
        int i = 0;
        for (String x : s) { if (i++ >= max) { b.append(" +").append(s.size() - max); break; } b.append(b.length() == 0 ? "" : ", ").append(x); }
        return b.toString();
    }
}
