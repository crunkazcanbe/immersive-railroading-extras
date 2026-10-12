package com.dogpound.railmap.yard;

import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.freight.Contract;
import com.dogpound.railmap.freight.ContractData;
import com.dogpound.railmap.planning.PlanConfig;
import com.dogpound.railmap.planning.PlanData;
import com.dogpound.railmap.sim.SimConfig;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Yard optimisation (her "next 48" list #4), on top of the Hump Yard Control Panel's classification. The yard planner
 * knows the yard's DEPARTURES (each: a departure road - one of the class tracks -, the destinations it takes in the
 * class-track rule language, a deadline, a car / length limit, optionally a loading road) and every wagon, inbound (in
 * the scanned cut list, behind the crest) or already on a class track, with its waybill destination, cargo, the
 * contract's deadline and pay. It
 * <ul>
 *   <li><b>assigns</b> each wagon to a departure by destination, cargo and deadline, by the chosen STRATEGY: FASTEST
 *   (earliest departure that takes it), LOWEST ENERGY (fewest extra shunting moves: a wagon already on / humped to a
 *   departure's road stays), LOWEST COST (late-delivery penalties + $20 per shunting move), MAXIMUM THROUGHPUT (fill
 *   departures to their limits, longest wagons first);</li>
 *   <li><b>reserves</b> departure and loading roads until their departure leaves - the classifier won't hump other
 *   traffic onto a reserved road - and flags two departures booked on one road;</li>
 *   <li><b>predicts deadlock</b> before it happens: plays the remaining cuts over the hump; a class track that fills
 *   while its departure still needs wagons from cuts behind the crest can never clear - the hump would stall - and is
 *   reported (and with yardDeadlockGuard on, START is refused);</li>
 *   <li>shows the <b>yard plan</b> - each departure's readiness (cars assigned / on its road, length, engine on the
 *   road, deadline) - and the <b>energy and time</b> to finish the job by HUMP (gravity: lift each wagon over the crest)
 *   or FLAT switching (kicks: accelerate the cut and the engine every time).</li>
 * </ul>
 */
public final class YardPlanner {
    private YardPlanner() {}

    public static final String[] STRATEGY = {"FASTEST", "LOWEST ENERGY", "LOWEST COST", "MAX THROUGHPUT"};
    public static final double HUMP_M = 1.5;
    private static final Set<String> RAISED = new HashSet<>();

    static final class Car {
        UUID id;
        String name = "", dest = "", cargo = "";
        double len, tonnes;
        long deadline = Long.MAX_VALUE, pay;
        int track = -1, cut = -1;              // on class track / in inbound cut k
        int planned = -1;                      // the class track the classifier will hump it to
        PlanData.Departure dep;
        String why = "";
    }

    public static int strategy(World w, TileYardPanel y) { return PlanData.get(w).yardStrategy.getOrDefault(y.getPos().toLong(), 0); }

    public static List<TileYardPanel> panels(World w) {
        List<TileYardPanel> o = new ArrayList<>();
        for (TileEntity t : w.loadedTileEntityList) if (t instanceof TileYardPanel y && !t.isInvalid()) o.add(y);
        o.sort(Comparator.comparingLong(a -> a.getPos().toLong()));
        return o;
    }

    public static List<PlanData.Departure> departures(World w, TileYardPanel y) { return PlanData.get(w).departures.computeIfAbsent(y.getPos().toLong(), k -> new ArrayList<>()); }

    static boolean matches(String tokens, Car c) {
        for (String raw : tokens.toLowerCase(Locale.ROOT).split("[,;]")) {
            String tok = raw.trim();
            if (tok.isEmpty()) continue;
            if (tok.equals("*")) return true;
            if (tok.startsWith("item:") && c.cargo.toLowerCase(Locale.ROOT).contains(tok.substring(5).trim())) return true;
            if (tok.equals("empty") && c.cargo.isEmpty()) return true;
            if (tok.equals("loaded") && !c.cargo.isEmpty()) return true;
            if (!c.dest.isEmpty() && c.dest.toLowerCase(Locale.ROOT).contains(tok)) return true;
        }
        return false;
    }

    /** a road reserved for a departure that this car isn't for (Classifier skips it) */
    static boolean reservedAgainst(World w, TileYardPanel y, int track, String dest, String cargo) {
        List<PlanData.Departure> ds = PlanData.get(w).departures.get(y.getPos().toLong());
        if (ds == null || ds.isEmpty()) return false;
        long now = w.getTotalWorldTime();
        Car c = new Car();
        c.dest = dest; c.cargo = cargo;
        boolean reserved = false;
        for (PlanData.Departure d : ds) {
            if (d.deadline > 0 && d.deadline < now - 20 * 600) continue;          // long gone: reservation lapsed
            if (d.road != track && d.loadRoad != track) continue;
            if (matches(d.dest, c)) return false;
            reserved = true;
        }
        return reserved;
    }

    public static final class Plan {
        public final List<String[]> rows = new ArrayList<>();
        public String deadlock = "";
        public int unassigned, ready;
    }

    public static Plan plan(World w, TileYardPanel y) {
        Plan p = new Plan();
        long now = w.getTotalWorldTime();
        int strat = strategy(w, y);
        List<PlanData.Departure> deps = departures(w, y);
        Classifier cl = new Classifier(w, y);
        ContractData cd = ContractData.get(w);
        Map<UUID, EntityMoveableRollingStock> stock = new HashMap<>();
        for (EntityMoveableRollingStock s : cam72cam.mod.world.World.get(w).getEntities(EntityMoveableRollingStock.class)) stock.put(s.getUUID(), s);
        List<Car> cars = new ArrayList<>();
        Map<Integer, Boolean> locoOnRoad = new HashMap<>();
        for (EntityMoveableRollingStock s : stock.values()) {
            if (y.getPos().distanceSq(s.getPosition().x, s.getPosition().y, s.getPosition().z) > 800 * 800) continue;
            int i = y.trackUnder(s);
            if (i < 0) continue;
            if (s instanceof Locomotive) { locoOnRoad.put(i, true); continue; }
            cars.add(car(cl, cd, s, i, -1));
        }
        for (int k = 0; k < y.cuts.size(); k++) {
            TileYardPanel.Cut c = y.cuts.get(k);
            if (c.status != 0) continue;
            for (UUID u : c.cars) { EntityMoveableRollingStock s = stock.get(u); if (s != null && !(s instanceof Locomotive)) { Car x = car(cl, cd, s, -1, k); x.planned = c.track; cars.add(x); } }
        }
        // assign
        Map<PlanData.Departure, double[]> load = new HashMap<>();             // cars, metres
        for (PlanData.Departure d : deps) load.put(d, new double[2]);
        List<Car> order = new ArrayList<>(cars);
        if (strat == 3) order.sort((a, b) -> Double.compare(b.len, a.len));
        else order.sort(Comparator.comparingLong(a -> a.deadline));
        for (Car c : order) {
            PlanData.Departure best = null;
            double bestScore = Double.MAX_VALUE;
            for (PlanData.Departure d : deps) {
                if (!matches(d.dest, c)) continue;
                double[] l = load.get(d);
                if (l[0] + 1 > d.maxCars || l[1] + c.len > d.maxLenM) continue;
                int where = c.track >= 0 ? c.track : c.planned;
                int moves = where == d.road ? 0 : where >= 0 ? 1 : 1;
                long dl = d.deadline <= 0 ? now + 20 * 3600 : d.deadline;
                boolean late = dl > c.deadline;
                double score;
                switch (strat) {
                    case 1: score = moves * 1000 + dl / 1000.0; break;
                    case 2: score = (late ? c.pay * 0.5 : 0) + moves * 2000 + dl / 1e6; break;   // cents
                    case 3: score = -(l[0] / Math.max(1, d.maxCars)) * 1000 + dl / 1e6; break;
                    default: score = dl;
                }
                if (score < bestScore) { bestScore = score; best = d; }
            }
            c.dep = best;
            if (best != null) {
                double[] l = load.get(best);
                l[0]++; l[1] += c.len;
                int where = c.track >= 0 ? c.track : c.planned;
                c.why = where == best.road ? "on its road" : where >= 0 ? "shunt T" + (where + 1) + " -> T" + (best.road + 1) : "hump to T" + (best.road + 1);
                if (best.deadline > 0 && c.deadline != Long.MAX_VALUE && best.deadline > c.deadline) c.why += ", LATE for its waybill";
            } else { c.why = "no departure takes it"; p.unassigned++; }
        }
        // readiness
        int[] onRoad = new int[TileYardPanel.TRACKS];
        for (Car c : cars) if (c.track >= 0) onRoad[c.track]++;
        Map<Integer, List<PlanData.Departure>> byRoad = new HashMap<>();
        for (PlanData.Departure d : deps) {
            if (d.road >= 0) byRoad.computeIfAbsent(d.road, k -> new ArrayList<>()).add(d);
            int assigned = 0, there = 0, waiting = 0;
            double len = 0;
            for (Car c : cars) if (c.dep == d) { assigned++; len += c.len; if (c.track == d.road) there++; else if (c.cut >= 0) waiting++; }
            boolean loco = d.road >= 0 && locoOnRoad.getOrDefault(d.road, false);
            String st = d.deadline > 0 && now > d.deadline ? "LATE" : assigned == 0 ? "NO CARS" : there == assigned && loco ? "READY" : there == assigned ? "NEEDS ENGINE" : waiting > 0 ? "WAITING CARS" : "MAKING UP";
            if (st.equals("READY")) p.ready++;
            d.status = st;
            p.rows.add(new String[]{"YD", String.valueOf(d.id), d.name, d.road < 0 ? "-" : "T" + (d.road + 1), d.loadRoad < 0 ? "-" : "T" + (d.loadRoad + 1), d.dest,
                    d.deadline <= 0 ? "-" : mins(d.deadline - now), assigned + "/" + d.maxCars, String.valueOf(there), String.format(Locale.ROOT, "%.0f/%d", len, d.maxLenM), loco ? "1" : "0", st});
        }
        // reservations
        for (Map.Entry<Integer, List<PlanData.Departure>> e : byRoad.entrySet()) {
            List<PlanData.Departure> l = e.getValue();
            for (PlanData.Departure d : l)
                p.rows.add(new String[]{"YR", "T" + (e.getKey() + 1), d.name, d.deadline <= 0 ? "open" : "until " + mins(d.deadline - now), l.size() > 1 ? "CONFLICT: " + l.size() + " departures" : ""});
        }
        // deadlock prediction: hump the remaining cuts
        double[] used = Arrays.copyOf(y.usedM, TileYardPanel.TRACKS);
        outer:
        for (int k = 0; k < y.cuts.size(); k++) {
            TileYardPanel.Cut c = y.cuts.get(k);
            if (c.status != 0 || c.track < 0) continue;
            double cl2 = 0;
            for (UUID u : c.cars) { EntityMoveableRollingStock s = stock.get(u); if (s != null) cl2 += s.getDefinition().getLength(s.gauge); }
            int t = c.track;
            double cap = Math.max(1, y.settings().num("len" + (t + 1)));
            if (used[t] + cl2 <= cap) { used[t] += cl2; continue; }
            // full: can a departure on that road leave before this cut?
            for (PlanData.Departure d : byRoad.getOrDefault(t, Collections.emptyList())) {
                List<Integer> need = new ArrayList<>();
                for (Car x : cars) if (x.dep == d && x.cut > k) need.add(x.cut + 1);
                if (!need.isEmpty()) {
                    p.deadlock = String.format(Locale.ROOT, "DEADLOCK: T%d fills at cut %d; its departure %s still needs cars from cut %s behind the crest", t + 1, k + 1, d.name, new TreeSet<>(need));
                    break outer;
                }
                p.deadlock = String.format(Locale.ROOT, "OVERFLOW: T%d fills at cut %d - send departure %s before humping on", t + 1, k + 1, d.name);
                break outer;
            }
            p.deadlock = String.format(Locale.ROOT, "OVERFLOW: T%d (%.0f m) fills at cut %d and no departure clears it - hump stalls", t + 1, cap, k + 1);
            break;
        }
        if (!p.deadlock.isEmpty()) p.rows.add(new String[]{"YK", p.deadlock});
        // energy + time to finish the job
        double humpT = 0, kickJ = 0, cutsLeft = 0, lenLeft = 0;
        double rest = 0;
        for (TileYardPanel.Cut c : y.cuts) if (c.status == 0) rest += c.tonnes;
        for (TileYardPanel.Cut c : y.cuts) {
            if (c.status != 0) continue;
            cutsLeft++;
            humpT += c.tonnes;
            double v = Math.max(3, y.settings().num("kick")) / 3.6;
            kickJ += 0.5 * (c.tonnes + rest) * 1000 * v * v * 2;                   // accelerate, then brake the rest back
            rest -= c.tonnes;
            for (UUID u : c.cars) { EntityMoveableRollingStock s = stock.get(u); if (s != null) lenLeft += s.getDefinition().getLength(s.gauge); }
        }
        double humpKwh = humpT * 1000 * 9.81 * HUMP_M / 0.85 / 3.6e6, flatKwh = kickJ / 0.85 / 3.6e6;
        double humpMin = lenLeft / Math.max(0.3, SimConfig.humpKmh / 3.6) / 60, flatMin = cutsLeft * 40 / 60.0;
        p.rows.add(new String[]{"YE", "HUMP (gravity)", String.format(Locale.ROOT, "%.2f kWh", humpKwh), String.format(Locale.ROOT, "%.1f min", humpMin), humpKwh <= flatKwh ? "LOWER ENERGY" : ""});
        p.rows.add(new String[]{"YE", "FLAT (kicks)", String.format(Locale.ROOT, "%.2f kWh", flatKwh), String.format(Locale.ROOT, "%.1f min", flatMin), flatMin < humpMin ? "FASTER" : ""});
        // cars
        int n = 0;
        for (Car c : order) {
            if (n++ >= 60) break;
            p.rows.add(new String[]{"YC", c.name, c.dest.isEmpty() ? "-" : c.dest, c.cargo.isEmpty() ? "empty" : c.cargo, c.track >= 0 ? "T" + (c.track + 1) : "cut " + (c.cut + 1),
                    c.dep == null ? "HELD" : c.dep.name, c.deadline == Long.MAX_VALUE ? "-" : mins(c.deadline - now), c.why});
        }
        p.rows.add(0, new String[]{"YP", TileYardPanel.at(y.getPos()), y.gearState(), STRATEGY[strat], String.valueOf(cars.size()), String.valueOf(deps.size()), String.valueOf(p.ready),
                String.valueOf(p.unassigned), p.deadlock.isEmpty() ? "none" : p.deadlock.startsWith("DEADLOCK") ? "DEADLOCK" : "OVERFLOW"});
        AlarmManager am = AlarmManager.get(w);
        String key = "yardplan:" + y.getPos().toLong();
        boolean dl = p.deadlock.startsWith("DEADLOCK");
        if (dl && RAISED.add(key)) am.raise(w, key, AlarmManager.Cat.FREIGHT, AlarmManager.Sev.MAJOR, "YARD " + p.deadlock, y.getPos(), true);   // once, on the change
        else if (!dl && RAISED.remove(key)) am.clear(key);
        return p;
    }

    private static Car car(Classifier cl, ContractData cd, EntityRollingStock s, int track, int cut) {
        Car c = new Car();
        c.id = s.getUUID();
        c.name = com.dogpound.railmap.track.Rails.name(s);
        Classifier.Result r = cl.classify(s);
        c.dest = r.dest.equals("loaded") || r.dest.equals("empty") ? "" : r.dest;
        c.cargo = Classifier.cargo(s);
        try { c.len = s.getDefinition().getLength(s.gauge); } catch (RuntimeException e) { c.len = 15; }
        c.tonnes = com.dogpound.railmap.track.Rails.tonnes(s);
        for (Contract k : cd.all()) if (k.state == Contract.State.ACCEPTED && k.cars.containsKey(s.getUUID())) { c.deadline = k.deadline; c.pay = k.pay; break; }
        c.track = track; c.cut = cut;
        return c;
    }

    static String mins(long ticks) { return ticks < 0 ? "-" + (-ticks / 1200) + " min" : ticks / 1200 + " min"; }

    /** null = may start; else why not (deadlock guard) */
    static String guardStart(World w, TileYardPanel y) {
        if (!PlanConfig.yardDeadlockGuard) return null;
        Plan p = plan(w, y);
        return p.deadlock.startsWith("DEADLOCK") ? "§cYard planner: " + p.deadlock + " - re-plan before humping" : null;
    }

    // ---- departures ---------------------------------------------------------------------------------------------------------
    public static String addDeparture(World w, TileYardPanel y, String name, int road, String dest, int minutes, int maxCars, int maxLen, int loadRoad) {
        if (road < 0 || road >= TileYardPanel.TRACKS) return "§cRoad 1-" + TileYardPanel.TRACKS;
        PlanData d = PlanData.get(w);
        PlanData.Departure dp = new PlanData.Departure();
        dp.id = d.id(); dp.name = name; dp.road = road; dp.dest = dest; dp.deadline = minutes <= 0 ? 0 : w.getTotalWorldTime() + minutes * 1200L;
        dp.maxCars = Math.max(1, maxCars); dp.maxLenM = Math.max(20, maxLen); dp.loadRoad = loadRoad;
        departures(w, y).add(dp);
        d.markDirty();
        return "§aDeparture " + name + " booked on T" + (road + 1) + " for " + dest + (minutes > 0 ? " in " + minutes + " min" : "");
    }

    public static String removeDeparture(World w, TileYardPanel y, int id) {
        boolean r = departures(w, y).removeIf(d -> d.id == id);
        PlanData.get(w).markDirty();
        return r ? "§eDeparture #" + id + " cancelled - its roads are free" : "§cNo departure #" + id;
    }

    public static String cycleStrategy(World w, TileYardPanel y) {
        PlanData d = PlanData.get(w);
        int s = (strategy(w, y) + 1) % STRATEGY.length;
        d.yardStrategy.put(y.getPos().toLong(), s);
        d.markDirty();
        return "§bYard strategy: " + STRATEGY[s];
    }

    static BlockPos pos(TileYardPanel y) { return y.getPos(); }
}
