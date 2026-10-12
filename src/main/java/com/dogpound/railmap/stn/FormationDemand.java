package com.dogpound.railmap.stn;

import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.economy.PassengerData;
import net.minecraft.world.World;

import java.util.*;

/**
 * Automatic train formation planning by demand (ChatGPT batch 3 #26). Once a minute, for every line: how many people
 * the busiest train on it carries and how many are waiting at its stations for stations on it (per train), kept as a
 * 24-hour profile (StnData.demand, peaks remembered, fading slowly). From that and the seats per car of the trains on
 * the line the planner recommends cars per train for each hour at the target load factor, capped by the longest train
 * the line's platforms take.
 */
public final class FormationDemand {
    private FormationDemand() {}

    public static final class LineNow {
        public String line;
        public int trains, cars, seats;
        public double perCar, aboardMax, waitingPerTrain, loadPct;
    }

    public static LineNow now(World w, String line) {
        LineNow n = new LineNow();
        n.line = line;
        RailwayData rd = RailwayData.get(w);
        PassengerData pd = PassengerData.get(w);
        RailwayData.Line l = rd.line(line);
        int carsSum = 0, seatSum = 0;
        for (RailwayData.AutoTrain a : rd.trains().values()) {
            if (!line.equals(a.line)) continue;
            n.trains++;
            int c = pd.cars(a.loco), s = pd.seats(a.loco);
            carsSum += Math.max(0, c - 1); seatSum += s;
            double ab = pd.aboard(a.loco);
            n.aboardMax = Math.max(n.aboardMax, ab);
            if (s > 0) n.loadPct = Math.max(n.loadPct, ab * 100 / s);
        }
        if (n.trains > 0) { n.cars = Math.round(carsSum / (float) n.trains); n.seats = Math.round(seatSum / (float) n.trains); }
        n.perCar = carsSum > 0 ? seatSum / (double) carsSum : 60;
        if (l != null) {
            double wait = 0;
            for (long st : l.stations) {
                PassengerData.Stn s = pd.stn(st);
                if (s == null) continue;
                for (Map.Entry<Long, Double> e : s.waiting.entrySet()) if (l.stations.contains(e.getKey())) wait += e.getValue();
            }
            n.waitingPerTrain = wait / Math.max(1, n.trains);
        }
        return n;
    }

    /** recommended passenger cars for a demand (people per train) */
    public static int recommend(double demand, double perCar, int lfPct, int maxCars) {
        if (demand <= 0.5) return 1;
        int c = (int) Math.ceil(demand / Math.max(1, perCar * lfPct / 100.0));
        return Math.max(1, Math.min(maxCars, c));
    }

    /** once a minute: fold the present demand into each line's hourly profile */
    static void sample(World w) {
        StnData d = StnData.get(w);
        int h = PassengerData.hour(w);
        for (String line : RailwayData.get(w).lines().keySet()) {
            LineNow n = now(w, line);
            double v = Math.max(n.aboardMax, n.waitingPerTrain);
            double[] p = d.demand(line);
            p[h] = v > p[h] ? p[h] * 0.5 + v * 0.5 : p[h] * 0.97 + v * 0.03;
        }
        d.demand.keySet().removeIf(k -> !RailwayData.get(w).lines().containsKey(k));
        d.markDirty();
    }
}
