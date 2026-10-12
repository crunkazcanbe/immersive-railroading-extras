package com.dogpound.railmap.alarm;

import com.dogpound.railmap.graph.TrainNode;
import com.dogpound.railmap.server.TrainTracker;
import net.minecraft.world.World;

import java.util.*;

/**
 * The railway's black box (her ChatGPT list: "event recorder and replay"): while a powered Event Recorder is in the
 * world it samples every train (where, how fast) every few seconds and logs every alarm and bookmark, keeping the last
 * 15 minutes. The console's REPLAY tab scrubs back through it; the recorder prints incident reports from it.
 * Memory only (a real recorder's buffer is lost on power-down too); bookmarks and events go into the printed report.
 */
public final class Recorder {
    private Recorder() {}

    public static final class Sample {
        public final long tick;
        public final List<float[]> trains = new ArrayList<>();   // x, z, speed km/h, entity id
        public final List<String> names = new ArrayList<>();
        Sample(long tick) { this.tick = tick; }
    }

    public static final class Event {
        public final long tick;
        public final String text;
        Event(long tick, String text) { this.tick = tick; this.text = text; }
    }

    private static final class Buf {
        final ArrayDeque<Sample> samples = new ArrayDeque<>();
        final ArrayDeque<Event> events = new ArrayDeque<>();
        long poweredUntil;                                  // a recorder ticked with power recently
        int period = 40;
        long written;
    }

    private static final Map<Integer, Buf> BUF = new HashMap<>();
    public static final int KEEP_TICKS = 15 * 1200;

    private static Buf buf(World w) { return BUF.computeIfAbsent(w.provider.getDimension(), d -> new Buf()); }

    /** a powered recorder says it is running (and at what sample period) */
    public static void alive(World w, int periodTicks) {
        Buf b = buf(w);
        b.poweredUntil = w.getTotalWorldTime() + 40;
        b.period = Math.max(20, periodTicks);
    }

    public static boolean recording(World w) { return buf(w).poweredUntil > w.getTotalWorldTime(); }

    /** called every tick from the recorder tile; samples when it is time */
    public static boolean tick(World w) {
        Buf b = buf(w);
        long now = w.getTotalWorldTime();
        if (now % b.period != 0) return false;
        Sample s = new Sample(now);
        for (TrainNode t : TrainTracker.latest(w)) {
            if (!t.lead && t.consist > 1) continue;                 // one dot per train
            s.trains.add(new float[]{t.x, t.z, t.speedKmh, t.id});
            s.names.add(t.displayName());
        }
        b.samples.addLast(s);
        while (!b.samples.isEmpty() && now - b.samples.peekFirst().tick > KEEP_TICKS) b.samples.removeFirst();
        b.written++;
        return true;
    }

    public static void event(World w, String text) {
        if (w == null || w.isRemote || !recording(w)) return;
        Buf b = buf(w);
        b.events.addLast(new Event(w.getTotalWorldTime(), text));
        while (b.events.size() > 400) b.events.removeFirst();
    }

    public static List<Sample> samples(World w) { return new ArrayList<>(buf(w).samples); }
    public static List<Event> events(World w) { return new ArrayList<>(buf(w).events); }
    public static long written(World w) { return buf(w).written; }

    public static String clock(World w, long tick) {
        long t = (w.getWorldTime() - (w.getTotalWorldTime() - tick) + 6000) % 24000;
        if (t < 0) t += 24000;
        return String.format(Locale.ROOT, "%02d:%02d", t / 1000, (t % 1000) * 60 / 1000);
    }
}
