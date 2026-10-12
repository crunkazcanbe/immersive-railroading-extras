package com.dogpound.railmap.crew;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.depot.Crew;
import com.dogpound.railmap.depot.DepotClock;
import com.dogpound.railmap.depot.DepotConfig;
import com.dogpound.railmap.depot.FleetData;
import com.dogpound.railmap.economy.PassengerData;
import com.dogpound.railmap.interior.Interior;
import com.dogpound.railmap.interior.InteriorServer;
import com.dogpound.railmap.server.StationData;
import com.dogpound.railmap.sound.PackSounds;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.event.world.WorldEvent;

import java.util.*;

/**
 * What each crew member is doing (batch 5 #7 + #9), worked out twice a second. Drivers (depot drivers: FleetData
 * driver + a Staff record), guards and inspectors sign on at a Crew Office when their shift diagram starts (if they
 * have had their rest), walk from the office to their train, drive / ride it, take breaks, sign off when the diagram
 * ends (once their train is standing) and walk back. A guard steps out at every stop, checks the doors (closes any
 * left open), blows the whistle and gives the right-away hand signal; a train with a guard on duty waits for that
 * (never past guardMaxSeconds). An inspector works platforms and rides a few stops on trains of their beat, checking
 * tickets ({@link Revenue}). Nothing here is saved but CrewData; the people you see (pax.CrewNpcs) only draw this.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class CrewTicker {
    private CrewTicker() {}

    private static final boolean IR = Loader.isModLoaded("immersiverailroading");
    public static final int OFF = 0, OFFICE = 1, WALK = 2, CAB = 3, ONBOARD = 4, PLATFORM = 5, PATROL = 6, BREAK = 7;
    public static final String[] PHASE = {"OFF DUTY", "SIGNING ON", "WALKING", "IN THE CAB", "ON THE TRAIN", "DISPATCHING", "ON PATROL", "ON A BREAK"};
    public static final int A_NONE = 0, A_SIGNAL = 1, A_WHISTLE = 2, A_CHECK = 3, A_LOOK = 4, A_TEA = 5;
    static final Random RND = new Random();

    /** live (unsaved) state of one crew member */
    public static final class Live {
        public int phase, after, action, step, stops;
        public long t0, t1, actionUntil, stepAt, dwellKey, rightAway, holdSince, fatigueKey;
        public double[] from, to, at;
        public UUID car, train, target;
        public long station;
        public double checkAcc, checked;
        public String doing = "";

        void walk(double[] a, double[] b, long now, int then) {
            from = a; to = b; t0 = now;
            double d = a == null || b == null ? 0 : Math.sqrt(sq(a[0] - b[0]) + sq(a[1] - b[1]) + sq(a[2] - b[2]));
            t1 = now + Math.max(20, Math.round(d / CrewConfig.walkSpeed * 20));
            phase = WALK; after = then; doing = "Walking";
        }

        public double frac(long now) { return t1 <= t0 ? 1 : Math.max(0, Math.min(1, (now - t0) / (double) (t1 - t0))); }
    }

    private static final Map<Integer, Map<String, Live>> LIVE = new HashMap<>();

    public static Map<String, Live> all(World w) { return LIVE.computeIfAbsent(w.provider.getDimension(), k -> new HashMap<>()); }
    public static Live live(World w, String name) { return all(w).computeIfAbsent(name, k -> new Live()); }

    @SubscribeEvent
    public static void unload(WorldEvent.Unload e) { if (!e.getWorld().isRemote) LIVE.remove(e.getWorld().provider.getDimension()); }

    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote || !IR || e.world.getTotalWorldTime() % 10 != 4) return;
        try { step(e.world, e.world.getTotalWorldTime()); }
        catch (RuntimeException | LinkageError ex) { RailMap.LOG.debug("[IR Extras] crew: {}", ex.toString()); }
    }

    // ---- where things are --------------------------------------------------------------------------------------------
    static double sq(double v) { return v * v; }
    public static EntityRollingStock car(World w, UUID id) { return id == null ? null : InteriorServer.byUuid(w, id); }
    static double[] pos(EntityRollingStock s) { cam72cam.mod.math.Vec3d p = s.getPosition(); return new double[]{p.x, p.y, p.z}; }
    static double[] blockPos(long l) { BlockPos b = BlockPos.fromLong(l); return new double[]{b.getX() + 0.5, b.getY(), b.getZ() + 0.5}; }

    /** where someone signing on at this office stands: in front of the desk */
    public static double[] officeSpot(World w, long office) {
        BlockPos b = BlockPos.fromLong(office);
        TileEntity t = w.isBlockLoaded(b) ? w.getTileEntity(b) : null;
        EnumFacing f = t instanceof com.dogpound.railmap.controls.IControlled c ? c.controlFacing() : EnumFacing.NORTH;
        return new double[]{b.getX() + 0.5 + f.getXOffset() * 1.2, b.getY(), b.getZ() + 0.5 + f.getZOffset() * 1.2};
    }

    /** the Crew Office this person uses: their own, else the nearest to `near` (0 = none placed) */
    public static long office(CrewData c, CrewData.Staff s, double[] near) {
        if (s.office != 0 && c.offices.contains(s.office)) return s.office;
        long best = 0; double bd = Double.MAX_VALUE;
        for (long o : c.offices) {
            double[] p = blockPos(o);
            double d = near == null ? 0 : sq(p[0] - near[0]) + sq(p[2] - near[2]);
            if (d < bd) { bd = d; best = o; }
        }
        return best;
    }

    /** the guard's end of a train: the car furthest from the loco */
    static EntityRollingStock rear(Locomotive l) {
        EntityRollingStock best = l; double bd = -1;
        double[] lp = pos(l);
        for (EntityCoupleableRollingStock s : l.getTrain()) { double[] p = pos(s); double d = sq(p[0] - lp[0]) + sq(p[2] - lp[2]); if (d > bd) { bd = d; best = s; } }
        return best;
    }

    static EntityRollingStock coach(Locomotive l) {
        List<EntityRollingStock> pc = new ArrayList<>();
        for (EntityCoupleableRollingStock s : l.getTrain()) if (s.getDefinition() != null && s.getDefinition().acceptsPassengers()) pc.add(s);
        return pc.isEmpty() ? l : pc.get(RND.nextInt(pc.size()));
    }

    /** players inside any car of a train */
    public static List<EntityPlayer> riders(World w, Locomotive l) {
        List<EntityPlayer> out = new ArrayList<>();
        for (EntityCoupleableRollingStock s : l.getTrain()) {
            if (s.getDefinition() == null) continue;
            double half = s.getDefinition().getLength(s.gauge) / 2 + 0.5, hw = s.getDefinition().getWidth(s.gauge) / 2 + 0.5;
            for (EntityPlayer p : w.playerEntities) {
                if (out.contains(p) || p.getDistanceSq(s.getPosition().x, s.getPosition().y, s.getPosition().z) > (half + 4) * (half + 4)) continue;
                double[] lo = Interior.toLocal(s, p.posX, p.posY, p.posZ);
                if (Math.abs(lo[2]) <= half && Math.abs(lo[0]) <= hw && lo[1] > -1 && lo[1] < 5) out.add(p);
            }
        }
        return out;
    }

    /** a person's position now (null = nowhere to be seen) */
    public static double[] where(World w, CrewData c, CrewData.Staff s, Live L, long now) {
        switch (L.phase) {
            case WALK -> {
                if (L.from == null || L.to == null) return L.to;
                double f = L.frac(now);
                return new double[]{L.from[0] + (L.to[0] - L.from[0]) * f, L.from[1] + (L.to[1] - L.from[1]) * f, L.from[2] + (L.to[2] - L.from[2]) * f};
            }
            case CAB, ONBOARD, BREAK -> { EntityRollingStock s0 = car(w, L.car); return s0 == null ? L.at : pos(s0); }
            case OFFICE -> { long o = office(c, s, L.at); return o == 0 ? L.at : officeSpot(w, o); }
            case PLATFORM, PATROL -> { return L.to != null ? L.to : L.station != 0 ? blockPos(L.station) : L.at; }
            default -> { return null; }
        }
    }

    // ---- fatigue ---------------------------------------------------------------------------------------------------------
    public static double fatigue(World w, CrewData.Staff s) {
        long now = w.getTotalWorldTime();
        long start; double duty;
        if (s.role == CrewData.DRIVER) {
            FleetData.Driver d = FleetData.get(w).drivers.get(s.name);
            if (d == null || !d.onDuty) return 0;
            start = d.dutyStart; duty = Crew.dutyHours(w, d);
        } else {
            if (!s.onDuty) return 0;
            start = s.dutyStart; duty = DepotClock.hours(start, now);
        }
        double restShort = s.lastOff <= 0 ? 0 : Math.max(0, CrewConfig.restHours - DepotClock.hours(s.lastOff, start));
        return Rules.fatigue(duty, DepotConfig.dutyMaxHours, restShort, s.tea);
    }

    // ---- departure (Autopilot asks via DepotHolds when a dwell is up) ----------------------------------------------------
    /** null = the crew are happy for this train to go */
    public static String departure(World w, RailwayData.AutoTrain a, Locomotive l) {
        CrewData c = CrewData.get(w);
        if (c.staff.isEmpty()) return null;
        long now = w.getTotalWorldTime();
        FleetData f = FleetData.get(w);
        FleetData.Driver d = Crew.driverOf(f, a.loco);
        Autopilot.Run r = Autopilot.run(a.loco);
        long key = r == null ? 0 : r.dwellStation;
        if (d != null && d.player == null && d.onDuty) {
            CrewData.Staff s = c.staff.get(d.name);
            Live L = live(w, d.name);
            if (CrewConfig.crewRequired && !a.line.isEmpty() && f.attended.contains(a.line) && L.phase != CAB && L.phase != BREAK)
                return "DRIVER " + d.name.toUpperCase(Locale.ROOT) + " WALKING TO THE CAB";
            if (s != null && CrewConfig.fatigueEffects && L.fatigueKey != key) {
                L.fatigueKey = key;
                double fa = fatigue(w, s);
                if (fa > 0.8) {
                    int late = 6 + (int) (fa * 12);
                    boolean mistake = RND.nextDouble() < (fa - 0.8) * 0.3;
                    if (mistake) {
                        late += 20;
                        s.mistakes++;
                        c.markDirty();
                        String what = RND.nextBoolean() ? "overran the stop mark and had to set back" : "missed the starting signal clearing";
                        f.crewNote(w, d.name + " (TIRED) " + what);
                        Recorder.event(w, "Crew fatigue: " + d.name + " " + what + " - " + f.unit(a.loco).name);
                    }
                    Autopilot.adjustDwell(a.loco, now, late);
                    return "DRIVER TIRED - SLOW TO START";
                }
            }
        }
        CrewData.Staff g = c.byRole(CrewData.GUARD, a.loco);
        if (g != null && g.onDuty && CrewConfig.guardRightAway) {
            Live L = live(w, g.name);
            if (L.rightAway == 0 || L.dwellKey != key) {
                if (L.holdSince == 0) L.holdSince = now;
                if (now - L.holdSince < CrewConfig.guardMaxSeconds * 20L) return L.phase == PLATFORM ? "GUARD: " + L.doing.toUpperCase(Locale.ROOT) : "WAITING FOR THE GUARD";
            }
        }
        return null;
    }

    // ---- the work ----------------------------------------------------------------------------------------------------------
    private static void step(World w, long now) {
        CrewData c = CrewData.get(w);
        FleetData f = FleetData.get(w);
        for (FleetData.Driver d : f.drivers.values())                              // depot drivers hired at the old desk get a uniform too
            if (d.player == null && !c.staff.containsKey(d.name)) { CrewData.Staff s = new CrewData.Staff(); s.name = d.name; s.role = CrewData.DRIVER; s.skin = RND.nextInt(48); s.slim = RND.nextBoolean(); c.staff.put(s.name, s); c.markDirty(); }
        c.rollDay(now / 24000);
        if (c.staff.isEmpty()) return;
        int hour = DepotClock.hour(w);
        Map<String, Live> lv = all(w);
        lv.keySet().retainAll(c.staff.keySet());
        AlarmManager am = AlarmManager.get(w);
        for (CrewData.Staff s : new ArrayList<>(c.staff.values())) {
            Live L = live(w, s.name);
            if (L.action != A_NONE && now > L.actionUntil) { L.action = A_NONE; L.target = null; }
            if (s.tea > 0) s.tea = Math.max(0, s.tea - 10 / 4000.0);
            switch (s.role) {
                case CrewData.DRIVER -> driver(w, c, f, s, L, hour, now);
                case CrewData.GUARD -> guard(w, c, f, s, L, hour, now);
                default -> inspector(w, c, f, s, L, hour, now);
            }
            double[] at = where(w, c, s, L, now);
            if (at != null) L.at = at;
            double fa = fatigue(w, s);
            if (fa > 1.0) am.raise(w, "crewfat:" + s.name, AlarmManager.Cat.GENERAL, AlarmManager.Sev.INFO, "CREW: " + s.name + " (" + CrewData.ROLE[s.role] + ") FATIGUED - relieve at the next stop", null, true);
            else am.clear("crewfat:" + s.name);
        }
    }

    /** roster sign-on for guards and inspectors (drivers sign on through FleetData) */
    private static boolean signOn(World w, CrewData c, FleetData f, CrewData.Staff s, Live L, long now) {
        if (s.onDuty) return false;
        s.onDuty = true; s.dutyStart = now; s.breakUntil = 0;
        c.markDirty();
        f.crewNote(w, s.name + " (" + CrewData.ROLE[s.role].toLowerCase(Locale.ROOT) + ") SIGNED ON");
        atOffice(c, s, L, now);
        return true;
    }

    static void signOff(World w, CrewData c, FleetData f, CrewData.Staff s, Live L, long now) {
        if (!s.onDuty) return;
        s.onDuty = false; s.lastOff = now;
        c.markDirty();
        f.crewNote(w, String.format(Locale.ROOT, "%s (%s) SIGNED OFF after %.1f h", s.name, CrewData.ROLE[s.role].toLowerCase(Locale.ROOT), DepotClock.hours(s.dutyStart, now)));
        goHome(w, c, s, L, now);
    }

    private static void atOffice(CrewData c, CrewData.Staff s, Live L, long now) {
        L.phase = OFFICE; L.t1 = now + 300; L.doing = "Signing on - kettle on"; L.action = A_TEA; L.actionUntil = now + 200; L.car = null;
        L.dwellKey = 0; L.rightAway = 0; L.holdSince = 0; L.stops = 0; L.checked = 0;
    }

    private static void goHome(World w, CrewData c, CrewData.Staff s, Live L, long now) {
        long o = office(c, s, L.at);
        if (o == 0 || L.at == null) { L.phase = OFF; return; }
        L.walk(L.at, officeSpot(w, o), now, OFF);
        L.doing = "Walking back to sign off";
        L.car = null;
    }

    /** walk from wherever they are to a car (or straight aboard when there's nowhere to walk from) */
    private static void boardCar(World w, Live L, EntityRollingStock car, int then, long now) {
        L.car = car.getUUID();
        if (L.at == null) { L.phase = then; return; }
        L.walk(L.at, pos(car), now, then);
        L.doing = "Walking to " + (then == CAB ? "the cab" : "the train");
    }

    private static void driver(World w, CrewData c, FleetData f, CrewData.Staff s, Live L, int hour, long now) {
        FleetData.Driver d = f.drivers.get(s.name);
        if (d == null || d.player != null) { L.phase = OFF; return; }
        if (c.autoRoster && s.start >= 0) {
            boolean in = Rules.inShift(hour, s.start, s.hours);
            if (!d.onDuty && in && Rules.rested(now, s.lastOff, CrewConfig.restHours)) {
                Crew.signOn(w, f, d);
                if (s.train != null && d.train == null) Crew.assign(w, f, d, s.train, "start of diagram");
            } else if (d.onDuty && !in && standing(d.train)) {
                Crew.signOff(w, f, d);
                s.lastOff = now; c.markDirty();
                goHome(w, c, s, L, now);
                return;
            }
            if (d.onDuty && !Crew.onBreak(w, d) && Crew.sinceBreak(w, d) >= DepotConfig.breakAfterHours && dwelling(d.train)) Crew.takeBreak(w, f, d);
        }
        if (!d.onDuty) {
            if (L.phase != OFF && !(L.phase == WALK && L.after == OFF)) goHome(w, c, s, L, now);
            if (L.phase == WALK && now >= L.t1) L.phase = OFF;
            return;
        }
        if (L.phase == OFF) atOffice(c, s, L, now);
        Locomotive loco = d.train == null ? null : Autopilot.find(w, d.train);
        switch (L.phase) {
            case OFFICE -> {
                if (office(c, s, L.at) == 0 && loco != null) { L.car = loco.getUUID(); L.phase = CAB; break; }
                if (now >= L.t1) { if (loco != null) boardCar(w, L, loco, CAB, now); else L.doing = d.train == null ? "Spare - waiting for a train" : "Waiting for " + f.unit(d.train).name; }
            }
            case WALK -> { if (now >= L.t1) { L.phase = L.after; L.doing = ""; } }
            case CAB, BREAK -> {
                if (loco == null) { if (d.train == null) atOffice(c, s, L, now); break; }
                if (!loco.getUUID().equals(L.car)) { boardCar(w, L, loco, CAB, now); break; }
                boolean brk = Crew.onBreak(w, d);
                L.phase = brk ? BREAK : CAB;
                L.doing = brk ? "On a break" : dwelling(d.train) ? "Station stop" : "Driving " + f.unit(d.train).name;
            }
            default -> L.phase = OFFICE;
        }
    }

    private static boolean dwelling(UUID loco) { Autopilot.Run r = loco == null ? null : Autopilot.run(loco); return r != null && r.dwelling; }
    private static boolean standing(UUID loco) { Autopilot.Run r = loco == null ? null : Autopilot.run(loco); return r == null || r.dwelling || r.idle; }

    private static void roster(World w, CrewData c, FleetData f, CrewData.Staff s, Live L, int hour, long now) {
        if (!c.autoRoster || s.start < 0) return;
        boolean in = Rules.inShift(hour, s.start, s.hours);
        if (!s.onDuty && in && Rules.rested(now, s.lastOff, CrewConfig.restHours)) signOn(w, c, f, s, L, now);
        else if (s.onDuty && !in && (L.phase == OFFICE || L.phase == PATROL || L.phase == WALK || standing(L.train) && L.phase != PLATFORM)) signOff(w, c, f, s, L, now);
    }

    private static void guard(World w, CrewData c, FleetData f, CrewData.Staff s, Live L, int hour, long now) {
        roster(w, c, f, s, L, hour, now);
        if (!s.onDuty) { if (L.phase == WALK && now >= L.t1) L.phase = OFF; else if (L.phase != WALK) L.phase = OFF; return; }
        if (L.phase == OFF) atOffice(c, s, L, now);
        L.train = s.train;
        Locomotive loco = s.train == null ? null : Autopilot.find(w, s.train);
        Autopilot.Run r = s.train == null ? null : Autopilot.run(s.train);
        switch (L.phase) {
            case OFFICE -> {
                if (loco == null) { L.doing = s.train == null ? "Spare guard" : "Waiting for " + f.unit(s.train).name; break; }
                if (office(c, s, L.at) == 0) { L.car = rear(loco).getUUID(); L.phase = ONBOARD; break; }
                if (now >= L.t1) boardCar(w, L, rear(loco), ONBOARD, now);
            }
            case WALK -> { if (now >= L.t1) { L.phase = L.after; L.doing = ""; } }
            case ONBOARD -> {
                if (loco == null) { atOffice(c, s, L, now); break; }
                EntityRollingStock rc = rear(loco);
                L.car = rc.getUUID();
                if (r != null && r.dwelling && L.dwellKey != r.dwellStation) {             // a new stop: step out on to the platform
                    L.dwellKey = r.dwellStation; L.station = r.dwellStation; L.step = 0; L.rightAway = 0; L.holdSince = 0;
                    double[] spot = com.dogpound.railmap.pax.CrewNpcs.platformSpot(w, r.dwellStation, s.train, pos(rc));
                    L.to = spot != null ? spot : pos(rc);
                    L.phase = PLATFORM; L.doing = "Door check"; L.action = A_LOOK; L.actionUntil = now + 400;
                } else if (r == null || !r.dwelling) {
                    if (r != null && L.dwellKey != 0 && !r.dwelling) { L.dwellKey = 0; L.rightAway = 0; L.holdSince = 0; }
                    L.doing = "Riding in the rear cab";
                }
            }
            case PLATFORM -> dispatch(w, f, s, L, loco, r, now);
            default -> L.phase = OFFICE;
        }
    }

    /** door check -> doors closed -> whistle -> right-away hand signal -> board */
    private static void dispatch(World w, FleetData f, CrewData.Staff s, Live L, Locomotive loco, Autopilot.Run r, long now) {
        if (loco == null || r == null || !r.dwelling) { L.phase = ONBOARD; L.action = A_NONE; return; }
        long left = Autopilot.dwellLeft(s.train, now);
        switch (L.step) {
            case 0 -> {
                L.doing = "Door check";
                if (left <= 100) {
                    for (EntityCoupleableRollingStock car : loco.getTrain())
                        if (com.dogpound.railmap.doors.TrainDoors.anyOpen(car)) {
                            com.dogpound.railmap.doors.TrainDoors.move(w, car, com.dogpound.railmap.doors.TrainDoors.LEFT, false, 2f);
                            com.dogpound.railmap.doors.TrainDoors.move(w, car, com.dogpound.railmap.doors.TrainDoors.RIGHT, false, 2f);
                        }
                    L.step = 1; L.stepAt = now; L.doing = "Doors closing";
                }
            }
            case 1 -> {
                boolean open = false;
                for (EntityCoupleableRollingStock car : loco.getTrain()) open |= com.dogpound.railmap.doors.TrainDoors.anyOpen(car);
                if (now - L.stepAt >= 40 && (!open || now - L.stepAt >= 80)) {
                    L.step = 2; L.stepAt = now; L.doing = "Whistle"; L.action = A_WHISTLE; L.actionUntil = now + 30;
                    if (L.to != null) PackSounds.play(w, L.to[0], L.to[1] + 1.6, L.to[2], "cab.whistle", 1.0f, 1.9f);
                }
            }
            case 2 -> {
                if (now - L.stepAt >= 20) {
                    L.step = 3; L.stepAt = now; L.doing = "Right away"; L.action = A_SIGNAL; L.actionUntil = now + 40;
                    if (L.to != null) PackSounds.play(w, L.to[0], L.to[1] + 1.6, L.to[2], "cab.bell", 0.8f, 1.2f);
                }
            }
            default -> {
                if (now - L.stepAt >= 20) {
                    L.rightAway = now; L.phase = ONBOARD; L.doing = "Aboard - right away given";
                    f.crewNote(w, s.name + " (guard) right away " + f.unit(s.train).name + " at " + StationData.get(w).names().getOrDefault(r.dwellStation, "?"));
                }
            }
        }
    }

    private static void inspector(World w, CrewData c, FleetData f, CrewData.Staff s, Live L, int hour, long now) {
        roster(w, c, f, s, L, hour, now);
        if (!s.onDuty) { if (L.phase == WALK && now >= L.t1) L.phase = OFF; else if (L.phase != WALK) L.phase = OFF; return; }
        if (L.phase == OFF) atOffice(c, s, L, now);
        RailwayData rd = RailwayData.get(w);
        switch (L.phase) {
            case OFFICE -> {
                if (now < L.t1) break;
                long st = nearestStation(w, s.line, L.at);
                if (st == 0) { L.doing = "No station on the beat"; break; }
                L.station = st;
                if (L.at == null) { L.phase = PATROL; L.t1 = now + 1800; L.to = null; break; }
                L.walk(L.at, blockPos(st), now, PATROL);
                L.doing = "Walking to " + StationData.get(w).names().getOrDefault(st, "the station");
            }
            case WALK -> { if (now >= L.t1) { L.phase = L.after; L.t1 = now + 1800; L.checked = 0; L.to = null; } }
            case PATROL -> {
                PassengerData.Stn stn = PassengerData.get(w).stn(L.station);
                String name = StationData.get(w).names().getOrDefault(L.station, "station");
                L.doing = "Checking tickets at " + name;
                if (stn != null) Revenue.checkNumbers(w, c, s, L, stn.waitingTotal(), stn.gates, null, s.line, name, 0.5);
                BlockPos sp = BlockPos.fromLong(L.station);
                for (EntityPlayer p : w.playerEntities)
                    if (Math.abs(p.posX - sp.getX()) < 14 && Math.abs(p.posZ - sp.getZ()) < 14 && Math.abs(p.posY - sp.getY()) < 5 && p.getRidingEntity() == null
                            && com.dogpound.railmap.pax.CrewNpcs.onPlatform(w, L.station, p.posX, p.posY, p.posZ) && Revenue.checkPlayer(w, c, s, L, p, L.station, "", null)) break;
                if (now >= L.t1) {                                                    // ride a train of the beat for a few stops
                    for (RailwayData.AutoTrain a : rd.trains().values()) {
                        Autopilot.Run r = Autopilot.run(a.loco);
                        if (r == null || !r.dwelling || r.dwellStation != L.station || !s.line.isEmpty() && !s.line.equals(a.line)) continue;
                        Locomotive l = Autopilot.find(w, a.loco);
                        if (l == null) continue;
                        L.train = a.loco; L.stops = 0; L.dwellKey = L.station; L.checked = 0;
                        boardCar(w, L, coach(l), ONBOARD, now);
                        f.crewNote(w, s.name + " (inspector) boards " + f.unit(a.loco).name + " at " + name);
                        break;
                    }
                    if (L.phase == PATROL) L.t1 = now + 400;
                }
            }
            case ONBOARD -> {
                Locomotive l = L.train == null ? null : Autopilot.find(w, L.train);
                RailwayData.AutoTrain a = L.train == null ? null : rd.train(L.train);
                if (l == null || a == null) { L.phase = PATROL; L.t1 = now + 1200; L.station = nearestStation(w, s.line, L.at); L.to = null; break; }
                Autopilot.Run r = Autopilot.run(L.train);
                long last = r == null ? 0 : r.dwellStation;
                PassengerData.Stn stn = PassengerData.get(w).stn(last);
                L.station = last;
                L.doing = "Checking tickets on " + f.unit(L.train).name;
                Revenue.checkNumbers(w, c, s, L, PassengerData.get(w).aboard(L.train), stn == null ? 0 : stn.gates, L.train, a.line, f.unit(L.train).name + " (" + a.line + ")", 0.5);
                for (EntityPlayer p : riders(w, l)) if (Revenue.checkPlayer(w, c, s, L, p, last, a.line, L.train)) break;
                if (r != null && r.dwelling && r.dwellStation != L.dwellKey) {              // a new stop
                    L.dwellKey = r.dwellStation; L.stops++; L.checked = 0;
                    if (L.stops >= 2 + RND.nextInt(3)) {
                        L.phase = PATROL; L.t1 = now + 1800; L.to = null; L.station = r.dwellStation; L.car = null;
                        f.crewNote(w, s.name + " (inspector) alights at " + StationData.get(w).names().getOrDefault(r.dwellStation, "?"));
                    }
                }
            }
            default -> L.phase = OFFICE;
        }
    }

    /** a station on the beat (or any) nearest to a spot */
    static long nearestStation(World w, String line, double[] at) {
        RailwayData.Line l = line.isEmpty() ? null : RailwayData.get(w).line(line);
        Collection<Long> cand = l != null ? l.stations : StationData.get(w).names().keySet();
        long best = 0; double bd = Double.MAX_VALUE;
        for (long st : cand) {
            double[] p = blockPos(st);
            double d = at == null ? 0 : sq(p[0] - at[0]) + sq(p[2] - at[2]);
            if (d < bd) { bd = d; best = st; }
        }
        return best;
    }

    // ---- hiring -----------------------------------------------------------------------------------------------------------
    /** hire a new NPC crew member of this role (a driver also joins the depot roster, passed on diesel) */
    public static CrewData.Staff hire(World w, int role, String name) {
        CrewData c = CrewData.get(w);
        FleetData f = FleetData.get(w);
        if (name == null || name.isEmpty()) {
            String base = role == CrewData.DRIVER ? "Driver " : role == CrewData.GUARD ? "Guard " : "Inspector ";
            int n = 1;
            while (c.staff.containsKey(base + n) || f.drivers.containsKey(base + n)) n++;
            name = base + n;
        }
        if (c.staff.containsKey(name)) return c.staff.get(name);
        CrewData.Staff s = new CrewData.Staff();
        s.name = name; s.role = role; s.skin = RND.nextInt(48); s.slim = RND.nextBoolean();
        c.staff.put(name, s);
        if (role == CrewData.DRIVER && !f.drivers.containsKey(name)) {
            FleetData.Driver d = new FleetData.Driver();
            d.name = name;
            f.drivers.put(name, d);
            f.markDirty();
        }
        f.crewNote(w, name + " hired (" + CrewData.ROLE[role].toLowerCase(Locale.ROOT) + ")");
        c.markDirty();
        return s;
    }

    /** let a crew member go (a driver leaves the depot roster too) */
    public static String dismiss(World w, String name) {
        CrewData c = CrewData.get(w);
        CrewData.Staff s = c.staff.remove(name);
        if (s == null) return "§7No such crew member";
        FleetData f = FleetData.get(w);
        FleetData.Driver d = f.drivers.get(name);
        if (s.role == CrewData.DRIVER && d != null && d.player == null) { f.drivers.remove(name); f.markDirty(); }
        all(w).remove(name);
        f.crewNote(w, name + " left the railway");
        c.markDirty();
        return "§e" + name + " has left";
    }
}
