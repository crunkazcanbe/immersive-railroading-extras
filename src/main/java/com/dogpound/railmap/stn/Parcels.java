package com.dogpound.railmap.stn;

import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.RailwayData;
import net.minecraft.item.ItemStack;
import net.minecraft.inventory.IInventory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Parcels by train (ChatGPT batch 3 #34): when a driverless train starts its stop at a station with an open Parcels
 * Office, parcels for this station come off it (onto the ARRIVED shelves; or onto the DISPATCH shelves when they still
 * have to change lines here), then parcels waiting here go on if its line serves their destination - or, when no line
 * from here does, if it reaches a station that has one. Parcels on a train that stops being a driverless train go back
 * to their origin office. The goods travel inside the parcel record (StnData), so nothing is lost if chunks unload.
 */
public final class Parcels {
    private Parcels() {}

    private static final Map<UUID, Long> DWELL = new HashMap<>();

    static boolean serves(RailwayData rd, String line, long st) { RailwayData.Line l = rd.line(line); return l != null && l.stations.contains(st); }

    /** is there a line from a that reaches b, directly or with one change */
    public static boolean reachable(World w, long a, long b) {
        if (a == 0 || b == 0) return false;
        RailwayData rd = RailwayData.get(w);
        for (RailwayData.Line l : rd.lines().values()) {
            if (!l.stations.contains(a)) continue;
            if (l.stations.contains(b)) return true;
            for (long c : l.stations) for (RailwayData.Line m : rd.lines().values()) if (m != l && m.stations.contains(c) && m.stations.contains(b)) return true;
        }
        return false;
    }

    public static boolean directFrom(RailwayData rd, long st, long to) { for (RailwayData.Line l : rd.lines().values()) if (l.stations.contains(st) && l.stations.contains(to)) return true; return false; }

    /** does this line get a parcel at `st` closer: serves `to`, or (no direct line here) reaches a station that has one */
    public static boolean useful(RailwayData rd, String line, long st, long to) {
        if (serves(rd, line, to)) return true;
        if (directFrom(rd, st, to)) return false;
        RailwayData.Line l = rd.line(line);
        if (l == null) return false;
        for (long c : l.stations) if (c != st && directFrom(rd, c, to)) return true;
        return false;
    }

    static List<TileParcelsOffice> offices(World w, long st) {
        List<TileParcelsOffice> o = new ArrayList<>();
        for (TileParcelsOffice t : StnUtil.near(w, TileParcelsOffice.class, BlockPos.fromLong(st), 128)) if (t.stationKey() == st && t.open()) o.add(t);
        return o;
    }

    static void tick(World w) {
        RailwayData rd = RailwayData.get(w);
        StnData d = StnData.get(w);
        long now = w.getTotalWorldTime();
        for (RailwayData.AutoTrain a : rd.trains().values()) {
            Autopilot.Run r = Autopilot.run(a.loco);
            long here = r != null && r.dwelling ? r.dwellStation : Long.MIN_VALUE;
            Long was = DWELL.put(a.loco, here);
            if (here == Long.MIN_VALUE || was != null && was == here) continue;
            List<TileParcelsOffice> offs = offices(w, here);
            if (offs.isEmpty()) continue;
            String stName = Autopilot.nameOf(com.dogpound.railmap.server.StationData.get(w), here);
            String train = a.label.isEmpty() ? "train" : a.label;
            // off
            for (StnData.Parcel p : d.parcels) {
                if (p.state != 1 || !a.loco.equals(p.train)) continue;
                boolean dest = p.to == here, change = !dest && !useful(rd, a.line, here, p.to) && directFrom(rd, here, p.to);
                if (!dest && !change) continue;
                for (TileParcelsOffice o : offs) {
                    IInventory inv = dest ? o.arrived() : o.dispatch();
                    if (!StnFurniture.fits(inv, p.stack)) continue;
                    StnFurniture.insert(inv, p.stack);
                    p.stack = ItemStack.EMPTY; p.train = null; p.at = here; p.moved = now;
                    p.state = dest ? 2 : 0;
                    p.log.add(now + "|" + (dest ? "arrived at " : "off at ") + stName + " from " + train + (dest ? "" : " to change"));
                    Recorder.event(w, "Parcel #" + p.id + " " + (dest ? "arrived at " : "transferred at ") + stName + " (" + p.what + ")");
                    if (dest) com.dogpound.railmap.api.QuestEvents.fire(w, null, o.getPos(), "parcel_delivered", "id", p.id, "from", p.fromName, "to", p.toName);
                    d.markDirty();
                    break;
                }
            }
            // on
            for (TileParcelsOffice o : offs) {
                IInventory inv = o.dispatch();
                for (int i = 0; i < inv.getSizeInventory(); i++) {
                    ItemStack s = inv.getStackInSlot(i);
                    if (s.isEmpty() || !s.hasTagCompound() || !s.getTagCompound().hasKey(TileParcelsOffice.TAG)) continue;
                    StnData.Parcel p = d.parcel(s.getTagCompound().getCompoundTag(TileParcelsOffice.TAG).getInteger("id"));
                    if (p == null || p.state != 0 || !useful(rd, a.line, here, p.to)) continue;
                    p.stack = s.copy(); p.state = 1; p.train = a.loco; p.trainName = train; p.at = 0; p.moved = now;
                    p.log.add(now + "|loaded on " + train + " (" + a.line + ") at " + stName);
                    inv.setInventorySlotContents(i, ItemStack.EMPTY);
                    d.markDirty();
                }
            }
        }
        DWELL.keySet().removeIf(u -> !rd.trains().containsKey(u));
        // parcels stranded on trains that are no longer driverless go home
        for (StnData.Parcel p : d.parcels) {
            if (p.state != 1 || p.train == null || rd.trains().containsKey(p.train)) continue;
            for (TileParcelsOffice o : StnUtil.near(w, TileParcelsOffice.class, BlockPos.fromLong(p.from), 128)) {
                if (o.stationKey() != p.from || !StnFurniture.fits(o.dispatch(), p.stack)) continue;
                StnFurniture.insert(o.dispatch(), p.stack);
                p.stack = ItemStack.EMPTY; p.state = 0; p.at = p.from; p.train = null;
                p.log.add(now + "|returned to " + p.fromName + " (its train left service)");
                d.markDirty();
                break;
            }
        }
    }
}
