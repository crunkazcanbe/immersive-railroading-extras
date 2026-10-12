package com.dogpound.railmap.weather;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.*;

/** Saved railway weather: the per-cell conditions that last (snow, ice, water, track condition, buckles), events, crews. */
public final class WeatherData extends WorldSavedData {
    private static final String NAME = "irextras_weather";

    /** one 64 x 64 block cell of railway */
    public static final class Cell {
        public final long key;
        public final int cx, cz;
        // live, recomputed every 5 s
        public transient double air = 15, rail = 15, wind = 3, vis = 10000, sft = 27;
        public transient int precip;
        public transient boolean snowing, fogged, tunnel;
        public transient String visWhy = "";
        public transient long lastQuery, lastTick;
        public transient double waterNear = -1;
        // lasting
        public double snow, ice, water, condition = 1, traffic, trackY = 64;
        public long buckled, patrolUntil;
        public int floodRisk = -1;
        public int crew = -1;
        /** weather trains (WxTrains): railhead swept clean (plough / RHTT) until, sandite on the rail until, anti-icing
         *  fluid on the contact wire until, and when leaves were last seen on the rail head (drones, the adhesion model) */
        public long clearUntil, treatUntil, antiIceUntil, leafSeen;

        Cell(long key) { this.key = key; this.cx = (int) (key >> 32); this.cz = (int) key; }

        public double flood() { return Math.max(0, water - 150); }
        public int x0() { return cx * Weather.CELL; }
        public int z0() { return cz * Weather.CELL; }
        boolean lasting() { return snow > 0.5 || ice > 0.01 || water > 1 || condition < 0.999 || buckled != 0 || floodRisk >= 0 || traffic > 0.5 || clearUntil + treatUntil + antiIceUntil + leafSeen > 0; }
    }

    public static long key(int cx, int cz) { return ((long) cx << 32) | (cz & 0xFFFFFFFFL); }

    public final Map<Long, Cell> cells = new HashMap<>();
    public long heatwaveUntil, coldSnapUntil;
    public final LinkedList<String> log = new LinkedList<>();

    public WeatherData() { super(NAME); }
    public WeatherData(String n) { super(n); }

    public static WeatherData get(World w) {
        MapStorage s = w.getPerWorldStorage();
        WeatherData d = (WeatherData) s.getOrLoadData(WeatherData.class, NAME);
        if (d == null) { d = new WeatherData(); s.setData(NAME, d); }
        return d;
    }

    public Cell cell(int cx, int cz) { return cells.computeIfAbsent(key(cx, cz), Cell::new); }

    public void log(World w, String s) {
        log.addFirst(com.dogpound.railmap.alarm.Recorder.clock(w, w.getTotalWorldTime()) + " " + s);
        while (log.size() > 50) log.removeLast();
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        cells.clear(); log.clear();
        heatwaveUntil = t.getLong("heat"); coldSnapUntil = t.getLong("cold");
        for (net.minecraft.nbt.NBTBase b : t.getTagList("cells", 10)) {
            NBTTagCompound c = (NBTTagCompound) b;
            Cell x = cell(c.getInteger("cx"), c.getInteger("cz"));
            x.snow = c.getDouble("snow"); x.ice = c.getDouble("ice"); x.water = c.getDouble("water"); x.condition = c.hasKey("cond") ? c.getDouble("cond") : 1;
            x.traffic = c.getDouble("traffic"); x.trackY = c.hasKey("ty") ? c.getDouble("ty") : 64; x.buckled = c.getLong("buckled"); x.patrolUntil = c.getLong("patrol");
            x.floodRisk = c.hasKey("risk") ? c.getInteger("risk") : -1;
            x.clearUntil = c.getLong("clr"); x.treatUntil = c.getLong("trt"); x.antiIceUntil = c.getLong("aice"); x.leafSeen = c.getLong("leaf");
        }
        for (net.minecraft.nbt.NBTBase b : t.getTagList("log", 8)) log.add(((net.minecraft.nbt.NBTTagString) b).getString());
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        t.setLong("heat", heatwaveUntil); t.setLong("cold", coldSnapUntil);
        NBTTagList l = new NBTTagList();
        for (Cell x : cells.values()) {
            if (!x.lasting()) continue;
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("cx", x.cx); c.setInteger("cz", x.cz);
            c.setDouble("snow", x.snow); c.setDouble("ice", x.ice); c.setDouble("water", x.water); c.setDouble("cond", x.condition);
            c.setDouble("traffic", x.traffic); c.setDouble("ty", x.trackY); c.setLong("buckled", x.buckled); c.setLong("patrol", x.patrolUntil); c.setInteger("risk", x.floodRisk);
            c.setLong("clr", x.clearUntil); c.setLong("trt", x.treatUntil); c.setLong("aice", x.antiIceUntil); c.setLong("leaf", x.leafSeen);
            l.appendTag(c);
        }
        t.setTag("cells", l);
        NBTTagList lg = new NBTTagList();
        for (String s : log) lg.appendTag(new net.minecraft.nbt.NBTTagString(s));
        t.setTag("log", lg);
        return t;
    }
}
