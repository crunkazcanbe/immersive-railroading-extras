package com.dogpound.railmap.freight6;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.stn.StnUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Oil Storage Tank (batch 6 #27): a welded steel tank in its bund - shell courses, wind girder, roof with vents and
 * foam chambers, spiral stair, servo level gauge and the valve manifold. Real fluid storage (F6Config.tankLitres, any
 * one fluid): pipes fill it through the INLET valve and draw through the OUTLET valve (levers - shut, nothing passes),
 * buckets / cans via FILL. Level HIGH (90 %) and HIGH-HIGH (95 %, an overfill alarm; racks stop unloading into it).
 * A fire touching the tank is detected by its heat detectors (FIRE lamp) for the Fire and Gas Panel. Tanks are numbered
 * TANK 1-6 by the racks / panel near them (west to east). 12 V for the gauge and alarms; the valves are mechanical.
 */
public class TileOilTank extends com.dogpound.railmap.depot.DepotTile {
    final FluidTank tank = new FluidTank(F6Config.tankLitres);
    long inTotal, outTotal;
    private final IFluidHandler pipes = new Valved();

    public TileOilTank() { super(new String[]{"kit"}, new String[]{"Dip tape and sample kit"}, new int[]{9}); tank.setTileEntity(this); }

    @Override public String wiredName() { return "Oil storage tank"; }
    @Override public String gearState() { return !powered() ? "NO 12 V" : String.format(Locale.ROOT, "%s %d / %d L (%d%%)%s", product(), tank.getFluidAmount(), tank.getCapacity(), pct(), fire() ? " · FIRE" : ""); }

    public FluidTank tank() { return tank; }
    public int pct() { return (int) ((long) tank.getFluidAmount() * 100 / Math.max(1, tank.getCapacity())); }
    public String product() { return tank.getFluid() == null ? "EMPTY" : tank.getFluid().getLocalizedName(); }
    public boolean inletOpen() { return ctl.get("m_in") == 0; }      // levers stand OPEN (0) as placed
    public boolean outletOpen() { return ctl.get("m_out") == 0; }
    public boolean highHigh() { return pct() >= 95; }
    public void shutValves() { ctl.set("m_in", 1); ctl.set("m_out", 1); controlsChanged(); }

    /** fire within 3 blocks of the tank (its heat detectors) */
    public boolean fire() { return TankFarm.fireNear(world, pos, 3) != null; }

    @Override
    protected String press(String id, EntityPlayer who) {
        switch (id) {
            case "dip" -> { return String.format(Locale.ROOT, "§aDip: %s, %.2f m ullage of %.2f m - %d L", product(), (1 - pct() / 100.0) * 14.6, 14.6, tank.getFluidAmount()); }
            case "fill" -> {
                if (who == null) return "§7Bring it by hand or pipe it in";
                if (!inletOpen()) return "§cThe INLET valve is shut";
                ItemStack h = who.getHeldItemMainhand();
                var res = FluidUtil.tryEmptyContainer(h, tank, Integer.MAX_VALUE, who, true);
                if (!res.isSuccess()) return "§7Hold a bucket / can of fluid (or pipe it in)";
                who.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND, res.getResult());
                inTotal += 1000;
                return "§aTank " + tank.getFluidAmount() + " L";
            }
            case "reset" -> { AlarmManager.get(world).clear("oil:hh:" + pos.toLong()); return "§7Alarms reset"; }
            default -> { return null; }
        }
    }

    @Override
    protected String mechanical(String id, EntityPlayer who) {
        if (id.equals("m_in") || id.equals("m_out")) {
            ctl.set(id, ctl.get(id) == 1 ? 0 : 1);
            world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ctl.lever"), net.minecraft.util.SoundCategory.BLOCKS, 0.8f, 0.6f);
            return (id.equals("m_in") ? "§eInlet valve " : "§eOutlet valve ") + (ctl.get(id) == 0 ? "OPEN" : "SHUT");
        }
        return null;
    }

    @Override
    protected void work() {
        if (age % 20 != 0) return;
        if (highHigh()) AlarmManager.get(world).raise(world, "oil:hh:" + pos.toLong(), AlarmManager.Cat.FREIGHT, AlarmManager.Sev.MAJOR,
                "Oil tank at " + pos.getX() + "," + pos.getZ() + ": HIGH-HIGH LEVEL " + pct() + " % - overfill", pos, false);
        else if (pct() < 93) AlarmManager.get(world).clear("oil:hh:" + pos.toLong());
        int n = TankFarm.tanks(world, pos).indexOf(this);
        put("pwr", powered()); put("high", pct() >= 90); put("hh", highHigh()); put("low", tank.getFluidAmount() > 0 && pct() <= 5);
        put("inl", inletOpen()); put("outl", outletOpen()); put("firel", fire());
        put("levelg", pct());
        text("tankno", n < 0 ? "--" : String.valueOf(n + 1));
        text("litres", String.valueOf(tank.getFluidAmount() / 1000));
        text("line1", cut(product().toUpperCase(Locale.ROOT), 16));
        text("line2", String.format(Locale.ROOT, "%d%% %.2fM", pct(), pct() / 100.0 * 14.6));
    }

    /** the tank's pipe connections: fill through the inlet valve, draw through the outlet valve */
    private final class Valved implements IFluidHandler {
        @Override public IFluidTankProperties[] getTankProperties() { return tank.getTankProperties(); }
        @Override public int fill(FluidStack r, boolean doFill) { if (!inletOpen()) return 0; int n = tank.fill(r, doFill); if (doFill) inTotal += n; return n; }
        @Override public FluidStack drain(FluidStack r, boolean doDrain) { if (!outletOpen()) return null; FluidStack d = tank.drain(r, doDrain); if (doDrain && d != null) outTotal += d.amount; return d; }
        @Override public FluidStack drain(int max, boolean doDrain) { if (!outletOpen()) return null; FluidStack d = tank.drain(max, doDrain); if (doDrain && d != null) outTotal += d.amount; return d; }
    }

    @Override public boolean hasCapability(Capability<?> c, EnumFacing f) { return c == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY || super.hasCapability(c, f); }
    @SuppressWarnings("unchecked")
    @Override public <T> T getCapability(Capability<T> c, EnumFacing f) { return c == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY ? (T) pipes : super.getCapability(c, f); }

    @Override protected void saveExtra(NBTTagCompound t) { t.setTag("tank", tank.writeToNBT(new NBTTagCompound())); t.setLong("in", inTotal); t.setLong("out", outTotal); }
    @Override protected void loadExtra(NBTTagCompound t) { tank.setCapacity(F6Config.tankLitres); tank.readFromNBT(t.getCompoundTag("tank")); inTotal = t.getLong("in"); outTotal = t.getLong("out"); }

    @Override public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() { return new net.minecraft.util.math.AxisAlignedBB(pos).grow(2, 3, 2); }

    /** the tank farm's shared lookups */
    public static final class TankFarm {
        private TankFarm() {}

        public static List<TileOilTank> tanks(net.minecraft.world.World w, BlockPos at) {
            List<TileOilTank> l = StnUtil.near(w, TileOilTank.class, at, 32);
            l.sort(Comparator.comparingInt((TileOilTank s) -> s.getPos().getX()).thenComparingInt(s -> s.getPos().getZ()).thenComparingInt(s -> s.getPos().getY()));
            return l.size() > 6 ? l.subList(0, 6) : l;
        }

        /** a fire block within r of p (null = none) */
        public static BlockPos fireNear(net.minecraft.world.World w, BlockPos p, int r) {
            for (BlockPos q : BlockPos.getAllInBoxMutable(p.add(-r, -1, -r), p.add(r, 3, r)))
                if (w.isBlockLoaded(q) && w.getBlockState(q).getBlock() == Blocks.FIRE) return q.toImmutable();
            return null;
        }
    }
}
