package com.dogpound.railmap.ctl;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.block.TileFurniture;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.SoundCategory;

/**
 * Real storage in a control desk / rack (her rule: "drawers and cabinets are real storage"): the model's drawer fronts
 * are ZONE controls; clicking one opens it as a chest screen (GUI_OFFICE_STORE + k, the same as the Freight Office),
 * its contents are saved with the tile and drop when the block is broken.
 */
public final class Drawers {
    private final String[] ids, names;
    private final int[] sizes;
    private final InventoryBasic[] inv;

    public Drawers(TileEntity owner, String[] ids, String[] names, int[] sizes) {
        this.ids = ids; this.names = names; this.sizes = sizes;
        inv = new InventoryBasic[ids.length];
        for (int i = 0; i < ids.length; i++) { inv[i] = new InventoryBasic(names[i], true, sizes[i]); inv[i].addInventoryChangeListener(x -> owner.markDirty()); }
    }

    public IInventory storage(int k) { return k >= 0 && k < inv.length ? inv[k] : null; }
    public String name(int k) { return k >= 0 && k < names.length ? names[k] : "Storage"; }
    public int size(int k) { return k >= 0 && k < sizes.length ? sizes[k] : 9; }

    /** a drawer control clicked: opens it (or null = not a drawer) */
    public String open(String id, EntityPlayer who, TileEntity t) {
        for (int k = 0; k < ids.length; k++) {
            if (!id.equals(ids[k])) continue;
            if (who == null) return "§7That is opened by hand";
            who.openGui(RailMap.instance, RailMap.GUI_OFFICE_STORE + k, t.getWorld(), t.getPos().getX(), t.getPos().getY(), t.getPos().getZ());
            t.getWorld().playSound(null, t.getPos(), sizes[k] >= 27 ? com.dogpound.railmap.sound.PackSounds.ev("machine.door_open") : com.dogpound.railmap.sound.PackSounds.ev("ui.drawer_open"), SoundCategory.BLOCKS, 0.6f, 1.0f);
            return "";
        }
        return null;
    }

    public void dropAll(TileEntity t) { for (InventoryBasic i : inv) InventoryHelper.dropInventoryItems(t.getWorld(), t.getPos(), i); }

    public void write(NBTTagCompound t) { NBTTagCompound c = new NBTTagCompound(); for (int i = 0; i < inv.length; i++) c.setTag(ids[i], TileFurniture.save(inv[i])); t.setTag("drawers", c); }
    public void read(NBTTagCompound t) { NBTTagCompound c = t.getCompoundTag("drawers"); for (int i = 0; i < inv.length; i++) TileFurniture.load(inv[i], c.getCompoundTag(ids[i])); }
}
