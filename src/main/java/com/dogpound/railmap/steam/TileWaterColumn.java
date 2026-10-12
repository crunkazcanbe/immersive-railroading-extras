package com.dogpound.railmap.steam;

import cam72cam.immersiverailroading.entity.FreightTank;
import com.dogpound.railmap.alarm.Recorder;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;

import java.util.Locale;

/**
 * Water Column / Water Crane (batch 5 #2): a cast-iron column with a swinging delivery arm and leather bag, fed from its
 * 30 000 L elevated tank. The tank fills by pipe (any side) or from water in the world through the electric feed PUMP
 * (mains + 12 V; FLOAT AUTO keeps it topped up). Swing the ARM out over the tender or tank engine standing at the column
 * and open the VALVE (a hand wheel - gravity, works with no power at all): 250 L/s until its tank is full, then shut
 * it. Water with nowhere to go spills. The TREATMENT dosing pot (bone meal = phosphate dosing) softens the water: each
 * 1 000 L treated takes one, and the hardness that goes aboard drives the boiler's scale (SteamTicker). The meter, the
 * hardness and level gauges are 12 V instruments.
 */
public class TileWaterColumn extends com.dogpound.railmap.depot.DepotTile {
    static final int CAP = 30_000, FLOW = 250, PUMP = 100;
    private final FluidTank tank = new FluidTank(CAP) { @Override public boolean canFillFluidType(FluidStack f) { return f != null && f.getFluid() == FluidRegistry.WATER; } };
    private int delivered, total, doseCredit;
    private boolean pumping, spilling;

    public TileWaterColumn() { super(new String[]{"dose"}, new String[]{"Treatment dosing pot (bone meal)"}, new int[]{9}); tank.setTileEntity(this); }

    @Override public String wiredName() { return "Water column"; }
    @Override protected boolean usesMains() { return true; }
    @Override protected long load() { return pumping ? 75 : 1; }
    @Override public String gearState() { return !powered() ? "NO 12 V (valve still works by hand)" : String.format(Locale.ROOT, "tank %d L · %d ppm · %s", tank.getFluidAmount(), ppm(), valveOpen() ? "VALVE OPEN" : "valve shut"); }

    boolean armOut() { return ctl.get("m_arm") == 1; }
    boolean valveOpen() { return ctl.get("m_valve") == 1; }
    boolean treating() { return Math.floorMod(ctl.get("s_treat"), 2) == 1 && doses() > 0; }

    int doses() { int n = 0; for (int i = 0; i < 9; i++) { ItemStack s = store(0).getStackInSlot(i); if (s.getItem() == Items.DYE && s.getMetadata() == 15) n += s.getCount(); } return n; }
    void useDose() {
        for (int i = 0; i < 9; i++) { ItemStack s = store(0).getStackInSlot(i); if (s.getItem() == Items.DYE && s.getMetadata() == 15) { s.shrink(1); if (s.isEmpty()) store(0).setInventorySlotContents(i, ItemStack.EMPTY); return; } }
    }

    /** raw hardness of the local water (ppm CaCO3): dry, hot country = hard; wet, cold = soft */
    int rawPpm() {
        Biome b = world.getBiome(pos);
        double rain = b.getRainfall(), temp = b.getDefaultTemperature();
        return (int) Math.max(40, Math.min(450, 60 + 330 * (1 - rain) * (temp > 1.5 ? 1.2 : temp < 0.3 ? 0.6 : 1)));
    }
    int ppm() { return treating() ? Math.min(rawPpm(), 35) : rawPpm(); }

    FreightTank stock() { return Stock.nearest(world, pos, 5, FreightTank.class, s -> Stock.steamStock(s) || s.theTank.allows(cam72cam.mod.fluid.Fluid.WATER)); }

    @Override
    protected String press(String id, EntityPlayer who) {
        switch (id) {
            case "pump" -> {
                if (ctl.get("m_estop") == 1) return "§cE-STOP is in";
                if (!mainsOk()) return "§cNo mains for the feed pump (" + mains.what + ")";
                if (source() == null) return "§cThe pump has no water to draw - it needs water beside or under the column";
                pumping = !pumping;
                return pumping ? "§aFeed pump running" : "§eFeed pump stopped";
            }
            case "reset" -> { delivered = 0; return "§7Meter reset"; }
            case "fill" -> {
                if (who == null || who.getHeldItemMainhand().getItem() != Items.WATER_BUCKET) return "§7Hold a water bucket (or pipe it in / run the pump)";
                tank.fill(new FluidStack(FluidRegistry.WATER, 1000), true);
                who.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND, new ItemStack(Items.BUCKET));
                return "§aTank " + tank.getFluidAmount() + " L";
            }
            default -> { return null; }
        }
    }

    @Override
    protected String mechanical(String id, EntityPlayer who) {
        switch (id) {
            case "m_estop" -> { ctl.set(id, ctl.get(id) == 1 ? 0 : 1); if (ctl.get(id) == 1) pumping = false; return ctl.get(id) == 1 ? "§cE-STOP - pump off" : "§aE-stop released"; }
            case "m_arm" -> {
                if (valveOpen()) return "§cShut the valve before swinging the arm";
                ctl.set(id, armOut() ? 0 : 1);
                world.playSound(null, pos, SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN, SoundCategory.BLOCKS, 0.8f, 0.5f);
                return armOut() ? "§eArm swung out over the track" : "§7Arm swung back and chained (clear of the track)";
            }
            case "m_valve" -> {
                ctl.set(id, valveOpen() ? 0 : 1);
                if (valveOpen()) delivered = 0;
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ctl.selector"), SoundCategory.BLOCKS, 0.8f, 0.6f);
                return valveOpen() ? "§bValve OPEN" : "§7Valve shut - " + delivered + " L delivered";
            }
            default -> { return null; }
        }
    }

    BlockPos source() {
        for (BlockPos p : new BlockPos[]{pos.down(), pos.down(2), pos.north(), pos.south(), pos.east(), pos.west()})
            if (world.getBlockState(p).getMaterial() == Material.WATER) return p;
        return null;
    }

    @Override
    protected void work() {
        if (age % 20 != 0) return;
        boolean run = powered() && mainsOk() && ctl.get("m_estop") == 0;
        if (ctl.get("k_float") == 1 && run && !pumping && tank.getFluidAmount() < CAP * 0.6 && source() != null) pumping = true;
        if (pumping) {
            if (!run || source() == null) pumping = false;
            else { tank.fill(new FluidStack(FluidRegistry.WATER, PUMP), true); if (tank.getFluidAmount() >= CAP) pumping = false; }
        }
        FreightTank s = armOut() ? stock() : null;
        spilling = false;
        if (valveOpen() && tank.getFluidAmount() > 0) {
            int out = Math.min(FLOW, tank.getFluidAmount());
            int got = 0;
            if (s != null) {
                int before = Stock.water(s);
                got = s.theTank.fill(new cam72cam.mod.fluid.FluidStack(cam72cam.mod.fluid.Fluid.WATER, out), false);
                if (got > 0) {
                    SteamData.get(world).of(s.getUUID()).mix(ppm(), got, before);
                    doseCredit += treating() ? got : 0;
                    while (doseCredit >= 1000) { doseCredit -= 1000; useDose(); }
                    delivered += got; total += got;
                }
                spilling = got == 0;                    // its tank is full: the bag overflows until the valve is shut
            } else spilling = true;
            tank.drain(spilling ? out : got, true);
            if (spilling && age % 60 == 0) world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.BLOCKS, 0.7f, 0.8f);
            if (got > 0 && age % 80 == 0) world.playSound(null, pos, SoundEvents.BLOCK_WATER_AMBIENT, SoundCategory.BLOCKS, 0.8f, 1.0f);
        }
        put("pwr", powered()); put("mainsl", mains.ok()); put("pumpl", pumping); put("low", tank.getFluidAmount() < CAP / 10);
        put("flow", valveOpen() && !spilling && tank.getFluidAmount() > 0); put("spill", spilling); put("armout", armOut()); put("locol", s != null); put("treat", treating());
        put("tankg", tank.getFluidAmount() * 100 / CAP);
        put("ppmg", ppm() * 100 / 500);
        put("stockg", s == null ? 0 : Stock.water(s) * 100 / Math.max(1, Stock.capacity(s)));
        text("litres", String.valueOf(delivered));
        text("line1", s == null ? (armOut() ? "NO ENGINE AT ARM" : "ARM STOWED") : cut(Stock.name(s).toUpperCase(Locale.ROOT), 18));
        text("line2", String.format(Locale.ROOT, "%d PPM %s", ppm(), treating() ? "TREATED" : "RAW"));
        if (s != null && valveOpen() && Stock.water(s) >= Stock.capacity(s) - 10 && age % 100 == 0)
            Recorder.event(world, "Water column: " + Stock.name(s) + " full, " + delivered + " L - shut the valve");
    }

    /** for the renderer: the arm swung out, water running */
    public boolean armIsOut() { return ctl.get("m_arm") == 1; }
    public boolean flowing() { return ctl.get("flow") == 1 || ctl.get("spill") == 1; }

    @Override public boolean hasCapability(Capability<?> c, EnumFacing f) { return c == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY || super.hasCapability(c, f); }
    @SuppressWarnings("unchecked")
    @Override public <T> T getCapability(Capability<T> c, EnumFacing f) { return c == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY ? (T) tank : super.getCapability(c, f); }

    @Override protected void saveExtra(NBTTagCompound t) { t.setTag("tank", tank.writeToNBT(new NBTTagCompound())); t.setBoolean("pump", pumping); t.setInteger("del", delivered); t.setInteger("total", total); t.setInteger("dose", doseCredit); }
    @Override protected void loadExtra(NBTTagCompound t) { tank.readFromNBT(t.getCompoundTag("tank")); pumping = t.getBoolean("pump"); delivered = t.getInteger("del"); total = t.getInteger("total"); doseCredit = t.getInteger("dose"); }
    @Override public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() { return new net.minecraft.util.math.AxisAlignedBB(pos).grow(3, 3, 3); }
}
