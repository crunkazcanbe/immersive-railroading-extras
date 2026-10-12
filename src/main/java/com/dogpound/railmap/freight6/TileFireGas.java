package com.dogpound.railmap.freight6;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.stn.StnUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tank Farm Fire and Gas Panel (batch 6 #27): the fire-water / foam house - fire-water tank, foam concentrate tank with
 * its bladder proportioner, deluge valve set, the fire and gas control panel with a zone mimic, beacon and horn.
 * GAS: each loading rack's detector (vapour, % LEL) - 10 % LOW alarm, 25 % HIGH = automatic ESD of every rack.
 * FIRE: heat detection round every tank and rack (real fire blocks). On fire with the panel in AUTO (or DELUGE pressed)
 * the deluge valve opens: fire water (60 000 L - pipe it in or buckets via FILL) at 500 L/s with 3 % foam
 * concentrate (slimeballs in the foam store = 200 L AFFF drums) blankets every tank and rack in range - the fires go
 * out. ESD stops every rack and shuts every tank's valves. SILENCE, RESET, lamp TEST. 12 V + mains (fire pump).
 */
public class TileFireGas extends com.dogpound.railmap.depot.DepotTile {
    static final int WATER = 60000, RATE = 500;
    final FluidTank water = new FluidTank(WATER) { @Override public boolean canFillFluidType(FluidStack f) { return f != null && f.getFluid() == FluidRegistry.WATER; } };
    private boolean deluge, esd, silenced;
    private float foamL;
    private int fireZones, quiet, page;
    private float lel;
    private long used;

    public TileFireGas() { super(new String[]{"foam"}, new String[]{"Foam concentrate drums (slimeballs = 200 L AFFF)"}, new int[]{9}); water.setTileEntity(this); }

    @Override public String wiredName() { return "Tank farm fire and gas panel"; }
    @Override protected boolean usesMains() { return true; }
    @Override protected long load() { return deluge ? 400 : 4; }
    @Override public String gearState() { return !powered() ? "NO 12 V" : String.format(Locale.ROOT, "gas %.0f %% LEL · fire zones %d · %s%s · water %d L, foam %.0f L", lel, fireZones, deluge ? "DELUGE" : "standby", esd ? " · ESD" : "", water.getFluidAmount(), foam()); }

    float foam() {
        float drums = 0;
        for (int i = 0; i < store(0).getSizeInventory(); i++) if (store(0).getStackInSlot(i).getItem() == Items.SLIME_BALL) drums += store(0).getStackInSlot(i).getCount();
        return foamL + drums * 200;
    }

    boolean useFoam(float l) {
        if (foamL >= l) { foamL -= l; return true; }
        for (int i = 0; i < store(0).getSizeInventory(); i++) {
            ItemStack s = store(0).getStackInSlot(i);
            if (s.getItem() != Items.SLIME_BALL) continue;
            s.shrink(1);
            if (s.isEmpty()) store(0).setInventorySlotContents(i, ItemStack.EMPTY);
            foamL += 200;
            foamL -= l;
            return true;
        }
        return false;
    }

    List<TileOilRack> racks() { return StnUtil.near(world, TileOilRack.class, pos, 32); }
    List<TileOilTank> tanks() { return TileOilTank.TankFarm.tanks(world, pos); }

    void doEsd(String why) {
        if (!esd) {
            Recorder.event(world, "Tank farm ESD: " + why);
            AlarmManager.get(world).raise(world, "oil:esd:" + pos.toLong(), AlarmManager.Cat.FREIGHT, AlarmManager.Sev.MAJOR, "Tank farm at " + pos.getX() + "," + pos.getZ() + ": EMERGENCY SHUTDOWN - " + why, pos, true);
        }
        esd = true;
        for (TileOilRack r : racks()) r.esd(why);
        for (TileOilTank t : tanks()) t.shutValves();
    }

    @Override
    protected String press(String id, EntityPlayer who) {
        switch (id) {
            case "deluge" -> {
                if (water.getFluidAmount() <= 0) return "§cThe fire-water tank is empty";
                deluge = true; quiet = 0;
                Recorder.event(world, "Tank farm deluge released by " + who(who));
                return "§cDELUGE RELEASED";
            }
            case "dstop" -> { if (fireZones > 0) return "§cFire still detected - the deluge stays open"; deluge = false; return "§eDeluge valve shut"; }
            case "esd" -> { doEsd("ESD pushed by " + who(who)); return "§cEMERGENCY SHUTDOWN - all racks stopped, tank valves shut"; }
            case "silence" -> { silenced = true; return "§7Horn silenced"; }
            case "reset" -> {
                if (fireZones > 0) return "§cFire still detected";
                if (lel >= 10) return "§cGas still above 10 % LEL";
                esd = false; silenced = false; deluge = false;
                for (TileOilRack r : racks()) r.esdReset();
                for (String k : new String[]{"oil:esd:", "oil:gas:", "oil:fire:"}) AlarmManager.get(world).clear(k + pos.toLong());
                return "§aPanel reset - reopen the tank valves by hand";
            }
            case "page" -> { page = (page + 1) % 2; return "§7Page " + (page + 1) + "/2"; }
            case "fill" -> {
                if (who == null) return "§7Pipe the fire water in";
                ItemStack h = who.getHeldItemMainhand();
                var res = FluidUtil.tryEmptyContainer(h, water, Integer.MAX_VALUE, who, true);
                if (!res.isSuccess()) return "§7Hold a water bucket (or pipe water in)";
                who.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND, res.getResult());
                return "§aFire water " + water.getFluidAmount() + " L";
            }
            default -> { return null; }
        }
    }

    @Override
    protected void work() {
        if (age % 20 != 0) return;
        List<TileOilRack> racks = racks();
        List<TileOilTank> tanks = tanks();
        lel = 0;
        for (TileOilRack r : racks) lel = Math.max(lel, r.vapour());
        List<BlockPos> fires = new ArrayList<>();
        int zones = 0;
        for (TileOilTank t : tanks) { BlockPos f = TileOilTank.TankFarm.fireNear(world, t.getPos(), 3); if (f != null) { fires.add(f); zones++; } }
        for (TileOilRack r : racks) { BlockPos f = TileOilTank.TankFarm.fireNear(world, r.getPos(), 3); if (f != null) { fires.add(f); zones++; } }
        fireZones = zones;
        boolean live = powered();
        if (live) {
            if (lel >= 10) AlarmManager.get(world).raise(world, "oil:gas:" + pos.toLong(), AlarmManager.Cat.FIRE, lel >= 25 ? AlarmManager.Sev.MAJOR : AlarmManager.Sev.MINOR,
                    String.format(Locale.ROOT, "Tank farm at %d,%d: GAS %s %.0f %% LEL at a loading rack", pos.getX(), pos.getZ(), lel >= 25 ? "HIGH" : "LOW", lel), pos, false);
            else AlarmManager.get(world).clear("oil:gas:" + pos.toLong());
            if (lel >= 25) doEsd(String.format(Locale.ROOT, "gas %.0f %% LEL", lel));
            if (zones > 0) {
                AlarmManager.get(world).raise(world, "oil:fire:" + pos.toLong(), AlarmManager.Cat.FIRE, AlarmManager.Sev.CRITICAL, "Tank farm at " + pos.getX() + "," + pos.getZ() + ": FIRE in " + zones + " zone(s)", pos, true);
                doEsd("fire detected");
                if (ctl.get("k_auto") == 1 && !deluge && water.getFluidAmount() > 0) { deluge = true; quiet = 0; Recorder.event(world, "Tank farm deluge released automatically (fire)"); }
            }
        }
        if (deluge) {
            boolean pump = live && mainsOk();
            FluidStack w = pump ? water.drain(RATE, true) : null;
            if (w == null || w.amount <= 0) { deluge = false; Recorder.event(world, "Tank farm deluge stopped: " + (pump ? "fire water exhausted" : "no fire pump (12 V / mains)")); }
            else {
                used += w.amount;
                boolean foamed = useFoam(w.amount * 0.03f);
                // the deluge blankets every tank and rack: water + foam put fires out (water alone only cools: half the time)
                for (BlockPos f : fires) for (BlockPos q : BlockPos.getAllInBoxMutable(f.add(-3, -1, -3), f.add(3, 3, 3)))
                    if (world.getBlockState(q).getBlock() == Blocks.FIRE && (foamed || world.rand.nextBoolean())) world.setBlockToAir(q);
                if (world instanceof WorldServer ws) {
                    for (TileOilTank t : tanks) ws.spawnParticle(foamed ? EnumParticleTypes.SNOW_SHOVEL : EnumParticleTypes.WATER_SPLASH, t.getPos().getX() + 0.5, t.getPos().getY() + 2.2, t.getPos().getZ() + 0.5, 40, 1.2, 0.4, 1.2, 0.05);
                    for (TileOilRack r : racks) ws.spawnParticle(EnumParticleTypes.WATER_SPLASH, r.getPos().getX() + 0.5, r.getPos().getY() + 2.5, r.getPos().getZ() + 0.5, 30, 1.0, 0.3, 1.0, 0.05);
                }
                if (age % 60 == 0) world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("machine.pump"), net.minecraft.util.SoundCategory.BLOCKS, 1f, 0.8f);
                if (zones == 0 && ++quiet > 20) { deluge = false; Recorder.event(world, "Tank farm deluge closed - fire out, " + used + " L used"); }
            }
        }
        boolean alarm = live && (zones > 0 || lel >= 10 || esd);
        if (alarm && !silenced && age % 60 == 0) world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev(zones > 0 ? "alarm.fire_bell" : "alarm.gas_release"), net.minecraft.util.SoundCategory.BLOCKS, 1.2f, 1f);
        if (!alarm) silenced = false;
        put("pwr", live); put("mainsl", mains.ok()); put("fire", zones > 0); put("gaslo", lel >= 10); put("gashi", lel >= 25); put("esdl", esd); put("delugel", deluge);
        put("autol", ctl.get("k_auto") == 1); put("beacon", alarm); put("silencel", silenced); put("foaml", foam() < 400); put("waterl", water.getFluidAmount() < WATER / 4);
        put("lelg", Math.round(Math.min(100, lel))); put("waterg", water.getFluidAmount() * 100 / WATER); put("foamg", Math.round(Math.min(100, foam() * 100 / 2000)));
        for (int i = 0; i < 6; i++) {
            put("z" + i, i < tanks.size() && TileOilTank.TankFarm.fireNear(world, tanks.get(i).getPos(), 3) != null ? 1 : 0);
            put("r" + i, i < racks.size() ? (racks.get(i).vapour() >= 25 ? 1 : racks.get(i).vapour() >= 10 ? 3 : 0) : 0);
        }
        text("lel", String.format(Locale.ROOT, "%.0f", lel));
        if (page == 0) {
            text("line1", zones > 0 ? "FIRE " + zones + " ZONE" + (zones > 1 ? "S" : "") : lel >= 10 ? String.format(Locale.ROOT, "GAS %.0f%% LEL", lel) : "NORMAL");
            text("line2", (deluge ? "DELUGE OPEN" : "DELUGE READY") + (esd ? " ESD" : ""));
            text("line3", tanks.size() + " TANKS " + racks.size() + " RACKS");
        } else {
            text("line1", "WATER " + water.getFluidAmount() / 1000 + "K L");
            text("line2", String.format(Locale.ROOT, "FOAM %.0f L 3%%", foam()));
            text("line3", "USED " + used / 1000 + "K L");
        }
        text("pageno", (page + 1) + "/2");
    }

    public boolean esdActive() { return esd; }
    public float lel() { return lel; }
    public int fireZones() { return fireZones; }
    public boolean delugeOn() { return deluge; }

    @Override public boolean hasCapability(Capability<?> c, EnumFacing f) { return c == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY || super.hasCapability(c, f); }
    @SuppressWarnings("unchecked")
    @Override public <T> T getCapability(Capability<T> c, EnumFacing f) { return c == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY ? (T) water : super.getCapability(c, f); }

    @Override protected void saveExtra(NBTTagCompound t) {
        t.setTag("water", water.writeToNBT(new NBTTagCompound())); t.setBoolean("del", deluge); t.setBoolean("esd", esd); t.setBoolean("sil", silenced);
        t.setFloat("foam", foamL); t.setLong("used", used); t.setInteger("page", page);
    }
    @Override protected void loadExtra(NBTTagCompound t) {
        water.readFromNBT(t.getCompoundTag("water")); deluge = t.getBoolean("del"); esd = t.getBoolean("esd"); silenced = t.getBoolean("sil");
        foamL = t.getFloat("foam"); used = t.getLong("used"); page = t.getInteger("page");
    }

}
