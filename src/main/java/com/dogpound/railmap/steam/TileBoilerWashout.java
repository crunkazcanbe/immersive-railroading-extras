package com.dogpound.railmap.steam;

import cam72cam.immersiverailroading.entity.LocomotiveSteam;
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

import java.util.*;

/**
 * Boiler Washout Plant (batch 5 #2): the hot-water washout pump set, hot well and hoses for the washout road. Washing out
 * a boiler is a procedure, step by step on the sequence panel, each step proved before the next:
 * <ol>
 *   <li>BLOW DOWN: the fire is drawn and the boiler blown down to 0 bar (steam roars out);</li>
 *   <li>ISOLATE key: the engine is locked out (regulator shut, it can't be moved) - it must be standing;</li>
 *   <li>PLUGS: the washout plugs and mud-hole doors come out;</li>
 *   <li>WASH: the 37 kW pump drives hot water (heater on mains, 60 °C+ for a full wash) through the plug holes for a
 *   minute - 150 L/s from the 20 000 L hot well (pipe or bucket fed) - lifting most of the scale and the sludge;</li>
 *   <li>INSPECT: the scale left and the tube ends are read on the screen (leaking tubes are re-expanded);</li>
 *   <li>PLUGS back in, then TEST: a hydraulic test at 1.5 x working pressure;</li>
 *   <li>CERTIFY: the washout certificate goes on the asset register and the boiler's record is reset.</li>
 * </ol>
 * Scale off per wash: 75 % with hot water (60 °C+), 50 % cold. The meter, gauges and sequence need 12 V; the pump and
 * heater need mains. Fittings drawer: real storage.
 */
public class TileBoilerWashout extends com.dogpound.railmap.depot.DepotTile {
    static final int WELL = 20_000, WASH_S = 60, TEST_S = 30;
    static final String[] STEPS = {"READY", "BLOWN DOWN", "ISOLATED", "PLUGS OUT", "WASHING", "WASHED", "INSPECTED", "PLUGS IN", "TESTING", "TESTED", "CERTIFIED"};
    private final FluidTank well = new FluidTank(WELL) { @Override public boolean canFillFluidType(FluidStack f) { return f != null && f.getFluid() == FluidRegistry.WATER; } };
    private int step, timer;
    private float wellC = 15, scaleBefore, scaleAfter, removed;
    private UUID loco;
    private final List<String> sheet = new ArrayList<>();

    public TileBoilerWashout() { super(new String[]{"fittings"}, new String[]{"Washout plugs, joints and gaskets"}, new int[]{9}); well.setTileEntity(this); }

    @Override public String wiredName() { return "Boiler washout plant"; }
    @Override protected boolean usesMains() { return true; }
    @Override protected long load() { return (step == 4 || step == 8 ? 370 : 0) + (heater() ? 450 : 0) + 1; }
    @Override public String gearState() { return !powered() ? "NO 12 V" : String.format(Locale.ROOT, "%s · hot well %d L at %.0f °C", STEPS[step], well.getFluidAmount(), wellC); }

    boolean heater() { return Math.floorMod(ctl.get("s_heat"), 2) == 1 && powered() && mainsOk() && wellC < 75 && well.getFluidAmount() > 500; }

    LocomotiveSteam near() { return Stock.nearest(world, pos, 6, LocomotiveSteam.class, null); }
    LocomotiveSteam mine() { LocomotiveSteam l = near(); return l != null && loco != null && l.getUUID().equals(loco) ? l : null; }

    @Override
    protected String press(String id, EntityPlayer who) {
        LocomotiveSteam l = step == 0 ? near() : mine();
        switch (id) {
            case "blow" -> {
                if (step != 0) return "§7Sequence already running (" + STEPS[step] + ") - ABORT to start again";
                if (l == null) return "§cNo steam engine on the washout road";
                if (!Stock.standing(l)) return "§cThe engine must be standing";
                for (Map.Entry<Integer, Integer> e : l.getBurnTime().entrySet()) e.setValue(0);
                l.setBoilerPressureBar(0);
                l.setBoilerTemperature(Math.min(l.getBoilerTemperature(), 95));
                loco = l.getUUID(); step = 1;
                scaleBefore = SteamData.get(world).of(loco).scaleMm;
                if (world instanceof net.minecraft.world.WorldServer ws) ws.spawnParticle(EnumParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 2, pos.getZ() + 0.5, 40, 1.2, 0.8, 1.2, 0.05);
                world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 1.4f, 0.4f);
                return "§aFire drawn, boiler blown down to 0 bar - turn the ISOLATE key";
            }
            case "plugs" -> {
                if (step == 2) { step = 3; sound(); return "§aWashout plugs and mud-hole doors OUT"; }
                if (step == 6) { step = 7; sound(); return "§aPlugs back in, new joints - ready for the hydraulic TEST"; }
                return "§c" + (step < 2 ? "Isolate the engine first" : "Not now: " + STEPS[step]);
            }
            case "wash" -> {
                if (step != 3) return "§c" + (step < 3 ? "Take the plugs out first" : "Not now: " + STEPS[step]);
                if (ctl.get("m_estop") == 1) return "§cE-STOP is in";
                if (!mainsOk()) return "§cNo mains for the washout pump (" + mains.what + ")";
                if (well.getFluidAmount() < WASH_S * 150) return "§cNot enough water in the hot well (" + WASH_S * 150 + " L for a wash)";
                step = 4; timer = 0;
                return String.format(Locale.ROOT, "§aWashing out with %.0f °C water", wellC);
            }
            case "inspect" -> {
                if (step != 5) return "§c" + (step < 5 ? "Wash it out first" : "Not now: " + STEPS[step]);
                SteamData.Boiler b = SteamData.get(world).of(loco);
                step = 6;
                sheet.clear();
                sheet.add(String.format(Locale.ROOT, "Scale before %.2f mm, after %.2f mm", scaleBefore, scaleAfter));
                sheet.add(String.format(Locale.ROOT, "Sludge / scale removed: %.0f kg", removed));
                sheet.add(b.leak ? "Tube ends LEAKING - re-expanded" : "Tube ends tight");
                sheet.add(scaleAfter > SteamConfig.washoutDueMm * 0.6 ? "Heavy scale left - wash again soon" : "Heating surfaces clean");
                return "§a" + String.join(" · ", sheet);
            }
            case "hyd" -> {
                if (step != 7) return "§c" + (step < 7 ? "Plugs back in first" : "Not now: " + STEPS[step]);
                if (!mainsOk()) return "§cNo mains for the test pump";
                step = 8; timer = 0;
                return String.format(Locale.ROOT, "§aHydraulic test to %.1f bar", testBar(l));
            }
            case "cert" -> {
                if (step != 9) return "§c" + (step < 9 ? "Complete the hydraulic test first" : "Already certified");
                if (l == null) return "§cThe engine has gone";
                float frac = scaleBefore <= 0 ? 0 : Math.max(0, 1 - scaleAfter / scaleBefore);
                SteamTicker.washed(world, l, frac, who(who));
                List<String> c = new ArrayList<>();
                c.add("BOILER WASHOUT - " + Stock.name(l));
                c.addAll(sheet);
                c.add(String.format(Locale.ROOT, "Hydraulic test %.1f bar - held, no leaks", testBar(l)));
                c.add("Washed out by " + who(who));
                try { com.dogpound.railmap.asset.AssetData.get(world).addCert(world, Stock.name(l), "BOILER WASHOUT", true, c, "washout plant"); } catch (LinkageError | RuntimeException ignored) { }
                step = 10;
                return "§aWashout certified - " + Stock.name(l) + " released (turn the ISOLATE key back)";
            }
            case "abort" -> { step = 0; loco = null; timer = 0; return "§eSequence aborted"; }
            case "fill" -> {
                if (who == null || who.getHeldItemMainhand().getItem() != Items.WATER_BUCKET) return "§7Hold a water bucket (or pipe water to the hot well)";
                well.fill(new FluidStack(FluidRegistry.WATER, 1000), true);
                who.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND, new ItemStack(Items.BUCKET));
                return "§aHot well " + well.getFluidAmount() + " L";
            }
            default -> { return null; }
        }
    }

    float testBar(LocomotiveSteam l) { return l == null ? 15 : l.getMaxBoilerPSI() / 14.5038f * 1.5f; }
    void sound() { world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("machine.air_tool"), SoundCategory.BLOCKS, 0.7f, 1.0f); }

    @Override
    protected String mechanical(String id, EntityPlayer who) {
        switch (id) {
            case "m_estop" -> { ctl.set(id, ctl.get(id) == 1 ? 0 : 1); if (ctl.get(id) == 1 && (step == 4 || step == 8)) step--; return ctl.get(id) == 1 ? "§cE-STOP - pump stopped" : "§aE-stop released"; }
            case "k_iso" -> {
                int v = ctl.get(id) == 1 ? 0 : 1;
                if (v == 1 && step != 1) return "§c" + (step == 0 ? "BLOW DOWN first" : "Not now: " + STEPS[step]);
                if (v == 0 && step > 1 && step < 10) return "§cThe engine stays isolated until the washout is certified (or ABORT)";
                ctl.set(id, v);
                if (v == 1) { step = 2; return "§eEngine ISOLATED - locked out for the washout"; }
                if (step == 10) { step = 0; loco = null; }
                return "§7Isolation off";
            }
            default -> { return null; }
        }
    }

    @Override
    protected void work() {
        if (age % 20 != 0) return;
        wellC += heater() ? 1.5f : -(wellC - 15) * 0.003f;
        LocomotiveSteam l = loco == null ? near() : mine();
        if (loco != null && l == null && step > 0 && step < 10) { step = 0; loco = null; Recorder.event(world, "Boiler washout: the engine left the washout road - sequence aborted"); }
        if (l != null && ctl.get("k_iso") == 1 && step >= 2) { l.setThrottle(0); l.setBoilerPressureBar(0); }
        boolean run = powered() && mainsOk() && ctl.get("m_estop") == 0;
        if (step == 4 && run) {
            if (well.getFluidAmount() < 150) { step = 3; Recorder.event(world, "Boiler washout: hot well ran dry - wash stopped"); }
            else {
                well.drain(150, true);
                if (++timer >= WASH_S) {
                    SteamData.Boiler b = SteamData.get(world).of(loco);
                    float frac = wellC >= 60 ? 0.75f : 0.5f;
                    scaleAfter = b.scaleMm * (1 - frac);
                    removed = (b.scaleMm - scaleAfter) * 40 + 5;
                    step = 5;
                    world.playSound(null, pos, com.dogpound.railmap.ModSounds.BEEP_OK, SoundCategory.BLOCKS, 0.5f, 1.0f);
                }
                if (world instanceof net.minecraft.world.WorldServer ws && timer % 3 == 0) ws.spawnParticle(EnumParticleTypes.WATER_SPLASH, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20, 1.0, 0.3, 1.0, 0.1);
            }
        }
        if (step == 8 && run && ++timer >= TEST_S) step = 9;
        SteamData.Boiler b = l == null ? null : SteamData.get(world).peek(l.getUUID());
        put("pwr", powered()); put("mainsl", mains.ok()); put("locol", l != null); put("heat", heater()); put("hot", wellC >= 60);
        put("pumpl", step == 4 || step == 8); put("isol", ctl.get("k_iso") == 1); put("due", b != null && b.scaleMm >= SteamConfig.washoutDueMm); put("leak", b != null && b.leak);
        for (int i = 1; i <= 10; i++) put("st" + i, step >= i);
        put("wellg", well.getFluidAmount() * 100 / WELL); put("tempg", Math.round(wellC));
        put("scaleg", b == null ? 0 : Math.round(Math.min(100, b.scaleMm * 25)));
        put("barg", l == null ? 0 : Math.round(step == 8 ? Math.min(100, timer * 100f / 10) : l.getBoilerPressureBar() * 100 / Math.max(1, testBar(l))));
        put("progg", step == 4 ? timer * 100 / WASH_S : step == 8 ? timer * 100 / TEST_S : 0);
        text("step", STEPS[step]);
        text("line1", l == null ? "NO ENGINE ON THE ROAD" : cut(Stock.name(l).toUpperCase(Locale.ROOT), 20));
        text("line2", b == null ? "" : String.format(Locale.ROOT, "SCALE %.2f MM %s", b.scaleMm, b.leak ? "LEAKING" : ""));
        text("line3", b == null ? "" : String.format(Locale.ROOT, "%.0f H IN STEAM %d WASHOUTS", b.steamH, b.washouts));
        text("temp", String.format(Locale.ROOT, "%.0f", wellC));
    }

    @Override public boolean hasCapability(Capability<?> c, EnumFacing f) { return c == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY || super.hasCapability(c, f); }
    @SuppressWarnings("unchecked")
    @Override public <T> T getCapability(Capability<T> c, EnumFacing f) { return c == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY ? (T) well : super.getCapability(c, f); }

    @Override protected void saveExtra(NBTTagCompound t) {
        t.setTag("well", well.writeToNBT(new NBTTagCompound())); t.setInteger("step", step); t.setInteger("timer", timer); t.setFloat("wc", wellC);
        t.setFloat("sb", scaleBefore); t.setFloat("sa", scaleAfter); t.setFloat("rm", removed);
        if (loco != null) { t.setLong("lh", loco.getMostSignificantBits()); t.setLong("ll", loco.getLeastSignificantBits()); }
        t.setString("sheet", String.join("\n", sheet));
    }
    @Override protected void loadExtra(NBTTagCompound t) {
        well.readFromNBT(t.getCompoundTag("well")); step = t.getInteger("step"); timer = t.getInteger("timer"); wellC = t.hasKey("wc") ? t.getFloat("wc") : 15;
        scaleBefore = t.getFloat("sb"); scaleAfter = t.getFloat("sa"); removed = t.getFloat("rm");
        loco = t.hasKey("lh") ? new UUID(t.getLong("lh"), t.getLong("ll")) : null;
        sheet.clear(); if (!t.getString("sheet").isEmpty()) sheet.addAll(Arrays.asList(t.getString("sheet").split("\n")));
    }
    @Override public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() { return new net.minecraft.util.math.AxisAlignedBB(pos).grow(2, 2, 2); }
}
