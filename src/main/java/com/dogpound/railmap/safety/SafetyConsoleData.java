package com.dogpound.railmap.safety;

import com.dogpound.railmap.alarm.Recorder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.Map;

/**
 * What the Control Center's SAFETY tab shows, packed as rows of strings into PacketRailState (row[0] = which table).
 * T train tracking · C comms log · A axle sections · H signal holds · S sighting · G self-test / maintenance ·
 * F fail-safe modes · D route decisions · I incidents · L lab state · R lab report · X injected faults
 */
public final class SafetyConsoleData {
    private SafetyConsoleData() {}

    public static void fill(World w, List<String[]> out) {
        SafetyData d = SafetyData.get(w);
        long now = w.getTotalWorldTime();
        for (SafetyData.TrainRec r : d.trains.values()) {
            if (r.gone || out.size() > 60) continue;
            out.add(new String[]{"T", r.headcode, String.valueOf(r.radioId), r.names, String.valueOf(r.cars), String.valueOf(r.axles), String.valueOf(Math.round(r.lengthM)),
                    String.valueOf(Math.round(r.x)), String.valueOf(Math.round(r.z)), String.valueOf(Math.round(Math.abs(r.speed))), r.berth, r.dest, r.route,
                    String.valueOf(r.radio), String.valueOf(r.rssi), r.mast, String.valueOf(r.integrity), r.integrityWhy, r.driverless ? "1" : "0", r.id,
                    r.detachedFrom, r.braked || r.held ? "1" : "0", r.radio == 1 ? String.valueOf((now - r.lastReport) / 20) : ""});
        }
        for (int i = 0; i < Math.min(40, d.comms.size()); i++) {
            SafetyData.Comm c = d.comms.get(i);
            out.add(new String[]{"C", String.valueOf(c.id), Recorder.clock(w, c.tick), c.from, c.to, c.text, String.valueOf(c.ack), c.ackBy, String.valueOf(c.kind)});
        }
        for (TileAxleEvaluator e : SafetyTicker.evaluators(w))
            for (int i = 0; i < TileAxleEvaluator.N; i++) {
                TileAxleEvaluator.Section s = e.sec[i];
                if (s.state == 4) continue;
                out.add(new String[]{"A", e.getPos().getX() + "," + e.getPos().getZ(), String.valueOf(i + 1), e.powered() ? TileAxleEvaluator.STATE[s.state] : "NO 12 V", String.valueOf(s.count),
                        s.why, String.valueOf(s.heads.size()), String.valueOf(s.signals.size()), String.valueOf(s.inTotal), String.valueOf(s.outTotal)});
            }
        for (Map.Entry<Long, String> h : SafetyHolds.all(w).entrySet()) out.add(new String[]{"H", Geo.xz(h.getKey()), h.getValue()});
        double req = SignalSighting.required(w);
        int sr = 0;
        for (SignalSighting.Result r : SignalSighting.all(w).values())
            if (sr++ < 120) { double rq = SignalSighting.requiredAt(w, BlockPos.fromLong(r.pos));   // batch-3 #16: each signal's own line speed
                out.add(new String[]{"S", r.name, Geo.xz(r.pos), String.valueOf(Math.round(SignalSighting.sightNow(w, r))), String.valueOf(Math.round(rq)), r.obstruction,
                    r.banner > 0 ? String.valueOf(Math.round(r.banner)) : "", String.valueOf(Math.round(Math.max(0, rq - SignalSighting.sightNow(w, r))))}); }
        int g = 0;
        for (SafetyData.Gear x : d.gear.values()) {
            if (g++ > 80) break;
            String last = x.history.isEmpty() ? "" : x.history.get(0);
            if (!last.isEmpty()) { String[] p = last.split("\\|", 2); last = Recorder.clock(w, Long.parseLong(p[0])) + " " + p[1]; }
            out.add(new String[]{"G", x.name, x.kind, Geo.xz(x.pos), String.valueOf(x.state), String.valueOf(x.pass), String.valueOf(x.fail), x.intermittent ? "1" : "0", x.last, last, String.valueOf(x.flips())});
        }
        for (FailSafe f : FailSafe.values())
            out.add(new String[]{"F", String.valueOf(f.ordinal()), f.name(), f.label, String.valueOf(FailSafe.mode(w, f)), String.join("|", f.options), f.critical ? "1" : "0"});
        for (int i = 0; i < Math.min(12, d.decisions.size()); i++) {
            SafetyData.Decision dec = d.decisions.get(i);
            out.add(new String[]{"D", Recorder.clock(w, dec.tick), dec.route, dec.granted ? "1" : "0", String.join("\n", dec.lines)});
        }
        for (int i = 0; i < Math.min(10, d.incidents.size()); i++) {
            SafetyData.Incident in = d.incidents.get(i);
            out.add(new String[]{"I", String.valueOf(in.id), in.where, in.by, String.valueOf(in.steps), Recorder.clock(w, in.opened), in.closed == 0 ? "" : (in.closed - in.opened) / 20 + " s"});
        }
        out.add(new String[]{"L", d.testMode ? "1" : "0", d.labPos == 0 ? "" : Geo.xz(d.labPos), String.valueOf(d.labRadius), String.valueOf(d.faults.size()),
                String.valueOf(d.reportPass), String.valueOf(d.reportFail), d.reportAt == 0 ? "" : Recorder.clock(w, d.reportAt),
                String.valueOf(d.sightKmh), String.valueOf(d.sightSeconds), String.valueOf(d.overlap), String.valueOf(d.selfTestMinutes),
                d.lastSelfTest == 0 ? "never" : (now - d.lastSelfTest) / 1200 + " min ago"});
        for (String r : d.report) out.add(new String[]{"R", r});
        for (SafetyData.Fault f : d.faults) out.add(new String[]{"X", f.what, String.valueOf((now - f.since) / 20)});
        SigConsole.fill(w, out);                                                        // signalling batch 3 pages
    }

    /** the console's buttons: 1 cycle a fail-safe mode, 2 self-test now, 3 lab clear, 4 confirm integrity, 5 release a train, 6 answer the oldest call */
    public static String op(World w, int op, String arg, String who) {
        SafetyData d = SafetyData.get(w);
        switch (op) {
            case 1: {
                FailSafe f = FailSafe.byName(arg);
                if (f == null) return null;
                return SafetyCommand.setFailSafe(w, f, (FailSafe.mode(w, f) + 1) % f.options.length, who);
            }
            case 2: { int[] r = SelfTest.run(w, "console, " + who); return "Self-test: " + r[0] + " pass, " + r[1] + " fail, " + r[2] + " intermittent"; }
            case 3: FaultLab.clearAll(w, "console, " + who); return "Faults cleared";
            case 4: { SafetyData.TrainRec r = d.train(arg); if (r != null) ConsistWatch.restore(w, d, r, "confirmed from the console by " + who); return r == null ? null : "Integrity confirmed: " + r.headcode; }
            case 5: {
                SafetyData.TrainRec r = d.train(arg);
                if (r == null) return null;
                RadioNet.release(w, r);
                d.log(w, "CONSOLE (" + who + ")", r.headcode, "train control brake / hold released", 2, 3);
                return "Released " + r.headcode;
            }
            case 6: {
                SafetyData.Comm oldest = null;
                for (SafetyData.Comm c : d.comms) if (c.kind == 1 && c.ack == 0) oldest = c;
                if (oldest == null) return "No call waiting";
                oldest.ack = 1; oldest.ackBy = "CONSOLE (" + who + ")"; oldest.ackTick = w.getTotalWorldTime();
                com.dogpound.railmap.alarm.AlarmManager.get(w).clear("call:" + oldest.id);
                for (SafetyData.Incident in : d.incidents) if (in.closed == 0 && in.pos == oldest.pos) in.steps |= 4;
                d.log(w, "CONSOLE (" + who + ")", oldest.from, "emergency call answered", 2, 3);
                return "Answered " + oldest.from;
            }
            default: return SigConsole.op(w, op, arg, who);                            // signalling batch 3 (ops 20-99)
        }
    }

    static String pos(BlockPos p) { return p.getX() + "," + p.getZ(); }
}
