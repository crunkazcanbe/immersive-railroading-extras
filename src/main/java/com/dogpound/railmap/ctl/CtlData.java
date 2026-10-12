package com.dogpound.railmap.ctl;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Custom dashboard layouts (her "next 48" list #39), saved per user ("user:Name") and per role ("role:DISPATCHER"...).
 * A layout is "WIDGET,x,y,w,h;..." on the Control Center's 12 x 8 dashboard grid. A player sees their own layout,
 * else their role's, else the default.
 */
public final class CtlData extends WorldSavedData {
    private static final String NAME = "irextras_ctl";
    public static final String DEFAULT = "TRAINS,0,0,4,3;ALARMS,4,0,4,3;POWER,8,0,4,3;FACTORY,0,3,4,2;FREIGHT,4,3,4,2;MAINT,8,3,4,2;FINANCE,0,5,6,3;GRID,6,5,6,3";
    public final Map<String, String> dashboards = new LinkedHashMap<>();

    public CtlData() { super(NAME); }
    public CtlData(String n) { super(n); }

    public static CtlData get(World w) {
        MapStorage st = w.getMapStorage();                                    // shared across dimensions: a layout is a person's, not a place's
        CtlData d = st == null ? null : (CtlData) st.getOrLoadData(CtlData.class, NAME);
        if (d == null) { d = new CtlData(); if (st != null) st.setData(NAME, d); }
        return d;
    }

    /** a layout must parse: known widgets, inside the grid, at most 24 widgets */
    public static boolean valid(String layout) {
        if (layout == null || layout.length() > 1200) return false;
        if (layout.isEmpty()) return true;
        String[] ws = layout.split(";");
        if (ws.length > 24) return false;
        for (String s : ws) {
            String[] p = s.split(",");
            if (p.length != 5) return false;
            try {
                int x = Integer.parseInt(p[1]), y = Integer.parseInt(p[2]), w = Integer.parseInt(p[3]), h = Integer.parseInt(p[4]);
                if (x < 0 || y < 0 || w < 1 || h < 1 || x + w > 12 || y + h > 8) return false;
            } catch (NumberFormatException e) { return false; }
            if (!p[0].matches("[A-Z]{2,12}")) return false;
        }
        return true;
    }

    @Override public void readFromNBT(NBTTagCompound t) { dashboards.clear(); NBTTagCompound d = t.getCompoundTag("dash"); for (String k : d.getKeySet()) dashboards.put(k, d.getString(k)); }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound t) { NBTTagCompound d = new NBTTagCompound(); for (Map.Entry<String, String> e : dashboards.entrySet()) d.setString(e.getKey(), e.getValue()); t.setTag("dash", d); return t; }
}
