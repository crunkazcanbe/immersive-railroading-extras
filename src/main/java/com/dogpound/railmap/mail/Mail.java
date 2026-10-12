package com.dogpound.railmap.mail;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.stn.Parcels;
import com.dogpound.railmap.stn.StnFurniture;
import com.dogpound.railmap.stn.StnUtil;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Mail by train (batch 5 #8): addressing, routing and the trains. A letter is addressed to a player ("Alex"), a player
 * at a town ("Alex, Riverside") or a town's post office ("Riverside"). The destination town is the town written on it,
 * else the town of the addressee's Pride Realms mailbox (RealmCoin, soft dependency by reflection), else their
 * registered PO box (REGISTER at a Sorting Office), else it is held for collection where it was posted.
 * <p>When a driverless train starts its stop at a station with an open Sorting Office, bags for this town come off
 * (ARRIVED shelf), bags that change lines here go on the DESPATCH shelf, then waiting bags go on if the train's line
 * serves their town (or gets them to a station that has a line that does - the same rule as parcels). A train with a
 * TPO car (travelling post office: a car fitted with the TPO interior, or whose name says mail / post / TPO) also takes
 * LOOSE letters (posted but not yet sorted) and sorts them en route, dropping each at its town once sorted.
 * At the destination office opened bags go to the addressee's mailbox by delivery robot (RealmCoin), else onto the
 * PICKUP shelves for collection.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class Mail {
    private Mail() {}

    private static final Map<UUID, Long> DWELL = new HashMap<>();

    // ---- addresses ---------------------------------------------------------------------------------------------------------
    /** "Alex, Riverside" -> "Alex" */
    public static String addressee(String to) { int c = to.indexOf(','); return (c < 0 ? to : to.substring(0, c)).trim(); }
    /** "Alex, Riverside" -> "Riverside", "" when no town is written */
    public static String townPart(String to) { int c = to.indexOf(','); return c < 0 ? "" : to.substring(c + 1).trim(); }

    /** a station whose name is this (case-insensitive), or null */
    public static Map.Entry<Long, String> stationNamed(World w, String name) {
        if (name == null || name.trim().isEmpty()) return null;
        for (Map.Entry<Long, String> e : com.dogpound.railmap.server.StationData.get(w).names().entrySet()) if (e.getValue().equalsIgnoreCase(name.trim())) return e;
        return null;
    }

    public static UUID playerId(World w, String name) {
        EntityPlayer p = w.getPlayerEntityByName(name);
        if (p != null) return p.getUniqueID();
        try {
            GameProfile g = w.getMinecraftServer() == null ? null : w.getMinecraftServer().getPlayerProfileCache().getGameProfileForUsername(name);
            return g == null ? null : g.getId();
        } catch (RuntimeException e) { return null; }
    }

    /**
     * work out where a letter goes: sets toPlayer, destKey, destName. Returns how it was resolved (for the log).
     * origin = the posting office's station.
     */
    public static String resolve(World w, MailData.Mail m, long originKey, String originName) {
        String who = addressee(m.to), town = townPart(m.to);
        Map.Entry<Long, String> st = stationNamed(w, town.isEmpty() ? null : town);
        Map.Entry<Long, String> asTown = town.isEmpty() ? stationNamed(w, who) : null;
        m.toPlayer = asTown != null ? null : playerId(w, who);
        if (st != null) { m.destKey = st.getKey(); m.destName = st.getValue(); return "addressed to " + st.getValue(); }
        if (asTown != null) { m.destKey = asTown.getKey(); m.destName = asTown.getValue(); return "post office " + asTown.getValue(); }
        if (m.toPlayer != null) {
            BlockPos box = Robots.mailbox(w, m.toPlayer);
            if (box != null) {
                Map.Entry<Long, String> e = com.dogpound.railmap.stn.StnData.station(w, box, 640);
                if (e != null) { m.destKey = e.getKey(); m.destName = e.getValue(); return "mailbox town " + e.getValue(); }
            }
            Long po = MailData.get(w).poBox.get(m.toPlayer);
            if (po != null && com.dogpound.railmap.server.StationData.get(w).names().containsKey(po)) {
                m.destKey = po; m.destName = com.dogpound.railmap.server.StationData.get(w).names().get(po);
                return "PO box " + m.destName;
            }
        }
        m.destKey = originKey; m.destName = originName;
        return m.toPlayer == null ? "unknown addressee - held at " + originName : "no address on file - held for collection at " + originName;
    }

    public static long postage(ItemStack s) {
        if (ItemMail.is(s, ItemMail.PARCEL)) return MailConfig.parcelBase + (long) MailConfig.parcelPer100g * ((ItemMail.weightG(s) + 99) / 100);
        return MailConfig.letterFee;
    }

    // ---- TPO cars ----------------------------------------------------------------------------------------------------------
    /** does this driverless train have a travelling post office car */
    public static boolean hasTpo(World w, UUID loco) { return tpo(w, loco) > 0; }

    /** 0 = no TPO car, 1 = a TPO with its sorters stood down, 2 = sorting (a TPO fitted car with its SORTING key on, or a car that is a TPO by name) */
    public static int tpo(World w, UUID loco) {
        int best = 0;
        try {
            cam72cam.immersiverailroading.entity.EntityRollingStock l = com.dogpound.railmap.interior.InteriorServer.byUuid(w, loco);
            if (!(l instanceof cam72cam.immersiverailroading.entity.Locomotive lo)) return 0;
            for (cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock c : lo.getTrain()) {
                if (!MailTpo.isTpo(w, c)) continue;
                com.dogpound.railmap.interior.InteriorData.Car ic = com.dogpound.railmap.interior.InteriorData.get(w).peek(c.getUUID());
                boolean fitted = ic != null && ic.layout == com.dogpound.railmap.interior.Interior.Layout.TPO.ordinal();
                best = Math.max(best, !fitted || ic.is("tposort") ? 2 : 1);
            }
        } catch (RuntimeException | LinkageError ignored) { }
        return best;
    }

    // ---- trains ------------------------------------------------------------------------------------------------------------
    static List<TileSortingOffice> offices(World w, long st) {
        List<TileSortingOffice> o = new ArrayList<>();
        for (TileSortingOffice t : StnUtil.near(w, TileSortingOffice.class, BlockPos.fromLong(st), 160)) if (t.stationKey() == st && t.open()) o.add(t);
        return o;
    }

    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote || e.world.provider.getDimension() != 0 || e.world.getTotalWorldTime() % 20 != 9) return;
        try { trains(e.world); } catch (RuntimeException | LinkageError ex) { RailMap.LOG.debug("mail trains: {}", ex.toString()); }
    }

    static void trains(World w) {
        RailwayData rd = RailwayData.get(w);
        MailData d = MailData.get(w);
        long now = w.getTotalWorldTime();
        Set<UUID> carrying = new HashSet<>();
        for (MailData.Mail m : d.mail.values()) if (m.loose && m.train != null) carrying.add(m.train);
        for (UUID t : carrying) if (rd.trains().containsKey(t) && tpo(w, t) < 2)                      // sorters stood down: nothing gets sorted
            for (MailData.Mail m : d.mail.values()) if (m.loose && t.equals(m.train) && m.sortAt > 0) m.sortAt = Math.max(m.sortAt, now) + 20;
        for (RailwayData.AutoTrain a : rd.trains().values()) {
            Autopilot.Run r = Autopilot.run(a.loco);
            long here = r != null && r.dwelling ? r.dwellStation : Long.MIN_VALUE;
            Long was = DWELL.put(a.loco, here);
            if (here == Long.MIN_VALUE || was != null && was == here) continue;
            List<TileSortingOffice> offs = offices(w, here);
            if (offs.isEmpty()) continue;
            String stName = Autopilot.nameOf(com.dogpound.railmap.server.StationData.get(w), here);
            String train = a.label.isEmpty() ? "train" : a.label;
            int tpoLevel = tpo(w, a.loco);
            boolean tpo = tpoLevel > 0;
            // bags off
            for (MailData.Bag b : d.bags.values()) {
                if (b.state != 1 || !a.loco.equals(b.train)) continue;
                boolean dest = b.toKey == here, change = !dest && !Parcels.useful(rd, a.line, here, b.toKey) && Parcels.directFrom(rd, here, b.toKey);
                if (!dest && !change) continue;
                for (TileSortingOffice o : offs) {
                    IInventory inv = dest ? o.arrived() : o.despatch();
                    if (!StnFurniture.fits(inv, ItemStack.EMPTY)) continue;
                    StnFurniture.insert(inv, ItemMail.bag(b));
                    b.state = dest ? 2 : 0; b.at = here; b.train = null; b.moved = now;
                    for (int id : b.items) { MailData.Mail m = d.mail.get(id); if (m != null) { m.state = dest ? MailData.ARRIVED : MailData.BAGGED; m.train = null; m.office = o.getPos().toLong(); d.track(m, now, (dest ? "arrived at " : "off to change at ") + stName + " (bag " + b.seal + ", " + train + ")"); } }
                    d.note(Recorder.clock(w, now) + " bag " + b.seal + " " + (dest ? "arrived at " : "transferred at ") + stName + " off " + train);
                    break;
                }
            }
            // TPO: sorted loose letters for this town are put off in a bag the sorters made up en route
            if (tpo) {
                List<MailData.Mail> drop = new ArrayList<>();
                for (MailData.Mail m : d.mail.values()) if (m.loose && a.loco.equals(m.train) && m.destKey == here && now >= m.sortAt && tpoLevel == 2) drop.add(m);
                if (!drop.isEmpty()) for (TileSortingOffice o : offs) {
                    if (!StnFurniture.fits(o.arrived(), ItemStack.EMPTY)) continue;
                    MailData.Bag b = d.newBag(now, drop.get(0).fromKey, "TPO " + train, here, stName);
                    b.state = 2; b.at = here;
                    for (MailData.Mail m : drop) { b.items.add(m.id); m.loose = false; m.bag = b.id; m.state = MailData.ARRIVED; m.train = null; m.office = o.getPos().toLong(); d.track(m, now, "sorted on the TPO of " + train + ", put off at " + stName + " in bag " + b.seal); }
                    StnFurniture.insert(o.arrived(), ItemMail.bag(b));
                    d.note(Recorder.clock(w, now) + " TPO " + train + " put off " + drop.size() + " letter(s) at " + stName);
                    break;
                }
            }
            // bags on
            for (TileSortingOffice o : offs) {
                IInventory inv = o.despatch();
                for (int i = 0; i < inv.getSizeInventory(); i++) {
                    MailData.Bag b = d.bags.get(ItemMail.bagId(inv.getStackInSlot(i)));
                    if (b == null || b.state != 0 || !Parcels.useful(rd, a.line, here, b.toKey)) continue;
                    b.state = 1; b.train = a.loco; b.trainName = train; b.at = 0; b.moved = now;
                    for (int id : b.items) { MailData.Mail m = d.mail.get(id); if (m != null) { m.state = MailData.ON_TRAIN; m.train = a.loco; m.trainName = train; d.track(m, now, "loaded on " + train + " (" + a.line + ") at " + stName + " in bag " + b.seal); } }
                    inv.setInventorySlotContents(i, ItemStack.EMPTY);
                    d.note(Recorder.clock(w, now) + " bag " + b.seal + " for " + b.toName + " on " + train + " at " + stName);
                }
                // TPO: unsorted (posted) letters go aboard loose and are sorted en route
                if (tpo) {
                    int k = 0;
                    for (MailData.Mail m : d.mail.values()) {
                        RailwayData.Line ln = rd.line(a.line);
                        if (m.state != MailData.POSTED || m.office != o.getPos().toLong() || m.destKey == here || ln == null || !ln.stations.contains(m.destKey)) continue;
                        m.state = MailData.ON_TRAIN; m.loose = true; m.train = a.loco; m.trainName = train; m.sortAt = now + 20L * MailConfig.tpoSortSeconds * ++k;
                        d.track(m, now, "taken loose by the TPO of " + train + " at " + stName + " (sorted en route)");
                    }
                    if (k > 0) { d.note(Recorder.clock(w, now) + " TPO " + train + " took " + k + " loose letter(s) at " + stName); o.controlsChanged(); }
                }
            }
        }
        DWELL.keySet().removeIf(u -> !rd.trains().containsKey(u));
        // mail stranded on trains that left service goes back to the office it was posted at
        for (MailData.Bag b : d.bags.values()) {
            if (b.state != 1 || b.train == null || rd.trains().containsKey(b.train)) continue;
            for (TileSortingOffice o : StnUtil.near(w, TileSortingOffice.class, BlockPos.fromLong(b.fromKey), 160)) {
                if (o.stationKey() != b.fromKey || !StnFurniture.fits(o.despatch(), ItemStack.EMPTY)) continue;
                StnFurniture.insert(o.despatch(), ItemMail.bag(b));
                b.state = 0; b.at = b.fromKey; b.train = null; b.moved = now;
                for (int id : b.items) { MailData.Mail m = d.mail.get(id); if (m != null) { m.state = MailData.BAGGED; m.train = null; d.track(m, now, "returned to " + b.fromName + " (its train left service)"); } }
                break;
            }
        }
        for (MailData.Mail m : d.mail.values())
            if (m.loose && m.train != null && !rd.trains().containsKey(m.train)) { m.loose = false; m.train = null; m.state = MailData.POSTED; m.office = officeOf(w, m.fromKey); d.track(m, now, "back to " + m.fromName + " (its TPO left service)"); }
    }

    static long officeOf(World w, long station) {
        for (TileSortingOffice o : StnUtil.loaded(w, TileSortingOffice.class)) if (o.stationKey() == station) return o.getPos().toLong();
        return 0;
    }

    // ---- delivery robots (Pride Realms / RealmCoin, soft) ------------------------------------------------------------------
    /** RealmCoin's delivery robots and mailboxes, by reflection: IR Extras never needs RealmCoin */
    public static final class Robots {
        private Robots() {}
        private static boolean tried;
        private static Method dataGet, mailbox, send, rescue;
        private static Field addrPos;

        private static void init() {
            if (tried) return;
            tried = true;
            if (!Loader.isModLoaded("realmcoin")) return;
            try {
                Class<?> dd = Class.forName("com.dogpound.realmcoin.delivery.DeliveryData");
                Class<?> dl = Class.forName("com.dogpound.realmcoin.delivery.Delivery");
                dataGet = dd.getMethod("get", World.class);
                mailbox = dd.getMethod("mailbox", UUID.class);
                addrPos = Class.forName("com.dogpound.realmcoin.delivery.DeliveryData$Address").getField("pos");
                send = dl.getMethod("send", EntityPlayer.class, List.class, BlockPos.class, String.class);
                rescue = dl.getMethod("rescue", net.minecraft.server.MinecraftServer.class, UUID.class, String.class, String.class, List.class, EntityPlayer.class);
            } catch (Throwable t) {
                RailMap.LOG.info("[IR Extras] RealmCoin delivery robots not found ({}): mail goes to post office pickup", t.toString());
                send = null;
            }
        }

        public static boolean available() { init(); return send != null && rescue != null && MailConfig.robotDelivery; }

        public static BlockPos mailbox(World w, UUID who) {
            init();
            if (dataGet == null || who == null) return null;
            try {
                World w0 = w.getMinecraftServer() == null ? w : w.getMinecraftServer().getWorld(0);
                Object a = mailbox.invoke(dataGet.invoke(null, w0), who);
                return a == null ? null : (BlockPos) addrPos.get(a);
            } catch (Throwable t) { return null; }
        }

        /** hand mail to a robot: online -> it rolls out from the office to their mailbox (or them); offline -> into the mailbox / kept for login. null = failed */
        public static String deliver(World w, UUID who, String name, ItemStack s, BlockPos office) {
            if (!available() || who == null) return null;
            List<ItemStack> l = new ArrayList<>(Collections.singletonList(s.copy()));
            try {
                EntityPlayer p = w.getMinecraftServer() == null ? null : w.getMinecraftServer().getPlayerList().getPlayerByUUID(who);
                if (p != null) { send.invoke(null, p, l, p.world == w ? office : null, "Pride Post"); return "delivery robot to " + name; }
                return "robot service: " + rescue.invoke(null, w.getMinecraftServer(), who, name, "Pride Post", l, null);
            } catch (Throwable t) {
                RailMap.LOG.warn("[IR Extras] mail robot delivery failed: {}", t.toString());
                return null;
            }
        }
    }
}
