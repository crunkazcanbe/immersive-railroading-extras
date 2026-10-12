package com.dogpound.railmap.track;

import net.minecraft.entity.player.EntityPlayer;

/**
 * Tunnel distance marker (requested feature): the white enamel plate on the tunnel wall giving the distance in metres from the
 * portal and to the nearest way out - measured from where the portals and cross-passages really are, so it stays right
 * however long the tunnel gets.
 */
public class TileTunnelMarker extends PlainTile {
    @Override
    protected void serverTick() {
        if (age % 100 != 2) return;
        TileTunnelPortal near = null;
        double nd = Double.MAX_VALUE, xd = Double.MAX_VALUE;
        for (TileTunnelPortal p : TrackHooks.live(world, TileTunnelPortal.class)) { double d = p.getPos().distanceSq(pos); if (d < nd) { nd = d; near = p; } }
        for (TileCrossPassage x : TrackHooks.live(world, TileCrossPassage.class)) { double d = x.getPos().distanceSq(pos); if (d < xd) xd = d; }
        double exit = Math.min(nd, xd);
        text("km", near == null ? "--.---" : String.format(java.util.Locale.ROOT, "%.3f", Math.sqrt(nd) / 1000.0));
        text("from", near == null ? "NO PORTAL" : near.name());
        text("exit", exit == Double.MAX_VALUE ? "EXIT --" : "EXIT " + Math.round(Math.sqrt(exit)) + " M");
    }

    @Override public String controlPress(String id, EntityPlayer who) { return "§7" + ctl.text("km") + " km from " + ctl.text("from") + " · " + ctl.text("exit"); }
}
