package com.dogpound.railmap.sound;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.common.config.Config;

/**
 * IR Extras sound settings (config/irextras_sound.cfg, also in Mods > IR Extras > Config). Client side: every
 * irextras sound passes through {@link com.dogpound.railmap.sound.client.SoundMixer}, which applies the group volume
 * or silences a group that is switched off. Groups: controls, switchgear, signalling, alarms, cab, train, station,
 * voice, machines, ui.
 */
@Config(modid = RailMap.MODID, name = "irextras_sound", category = "sound")
public final class SoundConfig {
    private SoundConfig() {}

    @Config.Comment("Master volume for every IR Extras sound, %")
    @Config.RangeInt(min = 0, max = 200)
    public static int master = 100;

    @Config.Comment("Panel buttons, selectors, keys, levers, e-stops")
    public static Group controls = new Group();
    @Config.Comment("Breakers, contactors, relays, transformer hum, arcs, fuses")
    public static Group switchgear = new Group();
    @Config.Comment("Point machines, signal box levers, block bells, level crossings")
    public static Group signalling = new Group();
    @Config.Comment("Annunciators, klaxons, fire bells, teleprinters, telephones")
    public static Group alarms = new Group();
    @Config.Comment("Locomotive cab: horn, whistle, bell, brakes, notches, switches, radio, AWS")
    public static Group cab = new Group();
    @Config.Comment("Trains running: rail joints, flange squeal, train doors")
    public static Group train = new Group();
    @Config.Comment("Stations: PA chimes, gates, lifts, wind and rain under the canopy")
    public static Group station = new Group();
    @Config.Comment("Spoken station announcements (PA voice)")
    public static Group voice = new Group();
    @Config.Comment("Factory and depot machines: crushers, conveyors, furnaces, pumps, fans, lathes")
    public static Group machines = new Group();
    @Config.Comment("Screens, keypads, beeps, office sounds")
    public static Group ui = new Group();

    @Config.Comment("Looping machine sounds play only within this many blocks")
    @Config.RangeInt(min = 8, max = 64)
    public static int loopRange = 24;

    @Config.Comment("At most this many looping sounds at once (the nearest win). Lower = lighter on the sound engine")
    @Config.RangeInt(min = 1, max = 48)
    public static int maxLoops = 14;

    @Config.Comment("PA voice: cori = British female, lj = American female")
    public static String voiceName = "cori";

    @Config.Comment("PA voice: spell out words the voice library doesn't know (unusual station names) letter by letter. Off = skip them")
    public static boolean spellUnknownWords = false;

    @Config.Comment("Station ambience: wind and rain under station canopies (needs a PA speaker to mark the station)")
    public static boolean stationAmbience = true;

    @Config.Comment("Train running sounds: rail joint clack on every train near you, flange squeal on tight curves")
    public static boolean trainRunning = true;

    public static final class Group {
        @Config.Comment("Play this group at all")
        public boolean enabled = true;
        @Config.Comment("Volume, %")
        @Config.RangeInt(min = 0, max = 200)
        public int volume = 100;
    }

    /** the group an irextras sound id belongs to (dotted pack ids by prefix, older plain ids by name) */
    public static Group groupOf(String path) {
        int dot = path.indexOf('.');
        String g = dot > 0 ? path.substring(0, dot) : legacyGroup(path);
        switch (g) {
            case "ctl": return controls;
            case "grid": return switchgear;
            case "signal": return signalling;
            case "alarm": return alarms;
            case "cab": return cab;
            case "train": return train;
            case "station": return station;
            case "voice": return voice;
            case "machine": case "track": return machines;
            default: return ui;
        }
    }

    private static String legacyGroup(String p) {
        if (p.startsWith("ctl_") || p.startsWith("button") || p.startsWith("estop") || p.startsWith("selector") || p.startsWith("key_") || p.startsWith("lever")) return "ctl";
        if (p.startsWith("door") || p.startsWith("psd") || p.startsWith("air_")) return "train";
        if (p.startsWith("hum") || p.startsWith("breaker") || p.startsWith("relay") || p.startsWith("contactor") || p.startsWith("fuse") || p.startsWith("arc") || p.startsWith("tap_") || p.startsWith("cable") || p.startsWith("cap_") || p.startsWith("whine") || p.startsWith("fan_") || p.startsWith("wind_") || p.startsWith("engine") || p.startsWith("diesel") || p.startsWith("loop_inverter") || p.startsWith("loop_dcdc")) return "grid";
        if (p.startsWith("alarm") || p.startsWith("buzzer") || p.startsWith("annunciator") || p.startsWith("klaxon") || p.startsWith("fire") || p.startsWith("phone") || p.startsWith("printer") || p.startsWith("ack")) return "alarm";
        if (p.startsWith("crossing") || p.startsWith("block_bell") || p.startsWith("point")) return "signal";
        if (p.startsWith("pa_") || p.startsWith("gate")) return "station";
        if (p.startsWith("loop_")) return "machine";
        return "ui";
    }
}
