package com.dogpound.railmap.travel6;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.block.StationControls;
import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.stn.StnFurniture;
import com.dogpound.railmap.stn.StnUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;

import java.util.*;

/**
 * Pride Card Top-Up Machine (batch 6b #6). Touch a card on the pad (it stays read for 30 s): the screen pages show
 * BALANCE (balance, journey in progress, today's fares against the daily cap), HISTORY (every tap, top-up and charge,
 * scrolled with PAGE), TOP UP (+$5/+$10/+$20/+$50, CLEAR, PAY from your bank) and SERVICES. NEW CARD issues a card from
 * the card stock (deposit; it also moves the balance of any card of yours you reported lost); a blank card touched on
 * the pad is registered the same way. REPORT LOST hotlists every card of yours you are not carrying. HAND BACK refunds
 * balance + deposit and keeps the card. RECEIPT prints the history. The card stock drawer opens with the SERVICE key.
 * 12 V control + mains (the card printer and pin pad).
 */
public class TileTopUp extends StnFurniture {
    static final long[] AMOUNTS = {500, 1000, 2000, 5000};
    static final String[] PAGES = {"BALANCE", "HISTORY", "TOP UP", "SERVICES"};
    private int sessCard, page, scroll;
    private long sessUntil, pending;
    private String lastMsg = "";
    private long testUntil;
    private boolean stockAlarm;

    public TileTopUp() { super(new String[]{"stock"}, new String[]{"Card stock"}, new int[]{9}, 1); }

    @Override public String wiredName() { return "Pride Card top-up machine"; }
    @Override public String gearState() { return !live() ? "NO SUPPLY" : stock() + " blank cards · " + (session() == null ? "idle" : "card " + sessCard); }
    @Override protected boolean mechanical(String id) { return id.startsWith("k_"); }

    @Override
    protected String refuse(int k, EntityPlayer who) {
        return ctl.get("k_svc") == 1 || who.capabilities.isCreativeMode ? null : "§7The card stock drawer is locked - turn the SERVICE key";
    }

    IInventory stockInv() { return storage(0); }

    int stock() {
        int n = 0;
        IInventory inv = stockInv();
        for (int i = 0; i < inv.getSizeInventory(); i++) { ItemStack s = inv.getStackInSlot(i); if (ItemTravel.is(s, ItemTravel.CARD) && ItemTravel.cardId(s) == 0) n += s.getCount(); }
        return n;
    }

    boolean takeBlank() {
        IInventory inv = stockInv();
        for (int i = 0; i < inv.getSizeInventory(); i++) { ItemStack s = inv.getStackInSlot(i); if (ItemTravel.is(s, ItemTravel.CARD) && ItemTravel.cardId(s) == 0) { inv.decrStackSize(i, 1); return true; } }
        return false;
    }

    TravelData.Card session() {
        if (sessCard == 0 || world.getTotalWorldTime() > sessUntil) return null;
        return TravelData.get(world).cards.get(sessCard);
    }

    String where() { Map.Entry<Long, String> st = Cards.stationAt(world, pos); return st == null ? "top-up machine " + pos.getX() + "," + pos.getZ() : st.getValue(); }

    @Override public int controlValue(String id) { return live() && testUntil > 0 && !mechanical(id) ? Math.max(1, ctl.get(id)) : super.controlValue(id); }

    @Override
    protected String act(String id, EntityPlayer who) {
        String r = act0(id, who);
        if (r != null && !r.isEmpty()) lastMsg = net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(r);
        return r;
    }

    private String act0(String id, EntityPlayer who) {
        TravelData d = TravelData.get(world);
        long now = world.getTotalWorldTime();
        TravelData.Card c = session();
        switch (id) {
            case "test": testUntil = now + 60; return "§7Lamp test";
            case "pgup": scroll = Math.max(0, scroll - 6); return "";
            case "pgdn": scroll += 6; return "";
            case "pgl": page = Math.floorMod(page - 1, PAGES.length); scroll = 0; return "";
            case "pgr": page = Math.floorMod(page + 1, PAGES.length); scroll = 0; return "";
            case "a5": case "a10": case "a20": case "a50":
                pending += AMOUNTS[Arrays.asList("a5", "a10", "a20", "a50").indexOf(id)];
                pending = Math.min(pending, 50000);
                page = 2;
                return "§7Top up " + Coins.fmt(pending);
            case "clr": pending = 0; return "§7Cleared";
            case "cancel": pending = 0; sessCard = 0; page = 0; return "§7Cancelled - card released";
            case "pad": return pad(who, d, now);
            case "pay": {
                if (c == null) return "§7Touch your card on the pad first";
                if (!(who instanceof EntityPlayerMP p)) return "§7Pay at the machine";
                String r = Cards.topUp(p, c, pending, where());
                if (r.startsWith("§a")) { pending = 0; sessUntil = now + 600; world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ui.beep_ok"), SoundCategory.BLOCKS, 0.7f, 1f); syncCard(who, c); }
                return r;
            }
            case "new": return newCard(who, d, now, ItemStack.EMPTY);
            case "block": {
                if (who == null) return "§7Report a lost card at the machine";
                int n = 0;
                Set<Integer> carried = new HashSet<>();
                for (ItemStack s : who.inventory.mainInventory) if (ItemTravel.cardId(s) != 0) carried.add(ItemTravel.cardId(s));
                for (ItemStack s : who.inventory.offHandInventory) if (ItemTravel.cardId(s) != 0) carried.add(ItemTravel.cardId(s));
                for (TravelData.Card x : Cards.of(world, who.getUniqueID())) if (!x.blocked && !carried.contains(x.id)) { x.blocked = true; x.note(Recorder.clock(world, now) + " REPORTED LOST at " + where()); n++; }
                if (n == 0) return "§7No missing cards of yours - every card registered to you is in your pockets";
                d.markDirty();
                Recorder.event(world, who.getName() + " reported " + n + " Pride Card(s) lost at " + where());
                return "§e" + n + " card(s) hotlisted - NEW CARD moves their balance onto a new card";
            }
            case "refund": {
                if (c == null) return "§7Touch the card to hand back on the pad";
                if (!(who instanceof EntityPlayerMP p)) return "§7Hand the card back at the machine";
                if (c.owner != null && !c.owner.equals(who.getUniqueID()) && !who.capabilities.isCreativeMode) return "§cThat card is registered to " + c.ownerName;
                if (c.in()) return "§cTouch out first - the card is on a journey from " + c.inName;
                ItemStack held = who.getHeldItemMainhand();
                if (ItemTravel.cardId(held) != c.id) return "§7Hold the card you are handing back";
                long back = Math.max(0, c.bal) + c.deposit;
                if (back > 0 && !Coins.pay(p, back, "Pride Card " + c.id + " refund")) return "§cThe refund can't be paid (no bank)";
                held.shrink(1);
                d.cards.remove(c.id);
                d.markDirty();
                sessCard = 0;
                StnFurniture.insert(stockInv(), new ItemStack(TravelRegistry.item, 1, ItemTravel.CARD));   // the card goes back into stock, wiped
                Recorder.event(world, "Pride Card " + c.id + " handed back at " + where() + ", " + Coins.fmt(back) + " refunded");
                return "§aCard handed back - " + Coins.fmt(back) + " refunded to your bank";
            }
            case "rcpt": {
                if (c == null) return "§7Touch your card on the pad first";
                List<String> out = new ArrayList<>();
                out.add("PRIDE CARD " + c.id);
                out.add(c.ownerName + " - balance " + Coins.fmt(c.bal));
                out.add("Today " + Coins.fmt(c.spentDay) + " of cap " + (c.zlo == 0 ? "-" : Coins.fmt(Cards.cap(world, c))));
                out.addAll(c.hist);
                StnUtil.print(world, pos, controlFacing(), "Pride Card " + c.id, where(), out);
                return "§aReceipt printed";
            }
            default: return null;
        }
    }

    private void syncCard(EntityPlayer who, TravelData.Card c) {
        if (who == null) return;
        for (ItemStack s : who.inventory.mainInventory) if (ItemTravel.cardId(s) == c.id) ItemTravel.sync(s, c);
        for (ItemStack s : who.inventory.offHandInventory) if (ItemTravel.cardId(s) == c.id) ItemTravel.sync(s, c);
    }

    private String pad(EntityPlayer who, TravelData d, long now) {
        if (who == null) return "§7Touch a card on the pad";
        ItemStack held = who.getHeldItemMainhand();
        if (!Cards.isCard(held)) held = who.getHeldItemOffhand();
        if (!Cards.isCard(held)) return "§7Touch a Pride Card on the pad (or a blank card to register it)";
        if (ItemTravel.cardId(held) == 0) return newCard(who, d, now, held);
        TravelData.Card c = d.cards.get(ItemTravel.cardId(held));
        if (c == null) return "§cCard not recognised";
        sessCard = c.id; sessUntil = now + 600; page = pending > 0 ? 2 : 0; scroll = 0;
        ItemTravel.sync(held, c);
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ui.beep_ok"), SoundCategory.BLOCKS, 0.6f, 1.2f);
        return "§bCard " + c.id + " read - balance " + Coins.fmt(c.bal) + (c.blocked ? " §c(BLOCKED)" : "");
    }

    /** issue a card: from stock (blank == EMPTY) or by registering the blank card in hand; moves lost cards' balance */
    private String newCard(EntityPlayer who, TravelData d, long now, ItemStack blank) {
        if (!(who instanceof EntityPlayerMP p)) return "§7Buy a card at the machine";
        if (blank.isEmpty() && stock() == 0) return "§cOUT OF CARDS - the card stock is empty";
        long dep = TravelConfig.cardDeposit;
        if (!Coins.charge(p, dep, "Pride Card deposit")) return "§cThe card deposit is " + Coins.fmt(dep) + " - your bank can't cover it";
        if (blank.isEmpty() && !takeBlank()) return "§cOUT OF CARDS";
        TravelData.Card c = d.newCard(who.getUniqueID(), who.getName(), now);
        c.deposit = dep;
        long moved = 0;
        for (TravelData.Card x : Cards.of(world, who.getUniqueID()))
            if (x.blocked && x.bal > 0 && x != c) { moved += x.bal; x.note(Recorder.clock(world, now) + " BALANCE MOVED to card " + c.id); x.bal = 0; }
        c.bal = moved;
        c.note(Recorder.clock(world, now) + " ISSUED at " + where() + (moved > 0 ? " with " + Coins.fmt(moved) + " from lost card(s)" : ""));
        d.markDirty();
        ItemStack card = ItemTravel.card(c);
        if (!blank.isEmpty()) blank.shrink(1);
        if (!who.inventory.addItemStackToInventory(card)) who.dropItem(card, false);
        sessCard = c.id; sessUntil = now + 600; page = 2;
        world.playSound(null, pos, com.dogpound.railmap.ModSounds.PRINTER, SoundCategory.BLOCKS, 0.5f, 1.3f);
        Recorder.event(world, "Pride Card " + c.id + " issued to " + who.getName() + " at " + where());
        return "§aPride Card " + c.id + " issued (deposit " + Coins.fmt(dep) + ")" + (moved > 0 ? " - " + Coins.fmt(moved) + " moved from your lost card" : "") + " - now TOP UP";
    }

    @Override
    protected void work() {
        long now = world.getTotalWorldTime();
        if (testUntil > 0 && now > testUntil) { testUntil = 0; controlsChanged(); }
        if (age % 100 == 37) {
            boolean empty = stock() == 0;
            String key = "travel6.stock." + pos.toLong();
            if (empty && !stockAlarm) AlarmManager.get(world).raise(world, key, AlarmManager.Cat.GENERAL, AlarmManager.Sev.INFO, "Top-up machine at " + where() + ": card stock empty", pos, true);
            if (!empty && stockAlarm) AlarmManager.get(world).clear(key);
            stockAlarm = empty;
        }
    }

    @Override
    protected boolean refresh() {
        TravelData.Card c = session();
        String[] l = new String[6];
        Arrays.fill(l, "");
        switch (page) {
            case 0 -> {
                if (c == null) { l[0] = "WELCOME - PRIDE CARD"; l[1] = "TOUCH YOUR CARD ON THE PAD"; l[2] = "NEW CARD  " + Coins.fmt(TravelConfig.cardDeposit) + " DEPOSIT"; l[4] = stock() + " CARDS IN STOCK"; }
                else {
                    l[0] = "CARD " + c.id + (c.blocked ? "  BLOCKED" : "");
                    l[1] = "BALANCE " + Coins.fmt(c.bal);
                    l[2] = c.in() ? "IN AT " + StnUtil.up(StnUtil.cut(c.inName, 16)) : "NOT ON A JOURNEY";
                    l[3] = "TODAY " + Coins.fmt(c.spentDay) + " CAP " + (c.zlo == 0 ? "-" : Coins.fmt(Cards.cap(world, c)));
                    l[4] = c.zlo == 0 ? "NO ZONES YET TODAY" : "ZONES " + c.zlo + "-" + c.zhi + "  " + c.tripsDay + " TRIPS";
                    l[5] = StnUtil.up(StnUtil.cut(c.ownerName, 24));
                }
            }
            case 1 -> {
                if (c == null) l[0] = "TOUCH CARD FOR HISTORY";
                else {
                    scroll = Math.max(0, Math.min(scroll, Math.max(0, c.hist.size() - 6)));
                    for (int i = 0; i < 6 && scroll + i < c.hist.size(); i++) l[i] = StnUtil.up(StnUtil.cut(c.hist.get(scroll + i), 30));
                    if (c.hist.isEmpty()) l[0] = "NO HISTORY YET";
                }
            }
            case 2 -> {
                l[0] = "TOP UP " + (c == null ? "- TOUCH CARD" : "CARD " + c.id);
                l[1] = "AMOUNT " + Coins.fmt(pending);
                l[2] = c == null ? "" : "NEW BALANCE " + Coins.fmt(c.bal + pending);
                l[4] = "+5 +10 +20 +50 THEN PAY";
                l[5] = "PAID FROM YOUR BANK";
            }
            default -> {
                l[0] = "SERVICES";
                l[1] = "NEW CARD - DEPOSIT " + Coins.fmt(TravelConfig.cardDeposit);
                l[2] = "REPORT LOST - HOTLIST";
                l[3] = "HAND BACK - REFUND";
                l[4] = "RECEIPT - PRINT HISTORY";
                l[5] = "CAP " + TravelConfig.capPct + "% OF A DAY PASS";
            }
        }
        boolean ch = false;
        for (int i = 0; i < 6; i++) ch |= StationControls.text(ctl, "s" + (i + 1), l[i]);
        ch |= StationControls.text(ctl, "title", PAGES[page] + " " + (page + 1) + "/" + PAGES.length);
        ch |= StationControls.text(ctl, "amt", Coins.fmt(pending));
        ch |= StationControls.text(ctl, "msg", cut(lastMsg, 34));
        int st = stock();
        ch |= StationControls.put(ctl, "cardl", c != null) | StationControls.put(ctl, "stockl", st < 3) | StationControls.put(ctl, "stockg", Math.min(64, st))
                | StationControls.put(ctl, "payl", pending > 0) | StationControls.put(ctl, "pwr", term.powered())
                | StationControls.put(ctl, "lostl", c != null && c.blocked);
        for (int i = 0; i < PAGES.length; i++) ch |= StationControls.put(ctl, "p" + i, page == i);
        return ch;
    }

    @Override protected void save(NBTTagCompound t) { t.setInteger("pg", page); t.setLong("pend", pending); t.setBoolean("sa", stockAlarm); }
    @Override protected void load(NBTTagCompound t) { page = Math.floorMod(t.getInteger("pg"), PAGES.length); pending = t.getLong("pend"); stockAlarm = t.getBoolean("sa"); }
}
