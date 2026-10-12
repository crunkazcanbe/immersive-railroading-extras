package com.dogpound.railmap.station;

import com.dogpound.railmap.depot.DepotTile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Waiting Shelter Heater (her ChatGPT list #85, heating): an overhead radiant heater with its thermostat. AUTO heats
 * when the shelter is cold (a cold biome, night, rain or snow), ON / OFF by hand; the dial shows the shelter
 * temperature rising while it runs. A warm shelter on a cold night counts double for the station's facilities; it
 * costs its power while it runs. Needs mains and 12 V.
 */
public class TilePlatformHeater extends DepotTile implements IStationFacility {
    private boolean on;
    private double temp = 12;
    private int runSec;

    @Override public String wiredName() { return "Shelter heater"; }
    @Override protected boolean usesMains() { return true; }
    @Override protected long load() { return on ? 15 : 0; }
    @Override public String gearState() { return (on ? "heating" : "off") + String.format(java.util.Locale.ROOT, " · %.0f C", temp); }
    @Override public String facilityKind() { return "Shelter heating"; }
    @Override public boolean facilityWorking() { return powered() && mainsOk(); }
    @Override public int facilityPoints() { return cold() ? (on ? 6 : 0) : 2; }
    @Override public long dailyCost() { return 20 + runSec / 30; }
    @Override public long takeIncome() { runSec = 0; markDirty(); return 0; }

    double outside() {
        float b = world.getBiome(pos).getTemperature(pos);
        long t = world.getWorldTime() % 24000;
        return b * 25 - (t > 12500 && t < 23500 ? 6 : 0) - (world.isRaining() ? 4 : 0);
    }

    boolean cold() { return outside() < 14; }

    @Override protected String press(String id, EntityPlayer who) { return null; }

    @Override
    protected void work() {
        if (age % 20 != 0) return;
        int mode = Math.floorMod(ctl.get("s_mode"), 3);
        on = facilityWorking() && (mode == 1 || mode == 0 && cold());
        double target = outside() + (on ? 12 : 0);
        temp += (target - temp) * 0.08;
        if (on) runSec++;
        put("pwr", powered()); put("mainsl", mains.ok()); put("heat", on); put("coldl", cold());
        put("temp", (int) Math.round(Math.max(0, temp)));
        text("tempd", String.format(java.util.Locale.ROOT, "%.0f", temp));
    }

    @Override protected void saveExtra(NBTTagCompound t) { t.setDouble("temp", temp); t.setInteger("run", runSec); }
    @Override protected void loadExtra(NBTTagCompound t) { temp = t.hasKey("temp") ? t.getDouble("temp") : 12; runSec = t.getInteger("run"); }
}
