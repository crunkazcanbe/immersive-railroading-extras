package com.dogpound.railmap.safety;

import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.Interlocking;
import com.dogpound.railmap.graph.TrainNode;
import com.dogpound.railmap.server.TrainTracker;
import com.dogpound.railmap.signal.Aspect;
import com.dogpound.railmap.signal.SignalRegistry;
import com.dogpound.railmap.signal.TileCrossing;
import com.dogpound.railmap.signal.TileSignalMast;
import com.dogpound.railmap.signal.TileTrackCircuit;
import cam72cam.immersiverailroading.library.SwitchState;
import cam72cam.mod.math.Vec3i;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import java.util.*;
import java.util.function.Supplier;

/**
 * Railway Fault-Injection Laboratory console (her ChatGPT list: "a particularly good addition"): a test bench that
 * breaks the railway on purpose, in an explicit TEST MODE, and proves the signalling fails safe.
 * <ul>
 *   <li>TEST MODE key: only one lab at a time, only gear inside its zone (32 / 64 / 128 m). With the guard on
 *   ISOLATED it won't start while a live train moves in the zone and aborts (clearing every fault) if one comes in;</li>
 *   <li>manual: FAULT selector + a TARGET (Signal Wrench: pick a track circuit, signal, rail, switch, radio mast or axle
 *   head, click the target window) + INJECT; CLEAR takes every fault out;</li>
 *   <li>RUN: the automated sequence finds every signal, track circuit, switch, radio mast and axle head in the zone and,
 *   one at a time, injects its fault, checks the railway reacted safely, takes it out and checks it recovered - then
 *   cuts the 12 V to the whole zone (backup-power / fail-safe test) and checks every item went to its safe state;</li>
 *   <li>PRINT: the pass/fail report as a book; SELF-TEST runs the self-test of every piece of gear now.</li>
 * </ul>
 */
public class TileFaultLab extends SafetyTile {
    public static final FaultLab.Kind[] FAULTS = FaultLab.Kind.values();
    private long target;

    private static final class Step {
        final String name;
        final Runnable inject, restore;
        final Supplier<String> check, after;
        Step(String name, Runnable inject, Supplier<String> check, Runnable restore, Supplier<String> after) {
            this.name = name; this.inject = inject; this.check = check; this.restore = restore; this.after = after;
        }
    }

    private final List<Step> steps = new ArrayList<>();
    private int stepAt = -1;
    private long stepT;
    private final List<String> lines = new ArrayList<>();
    private int pass, fail;
    private final Map<Long, int[][]> evalSnap = new HashMap<>();

    public int zone() { return new int[]{32, 64, 128}[Math.min(2, Math.max(0, ctl.get("s_zone")))]; }
    private boolean mine() { SafetyData d = SafetyData.get(world); return d.testMode && d.labPos == pos.toLong(); }
    private boolean running() { return stepAt >= 0; }

    @Override public String wiredName() { return "Fault lab console"; }
    @Override public String gearState() { SafetyData d = SafetyData.get(world); return mine() ? "TEST MODE · " + d.faults.size() + " fault(s) in" + (running() ? " · sequence step " + (stepAt + 1) + "/" + steps.size() : "") : "normal (not testing)"; }
    @Override public int wiredLamp() { return mine() ? 2 : 1; }
    @Override public void gearInfo(Map<String, Object> m) { SafetyData d = SafetyData.get(world); m.put("testMode", mine()); m.put("faults", d.faults.size()); m.put("pass", d.reportPass); m.put("fail", d.reportFail); }

    @Override
    protected void serverTick() {
        SafetyData d = SafetyData.get(world);
        boolean mine = mine();
        if (mine && ctl.get("k_test") == 0) ctl.set("k_test", 1);
        if (!mine && ctl.get("k_test") == 1) ctl.set("k_test", 0);
        if (mine && age % 20 == 0 && ctl.get("s_guard") == 0 && liveTrainInZone()) {
            abort("TEST ABORTED - a live train entered the test zone (guard: ISOLATED)");
        }
        if (running()) runStep();
        if (Chaos.running(world)) Chaos.tick(world);                      // next-48 #44: chaos scenarios (abort themselves when test mode ends)
        if (age % 5 != 0) return;
        put("test", mine);
        put("pwr", powered());
        put("busy", running());
        put("faults", mine && !d.faults.isEmpty());
        put("pass", !running() && d.reportAt > 0 && d.reportFail == 0);
        put("fail", !running() && d.reportFail > 0);
        put("nfault", d.faults.size());
        put("npass", running() ? pass : d.reportPass);
        put("nfail", running() ? fail : d.reportFail);
        FaultLab.Kind k = FAULTS[Math.min(FAULTS.length - 1, ctl.get("s_fault"))];
        text("fault", k.label.toUpperCase(Locale.ROOT));
        text("target", k == FaultLab.Kind.POWER_CUT ? "WHOLE ZONE " + zone() + "M" : target == 0 ? "NO TARGET - WRENCH" : Geo.xz(target));
        text("step", running() ? (stepAt + 1) + "/" + steps.size() + " " + steps.get(stepAt).name : !mine ? "NORMAL OPERATION" : d.faults.isEmpty() ? "TEST MODE - NO FAULTS" : d.faults.get(d.faults.size() - 1).what);
        List<String> src = running() ? lines : d.report;
        text("line1", src.size() > 0 ? src.get(src.size() - 1) : "");
        text("line2", src.size() > 1 ? src.get(src.size() - 2) : "");
        int scn = Math.min(Chaos.NAMES.length - 1, ctl.get("s_scn"));
        text("scn", Chaos.running(world) ? "CHAOS " + Chaos.current(world) : "SCENARIO " + Chaos.NAMES[scn]);
    }

    private boolean liveTrainInZone() {
        double r2 = (double) zone() * zone();
        for (TrainNode t : TrainTracker.latest(world)) if (t.moving() && Geo.d2(t.x, t.z, pos.getX() + 0.5, pos.getZ() + 0.5) <= r2) return true;
        return false;
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        SafetyData d = SafetyData.get(world);
        switch (id) {
            case "lamptest": return lampTest();
            case "s_zone": if (mine()) return "§cLeave test mode before changing the zone"; ctl.set("s_zone", (ctl.get("s_zone") + 1) % 3); return "§eTest zone " + zone() + " m";
            case "s_guard": ctl.set("s_guard", 1 - ctl.get("s_guard")); return ctl.get("s_guard") == 0 ? "§eGuard ISOLATED - no live trains allowed in the zone" : "§6Guard WITH TRAINS - live trains may run during tests";
            case "s_fault": ctl.set("s_fault", (ctl.get("s_fault") + 1) % FAULTS.length); return "§eFault: " + FAULTS[ctl.get("s_fault")].label;
            case "s_scn": ctl.set("s_scn", (ctl.get("s_scn") + 1) % Chaos.NAMES.length); return "§eChaos scenario: " + Chaos.NAMES[ctl.get("s_scn")];
            case "chabort": return Chaos.abort(world, who(who));
            case "k_test": return testKey(who);
            default: break;
        }
        if (!powered()) return "§cThe lab console is dead - no 12 V";
        switch (id) {
            case "inject": {
                if (running()) return "§cThe automated sequence is running";
                FaultLab.Kind k = FAULTS[Math.min(FAULTS.length - 1, ctl.get("s_fault"))];
                if (k != FaultLab.Kind.POWER_CUT && target == 0) return "§cNo target - Signal Wrench: pick the gear, then click the TARGET window";
                return FaultLab.inject(world, k, k == FaultLab.Kind.POWER_CUT ? pos : BlockPos.fromLong(target), who(who));
            }
            case "clear":
                if (running()) abort("sequence aborted by " + who(who));
                FaultLab.clearAll(world, "CLEAR by " + who(who));
                return "§aAll faults cleared";
            case "run": return run(who);
            case "chaos": {
                if (running()) return "§cThe automated sequence is running";
                String ap = com.dogpound.railmap.admin.Approvals.blocked(world, "chaos", pos, who);   // next-48 #43 (when the lab is under approval control)
                if (ap != null) return ap;
                return Chaos.start(world, pos, zone(), ctl.get("s_guard") == 0, Math.min(Chaos.NAMES.length - 1, ctl.get("s_scn")), who(who));
            }
            case "abort": if (!running()) return "§7Nothing running"; abort("sequence aborted by " + who(who)); return "§6Sequence aborted - faults cleared";
            case "print": print(who); return "§7Printing the test report...";
            case "selftest": { int[] r = SelfTest.run(world, "fault lab, " + who(who)); return "§eSelf-test: §a" + r[0] + " pass §c" + r[1] + " fail §6" + r[2] + " intermittent"; }
            default: return "§7" + gearState();
        }
    }

    private String testKey(EntityPlayer who) {
        SafetyData d = SafetyData.get(world);
        if (mine()) {
            if (running()) abort("test mode ended by " + who(who));
            FaultLab.clearAll(world, "test mode ended");
            d.testMode = false;
            d.labPos = 0;
            d.markDirty();
            ctl.set("k_test", 0);
            Recorder.event(world, "FAULT LAB: test mode OFF at " + pos.getX() + "," + pos.getZ() + " (" + who(who) + ")");
            return "§aTest mode OFF - every fault removed, normal operation";
        }
        if (!powered()) return "§cNo 12 V - the lab can't enter test mode";
        if (d.testMode && d.labPos != 0) return "§cAnother Fault Lab console (" + Geo.xz(d.labPos) + ") is already in test mode";
        if (ctl.get("s_guard") == 0 && liveTrainInZone()) return "§cRefused - a live train is moving in the test zone (guard ISOLATED)";
        d.testMode = true;
        d.labPos = pos.toLong();
        d.labRadius = zone();
        d.markDirty();
        ctl.set("k_test", 1);
        Recorder.event(world, "FAULT LAB: TEST MODE ON at " + pos.getX() + "," + pos.getZ() + ", zone " + zone() + " m (" + who(who) + ")");
        return "§6TEST MODE - faults may now be injected within " + zone() + " m. Turn the key back to end the test.";
    }

    @Override
    public String link(String control, BlockPos pick, EntityPlayer who) {
        if (!control.equals("target") && !control.equals("fault")) return "§7Click the TARGET window to set the lab's target";
        target = pick.toLong();
        controlsChanged();
        TileEntity t = world.getTileEntity(pick);
        return "§aLab target: " + (t instanceof IPickable p ? p.pickName() : t != null ? t.getClass().getSimpleName().replace("Tile", "") : "rail") + " at " + pick.getX() + "," + pick.getY() + "," + pick.getZ();
    }

    // ---- the automated sequence -----------------------------------------------------------------------------------
    private String run(EntityPlayer who) {
        if (!mine()) return "§cTurn the TEST MODE key first";
        if (running()) return "§7Already running";
        FaultLab.clearAll(world, "sequence start");
        steps.clear();
        lines.clear();
        evalSnap.clear();
        pass = fail = 0;
        double r2 = (double) zone() * zone();
        int dim = world.provider.getDimension();
        // signals: lamp failure (+ lamp proving), then false occupancy in their block
        for (TileSignalMast m : SignalRegistry.masts(dim)) {
            if (m.isInvalid() || m.getPos().distanceSq(pos) > r2) continue;
            String n = "signal " + SignalSighting.name(m);
            BlockPos mp = m.getPos();
            Aspect[] before = new Aspect[1];
            steps.add(new Step("LAMP " + n, () -> { before[0] = m.shownAspect(); inj(FaultLab.Kind.LAMP, mp, "lab sequence"); }, () -> {
                if (m.shownAspect() != Aspect.DARK) return "head still lit (" + m.shownAspect().label + ")";
                if (!stopish(m.aspect())) return "aspect not STOP (" + m.aspect().label + ")";
                BlockPos rear = SafetyHolds.rearOf(world, mp);
                if (rear != null && FailSafe.safe(world, FailSafe.LAMP_FAILURE) && world.getTileEntity(rear) instanceof TileSignalMast r && !stopish(r.aspect()))
                    return "lamp proving failed: signal in rear " + SignalSighting.name(r) + " shows " + r.aspect().label;
                return null;
            }, () -> FaultLab.clear(world, FaultLab.Kind.LAMP, mp), () -> before[0] != Aspect.DARK && m.shownAspect() == Aspect.DARK ? "head still dark after the lamp was replaced" : null));
            Vec3i g = m.governedRail();
            if (g != null && (m.mode() == TileSignalMast.Mode.AUTO || m.mode() == TileSignalMast.Mode.CTC)) {
                BlockPos rp = new BlockPos(g.x, g.y, g.z);
                steps.add(new Step("FALSE OCC at " + n, () -> inj(FaultLab.Kind.FALSE_OCC, rp, "lab sequence"),
                        () -> stopish(m.aspect()) ? null : "signal stayed " + m.aspect().label + " with its block occupied",
                        () -> FaultLab.clear(world, FaultLab.Kind.FALSE_OCC, rp), () -> null));
            }
        }
        // track circuits: drop the relay
        for (TileTrackCircuit tc : SafetyTicker.circuits(world)) {
            if (tc.getPos().distanceSq(pos) > r2) continue;
            BlockPos tp = tc.getPos();
            steps.add(new Step("TC DROP " + RouteCheck.xz(tp), () -> inj(FaultLab.Kind.TC_DROP, tp, "lab sequence"),
                    () -> tc.occupied() ? null : "track circuit did not show OCCUPIED", () -> FaultLab.clear(world, FaultLab.Kind.TC_DROP, tp),
                    () -> !tc.term().powered() ? null : tc.occupied() && !trainNear(tp, tc.range() + 3) ? "still OCCUPIED after the relay picked up" : null));
        }
        // switches: jam them
        Set<Long> sw = new LinkedHashSet<>();
        for (TileEntity t : new ArrayList<>(world.loadedTileEntityList)) {
            if (sw.size() >= 8 || t.getPos().distanceSq(pos) > r2) continue;
            BlockPos s = FaultLab.switchOf(world, t.getPos());
            if (Interlocking.stateAt(world, s) != SwitchState.NONE) sw.add(s.toLong());
        }
        for (long sl : sw) {
            BlockPos sp = BlockPos.fromLong(sl);
            steps.add(new Step("POINTS STUCK " + RouteCheck.xz(sp), () -> inj(FaultLab.Kind.POINT_STUCK, sp, "lab sequence"), () -> {
                if (RouteCheck.pointCheck(world, sp) == null) return "points still detected while jammed";
                SwitchState before = Interlocking.stateAt(world, sp);
                if (com.dogpound.railmap.scan.SwitchControl.set(world, sp, before == SwitchState.STRAIGHT)) {
                    Interlocking.setAuto(world, sp);
                    return "jammed points moved when thrown";
                }
                return null;
            }, () -> FaultLab.clear(world, FaultLab.Kind.POINT_STUCK, sp), () -> RouteCheck.pointCheck(world, sp)));
        }
        // radio masts: take them off air
        for (TileRadioMast m : SafetyTicker.radioMasts(world)) {
            if (m.getPos().distanceSq(pos) > r2) continue;
            BlockPos mp = m.getPos();
            List<String> on = new ArrayList<>();
            steps.add(new Step("RADIO CUT " + m.cell(), () -> {
                on.clear();
                for (SafetyData.TrainRec r : SafetyData.get(world).trains.values()) if (!r.gone && r.radio == 1 && r.mast.equals(m.cell())) on.add(r.id);
                inj(FaultLab.Kind.RADIO_CUT, mp, "lab sequence");
            }, () -> {
                if (m.healthy()) return "mast still on air";
                for (String id : on) { SafetyData.TrainRec r = SafetyData.get(world).trains.get(id); if (r != null && r.radio == 1 && r.mast.equals(m.cell())) return r.headcode + " still shows a link to the dead cell"; }
                return null;
            }, () -> FaultLab.clear(world, FaultLab.Kind.RADIO_CUT, mp), () -> m.healthy() || !m.powered() ? null : "mast did not come back on air"));
        }
        // axle counter heads: fail each linked head
        for (TileAxleEvaluator e : SafetyTicker.evaluators(world)) {
            if (e.getPos().distanceSq(pos) > r2 * 4) continue;
            for (int i = 0; i < TileAxleEvaluator.N; i++) {
                for (long h : new ArrayList<>(e.sec[i].heads.keySet())) {
                    BlockPos hp = BlockPos.fromLong(h);
                    int sec = i;
                    steps.add(new Step("AXLE HEAD " + RouteCheck.xz(hp) + " (section " + (i + 1) + ")", () -> { evalSnap.putIfAbsent(e.getPos().toLong(), e.snapshot()); inj(FaultLab.Kind.AXLE_HEAD, hp, "lab sequence"); },
                            () -> e.sec[sec].state == 2 ? null : "section " + (sec + 1) + " not DISTURBED (" + TileAxleEvaluator.STATE[e.sec[sec].state] + ")",
                            () -> FaultLab.clear(world, FaultLab.Kind.AXLE_HEAD, hp),
                            () -> { int[][] s = evalSnap.remove(e.getPos().toLong()); if (s != null) e.restore(s); return null; }));
                }
            }
        }
        // grid protection chains (her 48-next #19): open each IED's trip circuit - the secondary injection test and the
        // trip-circuit supervision must both catch it - then restore it and the chain must test healthy again
        int ieds = 0;
        for (Map.Entry<Long, com.dogpound.railmap.grid.GridData.Node> en : com.dogpound.railmap.grid.GridData.get(world).nodes().entrySet()) {
            if (ieds >= 6 || en.getValue().kind != com.dogpound.railmap.grid.GridKind.IED) continue;
            BlockPos ip = BlockPos.fromLong(en.getKey());
            if (ip.distanceSq(pos) > r2) continue;
            ieds++;
            steps.add(new Step("TRIP CIRCUIT IED " + RouteCheck.xz(ip), () -> inj(FaultLab.Kind.TRIP_WIRING, ip, "lab sequence"), () -> {
                Object[] t = com.dogpound.railmap.grid.Bench.test(world, ip);
                if ((Boolean) t[0]) return "secondary injection PASSED with the trip circuit open";
                boolean tcs = false;
                for (com.dogpound.railmap.alarm.AlarmManager.Alarm a : com.dogpound.railmap.alarm.AlarmManager.get(world).all()) if (a.active && a.key.equals("tcs:" + ip.toLong())) tcs = true;
                return tcs ? null : "no trip-circuit supervision alarm";
            }, () -> FaultLab.clear(world, FaultLab.Kind.TRIP_WIRING, ip), () -> {
                Object[] t = com.dogpound.railmap.grid.Bench.test(world, ip);
                if ((Boolean) t[0]) return null;
                @SuppressWarnings("unchecked") List<String> l = (List<String>) t[1];
                for (String x : l) if (x.startsWith("FAIL")) return "chain still fails: " + x.substring(6);
                return "chain still fails";
            }));
        }
        // the whole zone's 12 V: backup power / fail-safe test
        List<TileEntity> zoneGear = new ArrayList<>();
        steps.add(new Step("12 V SUPPLY CUT (zone)", () -> {
            zoneGear.clear();
            for (TileEntity t : new ArrayList<>(world.loadedTileEntityList))
                if (t instanceof com.dogpound.railmap.wire.IWiredGear g && !(t instanceof TileFaultLab) && t.getPos().distanceSq(pos) <= r2 && g.term().powered()) zoneGear.add(t);
            for (TileAxleEvaluator e : SafetyTicker.evaluators(world)) if (e.getPos().distanceSq(pos) <= r2) evalSnap.putIfAbsent(e.getPos().toLong(), e.snapshot());
            inj(FaultLab.Kind.POWER_CUT, pos, "lab sequence");
        }, () -> {
            int ok = 0;
            List<String> bad = new ArrayList<>();
            for (TileEntity t : zoneGear) {
                String why = null;
                if (t instanceof TileSignalMast m) why = stopish(m.aspect()) ? null : "signal " + SignalSighting.name(m) + " " + m.aspect().label;
                else if (t instanceof TileTrackCircuit tc) why = tc.occupied() ? null : "track circuit " + RouteCheck.xz(t.getPos()) + " reads clear";
                else if (t instanceof TileCrossing c) why = c.term().powered() ? "crossing still powered" : null;
                else if (t instanceof TileEmergencyPlunger p) why = p.operated() || !FailSafe.safe(world, FailSafe.ESP_WIRING) ? null : "plunger not treated as operated";
                else if (t instanceof TileRadioMast r) why = r.healthy() ? "radio mast still on air" : null;
                else if (t instanceof TileAxleEvaluator e) { for (int i = 0; i < TileAxleEvaluator.N; i++) if (e.sec[i].state != 4 && e.sec[i].state != 2) why = "axle section " + (i + 1) + " not disturbed"; }
                if (why == null) ok++; else bad.add(why);
            }
            lines.add("   " + ok + "/" + zoneGear.size() + " items in their safe state with no 12 V");
            return bad.isEmpty() ? null : String.join("; ", bad.subList(0, Math.min(3, bad.size()))) + (bad.size() > 3 ? " +" + (bad.size() - 3) : "");
        }, () -> FaultLab.clear(world, FaultLab.Kind.POWER_CUT, null), () -> {
            for (Map.Entry<Long, int[][]> en : evalSnap.entrySet()) if (world.getTileEntity(BlockPos.fromLong(en.getKey())) instanceof TileAxleEvaluator e) e.restore(en.getValue());
            evalSnap.clear();
            for (TileEntity t : zoneGear) if (t instanceof com.dogpound.railmap.wire.IWiredGear g && !g.term().powered()) return g.wiredName() + " did not come back after the supply returned";
            return null;
        }));
        stepAt = 0;
        stepT = world.getTotalWorldTime();
        lines.add("SEQUENCE " + Recorder.clock(world, stepT) + " zone " + zone() + "m: " + steps.size() + " tests");
        Recorder.event(world, "FAULT LAB: automated sequence started by " + who(who) + " (" + steps.size() + " tests)");
        lastInject = "";
        steps.get(0).inject.run();
        return "§6Automated test sequence: " + steps.size() + " tests, about " + steps.size() * 4 + " s";
    }

    private String lastInject = "";

    /** a sequence injection; a refusal (outside the zone, wrong target) makes that test a SKIP, not a FAIL */
    private void inj(FaultLab.Kind k, BlockPos p, String who) { lastInject = FaultLab.inject(world, k, p, who); }

    private static boolean stopish(Aspect a) { return a.isStop() || a == Aspect.DARK; }

    private boolean trainNear(BlockPos p, double r) {
        for (TrainNode t : TrainTracker.latest(world)) if (Geo.d2(t.x, t.z, p.getX() + 0.5, p.getZ() + 0.5) <= r * r) return true;
        return false;
    }

    /** inject at 0, check at 2 s, restore, check recovery at 4 s, next */
    private void runStep() {
        long dt = world.getTotalWorldTime() - stepT;
        Step s = steps.get(stepAt);
        if (dt == 40 && lastInject.startsWith("§c")) {
            lines.add("SKIP " + s.name + ": " + lastInject.replaceAll("§.", ""));
            s.restore.run();
            next();
            return;
        }
        if (dt == 40) {
            String bad;
            try { bad = s.check.get(); } catch (RuntimeException ex) { bad = "check crashed: " + ex; }
            if (bad == null) { pass++; lines.add("PASS " + s.name); } else { fail++; lines.add("FAIL " + s.name + ": " + bad); }
            s.restore.run();
        } else if (dt == 80) {
            String bad;
            try { bad = s.after.get(); } catch (RuntimeException ex) { bad = "check crashed: " + ex; }
            if (bad != null) { fail++; lines.add("FAIL " + s.name + " recovery: " + bad); }
            next();
        }
    }

    private void next() {
        stepAt++;
        stepT = world.getTotalWorldTime();
        if (stepAt >= steps.size()) { finish(); return; }
        lastInject = "";
        steps.get(stepAt).inject.run();
    }

    private void finish() {
        stepAt = -1;
        FaultLab.clearAll(world, "sequence finished");
        SafetyData d = SafetyData.get(world);
        lines.add("RESULT: " + pass + " PASS, " + fail + " FAIL" + (fail == 0 ? " - the signalling fails safe" : " - see the FAIL lines"));
        d.report.clear();
        d.report.addAll(lines);
        d.reportPass = pass;
        d.reportFail = fail;
        d.reportAt = world.getTotalWorldTime();
        d.markDirty();
        Recorder.event(world, "FAULT LAB: sequence finished - " + pass + " pass, " + fail + " fail");
        world.playSound(null, pos, fail == 0 ? com.dogpound.railmap.sound.PackSounds.ev("ui.beep_ok") : com.dogpound.railmap.sound.PackSounds.ev("ui.beep_error"), SoundCategory.BLOCKS, 0.6f, 1.0f);
        controlsChanged();
    }

    private void abort(String why) {
        if (running()) {
            Step s = steps.get(stepAt);
            try { s.restore.run(); } catch (RuntimeException ignored) { }
            lines.add("ABORTED: " + why);
            stepAt = -1;
            SafetyData d = SafetyData.get(world);
            d.report.clear(); d.report.addAll(lines); d.reportPass = pass; d.reportFail = fail + 1; d.reportAt = world.getTotalWorldTime();
        }
        for (Map.Entry<Long, int[][]> en : evalSnap.entrySet()) if (world.getTileEntity(BlockPos.fromLong(en.getKey())) instanceof TileAxleEvaluator e) e.restore(en.getValue());
        evalSnap.clear();
        FaultLab.clearAll(world, why);
        if (why.startsWith("TEST ABORTED")) {
            SafetyData d = SafetyData.get(world);
            d.testMode = false;
            d.labPos = 0;
            ctl.set("k_test", 0);
            d.markDirty();
        }
        Recorder.event(world, "FAULT LAB: " + why);
    }

    /** the report as a written book out of the printer slot */
    private void print(EntityPlayer who) {
        SafetyData d = SafetyData.get(world);
        NBTTagList pages = new NBTTagList();
        List<String> all = new ArrayList<>();
        all.add("§lFAULT LAB REPORT§r");
        all.add(Recorder.clock(world, world.getTotalWorldTime()) + " console " + pos.getX() + "," + pos.getZ());
        all.add(d.reportAt == 0 ? "No sequence run yet" : "§l" + d.reportPass + " PASS, " + d.reportFail + " FAIL§r");
        all.add("");
        all.addAll(d.report);
        all.add("");
        all.add("§lFAULTS IN NOW§r");
        if (d.faults.isEmpty()) all.add("none");
        for (SafetyData.Fault f : d.faults) all.add(f.what);
        all.add("");
        all.add("§lLAST ROUTE DECISIONS§r");
        for (int i = 0; i < Math.min(3, d.decisions.size()); i++) {
            SafetyData.Decision dec = d.decisions.get(i);
            all.add((dec.granted ? "GRANTED " : "REFUSED ") + dec.route);
            for (String l : dec.lines) if (l.startsWith("-")) all.add(" " + l);
        }
        StringBuilder p = new StringBuilder();
        int n = 0;
        for (String l : all) {
            String line = l.length() > 60 ? l.substring(0, 59) + "." : l;
            if (n >= 9) { pages.appendTag(new NBTTagString(ITextComponent.Serializer.componentToJson(new TextComponentString(p.toString())))); p = new StringBuilder(); n = 0; }
            p.append(line).append('\n');
            n += 1 + line.length() / 22;
        }
        pages.appendTag(new NBTTagString(ITextComponent.Serializer.componentToJson(new TextComponentString(p.toString()))));
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        NBTTagCompound t = new NBTTagCompound();
        t.setString("title", "Fault Lab Report " + Recorder.clock(world, world.getTotalWorldTime()));
        t.setString("author", "Fault Lab");
        t.setTag("pages", pages);
        book.setTagCompound(t);
        EnumFacing f = controlFacing();
        EntityItem e = new EntityItem(world, pos.getX() + 0.5 + f.getXOffset() * 0.7, pos.getY() + 1.1, pos.getZ() + 0.5 + f.getZOffset() * 0.7, book);
        e.motionX = f.getXOffset() * 0.05; e.motionZ = f.getZOffset() * 0.05;
        world.spawnEntity(e);
        world.playSound(null, pos, com.dogpound.railmap.ModSounds.PRINTER, SoundCategory.BLOCKS, 0.2f, 1.0f);
        Recorder.event(world, "FAULT LAB: report printed" + (who == null ? "" : " by " + who.getName()));
    }

    @Override protected void save(NBTTagCompound t) { t.setLong("target", target); }
    @Override protected void load(NBTTagCompound t) { target = t.getLong("target"); }
}
