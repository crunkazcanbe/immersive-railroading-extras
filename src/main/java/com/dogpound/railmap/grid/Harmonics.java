package com.dogpound.railmap.grid;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.ctl.CtlConfig;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Harmonics and electrical resonance (her ChatGPT "next 48" list #20), worked out per real AC network once a second,
 * right after PowerQuality has found the totals:
 * <ul>
 *   <li>sources: what each piece of power electronics really injects, harmonic by harmonic - a 6-pulse traction
 *       rectifier the classic 1/h family 5, 7, 11, 13, 17, 19, 23, 25 (~28 % total), a 12-pulse one only 11, 13, 23, 25,
 *       a UPS / battery charger's single-phase front end the triplens 3, 9, 15 plus 5 and 7, a PWM inverter a little low
 *       order and its switching sidebands at 23 / 25;</li>
 *   <li>the network: the source impedance (from the bus fault level, X/R 10) in parallel with every plain capacitor bank
 *       step and every tuned filter bank (L in series with C, Q 30), solved as complex impedances at each harmonic - so a
 *       capacitor bank really does tune the network into parallel resonance at h = sqrt(Ssc / Qc), and a filter really
 *       does pull its harmonic down (and adds its own parallel resonance just below its tuning, as real ones do);</li>
 *   <li>voltage distortion V_h = I_h x |Z(h)|, THD from the spectrum (this replaces PowerQuality's estimate), the
 *       resonance peak and its amplification;</li>
 *   <li>filter bank duty: each filter's harmonic current (its own protection trips it on overload, capacitor ageing
 *       detunes it), and sensitive equipment on the network: 12 V chargers / UPS, protection IEDs and induction motors
 *       alarm past their tolerance, chargers trip if CtlConfig.harmonicTrips is on (factory drives already trip).</li>
 * </ul>
 * Spectrum Meters read it from {@link #spectrum}. No filter, no meter and no new config = the same THD alarms as before.
 */
public final class Harmonics {
    private Harmonics() {}

    public static final int H = 25;                                          // harmonics 2..25
    /** magnitude (fraction of fundamental current) by harmonic order */
    static final double[] SIX = new double[H + 1], TWELVE = new double[H + 1], SMPS = new double[H + 1], PWM = new double[H + 1];
    static {
        double[][] six = {{5, .20}, {7, .14}, {11, .09}, {13, .075}, {17, .055}, {19, .05}, {23, .04}, {25, .035}};
        for (double[] e : six) SIX[(int) e[0]] = e[1];
        for (int h : new int[]{11, 13, 23, 25}) TWELVE[h] = SIX[h];
        double[][] smps = {{3, .22}, {5, .14}, {7, .07}, {9, .04}, {11, .03}, {13, .02}, {15, .015}};
        for (double[] e : smps) SMPS[(int) e[0]] = e[1];
        double[][] pwm = {{5, .02}, {7, .015}, {11, .008}, {13, .006}, {23, .02}, {25, .015}};
        for (double[] e : pwm) PWM[(int) e[0]] = e[1];
    }

    /** a filter bank registered by its tile (refreshed every second while its chunk is loaded) */
    public static final class Filter {
        public long pos;
        public double kvar, tuned, cap = 1;                                   // nominal kvar, tuned order, capacitance left (1 = new)
        public boolean closed, highPass;
        public long seen;
        // last solve
        public int net = -1;
        public double ampsPct, thIh;                                          // harmonic current as % of its rating, worst harmonic order
        public double actualTune() { return tuned / Math.sqrt(Math.max(0.5, cap)); }
    }

    /** per dimension: filters by position */
    private static final Map<Integer, Map<Long, Filter>> FILTERS = new HashMap<>();
    /** per dimension: net id -> {V_h % for h = 0..25, [26] = THD %, [27] = resonance order, [28] = resonance amplification, [29] = I THD %} */
    private static final Map<Integer, Map<Integer, double[]>> SPECTRA = new HashMap<>();
    /** per dimension: net id -> current spectrum (% of fundamental) */
    private static final Map<Integer, Map<Integer, double[]>> ISPECTRA = new HashMap<>();
    /** sensitive-equipment timers: node pos -> seconds over tolerance */
    private static final Map<Long, Integer> OVER = new HashMap<>();

    public static Filter filter(World w, long pos) { return FILTERS.computeIfAbsent(w.provider.getDimension(), k -> new HashMap<>()).computeIfAbsent(pos, k -> { Filter f = new Filter(); f.pos = k; return f; }); }
    public static void forget(World w, long pos) { Map<Long, Filter> m = FILTERS.get(w.provider.getDimension()); if (m != null) m.remove(pos); }

    public static double[] spectrum(World w, int net) { Map<Integer, double[]> m = SPECTRA.get(w.provider.getDimension()); return m == null ? null : m.get(net); }
    public static double[] currents(World w, int net) { Map<Integer, double[]> m = ISPECTRA.get(w.provider.getDimension()); return m == null ? null : m.get(net); }
    public static Collection<Filter> filters(World w) { Map<Long, Filter> m = FILTERS.get(w.provider.getDimension()); return m == null ? Collections.emptyList() : m.values(); }

    // ---- complex numbers as double[2] -------------------------------------------------------------------------------
    private static double[] c(double re, double im) { return new double[]{re, im}; }
    private static double[] inv(double[] z) { double d = z[0] * z[0] + z[1] * z[1]; return d < 1e-18 ? c(1e9, 0) : c(z[0] / d, -z[1] / d); }
    private static double mag(double[] z) { return Math.hypot(z[0], z[1]); }

    /** |Z(h)| of the bus (per unit on the fault-level base): source || capacitor steps || filters */
    static double zBus(double h, double xs, double xc, List<Filter> fs, double sc) {
        double[] y = inv(c(xs * h / 10, xs * h));                               // source: X/R = 10
        if (xc > 0) { double[] yc = inv(c(xc / h / 60, -xc / h)); y[0] += yc[0]; y[1] += yc[1]; }
        for (Filter f : fs) { double[] yf = inv(zFilter(f, h, sc)); y[0] += yf[0]; y[1] += yf[1]; }
        return mag(inv(y));
    }

    /** a filter branch: C with its reactor tuned to the filter's (aged) order; a high-pass adds a damping resistor */
    static double[] zFilter(Filter f, double h, double sc) {
        double xc = sc / Math.max(1, f.kvar * 1000 * f.cap);                    // capacitor reactance at 50/60 Hz (pu)
        double xl = sc / Math.max(1, f.kvar * 1000) / (f.tuned * f.tuned);      // reactor sized for the nominal C
        double x = h * xl - xc / h;
        double r = xl * f.tuned / 30;                                           // quality factor 30
        if (f.highPass) { double rp = xl * f.tuned * 2; double[] zl = c(0, h * xl), zr = c(rp, 0); double[] ypar = inv(zl); double[] yr = inv(zr); ypar[0] += yr[0]; ypar[1] += yr[1]; double[] zp = inv(ypar); return c(zp[0] + r, zp[1] - xc / h); }
        return c(r, x);
    }

    /** called from PowerQuality.tick after the totals, before the analysers read them */
    static void tick(World w, GridData d) {
        if (!CtlConfig.harmonicSpectrum) return;
        int dim = w.provider.getDimension();
        long now = w.getTotalWorldTime();
        Map<Long, Filter> fm = FILTERS.computeIfAbsent(dim, k -> new HashMap<>());
        fm.values().removeIf(f -> now - f.seen > 80);                          // its chunk unloaded: the filter is not there
        Map<Integer, double[]> spectra = new HashMap<>(), ispectra = new HashMap<>();
        List<GridData.Net> nets = d.nets();
        Map<Integer, List<Filter>> byNet = new HashMap<>();
        for (Filter f : fm.values()) {
            f.net = -1;
            GridData.Net n = PowerQuality.measured(d, f.pos);
            if (n == null) continue;
            f.net = n.id;
            if (f.closed) byNet.computeIfAbsent(n.id, k -> new ArrayList<>()).add(f);
        }
        for (GridData.Net n : nets) {
            if (n.level == null || !n.level.ac || !n.problem.isEmpty()) continue;
            double sc = Math.max(1, n.faultVA);
            // ---- injected current spectrum, VA per harmonic (fraction of the source's fundamental VA)
            double[] ih = new double[H + 1];
            for (long cpos : n.outs) {
                GridData.Node cv = d.nodes().get(cpos);
                if (cv == null || !cv.passes() || cv.net < 0 || cv.net >= nets.size()) continue;
                GridData.Net sec = nets.get(cv.net);
                if (cv.kind == GridKind.RECTIFIER) { double[] m = cv.get("pulse", 6) == 12 ? TWELVE : SIX; for (int h = 2; h <= H; h++) ih[h] += m[h] * sec.sVA; }
                if (cv.kind == GridKind.UPS) for (int h = 2; h <= H; h++) ih[h] += SMPS[h] * cv.loadVA;
            }
            for (long cpos : n.ins) {
                GridData.Node cv = d.nodes().get(cpos);
                if (cv != null && cv.kind == GridKind.INVERTER && cv.passes()) for (int h = 2; h <= H; h++) ih[h] += PWM[h] * n.sVA;
            }
            // ---- network impedance: source, plain capacitor steps (as PowerQuality sizes them), filters
            double xs = 1;                                                     // per unit on the fault-level base
            double qc = n.steps > 0 ? n.steps * 0.25 * Math.max(1, ratingVA(d, n)) : 0;
            double xc = qc > 0 ? sc / qc : 0;
            List<Filter> fs = byNet.getOrDefault(n.id, Collections.emptyList());
            double[] v = new double[30];
            double sum = 0;
            for (int h = 2; h <= H; h++) {
                double z = zBus(h, xs, xc, fs, sc);
                v[h] = ih[h] / sc * z * 100;
                sum += v[h] * v[h];
            }
            v[26] = Math.sqrt(sum);
            // resonance: the |Z| peak between h = 2 and 25 and how much it amplifies over the bare source
            double peakH = 0, peakA = 0;
            if (xc > 0 || !fs.isEmpty())
                for (double h = 2; h <= H; h += 0.05) { double a = zBus(h, xs, xc, fs, sc) / (xs * h); if (a > peakA) { peakA = a; peakH = h; } }
            v[27] = peakH; v[28] = peakA;
            double[] ip = new double[H + 1];
            double isum = 0;
            for (int h = 2; h <= H; h++) { ip[h] = ih[h] / Math.max(1, n.sVA) * 100; isum += ip[h] * ip[h]; }
            v[29] = Math.sqrt(isum);
            n.thdV = v[26];
            if (peakA > 2.5) {
                long near = Math.round(peakH);
                boolean excited = near >= 2 && near <= H && ih[(int) near] > 0;
                n.resonance = String.format(Locale.ROOT, "PARALLEL RESONANCE at h%.1f, x%.1f amplification%s", peakH, peakA, excited ? " - EXCITED by the " + ordinal((int) near) + " harmonic" : "");
            } else n.resonance = "";
            // ---- each filter's harmonic duty: I_f(h) = V_h / |Z_f(h)|, against a rating of 35 % of its fundamental current
            for (Filter f : fs) {
                double rated = f.kvar * 1000 / sc, s2 = 0, worst = 0;
                int worstH = 0;
                for (int h = 2; h <= H; h++) {
                    double i = v[h] / 100 / Math.max(1e-6, mag(zFilter(f, h, sc)));
                    s2 += i * i;
                    if (i > worst) { worst = i; worstH = h; }
                }
                f.ampsPct = Math.sqrt(s2) / Math.max(1e-9, 0.35 * rated) * 100;
                f.thIh = worstH;
            }
            spectra.put(n.id, v);
            ispectra.put(n.id, ip);
            sensitive(w, d, n, v[26]);
        }
        SPECTRA.put(dim, spectra);
        ISPECTRA.put(dim, ispectra);
    }

    static String ordinal(int h) { return h + (h == 2 ? "nd" : h == 3 ? "rd" : h == 21 ? "st" : h == 22 ? "nd" : h == 23 ? "rd" : "th"); }

    private static double ratingVA(GridData d, GridData.Net n) {
        double kva = 0;
        for (long c : n.ins) { GridData.Node cv = d.nodes().get(c); if (cv != null) kva += cv.kva; }
        return kva > 0 ? kva * 1000 : n.sVA;
    }

    /** sensitive equipment on a distorted network: chargers overheat, IEDs mis-measure, motors run hot */
    private static void sensitive(World w, GridData d, GridData.Net n, double thd) {
        AlarmManager am = AlarmManager.get(w);
        for (long p : n.nodes) {
            GridData.Node nd = d.nodes().get(p);
            if (nd == null) continue;
            double tol = nd.kind == GridKind.UPS ? CtlConfig.thdUps : nd.kind == GridKind.IED ? CtlConfig.thdIed : nd.kind == GridKind.MOTOR ? CtlConfig.thdMotor : -1;
            if (tol < 0) continue;
            String key = "thd:" + p;
            if (thd <= tol) { OVER.remove(p); am.clear(key); continue; }
            int secs = OVER.merge(p, 1, Integer::sum);
            if (secs < 5) continue;
            BlockPos bp = BlockPos.fromLong(p);
            String what = nd.kind == GridKind.UPS ? "12 V charger rectifier overheating" : nd.kind == GridKind.IED ? "protection IED measuring distorted waveforms - risk of mal-operation" : "induction motor running hot (negative-sequence + eddy losses)";
            Protection.standing(w, key, AlarmManager.Cat.POWER, AlarmManager.Sev.MINOR, String.format(Locale.ROOT, "HARMONICS: %s on network #%d - THD %.1f%% over its %.0f%% tolerance (%s)", what, n.id, thd, tol, nd.kind.label), bp);
            if (nd.kind == GridKind.UPS && CtlConfig.harmonicTrips && secs >= 20 && nd.dcOn) {
                nd.dcOn = false;
                d.touch();
                History.log(w, bp, "TRIP", String.format(Locale.ROOT, "harmonic overheating: THD %.1f%% for %d s", thd, secs));
                GridTicker.alarm(w, p, String.format(Locale.ROOT, "§c⚡ 12 V charger at %d %d %d TRIPPED on harmonic overheating (THD %.1f%%) - on battery now", bp.getX(), bp.getY(), bp.getZ(), thd));
            }
        }
    }

    /** self-test: a 6-pulse rectifier with a capacitor bank resonating at h5 must amplify, a 5th filter must cut V5 */
    public static List<String> selfTest() {
        List<String> out = new ArrayList<>();
        double sc = 100e6, xs = 1;
        double qc = sc / 25;                                                    // sqrt(Ssc/Qc) = 5
        double zRes = zBus(5, xs, sc / qc, Collections.emptyList(), sc), zBare = zBus(5, xs, 0, Collections.emptyList(), sc);
        out.add((zRes > zBare * 3 ? "PASS" : "FAIL") + String.format(Locale.ROOT, " capacitor bank tuned to h5 amplifies x%.1f", zRes / zBare));
        Filter f = new Filter(); f.kvar = qc / 1000; f.tuned = 4.7; f.closed = true;
        double zF = zBus(5, xs, 0, Collections.singletonList(f), sc);
        out.add((zF < zBare ? "PASS" : "FAIL") + String.format(Locale.ROOT, " 4.7-tuned filter lowers |Z(5)| to %.0f%% of the bare source", zF / zBare * 100));
        f.cap = 0.85;
        out.add((f.actualTune() > 4.7 ? "PASS" : "FAIL") + String.format(Locale.ROOT, " capacitor ageing to 85%% detunes it to h%.2f", f.actualTune()));
        return out;
    }
}
