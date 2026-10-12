package com.dogpound.railmap.depot;

import cam72cam.immersiverailroading.entity.Locomotive;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.server.WearData;
import net.minecraft.world.World;

/**
 * Why a driverless train standing at a station may not leave yet (Autopilot asks when its dwell is up): failed in
 * service (waiting for its spare / rescue - it may still limp to the depot it was sent to), stopped out of service,
 * failed its brake test, or (an attended line) no fit, qualified driver on it. null = it may go.
 */
public final class DepotHolds {
    private DepotHolds() {}

    public static String departure(World w, RailwayData.AutoTrain a, Locomotive l) {
        try {
            FleetData f = FleetData.get(w);
            FleetData.Unit u = f.peek(a.loco);
            boolean toDepot = u != null && u.role == FleetData.DEPOT && a.mode == RailwayData.Mode.SEND;
            if (DepotConfig.failedHoldsTrain && !toDepot) {
                String failed = Fleet.failedHere(WearData.get(w), f, l);
                if (!failed.isEmpty()) return "FAILED IN SERVICE - " + failed + " - awaiting rescue";
            }
            if (u != null && u.role == FleetData.STOPPED) return "STOPPED - out of service";
            if (u != null && !u.brakeOk && !toDepot) return "NOT FIT TO RUN - brake test failed";
            String crew = Crew.departure(w, a, l);
            return crew != null ? crew : com.dogpound.railmap.crew.CrewTicker.departure(w, a, l);   // batch 5 #7: driver in the cab, guard's right-away, fatigue
        } catch (LinkageError | RuntimeException e) {
            return null;
        }
    }
}
