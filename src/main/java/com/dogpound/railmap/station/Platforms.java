package com.dogpound.railmap.station;

import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.depot.DepotConfig;
import net.minecraft.world.World;

/** Autopilot asks where to stop for its next call: a Platform Allocation Panel may give it another platform (her list #82) */
public final class Platforms {
    private Platforms() {}

    /** the rail to stop at for this call (the call itself unless a panel allocates it elsewhere) */
    public static long resolve(World w, RailwayData.AutoTrain a, long stop) {
        if (!DepotConfig.platformAllocation) return stop;
        try {
            for (TilePlatformPanel p : TilePlatformPanel.loaded(w))
                if (p.index(stop) >= 0) return p.allocate(w, a, stop);
        } catch (LinkageError | RuntimeException e) {
            return stop;
        }
        return stop;
    }
}
