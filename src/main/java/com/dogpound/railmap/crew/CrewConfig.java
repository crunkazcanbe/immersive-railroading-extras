package com.dogpound.railmap.crew;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.common.config.Config;

/**
 * Train crew NPCs and ticket inspectors (batch 5 #7 + #9), config/irextras_crew.cfg. Safe by default: nothing here
 * holds or charges anyone in an old world until crew are hired at a Crew Office and rostered.
 */
@Config(modid = RailMap.MODID, name = "irextras_crew", category = "crew")
public final class CrewConfig {
    private CrewConfig() {}

    // ---- the people you see -------------------------------------------------------------------------------------------
    @Config.Comment("Show crew (drivers, guards, ticket inspectors) as people in uniform near players (off = they still work, unseen)")
    public static boolean npcs = true;

    @Config.Comment("Most crew people shown in one dimension")
    @Config.RangeInt(min = 0, max = 200)
    public static int maxNpcs = 24;

    @Config.Comment("Crew are shown within this many blocks of a player")
    @Config.RangeInt(min = 16, max = 160)
    public static int viewDistance = 72;

    // ---- crewed services -------------------------------------------------------------------------------------------------
    @Config.Comment("A driverless train on an ATTENDED line whose driver is a depot (NPC) driver only leaves once that driver is in the cab (they walk from the Crew Office). Off by default")
    public static boolean crewRequired = false;

    @Config.Comment("A train with a guard on duty waits for the guard's door check, whistle and right-away hand signal before leaving (never longer than guardMaxSeconds)")
    public static boolean guardRightAway = true;

    @Config.Comment("Longest a guard's dispatch may hold a train past its dwell (seconds)")
    @Config.RangeInt(min = 2, max = 60)
    public static int guardMaxSeconds = 8;

    @Config.Comment("Least rest (game hours) between two shifts for rostered crew; they are not signed on again before it")
    @Config.RangeInt(min = 0, max = 24)
    public static int restHours = 11;

    @Config.Comment("HARSH: tired crew (long duty, short rest) are slow to start away and now and then make a mistake (a late start or an overrun) - off by default")
    public static boolean fatigueEffects = false;

    @Config.Comment("Walking speed of crew between the office and their train (blocks per second)")
    @Config.RangeDouble(min = 0.5, max = 6)
    public static double walkSpeed = 2.2;

    // ---- revenue protection ----------------------------------------------------------------------------------------------
    @Config.Comment("Scale on the passenger model's fare evasion share (3 % at gated stations, 15 % ungated) for how many people an inspector finds without a ticket")
    @Config.RangeDouble(min = 0, max = 4)
    public static double dodgerScale = 1.0;

    @Config.Comment("Passengers an inspector checks per minute on a train or platform")
    @Config.RangeInt(min = 1, max = 120)
    public static int checksPerMinute = 12;

    @Config.Comment("Inspectors check players too (a valid ticket or pass for the journey)")
    public static boolean checkPlayers = true;

    @Config.Comment("No ticket: 0 = a friendly warning only, 1 = a warning the first time then a penalty fare, 2 = always a penalty fare. Never hostile; a player who can't pay is warned")
    @Config.RangeInt(min = 0, max = 2)
    public static int penaltyMode = 1;

    @Config.Comment("Penalty fare (Pride coins, cents) - gentle by default")
    @Config.RangeInt(min = 0, max = 100000)
    public static int penaltyCents = 250;

    @Config.Comment("A station or line an inspector worked in the last game day has this much less fare evasion (0.5 = half)")
    @Config.RangeDouble(min = 0.1, max = 1)
    public static double deterrence = 0.6;
}
