package com.dogpound.railmap.stn;

import com.dogpound.railmap.depot.DepotTile;
import net.minecraft.entity.player.EntityPlayer;

/**
 * A station services panel / board: a 12 V control terminal for its logic and lamps (the WIRED page) AND a real mains
 * supply (230 V class: 120-480 V AC from an IR Extras grid cable or machine touching it, drawn every second through
 * depot.Mains) for its LED screens, printers and motors. No 12 V = dead; no mains = the screens and lamps go dark and
 * the buttons do nothing (the MAINS lamp shows why). Keys, selectors and E-stops (k_ / s_ / m_) stay where they are put.
 */
public abstract class StnPanel extends DepotTile {
    protected StnPanel() { super(); }
    protected StnPanel(String[] ids, String[] names, int[] sizes) { super(ids, names, sizes); }

    @Override protected boolean usesMains() { return true; }
    /** FE a second while powered (screens, logic); machines override with their real load */
    @Override protected long load() { return 3; }

    /** 12 V and mains both there */
    public boolean live() { return powered() && mainsOk(); }

    @Override
    public int controlValue(String id) {
        if (id.startsWith("k_") || id.startsWith("s_") || id.startsWith("m_")) return ctl.get(id);
        if (id.equals("mainsl")) return powered() && mainsOk() ? 1 : 0;
        if (id.equals("pwr")) return powered() ? 1 : 0;
        if (!screensOn()) return 0;
        return super.controlValue(id);
    }

    @Override public String ptext(String key) { return screensOn() ? super.ptext(key) : ""; }

    /** the screens / lamps / buttons have a supply (mains; the emergency power panel also runs off its own UPS) */
    protected boolean screensOn() { return mainsOk(); }

    @Override
    protected final String press(String id, EntityPlayer who) {
        if (!screensOn()) return "§cThe " + wiredName().toLowerCase(java.util.Locale.ROOT) + " has no mains (" + mains.what + ")";
        return act(id, who);
    }

    /** a powered push with 12 V and mains: the message (null = default) */
    protected abstract String act(String id, EntityPlayer who);
}
