package com.dogpound.railmap.track;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

import java.util.Locale;

/**
 * Tunnel portal name plate (requested feature): the dressed stone
 * headwall block with the cast tunnel name, which end it is, and the tunnel's length measured to the portal at the
 * other end. Name it with a Name Tag (rename one in an anvil, right-click the plate). Exit signs, distance markers and
 * the tunnel control panel's rescue instructions use the portals.
 */
public class TileTunnelPortal extends PlainTile implements TrackBlock.Named {
    private String name = "";

    public String name() { return name.isEmpty() ? "TUNNEL " + Math.floorMod(pos.getX() * 7 + pos.getZ() * 3, 100) : name; }
    @Override public String setName(String n) { name = n.trim().toUpperCase(Locale.ROOT); markDirty(); controlsChanged(); return "§aTunnel portal named " + name(); }

    /** the nearest other portal (the other end of this tunnel), or null */
    TileTunnelPortal other() {
        TileTunnelPortal best = null;
        double bd = 4096.0 * 4096;
        for (TileTunnelPortal p : TrackHooks.live(world, TileTunnelPortal.class)) {
            if (p == this) continue;
            double d = p.getPos().distanceSq(pos);
            if (d < bd) { bd = d; best = p; }
        }
        return best;
    }

    @Override
    protected void serverTick() {
        if (age % 100 != 1) return;
        TileTunnelPortal o = other();
        EnumFacing f = controlFacing();
        text("name", name());
        text("end", f.getName().toUpperCase(Locale.ROOT) + " PORTAL");
        text("len", o == null ? "LENGTH --" : "LENGTH " + Math.round(Math.sqrt(o.getPos().distanceSq(pos))) + " M");
    }

    @Override public String controlPress(String id, EntityPlayer who) { return "§7" + name() + " - rename with a Name Tag"; }

    @Override protected void save(NBTTagCompound t) { t.setString("name", name); }
    @Override protected void load(NBTTagCompound t) { name = t.getString("name"); }
}
