package com.dogpound.railmap.light;

import com.dogpound.railmap.RailMap;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The batched light-block queue: light updates are the expensive part of 1.12 lighting, so floodlights never place or
 * clear their ground light directly - they queue it here and a few blocks are done each tick (helperOpsPerTick).
 * Only on a state change (switched on / off), never every tick.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class LightHelpers {
    private LightHelpers() {}

    public static Block helper;                                              // set by LightBlocks
    private static final Map<World, ArrayDeque<long[]>> QUEUE = new WeakHashMap<>();
    static long placed, removed;

    /** queue a light block of `level` at p (0 = remove ours) */
    public static void queue(World w, BlockPos p, int level) {
        QUEUE.computeIfAbsent(w, k -> new ArrayDeque<>()).add(new long[]{p.toLong(), level});
    }

    public static int pending(World w) { ArrayDeque<long[]> q = QUEUE.get(w); return q == null ? 0 : q.size(); }

    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote || helper == null) return;
        ArrayDeque<long[]> q = QUEUE.get(e.world);
        if (q == null || q.isEmpty()) return;
        for (int n = 0; n < LightConfig.helperOpsPerTick && !q.isEmpty(); n++) apply(e.world, q.poll());
    }

    static void apply(World w, long[] op) {
        BlockPos p = BlockPos.fromLong(op[0]);
        if (!w.isBlockLoaded(p)) return;
        IBlockState s = w.getBlockState(p);
        int level = (int) op[1];
        if (level <= 0) {
            if (s.getBlock() == helper) { w.setBlockToAir(p); removed++; }
            return;
        }
        if (s.getBlock() == helper) {
            if (s.getValue(BlockLightHelper.LEVEL) < level) { w.setBlockState(p, helper.getDefaultState().withProperty(BlockLightHelper.LEVEL, level), 2); placed++; }
            return;
        }
        if (s.getBlock().isAir(s, w, p) && s.getMaterial() == net.minecraft.block.material.Material.AIR) {
            w.setBlockState(p, helper.getDefaultState().withProperty(BlockLightHelper.LEVEL, level), 2);
            placed++;
        }
    }

    /** a random tick on a light block: keep it only while a lit floodlight lists it */
    static void orphanCheck(World w, BlockPos p) {
        int cx = p.getX() >> 4, cz = p.getZ() >> 4;
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                Chunk c = w.getChunkProvider().getLoadedChunk(cx + dx, cz + dz);
                if (c == null) return;                                      // can't see its floodlight: keep it for now
                for (TileEntity t : c.getTileEntityMap().values())
                    if (t instanceof TileLuminaire l && l.ownsHelper(p.toLong())) return;
            }
        w.setBlockToAir(p);
        removed++;
    }

    /** remove every light block a list names (a floodlight switched off or was broken) */
    public static void clear(World w, long[] list) {
        if (list == null) return;
        for (long l : list) queue(w, BlockPos.fromLong(l), 0);
    }
}
