package com.dogpound.railmap.infra;

import com.dogpound.railmap.controls.IControlled;
import com.dogpound.railmap.weather.TileCulvert;
import com.dogpound.railmap.weather.TilePumpStation;
import com.dogpound.railmap.weather.Weather;
import com.dogpound.railmap.weather.WeatherData;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Batch 3's rows on the Control Center (#36-50). OLE/WX tab (PacketRailState.env, via ohle.OhleConsole): NS neutral
 * sections · LS parallel feeding · ES energy stores · FL / FLM fault locator (+ map marks) · RC / RCC relay calibration
 * · OR / OX / OT OHLE recording runs, exceedances, trace · DC / DS / DP / DCV drainage cells, sensors, pumps, culverts ·
 * IL infrastructure log. TRACK tab (PacketRailState.track, via track.TrackConsole): RF rail flaws · UT test-train runs ·
 * TM on-track machines. Every part is wrapped: one failing never blanks the tab.
 */
public final class InfraConsole {
    private InfraConsole() {}

    private interface Part { void fill(World w, List<String[]> out); }

    private static void safe(World w, List<String[]> out, Part p) {
        try { p.fill(w, out); } catch (RuntimeException | LinkageError e) { com.dogpound.railmap.RailMap.LOG.debug("[IR Extras] infra console part failed: {}", e.toString()); }
    }

    public static void env(World w, List<String[]> out) {
        safe(w, out, NeutralSections::rows);
        safe(w, out, LoadSharing::rows);
        safe(w, out, Ess::rows);
        safe(w, out, FaultLocator::rows);
        safe(w, out, RelayCal::rows);
        safe(w, out, OhleRecording::rows);
        safe(w, out, InfraConsole::drainage);
        safe(w, out, (ww, o) -> { int n = 0; for (String s : InfraData.get(ww).log) { if (n++ > 30) break; o.add(new String[]{"IL", s}); } });
    }

    public static void track(World w, List<String[]> out) {
        safe(w, out, RailFlaws::rows);
        safe(w, out, InfraConsole::machines);
    }

    /** TM kind · x,z · state (gearState) */
    static void machines(World w, List<String[]> out) {
        for (TileEntity t : new ArrayList<>(w.loadedTileEntityList))
            if (t instanceof TileTrackMachine m && !t.isInvalid())
                out.add(new String[]{"TM", m instanceof TileRailGrinder ? "GRINDER" : "WELDER", t.getPos().getX() + "," + t.getPos().getZ(), m.gearState()});
    }

    /** the drainage network (#43): cells with gear or water, then every sensor, pump station and culvert */
    static void drainage(World w, List<String[]> out) {
        if (!Weather.on()) return;
        Map<Long, int[]> cells = new LinkedHashMap<>();          // cell key -> {culverts, worst blocked %, pumps, pumps running, sensors}
        for (TileCulvert c : TileCulvert.all(w)) { int[] v = cells.computeIfAbsent(Weather.cell(w, c.getPos()).key, k -> new int[5]); v[0]++; v[1] = Math.max(v[1], (int) Math.round(c.blocked() * 100)); }
        for (TilePumpStation p : TilePumpStation.all(w)) { int[] v = cells.computeIfAbsent(Weather.cell(w, p.getPos()).key, k -> new int[5]); v[2]++; if (p.running(0) || p.running(1)) v[3]++; }
        Map<Long, TileFloodSensor> sensors = new HashMap<>();
        for (TileFloodSensor s : TileFloodSensor.REG.all(w)) { long k = Weather.cell(w, s.getPos()).key; cells.computeIfAbsent(k, x -> new int[5])[4]++; sensors.put(k, s); }
        WeatherData wd = WeatherData.get(w);
        for (WeatherData.Cell c : wd.cells.values()) if (c.water > 5) cells.computeIfAbsent(c.key, k -> new int[5]);
        InfraData d = InfraData.get(w);
        for (Map.Entry<Long, int[]> e : cells.entrySet()) {
            WeatherData.Cell c = wd.cells.get(e.getKey());
            if (c == null) continue;
            int[] v = e.getValue();
            TileFloodSensor s = sensors.get(e.getKey());
            out.add(new String[]{"DC", Weather.name(c), String.valueOf(Math.round(c.water)), String.valueOf(Math.round(c.flood())), c.floodRisk < 0 ? "auto" : String.valueOf(c.floodRisk),
                    v[0] + (v[0] > 0 ? " (" + v[1] + "% blk)" : ""), v[3] + "/" + v[2], s == null ? "no sensor" : TileFloodSensor.LEVELS[s.level()] + String.format(Locale.ROOT, " %+.1f mm/min", s.rate()),
                    d.floodTsr.containsKey(e.getKey()) ? "TSR" : "", s == null ? "" : String.valueOf(s.getPos().toLong())});
        }
        for (TileFloodSensor s : TileFloodSensor.REG.all(w)) { String[] r = s.row(); out.add(Arrays.copyOf(r, r.length + 1)); out.get(out.size() - 1)[r.length] = String.valueOf(s.getPos().toLong()); }
        for (TilePumpStation p : TilePumpStation.all(w))
            out.add(new String[]{"DP", p.getPos().getX() + "," + p.getPos().getZ(), p.mode(), p.faulted(0) ? "FAULT" : p.running(0) ? "RUN" : "stby", p.faulted(1) ? "FAULT" : p.running(1) ? "RUN" : "stby",
                    String.format(Locale.ROOT, "%.0f / %.0f", p.minutes(0), p.minutes(1)), p.source()});
        for (TileCulvert c : TileCulvert.all(w))
            out.add(new String[]{"DCV", c.getPos().getX() + "," + c.getPos().getZ(), String.valueOf(Math.round(c.blocked() * 100)), String.format(Locale.ROOT, "%.1f", c.flowMm())});
    }

    // ---- console buttons (PacketOhleOp ops 20+) ---------------------------------------------------------------------------
    /** 20 dispatch "id:method" · 21 ack all faults · 22 press "pos:control" on a batch-3 tile · 23 recording orders for run id */
    public static String op(World w, int op, String arg, String who) {
        switch (op) {
            case 20: {
                String[] a = arg.split(":");
                InfraData.FaultRec r = InfraData.get(w).fault(Integer.parseInt(a[0]));
                if (r == null) return "No fault record #" + a[0];
                return FaultLocator.dispatch(w, r.id, BlockPos.fromLong(r.feeder), a.length > 1 ? Integer.parseInt(a[1]) : 0, who + " (console)");
            }
            case 21: {
                int n = 0;
                for (InfraData.FaultRec r : InfraData.get(w).faults) if (!r.acked) { r.acked = true; n++; com.dogpound.railmap.alarm.AlarmManager.get(w).clear("floc:" + r.id); }
                InfraData.get(w).markDirty();
                return n + " fault record(s) acknowledged";
            }
            case 22: {
                String[] a = arg.split(":");
                BlockPos p = BlockPos.fromLong(Long.parseLong(a[0]));
                if (!w.isBlockLoaded(p) || !(w.getTileEntity(p) instanceof InfraTile t)) return "Not loaded";
                Set<String> ok = new HashSet<>(Arrays.asList("s_mode", "reset", "pk_up", "pk_dn", "tsr", "pumps", "ack", "orders", "end"));
                if (!ok.contains(a[1])) return "That control is only worked at the gear";
                if (!t.powered()) return t.wiredName() + " has no 12 V - no telecontrol";
                return IControlled.operate(t, a[1], null);
            }
            case 23: return OhleRecording.orders(w, Integer.parseInt(arg)) + " work order(s) raised";
            default: return null;
        }
    }
}
