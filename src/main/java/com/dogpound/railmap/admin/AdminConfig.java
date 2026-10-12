package com.dogpound.railmap.admin;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.common.config.Config;

/**
 * Multiplayer + server administration (her ChatGPT list #111-120), config/irextras_admin.cfg. Safe by default: an old
 * world has no territory claims, so nothing is refused until a company claims track or sets roles; locks only ever
 * matter with two or more people at desks; unauthorised running only raises an alarm.
 */
@Config(modid = RailMap.MODID, name = "irextras_admin", category = "admin")
public final class AdminConfig {
    private AdminConfig() {}

    // ---- 111 access control -------------------------------------------------------------------------------------
    @Config.Comment({"Railway access control: signals, dispatching, schedules, depots and power on a company's territory need a role",
            "(or a delegated grant, or an access agreement). Also asks Forge PermissionAPI (PridePerms) for irextras.rail.* nodes.",
            "Unclaimed railway stays open to everyone."})
    public static boolean accessControl = true;

    @Config.Comment("A company's territory also covers this many blocks around each of its stations (and its lines' stations); 0 = explicit claims only")
    @Config.RangeInt(min = 0, max = 512)
    public static int stationTerritory = 64;

    // ---- 113 shared infrastructure -------------------------------------------------------------------------------
    @Config.Comment("HARSH: a driverless train running on another company's line with no access agreement is held at its next stop (off = alarm only)")
    public static boolean holdUnauthorised = false;

    @Config.Comment("Days a lessee / operator may stay in arrears before the lease or access agreement is terminated")
    @Config.RangeInt(min = 1, max = 30)
    public static int arrearsDays = 3;

    // ---- 115 command locking --------------------------------------------------------------------------------------
    @Config.Comment("Dispatcher command locking: the first person to work a control desk in an area takes control of it; other desks are refused until a handover")
    public static boolean commandLocking = true;

    @Config.Comment("Seconds without a command before a desk's control of an area lapses (the area becomes free)")
    @Config.RangeInt(min = 15, max = 3600)
    public static int lockIdleSeconds = 180;

    @Config.Comment("Size (blocks) of the automatic control areas where no named area is drawn")
    @Config.RangeInt(min = 64, max = 4096)
    public static int lockCellBlocks = 512;

    // ---- 117 admin --------------------------------------------------------------------------------------------------
    @Config.Comment("Longest an admin may pause a subsystem before it resumes by itself, minutes")
    @Config.RangeInt(min = 1, max = 1440)
    public static int pauseMaxMinutes = 30;

    // ---- 119 backups --------------------------------------------------------------------------------------------------
    @Config.Comment("Minutes of play between automatic railway snapshots (0 = only by hand)")
    @Config.RangeInt(min = 0, max = 1440)
    public static int snapshotMinutes = 30;

    @Config.Comment("Snapshots kept (oldest deleted)")
    @Config.RangeInt(min = 1, max = 200)
    public static int snapshotsKept = 24;

    // ---- 120 load-aware simulation ----------------------------------------------------------------------------------
    @Config.Comment("Load-aware simulation: background work (weather cells, station catchments) in areas with no player and no moving train runs less often. Occupancy, signalling, protection, train movement and power accounting are never slowed")
    public static boolean loadAware = true;

    @Config.Comment("A 64-block cell is ACTIVE within this many blocks of a player (half that of a moving train)")
    @Config.RangeInt(min = 32, max = 1024)
    public static int activeRadius = 192;

    @Config.Comment("Inactive cells update this many times less often (doubled while the server is over its 50 ms tick budget)")
    @Config.RangeInt(min = 1, max = 32)
    public static int inactiveSlowdown = 6;

    @Config.Comment("Time every IR Extras tick handler (profiler, /irextras admin perf) and allow pausing subsystems. Off = handlers untouched")
    public static boolean profiler = true;
}
