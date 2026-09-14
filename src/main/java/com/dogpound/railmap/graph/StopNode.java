package com.dogpound.railmap.graph;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/**
 * A place trains stop or are serviced. IR has no named "station" block; its stops are track
 * augments (loaders, detectors, loco-control) on gag tiles, so that is what we map — plus
 * {@link Kind#STATION}: a spot on the track a player named from the board.
 */
public final class StopNode {
    public enum Kind { LOADER, UNLOADER, CONTROL, DETECTOR, OTHER, STATION }

    public final BlockPos pos;
    /** Player-given name if any, else the augment's name. */
    public final String name;
    public final Kind kind;
    /** True when a player named it (renaming/removing is allowed). */
    public final boolean named;

    public StopNode(BlockPos pos, String name, Kind kind) {
        this(pos, name, kind, false);
    }

    public StopNode(BlockPos pos, String name, Kind kind, boolean named) {
        this.pos = pos;
        this.name = name == null ? "" : name;
        this.kind = kind;
        this.named = named;
    }

    public StopNode withName(String n) {
        return new StopNode(pos, n, kind, true);
    }

    NBTTagCompound toNBT() {
        NBTTagCompound t = new NBTTagCompound();
        t.setLong("p", pos.toLong());
        t.setString("n", name);
        t.setByte("k", (byte) kind.ordinal());
        if (named) t.setBoolean("u", true);
        return t;
    }

    static StopNode fromNBT(NBTTagCompound t) {
        Kind[] all = Kind.values();
        int k = t.getByte("k");
        return new StopNode(BlockPos.fromLong(t.getLong("p")), t.getString("n"),
                k >= 0 && k < all.length ? all[k] : Kind.OTHER, t.getBoolean("u"));
    }
}
