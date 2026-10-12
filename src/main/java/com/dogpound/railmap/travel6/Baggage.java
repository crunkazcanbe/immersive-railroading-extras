package com.dogpound.railmap.travel6;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.server.StationData;
import com.dogpound.railmap.stn.Parcels;
import com.dogpound.railmap.stn.StnFurniture;
import com.dogpound.railmap.stn.StnUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.*;

/**
 * Checked baggage by train (batch 6b #9) + the travel ticker. Each second, a driverless train that has just stopped at
 * a station: puts off the bags it carries for here (onto the Baggage Hall carousel, or a check-in desk's hold if the
 * station has no hall) and bags that change trains here (the hall's make-up cart); then, if it has a baggage car (or
 * TravelConfig.needBaggageCar is off), loads the outbound bags waiting in the make-up carts and check-in holds that
 * its line gets closer to their destination. Bags on a train that leaves service go back to where they were checked.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class Baggage {
    private Baggage() {}

    private static final Map<UUID, Long> DWELL = new HashMap<>();

    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote || e.world.provider.getDimension() != 0) return;
        long t = e.world.getTotalWorldTime();
        if (t % 20 == 13) try { trains(e.world); } catch (RuntimeException | LinkageError ex) { RailMap.LOG.debug("baggage trains: {}", ex.toString()); }
        if (t % 1200 == 77) try { Cards.sweep(e.world); TravelData.get(e.world).tidy(t); } catch (RuntimeException | LinkageError ex) { RailMap.LOG.debug("travel sweep: {}", ex.toString()); }
    }

    // ---- belts hand bags over ------------------------------------------------------------------------------------------
    /** a belt's end delivers a stack to whatever is there; returns what was not taken */
    public static ItemStack accept(World w, TileEntity t, ItemStack s) {
        TravelData d = TravelData.get(w);
        TravelData.Bag b = d.bags.get(ItemTravel.bagId(s));
        if (t instanceof TileBagHall h && h.live()) {
            if (b != null && b.state == TravelData.CHECKED && h.stationKey() == b.fromKey) {
                ItemStack left = StnFurniture.insert(h.makeup(), s);
                if (left.isEmpty()) { b.state = TravelData.MAKEUP; b.moved = w.getTotalWorldTime(); b.note(Recorder.clock(w, b.moved) + " into the make-up cart at " + h.stationName()); d.markDirty(); h.controlsChanged(); }
                return left;
            }
            return s;                                                           // not an outbound bag of this station: it stays on the belt (jam)
        }
        if (t instanceof TileBagCheckin c && c.live()) {
            ItemStack left = StnFurniture.insert(c.hold(), s);
            if (left.isEmpty()) c.controlsChanged();
            return left;
        }
        return s;
    }

    // ---- reclaim ---------------------------------------------------------------------------------------------------------
    /** show a claim tag at a carousel / desk: hands over the matching bag from `inv` */
    public static String claim(World w, EntityPlayer who, IInventory inv, String stationName) {
        if (who == null) return "§7Bags are claimed in person";
        ItemStack tag = who.getHeldItemMainhand();
        if (!ItemTravel.is(tag, ItemTravel.TAG)) return "§7Show your claim tag (hold it) to collect your bag";
        TravelData d = TravelData.get(w);
        TravelData.Bag b = d.bags.get(ItemTravel.bagId(tag));
        if (b == null) return "§cThat tag's bag is not in the system any more";
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            if (ItemTravel.bagId(inv.getStackInSlot(i)) != b.id) continue;
            inv.setInventorySlotContents(i, ItemStack.EMPTY);
            ItemStack bag = ItemTravel.packed(b);
            tag.shrink(1);
            if (!who.inventory.addItemStackToInventory(bag)) who.dropItem(bag, false);
            b.state = TravelData.COLLECTED; b.moved = w.getTotalWorldTime();
            b.note(Recorder.clock(w, b.moved) + " collected at " + stationName + " by " + who.getName());
            d.note(Recorder.clock(w, b.moved) + " bag " + b.tag + " collected at " + stationName);
            d.markDirty();
            return "§aYour bag " + b.tag + " - welcome to " + stationName;
        }
        return "§7Bag " + b.tag + " is " + TravelData.BSTATE[b.state].toLowerCase(Locale.ROOT) + (b.state == TravelData.ON_TRAIN ? " (" + b.trainName + ")" : "") + " - it's going to " + b.toName;
    }

    // ---- trains ----------------------------------------------------------------------------------------------------------
    static final String CAR_RX = ".*(baggage|luggage|brake|guard|parcel|\\bvan\\b|mail|combine).*";

    /** does this train have a baggage car */
    public static boolean baggageCar(World w, UUID loco) {
        if (!TravelConfig.needBaggageCar) return true;
        try {
            cam72cam.immersiverailroading.entity.EntityRollingStock l = com.dogpound.railmap.interior.InteriorServer.byUuid(w, loco);
            if (!(l instanceof cam72cam.immersiverailroading.entity.Locomotive lo)) return false;
            for (cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock c : lo.getTrain())
                if ((c.getDefinition().name() + " " + c.getDefinitionID()).toLowerCase(Locale.ROOT).matches(CAR_RX)) return true;
        } catch (RuntimeException | LinkageError ignored) { }
        return false;
    }

    static <T extends TileEntity> List<T> at(World w, Class<T> c, long st) {
        List<T> o = new ArrayList<>();
        for (T t : StnUtil.near(w, c, BlockPos.fromLong(st), 160)) {
            long k = t instanceof TileBagHall h ? h.stationKey() : t instanceof TileBagCheckin ci ? ci.stationKey() : Long.MIN_VALUE;
            if (k == st) o.add(t);
        }
        return o;
    }

    static void trains(World w) {
        RailwayData rd = RailwayData.get(w);
        TravelData d = TravelData.get(w);
        long now = w.getTotalWorldTime();
        boolean any = false;
        for (TravelData.Bag b : d.bags.values()) if (b.state < TravelData.RECLAIM) { any = true; break; }
        if (!any) { DWELL.clear(); return; }
        for (RailwayData.AutoTrain a : rd.trains().values()) {
            Autopilot.Run r = Autopilot.run(a.loco);
            long here = r != null && r.dwelling ? r.dwellStation : Long.MIN_VALUE;
            Long was = DWELL.put(a.loco, here);
            if (here == Long.MIN_VALUE || was != null && was == here) continue;
            List<TileBagHall> halls = at(w, TileBagHall.class, here);
            List<TileBagCheckin> desks = at(w, TileBagCheckin.class, here);
            if (halls.isEmpty() && desks.isEmpty()) continue;
            String stName = Autopilot.nameOf(StationData.get(w), here);
            String train = a.label.isEmpty() ? "train" : a.label;
            // bags off: for here (carousel / desk hold) or changing here (make-up cart)
            Map<TileBagHall, Integer> offAt = new HashMap<>();
            for (TravelData.Bag b : d.bags.values()) {
                if (b.state != TravelData.ON_TRAIN || !a.loco.equals(b.train)) continue;
                boolean dest = b.toKey == here, change = !dest && !Parcels.useful(rd, a.line, here, b.toKey) && Parcels.directFrom(rd, here, b.toKey);
                if (!dest && !change) continue;
                IInventory into = null;
                TileBagHall hall = null;
                for (TileBagHall h : halls) { IInventory inv = dest ? h.reclaim() : h.makeup(); if (StnFurniture.fits(inv, ItemStack.EMPTY)) { into = inv; hall = h; break; } }
                if (into == null) for (TileBagCheckin c : desks) if (StnFurniture.fits(c.hold(), ItemStack.EMPTY)) { into = c.hold(); break; }
                if (into == null) continue;                                     // nowhere to put it: it rides on
                StnFurniture.insert(into, ItemTravel.suitcase(b));
                b.state = dest ? TravelData.RECLAIM : hall != null ? TravelData.MAKEUP : TravelData.CHECKED;
                b.train = null; b.moved = now;
                b.note(Recorder.clock(w, now) + (dest ? " off " + train + " at " + stName + " - to reclaim" : " off " + train + " at " + stName + " to change trains"));
                if (dest && hall != null) offAt.merge(hall, 1, Integer::sum);
                d.markDirty();
            }
            for (Map.Entry<TileBagHall, Integer> e : offAt.entrySet()) {
                e.getKey().arrived(train, Autopilot.nameOf(StationData.get(w), a.line.isEmpty() ? here : prevStop(rd, a, here)), e.getValue());
                Recorder.event(w, e.getValue() + " bag(s) off " + train + " onto the carousel at " + stName);
            }
            // bags on: outbound bags in the make-up carts and desk holds that this line takes closer
            if (!baggageCar(w, a.loco)) continue;
            int n = 0;
            List<IInventory> waiting = new ArrayList<>();
            for (TileBagHall h : halls) waiting.add(h.makeup());
            for (TileBagCheckin c : desks) waiting.add(c.hold());
            for (IInventory inv : waiting)
                for (int i = 0; i < inv.getSizeInventory(); i++) {
                    TravelData.Bag b = d.bags.get(ItemTravel.bagId(inv.getStackInSlot(i)));
                    if (b == null || (b.state != TravelData.MAKEUP && b.state != TravelData.CHECKED) || b.toKey == here || !Parcels.useful(rd, a.line, here, b.toKey)) continue;
                    inv.setInventorySlotContents(i, ItemStack.EMPTY);
                    b.state = TravelData.ON_TRAIN; b.train = a.loco; b.trainName = train; b.moved = now;
                    b.note(Recorder.clock(w, now) + " loaded on " + train + " (" + a.line + ") at " + stName);
                    n++;
                }
            if (n > 0) { d.note(Recorder.clock(w, now) + " " + n + " bag(s) loaded on " + train + " at " + stName); Recorder.event(w, n + " bag(s) loaded on " + train + " at " + stName); d.markDirty(); }
        }
        DWELL.keySet().removeIf(u -> !rd.trains().containsKey(u));
        // bags on a train that left service go back to where they were checked in
        for (TravelData.Bag b : d.bags.values()) {
            if (b.state != TravelData.ON_TRAIN || b.train == null || rd.trains().containsKey(b.train)) continue;
            IInventory into = null;
            for (TileBagHall h : at(w, TileBagHall.class, b.fromKey)) if (StnFurniture.fits(h.reclaim(), ItemStack.EMPTY)) { into = h.reclaim(); break; }
            if (into == null) for (TileBagCheckin c : at(w, TileBagCheckin.class, b.fromKey)) if (StnFurniture.fits(c.hold(), ItemStack.EMPTY)) { into = c.hold(); break; }
            if (into == null) continue;
            StnFurniture.insert(into, ItemTravel.suitcase(b));
            b.state = TravelData.RECLAIM; b.train = null; b.moved = now;
            b.note(Recorder.clock(w, now) + " returned to " + b.fromName + " (its train left service) - collect it there");
            d.note(Recorder.clock(w, now) + " bag " + b.tag + " returned to " + b.fromName + " (train left service)");
            d.markDirty();
        }
    }

    /** the stop before `here` on the train's line (for the arrivals board) */
    static long prevStop(RailwayData rd, RailwayData.AutoTrain a, long here) {
        RailwayData.Line l = rd.line(a.line);
        if (l == null) return here;
        int i = l.stations.indexOf(here);
        int p = i - a.direction;
        return i < 0 || p < 0 || p >= l.stations.size() ? here : l.stations.get(p);
    }
}
