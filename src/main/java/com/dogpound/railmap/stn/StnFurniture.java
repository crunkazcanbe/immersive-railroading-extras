package com.dogpound.railmap.stn;

import com.dogpound.railmap.block.TileFurniture;
import com.dogpound.railmap.depot.Mains;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * A station services office (Lost Property, Parcels): TileFurniture's real storages + shelves that show what is on
 * them, a 12 V control terminal, and a real mains supply (230 V class, depot.Mains - the counter terminal, label
 * printer and parcel scale draw it every second). No mains = screens dark and the buttons do nothing; the drawers and
 * shelves always work by hand.
 */
public abstract class StnFurniture extends TileFurniture {
    protected final Mains mains = new Mains();

    protected StnFurniture(String[] ids, String[] names, int[] sizes, int shelf) { super(ids, names, sizes, shelf); }

    public boolean live() { return term.powered() && mains.ok(); }
    protected static String who(EntityPlayer p) { return p == null ? "console" : p.getName(); }

    @Override
    public int controlValue(String id) {
        if (mechanical(id)) return ctl.get(id);
        if (id.equals("mainsl")) return live() ? 1 : 0;
        if (id.equals("pwr")) return term.powered() ? 1 : 0;
        return live() ? ctl.get(id) : 0;
    }

    @Override public String ptext(String key) { return live() ? ctl.text(key) : ""; }

    @Override
    protected final String press(String id, EntityPlayer who) {
        if (!mains.ok()) return "§cThe counter terminal has no mains (" + mains.what + ")";
        return act(id, who);
    }

    protected abstract String act(String id, EntityPlayer who);

    @Override
    protected void serverTick() {
        if (mains.tick(world, pos, term, wiredName(), term.powered() ? 4 : 0)) controlsChanged();
        work();
    }

    protected abstract void work();

    @Override public void gearInfo(java.util.Map<String, Object> m) { m.put("mains", mains.what); }

    @Override protected void writeExtra(NBTTagCompound t) { mains.write(t); save(t); }
    @Override protected void readExtra(NBTTagCompound t) { mains.read(t); load(t); }
    protected void save(NBTTagCompound t) {}
    protected void load(NBTTagCompound t) {}

    /** put a stack into an inventory (merging); what did not fit */
    public static ItemStack insert(IInventory inv, ItemStack s) {
        s = s.copy();
        for (int i = 0; i < inv.getSizeInventory() && !s.isEmpty(); i++) {
            ItemStack h = inv.getStackInSlot(i);
            if (h.isEmpty()) { inv.setInventorySlotContents(i, s); return ItemStack.EMPTY; }
            if (ItemStack.areItemsEqual(h, s) && ItemStack.areItemStackTagsEqual(h, s) && h.getCount() < h.getMaxStackSize()) {
                int n = Math.min(s.getCount(), h.getMaxStackSize() - h.getCount());
                h.grow(n); s.shrink(n);
                inv.markDirty();
            }
        }
        return s;
    }

    public static boolean fits(IInventory inv, ItemStack s) {
        for (int i = 0; i < inv.getSizeInventory(); i++) if (inv.getStackInSlot(i).isEmpty()) return true;
        return false;
    }
}
