package com.dogpound.railmap.settings;

import net.minecraft.nbt.NBTTagCompound;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A block's option values, saved in its NBT under "cfg". Unset keys fall back to the setting's default. */
public final class SettingsStore {
    private final Map<String, String> values = new LinkedHashMap<>();
    private final ISettingsHolder owner;

    public SettingsStore(ISettingsHolder owner) { this.owner = owner; }

    /** key -> definition, built once (settingDefs() may read values for its INFO lines: guarded against re-entry) */
    private Map<String, Setting> defs;
    private boolean building;

    private Map<String, Setting> defs() {
        if (defs == null) {
            if (building) return java.util.Collections.emptyMap();
            building = true;
            try {
                Map<String, Setting> m = new LinkedHashMap<>();
                for (Setting s : owner.settingDefs()) if (!s.key.isEmpty()) m.put(s.key, s);
                defs = m;
            } finally { building = false; }
        }
        return defs;
    }

    private String def(String key) {
        Setting s = defs().get(key);
        return s == null ? "" : s.def;
    }

    public String get(String key) { String v = values.get(key); return v == null ? def(key) : v; }

    public boolean bool(String key) { return Boolean.parseBoolean(get(key)); }

    public int num(String key) { try { return Integer.parseInt(get(key).trim()); } catch (NumberFormatException e) { return 0; } }

    public String text(String key) { return get(key); }
    public boolean has(String key) { return values.containsKey(key); }

    /** validated set; true if it changed */
    public boolean set(String key, String value) {
        Setting s = defs().get(key);
        if (s == null || s.type == Setting.Type.INFO) return false;
        String v = s.sanitize(value);
        if (v.equals(get(key))) return false;
        values.put(key, v);
        return true;
    }

    public void reset() { values.clear(); }

    /** raw write with no validation, for blocks that mirror their own fields into the console */
    public void put(String key, String value) { values.put(key, value); }

    public void write(NBTTagCompound t) {
        NBTTagCompound c = new NBTTagCompound();
        for (Map.Entry<String, String> e : values.entrySet()) c.setString(e.getKey(), e.getValue());
        t.setTag("cfg", c);
    }

    public void read(NBTTagCompound t) {
        values.clear();
        NBTTagCompound c = t.getCompoundTag("cfg");
        for (String k : c.getKeySet()) values.put(k, c.getString(k));
    }

    /** the definitions with their current values filled in, ready to send */
    public List<Setting> withValues() {
        List<Setting> out = new java.util.ArrayList<>();
        for (Setting s : owner.settingDefs()) {
            if (s.type != Setting.Type.INFO) s.value = get(s.key);
            out.add(s);
        }
        return out;
    }
}
