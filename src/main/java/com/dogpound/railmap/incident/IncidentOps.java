package com.dogpound.railmap.incident;

import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.auto.Disruptions;
import com.dogpound.railmap.doors.TrainDoors;
import com.dogpound.railmap.graph.TrainNode;
import com.dogpound.railmap.grid.GridData;
import com.dogpound.railmap.grid.GridKind;
import com.dogpound.railmap.grid.History;
import com.dogpound.railmap.server.TrainTracker;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * What incident command does (her ChatGPT list #67-70): mirrors wrecks and failed trains from auto/Disruptions into
 * incident records, moves every incident through its stages (reported, accepted, responding, on scene, contained,
 * recovery, reopening, closed), reopens the closed sections in stages as each one is safe, watches rescues and
 * evacuations, counts the people involved - and carries out the commander's orders from the Incident Command Desk,
 * the console and the command: accept, level, crew, rescue engine, fire service, medical, protect adjacent lines,
 * isolate power, evacuate, all clear, reopen next section, close, print.
 */
public final class IncidentOps {
    private IncidentOps() {}

    private static final Map<String, Long> REOPEN_AT = new HashMap<>();

    // ---- once a second -------------------------------------------------------------------------------------------
    private static final Set<Integer> STARTED = new HashSet<>();

    static void tick(World w, long now) {
        if (!IncidentConfig.incidentCommand) return;
        IncidentLog log = IncidentLog.get(w);
        if (STARTED.add(w.provider.getDimension()))                             // after a restart Disruptions starts empty: old links are void
            for (IncidentLog.Rec r : log.active()) if (r.disruptId >= 0) { r.disruptId = -2 - r.disruptId; log.log(w, r, "server restarted - site re-surveyed"); }
        mirror(w, log, now);
        for (IncidentLog.Rec r : log.active()) progress(w, log, r, now);
        IncidentHooks.tidy(w);
    }

    private static void mirror(World w, IncidentLog log, long now) {
        int dim = w.provider.getDimension();
        Set<Integer> live = new HashSet<>();
        for (Disruptions.Incident in : Disruptions.incidents(dim)) {
            if (in.kind == Disruptions.Kind.HOLD) continue;
            live.add(in.id);
            IncidentLog.Rec r = log.byDisruption(in.id);
            if (r == null) {
                if (!in.blocking()) continue;
                r = openFrom(w, log, in);
            }
            for (UUID u : in.stock) r.stock.add(u.toString());
            int held = 0;
            for (UUID u : in.held) if (Disruptions.isHeld(dim, u)) held++;
            r.services = held;
            r.heldMax = Math.max(r.heldMax, held);
            switch (in.phase) {
                case CREW: log.stage(w, r, IncidentLog.Stage.RESPONDING, "recovery crew on the way"); break;
                case WORKING: if (in.kind == Disruptions.Kind.WRECK) { r.assigned |= IncidentLog.CREW; log.stage(w, r, IncidentLog.Stage.ON_SCENE, "recovery crew on site"); log.stage(w, r, IncidentLog.Stage.CONTAINED, "site made safe"); } break;
                case CLEAR: log.stage(w, r, IncidentLog.Stage.RECOVERY, "line clear: " + in.what); break;
                default: break;
            }
        }
        for (IncidentLog.Rec r : log.active())
            if (r.disruptId >= 0 && !live.contains(r.disruptId)) {
                r.disruptId = -2 - r.disruptId;                                          // its disruption is over (kept for the record)
                log.log(w, r, "line clear - held trains restarted");
                if (r.hazKind != 0 && r.cleanUntil == 0) { r.cleanUntil = now + Hazmat.cleanup(r.hazKind) * 20L; log.log(w, r, "dangerous goods clean-up " + Hazmat.cleanup(r.hazKind) + " s"); }
                log.stage(w, r, IncidentLog.Stage.RECOVERY, "");
            }
    }

    private static IncidentLog.Rec openFrom(World w, IncidentLog log, Disruptions.Incident in) {
        boolean wreck = in.kind == Disruptions.Kind.WRECK;
        IncidentLog.Rec r = log.open(w, wreck ? IncidentLog.Type.WRECK : IncidentLog.Type.FAILED, in.what, in.x, in.y, in.z, in.radius, wreck ? (in.stock.size() >= 3 ? 3 : 2) : 1);
        r.disruptId = in.id;
        for (UUID u : in.stock) r.stock.add(u.toString());
        r.needs |= wreck ? IncidentLog.CREW : IncidentLog.RESCUE;
        Analytics.count(w, wreck ? "wrecks" : "breakdowns", 1);
        Analytics.count(w, "incidents", 1);
        // dangerous goods in the train
        Hazmat.Placard worst = new Hazmat.Placard();
        cam72cam.mod.world.World umc = cam72cam.mod.world.World.get(w);
        int pax = 0;
        for (EntityRollingStock s : umc.getEntities(EntityRollingStock.class)) {
            if (!in.stock.contains(s.getUUID()) && !trainOf(s, in.stock)) continue;
            Hazmat.Placard p = Hazmat.of(s);
            if (Hazmat.worse(p.kind, worst.kind)) worst = p;
            pax += s.getPassengerCount();
            if (s instanceof Locomotive) r.blackbox.addAll(TrainRecorder.download(w, s.getUUID().toString(), 120));
        }
        r.paxAffected = pax;
        if (pax > 0 && wreck) r.needs |= IncidentLog.MEDICAL;
        if (worst.kind != 0) hazmat(w, log, r, worst);
        if (IncidentConfig.autoProtectAdjacent && r.level >= 2) protect(w, log, r, "automatic (level " + r.level + ")");
        return r;
    }

    private static boolean trainOf(EntityRollingStock s, Set<UUID> stock) {
        if (!(s instanceof cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock c)) return false;
        try { for (cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock x : c.getTrain()) if (stock.contains(x.getUUID())) return true; } catch (RuntimeException ignored) { }
        return false;
    }

    /** a train with dangerous goods: placard, response rules, cordon, evacuation for the nasty ones */
    static void hazmat(World w, IncidentLog log, IncidentLog.Rec r, Hazmat.Placard p) {
        if (!Hazmat.worse(p.kind, r.hazKind) && !r.haz.isEmpty()) return;
        r.haz = p.line();
        r.hazKind = p.kind;
        r.needs |= IncidentLog.HAZMAT | IncidentLog.FIRE;
        r.level = Math.max(r.level, p.kind >= 2 ? 3 : 2);
        log.log(w, r, "DANGEROUS GOODS " + r.haz + " - " + Hazmat.RULE[p.kind]);
        if (p.kind == 1 || p.kind == 3) r.needs |= IncidentLog.ISOLATE;
        int cordon = Hazmat.CORDON[p.kind];
        if (cordon > 0) log.section(w, r, "cordon:" + r.id, "dangerous goods cordon", r.x, r.y, r.z, Math.max(r.r, cordon));
        if (Hazmat.evacuates(p.kind) && !r.evac) startEvac(w, log, r, "automatic (" + Hazmat.KIND[p.kind].toLowerCase(Locale.ROOT) + ")");
    }

    private static void progress(World w, IncidentLog log, IncidentLog.Rec r, long now) {
        // people in the area (most at once), passengers aboard trains in it
        int pax = 0;
        for (TrainNode t : TrainTracker.latest(w)) if (Math.hypot(t.x - r.x, t.z - r.z) <= r.r + 8) pax += t.passengers;
        for (EntityPlayer p : w.playerEntities) if (Math.hypot(p.posX - r.x, p.posZ - r.z) <= r.r) pax++;
        r.paxAffected = Math.max(r.paxAffected, pax);
        // keep the closed sections closed (Disruptions forgets them on restart)
        for (IncidentLog.Sec s : r.secs) if (!s.released) Disruptions.hold(w, s.key, "incident #" + r.id + " " + s.name, s.x, s.y, s.z, s.r);
        Rescue.watch(w, log, r);
        if (r.evac) evacTick(w, log, r, now);
        if (r.cleanUntil > 0 && now >= r.cleanUntil && r.stage.ordinal() < IncidentLog.Stage.RECOVERY.ordinal()) log.stage(w, r, IncidentLog.Stage.RECOVERY, "clean-up complete");
        // staged reopening
        boolean hazard = !hazardClear(w, r, now), contained = r.stage.ordinal() >= IncidentLog.Stage.CONTAINED.ordinal();
        IncidentLog.Sec next = nextSection(r, hazard, contained);
        if (next != null && IncidentConfig.autoReopen && r.commander.isEmpty()) {
            Long at = REOPEN_AT.get(w.provider.getDimension() + ":" + r.id);
            if (at == null) REOPEN_AT.put(w.provider.getDimension() + ":" + r.id, now + IncidentConfig.reopenStageSeconds * 20L);
            else if (now >= at) {
                log.release(w, r, next, "automatic stage");
                log.stage(w, r, IncidentLog.Stage.REOPENING, "staged reopening");
                REOPEN_AT.remove(w.provider.getDimension() + ":" + r.id);
            }
        }
        boolean allOpen = true;
        for (IncidentLog.Sec s : r.secs) allOpen &= s.released;
        if (allOpen && !hazard && r.disruptId < 0 && !r.evac && (r.rescue.isEmpty() || !r.coupled || r.stabled) && autoClose(r)) {
            log.stage(w, r, IncidentLog.Stage.REOPENING, "all sections open");
            log.stage(w, r, IncidentLog.Stage.CLOSED, "line open, incident closed");
            closedStats(w, r);
        }
    }

    /** incidents the system can close itself; ones a commander has taken over are closed at the desk */
    private static boolean autoClose(IncidentLog.Rec r) { return r.commander.isEmpty() || r.type == IncidentLog.Type.WRECK || r.type == IncidentLog.Type.FAILED && r.stabled; }

    static void closedStats(World w, IncidentLog.Rec r) {
        Analytics.count(w, "closed", 1);
        if (r.onScene > 0) { Analytics.count(w, "resp", (r.onScene - r.opened) / 20.0); Analytics.count(w, "respN", 1); }
        Analytics.count(w, "reopen", (w.getTotalWorldTime() - r.opened) / 20.0);
        Analytics.count(w, "reopenN", 1);
    }

    /** is the cause gone? (wreck cleared / failed train moved or repowered / fire out + cleaned / evacuation over) */
    static boolean hazardClear(World w, IncidentLog.Rec r, long now) {
        if (r.evac) return false;
        if (r.disruptId >= 0) return false;
        if (r.type == IncidentLog.Type.FAILED && r.disruptId == -1 && !r.stock.isEmpty() && !r.stabled) {     // a crew's assistance request: until the train is gone
            cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock f = Rescue.failed(w, r);
            if (f != null && Math.hypot(f.getPosition().x - r.x, f.getPosition().z - r.z) <= r.r) return false;
        }
        switch (r.type) {
            case FIRE: case HAZMAT: return r.contained > 0 && (r.cleanUntil == 0 || now >= r.cleanUntil);
            default: return r.cleanUntil == 0 || now >= r.cleanUntil;
        }
    }

    /** reopen order: adjacent lines (once contained), the cordon (once clear), the incident line / evacuation zone last */
    static IncidentLog.Sec nextSection(IncidentLog.Rec r, boolean hazard, boolean contained) {
        for (IncidentLog.Sec s : r.secs) if (!s.released && s.key.startsWith("adj:") && contained) return s;
        for (IncidentLog.Sec s : r.secs) if (!s.released && s.key.startsWith("cordon:") && !hazard) return s;
        for (IncidentLog.Sec s : r.secs) if (!s.released && !s.key.startsWith("adj:") && !s.key.startsWith("cordon:") && !hazard) return s;
        return null;
    }

    // ---- the commander's orders ----------------------------------------------------------------------------------
    public static final String[] ORDERS = {"accept", "level", "crew", "rescue", "fire", "medical", "protect", "isolate", "evac", "allclear", "reopen", "close"};

    public static String order(World w, int id, String what, String who) {
        IncidentLog log = IncidentLog.get(w);
        IncidentLog.Rec r = log.byId(id);
        if (r == null || !r.open()) return "§7No open incident #" + id;
        switch (what) {
            case "accept":
                if (!r.commander.isEmpty() && !r.commander.equals(who)) { log.log(w, r, "command handed from " + r.commander + " to " + who); }
                r.commander = who;
                log.stage(w, r, IncidentLog.Stage.ACKNOWLEDGED, "accepted by " + who);
                log.changed();
                return "§eYou have command of incident #" + r.id;
            case "level":
                r.level = r.level % 3 + 1;
                log.log(w, r, "LEVEL " + r.level + " declared by " + who);
                return "§eIncident #" + r.id + " is now level " + r.level;
            case "crew": {
                r.needs |= IncidentLog.CREW;
                for (net.minecraft.tileentity.TileEntity te : w.loadedTileEntityList)
                    if (te instanceof TileBreakdownDepot d && d.ready()) {
                        String s = IncidentHooks.manual(w, d);
                        log.log(w, r, "crew requested by " + who + ": " + s.replaceAll("§.", ""));
                        return s;
                    }
                log.log(w, r, "crew requested by " + who + " - no breakdown depot available, railway's own crew (" + com.dogpound.railmap.RailMapConfig.recoveryCrewSeconds + " s)");
                return "§7No Breakdown Crew Depot ready - the railway's own crew is used";
            }
            case "rescue": return Rescue.assign(w, log, r, who);
            case "fire": case "medical": {
                if (what.equals("fire") && r.has(IncidentLog.FIRE) && r.contained == 0 && (r.type == IncidentLog.Type.FIRE || r.type == IncidentLog.Type.HAZMAT)) {
                    fireOut(w, r, "fire service reports it out (" + who + ")");
                    return "§aFire out - clean-up " + Hazmat.cleanup(r.hazKind) + " s, then the line reopens";
                }
                int b = what.equals("fire") ? IncidentLog.FIRE : IncidentLog.MEDICAL;
                r.needs |= b; r.assigned |= b;
                log.log(w, r, (b == IncidentLog.FIRE ? "FIRE SERVICE" : "AMBULANCE") + " called by " + who);
                log.stage(w, r, IncidentLog.Stage.RESPONDING, "");
                return "§e" + (b == IncidentLog.FIRE ? "Fire service" : "Ambulance") + " called to incident #" + r.id;
            }
            case "protect": return protect(w, log, r, who);
            case "isolate": {
                int n = isolate(w, r.x, r.z, r.r, who);
                r.needs |= IncidentLog.ISOLATE;
                if (n > 0) r.assigned |= IncidentLog.ISOLATE;
                log.log(w, r, n == 0 ? "power isolation asked by " + who + ": no feeder breakers cover the site" : "TRACTION POWER ISOLATED by " + who + ": " + n + " breaker(s) opened");
                return n == 0 ? "§7No feeder breakers cover the site" : "§e" + n + " feeder breaker(s) opened - reclose them at the breakers when safe";
            }
            case "evac": return startEvac(w, log, r, who);
            case "allclear": return allClear(w, r, who);
            case "reopen": {
                IncidentLog.Sec s = nextSection(r, !hazardClear(w, r, w.getTotalWorldTime()), r.stage.ordinal() >= IncidentLog.Stage.CONTAINED.ordinal());
                if (s == null) return r.secs.stream().allMatch(x -> x.released) ? "§7Every section is already open" : "§cNot safe to reopen yet - the hazard is still there";
                log.release(w, r, s, "reopened by " + who);
                log.stage(w, r, IncidentLog.Stage.REOPENING, "staged reopening");
                return "§aReopened: " + s.name;
            }
            case "close": {
                if (!hazardClear(w, r, w.getTotalWorldTime())) return "§cCan't close - " + (r.evac ? "evacuation still on (ALL CLEAR first)" : r.disruptId >= 0 ? "the line is still blocked" : "the hazard is not cleared yet");
                log.stage(w, r, IncidentLog.Stage.REOPENING, "");
                log.stage(w, r, IncidentLog.Stage.CLOSED, "closed by " + who);
                closedStats(w, r);
                return "§aIncident #" + r.id + " closed";
            }
            default: return null;
        }
    }

    static String protect(World w, IncidentLog log, IncidentLog.Rec r, String who) {
        for (IncidentLog.Sec s : r.secs) if (s.key.startsWith("adj:") && !s.released) return "§7The adjacent lines are already protected";
        r.secs.removeIf(s -> s.key.equals("adj:" + r.id) && s.released);
        log.section(w, r, "adj:" + r.id, "adjacent lines", r.x, r.y, r.z, r.r + IncidentConfig.adjacentMargin);
        r.needs |= IncidentLog.PROTECT; r.assigned |= IncidentLog.PROTECT;
        log.log(w, r, "ADJACENT LINES PROTECTED by " + who);
        return "§eAdjacent lines blocked within " + (int) (r.r + IncidentConfig.adjacentMargin) + " m";
    }

    /** open the breakers of every traction feeder covering a spot (fire / power isolation); returns how many */
    public static int isolate(World w, double x, double z, double r, String who) {
        if (!IncidentConfig.fireIsolatesPower) return 0;
        GridData d = GridData.get(w);
        Set<Long> opened = new HashSet<>();
        List<Map.Entry<Long, GridData.Node>> feeders = new ArrayList<>();
        for (Map.Entry<Long, GridData.Node> e : d.nodes().entrySet()) if (e.getValue().kind == GridKind.FEEDER) feeders.add(e);
        for (Map.Entry<Long, GridData.Node> e : feeders) {
            BlockPos p = BlockPos.fromLong(e.getKey());
            if (Math.hypot(p.getX() - x, p.getZ() - z) > e.getValue().range + r) continue;
            GridData.Net net = d.netOf(p);
            if (net == null) continue;
            for (long nk : new ArrayList<>(net.nodes)) {
                GridData.Node b = d.nodes().get(nk);
                if (b == null || b.kind != GridKind.BREAKER || b.breaker != 0 || !opened.add(nk)) continue;
                b.breaker = 1;
                b.lastTrip = "isolated for an incident";
                History.log(w, BlockPos.fromLong(nk), "OPEN", "isolated for an incident by " + who);
            }
        }
        if (!opened.isEmpty()) {
            d.touch();
            AlarmManager.get(w).raise(w, "isolate:" + (long) x + "," + (long) z, AlarmManager.Cat.POWER, AlarmManager.Sev.MAJOR,
                    "Traction power ISOLATED for an incident at " + (int) x + "," + (int) z + " (" + opened.size() + " breaker(s) opened by " + who + ")", new BlockPos(x, 64, z), false);
        }
        return opened.size();
    }

    // ---- evacuation (#70) -----------------------------------------------------------------------------------------
    public static String startEvac(World w, IncidentLog log, IncidentLog.Rec r, String who) {
        if (r.evac) return "§7Evacuation already in progress";
        r.evac = true;
        r.needs |= IncidentLog.EVAC; r.assigned |= IncidentLog.EVAC;
        r.paxAccounted = 0;
        double radius = Math.max(r.r, 48);
        log.section(w, r, "evac:" + r.id, "evacuation zone", r.x, r.y, r.z, radius);
        log.log(w, r, "EVACUATION ordered by " + who + " - " + (int) radius + " m");
        r.plan.removeIf(s -> s.startsWith("EVAC"));
        // train-specific plans: every train in the zone
        cam72cam.mod.world.World umc = cam72cam.mod.world.World.get(w);
        Set<UUID> done = new HashSet<>();
        for (EntityMoveableRollingStock s : umc.getEntities(EntityMoveableRollingStock.class)) {
            Vec3d p = s.getPosition();
            if (Math.hypot(p.x - r.x, p.z - r.z) > radius || done.contains(s.getUUID())) continue;
            List<EntityRollingStock> cars = new ArrayList<>();
            if (s instanceof cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock c) cars.addAll(c.getTrain()); else cars.add(s);
            int pax = 0;
            boolean moving = Math.abs(s.getCurrentSpeed().metric()) > 1;
            for (EntityRollingStock c : cars) {
                done.add(c.getUUID());
                pax += c.getPassengerCount();
                if (IncidentConfig.evacReleaseDoors && !moving) { TrainDoors.move(w, c, TrainDoors.LEFT, true, 2f); TrainDoors.move(w, c, TrainDoors.RIGHT, true, 2f); }
            }
            String name = s.tag != null && !s.tag.isEmpty() ? s.tag : s.getDefinition() == null ? "train" : s.getDefinition().name();
            r.plan.add(String.format(Locale.ROOT, "EVAC %s: %d car(s), %d aboard, %s - %s", name, cars.size(), pax, moving ? "MOVING: stop at next safe place, no detraining" : "stopped",
                    moving ? "detrain at a platform" : IncidentConfig.evacReleaseDoors ? "doors released both sides, detrain to the walkway, away from the live rail" : "staff open the doors, detrain to the walkway"));
            r.paxAffected += pax;
        }
        Announcer.sayNear(w, r.x, r.z, radius + 64, "EVAC", IncidentConfig.tEvacuation);
        r.evacSaid = w.getTotalWorldTime();
        log.changed();
        return "§c⚠ Evacuation ordered for incident #" + r.id;
    }

    private static void evacTick(World w, IncidentLog log, IncidentLog.Rec r, long now) {
        if (now - r.evacSaid >= IncidentConfig.evacRepeatSeconds * 20L) {
            r.evacSaid = now;
            Announcer.sayNear(w, r.x, r.z, Math.max(r.r, 48) + 64, "EVAC", IncidentConfig.tEvacuation);
        }
    }

    public static String allClear(World w, IncidentLog.Rec r, String who) {
        if (!r.evac) return "§7No evacuation in progress";
        IncidentLog log = IncidentLog.get(w);
        r.evac = false;
        for (IncidentLog.Sec s : r.secs) if (s.key.equals("evac:" + r.id)) log.release(w, r, s, "all clear");
        log.log(w, r, "ALL CLEAR given by " + who + " - " + r.paxAccounted + " accounted for at the assembly points");
        Announcer.sayNear(w, r.x, r.z, Math.max(r.r, 48) + 64, "CLEAR", IncidentConfig.tAllClear);
        return "§aAll clear given";
    }

    /** the evacuation covering a spot (assembly points, exit signs), or null */
    public static IncidentLog.Rec evacAt(World w, BlockPos p, double extra) {
        for (IncidentLog.Rec r : IncidentLog.get(w).active())
            if (r.evac && Math.hypot(r.x - p.getX(), r.z - p.getZ()) <= Math.max(r.r, 48) + extra) return r;
        return null;
    }

    // ---- fire (#69) -------------------------------------------------------------------------------------------------
    /** a fire panel saw fire / smoke: the incident for it (opened, or the one already open there) */
    static IncidentLog.Rec fire(World w, BlockPos panel, double zone, String what, Hazmat.Placard haz) {
        IncidentLog log = IncidentLog.get(w);
        IncidentLog.Rec r = null;
        for (IncidentLog.Rec x : log.active()) if (x.firePanel == panel.toLong()) { r = x; break; }
        if (r == null) r = log.at(panel.getX() + 0.5, panel.getZ() + 0.5, zone);
        if (r == null) {
            r = log.open(w, haz.kind == 0 ? IncidentLog.Type.FIRE : IncidentLog.Type.HAZMAT, what, panel.getX() + 0.5, panel.getY(), panel.getZ() + 0.5, zone, haz.kind == 0 ? 2 : 3);
            Analytics.count(w, "fires", 1);
            Analytics.count(w, "incidents", 1);
            Announcer.sayNear(w, r.x, r.z, zone + 96, "EMERG", Announcer.fill(IncidentConfig.tEmergency, Collections.singletonMap("place", panel.getX() + "," + panel.getZ())));
        }
        r.firePanel = panel.toLong();
        r.needs |= IncidentLog.FIRE;
        r.contained = 0;
        r.cleanUntil = 0;
        log.section(w, r, "fire:" + r.id, "fire zone", panel.getX() + 0.5, panel.getY(), panel.getZ() + 0.5, zone);
        if (haz.kind != 0) hazmat(w, log, r, haz);
        log.changed();
        return r;
    }

    /** the fire is out: contained, clean-up timer by what burned */
    static void fireOut(World w, IncidentLog.Rec r, String how) {
        IncidentLog log = IncidentLog.get(w);
        long now = w.getTotalWorldTime();
        log.stage(w, r, IncidentLog.Stage.ON_SCENE, "");
        if (r.contained == 0) r.contained = now;
        log.stage(w, r, IncidentLog.Stage.CONTAINED, "fire out (" + how + ")");
        r.cleanUntil = now + Hazmat.cleanup(r.hazKind) * 20L;
        log.log(w, r, "clean-up " + Hazmat.cleanup(r.hazKind) + " s before the line reopens");
    }

    /** an Assembly Point counted people in: the evacuation's roll call */
    static void accounted(World w, IncidentLog.Rec r, int n, String at) {
        if (n > r.paxAccounted) {
            r.paxAccounted = n;
            IncidentLog.get(w).changed();
        }
    }

    public static void clear() { REOPEN_AT.clear(); STARTED.clear(); }
}
