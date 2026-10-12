package com.dogpound.railmap.stnlife6;

import com.dogpound.railmap.alarm.Recorder;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/**
 * Hotel room door lock (batch 6c #13): the card reader next to a room's IRON door (iron doors can't be opened by
 * hand). Tap a key card cut for this lock (same serial, not past check-out) on the reader: green, the latch draws and
 * the door opens for 5 s. Anything else: red, refused. ROOM wheels (tens, units) number the room - only while vacant.
 * DND (guest's card in hand) shows DO NOT DISTURB; after check-out the room is DIRTY until housekeeping presses CLEANED.
 * Fail-secure: with no 12 V the door stays shut (reception cuts a new card when it is back). 12 V only (lock battery
 * board on the control supply - it is a 12 V device in real life).
 */
public class TileRoomLock extends com.dogpound.railmap.safety.SafetyTile {
    UUID guest;
    String guestName = "";
    long until;
    int serial, refused, opened;
    boolean dnd, dirty;
    private long flashUntil, closeAt = -1;
    private boolean lastOk, overstay;
    private BlockPos door;

    public int room() { return ctl.get("s_d1") * 10 + ctl.get("s_d2"); }
    public boolean occupied() { return guest != null; }

    @Override public String wiredName() { return "Room " + room() + " lock"; }
    @Override public String gearState() { return !powered() ? "NO 12 V - fail secure" : (occupied() ? "OCCUPIED " + guestName : "VACANT") + (dirty ? " · DIRTY" : "") + (dnd ? " · DND" : "") + " · " + opened + " opened, " + refused + " refused"; }

    void book(EntityPlayer p, long untilT) { guest = p.getUniqueID(); guestName = p.getName(); until = untilT; serial++; dnd = false; overstay = false; markDirty(); }
    void release(String why) {
        if (guest != null) Recorder.event(world, "Hotel room " + room() + ": " + guestName + " checked out (" + why + ")");
        guest = null; guestName = ""; serial++; dnd = false; dirty = true; overstay = false; markDirty();
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (id.startsWith("s_d")) return occupied() ? "§cRoom numbers can't change while it is let" : null;
        if (!powered()) return "§cThe lock is dark - no 12 V (fail secure)";
        long now = world.getTotalWorldTime();
        switch (id) {
            case "reader" -> {
                if (who == null) return "§7Tap a key card on the reader";
                boolean ok = occupied() && ItemRoomKey.opens(who.getHeldItemMainhand(), pos, serial, now);
                flashUntil = now + 40; lastOk = ok;
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev(ok ? "ui.beep_ok" : "ui.beep_error"), SoundCategory.BLOCKS, 0.7f, 1.0f);
                if (!ok) { refused++; return "§cRefused - " + (who.getHeldItemMainhand().getItem() == ItemRoomKey.INSTANCE ? "this card is not valid for room " + room() : "tap your room key card"); }
                opened++;
                return open(true) ? "§aClick - room " + room() + " unlocked" : "§eUnlocked - but there is no iron door next to the reader";
            }
            case "dnd" -> {
                if (who == null || !ItemRoomKey.holds(who, pos, serial, now)) return "§7Only the guest can set DO NOT DISTURB";
                dnd = !dnd; markDirty();
                return dnd ? "§eDO NOT DISTURB" : "§7Make up room";
            }
            case "clean" -> {
                if (occupied()) return "§7Room is let - housekeeping comes after check-out";
                dirty = false; markDirty();
                Recorder.event(world, "Hotel room " + room() + " cleaned by " + who(who));
                return "§aRoom " + room() + " ready to let";
            }
            default -> { return null; }
        }
    }

    private BlockPos findDoor() {
        for (BlockPos p : BlockPos.getAllInBox(pos.add(-2, -1, -2), pos.add(2, 1, 2))) {
            IBlockState s = world.getBlockState(p);
            if (s.getBlock() instanceof BlockDoor && s.getValue(BlockDoor.HALF) == BlockDoor.EnumDoorHalf.LOWER) return p.toImmutable();
        }
        return null;
    }

    private boolean open(boolean o) {
        if (door == null || !(world.getBlockState(door).getBlock() instanceof BlockDoor)) door = findDoor();
        if (door == null) return false;
        ((BlockDoor) world.getBlockState(door).getBlock()).toggleDoor(world, door, o);
        closeAt = o ? world.getTotalWorldTime() + 100 : -1;
        return true;
    }

    @Override
    protected void serverTick() {
        long now = world.getTotalWorldTime();
        if (closeAt > 0 && now >= closeAt) open(false);
        if (age % 10 != 2) return;
        if (occupied() && now > until && !overstay) { overstay = true; Recorder.event(world, "Hotel room " + room() + ": " + guestName + " past check-out time - keys stopped"); }
        boolean f = now < flashUntil;
        put("l_green", f && lastOk);
        put("l_red", f && !lastOk);
        put("l_dnd", dnd);
        put("l_occ", occupied());
        put("l_dirty", dirty);
        put("pwr", powered());
        text("num", String.format("ROOM %02d", room()));
        text("msg", f ? (lastOk ? "WELCOME" : "INVALID CARD") : dnd ? "DO NOT DISTURB" : overstay ? "CHECK OUT DUE" : occupied() ? "OCCUPIED" : dirty ? "MAKE UP ROOM" : "VACANT");
    }

    @Override
    protected void save(NBTTagCompound t) {
        if (guest != null) t.setUniqueId("g", guest);
        t.setString("gn", guestName); t.setLong("until", until); t.setInteger("serial", serial); t.setInteger("ref", refused); t.setInteger("op", opened);
        t.setBoolean("dnd", dnd); t.setBoolean("dirty", dirty); t.setBoolean("over", overstay);
    }

    @Override
    protected void load(NBTTagCompound t) {
        guest = t.hasUniqueId("g") ? t.getUniqueId("g") : null;
        guestName = t.getString("gn"); until = t.getLong("until"); serial = t.getInteger("serial"); refused = t.getInteger("ref"); opened = t.getInteger("op");
        dnd = t.getBoolean("dnd"); dirty = t.getBoolean("dirty"); overstay = t.getBoolean("over");
    }

}
