package com.dogpound.railmap.api;

import com.dogpound.railmap.logic.TileLogic;
import com.dogpound.railmap.program.ProgramData;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * The Control Center's API tab (her ChatGPT list #96-105), built on the server for PacketRailState.api. Row tags:
 * C compat, V vehicles (every adapter), P program + watchdog, L logic modules, J train-order jobs, T order templates,
 * O objectives, A audit log, X export status. Buttons come back as PacketApiOp -> {@link #op}.
 */
public final class ApiConsoleData {
    private ApiConsoleData() {}

    public static void fill(World w, List<String[]> out) {
        for (Map<String, Object> r : RailApi.compat(w)) out.add(new String[]{"C", s(r.get("area")), s(r.get("name")), s(r.get("state")), s(r.get("detail"))});
        int n = 0;
        for (Transport.Vehicle v : Transport.vehicles(w)) {
            if (n++ >= 80) break;
            out.add(new String[]{"V", v.id, v.name, v.adapter, v.kind + (v.cars > 1 ? " x" + v.cars : ""), String.valueOf(Math.round(v.speedKmh)),
                    v.destination.isEmpty() ? v.line : v.destination, v.cargo == 0 ? "-" : v.cargo + (v.fillPct >= 0 ? " (" + v.fillPct + "%)" : ""), v.status,
                    Math.round(v.x) + " " + Math.round(v.y) + " " + Math.round(v.z)});
        }
        ProgramData d = ProgramData.get(w);
        StringBuilder vars = new StringBuilder();
        for (Map.Entry<String, Double> e : d.vars.entrySet()) { if (vars.length() > 300) { vars.append(" ..."); break; } vars.append(e.getKey()).append('=').append(Expr.fmt(e.getValue())).append("  "); }
        out.add(new String[]{"P", d.halted, String.valueOf(d.lastSteps), String.valueOf(d.lastMicros), String.valueOf(d.worstMicros), String.valueOf(d.rules.size()),
                vars.toString(), String.join(", ", d.timers.keySet()), String.join(", ", d.recentEvents.subList(0, Math.min(6, d.recentEvents.size())))});
        n = 0;
        for (TileLogic t : TileLogic.loaded(w)) {
            if (n++ >= 60) break;
            BlockPos p = t.getPos();
            out.add(new String[]{"L", t.tag(), t.kind().label, t.fnName(), t.q() ? "1" : "0", t.display(), p.getX() + "," + p.getY() + "," + p.getZ(), String.valueOf(t.wiredLamp())});
        }
        TrainOrders o = TrainOrders.get(w);
        for (TrainOrders.Job j : o.jobs.values())
            out.add(new String[]{"J", j.loco.toString(), j.name, j.template, String.valueOf(j.step + 1), String.valueOf(j.laps), j.state, j.params.toString()});
        for (Map.Entry<String, String> e : o.templates.entrySet()) out.add(new String[]{"T", e.getKey(), e.getValue()});
        for (Map<String, Object> ob : ScenarioApi.get(w).objectiveList(w))
            out.add(new String[]{"O", s(ob.get("name")), s(ob.get("mode")), s(ob.get("expr")), s(ob.get("state")), s(ob.get("secondsLeft"))});
        n = 0;
        for (Audit.Entry e : Audit.get(w).entries) {
            if (n++ >= 80) break;
            out.add(new String[]{"A", Audit.clock(e.tick), e.src.name(), e.who, e.target, e.action, e.before.isEmpty() && e.after.isEmpty() ? "" : e.before + " > " + e.after, e.result, e.refused() ? "1" : "0"});
        }
        out.add(new String[]{"X", ApiConfig.exportJson ? "on" : "off", Export.lastWritten, Export.lastError, Export.httpRunning() ? ApiConfig.httpBind + ":" + ApiConfig.httpPort : "off",
                String.valueOf(Export.httpServed), String.valueOf(Export.httpRefused), RailApi.VERSION + " L" + RailApi.LEVEL});
    }

    private static String s(Object o) { return o == null ? "" : String.valueOf(o); }

    /** a console button: 1 reset watchdog, 2 export now, 3 self-test, 4 cancel orders (uuid), 5 cancel objective, 6 logic control "x,y,z:id", 7 demand reset, 8 clear lab faults */
    public static String op(World w, int op, String arg, String who) {
        switch (op) {
            case 1: return ProgramData.get(w).resetWatchdog(w, who);
            case 2: return Export.snapshot(w.getMinecraftServer() == null ? w : w.getMinecraftServer().getWorld(0));
            case 3: { List<String> r = RailApi.selftest(w); return r.get(0); }
            case 4: try { return TrainOrders.get(w).cancel(w, UUID.fromString(arg), Audit.Src.CONSOLE, who); } catch (IllegalArgumentException e) { return "bad train"; }
            case 5: return ScenarioApi.get(w).cancel(w, arg, Audit.Src.CONSOLE, who);
            case 6: {
                int c = arg.indexOf(':');
                if (c < 0) return "";
                String[] p = arg.substring(0, c).split(",");
                Object r = RailApi.call(w, "press", new Object[]{Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]), arg.substring(c + 1)}, Audit.Src.CONSOLE, who);
                return String.valueOf(r instanceof Map<?, ?> m && m.containsKey("error") ? m.get("error") : r);
            }
            case 7: return ScenarioApi.get(w).demandReset(w, Audit.Src.CONSOLE, who);
            case 8: return ScenarioApi.clearFaults(w, Audit.Src.CONSOLE, who);
            default: return "";
        }
    }
}
