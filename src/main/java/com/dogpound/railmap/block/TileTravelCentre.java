package com.dogpound.railmap.block;

import com.dogpound.railmap.RailMapConfig;
import com.dogpound.railmap.auto.Disruptions;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.economy.CompanyData;
import com.dogpound.railmap.economy.PassengerData;
import com.dogpound.railmap.item.ItemTicket;
import com.dogpound.railmap.proxy.CommonProxy;
import com.dogpound.railmap.server.StationData;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import java.util.*;

/**
 * The Travel Centre (her ChatGPT list #48 + #50): the station's staffed ticket office. Behind the counter the zone map
 * lists every station with its fare zone, satisfaction and crowd; the Passenger Charter board shows this station's
 * scores against their targets. At the counter: pick the zones (FROM / TO), the pass (DAY, 7-DAY, 28-DAY) and the
 * concession (ADULT, CHILD, SENIOR) and PRINT it - paid in Pride coins, shared between the operators calling here.
 * REFUND buys back an unused pass, CLEAN sends the cleaners round (the operator pays), COMPLAINTS reads out the
 * day's complaints, and ANNOUNCE apologises for the delays on the PA.
 */
public class TileTravelCentre extends TileFurniture {
    public static final String[] STORE_IDS = {"till", "cup1", "cup2"};
    public static final String[] STORE_NAMES = {"Ticket Office Till", "Stationery Cupboard (left)", "Stationery Cupboard (right)"};
    public static final int[] STORE_SIZES = {9, 18, 18};
    private static final double[][] SPOTS = {{-11.883, 7.0, 13.4}, {-10.25, 7.0, 13.4}, {-8.617, 7.0, 13.4}, {-6.983, 7.0, 13.4}, {-5.35, 7.0, 13.4}, {-3.717, 7.0, 13.4}};
    @Override public double[][] shelfSpots() { return SPOTS; }
    public static final int SHELF = 6;
    private static final long CLEAN_COST = 2_500;

    private long station = Long.MIN_VALUE;
    private String stationName = "";
    private int scroll;

    public TileTravelCentre() { super(STORE_IDS, STORE_NAMES, STORE_SIZES, SHELF); }

    private int zlo() { return Math.max(1, Math.min(9, ctl.get("zlo") == 0 ? 1 : ctl.get("zlo"))); }
    private int zhi() { return Math.max(zlo(), Math.min(9, ctl.get("zhi") == 0 ? 1 : ctl.get("zhi"))); }
    private int ptype() { return Math.floorMod(ctl.get("ptype"), 3); }
    private int conc() { return Math.floorMod(ctl.get("conc"), 3); }

    private void resolve() {
        StationData sd = StationData.get(world);
        long best = Long.MIN_VALUE;
        double bd = (double) RailMapConfig.stationReach * RailMapConfig.stationReach;
        for (Map.Entry<Long, String> e : sd.names().entrySet()) {
            double d = BlockPos.fromLong(e.getKey()).distanceSq(pos);
            if (d < bd) { bd = d; best = e.getKey(); }
        }
        station = best;
        stationName = best == Long.MIN_VALUE ? "" : sd.names().get(best);
    }

    private long price() { return PassengerData.get(world).passPrice(ptype(), zlo(), zhi(), conc()); }

    @Override
    protected boolean refresh() {
        if (age % 100 == 3 || station == Long.MIN_VALUE) resolve();
        boolean ch = false;
        PassengerData pd = PassengerData.get(world);
        PassengerData.Stn s = station == Long.MIN_VALUE ? null : pd.stn(station);
        // zone map: every station, nearest first
        List<Long> keys = new ArrayList<>(StationData.get(world).names().keySet());
        keys.sort(Comparator.comparingInt(pd::zoneOf).thenComparingDouble(k -> BlockPos.fromLong(k).distanceSq(pos)));
        scroll = Math.max(0, Math.min(scroll, Math.max(0, keys.size() - 6)));
        for (int r = 1; r <= 6; r++) {
            int i = scroll + r - 1;
            Long k = i < keys.size() ? keys.get(i) : null;
            PassengerData.Stn x = k == null ? null : pd.stn(k);
            ch |= StationControls.text(ctl, "r" + r + "a", k == null ? "" : "Z" + pd.zoneOf(k) + " " + cut(StationData.get(world).names().getOrDefault(k, "?").toUpperCase(Locale.ROOT), 18) + (k == station ? " *" : ""));
            ch |= StationControls.text(ctl, "r" + r + "b", x == null ? "" : String.format(Locale.ROOT, "%3.0f%% %4d WAIT", x.sat, Math.round(x.waitingTotal())));
            ch |= StationControls.put(ctl, "r" + r + "g", x != null && x.sat >= 65);
            ch |= StationControls.put(ctl, "r" + r + "r", x != null && x.sat < 45);
        }
        // the passenger charter
        String[] ids = {"punct", "crowd", "wait", "fac", "clean", "sat"};
        double[] vals = s == null ? new double[6] : new double[]{s.punct, s.crowd, s.wait, s.fac, s.clean, s.sat};
        double[] target = {90, 75, 70, 60, 75, 70};
        for (int i = 0; i < ids.length; i++) {
            ch |= StationControls.text(ctl, "c" + ids[i], s == null ? "--" : String.format(Locale.ROOT, "%.0f%%", vals[i]));
            ch |= StationControls.put(ctl, "ok" + ids[i], s != null && vals[i] >= target[i]);
            ch |= StationControls.put(ctl, "no" + ids[i], s != null && vals[i] < target[i]);
        }
        ch |= StationControls.text(ctl, "stn", station == Long.MIN_VALUE ? "NO STATION NEARBY" : cut(stationName.toUpperCase(Locale.ROOT), 22) + "  ZONE " + pd.zoneOf(station));
        ch |= StationControls.text(ctl, "zones", "ZONES " + zlo() + "-" + zhi());
        ch |= StationControls.text(ctl, "price", Coins.fmt(price()));
        ch |= StationControls.text(ctl, "pass", PassengerData.PASSES[ptype()] + " " + PassengerData.CONCESSIONS[conc()]);
        ch |= StationControls.text(ctl, "today", s == null ? "" : Coins.fmt(s.revenueDay));
        ch |= StationControls.text(ctl, "waitn", s == null ? "" : Math.round(s.waitingTotal()) + " WAITING");
        ch |= StationControls.text(ctl, "complaints", s == null ? "" : Math.round(s.complaintsDay) + " COMPLAINTS" + (s.topReason().isEmpty() ? "" : ": " + s.topReason().toUpperCase(Locale.ROOT)));
        ch |= StationControls.put(ctl, "power", term.powered());
        ch |= StationControls.put(ctl, "peak", PassengerData.peak(PassengerData.hour(world)));
        ch |= StationControls.put(ctl, "disrupt", !Disruptions.incidents(world.provider.getDimension()).isEmpty());
        ch |= StationControls.put(ctl, "full", s != null && s.waitingTotal() > 150);
        ch |= StationControls.put(ctl, "dirty", s != null && s.clean < 50);
        ch |= StationControls.put(ctl, "satg", s == null ? 0 : (int) Math.round(s.sat));
        return ch;
    }

    @Override
    protected String press(String id, EntityPlayer who) {
        PassengerData pd = PassengerData.get(world);
        switch (id) {
            case "zlo-": ctl.set("zlo", Math.max(1, zlo() - 1)); return "";
            case "zlo+": ctl.set("zlo", Math.min(9, zlo() + 1)); ctl.set("zhi", Math.max(zhi(), zlo())); return "";
            case "zhi-": ctl.set("zhi", Math.max(zlo(), zhi() - 1)); return "";
            case "zhi+": ctl.set("zhi", Math.min(9, zhi() + 1)); return "";
            case "ptype": ctl.set("ptype", (ptype() + 1) % 3); return "§7" + PassengerData.PASSES[ptype()] + " pass";
            case "conc": ctl.set("conc", (conc() + 1) % 3); return "§7" + PassengerData.CONCESSIONS[conc()] + " fare";
            case "up": scroll = Math.max(0, scroll - 1); return "";
            case "down": scroll++; return "";
            case "complain": {
                PassengerData.Stn s = pd.stn(station);
                if (s == null || who == null) return "§7No passengers here yet";
                who.sendMessage(new TextComponentString("§6✉ Complaints at " + stationName + " today: §f" + Math.round(s.complaintsDay)));
                for (int i = 0; i < PassengerData.REASONS.length; i++)
                    if (s.reasons[i] >= 0.5) who.sendMessage(new TextComponentString(String.format(Locale.ROOT, "§7  %s: §f%d", PassengerData.REASONS[i], Math.round(s.reasons[i]))));
                who.sendMessage(new TextComponentString(String.format(Locale.ROOT, "§7  left behind %d · walked away %d · fare dodgers ~%d", Math.round(s.leftDay), Math.round(s.walkedDay), Math.round(s.evadersDay))));
                return "";
            }
            case "announce": {
                int n = 0;
                String say = "We are sorry for the disruption to services at " + stationName + ". Please listen for further announcements.";
                for (net.minecraft.tileentity.TileEntity te : world.loadedTileEntityList)
                    if (te instanceof TilePaSpeaker sp && te.getPos().distanceSq(pos) < 64 * 64 && sp.page(say)) n++;
                return n == 0 ? "§7No powered PA speakers in reach" : "§eApology read out on " + n + " PA speakers";
            }
            case "clean": {
                if (station == Long.MIN_VALUE) return "§7No station here";
                List<Company> ops = CompanyData.get(world).operatorsAt(world, station);
                if (!ops.isEmpty() && !CompanyData.get(world).debit(world, ops.get(0), Company.Kind.CLEANING, CLEAN_COST, "Cleaners at " + stationName, true))
                    return "§c" + ops.get(0).name + "'s maintenance budget can't pay the cleaners (" + Coins.fmt(CLEAN_COST) + ")";
                pd.clean(station);
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("station.tap"), SoundCategory.BLOCKS, 0.6f, 1.0f);
                return "§aThe cleaning crew is on its way" + (ops.isEmpty() ? "" : " (" + ops.get(0).name + " pays " + Coins.fmt(CLEAN_COST) + ")");
            }
            default: break;
        }
        if (!(who instanceof EntityPlayerMP p)) return "§7Tickets are sold over the counter, in person";
        if (station == Long.MIN_VALUE) return "§7This office isn't at a named station";
        switch (id) {
            case "print": {
                long cost = price();
                int type = ptype();
                if (!Coins.charge(p, cost, PassengerData.PASSES[type] + " pass, zones " + zlo() + "-" + zhi())) return "§cThat pass costs " + Coins.fmt(cost) + " - your bank can't cover it";
                long until = world.getTotalWorldTime() + PassengerData.PASS_DAYS[type] * CompanyData.DAY;
                ItemStack pass = ItemTicket.makePass(CommonProxy.ticket, type, zlo(), zhi(), until, conc(), RailwayData.get(world).nextTicketSerial(), cost, stationName);
                CompanyData cd = CompanyData.get(world);
                cd.creditShared(world, cd.operatorsAt(world, station), Company.Kind.FARES, cost, PassengerData.PASSES[type] + " pass sold at " + stationName);
                PassengerData.Stn s = pd.stn(station);
                if (s != null) s.revenueDay += cost;
                pd.markDirty();
                EnumFacing f = controlFacing();
                EntityItem e = new EntityItem(world, pos.getX() + 0.5 + f.getXOffset() * 0.7, pos.getY() + 1.0, pos.getZ() + 0.5 + f.getZOffset() * 0.7, pass);
                e.motionX = f.getXOffset() * 0.08; e.motionY = 0.1; e.motionZ = f.getZOffset() * 0.08;
                world.spawnEntity(e);
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.PRINTER, SoundCategory.BLOCKS, 0.25f, 1.0f);
                return "§a" + PassengerData.PASSES[type] + " pass, zones " + zlo() + "-" + zhi() + " (" + PassengerData.CONCESSIONS[conc()].toLowerCase(Locale.ROOT) + ") - " + Coins.fmt(cost);
            }
            case "refund": {
                ItemStack h = p.getHeldItemMainhand();
                if (!ItemTicket.isPass(h)) return "§7Hold the pass you want refunded";
                if (ItemTicket.rides(h) > 0) return "§cThat pass has been used - no refund";
                if (world.getTotalWorldTime() > ItemTicket.expires(h)) return "§cThat pass has expired";
                long back = ItemTicket.paid(h) * 90 / 100;
                List<Company> ops = CompanyData.get(world).operatorsAt(world, station);
                if (!ops.isEmpty() && !CompanyData.get(world).debit(world, ops.get(0), Company.Kind.REFUNDS, back, "Pass refund at " + stationName, false))
                    return "§c" + ops.get(0).name + " can't pay refunds right now";
                if (!Coins.pay(p, back, "Pass refund")) return "§cThe bank isn't there (Pride Realms not installed)";
                h.setCount(0);
                return "§aRefunded " + Coins.fmt(back) + " (90%) - the pass is cancelled";
            }
            default: return null;
        }
    }

    @Override public String wiredName() { return "Travel Centre"; }
    @Override public String gearState() {
        PassengerData.Stn s = station == Long.MIN_VALUE ? null : PassengerData.get(world).stn(station);
        return stationName.isEmpty() ? "no station" : stationName + (s == null ? "" : String.format(Locale.ROOT, " · %.0f%% satisfied · %d waiting", s.sat, Math.round(s.waitingTotal())));
    }

    @Override protected void writeExtra(NBTTagCompound t) { t.setLong("station", station); t.setString("stationN", stationName); t.setInteger("scroll", scroll); }
    @Override protected void readExtra(NBTTagCompound t) { station = t.hasKey("station") ? t.getLong("station") : Long.MIN_VALUE; stationName = t.getString("stationN"); scroll = t.getInteger("scroll"); }
}
