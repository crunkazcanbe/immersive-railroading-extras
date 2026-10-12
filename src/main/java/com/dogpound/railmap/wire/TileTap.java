package com.dogpound.railmap.wire;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** A Grid Tap: the instrument transformer clipped onto a grid machine or cable; puts that point on one wire colour. */
public class TileTap extends TileEntity {
    public int channel;

    public EnumFacing facing() {
        IBlockState s = world == null ? null : world.getBlockState(pos);
        return s != null && s.getBlock() instanceof BlockTap ? s.getValue(BlockTap.FACING) : EnumFacing.UP;
    }
    /** the grid block it is clamped onto */
    public BlockPos target() { return pos.offset(facing().getOpposite()); }

    public void setChannel(int c) {
        channel = c & 15; markDirty();
        if (world != null) { IBlockState s = world.getBlockState(pos); world.notifyBlockUpdate(pos, s, s, 3); }
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound t) { super.writeToNBT(t); t.setInteger("channel", channel); return t; }
    @Override public void readFromNBT(NBTTagCompound t) { super.readFromNBT(t); channel = t.getInteger("channel"); }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager n, SPacketUpdateTileEntity p) { readFromNBT(p.getNbtCompound()); }
}
