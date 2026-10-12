package com.dogpound.railmap.safety;

import com.dogpound.railmap.auto.TrackPath;
import net.minecraft.block.Block;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Signalling batch 3, Part M: #15 route conflict visualizer, #19 route-setting macros.
 * The entry points SigBlocks / SigTicker / SigCommand / SigConsole / SigHooks call.
 */
final class SigM {
    private SigM() {}

    /** the blocks (SafetyBlock) this part adds */
    static List<Block> blocks() {
        return Collections.singletonList(
                new SafetyBlock("route_macro_panel", TileRouteMacros::new, new double[]{0, 0, 9, 16, 16, 16},
                        "Route macro panel: 16 stored route sequences, one button sets them all (SEQ retried / ALL or nothing)",
                        "Key to REC + press a macro, then set routes. Wrench steps: ENTRANCE / EXIT. 12 V"));
    }

    static void registerTiles() {
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileRouteMacros.class, new net.minecraft.util.ResourceLocation(com.dogpound.railmap.RailMap.MODID, "route_macro_panel"));
    }

    /** once a second / every 5 ticks (server) */
    static void tick(World w) { Macros.get(w).tick(w); }
    static void fast(World w) { }

    /** /irextras sig <sub> ...: true when the sub-command (a[1]) is this part's */
    static boolean command(ICommandSender s, World w, String[] a) {
        switch (a[1].toLowerCase(Locale.ROOT)) {
            case "macro": Macros.command(s, w, a); return true;
            case "conflict": ConflictView.command(s, w, a); return true;
            case "selftest":
                if (a.length > 2 && !a[2].equalsIgnoreCase("m")) return false;   // "selftest a/b/c" belong to the other parts
                for (String l : Macros.selfTest()) s.sendMessage(new net.minecraft.util.text.TextComponentString((l.startsWith("PASS") ? "§a" : "§c") + l));
                return a.length > 2;
            default: return false;
        }
    }

    static String usage() { return Macros.USAGE + "|conflict [n]|selftest m"; }

    /** console rows (tags start with "m.") and console ops (see SigConsole for this part's op range) */
    static void fill(World w, List<String[]> out) {
        ConflictView.fill(w, out);
        Macros d = Macros.get(w);
        for (int n = 1; n <= Macros.N; n++) {
            Macros.Macro m = d.macro(n);
            StringBuilder st = new StringBuilder();
            for (long[] s : m.steps) st.append(st.length() > 0 ? "\n" : "").append(Macros.sig(w, BlockPos.fromLong(s[0]))).append(" > ").append(Macros.sig(w, BlockPos.fromLong(s[1])));
            out.add(new String[]{"m.mac", String.valueOf(n), d.label(n), String.valueOf(m.steps.size()), m.mode == Macros.ALL ? "ALL" : "SEQ", String.valueOf(m.channel),
                    String.valueOf(d.state(w, n)), d.status(n), String.valueOf(m.runs), String.valueOf(m.fails), st.length() > 1000 ? st.substring(0, 1000) : st.toString(),
                    d.recording == n ? "1" : "0"});
        }
        for (String l : d.log.subList(0, Math.min(20, d.log.size()))) out.add(new String[]{"m.mlog", l});
    }

    /** 80 play, 81 cancel, 82 record / stop recording, 83 mode toggle (arg = macro number) */
    static String op(World w, int op, String arg, String who) {
        Macros d = Macros.get(w);
        int n;
        try { n = Integer.parseInt(arg.trim()); } catch (NumberFormatException e) { return null; }
        switch (op) {
            case 80: return d.play(w, n, "console, " + who);
            case 81: return d.cancel(w, n, "console, " + who);
            case 82: return d.record(w, n, "console, " + who);
            case 83: { Macros.Macro m = d.macro(n); if (m == null) return null; m.mode = 1 - m.mode; d.markDirty(); return d.label(n) + ": " + (m.mode == Macros.ALL ? "ALL" : "SEQ"); }
            default: return null;
        }
    }
    /** hook (SigHooks): a route was set (macro recording) */
    static void routeSet(World w, BlockPos start, BlockPos end) { Macros.get(w).heard(w, start, end); }
}
