package com.dogpound.railmap.scan;

import cam72cam.mod.block.BlockEntity;
import com.dogpound.railmap.graph.RailNode;
import com.dogpound.railmap.graph.SignalNode;
import net.minecraftforge.fml.common.Loader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects signals standing next to walked track. Each signal mod is an adapter that is only
 * touched when that mod is loaded; a missing class disables the adapter instead of crashing.
 */
final class SignalScanner {
    private static final Logger LOG = LogManager.getLogger("RailMap");
    private static boolean losAvailable = Loader.isModLoaded(LandOfSignalsAdapter.MODID);

    private final List<RailNode> nodes;
    private final double maxDistSq;
    final List<SignalNode> found = new ArrayList<>();

    SignalScanner(List<RailNode> nodes, int maxDist) {
        this.nodes = nodes;
        this.maxDistSq = (double) maxDist * maxDist;
    }

    void accept(BlockEntity be) {
        SignalNode s = null;
        if (losAvailable) {
            try {
                s = LandOfSignalsAdapter.read(be);
            } catch (LinkageError e) {
                losAvailable = false;
                LOG.warn("[RailMap] LandOfSignals adapter disabled: {}", e.toString());
            }
        }
        if (s != null && nearTrack(s)) found.add(s);
    }

    /** Signals belong to the map only if they stand beside a piece we walked. */
    private boolean nearTrack(SignalNode s) {
        double sx = s.pos.getX() + 0.5, sy = s.pos.getY(), sz = s.pos.getZ() + 0.5;
        for (RailNode n : nodes) {
            float[] p = n.points;
            for (int i = 0; i < p.length; i += 3) {
                double dx = p[i] - sx, dy = p[i + 1] - sy, dz = p[i + 2] - sz;
                if (dx * dx + dy * dy + dz * dz <= maxDistSq) return true;
            }
        }
        return false;
    }
}
