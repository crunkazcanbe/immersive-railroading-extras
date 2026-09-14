package com.dogpound.railmap.scan;

import cam72cam.immersiverailroading.library.Augment;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.mod.block.BlockEntity;
import cam72cam.mod.math.Vec3i;
import com.dogpound.railmap.graph.StopNode;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns IR track augments into stop markers. Augments sit on the gag tiles of a piece
 * ({@code TileRailBase.getAugment()}), so a gag counts only if its parent piece was walked.
 */
final class StopScanner {
    private final Map<Long, Integer> walkedRails;
    final List<StopNode> found = new ArrayList<>();

    StopScanner(Map<Long, Integer> walkedRails) {
        this.walkedRails = walkedRails;
    }

    void accept(BlockEntity be) {
        if (!(be instanceof TileRailBase rail)) return;
        Augment aug = rail.getAugment();
        if (aug == null) return;
        Vec3i parent = rail.getParent();
        if (parent == null || !walkedRails.containsKey(parent.toLong())) return;
        Vec3i p = rail.getPos();
        found.add(new StopNode(new BlockPos(p.x, p.y, p.z), aug.name(), kindOf(aug)));
    }

    private static StopNode.Kind kindOf(Augment a) {
        switch (a) {
            case ITEM_LOADER:
            case FLUID_LOADER:
            case WATER_TROUGH: return StopNode.Kind.LOADER;
            case ITEM_UNLOADER:
            case FLUID_UNLOADER: return StopNode.Kind.UNLOADER;
            case LOCO_CONTROL: return StopNode.Kind.CONTROL;
            case DETECTOR: return StopNode.Kind.DETECTOR;
            default: return StopNode.Kind.OTHER;
        }
    }
}
