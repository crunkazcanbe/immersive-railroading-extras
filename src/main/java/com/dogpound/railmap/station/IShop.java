package com.dogpound.railmap.station;

import com.dogpound.railmap.economy.PassengerData;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * A station shop (vending machine, kiosk, newsstand, coffee kiosk, ticket office counter): visible passengers queue at
 * it like at a ticket machine (pax.PaxGraph lists it with the machines), and it sells to the station's passengers in
 * proportion to how many board here. Takings reach the station's operators through IStationFacility.takeIncome.
 */
public interface IShop {
    /** open for business right now (powered, stocked, shutter up) */
    boolean shopOpen();

    /**
     * people who boarded at this shop's station since the last call: seen[0] holds the last count (-1 = first call).
     * Returns 0 on the first call, after the daily reset, or with no station in reach.
     */
    static double newRiders(World w, BlockPos p, double[] seen) {
        Long st = StationFacilities.stationOf(w, p);
        PassengerData.Stn s = st == null ? null : PassengerData.get(w).stn(st);
        if (s == null) return 0;
        double b = s.boardedDay, n = seen[0] < 0 || b < seen[0] ? 0 : b - seen[0];
        seen[0] = b;
        return n;
    }

    /** whole sales from `riders` new passengers when one in `every` buys (the fraction carries over randomly) */
    static int sales(double riders, double every, java.util.Random r) {
        return every <= 0 ? 0 : (int) Math.floor(riders / every + r.nextDouble() * 0.5);
    }
}
