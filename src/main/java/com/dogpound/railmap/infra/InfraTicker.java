package com.dogpound.railmap.infra;

import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.RailMap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Locale;

/** The once-a-second pass for batch 3 (#36-50): crews, relay drift, parallel-feed checks, the recording and test trains. */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class InfraTicker {
    private InfraTicker() {}

    private static final boolean IR = Loader.isModLoaded("immersiverailroading");

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side.isClient()) return;
        World w = e.world;
        long now = w.getTotalWorldTime();
        if (now % 20 != 15) return;
        int sec = (int) (now / 20);
        try {
            FaultLocator.tick(w, now);
            if (sec % 5 == 0) LoadSharing.check(w);
            if (sec % 60 == 7) RelayCal.tick(w);
            if (IR) stock(w, now);
            if (sec % 10 == 3) { OhleRecording.sweep(w, now); RailFlaws.sweep(w, now); }
        } catch (RuntimeException | LinkageError ex) {
            RailMap.LOG.warn("[IR Extras] infra tick failed: {}", ex.toString());
        }
    }

    /** recording cars (#44) and ultrasonic test trains (#41), by IR stock tag */
    private static void stock(World w, long now) {
        java.util.List<EntityRollingStock> all = cam72cam.mod.world.World.get(w).getEntities(EntityRollingStock.class);
        for (EntityRollingStock s : all) {
            if (s.isDead() || s.tag == null || s.tag.isEmpty()) continue;
            String tag = s.tag.toUpperCase(Locale.ROOT);
            boolean rec = OhleRecording.isRecorder(tag), ut = RailFlaws.isTestTrain(tag);
            if (!rec && !ut) continue;
            if (rec) OhleRecording.measure(w, s, now);
            if (ut) {
                Vec3d p = s.getPosition();
                double kmh = s instanceof EntityMoveableRollingStock m && m.getCurrentSpeed() != null ? Math.abs(m.getCurrentSpeed().metric()) : 0;
                RailFlaws.testCar(w, s.getUUID(), com.dogpound.railmap.track.Rails.name(s), new BlockPos(p.x, p.y, p.z), kmh, now);
            }
        }
    }
}
