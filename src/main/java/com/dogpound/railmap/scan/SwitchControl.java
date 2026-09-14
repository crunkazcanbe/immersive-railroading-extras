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
        SwitchState next;
        if (!sw.isSwitchForced()) {
            SwitchState cur = SwitchUtil.getSwitchState(sw);
            next = cur == SwitchState.STRAIGHT ? SwitchState.TURN : SwitchState.STRAIGHT;
        } else {
            SwitchState cur = SwitchUtil.getSwitchState(sw);
            next = cur == SwitchState.STRAIGHT ? SwitchState.TURN : SwitchState.NONE;
        }
        sw.setSwitchForced(next);
        sw.markDirty();
        return next == SwitchState.NONE ? "Switch: AUTO (redstone)" : "Switch: " + next.name();
    }
}
