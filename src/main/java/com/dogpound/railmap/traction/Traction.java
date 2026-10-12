package com.dogpound.railmap.traction;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.entity.LocomotiveDiesel;
import com.dogpound.railmap.network.PacketCab;
import com.dogpound.railmap.server.RailProps;
import net.minecraft.world.World;

import java.util.Locale;

/**
 * Battery-electric (BEMU) and hydrogen fuel-cell traction (batch 5 #6), on top of IR Extras' electric traction
 * (server.Electric, which calls in here once a second). Both are electric locomotives to the rest of the mod.
 * <ul>
 *   <li><b>BEMU</b>: a real traction battery (kWh from its rated power). Under live wire / third rail it runs from the
 *   line and charges at {@code wireChargeKw} (the OHLE charging section); off the wire it runs on the battery, limited
 *   to 2C discharge; flat = no traction. Regenerative braking goes into the battery (1C) before the resistors.</li>
 *   <li><b>Hydrogen</b>: no pantograph. The fuel cell (a share of the rated power) turns hydrogen into electricity
 *   ({@code kwhPerKgH2}); a buffer battery covers acceleration peaks and takes the regen; the fuel cell recharges it.
 *   Empty tanks = only what is left in the buffer.</li>
 * </ul>
 * The state of charge is RailProps.battery (the same SoC the rest of the mod shows); hydrogen, energy counters and the
 * measured kWh/km that the range is worked out from are in TractionData. The cab display, the cab desk's SOC glass
 * (CAB_SOC / CAB_TRACT / CAB_H2 control positions) and the console's DEPOT > Traction page read it.
 */
public final class Traction {
    private Traction() {}

    public static final int NONE = 0, BEMU = 1, H2 = 2;
    public static final String[] KIND = {"-", "BATTERY", "HYDROGEN"};
    public static final String SOC = "CAB_SOC", TRACT = "CAB_TRACT", H2LVL = "CAB_H2";
    static final double J_PER_KWH = 3.6e6;

    /** which kind of traction this stock has (NONE for everything not tagged / rebuilt) */
    public static int kind(EntityRollingStock s) {
        if (!TractionConfig.enabled || !(s instanceof LocomotiveDiesel)) return NONE;
        try {
            World w = s.getWorld().internal;
            TractionData.Unit u = TractionData.get(w).peek(s.getUUID());
            if (u != null && u.forced >= 0) return u.forced;
            com.dogpound.railmap.depot.FleetData.Unit f = com.dogpound.railmap.depot.FleetData.get(w).peek(s.getUUID());
            if (f != null && f.design[3] == 4) return BEMU;
            if (f != null && f.design[3] == 5) return H2;
            String n = ((s.getDefinition() == null ? "" : s.getDefinition().name()) + " " + s.getDefinitionID() + " " + (s.tag == null ? "" : s.tag)).toLowerCase(Locale.ROOT);
            for (String k : TractionConfig.hydrogenNames) if (!k.isEmpty() && n.contains(k)) return H2;
            for (String k : TractionConfig.batteryNames) if (!k.isEmpty() && n.contains(k)) return BEMU;
        } catch (LinkageError | RuntimeException ignored) { }
        return NONE;
    }

    public static double ratedKw(Locomotive l) {
        try { return Math.max(100, l.getDefinition().getScriptedWatt(l.gauge, l) / 1000); } catch (RuntimeException e) { return 1000; }
    }

    /** the traction battery (BEMU) or buffer battery (hydrogen), kWh; 100 kWh for a plain electric's battery */
    public static double capKwh(Locomotive l, int kind) {
        double kw = ratedKw(l);
        return kind == BEMU ? clamp(kw * TractionConfig.kwhPerKw, 100, 5000) : kind == H2 ? clamp(kw * 0.12, 40, 800) : 100;
    }

    public static double h2CapKg(Locomotive l) { return clamp(ratedKw(l) * TractionConfig.h2KgPerKw, 30, 800); }
    public static double fcKw(Locomotive l) { return ratedKw(l) * TractionConfig.fuelCellShare; }
    /** working pressure of the hydrogen tanks for the gauges */
    public static double h2Bar(Locomotive l, TractionData.Unit u) { return 350 * Math.max(0, u.h2kg) / h2CapKg(l); }

    static double clamp(double v, double a, double b) { return Math.max(a, Math.min(b, v)); }

    public static TractionData.Unit unit(Locomotive l) {
        TractionData.Unit u = TractionData.get(l.getWorld().internal).of(l.getUUID());
        if (u.h2kg < 0) u.h2kg = kind(l) == H2 ? (float) h2CapKg(l) : 0;      // first seen: delivered with full tanks
        return u;
    }

    /** extra W a BEMU under live wire draws to charge its battery (Electric adds it to what it asks the line for) */
    public static double chargeW(Locomotive l, RailProps.Props pr) {
        return pr.battery < 0.995f ? TractionConfig.wireChargeKw * 1000.0 : 0;
    }

    /**
     * Once a second from Electric for a BEMU / hydrogen unit: where its power comes from. `frac` is the share of
     * `wantW` the line gave (-1 off the wire). Returns the traction limit (0..1) and sets the source / SoC.
     */
    public static float power(World mc, LocomotiveDiesel l, RailProps.Props pr, int kind, boolean onWire, double frac, double wantW, double chargeW, float throttle) {
        TractionData.Unit u = unit(l);
        double cap = capKwh(l, kind);
        double need = Math.max(0, wantW - chargeW);
        float limit;
        u.kwCharge = 0;
        u.fcKw = 0;
        if (kind == BEMU && onWire && frac > 0.05) {
            double line = frac * wantW;
            double toTraction = Math.min(line, need);
            double toBattery = Math.max(0, line - toTraction);
            double deficit = need - toTraction;
            double fromBat = pr.battery > 0 ? Math.min(deficit, cap * 2000) : 0;
            pr.battery = (float) clamp(pr.battery + (toBattery - fromBat) / 1000 / 3600 / cap, 0, 1);
            u.kwhCharged += toBattery / 1000 / 3600;
            u.kwCharge = (float) (toBattery / 1000);
            limit = need <= 1 ? 1f : (float) ((toTraction + fromBat) / need);
            pr.source = 1;
            pr.supplied = (float) Math.min(1, frac);
        } else if (kind == BEMU) {
            double out = pr.battery > 0 ? Math.min(need, cap * 2000) : 0;
            pr.battery = (float) clamp(pr.battery - out / 1000 / 3600 / cap, 0, 1);
            u.kwhOut += out / 1000 / 3600;
            limit = need <= 1 ? (pr.battery > 0 ? 1f : 0f) : (float) (out / need);
            pr.source = pr.battery > 0 ? (byte) 2 : (byte) 4;
            pr.supplied = 0;
        } else {                                                                  // hydrogen: fuel cell + buffer battery
            double fcMax = u.fcOn && u.h2kg > 0 ? fcKw(l) * 1000 : 0;
            double recharge = pr.battery < 0.9f ? fcMax * 0.3 : 0;
            double fc = Math.min(fcMax, need + recharge);
            double kg = fc / 1000 / 3600 / TractionConfig.kwhPerKgH2;
            if (kg > u.h2kg) { fc *= u.h2kg / kg; kg = u.h2kg; }
            u.h2kg = (float) Math.max(0, u.h2kg - kg);
            u.h2Used += kg;
            u.fcKw = (float) (fc / 1000);
            double surplus = fc - need;
            double fromBat = surplus < 0 && pr.battery > 0 ? Math.min(-surplus, cap * 3000) : 0;
            pr.battery = (float) clamp(pr.battery + (Math.max(0, surplus) - fromBat) / 1000 / 3600 / cap, 0, 1);
            u.kwhOut += (Math.min(fc, need) + fromBat) / 1000 / 3600;
            limit = need <= 1 ? (fcMax > 0 || pr.battery > 0 ? 1f : 0f) : (float) ((Math.min(fc, need) + fromBat) / need);
            pr.source = fcMax > 0 || pr.battery > 0 ? (byte) 2 : (byte) 4;
            pr.supplied = 0;
        }
        u.kwNow = (float) (need * Math.min(1, limit) / 1000);
        // measured consumption -> range
        double ms = l.getCurrentSpeed() == null ? 0 : Math.abs(l.getCurrentSpeed().metersPerSecond());
        double km = ms / 1000;
        u.km += km;
        if (km > 0.002) {
            double wh = (u.kwNow - u.kwRegen) / 3.6;                    // Wh this second
            double now = Math.max(50, wh / km);
            u.whPerKm = u.whPerKm <= 0 ? (float) now : (float) (u.whPerKm * 0.98 + now * 0.02);
        }
        double perKm = u.whPerKm > 0 ? u.whPerKm : ratedKw(l) * 3.5;
        double kwh = pr.battery * cap + (kind == H2 ? Math.max(0, u.h2kg) * TractionConfig.kwhPerKgH2 : 0);
        u.rangeKm = (float) (kwh * 1000 / perKm);
        u.kwRegen = 0;
        cabPositions(l, pr, u, kind);
        return (float) clamp(limit, 0, 1);
    }

    /** regenerative braking with nowhere on the line to go: into the battery (1C), true if it took any */
    public static boolean regen(World mc, LocomotiveDiesel l, RailProps.Props pr, double brakeW) {
        int kind = kind(l);
        if (kind == NONE || pr.battery >= 0.999f) return false;
        double cap = capKwh(l, kind);
        double took = Math.min(brakeW, cap * 1000);
        pr.battery = (float) clamp(pr.battery + took / 1000 / 3600 / cap, 0, 1);
        TractionData.Unit u = unit(l);
        u.kwhRegen += took / 1000 / 3600;
        u.kwRegen = (float) (took / 1000);
        pr.kwRegen = (int) Math.round(took / 1000);
        return took > 0;
    }

    /** a charger feeding `kw` into a standing unit: kW it takes (its charge curve: full rate to 80 %, tapering to 100 %) */
    public static double charge(Locomotive l, RailProps.Props pr, double kw) {
        int kind = kind(l);
        double cap = capKwh(l, kind);
        if (pr.battery >= 0.999f) return 0;
        double taper = pr.battery < 0.8f ? 1 : Math.max(0.05, (1 - pr.battery) / 0.2);
        double take = Math.min(kw, Math.min(cap * 1000 * taper, cap * 1000));   // 1C max
        pr.battery = (float) clamp(pr.battery + take / 3600 / cap, 0, 1);
        pr.source = 3;
        if (kind != NONE) { TractionData.Unit u = unit(l); u.kwhCharged += take / 3600; u.kwCharge = (float) take; }
        return take;
    }

    /** room for hydrogen, kg */
    public static double h2Room(Locomotive l) { return kind(l) != H2 ? 0 : Math.max(0, h2CapKg(l) - unit(l).h2kg); }
    public static void addH2(Locomotive l, double kg) { TractionData.Unit u = unit(l); u.h2kg = (float) Math.min(h2CapKg(l), u.h2kg + kg); TractionData.get(l.getWorld().internal).markDirty(); }

    private static void cabPositions(Locomotive l, RailProps.Props pr, TractionData.Unit u, int kind) {
        set(l, SOC, Math.round(pr.battery * 100) / 100f);
        set(l, TRACT, kind == BEMU ? 0.5f : kind == H2 ? 1f : 0f);
        if (kind == H2) set(l, H2LVL, (float) Math.round(Math.max(0, u.h2kg) / h2CapKg(l) * 100) / 100f);
    }

    private static void set(Locomotive l, String g, float v) { if (Math.abs(l.getControlPosition(g) - v) > 0.005f) l.setControlPosition(g, v); }

    // ---- the cab display lines --------------------------------------------------------------------------------------------
    public static void cab(Locomotive loco, PacketCab p) {
        int kind = kind(loco);
        if (kind == NONE) return;
        World w = loco.getWorld().internal;
        RailProps.Props pr = RailProps.get(w).peek(loco.getUUID());
        TractionData.Unit u = TractionData.get(w).peek(loco.getUUID());
        if (pr == null || u == null) return;
        double cap = capKwh(loco, kind);
        String rng = u.rangeKm >= 999 ? "999+" : String.format(Locale.ROOT, "%.0f", u.rangeKm);
        if (kind == BEMU) {
            p.eng1 = String.format(Locale.ROOT, "BATTERY SoC %d%% %.0f/%.0f kWh · range %s km · %.1f kWh/km", Math.round(pr.battery * 100), pr.battery * cap, cap, rng, (u.whPerKm > 0 ? u.whPerKm : ratedKw(loco) * 3.5) / 1000);
            p.eng2 = String.format(Locale.ROOT, "traction %.0f kW · regen %.0f kW · %s", u.kwNow, (float) pr.kwRegen,
                    pr.source == 1 ? (u.kwCharge > 1 ? String.format(Locale.ROOT, "WIRE - charging %.0f kW", u.kwCharge) : "WIRE") : pr.source == 3 ? String.format(Locale.ROOT, "CHARGER %.0f kW", u.kwCharge) : pr.source == 4 ? "BATTERY FLAT" : "ON BATTERY");
        } else {
            p.eng1 = String.format(Locale.ROOT, "H2 %.0f/%.0f kg %.0f bar · fuel cell %.0f/%.0f kW%s · buffer %d%%", Math.max(0, u.h2kg), h2CapKg(loco), h2Bar(loco, u), u.fcKw, fcKw(loco), u.fcOn ? "" : " OFF", Math.round(pr.battery * 100));
            p.eng2 = String.format(Locale.ROOT, "range %s km · %.2f kg/100 km · traction %.0f kW · regen %.0f kW", rng,
                    (u.whPerKm > 0 ? u.whPerKm : ratedKw(loco) * 3.5) / 1000 / TractionConfig.kwhPerKgH2 * 100, u.kwNow, (float) pr.kwRegen);
        }
    }

    /** console / command row */
    public static String status(Locomotive l) {
        int kind = kind(l);
        RailProps.Props pr = RailProps.get(l.getWorld().internal).of(l.getUUID());
        TractionData.Unit u = unit(l);
        return String.format(Locale.ROOT, "%s · SoC %d%% of %.0f kWh%s · range %.0f km · out %.0f / regen %.0f / charged %.0f kWh", KIND[kind], Math.round(pr.battery * 100), capKwh(l, kind),
                kind == H2 ? String.format(Locale.ROOT, " · H2 %.0f/%.0f kg", u.h2kg, h2CapKg(l)) : "", u.rangeKm, u.kwhOut, u.kwhRegen, u.kwhCharged);
    }
}
