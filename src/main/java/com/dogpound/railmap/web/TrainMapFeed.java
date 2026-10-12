package com.dogpound.railmap.web;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.api.Export;
import com.dogpound.railmap.api.Transport;
import com.dogpound.railmap.block.TileRailDisplay;
import com.dogpound.railmap.graph.Chains;
import com.dogpound.railmap.graph.RailNetwork;
import com.dogpound.railmap.graph.SignalNode;
import com.dogpound.railmap.graph.TrainNode;
import com.dogpound.railmap.server.StationData;
import com.dogpound.railmap.server.TrainTracker;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * The live train map feed (her ChatGPT list #7). Two read-only JSON documents, built on the server thread and handed
 * out as finished text by {@link TrainMapServer} (and optionally the Pride web-hook outbox):
 * <ul>
 * <li>{@code tracks.json} - the rail graph as polylines (walked from every dispatcher board / Control Center console,
 * plus every named station no board covers), station positions and switches. Rebuilt every trackSeconds.</li>
 * <li>{@code live.json} - trains (position, speed, line, next stop, delay), lines, signals, stations with crowds and
 * coming trains, alarms and the CCTV camera list (names and status only - never video). Every liveSeconds.</li>
 * </ul>
 * Only what the {@code publish} allowlist names goes out; player names are scrubbed; no IPs, no UUIDs, no owners.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class TrainMapFeed {
    private TrainMapFeed() {}

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    static volatile String live = "{}", tracks = "{}";
    static volatile long liveAt, tracksAt;
    static String lastError = "";
    private static long lastTracksTick = -1_000_000;
    private static final Map<Integer, List<RailNetwork>> NETS = new HashMap<>();
    private static final Map<Integer, Long> NETS_AT = new HashMap<>();
    private static Set<String> names = Collections.emptySet();
    private static long namesAt;

    // ---- ticking ------------------------------------------------------------------------------------------------------
    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote || e.world.provider.getDimension() != TrainMapConfig.dimension) return;
        long now = e.world.getTotalWorldTime();
        if (now % 20 == 7) TrainMapServer.sync();
        boolean wanted = TrainMapConfig.port > 0 || TrainMapConfig.outbox;
        if (!wanted || now % (20L * TrainMapConfig.liveSeconds) != 3) return;
        try { build(e.world, now - lastTracksTick >= 20L * TrainMapConfig.trackSeconds); }
        catch (RuntimeException | LinkageError ex) {
            lastError = ex.toString();
            if (now % 1200 < 40) RailMap.LOG.warn("[IR Extras] train map feed: {}", ex.toString());
        }
    }

    /** build the documents now (server thread); tracks too when asked */
    public static String build(World w, boolean withTracks) {
        if (withTracks) {
            tracks = GSON.toJson(tracksDoc(w));
            tracksAt = System.currentTimeMillis();
            lastTracksTick = w.getTotalWorldTime();
        }
        live = GSON.toJson(liveDoc(w));
        liveAt = System.currentTimeMillis();
        lastError = "";
        if (TrainMapConfig.outbox) {
            File dir = new File(w.getSaveHandler().getWorldDirectory(), "pride-web/irextras");
            try {
                if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("cannot create " + dir);
                write(dir, "trainmap-live.json", live);
                if (withTracks) write(dir, "trainmap-tracks.json", tracks);
            } catch (java.io.IOException ex) { lastError = ex.toString(); }
        }
        return live.length() + " B live, " + tracks.length() + " B tracks";
    }

    private static void write(File dir, String name, String text) throws java.io.IOException {
        File tmp = new File(dir, name + ".tmp"), out = new File(dir, name);
        Files.write(tmp.toPath(), text.getBytes(StandardCharsets.UTF_8));
        Files.move(tmp.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    // ---- the rail graph -------------------------------------------------------------------------------------------------
    /**
     * Every network the railway knows: each loaded dispatcher board / console's last scan, plus a fresh walk from each
     * named station no board covers. Cached for trackSeconds (the walk is bounded by the scanner's node budget).
     * Also used by the model railway's mirror mode.
     */
    public static synchronized List<RailNetwork> networks(World w) {
        long now = w.getTotalWorldTime();
        int dim = w.provider.getDimension();
        long at = NETS_AT.getOrDefault(dim, -1_000_000L);
        if (now - at < 20L * TrainMapConfig.trackSeconds && now >= at && NETS.containsKey(dim)) return NETS.get(dim);
        List<RailNetwork> out = new ArrayList<>();
        Set<RailNetwork> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (TileEntity t : new ArrayList<>(w.loadedTileEntityList)) {
            if (!(t instanceof TileRailDisplay d) || t.isInvalid()) continue;
            RailNetwork n = d.getNetwork();
            if (n != null && !n.isEmpty() && seen.add(n)) out.add(n);
        }
        int scans = 0;
        for (long k : StationData.get(w).names().keySet()) {
            BlockPos p = BlockPos.fromLong(k);
            if (covered(out, p) || !w.isBlockLoaded(p) || scans >= 8) continue;
            RailNetwork n = com.dogpound.railmap.scan.NetworkScanner.scan(w, p);
            scans++;
            if (!n.isEmpty()) out.add(n);
        }
        NETS.put(dim, out);
        NETS_AT.put(dim, now);
        return out;
    }

    private static boolean covered(List<RailNetwork> l, BlockPos p) {
        for (RailNetwork n : l)
            if (p.getX() >= n.minX - 8 && p.getX() <= n.maxX + 8 && p.getZ() >= n.minZ - 8 && p.getZ() <= n.maxZ + 8 && n.nearestStop(p.getX(), p.getY(), p.getZ(), 12, false) != null) return true;
        return false;
    }

    static Map<String, Object> tracksDoc(World w) {
        Map<String, Object> m = head(w);
        if (!TrainMapConfig.allows("tracks")) { m.put("chains", Collections.emptyList()); return m; }
        List<RailNetwork> l = networks(w);
        List<List<Double>> chains = new ArrayList<>();
        Set<String> dupes = new HashSet<>();
        double nx = Double.MAX_VALUE, nz = Double.MAX_VALUE, xx = -Double.MAX_VALUE, xz = -Double.MAX_VALUE;
        List<Map<String, Object>> switches = new ArrayList<>();
        for (RailNetwork n : l) {
            for (float[] c : Chains.build(n)) {
                if (c.length < 6) continue;
                String key = Math.round(c[0]) + "," + Math.round(c[2]) + ":" + Math.round(c[c.length - 3]) + "," + Math.round(c[c.length - 1]) + ":" + c.length;
                if (!dupes.add(key)) continue;
                List<Double> pts = new ArrayList<>(c.length / 3 * 2);
                for (int i = 0; i + 2 < c.length; i += 3) {
                    pts.add(r1(c[i])); pts.add(r1(c[i + 2]));
                    nx = Math.min(nx, c[i]); xx = Math.max(xx, c[i]); nz = Math.min(nz, c[i + 2]); xz = Math.max(xz, c[i + 2]);
                }
                chains.add(pts);
            }
            for (com.dogpound.railmap.graph.RailNode nd : n.nodes)
                if (nd.kind == com.dogpound.railmap.graph.RailNode.Kind.SWITCH) {
                    Map<String, Object> s = new LinkedHashMap<>();
                    s.put("x", nd.pos.getX() + 0.5); s.put("z", nd.pos.getZ() + 0.5);
                    s.put("state", nd.switchState == com.dogpound.railmap.graph.RailNode.SWITCH_TURN ? "turn" : "straight");
                    switches.add(s);
                }
        }
        m.put("chains", chains);
        m.put("switches", switches);
        if (!chains.isEmpty()) m.put("bounds", Arrays.asList(r1(nx), r1(nz), r1(xx), r1(xz)));
        m.put("networks", l.size());
        return m;
    }

    // ---- the live document ------------------------------------------------------------------------------------------------
    static Map<String, Object> liveDoc(World w) {
        Map<String, Object> m = head(w);
        StationData st = StationData.get(w);
        List<Transport.Vehicle> vs = TrainMapConfig.allows("trains") || TrainMapConfig.allows("stations") ? Transport.vehicles(w) : Collections.emptyList();
        Map<String, String> colour = new HashMap<>();
        for (com.dogpound.railmap.auto.RailwayData.Line ln : com.dogpound.railmap.auto.RailwayData.get(w).lines().values())
            colour.put(ln.name, String.format("#%06X", ln.color & 0xFFFFFF));

        if (TrainMapConfig.allows("trains")) {
            Map<Integer, TrainNode> byEntity = new HashMap<>();
            for (TrainNode t : TrainTracker.latest(w)) byEntity.put(t.id, t);
            List<Object> l = new ArrayList<>();
            for (Transport.Vehicle v : vs) {
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("id", v.adapter + ":" + Integer.toHexString(Objects.hashCode(v.id)));      // stable, but not a world UUID
                t.put("name", scrub(w, v.name));
                t.put("kind", v.kind);
                t.put("source", v.adapter);
                t.put("x", r1(v.x)); t.put("y", r1(v.y)); t.put("z", r1(v.z));
                t.put("speedKmh", Math.round(Math.abs(v.speedKmh)));
                t.put("line", v.line);
                t.put("colour", colour.getOrDefault(v.line, ""));
                t.put("nextStop", v.destination);
                int delay = v.uuid == null ? 0 : com.dogpound.railmap.auto.Disruptions.delaySeconds(v.uuid);
                t.put("delayMin", delay < 60 ? 0 : Math.round(delay / 60.0));
                t.put("status", scrub(w, v.status));
                t.put("cars", v.cars);
                t.put("driverless", v.driverless);
                t.put("fillPct", v.fillPct);
                TrainNode tn = byEntity.get(v.entityId);
                if (tn != null) { t.put("yaw", Math.round(tn.yaw)); t.put("passengers", tn.passengers); if (!tn.power.isEmpty()) t.put("power", tn.power); }
                l.add(t);
            }
            m.put("trains", l);
        }
        if (TrainMapConfig.allows("lines")) m.put("lines", Export.doc(w, "lines"));
        if (TrainMapConfig.allows("signals")) {
            List<Object> l = new ArrayList<>();
            Set<Long> seen = new HashSet<>();
            for (RailNetwork n : networks(w))
                for (SignalNode s : n.signals) {
                    if (!seen.add(s.pos.toLong())) continue;
                    Map<String, Object> o = new LinkedHashMap<>();
                    o.put("x", s.pos.getX() + 0.5); o.put("z", s.pos.getZ() + 0.5);
                    o.put("aspect", s.aspect.name().toLowerCase(Locale.ROOT));
                    o.put("source", s.source);
                    l.add(o);
                }
            m.put("signals", l);
        }
        if (TrainMapConfig.allows("stations")) {
            com.dogpound.railmap.economy.PassengerData pd = com.dogpound.railmap.economy.PassengerData.get(w);
            List<Object> l = new ArrayList<>();
            for (Map.Entry<Long, String> e : new TreeMap<>(st.names()).entrySet()) {
                BlockPos p = BlockPos.fromLong(e.getKey());
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("name", e.getValue());
                o.put("x", p.getX() + 0.5); o.put("z", p.getZ() + 0.5);
                if (TrainMapConfig.allows("crowds")) {
                    com.dogpound.railmap.economy.PassengerData.Stn s = pd.stn(e.getKey());
                    if (s != null) {
                        o.put("waiting", (int) Math.round(s.waitingTotal()));
                        o.put("rating", s.rating());
                        o.put("satisfaction", (int) Math.round(s.sat));
                        o.put("headwayS", (int) Math.round(s.headway));
                    }
                }
                List<Object> deps = new ArrayList<>();
                for (Transport.Vehicle v : vs) {
                    if (v.destination == null || !v.destination.equalsIgnoreCase(e.getValue())) continue;
                    Map<String, Object> d = new LinkedHashMap<>();
                    double dist = Math.sqrt((v.x - p.getX()) * (v.x - p.getX()) + (v.z - p.getZ()) * (v.z - p.getZ()));
                    double ms = Math.abs(v.speedKmh) / 3.6;
                    d.put("train", scrub(w, v.name));
                    d.put("line", v.line);
                    d.put("colour", colour.getOrDefault(v.line, ""));
                    d.put("distance", Math.round(dist));
                    d.put("etaS", ms < 0.5 ? -1 : Math.round(dist / ms));
                    int delay = v.uuid == null ? 0 : com.dogpound.railmap.auto.Disruptions.delaySeconds(v.uuid);
                    d.put("delayMin", delay < 60 ? 0 : Math.round(delay / 60.0));
                    deps.add(d);
                }
                deps.sort(Comparator.comparingLong(d -> ((Number) ((Map<?, ?>) d).get("distance")).longValue()));
                o.put("coming", deps.size() > 6 ? deps.subList(0, 6) : deps);
                l.add(o);
            }
            m.put("stations", l);
        }
        if (TrainMapConfig.allows("alarms")) {
            List<Object> l = new ArrayList<>();
            long now = w.getTotalWorldTime();
            for (AlarmManager.Alarm a : AlarmManager.get(w).all()) {
                int seq = a.seq(now);
                if (seq == 0) continue;
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("id", a.id); o.put("category", a.cat.name()); o.put("severity", a.sev.name());
                o.put("text", scrub(w, a.text)); o.put("state", seq == 1 ? "new" : seq == 2 ? "acknowledged" : "cleared"); o.put("count", a.count);
                if (a.pos != 0) { BlockPos p = BlockPos.fromLong(a.pos); o.put("x", p.getX() + 0.5); o.put("z", p.getZ() + 0.5); }
                l.add(o);
            }
            m.put("alarms", l);
        }
        if (TrainMapConfig.allows("cctv")) {
            List<Object> l = new ArrayList<>();
            for (com.dogpound.railmap.cctv.CctvData.Cam c : com.dogpound.railmap.cctv.CctvData.get(w).cams.values()) {
                if (c.dim != w.provider.getDimension()) continue;
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("no", c.no); o.put("name", scrub(w, c.name)); o.put("type", c.kind().label);
                o.put("status", c.status >= 0 && c.status < com.dogpound.railmap.cctv.CctvData.STATUS.length ? com.dogpound.railmap.cctv.CctvData.STATUS[c.status] : "?");
                o.put("x", r1(c.x)); o.put("z", r1(c.z));
                if (!c.train.isEmpty()) o.put("onTrain", true);
                l.add(o);
            }
            m.put("cctv", l);
        }
        return m;
    }

    private static Map<String, Object> head(World w) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mod", "irextras");
        m.put("feed", "trainmap");
        m.put("v", 1);
        m.put("written", System.currentTimeMillis());
        m.put("worldTime", w.getWorldTime() % 24000);
        m.put("dimension", w.provider.getDimension());
        m.put("liveSeconds", TrainMapConfig.liveSeconds);
        m.put("publish", Arrays.asList(TrainMapConfig.publish));
        return m;
    }

    // ---- privacy -----------------------------------------------------------------------------------------------------------
    /** player names out of free text (train names, alarm texts) */
    static String scrub(World w, String s) {
        if (s == null || s.isEmpty() || !TrainMapConfig.hidePlayerNames) return s == null ? "" : s;
        for (String n : names(w)) if (n.length() >= 3 && s.contains(n)) s = s.replaceAll("(?<![A-Za-z0-9_])" + java.util.regex.Pattern.quote(n) + "(?![A-Za-z0-9_])", "a player");
        return s;
    }

    private static Set<String> names(World w) {
        long now = System.currentTimeMillis();
        if (now - namesAt < 60_000) return names;
        Set<String> out = new HashSet<>();
        MinecraftServer srv = w.getMinecraftServer();
        if (srv != null) {
            out.addAll(Arrays.asList(srv.getOnlinePlayerNames()));
            try { out.addAll(Arrays.asList(srv.getPlayerProfileCache().getUsernames())); } catch (RuntimeException ignored) { }
        }
        names = out;
        namesAt = now;
        return out;
    }

    static double r1(double v) { return Math.round(v * 10) / 10.0; }

    // ---- /irextras trainmap ----------------------------------------------------------------------------------------------
    public static boolean command(World w, net.minecraft.command.ICommandSender s, String[] a) {
        if (a.length == 0 || !a[0].equalsIgnoreCase("trainmap")) return false;
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "status";
        World mw = w.getMinecraftServer() == null ? w : w.getMinecraftServer().getWorld(TrainMapConfig.dimension);
        switch (sub) {
            case "status" -> {
                say(s, "§eTrain map: §7server " + (TrainMapServer.running() ? "§aon http://" + TrainMapConfig.bind + ":" + TrainMapConfig.port + "/" : TrainMapConfig.port == 0 ? "§7off (port = 0 in irextras_trainmap.cfg)" : "§c" + TrainMapServer.error)
                        + "§7, outbox " + (TrainMapConfig.outbox ? "on" : "off") + ", publishing " + String.join(" ", TrainMapConfig.publish));
                say(s, "§7live " + live.length() + " B (" + age(liveAt) + "), tracks " + tracks.length() + " B (" + age(tracksAt) + "), " + TrainMapServer.served + " served, " + TrainMapServer.refused + " refused"
                        + (lastError.isEmpty() ? "" : " §cerror: " + lastError));
            }
            case "build" -> { synchronized (TrainMapFeed.class) { NETS_AT.clear(); } say(s, "§a" + build(mw, true)); }
            case "selftest" -> selftest(mw, s);
            default -> say(s, "§e/irextras trainmap §7status | build | selftest");
        }
        return true;
    }

    private static String age(long t) { return t == 0 ? "never" : (System.currentTimeMillis() - t) / 1000 + " s ago"; }

    /** the feed builds, the allowlist really removes sections, names are scrubbed, the page files are in the jar */
    static void selftest(World w, net.minecraft.command.ICommandSender s) {
        int ok = 0, bad = 0;
        String[] keep = TrainMapConfig.publish;
        boolean hide = TrainMapConfig.hidePlayerNames;
        try {
            Map<String, Object> d = liveDoc(w);
            if (d.containsKey("trains") && d.containsKey("alarms")) ok++; else { bad++; say(s, "§cFAIL full allowlist: sections missing"); }
            TrainMapConfig.publish = new String[]{"tracks"};
            d = liveDoc(w);
            if (!d.containsKey("trains") && !d.containsKey("alarms") && !d.containsKey("cctv") && !d.containsKey("stations") && !d.containsKey("signals")) ok++;
            else { bad++; say(s, "§cFAIL allowlist: unlisted sections still published"); }
            TrainMapConfig.publish = keep;
            TrainMapConfig.hidePlayerNames = true;
            names = new HashSet<>(Collections.singletonList("Testplayer_9")); namesAt = System.currentTimeMillis();
            String sc = scrub(w, "E-stop by Testplayer_9 at Testplayer_90");
            if (sc.equals("E-stop by a player at Testplayer_90")) ok++; else { bad++; say(s, "§cFAIL scrub: " + sc); }
            namesAt = 0;
            String json = GSON.toJson(tracksDoc(w));
            if (new com.google.gson.JsonParser().parse(json).isJsonObject()) ok++; else bad++;
            if (json.contains("\"ip\"") || live.contains("uuid")) { bad++; say(s, "§cFAIL: the feed carries an ip / uuid field"); } else ok++;
            for (String f : TrainMapServer.FILES.keySet())
                if (TrainMapServer.resource(f) != null) ok++; else { bad++; say(s, "§cFAIL: web/trainmap/" + f + " is not in the jar"); }
        } finally {
            TrainMapConfig.publish = keep;
            TrainMapConfig.hidePlayerNames = hide;
        }
        say(s, (bad == 0 ? "§aTrain map selftest PASS " : "§cTrain map selftest FAIL ") + ok + " ok, " + bad + " failed");
    }

    static void say(net.minecraft.command.ICommandSender s, String t) { s.sendMessage(new net.minecraft.util.text.TextComponentString(t)); }
}
