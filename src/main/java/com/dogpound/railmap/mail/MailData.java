package com.dogpound.railmap.mail;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.*;

/**
 * Mail by train (batch 5 #8): every letter and parcel posted at a Sorting Office, every mail bag, and every player's
 * registered post office (PO box). While a piece of mail is inside the system the item itself lives in its record
 * (like a parcel's), so nothing is lost when chunks unload or a train leaves service.
 */
public final class MailData extends WorldSavedData {
    public static final String NAME = "irextras_mail";
    public static final String[] STATE = {"POSTED", "SORTED", "BAGGED", "ON TRAIN", "ARRIVED", "OUT FOR DELIVERY", "DELIVERED", "AT PICKUP", "COLLECTED", "RETURNED"};
    public static final int POSTED = 0, SORTED = 1, BAGGED = 2, ON_TRAIN = 3, ARRIVED = 4, OUT = 5, DELIVERED = 6, PICKUP = 7, COLLECTED = 8, RETURNED = 9;
    public static final String[] BSTATE = {"WAITING", "ON TRAIN", "ARRIVED", "OPENED"};

    public static final class Mail {
        public int id, kind, state, bag;                 // kind 0 letter, 1 parcel
        public String from = "", to = "", fromName = "", destName = "", trainName = "";
        public UUID toPlayer, fromPlayer, train;
        public long fromKey, destKey, office, posted, moved, sortAt;
        /** on a TPO train, not yet sorted into a bag (sorted en route) */
        public boolean loose;
        public ItemStack stack = ItemStack.EMPTY;
        public final List<String> log = new ArrayList<>();

        public boolean live() { return state < DELIVERED || state == PICKUP; }
        public String what() { return kind == 1 ? "parcel" : "letter"; }

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("id", id); t.setInteger("k", kind); t.setInteger("s", state); t.setInteger("b", bag);
            t.setString("f", from); t.setString("to", to); t.setString("fn", fromName); t.setString("dn", destName); t.setString("tn", trainName);
            if (toPlayer != null) t.setUniqueId("tp", toPlayer);
            if (fromPlayer != null) t.setUniqueId("fp", fromPlayer);
            if (train != null) t.setUniqueId("tr", train);
            t.setLong("fk", fromKey); t.setLong("dk", destKey); t.setLong("o", office); t.setLong("p", posted); t.setLong("m", moved); t.setLong("sa", sortAt);
            t.setBoolean("l", loose);
            t.setTag("st", stack.writeToNBT(new NBTTagCompound()));
            NBTTagList l = new NBTTagList();
            for (String s : log) l.appendTag(new NBTTagString(s));
            t.setTag("log", l);
            return t;
        }

        static Mail read(NBTTagCompound t) {
            Mail m = new Mail();
            m.id = t.getInteger("id"); m.kind = t.getInteger("k"); m.state = t.getInteger("s"); m.bag = t.getInteger("b");
            m.from = t.getString("f"); m.to = t.getString("to"); m.fromName = t.getString("fn"); m.destName = t.getString("dn"); m.trainName = t.getString("tn");
            m.toPlayer = t.hasUniqueId("tp") ? t.getUniqueId("tp") : null;
            m.fromPlayer = t.hasUniqueId("fp") ? t.getUniqueId("fp") : null;
            m.train = t.hasUniqueId("tr") ? t.getUniqueId("tr") : null;
            m.fromKey = t.getLong("fk"); m.destKey = t.getLong("dk"); m.office = t.getLong("o"); m.posted = t.getLong("p"); m.moved = t.getLong("m"); m.sortAt = t.getLong("sa");
            m.loose = t.getBoolean("l");
            m.stack = new ItemStack(t.getCompoundTag("st"));
            NBTTagList l = t.getTagList("log", 8);
            for (int i = 0; i < l.tagCount(); i++) m.log.add(l.getStringTagAt(i));
            return m;
        }
    }

    public static final class Bag {
        public int id, seal, state;
        public long toKey, fromKey, at, made, moved;
        public String toName = "", fromName = "", trainName = "";
        public UUID train;
        public final List<Integer> items = new ArrayList<>();

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("id", id); t.setInteger("seal", seal); t.setInteger("s", state);
            t.setLong("tk", toKey); t.setLong("fk", fromKey); t.setLong("at", at); t.setLong("made", made); t.setLong("m", moved);
            t.setString("tn", toName); t.setString("fn", fromName); t.setString("trn", trainName);
            if (train != null) t.setUniqueId("tr", train);
            t.setIntArray("items", items.stream().mapToInt(Integer::intValue).toArray());
            return t;
        }

        static Bag read(NBTTagCompound t) {
            Bag b = new Bag();
            b.id = t.getInteger("id"); b.seal = t.getInteger("seal"); b.state = t.getInteger("s");
            b.toKey = t.getLong("tk"); b.fromKey = t.getLong("fk"); b.at = t.getLong("at"); b.made = t.getLong("made"); b.moved = t.getLong("m");
            b.toName = t.getString("tn"); b.fromName = t.getString("fn"); b.trainName = t.getString("trn");
            b.train = t.hasUniqueId("tr") ? t.getUniqueId("tr") : null;
            for (int i : t.getIntArray("items")) b.items.add(i);
            return b;
        }
    }

    public final Map<Integer, Mail> mail = new LinkedHashMap<>();
    public final Map<Integer, Bag> bags = new LinkedHashMap<>();
    /** a player's registered post office (station key) and name, for addressing */
    public final Map<UUID, Long> poBox = new HashMap<>();
    public final Map<UUID, String> names = new HashMap<>();
    public final LinkedList<String> log = new LinkedList<>();
    public int nextMail = 1, nextBag = 1, nextSeal = 100001;
    public long postedTotal, deliveredTotal, robotTotal, pickupTotal;

    public MailData() { super(NAME); }
    public MailData(String n) { super(n); }

    public static MailData get(World w) {
        MapStorage st = w.getPerWorldStorage();
        MailData d = (MailData) st.getOrLoadData(MailData.class, NAME);
        if (d == null) { d = new MailData(); st.setData(NAME, d); }
        return d;
    }

    public Mail newMail(long now) {
        Mail m = new Mail();
        m.id = nextMail++;
        m.posted = m.moved = now;
        mail.put(m.id, m);
        postedTotal++;
        prune();
        markDirty();
        return m;
    }

    public Bag newBag(long now, long fromKey, String fromName, long toKey, String toName) {
        Bag b = new Bag();
        b.id = nextBag++; b.seal = nextSeal++;
        b.fromKey = fromKey; b.fromName = fromName; b.toKey = toKey; b.toName = toName; b.made = b.moved = now;
        bags.put(b.id, b);
        markDirty();
        return b;
    }

    public void note(String s) { log.addFirst(s); while (log.size() > 80) log.removeLast(); markDirty(); }

    /** finished mail (delivered / collected / returned) beyond the newest 300 and opened bags beyond 200 are forgotten */
    private void prune() {
        int done = 0;
        for (Mail m : mail.values()) if (!m.live()) done++;
        for (Iterator<Mail> it = mail.values().iterator(); it.hasNext() && done > 300; ) if (!it.next().live()) { it.remove(); done--; }
        int open = 0;
        for (Bag b : bags.values()) if (b.state == 3) open++;
        for (Iterator<Bag> it = bags.values().iterator(); it.hasNext() && open > 200; ) if (it.next().state == 3) { it.remove(); open--; }
    }

    public void track(Mail m, long now, String what) { m.moved = now; m.log.add(now + "|" + what); while (m.log.size() > 24) m.log.remove(0); markDirty(); }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        mail.clear(); bags.clear(); poBox.clear(); names.clear(); log.clear();
        nextMail = Math.max(1, t.getInteger("nm")); nextBag = Math.max(1, t.getInteger("nb")); nextSeal = Math.max(100001, t.getInteger("ns"));
        postedTotal = t.getLong("pt"); deliveredTotal = t.getLong("dt"); robotTotal = t.getLong("rt"); pickupTotal = t.getLong("kt");
        NBTTagList l = t.getTagList("mail", 10);
        for (int i = 0; i < l.tagCount(); i++) { Mail m = Mail.read(l.getCompoundTagAt(i)); mail.put(m.id, m); }
        l = t.getTagList("bags", 10);
        for (int i = 0; i < l.tagCount(); i++) { Bag b = Bag.read(l.getCompoundTagAt(i)); bags.put(b.id, b); }
        l = t.getTagList("po", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            if (!c.hasUniqueId("u")) continue;
            poBox.put(c.getUniqueId("u"), c.getLong("k"));
            names.put(c.getUniqueId("u"), c.getString("n"));
        }
        l = t.getTagList("log", 8);
        for (int i = 0; i < l.tagCount(); i++) log.add(l.getStringTagAt(i));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        t.setInteger("nm", nextMail); t.setInteger("nb", nextBag); t.setInteger("ns", nextSeal);
        t.setLong("pt", postedTotal); t.setLong("dt", deliveredTotal); t.setLong("rt", robotTotal); t.setLong("kt", pickupTotal);
        NBTTagList l = new NBTTagList();
        for (Mail m : mail.values()) l.appendTag(m.write());
        t.setTag("mail", l);
        l = new NBTTagList();
        for (Bag b : bags.values()) l.appendTag(b.write());
        t.setTag("bags", l);
        l = new NBTTagList();
        for (Map.Entry<UUID, Long> e : poBox.entrySet()) {
            NBTTagCompound c = new NBTTagCompound();
            c.setUniqueId("u", e.getKey()); c.setLong("k", e.getValue()); c.setString("n", names.getOrDefault(e.getKey(), ""));
            l.appendTag(c);
        }
        t.setTag("po", l);
        l = new NBTTagList();
        for (String s : log) l.appendTag(new NBTTagString(s));
        t.setTag("log", l);
        return t;
    }
}
