package com.dogpound.railmap.station;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The small hooks the rest of the mod calls for the platform operations gear (ChatGPT batch 3). Every one returns the
 * old behaviour when no crowd monitor / security panel / door controller is placed at that station.
 */
public final class PlatformOps {
    private PlatformOps() {}

    /** PassengerData.generate: share of new passengers let in (1 = normal; metering, a closed entrance or a closed station cut it) */
    public static double inflow(World w, long station) {
        try {
            double f = 1;
            for (TileStationSecurity s : PlatformTile.at(w, TileStationSecurity.class, station)) if (s.closed()) return 0;
            for (TileCrowdMonitor m : PlatformTile.at(w, TileCrowdMonitor.class, station)) f = Math.min(f, m.inflow());
            return f;
        } catch (RuntimeException | LinkageError e) { return 1; }
    }

    /** TileTicketGate.present: why a closed station's gate turns someone away going in (null = let them) */
    public static String gateRefuse(World w, long station) {
        try {
            for (TileStationSecurity s : PlatformTile.at(w, TileStationSecurity.class, station)) if (s.closed()) return "Station closed - it opens at " + s.opensAt() + ". Exit only.";
            for (TileCrowdMonitor m : PlatformTile.at(w, TileCrowdMonitor.class, station)) if (m.entranceClosed()) return "Entrance closed for crowd control - please wait.";
        } catch (RuntimeException | LinkageError e) { return null; }
        return null;
    }

    /** TilePlatformLamp (AUTO): a closed station's night mode switches this lamp off */
    public static boolean lampOff(World w, BlockPos lamp) {
        try {
            for (TileStationSecurity s : PlatformTile.loaded(w, TileStationSecurity.class))
                if (s.closed() && s.getPos().distanceSq(lamp) <= s.radius() * s.radius()) return s.lampOff(lamp);
        } catch (RuntimeException | LinkageError e) { return false; }
        return false;
    }

    /** TileStationDoors: may this car's doors open? (a door safety controller near it decides) */
    public static boolean doorsMayOpen(World w, BlockPos doors, EntityRollingStock car) {
        try {
            TileDoorController best = null;
            double bd = 64 * 64;
            for (TileDoorController c : PlatformTile.loaded(w, TileDoorController.class)) {
                double d = c.getPos().distanceSq(doors);
                if (d < bd) { bd = d; best = c; }
            }
            return best == null || best.mayOpen(car);
        } catch (RuntimeException | LinkageError e) { return true; }
    }
}
