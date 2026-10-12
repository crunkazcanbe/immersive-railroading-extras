package com.dogpound.railmap.shop;

import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.station.IShop;
import com.dogpound.railmap.station.IStationFacility;
import com.dogpound.railmap.stn.StnData;
import com.dogpound.railmap.stn.StnFurniture;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;

import java.util.Map;
import java.util.function.Predicate;

/**
 * A station shop counter (newsstand, coffee kiosk, ticket office): a wide desk with real storages, a display shelf, a
 * 12 V terminal and a mains supply (StnFurniture), an OPEN / CLOSED shutter key, a till that keeps the day's takings
 * (paid to the station's operators each day as facility income), and sales to the station's passengers. The stock
 * storages open for the shop's owner (whoever placed it) and creative players only; anyone may look at the shelf.
 */
public abstract class ShopTile extends StnFurniture implements IStationFacility, IShop {
    protected long takings, takingsToday, takingsYesterday;
    protected int soldToday, soldYesterday;
    protected long daySeen = -1;
    protected final double[] seen = {-1};
    protected String station = "", last = "";
    protected long stationKey;

    protected ShopTile(String[] ids, String[] names, int[] sizes, int shelf) { super(ids, names, sizes, shelf); }

    /** storages only the owner may open (index list) */
    protected abstract boolean ownerOnly(int k);
    /** one passenger sale (stock permitting): true if made */
    protected abstract boolean paxSale();
    /** passengers per sale (0 = they don't buy here) */
    protected abstract int paxEvery();
    /** a new day began (after the counters rolled over) */
    protected void newDay(long day) {}

    @Override protected boolean mechanical(String id) { return id.startsWith("k_"); }
    public boolean open() { return ctl.get("k_open") == 1; }
    @Override public boolean shopOpen() { return open() && live(); }
    @Override public boolean facilityWorking() { return shopOpen(); }
    @Override public long takeIncome() { long t = takings; takings = 0; markDirty(); return t; }
    public String stationName() { return station; }
    public long soldToday() { return soldToday; }
    public long takingsToday() { return takingsToday; }

    @Override
    protected String refuse(int k, EntityPlayer who) {
        if (!ownerOnly(k) || who.capabilities.isCreativeMode || placer == null || placer.equals(who.getUniqueID())) return null;
        return "§cStaff only - the stock belongs to " + (placerName.isEmpty() ? "the shop's owner" : placerName);
    }

    @Override
    protected String pressMechanical(String id, EntityPlayer who) {
        if (!id.equals("k_open")) return null;                  // other keys: the default turn
        ctl.set(id, ctl.get(id) == 1 ? 0 : 1);
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("station.shutter"), SoundCategory.BLOCKS, 0.8f, 1.0f);
        return open() ? "§aShutter up - OPEN" : "§eShutter down - CLOSED";
    }

    /** take a player's money for a sale; null = paid, else why not */
    protected String charge(EntityPlayer who, long cents, String what) {
        if (!(who instanceof EntityPlayerMP mp)) return "§7Served in person";
        if (cents > 0 && Coins.available() && !Coins.charge(mp, cents, what)) return "§cNot enough coins (" + Coins.fmt(cents) + ")";
        if (cents > 0 && !Coins.available() && !mp.capabilities.isCreativeMode) return "§cNo bank to pay from (Pride Realms is not installed)";
        return null;
    }

    protected void sold(long cents) {
        takings += cents; takingsToday += cents; soldToday++;
        markDirty();
    }

    protected void give(EntityPlayer who, ItemStack s) { if (!who.inventory.addItemStackToInventory(s)) who.dropItem(s, false); }

    /** FE this second from the mains (till + lights = 4; a press motor or boiler heater more) */
    protected long load() { return 4; }

    @Override
    protected void serverTick() {
        if (mains.tick(world, pos, term, wiredName(), term.powered() ? load() : 0)) controlsChanged();
        work();
    }

    @Override
    protected void work() {
        if (age % 20 != 7) return;
        long day = world.getWorldTime() / 24000;
        if (daySeen < 0) daySeen = day;
        if (day != daySeen) {
            daySeen = day;
            soldYesterday = soldToday; takingsYesterday = takingsToday; soldToday = 0; takingsToday = 0;
            newDay(day);
            markDirty();
        }
        if (age % 200 == 7) {
            Map.Entry<Long, String> st = StnData.station(world, pos, 48);
            station = st == null ? "" : st.getValue();
            stationKey = st == null ? 0 : st.getKey();
        }
        if (age % 1200 == 7) {
            double r = IShop.newRiders(world, pos, seen);
            if (ShopConfig.paxBuy && paxEvery() > 0 && shopOpen())
                for (int i = 0, n = IShop.sales(r, paxEvery(), world.rand); i < n; i++) if (!paxSale()) break;
        }
        tick();
    }

    /** the shop's own once-a-second work */
    protected void tick() {}

    // ---- stock helpers ------------------------------------------------------------------------------------------------
    public static int count(IInventory inv, Predicate<ItemStack> what) {
        int n = 0;
        for (int i = 0; i < inv.getSizeInventory(); i++) { ItemStack s = inv.getStackInSlot(i); if (!s.isEmpty() && what.test(s)) n += s.getCount(); }
        return n;
    }

    /** take n matching items; false (and nothing taken) if there aren't n */
    public static boolean take(IInventory inv, Predicate<ItemStack> what, int n) {
        if (count(inv, what) < n) return false;
        for (int i = 0; i < inv.getSizeInventory() && n > 0; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.isEmpty() || !what.test(s)) continue;
            int k = Math.min(n, s.getCount());
            s.shrink(k); n -= k;
            if (s.isEmpty()) inv.setInventorySlotContents(i, ItemStack.EMPTY);
        }
        inv.markDirty();
        return true;
    }

    @Override public void gearInfo(Map<String, Object> m) { super.gearInfo(m); m.put("open", open()); m.put("soldToday", soldToday); m.put("takingsToday", takingsToday); m.put("station", station); }

    /** a console row: station, kind, state, sold, takings, stock note */
    public String[] row() {
        return new String[]{station.isEmpty() ? ".no station" : station, facilityKind(), !live() ? "!NO SUPPLY" : open() ? "+OPEN" : "~closed",
                String.valueOf(soldToday), Coins.fmt(takingsToday), stockNote()};
    }

    public abstract String stockNote();

    @Override
    protected void save(NBTTagCompound t) {
        t.setLong("tk", takings); t.setLong("tkD", takingsToday); t.setLong("tkY", takingsYesterday);
        t.setInteger("sold", soldToday); t.setInteger("soldY", soldYesterday); t.setLong("dayS", daySeen); t.setDouble("seen", seen[0]); t.setString("last", last);
        saveShop(t);
    }

    @Override
    protected void load(NBTTagCompound t) {
        takings = t.getLong("tk"); takingsToday = t.getLong("tkD"); takingsYesterday = t.getLong("tkY");
        soldToday = t.getInteger("sold"); soldYesterday = t.getInteger("soldY"); daySeen = t.hasKey("dayS") ? t.getLong("dayS") : -1; seen[0] = t.hasKey("seen") ? t.getDouble("seen") : -1; last = t.getString("last");
        loadShop(t);
    }

    protected abstract void saveShop(NBTTagCompound t);
    protected abstract void loadShop(NBTTagCompound t);
}
