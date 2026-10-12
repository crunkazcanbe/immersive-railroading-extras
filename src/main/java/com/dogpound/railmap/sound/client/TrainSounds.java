package com.dogpound.railmap.sound.client;

import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.sound.PackSounds;
import com.dogpound.railmap.sound.SoundConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.entity.Entity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.*;

/**
 * Wheel flange squeal, client side: a car rounding a tight curve squeals (stick-slip of the flange against the rail
 * head excites the wheel web). Curve radius comes from how fast each car's heading turns against its speed; squeal
 * needs a radius under ~150 m and a running speed of 5-70 km/h, gets louder the tighter the curve, and each car has
 * its own cool-down so a whole train squeals car by car through the bend. (Rail-joint clack is IR's own.)
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID, value = Side.CLIENT)
public final class TrainSounds {
    private TrainSounds() {}

    private static final Map<UUID, float[]> LAST = new HashMap<>();   // yaw, next-allowed time (ms)
    private static final Random RNG = new Random();

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !SoundConfig.trainRunning) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) { LAST.clear(); return; }
        if (mc.isGamePaused() || mc.world.getTotalWorldTime() % 5 != 0) return;
        SoundEvent squeal = PackSounds.get("train.flange_squeal");
        if (squeal == null) return;
        try {
            scan(mc, mc.getRenderViewEntity() == null ? mc.player : mc.getRenderViewEntity(), squeal);
        } catch (LinkageError | RuntimeException ignored) {
            // IR not ready / a car half-loaded: try again next pass
        }
        if (LAST.size() > 512) LAST.clear();
    }

    private static void scan(Minecraft mc, Entity me, SoundEvent squeal) {
        long now = System.currentTimeMillis();
        for (EntityMoveableRollingStock s : cam72cam.mod.world.World.get(mc.world).getEntities(EntityMoveableRollingStock.class)) {
            cam72cam.mod.math.Vec3d p = s.getPosition();
            double dx = p.x - me.posX, dy = p.y - me.posY, dz = p.z - me.posZ;
            if (dx * dx + dy * dy + dz * dz > 64 * 64) continue;
            float yaw = s.getRotationYaw();
            float[] l = LAST.get(s.getUUID());
            if (l == null) { LAST.put(s.getUUID(), new float[]{yaw, 0}); continue; }
            float dYaw = Math.abs(wrap(yaw - l[0]));                        // degrees in 5 ticks
            l[0] = yaw;
            double kmh = Math.abs(s.getCurrentSpeed().metric());
            if (kmh < 5 || kmh > 70 || now < l[1]) continue;
            double v = kmh / 3.6, turn = Math.toRadians(dYaw) / 0.25;     // rad/s
            if (turn < 1e-3) continue;
            double radius = v / turn;
            if (radius > 150 || radius < 3) continue;
            if (com.dogpound.railmap.ops6.Lubricators.quiet(mc.world, p.x, p.y, p.z)) continue;   // batch 6 E #23: a flange lubricator greased this curve
            float tight = (float) Math.min(1, (150 - radius) / 120);
            if (RNG.nextFloat() > 0.35f + 0.5f * tight) continue;
            l[1] = now + 2500 + RNG.nextInt(2500);
            float vol = 0.35f + 0.65f * tight;                               // group volume: SoundMixer
            mc.getSoundHandler().playSound(new PositionedSoundRecord(squeal, SoundCategory.NEUTRAL, vol * 1.5f,
                    0.9f + 0.25f * (float) Math.min(1, kmh / 60), (float) p.x, (float) p.y, (float) p.z));
        }
    }

    private static float wrap(float a) {
        a %= 360f;
        if (a > 180f) a -= 360f;
        if (a < -180f) a += 360f;
        return a;
    }
}
