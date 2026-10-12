package com.dogpound.railmap.ohle;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.util.*;

/** loaded tiles of one kind per dimension (server side), for the once-a-second passes */
public final class Reg<T extends TileEntity> {
    private final Map<Integer, Map<Long, T>> m = new HashMap<>();

    public void add(T t) {
        if (t.getWorld() == null || t.getWorld().isRemote) return;
        m.computeIfAbsent(t.getWorld().provider.getDimension(), k -> new LinkedHashMap<>()).put(t.getPos().toLong(), t);
    }

    public void remove(T t) {
        if (t.getWorld() == null || t.getWorld().isRemote) return;
        Map<Long, T> d = m.get(t.getWorld().provider.getDimension());
        if (d != null && d.get(t.getPos().toLong()) == t) d.remove(t.getPos().toLong());
    }

    public Collection<T> all(World w) {
        Map<Long, T> d = m.get(w.provider.getDimension());
        if (d == null) return Collections.emptyList();
        d.values().removeIf(TileEntity::isInvalid);
        return new ArrayList<>(d.values());
    }
}
