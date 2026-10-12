package com.dogpound.railmap.auto;

import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.tile.TileRail;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.RailMapConfig;
import com.dogpound.railmap.signal.TrackFollower;
import com.dogpound.railmap.program.ProgramData;
import com.dogpound.railmap.server.RailProps;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Disruption recovery (her ChatGPT list): when a train wrecks, or a driverless train is stranded with no power, its
 * stretch of line is blocked. Route searches avoid it (driverless trains take another way round when there is one),
 * trains that can't get round stop at the last signal before it (Wayside stop limit, so player trains under protection
 * too), a recovery crew comes out and stands the cars up / rerails them / cuts write-offs up, and once the line is clear
 * the held trains set off again one at a time, nearest first. Every step goes to the signal-box alarms (Control Center).
 * Delays are counted per train while it is held.
 */
public final class Disruptions {
    private Disruptions() {}

    public enum Kind { WRECK, FAILED, HOLD }   // HOLD: a stretch closed by incident command (incident/IncidentLog)

    public enum Phase {
        REPORTED("reported, recovery crew called"), CREW("recovery crew on the way"), WORKING("recovery crew at work"),
        CLEAR("line clear, restarting trains"), STRANDED("train without power");

        public final String label;

        Phase(String label) { this.label = label; }
    }

    public static final class Incident {
        public final int id;
        public final Kind kind;
        public final Set<UUID> stock = new LinkedHashSet<>();
        public String what = "";
        /** HOLD: incident command's key for it */
        public String key = "";
        public double x, y, z, radius = 8;
        public Phase phase;
        long phaseAt, nextWork, lastRelease;
        public final long started;
        /** driverless trains held for it, in the order they reached it */
        public final List<UUID> held = new ArrayList<>();
        final Set<UUID> released = new HashSet<>();

        Incident(int id, Kind kind, long now) {
            this.id = id;
            this.kind = kind;
            this.started = now;
            this.phaseAt = now;
        }

        public boolean blocking() { return phase != Phase.CLEAR; }

        public BlockPos pos() { return new BlockPos(x, y, z); }

        boolean near(double px, double py, double pz, double extra) {
            double dx = px - x, dz = pz - z, r = radius + extra;
            return dx * dx + dz * dz <= r * r && Math.abs(py - y) <= 6;
        }
    }

    private static final Map<Integer, List<Incident>> BY_DIM = new HashMap<>();
    private static final Map<UUID, Integer> DELAY = new HashMap<>();          // seconds late, per driverless train
    private static final Map<UUID, Long> NO_POWER_SINCE = new HashMap<>();
    private static int nextId = 1;
    /** bumps when the blocked line changes: driverless trains replan their routes */
    private static int version;

    public static int version() { return version; }

    public static List<Incident> incidents(int dim) { return BY_DIM.getOrDefault(dim, Collections.emptyList()); }

    public static int delaySeconds(UUID train) { return DELAY.getOrDefault(train, 0); }

    // ---- once a second (from the autopilot pass) -------------------------------------------

    public static void tick(World world, RailwayData data, long now) {
        if (!RailMapConfig.disruptionRecovery) {
            if (!incidents(world.provider.getDimension()).isEmpty()) { BY_DIM.remove(world.provider.getDimension()); version++; }
            return;
        }
        int dim = world.provider.getDimension();
        List<Incident> list = BY_DIM.computeIfAbsent(dim, k -> new ArrayList<>());
        cam72cam.mod.world.World umc = cam72cam.mod.world.World.get(world);
        Map<UUID, EntityMoveableRollingStock> all = new HashMap<>();
        for (EntityMoveableRollingStock s : umc.getEntities(EntityMoveableRollingStock.class)) if (!s.isDead()) all.put(s.getUUID(), s);

        // wrecks: every wrecked car joins an incident (cars of one pile-up share it)
        for (EntityMoveableRollingStock s : all.values()) {
            if (s.wreck == 0) continue;
            Vec3d p = s.getPosition();
            Incident in = find(list, s.getUUID());
            if (in == null) {
                for (Incident o : list) if (o.kind == Kind.WRECK && o.blocking() && o.near(p.x, p.y, p.z, 12)) { in = o; break; }
                if (in == null) {
                    in = new Incident(nextId++, Kind.WRECK, now);
                    in.phase = RailMapConfig.recoveryCrewSeconds > 0 ? Phase.REPORTED : Phase.WORKING;
                    in.what = name(s);
                    list.add(in);
                    alarm(world, "§cWRECK: " + in.what + " " + state(s.wreck) + " - line blocked (incident #" + in.id + ")", p);
                    version++;
                }
                in.stock.add(s.getUUID());
                spread(in, all);
            }
        }

        // stranded trains: a driverless train standing with no power blocks its line
        for (RailwayData.AutoTrain a : data.trains().values()) {
            EntityMoveableRollingStock s = all.get(a.loco);
            if (!(s instanceof Locomotive l)) continue;
            RailProps.Props pr = RailProps.get(world).peek(a.loco);
            boolean dead = pr != null && pr.source == 4 && Math.abs(l.getCurrentSpeed().metric()) < 1;
            if (!dead) { NO_POWER_SINCE.remove(a.loco); continue; }
            long since = NO_POWER_SINCE.computeIfAbsent(a.loco, k -> now);
            if (now - since < RailMapConfig.failedTrainSeconds * 20L || find(list, a.loco) != null) continue;
            Incident in = new Incident(nextId++, Kind.FAILED, now);
            in.phase = Phase.STRANDED;
            in.what = name(s);
            in.stock.add(a.loco);
            spread(in, all);
            list.add(in);
            alarm(world, "§6TRAIN FAILED: " + in.what + " has no power - line blocked (incident #" + in.id + ")", s.getPosition());
            version++;
        }

        // each incident's recovery
        for (Iterator<Incident> it = list.iterator(); it.hasNext(); ) {
            Incident in = it.next();
            in.held.removeIf(u -> !data.trains().containsKey(u));            // back to manual / gone: not waiting any more
            in.stock.removeIf(u -> !all.containsKey(u) && in.kind == Kind.WRECK);   // scrapped / picked up
            if (in.blocking()) step(world, in, all, now);
            if (in.phase == Phase.CLEAR) {
                // release the held trains one at a time, nearest first (they were queued in arrival order)
                if (now - in.lastRelease >= RailMapConfig.resumeGapSeconds * 20L) {
                    for (UUID u : in.held) {
                        if (in.released.add(u)) {
                            in.lastRelease = now;
                            break;
                        }
                    }
                }
                if (in.released.containsAll(in.held) && now - in.lastRelease >= RailMapConfig.resumeGapSeconds * 20L) {
                    it.remove();
                    version++;
                }
            }
        }

        // delays: every second a driverless train spends held counts
        for (Incident in : list) for (UUID u : in.held) if (!in.released.contains(u)) DELAY.merge(u, 1, Integer::sum);
        DELAY.keySet().removeIf(u -> !data.trains().containsKey(u));
    }

    private static void step(World world, Incident in, Map<UUID, EntityMoveableRollingStock> all, long now) {
        if (in.kind == Kind.HOLD) return;                                       // stays closed until incident command releases it
        if (in.kind == Kind.FAILED) {
            EntityMoveableRollingStock s = all.get(in.stock.iterator().next());
            RailProps.Props pr = s == null ? null : RailProps.get(world).peek(s.getUUID());
            if (s != null && !in.near(s.getPosition().x, s.getPosition().y, s.getPosition().z, 4)) { clear(world, in, now, in.what + " towed clear of the line"); return; }   // rescue (#68)
            if (s == null || pr == null || pr.source != 4) clear(world, in, now, in.what + " has power again");
            return;
        }
        // wrecks still on the ground
        List<EntityMoveableRollingStock> wrecked = new ArrayList<>();
        for (UUID u : in.stock) { EntityMoveableRollingStock s = all.get(u); if (s != null && s.wreck != 0) wrecked.add(s); }
        if (wrecked.isEmpty()) {
            clear(world, in, now, in.phase == Phase.WORKING ? "the recovery crew cleared " + in.what : in.what + " is back on the rails");
            return;
        }
        long t = now - in.phaseAt;
        switch (in.phase) {
            case REPORTED:
                if (t >= 40) {
                    in.phase = Phase.CREW;
                    in.phaseAt = now;
                    alarm(world, "Recovery crew dispatched to incident #" + in.id + " (" + in.what + "), ETA " + com.dogpound.railmap.incident.IncidentHooks.crewSeconds(world, in, RailMapConfig.recoveryCrewSeconds) + " s", null);
                }
                break;
            case CREW:
                if (t >= com.dogpound.railmap.incident.IncidentHooks.crewSeconds(world, in, RailMapConfig.recoveryCrewSeconds) * 20L) {   // breakdown store distance + turnout (#68)
                    in.phase = Phase.WORKING;
                    in.phaseAt = now;
                    in.nextWork = now + com.dogpound.railmap.incident.IncidentHooks.workSeconds(world, in, RailMapConfig.recoveryWorkSeconds) * 20L;
                    alarm(world, "Recovery crew on site at incident #" + in.id + ": " + wrecked.size() + " car(s) to clear", null);
                }
                break;
            case WORKING: {
                EntityMoveableRollingStock s = wrecked.get(0);
                try {
                    cam72cam.immersiverailroading.wreck.Wrecks.crewEffects(s, s.wreck >= 2);
                    if (now >= in.nextWork) {
                        String did = cam72cam.immersiverailroading.wreck.Wrecks.crewStep(s);
                        if (did.equals("no_track")) {      // nowhere to put it back: it's a write-off
                            cam72cam.immersiverailroading.wreck.Wrecks.forceWreck(s, 3, "nowhere to rerail it");
                            did = cam72cam.immersiverailroading.wreck.Wrecks.crewStep(s);
                        }
                        if (!did.isEmpty() && !did.equals("moving")) alarm(world, "Incident #" + in.id + ": " + name(s) + " " + did, null);
                        in.nextWork = now + com.dogpound.railmap.incident.IncidentHooks.workSeconds(world, in, RailMapConfig.recoveryWorkSeconds) * 20L;
                    }
                } catch (LinkageError | RuntimeException e) {
                    in.nextWork = now + RailMapConfig.recoveryWorkSeconds * 20L;
                }
                break;
            }
            default:
                break;
        }
    }

    private static void clear(World world, Incident in, long now, String why) {
        in.phase = Phase.CLEAR;
        in.phaseAt = now;
        in.lastRelease = now - RailMapConfig.resumeGapSeconds * 20L;
        alarm(world, "§aLINE CLEAR (incident #" + in.id + "): " + why + (in.held.isEmpty() ? "" : " - restarting " + in.held.size() + " held train(s)"), null);
        version++;
    }

    /** the incident covers its cars plus a margin */
    private static void spread(Incident in, Map<UUID, EntityMoveableRollingStock> all) {
        double sx = 0, sy = 0, sz = 0;
        int n = 0;
        List<Vec3d> ps = new ArrayList<>();
        for (UUID u : in.stock) {
            EntityMoveableRollingStock s = all.get(u);
            if (s == null) continue;
            Vec3d p = s.getPosition();
            ps.add(p);
            sx += p.x; sy += p.y; sz += p.z; n++;
        }
        if (n == 0) return;
        in.x = sx / n; in.y = sy / n; in.z = sz / n;
        double r = 0;
        for (Vec3d p : ps) r = Math.max(r, Math.hypot(p.x - in.x, p.z - in.z));
        in.radius = r + 10;
    }

    @Nullable
    private static Incident find(List<Incident> list, UUID stock) {
        for (Incident in : list) if (in.stock.contains(stock)) return in;
        return null;
    }

    private static String name(EntityMoveableRollingStock s) {
        String n = s.getDefinition() == null ? "a car" : s.getDefinition().name();
        return s.tag != null && !s.tag.isEmpty() ? s.tag + " (" + n + ")" : n;
    }

    private static String state(int wreck) { return wreck >= 3 ? "wrecked" : wreck == 2 ? "on its side" : "derailed"; }

    private static void alarm(World world, String text, @Nullable Vec3d at) {
        ProgramData.get(world).alarm(world, text, at == null ? null : new BlockPos(at.x, at.y, at.z));
    }

    // ---- what the routing and the trains ask ------------------------------------------------

    /** the dimension the current route search runs in, and the train it's for (null = nobody: avoid every incident) */
    private static final ThreadLocal<Integer> SEARCH_DIM = new ThreadLocal<>();

    /** run a route search that avoids blocked line */
    public static <T> T avoiding(World world, java.util.function.Supplier<T> search) {
        if (!RailMapConfig.disruptionRecovery || incidents(world.provider.getDimension()).isEmpty()) return search.get();
        SEARCH_DIM.set(world.provider.getDimension());
        try {
            return search.get();
        } finally {
            SEARCH_DIM.remove();
        }
    }

    /** route search hook: is this piece of track blocked by an incident? (only inside avoiding(...)) */
    static boolean blocked(TileRail rail) {
        Integer dim = SEARCH_DIM.get();
        if (dim == null) return false;
        List<Incident> list = incidents(dim);
        if (list.isEmpty()) return false;
        double[] p = TrackFollower.worldPath(rail);
        for (Incident in : list) {
            if (!in.blocking()) continue;
            if (p == null) {
                cam72cam.mod.math.Vec3i v = rail.getPos();
                if (in.near(v.x + 0.5, v.y, v.z + 0.5, 0)) return true;
                continue;
            }
            for (int i = 0; i < p.length; i += 3) if (in.near(p[i], p[i + 1], p[i + 2], 0)) return true;
        }
        return false;
    }

    /** the first blocking incident on this route: {distance along the path to its edge, incident}, or null */
    @Nullable
    public static Object[] onPath(int dim, TrackPath path) {
        List<Incident> list = incidents(dim);
        if (list.isEmpty() || path.steps.isEmpty()) return null;
        for (TrackPath.Step st : path.steps) {
            double[] p = st.points;
            if (p == null) continue;
            for (Incident in : list) {
                if (!in.blocking()) continue;
                for (int i = 0; i < p.length; i += 3) {
                    if (!in.near(p[i], p[i + 1], p[i + 2], 0)) continue;
                    double d = path.distanceTo(p[i], p[i + 1], p[i + 2], 2);
                    return new Object[]{d < 0 ? st.distance : d, in};
                }
            }
        }
        return null;
    }

    /**
     * A driverless train whose route runs into an incident: it waits for it, queued in arrival order (the release order).
     * Returns the incident holding this train, or null when it may go.
     */
    @Nullable
    public static Incident holding(int dim, UUID train, @Nullable Incident onRoute) {
        for (Incident in : incidents(dim)) {
            if (in.held.contains(train)) return in.released.contains(train) ? null : in;
        }
        if (onRoute == null) return null;
        onRoute.held.add(train);
        return onRoute;
    }

    /** is this driverless train standing for an incident right now (boards show DELAYED)? */
    public static boolean isHeld(int dim, UUID train) {
        for (Incident in : incidents(dim)) if (in.held.contains(train) && !in.released.contains(train)) return true;
        return false;
    }

    /** status text for a held train */
    public static String why(Incident in) {
        return "Line blocked by " + (in.kind == Kind.WRECK ? "a wreck" : in.kind == Kind.HOLD ? "incident control" : "a failed train") + " (#" + in.id + ", " + in.what + "): " + in.phase.label;
    }

    /** incident command (#67): keep a stretch of line closed until released (fire, dangerous goods, evacuation, adjacent lines) */
    public static Incident hold(World world, String key, String what, double x, double y, double z, double radius) {
        if (!RailMapConfig.disruptionRecovery) return null;
        List<Incident> list = BY_DIM.computeIfAbsent(world.provider.getDimension(), k -> new ArrayList<>());
        for (Incident in : list) if (in.kind == Kind.HOLD && in.key.equals(key) && in.blocking()) return in;
        Incident in = new Incident(nextId++, Kind.HOLD, world.getTotalWorldTime());
        in.key = key; in.what = what; in.phase = Phase.WORKING;
        in.x = x; in.y = y; in.z = z; in.radius = radius;
        list.add(in);
        version++;
        return in;
    }

    /** incident command reopens a stretch it closed (held trains then restart one at a time) */
    public static void release(World world, String key, String why) {
        for (Incident in : incidents(world.provider.getDimension()))
            if (in.kind == Kind.HOLD && in.key.equals(key) && in.blocking()) clear(world, in, world.getTotalWorldTime(), why);
    }

    /** test / admin: clear an incident now */
    public static boolean forceClear(World world, int id) {
        for (Incident in : incidents(world.provider.getDimension())) {
            if (in.id == id && in.blocking()) {
                clear(world, in, world.getTotalWorldTime(), "cleared by hand");
                return true;
            }
        }
        return false;
    }
}
