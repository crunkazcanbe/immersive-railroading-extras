package com.dogpound.railmap.stnlife6;

import com.dogpound.railmap.depot.Mains;
import com.dogpound.railmap.safety.SafetyTile;
import net.minecraft.nbt.NBTTagCompound;

/**
 * A station-life machine on its own block: SafetyTile's 12 V control terminal + a real mains supply (depot.Mains, a
 * grid cable 120-480 V AC; 690 V+ burns it out). Lamps, screens and powered buttons need both; keys (k_), selectors
 * (s_) and mushrooms (m_) are mechanical and stay where they were put.
 */
public abstract class LifeTile extends SafetyTile {
    protected final Mains mains = new Mains();

    public boolean live() { return powered() && mains.ok(); }
    /** FE drawn from the mains every second while 12 V is on */
    protected long load() { return 4; }

    @Override
    public int controlValue(String id) {
        if (id.equals("pwr")) return powered() ? 1 : 0;
        if (id.equals("mainsl")) return live() ? 1 : 0;
        if (id.startsWith("k_") || id.startsWith("s_") || id.startsWith("m_")) return ctl.get(id);
        if (ctl.get("lamptest") == 1 && live()) return 1;
        return live() ? ctl.get(id) : 0;
    }

    @Override public String ptext(String key) { return live() ? ctl.text(key) : ""; }

    @Override
    protected final void serverTick() {
        if (mains.tick(world, pos, term, wiredName(), powered() ? load() : 0)) dirty();
        work();
    }

    protected void dirty() { controlsChanged(); }
    protected abstract void work();
    /** a powered button pressed with no supply: the message, else null */
    protected String dead() { return !powered() ? "§cNo 12 V control supply" : !mains.ok() ? "§cNo mains - " + mains.what : null; }

    @Override public void gearInfo(java.util.Map<String, Object> m) { m.put("mains", mains.what); }
    @Override protected final void save(NBTTagCompound t) { mains.write(t); write(t); }
    @Override protected final void load(NBTTagCompound t) { mains.read(t); read(t); }
    protected abstract void write(NBTTagCompound t);
    protected abstract void read(NBTTagCompound t);
}
