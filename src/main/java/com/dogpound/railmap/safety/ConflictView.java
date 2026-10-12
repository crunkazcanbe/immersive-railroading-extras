package com.dogpound.railmap.safety;

import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.auto.TrackPath;
import com.dogpound.railmap.alarm.Recorder;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.*;

/**
 * Route conflict visualizer (her ChatGPT batch-3 list #15). The interlocking's safety case (RouteCheck) notes, for
 * every check that fails, exactly WHAT blocks the request and WHERE: the points that aren't detected, the other route
 * holding them or set over the same track (with that route's own line), points locked the other way by a driverless
 * train, the occupied section (train, track circuit, axle section), what stands in the overlap, the possession or
 * plunger, the divided train, the isolated signal. The console's SAFETY > Routes page draws it as a mimic and the
 * signal box / dispatcher map draws it on the real track (GuiDispatcherBoard), each blocker lettered:
 * P points · R conflicting route · L locked points · S occupied section · O overlap · B line blocked · I integrity ·
 * X isolated signal · E no power.
 */
public final class ConflictView {
    private ConflictView() {}

    static int nextId = 1;

    /** a path's centre-line as "x,z;x,z..." (whole metres, at most ~70 points) */
    static String line(TrackPath path) {
        List<double[]> pts = new ArrayList<>();
        for (TrackPath.Step s : path.steps) {
            if (s.points == null) { pts.add(new double[]{s.pos.x + 0.5, s.pos.z + 0.5}); continue; }
            for (int i = 0; i + 2 < s.points.length; i += 3) pts.add(new double[]{s.points[i], s.points[i + 2]});
        }
        int step = Math.max(1, (pts.size() + 69) / 70);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < pts.size(); i += step) add(b, pts.get(i));
        if (!pts.isEmpty() && (pts.size() - 1) % step != 0) add(b, pts.get(pts.size() - 1));
        return b.toString();
    }

    private static void add(StringBuilder b, double[] p) { if (b.length() > 0) b.append(';'); b.append(Math.round(p[0])).append(',').append(Math.round(p[1])); }

    static void blocker(SafetyData.Decision d, String kind, BlockPos p, String label) { blocker(d, kind, p.getX() + 0.5, p.getZ() + 0.5, label, ""); }
    static void blocker(SafetyData.Decision d, String kind, BlockPos p, String label, String other) { blocker(d, kind, p.getX() + 0.5, p.getZ() + 0.5, label, other); }
    static void blocker(SafetyData.Decision d, String kind, double x, double z, String label) { blocker(d, kind, x, z, label, ""); }

    static void blocker(SafetyData.Decision d, String kind, double x, double z, String label, String other) {
        if (d.blockers.size() >= 12) return;
        d.blockers.add(new String[]{kind, String.valueOf(Math.round(x)), String.valueOf(Math.round(z)), label.replace('|', '/').replace('\n', ' '), other == null ? "" : other});
    }

    /** the letter the mimic shows for a blocker kind */
    public static char letter(String kind) {
        switch (kind) {
            case "POINTS": return 'P';
            case "ROUTE": return 'R';
            case "LOCKED": return 'L';
            case "SECTION": return 'S';
            case "OVERLAP": return 'O';
            case "LINE": return 'B';
            case "INTEGRITY": return 'I';
            case "ISOLATED": return 'X';
            case "POWER": return 'E';
            default: return '?';
        }
    }

    /** an interlocking lock owner in words: a lined route, a driverless train, a desk */
    static String owner(World w, String owner) {
        if (owner.startsWith("route@")) {
            try { BlockPos p = BlockPos.fromLong(Long.parseLong(owner.substring(6))); return "the route from " + p.getX() + "," + p.getZ(); } catch (NumberFormatException e) { return owner; }
        }
        try {
            UUID u = UUID.fromString(owner);
            for (SafetyData.TrainRec r : SafetyData.get(w).trains.values()) if (u.equals(r.lead)) return "driverless train " + r.headcode;
            RailwayData.AutoTrain a = RailwayData.get(w).train(u);
            if (a != null) return "driverless train " + owner.substring(0, 8);
        } catch (IllegalArgumentException ignored) { }
        return owner;
    }

    /** console rows: the last 6 decisions with their geometry (m.v) */
    static void fill(World w, List<String[]> out) {
        SafetyData d = SafetyData.get(w);
        for (int i = 0; i < Math.min(6, d.decisions.size()); i++) {
            SafetyData.Decision dec = d.decisions.get(i);
            StringBuilder bl = new StringBuilder(), others = new StringBuilder();
            for (String[] b : dec.blockers) {
                String one = b[0] + "|" + b[1] + "|" + b[2] + "|" + b[3];
                if (bl.length() + one.length() + 1 > 1000) break;
                bl.append(bl.length() > 0 ? "\n" : "").append(one);
                if (!b[4].isEmpty() && others.length() + b[4].length() + 1 < 1000) others.append(others.length() > 0 ? "/" : "").append(b[4]);
            }
            BlockPos f = BlockPos.fromLong(dec.from), t = BlockPos.fromLong(dec.to);
            out.add(new String[]{"m.v", String.valueOf(dec.id), Recorder.clock(w, dec.tick), dec.route, dec.granted ? "1" : "0",
                    dec.path.length() > 1000 ? dec.path.substring(0, dec.path.lastIndexOf(';', 1000)) : dec.path, bl.toString(), others.toString(),
                    f.getX() + "," + f.getZ(), t.getX() + "," + t.getZ(), String.valueOf((w.getTotalWorldTime() - dec.tick) / 20)});
        }
    }

    /** /irextras sig conflict [n]: the n-th newest refused route request, every blocker with where it is */
    static void command(ICommandSender s, World w, String[] a) {
        SafetyData d = SafetyData.get(w);
        int n = a.length > 2 ? Math.max(1, parse(a[2])) : 1, k = 0;
        for (SafetyData.Decision dec : d.decisions) {
            if (dec.granted || ++k < n) continue;
            say(s, "§cREFUSED §r" + dec.route + " §7" + Recorder.clock(w, dec.tick) + " · " + dec.blockers.size() + " blocker(s):");
            for (String[] b : dec.blockers) say(s, " §e" + letter(b[0]) + " " + b[0] + "§r at " + b[1] + "," + b[2] + " - " + b[3]);
            say(s, "§7Shown on the console SAFETY > Routes mimic and on the dispatcher / signal box map");
            return;
        }
        say(s, "§aNo refused route requests since the server started");
    }

    private static int parse(String v) { try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return 1; } }
    private static void say(ICommandSender s, String m) { s.sendMessage(new TextComponentString(m)); }
}
