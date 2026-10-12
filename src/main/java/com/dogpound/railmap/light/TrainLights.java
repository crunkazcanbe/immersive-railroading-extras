package com.dogpound.railmap.light;

import cam72cam.immersiverailroading.entity.CarPassenger;
import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.util.VecUtil;
import com.dogpound.railmap.RailMap;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lit trains (client): every tick, the light the trains near the player cast -
 * <ul>
 *   <li><b>headlights</b>: spots of light along each lit headlight's beam on the track ahead (bright 15..12, dimmed
 *   9..5). Where each headlight is and whether it is lit comes from the IR fork (model.part.PrideBeams, fed by IR's own
 *   LightFlare logic: control groups, direction, power, and the cab switch through prideLights OFF / DIM / BRIGHT);
 *   on an older fork it falls back to the leading end of a powered locomotive;</li>
 *   <li><b>windows</b>: a passenger car with power, or running, lights the platform round it (carLightLevel).</li>
 * </ul>
 * Only where it is dark (night, tunnels), nearest first, at most maxTrainLights moving lights; pooled invisible
 * EntityTrainLight points that the dynamic-lights mod lights.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID, value = Side.CLIENT)
public final class TrainLights {
    private TrainLights() {}

    private static final List<EntityTrainLight> POOL = new ArrayList<>();
    private static World poolWorld;
    private static final double[] SPOTS = {6, 13, 21, 30, 40, 52};
    public static int active;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.getRenderViewEntity() == null) { POOL.clear(); poolWorld = null; return; }
        if (poolWorld != mc.world) { POOL.clear(); poolWorld = mc.world; }
        if (!LightConfig.trainLights || LightConfig.maxTrainLights <= 0 || !DynLights.available()) { apply(mc.world, new ArrayList<>()); return; }
        List<double[]> want;
        try {
            want = gather(mc.world, mc.getRenderViewEntity());
        } catch (LinkageError | RuntimeException ex) {
            want = new ArrayList<>();
        }
        want.sort((a, b) -> Double.compare(a[4], b[4]));
        if (want.size() > LightConfig.maxTrainLights) want = new ArrayList<>(want.subList(0, LightConfig.maxTrainLights));
        apply(mc.world, want);
    }

    /** x, y, z, lum, distance^2 to the player */
    private static List<double[]> gather(World w, Entity me) {
        List<double[]> out = new ArrayList<>();
        double r2 = (double) LightConfig.trainLightRange * LightConfig.trainLightRange;
        boolean day = w.getSunBrightness(1f) > 0.65f;
        Map<UUID, Map<Object, double[]>> hook = PrideHook.beams();
        long nowMs = System.currentTimeMillis();
        for (EntityRollingStock s : cam72cam.mod.world.World.get(w).getEntities(EntityRollingStock.class)) {
            cam72cam.mod.math.Vec3d p = s.getPosition();
            double d2 = sq(p.x - me.posX) + sq(p.y - me.posY) + sq(p.z - me.posZ);
            if (d2 > r2 || !(s instanceof EntityMoveableRollingStock m)) continue;
            double speed = Math.abs(m.getCurrentSpeed().metric());
            // the windows of a lit carriage
            if (s instanceof CarPassenger && LightConfig.carLightLevel > 0 && (s.hasElectricalPower() || speed > 1)) {
                double y = p.y + 1.6 * s.gauge.scale();
                if (dark(w, p.x, y, p.z, day)) out.add(new double[]{p.x, y, p.z, LightConfig.carLightLevel, d2});
            }
            // headlight beams
            if (LightConfig.headlightBeams <= 0) continue;
            List<double[]> heads = new ArrayList<>();
            Map<Object, double[]> lit = hook == null ? null : hook.get(s.getUUID());
            if (lit != null) { for (double[] b : lit.values()) if (nowMs - (long) b[6] < 600) heads.add(b); }
            else if (hook == null && s instanceof Locomotive && s.hasElectricalPower() && m.prideLights != 1) {
                boolean rev = m.getCurrentSpeed().minecraft() < 0;
                double half = s.getDefinition().getLength(s.gauge) / 2;
                cam72cam.mod.math.Vec3d fwd = VecUtil.fromWrongYaw(1, s.getRotationYaw());
                double sx = rev ? -fwd.x : fwd.x, sz = rev ? -fwd.z : fwd.z;
                heads.add(new double[]{p.x + sx * half, p.y + 1.2 * s.gauge.scale(), p.z + sz * half, sx, sz, m.prideLights == 2 ? 0 : 1, nowMs});
            }
            for (double[] h : heads) {
                double len = Math.sqrt(h[3] * h[3] + h[4] * h[4]);
                if (len < 1e-4) continue;
                boolean bright = h[5] > 0.5;
                for (int i = 0; i < LightConfig.headlightBeams && i < SPOTS.length; i++) {
                    double dist = SPOTS[i] * Math.max(0.5, s.gauge.scale());
                    double x = h[0] + h[3] / len * dist, z = h[2] + h[4] / len * dist, y = h[1] - 0.6;
                    int lum = bright ? 15 - i : 9 - i * 2;
                    if (lum <= 0 || !dark(w, x, y, z, day)) continue;
                    out.add(new double[]{x, y, z, lum, sq(x - me.posX) + sq(y - me.posY) + sq(z - me.posZ)});
                }
            }
        }
        return out;
    }

    /** light only matters where it is dark: night, or under cover (tunnels, train sheds) by day */
    private static boolean dark(World w, double x, double y, double z, boolean day) {
        if (!day) return true;
        BlockPos b = new BlockPos(x, y, z);
        return w.isBlockLoaded(b) && w.getLightFor(EnumSkyBlock.SKY, b) < 10;
    }

    private static double sq(double v) { return v * v; }

    /** put the pooled light points where they are wanted; spare ones go dark (lum 0) rather than being churned */
    private static void apply(World w, List<double[]> want) {
        for (Iterator<EntityTrainLight> it = POOL.iterator(); it.hasNext(); ) if (it.next().isDead) it.remove();
        while (POOL.size() < want.size()) {
            EntityTrainLight l = new EntityTrainLight(w);
            double[] s = want.get(POOL.size());
            l.setPosition(s[0], s[1], s[2]);
            if (!w.spawnEntity(l)) { /* WorldClient queues it until its chunk is there */ }
            POOL.add(l);
        }
        int i = 0;
        for (EntityTrainLight l : POOL) {
            if (i < want.size()) { double[] s = want.get(i); l.setPosition(s[0], s[1], s[2]); l.lum = (int) s[3]; }
            else l.lum = 0;
            i++;
        }
        while (POOL.size() > Math.max(want.size(), 0) + 8) { EntityTrainLight l = POOL.remove(POOL.size() - 1); l.setDead(); w.removeEntity(l); }
        active = want.size();
    }

    /** reads the fork's PrideBeams (lit headlights per stock) by reflection, so an older fork jar still runs */
    static final class PrideHook {
        private static boolean tried;
        private static java.lang.reflect.Field field;

        @SuppressWarnings("unchecked")
        static Map<UUID, Map<Object, double[]>> beams() {
            if (!tried) {
                tried = true;
                try { field = Class.forName("cam72cam.immersiverailroading.model.part.PrideBeams").getField("LIT"); }
                catch (ReflectiveOperationException | LinkageError ex) { field = null; }
            }
            try { return field == null ? null : (Map<UUID, Map<Object, double[]>>) field.get(null); }
            catch (ReflectiveOperationException | ClassCastException ex) { return null; }
        }
    }
}
