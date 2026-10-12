package com.dogpound.railmap.block;

import com.dogpound.railmap.controls.ControlState;
import com.dogpound.railmap.controls.IControlled;
import com.dogpound.railmap.freight.Contract;
import com.dogpound.railmap.freight.ContractData;
import com.dogpound.railmap.grid.Panel;
import com.dogpound.railmap.item.ItemWaybill;
import com.dogpound.railmap.proxy.CommonProxy;
import com.dogpound.railmap.wire.IWiredGear;
import com.dogpound.railmap.wire.Terminal;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentString;

import java.util.List;
import java.util.Map;

/**
 * The Freight Office (requested feature): the yard clerk's desk under the big contract board. Six rows of contracts on the wall board with
 * status lamps; the clerk's monitor shows the selected one in full; the desk console has PREV / NEXT, ACCEPT (prints
 * the waybill), DECLINE, PRINT (a copy), COLLECT PAY, the office OPEN / CLOSED key, lamps for 12 V, NEW contracts,
 * LATE loads and the printer, and the earned / waiting-pay readouts; the phone calls the yard master for a report.
 * Screens, printer and buttons run on 12 V; the key is mechanical.
 */
public class TileFreightOffice extends TileEntity implements ITickable, IControlled, IWiredGear, IFurniture {
    // ---- storage (requested feature)
    public static final String[] STORE_IDS = {"dl1", "dl2", "dl3", "dr1", "dr2", "dr3", "fc1", "fc2", "fc3", "cb1", "cb2"};
    public static final String[] STORE_NAMES = {"Desk Drawer (left, bottom)", "Desk Drawer (left, middle)", "Desk Drawer (left, top)",
            "Desk Drawer (right, bottom)", "Desk Drawer (right, middle)", "Desk Drawer (right, top)",
            "Filing Cabinet (bottom)", "Filing Cabinet (middle)", "Filing Cabinet (top)", "Ledger Cupboard (left)", "Ledger Cupboard (right)"};
    public static final int[] STORE_SIZES = {9, 9, 9, 9, 9, 9, 18, 18, 18, 27, 27};
    public static final int SHELF = 8;
    private final net.minecraft.inventory.InventoryBasic[] stores = new net.minecraft.inventory.InventoryBasic[STORE_IDS.length];
    public final net.minecraft.inventory.InventoryBasic shelf = new net.minecraft.inventory.InventoryBasic("Shelf", true, SHELF);
    {
        for (int i = 0; i < stores.length; i++) { stores[i] = new net.minecraft.inventory.InventoryBasic(STORE_NAMES[i], true, STORE_SIZES[i]); stores[i].addInventoryChangeListener(inv -> markDirty()); }
    }
    public net.minecraft.inventory.IInventory storage(int k) { return k >= 0 && k < stores.length ? stores[k] : null; }
    @Override public String storeName(int k) { return k >= 0 && k < STORE_NAMES.length ? STORE_NAMES[k] : "Storage"; }
    @Override public int storeSize(int k) { return k >= 0 && k < STORE_SIZES.length ? STORE_SIZES[k] : 9; }

    /** a click on a drawer / cabinet / cupboard front or a shelf spot (mechanical: no power needed) */
    private String store(String id, EntityPlayer who) {
        for (int k = 0; k < STORE_IDS.length; k++)
            if (id.equals(STORE_IDS[k])) {
                if (who == null) return "§7Drawers are opened by hand";
                who.openGui(com.dogpound.railmap.RailMap.instance, com.dogpound.railmap.RailMap.GUI_OFFICE_STORE + k, world, pos.getX(), pos.getY(), pos.getZ());
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ui.drawer_open"), SoundCategory.BLOCKS, 0.6f, k >= 9 ? 0.85f : 1.05f);
                return "";
            }
        if (!id.startsWith("sh")) return null;
        int s = Integer.parseInt(id.substring(2)) - 1;
        if (who == null || s < 0 || s >= SHELF) return "§7Things go on the shelf by hand";
        net.minecraft.item.ItemStack held = who.getHeldItemMainhand(), there = shelf.getStackInSlot(s);
        if (there.isEmpty()) {
            if (held.isEmpty()) return "§7Hold something to put it on the shelf";
            shelf.setInventorySlotContents(s, held.splitStack(held.getCount()));
            world.playSound(null, pos, SoundEvents.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 0.6f, 1.2f);
        } else {
            if (!held.isEmpty()) return "§7That spot is taken - take it off with an empty hand first";
            if (!who.inventory.addItemStackToInventory(there)) who.dropItem(there, false);
            shelf.setInventorySlotContents(s, net.minecraft.item.ItemStack.EMPTY);
        }
        controlsChanged();                                                     // everyone sees what's on the shelf
        return "";
    }

    /** everything inside, for when the office is broken */
    @Override public void dropAll() {
        for (net.minecraft.inventory.InventoryBasic inv : stores) net.minecraft.inventory.InventoryHelper.dropInventoryItems(world, pos, inv);
        net.minecraft.inventory.InventoryHelper.dropInventoryItems(world, pos, shelf);
    }

    private static NBTTagCompound saveInv(net.minecraft.inventory.InventoryBasic inv) {
        NBTTagCompound t = new NBTTagCompound();
        for (int i = 0; i < inv.getSizeInventory(); i++) if (!inv.getStackInSlot(i).isEmpty()) t.setTag(String.valueOf(i), inv.getStackInSlot(i).writeToNBT(new NBTTagCompound()));
        return t;
    }

    private static void loadInv(net.minecraft.inventory.InventoryBasic inv, NBTTagCompound t) {
        for (int i = 0; i < inv.getSizeInventory(); i++) inv.setInventorySlotContents(i, t.hasKey(String.valueOf(i)) ? new net.minecraft.item.ItemStack(t.getCompoundTag(String.valueOf(i))) : net.minecraft.item.ItemStack.EMPTY);
    }

    private final ControlState ctl = new ControlState();
    private final Terminal term = new Terminal();
    private int sel;
    private int age;

    // ---- controls -------------------------------------------------------------------------------------------------
    @Override public ControlState controls() { return ctl; }
    @Override public void controlsChanged() { markDirty(); if (world != null && !world.isRemote) { IBlockState s = world.getBlockState(pos); world.notifyBlockUpdate(pos, s, s, 3); } }

    private boolean open() { return ctl.get("office") == 0; }

    @Override
    public int controlValue(String id) {
        if (id.equals("office")) return ctl.get(id);                             // the key is mechanical
        return term.powered() ? ctl.get(id) : 0;
    }

    @Override public String ptext(String key) { return term.powered() ? ctl.text(key) : ""; }

    private Contract selected() {
        List<Contract> b = ContractData.get(world).board();
        if (b.isEmpty()) return null;
        sel = Math.max(0, Math.min(sel, b.size() - 1));
        return b.get(sel);
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        String st = store(id, who);
        if (st != null) return st;
        if (id.equals("office")) return null;                                   // default: the key turns
        if (!term.powered()) return "§cThe office is dark - no 12 V";
        ContractData d = ContractData.get(world);
        Contract k;
        switch (id) {
            case "prev": sel = Math.max(0, sel - 1); refresh(); return "";
            case "next": sel++; refresh(); return "";
            case "accept":
                if (!open()) return "§7The office is CLOSED - turn the key to OPEN";
                if (who == null) return "§7Contracts are signed for at the desk";
                k = selected();
                if (k == null) return "§7No contracts on the board";
                String r = d.accept(world, k.id, who);
                if (k.state == Contract.State.ACCEPTED) print(k);
                refresh();
                return r;
            case "decline":
                k = selected();
                if (k == null) return "§7No contracts on the board";
                r = d.decline(k.id);
                refresh();
                return r;
            case "print":
                k = selected();
                if (k == null || k.state != Contract.State.ACCEPTED) return "§7Only contracts in progress have a waybill";
                print(k);
                return "§7Waybill #" + k.id + " reprinted";
            case "collect":
                if (!(who instanceof EntityPlayerMP mp)) return "§7Pay is collected at the desk, in person";
                long waiting = d.uncollected(mp.getUniqueID());
                if (waiting <= 0) return "§7Nothing waiting for you, " + who.getName();
                long got = d.collect(mp);
                refresh();
                return got > 0 ? "§a§l$" + Contract.money(got) + "§a paid out - thank you for hauling with us" : "§cThe cash box is empty (Pride Realms isn't installed)";
            case "phone":
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.CASH, SoundCategory.BLOCKS, 0.6f, 1.0f);
                if (who != null) for (String line : report(who)) who.sendMessage(new TextComponentString(line));
                return "§7Ring ring... the yard master picks up";
            default: return null;
        }
    }

    private List<String> report(EntityPlayer who) {
        List<String> out = new java.util.ArrayList<>();
        out.add("§6☎ Yard master:");
        long now = world.getTotalWorldTime();
        for (Contract k : ContractData.get(world).all())
            if (k.state == Contract.State.ACCEPTED && who.getUniqueID().equals(k.by))
                out.add(String.format("§7 #%d §f%d %s§7: loaded %d, delivered %d · %s · %s", k.id, k.qty, k.itemName, k.loaded, k.delivered, k.toName,
                        now > k.deadline ? "§c" + k.timeLeft(now) : "due " + k.timeLeft(now)));
        if (out.size() == 1) out.add("§7 Nothing on your books - take a job off the board");
        long w = ContractData.get(world).uncollected(who.getUniqueID());
        if (w > 0) out.add("§6 $" + Contract.money(w) + " waiting for you at the desk");
        return out;
    }

    /** the printer feeds a waybill out over the front of the desk */
    private void print(Contract k) {
        EnumFacing f = controlFacing();
        EntityItem e = new EntityItem(world, pos.getX() + 0.5 + f.getXOffset() * 0.7, pos.getY() + 0.8, pos.getZ() + 0.5 + f.getZOffset() * 0.7,
                ItemWaybill.make(CommonProxy.waybill, k, world.getTotalWorldTime()));
        e.motionX = f.getXOffset() * 0.08; e.motionY = 0.1; e.motionZ = f.getZOffset() * 0.08;
        world.spawnEntity(e);
        world.playSound(null, pos, com.dogpound.railmap.ModSounds.PRINTER, SoundCategory.BLOCKS, 0.25f, 1.0f);
        ctl.set("paper", 1);
    }

    // ---- the screens and lamps --------------------------------------------------------------------------------------
    @Override
    public void update() {
        if (world.isRemote) return;
        if (term.tick(world, pos, wiredName())) controlsChanged();
        if (++age % 10 != 3) return;
        refresh();
    }

    private void refresh() {
        boolean ch = false;
        ContractData d = ContractData.get(world);
        List<Contract> b = d.board();
        long now = world.getTotalWorldTime();
        sel = Math.max(0, Math.min(sel, Math.max(0, b.size() - 1)));
        int top = Math.max(0, Math.min(sel - 2, b.size() - 6));
        for (int r = 1; r <= 6; r++) {
            int i = top + r - 1;
            Contract k = i < b.size() ? b.get(i) : null;
            boolean me = i == sel;
            ch |= StationControls.text(ctl, "r" + r + "a", k == null ? "" : (me ? "> " : "  ") + "#" + k.id + " " + k.qty + " " + cut(k.itemName, 14));
            ch |= StationControls.text(ctl, "r" + r + "b", k == null ? "" : cut(shortName(k.fromName), 12) + " > " + cut(shortName(k.toName), 12));
            ch |= StationControls.text(ctl, "r" + r + "c", k == null ? "" : k.state == Contract.State.OPEN || k.state == Contract.State.ACCEPTED
                    ? "$" + Contract.money(k.payNow(now)) + " " + k.timeLeft(now) : k.state == Contract.State.DONE ? "DONE $" + Contract.money(k.paid) : k.state.label.toUpperCase());
            ch |= StationControls.put(ctl, "r" + r + "o", k != null && k.state == Contract.State.OPEN);
            ch |= StationControls.put(ctl, "r" + r + "p", k != null && k.state == Contract.State.ACCEPTED);
            ch |= StationControls.put(ctl, "r" + r + "x", k != null && k.state == Contract.State.ACCEPTED && now > k.deadline);
        }
        Contract s = b.isEmpty() ? null : b.get(sel);
        ch |= StationControls.text(ctl, "d1", s == null ? "NO CONTRACTS ON THE BOARD" : "CONTRACT #" + s.id + "  " + s.state.label.toUpperCase());
        ch |= StationControls.text(ctl, "d2", s == null ? "industries post jobs when" : s.qty + " " + s.itemName + " - " + s.distance + " blocks");
        ch |= StationControls.text(ctl, "d3", s == null ? "one needs what another makes" : shortName(s.fromName) + " > " + shortName(s.toName));
        ch |= StationControls.text(ctl, "d4", s == null ? "" : s.state == Contract.State.ACCEPTED
                ? "LOADED " + s.loaded + " DELIVERED " + s.delivered + "/" + s.qty + " - " + s.byName
                : s.state == Contract.State.OPEN ? "PAYS $" + Contract.money(s.pay) + " - DUE " + s.timeLeft(now) : s.state == Contract.State.DONE ? "PAID $" + Contract.money(s.paid) + " - " + s.byName : "");
        boolean fresh = false, late = false;
        long earned = 0, waiting = 0;
        for (Contract k : d.all()) {
            if (k.state == Contract.State.OPEN && now - k.posted < 2400) fresh = true;
            if (k.state == Contract.State.ACCEPTED && now > k.deadline) late = true;
            if (k.state == Contract.State.DONE) { earned += k.paid; if (!k.collected) waiting += k.paid; }
        }
        ch |= StationControls.put(ctl, "power", term.powered());
        ch |= StationControls.put(ctl, "new", fresh && open());
        ch |= StationControls.put(ctl, "late", late);
        ch |= StationControls.put(ctl, "paper", term.powered() && open());
        ch |= StationControls.text(ctl, "earned", "$" + Contract.money(earned));
        ch |= StationControls.text(ctl, "unpaid", "$" + Contract.money(waiting));
        if (ch) controlsChanged();
    }

    private static String shortName(String s) { int i = s.indexOf(" ("); return i < 0 ? s : s.substring(0, i); }
    private static String cut(String s, int n) { return s.length() <= n ? s : s.substring(0, n - 1) + "."; }

    // ---- 12 V terminal + console --------------------------------------------------------------------------------------
    @Override public Terminal term() { return term; }
    @Override public void gearChanged() { controlsChanged(); }
    @Override public String wiredName() { return "Freight Office"; }
    @Override public String gearState() {
        List<Contract> b = ContractData.get(world).board();
        long open = b.stream().filter(k -> k.state == Contract.State.OPEN).count(), run = b.stream().filter(k -> k.state == Contract.State.ACCEPTED).count();
        return (open() ? "OPEN" : "CLOSED") + " · " + open + " on offer · " + run + " in progress";
    }
    @Override public Panel wiredPanel() { return layout().all; }
    @Override public String wiredPress(String id, String who) { return IControlled.operate(this, id, null); }
    @Override public int wiredState(String id) { return controlValue(id); }
    @Override public void gearInfo(Map<String, Object> m) { m.put("selected", sel); m.put("office", open() ? "open" : "closed"); }

    // ---- save -----------------------------------------------------------------------------------------------------
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        ctl.write(t);
        term.write(t);
        t.setInteger("sel", sel);
        NBTTagCompound inv = new NBTTagCompound();
        for (int i = 0; i < stores.length; i++) inv.setTag(STORE_IDS[i], saveInv(stores[i]));
        inv.setTag("shelf", saveInv(shelf));
        t.setTag("inv", inv);
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        ctl.read(t);
        term.read(t);
        if (!t.hasKey("term")) term.legacy = false;
        sel = t.getInteger("sel");
        NBTTagCompound inv = t.getCompoundTag("inv");
        for (int i = 0; i < stores.length; i++) loadInv(stores[i], inv.getCompoundTag(STORE_IDS[i]));
        loadInv(shelf, inv.getCompoundTag("shelf"));
    }

    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket() { return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(net.minecraft.network.NetworkManager net, net.minecraft.network.play.server.SPacketUpdateTileEntity pkt) { readFromNBT(pkt.getNbtCompound()); }
    @Override public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() { return new net.minecraft.util.math.AxisAlignedBB(pos).grow(1, 2, 1); }
}
