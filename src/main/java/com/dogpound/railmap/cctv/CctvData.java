package com.dogpound.railmap.cctv;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.*;

/**
 * The CCTV directory, server-authoritative and shared by every dimension: camera numbers (what monitors and the desk
 * call them by), where each camera is and how it looks right now, monitor numbers, bookmarks on recordings, the motion /
 * tamper event list, and which video networks are armed. Camera tiles write their entries; clients get a copy
 * (PacketCctvDir) whenever it changes.
 */
public final class CctvData extends WorldSavedData {
    private static final String NAME = "irextras_cctv";

    /** camera status */
    public static final int OFF = 0, ONLINE = 1, NO_SUPPLY = 2, NO_NET = 3, TAMPER = 4, UNLOADED = 5;
    public static final String[] STATUS = {"NO 12 V", "ONLINE", "NO SUPPLY", "VIDEO LOSS", "TAMPER", "OUT OF RANGE"};

    public static final class Cam {
        public int no, type, dim, status = OFF, motion;
        public String name = "", train = "", what = "", masks = "";
        public long pos, net;
        public double x, y, z;
        public float yaw, pitch, fov = 60;
        public boolean ir;
        public long lastMotion;
        /** picture effects: iris gain, 1 rain on the lens, 2 out of focus */
        public float gain = 1;
        public int fx;
        /** recording right now on an NVR */
        public boolean rec;

        public boolean streams() { return status == ONLINE || status == TAMPER; }
        public CamType kind() { return CamType.of(type); }

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("no", no); t.setInteger("type", type); t.setInteger("dim", dim); t.setInteger("st", status);
            t.setString("name", name); t.setString("train", train); t.setString("what", what); t.setString("masks", masks);
            t.setLong("pos", pos); t.setLong("net", net); t.setDouble("x", x); t.setDouble("y", y); t.setDouble("z", z);
            t.setFloat("yaw", yaw); t.setFloat("pitch", pitch); t.setFloat("fov", fov); t.setBoolean("ir", ir); t.setLong("lm", lastMotion);
            t.setFloat("gain", gain); t.setInteger("fx", fx); t.setBoolean("rec", rec);
            return t;
        }

        static Cam read(NBTTagCompound t) {
            Cam c = new Cam();
            c.no = t.getInteger("no"); c.type = t.getInteger("type"); c.dim = t.getInteger("dim"); c.status = t.getInteger("st");
            c.name = t.getString("name"); c.train = t.getString("train"); c.what = t.getString("what"); c.masks = t.getString("masks");
            c.pos = t.getLong("pos"); c.net = t.getLong("net"); c.x = t.getDouble("x"); c.y = t.getDouble("y"); c.z = t.getDouble("z");
            c.yaw = t.getFloat("yaw"); c.pitch = t.getFloat("pitch"); c.fov = t.getFloat("fov"); c.ir = t.getBoolean("ir"); c.lastMotion = t.getLong("lm");
            c.gain = t.hasKey("gain") ? t.getFloat("gain") : 1; c.fx = t.getInteger("fx"); c.rec = t.getBoolean("rec");
            return c;
        }
    }

    /** a bookmark on a camera's recording (alarms, motion, doorbell, an operator's MARK) */
    public static final class Mark {
        public int cam; public long t; public String text;
        Mark(int cam, long t, String text) { this.cam = cam; this.t = t; this.text = text; }
    }

    public final TreeMap<Integer, Cam> cams = new TreeMap<>();
    public final ArrayDeque<Mark> marks = new ArrayDeque<>();
    /** newest first: {time, camera no, kind, text} */
    public final ArrayDeque<String[]> events = new ArrayDeque<>();
    /** video networks disarmed at a desk (default armed) */
    public final Set<Long> disarmed = new HashSet<>();
    public int nextCam = 1, nextMon = 1;
    /** bumps on any change the clients should see */
    public int version;

    public CctvData() { super(NAME); }
    public CctvData(String n) { super(n); }

    public static CctvData get(World w) {
        MapStorage st = w.getMapStorage();
        CctvData d = st == null ? null : (CctvData) st.getOrLoadData(CctvData.class, NAME);
        if (d == null) { d = new CctvData(); if (st != null) st.setData(NAME, d); }
        return d;
    }

    public void changed() { version++; markDirty(); }

    public int allocCam() { while (cams.containsKey(nextCam)) nextCam++; int n = nextCam++; changed(); return n; }
    public int allocMon() { changed(); return nextMon++; }

    public Cam cam(int no) { return cams.get(no); }

    public Cam byPos(int dim, long pos) {
        for (Cam c : cams.values()) if (c.dim == dim && c.pos == pos && c.train.isEmpty()) return c;
        return null;
    }

    public Cam byTrain(String uuid) {
        for (Cam c : cams.values()) if (uuid.equals(c.train)) return c;
        return null;
    }

    public void mark(int cam, long t, String text) {
        marks.addLast(new Mark(cam, t, text));
        while (marks.size() > 400) marks.removeFirst();
        markDirty();
    }

    public void event(long t, int cam, String kind, String text) {
        events.addFirst(new String[]{String.valueOf(t), String.valueOf(cam), kind, text});
        while (events.size() > 120) events.removeLast();
        changed();
    }

    public boolean armed(long net) { return !disarmed.contains(net); }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        cams.clear(); marks.clear(); events.clear(); disarmed.clear();
        NBTTagList l = t.getTagList("cams", 10);
        for (int i = 0; i < l.tagCount(); i++) { Cam c = Cam.read(l.getCompoundTagAt(i)); cams.put(c.no, c); }
        NBTTagList m = t.getTagList("marks", 10);
        for (int i = 0; i < m.tagCount(); i++) { NBTTagCompound k = m.getCompoundTagAt(i); marks.add(new Mark(k.getInteger("c"), k.getLong("t"), k.getString("x"))); }
        NBTTagList e = t.getTagList("events", 10);
        for (int i = 0; i < e.tagCount(); i++) { NBTTagCompound k = e.getCompoundTagAt(i); events.add(new String[]{k.getString("t"), k.getString("c"), k.getString("k"), k.getString("x")}); }
        for (long n : t.hasKey("disarmed") ? toLongs(t.getTag("disarmed")) : new long[0]) disarmed.add(n);
        nextCam = Math.max(1, t.getInteger("nextCam"));
        nextMon = Math.max(1, t.getInteger("nextMon"));
    }

    private static long[] toLongs(net.minecraft.nbt.NBTBase b) {
        if (!(b instanceof NBTTagList l)) return new long[0];
        long[] o = new long[l.tagCount()];
        for (int i = 0; i < o.length; i++) o[i] = ((net.minecraft.nbt.NBTTagLong) l.get(i)).getLong();
        return o;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        NBTTagList l = new NBTTagList();
        for (Cam c : cams.values()) l.appendTag(c.write());
        t.setTag("cams", l);
        NBTTagList m = new NBTTagList();
        for (Mark k : marks) { NBTTagCompound x = new NBTTagCompound(); x.setInteger("c", k.cam); x.setLong("t", k.t); x.setString("x", k.text); m.appendTag(x); }
        t.setTag("marks", m);
        NBTTagList e = new NBTTagList();
        for (String[] k : events) { NBTTagCompound x = new NBTTagCompound(); x.setString("t", k[0]); x.setString("c", k[1]); x.setString("k", k[2]); x.setString("x", k[3]); e.appendTag(x); }
        t.setTag("events", e);
        NBTTagList d = new NBTTagList();
        for (long n : disarmed) d.appendTag(new net.minecraft.nbt.NBTTagLong(n));
        t.setTag("disarmed", d);
        t.setInteger("nextCam", nextCam);
        t.setInteger("nextMon", nextMon);
        return t;
    }
}
