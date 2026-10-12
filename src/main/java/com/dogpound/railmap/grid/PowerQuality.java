package com.dogpound.railmap.grid;

import com.dogpound.railmap.alarm.AlarmManager;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Power quality (her ChatGPT list #39), worked out from what is really on each network once a second:
 *   unbalance  single-phase load (25 kV traction transformers, 240 / 120 V transformers) hanging on a 3-phase bus:
 *              VUF % ≈ S(1φ) / S(short-circuit) x 100 (the textbook estimate)
 *   harmonics  6-pulse rectifiers draw ~28 % 5th / 7th / 11th... current, UPS chargers ~30 %, inverters ~4 %;
 *              voltage THD = harmonic VA / fault level, amplified when a capacitor bank tunes the network into
 *              parallel resonance near the 5th or 7th (h = sqrt(Ssc / Qc))
 *   DC ripple  a 6-pulse rectifier's 4.2 % ripple, smoothed by the capacitor bank
 *   frequency, voltage, power factor   from the solve
 * The Power Quality Analyzer shows it, captures sags / swells / interruptions, and alarms past its limits.
 */
public final class PowerQuality {
    private PowerQuality() {}

    static void tick(World w, GridData d) {
        List<GridData.Net> nets = d.nets();
        for (GridData.Net n : nets) {
            n.vuf = n.thdV = n.thdI = n.ripple = 0;
            n.resonance = "";
            if (n.level == null || !n.problem.isEmpty()) continue;
            double sc = Math.max(1, n.faultVA);
            if (n.level.ac) {
                double s1 = 0, sh = 0;
                for (long c : n.outs) {
                    GridData.Node cv = d.nodes().get(c);
                    if (cv == null || !cv.passes() || cv.net < 0 || cv.net >= nets.size()) continue;
                    GridData.Net sec = nets.get(cv.net);
                    if (sec.level != null && sec.level.ac && sec.level.phases == 1) s1 += sec.sVA;
                    if (cv.kind == GridKind.RECTIFIER) sh += 0.28 * sec.sVA;
                    if (cv.kind == GridKind.UPS) sh += 0.30 * cv.loadVA;
                }
                for (long c : n.ins) { GridData.Node cv = d.nodes().get(c); if (cv != null && cv.kind == GridKind.INVERTER) sh += 0.04 * n.sVA; }
                n.vuf = n.level.phases == 3 ? s1 / sc * 100 : 0;
                n.thdI = sh / Math.max(1, n.sVA) * 100;
                double amp = 1;
                if (n.steps > 0) {
                    double qc = n.steps * 0.25 * Math.max(1, ratingVA(d, n));
                    double hr = Math.sqrt(sc / qc);
                    if (Math.abs(hr - 5) < 0.6 || Math.abs(hr - 7) < 0.6) { amp = 4; n.resonance = String.format(Locale.ROOT, "PARALLEL RESONANCE near h%d (capacitors + %s fault level)", Math.round(hr), Elec.si(sc, "VA")); }
                    else if (hr < 9) amp = 1.6;
                }
                n.thdV = sh / sc * 100 * 6 * amp;                          // ~6 % harmonic impedance at the 5th..7th
            } else {
                boolean rect = false;
                for (long c : n.ins) { GridData.Node cv = d.nodes().get(c); if (cv != null && cv.kind == GridKind.RECTIFIER) rect = true; }
                if (rect) n.ripple = n.capacitor ? 4.2 * Math.max(0.05, 1 - n.steps / 2.0) : 4.2;
            }
        }
        Harmonics.tick(w, d);                                               // the spectrum, filters, resonance (next-48 #20)
        for (Map.Entry<Long, GridData.Node> e : d.nodes().entrySet()) if (e.getValue().kind == GridKind.PQA) analyse(w, d, e.getKey(), e.getValue());
    }

    private static double ratingVA(GridData d, GridData.Net n) {
        double kva = 0;
        for (long c : n.ins) { GridData.Node cv = d.nodes().get(c); if (cv != null) kva += cv.kva; }
        return kva > 0 ? kva * 1000 : n.sVA;
    }

    /** the network a PQ analyser / ATS measures: the first piece of real plant it touches */
    public static GridData.Net measured(GridData d, long pos) {
        BlockPos p = BlockPos.fromLong(pos);
        for (EnumFacing f : EnumFacing.values()) {
            GridData.Node n = d.node(p.offset(f));
            if (n != null && !n.kind.control()) return Protection.netOf(d, p.offset(f).toLong());
        }
        return null;
    }

    /** one analyser: readings, limits, event capture, alarms */
    private static void analyse(World w, GridData d, long pos, GridData.Node a) {
        GridData.Net n = measured(d, pos);
        BlockPos bp = BlockPos.fromLong(pos);
        boolean live = n != null && n.level != null && n.problem.isEmpty();
        double v = live ? n.puSource : 0, hz = live && n.level.ac ? n.level.hz * n.hzPu : 0;
        a.st.put("net", n == null ? -1 : n.id);
        a.st.put("v", (int) Math.round(v * 1000));
        a.st.put("hz", (int) Math.round(hz * 100));
        a.st.put("vuf", live ? (int) Math.round(n.vuf * 100) : 0);
        a.st.put("thdv", live ? (int) Math.round(n.thdV * 100) : 0);
        a.st.put("thdi", live ? (int) Math.round(n.thdI * 10) : 0);
        a.st.put("rip", live ? (int) Math.round(n.ripple * 100) : 0);
        a.st.put("pf", live ? (int) Math.round(n.pf * 100) : 0);
        a.st.put("kw", live ? (int) Math.round(n.pW / 1000) : 0);
        a.st.put("dc", live && !n.level.ac ? 1 : 0);
        if (n == null || n.level == null) return;
        // limits (settings): voltage band %, frequency deviation tenths of Hz, VUF tenths %, THD tenths %, PF %
        int vlo = a.get("vlo", 90), vhi = a.get("vhi", 110), df = a.get("df", 5), vufMax = a.get("vuf", 20), thdMax = a.get("thd", 50), pfMin = a.get("pf", 90);
        String k = "pq:" + pos + ":";
        AlarmManager am = AlarmManager.get(w);
        // event capture: interruption < 10 %, sag < limit, swell > limit (counted on the transition)
        int state = !live || v < 0.1 ? 3 : v * 100 < vlo ? 1 : v * 100 > vhi ? 2 : 0;
        int was = a.st("ev");
        if (state != was) {
            a.st.put("ev", state);
            if (state != 0) {
                String[] names = {"", "SAG", "SWELL", "INTERRUPTION"};
                a.st.merge(state == 1 ? "nsag" : state == 2 ? "nswl" : "nint", 1, Integer::sum);
                History.log(w, bp, "PQ", String.format(Locale.ROOT, "%s captured: %.1f%% of %s", names[state], v * 100, n.level.label));
                a.st.put("lev", state);
            }
        }
        boolean bv = live && (v * 100 < vlo || v * 100 > vhi), bf = live && n.level.ac && Math.abs(hz - n.level.hz) * 10 > df,
                bu = live && n.vuf * 10 > vufMax, bh = live && (n.level.ac ? n.thdV * 10 > thdMax : n.ripple * 10 > thdMax), bp2 = live && n.level.ac && n.pW > 1000 && n.pf * 100 < pfMin;
        a.st.put("av", bv ? 1 : 0); a.st.put("af", bf ? 1 : 0); a.st.put("au", bu ? 1 : 0); a.st.put("ah", bh ? 1 : 0); a.st.put("ap", bp2 ? 1 : 0);
        String where = String.format(Locale.ROOT, " (PQ analyser %d %d %d, network #%d %s)", bp.getX(), bp.getY(), bp.getZ(), n.id, n.level.label);
        flag(w, am, k + "v", bv, AlarmManager.Sev.MAJOR, String.format(Locale.ROOT, "Voltage %.1f%% outside %d-%d%%", v * 100, vlo, vhi) + where, bp);
        flag(w, am, k + "f", bf, AlarmManager.Sev.MAJOR, String.format(Locale.ROOT, "Frequency %.2f Hz off by more than %.1f Hz", hz, df / 10.0) + where, bp);
        flag(w, am, k + "u", bu, AlarmManager.Sev.MINOR, String.format(Locale.ROOT, "Voltage unbalance %.2f%% > %.1f%% (single-phase traction load on a 3-phase bus)", n.vuf, vufMax / 10.0) + where, bp);
        flag(w, am, k + "h", bh, AlarmManager.Sev.MINOR, (n.level.ac ? String.format(Locale.ROOT, "Voltage THD %.2f%% > %.1f%%%s", n.thdV, thdMax / 10.0, n.resonance.isEmpty() ? "" : " - " + n.resonance)
                : String.format(Locale.ROOT, "DC ripple %.2f%% > %.1f%% (add capacitor steps)", n.ripple, thdMax / 10.0)) + where, bp);
        flag(w, am, k + "p", bp2, AlarmManager.Sev.INFO, String.format(Locale.ROOT, "Power factor %.2f below %.2f - switch in capacitor steps", n.pf, pfMin / 100.0) + where, bp);
    }

    private static void flag(World w, AlarmManager am, String key, boolean on, AlarmManager.Sev sev, String text, BlockPos at) {
        if (on) Protection.standing(w, key, AlarmManager.Cat.POWER, sev, "PQ: " + text, at);
        else am.clear(key);
    }
}
