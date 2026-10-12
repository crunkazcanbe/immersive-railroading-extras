package com.dogpound.railmap.api;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.server.StationData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * External telemetry export (her ChatGPT list #104), read-only, following the Pride web-hook standard
 * (~/mc-mods/research/WEB-HOOKS.md): every exportSeconds the overworld's railway is written to
 * {@code <world>/pride-web/irextras/<name>.json} as {"mod","version","written","data"} - written to .tmp then moved, so
 * a reader never sees half a file. Names: trains (every adapter's vehicles), lines, alarms, stats, telemetry, compat.
 * <p>
 * Optional HTTP: httpPort > 0 AND an exportToken serve the same documents at GET /api/v1/&lt;name&gt; (and /api/v1/ =
 * the list), only with "Authorization: Bearer &lt;token&gt;", rate-limited per client address, bound to 127.0.0.1 unless
 * the config says otherwise. It never accepts anything that changes the world. The documents are built on the server
 * thread; the HTTP threads only hand out the last built text.
 */
public final class Export {
    private Export() {}

    public static final String[] NAMES = {"trains", "lines", "alarms", "stats", "telemetry", "compat"};
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Map<String, String> LAST = new ConcurrentHashMap<>();
    static String lastError = "", lastWritten = "never", httpError = "";
    static long httpServed, httpRefused;
    private static Object http;                                  // a com.sun HttpServer, touched only inside Http (a JRE without jdk.httpserver just has no endpoint)
    private static int httpPortOpen;
    private static final Map<String, long[]> RATE = new ConcurrentHashMap<>();

    /** one document's data (server thread) */
    public static Object doc(World w, String name) {
        switch (name) {
            case "trains": {
                List<Object> l = new ArrayList<>();
                for (Transport.Vehicle v : Transport.vehicles(w)) l.add(v.map());
                return l;
            }
            case "lines": {
                List<Object> l = new ArrayList<>();
                RailwayData data = RailwayData.get(w);
                StationData st = StationData.get(w);
                for (RailwayData.Line ln : data.lines().values()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", ln.name); m.put("colour", String.format("#%06X", ln.color & 0xFFFFFF)); m.put("movingBlock", ln.movingBlock);
                    List<String> stops = new ArrayList<>();
                    for (long k : ln.stations) stops.add(com.dogpound.railmap.auto.Autopilot.nameOf(st, k));
                    m.put("stops", stops);
                    int trains = 0, held = 0;
                    for (RailwayData.AutoTrain a : data.trains().values()) if (ln.name.equals(a.line)) {
                        trains++;
                        if (com.dogpound.railmap.auto.Disruptions.isHeld(w.provider.getDimension(), a.loco)) held++;
                    }
                    m.put("trains", trains); m.put("held", held);
                    m.put("status", trains == 0 ? "no service" : held > 0 ? "disrupted" : "good service");
                    l.add(m);
                }
                return l;
            }
            case "alarms": {
                List<Object> l = new ArrayList<>();
                long now = w.getTotalWorldTime();
                for (AlarmManager.Alarm a : AlarmManager.get(w).all()) {
                    int seq = a.seq(now);
                    if (seq == 0) continue;
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", a.id); m.put("category", a.cat.name()); m.put("severity", a.sev.name()); m.put("text", a.text);
                    m.put("state", seq == 1 ? "new" : seq == 2 ? "acknowledged" : "cleared"); m.put("count", a.count);
                    if (a.pos != 0) { BlockPos p = BlockPos.fromLong(a.pos); m.put("x", p.getX()); m.put("y", p.getY()); m.put("z", p.getZ()); }
                    l.add(m);
                }
                return l;
            }
            case "stats": {
                Map<String, Object> m = new LinkedHashMap<>();
                for (String[] r : ScenarioApi.evaluate(w)) m.put(r[0], r[1]);
                m.put("Vehicles (all mods)", Transport.vehicles(w).size());
                m.put("Driverless trains", RailwayData.get(w).trains().size());
                m.put("Lines", RailwayData.get(w).lines().size());
                long now = w.getTotalWorldTime();
                m.put("Alarms standing", AlarmManager.get(w).count(1, now) + AlarmManager.get(w).count(2, now));
                m.put("Program", com.dogpound.railmap.program.ProgramData.get(w).halted.isEmpty() ? "running" : "HALTED");
                m.put("Train orders running", TrainOrders.get(w).jobs.size());
                m.put("Objectives", ScenarioApi.get(w).objectiveList(w));
                return m;
            }
            case "telemetry": return Telemetry.all(w, 500);
            case "compat": return RailApi.compat(w);
            default: throw new IllegalArgumentException("export documents: " + String.join(", ", NAMES));
        }
    }

    private static String wrap(Object data) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mod", "irextras"); m.put("version", RailApi.VERSION); m.put("apiLevel", RailApi.LEVEL); m.put("written", System.currentTimeMillis()); m.put("data", data);
        return GSON.toJson(m);
    }

    /** build every document, write the outbox, refresh what HTTP serves (server thread, overworld) */
    public static String snapshot(World w) {
        int n = 0;
        File dir = new File(w.getSaveHandler().getWorldDirectory(), "pride-web/irextras");
        try {
            for (String name : NAMES) {
                String text;
                try { text = wrap(doc(w, name)); } catch (RuntimeException | LinkageError e) { text = wrap(RailApi.err(e.toString())); }
                LAST.put(name, text);
                if (ApiConfig.exportJson) {
                    if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
                    File tmp = new File(dir, name + ".json.tmp"), out = new File(dir, name + ".json");
                    Files.write(tmp.toPath(), text.getBytes(StandardCharsets.UTF_8));
                    Files.move(tmp.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    n++;
                }
            }
            lastError = "";
            lastWritten = Audit.clock(w.getTotalWorldTime());
        } catch (IOException | RuntimeException e) {
            lastError = e.toString();
        }
        http();
        return n == 0 ? "documents built (outbox off)" : n + " documents written to " + dir;
    }

    // ---- HTTP (read only) ------------------------------------------------------------------------------------------
    static boolean httpRunning() { return http != null; }

    /** start / stop / restart to match the config */
    static synchronized void http() {
        boolean want = ApiConfig.httpPort > 0 && !ApiConfig.exportToken.trim().isEmpty();
        if (http != null && (!want || httpPortOpen != ApiConfig.httpPort)) stopHttp();
        if (!want) { httpError = ApiConfig.httpPort > 0 ? "exportToken is empty - refusing to serve without one" : ""; return; }
        if (http != null) return;
        try {
            http = Http.start();
            httpPortOpen = ApiConfig.httpPort;
            httpError = "";
            com.dogpound.railmap.RailMap.LOG.info("[IR Extras] read-only API on http://{}:{}/api/v1/", ApiConfig.httpBind, ApiConfig.httpPort);
        } catch (IOException | RuntimeException | LinkageError e) {
            httpError = "could not open " + ApiConfig.httpBind + ":" + ApiConfig.httpPort + ": " + e;
        }
    }

    public static synchronized void stopHttp() {
        if (http == null) return;
        try { Http.stop(http); } catch (LinkageError ignored) { }
        http = null;
    }

    private static final class Http {
        static Object start() throws IOException {
            HttpServer s = HttpServer.create(new InetSocketAddress(ApiConfig.httpBind, ApiConfig.httpPort), 8);
            s.createContext("/api/v1/", Http::serve);
            s.setExecutor(Executors.newFixedThreadPool(2, r -> { Thread t = new Thread(r, "IRExtras-API-HTTP"); t.setDaemon(true); return t; }));
            s.start();
            return s;
        }

        static void stop(Object s) { ((HttpServer) s).stop(0); }

        private static void serve(HttpExchange ex) throws IOException {
            try {
                String ip = ex.getRemoteAddress().getAddress().getHostAddress();
                if (!"GET".equals(ex.getRequestMethod())) { reply(ex, 405, "{\"error\":\"read-only: GET only\"}"); return; }
                String auth = ex.getRequestHeaders().getFirst("Authorization");
                if (auth == null || !java.security.MessageDigest.isEqual(auth.getBytes(StandardCharsets.UTF_8), ("Bearer " + ApiConfig.exportToken.trim()).getBytes(StandardCharsets.UTF_8))) {
                    httpRefused++;
                    reply(ex, 401, "{\"error\":\"missing or wrong bearer token\"}");
                    return;
                }
                long now = System.currentTimeMillis();
                long[] r = RATE.computeIfAbsent(ip, k -> new long[]{now, 0});
                synchronized (r) {
                    if (now - r[0] > 60_000) { r[0] = now; r[1] = 0; }
                    if (++r[1] > ApiConfig.httpPerMinute) { httpRefused++; reply(ex, 429, "{\"error\":\"rate limit: " + ApiConfig.httpPerMinute + " requests a minute\"}"); return; }
                }
                if (RATE.size() > 1000) RATE.clear();
                String name = ex.getRequestURI().getPath().substring("/api/v1/".length()).replace(".json", "");
                if (name.isEmpty()) { httpServed++; reply(ex, 200, GSON.toJson(Arrays.asList(NAMES))); return; }
                String text = LAST.get(name);
                if (text == null) { reply(ex, 404, "{\"error\":\"no document '" + name.replaceAll("[^a-z]", "") + "' (yet)\"}"); return; }
                httpServed++;
                reply(ex, 200, text);
            } finally {
                ex.close();
            }
        }

        private static void reply(HttpExchange ex, int code, String body) throws IOException {
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            ex.getResponseHeaders().set("Cache-Control", "no-store");
            ex.sendResponseHeaders(code, b.length);
            try (OutputStream o = ex.getResponseBody()) { o.write(b); }
        }
    }

}
