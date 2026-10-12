package com.dogpound.railmap.admin;

import cam72cam.immersiverailroading.entity.Locomotive;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.economy.CompanyData;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.io.File;
import java.util.*;

/** {@code /irextras admin ...} - her ChatGPT list #111-120 from the chat line (the console block does the same). */
public final class AdminCommand {
    private AdminCommand() {}

    public static final String USAGE = "admin [perf|pause|resume|inspect|stop|release|diag|report|profile|backup|stats|desk|territory|area|access|lease|agree|sla]";

    private static void say(ICommandSender s, String m) { s.sendMessage(new TextComponentString(m)); }
    private static void say(ICommandSender s, List<String> l) { for (String m : l) say(s, "§7" + m); }

    private static boolean admin(ICommandSender s) {
        return !(s instanceof EntityPlayer p) || Access.admin(p);
    }

    private static long cents(String s) { return Math.round(Double.parseDouble(s.replace("$", "")) * 100); }

    /** false = not "admin" */
    public static boolean command(World w, ICommandSender s, String[] a) {
        if (a.length == 0 || !a[0].equalsIgnoreCase("admin")) return false;
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "help";
        String who = s.getName();
        EntityPlayerMP p = s instanceof EntityPlayerMP mp ? mp : null;
        Vec3d at = s.getPositionVector();
        try {
            switch (sub) {
                case "perf": {
                    if (a.length > 2 && a[2].equalsIgnoreCase("reset")) { if (!admin(s)) break; Sched.reset(w.getMinecraftServer()); say(s, "§aProfiler reset - measure again in a minute"); return true; }
                    say(s, Sched.report(w.getMinecraftServer()));
                    say(s, "§7Active cells " + Sched.activeCells(w) + ", inactive work x" + Sched.slowdown(w) + " less often" + (AdminConfig.loadAware ? "" : " (load-aware OFF)"));
                    return true;
                }
                case "pause": if (!admin(s)) break; say(s, Sched.pause(w, a[2], a.length > 3 ? Integer.parseInt(a[3]) : 10, who)); return true;
                case "resume": if (!admin(s)) break; say(s, Sched.resume(w, a[2], who)); return true;
                case "inspect": {
                    Locomotive l = AdminOps.nearest(w, at.x, at.y, at.z, a.length > 2 ? Double.parseDouble(a[2]) : 64);
                    if (l == null) { say(s, "§7No loco near"); return true; }
                    for (String m : AdminOps.inspect(w, l)) say(s, m);
                    return true;
                }
                case "stop": case "release": {
                    if (!admin(s)) break;
                    Locomotive l = AdminOps.nearest(w, at.x, at.y, at.z, 64);
                    if (l == null) { say(s, "§7No loco within 64 blocks"); return true; }
                    say(s, sub.equals("stop") ? AdminOps.safeStop(w, l, who, a.length > 2 ? String.join(" ", Arrays.copyOfRange(a, 2, a.length)) : "") : AdminOps.release(w, l.getUUID(), who));
                    return true;
                }
                case "diag": {
                    TileEntity t = null;
                    if (p != null) {
                        RayTraceResult h = p.rayTrace(16, 1f);
                        if (h != null && h.typeOfHit == RayTraceResult.Type.BLOCK) t = w.getTileEntity(h.getBlockPos());
                    }
                    if (t == null) t = AdminOps.nearestSignal(w, new BlockPos(at));
                    if (t == null) { say(s, "§7Look at the gear (or stand near a signal)"); return true; }
                    for (String m : AdminOps.diagnose(w, t)) say(s, m);
                    return true;
                }
                case "report": {
                    List<String> r = AdminOps.faultReport(w);
                    File f = AdminOps.exportReport(w, r);
                    say(s, r.subList(0, Math.min(12, r.size())));
                    say(s, "§aFull fault report: " + f.getPath());
                    return true;
                }
                case "profile": {
                    if (a.length < 3) {
                        say(s, "§bSimulation profile now: §f" + Profiles.current() + " §7(casual | realistic | hardcore; add 'apply' to switch)");
                        for (String n : Profiles.NAMES) say(s, "§7  " + n + ": " + Profiles.diff(Profiles.index(n)).size() + " settings differ");
                        return true;
                    }
                    int i = Profiles.index(a[2]);
                    if (i < 0) { say(s, "§cProfiles: casual, realistic, hardcore"); return true; }
                    if (a.length > 3 && a[3].equalsIgnoreCase("apply")) {
                        if (!admin(s)) break;
                        List<String> ch = Profiles.apply(w.getMinecraftServer(), i, who);
                        say(s, "§a" + Profiles.NAMES[i] + " applied, " + ch.size() + " settings changed (config files written)");
                        say(s, ch);
                    } else { say(s, "§e" + Profiles.NAMES[i] + " would change:"); say(s, Profiles.diff(i)); say(s, "§7/irextras admin profile " + a[2] + " apply"); }
                    return true;
                }
                case "backup": {
                    if (!admin(s)) break;
                    String op = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "list";
                    switch (op) {
                        case "now": say(s, Backups.snapshot(w.getMinecraftServer(), a.length > 3 ? a[3] : "manual")); return true;
                        case "restore": say(s, Backups.stage(w.getMinecraftServer(), a[3])); return true;
                        case "cancel": say(s, Backups.cancel(w.getMinecraftServer())); return true;
                        case "verify": { for (File f : Backups.list(w.getMinecraftServer())) if (f.getName().startsWith(a[3])) { String b = Backups.verify(f); say(s, b == null ? "§a" + f.getName() + " verified" : "§c" + f.getName() + ": " + b); return true; } say(s, "§cNo snapshot " + a[3]); return true; }
                        case "recovery": say(s, Backups.RECOVERY.isEmpty() ? Collections.singletonList("Clean start: no recovery was needed") : Backups.RECOVERY); return true;
                        default:
                            say(s, "§b" + (Backups.lastResult.isEmpty() ? "Snapshots:" : Backups.lastResult));
                            for (File f : Backups.list(w.getMinecraftServer())) say(s, "§7  " + f.getName());
                            say(s, "§7now [tag] | restore <id> | cancel | verify <id> | recovery");
                            return true;
                    }
                }
                case "stats": {
                    Stats st = Stats.get(w);
                    if (a.length > 2 && a[2].equalsIgnoreCase("export")) { say(s, "§aWritten " + st.export(w).getPath()); return true; }
                    int d = a.length > 2 ? (a[2].equalsIgnoreCase("all") ? 0 : Integer.parseInt(a[2])) : 7;
                    say(s, Stats.lines(st.span(d), d == 0 ? "§bAll time" : "§bLast " + d + " days"));
                    return true;
                }
                case "desk": return desk(w, s, p, a);
                case "area": case "territory": return rects(w, s, p, a, sub.equals("territory"));
                case "access": return access(w, s, p, a);
                case "lease": case "agree": case "sla": return commerce(w, s, p, a, sub);
                default: break;
            }
            if (!sub.equals("help") && !admin(s)) { say(s, "§c⛔ That needs the irextras.rail.admin permission"); return true; }
            say(s, "§b/irextras admin§7 perf [reset] | pause <sub> [min] | resume <sub> | inspect | stop [why] | release | diag | report | profile [name [apply]]"
                    + " | backup [now|restore <id>|cancel|verify|recovery] | stats [days|all|export] | desk [list|request|accept|refuse|release|force] <area>"
                    + " | area add <name> <radius>|del <name>|list | territory here <company> <radius>|del <n>|list | access grant|deny|clear <player> <caps>|here"
                    + " | lease offer <company> <$/day> <days> [maxKm] [lessee|lessor]|accept|refuse|end <id>|list"
                    + " | agree offer <operator> <$/km> <$/call> <caps> [lines]|accept|refuse|end <id>|list | sla offer <operator> <line> <punct%> <avail%> <deps/day> <$/point>|accept|refuse|end <id>|list");
            return true;
        } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
            say(s, "§cMissing or bad value - /irextras admin help");
            return true;
        } catch (java.io.IOException e) {
            say(s, "§cCouldn't write: " + e.getMessage());
            return true;
        }
    }

    private static boolean desk(World w, ICommandSender s, EntityPlayerMP p, String[] a) {
        String op = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "list";
        String area = a.length > 3 ? String.join(" ", Arrays.copyOfRange(a, 3, a.length)) : p == null ? "" : Locks.area(w, p.getPosition());
        switch (op) {
            case "request": if (p != null) say(s, Locks.request(w, p, area)); return true;
            case "accept": if (p != null) say(s, Locks.accept(w, p, area)); return true;
            case "refuse": if (p != null) say(s, Locks.refuse(w, p, area)); return true;
            case "release": say(s, Locks.release(w, p, area, false)); return true;
            case "force": if (!admin(s)) { say(s, "§c⛔ Admins only"); return true; } say(s, Locks.release(w, p, area, true)); return true;
            default:
                List<Locks.Lock> ls = Locks.list(w);
                say(s, "§bControl areas held: " + ls.size() + (p == null ? "" : " §7(you are in " + Locks.area(w, p.getPosition()) + ")"));
                for (Locks.Lock l : ls) say(s, "§7  " + l.area + ": " + l.name + " at " + l.desk + ", " + Locks.mins(w.getTotalWorldTime() - l.since) + (l.want == null ? "" : ", handover asked by " + l.wantName));
                return true;
        }
    }

    /** named control areas (company 0) and company territory claims */
    private static boolean rects(World w, ICommandSender s, EntityPlayerMP p, String[] a, boolean territory) {
        AdminData ad = AdminData.get(w);
        List<AdminData.Rect> list = territory ? ad.claims : ad.areas;
        String op = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "list";
        CompanyData cd = CompanyData.get(w);
        if (op.equals("list")) {
            say(s, territory ? "§bTerritory claims (plus " + AdminConfig.stationTerritory + " blocks round company stations):" : "§bNamed control areas (else automatic " + AdminConfig.lockCellBlocks + "-block cells):");
            for (int i = 0; i < list.size(); i++) { AdminData.Rect r = list.get(i); Company c = cd.byId(r.company); say(s, "§7  " + (i + 1) + ". " + (territory ? (c == null ? "?" : c.name) : r.name) + " " + r.x0 + "," + r.z0 + " to " + r.x1 + "," + r.z1); }
            if (p != null && territory) { Company o = Access.ownerAt(w, p.getPosition()); say(s, "§7Here: " + (o == null ? "unclaimed (open railway)" : o.name)); }
            return true;
        }
        if (op.equals("del")) {
            int idx = -1;
            for (int i = 0; i < list.size(); i++) if (String.valueOf(i + 1).equals(a[3]) || list.get(i).name.equalsIgnoreCase(a[3])) idx = i;
            if (idx < 0) { say(s, "§cNo such entry"); return true; }
            AdminData.Rect r = list.get(idx);
            Company c = cd.byId(r.company);
            if (!admin(s) && !(territory && p != null && Commercial.finance(c, p.getUniqueID()))) { say(s, "§c⛔ Not yours to remove"); return true; }
            list.remove(idx); ad.markDirty();
            say(s, "§aRemoved");
            return true;
        }
        if (p == null) { say(s, "§cPlayers only"); return true; }
        // area add <name> <radius> | territory here <company> <radius>
        AdminData.Rect r = new AdminData.Rect();
        int rad = Integer.parseInt(a[4]);
        r.x0 = p.getPosition().getX() - rad; r.z0 = p.getPosition().getZ() - rad; r.x1 = p.getPosition().getX() + rad; r.z1 = p.getPosition().getZ() + rad;
        if (territory) {
            Company c = cd.byName(a[3]);
            if (c == null) { say(s, "§cNo company " + a[3]); return true; }
            if (!admin(s) && !Commercial.finance(c, p.getUniqueID())) { say(s, "§c⛔ Only " + c.name + "'s owner / directors (or an admin) can claim for it"); return true; }
            for (AdminData.Rect o : ad.claims)
                if (o.company != c.id && o.x0 <= r.x1 && o.x1 >= r.x0 && o.z0 <= r.z1 && o.z1 >= r.z0 && !admin(s)) { Company oc = cd.byId(o.company); say(s, "§cOverlaps " + (oc == null ? "another" : oc.name) + "'s territory"); return true; }
            r.company = c.id; r.name = c.name;
        } else {
            if (!admin(s)) { say(s, "§c⛔ Admins draw control areas"); return true; }
            r.name = a[3].toUpperCase(Locale.ROOT);
        }
        list.add(r);
        ad.markDirty();
        say(s, "§a" + (territory ? "Territory claimed for " : "Control area ") + r.name + ": " + r.x0 + "," + r.z0 + " to " + r.x1 + "," + r.z1);
        return true;
    }

    private static boolean access(World w, ICommandSender s, EntityPlayerMP p, String[] a) {
        String op = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "here";
        CompanyData cd = CompanyData.get(w);
        if (op.equals("here")) {
            if (p == null) return true;
            Company o = Access.ownerAt(w, p.getPosition());
            say(s, "§bHere: " + (o == null ? "unclaimed railway (open)" : o.name + "'s territory") + ", control area " + Locks.area(w, p.getPosition()));
            for (Access.Cap c : Access.Cap.values()) {
                boolean ok = Access.node(p, c) && (c == Access.Cap.ADMIN ? Access.admin(p) : (o == null || Access.admin(p) || Access.mayAct(w, p.getUniqueID(), o, c, null)));
                say(s, "§7  " + c.name() + ": " + (ok ? "§ayes" : "§cno"));
            }
            return true;
        }
        if (p == null) return true;
        Company c = cd.of(p.getUniqueID());
        if (c == null || !c.can(p.getUniqueID(), r -> r.staff)) { say(s, "§c⛔ Only a company owner / director delegates rights"); return true; }
        UUID target = null;
        for (Map.Entry<UUID, Company.Member> e : c.members.entrySet()) if (e.getValue().name.equalsIgnoreCase(a[3])) target = e.getKey();
        if (target == null) { say(s, "§c" + a[3] + " isn't in " + c.name); return true; }
        AdminData ad = AdminData.get(w);
        int[] g = ad.grants.computeIfAbsent(target, k -> new int[2]);
        int m = a.length > 4 ? Access.mask(a[4]) : 0x1F;
        if (op.equals("grant")) { g[0] |= m; g[1] &= ~m; }
        else if (op.equals("deny")) { g[1] |= m; g[0] &= ~m; }
        else { g[0] &= ~m; g[1] &= ~m; }
        if (g[0] == 0 && g[1] == 0) ad.grants.remove(target);
        ad.markDirty();
        com.dogpound.railmap.alarm.Recorder.event(w, "ACCESS: " + p.getName() + " " + op + " " + Access.caps(m) + " for " + a[3]);
        say(s, "§a" + a[3] + ": granted " + Access.caps(g[0]) + ", denied " + Access.caps(g[1]) + " (on top of the " + c.member(target).role.label + " role)");
        return true;
    }

    private static boolean commerce(World w, ICommandSender s, EntityPlayerMP p, String[] a, String kind) {
        String op = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "list";
        AdminData ad = AdminData.get(w);
        CompanyData cd = CompanyData.get(w);
        if (op.equals("list")) {
            if (kind.equals("lease")) for (AdminData.Lease l : ad.leases)
                say(s, String.format(Locale.ROOT, "§7L%d %s %s: %s > %s, %s/day, day %d/%d, %s km cap, maintained by %s, paid %s", l.id, l.state, l.stockName, l.lessorName,
                        name(cd, l.lesseeCo), com.dogpound.railmap.economy.Coins.fmt(l.rate), l.daysRun, l.days, l.maxKm == 0 ? "no" : String.valueOf(l.maxKm), l.maintLessee ? "lessee" : "lessor",
                        com.dogpound.railmap.economy.Coins.fmt(l.paid)));
            else if (kind.equals("agree")) for (AdminData.Agreement g : ad.agreements)
                say(s, String.format(Locale.ROOT, "§7A%d %s %s on %s's %s: %s/km + %s/call, rights %s, owed %s, paid %s", g.id, g.state, name(cd, g.operatorCo), name(cd, g.ownerCo),
                        g.lines.isEmpty() ? "lines" : String.join(",", g.lines), com.dogpound.railmap.economy.Coins.fmt(g.perKm), com.dogpound.railmap.economy.Coins.fmt(g.perCall),
                        Access.caps(g.caps), com.dogpound.railmap.economy.Coins.fmt(g.owed), com.dogpound.railmap.economy.Coins.fmt(g.paid)));
            else for (AdminData.Sla x : ad.slas) {
                say(s, String.format(Locale.ROOT, "§7S%d %s %s: %s (owner) / %s (operator): punct %d%%, avail %d%%, %d deps/day, %s/point; breaches %d, owner paid %s, operator paid %s",
                        x.id, x.state, x.line, name(cd, x.ownerCo), name(cd, x.operatorCo), x.punctPct, x.availPct, x.depsPerDay, com.dogpound.railmap.economy.Coins.fmt(x.penalty),
                        x.breaches, com.dogpound.railmap.economy.Coins.fmt(x.ownerPaid), com.dogpound.railmap.economy.Coins.fmt(x.operatorPaid)));
                for (int i = 0; i < Math.min(3, x.history.size()); i++) say(s, "§8    " + x.history.get(i));
            }
            return true;
        }
        if (p == null) { say(s, "§cPlayers only"); return true; }
        if (op.equals("offer")) {
            switch (kind) {
                case "lease": {
                    int maxKm = a.length > 6 ? Integer.parseInt(a[6]) : 0;
                    boolean lessee = a.length > 7 && a[7].equalsIgnoreCase("lessee");
                    say(s, Commercial.leaseOffer(p, a[3], cents(a[4]), Integer.parseInt(a[5]), maxKm, lessee));
                    return true;
                }
                case "agree": say(s, Commercial.agreeOffer(p, a[3], cents(a[4]), cents(a[5]), Access.mask(a[6]), a.length > 7 ? Arrays.asList(Arrays.copyOfRange(a, 7, a.length)) : Collections.emptyList())); return true;
                default: say(s, Commercial.slaOffer(p, a[3], a[4], Integer.parseInt(a[5]), Integer.parseInt(a[6]), Integer.parseInt(a[7]), cents(a[8]))); return true;
            }
        }
        int id = Integer.parseInt(a[3].replaceAll("[^0-9]", ""));
        boolean yes = op.equals("accept");
        if (op.equals("accept") || op.equals("refuse")) {
            say(s, kind.equals("lease") ? Commercial.leaseAccept(p, id, yes) : kind.equals("agree") ? Commercial.agreeAccept(p, id, yes) : Commercial.slaAccept(p, id, yes));
            return true;
        }
        if (op.equals("end")) {
            if (kind.equals("lease")) {
                AdminData.Lease l = ad.lease(id);
                if (l == null || l.state != AdminData.State.ACTIVE) { say(s, "§cNo active lease L" + id); return true; }
                boolean lessee = Commercial.finance(cd.byId(l.lesseeCo), p.getUniqueID()), lessor = p.getUniqueID().equals(l.lessorPlayer) || Commercial.finance(cd.byId(l.lessorCo), p.getUniqueID());
                if (!lessee && !lessor && !admin(s)) { say(s, "§c⛔ Not your lease"); return true; }
                say(s, Commercial.leaseEnd(w, l, "ended early by " + p.getName(), lessee));
                return true;
            }
            int owner, oper;
            AdminData.Agreement g = kind.equals("agree") ? ad.agreement(id) : null;
            AdminData.Sla x = kind.equals("sla") ? ad.sla(id) : null;
            if (g == null && x == null) { say(s, "§cNo such " + kind); return true; }
            owner = g != null ? g.ownerCo : x.ownerCo; oper = g != null ? g.operatorCo : x.operatorCo;
            if (!admin(s) && !Commercial.finance(cd.byId(owner), p.getUniqueID()) && !Commercial.finance(cd.byId(oper), p.getUniqueID())) { say(s, "§c⛔ Not a party to it"); return true; }
            if (g != null) g.state = AdminData.State.ENDED; else x.state = AdminData.State.ENDED;
            ad.markDirty();
            com.dogpound.railmap.alarm.Recorder.event(w, "CONTRACT: " + (g != null ? "A" : "S") + id + " ended by " + p.getName());
            say(s, "§e" + (g != null ? "Agreement A" : "SLA S") + id + " ended");
            return true;
        }
        say(s, "§c" + kind + " offer | accept | refuse | end <id> | list");
        return true;
    }

    private static String name(CompanyData cd, int id) { Company c = cd.byId(id); return c == null ? "?" : c.name; }
}
