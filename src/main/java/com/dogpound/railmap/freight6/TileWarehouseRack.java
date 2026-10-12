package com.dogpound.railmap.freight6;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.wrapper.InvWrapper;

import java.util.function.Predicate;

/**
 * Warehouse Pallet Rack (batch 6 #28): a three-level selective pallet rack bay - orange beams, blue uprights with
 * footplates and column guards, wire-mesh decks, location labels and the aisle light. Real storage: three levels of
 * 27 slots (click a level to open it). Whatever is in it stands on the decks as pallets of goods. The Warehouse Office's
 * forklifts put away into and pick from every rack within 24 blocks of it. Mechanical - no power needed (the aisle light
 * and the location display want 12 V).
 */
public class TileWarehouseRack extends com.dogpound.railmap.depot.DepotTile {
    private boolean resync;

    public TileWarehouseRack() { super(new String[]{"bay1", "bay2", "bay3"}, new String[]{"Rack level 1 (floor)", "Rack level 2", "Rack level 3 (top)"}, new int[]{27, 27, 27}); }

    @Override public String wiredName() { return "Warehouse pallet rack"; }
    @Override public String gearState() { return String.format(java.util.Locale.ROOT, "%d / %d slots used, %d items", used(), 81, count()); }

    public InventoryBasic level(int k) { return store(k); }

    public int count() { int n = 0; for (int k = 0; k < 3; k++) for (int i = 0; i < 27; i++) n += store(k).getStackInSlot(i).getCount(); return n; }
    public int used() { int n = 0; for (int k = 0; k < 3; k++) for (int i = 0; i < 27; i++) if (!store(k).getStackInSlot(i).isEmpty()) n++; return n; }

    /** put away: fills the lowest level first; returns what did not fit */
    public ItemStack putAway(ItemStack s) {
        ItemStack rest = s;
        for (int k = 0; k < 3 && !rest.isEmpty(); k++) rest = Cars.put(new InvWrapper(store(k)), rest);
        return rest;
    }

    public int room(ItemStack s) { int n = 0; for (int k = 0; k < 3; k++) n += Cars.room(new InvWrapper(store(k)), s); return n; }

    /** pick up to n of one kind matching ok */
    public ItemStack pick(int n, Predicate<ItemStack> ok) {
        ItemStack got = ItemStack.EMPTY;
        for (int k = 2; k >= 0 && got.getCount() < n; k--) {
            final ItemStack like = got;
            ItemStack more = Cars.take(new InvWrapper(store(k)), n - got.getCount(), x -> ok.test(x) && (like.isEmpty() || ItemStack.areItemsEqual(x, like) && ItemStack.areItemStackTagsEqual(x, like)));
            if (more.isEmpty()) continue;
            if (got.isEmpty()) got = more; else got.grow(more.getCount());
        }
        return got;
    }

    public boolean has(Predicate<ItemStack> ok) {
        for (int k = 0; k < 3; k++) for (int i = 0; i < 27; i++) { ItemStack s = store(k).getStackInSlot(i); if (!s.isEmpty() && ok.test(s)) return true; }
        return false;
    }

    @Override public void markDirty() { super.markDirty(); resync = true; }

    @Override protected String press(String id, EntityPlayer who) { return null; }

    @Override
    protected void work() {
        if (age % 10 != 0) return;
        if (resync && world != null) { resync = false; net.minecraft.block.state.IBlockState bs = world.getBlockState(pos); world.notifyBlockUpdate(pos, bs, bs, 3); }
        if (age % 20 != 0) return;
        int n = Warehouse.racks(world, pos).indexOf(this);
        put("pwr", powered()); put("full", used() >= 81); put("lamp", powered());
        put("fillg", used() * 100 / 81);
        text("loc", n < 0 ? "A-00" : String.format(java.util.Locale.ROOT, "A-%02d", n + 1));
    }

    @Override protected boolean keepTextUnpowered() { return true; }
    @Override protected void saveExtra(NBTTagCompound t) {}
    @Override protected void loadExtra(NBTTagCompound t) {}
}
