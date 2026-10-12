package com.dogpound.railmap.freight6;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/** batch 6 #26-30's own network channel, so it takes no packet ids from the shared RailMap channel */
public final class F6Net {
    private F6Net() {}

    public static SimpleNetworkWrapper NET;

    static void init() {
        if (NET != null) return;
        NET = NetworkRegistry.INSTANCE.newSimpleChannel("irextras_f6");
        NET.registerMessage(PacketHoliday.Handler.class, PacketHoliday.class, 0, Side.CLIENT);
    }
}
