package com.dogpound.railmap.safety;

import cam72cam.mod.math.Vec3i;
import com.dogpound.railmap.signal.SignalRegistry;
import com.dogpound.railmap.signal.TileSignalMast;
import com.dogpound.railmap.signal.TrackFollower;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Why a signal is being held at STOP by the safety systems - one place every signal asks (TileSignalMast) and the
 * signal engine consults per block (SignalEngine). Rebuilt once a second from:
 * <ul>
 *   <li>axle counter sections that aren't CLEAR -> the signals linked to them;</li>
 *   <li>operated emergency plungers and blocking possessions -> every signal whose block runs into their zone;</li>
 *   <li>divided / missing-wagon trains (fail-safe INTEGRITY) -> every signal whose block holds one of their vehicles;</li>
 *   <li>a train whose radio link is lost (fail-safe RADIO_LOSS = hold) -> the signal it is approaching;</li>
 *   <li>lamp proving (fail-safe LAMP_FAILURE) -> the signal in rear of one whose lamp has failed.</li>
 * </ul>
 * Holds only ever make a signal more restrictive.
 */
public final class SafetyHolds {
    private SafetyHolds() {}

    private static final Map<Integer, Map<Long, String>> HOLD = new HashMap<>();
    /** zones that block whole stretches: x, z, radius, and the reason */
    private static final Map<Integer, List<Object[]>> ZONES = new HashMap<>();
    /** the signal in rear of each signal (filled by the engine), for lamp proving and the lab */
    private static final Map<Integer, Map<Long, Long>> REAR = new HashMap<>();

    public static void clear() { HOLD.clear(); ZONES.clear(); REAR.clear(); SPAN.clear(); }

    /** blocks the engine found barred this pass (zone / integrity / lamp proving), per signal */
    private static final Map<Integer, Map<Long, String>> SPAN = new HashMap<>();

    public static String reason(World w, BlockPos mast) {
        Map<Long, String> m = HOLD.get(w.provider.getDimension());
        String r = m == null ? null : m.get(mast.toLong());
        if (r != null) return r;
        Map<Long, String> s = SPAN.get(w.provider.getDimension());
        return s == null ? null : s.get(mast.toLong());
    }

    /** the engine's verdict on a signal's block this pass (null = nothing barring it) */
    public static void spanHold(World w, BlockPos mast, String why) {
        Map<Long, String> s = SPAN.computeIfAbsent(w.provider.getDimension(), k -> new HashMap<>());
        if (why == null) s.remove(mast.toLong()); else s.put(mast.toLong(), why);
    }

    public static Map<Long, String> all(World w) {
        Map<Long, String> m = new HashMap<>(SPAN.getOrDefault(w.provider.getDimension(), Collections.emptyMap()));
        m.putAll(HOLD.getOrDefault(w.provider.getDimension(), Collections.emptyMap()));
        return m;
    }

    /** the engine found that `rear`'s block ends where `next` stands */
    public static void rear(World w, BlockPos next, BlockPos rear) { REAR.computeIfAbsent(w.provider.getDimension(), k -> new HashMap<>()).put(next.toLong(), rear.toLong()); }
    public static BlockPos rearOf(World w, BlockPos next) { Long r = REAR.getOrDefault(w.provider.getDimension(), Collections.emptyMap()).get(next.toLong()); return r == null ? null : BlockPos.fromLong(r); }

    /** a whole block (the stretch a signal protects) is barred: plunger / possession zones, divided trains in it */
    public static String spanReason(World w, TrackFollower.Span span) {
        if (w.isRemote || span == null) return null;
        for (Object[] z : ZONES.getOrDefault(w.provider.getDimension(), Collections.emptyList()))
            if (Geo.spanInZone(span, (Double) z[0], (Double) z[1], (Double) z[2])) return (String) z[3];
        if (FailSafe.safe(w, FailSafe.INTEGRITY)) {
            SafetyData d = SafetyData.get(w);
            Map<String, List<double[]>> cars = ConsistWatch.cars(w);
            for (SafetyData.TrainRec r : d.trains.values()) {
                if (r.gone || r.integrity != 2) continue;
                List<double[]> c = cars.get(r.id);
                if (c != null && Geo.spanHas(span, c)) return "train integrity lost: " + r.headcode;
            }
        }
        return null;
    }

    /** once a second */
    public static void tick(World w) {
        int dim = w.provider.getDimension();
        Map<Long, String> hold = new HashMap<>();
        List<Object[]> zones = new ArrayList<>();
        for (TileAxleEvaluator e : SafetyTicker.evaluators(w)) {
            for (int i = 0; i < TileAxleEvaluator.N; i++) {
                TileAxleEvaluator.Section s = e.sec[i];
                if (s.signals.isEmpty() || e.clear(i)) continue;
                String why = e.powered() ? e.sectionLine(i) : "axle evaluator has no 12 V";
                for (long p : s.signals) hold.putIfAbsent(p, why);
            }
        }
        for (TileEmergencyPlunger p : SafetyTicker.plungers(w))
            if (p.operated()) zones.add(new Object[]{p.getPos().getX() + 0.5, p.getPos().getZ() + 0.5, (double) p.zone(), "emergency stop plunger at " + p.getPos().getX() + "," + p.getPos().getZ()});
        for (TilePossessionSwitch p : SafetyTicker.possessions(w))
            if (p.blocking()) zones.add(new Object[]{p.getPos().getX() + 0.5, p.getPos().getZ() + 0.5, (double) p.zone(), "line blocked - possession by " + p.holder()});
        com.dogpound.railmap.track.TrackHooks.holds(w, hold, zones);   // #15-17 tunnel emergencies, crossing failures, structure hazards
        // signals standing in a zone are held too (their own block may run out of it)
        if (!zones.isEmpty()) for (TileSignalMast m : SignalRegistry.masts(dim)) {
            Vec3i g = m.governedRail();
            if (g == null) continue;
            for (Object[] z : zones) if (Geo.d2(g.x + 0.5, g.z + 0.5, (Double) z[0], (Double) z[1]) <= (Double) z[2] * (Double) z[2]) { hold.putIfAbsent(m.getPos().toLong(), (String) z[3]); break; }
        }
        long now = w.getTotalWorldTime();
        if (FailSafe.mode(w, FailSafe.RADIO_LOSS) == 1)
            for (SafetyData.TrainRec r : SafetyData.get(w).trains.values())
                if (!r.gone && r.radio == 2 && now - r.radioLostAt >= 200 && r.berthPos != 0) hold.putIfAbsent(r.berthPos, "radio link lost: " + r.headcode);
        SigHooks.holds(w, hold);                                                        // batch-3 #18 signals isolated for maintenance
        HOLD.put(dim, hold);
        ZONES.put(dim, zones);
    }
}
