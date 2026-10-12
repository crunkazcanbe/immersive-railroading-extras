package com.dogpound.railmap.incident;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.wire.IWiredMachine;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Runs incident command, the train recorders, analytics and work-order checks once a second per world (server). */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class IncidentTicker {
    private IncidentTicker() {}

    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote) return;
        World w = e.world;
        long now = w.getTotalWorldTime();
        if (now % 20 != 7) return;
        int sec = (int) (now / 20);
        try {
            TrainRecorder.tick(w, now, sec);
            IncidentOps.tick(w, now);
            Analytics.tick(w, now);
            if (sec % 5 == 0) workOrders(w);
        } catch (RuntimeException | LinkageError ex) {
            if (sec % 60 == 0) RailMap.LOG.warn("IR Extras incident tick: {}", ex.toString());
        }
    }

    /** a work order closes itself once the gear it was raised for is healthy again (lamp green, 12 V back) */
    private static void workOrders(World w) {
        IncidentLog log = IncidentLog.get(w);
        for (IncidentLog.WorkOrder o : log.orders) {
            if (o.done != 0) continue;
            BlockPos p = BlockPos.fromLong(o.pos);
            if (!w.isBlockLoaded(p)) continue;
            TileEntity te = w.getTileEntity(p);
            if (te == null) { o.done = w.getTotalWorldTime(); o.closedWhy = "equipment removed"; log.changed(); continue; }
            if (te instanceof IWiredMachine m && m.wiredLamp() == 1) { o.done = w.getTotalWorldTime(); o.closedWhy = "fault cleared - verified remotely"; log.changed(); }
        }
    }

    @SubscribeEvent
    public static void unload(WorldEvent.Unload e) {
        if (e.getWorld().isRemote || e.getWorld().provider.getDimension() != 0) return;
        IncidentHooks.clear();
        IncidentOps.clear();
    }
}
