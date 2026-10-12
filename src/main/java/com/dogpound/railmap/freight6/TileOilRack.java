package com.dogpound.railmap.freight6;

import cam72cam.immersiverailroading.entity.FreightTank;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;
import java.util.Locale;

/**
 * Tank Car Loading Rack (batch 6 #27): a top-loading gantry - stair tower, platform, gangway, counterweighted loading
 * arm with its drop tube, the preset meter and batch controller, ground-verification unit and overfill probe.
 * LOAD: pick the tank (FROM TANK 1-6, its OUTLET valve open) and the preset (+ / - in 5 000 L steps, or FULL), clamp
 * the GROUND, lower the ARM, START: the rack pump runs product from the tank into the tank car standing under it until
 * the preset, the car's overfill probe (98 %) or the tank runs dry. UNLOAD (mode key) runs the car's load back into the
 * tank (its INLET open, not HIGH-HIGH). The PERMISSIVE lamp needs ground + arm + a standing car + no ESD. Loading with
 * VAPOUR RECOVERY off vents vapour: the Fire and Gas Panel sees the LEL rise. Mains (pump) + 12 V.
 */
public class TileOilRack extends com.dogpound.railmap.depot.DepotTile {
    private boolean run, esd;
    private int preset = 20000, metered, total;
    float vapour;                                        // % LEL at the rack's detector
    private String car = "", why = "";

    public TileOilRack() { super(new String[]{"ppe"}, new String[]{"PPE locker (gloves, goggles, gas monitor)"}, new int[]{9}); }

    @Override public String wiredName() { return "Tank car loading rack"; }
    @Override protected boolean usesMains() { return true; }
    @Override protected long load() { return run ? 220 : 3; }
    @Override public String gearState() { return !powered() ? "NO 12 V" : (esd ? "ESD LOCKOUT · " : "") + (run ? String.format(Locale.ROOT, "%s %s %d / %d L", unloading() ? "UNLOADING" : "LOADING", car, metered, preset) : "idle") + String.format(Locale.ROOT, " · %.0f %% LEL", vapour); }

    boolean unloading() { return ctl.get("k_mode") == 1; }
    int tankNo() { return Math.floorMod(ctl.get("s_tank"), 6); }
    TileOilTank tank() { List<TileOilTank> l = TileOilTank.TankFarm.tanks(world, pos); return tankNo() < l.size() ? l.get(tankNo()) : null; }
    boolean grounded() { return ctl.get("m_ground") == 1; }
    boolean armDown() { return ctl.get("m_arm") == 1; }
    public boolean running() { return run; }
    public float vapour() { return vapour; }
    public void esd(String why) { if (run) Recorder.event(world, "Loading rack " + pos.getX() + "," + pos.getZ() + ": ESD - " + why); run = false; esd = true; this.why = "ESD"; }
    public void esdReset() { esd = false; why = ""; }
    boolean permissive(FreightTank c) { return grounded() && armDown() && c != null && !esd && ctl.get("m_estop") == 0; }

    @Override
    protected String press(String id, EntityPlayer who) {
        switch (id) {
            case "start" -> {
                FreightTank c = Cars.tank(world, pos, 7);
                if (esd) return "§cESD LOCKOUT - reset at the Fire and Gas Panel";
                if (ctl.get("m_estop") == 1) return "§cE-STOP is in";
                if (!grounded()) return "§cGROUND not verified - clamp the earthing lead to the car";
                if (!armDown()) return "§cLower the loading ARM into the dome";
                if (c == null) return "§cNo tank car standing at the rack";
                if (!mainsOk()) return "§cNo mains for the rack pump (" + mains.what + ")";
                TileOilTank t = tank();
                if (t == null) return "§cTANK " + (tankNo() + 1) + " does not exist (Oil Storage Tanks within 32 blocks)";
                if (unloading()) {
                    if (c.getLiquidAmount() <= 0) return "§cThe car is empty";
                    if (!t.inletOpen()) return "§cTANK " + (tankNo() + 1) + " INLET valve is shut";
                    if (t.highHigh()) return "§cTANK " + (tankNo() + 1) + " is at HIGH-HIGH";
                } else {
                    if (!t.outletOpen()) return "§cTANK " + (tankNo() + 1) + " OUTLET valve is shut";
                    if (t.tank().getFluidAmount() <= 0) return "§cTANK " + (tankNo() + 1) + " is empty";
                    cam72cam.mod.fluid.Fluid fl = cam72cam.mod.fluid.Fluid.getFluid(t.tank().getFluid().getFluid());
                    if (fl == null || !c.theTank.allows(fl)) return "§c" + Cars.name(c) + " does not take " + t.product();
                    if (c.getPercentLiquidFull() >= 98) return "§7The car is full (overfill probe wet)";
                }
                run = true; metered = 0; car = Cars.name(c); why = "";
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("machine.fuel_pump"), net.minecraft.util.SoundCategory.BLOCKS, 0.8f, 1f);
                return "§a" + (unloading() ? "Unloading " : "Loading ") + car + (unloading() ? " to" : " from") + " TANK " + (tankNo() + 1) + " - preset " + preset + " L";
            }
            case "stop" -> { run = false; return "§eStopped - " + metered + " L metered"; }
            case "p_up" -> { preset = Math.min(200000, preset + 5000); return "§7Preset " + preset + " L"; }
            case "p_dn" -> { preset = Math.max(5000, preset - 5000); return "§7Preset " + preset + " L"; }
            case "p_full" -> { preset = 200000; return "§7Preset FULL (to the overfill probe)"; }
            default -> { return null; }
        }
    }

    @Override
    protected String mechanical(String id, EntityPlayer who) {
        switch (id) {
            case "m_estop" -> { ctl.set(id, ctl.get(id) == 1 ? 0 : 1); if (ctl.get(id) == 1) run = false; return ctl.get(id) == 1 ? "§cE-STOP - pump stopped, valve shut" : "§aE-stop released"; }
            case "m_ground", "m_arm" -> {
                if (run) return "§cStop the transfer first";
                ctl.set(id, ctl.get(id) == 1 ? 0 : 1);
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ctl.lever"), net.minecraft.util.SoundCategory.BLOCKS, 0.8f, 0.8f);
                return id.equals("m_ground") ? (ctl.get(id) == 1 ? "§eGround clamp on - verifying" : "§7Ground clamp off") : (ctl.get(id) == 1 ? "§eArm lowered into the dome" : "§7Arm raised and stowed");
            }
            case "k_mode" -> { if (run) return "§cStop first"; return null; }
            default -> { return null; }
        }
    }

    @Override
    protected void work() {
        if (age % 20 != 0) return;
        FreightTank c = Cars.tank(world, pos, 7);
        TileOilTank t = tank();
        if (run && (!powered() || !mainsOk() || !permissive(c) || t == null)) { run = false; why = c == null ? "car moved" : "permissive lost"; }
        if (run) {
            int moved = 0;
            if (unloading()) {
                if (!t.inletOpen() || t.highHigh()) { run = false; why = "tank inlet shut / HIGH-HIGH"; }
                else {
                    cam72cam.mod.fluid.Fluid fl = c.getLiquid();
                    int want = Math.min(F6Config.rackRate, Math.min(c.getLiquidAmount(), preset - metered));
                    if (fl == null || want <= 0) { run = false; why = want <= 0 && metered >= preset ? "preset reached" : "car empty"; }
                    else {
                        int room = t.tank().fill(new FluidStack(fl.internal, want), false);
                        cam72cam.mod.fluid.FluidStack got = room <= 0 ? null : c.theTank.drain(new cam72cam.mod.fluid.FluidStack(fl, room), false);
                        if (got != null && got.getAmount() > 0) { moved = t.tank().fill(new FluidStack(fl.internal, got.getAmount()), true); t.inTotal += moved; }
                        if (moved <= 0) { run = false; why = "tank will not take " + t.product(); }
                    }
                }
            } else {
                if (!t.outletOpen()) { run = false; why = "tank outlet shut"; }
                else if (c.getPercentLiquidFull() >= 98) { run = false; why = "overfill probe"; }
                else {
                    FluidStack out = t.tank().drain(Math.min(F6Config.rackRate, preset - metered), false);
                    cam72cam.mod.fluid.Fluid fl = out == null ? null : cam72cam.mod.fluid.Fluid.getFluid(out.getFluid());
                    int got = fl == null || out.amount <= 0 ? 0 : c.theTank.fill(new cam72cam.mod.fluid.FluidStack(fl, out.amount), false);
                    if (got > 0) { t.tank().drain(got, true); t.outTotal += got; moved = got; }
                    if (got <= 0) { run = false; why = metered >= preset ? "preset reached" : out == null ? "tank empty" : "car full"; }
                }
            }
            metered += moved; total += moved;
            if (!run) {
                Recorder.event(world, String.format(Locale.ROOT, "Loading rack %d,%d: %s %s %d L (%s)", pos.getX(), pos.getZ(), car, unloading() ? "unloaded" : "loaded", metered, why));
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.BEEP_OK, net.minecraft.util.SoundCategory.BLOCKS, 0.5f, 1f);
            }
        }
        // vapour at the rack's gas detector: loading without vapour recovery vents the car's ullage
        if (run && !unloading() && ctl.get("k_vru") == 0) vapour = Math.min(100, vapour + 4.5f);
        else vapour = Math.max(0, vapour - 3f);
        if (F6Config.vapourIgnition && vapour >= 50 && world.rand.nextInt(20) == 0 && world.isAirBlock(pos.up(2))) {
            world.setBlockState(pos.up(2), Blocks.FIRE.getDefaultState());
            AlarmManager.get(world).raise(world, "oil:ign:" + pos.toLong(), AlarmManager.Cat.FIRE, AlarmManager.Sev.CRITICAL, "Loading rack at " + pos.getX() + "," + pos.getZ() + ": VAPOUR IGNITED", pos, false);
        }
        put("pwr", powered()); put("mainsl", mains.ok()); put("runl", run); put("perm", permissive(c)); put("groundl", grounded()); put("arml", armDown());
        put("carl", c != null); put("esdl", esd); put("vrul", ctl.get("k_vru") == 1); put("ofl", c != null && c.getPercentLiquidFull() >= 98); put("gasl", vapour >= 10);
        put("carg", c == null ? 0 : c.getPercentLiquidFull()); put("tankg", t == null ? 0 : t.pct()); put("lelg", Math.round(vapour)); put("flowg", run ? 80 : 0);
        text("litres", String.valueOf(metered));
        text("preset", preset >= 200000 ? "FULL" : String.valueOf(preset));
        text("line1", c == null ? "NO CAR AT RACK" : cut(Cars.name(c).toUpperCase(Locale.ROOT), 18));
        text("line2", (unloading() ? "UNLOAD > T" : "LOAD < T") + (tankNo() + 1) + " " + (t == null ? "NONE" : cut(t.product().toUpperCase(Locale.ROOT), 9)));
        text("line3", run ? "PUMPING" : esd ? "ESD LOCKOUT" : why.isEmpty() ? (permissive(c) ? "PERMISSIVE OK" : "NO PERMISSIVE") : why.toUpperCase(Locale.ROOT));
    }

    @Override protected void saveExtra(NBTTagCompound t) {
        t.setBoolean("run", run); t.setBoolean("esd", esd); t.setInteger("preset", preset); t.setInteger("met", metered); t.setInteger("total", total);
        t.setFloat("vap", vapour); t.setString("car", car); t.setString("why", why);
    }
    @Override protected void loadExtra(NBTTagCompound t) {
        run = t.getBoolean("run"); esd = t.getBoolean("esd"); preset = t.hasKey("preset") ? t.getInteger("preset") : 20000; metered = t.getInteger("met"); total = t.getInteger("total");
        vapour = t.getFloat("vap"); car = t.getString("car"); why = t.getString("why");
    }

    @Override public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() { return new net.minecraft.util.math.AxisAlignedBB(pos).grow(2, 3, 2); }
}
