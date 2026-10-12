package com.dogpound.railmap.infra;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.ohle.Reg;
import com.dogpound.railmap.planning.PlanData;
import com.dogpound.railmap.planning.TempWorks;
import com.dogpound.railmap.weather.TileCulvert;
import com.dogpound.railmap.weather.TilePumpStation;
import com.dogpound.railmap.weather.Weather;
import com.dogpound.railmap.weather.WeatherData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;

import java.util.Locale;
import java.util.Map;

/**
 * Flood Warning Outstation (her ChatGPT list batch 3, #43 - extends the drainage of #64: culverts, pump stations,
 * weather cells): an ultrasonic level sensor over the trackside drain with a staff gauge, beacon and siren, reporting by
 * telemetry. It reads the water in the track formation of its 64-block weather cell, keeps five minutes of history for
 * the RATE OF RISE, and predicts the minutes until the water reaches the rail head. ALERT at InfraConfig.floodAlertMm
 * (or rising fast), ALARM at the rail head (floodAlarmMm), FLOOD over it - each a real alarm, the siren and beacon
 * with it (MUTE silences the siren). TSR puts a precautionary 20 km/h flood-watch speed restriction over the cell (a
 * real temporary-works TSR the trains obey) or lifts it; PUMPS forces every pump station in the cell to run by hand for
 * ten minutes. The console's drainage page shows every cell, culvert, pump and sensor. 12 V (battery outstation).
 */
public class TileFloodSensor extends InfraTile {
    static final Reg<TileFloodSensor> REG = new Reg<>();
    static final String[] LEVELS = {"NORMAL", "ALERT", "ALARM", "FLOOD"};
    private final double[] hist = new double[15];     // water mm every 20 s, 5 minutes
    private int hi, level;
    private long forcedUntil;
    private double rate, eta = -1, water;

    public TileFloodSensor() { super(null); }

    @Override public void onLoad() { REG.add(this); }
    @Override public void invalidate() { super.invalidate(); REG.remove(this); }
    @Override public void onChunkUnload() { REG.remove(this); }

    @Override public String wiredName() { return "Flood sensor " + pos.getX() + "," + pos.getZ(); }

    @Override
    public String gearState() {
        return term.status().replaceAll("§.", "") + String.format(Locale.ROOT, " · %s · water %.0f mm · rising %.1f mm/min%s", LEVELS[level], water, rate, eta >= 0 ? String.format(Locale.ROOT, " · rail head in %.0f min", eta) : "");
    }

    @Override public int wiredLamp() { return !powered() ? 3 : level >= 2 ? 3 : level == 1 ? 2 : 1; }

    @Override public void gearInfo(Map<String, Object> m) { m.put("waterMm", Math.round(water)); m.put("riseMmMin", Math.round(rate * 10) / 10.0); m.put("level", LEVELS[level]); }

    int level() { return level; }
    double rate() { return rate; }
    double eta() { return eta; }

    @Override
    protected void serverTick() {
        long now = world.getTotalWorldTime();
        if (age % 20 != 17) return;
        if (!Weather.on()) { text("lvl", "WEATHER OFF"); put("pwr", powered()); return; }
        WeatherData.Cell c = Weather.cell(world, pos);
        water = c.water;
        if (age % 400 == 17) { hist[hi % hist.length] = water; hi++; }
        int n = Math.min(hi, hist.length);
        double oldest = n == 0 ? water : hist[hi >= hist.length ? hi % hist.length : 0];
        double span = Math.max(1, n) * 20 / 60.0;                                       // minutes covered
        rate = n < 2 ? 0 : (water - oldest) / span;
        eta = rate > 0.1 && water < InfraConfig.floodAlarmMm ? (InfraConfig.floodAlarmMm - water) / rate : water >= InfraConfig.floodAlarmMm ? 0 : -1;
        int was = level;
        level = water >= InfraConfig.floodAlarmMm + 75 ? 3 : water >= InfraConfig.floodAlarmMm ? 2 : water >= InfraConfig.floodAlertMm || rate > 2 && water > 10 ? 1 : 0;
        if (!powered()) level = Math.min(level, was);                                    // a dead outstation reports nothing new
        else if (level != was) report(was);
        // forced pumping ends
        if (forcedUntil != 0 && now >= forcedUntil) { forcedUntil = 0; for (TilePumpStation p : pumps()) p.force(false); log("forced pumping ended - pumps back on their float switches"); }
        // siren
        if (powered() && level >= 2 && ctl.get("s_siren") == 0 && age % 60 == 17) world.playSound(null, pos, com.dogpound.railmap.ModSounds.KLAXON, SoundCategory.BLOCKS, 1.2f, 1f);
        int culv = 0, pumps = 0, runP = 0;
        for (TileCulvert t : TileCulvert.all(world)) if (same(t.getPos())) culv++;
        for (TilePumpStation p : pumps()) { pumps++; if (p.running(0) || p.running(1)) runP++; }
        Integer tsr = InfraData.get(world).floodTsr.get(c.key);
        boolean tsrOn = tsr != null && tsrLive(tsr);
        put("alert", level == 1); put("alarm", level == 2); put("flood", level == 3); put("bcn", level >= 2);
        put("pump", runP > 0); put("tsrl", tsrOn); put("pwr", powered()); put("mute", ctl.get("s_siren") == 1);
        put("g_lvl", (int) Math.round(Math.min(100, water / 3)));
        text("lvl", String.format(Locale.ROOT, "WATER %.0f MM", water));
        text("rate", String.format(Locale.ROOT, "RISE %+.1f MM/MIN", rate));
        text("eta", eta < 0 ? "RAIL HEAD: NOT RISING" : eta == 0 ? "OVER THE RAIL HEAD" : String.format(Locale.ROOT, "RAIL HEAD IN %.0f MIN", eta));
        text("cell", String.format(Locale.ROOT, "CULV %d PUMPS %d/%d", culv, runP, pumps));
        text("state", LEVELS[level] + (forcedUntil > now ? " FORCED" : ""));
    }

    private boolean same(BlockPos p) { return p.getX() >> 6 == pos.getX() >> 6 && p.getZ() >> 6 == pos.getZ() >> 6; }

    private java.util.List<TilePumpStation> pumps() {
        java.util.List<TilePumpStation> l = new java.util.ArrayList<>();
        for (TilePumpStation p : TilePumpStation.all(world)) if (same(p.getPos())) l.add(p);
        return l;
    }

    private boolean tsrLive(int id) {
        for (PlanData.Work k : PlanData.get(world).works) if (k.id == id) return k.phase != PlanData.Phase.CLEARED;
        return false;
    }

    private void report(int was) {
        AlarmManager am = AlarmManager.get(world);
        String k = "flood:" + pos.toLong();
        String msg = String.format(Locale.ROOT, "%s %s: water %.0f mm in the formation, rising %.1f mm/min%s", wiredName(), LEVELS[level], water, rate,
                eta > 0 ? String.format(Locale.ROOT, " - rail head in %.0f min", eta) : eta == 0 ? " - OVER THE RAIL HEAD" : "");
        if (level == 0) am.clear(k);
        else am.raise(world, k, AlarmManager.Cat.GENERAL, level >= 2 ? AlarmManager.Sev.MAJOR : AlarmManager.Sev.MINOR, msg, pos, true);
        if (level > was) com.dogpound.railmap.api.QuestEvents.fire(world, null, pos, "flood_warning", "level", LEVELS[level]);
        log(msg);
    }

    private void log(String s) {
        WeatherData.get(world).log(world, s);
        Recorder.event(world, s);
    }

    @Override
    public String controlPress(String id, EntityPlayer p) {
        switch (id) {
            case "test": return lampTest();
            case "s_siren": return null;
            case "ack": AlarmManager.get(world).clear("flood:" + pos.toLong()); return "§7Flood alarm acknowledged (it comes back if the level changes)";
            case "tsr": {
                if (!powered()) return "§cOutstation dead - no 12 V";
                WeatherData.Cell c = Weather.cell(world, pos);
                InfraData d = InfraData.get(world);
                Integer id0 = d.floodTsr.get(c.key);
                if (id0 != null && tsrLive(id0)) {
                    String r = TempWorks.remove(world, id0, who(p));
                    d.floodTsr.remove(c.key); d.markDirty();
                    return r;
                }
                PlanData.Work k = TempWorks.add(world, PlanData.Kind.TSR, "flood watch " + Weather.name(c), c.x0() + 32, pos.getY(), c.z0() + 32, 40, 20, 0, 0, who(p) + " (flood sensor)");
                d.floodTsr.put(c.key, k.id); d.markDirty();
                log("precautionary 20 km/h flood-watch TSR requested over " + Weather.name(c) + " by " + who(p));
                return "§6Flood-watch TSR 20 km/h requested (#" + k.id + ") - the crew puts the boards out";
            }
            case "pumps": {
                if (!powered()) return "§cOutstation dead - no 12 V";
                java.util.List<TilePumpStation> l = pumps();
                if (l.isEmpty()) return "§7No pump station in this drainage cell";
                for (TilePumpStation ps : l) ps.force(true);
                forcedUntil = world.getTotalWorldTime() + 12000;
                log("pumps forced to HAND for 10 min by " + who(p) + " (" + l.size() + " station(s))");
                return "§b" + l.size() + " pump station(s) forced to run for 10 minutes";
            }
            default: return "§7" + gearState();
        }
    }

    @Override
    protected void store(NBTTagCompound t) {
        t.setInteger("level", level); t.setLong("forced", forcedUntil); t.setInteger("hi", hi);
        int[] h = new int[hist.length];
        for (int i = 0; i < h.length; i++) h[i] = (int) Math.round(hist[i] * 10);
        t.setIntArray("hist", h);
    }

    @Override
    protected void restore(NBTTagCompound t) {
        level = t.getInteger("level"); forcedUntil = t.getLong("forced"); hi = t.getInteger("hi");
        int[] h = t.getIntArray("hist");
        for (int i = 0; i < Math.min(h.length, hist.length); i++) hist[i] = h[i] / 10.0;
    }

    /** DS x,z · water · rise · level · eta · forced */
    String[] row() {
        return new String[]{"DS", pos.getX() + "," + pos.getZ(), String.valueOf(Math.round(water)), String.format(Locale.ROOT, "%+.1f", rate), LEVELS[level],
                eta < 0 ? "-" : String.format(Locale.ROOT, "%.0f", eta), forcedUntil > world.getTotalWorldTime() ? "FORCED" : "", powered() ? "1" : "0"};
    }
}
