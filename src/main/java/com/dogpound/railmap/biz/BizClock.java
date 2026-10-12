package com.dogpound.railmap.biz;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapConfig;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** runs the world + business layer every 5 s in each loaded dimension (after the economy's own tick) */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class BizClock {
    private BizClock() {}

    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote || !BizConfig.enabled || !RailMapConfig.railwayEconomy || e.world.getTotalWorldTime() % 100 != 71) return;
        try {
            BizData.get(e.world).tick(e.world);
        } catch (RuntimeException ex) {
            RailMap.LOG.warn("[IR Extras] world + business tick failed: {}", ex.toString());
        }
    }

    /** a brand-new world with scenarioOnNewWorld set is set up when its first player arrives (#47) */
    @SubscribeEvent
    public static void login(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent e) {
        if (!BizConfig.enabled || BizConfig.scenarioOnNewWorld.isEmpty() || e.player.world.isRemote) return;
        try { BizData d = BizData.get(e.player.world); d.scenario.autoApply(e.player.world, d, e.player, BizConfig.scenarioOnNewWorld); }
        catch (RuntimeException ex) { RailMap.LOG.warn("[IR Extras] scenario setup failed: {}", ex.toString()); }
    }
}
