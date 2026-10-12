package com.dogpound.railmap.incident;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Correlator;
import com.dogpound.railmap.alarm.Recorder;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * What the Control Center's OPS tab shows, packed as rows of strings into PacketRailState (row[0] = which table):
 * I incidents · IL / IP / IR the chosen one's timeline / plan / final report · G / GC alarm root-cause groups ·
 * B / BT / BE train recorders, the chosen one's trace and events · WO work orders · PA announcement log ·
 * K / K7 performance today / last 7 days · W* digital twin · R / RT reports · S / SR scenario.
 */
public final class IncidentConsoleData {
    private IncidentConsoleData() {}

    private static final Map<Integer, Integer> SEL_INC = new HashMap<>(), SEL_REP = new HashMap<>();
    private static final Map<Integer, String> SEL_TRAIN = new HashMap<>();

    public static void fill(World w, List<String[]> out) {
        int dim = w.provider.getDimension();
        long now = w.getTotalWorldTime();
        IncidentLog log = IncidentLog.get(w);
        // incidents
        int sel = SEL_INC.getOrDefault(dim, 0);
        IncidentLog.Rec chosen = null;
        int n = 0;
        for (IncidentLog.Rec r : log.all()) {
            if (n++ >= 24) break;
            if (sel == 0 && r.open() && chosen == null || r.id == sel) chosen = r;
            int open = 0;
            for (IncidentLog.Sec s : r.secs) if (!s.released) open++;
            out.add(new String[]{"I", String.valueOf(r.id), r.type.label, r.stage.name().replace('_', ' '), String.valueOf(r.level), r.what, (int) r.x + "," + (int) r.z,
                    IncidentLog.mins((r.closed == 0 ? now : r.closed) - r.opened), r.onScene == 0 ? "-" : IncidentLog.mins(r.onScene - r.opened), r.needLine(), r.haz,
                    String.valueOf(r.paxAffected), String.valueOf(r.paxAccounted), String.valueOf(r.services), r.commander, open + "/" + r.secs.size(), r.evac ? "1" : "0",
                    r.rescueName.isEmpty() ? "" : r.rescueName + " " + r.failKind + " " + r.towKmh + "km/h" + (r.coupled ? " COUPLED" : "") + (r.stabled ? " STABLED" : "")});
        }
        if (chosen != null) {
            out.add(new String[]{"IS", String.valueOf(chosen.id)});
            for (int i = chosen.log.size() - 1, k = 0; i >= 0 && k < 40; i--, k++) {
                String[] p = chosen.log.get(i).split("\\|", 2);
                out.add(new String[]{"IL", Recorder.clock(w, Long.parseLong(p[0])), p.length > 1 ? p[1] : ""});
            }
            for (String s : chosen.plan) out.add(new String[]{"IP", s});
            for (int i = 0; i < Math.min(30, chosen.blackbox.size()); i++) out.add(new String[]{"IB", chosen.blackbox.get(i)});
            for (IncidentLog.Sec s : chosen.secs) out.add(new String[]{"IX", s.name, String.valueOf((int) s.r), s.released ? "OPEN " + Recorder.clock(w, s.releasedAt) : "CLOSED"});
            if (!chosen.open()) for (String s : chosen.report) out.add(new String[]{"IR", s});
        }
        // alarm correlation
        AlarmManager am = AlarmManager.get(w);
        int g = 0;
        for (List<AlarmManager.Alarm> grp : Correlator.groups(am, now)) {
            if (g++ >= 12) break;
            AlarmManager.Alarm root = grp.get(0);
            out.add(new String[]{"G", String.valueOf(root.id), root.text, root.cat.tile.replace("|", " "), root.sev.name(), String.valueOf(grp.size() - 1),
                    String.valueOf(root.seq(now)), String.valueOf((now - root.first) / 20)});
            for (int i = 1; i < Math.min(grp.size(), 16); i++) {
                AlarmManager.Alarm c = grp.get(i);
                out.add(new String[]{"GC", String.valueOf(root.id), String.valueOf(c.id), c.text, c.cat.tile.replace("|", " "), String.valueOf(c.seq(now)), "+" + (c.first - root.first) / 20 + "s"});
            }
        }
        out.add(new String[]{"GN", String.valueOf(Correlator.consequential(am, now)), String.valueOf(am.count(1, now) + am.count(2, now) + am.count(3, now))});
        // train recorders
        Map<String, TrainRecorder.Box> boxes = TrainRecorder.boxes(w);
        String ts = SEL_TRAIN.get(dim);
        if ((ts == null || !boxes.containsKey(ts)) && !boxes.isEmpty()) ts = boxes.keySet().iterator().next();
        out.add(new String[]{"BR", String.valueOf(TrainRecorder.period(w)), String.valueOf(IncidentConfig.recorderMinutes), ts == null ? "" : ts});
        for (TrainRecorder.Box b : boxes.values()) {
            TrainRecorder.Sample l = b.samples.peekLast();
            out.add(new String[]{"B", b.id, b.name, String.valueOf(b.samples.size()), String.valueOf(b.events.size()), l == null ? "0" : String.valueOf(Math.round(l.kmh)),
                    l == null ? "" : Math.round(l.x) + "," + Math.round(l.z)});
        }
        TrainRecorder.Box tb = ts == null ? null : boxes.get(ts);
        if (tb != null) {
            List<TrainRecorder.Sample> ss = new ArrayList<>(tb.samples);
            int step = Math.max(1, ss.size() / 150);
            for (int i = 0; i < ss.size(); i += step) {
                TrainRecorder.Sample s = ss.get(i);
                out.add(new String[]{"BT", String.valueOf((now - s.tick) / 20), String.valueOf(Math.round(s.kmh)), String.valueOf(Math.round(s.throttle * 100)), String.valueOf(Math.round(s.brake * 100)), String.valueOf(s.kw), s.signal});
            }
            List<String> ev = new ArrayList<>(tb.events);
            for (int i = ev.size() - 1, k = 0; i >= 0 && k < 40; i--, k++) {
                String[] p = ev.get(i).split("\\|", 2);
                out.add(new String[]{"BE", Recorder.clock(w, Long.parseLong(p[0])), p[1]});
            }
        }
        // work orders
        int wo = 0;
        for (IncidentLog.WorkOrder o : log.orders) {
            if (wo++ >= 40) break;
            BlockPos p = BlockPos.fromLong(o.pos);
            out.add(new String[]{"WO", String.valueOf(o.id), o.gear, p.getX() + "," + p.getZ(), o.fault, o.by, o.done == 0 ? "OPEN" : "DONE", o.closedWhy, o.local ? "1" : "0", Recorder.clock(w, o.raised)});
        }
        // announcements
        int pa = 0;
        for (Announcer.Said s : Announcer.log(w)) { if (pa++ >= 40) break; out.add(new String[]{"PA", Recorder.clock(w, s.tick), s.station, s.kind, s.text, String.valueOf(s.speakers)}); }
        // performance
        Analytics an = Analytics.get(w);
        for (String[] k : Analytics.kpis(an.today(w))) out.add(new String[]{"K", k[0], k[1]});
        List<Map<String, Double>> wk = new ArrayList<>(an.days().tailMap(Analytics.day(w) - 6).values());
        for (String[] k : Analytics.kpis(Analytics.merge(wk))) out.add(new String[]{"K7", k[0], k[1]});
        // digital twin
        Twin.fill(w, out);
        // reports
        List<String[]> reps = an.reports();
        int rs = Math.max(0, Math.min(reps.size() - 1, SEL_REP.getOrDefault(dim, 0)));
        for (int i = 0; i < reps.size(); i++) out.add(new String[]{"R", String.valueOf(i), reps.get(i)[0].length() > 2 ? reps.get(i)[0].substring(2) : reps.get(i)[0], i == rs ? "1" : "0"});
        if (!reps.isEmpty()) for (int i = 1; i < reps.get(rs).length; i++) out.add(new String[]{"RT", reps.get(rs)[i]});
        // scenario
        NBTTagCompound snap = an.snapshot;
        out.add(new String[]{"S", snap.hasKey("trains") ? "1" : "0", snap.hasKey("tick") ? IncidentLog.mins(now - snap.getLong("tick")) + " ago" : "-",
                String.valueOf(snap.getTagList("lines", 10).tagCount()), String.valueOf(snap.getTagList("trains", 10).tagCount()), Scenario.describe(w), snap.getString("resFor"),
                String.valueOf(IncidentConfig.scenarioMinutes)});
        NBTTagList res = snap.getTagList("res", 10);
        for (int i = 0; i < res.tagCount(); i++) { NBTTagCompound c = res.getCompoundTagAt(i); out.add(new String[]{"SR", c.getString("w"), c.getString("a"), c.getString("b"), c.getString("d")}); }
        try { GradedDrills.rows(w, out); } catch (RuntimeException ignored) { }                // infra #47 graded drills
    }

    /** the console's buttons: 1 pick incident, 2 order "id:order", 3 pick recorder, 4 recorder rate, 5 ACK group, 6 pick report,
     *  7 report now, 8 scenario save, 9 scenario set "what:delta", 10 scenario run */
    public static String op(World w, int op, String arg, String who) {
        int dim = w.provider.getDimension();
        try {
            switch (op) {
                case 1: SEL_INC.put(dim, Integer.parseInt(arg)); return null;
                case 2: { String[] p = arg.split(":", 2); return IncidentOps.order(w, Integer.parseInt(p[0]), p[1], who + " (console)"); }
                case 3: SEL_TRAIN.put(dim, arg); return null;
                case 4: return "Train recorders sample every " + TrainRecorder.cyclePeriod(w) + " s";
                case 5: return Correlator.ackGroup(AlarmManager.get(w), Integer.parseInt(arg), who) + " alarm(s) acknowledged (root + consequences)";
                case 6: SEL_REP.put(dim, Integer.parseInt(arg)); return null;
                case 7: {
                    List<String[]> reps = Analytics.get(w).reports();
                    reps.add(0, Reports.now(w));
                    int k = 0;
                    for (Iterator<String[]> it = reps.iterator(); it.hasNext(); ) if (it.next()[0].startsWith("N") && ++k > 5) it.remove();
                    Analytics.get(w).markDirty();
                    SEL_REP.put(dim, 0);
                    return "Report generated";
                }
                case 8: return Scenario.save(w);
                case 9: { String[] p = arg.split(":", 2); return Scenario.set(w, p[0], Integer.parseInt(p[1])); }
                case 10: return Scenario.run(w);
                case 11: return GradedDrills.run(w, Integer.parseInt(arg), null);     // infra #47
                default: return null;
            }
        } catch (RuntimeException e) {
            return null;
        }
    }
}
