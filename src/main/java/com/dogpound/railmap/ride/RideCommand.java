package com.dogpound.railmap.ride;

import net.minecraft.command.ICommandSender;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /irextras ride list | line N | start N | stop N | estop N | reset N | wind N <m/s|off> | evac N | speed N <m/s> | remove N | selftest
 * (the desk does all of this with its buttons; these are for testing and inspection).
 */
public final class RideCommand {
    private RideCommand() {}

    public static boolean command(World w, ICommandSender s, String[] a) {
        if (a.length == 0 || !a[0].equalsIgnoreCase("ride")) return false;
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "list";
        RideData d = RideData.get(w);
        if (sub.equals("selftest")) { for (String l : selfTest()) say(s, l); return true; }
        if (sub.equals("list")) {
            if (d.lines.isEmpty()) say(s, "§7No ropeway / funicular lines in this dimension. Haul Rope Spool: station A -> pylons -> station B");
            for (RideData.Line l : d.lines.values()) say(s, info(l));
            return true;
        }
        RideData.Line l = a.length > 2 ? parse(d, a[2]) : null;
        if (l == null) { say(s, "§c/irextras ride list|line N|start N|stop N|estop N|reset N|wind N <m/s|off>|evac N|speed N <m/s>|remove N|selftest"); return true; }
        switch (sub) {
            case "line" -> {
                say(s, info(l));
                say(s, String.format(Locale.ROOT, "§7 supports %d, length %.1f m, machine %s, desk %s, tension %s", l.supports.size(), l.geom() == null ? 0 : l.geom().length,
                        l.machine == 0 ? "-" : "linked", l.desk == 0 ? "-" : "linked", l.tension == 0 ? "-" : "linked"));
                say(s, String.format(Locale.ROOT, "§7 motor %.0f kW %.0f A, winding %.0f C oil %.0f C, wind %.1f m/s (limit %.0f), rope swing %.0f mm, tension %.0f kN carriage %.0f%%",
                        l.kw, l.amps, l.motorC, l.oilC, l.wind, l.windLimit, l.rpdMm, l.tensionKn, l.carriage));
                say(s, "§7 drive: " + l.driveWhy + " · faults: " + RideSim.faults(l.faults) + " · e-stops " + l.estops);
                for (int i = 0; i < l.cars(); i++) {
                    double[] p = l.carPose(i);
                    say(s, String.format(Locale.ROOT, "§7  car %d at %.0f %.0f %.0f coord %.1f %s riders %d", i, p == null ? 0 : p[0], p == null ? 0 : p[1], p == null ? 0 : p[2], l.carCoord(i),
                            l.doorsOpen(i) ? "DOORS OPEN" : "doors shut", i < l.carRiders.length ? l.carRiders[i] : 0));
                }
            }
            case "start" -> { String r = RideSim.start(w, l); say(s, r == null ? "§aStarted" : "§c" + r); }
            case "stop" -> { RideSim.stop(w, l); say(s, "§eStopping"); }
            case "estop" -> { RideSim.trip(w, l, RideData.F_ESTOP); say(s, "§cEmergency stop (RESET at the desk once the E-stops are out)"); }
            case "reset" -> say(s, RideSim.reset(w, l));
            case "wind" -> {
                l.windSet = a.length > 3 && !a[3].equalsIgnoreCase("off") ? Double.parseDouble(a[3]) : -1;
                say(s, l.windSet < 0 ? "§7Wind from the weather again" : "§7Wind held at " + l.windSet + " m/s");
            }
            case "speed" -> { if (a.length > 3) l.setV = Double.parseDouble(a[3]); say(s, "§7Setpoint " + l.setV + " m/s (the desk overrides this every tick if one is linked)"); }
            case "evac" -> { l.mode = 2; l.brakeRel = true; String r = RideSim.start(w, l); say(s, r == null ? "§6Evacuation drive running" : "§c" + r); }
            case "remove" -> { RideBlocks.dropLine(w, l, "removed by command"); say(s, "§7Removed"); }
            default -> say(s, "§cUnknown: " + sub);
        }
        return true;
    }

    static RideData.Line parse(RideData d, String s) {
        try { return d.line(Integer.parseInt(s)); } catch (NumberFormatException e) { return null; }
    }

    static String info(RideData.Line l) {
        return String.format(Locale.ROOT, "§b#%d %s §7(%s) %s · %.1f m/s · %d %s", l.id, l.label(), RideGeom.KIND[l.kind], RideSim.state(l), Math.abs(l.v), l.cars(), l.shuttle() ? "cars" : "cabins");
    }

    static void say(ICommandSender s, String t) { s.sendMessage(new TextComponentString(t)); }

    // ---- self-test: the maths without a world ----------------------------------------------------------------------------
    public static List<String> selfTest() {
        List<String> out = new ArrayList<>();
        int[] fails = {0};
        List<double[]> sup = new ArrayList<>();
        sup.add(new double[]{0, 64, 0}); sup.add(new double[]{0, 90, 100}); sup.add(new double[]{0, 120, 200});
        RideGeom g = new RideGeom(RideGeom.GONDOLA, sup, 0.02);
        double straight = Math.hypot(100, 26) + Math.hypot(100, 30);
        check(out, fails, String.format(Locale.ROOT, "rope length %.1f m > the straight %.1f m (it sags)", g.length, straight), g.length > straight && g.length < straight * 1.02);
        double mid = g.at(g.length * 0.25)[1];
        check(out, fails, String.format(Locale.ROOT, "mid-span sags below the chord: %.1f < 77", mid), mid < 77 && mid > 70);
        double[] a0 = g.at(0), aL = g.at(g.length);
        check(out, fails, "rope ends on the stations", Math.abs(a0[1] - 64) < 1e-6 && Math.abs(aL[2] - 200) < 1e-6);
        check(out, fails, "grade uphill toward B is positive", g.grade(g.length * 0.1) > 0);
        // gondola loop: round trip is continuous
        double[] p1 = g.loopPose(g.length - 0.01), p2 = g.loopPose(g.length + 0.01);
        check(out, fails, "cabin rounds B's bullwheel without a jump", Math.hypot(p1[0] - p2[0], p1[2] - p2[2]) < 0.2);
        double[] q1 = g.loopPose(g.loop - 0.01), q2 = g.loopPose(0.01);
        check(out, fails, "and A's", Math.hypot(q1[0] - q2[0], q1[2] - q2[2]) < 0.2);
        double[] side = g.loopPose(50), back = g.loopPose(g.length + g.arc() + (g.length - 50));
        check(out, fails, String.format(Locale.ROOT, "outbound and return strands %.1f m apart", Math.abs(side[0] - back[0])), Math.abs(Math.abs(side[0] - back[0]) - 2 * RideGeom.LAT) < 0.05);
        check(out, fails, "detached in stations, gripped on the line", !g.gripped(1) && g.gripped(50) && !g.gripped(g.length + 1));
        // funicular passing loop
        check(out, fails, "funicular: single track at the ends, passing loop in the middle", RideGeom.passingLoop(0.1) == 0 && RideGeom.passingLoop(0.5) == 1 && RideGeom.passingLoop(0.33) > 0 && RideGeom.passingLoop(0.33) < 1);
        // shuttle drive: a full trip arrives and reverses
        RideData.Line l = new RideData.Line();
        l.kind = RideGeom.TRAM;
        l.supports.addAll(sup);
        l.machine = 1; l.machineSeen = Long.MAX_VALUE / 2; l.driveOk = true; l.hpu = true; l.setV = 6; l.windLimit = 20; l.windSet = 0; l.tension = 0;
        l.running = true; l.phase = RideData.RUN; l.dir = 1;
        long t = 0;
        double maxV = 0;
        for (; t < 20 * 400 && l.phase == RideData.RUN; t++) { RideSim.shuttleTest(l); maxV = Math.max(maxV, Math.abs(l.v)); }
        check(out, fails, String.format(Locale.ROOT, "aerial tram trip: %.0f s, top %.1f m/s (held to 2 without a tension weight), arrives and reverses", t / 20.0, maxV),
                l.phase == RideData.DWELL && l.dir == -1 && Math.abs(l.s - l.geom().length) < 1e-6 && maxV <= 2.0 + 1e-6);
        // power: a full car going up against an empty one coming down needs power
        l.carRiders = new int[]{24, 0}; l.s = 50;
        double[] pw = RideSim.power(l, l.geom());
        check(out, fails, String.format(Locale.ROOT, "full cabin up, empty down: %.0f kN rope pull", pw[0] / 1000), pw[0] > 0);
        l.carRiders = new int[]{0, 24};
        check(out, fails, "empty up, full down: it regenerates", RideSim.power(l, l.geom())[0] < pw[0]);
        // wind
        l.wind = 21; l.windLimit = 15; l.faults = 0; l.estops = 0;
        RideSim.rpd(l, l.geom());
        check(out, fails, String.format(Locale.ROOT, "21 m/s wind swings the rope %.0f mm at pylon %d", l.rpdMm, l.rpdTower), l.rpdMm > RideConfig.rpdTripMm && l.rpdTower == 1);
        // save / load
        NBTTagCompound c = new NBTTagCompound();
        l.w = new double[]{1, 2, 3};
        l.write(c);
        RideData.Line r = new RideData.Line();
        r.read(c);
        check(out, fails, "a line saves and loads", r.supports.size() == 3 && r.w.length == 3 && r.kind == RideGeom.TRAM && r.dir == l.dir);
        out.add(0, fails[0] == 0 ? "§a§lRIDE SELF-TEST PASSED§r§a (" + out.size() + " checks)" : "§c§lRIDE SELF-TEST: " + fails[0] + " FAILED");
        return out;
    }

    static void check(List<String> out, int[] fails, String what, boolean ok) {
        if (!ok) fails[0]++;
        out.add((ok ? "§aPASS §7" : "§cFAIL §7") + what);
    }
}
