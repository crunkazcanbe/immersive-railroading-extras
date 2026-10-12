package com.dogpound.railmap.newrail6;

import cam72cam.immersiverailroading.entity.*;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.sound.PackSounds;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Train ferry, batch 6 #17: two Ferry Docks on the same route number pair up; the ferry lies stern-in at one berth and
 * sails the straight water route to the other. Loading takes the train cars standing still on the dock tracks off the
 * rails and stows them on the ferry's deck (definition, livery, name, which way round, freight cargo and tank contents):
 * IR stock can't be carried over water as entities, so it is stored safely and re-railed on the destination dock's
 * track, coupled up in the same order, when it is unloaded there. Nothing is ever lost: cars stay on the deck (saved
 * with the world) until a dock unloads them.
 */
public final class Ferry {
    private Ferry() {}

    public static final double ACCEL = 0.15, TURN = 25;
    /** route number -> the ferry entity shown for it */
    private static final Map<String, EntityTrainFerry> SHIPS = new HashMap<>();
    /** cargo / tank contents to put back into freshly re-railed cars once they have assembled: {uuid, nbt, due tick} */
    private static final List<Object[]> PENDING = new ArrayList<>();

    // ---- geometry (pure, self-tested) --------------------------------------------------------------------------------
    public static double length(NR6Data.Route r) {
        if (!r.berths()) return 0;
        return Math.hypot(r.berth[1][0] - r.berth[0][0], r.berth[1][2] - r.berth[0][2]);
    }

    /** MC yaw of a heading (dx, dz) */
    public static float yaw(double dx, double dz) { return (float) Math.toDegrees(Math.atan2(-dx, dz)); }

    static float lerpYaw(float a, float b, double t) {
        float d = ((b - a) % 360 + 540) % 360 - 180;
        return (float) (a + d * Math.max(0, Math.min(1, t)));
    }

    /** {x, y, z, yaw} of the ship on route r now */
    public static double[] pose(NR6Data.Route r) {
        if (!r.berths()) return null;
        int from = r.at, to = 1 - r.at;
        if (r.phase == NR6Data.BERTHED || r.phase == NR6Data.DEPARTING) return new double[]{r.berth[from][0], r.berth[from][1], r.berth[from][2], r.out[from]};
        if (r.phase == NR6Data.ARRIVING) return new double[]{r.berth[to][0], r.berth[to][1], r.berth[to][2], r.out[to]};
        double L = Math.max(1, length(r)), f = Math.max(0, Math.min(1, r.s / L));
        double dx = r.berth[to][0] - r.berth[from][0], dz = r.berth[to][2] - r.berth[from][2];
        float route = yaw(dx, dz);
        float y = r.s < TURN ? lerpYaw(r.out[from], route, r.s / TURN) : r.s > L - TURN ? lerpYaw(route, r.out[to], (r.s - (L - TURN)) / TURN) : route;
        return new double[]{r.berth[from][0] + dx * f, r.berth[from][1] + (r.berth[to][1] - r.berth[from][1]) * f, r.berth[from][2] + dz * f, y};
    }

    /** speed for the distance still to go: accelerate to cruise, brake to arrive */
    public static double speedFor(double v, double toGo) {
        double brake = Math.sqrt(2 * ACCEL * Math.max(0, toGo)) + 0.3;
        return Math.min(Math.min(NR6Config.ferrySpeed, v + ACCEL * 0.05), brake);
    }

    /** the first point along the route that is not open water (null = navigable) */
    static BlockPos blocked(World w, NR6Data.Route r) {
        if (!NR6Config.ferryNeedsWater || !r.berths()) return null;
        double L = length(r);
        double[] a = r.berth[0], b = r.berth[1];
        int y = (int) Math.floor(Math.min(a[1], b[1]) - 0.5);
        for (double s = 8; s < L - 8; s += 2) {
            double f = s / L;
            BlockPos q = new BlockPos(a[0] + (b[0] - a[0]) * f, y, a[2] + (b[2] - a[2]) * f);
            if (!w.isBlockLoaded(q)) continue;
            if (w.getBlockState(q).getMaterial() != Material.WATER || w.getBlockState(q.up()).getMaterial().isSolid()) return q;
        }
        return null;
    }

    // ---- running ---------------------------------------------------------------------------------------------------
    static void tick(World w, long now) {
        NR6Data d = NR6Data.get(w);
        if (d.routes.isEmpty()) return;
        for (NR6Data.Route r : d.routes.values()) {
            try { run(w, d, r, now); } catch (RuntimeException ex) { RailMap.LOG.warn("[IR Extras] ferry route {} failed: {}", r.ch, ex.toString()); }
            if (now % 20 == 3) ship(w, r);
        }
        if (!PENDING.isEmpty()) applyPending(w, now);
        if (now % 200 == 0) d.markDirty();
    }

    static void run(World w, NR6Data d, NR6Data.Route r, long now) {
        if (!r.paired() || !r.berths()) return;
        switch (r.phase) {
            case NR6Data.DEPARTING -> {
                if (now - r.phaseAt >= 200) { r.phase = NR6Data.SAILING; r.s = 0; r.v = 0; r.phaseAt = now; d.changed(); }
            }
            case NR6Data.SAILING -> {
                double L = length(r);
                r.v = speedFor(r.v, L - r.s);
                r.s = Math.min(L, r.s + r.v * 0.05);
                if (r.s >= L - 0.01) {
                    r.phase = NR6Data.ARRIVING; r.phaseAt = now; r.v = 0; d.changed();
                    double[] b = r.berth[1 - r.at];
                    PackSounds.play(w, b[0], b[1], b[2], "station.ship_horn", 4f, 0.8f);
                }
            }
            case NR6Data.ARRIVING -> {
                if (now - r.phaseAt >= 200) {
                    r.at = 1 - r.at; r.phase = NR6Data.BERTHED; r.phaseAt = now; r.voyages++; r.s = 0; d.changed();
                    Recorder.event(w, "Train ferry route " + r.ch + " berthed at dock " + (r.at == 0 ? "A" : "B") + " with " + r.deck.size() + " cars");
                }
            }
            default -> { }
        }
    }

    /** keeps the ferry entity spawned while someone is near it */
    static void ship(World w, NR6Data.Route r) {
        String key = w.provider.getDimension() + ":" + r.ch;
        EntityTrainFerry e = SHIPS.get(key);
        double[] p = r.paired() ? pose(r) : null;
        if (e != null && (e.isDead || e.world != w)) { SHIPS.remove(key); e = null; }
        boolean near = false;
        if (p != null) for (EntityPlayer pl : w.playerEntities) if (pl.getDistanceSq(p[0], p[1], p[2]) < (double) NR6Config.spawnRange * NR6Config.spawnRange) { near = true; break; }
        if (p == null || !near) {
            if (e != null && e.getPassengers().isEmpty()) { e.setDead(); SHIPS.remove(key); }
            return;
        }
        if (e == null) {
            if (!w.isBlockLoaded(new BlockPos(p[0], p[1], p[2]))) return;
            e = new EntityTrainFerry(w, r.ch);
            e.setLocationAndAngles(p[0], p[1], p[2], (float) p[3], 0);
            w.spawnEntity(e);
            SHIPS.put(key, e);
        }
    }

    // ---- stowing cars ----------------------------------------------------------------------------------------------
    /** the IR track piece nearest a point, within r blocks of `around` (null = none) */
    static BlockPos track(World mc, BlockPos around, double[] near, int r) {
        cam72cam.mod.world.World w = cam72cam.mod.world.World.get(mc);
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++)
                for (int dy = -3; dy <= 2; dy++) {
                    BlockPos q = around.add(dx, dy, dz);
                    if (!mc.isBlockLoaded(q)) continue;
                    if (w.getBlockEntity(new Vec3i(q.getX(), q.getY(), q.getZ()), TileRailBase.class) == null) continue;
                    double dd = near == null ? q.distanceSq(around) : q.distanceSqToCenter(near[0], q.getY(), near[2]);
                    if (dd < bd) { bd = dd; best = q; }
                }
        return best;
    }

    /** stock standing within r blocks of p, nearest first */
    static List<EntityRollingStock> standing(World mc, BlockPos p, double r, boolean wagonsOnly) {
        cam72cam.mod.world.World w = cam72cam.mod.world.World.get(mc);
        List<EntityRollingStock> out = new ArrayList<>();
        for (EntityRollingStock s : w.getEntities(EntityRollingStock.class)) {
            if (s.isDead() || wagonsOnly && s instanceof Locomotive) continue;
            Vec3d q = s.getPosition();
            if (Math.abs(q.y - p.getY()) > 6 || p.distanceSqToCenter(q.x, p.getY() + 0.5, q.z) > r * r) continue;
            out.add(s);
        }
        out.sort(Comparator.comparingDouble(s -> p.distanceSqToCenter(s.getPosition().x, p.getY() + 0.5, s.getPosition().z)));
        return out;
    }

    static double kmh(EntityRollingStock s) { return s instanceof EntityMoveableRollingStock m ? Math.abs(m.getCurrentSpeed().metric()) : 0; }

    /** one car as stowed on the deck */
    public static NBTTagCompound pack(EntityRollingStock s, float refYaw) {
        NBTTagCompound t = new NBTTagCompound();
        t.setString("def", s.getDefinitionID());
        t.setString("tex", s.getTexture() == null ? "" : s.getTexture());
        t.setString("tag", s.tag == null ? "" : s.tag);
        t.setString("name", label(s));
        t.setBoolean("flip", Math.abs(cam72cam.mod.util.DegreeFuncs.delta(s.getRotationYaw(), refYaw)) > 90);
        try {
            if (s instanceof Freight f && f.cargoItems != null) t.setTag("cargo", f.cargoItems.save().internal);
            if (s instanceof FreightTank ft && ft.theTank != null) t.setTag("tank", ft.theTank.internal.writeToNBT(new NBTTagCompound()));
        } catch (RuntimeException | LinkageError ignored) { }
        return t;
    }

    static String label(EntityRollingStock s) {
        String n = s.tag != null && !s.tag.isEmpty() ? s.tag : s.getDefinition().name();
        return n.length() > 18 ? n.substring(0, 18) : n;
    }

    /** load: stow the standing cars by the dock onto the ferry deck; returns a message */
    static String load(World mc, TileFerryDock dock, NR6Data.Route r, boolean wagonsOnly) {
        int room = NR6Config.ferryCapacity - r.deck.size();
        if (room <= 0) return "§cThe deck is full (" + r.deck.size() + " cars)";
        double[] water = r.berth[dock.end(r)];
        List<EntityRollingStock> cars = standing(mc, dock.getPos(), 24, wagonsOnly);
        if (cars.isEmpty()) return "§7No train cars on the dock tracks (within 24 blocks)";
        for (EntityRollingStock s : cars) {
            if (kmh(s) > 0.5) return "§c" + label(s) + " is still moving";
            if (s.getPassengerCount() > 0) return "§c" + label(s) + " has people aboard - they get off first";
        }
        float ref = cars.get(0).getRotationYaw();
        if (water != null) cars.sort(Comparator.comparingDouble(s -> Math.hypot(s.getPosition().x - water[0], s.getPosition().z - water[2])));
        int n = 0;
        for (EntityRollingStock s : cars) {
            if (n >= room) break;
            r.deck.add(pack(s, ref));
            s.kill();
            n++;
        }
        NR6Data.get(mc).changed();
        return "§a" + n + " car" + (n == 1 ? "" : "s") + " chained down on the deck (" + r.deck.size() + "/" + NR6Config.ferryCapacity + ")" + (cars.size() > n ? " - deck full, " + (cars.size() - n) + " left ashore" : "");
    }

    /** unload: re-rail the deck's cars on this dock's track, coupled in order, nearest the water first */
    static String unload(World mc, TileFerryDock dock, NR6Data.Route r) {
        if (r.deck.isEmpty()) return "§7The deck is empty";
        double[] water = r.berth[dock.end(r)];
        BlockPos t = track(mc, dock.getPos(), water, 8);
        if (t == null) return "§cNo track on the dock within 8 blocks to unload onto";
        float look = yaw(dock.getPos().getX() + 0.5 - water[0], dock.getPos().getZ() + 0.5 - water[2]);
        List<String> ids = new ArrayList<>();
        List<Boolean> flips = new ArrayList<>();
        List<String> tex = new ArrayList<>();
        for (NBTTagCompound c : r.deck) { ids.add(c.getString("def")); flips.add(c.getBoolean("flip")); tex.add(c.getString("tex")); }
        Set<UUID> before = new HashSet<>();
        for (EntityRollingStock s : standing(mc, dock.getPos(), 96, false)) before.add(s.getUUID());
        int n;
        try { n = com.dogpound.railmap.server.ConsistPlacer.placeFormation(cam72cam.mod.world.World.get(mc), t, look, ids, flips, tex); }
        catch (RuntimeException ex) { RailMap.LOG.warn("[IR Extras] ferry unload failed: {}", ex.toString()); return "§cUnloading failed: " + ex; }
        if (n == 0) return "§cThe cars would not go onto the track at " + t.getX() + "," + t.getZ() + " - is it clear and long enough?";
        List<EntityRollingStock> fresh = new ArrayList<>();
        for (EntityRollingStock s : standing(mc, dock.getPos(), 96, false)) if (!before.contains(s.getUUID())) fresh.add(s);
        fresh.sort(Comparator.comparingDouble(s -> Math.hypot(s.getPosition().x - t.getX(), s.getPosition().z - t.getZ())));
        long due = mc.getTotalWorldTime() + 20;
        for (int i = 0; i < Math.min(n, fresh.size()); i++) {
            NBTTagCompound c = r.deck.get(i);
            EntityRollingStock s = fresh.get(i);
            if (!c.getString("tag").isEmpty()) s.tag = c.getString("tag");
            if (c.hasKey("cargo") || c.hasKey("tank")) PENDING.add(new Object[]{s.getUUID(), c, due});
        }
        r.deck.subList(0, n).clear();
        NR6Data.get(mc).changed();
        return "§a" + n + " car" + (n == 1 ? "" : "s") + " re-railed on the dock" + (r.deck.isEmpty() ? "" : " - " + r.deck.size() + " would not fit, still on the deck");
    }

    static void applyPending(World mc, long now) {
        cam72cam.mod.world.World w = cam72cam.mod.world.World.get(mc);
        Iterator<Object[]> it = PENDING.iterator();
        while (it.hasNext()) {
            Object[] p = it.next();
            if (now < (Long) p[2]) continue;
            it.remove();
            UUID u = (UUID) p[0];
            NBTTagCompound c = (NBTTagCompound) p[1];
            for (EntityRollingStock s : w.getEntities(EntityRollingStock.class)) {
                if (!s.getUUID().equals(u)) continue;
                try {
                    if (c.hasKey("cargo") && s instanceof Freight f && f.cargoItems != null) f.cargoItems.load(new cam72cam.mod.serialization.TagCompound(c.getCompoundTag("cargo")));
                    if (c.hasKey("tank") && s instanceof FreightTank ft && ft.theTank != null) ft.theTank.internal.readFromNBT(c.getCompoundTag("tank"));
                } catch (RuntimeException | LinkageError ex) { RailMap.LOG.warn("[IR Extras] ferry cargo restore failed for {}: {}", u, ex.toString()); }
            }
        }
    }
}
