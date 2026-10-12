package com.dogpound.railmap.stnlife6;

import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.block.StationControls;
import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.stn.StnFurniture;
import com.dogpound.railmap.stn.StnUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;

import java.util.*;

/**
 * Station hotel reception desk (batch 6c #13): a three-block counter with the key-card cabinet, cash drawer and linen
 * cupboard (real storage) and a brochure shelf that shows what stands on it. Its rooms are the Room Locks within
 * LifeConfig.hotelReach. ROOM < > picks one (the screen says VACANT / OCCUPIED / DIRTY), NIGHTS + - and the RATE
 * selector (standard / superior x1.6 / suite x2.5) price it; CHECK IN takes the money (Pride coins) and cuts a key card
 * bound to that lock, valid until check-out time (nights x one Minecraft day); CHECK OUT re-keys the lock (old cards
 * die) and marks the room for housekeeping. DUPLICATE cuts another card for the guest. REGISTER prints the guest book.
 * DESK key closes it. 12 V + mains.
 */
public class TileHotelDesk extends StnFurniture {
    static final int[] RATE = {100, 160, 250};
    static final String[] RATE_N = {"STANDARD", "SUPERIOR", "SUITE"};
    private int sel, nights = 1, lets;
    private long takings;
    private final List<String> book = new ArrayList<>();

    public TileHotelDesk() {
        super(new String[]{"keys", "cash", "linen"}, new String[]{"Key card cabinet", "Cash drawer", "Linen cupboard"}, new int[]{9, 9, 27}, 6);
    }

    @Override public String wiredName() { return "Hotel reception"; }
    @Override public String gearState() { List<TileRoomLock> r = rooms(); return !live() ? "NO SUPPLY" : occ(r) + "/" + r.size() + " rooms let · takings " + Coins.fmt(takings); }
    @Override protected boolean mechanical(String id) { return id.startsWith("k_") || id.startsWith("s_"); }
    @Override public double[][] shelfSpots() { return SPOTS; }
    static double[][] SPOTS = {{-11.883, 7.0, 13.4}, {-10.25, 7.0, 13.4}, {-8.617, 7.0, 13.4}, {-6.983, 7.0, 13.4}, {-5.35, 7.0, 13.4}, {-3.717, 7.0, 13.4}};   // tools/gen_bb_batch6c.py

    List<TileRoomLock> rooms() {
        List<TileRoomLock> l = StnUtil.near(world, TileRoomLock.class, pos, LifeConfig.hotelReach);
        l.sort(Comparator.comparingInt(TileRoomLock::room).thenComparingLong(t -> t.getPos().toLong()));
        return l;
    }

    static int occ(List<TileRoomLock> l) { int n = 0; for (TileRoomLock r : l) if (r.occupied()) n++; return n; }

    long price() { return (long) LifeConfig.hotelRate * nights * RATE[Math.floorMod(ctl.get("s_rate"), 3)] / 100; }

    TileRoomLock room() { List<TileRoomLock> l = rooms(); if (l.isEmpty()) return null; sel = Math.floorMod(sel, l.size()); return l.get(sel); }

    @Override
    protected String act(String id, EntityPlayer who) {
        if (!id.equals("bell") && ctl.get("k_open") != 1) return "§cThe desk is closed (DESK key)";
        TileRoomLock r = room();
        long now = world.getTotalWorldTime();
        switch (id) {
            case "rprev", "rnext" -> {
                if (r == null) return "§cNo rooms - fit Room Locks by iron doors within " + LifeConfig.hotelReach + " blocks";
                sel += id.equals("rnext") ? 1 : -1;
                r = room();
                return "§eRoom " + r.room() + " - " + state(r);
            }
            case "nplus" -> { nights = Math.min(14, nights + 1); return "§e" + nights + " night(s) - " + Coins.fmt(price()); }
            case "nminus" -> { nights = Math.max(1, nights - 1); return "§e" + nights + " night(s) - " + Coins.fmt(price()); }
            case "checkin" -> {
                if (who == null) return "§7Guests check in in person";
                if (r == null) return "§cNo room selected";
                if (r.occupied()) return "§cRoom " + r.room() + " is let";
                if (r.dirty) return "§cRoom " + r.room() + " hasn't been made up yet (CLEANED at the room)";
                if (!r.powered()) return "§cRoom " + r.room() + "'s lock has no 12 V";
                long p = price();
                if (who instanceof EntityPlayerMP mp && !Coins.charge(mp, p, "Hotel room " + r.room())) return "§cCard declined - " + Coins.fmt(p) + " for " + nights + " night(s)";
                long until = (now / 24000 + nights) * 24000 + 6000;                     // check-out: noon after the last night
                r.book(who, until);
                ItemStack key = ItemRoomKey.cut(r.getPos(), r.room(), r.serial, until, who.getName());
                if (!who.inventory.addItemStackToInventory(key)) who.dropItem(key, false);
                takings += p; lets++;
                book.add(Recorder.clock(world, now) + " " + who.getName() + " room " + r.room() + " " + nights + "N " + RATE_N[Math.floorMod(ctl.get("s_rate"), 3)] + " " + Coins.fmt(p));
                if (book.size() > 200) book.remove(0);
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.CASH, SoundCategory.BLOCKS, 0.7f, 1.0f);
                Recorder.event(world, "Hotel: " + who.getName() + " checked in to room " + r.room() + " for " + nights + " night(s), " + Coins.fmt(p));
                return "§aWelcome! Room " + r.room() + ", " + nights + " night(s) - here is your key card";
            }
            case "checkout" -> {
                if (r == null || !r.occupied()) return "§7Select the room to check out";
                if (who != null && !who.getUniqueID().equals(r.guest) && !who.capabilities.isCreativeMode) return "§cOnly " + r.guestName + " can check out of room " + r.room();
                if (who != null) for (int i = 0; i < who.inventory.getSizeInventory(); i++) {   // cards handed back
                    ItemStack s = who.inventory.getStackInSlot(i);
                    if (s.getItem() == ItemRoomKey.INSTANCE && s.hasTagCompound() && s.getTagCompound().getLong("lock") == r.getPos().toLong()) who.inventory.setInventorySlotContents(i, ItemStack.EMPTY);
                }
                r.release("at the desk");
                return "§aChecked out of room " + r.room() + " - thank you for staying";
            }
            case "dup" -> {
                if (who == null || r == null || !r.occupied() || !who.getUniqueID().equals(r.guest)) return "§7Only the guest of the selected room gets a duplicate";
                ItemStack key = ItemRoomKey.cut(r.getPos(), r.room(), r.serial, r.until, who.getName());
                if (!who.inventory.addItemStackToInventory(key)) who.dropItem(key, false);
                return "§aDuplicate card cut for room " + r.room();
            }
            case "print" -> {
                List<String> out = new ArrayList<>();
                out.add("HOTEL REGISTER");
                out.addAll(book);
                StnUtil.print(world, pos, controlFacing(), "Hotel register", "Reception", out);
                return "§aRegister printed";
            }
            case "bell" -> { world.playSound(null, pos, com.dogpound.railmap.ModSounds.CASH, SoundCategory.BLOCKS, 0.7f, 1.6f); return "§7Ding! - reception will be with you"; }
            default -> { return null; }
        }
    }

    static String state(TileRoomLock r) { return r.occupied() ? "OCCUPIED " + r.guestName : r.dirty ? "DIRTY" : "VACANT"; }

    @Override
    protected void work() {
        if (age % 40 != 13) return;
        long now = world.getTotalWorldTime();
        for (TileRoomLock r : rooms()) if (r.occupied() && now > r.until + 12000) r.release("overstayed - auto check-out");
    }

    @Override
    protected boolean refresh() {
        boolean ch = false;
        List<TileRoomLock> l = rooms();
        TileRoomLock r = room();
        int o = occ(l), dirty = 0;
        for (TileRoomLock x : l) if (x.dirty) dirty++;
        ch |= StationControls.put(ctl, "pwr", term.powered());
        ch |= StationControls.put(ctl, "l_vac", r != null && !r.occupied() && !r.dirty);
        ch |= StationControls.put(ctl, "l_occ", r != null && r.occupied());
        ch |= StationControls.put(ctl, "l_dirty", r != null && r.dirty);
        ch |= StationControls.put(ctl, "l_dnd", r != null && r.dnd);
        ch |= StationControls.put(ctl, "l_full", !l.isEmpty() && o == l.size());
        ch |= StationControls.put(ctl, "l_open", ctl.get("k_open") == 1);
        ch |= StationControls.put(ctl, "g_occ", l.isEmpty() ? 0 : o * 100 / l.size());
        ch |= StationControls.text(ctl, "sign", "RECEPTION");
        ch |= StationControls.text(ctl, "room", r == null ? "NO ROOMS" : "ROOM " + r.room() + " " + StnUtil.up(StnUtil.cut(state(r), 14)));
        ch |= StationControls.text(ctl, "price", nights + " NIGHTS " + RATE_N[Math.floorMod(ctl.get("s_rate"), 3)] + " " + Coins.fmt(price()));
        ch |= StationControls.text(ctl, "occ", "LET " + o + "/" + l.size() + " DIRTY " + dirty);
        ch |= StationControls.text(ctl, "take", lets + " LETS " + Coins.fmt(takings));
        ch |= StationControls.text(ctl, "clock", Recorder.clock(world, world.getTotalWorldTime()));
        return ch;
    }

    @Override
    protected void save(NBTTagCompound t) {
        t.setInteger("sel", sel); t.setInteger("nights", nights); t.setInteger("lets", lets); t.setLong("take", takings);
        t.setString("book", String.join("\n", book));
    }

    @Override
    protected void load(NBTTagCompound t) {
        sel = t.getInteger("sel"); nights = Math.max(1, t.getInteger("nights")); lets = t.getInteger("lets"); takings = t.getLong("take");
        book.clear();
        if (!t.getString("book").isEmpty()) book.addAll(Arrays.asList(t.getString("book").split("\n")));
    }
}
