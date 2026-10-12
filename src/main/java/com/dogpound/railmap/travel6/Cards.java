package com.dogpound.railmap.travel6;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.economy.CompanyData;
import com.dogpound.railmap.economy.PassengerData;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Locale;

/**
 * Pride Card fares (batch 6b #6): pay-as-you-go stored value like a real contactless card. Tap IN at a fare gate or card
 * reader at the start, tap OUT at the end; the journey fare comes from the railway's fare scheme (PassengerData.fare:
 * zones / distance / flat, peak and night rates). Daily capping: once today's fares reach the day pass price for the
 * zones the card touched today (x capPct), every further journey today is free. Tapping in and out at one station
 * within the grace time is free; a journey never tapped out is charged the maximum fare. A card reported lost is
 * hotlisted: tapping it raises an alarm and it is refused. Fares go to the operators at the station tapped out at.
 */
public final class Cards {
    private Cards() {}

    public static final int AUTO = 0, ENTRY = 1, EXIT = 2;

    public static final class Result {
        public final boolean ok, out;
        public final String msg;
        public final long charged;
        Result(boolean ok, boolean out, String msg, long charged) { this.ok = ok; this.out = out; this.msg = msg; this.charged = charged; }
    }

    public static boolean isCard(ItemStack s) { return ItemTravel.is(s, ItemTravel.CARD); }

    public static long day(World w) { return w.getWorldTime() / 24000L; }

    /** the most a journey can cost: across the railway's whole zone span */
    public static long maxFare(World w) {
        PassengerData pd = PassengerData.get(w);
        int lo = 9, hi = 1;
        for (Long k : com.dogpound.railmap.server.StationData.get(w).names().keySet()) { int z = pd.zoneOf(k); lo = Math.min(lo, z); hi = Math.max(hi, z); }
        if (lo > hi) lo = hi;
        long f = pd.zoneBase + pd.zoneStep * (hi - lo);
        return Math.max(f, pd.scheme.equals("FLAT") ? pd.flat : f) * (100 + pd.peakPct) / 100;
    }

    /** today's cap for the zones touched */
    public static long cap(World w, TravelData.Card c) {
        if (c.zlo == 0) return Long.MAX_VALUE;
        return PassengerData.get(w).passPrice(0, c.zlo, c.zhi, 0) * TravelConfig.capPct / 100;
    }

    static void rollDay(World w, TravelData.Card c) {
        long d = day(w);
        if (c.day == d) return;
        c.day = d; c.spentDay = 0; c.zlo = 0; c.zhi = 0; c.tripsDay = 0;
    }

    static void touch(TravelData.Card c, int zone) {
        c.zlo = c.zlo == 0 ? zone : Math.min(c.zlo, zone);
        c.zhi = Math.max(c.zhi, zone);
    }

    /** take a fare off the card (capped); returns what was charged */
    static long charge(World w, TravelData.Card c, long fare) {
        long room = Math.max(0, cap(w, c) - c.spentDay);
        long ch = Math.max(0, Math.min(fare, room));
        c.bal -= ch; c.spentDay += ch; c.totalSpent += ch;
        return ch;
    }

    static void credit(World w, long station, long cents, String stName) {
        if (cents <= 0) return;
        TravelData.get(w).fares += cents;
        PassengerData pd = PassengerData.get(w);
        PassengerData.Stn s = pd.stn(station);
        if (s != null) { s.revenueDay += cents; pd.markDirty(); }
        CompanyData cd = CompanyData.get(w);
        cd.creditShared(w, cd.operatorsAt(w, station), Company.Kind.FARES, cents, "Pride Card fares at " + stName);
    }

    /** an open journey is closed at the maximum fare */
    static long incomplete(World w, TravelData.Card c, String why) {
        long now = w.getTotalWorldTime();
        long f = charge(w, c, maxFare(w));
        c.note(Recorder.clock(w, now) + " INCOMPLETE from " + c.inName + " (" + why + ") -" + Coins.fmt(f));
        credit(w, c.inKey, f, c.inName);
        c.inKey = Long.MIN_VALUE; c.inName = "";
        return f;
    }

    /**
     * a card tapped on a reader at `station`. mode AUTO: in if not on a journey, else out. ENTRY / EXIT readers force it
     * (an EXIT with no tap-in, or an ENTRY while on another journey, is an incomplete journey).
     */
    public static Result tap(World w, ItemStack card, long station, String stName, int mode, String where) {
        int id = ItemTravel.cardId(card);
        if (id == 0) return new Result(false, false, "Blank card - register it at a Top-Up Machine", 0);
        TravelData d = TravelData.get(w);
        TravelData.Card c = d.cards.get(id);
        if (c == null) return new Result(false, false, "Card not recognised", 0);
        long now = w.getTotalWorldTime();
        if (c.blocked) {
            AlarmManager.get(w).raise(w, "travel6.hot." + id, AlarmManager.Cat.GENERAL, AlarmManager.Sev.MINOR,
                    "Hotlisted Pride Card " + id + " (" + c.ownerName + ") tapped at " + where + ", " + stName, null, false);
            return new Result(false, false, "SEEK ASSISTANCE - card " + id + " is blocked", 0);
        }
        rollDay(w, c);
        PassengerData pd = PassengerData.get(w);
        int zone = pd.zoneOf(station);
        String clock = Recorder.clock(w, now);
        try {
            if (c.in() && now - c.inAt > TravelConfig.incompleteHours * 1000L) incomplete(w, c, "never tapped out");
            if (c.in() && mode == ENTRY && c.inKey != station) incomplete(w, c, "tapped in again at " + stName);
            if (!c.in() && mode == EXIT) {
                long f = charge(w, c, maxFare(w));
                credit(w, station, f, stName);
                c.note(clock + " OUT " + stName + " with no tap in -" + Coins.fmt(f));
                return new Result(true, true, "No tap in found - maximum fare " + Coins.fmt(f) + " · balance " + Coins.fmt(c.bal), f);
            }
            if (!c.in()) {
                if (c.bal < TravelConfig.minTapIn) return new Result(false, false, "TOP UP - balance " + Coins.fmt(c.bal) + " (" + Coins.fmt(TravelConfig.minTapIn) + " needed to travel)", 0);
                c.inKey = station; c.inAt = now; c.inName = stName;
                touch(c, zone);
                c.note(clock + " IN  " + stName + " (zone " + zone + ")");
                return new Result(true, false, "Touched in at " + stName + " · zone " + zone + " · balance " + Coins.fmt(c.bal), 0);
            }
            if (c.inKey == station) {
                boolean grace = now - c.inAt <= TravelConfig.sameStationGrace * 20L;
                long f = grace ? 0 : charge(w, c, pd.fare(w, station, station));
                credit(w, station, f, stName);
                c.note(clock + " OUT " + stName + " (same station" + (grace ? ", no charge" : "") + ") -" + Coins.fmt(f));
                c.inKey = Long.MIN_VALUE; c.inName = "";
                return new Result(true, true, (grace ? "Journey cancelled - no charge" : "Same-station exit " + Coins.fmt(f)) + " · balance " + Coins.fmt(c.bal), f);
            }
            touch(c, zone);
            long fare = pd.fare(w, c.inKey, station);
            long f = charge(w, c, fare);
            boolean capped = f < fare;
            credit(w, station, f, stName);
            String from = c.inName;
            c.trips++; c.tripsDay++; c.lastTripEnd = now;
            c.note(clock + " OUT " + stName + " from " + from + " -" + Coins.fmt(f) + (capped ? " (CAPPED)" : ""));
            c.inKey = Long.MIN_VALUE; c.inName = "";
            return new Result(true, true, String.format(Locale.ROOT, "%s > %s %s%s · balance %s", from, stName, Coins.fmt(f), capped ? " (daily cap reached)" : "", Coins.fmt(c.bal)), f);
        } finally {
            ItemTravel.sync(card, c);
            d.markDirty();
        }
    }

    /** put money on a card from the player's bank */
    public static String topUp(EntityPlayerMP p, TravelData.Card c, long cents, String where) {
        if (cents <= 0) return "§7Pick an amount";
        if (!Coins.charge(p, cents, "Pride Card " + c.id + " top-up")) return "§cYour bank can't cover " + Coins.fmt(cents);
        World w = p.world;
        c.bal += cents;
        c.note(Recorder.clock(w, w.getTotalWorldTime()) + " TOP UP +" + Coins.fmt(cents) + " at " + where);
        TravelData d = TravelData.get(w);
        d.topups += cents;
        d.markDirty();
        return "§aTopped up " + Coins.fmt(cents) + " - balance " + Coins.fmt(c.bal);
    }

    /** the player's cards (wallet) */
    public static java.util.List<TravelData.Card> of(World w, java.util.UUID who) {
        java.util.List<TravelData.Card> o = new java.util.ArrayList<>();
        for (TravelData.Card c : TravelData.get(w).cards.values()) if (who.equals(c.owner)) o.add(c);
        return o;
    }

    /** the first registered card in a player's inventory */
    public static ItemStack held(net.minecraft.entity.player.EntityPlayer p) {
        for (ItemStack s : new ItemStack[]{p.getHeldItemMainhand(), p.getHeldItemOffhand()}) if (ItemTravel.cardId(s) != 0) return s;
        for (ItemStack s : p.inventory.mainInventory) if (ItemTravel.cardId(s) != 0) return s;
        return ItemStack.EMPTY;
    }

    /** once a minute: journeys left open too long are closed at the maximum fare */
    static void sweep(World w) {
        TravelData d = TravelData.get(w);
        long now = w.getTotalWorldTime();
        for (TravelData.Card c : d.cards.values())
            if (c.in() && now - c.inAt > TravelConfig.incompleteHours * 1000L) { rollDay(w, c); incomplete(w, c, "never tapped out"); d.markDirty(); }
    }

    /** the station a block guards (nearest named station in reach), or null */
    public static java.util.Map.Entry<Long, String> stationAt(World w, BlockPos p) {
        return com.dogpound.railmap.stn.StnData.station(w, p, com.dogpound.railmap.RailMapConfig.stationReach);
    }
}
