package com.dogpound.railmap.block;

import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.economy.CompanyData;
import com.dogpound.railmap.economy.MarketData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import net.minecraftforge.items.wrapper.InvWrapper;

import java.util.List;
import java.util.Locale;

/**
 * The Commodity Exchange (her ChatGPT list #49): the trading floor's ticker board lists every commodity with its price,
 * up / down lamps and the 24-hour change; the trader's monitor shows the selected one's supply and demand and a 24-hour
 * price chart. At the desk you SELL what you hold at the bid, BUY a lot of 16 at the ask, and the exchange's owner
 * SELLs LOTs straight out of its two warehouse cabinets - which an Unloading Pit, a hopper or any mod's pipe fills
 * (so a train can bring goods to market). Sales go to the owner's company, or to the owner. The TRADING key opens and
 * closes the floor; the bell announces the movers.
 */
public class TileExchange extends TileFurniture {
    public static final String[] STORE_IDS = {"wh1", "wh2", "cash"};
    public static final String[] STORE_NAMES = {"Warehouse Cabinet (left)", "Warehouse Cabinet (right)", "Cash Drawer"};
    public static final int[] STORE_SIZES = {27, 27, 9};
    private static final double[][] SPOTS = {{19.933, 7.0, 13.4}, {21.6, 7.0, 13.4}, {23.267, 7.0, 13.4}, {24.933, 7.0, 13.4}, {26.6, 7.0, 13.4}, {28.267, 7.0, 13.4}};
    @Override public double[][] shelfSpots() { return SPOTS; }
    public static final int SHELF = 6, ROWS = 8, LOT = 16;
    private int sel;

    public TileExchange() { super(STORE_IDS, STORE_NAMES, STORE_SIZES, SHELF); }

    private boolean open() { return ctl.get("trading") == 0; }
    @Override protected boolean mechanical(String id) { return id.equals("trading"); }

    private List<MarketData.Price> list() { return MarketData.get(world).list(); }

    private MarketData.Price selected() {
        List<MarketData.Price> l = list();
        if (l.isEmpty()) return null;
        sel = Math.max(0, Math.min(sel, l.size() - 1));
        return l.get(sel);
    }

    private int warehouseCount(ItemStack w) {
        int n = 0;
        for (int k = 0; k < 2; k++) { IInventory inv = storage(k); for (int i = 0; i < inv.getSizeInventory(); i++) if (inv.getStackInSlot(i).isItemEqual(w)) n += inv.getStackInSlot(i).getCount(); }
        return n;
    }

    private int takeFromWarehouse(ItemStack w, int max) {
        int got = 0;
        for (int k = 0; k < 2 && got < max; k++) {
            IInventory inv = storage(k);
            for (int i = 0; i < inv.getSizeInventory() && got < max; i++) if (inv.getStackInSlot(i).isItemEqual(w)) got += inv.decrStackSize(i, max - got).getCount();
        }
        return got;
    }

    /** a 24-character price chart: oldest left, now right */
    public static String spark(MarketData.Price p) {
        String bars = "_.,-~=*^";
        long lo = Long.MAX_VALUE, hi = Long.MIN_VALUE;
        long[] v = new long[24];
        for (int i = 0; i < 24; i++) { v[i] = i == 23 ? p.price : p.hist(22 - i); lo = Math.min(lo, v[i]); hi = Math.max(hi, v[i]); }
        StringBuilder b = new StringBuilder();
        for (long x : v) b.append(bars.charAt(hi == lo ? 3 : (int) ((x - lo) * 7 / (hi - lo))));
        return b.toString();
    }

    @Override
    protected boolean refresh() {
        boolean ch = false;
        MarketData md = MarketData.get(world);
        List<MarketData.Price> l = md.list();
        MarketData.Price s = selected();
        int top = Math.max(0, Math.min(sel - 3, l.size() - ROWS));
        int up = 0, down = 0, vol = 0;
        for (MarketData.Price p : l) { if (p.price > p.last) up++; else if (p.price < p.last) down++; if (Math.abs(p.price - p.last) * 100 >= Math.max(1, p.last) * 4) vol++; }
        for (int r = 1; r <= ROWS; r++) {
            int i = top + r - 1;
            MarketData.Price p = i < l.size() ? l.get(i) : null;
            ch |= StationControls.text(ctl, "r" + r + "n", p == null ? "" : (i == sel ? ">" : " ") + cut(p.name().toUpperCase(Locale.ROOT), 16));
            ch |= StationControls.text(ctl, "r" + r + "p", p == null ? "" : Coins.fmt(p.price));
            int c = p == null ? 0 : p.change24();
            ch |= StationControls.text(ctl, "r" + r + "c", p == null ? "" : String.format(Locale.ROOT, "%+.1f%%", c / 10.0));
            ch |= StationControls.put(ctl, "r" + r + "u", p != null && p.price > p.last);
            ch |= StationControls.put(ctl, "r" + r + "d", p != null && p.price < p.last);
        }
        ch |= StationControls.text(ctl, "idx", "INDEX " + md.index());
        long t = (world.getWorldTime() + 6000) % 24000;
        ch |= StationControls.text(ctl, "time", String.format(Locale.ROOT, "%02d:%02d", t / 1000, (t % 1000) * 60 / 1000));
        ch |= StationControls.text(ctl, "m1", s == null ? "NO MARKET" : cut(s.name().toUpperCase(Locale.ROOT), 18) + " " + Coins.fmt(s.price));
        ch |= StationControls.text(ctl, "m2", s == null ? "" : String.format(Locale.ROOT, "SUPPLY %.0f  DEMAND %.0f", s.supply, s.demand));
        ch |= StationControls.text(ctl, "m3", s == null ? "" : "24H " + spark(s));
        ch |= StationControls.text(ctl, "m4", s == null ? "" : String.format(Locale.ROOT, "BASE %s  x%.2f  VOL %d", Coins.fmt(s.base), s.factor(), s.volume));
        ch |= StationControls.text(ctl, "bid", s == null ? "" : Coins.fmt(s.bid()));
        ch |= StationControls.text(ctl, "ask", s == null ? "" : Coins.fmt(s.ask()));
        ch |= StationControls.text(ctl, "high", s == null ? "" : Coins.fmt(s.high()));
        ch |= StationControls.text(ctl, "low", s == null ? "" : Coins.fmt(s.low()));
        ch |= StationControls.text(ctl, "stock", s == null ? "" : warehouseCount(s.stack()) + " IN STORE");
        ch |= StationControls.put(ctl, "power", term.powered());
        ch |= StationControls.put(ctl, "open", open());
        ch |= StationControls.put(ctl, "bull", up > down);
        ch |= StationControls.put(ctl, "bear", down > up);
        ch |= StationControls.put(ctl, "volatile", vol > 0);
        return ch;
    }

    @Override
    protected String press(String id, EntityPlayer who) {
        MarketData md = MarketData.get(world);
        MarketData.Price s = selected();
        switch (id) {
            case "prev": sel = Math.max(0, sel - 1); return "";
            case "next": sel++; return "";
            case "chart":
                if (s == null || who == null) return "";
                who.sendMessage(new TextComponentString("§6📈 " + s.name() + " §7now §f" + Coins.fmt(s.price) + " §7(base " + Coins.fmt(s.base) + ")"));
                who.sendMessage(new TextComponentString("§7 24h: §e" + spark(s) + " §7high §a" + Coins.fmt(s.high()) + " §7low §c" + Coins.fmt(s.low())));
                who.sendMessage(new TextComponentString(String.format(Locale.ROOT, "§7 supply %.0f · demand %.0f · bid %s · ask %s · traded today %d", s.supply, s.demand, Coins.fmt(s.bid()), Coins.fmt(s.ask()), s.volume)));
                return "";
            case "bell": {
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.FIRE_BELL, SoundCategory.BLOCKS, 1.5f, 1.0f);
                MarketData.Price best = null, worst = null;
                for (MarketData.Price p : md.all()) { if (best == null || p.change24() > best.change24()) best = p; if (worst == null || p.change24() < worst.change24()) worst = p; }
                String say = "§6🔔 Exchange: §a▲ " + (best == null ? "-" : best.name() + String.format(Locale.ROOT, " %+.1f%%", best.change24() / 10.0))
                        + " §c▼ " + (worst == null ? "-" : worst.name() + String.format(Locale.ROOT, " %+.1f%%", worst.change24() / 10.0)) + " §7index " + md.index();
                for (EntityPlayer pl : world.playerEntities) if (pl.getDistanceSq(pos) < 48 * 48) pl.sendMessage(new TextComponentString(say));
                return "";
            }
            default: break;
        }
        if (!open()) return "§7The trading floor is CLOSED - turn the TRADING key";
        if (!(who instanceof EntityPlayerMP p)) return "§7Trades are made in person";
        if (!com.dogpound.railmap.economy.Perms.has(p, com.dogpound.railmap.economy.Perms.TRADE)) return com.dogpound.railmap.economy.Perms.no("trade at the exchange");
        switch (id) {
            case "sellhand": {
                ItemStack h = p.getHeldItemMainhand();
                if (h.isEmpty()) return "§7Hold the goods you want to sell";
                MarketData.Price q = md.of(h);
                if (q == null) return "§7The exchange doesn't deal in that";
                long got = q.bid() * h.getCount();
                if (!Coins.pay(p, got, "Sold " + h.getCount() + " " + h.getDisplayName())) return "§cThe bank isn't there (Pride Realms not installed)";
                md.sold(h, h.getCount());
                String what = h.getCount() + " " + h.getDisplayName();
                h.setCount(0);
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.CASH, SoundCategory.BLOCKS, 0.5f, 1.0f);
                return "§aSold " + what + " at " + Coins.fmt(q.bid()) + " - " + Coins.fmt(got) + " to your bank";
            }
            case "buylot": {
                if (s == null) return "";
                ItemStack w = s.stack();
                if (w.isEmpty()) return "§7That commodity isn't in this world";
                long cost = s.ask() * LOT;
                if (!Coins.charge(p, cost, "Bought " + LOT + " " + s.name())) return "§cA lot of " + LOT + " costs " + Coins.fmt(cost) + " - your bank can't cover it";
                md.bought(w, LOT);
                ItemStack lot = w.copy();
                lot.setCount(LOT);
                if (!p.inventory.addItemStackToInventory(lot)) p.dropItem(lot, false);
                return "§aBought " + LOT + " " + s.name() + " at " + Coins.fmt(s.ask()) + " (" + Coins.fmt(cost) + ")";
            }
            case "selllot": {
                if (s == null) return "";
                Company c = placer == null ? null : CompanyData.get(world).of(placer);
                boolean mine = p.getUniqueID().equals(placer) || c != null && c.can(p.getUniqueID(), r -> r.finance);
                if (!mine) return "§cOnly " + (placerName.isEmpty() ? "the exchange's owner" : placerName) + (c == null ? "" : " or " + c.name + "'s directors") + " can sell the warehouse stock";
                ItemStack w = s.stack();
                int n = takeFromWarehouse(w, 64 * 54);
                if (n <= 0) return "§7No " + s.name() + " in the warehouse";
                long got = s.bid() * n;
                md.sold(w, n);
                if (c != null) CompanyData.get(world).credit(world, c, Company.Kind.TRADING, got, "Sold " + n + " " + s.name() + " at the exchange");
                else if (!Coins.pay(p, got, "Sold " + n + " " + s.name())) return "§cThe bank isn't there (Pride Realms not installed)";
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.CASH, SoundCategory.BLOCKS, 0.6f, 1.0f);
                return "§aSold " + n + " " + s.name() + " from the warehouse - " + Coins.fmt(got) + (c != null ? " to " + c.name : " to your bank");
            }
            default: return null;
        }
    }

    // ---- the warehouse is a real inventory: pits, hoppers and pipes deliver into it ---------------------------------
    private IItemHandler handler;
    private IItemHandler handler() { if (handler == null) handler = new CombinedInvWrapper(new InvWrapper(storage(0)), new InvWrapper(storage(1))); return handler; }

    @Override
    public boolean hasCapability(Capability<?> cap, EnumFacing f) {
        return cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(cap, f);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCapability(Capability<T> cap, EnumFacing f) {
        return cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY ? (T) handler() : super.getCapability(cap, f);
    }

    @Override public String wiredName() { return "Commodity Exchange"; }
    @Override public String gearState() { return (open() ? "OPEN" : "CLOSED") + " · index " + MarketData.get(world).index(); }
    @Override protected void writeExtra(NBTTagCompound t) { t.setInteger("sel", sel); }
    @Override protected void readExtra(NBTTagCompound t) { sel = t.getInteger("sel"); }
}
