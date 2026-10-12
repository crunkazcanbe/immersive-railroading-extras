package com.dogpound.railmap.weather;

import com.dogpound.railmap.ohle.Reg;
import com.dogpound.railmap.safety.SafetyTile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Points Heater cabinet (her ChatGPT list #63: "switch heaters consume electricity", "frozen points can fail to move").
 * The control cabinet for the electric strip heaters clamped to the stock rails of every switch within 12 blocks:
 * AUTO / ON / OFF; in AUTO the thermostat (air at or below +3 °C, or snow on the sensor, or ice) switches the
 * contactor in. Heating draws 12 kW from any mod's cable beside it, else the overhead line through its auxiliary
 * transformer, else a substation. 12 V for the controls; the earth-leakage breaker trips if the cabinet floods.
 */
public class TilePointsHeater extends SafetyTile {
    static final Reg<TilePointsHeater> REG = new Reg<>();
    static final String[] MODES = {"AUTO", "ON", "OFF"};
    private boolean heating, rcd;
    private double kwh;
    private String src = "";

    @Override public void onLoad() { REG.add(this); }
    @Override public void invalidate() { super.invalidate(); REG.remove(this); }
    @Override public void onChunkUnload() { REG.remove(this); }

    /** a switch here is being heated */
    static boolean heating(World w, BlockPos sw) {
        for (TilePointsHeater t : REG.all(w)) {
            BlockPos p = t.getPos();
            double dx = p.getX() - sw.getX(), dz = p.getZ() - sw.getZ();
            if (t.heating && dx * dx + dz * dz <= 12 * 12 && Math.abs(p.getY() - sw.getY()) < 6) return true;
        }
        return false;
    }

    @Override public String wiredName() { return "Points heater " + pos.getX() + "," + pos.getZ(); }
    @Override public String gearState() { return MODES[ctl.get("s_mode")] + (rcd ? " · RCD TRIPPED" : heating ? " · HEATING 12 kW (" + src + ")" : " · off") + String.format(java.util.Locale.ROOT, " · %.1f kWh", kwh); }
    @Override public int wiredLamp() { return !powered() || rcd ? 3 : heating ? 1 : 2; }

    @Override
    protected void serverTick() {
        if (age % 20 != 13) return;
        WeatherData.Cell c = Weather.cell(world, pos);
        if (c.flood() > 100 && !rcd) { rcd = true; com.dogpound.railmap.alarm.Recorder.event(world, wiredName() + ": earth-leakage trip, cabinet flooded"); }
        int mode = ctl.get("s_mode");
        boolean demand = mode == 1 || mode == 0 && (c.air <= 3 || c.snow > 5 || c.ice > 0.2);
        boolean want = powered() && !rcd && demand;
        double got = 0;
        String[] s = {""};
        if (want) got = Aux.draw(world, pos, 12000, s);
        heating = want && got > 0.5;
        src = want ? s[0] : "";
        if (heating) kwh += 12.0 * got / 3600;
        put("heat", heating);
        put("frost", c.air <= 0);
        put("snow", c.snow > 5);
        put("supply", got > 0.5);
        put("fault", rcd || want && got <= 0.5);
        put("pwr", powered());
        put("kw", heating ? (int) Math.round(12 * got) : 0);
        text("air", String.format(java.util.Locale.ROOT, "AIR %+.0f C", c.air));
        text("rail", String.format(java.util.Locale.ROOT, "RAIL %+.0f C", c.rail));
        text("src", rcd ? "RCD TRIP" : want ? s[0].toUpperCase(java.util.Locale.ROOT) : MODES[mode]);
        text("kwh", String.format(java.util.Locale.ROOT, "%.0f KWH", kwh));
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        switch (id) {
            case "test": return lampTest();
            case "s_mode":
                ctl.set("s_mode", (ctl.get("s_mode") + 1) % 3);
                return "§ePoints heating: " + MODES[ctl.get("s_mode")];
            case "rcd":
                if (Weather.floodMm(world, pos) > 100) return "§cStill under water - it trips again";
                rcd = false;
                return "§aEarth-leakage breaker reset";
            default: return "§7" + gearState();
        }
    }

    @Override protected void save(NBTTagCompound t) { t.setBoolean("rcd", rcd); t.setDouble("kwh", kwh); }
    @Override protected void load(NBTTagCompound t) { rcd = t.getBoolean("rcd"); kwh = t.getDouble("kwh"); }
}
