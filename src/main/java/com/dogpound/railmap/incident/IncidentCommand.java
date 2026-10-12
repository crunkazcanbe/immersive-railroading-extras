package com.dogpound.railmap.incident;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Correlator;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** /irextras incident ... : test and inspect incident command, rescue, fire, evacuation, recorders, analytics, scenarios */
public final class IncidentCommand {
    private IncidentCommand() {}

    public static final String USAGE = "incident <list|show id|order id accept|level|crew|rescue|fire|medical|protect|isolate|evac|allclear|reopen|close"
            + "|assist|evac [radius]|fire [radius]|rescue id|recorder [train]|rca|kpi|report|twin|scenario save|run|set what delta|pa station text|wo>";

    public static void run(ICommandSender s, World w, String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "list";
        IncidentLog log = IncidentLog.get(w);
        String who = s.getName();
        Vec3d at = new Vec3d(s.getPositionVector().x, s.getPositionVector().y, s.getPositionVector().z);
        switch (sub) {
            case "list":
                if (log.all().isEmpty()) say(s, "No incidents recorded");
                for (IncidentLog.Rec r : log.all())
                    say(s, (r.open() ? "§c" : "§7") + "#" + r.id + " " + r.type.label + " L" + r.level + " " + r.stage + "§r " + r.what + " @" + (int) r.x + "," + (int) r.z + " need " + r.needLine());
                return;
            case "show": {
                IncidentLog.Rec r = a.length > 2 ? log.byId(Integer.parseInt(a[2])) : null;
                if (r == null) { say(s, "Usage: incident show <id>"); return; }
                for (String l : r.open() ? IncidentLog.finalReport(w, r) : r.report) say(s, l);
                for (String l : r.plan) say(s, "§bPLAN§r " + l);
                return;
            }
            case "order":
                if (a.length < 4) { say(s, "incident order <id> <" + String.join("|", IncidentOps.ORDERS) + ">"); return; }
                say(s, String.valueOf(IncidentOps.order(w, Integer.parseInt(a[2]), a[3].toLowerCase(Locale.ROOT), who)));
                return;
            case "assist": {                                   // the crew of the nearest train asks for assistance
                Locomotive best = null;
                double bd = 24 * 24;
                for (Locomotive l : cam72cam.mod.world.World.get(w).getEntities(Locomotive.class)) {
                    Vec3d p = l.getPosition();
                    double d = (p.x - at.x) * (p.x - at.x) + (p.z - at.z) * (p.z - at.z);
                    if (d < bd) { bd = d; best = l; }
                }
                if (best == null) { say(s, "No locomotive within 24 blocks"); return; }
                Vec3d p = best.getPosition();
                String name = best.tag != null && !best.tag.isEmpty() ? best.tag : best.getDefinition() == null ? "train" : best.getDefinition().name();
                IncidentLog.Rec r = log.open(w, IncidentLog.Type.FAILED, name + " requests assistance (crew report)", p.x, p.y, p.z, 12, 1);
                for (EntityCoupleableRollingStock c : best.getTrain()) r.stock.add(c.getUUID().toString());
                r.needs |= IncidentLog.RESCUE;
                r.blackbox.addAll(TrainRecorder.download(w, best.getUUID().toString(), 120));
                log.section(w, r, "line:" + r.id, "failed train", p.x, p.y, p.z, 12);
                Analytics.count(w, "breakdowns", 1);
                Analytics.count(w, "incidents", 1);
                say(s, "Incident #" + r.id + " opened. " + Rescue.assign(w, log, r, who));
                for (String l : r.plan) say(s, "§b" + l);
                return;
            }
            case "evac": case "fire": {
                int radius = a.length > 2 ? Integer.parseInt(a[2]) : 32;
                boolean fire = sub.equals("fire");
                IncidentLog.Rec r = log.open(w, fire ? IncidentLog.Type.FIRE : IncidentLog.Type.EVAC, (fire ? "reported fire" : "evacuation") + " (" + who + ")", at.x, at.y, at.z, radius, 2);
                Analytics.count(w, "incidents", 1);
                if (fire) { log.section(w, r, "fire:" + r.id, "fire zone", at.x, at.y, at.z, radius); Analytics.count(w, "fires", 1); r.needs |= IncidentLog.FIRE; }
                else say(s, IncidentOps.startEvac(w, log, r, who));
                say(s, "Incident #" + r.id + " opened");
                return;
            }
            case "rescue": { IncidentLog.Rec r = a.length > 2 ? log.byId(Integer.parseInt(a[2])) : null; if (r == null) { say(s, "incident rescue <id>"); return; } say(s, Rescue.assign(w, log, r, who)); for (String l : r.plan) say(s, "§b" + l); return; }
            case "recorder": {
                Map<String, TrainRecorder.Box> b = TrainRecorder.boxes(w);
                if (a.length < 3) { for (TrainRecorder.Box x : b.values()) say(s, x.name + " §7" + x.id.substring(0, 8) + " · " + x.samples.size() + " samples, " + x.events.size() + " events"); return; }
                for (TrainRecorder.Box x : b.values()) if (x.id.startsWith(a[2]) || x.name.equalsIgnoreCase(a[2])) { for (String l : TrainRecorder.download(w, x.id, 300)) say(s, l); return; }
                say(s, "No recorder for " + a[2]);
                return;
            }
            case "rca": {
                AlarmManager am = AlarmManager.get(w);
                List<List<AlarmManager.Alarm>> g = Correlator.groups(am, w.getTotalWorldTime());
                if (g.isEmpty()) say(s, "No correlated alarm groups");
                for (List<AlarmManager.Alarm> grp : g) {
                    say(s, "§cPROBABLE ROOT CAUSE§r #" + grp.get(0).id + " " + grp.get(0).text + " §7(" + (grp.size() - 1) + " consequential)");
                    for (int i = 1; i < grp.size(); i++) say(s, "   §7-> #" + grp.get(i).id + " " + grp.get(i).text);
                }
                return;
            }
            case "selftest": {                                 // the pure rules: placards + what can cause what
                java.util.List<String> bad = Hazmat.selfTest();
                AlarmManager.Cat P = AlarmManager.Cat.POWER, F = AlarmManager.Cat.FIRE, A = AlarmManager.Cat.AUTO, I = AlarmManager.Cat.INCIDENT;
                if (!Correlator.canCause(P, A) || !Correlator.canCause(F, P) || !Correlator.canCause(P, I) || Correlator.canCause(A, P) || Correlator.canCause(I, F)) bad.add("correlation rules");
                say(s, bad.isEmpty() ? "§aIncident self-test passed" : "§cIncident self-test FAILED: " + String.join("; ", bad));
                return;
            }
            case "kpi": for (String[] k : Analytics.kpis(Analytics.get(w).today(w))) say(s, k[0] + ": §e" + k[1]); return;
            case "report": for (String l : Reports.now(w)) say(s, l.startsWith("N|") ? "§l" + l.substring(2) : l); return;
            case "twin": {
                java.util.List<String[]> rows = new java.util.ArrayList<>();
                Twin.fill(w, rows);
                for (String[] r : rows) say(s, String.join(" | ", r));
                return;
            }
            case "scenario": {
                String what = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "show";
                if (what.equals("save")) say(s, Scenario.save(w));
                else if (what.equals("run")) say(s, Scenario.run(w));
                else if (what.equals("drill")) say(s, a.length > 3 ? GradedDrills.run(w, Integer.parseInt(a[3]), null) : "Drills: 0 PEAK, 1 POWER FAIL, 2 BLOCKED JUNCTION, 3 FREIGHT SURGE, 4 WEATHER - incident scenario drill <n> (your response = the scenario knobs)");
                else if (what.equals("set") && a.length > 4) say(s, Scenario.set(w, a[3], Integer.parseInt(a[4])));
                else say(s, "Scenario: " + Scenario.describe(w) + " - incident scenario save | set fault|faultmin|dwell|speed|line|trains <delta> | run");
                return;
            }
            case "pa": {
                if (a.length < 4) { say(s, "incident pa <station> <text...>"); return; }
                for (Map.Entry<Long, String> e : com.dogpound.railmap.server.StationData.get(w).names().entrySet())
                    if (e.getValue().equalsIgnoreCase(a[2])) { say(s, "Spoken on " + Announcer.say(w, e.getKey(), e.getValue(), "MANUAL", String.join(" ", java.util.Arrays.copyOfRange(a, 3, a.length))) + " speaker(s)"); return; }
                say(s, "No station named " + a[2]);
                return;
            }
            case "wo":
                for (IncidentLog.WorkOrder o : log.orders) { BlockPos p = BlockPos.fromLong(o.pos); say(s, (o.done == 0 ? "§eOPEN" : "§7DONE") + " " + o.id + "§r " + o.gear + " @" + p.getX() + "," + p.getZ() + ": " + o.fault); }
                return;
            default: say(s, "/irextras " + USAGE);
        }
    }

    private static void say(ICommandSender s, String t) { s.sendMessage(new TextComponentString(t)); }
}
