package com.dogpound.railmap.track;

import com.dogpound.railmap.safety.SafetyTile;

/**
 * Wired track infrastructure (point machines, structure boards, crossing predictors, tunnel systems): the safety
 * gear's plumbing (working controls, 12 V terminal on the WIRED page, save + sync) plus the track package's live
 * registry and a console row in the asset register, refreshed every 5 s.
 */
public abstract class TrackGear extends SafetyTile {
    @Override public void onLoad() { super.onLoad(); if (world != null && !world.isRemote) TrackHooks.add(this); }
    @Override public void invalidate() { super.invalidate(); if (world != null && !world.isRemote) TrackHooks.remove(this); }
    @Override public void onChunkUnload() { super.onChunkUnload(); if (world != null && !world.isRemote) TrackHooks.remove(this); }

    @Override
    protected final void serverTick() {
        tickGear();
        if (age % 100 == 3) { String[] r = assetRow(); if (r != null) TrackData.get(world).asset(pos, r); }
    }

    protected abstract void tickGear();

    /** the console's asset register row (kind tag first), or null for none */
    protected String[] assetRow() { return null; }

    protected String xyz() { return pos.getX() + "," + pos.getY() + "," + pos.getZ(); }
}
