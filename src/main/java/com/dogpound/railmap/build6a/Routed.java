package com.dogpound.railmap.build6a;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.function.Predicate;

/**
 * One item handler over a machine's several stores (hoppers, pipes, conveyors): what comes in goes to the store that
 * takes it (rails to the rail racks, sleepers to the sleeper magazine...), and only the output stores (spoil, despatch)
 * can be pulled from.
 */
public final class Routed implements IItemHandler {
    private final IInventory[] inv;
    private final Predicate<ItemStack>[] accepts;
    private final boolean[] out;

    @SafeVarargs
    public Routed(IInventory[] inv, boolean[] out, Predicate<ItemStack>... accepts) { this.inv = inv; this.out = out; this.accepts = accepts; }

    private int store(int slot) { for (int k = 0; k < inv.length; k++) { if (slot < inv[k].getSizeInventory()) return k; slot -= inv[k].getSizeInventory(); } return -1; }
    private int local(int slot) { for (IInventory i : inv) { if (slot < i.getSizeInventory()) return slot; slot -= i.getSizeInventory(); } return -1; }

    @Override public int getSlots() { int n = 0; for (IInventory i : inv) n += i.getSizeInventory(); return n; }
    @Override public ItemStack getStackInSlot(int slot) { int k = store(slot); return k < 0 ? ItemStack.EMPTY : inv[k].getStackInSlot(local(slot)); }
    @Override public int getSlotLimit(int slot) { return 64; }

    @Override
    public ItemStack insertItem(int slot, ItemStack s, boolean sim) {
        int k = store(slot);
        if (k < 0 || s.isEmpty() || accepts[k] == null || !accepts[k].test(s)) return s;
        int l = local(slot);
        ItemStack h = inv[k].getStackInSlot(l);
        int room;
        if (h.isEmpty()) room = Math.min(64, s.getMaxStackSize());
        else if (ItemStack.areItemsEqual(h, s) && ItemStack.areItemStackTagsEqual(h, s)) room = h.getMaxStackSize() - h.getCount();
        else return s;
        int n = Math.min(room, s.getCount());
        if (n <= 0) return s;
        if (!sim) {
            if (h.isEmpty()) { ItemStack c = s.copy(); c.setCount(n); inv[k].setInventorySlotContents(l, c); }
            else h.grow(n);
            inv[k].markDirty();
        }
        ItemStack r = s.copy();
        r.shrink(n);
        return r;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean sim) {
        int k = store(slot);
        if (k < 0 || !out[k]) return ItemStack.EMPTY;
        int l = local(slot);
        ItemStack h = inv[k].getStackInSlot(l);
        if (h.isEmpty()) return ItemStack.EMPTY;
        int n = Math.min(amount, h.getCount());
        ItemStack r = h.copy();
        r.setCount(n);
        if (!sim) { h.shrink(n); if (h.isEmpty()) inv[k].setInventorySlotContents(l, ItemStack.EMPTY); inv[k].markDirty(); }
        return r;
    }
}
