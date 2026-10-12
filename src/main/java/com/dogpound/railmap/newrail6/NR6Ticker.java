package com.dogpound.railmap.newrail6;

import com.dogpound.railmap.RailMap;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.*;

/** Batch 6 #16-20 server tick: rack supervision, ferry voyages, motorail sync to clients; signal holds for TrackHooks. */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class NR6Ticker {
    private NR6Ticker() {}

    private static final Map<Integer, Integer> SENT = new HashMap<>();

    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote) return;
        World w = e.world;
        long now = w.getTotalWorldTime();
        try { Rack.tick(w, now); } catch (RuntimeException | LinkageError ex) { if (now % 1200 == 5) RailMap.LOG.warn("[IR Extras] rack tick failed: {}", ex.toString()); }
        Ferry.tick(w, now);
        try { Motorail.orphan(w, now); } catch (RuntimeException | LinkageError ignored) { }
        NR6Data d = NR6Data.get(w);
        int dim = w.provider.getDimension();
        if (now % 40 == 9 && SENT.getOrDefault(dim, -1) != d.version) {
            SENT.put(dim, d.version);
            NR6Net.NET.sendToDimension(new NR6Net.Loads(d.loads), dim);
        }
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.player instanceof EntityPlayerMP p) NR6Net.NET.sendTo(new NR6Net.Loads(NR6Data.get(p.world).loads), p);
    }

    @SubscribeEvent
    public static void changedDim(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.player instanceof EntityPlayerMP p) NR6Net.NET.sendTo(new NR6Net.Loads(NR6Data.get(p.world).loads), p);
    }

    /** nobody steps off a ferry at sea */
    @SubscribeEvent
    public static void mount(EntityMountEvent e) {
        if (e.getWorldObj().isRemote || !e.isDismounting() || !(e.getEntityBeingMounted() instanceof EntityTrainFerry f)) return;
        if (f.isDead || f.ejecting || f.berthed()) return;
        e.setCanceled(true);
        e.getEntityMounting().sendMessage(new TextComponentString("§cThe ferry is at sea - you can go ashore at a berth"));
    }

    /** SafetyHolds (via TrackHooks): our gear holding signals */
    public static void holds(World w, Map<Long, String> hold, List<Object[]> zones) {
        for (NR6Tile t : NR6Tile.live(w, NR6Tile.class)) {
            try { t.holds(hold, zones); } catch (RuntimeException ignored) { }
        }
    }

    /** the garden station beside a piece of garden track (within 2 blocks) */
    static TileGardenStation gardenStation(World w, BlockPos p) {
        TileGardenStation best = null;
        double bd = 9;
        for (TileGardenStation s : NR6Tile.live(w, TileGardenStation.class)) {
            BlockPos q = s.getPos();
            if (Math.abs(q.getY() - p.getY()) > 1) continue;
            int dx = Math.abs(q.getX() - p.getX()), dz = Math.abs(q.getZ() - p.getZ());
            if (dx != 0 && dz != 0 || dx + dz == 0 || dx + dz > 2) continue;   // straight beside the track, 1-2 blocks off
            double d = dx + dz;
            if (d * d < bd) { bd = d * d; best = s; }
        }
        return best;
    }
}
