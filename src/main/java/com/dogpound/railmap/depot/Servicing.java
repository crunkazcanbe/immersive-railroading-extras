package com.dogpound.railmap.depot;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.economy.PassengerData;
import net.minecraft.world.World;

/**
 * Train cleaning and servicing (her ChatGPT list #83). Passenger stock gets dirty inside as people ride, dirty outside
 * with the miles (twice as fast in the rain), uses its fresh water, fills its toilet (CET) tanks and sells its
 * catering. Below 40 % clean, with no water or a full toilet tank, or nothing left to sell, riders on its line are less
 * satisfied (and complain). The Carriage Servicing Point and the Train Wash Plant put it right, at a cost to the
 * operator.
 */
public final class Servicing {
    private Servicing() {}

    public static final String[] WHAT = {"interior", "exterior", "water", "toilet tank", "catering"};

    /** one second of running */
    static void ride(World w, FleetData.Unit u, EntityRollingStock s, float kmh) {
        double km = kmh / 3600.0;
        double rain = w.isRaining() ? 2 : 1;
        u.interior = clamp(u.interior - km * 1.6);
        u.exterior = clamp(u.exterior - km * 0.9 * rain);
        u.water = clamp(u.water - km * 1.2);
        u.waste = clamp(u.waste + km * 1.0);
        u.catering = clamp(u.catering - km * 1.0);
    }

    static float clamp(double v) { return (float) Math.max(0, Math.min(100, v)); }

    /** the worst servicing need of a vehicle, 0..100 (100 = needs it badly) */
    public static int need(FleetData.Unit u) {
        return Math.round(Math.max(Math.max(100 - u.interior, 100 - u.water), Math.max(u.waste, Math.max(100 - u.catering, (100 - u.exterior) * 0.7f))));
    }

    public static String state(FleetData.Unit u) {
        return String.format(java.util.Locale.ROOT, "in %d · ext %d · water %d · CET %d · cat %d", Math.round(u.interior), Math.round(u.exterior), Math.round(u.water), Math.round(u.waste), Math.round(u.catering));
    }

    /** every 5 s: what the riders on each line think of the state of its trains */
    static void lines(World w, FleetData f) {
        if (!DepotConfig.servicing) return;
        PassengerData pd = PassengerData.get(w);
        for (RailwayData.AutoTrain a : RailwayData.get(w).trains().values()) {
            if (a.line.isEmpty()) continue;
            PassengerData.LineStat ls = pd.line(a.line);
            if (ls == null) continue;
            Locomotive l = Autopilot.find(w, a.loco);
            if (l == null) continue;
            double bad = 0;
            int n = 0;
            for (EntityCoupleableRollingStock c : l.getTrain()) {
                FleetData.Unit u = f.peek(c.getUUID());
                if (u == null || !u.passenger) continue;
                n++;
                if (u.interior < 40) bad += 1;
                if (u.water <= 0 || u.waste >= 100) bad += 1;
                if (u.catering <= 0) bad += 0.3;
                if (u.exterior < 20) bad += 0.3;
            }
            if (n == 0 || bad <= 0) continue;
            double k = bad / n;
            ls.sat = Math.max(0, ls.sat - 0.05 * k);
            ls.complaintsDay += 0.02 * k;
        }
    }
}
