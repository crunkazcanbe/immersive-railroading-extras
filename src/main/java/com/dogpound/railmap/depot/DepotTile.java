package com.dogpound.railmap.depot;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.block.IFurniture;
import com.dogpound.railmap.block.TileFurniture;
import com.dogpound.railmap.safety.SafetyTile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;

import java.util.Map;

/**
 * Shared plumbing for the depot and station machines: the safety gear's working controls + 12 V terminal + save/sync
 * (SafetyTile), real storages behind ZONE controls (a chest screen, dropped when it is broken), and mains power for the
 * motors (Mains). The "test" push is always a lamp test. Keys / selectors / mushrooms named k_ / s_ / m_ stay where
 * they were put with no power; everything else is dark without 12 V.
 */
public abstract class DepotTile extends SafetyTile implements IFurniture {
    protected final Mains mains = new Mains();
    private final String[] sids, snames;
    private final int[] ssizes;
    private final InventoryBasic[] stores;

    protected DepotTile(String[] ids, String[] names, int[] sizes) {
        sids = ids; snames = names; ssizes = sizes;
        stores = new InventoryBasic[ids.length];
        for (int i = 0; i < ids.length; i++) { stores[i] = new InventoryBasic(names[i], true, sizes[i]); stores[i].addInventoryChangeListener(inv -> markDirty()); }
    }

    protected DepotTile() { this(new String[0], new String[0], new int[0]); }

    // ---- storage ---------------------------------------------------------------------------------------------------
    @Override public IInventory storage(int k) { return k >= 0 && k < stores.length ? stores[k] : null; }
    @Override public String storeName(int k) { return k >= 0 && k < snames.length ? snames[k] : "Storage"; }
    @Override public int storeSize(int k) { return k >= 0 && k < ssizes.length ? ssizes[k] : 9; }
    @Override public void dropAll() { for (InventoryBasic inv : stores) InventoryHelper.dropInventoryItems(world, pos, inv); }
    protected InventoryBasic store(int k) { return stores[k]; }

    // ---- controls ---------------------------------------------------------------------------------------------------
    @Override
    public final String controlPress(String id, EntityPlayer who) {
        for (int k = 0; k < sids.length; k++)
            if (id.equals(sids[k])) {
                if (who == null) return "§7That is opened by hand";
                who.openGui(RailMap.instance, RailMap.GUI_OFFICE_STORE + k, world, pos.getX(), pos.getY(), pos.getZ());
                world.playSound(null, pos, ssizes[k] >= 27 ? com.dogpound.railmap.sound.PackSounds.ev("machine.door_open") : com.dogpound.railmap.sound.PackSounds.ev("ui.drawer_open"), SoundCategory.BLOCKS, 0.6f, 1.0f);
                return "";
            }
        if (id.equals("test")) return lampTest();
        if (id.startsWith("k_") || id.startsWith("s_") || id.startsWith("m_")) return mechanical(id, who);
        if (!powered()) return "§cThe " + wiredName().toLowerCase(java.util.Locale.ROOT) + " is dead - no 12 V";
        return press(id, who);
    }

    /** a key / selector / E-stop: null = the default (it turns / latches) */
    protected String mechanical(String id, EntityPlayer who) { return null; }
    /** a powered push: the message (null = default) */
    protected abstract String press(String id, EntityPlayer who);

    /** FE the motors draw this second (0 = idle); mains is checked either way */
    protected long load() { return 0; }
    /** true when this machine needs mains at all */
    protected boolean usesMains() { return false; }
    protected boolean mainsOk() { return !usesMains() || mains.ok(); }

    @Override
    protected final void serverTick() {
        if (usesMains() && mains.tick(world, pos, term, wiredName(), powered() ? load() : 0)) put("mains", mains.ok());
        work();
    }

    /** the machine's own tick (SafetyTile.update calls this every tick) */
    protected abstract void work();

    @Override public void gearInfo(Map<String, Object> m) { if (usesMains()) m.put("mains", mains.what); }

    // ---- save ---------------------------------------------------------------------------------------------------------
    protected abstract void saveExtra(NBTTagCompound t);
    protected abstract void loadExtra(NBTTagCompound t);

    @Override
    protected final void save(NBTTagCompound t) {
        NBTTagCompound inv = new NBTTagCompound();
        for (int i = 0; i < stores.length; i++) inv.setTag(sids[i], TileFurniture.save(stores[i]));
        t.setTag("inv", inv);
        mains.write(t);
        saveExtra(t);
    }

    @Override
    protected final void load(NBTTagCompound t) {
        NBTTagCompound inv = t.getCompoundTag("inv");
        for (int i = 0; i < stores.length; i++) TileFurniture.load(stores[i], inv.getCompoundTag(sids[i]));
        mains.read(t);
        loadExtra(t);
    }

    @Override public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() { return new net.minecraft.util.math.AxisAlignedBB(pos).grow(2, 3, 2); }

    protected static String cut(String s, int n) { return s == null ? "" : s.length() <= n ? s : s.substring(0, Math.max(1, n - 1)) + "."; }
}
