package com.dogpound.railmap.safety;

import com.dogpound.railmap.block.StationControls;
import com.dogpound.railmap.controls.ControlState;
import com.dogpound.railmap.controls.IControlled;
import com.dogpound.railmap.grid.Panel;
import com.dogpound.railmap.wire.IWiredGear;
import com.dogpound.railmap.wire.Terminal;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;

/**
 * Shared plumbing for the signalling-safety gear: the model's working controls (ControlState), the 12 V terminal that
 * puts it on the Control Center's WIRED page, save + client sync. New gear never existed before its terminal, so it
 * is never "legacy": no 12 V means it is dead (and fails safe), from the moment it is placed.
 */
public abstract class SafetyTile extends TileEntity implements ITickable, IControlled, IWiredGear {
    protected final ControlState ctl = new ControlState();
    protected final Terminal term = new Terminal();
    protected int age;
    private boolean dirty;

    @Override public ControlState controls() { return ctl; }
    @Override public void controlsChanged() { markDirty(); if (world != null && !world.isRemote) { IBlockState s = world.getBlockState(pos); world.notifyBlockUpdate(pos, s, s, 3); } }
    @Override public Terminal term() { return term; }
    @Override public void gearChanged() { controlsChanged(); }
    @Override public Panel wiredPanel() { return layout().all; }
    @Override public String wiredPress(String id, String who) { return com.dogpound.railmap.cabinet.Cabinet.isCabinetId(id) ? "§7Cabinet MCBs and fuses are worked by hand, at the cabinet" : IControlled.operate(this, id, null); }
    @Override public int wiredState(String id) { return controlValue(id); }

    public boolean powered() { return term.powered(); }

    /** lamps, displays and dials are dark with no 12 V; switches, keys and mushrooms stay where they were put */
    @Override
    public int controlValue(String id) {
        if (id.startsWith("k_") || id.startsWith("s_") || id.startsWith("m_")) return ctl.get(id);
        if (ctl.get("lamptest") == 1 && powered()) return 1;
        return powered() ? ctl.get(id) : 0;
    }

    @Override public String ptext(String key) { return (powered() || keepTextUnpowered()) && com.dogpound.railmap.cabinet.Cabinet.screenOk(ctl, key) ? ctl.text(key) : ""; }   // scr: its cabinet's screen MCB / fuse

    protected boolean keepTextUnpowered() { return false; }

    /** store a reading for the client; sync happens once at the end of the tick */
    protected void put(String id, int v) { dirty |= StationControls.put(ctl, id, v); }
    protected void put(String id, boolean v) { dirty |= StationControls.put(ctl, id, v); }
    protected void text(String id, String t) { dirty |= StationControls.text(ctl, id, t == null ? "" : t); }

    @Override
    public final void update() {
        if (world.isRemote) return;
        if (term.tick(world, pos, wiredName())) { dirty = true; onPowerChange(term.powered()); }
        age++;
        serverTick();
        if (ctl.get("lamptest") == 1 && world.getTotalWorldTime() - ctl.pressAt > 60) { ctl.set("lamptest", 0); dirty = true; }
        if (dirty) { dirty = false; controlsChanged(); }
    }

    protected abstract void serverTick();
    protected void onPowerChange(boolean on) {}

    /** a lamp test push lights every lamp for 3 s */
    protected String lampTest() { ctl.set("lamptest", 1); return "§7Lamp test"; }

    /** the Signal Wrench: picked block -> this control (null = the wrench does nothing here) */
    public String link(String control, BlockPos pick, EntityPlayer who) { return null; }

    protected String who(EntityPlayer p) { return p == null ? "console" : p.getName(); }

    // ---- save ---------------------------------------------------------------------------------------------------------
    protected abstract void save(NBTTagCompound t);
    protected abstract void load(NBTTagCompound t);

    @Override public NBTTagCompound writeToNBT(NBTTagCompound t) { super.writeToNBT(t); ctl.write(t); term.write(t); save(t); return t; }
    @Override public void readFromNBT(NBTTagCompound t) { super.readFromNBT(t); ctl.read(t); term.read(t); if (!t.hasKey("term")) term.legacy = false; load(t); }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager n, SPacketUpdateTileEntity p) { readFromNBT(p.getNbtCompound()); }
}
