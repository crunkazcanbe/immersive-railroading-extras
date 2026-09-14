package com.dogpound.railmap.auto;

import com.dogpound.railmap.block.TileArrivalsBoard;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Arrivals boards register here so a train pulling in can chime the right ones. */
public final class ArrivalEvents {
    private static final Map<Integer, List<TileArrivalsBoard>> BOARDS = new HashMap<>();

    private ArrivalEvents() {}

    public static void add(int dim, TileArrivalsBoard b) {
        List<TileArrivalsBoard> l = BOARDS.computeIfAbsent(dim, d -> new ArrayList<>());
        if (!l.contains(b)) l.add(b);
    }

    public static void remove(int dim, TileArrivalsBoard b) {
        List<TileArrivalsBoard> l = BOARDS.get(dim);
        if (l != null) l.remove(b);
    }

    public static void clear() {
        BOARDS.clear();
    }

    static void arrived(World world, long station, String train) {
        List<TileArrivalsBoard> l = BOARDS.get(world.provider.getDimension());
        if (l == null) return;
        for (TileArrivalsBoard b : new ArrayList<>(l)) {
            if (!b.isInvalid() && b.station() == station) b.chime(train);
        }
    }
}
