package com.dogpound.railmap.signal;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The number on a speed limit sign. American signs are posted in miles per hour, so that is
 * what the sign shows and what you set; trains are held to it in km/h underneath.
 * <p>
 * A sign governs track from where it stands onward, for trains that can read it — the
 * sign faces the oncoming train, like a signal.
 */
public class TileSpeedSign extends TileEntity {
    /** The limits a right-click steps through, mph. */
    private static final int[] STEPS = { 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 70, 79, 90 };

    private int mph = 30;

    public int mph() {
        return mph;
    }

    public double kmh() {
        return mph * 1.609344;
    }

    public void step(boolean down) {
        int i = 0;
        for (int k = 0; k < STEPS.length; k++) if (STEPS[k] == mph) i = k;
        i = down ? (i + STEPS.length - 1) % STEPS.length : (i + 1) % STEPS.length;
        mph = STEPS[i];
        markDirty();
        if (world != null && !world.isRemote) {
            IBlockState s = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, s, s, 3);
        }
    }

    public EnumFacing facing() {
        IBlockState s = world.getBlockState(pos);
        return s.getPropertyKeys().contains(BlockLineside.FACING) ? s.getValue(BlockLineside.FACING) : EnumFacing.NORTH;
    }

    @Override
    public void onLoad() {
        if (world != null && !world.isRemote) SignalRegistry.addSpeedSign(world.provider.getDimension(), this);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (world != null && !world.isRemote) SignalRegistry.removeSpeedSign(world.provider.getDimension(), this);
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        if (world != null && !world.isRemote) SignalRegistry.removeSpeedSign(world.provider.getDimension(), this);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        t.setInteger("mph", mph);
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        mph = t.hasKey("mph") ? t.getInteger("mph") : 30;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }

    @Override
    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }
}
