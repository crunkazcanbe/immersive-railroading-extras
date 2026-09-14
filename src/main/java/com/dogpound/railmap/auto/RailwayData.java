package com.dogpound.railmap.auto;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything the railroad remembers about running itself, saved with the world
 * ({@code irextras_railway.dat}):
 * <ul>
 *   <li><b>Lines</b> — named, coloured lists of stations ("Blue Line: Downtown, Harbor, Airport").</li>
 *   <li><b>Driverless trains</b> — which locomotive, what it runs, how long it waits.</li>
 *   <li><b>Passengers</b> — every ticket put into a machine: from, to, and which train is coming.</li>
 * </ul>
 * Stations themselves stay in {@link com.dogpound.railmap.server.StationData}, keyed by the
 * track position the dispatcher named.
 */
public final class RailwayData extends WorldSavedData {
    private static final String NAME = "irextras_railway";

    // ---- lines ---------------------------------------------------------------------------

    public static final class Line {
        public String name;
        public int color;
        public final List<Long> stations = new ArrayList<>();

        Line(String name, int color) {
            this.name = name;
            this.color = color;
        }
    }

    // ---- driverless trains ---------------------------------------------------------------

    public enum Mode {
        /** Round and round a line: last stop leads back to the first. */
        LINE("Runs a line (loop)"),
        /** End to end and back again, like a shuttle or a branch line. */
        SHUTTLE("Shuttle (back and forth)"),
        /** Waits at its home station until a ticket calls it, then takes the passenger. */
        ON_CALL("On call (tickets)"),
        /** A one-off trip to a single station, then it waits there. */
        SEND("Sent to a station");

        public final String label;

        Mode(String label) { this.label = label; }

        public static Mode byOrdinal(int i) {
            Mode[] v = values();
            return i >= 0 && i < v.length ? v[i] : LINE;
        }
    }

    public static final class AutoTrain {
        public final UUID loco;
        public String label = "";
        public Mode mode = Mode.LINE;
        public String line = "";
        /** Where it goes in SEND mode, and where it waits in ON_CALL mode. */
        public long home;
        public int index;
        /** Shuttle direction: +1 up the line, -1 back down. */
        public int direction = 1;
        public int dwellSeconds = 20;
        public int maxKmh = 60;
        /** Extra stops a ticket asked for, served before the line resumes. */
        public final List<Long> calls = new ArrayList<>();

        AutoTrain(UUID loco) {
            this.loco = loco;
        }
    }

    // ---- passengers ----------------------------------------------------------------------

    public enum CallState { WAITING, ASSIGNED, BOARDING, RIDING }

    public static final class Call {
        public final int id;
        public final long from, to;
        public final String passenger;
        public final long created;
        public CallState state = CallState.WAITING;
        public UUID train;

        Call(int id, long from, long to, String passenger, long created) {
            this.id = id;
            this.from = from;
            this.to = to;
            this.passenger = passenger;
            this.created = created;
        }
    }

    private final Map<String, Line> lines = new LinkedHashMap<>();
    private final Map<UUID, AutoTrain> trains = new LinkedHashMap<>();
    private final List<Call> calls = new ArrayList<>();
    private int nextCallId = 1;
    private int nextTicketSerial = 1000;

    public RailwayData() {
        super(NAME);
    }

    public RailwayData(String name) {
        super(name);
    }

    public static RailwayData get(World world) {
        MapStorage storage = world.getPerWorldStorage();
        RailwayData d = (RailwayData) storage.getOrLoadData(RailwayData.class, NAME);
        if (d == null) {
            d = new RailwayData();
            storage.setData(NAME, d);
        }
        return d;
    }

    // ---- lines ---------------------------------------------------------------------------

    public Map<String, Line> lines() { return lines; }

    public Line line(String name) { return name == null ? null : lines.get(name); }

    /** Colours handed to new lines in order, the way a transit map picks them. */
    private static final int[] PALETTE = { 0x1E88E5, 0xE53935, 0x43A047, 0xFDD835, 0x8E24AA, 0xFB8C00, 0x00ACC1, 0xD81B60 };

    public Line saveLine(String name, List<Long> stations) {
        Line l = lines.get(name);
        if (l == null) {
            l = new Line(name, PALETTE[lines.size() % PALETTE.length]);
            lines.put(name, l);
        }
        l.stations.clear();
        l.stations.addAll(stations);
        markDirty();
        return l;
    }

    public void deleteLine(String name) {
        if (lines.remove(name) != null) markDirty();
    }

    // ---- trains --------------------------------------------------------------------------

    public Map<UUID, AutoTrain> trains() { return trains; }

    public AutoTrain train(UUID loco) { return trains.get(loco); }

    public AutoTrain setTrain(UUID loco) {
        AutoTrain t = trains.computeIfAbsent(loco, AutoTrain::new);
        markDirty();
        return t;
    }

    public void removeTrain(UUID loco) {
        if (trains.remove(loco) != null) {
            for (Call c : calls) if (loco.equals(c.train)) { c.train = null; c.state = CallState.WAITING; }
            markDirty();
        }
    }

    // ---- passengers ----------------------------------------------------------------------

    public List<Call> calls() { return calls; }

    public Call addCall(long from, long to, String passenger, long now) {
        Call c = new Call(nextCallId++, from, to, passenger, now);
        calls.add(c);
        markDirty();
        return c;
    }

    public void removeCall(Call c) {
        if (calls.remove(c)) markDirty();
    }

    public int nextTicketSerial() {
        markDirty();
        return nextTicketSerial++;
    }

    public int waitingAt(long station) {
        int n = 0;
        for (Call c : calls) if (c.from == station && c.state != CallState.RIDING) n++;
        return n;
    }

    // ---- persistence ---------------------------------------------------------------------

    @Override
    public void readFromNBT(NBTTagCompound t) {
        lines.clear();
        NBTTagList ls = t.getTagList("lines", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < ls.tagCount(); i++) {
            NBTTagCompound c = ls.getCompoundTagAt(i);
            Line l = new Line(c.getString("n"), c.getInteger("c"));
            for (int[] pair : pairs(c.getIntArray("s"))) l.stations.add(unpack(pair));
            lines.put(l.name, l);
        }
        trains.clear();
        NBTTagList ts = t.getTagList("trains", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < ts.tagCount(); i++) {
            NBTTagCompound c = ts.getCompoundTagAt(i);
            AutoTrain a = new AutoTrain(c.getUniqueId("u"));
            a.label = c.getString("l");
            a.mode = Mode.byOrdinal(c.getByte("m"));
            a.line = c.getString("line");
            a.home = c.getLong("home");
            a.index = c.getInteger("i");
            a.direction = c.getByte("d") < 0 ? -1 : 1;
            a.dwellSeconds = c.hasKey("dw") ? c.getInteger("dw") : 20;
            a.maxKmh = c.hasKey("mx") ? c.getInteger("mx") : 60;
            for (int[] pair : pairs(c.getIntArray("calls"))) a.calls.add(unpack(pair));
            trains.put(a.loco, a);
        }
        calls.clear();
        NBTTagList cs = t.getTagList("calls", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < cs.tagCount(); i++) {
            NBTTagCompound c = cs.getCompoundTagAt(i);
            Call call = new Call(c.getInteger("id"), c.getLong("f"), c.getLong("t"), c.getString("p"), c.getLong("at"));
            CallState[] all = CallState.values();
            int st = c.getByte("st");
            call.state = st >= 0 && st < all.length ? all[st] : CallState.WAITING;
            if (c.hasUniqueId("tr")) call.train = c.getUniqueId("tr");
            calls.add(call);
        }
        nextCallId = Math.max(1, t.getInteger("nextCall"));
        nextTicketSerial = Math.max(1000, t.getInteger("nextSerial"));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        NBTTagList ls = new NBTTagList();
        for (Line l : lines.values()) {
            NBTTagCompound c = new NBTTagCompound();
            c.setString("n", l.name);
            c.setInteger("c", l.color);
            c.setIntArray("s", pack(l.stations));
            ls.appendTag(c);
        }
        t.setTag("lines", ls);
        NBTTagList ts = new NBTTagList();
        for (AutoTrain a : trains.values()) {
            NBTTagCompound c = new NBTTagCompound();
            c.setUniqueId("u", a.loco);
            c.setString("l", a.label);
            c.setByte("m", (byte) a.mode.ordinal());
            c.setString("line", a.line);
            c.setLong("home", a.home);
            c.setInteger("i", a.index);
            c.setByte("d", (byte) a.direction);
            c.setInteger("dw", a.dwellSeconds);
            c.setInteger("mx", a.maxKmh);
            c.setIntArray("calls", pack(a.calls));
            ts.appendTag(c);
        }
        t.setTag("trains", ts);
        NBTTagList cs = new NBTTagList();
        for (Call call : calls) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("id", call.id);
            c.setLong("f", call.from);
            c.setLong("t", call.to);
            c.setString("p", call.passenger);
            c.setLong("at", call.created);
            c.setByte("st", (byte) call.state.ordinal());
            if (call.train != null) c.setUniqueId("tr", call.train);
            cs.appendTag(c);
        }
        t.setTag("calls", cs);
        t.setInteger("nextCall", nextCallId);
        t.setInteger("nextSerial", nextTicketSerial);
        return t;
    }

    /** 1.12 NBT has no long arrays: store each long as two ints. */
    static int[] pack(List<Long> values) {
        int[] out = new int[values.size() * 2];
        for (int i = 0; i < values.size(); i++) {
            long v = values.get(i);
            out[i * 2] = (int) (v >> 32);
            out[i * 2 + 1] = (int) v;
        }
        return out;
    }

    private static List<int[]> pairs(int[] a) {
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i + 1 < a.length; i += 2) out.add(new int[]{ a[i], a[i + 1] });
        return out;
    }

    private static long unpack(int[] pair) {
        return ((long) pair[0] << 32) | (pair[1] & 0xffffffffL);
    }
}
