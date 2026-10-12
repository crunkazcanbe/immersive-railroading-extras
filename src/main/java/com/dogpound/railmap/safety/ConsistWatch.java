package com.dogpound.railmap.safety;

import cam72cam.immersiverailroading.entity.*;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.Interlocking;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.auto.TrackPath;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Train identification + tracking (her ChatGPT list #25) and train integrity monitoring (#20), from what the trains
 * really are: every 10 ticks the coupled consists are read straight off Immersive Railroading (who is coupled to whom),
 * matched to last time's records by their vehicles, and:
 * <ul>
 *   <li>each train keeps ONE record - headcode, radio id, locos, vehicles, axles, length, last position, speed,
 *   destination, the route it is on, its describer berth - for as long as any of its vehicles exist;</li>
 *   <li>a train whose vehicles turn up in two consists has divided. Moving when it happened = a coupler failure, the
 *   integrity is LOST (both halves), a critical alarm goes up, and signals / routes around both halves are blocked
 *   (fail-safe INTEGRITY) until the halves are coupled again or the dispatcher confirms after an inspection.
 *   Standing still = a planned split (shunting): the detached part just becomes a train of its own;</li>
 *   <li>a vehicle that vanishes from a moving train (wrecked, removed) is a MISSING WAGON - integrity LOST too.</li>
 * </ul>
 * Splits are detected from the consist membership changing, so any cause (a coupler break from the IR fork, a player
 * uncoupling, a wreck) is caught the same way. Rear-end confirmation (radio end-of-train) is RadioNet's job.
 */
public final class ConsistWatch {
    private ConsistWatch() {}

    /** where each tracked train's vehicles are (x, z), for holds and route checks */
    private static final Map<Integer, Map<String, List<double[]>>> CARS = new HashMap<>();
    /** each vehicle's last position: a vehicle that drops out of an unloaded chunk is not "missing" */
    private static final Map<UUID, double[]> LAST = new HashMap<>();

    public static void clear() { CARS.clear(); LAST.clear(); }

    public static Map<String, List<double[]>> cars(World w) { return CARS.getOrDefault(w.provider.getDimension(), Collections.emptyMap()); }

    private static final class Consist {
        final List<EntityCoupleableRollingStock> cars;
        EntityCoupleableRollingStock lead;
        final Set<UUID> ids = new HashSet<>();
        Consist(List<EntityCoupleableRollingStock> cars) { this.cars = cars; for (EntityCoupleableRollingStock c : cars) ids.add(c.getUUID()); }
        int size() { return cars.size(); }
    }

    public static void tick(World w) {
        try { pass(w); }
        catch (LinkageError | RuntimeException ex) { RailMap.LOG.warn("[irextras] train tracking pass failed: {}", ex.toString()); }
    }

    private static void pass(World mc) {
        cam72cam.mod.world.World w = cam72cam.mod.world.World.get(mc);
        long now = mc.getTotalWorldTime();
        SafetyData d = SafetyData.get(mc);
        List<Consist> consists = new ArrayList<>();
        Set<UUID> seen = new HashSet<>(), alive = new HashSet<>();
        for (EntityCoupleableRollingStock s : w.getEntities(EntityCoupleableRollingStock.class)) {
            if (s.isDead()) continue;
            alive.add(s.getUUID());
            if (seen.contains(s.getUUID())) continue;
            List<EntityCoupleableRollingStock> t = s.isCoupled() ? s.getTrain() : Collections.singletonList(s);
            Consist c = new Consist(new ArrayList<>(t));
            seen.addAll(c.ids);
            c.lead = leadOf(c.cars);
            consists.add(c);
        }
        // last pass: vehicle -> train
        Map<UUID, String> was = new HashMap<>();
        for (SafetyData.TrainRec r : d.trains.values()) if (!r.gone) for (UUID u : r.members) was.put(u, r.id);
        // biggest consists first, so the part with the lead keeps the identity when a train divides
        consists.sort((a, b) -> Integer.compare(b.size(), a.size()));
        Set<String> taken = new HashSet<>();
        Map<String, List<double[]>> cars = new HashMap<>();
        for (Consist c : consists) {
            Map<String, Integer> votes = new HashMap<>();
            for (UUID u : c.ids) { String id = was.get(u); if (id != null) votes.merge(id, c.lead != null && u.equals(c.lead.getUUID()) ? 1000 : 1, Integer::sum); }
            String id = null;
            int best = 0;
            for (Map.Entry<String, Integer> e : votes.entrySet()) if (!taken.contains(e.getKey()) && e.getValue() > best) { best = e.getValue(); id = e.getKey(); }
            SafetyData.TrainRec r;
            if (id == null) {
                r = create(mc, d, c, now);
                String from = null;
                for (Map.Entry<String, Integer> e : votes.entrySet()) if (from == null || e.getValue() > votes.get(from)) from = e.getKey();
                if (from != null) divided(mc, d, d.trains.get(from), r, c, now);
            } else r = d.trains.get(id);
            taken.add(r.id);
            // other trains whose vehicles are all in here now: they were coupled on (a rescue, re-coupling a divided train)
            for (String other : votes.keySet()) {
                if (other.equals(r.id) || taken.contains(other)) continue;
                SafetyData.TrainRec o = d.trains.get(other);
                if (o == null) continue;
                boolean all = true;
                for (UUID u : o.members) if (alive.contains(u) && !c.ids.contains(u)) { all = false; break; }
                if (!all) continue;
                o.gone = true;
                o.lastSeen = now;
                if (o.integrity == 2 || r.integrity == 2) restore(mc, d, r, "re-coupled to " + o.headcode);
                if (o.integrity == 2) restore(mc, d, o, "re-coupled to " + r.headcode);
                d.log(mc, "TRACKING", r.headcode, o.headcode + " coupled on (" + o.cars + " vehicles) - now one train", 2, 3);
            }
            // vehicles that were this train's and are nowhere now
            if (!r.members.isEmpty()) {
                int missing = 0;
                for (UUID u : r.members) {
                    double[] lp = LAST.get(u);
                    if (!alive.contains(u) && lp != null && mc.isBlockLoaded(new BlockPos(lp[0], lp[2], lp[1]))) missing++;
                }
                if (missing > 0 && Math.abs(r.speed) > 3 && r.integrity != 2)
                    lose(mc, d, r, missing + " vehicle(s) MISSING from the moving train", now);
            }
            update(mc, d, r, c, now);
            List<double[]> pos = new ArrayList<>();
            for (EntityCoupleableRollingStock s : c.cars) { Vec3d p = s.getPosition(); double[] a = {p.x, p.z, p.y}; pos.add(a); LAST.put(s.getUUID(), a); }
            cars.put(r.id, pos);
        }
        for (SafetyData.TrainRec r : d.trains.values()) if (!taken.contains(r.id) && !r.gone) { r.gone = true; r.lastSeen = now; }
        d.trains.values().removeIf(r -> r.gone && now - r.lastSeen > 20 * 600);   // forget a train 10 min after its last vehicle went
        LAST.keySet().retainAll(alive);
        CARS.put(mc.provider.getDimension(), cars);
        d.markDirty();
    }

    private static SafetyData.TrainRec create(World mc, SafetyData d, Consist c, long now) {
        SafetyData.TrainRec r = new SafetyData.TrainRec();
        r.id = "T" + d.nextTrain++;
        int serial = d.nextSerial++;
        r.radioId = 1000 + serial % 9000;
        r.firstSeen = now;
        update(mc, d, r, c, now);
        r.headcode = headcode(c, r, serial);
        d.trains.put(r.id, r);
        return r;
    }

    /** a UK-style headcode: class digit (what the train is) + letter (where it's going) + two digits */
    static String headcode(Consist c, SafetyData.TrainRec r, int serial) {
        boolean pax = false, freight = false;
        int locos = 0;
        for (EntityCoupleableRollingStock s : c.cars) {
            if (s instanceof Locomotive || s instanceof Tender) locos++;
            else if (s instanceof CarPassenger) pax = true;
            else if (s instanceof Freight || s instanceof FreightTank) freight = true;
        }
        char cls = locos == c.size() ? '0' : pax ? (r.driverless ? '2' : '1') : freight ? '6' : '5';
        String to = !r.dest.isEmpty() ? r.dest : r.line;
        char letter = 'Z';
        for (char ch : to.toUpperCase(Locale.ROOT).toCharArray()) if (ch >= 'A' && ch <= 'Z') { letter = ch; break; }
        return "" + cls + letter + String.format(Locale.ROOT, "%02d", serial % 100);
    }

    private static void update(World mc, SafetyData d, SafetyData.TrainRec r, Consist c, long now) {
        r.members.clear();
        r.members.addAll(c.ids);
        r.gone = false;
        r.lastSeen = now;
        r.cars = c.size();
        int ax = 0, locos = 0;
        double len = 0;
        StringBuilder names = new StringBuilder();
        for (EntityCoupleableRollingStock s : c.cars) {
            ax += AxleWatch.count(s);
            try { len += s.getDefinition().getLength(s.gauge); } catch (RuntimeException ignored) { }
            if (s instanceof Locomotive) {
                locos++;
                if (names.length() < 60) names.append(names.length() == 0 ? "" : " + ").append(s.tag != null && !s.tag.isEmpty() ? s.tag : s.getDefinition() == null ? "loco" : s.getDefinition().name());
            }
        }
        r.axles = ax;
        r.locos = locos;
        r.lengthM = len;
        r.names = names.length() == 0 ? (c.cars.isEmpty() || c.cars.get(0).getDefinition() == null ? "vehicles" : c.cars.get(0).getDefinition().name()) : names.toString();
        EntityCoupleableRollingStock lead = c.lead;
        r.lead = lead.getUUID();
        Vec3d p = lead.getPosition();
        r.x = p.x; r.y = p.y; r.z = p.z;
        EntityCoupleableRollingStock rear = lead;
        double far = -1;
        for (EntityCoupleableRollingStock s : c.cars) { double dd = s.getPosition().distanceToSquared(p); if (dd > far) { far = dd; rear = s; } }
        r.rear = rear.getUUID();
        r.rx = rear.getPosition().x; r.rz = rear.getPosition().z;
        r.speed = lead.getCurrentSpeed() != null ? (float) lead.getCurrentSpeed().metric() : 0;
        RailwayData.AutoTrain at = RailwayData.get(mc).train(lead.getUUID());
        r.driverless = at != null;
        r.line = at == null ? "" : at.line;
        Autopilot.Run run = at == null ? null : Autopilot.run(lead.getUUID());
        if (run != null && !run.nextStopName.isEmpty()) r.dest = run.nextStopName;
        else for (com.dogpound.railmap.graph.TrainNode n : com.dogpound.railmap.server.TrainTracker.latest(mc))   // driven by hand: the station it is heading for
            if (n.id == lead.getId()) { if (!n.heading.isEmpty()) r.dest = n.heading; break; }
        r.route = "";
        for (Interlocking.Route rt : Interlocking.routes(mc)) {
            if (Geo.onPath(rt.path, r.x, r.y, r.z, 3)) { r.route = at(rt.start) + " > " + at(rt.end); break; }
        }
        SignalSighting.Berth b = SignalSighting.berthOf(mc, r.x, r.y, r.z);
        r.berth = b == null ? "" : b.name;
        r.berthPos = b == null ? 0 : b.pos;
    }

    static String at(BlockPos p) {
        return Geo.signalName(p);
    }

    private static void divided(World mc, SafetyData d, SafetyData.TrainRec from, SafetyData.TrainRec part, Consist c, long now) {
        if (from == null) return;
        part.detachedFrom = from.headcode;
        boolean moving = Math.abs(from.speed) > 3 || Math.abs(part.speed) > 3;
        if (!moving) {
            d.log(mc, "TRACKING", from.headcode, "planned split while standing: " + part.cars + " vehicle(s) detached as " + part.headcode, 2, 3);
            return;
        }
        String why = "DIVIDED TRAIN " + from.headcode + ": " + part.cars + " vehicle(s) (" + part.headcode + ") parted at " + Math.round(part.x) + "," + Math.round(part.z)
                + " at " + Math.round(Math.abs(from.speed)) + " km/h";
        lose(mc, d, from, why, now);
        lose(mc, d, part, why, now);
    }

    static void lose(World mc, SafetyData d, SafetyData.TrainRec r, String why, long now) {
        r.integrity = 2;
        r.integrityWhy = why;
        r.integritySince = now;
        AlarmManager.get(mc).raise(mc, "integrity:" + r.id, AlarmManager.Cat.INCIDENT, AlarmManager.Sev.CRITICAL, why, new BlockPos(r.x, r.y, r.z), true);
        Recorder.event(mc, "INTEGRITY LOST " + r.headcode + ": " + why);
        d.log(mc, "INTEGRITY", r.headcode, why, 2, 3);
        tellRiders(mc, r, "§c⚠ TRAIN INTEGRITY LOST - " + why + ". Stop and examine the train.");
    }

    /** a dispatcher / driver confirmed the train is complete, or it was coupled together again */
    public static void restore(World mc, SafetyData d, SafetyData.TrainRec r, String how) {
        if (r.integrity == 0) return;
        r.integrity = 0;
        r.integrityWhy = "";
        r.integritySince = mc.getTotalWorldTime();
        AlarmManager.get(mc).clear("integrity:" + r.id);
        Recorder.event(mc, "INTEGRITY RESTORED " + r.headcode + ": " + how);
        d.log(mc, "INTEGRITY", r.headcode, "integrity restored - " + how, 2, 3);
        d.markDirty();
    }

    static EntityCoupleableRollingStock leadOf(List<EntityCoupleableRollingStock> train) {
        EntityCoupleableRollingStock best = null;
        for (EntityCoupleableRollingStock s : train) {
            boolean loco = s instanceof Locomotive, bestLoco = best instanceof Locomotive;
            if (best == null || loco && !bestLoco || loco == bestLoco && s.getId() < best.getId()) best = s;
        }
        return best;
    }

    /** the live loco entity of a record, or null */
    static Locomotive loco(World mc, SafetyData.TrainRec r) {
        if (r.lead == null) return null;
        for (Locomotive l : cam72cam.mod.world.World.get(mc).getEntities(Locomotive.class)) if (l.getUUID().equals(r.lead)) return l;
        return null;
    }

    /** every player riding anywhere in the train */
    static List<EntityPlayer> riders(World mc, SafetyData.TrainRec r) {
        List<EntityPlayer> out = new ArrayList<>();
        if (r.members.isEmpty()) return out;
        Set<UUID> m = new HashSet<>(r.members);
        for (EntityRollingStock s : cam72cam.mod.world.World.get(mc).getEntities(EntityRollingStock.class)) {
            if (!m.contains(s.getUUID())) continue;
            for (cam72cam.mod.entity.Entity e : s.getPassengers()) if (e.internal instanceof EntityPlayer p) out.add(p);
        }
        return out;
    }

    static void tellRiders(World mc, SafetyData.TrainRec r, String msg) {
        for (EntityPlayer p : riders(mc, r)) p.sendMessage(new net.minecraft.util.text.TextComponentString(msg));
    }

    static boolean onPath(TrackPath p, double x, double y, double z) { return Geo.onPath(p, x, y, z, 3); }
}
