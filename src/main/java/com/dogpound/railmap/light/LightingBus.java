package com.dogpound.railmap.light;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * What the lighting controllers tell each lamp on their wire run: ON or OFF, re-sent every second while the panel is
 * there. A lamp that has heard nothing for 15 s is on its own again (its built-in photocell) - so a lamp never goes
 * dark just because a panel was broken, and nothing here is saved (the panels re-send after a reload).
 */
public final class LightingBus {
    private LightingBus() {}

    private static final Map<World, Map<Long, long[]>> CMD = new WeakHashMap<>();
    static final int STALE = 300;

    /** a controller / contactor commands the lamp at p (on = lit), and says who it is */
    public static void set(World w, BlockPos p, boolean on, BlockPos from) {
        CMD.computeIfAbsent(w, k -> new HashMap<>()).put(p.toLong(), new long[]{on ? 1 : 0, w.getTotalWorldTime(), from.toLong()});
    }

    /** -1 = no controller (use your own photocell), 0 = OFF, 1 = ON */
    public static int command(World w, BlockPos p) {
        long[] c = find(w, p);
        return c == null ? -1 : (int) c[0];
    }

    /** the controller feeding this lamp, or null */
    public static BlockPos from(World w, BlockPos p) {
        long[] c = find(w, p);
        return c == null ? null : BlockPos.fromLong(c[2]);
    }

    private static long[] find(World w, BlockPos p) {
        Map<Long, long[]> m = CMD.get(w);
        if (m == null) return null;
        long[] c = m.get(p.toLong());
        if (c == null) return null;
        if (w.getTotalWorldTime() - c[1] > STALE) { m.remove(p.toLong()); return null; }
        return c;
    }

    public static int size(World w) { Map<Long, long[]> m = CMD.get(w); return m == null ? 0 : m.size(); }
}
