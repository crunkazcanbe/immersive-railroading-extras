package com.dogpound.railmap.wire;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;

/** One block of Instrument Wire: up to 16 colours laid side by side on the floor, or a Bundled Cable (all 16). */
public class TileWire extends TileEntity {
    private int mask;

    public boolean bundled() { return world != null && world.getBlockState(pos).getBlock() instanceof BlockWire b && b.bundled; }
    public int mask() { return bundled() ? 0xFFFF : mask; }
    public boolean has(int c) { return c >= 0 && c <= WireNet.POWER && (mask() >> c & 1) != 0; }
    public int count() { return Integer.bitCount(mask()); }

    public boolean add(int c) {
        if (has(c)) return false;
        mask |= 1 << c; sync(); return true;
    }

    /** pull the last-laid colour out; -1 if none left */
    public int removeLast() {
        if (mask == 0) return -1;
        int c = 31 - Integer.numberOfLeadingZeros(mask);
        mask &= ~(1 << c); sync();
        return c;
    }
    public int rawMask() { return mask; }

    void sync() {
        markDirty();
        if (world != null) { IBlockState s = world.getBlockState(pos); world.notifyBlockUpdate(pos, s, s, 3); world.markBlockRangeForRenderUpdate(pos.add(-1, -1, -1), pos.add(1, 1, 1)); }
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound t) { super.writeToNBT(t); t.setInteger("mask", mask); return t; }
    @Override public void readFromNBT(NBTTagCompound t) { super.readFromNBT(t); mask = t.getInteger("mask"); }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager n, SPacketUpdateTileEntity p) { readFromNBT(p.getNbtCompound()); }
    @Override public AxisAlignedBB getRenderBoundingBox() { return new AxisAlignedBB(pos).grow(0.1); }
}
