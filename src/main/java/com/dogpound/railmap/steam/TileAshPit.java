package com.dogpound.railmap.steam;

import cam72cam.immersiverailroading.entity.LocomotiveSteam;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;

import java.util.Locale;

/**
 * Ash Pit (batch 5 #2): the lined pit beside the disposal road with its quench sprays, grab hoist and ash wagon. A steam
 * engine standing at the pit (the 12 V pit interlock proves it stands and lights the pit) has its fire cleaned and its
 * ash pan dropped (DROP ASH): the ash goes into the pit red hot. QUENCH sprays it from the 5 000 L water tank (pipe or
 * bucket); unquenched ash stays hot, and hot ash left more than two minutes raises a FIRE alarm. HOIST lifts quenched ash
 * with the grab into the ash wagon - real Locomotive Ash items, 1 per 25 kg, taken from the wagon by hand or a hopper.
 * Mains for the grab hoist + 12 V for the interlock, lights and gauges; the quench valve is mechanical.
 */
public class TileAshPit extends com.dogpound.railmap.depot.DepotTile {
    static final int WATER_CAP = 5000;
    private final FluidTank water = new FluidTank(WATER_CAP) { @Override public boolean canFillFluidType(FluidStack f) { return f != null && f.getFluid() == FluidRegistry.WATER; } };
    private float pitKg, tempC = 20, hotSec, kgTotal;
    private boolean hoisting;

    public TileAshPit() { super(new String[]{"wagon", "tools"}, new String[]{"Ash wagon", "Fire-irons rack (rakes, clinker shovel, pricker)"}, new int[]{27, 9}); water.setTileEntity(this); }

    @Override public String wiredName() { return "Ash pit"; }
    @Override protected boolean usesMains() { return true; }
    @Override protected long load() { return hoisting ? 300 : 2; }
    @Override public String gearState() { return !powered() ? "NO 12 V" : String.format(Locale.ROOT, "pit %.0f kg ash at %.0f °C · water %d L", pitKg, tempC, water.getFluidAmount()); }

    LocomotiveSteam loco() { return Stock.nearest(world, pos, 5, LocomotiveSteam.class, null); }

    @Override
    protected String press(String id, EntityPlayer who) {
        switch (id) {
            case "drop" -> {
                LocomotiveSteam l = loco();
                if (l == null) return "§cNo steam engine at the pit";
                if (!Stock.standing(l)) return "§cInterlock: the engine must be standing, brakes on";
                SteamData.Boiler b = SteamData.get(world).of(l.getUUID());
                if (b.ashKg < 1) return "§7Its ash pan is clean";
                if (pitKg + b.ashKg > 2000) return "§cThe pit is full - hoist the ash out first";
                float kg = b.ashKg;
                pitKg += kg; kgTotal += kg; b.ashKg = 0;
                tempC = Math.max(tempC, 450);
                SteamData.get(world).note(world, String.format(Locale.ROOT, "%s fire cleaned, %.0f kg ash dropped at the pit", Stock.name(l), kg));
                SteamData.get(world).markDirty();
                world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.8f, 0.6f);
                return String.format(Locale.ROOT, "§aFire cleaned, ash pan dropped: %.0f kg of red-hot ash in the pit - QUENCH it", kg);
            }
            case "hoist" -> {
                if (ctl.get("m_estop") == 1) return "§cE-STOP is in";
                if (!mainsOk()) return "§cNo mains for the grab hoist (" + mains.what + ")";
                if (tempC > 100) return String.format(Locale.ROOT, "§cThe ash is still %.0f °C - QUENCH it before the grab goes in", tempC);
                if (pitKg < 25) return "§7Less than a grab-full in the pit";
                hoisting = true;
                return "§aGrab hoisting ash into the wagon";
            }
            case "fill" -> {
                if (who == null || who.getHeldItemMainhand().getItem() != Items.WATER_BUCKET) return "§7Hold a water bucket (or pipe water in)";
                water.fill(new FluidStack(FluidRegistry.WATER, 1000), true);
                who.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND, new ItemStack(Items.BUCKET));
                return "§aQuench tank " + water.getFluidAmount() + " L";
            }
            default -> { return null; }
        }
    }

    @Override
    protected String mechanical(String id, EntityPlayer who) {
        switch (id) {
            case "m_estop" -> { ctl.set(id, ctl.get(id) == 1 ? 0 : 1); if (ctl.get(id) == 1) hoisting = false; return ctl.get(id) == 1 ? "§cE-STOP - grab stopped" : "§aE-stop released"; }
            case "m_quench" -> { ctl.set(id, ctl.get(id) == 1 ? 0 : 1); return ctl.get(id) == 1 ? "§bQuench sprays ON" : "§7Quench sprays off"; }
            default -> { return null; }
        }
    }

    @Override
    protected void work() {
        if (age % 20 != 0) return;
        boolean spray = ctl.get("m_quench") == 1 && water.getFluidAmount() >= 20;
        if (spray) {
            water.drain(20, true);
            if (pitKg > 0) tempC = Math.max(20, tempC - 40);
            if (world instanceof net.minecraft.world.WorldServer ws && pitKg > 0 && tempC > 60)
                ws.spawnParticle(EnumParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 6, 0.4, 0.1, 0.4, 0.02);
            if (age % 60 == 0) world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.5f, 1.2f);
        } else if (pitKg > 0) tempC = Math.max(20, tempC - 1);      // ash cools slowly in air
        else tempC = 20;
        hotSec = pitKg > 0 && tempC > 200 ? hotSec + 1 : 0;
        String key = "ashfire:" + pos.toLong();
        if (hotSec > 120) AlarmManager.get(world).raise(world, key, AlarmManager.Cat.FIRE, AlarmManager.Sev.MAJOR, "Ash pit at " + pos.getX() + "," + pos.getZ() + ": HOT ASH NOT QUENCHED - fire risk", pos, true);
        else AlarmManager.get(world).clear(key);
        if (hoisting) {
            if (!powered() || !mainsOk() || ctl.get("m_estop") == 1 || pitKg < 25) hoisting = false;
            else {
                ItemStack grab = new ItemStack(SteamItems.ash, 1);
                ItemStack rest = net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(new net.minecraftforge.items.wrapper.InvWrapper(store(0)), grab, false);
                if (!rest.isEmpty()) { hoisting = false; Recorder.event(world, "Ash pit: ash wagon full - empty it"); }
                else { pitKg -= 25; if (age % 40 == 0) world.playSound(null, pos, SoundEvents.BLOCK_GRAVEL_BREAK, SoundCategory.BLOCKS, 0.7f, 0.8f); }
            }
        }
        int wag = 0;
        for (int i = 0; i < 27; i++) wag += store(0).getStackInSlot(i).getCount();
        LocomotiveSteam l = loco();
        SteamData.Boiler b = l == null ? null : SteamData.get(world).peek(l.getUUID());
        put("pwr", powered()); put("mainsl", mains.ok()); put("locol", l != null); put("hot", tempC > 100); put("spray", spray); put("hoistl", hoisting);
        put("full", pitKg > 1800); put("wfull", wag >= 27 * 64); put("lowwater", water.getFluidAmount() < 400); put("interlock", l != null && Stock.standing(l));
        put("pitg", Math.round(pitKg * 100 / 2000)); put("tempg", Math.round(tempC * 100 / 500)); put("waterg", water.getFluidAmount() * 100 / WATER_CAP); put("wagong", wag * 100 / (27 * 64));
        put("pang", b == null ? 0 : Math.round(b.ashKg * 100 / SteamConfig.ashPanKg));
        text("kg", String.format(Locale.ROOT, "%.0f", pitKg));
        text("line1", l == null ? "NO ENGINE AT PIT" : cut(Stock.name(l).toUpperCase(Locale.ROOT), 18));
        text("line2", b == null ? "" : String.format(Locale.ROOT, "ASH PAN %.0f KG", b.ashKg));
        text("temp", String.format(Locale.ROOT, "%.0f", tempC));
    }

    @Override public boolean hasCapability(Capability<?> c, EnumFacing f) { return c == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY || super.hasCapability(c, f); }
    @SuppressWarnings("unchecked")
    @Override public <T> T getCapability(Capability<T> c, EnumFacing f) { return c == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY ? (T) water : super.getCapability(c, f); }

    @Override protected void saveExtra(NBTTagCompound t) { t.setTag("water", water.writeToNBT(new NBTTagCompound())); t.setFloat("pit", pitKg); t.setFloat("temp", tempC); t.setFloat("kgT", kgTotal); t.setBoolean("hoist", hoisting); }
    @Override protected void loadExtra(NBTTagCompound t) { water.readFromNBT(t.getCompoundTag("water")); pitKg = t.getFloat("pit"); tempC = t.hasKey("temp") ? t.getFloat("temp") : 20; kgTotal = t.getFloat("kgT"); hoisting = t.getBoolean("hoist"); }
}
