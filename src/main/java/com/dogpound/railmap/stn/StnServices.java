package com.dogpound.railmap.stn;

import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.incident.Announcer;
import com.dogpound.railmap.server.StationData;
import com.dogpound.railmap.station.Pis;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

import static com.dogpound.railmap.stn.StnConsole.*;

/**
 * Station services (ChatGPT batch 3 #26 #28 #30 #31 #34 #35): the STN console pages, their buttons, and
 * /irextras stn formation|diagram|interchange|epower|lost|parcels|disrupt|selftest.
 */
public final class StnServices {
    private StnServices() {}

    // ---- console -----------------------------------------------------------------------------------------------------
    public static void fill(World w, List<String[]> out) {
        RailwayData rd = RailwayData.get(w);
        StnData d = StnData.get(w);
        int h = com.dogpound.railmap.economy.PassengerData.hour(w);
        // #26
        page(out, "Formation", "#26 formation by demand: cars per train recommended from each line's demand profile at " + StnConfig.targetLoadPct + "% load (Formation Planner block)");
        cols(out, "Line:0", "Trains:16", "Cars:24", "Seats:31", "Load:39", "Demand now:47", "Rec now:60", "Rec peak:70", "Advice:80");
        List<String> lines = new ArrayList<>(rd.lines().keySet());
        Collections.sort(lines);
        for (String l : lines) {
            FormationDemand.LineNow n = FormationDemand.now(w, l);
            double[] p = d.demand(l);
            double peak = 0;
            for (int i = 0; i < 24; i++) if (com.dogpound.railmap.economy.PassengerData.peak(i)) peak = Math.max(peak, p[i]);
            int rec = FormationDemand.recommend(Math.max(p[h], n.aboardMax), n.perCar, StnConfig.targetLoadPct, 24), rp = FormationDemand.recommend(peak, n.perCar, StnConfig.targetLoadPct, 24);
            String adv = n.trains == 0 ? ".no trains" : n.cars > rec ? "~detach " + (n.cars - rec) : n.cars < rec ? "!attach " + (rec - n.cars) : "+ok";
            row(out, l, l, String.valueOf(n.trains), String.valueOf(n.cars), String.valueOf(n.seats), Math.round(n.loadPct) + "%", String.valueOf(Math.round(p[h])), String.valueOf(rec), String.valueOf(rp), adv);
        }
        btn(out, "RESET PROFILE", "form.reset", true);
        // #31
        page(out, "Diagrams", "#31 service diagrams: each unit's daily workings (Service Diagram Board block); ENFORCE sends a unit to depot outside its hours");
        cols(out, "Diagram:0", "Unit:9", "Line:27", "Hours:39", "Trips:52", "Calls:58", "Late:65", "First:71", "Status:80");
        for (Map.Entry<UUID, StnData.Diagram> e : Diagrams.list(w)) {
            StnData.Diagram g = e.getValue();
            RailwayData.AutoTrain a = rd.train(e.getKey());
            String st = Diagrams.status(w, e.getKey(), g);
            row(out, e.getKey().toString(), g.code, g.unit, a == null ? "" : a.line, g.start < 0 ? ".-" : String.format(Locale.ROOT, "%02d-%02d%s", g.start, g.end, g.enforce ? " ENF" : ""),
                    String.valueOf(g.trips), String.valueOf(g.calls), (g.lateCalls > 0 ? "~" : "") + g.lateCalls, g.firstDep == 0 ? ".--:--" : com.dogpound.railmap.alarm.Recorder.clock(w, g.firstDep),
                    (st.startsWith("RUNNING") || st.startsWith("AT STN") ? st.contains("+") ? "~" : "+" : "!") + st);
        }
        btn(out, "ENFORCE ON/OFF", "diag.enforce", true);
        btn(out, "CLEAR HOURS", "diag.clear", true);
        // #28
        page(out, "Interchange", "#28 connections between lines with platform walking times (Interchange Board sets them; default " + StnConfig.defaultWalkMinutes + " min)");
        cols(out, "Station:0", "From:18", "Arr min:32", "To:42", "Dep min:56", "Walk:66", "Status:74");
        for (Map.Entry<Long, String> e : StationData.get(w).names().entrySet()) {
            if (Interchange.lines(w, e.getKey()).size() < 2) continue;
            for (Interchange.Conn c : Interchange.connections(w, e.getKey(), e.getValue())) {
                String s = c.status();
                row(out, c.dep.dwelling ? c.dep.loco.toString() : "", e.getValue(), c.arr.line + " " + c.arr.train, String.valueOf(Math.round(c.arrMin)), c.dep.line + " " + c.dep.train,
                        String.valueOf(Math.round(c.depMin)), c.walk + " min", (s.equals("MAKE") ? "+" : s.equals("TIGHT") ? "~" : "!") + s + (c.dep.dwelling ? " (standing)" : ""));
            }
        }
        btn(out, "HOLD 2 MIN", "ich.hold", true);
        // #30
        page(out, "Em power", "#30 station emergency power: incoming mains, transfer switch, generator, UPS battery, what is fed / shed");
        cols(out, "Station:0", "Mains:20", "ATS:30", "Generator:42", "Battery:56", "Fed:66", "Shed:72", "Level:80");
        for (TileStationPower t : StnUtil.loaded(w, TileStationPower.class)) {
            String[] s = t.summary().split("\\|", -1);
            row(out, String.valueOf(t.getPos().toLong()), s[0], (s[7].equals("OK") ? "+" : "!") + s[7], (s[1].equals("NORMAL") ? "+" : "~") + s[1], (s[2].equals("FAILED") ? "!" : "") + s[2],
                    (Integer.parseInt(s[3]) < 20 ? "!" : "") + s[3] + "%", s[4], (s[5].equals("0") ? "" : "~") + s[5], s[6]);
        }
        btn(out, "START GENERATOR", "epow.start", true);
        // #34
        page(out, "Parcels", "#34 parcels by train between Parcels Offices");
        cols(out, "#:0", "Parcel:6", "From:36", "To:52", "State:68", "Train:80");
        for (int i = d.parcels.size() - 1, n = 0; i >= 0 && n < 120; i--, n++) {
            StnData.Parcel p = d.parcels.get(i);
            row(out, String.valueOf(p.id), String.valueOf(p.id), p.what, p.fromName, p.toName, (p.state == 2 ? "+" : p.state == 3 ? "." : "") + StnData.PSTATE[p.state], p.state == 1 ? p.trainName : "");
        }
        btn(out, "PURGE COLLECTED", "parcel.purge", false);
        page(out, "Lost prop", "#34 lost property register (Lost Property Office block)");
        cols(out, "#:0", "When:6", "Station:16", "Item:34", "Owner:66", "State:80");
        for (String l : d.lost) {
            String[] p = l.split("\\|", -1);
            if (p.length < 6) continue;
            row(out, "", p[0], com.dogpound.railmap.alarm.Recorder.clock(w, Long.parseLong(p[1])), p[2], p[3], p[4].isEmpty() ? ".unknown" : p[4], (p[5].startsWith("CLAIMED") ? "+" : p[5].equals("UNCLAIMED") ? "~" : "") + p[5]);
        }
        // #35
        page(out, "Disruption", "#35 disrupted services and the travel alternatives the PIS gives their passengers");
        cols(out, "Station:0", "Service:16", "Late:34", "Why:40", "Alternative:58");
        for (Map.Entry<Long, String> e : StationData.get(w).names().entrySet())
            for (Map.Entry<Announcer.Due, List<String>> x : Disruption.at(w, e.getKey(), e.getValue()).entrySet()) {
                Announcer.Due du = x.getKey();
                List<String> alts = x.getValue().isEmpty() ? Collections.singletonList("") : x.getValue();
                for (String a : alts)
                    row(out, String.valueOf(e.getKey()), e.getValue(), du.line + " " + du.train, "!" + du.lateMin + "m", du.held ? "held: " + du.reason : "late", a.replaceFirst("^Disruption on the [^:]*: ", ""));
            }
        btn(out, "POST ALTERNATIVES", "dis.post", true);
        btn(out, "ANNOUNCE", "dis.say", true);
        txt(out, "Held lines now: " + (Disruption.heldLines(w).isEmpty() ? "none" : String.join(", ", Disruption.heldLines(w))));
    }

    public static String op(EntityPlayer p, String op, String arg) {
        World w = p.world;
        StnData d = StnData.get(w);
        switch (op) {
            case "form.reset" -> { d.demand.remove(arg); d.markDirty(); return "§eDemand profile of " + arg + " reset"; }
            case "diag.enforce", "diag.clear" -> {
                UUID u;
                try { u = UUID.fromString(arg); } catch (IllegalArgumentException e) { return "§7Select a unit"; }
                StnData.Diagram g = d.diagrams.get(u);
                if (g == null) return "§7No diagram";
                if (op.equals("diag.clear")) { g.start = g.end = -1; g.enforce = false; } else g.enforce = !g.enforce;
                d.markDirty();
                return "§eDiagram " + g.code + ": " + (g.start < 0 ? "no hours" : String.format(Locale.ROOT, "%02d-%02d", g.start, g.end)) + (g.enforce ? " ENFORCED" : "");
            }
            case "ich.hold" -> {
                try { return Autopilot.adjustDwell(UUID.fromString(arg), w.getTotalWorldTime(), 120) ? "§aHeld 2 minutes for the connection" : "§7That train is no longer standing"; }
                catch (IllegalArgumentException e) { return "§7Select a standing train's connection"; }
            }
            case "epow.start" -> {
                try { if (w.getTileEntity(BlockPos.fromLong(Long.parseLong(arg))) instanceof TileStationPower t) return t.startGen(p.getName()); } catch (NumberFormatException ignored) { }
                return "§7Select a panel";
            }
            case "parcel.purge" -> { int n = d.parcels.size(); d.parcels.removeIf(x -> x.state == 3); d.markDirty(); return "§e" + (n - d.parcels.size()) + " collected parcel(s) purged"; }
            case "dis.post", "dis.say" -> {
                long key;
                try { key = Long.parseLong(arg); } catch (NumberFormatException e) { return "§7Select a station"; }
                String name = StationData.get(w).names().getOrDefault(key, "");
                int n = 0;
                for (List<String> alts : Disruption.at(w, key, name).values())
                    for (String a : alts) { if (op.equals("dis.post")) Pis.get(w).post(w, name, Pis.Kind.ALT, a, p.getName(), 30); else Announcer.say(w, key, name, "ALT", a); n++; }
                return n == 0 ? "§7Nothing disrupted at " + name : "§a" + n + " alternative(s) " + (op.equals("dis.post") ? "posted" : "announced") + " at " + name;
            }
            default -> { return null; }
        }
    }

    // ---- command ---------------------------------------------------------------------------------------------------------
    private static void say(ICommandSender s, String t) { StnCommand.say(s, t); }

    public static boolean command(World w, ICommandSender s, String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
        String rest = a.length > 2 ? String.join(" ", Arrays.copyOfRange(a, 2, a.length)) : "";
        RailwayData rd = RailwayData.get(w);
        switch (sub) {
            case "selftest" -> { selftest(w, s); return true; }
            case "formation" -> {
                for (String l : rd.lines().keySet()) {
                    if (!rest.isEmpty() && !l.equalsIgnoreCase(rest)) continue;
                    FormationDemand.LineNow n = FormationDemand.now(w, l);
                    double[] p = StnData.get(w).demand(l);
                    StringBuilder b = new StringBuilder();
                    for (int i = 0; i < 24; i += 2) b.append(Math.round(Math.max(p[i], p[i + 1]))).append(i < 22 ? " " : "");
                    say(s, String.format(Locale.ROOT, "§e%s§7: %d trains, %d cars, %d seats, load %d%%, %.0f seats/car · profile [%s] · recommend %d cars now",
                            l, n.trains, n.cars, n.seats, Math.round(n.loadPct), n.perCar, b, FormationDemand.recommend(Math.max(p[com.dogpound.railmap.economy.PassengerData.hour(w)], n.aboardMax), n.perCar, StnConfig.targetLoadPct, 24)));
                }
                return true;
            }
            case "diagram" -> {
                for (Map.Entry<UUID, StnData.Diagram> e : Diagrams.list(w)) {
                    StnData.Diagram g = e.getValue();
                    say(s, "§e" + g.code + " §f" + g.unit + "§7 trips " + g.trips + " calls " + g.calls + " late " + g.lateCalls + " · " + Diagrams.status(w, e.getKey(), g)
                            + (g.start < 0 ? "" : String.format(Locale.ROOT, " · %02d-%02d%s", g.start, g.end, g.enforce ? " ENF" : "")));
                    if (!rest.isEmpty() && (g.code.equalsIgnoreCase(rest) || g.unit.equalsIgnoreCase(rest))) for (String l : Diagrams.book(w, g)) say(s, "§7  " + l);
                }
                return true;
            }
            case "interchange" -> {
                for (Map.Entry<Long, String> e : StationData.get(w).names().entrySet()) {
                    if (!rest.isEmpty() && !e.getValue().equalsIgnoreCase(rest)) continue;
                    List<String> ls = Interchange.lines(w, e.getKey());
                    if (ls.size() < 2) continue;
                    say(s, "§e" + e.getValue() + "§7: lines " + String.join(", ", ls) + (Interchange.board(w, e.getValue()) == null ? " (no board - " + StnConfig.defaultWalkMinutes + " min walks)" : ""));
                    for (Interchange.Conn c : Interchange.connections(w, e.getKey(), e.getValue()))
                        say(s, String.format(Locale.ROOT, "§7  %s %s in %.0f min > %s %s leaves %.0f min, walk %d: §f%s", c.arr.line, c.arr.train, c.arrMin, c.dep.line, c.dep.train, c.depMin, c.walk, c.status()));
                }
                return true;
            }
            case "epower" -> {
                List<TileStationPower> ps = StnUtil.loaded(w, TileStationPower.class);
                if (ps.isEmpty()) say(s, "§7No Station Emergency Power panels loaded");
                for (TileStationPower t : ps) say(s, "§e" + t.getPos() + "§7 " + t.gearState());
                if (rest.startsWith("test")) for (TileStationPower t : ps) say(s, "§7  gen test: " + t.startGen(s.getName()));
                return true;
            }
            case "lost" -> {
                for (int i = 0; i < Math.min(20, StnData.get(w).lost.size()); i++) say(s, "§7" + StnData.get(w).lost.get(i).replace("|", " · "));
                if (StnData.get(w).lost.isEmpty()) say(s, "§7The lost property register is empty");
                return true;
            }
            case "parcels" -> {
                for (StnData.Parcel p : StnData.get(w).parcels) {
                    say(s, "§e#" + p.id + " §f" + p.what + "§7 " + p.fromName + " > " + p.toName + " · " + StnData.PSTATE[p.state] + (p.state == 1 ? " on " + p.trainName : ""));
                    if (rest.equals(String.valueOf(p.id))) for (String l : p.log) { String[] x = l.split("\\|", 2); say(s, "§7  " + com.dogpound.railmap.alarm.Recorder.clock(w, Long.parseLong(x[0])) + " " + (x.length > 1 ? x[1] : "")); }
                }
                if (StnData.get(w).parcels.isEmpty()) say(s, "§7No parcels");
                return true;
            }
            case "disrupt" -> {
                say(s, "§eHeld lines: §f" + (Disruption.heldLines(w).isEmpty() ? "none" : String.join(", ", Disruption.heldLines(w))));
                for (Map.Entry<Long, String> e : StationData.get(w).names().entrySet()) {
                    if (!rest.isEmpty() && !e.getValue().equalsIgnoreCase(rest)) continue;
                    for (Map.Entry<Announcer.Due, List<String>> x : Disruption.at(w, e.getKey(), e.getValue()).entrySet()) {
                        say(s, "§e" + e.getValue() + "§7: " + x.getKey().line + " " + x.getKey().train + " +" + x.getKey().lateMin + " min" + (x.getKey().held ? " HELD (" + x.getKey().reason + ")" : ""));
                        for (String alt : x.getValue()) say(s, "§7  > " + alt);
                    }
                }
                return true;
            }
            default -> { return false; }
        }
    }

    // ---- self-test -------------------------------------------------------------------------------------------------------
    private static int pass, fail;

    private static void check(ICommandSender s, String what, boolean ok) {
        if (ok) pass++; else fail++;
        say(s, (ok ? "§a PASS " : "§c FAIL ") + "§7" + what);
    }

    public static void selftest(World w, ICommandSender s) {
        pass = fail = 0;
        // #26: 200 people at 85 % of 60 seats a car -> 4 cars; capped by the platforms
        check(s, "formation: 200 pax, 60 seats/car, 85 % -> 4 cars", FormationDemand.recommend(200, 60, 85, 12) == 4);
        check(s, "formation: capped at the max cars", FormationDemand.recommend(2000, 60, 85, 6) == 6);
        check(s, "formation: no demand -> 1 car", FormationDemand.recommend(0, 60, 85, 6) == 1);
        // #28: connection margins
        Interchange.Conn c = new Interchange.Conn();
        c.margin = 3; boolean m1 = c.status().equals("MAKE");
        c.margin = 0.5; boolean m2 = c.status().equals("TIGHT");
        c.margin = -1; boolean m3 = c.status().equals("MISS");
        check(s, "interchange: MAKE / TIGHT / MISS thresholds", m1 && m2 && m3);
        check(s, "interchange: default walk with no board = config", Interchange.walkMin(w, "~no such station~", "A", "B") == StnConfig.defaultWalkMinutes);
        // #30: emergency bus priorities
        check(s, "emergency power: platform lamp is life safety (1)", EmergencyPower.priority("Platform lamp") == 1);
        check(s, "emergency power: lift 2, escalator 3, vending 4", EmergencyPower.priority("Station lift") == 2 && EmergencyPower.priority("Escalator") == 3 && EmergencyPower.priority("Vending machine") == 4);
        check(s, "emergency power: depot wheel lathe never on the station bus", EmergencyPower.priority("Wheel lathe") == 5);
        // #31 + #34: the saved data survives a save / load
        StnData t = new StnData("selftest");
        StnData.Diagram g = t.diagram(new UUID(1, 2), "UNIT 1", "Red");
        g.start = 6; g.end = 22; g.enforce = true; g.work.add("100|A|ARR|0|Red");
        StnData.Parcel p = t.newParcel(w, 1, "A", 2, "B", "tester", new ItemStack(net.minecraft.init.Items.APPLE, 5));
        p.state = 1; p.stack = new ItemStack(net.minecraft.init.Items.APPLE, 5); p.train = new UUID(3, 4);
        t.lostEntry(w, "A", "1 x Stick", "tester");
        NBTTagCompound n = t.writeToNBT(new NBTTagCompound());
        StnData r = new StnData("selftest2");
        r.readFromNBT(n);
        StnData.Diagram g2 = r.diagrams.get(new UUID(1, 2));
        check(s, "diagrams: code " + g.code + ", hours and workings saved", g2 != null && g2.code.equals(g.code) && g2.start == 6 && g2.end == 22 && g2.enforce && g2.work.size() == 1);
        StnData.Parcel p2 = r.parcels.isEmpty() ? null : r.parcels.get(0);
        check(s, "parcels: on-train goods travel in the saved record", p2 != null && p2.state == 1 && p2.stack.getCount() == 5 && new UUID(3, 4).equals(p2.train));
        check(s, "lost property: register saved", r.lost.size() == 1 && r.lost.get(0).contains("Stick"));
        // #35: alternatives never throw, and a station with no lines has none
        boolean ok = true;
        try { Disruption.heldLines(w); } catch (RuntimeException e) { ok = false; }
        check(s, "disruption: held-line scan runs", ok);
        check(s, "PIS has the ALT message kind", Pis.Kind.valueOf("ALT").prio == 5);
        check(s, "parcels: no route between unknown stations", !Parcels.reachable(w, 0, 1));
        say(s, (fail == 0 ? "§a" : "§c") + "Station services self-test: " + pass + " passed, " + fail + " failed");
    }
}
