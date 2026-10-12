package com.dogpound.railmap.weather;

import com.dogpound.railmap.depot.DepotClock;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.*;

/** Saved weather-train state (irextras_wxtrains.dat): planned / running runs, locos with a plough fitted, the log. */
public final class WxTrainData extends WorldSavedData {
    private static final String NAME = "irextras_wxtrains";
    public static final String[] STATES = {"PLANNED", "CALLED", "RUNNING", "DONE", "CANCELLED"};
    public static final int PLANNED = 0, CALLED = 1, RUNNING = 2, DONE = 3, CANCELLED = 4;

    public static final class Run {
        public int id, kind, state;
        public long depot, created, started, ended;
        public String loco = "", locoName = "", why = "";
        public boolean auto;
        public final Set<Long> targets = new LinkedHashSet<>(), done = new HashSet<>();

        public boolean open() { return state < DONE; }
    }

    public final List<Run> runs = new ArrayList<>();
    /** locomotives with a Snowplough Attachment bolted on */
    public final Set<String> fitted = new HashSet<>();
    public final LinkedList<String> log = new LinkedList<>();
    public int nextRun = 1;

    public WxTrainData() { super(NAME); }
    public WxTrainData(String n) { super(n); }

    public static WxTrainData get(World w) {
        MapStorage s = w.getPerWorldStorage();
        WxTrainData d = (WxTrainData) s.getOrLoadData(WxTrainData.class, NAME);
        if (d == null) { d = new WxTrainData(); s.setData(NAME, d); }
        return d;
    }

    public Run run(int id) { for (Run r : runs) if (r.id == id) return r; return null; }

    public void log(World w, String s) {
        log.addFirst(DepotClock.stamp(w) + " " + s);
        while (log.size() > 50) log.removeLast();
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        runs.clear(); fitted.clear(); log.clear();
        nextRun = Math.max(1, t.getInteger("next"));
        NBTTagList rl = t.getTagList("runs", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < rl.tagCount(); i++) {
            NBTTagCompound c = rl.getCompoundTagAt(i);
            Run r = new Run();
            r.id = c.getInteger("id"); r.kind = c.getInteger("k"); r.state = c.getInteger("st");
            r.depot = c.getLong("dp"); r.created = c.getLong("cr"); r.started = c.getLong("sa"); r.ended = c.getLong("en");
            r.loco = c.getString("loco"); r.locoName = c.getString("ln"); r.why = c.getString("why"); r.auto = c.getBoolean("auto");
            for (net.minecraft.nbt.NBTBase b : c.getTagList("tg", Constants.NBT.TAG_LONG)) r.targets.add(((NBTTagLong) b).getLong());
            for (net.minecraft.nbt.NBTBase b : c.getTagList("dn", Constants.NBT.TAG_LONG)) r.done.add(((NBTTagLong) b).getLong());
            runs.add(r);
        }
        NBTTagList fl = t.getTagList("fitted", Constants.NBT.TAG_STRING);
        for (int i = 0; i < fl.tagCount(); i++) fitted.add(fl.getStringTagAt(i));
        NBTTagList lg = t.getTagList("log", Constants.NBT.TAG_STRING);
        for (int i = 0; i < lg.tagCount(); i++) log.add(lg.getStringTagAt(i));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        t.setInteger("next", nextRun);
        NBTTagList rl = new NBTTagList();
        for (Run r : runs) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("id", r.id); c.setInteger("k", r.kind); c.setInteger("st", r.state);
            c.setLong("dp", r.depot); c.setLong("cr", r.created); c.setLong("sa", r.started); c.setLong("en", r.ended);
            c.setString("loco", r.loco); c.setString("ln", r.locoName); c.setString("why", r.why); c.setBoolean("auto", r.auto);
            NBTTagList tg = new NBTTagList(), dn = new NBTTagList();
            for (long k : r.targets) tg.appendTag(new NBTTagLong(k));
            for (long k : r.done) dn.appendTag(new NBTTagLong(k));
            c.setTag("tg", tg); c.setTag("dn", dn);
            rl.appendTag(c);
        }
        t.setTag("runs", rl);
        NBTTagList fl = new NBTTagList();
        for (String s : fitted) fl.appendTag(new NBTTagString(s));
        t.setTag("fitted", fl);
        NBTTagList lg = new NBTTagList();
        for (String s : log) lg.appendTag(new NBTTagString(s));
        t.setTag("log", lg);
        return t;
    }
}
