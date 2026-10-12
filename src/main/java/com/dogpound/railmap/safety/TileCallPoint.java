package com.dogpound.railmap.safety;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;

import java.util.Map;

/**
 * Lineside Emergency Telephone (her ChatGPT list #23 - "emergency calls"): the yellow cabinet with the handset. CALL
 * puts an emergency call through to the signaller: it rings and flashes on every Radio Dispatch Desk, goes in the
 * communications log, and raises an alarm if nobody answers within 30 s. CALLING flashes until the desk ANSWERs, then
 * ANSWERED lights. CANCEL hangs up. Needs 12 V for the line.
 */
public class TileCallPoint extends SafetyTile {
    private int call;

    public String id() { return "LCP " + Math.floorMod(pos.getX() * 7 + pos.getZ() * 13, 1000); }

    @Override public String wiredName() { return "Lineside telephone " + id(); }

    private SafetyData.Comm comm() { return call == 0 ? null : SafetyData.get(world).comm(call); }

    @Override public String gearState() {
        SafetyData.Comm c = comm();
        return !powered() ? "line dead - no 12 V" : c == null ? "on hook" : c.ack == 0 ? "CALLING the signaller" : "answered by " + c.ackBy;
    }

    @Override public int wiredLamp() { SafetyData.Comm c = comm(); return !powered() ? 3 : c != null && c.ack == 0 ? 2 : 1; }
    @Override public void gearInfo(Map<String, Object> m) { m.put("id", id()); m.put("calling", comm() != null && comm().ack == 0); }

    @Override
    protected void serverTick() {
        if (age % 10 != 0) return;
        SafetyData.Comm c = comm();
        put("calling", c != null && c.ack == 0);
        put("answered", c != null && c.ack == 1 && world.getTotalWorldTime() - c.ackTick < 1200);
        put("pwr", powered());
        text("id", id());
        if (c != null && c.ack == 0 && powered() && age % 40 == 0) world.playSound(null, pos, com.dogpound.railmap.ModSounds.BUZZER, SoundCategory.BLOCKS, 0.4f, 1.0f);
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (id.equals("test")) return lampTest();
        if (!powered()) return "§cThe line is dead - no 12 V";
        SafetyData d = SafetyData.get(world);
        if (id.equals("call")) {
            SafetyData.Comm c = comm();
            if (c != null && c.ack == 0) return "§7Already calling - wait for the signaller";
            c = d.log(world, id() + " (" + pos.getX() + "," + pos.getZ() + ")", "DISPATCH", "EMERGENCY CALL from the lineside telephone" + (who == null ? "" : " - " + who.getName()), 1, 0);
            c.pos = pos.toLong();
            call = c.id;
            markDirty();
            return "§6Calling the signaller... (any Radio Dispatch Desk: ANSWER)";
        }
        if (id.equals("cancel")) {
            SafetyData.Comm c = comm();
            if (c != null && c.ack == 0) { c.ack = 3; c.ackBy = "caller hung up"; d.log(world, id(), "DISPATCH", "call cancelled by the caller", 2, 3); }
            call = 0;
            return "§7Hung up";
        }
        return "§7" + gearState();
    }

    @Override protected void save(NBTTagCompound t) { t.setInteger("call", call); }
    @Override protected void load(NBTTagCompound t) { call = t.getInteger("call"); }
}
