package com.dogpound.railmap.freight6;

import cam72cam.immersiverailroading.entity.Freight;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Locale;

/**
 * Warehouse Dock Door (batch 6 #28): a rail dock position along the siding - insulated roll-up door in its guides with
 * the drum housing and motor, a hinged-lip dock leveller, dock shelter pads, the inside / outside traffic lights, dock
 * lamp on its swing arm and the door control station. OPEN / CLOSE / STOP run the door motor (it really travels; the
 * forklifts can't pass a shut door). The LEVELLER lever drops the lip into the car, the DERAIL / blue-flag key protects
 * the car from being moved while people work in it. SECURED (green light) = door open + lip down + derail on + a car
 * standing at the dock - only then do the forklifts unload or load it. Mains (door motor) + 12 V.
 */
public class TileWarehouseDock extends com.dogpound.railmap.depot.DepotTile {
    private float door;            // 0 shut .. 100 open
    private int dir;               // -1 closing, 0 still, 1 opening

    public TileWarehouseDock() { super(new String[]{"chocks"}, new String[]{"Dock box (wheel chocks, blue flags, straps)"}, new int[]{9}); }

    @Override public String wiredName() { return "Warehouse dock door"; }
    @Override protected boolean usesMains() { return true; }
    @Override protected long load() { return dir != 0 ? 40 : 2; }
    @Override public String gearState() { return !powered() ? "NO 12 V" : String.format(Locale.ROOT, "door %d %% · %s%s", Math.round(door), secured() ? "SECURED" : "not secured", car() == null ? "" : " · " + Cars.name(car())); }

    public float door() { return door; }
    public boolean doorOpen() { return door >= 99; }
    public boolean leveller() { return ctl.get("m_lev") == 1; }
    public boolean derail() { return ctl.get("k_derail") == 1; }
    public Freight car() { return Cars.goods(world, pos, 6); }
    public boolean secured() { return doorOpen() && leveller() && derail() && car() != null; }

    @Override
    protected String press(String id, EntityPlayer who) {
        switch (id) {
            case "open" -> { if (ctl.get("m_estop") == 1) return "§cE-STOP is in"; if (!mainsOk()) return "§cNo mains for the door motor (" + mains.what + ")"; dir = 1; return "§aDoor opening"; }
            case "close" -> {
                if (ctl.get("m_estop") == 1) return "§cE-STOP is in";
                if (leveller()) return "§cRaise the dock leveller lip first";
                if (!mainsOk()) return "§cNo mains for the door motor (" + mains.what + ")";
                dir = -1; return "§eDoor closing";
            }
            case "dstop" -> { dir = 0; return "§7Door stopped at " + Math.round(door) + " %"; }
            default -> { return null; }
        }
    }

    @Override
    protected String mechanical(String id, EntityPlayer who) {
        switch (id) {
            case "m_estop" -> { ctl.set(id, ctl.get(id) == 1 ? 0 : 1); if (ctl.get(id) == 1) dir = 0; return ctl.get(id) == 1 ? "§cE-STOP - door motor off" : "§aE-stop released"; }
            case "m_lev" -> {
                if (ctl.get(id) == 0 && !doorOpen()) return "§cOpen the door before lowering the lip";
                if (ctl.get(id) == 0 && car() == null) return "§cNo car at the dock for the lip to rest on";
                ctl.set(id, ctl.get(id) == 1 ? 0 : 1);
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ctl.lever"), net.minecraft.util.SoundCategory.BLOCKS, 0.8f, 0.6f);
                return ctl.get(id) == 1 ? "§eLeveller lip down in the car" : "§7Leveller stored";
            }
            default -> { return null; }
        }
    }

    @Override
    protected void work() {
        if (dir != 0) {
            if (!powered() || !mainsOk() || ctl.get("m_estop") == 1) dir = 0;
            else {
                door = Math.max(0, Math.min(100, door + dir * 1.25f));
                if (age % 5 == 0) put("doorg", Math.round(door));
                if (door <= 0 || door >= 100) { dir = 0; put("doorg", Math.round(door)); world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev(door >= 100 ? "machine.door_open" : "machine.door_close"), net.minecraft.util.SoundCategory.BLOCKS, 0.7f, 0.8f); }
            }
        }
        if (leveller() && car() == null && age % 20 == 0) { ctl.set("m_lev", 0); controlsChanged(); }   // the car pulled away: the lip falls back to the stored position
        if (age % 20 != 0) return;
        Freight c = car();
        int n = Warehouse.docks(world, pos).indexOf(this);
        put("pwr", powered()); put("mainsl", mains.ok()); put("doorg", Math.round(door)); put("openl", doorOpen()); put("shutl", door <= 0); put("movel", dir != 0);
        put("levl", leveller()); put("derl", derail()); put("carl", c != null); put("green", secured()); put("red", !secured());
        put("carg", c == null ? 0 : c.getPercentCargoFull());
        text("dockno", n < 0 ? "-" : String.valueOf(n + 1));
        text("line1", c == null ? "NO CAR" : cut(Cars.name(c).toUpperCase(Locale.ROOT), 14));
        text("line2", secured() ? "SECURED" : !doorOpen() ? "DOOR " + Math.round(door) + "%" : !leveller() ? "LIP UP" : !derail() ? "NO DERAIL" : "NO CAR");
    }

    @Override protected void saveExtra(NBTTagCompound t) { t.setFloat("door", door); t.setInteger("dir", dir); }
    @Override protected void loadExtra(NBTTagCompound t) { door = t.getFloat("door"); dir = t.getInteger("dir"); }

    @Override public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() { return new net.minecraft.util.math.AxisAlignedBB(pos).grow(2, 3, 2); }
}
