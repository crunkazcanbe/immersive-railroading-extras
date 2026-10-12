package com.dogpound.railmap.stnlife6;

import com.dogpound.railmap.stn.StnData;
import com.dogpound.railmap.stn.StnUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

/**
 * Bus bay stop pole (batch 6c #11): the ROUTE and STOP thumbwheels put this bay on a route (1-4) at a place in its stop
 * order (1-8); the departure board strings a route's bays together in stop order. Bay letter selector A-H, OUT OF USE
 * key (buses run past), countdown display (route, destination, minutes) fed by the board, AT BAY / DUE lamps and a
 * REQUEST button that holds the next bus 10 s longer. Buses stop on the road cell nearest the pole. 12 V + mains.
 */
public class TileBusBay extends LifeTile {
    static final String[] LETTERS = {"A", "B", "C", "D", "E", "F", "G", "H"};
    private BlockPos road;
    private String place = "";
    private int calls;
    boolean requested;

    public int route() { return ctl.get("s_route") + 1; }
    public int seq() { return ctl.get("s_seq") + 1; }
    public String letter() { return LETTERS[Math.floorMod(ctl.get("s_bay"), LETTERS.length)]; }
    public boolean inUse() { return ctl.get("k_out") == 0 && live(); }
    public BlockPos road() { return road; }
    public String stopName() { return (place.isEmpty() ? "Stop " + pos.getX() + "," + pos.getZ() : place) + " bay " + letter(); }

    @Override public String wiredName() { return "Bus bay " + letter() + " " + pos.getX() + "," + pos.getZ(); }
    @Override public String gearState() { return !live() ? "NO SUPPLY" : "route " + route() + " stop " + seq() + (ctl.get("k_out") == 1 ? " - OUT OF USE" : "") + (road == null ? " - NO ROAD next to the pole" : ""); }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (id.equals("req")) {
            String d = dead(); if (d != null) return d;
            requested = true; calls++;
            put("l_req", 1);
            return "§aBus requested - the next route " + route() + " bus will wait for you";
        }
        return null;
    }

    /** the board: what to show (null = no service) */
    void show(String l1, String l2, boolean due, boolean at) {
        text("d1", l1 == null ? "NO SERVICE" : l1);
        text("d2", l2 == null ? "" : l2);
        put("l_due", due ? 1 : 0);
        put("l_at", at ? 1 : 0);
        if (at && requested) { requested = false; put("l_req", 0); }
    }

    @Override
    protected void work() {
        if (age % 40 != 7) return;
        if (road == null || age % 400 == 7) road = Roads.near(world, pos, 4);
        Map.Entry<Long, String> st = StnData.station(world, pos, 96);
        place = st == null ? "" : st.getValue();
        text("name", "BAY " + letter() + " ROUTE " + route() + " STOP " + seq());
        text("place", StnUtil.up(StnUtil.cut(place.isEmpty() ? "BUS STOP" : place, 18)));
        put("l_road", road == null ? 1 : 0);
        put("l_out", ctl.get("k_out"));
        if (TileBusBoard.boardFor(world, pos) == null) show(null, "NO DEPARTURE BOARD", false, false);
    }

    @Override protected void write(NBTTagCompound t) { t.setInteger("calls", calls); t.setBoolean("req", requested); }
    @Override protected void read(NBTTagCompound t) { calls = t.getInteger("calls"); requested = t.getBoolean("req"); }
}
