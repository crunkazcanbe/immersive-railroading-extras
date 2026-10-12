package com.dogpound.railmap.ohle;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.*;

/**
 * Saved overhead-line state (her ChatGPT list #31-33): contact-wire wear and damage, standing OHLE faults, the OHLE
 * feeder circuit breakers, each electric loco's pantograph, and the traction energy ledger.
 */
public final class OhleData extends WorldSavedData {
    private static final String NAME = "irextras_ohle";

    /** a fault on the contact line: an earth fault (wire down, flooded, earthed section energised) or a short (flashover) */
    public static final class Fault {
        public long pos;
        public String kind = "EARTH", why = "";
        /** stays until the wire is repaired / the earth removed; a transient one (an arc) clears once the breaker opens */
        public boolean persistent;
        public long since;
    }

    /** the OHLE feeder circuit breaker of one Track Feeder: 0 closed, 1 tripped (auto-reclose due), 2 locked out, 3 opened by hand */
    public static final class Cb {
        public int state, shots, trips;
        public long recloseAt, lastTrip, closedAt;
        public String last = "";
    }

    /** one electric loco's pantograph, circuit breaker and energy ledger */
    public static final class Pan {
        public boolean up, vcb, manual, wantUp = true, downZone, broken;
        public long raiseAt, lastWire, vcbAt;
        public int forceN;
        public float carbon = 1f;
        public long[] arcs = new long[8];
        public int arcN;
        public String note = "";
        public double lastH = Double.NaN, lastX, lastZ;
        public boolean alerted;
        public long lastMagnet;
        // ledger (#33): kWh drawn, regenerated (taken by the line), burnt in resistors, cost in coins, kWh in peak hours
        public double kwhIn, kwhRegen, kwhRes, cost, kwhPeak;
        public String name = "";
    }

    /** a timetable / operating period for comparing energy use (energy mark <label>) */
    public static final class Period {
        public String label = "";
        public long start, end;
        public double kwhIn, kwhRegen, kwhRes, cost, kwhPeak;
        public int departures, held, heldSeconds, starts;
    }

    public final Map<Long, Float> wear = new HashMap<>();
    public final Set<Long> damaged = new HashSet<>();
    public final List<Fault> faults = new ArrayList<>();
    public final Map<Long, Cb> cbs = new HashMap<>();
    public final Map<UUID, Pan> pans = new HashMap<>();
    public final List<Period> periods = new ArrayList<>();
    /** energy dispatch: hold FULL LOAD freights out of the peak tariff */
    public boolean peakShift;
    /** recent OHLE events for the console (newest first) */
    public final LinkedList<String> log = new LinkedList<>();
    /** wire spots with repeated arcing: pos -> {count, first tick, distinct-loco hash} */
    public final transient Map<Long, long[]> spots = new HashMap<>();

    public OhleData() { super(NAME); }
    public OhleData(String n) { super(n); }

    public static OhleData get(World w) {
        MapStorage s = w.getPerWorldStorage();
        OhleData d = (OhleData) s.getOrLoadData(OhleData.class, NAME);
        if (d == null) { d = new OhleData(); s.setData(NAME, d); }
        return d;
    }

    public Cb cb(long feeder) { return cbs.computeIfAbsent(feeder, k -> new Cb()); }
    public boolean cbClosed(long feeder) { Cb c = cbs.get(feeder); return c == null || c.state == 0; }

    public Pan pan(UUID id) {
        Pan p = pans.get(id);
        if (p == null) { p = new Pan(); p.forceN = com.dogpound.railmap.RailMapConfig.ohlePanForceN; pans.put(id, p); markDirty(); }
        return p;
    }

    public Period period() {
        if (periods.isEmpty()) { Period p = new Period(); p.label = "A"; periods.add(p); }
        return periods.get(periods.size() - 1);
    }

    public void log(World w, String s) {
        log.addFirst(com.dogpound.railmap.alarm.Recorder.clock(w, w.getTotalWorldTime()) + " " + s);
        while (log.size() > 60) log.removeLast();
        markDirty();
    }

    /** long[] as an int array of hi/lo pairs (works on every 1.12 NBT version) */
    static long[] longs(NBTTagCompound t, String k) {
        int[] a = t.getIntArray(k);
        long[] o = new long[a.length / 2];
        for (int i = 0; i < o.length; i++) o[i] = ((long) a[2 * i] << 32) | (a[2 * i + 1] & 0xFFFFFFFFL);
        return o;
    }

    static void longs(NBTTagCompound t, String k, long[] v) {
        int[] a = new int[v.length * 2];
        for (int i = 0; i < v.length; i++) { a[2 * i] = (int) (v[i] >>> 32); a[2 * i + 1] = (int) v[i]; }
        t.setIntArray(k, a);
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        wear.clear(); damaged.clear(); faults.clear(); cbs.clear(); pans.clear(); periods.clear(); log.clear();
        long[] wp = longs(t, "wearPos");
        int[] wv = t.getIntArray("wearVal");
        for (int i = 0; i < Math.min(wp.length, wv.length); i++) wear.put(wp[i], Float.intBitsToFloat(wv[i]));
        for (long p : longs(t, "damaged")) damaged.add(p);
        for (net.minecraft.nbt.NBTBase b : t.getTagList("faults", 10)) {
            NBTTagCompound c = (NBTTagCompound) b;
            Fault f = new Fault();
            f.pos = c.getLong("pos"); f.kind = c.getString("kind"); f.why = c.getString("why"); f.persistent = c.getBoolean("pers"); f.since = c.getLong("since");
            faults.add(f);
        }
        for (net.minecraft.nbt.NBTBase b : t.getTagList("cbs", 10)) {
            NBTTagCompound c = (NBTTagCompound) b;
            Cb x = new Cb();
            x.state = c.getInteger("st"); x.shots = c.getInteger("shots"); x.trips = c.getInteger("trips"); x.recloseAt = c.getLong("rc"); x.lastTrip = c.getLong("lt"); x.last = c.getString("last");
            if (x.state == 1) x.state = 2;                       // a reclose that was due when the world closed: lock out, safe side
            cbs.put(c.getLong("pos"), x);
        }
        for (net.minecraft.nbt.NBTBase b : t.getTagList("pans", 10)) {
            NBTTagCompound c = (NBTTagCompound) b;
            Pan p = new Pan();
            p.manual = c.getBoolean("manual"); p.wantUp = !c.hasKey("want") || c.getBoolean("want"); p.forceN = c.hasKey("force") ? c.getInteger("force") : com.dogpound.railmap.RailMapConfig.ohlePanForceN;
            p.carbon = c.hasKey("carbon") ? c.getFloat("carbon") : 1f; p.broken = c.getBoolean("broken"); p.downZone = c.getBoolean("dz");
            p.kwhIn = c.getDouble("kin"); p.kwhRegen = c.getDouble("kreg"); p.kwhRes = c.getDouble("kres"); p.cost = c.getDouble("cost"); p.kwhPeak = c.getDouble("kpk"); p.name = c.getString("name");
            pans.put(new UUID(c.getLong("m"), c.getLong("l")), p);
        }
        for (net.minecraft.nbt.NBTBase b : t.getTagList("periods", 10)) {
            NBTTagCompound c = (NBTTagCompound) b;
            Period p = new Period();
            p.label = c.getString("label"); p.start = c.getLong("start"); p.end = c.getLong("end");
            p.kwhIn = c.getDouble("kin"); p.kwhRegen = c.getDouble("kreg"); p.kwhRes = c.getDouble("kres"); p.cost = c.getDouble("cost"); p.kwhPeak = c.getDouble("kpk");
            p.departures = c.getInteger("dep"); p.held = c.getInteger("held"); p.heldSeconds = c.getInteger("hs"); p.starts = c.getInteger("starts");
            periods.add(p);
        }
        peakShift = t.getBoolean("peakShift");
        for (net.minecraft.nbt.NBTBase b : t.getTagList("log", 8)) log.add(((net.minecraft.nbt.NBTTagString) b).getString());
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        long[] wp = new long[wear.size()];
        int[] wv = new int[wear.size()];
        int i = 0;
        for (Map.Entry<Long, Float> e : wear.entrySet()) { wp[i] = e.getKey(); wv[i++] = Float.floatToIntBits(e.getValue()); }
        longs(t, "wearPos", wp);
        t.setIntArray("wearVal", wv);
        long[] dm = new long[damaged.size()];
        i = 0;
        for (long p : damaged) dm[i++] = p;
        longs(t, "damaged", dm);
        NBTTagList fl = new NBTTagList();
        for (Fault f : faults) {
            NBTTagCompound c = new NBTTagCompound();
            c.setLong("pos", f.pos); c.setString("kind", f.kind); c.setString("why", f.why); c.setBoolean("pers", f.persistent); c.setLong("since", f.since);
            fl.appendTag(c);
        }
        t.setTag("faults", fl);
        NBTTagList cl = new NBTTagList();
        for (Map.Entry<Long, Cb> e : cbs.entrySet()) {
            Cb x = e.getValue();
            NBTTagCompound c = new NBTTagCompound();
            c.setLong("pos", e.getKey()); c.setInteger("st", x.state); c.setInteger("shots", x.shots); c.setInteger("trips", x.trips); c.setLong("rc", x.recloseAt); c.setLong("lt", x.lastTrip); c.setString("last", x.last);
            cl.appendTag(c);
        }
        t.setTag("cbs", cl);
        NBTTagList pl = new NBTTagList();
        for (Map.Entry<UUID, Pan> e : pans.entrySet()) {
            Pan p = e.getValue();
            NBTTagCompound c = new NBTTagCompound();
            c.setLong("m", e.getKey().getMostSignificantBits()); c.setLong("l", e.getKey().getLeastSignificantBits());
            c.setBoolean("manual", p.manual); c.setBoolean("want", p.wantUp); c.setInteger("force", p.forceN); c.setFloat("carbon", p.carbon); c.setBoolean("broken", p.broken); c.setBoolean("dz", p.downZone);
            c.setDouble("kin", p.kwhIn); c.setDouble("kreg", p.kwhRegen); c.setDouble("kres", p.kwhRes); c.setDouble("cost", p.cost); c.setDouble("kpk", p.kwhPeak); c.setString("name", p.name);
            pl.appendTag(c);
        }
        t.setTag("pans", pl);
        NBTTagList ql = new NBTTagList();
        for (Period p : periods) {
            NBTTagCompound c = new NBTTagCompound();
            c.setString("label", p.label); c.setLong("start", p.start); c.setLong("end", p.end);
            c.setDouble("kin", p.kwhIn); c.setDouble("kreg", p.kwhRegen); c.setDouble("kres", p.kwhRes); c.setDouble("cost", p.cost); c.setDouble("kpk", p.kwhPeak);
            c.setInteger("dep", p.departures); c.setInteger("held", p.held); c.setInteger("hs", p.heldSeconds); c.setInteger("starts", p.starts);
            ql.appendTag(c);
        }
        t.setTag("periods", ql);
        NBTTagList lg = new NBTTagList();
        for (String s : log) lg.appendTag(new net.minecraft.nbt.NBTTagString(s));
        t.setTag("log", lg);
        t.setBoolean("peakShift", peakShift);
        return t;
    }
}
