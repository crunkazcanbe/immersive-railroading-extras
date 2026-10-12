package com.dogpound.railmap.crew;

import cam72cam.immersiverailroading.entity.Locomotive;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.depot.Crew;
import com.dogpound.railmap.depot.FleetData;
import com.dogpound.railmap.economy.Coins;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.*;

/**
 * /irextras crew ... - train crew NPCs and ticket inspectors (batch 5 #7 #9):
 * <pre>
 *   crew                          everyone: role, phase, what they're doing, diagram, fatigue
 *   crew hire driver|guard|inspector [name]
 *   crew shift <name> <startHour> <hours>|none
 *   crew assign <name>            the nearest train (driver / guard)
 *   crew beat <name> <line>|any   an inspector's or guard's line
 *   crew on|off <name>            sign on / off by hand
 *   crew dismiss <name>
 *   crew auto on|off              diagrams run automatically
 *   crew check                    have the nearest inspector check your ticket now (test)
 *   crew stats                    revenue protection today / yesterday / all time + recent cases
 *   crew npcs                     the crew people being shown
 *   crew selftest                 roster, rest, fatigue, penalty and ticket-validity rules
 * </pre>
 */
public final class CrewCommand {
    private CrewCommand() {}

    public static final String USAGE = "crew [hire <role> [name]|shift <name> <h> <len>|assign <name>|beat <name> <line>|on|off <name>|dismiss <name>|auto on|off|check|stats|npcs|selftest]";

    public static boolean command(World w, ICommandSender s, String[] a) {
        if (a.length == 0 || !a[0].equalsIgnoreCase("crew")) return false;
        try { run(w, s, a); } catch (RuntimeException | LinkageError e) { say(s, "§c" + e); }
        return true;
    }

    private static void say(ICommandSender s, String t) { s.sendMessage(new TextComponentString(t)); }

    private static CrewData.Staff who(World w, ICommandSender s, String[] a, int i) {
        if (a.length <= i) { say(s, "§7Name a crew member"); return null; }
        String n = String.join(" ", Arrays.copyOfRange(a, i, a.length));
        CrewData.Staff st = CrewData.get(w).staff.get(n);
        if (st == null) for (CrewData.Staff x : CrewData.get(w).staff.values()) if (x.name.equalsIgnoreCase(n)) st = x;
        if (st == null) say(s, "§cNo crew member '" + n + "'");
        return st;
    }

    private static void run(World w, ICommandSender s, String[] a) {
        CrewData c = CrewData.get(w);
        FleetData f = FleetData.get(w);
        long now = w.getTotalWorldTime();
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "" -> {
                say(s, "§6Crew: §f" + c.staff.size() + " · offices " + c.offices.size() + " · revenue offices " + c.rpOffices.size() + " · diagrams " + (c.autoRoster ? "AUTO" : "manual"));
                for (CrewData.Staff x : c.staff.values()) {
                    CrewTicker.Live L = CrewTicker.all(w).get(x.name);
                    say(s, String.format(Locale.ROOT, "§f%-9s §b%s§7 %s · %s · diag %s · fatigue %d%% %s", CrewData.ROLE[x.role], x.name, L == null ? "OFF" : CrewTicker.PHASE[L.phase],
                            L == null ? "" : L.doing, TileCrewOffice.shift(x), Math.round(CrewTicker.fatigue(w, x) * 100),
                            x.role == CrewData.INSPECTOR ? "· beat " + (x.line.isEmpty() ? "any" : x.line) : x.train == null ? "" : "· " + f.unit(x.train).name));
                }
                say(s, "§7" + USAGE);
            }
            case "hire" -> {
                String r = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "";
                int role = r.startsWith("d") ? CrewData.DRIVER : r.startsWith("g") ? CrewData.GUARD : r.startsWith("i") ? CrewData.INSPECTOR : 0;
                if (role == 0) { say(s, "§7crew hire driver|guard|inspector [name]"); return; }
                CrewData.Staff x = CrewTicker.hire(w, role, a.length > 3 ? String.join(" ", Arrays.copyOfRange(a, 3, a.length)) : null);
                say(s, "§a" + x.name + " hired (" + CrewData.ROLE[role] + ")");
            }
            case "shift" -> {
                if (a.length < 4) { say(s, "§7crew shift <name> <startHour> <hours>  |  crew shift <name> none"); return; }
                boolean none = a[a.length - 1].equalsIgnoreCase("none");
                CrewData.Staff x = who(w, s, Arrays.copyOf(a, a.length - (none ? 1 : 2)), 2);
                if (x == null) return;
                if (none) x.start = -1;
                else { x.start = Math.floorMod(Integer.parseInt(a[a.length - 2]), 24); x.hours = Math.max(1, Math.min(24, Integer.parseInt(a[a.length - 1]))); }
                c.markDirty();
                say(s, "§e" + x.name + " diagram " + TileCrewOffice.shift(x));
            }
            case "assign" -> {
                CrewData.Staff x = who(w, s, a, 2);
                if (x == null) return;
                Locomotive l = nearest(w, s);
                if (l == null) { say(s, "§cNo driverless train within 64 blocks"); return; }
                x.train = l.getUUID();
                c.markDirty();
                if (x.role == CrewData.DRIVER && f.drivers.get(x.name) != null) {
                    RailwayData.AutoTrain at = RailwayData.get(w).train(l.getUUID());
                    String no = Crew.cannot(w, f.drivers.get(x.name), l, at == null ? "" : at.line);
                    say(s, Crew.assign(w, f, f.drivers.get(x.name), l.getUUID(), "command") + (no == null ? "" : " §6(" + no + ")"));
                } else say(s, "§a" + x.name + " -> " + f.unit(l.getUUID()).name);
            }
            case "beat" -> {
                if (a.length < 4) { say(s, "§7crew beat <name> <line>|any"); return; }
                CrewData.Staff x = who(w, s, Arrays.copyOf(a, a.length - 1), 2);
                if (x == null) return;
                String l = a[a.length - 1];
                x.line = l.equalsIgnoreCase("any") ? "" : l;
                c.markDirty();
                say(s, "§e" + x.name + " beat: " + (x.line.isEmpty() ? "any line" : x.line));
            }
            case "on", "off" -> {
                CrewData.Staff x = who(w, s, a, 2);
                if (x == null) return;
                if (x.role == CrewData.DRIVER) {
                    FleetData.Driver d = f.drivers.get(x.name);
                    if (d == null) { say(s, "§cNo depot record"); return; }
                    if (sub.equals("off")) x.lastOff = now;
                    say(s, sub.equals("on") ? Crew.signOn(w, f, d) : Crew.signOff(w, f, d));
                } else if (sub.equals("on")) { x.onDuty = true; x.dutyStart = now; c.markDirty(); CrewTicker.live(w, x.name).phase = CrewTicker.OFF; f.crewNote(w, x.name + " SIGNED ON (command)"); say(s, "§a" + x.name + " signed on"); }
                else { CrewTicker.signOff(w, c, f, x, CrewTicker.live(w, x.name), now); say(s, "§e" + x.name + " signed off"); }
            }
            case "dismiss" -> { CrewData.Staff x = who(w, s, a, 2); if (x != null) say(s, CrewTicker.dismiss(w, x.name)); }
            case "auto" -> { c.autoRoster = a.length < 3 || a[2].equalsIgnoreCase("on"); c.markDirty(); say(s, "§eDiagrams " + (c.autoRoster ? "AUTO" : "manual")); }
            case "check" -> {
                if (!(s.getCommandSenderEntity() instanceof EntityPlayer p)) { say(s, "§7Players only"); return; }
                CrewData.Staff insp = null; CrewTicker.Live L = null; double bd = Double.MAX_VALUE;
                for (CrewData.Staff x : c.staff.values()) {
                    if (x.role != CrewData.INSPECTOR) continue;
                    CrewTicker.Live l = CrewTicker.live(w, x.name);
                    double d = l.at == null ? 1e12 : p.getDistanceSq(l.at[0], l.at[1], l.at[2]);
                    if (insp == null || d < bd) { bd = d; insp = x; L = l; }
                }
                if (insp == null) { say(s, "§7No inspectors - crew hire inspector"); return; }
                Map.Entry<Long, String> st = com.dogpound.railmap.stn.StnData.station(w, p.getPosition(), 256);
                Locomotive l = nearest(w, s);
                RailwayData.AutoTrain at = l == null ? null : RailwayData.get(w).train(l.getUUID());
                boolean onTrain = l != null && CrewTicker.riders(w, l).contains(p);
                Revenue.forget();
                Revenue.checkPlayer(w, c, insp, L, p, st == null ? 0 : st.getKey(), onTrain && at != null ? at.line : "", onTrain ? l.getUUID() : null);
            }
            case "stats" -> {
                say(s, String.format(Locale.ROOT, "§6Revenue protection §7(mode %d, penalty %s)", c.mode(), Coins.fmt(CrewConfig.penaltyCents)));
                String[] k = {"today", "yesterday", "all time"};
                long[][] v = {c.today, c.yesterday, c.total};
                for (int i = 0; i < 3; i++) say(s, String.format(Locale.ROOT, "§f%-9s §7%d checked (%d players) · %d caught · %d warned · %s", k[i], v[i][0], v[i][4], v[i][1], v[i][2], Coins.fmt(v[i][3])));
                int n = 0;
                for (CrewData.Case x : c.cases) { if (n++ >= 8) break; say(s, "§7#" + x.id + " " + x.who + " · " + x.where + " · " + x.state + (x.cents > 0 ? " " + Coins.fmt(x.cents) : "") + " · " + x.inspector + (x.cam.isEmpty() ? "" : " · " + x.cam)); }
            }
            case "npcs" -> {
                Map<String, com.dogpound.railmap.pax.EntityCrew> m = com.dogpound.railmap.pax.CrewNpcs.shown(w);
                say(s, "§6Crew shown: §f" + m.size() + " / " + CrewConfig.maxNpcs);
                for (Map.Entry<String, com.dogpound.railmap.pax.EntityCrew> e : m.entrySet())
                    say(s, String.format(Locale.ROOT, "§7%s at %.0f %.0f %.0f · %s", e.getKey(), e.getValue().posX, e.getValue().posY, e.getValue().posZ, e.getValue().note()));
            }
            case "selftest" -> {
                List<String> fail = Rules.selfTest(com.dogpound.railmap.proxy.CommonProxy.ticket);
                if (fail.isEmpty()) say(s, "§aCrew self-test: all " + 13 + " checks passed (shifts, rest, fatigue, dodger share, penalty, tickets, passes)");
                else for (String x : fail) say(s, "§cFAIL: " + x);
            }
            default -> say(s, "§7" + USAGE);
        }
    }

    /** the nearest driverless train to the sender (64 blocks) */
    static Locomotive nearest(World w, ICommandSender s) {
        Locomotive best = null; double bd = 64 * 64;
        for (UUID u : RailwayData.get(w).trains().keySet()) {
            Locomotive l = Autopilot.find(w, u);
            if (l == null) continue;
            double d = s.getPosition().distanceSq(l.getPosition().x, l.getPosition().y, l.getPosition().z);
            if (d < bd) { bd = d; best = l; }
        }
        return best;
    }
}
