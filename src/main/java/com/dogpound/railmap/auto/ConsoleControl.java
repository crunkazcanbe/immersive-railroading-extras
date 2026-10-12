package com.dogpound.railmap.auto;

import java.util.*;

/**
 * What the Control Center console has told the trains (server, per dimension): trains it holds, and the latched ALL
 * STOP. Driverless trains obey it in Autopilot; trains a player drives get their brakes put on by the console.
 * Not saved: a restart lifts every hold. (ponytail: in-memory; save it in RailwayData if holds need to survive restarts)
 */
public final class ConsoleControl {
    private ConsoleControl() {}

    private static final Map<Integer, Set<UUID>> HELD = new HashMap<>();
    private static final Set<Integer> ALL_STOP = new HashSet<>();

    public static boolean held(int dim, UUID loco) {
        return ALL_STOP.contains(dim) || HELD.getOrDefault(dim, Collections.emptySet()).contains(loco);
    }

    public static boolean heldByName(int dim, UUID loco) { return HELD.getOrDefault(dim, Collections.emptySet()).contains(loco); }

    public static void hold(int dim, UUID loco, boolean on) {
        Set<UUID> s = HELD.computeIfAbsent(dim, k -> new HashSet<>());
        if (on) s.add(loco); else s.remove(loco);
    }

    public static boolean allStop(int dim) { return ALL_STOP.contains(dim); }

    public static void allStop(int dim, boolean on) {
        if (on) ALL_STOP.add(dim); else ALL_STOP.remove(dim);
    }

    public static Set<UUID> heldSet(int dim) { return HELD.getOrDefault(dim, Collections.emptySet()); }
}
