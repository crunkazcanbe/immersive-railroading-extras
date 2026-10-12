package com.dogpound.railmap.infra;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.*;

/**
 * Saved state for her ChatGPT list batch 3 (#36-50): OHLE fault-location records and their inspection crews, relay
 * calibration records, OHLE recording runs and exceedances, ultrasonic test-train runs, possession work plans, graded
 * drill results and the infrastructure log. New file, new keys: an old world simply starts with all of it empty.
 */
public final class InfraData extends WorldSavedData {
    private static final String NAME = "irextras_infra";

    // ---- #39 fault location ------------------------------------------------------------------------------------------
    public static final class FaultRec {
        public int id;
        public long at, feeder, feeder2, pos, est, est2, found;
        public String kind = "", why = "", section = "";
        /** true wire distance from feeder 1 (m), the single-ended estimate and uncertainty, the double-ended estimate */
        public double dist, total, estD, unc, estD2 = -1, unc2;
        public double r, x, ika, rf;
        public boolean persistent, acked;
        /** crew: 0 none, 1 driving out, 2 searching, 3 found, 4 waiting for isolation + earth, 5 repairing, 6 done */
        public int crew;
        public long crewAt, crewDue;
        public String crewNote = "";

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("id", id); t.setLong("at", at); t.setLong("f", feeder); t.setLong("f2", feeder2); t.setLong("p", pos); t.setLong("e", est); t.setLong("e2", est2); t.setLong("fd", found);
            t.setString("k", kind); t.setString("w", why); t.setString("s", section);
            t.setDouble("d", dist); t.setDouble("tot", total); t.setDouble("ed", estD); t.setDouble("u", unc); t.setDouble("ed2", estD2); t.setDouble("u2", unc2);
            t.setDouble("r", r); t.setDouble("x", x); t.setDouble("i", ika); t.setDouble("rf", rf);
            t.setBoolean("pe", persistent); t.setBoolean("ak", acked); t.setInteger("c", crew); t.setLong("ca", crewAt); t.setLong("cd", crewDue); t.setString("cn", crewNote);
            return t;
        }

        static FaultRec read(NBTTagCompound t) {
            FaultRec f = new FaultRec();
            f.id = t.getInteger("id"); f.at = t.getLong("at"); f.feeder = t.getLong("f"); f.feeder2 = t.getLong("f2"); f.pos = t.getLong("p"); f.est = t.getLong("e"); f.est2 = t.getLong("e2"); f.found = t.getLong("fd");
            f.kind = t.getString("k"); f.why = t.getString("w"); f.section = t.getString("s");
            f.dist = t.getDouble("d"); f.total = t.getDouble("tot"); f.estD = t.getDouble("ed"); f.unc = t.getDouble("u"); f.estD2 = t.hasKey("ed2") ? t.getDouble("ed2") : -1; f.unc2 = t.getDouble("u2");
            f.r = t.getDouble("r"); f.x = t.getDouble("x"); f.ika = t.getDouble("i"); f.rf = t.getDouble("rf");
            f.persistent = t.getBoolean("pe"); f.acked = t.getBoolean("ak"); f.crew = t.getInteger("c"); f.crewAt = t.getLong("ca"); f.crewDue = t.getLong("cd"); f.crewNote = t.getString("cn");
            return f;
        }
    }

    // ---- #40 relay calibration ---------------------------------------------------------------------------------------
    public static final class Cal {
        /** pick-up and operating-time error, fractions (+0.03 = 3 % high / slow) */
        public double pick, time;
        public long calAt, testAt, lastDrift;
        public boolean lastPass = true;
        public int tests, cals;
    }

    // ---- #44 OHLE recording ------------------------------------------------------------------------------------------
    public static final class Run {
        public int id, exceed;
        public String vehicle = "", cls = "STANDARD";
        public long start, end;
        public double metres, hMin = 99, hMax, stgMax, wearMax, vMax;
    }

    public static final class Exc {
        public long pos;
        public int run;
        public String what = "";
        public double value;
    }

    // ---- #45 possession plans ----------------------------------------------------------------------------------------
    public static final class Task {
        public String kind = "", what = "";
        public long pos;
        /** seconds of gang time it needs, seconds done */
        public double secs, done;
        /** 0 waiting, 1 working, 2 done, 3 blocked, 4 deferred */
        public int state;
        public String note = "";
        public boolean ohle;
    }

    public static final class Plan {
        public int work;
        public long made;
        public double impactDelay, impactDeps;
        public String window = "", lines = "";
        public boolean overrunWarned;
        public final List<Task> tasks = new ArrayList<>();
    }

    public final LinkedList<String> log = new LinkedList<>();
    public final List<FaultRec> faults = new ArrayList<>();
    public final Map<Long, Cal> cal = new HashMap<>();
    public final LinkedList<String> certs = new LinkedList<>();
    public final Map<Long, double[]> rec = new HashMap<>();          // wire pos -> {height m, stagger m, wear, tick, run}
    public final List<Run> runs = new ArrayList<>();
    public final List<Exc> exc = new ArrayList<>();
    public final LinkedList<String> utLog = new LinkedList<>();
    public final Map<Integer, Plan> plans = new LinkedHashMap<>();
    public final Map<String, String> bestGrade = new LinkedHashMap<>();
    public final LinkedList<String[]> grades = new LinkedList<>();
    public final Map<Long, Integer> floodTsr = new HashMap<>();       // weather cell key -> TempWorks id of its precautionary TSR
    public int nextFault = 1, nextRun = 1;

    public InfraData() { super(NAME); }
    public InfraData(String name) { super(name); }

    public static InfraData get(World w) {
        MapStorage s = w.getPerWorldStorage();
        InfraData d = (InfraData) s.getOrLoadData(InfraData.class, NAME);
        if (d == null) { d = new InfraData(); s.setData(NAME, d); }
        return d;
    }

    public void log(World w, String s) {
        log.addFirst(com.dogpound.railmap.alarm.Recorder.clock(w, w.getTotalWorldTime()) + " " + s);
        while (log.size() > 80) log.removeLast();
        markDirty();
    }

    public Cal cal(long ied) { return cal.computeIfAbsent(ied, k -> new Cal()); }

    public Run run(int id) { for (Run r : runs) if (r.id == id) return r; return null; }

    public FaultRec fault(int id) { for (FaultRec f : faults) if (f.id == id) return f; return null; }

    // ---- save ----------------------------------------------------------------------------------------------------------
    private static NBTTagList strings(Collection<String> s) {
        NBTTagList l = new NBTTagList();
        for (String x : s) l.appendTag(new net.minecraft.nbt.NBTTagString(x));
        return l;
    }

    private static void strings(NBTTagCompound t, String k, Collection<String> into) {
        NBTTagList l = t.getTagList(k, 8);
        for (int i = 0; i < l.tagCount(); i++) into.add(l.getStringTagAt(i));
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        strings(t, "log", log);
        strings(t, "certs", certs);
        strings(t, "ut", utLog);
        NBTTagList l = t.getTagList("faults", 10);
        for (int i = 0; i < l.tagCount(); i++) faults.add(FaultRec.read(l.getCompoundTagAt(i)));
        l = t.getTagList("cal", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            Cal x = new Cal();
            x.pick = c.getDouble("p"); x.time = c.getDouble("t"); x.calAt = c.getLong("ca"); x.testAt = c.getLong("ta"); x.lastDrift = c.getLong("ld");
            x.lastPass = !c.hasKey("lp") || c.getBoolean("lp"); x.tests = c.getInteger("n"); x.cals = c.getInteger("nc");
            cal.put(c.getLong("pos"), x);
        }
        l = t.getTagList("rec", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            rec.put(c.getLong("p"), new double[]{c.getDouble("h"), c.getDouble("s"), c.getDouble("w"), c.getDouble("t"), c.getDouble("r")});
        }
        l = t.getTagList("runs", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            Run r = new Run();
            r.id = c.getInteger("id"); r.exceed = c.getInteger("x"); r.vehicle = c.getString("v"); r.cls = c.getString("c"); r.start = c.getLong("s"); r.end = c.getLong("e");
            r.metres = c.getDouble("m"); r.hMin = c.getDouble("h0"); r.hMax = c.getDouble("h1"); r.stgMax = c.getDouble("sg"); r.wearMax = c.getDouble("wr"); r.vMax = c.getDouble("v1");
            runs.add(r);
        }
        l = t.getTagList("exc", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            Exc e = new Exc();
            e.pos = c.getLong("p"); e.run = c.getInteger("r"); e.what = c.getString("w"); e.value = c.getDouble("v");
            exc.add(e);
        }
        l = t.getTagList("plans", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            Plan p = new Plan();
            p.work = c.getInteger("w"); p.made = c.getLong("m"); p.impactDelay = c.getDouble("id"); p.impactDeps = c.getDouble("ip");
            p.window = c.getString("win"); p.lines = c.getString("l"); p.overrunWarned = c.getBoolean("ow");
            NBTTagList tl = c.getTagList("t", 10);
            for (int j = 0; j < tl.tagCount(); j++) {
                NBTTagCompound x = tl.getCompoundTagAt(j);
                Task k = new Task();
                k.kind = x.getString("k"); k.what = x.getString("w"); k.pos = x.getLong("p"); k.secs = x.getDouble("s"); k.done = x.getDouble("d");
                k.state = x.getInteger("st"); k.note = x.getString("n"); k.ohle = x.getBoolean("o");
                p.tasks.add(k);
            }
            plans.put(p.work, p);
        }
        NBTTagCompound g = t.getCompoundTag("best");
        for (String k : g.getKeySet()) bestGrade.put(k, g.getString(k));
        l = t.getTagList("grades", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            grades.add(new String[]{c.getString("a"), c.getString("b"), c.getString("c"), c.getString("d"), c.getString("e")});
        }
        NBTTagCompound ft = t.getCompoundTag("ftsr");
        for (String k : ft.getKeySet()) try { floodTsr.put(Long.parseLong(k), ft.getInteger(k)); } catch (NumberFormatException ignored) { }
        nextFault = Math.max(1, t.getInteger("nf"));
        nextRun = Math.max(1, t.getInteger("nr"));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        t.setTag("log", strings(log));
        t.setTag("certs", strings(certs));
        t.setTag("ut", strings(utLog));
        NBTTagList l = new NBTTagList();
        for (FaultRec f : faults) l.appendTag(f.write());
        t.setTag("faults", l);
        l = new NBTTagList();
        for (Map.Entry<Long, Cal> e : cal.entrySet()) {
            NBTTagCompound c = new NBTTagCompound();
            Cal x = e.getValue();
            c.setLong("pos", e.getKey()); c.setDouble("p", x.pick); c.setDouble("t", x.time); c.setLong("ca", x.calAt); c.setLong("ta", x.testAt); c.setLong("ld", x.lastDrift);
            c.setBoolean("lp", x.lastPass); c.setInteger("n", x.tests); c.setInteger("nc", x.cals);
            l.appendTag(c);
        }
        t.setTag("cal", l);
        l = new NBTTagList();
        for (Map.Entry<Long, double[]> e : rec.entrySet()) {
            NBTTagCompound c = new NBTTagCompound();
            double[] v = e.getValue();
            c.setLong("p", e.getKey()); c.setDouble("h", v[0]); c.setDouble("s", v[1]); c.setDouble("w", v[2]); c.setDouble("t", v[3]); c.setDouble("r", v[4]);
            l.appendTag(c);
        }
        t.setTag("rec", l);
        l = new NBTTagList();
        for (Run r : runs) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("id", r.id); c.setInteger("x", r.exceed); c.setString("v", r.vehicle); c.setString("c", r.cls); c.setLong("s", r.start); c.setLong("e", r.end);
            c.setDouble("m", r.metres); c.setDouble("h0", r.hMin); c.setDouble("h1", r.hMax); c.setDouble("sg", r.stgMax); c.setDouble("wr", r.wearMax); c.setDouble("v1", r.vMax);
            l.appendTag(c);
        }
        t.setTag("runs", l);
        l = new NBTTagList();
        for (Exc e : exc) {
            NBTTagCompound c = new NBTTagCompound();
            c.setLong("p", e.pos); c.setInteger("r", e.run); c.setString("w", e.what); c.setDouble("v", e.value);
            l.appendTag(c);
        }
        t.setTag("exc", l);
        l = new NBTTagList();
        for (Plan p : plans.values()) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("w", p.work); c.setLong("m", p.made); c.setDouble("id", p.impactDelay); c.setDouble("ip", p.impactDeps);
            c.setString("win", p.window); c.setString("l", p.lines); c.setBoolean("ow", p.overrunWarned);
            NBTTagList tl = new NBTTagList();
            for (Task k : p.tasks) {
                NBTTagCompound x = new NBTTagCompound();
                x.setString("k", k.kind); x.setString("w", k.what); x.setLong("p", k.pos); x.setDouble("s", k.secs); x.setDouble("d", k.done);
                x.setInteger("st", k.state); x.setString("n", k.note); x.setBoolean("o", k.ohle);
                tl.appendTag(x);
            }
            c.setTag("t", tl);
            l.appendTag(c);
        }
        t.setTag("plans", l);
        NBTTagCompound g = new NBTTagCompound();
        for (Map.Entry<String, String> e : bestGrade.entrySet()) g.setString(e.getKey(), e.getValue());
        t.setTag("best", g);
        l = new NBTTagList();
        for (String[] r : grades) {
            NBTTagCompound c = new NBTTagCompound();
            c.setString("a", r[0]); c.setString("b", r[1]); c.setString("c", r[2]); c.setString("d", r[3]); c.setString("e", r[4]);
            l.appendTag(c);
        }
        t.setTag("grades", l);
        NBTTagCompound ft = new NBTTagCompound();
        for (Map.Entry<Long, Integer> e : floodTsr.entrySet()) ft.setInteger(String.valueOf(e.getKey()), e.getValue());
        t.setTag("ftsr", ft);
        t.setInteger("nf", nextFault);
        t.setInteger("nr", nextRun);
        return t;
    }
}
