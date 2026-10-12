package com.dogpound.railmap.wire;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

/** A Wire Chase Wall: every wire colour and the 12 V pair run hidden inside it; it can look like any block. */
public class TileWireChase extends TileEntity {
    public String camo = "";
    public int camoMeta;
    /** faces with a hole drilled through (bit per EnumFacing index): wire only goes in / out where there's a hole */
    public int holes;

    public boolean hole(net.minecraft.util.EnumFacing f) { return (holes >> f.getIndex() & 1) != 0; }
    public void setHole(net.minecraft.util.EnumFacing f, boolean on) { holes = on ? holes | 1 << f.getIndex() : holes & ~(1 << f.getIndex()); sync(); }

    /** can wire pass between this chase and the thing on face f? Another chase (inside the wall) or a gauge / panel /
     *  radio mounted over the face always can (its case covers the hole); anything else needs a drilled hole */
    public boolean opensTo(net.minecraft.util.EnumFacing f) {
        if (hole(f) || world == null) return true;
        TileEntity t = world.getTileEntity(pos.offset(f));
        return t instanceof TileWireChase || t instanceof com.dogpound.railmap.grid.TileInstrument || t instanceof TileGaugePanel || t instanceof TileRadio
                || t instanceof com.dogpound.railmap.block.TileDataLink;
    }

    public IBlockState camoState() {
        if (camo.isEmpty()) return null;
        Block b = Block.REGISTRY.getObject(new ResourceLocation(camo));
        if (b == null || b == net.minecraft.init.Blocks.AIR) return null;
        try { return b.getStateFromMeta(camoMeta); } catch (Exception e) { return b.getDefaultState(); }
    }

    /** keep this tile (and its disguise / holes) when the block flips between plain and disguised - Forge would replace it */
    @Override
    public boolean shouldRefresh(net.minecraft.world.World w, net.minecraft.util.math.BlockPos p, IBlockState oldState, IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }

    public void sync() {
        markDirty();
        if (world != null) { IBlockState s = world.getBlockState(pos); world.notifyBlockUpdate(pos, s, s, 3); world.markBlockRangeForRenderUpdate(pos, pos); }
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound t) { super.writeToNBT(t); t.setString("camo", camo); t.setInteger("camoMeta", camoMeta); t.setInteger("holes", holes); return t; }
    @Override public void readFromNBT(NBTTagCompound t) { super.readFromNBT(t); camo = t.getString("camo"); camoMeta = t.getInteger("camoMeta"); holes = t.getInteger("holes"); }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager n, SPacketUpdateTileEntity p) { readFromNBT(p.getNbtCompound()); }
}
