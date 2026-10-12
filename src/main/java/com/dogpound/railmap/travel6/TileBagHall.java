package com.dogpound.railmap.travel6;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.block.StationControls;
import com.dogpound.railmap.stn.StnFurniture;
import com.dogpound.railmap.stn.StnUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;

import java.util.*;

/**
 * Baggage Hall (batch 6b #9): the reclaim carousel - an oval of steel slats going round, the bags off each train riding
 * on it (drawn going round while it runs) - with its arrivals board over it, and the make-up cart where outbound bags
 * wait for their train (belts deliver them; trains with a baggage car calling here load the ones going their way).
 * Show your claim tag at the carousel (CLAIM, or click the carousel) to get your bag. START / STOP / latching E-STOP
 * for the carousel drive, the amber beacon flashes while a train's bags are coming off, uncollected bags raise an alarm
 * after a game day. Real storage: the carousel (27) and the make-up cart (27) - staff can open both. 12 V + mains.
 */
public class TileBagHall extends StnFurniture {
    private long station = Long.MIN_VALUE, beaconUntil, testUntil;
    private String stationName = "", lastMsg = "";
    private final List<String> board = new ArrayList<>();
    private final Set<Integer> stale = new HashSet<>();
    private boolean estopAlarm;

    public TileBagHall() { super(new String[]{"reclaim", "makeup"}, new String[]{"Reclaim carousel", "Make-up cart"}, new int[]{27, 27}, 1); }

    @Override public String wiredName() { return "Baggage hall"; }
    @Override public String gearState() { return !live() ? "NO SUPPLY" : TileBagCheckin.bagsIn(reclaim()) + " on the carousel · " + TileBagCheckin.bagsIn(makeup()) + " to load"; }
    @Override protected boolean mechanical(String id) { return id.startsWith("m_") || id.startsWith("k_"); }
    public long stationKey() { return station; }
    public String stationName() { return stationName; }
    public IInventory reclaim() { return storage(0); }
    public IInventory makeup() { return storage(1); }
    /** the carousel is going round (the renderer turns the bags) */
    public boolean turning() { return live() && ctl.get("runs") == 1 && ctl.get("m_estop") == 0; }

    @Override
    protected String refuse(int k, EntityPlayer who) {
        if (k == 0 && ItemTravel.is(who.getHeldItemMainhand(), ItemTravel.TAG)) return Baggage.claim(world, who, reclaim(), stationName);   // a click on the carousel with a claim tag
        return ctl.get("k_staff") == 1 || who.capabilities.isCreativeMode ? null : "§7Staff only - turn the STAFF key (show a claim tag at the carousel to get your bag)";
    }

    @Override
    protected String pressMechanical(String id, EntityPlayer who) {
        if (id.equals("m_estop")) {
            ctl.set(id, ctl.get(id) == 1 ? 0 : 1);
            if (ctl.get(id) == 1) ctl.set("runs", 0);
            return ctl.get(id) == 1 ? "§cCarousel E-STOP" : "§eE-STOP pulled back - press START";
        }
        return null;
    }

    @Override public int controlValue(String id) { return live() && testUntil > 0 && !mechanical(id) ? Math.max(1, ctl.get(id)) : super.controlValue(id); }

    @Override
    protected String act(String id, EntityPlayer who) {
        String r = switch (id) {
            case "test" -> { testUntil = world.getTotalWorldTime() + 60; yield "§7Lamp test"; }
            case "start" -> { if (ctl.get("m_estop") == 1) yield "§cE-STOP is in"; ctl.set("runs", 1); yield "§aCarousel running"; }
            case "stop" -> { ctl.set("runs", 0); yield "§eCarousel stopped"; }
            case "claim" -> Baggage.claim(world, who, reclaim(), stationName);
            case "print" -> {
                List<String> out = new ArrayList<>();
                out.add("BAGGAGE HALL - " + stationName);
                TravelData d = TravelData.get(world);
                for (IInventory inv : new IInventory[]{reclaim(), makeup()})
                    for (int i = 0; i < inv.getSizeInventory(); i++) {
                        TravelData.Bag b = d.bags.get(ItemTravel.bagId(inv.getStackInSlot(i)));
                        if (b != null) out.add((inv == reclaim() ? "RECLAIM " : "MAKE-UP ") + b.tag + " " + b.fromName + ">" + b.toName + " " + b.ownerName + " " + b.weightKg() + "kg");
                    }
                StnUtil.print(world, pos, controlFacing(), "Baggage hall", stationName, out);
                yield "§aBag list printed";
            }
            default -> null;
        };
        if (r != null && !r.isEmpty()) lastMsg = net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(r);
        return r;
    }

    /** a train's bags came off here */
    void arrived(String train, String from, int n) {
        board.add(0, StnUtil.up(StnUtil.cut(train, 10)) + " FROM " + StnUtil.up(StnUtil.cut(from, 10)) + " " + n + " BAGS");
        while (board.size() > 4) board.remove(board.size() - 1);
        beaconUntil = world.getTotalWorldTime() + 400;
        if (live() && ctl.get("m_estop") == 0) ctl.set("runs", 1);              // bags coming off start the carousel
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ui.beep_ok"), SoundCategory.BLOCKS, 0.8f, 0.7f);
        markDirty();
    }

    @Override
    protected void work() {
        long now = world.getTotalWorldTime();
        if (testUntil > 0 && now > testUntil) { testUntil = 0; controlsChanged(); }
        if (age % 100 == 13) {
            Map.Entry<Long, String> st = Cards.stationAt(world, pos);
            long k = st == null ? Long.MIN_VALUE : st.getKey();
            if (k != station) { station = k; stationName = st == null ? "" : st.getValue(); markDirty(); }
            TravelData d = TravelData.get(world);
            for (int i = 0; i < reclaim().getSizeInventory(); i++) {                // uncollected bags: an alarm after a game day
                TravelData.Bag b = d.bags.get(ItemTravel.bagId(reclaim().getStackInSlot(i)));
                if (b != null && b.state == TravelData.RECLAIM && now - b.moved > 24000 && stale.add(b.id))
                    AlarmManager.get(world).raise(world, "travel6.bag.stale." + b.id, AlarmManager.Cat.GENERAL, AlarmManager.Sev.INFO, "Bag " + b.tag + " (" + b.ownerName + ") uncollected on the carousel at " + stationName, pos, true);
            }
            stale.removeIf(id -> { TravelData.Bag b = d.bags.get(id); boolean gone = b == null || b.state != TravelData.RECLAIM; if (gone) AlarmManager.get(world).clear("travel6.bag.stale." + id); return gone; });
        }
        boolean es = ctl.get("m_estop") == 1;
        if (es != estopAlarm) {
            estopAlarm = es;
            String key = "travel6.hall.estop." + pos.toLong();
            if (es) AlarmManager.get(world).raise(world, key, AlarmManager.Cat.MACHINE, AlarmManager.Sev.MINOR, "Baggage carousel E-STOP at " + stationName, pos, true);
            else AlarmManager.get(world).clear(key);
            Recorder.event(world, "Baggage carousel at " + stationName + " E-STOP " + (es ? "pressed" : "reset"));
        }
    }

    @Override
    protected boolean refresh() {
        long now = world.getTotalWorldTime();
        boolean ch = false;
        for (int i = 0; i < 4; i++) ch |= StationControls.text(ctl, "b" + (i + 1), i < board.size() ? board.get(i) : "");
        ch |= StationControls.text(ctl, "hdr", station == Long.MIN_VALUE ? "NO STATION" : "BAGGAGE RECLAIM " + StnUtil.up(StnUtil.cut(stationName, 14)));
        TravelData d = TravelData.get(world);
        Map<String, Integer> out = new TreeMap<>();
        for (int i = 0; i < makeup().getSizeInventory(); i++) { TravelData.Bag b = d.bags.get(ItemTravel.bagId(makeup().getStackInSlot(i))); if (b != null) out.merge(b.toName, 1, Integer::sum); }
        int r = 0;
        for (Map.Entry<String, Integer> e : out.entrySet()) { if (r >= 3) break; ch |= StationControls.text(ctl, "mk" + (++r), StnUtil.up(StnUtil.cut(e.getKey(), 12)) + " " + e.getValue()); }
        while (r < 3) ch |= StationControls.text(ctl, "mk" + (++r), r == 1 && out.isEmpty() ? "NO BAGS TO LOAD" : "");
        ch |= StationControls.text(ctl, "msg", cut(lastMsg, 34));
        int onBelt = TileBagCheckin.bagsIn(reclaim());
        ch |= StationControls.put(ctl, "runl", turning()) | StationControls.put(ctl, "beacon", now < beaconUntil) | StationControls.put(ctl, "pwr", term.powered())
                | StationControls.put(ctl, "non", onBelt) | StationControls.put(ctl, "nmk", TileBagCheckin.bagsIn(makeup())) | StationControls.put(ctl, "fulll", !StnFurniture.fits(reclaim(), ItemStack.EMPTY))
                | StationControls.put(ctl, "amps", turning() ? 25 + Math.min(75, onBelt * 6) : 0) | StationControls.put(ctl, "stfl", ctl.get("k_staff") == 1);
        return ch;
    }

    @Override
    protected void save(NBTTagCompound t) {
        t.setLong("st", station); t.setString("stn", stationName); t.setLong("bu", beaconUntil); t.setBoolean("ea", estopAlarm);
        net.minecraft.nbt.NBTTagList l = new net.minecraft.nbt.NBTTagList();
        for (String s : board) l.appendTag(new net.minecraft.nbt.NBTTagString(s));
        t.setTag("board", l);
    }

    @Override
    protected void load(NBTTagCompound t) {
        station = t.hasKey("st") ? t.getLong("st") : Long.MIN_VALUE; stationName = t.getString("stn"); beaconUntil = t.getLong("bu"); estopAlarm = t.getBoolean("ea");
        board.clear();
        net.minecraft.nbt.NBTTagList l = t.getTagList("board", 8);
        for (int i = 0; i < l.tagCount(); i++) board.add(l.getStringTagAt(i));
    }
}
