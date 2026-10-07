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
 * Per-locomotive settings and state that IR itself doesn't keep, keyed by entity UUID: whether it
 * runs electric (auto by name / forced on / forced off) and its traction battery charge.
 * Saved as irextras_props.dat.
 */
public final class RailProps extends WorldSavedData {
    private static final String NAME = "irextras_props";

    public static final byte AUTO = 0, ON = 1, OFF = 2;

    public static final class Props {
        public byte electric = AUTO;
        /** Battery charge 0..1. */
        public float battery = 1f;
        /** Last power source seen: 0 none, 1 wire/third rail, 2 battery, 3 charger, 4 flat battery. */
        public transient byte source;
        /** Destination signs: 0 automatic (route / next stop), 1 custom text, 2 off. */
        public byte signMode;
        public String signText = "";
    }

    private final Map<UUID, Props> props = new HashMap<>();

    public RailProps() { super(NAME); }
    public RailProps(String name) { super(name); }

    public static RailProps get(World world) {
        MapStorage storage = world.getPerWorldStorage();
        RailProps d = (RailProps) storage.getOrLoadData(RailProps.class, NAME);
        if (d == null) {
            d = new RailProps();
            storage.setData(NAME, d);
        }
        return d;
    }

    public Props of(UUID id) {
        return props.computeIfAbsent(id, k -> new Props());
    }

    public Props peek(UUID id) {
        return props.get(id);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        props.clear();
        NBTTagList list = nbt.getTagList("locos", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            Props p = new Props();
            p.electric = t.getByte("electric");
            p.battery = t.hasKey("battery") ? t.getFloat("battery") : 1f;
            p.signMode = t.getByte("signMode");
            p.signText = t.getString("signText");
            props.put(new UUID(t.getLong("hi"), t.getLong("lo")), p);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<UUID, Props> e : props.entrySet()) {
            NBTTagCompound t = new NBTTagCompound();
            t.setLong("hi", e.getKey().getMostSignificantBits());
            t.setLong("lo", e.getKey().getLeastSignificantBits());
            t.setByte("electric", e.getValue().electric);
            t.setFloat("battery", e.getValue().battery);
            t.setByte("signMode", e.getValue().signMode);
            t.setString("signText", e.getValue().signText == null ? "" : e.getValue().signText);
            list.appendTag(t);
        }
        nbt.setTag("locos", list);
        return nbt;
    }
}
