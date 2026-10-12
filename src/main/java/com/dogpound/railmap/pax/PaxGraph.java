package com.dogpound.railmap.pax;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.*;

/**
 * A station's walk graph (visible passengers): every standable cell around the stop, scanned once (rebuilt every few
 * minutes while players are about), joined to its 8 neighbours (one step up or down allowed, no corner cutting). People
 * never path-find: they roll down precomputed BFS distance fields ("flow fields") - one per goal (the platform, the
 * exits, the ticket machines, the concourse, or one node: a waiting spot, a bench, a train door), cached LRU - so a
 * step is 8 array reads. Cells on the stop track (and any IR track) are never nodes: nobody ever stands on a rail.
 */
public final class PaxGraph {
    public static final int INF = Integer.MAX_VALUE;
    public static final long F_PLATFORM = -1, F_EXIT = -2, F_MACHINE = -3, F_CONCOURSE = -4;

    public final long key;
    public int n;
    public int[] xs, ys, zs;
    public float[] ground;                  // the height people stand at (slabs: half a block down)
    public int[][] nbr;
    public boolean[] platform, edge;
    public float[] faceYaw;                 // platform nodes: the yaw that faces the track
    private final Map<Long, Integer> index = new HashMap<>();
    public int[] entrances = new int[0], spots = new int[0], concourse = new int[0], edges = new int[0];
    public final List<Seat> seats = new ArrayList<>();
    public final List<int[]> machines = new ArrayList<>();   // {node, machine x, y, z}
    public final Map<Integer, BlockPos> gates = new HashMap<>();
    public final List<BlockPos> boards = new ArrayList<>();
    public int railCount;
    private final LinkedHashMap<Long, int[]> fields = new LinkedHashMap<Long, int[]>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, int[]> e) { return size() > 96; }
    };

    public static final class Seat {
        public final double x, y, z; public final float yaw; public final int node; public final boolean onPlatform;
        public EntityPax taken;
        Seat(double x, double y, double z, float yaw, int node, boolean onPlatform) { this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.node = node; this.onPlatform = onPlatform; }
    }

    PaxGraph(long key) { this.key = key; }

    public double cx(int i) { return xs[i] + 0.5; }
    public double cy(int i) { return ground[i]; }
    public double cz(int i) { return zs[i] + 0.5; }
    public int at(int x, int y, int z) { Integer i = index.get(pk(x, y, z)); return i == null ? -1 : i; }

    // ---- building ---------------------------------------------------------------------------------------------------
    /** raw input: standable cells {x, y, z, ground*1000}, the stop track's rail blocks, the scan box */
    static PaxGraph assemble(long key, List<int[]> cells, Set<Long> rails, int minX, int maxX, int minZ, int maxZ) {
        PaxGraph g = new PaxGraph(key);
        g.railCount = rails.size();
        // rail distance per column (horizontal), and the direction of the nearest rail
        Map<Long, double[]> near = new HashMap<>();             // xz -> {d2, railY, dx, dz}
        for (long r : rails) {
            BlockPos p = BlockPos.fromLong(r);
            for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
                long k = colKey(p.getX() + dx, p.getZ() + dz);
                double d2 = dx * dx + dz * dz;
                double[] o = near.get(k);
                if (o == null || d2 < o[0]) near.put(k, new double[]{d2, p.getY(), -dx, -dz});
            }
        }
        BlockPos kp = BlockPos.fromLong(key);
        List<int[]> keep = new ArrayList<>();
        Map<Long, Integer> idx = new HashMap<>();
        for (int[] c : cells) { idx.put(pk(c[0], c[1], c[2]), keep.size()); keep.add(c); }
        int m = keep.size();
        boolean[] plat = new boolean[m], edg = new boolean[m];
        float[] fy = new float[m];
        for (int i = 0; i < m; i++) {
            int[] c = keep.get(i);
            if (rails.isEmpty()) {
                double d2 = sq(c[0] - kp.getX()) + sq(c[2] - kp.getZ());
                plat[i] = d2 <= 25 && Math.abs(c[1] - kp.getY()) <= 3;
                edg[i] = d2 <= 6;
                fy[i] = yaw(kp.getX() - c[0], kp.getZ() - c[2]);
                continue;
            }
            double[] o = near.get(colKey(c[0], c[2]));
            if (o == null || c[1] - o[1] < -1 || c[1] - o[1] > 3) continue;
            plat[i] = o[0] <= 16;
            edg[i] = o[0] <= 4.5;
            fy[i] = yaw(o[2], o[3]);
        }
        // the connected part that reaches a platform
        int[][] nb = neighbours(keep, idx);
        boolean[] seen = new boolean[m];
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int i = 0; i < m; i++) if (plat[i]) { seen[i] = true; q.add(i); }
        while (!q.isEmpty()) { int i = q.poll(); for (int j : nb[i]) if (!seen[j]) { seen[j] = true; q.add(j); } }
        List<int[]> fin = new ArrayList<>();
        List<Integer> map = new ArrayList<>();
        for (int i = 0; i < m; i++) if (seen[i]) { map.add(i); fin.add(keep.get(i)); }
        g.n = fin.size();
        g.xs = new int[g.n]; g.ys = new int[g.n]; g.zs = new int[g.n]; g.ground = new float[g.n];
        g.platform = new boolean[g.n]; g.edge = new boolean[g.n]; g.faceYaw = new float[g.n];
        for (int i = 0; i < g.n; i++) {
            int[] c = fin.get(i); int o = map.get(i);
            g.xs[i] = c[0]; g.ys[i] = c[1]; g.zs[i] = c[2]; g.ground[i] = c[1] - 1 + c[3] / 1000f;
            g.platform[i] = plat[o]; g.edge[i] = edg[o]; g.faceYaw[i] = fy[o];
            g.index.put(pk(c[0], c[1], c[2]), i);
        }
        g.nbr = neighbours(fin, g.index);
        // spots: on the platform, back from the edge; edges: where doors are met
        List<Integer> sp = new ArrayList<>(), ed = new ArrayList<>();
        for (int i = 0; i < g.n; i++) if (g.platform[i]) { if (g.edge[i]) ed.add(i); else sp.add(i); }
        if (sp.isEmpty()) sp.addAll(ed);
        g.spots = toArr(sp); g.edges = toArr(ed.isEmpty() ? sp : ed);
        // entrances: where the walk graph meets the edge of the scan (the street), farthest from the platform, spread out
        int[] fp = g.field(F_PLATFORM);
        List<Integer> cand = new ArrayList<>();
        for (int i = 0; i < g.n; i++) if (!g.platform[i] && (g.xs[i] <= minX || g.xs[i] >= maxX || g.zs[i] <= minZ || g.zs[i] >= maxZ)) cand.add(i);
        if (cand.isEmpty()) for (int i = 0; i < g.n; i++) if (!g.platform[i] && fp[i] != INF && fp[i] >= 6) cand.add(i);
        if (cand.isEmpty()) for (int i = 0; i < g.n; i++) if (!g.platform[i]) cand.add(i);
        if (cand.isEmpty()) cand.addAll(sp);
        cand.sort((a, b) -> Integer.compare(fp[b], fp[a]));
        List<Integer> ent = new ArrayList<>();
        for (int c : cand) {
            boolean far = true;
            for (int e : ent) if (sq(g.xs[c] - g.xs[e]) + sq(g.zs[c] - g.zs[e]) < 36) { far = false; break; }
            if (far) ent.add(c);
            if (ent.size() >= 4) break;
        }
        g.entrances = toArr(ent);
        return g;
    }

    /** concourse = off the platform, near ticket machines / boards / gates / kiosks (else just off the platform) */
    void finishConcourse(List<BlockPos> attractors) {
        int[] fp = field(F_PLATFORM);
        List<Integer> c = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (platform[i] || fp[i] == INF || fp[i] < 3) continue;
            for (BlockPos a : attractors) if (sq(xs[i] - a.getX()) + sq(zs[i] - a.getZ()) <= 36 && Math.abs(ys[i] - a.getY()) <= 3) { c.add(i); break; }
        }
        if (c.isEmpty()) for (int i = 0; i < n; i++) if (!platform[i] && fp[i] != INF && fp[i] >= 3) c.add(i);
        if (c.isEmpty()) for (int e : entrances) c.add(e);
        concourse = toArr(c);
        fields.remove(F_CONCOURSE);
    }

    private static int[][] neighbours(List<int[]> cells, Map<Long, Integer> idx) {
        int[][] nb = new int[cells.size()][];
        int[] tmp = new int[24];
        for (int i = 0; i < cells.size(); i++) {
            int[] c = cells.get(i);
            int k = 0;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                for (int dy = -1; dy <= 1; dy++) {
                    Integer j = idx.get(pk(c[0] + dx, c[1] + dy, c[2] + dz));
                    if (j == null) continue;
                    // diagonals only where both sides are open (no cutting a wall corner)
                    if (dx != 0 && dz != 0 && (!open(idx, c[0] + dx, c[1], c[2]) || !open(idx, c[0], c[1], c[2] + dz))) continue;
                    tmp[k++] = j;
                    break;
                }
            }
            nb[i] = Arrays.copyOf(tmp, k);
        }
        return nb;
    }

    private static boolean open(Map<Long, Integer> idx, int x, int y, int z) {
        return idx.containsKey(pk(x, y, z)) || idx.containsKey(pk(x, y + 1, z)) || idx.containsKey(pk(x, y - 1, z));
    }

    // ---- fields -----------------------------------------------------------------------------------------------------
    /** BFS steps from every node to the goal (node index >= 0, or one of the F_ sets); INF = can't get there */
    public int[] field(long goal) {
        int[] f = fields.get(goal);
        if (f != null) return f;
        f = new int[n];
        Arrays.fill(f, INF);
        int[] q = new int[Math.max(1, n)];
        int h = 0, t = 0;
        if (goal >= 0) { if (goal < n) { f[(int) goal] = 0; q[t++] = (int) goal; } }
        else {
            int[] src = goal == F_PLATFORM ? null : goal == F_EXIT ? entrances : goal == F_CONCOURSE ? concourse : machineNodes();
            if (src == null) { for (int i = 0; i < n; i++) if (platform[i]) { f[i] = 0; q[t++] = i; } }
            else for (int i : src) if (f[i] != 0) { f[i] = 0; q[t++] = i; }
        }
        while (h < t) {
            int i = q[h++];
            for (int j : nbr[i]) if (f[j] == INF) { f[j] = f[i] + 1; q[t++] = j; }
        }
        fields.put(goal, f);
        return f;
    }

    private int[] machineNodes() { int[] o = new int[machines.size()]; for (int i = 0; i < o.length; i++) o[i] = machines.get(i)[0]; return o; }

    /** the next node downhill on a field (ties broken by r), or -1 if already there / stuck */
    public int next(int[] f, int at, Random r) {
        if (at < 0 || f[at] == 0 || f[at] == INF) return -1;
        int best = -1, bd = f[at], ties = 0;
        for (int j : nbr[at]) {
            if (f[j] < bd) { bd = f[j]; best = j; ties = 1; }
            else if (f[j] == bd && best >= 0 && r.nextInt(++ties) == 0) best = j;
        }
        return best;
    }

    public int nearest(double x, double y, double z, double maxDist, boolean edgesOnly) {
        int best = -1; double bd = maxDist * maxDist;
        int[] set = edgesOnly ? edges : null;
        int cnt = set == null ? n : set.length;
        for (int k = 0; k < cnt; k++) {
            int i = set == null ? k : set[k];
            double d = sq(cx(i) - x) + sq(cz(i) - z) + sq(cy(i) - y) * 0.25;
            if (Math.abs(cy(i) - y) <= 2.5 && d < bd) { bd = d; best = i; }
        }
        return best;
    }

    // ---- scanning the world -----------------------------------------------------------------------------------------
    /** scan the box around a station and build its graph (null when there is no platform to stand on) */
    public static PaxGraph scan(World w, long key, int radius, int height) {
        BlockPos k = BlockPos.fromLong(key);
        int x0 = k.getX() - radius, x1 = k.getX() + radius, z0 = k.getZ() - radius, z1 = k.getZ() + radius;
        int y0 = Math.max(1, k.getY() - height), y1 = Math.min(254, k.getY() + height);
        Set<Long> rails = rails(w, k, x0, x1, z0, z1, y0, y1);
        List<int[]> cells = new ArrayList<>();
        List<Object[]> benches = new ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                p.setPos(x, 64, z);
                if (!w.isBlockLoaded(p)) continue;
                for (int y = y0; y <= y1; y++) {
                    p.setPos(x, y, z);
                    IBlockState cell = w.getBlockState(p);
                    if (cell.getBlock() instanceof com.dogpound.railmap.station.BlockStationPiece bp
                            && (bp.kind == com.dogpound.railmap.station.BlockStationPiece.Kind.BENCH || bp.kind == com.dogpound.railmap.station.BlockStationPiece.Kind.SHELTER))
                        benches.add(new Object[]{p.toImmutable(), cell.getValue(com.dogpound.railmap.block.BlockStationDevice.FACING)});
                    if (!passable(w, p, cell)) continue;
                    p.setPos(x, y + 1, z);
                    if (!passable(w, p, w.getBlockState(p))) continue;
                    p.setPos(x, y - 1, z);
                    IBlockState below = w.getBlockState(p);
                    AxisAlignedBB bb = below.getCollisionBoundingBox(w, p);
                    if (bb == null || bb == Block.NULL_AABB) continue;
                    boolean slab = bb.maxY == 0.5 && bb.minX <= 0 && bb.maxX >= 1 && bb.minZ <= 0 && bb.maxZ >= 1;
                    if (!slab && (bb.maxY < 0.99 || bb.maxY > 1.01)) continue;
                    if (below.getMaterial().isLiquid() || rails.contains(p.toLong()) || isTrack(w, p, below)) continue;
                    p.setPos(x, y, z);
                    if (isTrack(w, p, cell)) continue;
                    cells.add(new int[]{x, y, z, (int) Math.round(bb.maxY * 1000)});
                }
            }
        PaxGraph g = assemble(key, cells, rails, x0, x1, z0, z1);
        if (g.n == 0 || g.spots.length == 0) return null;
        // fittings: machines, gates, boards (from the chunks' tile maps, not the world's whole list)
        List<BlockPos> attract = new ArrayList<>();
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++)
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                if (!w.isBlockLoaded(new BlockPos(cx << 4, 64, cz << 4))) continue;
                Chunk ch = w.getChunk(cx, cz);
                for (TileEntity te : new ArrayList<>(ch.getTileEntityMap().values())) {
                    BlockPos tp = te.getPos();
                    if (tp.getX() < x0 || tp.getX() > x1 || tp.getZ() < z0 || tp.getZ() > z1 || tp.getY() < y0 || tp.getY() > y1) continue;
                    if (te instanceof com.dogpound.railmap.block.TileTicketMachine || te instanceof com.dogpound.railmap.station.IShop) {   // shops: people queue at them too (#5)
                        int nd = g.nearest(tp.getX() + 0.5, tp.getY(), tp.getZ() + 0.5, 2.2, false);
                        if (nd >= 0) g.machines.add(new int[]{nd, tp.getX(), tp.getY(), tp.getZ()});
                        attract.add(tp);
                    } else if (te instanceof com.dogpound.railmap.block.TileTicketGate) {
                        int nd = g.at(tp.getX(), tp.getY(), tp.getZ());
                        if (nd >= 0) g.gates.put(nd, tp);
                        attract.add(tp);
                    } else if (te instanceof com.dogpound.railmap.block.TileArrivalsBoard || te instanceof com.dogpound.railmap.station.TilePisBoard) {
                        g.boards.add(tp); attract.add(tp);
                    } else if (te instanceof com.dogpound.railmap.station.IStationFacility) attract.add(tp);
                }
            }
        g.fields.remove(F_MACHINE);
        g.finishConcourse(attract);
        // benches: two seats each, met from the cell in front
        int[] fp = g.field(F_PLATFORM);
        for (Object[] b : benches) {
            BlockPos bp = (BlockPos) b[0];
            EnumFacing f = (EnumFacing) b[1];
            int nd = g.at(bp.getX() + f.getXOffset(), bp.getY(), bp.getZ() + f.getZOffset());
            if (nd < 0) nd = g.nearest(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5, 1.6, false);
            if (nd < 0) continue;
            float yaw = f.getHorizontalAngle();
            double lx = f.getZOffset(), lz = -f.getXOffset();       // along the bench
            boolean plat = g.platform[nd] || fp[nd] <= 3;
            for (int s = -1; s <= 1; s += 2)
                g.seats.add(new Seat(bp.getX() + 0.5 + lx * 0.26 * s - f.getXOffset() * 0.05, bp.getY() - 0.06, bp.getZ() + 0.5 + lz * 0.26 * s - f.getZOffset() * 0.05, yaw, nd, plat));
        }
        return g;
    }

    private static boolean passable(World w, BlockPos p, IBlockState s) {
        Block b = s.getBlock();
        if (b instanceof com.dogpound.railmap.block.BlockTicketGate || b instanceof com.dogpound.railmap.doors.BlockPlatformDoor) return true;
        if (s.getMaterial().isLiquid()) return false;
        AxisAlignedBB bb = s.getCollisionBoundingBox(w, p);
        return bb == null || bb == Block.NULL_AABB;
    }

    private static boolean isTrack(World w, BlockPos p, IBlockState s) {
        if (!s.getBlock().hasTileEntity(s)) return false;
        try { return com.dogpound.railmap.scan.SwitchControl.isRail(w, p); } catch (RuntimeException | LinkageError e) { return false; }
    }

    /** the stop track: IR rail blocks flood-filled from the station's own position */
    private static Set<Long> rails(World w, BlockPos k, int x0, int x1, int z0, int z1, int y0, int y1) {
        Set<Long> out = new HashSet<>();
        BlockPos start = null;
        outer:
        for (int r = 0; r <= 3; r++)
            for (int dx = -r; dx <= r; dx++) for (int dy = -2; dy <= 2; dy++) for (int dz = -r; dz <= r; dz++) {
                BlockPos p = k.add(dx, dy, dz);
                if (w.isBlockLoaded(p) && isTrack(w, p, w.getBlockState(p))) { start = p; break outer; }
            }
        if (start == null) return out;
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(start); out.add(start.toLong());
        while (!q.isEmpty() && out.size() < 4000) {
            BlockPos p = q.poll();
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos o = p.add(dx, dy, dz);
                if (o.getX() < x0 || o.getX() > x1 || o.getZ() < z0 || o.getZ() > z1 || o.getY() < y0 || o.getY() > y1) continue;
                if (out.contains(o.toLong()) || !w.isBlockLoaded(o) || !isTrack(w, o, w.getBlockState(o))) continue;
                out.add(o.toLong()); q.add(o);
            }
        }
        return out;
    }

    // ---- bits -------------------------------------------------------------------------------------------------------
    /** the same packing as BlockPos.toLong, without making a BlockPos */
    public static long pk(int x, int y, int z) { return ((long) x & 0x3FFFFFFL) << 38 | ((long) y & 0xFFFL) << 26 | ((long) z & 0x3FFFFFFL); }
    static long colKey(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }
    static double sq(double v) { return v * v; }
    /** Minecraft yaw facing along (dx, dz) */
    public static float yaw(double dx, double dz) { return (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90f; }
    private static int[] toArr(List<Integer> l) { int[] o = new int[l.size()]; for (int i = 0; i < o.length; i++) o[i] = l.get(i); return o; }
}
