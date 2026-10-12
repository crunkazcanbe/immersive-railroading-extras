package com.dogpound.railmap.depot;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * The depot's spare-parts inventory (her ChatGPT list #53): whatever really sits in the bins of every Depot Stores
 * rack within reach. Job cards count and take from it; nothing is ever made up.
 */
public final class Stores {
    private Stores() {}

    public static final int REACH = 32;

    public static List<TileDepotStores> near(World w, BlockPos at) {
        List<TileDepotStores> o = new ArrayList<>();
        for (TileEntity t : w.loadedTileEntityList)
            if (t instanceof TileDepotStores s && !t.isInvalid() && t.getPos().distanceSq(at) <= REACH * REACH) o.add(s);
        return o;
    }

    public static int count(World w, BlockPos at, ItemSpare.Spare p) {
        int n = 0;
        for (TileDepotStores s : near(w, at)) n += s.count(p);
        return n;
    }

    /** every spare the job needs is there */
    public static String shortOf(World w, BlockPos at, int[] need) {
        StringBuilder b = new StringBuilder();
        for (ItemSpare.Spare p : ItemSpare.Spare.values()) {
            int n = need[p.ordinal()];
            if (n <= 0) continue;
            int have = count(w, at, p);
            if (have < n) b.append(b.length() > 0 ? ", " : "").append(n - have).append(" x ").append(p.label);
        }
        return b.toString();
    }

    /** take them out of the bins (call shortOf first) */
    public static void take(World w, BlockPos at, int[] need) {
        for (ItemSpare.Spare p : ItemSpare.Spare.values()) {
            int left = need[p.ordinal()];
            for (TileDepotStores s : near(w, at)) {
                if (left <= 0) break;
                left -= s.take(p, left);
            }
        }
    }

    /** count / take helpers over any inventory */
    public static int count(IInventory inv, ItemSpare.Spare p) {
        int n = 0;
        for (int i = 0; i < inv.getSizeInventory(); i++) if (ItemSpare.is(inv.getStackInSlot(i), p)) n += inv.getStackInSlot(i).getCount();
        return n;
    }

    public static int take(IInventory inv, ItemSpare.Spare p, int want) {
        int got = 0;
        for (int i = 0; i < inv.getSizeInventory() && got < want; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!ItemSpare.is(s, p)) continue;
            int t = Math.min(want - got, s.getCount());
            s.shrink(t);
            got += t;
            if (s.isEmpty()) inv.setInventorySlotContents(i, ItemStack.EMPTY);
            inv.markDirty();
        }
        return got;
    }

    /** the first matching stack (bogies: read its serial), or empty */
    public static ItemStack first(IInventory inv, ItemSpare.Spare p) {
        for (int i = 0; i < inv.getSizeInventory(); i++) if (ItemSpare.is(inv.getStackInSlot(i), p)) return inv.getStackInSlot(i);
        return ItemStack.EMPTY;
    }

    /** put a stack in the first free slot; false if full */
    public static boolean put(IInventory inv, ItemStack s) {
        for (int i = 0; i < inv.getSizeInventory(); i++)
            if (inv.getStackInSlot(i).isEmpty()) { inv.setInventorySlotContents(i, s); inv.markDirty(); return true; }
        return false;
    }
}
