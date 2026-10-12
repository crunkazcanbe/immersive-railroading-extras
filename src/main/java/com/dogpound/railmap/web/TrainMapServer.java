package com.dogpound.railmap.web;

import com.dogpound.railmap.RailMap;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * The optional train map web server (OFF until port is set). Strictly read-only: GET / HEAD only, it serves the page
 * (web/trainmap in the jar) and the last feed text TrainMapFeed built - nothing a request says can reach the world.
 * Rate-limited per address (behind a reverse proxy on this machine, per X-Forwarded-For). A JRE without
 * jdk.httpserver simply has no map server.
 */
public final class TrainMapServer {
    private TrainMapServer() {}

    /** the page files: published name -> content type */
    static final Map<String, String> FILES = new LinkedHashMap<>();
    static {
        FILES.put("index.html", "text/html; charset=utf-8");
        FILES.put("app.js", "text/javascript; charset=utf-8");
        FILES.put("style.css", "text/css; charset=utf-8");
    }
    private static final Map<String, byte[]> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, long[]> RATE = new ConcurrentHashMap<>();
    private static Object http;
    private static int open;
    private static String openBind = "";
    static String error = "";
    static long served, refused;

    static boolean running() { return http != null; }

    /** start / stop / restart to match the config (server thread, once a second) */
    static synchronized void sync() {
        boolean want = TrainMapConfig.port > 0;
        if (http != null && (!want || open != TrainMapConfig.port || !openBind.equals(TrainMapConfig.bind))) stop();
        if (!want || http != null) { if (!want) { error = ""; open = 0; } return; }
        if (!error.isEmpty() && open == TrainMapConfig.port && openBind.equals(TrainMapConfig.bind)) return;   // failed on this port: wait for a config change
        try {
            http = Http.start();
            open = TrainMapConfig.port;
            openBind = TrainMapConfig.bind;
            error = "";
        } catch (IOException | RuntimeException | LinkageError e) {
            error = "could not open " + TrainMapConfig.bind + ":" + TrainMapConfig.port + ": " + e;
            open = TrainMapConfig.port;
            openBind = TrainMapConfig.bind;
            http = null;
            return;
        }
        RailMap.LOG.info("[IR Extras] train map on http://{}:{}/", TrainMapConfig.bind, TrainMapConfig.port);
    }

    public static synchronized void stop() {
        if (http == null) return;
        try { Http.stop(http); } catch (LinkageError ignored) { }
        http = null;
    }

    /** a page file from the jar (cached), or null */
    static byte[] resource(String name) {
        if (!FILES.containsKey(name)) return null;
        return CACHE.computeIfAbsent(name, n -> {
            try (InputStream in = TrainMapServer.class.getResourceAsStream("/assets/" + RailMap.MODID + "/web/trainmap/" + n)) {
                if (in == null) return null;
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                byte[] b = new byte[8192];
                for (int r; (r = in.read(b)) > 0; ) o.write(b, 0, r);
                return o.toByteArray();
            } catch (IOException e) { return null; }
        });
    }

    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = RailMap.MODID)
    public static final class Unload {
        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
        public static void unload(net.minecraftforge.event.world.WorldEvent.Unload e) {
            if (!e.getWorld().isRemote && e.getWorld().provider.getDimension() == TrainMapConfig.dimension) { stop(); open = 0; }
        }
    }

    private static final class Http {
        static Object start() throws IOException {
            HttpServer s = HttpServer.create(new InetSocketAddress(TrainMapConfig.bind, TrainMapConfig.port), 16);
            s.createContext("/", Http::serve);
            s.setExecutor(Executors.newFixedThreadPool(3, r -> { Thread t = new Thread(r, "IRExtras-TrainMap-HTTP"); t.setDaemon(true); return t; }));
            s.start();
            return s;
        }

        static void stop(Object s) { ((HttpServer) s).stop(0); }

        private static void serve(HttpExchange ex) throws IOException {
            try {
                String m = ex.getRequestMethod();
                if (!"GET".equals(m) && !"HEAD".equals(m)) { reply(ex, 405, "application/json", "{\"error\":\"read-only: GET only\"}".getBytes(StandardCharsets.UTF_8), null); return; }
                String ip = ex.getRemoteAddress().getAddress().getHostAddress();
                String fwd = ex.getRequestHeaders().getFirst("X-Forwarded-For");
                java.net.InetAddress from = ex.getRemoteAddress().getAddress();
                if (fwd != null && (from.isLoopbackAddress() || from.isSiteLocalAddress())) ip = fwd.split(",")[0].trim();   // only a proxy on this machine / the LAN (Docker bridge) is believed
                long now = System.currentTimeMillis();
                long[] r = RATE.computeIfAbsent(ip, k -> new long[]{now, 0});
                synchronized (r) {
                    if (now - r[0] > 60_000) { r[0] = now; r[1] = 0; }
                    if (++r[1] > TrainMapConfig.perMinute) { refused++; reply(ex, 429, "application/json", "{\"error\":\"slow down\"}".getBytes(StandardCharsets.UTF_8), null); return; }
                }
                if (RATE.size() > 2000) RATE.clear();
                String path = ex.getRequestURI().getPath();
                int slash = path.lastIndexOf('/');
                String name = slash < 0 ? path : path.substring(slash + 1);
                if (name.isEmpty()) name = "index.html";
                switch (name) {
                    case "live.json" -> json(ex, TrainMapFeed.live, TrainMapFeed.liveAt);
                    case "tracks.json" -> json(ex, TrainMapFeed.tracks, TrainMapFeed.tracksAt);
                    default -> {
                        byte[] b = resource(name);
                        if (b == null) { reply(ex, 404, "text/plain; charset=utf-8", "not here".getBytes(StandardCharsets.UTF_8), null); return; }
                        served++;
                        reply(ex, 200, FILES.get(name), b, "public, max-age=300");
                    }
                }
            } finally {
                ex.close();
            }
        }

        private static void json(HttpExchange ex, String text, long at) throws IOException {
            String tag = "\"" + Long.toHexString(at) + "-" + Integer.toHexString(text.hashCode()) + "\"";
            ex.getResponseHeaders().set("ETag", tag);
            if (tag.equals(ex.getRequestHeaders().getFirst("If-None-Match"))) { served++; ex.sendResponseHeaders(304, -1); return; }
            served++;
            reply(ex, 200, "application/json; charset=utf-8", text.getBytes(StandardCharsets.UTF_8), "no-cache");
        }

        private static void reply(HttpExchange ex, int code, String type, byte[] b, String cache) throws IOException {
            ex.getResponseHeaders().set("Content-Type", type);
            ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            ex.getResponseHeaders().set("Referrer-Policy", "no-referrer");
            if (cache != null) ex.getResponseHeaders().set("Cache-Control", cache);
            if (!TrainMapConfig.corsOrigin.isEmpty()) ex.getResponseHeaders().set("Access-Control-Allow-Origin", TrainMapConfig.corsOrigin);
            boolean head = "HEAD".equals(ex.getRequestMethod());
            ex.sendResponseHeaders(code, head ? -1 : b.length);
            if (!head) try (OutputStream o = ex.getResponseBody()) { o.write(b); }
        }
    }
}
