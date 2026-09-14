package com.dogpound.railmap.graph;

import net.minecraft.nbt.NBTTagCompound;

/** One line of the station timetable: "<train> arrived/departed <station> at <time>". */
public final class LogEntry {
    /** World time in ticks. */
    public final long time;
    public final String train;
    public final String station;
    public final boolean arrive;

    public LogEntry(long time, String train, String station, boolean arrive) {
        this.time = time;
        this.train = train == null ? "" : train;
        this.station = station == null ? "" : station;
        this.arrive = arrive;
    }

    public NBTTagCompound toNBT() {
        NBTTagCompound t = new NBTTagCompound();
        t.setLong("t", time);
        t.setString("n", train);
        t.setString("s", station);
        t.setBoolean("a", arrive);
        return t;
    }

    public static LogEntry fromNBT(NBTTagCompound t) {
        return new LogEntry(t.getLong("t"), t.getString("n"), t.getString("s"), t.getBoolean("a"));
    }

    /** "Day 3 14:02" from a world-time tick count (MC day = 24000 ticks, 0 = 06:00). */
    public static String clock(long ticks) {
        long day = ticks / 24000 + 1;
        long t = (ticks % 24000 + 6000) % 24000;
        long h = t / 1000, m = (t % 1000) * 60 / 1000;
        return "Day " + day + " " + (h < 10 ? "0" : "") + h + ":" + (m < 10 ? "0" : "") + m;
    }
}
