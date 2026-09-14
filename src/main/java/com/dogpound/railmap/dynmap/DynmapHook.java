package com.dogpound.railmap.dynmap;

import com.dogpound.railmap.graph.RailNetwork;
import com.dogpound.railmap.graph.TrainNode;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * What the rest of the mod is allowed to know about Dynmap: nothing.
 * <p>
 * This interface mentions only Minecraft and RailMap types, so it loads on any install. The
 * implementation ({@link DynmapBridge}) is the only class that touches {@code org.dynmap.*},
 * and it is class-loaded exactly once — inside a guarded {@code new} — after
 * {@code Loader.isModLoaded("dynmap")} says it is safe.
 * <p>
 * <b>Why this exists.</b> {@code RailMap.dynmap} used to be typed {@code DynmapBridge}. Forge's
 * {@code ProxyInjector} enumerates every declared field on the {@code @Mod} class to find
 * {@code @SidedProxy}, and enumerating fields forces each field's type to load. On an install
 * without Dynmap that threw {@code NoClassDefFoundError: DynmapBridge} during mod construction
 * and took the whole game down before RailMap had run a line of its own code. Typing the field
 * as this interface keeps the Dynmap classes off the field table entirely.
 */
public interface DynmapHook {

    /** True once Dynmap's marker API is up and a train layer exists to draw into. */
    boolean wantsTrains();

    /** Publish one display's scanned network as track chains, signal and stop markers. */
    void network(World world, BlockPos origin, RailNetwork net);

    /** Move the live train markers. */
    void trains(World world, List<TrainNode> trains);

    /** Drop the markers belonging to one display (it was broken or replaced). */
    void remove(World world, BlockPos origin);

    /** Server stopping: delete the marker layers. */
    void shutdown();
}
