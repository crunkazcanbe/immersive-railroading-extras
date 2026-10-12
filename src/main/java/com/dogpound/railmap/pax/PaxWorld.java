package com.dogpound.railmap.pax;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapConfig;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.economy.PassengerData;
import com.dogpound.railmap.incident.Announcer;
import com.dogpound.railmap.incident.IncidentConfig;
import com.dogpound.railmap.server.StationData;
import com.dogpound.railmap.station.Pis;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;

import java.util.*;

/**
 * Visible passengers: draws the people PassengerData only counts, at the stations players can see.
 * <ul>
 *   <li>a station near a player (viewDistance) gets a walk graph and as many people as it has passengers waiting, by
 *   destination, within the caps (per station, per player, per world); a station nobody is near is numbers only;</li>
 *   <li>new passengers come in at the entrances, buy a ticket at a machine (if there is one), go through the gates
 *   (they open for them) to a spot on the platform or a bench, look at the boards and wait;</li>
 *   <li>when the numbers for a destination drop while a train that serves it stands here, that many walk to its doors
 *   and step aboard once the doors are open (the counts moved with PassengerData's own boarding, so they always agree);
 *   when they drop with no train (overcrowding, abandoned connections), that many give up and walk out;</li>
 *   <li>people getting off a train come out of its doors (finishing here: to the exit; changing: to the platform);</li>
 *   <li>delays and cancellations (Announcer / PIS feed): notes in the tooltip, looking at the boards, back to the
 *   concourse, back again when the train is near; an evacuation sends everyone out until the all-clear.</li>
 * </ul>
 * Nothing here changes a number. The people are never saved; they are drawn again from the numbers.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class PaxWorld {
    private PaxWorld() {}

    static final Map<Integer, Map<Long, Site>> SITES = new HashMap<>();
    private static int buildsThisCycle;

    public static final class Door {
        final int node; final double x, y, z; final EntityRollingStock car;
        Door(int node, double x, double y, double z, EntityRollingStock car) { this.node = node; this.x = x; this.y = y; this.z = z; this.car = car; }
    }

    private static final class Doors { long at; final List<Door> list = new ArrayList<>(); }

    private static final class Pending {
        final UUID loco; final long notBefore, giveUp;
        Pending(UUID loco, long notBefore, long giveUp) { this.loco = loco; this.notBefore = notBefore; this.giveUp = giveUp; }
    }

    public static final class Site {
        public final long key;
        public String name = "";
        public PaxGraph g;
        long builtAt, retryAt, lastActive, lastDisrupt = -1_000_000;
        boolean dead, fresh = true, evac;
        public int budget;
        public final List<EntityPax> people = new ArrayList<>();
        final Set<Integer> taken = new HashSet<>();
        final Map<Long, Double> lastN = new HashMap<>();
        final Map<Long, Integer> doorChangers = new HashMap<>();
        UUID changerTrain;
        final ArrayDeque<Pending> pending = new ArrayDeque<>();
        final Map<UUID, Doors> doors = new HashMap<>();
        final Map<Long, Integer> status = new HashMap<>();        // dest -> 0 none, 1 ok, 2 delayed, 3 cancelled
        final Map<UUID, Long> dwellSeen = new HashMap<>();
        final Random rnd = new Random();
        long now;

        Site(long key) { this.key = key; }

        boolean disrupted(long dest) { return status.getOrDefault(dest, 0) >= 2; }

        void kill() {
            dead = true;
            for (EntityPax e : new ArrayList<>(people)) e.setDead();
            people.clear(); pending.clear(); taken.clear(); doors.clear();
            if (g != null) for (PaxGraph.Seat s : g.seats) s.taken = null;
        }

        void pickSpot(EntityPax e) {
            if (g.spots.length == 0) return;
            if (PaxConfig.benches && !e.wheelchair() && !e.kid() && rnd.nextInt(3) == 0 && !g.seats.isEmpty()) {
                for (int t = 0; t < 6; t++) {
                    PaxGraph.Seat s = g.seats.get(rnd.nextInt(g.seats.size()));
                    if (s.taken == null && s.onPlatform) { s.taken = e; e.seat = s; return; }
                }
            }
            for (int t = 0; t < 12; t++) {
                int i = g.spots[rnd.nextInt(g.spots.length)];
                if (taken.add(i)) { e.spot = i; return; }
            }
            e.spot = g.spots[rnd.nextInt(g.spots.length)];                      // a crowded platform: people share
        }

        void pickConcourse(EntityPax e) {
            if (g.concourse.length == 0) return;
            for (int t = 0; t < 8; t++) {
                int i = g.concourse[rnd.nextInt(g.concourse.length)];
                if (taken.add(i)) { e.spot = i; return; }
            }
            e.spot = g.concourse[rnd.nextInt(g.concourse.length)];
        }

        void gate(World w, BlockPos p) {
            TileEntity te = w.getTileEntity(p);
            if (te instanceof com.dogpound.railmap.block.TileTicketGate gt && gt.term().powered() && !gt.isOpen()) gt.open();
        }

        boolean dwellingHere(UUID loco) {
            Autopilot.Run r = loco == null ? null : Autopilot.run(loco);
            return r != null && r.dwelling && r.dwellStation == key;
        }

        /** the train's door positions met from the platform (refreshed every 2 s while it stands here) */
        List<Door> doors(World w, UUID loco) {
            Doors d = doors.get(loco);
            if (d != null && now - d.at < 40) return d.list;
            d = new Doors();
            d.at = now;
            doors.put(loco, d);
            if (g == null) return d.list;
            try {
                cam72cam.immersiverailroading.entity.Locomotive l = Autopilot.find(w, loco);
                if (l == null) return d.list;
                Set<Integer> used = new HashSet<>();
                for (EntityCoupleableRollingStock car : l.getTrain()) {
                    if (car.getDefinition() == null || !car.getDefinition().acceptsPassengers()) continue;
                    double len = car.getDefinition().getLength(car.gauge), wid = car.getDefinition().getWidth(car.gauge);
                    cam72cam.mod.math.Vec3d p = car.getPosition();
                    double yaw = Math.toRadians(car.getRotationYaw());
                    double ax = Math.sin(yaw), az = -Math.cos(yaw), lx = Math.cos(yaw), lz = Math.sin(yaw);
                    double[] along = len < 10 ? new double[]{0} : len < 18 ? new double[]{-(len / 2 - 1.6), len / 2 - 1.6} : new double[]{-(len / 2 - 1.8), 0, len / 2 - 1.8};
                    for (double o : along)
                        for (int side = -1; side <= 1; side += 2) {
                            double px = p.x + ax * o + lx * side * (wid / 2 + 0.6), pz = p.z + az * o + lz * side * (wid / 2 + 0.6);
                            int nd = g.nearest(px, p.y, pz, 2.6, true);
                            if (nd < 0 || !used.add(nd)) continue;
                            d.list.add(new Door(nd, p.x + ax * o + lx * side * (wid / 2 - 0.5), g.cy(nd), p.z + az * o + lz * side * (wid / 2 - 0.5), car));
                        }
                }
            } catch (RuntimeException | LinkageError ex) {
                RailMap.LOG.debug("[IR Extras] pax doors: {}", ex.toString());
            }
            return d.list;
        }

        /** the door a boarder is heading for, or null once the train has gone */
        Door door(World w, UUID loco, int node) {
            if (!dwellingHere(loco) || g == null) return null;
            List<Door> l = doors(w, loco);
            if (l.isEmpty()) return node >= 0 && node < g.n ? new Door(node, g.cx(node), g.cy(node), g.cz(node), null) : null;
            Door best = null; double bd = Double.MAX_VALUE;
            for (Door d : l) {
                if (d.node == node) return d;
                double dd = node < 0 || node >= g.n ? 0 : PaxGraph.sq(d.x - g.cx(node)) + PaxGraph.sq(d.z - g.cz(node));
                if (dd < bd) { bd = dd; best = d; }
            }
            return best;
        }
    }

    // ---- registration -------------------------------------------------------------------------------------------------
    @SubscribeEvent
    public static void registerEntities(RegistryEvent.Register<EntityEntry> e) {
        e.getRegistry().register(EntityEntryBuilder.create().entity(EntityPax.class).id(new ResourceLocation(RailMap.MODID, "passenger"), 7701)
                .name(RailMap.MODID + ".passenger").tracker(80, 2, false).build());
    }

    @SubscribeEvent
    public static void unload(WorldEvent.Unload e) {
        if (e.getWorld().isRemote) return;
        Map<Long, Site> m = SITES.remove(e.getWorld().provider.getDimension());
        if (m != null) for (Site s : m.values()) s.kill();
    }

    // ---- ticking ------------------------------------------------------------------------------------------------------
    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote) return;
        World w = e.world;
        long now = w.getTotalWorldTime();
        Map<Long, Site> sites = SITES.get(w.provider.getDimension());
        if (!PaxConfig.enabled || !RailMapConfig.railwayEconomy || PaxConfig.scale <= 0) {
            if (sites != null) { for (Site s : sites.values()) s.kill(); sites.clear(); }
            return;
        }
        try {
            if (sites != null && now % 4 == 0) for (Site s : sites.values()) { s.now = now; pending(w, s, now); }
            if (now % 20 == 7) manage(w, now);
        } catch (RuntimeException | LinkageError ex) {
            RailMap.LOG.warn("[IR Extras] visible passengers: {}", ex.toString());
        }
    }

    private static void manage(World w, long now) {
        int dim = w.provider.getDimension();
        Map<Long, Site> sites = SITES.computeIfAbsent(dim, k -> new HashMap<>());
        PassengerData pd = PassengerData.get(w);
        RailwayData rd = RailwayData.get(w);
        double r2 = (double) PaxConfig.viewDistance * PaxConfig.viewDistance;
        // budgets: each player shares maxPerPlayer over the stations near them, nearest first
        Map<Long, Integer> budget = new HashMap<>();
        for (EntityPlayer p : w.playerEntities) {
            if (p.isSpectator()) continue;
            List<Object[]> near = new ArrayList<>();
            for (PassengerData.Stn s : pd.stations()) {
                double d2 = p.getDistanceSq(BlockPos.fromLong(s.key));
                if (d2 <= r2) near.add(new Object[]{s, d2});
            }
            near.sort(Comparator.comparingDouble(o -> (Double) o[1]));
            int left = PaxConfig.maxPerPlayer;
            for (Object[] o : near) {
                PassengerData.Stn s = (PassengerData.Stn) o[0];
                int give = Math.min(left, Math.min(PaxConfig.maxPerStation, (int) Math.ceil(s.waitingTotal() * PaxConfig.scale)));
                left -= give;
                budget.merge(s.key, give, Math::max);
            }
        }
        int sum = 0;
        for (int b : budget.values()) sum += b;
        double cut = sum > PaxConfig.maxPerWorld ? PaxConfig.maxPerWorld / (double) sum : 1;
        for (Map.Entry<Long, Integer> b : budget.entrySet()) {
            Site s = sites.computeIfAbsent(b.getKey(), Site::new);
            s.lastActive = now;
            s.budget = (int) Math.floor(b.getValue() * cut);
        }
        for (Iterator<Site> it = sites.values().iterator(); it.hasNext(); ) {
            Site s = it.next();
            if (now - s.lastActive > 200 || pd.stn(s.key) == null) { s.kill(); it.remove(); }
        }
        buildsThisCycle = 0;
        int inWorld = 0;
        for (Site s : sites.values()) { s.people.removeIf(x -> x.isDead); inWorld += s.people.size(); }
        List<RailwayData.AutoTrain> trains = new ArrayList<>(rd.trains().values());
        for (Site s : sites.values()) {
            s.now = now;
            PassengerData.Stn stn = pd.stn(s.key);
            if (stn == null) continue;
            s.name = stn.name;
            if (!graph(w, s, now)) continue;
            List<RailwayData.AutoTrain> here = new ArrayList<>();
            for (RailwayData.AutoTrain a : trains) if (s.dwellingHere(a.loco)) { here.add(a); s.dwellSeen.putIfAbsent(a.loco, now); }
            s.dwellSeen.keySet().removeIf(u -> !s.dwellingHere(u));
            s.doors.keySet().removeIf(u -> !s.dwellingHere(u));
            if (!s.dwellingHere(s.changerTrain)) s.doorChangers.clear();
            if (now - s.lastDisrupt >= 100) { s.lastDisrupt = now; disruption(w, s, rd, now); }
            inWorld += reconcile(w, s, stn, rd, here, now, PaxConfig.maxPerWorld - inWorld);
        }
    }

    /** (re)build the walk graph, at most one station per second; false = no graph to walk on */
    private static boolean graph(World w, Site s, long now) {
        boolean stale = s.g != null && now - s.builtAt > PaxConfig.rebuildMinutes * 1200L;
        if ((s.g == null && now >= s.retryAt || stale) && buildsThisCycle == 0) {
            buildsThisCycle++;
            PaxGraph g = PaxGraph.scan(w, s.key, PaxConfig.graphRadius, PaxConfig.graphHeight);
            s.builtAt = now;
            if (g == null) { s.retryAt = now + 1200; if (s.g == null) return false; }
            else {
                if (s.g != null) {                                                  // re-anchor everyone on the new graph
                    s.taken.clear(); s.doors.clear();
                    for (EntityPax e : s.people) {
                        e.seat = null; e.spot = -1; e.node = -1;
                        if (e.state() == EntityPax.WAIT || e.state() == EntityPax.SIT) e.toPlatform();
                        if (e.state() == EntityPax.BOARD || e.state() == EntityPax.ALIGHT) e.doorNode = -1;
                    }
                }
                s.g = g;
            }
        }
        return s.g != null;
    }

    // ---- matching the numbers -----------------------------------------------------------------------------------------
    /** how many people to show for each destination: everyone, scaled down to fit the budget */
    static Map<Long, Integer> targets(Map<Long, Double> n, int budget, double scale) {
        double total = 0;
        for (double v : n.values()) total += v;
        double sc = total * scale <= budget ? scale : total <= 0 ? 0 : budget / total;
        Map<Long, Integer> t = new HashMap<>();
        for (Map.Entry<Long, Double> e : n.entrySet()) t.put(e.getKey(), (int) Math.floor(e.getValue() * sc + 0.5));
        return t;
    }

    /** does a train on `line` standing at `here` take you towards d (direct, or to a change onto another line)? */
    static boolean serves(List<Long> line, Collection<List<Long>> all, long here, long d) {
        if (line == null) return false;
        if (line.contains(d)) return true;
        for (List<Long> m : all) {
            if (m == line || !m.contains(d)) continue;
            for (long t : line) if (t != here && m.contains(t)) return true;
        }
        return false;
    }

    private static boolean serves(RailwayData rd, String line, long here, long d) {
        RailwayData.Line l = rd.line(line);
        if (l == null) return false;
        List<List<Long>> all = new ArrayList<>();
        for (RailwayData.Line m : rd.lines().values()) all.add(m.stations);
        return serves(l.stations, all, here, d);
    }

    private static int reconcile(World w, Site s, PassengerData.Stn stn, RailwayData rd, List<RailwayData.AutoTrain> here, long now, int worldLeft) {
        Map<Long, Double> n = new HashMap<>(stn.waiting);
        for (Map.Entry<Long, double[]> e : stn.changing.entrySet()) n.merge(e.getKey(), e.getValue()[0], Double::sum);
        Map<Long, Integer> target = targets(n, s.budget, PaxConfig.scale);
        Map<Long, Integer> cnt = new HashMap<>();
        Map<Long, List<EntityPax>> adults = new HashMap<>();
        for (EntityPax e : s.people) {
            if (e.isDead || !e.waitingType()) continue;
            cnt.merge(e.dest, 1, Integer::sum);
            if (!e.kid()) adults.computeIfAbsent(e.dest, k -> new ArrayList<>()).add(e);
        }
        Set<Long> dests = new HashSet<>(n.keySet());
        dests.addAll(cnt.keySet());
        int spawnLeft = s.fresh ? Integer.MAX_VALUE : Math.max(1, s.budget / 15), made = 0;
        for (long d : dests) {
            double nd = n.getOrDefault(d, 0.0);
            int t = target.getOrDefault(d, 0), v = cnt.getOrDefault(d, 0);
            double last = s.lastN.getOrDefault(d, nd);
            boolean dropped = nd < last - 0.2 || nd < 0.5;                     // they boarded / gave up in the numbers
            if (v > t && (dropped || v > t + 2)) {
                RailwayData.AutoTrain train = null;
                for (RailwayData.AutoTrain a : here) if (serves(rd, a.line, s.key, d)) { train = a; break; }
                List<EntityPax> l = adults.getOrDefault(d, Collections.emptyList());
                l.sort(Comparator.comparingInt(x -> x.state() == EntityPax.WAIT || x.state() == EntityPax.SIT ? 0 : 1));
                int surplus = v - t;
                for (EntityPax e : l) {
                    if (surplus <= 0) break;
                    if (train != null && dropped) board(w, s, e, train.loco);
                    else e.toExit(dropped ? "Gave up waiting" : "");                // (not dropped: the view budget shrank - they just wander off)
                    surplus -= 1 + e.kids.size();
                }
            } else if (v < t && !s.evac) {
                int deficit = t - v;
                while (deficit > 0 && spawnLeft > 0 && worldLeft - made > 0) {
                    int k = spawn(w, s, d, deficit, now);
                    if (k == 0) break;
                    deficit -= k; spawnLeft -= k; made += k;
                }
            }
        }
        s.lastN.clear();
        s.lastN.putAll(n);
        s.fresh = false;
        return made;
    }

    private static void board(World w, Site s, EntityPax e, UUID loco) {
        List<Door> l = s.doors(w, loco);
        if (l.isEmpty()) {
            int nd = s.g.nearest(e.posX, e.posY, e.posZ, 64, true);
            if (nd < 0) { e.setDead(); return; }
            e.toBoard(loco, nd, new double[]{s.g.cx(nd), s.g.cy(nd), s.g.cz(nd)});
            return;
        }
        Door a = null, b = null; double da = Double.MAX_VALUE, db = Double.MAX_VALUE;
        for (Door d : l) {
            double dd = PaxGraph.sq(d.x - e.posX) + PaxGraph.sq(d.z - e.posZ);
            if (dd < da) { b = a; db = da; a = d; da = dd; } else if (dd < db) { b = d; db = dd; }
        }
        Door pick = b != null && s.rnd.nextInt(3) == 0 ? b : a;                     // not everyone squeezes into the nearest door
        e.toBoard(loco, pick.node, new double[]{pick.x, pick.y, pick.z});
    }

    private static EntityPax make(World w, Site s, long dest, boolean kid, boolean chair) {
        Random r = s.rnd;
        EntityPax e = new EntityPax(w);
        e.site = s;
        e.dest = dest;
        int acc;
        if (kid) acc = r.nextInt(4) == 0 ? EntityPax.ACC_BACKPACK : EntityPax.ACC_NONE;
        else {
            int x = r.nextInt(100);
            acc = x < 35 ? EntityPax.ACC_NONE : x < 55 ? EntityPax.ACC_SUITCASE : x < 75 ? EntityPax.ACC_BACKPACK : x < 90 ? EntityPax.ACC_SHOULDER : EntityPax.ACC_BRIEFCASE;
        }
        e.setVariant(EntityPax.makeVariant(r.nextInt(EntityPax.SKINS), acc, kid, chair, r.nextBoolean(), r.nextInt(16)));
        e.setDestName(dest == s.key ? "Way out" : StationData.get(w).names().getOrDefault(dest, "?"));
        e.speed = chair ? 0.07 : 0.075 + r.nextDouble() * 0.03;
        e.ox = (r.nextDouble() - 0.5) * 0.5;
        e.oz = (r.nextDouble() - 0.5) * 0.5;
        return e;
    }

    /** a new waiting passenger (maybe a family): at the doors (changing here), on the platform (first sight), or an entrance */
    private static int spawn(World w, Site s, long dest, int deficit, long now) {
        PaxGraph g = s.g;
        Random r = s.rnd;
        boolean family = deficit >= 2 && r.nextDouble() < PaxConfig.families;
        EntityPax e = make(w, s, dest, false, !family && r.nextDouble() < PaxConfig.wheelchairs);
        Door door = null;
        int ch = s.doorChangers.getOrDefault(dest, 0);
        if (!s.fresh && ch > 0 && s.dwellingHere(s.changerTrain)) {
            List<Door> l = s.doors(w, s.changerTrain);
            if (!l.isEmpty()) door = l.get(r.nextInt(l.size()));
            s.doorChangers.put(dest, ch - 1);
        }
        if (s.fresh) {                                                         // first sight of the station: they're already waiting
            s.pickSpot(e);
            if (e.seat != null) { e.setPosition(e.seat.x, e.seat.y, e.seat.z); e.rotationYaw = e.seat.yaw; e.node = e.seat.node; e.setState(EntityPax.SIT); }
            else if (e.spot >= 0) { e.node = e.spot; e.setPosition(g.cx(e.spot) + e.ox, g.cy(e.spot), g.cz(e.spot) + e.oz); e.rotationYaw = g.faceYaw[e.spot] + (r.nextFloat() - 0.5f) * 40; e.setState(EntityPax.WAIT); }
            else return 0;
            e.since = Math.max(1, now - r.nextInt(2400));
        } else if (door != null) {
            e.setPosition(door.x, door.y, door.z);
            e.node = door.node;
            e.setState(EntityPax.TO_SPOT);
        } else {
            if (g.entrances.length == 0) return 0;
            int at = g.entrances[r.nextInt(g.entrances.length)];
            e.node = at;
            e.setPosition(g.cx(at) + e.ox, g.cy(at), g.cz(at) + e.oz);
            e.setState(!g.machines.isEmpty() && r.nextFloat() < 0.55f ? EntityPax.ARRIVE : EntityPax.TO_SPOT);
        }
        if (!w.isBlockLoaded(new BlockPos(e.posX, e.posY, e.posZ))) { e.release(); return 0; }
        w.spawnEntity(e);
        s.people.add(e);
        int made = 1;
        if (family) {
            int k = Math.min(deficit - 1, 1 + r.nextInt(2));
            for (int i = 0; i < k; i++) {
                EntityPax c = make(w, s, dest, true, false);
                c.parent = e;
                double a = r.nextDouble() * Math.PI * 2;
                c.ox = Math.cos(a) * 0.7; c.oz = Math.sin(a) * 0.7;
                c.setPosition(e.posX + c.ox, e.posY, e.posZ + c.oz);
                c.setState(EntityPax.FOLLOW);
                c.since = e.since;
                e.kids.add(c);
                w.spawnEntity(c);
                s.people.add(c);
                made++;
            }
        }
        return made;
    }

    // ---- getting off --------------------------------------------------------------------------------------------------
    /** PassengerData let people off a train here: off = final destination -> {passengers, fare}; st is "here" */
    public static void alighted(World w, UUID loco, long st, Map<Long, double[]> off) {
        try {
            if (!PaxConfig.enabled || off == null || w.isRemote) return;
            Map<Long, Site> m = SITES.get(w.provider.getDimension());
            Site s = m == null ? null : m.get(st);
            if (s == null || s.g == null || s.dead) return;
            long now = w.getTotalWorldTime();
            double out = 0;
            for (Map.Entry<Long, double[]> e : off.entrySet()) {
                if (e.getKey() == st) out += e.getValue()[0];
                else s.doorChangers.merge(e.getKey(), (int) Math.min(20, Math.round(e.getValue()[0] * PaxConfig.scale)), Integer::sum);
            }
            s.changerTrain = loco;
            int k = (int) Math.min(PaxConfig.alightMax, Math.round(out * PaxConfig.scale));
            for (int i = 0; i < k; i++) s.pending.add(new Pending(loco, now + i * 5L + s.rnd.nextInt(5), now + 400));
        } catch (RuntimeException | LinkageError ex) {
            RailMap.LOG.debug("[IR Extras] pax alight: {}", ex.toString());
        }
    }

    private static void pending(World w, Site s, long now) {
        if (s.pending.isEmpty()) return;
        if (s.g == null || s.evac) { s.pending.clear(); return; }
        int total = 0;
        for (Map<Long, Site> m : SITES.values()) for (Site x : m.values()) total += x.people.size();
        for (Iterator<Pending> it = s.pending.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (now < p.notBefore) continue;
            if (!s.dwellingHere(p.loco) || total >= PaxConfig.maxPerWorld) { it.remove(); continue; }   // the train left with them: fine, they're counted
            List<Door> l = s.doors(w, p.loco);
            Door d = l.isEmpty() ? null : l.get(s.rnd.nextInt(l.size()));
            if (d != null && !doorsOpen(d) && now < p.giveUp) continue;
            it.remove();
            EntityPax e = make(w, s, s.key, false, s.rnd.nextDouble() < PaxConfig.wheelchairs);
            if (d != null) { e.setPosition(d.x, d.y, d.z); e.node = d.node; e.doorNode = d.node; }
            else {
                int nd = s.g.edges.length == 0 ? -1 : s.g.edges[s.rnd.nextInt(s.g.edges.length)];
                if (nd < 0) continue;
                e.setPosition(s.g.cx(nd), s.g.cy(nd), s.g.cz(nd)); e.node = nd;
            }
            e.setState(EntityPax.ALIGHT);
            e.setNote("Arrived");
            w.spawnEntity(e);
            s.people.add(e);
            total++;
            break;                                                              // one at a time through the doors
        }
    }

    static final String PRIDE = "/pride_";

    /** a door people can use: pride cars must have that car's doors open; other cars have no door groups to wait for */
    static boolean doorsOpen(Door d) {
        if (d.car == null) return true;
        if (d.car.isDead()) return false;
        String id = d.car.getDefinitionID();
        return id == null || !id.contains(PRIDE) || com.dogpound.railmap.doors.TrainDoors.anyOpen(d.car);
    }

    // ---- disruption ---------------------------------------------------------------------------------------------------
    private static void disruption(World w, Site s, RailwayData rd, long now) {
        boolean evac = false;
        if (PaxConfig.reactions)
            try { for (Pis.Msg m : Pis.get(w).forStation(w, s.name)) if (m.kind == Pis.Kind.EVAC) { evac = true; break; } } catch (RuntimeException ignored) { }
        s.evac = evac;
        if (evac) {
            for (EntityPax e : s.people) if (!e.kid() && e.waitingType()) e.toExit("Evacuating the station");
            return;
        }
        List<Announcer.Due> due;
        try { due = Announcer.due(w, s.key, s.name); } catch (RuntimeException | LinkageError ex) { due = Collections.emptyList(); }
        Map<Long, List<EntityPax>> by = new HashMap<>();
        for (EntityPax e : s.people) if (!e.isDead && !e.kid() && e.waitingType()) by.computeIfAbsent(e.dest, k -> new ArrayList<>()).add(e);
        s.status.clear();
        for (Map.Entry<Long, List<EntityPax>> en : by.entrySet()) {
            long d = en.getKey();
            Announcer.Due best = null;
            for (Announcer.Due du : due) if (serves(rd, du.line, s.key, d)) { best = du; break; }
            String line = best == null || best.line.isEmpty() ? "Train" : best.line;
            int kind; String note;
            if (best == null) { kind = 0; note = "No train due yet"; }
            else if (best.dwelling) {
                kind = 1;
                Long seen = s.dwellSeen.get(best.loco);
                note = seen != null && now - seen > 140 ? "Train full - waiting for the next" : line + " at the platform";
            } else if (best.held && best.lateMin >= IncidentConfig.cancelMinutes) { kind = 3; note = line + " service cancelled"; }
            else if (best.lateMin >= IncidentConfig.announceDelayMinutes) { kind = 2; note = line + " running " + best.lateMin + " min late"; }
            else { kind = 1; note = line + (best.etaMin <= 0 ? " due now" : " due in " + best.etaMin + " min"); }
            s.status.put(d, kind);
            for (EntityPax e : en.getValue()) {
                long mins = e.since > 0 ? (now - e.since) * 60 / 1000 : 0;
                String nn = note + (mins >= 1 ? " · waited " + mins + " min" : "");
                e.setNote(nn);
                for (EntityPax k : e.kids) k.setNote(nn);
                if (!PaxConfig.reactions) continue;
                int st = e.state();
                if (kind == 3 && !e.reacted && (st == EntityPax.WAIT || st == EntityPax.SIT || st == EntityPax.TO_SPOT)) {
                    e.reacted = true;
                    if (s.rnd.nextDouble() < 0.6) e.toConcourse();
                } else if (kind == 2 && best.lateMin >= 10 && !e.reacted && (st == EntityPax.WAIT || st == EntityPax.SIT)) {
                    e.reacted = true;
                    if (s.rnd.nextDouble() < 0.3) e.toConcourse();
                } else if (kind == 1) {
                    e.reacted = false;
                    if (st == EntityPax.CONCOURSE && (best.dwelling || best.etaMin <= 3)) e.toPlatform();
                }
            }
        }
    }

    // ---- debug --------------------------------------------------------------------------------------------------------
    public static Map<Long, Site> sites(World w) { return SITES.getOrDefault(w.provider.getDimension(), Collections.emptyMap()); }

    public static void clear(World w) {
        Map<Long, Site> m = SITES.get(w.provider.getDimension());
        if (m == null) return;
        for (Site s : m.values()) s.kill();
        m.clear();
    }
}
