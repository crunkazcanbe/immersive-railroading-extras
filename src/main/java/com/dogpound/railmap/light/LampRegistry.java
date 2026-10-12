package com.dogpound.railmap.light;

import net.minecraft.tileentity.TileEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/** client: the luminaires loaded in the world, so the halo renderer never walks the whole tile list every frame */
public final class LampRegistry {
    private LampRegistry() {}

    private static final Set<TileEntity> LAMPS = Collections.newSetFromMap(new WeakHashMap<>());

    public static synchronized void add(TileEntity t) { if (t instanceof ILamp) LAMPS.add(t); }
    public static synchronized void remove(TileEntity t) { LAMPS.remove(t); }
    public static synchronized List<TileEntity> all() { return new ArrayList<>(LAMPS); }
    public static synchronized void clear() { LAMPS.clear(); }
}
