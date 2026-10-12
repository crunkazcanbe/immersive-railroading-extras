package com.dogpound.railmap.light;

import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.depot.DepotClock;
import com.dogpound.railmap.depot.DepotTile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Photocell / dusk-dawn lighting controller: a sky-facing photocell on a wall enclosure. It measures the light (lux, the
 * live readout and a log-scale meter), and after a 30 s time delay (so a passing cloud or a train's headlight doesn't
 * cycle the lamps) switches its output ON below the switch-on level and OFF again at twice that (hysteresis). MODE AUTO /
 * ON / OFF; the NIGHT CUT key with its schedule (cut start / end hour) turns circuits 3 and 4 off through the small
 * hours. Its output goes to the lighting contactor panel on its control wire - or, with no panel, straight to every lamp
 * on that wire. 12 V; with no 12 V it is gone from the circuit and the lamps fall back to their own photocells.
 */
public class TileLightController extends DepotTile {
    private double lux;
    private boolean dark, out, cut;
    private int delay, onLux = 35, cutFrom = 1, cutTo = 5;
    private List<BlockPos>[] lamps;
    private boolean panelOnBus;

    @Override public String wiredName() { return "Lighting controller " + Math.floorMod(pos.getX() * 5 + pos.getZ() * 11 + pos.getY(), 1000); }
    @Override public String gearState() { return (out ? "LIGHTS ON" : "lights off") + (cut ? " · NIGHT CUT" : "") + " · " + Lux.show(lux); }
    @Override public void gearInfo(Map<String, Object> m) { m.put("lux", Math.round(lux * 10) / 10.0); m.put("dark", dark); m.put("output", out); m.put("nightCut", cut); m.put("onLux", onLux); m.put("cut", cutFrom + "-" + cutTo); }

    /** what the contactor panel follows: lights wanted (AUTO and dark, or ON) */
    public boolean output() { return powered() && out; }
    /** the night-cut window is on: circuits 3 and 4 off */
    public boolean nightCut() { return powered() && cut; }
    public double lux() { return lux; }

    @Override
    protected String press(String id, EntityPlayer who) {
        switch (id) {
            case "lx_up": onLux = Math.min(200, onLux + 5); break;
            case "lx_dn": onLux = Math.max(5, onLux - 5); break;
            case "cs_up": cutFrom = (cutFrom + 1) % 24; break;
            case "cs_dn": cutFrom = (cutFrom + 23) % 24; break;
            case "ce_up": cutTo = (cutTo + 1) % 24; break;
            case "ce_dn": cutTo = (cutTo + 23) % 24; break;
            default: return null;
        }
        markDirty();
        show();
        return String.format(Locale.ROOT, "§7Switch on below %d lx (off above %d lx) · night cut %02d:00-%02d:00", onLux, onLux * 2, cutFrom, cutTo);
    }

    static boolean inWindow(int h, int from, int to) { return from == to ? false : from < to ? h >= from && h < to : h >= from || h < to; }

    @Override
    protected void work() {
        if (age % 20 != 0) return;
        lux = Lux.at(world, pos.up(2));                                      // the cell on top looks at the sky
        boolean sensed = dark ? lux < onLux * 2 : lux < onLux;
        if (sensed != dark) {
            if (++delay >= 30) {
                dark = sensed; delay = 0;
                if (powered()) Recorder.event(world, "LIGHTING: photocell " + (dark ? "DUSK - lights on" : "DAWN - lights off") + " at " + Lux.show(lux) + " (" + wiredName() + ")");
            }
        } else delay = 0;
        int mode = Math.floorMod(ctl.get("s_mode"), 3);
        boolean was = out;
        out = mode == 1 || mode == 0 && dark;
        cut = out && ctl.get("k_cut") == 1 && inWindow(DepotClock.hour(world), cutFrom, cutTo);
        if (was != out) controlsChanged();
        put("darkl", dark); put("outl", out); put("cutl", cut); put("dly", delay > 0);
        put("lux", (int) Math.max(0, Math.min(100, Math.log10(Math.max(0.1, lux)) * 20 + 20)));   // 0.1 lx .. 100 klx
        show();
        // no contactor panel on the wire: drive the lamps on it directly
        if (age % 100 == 0 || lamps == null) {
            TileLightingContactor.Scan s = TileLightingContactor.scan(world, pos, ctlWire());
            lamps = s.circuits; panelOnBus = s.panel != null;
        }
        if (!panelOnBus && powered() && lamps != null)
            for (int k = 0; k < 4; k++) for (BlockPos p : lamps[k]) LightingBus.set(world, p, out && !(k >= 2 && cut), pos);
    }

    private void show() {
        text("luxd", Lux.show(lux));
        text("clock", DepotClock.hhmm(world));
        text("onlx", "ON<" + onLux + " LX");
        text("sched", ctl.get("k_cut") == 1 ? String.format(Locale.ROOT, "CUT %02d-%02d", cutFrom, cutTo) : "CUT OFF");
        text("lamps", lamps == null ? "" : (lamps[0].size() + lamps[1].size() + lamps[2].size() + lamps[3].size()) + (panelOnBus ? " VIA LCP" : " LAMPS"));
    }

    @Override protected void saveExtra(NBTTagCompound t) {
        t.setBoolean("dark", dark); t.setBoolean("out", out); t.setBoolean("cut", cut);
        t.setInteger("onLux", onLux); t.setInteger("cutFrom", cutFrom); t.setInteger("cutTo", cutTo); t.setDouble("lux", lux);
    }

    @Override protected void loadExtra(NBTTagCompound t) {
        dark = t.getBoolean("dark"); out = t.getBoolean("out"); cut = t.getBoolean("cut"); lux = t.getDouble("lux");
        onLux = t.hasKey("onLux") ? t.getInteger("onLux") : 35; cutFrom = t.hasKey("cutFrom") ? t.getInteger("cutFrom") : 1; cutTo = t.hasKey("cutTo") ? t.getInteger("cutTo") : 5;
    }
}
