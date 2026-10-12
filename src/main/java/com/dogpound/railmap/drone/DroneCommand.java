package com.dogpound.railmap.drone;

import com.dogpound.railmap.asset.AssetKind;
import com.dogpound.railmap.weather.*;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.*;

/**
 * /irextras drone ... (IR Extras #2) and /irextras wxtrain ... (#4): status, test levers and the self-tests.
 */
public final class DroneCommand {
    private DroneCommand() {}

    static final String USAGE_D = "drone [list|flights|findings|log|launch|rth|hold|resume [cam]|nudge <cam> <fwd> <up> <yaw>|selftest]";
    static final String USAGE_W = "wxtrain [forecast|runs|vehicles|plan <plough|sandite|deice>|dispatch <run>|cancel <run>|snow <mm>|ice <mm>|leaves|selftest]";

    static void say(ICommandSender s, String t) { s.sendMessage(new TextComponentString(t)); }

    public static boolean command(World w, ICommandSender s, String[] a) {
        if (a.length == 0) return false;
        boolean drone = a[0].equalsIgnoreCase("drone"), wx = a[0].equalsIgnoreCase("wxtrain");
        if (!drone && !wx) return false;
        try { if (drone) drone(w, s, a); else wx(w, s, a); }
        catch (NumberFormatException | ArrayIndexOutOfBoundsException e) { say(s, "§c/irextras " + (drone ? USAGE_D : USAGE_W)); }
        return true;
    }

    private static TileDroneDock nearestDock(World w, BlockPos me) {
        TileDroneDock best = null;
        double bd = Double.MAX_VALUE;
        for (TileEntity t : new ArrayList<>(w.loadedTileEntityList))
            if (t instanceof TileDroneDock k && !t.isInvalid() && t.getPos().distanceSq(me) < bd) { bd = t.getPos().distanceSq(me); best = k; }
        return best;
    }

    private static TileWxDepot nearestDepot(World w, BlockPos me) {
        TileWxDepot best = null;
        double bd = Double.MAX_VALUE;
        for (TileEntity t : new ArrayList<>(w.loadedTileEntityList))
            if (t instanceof TileWxDepot k && !t.isInvalid() && t.getPos().distanceSq(me) < bd) { bd = t.getPos().distanceSq(me); best = k; }
        return best;
    }

    private static void drone(World w, ICommandSender s, String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
        DroneData d = DroneData.get(w);
        EntityPlayer p = s instanceof EntityPlayer pl ? pl : null;
        switch (sub) {
            case "": case "list": {
                say(s, "§b== Inspection drones");
                for (TileEntity t : new ArrayList<>(w.loadedTileEntityList)) if (t instanceof TileDroneDock k) say(s, "§7" + k.serial() + " @ " + t.getPos().getX() + "," + t.getPos().getY() + "," + t.getPos().getZ() + " · " + k.gearState() + " · CAM " + k.camNo);
                for (EntityDrone e : Drones.all(w)) say(s, Drones.statusLine(e) + " §8CAM " + e.camNo + " wp " + e.wp + "/" + e.way.length / 3);
                say(s, "§8/irextras " + USAGE_D);
                return;
            }
            case "flights": for (DroneData.Flight f : d.flights) say(s, String.format(Locale.ROOT, "§7#%d %s %s %.0f m, min %.0f%%, %d found · %s", f.id, f.drone, DroneData.MISSIONS[Math.max(0, Math.min(3, f.mission))], f.metres, f.minBattery, f.findings, f.end == 0 ? "IN FLIGHT" : f.result)); return;
            case "findings": { int n = 0; for (DroneData.Finding f : d.findings) { if (n++ > 25) break; say(s, (f.major ? "§c" : "§e") + "#" + f.id + " " + DroneData.STATES[f.state] + " " + f.kind + ": §7" + f.text); } return; }
            case "log": for (String l : d.log) say(s, "§7" + l); return;
            case "launch": case "rth": case "hold": case "resume": {
                TileDroneDock k = nearestDock(w, s.getPosition());
                if (k == null) { say(s, "§cNo Drone Dock loaded"); return; }
                if (sub.equals("launch") && k.controls().get("k_arm") != 1) { k.controls().set("k_arm", 1); k.controlsChanged(); say(s, "§7(ARM key turned for the test)"); }
                say(s, k.remote(sub, p));
                return;
            }
            case "nudge": {
                EntityDrone e = Drones.byCam(w, Integer.parseInt(a[2]));
                if (e == null) { say(s, "§cNo drone flying as CAM " + a[2]); return; }
                say(s, e.nudge(Double.parseDouble(a[3]), 0, Double.parseDouble(a[4]), Float.parseFloat(a[5])));
                return;
            }
            case "selftest": for (String l : selftest()) say(s, l); return;
            default: say(s, "§c/irextras " + USAGE_D);
        }
    }

    private static void wx(World w, ICommandSender s, String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
        BlockPos me = s.getPosition();
        WxTrainData td = WxTrainData.get(w);
        switch (sub) {
            case "": case "forecast": {
                WxTrains.Forecast f = WxTrains.forecast(w, me);
                say(s, String.format(Locale.ROOT, "§b== Weather trains §7(air %.0f C, rain %s, cold snap %s)", f.air, f.raining ? "now" : f.rainIn < 0 ? "not soon" : "in " + f.rainIn + " min", f.coldSnap ? "YES" : "no"));
                for (int k = 0; k < 3; k++) say(s, "§7" + WxTrains.KINDS[k] + ": risk " + f.risk[k] + "% " + f.why[k] + " · " + f.cells.get(k).size() + " cell(s) need it");
                say(s, "§8/irextras " + USAGE_W);
                return;
            }
            case "runs": for (WxTrainData.Run r : td.runs) say(s, "§7#" + r.id + " " + WxTrains.SHORT[r.kind] + " " + WxTrainData.STATES[r.state] + " " + r.locoName + " " + r.done.size() + "/" + r.targets.size() + " · " + r.why); return;
            case "vehicles": { List<String[]> rows = new ArrayList<>(); WxTrains.env(w, rows); for (String[] r : rows) if (r[0].equals("WV")) say(s, "§7" + r[2] + " " + r[1] + " (" + r[3] + ") " + r[4] + " km/h at " + r[5] + " " + r[6]); return; }
            case "plan": {
                TileWxDepot k = nearestDepot(w, me);
                int kind = Arrays.asList(WxTrains.SHORT).indexOf(a[2].toUpperCase(Locale.ROOT));
                if (kind < 0) { say(s, "§cplough | sandite | deice"); return; }
                if (k == null) { WxTrainData.Run r = WxTrains.plan(w, me, WxTrainConfig.depotRange, kind, "planned by command", false); say(s, r == null ? "§7Nothing to plan" : "§aRun #" + r.id + " planned (no desk: here)"); return; }
                say(s, WxTrains.op(w, 40 + kind, String.valueOf(k.getPos().toLong()), s.getName()));
                return;
            }
            case "dispatch": say(s, WxTrains.op(w, 43, a[2], s.getName())); return;
            case "cancel": say(s, WxTrains.op(w, 44, a[2], s.getName())); return;
            case "snow": case "ice": {
                if (!Weather.on()) { say(s, "§cRailway weather is off"); return; }
                WeatherData.Cell c = Weather.cell(w, me);
                if (sub.equals("snow")) c.snow = Double.parseDouble(a[2]); else c.ice = Double.parseDouble(a[2]);
                WeatherData.get(w).markDirty();
                say(s, "§a" + Weather.name(c) + ": snow " + Math.round(c.snow) + " mm, wire ice " + c.ice + " mm");
                return;
            }
            case "leaves": WxTrains.leafSeen(w, me); say(s, "§aLeaves marked on the rail head here (sandite planning)"); return;
            case "selftest": for (String l : selftest()) say(s, l); return;
            default: say(s, "§c/irextras " + USAGE_W);
        }
    }

    // ---- self-test ----------------------------------------------------------------------------------------------------------
    public static List<String> selftest() {
        List<String> out = new ArrayList<>();
        int[] fails = {0};
        // the drone's payload: a critical crack on a bridge is usually seen, a tiny one rarely; a motor's windings never
        double[] b = DroneInspect.pod(AssetKind.BRIDGE);
        double big = b[0] * (1 - Math.exp(-Math.pow(1 / b[1], 2))), tiny = b[0] * (1 - Math.exp(-Math.pow(0.05 / b[1], 2)));
        check(out, fails, String.format(Locale.ROOT, "drone POD bridge crack: critical %.0f%%, 1/20 critical %.1f%%", big * 100, tiny * 100), big > 0.6 && tiny < 0.05);
        check(out, fails, "drone can't see motor insulation; thermal sees transformers", DroneInspect.pod(AssetKind.MOTOR)[0] == 0 && DroneInspect.pod(AssetKind.TRANSFORMER)[2] == 1);
        // the reserve to get home grows with distance and an aged pack
        double r0 = EntityDrone.reserve(0, 1), r500 = EntityDrone.reserve(500, 1), rOld = EntityDrone.reserve(500, 0.8);
        check(out, fails, String.format(Locale.ROOT, "RTH reserve: at the dock %.0f%%, 500 m %.0f%%, 500 m on an aged pack %.0f%%", r0, r500, rOld), r0 == 8 && r500 > r0 && rOld > r500 && r500 < 60);
        // the battery pack ages
        net.minecraft.item.ItemStack pack = new net.minecraft.item.ItemStack(DroneRegistry.battery == null ? new DroneItems.Battery() : DroneRegistry.battery);
        double f0 = DroneItems.Battery.fade(pack);
        DroneItems.Battery.age(pack, 700);
        double f700 = DroneItems.Battery.fade(pack);
        check(out, fails, String.format(Locale.ROOT, "pack capacity new %.0f%%, after 700 cycles %.0f%%", f0 * 100, f700 * 100), f0 == 1 && Math.abs(f700 - 0.8) < 0.01);
        DroneItems.Battery.set(pack, 150);
        check(out, fails, "charge clamps to 100%", DroneItems.Battery.pct(pack) == 100);
        // waypoints every 3 m
        List<double[]> pts = new ArrayList<>();
        for (int i = 0; i <= 30; i++) DroneRoute.add(pts, i, 64, 0);
        check(out, fails, "route waypoints 3 m apart: 31 rail points over 30 m -> " + pts.size(), pts.size() == 10 || pts.size() == 11);
        // weather-train kinds from tags and names
        check(out, fails, "tags: SNOWPLOW plough, RHTT sandite, DEICER de-icer, OHLE none",
                WxTrains.kindOf("SNOWPLOW", "", false) == WxTrains.PLOUGH && WxTrains.kindOf("RHTT", "", false) == WxTrains.SANDITE
                        && WxTrains.kindOf("DEICER", "", false) == WxTrains.DEICE && WxTrains.kindOf("OHLE", "", false) < 0);
        check(out, fails, "a plough by name (Rotary Plow) or by the fitted attachment; a plain Class 66 is not",
                WxTrains.kindOf("", "Rotary Plow", false) == WxTrains.PLOUGH && WxTrains.kindOf("", "Class 66", true) == WxTrains.PLOUGH && WxTrains.kindOf("", "Class 66", false) < 0);
        out.add(0, fails[0] == 0 ? "§a== drone + weather train self-test: all " + out.size() + " PASS" : "§c== drone + weather train self-test: " + fails[0] + " FAIL");
        return out;
    }

    private static void check(List<String> out, int[] fails, String what, boolean ok) {
        if (!ok) fails[0]++;
        out.add((ok ? "§aPASS " : "§cFAIL ") + "§7" + what);
    }
}
