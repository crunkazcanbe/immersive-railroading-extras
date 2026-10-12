package com.dogpound.railmap.safety;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.graph.TrainNode;
import com.dogpound.railmap.server.TrainTracker;
import com.dogpound.railmap.signal.SignalRegistry;
import com.dogpound.railmap.signal.TileCrossing;
import com.dogpound.railmap.signal.TileRelayCase;
import com.dogpound.railmap.signal.TileSignalMast;
import com.dogpound.railmap.signal.TileTrackCircuit;
import com.dogpound.railmap.wire.IWiredGear;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Signal and sensor self-testing (her ChatGPT list #22). Every few minutes (and on demand: Fault Lab SELF-TEST,
 * {@code /irextras safety selftest}) every piece of signalling gear is tested the way its own diagnostics would:
 * <ul>
 *   <li>LAMP - each signal's lamp circuit: supply present, filament / LED proven (a failed lamp fails);</li>
 *   <li>RELAY - relay cases: the relay supply is up;</li>
 *   <li>DETECTOR - track circuits: supply up, relay not dropped, and an OCCUPIED reading with no train anywhere near
 *   it is reported as a suspected false occupancy; axle heads healthy; axle sections not disturbed;</li>
 *   <li>COMMS - radio masts on air; desks, telephones, plungers, describers, banners and crossings powered.</li>
 * </ul>
 * Each result goes into that gear's maintenance history (with every repair, reset and injected fault). A FAIL raises a
 * standing alarm until it passes again; gear that keeps flipping between pass and fail is flagged INTERMITTENT - the
 * kind of fault that is never there when the technician arrives.
 */
public final class SelfTest {
    private SelfTest() {}

    /** test everything now; returns pass, fail, intermittent counts */
    public static int[] run(World w, String why) {
        SafetyData d = SafetyData.get(w);
        d.lastSelfTest = w.getTotalWorldTime();
        int pass = 0, fail = 0, inter = 0;
        List<Object[]> items = new ArrayList<>();                // te, kind, verdict (null = pass)
        List<TrainNode> trains = TrainTracker.latest(w);
        for (TileSignalMast m : SignalRegistry.masts(w.provider.getDimension())) {
            if (m.isInvalid()) continue;
            String v = !m.term().powered() ? "lamp circuit dead - " + m.term().what : m.term().burnt ? "burnt out" : FaultLab.has(w, FaultLab.Kind.LAMP, m.getPos()) ? "LAMP FAILED - no current through the lamp" : null;
            items.add(new Object[]{m, "signal lamp", v});
        }
        for (TileEntity t : new ArrayList<>(w.loadedTileEntityList)) {
            if (t.isInvalid() || !(t instanceof IWiredGear g)) continue;
            String kind = null, v = null;
            if (t instanceof TileTrackCircuit tc) {
                kind = "track circuit";
                if (!g.term().powered()) v = "detector supply dead - " + g.term().what;
                else if (FaultLab.has(w, FaultLab.Kind.TC_DROP, t.getPos())) v = "track relay dropped";
                else if (tc.occupied()) {
                    boolean near = false;
                    for (TrainNode n : FaultLab.withPhantoms(w, trains)) if (Geo.d2(n.x, n.z, t.getPos().getX() + 0.5, t.getPos().getZ() + 0.5) <= Math.pow(tc.range() + 4, 2)) { near = true; break; }
                    if (!near) v = "shows OCCUPIED with no train near - false occupancy suspected";
                }
            } else if (t instanceof TileRelayCase) { kind = "relay case"; if (!g.term().powered()) v = "relay supply dead"; }
            else if (t instanceof TileCrossing) { kind = "level crossing"; if (!g.term().powered()) v = "crossing controller dead (barriers down)"; }
            else if (t instanceof TileAxleHead h) { kind = "axle counter head"; if (!h.healthy()) v = h.gearState(); }
            else if (t instanceof TileAxleEvaluator e) {
                kind = "axle evaluator";
                if (!e.powered()) v = "evaluator dead";
                else for (int i = 0; i < TileAxleEvaluator.N; i++) if (e.sec[i].state == 2) { v = "section " + (i + 1) + " disturbed: " + e.sec[i].why; break; }
            }
            else if (t instanceof TileRadioMast r) { kind = "radio mast"; if (!r.healthy()) v = "off air - " + r.gearState(); }
            else if (t instanceof SafetyTile s && !(t instanceof TileFaultLab)) { kind = "comms / lineside"; if (!s.powered()) v = "no 12 V - " + g.term().what; }
            if (kind != null) items.add(new Object[]{t, kind, v});
        }
        long now = w.getTotalWorldTime();
        AlarmManager am = AlarmManager.get(w);
        for (Object[] it : items) {
            TileEntity t = (TileEntity) it[0];
            IWiredGear g = (IWiredGear) t;
            String v = (String) it[2];
            SafetyData.Gear r = d.gear(t.getPos(), g.wiredName(), (String) it[1]);
            boolean failed = v != null;
            if (r.wasBurnt && !g.term().burnt) r.note(now, "REPAIRED - new electronics fitted");
            r.wasBurnt = g.term().burnt;
            int before = r.state;
            r.tests++;
            r.recent = (r.recent << 1) | (failed ? 1 : 0);
            if (failed) r.fail++; else r.pass++;
            r.state = failed ? 2 : 1;
            r.last = failed ? v : "ok";
            String key = "selftest:" + t.getPos().toLong();
            if (failed && before != 2) {
                r.note(now, "SELF-TEST FAIL: " + v);
                am.raise(w, key, AlarmManager.Cat.SIGNAL, AlarmManager.Sev.MAJOR, "Self-test FAIL: " + g.wiredName() + " at " + t.getPos().getX() + "," + t.getPos().getZ() + " - " + v, t.getPos(), true);
            } else if (!failed && before == 2) {
                r.note(now, "self-test pass - back to normal");
                am.clear(key);
            }
            boolean was = r.intermittent;
            r.intermittent = r.flips() >= 3;
            if (r.intermittent && !was) {
                r.note(now, "INTERMITTENT - " + Integer.bitCount(r.recent & 0xFFFF) + " of the last " + Math.min(16, r.tests) + " tests failed");
                am.raise(w, "intermittent:" + t.getPos().toLong(), AlarmManager.Cat.SIGNAL, AlarmManager.Sev.MINOR,
                        "INTERMITTENT fault: " + g.wiredName() + " at " + t.getPos().getX() + "," + t.getPos().getZ() + " (" + r.flips() + " pass/fail changes)", t.getPos(), false);
            }
            if (failed) fail++; else pass++;
            if (r.intermittent) inter++;
        }
        d.markDirty();
        Recorder.event(w, "SELF-TEST (" + why + "): " + pass + " pass, " + fail + " fail, " + inter + " intermittent");
        return new int[]{pass, fail, inter};
    }
}
