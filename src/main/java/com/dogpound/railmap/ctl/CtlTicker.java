package com.dogpound.railmap.ctl;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Server housekeeping for the next-48 control systems: approvals expire, a chaos scenario keeps (and ends) its clock
 *  even if its lab console's chunk unloads. */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class CtlTicker {
    private CtlTicker() {}

    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote) return;
        long t = e.world.getTotalWorldTime();
        if (com.dogpound.railmap.safety.Chaos.running(e.world)) com.dogpound.railmap.safety.Chaos.tick(e.world);
        if (t % 100 == 37) com.dogpound.railmap.admin.Approvals.get(e.world).tick(e.world);
    }
}
