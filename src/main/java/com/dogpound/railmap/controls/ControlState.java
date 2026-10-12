package com.dogpound.railmap.controls;

import net.minecraft.nbt.NBTTagCompound;

import java.util.HashMap;
import java.util.Map;

/** the stored positions of a machine's controls (switches, keys, levers, latched E-stops) and display texts */
public final class ControlState {
    public final Map<String, Integer> vals = new HashMap<>();
    public final Map<String, String> texts = new HashMap<>();
    public final Map<String, float[]> anim = new HashMap<>();   // client: eased needles / levers
    public String pressId = "";
    public long pressAt;

    public int get(String id) { return vals.getOrDefault(id, 0); }
    public void set(String id, int v) { vals.put(id, v); }
    public String text(String id) { return texts.getOrDefault(id, ""); }

    public void write(NBTTagCompound t) {
        NBTTagCompound c = new NBTTagCompound(), x = new NBTTagCompound();
        for (Map.Entry<String, Integer> e : vals.entrySet()) c.setInteger(e.getKey(), e.getValue());
        for (Map.Entry<String, String> e : texts.entrySet()) x.setString(e.getKey(), e.getValue());
        c.setTag("_t", x);
        c.setString("_p", pressId);
        c.setLong("_pa", pressAt);
        t.setTag("ctl", c);
    }

    public void read(NBTTagCompound t) {
        if (!t.hasKey("ctl")) return;
        NBTTagCompound c = t.getCompoundTag("ctl");
        vals.clear(); texts.clear();
        for (String k : c.getKeySet()) if (!k.startsWith("_")) vals.put(k, c.getInteger(k));
        NBTTagCompound x = c.getCompoundTag("_t");
        for (String k : x.getKeySet()) texts.put(k, x.getString(k));
        pressId = c.getString("_p");
        pressAt = c.getLong("_pa");
    }
}
