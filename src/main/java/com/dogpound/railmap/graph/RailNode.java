package com.dogpound.railmap.graph;

import net.minecraft.util.math.BlockPos;

/**
 * One IR track piece (== one {@code TileRail}, the tile the gag blocks point back to).
 * <p>
 * {@link #points} is the centre-line polyline in world coordinates, already down-sampled
 * to at most {@link #MAX_POINTS} vertices: straights keep two, curves keep nine (8 segments).
 * That keeps the whole network small enough to ride in a tile-entity update packet.
 */
public final class RailNode {
    public static final int MAX_POINTS = 9;

    public enum Kind { STRAIGHT, CURVE, SWITCH, SLOPE, CROSSING, TABLE }

    /** Mirrors IR's SwitchState, as bytes so it packs into one array. */
    public static final byte SWITCH_NONE = 0, SWITCH_STRAIGHT = 1, SWITCH_TURN = 2;

    public final int id;
    public final BlockPos pos;
    public final Kind kind;
    /** IR placement yaw in degrees (IR's own convention; only used for the hover readout). */
    public final float yaw;
    /** IR settings.length: blocks for straights, radius for turns/turntables. */
    public final int length;
    /** Gauge in millimetres. */
    public final int gaugeMm;
    /** Current switch state (on SWITCH nodes and on the diverging leg that belongs to them). */
    public final byte switchState;
    /** For a diverging (turn) leg: id of the SWITCH node it belongs to, else -1. */
    public final int switchId;
    /** Flat [x0,y0,z0, x1,y1,z1, ...] world-space centre-line, never empty. */
    public final float[] points;

    public RailNode(int id, BlockPos pos, Kind kind, float yaw, int length, int gaugeMm,
                    byte switchState, int switchId, float[] points) {
        this.id = id;
        this.pos = pos;
        this.kind = kind;
        this.yaw = yaw;
        this.length = length;
        this.gaugeMm = gaugeMm;
        this.switchState = switchState;
        this.switchId = switchId;
        this.points = points;
    }

    public int pointCount() {
        return points.length / 3;
    }

    /** Diverging leg of a switch: drawn highlighted together with its parent. */
    public boolean isSwitchLeg() {
        return switchId >= 0;
    }
}
