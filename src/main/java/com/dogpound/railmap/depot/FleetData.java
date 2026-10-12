package com.dogpound.railmap.depot;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.*;

/**
 * The railway's maintenance organisation (her ChatGPT list section 8 + #83 / #84), saved as irextras_fleet.dat:
 * per vehicle its operating hours, exam counters, wear trend, serial-numbered components and their history, role in
 * the fleet (service / spare / depot / stopped) and what it was doing before it went to the depot, brake test, and
 * how clean / watered / emptied / stocked it is. Plus the exam plans, the workshop job cards, saved diagnostic
 * downloads, line fleet targets and the crew roster. Anything it does not know about starts as new, so old worlds load
 * clean (nothing restricted, nothing due).
 */
public final class FleetData extends WorldSavedData {
    private static final String NAME = "irextras_fleet";

    /** components with serial numbers (#62): the five wear parts in WearData order, then bogies and couplers */
    public enum Kind {
        WHEELS("WS", "Wheelsets"), BRAKES("BB", "Brake blocks"), ENGINE("PU", "Power unit / motors"), BEARINGS("AB", "Axle bearings"),
        ELECTRICS("CE", "Control electronics"), BOGIE_A("BG", "Bogie A"), BOGIE_B("BG", "Bogie B"), COUPLERS("CK", "Couplers");
        public final String prefix, label;
        Kind(String prefix, String label) { this.prefix = prefix; this.label = label; }
    }

    public static final String[] ROLES = {"SERVICE", "SPARE", "DEPOT", "STOPPED"};
    public static final int SERVICE = 0, SPARE = 1, DEPOT = 2, STOPPED = 3;

    public static final class Comp {
        public String serial;
        public int kind;
        public long installTick;
        public double installKm;     // the vehicle's odometer when it went on
        public double priorKm;       // km it ran on other vehicles before (rotables: bogies)
        public int repairs;
        public float diameter;       // wheelsets: tread diameter mm

        public double life(double unitKm) { return priorKm + Math.max(0, unitKm - installKm); }

        NBTTagCompound save() {
            NBTTagCompound t = new NBTTagCompound();
            t.setString("s", serial); t.setInteger("k", kind); t.setLong("t", installTick); t.setDouble("km", installKm);
            t.setDouble("pk", priorKm); t.setInteger("r", repairs); t.setFloat("d", diameter);
            return t;
        }

        static Comp load(NBTTagCompound t) {
            Comp c = new Comp();
            c.serial = t.getString("s"); c.kind = t.getInteger("k"); c.installTick = t.getLong("t"); c.installKm = t.getDouble("km");
            c.priorKm = t.getDouble("pk"); c.repairs = t.getInteger("r"); c.diameter = t.getFloat("d");
            return c;
        }
    }

    public static final class Unit {
        public String name = "", def = "";
        public boolean loco, passenger, electric, steam;
        public double hours, km;
        public double examKm, examHours;
        public long examTick;
        public int faults, examFaults, faultSeen;
        public int role = SERVICE;
        public long depot;                        // the depot office it was sent to
        public int retMode = -1, retIndex, retDir = 1;
        public String retLine = "";
        public long retHome;
        public boolean brakeWork, brakeOk = true;  // brake work done since the last brake test / the last test passed
        public long brakeCert;
        public final int[] level = new int[5];     // condition level last acted on, per part
        public final List<float[]> trend = new ArrayList<>();   // {km, wear x5}
        public final Comp[] comps = new Comp[Kind.values().length];
        public final LinkedList<String> log = new LinkedList<>();
        public float interior = 100, exterior = 100, water = 100, waste = 0, catering = 100;
        public boolean dueAlarm, failed;
        public transient double[] rul = new double[5];   // remaining km per part (predictive), -1 unknown
        public transient float cap = Float.MAX_VALUE, brakeK = 1, derateK = 1, coolK = 1;
        public transient String capWhy = "";
        public transient long seenTick;
        // ---- her 48-next list #28: a rebuild design (-1 = as built per slot) and its acceptance; #5: telemetry history
        /** motor, gear ratio, cooling, storage, bogies, brakes (asset.Design catalogues), -1 = as built */
        public final int[] design = {-1, -1, -1, -1, -1, -1};
        /** 0 as built, 1 rebuilt - NOT ACCEPTED, 2 acceptance TEST RUN, 3 ACCEPTED, 4 FAILED its test */
        public int rebuild, rebuilds;
        public double testKm;
        public long testStart;
        public float testPeak, baseKmh;
        /** telemetry samples every 30 s: {operating hours, engine °C, traction motor °C, oil kPa, battery V, worst coupler %, active faults} */
        public final List<float[]> tele = new ArrayList<>();
        public transient List<String> pcodes = new ArrayList<>();

        public void note(World w, String text) {
            log.addFirst(DepotClock.stamp(w) + " " + text);
            while (log.size() > 30) log.removeLast();
        }
    }

    public static final class Job {
        public int id;
        public UUID unit;
        public String unitName = "", task = "", assignee = "", note = "";
        public int kind;           // Jobs.EXAM ...
        public int part = -1;      // the wear part a repair is for
        public int state;          // 0 open, 1 assigned, 2 in progress, 3 done, 4 cancelled
        public long created, started, done, depot;
        public int seconds;        // work time so far
        public final int[] spares = new int[ItemSpare.Spare.values().length];
    }

    public static final class Download {
        public int id;
        public String unit = "";
        public long tick;
        public final List<String> lines = new ArrayList<>();
    }

    public static final class Driver {
        public String name = "";
        public UUID player;                 // null = a depot (simulated) driver
        public int quals = 1;               // 1 diesel, 2 electric, 4 steam
        public final Set<String> routes = new LinkedHashSet<>();
        public boolean onDuty;
        public long dutyStart, drivingSince, breakUntil;
        public double drivenToday;          // game hours on this duty
        public UUID train;
    }

    private final Map<UUID, Unit> units = new HashMap<>();
    public final Map<String, int[]> plans = new LinkedHashMap<>();    // vehicle type -> {km, hours, days, faults}
    public final List<Job> jobs = new ArrayList<>();
    public final List<Download> downloads = new ArrayList<>();
    public final Map<String, Integer> lineTarget = new LinkedHashMap<>();  // line -> trains it should have (0 = not managed)
    public final Set<String> attended = new LinkedHashSet<>();            // lines that need a driver (#84)
    public final Map<String, Driver> drivers = new LinkedHashMap<>();
    public final LinkedList<String> crewLog = new LinkedList<>();
    public int nextJob = 1, nextDownload = 1, nextSerial = 1;

    public FleetData() { super(NAME); }
    public FleetData(String n) { super(n); }

    public static FleetData get(World w) {
        MapStorage s = w.getPerWorldStorage();
        FleetData d = (FleetData) s.getOrLoadData(FleetData.class, NAME);
        if (d == null) { d = new FleetData(); s.setData(NAME, d); }
        return d;
    }

    public Unit unit(UUID id) { return units.computeIfAbsent(id, k -> new Unit()); }
    public Unit peek(UUID id) { return units.get(id); }
    public Map<UUID, Unit> units() { return units; }

    public String serial(Kind k) { markDirty(); return String.format(Locale.ROOT, "%s-%05d", k.prefix, nextSerial++); }

    /** the component fitted in this slot (a new one is "fitted at build" the first time anyone looks) */
    public Comp comp(Unit u, Kind k, long now) {
        Comp c = u.comps[k.ordinal()];
        if (c == null) {
            c = new Comp();
            c.serial = serial(k);
            c.kind = k.ordinal();
            c.installTick = now;
            c.installKm = u.km;
            if (k == Kind.WHEELS) c.diameter = DepotConfig.wheelNewMm;
            u.comps[k.ordinal()] = c;
            markDirty();
        }
        return c;
    }

    /** fit a replacement: the old one's life goes in the log; returns the old one */
    public Comp replace(World w, Unit u, Kind k, String serial, double priorKm) {
        Comp old = comp(u, k, w.getTotalWorldTime());
        Comp c = new Comp();
        c.serial = serial == null ? serial(k) : serial;
        c.kind = k.ordinal();
        c.installTick = w.getTotalWorldTime();
        c.installKm = u.km;
        c.priorKm = priorKm;
        if (k == Kind.WHEELS) c.diameter = DepotConfig.wheelNewMm;
        u.comps[k.ordinal()] = c;
        u.note(w, String.format(Locale.ROOT, "%s %s off (%.1f km, %d repairs), %s on", k.label, old.serial, old.life(u.km), old.repairs, c.serial));
        markDirty();
        return old;
    }

    public int[] plan(Unit u) {
        int[] p = plans.get(u.def);
        if (p == null) p = plans.get("*");
        return p != null ? p : new int[]{DepotConfig.planKm, DepotConfig.planHours, DepotConfig.planDays, DepotConfig.planFaults};
    }

    public Job job(int id) { for (Job j : jobs) if (j.id == id) return j; return null; }

    public Job addJob(World w, UUID unit, String unitName, int kind, int part, String task, long depot) {
        Job j = new Job();
        j.id = nextJob++;
        j.unit = unit; j.unitName = unitName; j.kind = kind; j.part = part; j.task = task; j.depot = depot;
        j.created = w.getTotalWorldTime();
        Jobs.spares(j);
        jobs.add(j);
        while (jobs.size() > 80) { Job old = null; for (Job x : jobs) if (x.state >= 3) { old = x; break; } if (old == null) break; jobs.remove(old); }
        markDirty();
        return j;
    }

    public List<Job> openJobs(UUID unit) {
        List<Job> o = new ArrayList<>();
        for (Job j : jobs) if (j.state < 3 && (unit == null || unit.equals(j.unit))) o.add(j);
        return o;
    }

    public Download addDownload(World w, String unit, List<String> lines) {
        Download d = new Download();
        d.id = nextDownload++;
        d.unit = unit;
        d.tick = w.getTotalWorldTime();
        d.lines.addAll(lines);
        downloads.add(0, d);
        while (downloads.size() > 40) downloads.remove(downloads.size() - 1);
        markDirty();
        return d;
    }

    public void crewNote(World w, String text) {
        crewLog.addFirst(DepotClock.stamp(w) + " " + text);
        while (crewLog.size() > 40) crewLog.removeLast();
        markDirty();
    }

    // ---- save ---------------------------------------------------------------------------------------------------------
    private static NBTTagList strings(Collection<String> l) { NBTTagList t = new NBTTagList(); for (String s : l) t.appendTag(new NBTTagString(s)); return t; }
    private static void strings(NBTTagCompound t, String k, Collection<String> into) { NBTTagList l = t.getTagList(k, Constants.NBT.TAG_STRING); for (int i = 0; i < l.tagCount(); i++) into.add(l.getStringTagAt(i)); }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        units.clear(); plans.clear(); jobs.clear(); downloads.clear(); lineTarget.clear(); attended.clear(); drivers.clear(); crewLog.clear();
        nextJob = Math.max(1, t.getInteger("nj")); nextDownload = Math.max(1, t.getInteger("nd")); nextSerial = Math.max(1, t.getInteger("ns"));
        NBTTagList ul = t.getTagList("units", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < ul.tagCount(); i++) {
            NBTTagCompound c = ul.getCompoundTagAt(i);
            Unit u = new Unit();
            u.name = c.getString("n"); u.def = c.getString("d");
            u.loco = c.getBoolean("lo"); u.passenger = c.getBoolean("pa"); u.electric = c.getBoolean("el"); u.steam = c.getBoolean("st");
            u.hours = c.getDouble("h"); u.km = c.getDouble("km");
            u.examKm = c.getDouble("ek"); u.examHours = c.getDouble("eh"); u.examTick = c.getLong("et"); u.examFaults = c.getInteger("ef");
            u.faults = c.getInteger("f"); u.faultSeen = c.getInteger("fs");
            u.role = c.getInteger("role"); u.depot = c.getLong("dep");
            u.retMode = c.hasKey("rm") ? c.getInteger("rm") : -1; u.retIndex = c.getInteger("ri"); u.retDir = c.hasKey("rd") ? c.getInteger("rd") : 1;
            u.retLine = c.getString("rl"); u.retHome = c.getLong("rh");
            u.brakeWork = c.getBoolean("bw"); u.brakeOk = !c.hasKey("bo") || c.getBoolean("bo"); u.brakeCert = c.getLong("bc");
            int[] lv = c.getIntArray("lv");
            for (int k = 0; k < Math.min(lv.length, u.level.length); k++) u.level[k] = lv[k];
            NBTTagList tr = c.getTagList("tr", Constants.NBT.TAG_INT_ARRAY);
            for (int k = 0; k < tr.tagCount(); k++) { int[] a = tr.getIntArrayAt(k); float[] f = new float[6]; for (int q = 0; q < Math.min(6, a.length); q++) f[q] = a[q] / 100f; u.trend.add(f); }
            NBTTagCompound cp = c.getCompoundTag("cp");
            for (Kind k : Kind.values()) if (cp.hasKey(k.name())) u.comps[k.ordinal()] = Comp.load(cp.getCompoundTag(k.name()));
            strings(c, "log", u.log);
            if (c.hasKey("in")) { u.interior = c.getFloat("in"); u.exterior = c.getFloat("ex"); u.water = c.getFloat("wa"); u.waste = c.getFloat("ws"); u.catering = c.getFloat("ca"); }
            u.dueAlarm = c.getBoolean("da"); u.failed = c.getBoolean("fl");
            int[] dz = c.getIntArray("dz");
            for (int k = 0; k < Math.min(dz.length, u.design.length); k++) u.design[k] = dz[k];
            u.rebuild = c.getInteger("rb"); u.rebuilds = c.getInteger("rbn"); u.testKm = c.getDouble("tk"); u.testStart = c.getLong("ts"); u.testPeak = c.getFloat("tp"); u.baseKmh = c.getFloat("bk");
            NBTTagList tl = c.getTagList("tl", Constants.NBT.TAG_INT_ARRAY);
            for (int k = 0; k < tl.tagCount(); k++) { int[] a = tl.getIntArrayAt(k); float[] f = new float[7]; for (int q = 0; q < Math.min(7, a.length); q++) f[q] = a[q] / 10f; u.tele.add(f); }
            units.put(new UUID(c.getLong("hi"), c.getLong("lo2")), u);
        }
        NBTTagCompound pl = t.getCompoundTag("plans");
        for (String k : pl.getKeySet()) plans.put(k, pl.getIntArray(k));
        NBTTagList jl = t.getTagList("jobs", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < jl.tagCount(); i++) {
            NBTTagCompound c = jl.getCompoundTagAt(i);
            Job j = new Job();
            j.id = c.getInteger("id"); j.unit = c.hasUniqueId("u") ? c.getUniqueId("u") : null; j.unitName = c.getString("un");
            j.task = c.getString("t"); j.assignee = c.getString("as"); j.note = c.getString("no");
            j.kind = c.getInteger("k"); j.part = c.getInteger("p"); j.state = c.getInteger("s");
            j.created = c.getLong("c"); j.started = c.getLong("st"); j.done = c.getLong("d"); j.depot = c.getLong("dep"); j.seconds = c.getInteger("sec");
            int[] sp = c.getIntArray("sp");
            for (int k = 0; k < Math.min(sp.length, j.spares.length); k++) j.spares[k] = sp[k];
            jobs.add(j);
        }
        NBTTagList dl = t.getTagList("dl", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < dl.tagCount(); i++) {
            NBTTagCompound c = dl.getCompoundTagAt(i);
            Download d = new Download();
            d.id = c.getInteger("id"); d.unit = c.getString("u"); d.tick = c.getLong("t");
            strings(c, "l", d.lines);
            downloads.add(d);
        }
        NBTTagCompound lt = t.getCompoundTag("lt");
        for (String k : lt.getKeySet()) lineTarget.put(k, lt.getInteger(k));
        strings(t, "att", attended);
        NBTTagList dr = t.getTagList("drv", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < dr.tagCount(); i++) {
            NBTTagCompound c = dr.getCompoundTagAt(i);
            Driver d = new Driver();
            d.name = c.getString("n"); d.player = c.hasUniqueId("p") ? c.getUniqueId("p") : null; d.quals = c.getInteger("q");
            strings(c, "r", d.routes);
            d.onDuty = c.getBoolean("on"); d.dutyStart = c.getLong("ds"); d.drivingSince = c.getLong("dv"); d.breakUntil = c.getLong("bu");
            d.drivenToday = c.getDouble("dt"); d.train = c.hasUniqueId("t") ? c.getUniqueId("t") : null;
            drivers.put(d.name, d);
        }
        strings(t, "clog", crewLog);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        t.setInteger("nj", nextJob); t.setInteger("nd", nextDownload); t.setInteger("ns", nextSerial);
        NBTTagList ul = new NBTTagList();
        for (Map.Entry<UUID, Unit> e : units.entrySet()) {
            Unit u = e.getValue();
            NBTTagCompound c = new NBTTagCompound();
            c.setLong("hi", e.getKey().getMostSignificantBits()); c.setLong("lo2", e.getKey().getLeastSignificantBits());
            c.setString("n", u.name); c.setString("d", u.def);
            c.setBoolean("lo", u.loco); c.setBoolean("pa", u.passenger); c.setBoolean("el", u.electric); c.setBoolean("st", u.steam);
            c.setDouble("h", u.hours); c.setDouble("km", u.km);
            c.setDouble("ek", u.examKm); c.setDouble("eh", u.examHours); c.setLong("et", u.examTick); c.setInteger("ef", u.examFaults);
            c.setInteger("f", u.faults); c.setInteger("fs", u.faultSeen);
            c.setInteger("role", u.role); c.setLong("dep", u.depot);
            c.setInteger("rm", u.retMode); c.setInteger("ri", u.retIndex); c.setInteger("rd", u.retDir); c.setString("rl", u.retLine); c.setLong("rh", u.retHome);
            c.setBoolean("bw", u.brakeWork); c.setBoolean("bo", u.brakeOk); c.setLong("bc", u.brakeCert);
            c.setIntArray("lv", u.level.clone());
            NBTTagList tr = new NBTTagList();
            for (float[] f : u.trend) { int[] a = new int[f.length]; for (int q = 0; q < f.length; q++) a[q] = Math.round(f[q] * 100); tr.appendTag(new net.minecraft.nbt.NBTTagIntArray(a)); }
            c.setTag("tr", tr);
            NBTTagCompound cp = new NBTTagCompound();
            for (Kind k : Kind.values()) if (u.comps[k.ordinal()] != null) cp.setTag(k.name(), u.comps[k.ordinal()].save());
            c.setTag("cp", cp);
            c.setTag("log", strings(u.log));
            c.setFloat("in", u.interior); c.setFloat("ex", u.exterior); c.setFloat("wa", u.water); c.setFloat("ws", u.waste); c.setFloat("ca", u.catering);
            c.setBoolean("da", u.dueAlarm); c.setBoolean("fl", u.failed);
            if (u.rebuild != 0 || u.rebuilds != 0) {
                c.setIntArray("dz", u.design.clone());
                c.setInteger("rb", u.rebuild); c.setInteger("rbn", u.rebuilds); c.setDouble("tk", u.testKm); c.setLong("ts", u.testStart); c.setFloat("tp", u.testPeak); c.setFloat("bk", u.baseKmh);
            }
            if (!u.tele.isEmpty()) {
                NBTTagList tl = new NBTTagList();
                for (float[] f : u.tele) { int[] a = new int[f.length]; for (int q = 0; q < f.length; q++) a[q] = Math.round(f[q] * 10); tl.appendTag(new net.minecraft.nbt.NBTTagIntArray(a)); }
                c.setTag("tl", tl);
            }
            ul.appendTag(c);
        }
        t.setTag("units", ul);
        NBTTagCompound pl = new NBTTagCompound();
        for (Map.Entry<String, int[]> e : plans.entrySet()) pl.setIntArray(e.getKey(), e.getValue());
        t.setTag("plans", pl);
        NBTTagList jl = new NBTTagList();
        for (Job j : jobs) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("id", j.id); if (j.unit != null) c.setUniqueId("u", j.unit); c.setString("un", j.unitName);
            c.setString("t", j.task); c.setString("as", j.assignee); c.setString("no", j.note);
            c.setInteger("k", j.kind); c.setInteger("p", j.part); c.setInteger("s", j.state);
            c.setLong("c", j.created); c.setLong("st", j.started); c.setLong("d", j.done); c.setLong("dep", j.depot); c.setInteger("sec", j.seconds);
            c.setIntArray("sp", j.spares.clone());
            jl.appendTag(c);
        }
        t.setTag("jobs", jl);
        NBTTagList dl = new NBTTagList();
        for (Download d : downloads) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("id", d.id); c.setString("u", d.unit); c.setLong("t", d.tick); c.setTag("l", strings(d.lines));
            dl.appendTag(c);
        }
        t.setTag("dl", dl);
        NBTTagCompound lt = new NBTTagCompound();
        for (Map.Entry<String, Integer> e : lineTarget.entrySet()) lt.setInteger(e.getKey(), e.getValue());
        t.setTag("lt", lt);
        t.setTag("att", strings(attended));
        NBTTagList dr = new NBTTagList();
        for (Driver d : drivers.values()) {
            NBTTagCompound c = new NBTTagCompound();
            c.setString("n", d.name); if (d.player != null) c.setUniqueId("p", d.player); c.setInteger("q", d.quals);
            c.setTag("r", strings(d.routes));
            c.setBoolean("on", d.onDuty); c.setLong("ds", d.dutyStart); c.setLong("dv", d.drivingSince); c.setLong("bu", d.breakUntil);
            c.setDouble("dt", d.drivenToday); if (d.train != null) c.setUniqueId("t", d.train);
            dr.appendTag(c);
        }
        t.setTag("drv", dr);
        t.setTag("clog", strings(crewLog));
        return t;
    }
}
