package com.dogpound.railmap.ride;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Batch 5 #5 + #10's own network channel (ropeways + cinematic camera), so they take no packet ids from the shared
 * RailMap channel other agents also add to. Registered from RideRegistry's block registration (preInit).
 */
public final class RideNet {
    private RideNet() {}

    public static SimpleNetworkWrapper NET;

    static void init() {
        if (NET != null) return;
        NET = NetworkRegistry.INSTANCE.newSimpleChannel("irextras_ride");
        NET.registerMessage(PacketRide.Handler.class, PacketRide.class, 0, Side.CLIENT);
        NET.registerMessage(com.dogpound.railmap.cine.PacketCine.Handler.class, com.dogpound.railmap.cine.PacketCine.class, 1, Side.CLIENT);
    }
}
