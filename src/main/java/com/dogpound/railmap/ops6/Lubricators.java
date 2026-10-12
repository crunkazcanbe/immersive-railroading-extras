package com.dogpound.railmap.ops6;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where the grease is (batch 6 E #23): every working Flange Lubricator posts its grease film (0 dry .. 1 full film,
 * more = over-greased) here once a second. The track wear model (TrackCondition), the wheel wear model (Wear) and the
 * client's curve squeal (TrainSounds) ask it: a greased gauge face wears up to 70 % less on curves, greased flanges
 * 50 % less, and a film over 30 % stops the squeal.
 */
public final class Lubricators {
    private Lubricators() {}

    /** dim -> lubricator pos -> film (server and client keep their own) */
    private static final Map<Integer, Map<Long, Float>> FILM = new ConcurrentHashMap<>();

    static void post(World w, BlockPos p, float film) { FILM.computeIfAbsent(w.provider.getDimension() * 2 + (w.isRemote ? 1 : 0), k -> new ConcurrentHashMap<>()).put(p.toLong(), film); }
    static void gone(World w, BlockPos p) { Map<Long, Float> m = FILM.get(w.provider.getDimension() * 2 + (w.isRemote ? 1 : 0)); if (m != null) m.remove(p.toLong()); }

    /** the best grease film reaching (x, y, z): the nearest lubricators within lubeRange carry it along the rails */
    public static float film(World w, double x, double y, double z) {
        if (!Ops6Config.lubeEffect) return 0;
        Map<Long, Float> m = FILM.get(w.provider.getDimension() * 2 + (w.isRemote ? 1 : 0));
        if (m == null || m.isEmpty()) return 0;
        float best = 0;
        double r = Ops6Config.lubeRange;
        for (Map.Entry<Long, Float> e : m.entrySet()) {
            BlockPos p = BlockPos.fromLong(e.getKey());
            double dx = p.getX() + 0.5 - x, dz = p.getZ() + 0.5 - z, dy = p.getY() - y;
            if (Math.abs(dy) > 5 || dx * dx + dz * dz > r * r) continue;
            float f = e.getValue() * (float) (1 - 0.4 * Math.sqrt(dx * dx + dz * dz) / r);   // thinner the further it is carried
            best = Math.max(best, f);
        }
        return best;
    }

    /** rail side-wear factor for a piece of track (curve = 1 + 400 / radius; straight track = 1 = no gauge-face contact) */
    public static double rail(World w, BlockPos key, double curve) {
        if (curve <= 1.0001) return 1;
        return 1 - 0.7 * Math.min(1, film(w, key.getX() + 0.5, key.getY(), key.getZ() + 0.5));
    }

    /** wheel flange wear factor for a car at this position */
    public static double wheel(World w, double x, double y, double z) { return 1 - 0.5 * Math.min(1, film(w, x, y, z)); }

    // ---- client: curve squeal ----------------------------------------------------------------------------------------
    private static long clientScan;

    /** client: is the curve here greased enough to stop the squeal? (refreshes its lubricator list once a second) */
    public static boolean quiet(World w, double x, double y, double z) {
        long now = System.currentTimeMillis();
        if (now - clientScan > 1000) {
            clientScan = now;
            Map<Long, Float> fresh = new HashMap<>();
            for (TileEntity t : w.loadedTileEntityList) if (t instanceof TileFlangeLube l && !t.isInvalid()) fresh.put(t.getPos().toLong(), l.controls().get("filmg") / 100f);
            Map<Long, Float> m = FILM.computeIfAbsent(w.provider.getDimension() * 2 + 1, k -> new ConcurrentHashMap<>());
            m.clear();
            m.putAll(fresh);
        }
        return film(w, x, y, z) >= 0.3f;
    }
}
