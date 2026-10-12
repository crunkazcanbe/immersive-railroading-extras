package com.dogpound.railmap.alarm;

import com.dogpound.railmap.incident.IncidentConfig;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Alarm correlation and root-cause analysis (her ChatGPT list #72): "if a substation breaker trips and ten trains then
 * report traction failures, show that they share one underlying event". When an alarm comes in, the correlator looks
 * back over the alarms raised in the last correlationSeconds within correlationRadius for one that can CAUSE it
 * (a fire takes out power, a power trip stops machines, kills 12 V gear, darkens signals and strands trains...). If it
 * finds one, the new alarm becomes that root's consequence: it still lists, flashes and counts, but it doesn't sound
 * the horn (quietConsequences) and the console shows it folded under its probable root cause. The most specific cause
 * wins (fire, then short / earth fault, then power trip, then all-stop, then 12 V loss, then a train incident).
 */
public final class Correlator {
    private Correlator() {}

    /** what each kind of root alarm can cause, strongest root first */
    private static final AlarmManager.Cat[] ROOTS = {AlarmManager.Cat.FIRE, AlarmManager.Cat.FAULT, AlarmManager.Cat.POWER, AlarmManager.Cat.ALLSTOP,
            AlarmManager.Cat.WIRED, AlarmManager.Cat.INCIDENT, AlarmManager.Cat.CONSOLE};
    private static final Map<AlarmManager.Cat, EnumSet<AlarmManager.Cat>> CAUSES = new EnumMap<>(AlarmManager.Cat.class);
    static {
        CAUSES.put(AlarmManager.Cat.FIRE, EnumSet.of(AlarmManager.Cat.POWER, AlarmManager.Cat.FAULT, AlarmManager.Cat.MACHINE, AlarmManager.Cat.WIRED,
                AlarmManager.Cat.AUTO, AlarmManager.Cat.SIGNAL, AlarmManager.Cat.INCIDENT));
        CAUSES.put(AlarmManager.Cat.FAULT, EnumSet.of(AlarmManager.Cat.POWER, AlarmManager.Cat.MACHINE, AlarmManager.Cat.WIRED, AlarmManager.Cat.AUTO,
                AlarmManager.Cat.SIGNAL, AlarmManager.Cat.INCIDENT));
        CAUSES.put(AlarmManager.Cat.POWER, EnumSet.of(AlarmManager.Cat.MACHINE, AlarmManager.Cat.WIRED, AlarmManager.Cat.AUTO, AlarmManager.Cat.SIGNAL,
                AlarmManager.Cat.INCIDENT));
        CAUSES.put(AlarmManager.Cat.ALLSTOP, EnumSet.of(AlarmManager.Cat.AUTO, AlarmManager.Cat.INCIDENT, AlarmManager.Cat.SIGNAL));
        CAUSES.put(AlarmManager.Cat.WIRED, EnumSet.of(AlarmManager.Cat.SIGNAL, AlarmManager.Cat.AUTO));
        CAUSES.put(AlarmManager.Cat.INCIDENT, EnumSet.of(AlarmManager.Cat.AUTO, AlarmManager.Cat.SIGNAL));
        CAUSES.put(AlarmManager.Cat.CONSOLE, EnumSet.of(AlarmManager.Cat.AUTO));
    }

    public static boolean canCause(AlarmManager.Cat root, AlarmManager.Cat effect) {
        EnumSet<AlarmManager.Cat> s = CAUSES.get(root);
        return s != null && s.contains(effect);
    }

    /** a new / re-raised alarm: tie it to its probable root (called from AlarmManager.raise) */
    static void link(AlarmManager m, AlarmManager.Alarm a, long now) {
        if (a.parent != 0) return;                                              // a repeat keeps its root
        long window = IncidentConfig.correlationSeconds * 20L;
        double r2 = (double) IncidentConfig.correlationRadius * IncidentConfig.correlationRadius;
        AlarmManager.Alarm best = null;
        int bestRank = Integer.MAX_VALUE;
        for (AlarmManager.Alarm x : m.all()) {
            if (x == a || x.parent != 0 || now - x.first > window || x.first > now) continue;
            if (!canCause(x.cat, a.cat)) continue;
            if (x.cat == a.cat) continue;
            if (a.pos != 0 && x.pos != 0) {
                if (BlockPos.fromLong(a.pos).distanceSq(BlockPos.fromLong(x.pos)) > r2) continue;
            } else if (now - x.first > window / 3) continue;                    // no location: only close in time
            int rank = rank(x.cat);
            if (rank < bestRank || rank == bestRank && best != null && x.first > best.first) { best = x; bestRank = rank; }
        }
        if (best == null) return;
        a.parent = best.id;
        best.children++;
    }

    private static int rank(AlarmManager.Cat c) {
        for (int i = 0; i < ROOTS.length; i++) if (ROOTS[i] == c) return i;
        return ROOTS.length;
    }

    /** the alarm that started this one's chain (itself if it is a root) */
    public static AlarmManager.Alarm root(AlarmManager m, AlarmManager.Alarm a) {
        Set<Integer> seen = new HashSet<>();
        AlarmManager.Alarm cur = a;
        while (cur.parent != 0 && seen.add(cur.id)) {
            AlarmManager.Alarm p = m.byId(cur.parent);
            if (p == null) break;
            cur = p;
        }
        return cur;
    }

    /** consequences of a root (live ones) */
    public static List<AlarmManager.Alarm> children(AlarmManager m, AlarmManager.Alarm root) {
        List<AlarmManager.Alarm> l = new ArrayList<>();
        for (AlarmManager.Alarm x : m.all()) if (x.parent == root.id) l.add(x);
        return l;
    }

    /** ACK a whole group: the root and everything it caused */
    public static int ackGroup(AlarmManager m, int rootId, String who) {
        int n = 0;
        for (AlarmManager.Alarm x : m.all())
            if ((x.id == rootId || x.parent == rootId) && !x.acked) { x.acked = true; x.ackBy = who; n++; }
        m.touch();
        return n;
    }

    /**
     * Root-cause groups for the console: {root, consequences...} for every root alarm that is still showing (or has
     * showing consequences), newest first. A one-line verdict: "PROBABLE ROOT CAUSE: ... (n consequential alarms)".
     */
    public static List<List<AlarmManager.Alarm>> groups(AlarmManager m, long now) {
        List<List<AlarmManager.Alarm>> out = new ArrayList<>();
        for (AlarmManager.Alarm x : m.all()) {
            if (x.parent != 0) continue;
            List<AlarmManager.Alarm> kids = children(m, x);
            boolean live = x.seq(now) != 0;
            for (AlarmManager.Alarm k : kids) live |= k.seq(now) != 0;
            if (!live || kids.isEmpty() && x.children == 0) continue;
            List<AlarmManager.Alarm> g = new ArrayList<>();
            g.add(x);
            g.addAll(kids);
            out.add(g);
        }
        return out;
    }

    /** how many showing alarms are consequences (the flood the correlator spared the operator) */
    public static int consequential(AlarmManager m, long now) {
        int n = 0;
        for (AlarmManager.Alarm x : m.all()) if (x.parent != 0 && x.seq(now) != 0) n++;
        return n;
    }
}
