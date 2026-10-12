package com.dogpound.railmap.cine;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /irextras cine tripods | desks | selftest */
public final class CineCommand {
    private CineCommand() {}

    public static boolean command(World w, ICommandSender s, String[] a) {
        if (a.length == 0 || !a[0].equalsIgnoreCase("cine")) return false;
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "selftest";
        switch (sub) {
            case "tripods" -> {
                int n = 0;
                for (TileCamTripod t : TileCamTripod.ALL) if (t.getWorld() == w && !t.isInvalid()) { n++; say(s, "§bCAM " + t.number() + " §7at " + t.getPos().getX() + " " + t.getPos().getY() + " " + t.getPos().getZ() + " - " + t.gearState()); }
                if (n == 0) say(s, "§7No trackside camera tripods loaded");
            }
            case "desks" -> {
                int n = 0;
                for (TileDirectorDesk d : TileDirectorDesk.ALL) if (d.getWorld() == w && !d.isInvalid()) { n++; say(s, "§bDesk §7at " + d.getPos().getX() + " " + d.getPos().getZ() + " - " + d.gearState() + " · shots " + d.shots.size()); }
                if (n == 0) say(s, "§7No director desks loaded");
            }
            default -> { for (String l : selfTest()) say(s, l); }
        }
        return true;
    }

    static void say(ICommandSender s, String t) { s.sendMessage(new TextComponentString(t)); }

    public static List<String> selfTest() {
        List<String> out = new ArrayList<>();
        int[] fails = {0};
        double[] lk = CineMath.look(0, 0, 0, 0, 0, 10);
        check(out, fails, "look straight south: yaw 0 pitch 0", Math.abs(lk[0]) < 1e-9 && Math.abs(lk[1]) < 1e-9);
        double[] up = CineMath.look(0, 0, 0, 10, 10, 0);
        check(out, fails, String.format(Locale.ROOT, "look east and up: yaw %.0f pitch %.0f", up[0], up[1]), Math.abs(up[0] + 90) < 1e-6 && Math.abs(up[1] + 45) < 1e-6);
        List<double[]> keys = new ArrayList<>();
        keys.add(new double[]{0, 64, 0, 170, 0, 70}); keys.add(new double[]{10, 70, 0, -170, 10, 50}); keys.add(new double[]{20, 64, 10, -150, 0, 30});
        double[] p0 = CineMath.path(keys, 0), p1 = CineMath.path(keys, 0.5), p2 = CineMath.path(keys, 1);
        check(out, fails, "camera path starts on its first waypoint", Math.abs(p0[0]) < 1e-9 && Math.abs(p0[1] - 64) < 1e-9);
        check(out, fails, "passes through the middle one", Math.abs(p1[0] - 10) < 1e-9 && Math.abs(p1[1] - 70) < 1e-9);
        check(out, fails, "ends on the last", Math.abs(p2[0] - 20) < 1e-9 && Math.abs(p2[2] - 10) < 1e-9);
        double[] mid = CineMath.path(keys, 0.25);
        check(out, fails, String.format(Locale.ROOT, "turns the short way across 180 (yaw %.0f)", mid[3]), mid[3] > 170 && mid[3] < 190);
        check(out, fails, "letterbox 2.39 on 1920x1080: 138 px bars", CineMath.bar(1920, 1080, 2.39) == 138 && CineMath.bar(1920, 1080, 0) == 0);
        double f = CineMath.frameFov(20, 50, 0.6);
        check(out, fails, String.format(Locale.ROOT, "framing a 20 m train at 50 m: %.0f deg", f), f > 30 && f < 40);
        List<double[]> cams = new ArrayList<>();
        cams.add(new double[]{0, 64, 0, 1}); cams.add(new double[]{100, 64, 0, 2});
        check(out, fails, "auto-cut picks the nearer tripod", CineMath.autoCut(cams, 10, 64, 0, 0, 0, 96, 3) == 1);
        check(out, fails, "holds the shot for the minimum time", CineMath.autoCut(cams, 80, 64, 0, 1, 1, 96, 3) == 1);
        check(out, fails, "then cuts when the other is much nearer", CineMath.autoCut(cams, 80, 64, 0, 1, 5, 96, 3) == 2);
        check(out, fails, "cuts at once when the current one loses the train", CineMath.autoCut(cams, 150, 64, 0, 1, 0.5, 96, 3) == 2);
        double[] ch = CineMath.follow(CineMath.CHASE, 0, 64, 0, 0, 10, 0);
        check(out, fails, "chase cam sits behind the train (heading south -> camera north of it)", ch[2] < -20 && ch[1] > 64);
        double[] fb = CineMath.flybySpot(0, 64, 0, -90, 20);
        check(out, fails, "flyby waits ahead of an eastbound train", fb[0] > 100);
        out.add(0, fails[0] == 0 ? "§a§lCINE SELF-TEST PASSED§r§a (" + out.size() + " checks)" : "§c§lCINE SELF-TEST: " + fails[0] + " FAILED");
        return out;
    }

    static void check(List<String> out, int[] fails, String what, boolean ok) {
        if (!ok) fails[0]++;
        out.add((ok ? "§aPASS §7" : "§cFAIL §7") + what);
    }
}
