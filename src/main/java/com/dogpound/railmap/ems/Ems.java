package com.dogpound.railmap.ems;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.incident.IncidentLog;
import com.dogpound.railmap.incident.IncidentOps;
import com.dogpound.railmap.server.StationData;
import com.dogpound.railmap.signal.TileCrossing;
import com.dogpound.railmap.stn.StnUtil;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.*;

/**
 * Emergency services (her station-shops + emergency list #6). Fire, ambulance and police stations (TileEmergencyStation)
 * answer railway incidents (incident.IncidentLog: derailments and wrecks mirrored from auto/Disruptions, fires from the
 * Fire Alarm Panels, dangerous goods, evacuations):
 * <ul>
 *   <li><b>Dispatch</b> - once a second every open incident is checked against what it needs: fire service for fires,
 *   dangerous goods and serious derailments; ambulance when people are hurt (the incident asks for MEDIC, a derailment
 *   with passengers, an evacuation, or a player near it is injured); police for serious derailments, evacuations and
 *   dangerous goods. The nearest station of the kind with its AUTO key on, powered and with its vehicle in, turns out
 *   (or a dispatcher presses DISPATCH on the desk / the console). The station's turnout tones sound, the incident log
 *   and the alarm list are told.</li>
 *   <li><b>Driving</b> - the crew get in (fire 8 s, ambulance 5 s, police 3 s) and the vehicle drives the roads to the
 *   scene (EntityEmergencyVehicle, an A* road follower like Pride Realms' delivery robot), lights flashing and siren
 *   sounding. Where its chunks aren't loaded it carries on "off screen" at road speed, and appears again when a player
 *   is near.</li>
 *   <li><b>On scene</b> - the response time (turnout to on scene) is logged on the incident, the station and the
 *   console. Firefighters put out every fire block near the incident (faster with water buckets in the lockers) and
 *   report the fire out; paramedics treat injured players nearby and carry anyone badly hurt (or who boards) to their
 *   bed or the nearest station; police put up a temporary cordon of barriers on the empty ground around the scene and
 *   close the level crossings near it (the barriers come down) until the incident reaches recovery.</li>
 *   <li><b>Return</b> - then they take the cordon down, reopen the crossings and drive back to base; the run's total
 *   time goes in the history.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class Ems {
    private Ems() {}

    public enum Kind {
        FIRE("Fire engine", "Fire Station", "FIRE", 8), AMBULANCE("Ambulance", "Ambulance Station", "AMB", 5), POLICE("Police car", "Police Station", "POL", 3);
        public final String vehicle, station, code;
        public final int turnoutSec;
        Kind(String v, String s, String c, int t) { vehicle = v; station = s; code = c; turnoutSec = t; }
    }

    /** level crossings held down by police on scene, per dimension (rebuilt every second from the runs) */
    private static final Map<Integer, Set<Long>> CLOSED = new HashMap<>();

    public static boolean crossingClosed(World w, BlockPos p) {
        Set<Long> s = CLOSED.get(w.provider.getDimension());
        return s != null && s.contains(p.toLong());
    }

    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote || e.world.getTotalWorldTime() % 20 != 11) return;
        try { tick(e.world); } catch (RuntimeException | LinkageError ex) { RailMap.LOG.warn("[IR Extras] emergency services tick failed: {}", ex.toString()); }
    }

    static void tick(World w) {
        EmsData d = EmsData.get(w);
        long now = w.getTotalWorldTime();
        if (EmsConfig.enabled && EmsConfig.autoDispatch && hasStations(w)) autoDispatch(w, d);
        Set<Long> closed = new HashSet<>();
        for (EmsData.Run r : d.active()) {
            progress(w, d, r, now);
            if (r.active() && r.kind == Kind.POLICE && r.stage == EmsData.Stage.ON_SCENE) closed.addAll(r.crossings);
        }
        CLOSED.put(w.provider.getDimension(), closed);
        d.prune();
    }

    private static boolean hasStations(World w) {
        for (net.minecraft.tileentity.TileEntity t : w.loadedTileEntityList) if (t instanceof TileEmergencyStation) return true;
        return false;
    }

    // ---- dispatch -------------------------------------------------------------------------------------------------------
    /** does this incident want this service? */
    public static boolean wants(World w, Kind k, IncidentLog.Rec r) {
        boolean wreck = r.type == IncidentLog.Type.WRECK;
        switch (k) {
            case FIRE: return r.type == IncidentLog.Type.FIRE || r.type == IncidentLog.Type.HAZMAT || wreck && r.level >= EmsConfig.fireWreckLevel || r.needs(IncidentLog.FIRE);
            case AMBULANCE: return r.needs(IncidentLog.MEDICAL) || r.type == IncidentLog.Type.EVAC || wreck && r.paxAffected > 0 || w != null && hurtNear(w, r.x, r.y, r.z, r.r + 12) != null;
            default: return r.type == IncidentLog.Type.EVAC || r.type == IncidentLog.Type.HAZMAT || wreck && r.level >= EmsConfig.policeWreckLevel || r.type == IncidentLog.Type.FIRE && r.level >= 2;
        }
    }

    static void autoDispatch(World w, EmsData d) {
        for (IncidentLog.Rec r : IncidentLog.get(w).active()) {
            if (r.stage.ordinal() >= IncidentLog.Stage.REOPENING.ordinal()) continue;
            for (Kind k : Kind.values()) {
                if (d.sent(r.id, k) || !wants(w, k, r)) continue;
                TileEmergencyStation s = nearest(w, k, r.pos(), true);
                if (s != null) dispatch(w, s, r, "automatic");
            }
        }
    }

    /** the nearest station of a kind that can turn out now (auto = only ones with AUTO on) */
    public static TileEmergencyStation nearest(World w, Kind k, BlockPos at, boolean auto) {
        TileEmergencyStation best = null;
        double bd = (double) EmsConfig.maxRange * EmsConfig.maxRange;
        for (TileEmergencyStation s : StnUtil.loaded(w, TileEmergencyStation.class)) {
            if (s.kind() != k || !s.available() || auto && !s.auto()) continue;
            double dd = s.getPos().distanceSq(at);
            if (dd < bd) { bd = dd; best = s; }
        }
        return best;
    }

    /** turn a station out to an incident (r) or, with r null, to a spot (a test run) */
    public static String dispatch(World w, TileEmergencyStation s, IncidentLog.Rec r, String who) { return dispatch(w, s, r, r == null ? null : r.pos(), who); }

    public static String dispatch(World w, TileEmergencyStation s, IncidentLog.Rec r, BlockPos to, String who) {
        if (!EmsConfig.enabled) return "§cEmergency services are switched off (config)";
        if (!s.available()) return "§c" + s.callSign() + " is not available (" + s.why() + ")";
        if (to == null) return "§cNowhere to go";
        if (to.distanceSq(s.getPos()) > (double) EmsConfig.maxRange * EmsConfig.maxRange) return "§cToo far - over " + EmsConfig.maxRange + " blocks";
        EmsData d = EmsData.get(w);
        long now = w.getTotalWorldTime();
        EmsData.Run run = d.newRun();
        run.kind = s.kind();
        run.incident = r == null ? -1 : r.id;
        run.station = s.getPos().toLong();
        run.stationName = s.callSign();
        run.vehicle = s.kind().vehicle + " " + s.unitNo();
        run.turnout = now; run.stageAt = now;
        run.tx = to.getX() + 0.5; run.ty = to.getY(); run.tz = to.getZ() + 0.5;
        BlockPos bay = s.bay();
        run.vx = bay.getX() + 0.5; run.vy = bay.getY(); run.vz = bay.getZ() + 0.5;
        s.turnedOut(run);
        String what = run.vehicle + " (" + run.stationName + ") turned out" + (r == null ? " to " + to.getX() + "," + to.getZ() : " to incident #" + r.id + " " + r.type.label) + " by " + who;
        if (r != null) {
            IncidentLog log = IncidentLog.get(w);
            int bit = s.kind() == Kind.FIRE ? IncidentLog.FIRE : s.kind() == Kind.AMBULANCE ? IncidentLog.MEDICAL : IncidentLog.PROTECT;
            if (s.kind() != Kind.POLICE) { r.needs |= bit; r.assigned |= bit; }
            log.log(w, r, run.vehicle.toUpperCase(Locale.ROOT) + " from " + run.stationName + " turned out (" + who + ")");
            log.stage(w, r, IncidentLog.Stage.RESPONDING, "");
            log.changed();
        }
        AlarmManager.get(w).raise(w, "ems:" + run.id, AlarmManager.Cat.INCIDENT, AlarmManager.Sev.INFO, what, s.getPos(), false);
        Recorder.event(w, "EMS: " + what);
        d.markDirty();
        return "§e" + what;
    }

    // ---- the run --------------------------------------------------------------------------------------------------------
    static Entity vehicle(World w, EmsData.Run r) {
        if (r.entity == null || !(w instanceof WorldServer ws)) return null;
        Entity e = ws.getEntityFromUuid(r.entity);
        return e != null && !e.isDead ? e : null;
    }

    static void progress(World w, EmsData d, EmsData.Run r, long now) {
        TileEmergencyStation home = w.isBlockLoaded(BlockPos.fromLong(r.station)) && w.getTileEntity(BlockPos.fromLong(r.station)) instanceof TileEmergencyStation t ? t : null;
        if (!EmsConfig.enabled && r.stage != EmsData.Stage.RETURNING) { leaveScene(w, r, "service stood down (config)"); return; }
        switch (r.stage) {
            case TURNOUT -> {
                if (now - r.turnout < r.kind.turnoutSec * 20L) return;
                r.stage = EmsData.Stage.RESPONDING; r.stageAt = now; r.left = now;
                spawn(w, r);
                if (home != null) home.left(r);
                d.markDirty();
            }
            case RESPONDING, TRANSPORT, RETURNING -> {
                Entity v = vehicle(w, r);
                if (v != null) { r.vx = v.posX; r.vy = v.posY; r.vz = v.posZ; }
                else {
                    double dx = r.tx - r.vx, dz = r.tz - r.vz, dist = Math.sqrt(dx * dx + dz * dz), step = EmsConfig.speed * 20 * 0.8;
                    if (dist <= step + 3) { r.vx = r.tx; r.vz = r.tz; r.vy = r.ty; arrived(w, r); }
                    else { r.vx += dx / dist * step; r.vz += dz / dist * step; }
                    BlockPos at = new BlockPos(r.vx, r.vy, r.vz);
                    if (r.active() && r.stage != EmsData.Stage.ON_SCENE && w.isBlockLoaded(at) && w.isAreaLoaded(at, 16) && near(w, at, 96)) spawn(w, r);
                }
                if (r.stage == EmsData.Stage.RESPONDING && now - r.stageAt == 20L * 300) {
                    AlarmManager.get(w).raise(w, "ems-late:" + r.id, AlarmManager.Cat.INCIDENT, AlarmManager.Sev.MINOR, r.vehicle + " still not on scene after 5 min", new BlockPos(r.tx, r.ty, r.tz), false);
                }
            }
            case ON_SCENE -> work(w, d, r, now);
            default -> { }
        }
    }

    private static boolean near(World w, BlockPos at, double reach) {
        for (EntityPlayer p : w.playerEntities) if (p.getDistanceSq(at) < reach * reach) return true;
        return false;
    }

    /** put the vehicle in the world at its last known spot (a new one; a stale copy that loads later removes itself) */
    static void spawn(World w, EmsData.Run r) {
        Entity old = vehicle(w, r);
        if (old != null) return;
        BlockPos at = new BlockPos(r.vx, r.vy, r.vz);
        if (!w.isBlockLoaded(at)) { r.entity = null; return; }
        BlockPos g = RoadPath.ground(w, at, 6);
        if (g == null) g = w.getTopSolidOrLiquidBlock(at);
        EntityEmergencyVehicle v = new EntityEmergencyVehicle(w, r);
        v.setLocationAndAngles(g.getX() + 0.5, g.getY(), g.getZ() + 0.5, (float) Math.toDegrees(Math.atan2(-(r.tx - g.getX()), r.tz - g.getZ())), 0);
        r.entity = v.getUniqueID();
        w.spawnEntity(v);
    }

    /** the vehicle reached its target */
    static void arrived(World w, EmsData.Run r) {
        long now = w.getTotalWorldTime();
        EmsData d = EmsData.get(w);
        IncidentLog.Rec inc = r.incident < 0 ? null : IncidentLog.get(w).byId(r.incident);
        TileEmergencyStation home = w.getTileEntity(BlockPos.fromLong(r.station)) instanceof TileEmergencyStation t ? t : null;
        switch (r.stage) {
            case RESPONDING -> {
                r.stage = EmsData.Stage.ON_SCENE; r.onScene = now; r.stageAt = now;
                String resp = IncidentLog.mins(r.response());
                AlarmManager.get(w).clear("ems:" + r.id);
                AlarmManager.get(w).clear("ems-late:" + r.id);
                if (inc != null) {
                    IncidentLog log = IncidentLog.get(w);
                    log.log(w, inc, r.vehicle.toUpperCase(Locale.ROOT) + " ON SCENE - response " + resp);
                    log.stage(w, inc, IncidentLog.Stage.ON_SCENE, r.kind.vehicle.toLowerCase(Locale.ROOT) + " on scene");
                }
                Recorder.event(w, "EMS: " + r.vehicle + " on scene" + (inc == null ? "" : " at incident #" + inc.id) + ", response " + resp);
                if (home != null) home.onScene(r);
                if (r.kind == Kind.POLICE) cordon(w, r, inc, home);
            }
            case TRANSPORT -> {
                EntityPlayer p = w.getPlayerEntityByName(r.patient);
                Entity v = vehicle(w, r);
                if (p != null) {
                    p.dismountRidingEntity();
                    if (v != null) p.setPositionAndUpdate(v.posX + 1.2, v.posY, v.posZ);
                    p.setHealth(p.getMaxHealth());
                    p.extinguish();
                    p.sendMessage(new TextComponentString("§aThe paramedics hand you over - you're patched up. Take care!"));
                }
                Recorder.event(w, "EMS: " + r.vehicle + " delivered patient " + r.patient);
                if (inc != null) IncidentLog.get(w).log(w, inc, "AMBULANCE delivered patient " + r.patient + " to safety");
                r.patient = "";
                goHome(w, r);
            }
            case RETURNING -> {
                r.stage = EmsData.Stage.DONE; r.back = now; r.stageAt = now;
                Entity v = vehicle(w, r);
                if (v != null) { v.removePassengers(); v.setDead(); }
                String line = String.format(Locale.ROOT, "%s %s %s: response %s, on scene %s, total %s%s", Recorder.clock(w, r.turnout), r.vehicle, r.incident < 0 ? "test run" : "incident #" + r.incident,
                        r.onScene > 0 ? IncidentLog.mins(r.response()) : "-", r.onScene > 0 && r.left > 0 ? IncidentLog.mins(Math.max(0, now - r.onScene)) : "-", IncidentLog.mins(now - r.turnout),
                        r.work > 0 ? ", " + r.work + (r.kind == Kind.FIRE ? " fires out" : r.kind == Kind.AMBULANCE ? " treated" : " barriers") : "");
                d.history(line);
                Recorder.event(w, "EMS: " + r.vehicle + " back at base");
                if (home != null) home.back(r);
            }
            default -> { }
        }
        d.markDirty();
    }

    /** turn round for base (target = the station's bay) */
    static void goHome(World w, EmsData.Run r) {
        TileEmergencyStation home = w.getTileEntity(BlockPos.fromLong(r.station)) instanceof TileEmergencyStation t ? t : null;
        BlockPos bay = home != null ? home.bay() : BlockPos.fromLong(r.station);
        r.tx = bay.getX() + 0.5; r.ty = bay.getY(); r.tz = bay.getZ() + 0.5;
        r.stage = EmsData.Stage.RETURNING; r.stageAt = w.getTotalWorldTime();
        EmsData.get(w).markDirty();
    }

    /** finish on scene: take the cordon down, reopen the crossings, go home */
    static void leaveScene(World w, EmsData.Run r, String why) {
        int removed = 0;
        for (long l : r.cordon) {
            BlockPos p = BlockPos.fromLong(l);
            if (w.isBlockLoaded(p) && w.getBlockState(p).getBlock() instanceof BlockCordon) { w.setBlockToAir(p); removed++; }
        }
        boolean hadCordon = !r.cordon.isEmpty();
        r.cordon.clear();
        r.crossings.clear();
        IncidentLog.Rec inc = r.incident < 0 ? null : IncidentLog.get(w).byId(r.incident);
        if (inc != null) IncidentLog.get(w).log(w, inc, r.vehicle.toUpperCase(Locale.ROOT) + " leaving scene - " + why + (hadCordon ? ", cordon removed (" + removed + ")" : ""));
        if (r.stage == EmsData.Stage.TURNOUT) { r.stage = EmsData.Stage.DONE; r.back = w.getTotalWorldTime(); return; }
        goHome(w, r);
    }

    // ---- on-scene work ------------------------------------------------------------------------------------------------
    static void work(World w, EmsData d, EmsData.Run r, long now) {
        IncidentLog.Rec inc = r.incident < 0 ? null : IncidentLog.get(w).byId(r.incident);
        TileEmergencyStation home = w.getTileEntity(BlockPos.fromLong(r.station)) instanceof TileEmergencyStation t ? t : null;
        long on = now - r.onScene;
        boolean over = on > EmsConfig.maxOnSceneSeconds * 20L, closed = inc == null ? on > 20 * 60 : !inc.open();
        BlockPos at = new BlockPos(r.tx, r.ty, r.tz);
        boolean loaded = w.isAreaLoaded(at, 8);
        switch (r.kind) {
            case FIRE -> {
                int out = loaded ? extinguish(w, at, EmsConfig.fireRadius, 1 + r.crew / 2 + (home != null && home.foam() ? 3 : 0)) : 0;
                r.work += out;
                if (out > 0) { r.note = "0"; d.markDirty(); return; }
                int quiet = parse(r.note) + 1;
                r.note = String.valueOf(quiet);
                if (quiet == 10 && inc != null && inc.has(IncidentLog.FIRE) && inc.contained == 0 && (inc.type == IncidentLog.Type.FIRE || inc.type == IncidentLog.Type.HAZMAT))
                    IncidentOps.order(w, inc.id, "fire", r.vehicle);
                if (over || closed || quiet >= 30 && (inc == null || inc.contained > 0 || inc.type == IncidentLog.Type.WRECK || inc.type == IncidentLog.Type.FAILED)) leaveScene(w, r, "fire out, " + r.work + " fire blocks put out");
            }
            case AMBULANCE -> {
                Entity v = vehicle(w, r);
                int treated = 0;
                EntityPlayer carry = null;
                for (EntityPlayer p : w.playerEntities) {
                    if (p.isDead || p.isSpectator() || p.getDistanceSq(r.tx, r.ty, r.tz) > Math.pow((inc == null ? 8 : inc.r) + 12, 2)) continue;
                    if (p.isBurning()) p.extinguish();
                    if (p.getHealth() >= p.getMaxHealth()) continue;
                    boolean meds = home != null && home.useMedical();
                    p.heal(meds ? 3f : 1f);
                    treated++;
                    if (p.getHealth() < p.getMaxHealth() * EmsConfig.carryBelowPct / 100f && carry == null) carry = p;
                }
                if (treated > 0) { r.work = Math.max(r.work, treated); r.note = "0"; }
                if (carry != null && v != null && board(w, r, carry)) return;
                int quiet = treated > 0 ? 0 : parse(r.note) + 1;
                r.note = String.valueOf(quiet);
                if (inc != null && treated > 0 && inc.needs(IncidentLog.MEDICAL) && quiet == 0 && r.work == treated) IncidentLog.get(w).log(w, inc, "PARAMEDICS treating " + treated + " casualt" + (treated == 1 ? "y" : "ies"));
                if (over || closed || quiet >= 30 && (inc == null || on > 20 * 120 || inc.contained > 0)) leaveScene(w, r, r.work > 0 ? r.work + " casualties treated" : "no casualties");
            }
            default -> {
                boolean recovered = inc == null ? on > 20 * 90 : inc.stage.ordinal() >= IncidentLog.Stage.RECOVERY.ordinal();
                if (over || closed || recovered) leaveScene(w, r, recovered ? "scene handed back for recovery" : "incident closed");
            }
        }
        d.markDirty();
    }

    private static int parse(String s) { try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; } }

    /** put out up to `max` fire blocks within r of the spot (and burning mobs); returns how many */
    static int extinguish(World w, BlockPos c, int r, int max) {
        int n = 0;
        for (BlockPos.MutableBlockPos p : BlockPos.getAllInBoxMutable(c.add(-r, -4, -r), c.add(r, 8, r))) {
            if (n >= max) break;
            if (!w.isBlockLoaded(p) || w.getBlockState(p).getBlock() != Blocks.FIRE) continue;
            w.setBlockToAir(p);
            if (w instanceof WorldServer ws) ws.spawnParticle(EnumParticleTypes.WATER_SPLASH, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.1);
            w.playSound(null, p, net.minecraft.init.SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.6f, 1.2f);
            n++;
        }
        for (Entity e : w.getEntitiesWithinAABB(net.minecraft.entity.EntityLivingBase.class, new net.minecraft.util.math.AxisAlignedBB(c).grow(r))) if (e.isBurning()) e.extinguish();
        return n;
    }

    /** an injured player near the spot (most hurt first), or null */
    public static EntityPlayer hurtNear(World w, double x, double y, double z, double reach) {
        EntityPlayer best = null;
        for (EntityPlayer p : w.playerEntities) {
            if (p.isDead || p.isSpectator() || p.capabilities.isCreativeMode || p.getDistanceSq(x, y, z) > reach * reach) continue;
            if (p.getHealth() < p.getMaxHealth() * 0.6f && (best == null || p.getHealth() < best.getHealth())) best = p;
        }
        return best;
    }

    /** a patient gets in the ambulance: off to their bed (this world) or the nearest station */
    static boolean board(World w, EmsData.Run r, EntityPlayer p) {
        Entity v = vehicle(w, r);
        if (v == null || r.kind != Kind.AMBULANCE || r.stage != EmsData.Stage.ON_SCENE && r.stage != EmsData.Stage.RESPONDING) return false;
        BlockPos to = null;
        BlockPos bed = p.getBedLocation(w.provider.getDimension());
        if (bed != null && bed.distanceSq(v.getPosition()) < (double) EmsConfig.maxRange * EmsConfig.maxRange) to = bed;
        if (to == null) {
            double bd = Double.MAX_VALUE;
            for (Long k : StationData.get(w).names().keySet()) { double dd = BlockPos.fromLong(k).distanceSq(v.getPosition()); if (dd < bd) { bd = dd; to = BlockPos.fromLong(k); } }
        }
        if (to == null) return false;
        p.startRiding(v, true);
        r.patient = p.getName();
        r.tx = to.getX() + 0.5; r.ty = to.getY(); r.tz = to.getZ() + 0.5;
        r.stage = EmsData.Stage.TRANSPORT; r.stageAt = w.getTotalWorldTime(); r.work++;
        p.sendMessage(new TextComponentString("§bParamedics: \"We've got you - taking you " + (to.equals(bed) ? "home to your bed" : "to " + StationData.get(w).names().getOrDefault(to.toLong(), "the station")) + ".\""));
        IncidentLog.Rec inc = r.incident < 0 ? null : IncidentLog.get(w).byId(r.incident);
        if (inc != null) IncidentLog.get(w).log(w, inc, "AMBULANCE carrying casualty " + p.getName() + " from the scene");
        Recorder.event(w, "EMS: " + r.vehicle + " carrying " + p.getName());
        EmsData.get(w).markDirty();
        return true;
    }

    // ---- police cordon --------------------------------------------------------------------------------------------------
    /** the cordon ring: spots every ~3 blocks round a circle (pure geometry, for the self-test too) */
    public static List<int[]> ring(double cx, double cz, double radius, int max) {
        List<int[]> o = new ArrayList<>();
        int n = Math.max(8, (int) Math.round(2 * Math.PI * radius / 3));
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < n && o.size() < max; i++) {
            double a = 2 * Math.PI * i / n;
            int x = (int) Math.floor(cx + Math.cos(a) * radius), z = (int) Math.floor(cz + Math.sin(a) * radius);
            if (seen.add(((long) x << 32) ^ (z & 0xffffffffL))) o.add(new int[]{x, z});
        }
        return o;
    }

    static void cordon(World w, EmsData.Run r, IncidentLog.Rec inc, TileEmergencyStation home) {
        if (EmsConfig.cordon) {
            double radius = Math.min(24, Math.max(6, (inc == null ? 4 : inc.r) * 0.5 + 4));
            int max = Math.min(64, EmsConfig.cordonMax + (home == null ? 0 : home.barrierStock()));
            for (int[] xz : ring(r.tx, r.tz, radius, max * 2)) {
                if (r.cordon.size() >= max) break;
                BlockPos p = spot(w, xz[0], (int) r.ty, xz[1]);
                if (p == null) continue;
                w.setBlockState(p, EmsBlocks.cordon.getDefaultState().withProperty(BlockCordon.FACING, net.minecraft.util.EnumFacing.fromAngle(Math.toDegrees(Math.atan2(xz[1] + 0.5 - r.tz, xz[0] + 0.5 - r.tx)) - 90)), 3);
                r.cordon.add(p.toLong());
            }
            r.work = r.cordon.size();
        }
        if (EmsConfig.crossingRadius > 0)
            for (TileCrossing c : StnUtil.near(w, TileCrossing.class, new BlockPos(r.tx, r.ty, r.tz), EmsConfig.crossingRadius)) r.crossings.add(c.getPos().toLong());
        if (inc != null) IncidentLog.get(w).log(w, inc, "POLICE CORDON: " + r.cordon.size() + " barriers" + (r.crossings.isEmpty() ? "" : ", " + r.crossings.size() + " level crossing(s) closed"));
        Recorder.event(w, "EMS: police cordon " + r.cordon.size() + " barriers, " + r.crossings.size() + " crossings closed");
    }

    /** an empty spot standing on solid ground near height y (never on track, water or another block) */
    static BlockPos spot(World w, int x, int y, int z) {
        for (int dy = 4; dy >= -6; dy--) {
            BlockPos p = new BlockPos(x, y + dy, z), b = p.down();
            if (!w.isBlockLoaded(p) || !w.isAirBlock(p)) continue;
            IBlockState s = w.getBlockState(b);
            if (!s.isSideSolid(w, b, net.minecraft.util.EnumFacing.UP) || s.getMaterial().isLiquid() || s.getBlock() instanceof BlockCordon) continue;
            try { if (com.dogpound.railmap.scan.SwitchControl.isRail(w, b) || com.dogpound.railmap.scan.SwitchControl.isRail(w, p)) return null; } catch (RuntimeException | LinkageError ignored) { }
            if (s.getBlock() instanceof net.minecraft.block.BlockRailBase) return null;
            return p;
        }
        return null;
    }

    // ---- self-test -------------------------------------------------------------------------------------------------------
    public static List<String> selfTest() {
        List<String> f = new ArrayList<>();
        List<int[]> r = ring(0.5, 0.5, 10, 100);
        if (r.size() < 18 || r.size() > 24) f.add("ring of radius 10 should have ~21 spots, has " + r.size());
        for (int[] p : r) { double d = Math.hypot(p[0] + 0.5 - 0.5, p[1] + 0.5 - 0.5); if (d < 8.5 || d > 11.5) { f.add("ring spot off the circle: " + d); break; } }
        if (ring(0, 0, 10, 5).size() != 5) f.add("ring max ignored");
        IncidentLog.Rec x = new IncidentLog.Rec();
        x.type = IncidentLog.Type.FIRE; x.level = 1;
        if (!wants(null, Kind.FIRE, x)) f.add("a fire wants the fire service");
        if (wants(null, Kind.POLICE, x)) f.add("a level-1 fire doesn't need police");
        x.type = IncidentLog.Type.WRECK; x.level = 3; x.paxAffected = 20;
        if (!wants(null, Kind.AMBULANCE, x) || !wants(null, Kind.POLICE, x) || !wants(null, Kind.FIRE, x)) f.add("a level-3 derailment with passengers wants all three");
        x.level = 1; x.paxAffected = 0;
        if (wants(null, Kind.AMBULANCE, x) || wants(null, Kind.POLICE, x)) f.add("a level-1 derailment with nobody aboard wants nobody but the crew");
        x.type = IncidentLog.Type.EVAC;
        if (!wants(null, Kind.POLICE, x) || !wants(null, Kind.AMBULANCE, x)) f.add("an evacuation wants police + ambulance");
        EmsData.Run run = new EmsData.Run();
        run.turnout = 100; run.onScene = 100 + 20 * 83;
        if (!IncidentLog.mins(run.response()).equals(IncidentLog.mins(20 * 83))) f.add("response time");
        {
            run.cordon.add(123L); run.kind = Kind.POLICE; run.stage = EmsData.Stage.ON_SCENE;
            EmsData.Run back = EmsData.Run.read(run.write());
            if (back.kind != Kind.POLICE || back.stage != EmsData.Stage.ON_SCENE || back.cordon.size() != 1 || back.cordon.get(0) != 123L) f.add("run save/load");
        }
        return f;
    }
}
