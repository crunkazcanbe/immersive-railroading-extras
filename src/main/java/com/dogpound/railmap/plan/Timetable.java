package com.dogpound.railmap.plan;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.List;

/**
 * A PLANNED timetable — what is supposed to run, and when.
 *
 * <p>This is the layer the railway was missing. The mod already knows <em>who</em> goes where
 * ({@link com.dogpound.railmap.auto.Dispatcher}), <em>how</em> to drive it
 * ({@link com.dogpound.railmap.auto.Autopilot}) and what is <em>safe</em>
 * ({@link com.dogpound.railmap.auto.Interlocking}, {@link com.dogpound.railmap.signal.SignalEngine}).
 * What it could not do is say "the 9:15 from Harbor calls at Downtown at 9:23 and must be clear
 * of the single track before the 9:20 comes the other way".
 *
 * <p>Note the difference from the existing station "timetable": that one is a LOG — it records
 * trains that happened to stop nearby. This is a PLAN, written in advance, that the railway is
 * then held to.
 *
 * <p>Times are in ticks and are <b>offsets from the service's own departure</b>, not absolute
 * clock values. That keeps a service reusable: one "Blue Line stopper" pattern can run every
 * 20 minutes without duplicating its call list, and a delay shifts the whole shape at once
 * instead of corrupting it.
 */
public final class Timetable {

    /** Aliases; the real definitions live in {@link Recurrence}, which has no MC imports. */
    public static final int TICKS_PER_SECOND = Recurrence.TICKS_PER_SECOND;
    public static final int TICKS_PER_MINUTE = Recurrence.TICKS_PER_MINUTE;

    /** One booked stop: which station, and how long after departure it is due. */
    public static final class Call {
        /** Station position, packed the same way the rest of the mod packs stations. */
        public long station;
        /** Ticks after the service departs that it should ARRIVE here. */
        public int arriveOffset;
        /** Ticks after the service departs that it should LEAVE here. Must be >= arriveOffset. */
        public int departOffset;

        public Call() {}

        public Call(long station, int arriveOffset, int departOffset) {
            this.station = station;
            this.arriveOffset = arriveOffset;
            this.departOffset = departOffset;
        }

        /** How long the train is booked to stand here. */
        public int dwell() {
            return Math.max(0, departOffset - arriveOffset);
        }
    }

    /** A booked train run: a pattern of calls, and when it repeats. */
    public static final class Service {
        public int id;
        /** What the dispatcher board and the arrivals boards call it, e.g. "Blue Line 9:15". */
        public String name = "";
        /** Which line this belongs to, matching RailwayData.Line.name. Blank = standalone. */
        public String line = "";
        /** Absolute tick of the first departure (world time). */
        public long firstDeparture;
        /**
         * Repeat interval in ticks. 0 means it runs once.
         * A 20-minute headway service is {@code 20 * TICKS_PER_MINUTE}.
         */
        public int headway;
        /** Booked speed, used to turn distance into time when planning. */
        public int maxKmh = 60;
        /** The calls, in order. First is the origin, last is the destination. */
        public final List<Call> calls = new ArrayList<Call>();
        /** Set false to keep a service on the books without running it. */
        public boolean enabled = true;

        public Service() {}

        /** Total booked journey time, origin departure to final arrival. */
        public int journeyTicks() {
            if (calls.isEmpty()) return 0;
            return calls.get(calls.size() - 1).arriveOffset;
        }

        /**
         * The next departure at or after {@code now}.
         * Returns -1 for a one-off service that has already gone.
         */
        public long nextDeparture(long now) {
            // Kept in Recurrence so the arithmetic can be unit-checked without Minecraft.
            return Recurrence.next(firstDeparture, headway, enabled, now);
        }

        /** Absolute tick this run is booked to arrive at call {@code i}. */
        public long arrivalOf(long departure, int i) {
            return departure + calls.get(i).arriveOffset;
        }

        /** Absolute tick this run is booked to leave call {@code i}. */
        public long departureOf(long departure, int i) {
            return departure + calls.get(i).departOffset;
        }
    }

    private final List<Service> services = new ArrayList<Service>();
    private int nextId = 1;

    public List<Service> services() {
        return services;
    }

    public Service byId(int id) {
        for (int i = 0; i < services.size(); i++) {
            if (services.get(i).id == id) return services.get(i);
        }
        return null;
    }

    public Service create(String name) {
        Service s = new Service();
        s.id = nextId++;
        s.name = name == null ? "" : name;
        services.add(s);
        return s;
    }

    public boolean remove(int id) {
        for (int i = 0; i < services.size(); i++) {
            if (services.get(i).id == id) {
                services.remove(i);
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- persistence
    // Same shape as the rest of the mod's saved data: a tag list of compounds, small
    // enough that struct-of-arrays is not worth it here (services are tens, not thousands).

    public NBTTagCompound writeTo(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < services.size(); i++) {
            Service s = services.get(i);
            NBTTagCompound st = new NBTTagCompound();
            st.setInteger("id", s.id);
            st.setString("name", s.name);
            st.setString("line", s.line);
            st.setLong("first", s.firstDeparture);
            st.setInteger("headway", s.headway);
            st.setInteger("kmh", s.maxKmh);
            st.setBoolean("on", s.enabled);
            NBTTagList cl = new NBTTagList();
            for (int c = 0; c < s.calls.size(); c++) {
                Call call = s.calls.get(c);
                NBTTagCompound ct = new NBTTagCompound();
                ct.setLong("st", call.station);
                ct.setInteger("a", call.arriveOffset);
                ct.setInteger("d", call.departOffset);
                cl.appendTag(ct);
            }
            st.setTag("calls", cl);
            list.appendTag(st);
        }
        tag.setTag("services", list);
        tag.setInteger("nextId", nextId);
        return tag;
    }

    public void readFrom(NBTTagCompound tag) {
        services.clear();
        nextId = Math.max(1, tag.getInteger("nextId"));
        NBTTagList list = tag.getTagList("services", 10);   // 10 = compound
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound st = list.getCompoundTagAt(i);
            Service s = new Service();
            s.id = st.getInteger("id");
            s.name = st.getString("name");
            s.line = st.getString("line");
            s.firstDeparture = st.getLong("first");
            s.headway = st.getInteger("headway");
            s.maxKmh = st.hasKey("kmh") ? st.getInteger("kmh") : 60;
            s.enabled = !st.hasKey("on") || st.getBoolean("on");
            NBTTagList cl = st.getTagList("calls", 10);
            for (int c = 0; c < cl.tagCount(); c++) {
                NBTTagCompound ct = cl.getCompoundTagAt(c);
                s.calls.add(new Call(ct.getLong("st"), ct.getInteger("a"), ct.getInteger("d")));
            }
            services.add(s);
            if (s.id >= nextId) nextId = s.id + 1;
        }
    }
}
