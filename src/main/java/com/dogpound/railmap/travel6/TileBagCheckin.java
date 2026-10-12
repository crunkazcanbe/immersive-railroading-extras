package com.dogpound.railmap.travel6;

import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.block.StationControls;
import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.server.StationData;
import com.dogpound.railmap.stn.Parcels;
import com.dogpound.railmap.stn.StnFurniture;
import com.dogpound.railmap.stn.StnUtil;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Baggage check-in desk (batch 6b #9). Put what you are checking on the BAG DROP scale (a real 9-slot store), pick the
 * destination with DEST < > (stations a train can get it to), WEIGH, then TAG: the bag is labelled, you get the claim
 * tag, and the suitcase goes onto the baggage belt behind the desk (a Baggage Belt block at the back) - or into the
 * desk's bag hold when there is no belt. Trains with a baggage car calling here take bags from the hold (and from a
 * Baggage Hall's make-up cart); at the destination they come off onto the reclaim carousel - or this desk's hold, where
 * CLAIM gives a bag back for its tag. One stack is free, each extra stack costs bagPerStack; over 8 stacks is refused.
 * 12 V control + mains (scale, tag printer, belt drive).
 */
public class TileBagCheckin extends StnFurniture {
    private int dcur;
    private long station = Long.MIN_VALUE, testUntil;
    private String stationName = "", lastMsg = "";
    private int checkedToday;
    private long dayNo = -1;

    public TileBagCheckin() { super(new String[]{"drop", "hold"}, new String[]{"Bag drop scale", "Bag hold"}, new int[]{9, 18}, 1); }

    @Override public String wiredName() { return "Baggage check-in"; }
    @Override public String gearState() { return !live() ? "NO SUPPLY" : checkedToday + " bags today · " + bagsIn(hold()) + " in hold"; }
    public long stationKey() { return station; }
    IInventory drop() { return storage(0); }
    IInventory hold() { return storage(1); }

    static int bagsIn(IInventory inv) { int n = 0; for (int i = 0; i < inv.getSizeInventory(); i++) if (ItemTravel.bagId(inv.getStackInSlot(i)) != 0) n++; return n; }

    private List<Long> destCache = new ArrayList<>();

    List<Long> dests() { return destCache; }

    private List<Long> findDests() {
        List<Long> o = new ArrayList<>();
        if (station == Long.MIN_VALUE) return o;
        for (Long k : StationData.get(world).names().keySet()) if (k != station && Parcels.reachable(world, station, k)) o.add(k);
        o.sort(Comparator.comparing(k -> StationData.get(world).names().getOrDefault(k, "")));
        return o;
    }

    Long dest() { List<Long> l = dests(); if (l.isEmpty()) return null; dcur = Math.floorMod(dcur, l.size()); return l.get(dcur); }

    int stacks() { int n = 0; for (int i = 0; i < drop().getSizeInventory(); i++) if (!drop().getStackInSlot(i).isEmpty()) n++; return n; }
    int items() { int n = 0; for (int i = 0; i < drop().getSizeInventory(); i++) n += drop().getStackInSlot(i).getCount(); return n; }
    int kg() { return stacks() == 0 ? 0 : 2 + (items() + 7) / 8; }
    long fee() { return Math.max(0, stacks() - 1) * (long) TravelConfig.bagPerStack; }

    /** the belt behind the desk, if one is laid there */
    BlockPos beltPos() {
        EnumFacing f = controlFacing();
        BlockPos b = pos.offset(f.getOpposite());
        return world.getTileEntity(b) instanceof TileBagBelt ? b : null;
    }

    @Override public int controlValue(String id) { return live() && testUntil > 0 && !mechanical(id) ? Math.max(1, ctl.get(id)) : super.controlValue(id); }

    @Override
    protected String act(String id, EntityPlayer who) {
        String r = act0(id, who);
        if (r != null && !r.isEmpty()) lastMsg = net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(r);
        return r;
    }

    private String act0(String id, EntityPlayer who) {
        long now = world.getTotalWorldTime();
        switch (id) {
            case "test": testUntil = now + 60; return "§7Lamp test";
            case "dprev": dcur--; return "";
            case "dnext": dcur++; return "";
            case "weigh": world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ui.beep_ok"), SoundCategory.BLOCKS, 0.5f, 1.3f); return stacks() == 0 ? "§7The scale is empty - put your things on the BAG DROP" : "§7" + kg() + " kg, " + stacks() + " stack(s) - fee " + Coins.fmt(fee());
            case "tag": return tag(who, now);
            case "claim": return Baggage.claim(world, who, hold(), stationName);
            case "send": {
                BlockPos b = beltPos();
                if (b == null) return "§7No baggage belt behind the desk - bags wait in the hold for the train";
                int n = 0;
                for (int i = 0; i < hold().getSizeInventory(); i++) {
                    ItemStack s = hold().getStackInSlot(i);
                    TravelData.Bag bag = TravelData.get(world).bags.get(ItemTravel.bagId(s));
                    if (bag == null || bag.state != TravelData.CHECKED) continue;
                    onBelt(b, s.copy());
                    hold().setInventorySlotContents(i, ItemStack.EMPTY);
                    n++;
                }
                return n == 0 ? "§7No outbound bags in the hold" : "§a" + n + " bag(s) sent down the belt";
            }
            default: return null;
        }
    }

    private void onBelt(BlockPos b, ItemStack s) {
        EntityItem e = new EntityItem(world, b.getX() + 0.5, b.getY() + 0.45, b.getZ() + 0.5, s);
        e.motionX = e.motionY = e.motionZ = 0;
        e.setInfinitePickupDelay();
        e.setNoDespawn();
        world.spawnEntity(e);
    }

    private String tag(EntityPlayer who, long now) {
        if (!(who instanceof EntityPlayerMP p)) return "§7Check in at the desk";
        if (station == Long.MIN_VALUE) return "§cThis desk isn't at a station - name a station nearby";
        if (stacks() == 0) return "§7Put what you are checking on the BAG DROP scale first";
        if (stacks() > 8) return "§cToo heavy - one bag takes at most 8 stacks";
        Long to = dest();
        if (to == null) return "§cNo station a train can take a bag to from here";
        BlockPos belt = beltPos();
        if (belt == null && !StnFurniture.fits(hold(), ItemStack.EMPTY)) return "§cThe bag hold is full - lay a belt or wait for a train";
        long fee = fee();
        if (!Coins.charge(p, fee, "Checked baggage")) return "§cThe excess baggage fee is " + Coins.fmt(fee) + " - your bank can't cover it";
        TravelData d = TravelData.get(world);
        TravelData.Bag b = d.newBag(now);
        b.owner = who.getUniqueID(); b.ownerName = who.getName();
        b.fromKey = station; b.fromName = stationName; b.toKey = to; b.toName = StationData.get(world).names().getOrDefault(to, "?");
        for (int i = 0; i < drop().getSizeInventory(); i++) { ItemStack s = drop().getStackInSlot(i); if (!s.isEmpty()) b.items.add(s.copy()); drop().setInventorySlotContents(i, ItemStack.EMPTY); }
        b.note(Recorder.clock(world, now) + " checked in at " + stationName + " for " + b.toName + " (" + b.weightKg() + " kg)");
        d.bagFees += fee;
        d.note(Recorder.clock(world, now) + " bag " + b.tag + " checked in at " + stationName + " > " + b.toName + " by " + who.getName());
        ItemStack tag = ItemTravel.claimTag(b);
        if (!who.inventory.addItemStackToInventory(tag)) who.dropItem(tag, false);
        ItemStack bag = ItemTravel.suitcase(b);
        if (belt != null) onBelt(belt, bag);
        else StnFurniture.insert(hold(), bag);
        long day = world.getWorldTime() / 24000L;
        if (day != dayNo) { dayNo = day; checkedToday = 0; }
        checkedToday++;
        world.playSound(null, pos, com.dogpound.railmap.ModSounds.PRINTER, SoundCategory.BLOCKS, 0.5f, 1.2f);
        return "§aBag " + b.tag + " checked to " + b.toName + (fee > 0 ? " (" + Coins.fmt(fee) + ")" : "") + " - keep your claim tag" + (belt != null ? "; it's on the belt" : "; it waits in the hold for a train");
    }

    @Override
    protected void work() {
        long now = world.getTotalWorldTime();
        if (testUntil > 0 && now > testUntil) { testUntil = 0; controlsChanged(); }
        if (age % 100 == 11) {
            Map.Entry<Long, String> st = Cards.stationAt(world, pos);
            long k = st == null ? Long.MIN_VALUE : st.getKey();
            if (k != station) { station = k; stationName = st == null ? "" : st.getValue(); markDirty(); }
            destCache = findDests();
        }
    }

    @Override
    protected boolean refresh() {
        Long to = dest();
        String[] l = new String[6];
        l[0] = station == Long.MIN_VALUE ? "NO STATION NEARBY" : "CHECK-IN " + StnUtil.up(StnUtil.cut(stationName, 18));
        l[1] = "TO " + (to == null ? "-" : StnUtil.up(StnUtil.cut(StationData.get(world).names().getOrDefault(to, "?"), 18)));
        l[2] = stacks() == 0 ? "PLACE BAG ON SCALE" : kg() + " KG  " + stacks() + " STACKS";
        l[3] = stacks() > 8 ? "TOO HEAVY - MAX 8 STACKS" : "FEE " + Coins.fmt(fee());
        l[4] = "HOLD " + bagsIn(hold()) + " BAGS" + (beltPos() != null ? "  BELT OK" : "  NO BELT");
        l[5] = checkedToday + " CHECKED TODAY";
        boolean ch = false;
        for (int i = 0; i < 6; i++) ch |= StationControls.text(ctl, "s" + (i + 1), l[i]);
        ch |= StationControls.text(ctl, "msg", cut(lastMsg, 34));
        ch |= StationControls.text(ctl, "kgv", stacks() == 0 ? "0.0" : kg() + ".0");
        ch |= StationControls.put(ctl, "scale", Math.min(32, kg())) | StationControls.put(ctl, "heavy", stacks() > 8) | StationControls.put(ctl, "beltl", beltPos() != null)
                | StationControls.put(ctl, "holdl", bagsIn(hold()) > 0) | StationControls.put(ctl, "pwr", term.powered()) | StationControls.put(ctl, "nhold", bagsIn(hold()));
        return ch;
    }

    @Override protected void save(NBTTagCompound t) { t.setInteger("dc", dcur); t.setLong("st", station); t.setString("stn", stationName); t.setInteger("ct", checkedToday); t.setLong("dn", dayNo); }
    @Override protected void load(NBTTagCompound t) { dcur = t.getInteger("dc"); station = t.hasKey("st") ? t.getLong("st") : Long.MIN_VALUE; stationName = t.getString("stn"); checkedToday = t.getInteger("ct"); dayNo = t.getLong("dn"); }
}
