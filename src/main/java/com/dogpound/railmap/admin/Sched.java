package com.dogpound.railmap.admin;

import com.dogpound.railmap.RailMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.lang.reflect.Field;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Load-aware simulation (her ChatGPT list #120) and the subsystem controls behind admin pause (#117).
 * <ul>
 *   <li><b>Active cells</b>: 64-block cells near a player, or near a moving train, are ACTIVE (rebuilt each second).</li>
 *   <li><b>{@link #due}</b>: background jobs (weather cells, station catchments) ask before working on one thing; in an
 *   inactive cell they run {@link AdminConfig#inactiveSlowdown} times less often (twice that again while the server is
 *   over 50 ms a tick). The jobs integrate the elapsed time, so totals stay right. Occupancy, signalling, protection,
 *   train movement and power accounting never ask - they always run at full rate.</li>
 *   <li><b>Profiler + pause</b>: every IR Extras world/server tick handler is timed (avg / worst per tick, share of the
 *   50 ms budget) and non-safety subsystems can be paused. Done by wrapping their listeners on the event bus once, at
 *   server start, so no ticker's code needs touching.</li>
 * </ul>
 */
public final class Sched {
    private Sched() {}

    // ---- active cells ---------------------------------------------------------------------------------------------
    private static final Map<Integer, Set<Long>> ACTIVE = new HashMap<>();
    private static final Map<Integer, Long> BUILT = new HashMap<>();
    private static final Map<String, Long> LAST = new HashMap<>();
    /** job -> [runs, skips] */
    static final Map<String, long[]> JOBS = new TreeMap<>();

    static long key(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xffffffffL); }

    static Set<Long> cells(World w) {
        int dim = w.provider.getDimension();
        long now = w.getTotalWorldTime();
        Long b = BUILT.get(dim);
        Set<Long> s = ACTIVE.get(dim);
        if (s != null && b != null && now - b < 20 && now >= b) return s;
        s = new HashSet<>();
        for (EntityPlayer p : w.playerEntities) mark(s, p.posX, p.posZ, AdminConfig.activeRadius);
        try {
            for (cam72cam.immersiverailroading.entity.Locomotive l : cam72cam.mod.world.World.get(w).getEntities(cam72cam.immersiverailroading.entity.Locomotive.class))
                if (l.getCurrentSpeed() != null && Math.abs(l.getCurrentSpeed().metric()) > 1) mark(s, l.getPosition().x, l.getPosition().z, AdminConfig.activeRadius / 2);
        } catch (LinkageError | RuntimeException ignored) { }
        ACTIVE.put(dim, s);
        BUILT.put(dim, now);
        return s;
    }

    private static void mark(Set<Long> s, double x, double z, int r) {
        int cx = (int) Math.floor(x) >> 6, cz = (int) Math.floor(z) >> 6, n = r / 64 + 1;
        for (int i = -n; i <= n; i++) for (int k = -n; k <= n; k++) s.add(key(cx + i, cz + k));
    }

    public static boolean active(World w, int x, int z) { return !AdminConfig.loadAware || cells(w).contains(key(x >> 6, z >> 6)); }

    public static int activeCells(World w) { return cells(w).size(); }

    /** average milliseconds per server tick over the last 100 ticks */
    public static double mspt(MinecraftServer s) {
        if (s == null || s.tickTimeArray == null) return 0;
        long t = 0;
        for (long v : s.tickTimeArray) t += v;
        return t / (double) s.tickTimeArray.length / 1e6;
    }

    public static int slowdown(World w) { return Math.max(1, AdminConfig.inactiveSlowdown) * (mspt(w.getMinecraftServer()) > 50 ? 2 : 1); }

    /**
     * May background job `job` work on thing `id` at (x, z) now? Its caller normally does it every `period` ticks:
     * always yes in an active cell, otherwise once every period x slowdown. (Call it only on the ticks the job would run.)
     */
    public static boolean due(World w, String job, long id, int x, int z, long period) {
        long[] st = JOBS.computeIfAbsent(job, k -> new long[2]);
        long now = w.getTotalWorldTime();
        String k = job + w.provider.getDimension() + ":" + id;
        if (!active(w, x, z)) {
            Long last = LAST.get(k);
            if (last != null && now - last < period * slowdown(w) && now >= last) { st[1]++; return false; }
        }
        LAST.put(k, now);
        st[0]++;
        if (LAST.size() > 20000) LAST.clear();
        return true;
    }

    // ---- profiler + pause --------------------------------------------------------------------------------------
    /** handlers that keep the railway safe or keep exact accounting: never paused */
    static final Set<String> SAFETY = new HashSet<>(Arrays.asList("TrainTracker", "SignalEngine", "Protection", "Autopilot", "SafetyTicker", "TrainSim",
            "TrainDoors", "TrackCondition", "Electric", "GridTicker", "Admin", "Access"));
    static final Map<String, Long> PAUSED = new TreeMap<>();
    static final List<Timed> TIMED = new ArrayList<>();
    static long resetTick, resetNs;
    private static boolean installed;
    static String installError = "";

    public static final class Timed implements IEventListener {
        final IEventListener in;
        public final String name, cls;
        public long calls, ns, worst;
        Timed(IEventListener in, String cls, String name) { this.in = in; this.cls = cls; this.name = name; }

        @Override
        public void invoke(Event e) {
            if (!PAUSED.isEmpty() && PAUSED.containsKey(cls) && !SAFETY.contains(cls)) return;
            long t = System.nanoTime();
            try { in.invoke(e); } finally {
                long d = System.nanoTime() - t;
                ns += d; calls++;
                if (d > worst) worst = d;
            }
        }

        @Override public String toString() { return "IRExtras-timed[" + in + "]"; }
    }

    private static final Pattern ASM = Pattern.compile("ASM: (?:class )?com\\.dogpound\\.railmap\\.([\\w.$]+?)(?:@[0-9a-f]+)? (\\w+)\\(");

    /** once per game session (listeners outlive a single-player world): wrap every IR Extras tick handler */
    static void install(MinecraftServer server) {
        PAUSED.clear();
        reset(server);
        if (installed || !AdminConfig.profiler) return;
        installed = true;
        try {
            Field f = EventBus.class.getDeclaredField("busID");
            f.setAccessible(true);
            int bus = f.getInt(MinecraftForge.EVENT_BUS);
            // ponytail: reflection on the event bus so no ticker file is edited; if a loader changes the bus it just logs and profiling is off
            for (Event ev : new Event[]{new TickEvent.WorldTickEvent(Side.SERVER, TickEvent.Phase.END, null), new TickEvent.ServerTickEvent(TickEvent.Phase.END)}) {
                ListenerList ll = ev.getListenerList();
                EventPriority pr = EventPriority.NORMAL;
                List<Object[]> mine = new ArrayList<>();
                for (IEventListener l : ll.getListeners(bus)) {
                    if (l instanceof EventPriority p) { pr = p; continue; }
                    if (l instanceof Timed) continue;
                    Matcher m = ASM.matcher(l.toString());
                    if (m.find()) mine.add(new Object[]{l, pr, m.group(1), m.group(2)});
                }
                for (Object[] o : mine) {
                    String full = (String) o[2];
                    String cls = full.substring(full.lastIndexOf('.') + 1).replaceAll("\\$.*", "");
                    Timed t = new Timed((IEventListener) o[0], cls, cls + "." + o[3]);
                    ll.unregister(bus, (IEventListener) o[0]);
                    ll.register(bus, (EventPriority) o[1], t);
                    TIMED.add(t);
                }
            }
            RailMap.LOG.info("[IR Extras] admin profiler: timing {} tick handlers", TIMED.size());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            installError = e.toString();
            RailMap.LOG.warn("[IR Extras] admin profiler unavailable: {}", e.toString());
        }
    }

    static void reset(MinecraftServer s) {
        for (Timed t : TIMED) { t.calls = t.ns = t.worst = 0; }
        resetTick = s == null ? 0 : s.getTickCounter();
        resetNs = System.nanoTime();
        JOBS.clear();
    }

    /** subsystem names (handler classes) that exist */
    public static Set<String> subsystems() { Set<String> o = new TreeSet<>(); for (Timed t : TIMED) o.add(t.cls); return o; }

    public static String pause(World w, String name, int minutes, String who) {
        String n = match(name);
        if (n == null) return "§cNo subsystem " + name + " - one of " + subsystems();
        if (SAFETY.contains(n)) return "§c" + n + " keeps trains safe / power accounted - it can't be paused (use safe-stop for a train)";
        PAUSED.put(n, w.getTotalWorldTime() + Math.max(1, Math.min(AdminConfig.pauseMaxMinutes, minutes)) * 1200L);
        com.dogpound.railmap.alarm.AlarmManager.get(w).raise(w, "admin:pause:" + n, com.dogpound.railmap.alarm.AlarmManager.Cat.CONSOLE, com.dogpound.railmap.alarm.AlarmManager.Sev.INFO,
                "SUBSYSTEM PAUSED: " + n + " by " + who, null, true);
        com.dogpound.railmap.alarm.Recorder.event(w, "ADMIN: " + who + " paused " + n);
        return "§e" + n + " paused for " + Math.min(AdminConfig.pauseMaxMinutes, minutes) + " min";
    }

    public static String resume(World w, String name, String who) {
        String n = match(name);
        if (n == null || PAUSED.remove(n) == null) return "§7" + name + " isn't paused";
        com.dogpound.railmap.alarm.AlarmManager.get(w).clear("admin:pause:" + n);
        com.dogpound.railmap.alarm.Recorder.event(w, "ADMIN: " + who + " resumed " + n);
        return "§a" + n + " running again";
    }

    static void expire(World w) {
        long now = w.getTotalWorldTime();
        for (Iterator<Map.Entry<String, Long>> it = PAUSED.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Long> e = it.next();
            if (now >= e.getValue()) { it.remove(); com.dogpound.railmap.alarm.AlarmManager.get(w).clear("admin:pause:" + e.getKey()); com.dogpound.railmap.alarm.Recorder.event(w, "ADMIN: pause of " + e.getKey() + " ran out"); }
        }
    }

    static String match(String name) {
        for (String s : subsystems()) if (s.equalsIgnoreCase(name)) return s;
        return null;
    }

    /** profiler table: name, avg us per tick, worst us, % of the 50 ms budget, calls */
    public static List<String> report(MinecraftServer s) {
        List<String> o = new ArrayList<>();
        long ticks = Math.max(1, (s == null ? 0 : s.getTickCounter()) - resetTick);
        if (TIMED.isEmpty()) { o.add("Profiler off" + (installError.isEmpty() ? "" : ": " + installError)); return o; }
        List<Timed> ts = new ArrayList<>(TIMED);
        ts.sort((a, b) -> Long.compare(b.ns, a.ns));
        double sum = 0;
        for (Timed t : ts) sum += t.ns;
        o.add(String.format(Locale.ROOT, "IR Extras ticking: %.3f ms/tick (%.1f%% of 50 ms) over %d ticks; server %.1f ms/tick", sum / ticks / 1e6, sum / ticks / 5e5, ticks, mspt(s)));
        for (Timed t : ts)
            o.add(String.format(Locale.ROOT, "%-28s %7.1f us avg  %7.0f us worst  %5.2f%%%s", t.name, t.ns / (double) ticks / 1e3, t.worst / 1e3, t.ns / (double) ticks / 5e5,
                    PAUSED.containsKey(t.cls) ? "  PAUSED" : SAFETY.contains(t.cls) ? "  (safety)" : ""));
        for (Map.Entry<String, long[]> e : JOBS.entrySet()) {
            long all = e.getValue()[0] + e.getValue()[1];
            o.add(String.format(Locale.ROOT, "load-aware %-12s ran %d, deferred %d (%.0f%% saved)", e.getKey(), e.getValue()[0], e.getValue()[1], all == 0 ? 0 : 100.0 * e.getValue()[1] / all));
        }
        return o;
    }

    /** total IR Extras tick cost, ms per tick, since the last reset */
    public static double costMs(MinecraftServer s) {
        long ticks = Math.max(1, (s == null ? 0 : s.getTickCounter()) - resetTick);
        double sum = 0;
        for (Timed t : TIMED) sum += t.ns;
        return sum / ticks / 1e6;
    }
}
