package com.dogpound.railmap.web;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.common.config.Config;

/**
 * Live train map for the Pride website (her ChatGPT list #7), config/irextras_trainmap.cfg. Everything is OFF until she
 * switches it on: nothing here changes an old world. Read only - the map can never change the railway.
 */
@Config(modid = RailMap.MODID, name = "irextras_trainmap", category = "trainmap")
public final class TrainMapConfig {
    private TrainMapConfig() {}

    @Config.Comment("Port of the built-in train map web server (0 = off). It serves the map page at / and the live feed at /live.json + /tracks.json")
    @Config.RangeInt(min = 0, max = 65535)
    public static int port = 0;

    @Config.Comment("Address the map server listens on. 127.0.0.1 = this machine only (put your reverse proxy in front); 0.0.0.0 = the whole network")
    public static String bind = "127.0.0.1";

    @Config.Comment("Also write trainmap-live.json + trainmap-tracks.json into <world>/pride-web/irextras/ (the Pride web-hook outbox), so the website can serve them as plain files")
    public static boolean outbox = false;

    @Config.Comment("Seconds between live feed updates (trains, signals, alarms, stations)")
    @Config.RangeInt(min = 1, max = 60)
    public static int liveSeconds = 2;

    @Config.Comment("Seconds between track map rebuilds (the rail graph is walked from every dispatcher board / console and every named station)")
    @Config.RangeInt(min = 10, max = 3600)
    public static int trackSeconds = 60;

    @Config.Comment("Dimension the map shows")
    public static int dimension = 0;

    @Config.Comment("What the map publishes - remove a word to keep it off the website: tracks, trains, lines, signals, stations, crowds, alarms, cctv")
    public static String[] publish = {"tracks", "trains", "lines", "signals", "stations", "crowds", "alarms", "cctv"};

    @Config.Comment("Replace player names in train names and alarm texts with 'a player' (privacy)")
    public static boolean hidePlayerNames = true;

    @Config.Comment("Requests per minute one address may make (the page polls every liveSeconds)")
    @Config.RangeInt(min = 10, max = 6000)
    public static int perMinute = 240;

    @Config.Comment("Access-Control-Allow-Origin sent with the feed (\"*\" lets a page on any site read it; empty = no CORS header)")
    public static String corsOrigin = "*";

    static boolean allows(String what) {
        for (String s : publish) if (s != null && s.trim().equalsIgnoreCase(what)) return true;
        return false;
    }
}
