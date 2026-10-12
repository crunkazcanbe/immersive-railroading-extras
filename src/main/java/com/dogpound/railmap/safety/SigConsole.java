package com.dogpound.railmap.safety;

import net.minecraft.world.World;

import java.util.List;

/**
 * Signalling batch 3 on the Control Center's SAFETY tab: rows go out with the safety rows (tags "a." "b." "c." "m."),
 * buttons come back as PacketSafetyOp ops 20-39 part A, 40-59 part B, 60-79 part C, 80-99 part M.
 */
public final class SigConsole {
    private SigConsole() {}

    public static void fill(World w, List<String[]> out) {
        for (Runnable r : new Runnable[]{() -> SigA.fill(w, out), () -> SigB.fill(w, out), () -> SigC.fill(w, out), () -> SigM.fill(w, out)}) {
            try { r.run(); } catch (LinkageError | RuntimeException e) { com.dogpound.railmap.RailMap.LOG.debug("[irextras] signalling console rows failed: {}", e.toString()); }
        }
    }

    public static String op(World w, int op, String arg, String who) {
        if (op >= 20 && op < 40) return SigA.op(w, op, arg, who);
        if (op >= 40 && op < 60) return SigB.op(w, op, arg, who);
        if (op >= 60 && op < 80) return SigC.op(w, op, arg, who);
        if (op >= 80 && op < 100) return SigM.op(w, op, arg, who);
        return null;
    }
}
