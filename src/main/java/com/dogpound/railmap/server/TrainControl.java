package com.dogpound.railmap.server;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.library.Augment;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.mod.math.Vec3i;
import com.dogpound.railmap.RailMap;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * The dispatcher's hand on the train: what the board's train card can do to a locomotive, and
 * what its stop markers can do to a loader.
 * <p>
 * Everything here is a normal IR control input — the same setter the cab levers use — so a
 * remote move behaves exactly like a driven one: physics, couplers, brakes and signals all
 * still apply. Nothing here teleports or fakes a move.
 */
public final class TrainControl {
    /** Throttle/brake step per button press. */
    private static final float STEP = 0.1f;

    public enum Cmd {
        THROTTLE_UP, THROTTLE_DOWN, BRAKE_UP, BRAKE_DOWN,
        REVERSER_FORWARD, REVERSER_NEUTRAL, REVERSER_REVERSE,
        HORN, BELL, EMERGENCY_STOP;

        public static Cmd byOrdinal(int i) {
            Cmd[] v = values();
            return i >= 0 && i < v.length ? v[i] : EMERGENCY_STOP;
        }
    }

    private TrainControl() {}

    /** Apply a command to the loco with this entity id. Returns a line for the player's action bar. */
    public static String apply(World world, EntityPlayerMP player, int entityId, Cmd cmd) {
        cam72cam.mod.world.World umc = cam72cam.mod.world.World.get(world);
        EntityRollingStock stock = umc.getEntity(entityId, EntityRollingStock.class);
        if (stock == null) return "That train is not loaded any more";
        Locomotive loco = stock instanceof Locomotive l ? l : leadLoco(stock);
        if (loco == null) return "No locomotive in that consist";

        switch (cmd) {
            case THROTTLE_UP -> loco.setThrottle(clamp(loco.getThrottle() + STEP));
            case THROTTLE_DOWN -> loco.setThrottle(clamp(loco.getThrottle() - STEP));
            case BRAKE_UP -> loco.setTrainBrake(clamp(loco.getTrainBrakePos() + STEP));
            case BRAKE_DOWN -> loco.setTrainBrake(clamp(loco.getTrainBrakePos() - STEP));
            case REVERSER_FORWARD -> loco.setReverser(1f);
            case REVERSER_NEUTRAL -> loco.setReverser(0f);
            case REVERSER_REVERSE -> loco.setReverser(-1f);
            case HORN -> { if (player != null) loco.setHorn(40, player.getUniqueID()); else loco.setHorn(40, 1f); }
            case BELL -> loco.setBell(loco.getBell() > 0 ? 0 : 200);
            case EMERGENCY_STOP -> {
                loco.setThrottle(0f);
                loco.setReverser(0f);
                loco.setTrainBrake(1f);
                return "EMERGENCY: throttle off, brakes full";
            }
        }
        return String.format("%s · throttle %d%% · brake %d%% · %s",
                name(loco), Math.round(loco.getThrottle() * 100), Math.round(loco.getTrainBrakePos() * 100),
                loco.getReverser() > 0.05 ? "forward" : loco.getReverser() < -0.05 ? "reverse" : "neutral");
    }

    /**
     * Remote load/unload: an IR loader or unloader augment runs while it has redstone, so the
     * board simply feeds the augment tile the signal it wants. Toggling returns the new state.
     */
    public static String toggleAugment(World world, BlockPos pos) {
        cam72cam.mod.world.World umc = cam72cam.mod.world.World.get(world);
        Vec3i p = new Vec3i(pos.getX(), pos.getY(), pos.getZ());
        TileRailBase rail = umc.getBlockEntity(p, TileRailBase.class);
        if (rail == null) return "Nothing there any more";
        Augment aug = rail.getAugment();
        if (aug == null) return "No augment on that piece";
        boolean on;
        try {
            // There is no getter for the level, so we track it by asking the block for power:
            // flipping between 0 and 15 is enough for the loaders, detectors and controls.
            on = LAST_ON.remove(pos.toLong()) == null;
            rail.setRedstoneLevel(on ? 15 : 0);
            if (on) LAST_ON.put(pos.toLong(), Boolean.TRUE);
            rail.markDirty();
        } catch (RuntimeException | LinkageError e) {
            RailMap.LOG.warn("[RailMap] augment toggle failed at {}: {}", pos, e.toString());
            return "That augment refused the signal";
        }
        return pretty(aug) + (on ? " ON" : " off");
    }

    /** Which augments the board is allowed to drive; the rest are read-only markers. */
    public static boolean controllable(Augment a) {
        switch (a) {
            case ITEM_LOADER:
            case ITEM_UNLOADER:
            case FLUID_LOADER:
            case FLUID_UNLOADER:
            case WATER_TROUGH:
            case LOCO_CONTROL:
                return true;
            default:
                return false;
        }
    }

    private static final java.util.Map<Long, Boolean> LAST_ON = new java.util.HashMap<>();

    private static Locomotive leadLoco(EntityRollingStock stock) {
        if (!(stock instanceof EntityCoupleableRollingStock c)) return null;
        List<EntityCoupleableRollingStock> train = c.getTrain();
        for (EntityCoupleableRollingStock s : train) if (s instanceof Locomotive l) return l;
        return null;
    }

    private static String name(EntityRollingStock s) {
        if (s.tag != null && !s.tag.isEmpty()) return s.tag;
        return s.getDefinition() == null ? "Train" : s.getDefinition().name();
    }

    private static String pretty(Augment a) {
        String t = a.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
