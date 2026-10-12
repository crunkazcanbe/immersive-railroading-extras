package com.dogpound.railmap.planning;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.library.SwitchState;
import cam72cam.immersiverailroading.tile.TileRail;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import com.dogpound.railmap.RailMapConfig;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.Dispatcher;
import com.dogpound.railmap.auto.Interlocking;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.auto.TrackPath;
import com.dogpound.railmap.signal.TrackFollower;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Automatic turnback panel (her ChatGPT batch 3 #5) at a terminus or a reversing siding. A driverless train that has
 * to reverse here (its next stop lies back the way it came - checked on the real track - or the autopilot asks to
 * change direction near the panel) is turned round properly instead of just throwing the reverser:
 * <ol>
 *   <li>CHANGING ENDS: the driver shuts the cab down, walks the train (length / walking speed) and opens the other cab
 *   (PlanConfig.turnbackCabSeconds) - the train can't leave before that;</li>
 *   <li>CAB: if there is a loco or driving trailer at the other end the train's identity moves to it and the train is
 *   driven from there; with none, MODE PUSH-PULL lets the loco propel it, MODE CAB END ONLY refuses (FAULT: it needs
 *   a run-round, a loco changeover or a turntable);</li>
 *   <li>ROUTE: the way to the next stop is found from the new front end on the real track and its first points are
 *   claimed for the train in the interlocking - no route, no departure;</li>
 *   <li>BRAKE: a continuity test from the new driving cab (key BYPASS skips it);</li>
 *   <li>RIGHT AWAY. Turnarounds longer than the PLANNED time count as LATE.</li>
 * </ol>
 */
public class TileTurnbackPanel extends OpsGear {
    static final String[] STAGE = {"IDLE", "ARRIVED", "CHANGING ENDS", "ROUTE CHECK", "BRAKE TEST", "RIGHT AWAY", "", "", "", "FAULT"};
    static final int[] PLAN_MIN = {2, 3, 5, 8};

    private int stage;
    private UUID train, newLead;
    private long arriveAt, stageAt;
    private double turnS, lenM;
    private int turnbacks, late, faults, routeTries;
    private boolean propel, routeOk, routeAlarm;
    private String fault = "", brakeTxt = "-", trainName = "-", cabName = "-", routeTxt = "-";
    private final Map<UUID, Long> cleared = new HashMap<>(), checked = new HashMap<>();

    public String id() { return "TB " + Math.floorMod(pos.getX() * 19 + pos.getZ() * 23, 1000); }
    @Override public String wiredName() { return "Turnback panel " + id(); }
    private boolean cabOnly() { return ctl.get("s_mode") == 1; }
    private long planTicks() { return PLAN_MIN[Math.max(0, Math.min(3, ctl.get("s_plan")))] * 1200L; }
    private boolean active() { return stage >= 1 && stage <= 4; }

    @Override public String gearState() { return !powered() ? "NO 12 V" : STAGE[stage] + (active() ? " · " + trainName : "") + (stage == 9 ? " · " + fault : ""); }
    @Override public int wiredLamp() { return !powered() || stage == 9 ? 3 : routeAlarm ? 2 : 1; }

    @Override
    protected String hold(RailwayData.AutoTrain a, Locomotive loco, long now) {
        if (!a.loco.equals(train)) return null;
        if (active()) return "turnback: " + STAGE[stage] + (stage == 2 ? " (" + clock(Math.max(0, (long) (turnS * 20) - (now - arriveAt))) + ")" : stage == 3 ? " - " + routeTxt : "");
        if (stage == 9) return "turnback FAULT: " + fault;
        return null;
    }

    /** the autopilot is about to reverse this train: true = not yet (the panel turns it round first) */
    boolean veto(RailwayData.AutoTrain a, Locomotive loco, long now) {
        if (!near(loco.getPosition(), range())) return false;
        Long c = cleared.get(a.loco);
        if (c != null && now - c < 2400 || recentlyTurned(a.loco, now)) return false;
        if (a.loco.equals(train) && (active() || stage == 9)) return true;
        if (!powered() || ctl.get("s_on") == 0) return false;          // fail safe: nothing new starts without 12 V
        if (stage == 0 || stage == 5) { start(a, loco, now); return true; }
        return true;                                                     // busy with another train: one turnback at a time
    }

    private void start(RailwayData.AutoTrain a, Locomotive l, long now) {
        train = a.loco; trainName = Autopilot.label(a, l); arriveAt = now; newLead = null; cabName = "-"; routeTxt = "-"; brakeTxt = "-";
        propel = routeOk = routeAlarm = false; routeTries = 0;
        Recorder.event(world, "TURNBACK " + id() + ": " + trainName + " arrived to reverse");
        go(1);
    }

    private void go(int s) { stage = s; stageAt = world.getTotalWorldTime(); markDirty(); }

    private void fault(String why) {
        fault = why; faults++;
        AlarmManager.get(world).raise(world, "tb:" + pos.toLong(), AlarmManager.Cat.AUTO, AlarmManager.Sev.MAJOR, "TURNBACK " + id() + ": " + why, pos, true);
        Recorder.event(world, "TURNBACK " + id() + " FAULT: " + why);
        sound("alarm.annunciator", 0.6f);
        go(9);
    }

    /** the next stop after the one the train is standing at (Dispatcher.advance on a scratch copy) */
    private Long stopAfter(RailwayData.AutoTrain a) {
        RailwayData d = RailwayData.get(world);
        Long here = Dispatcher.nextStop(d, a);
        if (here == null) return null;
        int idx = a.index, dir = a.direction;
        List<Long> calls = new ArrayList<>(a.calls);
        Dispatcher.advance(d, a, here);
        Long next = Dispatcher.nextStop(d, a);
        a.index = idx; a.direction = dir; a.calls.clear(); a.calls.addAll(calls);
        return next;
    }

    /** the way from this loco to a stop, setting off along (+1) or against (-1) its nose; null = none */
    private TrackPath path(Locomotive l, int s, long stop) {
        try {
            cam72cam.mod.world.World umc = cam72cam.mod.world.World.get(world);
            Vec3d p = l.getPosition();
            TileRail rail = TrackFollower.railUnder(umc, p.x, p.y, p.z);
            if (rail == null) return null;
            double yaw = Math.toRadians(l.getRotationYaw()), fx = -Math.sin(yaw), fz = Math.cos(yaw);
            BlockPos sp = BlockPos.fromLong(stop);
            Vec3i from = TrackPath.behind(rail, p.x, p.y, p.z, fx * s, fz * s);
            return TrackPath.find(umc, rail, from, RailMapConfig.routeSearchBlocks, TrackPath.near(sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5, 3.0));
        } catch (LinkageError | RuntimeException e) { return null; }
    }

    /** does this dwelling train have to reverse to reach its next stop? */
    private boolean mustReverse(RailwayData.AutoTrain a, Locomotive l) {
        Long next = stopAfter(a);
        if (next == null) return false;
        int dir = l.getReverser() < -0.05f ? -1 : 1;
        return path(l, dir, next) == null && path(l, -dir, next) != null;
    }

    @Override
    protected void tickGear() {
        if (age % 20 != 0) return;
        long now = world.getTotalWorldTime();
        cleared.values().removeIf(t -> now - t > 2400);
        checked.values().removeIf(t -> now - t > 6000);
        Locomotive lead = loco(train);
        RailwayData.AutoTrain a = train == null ? null : RailwayData.get(world).train(train);
        switch (stage) {
            case 0, 5 -> {
                if (stage == 5 && now - stageAt < 200) break;
                if (stage == 5) go(0);
                if (!powered() || ctl.get("s_on") == 0) break;
                for (RailwayData.AutoTrain t : dwellingHere()) {
                    if (cleared.containsKey(t.loco) || checked.containsKey(t.loco)) continue;
                    Locomotive l = loco(t.loco);
                    if (l == null) continue;
                    checked.put(t.loco, now);                                  // one track search per arrival
                    if (mustReverse(t, l)) { start(t, l, now); break; }
                }
            }
            case 1 -> {
                if (lead == null || a == null) { go(0); break; }
                List<EntityCoupleableRollingStock> o = SplitJoin.ordered(lead);
                if (o == null) { fault("the driving loco is in mid-train"); break; }
                lenM = SplitJoin.lengthM(o);
                turnS = turnSeconds(lenM, PlanConfig.turnbackCabSeconds, PlanConfig.turnbackWalkMs);
                EntityCoupleableRollingStock far = o.get(o.size() - 1);
                if (o.size() > 1 && far instanceof Locomotive fl) { newLead = fl.getUUID(); propel = false; cabName = nm(fl); }
                else if (o.size() == 1) { newLead = train; propel = false; cabName = "SAME (LIGHT ENGINE)"; turnS = PlanConfig.turnbackCabSeconds; }
                else if (cabOnly()) { fault("no cab at the other end - needs a run-round, loco changeover or turntable"); break; }
                else { newLead = train; propel = true; cabName = "PROPELLING"; }
                sound("cab.brake_apply", 0.5f);
                go(2);
            }
            case 2 -> {
                if (lead == null) { go(0); break; }
                if (now - arriveAt >= turnS * 20) { sound("cab.key", 0.7f); go(3); }
            }
            case 3 -> {
                Locomotive nl = loco(newLead);
                if (nl == null || a == null) { fault("the train went missing"); break; }
                Long next = stopAfter(a);
                if (next == null) { routeTxt = "NO NEXT STOP"; routeOk = true; go(4); break; }
                TrackPath p = path(nl, 1, next);
                if (p == null) p = path(nl, -1, next);
                routeTries++;
                if (p == null) {
                    routeTxt = "NO ROUTE"; routeOk = false;
                    if (routeTries == 60 && !routeAlarm) { routeAlarm = true; AlarmManager.get(world).raise(world, "tb:rt:" + pos.toLong(), AlarmManager.Cat.AUTO, AlarmManager.Sev.MINOR, "TURNBACK " + id() + ": no route out for " + trainName, pos, false); }
                    break;
                }
                String owner = "auto:" + nl.getUUID();
                boolean ok = true;
                for (Map.Entry<Vec3i, SwitchState> e : p.switches.entrySet()) {
                    double dd = p.distanceTo(e.getKey().x + 0.5, e.getKey().y, e.getKey().z + 0.5, 6);
                    if (dd < 0 || dd > 200) continue;
                    if (!Interlocking.claim(world, e.getKey(), e.getValue(), owner)) { ok = false; break; }
                }
                if (!ok) {
                    routeTxt = "POINTS LOCKED"; routeOk = false;
                    if (routeTries == 60 && !routeAlarm) { routeAlarm = true; AlarmManager.get(world).raise(world, "tb:rt:" + pos.toLong(), AlarmManager.Cat.AUTO, AlarmManager.Sev.MINOR, "TURNBACK " + id() + ": route out for " + trainName + " locked against it", pos, false); }
                    break;
                }
                routeOk = true; routeTxt = Math.round(p.length) + " M " + p.switches.size() + " SW";
                AlarmManager.get(world).clear("tb:rt:" + pos.toLong());
                go(4);
            }
            case 4 -> {
                Locomotive nl = loco(newLead);
                if (nl == null) { fault("the train went missing"); break; }
                if (ctl.get("k_bypass") == 0) {
                    Object[] bt = SplitJoin.brakeTest(world, SplitJoin.ordered(nl));
                    brakeTxt = (boolean) bt[0] ? "OK" : "FAIL";
                    if (!(boolean) bt[0]) { fault("brake test from the new cab: " + bt[1]); break; }
                } else brakeTxt = "BYPASS";
                if (!powered()) break;
                if (!newLead.equals(train)) SplitJoin.transfer(world, train, nl, true);
                long took = now - arriveAt;
                boolean isLate = took > planTicks();
                if (isLate) late++;
                turnbacks++;
                cleared.put(train, now); cleared.put(newLead, now);
                Recorder.event(world, "TURNBACK " + id() + ": " + trainName + " turned in " + clock(took) + (isLate ? " - LATE (plan " + clock(planTicks()) + ")" : "") + (propel ? ", propelling" : ", driven from " + cabName));
                com.dogpound.railmap.api.QuestEvents.fire(world, null, pos, "turnback", "train", trainName, "seconds", took / 20, "late", isLate);
                nl.setHorn(20, 1f);
                sound("cab.brake_release", 0.6f);
                train = newLead;
                go(5);
            }
            default -> { }
        }
        live(now);
    }

    private void live(long now) {
        put("l_arr", stage >= 1 && stage <= 5);
        put("l_walk", stage == 2);
        put("l_cab", stage >= 3 && stage <= 5 && !propel);
        put("l_pp", propel && stage >= 2 && stage <= 5);
        put("l_rte", routeOk && stage >= 4);
        put("l_rtef", stage == 3 && routeTries > 2 && !routeOk);
        put("l_brk", brakeTxt.equals("OK") || brakeTxt.equals("BYPASS"));
        put("l_brkf", brakeTxt.equals("FAIL"));
        put("l_go", stage == 5);
        put("l_late", active() && now - arriveAt > planTicks());
        put("l_flt", stage == 9);
        put("l_pwr", powered());
        put("g_turn", active() ? (int) Math.min(100, (now - arriveAt) * 100 / Math.max(1, planTicks())) : 0);
        put("d_count", turnbacks);
        put("d_late", late);
        text("d_stage", stage == 9 ? "FAULT" : STAGE[stage]);
        text("d_train", active() || stage == 5 ? shortName(trainName) : "--");
        text("d_timer", (active() ? clock(now - arriveAt) : "0:00") + " / " + clock(planTicks()));
        text("d_turn", String.format(Locale.ROOT, "TURN %.0f S %.0f M", turnS, lenM));
        text("d_cab", shortName(cabName));
        text("d_route", routeTxt);
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        switch (id) {
            case "test": return lampTest();
            case "p_reset": {
                if (stage != 9) return "§7Nothing to reset - " + gearState();
                if (train != null) cleared.put(train, world.getTotalWorldTime());
                AlarmManager.get(world).clear("tb:" + pos.toLong());
                Recorder.event(world, "TURNBACK " + id() + " reset by " + who(who));
                train = null;
                go(0);
                return "§aReset - the train is released as it stands";
            }
            case "p_now": return "§e" + forceStep();
            default: return null;
        }
    }

    String forceStep() { if (stage == 2) { arriveAt -= (long) (turnS * 20); return "crew walk skipped"; } return "stage " + STAGE[stage]; }

    @Override
    protected String[] assetRow() {
        long now = world.getTotalWorldTime();
        return new String[]{"TB", id(), (cabOnly() ? "CAB END ONLY" : "PUSH-PULL") + (ctl.get("s_on") == 0 ? " (OFF)" : ""), stage == 9 ? "FAULT " + fault : STAGE[stage], active() ? trainName : "-",
                active() ? (now - arriveAt) / 20 + "/" + Math.round(turnS) : String.valueOf(Math.round(turnS)), cabName, routeTxt, brakeTxt, String.valueOf(turnbacks), String.valueOf(late), powered() ? "12V" : "NO 12V"};
    }

    @Override
    protected void save(NBTTagCompound t) {
        t.setInteger("stage", stage); if (train != null) t.setString("train", train.toString()); if (newLead != null) t.setString("nl", newLead.toString());
        t.setLong("arr", arriveAt); t.setLong("sAt", stageAt); t.setDouble("turn", turnS); t.setDouble("len", lenM); t.setInteger("tb", turnbacks); t.setInteger("late", late); t.setInteger("faults", faults);
        t.setBoolean("pp", propel); t.setString("fault", fault); t.setString("bt", brakeTxt); t.setString("tn", trainName); t.setString("cab", cabName); t.setString("rt", routeTxt);
    }

    @Override
    protected void load(NBTTagCompound t) {
        stage = t.getInteger("stage"); train = TileChangeover.uuid(t.getString("train")); newLead = TileChangeover.uuid(t.getString("nl"));
        arriveAt = t.getLong("arr"); stageAt = t.getLong("sAt"); turnS = t.getDouble("turn"); lenM = t.getDouble("len"); turnbacks = t.getInteger("tb"); late = t.getInteger("late"); faults = t.getInteger("faults");
        propel = t.getBoolean("pp"); fault = t.getString("fault"); brakeTxt = t.hasKey("bt") ? t.getString("bt") : "-"; trainName = t.hasKey("tn") ? t.getString("tn") : "-";
        cabName = t.hasKey("cab") ? t.getString("cab") : "-"; routeTxt = t.hasKey("rt") ? t.getString("rt") : "-";
    }
}
