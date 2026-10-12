package com.dogpound.railmap.ride;

import com.dogpound.railmap.grid.Elec;
import com.dogpound.railmap.infra.Supply;
import net.minecraft.entity.player.EntityPlayer;

import java.util.Locale;

/**
 * The ropeway machine room (three blocks wide): the main drive motor (480 V / 4.16 kV / 13.8 kV 3-phase from a grid
 * cable touching it, its real load drawn every second), the high-speed coupling with the service brake disc, the
 * planetary gearbox, the drive bullwheel with the emergency (rim) brake calipers, the brake hydraulic power unit (HPU:
 * hydraulic pressure holds the spring brakes OFF - lose it and they fall) and the evacuation drive.
 * Local panel: drive state, kW / A, motor + bullwheel rpm, brake pressure, winding + oil temperature, supply; HPU
 * START/STOP, LOCAL/REMOTE key (LOCAL locks the desk out for maintenance), E-STOP, RESET, LAMP TEST.
 */
public class TileRideMachine extends RideTile {
    public TileRideMachine() { super(new Supply(Elec.Level.AC480, Elec.Level.MV4160, Elec.Level.MV13800)); }

    @Override public String wiredName() { return "Ropeway machine room " + pos.getX() + "," + pos.getZ(); }
    @Override protected boolean owns(RideData.Line l) { return l.machine == pos.toLong(); }
    @Override protected boolean free(RideData.Line l) { return l.machine == 0; }
    @Override protected void claim(RideData.Line l) { l.machine = pos.toLong(); }
    @Override protected void release(RideData.Line l) { l.machine = 0; }

    @Override
    public String gearState() {
        RideData.Line l = line();
        return mains.status().replaceAll("§.", "") + (l == null ? " · no line" : " · " + l.label() + " " + RideSim.state(l) + String.format(Locale.ROOT, " %.1f m/s %.0f kW", Math.abs(l.v), l.kw));
    }

    @Override public int wiredLamp() { RideData.Line l = line(); return !live() ? 3 : l != null && l.faults != 0 ? 2 : 1; }

    @Override
    protected void serverTick() {
        RideData.Line l = line();
        long now = world.getTotalWorldTime();
        if (age % 20 == 3) mainsTick(l == null ? 600 : Math.max(3000, Math.abs(l.kw) * 1000) + (l.hpu ? 4000 : 0));
        if (l != null) {
            l.machineSeen = now;
            l.local = ctl.get("k_local") == 1;
            l.estops = ctl.get("m_estop") == 1 || !powered() ? l.estops | 2 : l.estops & ~2;   // the safety loop is 12 V: lose it and it opens
            l.supplyV = mains.level == null ? 0 : mains.level.volts;
            l.driveOk = live() && (!RideConfig.needSupply || mains.ok);
            l.driveWhy = !powered() ? "machine room has no 12 V control power" : !mains.ok ? "supply: " + mains.what : l.local ? "machine room on LOCAL" : "ready";
            if (l.local) l.driveOk = false;
        }
        if (age % 5 != 2) return;
        boolean on = powered();
        put("l_pwr", on);
        put("l_sup", mains.ok);
        put("l_local", ctl.get("k_local") == 1);
        if (l == null) { text("d_state", on ? "NO LINE - STRING A ROPE" : ""); for (String k : new String[]{"d_kw", "d_rpm", "d_bar", "d_temp", "d_sup"}) text(k, ""); return; }
        RideGeom g = l.geom();
        double bw = g == null ? 0 : Math.abs(l.v) / (Math.PI * 4.0) * 60, motorRpm = bw * 72;
        double bar = l.hpu && !l.sbrake && !l.ebrake ? 160 : l.hpu ? 6 : 0;
        put("l_run", on && Math.abs(l.v) > 0.01 && l.mode != 2);
        put("l_sb", on && l.sbrake);
        put("l_eb", on && l.ebrake);
        put("l_hpu", on && l.hpu);
        put("l_flt", on && l.faults != 0);
        put("l_evac", on && l.mode == 2);
        put("g_kw", (int) Math.min(100, Math.abs(l.kw) / RideSim.RATED_KW[l.kind] * 100));
        put("g_amp", (int) Math.min(100, l.amps / Math.max(1, RideSim.RATED_KW[l.kind] * 1000 / (Math.sqrt(3) * Math.max(1, l.supplyV) * 0.88)) * 100));
        put("g_bar", (int) Math.round(bar / 200 * 100));
        put("g_oil", (int) Math.min(100, l.oilC / 120 * 100));
        put("g_rpm", (int) Math.min(100, motorRpm / 1800 * 100));
        text("d_state", RideSim.state(l));
        text("d_kw", String.format(Locale.ROOT, "%s %.0f KW %.0f A", l.kw < -1 ? "REGEN" : "MOTOR", Math.abs(l.kw), l.amps));
        text("d_rpm", String.format(Locale.ROOT, "MOTOR %.0f RPM BW %.1f", motorRpm, bw));
        text("d_bar", String.format(Locale.ROOT, "BRAKES %.0f BAR %s", bar, l.ebrake ? "EMERG" : l.sbrake ? "APPLIED" : "OFF"));
        text("d_temp", String.format(Locale.ROOT, "WINDING %.0fC OIL %.0fC", l.motorC, l.oilC));
        text("d_sup", mains.ok ? "SUPPLY " + (mains.level == null ? "" : mains.level.label.toUpperCase(Locale.ROOT).replace("Φ", "")) + " OK" : "SUPPLY FAIL");
    }

    @Override
    public String controlPress(String id, EntityPlayer p) {
        RideData.Line l = line();
        switch (id) {
            case "test": return lampTest();
            case "hpu":
                if (l == null) return "§7Not on a line";
                if (!live()) return "§cNo power for the HPU pump: " + (powered() ? mains.what : "no 12 V");
                if (l.hpu && Math.abs(l.v) > 0.01) { l.hpu = false; return "§6HPU stopped with the rope moving - brakes falling"; }
                l.hpu = !l.hpu;
                return l.hpu ? "§aHPU running - brake pressure building" : "§eHPU stopped - brakes applied";
            case "reset":
                if (l == null) return "§7Not on a line";
                return RideSim.reset(world, l);
            default: return null;
        }
    }
}
