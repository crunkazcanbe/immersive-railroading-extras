package com.dogpound.railmap.asset;

import com.dogpound.railmap.depot.ItemSpare;

/**
 * What kinds of infrastructure the asset register knows (her ChatGPT list #2). Each has the unit its use is counted
 * in, its service life in those units (at ageingRate 1), how often it must be inspected, which inspection tool looks at
 * it, the latent defect it grows, that defect's critical size and the spare its repair takes.
 */
public enum AssetKind {
    //        prefix  label                 use unit          life    insp days  tool                         defect                         unit  crit   spare
    TRACK("TRK", "Track piece", "MGT", 2.5, 7, Ndt.Tool.ULTRASONIC, "rail head crack (RCF / transverse)", "mm", 25, ItemSpare.Spare.RAIL),
    POINTS("PM", "Point machine", "throws", 3000, 7, Ndt.Tool.ULTRASONIC, "switch rail / drive rod crack", "mm", 20, ItemSpare.Spare.POINT_MOTOR),
    SIGNAL("SIG", "Signal", "lamp h", 1440, 14, Ndt.Tool.THERMAL, "failing LED string / hot lamp driver", "°C", 45, ItemSpare.Spare.SIGNAL_MODULE),
    TRANSFORMER("TX", "Transformer", "aged h", 1440, 14, Ndt.Tool.THERMAL, "hot bushing / tap-changer joint", "°C", 40, ItemSpare.Spare.BUSHING),
    BREAKER("CB", "Circuit breaker", "ops", 2000, 14, Ndt.Tool.THERMAL, "hot main contact / terminal", "°C", 35, ItemSpare.Spare.BREAKER_KIT),
    MOTOR("MOT", "Electric motor", "run h", 1440, 14, Ndt.Tool.INSULATION, "winding insulation weakness", "%", 60, ItemSpare.Spare.MOTOR_KIT),
    INSULATOR("INS", "Insulator", "wet days", 90, 21, Ndt.Tool.INSULATION, "surface tracking / cracked shed", "%", 60, ItemSpare.Spare.INSULATOR),
    PLATFORM("PLT", "Platform", "calls", 6000, 28, Ndt.Tool.STRUCTURAL, "loose coping / edge spalling", "mm", 30, ItemSpare.Spare.STRUCTURE_KIT),
    BRIDGE("BRG", "Bridge / viaduct", "k axles", 60, 14, Ndt.Tool.STRUCTURAL, "fatigue crack at a weld / bearing", "mm", 3, ItemSpare.Spare.STRUCTURE_KIT),
    TUNNEL("TUN", "Tunnel", "k axles", 80, 14, Ndt.Tool.STRUCTURAL, "portal / invert crack", "mm", 3, ItemSpare.Spare.STRUCTURE_KIT),
    LINING("LIN", "Tunnel lining", "wet days", 120, 28, Ndt.Tool.STRUCTURAL, "lining spall / delamination", "mm", 40, ItemSpare.Spare.STRUCTURE_KIT),
    STRUCTURE("STR", "Culvert / wall / cutting", "k axles", 80, 28, Ndt.Tool.STRUCTURAL, "cracking / bulging", "mm", 5, ItemSpare.Spare.STRUCTURE_KIT),
    // night lighting: station / yard / tunnel luminaires (light.ILamp) - a growing driver fault flickers the lamp, past critical it fails
    LUMINAIRE("LUM", "Luminaire", "lamp h", 2400, 28, Ndt.Tool.THERMAL, "failing LED driver / hot lamp holder", "°C", 45, ItemSpare.Spare.SIGNAL_MODULE);

    public final String prefix, label, unit, defect, defectUnit;
    public final double life, crit;
    public final int inspectDays;
    public final Ndt.Tool tool;
    public final ItemSpare.Spare spare;

    AssetKind(String prefix, String label, String unit, double life, int inspectDays, Ndt.Tool tool, String defect, String defectUnit, double crit, ItemSpare.Spare spare) {
        this.prefix = prefix; this.label = label; this.unit = unit; this.life = life; this.inspectDays = inspectDays; this.tool = tool;
        this.defect = defect; this.defectUnit = defectUnit; this.crit = crit; this.spare = spare;
    }

    public static AssetKind of(String s) {
        for (AssetKind k : values()) if (k.name().equalsIgnoreCase(s) || k.prefix.equalsIgnoreCase(s)) return k;
        return null;
    }

    /** does this kind live on the electrical grid (insulation, thermal, commissioning tests through the grid) */
    public boolean electrical() { return this == TRANSFORMER || this == BREAKER || this == MOTOR || this == INSULATOR; }
}
