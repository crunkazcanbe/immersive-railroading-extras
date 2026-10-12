package com.dogpound.railmap.biz;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.economy.CompanyData;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.LinkedList;

/**
 * The world + business layer's save (one per world, "irextras_biz"). Each system keeps its own part under its own key,
 * so a save from before any of this simply has no keys and every part starts empty: nothing changes in an old world
 * until somebody uses a desk, a board or a command.
 */
public final class BizData extends WorldSavedData {
    private static final String NAME = "irextras_biz";
    public static final long DAY = CompanyData.DAY;

    /** one system of the layer: saved under its key, ticked every 5 s, and once a day */
    public interface Part {
        String key();
        void read(NBTTagCompound t);
        NBTTagCompound write();
        default void tick(World w, BizData d, long now) {}
        default void daily(World w, BizData d, long day) {}
        /** once, in the last 5 s of the day - before the passenger day's counters roll over */
        default void dayEnd(World w, BizData d, long day) {}
    }

    public final Capacity capacity = new Capacity();
    public final Towns towns = new Towns();
    public final Journeys journeys = new Journeys();
    public final Intermodal intermodal = new Intermodal();
    public final Custody custody = new Custody();
    public final Environment env = new Environment();
    public final Research research = new Research();
    public final Insurance insurance = new Insurance();
    public final Regulator regulator = new Regulator();
    public final Deals deals = new Deals();
    public final Securities securities = new Securities();
    public final Mastery mastery = new Mastery();
    public final Pso pso = new Pso();
    public final ScenarioGen scenario = new ScenarioGen();
    private final Part[] parts = {capacity, towns, journeys, intermodal, custody, env, research, insurance, regulator, deals, securities, mastery, pso, scenario};

    /** the layer's own log for the console (newest first) */
    public final LinkedList<String> log = new LinkedList<>();
    private long lastDay = -1, lastDayEnd = -1;

    public BizData() { super(NAME); }
    public BizData(String n) { super(n); }

    public static BizData get(World w) {
        MapStorage s = w.getPerWorldStorage();
        BizData d = (BizData) s.getOrLoadData(BizData.class, NAME);
        if (d == null) { d = new BizData(); s.setData(NAME, d); }
        return d;
    }

    public void note(World w, String text) {
        log.addFirst(com.dogpound.railmap.depot.DepotClock.stamp(w) + " " + text);
        while (log.size() > 80) log.removeLast();
        markDirty();
    }

    public void tick(World w) {
        long now = w.getTotalWorldTime();
        for (Part p : parts) {
            try { p.tick(w, this, now); } catch (RuntimeException e) { com.dogpound.railmap.RailMap.LOG.warn("[IR Extras] biz {} tick failed: {}", p.key(), e.toString()); }
        }
        long day = now / DAY;
        if (now % DAY >= DAY - 100 && lastDayEnd != day) {
            lastDayEnd = day;
            for (Part p : parts) {
                try { p.dayEnd(w, this, day); } catch (RuntimeException e) { com.dogpound.railmap.RailMap.LOG.warn("[IR Extras] biz {} day-end failed: {}", p.key(), e.toString()); }
            }
        }
        if (lastDay < 0) lastDay = day;
        if (day != lastDay) {
            lastDay = day;
            for (Part p : parts) {
                try { p.daily(w, this, day); } catch (RuntimeException e) { com.dogpound.railmap.RailMap.LOG.warn("[IR Extras] biz {} daily failed: {}", p.key(), e.toString()); }
            }
        }
        markDirty();
    }

    // ---- shared helpers ---------------------------------------------------------------------------------------------
    public static Company company(World w, String nameOrMark) { return CompanyData.get(w).byName(nameOrMark); }

    /** a company pays; false (and an alarm) if its operating account can't */
    public static boolean pay(World w, Company c, Company.Kind k, long cents, String memo) {
        if (c == null) return false;
        if (CompanyData.get(w).debit(w, c, k, cents, memo, false)) return true;
        alarm(w, "biz:" + c.id + ":" + k.name(), AlarmManager.Sev.MINOR, c.name + " can't pay " + com.dogpound.railmap.economy.Coins.fmt(cents) + ": " + memo, null);
        return false;
    }

    public static void alarm(World w, String key, AlarmManager.Sev sev, String text, BlockPos at) {
        AlarmManager.get(w).raise(w, key, AlarmManager.Cat.GENERAL, sev, text, at, false);
    }

    public static String hhmm(long ticks) { long t = (ticks + 6000) % 24000; return String.format(java.util.Locale.ROOT, "%02d:%02d", t / 1000, (t % 1000) * 60 / 1000); }

    // ---- save ---------------------------------------------------------------------------------------------------------
    @Override
    public void readFromNBT(NBTTagCompound t) {
        for (Part p : parts) if (t.hasKey(p.key())) {
            try { p.read(t.getCompoundTag(p.key())); } catch (RuntimeException e) { com.dogpound.railmap.RailMap.LOG.warn("[IR Extras] biz {} load failed: {}", p.key(), e.toString()); }
        }
        log.clear();
        net.minecraft.nbt.NBTTagList l = t.getTagList("log", 8);
        for (int i = 0; i < l.tagCount(); i++) log.add(l.getStringTagAt(i));
        lastDay = t.hasKey("day") ? t.getLong("day") : -1;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        for (Part p : parts) t.setTag(p.key(), p.write());
        net.minecraft.nbt.NBTTagList l = new net.minecraft.nbt.NBTTagList();
        for (String s : log) l.appendTag(new net.minecraft.nbt.NBTTagString(s));
        t.setTag("log", l);
        t.setLong("day", lastDay);
        return t;
    }
}
