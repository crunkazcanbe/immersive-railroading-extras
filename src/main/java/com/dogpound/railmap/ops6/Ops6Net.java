package com.dogpound.railmap.ops6;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/** batch 6 E's own network channel (registered from Ops6Registry's block registration, preInit): no shared packet ids */
public final class Ops6Net {
    private Ops6Net() {}

    public static SimpleNetworkWrapper NET;

    static void init() {
        if (NET != null) return;
        NET = NetworkRegistry.INSTANCE.newSimpleChannel("irextras_ops6");
        NET.registerMessage(PacketOps6.Handler.class, PacketOps6.class, 0, Side.CLIENT);
        NET.registerMessage(PacketOps6.Handler.class, PacketOps6.class, 1, Side.SERVER);
    }
}
