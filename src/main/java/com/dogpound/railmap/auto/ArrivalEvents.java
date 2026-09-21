package com.dogpound.railmap.auto;

import com.dogpound.railmap.block.TileArrivalsBoard;
import com.dogpound.railmap.block.TilePaSpeaker;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Arrivals boards register here so a train pulling in can chime the right ones. */
public final class ArrivalEvents {
    private static final Map<Integer, List<TileArrivalsBoard>> BOARDS = new HashMap<>();
    private static final Map<Integer, List<TilePaSpeaker>> SPEAKERS = new HashMap<>();

    private ArrivalEvents() {}

    public static void add(int dim, TileArrivalsBoard b) {
        List<TileArrivalsBoard> l = BOARDS.computeIfAbsent(dim, d -> new ArrayList<>());
        if (!l.contains(b)) l.add(b);
    }

    public static void remove(int dim, TileArrivalsBoard b) {
        List<TileArrivalsBoard> l = BOARDS.get(dim);
        if (l != null) l.remove(b);
    }

    public static void addSpeaker(int dim, TilePaSpeaker s) {
        List<TilePaSpeaker> l = SPEAKERS.computeIfAbsent(dim, d -> new ArrayList<>());
        if (!l.contains(s)) l.add(s);
    }

    public static void removeSpeaker(int dim, TilePaSpeaker s) {
        List<TilePaSpeaker> l = SPEAKERS.get(dim);
        if (l != null) l.remove(s);
    }

    public static void clear() {
        BOARDS.clear();
        SPEAKERS.clear();
    }

    static void arrived(World world, long station, String train) {
        int dim = world.provider.getDimension();
        List<TileArrivalsBoard> l = BOARDS.get(dim);
        if (l != null) {
            for (TileArrivalsBoard b : new ArrayList<>(l)) {
                if (!b.isInvalid() && b.station() == station) b.chime(train);
            }
        }
        List<TilePaSpeaker> sp = SPEAKERS.get(dim);
        if (sp != null) {
            for (TilePaSpeaker s : new ArrayList<>(sp)) {
                if (!s.isInvalid() && s.station() == station) s.announceArrival(train);
            }
        }
    }
}
