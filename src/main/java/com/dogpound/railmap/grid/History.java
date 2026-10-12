package com.dogpound.railmap.grid;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Electrical maintenance history (her ChatGPT list #43): every grid node keeps its own log book in the world save -
 * breaker operations, fault currents, trip reasons, temperatures, insulation readings, permits. Read it with the
 * multimeter (sneak + right-click), the Settings Console's History page, the console's PROTECT tab or
 * /irextras grid log x y z.
 */
public final class History {
    private History() {}

    public static final int KEEP = 60;

    /** write one entry (type = OPEN, CLOSE, TRIP, FAULT, TEMP, INSUL, PERMIT, PQ, ATS, RESTORE, MAINT...) */
    public static void log(World w, BlockPos p, String type, String text) {
        if (w == null || w.isRemote) return;
        GridData d = GridData.get(w);
        GridData.Node n = d.node(p);
        if (n == null) return;
        n.log.add(w.getTotalWorldTime() + "|" + type + "|" + text.replaceAll("§.", "").replace('|', '/'));
        while (n.log.size() > KEEP) n.log.remove(0);
        d.markDirty();
    }

    public static void log(World w, long p, String type, String text) { log(w, BlockPos.fromLong(p), type, text); }

    /** "tick|TYPE|text" -> "day 3 14:05  TRIP  51 overcurrent 1.2 kA" (Minecraft clock) */
    public static String pretty(String e) {
        String[] f = e.split("\\|", 3);
        if (f.length < 3) return e;
        long t;
        try { t = Long.parseLong(f[0]); } catch (NumberFormatException x) { return e; }
        return String.format(Locale.ROOT, "%s %-6s %s", clock(t), f[1], f[2]);
    }

    /** world tick -> "D3 14:05" */
    public static String clock(long t) {
        long day = t / 24000, tod = (t + 6000) % 24000;
        return String.format(Locale.ROOT, "D%d %02d:%02d", day, tod / 1000, tod % 1000 * 60 / 1000);
    }

    /** the newest `n` entries, newest first, readable */
    public static List<String> recent(GridData.Node node, int n) {
        List<String> l = new ArrayList<>();
        for (int i = node.log.size() - 1; i >= 0 && l.size() < n; i--) l.add(pretty(node.log.get(i)));
        return l;
    }

    /** counters line: operations, contact wear, insulation, last trip */
    public static String counters(GridData.Node n) {
        StringBuilder b = new StringBuilder();
        if (GridData.opens(n)) b.append(String.format(Locale.ROOT, "%,d operations · contact wear %.0f%%%s · ", n.ops, n.wear * 100, n.wear >= 1 ? " (MAINTENANCE DUE)" : ""));
        b.append(String.format(Locale.ROOT, "insulation %.0f%% (%s)", n.insul * 100, Insulation.megohms(n)));
        if (n.efault != 0) b.append(" · EARTH FAULT");
        if (!n.lastTrip.isEmpty()) b.append(" · last trip: ").append(n.lastTrip);
        return b.toString();
    }
}
