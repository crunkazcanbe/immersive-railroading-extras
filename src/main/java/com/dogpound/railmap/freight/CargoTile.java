package com.dogpound.railmap.freight;

import com.dogpound.railmap.depot.DepotTile;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Cargo care machines: a 12 V control terminal AND a real mains supply (120-480 V AC from an IR Extras grid cable or
 * machine touching it, drawn every second as load() FE through depot.Mains). No mains = screens and lamps dark, buttons
 * refused (the MAINS lamp tells why); keys, selectors and E-stops (k_ / s_ / m_) stay where they were put.
 */
abstract class CargoTile extends DepotTile {
    CargoTile(String[] ids, String[] names, int[] sizes) { super(ids, names, sizes); }
    CargoTile() { super(); }

    @Override protected boolean usesMains() { return true; }
    boolean live() { return powered() && mainsOk(); }

    @Override
    public int controlValue(String id) {
        if (id.startsWith("k_") || id.startsWith("s_") || id.startsWith("m_")) return ctl.get(id);
        if (id.equals("mainsl")) return live() ? 1 : 0;
        if (id.equals("pwr")) return powered() ? 1 : 0;
        if (!mainsOk()) return 0;
        return super.controlValue(id);
    }

    @Override public String ptext(String key) { return mainsOk() ? super.ptext(key) : ""; }

    @Override
    protected final String press(String id, EntityPlayer who) {
        if (!mainsOk()) return "§cThe " + wiredName().toLowerCase(java.util.Locale.ROOT) + " has no mains (" + mains.what + ")";
        return act(id, who);
    }

    protected abstract String act(String id, EntityPlayer who);
}
