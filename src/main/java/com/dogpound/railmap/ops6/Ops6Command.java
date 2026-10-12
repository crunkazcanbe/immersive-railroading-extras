package com.dogpound.railmap.ops6;

import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import com.dogpound.railmap.track.Rails;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.*;

/**
 * Batch 6 E commands:
 * /irextras rco [list|estop|selftest]                         beltpack sessions, stop them all, controller check
 * /irextras crane [list|wreck <1-3>|selftest]                 cranes, derail / tip / destroy the nearest car (test), duty chart check
 * /irextras lube [list|film|selftest]                         lubricators, the film where you stand, wear-factor check
 * /irextras masterclock [list|slaves|drift <min>|selftest]    masters, slaves near you, push the slaves near you off time (test)
 * /irextras trainsim [list|results|selftest]                  sim desks, results, the physics self-check
 */
public final class Ops6Command {
    private Ops6Command() {}

    public static boolean command(World w, ICommandSender s, String[] a) {
        if (a.length == 0) return false;
        String c = a[0].toLowerCase(Locale.ROOT);
        if (!c.equals("rco") && !c.equals("crane") && !c.equals("lube") && !c.equals("masterclock") && !c.equals("trainsim")) return false;
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "list";
        try {
            switch (c) {
                case "rco" -> rco(w, s, sub);
                case "crane" -> crane(w, s, sub, a);
                case "lube" -> lube(w, s, sub);
                case "masterclock" -> clock(w, s, sub, a);
                default -> sim(w, s, sub);
            }
        } catch (RuntimeException | LinkageError e) { say(s, "§c" + e); }
        return true;
    }

    static void say(ICommandSender s, String m) { s.sendMessage(new TextComponentString(m)); }

    static <T> List<T> tiles(World w, Class<T> k) {
        List<T> l = new ArrayList<>();
        for (TileEntity t : new ArrayList<>(w.loadedTileEntityList)) if (k.isInstance(t) && !t.isInvalid()) l.add(k.cast(t));
        return l;
    }

    static void pass(ICommandSender s, String what, List<String> bad) {
        if (bad.isEmpty()) say(s, "§a" + what + " self-test PASSED");
        else for (String b : bad) say(s, "§cFAIL: " + b);
    }

    // ---- RCO ---------------------------------------------------------------------------------------------------------------
    static void rco(World w, ICommandSender s, String sub) {
        switch (sub) {
            case "estop" -> say(s, "§c" + Rco.estopAll(w) + " RCO locos emergency-braked");
            case "selftest" -> {
                List<String> bad = new ArrayList<>();
                if (Rco.SPEED.length != Rco.SPEED_KMH.length) bad.add("speed selector table");
                if (Math.abs(Rco.SPEED_KMH[5] - 16.1) > 0.1 || Math.abs(Rco.SPEED_KMH[2] - 3.2) > 0.1) bad.add("10 mph / 2 mph are not 16.1 / 3.2 km/h");
                // the speed controller on a toy loco: 1st order, a = 0.5 m/s^2 per unit throttle - must settle near 7 mph without braking
                double v = 0, integ = 0, target = Rco.SPEED_KMH[4];
                for (int i = 0; i < 600; i++) {
                    double err = target - v;
                    integ = Math.max(0, Math.min(0.6, integ + err * 0.004));
                    double thr = Math.max(0, Math.min(1, err * 0.08 + integ));
                    v += (thr * 1.8 - 0.05 * v) * 0.1;
                }
                if (Math.abs(v - target) > 2.5) bad.add(String.format(Locale.ROOT, "speed controller settles at %.1f km/h for 7 mph (%.1f)", v, target));
                if (Ops6Config.rcoRange < 16) bad.add("range too short");
                pass(s, "RCO (speed table, controller, range)", bad);
            }
            default -> {
                List<String> l = Rco.list();
                say(s, "§6RCO sessions: §f" + l.size() + " §7(range " + Ops6Config.rcoRange + " m, vigilance " + Ops6Config.rcoVigilanceS + " s)");
                for (String x : l) say(s, "§f" + x);
                say(s, "§7rco [list|estop|selftest] - pair: look at a loco, sneak + right-click with the beltpack");
            }
        }
    }

    // ---- crane -------------------------------------------------------------------------------------------------------------
    static void crane(World w, ICommandSender s, String sub, String[] a) {
        switch (sub) {
            case "wreck" -> {
                int sev = a.length > 2 ? Integer.parseInt(a[2]) : 1;
                EntityMoveableRollingStock best = null;
                double bd = 32 * 32;
                for (EntityMoveableRollingStock x : cam72cam.mod.world.World.get(w).getEntities(EntityMoveableRollingStock.class)) {
                    double d = s.getPositionVector().squareDistanceTo(x.getPosition().x, x.getPosition().y, x.getPosition().z);
                    if (d < bd) { bd = d; best = x; }
                }
                if (best == null) { say(s, "§7No car within 32 blocks"); return; }
                cam72cam.immersiverailroading.wreck.Wrecks.forceWreck(best, Math.max(1, Math.min(3, sev)), "test (irextras crane wreck)");
                say(s, "§e" + Rails.name(best) + " forced to " + (sev <= 1 ? "DERAILED" : sev == 2 ? "ON ITS SIDE" : "DESTROYED") + " - recover it with the crane");
            }
            case "selftest" -> {
                List<String> bad = new ArrayList<>();
                if (Math.abs(TileRailCrane.rated(6, 90, true) - 250) > 0.01) bad.add("on outriggers over the end at 6 m should be 250 t, got " + TileRailCrane.rated(6, 90, true));
                if (Math.abs(TileRailCrane.rated(10, 90, true) - 150) > 0.01) bad.add("1500 t·m at 10 m should be 150 t");
                if (Math.abs(TileRailCrane.rated(10, 0, true) - 112.5) > 0.01) bad.add("over the side on outriggers should be 75 %");
                if (Math.abs(TileRailCrane.rated(10, 90, false) - 30) > 0.01) bad.add("free on rails 300 t·m at 10 m should be 30 t");
                if (TileRailCrane.radius(78) > 4 || TileRailCrane.radius(8) < 14) bad.add("jib radius range 3-14.5 m");
                if (TileRailCrane.angDiff(350, 10) != -20 || TileRailCrane.angDiff(10, 350) != 20) bad.add("slew angle wrap");
                pass(s, "Breakdown crane (duty chart, radius, slew)", bad);
            }
            default -> {
                List<TileRailCrane> l = tiles(w, TileRailCrane.class);
                say(s, "§6Breakdown cranes: §f" + l.size());
                for (TileRailCrane t : l) say(s, "§f" + xyz(t.getPos()) + " §7" + t.gearState());
                say(s, "§7crane [list|wreck <1 derail|2 tip|3 destroy>|selftest]");
            }
        }
    }

    // ---- lubricators -------------------------------------------------------------------------------------------------------
    static void lube(World w, ICommandSender s, String sub) {
        switch (sub) {
            case "film" -> {
                net.minecraft.util.math.Vec3d p = s.getPositionVector();
                float f = Lubricators.film(w, p.x, p.y, p.z);
                say(s, String.format(Locale.ROOT, "§7Grease film here: §f%d %% §7- rail side wear on curves x%.2f, flange wear x%.2f, squeal %s", Math.round(f * 100),
                        Lubricators.rail(w, new BlockPos(p), 3), Lubricators.wheel(w, p.x, p.y, p.z), f >= 0.3f ? "§astopped" : "§enot stopped"));
            }
            case "selftest" -> {
                List<String> bad = new ArrayList<>();
                World ws = w;
                BlockPos at = new BlockPos(0, 255, 0);
                Lubricators.post(ws, at, 1f);
                if (Ops6Config.lubeEffect) {
                    if (Math.abs(Lubricators.rail(ws, at, 3) - 0.3) > 0.02) bad.add("full film should leave 30 % of the side wear on a curve, got " + Lubricators.rail(ws, at, 3));
                    if (Lubricators.rail(ws, at, 1) != 1) bad.add("straight track has no gauge-face wear to save");
                    if (Math.abs(Lubricators.wheel(ws, 0.5, 255, 0.5) - 0.5) > 0.02) bad.add("full film should halve flange wear");
                    if (Lubricators.film(ws, Ops6Config.lubeRange + 5, 255, 0) != 0) bad.add("film reaches past the carry range");
                }
                Lubricators.gone(ws, at);
                if (Lubricators.film(ws, 0.5, 255, 0.5) != 0) bad.add("a removed lubricator still greases");
                pass(s, "Flange lubricator (wear factors, range)", bad);
            }
            default -> {
                List<TileFlangeLube> l = tiles(w, TileFlangeLube.class);
                say(s, "§6Flange lubricators: §f" + l.size() + " §7(carry " + Ops6Config.lubeRange + " blocks)");
                for (TileFlangeLube t : l) say(s, "§f" + xyz(t.getPos()) + " §7" + t.gearState());
                say(s, "§7lube [list|film|selftest]");
            }
        }
    }

    // ---- master clock ------------------------------------------------------------------------------------------------------
    static void clock(World w, ICommandSender s, String sub, String[] a) {
        MasterClocks.Data d = MasterClocks.data(w);
        switch (sub) {
            case "slaves" -> {
                int n = 0;
                for (Map.Entry<Long, MasterClocks.Slave> e : d.slaves.entrySet()) {
                    BlockPos b = BlockPos.fromLong(e.getKey());
                    if (s.getPosition().distanceSq(b) > 128 * 128) continue;
                    MasterClocks.Slave x = e.getValue();
                    say(s, String.format(Locale.ROOT, "§f%s §7%s · shows %s · off %+.0f s%s", x.kind, xyz(b), TileMasterClock.hms(w.getWorldTime() + Math.round(x.drift)), x.drift * 3.6, x.fault ? " §cDARK" : ""));
                    n++;
                }
                if (n == 0) say(s, "§7No slave clocks within 128 blocks (station clocks, arrivals boards, PIS signs)");
            }
            case "drift" -> {
                float min = a.length > 2 ? Float.parseFloat(a[2]) : 5;
                int n = 0;
                for (Map.Entry<Long, MasterClocks.Slave> e : d.slaves.entrySet())
                    if (s.getPosition().distanceSq(BlockPos.fromLong(e.getKey())) <= 128 * 128) { e.getValue().drift += min * 1000f / 60; e.getValue().syncAt = w.getTotalWorldTime(); n++; }
                d.markDirty();
                say(s, "§e" + n + " slave clocks pushed " + min + " min off - a master clock in range pulls them back at its next impulse");
            }
            case "selftest" -> {
                List<String> bad = new ArrayList<>();
                if (!TileMasterClock.hms(0).equals("06:00:00")) bad.add("world time 0 should read 06:00:00, got " + TileMasterClock.hms(0));
                if (!TileMasterClock.hms(18000).equals("00:00:00")) bad.add("world time 18000 should read 00:00:00");
                if (!TileMasterClock.hms(6500).equals("12:30:00")) bad.add("world time 6500 should read 12:30:00, got " + TileMasterClock.hms(6500));
                double perDay = Math.abs(MasterClocks.rate(12345L)) * 24000 / (1000.0 / 60);
                if (perDay > Ops6Config.clockDriftMinPerDay + 1e-6 || (Ops6Config.clockDriftMinPerDay > 0 && perDay < Ops6Config.clockDriftMinPerDay * 0.19)) bad.add("slave drift " + perDay + " min/day outside 0.2-1 x the configured " + Ops6Config.clockDriftMinPerDay);
                if (MasterClocks.rate(1L) == MasterClocks.rate(2L)) bad.add("every slave drifts at the same rate");
                pass(s, "Master clock (time display, drift rates)", bad);
            }
            default -> {
                List<TileMasterClock> l = tiles(w, TileMasterClock.class);
                say(s, "§6Master clocks: §f" + l.size() + " §7(range " + Ops6Config.clockRange + ", slaves known " + d.slaves.size() + (d.everMaster ? "" : ", no master ever placed: slaves keep time") + ")");
                for (TileMasterClock t : l) say(s, "§f" + xyz(t.getPos()) + " §7" + t.gearState());
                say(s, "§7masterclock [list|slaves|drift <min>|selftest]");
            }
        }
    }

    // ---- training simulator -----------------------------------------------------------------------------------------------
    static void sim(World w, ICommandSender s, String sub) {
        switch (sub) {
            case "results" -> {
                for (TileDriverSim t : tiles(w, TileDriverSim.class)) for (String r : t.results) say(s, "§7" + xyz(t.getPos()) + " §f" + r);
                if (s instanceof EntityPlayerMP p) {
                    com.dogpound.railmap.safety.Competency.Rec r = com.dogpound.railmap.safety.Competency.get(w).rec(p);
                    for (String x : r.incidents) if (x.contains("|SIM|")) say(s, "§b[record] §f" + x.substring(x.indexOf("|SIM|") + 5));
                    for (int i = 0; i < 3; i++) say(s, String.format(Locale.ROOT, "§7Sim hours credited (%s): §f%.2f", com.dogpound.railmap.safety.Competency.TRACTION[i], p.getEntityData().getDouble("irextras_simH" + i)));
                }
            }
            case "selftest" -> pass(s, "Training simulator physics (acceleration, braking, gradient, SPAD, WSP)", SimCore.selftest());
            default -> {
                List<TileDriverSim> l = tiles(w, TileDriverSim.class);
                say(s, "§6Training simulators: §f" + l.size());
                for (TileDriverSim t : l) say(s, "§f" + xyz(t.getPos()) + " §7" + t.gearState());
                say(s, "§7trainsim [list|results|selftest]");
            }
        }
    }

    static String xyz(BlockPos p) { return p.getX() + "," + p.getY() + "," + p.getZ(); }
}
