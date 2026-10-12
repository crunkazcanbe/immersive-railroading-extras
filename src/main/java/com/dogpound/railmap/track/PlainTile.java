package com.dogpound.railmap.track;

import com.dogpound.railmap.block.StationControls;
import com.dogpound.railmap.controls.ControlState;
import com.dogpound.railmap.controls.IControlled;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;

/**
 * Non-electric track furniture with live lettering (tunnel portals, distance markers, survey pegs): working controls
 * and displays, save + client sync, no 12 V terminal (so it never shows as a dead box on the WIRED page).
 */
public abstract class PlainTile extends TileEntity implements ITickable, IControlled {
    protected final ControlState ctl = new ControlState();
    protected int age;
    private boolean dirty;

    @Override public ControlState controls() { return ctl; }
    @Override public void controlsChanged() { markDirty(); if (world != null && !world.isRemote) { IBlockState s = world.getBlockState(pos); world.notifyBlockUpdate(pos, s, s, 3); } }

    protected void put(String id, int v) { dirty |= StationControls.put(ctl, id, v); }
    protected void text(String id, String t) { dirty |= StationControls.text(ctl, id, t == null ? "" : t); }

    @Override public void onLoad() { super.onLoad(); if (world != null && !world.isRemote) TrackHooks.add(this); }
    @Override public void invalidate() { super.invalidate(); if (world != null && !world.isRemote) TrackHooks.remove(this); }
    @Override public void onChunkUnload() { super.onChunkUnload(); if (world != null && !world.isRemote) TrackHooks.remove(this); }

    @Override
    public final void update() {
        if (world.isRemote) return;
        age++;
        serverTick();
        if (dirty) { dirty = false; controlsChanged(); }
    }

    protected abstract void serverTick();
    protected void save(NBTTagCompound t) {}
    protected void load(NBTTagCompound t) {}

    @Override public NBTTagCompound writeToNBT(NBTTagCompound t) { super.writeToNBT(t); ctl.write(t); save(t); return t; }
    @Override public void readFromNBT(NBTTagCompound t) { super.readFromNBT(t); ctl.read(t); load(t); }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager n, SPacketUpdateTileEntity p) { readFromNBT(p.getNbtCompound()); }
}
