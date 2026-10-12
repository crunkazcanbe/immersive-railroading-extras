package com.dogpound.railmap.sim;

import cam72cam.immersiverailroading.entity.CarPassenger;
import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.server.WearData;
import net.minecraft.block.material.Material;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import java.util.Locale;

/**
 * Wheel-slip and wheel-slide (her ChatGPT list item 12). IR already has the basics: a rain / snow adhesion factor,
 * wheel slip when the tractive effort beats adhesion (traction halves while slipping), sanding (x3 adhesion) and
 * wheels sliding when the brakes beat adhesion. On top, twice a second per car:
 * <ul>
 *   <li><b>rail-head condition</b>: leaves over the track (much worse wet - the autumn "leaf mulch"), frost on cold
 *       nights, morning dew; under a roof / in a tunnel the rail stays dry. Fed to the fork's prideRailAdhesion;</li>
 *   <li><b>sanders</b>: automatic sanding on slip, on slide and on a bad rail when starting away; sand comes out of the
 *       locomotive's sandbox (slot 3 of its inventory) and runs out (the fork now takes it on the server);</li>
 *   <li><b>traction control</b> (slip control): power held just under adhesion instead of spinning (fork hook);</li>
 *   <li><b>wheel-slide protection</b> on locomotives and passenger stock: brake force eased to the adhesion limit
 *       while a wheel would lock (fork hook, lamp in the cab); freight wagons have none, as in real life;</li>
 *   <li><b>wheel flats</b>: every second a car slides locked at speed grinds a flat; flats thump, wear the wheels
 *       (Wear) and alarm past 60 mm; a depot that turns the wheels takes them off.</li>
 * </ul>
 */
public final class AdhesionSim {
    private AdhesionSim() {}

    static void tick(World mc, SimData data, long now, double dt) {
        cam72cam.mod.world.World w = cam72cam.mod.world.World.get(mc);
        WearData wear = WearData.get(mc);
        for (EntityMoveableRollingStock s : w.getEntities(EntityMoveableRollingStock.class)) {
            if (s.isDead()) continue;
            SimData.Car c = data.of(s.getUUID());
            // ---- the rail head
            float adh = 1f;
            String why = "";
            if (SimConfig.railConditions) {
                Vec3d p = s.getPosition();
                BlockPos at = new BlockPos(p.x, p.y, p.z);
                float[] r = c.adhCache;
                if (r == null || c.adhPos == null || c.adhPos.distanceSq(at) > 16 || now - c.adhAt > 200) {
                    r = c.adhCache = rail(mc, at);
                    c.adhPos = at;
                    c.adhAt = now;
                }
                adh = r[0];
                why = r[1] > 0 ? REASONS[(int) r[1]] : "";
            }
            adh = com.dogpound.railmap.newrail6.Rack.adhesion(mc, s, adh);   // batch 6 #16: a cog loco on rack rail has full grip
            adh = Math.round(adh * 20) / 20f;
            if (Math.abs(s.prideRailAdhesion - adh) > 0.01f) s.prideRailAdhesion = adh;
            c.railAdhesion = adh;
            c.railWhy = why;

            // ---- WSP: locomotives and passenger stock carry it
            boolean fitted = s instanceof Locomotive || s instanceof CarPassenger;
            boolean wsp = fitted && c.wsp;
            if (s.prideWsp != wsp) s.prideWsp = wsp;
            c.wspActive = s.prideWspActive;
            c.slide = s.isSliding();
            double kmh = Math.abs(s.getCurrentSpeed().metric());

            // ---- locomotives: traction control and sanders
            if (s instanceof Locomotive l) {
                if (l.prideTractionControl != c.tractionControl) l.prideTractionControl = c.tractionControl;
                c.slip = l.slipping;
                boolean bad = adh < 0.8f || s.adhesionCoefficient() < 0.75f;
                boolean starting = Math.abs(l.getThrottle()) > 0.4f && kmh < 15;
                if (c.autoSand && (c.slip || c.slide || c.wspActive || bad && starting)) c.sandHold = 3;
                else c.sandHold = Math.max(0, c.sandHold - dt);
                boolean sand = c.sandHold > 0;                          // auto sanding, or the diagnostic panel's SAND test
                if (l.prideSand != sand) l.prideSand = sand;
                c.sanding = l.isSanding;
            }

            // ---- wheel flats from locked sliding
            if (SimConfig.wheelFlats) {
                if (c.slide && !c.wspActive && kmh > 3) {
                    c.slideSec += dt;
                    if (c.slideSec > 1.0) {
                        float before = c.flatMm;
                        c.flatMm = Math.min(120f, c.flatMm + (float) (dt * (8 + kmh / 6)));
                        WearData.Record r = wear.of(s.getUUID());
                        r.wear[WearData.Part.WHEELS.ordinal()] = Math.min(100f, r.wear[WearData.Part.WHEELS.ordinal()] + (c.flatMm - before) / 3f);
                        wear.markDirty();
                        data.markDirty();
                        if (before < 60 && c.flatMm >= 60) {
                            Vec3d p = s.getPosition();
                            AlarmManager.get(mc).raise(mc, "flat:" + s.getUUID(), AlarmManager.Cat.MACHINE, AlarmManager.Sev.MINOR,
                                    String.format(Locale.ROOT, "WHEEL FLAT %d mm on %s - take it out of service (depot)", Math.round(c.flatMm), ConsistSim.name(s)),
                                    new BlockPos(p.x, p.y, p.z), true);
                        }
                    }
                } else c.slideSec = 0;
                // a flat thumps once a wheel turn (a ~0.9 m wheel: 2.8 m per turn)
                if (c.flatMm >= 15 && kmh > 5) {
                    c.flatThump += kmh / 3.6 * dt / 2.8;
                    if (c.flatThump >= 1) {
                        c.flatThump = 0;
                        Vec3d p = s.getPosition();
                        mc.playSound(null, p.x, p.y, p.z, com.dogpound.railmap.sound.PackSounds.ev("train.rail_joint"), SoundCategory.NEUTRAL,
                                Math.min(0.35f, c.flatMm / 300f), 0.5f + (float) Math.min(0.4, kmh / 200));
                    }
                }
            }
            // in the depot: wheels turned (flats gone), knuckles renewed, a diesel's oil / coolant / battery seen to
            WearData.Record r = wear.peek(s.getUUID());
            if (r != null && r.servicedAt > c.serviceSeen) {
                boolean first = c.serviceSeen == 0 && now - r.servicedAt > 200;
                c.serviceSeen = r.servicedAt;
                if (!first) {
                    if (r.wear[WearData.Part.WHEELS.ordinal()] < 20) c.flatMm = 0;
                    if (s instanceof cam72cam.immersiverailroading.entity.LocomotiveDiesel) EngineSim.service(c);
                    else { c.coupler[0] = 1f; c.coupler[1] = 1f; }
                    AlarmManager.get(mc).clear("flat:" + s.getUUID());
                }
                data.markDirty();
            }
        }
    }

    static final String[] REASONS = {"", "wet leaves", "leaves", "frost", "dew", "dry (covered)", "snow", "ice", "standing water"};

    /** {adhesion factor, reason index}: leaves within 3 blocks above / beside the track, frost, dew */
    static float[] rail(World mc, BlockPos at) {
        boolean sky = mc.canSeeSky(at.up());
        if (!sky) return new float[]{1.0f, 5};
        int leaves = 0;
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++)
                for (int dy = 1; dy <= 6; dy++) {
                    BlockPos q = at.add(dx, dy, dz);
                    if (!mc.isBlockLoaded(q)) continue;
                    if (mc.getBlockState(q).getMaterial() == Material.LEAVES) leaves++;
                }
        boolean wet = mc.isRainingAt(at.up());
        float leafy = Math.min(1f, leaves / 14f);
        if (leafy > 0.15f) com.dogpound.railmap.weather.WxTrains.leafSeen(mc, at);                 // the weather-train planner's leaf-fall map
        if (leafy > 0.15f && !com.dogpound.railmap.weather.Weather.railheadClean(mc, at)) return new float[]{wet ? 1f - 0.65f * leafy : 1f - 0.35f * leafy, wet ? 1 : 2};   // swept / sandite-treated rail heads keep their grip
        float[] wx = com.dogpound.railmap.weather.Weather.adhesion(mc, at);         // #63-64: snow, ice, flood water on the rail
        if (wx != null) return wx;
        Biome b = mc.getBiome(at);
        long t = mc.getWorldTime() % 24000;
        boolean night = t > 13000 && t < 23500;
        boolean morning = t >= 22500 || t < 1500;
        if (b.getTemperature(at) < 0.25f && night) return new float[]{0.7f, 3};
        if (morning && !wet && b.getRainfall() > 0.3f) return new float[]{0.85f, 4};
        return new float[]{1f, 0};
    }

    /** "rail 55% wet leaves · SAND · SLIP" */
    public static String line(Locomotive l, SimData.Car c) {
        if (c == null) return "";
        StringBuilder b = new StringBuilder();
        b.append(String.format(Locale.ROOT, "adhesion %d%%", Math.round(l.adhesionCoefficient() * 100)));
        if (!c.railWhy.isEmpty()) b.append(" (").append(c.railWhy).append(')');
        float sand = l.prideSandLevel();
        if (sand >= 0) b.append(String.format(Locale.ROOT, " · sand %d%%", Math.round(sand * 100)));
        if (c.flatMm >= 15) b.append(String.format(Locale.ROOT, " · flat %d mm", Math.round(c.flatMm)));
        return b.toString();
    }
}
