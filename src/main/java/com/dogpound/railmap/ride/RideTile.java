package com.dogpound.railmap.ride;

import com.dogpound.railmap.infra.InfraTile;
import com.dogpound.railmap.infra.Supply;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Shared base of the ropeway gear: InfraTile's working controls, 12 V terminal and real supply, plus which line it
 * belongs to. Gear that isn't on a line yet joins the nearest line within 32 blocks that has no such part (the Haul Rope
 * Spool can relink it by hand).
 */
public abstract class RideTile extends InfraTile {
    public int lineId;

    protected RideTile(Supply s) { super(s); }

    /** this tile's line, or null; links itself to a near line when free */
    protected RideData.Line line() {
        RideData d = RideData.get(world);
        RideData.Line l = lineId == 0 ? null : d.line(lineId);
        if (l != null && owns(l)) return l;
        if (l != null) { lineId = 0; controlsChanged(); }
        if (age % 40 != 7) return null;
        RideData.Line n = d.near(pos, 32);
        if (n == null || !free(n)) return null;
        claim(n);
        lineId = n.id;
        d.changed();
        controlsChanged();
        return n;
    }

    /** is this tile the line's part of its kind */
    protected abstract boolean owns(RideData.Line l);
    /** can the line take this part */
    protected abstract boolean free(RideData.Line l);
    protected abstract void claim(RideData.Line l);

    /** the Haul Rope Spool links this part to line l by hand */
    public String linkTo(RideData.Line l) {
        RideData d = RideData.get(world);
        RideData.Line old = d.line(lineId);
        if (old != null && old != l) release(old);
        claim(l);
        lineId = l.id;
        d.changed();
        controlsChanged();
        return "§a" + wiredName() + " linked to " + l.label();
    }

    protected void release(RideData.Line l) {}

    @Override protected void store(NBTTagCompound t) { t.setInteger("line", lineId); storeMore(t); }
    @Override protected void restore(NBTTagCompound t) { lineId = t.getInteger("line"); restoreMore(t); }

    protected void storeMore(NBTTagCompound t) {}
    protected void restoreMore(NBTTagCompound t) {}

    protected static String f0(double v) { return String.format(java.util.Locale.ROOT, "%.0f", v); }
}
