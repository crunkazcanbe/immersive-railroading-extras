package com.dogpound.railmap.safety;

import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.tile.TileRail;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import com.dogpound.railmap.RailMapConfig;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.*;
import com.dogpound.railmap.graph.TrainNode;
import com.dogpound.railmap.server.TrainTracker;
import com.dogpound.railmap.signal.TrackFollower;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.*;

/**
 * Independent safety supervisor (her batch-3 list #20): a watchdog that does not trust the autopilot. Twice a second,
 * for every driverless train inside a powered Safety Supervisor rack's coverage, it builds its OWN picture - its own
 * track path ahead of the loco, the lineside restrictions on it (Wayside), trains ahead (train tracking), the line's
 * speed limit - and its own braking curve, in two channels: A at the autopilot's design deceleration, B at 85 % of it.
 * Then it checks what the autopilot is actually commanding against that authority envelope:
 * <ul>
 *   <li>OVERSPEED - faster than channel A's envelope + the margin (both channels agree = a violation; only B = a channel
 *   discrepancy, logged, the 2oo2 vote stops spurious interventions);</li>
 *   <li>POWER AT LIMIT - throttle open while at / over the envelope;</li>
 *   <li>STOP APPROACH - closing on a stop (signal, end of authority, train ahead) faster than the curve allows;</li>
 *   <li>WATCHDOG - the autopilot's control cycle has not run for 3 s while the train moves.</li>
 * </ul>
 * MONITOR (default) logs and alarms. ENFORCE also cuts power, applies the brakes and holds the train on the console
 * until RESET on the rack or the console. A rack without its mains or 12 V supervises nothing: alarm, existing holds stay.
 */
public final class Supervisor {
    private Supervisor() {}

    static final double B_FACTOR = 0.85;

    static final class St {
        String label = "", verdict = "ok", limitedBy = "";
        double speed, envA, envB, signKmh = Double.MAX_VALUE;
        boolean held, violating, disagree;
        long lastLog;
        BlockPos rack;
    }

    private static final Map<UUID, St> STATES = new HashMap<>();

    /** the braking curve: fastest speed now that still gets down to target within distance at decel */
    static double envelope(double distance, double targetKmh, double decel) { return Wayside.allowed(distance, targetKmh, decel); }

    static void tick(World w) {
        for (TileSafetySupervisor t : SigTicker.tiles(w, TileSafetySupervisor.class)) t.second();
    }

    static void fast(World w, long now) {
        List<TileSafetySupervisor> racks = SigTicker.tiles(w, TileSafetySupervisor.class);
        if (racks.isEmpty()) { if (!STATES.isEmpty()) STATES.clear(); return; }
        long t0 = System.nanoTime();
        RailwayData data = RailwayData.get(w);
        cam72cam.mod.world.World umc = cam72cam.mod.world.World.get(w);
        Map<UUID, Locomotive> locos = new HashMap<>();
        for (Locomotive l : umc.getEntities(Locomotive.class)) if (!l.isDead()) locos.put(l.getUUID(), l);
        List<TrainNode> nodes = TrainTracker.latest(w);
        Map<TileSafetySupervisor, int[]> counts = new HashMap<>();
        Set<UUID> live = new HashSet<>();
        for (RailwayData.AutoTrain a : data.trains().values()) {
            Locomotive l = locos.get(a.loco);
            Autopilot.Run run = Autopilot.run(a.loco);
            if (l == null || run == null) continue;
            Vec3d p = l.getPosition();
            TileSafetySupervisor rack = null;
            for (TileSafetySupervisor r : racks) if (r.online() && r.covers(p.x, p.z) && (rack == null || r.enforce())) rack = r;
            if (rack == null) continue;
            live.add(a.loco);
            St st = STATES.computeIfAbsent(a.loco, u -> new St());
            st.label = Autopilot.label(a, l);
            st.rack = rack.getPos();
            counts.computeIfAbsent(rack, k -> new int[2])[0]++;
            try { check(w, umc, a, l, run, st, nodes, now); } catch (RuntimeException ex) { st.verdict = "check failed: " + ex; }
            if (st.violating && rack.enforce() && !st.held) {
                st.held = true;
                ConsoleControl.hold(w.provider.getDimension(), a.loco, true);
                rack.intervened(st.label + ": " + st.verdict);
                data(w).interventions++;
                log(w, "ENFORCE " + st.label + " held - " + st.verdict);
                AlarmManager.get(w).raise(w, "sup:" + a.loco, AlarmManager.Cat.AUTO, AlarmManager.Sev.CRITICAL, "Safety supervisor INTERVENED: " + st.label + " - " + st.verdict, new BlockPos(p.x, p.y, p.z), true);
            }
            if (st.held) { l.setThrottle(0); l.setTrainBrake(1); ConsoleControl.hold(w.provider.getDimension(), a.loco, true); }
            if (st.violating) rack.violation(st.label + ": " + st.verdict);
            if (st.envA > 0 && st.envA < 1e5) counts.get(rack)[1] = Math.max(counts.get(rack)[1], (int) Math.round(st.speed / Math.max(1, st.envA) * 100));
        }
        STATES.keySet().removeIf(u -> !live.contains(u) && !STATES.get(u).held);
        long cyc = System.nanoTime() - t0;
        for (TileSafetySupervisor r : racks) { int[] c = counts.getOrDefault(r, new int[2]); r.pass(c[0], c[1], cyc); }
    }

    private static void check(World w, cam72cam.mod.world.World umc, RailwayData.AutoTrain a, Locomotive l, Autopilot.Run run, St st, List<TrainNode> nodes, long now) {
        double speed = Math.abs(l.getCurrentSpeed().metric());
        st.speed = speed;
        st.violating = false;
        st.disagree = false;
        st.limitedBy = "";
        if (speed < 1) { st.verdict = "standing"; st.envA = st.envB = Math.min(a.maxKmh, st.signKmh); return; }
        Vec3d p = l.getPosition(), v = l.getVelocity();
        double vl = Math.sqrt(v.x * v.x + v.z * v.z);
        if (vl < 1e-4) { st.verdict = "no motion vector"; return; }
        double dx = v.x / vl, dz = v.z / vl;
        TileRail rail = TrackFollower.railUnder(umc, p.x, p.y, p.z);
        if (rail == null) { st.verdict = "not on track"; return; }
        double decel = RailMapConfig.autopilotBraking;
        double budget = Math.max(200, speed / 3.6 * speed / 3.6 / (2 * decel * B_FACTOR) + 80);
        Vec3i from = TrackPath.behind(rail, p.x, p.y, p.z, dx, dz);
        TrackPath path = TrackPath.ahead(umc, rail, from, budget);
        double cur = Math.max(0, path.distanceTo(p.x, p.y, p.z, 3.5));
        double line = Math.min(a.maxKmh, st.signKmh);
        double envA = line, envB = line;
        String whyA = "line speed " + Math.round(line), whyB = whyA;
        boolean stopNear = false;
        for (Wayside.Limit lim : Wayside.read(w, path)) {
            double dd = lim.distance - cur;
            if (!lim.stop && lim.kmh < 1e6 && dd <= 0.5 && dd > -4) st.signKmh = lim.kmh;
            if (dd < -1) continue;
            double ea = lim.stop ? envelope(dd - 3, 0, decel) : envelope(Math.max(0, dd), lim.kmh, decel);
            double eb = lim.stop ? envelope(dd - 3, 0, decel * B_FACTOR) : envelope(Math.max(0, dd), lim.kmh, decel * B_FACTOR);
            if (ea < envA) { envA = ea; whyA = lim.what + " " + Math.round(dd) + " m"; stopNear = lim.stop; }
            if (eb < envB) { envB = eb; whyB = lim.what; }
        }
        // trains ahead (not our own consist)
        int mine = Integer.MIN_VALUE;
        for (TrainNode n : nodes) if (n.id == l.getId()) mine = n.consist;
        for (TrainNode n : nodes) {
            if (n.consist == mine && mine != Integer.MIN_VALUE || n.id == l.getId()) continue;
            double d = path.distanceTo(n.x, n.y, n.z, 2.5);
            if (d < 0 || d <= cur + 2) continue;
            double ea = envelope(d - cur - 10, 0, decel), eb = envelope(d - cur - 10, 0, decel * B_FACTOR);
            if (ea < envA) { envA = ea; whyA = "train ahead " + Math.round(d - cur) + " m"; stopNear = true; }
            if (eb < envB) envB = eb;
        }
        st.envA = envA;
        st.envB = envB;
        st.limitedBy = whyA;
        double m = SigConfig.supervisorMarginKmh;
        boolean overA = speed > envA + m, overB = speed > envB + m;
        String verdict = "ok";
        if (overA) verdict = (stopNear ? "STOP APPROACH too fast: " : "OVERSPEED: ") + Math.round(speed) + " km/h, envelope " + Math.round(envA) + " (" + whyA + ")";
        else if (l.getThrottle() > 0.05f && speed >= envA - 0.5) verdict = "POWER AT LIMIT: throttle " + Math.round(l.getThrottle() * 100) + "% at " + Math.round(speed) + " km/h, envelope " + Math.round(envA);
        else if (now - run.beat > 60) verdict = "WATCHDOG: autopilot control cycle silent for " + (now - run.beat) / 20 + " s while moving";
        st.violating = !verdict.equals("ok");
        st.disagree = overB && !overA;
        if (st.disagree && now - st.lastLog > 200) { st.lastLog = now; log(w, "XC discrepancy " + st.label + ": channel B " + Math.round(envB) + " km/h (" + whyB + ") vs A " + Math.round(envA) + " - no intervention (2oo2)"); }
        if (st.violating && !verdict.equals(st.verdict) && now - st.lastLog > 100) {
            st.lastLog = now;
            log(w, "VIOLATION " + st.label + ": " + verdict);
            Recorder.event(w, "SAFETY SUPERVISOR: " + st.label + " - " + verdict);
            if (!st.held) AlarmManager.get(w).raise(w, "supv:" + a.loco, AlarmManager.Cat.AUTO, AlarmManager.Sev.MAJOR, "Safety supervisor: " + st.label + " - " + verdict, new BlockPos(p.x, p.y, p.z), false);
        }
        st.verdict = verdict;
    }

    /** RESET: release one train (uuid or label) or all the supervisor holds */
    static String reset(World w, String which, String who) {
        int n = 0;
        for (Map.Entry<UUID, St> e : STATES.entrySet()) {
            St st = e.getValue();
            if (!st.held || which != null && !e.getKey().toString().equals(which) && !st.label.equalsIgnoreCase(which)) continue;
            st.held = false;
            ConsoleControl.hold(w.provider.getDimension(), e.getKey(), false);
            AlarmManager.get(w).clear("sup:" + e.getKey());
            log(w, "RESET " + st.label + " released by " + who);
            n++;
        }
        return n == 0 ? "§7No train held by the supervisor" : "§aSupervisor released " + n + " train(s)";
    }

    static List<String> status(World w) {
        List<String> out = new ArrayList<>();
        for (TileSafetySupervisor r : SigTicker.tiles(w, TileSafetySupervisor.class)) out.add("§eRack " + r.getPos().getX() + "," + r.getPos().getZ() + "§r " + r.gearState());
        for (St st : STATES.values()) out.add((st.held ? "§c HELD " : st.violating ? "§6 " : "§a ") + st.label + "§r " + Math.round(st.speed) + " km/h · env A " + Math.round(st.envA) + " B " + Math.round(st.envB) + " · " + st.verdict);
        SupData d = data(w);
        out.add("§7" + d.interventions + " intervention(s) total");
        for (int i = 0; i < Math.min(8, d.log.size()); i++) out.add(" §7" + d.log.get(i));
        if (out.size() == 1) out.add(0, "No safety supervisor racks loaded");
        return out;
    }

    static void fill(World w, List<String[]> out, long now) {
        for (Map.Entry<UUID, St> e : STATES.entrySet()) {
            St st = e.getValue();
            out.add(new String[]{"a.sup", e.getKey().toString(), st.label, String.valueOf(Math.round(st.speed)), st.envA > 1e5 ? "-" : String.valueOf(Math.round(st.envA)),
                    st.envB > 1e5 ? "-" : String.valueOf(Math.round(st.envB)), st.verdict, st.held ? "1" : st.violating ? "2" : "0", st.limitedBy, st.disagree ? "1" : "0", st.rack == null ? "" : String.valueOf(st.rack.toLong())});
        }
        for (TileSafetySupervisor r : SigTicker.tiles(w, TileSafetySupervisor.class))
            out.add(new String[]{"a.rack", r.getPos().getX() + "," + r.getPos().getZ(), r.online() ? "1" : "0", r.enforce() ? "ENFORCE" : "MONITOR", r.coverName(), r.gearState()});
        SupData d = data(w);
        for (int i = 0; i < Math.min(40, d.log.size()); i++) out.add(new String[]{"a.sv", d.log.get(i)});
        out.add(new String[]{"a.svn", String.valueOf(d.interventions)});
    }

    static void log(World w, String line) {
        SupData d = data(w);
        d.log.add(0, Recorder.clock(w, w.getTotalWorldTime()) + " " + line);
        while (d.log.size() > 100) d.log.remove(d.log.size() - 1);
        d.markDirty();
    }

    static SupData data(World w) {
        MapStorage s = w.getPerWorldStorage();
        SupData d = (SupData) s.getOrLoadData(SupData.class, SupData.NAME);
        if (d == null) { d = new SupData(); s.setData(SupData.NAME, d); }
        return d;
    }

    /** the supervisor's log book, saved with the world */
    public static final class SupData extends WorldSavedData {
        static final String NAME = "irextras_supervisor";
        final List<String> log = new ArrayList<>();
        int interventions;
        public SupData() { super(NAME); }
        public SupData(String n) { super(n); }
        @Override public void readFromNBT(NBTTagCompound t) {
            log.clear();
            NBTTagList l = t.getTagList("log", 8);
            for (int i = 0; i < l.tagCount(); i++) log.add(l.getStringTagAt(i));
            interventions = t.getInteger("iv");
        }
        @Override public NBTTagCompound writeToNBT(NBTTagCompound t) {
            NBTTagList l = new NBTTagList();
            for (String s : log) l.appendTag(new NBTTagString(s));
            t.setTag("log", l);
            t.setInteger("iv", interventions);
            return t;
        }
    }
}
