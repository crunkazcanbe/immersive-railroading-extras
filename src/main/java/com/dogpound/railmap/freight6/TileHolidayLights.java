package com.dogpound.railmap.freight6;

/**
 * Festive String Lights (batch 6 #30): a canopy fascia garland with a 12 V LED festoon draped in swags along it,
 * bows and a hanging star / bat. Colours follow the controller's season (red-green-gold-white for Christmas,
 * orange-purple-green for Halloween) in its pattern. The garland is a decoration; the LEDs need 12 V + a controller.
 */
public class TileHolidayLights extends TileFestive {
    public TileHolidayLights() { super(new String[0], new String[0], new int[0]); }

    @Override public String wiredName() { return "Festive string lights"; }
    @Override protected int ownSeason() { return Season.NONE; }
}
