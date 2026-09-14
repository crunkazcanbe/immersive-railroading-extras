package com.dogpound.railmap.client;

import com.dogpound.railmap.graph.TrainNode;
import net.minecraft.client.Minecraft;

import java.util.Collections;
import java.util.List;

/** Client cache of the last {@code PacketTrains}. Every board, panel and handheld map reads it. */
public final class ClientTrains {
    private static int dim = Integer.MIN_VALUE;
    private static List<TrainNode> trains = Collections.emptyList();
    private static long receivedAt;

    private ClientTrains() {}

    public static void accept(int d, List<TrainNode> t) {
        dim = d;
        trains = t;
        receivedAt = System.currentTimeMillis();
    }

    /** Trains for the player's current dimension; empty when the feed is stale (>5 s). */
    public static List<TrainNode> get() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.world.provider.getDimension() != dim) return Collections.emptyList();
        if (System.currentTimeMillis() - receivedAt > 5000) return Collections.emptyList();
        return trains;
    }

    public static boolean live() {
        return System.currentTimeMillis() - receivedAt <= 5000;
    }

    public static TrainNode byId(int id) {
        for (TrainNode t : get()) if (t.id == id) return t;
        return null;
    }
}
