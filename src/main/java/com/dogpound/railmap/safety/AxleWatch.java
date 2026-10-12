package com.dogpound.railmap.safety;

import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.library.Gauge;
import cam72cam.immersiverailroading.library.ModelComponentType;
import cam72cam.immersiverailroading.model.components.ModelComponent;
import cam72cam.immersiverailroading.registry.EntityRollingStockDefinition;
import cam72cam.immersiverailroading.util.VecUtil;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.RailMap;
import net.minecraft.world.World;

import java.util.*;

/**
 * Where every wheelset of every piece of rolling stock is (her ChatGPT list #19, axle counters). An axle counter head
 * is a pair of magnetic sensors clamped to the rail: it sees each wheel flange go past and which way. To make that real
 * we need real axles, so the wheelsets come from the stock's own model (its driver / frame / bogie wheel parts, at the
 * gauge's scale), placed along the car at its yaw. A sampled snapshot every 2 ticks, only while an axle counter head is
 * loaded; heads compare each axle with its previous position and count the ones that cross them.
 */
public final class AxleWatch {
    private AxleWatch() {}

    /** one wheelset: stock uuid + index, world position */
    public static final class Axle {
        public final UUID car;
        public final int idx;
        public final double x, y, z;
        Axle(UUID car, int idx, double x, double y, double z) { this.car = car; this.idx = idx; this.x = x; this.y = y; this.z = z; }
        public long key() { return car.getMostSignificantBits() * 31 + car.getLeastSignificantBits() + idx * 0x9E3779B97F4A7C15L; }
    }

    private static final Map<Integer, List<Axle>> NOW = new HashMap<>();
    private static final Map<Integer, Long> AT = new HashMap<>();
    private static final Map<String, double[]> OFFSETS = new HashMap<>();
    private static final ModelComponentType[] WHEELS = {ModelComponentType.WHEEL_DRIVER_X, ModelComponentType.WHEEL_DRIVER_POS_X,
            ModelComponentType.FRAME_WHEEL_X, ModelComponentType.BOGEY_FRONT_WHEEL_X, ModelComponentType.BOGEY_REAR_WHEEL_X, ModelComponentType.BOGEY_POS_WHEEL_X};

    public static void clear() { NOW.clear(); AT.clear(); }

    /** the axles at the last snapshot (taken at most every 2 ticks, on demand) */
    public static List<Axle> axles(World w) {
        int dim = w.provider.getDimension();
        long now = w.getTotalWorldTime();
        Long at = AT.get(dim);
        if (at == null || now - at >= 2) {
            AT.put(dim, now);
            try { NOW.put(dim, snapshot(w)); }
            catch (LinkageError | RuntimeException ex) { RailMap.LOG.debug("[irextras] axle snapshot failed: {}", ex.toString()); NOW.put(dim, Collections.emptyList()); }
        }
        return NOW.getOrDefault(dim, Collections.emptyList());
    }

    private static List<Axle> snapshot(World mc) {
        List<Axle> out = new ArrayList<>();
        cam72cam.mod.world.World w = cam72cam.mod.world.World.get(mc);
        for (EntityRollingStock s : w.getEntities(EntityRollingStock.class)) {
            if (s.isDead() || !(s instanceof EntityMoveableRollingStock)) continue;
            Vec3d p = s.getPosition();
            float yaw = s.getRotationYaw();
            double[] off = offsets(s);
            for (int i = 0; i < off.length; i++) {
                Vec3d o = VecUtil.fromWrongYaw(off[i], yaw);
                out.add(new Axle(s.getUUID(), i, p.x + o.x, p.y, p.z + o.z));
            }
        }
        return out;
    }

    /** how many wheelsets this car runs on */
    public static int count(EntityRollingStock s) { return offsets(s).length; }

    /** each wheelset's distance from the car's centre along its length (m), from the model's wheel parts */
    static double[] offsets(EntityRollingStock s) {
        EntityRollingStockDefinition def = s.getDefinition();
        Gauge g = s.gauge;
        String key = s.getDefinitionID() + "@" + (g == null ? 0 : g.value());
        double[] c = OFFSETS.get(key);
        if (c != null) return c;
        double sc = g == null ? 1 : g.scale();
        TreeSet<Long> xs = new TreeSet<>();
        if (def != null) {
            for (ModelComponentType t : WHEELS) {
                try {
                    List<ModelComponent> l = def.getComponents(t);
                    if (l != null) for (ModelComponent m : l) if (m != null && m.center != null) xs.add(Math.round(m.center.x * sc * 5));   // 0.2 m buckets: L + R wheel = one axle
                } catch (RuntimeException ignored) { }
            }
        }
        if (xs.isEmpty() && def != null && g != null) {                                    // no wheel parts named: a wheelset either side of each bogie pivot
            double f = def.getBogeyFront(g), r = def.getBogeyRear(g), half = 0.9 * sc;
            if (Math.abs(f - r) > 1) { xs.add(Math.round((f - half) * 5)); xs.add(Math.round((f + half) * 5)); xs.add(Math.round((r - half) * 5)); xs.add(Math.round((r + half) * 5)); }
            else { double l = def.getLength(g) * 0.3; xs.add(Math.round(-l * 5)); xs.add(Math.round(l * 5)); }
        }
        if (xs.isEmpty()) xs.add(0L);
        c = new double[xs.size()];
        int i = 0;
        for (long x : xs) c[i++] = x / 5.0;
        OFFSETS.put(key, c);
        return c;
    }
}
