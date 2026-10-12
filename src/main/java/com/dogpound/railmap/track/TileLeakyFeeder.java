package com.dogpound.railmap.track;

import com.dogpound.railmap.safety.TileRadioMast;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Tunnel radio - leaky feeder head end (her ChatGPT list #16 "tunnel radio coverage"): a GSM-R repeater cabinet that feeds
 * radiating cable along the tunnel roof. It is a real cell of the train radio network (safety.RadioNet): trains in the
 * tunnel stay registered where an outside mast's signal is blocked by the rock. Cable run 128 / 192 / 256 m.
 */
public class TileLeakyFeeder extends TileRadioMast {
    @Override public String pickName() { return "Tunnel radio"; }
    @Override public String wiredName() { return "Tunnel radio " + cell(); }
    @Override public String cell() { return "T" + Math.floorMod(pos.getX() * 31 + pos.getZ(), 1000); }
    @Override public int radius() { return new int[]{128, 192, 256}[Math.min(2, Math.max(0, ctl.get("s_power")))]; }
    @Override public double txDbm() { return 43; }

    @Override public void onLoad() { super.onLoad(); if (world != null && !world.isRemote) TrackHooks.add(this); }
    @Override public void invalidate() { super.invalidate(); if (world != null && !world.isRemote) TrackHooks.remove(this); }
    @Override public void onChunkUnload() { super.onChunkUnload(); if (world != null && !world.isRemote) TrackHooks.remove(this); }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (id.equals("s_power")) {
            ctl.set("s_power", (ctl.get("s_power") + 1) % 3);
            return "§eLeaky feeder run " + radius() + " m";
        }
        return super.controlPress(id, who);
    }
}
