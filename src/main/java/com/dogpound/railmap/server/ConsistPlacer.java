package com.dogpound.railmap.server;

import cam72cam.immersiverailroading.entity.EntityBuildableRollingStock;
import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock.CouplerType;
import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.library.Gauge;
import cam72cam.immersiverailroading.registry.EntityRollingStockDefinition;
import cam72cam.immersiverailroading.registry.UnitDefinition;
import cam72cam.immersiverailroading.thirdparty.trackapi.IRPathingData;
import cam72cam.immersiverailroading.thirdparty.trackapi.ITrack;
import cam72cam.immersiverailroading.util.VecUtil;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.world.World;
import net.minecraft.util.math.BlockPos;

/**
 * Places a whole multiple-unit train on the track you look at. IR's own placer looks for track 0.7 above each next
 * car's position, which misses our Pride tracks (stock rides a little higher on them) and stopped after car 1. This
 * one walks the track itself: each car starts where the last one's coupler ended, found by searching a few heights.
 */
final class ConsistPlacer {
    private ConsistPlacer() {}

    /** track at (or just under) a point */
    private static ITrack track(World w, Vec3d p) {
        // IR finds track from a point 0.8 above the rail block; try this block level and the ones around it
        int by = (int) Math.floor(p.y);
        for (int dy : new int[]{ 0, -1, 1, -2, 2 }) {
            ITrack t = ITrack.get(w, new Vec3d(p.x, by + dy + 0.8, p.z), true);
            if (t != null) return t;
            t = ITrack.get(w, new Vec3d(p.x, by + dy + 0.1, p.z), true);
            if (t != null) return t;
        }
        return null;
    }

    /** the direction along the track closest to where the player looks (degrees, IR 'wrong yaw' = MC yaw here) */
    private static float alongTrack(ITrack t, Vec3d at, float lookYaw, double gauge) {
        IRPathingData a = new IRPathingData(at, 0.0), b2 = new IRPathingData(at, 0.0);
        t.getNextPosition(a, VecUtil.fromWrongYaw(2.0, lookYaw), gauge);
        Vec3d d = a.getUMCPos().subtract(at);
        if (d.lengthSquared() < 1e-4) { t.getNextPosition(b2, VecUtil.fromWrongYaw(2.0, lookYaw + 90), gauge); d = b2.getUMCPos().subtract(at); }
        return VecUtil.toWrongYaw(d);
    }

    static int place(World w, BlockPos look, float lookYaw, UnitDefinition unit) {
        Vec3d spawnPos = new Vec3d(look.getX() + 0.5, look.getY() + 0.1, look.getZ() + 0.5);
        ITrack first = track(w, spawnPos);
        if (first == null) return 0;
        double trackGauge = first.getTrackGauges()[0];
        Gauge gauge = Gauge.from(trackGauge);
        float originalRot = alongTrack(first, spawnPos, lookYaw, gauge.value());
        // the track direction comes back either way round: lay the train out the way the player is looking
        Vec3d lookDir = VecUtil.fromWrongYaw(1, lookYaw), dir = VecUtil.fromWrongYaw(1, originalRot);
        if (lookDir.x * dir.x + lookDir.z * dir.z < 0) originalRot = (originalRot + 180F) % 360F;
        int n = 0;
        for (UnitDefinition.Stock rs : unit.unitList) {
            EntityRollingStockDefinition def = rs.definition;
            boolean flipped = rs.direction.getDirection();
            ITrack initte = track(w, spawnPos);
            com.dogpound.railmap.RailMap.LOG.debug("[IR Extras] consist car {} ({}) at {} yaw {} -> track {}", n + 1, def.defID, spawnPos, originalRot, initte != null);
            if (initte == null) return n;
            double offset = def.getCouplerPosition(flipped ? CouplerType.FRONT : CouplerType.BACK, gauge) - cam72cam.immersiverailroading.Config.ConfigDebug.couplerRange;
            float yaw = originalRot + (flipped ? 180F : 0F);
            String texture = rs.texture;
            EntityRollingStock stock = def.spawn(w, spawnPos, yaw, gauge, texture);
            IRPathingData center = new IRPathingData(stock.getPosition(), 0.0);
            initte.getNextPosition(center, VecUtil.fromWrongYaw(-0.1, originalRot), gauge.value());
            initte.getNextPosition(center, VecUtil.fromWrongYaw(0.1, originalRot), gauge.value());
            initte.getNextPosition(center, VecUtil.fromWrongYaw(offset, originalRot), gauge.value());
            stock.setPosition(center.getUMCPos());
            rs.controlGroup.forEach(stock::setControlPosition);
            if (stock instanceof EntityMoveableRollingStock) {
                EntityMoveableRollingStock mv = (EntityMoveableRollingStock) stock;
                ITrack c = track(w, center.getUMCPos());
                if (c != null) {
                    float fd = mv.getDefinition().getBogeyFront(gauge), rd = mv.getDefinition().getBogeyRear(gauge);
                    IRPathingData f = center.clone(), r = center.clone();
                    c.getNextPosition(f, VecUtil.fromWrongYaw(fd, yaw), gauge.value());
                    c.getNextPosition(r, VecUtil.fromWrongYaw(rd, yaw), gauge.value());
                    Vec3d front = f.getUMCPos(), rear = r.getUMCPos();
                    mv.setRotationYaw(VecUtil.toWrongYaw(front.subtract(rear)));
                    float pitch = -VecUtil.toPitch(front.subtract(rear)) - 90F;
                    if (cam72cam.mod.util.DegreeFuncs.delta(pitch, 0F) > 90F) pitch = 180F - pitch;
                    mv.setRotationPitch(pitch);
                    mv.setPosition(rear.add(front.subtract(rear).scale(fd / (fd - rd))));
                    mv.setFrontYaw(mv.getRotationYaw());
                    mv.setRearYaw(mv.getRotationYaw());
                }
                mv.newlyPlaced = true;
            }
            if (stock instanceof EntityBuildableRollingStock) ((EntityBuildableRollingStock) stock).setComponents(def.getItemComponents());
            Vec3d length = VecUtil.fromWrongYaw(def.getCouplerPosition(flipped ? CouplerType.BACK : CouplerType.FRONT, stock.gauge), originalRot);
            w.spawnEntity(stock);
            n++;
            spawnPos = stock.getPosition().add(length);
            spawnPos = new Vec3d(spawnPos.x, Math.floor(spawnPos.y) + 0.1, spawnPos.z);
            com.dogpound.railmap.RailMap.LOG.debug("[IR Extras]   placed at {} (front coupler {}), next anchor {}", stock.getPosition(), length, spawnPos);
        }
        return n;
    }
}
