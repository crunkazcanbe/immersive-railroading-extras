package com.dogpound.railmap.block;

import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

/**
 * One tile of a wall screen. Panels with the same facing that form a solid rectangle merge
 * into one screen (WebDisplays style, no wand needed): every panel works out the rectangle it
 * is in, and the one at the viewer's bottom-left corner is the {@link #controller()} that
 * scans, holds the data and renders the whole surface.
 * <p>
 * The rectangle is recomputed lazily every second; placing/breaking a neighbour shows up on
 * the next recompute. ponytail: greedy growth from this cell — an L-shaped arrangement can make
 * cells disagree about the rectangle; keep walls rectangular.
 */
public class TileDisplayPanel extends TileRailDisplay {
    public static final int MAX_SIZE = 16;

    private BlockPos rectOrigin;   // controller position
    private int cols = 1, rows = 1;
    private long rectTime = Long.MIN_VALUE;

    public EnumFacing facing() {
        IBlockState s = world.getBlockState(pos);
        return s.getBlock() instanceof BlockDisplayPanel ? s.getValue(BlockDisplayPanel.FACING) : EnumFacing.NORTH;
    }

    /** Viewer's "right" along the wall. */
    public static EnumFacing right(EnumFacing facing) {
        return facing.rotateYCCW();
    }

    public void markRectDirty() {
        rectTime = Long.MIN_VALUE;
    }

    private void ensureRect() {
        long now = world.getTotalWorldTime();
        if (rectTime != Long.MIN_VALUE && now - rectTime < 20) return;
        rectTime = now;
        EnumFacing f = facing();
        EnumFacing r = right(f);
        // Grow [u0,u1]×[v0,v1] (u along r, v up) while every cell of the new row/column is a like panel.
        int u0 = 0, u1 = 0, v0 = 0, v1 = 0;
        boolean grew = true;
        while (grew) {
            grew = false;
            if (u1 - u0 + 1 < MAX_SIZE && column(f, r, u0 - 1, v0, v1)) { u0--; grew = true; }
            if (u1 - u0 + 1 < MAX_SIZE && column(f, r, u1 + 1, v0, v1)) { u1++; grew = true; }
            if (v1 - v0 + 1 < MAX_SIZE && row(f, r, v0 - 1, u0, u1)) { v0--; grew = true; }
            if (v1 - v0 + 1 < MAX_SIZE && row(f, r, v1 + 1, u0, u1)) { v1++; grew = true; }
        }
        cols = u1 - u0 + 1;
        rows = v1 - v0 + 1;
        rectOrigin = pos.offset(r, u0).up(v0);
    }

    private boolean column(EnumFacing f, EnumFacing r, int u, int v0, int v1) {
        for (int v = v0; v <= v1; v++) if (!isLikePanel(pos.offset(r, u).up(v), f)) return false;
        return true;
    }

    private boolean row(EnumFacing f, EnumFacing r, int v, int u0, int u1) {
        for (int u = u0; u <= u1; u++) if (!isLikePanel(pos.offset(r, u).up(v), f)) return false;
        return true;
    }

    private boolean isLikePanel(BlockPos p, EnumFacing f) {
        if (!world.isBlockLoaded(p)) return false;
        IBlockState s = world.getBlockState(p);
        return s.getBlock() instanceof BlockDisplayPanel && s.getValue(BlockDisplayPanel.FACING) == f;
    }

    public int cols() { ensureRect(); return cols; }
    public int rows() { ensureRect(); return rows; }
    public BlockPos rectOrigin() { ensureRect(); return rectOrigin; }

    @Override
    public TileRailDisplay controller() {
        if (world == null) return this;
        ensureRect();
        if (rectOrigin.equals(pos)) return this;
        TileEntity te = world.getTileEntity(rectOrigin);
        return te instanceof TileDisplayPanel p ? p : this;
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        if (world == null) return super.getRenderBoundingBox();
        ensureRect();
        if (!rectOrigin.equals(pos)) return new AxisAlignedBB(pos); // non-controllers draw nothing
        EnumFacing r = right(facing());
        BlockPos far = pos.offset(r, cols - 1).up(rows - 1);
        return new AxisAlignedBB(pos).union(new AxisAlignedBB(far)).grow(0.5);
    }
}
