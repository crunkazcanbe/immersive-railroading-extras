package com.dogpound.railmap.stn;

import com.dogpound.railmap.RailMap;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** station services clock: diagrams + parcels every second, the formation demand profile every minute */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class StnTicker {
    private StnTicker() {}

    private static final boolean IR = Loader.isModLoaded("immersiverailroading");

    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side.isClient() || !IR) return;
        World w = e.world;
        long now = w.getTotalWorldTime();
        if (now % 20 != 9) return;
        if (com.dogpound.railmap.auto.RailwayData.get(w).trains().isEmpty()) return;
        try {
            Diagrams.track(w);
            Parcels.tick(w);
            if (now % 1200 == 9) FormationDemand.sample(w);
        } catch (RuntimeException | LinkageError ex) {
            RailMap.LOG.warn("[IR Extras] station services tick failed: {}", ex.toString());
        }
    }
}
