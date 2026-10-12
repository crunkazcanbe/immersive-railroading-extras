package com.dogpound.railmap.ride;

import net.minecraft.entity.player.EntityPlayer;

import java.util.Locale;

/**
 * The tension weight: a concrete counterweight hanging in its frame from the tension carriage, keeping the haul rope
 * taut whatever the load and temperature (the rope sags 2 % instead of 3.5 % of a span; without it the line is held to
 * 2 m/s). Its monitor (12 V): carriage position scale, rope tension, LIMIT HIGH / LOW lamps - and a lost monitor signal
 * trips the line, like a carriage at its limit.
 */
public class TileRideTension extends RideTile {
    public TileRideTension() { super(null); }

    @Override public String wiredName() { return "Ropeway tension weight " + pos.getX() + "," + pos.getZ(); }
    @Override protected boolean owns(RideData.Line l) { return l.tension == pos.toLong(); }
    @Override protected boolean free(RideData.Line l) { return l.tension == 0 && l.kind != RideGeom.FUNICULAR; }
    @Override protected void claim(RideData.Line l) { l.tension = pos.toLong(); }
    @Override protected void release(RideData.Line l) { l.tension = 0; }

    @Override public String gearState() { RideData.Line l = line(); return (powered() ? "monitor ok" : "monitor dark - no 12 V") + (l == null ? " · no line" : String.format(Locale.ROOT, " · %s %.0f kN carriage %.0f %%", l.label(), l.tensionKn, l.carriage)); }

    @Override
    protected void serverTick() {
        RideData.Line l = line();
        if (l != null) { l.tensionSeen = world.getTotalWorldTime(); l.tensionPowered = powered(); }
        if (age % 10 != 4) return;
        boolean on = powered();
        put("l_pwr", on);
        if (l == null) { text("d_kn", on ? "NO LINE" : ""); text("d_pos", ""); put("g_pos", 0); return; }
        double c = Math.max(0, l.carriage);
        put("l_hi", on && c > 90);
        put("l_lo", on && c < 10);
        put("l_ok", on && c >= 10 && c <= 90);
        put("g_pos", (int) Math.round(Math.min(100, c)));
        text("d_kn", String.format(Locale.ROOT, "%.0f KN", l.tensionKn));
        text("d_pos", String.format(Locale.ROOT, "CARR %.0f%%", c));
    }

    /** the weight's drawn height in the frame (0..1), for the renderer */
    public float carriage() { RideData.Line l = line(); return l == null ? 0.5f : (float) Math.max(0, Math.min(1, l.carriage / 100)); }

    @Override
    public String controlPress(String id, EntityPlayer p) {
        if (id.equals("test")) return lampTest();
        return null;
    }
}
