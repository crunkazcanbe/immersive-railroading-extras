package com.dogpound.railmap.newrail6;

import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.alarm.AlarmManager;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Rack (cog) railway, batch 6 #16. Rack Rail sections are laid in the ground under the track (the toothed Abt bars show
 * between the rails); an Entry section is the sprung, tapered first length where the pinion meshes. A locomotive with a
 * rack pinion (tagged at a Rack Panel, or one IR's pack already marks "cog") standing on rack gets full grip through the
 * fork's prideRailAdhesion hook (1.5 = the most the fork takes): its wheels can't slip or slide, so it climbs and holds
 * grades no adhesion loco could - the motor's power is still the limit. Running on to the rack anywhere but an Entry
 * section, or through one too fast, strikes the tooth tips (pinion wear + alarm); the pinion wears with distance (much
 * less with the panel's lubricator running) and a worn-out pinion no longer holds. The nearest Rack Panel supervises:
 * rack speed limit (overspeed = alarm, or brakes on), entry speed, non-rack locos on rack, its E-stop holds every train on
 * the rack. Checked twice a second.
 */
public final class Rack {
    private Rack() {}

    /** per dimension: stock UUID -> what it stood on at the last check: 0 none, 1 rack, 2 entry section */
    private static final Map<Integer, Map<UUID, Integer>> ON = new HashMap<>();

    static Map<UUID, Integer> on(World w) { return ON.computeIfAbsent(w.provider.getDimension(), k -> new HashMap<>()); }

    /** AdhesionSim hook: the rail adhesion factor for this car (rack grip for an engaged cog locomotive) */
    public static float adhesion(World w, EntityMoveableRollingStock s, float adh) {
        if (!NR6Config.rackGrip || !(s instanceof Locomotive l)) return adh;
        Integer k = on(w).get(s.getUUID());
        if (k == null || k == 0) return adh;
        return engaged(w, l) ? 1.5f : adh;
    }

    public static boolean cog(World w, Locomotive l) {
        if (NR6Data.get(w).cog.contains(l.getUUID())) return true;
        try { return l.getDefinition().isCog(); } catch (RuntimeException e) { return false; }
    }

    /** a cog loco whose pinion still holds */
    public static boolean engaged(World w, Locomotive l) {
        return cog(w, l) && (!NR6Config.pinionWear || NR6Data.get(w).wear(l.getUUID()) < 100f);
    }

    /** what the stock at p stands on: 0 none, 1 rack, 2 entry section (the rack is laid in the ground, 1-3 blocks under the rail) */
    public static int under(World w, Vec3d p) {
        BlockPos b = new BlockPos(Math.floor(p.x), Math.floor(p.y), Math.floor(p.z));
        for (int dy = 0; dy <= 3; dy++) {
            BlockPos q = b.down(dy);
            if (!w.isBlockLoaded(q)) return 0;
            Block k = w.getBlockState(q).getBlock();
            if (k == NR6Registry.rackRail) return 1;
            if (k == NR6Registry.rackEntry) return 2;
        }
        return 0;
    }

    /** grade in % from the stock's pitch */
    public static double grade(float pitchDeg) { return Math.abs(Math.tan(Math.toRadians(pitchDeg)) * 100); }

    /** pinion wear for distance run on rack: % per km (0.12 lubricated, 0.5 dry) */
    public static float wearFor(double km, boolean lubed) { return (float) (km * (lubed ? 0.12 : 0.5)); }

    /** entry verdict: 0 clean, 1 hard (too fast through the entry), 2 no entry section */
    public static int entry(int kind, double kmh, double limit) {
        if (kind == 1) return kmh > 3 ? 2 : 0;
        return kmh > limit ? 1 : 0;
    }

    static void tick(World mc, long now) {
        if (now % 10 != 5) return;
        List<TileRackPanel> panels = NR6Tile.live(mc, TileRackPanel.class);
        for (TileRackPanel p : panels) p.scanStart();
        cam72cam.mod.world.World w = cam72cam.mod.world.World.get(mc);
        if (w == null) return;
        NR6Data d = NR6Data.get(mc);
        Map<UUID, Integer> ON = on(mc);
        Set<UUID> here = new HashSet<>();
        for (Locomotive l : w.getEntities(Locomotive.class)) {
            if (l.isDead()) continue;
            Vec3d p = l.getPosition();
            int k = under(mc, p);
            UUID u = l.getUUID();
            int prev = ON.getOrDefault(u, 0);
            if (k == 0) {
                if (prev != 0) { ON.remove(u); if (l.prideRailAdhesion > 1.01f) l.prideRailAdhesion = 1f; }
                continue;
            }
            here.add(u);
            ON.put(u, k);
            BlockPos at = new BlockPos(p.x, p.y, p.z);
            TileRackPanel panel = nearest(panels, at);
            boolean cog = cog(mc, l);
            double kmh = Math.abs(l.getRealSpeed().metric());
            double grade = grade(l.getRotationPitch());
            String name = name(l);
            float wear = d.wear(u);
            if (cog && NR6Config.rackGrip && wear < 100f && l.prideRailAdhesion < 1.49f) l.prideRailAdhesion = 1.5f;   // AdhesionSim keeps it there while it runs
            if (prev == 0 && cog) {
                int e = entry(k, kmh, panel == null ? 10 : panel.entryLimit());
                if (e != 0 && NR6Config.pinionWear) { wear = Math.min(100f, wear + (e == 1 ? 5f : 2f)); d.wear.put(u, wear); d.markDirty(); }
                if (panel != null) panel.entered(l, name, e, kmh);
            }
            if (cog && NR6Config.pinionWear && kmh > 0.5) {
                boolean lubed = panel != null && panel.lubricating();
                float before = wear;
                wear = Math.min(100f, wear + wearFor(kmh * 0.5 / 3600.0, lubed));
                d.wear.put(u, wear);
                if ((int) before != (int) wear) d.markDirty();
                if (before < 100f && wear >= 100f)
                    AlarmManager.get(mc).raise(mc, "rack:pinion:" + u, AlarmManager.Cat.MACHINE, AlarmManager.Sev.MAJOR,
                            "RACK PINION WORN OUT on " + name + " - it no longer holds on the rack (renew at a Rack Panel)", at, true);
            }
            if (panel != null) panel.report(l, name, cog, kmh, grade, wear);
        }
        ON.keySet().retainAll(here);
        for (TileRackPanel p : panels) p.scanEnd();
    }

    static TileRackPanel nearest(List<TileRackPanel> ps, BlockPos at) {
        TileRackPanel best = null;
        double bd = 128 * 128;
        for (TileRackPanel p : ps) { double q = p.getPos().distanceSq(at); if (q < bd) { bd = q; best = p; } }
        return best;
    }

    static String name(Locomotive l) {
        String t = l.tag == null || l.tag.isEmpty() ? l.getDefinition().name() : l.tag;
        return t.length() > 14 ? t.substring(0, 14) : t;
    }

    /** the locomotive nearest a point within r blocks (or null) */
    static Locomotive nearestLoco(World mc, BlockPos at, double r) {
        cam72cam.mod.world.World w = cam72cam.mod.world.World.get(mc);
        Locomotive best = null;
        double bd = r * r;
        for (Locomotive l : w.getEntities(Locomotive.class)) {
            Vec3d p = l.getPosition();
            double q = at.distanceSqToCenter(p.x, p.y, p.z);
            if (q < bd) { bd = q; best = l; }
        }
        return best;
    }
}
