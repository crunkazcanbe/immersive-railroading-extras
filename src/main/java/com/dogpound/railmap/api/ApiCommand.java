package com.dogpound.railmap.api;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.*;

/**
 * /irextras api ... - the platform API from chat (her ChatGPT list #96-105). Reading is open to everyone; anything that
 * changes the railway needs op (permission level 2) and lands in the audit log with the player's name.
 */
public final class ApiCommand {
    private ApiCommand() {}

    public static final String USAGE = "/irextras api version | compat | selftest | methods | call <method> [args] | vehicles | telemetry [x y z] | audit [n] [filter] | export"
            + " | orders [templates | define <name> <steps> | assign <train> <template> k=v.. | cancel <train>] | objective <name> <minutes> reach|hold <expr> | objectives | program [reset]";

    public static boolean run(ICommandSender s, World w, String[] a) {
        if (a.length == 0 || !a[0].equalsIgnoreCase("api")) return false;
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "version";
        String who = s.getName();
        boolean op = s.canUseCommand(2, "irextras");
        switch (sub) {
            case "version": case "hello": {
                say(s, "§bIR Extras railway API §f" + RailApi.VERSION + "§7 level " + RailApi.LEVEL + " (oldest served " + RailApi.MIN_LEVEL + "), " + RailApi.methods().size() + " methods");
                say(s, "§7Capabilities: §f" + String.join(", ", RailApi.capabilities()));
                return true;
            }
            case "compat": {
                say(s, "§b== Mod compatibility report");
                for (Map<String, Object> r : RailApi.compat(w)) {
                    String st = String.valueOf(r.get("state"));
                    String c = st.equals("OK") ? "§a" : st.equals("ERROR") ? "§c" : st.equals("OFF") ? "§7" : "§6";
                    say(s, c + st + " §f" + r.get("area") + ": " + r.get("name") + " §8(" + r.get("mod") + ")§7 " + r.get("detail"));
                }
                return true;
            }
            case "selftest": for (String l : RailApi.selftest(w)) say(s, l); return true;
            case "methods": {
                for (RailApi.Method m : RailApi.methods()) say(s, (m.control ? "§6CONTROL " : "§aREAD ") + "§f" + m.doc);
                return true;
            }
            case "call": {
                if (a.length < 3) { say(s, "§c/irextras api call <method> [args...]"); return true; }
                RailApi.Method m = RailApi.method(a[2]);
                if (m != null && m.control && !op) { say(s, "§cThat API call changes the railway - ops only"); return true; }
                Object[] args = new Object[a.length - 3];
                for (int i = 3; i < a.length; i++) args[i - 3] = parse(a[i]);
                Object r = RailApi.call(w, a[2], args, Audit.Src.PLAYER, who);
                show(s, r, 0);
                return true;
            }
            case "vehicles": {
                List<Transport.Vehicle> vs = Transport.vehicles(w);
                say(s, "§b== " + vs.size() + " vehicles (all adapters)");
                for (Transport.Vehicle v : vs) say(s, String.format(Locale.ROOT, "§f%s §7[%s] %s, %d cars, %.0f km/h at %.0f %.0f %.0f%s%s", v.name, v.id, v.kind, v.cars, v.speedKmh, v.x, v.y, v.z,
                        v.destination.isEmpty() ? "" : " -> " + v.destination, v.cargo > 0 ? ", cargo " + v.cargo + (v.fillPct >= 0 ? " (" + v.fillPct + "%)" : "") : ""));
                return true;
            }
            case "telemetry": {
                BlockPos p = a.length >= 5 ? new BlockPos(Integer.parseInt(a[2]), Integer.parseInt(a[3]), Integer.parseInt(a[4])) : looked(s);
                if (p == null) { say(s, "§cLook at a machine or give x y z"); return true; }
                Map<String, Object> m = Telemetry.read(w, p);
                if (m == null) { say(s, "§7Nothing that reports at " + p.getX() + " " + p.getY() + " " + p.getZ()); return true; }
                show(s, m, 0);
                return true;
            }
            case "audit": {
                int n = a.length > 2 ? Math.max(1, Math.min(100, Integer.parseInt(a[2]))) : 15;
                String f = a.length > 3 ? String.join(" ", Arrays.copyOfRange(a, 3, a.length)).toLowerCase(Locale.ROOT) : "";
                say(s, "§b== Audit log, newest first" + (f.isEmpty() ? "" : " (matching '" + f + "')"));
                int k = 0;
                for (Audit.Entry e : Audit.get(w).entries) {
                    if (!f.isEmpty() && !e.line().toLowerCase(Locale.ROOT).contains(f)) continue;
                    say(s, (e.refused() ? "§c" : "§7") + e.line());
                    if (++k >= n) break;
                }
                return true;
            }
            case "export": {
                if (!op) { say(s, "§cOps only"); return true; }
                say(s, "§e" + Export.snapshot(w.getMinecraftServer() == null ? w : w.getMinecraftServer().getWorld(0)));
                return true;
            }
            case "orders": return orders(s, w, a, op, who);
            case "objective": {
                if (!op) { say(s, "§cOps only"); return true; }
                if (a.length < 6) { say(s, "§c/irextras api objective <name> <minutes> reach|hold <expression>"); return true; }
                say(s, "§e" + ScenarioApi.get(w).objective(w, a[2], String.join(" ", Arrays.copyOfRange(a, 5, a.length)), Double.parseDouble(a[3]), a[4].equalsIgnoreCase("hold"), Audit.Src.PLAYER, who));
                return true;
            }
            case "objectives": {
                List<Map<String, Object>> l = ScenarioApi.get(w).objectiveList(w);
                say(s, "§b== " + l.size() + " objectives");
                for (Map<String, Object> o : l) say(s, "§f" + o.get("name") + " §7" + o.get("mode") + " " + o.get("expr") + " §e" + o.get("state") + ((long) o.get("secondsLeft") >= 0 ? "§7 " + o.get("secondsLeft") + " s left" : ""));
                return true;
            }
            case "program": {
                com.dogpound.railmap.program.ProgramData d = com.dogpound.railmap.program.ProgramData.get(w);
                if (a.length > 2 && a[2].equalsIgnoreCase("reset")) { if (!op) { say(s, "§cOps only"); return true; } say(s, "§e" + d.resetWatchdog(w, who)); return true; }
                say(s, d.halted.isEmpty() ? "§aProgram running§7: " + d.rules.size() + " rules, last pass " + d.lastSteps + " steps / " + d.lastMicros + " µs (worst " + d.worstMicros + ")" : "§cProgram HALTED by the watchdog: " + d.halted);
                say(s, "§7Variables: " + d.vars + "  Timers: " + d.timers.keySet());
                return true;
            }
            default: say(s, "§7" + USAGE); return true;
        }
    }

    private static boolean orders(ICommandSender s, World w, String[] a, boolean op, String who) {
        TrainOrders o = TrainOrders.get(w);
        String sub = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "list";
        switch (sub) {
            case "templates":
                for (Map.Entry<String, String> e : o.templates.entrySet()) say(s, "§f" + e.getKey() + "§7: " + e.getValue());
                say(s, "§7Steps: GOTO <stop>, LOAD <pct> [cargo], UNLOAD, WAIT <s>, WINDOW hh:mm-hh:mm, WAITCH <n>, SET <n> on|off, EMIT <event>, REPEAT, END; $param from assign");
                return true;
            case "define": {
                if (!op) { say(s, "§cOps only"); return true; }
                if (a.length < 5) { say(s, "§c/irextras api orders define <name> <step; step; ...>"); return true; }
                String r = o.define(w, a[3], String.join(" ", Arrays.copyOfRange(a, 4, a.length)), who);
                say(s, r == null ? "§aTemplate " + a[3] + " saved" : "§c" + r);
                return true;
            }
            case "assign": {
                if (!op) { say(s, "§cOps only"); return true; }
                if (a.length < 5) { say(s, "§c/irextras api orders assign <train id|name> <template> origin=A dest=B load=80 cargo=coal window=06:00-18:00"); return true; }
                Object r = RailApi.call(w, "orders.assign", new Object[]{a[3], a[4], String.join(" ", Arrays.copyOfRange(a, 5, a.length))}, Audit.Src.PLAYER, who);
                show(s, r, 0);
                return true;
            }
            case "cancel": {
                if (!op) { say(s, "§cOps only"); return true; }
                if (a.length < 4) { say(s, "§c/irextras api orders cancel <train>"); return true; }
                show(s, RailApi.call(w, "orders.cancel", new Object[]{a[3]}, Audit.Src.PLAYER, who), 0);
                return true;
            }
            default:
                say(s, "§b== " + o.jobs.size() + " trains on orders");
                for (TrainOrders.Job j : o.jobs.values()) say(s, "§f" + j.name + " §7" + j.template + " " + j.params + " §elap " + j.laps + ", step " + (j.step + 1) + "§7 - " + j.state);
                return true;
        }
    }

    private static Object parse(String t) {
        if (t.equalsIgnoreCase("true") || t.equalsIgnoreCase("false")) return Boolean.parseBoolean(t);
        try { return Double.parseDouble(t); } catch (NumberFormatException e) { return t; }
    }

    private static BlockPos looked(ICommandSender s) {
        if (!(s instanceof EntityPlayer p)) return null;
        RayTraceResult r = p.rayTrace(8, 1f);
        return r != null && r.typeOfHit == RayTraceResult.Type.BLOCK ? r.getBlockPos() : null;
    }

    @SuppressWarnings("unchecked")
    private static void show(ICommandSender s, Object o, int depth) {
        String pad = depth == 0 ? "" : depth == 1 ? "  " : "    ";
        if (o instanceof Map<?, ?> m) {
            if (m.containsKey("error")) { say(s, pad + "§c" + m.get("error")); return; }
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if ((e.getValue() instanceof Map || e.getValue() instanceof List) && depth < 2) { say(s, pad + "§b" + e.getKey() + ":"); show(s, e.getValue(), depth + 1); }
                else say(s, pad + "§7" + e.getKey() + ": §f" + Audit.cut(String.valueOf(e.getValue()), 90));
            }
        } else if (o instanceof List<?> l) {
            int i = 0;
            for (Object x : l) { if (i++ >= 30) { say(s, pad + "§8... " + (l.size() - 30) + " more"); break; } if (x instanceof Map && depth < 2) { say(s, pad + "§8#" + i); show(s, x, depth + 1); } else say(s, pad + "§f" + x); }
            if (l.isEmpty()) say(s, pad + "§8(none)");
        } else say(s, pad + "§f" + o);
    }

    private static void say(ICommandSender s, String m) { s.sendMessage(new TextComponentString(m)); }
}
