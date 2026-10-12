package com.dogpound.railmap.economy;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapConfig;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** runs the railway economy every 5 s in each loaded dimension: companies' books, commodity prices, passengers */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class EconClock {
    private EconClock() {}

    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote || !RailMapConfig.railwayEconomy || e.world.getTotalWorldTime() % 100 != 53) return;
        try {
            MarketData.get(e.world).tick(e.world);
            PassengerData.get(e.world).tick(e.world);
            CompanyData.get(e.world).tick(e.world);
        } catch (RuntimeException ex) {
            RailMap.LOG.warn("[IR Extras] railway economy tick failed: {}", ex.toString());
        }
    }
}
