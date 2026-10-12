package com.dogpound.railmap.ems;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.*;

/**
 * Emergency-service runs (per world): each turnout from a station to an incident, its timings (turnout, on scene, back),
 * the cordon barriers it put up and the crossings it closed (so they are always taken away again, even after a
 * restart), and a history of finished runs with their response times.
 */
public final class EmsData extends WorldSavedData {
    private static final String NAME = "irextras_ems";

    public enum Stage { TURNOUT, RESPONDING, ON_SCENE, TRANSPORT, RETURNING, DONE }

    public static final class Run {
        public int id, incident = -1;
        public Ems.Kind kind = Ems.Kind.FIRE;
        public long station;                          // the station desk's pos
        public String stationName = "", vehicle = "";
        public UUID entity;
        public Stage stage = Stage.TURNOUT;
        public double tx, ty, tz;                     // where it is going (the incident, or a patient's bed)
        public long turnout, onScene, left, back, stageAt;
        public double vx, vy, vz;                     // last known vehicle position (virtual driving when its chunk isn't loaded)
        public int work;                              // fires out / patients treated / barriers placed
        public String patient = "";
        public final List<Long> cordon = new ArrayList<>(), crossings = new ArrayList<>();
        public String note = "";
        public boolean silent;                        // the station's SIRENS key was at SILENT
        public int crew = 2;

        public long response() { return onScene > 0 ? onScene - turnout : -1; }
        public boolean active() { return stage != Stage.DONE; }

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("id", id); t.setInteger("inc", incident); t.setString("kind", kind.name()); t.setLong("st", station); t.setString("stN", stationName); t.setString("veh", vehicle);
            if (entity != null) t.setUniqueId("ent", entity);
            t.setString("stage", stage.name());
            t.setDouble("tx", tx); t.setDouble("ty", ty); t.setDouble("tz", tz);
            t.setLong("to", turnout); t.setLong("os", onScene); t.setLong("lf", left); t.setLong("bk", back); t.setLong("sa", stageAt);
            t.setDouble("vx", vx); t.setDouble("vy", vy); t.setDouble("vz", vz); t.setInteger("work", work); t.setString("pat", patient); t.setString("note", note);
            t.setBoolean("silent", silent); t.setInteger("crew", crew);
            t.setTag("cordon", longs(cordon)); t.setTag("lx", longs(crossings));
            return t;
        }

        static Run read(NBTTagCompound t) {
            Run r = new Run();
            r.id = t.getInteger("id"); r.incident = t.getInteger("inc");
            try { r.kind = Ems.Kind.valueOf(t.getString("kind")); } catch (IllegalArgumentException e) { r.kind = Ems.Kind.FIRE; }
            r.station = t.getLong("st"); r.stationName = t.getString("stN"); r.vehicle = t.getString("veh");
            r.entity = t.hasUniqueId("ent") ? t.getUniqueId("ent") : null;
            try { r.stage = Stage.valueOf(t.getString("stage")); } catch (IllegalArgumentException e) { r.stage = Stage.RETURNING; }
            r.tx = t.getDouble("tx"); r.ty = t.getDouble("ty"); r.tz = t.getDouble("tz");
            r.turnout = t.getLong("to"); r.onScene = t.getLong("os"); r.left = t.getLong("lf"); r.back = t.getLong("bk"); r.stageAt = t.getLong("sa");
            r.vx = t.getDouble("vx"); r.vy = t.getDouble("vy"); r.vz = t.getDouble("vz"); r.work = t.getInteger("work"); r.patient = t.getString("pat"); r.note = t.getString("note");
            r.silent = t.getBoolean("silent"); r.crew = t.hasKey("crew") ? t.getInteger("crew") : 2;
            readLongs(t, "cordon", r.cordon); readLongs(t, "lx", r.crossings);
            return r;
        }
    }

    public final List<Run> runs = new ArrayList<>();
    public final LinkedList<String> history = new LinkedList<>();
    private int nextId = 1;

    public EmsData() { super(NAME); }
    public EmsData(String n) { super(n); }

    public static EmsData get(World w) {
        MapStorage s = w.getPerWorldStorage();
        EmsData d = (EmsData) s.getOrLoadData(EmsData.class, NAME);
        if (d == null) { d = new EmsData(); s.setData(NAME, d); }
        return d;
    }

    public Run newRun() { Run r = new Run(); r.id = nextId++; runs.add(r); markDirty(); return r; }
    public Run byId(int id) { for (Run r : runs) if (r.id == id) return r; return null; }
    public Run byEntity(UUID u) { for (Run r : runs) if (u.equals(r.entity) && r.active()) return r; return null; }
    public List<Run> active() { List<Run> l = new ArrayList<>(); for (Run r : runs) if (r.active()) l.add(r); return l; }

    /** has this kind already been sent to this incident (running or finished)? */
    public boolean sent(int incident, Ems.Kind k) { for (Run r : runs) if (r.incident == incident && r.kind == k) return true; return false; }

    public void history(String s) { history.addFirst(s); while (history.size() > 60) history.removeLast(); markDirty(); }

    /** keep the last 40 finished runs */
    public void prune() {
        int done = 0;
        for (int i = runs.size() - 1; i >= 0; i--) if (!runs.get(i).active() && ++done > 40) runs.remove(i);
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        runs.clear(); history.clear();
        nextId = Math.max(1, t.getInteger("next"));
        NBTTagList l = t.getTagList("runs", 10);
        for (int i = 0; i < l.tagCount(); i++) runs.add(Run.read(l.getCompoundTagAt(i)));
        NBTTagList h = t.getTagList("hist", 8);
        for (int i = 0; i < h.tagCount(); i++) history.add(h.getStringTagAt(i));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        t.setInteger("next", nextId);
        NBTTagList l = new NBTTagList();
        for (Run r : runs) l.appendTag(r.write());
        t.setTag("runs", l);
        NBTTagList h = new NBTTagList();
        for (String s : history) h.appendTag(new NBTTagString(s));
        t.setTag("hist", h);
        return t;
    }

    static NBTTagList longs(List<Long> l) { NBTTagList o = new NBTTagList(); for (long v : l) o.appendTag(new NBTTagLong(v)); return o; }
    static void readLongs(NBTTagCompound t, String k, List<Long> into) { into.clear(); NBTTagList l = t.getTagList(k, 4); for (int i = 0; i < l.tagCount(); i++) into.add(((NBTTagLong) l.get(i)).getLong()); }
}
