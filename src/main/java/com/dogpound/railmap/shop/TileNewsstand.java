package com.dogpound.railmap.shop;

import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.block.StationControls;
import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.incident.Analytics;
import com.dogpound.railmap.stn.StnFurniture;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;

import java.util.List;
import java.util.function.Predicate;

/**
 * Newsstand (station shop #5): prints and sells "The Pride Rail Times", the real daily newspaper (shop.News). The owner
 * stocks the NEWSPRINT cabinet with paper and black ink (ink sacs); PRINT 1 / PRINT 5 queue copies on the little
 * press (mains: the press motor draws 12 FE a second while it runs, one copy every two seconds, a sheet of paper each
 * and an ink sac every four). Printed copies go in the bundle bin; BUY sells you today's copy at the PRICE selected,
 * and the station's passengers buy one in ShopConfig.paxPerPaper. With the AUTO PRINT key on it prints the morning's
 * run when the new edition comes out. At the day's end unsold copies are returned to the back-issues drawer. The front
 * board scrolls the headlines; HEAD < / > steps them by hand.
 */
public class TileNewsstand extends ShopTile {
    static double[][] SPOTS = {{19.933, 7.0, 13.4}, {21.6, 7.0, 13.4}, {23.267, 7.0, 13.4}, {24.933, 7.0, 13.4}, {26.6, 7.0, 13.4}, {28.267, 7.0, 13.4}};   // tools/gen_bb_shops.py
    static final long[] PRICES = {50, 100, 200};
    static final Predicate<ItemStack> PAPER = s -> s.getItem() == Items.PAPER;
    static final Predicate<ItemStack> INK = s -> s.getItem() == Items.DYE && s.getMetadata() == 0;
    private int queue, inkLeft, head, printedToday, edition = -1, pressT;

    public TileNewsstand() {
        super(new String[]{"np", "copies", "back"}, new String[]{"Newsprint + ink cabinet", "Today's copies (bundle bin)", "Back issues + returns"}, new int[]{18, 27, 9}, 6);
    }

    @Override public String wiredName() { return "Newsstand"; }
    @Override public String facilityKind() { return "Newsstand"; }
    @Override public int facilityPoints() { return 6; }
    @Override public long dailyCost() { return 40; }
    @Override public double[][] shelfSpots() { return SPOTS; }
    @Override protected boolean ownerOnly(int k) { return true; }
    @Override protected int paxEvery() { return ShopConfig.paxPerPaper; }
    @Override protected long load() { return queue > 0 ? 12 : 4; }
    @Override public String gearState() { return !live() ? "NO SUPPLY" : (open() ? "OPEN" : "closed") + " · " + copies() + " copies · " + soldToday + " sold"; }
    @Override public String stockNote() { return copies() + " copies, " + paper() + " paper, " + ink() + " ink" + (queue > 0 ? ", printing " + queue : ""); }

    long price() { return PRICES[Math.floorMod(ctl.get("s_price"), PRICES.length)]; }
    int paper() { return count(storage(0), PAPER); }
    int ink() { return count(storage(0), INK); }
    int today() { return NewsData.get(world).day; }
    int copies() { int d = today(); return count(storage(1), s -> News.dayOf(s) == d); }

    @Override
    protected String act(String id, EntityPlayer who) {
        switch (id) {
            case "print": case "print5": {
                int n = id.equals("print") ? 1 : 5;
                if (paper() < n + queue) return "§cNot enough newsprint in the cabinet (" + paper() + " sheets)";
                if (ink() == 0 && inkLeft < n + queue) return "§cOut of ink - put ink sacs in the cabinet";
                queue += n;
                last = "PRINTING " + queue;
                return "§e" + n + " cop" + (n == 1 ? "y" : "ies") + " queued on the press";
            }
            case "buy": {
                if (who == null) return "§7Bought in person";
                if (!open()) return "§cThe stand is closed";
                ItemStack c = takeCopy();
                if (c.isEmpty()) { last = "SOLD OUT"; return "§cSold out - nothing printed yet today"; }
                String no = charge(who, price(), "Newspaper");
                if (no != null) { putBack(c); return no; }
                give(who, c);
                sold(price());
                NewsData.get(world).sold++;
                last = "SOLD " + Coins.fmt(price());
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.CASH, SoundCategory.BLOCKS, 0.6f, 1.0f);
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ui.paper"), SoundCategory.BLOCKS, 0.6f, 1.0f);
                return "§aThe Pride Rail Times, " + Coins.fmt(price()) + " - enjoy!";
            }
            case "hprev": case "hnext": {
                List<String> h = NewsData.get(world).heads;
                if (h.isEmpty()) return "§7No edition yet";
                head = Math.floorMod(head + (id.equals("hnext") ? 1 : -1), h.size());
                return "§e" + h.get(head);
            }
            case "proof": {
                NewsData d = NewsData.get(world);
                if (d.day < 0) News.publish(world, d, Analytics.day(world));
                return "§eEdition No. " + d.number + " (day " + d.day + "): " + d.heads.size() + " headlines, " + d.lines.size() + " lines - " + (d.heads.isEmpty() ? "" : d.heads.get(0));
            }
            case "bell":
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.CASH, SoundCategory.BLOCKS, 0.7f, 1.3f);
                return open() ? "§7Ding! - Papers! Get your paper!" : "§7Ding! - the stand is closed";
            case "cancel":
                queue = 0; last = "CANCELLED";
                return "§7Press run cancelled";
            default: return null;
        }
    }

    private ItemStack takeCopy() {
        int d = today();
        for (int i = 0; i < storage(1).getSizeInventory(); i++) {
            ItemStack s = storage(1).getStackInSlot(i);
            if (News.dayOf(s) == d) { ItemStack one = s.splitStack(1); if (s.isEmpty()) storage(1).setInventorySlotContents(i, ItemStack.EMPTY); storage(1).markDirty(); return one; }
        }
        return ItemStack.EMPTY;
    }

    private void putBack(ItemStack c) { if (!StnFurniture.insert(storage(1), c).isEmpty()) StnFurniture.insert(storage(2), c); }

    @Override
    protected boolean paxSale() {
        ItemStack c = takeCopy();
        if (c.isEmpty()) return false;
        sold(price());
        NewsData.get(world).sold++;
        return true;
    }

    @Override
    protected void newDay(long day) {
        // last edition's unsold copies go back to the returns drawer (or stay in the bin if that is full)
        int now = today();
        for (int i = 0; i < storage(1).getSizeInventory(); i++) {
            ItemStack s = storage(1).getStackInSlot(i);
            if (s.isEmpty() || News.dayOf(s) == now) continue;
            ItemStack rest = StnFurniture.insert(storage(2), s);
            storage(1).setInventorySlotContents(i, rest);
        }
        printedToday = 0;
    }

    @Override
    protected void tick() {
        NewsData d = NewsData.get(world);
        if (d.day != edition) {                                 // a new edition is out
            if (edition >= 0) newDay(d.day);
            edition = d.day;
            if (ctl.get("k_auto") == 1 && live()) queue = Math.max(queue, Math.min(ShopConfig.autoPrintCopies, paper()));
            markDirty();
        }
        if (queue > 0 && live() && ++pressT >= 2) {
            pressT = 0;
            if (inkLeft <= 0 && take(storage(0), INK, 1)) inkLeft = 4;
            if (inkLeft <= 0 || !take(storage(0), PAPER, 1)) { queue = 0; last = inkLeft <= 0 ? "OUT OF INK" : "OUT OF PAPER"; return; }
            inkLeft--;
            if (d.day < 0) News.publish(world, d, Analytics.day(world));
            ItemStack c = News.book(d.lines, d.number, d.day);
            ItemStack rest = StnFurniture.insert(storage(1), c);
            if (!rest.isEmpty()) { queue = 0; last = "BIN FULL"; return; }
            queue--; printedToday++;
            last = queue > 0 ? "PRINTING " + queue : "PRINTED " + printedToday;
            world.playSound(null, pos, com.dogpound.railmap.ems.EmsSounds.NEWS_PRESS, SoundCategory.BLOCKS, 0.7f, 1.0f);
            if (queue == 0) Recorder.event(world, "Newsstand at " + (station.isEmpty() ? pos.toString() : station) + ": " + printedToday + " copies of No. " + d.number + " printed");
        }
        if (age % 200 == 7 && !d.heads.isEmpty()) head = (head + 1) % d.heads.size();     // the board scrolls
    }

    @Override
    protected boolean refresh() {
        NewsData d = NewsData.get(world);
        List<String> h = d.heads;
        String h1 = h.isEmpty() ? "NO EDITION YET" : h.get(Math.floorMod(head, h.size())), h2 = h.size() < 2 ? "" : h.get(Math.floorMod(head + 1, h.size())), h3 = h.size() < 3 ? "" : h.get(Math.floorMod(head + 2, h.size()));
        int cp = copies(), pp = paper(), ik = ink();
        boolean ch = false;
        ch |= StationControls.put(ctl, "pwr", term.powered());
        ch |= StationControls.put(ctl, "openl", open());
        ch |= StationControls.put(ctl, "onsale", open() && cp > 0);
        ch |= StationControls.put(ctl, "soldout", open() && cp == 0);
        ch |= StationControls.put(ctl, "printl", queue > 0);
        ch |= StationControls.put(ctl, "paperlow", pp < 5);
        ch |= StationControls.put(ctl, "inklow", ik == 0 && inkLeft <= 1);
        ch |= StationControls.put(ctl, "autol", ctl.get("k_auto") == 1);
        ch |= StationControls.put(ctl, "newed", printedToday == 0 && d.day >= 0);
        ch |= StationControls.put(ctl, "gpaper", Math.min(100, pp * 100 / 64));
        ch |= StationControls.put(ctl, "gink", Math.min(100, (ik * 4 + inkLeft) * 100 / 64));
        ch |= StationControls.text(ctl, "mast", "PRIDE RAIL TIMES NO." + d.number);
        ch |= StationControls.text(ctl, "h1", StnFurniture.cut(h1, 26));
        ch |= StationControls.text(ctl, "h2", StnFurniture.cut(h2, 26));
        ch |= StationControls.text(ctl, "h3", StnFurniture.cut(h3, 26));
        ch |= StationControls.text(ctl, "price", Coins.fmt(price()));
        ch |= StationControls.text(ctl, "stockn", "COPIES " + cp + " BACK " + count(storage(2), s -> true));
        ch |= StationControls.text(ctl, "papern", "PAPER " + pp + " INK " + ik);
        ch |= StationControls.text(ctl, "soldn", "SOLD " + soldToday + " " + Coins.fmt(takingsToday));
        ch |= StationControls.text(ctl, "ed", "DAY " + d.day + " PRINTED " + printedToday);
        ch |= StationControls.text(ctl, "last", last);
        ch |= StationControls.text(ctl, "stn", station.isEmpty() ? "NO STATION" : station.toUpperCase(java.util.Locale.ROOT));
        return ch;
    }

    @Override protected void saveShop(NBTTagCompound t) { t.setInteger("q", queue); t.setInteger("ink", inkLeft); t.setInteger("head", head); t.setInteger("pr", printedToday); t.setInteger("ed", edition); }
    @Override protected void loadShop(NBTTagCompound t) { queue = t.getInteger("q"); inkLeft = t.getInteger("ink"); head = t.getInteger("head"); printedToday = t.getInteger("pr"); edition = t.hasKey("ed") ? t.getInteger("ed") : -1; }
}
