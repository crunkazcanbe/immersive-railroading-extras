package com.dogpound.railmap.weather;

import com.dogpound.railmap.ohle.Reg;
import com.dogpound.railmap.safety.SafetyTile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Locale;

/**
 * Lineside Weather Station (her ChatGPT list #66: "weather stations feed current conditions to the control center"):
 * cup anemometer, wind vane, tipping-bucket rain gauge, Stevenson screen thermometer, rail temperature probe, forward
 * scatter visibility sensor, snow depth sensor and an ice detector on the wire. Its cell's readings go to the Control
 * Center's weather page; its lamps flag frost, fog, heat, flood and snow, and with ALARMS on it raises them. 12 V.
 */
public class TileWeatherStation extends SafetyTile {
    public static final Reg<TileWeatherStation> REG = new Reg<>();

    @Override public void onLoad() { REG.add(this); }
    @Override public void invalidate() { super.invalidate(); REG.remove(this); }
    @Override public void onChunkUnload() { REG.remove(this); }

    @Override public String wiredName() { return "Weather station " + pos.getX() + "," + pos.getZ(); }
    @Override public String gearState() { WeatherData.Cell c = Weather.cell(world, pos); return String.format(Locale.ROOT, "air %.0f °C rail %.0f °C wind %.0f m/s vis %.0f m", c.air, c.rail, c.wind, c.vis); }
    @Override public void gearInfo(java.util.Map<String, Object> m) { WeatherData.Cell c = Weather.cell(world, pos); m.put("airC", c.air); m.put("railC", c.rail); m.put("windMs", c.wind); m.put("visM", c.vis); m.put("snowMm", c.snow); m.put("iceMm", c.ice); m.put("floodMm", c.flood()); }

    @Override
    protected void serverTick() {
        if (age % 20 != 15) return;
        WeatherData.Cell c = Weather.cell(world, pos);
        long now = world.getTotalWorldTime();
        text("air", String.format(Locale.ROOT, "%+.1f C", c.air));
        text("rail", String.format(Locale.ROOT, "%+.1f C", c.rail));
        text("wind", String.format(Locale.ROOT, "%.0f M/S", c.wind));
        text("vis", c.vis >= 9999 ? ">10 KM" : String.format(Locale.ROOT, "%.0f M", c.vis));
        text("rain", c.precip == 0 ? "DRY" : (c.snowing ? "SNOW " : "RAIN ") + new String[]{"", "LIGHT", "HEAVY", "STORM"}[c.precip]);
        text("snow", String.format(Locale.ROOT, "%.0f MM", c.snow));
        text("ice", String.format(Locale.ROOT, "%.1f MM", c.ice));
        text("flood", c.flood() > 0 ? String.format(Locale.ROOT, "%.0f MM", c.flood()) : String.format(Locale.ROOT, "-%.0f MM", 150 - c.water));
        Object[] r = Weather.restriction(world, c, now);
        text("esr", (double) r[0] < 0 ? "NO RESTRICTION" : (double) r[0] <= 0 ? "LINE CLOSED" : Math.round((double) r[0]) + " KMH " + r[1]);
        put("windg", (int) Math.min(40, c.wind));
        put("lfrost", c.air <= 0);
        put("lfog", c.vis < 300);
        put("lheat", c.rail >= Weather.heatLimit(c, now, 0));
        put("lflood", c.water > 150);
        put("lsnow", c.snow >= 50);
        put("lice", c.ice > 0.2);
        put("pwr", powered());
        put("w2", Weather.weather2());
        if (powered() && ctl.get("s_alarm") == 1) {
            Weather.alarmOnce(world, "wxst:fog:" + pos.toLong(), c.vis < 200, com.dogpound.railmap.alarm.AlarmManager.Cat.GENERAL, com.dogpound.railmap.alarm.AlarmManager.Sev.INFO, wiredName() + ": restricted visibility " + Math.round(c.vis) + " m (" + c.visWhy + ")", pos);
            Weather.alarmOnce(world, "wxst:frost:" + pos.toLong(), c.air <= 0, com.dogpound.railmap.alarm.AlarmManager.Cat.GENERAL, com.dogpound.railmap.alarm.AlarmManager.Sev.INFO, wiredName() + ": air frost " + Math.round(c.air) + " °C - points heating", pos);
        }
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        switch (id) {
            case "test": return lampTest();
            case "s_alarm": ctl.set("s_alarm", 1 - ctl.get("s_alarm")); return "§eWeather alarms " + (ctl.get("s_alarm") == 1 ? "ON" : "OFF");
            case "report": {
                if (!powered()) return "§cNo 12 V";
                String s = "§b[WEATHER " + pos.getX() + "," + pos.getZ() + "] " + gearState() + " · " + ptext("rain") + " · snow " + ptext("snow") + " · " + ptext("esr");
                for (EntityPlayer p : world.playerEntities) if (p.getDistanceSq(pos) < 256 * 256) p.sendMessage(new net.minecraft.util.text.TextComponentString(s));
                return null;
            }
            default: return "§7" + gearState();
        }
    }

    @Override protected void save(NBTTagCompound t) {}
    @Override protected void load(NBTTagCompound t) {}
}
