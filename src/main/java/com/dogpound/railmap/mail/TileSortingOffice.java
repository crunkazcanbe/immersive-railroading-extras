package com.dogpound.railmap.mail;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.block.StationControls;
import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.stn.Parcels;
import com.dogpound.railmap.stn.StnData;
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
 * Sorting Office (batch 5 #8): the town's post office counter and the sorting frame behind it.
 * <ul>
 *   <li>POSTING BOX: letters and parcels go in; FRANK charges the postage (Pride Realms money when installed), stamps the
 *   postmark, works out where each one goes (Mail.resolve) and puts it in the tray to be sorted.</li>
 *   <li>SORT puts every letter in the tray into its town's pigeonhole (25 holes, labelled live with the town names, the
 *   letters really show in them); BAG ties each hole off into a sealed, numbered mail bag on the DESPATCH shelf.</li>
 *   <li>Driverless trains take the bags (Mail.trains); a train with a TPO car also takes letters still in the tray.</li>
 *   <li>Bags for this town arrive on the ARRIVED shelf; OPEN BAGS hands each letter to a RealmCoin delivery robot for
 *   the addressee's mailbox, or puts it on the PICKUP shelves; COLLECT gives you yours. REGISTER makes this your PO box.</li>
 *   <li>WRAP makes a parcel: things in the wrapping drawer + an addressed letter as its label. PAPER gives writing paper.</li>
 *   <li>The AUTO key sorts one letter a second, bags full holes and opens arriving bags by itself.</li>
 * </ul>
 * 12 V control terminal + a 230 V class mains supply (franking machine, scale, label printer).
 */
public class TileSortingOffice extends StnFurniture {
    public static final int HOLES = 25, COLS = 5, ROWS = 5;
    /** pigeonhole grid (model px): left edge, bottom, hole width / height, front face z - tools/gen_bb_mail.py */
    public static final double HX0 = -13.6, HY0 = 9.0, HW = 2.4, HH = 3.6, HZ = 11.6;
    static final double[][] SPOTS = {{20.35, 7.0, 13.4}, {22.85, 7.0, 13.4}, {25.35, 7.0, 13.4}, {27.85, 7.0, 13.4}};   // tools/gen_bb_mail.py

    /** client + server: letters in each pigeonhole, letters in the tray, bags on each shelf (drawn by MailFrameRenderer) */
    public int[] holes = new int[HOLES];
    public int tray, bagsOut, bagsIn;
    private String station = "", last = "";
    private long stationKey;
    private final List<String> towns = new ArrayList<>();
    private boolean tpoHere, noRoute;
    private long lastBag;

    public TileSortingOffice() {
        super(new String[]{"post", "desp", "arr", "pick", "dr", "ptray"},
                new String[]{"Posting box", "Despatch shelf (bags for the trains)", "Arrived bags", "Pickup (poste restante)", "Stationery drawer", "Parcel wrapping drawer"},
                new int[]{9, 18, 18, 27, 9, 9}, 4);
    }

    @Override public String wiredName() { return "Sorting office"; }
    @Override public double[][] shelfSpots() { return SPOTS; }
    @Override protected boolean mechanical(String id) { return id.startsWith("k_"); }
    @Override public String gearState() { return !live() ? "NO SUPPLY" : (open() ? "OPEN" : "closed") + " · tray " + tray + " · sorted " + sum(holes) + " · bags out " + bagsOut + " in " + bagsIn; }

    public boolean open() { return ctl.get("k_open") == 1 && live(); }
    public long stationKey() { return stationKey; }
    public String station() { return station; }
    IInventory post() { return storage(0); }
    public IInventory despatch() { return storage(1); }
    public IInventory arrived() { return storage(2); }
    IInventory pickup() { return storage(3); }

    static int sum(int[] a) { int n = 0; for (int x : a) n += x; return n; }

    /** pigeonhole for a destination town (alphabetical; past 24 towns = OTHER) */
    int hole(long destKey, String destName) {
        int i = towns.indexOf(destName);
        return i < 0 || i >= HOLES - 1 ? HOLES - 1 : i;
    }

    @Override
    protected String pressMechanical(String id, EntityPlayer who) {
        ctl.set(id, ctl.get(id) == 1 ? 0 : 1);
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev(id.equals("k_open") ? "station.shutter" : "ctl.key"), SoundCategory.BLOCKS, 0.7f, 1.1f);
        if (id.equals("k_open")) return ctl.get(id) == 1 ? "§aPost office OPEN" : "§ePost office CLOSED";
        return ctl.get(id) == 1 ? "§aAUTO: sorting, bagging and opening bags by itself" : "§eAUTO off - sort and bag by hand";
    }

    // ---- the counter ----------------------------------------------------------------------------------------------------------
    @Override
    protected String act(String id, EntityPlayer who) {
        MailData d = MailData.get(world);
        long now = world.getTotalWorldTime();
        switch (id) {
            case "frank" -> {
                if (!open()) return "§cThe counter is closed (COUNTER key)";
                if (stationKey == 0) return "§cNo station near enough to postmark from";
                int n = 0, refused = 0;
                long total = 0;
                for (int i = 0; i < post().getSizeInventory(); i++) {
                    ItemStack s = post().getStackInSlot(i);
                    if (s.isEmpty()) continue;
                    if (!ItemMail.isMail(s) || ItemMail.to(s).isEmpty() || ItemMail.mailId(s) != 0) { refused++; continue; }
                    long fee = Mail.postage(s);
                    if (who instanceof EntityPlayerMP mp && Coins.available() && !Coins.charge(mp, fee, "Postage to " + ItemMail.to(s))) { last = "CANNOT PAY"; return "§cYou can't pay the " + Coins.fmt(fee) + " postage"; }
                    post().setInventorySlotContents(i, ItemStack.EMPTY);
                    frank(d, s, who == null ? (ItemMail.from(s).isEmpty() ? "console" : ItemMail.from(s)) : who.getName(), who == null ? null : who.getUniqueID(), now, false);
                    total += fee;
                    n++;
                }
                if (n == 0) return refused > 0 ? "§cNothing to frank: only addressed letters and parcels (write one on writing paper)" : "§7Put letters or parcels in the posting box first";
                last = n + " FRANKED " + Coins.fmt(total);
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ui.stamp"), SoundCategory.BLOCKS, 0.9f, 0.9f);
                return "§a" + n + " item(s) franked (" + Coins.fmt(total) + ") - in the tray for sorting" + (refused > 0 ? " §7(" + refused + " not mail, left in the box)" : "");
            }
            case "sort" -> { int n = 0; while (sortOne(d, now)) n++; last = n + " SORTED"; return n == 0 ? "§7The tray is empty" : "§a" + n + " letter(s) sorted into the pigeonholes"; }
            case "bag" -> { int n = bagAll(d, now, false); return n == 0 ? (noRoute ? "§cNo bag made: no rail route to those towns" : "§7No letters in the pigeonholes") : "§a" + n + " bag(s) tied, sealed and put on the despatch shelf"; }
            case "open" -> { int n = openBags(d, now); return n == 0 ? "§7No bags on the arrived shelf" : "§a" + n + " letter(s) out of the bags: " + last.toLowerCase(Locale.ROOT); }
            case "collect" -> {
                if (who == null) return "§7Collected in person";
                if (!open()) return "§cThe counter is closed";
                int n = 0;
                boolean master = who.getUniqueID().equals(placer) || who.canUseCommand(2, "");
                for (int i = 0; i < pickup().getSizeInventory(); i++) {
                    ItemStack s = pickup().getStackInSlot(i);
                    if (s.isEmpty()) continue;
                    MailData.Mail m = d.mail.get(ItemMail.mailId(s));
                    boolean mine = Mail.addressee(ItemMail.to(s)).equalsIgnoreCase(who.getName()) || m != null && who.getUniqueID().equals(m.toPlayer)
                            || master && (m == null || m.toPlayer == null);
                    if (!mine) continue;
                    if (m != null) { m.state = MailData.COLLECTED; d.track(m, now, "collected at " + station + " by " + who.getName()); d.deliveredTotal++; }
                    if (!who.inventory.addItemStackToInventory(s.copy())) who.dropItem(s.copy(), false);
                    pickup().setInventorySlotContents(i, ItemStack.EMPTY);
                    n++;
                }
                last = n + " COLLECTED";
                return n == 0 ? "§7Nothing for you at " + station : "§aYou collected " + n + " item(s) of mail";
            }
            case "register" -> {
                if (who == null) return "§7Register in person";
                if (stationKey == 0) return "§cNo station near enough";
                d.poBox.put(who.getUniqueID(), stationKey);
                d.names.put(who.getUniqueID(), who.getName());
                d.markDirty();
                return "§aPO box registered: mail to " + who.getName() + " now comes to " + station + (Mail.Robots.available() ? " §7(a RealmCoin mailbox still wins - robots bring it to your door)" : "");
            }
            case "wrap" -> {
                IInventory t = storage(5);
                ItemStack label = ItemStack.EMPTY;
                int li = -1;
                List<ItemStack> goods = new ArrayList<>();
                for (int i = 0; i < t.getSizeInventory(); i++) {
                    ItemStack s = t.getStackInSlot(i);
                    if (s.isEmpty()) continue;
                    if (li < 0 && ItemMail.is(s, ItemMail.LETTER) && ItemMail.mailId(s) == 0) { label = s; li = i; } else if (!ItemMail.is(s, ItemMail.BAG)) goods.add(s);
                }
                if (li < 0) return "§7Put an addressed letter in the wrapping drawer as the label (and the things to send)";
                if (goods.isEmpty()) return "§7Nothing to wrap - put the things to send in the wrapping drawer";
                ItemStack parcel = ItemMail.parcel(ItemMail.from(label).isEmpty() ? who(who) : ItemMail.from(label), ItemMail.to(label), goods);
                for (int i = 0; i < t.getSizeInventory(); i++) t.setInventorySlotContents(i, ItemStack.EMPTY);
                ItemStack left = insert(post(), parcel);
                if (!left.isEmpty()) { insert(t, left); return "§cThe posting box is full - parcel left in the drawer"; }
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ui.drawer_open"), SoundCategory.BLOCKS, 0.6f, 0.8f);
                return "§aParcel wrapped (" + goods.size() + " stack(s), " + ItemMail.weightG(parcel) + " g, postage " + Coins.fmt(Mail.postage(parcel)) + ") and put in the posting box - FRANK it";
            }
            case "paper" -> {
                if (who == null) return "§7Taken in person";
                IInventory dr = storage(4);
                for (int i = 0; i < dr.getSizeInventory(); i++)
                    if (ItemMail.is(dr.getStackInSlot(i), ItemMail.PAPER)) {
                        ItemStack one = dr.decrStackSize(i, 1);
                        if (!who.inventory.addItemStackToInventory(one)) who.dropItem(one, false);
                        return "§aWriting paper + envelope from the drawer - right-click it to write";
                    }
                if (who instanceof EntityPlayerMP mp && Coins.available() && !Coins.charge(mp, 5, "Writing paper")) return "§cWriting paper costs " + Coins.fmt(5);
                ItemStack p = new ItemStack(MailRegistry.mail, 1, ItemMail.PAPER);
                if (!who.inventory.addItemStackToInventory(p)) who.dropItem(p, false);
                return "§aWriting paper + envelope - right-click it to write";
            }
            case "weigh" -> {
                long fee = 0;
                int g = 0;
                for (int i = 0; i < post().getSizeInventory(); i++) { ItemStack s = post().getStackInSlot(i); if (ItemMail.isMail(s)) { fee += Mail.postage(s); g += ItemMail.weightG(s); } }
                last = g + " G " + Coins.fmt(fee);
                return "§eIn the posting box: " + g + " g, postage " + Coins.fmt(fee);
            }
            case "print" -> {
                List<String> out = new ArrayList<>();
                out.add("MAIL BOOK - " + station);
                for (MailData.Bag b : d.bags.values()) if (b.fromKey == stationKey || b.toKey == stationKey) out.add("BAG " + b.seal + " " + b.fromName + ">" + b.toName + " " + MailData.BSTATE[b.state] + " " + b.items.size() + " items" + (b.state == 1 ? " on " + b.trainName : ""));
                for (MailData.Mail m : d.mail.values()) if (m.fromKey == stationKey || m.destKey == stationKey) out.add("#" + m.id + " " + m.what() + " to " + m.to + " " + MailData.STATE[m.state]);
                StnUtil.print(world, pos, controlFacing(), "Mail book", station, out);
                return "§aMail book printed";
            }
            case "bell" -> { world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("station.chime_pling"), SoundCategory.BLOCKS, 0.8f, 1.2f); return "§7Ding!"; }
            default -> { return null; }
        }
    }

    /** a letter or parcel enters the system: record, postmark, address worked out */
    MailData.Mail frank(MailData d, ItemStack s, String by, UUID byId, long now, boolean late) {
        MailData.Mail m = d.newMail(now);
        m.kind = ItemMail.is(s, ItemMail.PARCEL) ? 1 : 0;
        m.from = ItemMail.from(s).isEmpty() ? by : ItemMail.from(s);
        m.fromPlayer = byId;
        m.to = ItemMail.to(s);
        m.fromKey = stationKey; m.fromName = station;
        String how = Mail.resolve(world, m, stationKey, station);
        ItemStack c = s.copy();
        ItemMail.tag(c).setInteger("mid", m.id);
        ItemMail.tag(c).setString("pm", StnUtil.up(station) + " " + Recorder.clock(world, now) + (late ? " LATE FEE" : ""));
        m.stack = c;
        m.state = MailData.POSTED;
        m.office = pos.toLong();
        d.track(m, now, "posted at " + station + " by " + by + " - " + how);
        d.note(Recorder.clock(world, now) + " #" + m.id + " " + m.what() + " posted at " + station + " for " + m.to + " (" + m.destName + ")");
        com.dogpound.railmap.api.QuestEvents.fire(world, null, pos, "mail_posted", "id", m.id, "to", m.to);
        return m;
    }

    private boolean sortOne(MailData d, long now) {
        for (MailData.Mail m : d.mail.values()) {
            if (m.state != MailData.POSTED || m.office != pos.toLong()) continue;
            if (m.destKey == stationKey) {                                                       // local letter: straight to the addressee
                if (deliver(d, m, now) == 0) { m.state = MailData.ARRIVED; m.bag = 0; m.office = pos.toLong(); d.track(m, now, "waiting for room on the pickup shelves at " + station); }
                return true;
            }
            m.state = MailData.SORTED;
            d.track(m, now, "sorted at " + station + " into the " + StnUtil.up(m.destName) + " pigeonhole");
            return true;
        }
        return false;
    }

    /** tie off the pigeonholes into bags (onlyFull: just the holes at MailConfig.bagAt, AUTO) */
    private int bagAll(MailData d, long now, boolean onlyFull) {
        Map<Long, List<MailData.Mail>> by = new LinkedHashMap<>();
        for (MailData.Mail m : d.mail.values()) if (m.state == MailData.SORTED && m.office == pos.toLong()) by.computeIfAbsent(m.destKey, k -> new ArrayList<>()).add(m);
        int n = 0;
        noRoute = false;
        for (Map.Entry<Long, List<MailData.Mail>> e : by.entrySet()) {
            if (onlyFull && e.getValue().size() < MailConfig.bagAt) continue;
            if (!Parcels.reachable(world, stationKey, e.getKey())) { noRoute = true; continue; }
            if (!fits(despatch(), ItemStack.EMPTY)) break;
            MailData.Mail f = e.getValue().get(0);
            MailData.Bag b = d.newBag(now, stationKey, station, e.getKey(), f.destName);
            for (MailData.Mail m : e.getValue()) { b.items.add(m.id); m.bag = b.id; m.state = MailData.BAGGED; d.track(m, now, "bagged at " + station + " (bag " + b.seal + ")"); }
            insert(despatch(), ItemMail.bag(b));
            d.note(Recorder.clock(world, now) + " bag " + b.seal + " made at " + station + " for " + b.toName + " (" + b.items.size() + ")");
            n++;
        }
        if (n > 0) { lastBag = now; last = n + " BAGS TIED"; world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("machine.clunk"), SoundCategory.BLOCKS, 0.5f, 1.3f); }
        return n;
    }

    private int openBags(MailData d, long now) {
        int n = 0, robot = 0, pick = 0;
        IInventory a = arrived();
        for (int i = 0; i < a.getSizeInventory(); i++) {
            MailData.Bag b = d.bags.get(ItemMail.bagId(a.getStackInSlot(i)));
            if (b == null) { if (ItemMail.is(a.getStackInSlot(i), ItemMail.BAG)) a.setInventorySlotContents(i, ItemStack.EMPTY); continue; }
            boolean all = true;
            for (int id : b.items) {
                MailData.Mail m = d.mail.get(id);
                if (m == null || m.state != MailData.ARRIVED && m.state != MailData.BAGGED) continue;
                int r = deliver(d, m, now);
                if (r == 0) all = false; else { n++; if (r == 1) robot++; else pick++; }
            }
            if (all) { b.state = 3; d.markDirty(); a.setInventorySlotContents(i, ItemStack.EMPTY); }
        }
        for (MailData.Mail m : d.mail.values())                                                  // local mail that found the pickup shelves full
            if (m.state == MailData.ARRIVED && m.bag == 0 && m.office == pos.toLong()) { int r = deliver(d, m, now); if (r > 0) { n++; if (r == 1) robot++; else pick++; } }
        if (n > 0) last = robot + " ROBOT " + pick + " PICKUP";
        return n;
    }

    /** 1 = robot, 2 = pickup shelf, 0 = no room (stays in the bag) */
    int deliver(MailData d, MailData.Mail m, long now) {
        if (m.toPlayer != null && Mail.Robots.mailbox(world, m.toPlayer) != null) {
            String r = Mail.Robots.deliver(world, m.toPlayer, Mail.addressee(m.to), m.stack, pos);
            if (r != null) {
                m.stack = ItemStack.EMPTY; m.state = MailData.DELIVERED; m.office = 0;
                d.robotTotal++; d.deliveredTotal++;
                d.track(m, now, "handed to a Pride Realms " + r + " at " + station);
                return 1;
            }
        }
        try { if (com.dogpound.railmap.freight6.TileParcelLockers.fromMail(world, stationKey, m)) { d.pickupTotal++; return 2; } } catch (RuntimeException | LinkageError ignored) { }   // batch 6 #29: the station's parcel lockers (code by chat)
        if (!fits(pickup(), m.stack)) return 0;
        insert(pickup(), m.stack);
        m.stack = ItemStack.EMPTY; m.state = MailData.PICKUP; m.office = pos.toLong();
        d.pickupTotal++;
        d.track(m, now, "on the pickup shelf at " + station);
        return 2;
    }

    // ---- ticking ----------------------------------------------------------------------------------------------------------
    @Override
    protected void work() {
        if (age % 20 != 11) return;
        Map.Entry<Long, String> st = StnData.station(world, pos, 128);
        station = st == null ? "" : st.getValue();
        stationKey = st == null ? 0 : st.getKey();
        towns.clear();
        for (String n : new TreeSet<>(com.dogpound.railmap.server.StationData.get(world).names().values())) if (!n.equals(station)) towns.add(n);
        MailData d = MailData.get(world);
        long now = world.getTotalWorldTime();
        if (open() && ctl.get("k_auto") == 1) {
            sortOne(d, now);
            if (bagAll(d, now, true) == 0 && now - lastBag > 2400) { bagAll(d, now, false); lastBag = now; }
            if (age % 100 == 11) openBags(d, now);
        }
        int[] h = new int[HOLES];
        int t = 0;
        for (MailData.Mail m : d.mail.values()) {
            if (m.office != pos.toLong()) continue;
            if (m.state == MailData.SORTED) h[hole(m.destKey, m.destName)]++;
            else if (m.state == MailData.POSTED) t++;
        }
        tpoHere = false;
        RailwayData rd = RailwayData.get(world);
        for (RailwayData.AutoTrain a : rd.trains().values()) {
            Autopilot.Run r = Autopilot.run(a.loco);
            if (r != null && r.dwelling && r.dwellStation == stationKey && Mail.hasTpo(world, a.loco)) { tpoHere = true; break; }
        }
        int out = count(despatch()), in = count(arrived());
        boolean ch = !Arrays.equals(h, holes) || t != tray || out != bagsOut || in != bagsIn;
        holes = h; tray = t; bagsOut = out; bagsIn = in;
        if (count(pickup()) >= pickup().getSizeInventory() - 2) AlarmManager.get(world).raise(world, "mail:pick:" + pos.toLong(), AlarmManager.Cat.GENERAL, AlarmManager.Sev.MINOR, "Sorting office " + station + ": pickup shelves full - arrived mail is waiting in its bags", pos, false);
        else AlarmManager.get(world).clear("mail:pick:" + pos.toLong());
        if (ch) controlsChanged();
    }

    static int count(IInventory inv) { int n = 0; for (int i = 0; i < inv.getSizeInventory(); i++) if (!inv.getStackInSlot(i).isEmpty()) n++; return n; }

    @Override
    protected boolean refresh() {
        boolean ch = false;
        MailData d = MailData.get(world);
        ch |= StationControls.put(ctl, "pwr", term.powered());
        ch |= StationControls.put(ctl, "openl", ctl.get("k_open") == 1);
        ch |= StationControls.put(ctl, "autol", ctl.get("k_auto") == 1);
        ch |= StationControls.put(ctl, "trayl", tray > 0);
        ch |= StationControls.put(ctl, "outl", bagsOut > 0);
        ch |= StationControls.put(ctl, "inl", bagsIn > 0);
        ch |= StationControls.put(ctl, "pickl", count(pickup()) > 0);
        ch |= StationControls.put(ctl, "robotl", Mail.Robots.available());
        ch |= StationControls.put(ctl, "tpol", tpoHere);
        ch |= StationControls.put(ctl, "noroute", noRoute);
        ch |= StationControls.put(ctl, "fill", Math.min(100, sum(holes) * 100 / Math.max(1, HOLES * MailConfig.bagAt)));
        ch |= StationControls.text(ctl, "sign", "PRIDE POST");
        ch |= StationControls.text(ctl, "stn", StnUtil.up(StnUtil.cut(station.isEmpty() ? "NO STATION" : station, 16)));
        ch |= StationControls.text(ctl, "counts", "TRAY " + tray + " SORTED " + sum(holes) + " OUT " + bagsOut);
        ch |= StationControls.text(ctl, "inbound", "BAGS IN " + bagsIn + " PICKUP " + count(pickup()));
        ch |= StationControls.text(ctl, "fee", "LETTER " + Coins.fmt(MailConfig.letterFee));
        ch |= StationControls.text(ctl, "last", last);
        ch |= StationControls.text(ctl, "robot", Mail.Robots.available() ? "ROBOTS ON" : "PICKUP ONLY");
        // live: bags in transit to / from this town
        List<String> tr = new ArrayList<>();
        for (MailData.Bag b : d.bags.values()) if (b.state == 1 && (b.fromKey == stationKey || b.toKey == stationKey)) tr.add("BAG " + b.seal + (b.toKey == stationKey ? " IN FROM " + StnUtil.up(StnUtil.cut(b.fromName, 9)) : " TO " + StnUtil.up(StnUtil.cut(b.toName, 9))) + " " + StnUtil.up(StnUtil.cut(b.trainName, 8)));
        int loose = 0;
        for (MailData.Mail m : d.mail.values()) if (m.loose && (m.fromKey == stationKey || m.destKey == stationKey)) loose++;
        if (loose > 0) tr.add(0, loose + " LOOSE ON TPO");
        for (int i = 0; i < 4; i++) ch |= StationControls.text(ctl, "t" + (i + 1), i < tr.size() ? tr.get(i) : i == 0 ? "NO BAGS IN TRANSIT" : "");
        for (int i = 0; i < HOLES; i++) ch |= StationControls.text(ctl, "h" + i, i == HOLES - 1 ? "OTHER" : i < towns.size() ? StnUtil.up(StnUtil.cut(towns.get(i), 7)) : "");
        return ch;
    }

    @Override
    protected void save(NBTTagCompound t) {
        t.setString("last", last); t.setIntArray("holes", holes); t.setInteger("tray", tray); t.setInteger("bo", bagsOut); t.setInteger("bi", bagsIn); t.setLong("lastBag", lastBag);
    }

    @Override
    protected void load(NBTTagCompound t) {
        last = t.getString("last");
        int[] h = t.getIntArray("holes");
        holes = h.length == HOLES ? h : new int[HOLES];
        tray = t.getInteger("tray"); bagsOut = t.getInteger("bo"); bagsIn = t.getInteger("bi"); lastBag = t.getLong("lastBag");
    }
}
