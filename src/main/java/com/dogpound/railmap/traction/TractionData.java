package com.dogpound.railmap.traction;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.*;

/** per-unit battery / hydrogen state (irextras_traction.dat); the SoC itself is RailProps.battery */
public final class TractionData extends WorldSavedData {
    private static final String NAME = "irextras_traction";

    public static final class Unit {
        /** -1 auto (name / workshop design), else forced Traction.NONE / BEMU / H2 */
        public byte forced = -1;
        /** hydrogen aboard, kg (-1 = not seen yet: arrives full) */
        public float h2kg = -1;
        public boolean fcOn = true;
        public double kwhOut, kwhRegen, kwhCharged, km, h2Used;
        /** measured consumption, Wh per km (0 = not yet) */
        public float whPerKm;
        public transient float kwNow, kwRegen, kwCharge, fcKw, rangeKm;
        public transient String note = "";

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setByte("f", forced); t.setFloat("h2", h2kg); t.setBoolean("fc", fcOn); t.setDouble("o", kwhOut); t.setDouble("r", kwhRegen);
            t.setDouble("c", kwhCharged); t.setDouble("km", km); t.setDouble("hu", h2Used); t.setFloat("wpk", whPerKm);
            return t;
        }

        static Unit read(NBTTagCompound t) {
            Unit u = new Unit();
            u.forced = t.hasKey("f") ? t.getByte("f") : -1; u.h2kg = t.hasKey("h2") ? t.getFloat("h2") : -1; u.fcOn = !t.hasKey("fc") || t.getBoolean("fc");
            u.kwhOut = t.getDouble("o"); u.kwhRegen = t.getDouble("r"); u.kwhCharged = t.getDouble("c"); u.km = t.getDouble("km"); u.h2Used = t.getDouble("hu"); u.whPerKm = t.getFloat("wpk");
            return u;
        }
    }

    private final Map<UUID, Unit> units = new HashMap<>();
    public final LinkedList<String> log = new LinkedList<>();

    public TractionData() { super(NAME); }
    public TractionData(String n) { super(n); }

    public static TractionData get(World w) {
        TractionData d = (TractionData) w.getPerWorldStorage().getOrLoadData(TractionData.class, NAME);
        if (d == null) { d = new TractionData(); w.getPerWorldStorage().setData(NAME, d); }
        return d;
    }

    public Unit of(UUID id) { return units.computeIfAbsent(id, k -> new Unit()); }
    public Unit peek(UUID id) { return units.get(id); }
    public Map<UUID, Unit> all() { return units; }

    public void note(World w, String s) {
        log.addFirst(com.dogpound.railmap.alarm.Recorder.clock(w, w.getTotalWorldTime()) + " " + s);
        while (log.size() > 40) log.removeLast();
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        units.clear();
        NBTTagList l = t.getTagList("u", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < l.tagCount(); i++) { NBTTagCompound c = l.getCompoundTagAt(i); units.put(new UUID(c.getLong("hi"), c.getLong("lo")), Unit.read(c)); }
        log.clear();
        NBTTagList g = t.getTagList("log", Constants.NBT.TAG_STRING);
        for (int i = 0; i < g.tagCount(); i++) log.add(g.getStringTagAt(i));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        NBTTagList l = new NBTTagList();
        for (Map.Entry<UUID, Unit> e : units.entrySet()) { NBTTagCompound c = e.getValue().write(); c.setLong("hi", e.getKey().getMostSignificantBits()); c.setLong("lo", e.getKey().getLeastSignificantBits()); l.appendTag(c); }
        t.setTag("u", l);
        NBTTagList g = new NBTTagList();
        for (String s : log) g.appendTag(new net.minecraft.nbt.NBTTagString(s));
        t.setTag("log", g);
        return t;
    }
}
