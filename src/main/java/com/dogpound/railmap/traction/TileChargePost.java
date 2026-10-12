package com.dogpound.railmap.traction;

import cam72cam.immersiverailroading.entity.LocomotiveDiesel;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.server.Electric;
import com.dogpound.railmap.server.RailProps;
import com.dogpound.railmap.steam.Stock;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;

import java.util.Locale;
import java.util.UUID;

/**
 * Train Charging Post (batch 5 #6): a charging plug post with its power-electronics cabinet (rectifier, DC/DC stage,
 * insulation monitor, cooling fans) for battery-electric trains - and any electric unit's battery. PLUG connects the
 * charging cable to the unit standing at the post; START charges it on the selected profile: ECO 150 kW, NORMAL 400 kW,
 * RAPID 1 000 kW - constant current to 80 %, then a constant-voltage taper to full. The power really comes off the IR
 * Extras grid cable feeding the post (400/480 V AC, 1 FE/s = 100 W): if the supply can't carry it the charger derates
 * itself. The kW meter, SoC bar and charge-profile screen are live; the cabinet heats with the power and its fans
 * cool it (over 85 °C it trips). A unit that drives off plugged in tears the plug out (alarm). Mains + 12 V.
 * (Battery trains running under live wire also charge from it: the OHLE charging section, Traction.chargeW.)
 */
public class TileChargePost extends com.dogpound.railmap.depot.DepotTile {
    static final String[] PROF = {"ECO", "NORMAL", "RAPID"};
    static final int[] PROF_KW = {150, 400, 1000};
    private UUID plugged;
    private boolean charging;
    private float kw, cabC = 25, derate = 1;
    private double session, total;
    private int startSoc;

    public TileChargePost() { super(new String[]{"cables"}, new String[]{"Spare charging cable + connector store"}, new int[]{9}); }

    @Override public String wiredName() { return "Train charging post"; }
    @Override protected boolean usesMains() { return true; }
    @Override protected long load() { return Math.round(kw * 10) + 3; }
    @Override public String gearState() { return !powered() ? "NO 12 V" : String.format(Locale.ROOT, "%s · %.0f kW · cabinet %.0f °C · %.0f kWh this session", charging ? "CHARGING" : plugged != null ? "PLUGGED" : "free", kw, cabC, session); }

    int prof() { return Math.floorMod(ctl.get("s_prof"), 3); }
    LocomotiveDiesel near() { return Stock.nearest(world, pos, 6, LocomotiveDiesel.class, l -> Electric.isElectric(l, RailProps.get(world).of(l.getUUID()))); }
    LocomotiveDiesel unit() { LocomotiveDiesel l = near(); return l != null && plugged != null && l.getUUID().equals(plugged) ? l : null; }

    @Override
    protected String press(String id, EntityPlayer who) {
        switch (id) {
            case "plug" -> {
                if (plugged != null) { if (charging) return "§cSTOP the charge before unplugging"; plugged = null; sound(0.8f); return "§7Plug out and stowed"; }
                LocomotiveDiesel l = near();
                if (l == null) return "§cNo electric / battery unit at the post";
                if (!Stock.standing(l)) return "§cThe unit must be standing";
                plugged = l.getUUID(); sound(1.2f);
                return "§aPlugged into " + Stock.name(l) + " (" + Traction.KIND[Traction.kind(l)] + ")";
            }
            case "start" -> {
                if (ctl.get("m_estop") == 1) return "§cE-STOP is in";
                LocomotiveDiesel l = unit();
                if (l == null) return "§cPLUG a unit in first";
                if (!mainsOk()) return "§cNo mains for the charger (" + mains.what + ")";
                if (!com.dogpound.railmap.cabinet.Cabinet.ok(ctl, "out")) return "§cThe DC OUT breaker in the cabinet is off";
                if (cabC > 75) return String.format(Locale.ROOT, "§cCabinet still hot (%.0f °C) - let the fans cool it", cabC);
                charging = true; derate = 1; session = 0;
                startSoc = Math.round(RailProps.get(world).of(l.getUUID()).battery * 100);
                return "§aCharging - " + PROF[prof()] + " profile";
            }
            case "stop" -> { boolean was = charging; charging = false; kw = 0; return was ? String.format(Locale.ROOT, "§eCharge stopped - %.1f kWh delivered", session) : "§7Not charging"; }
            default -> { return null; }
        }
    }

    void sound(float pitch) { world.playSound(null, pos, SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, SoundCategory.BLOCKS, 0.7f, pitch); }

    @Override
    protected String mechanical(String id, EntityPlayer who) {
        if (id.equals("m_estop")) { ctl.set(id, ctl.get(id) == 1 ? 0 : 1); if (ctl.get(id) == 1) { charging = false; kw = 0; } return ctl.get(id) == 1 ? "§cE-STOP - DC contactors open" : "§aE-stop released"; }
        return null;
    }

    @Override
    protected void work() {
        if (age % 20 != 0) return;
        LocomotiveDiesel near = near();
        if (plugged != null) {
            LocomotiveDiesel l = unit();
            if (l == null || !Stock.standing(l)) {                          // drove off with the plug in
                AlarmManager.get(world).raise(world, "plug:" + pos.toLong(), AlarmManager.Cat.MACHINE, AlarmManager.Sev.MAJOR, "Charging post at " + pos.getX() + "," + pos.getZ() + ": PLUG TORN OUT - unit moved while plugged in", pos, false);
                Recorder.event(world, "Charging post: plug torn out");
                plugged = null; charging = false; kw = 0;
            }
        }
        RailProps.Props pr = plugged == null ? null : RailProps.get(world).of(plugged);
        if (charging) {
            LocomotiveDiesel l = unit();
            if (l == null || !powered() || ctl.get("m_estop") == 1 || cabC > 85 || !com.dogpound.railmap.cabinet.Cabinet.ok(ctl, "out")) {
                if (cabC > 85) AlarmManager.get(world).raise(world, "chg:" + pos.toLong(), AlarmManager.Cat.MACHINE, AlarmManager.Sev.MINOR, "Charging post at " + pos.getX() + "," + pos.getZ() + ": CABINET OVERTEMP - tripped", pos, false);
                charging = false; kw = 0;
            } else if (!mainsOk()) {
                derate = Math.max(0.1f, derate * 0.5f);                    // supply can't carry it: back off
                kw = PROF_KW[prof()] * derate;
            } else {
                double took = Traction.charge(l, pr, PROF_KW[prof()] * derate);
                kw = (float) took;
                session += took / 3600; total += took / 3600;
                RailProps.get(world).markDirty();
                if (took < 1) {
                    charging = false; kw = 0;
                    Recorder.event(world, String.format(Locale.ROOT, "Charging post: %s charged %d%% -> 100%%, %.1f kWh", Stock.name(l), startSoc, session));
                    world.playSound(null, pos, com.dogpound.railmap.ModSounds.BEEP_OK, SoundCategory.BLOCKS, 0.5f, 1.0f);
                }
            }
        }
        boolean fans = cabC > 40 || charging;
        cabC += kw / 200f - (cabC - 25) * (fans ? 0.06f : 0.01f);
        if (cabC < 70) AlarmManager.get(world).clear("chg:" + pos.toLong());
        int soc = pr == null ? 0 : Math.round(pr.battery * 100);
        LocomotiveDiesel l = unit();
        double cap = l == null ? 0 : Traction.capKwh(l, Traction.kind(l));
        put("pwr", powered()); put("mainsl", mains.ok()); put("plugl", plugged != null); put("chgl", charging); put("full", pr != null && pr.battery >= 0.999f);
        put("fan", fans); put("hot", cabC > 70); put("der", charging && derate < 1); put("imd", powered()); put("locol", near != null); put("cv", charging && soc >= 80);
        for (int i = 1; i <= 10; i++) put("soc" + i, pr != null && soc >= i * 10 - 5);
        put("kwg", Math.round(kw * 100 / 1000)); put("tempg", Math.round(cabC)); put("socg", soc);
        text("kw", String.format(Locale.ROOT, "%.0f", kw));
        text("kwh", String.format(Locale.ROOT, "%.1f", session));
        text("line1", l == null ? (near == null ? "NO UNIT AT POST" : "NOT PLUGGED") : cut(Stock.name(l).toUpperCase(Locale.ROOT), 20));
        text("line2", l == null ? PROF[prof()] + " " + PROF_KW[prof()] + " KW" : String.format(Locale.ROOT, "%s SOC %d%% %.0f KWH", Traction.KIND[Traction.kind(l)], soc, cap));
        text("line3", !charging ? "PROFILE " + PROF[prof()] + " CC TO 80%" : soc < 80 ? String.format(Locale.ROOT, "CC %.0f KW TO 80%% %s", kw, eta(cap, pr.battery, 0.8f, kw)) : String.format(Locale.ROOT, "CV TAPER %.0f KW %s", kw, eta(cap, pr.battery, 1f, Math.max(1, kw))));
    }

    static String eta(double capKwh, float soc, float to, float kw) {
        if (kw < 1) return "";
        double min = Math.max(0, (to - soc) * capKwh / kw * 60);
        return min < 1 ? "" : String.format(Locale.ROOT, "%.0f MIN", min);
    }

    @Override protected void saveExtra(NBTTagCompound t) {
        if (plugged != null) { t.setLong("ph", plugged.getMostSignificantBits()); t.setLong("pl", plugged.getLeastSignificantBits()); }
        t.setBoolean("chg", charging); t.setFloat("cab", cabC); t.setDouble("ses", session); t.setDouble("tot", total); t.setInteger("ss", startSoc);
    }
    @Override protected void loadExtra(NBTTagCompound t) {
        plugged = t.hasKey("ph") ? new UUID(t.getLong("ph"), t.getLong("pl")) : null;
        charging = t.getBoolean("chg"); cabC = t.hasKey("cab") ? t.getFloat("cab") : 25; session = t.getDouble("ses"); total = t.getDouble("tot"); startSoc = t.getInteger("ss");
    }
}
