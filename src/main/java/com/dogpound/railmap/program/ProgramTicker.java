package com.dogpound.railmap.program;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Runs every world's signal-box program twice a second. */
public class ProgramTicker {
    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side.isClient() || e.world.getTotalWorldTime() % 10 != 3) return;
        ProgramData d = ProgramData.get(e.world);
        if (!d.rules.isEmpty()) d.run(e.world);
    }
}
