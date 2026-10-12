package com.dogpound.railmap.ctl;

import com.dogpound.railmap.admin.Approvals;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.alarm.RootCause;
import com.dogpound.railmap.factory.Planner;
import com.dogpound.railmap.factory.TilePlanner;
import com.dogpound.railmap.graph.TrainNode;
import com.dogpound.railmap.grid.*;
import com.dogpound.railmap.safety.Chaos;
import com.dogpound.railmap.server.TrainTracker;
import com.dogpound.railmap.station.Pis;
import com.dogpound.railmap.track.Gis;
import com.dogpound.railmap.track.TileTunnelControl;
import com.dogpound.railmap.track.TrackHooks;
import com.dogpound.railmap.track.TunnelAir;
import com.dogpound.railmap.wire.Comms;
import com.dogpound.railmap.wire.IWiredMachine;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * What the Control Center's CTRL tab shows (her "next 48" grid / control / comms items), packed as rows into
 * PacketRailState.ctl (row[0] = which table):
 * Q power quality per network · F filter banks · M microgrids · PX/PL/PF/PW production plan · C RTUs · G gear trust ·
 * S PIS feed · R root-cause tree · E evidence · A approvals · AT audit trail · X chaos report · T tunnel sections ·
 * GI GIS verdicts · GB boundaries · K dashboard KPIs · D dashboard layouts · DR player roles
 */
public final class CtlConsole {
    private CtlConsole() {}

    public static void fill(World w, List<String[]> out) {
        long now = w.getTotalWorldTime();
        GridData gd = GridData.get(w);
        // ---- #20 power quality + filters
        for (GridData.Net n : gd.nets()) {
            if (n.level == null || !n.level.ac || !n.problem.isEmpty()) continue;
            double[] v = Harmonics.spectrum(w, n.id);
            if (v == null) continue;
            StringBuilder sp = new StringBuilder();
            for (int h = 2; h <= Harmonics.H; h++) sp.append(h == 2 ? "" : ",").append(String.format(Locale.ROOT, "%.2f", v[h]));
            out.add(new String[]{"Q", String.valueOf(n.id), n.level.label, String.format(Locale.ROOT, "%.2f", v[26]), String.format(Locale.ROOT, "%.1f", v[29]), n.resonance, sp.toString(), String.format(Locale.ROOT, "%.1f", v[27]), String.format(Locale.ROOT, "%.1f", v[28])});
            if (out.size() > 400) break;
        }
        for (Harmonics.Filter f : Harmonics.filters(w))
            out.add(new String[]{"F", xyz(f.pos), String.valueOf(f.net), String.valueOf((int) f.kvar), String.format(Locale.ROOT, "%.2f", f.tuned), String.format(Locale.ROOT, "%.2f", f.actualTune()), String.format(Locale.ROOT, "%.0f", f.ampsPct), f.closed ? "1" : "0", String.format(Locale.ROOT, "%.1f", f.cap * 100)});
        // ---- #21 microgrids, #23 planner, #25 RTUs, #29 tunnels (loaded tiles)
        TilePlanner planner = null;
        for (TileEntity t : new ArrayList<>(w.loadedTileEntityList)) {
            if (t instanceof TileMicrogrid m) {
                Map<String, Object> i = new LinkedHashMap<>();
                m.gearInfo(i);
                out.add(new String[]{"M", m.id(), xyz(m.getPos().toLong()), String.valueOf(i.get("state")), String.valueOf(i.get("mode")), String.valueOf(i.get("hz")), String.valueOf(i.get("poiKw")), String.valueOf(i.get("shedStages")), m.gearState(), String.join("\n", m.log().subList(0, Math.min(6, m.log().size())))});
            } else if (t instanceof TilePlanner p && planner == null && p.plan() != null) planner = p;
            else if (t instanceof TileTunnelControl tc && tc.sections() != null) {
                TunnelAir a = tc.sections();
                out.add(new String[]{"T", tc.id(), a.profile(), String.format(Locale.ROOT, "%.2f", a.u), String.format(Locale.ROOT, "%.2f", a.uc), a.backlayer ? "1" : "0", String.valueOf(a.incident), String.format(Locale.ROOT, "%.0f", tc.emergKwh()), tc.emergency() ? "1" : "0", String.format(Locale.ROOT, "%.0f", a.sectionLength())});
            }
        }
        if (planner != null) {
            Planner.Plan p = planner.plan();
            out.add(new String[]{"PX", planner.gearState(), p.bottleneck == null ? "-" : p.bottleneck.label, String.format(Locale.ROOT, "%.0f", p.bottleneckUtil * 100), String.valueOf(p.held), String.valueOf(p.atRisk), String.format(Locale.ROOT, "%.0f", p.clearing)});
            for (Planner.Stage s : Planner.Stage.values())
                out.add(new String[]{"PL", s.label, String.valueOf(p.plants.get(s)), String.format(Locale.ROOT, "%.0f", p.demand.get(s)), String.format(Locale.ROOT, "%.0f", p.capacity.get(s)), String.format(Locale.ROOT, "%.0f", Math.min(999, p.util(s) * 100)), String.format(Locale.ROOT, "%.0f", p.stock.get(s)), s == p.bottleneck ? "1" : "0"});
            for (String f : p.freight) out.add(new String[]{"PF", f});
            for (String f : p.power) out.add(new String[]{"PW", f});
            for (Planner.PlantRow r : p.rows) out.add(new String[]{"PP", r.m.type().label, r.stage.label, String.format(Locale.ROOT, "%.0f", r.bid), r.verdict, r.hold, xyz(r.m.getPos().toLong()), String.format(Locale.ROOT, "%.0f", r.kw)});
        }
        for (Comms.Rtu r : Comms.rtus(w).values()) {
            if (now - r.seen > 80) continue;
            out.add(new String[]{"C", r.id, xyz(r.pos), String.valueOf(r.path), String.format(Locale.ROOT, "%.0f", r.latencyMs), String.format(Locale.ROOT, "%.1f", r.loss * 100), String.valueOf(r.tx), String.valueOf(r.rx), String.valueOf(r.lost), r.cpuOk ? "1" : "0", Comms.stale(w, r) ? "1" : "0", r.why, String.valueOf(r.radius), r.aUp ? "1" : "0", r.bUp ? "1" : "0"});
        }
        if (!Comms.rtus(w).isEmpty()) {
            int g = 0;
            for (TileEntity t : new ArrayList<>(w.loadedTileEntityList)) {
                if (!(t instanceof IWiredMachine m) || g >= 80) continue;
                String[] d = Comms.diag(w, t.getPos());
                if (d[0].equals("DIRECT")) continue;
                g++;
                out.add(new String[]{"G", m.wiredName(), d[0], d[1], d[2], d[3], xyz(t.getPos().toLong())});
            }
        }
        for (String c : Comms.cuts(w)) out.add(new String[]{"CX", c});
        // ---- #30 PIS
        if (Pis.anyServer(w) || !Pis.get(w).msgs.isEmpty())
            for (Pis.Msg m : Pis.get(w).msgs) {
                if (m.expires != 0 && m.expires <= now) continue;
                out.add(new String[]{"S", String.valueOf(m.id), m.station, m.kind.name(), String.valueOf(m.prio), m.text, m.source, String.valueOf((now - m.created) / 20), m.expires == 0 ? "" : String.valueOf((m.expires - now) / 20)});
            }
        // ---- #31 root cause
        try {
            RootCause.Graph g = RootCause.build(w);
            for (AlarmManager.Alarm r : g.originals()) tree(w, g, r, 0, out, new HashSet<>());
            for (AlarmManager.Alarm a : g.nodes) if ("UNRELATED".equals(g.role.get(a.id)) && a.seq(now) != 0) out.add(new String[]{"R", "0", String.valueOf(a.id), "UNRELATED", a.cat.name(), a.text, "", Recorder.clock(w, a.first)});
            int ev = 0;
            for (RootCause.Edge e : g.best.values()) if (ev++ < 40) out.add(new String[]{"E", String.valueOf(e.effect.id), String.valueOf(e.cause.id), String.format(Locale.ROOT, "%.0f", e.confidence * 100), String.join("\n", e.evidence)});
        } catch (RuntimeException ignored) { }
        // ---- #43 approvals
        Approvals ap = Approvals.get(w);
        for (Approvals.Req r : ap.reqs) {
            if (r.state != Approvals.State.REQUESTED && r.state != Approvals.State.APPROVED && now - r.created > 72000) continue;
            out.add(new String[]{"A", String.valueOf(r.id), r.action, r.where(), r.requester, r.state.name(), r.approvers.size() + "/" + r.needed(), r.dual ? "1" : "0", r.state == Approvals.State.APPROVED ? String.valueOf(Math.max(0, (r.expires - now) / 20)) : "", String.join(", ", r.approvers), r.detail});
        }
        for (int i = 0; i < Math.min(16, ap.trail.size()); i++) out.add(new String[]{"AT", ap.trail.get(i)});
        for (Map.Entry<Long, Boolean> e : ap.required.entrySet()) out.add(new String[]{"AQ", xyz(e.getKey()), e.getValue() ? "1" : "0"});
        // ---- #44 chaos
        out.add(new String[]{"XR", Chaos.current(w)});
        for (String l : Chaos.lastReport(w)) out.add(new String[]{"X", l});
        // ---- #26 GIS
        Gis gis = Gis.get(w);
        for (Map.Entry<String, net.minecraft.nbt.NBTTagCompound> e : com.dogpound.railmap.track.TrackData.get(w).surveys.entrySet())
            out.add(new String[]{"GI", e.getKey(), gis.verdicts.getOrDefault(e.getKey(), "not validated - VALIDATE"), String.format(Locale.ROOT, "%.0f", com.dogpound.railmap.track.Survey.length(com.dogpound.railmap.track.Survey.points(e.getValue())))});
        for (Gis.Boundary b : gis.boundaries) out.add(new String[]{"GB", b.name, b.kind.name(), b.x1 + "," + b.z1 + " - " + b.x2 + "," + b.z2});
        // ---- #39 dashboards: KPIs, layouts, roles
        kpis(w, out);
        for (Map.Entry<String, String> e : CtlData.get(w).dashboards.entrySet()) out.add(new String[]{"D", e.getKey(), e.getValue()});
        com.dogpound.railmap.economy.CompanyData cd = com.dogpound.railmap.economy.CompanyData.get(w);
        for (EntityPlayer p : w.playerEntities) { com.dogpound.railmap.economy.Company c = cd.of(p.getUniqueID()); out.add(new String[]{"DR", p.getName(), c == null || c.members.get(p.getUniqueID()) == null ? "ALL" : c.members.get(p.getUniqueID()).role.name()}); }
    }

    private static void tree(World w, RootCause.Graph g, AlarmManager.Alarm a, int depth, List<String[]> out, Set<Integer> seen) {
        if (!seen.add(a.id) || out.size() > 900) return;
        RootCause.Edge e = g.best.get(a.id);
        out.add(new String[]{"R", String.valueOf(depth), String.valueOf(a.id), g.role.get(a.id), a.cat.name(), a.text, e == null ? "" : String.format(Locale.ROOT, "%.0f", e.confidence * 100), Recorder.clock(w, a.first)});
        for (AlarmManager.Alarm c : g.children(a)) tree(w, g, c, depth + 1, out, seen);
    }

    /** the numbers the dashboard widgets show */
    static void kpis(World w, List<String[]> out) {
        long now = w.getTotalWorldTime();
        int trains = 0, moving = 0;
        for (TrainNode t : TrainTracker.latest(w)) if (t.lead) { trains++; if (t.moving()) moving++; }
        GridData gd = GridData.get(w);
        double traction = 0, total = 0;
        int nets = 0, dead = 0;
        for (GridData.Net n : gd.nets()) { nets++; if (!n.problem.isEmpty()) dead++; total += n.pW / 1000; }
        for (GridData.Node nd : gd.nodes().values()) if (nd.kind == GridKind.FEEDER) traction += nd.drawLast * Elec.W_PER_FE_T / 20.0 / 1000;
        double ind = com.dogpound.railmap.industry.IndustryLink.industryKw(w);
        int rate = 0, plants = 0, running = 0;
        for (TileEntity t : com.dogpound.railmap.industry.IndustryLink.all(w)) if (t instanceof com.dogpound.railmap.factory.machine.TileMachine m) { plants++; if (m.planRunning()) running++; rate += m.planRate(); }
        int nNew = 0, nStand = 0, crit = 0;
        for (AlarmManager.Alarm a : AlarmManager.get(w).all()) { int s = a.seq(now); if (s == 1) nNew++; if (s == 2) nStand++; if (s != 0 && a.sev == AlarmManager.Sev.CRITICAL) crit++; }
        int due = 0, late = 0, open = 0;
        for (com.dogpound.railmap.freight.Contract k : com.dogpound.railmap.freight.ContractData.get(w).board()) {
            if (k.state == com.dogpound.railmap.freight.Contract.State.OPEN) open++;
            if (k.state != com.dogpound.railmap.freight.Contract.State.ACCEPTED) continue;
            if (k.deadline - now < 0) late++; else if (k.deadline - now < 24000) due++;
        }
        int maint = 0;
        for (GridData.Node nd : gd.nodes().values()) if (nd.wear >= 1 || nd.insul < 0.5 || nd.burnt) maint++;
        for (Harmonics.Filter f : Harmonics.filters(w)) if (f.cap < 0.95) maint++;
        long cash = 0, profit = 0;
        for (com.dogpound.railmap.economy.Company c : com.dogpound.railmap.economy.CompanyData.get(w).all()) { cash += c.operating; profit += c.profit(c.month); }
        double worstThd = 0;
        for (GridData.Net n : gd.nets()) worstThd = Math.max(worstThd, n.thdV);
        int stale = 0;
        for (Comms.Rtu r : Comms.rtus(w).values()) if (now - r.seen < 80 && Comms.stale(w, r)) stale++;
        out.add(new String[]{"K", String.valueOf(trains), String.valueOf(moving), String.format(Locale.ROOT, "%.0f", traction), String.format(Locale.ROOT, "%.0f", total), String.format(Locale.ROOT, "%.0f", ind),
                String.valueOf(plants), String.valueOf(running), String.valueOf(rate), String.valueOf(nNew), String.valueOf(nStand), String.valueOf(crit), String.valueOf(due), String.valueOf(late), String.valueOf(open),
                String.valueOf(maint), String.valueOf(cash), String.valueOf(profit), String.valueOf(nets), String.valueOf(dead), String.format(Locale.ROOT, "%.1f", worstThd), String.valueOf(stale)});
    }

    static String xyz(long p) { BlockPos b = BlockPos.fromLong(p); return b.getX() + "," + b.getY() + "," + b.getZ(); }

    /** console buttons: see PacketCtlOp */
    public static String op(World w, int op, String arg, EntityPlayer p) {
        String who = p.getName();
        switch (op) {
            case 1: {                                                             // dashboard save: "user|layout" / "role|layout"
                String[] a = arg.split("\\|", 2);
                if (a.length < 2 || !CtlData.valid(a[1])) return "§cBad layout";
                String key;
                if (a[0].equals("user")) key = "user:" + who;
                else {
                    com.dogpound.railmap.economy.Company c = com.dogpound.railmap.economy.CompanyData.get(w).of(p.getUniqueID());
                    com.dogpound.railmap.economy.Company.Member m = c == null ? null : c.members.get(p.getUniqueID());
                    String role = m == null ? "ALL" : m.role.name();
                    boolean may = com.dogpound.railmap.admin.Access.admin(p) || m != null && (m.role == com.dogpound.railmap.economy.Company.Role.OWNER || m.role == com.dogpound.railmap.economy.Company.Role.DIRECTOR);
                    if (!may) return "§cOnly an owner / director (or an admin) may save a role layout";
                    key = "role:" + (a[0].startsWith("role:") ? a[0].substring(5) : role);
                }
                if (a[1].isEmpty()) CtlData.get(w).dashboards.remove(key); else CtlData.get(w).dashboards.put(key, a[1]);
                CtlData.get(w).markDirty();
                return "§aDashboard saved as " + key;
            }
            case 2: {                                                             // PIS info: "station|template"
                if (!Pis.anyServer(w)) return "§cNo PIS server online";
                String[] a = arg.split("\\|");
                int tp = a.length > 1 ? Math.floorMod(Integer.parseInt(a[1]), Pis.INFO_TEMPLATES.length) : 0;
                Pis.get(w).post(w, a[0], Pis.Kind.INFO, Pis.INFO_TEMPLATES[tp], "console " + who, 60);
                return "§aPublished";
            }
            case 3: Pis.get(w).clear(w, arg.isEmpty() || arg.equals("*") ? null : arg, who); return "§7Operator messages cleared";
            case 4: return Approvals.get(w).approve(w, Integer.parseInt(arg), who);
            case 5: return Approvals.get(w).reject(w, Integer.parseInt(arg), who);
            case 6: return Chaos.abort(w, who);
            case 7: {                                                             // GIS validate a route
                net.minecraft.nbt.NBTTagCompound s = com.dogpound.railmap.track.TrackData.get(w).surveys.get(arg);
                if (s == null) return "§cNo surveyed route " + arg;
                Gis.Report r = Gis.validate(w, s, 0, 0);
                Gis.get(w).verdicts.put(arg, r.verdict()); Gis.get(w).markDirty();
                for (String l : r.lines) p.sendMessage(new net.minecraft.util.text.TextComponentString((l.startsWith("FAIL") ? "§c" : l.startsWith("WARN") ? "§6" : "§a") + l));
                return "§e" + arg + ": " + r.verdict();
            }
            case 8: {                                                             // heal comms injections
                Comms.clearCuts(w); return "§aComms injections cleared";
            }
            case 9: {                                                             // remote press on a ctl device: "x,y,z|control"
                String[] a = arg.split("\\|");
                String[] c = a[0].split(",");
                BlockPos bp = new BlockPos(Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2]));
                if (!w.isBlockLoaded(bp) || !(w.getTileEntity(bp) instanceof com.dogpound.railmap.controls.IControlled m)) return "§cNot loaded";
                if (com.dogpound.railmap.admin.Access.denyRemote((net.minecraft.entity.player.EntityPlayerMP) p, bp)) return null;
                String comms = Comms.command(w, bp);
                if (comms != null) return comms;
                return com.dogpound.railmap.api.Audit.press(w, com.dogpound.railmap.api.Audit.Src.CONSOLE, who, m, a[1], () -> com.dogpound.railmap.controls.IControlled.operate(m, a[1], null));
            }
            default: return null;
        }
    }
}
