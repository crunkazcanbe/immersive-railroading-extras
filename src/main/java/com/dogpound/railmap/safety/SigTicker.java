package com.dogpound.railmap.safety;

import com.dogpound.railmap.RailMap;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.*;

/**
 * Drives signalling batch 3 (her ChatGPT batch-3 list #11-20, #46, #48, #49) on the server. Once a second it lists the
 * loaded batch-3 gear by class ({@link #tiles}) and runs each part's second tick; every 5 ticks the fast part (trains
 * passing trip cocks and balises). Costs nothing in a world without the gear.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class SigTicker {
    private SigTicker() {}

    private static final Map<Integer, Map<Class<?>, List<TileEntity>>> TILES = new HashMap<>();

    public static void clear() { TILES.clear(); }

    /** the loaded tiles of this class (and subclasses) in this world, as of the last scan (once a second) */
    @SuppressWarnings("unchecked")
    public static <T> List<T> tiles(World w, Class<T> c) {
        Map<Class<?>, List<TileEntity>> m = TILES.get(w.provider.getDimension());
        if (m == null) return Collections.emptyList();
        List<T> out = new ArrayList<>();
        for (Map.Entry<Class<?>, List<TileEntity>> e : m.entrySet()) if (c.isAssignableFrom(e.getKey())) for (TileEntity t : e.getValue()) if (!t.isInvalid()) out.add((T) t);
        return out;
    }

    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side.isClient()) return;
        World w = e.world;
        long now = w.getTotalWorldTime();
        try {
            if (now % 5 == 2) { SigA.fast(w); SigM.fast(w); }
            if (now % 20 != 11) return;
            scan(w);
            SigA.tick(w);
            SigB.tick(w);
            SigC.tick(w);
            SigM.tick(w);
        } catch (LinkageError | RuntimeException ex) {
            RailMap.LOG.warn("[irextras] signalling batch-3 pass failed: {}", ex.toString());
        }
    }

    private static void scan(World w) {
        Map<Class<?>, List<TileEntity>> m = new HashMap<>();
        for (TileEntity t : w.loadedTileEntityList) {
            if (t.isInvalid() || !(t instanceof SafetyTile) || !t.getClass().getName().startsWith("com.dogpound.railmap.safety.")) continue;
            m.computeIfAbsent(t.getClass(), k -> new ArrayList<>()).add(t);
        }
        TILES.put(w.provider.getDimension(), m);
    }
}
