package com.dogpound.railmap.admin;

import cam72cam.immersiverailroading.entity.Locomotive;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.ConsoleControl;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.block.StationControls;
import com.dogpound.railmap.block.TileFurniture;
import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.economy.CompanyData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.util.*;

/**
 * Railway Administration Console (her ChatGPT list #111-120): the server admin's workstation. Eight pages on the
 * three monitors (PAGE selector): OVERVIEW, TRAINS (inspect, SAFE STOP / RELEASE), SUBSYS (profiler, PAUSE / RESUME),
 * DESKS (command locks, FORCE RELEASE), BACKUP (SNAPSHOT, RESTORE), STATS (span), PROFILE (CASUAL / REALISTIC /
 * HARDCORE, APPLY) and COMMERCE (leases, access agreements, SLAs). &lt; &gt; pick the item, ACTION does the page's
 * action, PRINT prints the page (a fault report on OVERVIEW), EXPORT writes the fault report JSON / stats CSV,
 * RESET zeroes the profiler. The ADMIN KEY must be ARMED for restore, profile and force release. The mushroom is a
 * real network ALL STOP. Status lamps, an MSPT gauge and the rack LEDs read the live server. Needs 12 V; every powered
 * control needs the irextras.rail.admin permission. Logbook drawer and backup-tape cabinet are real storage.
 */
public class TileAdminConsole extends TileFurniture {
    public static final String[] STORE_IDS = {"logbook", "tapes"};
    public static final String[] STORE_NAMES = {"Admin Logbook Drawer", "Backup Tape Cabinet"};
    public static final int[] STORE_SIZES = {18, 9};
    static final String[] PAGES = {"OVERVIEW", "TRAINS", "SUBSYS", "DESKS", "BACKUP", "STATS", "PROFILE", "COMMERCE"};
    private static final int[] SPANS = {1, 7, 30, 0};

    private List<File> snaps = new ArrayList<>();
    private int span = 1;
    private final List<String> lines = new ArrayList<>();

    public TileAdminConsole() { super(STORE_IDS, STORE_NAMES, STORE_SIZES, 1); }

    @Override protected boolean mechanical(String id) { return id.equals("k_arm"); }

    @Override
    protected String pressMechanical(String id, EntityPlayer who) {
        return who != null && !Access.admin(who) ? "§c⛔ The ADMIN KEY is held by the server admins" : null;
    }

    private int page() { return Math.floorMod(ctl.get("s_page"), PAGES.length); }
    private boolean armed() { return ctl.get("k_arm") == 1; }
    private MinecraftServer server() { return world.getMinecraftServer(); }

    private int count() {
        switch (page()) {
            case 1: return AdminOps.locos(world).size();
            case 2: return Sched.subsystems().size();
            case 3: return Locks.list(world).size();
            case 4: return snaps.size();
            case 6: return Profiles.NAMES.length;
            default: return 1;
        }
    }

    private int sel() { int n = count(); return n == 0 ? 0 : Math.floorMod(ctl.get("sel"), n); }

    @Override
    protected String press(String id, EntityPlayer who) {
        if (who != null && !Access.admin(who)) return "§c⛔ Railway administration is for server admins (irextras.rail.admin)";
        String name = who == null ? "console" : who.getName();
        switch (id) {
            case "s_page": ctl.set("sel", 0); return null;                     // default: the selector turns
            case "prev": ctl.set("sel", ctl.get("sel") - 1); return "";
            case "next": ctl.set("sel", ctl.get("sel") + 1); return "";
            case "reset": Sched.reset(server()); return "§7Profiler counters reset";
            case "allstop": {
                boolean on = ctl.get("allstop") == 0;
                ctl.set("allstop", on ? 1 : 0);
                ConsoleControl.allStop(world.provider.getDimension(), on);
                Recorder.event(world, "ADMIN: " + name + (on ? " pressed RAILWAY ALL STOP" : " released ALL STOP"));
                if (on) AlarmManager.get(world).raise(world, "admin:allstop", AlarmManager.Cat.ALLSTOP, AlarmManager.Sev.CRITICAL, "RAILWAY ALL STOP from the admin console (" + name + ")", pos, true);
                else AlarmManager.get(world).clear("admin:allstop");
                return on ? "§c⛔ ALL STOP - every driverless train is braking" : "§aALL STOP released";
            }
            case "print": {
                List<String> l = page() == 0 ? AdminOps.faultReport(world) : new ArrayList<>(lines);
                if (page() != 0) l.add(0, "ADMIN CONSOLE - " + PAGES[page()]);
                AdminOps.printBook(world, pos, controlFacing(), page() == 0 ? "Fault Report" : "Admin " + PAGES[page()], l);
                return "§7Printing...";
            }
            case "export": {
                try {
                    File f = page() == 5 ? Stats.get(world).export(world) : AdminOps.exportReport(world, AdminOps.faultReport(world));
                    return "§aWritten " + f.getName();
                } catch (java.io.IOException e) { return "§cExport failed: " + e.getMessage(); }
            }
            case "restore": {
                if (page() != 4 || snaps.isEmpty()) return "§7Pick a snapshot on the BACKUP page";
                if (!armed()) return "§cTurn the ADMIN KEY to ARMED first";
                return Backups.stage(server(), snaps.get(sel()).getName());
            }
            case "act": return act(name);
            default: return null;
        }
    }

    private String act(String name) {
        int s = sel();
        switch (page()) {
            case 1: {
                List<Locomotive> ls = AdminOps.locos(world);
                if (ls.isEmpty()) return "§7No trains loaded";
                Locomotive l = ls.get(s);
                return AdminData.get(world).stopped.containsKey(l.getUUID()) ? AdminOps.release(world, l.getUUID(), name) : AdminOps.safeStop(world, l, name, "admin console");
            }
            case 2: {
                List<String> ss = new ArrayList<>(Sched.subsystems());
                if (ss.isEmpty()) return "§7Profiler off";
                String n = ss.get(s);
                return Sched.PAUSED.containsKey(n) ? Sched.resume(world, n, name) : Sched.pause(world, n, 10, name);
            }
            case 3: {
                List<Locks.Lock> ls = Locks.list(world);
                if (ls.isEmpty()) return "§7No desk holds an area";
                if (!armed()) return "§cTurn the ADMIN KEY to ARMED to force a release";
                return Locks.release(world, null, ls.get(s).area, true);
            }
            case 4: return Backups.snapshot(server(), "console");
            case 5: span = (span + 1) % SPANS.length; return "";
            case 6: {
                if (!armed()) return "§cTurn the ADMIN KEY to ARMED to change the simulation profile";
                List<String> ch = Profiles.apply(server(), s, name);
                return "§e" + Profiles.NAMES[s] + " profile applied: " + ch.size() + " settings changed";
            }
            default: return "§7Nothing to do on this page";
        }
    }

    // ---- screens -------------------------------------------------------------------------------------------------
    private void build() {
        lines.clear();
        int s = sel(), n = count();
        MinecraftServer sv = server();
        switch (page()) {
            case 0: {
                AdminData ad = AdminData.get(world);
                int open = 0;
                for (AlarmManager.Alarm a : AlarmManager.get(world).all()) if (a.seq(world.getTotalWorldTime()) != 0) open++;
                int act = 0;
                for (AdminData.Lease l : ad.leases) if (l.state == AdminData.State.ACTIVE) act++;
                lines.add("RAILWAY ADMIN - PROFILE " + Profiles.current());
                lines.add(String.format(Locale.ROOT, "SERVER %.1f MS/TICK  IR EXTRAS %.2f MS", Sched.mspt(sv), Sched.costMs(sv)));
                lines.add("TRAINS " + AdminOps.locos(world).size() + "  DRIVERLESS " + RailwayData.get(world).trains().size() + "  STOPPED " + ad.stopped.size());
                lines.add("COMPANIES " + CompanyData.get(world).all().size() + "  LEASES " + act + "  AGREEMENTS " + ad.agreements.size() + "  SLAS " + ad.slas.size());
                lines.add("ALARMS " + open + "  PAUSED " + Sched.PAUSED.size() + "  DESKS HELD " + Locks.list(world).size());
                lines.add(Backups.lastSnapshotMs == 0 ? "NO SNAPSHOT YET" : "LAST SNAPSHOT " + (System.currentTimeMillis() - Backups.lastSnapshotMs) / 60000 + " MIN AGO");
                lines.add(Backups.RECOVERY.isEmpty() ? "CLEAN START - NO RECOVERY NEEDED" : Backups.RECOVERY.get(0));
                lines.add("ACTIVE CELLS " + Sched.activeCells(world) + "  SLOWDOWN X" + Sched.slowdown(world));
                break;
            }
            case 1: {
                List<Locomotive> ls = AdminOps.locos(world);
                if (ls.isEmpty()) { lines.add("NO TRAINS LOADED"); break; }
                lines.add("TRAIN " + (s + 1) + "/" + n + "  ACTION = SAFE STOP / RELEASE");
                lines.addAll(AdminOps.inspect(world, ls.get(s)));
                break;
            }
            case 2: {
                List<String> ss = new ArrayList<>(Sched.subsystems());
                if (ss.isEmpty()) { lines.add("PROFILER OFF"); break; }
                lines.add("SUBSYSTEM " + (s + 1) + "/" + n + " " + ss.get(s) + (Sched.PAUSED.containsKey(ss.get(s)) ? " PAUSED" : Sched.SAFETY.contains(ss.get(s)) ? " SAFETY" : ""));
                lines.addAll(Sched.report(sv));
                break;
            }
            case 3: {
                List<Locks.Lock> ls = Locks.list(world);
                if (ls.isEmpty()) { lines.add("NO DESK HOLDS AN AREA"); break; }
                Locks.Lock l = ls.get(s);
                lines.add("AREA " + (s + 1) + "/" + n + " " + l.area);
                lines.add("HELD BY " + l.name + " AT " + l.desk);
                lines.add("FOR " + Locks.mins(world.getTotalWorldTime() - l.since) + (l.want == null ? "" : "  HANDOVER ASKED BY " + l.wantName));
                lines.addAll(Locks.briefing(world, l.area));
                break;
            }
            case 4: {
                lines.add(snaps.isEmpty() ? "NO SNAPSHOTS - ACTION TAKES ONE" : "SNAPSHOT " + (s + 1) + "/" + n + " " + snaps.get(s).getName());
                lines.add(Backups.writing ? "WRITING..." : Backups.lastResult);
                lines.add("ACTION = SNAPSHOT NOW  RESTORE = STAGE (KEY ARMED)");
                for (int i = 0; i < Math.min(5, snaps.size()); i++) lines.add((i == s ? "> " : "  ") + snaps.get(i).getName());
                break;
            }
            case 5: {
                int d = SPANS[span];
                lines.addAll(Stats.lines(Stats.get(world).span(d), d == 0 ? "ALL TIME" : "LAST " + d + " DAYS"));
                break;
            }
            case 6: {
                lines.add("PROFILE " + Profiles.NAMES[s] + "  NOW " + Profiles.current() + (armed() ? "  ARMED" : "  KEY SAFE"));
                List<String> df = Profiles.diff(s);
                lines.add(df.isEmpty() ? "NO CHANGES - ALREADY IN FORCE" : df.size() + " SETTINGS WOULD CHANGE:");
                lines.addAll(df);
                break;
            }
            default: {
                AdminData ad = AdminData.get(world);
                CompanyData cd = CompanyData.get(world);
                for (AdminData.Lease l : ad.leases) if (l.state == AdminData.State.ACTIVE || l.state == AdminData.State.OFFERED)
                    lines.add("L" + l.id + " " + l.state + " " + l.stockName + " " + l.lessorName + ">" + nm(cd, l.lesseeCo) + " " + Coins.fmt(l.rate) + "/D " + l.daysRun + "/" + l.days + "D");
                for (AdminData.Agreement a : ad.agreements) if (a.state != AdminData.State.ENDED && a.state != AdminData.State.REFUSED)
                    lines.add("A" + a.id + " " + a.state + " " + nm(cd, a.operatorCo) + " ON " + nm(cd, a.ownerCo) + " " + Coins.fmt(a.perKm) + "/KM OWED " + Coins.fmt(a.owed));
                for (AdminData.Sla x : ad.slas) if (x.state == AdminData.State.ACTIVE || x.state == AdminData.State.OFFERED)
                    lines.add("S" + x.id + " " + x.state + " " + x.line + " BREACHES " + x.breaches + (x.history.isEmpty() ? "" : " " + x.history.get(0)));
                if (lines.isEmpty()) lines.add("NO LEASES, AGREEMENTS OR SLAS");
                break;
            }
        }
    }

    private static String nm(CompanyData cd, int id) { com.dogpound.railmap.economy.Company c = cd.byId(id); return c == null ? "?" : c.mark.isEmpty() ? c.name : c.mark; }

    @Override
    protected boolean refresh() {
        boolean ch = false;
        if (age % 100 == 3 || (snaps.isEmpty() && age % 40 == 3)) snaps = Backups.list(server());
        build();
        for (int i = 0; i < 8; i++) ch |= txt("t" + (i + 1), i < lines.size() ? cut(lines.get(i).replaceAll("§.", ""), 52).toUpperCase(Locale.ROOT) : "");
        MinecraftServer sv = server();
        double ms = Sched.mspt(sv);
        boolean recov = false, sla = false;
        for (AlarmManager.Alarm a : AlarmManager.get(world).all()) {
            if (a.seq(world.getTotalWorldTime()) == 0) continue;
            if (a.key.equals("admin:recovery")) recov = true;
            if (a.key.startsWith("admin:sla") || a.key.startsWith("admin:unauth")) sla = true;
        }
        ch |= txt("page", PAGES[page()]);
        ch |= txt("tps", String.format(Locale.ROOT, "%.1f TPS", Math.min(20, 1000 / Math.max(50, ms))));
        ch |= put("g_mspt", (int) Math.min(100, Math.round(ms)));
        ch |= put("g_share", (int) Math.min(100, Math.round(Sched.costMs(sv) * 2)));
        ch |= put("cells", Sched.activeCells(world));
        ch |= put("l_power", 1);
        ch |= put("l_armed", armed());
        ch |= put("l_paused", !Sched.PAUSED.isEmpty());
        ch |= put("l_locks", !Locks.list(world).isEmpty());
        ch |= put("l_backup", Backups.lastSnapshotMs > 0 && (AdminConfig.snapshotMinutes == 0 || System.currentTimeMillis() - Backups.lastSnapshotMs < AdminConfig.snapshotMinutes * 120_000L));
        ch |= put("l_recovery", recov);
        ch |= put("l_overload", ms > 50);
        ch |= put("l_sla", sla);
        ch |= put("l_allstop", ConsoleControl.allStop(world.provider.getDimension()));
        ch |= put("r_disk", Backups.writing);
        ch |= put("r_net", sv != null && sv.getCurrentPlayerCount() > 0);
        ch |= put("r_cpu", ms > 25);
        return ch;
    }

    private boolean put(String id, int v) { return StationControls.put(ctl, id, v); }
    private boolean put(String id, boolean v) { return StationControls.put(ctl, id, v); }
    private boolean txt(String id, String t) { return StationControls.text(ctl, id, t); }

    @Override public String wiredName() { return "Railway administration console"; }
    @Override public String gearState() { return term.powered() ? PAGES[page()] + (armed() ? ", key ARMED" : "") : "NO 12 V - console dark"; }
}
