package com.dogpound.railmap.cctv;

import com.dogpound.railmap.depot.Mains;
import com.dogpound.railmap.safety.SafetyTile;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

/**
 * Shared plumbing of every CCTV block: the 12 V control terminal (SafetyTile: no 12 V = dark / NO SIGNAL), the real
 * supply (mains through Mains: a grid cable touching it, 120-480 V AC; cameras may take PoE from their NVR instead),
 * and the video network it is on (set by the NVR's scan of the wire / radio bus; stale after 15 s = off the network).
 */
public abstract class CctvTile extends SafetyTile {
    protected final Mains mains = new Mains();
    /** video network id (the lowest NVR position of the linked NVRs), 0 = none */
    protected long net;
    private long netAt = -10000;

    public long net() { return world != null && world.getTotalWorldTime() - netAt < 300 ? net : 0; }

    /** an NVR scan reached this device */
    public void joinNet(long n) {
        boolean ch = net != n;
        net = n;
        netAt = world.getTotalWorldTime();
        if (ch) controlsChanged();
    }

    /** watts it draws from its supply */
    protected abstract int watts();

    /** FE per second for a load in watts (at least 1) */
    protected static long fe(int watts) { return Math.max(1, Math.round(watts / com.dogpound.railmap.grid.Elec.W_PER_FE_T * 20)); }

    /** mains ok (or not required) - call every tick */
    protected boolean mainsOk() {
        if (!CctvConfig.needSupply) return true;
        mains.tick(world, pos, term, wiredName(), fe(watts()));
        return mains.ok();
    }

    /** the 12 V terminal is live and the real supply is there */
    public abstract boolean running();

    public EnumFacing facing() { return controlFacing(); }

    @Override
    protected void save(NBTTagCompound t) { t.setLong("net", net()); mains.write(t); saveMore(t); }

    @Override
    protected void load(NBTTagCompound t) {
        net = t.getLong("net");
        mains.read(t);
        loadMore(t);
    }

    @Override
    public void onLoad() { if (world != null && world.isRemote) netAt = Long.MAX_VALUE / 4; }

    protected abstract void saveMore(NBTTagCompound t);
    protected abstract void loadMore(NBTTagCompound t);
}
