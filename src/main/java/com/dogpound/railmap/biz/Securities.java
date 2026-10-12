package com.dogpound.railmap.biz;

import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.economy.CompanyData;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.*;

/**
 * The railway stock exchange (her "next 48" #36) - Pride coins only, and closed-loop: money only ever moves between
 * players and companies, never out of thin air.
 * <ul>
 *   <li><b>Shares</b>: a company LISTS by putting part of the owners' holding on offer (the IPO proceeds go to the
 *   company). A limit-order book per company: BUY orders escrow the buyer's coins, SELL orders escrow the seller's
 *   shares; crossing orders trade at the resting order's price, shares move in the company's own share register
 *   (the one the Company HQ pays dividends on), sellers who are offline are paid when they next come online.</li>
 *   <li><b>Bonds</b>: a company issues bonds (units of $100, a weekly coupon, a term in weeks); players buy them on the
 *   board; coupons are paid weekly and the principal at maturity - a company that can't pay DEFAULTS (reputation).</li>
 *   <li><b>Project loans</b>: the Railway Development Bank lends against a construction project; the money goes straight
 *   into the site's budget and onto the company's loan (interest as usual).</li>
 *   <li>The index, each listing's last price, its fair value (book value per share), the dividend yield, volume, and the
 *   best bid / ask are on the Stock Exchange Board and the console.</li>
 * </ul>
 */
public final class Securities implements BizData.Part {
    public static final class Order {
        public int id, company, qty;            // qty in thousandths of the company
        public boolean buy;
        public long price, placed;              // cents per share
        public UUID who;
        public String whoName = "";
        public boolean issuer;                  // an IPO sell order: proceeds to the company
    }

    public static final class Listing {
        public int company;
        public long last, prev, volumeDay, listed;
        public final long[] hist = new long[24];
    }

    public static final class Bond {
        public int id, company, units, sold, weeks, couponBp;   // coupon basis points per week
        public long issued, matures, lastCoupon;
        public String state = "OPEN";           // OPEN (on sale), LIVE, REPAID, DEFAULTED
        public final Map<UUID, Integer> holders = new LinkedHashMap<>();
    }

    public static final class Loan {
        public int company;
        public long site, amount, taken;
        public String project = "";
    }

    public final Map<Integer, Listing> listings = new LinkedHashMap<>();
    public final List<Order> orders = new ArrayList<>();
    public final List<Bond> bonds = new ArrayList<>();
    public final List<Loan> loans = new ArrayList<>();
    public final Map<UUID, Long> owed = new HashMap<>();
    public final LinkedList<String> tape = new LinkedList<>();
    private int nextId = 1;

    @Override public String key() { return "securities"; }

    private void tape(World w, String s) { tape.addFirst(BizData.hhmm(w.getTotalWorldTime()) + " " + s); while (tape.size() > 40) tape.removeLast(); }

    public static long fair(Company c) { return Math.max(1, c.sharePrice(0)); }

    public long index(World w) {
        long s = 0; int n = 0;
        for (Listing l : listings.values()) { s += l.last; n++; }
        return n == 0 ? 1000 : s / n;
    }

    public long bestBid(int c) { long b = 0; for (Order o : orders) if (o.company == c && o.buy) b = Math.max(b, o.price); return b; }
    public long bestAsk(int c) { long a = 0; for (Order o : orders) if (o.company == c && !o.buy && (a == 0 || o.price < a)) a = o.price; return a; }

    // ---- listing + orders ---------------------------------------------------------------------------------------------
    public String list(World w, Company c, EntityPlayerMP by, int qty, long price) {
        if (!BizConfig.stockExchange) return "§cThe stock exchange is closed (config)";
        Integer own = c.shares.get(by.getUniqueID());
        if (own == null || own < qty) return "§cYou hold " + (own == null ? 0 : own) + "/1000 of " + c.mark;
        if (qty < 1) return "§cOffer at least 1/1000";
        Listing l = listings.computeIfAbsent(c.id, k -> { Listing x = new Listing(); x.company = c.id; x.last = x.prev = fair(c); x.listed = w.getTotalWorldTime(); Arrays.fill(x.hist, x.last); return x; });
        c.shares.put(by.getUniqueID(), own - qty);
        Order o = new Order();
        o.id = nextId++; o.company = c.id; o.qty = qty; o.buy = false; o.price = price > 0 ? price : l.last; o.who = by.getUniqueID(); o.whoName = c.name; o.issuer = true; o.placed = w.getTotalWorldTime();
        orders.add(o);
        tape(w, c.mark + " lists " + qty + " shares at " + Coins.fmt(o.price));
        match(w, c);
        return "§a" + c.mark + " listed: " + qty + "/1000 offered at " + Coins.fmt(o.price) + " a share (IPO proceeds to the company)";
    }

    public String order(World w, Company c, EntityPlayerMP p, boolean buy, int qty, long price) {
        if (!BizConfig.stockExchange) return "§cThe stock exchange is closed (config)";
        Listing l = listings.get(c.id);
        if (l == null) return "§c" + c.mark + " isn't listed";
        if (qty < 1 || price < 1) return "§cQuantity and price must be positive";
        if (buy) {
            if (!Coins.charge(p, qty * price, "Escrow: buy " + qty + " " + c.mark)) return "§cYour bank can't cover " + Coins.fmt(qty * price);
        } else {
            Integer own = c.shares.get(p.getUniqueID());
            if (own == null || own < qty) return "§cYou hold " + (own == null ? 0 : own) + " " + c.mark + " shares";
            c.shares.put(p.getUniqueID(), own - qty);
        }
        Order o = new Order();
        o.id = nextId++; o.company = c.id; o.qty = qty; o.buy = buy; o.price = price; o.who = p.getUniqueID(); o.whoName = p.getName(); o.placed = w.getTotalWorldTime();
        orders.add(o);
        int traded = match(w, c);
        return "§aOrder #" + o.id + ": " + (buy ? "BUY " : "SELL ") + qty + " " + c.mark + " at " + Coins.fmt(price) + (traded > 0 ? " - " + traded + " traded" : " - resting on the book");
    }

    public String cancel(World w, Company c, EntityPlayerMP p, int id) {
        for (Iterator<Order> it = orders.iterator(); it.hasNext(); ) {
            Order o = it.next();
            if (o.id != id || !p.getUniqueID().equals(o.who)) continue;
            it.remove();
            Company k = CompanyData.get(w).byId(o.company);
            if (o.buy) credit(w, o.who, o.qty * o.price, "Order #" + id + " cancelled - escrow back");
            else if (k != null) k.shares.merge(o.who, o.qty, Integer::sum);
            return "§eOrder #" + id + " cancelled";
        }
        return "§7No order #" + id + " of yours";
    }

    /** cross the book: best bid >= best ask trades at the resting (older) order's price */
    int match(World w, Company c) {
        int traded = 0;
        Listing l = listings.get(c.id);
        while (true) {
            Order b = null, a = null;
            for (Order o : orders) if (o.company == c.id) { if (o.buy && (b == null || o.price > b.price)) b = o; if (!o.buy && (a == null || o.price < a.price)) a = o; }
            if (b == null || a == null || b.price < a.price) break;
            int q = Math.min(b.qty, a.qty);
            long px = b.placed <= a.placed ? b.price : a.price;
            c.shares.merge(b.who, q, Integer::sum);
            long proceeds = q * px;
            if (a.issuer) CompanyData.get(w).credit(w, c, Company.Kind.BOND_ISSUE, proceeds, "Share issue: " + q + " to " + b.whoName);
            else credit(w, a.who, proceeds, "Sold " + q + " " + c.mark + " shares");
            if (b.price > px) credit(w, b.who, q * (b.price - px), "Price improvement on " + c.mark);
            b.qty -= q; a.qty -= q;
            if (b.qty == 0) orders.remove(b);
            if (a.qty == 0) orders.remove(a);
            if (l != null) { l.last = px; l.volumeDay += q; }
            tape(w, c.mark + " " + q + " @ " + Coins.fmt(px) + " " + a.whoName + " > " + b.whoName);
            traded += q;
        }
        return traded;
    }

    /** money to a player: now if online, else when they come back */
    void credit(World w, UUID who, long cents, String why) {
        if (cents <= 0 || who == null) return;
        EntityPlayerMP p = CompanyData.online(w, who);
        if (p != null && Coins.pay(p, cents, why)) return;
        owed.merge(who, cents, Long::sum);
    }

    // ---- bonds ------------------------------------------------------------------------------------------------------
    public String issueBond(World w, Company c, int units, int weeks, int couponBp) {
        if (!BizConfig.stockExchange) return "§cThe stock exchange is closed (config)";
        if (Research.locked(w, c, Research.Tech.CORPORATE_FINANCE)) return Research.why(Research.Tech.CORPORATE_FINANCE);
        if (units < 1 || units > 500 || weeks < 1 || weeks > 52 || couponBp < 10 || couponBp > 2000) return "§cUnits 1-500, term 1-52 weeks, coupon 10-2000 bp a week";
        Bond b = new Bond();
        b.id = nextId++; b.company = c.id; b.units = units; b.weeks = weeks; b.couponBp = couponBp; b.issued = w.getTotalWorldTime();
        bonds.add(b);
        tape(w, c.mark + " issues bond B" + b.id + ": " + units + " x $100, " + couponBp / 100.0 + "%/wk, " + weeks + " wk");
        return "§aBond B" + b.id + " on sale: " + units + " x $100, coupon " + String.format(Locale.ROOT, "%.2f", couponBp / 100.0) + "% a week, " + weeks + " weeks";
    }

    public String buyBond(World w, EntityPlayerMP p, Bond b, int units) {
        if (!b.state.equals("OPEN") && !b.state.equals("LIVE") || b.sold + units > b.units) return "§cBond B" + b.id + " has " + (b.units - b.sold) + " units left";
        Company c = CompanyData.get(w).byId(b.company);
        if (c == null) return "§cThe issuer is gone";
        if (!Coins.charge(p, units * 10_000L, "Bond B" + b.id + " x" + units)) return "§cYour bank can't cover " + Coins.fmt(units * 10_000L);
        CompanyData.get(w).credit(w, c, Company.Kind.BOND_ISSUE, units * 10_000L, "Bond B" + b.id + " sold to " + p.getName());
        b.sold += units;
        b.holders.merge(p.getUniqueID(), units, Integer::sum);
        if (b.state.equals("OPEN")) { b.state = "LIVE"; b.lastCoupon = w.getTotalWorldTime(); b.matures = w.getTotalWorldTime() + b.weeks * 7L * BizData.DAY; }
        return "§aBought " + units + " x B" + b.id + " (" + c.mark + ") - coupon weekly, principal back on day " + b.matures / BizData.DAY;
    }

    // ---- project loans ------------------------------------------------------------------------------------------------
    public String projectLoan(World w, Company c, BlockPos site, long amount) {
        if (Research.locked(w, c, Research.Tech.CORPORATE_FINANCE)) return Research.why(Research.Tech.CORPORATE_FINANCE);
        if (!(w.getTileEntity(site) instanceof com.dogpound.railmap.industry.TileConstructionSite s)) return "§cNo construction site office there";
        long cap = s.estimate() * 3 / 2 - s.budget();
        if (amount <= 0 || amount > cap) return "§cThe bank lends up to " + Coins.fmt(Math.max(0, cap)) + " against this project";
        s.fund(amount, c.mark + " project loan");
        c.loan += amount;
        CompanyData.get(w).markDirty();
        Loan l = new Loan();
        l.company = c.id; l.site = site.toLong(); l.amount = amount; l.taken = w.getTotalWorldTime(); l.project = s.projectName();
        loans.add(l);
        tape(w, c.mark + " project loan " + Coins.fmt(amount) + " for " + l.project);
        return "§aProject loan of " + Coins.fmt(amount) + " paid into the " + l.project.toLowerCase(Locale.ROOT) + " budget (on " + c.mark + "'s loan account)";
    }

    // ---- the clock ---------------------------------------------------------------------------------------------------
    @Override
    public void tick(World w, BizData d, long now) {
        if (owed.isEmpty()) return;
        for (Iterator<Map.Entry<UUID, Long>> it = owed.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> e = it.next();
            EntityPlayerMP p = CompanyData.online(w, e.getKey());
            if (p != null && Coins.pay(p, e.getValue(), "Stock exchange settlement")) {
                p.sendMessage(new TextComponentString("§a📈 Stock exchange settlement paid: §6" + Coins.fmt(e.getValue())));
                it.remove();
            }
        }
    }

    @Override
    public void daily(World w, BizData d, long day) {
        CompanyData cd = CompanyData.get(w);
        listings.values().removeIf(l -> cd.byId(l.company) == null);
        for (Listing l : listings.values()) {
            Company c = cd.byId(l.company);
            l.prev = l.last;
            if (l.volumeDay == 0) l.last = Math.max(1, Math.round(l.last * 0.8 + fair(c) * 0.2));   // no trades: drifts toward book value
            System.arraycopy(l.hist, 0, l.hist, 1, l.hist.length - 1);
            l.hist[0] = l.last;
            l.volumeDay = 0;
        }
        long now = w.getTotalWorldTime();
        for (Bond b : bonds) {
            if (!b.state.equals("LIVE")) continue;
            Company c = cd.byId(b.company);
            if (c == null) { b.state = "DEFAULTED"; continue; }
            if (now - b.lastCoupon >= 7 * BizData.DAY) {
                long coupon = b.sold * 10_000L * b.couponBp / 10_000;
                if (BizData.pay(w, c, Company.Kind.COUPON, coupon, "Coupon on bond B" + b.id)) {
                    for (Map.Entry<UUID, Integer> h : b.holders.entrySet()) credit(w, h.getKey(), coupon * h.getValue() / Math.max(1, b.sold), "Coupon B" + b.id);
                    b.lastCoupon = now;
                } else default_(w, d, c, b);
            }
            if (b.state.equals("LIVE") && now >= b.matures) {
                long principal = b.sold * 10_000L;
                if (BizData.pay(w, c, Company.Kind.REPAY, principal, "Bond B" + b.id + " redeemed")) {
                    for (Map.Entry<UUID, Integer> h : b.holders.entrySet()) credit(w, h.getKey(), h.getValue() * 10_000L, "Bond B" + b.id + " principal");
                    b.state = "REPAID";
                    d.note(w, c.mark + " repaid bond B" + b.id + " in full");
                } else default_(w, d, c, b);
            }
        }
    }

    private static void default_(World w, BizData d, Company c, Bond b) {
        b.state = "DEFAULTED";
        c.reputation = Math.max(0, c.reputation - 20);
        d.note(w, c.mark + " DEFAULTED on bond B" + b.id);
        BizData.alarm(w, "bond:" + b.id, com.dogpound.railmap.alarm.AlarmManager.Sev.MAJOR, c.name + " defaulted on bond B" + b.id, null);
    }

    /** merger: the target's securities become the acquirer's */
    public void transfer(int from, int to) {
        for (Bond b : bonds) if (b.company == from) b.company = to;
        for (Loan l : loans) if (l.company == from) l.company = to;
        orders.removeIf(o -> o.company == from && o.issuer);
        for (Order o : orders) if (o.company == from) o.company = to;
        listings.remove(from);
    }

    // ---- save -------------------------------------------------------------------------------------------------------
    @Override
    public void read(NBTTagCompound t) {
        listings.clear(); orders.clear(); bonds.clear(); loans.clear(); owed.clear(); tape.clear();
        NBTTagList l = t.getTagList("ls", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            Listing x = new Listing();
            x.company = c.getInteger("c"); x.last = c.getLong("l"); x.prev = c.getLong("p"); x.volumeDay = c.getLong("v"); x.listed = c.getLong("t");
            int[] h = c.getIntArray("h");
            for (int k = 0; k < x.hist.length; k++) x.hist[k] = k < h.length ? h[k] : x.last;
            listings.put(x.company, x);
        }
        l = t.getTagList("or", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            Order o = new Order();
            o.id = c.getInteger("id"); o.company = c.getInteger("c"); o.qty = c.getInteger("q"); o.buy = c.getBoolean("b"); o.price = c.getLong("p"); o.placed = c.getLong("t");
            if (c.hasUniqueId("w")) o.who = c.getUniqueId("w");
            o.whoName = c.getString("wn"); o.issuer = c.getBoolean("is");
            orders.add(o);
        }
        l = t.getTagList("bd", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            Bond b = new Bond();
            b.id = c.getInteger("id"); b.company = c.getInteger("c"); b.units = c.getInteger("u"); b.sold = c.getInteger("s"); b.weeks = c.getInteger("wk"); b.couponBp = c.getInteger("cp");
            b.issued = c.getLong("i"); b.matures = c.getLong("m"); b.lastCoupon = c.getLong("lc"); b.state = c.getString("st");
            NBTTagCompound h = c.getCompoundTag("h");
            for (String k : h.getKeySet()) try { b.holders.put(UUID.fromString(k), h.getInteger(k)); } catch (IllegalArgumentException ignored) { }
            bonds.add(b);
        }
        l = t.getTagList("ln", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            Loan x = new Loan();
            x.company = c.getInteger("c"); x.site = c.getLong("s"); x.amount = c.getLong("a"); x.taken = c.getLong("t"); x.project = c.getString("p");
            loans.add(x);
        }
        NBTTagCompound ow = t.getCompoundTag("owed");
        for (String k : ow.getKeySet()) try { owed.put(UUID.fromString(k), ow.getLong(k)); } catch (IllegalArgumentException ignored) { }
        NBTTagList tp = t.getTagList("tape", 8);
        for (int i = 0; i < tp.tagCount(); i++) tape.add(tp.getStringTagAt(i));
        nextId = Math.max(1, t.getInteger("next"));
    }

    @Override
    public NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        NBTTagList l = new NBTTagList();
        for (Listing x : listings.values()) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("c", x.company); c.setLong("l", x.last); c.setLong("p", x.prev); c.setLong("v", x.volumeDay); c.setLong("t", x.listed);
            int[] h = new int[x.hist.length];
            for (int k = 0; k < h.length; k++) h[k] = (int) Math.min(Integer.MAX_VALUE, x.hist[k]);
            c.setIntArray("h", h);
            l.appendTag(c);
        }
        t.setTag("ls", l);
        l = new NBTTagList();
        for (Order o : orders) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("id", o.id); c.setInteger("c", o.company); c.setInteger("q", o.qty); c.setBoolean("b", o.buy); c.setLong("p", o.price); c.setLong("t", o.placed);
            if (o.who != null) c.setUniqueId("w", o.who);
            c.setString("wn", o.whoName); c.setBoolean("is", o.issuer);
            l.appendTag(c);
        }
        t.setTag("or", l);
        l = new NBTTagList();
        for (Bond b : bonds) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("id", b.id); c.setInteger("c", b.company); c.setInteger("u", b.units); c.setInteger("s", b.sold); c.setInteger("wk", b.weeks); c.setInteger("cp", b.couponBp);
            c.setLong("i", b.issued); c.setLong("m", b.matures); c.setLong("lc", b.lastCoupon); c.setString("st", b.state);
            NBTTagCompound h = new NBTTagCompound();
            for (Map.Entry<UUID, Integer> e : b.holders.entrySet()) h.setInteger(e.getKey().toString(), e.getValue());
            c.setTag("h", h);
            l.appendTag(c);
        }
        t.setTag("bd", l);
        l = new NBTTagList();
        for (Loan x : loans) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("c", x.company); c.setLong("s", x.site); c.setLong("a", x.amount); c.setLong("t", x.taken); c.setString("p", x.project);
            l.appendTag(c);
        }
        t.setTag("ln", l);
        NBTTagCompound ow = new NBTTagCompound();
        for (Map.Entry<UUID, Long> e : owed.entrySet()) ow.setLong(e.getKey().toString(), e.getValue());
        t.setTag("owed", ow);
        NBTTagList tp = new NBTTagList();
        for (String s : tape) tp.appendTag(new net.minecraft.nbt.NBTTagString(s));
        t.setTag("tape", tp);
        t.setInteger("next", nextId);
        return t;
    }
}
