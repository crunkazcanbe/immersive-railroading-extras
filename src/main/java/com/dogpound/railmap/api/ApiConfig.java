package com.dogpound.railmap.api;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.common.config.Config;

/**
 * The railway platform API (her ChatGPT list #96-105), config/irextras_api.cfg. Safe by default: nothing here changes an
 * old world by itself. The JSON outbox is on (read-only files beside the world); the HTTP endpoint is OFF until a port and
 * a token are set; the program watchdog only ever stops a runaway program, never the railway.
 */
@Config(modid = RailMap.MODID, name = "irextras_api", category = "api")
public final class ApiConfig {
    private ApiConfig() {}

    // ---- 98 programmable automation: the watchdog -----------------------------------------------------------------
    @Config.Comment("Most rule evaluations + routine calls one program pass may make before the watchdog trips (stops the program, raises an alarm)")
    @Config.RangeInt(min = 50, max = 100000)
    public static int programMaxSteps = 4000;

    @Config.Comment("Most milliseconds one program pass may take before the watchdog trips")
    @Config.RangeInt(min = 1, max = 200)
    public static int programMaxMillis = 8;

    @Config.Comment("Deepest routine-calls-routine nesting (CALL inside CALL ...) before the watchdog trips")
    @Config.RangeInt(min = 1, max = 64)
    public static int programMaxDepth = 8;

    @Config.Comment("A rule that throws an error this many passes in a row is switched off (with an alarm) instead of failing forever")
    @Config.RangeInt(min = 1, max = 100)
    public static int ruleErrorLimit = 5;

    // ---- 101 train orders --------------------------------------------------------------------------------------------
    @Config.Comment("A train-order step that waits (LOAD / UNLOAD / WAIT CHANNEL) longer than this raises an alarm and moves on, minutes (0 = wait forever)")
    @Config.RangeInt(min = 0, max = 1440)
    public static int orderStepTimeoutMinutes = 30;

    // ---- 102 audit log -----------------------------------------------------------------------------------------------
    @Config.Comment("Audit log entries kept per world (oldest dropped)")
    @Config.RangeInt(min = 50, max = 20000)
    public static int auditKeep = 2000;

    // ---- 104 external telemetry export -------------------------------------------------------------------------------
    @Config.Comment("Write read-only JSON snapshots to <world>/pride-web/irextras/ (trains, lines, alarms, stats, telemetry) - the Pride web-hook outbox")
    public static boolean exportJson = true;

    @Config.Comment("Seconds between JSON snapshots")
    @Config.RangeInt(min = 10, max = 3600)
    public static int exportSeconds = 60;

    @Config.Comment("Read-only HTTP endpoint port (0 = off). Serves the same JSON at /api/v1/<name>; needs exportToken")
    @Config.RangeInt(min = 0, max = 65535)
    public static int httpPort = 0;

    @Config.Comment("Address the HTTP endpoint listens on (127.0.0.1 = this machine only; 0.0.0.0 = the network - put it behind your proxy)")
    public static String httpBind = "127.0.0.1";

    @Config.Comment("Bearer token every HTTP request must send (Authorization: Bearer <token>). Empty = the endpoint refuses to start")
    public static String exportToken = "";

    @Config.Comment("HTTP requests allowed per client address per minute")
    @Config.RangeInt(min = 1, max = 6000)
    public static int httpPerMinute = 60;

    // ---- 105 scenario API ----------------------------------------------------------------------------------------------
    @Config.Comment("Let the scenario API change passenger demand (station population) for a running objective; restored when it ends")
    public static boolean scenarioDemand = true;
}
