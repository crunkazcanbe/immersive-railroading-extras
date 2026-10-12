package com.dogpound.railmap.block;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.controls.ControlState;
import com.dogpound.railmap.controls.IControlled;
import com.dogpound.railmap.grid.Panel;
import com.dogpound.railmap.wire.IWiredGear;
import com.dogpound.railmap.wire.Terminal;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;

import java.util.UUID;

/**
 * The shared half of the economy office furniture (Company HQ, Commodity Exchange, Travel Centre, Container Terminal
 * desk), built like the Freight Office: real storages behind the drawer / cupboard / safe fronts (ZONE controls "id"
 * open a chest screen), a shelf whose spots "sh1".."shN" hold what you put there (drawn standing on it), a 12 V terminal
 * that powers the screens, lamps and buttons, and whoever placed it.
 */
public abstract class TileFurniture extends TileEntity implements ITickable, IControlled, IWiredGear, IFurniture {
    protected final ControlState ctl = new ControlState();
    protected final Terminal term = new Terminal();
    private final String[] ids, names;
    private final int[] sizes;
    private final InventoryBasic[] stores;
    public final InventoryBasic shelf;
    protected UUID placer;
    protected String placerName = "";
    protected int age;

    protected TileFurniture(String[] ids, String[] names, int[] sizes, int shelf) {
        this.ids = ids; this.names = names; this.sizes = sizes;
        stores = new InventoryBasic[ids.length];
        for (int i = 0; i < ids.length; i++) { stores[i] = new InventoryBasic(names[i], true, sizes[i]); stores[i].addInventoryChangeListener(inv -> markDirty()); }
        this.shelf = new InventoryBasic("Shelf", true, Math.max(1, shelf));
    }

    public void placedBy(EntityPlayer p) { placer = p.getUniqueID(); placerName = p.getName(); markDirty(); }

    // ---- storage ---------------------------------------------------------------------------------------------------
    @Override public IInventory storage(int k) { return k >= 0 && k < stores.length ? stores[k] : null; }
    @Override public String storeName(int k) { return k >= 0 && k < names.length ? names[k] : "Storage"; }
    @Override public int storeSize(int k) { return k >= 0 && k < sizes.length ? sizes[k] : 9; }
    public int shelfSize() { return shelf.getSizeInventory(); }
    /** where each shelf spot is (model px: x, y of the shelf top, z) - written by tools/gen_bb_economy.py */
    public double[][] shelfSpots() { return new double[0][]; }

    /** null = may open; else why not (the safe is for company members only) */
    protected String refuse(int k, EntityPlayer who) { return null; }

    @Override
    public void dropAll() {
        for (InventoryBasic inv : stores) InventoryHelper.dropInventoryItems(world, pos, inv);
        InventoryHelper.dropInventoryItems(world, pos, shelf);
    }

    private String store(String id, EntityPlayer who) {
        for (int k = 0; k < ids.length; k++)
            if (id.equals(ids[k])) {
                if (who == null) return "§7That is opened by hand";
                String no = refuse(k, who);
                if (no != null) { world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("machine.clunk"), SoundCategory.BLOCKS, 0.35f, 1.5f); return no; }
                who.openGui(RailMap.instance, RailMap.GUI_OFFICE_STORE + k, world, pos.getX(), pos.getY(), pos.getZ());
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ui.drawer_open"), SoundCategory.BLOCKS, 0.6f, sizes[k] >= 27 ? 0.85f : 1.05f);
                return "";
            }
        if (!id.startsWith("sh") || id.length() < 3 || !Character.isDigit(id.charAt(2))) return null;
        int s = Integer.parseInt(id.substring(2)) - 1;
        if (who == null || s < 0 || s >= shelf.getSizeInventory()) return "§7Things go on the shelf by hand";
        ItemStack held = who.getHeldItemMainhand(), there = shelf.getStackInSlot(s);
        if (there.isEmpty()) {
            if (held.isEmpty()) return "§7Hold something to put it here";
            shelf.setInventorySlotContents(s, held.splitStack(held.getCount()));
            world.playSound(null, pos, SoundEvents.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 0.6f, 1.2f);
        } else {
            if (!held.isEmpty()) return "§7That spot is taken - take it off with an empty hand first";
            if (!who.inventory.addItemStackToInventory(there)) who.dropItem(there, false);
            shelf.setInventorySlotContents(s, ItemStack.EMPTY);
        }
        controlsChanged();
        return "";
    }

    // ---- controls ---------------------------------------------------------------------------------------------------
    @Override public ControlState controls() { return ctl; }
    @Override public void controlsChanged() { markDirty(); if (world != null && !world.isRemote) { IBlockState s = world.getBlockState(pos); world.notifyBlockUpdate(pos, s, s, 3); } }

    /** controls that work with no power (keys, latches) */
    protected boolean mechanical(String id) { return false; }

    @Override public int controlValue(String id) { return mechanical(id) || id.startsWith("m_cab_") || term.powered() ? ctl.get(id) : 0; }   // m_cab_: cabinet doors, MCBs, fuses
    @Override public String ptext(String key) { return term.powered() && com.dogpound.railmap.cabinet.Cabinet.screenOk(ctl, key) ? ctl.text(key) : ""; }

    @Override
    public final String controlPress(String id, EntityPlayer who) {
        String st = store(id, who);
        if (st != null) return st;
        if (mechanical(id)) return pressMechanical(id, who);
        if (!term.powered()) return "§cThe " + wiredName().toLowerCase(java.util.Locale.ROOT) + " is dark - no 12 V";
        String r = press(id, who);
        if (refresh()) controlsChanged();
        return r;
    }

    /** a key / latch: null = default (turns) */
    protected String pressMechanical(String id, EntityPlayer who) { return null; }
    /** a powered button: the message, or null for the default behaviour */
    protected abstract String press(String id, EntityPlayer who);
    /** update the screens and lamps; true if anything changed */
    protected abstract boolean refresh();

    @Override
    public void update() {
        if (world.isRemote) return;
        if (term.tick(world, pos, wiredName())) controlsChanged();
        serverTick();
        if (++age % 10 != 3) return;
        if (refresh()) controlsChanged();
    }

    protected void serverTick() {}

    // ---- 12 V terminal + console ---------------------------------------------------------------------------------
    @Override public Terminal term() { return term; }
    @Override public void gearChanged() { controlsChanged(); }
    @Override public Panel wiredPanel() { return layout().all; }
    @Override public String wiredPress(String id, String who) { return com.dogpound.railmap.cabinet.Cabinet.isCabinetId(id) ? "§7Cabinet MCBs and fuses are worked by hand, at the cabinet" : IControlled.operate(this, id, null); }
    @Override public int wiredState(String id) { return controlValue(id); }

    // ---- save -----------------------------------------------------------------------------------------------------
    protected void writeExtra(NBTTagCompound t) {}
    protected void readExtra(NBTTagCompound t) {}

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        ctl.write(t);
        term.write(t);
        NBTTagCompound inv = new NBTTagCompound();
        for (int i = 0; i < stores.length; i++) inv.setTag(ids[i], save(stores[i]));
        inv.setTag("shelf", save(shelf));
        t.setTag("inv", inv);
        if (placer != null) t.setUniqueId("placer", placer);
        t.setString("placerN", placerName);
        writeExtra(t);
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        ctl.read(t);
        term.read(t);
        if (!t.hasKey("term")) term.legacy = false;
        NBTTagCompound inv = t.getCompoundTag("inv");
        for (int i = 0; i < stores.length; i++) load(stores[i], inv.getCompoundTag(ids[i]));
        load(shelf, inv.getCompoundTag("shelf"));
        placer = t.hasUniqueId("placer") ? t.getUniqueId("placer") : null;
        placerName = t.getString("placerN");
        readExtra(t);
    }

    public static NBTTagCompound save(IInventory inv) {
        NBTTagCompound t = new NBTTagCompound();
        for (int i = 0; i < inv.getSizeInventory(); i++) if (!inv.getStackInSlot(i).isEmpty()) t.setTag(String.valueOf(i), inv.getStackInSlot(i).writeToNBT(new NBTTagCompound()));
        return t;
    }

    public static void load(IInventory inv, NBTTagCompound t) {
        for (int i = 0; i < inv.getSizeInventory(); i++) inv.setInventorySlotContents(i, t.hasKey(String.valueOf(i)) ? new ItemStack(t.getCompoundTag(String.valueOf(i))) : ItemStack.EMPTY);
    }

    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket() { return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(net.minecraft.network.NetworkManager net, net.minecraft.network.play.server.SPacketUpdateTileEntity pkt) { readFromNBT(pkt.getNbtCompound()); }
    @Override public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() { return new net.minecraft.util.math.AxisAlignedBB(pos).grow(2, 3, 2); }

    protected static String cut(String s, int n) { return s.length() <= n ? s : s.substring(0, Math.max(1, n - 1)) + "."; }
}
