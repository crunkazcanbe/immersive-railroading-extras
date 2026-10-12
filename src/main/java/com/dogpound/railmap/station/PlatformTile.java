package com.dogpound.railmap.station;

import com.dogpound.railmap.RailMapConfig;
import com.dogpound.railmap.depot.DepotTile;
import com.dogpound.railmap.server.StationData;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Shared plumbing for the platform operations gear (ChatGPT batch 3 #27 #29 #32 #33): the station it belongs to (the
 * nearest named station within RailMapConfig.stationReach, looked up every 5 s, like a ticket gate) and a per-world
 * register of the loaded ones, so the passenger model, the doors and the lamps can ask them cheaply.
 */
public abstract class PlatformTile extends DepotTile {
    private static final Map<Integer, Set<PlatformTile>> LOADED = new HashMap<>();
    protected long station = Long.MIN_VALUE;
    protected String stationName = "";

    protected PlatformTile() { super(); }
    protected PlatformTile(String[] ids, String[] names, int[] sizes) { super(ids, names, sizes); }

    public static <T extends PlatformTile> List<T> loaded(World w, Class<T> c) {
        List<T> o = new ArrayList<>();
        Set<PlatformTile> s = LOADED.get(w.provider.getDimension());
        if (s != null) for (PlatformTile t : s) if (c.isInstance(t) && !t.isInvalid()) o.add(c.cast(t));
        return o;
    }

    public static <T extends PlatformTile> List<T> at(World w, Class<T> c, long station) {
        List<T> o = loaded(w, c);
        o.removeIf(t -> t.station != station || station == Long.MIN_VALUE);
        return o;
    }

    @Override public void onLoad() { super.onLoad(); if (world != null && !world.isRemote) LOADED.computeIfAbsent(world.provider.getDimension(), k -> new HashSet<>()).add(this); }
    @Override public void invalidate() { super.invalidate(); drop(); }
    @Override public void onChunkUnload() { super.onChunkUnload(); drop(); }
    private void drop() { if (world != null) { Set<PlatformTile> s = LOADED.get(world.provider.getDimension()); if (s != null) s.remove(this); } }

    // ---- power (owner rule): 12 V control terminal AND mains (120-480 V AC grid supply via depot.Mains) for the screens
    @Override protected boolean usesMains() { return true; }
    @Override protected long load() { return 3; }
    /** 12 V and mains both there */
    public boolean live() { return powered() && mainsOk(); }

    @Override
    public int controlValue(String id) {
        if (id.startsWith("k_") || id.startsWith("s_") || id.startsWith("m_")) return ctl.get(id);
        if (id.equals("mainsl")) return live() ? 1 : 0;
        if (id.equals("pwr")) return powered() ? 1 : 0;
        if (!mainsOk()) return 0;
        return super.controlValue(id);
    }

    @Override public String ptext(String key) { return mainsOk() ? super.ptext(key) : ""; }

    @Override
    protected final String press(String id, net.minecraft.entity.player.EntityPlayer who) {
        if (!mainsOk()) return "§cThe " + wiredName().toLowerCase(java.util.Locale.ROOT) + " has no mains (" + mains.what + ")";
        return act(id, who);
    }

    /** a powered push with 12 V and mains: the message (null = default) */
    protected abstract String act(String id, net.minecraft.entity.player.EntityPlayer who);

    public long station() { return station; }
    public String stationName() { return stationName; }

    /** call every tick from work(): keeps the station up to date */
    protected void resolve() {
        if (age % 100 != 1) return;
        StationData sd = StationData.get(world);
        long best = Long.MIN_VALUE;
        double bd = (double) RailMapConfig.stationReach * RailMapConfig.stationReach;
        for (Map.Entry<Long, String> e : sd.names().entrySet()) {
            double d = BlockPos.fromLong(e.getKey()).distanceSq(pos);
            if (d < bd) { bd = d; best = e.getKey(); }
        }
        if (best != station) { station = best; stationName = best == Long.MIN_VALUE ? "" : sd.names().get(best); markDirty(); }
    }

    protected String where() { return stationName.isEmpty() ? "(no station)" : stationName; }
}
