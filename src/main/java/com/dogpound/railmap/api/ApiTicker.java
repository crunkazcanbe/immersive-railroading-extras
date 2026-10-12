package com.dogpound.railmap.api;

import com.dogpound.railmap.RailMap;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Runs the train orders and scenario objectives once a second, and the export every exportSeconds (server, per world). */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class ApiTicker {
    private ApiTicker() {}

    private static boolean ccDone;

    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote) return;
        World w = e.world;
        long now = w.getTotalWorldTime();
        if (now % 20 != 11) return;
        if (!ccDone) { ccDone = true; CCBridge.init(); }                 // after every mod is up
        try {
            TrainOrders.get(w).tick(w);
            ScenarioApi.get(w).tick(w);
            if (w.provider.getDimension() == 0 && now / 20 % ApiConfig.exportSeconds == 0) Export.snapshot(w);
        } catch (RuntimeException | LinkageError ex) {
            if (now % 1200 == 11) RailMap.LOG.warn("IR Extras API tick: {}", ex.toString());
        }
    }

    @SubscribeEvent
    public static void unload(net.minecraftforge.event.world.WorldEvent.Unload e) {
        if (!e.getWorld().isRemote && e.getWorld().provider.getDimension() == 0) Export.stopHttp();   // the port is free again for the next world
    }
}
