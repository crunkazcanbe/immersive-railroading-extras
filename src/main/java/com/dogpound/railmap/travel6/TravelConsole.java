package com.dogpound.railmap.travel6;

import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.stn.StnUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.*;

import static com.dogpound.railmap.stn.StnConsole.*;

/** Control Center > STN tab pages for passenger travel (batch 6b #6-10): Cards, Baggage, Lockers + parking. Ops arrive without "travel6.". */
public final class TravelConsole {
    private TravelConsole() {}

    public static void fill(World w, List<String[]> out) {
        TravelData d = TravelData.get(w);
        long now = w.getTotalWorldTime();
        page(out, "Cards", "Pride Cards: balance, the journey each is on, today's fares against the daily cap, hotlisted cards");
        cols(out, "Card:0", "Owner:12", "Balance:28", "Journey:40", "Today:62", "Trips:74", "State:82");
        btn(out, "BLOCK / UNBLOCK", "travel6.block", true);
        btn(out, "HISTORY", "travel6.hist", true);
        for (TravelData.Card c : d.cards.values()) {
            boolean today = c.day == Cards.day(w);
            row(out, "C" + c.id, String.valueOf(c.id), c.ownerName, (c.bal < TravelConfig.minTapIn ? "~" : "") + Coins.fmt(c.bal), c.in() ? "~IN " + c.inName : ".-",
                    Coins.fmt(today ? c.spentDay : 0) + (today && c.zlo > 0 && c.spentDay >= Cards.cap(w, c) ? " CAP" : ""), String.valueOf(c.trips), c.blocked ? "!BLOCKED" : "+ok");
        }
        txt(out, String.format(Locale.ROOT, "Card fares %s · top-ups %s · %d cards · cap %d%% of a day pass · min to tap in %s", Coins.fmt(d.fares), Coins.fmt(d.topups), d.cards.size(), TravelConfig.capPct, Coins.fmt(TravelConfig.minTapIn)));

        page(out, "Baggage", "Checked bags live: checked in, make-up cart, on which train, on the carousel, collected");
        cols(out, "Tag:0", "Owner:8", "From:22", "To:38", "State:54", "Train:68", "Kg:82", "Age:88");
        btn(out, "TRACE", "travel6.trace", true);
        List<TravelData.Bag> bs = new ArrayList<>(d.bags.values());
        Collections.reverse(bs);
        for (TravelData.Bag b : bs)
            row(out, "B" + b.id, String.valueOf(b.tag), b.ownerName, b.fromName, b.toName,
                    (b.state == TravelData.ON_TRAIN ? "~" : b.state == TravelData.RECLAIM ? "+" : b.state == TravelData.COLLECTED ? "." : "") + TravelData.BSTATE[b.state],
                    b.state == TravelData.ON_TRAIN ? b.trainName : ".", String.valueOf(b.weightKg()), (now - b.moved) / 1000 + " h");
        txt(out, "Bag fees " + Coins.fmt(d.bagFees) + " · " + (TravelConfig.needBaggageCar ? "bags need a baggage car" : "any driverless train on a useful line takes bags"));
        for (int i = 0; i < Math.min(5, d.log.size()); i++) txt(out, d.log.get(i));

        page(out, "Lockers + parking", "Left-luggage banks (rented / free / overstays) and park & ride car parks (parked / bays / takings)");
        cols(out, "Where:0", "Kind:28", "Use:42", "Detail:58", "Supply:84");
        for (TileLeftLuggage t : StnUtil.loaded(w, TileLeftLuggage.class)) {
            int over = 0;
            for (int i = 0; i < TileLeftLuggage.N; i++) if (t.owed(i) > 0) over++;
            row(out, "", t.where(), "Lockers", t.rented() + "/" + TileLeftLuggage.N + " rented", over > 0 ? "~" + over + " overstayed" : ".", t.live() ? "+ok" : "!NO SUPPLY");
        }
        for (TileParkPay t : StnUtil.loaded(w, TileParkPay.class))
            row(out, "", t.garage(), "Car park", t.full() ? "!FULL" : "+" + t.free() + " free", t.gearState(), t.live() ? "+ok" : "!NO SUPPLY");
        long live = d.parks.values().stream().filter(TravelData.Park::live).count();
        txt(out, "Parking tickets live " + live + " · parking takings " + Coins.fmt(d.parking) + " · locker rent " + Coins.fmt(d.rent) + " · " + Coins.fmt(TravelConfig.parkPerHour) + "/h, park & ride " + TravelConfig.parkRidePct + "%");
    }

    public static String op(EntityPlayer p, String op, String arg) {
        World w = p.world;
        TravelData d = TravelData.get(w);
        switch (op) {
            case "block" -> {
                if (!arg.startsWith("C")) return "§7Select a card";
                TravelData.Card c = d.cards.get(Integer.parseInt(arg.substring(1)));
                if (c == null) return "§7Gone";
                c.blocked = !c.blocked;
                c.note(Recorder.clock(w, w.getTotalWorldTime()) + (c.blocked ? " BLOCKED" : " UNBLOCKED") + " by control (" + p.getName() + ")");
                d.markDirty();
                Recorder.event(w, "Pride Card " + c.id + (c.blocked ? " hotlisted" : " unblocked") + " from the console by " + p.getName());
                return (c.blocked ? "§eCard " : "§aCard ") + c.id + (c.blocked ? " hotlisted" : " unblocked");
            }
            case "hist" -> {
                if (!arg.startsWith("C")) return "§7Select a card";
                TravelData.Card c = d.cards.get(Integer.parseInt(arg.substring(1)));
                if (c == null) return "§7Gone";
                p.sendMessage(new TextComponentString("§bPride Card " + c.id + " · " + c.ownerName + " · " + Coins.fmt(c.bal)));
                for (String s : c.hist) p.sendMessage(new TextComponentString("§7  " + s));
                return "";
            }
            case "trace" -> {
                if (!arg.startsWith("B")) return "§7Select a bag";
                TravelData.Bag b = d.bags.get(Integer.parseInt(arg.substring(1)));
                if (b == null) return "§7Gone";
                p.sendMessage(new TextComponentString("§bBag " + b.tag + " · " + b.ownerName + " · " + b.fromName + " > " + b.toName + " · " + TravelData.BSTATE[b.state]));
                for (String s : b.log) p.sendMessage(new TextComponentString("§7  " + s));
                return "";
            }
            default -> { return null; }
        }
    }
}
