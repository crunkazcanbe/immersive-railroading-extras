package com.dogpound.railmap.planning;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.Disruptions;
import com.dogpound.railmap.auto.TrackPath;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.*;

/**
 * Temporary railway configurations (her "next 48" list #7), each put in and taken out by a WORK CREW:
 * <ul>
 *   <li><b>TSR</b> - a temporary speed restriction over a zone (boards out, every driverless train brakes for it, drivers
 *   of manual trains are warned when they run over it too fast);</li>
 *   <li><b>SINGLE-LINE WORKING</b> - one line of a double track is blocked, trains of BOTH directions share the other
 *   between two crossovers: a pilotman lets one direction in at a time (a train from the other end is stopped at the
 *   section entry until the last train out of it has cleared), at the single-line speed;</li>
 *   <li><b>HAND-WORKED CROSSING</b> - the level crossing's automatic equipment is out: every train stops short of it
 *   until the crossing keeper (console / desk / command) authorises it over, then crosses at 15 km/h;</li>
 *   <li><b>TRAIN-LENGTH LIMIT</b> - a loop or platform shortened by the works: a train longer than the limit is stopped
 *   before the zone (its length comes from IR's vehicle definitions);</li>
 *   <li><b>POSSESSION</b> - the zone is closed to traffic (incident-style block: trains stand at the last signal).</li>
 * </ul>
 * Every configuration has a window; it is PLANNED until its start, the crew then INSTALLS it (boards, clamps, the pilot,
 * the crossing keeper), it is ACTIVE until its end or until taken out, the crew REMOVES it, then it is CLEARED. Each step
 * goes to the alarms and the event recorder, and every change is versioned (#40).
 */
public final class TempWorks {
    private TempWorks() {}

    private static final Map<UUID, double[]> LAST = new HashMap<>();
    private static final Map<String, Set<UUID>> CROSSING_IN = new HashMap<>();
    private static final Map<String, Long> WARNED = new HashMap<>();

    public static long crewTicks(PlanData.Work w) { return (PlanConfig.crewSeconds + Math.round(w.r / 4)) * 20L; }

    public static String key(PlanData.Work w) { return "tw:" + w.id; }

    // ---- once a second -----------------------------------------------------------------------------------------------
    static void tick(World w, long now) {
        PlanData d = PlanData.get(w);
        if (d.works.isEmpty()) return;
        boolean ch = false;
        for (Iterator<PlanData.Work> it = d.works.iterator(); it.hasNext(); ) {
            PlanData.Work k = it.next();
            switch (k.phase) {
                case PLANNED:
                    if (PlanConfig.temporaryWorks && now >= k.start) { phase(w, d, k, PlanData.Phase.INSTALLING, now, "work crew on site, installing"); ch = true; }
                    break;
                case INSTALLING:
                    if (now - k.phaseAt >= crewTicks(k)) { phase(w, d, k, PlanData.Phase.ACTIVE, now, "IN FORCE"); ch = true; }
                    break;
                case ACTIVE:
                    if (!PlanConfig.temporaryWorks || k.end > 0 && now >= k.end) { phase(w, d, k, PlanData.Phase.REMOVING, now, "window over, crew taking it out"); ch = true; }
                    break;
                case REMOVING:
                    if (now - k.phaseAt >= crewTicks(k)) { phase(w, d, k, PlanData.Phase.CLEARED, now, "removed, line back to normal"); ch = true; }
                    break;
                case CLEARED:
                    if (now - k.phaseAt > 20 * 600) { it.remove(); ch = true; }
                    break;
            }
        }
        // live state: who is in each single-line section, trains over hand-worked crossings, warnings to manual drivers
        List<Locomotive> locos;
        try { locos = cam72cam.mod.world.World.get(w).getEntities(Locomotive.class); } catch (RuntimeException | LinkageError e) { locos = Collections.emptyList(); }
        Map<UUID, double[]> move = new HashMap<>();
        for (Locomotive l : locos) {
            Vec3d p = l.getPosition();
            double[] prev = LAST.get(l.getUUID());
            move.put(l.getUUID(), new double[]{p.x, p.z, prev == null ? 0 : p.x - prev[0], prev == null ? 0 : p.z - prev[1]});
        }
        LAST.clear();
        for (Map.Entry<UUID, double[]> e : move.entrySet()) LAST.put(e.getKey(), new double[]{e.getValue()[0], e.getValue()[1]});
        for (PlanData.Work k : d.works) {
            if (!k.live()) continue;
            if (k.kind == PlanData.Kind.SINGLE_LINE) {
                int in = 0, dir = 0;
                double ax = k.x2 - k.x, az = k.z2 - k.z;
                for (Locomotive l : locos) {
                    double[] m = move.get(l.getUUID());
                    if (distToSegment(m[0], m[1], k.x, k.z, k.x2, k.z2) > k.r) continue;
                    in++;
                    double dot = m[2] * ax + m[3] * az;
                    if (Math.abs(dot) > 0.01 && dir == 0) dir = dot > 0 ? 1 : -1;
                }
                int nd = in == 0 ? 0 : dir != 0 ? (k.dir == 0 ? dir : k.dir) : k.dir;
                if (nd != k.dir || in != k.inSection) {
                    if (nd != k.dir) Recorder.event(w, "Single-line working " + k.name + ": " + (nd == 0 ? "section clear, pilotman free" : "working " + (nd > 0 ? "A to B" : "B to A")));
                    k.dir = nd; k.inSection = in; ch = true;
                }
            } else if (k.kind == PlanData.Kind.CROSSING) {
                Set<UUID> inNow = new HashSet<>();
                for (Locomotive l : locos) { double[] m = move.get(l.getUUID()); if (Math.hypot(m[0] - k.x, m[1] - k.z) <= 6) inNow.add(l.getUUID()); }
                Set<UUID> was = CROSSING_IN.computeIfAbsent(key(k), x -> new HashSet<>());
                for (UUID u : was) if (!inNow.contains(u) && k.authorised > 0) { k.authorised--; ch = true; Recorder.event(w, "Hand-worked crossing " + k.name + ": train over, " + k.authorised + " still authorised"); }
                was.clear(); was.addAll(inNow);
            }
            if ((k.kind == PlanData.Kind.TSR || k.kind == PlanData.Kind.SINGLE_LINE) && k.kmh > 0)
                for (Locomotive l : locos) {
                    if (Autopilot.run(l.getUUID()) != null) continue;                                  // driverless trains brake for it themselves
                    double[] m = move.get(l.getUUID());
                    if (!inZone(k, m[0], m[1])) continue;
                    double kmh = Math.abs(l.getCurrentSpeed().metric());
                    String wk = key(k) + l.getUUID();
                    if (kmh > k.kmh + 5 && now - WARNED.getOrDefault(wk, -100000L) > 400) {
                        WARNED.put(wk, now);
                        for (EntityPlayer p : w.playerEntities)
                            if (p.getDistanceSq(m[0], p.posY, m[1]) < 24 * 24) p.sendStatusMessage(new TextComponentString("§6⚠ " + k.kind.label.toUpperCase(Locale.ROOT) + " " + k.kmh + " km/h - " + k.name + " §7(" + Math.round(kmh) + " km/h)"), true);
                    }
                }
        }
        if (ch) d.markDirty();
    }

    private static void phase(World w, PlanData d, PlanData.Work k, PlanData.Phase p, long now, String what) {
        k.phase = p;
        k.phaseAt = now;
        String txt = k.kind.label + " #" + k.id + " " + k.name + ": " + what;
        Recorder.event(w, txt);
        AlarmManager am = AlarmManager.get(w);
        BlockPos at = new BlockPos(k.x, k.y, k.z);
        if (p == PlanData.Phase.ACTIVE) am.raise(w, key(k), AlarmManager.Cat.GENERAL, AlarmManager.Sev.INFO, "WORKS IN FORCE: " + label(k), at, true);
        if (p == PlanData.Phase.CLEARED) am.clear(key(k));
        if (k.kind == PlanData.Kind.POSSESSION) {
            if (p == PlanData.Phase.ACTIVE) Disruptions.hold(w, key(k), "possession " + k.name, k.x, k.y, k.z, k.r);
            if (p == PlanData.Phase.REMOVING || p == PlanData.Phase.CLEARED) Disruptions.release(w, key(k), "possession given up");
        }
        d.markDirty();
    }

    public static String label(PlanData.Work k) {
        switch (k.kind) {
            case TSR: return "TSR " + k.kmh + " km/h " + k.name;
            case SINGLE_LINE: return "SLW " + k.name + " " + k.kmh + " km/h";
            case CROSSING: return "HAND-WORKED LX " + k.name;
            case LENGTH: return "MAX " + k.maxLenM + " M " + k.name;
            default: return "POSSESSION " + k.name;
        }
    }

    // ---- set up / take out ----------------------------------------------------------------------------------------------
    public static PlanData.Work add(World w, PlanData.Kind kind, String name, double x, double y, double z, double r, int value, long startIn, long minutes, String who) {
        PlanData d = PlanData.get(w);
        PlanData.Work k = new PlanData.Work();
        k.id = d.id();
        k.kind = kind; k.name = name.isEmpty() ? kind.name().toLowerCase(Locale.ROOT) + " " + k.id : name;
        k.x = x; k.y = y; k.z = z; k.r = Math.max(4, r); k.by = who;
        if (kind == PlanData.Kind.LENGTH) k.maxLenM = Math.max(20, value); else k.kmh = Math.max(5, value);
        long now = w.getTotalWorldTime();
        k.start = now + Math.max(0, startIn);
        k.end = minutes <= 0 ? 0 : k.start + minutes * 1200L;
        k.phaseAt = now;
        d.works.add(k);
        d.markDirty();
        Recorder.event(w, k.kind.label + " #" + k.id + " " + k.name + " planned by " + who);
        Versions.commit(w, "works", who, "planned " + label(k));
        return k;
    }

    public static String remove(World w, int id, String who) {
        PlanData d = PlanData.get(w);
        for (PlanData.Work k : d.works) {
            if (k.id != id) continue;
            long now = w.getTotalWorldTime();
            if (k.phase == PlanData.Phase.PLANNED) { k.phase = PlanData.Phase.CLEARED; k.phaseAt = now; d.markDirty(); Versions.commit(w, "works", who, "cancelled " + label(k)); return "§eCancelled before the crew went out: " + label(k); }
            if (k.phase == PlanData.Phase.INSTALLING || k.phase == PlanData.Phase.ACTIVE) { phase(w, d, k, PlanData.Phase.REMOVING, now, "taken out by " + who); Versions.commit(w, "works", who, "taking out " + label(k)); return "§eWork crew sent to take out " + label(k); }
            return "§7Already " + k.phase.name().toLowerCase(Locale.ROOT);
        }
        return "§cNo temporary configuration #" + id;
    }

    public static String authorise(World w, int id, int trains, String who) {
        for (PlanData.Work k : PlanData.get(w).works)
            if (k.id == id && k.kind == PlanData.Kind.CROSSING) {
                if (!k.live()) return "§7That crossing isn't under hand working";
                k.authorised = Math.max(0, Math.min(9, k.authorised + trains));
                PlanData.get(w).markDirty();
                Recorder.event(w, "Crossing keeper at " + k.name + " (" + who + "): barriers down, " + k.authorised + " train(s) authorised over");
                return "§a" + k.authorised + " train(s) authorised over " + k.name;
            }
        return "§cNo hand-worked crossing #" + id;
    }

    // ---- what the driverless trains obey (Wayside) ---------------------------------------------------------------------
    static boolean inZone(PlanData.Work k, double x, double z) {
        if (k.kind == PlanData.Kind.SINGLE_LINE) return distToSegment(x, z, k.x, k.z, k.x2, k.z2) <= k.r;
        return Math.hypot(x - k.x, z - k.z) <= k.r;
    }

    /** Wayside: {distance, kmh, stop, what, where} for every temporary configuration along a driverless train's path */
    public static List<Object[]> restrictions(World w, TrackPath path) {
        if (w == null || w.isRemote || path == null || path.steps.isEmpty() || !PlanConfig.temporaryWorks) return Collections.emptyList();
        PlanData d = PlanData.get(w);
        if (d.works.isEmpty()) return Collections.emptyList();
        List<Object[]> out = new ArrayList<>();
        double trainLen = -1;
        for (PlanData.Work k : d.works) {
            if (!k.live() || k.kind == PlanData.Kind.POSSESSION) continue;
            int first = -1;
            for (int i = 0; i < path.steps.size(); i++) {
                TrackPath.Step st = path.steps.get(i);
                if (inZone(k, st.pos.x + 0.5, st.pos.z + 0.5) || k.kind == PlanData.Kind.CROSSING && Math.hypot(st.pos.x + 0.5 - k.x, st.pos.z + 0.5 - k.z) <= 8) { first = i; break; }
            }
            if (first < 0) continue;
            double dist = first == 0 ? 0 : path.steps.get(first - 1).distance;
            BlockPos at = new BlockPos(k.x, k.y, k.z);
            switch (k.kind) {
                case TSR:
                    out.add(new Object[]{dist, (double) k.kmh, false, "TSR " + k.kmh + " km/h - works: " + k.name, at});
                    break;
                case SINGLE_LINE: {
                    out.add(new Object[]{dist, (double) k.kmh, false, "Single-line working " + k.kmh + " km/h - " + k.name, at});
                    if (first == 0 || k.dir == 0) break;                                              // already in it, or the section is free
                    int j = Math.min(path.steps.size() - 1, first + 6);
                    double hx = path.steps.get(j).pos.x - path.steps.get(first).pos.x, hz = path.steps.get(j).pos.z - path.steps.get(first).pos.z;
                    int mine = hx * (k.x2 - k.x) + hz * (k.z2 - k.z) >= 0 ? 1 : -1;
                    if (mine != k.dir) out.add(new Object[]{Math.max(0, dist - 10), 0.0, true, "Single-line working " + k.name + ": wait for the pilotman (working " + (k.dir > 0 ? "A to B" : "B to A") + ", " + k.inSection + " in section)", at});
                    break;
                }
                case CROSSING:
                    if (k.authorised <= 0 && first > 0) out.add(new Object[]{Math.max(0, dist - 15), 0.0, true, "Hand-worked crossing " + k.name + ": wait for the crossing keeper", at});
                    else out.add(new Object[]{Math.max(0, dist - 15), 15.0, false, "Hand-worked crossing " + k.name + ": 15 km/h over the crossing", at});
                    break;
                case LENGTH: {
                    if (first == 0) break;
                    if (trainLen < 0) trainLen = trainLength(w, path);
                    if (trainLen > k.maxLenM) out.add(new Object[]{Math.max(0, dist - 10), 0.0, true, String.format(Locale.ROOT, "Train too long (%.0f m) for %s, limit %d m", trainLen, k.name, k.maxLenM), at});
                    break;
                }
                default:
            }
        }
        return out;
    }

    /** the length of the train this path belongs to: the loco nearest the path's start */
    private static double trainLength(World w, TrackPath path) {
        TrackPath.Step s0 = path.steps.get(0);
        Locomotive best = null;
        double bd = 40 * 40;
        try {
            for (Locomotive l : cam72cam.mod.world.World.get(w).getEntities(Locomotive.class)) {
                Vec3d p = l.getPosition();
                double dd = (p.x - s0.pos.x) * (p.x - s0.pos.x) + (p.z - s0.pos.z) * (p.z - s0.pos.z);
                if (dd < bd) { bd = dd; best = l; }
            }
        } catch (RuntimeException | LinkageError e) { return 0; }
        return best == null ? 0 : lengthOf(best);
    }

    public static double lengthOf(EntityCoupleableRollingStock s) {
        double m = 0;
        try { for (EntityCoupleableRollingStock c : s.getTrain(false)) m += c.getDefinition().getLength(c.gauge); } catch (RuntimeException e) { m = 20; }
        return m;
    }

    public static double distToSegment(double px, double pz, double ax, double az, double bx, double bz) {
        double dx = bx - ax, dz = bz - az, l2 = dx * dx + dz * dz;
        double t = l2 <= 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (pz - az) * dz) / l2));
        return Math.hypot(px - (ax + t * dx), pz - (az + t * dz));
    }

    public static void clear() { LAST.clear(); CROSSING_IN.clear(); WARNED.clear(); }
}
