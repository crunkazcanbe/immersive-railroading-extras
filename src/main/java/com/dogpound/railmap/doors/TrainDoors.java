package com.dogpound.railmap.doors;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import com.dogpound.railmap.ModSounds;
import com.dogpound.railmap.RailMap;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Moves a car's sliding doors smoothly (Pride Rail cars name their door control groups DR1..DR9 for the right-hand
 * side and DL1..DL9 for the left), with the real sounds: air valve + slide + end stop on opening, slide + seal
 * thump + lock click on closing. Anything can ask: station door controllers, the Control Center, redstone.
 */
public final class TrainDoors {
    public static final String RIGHT = "DR", LEFT = "DL";
    private static final boolean IR = Loader.isModLoaded("immersiverailroading");
    private static final Map<String, Motion> MOVING = new HashMap<>();        // uuid|side -> motion

    private static final class Motion {
        final UUID car; final String side; final int dim;
        float pos, target, speed;
        Motion(UUID car, String side, int dim) { this.car = car; this.side = side; this.dim = dim; }
    }

    public TrainDoors() {}

    /** start opening (target 1) or closing (target 0) one side of a car; seconds = how long the stroke takes */
    public static void move(World world, EntityRollingStock car, String side, boolean open, float seconds) {
        if (!IR || world.isRemote || car == null) return;
        String key = car.getUUID() + "|" + side;
        Motion m = MOVING.get(key);
        float now = current(car, side);
        if (m == null) {
            m = new Motion(car.getUUID(), side, world.provider.getDimension());
            m.pos = now;
            MOVING.put(key, m);
        }
        float target = open ? 1f : 0f;
        if (m.target == target && Math.abs(now - target) < 0.01f) return;
        m.target = target;
        m.speed = 1f / Math.max(5, seconds * 20f);
        cam72cam.mod.math.Vec3d p = car.getPosition();
        float vol = side.startsWith("C") ? 0.45f : 1f, pitch = side.startsWith("C") ? 1.25f : 1f;
        world.playSound(null, p.x, p.y + 1.5, p.z, side.startsWith("C") ? ModSounds.PSD_MOVE : open ? ModSounds.DOOR_OPEN : ModSounds.DOOR_CLOSE,
                SoundCategory.NEUTRAL, vol, pitch);
    }

    /** how far open a side is (0..1), read from the first door group on that side */
    public static float current(EntityRollingStock car, String side) {
        try {
            return car.getControlPosition(side + "1");
        } catch (RuntimeException e) {
            return 0f;
        }
    }

    public static boolean anyOpen(EntityRollingStock car) {
        return current(car, RIGHT) > 0.02f || current(car, LEFT) > 0.02f;
    }

    public static void sound(World world, EntityRollingStock car, SoundEvent s, float vol) {
        cam72cam.mod.math.Vec3d p = car.getPosition();
        world.playSound(null, p.x, p.y + 1.5, p.z, s, SoundCategory.NEUTRAL, vol, 1f);
    }

    private static void set(EntityRollingStock car, String side, float v) {
        if (side.startsWith("C")) { car.setControlPosition(side, v); return; }      // gangway door: one group per end
        for (int i = 1; i <= 9; i++) car.setControlPosition(side + i, v);
    }

    // ---- gangway doors open as you walk up to the end of a car (and the matching door of the next car) --------
    private static final Map<String, Long> GANGWAY_UNTIL = new HashMap<>();   // uuid|end -> close after this tick
    private static final Map<String, Boolean> GANGWAY_OPEN = new HashMap<>();

    private static void gangways(World w) {
        long now = w.getTotalWorldTime();
        cam72cam.mod.world.World umc = cam72cam.mod.world.World.get(w);
        List<EntityRollingStock> all = null;
        for (net.minecraft.entity.player.EntityPlayer pl : w.playerEntities) {
            if (all == null) all = umc.getEntities(EntityRollingStock.class);
            for (EntityRollingStock s : all) {
                if (s.isDead() || s.getDefinitionID() == null || !s.getDefinitionID().contains("/pride_")) continue;
                cam72cam.mod.math.Vec3d p = s.getPosition();
                double dx = pl.posX - p.x, dz = pl.posZ - p.z, dy = pl.posY - p.y;
                if (dx * dx + dz * dz > 40 * 40 || dy < -1 || dy > 5) continue;
                double yaw = Math.toRadians(s.getRotationYaw());
                double mx = Math.sin(yaw), mz = -Math.cos(yaw);                    // model +x in the world
                double lx = dx * mx + dz * mz, lz = -dx * mz + dz * mx;
                double half = s.getDefinition().getLength(s.gauge) / 2.0;
                if (Math.abs(lz) > 0.9 || Math.abs(lx) > half + 1.0) continue;     // must be in the aisle, inside / at the car
                String end = lx > half - 2.2 ? "CF" : lx < -half + 2.2 ? "CR" : null;
                if (end == null) continue;
                hold(w, s, end, now);
                // the next car's door at the same gap
                double ex = p.x + mx * Math.signum(lx) * (half + 0.4), ez = p.z + mz * Math.signum(lx) * (half + 0.4);
                for (EntityRollingStock o : all) {
                    if (o == s || o.isDead() || o.getDefinitionID() == null || !o.getDefinitionID().contains("/pride_")) continue;
                    cam72cam.mod.math.Vec3d q = o.getPosition();
                    double oy = Math.toRadians(o.getRotationYaw());
                    double omx = Math.sin(oy), omz = -Math.cos(oy);
                    double olx = (ex - q.x) * omx + (ez - q.z) * omz, olz = -(ex - q.x) * omz + (ez - q.z) * omx;
                    double oh = o.getDefinition().getLength(o.gauge) / 2.0;
                    if (Math.abs(olz) < 1.0 && Math.abs(Math.abs(olx) - oh) < 1.2) hold(w, o, olx > 0 ? "CF" : "CR", now);
                }
            }
        }
        for (Iterator<Map.Entry<String, Long>> it = GANGWAY_UNTIL.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Long> e = it.next();
            if (now < e.getValue()) continue;
            String[] k = e.getKey().split("\\|");
            EntityRollingStock car = umc.getEntity(java.util.UUID.fromString(k[0]), EntityRollingStock.class);
            if (car != null) move(w, car, k[1], false, 1.2f);
            GANGWAY_OPEN.remove(e.getKey());
            it.remove();
        }
    }

    private static void hold(World w, EntityRollingStock car, String end, long now) {
        String key = car.getUUID() + "|" + end;
        GANGWAY_UNTIL.put(key, now + 50);
        if (GANGWAY_OPEN.put(key, true) == null) move(w, car, end, true, 1.0f);
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote || !IR) return;
        if (e.world.getTotalWorldTime() % 4 == 0 && !e.world.playerEntities.isEmpty()) {
            try { gangways(e.world); } catch (RuntimeException ex) { RailMap.LOG.debug("[IR Extras] gangway: {}", ex.toString()); }
        }
        if (MOVING.isEmpty()) return;
        int dim = e.world.provider.getDimension();
        cam72cam.mod.world.World umc = null;
        for (Iterator<Motion> it = MOVING.values().iterator(); it.hasNext(); ) {
            Motion m = it.next();
            if (m.dim != dim) continue;
            if (umc == null) umc = cam72cam.mod.world.World.get(e.world);
            EntityRollingStock car;
            try {
                car = umc.getEntity(m.car, EntityRollingStock.class);
            } catch (RuntimeException ex) {
                car = null;
            }
            if (car == null || car.isDead()) { it.remove(); continue; }
            // ease in/out: slow start, slow finish, like a real door drive
            float d = m.target - m.pos;
            float ease = 0.35f + 0.65f * (float) Math.sin(Math.PI * Math.min(1f, Math.max(0f, m.target > 0.5f ? m.pos : 1f - m.pos)));
            float step = Math.min(Math.abs(d), m.speed * ease * 1.6f);
            m.pos += Math.signum(d) * step;
            set(car, m.side, m.pos);
            if (Math.abs(m.target - m.pos) < 1e-3f) {
                set(car, m.side, m.target);
                it.remove();
            }
        }
    }

    static {
        RailMap.LOG.debug("[IR Extras] train doors ready");
    }
}
