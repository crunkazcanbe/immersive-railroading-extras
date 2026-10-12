package com.dogpound.railmap.freight;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** runs the freight contracts: posting, deadlines (every 5 s, every loaded dimension) */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class ContractClock {
    private ContractClock() {}

    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote || e.world.getTotalWorldTime() % 100 != 37) return;
        ContractData.get(e.world).tick(e.world);
    }
}
