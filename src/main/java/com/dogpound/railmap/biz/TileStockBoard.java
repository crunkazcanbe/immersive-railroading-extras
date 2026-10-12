package com.dogpound.railmap.biz;

import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.economy.CompanyData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentString;

import java.util.*;

/**
 * The Railway Stock Exchange board + dealing desk (her "next 48" #36, #37). The big board lists every listed company:
 * last price, the day's change with up / down lamps; the dealer's monitor shows the selected stock's fair (book) value,
 * dividend yield, best bid / ask, volume, your holding and your resting orders; the bond monitor the bonds on issue.
 * You set QTY and PRICE, then BUY or SELL (escrowed until matched); CANCEL takes back your newest order. A director
 * LISTs part of their holding (an IPO) or ISSUEs a bond. The TROPHY board shows the latest operational-mastery awards
 * (#37). The TRADING key opens the floor; the bell rings the index. Certificate cabinet and cash drawer are storage; the
 * trophy shelf shows what stands on it.
 */
public class TileStockBoard extends BizDesk {
    public static final String[] STORE_IDS = {"certs", "cash"};
    public static final String[] STORE_NAMES = {"Share Certificate Cabinet", "Cash Drawer"};
    public static final int[] STORE_SIZES = {27, 9};
    private static final double[][] SPOTS = {{20.1, 7.0, 13.4}, {22.1, 7.0, 13.4}, {24.1, 7.0, 13.4}, {26.1, 7.0, 13.4}, {28.1, 7.0, 13.4}};
    @Override public double[][] shelfSpots() { return SPOTS; }
    private static final int[] QTY = {1, 5, 10, 25, 50, 100};
    private int sel, qty = 1, bsel;
    private long price;

    public TileStockBoard() { super(STORE_IDS, STORE_NAMES, STORE_SIZES, 5); }

    private boolean open() { return ctl.get("trading") == 0; }
    @Override protected boolean mechanical(String id) { return id.equals("trading"); }

    private List<Securities.Listing> listings() { return new ArrayList<>(biz().securities.listings.values()); }

    private Securities.Listing selected() {
        List<Securities.Listing> l = listings();
        if (l.isEmpty()) return null;
        sel = Math.floorMod(sel, l.size());
        return l.get(sel);
    }

    private Securities.Bond bond() {
        List<Securities.Bond> b = new ArrayList<>();
        for (Securities.Bond x : biz().securities.bonds) if (x.state.equals("OPEN") || x.state.equals("LIVE")) b.add(x);
        if (b.isEmpty()) return null;
        bsel = Math.floorMod(bsel, b.size());
        return b.get(bsel);
    }

    @Override
    protected String press(String id, EntityPlayer who) {
        Securities sec = biz().securities;
        Securities.Listing l = selected();
        Company c = l == null ? null : CompanyData.get(world).byId(l.company);
        switch (id) {
            case "prev": sel--; price = 0; return "";
            case "next": sel++; price = 0; return "";
            case "qtyup": case "qtydn": {
                int i = 0;
                for (int k = 0; k < QTY.length; k++) if (QTY[k] == qty) i = k;
                qty = QTY[Math.max(0, Math.min(QTY.length - 1, i + (id.equals("qtyup") ? 1 : -1)))];
                return "§7Quantity " + qty + "/1000";
            }
            case "pxup": case "pxdn": {
                if (l == null) return "";
                if (price <= 0) price = l.last;
                price = Math.max(1, price + (id.equals("pxup") ? 1 : -1) * Math.max(1, price / 20));
                return "§7Limit " + Coins.fmt(price) + " a share (" + String.format(Locale.ROOT, "%+.0f%%", (price - l.last) * 100.0 / Math.max(1, l.last)) + " vs last)";
            }
            case "bprev": bsel--; return "";
            case "bnext": bsel++; return "";
            case "tape": {
                if (who == null) return "";
                who.sendMessage(new TextComponentString("§6📈 Exchange tape - index " + sec.index(world)));
                for (int i = 0; i < Math.min(10, sec.tape.size()); i++) who.sendMessage(new TextComponentString("§7 " + sec.tape.get(i)));
                return "";
            }
            case "bell": {
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.FIRE_BELL, SoundCategory.BLOCKS, 1.5f, 1.0f);
                String say = "§6🔔 Railway Stock Exchange: index §f" + sec.index(world) + "§6, " + sec.listings.size() + " listed, " + sec.bonds.size() + " bonds";
                for (EntityPlayer pl : world.playerEntities) if (pl.getDistanceSq(pos) < 48 * 48) pl.sendMessage(new TextComponentString(say));
                return "";
            }
            default: break;
        }
        if (!open()) return "§7The floor is CLOSED - turn the TRADING key";
        if (!(who instanceof EntityPlayerMP p)) return "§7Deals are made in person";
        switch (id) {
            case "buy": case "sell": {
                if (l == null || c == null) return "§7Nothing listed yet - a director LISTs their company first";
                return sec.order(world, c, p, id.equals("buy"), qty, price > 0 ? price : l.last);
            }
            case "cancel": {
                Securities.Order last = null;
                for (Securities.Order o : sec.orders) if (p.getUniqueID().equals(o.who) && !o.issuer) last = o;
                if (last == null) return "§7You have no resting orders";
                Company k = CompanyData.get(world).byId(last.company);
                return sec.cancel(world, k, p, last.id);
            }
            case "list": {
                Company m = mine(p, true);
                if (m == null) return "§cOnly a company's owner or director can list it";
                return sec.list(world, m, p, Math.min(qty, 500), price);
            }
            case "issue": {
                Company m = mine(p, true);
                if (m == null) return "§cOnly a company's owner or director can issue bonds";
                return sec.issueBond(world, m, Math.max(5, qty), 8, 150);
            }
            case "bbuy": {
                Securities.Bond b = bond();
                if (b == null) return "§7No bonds on issue";
                return sec.buyBond(world, p, b, Math.min(qty, b.units - b.sold));
            }
            default: return null;
        }
    }

    @Override
    protected void screens() {
        Securities sec = biz().securities;
        CompanyData cd = CompanyData.get(world);
        List<Securities.Listing> ls = listings();
        Securities.Listing s = selected();
        int top = Math.max(0, Math.min(sel - 3, ls.size() - 8));
        int up = 0, down = 0;
        for (Securities.Listing l : ls) { if (l.last > l.prev) up++; else if (l.last < l.prev) down++; }
        for (int r = 1; r <= 8; r++) {
            int i = top + r - 1;
            Securities.Listing l = i < ls.size() ? ls.get(i) : null;
            Company c = l == null ? null : cd.byId(l.company);
            t("r" + r + "n", c == null ? "" : (l == s ? ">" : " ") + cut(c.mark + " " + c.name.toUpperCase(Locale.ROOT), 18));
            t("r" + r + "p", l == null ? "" : Coins.fmt(l.last));
            t("r" + r + "c", l == null ? "" : String.format(Locale.ROOT, "%+.1f%%", (l.last - l.prev) * 100.0 / Math.max(1, l.prev)));
            p("r" + r + "u", l != null && l.last > l.prev);
            p("r" + r + "d", l != null && l.last < l.prev);
        }
        t("idx", "RSX INDEX " + sec.index(world));
        long tm = (world.getWorldTime() + 6000) % 24000;
        t("time", String.format(Locale.ROOT, "%02d:%02d", tm / 1000, (tm % 1000) * 60 / 1000));
        Company c = s == null ? null : cd.byId(s.company);
        if (c != null) {
            long fair = Securities.fair(c), div = c.lastMonth[Company.Kind.DIVIDEND.ordinal()];
            t("m1", c.mark + " " + cut(c.name.toUpperCase(Locale.ROOT), 16) + " " + Coins.fmt(s.last));
            t("m2", "FAIR " + Coins.fmt(fair) + String.format(Locale.ROOT, "  YIELD %.1f%%", s.last > 0 ? div / 10.0 / s.last : 0));
            t("m3", "BID " + Coins.fmt(sec.bestBid(c.id)) + "  ASK " + Coins.fmt(sec.bestAsk(c.id)));
            t("m4", "VOL " + s.volumeDay + "  REP " + c.reputation + "  LVL " + biz().mastery.level(c.id));
            t("m5", "24H " + spark(Arrays.stream(s.hist).asDoubleStream().toArray()));
        } else { t("m1", "NO LISTINGS"); t("m2", "A DIRECTOR PRESSES LIST"); t("m3", ""); t("m4", ""); t("m5", ""); }
        t("m6", "QTY " + qty + "  LIMIT " + (price > 0 ? Coins.fmt(price) : "MARKET"));
        Securities.Bond b = bond();
        Company bc = b == null ? null : cd.byId(b.company);
        t("b1", b == null ? "NO BONDS ON ISSUE" : "B" + b.id + " " + (bc == null ? "?" : bc.mark) + " " + b.state);
        t("b2", b == null ? "" : String.format(Locale.ROOT, "%.2f%%/WK %d WK", b.couponBp / 100.0, b.weeks));
        t("b3", b == null ? "" : (b.units - b.sold) + " OF " + b.units + " LEFT X $100");
        LinkedList<String> feed = biz().mastery.feed;
        for (int i = 0; i < 4; i++) t("t" + (i + 1), i < feed.size() ? cut(feed.get(i).toUpperCase(Locale.ROOT), 34) : "");
        p("power", term.powered());
        p("open", open());
        p("bull", up > down);
        p("bear", down > up);
        p("halt", ls.isEmpty());
        v("idxg", (int) Math.min(2000, sec.index(world) / 10));
    }

    @Override public String wiredName() { return "Stock Exchange Board"; }
    @Override public String gearState() { return (open() ? "OPEN" : "CLOSED") + " · index " + biz().securities.index(world) + " · " + biz().securities.listings.size() + " listed"; }
    @Override protected void save(NBTTagCompound t) { t.setInteger("sel", sel); t.setInteger("qty", qty); t.setLong("px", price); t.setInteger("bsel", bsel); }
    @Override protected void load(NBTTagCompound t) { sel = t.getInteger("sel"); qty = Math.max(1, t.getInteger("qty")); price = t.getLong("px"); bsel = t.getInteger("bsel"); }
}
