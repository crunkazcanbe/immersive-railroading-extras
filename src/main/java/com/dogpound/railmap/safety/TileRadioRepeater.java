package com.dogpound.railmap.safety;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Map;

/**
 * Radio repeater (her ChatGPT batch-3 #17): a pole-mounted on-frequency repeater that fills a dead zone - a cutting,
 * a tunnel mouth, the far side of a hill. It is a cell of its own (RadioNet treats it like a mast: trains register on
 * it and it counts for train control), but it has no network link of its own: its donor antenna must hear a real
 * base station (a Radio Mast, never another repeater) above the -95 dBm threshold, worked out with the same path-loss
 * and terrain maths as the trains (RadioNet.rssi). No donor = off air. Smaller cell (48 / 80 / 128 m).
 */
public class TileRadioRepeater extends TileRadioMast {
    private String donor = "";
    private int donorDbm = Coverage.NONE;

    @Override public String pickName() { return "Radio repeater"; }
    @Override public String wiredName() { return "Radio repeater " + cell(); }
    @Override public String cell() { return "R" + Math.floorMod(pos.getX() * 31 + pos.getZ(), 1000); }
    @Override public int radius() { return new int[]{48, 80, 128}[Math.min(2, Math.max(0, ctl.get("s_power")))]; }
    @Override public double txDbm() { return new double[]{27, 33, 40}[Math.min(2, Math.max(0, ctl.get("s_power")))]; }

    public boolean donorOk() { return !donor.isEmpty() && donorDbm >= RadioNet.THRESHOLD; }

    @Override public boolean healthy() { return super.healthy() && donorOk(); }

    @Override public String gearState() {
        if (!powered()) return "OFF AIR - no 12 V";
        if (!donorOk()) return "OFF AIR - no donor cell (" + (donor.isEmpty() ? "no base station in range" : donor + " " + donorDbm + " dBm") + ")";
        return super.gearState() + " · donor " + donor + " " + donorDbm + " dBm";
    }
    @Override public void gearInfo(Map<String, Object> m) { super.gearInfo(m); m.put("donor", donor); m.put("donorDbm", donorDbm); }

    @Override
    protected void serverTick() {
        if (age % 20 == 2) {
            donor = "";
            donorDbm = Coverage.NONE;
            for (TileRadioMast m : SafetyTicker.radioMasts(world)) {
                if (m instanceof TileRadioRepeater || !m.healthy()) continue;
                int v = (int) Math.round(RadioNet.rssi(world, m, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5));   // the donor yagi up the pole
                if (v > donorDbm) { donorDbm = v; donor = m.cell(); }
            }
            put("dlink", donorOk());
            put("dbar", donorDbm == Coverage.NONE ? 0 : Math.max(0, Math.min(100, (donorDbm + 120) * 2)));
            text("donor", donor.isEmpty() ? "NO DONOR" : donor + " " + donorDbm + "DBM");
        }
        super.serverTick();
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (id.equals("s_power")) {
            ctl.set("s_power", (ctl.get("s_power") + 1) % 3);
            return "§eRepeater output " + new String[]{"0.5 W", "2 W", "10 W"}[ctl.get("s_power")] + " - cell radius " + radius() + " m";
        }
        return super.controlPress(id, who);
    }

    @Override protected void save(NBTTagCompound t) { }
    @Override protected void load(NBTTagCompound t) { }
}
