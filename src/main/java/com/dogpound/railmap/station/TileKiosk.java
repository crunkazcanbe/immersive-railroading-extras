package com.dogpound.railmap.station;

import com.dogpound.railmap.block.StationControls;
import com.dogpound.railmap.block.TileFurniture;
import com.dogpound.railmap.economy.Coins;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.SoundCategory;

/**
 * Retail Kiosk (her ChatGPT list #85, retail concessions): a newsagent / coffee counter let to a concession. The display
 * shelf shows what is for sale (what you put on it), the back cupboards are its stock room, and the OPEN / CLOSED key
 * opens the shutter. Open and with something on display it pays the station's operators its daily rent and counts for
 * the station's facilities. The bell on the counter rings for service.
 */
public class TileKiosk extends TileFurniture implements IStationFacility, IShop {
    private static final long RENT = 1500;
    private long owed, daySeen = -1;

    public TileKiosk() { super(new String[]{"k1", "k2", "k3"}, new String[]{"Stock cupboard", "Stock cupboard", "Till drawer"}, new int[]{27, 27, 9}, 6); }

    @Override public String wiredName() { return "Retail kiosk"; }
    @Override public String gearState() { return (open() ? "OPEN" : "closed") + (stocked() ? "" : " · nothing on display"); }
    @Override public double[][] shelfSpots() { return SPOTS; }
    static double[][] SPOTS = {{19.933, 7.0, 13.4}, {21.6, 7.0, 13.4}, {23.267, 7.0, 13.4}, {24.933, 7.0, 13.4}, {26.6, 7.0, 13.4}, {28.267, 7.0, 13.4}};   // tools/gen_bb_stationfx.py
    @Override protected boolean mechanical(String id) { return id.startsWith("k_"); }

    boolean open() { return ctl.get("k_open") == 1; }
    boolean stocked() { for (int i = 0; i < shelf.getSizeInventory(); i++) if (!shelf.getStackInSlot(i).isEmpty()) return true; return false; }

    @Override public String facilityKind() { return "Retail kiosk"; }
    @Override public boolean facilityWorking() { return open() && stocked() && term.powered(); }
    @Override public boolean shopOpen() { return facilityWorking(); }
    @Override public int facilityPoints() { return 10; }
    @Override public long dailyCost() { return 0; }
    @Override public long takeIncome() { long t = owed; owed = 0; markDirty(); return t; }

    @Override
    protected String pressMechanical(String id, EntityPlayer who) {
        ctl.set(id, ctl.get(id) == 1 ? 0 : 1);
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("station.shutter"), SoundCategory.BLOCKS, 0.8f, 1.0f);
        return open() ? "§aShutter up - OPEN" : "§eShutter down - CLOSED";
    }

    @Override
    protected String press(String id, EntityPlayer who) {
        if (!id.equals("bell")) return null;
        world.playSound(null, pos, com.dogpound.railmap.ModSounds.CASH, SoundCategory.BLOCKS, 0.7f, 1.0f);
        return open() ? "§7Ding! - be right with you" : "§7Ding! - the kiosk is closed";
    }

    @Override
    protected void serverTick() {
        long d = world.getTotalWorldTime() / 24000;
        if (daySeen < 0) daySeen = d;
        if (d != daySeen) { daySeen = d; if (facilityWorking()) owed += RENT; markDirty(); }
    }

    @Override
    protected boolean refresh() {
        return StationControls.put(ctl, "pwr", true) | StationControls.put(ctl, "openl", open()) | StationControls.put(ctl, "stockl", stocked())
                | StationControls.text(ctl, "rent", Coins.fmt(RENT) + "/DAY") | StationControls.text(ctl, "sign", open() ? "OPEN" : "CLOSED");
    }

    @Override protected void writeExtra(net.minecraft.nbt.NBTTagCompound t) { t.setLong("owed", owed); t.setLong("day", daySeen); }
    @Override protected void readExtra(net.minecraft.nbt.NBTTagCompound t) { owed = t.getLong("owed"); daySeen = t.hasKey("day") ? t.getLong("day") : -1; }
}
