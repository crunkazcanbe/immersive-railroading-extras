package com.dogpound.railmap.travel6;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.block.StationControls;
import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.economy.CompanyData;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.stn.StnFurniture;
import com.dogpound.railmap.stn.StnUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;

import java.util.*;

/**
 * Park & ride pay station with the SPACES FREE sign on its mast (batch 6b #10). The sign counts the vehicles really
 * parked within the GARAGE radius (Immersive Vehicles + road traffic, Parking.parked) against the bays (CAP + / -,
 * SERVICE key) and shows the number free or FULL; the entry barriers read it and stay shut when it is full. Put your
 * parking ticket in the slot (a real slot: RETURN gives it back): the screen shows the time parked and the fee - the
 * park & ride rate if your Pride Card made a rail journey since you parked. PAY takes it from your bank; touching a
 * Pride Card on the pad pays from the card. The paid ticket comes back out; the exit barrier lets it out within the
 * grace time. Real storage: the ticket slot (staff can open it with the SERVICE key). 12 V + mains (sign, printer).
 */
public class TileParkPay extends StnFurniture {
    static final int[] RADII = {8, 16, 24, 32};
    private int bays = 20, parked;
    private String lastMsg = "", garageName = "";
    private long testUntil, takings;
    private boolean fullAlarm;

    public TileParkPay() { super(new String[]{"slot"}, new String[]{"Ticket slot"}, new int[]{9}, 1); }

    @Override public String wiredName() { return "Park & ride pay station"; }
    @Override public String gearState() { return !live() ? "NO SUPPLY - sign dark" : free() + " spaces free of " + bays + " · " + Coins.fmt(takings) + " taken"; }
    @Override protected boolean mechanical(String id) { return id.startsWith("k_"); }

    public int radius() { return RADII[Math.floorMod(ctl.get("s_rad"), RADII.length)]; }
    public int free() { return Math.max(0, bays - parked); }
    public boolean full() { return live() && parked >= bays; }
    public String garage() { return garageName.isEmpty() ? "car park " + pos.getX() + "," + pos.getZ() : garageName; }
    IInventory slot() { return storage(0); }

    @Override
    protected String refuse(int k, EntityPlayer who) {
        ItemStack held = who.getHeldItemMainhand();
        if (ItemTravel.parkId(held) != 0) {                                      // a click on the slot with a ticket: it goes in
            if (!live()) return "§cThe pay station is dark (no supply)";
            ItemStack left = StnFurniture.insert(slot(), held.copy());
            if (!left.isEmpty()) return "§7The slot already holds a ticket - RETURN it first";
            held.shrink(1);
            TravelData.Park p = Parking.session(world, inSlot());
            world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ui.beep_ok"), SoundCategory.BLOCKS, 0.5f, 1.2f);
            controlsChanged();
            return p == null ? "§cTicket not recognised" : "§7Ticket #" + p.id + ": " + hrs(p) + " h - " + Coins.fmt(Parking.due(world, p)) + (Parking.railSince(world, p) ? " (park & ride rate)" : "") + " - PAY, or touch your Pride Card";
        }
        return ctl.get("k_svc") == 1 ? null : "§7Put your parking ticket in the slot (hold it and click)";
    }

    ItemStack inSlot() { for (int i = 0; i < slot().getSizeInventory(); i++) if (ItemTravel.parkId(slot().getStackInSlot(i)) != 0) return slot().getStackInSlot(i); return ItemStack.EMPTY; }

    long hrs(TravelData.Park p) { return Math.max(1, (world.getTotalWorldTime() - p.in + 999) / 1000); }

    @Override public int controlValue(String id) { return live() && testUntil > 0 && !mechanical(id) ? Math.max(1, ctl.get(id)) : super.controlValue(id); }

    @Override
    protected String act(String id, EntityPlayer who) {
        String r = act0(id, who);
        if (r != null && !r.isEmpty()) lastMsg = net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(r);
        return r;
    }

    private String act0(String id, EntityPlayer who) {
        switch (id) {
            case "test": testUntil = world.getTotalWorldTime() + 60; return "§7Lamp test";
            case "capup": case "capdn":
                if (ctl.get("k_svc") != 1) return "§7Bays are set with the SERVICE key";
                bays = Math.max(1, Math.min(500, bays + (id.equals("capup") ? 1 : -1)));
                return "§7" + bays + " bays";
            case "ret": return giveBack(who, "§7Ticket returned");
            case "pay": case "pad": {
                ItemStack t = inSlot();
                TravelData.Park p = Parking.session(world, t);
                if (p == null) return "§7Put your parking ticket in the slot first";
                if (!p.live()) return "§7That ticket has already left the car park";
                if (Parking.paidUp(world, p)) return "§7Already paid - " + (TravelConfig.exitGraceSeconds * 20L - (world.getTotalWorldTime() - p.paidAt)) / 20 + " s left to exit";
                TravelData.Card card = null;
                if (id.equals("pad")) {
                    ItemStack c = who == null ? ItemStack.EMPTY : Cards.held(who);
                    if (ItemTravel.cardId(who == null ? ItemStack.EMPTY : who.getHeldItemMainhand()) != 0) c = who.getHeldItemMainhand();
                    card = TravelData.get(world).cards.get(ItemTravel.cardId(c));
                    if (card == null) return "§7Touch a Pride Card on the pad";
                }
                long due = Parking.due(world, p);
                String no = Parking.pay(world, who, p, card, garage());
                if (no != null) return "§c" + no;
                if (card != null && who != null) for (ItemStack s : who.inventory.mainInventory) if (ItemTravel.cardId(s) == card.id) ItemTravel.sync(s, card);
                takings += due;
                credit(due);
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.PRINTER, SoundCategory.BLOCKS, 0.4f, 1.4f);
                return giveBack(who, "§aPaid " + Coins.fmt(due) + (p.rail ? " (park & ride)" : "") + (card != null ? " by Pride Card" : "") + " - exit within " + TravelConfig.exitGraceSeconds / 60 + " min");
            }
            case "print": {
                List<String> out = new ArrayList<>();
                out.add("CAR PARK - " + garage());
                out.add(parked + " parked, " + bays + " bays, takings " + Coins.fmt(takings));
                for (TravelData.Park p : TravelData.get(world).parks.values())
                    if (p.garage.equals(garage())) out.add("#" + p.id + " " + p.whoName + " in " + Recorder.clock(world, p.in) + (p.live() ? "" : " out " + Recorder.clock(world, p.out)) + (p.paid > 0 ? " " + Coins.fmt(p.paid) : "") + (p.rail ? " P+R" : ""));
                StnUtil.print(world, pos, controlFacing(), "Car park", garage(), out);
                return "§aCar park log printed";
            }
            default: return null;
        }
    }

    private String giveBack(EntityPlayer who, String msg) {
        ItemStack t = inSlot();
        if (t.isEmpty()) return "§7The slot is empty";
        ItemStack out = t.copy();
        for (int i = 0; i < slot().getSizeInventory(); i++) if (slot().getStackInSlot(i) == t) slot().setInventorySlotContents(i, ItemStack.EMPTY);
        if (who != null && who.inventory.addItemStackToInventory(out)) return msg;
        net.minecraft.inventory.InventoryHelper.spawnItemStack(world, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, out);
        return msg;
    }

    /** the station's operators get park & ride takings */
    private void credit(long cents) {
        Map.Entry<Long, String> st = Cards.stationAt(world, pos);
        if (st == null || cents <= 0) return;
        CompanyData cd = CompanyData.get(world);
        cd.creditShared(world, cd.operatorsAt(world, st.getKey()), Company.Kind.FARES, cents, "Park & ride at " + st.getValue());
    }

    @Override
    protected void work() {
        long now = world.getTotalWorldTime();
        if (testUntil > 0 && now > testUntil) { testUntil = 0; controlsChanged(); }
        if (age % 100 == 41) {
            Map.Entry<Long, String> st = Cards.stationAt(world, pos);
            String g = st == null ? "" : st.getValue() + " P+R";
            if (!g.equals(garageName)) { garageName = g; markDirty(); }
            int p = Parking.parked(world, pos, radius());
            if (p != parked) { parked = p; markDirty(); }
            boolean f = parked >= bays;
            if (f != fullAlarm) {
                fullAlarm = f;
                String key = "travel6.park.full." + pos.toLong();
                if (f) AlarmManager.get(world).raise(world, key, AlarmManager.Cat.GENERAL, AlarmManager.Sev.INFO, garage() + " FULL (" + parked + "/" + bays + ")", pos, true);
                else AlarmManager.get(world).clear(key);
                Recorder.event(world, garage() + (f ? " full" : " has spaces again") + " (" + parked + "/" + bays + ")");
            }
        }
    }

    @Override
    protected boolean refresh() {
        TravelData.Park p = Parking.session(world, inSlot());
        String[] l = new String[4];
        if (p == null) { l[0] = StnUtil.up(StnUtil.cut(garage(), 20)); l[1] = "INSERT TICKET"; l[2] = Coins.fmt(TravelConfig.parkPerHour) + " PER HOUR"; l[3] = "P+R WITH PRIDE CARD " + TravelConfig.parkRidePct + "%"; }
        else {
            l[0] = "TICKET #" + p.id + " IN " + Recorder.clock(world, p.in);
            l[1] = hrs(p) + " H  FEE " + Coins.fmt(Parking.fee(world, p));
            l[2] = Parking.paidUp(world, p) ? "PAID - EXIT NOW" : (Parking.railSince(world, p) ? "P+R DUE " : "DUE ") + Coins.fmt(Parking.due(world, p));
            l[3] = "PAY OR TOUCH PRIDE CARD";
        }
        boolean ch = false;
        for (int i = 0; i < 4; i++) ch |= StationControls.text(ctl, "s" + (i + 1), l[i]);
        ch |= StationControls.text(ctl, "msg", cut(lastMsg, 30));
        ch |= StationControls.text(ctl, "free", full() ? "FULL" : String.valueOf(free()));
        ch |= StationControls.text(ctl, "baysv", parked + "/" + bays);
        ch |= StationControls.text(ctl, "radv", radius() + "M");
        ch |= StationControls.put(ctl, "fulll", full()) | StationControls.put(ctl, "freel", live() && !full()) | StationControls.put(ctl, "pwr", term.powered())
                | StationControls.put(ctl, "paidl", p != null && Parking.paidUp(world, p)) | StationControls.put(ctl, "tktl", p != null)
                | StationControls.put(ctl, "occ", bays == 0 ? 0 : Math.min(100, parked * 100 / bays)) | StationControls.put(ctl, "svcl", ctl.get("k_svc") == 1);
        return ch;
    }

    @Override protected void save(NBTTagCompound t) { t.setInteger("bays", bays); t.setInteger("parked", parked); t.setString("gn", garageName); t.setLong("tk", takings); t.setBoolean("fa", fullAlarm); }
    @Override protected void load(NBTTagCompound t) { bays = t.hasKey("bays") ? t.getInteger("bays") : 20; parked = t.getInteger("parked"); garageName = t.getString("gn"); takings = t.getLong("tk"); fullAlarm = t.getBoolean("fa"); }
}
