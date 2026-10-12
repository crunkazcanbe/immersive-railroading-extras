package com.dogpound.railmap.depot;

import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.server.StationData;
import com.dogpound.railmap.server.WearData;
import com.dogpound.railmap.station.StationFacilities;
import net.minecraft.world.World;

import java.util.*;

/**
 * The Control Center's DEPOT tab data (PacketRailState.depot), rows tagged:
 * U vehicle · J job card · L line allocation · C driver · H crew log · D download · S station audit.
 * And the tab's buttons (PacketDepotOp).
 */
public final class DepotConsole {
    private DepotConsole() {}

    public static void fill(World w, List<String[]> out) {
        FleetData f = FleetData.get(w);
        WearData wd = WearData.get(w);
        List<Map.Entry<UUID, FleetData.Unit>> us = new ArrayList<>(f.units().entrySet());
        us.removeIf(e -> e.getValue().name.isEmpty());
        us.sort(Comparator.comparing((Map.Entry<UUID, FleetData.Unit> e) -> !e.getValue().loco).thenComparing(e -> e.getValue().name));
        for (Map.Entry<UUID, FleetData.Unit> e : us) {
            if (out.size() > 150) break;
            FleetData.Unit u = e.getValue();
            WearData.Record r = wd.peek(e.getKey());
            double[] fp = Predict.first(u);
            StringBuilder parts = new StringBuilder();
            if (r != null) for (float x : r.wear) parts.append(parts.length() > 0 ? "," : "").append(Math.round(x));
            out.add(new String[]{"U", e.getKey().toString(), u.name, FleetData.ROLES[u.role], String.valueOf(r == null ? 0 : Math.round(r.worst())), Condition.NAMES[Condition.worst(r)],
                    String.valueOf(Math.round(Predict.used(w, f, u) * 100)), fp == null ? "" : WearData.Part.values()[(int) fp[0]].label + " " + String.format(Locale.ROOT, "%.1f km", fp[1]),
                    String.format(Locale.ROOT, "%.1f", u.km), String.format(Locale.ROOT, "%.1f", u.hours), u.passenger ? String.valueOf(Servicing.need(u)) : "", u.failed ? "1" : "0",
                    parts.toString(), u.loco ? "1" : "0", u.cap < 1e9 ? Math.round(u.cap) + " " + u.capWhy : ""});
        }
        for (FleetData.Job j : f.jobs) if (j.state < 3 || w.getTotalWorldTime() - j.done < 24000)
            out.add(new String[]{"J", String.valueOf(j.id), j.unitName, j.task, j.assignee, Jobs.STATES[j.state], String.valueOf(j.seconds * 100 / Math.max(1, DepotConfig.fitterSeconds))});
        for (String l : new TreeSet<>(RailwayData.get(w).lines().keySet())) {
            int run = 0;
            for (RailwayData.AutoTrain a : RailwayData.get(w).trains().values()) { FleetData.Unit u = f.peek(a.loco); if (l.equals(a.line) && (u == null || u.role == FleetData.SERVICE)) run++; }
            out.add(new String[]{"L", l, String.valueOf(f.lineTarget.getOrDefault(l, 0)), String.valueOf(run), f.attended.contains(l) ? "1" : "0"});
        }
        for (FleetData.Driver d : f.drivers.values())
            out.add(new String[]{"C", badge(d.player) + d.name, !d.onDuty ? "OFF" : Crew.onBreak(w, d) ? "BREAK" : "ON", String.format(Locale.ROOT, "%.1f", Crew.dutyHours(w, d)), String.format(Locale.ROOT, "%.1f", Crew.sinceBreak(w, d)),
                    d.train == null ? "" : f.unit(d.train).name, ((d.quals & 1) != 0 ? "D" : "-") + ((d.quals & 2) != 0 ? "E" : "-") + ((d.quals & 4) != 0 ? "S" : "-"), String.join(",", d.routes)});
        for (int i = 0; i < Math.min(12, f.crewLog.size()); i++) out.add(new String[]{"H", f.crewLog.get(i)});
        try { com.dogpound.railmap.crew.CrewConsole.fill(w, out); } catch (RuntimeException | LinkageError ignored) { }   // batch 5 #7 #9: N crew people, R revenue protection
        for (net.minecraft.entity.player.EntityPlayer pl : w.playerEntities)              // operators online + their career rank (quest mod)
            out.add(new String[]{"O", pl.getName(), com.dogpound.railmap.api.QuestEvents.rankOf(pl.getUniqueID())});
        for (int i = 0; i < Math.min(8, f.downloads.size()); i++) {
            FleetData.Download d = f.downloads.get(i);
            out.add(new String[]{"D", String.valueOf(d.id), d.unit, String.valueOf((w.getTotalWorldTime() - d.tick) * 60 / 1000), String.join("\n", d.lines)});
        }
        for (StationFacilities.Audit a : StationFacilities.audits(w).values())
            if (StationData.get(w).names().containsKey(a.station))
                out.add(new String[]{"S", a.name, a.grade(), String.valueOf(a.rating), a.stepFree ? "1" : "0", a.liftsOk + "/" + a.lifts, String.valueOf(a.tactile), a.working + "/" + a.total,
                        com.dogpound.railmap.economy.Coins.fmt(a.cost), com.dogpound.railmap.economy.Coins.fmt(a.incomeYesterday)});
        com.dogpound.railmap.steam.SteamConsole.fill(w, out);            // batch 5 #2: DEPOT > Steam page
        com.dogpound.railmap.traction.TractionConsole.fill(w, out);      // batch 5 #6: DEPOT > Traction page
    }

    /** a console button: returns what to tell the player */
    public static String op(World w, int op, String arg, String who) {
        FleetData f = FleetData.get(w);
        UUID u = null;
        try { u = UUID.fromString(arg); } catch (IllegalArgumentException ignored) { }
        TileDepotOffice o = null;
        if (u != null) {
            cam72cam.immersiverailroading.entity.Locomotive l = com.dogpound.railmap.auto.Autopilot.find(w, u);
            o = l == null ? null : Fleet.nearestOffice(w, Fleet.at(l), true);
            if (o == null && f.peek(u) != null && f.peek(u).depot != 0) o = Fleet.office(w, f.peek(u).depot);
            if (o == null) o = TileDepotOffice.loaded(w).stream().filter(x -> x.road() != 0).findFirst().orElse(null);
        }
        return switch (op) {
            case 1 -> u == null ? "No vehicle" : Fleet.sendToDepot(w, u, o, "sent from the control center");
            case 2 -> u == null ? "No vehicle" : Fleet.returnToService(w, u, false);
            case 3 -> u == null ? "No vehicle" : Fleet.makeSpare(w, u, o);
            case 4 -> {
                if (u == null) yield "No vehicle";
                FleetData.Unit x = f.unit(u);
                x.role = x.role == FleetData.STOPPED ? FleetData.DEPOT : FleetData.STOPPED;
                x.note(w, (x.role == FleetData.STOPPED ? "STOPPED" : "released") + " from the control center by " + who);
                f.markDirty();
                yield x.name + (x.role == FleetData.STOPPED ? " STOPPED" : " released");
            }
            case 5, 6 -> {
                int t = Math.max(0, Math.min(12, f.lineTarget.getOrDefault(arg, 0) + (op == 5 ? 1 : -1)));
                f.lineTarget.put(arg, t);
                f.markDirty();
                yield arg + ": " + (t == 0 ? "not managed" : t + " trains wanted");
            }
            case 7 -> { boolean on = !f.attended.remove(arg); if (on) f.attended.add(arg); f.markDirty(); f.crewNote(w, arg + (on ? " ATTENDED" : " unattended") + " (control center)"); yield arg + (on ? " ATTENDED" : " unattended"); }
            case 8 -> u == null ? "No vehicle" : "Job card #" + Jobs.raise(w, u, f.unit(u).name, Jobs.EXAM, -1, o == null ? 0 : o.getPos().toLong(), "raised from the control center by " + who).id + " EXAM";
            default -> null;
        };
    }

    /** "§6[Driver]§r " for a player driver with a career rank, else "" */
    static String badge(java.util.UUID player) {
        String r = com.dogpound.railmap.api.QuestEvents.rankOf(player);
        return r.isEmpty() ? "" : "§6[" + r + "]§r ";
    }
}
