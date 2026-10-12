package com.dogpound.railmap.depot;

import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.entity.LocomotiveDiesel;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.alarm.Recorder;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;

import java.util.Locale;

/**
 * Fuelling and Sanding Point: a 40 000 L bunded fuel tank with a delivery pump and metered nozzle, and a sand hopper
 * with a sand-delivery hose. The tank fills from pipes (any side) or buckets / cans (FILL on the tank); PUMP fuels the
 * diesel locomotive standing at it (within 6 blocks) at 500 L/s on mains until it is full or you STOP, and the meter
 * counts the litres. SAND blows sand from the hopper into the locomotive's sandbox. Locos that won't take this fuel
 * are refused by their own tank.
 */
public class TileFuelPoint extends DepotTile implements com.dogpound.railmap.industry.IndustryLink.Endpoint {
    private final FluidTank tank = new FluidTank(40_000);
    private boolean pumping;
    private int delivered, total;

    public TileFuelPoint() { super(new String[]{"hopper"}, new String[]{"Sand hopper"}, new int[]{9}); tank.setTileEntity(this); }

    @Override public String wiredName() { return "Fuel and sand point"; }
    @Override protected boolean usesMains() { return true; }
    @Override protected long load() { return pumping ? 20 : 2; }
    @Override public String gearState() { return !powered() ? "NO 12 V" : String.format(Locale.ROOT, "tank %d L %s · %s", tank.getFluidAmount(), tank.getFluid() == null ? "" : tank.getFluid().getLocalizedName(), pumping ? "PUMPING" : "idle"); }

    LocomotiveDiesel loco() {
        LocomotiveDiesel best = null;
        double bd = 36;
        for (LocomotiveDiesel l : cam72cam.mod.world.World.get(world).getEntities(LocomotiveDiesel.class)) {
            Vec3d p = l.getPosition();
            double d = pos.distanceSqToCenter(p.x, p.y, p.z);
            if (!l.isDead() && d < bd) { bd = d; best = l; }
        }
        return best;
    }

    Locomotive anyLoco() {
        Locomotive best = null;
        double bd = 36;
        for (Locomotive l : cam72cam.mod.world.World.get(world).getEntities(Locomotive.class)) {
            Vec3d p = l.getPosition();
            double d = pos.distanceSqToCenter(p.x, p.y, p.z);
            if (!l.isDead() && d < bd) { bd = d; best = l; }
        }
        return best;
    }

    @Override
    protected String press(String id, EntityPlayer who) {
        switch (id) {
            case "pump" -> {
                if (pumping) return "§7Already pumping";
                if (ctl.get("m_estop") == 1) return "§cE-STOP is in";
                if (!mainsOk()) return "§cNo mains for the pump (" + mains.what + ")";
                if (tank.getFluidAmount() <= 0) return "§cThe fuel tank is empty";
                LocomotiveDiesel l = loco();
                if (l == null) return "§cNo diesel locomotive at the fuelling point";
                cam72cam.mod.fluid.Fluid fl = cam72cam.mod.fluid.Fluid.getFluid(tank.getFluid().getFluid());
                if (fl == null || !l.theTank.allows(fl)) return "§c" + tank.getFluid().getLocalizedName() + " is not a fuel this locomotive takes";
                pumping = true; delivered = 0;
                return "§aPumping into " + FleetData.get(world).unit(l.getUUID()).name;
            }
            case "stop" -> { pumping = false; return "§eNozzle off - " + delivered + " L delivered"; }
            case "sand" -> {
                Locomotive l = anyLoco();
                if (l == null) return "§cNo locomotive at the sand hose";
                if (l.prideSandLevel() < 0) return "§cThat locomotive has no sandbox";
                int have = 0;
                for (int i = 0; i < store(0).getSizeInventory(); i++) if (store(0).getStackInSlot(i).getItem() == Item.getItemFromBlock(Blocks.SAND)) have += store(0).getStackInSlot(i).getCount();
                if (have == 0) return "§cThe sand hopper is empty";
                cam72cam.mod.item.ItemStack rest = l.cargoItems.insert(2, new cam72cam.mod.item.ItemStack(new ItemStack(Blocks.SAND, have)), false);
                int put = have - rest.internal.getCount();
                int left = put;
                for (int i = 0; i < store(0).getSizeInventory() && left > 0; i++) {
                    ItemStack s = store(0).getStackInSlot(i);
                    if (s.getItem() != Item.getItemFromBlock(Blocks.SAND)) continue;
                    int t = Math.min(left, s.getCount()); s.shrink(t); left -= t;
                    if (s.isEmpty()) store(0).setInventorySlotContents(i, ItemStack.EMPTY);
                }
                world.playSound(null, pos, SoundEvents.BLOCK_SAND_PLACE, SoundCategory.BLOCKS, 0.8f, 0.8f);
                return put == 0 ? "§7The sandbox is full" : "§aSanded: " + put + " sand into the sandbox";
            }
            case "fill" -> {
                if (who == null) return "§7Bring the fuel by hand or by pipe";
                ItemStack h = who.getHeldItemMainhand();
                var res = FluidUtil.tryEmptyContainer(h, tank, Integer.MAX_VALUE, who, true);
                if (!res.isSuccess()) return "§7Hold a bucket or can of fuel (or pipe it in)";
                who.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND, res.getResult());
                return "§aTank " + tank.getFluidAmount() + " L";
            }
            default -> { return null; }
        }
    }

    @Override
    protected String mechanical(String id, EntityPlayer who) {
        if (id.equals("m_estop")) { ctl.set(id, ctl.get(id) == 1 ? 0 : 1); if (ctl.get(id) == 1) pumping = false; return ctl.get(id) == 1 ? "§cE-STOP - pump off" : "§aE-stop released"; }
        return null;
    }

    // ---- her list #87: the fuel point orders diesel by tank car when its tank runs below a quarter ---------------------
    @Override public String endpointName() { return "Fuel point"; }
    @Override public java.util.List<ItemStack> makes() { return java.util.Collections.emptyList(); }
    @Override public boolean accepts(ItemStack s) { return false; }
    @Override public java.util.List<ItemStack> wants() { return java.util.Collections.emptyList(); }
    @Override public String endpointState() { return tank.getFluidAmount() < tank.getCapacity() / 4 ? "LOW FUEL" : "OK"; }

    @Override
    public java.util.List<FluidStack> fluidsWanted() {
        if (tank.getFluidAmount() >= tank.getCapacity() / 4) return java.util.Collections.emptyList();
        net.minecraftforge.fluids.Fluid f = tank.getFluid() != null ? tank.getFluid().getFluid() : net.minecraftforge.fluids.FluidRegistry.getFluid("diesel");
        return f == null ? java.util.Collections.emptyList() : java.util.Collections.singletonList(new FluidStack(f, tank.getCapacity() - tank.getFluidAmount()));
    }

    @Override public void onLoad() { com.dogpound.railmap.industry.IndustryLink.register(this); }
    @Override public void invalidate() { super.invalidate(); com.dogpound.railmap.industry.IndustryLink.forget(this); }

    @Override
    protected void work() {
        if (age % 20 != 0) return;
        if (age % 400 == 0) com.dogpound.railmap.industry.IndustryLink.register(this);
        LocomotiveDiesel l = loco();
        if (pumping) {
            if (l == null || !powered() || !mainsOk() || tank.getFluidAmount() <= 0 || ctl.get("m_estop") == 1) pumping = false;
            else {
                FluidStack out = tank.drain(500, false);
                cam72cam.mod.fluid.Fluid fl = out == null ? null : cam72cam.mod.fluid.Fluid.getFluid(out.getFluid());
                int got = fl == null ? 0 : l.theTank.fill(new cam72cam.mod.fluid.FluidStack(fl, out.amount), false);
                if (got > 0) { tank.drain(got, true); delivered += got; total += got; }
                if (got < 500 && got >= 0 && (out == null || got < out.amount)) {
                    pumping = false;
                    Recorder.event(world, "Fuel point: " + FleetData.get(world).unit(l.getUUID()).name + " fuelled, " + delivered + " L");
                    world.playSound(null, pos, com.dogpound.railmap.ModSounds.BEEP_OK, SoundCategory.BLOCKS, 0.5f, 1.0f);
                }
            }
        }
        put("pwr", powered()); put("mainsl", mains.ok()); put("pumpl", pumping); put("low", tank.getFluidAmount() < 4000);
        put("tankg", tank.getFluidAmount() * 100 / tank.getCapacity());
        put("locol", l != null);
        int sand = 0;
        for (int i = 0; i < store(0).getSizeInventory(); i++) if (store(0).getStackInSlot(i).getItem() == Item.getItemFromBlock(Blocks.SAND)) sand += store(0).getStackInSlot(i).getCount();
        put("sandg", sand * 100 / (9 * 64));
        Locomotive any = anyLoco();
        float sl = any == null ? -1 : any.prideSandLevel();
        put("boxg", sl < 0 ? 0 : Math.round(sl * 100));
        text("litres", String.valueOf(delivered));
        text("line1", l == null ? "NO LOCO" : cut(FleetData.get(world).unit(l.getUUID()).name.toUpperCase(Locale.ROOT), 16));
        text("line2", l == null ? "" : String.format(Locale.ROOT, "%d/%d L", l.getLiquidAmount(), l.getTankCapacity().MilliBuckets()));
    }

    @Override public boolean hasCapability(Capability<?> c, EnumFacing f) { return c == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY || super.hasCapability(c, f); }
    @SuppressWarnings("unchecked")
    @Override public <T> T getCapability(Capability<T> c, EnumFacing f) { return c == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY ? (T) tank : super.getCapability(c, f); }

    @Override protected void saveExtra(NBTTagCompound t) { t.setTag("tank", tank.writeToNBT(new NBTTagCompound())); t.setBoolean("pumping", pumping); t.setInteger("del", delivered); t.setInteger("total", total); }
    @Override protected void loadExtra(NBTTagCompound t) { tank.readFromNBT(t.getCompoundTag("tank")); pumping = t.getBoolean("pumping"); delivered = t.getInteger("del"); total = t.getInteger("total"); }
}
