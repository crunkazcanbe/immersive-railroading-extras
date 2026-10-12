package com.dogpound.railmap.depot;

import cam72cam.immersiverailroading.entity.LocomotiveDiesel;
import com.dogpound.railmap.server.WearData;

/**
 * Condition-based operating restrictions (her ChatGPT list, section 8: "don't make every worn component cause a
 * breakdown immediately - give each component a condition curve"). From a part's wear:
 * <pre>
 *   HEALTHY   under 50 %   normal operation
 *   DEGRADED  50 - 80 %    engine: 10 % less power; electrics: traction motors run hotter; brakes: 8 % weaker
 *   CRITICAL  80 - 100 %   warning + restriction: engine 30 % down and hot, electrics 20 % down, brakes 20 % weaker
 *                          and 80 km/h, wheels 70 km/h, hot bearings 60 km/h
 *   FAILED    100 % +      the part no longer works properly: engine 65 % down, electrics 75 % down, brakes 40 %
 *                          weaker and 30 km/h, wheels 30 km/h, bearings 20 km/h; a driverless train is failed in service
 * </pre>
 * Applied through the fork's own hooks: prideDerate (power), prideCooling (heat), prideBrakeFactor (brake force), and
 * the driver's throttle / brake for the speed limits. A player who responds to the warnings gets the chance to bring
 * it in before it fails.
 */
public final class Condition {
    private Condition() {}

    public static final int HEALTHY = 0, DEGRADED = 1, CRITICAL = 2, FAILED = 3;
    public static final String[] NAMES = {"HEALTHY", "DEGRADED", "CRITICAL", "FAILED"};
    public static final int W = WearData.Part.WHEELS.ordinal(), B = WearData.Part.BRAKES.ordinal(), E = WearData.Part.ENGINE.ordinal(),
            BR = WearData.Part.BEARINGS.ordinal(), EL = WearData.Part.ELECTRICAL.ordinal();

    public static int level(float wear) { return wear >= 100 ? FAILED : wear >= 80 ? CRITICAL : wear >= 50 ? DEGRADED : HEALTHY; }

    /** worked out once a second per vehicle (transient on the unit) */
    public static void work(FleetData.Unit u, WearData.Record r) {
        u.derateK = 1; u.coolK = 1; u.brakeK = 1; u.cap = Float.MAX_VALUE; u.capWhy = "";
        com.dogpound.railmap.asset.Design.apply(u);                          // a rebuild's power / cooling / brakes / speed (her 48-next #28)
        if (r == null || !DepotConfig.conditionEffects) return;
        int e = level(r.wear[E]), el = level(r.wear[EL]), b = level(r.wear[B]), w = level(r.wear[W]), br = level(r.wear[BR]);
        u.derateK *= new float[]{1f, 0.9f, 0.7f, 0.35f}[e];
        u.coolK *= new float[]{1f, 1f, 0.85f, 0.7f}[e];
        if (u.electric || !u.steam) {
            u.derateK *= new float[]{1f, 1f, 0.8f, 0.25f}[el];
            u.coolK *= new float[]{1f, 0.9f, 0.8f, 0.7f}[el];
        }
        u.brakeK *= new float[]{1f, 0.92f, 0.8f, 0.6f}[b];
        cap(u, b == CRITICAL ? DepotConfig.critBrakesKmh : b == FAILED ? DepotConfig.failBrakesKmh : 0, "brakes " + NAMES[b]);
        cap(u, w == CRITICAL ? DepotConfig.critWheelsKmh : w == FAILED ? DepotConfig.failWheelsKmh : 0, "wheels " + NAMES[w]);
        cap(u, br == CRITICAL ? DepotConfig.critBearingsKmh : br == FAILED ? DepotConfig.failBearingsKmh : 0, "hot axle bearings");
    }

    private static void cap(FleetData.Unit u, int kmh, String why) {
        if (kmh > 0 && kmh < u.cap) { u.cap = kmh; u.capWhy = why; }
    }

    /** the worst level of any part */
    public static int worst(WearData.Record r) {
        if (r == null) return HEALTHY;
        int l = 0;
        for (float f : r.wear) l = Math.max(l, level(f));
        return l;
    }

    /** a part whose failure stops a driverless train in service (engine, electrics, brakes, bearings), or -1 */
    public static int failedPart(WearData.Record r) {
        if (r == null || !DepotConfig.conditionEffects) return -1;
        for (int p : new int[]{E, EL, B, BR}) if (r.wear[p] >= 100) return p;
        return -1;
    }

    // ---- EngineSim hooks (it owns prideDerate / prideCooling while engine management runs) -----------------------------
    /** power factor for this locomotive from its condition (1 = no restriction) */
    public static float derate(LocomotiveDiesel l) {
        FleetData.Unit u = DepotTicker.live(l.getUUID());
        return u == null ? 1f : u.derateK;
    }

    /** cooling factor for this locomotive from its condition (1 = no change) */
    public static float cooling(LocomotiveDiesel l) {
        FleetData.Unit u = DepotTicker.live(l.getUUID());
        return u == null ? 1f : u.coolK;
    }
}
