package com.dogpound.railmap.grid;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.safety.SafetyTile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Locale;
import java.util.Map;

/**
 * Harmonic Spectrum Meter (her "next 48" list #20): a panel-mounted power-quality instrument placed touching grid plant
 * (like the PQ analyser). Its screen is a real LED bar-graph spectrum - twelve columns (h3 .. h25), eight segments each,
 * green / amber / red against the selected limit - with a cursor that picks one harmonic for the numeric readout.
 * <ul>
 *   <li>MODE V / I: voltage distortion (what the network does to everyone) or the current the loads inject;</li>
 *   <li>LIMIT: IEEE 519 (8 % / 5 % THD by voltage, 5 % / 3 % per harmonic), ER G5/4, STRICT (3 % / 2 %);</li>
 *   <li>RANGE: full scale of a bar 1 / 2 / 5 / 10 %;</li>
 *   <li>H&lt; H&gt; move the cursor, HOLD freezes the display, RESET clears the peak memory, ACK clears the relay;</li>
 *   <li>LIMIT lamp + alarm relay: THD over the limit for 10 s raises an alarm; RES lights on a resonance peak and goes
 *       MAJOR when the network's own harmonics excite it.</li>
 * </ul>
 * 12 V for the instrument; with no 12 V the screen is dark and it can't alarm.
 */
public class TileSpectrumMeter extends SafetyTile {
    public static final int[] HS = {3, 5, 7, 9, 11, 13, 15, 17, 19, 21, 23, 25};
    static final String[] STD = {"IEEE 519", "G5/4", "STRICT"};
    static final double[] RANGE = {1, 2, 5, 10};
    private final double[] held = new double[30], peak = new double[30];
    private int over, cursor = 1, net = -1;
    private boolean relay, hold;

    @Override public String wiredName() { return "Harmonic spectrum meter " + Math.floorMod(pos.getX() * 13 + pos.getZ() * 7, 1000); }

    /** {THD limit %, individual harmonic limit %} for the selected standard on this level */
    double[] limits(GridData.Net n) {
        boolean lv = n != null && n.level != null && n.level.volts <= 1000;
        switch (ctl.get("s_std")) {
            case 1: return new double[]{5, 4};
            case 2: return new double[]{3, 2};
            default: return lv ? new double[]{8, 5} : new double[]{5, 3};
        }
    }

    @Override public String gearState() {
        if (!powered()) return "dark - no 12 V";
        return net < 0 ? "not touching any grid plant" : String.format(Locale.ROOT, "network #%d · THDv %.2f%% · THDi %.1f%%%s", net, held[26], held[29], relay ? " · LIMIT ALARM" : "");
    }
    @Override public int wiredLamp() { return !powered() ? 3 : relay ? 2 : 1; }
    @Override public void gearInfo(Map<String, Object> m) { m.put("network", net); m.put("thdV", Math.round(held[26] * 100) / 100.0); m.put("thdI", Math.round(held[29] * 10) / 10.0); m.put("resonanceH", Math.round(held[27] * 10) / 10.0); m.put("alarm", relay); }

    public double[] reading() { return held; }
    public int net() { return net; }

    @Override
    protected void serverTick() {
        if (age % 20 != 3) return;
        GridData d = GridData.get(world);
        GridData.Net n = PowerQuality.measured(d, pos.toLong());
        net = n == null ? -1 : n.id;
        boolean live = n != null && n.level != null && n.level.ac && n.problem.isEmpty() && powered();
        double[] v = live ? Harmonics.spectrum(world, n.id) : null, cur = live ? Harmonics.currents(world, n.id) : null;
        if (!hold) {
            for (int i = 0; i < held.length; i++) held[i] = 0;
            if (v != null) System.arraycopy(v, 0, held, 0, Math.min(held.length, v.length));
            if (cur != null && ctl.get("s_mode") == 1) for (int h = 2; h <= Harmonics.H; h++) held[h] = cur[h];   // MODE I: bars show current
        }
        for (int i = 0; i < held.length; i++) peak[i] = Math.max(peak[i], held[i]);
        double[] lim = limits(n);
        double fs = RANGE[Math.min(3, ctl.get("s_range"))];
        for (int k = 0; k < HS.length; k++) {
            int h = HS[k];
            int segs = (int) Math.min(8, Math.ceil(held[h] / fs * 8 - 0.001));
            for (int s = 1; s <= 8; s++) put("b" + h + "_" + s, segs >= s);
            put("c" + h, k == cursor);
        }
        boolean overNow = live && (held[26] > lim[0] || ctl.get("s_mode") == 0 && indiv(lim[1]));
        over = overNow ? over + 1 : 0;
        if (over >= 10 && !relay) {
            relay = true;
            Recorder.event(world, String.format(Locale.ROOT, "HARMONICS: spectrum meter %d,%d LIMIT - THDv %.2f%% (%s %.0f%%)", pos.getX(), pos.getZ(), held[26], STD[Math.min(2, ctl.get("s_std"))], lim[0]));
        }
        String key = "hm:" + pos.toLong();
        if (relay && overNow) Protection.standing(world, key, AlarmManager.Cat.POWER, AlarmManager.Sev.MINOR, String.format(Locale.ROOT, "HARMONICS over the %s limit on network #%d: THDv %.2f%% > %.0f%%%s", STD[Math.min(2, ctl.get("s_std"))], net, held[26], lim[0], worst(lim[1])), pos);
        else if (!overNow) AlarmManager.get(world).clear(key);
        boolean excited = live && n.resonance.contains("EXCITED");
        if (excited) Protection.standing(world, key + ":res", AlarmManager.Cat.POWER, AlarmManager.Sev.MAJOR, "RESONANCE on network #" + net + ": " + n.resonance + " (spectrum meter " + pos.getX() + "," + pos.getZ() + ")", pos);
        else AlarmManager.get(world).clear(key + ":res");
        int h = HS[cursor];
        double[] cv = cur == null ? new double[Harmonics.H + 1] : cur;
        put("pwr", powered());
        put("lim", overNow);
        put("res", live && held[28] > 2.5);
        put("lhold", hold);
        put("relay", relay);
        put("g_thd", (int) Math.round(Math.min(10, held[26]) * 10));
        text("thdv", live ? String.format(Locale.ROOT, "THDV %.2f%%", ctl.get("s_mode") == 0 ? held[26] : v == null ? 0 : v[26]) : "THDV ----");
        text("thdi", live ? String.format(Locale.ROOT, "THDI %.1f%%", held[29]) : "THDI ----");
        text("cur", live ? String.format(Locale.ROOT, "H%d %s %.2f%% PK %.2f LIM %.0f%%", h, ctl.get("s_mode") == 0 ? "V" : "I", held[h], peak[h], lim[1]) : "");
        text("restxt", !live ? "" : held[28] > 2.5 ? String.format(Locale.ROOT, "RES H%.1f X%.1f", held[27], held[28]) : "NO RESONANCE");
        text("net", n == null ? "NO PLANT TOUCHING" : n.level == null ? "NET #" + n.id + " NO LEVEL" : !n.level.ac ? "DC NETWORK - USE PQ" : "NET #" + n.id + " " + n.level.label.toUpperCase(Locale.ROOT));
        text("mode", (ctl.get("s_mode") == 0 ? "VOLTAGE" : "CURRENT") + " " + STD[Math.min(2, ctl.get("s_std"))] + " FS " + (int) fs + "%");
        text("hsel", "H" + h);
    }

    private boolean indiv(double lim) { for (int h : HS) if (held[h] > lim) return true; return false; }
    private String worst(double lim) { int wh = 0; for (int h : HS) if (held[h] > held[wh]) wh = h; return wh == 0 || held[wh] <= lim ? "" : String.format(Locale.ROOT, ", worst h%d %.2f%%", wh, held[wh]); }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        switch (id) {
            case "test": return lampTest();
            case "hl": cursor = (cursor + HS.length - 1) % HS.length; return "§eCursor h" + HS[cursor];
            case "hr": cursor = (cursor + 1) % HS.length; return "§eCursor h" + HS[cursor];
            case "hold": hold = !hold; return hold ? "§eDisplay HELD" : "§7Display live";
            case "reset": java.util.Arrays.fill(peak, 0); return "§7Peak memory cleared";
            case "ack":
                if (!relay) return "§7Relay not operated";
                relay = false; over = 0; AlarmManager.get(world).clear("hm:" + pos.toLong());
                return "§aAlarm relay reset";
            default: return null;
        }
    }

    @Override protected void save(NBTTagCompound t) { t.setInteger("cursor", cursor); t.setBoolean("relay", relay); t.setBoolean("hold", hold); }
    @Override protected void load(NBTTagCompound t) { cursor = Math.max(0, Math.min(HS.length - 1, t.hasKey("cursor") ? t.getInteger("cursor") : 1)); relay = t.getBoolean("relay"); hold = t.getBoolean("hold"); }
}
