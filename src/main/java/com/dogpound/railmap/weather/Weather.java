package com.dogpound.railmap.weather;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapConfig;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.TrackPath;
import com.dogpound.railmap.signal.SignalRegistry;
import com.dogpound.railmap.signal.TileCrossing;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Railway weather (her ChatGPT list #63-66), worked out per 64 x 64 block cell where trains run, every 5 s - never
 * per block. Air temperature is the grid's own ambient (biome, height, time of day, rain) plus any heatwave / cold
 * snap; precipitation and wind come from Weather2 when it is installed (else vanilla rain / thunder).
 * <ul>
 *   <li><b>#63 snow and ice</b>: snow lies on open track (not in tunnels) while it snows at or below 0.5 °C, melts
 *   above; trains compact it, a snowplough (a loco named plow / plough / snow / blower) clears it, snow gangs work
 *   through work orders by track priority (traffic). Freezing rain builds ice on the contact wire (arcing, see ohle).
 *   Frozen points won't move unless a Points Heater cabinet near them is heating. 150 mm = 60 km/h, 300 = 30,
 *   500 = closed.</li>
 *   <li><b>#64 flooding</b>: each cell has a flood risk (low track, water nearby, tunnels take water in; or set it);
 *   rain fills the formation, natural drainage, Culverts and Pump Stations empty it. Water at the rail head =
 *   30 km/h, 75 mm over = 10, 150 mm = closed; a flooded third rail is shorted; isolator motors and heaters drown.</li>
 *   <li><b>#65 heat</b>: rail temperature = air + sun on the steel (none in tunnels); the stress-free temperature is
 *   the regional climate's (at least railStressFreeC). Watch at SFT+16, 90 km/h at +20, 50 km/h at +25, buckle risk
 *   (railBuckling) at +28 - all lower on poor track; a hot-weather patrol at a Rail Temperature Monitor relaxes it one
 *   step for an hour, ballast work restores the track's condition.</li>
 *   <li><b>#66 visibility</b>: rain, snow, blizzard, radiation fog (still, humid, dawn) and desert dust shorten
 *   the view; signal sighting uses it, trains get told, &lt; 200 m = 60 km/h, &lt; 80 m = 40 km/h for lineside-signalled
 *   trains (CBTC trains run on their in-cab authority), and open level crossings in fog get a 40 km/h approach.</li>
 * </ul>
 * Weather Stations put their cell's conditions on the Control Center; restrictions show on the map.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class Weather {
    private Weather() {}

    public static final int CELL = 64;

    // ------------------------------------------------------------------------------------------------ access
    public static WeatherData.Cell cell(World w, BlockPos p) {
        WeatherData d = WeatherData.get(w);
        WeatherData.Cell c = d.cell(p.getX() >> 6, p.getZ() >> 6);
        long now = w.getTotalWorldTime();
        c.lastQuery = now;
        if (c.lastTick == 0) { c.trackY = p.getY(); update(w, d, c, now, 0); }
        return c;
    }

    public static boolean on() { return RailMapConfig.railWeather; }

    /** air temperature, °C */
    public static double airC(World w, BlockPos p) {
        return on() ? cell(w, p).air : com.dogpound.railmap.grid.Thermal.biomeAmbient(w, p);
    }

    public static double railC(World w, BlockPos p) { return on() ? cell(w, p).rail : airC(w, p); }
    public static double iceMm(World w, BlockPos p) { return on() ? cell(w, p).ice : 0; }
    public static double snowMm(World w, BlockPos p) { return on() ? cell(w, p).snow : 0; }
    public static double floodMm(World w, BlockPos p) { return on() ? cell(w, p).flood() : 0; }

    /** weather trains: the rail head here is swept or treated (no leaf-mulch penalty) */
    public static boolean railheadClean(World w, BlockPos p) {
        if (!on()) return false;
        WeatherData.Cell c = cell(w, p);
        long now = w.getTotalWorldTime();
        return now < c.clearUntil || now < c.treatUntil;
    }

    /** a pantograph scraped the ice off the wire it passed */
    public static void scrapeIce(World w, BlockPos p, double mm) {
        WeatherData.Cell c = cell(w, p);
        c.ice = Math.max(0, c.ice - mm);
    }

    // ------------------------------------------------------------------------------------------------ Weather2 (reflection)
    private static boolean w2tried;
    private static Method w2Precip, w2Wind;
    private static Constructor<?> w2Vec;

    private static void w2() {
        if (w2tried) return;
        w2tried = true;
        try {
            Class<?> api = Class.forName("net.mrbt0907.weather2.api.WeatherAPI");
            w2Precip = api.getMethod("isPrecipitatingAt", World.class, BlockPos.class);
            Class<?> vec = Class.forName("net.mrbt0907.weather2.util.Maths$Vec3");
            w2Vec = vec.getConstructor(double.class, double.class, double.class);
            w2Wind = Class.forName("net.mrbt0907.weather2.api.WindReader").getMethod("getWindSpeed", World.class, vec);
            RailMap.LOG.info("[irextras] railway weather: reading Weather2 rain and wind");
        } catch (Throwable t) {
            w2Precip = null; w2Wind = null;
        }
    }

    public static boolean weather2() { w2(); return w2Precip != null; }

    private static Boolean w2Precip(World w, BlockPos p) {
        w2();
        if (w2Precip == null) return null;
        try { return (Boolean) w2Precip.invoke(null, w, p); } catch (Throwable t) { return null; }
    }

    private static double w2Wind(World w, BlockPos p) {
        w2();
        if (w2Wind == null) return -1;
        try { return ((Number) w2Wind.invoke(null, w, w2Vec.newInstance(p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5))).doubleValue(); } catch (Throwable t) { return -1; }
    }

    // ------------------------------------------------------------------------------------------------ the 5 s update
    static void update(World w, WeatherData d, WeatherData.Cell c, long now, double dtSteps) {
        BlockPos at = new BlockPos(c.x0() + 32, (int) c.trackY, c.z0() + 32);
        if (!w.isBlockLoaded(at)) return;
        c.lastTick = now;
        Biome b = w.getBiome(at);
        double base = com.dogpound.railmap.grid.Thermal.biomeAmbient(w, at);
        boolean heat = now < d.heatwaveUntil, cold = now < d.coldSnapUntil;
        c.air = base + (heat ? 9 : 0) - (cold ? 12 : 0);
        double mean = 10 + 20 * (b.getTemperature(at) - 0.5);
        c.sft = Math.max(RailMapConfig.railStressFreeC, Math.round(mean + 10));
        // precipitation: Weather2 locally, else the world's rain (some days harder than others), thunder = storm
        boolean canFall = b.canRain() || b.getEnableSnow();
        Boolean local = w2Precip(w, at.up(2));
        boolean raining = local != null ? local : w.isRaining();
        long day = w.getWorldTime() / 24000;
        int hash = (int) ((day * 31 + c.cx * 7L + c.cz * 13L) & 0x7fffffff);
        c.precip = !canFall || !raining ? 0 : w.isThundering() ? 3 : hash % 3 == 0 ? 2 : 1;
        c.snowing = c.precip > 0 && c.air <= 0.5;
        double ww = w2Wind(w, at);
        c.wind = ww >= 0 ? ww : (w.isThundering() ? 18 : raining ? 9 : 3) + 2 * Math.sin(now / 900.0 + c.cx);
        // rail temperature: sun on the steel
        c.tunnel = !w.canSeeSky(at.up(2)) && !w.canSeeSky(at.up(3));
        long t = w.getWorldTime() % 24000;
        double sun = t < 12000 ? Math.sin(Math.PI * t / 12000.0) : 0;
        c.rail = c.air + (c.tunnel ? 0 : sun * (c.precip == 0 ? 1 : 0.25) * (4 + 0.45 * Math.max(0, c.air)));
        if (dtSteps <= 0) { visibility(w, c, b, now); return; }
        // ---- snow on the track, ice on the wire
        if (c.snowing && !c.tunnel) c.snow += new double[]{0, 1.0, 2.5, 4.0}[c.precip] * dtSteps;
        if (c.air > 0.5) c.snow = Math.max(0, c.snow - (0.4 * c.air + (c.precip > 0 ? 1.5 : 0)) * dtSteps);
        if (c.tunnel) c.snow = 0;
        if (c.precip > 0 && c.air > -4 && c.air <= 0.5 && now >= c.antiIceUntil) c.ice = Math.min(10, c.ice + 0.05 * c.precip * dtSteps);   // de-icer fluid on the wire stops it forming
        else if (c.air > 1) c.ice = Math.max(0, c.ice - 0.02 * c.air * dtSteps);
        else c.ice = Math.max(0, c.ice - 0.002 * dtSteps);
        // ---- water in the formation
        double risk = floodRisk(w, c, at);
        double in = risk * new double[]{0, 0.3, 0.9, 2.2}[c.snowing ? 0 : c.precip];
        if (c.air > 2 && c.snow > 0) in += risk * 0.15 * Math.min(c.snow, 0.4 * c.air);  // snow melt runs off
        double out = 1.2 + Drainage.capacity(w, c, now);
        c.water = Math.max(0, c.water + (in - out) * dtSteps);
        // ---- heat: track condition creeps down in big heat cycles, buckles
        if (c.rail > c.sft + 20) c.condition = Math.max(0.3, c.condition - 0.0005 * dtSteps);
        if (RailMapConfig.railBuckling && c.buckled == 0 && c.traffic > 0.5 && c.rail > heatLimit(c, now, 3) && w.rand.nextDouble() < 0.004 * dtSteps * (2 - c.condition)) {
            c.buckled = now;
            d.log(w, "TRACK BUCKLED in " + name(c) + " (rail " + Math.round(c.rail) + " °C) - line closed");
            Recorder.event(w, "TRACK BUCKLE " + name(c) + ": rail " + Math.round(c.rail) + " °C, condition " + Math.round(c.condition * 100) + "%");
        }
        c.traffic = Math.max(0, c.traffic - c.traffic * 0.0014 * dtSteps);       // trains per hour, decaying
        visibility(w, c, b, now);
        d.markDirty();
    }

    private static void visibility(World w, WeatherData.Cell c, Biome b, long now) {
        double v = 10000;
        String why = "";
        if (c.precip > 0) {
            double r = c.snowing ? new double[]{0, 1500, 400, 150}[c.precip] : new double[]{0, 3000, 1200, 500}[c.precip];
            if (r < v) { v = r; why = c.snowing ? (c.precip == 3 ? "blizzard" : "snow") : (c.precip == 3 ? "torrential rain" : "rain"); }
        }
        // radiation fog: still, humid, clear, around dawn (some days), thicker over water and in swamps
        long t = w.getWorldTime() % 24000;
        boolean dawn = t >= 21500 || t < 2500;
        String bn = b.getBiomeName().toLowerCase(Locale.ROOT);
        boolean swamp = bn.contains("swamp") || bn.contains("marsh") || bn.contains("bog") || bn.contains("bayou");
        long day = w.getWorldTime() / 24000;
        int hash = (int) ((day * 2654435761L + (c.cx >> 2) * 40503L + (c.cz >> 2) * 9973L) & 0x7fffffff);
        double humid = b.getRainfall() + (c.waterNear > 0 ? c.waterNear * 0.05 : 0) + (swamp ? 0.4 : 0);
        c.fogged = c.precip == 0 && c.wind < 6 && (dawn || swamp && (t > 13000)) && humid > 0.5 && hash % 100 < 25 + humid * 20 && c.air > -10;
        if (c.fogged) {
            double f = 60 + (hash % 240);
            if (f < v) { v = f; why = "fog"; }
        }
        if (c.wind > 15 && b.getRainfall() < 0.1 && c.precip == 0 && 300 < v) { v = 300; why = "blowing dust"; }
        c.vis = v;
        c.visWhy = why;
    }

    static double floodRisk(World w, WeatherData.Cell c, BlockPos at) {
        if (c.floodRisk >= 0) return c.floodRisk;
        if (c.waterNear < 0) {
            int n = 0;
            for (int i = 0; i < 5; i++)
                for (int k = 0; k < 5; k++) {
                    BlockPos q = w.getHeight(new BlockPos(c.x0() + 6 + i * 13, 0, c.z0() + 6 + k * 13)).down();
                    if (w.isBlockLoaded(q) && w.getBlockState(q).getMaterial() == Material.WATER) n++;
                }
            c.waterNear = n;
        }
        return Math.min(2, Math.max(0, (66 - c.trackY) / 3.0) + c.waterNear * 0.06 + (c.tunnel ? 1 : 0));
    }

    // ------------------------------------------------------------------------------------------------ restrictions
    /** heat thresholds: 0 watch, 1 = 90 km/h, 2 = 50 km/h, 3 = buckle risk (poor track lowers them; a patrol raises them a step) */
    static double heatLimit(WeatherData.Cell c, long now, int level) {
        double k = (1 - c.condition) * 8 - (now < c.patrolUntil && level > 0 ? 4 : 0);
        return c.sft + new double[]{16, 20, 25, 28}[level] - k;
    }

    /** the tightest weather restriction in a cell: {kmh (0 = closed, -1 = none), why, applies to CBTC too (0/1)} */
    public static Object[] restriction(World w, WeatherData.Cell c, long now) {
        double kmh = -1;
        String why = "";
        boolean cbtcToo = true;
        if (!on() || !RailMapConfig.weatherRestrictions) return new Object[]{-1.0, "", true};
        double[][] tiers = {
                {c.snow >= 500 ? 0 : c.snow >= 300 ? 30 : c.snow >= 150 ? 60 : -1},
                {c.flood() > 150 ? 0 : c.flood() > 75 ? 10 : c.water > 150 ? 30 : -1},
                {c.buckled != 0 ? 0 : c.rail >= heatLimit(c, now, 2) ? 50 : c.rail >= heatLimit(c, now, 1) ? 90 : -1},
                {c.wind > 26 ? 60 : -1}};
        String[] whys = {"snow " + Math.round(c.snow) + " mm", c.flood() > 0 ? "flood " + Math.round(c.flood()) + " mm over the rail" : "water at the rail head",
                c.buckled != 0 ? "TRACK BUCKLED" : "rail " + Math.round(c.rail) + " °C (heat)", "high wind " + Math.round(c.wind) + " m/s"};
        for (int i = 0; i < tiers.length; i++) {
            double v = tiers[i][0];
            if (v >= 0 && (kmh < 0 || v < kmh)) { kmh = v; why = whys[i]; }
        }
        double vk = c.vis < 80 ? 40 : c.vis < 200 ? 60 : -1;
        if (vk >= 0 && (kmh < 0 || vk < kmh)) { kmh = vk; why = c.visWhy + " " + Math.round(c.vis) + " m"; cbtcToo = false; }
        return new Object[]{kmh, why, cbtcToo};
    }

    /**
     * Wayside: the weather restrictions along a route ahead, as {distance, kmh, stop, what, where, signal-flag}. A
     * snowplough is never stopped by snow. Plus a 40 km/h approach to every level crossing in fog.
     */
    public static List<Object[]> onPath(World w, TrackPath path) {
        List<Object[]> out = new ArrayList<>();
        if (!on() || !RailMapConfig.weatherRestrictions || path.steps.isEmpty()) return out;
        long now = w.getTotalWorldTime(), last = Long.MIN_VALUE;
        double from = 0;
        for (TrackPath.Step st : path.steps) {
            BlockPos p = new BlockPos(st.pos.x, st.pos.y, st.pos.z);
            WeatherData.Cell c = cell(w, p);
            if (c.key != last) {
                last = c.key;
                Object[] r = restriction(w, c, now);
                double kmh = (double) r[0];
                if (kmh >= 0) out.add(new Object[]{from, kmh <= 0 ? 0.0 : kmh, kmh <= 0, "WEATHER: " + r[1], p, !(boolean) r[2]});
            }
            from = st.distance;
        }
        for (TileCrossing x : SignalRegistry.crossings(w.provider.getDimension())) {
            if (x.isInvalid()) continue;
            BlockPos p = x.getPos();
            WeatherData.Cell c = cell(w, p);
            if (c.vis >= 250) continue;
            double dd = path.distanceTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 8);
            if (dd < 0) continue;
            out.add(new Object[]{Math.max(0, dd - 40), 40.0, false, "FOG: level crossing ahead - sound the horn", p, false});
        }
        return out;
    }

    /** signal sighting: what's left of the view, 0..1 of 600 m (NaN when the weather model is off) */
    public static double visibilityFactor(World w, BlockPos at) {
        if (!on()) return Double.NaN;
        return Math.max(0.08, Math.min(1, cell(w, at).vis / 600.0));
    }

    /** adhesion: {factor, reason index into AdhesionSim.REASONS (6 snow, 7 ice, 8 water)} or null */
    public static float[] adhesion(World w, BlockPos at) {
        if (!on()) return null;
        WeatherData.Cell c = cell(w, at);
        if (c.water > 140) return new float[]{0.75f, 8};
        if (c.snow > 30) return new float[]{(float) Math.max(0.45, 0.8 - c.snow / 1500), 6};
        if (c.air <= 0 && c.precip > 0 && w.getTotalWorldTime() >= c.treatUntil) return new float[]{0.55f, 7};   // a railhead treatment train's coat keeps the rail head working
        return null;
    }

    /**
     * Points that won't move: frozen (snow / ice packed in, at or below 0 °C, no Points Heater heating them) or, in
     * great heat, the expanded switch rails fail to detect (3 in 10 throws).
     */
    public static String pointsFail(World w, BlockPos sw) {
        if (!on() || !RailMapConfig.frozenPoints) return null;
        WeatherData.Cell c = cell(w, sw);
        boolean frost = c.air <= 0 && (c.snow >= 30 || c.ice > 1 || c.precip > 0 && c.snowing);
        if (frost && !c.tunnel && !TilePointsHeater.heating(w, sw)) return "Points FROZEN - snow and ice packed in the switch (" + Math.round(c.snow) + " mm, " + Math.round(c.air) + " °C): a Points Heater or a snow gang";
        if (c.rail >= heatLimit(c, w.getTotalWorldTime(), 2) && w.rand.nextInt(10) < 3) return "Points FAILED TO DETECT - switch rails expanded in the heat (rail " + Math.round(c.rail) + " °C)";
        return null;
    }

    // ------------------------------------------------------------------------------------------------ the tick
    private static final Map<UUID, Long> LAST_CELL = new HashMap<>();

    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side.isClient() || !on()) return;
        World w = e.world;
        long now = w.getTotalWorldTime();
        if (now % 100 != 37) return;
        try {
            WeatherData d = WeatherData.get(w);
            events(w, d, now);
            trains(w, d, now);
            for (WeatherData.Cell c : new ArrayList<>(d.cells.values())) {
                if (now - c.lastQuery > 12000 && !c.lasting()) continue;
                if (!com.dogpound.railmap.admin.Sched.due(w, "weather", c.key, c.cx * 64 + 32, c.cz * 64 + 32, 100)) continue;   // #120 load-aware: quiet cells less often (dt below catches up)
                update(w, d, c, now, c.lastTick == 0 ? 1 : Math.min(24, (now - c.lastTick) / 100.0));
                alarms(w, d, c, now);
            }
            Crews.tick(w, d, now);
            d.cells.values().removeIf(c -> !c.lasting() && now - c.lastQuery > 24000);
        } catch (RuntimeException | LinkageError ex) {
            RailMap.LOG.warn("[irextras] railway weather pass failed: {}", ex.toString());
        }
    }

    /** the odd heatwave (2 days) or cold snap (1 day), rolled once a game day at dawn */
    private static void events(World w, WeatherData d, long now) {
        long t = w.getWorldTime() % 24000;
        if (t < 23000 || t >= 23100 || now < d.heatwaveUntil || now < d.coldSnapUntil) return;
        int r = w.rand.nextInt(100);
        if (r < 4) { d.heatwaveUntil = now + 48000; d.log(w, "HEATWAVE forecast: +9 °C for 2 days - expect heat speed restrictions"); Recorder.event(w, "WEATHER: heatwave begins"); }
        else if (r < 7) { d.coldSnapUntil = now + 24000; d.log(w, "COLD SNAP forecast: -12 °C for a day - points heating, ice on the wire"); Recorder.event(w, "WEATHER: cold snap begins"); }
    }

    /** trains entering a cell: traffic counts, they compact snow, a plough clears it, the driver hears the weather */
    private static void trains(World w, WeatherData d, long now) {
        if (!net.minecraftforge.fml.common.Loader.isModLoaded("immersiverailroading")) return;
        for (Locomotive l : cam72cam.mod.world.World.get(w).getEntities(Locomotive.class)) {
            if (l.isDead()) continue;
            BlockPos p = new BlockPos(l.getPosition().x, l.getPosition().y, l.getPosition().z);
            WeatherData.Cell c = cell(w, p);
            Long was = LAST_CELL.put(l.getUUID(), c.key);
            if (was != null && was == c.key) continue;
            c.traffic += 1;
            c.trackY = c.trackY * 0.8 + p.getY() * 0.2;
            c.snow *= 0.97;
            String n = ((l.getDefinition() == null ? "" : l.getDefinition().name()) + " " + l.getDefinitionID()).toLowerCase(Locale.ROOT);
            double kmh = l.getCurrentSpeed() == null ? 0 : Math.abs(l.getCurrentSpeed().metric());
            if ((n.contains("plow") || n.contains("plough") || n.contains("snow") || n.contains("blower")) && kmh > 8 && c.snow > 20) {
                d.log(w, "snowplough cleared " + name(c) + " (" + Math.round(c.snow) + " mm)");
                c.snow = 20;
            }
            if (was == null) continue;
            StringBuilder say = new StringBuilder();
            if (c.vis < 300) say.append("RESTRICTED VISIBILITY: ").append(c.visWhy).append(" ").append(Math.round(c.vis)).append(" m - caution");
            Object[] r = restriction(w, c, now);
            if ((double) r[0] >= 0 && !(c.vis < 300 && !(boolean) r[2])) say.append(say.length() > 0 ? " · " : "").append((double) r[0] <= 0 ? "LINE CLOSED: " : "WEATHER " + Math.round((double) r[0]) + " km/h: ").append(r[1]);
            if (say.length() > 0) tell(l, "§e⚠ " + say);
        }
        if (LAST_CELL.size() > 4000) LAST_CELL.clear();
    }

    private static void alarms(World w, WeatherData d, WeatherData.Cell c, long now) {
        AlarmManager am = AlarmManager.get(w);
        BlockPos at = new BlockPos(c.x0() + 32, (int) c.trackY, c.z0() + 32);
        alarm(am, w, "wxsnow:" + c.key, c.snow >= 300, "Snow " + Math.round(c.snow) + " mm on the track in " + name(c) + " - work order raised", at, AlarmManager.Sev.MINOR);
        alarm(am, w, "wxflood:" + c.key, c.flood() > 75, "FLOOD " + Math.round(c.flood()) + " mm over the rail head in " + name(c) + (c.flood() > 150 ? " - LINE CLOSED" : ""), at, AlarmManager.Sev.MAJOR);
        alarm(am, w, "wxheat:" + c.key, c.rail >= heatLimit(c, now, 1), "Rail temperature " + Math.round(c.rail) + " °C in " + name(c) + " (SFT " + Math.round(c.sft) + " °C) - heat speed restriction", at, AlarmManager.Sev.MINOR);
        alarm(am, w, "wxbuckle:" + c.key, c.buckled != 0, "TRACK BUCKLED in " + name(c) + " - line closed: repair at a Rail Temperature Monitor", at, AlarmManager.Sev.CRITICAL);
    }

    private static void alarm(AlarmManager am, World w, String key, boolean on, String text, BlockPos at, AlarmManager.Sev sev) {
        alarmOnce(w, key, on, AlarmManager.Cat.GENERAL, sev, text, at);
    }

    private static final Set<String> ACTIVE = new HashSet<>();

    /** a standing alarm raised when its condition starts and cleared when it ends (not re-raised every pass) */
    public static void alarmOnce(World w, String key, boolean on, AlarmManager.Cat cat, AlarmManager.Sev sev, String text, BlockPos at) {
        String k = w.provider.getDimension() + "|" + key;
        if (on && ACTIVE.add(k)) AlarmManager.get(w).raise(w, key, cat, sev, text, at, true);
        else if (!on && ACTIVE.remove(k)) AlarmManager.get(w).clear(key);
    }

    static void tell(EntityRollingStock l, String msg) {
        try {
            List<EntityRollingStock> cars = new ArrayList<>();
            if (l instanceof cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock cs) cars.addAll(cs.getTrain()); else cars.add(l);
            for (EntityRollingStock car : cars)
                for (cam72cam.mod.entity.Entity e : car.getPassengers())
                    if (e.internal instanceof EntityPlayerMP mp) mp.sendStatusMessage(new TextComponentString(msg), true);
        } catch (RuntimeException | LinkageError ignored) { }
    }

    public static String name(WeatherData.Cell c) { return "cell " + (c.x0() + 32) + "," + (c.z0() + 32); }
}
