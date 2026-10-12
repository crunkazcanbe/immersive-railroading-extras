package com.dogpound.railmap.asset;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.grid.GridData;
import com.dogpound.railmap.grid.GridKind;
import com.dogpound.railmap.grid.Insulation;
import com.dogpound.railmap.grid.Permits;
import com.dogpound.railmap.grid.Thermal;
import com.dogpound.railmap.signal.Aspect;
import com.dogpound.railmap.signal.TileSignalMast;
import com.dogpound.railmap.track.TilePointMachine;
import com.dogpound.railmap.track.TrackData;
import com.dogpound.railmap.wire.IWiredGear;
import com.dogpound.railmap.wire.WireNet;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;
import java.util.function.Supplier;

/**
 * Commissioning (her 48-next list #10) and the commissioning wizard (#45). New gear starts UNCOMMISSIONED. The wizard
 * SCANS the gear round the test set and its connections - DISCOVERED from what is physically there (a 12 V pair traced
 * to a supply, a control wire on the test set's bus, a relay / IED touching its breaker, a point machine linked to its
 * switch, a signal reading its rail, a transformer's protection) - and PROPOSES the acceptance tests each asset needs.
 * RUN works through them with the real machines (it throws the points through their own machine, megger-tests dead
 * plant, secondary-injects the IEDs...). A connection becomes VERIFIED only when a test has made something go across it
 * and come back (the points moved and the machine detected them, the relay tripped the breaker on the bench...), never
 * just because it was found. SIGN OFF: every test of the asset passed -> COMMISSIONED, signed, with a certificate.
 */
public final class Commissioning {
    private Commissioning() {}

    public static final class Conn {
        public String a = "", b = "", kind = "", how = "";
        public long pa, pb;
        public boolean verified;
    }

    public static final class Test {
        public String asset = "", name = "", detail = "";
        public int state;                 // 0 pending, 1 running, 2 PASS, 3 FAIL, 4 SKIP
        int wait = 20;
        Supplier<String> start = () -> null, check = () -> null;
        Runnable after = () -> { };
        public Conn verifies;
    }

    public static final String[] TSTATE = {"PENDING", "RUNNING", "PASS", "FAIL", "SKIP"};

    // ---- discovery --------------------------------------------------------------------------------------------------------
    public static List<Conn> discover(World w, BlockPos at, int radius) {
        List<Conn> out = new ArrayList<>();
        AssetData d = AssetData.get(w);
        double r2 = (double) radius * radius;
        GridData g = GridData.get(w);
        // 12 V supplies and the test set's own control bus
        Set<Long> bus = new HashSet<>();
        try { for (BlockPos p : WireNet.bus(w, at)) bus.add(p.toLong()); } catch (RuntimeException ignored) { }
        for (TileEntity t : new ArrayList<>(w.loadedTileEntityList)) {
            if (!(t instanceof IWiredGear gear) || t.getPos().distanceSq(at) > r2 || t.getPos().equals(at)) continue;
            WireNet.Supply s = WireNet.power(w, t.getPos());
            if (s.state != 0) out.add(conn("12V SUPPLY", "12 V " + s.what, gear.wiredName() + " " + xz(t.getPos()), 0, t.getPos().toLong(), s.state == 1 && gear.term().powered(),
                    s.state == 1 && gear.term().powered() ? "traced + its terminal sees 12 V" : s.state == 2 ? "WRONG VOLTAGE" : "traced, terminal dead"));
            if (bus.contains(t.getPos().toLong())) out.add(conn("CONTROL BUS", "test set", gear.wiredName() + " " + xz(t.getPos()), at.toLong(), t.getPos().toLong(), false, "on the bus - verify by test"));
            if (t instanceof TilePointMachine pm && pm.switchPos() != null)
                out.add(conn("POINTS DRIVE", pm.wiredName(), "switch " + xz(pm.switchPos()), t.getPos().toLong(), pm.switchPos().toLong(), false, "linked - verify by a throw test"));
            if (t instanceof TileSignalMast sm && sm.governedRail() != null)
                out.add(conn("SIGNAL READS", "signal " + com.dogpound.railmap.safety.SignalSighting.name(sm), "rail " + sm.governedRail().x + "," + sm.governedRail().z, t.getPos().toLong(),
                        new BlockPos(sm.governedRail().x, sm.governedRail().y, sm.governedRail().z).toLong(), false, "governs that rail - verify by lamp proving"));
        }
        // protection: relays / IEDs touching breakers, transformers' protection
        for (Map.Entry<Long, GridData.Node> e : g.nodes().entrySet()) {
            BlockPos p = BlockPos.fromLong(e.getKey());
            if (p.distanceSq(at) > r2) continue;
            GridData.Node n = e.getValue();
            if (n.kind == GridKind.BREAKER)
                for (EnumFacing f : EnumFacing.values()) {
                    GridData.Node rn = g.nodes().get(p.offset(f).toLong());
                    if (rn != null && rn.kind.relay()) out.add(conn("PROTECTION", rn.kind.label + " " + xz(p.offset(f)), "breaker " + xz(p), p.offset(f).toLong(), e.getKey(), false,
                            rn.kind == GridKind.IED ? "touching - verify by secondary injection" : "legacy relay - verify by a trip test"));
                }
            if (n.kind == GridKind.TRANSFORMER || n.kind == GridKind.TRACTION) {
                boolean prot = false;
                for (EnumFacing f : EnumFacing.values()) { GridData.Node rn = g.nodes().get(p.offset(f).toLong()); if (rn != null && rn.kind == GridKind.IED) prot = true; }
                out.add(conn("TX PROTECTION", "transformer " + xz(p), prot ? "87T IED" : "NONE FOUND", e.getKey(), 0, false, prot ? "IED touching - verify by injection" : "no IED touching the transformer"));
            }
        }
        // remember what was found against each asset (dependencies)
        for (Conn c : out) {
            AssetData.Asset a = byPos(d, c.pb), b = byPos(d, c.pa);
            if (a != null && b != null) a.deps.add(b.id);
        }
        return out;
    }

    static Conn conn(String kind, String a, String b, long pa, long pb, boolean verified, String how) {
        Conn c = new Conn();
        c.kind = kind; c.a = a; c.b = b; c.pa = pa; c.pb = pb; c.verified = verified; c.how = how;
        return c;
    }

    static AssetData.Asset byPos(AssetData d, long p) {
        for (AssetKind k : AssetKind.values()) { AssetData.Asset a = d.at(k, p); if (a != null) return a; }
        return null;
    }

    static String xz(BlockPos p) { return p.getX() + "," + p.getZ(); }

    /** the assets in range, uncommissioned first */
    public static List<AssetData.Asset> inRange(World w, BlockPos at, int radius, boolean all) {
        List<AssetData.Asset> o = new ArrayList<>();
        double r2 = (double) radius * radius;
        for (AssetData.Asset a : AssetData.get(w).all()) {
            if (a.kind == AssetKind.TRACK && a.comm == AssetData.COMMISSIONED && !all) continue;
            if (a.at().distanceSq(at) <= r2 && (all || a.comm != AssetData.COMMISSIONED)) o.add(a);
        }
        o.sort(Comparator.comparingInt((AssetData.Asset a) -> a.comm == AssetData.COMMISSIONED ? 1 : 0).thenComparing(a -> a.id));
        return o;
    }

    // ---- the test plan ------------------------------------------------------------------------------------------------------
    public static List<Test> plan(World w, AssetData.Asset a, List<Conn> conns) {
        List<Test> o = new ArrayList<>();
        GridData g = GridData.get(w);
        GridData.Node n = g.nodes().get(a.pos);
        TileEntity te = w.isBlockLoaded(a.at()) ? w.getTileEntity(a.at()) : null;
        Conn supply = find(conns, "12V SUPPLY", a.pos), bus = find(conns, "CONTROL BUS", a.pos);
        if (te instanceof IWiredGear gear) {
            Test t = test(o, a, "12 V supply at the terminal", () -> gear.term().powered() ? null : "terminal dead: " + gear.term().status());
            t.verifies = supply;
            if (bus != null && !(te instanceof com.dogpound.railmap.grid.TileGrid)) {
                Test b = test(o, a, "control bus round trip (lamp test)", () -> { gear.wiredPress(te instanceof com.dogpound.railmap.safety.SafetyTile ? "lamptest" : "test", "commissioning"); return null; },
                        () -> gear.wiredLamp() == 3 && !gear.term().powered() ? "no answer on the bus" : null);
                b.verifies = bus;
            }
        }
        switch (a.kind) {
            case POINTS -> {
                if (te instanceof TilePointMachine pm) {
                    Conn drive = find(conns, "POINTS DRIVE", a.pos);
                    for (String[] dir : new String[][]{{"p_rev", "REVERSE"}, {"p_nor", "NORMAL"}}) {
                        Test t = test(o, a, "throw " + dir[1] + " + detection", () -> { String r = pm.controlPress(dir[0], null); return r != null && r.startsWith("§c") || r != null && r.startsWith("§7") ? r.replaceAll("§.", "") : null; },
                                () -> { Map<String, Object> m = new HashMap<>(); pm.gearInfo(m); return dir[1].equals(m.get("detected")) ? null : "detected " + m.get("detected"); });
                        t.wait = 100;
                        t.verifies = drive;
                    }
                }
            }
            case SIGNAL -> {
                if (te instanceof TileSignalMast sm) {
                    Test t = test(o, a, "lamp proving (head lit)", () -> sm.shownAspect() == Aspect.DARK ? "head DARK" : null);
                    t.verifies = find(conns, "SIGNAL READS", a.pos);
                    test(o, a, "aspect valid", () -> sm.aspect() == null ? "no aspect" : null);
                }
            }
            case TRANSFORMER -> {
                test(o, a, "insulation resistance (megger, plant DEAD)", () -> Permits.liveNow(w, a.at()) ? "plant is LIVE - isolate + prove dead first" : n == null ? "no node" : Insulation.passes(n, volts(n)) ? null : "IR " + Insulation.megohms(n));
                test(o, a, "cooling fans", () -> n == null ? "no node" : n.fanFail ? "fan motor FAILED" : n.cool == 0 ? "fans switched OFF" : null);
                test(o, a, "ratio / tap position", () -> n == null ? "no node" : Math.abs(n.tap - 9) <= 1 || n.avr ? null : "tap " + n.tap + " (nominal 9)");
                test(o, a, "winding temperature sensors", () -> n == null ? "no node" : Thermal.hotSpotC(n) > 110 ? String.format(Locale.ROOT, "hot spot %.0f °C - investigate", Thermal.hotSpotC(n)) : null);
                Conn tp = find(conns, "TX PROTECTION", a.pos);
                if (tp != null) { Test t = test(o, a, "87T protection fitted", () -> tp.b.startsWith("NONE") ? "no IED touching the transformer" : null); t.verifies = tp.b.startsWith("NONE") ? null : tp; }
            }
            case BREAKER -> {
                test(o, a, "contact wear", () -> n == null ? "no node" : n.wear < 0.5f ? null : String.format(Locale.ROOT, "contacts %.0f%% worn - overhaul first", n.wear * 100));
                for (Conn c : conns) if (c.kind.equals("PROTECTION") && c.pb == a.pos) {
                    GridData.Node rn = g.nodes().get(c.pa);
                    Test t;
                    if (rn != null && rn.kind == GridKind.IED)
                        t = test(o, a, "secondary injection (IED " + xz(BlockPos.fromLong(c.pa)) + ")", () -> {
                            Object[] r = com.dogpound.railmap.grid.Bench.test(w, BlockPos.fromLong(c.pa));
                            if (!(Boolean) r[0]) { @SuppressWarnings("unchecked") List<String> l = (List<String>) r[1]; for (String x : l) if (x.startsWith("FAIL")) return x.substring(6); return "failed"; }
                            return null;
                        });
                    else t = test(o, a, "relay fitted (legacy, no injection)", () -> null);
                    t.verifies = c;
                }
                test(o, a, "network earthed", () -> { GridData.Net net = g.netOf(a.at()); return net == null || net.earthed ? null : "no earthing switch on the network"; });
            }
            case MOTOR -> {
                test(o, a, "insulation resistance (megger, DEAD)", () -> Permits.liveNow(w, a.at()) ? "motor supply is LIVE - isolate it first" : n == null ? "no node" : Insulation.passes(n, volts(n)) ? null : "IR " + Insulation.megohms(n));
                test(o, a, "starter + rating configured", () -> n == null ? "no node" : n.st("mva") > 0 ? null : "never energised - run it once");
            }
            case INSULATOR -> test(o, a, "insulation resistance", () -> n == null ? null : Insulation.passes(n, volts(n)) ? null : "IR " + Insulation.megohms(n));
            case TRACK -> test(o, a, "geometry within limits", () -> { TrackData.Seg s = TrackData.get(w).peek(a.at()); return s == null || s.band() <= 1 ? null : "band " + s.band() + " (" + s.worst().label + ")"; });
            case LUMINAIRE -> {                                                   // night lighting
                test(o, a, "luminaire in place", () -> w.isBlockLoaded(a.at()) && !(w.getTileEntity(a.at()) instanceof com.dogpound.railmap.light.ILamp) ? "no luminaire there" : null);
                test(o, a, "lamp healthy (no failed driver)", () -> w.getTileEntity(a.at()) instanceof com.dogpound.railmap.light.ILamp l && l.lampFailed() ? "lamp FAILED" : null);
            }
            default -> test(o, a, "structural survey (condition 60 %+)", () -> a.cond >= 60 ? null : "condition " + Math.round(a.cond) + "%");
        }
        test(o, a, "no open defects", () -> { for (AssetData.Defect df : AssetData.get(w).defectsOf(a.id)) if (df.open()) return "defect #" + df.id + " " + AssetData.STAGES[df.stage]; return null; });
        return o;
    }

    static double volts(GridData.Node n) {
        com.dogpound.railmap.grid.Elec.Level lv = com.dogpound.railmap.grid.Elec.Level.byName(n.level, null);
        return lv == null ? 11_000 : lv.volts;
    }

    static Conn find(List<Conn> l, String kind, long pos) { for (Conn c : l) if (c.kind.equals(kind) && (c.pb == pos || c.pa == pos)) return c; return null; }

    private static Test test(List<Test> o, AssetData.Asset a, String name, Supplier<String> check) { return test(o, a, name, () -> null, check); }

    private static Test test(List<Test> o, AssetData.Asset a, String name, Supplier<String> start, Supplier<String> check) {
        Test t = new Test();
        t.asset = a.id; t.name = name; t.start = start; t.check = check;
        o.add(t);
        return t;
    }

    /** step a running test: 0 ticks in = start, after its wait = check. Returns true when it finished */
    public static boolean step(World w, Test t, int ticks) {
        if (ticks == 0) {
            t.state = 1;
            String r;
            try { r = t.start.get(); } catch (RuntimeException e) { r = "test crashed: " + e; }
            if (r != null) { t.state = 3; t.detail = r; return true; }
            return false;
        }
        if (ticks < t.wait) return false;
        String r;
        try { r = t.check.get(); } catch (RuntimeException e) { r = "check crashed: " + e; }
        t.state = r == null ? 2 : 3;
        t.detail = r == null ? "ok" : r;
        if (r == null && t.verifies != null) { t.verifies.verified = true; t.verifies.how = "VERIFIED by " + t.name; }
        try { t.after.run(); } catch (RuntimeException ignored) { }
        return true;
    }

    /** every test of the asset passed: COMMISSIONED, signed, certificate. Returns the message */
    public static String sign(World w, AssetData.Asset a, List<Test> tests, List<Conn> conns, String who) {
        List<Test> mine = new ArrayList<>();
        for (Test t : tests) if (t.asset.equals(a.id)) mine.add(t);
        if (mine.isEmpty()) return "§cNo tests run for " + a.id + " - PLAN and RUN first";
        for (Test t : mine) if (t.state != 2) return "§cCan't sign " + a.id + ": '" + t.name + "' is " + TSTATE[t.state] + (t.detail.isEmpty() ? "" : " (" + t.detail + ")");
        List<String> l = new ArrayList<>();
        l.add("COMMISSIONING CERTIFICATE " + a.id);
        l.add(a.kind.label + " - " + a.name + " at " + a.at().getX() + "," + a.at().getY() + "," + a.at().getZ());
        for (Test t : mine) l.add("PASS  " + t.name + (t.detail.equals("ok") ? "" : " - " + t.detail));
        for (Conn c : conns) if (c.pa == a.pos || c.pb == a.pos) l.add((c.verified ? "VERIFIED  " : "found     ") + c.kind + ": " + c.a + " -> " + c.b);
        l.add("Signed off by " + who);
        AssetData d = AssetData.get(w);
        AssetData.Cert cert = d.addCert(w, a.id, "COMMISSIONING", true, l, who);
        a.comm = AssetData.COMMISSIONED;
        a.commBy = who;
        a.commAt = w.getTotalWorldTime();
        a.inspected = a.commAt;
        a.note(w, "COMMISSIONED - signed off by " + who + " (certificate #" + cert.id + ")");
        d.note(w, a.id + " COMMISSIONED by " + who);
        Recorder.event(w, "COMMISSIONING: " + a.id + " " + a.name + " signed off by " + who);
        d.markDirty();
        return "§a" + a.id + " COMMISSIONED - certificate #" + cert.id;
    }

    /** a test failed: the asset is FAILED TESTS until it passes */
    public static void failed(World w, AssetData.Asset a, Test t) {
        if (a.comm == AssetData.COMMISSIONED) return;
        a.comm = AssetData.COMM_FAIL;
        a.note(w, "acceptance test FAILED: " + t.name + " - " + t.detail);
        AssetData.get(w).markDirty();
    }

    /** harsh mode: may this breaker close? (TileGrid.setBreaker) */
    public static boolean mayEnergise(World w, BlockPos p) {
        if (!AssetConfig.commissioningRequired || !AssetConfig.assetRegister) return true;
        AssetData.Asset a = AssetData.get(w).at(AssetKind.BREAKER, p.toLong());
        if (a == null || a.comm == AssetData.COMMISSIONED) return true;
        AssetTicker.standing(w, "commreq:" + p.toLong(), AlarmManager.Cat.POWER, AlarmManager.Sev.INFO, "Breaker " + a.id + " refused to close: UNCOMMISSIONED - test and sign it off first", p);
        return false;
    }
}
