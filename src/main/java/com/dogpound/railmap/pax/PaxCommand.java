package com.dogpound.railmap.pax;

import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.economy.PassengerData;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.*;

/** /irextras pax selftest | people | graph [show] | clear | add <n>  (plain "/irextras pax [name]" stays the economy's station list) */
public final class PaxCommand {
    private PaxCommand() {}
    public static final String USAGE = "pax selftest | pax people | pax graph [show] | pax clear | pax add <n>";
    private static final Set<String> SUBS = new HashSet<>(Arrays.asList("selftest", "people", "graph", "clear", "add"));

    public static boolean command(World w, ICommandSender s, String[] a) {
        if (a.length < 2 || !a[0].equalsIgnoreCase("pax") || !SUBS.contains(a[1].toLowerCase(Locale.ROOT))) return false;
        switch (a[1].toLowerCase(Locale.ROOT)) {
            case "selftest" -> selftest(w, s);
            case "people" -> people(w, s);
            case "graph" -> graph(w, s, a.length > 2 && a[2].equalsIgnoreCase("show"));
            case "clear" -> { PaxWorld.clear(w); say(s, "Visible passengers cleared - the stations draw them again from the numbers."); }
            case "add" -> add(w, s, a);
            default -> { return false; }
        }
        return true;
    }

    private static void say(ICommandSender s, String m) { s.sendMessage(new TextComponentString(m)); }

    private static PassengerData.Stn nearest(World w, ICommandSender s) {
        Vec3d p = s.getPositionVector();
        PassengerData.Stn best = null; double bd = 96 * 96;
        for (PassengerData.Stn x : PassengerData.get(w).stations()) {
            double d = BlockPos.fromLong(x.key).distanceSqToCenter(p.x, p.y, p.z);
            if (d < bd) { bd = d; best = x; }
        }
        return best;
    }

    private static void people(World w, ICommandSender s) {
        Map<Long, PaxWorld.Site> m = PaxWorld.sites(w);
        if (m.isEmpty()) { say(s, "§7No station near a player is showing people" + (PaxConfig.enabled ? "" : " (visible passengers are OFF in irextras_pax.cfg)")); return; }
        PassengerData pd = PassengerData.get(w);
        for (PaxWorld.Site x : m.values()) {
            int[] st = new int[EntityPax.STATES.length];
            int waitingType = 0;
            for (EntityPax e : x.people) if (!e.isDead) { st[Math.min(st.length - 1, e.state())]++; if (e.waitingType()) waitingType++; }
            PassengerData.Stn stn = pd.stn(x.key);
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < st.length; i++) if (st[i] > 0) b.append(' ').append(EntityPax.STATES[i].toLowerCase(Locale.ROOT)).append(' ').append(st[i]).append(',');
            say(s, String.format(Locale.ROOT, "§b%s§7: %d shown (%d waiting-type) for %.1f simulated waiting · budget %d · graph %s",
                    x.name, x.people.size(), waitingType, stn == null ? 0 : stn.waitingTotal(), x.budget, x.g == null ? "none" : x.g.n + " cells"));
            if (b.length() > 0) say(s, "§7  " + b.substring(0, b.length() - 1));
        }
    }

    private static void graph(World w, ICommandSender s, boolean show) {
        PassengerData.Stn stn = nearest(w, s);
        if (stn == null) { say(s, "No station within 96 blocks."); return; }
        long t0 = System.nanoTime();
        PaxGraph g = PaxGraph.scan(w, stn.key, PaxConfig.graphRadius, PaxConfig.graphHeight);
        long us = (System.nanoTime() - t0) / 1000;
        if (g == null) { say(s, "§c" + stn.name + ": no walk graph - no standable platform beside its track within " + PaxConfig.graphRadius + " blocks"); return; }
        int plat = 0;
        for (boolean b : g.platform) if (b) plat++;
        say(s, String.format(Locale.ROOT, "§b%s§7 walk graph: %d cells (%d platform, %d at the edge, %d waiting spots), %d entrances, %d concourse, %d bench seats, %d ticket machines, %d gates, %d boards, %d stop-track rail blocks · built in %d µs",
                stn.name, g.n, plat, g.edges.length, g.spots.length, g.entrances.length, g.concourse.length, g.seats.size(), g.machines.size(), g.gates.size(), g.boards.size(), g.railCount, us));
        if (show && w instanceof WorldServer ws) {
            for (int i = 0; i < g.n; i++) if (g.edge[i]) ws.spawnParticle(EnumParticleTypes.VILLAGER_HAPPY, g.cx(i), g.cy(i) + 0.2, g.cz(i), 1, 0.0, 0.0, 0.0, 0.0);
            for (int i : g.spots) if (i % 3 == 0) ws.spawnParticle(EnumParticleTypes.END_ROD, g.cx(i), g.cy(i) + 0.2, g.cz(i), 1, 0.0, 0.0, 0.0, 0.0);
            for (int i : g.entrances) ws.spawnParticle(EnumParticleTypes.REDSTONE, g.cx(i), g.cy(i) + 1, g.cz(i), 12, 0.2, 0.5, 0.2, 0.0);
            for (int i : g.concourse) if (i % 4 == 0) ws.spawnParticle(EnumParticleTypes.WATER_SPLASH, g.cx(i), g.cy(i) + 0.3, g.cz(i), 2, 0.0, 0.0, 0.0, 0.0);
            for (PaxGraph.Seat x : g.seats) ws.spawnParticle(EnumParticleTypes.HEART, x.x, x.y + 1, x.z, 1, 0.0, 0.0, 0.0, 0.0);
            say(s, "§7 green = platform edge, white = waiting spots, red = entrances, blue = concourse, hearts = seats");
        }
    }

    private static void add(World w, ICommandSender s, String[] a) {
        if (!s.canUseCommand(2, "irextras")) { say(s, "Operators only (it adds simulated passengers)."); return; }
        PassengerData.Stn stn = nearest(w, s);
        if (stn == null) { say(s, "No station within 96 blocks."); return; }
        int n;
        try { n = Math.max(1, Math.min(500, Integer.parseInt(a.length > 2 ? a[2] : "20"))); } catch (NumberFormatException e) { say(s, "pax add <n>"); return; }
        List<Long> dests = new ArrayList<>();
        PassengerData pd = PassengerData.get(w);
        for (RailwayData.Line l : RailwayData.get(w).lines().values())
            if (l.stations.contains(stn.key)) for (long d : l.stations) if (d != stn.key && pd.stn(d) != null && !dests.contains(d)) dests.add(d);
        if (dests.isEmpty()) { say(s, stn.name + " is on no line with another station."); return; }
        for (int i = 0; i < n; i++) stn.waiting.merge(dests.get(i % dests.size()), 1.0, Double::sum);
        pd.markDirty();
        say(s, "Added " + n + " waiting passengers at " + stn.name + " for " + dests.size() + " destination(s).");
    }

    // ---- self-test ----------------------------------------------------------------------------------------------------
    private static int pass, fail;

    private static void check(ICommandSender s, boolean ok, String what) {
        if (ok) pass++; else fail++;
        say(s, (ok ? "§a PASS " : "§c FAIL ") + "§7" + what);
    }

    private static void selftest(World w, ICommandSender s) {
        pass = fail = 0;
        say(s, "§bVisible passengers self-test");
        // a 24 x 12 station: track along z = 0, platform z 2..4, a wall at z = 7 with one gap at x = 20
        List<int[]> cells = new ArrayList<>();
        for (int x = 0; x < 24; x++) for (int z = 2; z < 14; z++) if (z != 7 || x == 20) cells.add(new int[]{x, 64, z, 1000});
        Set<Long> rails = new HashSet<>();
        for (int x = 0; x < 24; x++) rails.add(PaxGraph.pk(x, 64, 0));
        PaxGraph g = PaxGraph.assemble(PaxGraph.pk(12, 64, 0), cells, rails, 0, 23, 0, 13);
        g.finishConcourse(Collections.emptyList());
        check(s, g.n == 24 * 12 - 23, "graph keeps every standable cell (" + g.n + " / 265)");
        int plat = 0;
        for (boolean b : g.platform) if (b) plat++;
        check(s, plat == 72 && g.edges.length == 24, "platform = 3 rows beside the track (" + plat + "), edge = 1 row (" + g.edges.length + ")");
        int[] f = g.field(PaxGraph.F_PLATFORM);
        int at = g.at(2, 64, 12), steps = 0;
        boolean gap = false;
        while (at >= 0 && f[at] > 0 && steps < 200) { at = g.next(f, at, new Random(steps)); steps++; if (at >= 0 && g.xs[at] == 20 && g.zs[at] == 7) gap = true; }
        check(s, at >= 0 && f[at] == 0 && gap && steps <= 30, "a passenger behind the wall reaches the platform through the gap in " + steps + " steps");
        int a = g.at(19, 64, 8), b2 = g.at(20, 64, 7);
        boolean cut = false;
        for (int j : g.nbr[a]) if (j == b2) cut = true;
        check(s, !cut, "no cutting the corner of a wall diagonally");
        boolean entOk = g.entrances.length > 0;
        for (int e : g.entrances) if (g.platform[e]) entOk = false;
        check(s, entOk, g.entrances.length + " entrance(s), none on the platform");
        int[] fx = g.field(PaxGraph.F_EXIT);
        check(s, fx[g.edges[0]] != PaxGraph.INF, "from the platform edge everyone can walk out (" + fx[g.edges[0]] + " steps)");
        check(s, g.concourse.length > 0 && !g.platform[g.concourse[0]], "concourse found off the platform (" + g.concourse.length + " cells)");
        boolean onRail = false;
        for (int i = 0; i < g.n; i++) if (rails.contains(PaxGraph.pk(g.xs[i], g.ys[i], g.zs[i]))) onRail = true;
        check(s, !onRail, "nobody can stand on the track");
        // the numbers -> people mapping
        Map<Long, Double> n = new HashMap<>();
        n.put(1L, 10.0); n.put(2L, 30.0); n.put(3L, 0.4);
        Map<Long, Integer> t = PaxWorld.targets(n, 20, 1.0);
        int sum = 0;
        for (int v : t.values()) sum += v;
        check(s, sum <= 20 && t.get(1L) == 5 && t.get(2L) == 15 && t.get(3L) == 0, "budget 20 for 40.4 waiting shows 5 + 15 + 0 (" + t + ")");
        t = PaxWorld.targets(n, 100, 1.0);
        check(s, t.get(1L) == 10 && t.get(2L) == 30, "under budget: one person per passenger (" + t + ")");
        t = PaxWorld.targets(n, 100, 0.5);
        check(s, t.get(1L) == 5 && t.get(2L) == 15, "scale 0.5 shows half (" + t + ")");
        // which trains take whom
        List<Long> la = Arrays.asList(1L, 2L, 3L), lb = Arrays.asList(3L, 4L);
        List<List<Long>> all = Arrays.asList(la, lb);
        check(s, PaxWorld.serves(la, all, 1, 2), "a direct train serves its own stops");
        check(s, PaxWorld.serves(la, all, 1, 4), "a train to an interchange serves the far line (change at 3)");
        check(s, !PaxWorld.serves(la, all, 1, 5), "a train never serves a place no line reaches");
        check(s, !PaxWorld.serves(lb, all, 3, 1), "no 'change here' onto the line you're already standing at");
        check(s, PaxGraph.pk(-1234, 70, 98765) == new BlockPos(-1234, 70, 98765).toLong() && PaxGraph.pk(5, 0, -3) == new BlockPos(5, 0, -3).toLong(), "graph cell keys match BlockPos packing");
        // look bits
        int v = EntityPax.makeVariant(37, EntityPax.ACC_SHOULDER, true, false, true, 11);
        check(s, (v & 63) == 37 && (v >> 6 & 7) == EntityPax.ACC_SHOULDER && (v >> 9 & 1) == 1 && (v >> 10 & 1) == 0 && (v >> 11 & 1) == 1 && (v >> 12 & 15) == 11, "look bits round-trip");
        // live: the nearest station
        PassengerData.Stn stn = nearest(w, s);
        if (stn != null) {
            PaxGraph lg = PaxGraph.scan(w, stn.key, PaxConfig.graphRadius, PaxConfig.graphHeight);
            check(s, lg != null, "live: " + stn.name + " has a walk graph" + (lg == null ? " (no standable platform beside its track)" : " (" + lg.n + " cells, " + lg.spots.length + " spots, " + lg.entrances.length + " entrances)"));
            PaxWorld.Site site = PaxWorld.sites(w).get(stn.key);
            if (site != null) {
                int shown = 0;
                for (EntityPax e : site.people) if (!e.isDead && e.waitingType()) shown++;
                double wt = stn.waitingTotal() * PaxConfig.scale;
                check(s, shown <= Math.ceil(wt) + 3 && shown <= site.budget + 3, "live: " + shown + " shown waiting <= " + String.format(Locale.ROOT, "%.1f", wt) + " simulated (budget " + site.budget + ")");
            } else say(s, "§7 (live: no player-near site for " + stn.name + " yet - stand within " + PaxConfig.viewDistance + " blocks)");
        } else say(s, "§7 (live checks skipped: no station within 96 blocks)");
        say(s, (fail == 0 ? "§a" : "§c") + pass + " passed, " + fail + " failed");
    }
}
