package com.dogpound.railmap.economy;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.Disruptions;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.industry.IndustryKind;
import com.dogpound.railmap.industry.TileIndustry;
import com.dogpound.railmap.server.StationData;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.*;

/**
 * Passengers, simulated statistically (her ChatGPT list #10 + #48 + #50) - no entities, just numbers per station:
 * <ul>
 *   <li><b>Demand</b>: each named station on a line has a catchment (towns within 192 blocks by population, jobs at
 *   industries within 128, the station type). Every 5 s it generates passengers by time of day (morning and evening
 *   peaks, quiet nights) and by how good the service is; each picks a destination it can reach (direct, or with one
 *   change) by a gravity model (bigger and nearer places attract more).</li>
 *   <li><b>Riding</b>: a driverless train standing at a station lets off whoever is going there (or changing there)
 *   and takes on as many as fit (coaches seat by their length); the rest are left behind on the platform.</li>
 *   <li><b>Fares</b>: zone fares (or distance / flat), peak +30 %, night -20 %, one integrated fare for a trip with a
 *   change, shared between the operators of each leg by zones; fare evasion is lower where there are ticket gates.</li>
 *   <li><b>Satisfaction</b> per station and line: punctuality (train delays), crowding, waiting time (headway),
 *   facilities (boards, machines, gates, PA, shelters), cleanliness. It feeds back into demand. Trains 15+ minutes late
 *   refund half the fare of everyone aboard (the operator pays).</li>
 * </ul>
 */
public final class PassengerData extends WorldSavedData {
    private static final String NAME = "irextras_passengers";
    public static final String[] REASONS = {"late trains", "overcrowding", "long waits", "dirty station", "poor facilities", "left behind"};

    public static final class Stn {
        public final long key;
        public String name = "";
        /** destination -> passengers waiting for it (fractional: statistics) */
        public final Map<Long, Double> waiting = new HashMap<>();
        /** changing here: final destination -> {passengers, fare still owed to the next operator (cents)} */
        public final Map<Long, double[]> changing = new HashMap<>();
        public double sat = 70, punct = 85, crowd = 85, wait = 60, fac = 30, clean = 90;
        public int pop = 100, gates, facCount;
        public double boardedDay, alightedDay, leftDay, evadersDay, walkedDay, transfersDay, complaintsDay;
        public long revenueDay;
        public double boardedYesterday;
        public long lastArrival;
        public double headway;              // seconds, smoothed
        public final double[] hourly = new double[24];
        public final double[] reasons = new double[REASONS.length];
        public boolean staffed;
        Stn(long key) { this.key = key; }

        public double waitingTotal() { double s = 0; for (double v : waiting.values()) s += v; for (double[] v : changing.values()) s += v[0]; return s; }
        public String rating() { return sat >= 80 ? "EXCELLENT" : sat >= 65 ? "GOOD" : sat >= 50 ? "FAIR" : sat >= 35 ? "POOR" : "BAD"; }
        public String topReason() {
            int b = -1;
            for (int i = 0; i < reasons.length; i++) if (reasons[i] >= 0.5 && (b < 0 || reasons[i] > reasons[b])) b = i;
            return b < 0 ? "" : REASONS[b];
        }
    }

    public static final class LineStat {
        public final String name;
        public double sat = 70, punct = 85, load;
        public double ridersDay, ridersYesterday, delayedDay, complaintsDay, leftDay;
        public long revenueDay, revenueYesterday, refundsDay, costDay;   // costDay: the upkeep of the trains on it, charged at the day's start
        public int arrivalsDay;
        LineStat(String name) { this.name = name; }
    }

    private final Map<Long, Stn> stations = new LinkedHashMap<>();
    private final Map<String, LineStat> lines = new LinkedHashMap<>();
    /** train -> alight-at station -> final destination -> {passengers, fare owed to a later operator} */
    private final Map<UUID, Map<Long, Map<Long, double[]>>> onboard = new HashMap<>();
    private final Map<Long, Integer> zones = new HashMap<>();
    // live, not saved
    private final Map<UUID, Long> lastDwell = new HashMap<>();
    private final Map<UUID, int[]> capacity = new HashMap<>();   // seats, cars, tick checked
    private long lastDay = -1, lastCatch = -1_000_000;

    // ---- fares (world-wide scheme, set with /irextras fares) ------------------------------------------------------
    public String scheme = "ZONES";                 // ZONES, DISTANCE, FLAT
    public long zoneBase = 150, zoneStep = 90, flat = 250, perBlock = 1;
    public int peakPct = 30;

    public PassengerData() { super(NAME); }
    public PassengerData(String n) { super(n); }

    public static PassengerData get(World w) {
        MapStorage s = w.getPerWorldStorage();
        PassengerData d = (PassengerData) s.getOrLoadData(PassengerData.class, NAME);
        if (d == null) { d = new PassengerData(); s.setData(NAME, d); }
        return d;
    }

    public Stn stn(long key) { return stations.get(key); }
    public Collection<Stn> stations() { return stations.values(); }
    public LineStat line(String n) { return lines.get(n); }
    public Collection<LineStat> lines() { return lines.values(); }
    public int cars(UUID loco) { int[] c = capacity.get(loco); return c == null ? 0 : c[1]; }
    public int seats(UUID loco) { int[] c = capacity.get(loco); return c == null ? 0 : c[0]; }
    public double aboard(UUID loco) {
        double s = 0;
        Map<Long, Map<Long, double[]>> m = onboard.get(loco);
        if (m != null) for (Map<Long, double[]> x : m.values()) for (double[] v : x.values()) s += v[0];
        return s;
    }

    public int zoneOf(long station) { return zones.getOrDefault(station, 1); }
    public void setZone(long station, int z) { if (zoneOf(station) != z || !zones.containsKey(station)) { zones.put(station, Math.max(1, Math.min(9, z))); markDirty(); } }

    public static int hour(World w) { return (int) (((w.getWorldTime() + 6000) % 24000) / 1000); }
    public static boolean peak(int h) { return h >= 7 && h < 9 || h >= 17 && h < 19; }
    public static boolean night(int h) { return h >= 22 || h < 5; }

    public int zonesBetween(long a, long b) { return Math.abs(zoneOf(a) - zoneOf(b)) + 1; }

    /** one adult single fare in cents, at this hour */
    public long fare(World w, long from, long to) {
        long f = switch (scheme) {
            case "FLAT" -> flat;
            case "DISTANCE" -> Math.max(zoneBase / 2, Math.round(Math.sqrt(BlockPos.fromLong(from).distanceSq(BlockPos.fromLong(to))) * perBlock) + 50);
            default -> zoneBase + zoneStep * (zonesBetween(from, to) - 1);
        };
        int h = hour(w);
        if (peak(h)) f = f * (100 + peakPct) / 100;
        else if (night(h)) f = f * 80 / 100;
        return f;
    }

    public static final String[] CONCESSIONS = {"ADULT", "CHILD", "SENIOR"};
    public static long concession(long cents, int c) { return c == 1 ? cents / 2 : c == 2 ? cents * 40 / 100 : cents; }

    public static final String[] PASSES = {"DAY", "7-DAY", "28-DAY"};
    public static final int[] PASS_DAYS = {1, 7, 28};
    /** a travel pass covering zones lo..hi (off-peak single x 2.5 per day, cheaper the longer it runs) */
    public long passPrice(int type, int lo, int hi, int conc) {
        long single = zoneBase + zoneStep * (Math.abs(hi - lo));
        long day = single * 5 / 2;
        long p = type == 0 ? day : type == 1 ? day * 5 : day * 16;
        return concession(p, conc);
    }

    // ---- the simulation -------------------------------------------------------------------------------------------
    public void tick(World w) {
        long now = w.getTotalWorldTime();
        StationData sd = StationData.get(w);
        RailwayData rd = RailwayData.get(w);
        // the stations on lines
        Set<Long> onLines = new HashSet<>();
        for (RailwayData.Line l : rd.lines().values()) onLines.addAll(l.stations);
        for (Map.Entry<Long, String> e : sd.names().entrySet()) {
            Stn s = stations.computeIfAbsent(e.getKey(), Stn::new);
            s.name = e.getValue();
        }
        stations.keySet().removeIf(k -> !sd.names().containsKey(k) && stations.get(k).waitingTotal() < 1);
        for (RailwayData.Line l : rd.lines().values()) lines.computeIfAbsent(l.name, LineStat::new);
        lines.keySet().removeIf(n -> !rd.lines().containsKey(n));
        if (now - lastCatch >= 1200) { lastCatch = now; catchments(w); }
        generate(w, rd, onLines);
        trains(w, rd, now);
        scores(now);
        long day = now / CompanyData.DAY;
        if (lastDay < 0) lastDay = day;
        if (day != lastDay) {
            lastDay = day;
            for (Stn s : stations.values()) {
                s.boardedYesterday = s.boardedDay;
                s.boardedDay = s.alightedDay = s.leftDay = s.evadersDay = s.walkedDay = s.transfersDay = s.complaintsDay = 0;
                s.revenueDay = 0;
                Arrays.fill(s.hourly, 0);
                Arrays.fill(s.reasons, 0);
            }
            for (LineStat l : lines.values()) {
                l.ridersYesterday = l.ridersDay; l.revenueYesterday = l.revenueDay;
                l.ridersDay = l.delayedDay = l.complaintsDay = l.leftDay = 0; l.revenueDay = l.refundsDay = l.costDay = 0; l.arrivalsDay = 0;
            }
        }
        markDirty();
    }

    /** catchment population and facilities, from what is built around each station */
    private void catchments(World w) {
        List<TileIndustry> inds = new ArrayList<>();
        Set<Long> loaded = TileIndustry.LOADED.get(w.provider.getDimension());
        if (loaded != null) for (long l : loaded) { BlockPos p = BlockPos.fromLong(l); if (w.isBlockLoaded(p) && w.getTileEntity(p) instanceof TileIndustry t) inds.add(t); }
        for (Stn s : stations.values()) {
            BlockPos at = BlockPos.fromLong(s.key);
            if (!com.dogpound.railmap.admin.Sched.due(w, "catchment", s.key, at.getX(), at.getZ(), 1200)) continue;   // #120 load-aware: quiet stations keep last catchment
            double pop = 80;
            if (com.dogpound.railmap.biz.BizConfig.enabled) pop += com.dogpound.railmap.biz.BizData.get(w).towns.catchment(w, at);   // #13: hall towns in the catchment
            for (TileIndustry t : inds) {
                double d = Math.sqrt(t.getPos().distanceSq(at));
                if (t.kind() == IndustryKind.TOWN_MARKET && d < 192) pop += t.population() * (1 - d / 240);
                else if (d < 128) pop += 30;                                  // jobs
            }
            com.dogpound.railmap.station.TileStationMaster desk = com.dogpound.railmap.station.TileStationMaster.at(w, at);
            String type = desk == null ? "" : desk.type();
            pop *= type.startsWith("Metropolitan") ? 3.0 : type.startsWith("Multi") ? 2.0 : type.startsWith("Underground") || type.startsWith("Elevated") ? 1.5
                    : type.startsWith("Rural") ? 0.4 : type.startsWith("Freight") || type.startsWith("Industrial") ? 0.15 : 1.0;
            s.pop = (int) Math.round(pop);
            s.staffed = desk != null && desk.term().powered();
            int f = 0, gates = 0, n = 0;
            for (TileEntity te : w.loadedTileEntityList) {
                if (te.getPos().distanceSq(at) > 48 * 48) continue;
                if (te instanceof com.dogpound.railmap.block.TileArrivalsBoard) f += 15;
                else if (te instanceof com.dogpound.railmap.block.TileTicketMachine) f += 15;
                else if (te instanceof com.dogpound.railmap.block.TileTicketGate) { gates++; f += 4; }
                else if (te instanceof com.dogpound.railmap.block.TilePaSpeaker) f += 8;
                else if (te instanceof com.dogpound.railmap.block.TileTravelCentre) f += 20;
                else if (te instanceof com.dogpound.railmap.station.TileStationMaster) f += 10;
                else if (te instanceof com.dogpound.railmap.station.TileStationPiece) { if (n++ < 15) f += 2; }
                else if (te instanceof com.dogpound.railmap.station.IStationFacility) f += com.dogpound.railmap.station.StationFacilities.points(te);   // lifts, toilets, kiosks... (her list #85)
                else continue;
            }
            s.fac = Math.min(100, f);
            s.gates = gates;
            s.facCount = f;
        }
    }

    private static double tod(int h) {
        if (h >= 7 && h < 9) return 2.2;
        if (h >= 17 && h < 19) return 2.0;
        if (h >= 9 && h < 17) return 1.0;
        if (h >= 19 && h < 22) return 0.7;
        if (h >= 5 && h < 7) return 0.6;
        return 0.15;
    }

    /** which stations a trip from s can reach: direct (true) or with one change (false) */
    private static Map<Long, Boolean> reach(RailwayData rd, long s) {
        Map<Long, Boolean> out = new HashMap<>();
        List<RailwayData.Line> mine = new ArrayList<>();
        for (RailwayData.Line l : rd.lines().values()) if (l.stations.contains(s)) { mine.add(l); for (long d : l.stations) out.put(d, true); }
        for (RailwayData.Line l : mine)
            for (RailwayData.Line m : rd.lines().values()) {
                if (m == l || Collections.disjoint(l.stations, m.stations)) continue;
                for (long d : m.stations) out.putIfAbsent(d, false);
            }
        out.remove(s);
        return out;
    }

    private void generate(World w, RailwayData rd, Set<Long> onLines) {
        double t = tod(hour(w)) * com.dogpound.railmap.RailMapConfig.passengerDemand;
        for (Stn s : stations.values()) {
            if (!onLines.contains(s.key)) continue;
            Map<Long, Boolean> dests = reach(rd, s.key);
            dests.keySet().removeIf(d -> !stations.containsKey(d));
            if (dests.isEmpty()) continue;
            double rate = s.pop * 0.0012 * t * (0.5 + s.sat / 100.0) * com.dogpound.railmap.station.PlatformOps.inflow(w, s.key);   // crowd metering / closed station (batch 3 #27 #33)
            double sum = 0;
            Map<Long, Double> wt = new HashMap<>();
            BlockPos at = BlockPos.fromLong(s.key);
            for (long d : dests.keySet()) {
                double dist = Math.max(32, Math.sqrt(BlockPos.fromLong(d).distanceSq(at)));
                double v = stations.get(d).pop / Math.pow(dist, 0.7) * (dests.get(d) ? 1.0 : 0.6);   // a change puts people off
                v *= com.dogpound.railmap.station.StationFacilities.accessWeight(w, s.key, d);   // step-free travellers avoid stations they can't use (her list #81)
                wt.put(d, v);
                sum += v;
            }
            if (sum <= 0) continue;
            for (Map.Entry<Long, Double> e : wt.entrySet()) s.waiting.merge(e.getKey(), rate * e.getValue() / sum, Double::sum);
            double cap = 250 + s.fac * 4, tot = s.waitingTotal();
            if (tot > cap) {                                                  // the platform is full: people give up and walk away
                double keep = cap / tot, gone = tot - cap;
                s.waiting.replaceAll((k, v) -> v * keep);
                s.walkedDay += gone;
                s.reasons[2] += gone * 0.05;
                s.complaintsDay += gone * 0.05;
            }
        }
    }

    private void trains(World w, RailwayData rd, long now) {
        for (RailwayData.AutoTrain a : rd.trains().values()) {
            Autopilot.Run r = Autopilot.run(a.loco);
            if (r == null || !r.dwelling) { lastDwell.remove(a.loco); continue; }
            if (Objects.equals(lastDwell.get(a.loco), r.dwellStation)) continue;
            lastDwell.put(a.loco, r.dwellStation);
            arrive(w, rd, a, r.dwellStation, now);
        }
        onboard.keySet().removeIf(u -> !rd.trains().containsKey(u));
    }

    private int[] cap(World w, UUID loco, long now) {
        int[] c = capacity.get(loco);
        if (c != null && now - c[2] < 2400) return c;
        int seats = 0, cars = 0;
        try {
            cam72cam.immersiverailroading.entity.Locomotive l = Autopilot.find(w, loco);
            if (l != null) {
                for (cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock s : l.getTrain()) {
                    cars++;
                    cam72cam.immersiverailroading.registry.EntityRollingStockDefinition def = s.getDefinition();
                    if (def != null && def.acceptsPassengers())
                        seats += Math.max(def.getMaxPassengers(), (int) Math.round(def.getLength(s.gauge) * 3));   // ~3 a metre, seated + standing
                }
            } else if (c != null) return c;
        } catch (LinkageError | RuntimeException e) { if (c != null) return c; }
        c = new int[]{seats, cars, (int) now};
        capacity.put(loco, c);
        return c;
    }

    private void arrive(World w, RailwayData rd, RailwayData.AutoTrain a, long st, long now) {
        Stn s = stations.get(st);
        if (s == null) return;
        LineStat ls = lines.get(a.line);
        RailwayData.Line line = rd.line(a.line);
        Company op = CompanyData.get(w).ownerOfTrain(a.loco, a.line);
        // punctuality and headway
        int delay = Disruptions.delaySeconds(a.loco);
        double p = delay < 120 ? 100 : delay < 300 ? 65 : delay < 600 ? 35 : 5;
        s.punct += (p - s.punct) * 0.15;
        if (ls != null) { ls.punct += (p - ls.punct) * 0.15; ls.arrivalsDay++; if (delay >= 300) ls.delayedDay++; }
        if (delay >= 300) s.reasons[0] += 1;
        if (s.lastArrival > 0) {
            double gap = (now - s.lastArrival) / 20.0;
            s.headway = s.headway <= 0 ? gap : s.headway * 0.7 + gap * 0.3;
        }
        s.lastArrival = now;
        // off
        Map<Long, Map<Long, double[]>> on = onboard.computeIfAbsent(a.loco, k -> new HashMap<>());
        Map<Long, double[]> off = on.remove(st);
        double alight = 0;
        long fareSum = 0;
        if (off != null)
            for (Map.Entry<Long, double[]> e : off.entrySet()) {
                double n = e.getValue()[0];
                alight += n;
                if (e.getKey() == st) { com.dogpound.railmap.biz.BizData.get(w).journeys.arrived(w, a.loco, st, n, now); continue; }   // #14 journeys
                double[] c = s.changing.computeIfAbsent(e.getKey(), k -> new double[2]);
                c[0] += n; c[1] += e.getValue()[1];
                s.transfersDay += n;
                com.dogpound.railmap.biz.BizData.get(w).journeys.transferIn(w, st, e.getKey(), n, c, now, a.line);   // #14: queued at the interchange (step-free reroute / abandon)
            }
        s.alightedDay += alight;
        if (off != null) com.dogpound.railmap.pax.PaxWorld.alighted(w, a.loco, st, off);   // visible passengers step off at the doors (numbers unchanged)
        if (delay >= 900 && alight >= 1 && op != null) {                          // delay repay: half the fare back
            long refund = Math.round(alight * fare(w, st, st) / 2.0);
            if (!CompanyData.get(w).debit(w, op, Company.Kind.REFUNDS, refund, "Delay repay at " + s.name + " (" + delay / 60 + " min late)", false))
                op.reputation = Math.max(0, op.reputation - 1);
            if (ls != null) ls.refundsDay += refund;
            s.complaintsDay += alight * 0.1;
        }
        // on
        int[] cap = cap(w, a.loco, now);
        if (cap[0] <= 0 || line == null) return;
        double aboard = aboard(a.loco), free = Math.max(0, cap[0] - aboard), boarded = 0, left = 0;
        double evade = (s.gates > 0 ? 0.03 : 0.15) * com.dogpound.railmap.crew.Revenue.deterrent(w, s.key);   // batch 5 #9: inspectors deter fare dodging
        List<Long> stops = line.stations;
        for (int pass = 0; pass < 2; pass++) {                                   // people changing here first, then fresh trips
            Iterator<Map.Entry<Long, ?>> it = pass == 0 ? (Iterator) s.changing.entrySet().iterator() : (Iterator) s.waiting.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Long, ?> e = it.next();
                long d = e.getKey();
                double want = pass == 0 ? ((double[]) e.getValue())[0] : (Double) e.getValue();
                long via = stops.contains(d) ? d : changeFor(rd, line, st, d);
                if (via == Long.MIN_VALUE || want < 0.01) continue;
                double take = Math.min(want, free);
                if (take < want) left += want - take;
                if (take <= 0) continue;
                free -= take;
                boarded += take;
                double owed;
                if (pass == 0) {                                                   // already paid at the start: this operator's share
                    double[] c = (double[]) e.getValue();
                    double share = c[1] * take / Math.max(0.0001, c[0]);
                    owed = via == d ? 0 : share * zonesBetween(via, d) / (double) (zonesBetween(st, via) + zonesBetween(via, d));
                    fareSum += Math.round((share - owed) * (1 - evade));
                    c[0] -= take; c[1] -= share;
                    com.dogpound.railmap.biz.BizData.get(w).journeys.transferOut(w, st, d, take, now, a.line, a.loco);   // #14: connection made / missed
                    if (c[0] < 0.01) it.remove();
                } else {
                    long through = fare(w, st, d);                                 // one integrated fare for the whole trip
                    double tot = through * take;
                    owed = via == d ? 0 : tot * zonesBetween(via, d) / (double) (zonesBetween(st, via) + zonesBetween(via, d));
                    fareSum += Math.round((tot - owed) * (1 - evade));
                    com.dogpound.railmap.biz.BizData.get(w).journeys.board(w, a.loco, st, d, take, now, a.line);   // #14: sample journeys
                    double rest = want - take;
                    if (rest < 0.01) it.remove(); else ((Map.Entry<Long, Double>) e).setValue(rest);
                }
                double[] slot = on.computeIfAbsent(via, k -> new HashMap<>()).computeIfAbsent(d, k -> new double[2]);
                slot[0] += take;
                slot[1] += owed * (1 - evade);
            }
        }
        s.boardedDay += boarded;
        s.hourly[hour(w)] += boarded;
        s.evadersDay += boarded * evade;
        s.leftDay += left;
        s.revenueDay += fareSum;
        s.clean = Math.max(0, s.clean - boarded * 0.03);
        if (left > 0) { s.reasons[5] += left * 0.05; s.complaintsDay += left * 0.05; }
        if (op != null && fareSum > 0) CompanyData.get(w).credit(w, op, Company.Kind.FARES, fareSum, "Fares at " + s.name + " (" + Math.round(boarded) + " aboard)");
        double load = cap[0] <= 0 ? 0 : (cap[0] - free) / cap[0];
        double cr = load < 0.6 ? 100 : load < 0.85 ? 80 : load < 1.0 ? 55 : left > 0 ? 15 : 35;
        s.crowd += (cr - s.crowd) * 0.15;
        if (cr < 50) s.reasons[1] += boarded * 0.02;
        if (ls != null) {
            ls.ridersDay += boarded; ls.revenueDay += fareSum; ls.leftDay += left;
            ls.load = ls.load * 0.8 + load * 100 * 0.2;
            ls.complaintsDay += left * 0.05;
            String key = "pax:full:" + ls.name;
            if (ls.leftDay > 100) AlarmManager.get(w).raise(w, key, AlarmManager.Cat.GENERAL, AlarmManager.Sev.INFO,
                    ls.name + " overcrowded: " + Math.round(ls.leftDay) + " passengers left behind today - add trains", null, true);
            else AlarmManager.get(w).clear(key);
        }
    }

    /** a station on this train's line where the trip to d can change onto another line (not here) */
    private static long changeFor(RailwayData rd, RailwayData.Line line, long here, long d) {
        for (RailwayData.Line m : rd.lines().values()) {
            if (m == line || !m.stations.contains(d)) continue;
            for (long t : line.stations) if (t != here && m.stations.contains(t)) return t;
        }
        return Long.MIN_VALUE;
    }

    private void scores(long now) {
        for (Stn s : stations.values()) {
            double hw = s.headway;
            if (s.lastArrival > 0 && (now - s.lastArrival) / 20.0 > Math.max(hw, 1) * 1.5) hw = (now - s.lastArrival) / 20.0;   // nothing has come for a while
            double avgWait = hw <= 0 ? 1800 : hw / 2;
            double ws = avgWait < 120 ? 100 : avgWait < 300 ? 80 : avgWait < 600 ? 60 : avgWait < 1200 ? 40 : 20;
            s.wait += (ws - s.wait) * 0.05;
            if (s.staffed) s.clean = Math.min(100, s.clean + 0.2);             // the station master's staff sweep up
            double target = 0.30 * s.punct + 0.20 * s.crowd + 0.20 * s.wait + 0.15 * s.fac + 0.15 * s.clean;
            s.sat += (target - s.sat) * 0.03;
            if (s.wait < 50) s.reasons[2] += 0.01;
            if (s.clean < 50) s.reasons[3] += 0.01;
            if (s.fac < 30) s.reasons[4] += 0.005;
            if (s.sat < 45) s.complaintsDay += (45 - s.sat) / 300.0;
        }
        for (LineStat l : lines.values()) {
            double t = 0.5 * l.punct + 0.5 * (l.load < 60 ? 100 : l.load < 85 ? 80 : l.load < 100 ? 55 : 30);
            l.sat += (t - l.sat) * 0.05;
        }
    }

    public void clean(long station) { Stn s = stations.get(station); if (s != null) { s.clean = 100; s.reasons[3] = 0; markDirty(); } }

    /** average satisfaction over a company's stations and lines, or -1 */
    public double companySatisfaction(World w, Company c) {
        double sum = 0; int n = 0;
        for (long k : c.stations) { Stn s = stations.get(k); if (s != null) { sum += s.sat; n++; } }
        for (String l : c.lines) { LineStat x = lines.get(l); if (x != null) { sum += x.sat; n++; } }
        return n == 0 ? -1 : sum / n;
    }

    /** a ticket machine sold a ticket (paid in items): its operators get the items' market value */
    public void ticketSold(World w, long station, long dest, int items, net.minecraft.item.Item item) {
        Stn s = stations.get(station);
        long cents = item == null ? 0 : items * MarketData.get(w).of(new net.minecraft.item.ItemStack(item)).price;
        if (s != null) { s.revenueDay += cents; markDirty(); }
        CompanyData cd = CompanyData.get(w);
        cd.creditShared(w, cd.operatorsAt(w, station), Company.Kind.FARES, cents, "Ticket machine at " + (s == null ? "?" : s.name));
    }

    // ---- save -----------------------------------------------------------------------------------------------------
    @Override
    public void readFromNBT(NBTTagCompound t) {
        stations.clear(); lines.clear(); onboard.clear(); zones.clear();
        NBTTagList l = t.getTagList("st", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            Stn s = new Stn(c.getLong("k"));
            s.name = c.getString("n");
            NBTTagCompound wt = c.getCompoundTag("w");
            for (String k : wt.getKeySet()) s.waiting.put(Long.parseLong(k), wt.getDouble(k));
            NBTTagCompound ch = c.getCompoundTag("ch");
            for (String k : ch.getKeySet()) { NBTTagCompound x = ch.getCompoundTag(k); s.changing.put(Long.parseLong(k), new double[]{x.getDouble("n"), x.getDouble("o")}); }
            s.sat = c.getDouble("sat"); s.punct = c.getDouble("pu"); s.crowd = c.getDouble("cr"); s.wait = c.getDouble("wa"); s.fac = c.getDouble("fa"); s.clean = c.getDouble("cl");
            s.pop = c.getInteger("pop"); s.boardedDay = c.getDouble("bd"); s.alightedDay = c.getDouble("ad"); s.leftDay = c.getDouble("ld");
            s.evadersDay = c.getDouble("ev"); s.walkedDay = c.getDouble("wk"); s.transfersDay = c.getDouble("tr"); s.complaintsDay = c.getDouble("co");
            s.revenueDay = c.getLong("rev"); s.boardedYesterday = c.getDouble("by"); s.lastArrival = c.getLong("la"); s.headway = c.getDouble("hw");
            stations.put(s.key, s);
        }
        l = t.getTagList("ln", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            LineStat x = new LineStat(c.getString("n"));
            x.sat = c.getDouble("sat"); x.punct = c.getDouble("pu"); x.load = c.getDouble("lo");
            x.ridersDay = c.getDouble("rd"); x.ridersYesterday = c.getDouble("ry"); x.revenueDay = c.getLong("rv"); x.revenueYesterday = c.getLong("rvy");
            x.refundsDay = c.getLong("rf"); x.costDay = c.getLong("cd"); x.delayedDay = c.getDouble("dl"); x.complaintsDay = c.getDouble("co"); x.leftDay = c.getDouble("lf");
            lines.put(x.name, x);
        }
        NBTTagCompound ob = t.getCompoundTag("on");
        for (String tr : ob.getKeySet()) {
            Map<Long, Map<Long, double[]>> m = new HashMap<>();
            NBTTagCompound a = ob.getCompoundTag(tr);
            for (String at : a.getKeySet()) {
                Map<Long, double[]> mm = new HashMap<>();
                NBTTagCompound b = a.getCompoundTag(at);
                for (String d : b.getKeySet()) { NBTTagCompound x = b.getCompoundTag(d); mm.put(Long.parseLong(d), new double[]{x.getDouble("n"), x.getDouble("o")}); }
                m.put(Long.parseLong(at), mm);
            }
            try { onboard.put(UUID.fromString(tr), m); } catch (IllegalArgumentException ignored) { }
        }
        NBTTagCompound z = t.getCompoundTag("zones");
        for (String k : z.getKeySet()) zones.put(Long.parseLong(k), z.getInteger(k));
        if (t.hasKey("scheme")) {
            scheme = t.getString("scheme"); zoneBase = t.getLong("zb"); zoneStep = t.getLong("zs"); flat = t.getLong("fl"); perBlock = t.getLong("pb"); peakPct = t.getInteger("pk");
        }
        lastDay = t.hasKey("day") ? t.getLong("day") : -1;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        NBTTagList l = new NBTTagList();
        for (Stn s : stations.values()) {
            NBTTagCompound c = new NBTTagCompound();
            c.setLong("k", s.key); c.setString("n", s.name);
            NBTTagCompound wt = new NBTTagCompound();
            for (Map.Entry<Long, Double> e : s.waiting.entrySet()) if (e.getValue() >= 0.01) wt.setDouble(String.valueOf(e.getKey()), e.getValue());
            c.setTag("w", wt);
            NBTTagCompound ch = new NBTTagCompound();
            for (Map.Entry<Long, double[]> e : s.changing.entrySet()) { NBTTagCompound x = new NBTTagCompound(); x.setDouble("n", e.getValue()[0]); x.setDouble("o", e.getValue()[1]); ch.setTag(String.valueOf(e.getKey()), x); }
            c.setTag("ch", ch);
            c.setDouble("sat", s.sat); c.setDouble("pu", s.punct); c.setDouble("cr", s.crowd); c.setDouble("wa", s.wait); c.setDouble("fa", s.fac); c.setDouble("cl", s.clean);
            c.setInteger("pop", s.pop); c.setDouble("bd", s.boardedDay); c.setDouble("ad", s.alightedDay); c.setDouble("ld", s.leftDay);
            c.setDouble("ev", s.evadersDay); c.setDouble("wk", s.walkedDay); c.setDouble("tr", s.transfersDay); c.setDouble("co", s.complaintsDay);
            c.setLong("rev", s.revenueDay); c.setDouble("by", s.boardedYesterday); c.setLong("la", s.lastArrival); c.setDouble("hw", s.headway);
            l.appendTag(c);
        }
        t.setTag("st", l);
        l = new NBTTagList();
        for (LineStat x : lines.values()) {
            NBTTagCompound c = new NBTTagCompound();
            c.setString("n", x.name); c.setDouble("sat", x.sat); c.setDouble("pu", x.punct); c.setDouble("lo", x.load);
            c.setDouble("rd", x.ridersDay); c.setDouble("ry", x.ridersYesterday); c.setLong("rv", x.revenueDay); c.setLong("rvy", x.revenueYesterday);
            c.setLong("rf", x.refundsDay); c.setLong("cd", x.costDay); c.setDouble("dl", x.delayedDay); c.setDouble("co", x.complaintsDay); c.setDouble("lf", x.leftDay);
            l.appendTag(c);
        }
        t.setTag("ln", l);
        NBTTagCompound ob = new NBTTagCompound();
        for (Map.Entry<UUID, Map<Long, Map<Long, double[]>>> e : onboard.entrySet()) {
            NBTTagCompound a = new NBTTagCompound();
            for (Map.Entry<Long, Map<Long, double[]>> at : e.getValue().entrySet()) {
                NBTTagCompound b = new NBTTagCompound();
                for (Map.Entry<Long, double[]> d : at.getValue().entrySet()) { NBTTagCompound x = new NBTTagCompound(); x.setDouble("n", d.getValue()[0]); x.setDouble("o", d.getValue()[1]); b.setTag(String.valueOf(d.getKey()), x); }
                a.setTag(String.valueOf(at.getKey()), b);
            }
            ob.setTag(e.getKey().toString(), a);
        }
        t.setTag("on", ob);
        NBTTagCompound z = new NBTTagCompound();
        for (Map.Entry<Long, Integer> e : zones.entrySet()) z.setInteger(String.valueOf(e.getKey()), e.getValue());
        t.setTag("zones", z);
        t.setString("scheme", scheme); t.setLong("zb", zoneBase); t.setLong("zs", zoneStep); t.setLong("fl", flat); t.setLong("pb", perBlock); t.setInteger("pk", peakPct);
        t.setLong("day", lastDay);
        return t;
    }
}
