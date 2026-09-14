package com.dogpound.railmap.graph;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/** A trackside signal and the aspect it is currently showing. */
public final class SignalNode {
    public enum Aspect {
        RED, YELLOW, GREEN, UNKNOWN;

        /** RED is most restrictive; used when one signal head has several groups. */
        public Aspect mostRestrictive(Aspect o) {
            return ordinal() <= o.ordinal() ? this : o;
        }
    }

    public final BlockPos pos;
    public final Aspect aspect;
    /** Mod id the signal came from (e.g. "landofsignals"). */
    public final String source;
    /** The mod's own state string, shown on hover so a mis-classified aspect is still readable. */
    public final String rawState;

    public SignalNode(BlockPos pos, Aspect aspect, String source, String rawState) {
        this.pos = pos;
        this.aspect = aspect;
        this.source = source;
        this.rawState = rawState == null ? "" : rawState;
    }

    NBTTagCompound toNBT() {
        NBTTagCompound t = new NBTTagCompound();
        t.setLong("p", pos.toLong());
        t.setByte("a", (byte) aspect.ordinal());
        t.setString("s", source);
        t.setString("r", rawState);
        return t;
    }

    static SignalNode fromNBT(NBTTagCompound t) {
        Aspect[] all = Aspect.values();
        int a = t.getByte("a");
        return new SignalNode(BlockPos.fromLong(t.getLong("p")),
                a >= 0 && a < all.length ? all[a] : Aspect.UNKNOWN,
                t.getString("s"), t.getString("r"));
    }
}
