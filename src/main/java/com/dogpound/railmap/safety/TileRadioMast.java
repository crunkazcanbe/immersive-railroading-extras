package com.dogpound.railmap.safety;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Map;

/**
 * Radio Mast (her ChatGPT list #21): a GSM-R style base station - lattice mast, antenna, equipment cabinet. Its cell
 * covers a planned radius set by the transmitter power selector (1 W 96 m, 5 W 160 m, 25 W 256 m); trains inside it
 * register and report. The cabinet shows the power, transmit and fault lamps, how many trains are on the cell and the
 * weakest signal it hears. Needs 12 V: without it (or burnt, or cut by the fault lab) the cell is off air and every
 * train relying on it loses its link - the fallback in the fail-safe configuration then applies.
 */
public class TileRadioMast extends SafetyTile implements IPickable {
    /** trains on this cell (RadioNet fills it each second) */
    int linked;
    private int worst = 0;

    @Override public String pickName() { return "Radio mast"; }
    @Override public String wiredName() { return "Radio mast " + cell(); }

    public String cell() { return "C" + Math.floorMod(pos.getX() * 31 + pos.getZ(), 1000); }

    public int radius() { return new int[]{96, 160, 256}[Math.min(2, Math.max(0, ctl.get("s_power")))]; }

    public double txDbm() { return new double[]{30, 37, 44}[Math.min(2, Math.max(0, ctl.get("s_power")))]; }

    public boolean healthy() { return powered() && !term.burnt && !FaultLab.has(world, FaultLab.Kind.RADIO_CUT, pos); }

    @Override public String gearState() { return !powered() ? "OFF AIR - no 12 V" : !healthy() ? "OFF AIR - link cut (fault lab)" : "on air · " + linked + " train(s) · " + radius() + " m cell"; }
    @Override public int wiredLamp() { return healthy() ? 1 : 3; }
    @Override public void gearInfo(Map<String, Object> m) { m.put("cell", cell()); m.put("trains", linked); m.put("radius", radius()); m.put("onAir", healthy()); }

    @Override
    protected void serverTick() {
        if (age % 20 != 3) return;
        boolean ok = healthy();
        worst = 0;
        for (SafetyData.TrainRec r : SafetyData.get(world).trains.values())
            if (!r.gone && r.radio == 1 && r.mast.equals(cell())) worst = Math.min(worst, r.rssi);
        put("pwr", powered());
        put("tx", ok);
        put("fault", powered() && !ok);
        put("links", ok ? linked : 0);
        text("cell", cell() + " " + radius() + "M");
        text("rssi", ok && worst < 0 ? worst + " DBM" : "--");
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (id.equals("test")) return lampTest();
        if (id.equals("s_power")) {
            ctl.set("s_power", (ctl.get("s_power") + 1) % 3);
            return "§eTransmitter " + new String[]{"1 W", "5 W", "25 W"}[ctl.get("s_power")] + " - cell radius " + radius() + " m";
        }
        return "§7" + gearState();
    }

    @Override protected void save(NBTTagCompound t) { }
    @Override protected void load(NBTTagCompound t) { }
}
