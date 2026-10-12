package com.dogpound.railmap.ride;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Every ropeway / funicular line in one dimension: what it is (stations, supports, linked machine / desk / tension weight) and its running state. */
public final class RideData extends WorldSavedData {
    private static final String NAME = "irextras_ride";

    /** annunciator bits */
    public static final int F_ESTOP = 1, F_WIND = 2, F_RPD = 4, F_SUPPLY = 8, F_DOORS = 16, F_OVERSPEED = 32, F_TENSION = 64, F_MOTOR = 128, F_DRIVE = 256;
    public static final String[] F_NAMES = {"E-STOP", "WIND", "ROPE POS", "SUPPLY", "DOORS", "OVERSPEED", "TENSION", "MOTOR", "DRIVE"};
    /** shuttle phases */
    public static final int DWELL = 0, CLOSING = 1, RUN = 2;

    public static final class Line {
        public int id;
        public String name = "";
        public int kind;
        public final List<double[]> supports = new ArrayList<>();
        public final List<Long> pylons = new ArrayList<>();
        public long stA, stB, machine, desk, tension;
        // ---- state ----
        public boolean running, ebrake, sbrake = true, hpu;
        /** latched faults (annunciator) - RESET clears what has gone */
        public int faults;
        /** shuttle: car 0's distance from A; loop: rope distance run (bullwheel turns) */
        public double s, v, ropeRun;
        public int dir = 1, phase = DWELL, dwell;
        public double[] w = new double[0];
        /** per station 0 = A, 1 = B: gate open, attendant ready, cabin hold */
        public final boolean[] gate = new boolean[2], ready = new boolean[2], hold = new boolean[2];
        public int cabins = 6;
        // ---- live readings (not saved) ----
        public transient double wind, rpdMm, kw, amps, motorC = 20, oilC = 20, tensionKn, carriage = 50, setV, vMax, accel;
        // ---- inputs the tiles give every tick (not saved: the tiles say them again) ----
        /** desk: wind limit m/s, mode 0 AUTO / 1 MANUAL / 2 EVAC; machine: drive healthy, LOCAL key, supply volts */
        public transient double windLimit = 15, supplyV = 480, windSet = -1;
        public transient int mode;
        public transient boolean driveOk, local, brakeRel;
        /** E-stops pushed in: 1 desk, 2 machine room, 4 station A, 8 station B */
        public transient int estops;
        public transient String driveWhy = "no machine room";
        /** the tension weight's monitor last reported (and had 12 V) - a lost signal trips the line like a carriage at its limit */
        public transient long tensionSeen = -1000;
        public transient boolean tensionPowered;
        public transient int rpdTower = -1, riders, overload;
        public transient long machineSeen = -1000, deskSeen = -1000;
        public transient String why = "";
        public transient double[] carV = new double[0];
        public transient int[] carRiders = new int[0];
        private transient RideGeom geom;
        private transient int geomKey;

        public boolean shuttle() { return kind != RideGeom.GONDOLA; }

        public int cars() { return shuttle() ? 2 : Math.max(2, Math.min(RideConfig.maxCabins, cabins)); }

        public boolean hasTension() { return tension != 0; }

        public RideGeom geom() {
            int key = supports.size() * 31 + kind * 7 + (hasTension() ? 1 : 0);
            for (double[] p : supports) key = key * 17 + Double.hashCode(p[0] + p[1] * 3 + p[2] * 7);
            if (geom == null || key != geomKey) { geom = supports.size() < 2 ? null : new RideGeom(kind, supports, hasTension() ? 0.02 : 0.035); geomKey = key; }
            return geom;
        }

        /** the car / cabin's own coordinate (u for a shuttle, loop w for a gondola) */
        public double carCoord(int i) {
            if (shuttle()) return i == 0 ? s : geom() == null ? 0 : geom().length - s;
            return i < w.length ? w[i] : 0;
        }

        /** pose {x, y, z, yaw, pitch} of car i */
        public double[] carPose(int i) {
            RideGeom g = geom();
            if (g == null) return null;
            if (shuttle()) return g.carPose(carCoord(i), i == 0 ? 1 : -1, (i == 0 ? 1 : -1) * (dir == 0 ? 1 : dir));
            return g.loopPose(carCoord(i));
        }

        /** car i's doors open (riders may get in / out) */
        public boolean doorsOpen(int i) {
            RideGeom g = geom();
            if (g == null) return true;
            if (shuttle()) return Math.abs(v) < 0.01 && phase == DWELL && gate[atStation(i)] && atEnd();
            double[] p = g.loopPlace(carCoord(i));
            return p[2] > 0.15 && p[2] < 0.85 && (i >= carV.length || carV[i] < 0.6);
        }

        /** the station car i is at or nearest (0 = A, 1 = B) */
        public int atStation(int i) {
            RideGeom g = geom();
            if (g == null) return 0;
            if (shuttle()) return carCoord(i) < g.length / 2 ? 0 : 1;
            double[] p = g.loopPlace(carCoord(i));
            return p[3] >= 0 ? (int) p[3] : p[0] < g.length / 2 ? 0 : 1;
        }

        public boolean atEnd() { RideGeom g = geom(); return g == null || s < 0.05 || s > g.length - 0.05; }

        public BlockPos station(int k) { return BlockPos.fromLong(k == 0 ? stA : stB); }

        public String label() { return name.isEmpty() ? RideGeom.KIND[kind] + " " + id : name; }

        void write(NBTTagCompound t) {
            t.setInteger("id", id); t.setString("name", name); t.setByte("kind", (byte) kind);
            NBTTagList sl = new NBTTagList();
            for (double[] p : supports) { NBTTagCompound c = new NBTTagCompound(); c.setDouble("x", p[0]); c.setDouble("y", p[1]); c.setDouble("z", p[2]); sl.appendTag(c); }
            t.setTag("sup", sl);
            NBTTagList py = new NBTTagList();
            for (long p : pylons) py.appendTag(new net.minecraft.nbt.NBTTagLong(p));
            t.setTag("pyl", py);
            t.setLong("stA", stA); t.setLong("stB", stB); t.setLong("mach", machine); t.setLong("desk", desk); t.setLong("tens", tension);
            t.setBoolean("run", running); t.setBoolean("eb", ebrake); t.setBoolean("sb", sbrake); t.setBoolean("hpu", hpu);
            t.setInteger("faults", faults);
            t.setDouble("s", s); t.setDouble("v", v); t.setDouble("rr", ropeRun);
            t.setByte("dir", (byte) dir); t.setByte("phase", (byte) phase); t.setInteger("dwell", dwell);
            NBTTagList wl = new NBTTagList();
            for (double x : w) wl.appendTag(new net.minecraft.nbt.NBTTagDouble(x));
            t.setTag("w", wl);
            for (int k = 0; k < 2; k++) { t.setBoolean("gate" + k, gate[k]); t.setBoolean("ready" + k, ready[k]); t.setBoolean("hold" + k, hold[k]); }
            t.setInteger("cabins", cabins);
        }

        void read(NBTTagCompound t) {
            id = t.getInteger("id"); name = t.getString("name"); kind = t.getByte("kind");
            supports.clear();
            NBTTagList sl = t.getTagList("sup", 10);
            for (int i = 0; i < sl.tagCount(); i++) { NBTTagCompound c = sl.getCompoundTagAt(i); supports.add(new double[]{c.getDouble("x"), c.getDouble("y"), c.getDouble("z")}); }
            pylons.clear();
            NBTTagList py = t.getTagList("pyl", 4);
            for (int i = 0; i < py.tagCount(); i++) pylons.add(((net.minecraft.nbt.NBTTagLong) py.get(i)).getLong());
            stA = t.getLong("stA"); stB = t.getLong("stB"); machine = t.getLong("mach"); desk = t.getLong("desk"); tension = t.getLong("tens");
            running = t.getBoolean("run"); ebrake = t.getBoolean("eb"); sbrake = !t.hasKey("sb") || t.getBoolean("sb"); hpu = t.getBoolean("hpu");
            faults = t.getInteger("faults");
            s = t.getDouble("s"); v = t.getDouble("v"); ropeRun = t.getDouble("rr");
            dir = t.getByte("dir") == 0 ? 1 : t.getByte("dir"); phase = t.getByte("phase"); dwell = t.getInteger("dwell");
            NBTTagList wl = t.getTagList("w", 6);
            w = new double[wl.tagCount()];
            for (int i = 0; i < w.length; i++) w[i] = wl.getDoubleAt(i);
            for (int k = 0; k < 2; k++) { gate[k] = t.getBoolean("gate" + k); ready[k] = t.getBoolean("ready" + k); hold[k] = t.getBoolean("hold" + k); }
            cabins = t.hasKey("cabins") ? t.getInteger("cabins") : 6;
        }
    }

    public final Map<Integer, Line> lines = new TreeMap<>();
    public int nextId = 1;
    public int version;

    public RideData() { super(NAME); }
    public RideData(String n) { super(n); }

    public static RideData get(World w) {
        MapStorage st = w.getPerWorldStorage();
        RideData d = (RideData) st.getOrLoadData(RideData.class, NAME);
        if (d == null) { d = new RideData(); st.setData(NAME, d); }
        return d;
    }

    public void changed() { version++; markDirty(); }

    public Line line(int id) { return lines.get(id); }

    public Line byStation(BlockPos p) {
        long l = p.toLong();
        for (Line x : lines.values()) if (x.stA == l || x.stB == l) return x;
        return null;
    }

    public Line byPart(BlockPos p) {
        long l = p.toLong();
        for (Line x : lines.values()) if (x.stA == l || x.stB == l || x.machine == l || x.desk == l || x.tension == l || x.pylons.contains(l)) return x;
        return null;
    }

    /** the line with a station nearest p within r blocks */
    public Line near(BlockPos p, double r) {
        Line best = null;
        double bd = r * r;
        for (Line x : lines.values()) for (int k = 0; k < 2; k++) {
            double d = x.station(k).distanceSq(p);
            if (d < bd) { bd = d; best = x; }
        }
        return best;
    }

    public Line add(int kind) {
        Line l = new Line();
        l.id = nextId++;
        l.kind = kind;
        lines.put(l.id, l);
        changed();
        return l;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        lines.clear();
        nextId = Math.max(1, t.getInteger("next"));
        NBTTagList l = t.getTagList("lines", 10);
        for (int i = 0; i < l.tagCount(); i++) { Line x = new Line(); x.read(l.getCompoundTagAt(i)); lines.put(x.id, x); nextId = Math.max(nextId, x.id + 1); }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        t.setInteger("next", nextId);
        NBTTagList l = new NBTTagList();
        for (Line x : lines.values()) { NBTTagCompound c = new NBTTagCompound(); x.write(c); l.appendTag(c); }
        t.setTag("lines", l);
        return t;
    }
}
