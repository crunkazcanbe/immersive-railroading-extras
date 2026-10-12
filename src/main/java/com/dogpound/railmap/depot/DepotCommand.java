package com.dogpound.railmap.depot;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.server.WearData;
import com.dogpound.railmap.station.StationFacilities;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.*;

/**
 * /irextras depot ... - inspect and test the depot organisation (her ChatGPT list sections 8 and 12):
 * <pre>
 *   depot                     summary: fleet, open job cards, spares, drivers
 *   depot fleet               every vehicle: role, worst wear and condition, exam plan used
 *   depot unit                the nearest vehicle: wear + condition, restrictions, remaining life, components, history
 *   depot jobs                open job cards
 *   depot job exam|repair <part>|wheels|bogie|braketest|service   raise a card for the nearest vehicle
 *   depot send | return | spare | alloc <line>                    the nearest locomotive (driverless)
 *   depot target <line> <n>   trains a line should have (0 = not managed)
 *   depot plan <km> <hours> <days> <faults>                       the default exam plan
 *   depot download            diagnostics from the nearest locomotive
 *   depot crew | attend <line>
 *   depot dirty               make the nearest passenger vehicle need servicing (test)
 *   depot give                a set of spares (test)
 *   depot stations            accessibility audits
 * </pre>
 */
public final class DepotCommand {
    private DepotCommand() {}

    public static final String USAGE = "depot [fleet|unit|jobs|job <kind>|send|return|spare|alloc <line>|target <line> <n>|plan <km> <h> <d> <f>|download|crew|attend <line>|dirty|give|stations]";

    public static boolean command(World w, ICommandSender s, String[] a) {
        if (a.length == 0 || !a[0].equalsIgnoreCase("depot")) return false;
        try { run(w, s, a); } catch (RuntimeException | LinkageError e) { say(s, "§c" + e); }
        return true;
    }

    private static void run(World w, ICommandSender s, String[] a) {
        FleetData f = FleetData.get(w);
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
        BlockPos at = s.getPosition();
        switch (sub) {
            case "" -> {
                int spares = 0, depot = 0, stopped = 0;
                for (FleetData.Unit u : f.units().values()) { if (u.role == FleetData.SPARE) spares++; if (u.role == FleetData.DEPOT) depot++; if (u.role == FleetData.STOPPED) stopped++; }
                say(s, "§6Depot: §f" + f.units().size() + " vehicles · " + f.openJobs(null).size() + " open job cards · " + spares + " spares · " + depot + " at/to depot · " + stopped + " stopped · "
                        + f.drivers.size() + " drivers · " + TileDepotOffice.loaded(w).size() + " offices loaded");
                say(s, "§7" + USAGE);
            }
            case "fleet" -> {
                WearData wd = WearData.get(w);
                for (Map.Entry<UUID, FleetData.Unit> e : f.units().entrySet()) {
                    FleetData.Unit u = e.getValue();
                    WearData.Record r = wd.peek(e.getKey());
                    say(s, String.format(Locale.ROOT, "§f%s §7%s · worst %d%% %s · plan %d%% used · %.1f km %.1f h%s", u.name, FleetData.ROLES[u.role], r == null ? 0 : Math.round(r.worst()),
                            Condition.NAMES[Condition.worst(r)], Math.round(Predict.used(w, f, u) * 100), u.km, u.hours, u.failed ? " §cFAILED" : ""));
                }
            }
            case "unit" -> {
                EntityRollingStock e = nearest(w, at, EntityRollingStock.class);
                if (e == null) { say(s, "No vehicle within 64 blocks"); return; }
                FleetData.Unit u = f.unit(e.getUUID());
                WearData.Record r = WearData.get(w).peek(e.getUUID());
                say(s, "§6" + u.name + " §7" + FleetData.ROLES[u.role] + String.format(Locale.ROOT, " · %.1f km · %.1f h · %d faults (%d since exam)", u.km, u.hours, u.faults, u.faults - u.examFaults));
                if (r != null) for (WearData.Part p : WearData.Part.values()) {
                    int lv = Condition.level(r.wear[p.ordinal()]);
                    say(s, String.format(Locale.ROOT, "  %s %d%% %s - %s · RUL %s", p.label, Math.round(r.wear[p.ordinal()]), Condition.NAMES[lv], DepotTicker.effect(p, lv),
                            u.rul == null || u.rul[p.ordinal()] < 0 ? "?" : String.format(Locale.ROOT, "%.1f km", u.rul[p.ordinal()])));
                }
                FleetData.Unit live = DepotTicker.live(e.getUUID());
                if (live != null) say(s, String.format(Locale.ROOT, "  power x%.2f · cooling x%.2f · brakes x%.2f · limit %s", live.derateK, live.coolK, live.brakeK, live.cap < 1e9 ? Math.round(live.cap) + " km/h (" + live.capWhy + ")" : "none"));
                String due = Predict.due(w, f, u);
                say(s, "  exam plan " + Math.round(Predict.used(w, f, u) * 100) + "% used" + (due.isEmpty() ? "" : " - DUE: " + due));
                if (u.passenger) say(s, "  servicing: " + Servicing.state(u));
                say(s, "  brakes: " + (u.brakeOk ? "OK" : "TEST FAILED") + (u.brakeWork ? " (test due after brake work)" : ""));
                for (FleetData.Kind k : FleetData.Kind.values()) { FleetData.Comp c = f.comp(u, k, w.getTotalWorldTime()); say(s, String.format(Locale.ROOT, "  §8%s %s %.1f km, %d repairs%s", k.label, c.serial, c.life(u.km), c.repairs, k == FleetData.Kind.WHEELS ? ", " + Math.round(c.diameter) + " mm" : "")); }
                for (int i = 0; i < Math.min(6, u.log.size()); i++) say(s, "  §7" + u.log.get(i));
            }
            case "jobs" -> {
                List<FleetData.Job> l = f.openJobs(null);
                if (l.isEmpty()) say(s, "No open job cards");
                for (FleetData.Job j : l) say(s, "§f" + Jobs.line(j));
            }
            case "job" -> {
                EntityRollingStock e = nearest(w, at, EntityRollingStock.class);
                if (e == null || a.length < 3) { say(s, "Stand by a vehicle: depot job exam|repair <part>|wheels|bogie|braketest|service|couplers"); return; }
                FleetData.Unit u = f.unit(e.getUUID());
                TileDepotOffice o = Fleet.nearestOffice(w, at, false);
                long dep = o == null ? 0 : o.getPos().toLong();
                int kind = switch (a[2].toLowerCase(Locale.ROOT)) { case "exam" -> Jobs.EXAM; case "repair" -> Jobs.REPAIR; case "wheels" -> Jobs.WHEELS; case "bogie" -> Jobs.BOGIE;
                    case "braketest" -> Jobs.BRAKE_TEST; case "service" -> Jobs.SERVICE; default -> Jobs.COUPLERS; };
                int part = -1;
                if (kind == Jobs.REPAIR) {
                    String pn = a.length > 3 ? a[3] : "brakes";
                    for (WearData.Part p : WearData.Part.values()) if (p.label.equalsIgnoreCase(pn) || p.name().equalsIgnoreCase(pn)) part = p.ordinal();
                    if (part < 0) part = Condition.B;
                }
                say(s, "§aJob card #" + Jobs.raise(w, e.getUUID(), u.name, kind, part, dep, "raised by command").id);
            }
            case "send", "return", "spare", "alloc" -> {
                Locomotive l = nearest(w, at, Locomotive.class);
                if (l == null) { say(s, "No locomotive within 64 blocks"); return; }
                TileDepotOffice o = Fleet.nearestOffice(w, at, true);
                say(s, switch (sub) {
                    case "send" -> Fleet.sendToDepot(w, l.getUUID(), o, "sent by command");
                    case "return" -> Fleet.returnToService(w, l.getUUID(), a.length > 2 && a[2].equals("force"));
                    case "spare" -> Fleet.makeSpare(w, l.getUUID(), o);
                    default -> a.length < 3 ? "depot alloc <line>" : Fleet.allocate(w, l.getUUID(), a[2]);
                });
            }
            case "target" -> {
                if (a.length < 4) { say(s, "depot target <line> <trains>"); return; }
                f.lineTarget.put(a[2], Integer.parseInt(a[3]));
                f.markDirty();
                say(s, "§a" + a[2] + ": " + a[3] + " trains wanted");
            }
            case "plan" -> {
                if (a.length < 6) { say(s, "depot plan <km> <hours> <days> <faults>  (0 = not used)"); return; }
                f.plans.put("*", new int[]{Integer.parseInt(a[2]), Integer.parseInt(a[3]), Integer.parseInt(a[4]), Integer.parseInt(a[5])});
                f.markDirty();
                say(s, "§aDefault exam plan set");
            }
            case "download" -> {
                Locomotive l = nearest(w, at, Locomotive.class);
                if (l == null) { say(s, "No locomotive within 64 blocks"); return; }
                for (String line : Diagnostics.download(w, l).lines) say(s, "§7" + line);
            }
            case "crew" -> {
                for (FleetData.Driver d : f.drivers.values())
                    say(s, String.format(Locale.ROOT, "§f%s §7%s %.1f h duty %.1f h since break · quals %d · routes %s · %s", d.name, d.onDuty ? "ON" : "off", Crew.dutyHours(w, d), Crew.sinceBreak(w, d), d.quals, d.routes, d.train == null ? "no train" : f.unit(d.train).name));
                say(s, "§7Attended lines: " + f.attended);
                for (int i = 0; i < Math.min(6, f.crewLog.size()); i++) say(s, "  §8" + f.crewLog.get(i));
            }
            case "attend" -> {
                if (a.length < 3) { say(s, "depot attend <line>"); return; }
                boolean on = !f.attended.remove(a[2]);
                if (on) f.attended.add(a[2]);
                f.markDirty();
                say(s, "§e" + a[2] + (on ? " ATTENDED" : " unattended"));
            }
            case "dirty" -> {
                EntityRollingStock e = nearest(w, at, EntityRollingStock.class);
                if (e == null) { say(s, "No vehicle"); return; }
                FleetData.Unit u = f.unit(e.getUUID());
                u.interior = 25; u.exterior = 15; u.water = 0; u.waste = 100; u.catering = 0;
                f.markDirty();
                say(s, "§e" + u.name + ": " + Servicing.state(u));
            }
            case "give" -> {
                if (!(s instanceof EntityPlayer p)) return;
                for (ItemSpare.Spare sp : ItemSpare.Spare.values()) if (sp != ItemSpare.Spare.BOGIE_BAD)
                    p.inventory.addItemStackToInventory(sp == ItemSpare.Spare.BOGIE ? ItemSpare.bogie(true, f.serial(FleetData.Kind.BOGIE_A), 0) : ItemSpare.stack(sp, sp.ordinal() <= 3 ? 1 : 4));
                say(s, "§aA set of spares");
            }
            case "stations" -> {
                for (long k : new ArrayList<>(com.dogpound.railmap.server.StationData.get(w).names().keySet())) {
                    StationFacilities.Audit x = StationFacilities.audit(w, k);
                    say(s, String.format(Locale.ROOT, "§f%s §7access %s (%d) step-free %s · lifts %d/%d · tactile %d · toilets %d · help %d · facilities %d/%d · cost %s/day",
                            x.name, x.grade(), x.rating, x.stepFree ? "yes" : "no", x.liftsOk, x.lifts, x.tactile, x.toiletsOk, x.helpOk, x.working, x.total, com.dogpound.railmap.economy.Coins.fmt(x.cost)));
                }
            }
            default -> say(s, "§7/irextras " + USAGE);
        }
    }

    static <T extends EntityRollingStock> T nearest(World w, BlockPos at, Class<T> c) {
        T best = null;
        double bd = 64 * 64;
        for (T e : cam72cam.mod.world.World.get(w).getEntities(c)) {
            Vec3d p = e.getPosition();
            double d = at.distanceSq(p.x, p.y, p.z);
            if (!e.isDead() && d < bd) { bd = d; best = e; }
        }
        return best;
    }

    static void say(ICommandSender s, String m) { s.sendMessage(new TextComponentString(m)); }
}
