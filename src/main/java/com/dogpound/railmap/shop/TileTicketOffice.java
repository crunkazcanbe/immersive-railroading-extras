package com.dogpound.railmap.shop;

import com.dogpound.railmap.RailMapConfig;
import com.dogpound.railmap.auto.Dispatcher;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.block.StationControls;
import com.dogpound.railmap.block.TileTicketMachine;
import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.economy.PassengerData;
import com.dogpound.railmap.item.ItemTicket;
import com.dogpound.railmap.proxy.CommonProxy;
import com.dogpound.railmap.server.StationData;
import com.dogpound.railmap.stn.StnFurniture;
import com.dogpound.railmap.stn.StnUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Ticket Office Counter (station shop #5): a staffed booking window that sells the railway's own tickets (item.ItemTicket,
 * the same tickets the machines print and the dispatcher takes). The clerk keys a destination number on the keypad
 * (1 = nearest, as on the machines) or steps through them with DEST < / >, SINGLE or RETURN, and ISSUE prints the
 * ticket on a blank card from the ticket stock drawer (paper). Fares follow the railway's fare rules: by distance
 * (RailMapConfig.fareBlocksPerItem), by zones (Station Master's zones) or flat, +25 % in the peaks. PAY selects the fare
 * item (it goes in the cash drawer - real takings for the owner) or Pride coins (to the station's operators). CALL
 * TRAIN feeds a ticket you hold to the dispatcher like a machine; FARE TABLE prints every fare from here as a book.
 * Passengers queue at the window; the clerk serves them from the ticket stock.
 */
public class TileTicketOffice extends ShopTile {
    static double[][] SPOTS = {{-11.883, 7.0, 13.4}, {-10.25, 7.0, 13.4}, {-8.617, 7.0, 13.4}, {-6.983, 7.0, 13.4}, {-5.35, 7.0, 13.4}, {-3.717, 7.0, 13.4}};   // tools/gen_bb_shops.py
    static final String[] SCHEMES = {"DISTANCE", "ZONES", "FLAT"};
    private int num, served, servedToday;
    private boolean round;

    public TileTicketOffice() {
        super(new String[]{"stock", "cash", "rack"}, new String[]{"Ticket stock (blank cards = paper)", "Cash drawer (fares taken)", "Timetable leaflet rack"}, new int[]{18, 18, 9}, 6);
    }

    @Override public String wiredName() { return "Ticket office"; }
    @Override public String facilityKind() { return "Ticket office"; }
    @Override public int facilityPoints() { return 15; }
    @Override public int accessPoints() { return 5; }
    @Override public long dailyCost() { return 150; }
    @Override public double[][] shelfSpots() { return SPOTS; }
    @Override protected boolean ownerOnly(int k) { return k != 2; }
    @Override protected int paxEvery() { return 8; }
    @Override public String gearState() { return !live() ? "NO SUPPLY" : (open() ? "OPEN" : "closed") + " · " + soldToday + " sold · " + servedToday + " served"; }
    @Override public String stockNote() { return cards() + " blank cards, " + count(storage(1), s -> true) + " in the cash drawer, served " + servedToday; }

    int cards() { return count(storage(0), s -> s.getItem() == Items.PAPER); }
    boolean coins() { return ctl.get("s_pay") == 1; }
    String scheme() { return SCHEMES[Math.floorMod(ctl.get("s_scheme"), SCHEMES.length)]; }

    List<Long> dests() {
        List<Long> l = new ArrayList<>();
        if (stationKey == 0) return l;
        for (Long k : StationData.get(world).names().keySet()) if (k != stationKey) l.add(k);
        BlockPos here = BlockPos.fromLong(stationKey);
        l.sort(Comparator.comparingDouble(a -> BlockPos.fromLong(a).distanceSq(here)));
        return l;
    }

    Long picked() { List<Long> d = dests(); return num >= 1 && num <= d.size() ? d.get(num - 1) : null; }

    /** fare in fare items (the railway's fare rules) */
    public int fare(long dest, boolean ret) {
        return fare(scheme(), Math.sqrt(BlockPos.fromLong(dest).distanceSq(BlockPos.fromLong(stationKey))), PassengerData.get(world).zonesBetween(stationKey, dest),
                PassengerData.peak(PassengerData.hour(world)), ret, RailMapConfig.fareBlocksPerItem);
    }

    static int fare(String scheme, double dist, int zones, boolean peak, boolean ret, int per) {
        int one = switch (scheme) {
            case "ZONES" -> Math.max(1, zones);
            case "FLAT" -> 1;
            default -> Math.max(1, (int) Math.ceil(dist / Math.max(1, per)));
        };
        if (peak) one = (int) Math.ceil(one * 1.25);
        return ret ? one * 2 : one;
    }

    @Override
    protected String act(String id, EntityPlayer who) {
        if (id.length() == 2 && id.charAt(0) == 'n' && Character.isDigit(id.charAt(1))) { num = (num * 10 + id.charAt(1) - '0') % 100; return "§7" + num; }
        switch (id) {
            case "nclr": num = 0; return "§7Cleared";
            case "single": round = false; return "§7Single";
            case "return": round = true; return "§7Return";
            case "dprev": case "dnext": {
                int n = dests().size();
                if (n == 0) return "§7No other stations";
                num = Math.floorMod(num - 1 + (id.equals("dnext") ? 1 : -1), n) + 1;
                Long d = picked();
                return "§e" + num + " " + StationData.get(world).names().get(d);
            }
            case "issue": return issue(who);
            case "fares": {
                List<String> out = new ArrayList<>();
                out.add("FARES FROM " + station.toUpperCase(Locale.ROOT));
                out.add("Scheme: " + scheme().toLowerCase(Locale.ROOT) + (PassengerData.peak(PassengerData.hour(world)) ? " (peak now, +25%)" : ""));
                Item fi = TileTicketMachine.fareItem();
                String unit = fi == null ? "free" : new ItemStack(fi).getDisplayName();
                List<Long> d = dests();
                for (int i = 0; i < d.size(); i++) out.add((i + 1) + " " + StationData.get(world).names().get(d.get(i)) + ": " + fare(d.get(i), false) + " / rtn " + fare(d.get(i), true) + (fi == null ? "" : " " + unit));
                StnUtil.print(world, pos, controlFacing(), "Fare table", station, out);
                return "§aFare table printed";
            }
            case "call": {
                if (who == null || !(who.getHeldItemMainhand().getItem() instanceof ItemTicket)) return "§eHold your ticket and press CALL TRAIN";
                ItemStack t = who.getHeldItemMainhand();
                if (!ItemTicket.valid(t) || ItemTicket.isSpent(t)) return "§cThat ticket can't be used";
                if (ItemTicket.from(t) != stationKey) return "§cThat ticket is from " + ItemTicket.fromName(t);
                ItemTicket.punch(t);
                return "§e" + Dispatcher.ticketInserted(world, stationKey, ItemTicket.to(t), who.getName());
            }
            case "next": {
                if (!open()) return "§cThe window is closed";
                double q = queue();
                if (q < 1) return "§7Nobody waiting at the window";
                if (!take(storage(0), s -> s.getItem() == Items.PAPER, 1)) return "§cOut of ticket stock";
                served++; servedToday++;
                last = "SERVED " + servedToday;
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.PRINTER, SoundCategory.BLOCKS, 0.5f, 1.1f);
                return "§aNext please! - ticket issued to a waiting passenger";
            }
            case "bell":
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.CASH, SoundCategory.BLOCKS, 0.7f, 1.2f);
                return open() ? "§7Ding! - next customer please" : "§7Ding! - the window is closed";
            default: return null;
        }
    }

    private String issue(EntityPlayer who) {
        if (who == null) return "§7Tickets are sold at the window, in person";
        if (!open()) return "§cThe window is closed";
        if (stationKey == 0) return "§cNo station in reach - name one nearby";
        Long d = picked();
        if (d == null) return "§eKey a destination number first (1 = nearest) or use DEST < / >";
        if (cards() == 0) return "§cOut of ticket stock - put paper in the stock drawer";
        int f = fare(d, round);
        Item fi = TileTicketMachine.fareItem();
        String destName = StationData.get(world).names().get(d);
        if (fi != null && !who.capabilities.isCreativeMode) {
            if (coins()) {
                String no = charge(who, (long) f * ShopConfig.centsPerFareItem, "Ticket to " + destName);
                if (no != null) return no;
                sold((long) f * ShopConfig.centsPerFareItem);
            } else {
                if (count(who.inventory, s -> s.getItem() == fi) < f) return "§cThis ticket costs " + f + " " + new ItemStack(fi).getDisplayName();
                ItemStack cash = new ItemStack(fi, f);
                if (!StnFurniture.fits(storage(1), cash)) return "§cThe cash drawer is full - the owner must empty it";
                take(who.inventory, s -> s.getItem() == fi, f);
                StnFurniture.insert(storage(1), cash);
                sold(0);
            }
        } else sold(0);
        take(storage(0), s -> s.getItem() == Items.PAPER, 1);
        ItemStack t = ItemTicket.make(CommonProxy.ticket, stationKey, station, d, destName, round, RailwayData.get(world).nextTicketSerial(), world.getWorldTime() / 24000);
        give(who, t);
        last = "ISSUED " + destName.toUpperCase(Locale.ROOT);
        num = 0;
        world.playSound(null, pos, com.dogpound.railmap.ModSounds.PRINTER, SoundCategory.BLOCKS, 0.7f, 1.0f);
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ui.stamp"), SoundCategory.BLOCKS, 0.7f, 1.0f);
        return "§aTicket " + station + " > " + destName + (round ? " return" : " single") + (fi == null ? "" : " · " + (coins() ? Coins.fmt((long) f * ShopConfig.centsPerFareItem) : f + " " + new ItemStack(fi).getDisplayName()))
                + com.dogpound.railmap.station.Pis.ticketNote(world, station);
    }

    double queue() {
        PassengerData.Stn s = stationKey == 0 ? null : PassengerData.get(world).stn(stationKey);
        if (s == null) return 0;
        double n = 0;
        for (double v : s.waiting.values()) n += v;
        return n;
    }

    @Override
    protected boolean paxSale() {
        if (!take(storage(0), s -> s.getItem() == Items.PAPER, 1)) return false;
        served++; servedToday++;
        return true;
    }

    @Override protected void newDay(long day) { servedToday = 0; }

    @Override
    protected boolean refresh() {
        Long d = picked();
        String dn = d == null ? "" : StationData.get(world).names().get(d);
        Item fi = TileTicketMachine.fareItem();
        int f = d == null ? 0 : fare(d, round);
        boolean ch = false;
        ch |= StationControls.put(ctl, "pwr", term.powered());
        ch |= StationControls.put(ctl, "openl", open());
        ch |= StationControls.put(ctl, "servicel", open() && cards() > 0);
        ch |= StationControls.put(ctl, "queuel", queue() >= 5);
        ch |= StationControls.put(ctl, "stockl", cards() < 8);
        ch |= StationControls.put(ctl, "boardl", stationKey != 0 && Dispatcher.boardingAt(world, stationKey));
        ch |= StationControls.put(ctl, "peakl", PassengerData.peak(PassengerData.hour(world)));
        ch |= StationControls.put(ctl, "rtnl", round);
        ch |= StationControls.text(ctl, "sel", d == null ? (num > 0 ? num + " - NO SUCH STOP" : "KEY 1-" + dests().size()) : num + " " + StnFurniture.cut(dn.toUpperCase(Locale.ROOT), 16) + (round ? " RTN" : " SGL"));
        ch |= StationControls.text(ctl, "fare", d == null ? "" : fi == null ? "FREE TRAVEL" : coins() ? "FARE " + Coins.fmt((long) f * ShopConfig.centsPerFareItem) : "FARE " + f + " " + StnFurniture.cut(new ItemStack(fi).getDisplayName().toUpperCase(Locale.ROOT), 12));
        ch |= StationControls.text(ctl, "pay", (coins() ? "PAY COINS" : "PAY ITEMS") + " " + scheme());
        ch |= StationControls.text(ctl, "queue", String.format(Locale.ROOT, "QUEUE %.0f SERVED %d", queue(), servedToday));
        ch |= StationControls.text(ctl, "sold", "SOLD " + soldToday + " " + Coins.fmt(takingsToday));
        ch |= StationControls.text(ctl, "stockn", "CARDS " + cards());
        ch |= StationControls.text(ctl, "last", last);
        ch |= StationControls.text(ctl, "stn", station.isEmpty() ? "NO STATION" : station.toUpperCase(Locale.ROOT));
        ch |= StationControls.text(ctl, "clock", com.dogpound.railmap.biz.BizData.hhmm(world.getWorldTime()));
        return ch;
    }

    @Override protected void saveShop(NBTTagCompound t) { t.setInteger("num", num); t.setBoolean("rtn", round); t.setInteger("served", served); t.setInteger("servedD", servedToday); }
    @Override protected void loadShop(NBTTagCompound t) { num = t.getInteger("num"); round = t.getBoolean("rtn"); served = t.getInteger("served"); servedToday = t.getInteger("servedD"); }

    public static List<String> selfTest() {
        List<String> f = new ArrayList<>();
        if (fare("DISTANCE", 450, 1, false, false, 200) != 3) f.add("distance fare 450/200 should be 3");
        if (fare("DISTANCE", 450, 1, true, true, 200) != 8) f.add("peak return should be ceil(3*1.25)*2 = 8");
        if (fare("ZONES", 9999, 3, false, false, 200) != 3) f.add("3 zones = 3");
        if (fare("FLAT", 9999, 9, false, true, 200) != 2) f.add("flat return = 2");
        return f;
    }
}
