package com.dogpound.railmap.road;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.*;

/**
 * Road + tram records per world: level crossing road statistics (queue now / longest today, vehicles delayed, longest
 * closure, the ROAD QUEUE alarm state), traffic counters, the console's traffic pause switch, and the hand-set TRAM
 * tags (on / off overrides of the name match). The vehicles themselves are never saved.
 */
public final class RoadData extends WorldSavedData {
    private static final String NAME = "irextras_road";

    public static final class Crossing {
        public int queue, maxToday, delayedToday, delayedTotal, closures;
        public long closedSince = -1, longestClose, day = -1;
        public boolean alarm;

        NBTTagCompound write(long pos) {
            NBTTagCompound t = new NBTTagCompound();
            t.setLong("p", pos); t.setInteger("max", maxToday); t.setInteger("dT", delayedToday); t.setInteger("dA", delayedTotal);
            t.setInteger("cl", closures); t.setLong("lc", longestClose); t.setLong("day", day); t.setBoolean("al", alarm);
            return t;
        }

        static Crossing read(NBTTagCompound t) {
            Crossing c = new Crossing();
            c.maxToday = t.getInteger("max"); c.delayedToday = t.getInteger("dT"); c.delayedTotal = t.getInteger("dA");
            c.closures = t.getInteger("cl"); c.longestClose = t.getLong("lc"); c.day = t.hasKey("day") ? t.getLong("day") : -1; c.alarm = t.getBoolean("al");
            return c;
        }
    }

    public final Map<Long, Crossing> crossings = new LinkedHashMap<>();
    public final Set<UUID> tramOn = new HashSet<>(), tramOff = new HashSet<>();
    public long spawned, removed;
    public boolean paused;

    public RoadData() { super(NAME); }
    public RoadData(String n) { super(n); }

    public static RoadData get(World w) {
        MapStorage s = w.getPerWorldStorage();
        RoadData d = (RoadData) s.getOrLoadData(RoadData.class, NAME);
        if (d == null) { d = new RoadData(); s.setData(NAME, d); }
        return d;
    }

    public Crossing crossing(long pos) { return crossings.computeIfAbsent(pos, k -> new Crossing()); }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        crossings.clear();
        NBTTagList l = t.getTagList("x", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < l.tagCount(); i++) crossings.put(l.getCompoundTagAt(i).getLong("p"), Crossing.read(l.getCompoundTagAt(i)));
        tramOn.clear(); tramOff.clear();
        readIds(t.getTagList("on", Constants.NBT.TAG_COMPOUND), tramOn);
        readIds(t.getTagList("off", Constants.NBT.TAG_COMPOUND), tramOff);
        spawned = t.getLong("sp"); removed = t.getLong("rm"); paused = t.getBoolean("pause");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        NBTTagList l = new NBTTagList();
        for (Map.Entry<Long, Crossing> e : crossings.entrySet()) l.appendTag(e.getValue().write(e.getKey()));
        t.setTag("x", l);
        t.setTag("on", ids(tramOn)); t.setTag("off", ids(tramOff));
        t.setLong("sp", spawned); t.setLong("rm", removed); t.setBoolean("pause", paused);
        return t;
    }

    private static NBTTagList ids(Set<UUID> s) {
        NBTTagList l = new NBTTagList();
        for (UUID u : s) { NBTTagCompound c = new NBTTagCompound(); c.setUniqueId("u", u); l.appendTag(c); }
        return l;
    }

    private static void readIds(NBTTagList l, Set<UUID> into) {
        for (int i = 0; i < l.tagCount(); i++) if (l.getCompoundTagAt(i).hasUniqueId("u")) into.add(l.getCompoundTagAt(i).getUniqueId("u"));
    }
}
