package com.dogpound.railmap.grid;

import java.util.Locale;

/**
 * Real electricity for the railway grid (requested feature). Tables and formulas only; GridData / GridTicker
 * run the networks with them.
 *
 * Units: 1 FE/t = 2 kW (so a 2,000 FE/t train pulls 4 MW, an electric locomotive). One cable block = 1 m.
 */
public final class Elec {
    private Elec() {}

    /** watts per FE/t */
    public static final double W_PER_FE_T = 2000;

    // ---- voltage levels -----------------------------------------------------------------------------------------
    public enum Level {
        DC12("12 V DC", 12, false, 1, 0, Insulation.LV),
        DC24("24 V DC", 24, false, 1, 0, Insulation.LV),
        DC48("48 V DC", 48, false, 1, 0, Insulation.LV),
        AC120("120 V AC", 120, true, 1, 60, Insulation.LV),
        AC240("240 V AC", 240, true, 1, 60, Insulation.LV),
        AC208("208 V 3φ", 208, true, 3, 60, Insulation.LV),
        AC480("480 V 3φ", 480, true, 3, 60, Insulation.LV),
        MV4160("4.16 kV 3φ", 4_160, true, 3, 60, Insulation.MV),
        MV13800("13.8 kV 3φ", 13_800, true, 3, 60, Insulation.MV),
        HV69000("69 kV 3φ", 69_000, true, 3, 60, Insulation.HV),
        // railway traction
        DC600("600 V DC (tram)", 600, false, 1, 0, Insulation.LV),
        DC750("750 V DC (third rail)", 750, false, 1, 0, Insulation.LV),
        DC1500("1.5 kV DC (beam)", 1_500, false, 1, 0, Insulation.MV),
        DC3000("3 kV DC (mainline)", 3_000, false, 1, 0, Insulation.MV),
        AC25000("25 kV 1φ 50 Hz (overhead)", 25_000, true, 1, 50, Insulation.HV);

        public final String label;
        public final double volts;
        public final boolean ac;
        public final int phases;        // 1 or 3 (DC counted as 1)
        public final int hz;
        public final Insulation needs;  // the least insulation a cable on this level must have

        Level(String label, double volts, boolean ac, int phases, int hz, Insulation needs) {
            this.label = label; this.volts = volts; this.ac = ac; this.phases = phases; this.hz = hz; this.needs = needs;
        }

        public static Level dc(int volts) { return volts <= 600 ? DC600 : volts <= 750 ? DC750 : volts <= 1500 ? DC1500 : DC3000; }

        public static Level byName(String s, Level dflt) {
            for (Level l : values()) if (l.name().equals(s) || l.label.equals(s)) return l;
            return dflt;
        }

        /** current drawn by an apparent power S (VA) on this level */
        public double amps(double va) { return phases == 3 ? va / (Math.sqrt(3) * volts) : va / volts; }
    }

    // ---- conductors ---------------------------------------------------------------------------------------------
    public enum Material {
        COPPER("Copper", 0.0172, 0.00393, 1.0, 0xC87533),
        ALUMINIUM("Aluminium", 0.0282, 0.00403, 0.78, 0xB8BCC2),     // cheaper and lighter, but higher resistance
        SUPERCONDUCTOR("Superconductor (HTS)", 0, 0, 6.0, 0x40C8E0);  // zero resistance while cryo-cooled

        public final String label;
        public final double rho20;      // Ω·mm²/m at 20 °C
        public final double alpha;      // resistance rise per °C
        public final double ampacityK;  // current-carrying factor vs copper (superconductor: very high)
        public final int colour;

        Material(String label, double rho20, double alpha, double ampacityK, int colour) {
            this.label = label; this.rho20 = rho20; this.alpha = alpha; this.ampacityK = ampacityK; this.colour = colour;
        }
    }

    /** standard cross-sections, mm² */
    public static final double[] SIZES = {1, 2.5, 4, 6, 10, 16, 25, 50, 95, 150, 300};

    /** copper current rating in air (A), per size above — IEC 60364-5-52 style values, XLPE insulated */
    private static final double[] CU_AMPS = {19, 32, 42, 54, 75, 100, 127, 192, 290, 389, 600};

    public enum Insulation {
        LV("LV 1 kV PVC/XLPE", 1_000, 70),
        MV("MV 15 kV XLPE", 15_000, 90),
        HV("HV 72.5 kV XLPE", 72_500, 90);

        public final String label;
        public final double maxVolts;
        public final int maxTemp;       // °C the insulation can stand continuously

        Insulation(String label, double maxVolts, int maxTemp) { this.label = label; this.maxVolts = maxVolts; this.maxTemp = maxTemp; }
    }

    public enum Cores {
        SINGLE("Single core", 1, false),
        DC2("2-core DC (+/-)", 2, false),
        THREE_N_PE("3-phase + N + PE", 5, true),
        MOTOR("3-phase motor cable (3 + PE, screened)", 4, true),
        BUSBAR("Busbar (copper bar)", 3, true);

        public final String label;
        public final int count;
        public final boolean threePhase;

        Cores(String label, int count, boolean threePhase) { this.label = label; this.count = count; this.threePhase = threePhase; }
    }

    /** one kind of power cable: what a cable block (and its item) is made of */
    public static final class Spec {
        public final Material mat;
        public final double mm2;
        public final Cores cores;
        public final Insulation ins;

        public Spec(Material mat, double mm2, Cores cores, Insulation ins) { this.mat = mat; this.mm2 = mm2; this.cores = cores; this.ins = ins; }

        /** the legacy Feeder Cable: 95 mm² copper, 3-phase, MV */
        public static final Spec LEGACY = new Spec(Material.COPPER, 95, Cores.THREE_N_PE, Insulation.MV);

        /** resistance of one conductor, Ω per metre, at conductor temperature t °C */
        public double ohmsPerM(double t) {
            if (mat == Material.SUPERCONDUCTOR) return 0;
            double area = cores == Cores.BUSBAR ? mm2 * 4 : mm2;            // busbars: solid bar, ~4x the section
            return mat.rho20 * (1 + mat.alpha * (t - 20)) / area;
        }

        /** reactance of one conductor, Ω per metre (typical cable: ~0.08 mΩ/m; busbar lower) */
        public double reactancePerM() { return cores == Cores.BUSBAR ? 0.00004 : 0.00008; }

        /** continuous current rating, A */
        public double ampacity() {
            int i = 0;
            while (i < SIZES.length - 1 && SIZES[i] < mm2) i++;
            double base = cores == Cores.BUSBAR ? mm2 * 4 * 2.0 : CU_AMPS[i];   // busbar: ~2 A/mm² of bar
            return base * mat.ampacityK;
        }

        public String label() {
            return String.format(Locale.ROOT, "%s %s mm² %s, %s", mat.label, fmt(mm2), cores.label, ins.label);
        }

        public String key() { return mat.name() + ":" + mm2 + ":" + cores.name() + ":" + ins.name(); }

        public static Spec parse(String k) {
            try {
                String[] p = k.split(":");
                return new Spec(Material.valueOf(p[0]), Double.parseDouble(p[1]), Cores.valueOf(p[2]), Insulation.valueOf(p[3]));
            } catch (RuntimeException e) { return LEGACY; }
        }
    }

    // ---- formulas ---------------------------------------------------------------------------------------------

    /** reactive power Q (var) for real power P (W) at power factor pf */
    public static double reactive(double p, double pf) { return pf >= 1 ? 0 : p * Math.tan(Math.acos(Math.max(0.05, pf))); }

    /** I²R loss of a run carrying amps: conductors that carry current (3 for 3φ, 2 for 1φ / DC) */
    public static double lossW(Level l, double amps, double ohmsPerConductor) {
        return (l.phases == 3 ? 3 : 2) * amps * amps * ohmsPerConductor;
    }

    /** voltage drop along a run (V): ΔV = k·I·(R cosφ + X sinφ), k = √3 for 3φ, 2 for 1φ / DC */
    public static double dropV(Level l, double amps, double r, double x, double pf) {
        double sin = Math.sqrt(Math.max(0, 1 - pf * pf));
        return (l.phases == 3 ? Math.sqrt(3) : 2) * amps * (r * pf + (l.ac ? x * sin : 0));
    }

    /** steady conductor temperature at a load fraction (current / rating): heating goes with I² */
    public static double steadyTemp(double ambient, Insulation ins, double loadFraction) {
        return ambient + (ins.maxTemp - ambient) * loadFraction * loadFraction;
    }

    public static String si(double v, String unit) {
        double a = Math.abs(v);
        if (a >= 1e6) return String.format(Locale.ROOT, "%.2f M%s", v / 1e6, unit);
        if (a >= 1e3) return String.format(Locale.ROOT, "%.1f k%s", v / 1e3, unit);
        return String.format(Locale.ROOT, "%.0f %s", v, unit);
    }

    static String fmt(double mm2) { return mm2 == Math.floor(mm2) ? String.valueOf((int) mm2) : String.valueOf(mm2); }
}
