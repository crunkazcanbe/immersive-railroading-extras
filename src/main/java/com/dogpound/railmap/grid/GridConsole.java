package com.dogpound.railmap.grid;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Server side of the Control Center's PROTECT tab (her ChatGPT list section 6: "make the simulation consistent and
 * inspectable - follow the fault from the locomotive through the feeder, protection relay, breaker and control-room
 * alarm"): every protection device, breaker, transformer, supply and analyser with its state and last event, the last
 * protection study, and the grid's log books merged newest first.
 */
public final class GridConsole {
    private GridConsole() {}

    /** {kind, x y z, state, reading, last event, counters, colour 0 ok / 1 warn / 2 bad} */
    public static List<String[]> devices(World w) {
        GridData d = GridData.get(w);
        List<String[]> out = new ArrayList<>();
        List<Map.Entry<Long, GridData.Node>> es = new ArrayList<>(d.nodes().entrySet());
        es.sort(Comparator.comparingInt(e -> order(e.getValue().kind)));
        for (Map.Entry<Long, GridData.Node> e : es) {
            if (out.size() >= 80) break;
            GridData.Node n = e.getValue();
            if (order(n.kind) >= 99) continue;
            BlockPos p = BlockPos.fromLong(e.getKey());
            String state, reading = "", last = lastEvent(n);
            int col = 0;
            switch (n.kind) {
                case IED:
                    state = n.st("aux") == 0 ? "DEAD (no 12 V)" : n.st("test") == 1 ? "TEST" : n.st("ltrip") == 1 ? "TRIPPED" : "in service";
                    col = n.st("aux") == 0 || n.st("ltrip") == 1 ? 2 : n.st("test") == 1 || n.st("pick") == 1 ? 1 : 0;
                    reading = "IA " + Elec.si(n.st("ia"), "A") + " / I> " + Elec.si(n.st("is"), "A") + (n.st("pick") == 1 ? " PICKUP" : "");
                    break;
                case RELAY: state = "plain relay " + n.relaySet + "%"; break;
                case BREAKER:
                    state = n.breaker == 0 ? "closed" : n.breaker == 1 ? "open" : "TRIPPED";
                    col = n.breaker == 2 || n.wear >= 1 ? 2 : n.breaker == 1 ? 1 : 0;
                    reading = Elec.si(n.amps, "A") + " · " + n.kA + " kA · " + new String[]{"critical", "essential", "non-ess."}[Math.max(0, Math.min(2, n.get("prio", 2)))];
                    if (!n.lastTrip.isEmpty() && n.breaker == 2) last = n.lastTrip;
                    break;
                case TRANSFORMER: case TRACTION: case RECTIFIER:
                    state = n.burnt ? "BURNT" : n.hot ? "OVERHEATED" : n.fanFail ? "FAN FAIL" : "ok";
                    col = n.burnt || n.hot ? 2 : n.fanFail || n.derate < 0.9 || n.heat > 85 ? 1 : 0;
                    reading = String.format(Locale.ROOT, "%.0f°C amb · rise %.0f · %.0f%% rating", n.ambient, n.heat, n.derate * 100);
                    break;
                case UPS:
                    state = n.lvd ? "LVD - OUT OFF" : n.st("onbat") == 1 ? "ON BATTERY" : "on mains";
                    col = n.lvd ? 2 : n.st("onbat") == 1 || Ups.health(n) < 0.6 ? 1 : 0;
                    reading = String.format(Locale.ROOT, "%d%% · %d W · %s", Math.round(Ups.soc(n) * 100), n.st("lw"), Ups.autonomy(n));
                    break;
                case PQA:
                    state = n.st("av") + n.st("af") + n.st("au") + n.st("ah") + n.st("ap") > 0 ? "LIMIT" : "ok";
                    col = state.equals("ok") ? 0 : 1;
                    reading = String.format(Locale.ROOT, "U %.1f%% VUF %.2f%% THD %.1f%% PF %.2f", n.st("v") / 10.0, n.st("vuf") / 100.0, n.st("thdv") / 100.0, n.st("pf") / 100.0);
                    break;
                case EARTHING: state = n.earthOn ? "earthed" : "OPEN"; col = n.earthOn ? 0 : 2; reading = n.get("ner", 0) == 0 ? "solid" : "NER " + n.get("ner", 0) + " A"; break;
                case ATS: case RESTORE: {
                    TileGrid t = w.isBlockLoaded(p) && w.getTileEntity(p) instanceof TileGrid g ? g : null;
                    state = t == null ? "-" : n.kind == GridKind.ATS ? t.ptext("status") : t.ptext("stage");
                    reading = t == null ? "" : n.kind == GridKind.ATS ? t.ptext("va") + " " + t.ptext("vb") : t.ptext("msg");
                    col = state.contains("LOCKOUT") || state.contains("LOST") ? 2 : state.contains("STANDBY") || state.startsWith("1") || state.startsWith("2") ? 1 : 0;
                    break;
                }
                default: state = ""; break;
            }
            if (n.efault != 0) { state += " · EARTH FAULT"; col = 2; }
            out.add(new String[]{n.kind.label, p.getX() + " " + p.getY() + " " + p.getZ(), state, reading, last,
                    GridData.opens(n) ? String.format(Locale.ROOT, "%,d ops · %.0f%%", n.ops, n.wear * 100) : n.insul < 1 ? String.format(Locale.ROOT, "insul %.0f%%", n.insul * 100) : "", String.valueOf(col)});
        }
        return out;
    }

    private static int order(GridKind k) {
        switch (k) {
            case IED: return 0; case RELAY: return 1; case BREAKER: return 2; case EARTHING: return 3; case TRANSFORMER: case TRACTION: case RECTIFIER: return 4;
            case UPS: return 5; case ATS: return 6; case RESTORE: return 7; case PQA: return 8; default: return 99;
        }
    }

    private static String lastEvent(GridData.Node n) {
        if (n.log.isEmpty()) return "";
        return History.pretty(n.log.get(n.log.size() - 1));
    }

    /** the grid's log books merged, newest first: {time, part, x y z, TYPE, text} */
    public static List<String[]> log(World w) {
        GridData d = GridData.get(w);
        List<Object[]> all = new ArrayList<>();
        for (Map.Entry<Long, GridData.Node> e : d.nodes().entrySet())
            for (String s : e.getValue().log) {
                String[] f = s.split("\\|", 3);
                if (f.length < 3) continue;
                try { all.add(new Object[]{Long.parseLong(f[0]), e.getValue().kind.label, e.getKey(), f[1], f[2]}); } catch (NumberFormatException ignored) { }
            }
        all.sort((a, b) -> Long.compare((Long) b[0], (Long) a[0]));
        List<String[]> out = new ArrayList<>();
        for (Object[] o : all) {
            if (out.size() >= 80) break;
            BlockPos p = BlockPos.fromLong((Long) o[2]);
            out.add(new String[]{History.clock((Long) o[0]), (String) o[1], p.getX() + " " + p.getY() + " " + p.getZ(), (String) o[3], (String) o[4]});
        }
        return out;
    }

    public static List<String> study(World w) {
        List<String> l = Protection.LAST.get(w.provider.getDimension());
        return l == null ? Collections.emptyList() : l;
    }
}
