package com.dogpound.railmap.biz;

import com.dogpound.railmap.block.TileContainerTerminal;
import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.economy.CompanyData;
import com.dogpound.railmap.economy.MarketData;
import com.dogpound.railmap.item.ItemContainer;
import com.dogpound.railmap.proxy.CommonProxy;
import com.dogpound.railmap.server.StationData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentString;

import java.util.*;

/**
 * Port Berth + ship-to-shore crane (her "next 48" #15). The quay desk under a working STS crane: ships call on a
 * schedule (or when CALLed), lie alongside for a day and sail at their time. The crane works one box per cycle -
 * DISCHARGE (ship -> quay), LOAD (quay -> ship, sealed boxes only), TO TERMINAL (a terminal tractor hauls the quay box
 * to the apron of the nearest Container Terminal, for the railway), TRUCK OUT / TRUCK IN (a road haulier at the gate,
 * by appointment). The crane only works with its key ON, the boom LOWERED, a ship alongside and the wind under 20 m/s
 * (the anemometer reads the weather); the boom must be RAISED for a ship to berth or sail. Imports carry goods to an
 * inland station and their through freight; exports are sold abroad when the ship sails (15 % over the bid) and the
 * empty box comes back on the next ship. The quay holds 8 boxes; after quayFreeDays they pay demurrage. Every move is
 * in the box's custody record. Two transit sheds and the port office drawers are real storage.
 */
public class TilePortBerth extends BizDesk {
    public static final String[] STORE_IDS = {"shed1", "shed2", "office"};
    public static final String[] STORE_NAMES = {"Transit Shed A", "Transit Shed B", "Port Office Drawers"};
    public static final int[] STORE_SIZES = {27, 27, 9};
    public static final int QUAY_CAP = 8, SHIP_CAP = 10;
    private static final double[][] SPOTS = {{19.725, 7.0, 13.4}, {21.775, 7.0, 13.4}, {23.825, 7.0, 13.4}, {25.875, 7.0, 13.4}};
    @Override public double[][] shelfSpots() { return SPOTS; }
    private static final String[] SHIPS = {"PRIDE STAR", "RAINBOW SPIRIT", "EMERALD BAY", "AURORA TRADER", "COBALT HARMONY", "VIOLET DAWN", "AMBER VOYAGER", "CORAL PRIDE"};
    private static final long LIFT = 250;

    public final List<ItemStack> quay = new ArrayList<>(), aboard = new ArrayList<>(), loaded = new ArrayList<>(), returning = new ArrayList<>();
    private String ship = "";
    private long arrived, sails, nextCall;
    private int sel, shipNo, moves, movesHour;
    private long movesHourStart, opEnd, feesDay, feesToday;
    private char op;                                   // 'D' discharge, 'L' load, 'T' to terminal, 0 idle
    private String opBox = "";

    public TilePortBerth() { super(STORE_IDS, STORE_NAMES, STORE_SIZES, 4); }

    @Override protected boolean mechanical(String id) { return id.equals("boom") || id.equals("estop") || id.equals("k_crane"); }

    @Override
    protected String pressMechanical(String id, EntityPlayer who) {
        switch (id) {
            case "boom":
                if (op != 0) return "§cThe crane is working - wait for the cycle";
                ctl.set("boom", ctl.get("boom") == 0 ? 1 : 0);
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("machine.jack"), SoundCategory.BLOCKS, 1.0f, 0.8f);
                return ctl.get("boom") == 1 ? "§eBoom LOWERED over the ship's hatches" : "§aBoom RAISED - clear for a ship to berth or sail";
            case "estop":
                ctl.set("estop", ctl.get("estop") == 0 ? 1 : 0);
                if (ctl.get("estop") == 1) op = 0;
                return ctl.get("estop") == 1 ? "§cCRANE EMERGENCY STOP" : "§aE-stop released";
            default:
                ctl.set("k_crane", ctl.get("k_crane") == 0 ? 1 : 0);
                return ctl.get("k_crane") == 1 ? "§aCrane ON" : "§7Crane OFF";
        }
    }

    private boolean alongside() { return !ship.isEmpty(); }
    private boolean boomDown() { return ctl.get("boom") == 1; }
    public double wind() {
        double w = 4 + 3 * Math.sin(world.getTotalWorldTime() / 1800.0) + (world.isRaining() ? 8 : 0) + (world.isThundering() ? 12 : 0);
        return Math.max(0, w);
    }

    private String craneReady() {
        if (!term.powered()) return "no 12 V";
        if (ctl.get("k_crane") == 0) return "crane key is OFF";
        if (ctl.get("estop") == 1) return "E-STOP is in";
        if (wind() > 20) return String.format(Locale.ROOT, "wind %.0f m/s - cranes stop over 20", wind());
        if (op != 0) return "the crane is mid-cycle";
        return null;
    }

    private ItemStack selected() { return quay.isEmpty() ? ItemStack.EMPTY : quay.get(Math.floorMod(sel, quay.size())); }

    private Company operator() { return placer == null ? null : CompanyData.get(world).of(placer); }

    private void fee(ItemStack box, long cents, String why) {
        EntityPlayerMP o = CompanyData.online(world, ItemContainer.owner(box));
        if (o != null && Coins.charge(o, cents, "Port: " + why)) collected(cents, why);
        else ItemContainer.addFees(box, cents);
    }

    private void collected(long cents, String why) {
        long day = world.getTotalWorldTime() / BizData.DAY;
        if (day != feesDay) { feesDay = day; feesToday = 0; }
        feesToday += cents;
        Company c = operator();
        if (c != null) CompanyData.get(world).credit(world, c, Company.Kind.HANDLING, cents, "Port " + why);
        Intermodal.Stat s = biz().intermodal.stat(world, pos, true);
        s.fees += cents;
    }

    // ---- ships -----------------------------------------------------------------------------------------------------------
    private String arrive() {
        if (alongside()) return "§7" + ship + " is already alongside";
        if (boomDown()) return "§cRaise the boom - the ship can't berth under it";
        ship = SHIPS[shipNo++ % SHIPS.length];
        arrived = world.getTotalWorldTime();
        sails = arrived + BizData.DAY;
        aboard.clear();
        aboard.addAll(returning);
        returning.clear();
        int n = 3 + world.rand.nextInt(3);
        List<MarketData.Price> goods = new ArrayList<>(MarketData.get(world).all());
        goods.removeIf(p -> p.stack().isEmpty());
        for (int i = 0; i < n && !goods.isEmpty() && aboard.size() < SHIP_CAP; i++) {
            ItemStack box = ItemContainer.make(CommonProxy.container, "PLN", world.rand.nextInt(1_000_000), null, "Pride Lines");
            List<ItemStack> in = new ArrayList<>();
            for (int k = 0; k < 2; k++) { ItemStack g = goods.get(world.rand.nextInt(goods.size())).stack().copy(); g.setCount(16 + world.rand.nextInt(49)); in.add(g); }
            ItemContainer.setContents(box, in);
            long dest = Intermodal.randomDest(world, pos);
            if (dest != Long.MIN_VALUE) { ItemContainer.setDest(box, dest, StationData.get(world).names().get(dest)); Intermodal.setFreight(box, Intermodal.freightFor(world, pos, dest)); }
            Custody cu = biz().custody;
            cu.stuffed(world, box, in, pos, "Pride Lines");
            cu.seal(world, box, pos, "Pride Lines");
            cu.event(world, box, "SHIP", pos, ship, "aboard " + ship + " - import for " + ItemContainer.destName(box));
            aboard.add(box);
        }
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("station.ship_horn"), SoundCategory.BLOCKS, 4.0f, 1.0f);
        biz().intermodal.note(world, ship + " berthed at " + biz().intermodal.stat(world, pos, true).name + ": " + aboard.size() + " boxes to discharge");
        com.dogpound.railmap.alarm.Recorder.event(world, "Port: " + ship + " alongside, " + aboard.size() + " import boxes, sails " + BizData.hhmm(sails));
        return "§a" + ship + " alongside: " + aboard.size() + " boxes to discharge, sails " + BizData.hhmm(sails) + " day " + sails / BizData.DAY;
    }

    private String sail() {
        if (!alongside()) return "§7No ship alongside";
        if (boomDown()) {
            com.dogpound.railmap.alarm.AlarmManager.get(world).raise(world, "port:boom:" + pos.toLong(), com.dogpound.railmap.alarm.AlarmManager.Cat.FREIGHT, com.dogpound.railmap.alarm.AlarmManager.Sev.MINOR,
                    ship + " can't sail - the crane boom is down over her", pos, false);
            return "§cRaise the boom first - " + ship + " can't sail under it";
        }
        com.dogpound.railmap.alarm.AlarmManager.get(world).clear("port:boom:" + pos.toLong());
        long sold = 0;
        for (ItemStack box : loaded) {                                           // exports sold abroad; the empty box comes back
            long value = 0;
            for (ItemStack g : ItemContainer.contents(box)) { MarketData.Price q = MarketData.get(world).of(g); if (q != null) { value += q.bid() * g.getCount() * 115 / 100; MarketData.get(world).sold(g, g.getCount()); } }
            UUID owner = ItemContainer.owner(box);
            Company c = owner == null ? null : CompanyData.get(world).of(owner);
            if (value > 0) {
                if (c != null) CompanyData.get(world).credit(world, c, Company.Kind.TRADING, value, "Export " + ItemContainer.pretty(box) + " on " + ship);
                else if (owner != null) biz().securities.credit(world, owner, value, "Export " + ItemContainer.pretty(box));
            }
            sold += value;
            biz().custody.event(world, box, "SHIP", pos, ship, "sailed on " + ship + " - export sold for " + Coins.fmt(value));
            biz().custody.stripped(world, box, pos, "consignee abroad");
            ItemContainer.setContents(box, new ArrayList<>());
            returning.add(box);
            biz().mastery.count(world, c, null, "boxes", 1);
        }
        String r = "§a" + ship + " sailed with " + loaded.size() + " export boxes (" + Coins.fmt(sold) + " sold abroad)" + (aboard.isEmpty() ? "" : ", " + aboard.size() + " imports left aboard (over-carried)");
        biz().intermodal.note(world, ship + " sailed: " + loaded.size() + " exports, " + Coins.fmt(sold));
        loaded.clear(); aboard.clear();
        ship = "";
        nextCall = world.getTotalWorldTime() + Math.max(1, BizConfig.shipEveryDays) * BizData.DAY;
        return r;
    }

    // ---- the crane cycle ------------------------------------------------------------------------------------------------
    private String start(char what, long ticks, String box) {
        Company c = operator();
        if (c != null && BizConfig.researchTree && Research.has(world, c, Research.Tech.PORT_LOGISTICS)) ticks = ticks * 7 / 10;
        op = what; opEnd = world.getTotalWorldTime() + ticks; opBox = box;
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("machine.servo"), SoundCategory.BLOCKS, 0.9f, 0.6f);
        return "";
    }

    private void complete() {
        char what = op;
        op = 0;
        Intermodal.Stat st = biz().intermodal.stat(world, pos, true);
        moves++;
        long hour = world.getTotalWorldTime() / 1000;
        if (hour != movesHourStart) { movesHourStart = hour; movesHour = 0; }
        movesHour++;
        if (what == 'D' && !aboard.isEmpty() && quay.size() < QUAY_CAP) {
            ItemStack b = aboard.remove(0);
            ItemContainer.setSince(b, world.getTotalWorldTime()); ItemContainer.setBilled(b, world.getTotalWorldTime());
            quay.add(b);
            st.shipMoves++;
            fee(b, LIFT, "discharge " + ItemContainer.pretty(b));
            biz().custody.event(world, b, "SHIP", pos, ship, "discharged by the STS crane onto the quay");
        } else if (what == 'L') {
            ItemStack b = take(opBox);
            if (!b.isEmpty()) { loaded.add(b); st.shipMoves++; fee(b, LIFT, "load " + ItemContainer.pretty(b)); biz().custody.event(world, b, "SHIP", pos, ship, "loaded aboard " + ship + " for export"); }
        } else if (what == 'T') {
            ItemStack b = take(opBox);
            TileContainerTerminal t = terminal();
            if (!b.isEmpty() && t != null && t.apron.isEmpty()) {
                t.apron = b;
                t.controlsChanged();
                st.railMoves++;
                biz().custody.event(world, b, "TRACTOR", pos, placerName, "terminal tractor to the rail terminal apron");
            } else if (!b.isEmpty()) quay.add(b);                                // nowhere to put it: back on the quay
        }
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("machine.clunk"), SoundCategory.BLOCKS, 0.9f, 1.0f);
    }

    private ItemStack take(String cid) {
        for (Iterator<ItemStack> it = quay.iterator(); it.hasNext(); ) { ItemStack b = it.next(); if (ItemContainer.id(b).equals(cid)) { it.remove(); return b; } }
        return ItemStack.EMPTY;
    }

    /** the nearest Container Terminal within 24 blocks */
    private TileContainerTerminal terminal() {
        TileContainerTerminal best = null;
        double bd = 24 * 24;
        for (TileEntity te : world.loadedTileEntityList) if (te instanceof TileContainerTerminal t && !te.isInvalid()) { double d = te.getPos().distanceSq(pos); if (d < bd) { bd = d; best = t; } }
        return best;
    }

    @Override
    protected void serverTick() {
        if (world.getTotalWorldTime() % 20 != 7) return;
        long now = world.getTotalWorldTime();
        if (op != 0 && now >= opEnd) { complete(); controlsChanged(); }
        if (alongside() && now >= sails && !boomDown()) { sail(); controlsChanged(); }
        else if (alongside() && now >= sails && now % 1200 == 7)                // sailing time, boom still down over her
            com.dogpound.railmap.alarm.AlarmManager.get(world).raise(world, "port:boom:" + pos.toLong(), com.dogpound.railmap.alarm.AlarmManager.Cat.FREIGHT,
                    com.dogpound.railmap.alarm.AlarmManager.Sev.MINOR, ship + " is due to sail - RAISE THE BOOM", pos, false);
        if (!alongside() && BizConfig.shipEveryDays > 0 && now >= nextCall && !boomDown() && BizConfig.enabled) {
            if (nextCall == 0) nextCall = now + BizConfig.shipEveryDays * BizData.DAY;
            else { arrive(); controlsChanged(); }
        }
        if (now % 1200 == 7) bill();
    }

    /** quay demurrage + the boxes standing out in the weather */
    private void bill() {
        long now = world.getTotalWorldTime();
        Intermodal.Stat st = biz().intermodal.stat(world, pos, true);
        st.quay = quay.size(); st.quayCap = QUAY_CAP;
        for (ItemStack b : quay) {
            biz().custody.exposure(world, b, pos, 1200);
            long since = ItemContainer.since(b), billed = Math.max(since, ItemContainer.lastBilled(b));
            long days = (now - billed) / BizData.DAY;
            if (days < 1) continue;
            boolean over = now - since > BizConfig.quayFreeDays * BizData.DAY;
            if (over) { ItemContainer.addFees(b, days * BizConfig.quayDemurrage); st.demurrage += days * BizConfig.quayDemurrage; }
            ItemContainer.setBilled(b, billed + days * BizData.DAY);
        }
    }

    private boolean demurrage() {
        long now = world.getTotalWorldTime();
        for (ItemStack b : quay) if (now - ItemContainer.since(b) > BizConfig.quayFreeDays * BizData.DAY) return true;
        return false;
    }

    // ---- the desk ---------------------------------------------------------------------------------------------------
    @Override
    protected String press(String id, EntityPlayer who) {
        ItemStack s = selected();
        switch (id) {
            case "prev": sel--; return "";
            case "next": sel++; return "";
            case "manifest": {
                if (who == null) return "";
                who.sendMessage(new TextComponentString("§b⚓ " + (alongside() ? ship + " - sails " + BizData.hhmm(sails) : "No ship alongside - next call day " + nextCall / BizData.DAY)));
                for (ItemStack b : aboard) who.sendMessage(new TextComponentString("§7 aboard " + ItemContainer.pretty(b) + " to " + ItemContainer.destName(b) + ": " + ItemContainer.manifest(b)));
                for (ItemStack b : loaded) who.sendMessage(new TextComponentString("§7 export " + ItemContainer.pretty(b) + ": " + ItemContainer.manifest(b)));
                for (ItemStack b : quay) who.sendMessage(new TextComponentString("§7 quay " + ItemContainer.pretty(b) + (ItemContainer.fees(b) > 0 ? " DUE " + Coins.fmt(ItemContainer.fees(b)) : "") + ": " + ItemContainer.manifest(b)));
                return "";
            }
            case "call": return arrive();
            case "sail": return sail();
            case "disch": {
                String no = craneReady();
                if (no != null) return "§c" + no;
                if (!alongside() || !boomDown()) return "§cA ship alongside and the boom lowered first";
                if (aboard.isEmpty()) return "§7Nothing left aboard to discharge";
                if (quay.size() >= QUAY_CAP) return "§cThe quay is full (" + QUAY_CAP + ") - clear boxes to the terminal or the gate";
                start('D', 200, "");
                return "§7Discharging " + ItemContainer.pretty(aboard.get(0)) + "...";
            }
            case "load": {
                String no = craneReady();
                if (no != null) return "§c" + no;
                if (!alongside() || !boomDown()) return "§cA ship alongside and the boom lowered first";
                if (s.isEmpty()) return "§7Select a box on the quay";
                if (loaded.size() >= SHIP_CAP) return "§c" + ship + " is full";
                if (world.getTotalWorldTime() > sails - 2000) return "§cCUT-OFF passed - " + ship + " takes no more exports";
                Custody.Rec r = biz().custody.box(s);
                if (r.seal == 0 || r.sealBroken) return "§cExports must be SEALED (SEAL) before loading";
                Company c = operator();
                if (c != null && Research.locked(world, c, Research.Tech.CONTAINERISATION)) return Research.why(Research.Tech.CONTAINERISATION);
                start('L', 200, ItemContainer.id(s));
                return "§7Loading " + ItemContainer.pretty(s) + " aboard " + ship + "...";
            }
            case "toterm": {
                String no = craneReady();
                if (no != null && !no.contains("wind")) return "§c" + no;
                if (s.isEmpty()) return "§7Select a box on the quay";
                TileContainerTerminal t = terminal();
                if (t == null) return "§cNo Container Terminal within 24 blocks for the tractor";
                if (!t.apron.isEmpty()) return "§cThe terminal's apron is taken";
                start('T', 300, ItemContainer.id(s));
                return "§7Terminal tractor hauling " + ItemContainer.pretty(s) + " to the rail terminal...";
            }
            case "seal": {
                if (s.isEmpty()) return "§7Select a box on the quay";
                int no = biz().custody.seal(world, s, pos, who == null ? "port" : who.getName());
                return "§aSealed for export: seal " + no;
            }
            case "wash": {
                if (s.isEmpty()) return "§7Select a box on the quay";
                if (ItemContainer.count(s) > 0) return "§7Only an empty box can be washed";
                fee(s, 300, "washing");
                biz().custody.wash(world, s, pos, who == null ? "port" : who.getName());
                return "§aBox washed and inspected clean";
            }
            default: break;
        }
        if (!(who instanceof EntityPlayerMP pl)) return null;
        switch (id) {
            case "appt": return biz().intermodal.book(world, pos, pl.getName());
            case "totruck": {
                if (s.isEmpty()) return "§7Select a box on the quay";
                String full = biz().intermodal.gateRefuse(world, pos, pl);
                if (full != null) return full;
                long due = ItemContainer.fees(s);
                if (due > 0) {
                    if (!Coins.charge(pl, due, "Port fees " + ItemContainer.pretty(s))) return "§c" + Coins.fmt(due) + " in port fees are due on that box";
                    collected(due, "fees on " + ItemContainer.pretty(s));
                    ItemContainer.addFees(s, -due);
                }
                quay.remove(s);
                String appt = biz().intermodal.gate(world, pos, pl, false);
                biz().custody.event(world, s, "TRUCK", pos, pl.getName(), "collected from the quay by truck" + appt);
                if (!pl.inventory.addItemStackToInventory(s)) pl.dropItem(s, false);
                return "§aTruck out: " + ItemContainer.pretty(s) + appt;
            }
            case "fromtruck": {
                ItemStack h = pl.getHeldItemMainhand();
                if (!ItemContainer.is(h)) return "§7Hold a container to bring it onto the quay";
                if (quay.size() >= QUAY_CAP) return "§cThe quay is full";
                String full = biz().intermodal.gateRefuse(world, pos, pl);
                if (full != null) return full;
                ItemStack b = h.splitStack(1);
                ItemContainer.setSince(b, world.getTotalWorldTime()); ItemContainer.setBilled(b, world.getTotalWorldTime());
                quay.add(b);
                String appt = biz().intermodal.gate(world, pos, pl, true);
                biz().custody.event(world, b, "TRUCK", pos, pl.getName(), "delivered to the quay by truck" + appt);
                return "§aTruck in: " + ItemContainer.pretty(b) + " on the quay" + appt;
            }
            default: return null;
        }
    }

    // ---- screens ----------------------------------------------------------------------------------------------------
    @Override
    protected void screens() {
        long now = world.getTotalWorldTime();
        double wind = wind();
        t("sname", alongside() ? ship : "NO SHIP");
        t("seta", alongside() ? "SAILS " + BizData.hhmm(sails) + " D" + sails / BizData.DAY : nextCall > 0 ? "NEXT CALL D" + nextCall / BizData.DAY : "CALL A SHIP");
        t("sman", "IMPORT " + aboard.size() + "  EXPORT " + loaded.size() + "/" + SHIP_CAP);
        int top = Math.max(0, Math.min(Math.floorMod(sel, Math.max(1, quay.size())) - 1, quay.size() - 4));
        for (int i = 0; i < 4; i++) {
            int k = top + i;
            ItemStack b = k < quay.size() ? quay.get(k) : ItemStack.EMPTY;
            boolean on = !quay.isEmpty() && k == Math.floorMod(sel, quay.size());
            t("q" + (i + 1), b.isEmpty() ? "" : (on ? ">" : " ") + ItemContainer.pretty(b) + " " + cut(ItemContainer.destName(b).toUpperCase(Locale.ROOT), 10)
                    + (ItemContainer.fees(b) > 0 ? " $" + ItemContainer.fees(b) / 100 : ""));
        }
        String ready = craneReady();
        t("cstat", op == 'D' ? "DISCHARGING" : op == 'L' ? "LOADING " + opBox : op == 'T' ? "TRACTOR TO TERMINAL" : ready == null ? "CRANE READY" : cut(ready.toUpperCase(Locale.ROOT), 24));
        t("mph", movesHour + " MOVES/H  " + moves + " TOTAL");
        t("fees", Coins.fmt(feesToday));
        t("windd", String.format(Locale.ROOT, "%.0f M/S", wind));
        ItemStack s = selected();
        Custody.Rec r = s.isEmpty() ? null : biz().custody.recs.get(ItemContainer.id(s));
        t("box", s.isEmpty() ? "QUAY EMPTY" : ItemContainer.pretty(s) + (r != null && r.seal > 0 ? " SEAL " + r.seal + (r.sealBroken ? " BROKEN" : "") : " UNSEALED"));
        t("cargo", s.isEmpty() ? "" : cut(ItemContainer.manifest(s).toUpperCase(Locale.ROOT), 28));
        t("qual", r == null ? "" : String.format(Locale.ROOT, "CLEAN %d%%  MOIST %.0f%%  %.0fC", Math.round(r.clean), r.moisture, r.tempC));
        p("pw", term.powered());
        p("ship", alongside());
        v("cutoff", alongside() && now > sails - 2000 ? 1 : 0);
        p("wind", wind > 20);
        p("busy", op != 0);
        p("lockd", op == 'D' || op == 'L');
        p("full", quay.size() >= QUAY_CAP);
        p("demur", demurrage());
        p("boomdn", boomDown());
        p("craneon", ctl.get("k_crane") == 1);
        v("windg", (int) Math.round(wind));
        v("loadg", op == 'D' || op == 'L' ? 18 + (int) (now % 7) : 0);
    }

    @Override
    public void dropAll() {
        super.dropAll();
        for (ItemStack b : quay) net.minecraft.inventory.InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY() + 1, pos.getZ(), b);
        quay.clear();
    }

    @Override public String wiredName() { return "Port Berth"; }
    @Override public String gearState() { return (alongside() ? ship + " alongside" : "no ship") + " · quay " + quay.size() + "/" + QUAY_CAP + " · " + (op != 0 ? "crane working" : "crane idle"); }

    private static NBTTagList boxes(List<ItemStack> l) { NBTTagList t = new NBTTagList(); for (ItemStack s : l) t.appendTag(s.writeToNBT(new NBTTagCompound())); return t; }
    private static void boxes(NBTTagList t, List<ItemStack> l) { l.clear(); for (int i = 0; i < t.tagCount(); i++) { ItemStack s = new ItemStack(t.getCompoundTagAt(i)); if (!s.isEmpty()) l.add(s); } }

    @Override
    protected void save(NBTTagCompound t) {
        t.setTag("quay", boxes(quay)); t.setTag("aboard", boxes(aboard)); t.setTag("loaded", boxes(loaded)); t.setTag("ret", boxes(returning));
        t.setString("ship", ship); t.setLong("arr", arrived); t.setLong("sails", sails); t.setLong("next", nextCall);
        t.setInteger("sel", sel); t.setInteger("shipNo", shipNo); t.setInteger("moves", moves);
        t.setInteger("op", op); t.setLong("opEnd", opEnd); t.setString("opBox", opBox);
    }

    @Override
    protected void load(NBTTagCompound t) {
        boxes(t.getTagList("quay", 10), quay); boxes(t.getTagList("aboard", 10), aboard); boxes(t.getTagList("loaded", 10), loaded); boxes(t.getTagList("ret", 10), returning);
        ship = t.getString("ship"); arrived = t.getLong("arr"); sails = t.getLong("sails"); nextCall = t.getLong("next");
        sel = t.getInteger("sel"); shipNo = t.getInteger("shipNo"); moves = t.getInteger("moves");
        op = (char) t.getInteger("op"); opEnd = t.getLong("opEnd"); opBox = t.getString("opBox");
    }
}
