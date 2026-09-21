package com.dogpound.railmap.plan;

import com.dogpound.railmap.auto.TrackPath;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns a route into a conflict-free plan — or explains why it cannot be one.
 *
 * <p>Three jobs, in increasing order of how hard they are:
 *
 * <ol>
 *   <li><b>Timing.</b> Walk a {@link TrackPath} at the booked speed and work out when the train
 *       is on each piece. Arithmetic, but it is what makes everything else possible.</li>
 *   <li><b>Conflict resolution.</b> Test those occupations against everything already booked
 *       ({@link TimeSpaceMap}). On a clash, the cheapest honest fix is to leave later — so push
 *       the departure by exactly enough to clear the blocking slot and try again. This converges
 *       because every push is strictly forwards and bounded by {@link #MAX_PUSH}.</li>
 *   <li><b>Deadlock.</b> The one that actually bites on a model railway. Two trains heading at
 *       each other down a single track will each happily pass a green signal and meet nose to
 *       nose with nowhere to go. No amount of signalling prevents it, because at the moment each
 *       train enters, the line ahead genuinely is clear. It has to be caught in the PLAN.</li>
 * </ol>
 *
 * <p>The deadlock test is the interesting one: for two runs travelling in opposite directions,
 * find the pieces they share. If any shared piece is a <em>passing place</em> — somewhere one
 * train can stand clear while the other goes by — the meet is arrangeable and we say where.
 * If the shared stretch has no passing place anywhere along it, the plan is impossible and must
 * be rejected rather than signalled around.
 */
public final class Scheduler {

    /** Never push a departure more than this (20 minutes) before declaring it unplannable. */
    public static final int MAX_PUSH = 20 * Timetable.TICKS_PER_MINUTE;

    /** How many times to retry after a push. */
    private static final int MAX_ATTEMPTS = 24;

    /**
     * km/h to blocks per tick. A Minecraft block is a metre, and there are 20 ticks a second,
     * so 72 km/h is exactly 1 block/tick — a handy sanity check when reading these numbers.
     */
    public static double blocksPerTick(int kmh) {
        return Math.max(0.01D, kmh / 72.0D);
    }

    /** One piece of the plan: this train is on this piece for this window. */
    public static final class Occupation {
        public final long piece;
        public final long from, to;

        public Occupation(long piece, long from, long to) {
            this.piece = piece;
            this.from = from;
            this.to = to;
        }
    }

    /** The outcome of trying to plan one run. */
    public static final class Plan {
        /** Departure actually achieved (may be later than requested). */
        public long departure;
        /** Empty when {@link #ok} is false. */
        public final List<Occupation> occupations = new ArrayList<Occupation>();
        /** Every clash encountered while trying, newest last — useful for explaining failure. */
        public final List<TimeSpaceMap.Conflict> hit = new ArrayList<TimeSpaceMap.Conflict>();
        public boolean ok;
        /** Human-readable reason when {@link #ok} is false. */
        public String reason = "";

        public long arrival() {
            return occupations.isEmpty() ? departure : occupations.get(occupations.size() - 1).to;
        }
    }

    private Scheduler() {}

    /** Pack a track piece the same way the rest of the mod packs positions.
     *  Note TrackPath.Step.pos is Universal Mod Core's cam72cam.mod.math.Vec3i, NOT
     *  Minecraft's — it carries plain public x/y/z ints, so build the BlockPos from those. */
    public static long key(TrackPath.Step step) {
        return new BlockPos(step.pos.x, step.pos.y, step.pos.z).toLong();
    }

    /**
     * Time a run along {@code path} leaving at {@code departure}, without testing conflicts.
     * Each piece is occupied from the moment the train reaches it until it clears the NEXT one
     * (a train is not a point; it is still fouling the piece behind it as it enters the next).
     */
    public static List<Occupation> time(TrackPath path, long departure, int kmh) {
        List<Occupation> out = new ArrayList<Occupation>();
        if (path == null || path.steps.isEmpty()) return out;
        double bpt = blocksPerTick(kmh);
        int n = path.steps.size();
        for (int i = 0; i < n; i++) {
            TrackPath.Step s = path.steps.get(i);
            long enter = departure + (long) Math.floor(s.distance / bpt);
            // clear when the train has reached the piece after next, or +1 tick at the end
            double clearAt = (i + 1 < n) ? path.steps.get(i + 1).distance : s.distance + bpt;
            long leave = departure + (long) Math.ceil(clearAt / bpt);
            if (leave <= enter) leave = enter + 1;
            out.add(new Occupation(key(s), enter, leave));
        }
        return out;
    }

    /**
     * Plan a run, pushing the departure later as needed until it is conflict-free.
     * Does NOT book it — call {@link #commit} once you are happy.
     */
    public static Plan plan(TrackPath path, long wantDeparture, int kmh,
                            int serviceId, TimeSpaceMap map) {
        Plan plan = new Plan();
        plan.departure = wantDeparture;
        if (path == null || path.steps.isEmpty()) {
            plan.reason = "no route";
            return plan;
        }
        long departure = wantDeparture;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            List<Occupation> occ = time(path, departure, kmh);
            TimeSpaceMap.Conflict clash = null;
            for (int i = 0; i < occ.size(); i++) {
                Occupation o = occ.get(i);
                TimeSpaceMap.Conflict c = map.test(o.piece, o.from, o.to, serviceId);
                if (c != null) { clash = c; break; }
            }
            if (clash == null) {
                plan.ok = true;
                plan.departure = departure;
                plan.occupations.addAll(occ);
                return plan;
            }
            plan.hit.add(clash);
            long push = Math.max(1L, clash.pushRequired());
            departure += push;
            if (departure - wantDeparture > MAX_PUSH) {
                plan.reason = "cannot fit: would need to leave "
                        + ((departure - wantDeparture) / Timetable.TICKS_PER_MINUTE)
                        + " min late";
                return plan;
            }
        }
        plan.reason = "gave up after " + MAX_ATTEMPTS + " attempts";
        return plan;
    }

    /** Write an accepted plan into the map so later runs see it. */
    public static void commit(Plan plan, int serviceId, TimeSpaceMap map) {
        if (!plan.ok) return;
        for (int i = 0; i < plan.occupations.size(); i++) {
            Occupation o = plan.occupations.get(i);
            map.reserve(o.piece, o.from, o.to, serviceId, plan.departure);
        }
    }

    // ------------------------------------------------------------------ deadlock

    /** Where two opposing runs can legally pass each other, or the fact that they cannot. */
    public static final class Meet {
        /** True when a passing place exists on the shared stretch. */
        public boolean possible;
        /** The piece to hold one train at. Only meaningful when {@link #possible}. */
        public long at;
        /** How many pieces the two runs share. */
        public int sharedPieces;
        public String reason = "";
    }

    /**
     * Can these two routes meet, and where?
     *
     * <p>{@code passingPlaces} is the set of packed pieces where a train can stand clear of the
     * running line — loops, sidings, platform roads at a two-road station. Callers build it from
     * the scanned network (a node with more than two track connections is a candidate) so this
     * class stays free of world lookups and testable on its own.
     *
     * <p>The rule is simple and strict: if the two paths share track and NONE of the shared
     * pieces is a passing place, the two runs cannot both happen — that is a deadlock, and the
     * only honest answers are to retime one of them or build a loop.
     */
    public static Meet meet(TrackPath a, TrackPath b, Set<Long> passingPlaces) {
        Meet m = new Meet();
        if (a == null || b == null || a.steps.isEmpty() || b.steps.isEmpty()) {
            m.possible = true;      // nothing shared, nothing to arrange
            m.reason = "no route to compare";
            return m;
        }
        Set<Long> inA = new HashSet<Long>();
        for (int i = 0; i < a.steps.size(); i++) inA.add(Long.valueOf(key(a.steps.get(i))));

        List<Long> shared = new ArrayList<Long>();
        for (int i = 0; i < b.steps.size(); i++) {
            Long k = Long.valueOf(key(b.steps.get(i)));
            if (inA.contains(k)) shared.add(k);
        }
        m.sharedPieces = shared.size();
        if (shared.isEmpty()) {
            m.possible = true;
            m.reason = "routes do not share track";
            return m;
        }
        // Prefer a passing place in the MIDDLE of the shared stretch: holding a train at the
        // very first or last shared piece usually means it is still fouling the junction.
        int bestIdx = -1;
        int mid = shared.size() / 2;
        for (int d = 0; d < shared.size(); d++) {
            int i = mid + ((d % 2 == 0) ? d / 2 : -(d / 2 + 1));
            if (i < 0 || i >= shared.size()) continue;
            if (passingPlaces != null && passingPlaces.contains(shared.get(i))) {
                bestIdx = i;
                break;
            }
        }
        if (bestIdx < 0) {
            m.possible = false;
            m.reason = "single track for " + shared.size()
                     + " pieces with no passing place — deadlock";
            return m;
        }
        m.possible = true;
        m.at = shared.get(bestIdx).longValue();
        m.reason = "meet at a passing place " + bestIdx + " of " + shared.size()
                 + " along the shared stretch";
        return m;
    }
}
