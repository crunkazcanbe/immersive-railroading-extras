package com.dogpound.railmap.grid;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.safety.FaultLab;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The power commissioning test bench (her 48-next list #19), built on the Fault Lab: while a lab console is in TEST
 * MODE it can put faults into the protection chain of a grid IED or breaker, and they act through the real protection
 * code (Protection.iedTime / tick / stuck):
 * <ul>
 *   <li>CT ratio error (sensor) - the relay sees half the current: slow or no trip;</li>
 *   <li>VT fuse failure (sensor) - the relay sees no voltage: 27 undervoltage misoperates;</li>
 *   <li>trip circuit open (wiring) - the relay operates but nothing reaches the trip coil: backup must clear it,
 *   the trip-circuit supervision (TCS) alarms;</li>
 *   <li>breaker mechanism stuck - the breaker does not open: 50BF;</li>
 *   <li>wrong relay setting - pick-up three times too high.</li>
 * </ul>
 * The SECONDARY INJECTION TEST compares, point by point, what the relay should do (its settings and curve) with what
 * it actually does through that chain (CT, VT, relay, trip wiring, breaker) within IEC 60255 tolerances (time ±5 % or
 * 40 ms, ratio ±1 %, voltage ±2 %) and issues a certificate: a healthy chain passes, any of the faults above fails.
 */
public final class Bench {
    private Bench() {}

    static double ct(long ied) { return FaultLab.benchAny() && FaultLab.bench(FaultLab.Kind.CT_ERROR, ied) ? 0.5 : 1; }
    static boolean vtFail(long ied) { return FaultLab.benchAny() && FaultLab.bench(FaultLab.Kind.VT_FAIL, ied); }
    static double setting(long ied) { return benchSetting(ied) * com.dogpound.railmap.infra.RelayCal.livePickup(ied); }   // + calibration drift on the live protection (infra #40, harsh switch)
    private static double benchSetting(long ied) { return FaultLab.benchAny() && FaultLab.bench(FaultLab.Kind.RELAY_SETTING, ied) ? 3 : 1; }

    /** the trip circuit from this IED to its breaker(s) is open (at the IED or at the breaker's coil) */
    static boolean wiringOpen(GridData d, long ied) {
        if (!FaultLab.benchAny()) return false;
        if (FaultLab.bench(FaultLab.Kind.TRIP_WIRING, ied)) return true;
        for (long b : Protection.tripsOf(d, ied)) if (FaultLab.bench(FaultLab.Kind.TRIP_WIRING, b)) return true;
        return false;
    }

    static boolean stuck(World w, GridData.Node b) {
        if (!FaultLab.benchAny()) return false;
        GridData d = GridData.get(w);
        for (long p : FaultLab.benchAt(FaultLab.Kind.BREAKER_STUCK)) if (d.nodes().get(p) == b) return true;
        return false;
    }

    /** trip-circuit supervision: an IED watches its trip circuit continuously (Protection.tick) */
    static void supervise(World w, GridData d, long ied) {
        String key = "tcs:" + ied;
        boolean open = wiringOpen(d, ied);
        if (open) Protection.standing(w, key, AlarmManager.Cat.POWER, AlarmManager.Sev.MAJOR, "TRIP CIRCUIT FAIL (TCS) at IED " + Protection.at(ied) + " - the breaker cannot be tripped", BlockPos.fromLong(ied));
        else if (FaultLab.benchAny() || w.getTotalWorldTime() % 200 == 0) AlarmManager.get(w).clear(key);
    }

    // ---- the Relay Test Set (infra #40) ----------------------------------------------------------------------------------
    /** {set A, curve, TMS, I>> multiple, I>> delay s} of an IED and the breakers it trips (empty when none) */
    public static double[] settings(World w, BlockPos ied) {
        GridData d = GridData.get(w);
        GridData.Node r = d.node(ied);
        if (r == null || r.kind != GridKind.IED) return null;
        List<Long> bs = Protection.tripsOf(d, ied.toLong());
        if (bs.isEmpty()) return null;
        GridData.Node b = d.nodes().get(bs.get(0));
        GridData.Net net = Protection.netOf(d, bs.get(0));
        return new double[]{Protection.ratedAmps(b, net == null ? null : net.level) * r.get("is", 100) / 100.0, r.get("curve", 0), r.get("tms", 10) / 100.0, r.get("hi", 8), r.get("hid", 50) / 1000.0};
    }

    /** the IED has its 12 V aux supply */
    public static boolean alive(World w, BlockPos ied) { GridData.Node r = GridData.get(w).node(ied); return r != null && r.st("aux") != 0; }

    /** the operating time the IED's settings call for at this multiple of pick-up (seconds, +inf = no operation) */
    public static double expected(double[] s, double m) {
        if (m <= 1) return Double.POSITIVE_INFINITY;
        return s[3] > 0 && m >= s[3] ? s[4] : Protection.curve((int) s[1], m, s[2]);
    }

    /** the test set's injection reached the real trip circuit (test block LIVE): the breaker(s) trip */
    public static int liveTrip(World w, BlockPos ied, String who) {
        GridData d = GridData.get(w);
        double[] s = settings(w, ied);
        int n = 0;
        for (long b : Protection.tripsOf(d, ied.toLong())) {
            GridData.Node bn = d.nodes().get(b);
            if (bn == null || bn.breaker != 0) continue;
            Protection.trip(w, d, b, ied.toLong(), "51 relay test injection by " + who + " - test block was LIVE", s == null ? 0 : s[0] * 5);
            n++;
        }
        return n;
    }

    /** one secondary-injection test: {pass?, lines} */
    public static Object[] test(World w, BlockPos iedPos) {
        GridData d = GridData.get(w);
        GridData.Node r = d.node(iedPos);
        List<String> out = new ArrayList<>();
        if (r == null || r.kind != GridKind.IED) { out.add("Not a protection IED"); return new Object[]{false, out}; }
        long rp = iedPos.toLong();
        List<Long> bs = Protection.tripsOf(d, rp);
        if (bs.isEmpty()) { out.add("No breaker touching this IED - nothing to test"); return new Object[]{false, out}; }
        GridData.Node b = d.nodes().get(bs.get(0));
        GridData.Net net = Protection.netOf(d, bs.get(0));
        Elec.Level lv = net == null ? null : net.level;
        double isSet = Protection.ratedAmps(b, lv) * r.get("is", 100) / 100.0;
        int curve = r.get("curve", 0), hi = r.get("hi", 8);
        double tms = r.get("tms", 10) / 100.0, hid = r.get("hid", 50) / 1000.0;
        boolean pass = true;
        out.add(String.format(Locale.ROOT, "IED %s -> breaker %s · I> %s %s TMS %.2f · I>> %s", Protection.at(rp), Protection.at(bs.get(0)), Elec.si(isSet, "A"),
                Protection.CURVES[Math.max(0, Math.min(4, curve))], tms, hi == 0 ? "off" : hi + "x"));
        if (r.st("aux") == 0) { out.add("FAIL  aux supply: IED is DEAD (no 12 V) - nothing else can be tested"); return new Object[]{false, out}; }
        // 1. CT ratio (primary injection at 1.0 x I>)
        double ratio = ct(rp);
        boolean ok = Math.abs(ratio - 1) <= 0.01;
        pass &= ok;
        out.add(String.format(Locale.ROOT, "%s  CT ratio: injected %s, relay measured %s (expected 1.00, got %.2f, ±1%%)", ok ? "PASS" : "FAIL", Elec.si(isSet, "A"), Elec.si(isSet * ratio, "A"), ratio));
        // 2. VT
        boolean vt = !vtFail(rp);
        pass &= vt;
        out.add(String.format(Locale.ROOT, "%s  VT: injected 100%% V, relay measured %s (±2%%)", vt ? "PASS" : "FAIL", vt ? "100%" : "0% - VT FUSE BLOWN"));
        // 3. timing points
        double drift = com.dogpound.railmap.infra.RelayCal.pickupErr(w, rp), tdrift = com.dogpound.railmap.infra.RelayCal.timeErr(w, rp);   // infra #40: the measuring chain has drifted
        double isAct = isSet * benchSetting(rp) * (1 + drift);
        boolean wiring = wiringOpen(d, rp), stuck = false;
        for (long bp : bs) if (stuck(w, d.nodes().get(bp))) stuck = true;
        for (double m : new double[]{2, 5, 10}) {
            double i = isSet * m;
            double te = hi > 0 && m >= hi ? hid : Protection.curve(curve, m, tms);
            double im = i * ratio;
            double ta = hi > 0 && im >= hi * isAct ? hid : im > isAct ? Protection.curve(curve, im / isAct, tms) * (1 + tdrift) : Double.POSITIVE_INFINITY;
            String got;
            boolean pt;
            if (wiring && ta != Double.POSITIVE_INFINITY) { got = String.format(Locale.ROOT, "relay %.3f s but NO TRIP (trip circuit open)", ta); pt = false; }
            else if (ta == Double.POSITIVE_INFINITY) { got = "no operation"; pt = te == Double.POSITIVE_INFINITY; }
            else { got = String.format(Locale.ROOT, "%.3f s", ta); pt = Math.abs(ta - te) <= Math.max(0.04, te * 0.05); }
            pass &= pt;
            out.add(String.format(Locale.ROOT, "%s  %4.1fx (%s): expected %s, actual %s", pt ? "PASS" : "FAIL", m, Elec.si(i, "A"), te == Double.POSITIVE_INFINITY ? "no op" : String.format(Locale.ROOT, "%.3f s", te), got));
        }
        // 4. breaker: opening time on a trip command (a real open-close is not needed: timing from its mechanism state)
        double open = stuck ? Double.POSITIVE_INFINITY : Protection.BREAKER_S * (1 + Math.max(0, b.wear - 0.5));
        boolean bo = open <= Protection.BREAKER_S * 1.5;
        pass &= bo;
        out.add(String.format(Locale.ROOT, "%s  breaker opening: expected <= %.0f ms, actual %s (contacts %.0f%% worn)", bo ? "PASS" : "FAIL", Protection.BREAKER_S * 1500,
                open == Double.POSITIVE_INFINITY ? "DID NOT OPEN (mechanism stuck)" : String.format(Locale.ROOT, "%.0f ms", open * 1000), b.wear * 100));
        // 5. trip circuit supervision
        out.add(String.format(Locale.ROOT, "%s  trip circuit supervision: %s", wiring ? "FAIL" : "PASS", wiring ? "TRIP CIRCUIT OPEN" : "healthy"));
        pass &= !wiring;
        boolean cal = Math.abs(drift) <= 0.05 && Math.abs(tdrift) <= 0.05;
        out.add(String.format(Locale.ROOT, "%s  calibration: pick-up %+.1f%%, timing %+.1f%% (IEC 60255 ±5%%)", cal ? "PASS" : "FAIL", drift * 100, tdrift * 100));
        pass &= cal;
        out.add(pass ? "RESULT: PASS - protection chain CT -> relay -> trip wiring -> breaker verified" : "RESULT: FAIL - see the FAIL lines");
        History.log(w, iedPos, "TEST", "secondary injection " + (pass ? "PASSED" : "FAILED"));
        return new Object[]{pass, out};
    }
}
