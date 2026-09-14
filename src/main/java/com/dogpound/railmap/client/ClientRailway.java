package com.dogpound.railmap.client;

import com.dogpound.railmap.network.PacketRailState;

/** The latest lines, driverless trains and routes, as the board last heard them. */
public final class ClientRailway {
    private static PacketRailState state = new PacketRailState();

    private ClientRailway() {}

    public static void accept(PacketRailState s) {
        state = s;
    }

    public static PacketRailState get() {
        return state;
    }

    /** The driverless-train entry for this entity, or null if a person drives it. */
    public static PacketRailState.TrainInfo train(int entityId) {
        for (PacketRailState.TrainInfo t : state.trains) if (t.entityId == entityId) return t;
        return null;
    }
}
