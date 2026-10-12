package com.dogpound.railmap.safety;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.alarm.RootCause;
import com.dogpound.railmap.ctl.CtlConfig;
import com.dogpound.railmap.graph.TrainNode;
import com.dogpound.railmap.grid.*;
import com.dogpound.railmap.server.TrainTracker;
import com.dogpound.railmap.signal.SignalRegistry;
import com.dogpound.railmap.signal.TileSignalMast;
import com.dogpound.railmap.station.Pis;
import com.dogpound.railmap.station.TilePisBoard;
import com.dogpound.railmap.station.TilePisServer;
import com.dogpound.railmap.track.TileSmokeDetector;
import com.dogpound.railmap.track.TileTunnelControl;
import com.dogpound.railmap.track.TileTunnelFan;
import com.dogpound.railmap.track.TrackHooks;
import com.dogpound.railmap.wire.Comms;
import com.dogpound.railmap.wire.IWiredGear;
import com.dogpound.railmap.wire.TileRtu;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * Railway chaos engineering (her "next 48" list #44): the Fault Lab grown from single faults into SCENARIOS - a
 * realistic failure injected into the live (test-mode) railway, with the outcomes the design promises written down
 * beforehand, safety guards that abort and restore if anything goes beyond the experiment, and a pass / fail report.
 * <ul>
 *   <li>SUBSTATION TRIP - a loaded feeder breaker in the zone trips: the alarm comes in, root-cause analysis names the
 *       trip as the ORIGINAL fault, a microgrid controller islands, UPS-backed 12 V gear stays up; then it recovers;</li>
 *   <li>COMMS PARTITION - every comms link in the zone is cut: RTUs report NO PATH, covered gear goes STALE on the
 *       console, console commands are refused, but the signals keep working locally; it heals and goes FRESH;</li>
 *   <li>RTU CPU FAILURE - one field controller dies: alarm, its gear STALE; RESTART brings it back;</li>
 *   <li>FIBRE CUT - link A cut on every RTU: they fail over to radio where a mast reaches, alarm where none does;</li>
 *   <li>TUNNEL FIRE - test smoke in a detector: tunnel emergency, fans to full, signals held, evacuation on the PIS;</li>
 *   <li>FILTER LOSS - the harmonic filter banks trip: their network's voltage THD must not FALL (it rises with harmonic
 *       load on it), the meters see it;</li>
 *   <li>PIS SERVER LOSS - the PIS servers lose 12 V: every PIS sign in the zone falls back to the fallback message.</li>
 * </ul>
 * Guards: only in TEST MODE from the lab console (CtlConfig.chaosNeedsTestMode), only gear inside the lab's zone, the
 * lab's ISOLATED guard aborts on a moving live train, a CRITICAL alarm outside the zone aborts, 3 minutes at most.
 */
public final class Chaos {
    private Chaos() {}

    public static final String[] NAMES = {"SUBSTATION TRIP", "COMMS PARTITION", "RTU CPU FAILURE", "FIBRE CUT", "TUNNEL FIRE", "FILTER LOSS", "PIS SERVER LOSS"};
    public static final String[] SHORT = {"TRIP", "PART", "RTU", "FIBRE", "FIRE", "FILTER", "PIS"};

    static final class Expect {
        final String what; final int by; final BooleanSupplier met; final boolean recovery;
        int done = -1;            // second it was met, -2 = failed
        Expect(String what, int by, BooleanSupplier met, boolean recovery) { this.what = what; this.by = by; this.met = met; this.recovery = recovery; }
    }

    static final class Run {
        int scenario;
        long lab, start, restoredAt = -1, lastTick;
        int radius;
        boolean guardIsolated;
        final List<Expect> expects = new ArrayList<>();
        final List<Runnable> restore = new ArrayList<>();
        final List<String> lines = new ArrayList<>();
        final Set<Integer> alarmsBefore = new HashSet<>();
        String skip;
    }

    private static final Map<Integer, Run> RUNS = new HashMap<>();
    private static final Map<Integer, List<String>> LAST = new HashMap<>();

    public static boolean running(World w) { return RUNS.containsKey(w.provider.getDimension()); }
    public static List<String> lastReport(World w) { return LAST.getOrDefault(w.provider.getDimension(), Collections.emptyList()); }
    public static String current(World w) { Run r = RUNS.get(w.provider.getDimension()); return r == null ? "" : NAMES[r.scenario] + " " + (w.getTotalWorldTime() - r.start) / 20 + " s"; }

    private static boolean in(Run r, BlockPos p) { BlockPos l = BlockPos.fromLong(r.lab); return l.distanceSq(p) <= (double) r.radius * r.radius; }
    private static <T extends TileEntity> List<T> inZone(World w, Run r, Class<T> c) {
        List<T> out = new ArrayList<>();
        for (TileEntity t : new ArrayList<>(w.loadedTileEntityList)) if (c.isInstance(t) && !t.isInvalid() && in(r, t.getPos())) out.add(c.cast(t));
        return out;
    }

    /** start a scenario from a lab console (or the command, which names the lab) */
    public static String start(World w, BlockPos lab, int radius, boolean guardIsolated, int scenario, String who) {
        if (running(w)) return "§cA chaos scenario is already running (" + current(w) + ")";
        SafetyData d = SafetyData.get(w);
        if (CtlConfig.chaosNeedsTestMode && (!d.testMode || d.labPos != lab.toLong())) return "§cChaos scenarios run only from a Fault Lab console in TEST MODE";
        if (scenario < 0 || scenario >= NAMES.length) return "§cNo such scenario";
        Run r = new Run();
        r.scenario = scenario; r.lab = lab.toLong(); r.radius = radius; r.guardIsolated = guardIsolated; r.start = w.getTotalWorldTime();
        for (AlarmManager.Alarm a : AlarmManager.get(w).all()) r.alarmsBefore.add(a.id);
        if (guardIsolated && liveTrain(w, r)) return "§cRefused - a live train is moving in the zone (guard ISOLATED)";
        r.lines.add("CHAOS " + NAMES[scenario] + " " + Recorder.clock(w, r.start) + " zone " + radius + "m by " + who);
        try { setup(w, r); } catch (RuntimeException e) { r.skip = "setup failed: " + e; }
        if (r.skip != null) { r.lines.add("SKIP: " + r.skip); finish(w, r); return "§6Scenario skipped: " + r.skip; }
        RUNS.put(w.provider.getDimension(), r);
        Recorder.event(w, "CHAOS: " + NAMES[scenario] + " started by " + who + " (" + r.expects.size() + " expected outcomes)");
        return "§6Chaos scenario " + NAMES[scenario] + " running - " + r.expects.size() + " expected outcomes";
    }

    private static boolean liveTrain(World w, Run r) {
        BlockPos l = BlockPos.fromLong(r.lab);
        for (TrainNode t : TrainTracker.latest(w)) if (t.moving() && l.distanceSq(t.x, t.y, t.z) <= (double) r.radius * r.radius) return true;
        return false;
    }

    private static void expect(Run r, String what, int by, BooleanSupplier met) { r.expects.add(new Expect(what, by, met, false)); }
    private static void recover(Run r, String what, int by, BooleanSupplier met) { r.expects.add(new Expect(what, by, met, true)); }

    // ---- the scenarios -------------------------------------------------------------------------------------------------
    private static void setup(World w, Run r) {
        switch (r.scenario) {
            case 0: { // substation trip
                GridData d = GridData.get(w);
                TileGrid pick = null;
                double load = -1;
                for (TileGrid t : inZone(w, r, TileGrid.class)) {
                    if (t.kind() != GridKind.BREAKER || t.breakerState() != 0) continue;
                    GridData.Node n = d.node(t.getPos());
                    if (n != null && n.amps > load) { load = n.amps; pick = t; }
                }
                if (pick == null) { r.skip = "no closed grid breaker in the zone"; return; }
                TileGrid br = pick;
                BlockPos bp = br.getPos();
                br.chaosTrip("CHAOS substation trip");
                r.lines.add(String.format(Locale.ROOT, "INJECT: tripped breaker %d,%d,%d (%.0f A through it)", bp.getX(), bp.getY(), bp.getZ(), load));
                expect(r, "breaker shows TRIPPED", 2, () -> br.breakerState() == 2);
                expect(r, "a POWER alarm is raised for it", 6, () -> newAlarm(w, r, AlarmManager.Cat.POWER, null) != null || newAlarm(w, r, null, null) != null);
                expect(r, "root cause: the trip is an ORIGINAL fault, not a symptom", 10, () -> {
                    RootCause.Graph g = RootCause.build(w);
                    for (AlarmManager.Alarm a : g.nodes) if (!r.alarmsBefore.contains(a.id) && !"SYMPTOM".equals(g.role.get(a.id))) return true;
                    return false;
                });
                List<TileMicrogrid> mgs = inZone(w, r, TileMicrogrid.class);
                if (!mgs.isEmpty()) expect(r, "microgrid controller(s) island / stay supplied", 20, () -> { for (TileMicrogrid m : mgs) if (m.state() == TileMicrogrid.St.TIED && m.controlValue("util") == 0) return false; return true; });
                List<IWiredGear> gear = new ArrayList<>();
                for (TileEntity t : inZone(w, r, TileEntity.class)) if (t instanceof IWiredGear g && g.term().powered()) gear.add(g);
                expect(r, "12 V gear stays powered (UPS / battery) - " + gear.size() + " items", 8, () -> { for (IWiredGear g : gear) if (!g.term().powered()) return false; return true; });
                byte was = 0;
                r.restore.add(() -> br.chaosRestore(was, "restored by the chaos lab"));
                recover(r, "breaker back in", 5, () -> br.breakerState() == 0);
                return;
            }
            case 1: { // comms partition
                List<TileRtu> rtus = inZone(w, r, TileRtu.class);
                if (rtus.isEmpty()) { r.skip = "no Field RTU in the zone"; return; }
                List<TileSignalMast> masts = new ArrayList<>();
                for (TileSignalMast m : SignalRegistry.masts(w.provider.getDimension())) if (!m.isInvalid() && in(r, m.getPos()) && m.term().powered()) masts.add(m);
                BlockPos l = BlockPos.fromLong(r.lab);
                Comms.partition(w, l.getX(), l.getZ(), r.radius, true);
                r.lines.add("INJECT: comms partition, radius " + r.radius + " m (" + rtus.size() + " RTUs)");
                expect(r, "every RTU in the zone reports NO PATH", 4, () -> { for (TileRtu t : rtus) if (t.rec().path != 0) return false; return true; });
                expect(r, "covered gear goes STALE on the console", CtlConfig.commsStaleSeconds + 5, () -> { for (TileRtu t : rtus) if (!Comms.stale(w, t.rec())) return false; return true; });
                expect(r, "console commands to covered gear are refused", CtlConfig.commsStaleSeconds + 5, () -> Comms.command(w, rtus.get(0).getPos().add(1, 0, 0)) != null);
                expect(r, "signals keep working locally (" + masts.size() + " stay powered and lit)", 8, () -> { for (TileSignalMast m : masts) if (!m.term().powered()) return false; return true; });
                r.restore.add(() -> Comms.partition(w, l.getX(), l.getZ(), r.radius, false));
                recover(r, "RTUs back on a path and FRESH", 15, () -> { for (TileRtu t : rtus) if (t.rec().path == 0 || Comms.stale(w, t.rec())) return false; return true; });
                return;
            }
            case 2: { // RTU cpu failure
                List<TileRtu> rtus = inZone(w, r, TileRtu.class);
                if (rtus.isEmpty()) { r.skip = "no Field RTU in the zone"; return; }
                TileRtu t = rtus.get(0);
                Comms.cuts(w).add("CPU:" + t.getPos().toLong());
                r.lines.add("INJECT: CPU failure on " + t.id());
                expect(r, "RTU reports CPU FAILED", 3, () -> !t.rec().cpuOk);
                expect(r, "a WIRED alarm names the RTU", 5, () -> newAlarm(w, r, AlarmManager.Cat.WIRED, t.id()) != null);
                expect(r, "its gear is STALE", 4, () -> Comms.stale(w, t.rec()));
                r.restore.add(() -> Comms.cuts(w).remove("CPU:" + t.getPos().toLong()));
                recover(r, "RTU running again", 6, () -> t.rec().cpuOk);
                return;
            }
            case 3: { // fibre cut
                List<TileRtu> rtus = inZone(w, r, TileRtu.class);
                if (rtus.isEmpty()) { r.skip = "no Field RTU in the zone"; return; }
                for (TileRtu t : rtus) Comms.cuts(w).add("A:" + t.getPos().toLong());
                r.lines.add("INJECT: fibre (link A) cut on " + rtus.size() + " RTU(s)");
                expect(r, "each RTU fails over to radio or alarms NO PATH", 6, () -> {
                    for (TileRtu t : rtus) { Comms.Rtu x = t.rec(); if (x.path == 1) return false; if (x.path == 0 && AlarmManager.get(w).all().stream().noneMatch(a -> a.active && a.key.equals("rtu:" + t.getPos().toLong()))) return false; }
                    return true;
                });
                r.restore.add(() -> { for (TileRtu t : rtus) Comms.cuts(w).remove("A:" + t.getPos().toLong()); });
                recover(r, "back on the preferred path", 8, () -> { for (TileRtu t : rtus) if (t.rec().path != t.rec().prefer && (t.rec().prefer == 1 ? t.rec().aUp : t.rec().bUp)) return false; return true; });
                return;
            }
            case 4: { // tunnel fire
                List<TileSmokeDetector> dets = inZone(w, r, TileSmokeDetector.class);
                List<TileTunnelControl> tcs = inZone(w, r, TileTunnelControl.class);
                if (dets.isEmpty() || tcs.isEmpty()) { r.skip = "no tunnel control panel + smoke detector in the zone"; return; }
                TileSmokeDetector d = null;
                for (TileSmokeDetector x : dets) if (!x.isolated() && x.term().powered()) { d = x; break; }
                if (d == null) { r.skip = "every detector is isolated or unpowered"; return; }
                TileSmokeDetector det = d;
                TileTunnelControl tc = TileTunnelControl.of(w, det.getPos());
                if (tc == null) { r.skip = "the detector isn't inside a tunnel panel's zone"; return; }
                det.controlPress("test", null);
                r.lines.add("INJECT: test smoke in detector " + det.id() + " (tunnel " + tc.id() + ")");
                expect(r, "detector in ALARM", 4, det::alarm);
                expect(r, "tunnel EMERGENCY", 6, tc::emergency);
                expect(r, "ventilation running", 12, () -> { for (TileTunnelFan f : TrackHooks.live(w, TileTunnelFan.class)) if (tc.inZone(f.getPos().getX(), f.getPos().getZ()) && f.running()) return true; return TrackHooks.live(w, TileTunnelFan.class).isEmpty(); });
                if (Pis.anyServer(w)) expect(r, "an EVACUATION is on the passenger information feed", 15, () -> { for (Pis.Msg m : Pis.get(w).msgs) if (m.kind == Pis.Kind.EVAC && m.expires == 0 && m.created >= r.start) return true; return false; });
                r.restore.add(() -> {});
                recover(r, "smoke clears and the emergency can be reset", 40, () -> { if (det.alarm()) return false; if (tc.emergency()) tc.controlPress("p_reset", null); return !tc.emergency(); });
                return;
            }
            case 5: { // filter loss
                List<TileFilterBank> fs = new ArrayList<>();
                for (TileFilterBank f : inZone(w, r, TileFilterBank.class)) if (f.closed()) fs.add(f);
                if (fs.isEmpty()) { r.skip = "no harmonic filter bank in service in the zone"; return; }
                GridData.Net n = PowerQuality.measured(GridData.get(w), fs.get(0).getPos().toLong());
                double[] before = n == null ? null : Harmonics.spectrum(w, n.id);
                double thd0 = before == null ? 0 : before[26];
                int net = n == null ? -1 : n.id;
                for (TileFilterBank f : fs) f.chaosTrip();
                r.lines.add(String.format(Locale.ROOT, "INJECT: %d filter bank(s) tripped, THDv before %.2f%%", fs.size(), thd0));
                expect(r, "filters out of service with a MAJOR alarm", 3, () -> { for (TileFilterBank f : fs) if (f.closed()) return false; return newAlarm(w, r, AlarmManager.Cat.POWER, null) != null; });
                expect(r, "network THD does not fall with the filters out", 4, () -> { double[] v = net < 0 ? null : Harmonics.spectrum(w, net); return v != null && v[26] >= thd0 - 0.01; });
                r.restore.add(() -> { for (TileFilterBank f : fs) f.chaosReset(); });
                recover(r, "filters reset and ready to close", 3, () -> { for (TileFilterBank f : fs) if (f.controlValue("trip") == 1) return false; return true; });
                return;
            }
            default: { // PIS server loss
                List<TilePisServer> ss = inZone(w, r, TilePisServer.class);
                List<TilePisBoard> bs = inZone(w, r, TilePisBoard.class);
                if (ss.isEmpty() || bs.isEmpty()) { r.skip = "no PIS server + PIS sign in the zone"; return; }
                for (TilePisServer s : ss) { s.term().testCut = true; s.gearChanged(); }
                r.lines.add("INJECT: 12 V cut to " + ss.size() + " PIS server(s)");
                expect(r, "every PIS sign shows the FALLBACK message", 5, () -> { for (TilePisBoard b : bs) if (b.term().powered() && b.controlValue("fallback") != 1) return false; return true; });
                r.restore.add(() -> { for (TilePisServer s : ss) { s.term().testCut = false; s.gearChanged(); } });
                recover(r, "signs back online", 6, () -> { for (TilePisBoard b : bs) if (b.term().powered() && b.controlValue("online") != 1) return false; return true; });
            }
        }
    }

    /** a new alarm since the start (category / text filter), or null */
    private static AlarmManager.Alarm newAlarm(World w, Run r, AlarmManager.Cat cat, String textHas) {
        for (AlarmManager.Alarm a : AlarmManager.get(w).all())
            if (!r.alarmsBefore.contains(a.id) && (cat == null || a.cat == cat) && (textHas == null || a.text.contains(textHas))) return a;
        return null;
    }

    /** every second, from the lab console that started it */
    public static void tick(World w) {
        Run r = RUNS.get(w.provider.getDimension());
        long now = w.getTotalWorldTime();
        if (r == null || now - r.lastTick < 20) return;
        r.lastTick = now;
        int t = (int) ((now - r.start) / 20);
        SafetyData d = SafetyData.get(w);
        // guards
        String abort = null;
        if (CtlConfig.chaosNeedsTestMode && (!d.testMode || d.labPos != r.lab)) abort = "test mode ended";
        else if (r.guardIsolated && liveTrain(w, r)) abort = "a live train entered the zone (guard ISOLATED)";
        else if (t > 180) abort = "over 3 minutes";
        else for (AlarmManager.Alarm a : AlarmManager.get(w).all())
            if (!r.alarmsBefore.contains(a.id) && a.sev == AlarmManager.Sev.CRITICAL && a.pos != 0 && !in(r, BlockPos.fromLong(a.pos)) && !a.text.contains("CHAOS")) { abort = "CRITICAL alarm outside the zone: " + a.text; break; }
        if (abort != null) {
            r.lines.add("ABORTED: " + abort);
            for (Runnable x : r.restore) try { x.run(); } catch (RuntimeException ignored) { }
            for (Expect e : r.expects) if (e.done == -1) e.done = -2;
            finish(w, r);
            return;
        }
        boolean injectDone = true;
        for (Expect e : r.expects) {
            if (e.recovery || e.done != -1) continue;
            if (safe(e.met)) { e.done = t; r.lines.add("PASS " + e.what + " (" + t + " s)"); }
            else if (t > e.by) { e.done = -2; r.lines.add("FAIL " + e.what + " - not within " + e.by + " s"); }
            else injectDone = false;
        }
        if (injectDone && r.restoredAt < 0) {
            for (Runnable x : r.restore) try { x.run(); } catch (RuntimeException ex) { r.lines.add("restore error: " + ex); }
            r.restoredAt = now;
            r.lines.add("RESTORE at " + t + " s");
            return;
        }
        if (r.restoredAt >= 0) {
            int rt = (int) ((now - r.restoredAt) / 20);
            boolean all = true;
            for (Expect e : r.expects) {
                if (!e.recovery || e.done != -1) continue;
                if (safe(e.met)) { e.done = rt; r.lines.add("PASS recovery: " + e.what + " (" + rt + " s)"); }
                else if (rt > e.by) { e.done = -2; r.lines.add("FAIL recovery: " + e.what + " - not within " + e.by + " s"); }
                else all = false;
            }
            if (all) finish(w, r);
        }
    }

    private static boolean safe(BooleanSupplier s) { try { return s.getAsBoolean(); } catch (RuntimeException e) { return false; } }

    private static void finish(World w, Run r) {
        RUNS.remove(w.provider.getDimension());
        int pass = 0, fail = 0;
        for (Expect e : r.expects) if (e.done >= 0) pass++; else fail++;
        r.lines.add("RESULT: " + pass + " PASS, " + fail + " FAIL" + (r.skip != null ? " (SKIPPED)" : fail == 0 ? " - behaved as designed" : " - see the FAIL lines"));
        LAST.put(w.provider.getDimension(), new ArrayList<>(r.lines));
        SafetyData d = SafetyData.get(w);
        d.report.clear(); d.report.addAll(r.lines); d.reportPass = pass; d.reportFail = fail; d.reportAt = w.getTotalWorldTime();
        d.markDirty();
        Recorder.event(w, "CHAOS: " + NAMES[r.scenario] + " finished - " + pass + " pass, " + fail + " fail");
        com.dogpound.railmap.api.QuestEvents.fire(w, null, BlockPos.fromLong(r.lab), "chaos_finished", "scenario", NAMES[r.scenario], "pass", pass, "fail", fail, "skipped", r.skip != null ? 1 : 0);
    }

    public static String abort(World w, String who) {
        Run r = RUNS.get(w.provider.getDimension());
        if (r == null) return "§7No chaos scenario running";
        r.lines.add("ABORTED by " + who);
        for (Runnable x : r.restore) try { x.run(); } catch (RuntimeException ignored) { }
        for (Expect e : r.expects) if (e.done == -1) e.done = -2;
        finish(w, r);
        return "§6Chaos scenario aborted - everything put back";
    }
}
