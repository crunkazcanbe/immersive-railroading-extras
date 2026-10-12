package com.dogpound.railmap.stnlife6;

import com.dogpound.railmap.block.StationControls;
import com.dogpound.railmap.block.TileFurniture;
import com.dogpound.railmap.stn.StnUtil;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Hotel room wardrobe + room safe (batch 6c #13): a hanging wardrobe and a 9-slot digital safe (real storage) and a
 * hat shelf that shows what is on it. It belongs to the nearest Room Lock (8 blocks): while the room is let, only the
 * holder of a valid key card for it opens either; vacant, housekeeping can. The safe's keypad runs on 12 V - dead,
 * it stays shut (fail secure); the wardrobe doors are just doors.
 */
public class TileRoomWardrobe extends TileFurniture {
    public TileRoomWardrobe() { super(new String[]{"hang", "safe"}, new String[]{"Wardrobe", "Room safe"}, new int[]{27, 9}, 3); }

    static double[][] SPOTS = {{3.0, 16.0, 10.0}, {8.0, 16.0, 10.0}, {13.0, 16.0, 10.0}};   // hat shelf, tools/gen_bb_batch6c.py
    @Override public double[][] shelfSpots() { return SPOTS; }
    @Override public String wiredName() { return "Room wardrobe"; }
    @Override public String gearState() { TileRoomLock r = lock(); return r == null ? "no room lock within 8 blocks" : "room " + r.room() + (r.occupied() ? " let" : " vacant"); }

    TileRoomLock lock() {
        TileRoomLock best = null;
        double bd = Double.MAX_VALUE;
        for (TileRoomLock r : StnUtil.near(world, TileRoomLock.class, pos, 8)) { double d = r.getPos().distanceSq(pos); if (d < bd) { bd = d; best = r; } }
        return best;
    }

    @Override
    protected String refuse(int k, EntityPlayer who) {
        TileRoomLock r = lock();
        if (k == 1 && !term.powered()) return "§cThe safe keypad is dark - no 12 V";
        if (r == null || !r.occupied() || who == null || who.capabilities.isCreativeMode) return null;
        return ItemRoomKey.holds(who, r.getPos(), r.serial, world.getTotalWorldTime()) ? null : "§cLocked - room " + r.room() + " is let (needs its key card)";
    }

    @Override protected String press(String id, EntityPlayer who) { return null; }

    @Override
    protected boolean refresh() {
        TileRoomLock r = lock();
        boolean ch = StationControls.put(ctl, "l_safe", r != null && r.occupied());
        ch |= StationControls.put(ctl, "pwr", term.powered());
        ch |= StationControls.text(ctl, "sd", r == null ? "NO ROOM" : r.occupied() ? "LOCKED " + r.room() : "OPEN");
        return ch;
    }
}
