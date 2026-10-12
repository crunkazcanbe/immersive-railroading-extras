package com.dogpound.railmap.planning;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.auto.RailwayData;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Runs the planning side per world (server): joins every 5 ticks; works, dispatchers, split plans, version watch once a second; predictions + resilience now and then. */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class PlanTicker {
    private PlanTicker() {}

    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote) return;
        World w = e.world;
        long now = w.getTotalWorldTime();
        try {
            if (now % 5 == 2) SplitJoin.tickJoins(w);
            if (now % 20 != 11) return;
            int sec = (int) (now / 20);
            TempWorks.tick(w, now);
            PossessionPlanner.tick(w, now);                       // infra #45: gangs work the planned possessions
            Dispatchers.tick(w, now);
            SplitJoin.tickPlans(w);
            Versions.watch(w, now);
            if (sec % 60 == 17) TwinLab.checkPredictions(w);
            if (sec % 300 == 41 && !RailwayData.get(w).lines().isEmpty()) Resilience.store(w, Resilience.study(w));
        } catch (RuntimeException | LinkageError ex) {
            if (now / 20 % 60 == 0) RailMap.LOG.warn("IR Extras planning tick: {}", ex.toString());
        }
    }

    @SubscribeEvent
    public static void unload(WorldEvent.Unload e) {
        if (e.getWorld().isRemote || e.getWorld().provider.getDimension() != 0) return;
        TempWorks.clear();
        Dispatchers.clear();
        SplitJoin.clear();
        Versions.clear();
    }
}
