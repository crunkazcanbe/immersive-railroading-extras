package com.dogpound.railmap.grid;

import com.dogpound.railmap.alarm.AlarmManager;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * The protection engine (her ChatGPT list #34, #35, #36): one set of rules used both live and by the Protection Study,
 * so what the study says is exactly what happens.
 *
 * A fault (short circuit, earth fault, internal transformer fault) is "seen" by every closed breaker between it and
 * the sources - across transformers too, scaled by their ratio. Each breaker's relay works out its operating time for
 * the current it sees: an IED by its real settings (50/51 IEC curves, 50N/51N, 87T), an old plain Protection Relay by
 * fixed time grading (0.05 s + 0.3 s per breaker further from the fault, so old grids behave exactly as before).
 * The fastest relay trips its breaker. Badly set relays mean an upstream breaker trips first and blacks out far more
 * than it needs to. A worn breaker can fail to open: breaker-failure protection (50BF) then trips the next one up.
 * Overload (51 below fault level), under / over voltage (27/59), frequency (81) and DC earth leakage (64) are timed
 * once a second by {@link #tick}.
 */
public final class Protection {
    private Protection() {}

    public static final String[] CURVES = {"IEC Standard Inverse", "IEC Very Inverse", "IEC Extremely Inverse", "IEC Long-Time Inverse", "Definite Time"};
    static final double BREAKER_S = 0.05;              // breaker opening time (3 cycles)
    static final double INF = Double.POSITIVE_INFINITY;

    /** world changes found during a solve wait until it has finished (see GridSolver) */
    static final List<Runnable> AFTER = new ArrayList<>();

    static void flush() { List<Runnable> l = new ArrayList<>(AFTER); AFTER.clear(); for (Runnable r : l) r.run(); }

    /** operating time of an IEC 60255 curve at multiple m of the setting, time multiplier tms */
    public static double curve(int c, double m, double tms) {
        if (m <= 1.0) return INF;
        switch (c) {
            case 1: return tms * 13.5 / (m - 1);
            case 2: return tms * 80 / (m * m - 1);
            case 3: return tms * 120 / (m - 1);
            case 4: return tms * 10;                                       // DT: TMS 0.10 = 1.0 s
            default: return tms * 0.14 / (Math.pow(m, 0.02) - 1);
        }
    }

    /** a breaker's rated current from its trip rating (FE/t → W) on its voltage level */
    static double ratedAmps(GridData.Node b, Elec.Level lv) {
        if (lv == null) return Math.max(1, b.rating);
        return Math.max(1, lv.amps(b.rating * Elec.W_PER_FE_T / 0.9));
    }

    /** one breaker on a fault's path and what its relay does about it */
    public static final class Dev {
        public long breaker, relay = Long.MIN_VALUE;
        public double amps, earthAmps, time = INF, pickup;
        public String func = "", note = "", relayKind = "";
        public int hop;
        public Elec.Level level;
        public boolean bf;                  // relay has 50BF
    }

    static GridData.Net netOf(GridData d, long p) {
        GridData.Node n = d.nodes().get(p);
        List<GridData.Net> nets = d.nets();
        return n == null || n.net < 0 || n.net >= nets.size() ? null : nets.get(n.net);
    }

    /** the transformer / rectifier an IED guards (touching it) */
    static long zoneOf(GridData d, long ied) {
        BlockPos p = BlockPos.fromLong(ied);
        for (EnumFacing f : EnumFacing.values()) {
            GridData.Node n = d.node(p.offset(f));
            if (n != null && n.sided() && (n.kind == GridKind.TRANSFORMER || n.kind == GridKind.TRACTION || n.kind == GridKind.RECTIFIER)) return p.offset(f).toLong();
        }
        return Long.MIN_VALUE;
    }

    /** breakers an IED trips (touching it) */
    static List<Long> tripsOf(GridData d, long ied) {
        List<Long> l = new ArrayList<>();
        BlockPos p = BlockPos.fromLong(ied);
        for (EnumFacing f : EnumFacing.values()) { GridData.Node n = d.node(p.offset(f)); if (n != null && n.kind == GridKind.BREAKER) l.add(p.offset(f).toLong()); }
        return l;
    }

    /** walk from the fault to the sources (through converters into the networks feeding them) */
    public static List<Dev> path(GridData d, GridData.Net net, long at, double iFault, boolean earth) {
        List<Dev> out = new ArrayList<>();
        List<GridData.Net> nets = d.nets();
        long cur = at;
        GridData.Net cn = net;
        double scale = 1;
        boolean faultNet = true;
        int hop = 0;
        Set<Long> seen = new HashSet<>();
        for (int guard = 0; guard < 100_000 && seen.add(cur); guard++) {
            GridData.Node nd = d.nodes().get(cur);
            if (nd == null) break;
            if (nd.kind == GridKind.BREAKER && nd.breaker == 0) {
                Dev v = device(d, cur, nd, cn, iFault * scale, faultNet && earth ? iFault : 0, at, hop, earth);
                out.add(v);
                hop++;
            }
            if (nd.par == Long.MIN_VALUE) break;
            if (nd.par == cur) {                                               // the root of this network
                if (!nd.sided() || nd.upNet < 0 || nd.upNet >= nets.size()) break;
                long touch = BlockPos.fromLong(cur).offset(nd.back()).toLong();
                if (!d.nodes().containsKey(touch)) break;
                GridData.Net up = nets.get(nd.upNet);
                if (cn != null && cn.level != null && up.level != null) scale *= cn.level.volts / up.level.volts;
                cn = up;
                faultNet = false;
                cur = touch;
                continue;
            }
            cur = nd.par;
        }
        return out;
    }

    /** what the relay(s) on breaker b do for this fault */
    private static Dev device(GridData d, long b, GridData.Node bn, GridData.Net net, double amps, double earthAmps, long faultAt, int hop, boolean earth) {
        Dev v = new Dev();
        v.breaker = b; v.amps = amps; v.earthAmps = earthAmps; v.hop = hop;
        v.level = net == null ? null : net.level;
        BlockPos bp = BlockPos.fromLong(b);
        String why = "no relay";
        for (EnumFacing f : EnumFacing.values()) {
            long rp = bp.offset(f).toLong();
            GridData.Node r = d.nodes().get(rp);
            if (r == null || !r.kind.relay()) continue;
            double t; String func; String note = "";
            if (r.kind == GridKind.RELAY) {
                double pick = ratedAmps(bn, v.level) * r.relaySet / 100.0;
                if (earth && amps < pick) { t = INF; func = ""; note = String.format(Locale.ROOT, "legacy relay: %s earth fault is below its %s pick-up", Elec.si(amps, "A"), Elec.si(pick, "A")); }
                else { t = 0.05 + 0.3 * hop; func = "50/51 (time-graded)"; }
                if (t < v.time || v.relay == Long.MIN_VALUE) { v.time = t; v.func = func; v.relay = rp; v.relayKind = "Protection Relay"; v.pickup = pick; v.note = note; }
                continue;
            }
            Object[] o = iedTime(d, rp, r, bn, v.level, amps, earthAmps, faultAt);
            t = (Double) o[0]; func = (String) o[1]; note = (String) o[2];
            if (t < v.time || v.relay == Long.MIN_VALUE) { v.time = t; v.func = func; v.relay = rp; v.relayKind = "IED"; v.pickup = (Double) o[3]; v.note = note; v.bf = r.get("bf", 1) == 1; }
        }
        if (v.relay == Long.MIN_VALUE) {                                   // built before relays were needed: its own direct-acting trip coil (old behaviour)
            v.time = 0.05 + 0.3 * hop; v.func = "direct-acting trip (no relay fitted)"; v.note = why;
        }
        return v;
    }

    /** {time s, function, note, phase pick-up A} of an IED for a fault */
    static Object[] iedTime(GridData d, long rp, GridData.Node r, GridData.Node bn, Elec.Level lv, double amps, double earthAmps, long faultAt) {
        double is = ratedAmps(bn, lv) * r.get("is", 100) / 100.0 * Bench.setting(rp);   // test bench faults (her 48-next #19): wrong setting, CT error, open trip circuit
        amps *= Bench.ct(rp); earthAmps *= Bench.ct(rp);
        if (Bench.wiringOpen(d, rp)) return new Object[]{INF, "", "TRIP CIRCUIT OPEN (test bench) - the relay operates but nothing reaches the trip coil", is};
        if (r.st("aux") == 0) return new Object[]{INF, "", "IED DEAD - no 12 V aux supply, it cannot trip", is};
        if (r.st("test") == 1) return new Object[]{INF, "", "IED in TEST mode - trip outputs blocked", is};
        double t = INF; String func = "";
        int hi = r.get("hi", 8);
        if (faultAt == zoneOf(d, rp) && r.get("dif", 1) == 1) { t = 0.03; func = "87T differential"; }
        if (hi > 0 && amps >= hi * is) { double th = r.get("hid", 50) / 1000.0; if (th < t) { t = th; func = "50 I>> high-set"; } }
        else if (amps > is) { double tc = curve(r.get("curve", 0), amps / is, r.get("tms", 10) / 100.0); if (tc < t) { t = tc; func = "51 I> " + CURVES[Math.max(0, Math.min(4, r.get("curve", 0)))]; } }
        if (earthAmps > 0 && r.get("efon", 1) == 1) {
            double ins = is * r.get("efs", 20) / 100.0;
            if (earthAmps > ins) { double te = r.get("efd", 500) / 1000.0; if (te < t) { t = te; func = "50N/51N IN> earth fault"; } }
        }
        String note = t == INF ? String.format(Locale.ROOT, "below pick-up (I> %s%s)", Elec.si(is, "A"), earthAmps > 0 ? ", IN> " + Elec.si(is * r.get("efs", 20) / 100.0, "A") : "") : "";
        return new Object[]{t, func, note, is};
    }

    /**
     * A fault of `amps` at `at` on `net`: the fastest relay trips. Returns true when something tripped. Used by the
     * solver (short circuits, flashovers, burn-outs) and by earth-fault checking.
     */
    public static boolean clear(World w, GridData d, GridData.Net net, long at, double amps, boolean earth, String what) {
        List<Dev> devs = path(d, net, at, amps, earth);
        List<Dev> order = new ArrayList<>(devs);
        order.sort(Comparator.comparingDouble(v -> v.time));
        BlockPos fp = BlockPos.fromLong(at);
        Set<Long> tripped = new HashSet<>();
        for (int i = 0; i < order.size(); i++) {
            Dev v = order.get(i);
            if (v.time == INF) break;
            if (tripped.contains(v.breaker)) continue;
            GridData.Node bn = d.nodes().get(v.breaker);
            if (bn == null || bn.breaker != 0) continue;
            BlockPos bp = BlockPos.fromLong(v.breaker);
            String reason = String.format(Locale.ROOT, "%s %s (%s, %.2f s)", v.func, Elec.si(v.amps, "A"), what, v.time + BREAKER_S);
            // 1) a fault bigger than the breaker can interrupt: it fails violently, the next one up must clear it
            if (v.amps > bn.kA * 1000.0) {
                alarm(w, v.breaker, String.format(Locale.ROOT, "§4💥 BREAKER FAILED at %d %d %d: %s fault current, it is only rated %d kA - fit a 40 / 63 kA breaker",
                        bp.getX(), bp.getY(), bp.getZ(), Elec.si(v.amps, "A"), bn.kA));
                History.log(w, bp, "FAIL", String.format(Locale.ROOT, "DESTROYED interrupting %s (rated %d kA)", Elec.si(v.amps, "A"), bn.kA));
                bn.breaker = 2;
                bn.lastTrip = "destroyed by a " + Elec.si(v.amps, "A") + " fault";
                tripped.add(v.breaker);
                AFTER.add(() -> {
                    if (w.isBlockLoaded(bp)) {
                        w.setBlockToAir(bp);
                        w.newExplosion(null, bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5, 2.0f, true, false);
                    }
                    d.remove(bp);
                });
                continue;                                                      // backup protection keeps going
            }
            // 2) worn out: the mechanism sticks (breaker failure)
            if (stuck(w, bn)) {
                History.log(w, bp, "FAIL", "FAILED TO OPEN on a trip command (" + v.func + ") - contacts worn " + Math.round(bn.wear * 100) + "%");
                alarm(w, v.breaker, String.format(Locale.ROOT, "§4⚠ BREAKER FAILURE at %d %d %d: it did not open (worn %d%%)%s", bp.getX(), bp.getY(), bp.getZ(),
                        Math.round(bn.wear * 100), v.bf ? " - 50BF trips the breaker upstream" : " - backup protection must clear it"));
                tripped.add(v.breaker);
                if (v.bf) {
                    int idx = devs.indexOf(v);
                    for (int k = idx + 1; k < devs.size(); k++) {
                        Dev u = devs.get(k);
                        GridData.Node un = d.nodes().get(u.breaker);
                        if (un == null || un.breaker != 0) continue;
                        trip(w, d, u.breaker, v.relay, "50BF breaker failure (" + bp.getX() + " " + bp.getY() + " " + bp.getZ() + " stuck)", u.amps);
                        History.log(w, fp, "FAULT", what + " - cleared by 50BF at " + at(u.breaker));
                        return true;
                    }
                }
                continue;
            }
            trip(w, d, v.breaker, v.relay, reason, v.amps);
            if (fp.toLong() != v.breaker) History.log(w, fp, "FAULT", String.format(Locale.ROOT, "%s - %s, cleared by breaker %s", what, Elec.si(amps, "A"), at(v.breaker)));
            alarm(w, v.breaker, String.format(Locale.ROOT, "§c⚡ %s: %s at %s tripped breaker %d %d %d - %s cleared in %.2f s", cap(what), v.func, at(v.relay), bp.getX(), bp.getY(), bp.getZ(),
                    Elec.si(v.amps, "A"), v.time + BREAKER_S));
            if (v.hop > 0 && i == 0) {
                Dev near = devs.get(0);
                alarm(w, v.breaker, String.format(Locale.ROOT, "§e⚠ MIS-COORDINATION: breaker %s nearer the fault should have cleared it first (it needed %s) - check relay settings with the Protection Study",
                        at(near.breaker), near.time == INF ? "more current than it saw" : String.format(Locale.ROOT, "%.2f s", near.time)));
            }
            return true;
        }
        return false;
    }

    /** a worn breaker may not open: from 100 % wear the chance climbs to 50 % at 200 % */
    static boolean stuck(World w, GridData.Node b) {
        if (Bench.stuck(w, b)) return true;                                     // test bench: mechanism stuck
        if (b.wear < 1) return false;
        return w.rand.nextDouble() < Math.min(0.9, (b.wear - 1) * 0.5);
    }

    /** trip one breaker by protection: the breaker, its log book, the relay's targets */
    static void trip(World w, GridData d, long breaker, long relay, String reason, double amps) {
        BlockPos bp = BlockPos.fromLong(breaker);
        GridData.Node bn = d.nodes().get(breaker);
        if (bn == null) return;
        if (w.isBlockLoaded(bp) && w.getTileEntity(bp) instanceof TileGrid t) t.setBreaker((byte) 2, "TRIPPED: " + reason);
        else { bn.breaker = 2; bn.lastTrip = reason; bn.ops++; History.log(w, bp, "TRIP", reason); d.touch(); }
        bn.wear += (float) (Math.pow(amps / Math.max(1, bn.kA * 1000.0), 2) / 30.0);   // ~30 full-rated interruptions wear the contacts out
        if (bn.wear >= 1) standing(w, "wear:" + breaker, AlarmManager.Cat.POWER, AlarmManager.Sev.MINOR,
                String.format(Locale.ROOT, "Breaker %d %d %d MAINTENANCE DUE: contacts %d%% worn - overhaul it (screwdriver, under a permit)", bp.getX(), bp.getY(), bp.getZ(), Math.round(bn.wear * 100)), bp);
        GridData.Node r = relay == Long.MIN_VALUE ? null : d.nodes().get(relay);
        if (r != null && r.kind == GridKind.IED) {
            String code = reason.startsWith("87") ? "l87" : reason.startsWith("50N") ? "l50n" : reason.startsWith("50BF") ? "lbf" : reason.startsWith("50 ") ? "l50"
                    : reason.startsWith("51") ? "l51" : reason.startsWith("27") || reason.startsWith("59") ? "l27" : reason.startsWith("81") ? "l81" : reason.startsWith("64") ? "l64" : "l51";
            r.st.put(code, 1); r.st.put("ltrip", 1);
            r.st.put("rec", (int) Math.min(Integer.MAX_VALUE, Math.round(amps)));
            History.log(w, relay, "TRIP", reason + " -> breaker " + bp.getX() + " " + bp.getY() + " " + bp.getZ());
        }
        d.markDirty();
    }

    /** raise a standing alarm once (raise() again on an active one would count it every second) */
    public static void standing(World w, String key, AlarmManager.Cat cat, AlarmManager.Sev sev, String text, BlockPos at) {
        AlarmManager m = AlarmManager.get(w);
        for (AlarmManager.Alarm a : m.all()) if (a.key.equals(key) && a.active) return;
        m.raise(w, key, cat, sev, text, at, true);
    }

    static void alarm(World w, long at, String msg) { GridTicker.alarm(w, at, msg); }

    static String at(long p) { if (p == Long.MIN_VALUE) return "-"; BlockPos b = BlockPos.fromLong(p); return b.getX() + " " + b.getY() + " " + b.getZ(); }

    private static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    // ---- once a second: the IEDs' timed functions -------------------------------------------------------------------
    static void tick(World w, GridData d) {
        List<GridData.Net> nets = d.nets();
        for (Map.Entry<Long, GridData.Node> e : new ArrayList<>(d.nodes().entrySet())) {
            GridData.Node r = e.getValue();
            if (r.kind != GridKind.IED) continue;
            long rp = e.getKey();
            List<Long> bs = tripsOf(d, rp);
            GridData.Node b = null; long bpos = Long.MIN_VALUE;
            for (long p : bs) { GridData.Node x = d.nodes().get(p); if (b == null || x.breaker == 0 && b.breaker != 0) { b = x; bpos = p; } }
            if (b == null) { r.st.put("ia", 0); r.st.put("ok", 0); continue; }
            GridData.Net net = b.net >= 0 && b.net < nets.size() ? nets.get(b.net) : null;
            Elec.Level lv = net == null ? null : net.level;
            boolean closed = b.breaker == 0, live = closed && net != null && net.problem.isEmpty();
            if (r.get("aux", 0) == 1) r.st.put("aux", live ? 1 : 0);              // CT-powered: alive only with load current flowing
            double is = ratedAmps(b, lv) * r.get("is", 100) / 100.0 * Bench.setting(rp);
            double ia = closed ? b.amps * Bench.ct(rp) : 0;
            double v = live ? (Bench.vtFail(rp) ? 0 : b.pu > 0 ? b.pu : net.puSource) : 0;
            Bench.supervise(w, d, rp);
            double hz = live && lv != null && lv.ac ? lv.hz * net.hzPu : 0;
            r.st.put("ok", 1);
            r.st.put("ia", (int) Math.round(ia));
            r.st.put("is", (int) Math.round(is));
            r.st.put("v", (int) Math.round(v * 1000));
            r.st.put("hz", (int) Math.round(hz * 100));
            r.st.put("pick", ia > is ? 1 : 0);
            r.st.put("ie", closed && net != null && net.efAmps > 0 ? (int) Math.round(net.efAmps) : 0);
            // 87T: through-load is stable; only tap-changer mismatch and magnetising current show up
            long z = zoneOf(d, rp);
            if (z != Long.MIN_VALUE) {
                GridData.Node t = d.nodes().get(z);
                GridData.Net sec = t.net >= 0 && t.net < nets.size() ? nets.get(t.net) : null;
                double load = sec == null ? 0 : sec.sVA / Math.max(1, t.kva * 1000.0);
                double idf = Math.abs(t.tap - 9) * 0.0125 * load + (sec != null && sec.problem.isEmpty() ? 0.01 : 0);
                r.st.put("idf", (int) Math.round(idf * 1000));
                boolean tLive = t.upNet >= 0 && t.upNet < nets.size() && nets.get(t.upNet).problem.isEmpty() && t.passes();
                if (tLive && r.st("tl") == 0 && r.get("dif", 1) == 1) {               // just energised: magnetising inrush ~6 pu, rich in 2nd harmonic
                    if (r.get("h2", 1) == 0 && r.st("aux") == 1 && r.st("test") == 0)
                        for (long p : bs) { GridData.Node x = d.nodes().get(p); if (x.breaker == 0) trip(w, d, p, rp, "87T differential on magnetising INRUSH (2nd-harmonic restraint is OFF)", ia * 6); }
                    else History.log(w, rp, "87T", "transformer energised: inrush ~6 pu, 2nd harmonic 35% - restrained");
                }
                r.st.put("tl", tLive ? 1 : 0);
            }
            if (r.st("aux") == 0 || r.st("test") == 1 || !closed) { r.st.remove("p51"); r.st.remove("c27"); r.st.remove("c59"); r.st.remove("c81"); continue; }
            String fire = null;
            // 51: the inverse-time disc - integrates while over pick-up, resets when the current drops
            int hi = r.get("hi", 8);
            if (hi > 0 && ia >= hi * is) fire = String.format(Locale.ROOT, "50 I>> HIGH-SET %s (%dx I> = %s)", Elec.si(ia, "A"), hi, Elec.si(hi * is, "A"));
            else if (ia > is) {
                double t = curve(r.get("curve", 0), ia / is, r.get("tms", 10) / 100.0);
                int p = r.st("p51") + (int) Math.round(1000 / Math.max(0.05, t));
                r.st.put("p51", p);
                if (p >= 1000) fire = String.format(Locale.ROOT, "51 I> OVERCURRENT %s (I> %s, %.1fx, %s)", Elec.si(ia, "A"), Elec.si(is, "A"), ia / is, CURVES[Math.max(0, Math.min(4, r.get("curve", 0)))]);
            } else r.st.put("p51", Math.max(0, r.st("p51") - 250));
            // 27 / 59
            int uv = r.get("uv", 0), ov = r.get("ov", 115);
            if (fire == null && uv > 0 && v * 100 < uv) { int c = r.st("c27") + 1000; r.st.put("c27", c); if (c >= r.get("uvd", 3000)) fire = String.format(Locale.ROOT, "27 U< UNDERVOLTAGE %.0f%% (set %d%%)", v * 100, uv); }
            else r.st.put("c27", 0);
            if (fire == null && ov > 0 && v * 100 > ov) { int c = r.st("c59") + 1000; r.st.put("c59", c); if (c >= r.get("ovd", 2000)) fire = String.format(Locale.ROOT, "59 U> OVERVOLTAGE %.0f%% (set %d%%)", v * 100, ov); }
            else r.st.put("c59", 0);
            // 81U / 81O: under-frequency load shedding / over-frequency
            int uf = r.get("uf", 0), of = r.get("of", 0);
            if (fire == null && hz > 0 && (uf > 0 && hz * 10 < uf || of > 0 && hz * 10 > of)) {
                int c = r.st("c81") + 1000; r.st.put("c81", c);
                if (c >= r.get("ufd", 1000)) fire = String.format(Locale.ROOT, "81%s FREQUENCY %.2f Hz (set %.1f Hz) - load shed", hz * 10 < uf ? "U UNDER" : "O OVER", hz, (hz * 10 < uf ? uf : of) / 10.0);
            } else r.st.put("c81", 0);
            // 64: DC earth leakage (first fault on a floating DC system)
            boolean leak = net != null && net.efAmps < 0;
            r.st.put("l64", leak ? 1 : r.st("l64"));
            if (fire == null && leak && r.get("dcef", 0) == 1) fire = "64 DC EARTH FAULT (insulation monitor)";
            if (fire != null && Bench.wiringOpen(d, rp)) {                     // the relay operated, the trip never got to the breaker
                History.log(w, rp, "FAIL", fire + " - TRIP CIRCUIT OPEN, the breaker got no trip command");
                fire = null;
            }
            if (fire != null) {
                for (long p : bs) {
                    GridData.Node x = d.nodes().get(p);
                    if (x.breaker != 0) continue;
                    if (stuck(w, x) && r.get("bf", 1) == 1) {
                        History.log(w, p, "FAIL", "FAILED TO OPEN - 50BF");
                        long up = upstreamBreaker(d, p);
                        if (up != Long.MIN_VALUE) trip(w, d, up, rp, "50BF breaker failure after " + fire, ia);
                        continue;
                    }
                    trip(w, d, p, rp, fire, ia);
                    alarm(w, p, String.format(Locale.ROOT, "§c⚡ IED %s: %s - tripped breaker %s", at(rp), fire, at(p)));
                }
                r.st.remove("p51"); r.st.put("c27", 0); r.st.put("c59", 0); r.st.put("c81", 0);
            }
        }
    }

    /** the nearest closed breaker feeding this one (for 50BF) */
    static long upstreamBreaker(GridData d, long b) {
        GridData.Node n = d.nodes().get(b);
        GridData.Net net = netOf(d, b);
        if (n == null || net == null) return Long.MIN_VALUE;
        for (Dev v : path(d, net, b, 0, false)) if (v.breaker != b) return v.breaker;
        return Long.MIN_VALUE;
    }

    // ---- the Protection Study (#36) ----------------------------------------------------------------------------------
    /** last study per dimension, for the console's PROTECT tab */
    public static final Map<Integer, List<String>> LAST = new HashMap<>();

    /** simulate a fault at `at`: who sees it, how fast each relay is, who trips first, what is wrong with the settings */
    public static List<String> study(World w, BlockPos at, boolean earth) {
        GridData d = GridData.get(w);
        List<String> out = new ArrayList<>();
        GridData.Node fn = d.node(at);
        GridData.Net net = d.netOf(at);
        if (fn == null || net == null) { out.add("§cNot a grid part"); return out; }
        if (net.level == null) { out.add("§cLegacy network (no voltage levels) - set its converters to Real connections first"); return out; }
        if (!net.problem.isEmpty() || fn.par == Long.MIN_VALUE) { out.add("§eThis point is dead (" + (net.problem.isEmpty() ? "not fed" : net.problem) + ") - a fault here draws no current. Study it energised."); return out; }
        double amps;
        String type;
        if (earth && net.level.ac) { int ner = Insulation.ner(d, net); amps = ner > 0 ? Math.min(ner, net.isc) : 0.9 * net.isc; type = ner > 0 ? "earth fault (resistance earthed, NER " + ner + " A)" : "earth fault (solidly earthed)"; }
        else if (earth) { out.add("§b§lPROTECTION STUDY§r§7 - earth fault on a DC system (" + net.level.label + ")"); out.add("§7DC traction / control systems float: a FIRST earth fault draws no fault current - only an insulation monitor (IED 64, UPS) sees it."); out.add("§7A second fault on the other pole is a short circuit: run the 3-phase / short-circuit study for that."); return out; }
        else { amps = net.isc; type = net.level.ac && net.level.phases == 3 ? "3-phase short circuit" : "short circuit"; }
        out.add(String.format(Locale.ROOT, "§b§lPROTECTION STUDY§r§7 - %s at %s %d %d %d on %s (network #%d)", type, fn.kind.label.toLowerCase(Locale.ROOT), at.getX(), at.getY(), at.getZ(), net.level.label, net.id));
        out.add(String.format(Locale.ROOT, "§7Fault current §f%s§7 · fault level §f%s", Elec.si(amps, "A"), Elec.si(net.faultVA, "VA")));
        List<Dev> devs = path(d, net, at.toLong(), amps, earth);
        if (devs.isEmpty()) { out.add("§c✖ No closed breaker between this point and the source - nothing can clear a fault here!"); LAST.put(w.provider.getDimension(), out); return out; }
        for (Dev v : devs) {
            GridData.Node bn = d.nodes().get(v.breaker);
            out.add(String.format(Locale.ROOT, "§7#%d breaker §f%s§7 (%s%s) sees §f%s%s§7 · %s → %s", v.hop + 1, at(v.breaker), v.level == null ? "?" : v.level.label,
                    bn.kA > 0 ? ", " + bn.kA + " kA" : "", Elec.si(v.amps, "A"), v.earthAmps > 0 ? " + IN " + Elec.si(v.earthAmps, "A") : "",
                    v.relay == Long.MIN_VALUE ? "no relay" : v.relayKind + " " + at(v.relay),
                    v.time == INF ? "§cdoes NOT trip§7 (" + v.note + ")" : String.format(Locale.ROOT, "§a%s in %.2f s§7", v.func, v.time)));
        }
        List<Dev> order = new ArrayList<>(devs);
        order.sort(Comparator.comparingDouble(v -> v.time));
        Dev first = order.get(0);
        if (first.time == INF) out.add("§4✖ NOTHING TRIPS - the fault would burn until something melts. Lower the pick-ups / turn on 50N");
        else {
            boolean right = first.hop == devs.stream().filter(v -> v.time != INF).mapToInt(v -> v.hop).min().orElse(0) && first.hop == 0;
            out.add(String.format(Locale.ROOT, "%s TRIPS FIRST: breaker %s (%s) in %.2f s incl. breaker", right ? "§a✔" : "§c✖", at(first.breaker), first.func, first.time + BREAKER_S));
            if (first.hop > 0) out.add(String.format(Locale.ROOT, "§c✖ MIS-COORDINATION: %d breaker(s) nearer the fault would stay closed - everything they feed AND everything beyond breaker %s goes dark", first.hop, at(first.breaker)));
            // grading margins between consecutive operating relays, nearest first
            Dev prev = null;
            for (Dev v : devs) {
                if (v.time == INF) continue;
                if (prev != null) {
                    double m = v.time - prev.time;
                    if (m < 0) out.add(String.format(Locale.ROOT, "§c✖ %s (upstream, %.2f s) is FASTER than %s (%.2f s)", at(v.breaker), v.time, at(prev.breaker), prev.time));
                    else if (m < 0.25) out.add(String.format(Locale.ROOT, "§e⚠ grading margin only %.2f s between %s and %s (want ≥ 0.25 s: relay error + breaker time + overshoot)", m, at(prev.breaker), at(v.breaker)));
                }
                prev = v;
            }
            Dev backup = order.size() > 1 && order.get(1).time != INF ? order.get(1) : null;
            out.add(backup == null ? "§e⚠ No backup: if breaker " + at(first.breaker) + " fails to open, nothing else clears this fault"
                    : String.format(Locale.ROOT, "§7Backup: if %s fails to open, %s clears in %.2f s%s", at(first.breaker), at(backup.breaker), backup.time + BREAKER_S, first.bf ? " (50BF would trip the next breaker up at +0.15 s)" : ""));
        }
        for (Dev v : devs) {
            GridData.Node bn = d.nodes().get(v.breaker);
            if (v.amps > bn.kA * 1000.0) out.add(String.format(Locale.ROOT, "§4✖ breaker %s is rated %d kA but would have to break %s - it would EXPLODE. Fit a bigger one", at(v.breaker), bn.kA, Elec.si(v.amps, "A")));
            if (bn.wear >= 1) out.add(String.format(Locale.ROOT, "§e⚠ breaker %s is %d%% worn - it may fail to open", at(v.breaker), Math.round(bn.wear * 100)));
        }
        LAST.put(w.provider.getDimension(), out);
        return out;
    }

    /** secondary-injection test of one IED (commissioning): pick-up and timings at 2x, 5x, 10x its setting */
    public static List<String> inject(World w, BlockPos ied) {
        GridData d = GridData.get(w);
        GridData.Node r = d.node(ied);
        List<String> out = new ArrayList<>();
        List<Long> bs = tripsOf(d, ied.toLong());
        if (r == null || bs.isEmpty()) { out.add("§cNo breaker touching this IED - it has nothing to trip"); return out; }
        GridData.Node b = d.nodes().get(bs.get(0));
        GridData.Net net = netOf(d, bs.get(0));
        Elec.Level lv = net == null ? null : net.level;
        double is = ratedAmps(b, lv) * r.get("is", 100) / 100.0;
        out.add(String.format(Locale.ROOT, "§b§lSECONDARY INJECTION§r§7 - IED %d %d %d · I> %s · %s TMS %.2f · I>> %s", ied.getX(), ied.getY(), ied.getZ(), Elec.si(is, "A"),
                CURVES[Math.max(0, Math.min(4, r.get("curve", 0)))], r.get("tms", 10) / 100.0, r.get("hi", 8) == 0 ? "off" : r.get("hi", 8) + "x (" + r.get("hid", 50) + " ms)"));
        for (double m : new double[]{1.05, 2, 5, 10, 20}) {
            double t = (r.get("hi", 8) > 0 && m >= r.get("hi", 8)) ? r.get("hid", 50) / 1000.0 : curve(r.get("curve", 0), m, r.get("tms", 10) / 100.0);
            out.add(String.format(Locale.ROOT, "§7  %5.2fx = %s → %s", m, Elec.si(is * m, "A"), t == INF ? "no trip" : String.format(Locale.ROOT, "%.3f s", t)));
        }
        out.add(String.format(Locale.ROOT, "§7  IN> %s %s · 87T %s (2nd harmonic restraint %s) · 27 %s · 59 %s · 81U %s · 81O %s · 50BF %s",
                r.get("efon", 1) == 1 ? Elec.si(is * r.get("efs", 20) / 100.0, "A") : "off", r.get("efon", 1) == 1 ? "in " + r.get("efd", 500) + " ms" : "",
                zoneOf(d, ied.toLong()) == Long.MIN_VALUE ? "no transformer in zone" : r.get("dif", 1) == 1 ? "ON" : "off", r.get("h2", 1) == 1 ? "ON" : "OFF",
                r.get("uv", 0) == 0 ? "off" : "<" + r.get("uv", 0) + "%", r.get("ov", 115) == 0 ? "off" : ">" + r.get("ov", 115) + "%",
                r.get("uf", 0) == 0 ? "off" : "<" + r.get("uf", 0) / 10.0 + " Hz", r.get("of", 0) == 0 ? "off" : ">" + r.get("of", 0) / 10.0 + " Hz", r.get("bf", 1) == 1 ? "ON" : "off"));
        out.add(r.st("aux") == 1 ? (r.st("test") == 1 ? "§eIED healthy, in TEST: trip outputs blocked" : "§aIED healthy, in service") : "§cIED has no 12 V aux supply: DEAD");
        return out;
    }
}
