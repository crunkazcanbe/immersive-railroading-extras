package com.dogpound.railmap.infra;

import com.dogpound.railmap.auto.TrackPath;
import com.dogpound.railmap.grid.GridData;
import com.dogpound.railmap.ohle.Ohle;
import com.dogpound.railmap.ohle.OhleData;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Neutral sections (her ChatGPT list batch 3, #36) - built on the OHLE model's NEUTRAL_SECTION block (ohle.Ohle already
 * splits the wire there, opens a loco's main breaker through it with APC, and flashes over if a train draws power across
 * it). This adds what the driver and the controller see:
 * <ul>
 *   <li>every neutral section with the two sections either side, their state and whether it is a real PHASE BREAK (the
 *   sides fed from different grid networks) or a plain section gap; how many trains coasted through it and how many
 *   drew power across it - on the console and on the Rail Map;</li>
 *   <li>on the cab: the neutral section on the DMI planning area ("NS" marker at its distance) and a message line
 *   warning ahead of it (InfraConfig.neutralWarnM): APC will open the VCB, or - pantograph in MANUAL without on-board
 *   APC (InfraConfig.cabAutoVcb off) - SHUT OFF POWER;</li>
 *   <li>on-board APC: with cabAutoVcb the cab desk opens the VCB through the neutral section even when the driver has
 *   the pantograph in MANUAL (Ohle.contact asks {@link #apc}).</li>
 * </ul>
 */
public final class NeutralSections {
    private NeutralSections() {}

    public static final class Ns {
        public BlockPos pos;
        public String a = "-", b = "-", feedA = "", feedB = "";
        public boolean phaseBreak;
    }

    /** per neutral section: {passes coasting, passes drawing power (flashover), last tick} - since the server started */
    private static final Map<Long, long[]> STATS = new HashMap<>();
    /** per loco: the neutral section it is under now (so a pass counts once) */
    private static final Map<UUID, Long> UNDER = new HashMap<>();
    private static final Map<Integer, Object[]> CACHE = new HashMap<>();

    /** every neutral section on a known OHLE section, cached 5 s */
    @SuppressWarnings("unchecked")
    public static List<Ns> all(World w) {
        int dim = w.provider.getDimension();
        Object[] c = CACHE.get(dim);
        long now = w.getTotalWorldTime();
        if (c != null && now - (Long) c[0] < 100 && now >= (Long) c[0]) return (List<Ns>) c[1];
        Set<Long> seen = new LinkedHashSet<>();
        for (Ohle.Section s : Ohle.known(w)) seen.addAll(s.neutrals);
        List<Ns> out = new ArrayList<>();
        GridData g = GridData.get(w);
        for (long l : seen) {
            Ns n = new Ns();
            n.pos = BlockPos.fromLong(l);
            List<Ohle.Section> sides = Ohle.around(w, n.pos);
            if (sides.size() > 0) { n.a = sides.get(0).name() + " " + sides.get(0).state(); n.feedA = nets(g, sides.get(0)); }
            if (sides.size() > 1) { n.b = sides.get(1).name() + " " + sides.get(1).state(); n.feedB = nets(g, sides.get(1)); }
            n.phaseBreak = !n.feedA.isEmpty() && !n.feedB.isEmpty() && !n.feedA.equals(n.feedB);
            out.add(n);
        }
        CACHE.put(dim, new Object[]{now, out});
        return out;
    }

    /** the grid networks feeding a section, e.g. "N3" (phase / supply identity) */
    private static String nets(GridData g, Ohle.Section s) {
        TreeSet<Integer> ids = new TreeSet<>();
        for (long f : s.feeders) { GridData.Net n = g.netOf(BlockPos.fromLong(f)); if (n != null) ids.add(n.id); }
        StringBuilder b = new StringBuilder();
        for (int i : ids) b.append(b.length() == 0 ? "N" : "+N").append(i);
        return b.toString();
    }

    /** Ohle.contact: is APC opening this loco's VCB under a neutral section? (AUTO pantograph, or on-board APC) */
    public static boolean apc(boolean manualPan) { return !manualPan || InfraConfig.cabAutoVcb; }

    /** Ohle.contact: a loco's pantograph is under a neutral section this second */
    public static void passing(World w, BlockPos ns, UUID loco, boolean drawing, long now) {
        Long was = UNDER.get(loco);
        if (was != null && was == ns.toLong()) { if (drawing) STATS.computeIfAbsent(ns.toLong(), k -> new long[3])[1]++; return; }
        UNDER.put(loco, ns.toLong());
        long[] s = STATS.computeIfAbsent(ns.toLong(), k -> new long[3]);
        if (drawing) s[1]++; else s[0]++;
        s[2] = now;
    }

    /** the loco left the wire / the neutral section */
    public static void clear(UUID loco) { UNDER.remove(loco); }

    // ---- the cab ---------------------------------------------------------------------------------------------------------
    /** neutral sections along the route ahead: distances from the train (m), nearest first */
    public static List<Double> ahead(World w, TrackPath path, double cur) {
        List<Double> out = new ArrayList<>();
        if (path == null || path.steps.isEmpty()) return out;
        for (Ns n : all(w)) {
            double d = path.distanceTo(n.pos.getX() + 0.5, n.pos.getY() - 5.5, n.pos.getZ() + 0.5, 2.5);
            if (d >= 0 && d - cur >= -5 && d - cur <= 4000) out.add(Math.max(0, d - cur));
        }
        Collections.sort(out);
        return out;
    }

    /** CabData: add the neutral sections to the planning area ({distance, 0, kind 3}) and the warning text (null = none) */
    public static String plan(World w, UUID loco, TrackPath path, double cur, List<float[]> into) {
        if (path == null) return null;
        List<Double> ahead = ahead(w, path, cur);
        for (double d : ahead) into.add(new float[]{(float) d, 0, 3});
        if (ahead.isEmpty() || InfraConfig.neutralWarnM <= 0 || ahead.get(0) > InfraConfig.neutralWarnM) return null;
        OhleData.Pan p = OhleData.get(w).pans.get(loco);
        if (p == null) return null;                                                   // not an electric loco
        long d = Math.round(ahead.get(0));
        if (apc(p.manual)) return "NEUTRAL SECTION " + d + " m - APC will open the VCB, coast through";
        return "NEUTRAL SECTION " + d + " m - SHUT OFF POWER (pantograph MANUAL, no APC)";
    }

    // ---- console rows ----------------------------------------------------------------------------------------------------
    /** NS x y z · side A · side B · PHASE BREAK / gap · coasted · drew power · last pass */
    public static void rows(World w, List<String[]> out) {
        long now = w.getTotalWorldTime();
        for (Ns n : all(w)) {
            long[] s = STATS.getOrDefault(n.pos.toLong(), new long[3]);
            out.add(new String[]{"NS", String.valueOf(n.pos.getX()), String.valueOf(n.pos.getY()), String.valueOf(n.pos.getZ()), n.a + (n.feedA.isEmpty() ? "" : " (" + n.feedA + ")"),
                    n.b + (n.feedB.isEmpty() ? "" : " (" + n.feedB + ")"), n.phaseBreak ? "PHASE BREAK" : "section gap", String.valueOf(s[0]), String.valueOf(s[1]),
                    s[2] == 0 ? "-" : ((now - s[2]) / 1200) + " min ago"});
        }
    }
}
