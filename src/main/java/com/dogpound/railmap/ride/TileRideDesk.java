package com.dogpound.railmap.ride;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.sound.PackSounds;
import net.minecraft.entity.player.EntityPlayer;

import java.util.Locale;

/**
 * The ropeway control desk (three blocks wide): ENABLE key, START / STOP, E-STOP, RESET, speed setpoint, wind limit,
 * AUTO / MANUAL / EVAC mode, cabin count (gondola), DOORS, BRAKE RELEASE (evacuation), HORN, alarm ACK + lamp test, the
 * fault annunciator, and the bridge: line state, speed, rope position, rope-position detector, wind, motor, cabins +
 * riders, next departure, tension - and the mimic screen (RideRender draws the line and every cabin live on it).
 * A 12 V instrument: with no 12 V it is dark, its E-stop loop opens and the line will not run.
 */
public class TileRideDesk extends RideTile {
    public static final double[] SPEEDS = {0.5, 1, 2, 3, 4, 5, 6};
    public static final double[] WIND = {10, 12, 15, 18, 22};
    public static final int[] CABINS = {4, 6, 8, 12, 16, 24};
    static final String[] TILES = {"t_estop", "t_wind", "t_rpd", "t_sup", "t_doors", "t_ovs", "t_tens", "t_motor", "t_drive"};

    public TileRideDesk() { super(null); }

    @Override public String wiredName() { return "Ropeway control desk " + pos.getX() + "," + pos.getZ(); }
    @Override protected boolean owns(RideData.Line l) { return l.desk == pos.toLong(); }
    @Override protected boolean free(RideData.Line l) { return l.desk == 0; }
    @Override protected void claim(RideData.Line l) { l.desk = pos.toLong(); }
    @Override protected void release(RideData.Line l) { l.desk = 0; }

    @Override public String gearState() { RideData.Line l = line(); return (powered() ? "12 V ok" : "dark - no 12 V") + (l == null ? " · no line" : " · " + l.label() + " " + RideSim.state(l)); }
    @Override public int wiredLamp() { RideData.Line l = line(); return !powered() ? 3 : l != null && l.faults != 0 ? 2 : 1; }

    static int sel(int v, int n) { return Math.max(0, Math.min(n - 1, v)); }

    @Override
    protected void serverTick() {
        RideData.Line l = line();
        if (l != null) {
            l.deskSeen = world.getTotalWorldTime();
            boolean on = powered();
            l.estops = ctl.get("m_estop") == 1 || !on ? l.estops | 1 : l.estops & ~1;
            l.setV = on ? SPEEDS[sel(ctl.get("s_spd"), SPEEDS.length)] : 0;
            l.windLimit = WIND[sel(ctl.get("s_wlim"), WIND.length)];
            int mode = sel(ctl.get("s_mode"), 3);
            if (mode != l.mode && Math.abs(l.v) < 0.01) { l.mode = mode; if (mode != 2) l.brakeRel = false; }
            int cab = CABINS[sel(ctl.get("s_cab"), CABINS.length)];
            if (!l.shuttle() && cab != l.cabins && Math.abs(l.v) < 0.01 && !l.running) { l.cabins = cab; RideData.get(world).changed(); }
        }
        if (age % 5 != 1) return;
        boolean on = powered();
        if (l == null) { text("d_name", on ? "NO LINE - PLACE NEAR A STATION" : ""); return; }
        RideGeom g = l.geom();
        put("l_run", on && l.running);
        put("l_stop", on && !l.running && Math.abs(l.v) < 0.01);
        put("l_eb", on && l.ebrake);
        put("l_sb", on && l.sbrake);
        put("l_doors", on && (l.gate[0] || l.gate[1]));
        put("l_brel", on && l.brakeRel);
        put("l_restr", on && RideConfig.windStops && l.wind > 0.75 * l.windLimit);
        for (int i = 0; i < TILES.length; i++) put(TILES[i], on && (l.faults & (1 << i)) != 0 ? 2 : 0);
        put("g_spd", (int) Math.min(100, Math.abs(l.v) / 6 * 100));
        put("g_wind", (int) Math.min(100, l.wind / 30 * 100));
        put("g_kw", (int) Math.min(100, Math.abs(l.kw) / RideSim.RATED_KW[l.kind] * 100));
        text("d_name", (l.label() + " " + RideGeom.KIND[l.kind]).toUpperCase(Locale.ROOT));
        text("d_state", RideSim.state(l).toUpperCase(Locale.ROOT));
        text("d_speed", String.format(Locale.ROOT, "SPEED %.1f / SET %.1f M/S", Math.abs(l.v), l.vMax));
        text("d_rope", g == null ? "NO ROPE" : l.shuttle() ? String.format(Locale.ROOT, "ROPE POS %.1f / %.0f M", l.s, g.length) : String.format(Locale.ROOT, "ROPE RUN %.0f M LOOP %.0f", l.ropeRun % 100000, g.loop));
        text("d_rpd", l.kind == RideGeom.FUNICULAR ? "RPD - (TRACK)" : String.format(Locale.ROOT, "RPD MAX %.0f MM %s", l.rpdMm, l.rpdTower < 0 ? "" : "P" + l.rpdTower));
        text("d_wind", String.format(Locale.ROOT, "WIND %.1f M/S LIM %.0f", l.wind, l.windLimit));
        text("d_motor", String.format(Locale.ROOT, "MOTOR %.0f KW %.0f A", l.kw, l.amps));
        text("d_cars", String.format(Locale.ROOT, "%s %d RIDERS %d", l.shuttle() ? "CARS" : "CABINS", l.cars(), l.riders));
        text("d_next", nextDeparture(l));
        text("d_tens", !l.hasTension() ? "TENSION NOT FITTED" : l.carriage < 0 ? "TENSION SIGNAL LOST" : String.format(Locale.ROOT, "TENSION %.0f KN CARR %.0f%%", l.tensionKn, l.carriage));
    }

    static String nextDeparture(RideData.Line l) {
        if (!l.shuttle()) return l.running ? "CABIN INTERVAL " + intervalS(l) + " S" : "LINE STOPPED";
        if (l.phase == RideData.DWELL) return l.running ? "NEXT DEP " + (l.dwell / 20) + " S" : "WAITING START";
        if (l.phase == RideData.CLOSING) return "DOORS CLOSING";
        RideGeom g = l.geom();
        double d = g == null ? 0 : l.dir > 0 ? g.length - l.s : l.s;
        return Math.abs(l.v) < 0.05 ? "HELD ON LINE" : "ARRIVE " + Math.round(d / Math.max(0.3, Math.abs(l.v))) + " S";
    }

    static long intervalS(RideData.Line l) {
        RideGeom g = l.geom();
        return g == null || l.v < 0.05 ? 0 : Math.round(g.loop / l.cars() / l.v);
    }

    @Override
    public String controlPress(String id, EntityPlayer p) {
        RideData.Line l = line();
        if (id.equals("test")) return lampTest();
        if (id.startsWith("s_") || id.startsWith("k_") || id.equals("m_estop")) return null;
        if (l == null) return "§7The desk is not on a line";
        if (!powered()) return "§cThe desk is dark - no 12 V";
        switch (id) {
            case "start": {
                if (ctl.get("k_en") != 1) return "§cTurn the ENABLE key first";
                String r = RideSim.start(world, l);
                return r == null ? "§a" + l.label() + " START" : "§c" + r;
            }
            case "stop": RideSim.stop(world, l); return "§e" + l.label() + " normal stop";
            case "reset": return RideSim.reset(world, l);
            case "ack":
                for (AlarmManager.Alarm a : AlarmManager.get(world).all())
                    if (a.key.startsWith("ride:" + l.id + ":") && !a.acked) AlarmManager.get(world).ack(a.id, who(p));
                return "§7Alarms acknowledged";
            case "horn":
                for (int k = 0; k < 2; k++) PackSounds.play(world, l.station(k), "cab.horn", 1.5f, 0.8f);
                return "§7Horn";
            case "doors": {
                if (Math.abs(l.v) > 0.01) return "§cInterlocked - the line is moving";
                boolean open = !(l.gate[0] || l.gate[1]);
                l.gate[0] = l.gate[1] = open;
                if (open && l.shuttle()) { l.phase = RideData.DWELL; l.dwell = Math.max(l.dwell, 200); }
                return open ? "§eStation gates OPEN (line interlocked)" : "§aStation gates closed";
            }
            case "brel":
                if (l.mode != 2) return "§7BRAKE REL works in EVAC mode only";
                l.brakeRel = !l.brakeRel;
                return l.brakeRel ? "§6Evacuation: brakes released by the hand pump" : "§eBrakes re-applied";
            default: return null;
        }
    }
}
