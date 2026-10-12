package com.dogpound.railmap.wire;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

/** Radio Transmitter / Receiver: carries all 16 wire colours across the world on a channel (1-32). */
public class TileRadio extends TileEntity implements net.minecraft.util.ITickable {
    public int channel = 1;
    private boolean registered;
    /** its 12 V DC feed: 0 none, 1 powered, 2 burnt out (wrong voltage) - only a powered radio sends or receives */
    public int pw = 0;
    public boolean burnt;
    public String supply = "";

    public boolean working() { return pw == 1; }

    @Override
    public void update() {
        if (world.isRemote || world.getTotalWorldTime() % 20 != (pos.hashCode() & 15)) return;
        int before = pw;
        WireNet.Supply sup = burnt ? null : WireNet.power(world, pos);
        if (sup != null && sup.state == 2) { burnt = true; WireNet.burnOut(world, pos, receiver() ? "Radio Receiver" : "Radio Transmitter", sup.what); }
        pw = burnt ? 2 : sup.state == 0 ? 0 : 1;
        supply = burnt ? "burnt out" : sup.what;
        if (pw != before) { markDirty(); IBlockState s = world.getBlockState(pos); world.notifyBlockUpdate(pos, s, s, 3); }
    }

    public String repair() {
        if (!burnt) return null;
        burnt = false; pw = 0; markDirty();
        IBlockState s = world.getBlockState(pos); world.notifyBlockUpdate(pos, s, s, 3);
        return "§aRepaired - new radio board in";
    }

    public boolean receiver() { return world != null && world.getBlockState(pos).getBlock() instanceof BlockRadio b && b.receiver; }

    @Override
    public void onLoad() {
        if (world != null && !world.isRemote && !receiver()) { WireNet.txAdd(world, channel, pos); registered = true; }
    }

    public void setChannel(int c) {
        if (registered) WireNet.txRemove(world, channel, pos);
        channel = c;
        if (registered) WireNet.txAdd(world, channel, pos);
        markDirty();
        IBlockState s = world.getBlockState(pos); world.notifyBlockUpdate(pos, s, s, 3);
    }

    @Override public void invalidate() { super.invalidate(); if (registered) { WireNet.txRemove(world, channel, pos); registered = false; } }
    @Override public void onChunkUnload() { if (registered) { WireNet.txRemove(world, channel, pos); registered = false; } }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound t) { super.writeToNBT(t); t.setInteger("channel", channel); t.setInteger("pw", pw); t.setBoolean("burnt", burnt); return t; }
    @Override public void readFromNBT(NBTTagCompound t) { super.readFromNBT(t); channel = Math.max(1, t.getInteger("channel")); pw = t.getInteger("pw"); burnt = t.getBoolean("burnt"); }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager n, SPacketUpdateTileEntity p) { readFromNBT(p.getNbtCompound()); }
    @Override public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() { return new net.minecraft.util.math.AxisAlignedBB(pos).expand(0, 1.2, 0); }
}
