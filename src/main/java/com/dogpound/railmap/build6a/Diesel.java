package com.dogpound.railmap.build6a;

import com.dogpound.railmap.wire.Terminal;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

import java.util.Locale;

/**
 * The on-board power of a self-propelled works machine (track layer, tamper): a diesel engine (the factory's diesel or
 * any fluid called diesel, piped into its tank) driving the hydraulics and a 24 V alternator, and the 12 V control
 * battery behind an isolator. The engine cranks only off a battery with charge in it; running, it charges the battery.
 * With the isolator ON and charge left the machine's 12 V terminal is live even where no 12 V pair is laid (it is
 * a vehicle); the 12 V pair, when laid to it, works too.
 */
public final class Diesel {
    public static final int TANK = 16000;
    public final FluidTank tank = new FluidTank(TANK) {
        @Override public boolean canFillFluidType(FluidStack f) { return isDiesel(f); }
    };
    public float battery = 80, rpm, oilBar, coolC = 20;
    public boolean running;
    public String why = "";

    public static boolean isDiesel(FluidStack f) {
        if (f == null || f.getFluid() == null) return false;
        Fluid fl = f.getFluid();
        return fl.getName().toLowerCase(Locale.ROOT).contains("diesel");
    }

    /**
     * Once a second. isolator: battery switch ON; key: engine key at RUN; stop: E-stop / shutdown; working: the
     * hydraulics are under load. Feeds the 12 V terminal from the battery. Returns true when running changed.
     */
    public boolean tick(Terminal term, boolean isolator, boolean key, boolean stop, boolean working) {
        boolean was = running;
        if (!isolator) { running = false; why = "battery isolator OFF"; }
        else if (stop || !key) { if (running) why = stop ? "engine stopped (E-stop)" : "engine stopped (key OFF)"; running = false; }
        else if (!running) {
            if (battery < 15) why = "battery flat - the engine won't crank";
            else if (tank.getFluidAmount() <= 0) why = "no diesel in the tank";
            else { running = true; battery -= 4; why = "engine running"; }
        }
        if (running) {
            int burn = B6Config.dieselIdleMb * (working ? 4 : 1);
            FluidStack got = burn > 0 ? tank.drain(burn, true) : null;
            if (burn > 0 && (got == null || got.amount < burn)) { running = false; why = "ran out of diesel"; }
            battery = Math.min(100, battery + 0.6f);
            rpm += ((working ? 1850 : 750) - rpm) * 0.5f;
            oilBar += ((working ? 5.2f : 2.6f) - oilBar) * 0.5f;
            coolC += ((working ? 88 : 72) - coolC) * 0.05f;
        } else {
            rpm *= 0.3f; oilBar *= 0.3f; coolC += (20 - coolC) * 0.01f;
            if (isolator) battery = Math.max(0, battery - 0.01f);   // the controls and screens on the battery
        }
        if (isolator && battery > 3 && term != null) term.backup = 2;   // on-board 12 V
        return was != running;
    }

    public int fuelPct() { return tank.getFluidAmount() * 100 / TANK; }

    public void write(NBTTagCompound t) {
        t.setTag("tank", tank.writeToNBT(new NBTTagCompound()));
        t.setFloat("bat", battery); t.setBoolean("run", running); t.setFloat("cool", coolC); t.setString("dwhy", why);
    }

    public void read(NBTTagCompound t) {
        tank.readFromNBT(t.getCompoundTag("tank"));
        battery = t.hasKey("bat") ? t.getFloat("bat") : 80; running = t.getBoolean("run"); coolC = t.hasKey("cool") ? t.getFloat("cool") : 20; why = t.getString("dwhy");
    }
}
