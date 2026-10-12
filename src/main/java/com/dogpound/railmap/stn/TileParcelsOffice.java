package com.dogpound.railmap.stn;

import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.economy.Coins;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;

import java.util.*;

/**
 * Parcels Office (ChatGPT batch 3 #34): send things by train. Put them in the CONSIGN tray, pick the destination with
 * DEST < / >, press CONSIGN: each stack is labelled with a parcel number and waybill (one goes in the waybill drawer),
 * charged (config parcelFee, when Pride Realms money is present) and put on the DISPATCH shelves. The next driverless
 * train standing at this station whose line serves the destination (or, with no direct line, one that gets it to a
 * station that has one) takes it; at the destination station's Parcels Office it goes on the ARRIVED shelves, or is
 * transferred at the change. COLLECT gives you the arrived parcels and takes their labels off. The COUNTER key opens
 * the office (closed: trains neither take nor leave parcels here). The scale shelf shows what is on it. 12 V + mains.
 */
public class TileParcelsOffice extends StnFurniture {
    static final String TAG = "irxParcel";
    static double[][] SPOTS = {{-11.883, 7.0, 13.4}, {-10.25, 7.0, 13.4}, {-8.617, 7.0, 13.4}, {-6.983, 7.0, 13.4}, {-5.35, 7.0, 13.4}, {-3.717, 7.0, 13.4}};   // tools/gen_bb_stnsvc.py
    private int destI;
    private String station = "", dest = "";
    private long stationKey, destKey;
    private String last = "";

    public TileParcelsOffice() {
        super(new String[]{"in", "out", "arr", "dr"}, new String[]{"Consign tray", "Dispatch shelves (waiting for a train)", "Arrived parcels", "Waybill drawer"}, new int[]{9, 27, 27, 9}, 6);
    }

    @Override public String wiredName() { return "Parcels office"; }
    @Override public String gearState() { return !live() ? "NO SUPPLY" : (open() ? "OPEN" : "closed") + " · " + count(storage(1)) + " waiting · " + count(storage(2)) + " arrived"; }
    @Override public double[][] shelfSpots() { return SPOTS; }
    @Override protected boolean mechanical(String id) { return id.startsWith("k_"); }

    public boolean open() { return ctl.get("k_open") == 1 && live(); }
    public long stationKey() { return stationKey; }
    public IInventory dispatch() { return storage(1); }
    public IInventory arrived() { return storage(2); }

    static int count(IInventory inv) { int n = 0; for (int i = 0; i < inv.getSizeInventory(); i++) if (!inv.getStackInSlot(i).isEmpty()) n++; return n; }

    private List<Map.Entry<Long, String>> dests() {
        List<Map.Entry<Long, String>> l = new ArrayList<>(com.dogpound.railmap.server.StationData.get(world).names().entrySet());
        l.removeIf(e -> e.getKey() == stationKey);
        l.sort(Map.Entry.comparingByValue());
        return l;
    }

    @Override
    protected String pressMechanical(String id, EntityPlayer who) {
        ctl.set(id, ctl.get(id) == 1 ? 0 : 1);
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("station.shutter"), SoundCategory.BLOCKS, 0.7f, 1.1f);
        return ctl.get(id) == 1 ? "§aParcels office OPEN" : "§eParcels office CLOSED";
    }

    @Override
    protected String act(String id, EntityPlayer who) {
        switch (id) {
            case "dprev", "dnext" -> {
                List<Map.Entry<Long, String>> l = dests();
                if (l.isEmpty()) return "§7No other named stations";
                destI = Math.floorMod(destI + (id.equals("dnext") ? 1 : -1), l.size());
                return "§eTo " + l.get(destI).getValue() + (Parcels.reachable(world, stationKey, l.get(destI).getKey()) ? "" : " §c(no rail route from here)");
            }
            case "consign" -> {
                if (!open()) return "§cThe counter is closed (COUNTER key)";
                if (destKey == 0) return "§7Pick a destination";
                if (!Parcels.reachable(world, stationKey, destKey)) return "§cNo rail route from " + station + " to " + dest;
                IInventory in = storage(0);
                int n = 0, refused = 0;
                for (int i = 0; i < in.getSizeInventory(); i++) {
                    ItemStack s = in.getStackInSlot(i);
                    if (s.isEmpty()) continue;
                    if (!fits(dispatch(), s)) { refused++; continue; }
                    if (who instanceof EntityPlayerMP mp && Coins.available() && !Coins.charge(mp, StnConfig.parcelFee, "Parcel to " + dest)) { last = "CANNOT PAY"; return "§cYou can't pay the " + Coins.fmt(StnConfig.parcelFee) + " carriage"; }
                    StnData.Parcel p = StnData.get(world).newParcel(world, stationKey, station, destKey, dest, who(who), s);
                    ItemStack c = s.copy();
                    NBTTagCompound t = new NBTTagCompound();
                    t.setInteger("id", p.id); t.setString("to", dest); t.setString("from", station);
                    c.setTagInfo(TAG, t);
                    insert(dispatch(), c);
                    in.setInventorySlotContents(i, ItemStack.EMPTY);
                    ItemStack bill = new ItemStack(Items.PAPER);
                    bill.setStackDisplayName("Waybill #" + p.id + " " + station + " > " + dest);
                    insert(storage(3), bill);
                    n++;
                }
                if (n == 0) return refused > 0 ? "§cThe dispatch shelves are full" : "§7Put the parcels in the consign tray first";
                last = n + " TO " + StnUtil.up(dest);
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ui.stamp"), SoundCategory.BLOCKS, 0.8f, 1.0f);
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.PRINTER, SoundCategory.BLOCKS, 0.5f, 1.2f);
                Recorder.event(world, "Parcels: " + n + " consigned at " + station + " for " + dest + " by " + who(who));
                com.dogpound.railmap.api.QuestEvents.fire(world, who, pos, "parcel_consigned", "count", n, "to", dest);
                return "§a" + n + " parcel(s) consigned to " + dest + " - waybills in the drawer";
            }
            case "collect" -> {
                if (who == null) return "§7Collected in person";
                if (!open()) return "§cThe counter is closed";
                int n = 0;
                IInventory a = arrived();
                for (int i = 0; i < a.getSizeInventory(); i++) {
                    ItemStack s = a.getStackInSlot(i);
                    if (s.isEmpty()) continue;
                    ItemStack c = s.copy();
                    if (c.hasTagCompound() && c.getTagCompound().hasKey(TAG)) {
                        StnData.Parcel p = StnData.get(world).parcel(c.getTagCompound().getCompoundTag(TAG).getInteger("id"));
                        if (p != null) { p.state = 3; p.log.add(world.getTotalWorldTime() + "|collected at " + station + " by " + who.getName()); StnData.get(world).markDirty(); }
                        c.getTagCompound().removeTag(TAG);
                        if (c.getTagCompound().isEmpty()) c.setTagCompound(null);
                    }
                    if (!who.inventory.addItemStackToInventory(c)) who.dropItem(c, false);
                    a.setInventorySlotContents(i, ItemStack.EMPTY);
                    n++;
                }
                last = n + " COLLECTED";
                return n == 0 ? "§7No parcels waiting here" : "§aYou collected " + n + " parcel(s)";
            }
            case "print" -> {
                List<String> out = new ArrayList<>();
                out.add("PARCELS - " + station);
                for (StnData.Parcel p : StnData.get(world).parcels)
                    if (p.from == stationKey || p.to == stationKey || p.at == stationKey)
                        out.add("#" + p.id + " " + p.what + " " + p.fromName + ">" + p.toName + " " + StnData.PSTATE[p.state] + (p.state == 1 ? " " + p.trainName : ""));
                StnUtil.print(world, pos, controlFacing(), "Parcels book", station, out);
                return "§aParcels book printed";
            }
            case "weigh" -> {
                int items = 0;
                for (int i = 0; i < shelf.getSizeInventory(); i++) items += shelf.getStackInSlot(i).getCount();
                for (int i = 0; i < storage(0).getSizeInventory(); i++) items += storage(0).getStackInSlot(i).getCount();
                last = String.format(Locale.ROOT, "%.1f KG", items * 0.25);
                return "§eOn the scale + tray: " + last;
            }
            default -> { return null; }
        }
    }

    @Override
    protected void work() {
        if (age % 40 != 23) return;
        Map.Entry<Long, String> st = StnData.station(world, pos, 128);
        station = st == null ? "" : st.getValue();
        stationKey = st == null ? 0 : st.getKey();
        List<Map.Entry<Long, String>> l = dests();
        if (l.isEmpty()) { dest = ""; destKey = 0; } else { Map.Entry<Long, String> e = l.get(Math.floorMod(destI, l.size())); dest = e.getValue(); destKey = e.getKey(); }
    }

    @Override
    protected boolean refresh() {
        boolean ch = false;
        int transit = 0;
        for (StnData.Parcel p : StnData.get(world).parcels) if (p.state == 1 && (p.from == stationKey || p.to == stationKey)) transit++;
        boolean route = destKey != 0 && Parcels.reachable(world, stationKey, destKey);
        ch |= com.dogpound.railmap.block.StationControls.put(ctl, "pwr", term.powered());
        ch |= com.dogpound.railmap.block.StationControls.put(ctl, "openl", ctl.get("k_open") == 1);
        ch |= com.dogpound.railmap.block.StationControls.put(ctl, "arrl", count(arrived()) > 0);
        ch |= com.dogpound.railmap.block.StationControls.put(ctl, "waitl", count(dispatch()) > 0);
        ch |= com.dogpound.railmap.block.StationControls.put(ctl, "nodl", destKey != 0 && !route);
        ch |= com.dogpound.railmap.block.StationControls.text(ctl, "dest", destKey == 0 ? "NO DEST" : "TO " + StnUtil.up(StnUtil.cut(dest, 14)));
        ch |= com.dogpound.railmap.block.StationControls.text(ctl, "fee", Coins.fmt(StnConfig.parcelFee) + " EACH");
        ch |= com.dogpound.railmap.block.StationControls.text(ctl, "counts", "WAIT " + count(dispatch()) + " ARR " + count(arrived()) + " TRANSIT " + transit);
        ch |= com.dogpound.railmap.block.StationControls.text(ctl, "last", last);
        ch |= com.dogpound.railmap.block.StationControls.text(ctl, "sign", "PARCELS");
        ch |= com.dogpound.railmap.block.StationControls.text(ctl, "stn", StnUtil.up(StnUtil.cut(station, 16)));
        return ch;
    }

    @Override protected void save(NBTTagCompound t) { t.setInteger("destI", destI); t.setString("last", last); }
    @Override protected void load(NBTTagCompound t) { destI = t.getInteger("destI"); last = t.getString("last"); }
}
