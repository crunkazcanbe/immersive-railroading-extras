package com.dogpound.railmap.ohle;

import com.dogpound.railmap.grid.GridData;
import com.dogpound.railmap.grid.GridKind;
import com.dogpound.railmap.weather.Crews;
import com.dogpound.railmap.weather.TileWeatherStation;
import com.dogpound.railmap.weather.Weather;
import com.dogpound.railmap.weather.WeatherData;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * What the Control Center's OLE / WEATHER tab shows, packed as rows of strings into PacketRailState.env (row[0] = which
 * table). S sections · F feeder breakers · I isolators · P pantographs · L OHLE log · E energy periods · N energy now ·
 * W weather station readings · R weather restrictions (also drawn on the map) · O snow work orders · X weather log.
 */
public final class OhleConsole {
    private OhleConsole() {}

    private static String f1(double v) { return String.format(Locale.ROOT, "%.1f", v); }

    public static void fill(World w, List<String[]> out) {
        OhleData d = OhleData.get(w);
        long now = w.getTotalWorldTime();
        for (Ohle.Section s : Ohle.known(w)) {
            if (out.size() > 40) break;
            out.add(new String[]{"S", s.name(), s.state(), String.valueOf(s.wires.size()), String.valueOf(s.feeders.size()), String.valueOf(s.insulators.size()), String.valueOf(s.neutrals.size()),
                    f1(s.tension(w) / 1000) + (s.tensioners.isEmpty() ? " fixed" : " weights"), s.why});
        }
        for (Map.Entry<Long, GridData.Node> en : GridData.get(w).nodes().entrySet()) {
            if (en.getValue().kind != GridKind.FEEDER) continue;
            OhleData.Cb cb = d.cbs.get(en.getKey());
            out.add(new String[]{"F", Ohle.xz(BlockPos.fromLong(en.getKey())), String.valueOf(en.getKey()), String.valueOf(cb == null ? 0 : cb.state), String.valueOf(cb == null ? 0 : cb.trips),
                    cb == null ? "" : cb.last, en.getValue().feeds, Ohle.feederLive(w, GridData.get(w), en.getKey()) ? "1" : "0"});
        }
        for (TileIsolator i : TileIsolator.all(w))
            out.add(new String[]{"I", Ohle.xz(i.getPos()), String.valueOf(i.getPos().toLong()), i.closed() ? "1" : "0", TileIsolator.MODES[i.mode()], i.earthed() ? "1" : "0", i.link() != 0 ? "1" : "0", i.powered() ? "1" : "0", i.gearState()});
        int n = 0;
        for (OhleData.Pan p : d.pans.values()) {
            if (n++ > 30) break;
            int recent = 0;
            for (long t : p.arcs) if (t > 0 && now - t < 2400) recent++;
            out.add(new String[]{"P", p.name, p.broken ? "BROKEN" : p.up ? "UP" : "down", p.vcb ? "1" : "0", p.manual ? "MAN" : "AUTO", String.valueOf(p.forceN), String.valueOf(Math.round(p.carbon * 100)),
                    String.valueOf(recent), p.note == null ? "" : p.note, f1(p.kwhIn), f1(p.kwhRegen), f1(p.kwhRes), String.valueOf(Math.round(p.cost))});
        }
        for (int i = 0; i < Math.min(14, d.log.size()); i++) out.add(new String[]{"L", d.log.get(i)});
        for (OhleData.Period p : d.periods) {
            double hrs = Math.max(1, ((p.end == 0 ? now : p.end) - p.start)) / 72000.0;
            out.add(new String[]{"E", p.label, f1(p.kwhIn), f1(p.kwhIn / hrs), f1(p.kwhRegen), f1(p.kwhRes), String.valueOf(Math.round(p.cost)), String.valueOf(p.kwhIn > 0 ? Math.round(p.kwhPeak / p.kwhIn * 100) : 0),
                    String.valueOf(p.departures), String.valueOf(p.held), String.valueOf(p.held > 0 ? p.heldSeconds / p.held : 0), p.end == 0 ? "1" : "0"});
        }
        int h = EnergyDispatch.hour(w);
        out.add(new String[]{"N", String.format(Locale.ROOT, "%02d:00", h), EnergyDispatch.BANDS[EnergyDispatch.band(h)], String.valueOf(EnergyDispatch.tariff(w)), d.peakShift ? "1" : "0"});
        com.dogpound.railmap.infra.InfraConsole.env(w, out);                 // batch 3 #36-50: neutral sections, sharing, storage, fault locator, relays, recording, drainage
        try { com.dogpound.railmap.weather.WxTrains.env(w, out); } catch (RuntimeException | LinkageError e) { com.dogpound.railmap.RailMap.LOG.debug("[IR Extras] weather train rows failed: {}", e.toString()); }   // IR Extras #4: WT runs, WF forecast, WV trains, WL log
        // ---- weather
        if (!Weather.on()) return;
        WeatherData wd = WeatherData.get(w);
        Set<Long> seen = new HashSet<>();
        for (TileWeatherStation st : TileWeatherStation.REG.all(w)) {
            WeatherData.Cell c = Weather.cell(w, st.getPos());
            if (!seen.add(c.key)) continue;
            out.add(new String[]{"W", st.getPos().getX() + "," + st.getPos().getZ(), st.powered() ? "1" : "0", f1(c.air), f1(c.rail), String.valueOf(Math.round(c.sft)), f1(c.wind),
                    c.precip == 0 ? "dry" : (c.snowing ? "snow " : "rain ") + new String[]{"", "light", "heavy", "storm"}[c.precip], String.valueOf(Math.round(c.vis)), c.visWhy,
                    String.valueOf(Math.round(c.snow)), f1(c.ice), String.valueOf(Math.round(c.flood())), String.valueOf(Math.round(c.condition * 100))});
        }
        for (WeatherData.Cell c : wd.cells.values()) {
            Object[] r = Weather.restriction(w, c, now);
            if ((double) r[0] < 0) continue;
            out.add(new String[]{"R", String.valueOf(c.x0()), String.valueOf(c.z0()), String.valueOf(Weather.CELL), String.valueOf(Math.round((double) r[0])), String.valueOf(r[1]), (boolean) r[2] ? "1" : "0"});
        }
        for (WeatherData.Cell c : Crews.orders(wd))
            out.add(new String[]{"O", Weather.name(c), String.valueOf(Math.round(c.snow)), f1(c.traffic), String.valueOf(Math.round(Crews.priority(c))), c.crew >= 0 ? String.valueOf(c.crew + 1) : ""});
        for (int i = 0; i < Math.min(10, wd.log.size()); i++) out.add(new String[]{"X", wd.log.get(i)});
        out.add(new String[]{"H", now < wd.heatwaveUntil ? "HEATWAVE" : now < wd.coldSnapUntil ? "COLD SNAP" : "", Weather.weather2() ? "1" : "0"});
    }

    /** console buttons: 1 feeder close, 2 feeder open, 3 isolator operate, 4 energy period mark, 5 peak shift toggle */
    public static String op(World w, int op, String arg, String who) {
        switch (op) {
            case 1: case 2: return Ohle.feederOp(w, Long.parseLong(arg), op == 1, who + " (console)");
            case 3: {
                if (!(w.getTileEntity(BlockPos.fromLong(Long.parseLong(arg))) instanceof TileIsolator t)) return "Isolator not loaded";
                return t.remote(null, who + " (console)");
            }
            case 4: {
                OhleData d = OhleData.get(w);
                d.period().end = w.getTotalWorldTime();
                OhleData.Period p = new OhleData.Period();
                p.label = String.valueOf((char) ('A' + d.periods.size() % 26));
                p.start = w.getTotalWorldTime();
                d.periods.add(p);
                while (d.periods.size() > 12) d.periods.remove(0);
                d.markDirty();
                return "New energy period " + p.label;
            }
            case 5: { OhleData d = OhleData.get(w); d.peakShift = !d.peakShift; d.markDirty(); return "Peak shift " + (d.peakShift ? "ON" : "OFF"); }
            default: return op >= 40 && op <= 45 ? com.dogpound.railmap.weather.WxTrains.op(w, op, arg, who) : op >= 20 ? com.dogpound.railmap.infra.InfraConsole.op(w, op, arg, who) : null;   // 40-45: weather trains
        }
    }
}
