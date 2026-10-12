package com.dogpound.railmap.grid;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Locale;

/**
 * Traction substation thermal management (her ChatGPT list #42). A transformer is rated ONAF (oil natural, air forced:
 * fans running) at 30 °C ambient. Without its fans (switched OFF or failed) it is only good for ONAN = 67 % of that; a
 * hotter ambient eats the margin the oil has left (IEEE C57.91 style: k = sqrt((110 - ambient) / 80)), a cold one gives
 * some back (capped at 120 %). Power electronics (rectifiers, inverters) derate 2 % per °C above 40 °C.
 * The ambient is the biome's temperature, warmer by day, cooler in rain; fans wear and fail with their running hours.
 */
public final class Thermal {
    private Thermal() {}

    /** air temperature round a machine, °C */
    /** what plant feels: the weather model's air (heatwaves, cold snaps) when it is on, else the biome's */
    public static double ambient(World w, BlockPos p) {
        if (w != null && com.dogpound.railmap.weather.Weather.on() && w.isBlockLoaded(p)) return com.dogpound.railmap.weather.Weather.airC(w, p);
        return biomeAmbient(w, p);
    }

    /** the biome's own temperature, warmer by day, cooler in rain (the weather model builds on this) */
    public static double biomeAmbient(World w, BlockPos p) {
        if (w == null || !w.isBlockLoaded(p)) return 25;
        double t = w.getBiome(p).getTemperature(p);                   // 0 snow .. 0.8 plains .. 2 desert (falls with height)
        double c = 10 + 20 * (t - 0.5);
        long tod = w.getWorldTime() % 24000;
        c += 5 * Math.sin((tod - 0) / 24000.0 * 2 * Math.PI);         // warmest mid-day
        if (w.isRainingAt(p.up()) || w.isRaining() && w.canSeeSky(p.up())) c -= 3;
        return Math.max(-30, Math.min(55, c));
    }

    /** capacity factor of an oil transformer: fans (ONAF) or none (ONAN), ambient correction */
    public static double transformer(double ambient, boolean fansOk) {
        double k = Math.sqrt(Math.max(0, 110 - ambient) / 80.0);
        return Math.min(1.2, k) * (fansOk ? 1.0 : 0.67);
    }

    /** power electronics: full rating to 40 °C, then 2 % per °C */
    public static double electronics(double ambient) {
        return ambient <= 40 ? 1.0 : Math.max(0.5, 1 - 0.02 * (ambient - 40));
    }

    /** are this transformer's fans able to run? (selector not OFF and not failed) */
    public static boolean fansOk(GridData.Node n) { return n.cool != 0 && !n.fanFail; }

    /** once a second per transformer / rectifier: ambient, fan wear and failure, the derating factor */
    static void tick(World w, GridData d, long pos, GridData.Node n) {
        BlockPos p = BlockPos.fromLong(pos);
        if (w.isBlockLoaded(p)) n.ambient = ambient(w, p);
        if (n.kind == GridKind.TRANSFORMER || n.kind == GridKind.TRACTION) {
            boolean running = n.cool == 2 || (n.cool == 1 && n.heat > 40);
            if (running && !n.fanFail) {
                n.fanHours += 1f / 3600f * 72f;                          // 1 game-hour per 50 s of running (72x real time, like the MC clock)
                // fan motors: about 1 failure per 2000 running hours (they run a lot on a busy substation)
                if (w.rand.nextDouble() < 1.0 / (2000.0 * 50)) {
                    n.fanFail = true;
                    d.markDirty();
                    GridTicker.alarm(w, pos, String.format(Locale.ROOT, "§e⚠ Transformer cooling FAN FAIL at %d %d %d after %.0f h: now ONAN only (67%% rating) - screwdriver to replace the fan motor",
                            p.getX(), p.getY(), p.getZ(), n.fanHours));
                    History.log(w, p, "FAN", String.format(Locale.ROOT, "cooling fan motor failed at %.0f running hours - derated to ONAN", n.fanHours));
                }
            }
            n.derate = transformer(n.ambient, fansOk(n));
            // the daily temperature log (her list #43: "transformer temperatures"): peak winding rise and ambient per day
            int day = (int) (w.getWorldTime() / 24000);
            int peak = Math.max(n.st("tmax"), Math.round((float) (n.heat * 10)));
            if (n.st.containsKey("tday") && n.st("tday") != day) {
                if (peak > 0) History.log(w, p, "TEMP", String.format(Locale.ROOT, "day peak: winding rise %.1f °C (top %.0f °C at %.0f °C ambient), %s", peak / 10.0, n.ambient + peak / 10.0, n.ambient,
                        n.fanFail ? "fans FAILED" : String.format(Locale.ROOT, "fans %.0f h", n.fanHours)));
                peak = 0;
            }
            n.st.put("tday", day);
            n.st.put("tmax", peak);
        } else if (n.kind.electronic()) n.derate = electronics(n.ambient);
        else n.derate = 1;
    }

    /** the settings / multimeter lines */
    public static String describe(GridData.Node n) {
        String cool = n.kind == GridKind.TRANSFORMER || n.kind == GridKind.TRACTION
                ? (n.fanFail ? "ONAN - FANS FAILED" : n.cool == 0 ? "ONAN - fans switched OFF" : "ONAF (fans " + (n.cool == 2 ? "ON" : "AUTO") + ")") : "forced air";
        return String.format(Locale.ROOT, "ambient %.0f °C · %s · derated to %.0f%% (%,d kVA)", n.ambient, cool, n.derate * 100, Math.round(n.kva * n.derate));
    }

    // =================================================================================================================
    // Her 48-next list #22: a real thermal model of the electrical plant, on top of the derating above.
    // Transformers (IEC 60076-7 difference equations): top-oil rise and winding hot-spot gradient each settle toward the
    // value the load calls for with their own thermal inertia (oil ~180 min, winding ~10 min, in game minutes: a real
    // second is 1.2 game minutes), so a short peak is ridden through and a long one is not. Cooling: ONAF with the fans,
    // ONAN without (a fan failure, the fans switched OFF), and a radiator that fouls with dust and leaves the longer the
    // fans run. The hot spot ages the insulation at V = 2^((θh - 98) / 6) per hour (Montsinger); the loss of life is kept.
    // A thermal replica (IEC 60255-149, ANSI 49) gives the inverse-time overload curve t = τ ln((I² - Ip²)/(I² - (k·IB)²)).
    // Motors: the same replica on their current. Everything is kept in the node's latched state map (saved with the grid):
    //   toil top-oil rise x10, hsg hot-spot gradient x10, hs hot spot °C x10, lol aged minutes, foul radiator fouling ‰,
    //   rep thermal replica ‰ (1000 = trip level), mrep motor replica ‰.
    // =================================================================================================================

    /** IEC 60076-7 parameters: {top-oil rise at rated K, loss ratio R, oil exponent x, winding exponent y, hot-spot gradient H·gr, τo min, τw min} */
    static final double[] ONAF = {45, 6, 0.8, 1.3, 26, 150, 7}, ONAN = {52, 6, 0.8, 1.3, 26, 210, 10};
    /** game minutes per real second (the Minecraft day: 24 h in 20 min) */
    static final double DT_MIN = 1.2;

    public static double topOilC(GridData.Node n) { return n.ambient + n.st("toil") / 10.0; }
    public static double hotSpotC(GridData.Node n) { return n.st.containsKey("hs") ? n.st("hs") / 10.0 : n.ambient + 25 + n.heat; }
    /** insulation loss of life so far, in aged hours at the 98 °C reference */
    public static double agedHours(GridData.Node n) { return n.st("lol") / 60.0; }
    public static double fouling(GridData.Node n) { return n.st("foul") / 1000.0; }
    /** thermal replica (49): 1.0 = trip level */
    public static double replica(GridData.Node n) { return (n.kind == GridKind.MOTOR ? n.st("mrep") : n.st("rep")) / 1000.0; }

    /** relative ageing rate at a hot spot (IEC 60076-7, non-thermally-upgraded paper) */
    public static double ageing(double hotSpot) { return Math.pow(2, (hotSpot - 98) / 6.0); }

    /** IEC 60255-149 thermal overload: seconds (game minutes x 60 / 72 shown in game minutes) to trip from a preload, -1 = never */
    public static double tripMinutes(double iPu, double preloadPu, double tauMin, double k) {
        double i2 = iPu * iPu, ip2 = preloadPu * preloadPu, kk = k * k;
        if (i2 <= kk) return -1;
        return tauMin * Math.log((i2 - ip2) / (i2 - kk));
    }

    /** load factor K of a converter / motor this second (real amps where the network has a level, FE figures otherwise) */
    static double loadK(GridData d, GridData.Node n, java.util.Map<Integer, GridData.Net> nets) {
        if (n.kind == GridKind.MOTOR) { int r = n.st("mva"); return r > 0 ? n.loadVA / r : 0; }
        Elec.Level lv = Elec.Level.byName(n.level, null);
        if (lv != null && n.amps > 0 && n.kva > 0) {
            double va = lv.phases == 3 ? Math.sqrt(3) * lv.volts * n.amps : lv.volts * n.amps;
            return va / (n.kva * 1000.0);
        }
        GridData.Net net = nets.get(n.net);
        if (net == null) return 0;
        return net.lastSecond / 20.0 / ((double) GridData.RATING * Math.max(1, net.transformers));
    }

    /** once a second after Thermal.tick: transformers' top oil + hot spot, motors' replicas, alarms (GridTicker) */
    public static void models(World w, GridData d) {
        if (!com.dogpound.railmap.asset.AssetConfig.thermalModel) return;
        java.util.Map<Integer, GridData.Net> nets = new java.util.HashMap<>();
        for (GridData.Net net : d.nets()) nets.put(net.id, net);
        for (java.util.Map.Entry<Long, GridData.Node> e : d.nodes().entrySet()) {
            GridData.Node n = e.getValue();
            if (n.kind == GridKind.TRANSFORMER || n.kind == GridKind.TRACTION) transformer(w, d, e.getKey(), n, loadK(d, n, nets));
            else if (n.kind == GridKind.MOTOR) motor(w, d, e.getKey(), n, loadK(d, n, nets));
        }
    }

    private static void transformer(World w, GridData d, long pos, GridData.Node n, double k) {
        boolean energised = !n.burnt && !n.hot && k > 0.0001 || n.st("toil") > 0;
        if (!energised && !n.st.containsKey("toil")) return;
        boolean fans = !n.fanFail && (n.cool == 2 || (n.cool == 1 && n.st("hs") > (n.ambient + 60) * 10));
        double[] p = fans ? ONAF : ONAN;
        double foul = fouling(n);
        double dto = n.st("toil") / 10.0, dth = n.st("hsg") / 10.0;
        double dtoU = p[0] * (1 + 0.35 * foul) * Math.pow((1 + p[1] * k * k) / (1 + p[1]), p[2]);
        double dthU = p[4] * Math.pow(k, p[3]);
        dto += (dtoU - dto) * Math.min(1, DT_MIN / p[5]);
        dth += (dthU - dth) * Math.min(1, DT_MIN / p[6]);
        double hs = n.ambient + dto + dth;
        n.st.put("toil", (int) Math.round(dto * 10));
        n.st.put("hsg", (int) Math.round(dth * 10));
        n.st.put("hs", (int) Math.round(hs * 10));
        // radiator fouling: dust and leaves build up while the fans pull air through (about a year of running to 100 %)
        if (fans && w.rand.nextInt(400) == 0) n.st.put("foul", Math.min(1000, n.st("foul") + 1));
        // insulation loss of life: aged minutes at the reference hot spot
        double v = ageing(hs);
        int lolWas = n.st("lol");
        n.st.put("lol", (int) Math.min(Integer.MAX_VALUE, lolWas + Math.round(v * DT_MIN)));
        if (com.dogpound.railmap.asset.AssetConfig.thermalTrips && v > 1) {
            n.insul = (float) Math.max(0, n.insul - v * DT_MIN / (1440.0 * 60) / Math.max(0.01, com.dogpound.railmap.asset.AssetConfig.ageingRate) * 0.5);
        }
        // the thermal replica on the load current (time constant = the oil's)
        double rep = n.st("rep") / 1000.0;
        rep += (k * k / (1.1 * 1.1) - rep) * Math.min(1, DT_MIN / p[5]);
        n.st.put("rep", (int) Math.round(Math.max(0, rep) * 1000));
        BlockPos bp = BlockPos.fromLong(pos);
        String at = bp.getX() + " " + bp.getY() + " " + bp.getZ();
        com.dogpound.railmap.alarm.AlarmManager am = com.dogpound.railmap.alarm.AlarmManager.get(w);
        if (hs >= 140 || rep >= 1) {
            Protection.standing(w, "hs:" + pos, com.dogpound.railmap.alarm.AlarmManager.Cat.POWER, com.dogpound.railmap.alarm.AlarmManager.Sev.MAJOR,
                    String.format(Locale.ROOT, "TRANSFORMER %s HOT-SPOT %.0f °C (top oil %.0f °C, load %.0f%%, replica %.0f%%) - TRIP LEVEL%s", at, hs, topOilC(n), k * 100, rep * 100,
                            com.dogpound.railmap.asset.AssetConfig.thermalTrips ? "" : " (49 trips off in config)"), bp);
            if (com.dogpound.railmap.asset.AssetConfig.thermalTrips && !n.hot) {
                n.hot = true;
                n.heat = Math.max(n.heat, 100);
                d.touch();
                GridTicker.alarm(w, pos, String.format(Locale.ROOT, "§c⚡ 49 THERMAL OVERLOAD tripped the transformer at %s: hot spot %.0f °C, replica %.0f%%", at, hs, rep * 100));
                History.log(w, bp, "TRIP", String.format(Locale.ROOT, "49 thermal overload: hot spot %.0f °C, top oil %.0f °C, load %.0f%%", hs, topOilC(n), k * 100));
            }
        } else if (hs >= 120) {
            Protection.standing(w, "hs:" + pos, com.dogpound.railmap.alarm.AlarmManager.Cat.POWER, com.dogpound.railmap.alarm.AlarmManager.Sev.MINOR,
                    String.format(Locale.ROOT, "TRANSFORMER %s HOT-SPOT ALARM %.0f °C (top oil %.0f °C, load %.0f%%%s) - ageing %.0fx normal", at, hs, topOilC(n), k * 100,
                            n.fanFail ? ", FANS FAILED" : foul > 0.3 ? String.format(Locale.ROOT, ", radiator %.0f%% fouled", foul * 100) : "", v), bp);
        } else if (hs < 110) am.clear("hs:" + pos);
        if (lolWas / 60 / 24 != n.st("lol") / 60 / 24) History.log(w, bp, "TEMP", String.format(Locale.ROOT, "insulation loss of life now %.0f aged hours (hot spot %.0f °C)", agedHours(n), hs));
    }

    private static void motor(World w, GridData d, long pos, GridData.Node n, double k) {
        double rep = n.st("mrep") / 1000.0;
        double tau = 25;                                                     // motor heating time constant, game minutes
        rep += (k * k / (1.05 * 1.05) - rep) * Math.min(1, DT_MIN / (k > 0.01 ? tau : tau * 3));   // it cools slower standing (no fan)
        n.st.put("mrep", (int) Math.round(Math.max(0, rep) * 1000));
        BlockPos bp = BlockPos.fromLong(pos);
        if (rep >= 1) {
            Protection.standing(w, "m49:" + pos, com.dogpound.railmap.alarm.AlarmManager.Cat.MACHINE, com.dogpound.railmap.alarm.AlarmManager.Sev.MINOR,
                    String.format(Locale.ROOT, "MOTOR %d %d %d THERMAL OVERLOAD (49): replica %.0f%% at %.0f%% current", bp.getX(), bp.getY(), bp.getZ(), rep * 100, k * 100), bp);
            if (com.dogpound.railmap.asset.AssetConfig.thermalTrips) n.st.put("t49", 1);
        } else if (rep < 0.9) com.dogpound.railmap.alarm.AlarmManager.get(w).clear("m49:" + pos);
    }

    /** the console / imager / study line for a transformer or motor */
    public static String model(GridData.Node n) {
        if (n.kind == GridKind.MOTOR) {
            double r = replica(n);
            return String.format(Locale.ROOT, "49 replica %.0f%%", r * 100);
        }
        if (!n.st.containsKey("hs")) return "thermal model: no data yet";
        double hs = hotSpotC(n), rep = replica(n);
        return String.format(Locale.ROOT, "top oil %.0f °C · hot spot %.0f °C · ageing %.2fx · loss of life %.0f h · radiator %.0f%% fouled · 49 replica %.0f%%",
                topOilC(n), hs, ageing(hs), agedHours(n), fouling(n) * 100, rep * 100);
    }
}
