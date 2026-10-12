package com.dogpound.railmap.shop;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.*;

/** the daily newspaper (one per world): today's edition, the stations it has already reported, the day's notes */
public final class NewsData extends WorldSavedData {
    private static final String NAME = "irextras_news";

    /** edition day (world day number), -1 = none yet */
    public int day = -1;
    public int number;
    public final List<String> lines = new ArrayList<>();         // the paper's text, section by section
    public final List<String> heads = new ArrayList<>();         // headlines for the stand's front board
    public final Set<String> stations = new LinkedHashSet<>();   // names already reported (new ones make the news)
    public final List<String> notes = new ArrayList<>();         // today's achievements "who|what"
    public final Map<String, Integer> trips = new HashMap<>();   // trips per train at the last edition (busiest train of the day)
    public long sold;                                            // copies sold, all stands, all time

    public NewsData() { super(NAME); }
    public NewsData(String n) { super(n); }

    public static NewsData get(World w) {
        MapStorage s = w.getPerWorldStorage();
        NewsData d = (NewsData) s.getOrLoadData(NewsData.class, NAME);
        if (d == null) { d = new NewsData(); s.setData(NAME, d); }
        return d;
    }

    public void note(String who, String what) {
        String n = who + "|" + what;
        if (notes.contains(n)) return;
        notes.add(n);
        while (notes.size() > 60) notes.remove(0);
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        day = t.hasKey("day") ? t.getInteger("day") : -1;
        number = t.getInteger("no");
        sold = t.getLong("sold");
        read(t, "lines", lines); read(t, "heads", heads); read(t, "notes", notes);
        List<String> st = new ArrayList<>();
        read(t, "stations", st);
        stations.clear(); stations.addAll(st);
        trips.clear();
        NBTTagCompound tr = t.getCompoundTag("trips");
        for (String k : tr.getKeySet()) trips.put(k, tr.getInteger(k));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        t.setInteger("day", day);
        t.setInteger("no", number);
        t.setLong("sold", sold);
        write(t, "lines", lines); write(t, "heads", heads); write(t, "notes", notes); write(t, "stations", new ArrayList<>(stations));
        NBTTagCompound tr = new NBTTagCompound();
        for (Map.Entry<String, Integer> e : trips.entrySet()) tr.setInteger(e.getKey(), e.getValue());
        t.setTag("trips", tr);
        return t;
    }

    private static void read(NBTTagCompound t, String k, List<String> into) {
        into.clear();
        NBTTagList l = t.getTagList(k, 8);
        for (int i = 0; i < l.tagCount(); i++) into.add(l.getStringTagAt(i));
    }

    private static void write(NBTTagCompound t, String k, List<String> from) {
        NBTTagList l = new NBTTagList();
        for (String s : from) l.appendTag(new NBTTagString(s));
        t.setTag(k, l);
    }
}
