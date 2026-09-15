package com.dogpound.railmap.signal;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Lineside scenery (whistle post, milepost, crossbuck, switch stand, derail, bumper) -- and the
 * speed sign, which extends it. Holds only the size, so the piece can be scaled to sit right next
 * to full-size Immersive Railroading stock; it is drawn by {@code LinesideRenderer}.
 */
public class TileLineside extends TileEntity implements IScalable {
    private float scale = 1f;

    @Override
    public float scale() {
        return scale;
    }

    @Override
    public void setScale(float s) {
        scale = IScalable.clamp(s);
        markDirty();
        if (world != null && !world.isRemote) {
            IBlockState st = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, st, st, 3);
        }
    }

    public EnumFacing facing() {
        IBlockState s = world.getBlockState(pos);
        return s.getPropertyKeys().contains(BlockLineside.FACING) ? s.getValue(BlockLineside.FACING) : EnumFacing.NORTH;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        if (scale != 1f) t.setFloat("scale", scale);
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        scale = t.hasKey("scale") ? IScalable.clamp(t.getFloat("scale")) : 1f;
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

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        double g = Math.max(0, scale - 1);
        return new AxisAlignedBB(pos).grow(g, 0, g).expand(0, g, 0);
    }
}
