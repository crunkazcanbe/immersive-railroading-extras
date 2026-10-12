package com.dogpound.railmap.freight6;

import com.dogpound.railmap.stn.StnUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** the rail-served warehouse's shared lookups (batch 6 #28): racks and docks in a fixed order, the stock list */
public final class Warehouse {
    private Warehouse() {}

    public static final int MAX_DOCKS = 4;

    static <T extends net.minecraft.tileentity.TileEntity> List<T> sorted(List<T> l) {
        l.sort(Comparator.comparingInt((T s) -> s.getPos().getX()).thenComparingInt(s -> s.getPos().getZ()).thenComparingInt(s -> s.getPos().getY()));
        return l;
    }

    public static List<TileWarehouseRack> racks(World w, BlockPos at) { return sorted(StnUtil.near(w, TileWarehouseRack.class, at, 24)); }

    public static List<TileWarehouseDock> docks(World w, BlockPos at) {
        List<TileWarehouseDock> l = sorted(StnUtil.near(w, TileWarehouseDock.class, at, 32));
        return l.size() > MAX_DOCKS ? l.subList(0, MAX_DOCKS) : l;
    }

    /** what the racks hold, by item name: name -> {count, slots} */
    public static Map<String, int[]> stock(World w, BlockPos at) {
        Map<String, int[]> m = new TreeMap<>();
        for (TileWarehouseRack r : racks(w, at))
            for (int k = 0; k < 3; k++)
                for (int i = 0; i < 27; i++) {
                    ItemStack s = r.level(k).getStackInSlot(i);
                    if (s.isEmpty()) continue;
                    int[] c = m.computeIfAbsent(s.getDisplayName(), x -> new int[2]);
                    c[0] += s.getCount(); c[1]++;
                }
        return m;
    }
}
