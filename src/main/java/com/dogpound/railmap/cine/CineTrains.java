package com.dogpound.railmap.cine;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Immersive Railroading locomotives as camera subjects (works on either side). */
public final class CineTrains {
    private CineTrains() {}

    /** {x, y, z, heading yaw deg (direction of travel), speed m/s, half length m, entity id} or null */
    public static double[] pose(World w, String uuid, float pt) {
        if (uuid == null || uuid.isEmpty()) return null;
        try {
            EntityRollingStock rs = cam72cam.mod.world.World.get(w).getEntity(UUID.fromString(uuid), EntityRollingStock.class);
            return rs == null ? null : pose(rs, pt);
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    public static double[] pose(EntityRollingStock rs, float pt) {
        try {
            if (rs.getDefinition() == null) return null;
            Entity e = rs.internal;
            double x = e.lastTickPosX + (e.posX - e.lastTickPosX) * pt, y = e.lastTickPosY + (e.posY - e.lastTickPosY) * pt, z = e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * pt;
            double half = rs.getDefinition().getLength(rs.gauge) / 2.0;
            double kmh = rs instanceof cam72cam.immersiverailroading.entity.EntityMoveableRollingStock m && m.getCurrentSpeed() != null ? m.getCurrentSpeed().metric() : 0;
            boolean back = rs instanceof Locomotive l && l.getReverser() < -0.05;
            double yaw = rs.getRotationYaw() + (back ? 180 : 0);                   // IR facing (-sin, cos) = Minecraft yaw
            return new double[]{x, y, z, yaw, Math.abs(kmh) / 3.6, half, e.getEntityId()};
        } catch (RuntimeException | LinkageError ex) {
            return null;
        }
    }

    /** locomotives within r blocks of (x, z), nearest first */
    public static List<Locomotive> near(World w, double x, double z, double r) {
        List<Locomotive> o = new ArrayList<>();
        try {
            for (Locomotive l : cam72cam.mod.world.World.get(w).getEntities(Locomotive.class)) {
                if (l.isDead()) continue;
                Entity e = l.internal;
                if ((e.posX - x) * (e.posX - x) + (e.posZ - z) * (e.posZ - z) <= r * r) o.add(l);
            }
            o.sort(Comparator.comparingDouble(l -> (l.internal.posX - x) * (l.internal.posX - x) + (l.internal.posZ - z) * (l.internal.posZ - z)));
        } catch (RuntimeException | LinkageError ignored) { }
        return o;
    }

    public static String name(Locomotive l) {
        try { return l.tag != null && !l.tag.isEmpty() ? l.tag : l.getDefinition().name(); } catch (RuntimeException e) { return "loco"; }
    }

    public static String name(World w, String uuid) {
        try {
            Locomotive l = cam72cam.mod.world.World.get(w).getEntity(UUID.fromString(uuid), Locomotive.class);
            return l == null ? "" : name(l);
        } catch (RuntimeException | LinkageError e) {
            return "";
        }
    }
}
