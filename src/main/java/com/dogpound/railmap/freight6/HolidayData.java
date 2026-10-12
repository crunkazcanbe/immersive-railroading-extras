package com.dogpound.railmap.freight6;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** which IR cars wear which decoration kit (batch 6 #30), by stock UUID - irextras_holiday.dat (none on an old world) */
public final class HolidayData extends WorldSavedData {
    private static final String NAME = "irextras_holiday";
    public static final int XMAS = 1, GHOST = 2;

    public final Map<UUID, Integer> kits = new HashMap<>();
    public long presents, treats;

    public HolidayData() { super(NAME); }
    public HolidayData(String n) { super(n); }

    public static HolidayData get(World w) {
        HolidayData d = (HolidayData) w.getPerWorldStorage().getOrLoadData(HolidayData.class, NAME);
        if (d == null) { d = new HolidayData(); w.getPerWorldStorage().setData(NAME, d); }
        return d;
    }

    public int kit(UUID u) { return u == null ? 0 : kits.getOrDefault(u, 0); }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        kits.clear();
        NBTTagList l = t.getTagList("k", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < l.tagCount(); i++) { NBTTagCompound c = l.getCompoundTagAt(i); kits.put(new UUID(c.getLong("hi"), c.getLong("lo")), c.getInteger("kit")); }
        presents = t.getLong("pr"); treats = t.getLong("tr");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        NBTTagList l = new NBTTagList();
        for (Map.Entry<UUID, Integer> e : kits.entrySet()) {
            NBTTagCompound c = new NBTTagCompound();
            c.setLong("hi", e.getKey().getMostSignificantBits()); c.setLong("lo", e.getKey().getLeastSignificantBits()); c.setInteger("kit", e.getValue());
            l.appendTag(c);
        }
        t.setTag("k", l);
        t.setLong("pr", presents); t.setLong("tr", treats);
        return t;
    }
}
