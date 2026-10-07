package com.dogpound.railmap.server;

import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Freight;
import cam72cam.immersiverailroading.entity.FreightTank;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.entity.LocomotiveSteam;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapConfig;
import com.dogpound.railmap.server.WearData.Part;
import com.dogpound.railmap.server.WearData.Record;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.List;
import java.util.UUID;

/**
 * Maintenance and wear (her railway list §13). Once a second every loaded IR car and loco adds
 * the distance it ran to its odometer and wears its parts:
 * <ul>
 *   <li>wheels: distance, double above 120 km/h</li>
 *   <li>bearings: distance, more when loaded</li>
 *   <li>brakes: brake applied while moving, more at speed</li>
 *   <li>engine (locomotives): distance under throttle</li>
 *   <li>electrics (diesel/electric locomotives): distance</li>
 * </ul>
 * At 80% the crew and anyone nearby is told MAINTENANCE REQUIRED; at 100% (if wearEffects) a worn
 * engine only gets half throttle and worn brakes/wheels/bearings hold the train to wornSpeedCapKmh.
 * A Maintenance Depot beside the track repairs stopped stock. Everything is a normal IR control
 * input (throttle), never a physics hack.
 */
public final class Wear {
    private static final boolean IR = Loader.isModLoaded("immersiverailroading");
    private static final float WARN = 80, WORN = 100;

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side.isClient() || !IR || !RailMapConfig.wearEnabled) return;
        if (e.world.getTotalWorldTime() % 20 != 0) return;
        try {
            tick(e.world);
        } catch (LinkageError | RuntimeException ex) {
            RailMap.LOG.warn("[IR Extras] wear tick failed: {}", ex.toString());
        }
    }

    private static void tick(World mc) {
        cam72cam.mod.world.World world = cam72cam.mod.world.World.get(mc);
        List<EntityRollingStock> all = world.getEntities(EntityRollingStock.class);
        if (all.isEmpty()) return;
        WearData data = WearData.get(mc);
        double rate = RailMapConfig.wearRate;
        boolean dirty = false;

        for (EntityRollingStock stock : all) {
            if (stock.isDead()) continue;
            float kmh = 0;
            if (stock instanceof EntityMoveableRollingStock m && m.getCurrentSpeed() != null)
                kmh = Math.abs((float) m.getCurrentSpeed().metric());
            Record r = data.of(stock.getUUID());
            Locomotive loco = stock instanceof Locomotive l ? l : null;

            if (kmh > 0.5f) {
                double km = kmh / 3600.0;                       // one second of running
                r.km += km;
                r.kmSinceService += km;
                add(r, Part.WHEELS, km * 0.5 * (kmh > 120 ? 2 : 1) * rate);
                add(r, Part.BEARINGS, km * (0.3 + 0.3 * load(stock) / 100.0) * rate);
                if (loco != null) {
                    add(r, Part.ENGINE, km * 0.4 * (0.5 + loco.getThrottle()) * rate);
                    if (!(loco instanceof LocomotiveSteam)) add(r, Part.ELECTRICAL, km * 0.2 * rate);
                    float brake = loco.getTrainBrakePos();
                    if (brake > 0.05f) add(r, Part.BRAKES, brake * kmh / 100.0 * 0.15 * rate);
                }
                dirty = true;
            }
            if (warn(mc, stock, r)) dirty = true;
            if (loco != null && RailMapConfig.wearEffects) limit(loco, r, kmh);
        }
        if (dirty) data.markDirty();
    }

    private static void add(Record r, Part p, double amount) {
        r.wear[p.ordinal()] = (float) Math.min(150, r.wear[p.ordinal()] + amount);
    }

    private static int load(EntityRollingStock s) {
        if (s instanceof FreightTank ft) return ft.getPercentLiquidFull();
        if (s instanceof Freight f) return f.getPercentCargoFull();
        return 0;
    }

    /** Announce each part once when it crosses 80%. Returns true when a flag changed. */
    private static boolean warn(World mc, EntityRollingStock stock, Record r) {
        boolean changed = false;
        for (Part p : Part.values()) {
            int bit = 1 << p.ordinal();
            if (r.wear[p.ordinal()] >= WARN && (r.warned & bit) == 0) {
                r.warned |= bit;
                changed = true;
                String msg = TextFormatting.GOLD + "⚠ MAINTENANCE REQUIRED: " + TextFormatting.WHITE + name(stock)
                        + TextFormatting.GRAY + " · " + p.label + " " + Math.round(r.wear[p.ordinal()]) + "% worn · "
                        + String.format("%.1f km since service", r.kmSinceService);
                tellNearby(mc, stock, msg);
            }
        }
        return changed;
    }

    /** Worn parts limit the train through its own controls. */
    private static void limit(Locomotive loco, Record r, float kmh) {
        if (r.wear[Part.ENGINE.ordinal()] >= WORN && loco.getThrottle() > 0.5f) loco.setThrottle(0.5f);
        boolean capped = r.wear[Part.BRAKES.ordinal()] >= WORN || r.wear[Part.WHEELS.ordinal()] >= WORN
                || r.wear[Part.BEARINGS.ordinal()] >= WORN;
        if (capped && kmh > RailMapConfig.wornSpeedCapKmh && loco.getThrottle() > 0) loco.setThrottle(0);
    }

    private static void tellNearby(World mc, EntityRollingStock stock, String msg) {
        Vec3d p = stock.getPosition();
        for (EntityPlayer pl : mc.playerEntities)
            if (pl.getDistanceSq(p.x, p.y, p.z) < 96 * 96) pl.sendMessage(new TextComponentString(msg));
    }

    static String name(EntityRollingStock s) {
        if (s.tag != null && !s.tag.isEmpty()) return s.tag;
        return s.getDefinition() == null ? "Train" : s.getDefinition().name();
    }

    /** Worst wear (0-100+) of this stock, -1 when unknown. Used by the board and displays. */
    public static int worst(World mc, UUID id) {
        Record r = WearData.get(mc).peek(id);
        return r == null ? -1 : Math.round(r.worst());
    }

    /** One line per part, for the depot / command report. */
    public static String report(Record r) {
        StringBuilder b = new StringBuilder();
        for (Part p : Part.values()) {
            float w = r.wear[p.ordinal()];
            TextFormatting c = w >= WORN ? TextFormatting.RED : w >= WARN ? TextFormatting.GOLD : w >= 50 ? TextFormatting.YELLOW : TextFormatting.GREEN;
            if (b.length() > 0) b.append(TextFormatting.DARK_GRAY).append(" · ");
            b.append(c).append(p.label).append(' ').append(Math.round(w)).append('%');
        }
        b.append(TextFormatting.GRAY).append(String.format(" · %.1f km total, %.1f since service", r.km, r.kmSinceService));
        return b.toString();
    }

    /** Depot service: repair every part a step, clear the warnings once clean. Returns true when anything changed. */
    public static boolean service(World mc, EntityRollingStock stock, double step) { return service(mc, stock, step, ~0, true); }

    /** @param mask bit per {@link Part} to repair; @param announce say "fully serviced" when done */
    public static boolean service(World mc, EntityRollingStock stock, double step, int mask, boolean announce) {
        WearData data = WearData.get(mc);
        Record r = data.of(stock.getUUID());
        boolean any = false;
        for (Part p : Part.values()) {
            int i = p.ordinal();
            if ((mask & (1 << i)) == 0) continue;
            if (r.wear[i] > 0) { r.wear[i] = (float) Math.max(0, r.wear[i] - step); any = true; }
            if (r.wear[i] < WARN) r.warned &= ~(1 << i);
        }
        if (any && r.worst() == 0) {
            r.kmSinceService = 0;
            r.servicedAt = mc.getTotalWorldTime();
            if (announce) tellNearby(mc, stock, TextFormatting.GREEN + "✔ " + name(stock) + " fully serviced");
        }
        if (any) data.markDirty();
        return any;
    }

    /**
     * Depot: service every stopped car/loco within reach of {@code at}. Returns how many were
     * worked on (0 = nothing to do). Safe to call without IR loaded (returns -1).
     */
    public static int serviceNear(World mc, net.minecraft.util.math.BlockPos at, int reach, double step) {
        return serviceNear(mc, at, reach, step, 0.5, ~0, true);
    }

    public static int serviceNear(World mc, net.minecraft.util.math.BlockPos at, int reach, double step, double parkedKmh, int mask, boolean announce) {
        if (!IR || !RailMapConfig.wearEnabled) return -1;
        cam72cam.mod.world.World world = cam72cam.mod.world.World.get(mc);
        int n = 0;
        double r2 = reach * (double) reach;
        for (EntityRollingStock s : world.getEntities(EntityRollingStock.class)) {
            if (s.isDead()) continue;
            Vec3d p = s.getPosition();
            if (at.distanceSqToCenter(p.x, p.y, p.z) > r2) continue;
            if (s instanceof EntityMoveableRollingStock m && m.getCurrentSpeed() != null
                    && Math.abs(m.getCurrentSpeed().metric()) > parkedKmh) continue;  // only parked stock
            if (service(mc, s, step, mask, announce)) n++;
        }
        return n;
    }

    /** Report lines for every car/loco within reach (depot right-click). */
    public static List<String> reportNear(World mc, net.minecraft.util.math.BlockPos at, int reach) {
        List<String> out = new java.util.ArrayList<>();
        if (!IR) { out.add("Immersive Railroading is not installed"); return out; }
        cam72cam.mod.world.World world = cam72cam.mod.world.World.get(mc);
        double r2 = reach * (double) reach;
        WearData data = WearData.get(mc);
        for (EntityRollingStock s : world.getEntities(EntityRollingStock.class)) {
            Vec3d p = s.getPosition();
            if (s.isDead() || at.distanceSqToCenter(p.x, p.y, p.z) > r2) continue;
            out.add(TextFormatting.WHITE + name(s) + TextFormatting.DARK_GRAY + ": " + report(data.of(s.getUUID())));
        }
        return out;
    }

    /** Testing aid: add {@code pct} wear (negative repairs) to one part or "all" on stock within reach. */
    public static int adjustNear(World mc, net.minecraft.util.math.BlockPos at, int reach, String part, double pct) {
        if (!IR) return 0;
        cam72cam.mod.world.World world = cam72cam.mod.world.World.get(mc);
        WearData data = WearData.get(mc);
        double r2 = reach * (double) reach;
        int n = 0;
        for (EntityRollingStock s : world.getEntities(EntityRollingStock.class)) {
            Vec3d p = s.getPosition();
            if (s.isDead() || at.distanceSqToCenter(p.x, p.y, p.z) > r2) continue;
            Record r = data.of(s.getUUID());
            for (Part pt : Part.values()) {
                if (!part.equalsIgnoreCase("all") && !part.equalsIgnoreCase(pt.label) && !part.equalsIgnoreCase(pt.name())) continue;
                int i = pt.ordinal();
                r.wear[i] = (float) Math.max(0, Math.min(150, r.wear[i] + pct));
                if (r.wear[i] < WARN) r.warned &= ~(1 << i);
            }
            n++;
        }
        data.markDirty();
        return n;
    }
}
