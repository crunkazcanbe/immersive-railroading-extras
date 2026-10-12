package com.dogpound.railmap.scan;

import cam72cam.immersiverailroading.library.SwitchState;
import cam72cam.immersiverailroading.library.TrackItems;
import cam72cam.immersiverailroading.tile.TileRail;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.immersiverailroading.util.SwitchUtil;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.world.World;
import net.minecraft.util.math.BlockPos;

/**
 * Throws IR switches from the board. IR's own Switch Key does exactly this: it "forces" the
 * switch to a state, overriding redstone until forced back to NONE (= automatic again).
 * Cycle order: STRAIGHT → TURN → AUTO, starting from whatever it currently shows.
 */
public final class SwitchControl {
    private SwitchControl() {}

    public static boolean isRail(net.minecraft.world.World mcWorld, BlockPos pos) {
        return World.get(mcWorld).getBlockEntity(new Vec3i(pos.getX(), pos.getY(), pos.getZ()), TileRailBase.class) != null;
    }

    /** a lever frame's points lever: force the switch at pos to straight (normal) or turnout (reverse); false = no switch there */
    public static boolean set(net.minecraft.world.World mcWorld, BlockPos pos, boolean reverse) {
        TileRailBase base = World.get(mcWorld).getBlockEntity(new Vec3i(pos.getX(), pos.getY(), pos.getZ()), TileRailBase.class);
        if (base == null) return false;
        TileRail sw = base instanceof TileRail tr ? tr : base.getParentTile();
        if (sw == null || sw.info == null || sw.info.settings.type != TrackItems.SWITCH) sw = base.findSwitchParent();
        if (sw == null || sw.info == null || sw.info.settings.type != TrackItems.SWITCH) return false;
        SwitchState want = reverse ? SwitchState.TURN : SwitchState.STRAIGHT;
        if (sw.isSwitchForced() && SwitchUtil.getSwitchState(sw) == want) return true;
        if (com.dogpound.railmap.safety.FaultLab.stuck(mcWorld, new BlockPos(sw.getPos().x, sw.getPos().y, sw.getPos().z))) return false;   // jammed points (fault lab)
        if (com.dogpound.railmap.weather.Weather.pointsFail(mcWorld, new BlockPos(sw.getPos().x, sw.getPos().y, sw.getPos().z)) != null) return false;   // #63/#65 frozen / heat-bound points
        if (com.dogpound.railmap.track.TrackHooks.throwVeto(mcWorld, new BlockPos(sw.getPos().x, sw.getPos().y, sw.getPos().z), want, true) != null) return false;   // #14 point machine refuses / failed
        sw.setSwitchForced(want);
        sw.markDirty();
        return true;
    }

    /** Returns a short status line for the player's action bar. */
    public static String cycle(net.minecraft.world.World mcWorld, BlockPos pos) {
        World world = World.get(mcWorld);
        Vec3i p = new Vec3i(pos.getX(), pos.getY(), pos.getZ());
        TileRailBase base = world.getBlockEntity(p, TileRailBase.class);
        if (base == null) return "No track there";
        TileRail sw = base instanceof TileRail tr ? tr : base.getParentTile();
        if (sw == null || sw.info == null || sw.info.settings.type != TrackItems.SWITCH) {
            // The clicked piece may be the diverging leg; its parent is the switch.
            sw = base.findSwitchParent();
            if (sw == null || sw.info == null || sw.info.settings.type != TrackItems.SWITCH) return "Not a switch";
        }
        if (com.dogpound.railmap.safety.FaultLab.stuck(mcWorld, new BlockPos(sw.getPos().x, sw.getPos().y, sw.getPos().z))) return "Points JAMMED - they won't move (fault lab)";
        String wxFail = com.dogpound.railmap.weather.Weather.pointsFail(mcWorld, new BlockPos(sw.getPos().x, sw.getPos().y, sw.getPos().z));   // #63/#65
        if (wxFail != null) return wxFail;
        SwitchState next;
        if (!sw.isSwitchForced()) {
            SwitchState cur = SwitchUtil.getSwitchState(sw);
            next = cur == SwitchState.STRAIGHT ? SwitchState.TURN : SwitchState.STRAIGHT;
        } else {
            SwitchState cur = SwitchUtil.getSwitchState(sw);
            next = cur == SwitchState.STRAIGHT ? SwitchState.TURN : SwitchState.NONE;
        }
        String pm = com.dogpound.railmap.track.TrackHooks.throwVeto(mcWorld, new BlockPos(sw.getPos().x, sw.getPos().y, sw.getPos().z), next, true);   // #14 point machine
        if (pm != null) return "Points won't move - " + pm;
        sw.setSwitchForced(next);
        sw.markDirty();
        return next == SwitchState.NONE ? "Switch: AUTO (redstone)" : "Switch: " + next.name();
    }
}
