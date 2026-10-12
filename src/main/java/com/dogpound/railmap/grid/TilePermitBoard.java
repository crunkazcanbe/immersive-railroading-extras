package com.dogpound.railmap.grid;

import com.dogpound.railmap.block.StationControls;
import com.dogpound.railmap.controls.ControlState;
import com.dogpound.railmap.controls.IControlled;
import com.dogpound.railmap.wire.IWiredGear;
import com.dogpound.railmap.wire.Terminal;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Permit-to-Work Board (her ChatGPT list #44; Requested: furniture that opens and stores things, lots of working buttons,
 * screens and lamps). A wall cabinet with:
 *   a KEY SAFE - twelve hooks behind a glass door that really hold things (the lockout keys hang where you see them);
 *               keys a live permit depends on are locked in until it is cancelled
 *   a PERMIT PRINTER - feed paper into its tray, PRINT gives a blank Permit to Work form
 *   CHECK - hold a made-out form: the four procedure lamps show isolated / earthed / proven dead / locked off
 *   ISSUE - with every step done and every padlock key in the safe, the permit is issued to you and registered
 *   CANCEL - hand an issued permit back: the work is finished, the keys come free
 *   a REGISTER display with PREV / NEXT through the live permits, PERMITS LIVE + KEY SAFE LOCKED lamps.
 * The printer, display and lamps run on 12 V; the key safe is mechanical.
 */
public class TilePermitBoard extends TileEntity implements ITickable, IControlled, IWiredGear {
    public static final int HOOKS = 12;
    public final InventoryBasic hooks = new InventoryBasic("Key Safe", true, HOOKS);
    private final ControlState ctl = new ControlState();
    private final Terminal term = new Terminal();
    private int paper, sel, age;

    @Override public ControlState controls() { return ctl; }
    @Override public void controlsChanged() { markDirty(); if (world != null && !world.isRemote) { IBlockState s = world.getBlockState(pos); world.notifyBlockUpdate(pos, s, s, 3); } }
    @Override public int controlValue(String id) { return id.startsWith("k") || id.equals("tray") ? ctl.get(id) : term.powered() ? ctl.get(id) : 0; }
    @Override public String ptext(String key) { return term.powered() ? ctl.text(key) : ""; }

    public void dropAll() { net.minecraft.inventory.InventoryHelper.dropInventoryItems(world, pos, hooks); if (paper > 0) net.minecraft.inventory.InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.PAPER, paper)); }

    private int hook(String id) {
        if (!id.startsWith("k")) return -1;
        try { int i = Integer.parseInt(id.substring(1)) - 1; return i >= 0 && i < HOOKS ? i : -1; } catch (NumberFormatException e) { return -1; }
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        int h = hook(id);
        if (h >= 0) return hang(h, who);
        Permits ps = Permits.get(world);
        ItemStack held = who == null ? ItemStack.EMPTY : who.getHeldItemMainhand();
        if (id.equals("tray")) {
            if (who == null) return "§7Paper goes in by hand";
            if (held.getItem() == Items.PAPER) {
                int n = Math.min(64 - paper, held.getCount());
                if (n <= 0) return "§7The paper tray is full (64 sheets)";
                paper += n; held.shrink(n); refresh();
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ui.paper"), SoundCategory.BLOCKS, 0.7f, 1.0f);
                return "§7" + paper + " sheets in the printer tray";
            }
            return "§7Printer tray: " + paper + " sheets of paper (put paper in by hand)";
        }
        if (id.equals("lamptest")) { ctl.set("lt", 6); refresh(); return "§7Lamp test"; }
        if (!term.powered()) return "§cThe board is dark - no 12 V (the key safe still works)";
        switch (id) {
            case "print": {
                if (paper <= 0) return "§cOut of paper - put paper in the tray";
                paper--;
                ItemStack form = new ItemStack(com.dogpound.railmap.proxy.CommonProxy.workPermit);
                if (who == null || !who.addItemStackToInventory(form)) net.minecraft.inventory.InventoryHelper.spawnItemStack(world, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, form);
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.PRINTER, SoundCategory.BLOCKS, 0.3f, 1.0f);
                refresh();
                return "§aPermit form printed §7- right-click the equipment you will work on with it";
            }
            case "check": {
                if (!ItemPermit.bound(held)) return "§7Hold a made-out permit form (right-click the equipment with a blank one first)";
                Permits.Check c = Permits.check(world, ItemPermit.equip(held), ItemPermit.bindAt(held));
                lamps(c);
                ItemPermit.snapshot(held, c.lines);
                List<String> miss = missingKeys(c);
                if (who != null) for (String l : c.lines) who.sendMessage(new net.minecraft.util.text.TextComponentString("  " + l));
                return c.ok() && miss.isEmpty() ? "§aAll steps done, keys in the safe - press ISSUE" : !miss.isEmpty() && c.ok() ? "§eKey for " + miss.get(0) + " is not in the key safe" : "§eNot ready - see the lamps";
            }
            case "issue": {
                if (ItemPermit.issued(held)) return "§7That permit is already issued (#" + held.getTagCompound().getInteger("id") + ")";
                if (!ItemPermit.bound(held)) return "§7Hold the made-out permit form when you press ISSUE";
                BlockPos eq = ItemPermit.equip(held);
                Permits.Check c = Permits.check(world, eq, ItemPermit.bindAt(held));
                lamps(c);
                ItemPermit.snapshot(held, c.lines);
                if (!c.ok()) return "§c⛔ Can't issue: " + firstFail(c);
                List<String> miss = missingKeys(c);
                if (!miss.isEmpty()) return "§c⛔ Can't issue: the key for the padlock on " + miss.get(0) + " is not hanging in this key safe";
                { String ap = com.dogpound.railmap.admin.Approvals.blocked(world, "permit", eq, who); if (ap != null) return ap; }   // next-48 #43: request -> approve -> execute
                String what = held.getTagCompound().getString("what");
                Permits.Permit p = ps.add(world, eq.toLong(), pos.toLong(), who == null ? "?" : who.getName(), what, c);
                held.getTagCompound().setInteger("id", p.id);
                held.getTagCompound().setString("by", p.holder);
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.CTL_KEY, SoundCategory.BLOCKS, 0.25f, 1.0f);
                for (long a : c.points) History.log(world, a, "PERMIT", "isolation point for PERMIT #" + p.id + " (" + p.holder + ")");
                History.log(world, eq, "PERMIT", "PERMIT #" + p.id + " ISSUED to " + p.holder + " - " + c.points.size() + " points locked, earthed, proven dead");
                GridTicker.alarm(world, eq.toLong(), "§b📋 PERMIT #" + p.id + " issued to " + p.holder + " for " + what + " - section isolated, earthed and locked");
                sel = ps.live().indexOf(p);
                refresh();
                return "§a✔ PERMIT #" + p.id + " ISSUED to " + p.holder + " §7- you may work inside the isolated section. Hand it back here (CANCEL) when finished";
            }
            case "cancel": {
                Permits.Permit p = ItemPermit.permit(ps, held);
                if (p == null) return "§7Hold the issued permit you are handing back";
                ps.cancel(p);
                held.shrink(1);                                                // filed in the register
                History.log(world, BlockPos.fromLong(p.equip), "PERMIT", "PERMIT #" + p.id + " CANCELLED - work finished, men clear (" + (who == null ? "?" : who.getName()) + ")");
                GridTicker.alarm(world, p.equip, "§b📋 PERMIT #" + p.id + " cancelled - the section may be restored (remove earths, then padlocks, then close)");
                refresh();
                return "§aPERMIT #" + p.id + " cancelled and filed §7- keys released. Restore: earth off, padlocks off, breakers closed";
            }
            case "prev": sel = Math.max(0, sel - 1); refresh(); return "";
            case "next": sel++; refresh(); return "";
            default: return "";
        }
    }

    private static String firstFail(Permits.Check c) {
        for (String l : c.lines) if (l.contains("✖")) return l.replaceAll("§.", "");
        return "?";
    }

    private void lamps(Permits.Check c) {
        ctl.set("c1", c.isolated ? 1 : 2); ctl.set("c2", c.earthed ? 1 : 2); ctl.set("c3", c.dead ? 1 : 2); ctl.set("c4", c.locked ? 1 : 2);
        ctl.set("shown", 400);
    }

    /** padlocks in the procedure whose key is not on a hook here */
    private List<String> missingKeys(Permits.Check c) {
        List<String> out = new ArrayList<>();
        Set<Long> have = new HashSet<>();
        for (int i = 0; i < HOOKS; i++) { long d = ItemLotoKey.device(hooks.getStackInSlot(i)); if (d != Long.MIN_VALUE) have.add(d); }
        Set<Long> need = new LinkedHashSet<>(c.points);
        need.addAll(c.earths);
        for (long p : need) if (!have.contains(p)) out.add(Protection.at(p));
        return out;
    }

    /** hang something on a hook / take it off (keys a live permit needs stay locked in) */
    private String hang(int h, EntityPlayer who) {
        if (who == null) return "§7The key safe is opened by hand";
        ItemStack held = who.getHeldItemMainhand(), there = hooks.getStackInSlot(h);
        if (there.isEmpty()) {
            if (held.isEmpty()) return "§7Empty hook - hang a lockout key (or a padlock, a permit...) on it";
            hooks.setInventorySlotContents(h, held.splitStack(1));
            world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ui.stamp"), SoundCategory.BLOCKS, 0.5f, 1.3f);
        } else {
            long dev = ItemLotoKey.device(there);
            Permits.Permit p = dev == Long.MIN_VALUE ? null : Permits.get(world).keyHeld(dev);
            if (p != null) return "§c🔒 Locked in the safe: PERMIT #" + p.id + " (" + p.holder + ") depends on this key - cancel the permit first";
            if (!held.isEmpty()) return "§7That hook is taken - take it off with an empty hand first";
            if (!who.inventory.addItemStackToInventory(there)) who.dropItem(there, false);
            hooks.setInventorySlotContents(h, ItemStack.EMPTY);
            world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ui.paper"), SoundCategory.BLOCKS, 0.6f, 1.1f);
        }
        refresh();
        controlsChanged();                                                      // everyone sees what hangs where
        return "";
    }

    @Override
    public void update() {
        if (world.isRemote) return;
        if (term.tick(world, pos, wiredName())) controlsChanged();
        if (++age % 10 == 0) refresh();
    }

    private void refresh() {
        Permits ps = Permits.get(world);
        List<Permits.Permit> live = ps.live();
        boolean ch = false;
        int lt = ctl.get("lt");
        if (lt > 0) ctl.set("lt", lt - 1);
        boolean t = lt > 0;
        boolean locked = false;
        for (int i = 0; i < HOOKS; i++) {
            long dev = ItemLotoKey.device(hooks.getStackInSlot(i));
            boolean held = dev != Long.MIN_VALUE && ps.keyHeld(dev) != null;
            locked |= held;
            ch |= StationControls.put(ctl, "k" + (i + 1), hooks.getStackInSlot(i).isEmpty() ? 0 : held ? 2 : 1);
        }
        ch |= StationControls.put(ctl, "lsafe", t || locked ? 1 : 0);
        ch |= StationControls.put(ctl, "llive", t || !live.isEmpty() ? 1 : 0);
        ch |= StationControls.put(ctl, "lpwr", term.powered() ? 1 : 0);
        ch |= StationControls.put(ctl, "lpaper", t || paper == 0 ? 1 : 0);
        ch |= StationControls.put(ctl, "count", live.size());
        ch |= StationControls.put(ctl, "paper", paper);
        int shown = ctl.get("shown");
        if (shown > 0) ctl.set("shown", Math.max(0, shown - 10));
        for (int i = 1; i <= 4; i++) {
            int v = t ? 1 : shown > 0 ? ctl.get("c" + i) : 0;
            ch |= StationControls.put(ctl, "s" + i, v == 1 ? 1 : 0);
            ch |= StationControls.put(ctl, "x" + i, v == 2 ? 1 : 0);
        }
        if (live.isEmpty()) { ch |= StationControls.text(ctl, "reg1", "NO PERMITS LIVE"); ch |= StationControls.text(ctl, "reg2", "PLANT IN NORMAL SERVICE"); }
        else {
            sel = Math.max(0, Math.min(sel, live.size() - 1));
            Permits.Permit p = live.get(sel);
            ch |= StationControls.text(ctl, "reg1", "#" + p.id + " " + p.holder + " " + History.clock(p.issued) + " (" + (sel + 1) + "/" + live.size() + ")");
            ch |= StationControls.text(ctl, "reg2", cut(p.what + " · " + p.points.size() + " PTS", 30));
        }
        if (ch) controlsChanged();
    }

    private static String cut(String s, int n) { return s.length() <= n ? s : s.substring(0, n - 1) + "."; }

    // ---- 12 V + console -------------------------------------------------------------------------------------------
    @Override public Terminal term() { return term; }
    @Override public void gearChanged() { controlsChanged(); }
    @Override public String wiredName() { return "Permit Board"; }
    @Override public String gearState() { int n = Permits.get(world).live().size(); return n == 0 ? "no permits live" : n + " permit(s) LIVE"; }
    @Override public com.dogpound.railmap.grid.Panel wiredPanel() { return layout().all; }
    @Override public String wiredPress(String id, String who) { return IControlled.operate(this, id, null); }
    @Override public int wiredState(String id) { return controlValue(id); }
    @Override public void gearInfo(Map<String, Object> m) { m.put("live", Permits.get(world).live().size()); m.put("paper", paper); }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        ctl.write(t); term.write(t);
        t.setInteger("paper", paper); t.setInteger("sel", sel);
        NBTTagCompound inv = new NBTTagCompound();
        for (int i = 0; i < HOOKS; i++) if (!hooks.getStackInSlot(i).isEmpty()) inv.setTag(String.valueOf(i), hooks.getStackInSlot(i).writeToNBT(new NBTTagCompound()));
        t.setTag("hooks", inv);
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        ctl.read(t); term.read(t);
        if (!t.hasKey("term")) term.legacy = false;
        paper = t.getInteger("paper"); sel = t.getInteger("sel");
        NBTTagCompound inv = t.getCompoundTag("hooks");
        for (int i = 0; i < HOOKS; i++) hooks.setInventorySlotContents(i, inv.hasKey(String.valueOf(i)) ? new ItemStack(inv.getCompoundTag(String.valueOf(i))) : ItemStack.EMPTY);
    }

    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket() { return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(net.minecraft.network.NetworkManager n, net.minecraft.network.play.server.SPacketUpdateTileEntity p) { readFromNBT(p.getNbtCompound()); }
    @Override public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() { return new net.minecraft.util.math.AxisAlignedBB(pos).grow(1, 2, 1); }
}
