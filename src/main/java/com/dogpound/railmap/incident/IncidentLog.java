package com.dogpound.railmap.incident;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.Disruptions;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.*;

/**
 * The railway incident command system (her ChatGPT list #67): every major incident gets a record with where it is,
 * how bad (level 1 minor / 2 serious / 3 major), the stretch of line it closes, what the response needs (recovery crew,
 * rescue engine, fire, dangerous-goods team, medical, evacuation, power isolation, adjacent-line protection) and what
 * has been sent, a timeline of everything that happened, response time (reported -> on scene) and reopening time
 * (reported -> line open), and a final report. Lines reopen in stages: the adjacent lines first once the hazard is
 * contained, the incident line last once it is clear.
 * Wrecks and failed trains come from auto/Disruptions (mirrored here every second); fires from the Fire Alarm Panels,
 * evacuations from the desk / panels / command, assistance requests from a train's crew (command).
 * Also keeps the work orders the Remote Maintenance Terminal raises (#75).
 */
public final class IncidentLog extends WorldSavedData {
    public enum Type {
        WRECK("DERAILMENT"), FAILED("FAILED TRAIN"), FIRE("FIRE / SMOKE"), HAZMAT("DANGEROUS GOODS"), EVAC("EVACUATION"), OTHER("OTHER");
        public final String label;
        Type(String l) { label = l; }
    }

    public enum Stage { REPORTED, ACKNOWLEDGED, RESPONDING, ON_SCENE, CONTAINED, RECOVERY, REOPENING, CLOSED }

    /** response needs / assignments (bits) */
    public static final int CREW = 1, RESCUE = 2, FIRE = 4, HAZMAT = 8, MEDICAL = 16, EVAC = 32, ISOLATE = 64, PROTECT = 128;
    public static final String[] NEED_NAMES = {"CREW", "RESCUE", "FIRE", "HAZMAT", "MEDIC", "EVAC", "ISOLATE", "PROTECT"};

    public static final class Sec {
        public String key = "", name = "";
        public double x, y, z, r;
        public boolean released;
        public long releasedAt;
    }

    public static final class Rec {
        public int id;
        public Type type = Type.OTHER;
        public int level = 1;
        public String what = "";
        public double x, y, z, r = 8;
        public Stage stage = Stage.REPORTED;
        public long opened, acked, onScene, contained, reopened, closed, lastStage;
        public String commander = "";
        public int disruptId = -1;
        public int needs, assigned;
        public String rescue = "", rescueName = "", failKind = "";
        public int towKmh;
        public boolean coupled, stabled;
        public long crewFrom;
        public final List<Sec> secs = new ArrayList<>();
        public String haz = "";
        public int hazKind;
        public long cleanUntil;
        public int paxAffected, paxAccounted, services, heldMax;
        public boolean evac;
        public long evacSaid, firePanel;
        public final List<String> log = new ArrayList<>();          // "tick|text"
        public final List<String> blackbox = new ArrayList<>();     // train recorder download (#74)
        public final List<String> plan = new ArrayList<>();         // rescue / evacuation plan lines
        public final List<String> report = new ArrayList<>();       // final report
        public final Set<String> stock = new LinkedHashSet<>();     // UUIDs of the vehicles involved

        public boolean open() { return stage != Stage.CLOSED; }
        public BlockPos pos() { return new BlockPos(x, y, z); }
        public boolean needs(int b) { return (needs & b) != 0; }
        public boolean has(int b) { return (assigned & b) != 0; }
        public String needLine() {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < NEED_NAMES.length; i++) if ((needs & 1 << i) != 0) b.append(b.length() == 0 ? "" : " ").append(NEED_NAMES[i]).append((assigned & 1 << i) != 0 ? "+" : "?");
            return b.length() == 0 ? "none" : b.toString();
        }

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("id", id); t.setString("type", type.name()); t.setInteger("lvl", level); t.setString("what", what);
            t.setDouble("x", x); t.setDouble("y", y); t.setDouble("z", z); t.setDouble("r", r); t.setString("stage", stage.name());
            t.setLong("o", opened); t.setLong("a", acked); t.setLong("s", onScene); t.setLong("c", contained); t.setLong("ro", reopened); t.setLong("cl", closed); t.setLong("ls", lastStage);
            t.setString("cmd", commander); t.setInteger("dis", disruptId); t.setInteger("needs", needs); t.setInteger("asg", assigned);
            t.setString("resc", rescue); t.setString("rescN", rescueName); t.setString("fail", failKind); t.setInteger("tow", towKmh);
            t.setBoolean("cpl", coupled); t.setBoolean("stab", stabled); t.setLong("crew", crewFrom);
            t.setString("haz", haz); t.setInteger("hazK", hazKind); t.setLong("clean", cleanUntil);
            t.setInteger("pax", paxAffected); t.setInteger("paxA", paxAccounted); t.setInteger("svc", services); t.setInteger("heldMax", heldMax);
            t.setBoolean("evac", evac); t.setLong("fp", firePanel);
            NBTTagList sl = new NBTTagList();
            for (Sec s : secs) {
                NBTTagCompound c = new NBTTagCompound();
                c.setString("k", s.key); c.setString("n", s.name); c.setDouble("x", s.x); c.setDouble("y", s.y); c.setDouble("z", s.z); c.setDouble("r", s.r);
                c.setBoolean("rel", s.released); c.setLong("ra", s.releasedAt);
                sl.appendTag(c);
            }
            t.setTag("secs", sl);
            t.setTag("log", strings(log)); t.setTag("bb", strings(blackbox)); t.setTag("plan", strings(plan)); t.setTag("rep", strings(report));
            t.setTag("stock", strings(new ArrayList<>(stock)));
            return t;
        }

        static Rec read(NBTTagCompound t) {
            Rec r = new Rec();
            r.id = t.getInteger("id");
            try { r.type = Type.valueOf(t.getString("type")); } catch (IllegalArgumentException e) { r.type = Type.OTHER; }
            r.level = Math.max(1, t.getInteger("lvl")); r.what = t.getString("what");
            r.x = t.getDouble("x"); r.y = t.getDouble("y"); r.z = t.getDouble("z"); r.r = t.getDouble("r");
            try { r.stage = Stage.valueOf(t.getString("stage")); } catch (IllegalArgumentException e) { r.stage = Stage.CLOSED; }
            r.opened = t.getLong("o"); r.acked = t.getLong("a"); r.onScene = t.getLong("s"); r.contained = t.getLong("c"); r.reopened = t.getLong("ro"); r.closed = t.getLong("cl"); r.lastStage = t.getLong("ls");
            r.commander = t.getString("cmd"); r.disruptId = t.getInteger("dis"); r.needs = t.getInteger("needs"); r.assigned = t.getInteger("asg");
            r.rescue = t.getString("resc"); r.rescueName = t.getString("rescN"); r.failKind = t.getString("fail"); r.towKmh = t.getInteger("tow");
            r.coupled = t.getBoolean("cpl"); r.stabled = t.getBoolean("stab"); r.crewFrom = t.getLong("crew");
            r.haz = t.getString("haz"); r.hazKind = t.getInteger("hazK"); r.cleanUntil = t.getLong("clean");
            r.paxAffected = t.getInteger("pax"); r.paxAccounted = t.getInteger("paxA"); r.services = t.getInteger("svc"); r.heldMax = t.getInteger("heldMax");
            r.evac = t.getBoolean("evac"); r.firePanel = t.getLong("fp");
            NBTTagList sl = t.getTagList("secs", 10);
            for (int i = 0; i < sl.tagCount(); i++) {
                NBTTagCompound c = sl.getCompoundTagAt(i);
                Sec s = new Sec();
                s.key = c.getString("k"); s.name = c.getString("n"); s.x = c.getDouble("x"); s.y = c.getDouble("y"); s.z = c.getDouble("z"); s.r = c.getDouble("r");
                s.released = c.getBoolean("rel"); s.releasedAt = c.getLong("ra");
                r.secs.add(s);
            }
            r.log.addAll(list(t, "log")); r.blackbox.addAll(list(t, "bb")); r.plan.addAll(list(t, "plan")); r.report.addAll(list(t, "rep"));
            r.stock.addAll(list(t, "stock"));
            return r;
        }
    }

    /** a work order from the Remote Maintenance Terminal (#75) */
    public static final class WorkOrder {
        public int id;
        public long pos, raised, done;
        public String gear = "", fault = "", by = "", closedWhy = "";
        public boolean local;
    }

    private static final String NAME = "irextras_incidents";
    private final List<Rec> recs = new ArrayList<>();
    public final List<WorkOrder> orders = new ArrayList<>();
    private int nextId = 1, nextWo = 1;
    /** bumps on every change (desks refresh) */
    public int version;

    public IncidentLog() { super(NAME); }
    public IncidentLog(String n) { super(n); }

    public static IncidentLog get(World w) {
        MapStorage s = w.getPerWorldStorage();
        IncidentLog d = (IncidentLog) s.getOrLoadData(IncidentLog.class, NAME);
        if (d == null) { d = new IncidentLog(); s.setData(NAME, d); }
        return d;
    }

    public List<Rec> all() { return recs; }

    public List<Rec> active() {
        List<Rec> l = new ArrayList<>();
        for (Rec r : recs) if (r.open()) l.add(r);
        return l;
    }

    public Rec byId(int id) {
        for (Rec r : recs) if (r.id == id) return r;
        return null;
    }

    public Rec byDisruption(int id) {
        for (Rec r : recs) if (r.open() && r.disruptId == id) return r;
        return null;
    }

    /** the open incident covering a spot (nearest), or null */
    public Rec at(double x, double z, double extra) {
        Rec best = null;
        double bd = Double.MAX_VALUE;
        for (Rec r : recs) {
            if (!r.open()) continue;
            double d = Math.hypot(r.x - x, r.z - z);
            if (d <= r.r + extra && d < bd) { bd = d; best = r; }
        }
        return best;
    }

    public void changed() { version++; markDirty(); }

    // ---- opening and logging --------------------------------------------------------------------------------------
    public Rec open(World w, Type type, String what, double x, double y, double z, double radius, int level) {
        Rec r = new Rec();
        r.id = nextId++;
        r.type = type; r.what = what; r.x = x; r.y = y; r.z = z; r.r = radius; r.level = Math.max(1, Math.min(3, level));
        r.opened = r.lastStage = w.getTotalWorldTime();
        recs.add(0, r);
        while (recs.size() > 60) {                                                   // keep the 60 newest (open ones never dropped)
            int k = -1;
            for (int i = recs.size() - 1; i >= 0; i--) if (!recs.get(i).open()) { k = i; break; }
            if (k < 0) break;
            recs.remove(k);
        }
        log(w, r, "OPENED: " + type.label + " level " + r.level + " - " + what + " at " + (int) x + "," + (int) z);
        AlarmManager.get(w).raise(w, "incident:" + r.id, AlarmManager.Cat.INCIDENT, r.level >= 2 ? AlarmManager.Sev.CRITICAL : AlarmManager.Sev.MAJOR,
                "INCIDENT #" + r.id + " " + type.label + ": " + what + " (dispatcher to accept)", r.pos(), true);
        for (net.minecraft.entity.player.EntityPlayer p : w.playerEntities)
            if (p.getDistanceSq(r.pos()) < 512 * 512)
                p.sendMessage(new net.minecraft.util.text.TextComponentString("§c§l⚠ INCIDENT #" + r.id + "§r§c " + type.label + ": " + what + " - control notified"));
        changed();
        return r;
    }

    public void log(World w, Rec r, String text) {
        r.log.add(w.getTotalWorldTime() + "|" + text);
        while (r.log.size() > 120) r.log.remove(0);
        Recorder.event(w, "INCIDENT #" + r.id + ": " + text);
        changed();
    }

    /** move an incident on to a stage (only forwards), stamping the response times */
    public void stage(World w, Rec r, Stage s, String why) {
        if (s.ordinal() <= r.stage.ordinal()) return;
        long now = w.getTotalWorldTime();
        if (s.ordinal() >= Stage.ACKNOWLEDGED.ordinal() && r.acked == 0) r.acked = now;
        if (s.ordinal() >= Stage.ON_SCENE.ordinal() && r.onScene == 0) r.onScene = now;
        if (s.ordinal() >= Stage.CONTAINED.ordinal() && r.contained == 0) r.contained = now;
        if (s.ordinal() >= Stage.REOPENING.ordinal() && r.reopened == 0) r.reopened = now;
        r.stage = s;
        r.lastStage = now;
        log(w, r, s.name().replace('_', ' ') + (why == null || why.isEmpty() ? "" : ": " + why));
        if (s == Stage.ACKNOWLEDGED || s.ordinal() > Stage.ACKNOWLEDGED.ordinal()) AlarmManager.get(w).clear("incident:" + r.id);
        if (s == Stage.CLOSED) close(w, r);
    }

    private void close(World w, Rec r) {
        long now = w.getTotalWorldTime();
        r.closed = now;
        for (Sec s : r.secs) if (!s.released) { s.released = true; s.releasedAt = now; Disruptions.release(w, s.key, "incident #" + r.id + " closed"); }
        AlarmManager.get(w).clear("incident:" + r.id);
        if (r.evac) IncidentOps.allClear(w, r, "incident closed");
        r.report.clear();
        r.report.addAll(finalReport(w, r));
        AlarmManager.get(w).raise(w, "incident-closed:" + r.id, AlarmManager.Cat.INCIDENT, AlarmManager.Sev.INFO,
                "Incident #" + r.id + " closed - line clear after " + mins(now - r.opened), r.pos(), false);
        changed();
    }

    /** the final report: what, when, response and reopening times, resources, people, the timeline */
    public static List<String> finalReport(World w, Rec r) {
        List<String> o = new ArrayList<>();
        o.add("INCIDENT #" + r.id + " - " + r.type.label + " - LEVEL " + r.level);
        o.add(r.what);
        o.add("Location " + (int) r.x + "," + (int) r.y + "," + (int) r.z + ", closed radius " + (int) r.r + " m");
        o.add("Commander: " + (r.commander.isEmpty() ? "none (automatic)" : r.commander));
        o.add("Reported " + Recorder.clock(w, r.opened) + ", accepted " + (r.acked == 0 ? "-" : "after " + mins(r.acked - r.opened)));
        o.add("Response time (on scene): " + (r.onScene == 0 ? "-" : mins(r.onScene - r.opened)));
        o.add("Contained: " + (r.contained == 0 ? "-" : "after " + mins(r.contained - r.opened)));
        o.add("Reopening time: " + (r.reopened == 0 ? "-" : mins(r.reopened - r.opened)) + ", closed after " + (r.closed == 0 ? "-" : mins(r.closed - r.opened)));
        o.add("Response: " + r.needLine());
        if (!r.rescueName.isEmpty()) o.add("Rescue engine: " + r.rescueName + " (" + r.failKind + ", tow limit " + r.towKmh + " km/h)" + (r.stabled ? " - stabled" : ""));
        if (!r.haz.isEmpty()) o.add("Dangerous goods: " + r.haz);
        o.add("Passengers affected " + r.paxAffected + ", accounted for " + r.paxAccounted + "; services held (most at once) " + r.heldMax);
        int st = 0;
        for (Sec s : r.secs) o.add("Section " + (++st) + " " + s.name + ": " + (s.released ? "reopened " + Recorder.clock(w, s.releasedAt) : "CLOSED"));
        if (!r.blackbox.isEmpty()) { o.add("Train recorder download:"); for (int i = 0; i < Math.min(12, r.blackbox.size()); i++) o.add("  " + r.blackbox.get(i)); }
        o.add("Timeline:");
        for (String l : r.log) { String[] p = l.split("\\|", 2); o.add("  " + Recorder.clock(w, Long.parseLong(p[0])) + " " + (p.length > 1 ? p[1] : "")); }
        return o;
    }

    public static String mins(long ticks) {
        long s = Math.max(0, ticks / 20);
        return s >= 60 ? (s / 60) + " min " + (s % 60) + " s" : s + " s";
    }

    // ---- sections (line closures) ----------------------------------------------------------------------------------
    public Sec section(World w, Rec r, String key, String name, double x, double y, double z, double radius) {
        for (Sec s : r.secs) if (s.key.equals(key)) return s;
        Sec s = new Sec();
        s.key = key; s.name = name; s.x = x; s.y = y; s.z = z; s.r = radius;
        r.secs.add(s);
        Disruptions.hold(w, key, "incident #" + r.id + " " + name, x, y, z, radius);
        log(w, r, "LINE CLOSED: " + name + " (" + (int) radius + " m)");
        return s;
    }

    public void release(World w, Rec r, Sec s, String why) {
        if (s.released) return;
        s.released = true;
        s.releasedAt = w.getTotalWorldTime();
        Disruptions.release(w, s.key, why);
        log(w, r, "REOPENED: " + s.name + " (" + why + ")");
    }

    // ---- work orders (#75) -------------------------------------------------------------------------------------
    public WorkOrder workOrder(World w, BlockPos at, String gear, String fault, String by, boolean local) {
        for (WorkOrder o : orders) if (o.done == 0 && o.pos == at.toLong()) return o;          // one open order per item
        WorkOrder o = new WorkOrder();
        o.id = nextWo++; o.pos = at.toLong(); o.gear = gear; o.fault = fault; o.by = by; o.local = local; o.raised = w.getTotalWorldTime();
        orders.add(0, o);
        while (orders.size() > 80) orders.remove(orders.size() - 1);
        Recorder.event(w, "WORK ORDER " + o.id + " raised by " + by + ": " + gear + " - " + fault);
        changed();
        return o;
    }

    // ---- save -----------------------------------------------------------------------------------------------------
    @Override
    public void readFromNBT(NBTTagCompound t) {
        recs.clear();
        NBTTagList l = t.getTagList("recs", 10);
        for (int i = 0; i < l.tagCount(); i++) recs.add(Rec.read(l.getCompoundTagAt(i)));
        orders.clear();
        NBTTagList o = t.getTagList("wo", 10);
        for (int i = 0; i < o.tagCount(); i++) {
            NBTTagCompound c = o.getCompoundTagAt(i);
            WorkOrder w = new WorkOrder();
            w.id = c.getInteger("id"); w.pos = c.getLong("p"); w.raised = c.getLong("r"); w.done = c.getLong("d");
            w.gear = c.getString("g"); w.fault = c.getString("f"); w.by = c.getString("b"); w.closedWhy = c.getString("cw"); w.local = c.getBoolean("l");
            orders.add(w);
        }
        nextId = Math.max(1, t.getInteger("next"));
        nextWo = Math.max(1, t.getInteger("nextWo"));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        NBTTagList l = new NBTTagList();
        for (Rec r : recs) l.appendTag(r.write());
        t.setTag("recs", l);
        NBTTagList o = new NBTTagList();
        for (WorkOrder w : orders) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("id", w.id); c.setLong("p", w.pos); c.setLong("r", w.raised); c.setLong("d", w.done);
            c.setString("g", w.gear); c.setString("f", w.fault); c.setString("b", w.by); c.setString("cw", w.closedWhy); c.setBoolean("l", w.local);
            o.appendTag(c);
        }
        t.setTag("wo", o);
        t.setInteger("next", nextId);
        t.setInteger("nextWo", nextWo);
        return t;
    }

    static NBTTagList strings(List<String> l) {
        NBTTagList t = new NBTTagList();
        for (String s : l) t.appendTag(new NBTTagString(s));
        return t;
    }

    static List<String> list(NBTTagCompound t, String k) {
        List<String> o = new ArrayList<>();
        NBTTagList l = t.getTagList(k, 8);
        for (int i = 0; i < l.tagCount(); i++) o.add(l.getStringTagAt(i));
        return o;
    }
}
