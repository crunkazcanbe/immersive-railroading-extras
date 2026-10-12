package com.dogpound.railmap.ems;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.*;

/** (Copied from Pride Realms' delivery robot, realmcoin/delivery/RoadPath - same author; IR Extras can't depend on it.)
 *  A* over walkable ground for the emergency vehicles: 8 directions, 1-block step up, up to 3 down, needs 2 blocks of
 *  head room. Roads are cheap, everything else costs more, so it keeps to the street when there is one.
 *  If the goal can't be reached inside the search budget it returns the path to the closest point it found. */
public final class RoadPath {
    private RoadPath() {}

    static final int MAX_NODES = 9000;
    static final double ROAD = 1.0, OFFROAD = 2.6;

    /** paths in, roads and pavements: path blocks, gravel, concrete, stone bricks, smooth stone + any "road"-like name */
    public static boolean isRoad(IBlockState s) {
        Block b = s.getBlock();
        if (b == Blocks.GRASS_PATH || b == Blocks.GRAVEL || b == Blocks.CONCRETE || b == Blocks.STONEBRICK
                || b == Blocks.DOUBLE_STONE_SLAB || b == Blocks.STONE_SLAB) return true;
        if (b == Blocks.STONE && s.getValue(net.minecraft.block.BlockStone.VARIANT).isNatural()) return false;
        if (b == Blocks.STONE) return true;                                       // polished granite / diorite / andesite
        ResourceLocation r = b.getRegistryName();
        if (r == null) return false;
        String n = r.getPath();
        return n.contains("road") || n.contains("asphalt") || n.contains("path") || n.contains("concrete") || n.contains("tarmac");
    }

    /** the robot can stand with its feet in `feet` (solid floor under it, two blocks of air) */
    public static boolean standable(World w, BlockPos feet) {
        if (!w.isBlockLoaded(feet)) return false;
        BlockPos below = feet.down();
        IBlockState fl = w.getBlockState(below);
        if (fl.getCollisionBoundingBox(w, below) == null || fl.getMaterial().isLiquid()) return false;
        return passable(w, feet) && passable(w, feet.up());
    }

    static boolean passable(World w, BlockPos p) {
        IBlockState s = w.getBlockState(p);
        Material m = s.getMaterial();
        return !m.isLiquid() && s.getCollisionBoundingBox(w, p) == null;
    }

    /** where something standing near `p` would have its feet: drop down / climb up a few blocks to the ground */
    @Nullable
    public static BlockPos ground(World w, BlockPos p, int range) {
        for (int dy = 0; dy <= range; dy++) {
            if (standable(w, p.down(dy))) return p.down(dy);
            if (dy > 0 && standable(w, p.up(dy))) return p.up(dy);
        }
        return null;
    }

    private static final class Node implements Comparable<Node> {
        final BlockPos p; final double g, f; final Node parent;
        Node(BlockPos p, double g, double f, Node parent) { this.p = p; this.g = g; this.f = f; this.parent = parent; }
        @Override public int compareTo(Node o) { return Double.compare(f, o.f); }
    }

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    /** the cells to drive through from start to goal (both feet positions), or null if start is boxed in.
     *  range = how far from start the search may wander */
    @Nullable
    public static List<BlockPos> find(World w, BlockPos start, BlockPos goal, int range) {
        if (!standable(w, start)) { start = ground(w, start, 3); if (start == null) return null; }
        PriorityQueue<Node> open = new PriorityQueue<>();
        Map<BlockPos, Double> best = new HashMap<>();
        Node first = new Node(start, 0, h(start, goal), null), closest = first;
        open.add(first);
        best.put(start, 0.0);
        int expanded = 0;
        while (!open.isEmpty() && expanded++ < MAX_NODES) {
            Node n = open.poll();
            if (n.g > best.getOrDefault(n.p, Double.MAX_VALUE)) continue;
            if (h(n.p, goal) < h(closest.p, goal)) closest = n;
            if (n.p.distanceSq(goal) <= 2.0) return trace(n);
            for (int[] d : DIRS) {
                BlockPos next = step(w, n.p, d[0], d[1]);
                if (next == null || Math.abs(next.getX() - start.getX()) > range || Math.abs(next.getZ() - start.getZ()) > range) continue;
                if (d[0] != 0 && d[1] != 0                                          // no cutting corners
                        && (step(w, n.p, d[0], 0) == null || step(w, n.p, 0, d[1]) == null)) continue;
                double cost = (d[0] != 0 && d[1] != 0 ? 1.4142 : 1.0) * (isRoad(w.getBlockState(next.down())) ? ROAD : OFFROAD)
                        + Math.abs(next.getY() - n.p.getY()) * 0.6;
                double g = n.g + cost;
                if (g >= best.getOrDefault(next, Double.MAX_VALUE)) continue;
                best.put(next, g);
                open.add(new Node(next, g, g + h(next, goal), n));
            }
        }
        return closest == first ? null : trace(closest);
    }

    private static double h(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dy = a.getY() - b.getY(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz) * ROAD;
    }

    /** the ground cell one step over (same level, one up, or up to three down), or null */
    @Nullable
    static BlockPos step(World w, BlockPos from, int dx, int dz) {
        BlockPos side = from.add(dx, 0, dz);
        if (standable(w, side)) return side;
        if (standable(w, side.up()) && passable(w, from.up(2))) return side.up();     // a kerb: climb one
        for (int dy = 1; dy <= 3; dy++) {
            if (!passable(w, side.down(dy - 1)) || !passable(w, side.down(dy - 1).up())) return null;
            if (standable(w, side.down(dy))) return side.down(dy);
        }
        return null;
    }

    private static List<BlockPos> trace(Node n) {
        LinkedList<BlockPos> out = new LinkedList<>();
        for (; n != null; n = n.parent) out.addFirst(n.p);
        return new ArrayList<>(out);
    }
}
