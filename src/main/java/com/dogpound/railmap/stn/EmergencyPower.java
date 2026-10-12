package com.dogpound.railmap.stn;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Locale;

/**
 * Station emergency power (ChatGPT batch 3 #30): when a station machine's grid supply (depot.Mains) fails, a Station
 * Emergency Power panel within reach whose supply has also failed feeds it from its emergency bus - the UPS battery at
 * once, the standby generator once it has run up - by priority, up to the panel's SHED LEVEL:
 * <pre>
 *   1 LIFE SAFETY  lighting, PIS, PA, help points, security, crowd / door / zone panels, emergency power itself
 *   2 LIFTS        lifts (step-free access, people trapped)
 *   3 ESCALATORS   escalators, gates, ticketing
 *   4 ALL STATION  heaters, toilets, vending, kiosks, offices, boards
 *   5 (never)      depot machines - wheel lathe, wash plant, fuel point... are not on a station's emergency bus
 * </pre>
 * Machines above the shed level stay dead ("load shed by station emergency power").
 */
public final class EmergencyPower {
    private EmergencyPower() {}

    public static final String[] LEVELS = {"", "LIFE SAFETY", "+LIFTS", "+ESCALATORS", "ALL STATION"};

    public static int priority(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (n.contains("wheel lathe") || n.contains("bogie") || n.contains("wash") || n.contains("fuel") || n.contains("brake test") || n.contains("servicing") || n.contains("depot")
                || n.contains("weighbridge") || n.contains("washout") || n.contains("reefer")) return 5;
        if (n.contains("lamp") || n.contains("light") || n.contains("pis") || n.contains("pa ") || n.contains("help") || n.contains("security") || n.contains("crowd")
                || n.contains("door") || n.contains("zone") || n.contains("emergency") || n.contains("interchange") || n.contains("disruption")) return 1;
        if (n.contains("lift")) return 2;
        if (n.contains("escalator") || n.contains("gate") || n.contains("ticket")) return 3;
        return 4;
    }

    /**
     * depot.Mains asks when a machine has no grid supply: the label of the emergency supply now feeding it (and the
     * load taken from that panel's battery / generator), or null.
     */
    public static String feed(World w, BlockPos p, String name, long load) {
        if (!StnConfig.emergencyFeed || w == null || w.isRemote) return null;
        int prio = priority(name);
        if (prio >= 5) return null;
        String shed = null;
        for (TileStationPower t : StnUtil.near(w, TileStationPower.class, p, StnConfig.emergencyRadius)) {
            if (t.getPos().equals(p)) return null;                            // the panel's own incoming supply
            String r = t.supply(prio, Math.max(0, load), name);
            if (r != null && !r.startsWith("!")) return r;
            if (r != null) shed = r.substring(1);
        }
        if (shed != null) { lastShed = shed; }
        return null;
    }

    /** the last refusal (console / tooltips) */
    public static String lastShed = "";
}
