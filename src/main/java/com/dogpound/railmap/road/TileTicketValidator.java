package com.dogpound.railmap.road;

import com.dogpound.railmap.item.ItemTicket;
import com.dogpound.railmap.safety.SafetyTile;
import com.dogpound.railmap.sound.PackSounds;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;

/**
 * Ticket validator post at a tram stop (batch 5 #4). Tap / insert a ticket on the reader: a travel pass is checked
 * against the stop's fare zone and counted as a ride; a single or return valid from or to this stop (the nearest Tram
 * Stop within 16 blocks) is punched and stamped with the time and stop; anything else is refused (red lamp, error
 * beep). A good validation also tells a REQUEST stop someone wants the tram. 12 V; dead without it.
 */
public class TileTicketValidator extends SafetyTile {
    private int ok, refused;
    private long flashUntil;
    private boolean lastOk;

    @Override public String wiredName() { return "Ticket validator " + pos.getX() + "," + pos.getZ(); }
    @Override public String gearState() { return !powered() ? "NO 12 V - out of order" : ok + " validated · " + refused + " refused"; }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (!id.equals("reader")) return "§7" + gearState();
        if (who == null) return "§7Present the ticket at the post";
        if (!powered()) return "§cThe validator is dark - OUT OF ORDER (no 12 V)";
        String r = validate(who.getHeldItemMainhand());
        flashUntil = world.getTotalWorldTime() + 40;
        world.playSound(null, pos, PackSounds.ev(lastOk ? "ui.beep_ok" : "ui.beep_error"), SoundCategory.BLOCKS, 0.7f, 1.0f);
        text("msg", lastOk ? "VALID" : "INVALID");
        markDirty();
        return (lastOk ? "§a" : "§c") + r;
    }

    private String validate(ItemStack held) {
        TileTramStop stop = TileTramStop.nearest(world, pos, 16);
        lastOk = false;
        if (stop == null) { refused++; return "No tram stop here - the validator isn't set up"; }
        long station = stop.getPos().toLong();
        long now = world.getTotalWorldTime();
        if (ItemTicket.isPass(held)) {
            int zone = com.dogpound.railmap.economy.PassengerData.get(world).zoneOf(station);
            String no = ItemTicket.passRefused(held, now, zone);
            if (no != null) { refused++; return no; }
            ItemTicket.ride(held);
            stamp(held, stop, now);
            return "Pass valid - zone " + zone + ", " + stop.stopName();
        }
        if (!ItemTicket.valid(held)) { refused++; return "Tap a ticket or pass on the reader"; }
        if (ItemTicket.isSpent(held)) { refused++; return "That ticket is spent"; }
        if (ItemTicket.from(held) != station && ItemTicket.to(held) != station) { refused++; return "Not valid here - that ticket is " + ItemTicket.fromName(held) + " to " + ItemTicket.toName(held); }
        ItemTicket.punch(held);
        stamp(held, stop, now);
        return "Validated - " + ItemTicket.fromName(held) + " to " + ItemTicket.toName(held);
    }

    private void stamp(ItemStack held, TileTramStop stop, long now) {
        held.getTagCompound().setLong("irxValAt", now);
        held.getTagCompound().setString("irxValStop", stop.stopName());
        ok++; lastOk = true;
        stop.validated();
    }

    @Override
    protected void serverTick() {
        if (age % 4 != 0) return;
        boolean f = world.getTotalWorldTime() < flashUntil;
        put("ok", f && lastOk);
        put("no", f && !lastOk);
        if (!f) text("msg", "TAP HERE");
    }

    @Override protected boolean keepTextUnpowered() { return false; }
    @Override protected void save(NBTTagCompound t) { t.setInteger("ok", ok); t.setInteger("ref", refused); }
    @Override protected void load(NBTTagCompound t) { ok = t.getInteger("ok"); refused = t.getInteger("ref"); }
}
