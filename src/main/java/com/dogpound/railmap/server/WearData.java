package com.dogpound.railmap.server;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Wear and mileage of every piece of IR rolling stock, keyed by its entity UUID (stable across
 * restarts, unlike the entity id). Saved with the world as irextras_wear.dat.
 */
public final class WearData extends WorldSavedData {
    private static final String NAME = "irextras_wear";

    /** What wears out. Order = NBT layout, append only. */
    public enum Part {
        WHEELS("wheels"), BRAKES("brakes"), ENGINE("engine"), BEARINGS("bearings"), ELECTRICAL("electrics");
        public final String label;
        Part(String label) { this.label = label; }
    }

    public static final class Record {
        /** Total km ever driven, and km since the last depot visit. */
        public double km, kmSinceService;
        /** 0 (new) .. 100+ (worn out) per {@link Part}. */
        public final float[] wear = new float[Part.values().length];
        /** Bit per part: the 80% warning was already given (cleared when serviced). */
        public int warned;
        /** World time of the last depot service, 0 = never. */
        public long servicedAt;

        public float worst() {
            float w = 0;
            for (float f : wear) w = Math.max(w, f);
            return w;
        }

        public Part worstPart() {
            Part best = Part.WHEELS;
            for (Part p : Part.values()) if (wear[p.ordinal()] > wear[best.ordinal()]) best = p;
            return best;
        }
    }

    private final Map<UUID, Record> records = new HashMap<>();

    public WearData() { super(NAME); }
    public WearData(String name) { super(name); }

    public static WearData get(World world) {
        MapStorage storage = world.getPerWorldStorage();
        WearData d = (WearData) storage.getOrLoadData(WearData.class, NAME);
        if (d == null) {
            d = new WearData();
            storage.setData(NAME, d);
        }
        return d;
    }

    public Record of(UUID id) {
        return records.computeIfAbsent(id, k -> new Record());
    }

    public Record peek(UUID id) {
        return records.get(id);
    }

    public Map<UUID, Record> all() {
        return records;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        records.clear();
        NBTTagList list = nbt.getTagList("stock", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            Record r = new Record();
            r.km = t.getDouble("km");
            r.kmSinceService = t.getDouble("kmS");
            r.warned = t.getInteger("warned");
            r.servicedAt = t.getLong("serviced");
            int[] w = t.getIntArray("wear");               // stored as hundredths of a percent
            for (int p = 0; p < Math.min(w.length, r.wear.length); p++) r.wear[p] = w[p] / 100f;
            records.put(new UUID(t.getLong("hi"), t.getLong("lo")), r);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<UUID, Record> e : records.entrySet()) {
            Record r = e.getValue();
            NBTTagCompound t = new NBTTagCompound();
            t.setLong("hi", e.getKey().getMostSignificantBits());
            t.setLong("lo", e.getKey().getLeastSignificantBits());
            t.setDouble("km", r.km);
            t.setDouble("kmS", r.kmSinceService);
            t.setInteger("warned", r.warned);
            t.setLong("serviced", r.servicedAt);
            int[] w = new int[r.wear.length];
            for (int p = 0; p < w.length; p++) w[p] = Math.round(r.wear[p] * 100);
            t.setIntArray("wear", w);
            list.appendTag(t);
        }
        nbt.setTag("stock", list);
        return nbt;
    }
}
