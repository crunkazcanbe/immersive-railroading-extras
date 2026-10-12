package com.dogpound.railmap.steam;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.*;

/**
 * Per steam locomotive / tender (by entity UUID): boiler scale, the hardness of the water aboard, the ash pan, washout
 * history. Saved as irextras_steam.dat - a world without it starts every boiler clean.
 */
public final class SteamData extends WorldSavedData {
    private static final String NAME = "irextras_steam";

    public static final class Boiler {
        /** scale on the heating surfaces, mm */
        public float scaleMm;
        /** hardness of the water aboard, ppm CaCO3 (-1 = not known: SteamConfig.defaultPpm) */
        public float ppm = -1;
        /** ash in the ash pan, kg */
        public float ashKg;
        /** hours in steam since the last washout */
        public double steamH;
        public long washedAt;
        public int washouts;
        public boolean leak;
        public String name = "";

        float hardness() { return ppm < 0 ? SteamConfig.defaultPpm : ppm; }

        /** water of `ppmIn` hardness mixed into `aboardL` litres already aboard */
        public void mix(float ppmIn, double addL, double aboardL) {
            double was = hardness();
            ppm = (float) ((was * Math.max(0, aboardL) + ppmIn * addL) / Math.max(1, aboardL + addL));
        }

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setFloat("sc", scaleMm); t.setFloat("pp", ppm); t.setFloat("ash", ashKg); t.setDouble("sh", steamH);
            t.setLong("wa", washedAt); t.setInteger("wn", washouts); t.setBoolean("lk", leak); t.setString("n", name);
            return t;
        }

        static Boiler read(NBTTagCompound t) {
            Boiler b = new Boiler();
            b.scaleMm = t.getFloat("sc"); b.ppm = t.hasKey("pp") ? t.getFloat("pp") : -1; b.ashKg = t.getFloat("ash"); b.steamH = t.getDouble("sh");
            b.washedAt = t.getLong("wa"); b.washouts = t.getInteger("wn"); b.leak = t.getBoolean("lk"); b.name = t.getString("n");
            return b;
        }
    }

    private final Map<UUID, Boiler> boilers = new HashMap<>();
    public final LinkedList<String> log = new LinkedList<>();

    public SteamData() { super(NAME); }
    public SteamData(String n) { super(n); }

    public static SteamData get(World w) {
        SteamData d = (SteamData) w.getPerWorldStorage().getOrLoadData(SteamData.class, NAME);
        if (d == null) { d = new SteamData(); w.getPerWorldStorage().setData(NAME, d); }
        return d;
    }

    public Boiler of(UUID id) { return boilers.computeIfAbsent(id, k -> new Boiler()); }
    public Boiler peek(UUID id) { return boilers.get(id); }
    public Map<UUID, Boiler> all() { return boilers; }

    public void note(World w, String s) {
        log.addFirst(com.dogpound.railmap.alarm.Recorder.clock(w, w.getTotalWorldTime()) + " " + s);
        while (log.size() > 40) log.removeLast();
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        boilers.clear();
        NBTTagList l = t.getTagList("b", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            boilers.put(new UUID(c.getLong("hi"), c.getLong("lo")), Boiler.read(c));
        }
        log.clear();
        NBTTagList g = t.getTagList("log", Constants.NBT.TAG_STRING);
        for (int i = 0; i < g.tagCount(); i++) log.add(g.getStringTagAt(i));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        NBTTagList l = new NBTTagList();
        for (Map.Entry<UUID, Boiler> e : boilers.entrySet()) {
            NBTTagCompound c = e.getValue().write();
            c.setLong("hi", e.getKey().getMostSignificantBits()); c.setLong("lo", e.getKey().getLeastSignificantBits());
            l.appendTag(c);
        }
        t.setTag("b", l);
        NBTTagList g = new NBTTagList();
        for (String s : log) g.appendTag(new NBTTagString(s));
        t.setTag("log", g);
        return t;
    }
}
