package com.dogpound.railmap.travel6;

import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.economy.PassengerData;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.*;

/**
 * /irextras travel ... - passenger travel (batch 6b #6-10):
 * <pre>
 *   travel                              totals: cards, bags, parking, lockers
 *   travel card                         your cards
 *   travel card new [cents]             (op) a registered card for you, with a balance
 *   travel card topup <id> <cents>      (op) put money on a card
 *   travel card tap <id> <station> [auto|in|out]   tap a card as if at that station's reader (test)
 *   travel card block <id>              hotlist / un-hotlist
 *   travel bags                         every bag in the system
 *   travel park                         live parking sessions
 *   travel phone [home|plan A|B|dep S|wallet|trains|alerts]   the phone app's screen in chat
 *   travel selftest                     fares, capping, grace, incomplete journeys, parking, bags, the planner
 * </pre>
 */
public final class TravelCommand {
    private TravelCommand() {}

    public static final String USAGE = "travel [card [new|topup|tap|block]|bags|park|phone <op>|selftest]";

    public static boolean command(World w, ICommandSender s, String[] a) {
        if (a.length == 0 || !a[0].equalsIgnoreCase("travel")) return false;
        try { run(w, s, a); } catch (RuntimeException | LinkageError e) { say(s, "§c" + e); }
        return true;
    }

    private static void say(ICommandSender s, String t) { s.sendMessage(new TextComponentString(t)); }
    private static boolean op(ICommandSender s) { if (s.canUseCommand(2, "irextras")) return true; say(s, "§cNeeds op"); return false; }

    private static void run(World w, ICommandSender s, String[] a) {
        TravelData d = TravelData.get(w);
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "" -> {
                long live = d.parks.values().stream().filter(TravelData.Park::live).count();
                long onTrain = d.bags.values().stream().filter(b -> b.state == TravelData.ON_TRAIN).count();
                say(s, "§bPassenger travel: §f" + d.cards.size() + " cards · " + d.bags.size() + " bags (" + onTrain + " on trains) · " + live + " cars parked on ticket");
                say(s, "§7Fares " + Coins.fmt(d.fares) + " · top-ups " + Coins.fmt(d.topups) + " · locker rent " + Coins.fmt(d.rent) + " · bag fees " + Coins.fmt(d.bagFees) + " · parking " + Coins.fmt(d.parking));
                for (int i = 0; i < Math.min(8, d.log.size()); i++) say(s, "§8" + d.log.get(i));
                say(s, "§8/irextras " + USAGE);
            }
            case "card" -> card(w, s, a, d);
            case "bags" -> {
                if (d.bags.isEmpty()) say(s, "§7No bags");
                for (TravelData.Bag b : d.bags.values()) say(s, "§f" + b.tag + " §7" + b.ownerName + " " + b.fromName + " > " + b.toName + " §e" + TravelData.BSTATE[b.state] + (b.trainName.isEmpty() ? "" : " §7(" + b.trainName + ")") + " §8" + b.stacks() + " stacks");
            }
            case "park" -> {
                for (TravelData.Park p : d.parks.values()) if (p.live()) say(s, "§f#" + p.id + " §7" + p.whoName + " · " + p.garage + " · due " + Coins.fmt(Parking.due(w, p)) + (Parking.paidUp(w, p) ? " §aPAID" : ""));
            }
            case "phone" -> {
                if (!(s instanceof EntityPlayerMP p)) { say(s, "§7Players only"); return; }
                String op = a.length > 2 ? a[2] : "home";
                List<String> args = new ArrayList<>();
                if (a.length > 3) args.addAll(Arrays.asList(String.join(" ", Arrays.copyOfRange(a, 3, a.length)).split("\\|")));
                PacketPhone r = Phone.answer(p, op, args);
                say(s, "§b" + r.title);
                for (String l : r.lines) say(s, "§7" + l);
            }
            case "selftest" -> selftest(w, s);
            default -> say(s, "§7/irextras " + USAGE);
        }
    }

    private static void card(World w, ICommandSender s, String[] a, TravelData d) {
        String c2 = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "";
        switch (c2) {
            case "new" -> {
                if (!op(s) || !(s instanceof EntityPlayer p)) return;
                TravelData.Card c = d.newCard(p.getUniqueID(), p.getName(), w.getTotalWorldTime());
                c.bal = a.length > 3 ? Long.parseLong(a[3]) : 2000;
                ItemStack it = ItemTravel.card(c);
                if (!p.inventory.addItemStackToInventory(it)) p.dropItem(it, false);
                say(s, "§aPride Card " + c.id + " with " + Coins.fmt(c.bal));
            }
            case "topup" -> {
                if (!op(s) || a.length < 5) return;
                TravelData.Card c = d.cards.get(Integer.parseInt(a[3]));
                if (c == null) { say(s, "§cNo card " + a[3]); return; }
                c.bal += Long.parseLong(a[4]); d.markDirty();
                say(s, "§aCard " + c.id + " balance " + Coins.fmt(c.bal));
            }
            case "block" -> {
                if (!op(s) || a.length < 4) return;
                TravelData.Card c = d.cards.get(Integer.parseInt(a[3]));
                if (c == null) { say(s, "§cNo card " + a[3]); return; }
                c.blocked = !c.blocked; d.markDirty();
                say(s, "§eCard " + c.id + (c.blocked ? " hotlisted" : " unblocked"));
            }
            case "tap" -> {
                if (!op(s) || a.length < 5) { say(s, "§7travel card tap <id> <station> [auto|in|out]"); return; }
                TravelData.Card c = d.cards.get(Integer.parseInt(a[3]));
                Long k = Phone.key(w, a[4].replace('_', ' '));
                if (c == null || k == null) { say(s, "§cNo such card / station (use _ for spaces)"); return; }
                int mode = a.length > 5 ? Arrays.asList("auto", "in", "out").indexOf(a[5].toLowerCase(Locale.ROOT)) : 0;
                ItemStack it = ItemTravel.card(c);
                Cards.Result r = Cards.tap(w, it, k, a[4].replace('_', ' '), Math.max(0, mode), "command");
                say(s, (r.ok ? "§a" : "§c") + r.msg);
            }
            default -> {
                if (!(s instanceof EntityPlayer p)) return;
                List<TravelData.Card> cs = Cards.of(w, p.getUniqueID());
                if (cs.isEmpty()) say(s, "§7You have no Pride Cards");
                for (TravelData.Card c : cs) say(s, "§b" + c.id + " §f" + Coins.fmt(c.bal) + (c.in() ? " §eIN " + c.inName : "") + (c.blocked ? " §cBLOCKED" : "") + " §7today " + Coins.fmt(c.spentDay) + " · " + c.trips + " trips");
            }
        }
    }

    // ---- selftest ----------------------------------------------------------------------------------------------------------
    private static int pass, fail;

    private static void check(ICommandSender s, String what, boolean ok, String got) {
        if (ok) pass++; else { fail++; say(s, "§c FAIL " + what + " §7(" + got + ")"); }
    }

    static void selftest(World w, ICommandSender s) {
        pass = fail = 0;
        TravelData d = TravelData.get(w);
        PassengerData pd = PassengerData.get(w);
        long now = w.getTotalWorldTime();
        long A = Long.MAX_VALUE - 101, B = Long.MAX_VALUE - 202;               // stations no world has: zone 1 each
        TravelData.Card c = d.newCard(UUID.randomUUID(), "selftest", now);
        long fares0 = d.fares, parking0 = d.parking;
        try {
            c.bal = 10000;
            ItemStack it = ItemTravel.card(c);
            // tap in / out, fare from the scheme
            Cards.Result r = Cards.tap(w, it, A, "Alpha", Cards.AUTO, "selftest");
            check(s, "tap in", r.ok && !r.out && c.in() && c.inKey == A, r.msg);
            long fare = pd.fare(w, A, B);
            r = Cards.tap(w, it, B, "Bravo", Cards.AUTO, "selftest");
            check(s, "tap out charges the fare", r.ok && r.out && !c.in() && r.charged == fare && c.bal == 10000 - fare, r.msg + " fare " + fare);
            // same station inside the grace: free
            long bal = c.bal;
            Cards.tap(w, it, A, "Alpha", Cards.AUTO, "selftest");
            r = Cards.tap(w, it, A, "Alpha", Cards.AUTO, "selftest");
            check(s, "same-station grace is free", r.charged == 0 && c.bal == bal, r.msg);
            // capping: never more than the cap in a day
            long cap = Cards.cap(w, c);
            for (int i = 0; i < 8; i++) { Cards.tap(w, it, A, "Alpha", Cards.AUTO, "selftest"); Cards.tap(w, it, B, "Bravo", Cards.AUTO, "selftest"); }
            check(s, "daily cap holds", c.spentDay <= cap && c.spentDay >= Math.min(cap, fare), "spent " + c.spentDay + " cap " + cap);
            r = Cards.tap(w, it, A, "Alpha", Cards.AUTO, "selftest");
            Cards.Result r2 = Cards.tap(w, it, B, "Bravo", Cards.AUTO, "selftest");
            check(s, "capped journey is free", c.spentDay < cap || r2.charged == 0, r2.msg);
            // exit with no tap in: maximum fare
            c.day = -1;                                                         // a new day: cap reset
            r = Cards.tap(w, it, B, "Bravo", Cards.EXIT, "selftest");
            check(s, "exit with no tap-in = max fare", r.ok && r.charged == Cards.maxFare(w), r.msg + " max " + Cards.maxFare(w));
            // too little to travel
            c.bal = TravelConfig.minTapIn - 1; c.day = -1;
            r = Cards.tap(w, it, A, "Alpha", Cards.AUTO, "selftest");
            check(s, "low balance refused", !r.ok && !c.in(), r.msg);
            // incomplete journey at an ENTRY reader somewhere else
            c.bal = 10000; c.day = -1;
            Cards.tap(w, it, A, "Alpha", Cards.AUTO, "selftest");
            long before = c.bal;
            r = Cards.tap(w, it, B, "Bravo", Cards.ENTRY, "selftest");
            check(s, "incomplete journey charged", c.bal < before && c.in() && c.inKey == B, before + " > " + c.bal);
            check(s, "card item syncs balance", it.getTagCompound().getLong("bal") == c.bal, String.valueOf(it.getTagCompound().getLong("bal")));
            // parking: fee by started hour, park & ride rate after a rail journey
            TravelData.Park p = d.newPark(now - 2500);
            p.who = c.owner;
            check(s, "parking fee 3 started hours", Parking.fee(w, p) == 3L * TravelConfig.parkPerHour, String.valueOf(Parking.fee(w, p)));
            c.lastTripEnd = -1; c.inKey = Long.MIN_VALUE;
            check(s, "no rail journey: full fee", Parking.due(w, p) == Parking.fee(w, p), String.valueOf(Parking.due(w, p)));
            c.lastTripEnd = now;
            check(s, "park & ride rate", Parking.due(w, p) == Parking.fee(w, p) * TravelConfig.parkRidePct / 100, String.valueOf(Parking.due(w, p)));
            String no = Parking.pay(w, null, p, c, "selftest");
            check(s, "pay parking by card", no == null && Parking.paidUp(w, p), String.valueOf(no));
            d.parks.remove(p.id);
            // bags: weight, packing round trip
            TravelData.Bag b = d.newBag(now);
            b.items.add(new ItemStack(Items.IRON_INGOT, 20)); b.items.add(new ItemStack(Items.BREAD, 5));
            check(s, "bag weight", b.weightKg() == 2 + (25 + 7) / 8, String.valueOf(b.weightKg()));
            check(s, "claim tag 4 digits", b.tag >= 1000 && b.tag <= 9999, String.valueOf(b.tag));
            ItemStack packed = ItemTravel.packed(b);
            List<ItemStack> back = ItemTravel.contents(packed);
            check(s, "suitcase packs + unpacks", back.size() == 2 && back.get(0).getCount() == 20 && ItemTravel.bagId(packed) == 0, back.toString());
            check(s, "suitcase knows its bag", ItemTravel.bagId(ItemTravel.suitcase(b)) == b.id, "");
            d.bags.remove(b.id);
            // lockers: sizes, small vs large
            check(s, "locker sizes", TileLeftLuggage.sizes()[0] == 9 && TileLeftLuggage.sizes()[11] == 27 && TileLeftLuggage.sizes().length == TileLeftLuggage.N, Arrays.toString(TileLeftLuggage.sizes()));
            // the planner on the real network (when it has a line with two stations)
            RailwayData rd = RailwayData.get(w);
            for (RailwayData.Line l : rd.lines().values()) {
                if (l.stations.size() < 2) continue;
                List<String> rt = Phone.route(rd, l.stations.get(0), l.stations.get(l.stations.size() - 1));
                check(s, "planner direct route on " + l.name, rt != null && !rt.isEmpty() && rd.line(rt.get(0)).stations.contains(l.stations.get(0)), String.valueOf(rt));
                break;
            }
            check(s, "baggage car names", "Pullman baggage car".toLowerCase(Locale.ROOT).matches(Baggage.CAR_RX) && !"coach".matches(Baggage.CAR_RX), "");
        } finally {
            d.cards.remove(c.id);
            d.fares = fares0; d.parking = parking0;
            d.markDirty();
        }
        say(s, (fail == 0 ? "§a" : "§c") + "travel selftest: " + pass + " passed, " + fail + " failed");
    }
}
