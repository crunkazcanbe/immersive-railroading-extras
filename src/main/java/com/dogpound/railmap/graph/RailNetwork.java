package com.dogpound.railmap.graph;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Snapshot of everything one Dispatcher Board knows about. Immutable once built; the tile
 * swaps in a fresh instance after each scan and the GUI only ever reads.
 * <p>
 * NBT layout is struct-of-arrays (one int[] per field across all nodes) instead of one
 * compound per node: with ~2000 pieces the per-compound key overhead alone would be ~100KB,
 * and this whole thing rides inside a tile-entity update packet.
 */
public final class RailNetwork {
    /** Fixed-point scale for polyline coordinates: 1/8 block is plenty for a map. */
    private static final float FP = 8f;

    public static final RailNetwork EMPTY = new RailNetwork(BlockPos.ORIGIN, Collections.<RailNode>emptyList(),
            Collections.<RailSegment>emptyList(), Collections.<SignalNode>emptyList(),
            Collections.<StopNode>emptyList(), Collections.<LogEntry>emptyList(), 0L, false, 0);

    /** Board position the scan started from. */
    public final BlockPos origin;
    public final List<RailNode> nodes;
    public final List<RailSegment> segments;
    public final List<SignalNode> signals;
    public final List<StopNode> stops;
    /** Station timetable (newest last), from the world's StationData at scan time. */
    public final List<LogEntry> log;
    /** World time (ticks) of the scan, for the "updated N s ago" readout. */
    public final long timestamp;
    /** True when the walk hit the node budget, i.e. there is more track than shown. */
    public final boolean truncated;
    /** Seed radius that was used; lets the GUI phrase the "nothing found" message. */
    public final int seedRadius;

    // Bounds over every polyline point, world coords.
    public final double minX, minZ, maxX, maxZ;

    public RailNetwork(BlockPos origin, List<RailNode> nodes, List<RailSegment> segments,
                       List<SignalNode> signals, List<StopNode> stops, List<LogEntry> log, long timestamp,
                       boolean truncated, int seedRadius) {
        this.origin = origin;
        this.nodes = Collections.unmodifiableList(nodes);
        this.segments = Collections.unmodifiableList(segments);
        this.signals = Collections.unmodifiableList(signals);
        this.stops = Collections.unmodifiableList(stops);
        this.log = Collections.unmodifiableList(log);
        this.timestamp = timestamp;
        this.truncated = truncated;
        this.seedRadius = seedRadius;

        double nx = Double.MAX_VALUE, nz = Double.MAX_VALUE, xx = -Double.MAX_VALUE, xz = -Double.MAX_VALUE;
        for (RailNode n : nodes) {
            for (int i = 0; i < n.points.length; i += 3) {
                nx = Math.min(nx, n.points[i]);
                xx = Math.max(xx, n.points[i]);
                nz = Math.min(nz, n.points[i + 2]);
                xz = Math.max(xz, n.points[i + 2]);
            }
        }
        if (nodes.isEmpty()) {
            nx = xx = origin.getX();
            nz = xz = origin.getZ();
        }
        minX = nx; maxX = xx; minZ = nz; maxZ = xz;
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    /** Named station (or any stop) nearest to a world point within {@code maxDist}, else null. */
    public StopNode nearestStop(double x, double y, double z, double maxDist, boolean namedOnly) {
        StopNode best = null;
        double bd = maxDist * maxDist;
        for (StopNode s : stops) {
            if (namedOnly && !s.named) continue;
            double dx = s.pos.getX() + 0.5 - x, dy = s.pos.getY() - y, dz = s.pos.getZ() + 0.5 - z;
            if (Math.abs(dy) > 4) continue;
            double d = dx * dx + dz * dz;
            if (d < bd) { bd = d; best = s; }
        }
        return best;
    }

    public NBTTagCompound toNBT() {
        NBTTagCompound t = new NBTTagCompound();
        t.setLong("origin", origin.toLong());
        t.setLong("time", timestamp);
        t.setBoolean("trunc", truncated);
        t.setInteger("seed", seedRadius);

        int n = nodes.size();
        int[] pos = new int[n * 3];
        byte[] kind = new byte[n];
        byte[] sw = new byte[n];
        int[] yaw = new int[n];
        int[] len = new int[n];
        int[] gauge = new int[n];
        int[] link = new int[n];
        int[] ptOff = new int[n + 1];
        int total = 0;
        for (RailNode nd : nodes) total += nd.points.length;
        int[] pts = new int[total];
        int p = 0;
        for (int i = 0; i < n; i++) {
            RailNode nd = nodes.get(i);
            pos[i * 3] = nd.pos.getX();
            pos[i * 3 + 1] = nd.pos.getY();
            pos[i * 3 + 2] = nd.pos.getZ();
            kind[i] = (byte) nd.kind.ordinal();
            sw[i] = nd.switchState;
            yaw[i] = Math.round(nd.yaw * 10f);
            len[i] = nd.length;
            gauge[i] = nd.gaugeMm;
            link[i] = nd.switchId;
            ptOff[i] = p;
            for (float f : nd.points) pts[p++] = Math.round(f * FP);
        }
        ptOff[n] = p;
        t.setIntArray("n_pos", pos);
        t.setByteArray("n_kind", kind);
        t.setByteArray("n_sw", sw);
        t.setIntArray("n_yaw", yaw);
        t.setIntArray("n_len", len);
        t.setIntArray("n_gauge", gauge);
        t.setIntArray("n_link", link);
        t.setIntArray("n_ptoff", ptOff);
        t.setIntArray("n_pts", pts);

        int[] edges = new int[segments.size() * 2];
        for (int i = 0; i < segments.size(); i++) {
            edges[i * 2] = segments.get(i).a();
            edges[i * 2 + 1] = segments.get(i).b();
        }
        t.setIntArray("edges", edges);

        NBTTagList sig = new NBTTagList();
        for (SignalNode s : signals) sig.appendTag(s.toNBT());
        t.setTag("signals", sig);
        NBTTagList st = new NBTTagList();
        for (StopNode s : stops) st.appendTag(s.toNBT());
        t.setTag("stops", st);
        NBTTagList lg = new NBTTagList();
        for (LogEntry e : log) lg.appendTag(e.toNBT());
        t.setTag("log", lg);
        return t;
    }

    public static RailNetwork fromNBT(NBTTagCompound t) {
        if (t == null || !t.hasKey("n_pos")) return EMPTY;
        int[] pos = t.getIntArray("n_pos");
        byte[] kind = t.getByteArray("n_kind");
        byte[] sw = t.getByteArray("n_sw");
        int[] yaw = t.getIntArray("n_yaw");
        int[] len = t.getIntArray("n_len");
        int[] gauge = t.getIntArray("n_gauge");
        int[] link = t.getIntArray("n_link");
        int[] ptOff = t.getIntArray("n_ptoff");
        int[] pts = t.getIntArray("n_pts");
        int n = kind.length;
        // A truncated/corrupt tag must never crash the client; just show nothing.
        if (pos.length != n * 3 || sw.length != n || yaw.length != n || len.length != n
                || gauge.length != n || link.length != n || ptOff.length != n + 1) {
            return EMPTY;
        }
        RailNode.Kind[] kinds = RailNode.Kind.values();
        List<RailNode> nodes = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int from = ptOff[i], to = ptOff[i + 1];
            if (from < 0 || to > pts.length || to < from) return EMPTY;
            float[] f = new float[to - from];
            for (int j = from; j < to; j++) f[j - from] = pts[j] / FP;
            int k = kind[i] & 0xff;
            nodes.add(new RailNode(i, new BlockPos(pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2]),
                    k < kinds.length ? kinds[k] : RailNode.Kind.STRAIGHT,
                    yaw[i] / 10f, len[i], gauge[i], sw[i], link[i], f));
        }
        int[] edges = t.getIntArray("edges");
        List<RailSegment> segs = new ArrayList<>(edges.length / 2);
        for (int i = 0; i + 1 < edges.length; i += 2) segs.add(new RailSegment(edges[i], edges[i + 1]));

        List<SignalNode> signals = new ArrayList<>();
        NBTTagList sig = t.getTagList("signals", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < sig.tagCount(); i++) signals.add(SignalNode.fromNBT(sig.getCompoundTagAt(i)));
        List<StopNode> stops = new ArrayList<>();
        NBTTagList st = t.getTagList("stops", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < st.tagCount(); i++) stops.add(StopNode.fromNBT(st.getCompoundTagAt(i)));

        List<LogEntry> log = new ArrayList<>();
        NBTTagList lg = t.getTagList("log", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < lg.tagCount(); i++) log.add(LogEntry.fromNBT(lg.getCompoundTagAt(i)));

        return new RailNetwork(BlockPos.fromLong(t.getLong("origin")), nodes, segs, signals, stops, log,
                t.getLong("time"), t.getBoolean("trunc"), t.getInteger("seed"));
    }
}
