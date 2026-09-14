package com.dogpound.railmap.scan;

import cam72cam.mod.block.BlockEntity;
import cam72cam.mod.math.Vec3i;
import com.dogpound.railmap.graph.SignalNode;
import com.dogpound.railmap.graph.SignalNode.Aspect;
import net.landofrails.landofsignals.tile.TileComplexSignal;
import net.landofrails.landofsignals.tile.TileSignalPart;
import net.landofrails.landofsignals.tile.TileSignalPartAnimated;
import net.minecraft.util.math.BlockPos;

import java.util.Locale;
import java.util.Map;

/**
 * Reads LandOfSignals signal heads. LOS has no aspect enum: a signal's state is a free-form
 * string chosen by its content pack ("red", "hp0", "ks1", ...). So we classify by common
 * German/English aspect vocabulary and fall back to the head's ordered state list (packs
 * list the most restrictive state first). The raw string is kept for the hover readout.
 * <p>
 * This class is only ever loaded when landofsignals is present (see {@link SignalScanner}).
 */
final class LandOfSignalsAdapter {
    static final String MODID = "landofsignals";

    private LandOfSignalsAdapter() {}

    static SignalNode read(BlockEntity be) {
        if (be instanceof TileSignalPart part) {
            String state = part.getState();
            return node(be, classify(state, part.getOrderedStates()), state);
        }
        if (be instanceof TileComplexSignal complex) {
            Map<String, String> groups = complex.getSignalGroupStates();
            Map<String, String[]> ordered = complex.getOrderedGroupStates();
            if (groups == null || groups.isEmpty()) return node(be, Aspect.UNKNOWN, "");
            Aspect worst = null;
            StringBuilder raw = new StringBuilder();
            for (Map.Entry<String, String> g : groups.entrySet()) {
                Aspect a = classify(g.getValue(), ordered == null ? null : ordered.get(g.getKey()));
                // A complex head shows several lamps; a train obeys the most restrictive one.
                worst = worst == null ? a : (a == Aspect.UNKNOWN ? worst : worst.mostRestrictive(a));
                if (raw.length() > 0) raw.append(' ');
                raw.append(g.getKey()).append('=').append(g.getValue());
            }
            return node(be, worst == null ? Aspect.UNKNOWN : worst, raw.toString());
        }
        if (be instanceof TileSignalPartAnimated anim) {
            String state = anim.getAnimationOrTextureName();
            return node(be, classify(state, null), state);
        }
        return null;
    }

    private static SignalNode node(BlockEntity be, Aspect a, String raw) {
        Vec3i p = be.getPos();
        return new SignalNode(new BlockPos(p.x, p.y, p.z), a, MODID, raw);
    }

    static Aspect classify(String state, String[] ordered) {
        if (state == null || state.isEmpty()) return Aspect.UNKNOWN;
        String s = state.toLowerCase(Locale.ROOT);
        // Order matters: "hp00"/"hp0" before "hp1"-style checks, and whole tokens before substrings.
        for (String tok : s.split("[^a-z0-9]+")) {
            switch (tok) {
                case "red": case "stop": case "halt": case "danger": case "hp0": case "hp00":
                case "sh0": case "vr0": case "ks0": case "hl0": case "off":
                    return Aspect.RED;
                case "yellow": case "orange": case "amber": case "caution": case "slow": case "warn":
                case "approach": case "hp2": case "vr2": case "ks2": case "hl2": case "hl3":
                    return Aspect.YELLOW;
                case "green": case "go": case "clear": case "proceed": case "free": case "fahrt":
                case "hp1": case "vr1": case "ks1": case "hl1": case "sh1":
                    return Aspect.GREEN;
                default:
                    break;
            }
        }
        if (s.contains("red") || s.contains("stop")) return Aspect.RED;
        if (s.contains("yellow") || s.contains("orange")) return Aspect.YELLOW;
        if (s.contains("green")) return Aspect.GREEN;
        // Unknown vocabulary: content packs list stop first and clear last.
        if (ordered != null && ordered.length >= 2) {
            for (int i = 0; i < ordered.length; i++) {
                if (state.equals(ordered[i])) {
                    return i == 0 ? Aspect.RED : i == ordered.length - 1 ? Aspect.GREEN : Aspect.YELLOW;
                }
            }
        }
        return Aspect.UNKNOWN;
    }
}
