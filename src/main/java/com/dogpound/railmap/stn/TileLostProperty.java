package com.dogpound.railmap.stn;

import com.dogpound.railmap.alarm.Recorder;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;

import java.util.*;

/**
 * Lost Property Office (ChatGPT batch 3 #34): a three-block counter with three FOUND racks, an UNCLAIMED store, a
 * claims drawer and a display shelf (what you put on it stands on it). With the SWEEP key on, staff pick up anything
 * left lying around the station (within 24 blocks, after config lostAfterSeconds) and hand it in: it is labelled
 * (number, who dropped it, where, when), logged in the register and put on a rack. CLAIM gives you back everything
 * that is yours; HAND IN logs the item in your hand as found property; after config lostKeepDays unclaimed items go
 * to the unclaimed store. PRINT gives the register as a book. BELL rings for the clerk. 12 V + mains.
 */
public class TileLostProperty extends StnFurniture {
    static final String TAG = "irxLost";
    static double[][] SPOTS = {{-12.088, 7.0, 13.4}, {-10.863, 7.0, 13.4}, {-9.638, 7.0, 13.4}, {-8.413, 7.0, 13.4}, {-7.188, 7.0, 13.4}, {-5.963, 7.0, 13.4}, {-4.738, 7.0, 13.4}, {-3.513, 7.0, 13.4}};   // tools/gen_bb_stnsvc.py
    private final Map<UUID, Long> seen = new HashMap<>();
    private String station = "";
    private int foundToday, claimedToday;
    private long day = -1;

    public TileLostProperty() {
        super(new String[]{"r1", "r2", "r3", "un", "dr"}, new String[]{"Found property rack 1", "Found property rack 2", "Found property rack 3", "Unclaimed store", "Claim forms drawer"},
                new int[]{27, 27, 27, 27, 9}, 8);
    }

    @Override public String wiredName() { return "Lost property office"; }
    @Override public String gearState() { return !live() ? "NO SUPPLY" : held() + " items held · sweep " + (ctl.get("k_sweep") == 1 ? "ON" : "off"); }
    @Override public double[][] shelfSpots() { return SPOTS; }
    @Override protected boolean mechanical(String id) { return id.startsWith("k_"); }

    private IInventory[] racks() { return new IInventory[]{storage(0), storage(1), storage(2)}; }

    int held() { int n = 0; for (IInventory r : racks()) for (int i = 0; i < r.getSizeInventory(); i++) if (!r.getStackInSlot(i).isEmpty()) n++; return n; }
    int unclaimed() { int n = 0; IInventory u = storage(3); for (int i = 0; i < u.getSizeInventory(); i++) if (!u.getStackInSlot(i).isEmpty()) n++; return n; }

    private boolean store(ItemStack s) {
        for (IInventory r : racks()) { s = insert(r, s); if (s.isEmpty()) return true; }
        return false;
    }

    /** label a stack as found property: its register number */
    private ItemStack label(ItemStack s, String owner) {
        ItemStack c = s.copy();
        int id = StnData.get(world).lostEntry(world, station, c.getCount() + " x " + c.getDisplayName(), owner);
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger("id", id); t.setString("owner", owner); t.setLong("found", world.getTotalWorldTime()); t.setString("at", station);
        c.setTagInfo(TAG, t);
        return c;
    }

    @Override
    protected String pressMechanical(String id, EntityPlayer who) {
        ctl.set(id, ctl.get(id) == 1 ? 0 : 1);
        return id.equals("k_sweep") ? (ctl.get(id) == 1 ? "§aStaff sweep the station for left property" : "§eSweep stopped") : null;
    }

    @Override
    protected String act(String id, EntityPlayer who) {
        switch (id) {
            case "claim" -> {
                if (who == null) return "§7Claims are made in person";
                int n = 0;
                for (IInventory r : new IInventory[]{storage(0), storage(1), storage(2), storage(3)})
                    for (int i = 0; i < r.getSizeInventory(); i++) {
                        ItemStack s = r.getStackInSlot(i);
                        if (s.isEmpty() || !s.hasTagCompound() || !s.getTagCompound().hasKey(TAG)) continue;
                        NBTTagCompound t = s.getTagCompound().getCompoundTag(TAG);
                        if (!t.getString("owner").equalsIgnoreCase(who.getName())) continue;
                        StnData.get(world).lostState(t.getInteger("id"), "CLAIMED " + who.getName());
                        ItemStack back = s.copy();
                        back.getTagCompound().removeTag(TAG);
                        if (back.getTagCompound().isEmpty()) back.setTagCompound(null);
                        if (!who.inventory.addItemStackToInventory(back)) who.dropItem(back, false);
                        r.setInventorySlotContents(i, ItemStack.EMPTY);
                        n++;
                    }
                claimedToday += n;
                ctl.texts.put("last", n == 0 ? "NOTHING FOR " + StnUtil.up(who.getName()) : n + " RETURNED");
                if (n > 0) { world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ui.stamp"), SoundCategory.BLOCKS, 0.7f, 1.0f); Recorder.event(world, "Lost property: " + n + " item(s) claimed by " + who.getName() + " at " + station); com.dogpound.railmap.api.QuestEvents.fire(world, who, pos, "lost_property_claimed", "count", n); }
                return n == 0 ? "§7Nothing handed in for " + who.getName() : "§aReturned " + n + " item(s) - sign here";
            }
            case "handin" -> {
                if (who == null || who.getHeldItemMainhand().isEmpty()) return "§7Hold the item you found";
                ItemStack s = who.getHeldItemMainhand();
                if (s.hasTagCompound() && s.getTagCompound().hasKey(TAG)) return "§7That is already labelled";
                if (!store(label(s, ""))) return "§cThe racks are full";
                who.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND, ItemStack.EMPTY);
                foundToday++;
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ui.paper"), SoundCategory.BLOCKS, 0.7f, 1.0f);
                return "§aHanded in - thank you";
            }
            case "print" -> {
                List<String> out = new ArrayList<>();
                out.add("LOST PROPERTY REGISTER - " + station);
                for (String l : StnData.get(world).lost) {
                    String[] p = l.split("\\|", -1);
                    if (p.length < 6 || !p[2].equals(station)) continue;
                    out.add("#" + p[0] + " " + com.dogpound.railmap.alarm.Recorder.clock(world, Long.parseLong(p[1])) + " " + p[3] + (p[4].isEmpty() ? "" : " (" + p[4] + ")") + " " + p[5]);
                }
                StnUtil.print(world, pos, controlFacing(), "Lost property register", station, out);
                return "§aRegister printed";
            }
            case "bell" -> { world.playSound(null, pos, com.dogpound.railmap.ModSounds.CASH, SoundCategory.BLOCKS, 0.7f, 1.4f); return "§7Ding! - the clerk is coming"; }
            default -> { return null; }
        }
    }

    @Override
    protected void work() {
        if (age % 100 != 31) return;
        Map.Entry<Long, String> st = StnData.station(world, pos, 128);
        station = st == null ? "Station" : st.getValue();
        long now = world.getTotalWorldTime(), d = now / 24000;
        if (d != day) { day = d; foundToday = claimedToday = 0; expire(now); }
        if (ctl.get("k_sweep") != 1 || !live()) { seen.clear(); return; }
        for (EntityItem e : world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(pos).grow(24, 8, 24))) {
            if (e.isDead || e.getItem().isEmpty()) continue;
            Long first = seen.putIfAbsent(e.getUniqueID(), now);
            if (first == null || now - first < StnConfig.lostAfterSeconds * 20L) continue;
            ItemStack s = e.getItem();
            if (s.hasTagCompound() && (s.getTagCompound().hasKey(TAG) || s.getTagCompound().hasKey(TileParcelsOffice.TAG))) continue;
            String owner = e.getThrower() == null ? "" : e.getThrower();
            if (!store(label(s, owner))) break;
            e.setDead();
            seen.remove(e.getUniqueID());
            foundToday++;
            Recorder.event(world, "Lost property found at " + station + ": " + s.getCount() + " x " + s.getDisplayName() + (owner.isEmpty() ? "" : " (dropped by " + owner + ")"));
            com.dogpound.railmap.api.QuestEvents.fire(world, null, pos, "lost_property_found", "item", s.getDisplayName(), "owner", owner);
        }
        seen.values().removeIf(t -> now - t > 12000);
    }

    /** items past the keeping time move to the unclaimed store */
    private void expire(long now) {
        for (IInventory r : racks())
            for (int i = 0; i < r.getSizeInventory(); i++) {
                ItemStack s = r.getStackInSlot(i);
                if (s.isEmpty() || !s.hasTagCompound() || !s.getTagCompound().hasKey(TAG)) continue;
                NBTTagCompound t = s.getTagCompound().getCompoundTag(TAG);
                if (now - t.getLong("found") < StnConfig.lostKeepDays * 24000L) continue;
                ItemStack left = insert(storage(3), s);
                r.setInventorySlotContents(i, left);
                if (left.isEmpty()) StnData.get(world).lostState(t.getInteger("id"), "UNCLAIMED");
            }
    }

    @Override
    protected boolean refresh() {
        boolean ch = false;
        int h = held(), u = unclaimed();
        ch |= com.dogpound.railmap.block.StationControls.put(ctl, "pwr", term.powered());
        ch |= com.dogpound.railmap.block.StationControls.put(ctl, "sweepl", ctl.get("k_sweep") == 1);
        ch |= com.dogpound.railmap.block.StationControls.put(ctl, "fulll", h >= 81);
        ch |= com.dogpound.railmap.block.StationControls.put(ctl, "unl", u > 0);
        ch |= com.dogpound.railmap.block.StationControls.put(ctl, "heldg", Math.min(100, h * 100 / 81));
        ch |= com.dogpound.railmap.block.StationControls.text(ctl, "held", h + " HELD  " + u + " UNCL");
        ch |= com.dogpound.railmap.block.StationControls.text(ctl, "today", "FOUND " + foundToday + " CLAIMED " + claimedToday);
        ch |= com.dogpound.railmap.block.StationControls.text(ctl, "stn", StnUtil.up(StnUtil.cut(station, 16)));
        ch |= com.dogpound.railmap.block.StationControls.text(ctl, "sign", "LOST PROPERTY");
        return ch;
    }

    @Override protected void save(NBTTagCompound t) { t.setInteger("ft", foundToday); t.setInteger("ct", claimedToday); t.setLong("day", day); t.setString("stn", station); }
    @Override protected void load(NBTTagCompound t) { foundToday = t.getInteger("ft"); claimedToday = t.getInteger("ct"); day = t.hasKey("day") ? t.getLong("day") : -1; station = t.getString("stn"); }
}
