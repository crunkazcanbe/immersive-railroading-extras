package com.dogpound.railmap.steam;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;

import java.util.Collections;
import java.util.List;

/**
 * Coal Receiving Pit + Skip (batch 5 #2): the ground hopper at the foot of a Coaling Tower. Wagons / hoppers / pipes /
 * your hands tip coal through its grating (a 54-slot pit, any side takes items); the tower's skip hoist loads from it.
 * Placed beside a Coaling Tower it FORMS the coaling plant (the tower's FORMED lamp). The pit's level indicator (and its
 * own "wants coal" on the industry board) needs the 12 V terminal; the grating and pit themselves are just steel.
 */
public class TileCoalPit extends com.dogpound.railmap.depot.DepotTile implements com.dogpound.railmap.industry.IndustryLink.Endpoint {
    private final InvWrapper items;

    public TileCoalPit() {
        super(new String[]{"pit"}, new String[]{"Coal receiving pit"}, new int[]{54});
        items = new InvWrapper(store(0)) {
            @Override public ItemStack insertItem(int slot, ItemStack s, boolean sim) { return isCoal(s) ? super.insertItem(slot, s, sim) : s; }
        };
    }

    static boolean isCoal(ItemStack s) { return !s.isEmpty() && s.getItem() == Items.COAL && s.getMetadata() == 0; }

    @Override public String wiredName() { return "Coal receiving pit"; }
    @Override public String gearState() { return !powered() ? "NO 12 V" : coal() + " coal in the pit" + (tower() == null ? " · NO TOWER" : " · tower formed"); }

    public int coal() { int n = 0; for (int i = 0; i < store(0).getSizeInventory(); i++) if (isCoal(store(0).getStackInSlot(i))) n += store(0).getStackInSlot(i).getCount(); return n; }

    /** take up to n coal out for the skip */
    public int take(int n) {
        int got = 0;
        for (int i = 0; i < store(0).getSizeInventory() && got < n; i++) {
            ItemStack s = store(0).getStackInSlot(i);
            if (!isCoal(s)) continue;
            int t = Math.min(n - got, s.getCount());
            s.shrink(t); got += t;
            if (s.isEmpty()) store(0).setInventorySlotContents(i, ItemStack.EMPTY);
        }
        if (got > 0) store(0).markDirty();
        return got;
    }

    TileCoalingTower tower() {
        for (EnumFacing f : EnumFacing.HORIZONTALS) if (world.getTileEntity(pos.offset(f)) instanceof TileCoalingTower t) return t;
        return null;
    }

    @Override protected String press(String id, EntityPlayer who) { return null; }

    @Override
    protected void work() {
        if (age % 20 != 0) return;
        if (age % 400 == 0) com.dogpound.railmap.industry.IndustryLink.register(this);
        put("pwr", powered());
        put("pitg", coal() * 100 / (54 * 64));
        put("formed", tower() != null);
        text("coal", String.valueOf(coal()));
    }

    // ---- the industry board: deliveries of coal come here ----------------------------------------------------------------
    @Override public String endpointName() { return "Coaling plant"; }
    @Override public List<ItemStack> makes() { return Collections.emptyList(); }
    @Override public boolean accepts(ItemStack s) { return isCoal(s); }
    @Override public List<ItemStack> wants() {
        TileCoalingTower t = tower();
        int want = (t == null ? 0 : Math.max(0, SteamConfig.bunkerItems / 4 - t.bunker())) + Math.max(0, 16 * 64 - coal());
        return t == null || t.bunker() >= SteamConfig.bunkerItems / 4 ? Collections.emptyList() : Collections.singletonList(new ItemStack(Items.COAL, Math.min(54 * 64, want)));
    }
    @Override public String endpointState() { TileCoalingTower t = tower(); return t == null ? "NO TOWER" : t.bunker() < SteamConfig.bunkerItems / 4 ? "LOW COAL" : "OK"; }
    @Override public void onLoad() { com.dogpound.railmap.industry.IndustryLink.register(this); }
    @Override public void invalidate() { super.invalidate(); com.dogpound.railmap.industry.IndustryLink.forget(this); }

    @Override public boolean hasCapability(Capability<?> c, EnumFacing f) { return c == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(c, f); }
    @SuppressWarnings("unchecked")
    @Override public <T> T getCapability(Capability<T> c, EnumFacing f) { return c == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY ? (T) items : super.getCapability(c, f); }

    @Override protected void saveExtra(NBTTagCompound t) { }
    @Override protected void loadExtra(NBTTagCompound t) { }
    @Override public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() { return new net.minecraft.util.math.AxisAlignedBB(pos).grow(1); }
}
