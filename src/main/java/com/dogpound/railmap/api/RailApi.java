package com.dogpound.railmap.api;

import com.dogpound.railmap.program.ProgramData;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import java.util.*;

/**
 * IR Extras as a railway platform (her ChatGPT list #96-105): the one, versioned entry point other mods, computers
 * (OpenComputers Data Link: {@code rr.api("vehicles")}, ComputerCraft peripheral "irextras_api"), the web export and
 * commands use. Stable rules:
 * <ul>
 *   <li>LEVEL is the API level; a caller asks {@code hello(level)} and gets the level it will be served at, the
 *   capabilities present in this pack and the methods that level has. Methods are never removed or changed inside a
 *   level - new ones arrive with a higher {@code since}.</li>
 *   <li>Every method is READ (never changes the world) or CONTROL (changes it, always written to the audit log with
 *   who asked, before / after and the result).</li>
 *   <li>Optional mods are detected, never required: a missing mod only switches its adapter / capability off.</li>
 *   <li>{@code /irextras api selftest} is the compatibility test suite: it calls every READ method and checks the
 *   shapes, and checks every adapter and telemetry source.</li>
 * </ul>
 * Java mods: {@link #registerTransport}, {@link #registerTelemetry}, {@link #call}, {@link #post}.
 */
public final class RailApi {
    private RailApi() {}

    public static final String VERSION = "1.0.0";
    public static final int LEVEL = 1, MIN_LEVEL = 1;

    @FunctionalInterface
    public interface Handler { Object call(World w, Object[] a, Audit.Src src, String who); }

    public static final class Method {
        public final String name, doc;
        public final boolean control;
        public final int since;
        final Handler h;
        Method(String name, boolean control, int since, String doc, Handler h) { this.name = name; this.control = control; this.since = since; this.doc = doc; this.h = h; }
    }

    private static final Map<String, Method> METHODS = new LinkedHashMap<>();

    // ---- public Java entry points -----------------------------------------------------------------------------------
    public static void registerTransport(Transport.Adapter a) { Transport.register(a); }
    public static void registerTelemetry(Telemetry.Source s) { Telemetry.register(s); }
    /** an event for the signal-box program's EVENT conditions (e.g. "quest:started") */
    public static void post(World w, String event) { ProgramData.post(w, event); }

    public static Collection<Method> methods() { return Collections.unmodifiableCollection(METHODS.values()); }
    public static Method method(String n) { return METHODS.get(n); }

    /** call a method by name. Errors come back as {"error": text} (never thrown to the caller). */
    public static Object call(World w, String name, Object[] args, Audit.Src src, String who) {
        Method m = METHODS.get(name);
        if (m == null) return err("no API method '" + name + "' - try methods()");
        Object[] a = norm(args);
        try {
            Object r = m.h.call(w, a, src, who);
            if (m.control && !(name.startsWith("orders.") || name.startsWith("scenario.") || name.equals("vehicleCommand") || name.equals("press")))   // those log themselves with before / after
                Audit.log(w, src, who, null, "api", name + " " + Arrays.toString(a), "", "", String.valueOf(r));
            return r;
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            return err(name + ": " + e.getMessage());
        } catch (RuntimeException | LinkageError e) {
            return err(name + " failed: " + e);
        }
    }

    public static Map<String, Object> err(String s) { Map<String, Object> m = new LinkedHashMap<>(); m.put("error", s); return m; }

    private static Object[] norm(Object[] a) {
        if (a == null) return new Object[0];
        Object[] o = a.clone();
        for (int i = 0; i < o.length; i++) if (o[i] instanceof byte[] b) o[i] = new String(b, java.nio.charset.StandardCharsets.UTF_8);   // OpenComputers strings
        return o;
    }

    static String s(Object[] a, int i) { if (i >= a.length || a[i] == null) throw new IllegalArgumentException("argument " + (i + 1) + " missing"); return String.valueOf(a[i]); }
    static String s(Object[] a, int i, String d) { return i < a.length && a[i] != null ? String.valueOf(a[i]) : d; }
    static double d(Object[] a, int i) {
        if (i >= a.length || a[i] == null) throw new IllegalArgumentException("argument " + (i + 1) + " missing");
        if (a[i] instanceof Number n) return n.doubleValue();
        if (a[i] instanceof Boolean b) return b ? 1 : 0;
        try { return Double.parseDouble(String.valueOf(a[i])); } catch (NumberFormatException e) { throw new IllegalArgumentException("argument " + (i + 1) + " must be a number"); }
    }
    static double d(Object[] a, int i, double def) { return i < a.length && a[i] != null ? d(a, i) : def; }
    static int i(Object[] a, int k) { return (int) Math.floor(d(a, k)); }
    static boolean b(Object[] a, int i) { return i < a.length && (a[i] instanceof Boolean bb ? bb : a[i] instanceof Number n ? n.doubleValue() != 0 : a[i] != null && (String.valueOf(a[i]).equalsIgnoreCase("true") || String.valueOf(a[i]).equalsIgnoreCase("on"))); }
    static BlockPos pos(Object[] a, int k) { return new BlockPos(i(a, k), i(a, k + 1), i(a, k + 2)); }

    private static void def(String name, boolean control, String doc, Handler h) { METHODS.put(name, new Method(name, control, 1, doc, h)); }

    /** "key=value key2=value" or a Lua table -> map */
    @SuppressWarnings("unchecked")
    static Map<String, String> params(Object o) {
        Map<String, String> m = new LinkedHashMap<>();
        if (o instanceof Map<?, ?> mm) { for (Map.Entry<?, ?> e : mm.entrySet()) m.put(String.valueOf(e.getKey()), e.getValue() instanceof byte[] b ? new String(b) : String.valueOf(e.getValue())); return m; }
        if (o == null) return m;
        for (String p : String.valueOf(o).trim().split("\\s+")) { int i = p.indexOf('='); if (i > 0) m.put(p.substring(0, i), p.substring(i + 1)); }
        return m;
    }

    static UUID loco(World w, String v) {
        Transport.Vehicle x = Transport.find(w, v);
        if (x == null || !x.adapter.equals("ir")) throw new IllegalArgumentException("no loaded Immersive Railroading train '" + v + "'");
        return x.uuid;
    }

    static {
        // ---- discovery / versioning (#100) ----
        def("hello", false, "hello([level]) -> {version, level, minLevel, capabilities, methods}: negotiate the API level", (w, a, src, who) -> {
            int want = a.length > 0 ? i(a, 0) : LEVEL;
            if (want < MIN_LEVEL) return err("level " + want + " is no longer served (oldest: " + MIN_LEVEL + ")");
            int lvl = Math.min(want, LEVEL);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("version", VERSION); m.put("level", lvl); m.put("minLevel", MIN_LEVEL); m.put("maxLevel", LEVEL);
            m.put("capabilities", capabilities());
            List<String> ms = new ArrayList<>();
            for (Method x : METHODS.values()) if (x.since <= lvl) ms.add(x.name);
            m.put("methods", ms);
            return m;
        });
        def("methods", false, "methods() -> [{name, control, since, doc}]", (w, a, src, who) -> {
            List<Object> l = new ArrayList<>();
            for (Method x : METHODS.values()) { Map<String, Object> m = new LinkedHashMap<>(); m.put("name", x.name); m.put("control", x.control); m.put("since", x.since); m.put("doc", x.doc); l.add(m); }
            return l;
        });
        def("compat", false, "compat() -> the compatibility report: integrations present / working / missing", (w, a, src, who) -> compat(w));
        // ---- transport (#96) ----
        def("vehicles", false, "vehicles() -> every loaded train from every adapter (IR, Traincraft, PrideCarts, ...)", (w, a, src, who) -> {
            List<Object> l = new ArrayList<>();
            for (Transport.Vehicle v : Transport.vehicles(w)) l.add(v.map());
            return l;
        });
        def("vehicle", false, "vehicle(id|name) -> one vehicle", (w, a, src, who) -> { Transport.Vehicle v = Transport.find(w, s(a, 0)); return v == null ? err("no such vehicle") : v.map(); });
        def("vehicleCommand", true, "vehicleCommand(id|name, command [, arg]) -> result (send/stop/throttle_up... for IR, destination/stop for Traincraft + PrideCarts)",
                (w, a, src, who) -> Transport.command(w, s(a, 0), s(a, 1), s(a, 2, ""), src, who));
        // ---- telemetry (#97) ----
        def("telemetry", false, "telemetry(x,y,z) -> the standard reading of the machine there (schema 1)", (w, a, src, who) -> { Map<String, Object> m = Telemetry.read(w, pos(a, 0)); return m == null ? err("nothing that reports at that position (or unloaded)") : m; });
        def("telemetryAll", false, "telemetryAll([max]) -> every loaded machine that reports (IR Extras, grid, factory plants, registered sources)", (w, a, src, who) -> Telemetry.all(w, (int) d(a, 0, 200)));
        def("value", false, "value(expression) -> number: the program's expression language, e.g. value(\"@1,64,2.kw * 2\")", (w, a, src, who) -> Expr.sum(w, s(a, 0)));
        // ---- automation (#98) ----
        def("channel", false, "channel(n) -> boolean", (w, a, src, who) -> ProgramData.get(w).ch(i(a, 0)));
        def("setChannel", true, "setChannel(n, on) -> boolean (the channel's new state)", (w, a, src, who) -> {
            ProgramData d = ProgramData.get(w);
            int n = i(a, 0);
            boolean was = d.ch(n);
            d.setChannel(n, b(a, 1));
            Audit.log(w, src, who, null, "channel " + n, "set", was ? "on" : "off", d.ch(n) ? "on" : "off", "ok");
            return d.ch(n);
        });
        def("var", false, "var(name) -> number (program variable, 0 if unset)", (w, a, src, who) -> ProgramData.get(w).var(s(a, 0)));
        def("vars", false, "vars() -> {name: number} every program variable", (w, a, src, who) -> new LinkedHashMap<>(ProgramData.get(w).vars));
        def("setVar", true, "setVar(name, number) -> number", (w, a, src, who) -> {
            String n = s(a, 0);
            if (!n.matches("[A-Za-z_][A-Za-z0-9_.]*")) throw new IllegalArgumentException("bad variable name");
            ProgramData.get(w).setVar(n, d(a, 1));
            return d(a, 1);
        });
        def("event", true, "event(name) -> true: post an event for the program's EVENT conditions", (w, a, src, who) -> { ProgramData.post(w, s(a, 0)); return true; });
        def("program", false, "program() -> {running, halted, lastSteps, lastMicros, rules, routines, timers}", (w, a, src, who) -> {
            ProgramData d = ProgramData.get(w);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("running", d.halted.isEmpty()); m.put("halted", d.halted); m.put("lastSteps", d.lastSteps); m.put("lastMicros", d.lastMicros); m.put("rules", d.rules.size());
            Set<String> g = new TreeSet<>();
            for (com.dogpound.railmap.program.Rule r : d.rules) if (!r.group.isEmpty()) g.add(r.group);
            m.put("routines", new ArrayList<>(g));
            Map<String, Object> t = new LinkedHashMap<>();
            for (String k : d.timers.keySet()) t.put(k, d.timerLeft(w, k));
            m.put("timers", t);
            return m;
        });
        def("press", true, "press(x,y,z, control) -> result: press a machine's panel control exactly like its own button", (w, a, src, who) -> {
            BlockPos p = pos(a, 0);
            String id = s(a, 3);
            TileEntity te = w.isBlockLoaded(p) ? w.getTileEntity(p) : null;
            String r;
            if (te instanceof com.dogpound.railmap.controls.IControlled m) r = Audit.press(w, src, who, m, id, () -> com.dogpound.railmap.controls.IControlled.operate(m, id, null));
            else if (te instanceof com.dogpound.railmap.wire.IWiredMachine m) { r = m.wiredPress(id, who); Audit.log(w, src, who, p, m.wiredName(), "press " + id, "", "", r); }
            else return err("no machine with controls there");
            return r == null ? "ok" : r.replaceAll("§.", "");
        });
        // ---- train orders (#101) ----
        def("orders.templates", false, "orders.templates() -> {name: steps}", (w, a, src, who) -> new LinkedHashMap<>(TrainOrders.get(w).templates));
        def("orders.define", true, "orders.define(name, \"GOTO $origin; LOAD $load $cargo; GOTO $dest; UNLOAD; REPEAT\") -> ok / error", (w, a, src, who) -> {
            String r = TrainOrders.get(w).define(w, s(a, 0), s(a, 1), who);
            return r == null ? "ok" : err(r);
        });
        def("orders.assign", true, "orders.assign(train, template, \"origin=A dest=B load=80 cargo=coal window=06:00-18:00\") -> result",
                (w, a, src, who) -> { Transport.Vehicle v = Transport.find(w, s(a, 0)); UUID u = loco(w, s(a, 0)); return TrainOrders.get(w).assign(w, u, v.name, s(a, 1), params(a.length > 2 ? a[2] : null), src, who); });
        def("orders.cancel", true, "orders.cancel(train) -> result", (w, a, src, who) -> TrainOrders.get(w).cancel(w, loco(w, s(a, 0)), src, who));
        def("orders.list", false, "orders.list() -> [{train, template, params, step, laps, state}]", (w, a, src, who) -> {
            List<Object> l = new ArrayList<>();
            for (TrainOrders.Job j : TrainOrders.get(w).jobs.values()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("train", j.name); m.put("uuid", j.loco.toString()); m.put("template", j.template); m.put("params", new LinkedHashMap<>(j.params));
                m.put("step", j.step + 1); m.put("laps", j.laps); m.put("state", j.state);
                l.add(m);
            }
            return l;
        });
        // ---- audit (#102) ----
        def("audit", false, "audit([count [, filter]]) -> newest audit entries {time, source, who, target, action, before, after, result}", (w, a, src, who) -> {
            int n = (int) d(a, 0, 50);
            String f = s(a, 1, "").toLowerCase(Locale.ROOT);
            List<Object> l = new ArrayList<>();
            for (Audit.Entry e : Audit.get(w).entries) {
                if (l.size() >= n) break;
                if (!f.isEmpty() && !e.line().toLowerCase(Locale.ROOT).contains(f)) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("time", Audit.clock(e.tick)); m.put("tick", e.tick); m.put("source", e.src.name().toLowerCase(Locale.ROOT)); m.put("who", e.who);
                m.put("target", e.target); m.put("action", e.action); m.put("before", e.before); m.put("after", e.after); m.put("result", e.result);
                l.add(m);
            }
            return l;
        });
        // ---- export (#104) ----
        def("snapshot", false, "snapshot(name) -> the export document (trains, lines, alarms, stats, telemetry, vehicles)", (w, a, src, who) -> Export.doc(w, s(a, 0)));
        // ---- scenario (#105) ----
        def("scenario.save", true, "scenario.save() -> the scenario lab snapshots the railway", (w, a, src, who) -> ScenarioApi.labSave(w));
        def("scenario.set", true, "scenario.set(what, delta) -> what: fault, faultmin, dwell, speed, line, trains, reset", (w, a, src, who) -> ScenarioApi.labSet(w, s(a, 0), (int) d(a, 1, 0)));
        def("scenario.run", true, "scenario.run() -> plays the saved state forward as-is and with the changes", (w, a, src, who) -> ScenarioApi.labRun(w));
        def("scenario.result", false, "scenario.result() -> the last run's comparison rows", (w, a, src, who) -> ScenarioApi.labResult(w));
        def("scenario.describe", false, "scenario.describe() -> the changes set for the next run", (w, a, src, who) -> ScenarioApi.labDescribe(w));
        def("scenario.objective", true, "scenario.objective(name, expression, minutes [, hold]) -> REACH (or HOLD) an expression before the deadline",
                (w, a, src, who) -> ScenarioApi.get(w).objective(w, s(a, 0), s(a, 1), d(a, 2, 0), b(a, 3), src, who));
        def("scenario.cancel", true, "scenario.cancel(name) -> remove an objective", (w, a, src, who) -> ScenarioApi.get(w).cancel(w, s(a, 0), src, who));
        def("scenario.objectives", false, "scenario.objectives() -> [{name, expr, mode, state, secondsLeft}]", (w, a, src, who) -> ScenarioApi.get(w).objectiveList(w));
        def("scenario.inject", true, "scenario.inject(kind, x,y,z) -> a controlled fault through the Fault Lab (needs its TEST MODE)", (w, a, src, who) -> ScenarioApi.inject(w, s(a, 0), pos(a, 1), src, who));
        def("scenario.clearFaults", true, "scenario.clearFaults() -> clears every Fault Lab fault", (w, a, src, who) -> ScenarioApi.clearFaults(w, src, who));
        def("scenario.demand", true, "scenario.demand(station, percent [, minutes]) -> scale the station's passenger demand (restored after)",
                (w, a, src, who) -> ScenarioApi.get(w).demand(w, s(a, 0), (int) d(a, 1), d(a, 2, 0), src, who));
        def("scenario.demandReset", true, "scenario.demandReset() -> every station's demand back to normal", (w, a, src, who) -> ScenarioApi.get(w).demandReset(w, src, who));
        def("scenario.evaluate", false, "scenario.evaluate() -> today's KPIs [{what, value}]", (w, a, src, who) -> {
            List<Object> l = new ArrayList<>();
            for (String[] r : ScenarioApi.evaluate(w)) { Map<String, Object> m = new LinkedHashMap<>(); m.put("what", r[0]); m.put("value", r[1]); l.add(m); }
            return l;
        });
    }

    // ---- capabilities + the compatibility report (#100, #103) -------------------------------------------------------
    public static List<String> capabilities() {
        List<String> c = new ArrayList<>(Arrays.asList("transport", "telemetry", "automation", "orders", "audit", "scenario", "export.json"));
        if (Export.httpRunning()) c.add("export.http");
        for (Transport.Adapter a : Transport.adapters()) if (Transport.active(a)) c.add("transport." + a.id());
        if (Loader.isModLoaded("opencomputers")) c.add("computer.opencomputers");
        if (Loader.isModLoaded("computercraft")) c.add("computer.computercraft");
        if (Loader.isModLoaded("projectred-core")) c.add("redstone.projectred");
        c.add("telemetry.factory"); c.add("telemetry.prideindustry");   // factory plants (ex Pride Industry), built in; old name kept for scripts
        if (Loader.isModLoaded("dynmap")) c.add("map.dynmap");
        return c;
    }

    /** one row per integration: {area, name, mod, state (OK / MISSING / ERROR / OFF), detail} */
    public static List<Map<String, Object>> compat(World w) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (w != null) Transport.vehicles(w);                                             // refresh the adapters' counts / errors
        for (Transport.Adapter a : Transport.adapters()) {
            boolean on = Transport.active(a);
            String e = Transport.ERRORS.get(a.id());
            row(out, "Transport adapter", a.label(), a.modId(), !on ? "MISSING" : e != null ? "ERROR" : "OK",
                    !on ? "mod not installed - adapter off, railway unaffected" : e != null ? e : Transport.SEEN.getOrDefault(a.id(), 0) + " vehicles seen; commands: " + String.join(", ", a.commands()));
        }
        if (Loader.isModLoaded("tc") && Transport.adapters().stream().noneMatch(x -> x.id().equals("tc")))
            row(out, "Transport adapter", "Traincraft", "tc", "ERROR", Transport.ERRORS.getOrDefault("tc", "did not register"));
        row(out, "Telemetry", "IR Extras gear + grid (IWiredMachine)", "irextras", "OK", "built in");
        { List<String> bad = com.dogpound.railmap.factory.Factory.selfTest();   // factory: merged in, so check its old-world ids instead of the mod
          row(out, "Telemetry", "Factory plants (ex Pride Industry)", "irextras", bad.isEmpty() ? "OK" : "ERROR", bad.isEmpty() ? "built in, via each plant's control terminal (IWiredMachine); old prideindustry ids remap" : "missing: " + String.join(", ", bad)); }
        row(out, "Telemetry", "Forge inventories / tanks / energy (any mod)", "forge", "OK", "items, fluids, energy on every block entity");
        for (Telemetry.Source s : Telemetry.sources()) row(out, "Telemetry", "registered source " + s.id(), "?", "OK", "added by another mod");
        row(out, "Computer", "OpenComputers - Railroad Data Link", "opencomputers", Loader.isModLoaded("opencomputers") ? "OK" : "MISSING",
                Loader.isModLoaded("opencomputers") ? "component.railroad, rr.api(\"hello\")" : "not installed - the Data Link block does nothing");
        row(out, "Computer", "ComputerCraft - peripheral irextras_api", "computercraft", !Loader.isModLoaded("computercraft") ? "MISSING" : CCBridge.state(),
                Loader.isModLoaded("computercraft") ? CCBridge.detail() : "not installed");
        row(out, "Redstone", "Project Red bundled cable (Redstone Interface)", "projectred-core", Loader.isModLoaded("projectred-core") ? "OK" : "MISSING", "16 channels per bundle");
        row(out, "Map", "Dynmap web map", "dynmap", Loader.isModLoaded("dynmap") ? (com.dogpound.railmap.RailMap.dynmap != null ? "OK" : "ERROR") : "MISSING", "trains + signals on the web map");
        row(out, "Export", "JSON outbox (pride-web/irextras)", "irextras", ApiConfig.exportJson ? (Export.lastError.isEmpty() ? "OK" : "ERROR") : "OFF",
                ApiConfig.exportJson ? (Export.lastError.isEmpty() ? "every " + ApiConfig.exportSeconds + " s, last " + Export.lastWritten : Export.lastError) : "exportJson = false");
        row(out, "Export", "HTTP endpoint", "irextras", Export.httpRunning() ? "OK" : ApiConfig.httpPort == 0 ? "OFF" : "ERROR",
                Export.httpRunning() ? ApiConfig.httpBind + ":" + ApiConfig.httpPort + "/api/v1/  (" + Export.httpServed + " served, " + Export.httpRefused + " refused)" : ApiConfig.httpPort == 0 ? "httpPort = 0" : Export.httpError);
        row(out, "Automation", "Signal box program", "irextras", w == null ? "?" : ProgramData.get(w).halted.isEmpty() ? "OK" : "ERROR",
                w == null ? "" : ProgramData.get(w).halted.isEmpty() ? ProgramData.get(w).rules.size() + " rules, watchdog armed" : "HALTED: " + ProgramData.get(w).halted);
        row(out, "Automation", "Logic modules", "irextras", "OK", com.dogpound.railmap.logic.TileLogic.count(w) + " loaded");
        row(out, "API", "Versioned interface", "irextras", "OK", "version " + VERSION + ", level " + LEVEL + " (min " + MIN_LEVEL + "), " + METHODS.size() + " methods");
        for (String dep : new String[]{"immersiverailroading", "universalmodcore"}) {
            ModContainer mc = Loader.instance().getIndexedModList().get(dep);
            row(out, "Required", dep, dep, mc != null ? "OK" : "MISSING", mc != null ? "version " + mc.getVersion() : "IR Extras needs this");
        }
        return out;
    }

    private static void row(List<Map<String, Object>> out, String area, String name, String mod, String state, String detail) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("area", area); m.put("name", name); m.put("mod", mod == null ? "-" : mod); m.put("state", state); m.put("detail", detail);
        out.add(m);
    }

    // ---- the compatibility test suite (#100) ----------------------------------------------------------------------
    /** call every READ method with harmless arguments and check the shape; returns {pass, fail, lines} */
    public static List<String> selftest(World w) {
        List<String> out = new ArrayList<>();
        int pass = 0, fail = 0;
        Map<String, Object[]> args = new HashMap<>();
        args.put("vehicle", new Object[]{"nothing-by-this-name"});
        args.put("telemetry", new Object[]{0, 0, 0});
        args.put("value", new Object[]{"1 + 2 * 3"});
        args.put("channel", new Object[]{1});
        args.put("var", new Object[]{"selftest_unset"});
        args.put("audit", new Object[]{5});
        args.put("snapshot", new Object[]{"stats"});
        args.put("telemetryAll", new Object[]{5});
        for (Method m : METHODS.values()) {
            if (m.control) continue;
            Object r = call(w, m.name, args.getOrDefault(m.name, new Object[0]), Audit.Src.API, "selftest");
            boolean expectErr = m.name.equals("vehicle") || m.name.equals("telemetry");
            boolean isErr = r instanceof Map<?, ?> mm && mm.containsKey("error");
            boolean ok = r != null && (expectErr || !isErr);
            if (m.name.equals("value")) ok = r instanceof Double dd && dd == 9.0;      // left to right: (1 + 2) * 3
            if (m.name.equals("hello")) ok = r instanceof Map<?, ?> h && Integer.valueOf(LEVEL).equals(h.get("level"));
            if (ok) pass++; else fail++;
            out.add((ok ? "§aPASS " : "§cFAIL ") + "§f" + m.name + "§7 -> " + Audit.cut(String.valueOf(r), 70));
        }
        // the expression language
        String[][] ex = {{"2 + 3 * 4", "20"}, {"10 / 4", "2.5"}, {"-3 + 5", "2"}, {"7 % 4", "3"}};
        for (String[] e : ex) {
            double v = Expr.sum(w, e[0]);
            boolean ok = Math.abs(v - Double.parseDouble(e[1])) < 1e-9;
            if (ok) pass++; else fail++;
            out.add((ok ? "§aPASS " : "§cFAIL ") + "§fexpr " + e[0] + "§7 = " + Expr.fmt(v) + " (want " + e[1] + ")");
        }
        boolean t = Expr.test(w, "1 < 2 && 3 >= 3 || 0", null), f = Expr.test(w, "1 > 2", null);
        if (t && !f) pass++; else fail++;
        out.add((t && !f ? "§aPASS " : "§cFAIL ") + "§fconditions (&&, ||, < >=)");
        for (Map<String, Object> r : compat(w)) if ("ERROR".equals(r.get("state"))) { fail++; out.add("§cFAIL §f" + r.get("name") + "§7 " + r.get("detail")); }
        out.add(0, "§eAPI self-test v" + VERSION + " level " + LEVEL + ": §a" + pass + " pass §c" + fail + " fail");
        return out;
    }
}
