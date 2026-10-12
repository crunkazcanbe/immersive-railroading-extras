package com.dogpound.railmap.factory.block;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import java.util.EnumMap;
import java.util.Map;

/**
 * A length of pipe (or a gate valve / flow meter in the line). Holds 1,000 mB; shares it with lower-filled pipes next to
 * it and pushes it into any other mod's tank, pipe or machine that will take it - never straight back the way it came.
 */
public class TilePipe extends TileEntity implements ITickable {
    public static final int CAP = 1000, RATE = 250;
    public final FluidTank tank = new FluidTank(CAP);
    public boolean open = true;
    public int flow;                 // flow meter: mB/t over the last second
    public long total;               // flow meter: litres through it
    private int movedThisSec;
    private final int[] fromAge = new int[6];
    private final Map<EnumFacing, IFluidHandler> handlers = new EnumMap<>(EnumFacing.class);

    public BlockPipe.Kind kind() {
        IBlockState s = world == null ? null : world.getBlockState(pos);
        return s != null && s.getBlock() instanceof BlockPipe b ? b.kind : BlockPipe.Kind.PIPE;
    }

    /** a shut gate valve (or one held shut by a redstone signal on its actuator) passes nothing */
    public boolean passes() { return kind() != BlockPipe.Kind.VALVE || open && !world.isBlockPowered(pos); }

    private IFluidHandler handler(EnumFacing side) {
        EnumFacing key = side == null ? EnumFacing.UP : side;
        return handlers.computeIfAbsent(key, k -> new IFluidHandler() {
            @Override public IFluidTankProperties[] getTankProperties() { return tank.getTankProperties(); }
            @Override public int fill(FluidStack r, boolean doFill) {
                if (!passes()) return 0;
                int n = tank.fill(r, doFill);
                if (doFill && n > 0 && side != null) fromAge[side.ordinal()] = 10;
                return n;
            }
            @Override public FluidStack drain(FluidStack r, boolean doDrain) { return passes() ? tank.drain(r, doDrain) : null; }
            @Override public FluidStack drain(int max, boolean doDrain) { return passes() ? tank.drain(max, doDrain) : null; }
        });
    }

    @Override public boolean hasCapability(Capability<?> cap, EnumFacing side) { return cap == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY || super.hasCapability(cap, side); }
    @Override @SuppressWarnings("unchecked")
    public <T> T getCapability(Capability<T> cap, EnumFacing side) {
        if (cap == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) return (T) handler(side);
        return super.getCapability(cap, side);
    }

    @Override
    public void update() {
        if (world.isRemote) return;
        for (int i = 0; i < 6; i++) if (fromAge[i] > 0) fromAge[i]--;
        if (tank.getFluidAmount() > 0 && passes()) {
            int start = (int) (world.getTotalWorldTime() % 6);
            for (int k = 0; k < 6 && tank.getFluidAmount() > 0; k++) {
                EnumFacing f = EnumFacing.byIndex((start + k) % 6);
                if (fromAge[f.ordinal()] > 0) continue;
                TileEntity t = world.getTileEntity(pos.offset(f));
                if (t == null || !t.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, f.getOpposite())) continue;
                int give;
                if (t instanceof TilePipe p) {                                   // pipes even out, high to low
                    if (!p.passes()) continue;
                    int diff = tank.getFluidAmount() - p.tank.getFluidAmount();
                    if (diff <= 1) continue;
                    give = Math.min(RATE, (diff + 1) / 2);
                } else give = RATE;                                               // anything else: push into it
                IFluidHandler h = t.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, f.getOpposite());
                if (h == null) continue;
                FluidStack offer = tank.drain(give, false);
                if (offer == null) continue;
                int n = h.fill(offer, true);
                if (n > 0) { tank.drain(n, true); movedThisSec += n; markDirty(); }
            }
        }
        if (world.getTotalWorldTime() % 20 == (pos.hashCode() & 15)) {
            int f = movedThisSec / 20;
            total += movedThisSec; movedThisSec = 0;
            if (f != flow) {
                flow = f;
                if (kind() == BlockPipe.Kind.METER) { sync(); world.updateComparatorOutputLevel(pos, getBlockType()); }
            }
        }
    }

    public void sync() { IBlockState s = world.getBlockState(pos); world.notifyBlockUpdate(pos, s, s, 3); markDirty(); }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        t.setTag("tank", tank.writeToNBT(new NBTTagCompound()));
        t.setBoolean("open", open); t.setInteger("flow", flow); t.setLong("total", total);
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        tank.readFromNBT(t.getCompoundTag("tank"));
        open = !t.hasKey("open") || t.getBoolean("open"); flow = t.getInteger("flow"); total = t.getLong("total");
    }

    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager n, SPacketUpdateTileEntity p) {
        boolean was = open;
        readFromNBT(p.getNbtCompound());
        if (was != open) world.markBlockRangeForRenderUpdate(pos, pos);
    }
    @Override public boolean shouldRefresh(net.minecraft.world.World w, BlockPos p, IBlockState o, IBlockState n) { return o.getBlock() != n.getBlock(); }
}
