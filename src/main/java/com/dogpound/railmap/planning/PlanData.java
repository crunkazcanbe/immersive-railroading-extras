package com.dogpound.railmap.planning;

import com.dogpound.railmap.incident.TwinSim;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.*;

/**
 * Everything the operations-planning side keeps with the world (her "next 48" list #1, 3, 4, 6, 7, 9, 40, 42, 46, 48):
 * the surveyed single / double track of each stretch, temporary railway configurations and their work crews, the
 * configuration history, the regional AI dispatchers and their decision log, yard departure plans, split / join
 * services, twin predictions and the last results of every study. An old world without this record starts empty:
 * nothing is active, no dispatcher is switched on.
 */
public final class PlanData extends WorldSavedData {
    private static final String NAME = "irextras_plan";

    // ---- #7 temporary configurations ------------------------------------------------------------------------------
    public enum Kind {
        TSR("Temporary speed restriction"), SINGLE_LINE("Single-line working"), CROSSING("Hand-worked level crossing"),
        LENGTH("Train-length limit"), POSSESSION("Engineering possession");
        public final String label;
        Kind(String l) { label = l; }
    }

    public enum Phase { PLANNED, INSTALLING, ACTIVE, REMOVING, CLEARED }

    public static final class Work {
        public int id;
        public Kind kind = Kind.TSR;
        public String name = "", by = "";
        /** zone centre (or single-line end A), end B for single-line working, radius / corridor half-width */
        public double x, y = 64, z, x2, z2, r = 16;
        public int kmh = 40, maxLenM = 200;
        /** window: world ticks; end 0 = until taken out */
        public long start, end;
        public Phase phase = Phase.PLANNED;
        public long phaseAt;
        /** crossing: trains authorised over it by the crossing keeper (each one used up as a train passes) */
        public int authorised;
        /** single-line working: the direction the line is working in now (+1 A to B, -1 B to A, 0 free) and the pilot's train count */
        public int dir, inSection;
        public String note = "";

        public boolean live() { return phase == Phase.ACTIVE; }

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("id", id); t.setString("k", kind.name()); t.setString("n", name); t.setString("by", by);
            t.setDouble("x", x); t.setDouble("y", y); t.setDouble("z", z); t.setDouble("x2", x2); t.setDouble("z2", z2); t.setDouble("r", r);
            t.setInteger("kmh", kmh); t.setInteger("len", maxLenM); t.setLong("s", start); t.setLong("e", end);
            t.setString("ph", phase.name()); t.setLong("pa", phaseAt); t.setInteger("au", authorised); t.setInteger("dir", dir); t.setString("note", note);
            return t;
        }

        static Work read(NBTTagCompound t) {
            Work w = new Work();
            w.id = t.getInteger("id");
            try { w.kind = Kind.valueOf(t.getString("k")); } catch (IllegalArgumentException e) { w.kind = Kind.TSR; }
            w.name = t.getString("n"); w.by = t.getString("by");
            w.x = t.getDouble("x"); w.y = t.hasKey("y") ? t.getDouble("y") : 64; w.z = t.getDouble("z"); w.x2 = t.getDouble("x2"); w.z2 = t.getDouble("z2"); w.r = t.getDouble("r");
            w.kmh = t.getInteger("kmh"); w.maxLenM = t.getInteger("len"); w.start = t.getLong("s"); w.end = t.getLong("e");
            try { w.phase = Phase.valueOf(t.getString("ph")); } catch (IllegalArgumentException e) { w.phase = Phase.PLANNED; }
            w.phaseAt = t.getLong("pa"); w.authorised = t.getInteger("au"); w.dir = t.getInteger("dir"); w.note = t.getString("note");
            return w;
        }
    }

    // ---- #40 configuration versions -------------------------------------------------------------------------------
    public static final class Version {
        public int id;
        public long tick;
        public String who = "", cat = "", why = "";
        public NBTTagCompound snap = new NBTTagCompound();
        public final List<String> diff = new ArrayList<>();
    }

    // ---- #46 regional dispatchers ---------------------------------------------------------------------------------
    public enum Goal {
        PUNCTUALITY("Punctuality", "even out headways: hold early trains, cut the dwell of late ones"),
        FREIGHT("Freight throughput", "keep freight moving: hold passenger trains that would block a freight path, short freight stops"),
        MAINTENANCE("Protect maintenance", "keep trains out of engineering possessions and work windows about to start");
        public final String label, help;
        Goal(String l, String h) { label = l; help = h; }
    }

    public static final class Region {
        public String name = "";
        public double x, z, r;                  // r 0 = the whole railway
        public String lines = "";               // comma list, "" = every line
        public Goal goal = Goal.PUNCTUALITY;
        public boolean on;
        public int priority = 2, maxHold = 45;
        public int decisions, overruled;
    }

    // ---- #4 yard departures ----------------------------------------------------------------------------------------
    public static final class Departure {
        public int id, road = -1, loadRoad = -1, maxCars = 40, maxLenM = 600;
        public String name = "", dest = "";
        public long deadline;
        public String status = "";
    }

    // ---- #9 split / join services -----------------------------------------------------------------------------------
    public static final class SplitPlan {
        public int id;
        public String train = "", splitAt = "", joinAt = "", rearLine = "", state = "WAITING", log = "";
        public int rearCars = 2;
        public String rear = "";                 // the rear portion's loco UUID once split
        public long last;
    }

    public final Map<String, Integer> tracks = new TreeMap<>();
    public final List<Work> works = new ArrayList<>();
    public final List<Version> versions = new ArrayList<>();
    public final List<Region> regions = new ArrayList<>();
    public final LinkedList<String> decisions = new LinkedList<>();
    public final Map<Long, List<Departure>> departures = new HashMap<>();
    public final Map<Long, Integer> yardStrategy = new HashMap<>();
    public final List<SplitPlan> splits = new ArrayList<>();
    /** #1 predictions waiting to be checked against what really happened: {tick, minutes, line, deps/hour, avg delay} */
    public final List<String[]> predictions = new ArrayList<>();
    public final LinkedList<String[]> accuracy = new LinkedList<>();
    /** the planning desk's / console's working case and the last study's printable lines */
    public NBTTagCompound twinCase = new NBTTagCompound();
    public final List<String[]> lastTwin = new ArrayList<>(), lastWhatIf = new ArrayList<>(), lastRobust = new ArrayList<>(), lastResil = new ArrayList<>();
    public final List<String> lastCompat = new ArrayList<>();
    public int nextId = 1, nextVersion = 1;
    public int resilienceScore = -1;
    /** what the last twin run of the working case would change on PROMOTE */
    public final List<String> promotable = new ArrayList<>();
    public boolean twinOk;

    public PlanData() { super(NAME); }
    public PlanData(String n) { super(n); }

    public static PlanData get(World w) {
        MapStorage s = w.getPerWorldStorage();
        PlanData d = (PlanData) s.getOrLoadData(PlanData.class, NAME);
        if (d == null) { d = new PlanData(); s.setData(NAME, d); }
        return d;
    }

    public int id() { markDirty(); return nextId++; }

    /** surveyed track counts (and live single-line working) into a twin model */
    public void applyTracks(TwinSim.Model m) {
        m.tracks.putAll(tracks);
        for (Work w : works) {
            if (!w.live() || w.kind != Kind.SINGLE_LINE) continue;
            for (Map.Entry<String, long[]> e : m.segments().entrySet()) {
                net.minecraft.util.math.BlockPos a = net.minecraft.util.math.BlockPos.fromLong(e.getValue()[0]), b = net.minecraft.util.math.BlockPos.fromLong(e.getValue()[1]);
                double mx = (a.getX() + b.getX()) / 2.0, mz = (a.getZ() + b.getZ()) / 2.0;
                if (TempWorks.distToSegment(mx, mz, w.x, w.z, w.x2, w.z2) <= w.r + 32) m.tracks.put(e.getKey(), 1);
            }
        }
    }

    public void decide(World w, String s) {
        decisions.addFirst(com.dogpound.railmap.alarm.Recorder.clock(w, w.getTotalWorldTime()) + " " + s);
        while (decisions.size() > 200) decisions.removeLast();
        markDirty();
    }

    // ---- save ------------------------------------------------------------------------------------------------------
    private static NBTTagList strs(Collection<String> l) { NBTTagList o = new NBTTagList(); for (String s : l) o.appendTag(new NBTTagString(s)); return o; }
    private static List<String> strs(NBTTagList l) { List<String> o = new ArrayList<>(); for (int i = 0; i < l.tagCount(); i++) o.add(l.getStringTagAt(i)); return o; }
    private static NBTTagList rows(Collection<String[]> l) { NBTTagList o = new NBTTagList(); for (String[] r : l) o.appendTag(new NBTTagString(String.join("\u0001", r))); return o; }
    private static List<String[]> rows(NBTTagList l) { List<String[]> o = new ArrayList<>(); for (int i = 0; i < l.tagCount(); i++) o.add(l.getStringTagAt(i).split("\u0001", -1)); return o; }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        tracks.clear();
        NBTTagCompound tr = t.getCompoundTag("tracks");
        for (String k : tr.getKeySet()) tracks.put(k, tr.getInteger(k));
        works.clear();
        NBTTagList wl = t.getTagList("works", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < wl.tagCount(); i++) works.add(Work.read(wl.getCompoundTagAt(i)));
        versions.clear();
        NBTTagList vl = t.getTagList("versions", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < vl.tagCount(); i++) {
            NBTTagCompound c = vl.getCompoundTagAt(i);
            Version v = new Version();
            v.id = c.getInteger("id"); v.tick = c.getLong("t"); v.who = c.getString("who"); v.cat = c.getString("cat"); v.why = c.getString("why");
            v.snap = c.getCompoundTag("snap");
            v.diff.addAll(strs(c.getTagList("diff", Constants.NBT.TAG_STRING)));
            versions.add(v);
        }
        regions.clear();
        NBTTagList rl = t.getTagList("regions", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < rl.tagCount(); i++) {
            NBTTagCompound c = rl.getCompoundTagAt(i);
            Region r = new Region();
            r.name = c.getString("n"); r.x = c.getDouble("x"); r.z = c.getDouble("z"); r.r = c.getDouble("r"); r.lines = c.getString("l");
            try { r.goal = Goal.valueOf(c.getString("g")); } catch (IllegalArgumentException e) { r.goal = Goal.PUNCTUALITY; }
            r.on = c.getBoolean("on"); r.priority = c.hasKey("p") ? c.getInteger("p") : 2; r.maxHold = c.hasKey("mh") ? c.getInteger("mh") : 45;
            r.decisions = c.getInteger("d"); r.overruled = c.getInteger("o");
            regions.add(r);
        }
        decisions.clear();
        decisions.addAll(strs(t.getTagList("dec", Constants.NBT.TAG_STRING)));
        departures.clear();
        NBTTagCompound dp = t.getCompoundTag("yard");
        for (String k : dp.getKeySet()) {
            List<Departure> l = new ArrayList<>();
            NBTTagList dl = dp.getTagList(k, Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < dl.tagCount(); i++) {
                NBTTagCompound c = dl.getCompoundTagAt(i);
                Departure d = new Departure();
                d.id = c.getInteger("id"); d.road = c.getInteger("road"); d.loadRoad = c.getInteger("load"); d.maxCars = c.getInteger("cars"); d.maxLenM = c.getInteger("len");
                d.name = c.getString("n"); d.dest = c.getString("dest"); d.deadline = c.getLong("dl");
                l.add(d);
            }
            try { departures.put(Long.parseLong(k), l); } catch (NumberFormatException ignored) { }
        }
        yardStrategy.clear();
        NBTTagCompound ys = t.getCompoundTag("ystrat");
        for (String k : ys.getKeySet()) try { yardStrategy.put(Long.parseLong(k), ys.getInteger(k)); } catch (NumberFormatException ignored) { }
        splits.clear();
        NBTTagList sl = t.getTagList("splits", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < sl.tagCount(); i++) {
            NBTTagCompound c = sl.getCompoundTagAt(i);
            SplitPlan p = new SplitPlan();
            p.id = c.getInteger("id"); p.train = c.getString("tr"); p.splitAt = c.getString("sa"); p.joinAt = c.getString("ja"); p.rearLine = c.getString("rl");
            p.state = c.getString("st"); p.log = c.getString("log"); p.rearCars = c.getInteger("rc"); p.rear = c.getString("rear"); p.last = c.getLong("last");
            splits.add(p);
        }
        predictions.clear(); predictions.addAll(rows(t.getTagList("pred", Constants.NBT.TAG_STRING)));
        accuracy.clear(); accuracy.addAll(rows(t.getTagList("acc", Constants.NBT.TAG_STRING)));
        twinCase = t.getCompoundTag("case");
        lastTwin.clear(); lastTwin.addAll(rows(t.getTagList("lt", Constants.NBT.TAG_STRING)));
        lastWhatIf.clear(); lastWhatIf.addAll(rows(t.getTagList("lw", Constants.NBT.TAG_STRING)));
        lastRobust.clear(); lastRobust.addAll(rows(t.getTagList("lr", Constants.NBT.TAG_STRING)));
        lastResil.clear(); lastResil.addAll(rows(t.getTagList("ls", Constants.NBT.TAG_STRING)));
        lastCompat.clear(); lastCompat.addAll(strs(t.getTagList("lc", Constants.NBT.TAG_STRING)));
        promotable.clear(); promotable.addAll(strs(t.getTagList("prom", Constants.NBT.TAG_STRING)));
        twinOk = t.getBoolean("tok");
        nextId = Math.max(1, t.getInteger("nid"));
        nextVersion = Math.max(1, t.getInteger("nv"));
        resilienceScore = t.hasKey("rs") ? t.getInteger("rs") : -1;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        NBTTagCompound tr = new NBTTagCompound();
        tracks.forEach(tr::setInteger);
        t.setTag("tracks", tr);
        NBTTagList wl = new NBTTagList();
        for (Work w : works) wl.appendTag(w.write());
        t.setTag("works", wl);
        NBTTagList vl = new NBTTagList();
        for (Version v : versions) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("id", v.id); c.setLong("t", v.tick); c.setString("who", v.who); c.setString("cat", v.cat); c.setString("why", v.why);
            c.setTag("snap", v.snap); c.setTag("diff", strs(v.diff));
            vl.appendTag(c);
        }
        t.setTag("versions", vl);
        NBTTagList rl = new NBTTagList();
        for (Region r : regions) {
            NBTTagCompound c = new NBTTagCompound();
            c.setString("n", r.name); c.setDouble("x", r.x); c.setDouble("z", r.z); c.setDouble("r", r.r); c.setString("l", r.lines); c.setString("g", r.goal.name());
            c.setBoolean("on", r.on); c.setInteger("p", r.priority); c.setInteger("mh", r.maxHold); c.setInteger("d", r.decisions); c.setInteger("o", r.overruled);
            rl.appendTag(c);
        }
        t.setTag("regions", rl);
        t.setTag("dec", strs(decisions));
        NBTTagCompound dp = new NBTTagCompound();
        for (Map.Entry<Long, List<Departure>> e : departures.entrySet()) {
            NBTTagList dl = new NBTTagList();
            for (Departure d : e.getValue()) {
                NBTTagCompound c = new NBTTagCompound();
                c.setInteger("id", d.id); c.setInteger("road", d.road); c.setInteger("load", d.loadRoad); c.setInteger("cars", d.maxCars); c.setInteger("len", d.maxLenM);
                c.setString("n", d.name); c.setString("dest", d.dest); c.setLong("dl", d.deadline);
                dl.appendTag(c);
            }
            dp.setTag(String.valueOf(e.getKey()), dl);
        }
        t.setTag("yard", dp);
        NBTTagCompound ys = new NBTTagCompound();
        yardStrategy.forEach((k, v) -> ys.setInteger(String.valueOf(k), v));
        t.setTag("ystrat", ys);
        NBTTagList sl = new NBTTagList();
        for (SplitPlan p : splits) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("id", p.id); c.setString("tr", p.train); c.setString("sa", p.splitAt); c.setString("ja", p.joinAt); c.setString("rl", p.rearLine);
            c.setString("st", p.state); c.setString("log", p.log); c.setInteger("rc", p.rearCars); c.setString("rear", p.rear); c.setLong("last", p.last);
            sl.appendTag(c);
        }
        t.setTag("splits", sl);
        t.setTag("pred", rows(predictions));
        t.setTag("acc", rows(accuracy));
        t.setTag("case", twinCase);
        t.setTag("lt", rows(lastTwin)); t.setTag("lw", rows(lastWhatIf)); t.setTag("lr", rows(lastRobust)); t.setTag("ls", rows(lastResil));
        t.setTag("lc", strs(lastCompat)); t.setTag("prom", strs(promotable));
        t.setBoolean("tok", twinOk);
        t.setInteger("nid", nextId);
        t.setInteger("nv", nextVersion);
        t.setInteger("rs", resilienceScore);
        return t;
    }
}
