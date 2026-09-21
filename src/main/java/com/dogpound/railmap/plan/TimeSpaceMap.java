package com.dogpound.railmap.plan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Who is booked to be on which piece of track, and when.
 *
 * <p>This is the difference between a timetable that is merely written down and one that is
 * actually workable. Real dispatchers think in <b>time-space</b>: a train is not "at" a place,
 * it <em>occupies a stretch of track for a stretch of time</em>. Two trains conflict when their
 * occupations overlap on the same piece — and you can know that in advance, before either has
 * moved, which is the whole point.
 *
 * <p>The existing {@link com.dogpound.railmap.graph.Occupancy} answers "is a train there RIGHT
 * NOW" by geometry. This answers "will a train be there at tick N", which no amount of geometry
 * can tell you.
 *
 * <p><b>Headway</b> is the safety gap. Booking two trains onto the same piece back-to-back is
 * legal on paper and awful in practice: signals need time to clear, and a driver needs to see
 * green rather than crawl up to a yellow. Every occupation is therefore padded by
 * {@link #headway} ticks at both ends before it is tested.
 *
 * <p>Deliberately NOT thread-safe and deliberately not persisted: it is rebuilt from the
 * {@link Timetable} whenever the plan changes. The plan is the truth; this is a derived index.
 */
public final class TimeSpaceMap {

    /** One train's booked occupation of one track piece. */
    public static final class Slot {
        /** Packed track piece position. */
        public final long piece;
        /** Absolute ticks, inclusive of the headway padding. */
        public final long from, to;
        /** Which service booked it, so a conflict can be named rather than just detected. */
        public final int serviceId;
        /** Which departure of that service (a service repeating on a headway has many runs). */
        public final long runDeparture;

        public Slot(long piece, long from, long to, int serviceId, long runDeparture) {
            this.piece = piece;
            this.from = from;
            this.to = to;
            this.serviceId = serviceId;
            this.runDeparture = runDeparture;
        }

        public boolean overlaps(long a, long b) {
            return from <= b && a <= to;
        }
    }

    /** A detected clash, with enough detail to explain it on the dispatcher board. */
    public static final class Conflict {
        public final long piece;
        public final Slot existing;
        public final long wantFrom, wantTo;

        public Conflict(long piece, Slot existing, long wantFrom, long wantTo) {
            this.piece = piece;
            this.existing = existing;
            this.wantFrom = wantFrom;
            this.wantTo = wantTo;
        }

        /** How much later the newcomer would have to be to miss this clash entirely. */
        public long pushRequired() {
            return existing.to - wantFrom + 1;
        }
    }

    private final Map<Long, List<Slot>> byPiece = new HashMap<Long, List<Slot>>();
    private int headway = Recurrence.TICKS_PER_SECOND * 15;   // 15s default

    public void setHeadway(int ticks) {
        this.headway = Math.max(0, ticks);
    }

    public int headway() {
        return headway;
    }

    public void clear() {
        byPiece.clear();
    }

    /** Drop every booking made by a service — used when its plan is edited. */
    public void clearService(int serviceId) {
        for (List<Slot> slots : byPiece.values()) {
            for (int i = slots.size() - 1; i >= 0; i--) {
                if (slots.get(i).serviceId == serviceId) slots.remove(i);
            }
        }
    }

    /**
     * Test one occupation without booking it.
     * Returns the first clash found, or null if the slot is free.
     */
    public Conflict test(long piece, long from, long to, int ignoreServiceId) {
        List<Slot> slots = byPiece.get(Long.valueOf(piece));
        if (slots == null) return null;
        long padFrom = from - headway;
        long padTo = to + headway;
        for (int i = 0; i < slots.size(); i++) {
            Slot s = slots.get(i);
            if (s.serviceId == ignoreServiceId) continue;   // re-planning itself
            if (s.overlaps(padFrom, padTo)) {
                return new Conflict(piece, s, from, to);
            }
        }
        return null;
    }

    /** Book it. Caller is expected to have called {@link #test} first if it cares. */
    public void reserve(long piece, long from, long to, int serviceId, long runDeparture) {
        Long key = Long.valueOf(piece);
        List<Slot> slots = byPiece.get(key);
        if (slots == null) {
            slots = new ArrayList<Slot>(2);
            byPiece.put(key, slots);
        }
        slots.add(new Slot(piece, from, to, serviceId, runDeparture));
    }

    /** Everything booked on a piece, for the train-graph GUI and for explaining conflicts. */
    public List<Slot> at(long piece) {
        List<Slot> slots = byPiece.get(Long.valueOf(piece));
        return slots == null ? new ArrayList<Slot>(0) : slots;
    }

    /** Total bookings held, for diagnostics. */
    public int size() {
        int n = 0;
        for (List<Slot> s : byPiece.values()) n += s.size();
        return n;
    }

    /** How many distinct pieces have at least one booking. */
    public int pieces() {
        return byPiece.size();
    }
}
