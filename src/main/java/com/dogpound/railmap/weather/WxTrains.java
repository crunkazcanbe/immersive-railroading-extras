package com.dogpound.railmap.weather;

import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.*;

/**
 * Snowploughs and weather trains (her IR Extras list #4), built on the railway weather cells:
 * <ul>
 *   <li><b>snowplough</b>: IR stock tagged PLOUGH / SNOWPLOW / BLOWER, a loco named like one, or a loco with the
 *   Snowplough Attachment fitted. Moving over 5 km/h it clears the cell's snow (down to a few mm), shovels snow layers
 *   off the track, sweeps the leaf mulch off the rail head for a while and throws the snow aside (particles) - the
 *   snow / ice / leaf adhesion penalties go with it;</li>
 *   <li><b>railhead treatment train</b> (tagged SANDITE / RHTT / RAILHEAD): water-jets the leaf film off and lays
 *   sandite: no leaf or rail-ice penalty for a day;</li>
 *   <li><b>de-icing train</b> (tagged DEICER / DE-ICE / ICE): sprays the contact wire - the ice goes, and anti-icing
 *   fluid keeps new ice off for half a day (ice on the wire makes pantographs arc);</li>
 *   <li><b>the plan</b>: a forecast per Weather Train Depot desk (temperature, the world's rain clock, cold snaps, where
 *   leaves were seen on the rail), runs planned by hand or AUTO when the risk passes the desk's threshold, the nearest
 *   suitable train called (its crew told), cells ticked off as it treats them, done when all are.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class WxTrains {
    private WxTrains() {}

    public static final int PLOUGH = 0, SANDITE = 1, DEICE = 2;
    public static final String[] KINDS = {"SNOWPLOUGH", "RAILHEAD TREATMENT", "DE-ICER"};
    public static final String[] SHORT = {"PLOUGH", "SANDITE", "DEICE"};
    private static final boolean IR = Loader.isModLoaded("immersiverailroading");
    /** per train: the last cell it treated (log once per cell) */
    private static final Map<UUID, Long> LAST = new HashMap<>();

    public static boolean on() { return WxTrainConfig.weatherTrains && Weather.on(); }

    // ---- which stock is what ---------------------------------------------------------------------------------------------
    /** the weather-train kind of a vehicle, -1 = none */
    public static int kindOf(World w, EntityRollingStock s) {
        String tag = s.tag == null ? "" : s.tag.toUpperCase(Locale.ROOT);
        return kindOf(tag, name(s), WxTrainData.get(w).fitted.contains(s.getUUID().toString()));
    }

    /** pure: tag, definition name, plough fitted */
    public static int kindOf(String tag, String name, boolean fitted) {
        if (tag.contains("DEICE") || tag.contains("DE-ICE") || tag.equals("ICE")) return DEICE;
        if (tag.contains("SANDITE") || tag.contains("RHTT") || tag.contains("RAILHEAD")) return SANDITE;
        if (tag.contains("PLOUGH") || tag.contains("PLOW") || tag.contains("BLOWER") || fitted) return PLOUGH;
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("plow") || n.contains("plough") || n.contains("snowblower")) return PLOUGH;
        return -1;
    }

    static String name(EntityRollingStock s) {
        try { return (s.getDefinition() == null ? "" : s.getDefinition().name()) + " " + s.getDefinitionID(); } catch (RuntimeException e) { return ""; }
    }

    static String how(World w, EntityRollingStock s) {
        String tag = s.tag == null ? "" : s.tag.toUpperCase(Locale.ROOT);
        return !tag.isEmpty() && kindOf(tag, "", false) >= 0 ? "tag " + tag : WxTrainData.get(w).fitted.contains(s.getUUID().toString()) ? "plough fitted" : "by name";
    }

    // ---- the tick --------------------------------------------------------------------------------------------------------
    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side.isClient() || !IR || !on()) return;
        World w = e.world;
        long now = w.getTotalWorldTime();
        if (now % 20 != 11) return;
        try {
            for (EntityRollingStock s : cam72cam.mod.world.World.get(w).getEntities(EntityRollingStock.class)) {
                if (s.isDead()) continue;
                int k = kindOf(w, s);
                if (k < 0) continue;
                double kmh = s instanceof EntityMoveableRollingStock m && m.getCurrentSpeed() != null ? Math.abs(m.getCurrentSpeed().metric()) : 0;
                if (kmh > 5) act(w, s, k, kmh, now);
            }
            if (now % 200 == 11) progress(w, now);
            if (LAST.size() > 2000) LAST.clear();
        } catch (RuntimeException | LinkageError ex) {
            RailMap.LOG.warn("[IR Extras] weather trains tick failed: {}", ex.toString());
        }
    }

    private static void act(World w, EntityRollingStock s, int kind, double kmh, long now) {
        Vec3d p = s.getPosition();
        BlockPos at = new BlockPos(p.x, p.y, p.z);
        WeatherData.Cell c = Weather.cell(w, at);
        WeatherData d = WeatherData.get(w);
        double yaw = Math.toRadians(s.getRotationYaw());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);                        // IR facing
        String what;
        switch (kind) {
            case PLOUGH: {
                double was = c.snow;
                c.snow = Math.min(c.snow, 4);
                c.clearUntil = Math.max(c.clearUntil, now + 6000);
                if (WxTrainConfig.ploughClearsBlocks) shovel(w, at, fx, fz);
                if (was > 8) throw_(w, s, p, fx, fz, Math.min(40, (int) (was / 6) + 6), EnumParticleTypes.SNOW_SHOVEL);
                what = was > 8 ? "cleared " + Math.round(was) + " mm of snow" : "swept the rail head";
                break;
            }
            case SANDITE: {
                c.clearUntil = Math.max(c.clearUntil, now + WxTrainConfig.sanditeTicks);
                c.treatUntil = Math.max(c.treatUntil, now + WxTrainConfig.sanditeTicks);
                throw_(w, s, p, fx, fz, 10, EnumParticleTypes.WATER_SPLASH);
                what = "jetted the leaf film off and laid sandite";
                break;
            }
            default: {
                double was = c.ice;
                c.ice = 0;
                c.antiIceUntil = Math.max(c.antiIceUntil, now + WxTrainConfig.antiIceTicks);
                if (w instanceof WorldServer ws) ws.spawnParticle(EnumParticleTypes.CLOUD, p.x, p.y + 5.2, p.z, 8, 0.6, 0.2, 0.6, 0.01);
                what = was > 0.05 ? String.format(Locale.ROOT, "de-iced the wire (%.1f mm)", was) : "sprayed anti-icing fluid on the wire";
            }
        }
        d.markDirty();
        Long last = LAST.put(s.getUUID(), c.key);
        WxTrainData td = WxTrainData.get(w);
        boolean counted = false;
        for (WxTrainData.Run r : td.runs)
            if (r.open() && r.kind == kind && (r.loco.isEmpty() || r.loco.equals(s.getUUID().toString())) && r.targets.contains(c.key) && r.done.add(c.key)) { counted = true; td.markDirty(); }
        if (last == null || last != c.key) {
            if (kind != PLOUGH || c.snow > 0 || counted) d.log(w, KINDS[kind].toLowerCase(Locale.ROOT) + " " + com.dogpound.railmap.track.Rails.name(s) + " " + what + " in " + Weather.name(c));
            if (counted) td.log(w, com.dogpound.railmap.track.Rails.name(s) + " " + what + " in " + Weather.name(c));
        }
    }

    /** snow thrown out to both sides of the front */
    private static void throw_(World w, EntityRollingStock s, Vec3d p, double fx, double fz, int n, EnumParticleTypes type) {
        if (!(w instanceof WorldServer ws)) return;
        double half = 2.5;
        try { half = s.getDefinition().getLength(s.gauge) / 2.0; } catch (RuntimeException ignored) { }
        double x = p.x + fx * half, z = p.z + fz * half;
        for (int side = -1; side <= 1; side += 2) {
            double sx = -fz * side, sz = fx * side;
            ws.spawnParticle(type, x + sx * 1.2, p.y + 0.6, z + sz * 1.2, n, 0.3, 0.3, 0.3, 0.25);
            ws.spawnParticle(EnumParticleTypes.FALLING_DUST, x + sx * 2.2, p.y + 1.2, z + sz * 2.2, n / 2, 0.6, 0.4, 0.6, 0.0, net.minecraft.block.Block.getStateId(Blocks.SNOW.getDefaultState()));
        }
        w.playSound(null, x, p.y, z, net.minecraft.init.SoundEvents.BLOCK_SNOW_BREAK, SoundCategory.BLOCKS, 0.8f, 0.7f);
    }

    /** snow layers off the track and the cess under / ahead of the plough */
    private static void shovel(World w, BlockPos at, double fx, double fz) {
        for (int a = -3; a <= 3; a++)
            for (int side = -2; side <= 2; side++)
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos q = new BlockPos(at.getX() + fx * a - fz * side, at.getY() + dy, at.getZ() + fz * a + fx * side);
                    if (w.isBlockLoaded(q) && w.getBlockState(q).getBlock() == Blocks.SNOW_LAYER) w.setBlockToAir(q);
                }
    }

    // ---- leaves seen, rail heads clean -------------------------------------------------------------------------------------
    public static void leafSeen(World w, BlockPos p) {
        if (!on()) return;
        WeatherData.Cell c = Weather.cell(w, p);
        long now = w.getTotalWorldTime();
        if (now - c.leafSeen > 200) { c.leafSeen = now; WeatherData.get(w).markDirty(); }
    }

    // ---- the forecast ------------------------------------------------------------------------------------------------------
    /** per kind: {risk 0..100} and why, for the railway within range of a depot */
    public static final class Forecast {
        public final int[] risk = new int[3];
        public final String[] why = {"", "", ""};
        public final List<List<WeatherData.Cell>> cells = Arrays.asList(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        public double air = 15, worstSnow, worstIce;
        public int leafy, rainIn = -1;
        public boolean raining, coldSnap;
    }

    public static Forecast forecast(World w, BlockPos depot) { return forecast(w, depot, WxTrainConfig.depotRange); }

    public static Forecast forecast(World w, BlockPos depot, int r) {
        Forecast f = new Forecast();
        WeatherData d = WeatherData.get(w);
        long now = w.getTotalWorldTime();
        WeatherData.Cell here = Weather.cell(w, depot);
        f.air = here.air;
        f.raining = w.isRaining();
        f.coldSnap = now < d.coldSnapUntil;
        int rt = w.getWorldInfo().getRainTime();
        f.rainIn = f.raining ? 0 : rt < 24000 ? rt / 1200 : -1;                // minutes until the world's rain starts (vanilla's own clock)
        boolean wetSoon = f.raining || f.rainIn >= 0 && f.rainIn < 15;
        double minAir = 99;
        for (WeatherData.Cell c : d.cells.values()) {
            if (Math.abs(c.x0() + 32 - depot.getX()) > r || Math.abs(c.z0() + 32 - depot.getZ()) > r || c.lastTick == 0) continue;
            minAir = Math.min(minAir, c.air);
            f.worstSnow = Math.max(f.worstSnow, c.snow);
            f.worstIce = Math.max(f.worstIce, c.ice);
            boolean used = c.traffic > 0.3;
            if (c.snow >= 60 || used && c.snowing && c.precip > 0) f.cells.get(PLOUGH).add(c);
            if (c.leafSeen > 0 && now - c.leafSeen < 48000 && now >= c.treatUntil) { f.cells.get(SANDITE).add(c); f.leafy++; }
            boolean wire = c.ice > 0.5 || used && c.air > -4 && c.air <= 1 && (c.precip > 0 || wetSoon) && now >= c.antiIceUntil;
            if (wire) f.cells.get(DEICE).add(c);
        }
        if (minAir > 90) minAir = here.air;
        // snow: lying already, or cold + wet now / soon
        int snow = (int) Math.min(100, f.worstSnow / 2.0);
        if (minAir <= 1 && f.raining) snow = Math.max(snow, 90);
        else if (minAir <= 2 && wetSoon) snow = Math.max(snow, 65);
        else if (f.coldSnap && wetSoon) snow = Math.max(snow, 55);
        f.risk[PLOUGH] = snow;
        f.why[PLOUGH] = f.worstSnow >= 60 ? Math.round(f.worstSnow) + " mm lying" : minAir <= 2 && wetSoon ? String.format(Locale.ROOT, "%.0f C and %s", minAir, f.raining ? "precipitation now" : "precipitation in " + f.rainIn + " min") : "";
        // leaves: where they were seen on the rail head, worse wet and at dawn (dew)
        long t = w.getWorldTime() % 24000;
        boolean dew = t >= 22000 || t < 2000;
        int leaf = f.leafy == 0 ? 0 : (int) Math.min(100, 30 + f.leafy * 8 + (wetSoon ? 30 : 0) + (dew ? 15 : 0));
        f.risk[SANDITE] = leaf;
        f.why[SANDITE] = f.leafy == 0 ? "" : f.leafy + " leafy cell(s)" + (wetSoon ? ", wet" : "") + (dew ? ", dew" : "");
        // ice on the wire: freezing rain band
        int ice = (int) Math.min(100, f.worstIce * 25);
        if (minAir > -4 && minAir <= 1 && wetSoon) ice = Math.max(ice, f.raining ? 85 : 60);
        else if (f.coldSnap) ice = Math.max(ice, 40);
        f.risk[DEICE] = ice;
        f.why[DEICE] = f.worstIce > 0.5 ? String.format(Locale.ROOT, "%.1f mm on the wire", f.worstIce) : minAir > -4 && minAir <= 1 && wetSoon ? "freezing rain band" : f.coldSnap ? "cold snap" : "";
        return f;
    }

    // ---- runs --------------------------------------------------------------------------------------------------------------
    /** plan a run from a depot: its target cells are the forecast's; null = nothing needs it */
    public static WxTrainData.Run plan(World w, BlockPos depot, int range, int kind, String why, boolean auto) {
        WxTrainData td = WxTrainData.get(w);
        for (WxTrainData.Run r : td.runs) if (r.open() && r.kind == kind && r.depot == depot.toLong()) return r;   // one open run per kind and depot
        Forecast f = forecast(w, depot, range);
        List<WeatherData.Cell> cells = f.cells.get(kind);
        if (cells.isEmpty() && !auto) {                                       // a precautionary run over every used cell in range
            for (WeatherData.Cell c : WeatherData.get(w).cells.values())
                if (c.traffic > 0.3 && Math.abs(c.x0() + 32 - depot.getX()) <= range && Math.abs(c.z0() + 32 - depot.getZ()) <= range) cells.add(c);
        }
        if (cells.isEmpty()) return null;
        WxTrainData.Run r = new WxTrainData.Run();
        r.id = td.nextRun++;
        r.kind = kind;
        r.depot = depot.toLong();
        r.created = w.getTotalWorldTime();
        r.why = why;
        r.auto = auto;
        cells.sort((a, b) -> Double.compare(b.traffic, a.traffic));
        for (WeatherData.Cell c : cells) { if (r.targets.size() >= 24) break; r.targets.add(c.key); }
        td.runs.add(0, r);
        while (td.runs.size() > 30) { WxTrainData.Run old = null; for (WxTrainData.Run x : td.runs) if (!x.open()) old = x; if (old == null) break; td.runs.remove(old); }
        td.log(w, "run #" + r.id + " " + KINDS[kind] + " planned" + (auto ? " (AUTO)" : "") + ": " + r.targets.size() + " cell(s) - " + why);
        Recorder.event(w, "WEATHER TRAIN: " + KINDS[kind] + " run #" + r.id + " planned - " + why);
        return r;
    }

    /** call the nearest free train of the run's kind: its crew is told where to go */
    public static String dispatch(World w, WxTrainData.Run r) {
        if (!r.open()) return "§7Run #" + r.id + " is " + WxTrainData.STATES[r.state];
        if (!IR) return "§cImmersive Railroading isn't loaded";
        WxTrainData td = WxTrainData.get(w);
        Set<String> busy = new HashSet<>();
        for (WxTrainData.Run x : td.runs) if (x.open() && x != r && !x.loco.isEmpty()) busy.add(x.loco);
        BlockPos depot = BlockPos.fromLong(r.depot);
        EntityRollingStock best = null;
        double bd = Double.MAX_VALUE;
        for (EntityRollingStock s : cam72cam.mod.world.World.get(w).getEntities(EntityRollingStock.class)) {
            if (s.isDead() || kindOf(w, s) != r.kind || busy.contains(s.getUUID().toString())) continue;
            Vec3d p = s.getPosition();
            double dd = depot.distanceSq(p.x, p.y, p.z);
            if (dd < bd) { bd = dd; best = s; }
        }
        if (best == null) return "§cNo " + KINDS[r.kind].toLowerCase(Locale.ROOT) + " train loaded - tag IR stock " + SHORT[r.kind] + (r.kind == PLOUGH ? " or fit a Snowplough Attachment" : "");
        r.loco = best.getUUID().toString();
        r.locoName = com.dogpound.railmap.track.Rails.name(best);
        r.state = WxTrainData.CALLED;
        r.started = w.getTotalWorldTime();
        td.markDirty();
        StringBuilder where = new StringBuilder();
        int n = 0;
        for (long k : r.targets) { if (n++ >= 4) { where.append(" ..."); break; } WeatherData.Cell c = WeatherData.get(w).cells.get(k); if (c != null) where.append(where.length() == 0 ? "" : ", ").append(c.x0() + 32).append(",").append(c.z0() + 32); }
        tell(best, "§b⚑ WEATHER TRAIN CALLED: " + KINDS[r.kind] + " run #" + r.id + " - " + r.targets.size() + " cell(s): " + where);
        AlarmManager.get(w).raise(w, "wxrun:" + r.id, AlarmManager.Cat.GENERAL, AlarmManager.Sev.INFO, KINDS[r.kind] + " run #" + r.id + " called: " + r.locoName + " - " + r.why, depot, false);
        td.log(w, "run #" + r.id + " " + r.locoName + " called");
        return "§a" + r.locoName + " called for " + KINDS[r.kind].toLowerCase(Locale.ROOT) + " run #" + r.id + " (" + r.targets.size() + " cells)";
    }

    public static String cancel(World w, WxTrainData.Run r, String who) {
        if (!r.open()) return "§7Run #" + r.id + " is already " + WxTrainData.STATES[r.state];
        r.state = WxTrainData.CANCELLED;
        r.ended = w.getTotalWorldTime();
        WxTrainData.get(w).log(w, "run #" + r.id + " cancelled by " + who);
        return "§eRun #" + r.id + " cancelled";
    }

    private static void progress(World w, long now) {
        WxTrainData td = WxTrainData.get(w);
        for (WxTrainData.Run r : td.runs) {
            if (!r.open()) continue;
            if (r.state == WxTrainData.CALLED && !r.done.isEmpty()) { r.state = WxTrainData.RUNNING; td.log(w, "run #" + r.id + " running"); }
            if (r.done.containsAll(r.targets)) { r.state = WxTrainData.DONE; r.ended = now; td.log(w, "run #" + r.id + " " + KINDS[r.kind] + " COMPLETE (" + r.targets.size() + " cells)"); Recorder.event(w, "WEATHER TRAIN: run #" + r.id + " complete"); }
            else if (now - r.created > 48000) { r.state = WxTrainData.DONE; r.ended = now; td.log(w, "run #" + r.id + " timed out: " + r.done.size() + "/" + r.targets.size() + " cells treated"); }
            td.markDirty();
        }
    }

    /** something saw a need (a drone: snow, ice on the wire): the nearest depot desk in AUTO plans and calls a run */
    public static void request(World w, int kind, BlockPos at, String why) {
        if (!on()) return;
        TileWxDepot best = null;
        double bd = (double) WxTrainConfig.depotRange * WxTrainConfig.depotRange;
        for (TileEntity t : new ArrayList<>(w.loadedTileEntityList))
            if (t instanceof TileWxDepot k && !t.isInvalid() && t.getPos().distanceSq(at) < bd) { bd = t.getPos().distanceSq(at); best = k; }
        if (best == null || !best.auto()) return;
        best.autoRun(kind, why);
    }

    static void tell(EntityRollingStock l, String msg) {
        try {
            List<EntityRollingStock> cars = new ArrayList<>();
            if (l instanceof cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock cs) cars.addAll(cs.getTrain()); else cars.add(l);
            for (EntityRollingStock car : cars)
                for (cam72cam.mod.entity.Entity e : car.getPassengers())
                    if (e.internal instanceof EntityPlayerMP mp) mp.sendMessage(new TextComponentString(msg));
        } catch (RuntimeException | LinkageError ignored) { }
    }

    // ---- console (OLE / WX tab, PacketRailState.env) -------------------------------------------------------------------------
    /** WT runs · WF forecast per depot · WV weather vehicles · WL log */
    public static void env(World w, List<String[]> out) {
        if (!on()) return;
        long now = w.getTotalWorldTime();
        WxTrainData td = WxTrainData.get(w);
        for (WxTrainData.Run r : td.runs)
            out.add(new String[]{"WT", String.valueOf(r.id), SHORT[r.kind], WxTrainData.STATES[r.state], r.locoName.isEmpty() ? "-" : r.locoName, r.done.size() + "/" + r.targets.size(),
                    String.valueOf((now - r.created) / 1200), r.why, r.auto ? "AUTO" : "", String.valueOf(r.depot)});
        for (TileEntity t : new ArrayList<>(w.loadedTileEntityList)) {
            if (!(t instanceof TileWxDepot k) || t.isInvalid()) continue;
            Forecast f = forecast(w, t.getPos(), k.range());
            out.add(new String[]{"WF", t.getPos().getX() + "," + t.getPos().getZ(), String.valueOf(f.risk[0]), String.valueOf(f.risk[1]), String.valueOf(f.risk[2]),
                    f.why[0], f.why[1], f.why[2], k.auto() ? "1" : "0", String.valueOf(t.getPos().toLong()), String.format(Locale.ROOT, "%.0f", f.air), f.rainIn < 0 ? "-" : String.valueOf(f.rainIn)});
        }
        if (IR) {
            try {
                for (EntityRollingStock s : cam72cam.mod.world.World.get(w).getEntities(EntityRollingStock.class)) {
                    if (s.isDead()) continue;
                    int k = kindOf(w, s);
                    if (k < 0) continue;
                    double kmh = s instanceof EntityMoveableRollingStock m && m.getCurrentSpeed() != null ? Math.abs(m.getCurrentSpeed().metric()) : 0;
                    String run = "";
                    for (WxTrainData.Run r : td.runs) if (r.open() && r.loco.equals(s.getUUID().toString())) run = "#" + r.id;
                    Vec3d p = s.getPosition();
                    out.add(new String[]{"WV", com.dogpound.railmap.track.Rails.name(s), SHORT[k], how(w, s), String.valueOf(Math.round(kmh)), (int) p.x + "," + (int) p.z, run});
                }
            } catch (RuntimeException | LinkageError ignored) { }
        }
        for (int i = 0; i < Math.min(12, td.log.size()); i++) out.add(new String[]{"WL", td.log.get(i)});
    }

    /** console buttons 40-45: plan plough / sandite / de-ice (arg = depot pos), dispatch, cancel (arg = run id), auto toggle (depot pos) */
    public static String op(World w, int op, String arg, String who) {
        switch (op) {
            case 40: case 41: case 42: {
                if (!(w.getTileEntity(BlockPos.fromLong(Long.parseLong(arg))) instanceof TileWxDepot k)) return "Depot desk not loaded";
                return k.planFromConsole(op - 40, who);
            }
            case 43: case 44: {
                WxTrainData.Run r = WxTrainData.get(w).run(Integer.parseInt(arg));
                if (r == null) return "No run " + arg;
                if (op == 44) return cancel(w, r, who + " (console)");
                if (r.kind == SANDITE && w.getTileEntity(BlockPos.fromLong(r.depot)) instanceof TileWxDepot k) { String no = k.takeSand(); if (no != null) return no; }
                return dispatch(w, r);
            }
            case 45: {
                if (!(w.getTileEntity(BlockPos.fromLong(Long.parseLong(arg))) instanceof TileWxDepot k)) return "Depot desk not loaded";
                return k.toggleAuto(who);
            }
            default: return null;
        }
    }
}
