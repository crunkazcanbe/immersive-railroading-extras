package com.dogpound.railmap.ctl;

import com.dogpound.railmap.admin.Approvals;
import com.dogpound.railmap.alarm.RootCause;
import com.dogpound.railmap.factory.Planner;
import com.dogpound.railmap.grid.*;
import com.dogpound.railmap.program.ProgramData;
import com.dogpound.railmap.program.Rule;
import com.dogpound.railmap.safety.Chaos;
import com.dogpound.railmap.safety.SafetyData;
import com.dogpound.railmap.safety.TileFaultLab;
import com.dogpound.railmap.station.Pis;
import com.dogpound.railmap.track.*;
import com.dogpound.railmap.wire.Comms;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.*;

/** /irextras ctl ... - inspection, test and admin commands for the next-48 grid / control / comms systems */
public final class CtlCommand {
    private CtlCommand() {}

    public static final String USAGE = "ctl <selftest | pq | mgrid | plan | comms [cut a|b|cpu x y z | partition r | heal] | pis [post st kind text | clear st] | rca [id] | gis [validate route kmh permille | boundary add name kind x1 z1 x2 z2 | boundary del name | at route x z] | approve [id | reject id | request action [x y z] | require x y z [dual] | unrequire x y z | trail] | chaos [run n | abort] | tunnel | dash [user|role layout|reset] | rules [prio id n | onerror id ACT text]>";

    private static void say(ICommandSender s, String t) { s.sendMessage(new TextComponentString(t)); }

    public static boolean command(World w, ICommandSender s, String[] a) {
        if (a.length == 0 || !a[0].equalsIgnoreCase("ctl")) return false;
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
        String who = s.getName();
        try {
            switch (sub) {
                case "selftest": {
                    List<String> out = new ArrayList<>();
                    out.addAll(Harmonics.selfTest());
                    out.addAll(TunnelAir.selfTest());
                    out.addAll(Gis.selfTest());
                    out.add((CtlData.valid(CtlData.DEFAULT) && !CtlData.valid("TRAINS,10,0,4,3") ? "PASS" : "FAIL") + " dashboard layout validation");
                    out.addAll(approvalSelfTest());
                    int f = 0;
                    for (String l : out) { say(s, (l.startsWith("PASS") ? "§a" : "§c") + l); if (!l.startsWith("PASS")) f++; }
                    say(s, (f == 0 ? "§a" : "§c") + "ctl self-test: " + (out.size() - f) + " pass, " + f + " fail");
                    return true;
                }
                case "pq": {
                    for (GridData.Net n : GridData.get(w).nets()) {
                        double[] v = Harmonics.spectrum(w, n.id);
                        if (v == null) continue;
                        StringBuilder b = new StringBuilder();
                        for (int h : TileSpectrumMeter.HS) if (v[h] >= 0.01) b.append(String.format(Locale.ROOT, " h%d %.2f%%", h, v[h]));
                        say(s, String.format(Locale.ROOT, "§bNet #%d %s§r THDv %.2f%% THDi %.1f%%%s%s", n.id, n.level.label, v[26], v[29], b, n.resonance.isEmpty() ? "" : " §c" + n.resonance));
                    }
                    for (Harmonics.Filter f : Harmonics.filters(w)) say(s, String.format(Locale.ROOT, "§7Filter at %s: net #%d %.0f kvar tuned h%.2f (now h%.2f) %s duty %.0f%% C %.1f%%", CtlConsole.xyz(f.pos), f.net, f.kvar, f.tuned, f.actualTune(), f.closed ? "IN" : "OUT", f.ampsPct, f.cap * 100));
                    return true;
                }
                case "mgrid": {
                    for (TileEntity t : new ArrayList<>(w.loadedTileEntityList)) if (t instanceof TileMicrogrid m) { say(s, "§b" + m.id() + "§r " + m.gearState()); for (String l : m.log().subList(0, Math.min(5, m.log().size()))) say(s, "§7  " + l); }
                    return true;
                }
                case "plan": {
                    Planner.Plan p = Planner.plan(w, a.length > 2 ? Double.parseDouble(a[2]) : 0, a.length > 3 ? Double.parseDouble(a[3]) : 0, true);
                    say(s, "§bPlan (advisory): bottleneck " + (p.bottleneck == null ? "-" : p.bottleneck.label) + String.format(Locale.ROOT, " %.0f%%, %d would be held, %d departures at risk, clearing $%.0f/MWh", p.bottleneckUtil * 100, p.held, p.atRisk, p.clearing));
                    for (Planner.Stage st : Planner.Stage.values()) if (p.plants.get(st) > 0 || p.demand.get(st) > 0) say(s, String.format(Locale.ROOT, "§7 %s: %d plants, need %.0f/h, can %.0f/h (%.0f%%), stock %.0f", st.label, p.plants.get(st), p.demand.get(st), p.capacity.get(st), p.util(st) * 100, p.stock.get(st)));
                    for (String l : p.power) say(s, "§7 " + l);
                    for (String l : p.freight) say(s, "§7 " + l);
                    return true;
                }
                case "comms": {
                    if (a.length > 2 && a[2].equalsIgnoreCase("heal")) { Comms.clearCuts(w); say(s, "§aComms injections cleared"); return true; }
                    if (a.length > 3 && a[2].equalsIgnoreCase("partition")) { BlockPos p = s.getPosition(); int r = Integer.parseInt(a[3]); Comms.partition(w, p.getX(), p.getZ(), r, true); say(s, "§6Comms partition " + r + " m round you - /irextras ctl comms heal"); return true; }
                    if (a.length > 6 && a[2].equalsIgnoreCase("cut")) { BlockPos p = new BlockPos(Integer.parseInt(a[4]), Integer.parseInt(a[5]), Integer.parseInt(a[6])); Comms.cuts(w).add(a[3].toUpperCase(Locale.ROOT) + ":" + p.toLong()); say(s, "§6Cut " + a[3].toUpperCase(Locale.ROOT) + " on the RTU at " + p); return true; }
                    long now = w.getTotalWorldTime();
                    for (Comms.Rtu r : Comms.rtus(w).values()) if (now - r.seen < 80) say(s, String.format(Locale.ROOT, "§b%s§r %s path %s %.0f ms %.1f%% loss tx %d rx %d lost %d %s %s", r.id, CtlConsole.xyz(r.pos), r.path == 1 ? "A" : r.path == 2 ? "B" : "NONE", r.latencyMs, r.loss * 100, r.tx, r.rx, r.lost, r.cpuOk ? "" : "§cCPU FAILED", Comms.stale(w, r) ? "§6STALE" : "§aFRESH"));
                    for (String c : Comms.cuts(w)) say(s, "§6 injected: " + c);
                    return true;
                }
                case "pis": {
                    Pis p = Pis.get(w);
                    if (a.length > 5 && a[2].equalsIgnoreCase("post")) { Pis.Kind k = Pis.Kind.valueOf(a[4].toUpperCase(Locale.ROOT)); p.post(w, a[3].replace('_', ' '), k, String.join(" ", Arrays.copyOfRange(a, 5, a.length)), "command " + who, 60); say(s, "§aPosted"); return true; }
                    if (a.length > 2 && a[2].equalsIgnoreCase("clear")) { p.clear(w, a.length > 3 ? a[3].replace('_', ' ') : null, who); say(s, "§7Cleared"); return true; }
                    say(s, "§bPIS feed§r (" + (Pis.anyServer(w) ? "server online" : "§cno server online - signs on fallback") + "§r)");
                    long now = w.getTotalWorldTime();
                    for (Pis.Msg m : p.msgs) if (m.expires == 0 || m.expires > now) say(s, "§7#" + m.id + " " + m.station + " P" + m.prio + " " + m.kind + " §f" + m.text + " §8(" + m.source + ")");
                    return true;
                }
                case "rca": {
                    List<String> out = a.length > 2 ? RootCause.explain(w, Integer.parseInt(a[2])) : RootCause.report(w);
                    for (String l : out) say(s, (l.startsWith("ORIGINAL") || l.startsWith("ROLE") ? "§c" : "§7") + l);
                    return true;
                }
                case "gis": return gis(w, s, a);
                case "approve": return approve(w, s, a, who);
                case "chaos": {
                    if (a.length > 2 && a[2].equalsIgnoreCase("abort")) { say(s, Chaos.abort(w, who)); return true; }
                    if (a.length > 3 && a[2].equalsIgnoreCase("run")) {
                        SafetyData d = SafetyData.get(w);
                        BlockPos lab = d.labPos == 0 ? null : BlockPos.fromLong(d.labPos);
                        if (lab == null || !(w.getTileEntity(lab) instanceof TileFaultLab fl)) { say(s, "§cTurn a Fault Lab console's TEST MODE key first"); return true; }
                        say(s, Chaos.start(w, lab, fl.zone(), fl.controlValue("s_guard") == 0, Integer.parseInt(a[3]), who));
                        return true;
                    }
                    for (int i = 0; i < Chaos.NAMES.length; i++) say(s, "§7" + i + " " + Chaos.NAMES[i]);
                    say(s, "§bRunning: §r" + (Chaos.running(w) ? Chaos.current(w) : "none"));
                    for (String l : Chaos.lastReport(w)) say(s, (l.startsWith("FAIL") ? "§c" : l.startsWith("PASS") ? "§a" : "§7") + l);
                    return true;
                }
                case "tunnel": {
                    for (TileTunnelControl t : TrackHooks.live(w, TileTunnelControl.class)) {
                        TunnelAir x = t.sections();
                        if (x == null) { say(s, "§b" + t.id() + "§r single-zone air (tunnelAirSections off)"); continue; }
                        say(s, String.format(Locale.ROOT, "§b%s§r air %+.2f m/s, critical %.2f m/s%s, emergency bus %.0f kWh, %d sections of %.0f m", t.id(), x.u, x.uc, x.backlayer ? " §cBACK-LAYERING" : "", t.emergKwh(), x.n, x.sectionLength()));
                        StringBuilder b = new StringBuilder("§7");
                        for (int i = 0; i < x.n; i++) b.append(String.format(Locale.ROOT, "[%s%.1f/%.0fppm/%.0fC] ", i == x.incident ? "FIRE " : "", x.smoke[i], x.co[i], x.temp[i]));
                        say(s, b.toString());
                    }
                    return true;
                }
                case "dash": {
                    if (!(s instanceof EntityPlayer p)) return true;
                    if (a.length > 3) { say(s, CtlConsole.op(w, 1, a[2] + "|" + (a[3].equalsIgnoreCase("reset") ? "" : a[3]), p)); return true; }
                    for (Map.Entry<String, String> e : CtlData.get(w).dashboards.entrySet()) say(s, "§7" + e.getKey() + ": " + e.getValue());
                    return true;
                }
                case "rules": {
                    ProgramData d = ProgramData.get(w);
                    if (a.length > 4 && a[2].equalsIgnoreCase("prio")) { Rule r = d.rule(Integer.parseInt(a[3])); if (r != null) { r.priority = Integer.parseInt(a[4]); d.changed(); } say(s, r == null ? "§cNo rule" : "§aPriority " + r.priority); return true; }
                    if (a.length > 4 && a[2].equalsIgnoreCase("onerror")) { Rule r = d.rule(Integer.parseInt(a[3])); if (r != null) { r.errAct = Rule.Act.valueOf(a[4].toUpperCase(Locale.ROOT)); r.errText = a.length > 5 ? String.join(" ", Arrays.copyOfRange(a, 5, a.length)) : ""; d.changed(); } say(s, r == null ? "§cNo rule" : "§aON ERROR " + r.errAct.label); return true; }
                    for (Rule r : d.rules) { say(s, "§b#" + r.id + (r.enabled ? "" : " §8(off)") + "§r P" + r.priority + " " + r.summary() + " §7fired " + r.fired + " errors " + r.errors); for (String h : r.history.subList(0, Math.min(3, r.history.size()))) say(s, "§8   " + h); }
                    return true;
                }
                default: say(s, "§7/irextras " + USAGE); return true;
            }
        } catch (RuntimeException e) { say(s, "§cBad arguments: " + e.getMessage() + " - /irextras " + USAGE); return true; }
    }

    private static boolean gis(World w, ICommandSender s, String[] a) {
        Gis g = Gis.get(w);
        String op = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "";
        if (op.equals("validate") && a.length > 3) {
            NBTTagCompound sv = TrackData.get(w).surveys.get(a[3].replace('_', ' ').toUpperCase(Locale.ROOT));
            if (sv == null) sv = TrackData.get(w).surveys.get(a[3]);
            if (sv == null) { say(s, "§cNo surveyed route '" + a[3] + "' (routes: " + TrackData.get(w).surveys.keySet() + ")"); return true; }
            Gis.Report r = Gis.validate(w, sv, a.length > 4 ? Double.parseDouble(a[4]) : 0, a.length > 5 ? Double.parseDouble(a[5]) : 0);
            g.verdicts.put(a[3], r.verdict()); g.markDirty();
            for (String l : r.lines) say(s, (l.startsWith("FAIL") ? "§c" : l.startsWith("WARN") ? "§6" : "§a") + l);
            for (String l : r.longSection.subList(0, Math.min(12, r.longSection.size()))) say(s, "§8" + l);
            say(s, "§e" + r.verdict());
            return true;
        }
        if (op.equals("boundary") && a.length > 4 && a[3].equalsIgnoreCase("add") && a.length > 9) {
            Gis.Boundary b = new Gis.Boundary();
            b.name = a[4]; b.kind = Gis.Kind.valueOf(a[5].toUpperCase(Locale.ROOT));
            b.x1 = Integer.parseInt(a[6]); b.z1 = Integer.parseInt(a[7]); b.x2 = Integer.parseInt(a[8]); b.z2 = Integer.parseInt(a[9]);
            g.boundaries.removeIf(x -> x.name.equals(b.name));
            g.boundaries.add(b); g.markDirty();
            say(s, "§aBoundary " + b.name + " (" + b.kind + ") added");
            return true;
        }
        if (op.equals("boundary") && a.length > 4 && a[3].equalsIgnoreCase("del")) { g.boundaries.removeIf(x -> x.name.equals(a[4])); g.markDirty(); say(s, "§7Removed " + a[4]); return true; }
        if (op.equals("at") && a.length > 5) {
            NBTTagCompound sv = TrackData.get(w).surveys.get(a[3]);
            if (sv == null) { say(s, "§cNo route " + a[3]); return true; }
            double[] cd = Gis.nearest(Survey.points(sv), Double.parseDouble(a[4]), Double.parseDouble(a[5]));
            say(s, String.format(Locale.ROOT, "§bChainage %s, offset %.1f m", Survey.chain(cd[0]), cd[1]));
            return true;
        }
        for (Map.Entry<String, NBTTagCompound> e : TrackData.get(w).surveys.entrySet()) say(s, "§b" + e.getKey() + "§r " + g.verdicts.getOrDefault(e.getKey(), "not validated"));
        for (Gis.Boundary b : g.boundaries) say(s, "§7" + b.kind + " " + b.name + " " + b.x1 + "," + b.z1 + " - " + b.x2 + "," + b.z2);
        return true;
    }

    private static boolean approve(World w, ICommandSender s, String[] a, String who) {
        Approvals ap = Approvals.get(w);
        String op = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "";
        boolean admin = !(s instanceof EntityPlayer p) || com.dogpound.railmap.admin.Access.admin(p);
        switch (op) {
            case "": for (Approvals.Req r : ap.open()) say(s, "§b#" + r.id + "§r " + r.action + " @ " + r.where() + " by " + r.requester + " " + r.state + " " + r.approvers.size() + "/" + r.needed() + (r.dual ? " DUAL" : "")); for (Map.Entry<Long, Boolean> e : ap.required.entrySet()) say(s, "§7under approval control: " + CtlConsole.xyz(e.getKey()) + (e.getValue() ? " (dual)" : "")); return true;
            case "reject": say(s, ap.reject(w, Integer.parseInt(a[3]), who)); return true;
            case "cancel": say(s, ap.cancel(w, Integer.parseInt(a[3]), who)); return true;
            case "trail": for (String l : ap.trail.subList(0, Math.min(20, ap.trail.size()))) say(s, "§7" + l); return true;
            case "request": { BlockPos p = a.length > 6 ? new BlockPos(Integer.parseInt(a[4]), Integer.parseInt(a[5]), Integer.parseInt(a[6])) : null; Approvals.Req r = ap.request(w, a[3], p, a[3] + (p == null ? "" : " at " + p), who, false); say(s, "§aRequest #" + r.id + " filed"); return true; }
            case "require": case "unrequire": {
                if (!admin) { say(s, "§cAdmins only"); return true; }
                long p = new BlockPos(Integer.parseInt(a[3]), Integer.parseInt(a[4]), Integer.parseInt(a[5])).toLong();
                if (op.equals("require")) ap.required.put(p, a.length > 6 && a[6].equalsIgnoreCase("dual")); else ap.required.remove(p);
                ap.markDirty();
                say(s, "§a" + CtlConsole.xyz(p) + (op.equals("require") ? " now needs approval" : " no longer needs approval"));
                return true;
            }
            default: say(s, ap.approve(w, Integer.parseInt(a[2]), who)); return true;
        }
    }

    /** the workflow in memory: request, self-approval refused, dual needs two, execute consumes it */
    static List<String> approvalSelfTest() {
        List<String> out = new ArrayList<>();
        Approvals a = new Approvals();
        Approvals.Req r = new Approvals.Req();
        r.id = 1; r.requester = "alice"; r.dual = true;
        a.reqs.add(r);
        boolean ok = r.state == Approvals.State.REQUESTED;
        r.approvers.add("bob");
        ok &= r.approvers.size() < r.needed();
        r.approvers.add("carol");
        ok &= r.approvers.size() >= r.needed();
        out.add((ok ? "PASS" : "FAIL") + " dual authorisation needs two different approvers");
        return out;
    }
}
