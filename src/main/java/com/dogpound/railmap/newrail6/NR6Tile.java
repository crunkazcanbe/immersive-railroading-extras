package com.dogpound.railmap.newrail6;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.infra.InfraTile;
import com.dogpound.railmap.infra.Supply;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.util.*;

/**
 * Shared base of the batch 6 #16-20 gear: InfraTile's working controls, 12 V terminal and real supply, plus the paged
 * screen every panel carries (a PAGE knob turns the screen between its pages; the last page is the panel's own event
 * log) and a list of the loaded tiles of each kind for the tickers and signal holds.
 */
public abstract class NR6Tile extends InfraTile {
    private static final Map<Integer, Set<NR6Tile>> LIVE = new HashMap<>();
    /** the panel's event log (newest first, kept with the block) */
    protected final LinkedList<String> log = new LinkedList<>();

    protected NR6Tile(Supply s) { super(s); }

    public static <T> List<T> live(World w, Class<T> c) {
        Set<NR6Tile> s = LIVE.get(w.provider.getDimension());
        if (s == null || s.isEmpty()) return Collections.emptyList();
        List<T> out = new ArrayList<>();
        for (TileEntity t : s) if (c.isInstance(t) && !t.isInvalid() && t.getWorld() == w) out.add(c.cast(t));
        return out;
    }

    @Override public void onLoad() { if (world != null && !world.isRemote) LIVE.computeIfAbsent(world.provider.getDimension(), k -> Collections.newSetFromMap(new IdentityHashMap<>())).add(this); }
    @Override public void invalidate() { super.invalidate(); drop(); }
    @Override public void onChunkUnload() { super.onChunkUnload(); drop(); }
    private void drop() { if (world != null) { Set<NR6Tile> s = LIVE.get(world.provider.getDimension()); if (s != null) s.remove(this); } }

    /** a line on the panel's EVENTS page, into the recorder too */
    protected void event(String text) {
        String stamp = String.format(Locale.ROOT, "%02d:%02d ", (world.getWorldTime() / 1000 + 6) % 24, (world.getWorldTime() % 1000) * 60 / 1000);
        log.addFirst(stamp + text.toUpperCase(Locale.ROOT));
        while (log.size() > 12) log.removeLast();
        Recorder.event(world, wiredName() + ": " + text);
    }

    /** standing alarms this panel has up (raised once when the condition starts, cleared when it goes) */
    private final Set<String> raised = new HashSet<>();

    protected void alarm(String key, AlarmManager.Cat cat, AlarmManager.Sev sev, String text) {
        if (raised.add(key)) AlarmManager.get(world).raise(world, key, cat, sev, text, pos, true);
    }

    protected void clearAlarm(String key) { if (raised.remove(key)) AlarmManager.get(world).clear(key); }

    protected int page(int n) { return Math.floorMod(ctl.get("s_page"), n); }

    /** show lines on the screen's digit rows d1..dN (blank the rest) */
    protected void screen(String... lines) {
        for (int i = 1; i <= 6; i++) text("d" + i, i <= lines.length && lines[i - 1] != null ? lines[i - 1].toUpperCase(Locale.ROOT) : "");
    }

    protected String[] logLines(String title, int n) {
        String[] o = new String[n];
        o[0] = title;
        for (int i = 1; i < n; i++) o[i] = i - 1 < log.size() ? log.get(i - 1) : (i == 1 ? "NO EVENTS" : "");
        return o;
    }

    @Override
    protected final void store(NBTTagCompound t) {
        NBTTagList l = new NBTTagList();
        for (String s : log) l.appendTag(new NBTTagString(s));
        t.setTag("nr6log", l);
        storeMore(t);
    }

    @Override
    protected final void restore(NBTTagCompound t) {
        log.clear();
        NBTTagList l = t.getTagList("nr6log", 8);
        for (int i = 0; i < l.tagCount(); i++) log.add(l.getStringTagAt(i));
        restoreMore(t);
    }

    protected void storeMore(NBTTagCompound t) {}
    protected void restoreMore(NBTTagCompound t) {}

    /** signal holds (SafetyHolds via TrackHooks): zones {x, z, radius, why} */
    public void holds(Map<Long, String> hold, List<Object[]> zones) {}

    protected static String f0(double v) { return String.format(Locale.ROOT, "%.0f", v); }
}
