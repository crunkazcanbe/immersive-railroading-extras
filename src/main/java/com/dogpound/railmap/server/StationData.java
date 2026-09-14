package com.dogpound.railmap.server;

import com.dogpound.railmap.graph.LogEntry;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-world store of player-named stations and the arrival/departure timetable. Saved with the
 * world (railmap_stations.dat), shared by every board, panel and handheld map in that dimension.
 */
public final class StationData extends WorldSavedData {
    private static final String NAME = "railmap_stations";
    /** Timetable length. Older lines fall off; the board shows the newest ~20 anyway. */
    public static final int LOG_CAP = 60;

    /** Station block position → name. Positions are track piece / augment positions. */
    private final Map<Long, String> names = new LinkedHashMap<>();
    private final List<LogEntry> log = new ArrayList<>();

    public StationData() {
        super(NAME);
    }

    public StationData(String name) {
        super(name);
    }

    public static StationData get(World world) {
        MapStorage storage = world.getPerWorldStorage();
        StationData d = (StationData) storage.getOrLoadData(StationData.class, NAME);
        if (d == null) {
            d = new StationData();
            storage.setData(NAME, d);
        }
        return d;
    }

    public Map<Long, String> names() {
        return names;
    }

    public String nameAt(BlockPos pos) {
        return names.get(pos.toLong());
    }

    /** Empty name removes the station. */
    public void setName(BlockPos pos, String name) {
        if (name == null || name.trim().isEmpty()) names.remove(pos.toLong());
        else names.put(pos.toLong(), name.trim());
        markDirty();
    }

    public List<LogEntry> log() {
        return log;
    }

    public void addLog(LogEntry e) {
        log.add(e);
        while (log.size() > LOG_CAP) log.remove(0);
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        names.clear();
        NBTTagList st = t.getTagList("stations", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < st.tagCount(); i++) {
            NBTTagCompound c = st.getCompoundTagAt(i);
            names.put(c.getLong("p"), c.getString("n"));
        }
        log.clear();
        NBTTagList lg = t.getTagList("log", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < lg.tagCount(); i++) log.add(LogEntry.fromNBT(lg.getCompoundTagAt(i)));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        NBTTagList st = new NBTTagList();
        for (Map.Entry<Long, String> e : names.entrySet()) {
            NBTTagCompound c = new NBTTagCompound();
            c.setLong("p", e.getKey());
            c.setString("n", e.getValue());
            st.appendTag(c);
        }
        t.setTag("stations", st);
        NBTTagList lg = new NBTTagList();
        for (LogEntry e : log) lg.appendTag(e.toNBT());
        t.setTag("log", lg);
        return t;
    }
}
