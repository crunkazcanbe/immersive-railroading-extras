package com.dogpound.railmap.wire;

import com.dogpound.railmap.ctl.CtlConfig;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Distributed control and communications failure (her "next 48" list #25). Field gear inside a Field RTU's coverage no
 * longer reports straight to the control room: its telemetry is polled by the RTU and sent over the RTU's links -
 * A, fibre (fast, near loss-free, can be cut), and B, GSM-R radio through a healthy radio mast (slower, lossy, worse in
 * thunderstorms) - with failover between them. When a poll doesn't get through, the remote panels (the console's
 * WIRED page and every remote screen) keep showing the LAST GOOD value, flagged STALE with its age; console commands to
 * gear behind a dead path are refused ("no comms path - operate locally"). The gear itself, and the interlocking, keep
 * working locally the whole time - only the remote view and remote control go. Gear no RTU covers is direct-wired as it
 * always was, so an old railway is unchanged until RTUs are placed.
 */
public final class Comms {
    private Comms() {}

    /** an RTU as the comms model sees it (filled by TileRtu every second while its chunk is loaded) */
    public static final class Rtu {
        public long pos;
        public String id = "";
        public int radius = 48;
        public boolean cpuOk = true, aUp, bUp, aService = true, bService = true;
        public int path;                     // 0 none, 1 A fibre, 2 B radio
        public int prefer = 1;
        public double latencyMs, loss;
        public long lastGood = -1000, seen, failoverUntil;
        public long tx, rx, lost;
        public String why = "";
    }

    private static final Map<Integer, Map<Long, Rtu>> RTUS = new HashMap<>();
    /** last good snapshot of each covered gear's console entry */
    private static final Map<Integer, Map<Long, NBTTagCompound>> SNAP = new HashMap<>();
    /** injected cuts: "A:pos", "B:pos", "CPU:pos", "PART:x:z:r" (chaos / commands; memory only) */
    private static final Map<Integer, Set<String>> CUTS = new HashMap<>();

    public static Map<Long, Rtu> rtus(World w) { return RTUS.computeIfAbsent(w.provider.getDimension(), k -> new HashMap<>()); }
    public static Rtu rtu(World w, long pos) { return rtus(w).computeIfAbsent(pos, k -> { Rtu r = new Rtu(); r.pos = k; return r; }); }
    public static void forget(World w, long pos) { rtus(w).remove(pos); }
    public static Set<String> cuts(World w) { return CUTS.computeIfAbsent(w.provider.getDimension(), k -> new LinkedHashSet<>()); }

    public static boolean cut(World w, String kind, long pos) {
        Set<String> c = cuts(w);
        if (c.contains(kind + ":" + pos)) return false;
        for (String s : c) if (s.startsWith("PART:")) {
            String[] p = s.split(":");
            BlockPos b = BlockPos.fromLong(pos);
            double dx = b.getX() - Double.parseDouble(p[1]), dz = b.getZ() - Double.parseDouble(p[2]), r = Double.parseDouble(p[3]);
            if (!kind.equals("CPU") && dx * dx + dz * dz <= r * r) return true;
        }
        return false;
    }

    /** a comms partition: every RTU link inside the circle goes down (both paths) until healed */
    public static void partition(World w, int x, int z, int r, boolean on) {
        String k = "PART:" + x + ":" + z + ":" + r;
        if (on) cuts(w).add(k); else cuts(w).remove(k);
    }

    public static void clearCuts(World w) { cuts(w).clear(); }

    /** the RTU whose coverage holds this gear (nearest live one), or null = direct-wired */
    public static Rtu cover(World w, BlockPos p) {
        if (!CtlConfig.commsSimulation) return null;
        long now = w.getTotalWorldTime();
        Rtu best = null;
        double bd = Double.MAX_VALUE;
        for (Rtu r : rtus(w).values()) {
            if (now - r.seen > 80) continue;
            BlockPos b = BlockPos.fromLong(r.pos);
            if (b.equals(p)) return null;                                     // the RTU itself reports direct
            double d = b.distanceSq(p);
            if (d <= (double) r.radius * r.radius && d < bd) { bd = d; best = r; }
        }
        return best;
    }

    public static boolean stale(World w, Rtu r) { return r != null && (!r.cpuOk || w.getTotalWorldTime() - r.lastGood > CtlConfig.commsStaleSeconds * 20L); }

    /** what a remote panel may show for this gear: the fresh entry, or the last good snapshot flagged STALE */
    public static NBTTagCompound view(World w, BlockPos p, NBTTagCompound fresh) {
        Rtu r = cover(w, p);
        if (r == null) return fresh;
        Map<Long, NBTTagCompound> snaps = SNAP.computeIfAbsent(w.provider.getDimension(), k -> new HashMap<>());
        if (!stale(w, r)) {
            snaps.put(p.toLong(), fresh.copy());
            fresh.setString("trust", "FRESH via " + r.id + (r.path == 2 ? " radio" : " fibre") + String.format(Locale.ROOT, " %.0f ms", r.latencyMs));
            return fresh;
        }
        NBTTagCompound old = snaps.get(p.toLong());
        NBTTagCompound out = old == null ? fresh.copy() : old.copy();
        long age = (w.getTotalWorldTime() - r.lastGood) / 20;
        String what = !r.cpuOk ? "RTU " + r.id + " FAILED" : "NO DATA " + (age > 3600 ? ">1h" : age + "s");
        out.setString("s", "§6[STALE - " + what + "] §7" + (old == null ? "no value received yet" : old.getString("s")));
        if (out.getInteger("l") != 3) out.setInteger("l", 2);
        out.setString("trust", "STALE " + what);
        out.setBoolean("stale", true);
        return out;
    }

    /** a remote command to gear: null = it goes through; else why it didn't */
    public static String command(World w, BlockPos target) {
        Rtu r = cover(w, target);
        if (r == null) return null;
        if (!r.cpuOk) return "§cField controller " + r.id + " has FAILED - command not delivered. Operate the gear locally.";
        if (r.path == 0) return "§cNO COMMS PATH to " + r.id + " (" + r.why + ") - command not sent. Operate the gear locally.";
        Random rnd = w.rand;
        for (int i = 0; i < 3; i++) {
            r.tx++;
            if (rnd.nextDouble() >= r.loss) { r.rx++; return null; }
            r.lost++;
        }
        return "§cCommand lost 3 times on " + (r.path == 2 ? "link B (radio)" : "link A (fibre)") + " to " + r.id + " - not executed";
    }

    /** console diagnostics for one gear position: {trust, age s, rtu, path} */
    public static String[] diag(World w, BlockPos p) {
        Rtu r = cover(w, p);
        if (r == null) return new String[]{"DIRECT", "0", "-", "hard-wired"};
        long age = Math.max(0, (w.getTotalWorldTime() - r.lastGood) / 20);
        return new String[]{stale(w, r) ? "STALE" : r.latencyMs > 250 ? "DELAYED" : "FRESH", String.valueOf(age), r.id, !r.cpuOk ? "RTU FAILED" : r.path == 1 ? "A fibre" : r.path == 2 ? "B radio" : "NO PATH"};
    }
}
