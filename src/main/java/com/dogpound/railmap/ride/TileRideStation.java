package com.dogpound.railmap.ride;

import com.dogpound.railmap.grid.Elec;
import com.dogpound.railmap.infra.Supply;
import net.minecraft.entity.player.EntityPlayer;

import java.util.Locale;

/**
 * A ropeway / funicular station (the end of a line - the Haul Rope Spool strings the rope from one to the other). Its
 * attendant pedestal: platform gates OPEN / CLOSE (interlocked: never with the line moving), READY (MANUAL mode wants
 * both stations ready), HOLD (gondola: keeps cabins from launching), the station E-stop, lamps (gates closed, car in,
 * line running, fault, ready, hold) and the departure display. 12 V for the safety loop and controls (no 12 V = its
 * E-stop loop is open), 120 / 240 V AC for the gate operators and lighting.
 */
public class TileRideStation extends RideTile {
    public TileRideStation() { super(new Supply(Elec.Level.AC120, Elec.Level.AC240)); }

    @Override public String wiredName() { return "Ropeway station " + pos.getX() + "," + pos.getZ(); }
    @Override protected boolean owns(RideData.Line l) { return l.stA == pos.toLong() || l.stB == pos.toLong(); }
    @Override protected boolean free(RideData.Line l) { return owns(l); }              // stations join a line only by the spool (this finds it again)
    @Override protected void claim(RideData.Line l) { }

    public int end(RideData.Line l) { return l.stB == pos.toLong() ? 1 : 0; }

    @Override
    public String gearState() {
        RideData.Line l = line();
        return mains.status().replaceAll("§.", "") + (l == null ? " · no rope - string one with the Haul Rope Spool" : " · " + l.label() + " station " + (end(l) == 0 ? "A" : "B") + " · " + RideSim.state(l));
    }

    @Override public int wiredLamp() { RideData.Line l = line(); return !live() ? 3 : l != null && l.faults != 0 ? 2 : 1; }

    @Override
    protected void serverTick() {
        RideData.Line l = line();
        if (age % 20 == 9) mainsTick(l == null ? 150 : 900);
        if (l == null) { if (age % 10 == 0) { put("l_closed", false); text("d_dep", powered() ? "NO ROPE" : ""); } return; }
        int k = end(l), bit = k == 0 ? 4 : 8;
        l.estops = ctl.get("m_estop") == 1 || !powered() ? l.estops | bit : l.estops & ~bit;
        if (age % 5 != 0) return;
        boolean on = powered();
        boolean carIn = false;
        for (int i = 0; i < l.cars(); i++) if (l.atStation(i) == k && (l.shuttle() ? l.atEnd() : !l.geom().gripped(l.carCoord(i)))) carIn = true;
        put("l_closed", on && !l.gate[k]);
        put("l_open", on && l.gate[k]);
        put("l_car", on && carIn);
        put("l_run", on && l.running);
        put("l_flt", on && l.faults != 0);
        put("l_ready", on && l.ready[k]);
        put("l_hold", on && l.hold[k]);
        text("d_dep", TileRideDesk.nextDeparture(l));
        text("d_line", (l.label() + " " + (k == 0 ? "A" : "B")).toUpperCase(Locale.ROOT));
    }

    @Override
    public String controlPress(String id, EntityPlayer p) {
        if (id.equals("test")) return lampTest();
        if (id.equals("m_estop")) return null;
        RideData.Line l = line();
        if (l == null) return "§7This station has no rope yet - string one with the Haul Rope Spool";
        if (!powered()) return "§cStation panel dark - no 12 V";
        int k = end(l);
        switch (id) {
            case "dopen":
                if (!mains.ok) return "§cGate operators have no mains: " + mains.what;
                if (Math.abs(l.v) > 0.01) return "§cInterlocked - the line is moving";
                if (l.shuttle() && !l.atEnd()) return "§cNo car in the station";
                l.gate[k] = true;
                if (l.shuttle()) { l.phase = RideData.DWELL; l.dwell = Math.max(l.dwell, 200); }
                return "§eGates open";
            case "dclose":
                if (!mains.ok) return "§cGate operators have no mains: " + mains.what;
                l.gate[k] = false;
                return "§aGates closed";
            case "ready":
                l.ready[k] = !l.ready[k];
                return l.ready[k] ? "§aStation " + (k == 0 ? "A" : "B") + " READY" : "§eReady cancelled";
            case "hold":
                l.hold[k] = !l.hold[k];
                return l.hold[k] ? "§6Cabins held in the station" : "§aCabin launch released";
            default: return null;
        }
    }

}
