package com.dogpound.railmap.cctv;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * Forward-facing train cameras (an item fitted to an Immersive Railroading locomotive): where the camera is and where
 * it looks, from the loco itself - at the cab end it is driving toward (reverser), at windscreen height. Works on
 * both sides: the server ray-casts / records it, the client renders it live.
 */
public final class TrainCams {
    private TrainCams() {}

    /** {x, y, z, yaw, pitch} or null when the loco isn't loaded here */
    public static double[] pose(World w, String uuid, float pt) {
        if (uuid == null || uuid.isEmpty()) return null;
        double[] drone = com.dogpound.railmap.drone.Drones.camPose(w, uuid, pt);   // an inspection drone flying as this camera
        if (drone != null) return drone;
        if (uuid.startsWith("rmg@")) return com.dogpound.railmap.freight.TileYardGantry.camPose(w, uuid, pt);   // yard gantry spreader camera (batch 5 #1)
        if (uuid.endsWith("#in")) return com.dogpound.railmap.interior.InteriorServer.camPose(w, uuid.substring(0, uuid.length() - 3), pt);   // saloon camera (interiors)
        try {
            EntityRollingStock rs = cam72cam.mod.world.World.get(w).getEntity(UUID.fromString(uuid), EntityRollingStock.class);
            if (rs == null || rs.getDefinition() == null) return null;
            Entity e = rs.internal;
            double x = e.lastTickPosX + (e.posX - e.lastTickPosX) * pt, y = e.lastTickPosY + (e.posY - e.lastTickPosY) * pt, z = e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * pt;
            double gs = rs.gauge.scale();
            double half = rs.getDefinition().getLength(rs.gauge) / 2.0;
            boolean back = rs instanceof Locomotive l && l.getReverser() < -0.05;
            double yaw = Math.toRadians(rs.getRotationYaw());
            double fx = -Math.sin(yaw), fz = Math.cos(yaw);                     // IR facing: (-sin, cos) like TrainTracker
            if (back) { fx = -fx; fz = -fz; }
            double ex = x + fx * (half + 0.15), ez = z + fz * (half + 0.15), ey = y + 2.3 * gs;
            float mcYaw = (float) Math.toDegrees(Math.atan2(-fx, fz));
            return new double[]{ex, ey, ez, mcYaw, CamType.TRAIN.pitch};
        } catch (RuntimeException | LinkageError ex) {
            return null;
        }
    }

    /** the loco a player is looking at / standing next to (within 6 blocks), or null */
    public static EntityRollingStock nearLoco(World w, net.minecraft.entity.player.EntityPlayer p) {
        try {
            EntityRollingStock best = null;
            double bd = 36;
            for (EntityRollingStock rs : cam72cam.mod.world.World.get(w).getEntities(EntityRollingStock.class)) {
                if (!(rs instanceof Locomotive)) continue;
                double d = rs.internal.getDistanceSq(p);
                if (d < bd) { bd = d; best = rs; }
            }
            return best;
        } catch (RuntimeException | LinkageError ex) {
            return null;
        }
    }

    public static String name(EntityRollingStock rs) {
        try { return rs.getDefinition().name(); } catch (RuntimeException e) { return "Loco"; }
    }
}
