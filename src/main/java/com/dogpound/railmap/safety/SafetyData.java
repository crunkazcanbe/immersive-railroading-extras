package com.dogpound.railmap.safety;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.*;

/**
 * Everything the signalling-safety systems remember per world (her ChatGPT list section 4, #19-28 + the fault lab):
 * the fail-safe configuration, one tracking record per train, the radio communications log, every piece of gear's
 * self-test / maintenance history, the interlocking's route decisions with their safety case, the fault lab and the
 * open incidents. Saved with the world, so headcodes, logs and histories survive a restart.
 */
public final class SafetyData extends WorldSavedData {
    private static final String NAME = "irextras_safety";

    // ---- #27 fail-safe modes ----------------------------------------------------------------------------------------
    public final int[] failsafe = new int[FailSafe.values().length];
    /** #24: line speed and reaction time a signal must be sighted for; #28: required overlap past the exit signal */
    public int sightKmh = 80, sightSeconds = 7, overlap = 20;
    /** #22: minutes between automatic self-tests (0 = only on demand) */
    public int selfTestMinutes = 5;
    /** batch-3 #16: per-signal line speed for the sighting requirement (signal pos -> km/h) */
    public final Map<Long, Integer> sightKmhAt = new HashMap<>();
    public long lastSelfTest;

    // ---- #25 train tracking -----------------------------------------------------------------------------------------
    public static final class TrainRec {
        public String id = "", headcode = "", detachedFrom = "";
        public int radioId, cars, axles, locos;
        public final List<UUID> members = new ArrayList<>();
        public UUID lead, rear;
        public String names = "", dest = "", route = "", berth = "", line = "";
        public long berthPos;
        public double x, y, z, rx, rz, lengthM;
        public float speed;
        public long firstSeen, lastSeen;
        public boolean driverless, gone;
        /** 0 confirmed, 1 rear end not confirmed, 2 integrity LOST (divided / missing wagon) */
        public int integrity;
        public String integrityWhy = "";
        public long integritySince;
        /** 0 not in radio territory, 1 linked, 2 LOST (in territory, no link) */
        public int radio;
        public int rssi = -200;
        public long lastReport, radioLostAt;
        public String mast = "";
        /** what the safety systems are doing to this train (we undo exactly what we did) */
        public boolean braked, held;

        public String label() { return headcode + (names.isEmpty() ? "" : " " + names); }

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setString("id", id); t.setString("hc", headcode); t.setString("det", detachedFrom);
            t.setInteger("rid", radioId); t.setInteger("cars", cars); t.setInteger("ax", axles); t.setInteger("lc", locos);
            NBTTagList m = new NBTTagList();
            for (UUID u : members) m.appendTag(new NBTTagString(u.toString()));
            t.setTag("m", m);
            if (lead != null) t.setString("lead", lead.toString());
            t.setString("names", names); t.setString("dest", dest); t.setString("route", route); t.setString("berth", berth); t.setString("line", line);
            t.setLong("bp", berthPos);
            t.setDouble("x", x); t.setDouble("y", y); t.setDouble("z", z); t.setDouble("len", lengthM);
            t.setLong("first", firstSeen); t.setLong("last", lastSeen);
            t.setBoolean("dl", driverless);
            t.setByte("int", (byte) integrity); t.setString("iw", integrityWhy); t.setLong("is", integritySince);
            return t;
        }

        static TrainRec read(NBTTagCompound t) {
            TrainRec r = new TrainRec();
            r.id = t.getString("id"); r.headcode = t.getString("hc"); r.detachedFrom = t.getString("det");
            r.radioId = t.getInteger("rid"); r.cars = t.getInteger("cars"); r.axles = t.getInteger("ax"); r.locos = t.getInteger("lc");
            NBTTagList m = t.getTagList("m", 8);
            for (int i = 0; i < m.tagCount(); i++) try { r.members.add(UUID.fromString(m.getStringTagAt(i))); } catch (IllegalArgumentException ignored) { }
            if (t.hasKey("lead")) try { r.lead = UUID.fromString(t.getString("lead")); } catch (IllegalArgumentException ignored) { }
            r.names = t.getString("names"); r.dest = t.getString("dest"); r.route = t.getString("route"); r.berth = t.getString("berth"); r.line = t.getString("line");
            r.berthPos = t.getLong("bp");
            r.x = t.getDouble("x"); r.y = t.getDouble("y"); r.z = t.getDouble("z"); r.lengthM = t.getDouble("len");
            r.firstSeen = t.getLong("first"); r.lastSeen = t.getLong("last");
            r.driverless = t.getBoolean("dl");
            r.integrity = t.getByte("int"); r.integrityWhy = t.getString("iw"); r.integritySince = t.getLong("is");
            // braked / held are not restored: console holds aren't saved either, and train control re-applies what still applies
            return r;
        }
    }

    public final Map<String, TrainRec> trains = new LinkedHashMap<>();
    public int nextTrain = 1, nextSerial = 1;

    public TrainRec train(String idOrHeadcode) {
        TrainRec r = trains.get(idOrHeadcode);
        if (r != null) return r;
        for (TrainRec t : trains.values()) if (t.headcode.equalsIgnoreCase(idOrHeadcode) || String.valueOf(t.radioId).equals(idOrHeadcode)) return t;
        return null;
    }

    // ---- #26 radio communications log -------------------------------------------------------------------------------
    public static final class Comm {
        public int id;
        public long tick, ackTick;
        public String from = "", to = "", text = "", ackBy = "";
        /** 0 instruction (needs ack), 1 emergency / lineside call (needs answer), 2 report / note (no reply) */
        public int kind;
        /** 0 waiting, 1 acknowledged / answered, 2 failed (no radio contact), 3 nothing needed */
        public int ack;
        /** the train record it went to ("" = several / a desk) and what to do when acknowledged */
        public String train = "";
        public int msg = -1;
        public long pos;

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("id", id); t.setLong("t", tick); t.setLong("at", ackTick);
            t.setString("f", from); t.setString("to", to); t.setString("x", text); t.setString("by", ackBy);
            t.setByte("k", (byte) kind); t.setByte("a", (byte) ack); t.setString("tr", train); t.setInteger("msg", msg); t.setLong("p", pos);
            return t;
        }

        static Comm read(NBTTagCompound t) {
            Comm c = new Comm();
            c.id = t.getInteger("id"); c.tick = t.getLong("t"); c.ackTick = t.getLong("at");
            c.from = t.getString("f"); c.to = t.getString("to"); c.text = t.getString("x"); c.ackBy = t.getString("by");
            c.kind = t.getByte("k"); c.ack = t.getByte("a"); c.train = t.getString("tr"); c.msg = t.getInteger("msg"); c.pos = t.getLong("p");
            return c;
        }
    }

    public final List<Comm> comms = new ArrayList<>();      // newest first
    public int nextComm = 1;

    public Comm log(World w, String from, String to, String text, int kind, int ack) {
        Comm c = new Comm();
        c.id = nextComm++;
        c.tick = w.getTotalWorldTime();
        c.from = from; c.to = to; c.text = text; c.kind = kind; c.ack = ack;
        comms.add(0, c);
        while (comms.size() > 150) comms.remove(comms.size() - 1);
        com.dogpound.railmap.alarm.Recorder.event(w, "RADIO " + from + " > " + to + ": " + text);
        markDirty();
        return c;
    }

    public Comm comm(int id) {
        for (Comm c : comms) if (c.id == id) return c;
        return null;
    }

    // ---- #22 self-test + maintenance history ------------------------------------------------------------------------
    public static final class Gear {
        public long pos;
        public String name = "", kind = "", last = "";
        /** 0 untested, 1 pass, 2 fail */
        public int state, pass, fail;
        /** the last 16 results, newest in bit 0 (1 = fail) */
        public int recent, tests;
        public boolean intermittent, wasBurnt;
        public final List<String> history = new ArrayList<>();   // newest first: "tick|text"

        public int flips() {
            int n = Math.min(tests, 16), f = 0;
            for (int i = 0; i + 1 < n; i++) if (((recent >> i) & 1) != ((recent >> (i + 1)) & 1)) f++;
            return f;
        }

        public void note(long tick, String text) {
            history.add(0, tick + "|" + text);
            while (history.size() > 24) history.remove(history.size() - 1);
        }

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setLong("p", pos); t.setString("n", name); t.setString("k", kind); t.setString("l", last);
            t.setByte("s", (byte) state); t.setInteger("ok", pass); t.setInteger("bad", fail); t.setInteger("r", recent); t.setInteger("t", tests);
            t.setBoolean("i", intermittent); t.setBoolean("b", wasBurnt);
            NBTTagList h = new NBTTagList();
            for (String s : history) h.appendTag(new NBTTagString(s));
            t.setTag("h", h);
            return t;
        }

        static Gear read(NBTTagCompound t) {
            Gear g = new Gear();
            g.pos = t.getLong("p"); g.name = t.getString("n"); g.kind = t.getString("k"); g.last = t.getString("l");
            g.state = t.getByte("s"); g.pass = t.getInteger("ok"); g.fail = t.getInteger("bad"); g.recent = t.getInteger("r"); g.tests = t.getInteger("t");
            g.intermittent = t.getBoolean("i"); g.wasBurnt = t.getBoolean("b");
            NBTTagList h = t.getTagList("h", 8);
            for (int i = 0; i < h.tagCount(); i++) g.history.add(h.getStringTagAt(i));
            return g;
        }
    }

    public final Map<Long, Gear> gear = new LinkedHashMap<>();

    public Gear gear(BlockPos p, String name, String kind) {
        Gear g = gear.computeIfAbsent(p.toLong(), k -> new Gear());
        g.pos = p.toLong();
        if (name != null) g.name = name;
        if (kind != null) g.kind = kind;
        return g;
    }

    /** a line in a piece of gear's maintenance history (repairs, resets, injected faults...) */
    public void history(World w, BlockPos p, String name, String kind, String text) {
        gear(p, name, kind).note(w.getTotalWorldTime(), text);
        markDirty();
    }

    // ---- #28 route decisions with their safety case -----------------------------------------------------------------
    public static final class Decision {
        public long tick;
        public String route = "";
        public boolean granted;
        public final List<String> lines = new ArrayList<>();   // "+ check passed" / "- check FAILED" / "~ note"
        /** batch-3 #15 conflict visualizer: what blocks it, where - {kind, x, z, label, other route "x,z;x,z..." or ""} */
        public final List<String[]> blockers = new ArrayList<>();
        /** the requested route's centre-line "x,z;x,z..." (downsampled) and its entrance / exit signals */
        public String path = "";
        public long from, to;
        public int id;
    }

    public final List<Decision> decisions = new ArrayList<>();   // newest first, memory only

    // ---- the fault-injection laboratory -----------------------------------------------------------------------------
    public static final class Fault {
        public FaultLab.Kind kind;
        public long pos, since;
        public String what = "";
    }

    public boolean testMode;
    public long labPos;
    public int labRadius = 64;
    public final List<Fault> faults = new ArrayList<>();
    public final List<String> report = new ArrayList<>();
    public int reportPass, reportFail;
    public long reportAt;

    // ---- #23 incidents (emergency plungers) -------------------------------------------------------------------------
    public static final class Incident {
        public int id;
        public long pos, opened, closed;
        public String where = "", by = "";
        /** procedure: 1 signals at danger, 2 trains at a stand, 4 dispatcher acknowledged, 8 site protected (possession) */
        public int steps;
    }

    public final List<Incident> incidents = new ArrayList<>();   // newest first
    public int nextIncident = 1;

    public Incident openIncident(long pos) {
        for (Incident i : incidents) if (i.pos == pos && i.closed == 0) return i;
        return null;
    }

    // ---- storage ----------------------------------------------------------------------------------------------------
    public SafetyData() { super(NAME); }
    public SafetyData(String n) { super(n); }

    public static SafetyData get(World w) {
        MapStorage s = w.getPerWorldStorage();
        SafetyData d = (SafetyData) s.getOrLoadData(SafetyData.class, NAME);
        if (d == null) { d = new SafetyData(); s.setData(NAME, d); }
        return d;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        int[] fs = t.getIntArray("fs");
        for (int i = 0; i < Math.min(fs.length, failsafe.length); i++) failsafe[i] = fs[i];
        if (t.hasKey("sk")) { sightKmh = t.getInteger("sk"); sightSeconds = t.getInteger("ss"); overlap = t.getInteger("ov"); selfTestMinutes = t.getInteger("stm"); }
        trains.clear();
        NBTTagList tl = t.getTagList("trains", 10);
        for (int i = 0; i < tl.tagCount(); i++) { TrainRec r = TrainRec.read(tl.getCompoundTagAt(i)); trains.put(r.id, r); }
        nextTrain = Math.max(1, t.getInteger("nt")); nextSerial = Math.max(1, t.getInteger("ns"));
        comms.clear();
        NBTTagList cl = t.getTagList("comms", 10);
        for (int i = 0; i < cl.tagCount(); i++) comms.add(Comm.read(cl.getCompoundTagAt(i)));
        nextComm = Math.max(1, t.getInteger("nc"));
        gear.clear();
        NBTTagList gl = t.getTagList("gear", 10);
        for (int i = 0; i < gl.tagCount(); i++) { Gear g = Gear.read(gl.getCompoundTagAt(i)); gear.put(g.pos, g); }
        testMode = t.getBoolean("test"); labPos = t.getLong("lab"); labRadius = t.hasKey("lr") ? t.getInteger("lr") : 64;
        faults.clear();
        NBTTagList fl = t.getTagList("faults", 10);
        for (int i = 0; i < fl.tagCount(); i++) {
            NBTTagCompound c = fl.getCompoundTagAt(i);
            FaultLab.Kind k = FaultLab.Kind.byName(c.getString("k"));
            if (k == null) continue;
            Fault f = new Fault();
            f.kind = k; f.pos = c.getLong("p"); f.since = c.getLong("s"); f.what = c.getString("w");
            faults.add(f);
        }
        report.clear();
        NBTTagList rl = t.getTagList("report", 8);
        for (int i = 0; i < rl.tagCount(); i++) report.add(rl.getStringTagAt(i));
        reportPass = t.getInteger("rp"); reportFail = t.getInteger("rf"); reportAt = t.getLong("ra");
        incidents.clear();
        NBTTagList il = t.getTagList("inc", 10);
        for (int i = 0; i < il.tagCount(); i++) {
            NBTTagCompound c = il.getCompoundTagAt(i);
            Incident n = new Incident();
            n.id = c.getInteger("id"); n.pos = c.getLong("p"); n.opened = c.getLong("o"); n.closed = c.getLong("c");
            n.where = c.getString("w"); n.by = c.getString("b"); n.steps = c.getInteger("s");
            incidents.add(n);
        }
        nextIncident = Math.max(1, t.getInteger("ni"));
        sightKmhAt.clear();
        if (t.hasKey("skAt")) { NBTTagCompound k = t.getCompoundTag("skAt"); for (String s : k.getKeySet()) try { sightKmhAt.put(Long.parseLong(s), k.getInteger(s)); } catch (NumberFormatException ignored) { } }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        t.setIntArray("fs", failsafe.clone());
        t.setInteger("sk", sightKmh); t.setInteger("ss", sightSeconds); t.setInteger("ov", overlap); t.setInteger("stm", selfTestMinutes);
        NBTTagList tl = new NBTTagList();
        for (TrainRec r : trains.values()) tl.appendTag(r.write());
        t.setTag("trains", tl);
        t.setInteger("nt", nextTrain); t.setInteger("ns", nextSerial);
        NBTTagList cl = new NBTTagList();
        for (Comm c : comms) cl.appendTag(c.write());
        t.setTag("comms", cl);
        t.setInteger("nc", nextComm);
        NBTTagList gl = new NBTTagList();
        for (Gear g : gear.values()) gl.appendTag(g.write());
        t.setTag("gear", gl);
        t.setBoolean("test", testMode); t.setLong("lab", labPos); t.setInteger("lr", labRadius);
        NBTTagList fl = new NBTTagList();
        for (Fault f : faults) {
            NBTTagCompound c = new NBTTagCompound();
            c.setString("k", f.kind.name()); c.setLong("p", f.pos); c.setLong("s", f.since); c.setString("w", f.what);
            fl.appendTag(c);
        }
        t.setTag("faults", fl);
        NBTTagList rl = new NBTTagList();
        for (String s : report) rl.appendTag(new NBTTagString(s));
        t.setTag("report", rl);
        t.setInteger("rp", reportPass); t.setInteger("rf", reportFail); t.setLong("ra", reportAt);
        NBTTagList il = new NBTTagList();
        for (Incident n : incidents) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("id", n.id); c.setLong("p", n.pos); c.setLong("o", n.opened); c.setLong("c", n.closed);
            c.setString("w", n.where); c.setString("b", n.by); c.setInteger("s", n.steps);
            il.appendTag(c);
        }
        t.setTag("inc", il);
        t.setInteger("ni", nextIncident);
        NBTTagCompound k = new NBTTagCompound();
        for (Map.Entry<Long, Integer> e : sightKmhAt.entrySet()) k.setInteger(String.valueOf(e.getKey()), e.getValue());
        t.setTag("skAt", k);
        return t;
    }
}
