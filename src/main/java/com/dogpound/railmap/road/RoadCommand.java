package com.dogpound.railmap.road;

import cam72cam.immersiverailroading.entity.Locomotive;
import com.dogpound.railmap.signal.SignalRegistry;
import com.dogpound.railmap.signal.TileCrossing;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.List;
import java.util.Locale;

/**
 * /irextras road status|spawn [n] [car|van|truck|bus]|clear|pause|crossings|signals|selftest
 * /irextras tram list|tag|untag|stops|selftest
 */
public final class RoadCommand {
    private RoadCommand() {}

    public static boolean command(World w, ICommandSender s, String[] a) {
        if (a.length == 0) return false;
        String top = a[0].toLowerCase(Locale.ROOT);
        if (!top.equals("road") && !top.equals("tram")) return false;
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "help";
        if (top.equals("tram")) { tram(w, s, sub); return true; }
        RoadData d = RoadData.get(w);
        switch (sub) {
            case "status" -> {
                List<EntityRoadVehicle> vs = RoadTraffic.vehicles(w);
                say(s, String.format(Locale.ROOT, "§eRoad traffic %s · %d vehicles · spawned %d, removed %d · %d controllers · %d crossings",
                        !RoadConfig.traffic ? "§cOFF (config)§e" : d.paused ? "PAUSED" : "on", vs.size(), d.spawned, d.removed, TileTrafficController.all(w).size(), SignalRegistry.crossings(w.provider.getDimension()).size()));
                for (EntityRoadVehicle v : vs) if (s instanceof EntityPlayer p && v.getDistanceSq(p) < 64 * 64)
                    say(s, String.format(Locale.ROOT, "§7%s at %.0f,%.0f %.0f km/h %s", v.kind().label, v.posX, v.posZ, v.speed * 72, v.why));
            }
            case "spawn" -> {
                if (!(s instanceof EntityPlayer p)) { say(s, "§cPlayers only"); return true; }
                int n = a.length > 2 ? Math.max(1, Math.min(20, parse(a[2], 1))) : 1;
                EntityRoadVehicle.Kind k = null;
                if (a.length > 3) for (EntityRoadVehicle.Kind x : EntityRoadVehicle.Kind.values()) if (x.name().equalsIgnoreCase(a[3])) k = x;
                say(s, RoadConsole.spawn(p, n, k));
            }
            case "clear" -> { int n = 0; for (EntityRoadVehicle v : RoadTraffic.vehicles(w)) { v.setDead(); n++; } say(s, "§e" + n + " road vehicles cleared"); }
            case "pause" -> { d.paused = !d.paused; d.markDirty(); say(s, d.paused ? "§eRoad traffic paused" : "§aRoad traffic resumed"); }
            case "crossings" -> {
                for (TileCrossing x : SignalRegistry.crossings(w.provider.getDimension()))
                    say(s, "§e" + x.getPos().getX() + "," + x.getPos().getZ() + (x.active() ? " §cCLOSED§e: " : " open: ") + "§7" + RoadTraffic.crossingInfo(w, x.getPos()));
            }
            case "signals" -> {
                for (TileTrafficController c : TileTrafficController.all(w)) say(s, "§e" + String.join(" · ", c.row()).replaceAll("(^|· )[!~+.]", "$1"));
                if (TileTrafficController.all(w).isEmpty()) say(s, "§7No Traffic Signal Controllers loaded");
            }
            case "selftest" -> {
                List<String> f = RoadTraffic.selfTest();
                say(s, f.isEmpty() ? "§aRoad + tram self-test passed (stage logic: rest / demand / extension / tram priority / ped / flash / manual; stop lines; turn indicators; braking curve; tram names; line-of-sight speed)"
                        : "§cRoad self-test FAILED: " + String.join("; ", f));
            }
            default -> say(s, "§e/irextras road §7status · spawn [n] [car|van|truck|bus] · clear · pause · crossings · signals · selftest");
        }
        return true;
    }

    private static void tram(World w, ICommandSender s, String sub) {
        RoadData d = RoadData.get(w);
        switch (sub) {
            case "list" -> {
                List<String[]> l = TramMode.trams(w);
                if (l.isEmpty()) say(s, "§7No trams loaded (tag IR stock with a name tag containing TRAM, or ride it and /irextras tram tag)");
                for (String[] t : l) say(s, "§e" + t[1] + " §7line " + t[2] + " · next " + t[3] + " · " + t[4] + " km/h · cap " + t[5] + " · " + t[6].replaceAll("^[!~+.]", ""));
            }
            case "tag", "untag" -> {
                Locomotive l = near(w, s);
                if (l == null) { say(s, "§cRide a locomotive, or stand within 8 blocks of one"); return; }
                if (sub.equals("tag")) { d.tramOff.remove(l.getUUID()); d.tramOn.add(l.getUUID()); }
                else { d.tramOn.remove(l.getUUID()); d.tramOff.add(l.getUUID()); }
                d.markDirty();
                say(s, sub.equals("tag") ? "§aTagged TRAM - its autopilot now drives in tram mode" : "§eUntagged - runs as an ordinary train");
            }
            case "stops" -> {
                for (TileTramStop t : TileTramStop.all(w)) say(s, "§e" + String.join(" · ", t.row()).replaceAll("(^|· )[!~+.]", "$1") + " §7at " + t.getPos().getX() + "," + t.getPos().getZ());
                if (TileTramStop.all(w).isEmpty()) say(s, "§7No tram stops loaded");
            }
            case "selftest" -> { List<String> f = TramMode.selfTest(); say(s, f.isEmpty() ? "§aTram self-test passed" : "§cTram self-test FAILED: " + String.join("; ", f)); }
            default -> say(s, "§e/irextras tram §7list · tag · untag (the loco you ride / nearest) · stops · selftest");
        }
    }

    private static Locomotive near(World w, ICommandSender s) {
        if (!(s instanceof EntityPlayer p)) return null;
        Locomotive best = null;
        double bd = 8 * 8;
        for (Locomotive l : cam72cam.mod.world.World.get(w).getEntities(Locomotive.class)) {
            cam72cam.mod.math.Vec3d v = l.getPosition();
            double dd = p.getDistanceSq(v.x, v.y, v.z);
            if (p.getRidingEntity() != null && l.getUUID().equals(p.getRidingEntity().getUniqueID())) return l;
            if (dd < bd) { bd = dd; best = l; }
        }
        return best;
    }

    static int parse(String x, int def) { try { return Integer.parseInt(x); } catch (NumberFormatException e) { return def; } }
    static void say(ICommandSender s, String t) { s.sendMessage(new TextComponentString(t)); }
}
