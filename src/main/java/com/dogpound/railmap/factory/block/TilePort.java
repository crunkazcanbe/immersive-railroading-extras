package com.dogpound.railmap.factory.block;

import com.dogpound.railmap.factory.machine.TileMachine;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

/** A port hands everything straight to (or from) its machine's controller; an output hatch also pushes its products out. */
public class TilePort extends TileEntity implements ITickable, com.dogpound.railmap.industry.IndustryLink.Part {
    private BlockPos controller;

    /** an IR Extras loader / terminal working off this hatch bills the plant it belongs to */
    @Override public BlockPos endpoint() { return controller; }

    public void setController(BlockPos c) { controller = c == null ? null : c.toImmutable(); markDirty(); }

    public TileMachine machine() {
        if (controller == null || world == null) return null;
        TileEntity t = world.getTileEntity(controller);
        return t instanceof TileMachine m && m.formed() ? m : null;
    }

    private BlockPort.Kind kind() {
        IBlockState s = world == null ? null : world.getBlockState(pos);
        return s != null && s.getBlock() instanceof BlockPort b ? b.kind : null;
    }

    @Override
    public boolean hasCapability(Capability<?> cap, EnumFacing side) { return getCapability(cap, side) != null || super.hasCapability(cap, side); }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCapability(Capability<T> cap, EnumFacing side) {
        TileMachine m = machine();
        BlockPort.Kind k = kind();
        if (m != null && k != null) {
            if (cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY && k == BlockPort.Kind.ITEM_IN) return (T) m.inputView();
            if (cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY && k == BlockPort.Kind.ITEM_OUT) return (T) m.outputView();
            if (cap == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY && k == BlockPort.Kind.FLUID_IN) return (T) m.fluidIn();
            if (cap == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY && k == BlockPort.Kind.FLUID_OUT) return (T) m.fluidOut();
            if (cap == CapabilityEnergy.ENERGY && k == BlockPort.Kind.DRIVE) return (T) m.energy();
        }
        return super.getCapability(cap, side);
    }

    /** output hatches push their products into whatever they touch (outside the machine) once a second */
    @Override
    public void update() {
        if (world.isRemote) return;
        if (kind() == BlockPort.Kind.FLUID_IN && world.getTotalWorldTime() % 5 == 0) suction();
        if (kind() == BlockPort.Kind.FLUID_OUT && world.getTotalWorldTime() % 5 == 0) pushWater();
        if (world.getTotalWorldTime() % 5 != 0 || kind() != BlockPort.Kind.ITEM_OUT) return;
        boolean slow = world.getTotalWorldTime() % 20 == (pos.hashCode() & 15) / 5 * 5;
        TileMachine m = machine();
        if (m == null) return;
        IItemHandler out = m.outputView();
        for (EnumFacing f : EnumFacing.values()) {
            BlockPos n = pos.offset(f);
            if (m.isMember(n) || n.equals(controller)) continue;
            TileEntity t = world.getTileEntity(n);
            boolean belt = t instanceof com.dogpound.railmap.factory.conveyor.TileConveyor;   // a belt: a steady stream of small batches
            if (!belt && !slow) continue;
            if (t == null || !t.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, f.getOpposite())) continue;
            IItemHandler dst = t.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, f.getOpposite());
            if (dst == null) continue;
            for (int i = 0; i < out.getSlots(); i++) {
                if (out.getStackInSlot(i).isEmpty()) continue;
                net.minecraft.item.ItemStack take = out.extractItem(i, belt ? 4 : 64, true);
                net.minecraft.item.ItemStack left = ItemHandlerHelper.insertItemStacked(dst, take, false);
                out.extractItem(i, take.getCount() - left.getCount(), false);
                if (belt) break;                                                   // one batch per belt per go
            }
        }
    }

    /** pumps and water towers push their water out of the Fluid Output Hatch into whatever touches it: any mod's pipes or tanks */
    private void pushWater() {
        TileMachine m = machine();
        if (m == null || !m.waterWorks() && !m.makesFluid()) return;
        net.minecraftforge.fluids.FluidStack offer = m.offer();
        if (offer == null || offer.amount <= 0) return;
        int left = offer.amount;
        for (EnumFacing f : EnumFacing.values()) {
            if (left <= 0) return;
            BlockPos n = pos.offset(f);
            if (m.isMember(n) || n.equals(controller)) continue;
            TileEntity t = world.getTileEntity(n);
            if (t == null || !t.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, f.getOpposite())) continue;
            net.minecraftforge.fluids.capability.IFluidHandler dst = t.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, f.getOpposite());
            if (dst == null) continue;
            net.minecraftforge.fluids.FluidStack give = offer.copy(); give.amount = left;
            int took = dst.fill(give, true);
            m.took(took);
            left -= took;
        }
    }

    /** a water inlet with water flowing past it (a source block touching it) sucks it in like a pump intake: 250 mB a second */
    private void suction() {
        TileMachine m = machine();
        if (m == null || m.water().getFluidAmount() >= m.water().getCapacity()) return;
        for (EnumFacing f : EnumFacing.values()) {
            net.minecraft.block.state.IBlockState s = world.getBlockState(pos.offset(f));
            if (s.getMaterial() == net.minecraft.block.material.Material.WATER && s.getPropertyKeys().contains(net.minecraft.block.BlockLiquid.LEVEL)
                    && s.getValue(net.minecraft.block.BlockLiquid.LEVEL) == 0) {
                m.fluidIn().fill(new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER, 63), true);
                return;
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        if (controller != null) t.setLong("controller", controller.toLong());
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        controller = t.hasKey("controller") ? BlockPos.fromLong(t.getLong("controller")) : null;
    }
}
