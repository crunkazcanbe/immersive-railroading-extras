package com.dogpound.railmap.pax;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.common.config.Config;

/** Visible passengers (config/irextras_pax.cfg). They are only a picture of the simulated numbers: turning them off changes nothing else. */
@Config(modid = RailMap.MODID, name = "irextras_pax", category = "pax")
public final class PaxConfig {
    private PaxConfig() {}

    @Config.Comment("Show simulated passengers as people walking about stations near players (off = numbers only, as before)")
    public static boolean enabled = true;

    @Config.Comment("Visible people per simulated passenger (1 = everyone waiting is shown, up to the caps; 0.5 = half)")
    @Config.RangeDouble(min = 0, max = 1)
    public static double scale = 1.0;

    @Config.Comment("Most people shown at one station")
    @Config.RangeInt(min = 0, max = 200)
    public static int maxPerStation = 40;

    @Config.Comment("Most people shown around one player (all the stations near them together)")
    @Config.RangeInt(min = 0, max = 500)
    public static int maxPerPlayer = 120;

    @Config.Comment("Most people in one dimension")
    @Config.RangeInt(min = 0, max = 2000)
    public static int maxPerWorld = 300;

    @Config.Comment("People appear at stations within this many blocks of a player; further away they stay numbers only")
    @Config.RangeInt(min = 16, max = 160)
    public static int viewDistance = 64;

    @Config.Comment("Walk graph: blocks scanned around a station's stop position (horizontal radius)")
    @Config.RangeInt(min = 8, max = 64)
    public static int graphRadius = 28;

    @Config.Comment("Walk graph: blocks scanned above and below the stop position")
    @Config.RangeInt(min = 2, max = 24)
    public static int graphHeight = 8;

    @Config.Comment("Minutes between rescans of a station's walk graph while players are near it")
    @Config.RangeInt(min = 1, max = 60)
    public static int rebuildMinutes = 5;

    @Config.Comment("Most people shown getting off one train")
    @Config.RangeInt(min = 0, max = 64)
    public static int alightMax = 16;

    @Config.Comment("Share of new arrivals who are a parent with one or two children")
    @Config.RangeDouble(min = 0, max = 0.5)
    public static double families = 0.12;

    @Config.Comment("Share of people who use a wheelchair")
    @Config.RangeDouble(min = 0, max = 0.3)
    public static double wheelchairs = 0.04;

    @Config.Comment("People sit on station benches and shelters while they wait")
    public static boolean benches = true;

    @Config.Comment("People react to delays and cancellations (look at the boards, go back to the concourse) and leave stations being evacuated")
    public static boolean reactions = true;
}
