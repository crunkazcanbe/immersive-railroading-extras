package com.dogpound.railmap.station;

import com.dogpound.railmap.depot.DepotTile;
import com.dogpound.railmap.economy.Coins;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;

/**
 * Vending Machine (her ChatGPT list #85, station shops #5): stocked from its loading door (real storage, anything you
 * put in is what it sells), priced on the PRICE selector. The glowing selection display shows the product in the
 * picked spiral (SELECT < / >, A1-C9 like the real thing); BUY charges you the price in Pride coins and drops it. The
 * drinks chiller and the display backlight run on mains (8 FE a second): with no mains it still sells, warm, and the
 * passengers buy half as much. The station's passengers buy from it - every game hour about one in fifty people who
 * boarded here buys something (taking it out of the stock) and queue at it on the concourse; the takings go to the
 * station's operators each day. Empty = out of service.
 */
public class TileVending extends DepotTile implements IStationFacility, IShop {
    static final long[] PRICES = {100, 250, 500};
    private long takings, hourSeen;
    private int soldToday, sel;
    private double boardedSeen = -1;

    public TileVending() { super(new String[]{"stock"}, new String[]{"Vending stock"}, new int[]{27}); }

    @Override public String wiredName() { return "Vending machine"; }
    @Override public String gearState() { return !powered() ? "NO 12 V" : stock() + " items · " + soldToday + " sold today" + (mains.ok() ? " · chilled" : " · no mains (warm)"); }
    @Override public String facilityKind() { return "Vending machine"; }
    @Override public boolean facilityWorking() { return powered() && stock() > 0; }
    @Override public boolean shopOpen() { return facilityWorking(); }
    @Override public int facilityPoints() { return 5; }
    @Override public long dailyCost() { return 60; }
    @Override public long takeIncome() { long t = takings; takings = 0; markDirty(); return t; }
    @Override protected boolean usesMains() { return true; }
    @Override protected long load() { return 8; }

    long price() { return PRICES[Math.floorMod(ctl.get("s_price"), PRICES.length)]; }
    int stock() { int n = 0; for (int i = 0; i < store(0).getSizeInventory(); i++) n += store(0).getStackInSlot(i).getCount(); return n; }
    static String slotName(int i) { return (char) ('A' + i / 9) + String.valueOf(i % 9 + 1); }

    /** the picked spiral, or the next one with something in it */
    int picked() {
        int n = store(0).getSizeInventory();
        for (int k = 0; k < n; k++) { int i = Math.floorMod(sel + k, n); if (!store(0).getStackInSlot(i).isEmpty()) return i; }
        return -1;
    }

    ItemStack vend(int slot) {
        if (slot < 0) return ItemStack.EMPTY;
        ItemStack s = store(0).getStackInSlot(slot);
        if (s.isEmpty()) return ItemStack.EMPTY;
        ItemStack one = s.splitStack(1);
        if (s.isEmpty()) store(0).setInventorySlotContents(slot, ItemStack.EMPTY);
        markDirty();
        return one;
    }

    @Override
    protected String press(String id, EntityPlayer who) {
        switch (id) {
            case "selup": case "seldn": {
                int n = store(0).getSizeInventory(), d = id.equals("selup") ? 1 : -1;
                for (int k = 1; k <= n; k++) { int i = Math.floorMod(sel + d * k, n); if (!store(0).getStackInSlot(i).isEmpty()) { sel = i; break; } }
                int p = picked();
                return p < 0 ? "§cSOLD OUT" : "§e" + slotName(p) + " " + store(0).getStackInSlot(p).getDisplayName() + " - " + Coins.fmt(price());
            }
            case "coinret":
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.CASH, SoundCategory.BLOCKS, 0.5f, 1.4f);
                return "§7Clink - coin return (nothing owed)";
            case "buy": break;
            default: return null;
        }
        if (stock() == 0) return "§cSOLD OUT";
        if (!(who instanceof EntityPlayerMP mp)) return "§7Buy in person";
        if (Coins.available() && !Coins.charge(mp, price(), "Vending machine")) return "§cNot enough coins (" + Coins.fmt(price()) + ")";
        ItemStack s = vend(picked());
        if (!who.inventory.addItemStackToInventory(s)) who.dropItem(s, false);
        takings += price(); soldToday++;
        world.playSound(null, pos, com.dogpound.railmap.ModSounds.BEEP_OK, SoundCategory.BLOCKS, 0.6f, 1.0f);
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("station.vend"), SoundCategory.BLOCKS, 0.8f, 1.0f);
        return "§aVended " + s.getDisplayName() + (Coins.available() ? " for " + Coins.fmt(price()) : "") + (mains.ok() ? "" : " §7(warm - the chiller has no mains)");
    }

    @Override
    protected void work() {
        if (age % 20 != 0) return;
        long h = world.getTotalWorldTime() / 1000;
        if (h != hourSeen) {
            hourSeen = h;
            if (h % 24 == 0) soldToday = 0;
            Long st = StationFacilities.stationOf(world, pos);
            com.dogpound.railmap.economy.PassengerData.Stn s = st == null ? null : com.dogpound.railmap.economy.PassengerData.get(world).stn(st);
            if (s != null && facilityWorking()) {
                double b = s.boardedDay;
                double newRiders = boardedSeen < 0 || b < boardedSeen ? 0 : b - boardedSeen;
                boardedSeen = b;
                int sales = (int) Math.floor(newRiders / (mains.ok() ? 50 : 100) + world.rand.nextDouble() * 0.5);
                for (int i = 0; i < sales && stock() > 0; i++) { vend(picked()); takings += price(); soldToday++; }
            }
        }
        int p = picked();
        boolean chill = powered() && mains.ok();
        put("pwr", powered()); put("instock", stock() > 0); put("empty", stock() == 0);
        put("nstock", stock()); put("nsold", soldToday); put("chill", chill); put("disp", chill && p >= 0);
        text("price", Coins.fmt(price()));
        text("sel", p < 0 ? "SOLD OUT" : slotName(p) + " " + cut(store(0).getStackInSlot(p).getDisplayName().toUpperCase(java.util.Locale.ROOT), 14));
        text("slotn", p < 0 ? "--" : slotName(p) + " x" + store(0).getStackInSlot(p).getCount());
    }

    @Override protected void saveExtra(NBTTagCompound t) { t.setLong("takings", takings); t.setInteger("sold", soldToday); t.setDouble("bs", boardedSeen); t.setInteger("sel", sel); }
    @Override protected void loadExtra(NBTTagCompound t) { takings = t.getLong("takings"); soldToday = t.getInteger("sold"); boardedSeen = t.hasKey("bs") ? t.getDouble("bs") : -1; sel = t.getInteger("sel"); }
}
