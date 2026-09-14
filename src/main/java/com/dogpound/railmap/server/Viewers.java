package com.dogpound.railmap.server;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Who currently needs live train positions: players standing near a board/panel (the tile
 * marks them while it ticks) and players holding a map open (the GUI pings every 2 s).
 * Nobody watching = the train tracker does nothing, so idle servers pay nothing.
 */
public final class Viewers {
    /** Ticks a mark stays valid; tiles re-mark every 20, GUIs every 40. */
    private static final int TTL = 100;
    public static final double TILE_RANGE = 64;

    private static final Map<UUID, Long> until = new HashMap<>();

    private Viewers() {}

    public static void mark(EntityPlayerMP p) {
        until.put(p.getUniqueID(), p.world.getTotalWorldTime() + TTL);
    }

    /** Called by display tiles: everyone in range of the tile is watching it. */
    public static void markAround(World world, BlockPos pos) {
        for (EntityPlayerMP p : world.getMinecraftServer().getPlayerList().getPlayers()) {
            if (p.world == world && p.getDistanceSq(pos) <= TILE_RANGE * TILE_RANGE) mark(p);
        }
    }

    public static List<EntityPlayerMP> active(World world) {
        long now = world.getTotalWorldTime();
        List<EntityPlayerMP> out = new ArrayList<>();
        Iterator<Map.Entry<UUID, Long>> it = until.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> e = it.next();
            if (e.getValue() < now) {
                it.remove();
                continue;
            }
            EntityPlayerMP p = world.getMinecraftServer().getPlayerList().getPlayerByUUID(e.getKey());
            if (p != null && p.world == world) out.add(p);
        }
        return out;
    }

    public static void clear() {
        until.clear();
    }
}
