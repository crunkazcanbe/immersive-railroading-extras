package com.dogpound.railmap.signal;

import com.dogpound.railmap.RailMapConfig;
import com.dogpound.railmap.graph.SignalNode;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Puts RailMap's own American signals onto the Dispatcher Board, the wall screens, the
 * handheld map and Dynmap — the same list LandOfSignals signals land in, so one map shows
 * every signal on the railroad whoever made it.
 */
public final class RailMapSignalAdapter {
    public static final String SOURCE = "railmap";

    private RailMapSignalAdapter() {}

    public static List<SignalNode> collect(World world) {
        List<SignalNode> out = new ArrayList<>();
        if (!RailMapConfig.showOwnSignals) return out;
        for (TileSignalMast mast : SignalRegistry.masts(world.provider.getDimension())) {
            if (mast.isInvalid()) continue;
            BlockPos p = mast.getPos();
            Aspect a = mast.aspect();
            out.add(new SignalNode(p, map(a), SOURCE, describe(mast, a)));
        }
        return out;
    }

    /** RailMap aspects are richer than the map's three colours; keep the most restrictive read. */
    static SignalNode.Aspect map(Aspect a) {
        switch (a) {
            case CLEAR:
            case MEDIUM_CLEAR:
                return SignalNode.Aspect.GREEN;
            case ADVANCE_APPROACH:
            case APPROACH:
            case APPROACH_MEDIUM:
            case MEDIUM_APPROACH:
            case RESTRICTING:
                return SignalNode.Aspect.YELLOW;
            case STOP:
            case STOP_AND_PROCEED:
                return SignalNode.Aspect.RED;
            default:
                return SignalNode.Aspect.UNKNOWN;
        }
    }

    private static String describe(TileSignalMast mast, Aspect a) {
        StringBuilder sb = new StringBuilder(a.label);
        sb.append(" · ").append(mast.style().label);
        if (mast.permissive()) sb.append(" · permissive");
        if (mast.mode() != TileSignalMast.Mode.AUTO) sb.append(" · ").append(mast.mode().label);
        else sb.append(" · ").append(mast.clearBlocks()).append(" clear");
        return sb.toString();
    }
}
