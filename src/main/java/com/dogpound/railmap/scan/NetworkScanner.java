package com.dogpound.railmap.scan;

import cam72cam.mod.block.BlockEntity;
import com.dogpound.railmap.RailMapConfig;
import com.dogpound.railmap.graph.LogEntry;
import com.dogpound.railmap.graph.RailNetwork;
import com.dogpound.railmap.graph.RailNode;
import com.dogpound.railmap.graph.StopNode;
import com.dogpound.railmap.server.StationData;
import com.dogpound.railmap.signal.RailMapSignalAdapter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Server-side entry point: walk the track, one pass over loaded tiles for signals and stops, then merge the player-named stations. */
public final class NetworkScanner {
    private static final boolean IR_LOADED = Loader.isModLoaded("immersiverailroading");

    private NetworkScanner() {}

    public static RailNetwork scan(World world, BlockPos origin) {
        int seedRadius = RailMapConfig.seedRadius;
        StationData data = StationData.get(world);
        List<LogEntry> log = new ArrayList<>(data.log());
        if (!IR_LOADED) {
            return new RailNetwork(origin, new ArrayList<>(), new ArrayList<>(),
                    new ArrayList<>(), new ArrayList<>(), log, world.getTotalWorldTime(), false, seedRadius);
        }
        IRTrackWalker.Result r = IRTrackWalker.walk(world, origin, seedRadius, RailMapConfig.nodeBudget);
        SignalScanner signals = new SignalScanner(r.nodes, RailMapConfig.signalTrackDistance);
        StopScanner stops = new StopScanner(r.idByPos);
        if (!r.nodes.isEmpty()) {
            for (BlockEntity be : UmcTiles.loaded(world)) {
                signals.accept(be);
                stops.accept(be);
            }
        }
        List<StopNode> merged = mergeStations(stops.found, r.nodes, r.idByPos, data.names());
        // RailMap's own American signals are plain Forge tiles, not UMC ones, so they come
        // from the signal registry rather than the loaded-tile sweep above.
        List<com.dogpound.railmap.graph.SignalNode> allSignals = new ArrayList<>(signals.found);
        for (com.dogpound.railmap.graph.SignalNode s : RailMapSignalAdapter.collect(world)) {
            if (nearTrack(r.nodes, s.pos, RailMapConfig.signalTrackDistance)) allSignals.add(s);
        }
        return new RailNetwork(origin, r.nodes, r.segments, allSignals, merged, log,
                world.getTotalWorldTime(), r.truncated, seedRadius);
    }

    /**
     * A named position that is an existing stop renames it; one that sits on a walked piece
     * (its TileRail position, or within 3 blocks of its centre-line) becomes a STATION stop.
     * Names on track that isn't on this map are simply not shown.
     */
    private static List<StopNode> mergeStations(List<StopNode> stops, List<RailNode> nodes,
                                                Map<Long, Integer> idByPos, Map<Long, String> names) {
        if (names.isEmpty()) return stops;
        List<StopNode> out = new ArrayList<>(stops.size() + names.size());
        java.util.Set<Long> used = new java.util.HashSet<>();
        for (StopNode s : stops) {
            String n = names.get(s.pos.toLong());
            if (n != null) {
                used.add(s.pos.toLong());
                out.add(s.withName(n));
            } else {
                out.add(s);
            }
        }
        for (Map.Entry<Long, String> e : names.entrySet()) {
            if (used.contains(e.getKey())) continue;
            BlockPos p = BlockPos.fromLong(e.getKey());
            if (idByPos.containsKey(e.getKey()) || nearTrack(nodes, p)) {
                out.add(new StopNode(p, e.getValue(), StopNode.Kind.STATION, true));
            }
        }
        return out;
    }

    private static boolean nearTrack(List<RailNode> nodes, BlockPos p) {
        return nearTrack(nodes, p, 3);
    }

    /**
     * Within {@code radius} blocks of a piece's centre-line. Measured to the line between its
     * points, not the points themselves: a straight is just its two ends, so a signal half-way
     * along a 30 m straight was "nowhere near the track" and never showed on the map.
     */
    private static boolean nearTrack(List<RailNode> nodes, BlockPos p, double radius) {
        double px = p.getX() + 0.5, py = p.getY(), pz = p.getZ() + 0.5;
        double r2 = radius * radius;
        for (RailNode n : nodes) {
            float[] q = n.points;
            for (int i = 0; i < q.length; i += 3) {
                if (Math.abs(q[i + 1] - py) > 4) continue;
                double ax = q[i], az = q[i + 2];
                double bx = i + 3 < q.length ? q[i + 3] : ax, bz = i + 3 < q.length ? q[i + 5] : az;
                double vx = bx - ax, vz = bz - az, len2 = vx * vx + vz * vz;
                double k = len2 < 1e-6 ? 0 : Math.max(0, Math.min(1, ((px - ax) * vx + (pz - az) * vz) / len2));
                double dx = px - (ax + k * vx), dz = pz - (az + k * vz);
                if (dx * dx + dz * dz <= r2) return true;
            }
        }
        return false;
    }
}
