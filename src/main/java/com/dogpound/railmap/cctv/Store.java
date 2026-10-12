package com.dogpound.railmap.cctv;

import com.dogpound.railmap.RailMap;
import net.minecraft.world.World;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;

/**
 * NVR recordings on disk (world/irextras_cctv/cam_N.rec): a ring buffer of fixed-size frames per camera, sized by the
 * hard drives in the NVR (CctvConfig.framesPerTB per TB, shared out over its recording cameras). Header: magic, slots,
 * w, h; then per slot: world time (0 = empty) + w*h RGB555 pixels. Server thread only.
 */
public final class Store {
    private Store() {}

    private static final int MAGIC = 0x43435456;   // "CCTV"
    private static final int HEAD = 16;

    private static final class Idx { int slots, w, h; long[] times; int head; }
    private static final Map<String, Idx> CACHE = new HashMap<>();

    static File dir(World w) {
        File d = new File(w.getSaveHandler().getWorldDirectory(), "irextras_cctv");
        if (!d.exists() && !d.mkdirs()) RailMap.LOG.warn("[IR Extras] CCTV: can't make {}", d);
        return d;
    }

    private static File file(World w, int cam) { return new File(dir(w), "cam_" + cam + ".rec"); }
    private static String key(World w, int cam) { return w.getSaveHandler().getWorldDirectory().getAbsolutePath() + "#" + cam; }

    private static Idx index(World w, int cam) {
        String k = key(w, cam);
        Idx x = CACHE.get(k);
        if (x != null) return x;
        File f = file(w, cam);
        if (!f.exists()) return null;
        try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
            if (r.readInt() != MAGIC) return null;
            x = new Idx();
            x.slots = r.readInt(); x.w = r.readInt(); x.h = r.readInt();
            if (x.slots <= 0 || x.slots > 200000 || x.w <= 0 || x.h <= 0) return null;
            x.times = new long[x.slots];
            long best = -1;
            for (int i = 0; i < x.slots; i++) {
                r.seek(HEAD + (long) i * rec(x));
                x.times[i] = r.readLong();
                if (x.times[i] > best) { best = x.times[i]; x.head = (i + 1) % x.slots; }
            }
            CACHE.put(k, x);
            return x;
        } catch (IOException e) { return null; }
    }

    private static long rec(Idx x) { return 8L + 2L * x.w * x.h; }

    /** make (or re-make, wiping it) a camera's ring with this many slots */
    public static void format(World w, int cam, int slots) {
        CACHE.remove(key(w, cam));
        File f = file(w, cam);
        if (slots <= 0) { if (f.exists() && !f.delete()) RailMap.LOG.warn("[IR Extras] CCTV: can't delete {}", f); return; }
        Idx x = new Idx();
        x.slots = slots; x.w = CctvConfig.snapW; x.h = CctvConfig.snapH; x.times = new long[slots];
        try (RandomAccessFile r = new RandomAccessFile(f, "rw")) {
            r.setLength(0);
            r.writeInt(MAGIC); r.writeInt(slots); r.writeInt(x.w); r.writeInt(x.h);
            r.setLength(HEAD + slots * rec(x));
            CACHE.put(key(w, cam), x);
        } catch (IOException e) { RailMap.LOG.warn("[IR Extras] CCTV: can't format {}: {}", f, e.toString()); }
    }

    public static int slots(World w, int cam) { Idx x = index(w, cam); return x == null ? 0 : x.slots; }

    /** frames stored */
    public static int used(World w, int cam) {
        Idx x = index(w, cam);
        if (x == null) return 0;
        int n = 0;
        for (long t : x.times) if (t > 0) n++;
        return n;
    }

    /** would the next write overwrite footage */
    public static boolean full(World w, int cam) { Idx x = index(w, cam); return x != null && x.times[x.head] > 0; }

    /** append a frame (ring: overwrites the oldest); false if there is no ring or the frame size changed */
    public static boolean write(World w, int cam, Snap.Frame f) {
        Idx x = index(w, cam);
        if (x == null || x.w != f.w || x.h != f.h) return false;
        try (RandomAccessFile r = new RandomAccessFile(file(w, cam), "rw")) {
            r.seek(HEAD + (long) x.head * rec(x));
            byte[] b = new byte[8 + 2 * f.px.length];
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(b);
            bb.putLong(f.t);
            for (short s : f.px) bb.putShort(s);
            r.write(b);
            x.times[x.head] = f.t;
            x.head = (x.head + 1) % x.slots;
            return true;
        } catch (IOException e) { return false; }
    }

    /** {oldest, newest} recorded time, or null */
    public static long[] range(World w, int cam) {
        Idx x = index(w, cam);
        if (x == null) return null;
        long lo = Long.MAX_VALUE, hi = -1;
        for (long t : x.times) if (t > 0) { lo = Math.min(lo, t); hi = Math.max(hi, t); }
        return hi < 0 ? null : new long[]{lo, hi};
    }

    /** the recorded frame at or just before time t (or the oldest one after it) */
    public static Snap.Frame read(World w, int cam, long t) {
        Idx x = index(w, cam);
        if (x == null) return null;
        int best = -1, after = -1;
        for (int i = 0; i < x.slots; i++) {
            long s = x.times[i];
            if (s <= 0) continue;
            if (s <= t && (best < 0 || s > x.times[best])) best = i;
            if (s > t && (after < 0 || s < x.times[after])) after = i;
        }
        if (best < 0) best = after;
        if (best < 0) return null;
        try (RandomAccessFile r = new RandomAccessFile(file(w, cam), "r")) {
            r.seek(HEAD + (long) best * rec(x));
            byte[] b = new byte[(int) rec(x)];
            r.readFully(b);
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(b);
            Snap.Frame f = new Snap.Frame();
            f.t = bb.getLong(); f.w = x.w; f.h = x.h; f.px = new short[x.w * x.h];
            for (int i = 0; i < f.px.length; i++) f.px[i] = bb.getShort();
            return f;
        } catch (IOException e) { return null; }
    }

    /** drop cached indexes (world unload) */
    public static void forget() { CACHE.clear(); }
}
