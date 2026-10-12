package com.dogpound.railmap.drone;

import com.dogpound.railmap.depot.DepotClock;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.*;

/**
 * Saved drone records (irextras_drones.dat): every flight (dock, mission, how far, what it found, how it ended), every
 * finding (what, where, which flight, OPEN / ACKNOWLEDGED / CLOSED) and the drone log. Old worlds start empty.
 */
public final class DroneData extends WorldSavedData {
    private static final String NAME = "irextras_drones";
    public static final String[] MISSIONS = {"TRACK", "OHLE", "STRUCTURES", "TUNNEL"};
    public static final String[] STATES = {"OPEN", "ACKED", "CLOSED"};

    public static final class Flight {
        public int id, mission, findings;
        public long dock, start, end;
        public double metres, maxAlt, minBattery = 100;
        public String result = "", drone = "";
    }

    public static final class Finding {
        public int id, flight, state;
        public String kind = "", text = "", key = "";
        public long pos, at;
        public boolean major;
    }

    public final List<Flight> flights = new ArrayList<>();
    public final List<Finding> findings = new ArrayList<>();
    public final LinkedList<String> log = new LinkedList<>();
    public int nextFlight = 1, nextFinding = 1, nextDrone = 1;

    public DroneData() { super(NAME); }
    public DroneData(String n) { super(n); }

    public static DroneData get(World w) {
        MapStorage s = w.getPerWorldStorage();
        DroneData d = (DroneData) s.getOrLoadData(DroneData.class, NAME);
        if (d == null) { d = new DroneData(); s.setData(NAME, d); }
        return d;
    }

    public Flight flight(int id) { for (Flight f : flights) if (f.id == id) return f; return null; }
    public Finding finding(int id) { for (Finding f : findings) if (f.id == id) return f; return null; }

    public Flight newFlight(World w, long dock, int mission, String drone) {
        Flight f = new Flight();
        f.id = nextFlight++;
        f.dock = dock; f.mission = mission; f.drone = drone;
        f.start = w.getTotalWorldTime();
        flights.add(0, f);
        while (flights.size() > 40) flights.remove(flights.size() - 1);
        markDirty();
        return f;
    }

    /** a finding, unless the same thing (key) is already open from the last game day; null when it was a repeat */
    public Finding find(World w, int flight, String kind, String key, String text, BlockPos at, boolean major) {
        long now = w.getTotalWorldTime();
        for (Finding f : findings) if (f.key.equals(key) && f.state != 2 && now - f.at < 24000) return null;
        Finding f = new Finding();
        f.id = nextFinding++;
        f.flight = flight; f.kind = kind; f.key = key; f.text = text; f.major = major;
        f.pos = at == null ? 0 : at.toLong();
        f.at = now;
        findings.add(0, f);
        while (findings.size() > 300) {                                    // drop the oldest closed one, else the oldest
            Finding old = null;
            for (int i = findings.size() - 1; i >= 0 && old == null; i--) if (findings.get(i).state == 2) old = findings.get(i);
            findings.remove(old == null ? findings.get(findings.size() - 1) : old);
        }
        Flight fl = flight(flight);
        if (fl != null) fl.findings++;
        markDirty();
        return f;
    }

    public void log(World w, String s) {
        log.addFirst(DepotClock.stamp(w) + " " + s);
        while (log.size() > 60) log.removeLast();
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        flights.clear(); findings.clear(); log.clear();
        nextFlight = Math.max(1, t.getInteger("nf")); nextFinding = Math.max(1, t.getInteger("nd")); nextDrone = Math.max(1, t.getInteger("nr"));
        NBTTagList fl = t.getTagList("flights", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < fl.tagCount(); i++) {
            NBTTagCompound c = fl.getCompoundTagAt(i);
            Flight f = new Flight();
            f.id = c.getInteger("id"); f.mission = c.getInteger("m"); f.findings = c.getInteger("fd"); f.dock = c.getLong("dock"); f.start = c.getLong("s"); f.end = c.getLong("e");
            f.metres = c.getDouble("mt"); f.maxAlt = c.getDouble("alt"); f.minBattery = c.hasKey("bat") ? c.getDouble("bat") : 100; f.result = c.getString("r"); f.drone = c.getString("dr");
            flights.add(f);
        }
        NBTTagList dl = t.getTagList("findings", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < dl.tagCount(); i++) {
            NBTTagCompound c = dl.getCompoundTagAt(i);
            Finding f = new Finding();
            f.id = c.getInteger("id"); f.flight = c.getInteger("fl"); f.state = c.getInteger("st"); f.kind = c.getString("k"); f.text = c.getString("t"); f.key = c.getString("key");
            f.pos = c.getLong("p"); f.at = c.getLong("at"); f.major = c.getBoolean("mj");
            findings.add(f);
        }
        NBTTagList lg = t.getTagList("log", Constants.NBT.TAG_STRING);
        for (int i = 0; i < lg.tagCount(); i++) log.add(lg.getStringTagAt(i));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        t.setInteger("nf", nextFlight); t.setInteger("nd", nextFinding); t.setInteger("nr", nextDrone);
        NBTTagList fl = new NBTTagList();
        for (Flight f : flights) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("id", f.id); c.setInteger("m", f.mission); c.setInteger("fd", f.findings); c.setLong("dock", f.dock); c.setLong("s", f.start); c.setLong("e", f.end);
            c.setDouble("mt", f.metres); c.setDouble("alt", f.maxAlt); c.setDouble("bat", f.minBattery); c.setString("r", f.result); c.setString("dr", f.drone);
            fl.appendTag(c);
        }
        t.setTag("flights", fl);
        NBTTagList dl = new NBTTagList();
        for (Finding f : findings) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("id", f.id); c.setInteger("fl", f.flight); c.setInteger("st", f.state); c.setString("k", f.kind); c.setString("t", f.text); c.setString("key", f.key);
            c.setLong("p", f.pos); c.setLong("at", f.at); c.setBoolean("mj", f.major);
            dl.appendTag(c);
        }
        t.setTag("findings", dl);
        NBTTagList lg = new NBTTagList();
        for (String s : log) lg.appendTag(new net.minecraft.nbt.NBTTagString(s));
        t.setTag("log", lg);
        return t;
    }
}
