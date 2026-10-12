package com.dogpound.railmap.cine;

import com.dogpound.railmap.RailMap;
import net.minecraftforge.common.config.Config;

/**
 * The cinematic train camera (batch 5 #10), config/irextras_cine.cfg. The camera is purely client-side: it never moves
 * your player (spectator-style view entity only), and it can be switched off here per client.
 */
@Config(modid = RailMap.MODID, name = "irextras_cine", category = "cine")
public final class CineConfig {
    private CineConfig() {}

    @Config.Comment("Photo mode / cinematic camera available (F6 by default). Off = the key does nothing")
    public static boolean photoMode = true;
    @Config.Comment("On a multiplayer server the free camera can't pass through solid blocks (no peeking into bases / caves). Single player: always free")
    public static boolean collideOnServers = true;
    @Config.Comment("How far (blocks) the camera may go from your player (chunks further away aren't loaded on your client anyway)")
    @Config.RangeInt(min = 16, max = 512)
    public static int maxDistance = 160;
    @Config.Comment("Free camera speed, blocks per second (Ctrl = x4)")
    @Config.RangeInt(min = 1, max = 60)
    public static int flySpeed = 8;
    @Config.Comment("Trackside camera auto-cut: a tripod is used while the train is within this many blocks of it")
    @Config.RangeInt(min = 16, max = 256)
    public static int tripodRange = 96;
    @Config.Comment("Shortest trackside / director shot before the next cut, seconds")
    @Config.RangeInt(min = 1, max = 20)
    public static int minShotSeconds = 3;
    @Config.Comment("Depth-of-field blur (screen copy + masked blur, no shaders). Off if your GPU struggles")
    public static boolean depthOfField = true;
}
