package com.dogpound.railmap.cctv;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.common.config.Config;

/**
 * CCTV (requested feature),
 * config/irextras_cctv.cfg. Nothing changes in an old world until CCTV blocks are placed. The client part (live feeds)
 * is per player: turn it down here if a big pack struggles.
 */
@Config(modid = RailMap.MODID, name = "irextras_cctv", category = "cctv")
public final class CctvConfig {
    private CctvConfig() {}

    // ---- live video (client) -------------------------------------------------------------------------------------
    @Config.Comment("Render real live video into the monitors (a second world render per feed into a framebuffer). Off = the server snapshot fallback only")
    public static boolean liveVideo = true;
    @Config.Comment("Frames per second for each live feed (round-robin across all feeds on screen)")
    @Config.RangeInt(min = 1, max = 30)
    public static int liveFps = 10;
    @Config.Comment("Global budget: at most this many camera renders per game frame")
    @Config.RangeInt(min = 1, max = 8)
    public static int liveBudget = 1;
    @Config.Comment("Resolution (pixels, square) of one live feed")
    @Config.RangeInt(min = 32, max = 512)
    public static int liveRes = 128;
    @Config.Comment("Monitors further than this (blocks) from you show no live picture (they stay lit with their overlay)")
    @Config.RangeInt(min = 8, max = 96)
    public static int liveRange = 32;
    @Config.Comment("Fog distance (blocks) of a camera view: cameras see less far than you do")
    @Config.RangeInt(min = 16, max = 256)
    public static int cameraFogBlocks = 64;
    @Config.Comment("Seconds between snapshot requests for a camera whose chunk your client hasn't loaded (fallback picture from the server)")
    @Config.RangeInt(min = 1, max = 60)
    public static int snapshotSeconds = 2;

    // ---- server analytics + recording -----------------------------------------------------------------------------
    @Config.Comment("Width of a server frame (software ray-cast snapshot used for recording, motion detection, tamper and far-away feeds)")
    @Config.RangeInt(min = 16, max = 128)
    public static int snapW = 48;
    @Config.RangeInt(min = 12, max = 96)
    public static int snapH = 36;
    @Config.Comment("How far (blocks) the server ray-cast sees")
    @Config.RangeInt(min = 8, max = 96)
    public static int snapRange = 48;
    @Config.Comment("Ticks between server frames of one camera (motion detection rate)")
    @Config.RangeInt(min = 10, max = 400)
    public static int analyticsTicks = 40;
    @Config.Comment("Server frames (ray-casts) per tick across all cameras")
    @Config.RangeInt(min = 1, max = 16)
    public static int framesPerTick = 2;
    @Config.Comment("Seconds between recorded frames of one camera on an NVR")
    @Config.RangeInt(min = 1, max = 60)
    public static int recordSeconds = 2;
    @Config.Comment("Recorded frames each terabyte of drive holds (a 9-bay NVR full of 8 TB drives = 72 x this)")
    @Config.RangeInt(min = 50, max = 5000)
    public static int framesPerTB = 600;

    // ---- realism ------------------------------------------------------------------------------------------------
    @Config.Comment("Cameras must reach an NVR / PoE switch over a wire run (or radio) to be seen; monitors too. Off = any monitor shows any working camera")
    public static boolean requireNetwork = true;
    @Config.Comment("Cameras need their real supply (PoE 48 V from an NVR switch or 230 V mains); monitors, NVRs and desks need mains. Off = 12 V is enough")
    public static boolean needSupply = true;
    @Config.Comment("PoE budget (W) of an NVR's switch")
    @Config.RangeInt(min = 30, max = 2000)
    public static int poeBudget = 370;
    @Config.Comment("Motion detection raises alarms (Alarm Manager) on armed networks")
    public static boolean motionAlarms = true;
}
