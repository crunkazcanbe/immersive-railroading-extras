package com.dogpound.railmap.incident;

import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.DriverAids;
import com.dogpound.railmap.safety.SignalSighting;
import com.dogpound.railmap.server.RailProps;
import net.minecraft.world.World;

import java.util.*;

/**
 * The train recorder (her ChatGPT list #74): every locomotive carries its own "black box" (OTMR / data logger), on
 * top of the railway-wide Event Recorder. Each sample period it logs speed, throttle, train brake, where it is, the
 * signal it is approaching, traction power drawn; and as events every change that matters - throttle / brake moves,
 * emergency brake, AWS warnings, vigilance and SPAD penalties, power lost / back, wheel slip, stopping and starting.
 * It keeps recorderMinutes; when the train is in an incident its last two minutes are downloaded into the incident
 * record. Memory only (like a real recorder's buffer it is lost when the server stops).
 */
public final class TrainRecorder {
    private TrainRecorder() {}

    public static final class Sample {
        public final long tick;
        public final float kmh, throttle, brake, x, z;
        public final int kw;
        public final String signal;
        Sample(long tick, float kmh, float throttle, float brake, float x, float z, int kw, String signal) {
            this.tick = tick; this.kmh = kmh; this.throttle = throttle; this.brake = brake; this.x = x; this.z = z; this.kw = kw; this.signal = signal;
        }
    }

    public static final class Box {
        public final String id;
        public String name = "";
        public final ArrayDeque<Sample> samples = new ArrayDeque<>();
        public final ArrayDeque<String> events = new ArrayDeque<>();     // "tick|text"
        long seen;
        float lastThr = -1, lastBrk = -1, lastKmh;
        int lastBits;
        byte lastSource = -1;
        String lastSignal = "";
        Box(String id) { this.id = id; }

        void ev(long now, String t) {
            events.addLast(now + "|" + t);
            while (events.size() > 200) events.removeFirst();
        }
    }

    private static final Map<Integer, Map<String, Box>> BOXES = new HashMap<>();
    /** sample period (s) per world: 1 / 2 / 5 (console RATE) */
    private static final Map<Integer, Integer> PERIOD = new HashMap<>();

    public static Map<String, Box> boxes(World w) { return BOXES.computeIfAbsent(w.provider.getDimension(), k -> new LinkedHashMap<>()); }

    public static int period(World w) { return PERIOD.getOrDefault(w.provider.getDimension(), 1); }

    public static int cyclePeriod(World w) {
        int p = period(w), n = p == 1 ? 2 : p == 2 ? 5 : 1;
        PERIOD.put(w.provider.getDimension(), n);
        return n;
    }

    /** once a second */
    static void tick(World w, long now, int sec) {
        Map<String, Box> m = boxes(w);
        int per = period(w);
        long keep = IncidentConfig.recorderMinutes * 1200L;
        for (Locomotive l : cam72cam.mod.world.World.get(w).getEntities(Locomotive.class)) {
            if (l.isDead()) continue;
            String id = l.getUUID().toString();
            Box b = m.get(id);
            if (b == null) { if (m.size() >= 96) continue; b = new Box(id); m.put(id, b); }
            b.seen = now;
            b.name = l.tag != null && !l.tag.isEmpty() ? l.tag : l.getDefinition() == null ? "engine" : l.getDefinition().name();
            Vec3d p = l.getPosition();
            float kmh = (float) l.getCurrentSpeed().metric(), thr = l.getThrottle(), brk = l.getTrainBrakePos();
            RailProps.Props pr = RailProps.get(w).peek(l.getUUID());
            String sig = b.lastSignal;
            if (Math.abs(kmh) > 1 || sec % 5 == 0) { SignalSighting.Berth berth = SignalSighting.berthOf(w, p.x, p.y, p.z); sig = berth == null ? "" : berth.name; }
            // events: what changed
            if (b.lastThr >= 0 && Math.abs(thr - b.lastThr) >= 0.1f) b.ev(now, String.format(Locale.ROOT, "throttle %.0f%% -> %.0f%%", b.lastThr * 100, thr * 100));
            if (b.lastBrk >= 0 && Math.abs(brk - b.lastBrk) >= 0.1f) b.ev(now, brk >= 0.95f ? "EMERGENCY BRAKE at " + Math.round(Math.abs(kmh)) + " km/h" : String.format(Locale.ROOT, "train brake %.0f%% -> %.0f%%", b.lastBrk * 100, brk * 100));
            if (Math.abs(b.lastKmh) >= 1 && Math.abs(kmh) < 0.5f) b.ev(now, "stopped" + (sig.isEmpty() ? "" : " at " + sig));
            if (Math.abs(b.lastKmh) < 0.5f && Math.abs(kmh) >= 1) b.ev(now, "moving off");
            int bits = DriverAids.bits(l.getUUID(), now);
            int rise = bits & ~b.lastBits;
            if ((rise & DriverAids.AWS_HORN) != 0) b.ev(now, "AWS WARNING horn" + (sig.isEmpty() ? "" : " approaching " + sig));
            if ((rise & DriverAids.AWS_BELL) != 0) b.ev(now, "AWS bell (clear)" + (sig.isEmpty() ? "" : " approaching " + sig));
            if ((rise & DriverAids.AWS_PENALTY) != 0) b.ev(now, "AWS PENALTY BRAKE (not acknowledged)");
            if ((rise & DriverAids.VIG_PENALTY) != 0) b.ev(now, "VIGILANCE PENALTY BRAKE");
            if ((rise & DriverAids.SPAD) != 0) b.ev(now, "SPAD - SIGNAL PASSED AT DANGER" + (sig.isEmpty() ? "" : ": " + sig));
            if (!sig.equals(b.lastSignal) && !sig.isEmpty()) b.ev(now, "approaching signal " + sig);
            byte src = pr == null ? -1 : pr.source;
            if (b.lastSource >= 0 && src != b.lastSource) b.ev(now, src == 4 ? "TRACTION POWER LOST (no supply)" : src == 0 ? "no traction supply" : b.lastSource == 4 ? "traction power back" : "power from " + srcName(src));
            if (l.wreck != 0 && b.lastSource != -2) b.ev(now, "VEHICLE DERAILED / WRECKED");
            b.lastThr = thr; b.lastBrk = brk; b.lastKmh = kmh; b.lastBits = bits; b.lastSource = l.wreck != 0 ? -2 : src; b.lastSignal = sig;
            if (sec % per == 0) {
                b.samples.addLast(new Sample(now, kmh, thr, brk, (float) p.x, (float) p.z, pr == null ? 0 : pr.kwDraw, sig));
                while (!b.samples.isEmpty() && now - b.samples.peekFirst().tick > keep) b.samples.removeFirst();
            }
            while (!b.events.isEmpty() && now - Long.parseLong(b.events.peekFirst().split("\\|", 2)[0]) > keep) b.events.removeFirst();
        }
        m.values().removeIf(b -> now - b.seen > keep);
    }

    private static String srcName(byte s) { return s == 1 ? "wire / third rail" : s == 2 ? "battery" : s == 3 ? "charger" : "?"; }

    /** the download: the last `seconds` of one train, as lines (for an incident record or a printout) */
    public static List<String> download(World w, String id, int seconds) {
        Box b = boxes(w).get(id);
        List<String> o = new ArrayList<>();
        if (b == null) return o;
        long now = w.getTotalWorldTime(), from = now - seconds * 20L;
        o.add("TRAIN RECORDER " + b.name + " (" + id.substring(0, 8) + ")");
        Sample last = null;
        int n = 0;
        for (Sample s : b.samples) {
            if (s.tick < from) continue;
            if (last != null && n++ % 5 != 0 && Math.abs(s.kmh - last.kmh) < 5) continue;    // thin the steady bits
            o.add(String.format(Locale.ROOT, "%s %4.0f km/h thr %3.0f%% brk %3.0f%% %d,%d %s", Recorder.clock(w, s.tick), s.kmh, s.throttle * 100, s.brake * 100,
                    Math.round(s.x), Math.round(s.z), s.signal.isEmpty() ? "" : "sig " + s.signal));
            last = s;
        }
        for (String e : b.events) {
            String[] p = e.split("\\|", 2);
            if (Long.parseLong(p[0]) >= from) o.add(Recorder.clock(w, Long.parseLong(p[0])) + " EVENT " + p[1]);
        }
        return o;
    }
}
