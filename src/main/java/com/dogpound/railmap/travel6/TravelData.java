package com.dogpound.railmap.travel6;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.*;

/**
 * Passenger travel records (batch 6b #6-10): every Pride Card (stored value, the journey it is on, today's capping,
 * its history), every checked bag (its contents live here while it is in the system, so nothing is lost when a chunk
 * unloads or a train leaves service) and every park & ride session.
 */
public final class TravelData extends WorldSavedData {
    public static final String NAME = "irextras_travel6";

    // ---- Pride Card ------------------------------------------------------------------------------------------------------
    public static final class Card {
        public int id;
        public UUID owner;
        public String ownerName = "";
        public long bal, deposit, issued, totalSpent;
        /** the journey it is on: tapped in at inKey (Long.MIN_VALUE = not in) */
        public long inKey = Long.MIN_VALUE, inAt;
        public String inName = "";
        /** today's capping: game day, paid today, zones touched today (0 = none yet), journeys */
        public long day = -1, spentDay;
        public int zlo, zhi, tripsDay, trips;
        public long lastTripEnd = -1;
        public boolean blocked;
        public final List<String> hist = new ArrayList<>();

        public boolean in() { return inKey != Long.MIN_VALUE; }

        void note(String s) { hist.add(0, s); while (hist.size() > 40) hist.remove(hist.size() - 1); }

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("id", id); if (owner != null) t.setUniqueId("o", owner); t.setString("on", ownerName);
            t.setLong("b", bal); t.setLong("dep", deposit); t.setLong("is", issued); t.setLong("ts", totalSpent);
            t.setLong("ik", inKey); t.setLong("ia", inAt); t.setString("in", inName);
            t.setLong("d", day); t.setLong("sd", spentDay); t.setInteger("zl", zlo); t.setInteger("zh", zhi); t.setInteger("td", tripsDay); t.setInteger("tr", trips);
            t.setLong("le", lastTripEnd); t.setBoolean("bl", blocked);
            NBTTagList l = new NBTTagList();
            for (String s : hist) l.appendTag(new NBTTagString(s));
            t.setTag("h", l);
            return t;
        }

        static Card read(NBTTagCompound t) {
            Card c = new Card();
            c.id = t.getInteger("id"); c.owner = t.hasUniqueId("o") ? t.getUniqueId("o") : null; c.ownerName = t.getString("on");
            c.bal = t.getLong("b"); c.deposit = t.getLong("dep"); c.issued = t.getLong("is"); c.totalSpent = t.getLong("ts");
            c.inKey = t.getLong("ik"); c.inAt = t.getLong("ia"); c.inName = t.getString("in");
            c.day = t.getLong("d"); c.spentDay = t.getLong("sd"); c.zlo = t.getInteger("zl"); c.zhi = t.getInteger("zh"); c.tripsDay = t.getInteger("td"); c.trips = t.getInteger("tr");
            c.lastTripEnd = t.hasKey("le") ? t.getLong("le") : -1; c.blocked = t.getBoolean("bl");
            NBTTagList l = t.getTagList("h", 8);
            for (int i = 0; i < l.tagCount(); i++) c.hist.add(l.getStringTagAt(i));
            return c;
        }
    }

    // ---- checked baggage -------------------------------------------------------------------------------------------------
    public static final String[] BSTATE = {"CHECKED IN", "MAKE-UP", "ON TRAIN", "ON CAROUSEL", "COLLECTED"};
    public static final int CHECKED = 0, MAKEUP = 1, ON_TRAIN = 2, RECLAIM = 3, COLLECTED = 4;

    public static final class Bag {
        public int id, tag, state;
        public UUID owner, train;
        public String ownerName = "", fromName = "", toName = "", trainName = "";
        public long fromKey, toKey, checked, moved;
        public final List<ItemStack> items = new ArrayList<>();
        public final List<String> log = new ArrayList<>();

        public int stacks() { return items.size(); }
        public int weightKg() { int n = 0; for (ItemStack s : items) n += s.getCount(); return 2 + (n + 7) / 8; }
        void note(String s) { log.add(s); while (log.size() > 20) log.remove(0); }

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("id", id); t.setInteger("tag", tag); t.setInteger("s", state);
            if (owner != null) t.setUniqueId("o", owner);
            if (train != null) t.setUniqueId("tr", train);
            t.setString("on", ownerName); t.setString("fn", fromName); t.setString("tn", toName); t.setString("trn", trainName);
            t.setLong("fk", fromKey); t.setLong("tk", toKey); t.setLong("c", checked); t.setLong("m", moved);
            NBTTagList l = new NBTTagList();
            for (ItemStack s : items) l.appendTag(s.writeToNBT(new NBTTagCompound()));
            t.setTag("it", l);
            NBTTagList g = new NBTTagList();
            for (String s : log) g.appendTag(new NBTTagString(s));
            t.setTag("log", g);
            return t;
        }

        static Bag read(NBTTagCompound t) {
            Bag b = new Bag();
            b.id = t.getInteger("id"); b.tag = t.getInteger("tag"); b.state = t.getInteger("s");
            b.owner = t.hasUniqueId("o") ? t.getUniqueId("o") : null;
            b.train = t.hasUniqueId("tr") ? t.getUniqueId("tr") : null;
            b.ownerName = t.getString("on"); b.fromName = t.getString("fn"); b.toName = t.getString("tn"); b.trainName = t.getString("trn");
            b.fromKey = t.getLong("fk"); b.toKey = t.getLong("tk"); b.checked = t.getLong("c"); b.moved = t.getLong("m");
            NBTTagList l = t.getTagList("it", 10);
            for (int i = 0; i < l.tagCount(); i++) { ItemStack s = new ItemStack(l.getCompoundTagAt(i)); if (!s.isEmpty()) b.items.add(s); }
            NBTTagList g = t.getTagList("log", 8);
            for (int i = 0; i < g.tagCount(); i++) b.log.add(g.getStringTagAt(i));
            return b;
        }
    }

    // ---- park & ride -----------------------------------------------------------------------------------------------------
    public static final class Park {
        public int id;
        public UUID who;
        public String whoName = "", garage = "";
        public long in, paidAt = -1, out = -1, paid;
        public int card;               // the Pride Card that paid (0 = bank)
        public boolean rail;           // park & ride rate applied

        public boolean live() { return out < 0; }

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("id", id); if (who != null) t.setUniqueId("w", who); t.setString("wn", whoName); t.setString("g", garage);
            t.setLong("in", in); t.setLong("pa", paidAt); t.setLong("out", out); t.setLong("p", paid); t.setInteger("c", card); t.setBoolean("r", rail);
            return t;
        }

        static Park read(NBTTagCompound t) {
            Park p = new Park();
            p.id = t.getInteger("id"); p.who = t.hasUniqueId("w") ? t.getUniqueId("w") : null; p.whoName = t.getString("wn"); p.garage = t.getString("g");
            p.in = t.getLong("in"); p.paidAt = t.getLong("pa"); p.out = t.getLong("out"); p.paid = t.getLong("p"); p.card = t.getInteger("c"); p.rail = t.getBoolean("r");
            return p;
        }
    }

    public final Map<Integer, Card> cards = new LinkedHashMap<>();
    public final Map<Integer, Bag> bags = new LinkedHashMap<>();
    public final Map<Integer, Park> parks = new LinkedHashMap<>();
    public final List<String> log = new ArrayList<>();
    public int nextCard = 1, nextBag = 1, nextPark = 1;
    /** totals for the console: card fares, top-ups, locker rent, bag fees, parking (cents) */
    public long fares, topups, rent, bagFees, parking;

    public TravelData() { super(NAME); }
    public TravelData(String n) { super(n); }

    public static TravelData get(World w) {
        MapStorage s = w.getMapStorage();                       // world-wide: a card bought in one dimension works in all
        if (s == null) s = w.getPerWorldStorage();
        TravelData d = (TravelData) s.getOrLoadData(TravelData.class, NAME);
        if (d == null) { d = new TravelData(); s.setData(NAME, d); }
        return d;
    }

    public void note(String s) { log.add(0, s); while (log.size() > 60) log.remove(log.size() - 1); markDirty(); }

    public Card newCard(UUID owner, String name, long now) {
        Card c = new Card();
        c.id = 7310000 + nextCard++;                             // the number printed on the card
        while (cards.containsKey(c.id)) c.id++;
        c.owner = owner; c.ownerName = name; c.issued = now;
        cards.put(c.id, c);
        markDirty();
        return c;
    }

    public Bag newBag(long now) {
        Bag b = new Bag();
        b.id = nextBag++;
        b.tag = 1000 + (b.id * 7919) % 9000;                    // the 4-digit number on the claim tag
        b.checked = b.moved = now;
        bags.put(b.id, b);
        markDirty();
        return b;
    }

    public Park newPark(long now) {
        Park p = new Park();
        p.id = nextPark++;
        p.in = now;
        parks.put(p.id, p);
        markDirty();
        return p;
    }

    /** forget collected bags and finished parking after a while (game days) */
    public void tidy(long now) {
        boolean ch = bags.values().removeIf(b -> b.state == COLLECTED && now - b.moved > 24000L * 3);
        ch |= parks.values().removeIf(p -> !p.live() && now - p.out > 24000L * 2);
        if (ch) markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        cards.clear(); bags.clear(); parks.clear(); log.clear();
        NBTTagList l = t.getTagList("cards", 10);
        for (int i = 0; i < l.tagCount(); i++) { Card c = Card.read(l.getCompoundTagAt(i)); cards.put(c.id, c); }
        l = t.getTagList("bags", 10);
        for (int i = 0; i < l.tagCount(); i++) { Bag b = Bag.read(l.getCompoundTagAt(i)); bags.put(b.id, b); }
        l = t.getTagList("parks", 10);
        for (int i = 0; i < l.tagCount(); i++) { Park p = Park.read(l.getCompoundTagAt(i)); parks.put(p.id, p); }
        l = t.getTagList("log", 8);
        for (int i = 0; i < l.tagCount(); i++) log.add(l.getStringTagAt(i));
        nextCard = Math.max(1, t.getInteger("nc")); nextBag = Math.max(1, t.getInteger("nb")); nextPark = Math.max(1, t.getInteger("np"));
        fares = t.getLong("fa"); topups = t.getLong("tu"); rent = t.getLong("re"); bagFees = t.getLong("bf"); parking = t.getLong("pk");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        NBTTagList l = new NBTTagList();
        for (Card c : cards.values()) l.appendTag(c.write());
        t.setTag("cards", l);
        l = new NBTTagList();
        for (Bag b : bags.values()) l.appendTag(b.write());
        t.setTag("bags", l);
        l = new NBTTagList();
        for (Park p : parks.values()) l.appendTag(p.write());
        t.setTag("parks", l);
        l = new NBTTagList();
        for (String s : log) l.appendTag(new NBTTagString(s));
        t.setTag("log", l);
        t.setInteger("nc", nextCard); t.setInteger("nb", nextBag); t.setInteger("np", nextPark);
        t.setLong("fa", fares); t.setLong("tu", topups); t.setLong("re", rent); t.setLong("bf", bagFees); t.setLong("pk", parking);
        return t;
    }
}
