package com.dogpound.railmap.freight6;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Freight;
import cam72cam.immersiverailroading.entity.FreightTank;
import cam72cam.immersiverailroading.entity.Locomotive;
import com.dogpound.railmap.steam.Stock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.function.Predicate;

/** the IR wagons standing at a batch 6 freight machine, and moving goods in / out of them */
public final class Cars {
    private Cars() {}

    /** nearest standing goods wagon (not a tank car, not a loco) within reach */
    public static Freight goods(World w, BlockPos p, double reach) {
        return Stock.nearest(w, p, reach, Freight.class, s -> !(s instanceof FreightTank) && !(s instanceof Locomotive) && s.cargoItems != null && Stock.standing(s));
    }

    /** nearest standing tank car (not a loco / tender) within reach */
    public static FreightTank tank(World w, BlockPos p, double reach) {
        return Stock.nearest(w, p, reach, FreightTank.class, s -> !(s instanceof Locomotive) && !Stock.steamStock(s) && Stock.standing(s));
    }

    public static IItemHandler inv(Freight f) { return f == null || f.cargoItems == null ? null : f.cargoItems.internal; }

    public static String name(EntityRollingStock s) { return s == null ? "" : Stock.name(s); }

    /** the wagon's type name for CargoRules (its definition name) */
    public static String type(EntityRollingStock s) {
        try { return s.getDefinition().name(); } catch (RuntimeException e) { return ""; }
    }

    /** take up to n items matching ok out of h; returns them as one stack (one kind at a time) */
    public static ItemStack take(IItemHandler h, int n, Predicate<ItemStack> ok) {
        if (h == null || n <= 0) return ItemStack.EMPTY;
        ItemStack got = ItemStack.EMPTY;
        for (int i = 0; i < h.getSlots() && got.getCount() < n; i++) {
            ItemStack peek = h.getStackInSlot(i);
            if (peek.isEmpty() || !ok.test(peek) || !got.isEmpty() && !ItemHandlerHelper.canItemStacksStack(got, peek)) continue;
            ItemStack t = h.extractItem(i, n - got.getCount(), false);
            if (t.isEmpty()) continue;
            if (got.isEmpty()) got = t; else got.grow(t.getCount());
        }
        return got;
    }

    /** put s into h; returns what did not fit */
    public static ItemStack put(IItemHandler h, ItemStack s) { return h == null ? s : ItemHandlerHelper.insertItemStacked(h, s, false); }

    /** room for s in h (simulated) */
    public static int room(IItemHandler h, ItemStack s) {
        if (h == null || s.isEmpty()) return 0;
        ItemStack probe = s.copy();
        probe.setCount(Math.max(1, s.getMaxStackSize()) * h.getSlots());
        return probe.getCount() - ItemHandlerHelper.insertItemStacked(h, probe, true).getCount();
    }
}
