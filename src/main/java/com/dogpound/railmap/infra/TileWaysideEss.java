package com.dogpound.railmap.infra;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.grid.Elec;
import com.dogpound.railmap.grid.GridData;
import com.dogpound.railmap.ohle.EnergyDispatch;
import com.dogpound.railmap.ohle.Reg;
import com.dogpound.railmap.weather.Weather;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.WorldServer;

import java.util.Locale;
import java.util.Map;

/**
 * Wayside Energy Storage System (her ChatGPT list batch 3, #38): a containerised store beside the line that soaks up
 * the regenerative braking other trains can't use (on a DC line that energy would otherwise be burnt in the braking
 * resistors) and gives it back to accelerating trains. Its power conversion system (PCS) is wired to the traction
 * network like any grid machine - a cable / breaker / rectifier of the network TOUCHING the container, at 600 V - 3 kV
 * DC or 13.8 kV AC (anything higher burns the PCS out; LV locks it out) - and it serves the Track Feeders of that
 * network. Its HVAC and BMS run off the same supply (a real load) and 12 V control power.
 * <p>
 * Chemistry (selector, only when ISOLATED - it swaps the modules): LI-ION 400 kWh / 1.2 MW, SUPERCAP 25 kWh / 3 MW
 * (a million cycles), HYBRID 215 kWh / 2.5 MW. AUTO: absorbs regen, lops the feeder's load above the PEAK setting and
 * tops itself up off-peak; REGEN: absorbs, and only gives it back when the supply fails; SUPPORT: gives freely; OFF.
 * <p>
 * Real behaviour: losses heat the cells (I²R grows as they age), the HVAC fights it, over 45 °C (Li-ion) the power is
 * derated, over 60 °C the BMS trips; under 5 °C charging is limited (lithium plating); state of health falls with
 * equivalent full cycles (faster when hot - Arrhenius, x2 per 10 K) and with time at high charge; cells drift apart
 * (imbalance) until BALANCE is run with the store isolated. Thermal runaway is a harsh option (off).
 */
public class TileWaysideEss extends InfraTile {
    static final Reg<TileWaysideEss> REG = new Reg<>();
    static final String[] TECH = {"LI-ION", "SUPERCAP", "HYBRID"}, MODES = {"AUTO", "REGEN", "SUPPORT", "OFF"};
    static final double[] CAP_KWH = {400, 25, 215}, P_KW = {1200, 3000, 2500}, EFF = {0.95, 0.97, 0.96}, FADE = {5e-5, 1e-7, 3e-5},
            T_DERATE = {45, 60, 50}, T_TRIP = {60, 75, 65}, HEAT_CAP = {2.5e6, 0.8e6, 1.8e6};

    private double soc = 20, soh = 1, temp = 20, efc, imb = 2, kwhIn, kwhOut;
    private int peakKw = 1500;
    private long balanceUntil;
    private String trip = "";
    // this traction second (FE), last second (kW)
    private long accIn, accOut;
    private double inKw, outKw, hvacKw;
    private boolean hvacOn;

    public TileWaysideEss() { super(new Supply(Elec.Level.DC600, Elec.Level.DC750, Elec.Level.DC1500, Elec.Level.DC3000, Elec.Level.MV13800)); }

    @Override public void onLoad() { REG.add(this); }
    @Override public void invalidate() { super.invalidate(); REG.remove(this); }
    @Override public void onChunkUnload() { REG.remove(this); }

    int tech() { return Math.max(0, Math.min(2, ctl.get("s_tech"))); }
    int mode() { return Math.max(0, Math.min(3, ctl.get("s_mode"))); }
    boolean isolated() { return ctl.get("k_iso") == 1; }
    double usableKwh() { return CAP_KWH[tech()] * soh * (imb > 30 ? 1 - Math.min(0.3, (imb - 30) / 200) : 1); }
    /** the PCS can run: 12 V, mains, online, not tripped, not balancing */
    boolean online() { return powered() && mains.connected && !isolated() && trip.isEmpty() && mode() != 3 && world.getTotalWorldTime() >= balanceUntil; }
    GridData.Net net() { return mains.net; }

    /** power limit now, kW: thermal derating, cold charging */
    double limitKw(boolean charge) {
        int t = tech();
        double p = P_KW[t];
        if (temp > T_DERATE[t]) p *= Math.max(0, 1 - (temp - T_DERATE[t]) / (T_TRIP[t] - T_DERATE[t]));
        if (charge && t != 1) { if (temp < -20) p = 0; else if (temp < 5) p *= 0.1; }
        return p * Math.max(0.5, soh);
    }

    /** self-test / test commands: put the store in a state */
    void set(double tempC, double health, double socPct) { temp = tempC; soh = health; soc = socPct; }

    /** settle: regen this network can't use - returns FE taken */
    long absorb(long fe) {
        if (!online() || fe <= 0) return 0;
        long limit = (long) (limitKw(true) * 10) - accIn;                      // 1 FE/s = 0.1 kW
        long room = (long) ((usableKwh() * 0.95 - soc / 100 * usableKwh()) * 36000 / EFF[tech()]);
        long take = Math.max(0, Math.min(fe, Math.min(limit, room)));
        if (take <= 0) return 0;
        soc = Math.min(100, soc + take / 36000.0 * EFF[tech()] / Math.max(1e-6, usableKwh()) * 100);
        accIn += take;
        return take;
    }

    /** a train on this network wants fe: what the store gives (feederKw = the feeder's load last second) */
    long give(long fe, boolean gridOk, double feederKw) {
        if (!online() || fe <= 0) return 0;
        long cap;
        switch (mode()) {
            case 0: cap = !gridOk ? fe : feederKw > peakKw ? (long) ((feederKw - peakKw) * 10) : 0; break;   // peak lopping
            case 1: cap = gridOk ? 0 : fe; break;                                                              // backup only
            case 2: cap = fe; break;
            default: cap = 0;
        }
        long limit = (long) (limitKw(false) * 10) - accOut;
        long avail = (long) (Math.max(0, soc - 5) / 100 * usableKwh() * 36000 * EFF[tech()]);
        long g = Math.max(0, Math.min(Math.min(fe, cap), Math.min(limit, avail)));
        if (g <= 0) return 0;
        soc = Math.max(0, soc - g / 36000.0 / EFF[tech()] / Math.max(1e-6, usableKwh()) * 100);
        accOut += g;
        return g;
    }

    @Override public String wiredName() { return "Wayside ESS " + pos.getX() + "," + pos.getZ(); }

    @Override
    public String gearState() {
        return String.format(Locale.ROOT, "%s %s · SOC %.0f%% of %.0f kWh · SOH %.1f%% · %.1f C · %s%s", TECH[tech()], MODES[mode()], soc, usableKwh(), soh * 100, temp,
                inKw > 1 ? "charging " + Math.round(inKw) + " kW" : outKw > 1 ? "giving " + Math.round(outKw) + " kW" : "idle", trip.isEmpty() ? "" : " · TRIP " + trip) + " · " + mains.status().replaceAll("§.", "");
    }

    @Override public int wiredLamp() { return !live() || !trip.isEmpty() ? 3 : temp > T_DERATE[tech()] || soh < 0.8 || imb > 50 ? 2 : 1; }

    @Override
    public void gearInfo(Map<String, Object> m) {
        m.put("soc", Math.round(soc)); m.put("soh", Math.round(soh * 1000) / 10.0); m.put("tempC", Math.round(temp * 10) / 10.0); m.put("kwIn", Math.round(inKw)); m.put("kwOut", Math.round(outKw));
        m.put("tech", TECH[tech()]); m.put("mode", MODES[mode()]); m.put("trip", trip); m.put("efc", Math.round(efc * 10) / 10.0);
    }

    @Override
    protected void serverTick() {
        if (age % 20 != 13) return;
        long now = world.getTotalWorldTime();
        int t = tech();
        double amb = Weather.airC(world, pos);
        // the second that just ended
        inKw = accIn / 10.0; outKw = accOut / 10.0;
        double kwh = (accIn + accOut) / 36000.0;
        kwhIn += accIn / 36000.0; kwhOut += accOut / 36000.0;
        accIn = 0; accOut = 0;
        // HVAC + BMS off the mains (a real load), heating losses in the cells
        int hv = ctl.get("s_hvac");
        boolean wantCool = hv == 1 ? temp > 18 : hv == 0 && temp > 28, wantHeat = hv != 2 && temp < 5;
        double hvacW = wantCool ? 40000 / 3.0 : wantHeat ? 10000 : 0;      // COP 3 chiller / resistive heater
        double got = mainsTick(1500 + hvacW);
        hvacOn = (wantCool || wantHeat) && got > 0.5 && powered();
        hvacKw = hvacOn ? hvacW / 1000 : 0;
        double lossW = (inKw + outKw) * 1000 * (1 - EFF[t]) * (1 + (1 - soh) * 2);
        double coolW = (temp - amb) * 300 + (hvacOn && wantCool ? 40000 * Math.min(1, (temp - 18) / 6) : 0) - (hvacOn && wantHeat ? 10000 : 0);
        temp += (lossW - coolW) / HEAT_CAP[t];
        // ageing
        if (kwh > 0) efc += kwh / (2 * CAP_KWH[t]);
        if (InfraConfig.essDegradation) {
            double arr = Math.pow(2, (temp - 25) / 10);
            soh -= FADE[t] * kwh / (2 * CAP_KWH[t]) * arr * (soc > 90 ? 1.3 : 1);
            soh -= 2e-9 * arr * (soc > 90 ? 2 : 1);                                       // calendar ageing
            if (t != 1) imb += kwh * 0.002;
            soh = Math.max(0.3, soh);
        }
        // balancing finished
        if (balanceUntil != 0 && now >= balanceUntil) { balanceUntil = 0; imb = 2; log("cell balancing complete - spread 2 mV"); }
        // protection
        if (trip.isEmpty() && temp > T_TRIP[t]) { trip = "OVERTEMP"; log("BMS TRIP: cells " + f1(temp) + " C"); }
        if (trip.isEmpty() && imb > 120) { trip = "CELL IMBALANCE"; log("BMS TRIP: cell spread " + Math.round(imb) + " mV"); }
        if (InfraConfig.essThermalRunaway && t != 1 && temp > 80 && soh < 0.7 && world.rand.nextDouble() < 0.05) runaway();
        // off-peak top-up from the grid (AUTO)
        if (online() && mode() == 0 && EnergyDispatch.band(EnergyDispatch.hour(world)) == 0 && soc < 60 && mains.net != null) {
            long want = (long) Math.min(limitKw(true) * 0.25 * 10, (60 - soc) / 100 * usableKwh() * 36000);
            long drew = GridData.get(world).draw(mains.net, want);
            soc = Math.min(100, soc + drew / 36000.0 * EFF[t] / Math.max(1e-6, usableKwh()) * 100);
            inKw += drew / 10.0;
        }
        alarms();
        // the HMI
        put("g_soc", (int) Math.round(soc));
        put("g_temp", (int) Math.round(Math.max(0, Math.min(80, temp))));
        put("chg", inKw > 1); put("dis", outKw > 1);
        put("hot", temp > T_DERATE[t]); put("bms", !trip.isEmpty()); put("eol", soh < 0.8);
        put("balr", balanceUntil > now); put("hvac", hvacOn); put("trip", !trip.isEmpty());
        put("pcs", mains.ok); put("pwr", powered());
        text("soc", String.format(Locale.ROOT, "SOC %.0f%% %.0f KWH", soc, soc / 100 * usableKwh()));
        text("pwrk", inKw > 1 ? String.format(Locale.ROOT, "+%.0f KW IN", inKw) : outKw > 1 ? String.format(Locale.ROOT, "-%.0f KW OUT", outKw) : "0 KW IDLE");
        text("temp", String.format(Locale.ROOT, "CELLS %.1f C AMB %.0f", temp, amb));
        text("soh", String.format(Locale.ROOT, "SOH %.1f%% %.0f EFC", soh * 100, efc));
        text("imb", String.format(Locale.ROOT, "SPREAD %.0f MV", imb));
        text("peak", "PEAK " + peakKw + " KW");
        text("pcsv", mains.ok ? mains.level.label.toUpperCase(Locale.ROOT).replace("φ", "").replace("Φ", "") : trip.isEmpty() ? "PCS: NO SUPPLY" : "TRIP " + trip);
        text("mode", TECH[t] + " " + MODES[mode()]);
        markDirty();
    }

    private void alarms() {
        AlarmManager am = AlarmManager.get(world);
        String k = "ess:" + pos.toLong();
        alarm(am, k + ":trip", !trip.isEmpty(), AlarmManager.Sev.MAJOR, wiredName() + " BMS TRIP: " + trip + " - reset at the HMI once safe");
        alarm(am, k + ":hot", trip.isEmpty() && temp > T_DERATE[tech()], AlarmManager.Sev.MINOR, wiredName() + " cells hot (" + f1(temp) + " C) - power derated, check the HVAC");
        alarm(am, k + ":eol", soh < 0.8, AlarmManager.Sev.MINOR, wiredName() + " state of health " + f1(soh * 100) + "% - end of life, replace the modules");
        alarm(am, k + ":imb", imb > 50 && trip.isEmpty(), AlarmManager.Sev.MINOR, wiredName() + " cell imbalance " + Math.round(imb) + " mV - isolate it and run BALANCE");
    }

    private void alarm(AlarmManager am, String key, boolean on, AlarmManager.Sev sev, String text) {
        if (!on) { am.clear(key); return; }
        for (AlarmManager.Alarm a : am.all()) if (a.key.equals(key) && a.active) return;
        am.raise(world, key, AlarmManager.Cat.MACHINE, sev, text, pos, true);
    }

    private void runaway() {
        trip = "THERMAL RUNAWAY";
        soh = 0.3; soc = 0;
        if (world instanceof WorldServer ws) ws.spawnParticle(EnumParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 60, 0.5, 0.6, 0.5, 0.03);
        world.playSound(null, pos, com.dogpound.railmap.ModSounds.FIRE_BELL, SoundCategory.BLOCKS, 1f, 1f);
        AlarmManager.get(world).raise(world, "ess:" + pos.toLong() + ":fire", AlarmManager.Cat.FIRE, AlarmManager.Sev.CRITICAL, wiredName() + " THERMAL RUNAWAY - battery fire, keep clear", pos, true);
        log("THERMAL RUNAWAY - battery fire");
    }

    private void log(String s) {
        InfraData.get(world).log(world, wiredName() + ": " + s);
        Recorder.event(world, wiredName() + ": " + s);
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        switch (id) {
            case "test": return lampTest();
            case "s_tech": {
                if (!isolated()) return "§cIsolate the store (key) before changing the modules";
                ctl.set("s_tech", (tech() + 1) % 3);
                soh = 1; soc = 20; efc = 0; imb = 2; trip = "";
                log("modules changed to " + TECH[tech()] + " by " + who(who));
                return "§e" + TECH[tech()] + " modules fitted (" + Math.round(CAP_KWH[tech()]) + " kWh, " + Math.round(P_KW[tech()]) + " kW) - new, 20% charged";
            }
            case "s_mode": return null;
            case "s_hvac": return null;
            case "k_iso":
                if (who == null) return "§cThe isolation key is turned at the container";
                ctl.set("k_iso", 1 - ctl.get("k_iso"));
                log((isolated() ? "ISOLATED" : "back ONLINE") + " by " + who.getName());
                return isolated() ? "§ePCS ISOLATED - DC contactors open" : "§aPCS ONLINE";
            case "balance":
                if (!isolated()) return "§cIsolate the store first - balancing runs offline";
                if (tech() == 1) return "§7Supercapacitors balance passively";
                balanceUntil = world.getTotalWorldTime() + 1200;
                log("cell balancing started by " + who(who));
                return "§bBalancing the cells (60 s)";
            case "reset":
                if (trip.isEmpty()) return "§7No trip to reset";
                if (temp > T_TRIP[tech()] - 10 || imb > 100) return "§cStill unsafe: " + f1(temp) + " C, spread " + Math.round(imb) + " mV";
                if (trip.equals("THERMAL RUNAWAY")) return "§cThe modules burnt - change them (isolate, then MODULES selector)";
                log("trip " + trip + " reset by " + who(who));
                trip = "";
                return "§aBMS reset";
            case "pk_up": peakKw = Math.min(8000, peakKw + 250); return "§ePeak lopping above " + peakKw + " kW";
            case "pk_dn": peakKw = Math.max(250, peakKw - 250); return "§ePeak lopping above " + peakKw + " kW";
            default: return "§7" + gearState();
        }
    }

    @Override
    protected void store(NBTTagCompound t) {
        t.setDouble("soc", soc); t.setDouble("soh", soh); t.setDouble("temp", temp); t.setDouble("efc", efc); t.setDouble("imb", imb);
        t.setDouble("kin", kwhIn); t.setDouble("kout", kwhOut); t.setInteger("peak", peakKw); t.setLong("bal", balanceUntil); t.setString("trip", trip);
    }

    @Override
    protected void restore(NBTTagCompound t) {
        if (!t.hasKey("soh")) return;
        soc = t.getDouble("soc"); soh = t.getDouble("soh"); temp = t.getDouble("temp"); efc = t.getDouble("efc"); imb = t.getDouble("imb");
        kwhIn = t.getDouble("kin"); kwhOut = t.getDouble("kout"); peakKw = t.getInteger("peak"); balanceUntil = t.getLong("bal"); trip = t.getString("trip");
    }

    /** console row: ES x,z · tech · mode · SOC % · usable kWh · SOH % · temp · kW in · kW out · EFC · state · supply */
    String[] row() {
        return new String[]{"ES", pos.getX() + "," + pos.getZ(), TECH[tech()], MODES[mode()], String.valueOf(Math.round(soc)), String.valueOf(Math.round(usableKwh())), f1(soh * 100), f1(temp),
                String.valueOf(Math.round(inKw)), String.valueOf(Math.round(outKw)), f1(efc), !trip.isEmpty() ? "TRIP " + trip : isolated() ? "ISOLATED" : !live() ? "NO SUPPLY" : balanceUntil > world.getTotalWorldTime() ? "BALANCING" : "ONLINE",
                mains.what, String.format(Locale.ROOT, "%.0f/%.0f", kwhIn, kwhOut), String.valueOf(pos.toLong())};
    }
}
