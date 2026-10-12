package com.dogpound.railmap.track;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Track condition per piece of IR track (keyed by the TileRail parent position, the same key TrackPath steps use) and
 * the asset register the console reads (point machines, structures, tunnels, crossings - one summary row each, kept
 * even while their chunk is unloaded). Saved as irextras_track.dat. Track nobody has run over has no record = new.
 */
public final class TrackData extends WorldSavedData {
    private static final String NAME = "irextras_track";

    /** Limits (her list #13): {alert, intervention, immediate action}. Values are UIC / Network Rail style. */
    public enum Metric {
        TOP("top", "mm SD", 1.0, 3.0, 5.0, 8.0),          // vertical alignment (longitudinal level) standard deviation
        LINE("line", "mm SD", 0.8, 2.5, 4.0, 6.0),        // lateral alignment standard deviation
        TWIST("twist", "mm/3m", 2.0, 8.0, 12.0, 18.0),    // cross-level change over a 3 m base
        GAUGE("gauge", "mm wide", 0.0, 8.0, 15.0, 25.0),  // gauge spread over nominal
        RAIL("rail wear", "mm", 0.0, 6.0, 9.0, 13.0),     // rail head loss
        SURFACE("rail surface", "%", 0.0, 40.0, 65.0, 85.0),   // corrugation / rolling contact fatigue
        SLEEPERS("sleepers", "% failed", 0.0, 15.0, 30.0, 50.0),
        FOULING("ballast", "% fouled", 5.0, 30.0, 50.0, 75.0);
        public final String label, unit;
        public final double fresh, alert, il, ial;
        Metric(String label, String unit, double fresh, double alert, double il, double ial) { this.label = label; this.unit = unit; this.fresh = fresh; this.alert = alert; this.il = il; this.ial = ial; }
        /** 0 good, 1 alert, 2 intervention, 3 immediate action */
        public int band(double v) { return v >= ial ? 3 : v >= il ? 2 : v >= alert ? 1 : 0; }
    }

    /**
     * Track construction standards (her ChatGPT batch 3 #10). Every piece of track is built to one; an old world's
     * track is BALLASTED (the standard it always behaved like). Each has a build cost, an axle-load limit, a line speed
     * and its own wear: slab track has no ballast to foul and barely loses geometry, heavy-haul has head-hardened rail
     * and heavy concrete sleepers, metro track has resilient fastenings but corrugates in its tight curves, high-speed
     * track is held to tight geometry. Running heavier axles or faster trains than a standard is built for wears it far
     * faster (and is counted); with TrackConfig.trackStandardLimits on, its line speed is also a permanent restriction.
     * Wear factors: {geometry, rail, sleepers/fastenings, ballast}.
     */
    public enum Standard {
        BALLASTED("Ballasted", "BAL", 25.0, 160, 900, new double[]{1.0, 1.0, 1.0, 1.0}),
        SLAB("Slab track", "SLAB", 22.5, 300, 3200, new double[]{0.15, 0.9, 0.25, 0.0}),
        HEAVY_HAUL("Heavy haul", "HH", 40.0, 110, 1600, new double[]{0.7, 0.55, 0.5, 0.8}),
        METRO("Metro", "MET", 18.0, 100, 2400, new double[]{0.5, 1.25, 0.6, 0.3}),
        HIGH_SPEED("High speed", "HS", 17.0, 350, 2800, new double[]{0.4, 0.8, 0.4, 0.6});
        public final String label, tag;
        public final double axleT;
        public final int lineKmh, costPerM;
        public final double[] wear;
        Standard(String label, String tag, double axleT, int lineKmh, int costPerM, double[] wear) { this.label = label; this.tag = tag; this.axleT = axleT; this.lineKmh = lineKmh; this.costPerM = costPerM; this.wear = wear; }
        public static Standard of(int i) { return values()[Math.max(0, Math.min(values().length - 1, i))]; }
        /** mean wear factor (the console's "Wear x") */
        public double wearAvg() { return (wear[0] + wear[1] + wear[2] + wear[3]) / 4; }
    }

    public static final class Seg {
        public final double[] v = new double[Metric.values().length];
        /** batch-3 #10: construction standard (ordinal), axle overloads and overspeeds seen */
        public int std, overloads, overspeeds;
        public Standard standard() { return Standard.of(std); }
        /** gross tonnes carried since renewal, km/h fastest seen, curve radius (0 = straight), nominal gauge mm */
        public double tonnes;
        public int maxKmh, radius, gaugeMm = 1435, lengthM;
        public long inspectedAt, workedAt;
        public String inspectedBy = "", lastWork = "";
        /** the values the last inspection recorded (what the console and reports show) */
        public double[] seen;

        Seg() { for (Metric m : Metric.values()) v[m.ordinal()] = m.fresh; }

        public double get(Metric m) { return v[m.ordinal()]; }
        public void add(Metric m, double d) { v[m.ordinal()] = Math.max(m.fresh * 0.5, Math.min(m.ial * 1.6, v[m.ordinal()] + d)); }
        public void set(Metric m, double d) { v[m.ordinal()] = Math.max(0, d); }

        /** worst band over all metrics */
        public int band() { int b = 0; for (Metric m : Metric.values()) b = Math.max(b, m.band(get(m))); return b; }

        public Metric worst() {
            Metric w = Metric.TOP;
            double ws = -1;
            for (Metric m : Metric.values()) { double s = (get(m) - m.fresh) / (m.ial - m.fresh); if (s > ws) { ws = s; w = m; } }
            return w;
        }

        /** track quality index, 100 = new, 0 = immediate-action everywhere */
        public int tqi() {
            double worst = 0, sum = 0;
            for (Metric m : Metric.values()) {
                double s = Math.max(0, Math.min(1.2, (get(m) - m.fresh) / (m.ial - m.fresh)));
                worst = Math.max(worst, s);
                sum += s;
            }
            return (int) Math.round(Math.max(0, 100 - 70 * worst - 30 * sum / Metric.values().length));
        }

        /** temporary speed restriction this condition calls for (km/h), 0 = none */
        public int tsr() {
            int b = band();
            if (b < 2) return 0;
            Metric w = worst();
            boolean geometry = w == Metric.TOP || w == Metric.TWIST || w == Metric.GAUGE || w == Metric.LINE;
            if (b == 3) return geometry && get(w) >= w.ial * 1.3 ? 10 : 30;
            return geometry ? 50 : 80;
        }
    }

    public final Map<Long, Seg> segs = new HashMap<>();
    /** asset register for the console: pos -> {kind, columns...}; kinds PM, ST, TN, LX */
    public final Map<Long, String[]> assets = new LinkedHashMap<>();
    /** survey alignments exported from the surveyor's instrument: name -> NBT (points, profile) */
    public final Map<String, NBTTagCompound> surveys = new LinkedHashMap<>();
    /** bump to make the console re-read */
    public int version;

    public TrackData() { super(NAME); }
    public TrackData(String name) { super(name); }

    public static TrackData get(World world) {
        MapStorage storage = world.getPerWorldStorage();
        TrackData d = (TrackData) storage.getOrLoadData(TrackData.class, NAME);
        if (d == null) { d = new TrackData(); storage.setData(NAME, d); }
        return d;
    }

    public Seg seg(BlockPos p) { return segs.computeIfAbsent(p.toLong(), k -> new Seg()); }
    public Seg peek(BlockPos p) { return segs.get(p.toLong()); }

    public void asset(BlockPos p, String... row) {
        String[] old = assets.get(p.toLong());
        if (old != null && java.util.Arrays.equals(old, row)) return;
        assets.put(p.toLong(), row);
        version++;
        markDirty();
    }

    public void unasset(BlockPos p) { if (assets.remove(p.toLong()) != null) { version++; markDirty(); } }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        segs.clear();
        NBTTagList l = nbt.getTagList("segs", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound t = l.getCompoundTagAt(i);
            Seg s = new Seg();
            int[] v = t.getIntArray("v");                                   // hundredths
            for (int k = 0; k < Math.min(v.length, s.v.length); k++) s.v[k] = v[k] / 100.0;
            if (t.hasKey("seen")) { int[] sv = t.getIntArray("seen"); s.seen = new double[s.v.length]; for (int k = 0; k < Math.min(sv.length, s.seen.length); k++) s.seen[k] = sv[k] / 100.0; }
            s.tonnes = t.getDouble("t");
            s.maxKmh = t.getInteger("kmh");
            s.radius = t.getInteger("r");
            s.gaugeMm = t.hasKey("g") ? t.getInteger("g") : 1435;
            s.lengthM = t.getInteger("len");
            s.inspectedAt = t.getLong("ins");
            s.inspectedBy = t.getString("insBy");
            s.workedAt = t.getLong("wk");
            s.lastWork = t.getString("work");
            s.std = t.getInteger("std"); s.overloads = t.getInteger("ovl"); s.overspeeds = t.getInteger("ovs");   // #10 (absent = ballasted)
            segs.put(t.getLong("p"), s);
        }
        assets.clear();
        NBTTagList al = nbt.getTagList("assets", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < al.tagCount(); i++) {
            NBTTagCompound t = al.getCompoundTagAt(i);
            NBTTagList cols = t.getTagList("c", Constants.NBT.TAG_STRING);
            String[] row = new String[cols.tagCount()];
            for (int k = 0; k < row.length; k++) row[k] = cols.getStringTagAt(k);
            assets.put(t.getLong("p"), row);
        }
        surveys.clear();
        NBTTagCompound sv = nbt.getCompoundTag("surveys");
        for (String k : sv.getKeySet()) surveys.put(k, sv.getCompoundTag(k));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList l = new NBTTagList();
        for (Map.Entry<Long, Seg> e : segs.entrySet()) {
            Seg s = e.getValue();
            NBTTagCompound t = new NBTTagCompound();
            t.setLong("p", e.getKey());
            int[] v = new int[s.v.length];
            for (int k = 0; k < v.length; k++) v[k] = (int) Math.round(s.v[k] * 100);
            t.setIntArray("v", v);
            if (s.seen != null) { int[] sv = new int[s.seen.length]; for (int k = 0; k < sv.length; k++) sv[k] = (int) Math.round(s.seen[k] * 100); t.setIntArray("seen", sv); }
            t.setDouble("t", s.tonnes);
            t.setInteger("kmh", s.maxKmh);
            t.setInteger("r", s.radius);
            t.setInteger("g", s.gaugeMm);
            t.setInteger("len", s.lengthM);
            t.setLong("ins", s.inspectedAt);
            t.setString("insBy", s.inspectedBy);
            t.setLong("wk", s.workedAt);
            t.setString("work", s.lastWork);
            if (s.std != 0) t.setInteger("std", s.std);
            if (s.overloads != 0) t.setInteger("ovl", s.overloads);
            if (s.overspeeds != 0) t.setInteger("ovs", s.overspeeds);
            l.appendTag(t);
        }
        nbt.setTag("segs", l);
        NBTTagList al = new NBTTagList();
        for (Map.Entry<Long, String[]> e : assets.entrySet()) {
            NBTTagCompound t = new NBTTagCompound();
            t.setLong("p", e.getKey());
            NBTTagList cols = new NBTTagList();
            for (String c : e.getValue()) cols.appendTag(new net.minecraft.nbt.NBTTagString(c == null ? "" : c));
            t.setTag("c", cols);
            al.appendTag(t);
        }
        nbt.setTag("assets", al);
        NBTTagCompound sv = new NBTTagCompound();
        for (Map.Entry<String, NBTTagCompound> e : surveys.entrySet()) sv.setTag(e.getKey(), e.getValue());
        nbt.setTag("surveys", sv);
        return nbt;
    }
}
