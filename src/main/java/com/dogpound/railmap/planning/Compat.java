package com.dogpound.railmap.planning;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock.CouplerType;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.entity.LocomotiveSteam;
import cam72cam.immersiverailroading.registry.EntityRollingStockDefinition;
import cam72cam.immersiverailroading.registry.LocomotiveDefinition;
import cam72cam.immersiverailroading.tile.TileRail;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.auto.TrackPath;
import com.dogpound.railmap.server.Electric;
import com.dogpound.railmap.server.RailProps;
import com.dogpound.railmap.server.StationData;
import com.dogpound.railmap.sim.MultiUnit;
import com.dogpound.railmap.sim.SimData;
import com.dogpound.railmap.track.Rails;
import com.dogpound.railmap.track.TileStructureBoard;
import com.dogpound.railmap.track.TrackHooks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Rolling-stock compatibility engineering (her "next 48" list #8): a real train (the consist standing nearest, as IR
 * built it) checked against a real route (its line's stops, followed rail by rail along the track as it lies) for
 * <ul>
 *   <li><b>gauge</b> - every vehicle's wheelset against every rail's gauge;</li>
 *   <li><b>minimum curve radius</b> - the tightest curve against what each vehicle's length lets it take
 *   (bogie vehicles ~1.6 x length, long locomotives 2 x length);</li>
 *   <li><b>axle load</b> - the heaviest axle against each structure's route availability and the route default;</li>
 *   <li><b>loading gauge</b> - each vehicle's height / width against measured structure clearances and the route default;</li>
 *   <li><b>couplers + MU jumpers</b> - broken / weak knuckles, couplers not engaged, powered units on different MU systems;</li>
 *   <li><b>brakes</b> - vehicles without a continuous (air) brake, the braked-mass percentage against what the line speed
 *   needs (50 / 70 / 85 %), EP brakes for 140 km/h+;</li>
 *   <li><b>train control</b> - a CBTC line needs every cab fitted (steam and non-MU units can't be);</li>
 *   <li><b>speeds</b> - the slowest vehicle's top speed against the line speed the train is set to;</li>
 *   <li><b>traction</b> - an electric on stretches without wire; starting on the ruling gradient;</li>
 *   <li><b>temporary works</b> - train-length limits on the route.</li>
 * </ul>
 * Every problem names the vehicle and the exact place.
 */
public final class Compat {
    private Compat() {}

    public static Locomotive nearest(World w, double x, double z, double reach) {
        Locomotive best = null;
        double bd = reach * reach;
        try {
            for (Locomotive l : cam72cam.mod.world.World.get(w).getEntities(Locomotive.class)) {
                Vec3d p = l.getPosition();
                double d = (p.x - x) * (p.x - x) + (p.z - z) * (p.z - z);
                if (d < bd) { bd = d; best = l; }
            }
        } catch (RuntimeException | LinkageError ignored) { }
        return best;
    }

    private static final class Route {
        int gaugeMin = Integer.MAX_VALUE, gaugeMax, radius = Integer.MAX_VALUE, steps, wired, samples;
        BlockPos radiusAt, gaugeAt, unwiredAt;
        double km, grade, gradeAt;
        BlockPos gradePos;
        final List<Object[]> structures = new ArrayList<>();      // {board, rating t, clearance H, pos}
        final Set<BlockPos> seenBoards = new HashSet<>();
        final List<PlanData.Work> lengthLimits = new ArrayList<>();
        int tsrs;
        boolean found = true;
    }

    public static List<String> check(World w, Locomotive loco, String lineName) {
        List<String> out = new ArrayList<>();
        RailwayData data = RailwayData.get(w);
        RailwayData.AutoTrain at = data.train(loco.getUUID());
        String line = lineName != null && !lineName.isEmpty() ? lineName : at != null && !at.line.isEmpty() ? at.line : "";
        RailwayData.Line l = data.line(line);
        List<EntityCoupleableRollingStock> train = loco.getTrain(false);
        out.add("H|" + Rails.name(loco) + " (" + train.size() + " vehicles) on line " + (l == null ? "-" : l.name));
        if (l == null || l.stations.size() < 2) { out.add("P|No route: the train has no line (give a line name)"); return out; }
        Route r = route(w, l);
        StationData sd = StationData.get(w);
        out.add(String.format(Locale.ROOT, "I|Route %s: %d stops, %.2f km followed, tightest curve %s, ruling grade %.1f %%, %d structures, %d TSRs",
                l.name, l.stations.size(), r.km, r.radius == Integer.MAX_VALUE ? "straight" : r.radius + " m", r.grade, r.structures.size(), r.tsrs));
        if (!r.found) out.add("W|Part of the route could not be followed along the track (points set the other way / gap) - checked only what was found");
        // per vehicle
        double kg = 0, braked = 0, te = 0, maxAxle = 0, tallest = 0, widest = 0;
        String heaviest = "", tallestN = "", widestN = "";
        double maxKmh = Double.MAX_VALUE;
        String slowest = "";
        boolean ep = true;
        Set<String> mu = new LinkedHashSet<>();
        double lenM = 0;
        int i = 0;
        for (EntityCoupleableRollingStock s : train) {
            i++;
            EntityRollingStockDefinition d = s.getDefinition();
            if (d == null) { out.add("P|Vehicle " + i + ": unknown definition"); continue; }
            String nm = i + " " + Rails.name(s);
            double len = d.getLength(s.gauge), h = d.getHeight(s.gauge), wd = d.getWidth(s.gauge);
            lenM += len;
            int gmm = (int) Math.round(s.gauge.value() * 1000);
            if (r.gaugeMin != Integer.MAX_VALUE && (Math.abs(gmm - r.gaugeMin) > 5 || Math.abs(gmm - r.gaugeMax) > 5))
                out.add("P|GAUGE: " + nm + " runs on " + gmm + " mm, the route has " + r.gaugeMin + (r.gaugeMax != r.gaugeMin ? "-" + r.gaugeMax : "") + " mm" + (r.gaugeAt == null ? "" : " (at " + Rails.xz(r.gaugeAt) + ")"));
            double rmin = Math.max(15, (s instanceof Locomotive && len > 19 ? 2.0 : 1.6) * len);
            if (r.radius != Integer.MAX_VALUE && r.radius < rmin)
                out.add(String.format(Locale.ROOT, "P|CURVE: %s (%.1f m long) needs %.0f m radius, the route has %d m at %s", nm, len, rmin, r.radius, Rails.xz(r.radiusAt)));
            double t = Rails.tonnes(s), axle = t / Rails.axles(s);
            kg += t * 1000;
            if (d.hasPressureBrake()) braked += t * 1000; else out.add("P|BRAKE: " + nm + " has no continuous (air) brake - unbraked vehicle in the train");
            if (!d.hasEpBrake()) ep = false;
            if (axle > maxAxle) { maxAxle = axle; heaviest = nm; }
            if (h > tallest) { tallest = h; tallestN = nm; }
            if (wd > widest) { widest = wd; widestN = nm; }
            if (s instanceof Locomotive lo && d instanceof LocomotiveDefinition ld) {
                double v = ld.getMaxSpeed(s.gauge).metric();
                if (v > 0 && v < maxKmh) { maxKmh = v; slowest = nm; }
                if (!ld.isCabCar()) { te += ld.getStartingTractionNewtons(s.gauge); mu.add(MultiUnit.system(w, lo)); }
                RailProps.Props pr = RailProps.get(w).peek(lo.getUUID());
                if (pr != null && Electric.isElectric(lo, pr) && r.samples > 0 && r.wired < r.samples)
                    out.add(String.format(Locale.ROOT, "P|TRACTION: %s is electric, %d %% of the route has no wire%s", nm, 100 - r.wired * 100 / r.samples, r.unwiredAt == null ? "" : " (first gap " + Rails.xz(r.unwiredAt) + ")"));
                if (l.movingBlock && (lo instanceof LocomotiveSteam || !ld.muliUnitCapable && !ld.isCabCar()))
                    out.add("P|TRAIN CONTROL: " + nm + " can't carry CBTC cab equipment (" + (lo instanceof LocomotiveSteam ? "steam" : "no MU / control bus") + ") - line " + l.name + " is CBTC");
            }
            // couplers
            for (CouplerType ct : CouplerType.values()) {
                EntityCoupleableRollingStock o = s.getCoupled(ct);
                if (o == null) continue;
                SimData.Car c = SimData.get(w).peek(s.getUUID());
                float k = c == null ? 1 : c.coupler[ct == CouplerType.FRONT ? 0 : 1];
                if (k <= 0.15f) out.add("P|COUPLER: " + nm + " " + ct.name().toLowerCase(Locale.ROOT) + " knuckle broken / cracked (" + Math.round(k * 100) + " %)");
                else if (k < 0.5f) out.add("W|COUPLER: " + nm + " " + ct.name().toLowerCase(Locale.ROOT) + " knuckle worn (" + Math.round(k * 100) + " %)");
                if (!s.isCouplerEngaged(ct)) out.add("W|COUPLER: " + nm + " " + ct.name().toLowerCase(Locale.ROOT) + " coupler not engaged");
            }
        }
        mu.remove("ANY");
        if (mu.size() > 1) out.add("P|MU: powered units on different control systems " + mu + " - they can't work in multiple");
        if (mu.contains("NONE") && te > 0 && countPowered(train) > 1) out.add("W|MU: a unit without MU equipment needs its own driver");
        // axle load
        double ra = PlanConfig.routeAxleTonnes;
        String raWhere = "route default";
        for (Object[] s : r.structures) if ((double) s[1] < ra) { ra = (double) s[1]; raWhere = s[0] + " at " + Rails.xz((BlockPos) s[3]); }
        if (maxAxle > ra + 0.05) out.add(String.format(Locale.ROOT, "P|AXLE LOAD: %s %.1f t/axle over RA %.1f t (%s)", heaviest, maxAxle, ra, raWhere));
        else out.add(String.format(Locale.ROOT, "O|Axle load: heaviest %.1f t (%s) within %.1f t", maxAxle, heaviest, ra));
        // loading gauge
        double gh = PlanConfig.gaugeHeightM, gw = PlanConfig.gaugeWidthM;
        String ghWhere = "route default";
        for (Object[] s : r.structures) if ((double) s[2] > 0 && (double) s[2] < gh) { gh = (double) s[2]; ghWhere = s[0] + " at " + Rails.xz((BlockPos) s[3]); }
        if (tallest > gh) out.add(String.format(Locale.ROOT, "P|LOADING GAUGE: %s is %.2f m tall, clearance %.2f m (%s)", tallestN, tallest, gh, ghWhere));
        if (widest > gw) out.add(String.format(Locale.ROOT, "P|LOADING GAUGE: %s is %.2f m wide, gauge %.2f m", widestN, widest, gw));
        if (tallest <= gh && widest <= gw) out.add(String.format(Locale.ROOT, "O|Loading gauge: %.2f x %.2f m inside %.2f x %.2f m", tallest, widest, gh, gw));
        // brakes vs speed
        double lineKmh = at != null ? at.maxKmh : maxKmh == Double.MAX_VALUE ? 80 : maxKmh;
        double need = lineKmh <= 60 ? 50 : lineKmh <= 100 ? 70 : 85, pct = kg <= 0 ? 0 : braked * 100 / kg;
        if (pct < need) out.add(String.format(Locale.ROOT, "P|BRAKED MASS: %.0f %% - %.0f km/h needs %.0f %%", pct, lineKmh, need));
        else out.add(String.format(Locale.ROOT, "O|Braked mass %.0f %% (needs %.0f %% at %.0f km/h)", pct, need, lineKmh));
        if (lineKmh > 140 && !ep) out.add("P|BRAKES: above 140 km/h every vehicle needs EP brakes");
        // speeds
        if (maxKmh != Double.MAX_VALUE && lineKmh > maxKmh + 1) out.add(String.format(Locale.ROOT, "P|SPEED: set to %.0f km/h, %s tops out at %.0f km/h", lineKmh, slowest, maxKmh));
        // grade
        double mass = kg * 9.81, startGrade = mass <= 0 ? 0 : Math.max(0, (te / mass - 0.004) * 100);
        if (te > 0 && startGrade < r.grade) out.add(String.format(Locale.ROOT, "P|GRADE: can start on %.1f %%, the route climbs %.1f %%%s", startGrade, r.grade, r.gradePos == null ? "" : " at " + Rails.xz(r.gradePos)));
        else if (te > 0) out.add(String.format(Locale.ROOT, "O|Starts on %.1f %% (ruling grade %.1f %%)", startGrade, r.grade));
        // works
        for (PlanData.Work k : r.lengthLimits) if (lenM > k.maxLenM) out.add(String.format(Locale.ROOT, "P|LENGTH: %.0f m long, %s allows %d m", lenM, k.name, k.maxLenM));
        long problems = out.stream().filter(s -> s.startsWith("P|")).count();
        out.add(1, problems == 0 ? "V|COMPATIBLE - no problems found" : "V|NOT COMPATIBLE - " + problems + " problem" + (problems == 1 ? "" : "s"));
        return out;
    }

    private static int countPowered(List<EntityCoupleableRollingStock> train) {
        int n = 0;
        for (EntityCoupleableRollingStock s : train) if (s instanceof Locomotive l && !l.getDefinition().isCabCar()) n++;
        return n;
    }

    /** follow the line's track stop to stop */
    private static Route route(World w, RailwayData.Line l) {
        Route r = new Route();
        cam72cam.mod.world.World umc = cam72cam.mod.world.World.get(w);
        List<TileStructureBoard> boards = TrackHooks.live(w, TileStructureBoard.class);
        for (int i = 0; i + 1 < l.stations.size(); i++) {
            BlockPos a = BlockPos.fromLong(l.stations.get(i)), b = BlockPos.fromLong(l.stations.get(i + 1));
            TileRail start = Rails.pieceAt(w, a);
            if (start == null) { r.found = false; continue; }
            double budget = Math.max(200, Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ()) * 3);
            TrackPath p = TrackPath.find(umc, start, null, budget, TrackPath.near(b.getX() + 0.5, b.getY(), b.getZ() + 0.5, 4));
            if (p == null) { r.found = false; continue; }
            r.km += p.length / 1000.0;
            double lastY = Double.NaN, lastD = 0;
            for (int k = 0; k < p.steps.size(); k++) {
                TrackPath.Step st = p.steps.get(k);
                BlockPos bp = new BlockPos(st.pos.x, st.pos.y, st.pos.z);
                TileRail rail = Rails.pieceAt(w, bp);
                if (rail != null) {
                    int rad = Rails.radius(rail), g = Rails.gaugeMm(rail);
                    if (rad > 0 && rad < r.radius) { r.radius = rad; r.radiusAt = bp; }
                    if (g < r.gaugeMin) { r.gaugeMin = g; r.gaugeAt = bp; }
                    r.gaugeMax = Math.max(r.gaugeMax, g);
                }
                if (TrackHooks.tsrAt(w, bp) > 0) r.tsrs++;
                if (k % 8 == 0) { r.samples++; if (Electric.wiredAt(w, bp)) r.wired++; else if (r.unwiredAt == null) r.unwiredAt = bp; }
                if (Double.isNaN(lastY)) { lastY = st.pos.y; lastD = st.distance; }
                else if (st.distance - lastD >= 50) {
                    double gr = Math.abs(st.pos.y - lastY) / (st.distance - lastD) * 100;
                    if (gr > r.grade) { r.grade = gr; r.gradePos = bp; }
                    lastY = st.pos.y; lastD = st.distance;
                }
                for (TileStructureBoard sb : boards) {
                    if (r.seenBoards.contains(sb.getPos()) || sb.getPos().distanceSq(bp) > 24 * 24) continue;      // a board stands at its structure
                    r.seenBoards.add(sb.getPos());
                    Map<String, Object> info = new HashMap<>();
                    sb.gearInfo(info);
                    r.structures.add(new Object[]{info.get("kind"), ((Number) info.getOrDefault("axleRatingT", PlanConfig.routeAxleTonnes)).doubleValue(),
                            ((Number) info.getOrDefault("clearanceH", 0.0)).doubleValue(), sb.getPos()});
                }
                r.steps++;
            }
            for (PlanData.Work k : PlanData.get(w).works)
                if (k.live() && k.kind == PlanData.Kind.LENGTH && !r.lengthLimits.contains(k))
                    for (TrackPath.Step st : p.steps) if (Math.hypot(st.pos.x - k.x, st.pos.z - k.z) <= k.r) { r.lengthLimits.add(k); break; }
        }
        return r;
    }

    /** run it and keep the answer for the console / desk */
    public static String run(World w, double x, double z, String line) {
        Locomotive l = nearest(w, x, z, 48);
        PlanData d = PlanData.get(w);
        d.lastCompat.clear();
        if (l == null) { d.lastCompat.add("P|No locomotive within 48 blocks"); d.markDirty(); return "§cNo locomotive within 48 blocks"; }
        d.lastCompat.addAll(check(w, l, line));
        d.markDirty();
        String v = d.lastCompat.size() > 1 ? d.lastCompat.get(1).substring(2) : "";
        return (v.startsWith("COMPATIBLE") ? "§a" : "§c") + Rails.name(l) + ": " + v;
    }

    static String stationName(World w, long k) { return Autopilot.nameOf(StationData.get(w), k); }
}
