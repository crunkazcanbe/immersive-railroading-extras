package com.dogpound.railmap.planning;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock.CouplerType;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.api.Audit;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.server.StationData;
import com.dogpound.railmap.sim.SimData;
import com.dogpound.railmap.track.Rails;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Dynamic train formation - splitting and joining (her "next 48" list #9), on Immersive Railroading's own couplers.
 * <ul>
 *   <li><b>Uncoupling permission</b>: only standing (under 1 km/h), brakes applied, at a stop of the train's line (a
 *   platform, where staff can do it safely - config); never with the lead loco in mid-train.</li>
 *   <li><b>Split</b>: the couplers between the two portions are lifted (IR uncouples), each portion gets a BRAKE
 *   CONTINUITY TEST (every vehicle on the continuous brake, knuckles sound, braked mass) - a rear portion with its own
 *   locomotive becomes a NEW TRAIN (its own driverless identity "&lt;train&gt;-B" on the same or another line, dwell and
 *   speed copied); one without is SECURED with every hand brake on.</li>
 *   <li><b>Join</b>: two portions at the same place: couplers set to engage, the joining portion creeps up at 3 km/h
 *   until IR couples them, stops, the brake continuity test runs on the whole train and the joining identity ends - the
 *   front portion carries on as the full train.</li>
 *   <li><b>Through services</b>: a plan "train X splits its rear N cars at A, they run line L, and rejoin at B" runs by
 *   itself every lap; each step and every refusal goes to the recorder, faults to the alarms.</li>
 * </ul>
 */
public final class SplitJoin {
    private SplitJoin() {}

    /** active joins: mover -> {target, started tick, reverser tried, best gap} */
    private static final Map<UUID, Object[]> JOINS = new HashMap<>();

    /** the train from one end: the lead loco must be at an end */
    public static List<EntityCoupleableRollingStock> ordered(EntityCoupleableRollingStock lead) {
        List<EntityCoupleableRollingStock> o = new ArrayList<>();
        EntityCoupleableRollingStock f = lead.getCoupled(CouplerType.FRONT), b = lead.getCoupled(CouplerType.BACK);
        if (f != null && b != null) return null;
        o.add(lead);
        EntityCoupleableRollingStock prev = lead, cur = f != null ? f : b;
        while (cur != null && o.size() < 200 && !o.contains(cur)) {
            o.add(cur);
            EntityCoupleableRollingStock n1 = cur.getCoupled(CouplerType.FRONT), n2 = cur.getCoupled(CouplerType.BACK);
            EntityCoupleableRollingStock next = n1 != null && n1 != prev ? n1 : n2 != null && n2 != prev ? n2 : null;
            prev = cur;
            cur = next;
        }
        return o;
    }

    /** the brake continuity test: {ok, text} */
    public static Object[] brakeTest(World w, List<EntityCoupleableRollingStock> part) {
        double kg = 0, braked = 0;
        List<String> bad = new ArrayList<>();
        for (int i = 0; i < part.size(); i++) {
            EntityCoupleableRollingStock s = part.get(i);
            double t = Rails.tonnes(s) * 1000;
            kg += t;
            try { if (s.getDefinition().hasPressureBrake()) braked += t; else bad.add(Rails.name(s) + " no air brake"); } catch (RuntimeException ignored) { }
            if (i + 1 < part.size()) {
                CouplerType ct = s.getCouplerFor(part.get(i + 1));
                SimData.Car c = SimData.get(w).peek(s.getUUID());
                if (ct != null && c != null && c.coupler[ct == CouplerType.FRONT ? 0 : 1] <= 0.15f) bad.add(Rails.name(s) + " knuckle broken");
            }
        }
        double pct = kg <= 0 ? 0 : braked * 100 / kg;
        boolean ok = bad.isEmpty() && pct >= 50;
        return new Object[]{ok, String.format(Locale.ROOT, "%s %.0f %% braked%s", ok ? "CONTINUITY OK," : "FAILED:", pct, bad.isEmpty() ? "" : " - " + String.join(", ", bad.subList(0, Math.min(3, bad.size()))))};
    }

    private static boolean standing(EntityCoupleableRollingStock s) { return Math.abs(s.getCurrentSpeed().metric()) < 1; }

    /** null = may uncouple here; else why not */
    public static String permission(World w, Locomotive lead) {
        if (!standing(lead)) return "the train is moving";
        if (!PlanConfig.splitNeedsPlatform) return null;
        RailwayData.AutoTrain a = RailwayData.get(w).train(lead.getUUID());
        Autopilot.Run r = Autopilot.run(lead.getUUID());
        if (r != null && r.dwelling) return null;
        Vec3d p = lead.getPosition();
        for (RailwayData.Line l : RailwayData.get(w).lines().values()) {
            if (a != null && !l.name.equals(a.line)) continue;
            for (long k : l.stations) { BlockPos b = BlockPos.fromLong(k); if (Math.hypot(b.getX() - p.x, b.getZ() - p.z) < 60) return lead.getTrainBrakePos() >= 0.3f || lead.getIndependentBrake() >= 0.3f ? null : "apply the brakes first"; }
        }
        return "only at a station stop of its line (platform staff)";
    }

    public static String split(World w, Locomotive lead, int rearCars, String newLine, String who) {
        String no = permission(w, lead);
        if (no != null) return "§cUncoupling refused: " + no;
        List<EntityCoupleableRollingStock> o = ordered(lead);
        if (o == null) return "§cUncoupling refused: the lead locomotive is in mid-train";
        if (rearCars < 1 || rearCars >= o.size()) return "§cThe train has " + o.size() + " vehicles - split off 1.." + (o.size() - 1);
        int k = o.size() - rearCars;
        EntityCoupleableRollingStock a = o.get(k - 1), b = o.get(k);
        CouplerType ca = a.getCouplerFor(b), cb = b.getCouplerFor(a);
        if (ca != null) a.setCouplerEngaged(ca, false);
        if (cb != null) b.setCouplerEngaged(cb, false);
        List<EntityCoupleableRollingStock> front = new ArrayList<>(o.subList(0, k)), rear = new ArrayList<>(o.subList(k, o.size()));
        Object[] tf = brakeTest(w, front), tr = brakeTest(w, rear);
        RailwayData data = RailwayData.get(w);
        RailwayData.AutoTrain at = data.train(lead.getUUID());
        String label = at != null && !at.label.isEmpty() ? at.label : Rails.name(lead);
        Locomotive rearLoco = null;
        for (int i = rear.size() - 1; i >= 0; i--) if (rear.get(i) instanceof Locomotive l && !l.getDefinition().isCabCar()) { rearLoco = l; break; }
        if (rearLoco == null) for (EntityCoupleableRollingStock s : rear) if (s instanceof Locomotive l) { rearLoco = l; break; }
        String rearWhat;
        if (rearLoco != null && !rearLoco.getDefinition().isCabCar()) {
            RailwayData.AutoTrain nt = data.setTrain(rearLoco.getUUID());
            nt.label = label + "-B";
            nt.line = newLine == null || newLine.isEmpty() || data.line(newLine) == null ? at == null ? "" : at.line : newLine;
            if (at != null) { nt.mode = at.mode; nt.dwellSeconds = at.dwellSeconds; nt.maxKmh = at.maxKmh; nt.index = at.index; nt.direction = -at.direction; }
            data.markDirty();
            rearWhat = "new train " + nt.label + (nt.line.isEmpty() ? "" : " on " + nt.line);
        } else {
            for (EntityCoupleableRollingStock s : rear) s.setHandBrake(1f);
            rearWhat = "rear portion SECURED (hand brakes on, no locomotive)";
        }
        String txt = "SPLIT " + label + " at " + here(w, lead) + ": " + front.size() + " + " + rear.size() + " vehicles; front " + tf[1] + "; rear " + tr[1] + "; " + rearWhat;
        Recorder.event(w, txt);
        Audit.log(w, Audit.Src.PLAYER, who, new BlockPos(lead.getPosition().x, lead.getPosition().y, lead.getPosition().z), label, "split", o.size() + " vehicles", front.size() + "+" + rear.size(), (String) tf[1]);
        if (!(boolean) tf[0] || !(boolean) tr[0])
            AlarmManager.get(w).raise(w, "split:" + lead.getUUID(), AlarmManager.Cat.INCIDENT, AlarmManager.Sev.MAJOR, "BRAKE TEST FAILED after split of " + label + ": " + (!(boolean) tf[0] ? tf[1] : tr[1]),
                    new BlockPos(lead.getPosition().x, lead.getPosition().y, lead.getPosition().z), false);
        return "§a" + txt;
    }

    /** start joining {@code mover}'s portion onto {@code target}'s */
    public static String join(World w, Locomotive target, Locomotive mover, String who) {
        if (!standing(target)) return "§cThe portion being joined must be standing";
        List<EntityCoupleableRollingStock> t = ordered(target), m = ordered(mover);
        if (t == null || m == null) return "§cA lead locomotive is in mid-train";
        if (t.contains(mover)) return "§7Already one train";
        EntityCoupleableRollingStock[] ends = closestEnds(t, m);
        double gap = gap(ends[0], ends[1]);
        if (gap > 80) return String.format(Locale.ROOT, "§cThe portions are %.0f m apart - bring them within 80 m", gap);
        for (EntityCoupleableRollingStock e : ends) for (CouplerType ct : CouplerType.values()) if (e.getCoupled(ct) == null) e.setCouplerEngaged(ct, true);
        RailwayData data = RailwayData.get(w);
        if (data.train(mover.getUUID()) != null) { data.removeTrain(mover.getUUID()); data.markDirty(); }   // its own identity ends; the creep is ours
        JOINS.put(mover.getUUID(), new Object[]{target.getUUID(), w.getTotalWorldTime(), 0, gap, who});
        Recorder.event(w, "JOIN started: " + Rails.name(mover) + " closing up on " + Rails.name(target) + String.format(Locale.ROOT, " (%.0f m)", gap));
        return String.format(Locale.ROOT, "§eJoining: %s creeping up at 3 km/h (%.0f m)", Rails.name(mover), gap);
    }

    private static EntityCoupleableRollingStock[] closestEnds(List<EntityCoupleableRollingStock> t, List<EntityCoupleableRollingStock> m) {
        EntityCoupleableRollingStock[] te = {t.get(0), t.get(t.size() - 1)}, me = {m.get(0), m.get(m.size() - 1)};
        EntityCoupleableRollingStock[] best = null;
        double bd = Double.MAX_VALUE;
        for (EntityCoupleableRollingStock a : te) for (EntityCoupleableRollingStock b : me) { double d = gap(a, b); if (d < bd) { bd = d; best = new EntityCoupleableRollingStock[]{a, b}; } }
        return best;
    }

    private static double gap(EntityCoupleableRollingStock a, EntityCoupleableRollingStock b) {
        Vec3d p = a.getPosition(), q = b.getPosition();
        double half = 0;
        try { half = a.getDefinition().getLength(a.gauge) / 2 + b.getDefinition().getLength(b.gauge) / 2; } catch (RuntimeException ignored) { }
        return Math.max(0, Math.hypot(p.x - q.x, p.z - q.z) - half);
    }

    /** every 5 ticks: drive the joins */
    static void tickJoins(World w) {
        if (JOINS.isEmpty()) return;
        long now = w.getTotalWorldTime();
        for (Iterator<Map.Entry<UUID, Object[]>> it = JOINS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Object[]> e = it.next();
            Locomotive mover = Autopilot.find(w, e.getKey()), target = Autopilot.find(w, (UUID) e.getValue()[0]);
            if (mover == null || target == null) { it.remove(); continue; }
            if (target.getTrain().contains(mover)) {
                mover.setThrottle(0); mover.setTrainBrake(0.6f);
                List<EntityCoupleableRollingStock> all = ordered(target);
                Object[] bt = brakeTest(w, all == null ? target.getTrain() : all);
                Recorder.event(w, "JOINED " + Rails.name(mover) + " to " + Rails.name(target) + " at " + here(w, target) + ": " + bt[1]);
                if (!(boolean) bt[0]) AlarmManager.get(w).raise(w, "join:" + target.getUUID(), AlarmManager.Cat.INCIDENT, AlarmManager.Sev.MAJOR, "BRAKE TEST FAILED after join: " + bt[1], null, false);
                it.remove();
                continue;
            }
            List<EntityCoupleableRollingStock> t = ordered(target), m = ordered(mover);
            if (t == null || m == null || now - (long) e.getValue()[1] > 20 * 120) {
                mover.setThrottle(0); mover.setTrainBrake(1);
                Recorder.event(w, "JOIN ABANDONED: " + Rails.name(mover) + " did not couple to " + Rails.name(target));
                AlarmManager.get(w).raise(w, "join:" + target.getUUID(), AlarmManager.Cat.INCIDENT, AlarmManager.Sev.MINOR, "JOIN FAILED: " + Rails.name(mover) + " did not couple to " + Rails.name(target), null, false);
                it.remove();
                continue;
            }
            double g = gap(closestEnds(t, m)[0], closestEnds(t, m)[1]);
            if (e.getValue().length > 5 && g < 0.5) {                              // batch-3 #2 uncoupled banking: buffered up, couplers stay open
                mover.setThrottle(0); mover.setTrainBrake(0.2f);
                Recorder.event(w, "BUFFERED UP: " + Rails.name(mover) + " against " + Rails.name(target) + " (couplers open)");
                it.remove();
                continue;
            }
            double best = (double) e.getValue()[3];
            int rev = (int) e.getValue()[2];
            if (rev == 0) rev = 1;
            if (g > best + 1.5) { rev = -rev; e.getValue()[3] = g; }               // going the wrong way: reverse
            else if (g < best) e.getValue()[3] = g;
            e.getValue()[2] = rev;
            double kmh = Math.abs(mover.getCurrentSpeed().metric());
            mover.setIndependentBrake(0);
            mover.setReverser(rev);
            if (kmh > 3.5 || g < 0.6 && kmh > 1.5) { mover.setThrottle(0); mover.setTrainBrake(0.3f); }
            else { mover.setTrainBrake(0); mover.setThrottle(Math.min(0.3f, mover.getThrottle() + 0.05f)); }
        }
    }

    private static String here(World w, Locomotive l) {
        Vec3d p = l.getPosition();
        String best = Math.round(p.x) + "," + Math.round(p.z);
        double bd = 80;
        for (Map.Entry<Long, String> e : StationData.get(w).names().entrySet()) {
            BlockPos b = BlockPos.fromLong(e.getKey());
            double d = Math.hypot(b.getX() - p.x, b.getZ() - p.z);
            if (d < bd) { bd = d; best = e.getValue(); }
        }
        return best;
    }

    // ---- through services: split at A, rejoin at B ---------------------------------------------------------------------
    static void tickPlans(World w) {
        PlanData d = PlanData.get(w);
        if (d.splits.isEmpty()) return;
        RailwayData data = RailwayData.get(w);
        long now = w.getTotalWorldTime();
        for (PlanData.SplitPlan p : d.splits) {
            RailwayData.AutoTrain front = null;
            for (RailwayData.AutoTrain a : data.trains().values()) if (p.train.equalsIgnoreCase(a.label)) { front = a; break; }
            if (front == null) { if (!p.state.startsWith("JOIN")) state(w, d, p, "NO TRAIN " + p.train); continue; }
            Locomotive fl = Autopilot.find(w, front.loco);
            if (fl == null) continue;
            Autopilot.Run fr = Autopilot.run(front.loco);
            String at = fr != null && fr.dwelling ? Autopilot.nameOf(StationData.get(w), fr.dwellStation) : "";
            switch (p.state.split(" ")[0]) {
                case "WAITING": case "NO": case "FAULT:":
                    if (at.equalsIgnoreCase(p.splitAt) && now - p.last > 20 * 60) {
                        Autopilot.adjustDwell(front.loco, now, 30);                                // time for the shunter
                        String r = split(w, fl, p.rearCars, p.rearLine, "plan #" + p.id);
                        p.last = now;
                        if (r.startsWith("§a")) {
                            for (RailwayData.AutoTrain a : data.trains().values()) if ((p.train + "-B").equalsIgnoreCase(a.label)) p.rear = a.loco.toString();
                            state(w, d, p, "SPLIT at " + p.splitAt);
                        } else state(w, d, p, "FAULT: " + r.replaceAll("§.", ""));
                    } else if (!p.state.startsWith("WAITING") && !p.state.startsWith("FAULT")) state(w, d, p, "WAITING for " + p.splitAt);
                    break;
                case "SPLIT": {
                    if (p.rear.isEmpty()) { state(w, d, p, "WAITING for " + p.splitAt); break; }
                    UUID ru;
                    try { ru = UUID.fromString(p.rear); } catch (IllegalArgumentException e) { state(w, d, p, "FAULT: rear lost"); break; }
                    Autopilot.Run rr = Autopilot.run(ru);
                    String rat = rr != null && rr.dwelling ? Autopilot.nameOf(StationData.get(w), rr.dwellStation) : "";
                    Locomotive rl = Autopilot.find(w, ru);
                    if (rl == null) break;
                    if (at.equalsIgnoreCase(p.joinAt) && rat.equalsIgnoreCase(p.joinAt)) {
                        Autopilot.adjustDwell(front.loco, now, 90);
                        String r = join(w, fl, rl, "plan #" + p.id);
                        state(w, d, p, r.startsWith("§e") ? "JOINING at " + p.joinAt : "FAULT: " + r.replaceAll("§.", ""));
                    } else if (at.equalsIgnoreCase(p.joinAt)) Autopilot.adjustDwell(front.loco, now, 5);   // first portion waits for the other
                    break;
                }
                case "JOINING":
                    if (!JOINS.containsKey(parse(p.rear))) { p.rear = ""; p.last = now; state(w, d, p, "WAITING for " + p.splitAt + " (joined)"); }
                    break;
                default:
            }
        }
    }

    private static UUID parse(String s) { try { return UUID.fromString(s); } catch (IllegalArgumentException e) { return new UUID(0, 0); } }

    private static void state(World w, PlanData d, PlanData.SplitPlan p, String s) {
        if (s.equals(p.state)) return;
        p.state = s;
        p.log = com.dogpound.railmap.alarm.Recorder.clock(w, w.getTotalWorldTime()) + " " + s;
        d.markDirty();
        if (s.startsWith("FAULT")) AlarmManager.get(w).raise(w, "splitplan:" + p.id, AlarmManager.Cat.AUTO, AlarmManager.Sev.MINOR, "Split/join plan #" + p.id + " " + p.train + ": " + s, null, false);
    }

    public static PlanData.SplitPlan plan(World w, String train, String splitAt, int rearCars, String rearLine, String joinAt) {
        PlanData d = PlanData.get(w);
        PlanData.SplitPlan p = new PlanData.SplitPlan();
        p.id = d.id(); p.train = train; p.splitAt = splitAt; p.rearCars = Math.max(1, rearCars); p.rearLine = rearLine; p.joinAt = joinAt; p.state = "WAITING for " + splitAt;
        d.splits.add(p);
        d.markDirty();
        return p;
    }

    // ---- batch-3 helpers: #1 changeover, #2 banking, #4 coupling points, #5 turnback -----------------------------------

    /** a creep (join or buffer-up) is still running for this mover */
    public static boolean joining(UUID mover) { return JOINS.containsKey(mover); }

    /** #2 uncoupled banking: like join, but the couplers stay open - the mover creeps up until its buffers touch */
    public static String buffer(World w, Locomotive target, Locomotive mover, String who) {
        if (!standing(target)) return "§cThe train being banked must be standing";
        List<EntityCoupleableRollingStock> t = ordered(target), m = ordered(mover);
        if (t == null || m == null) return "§cA lead locomotive is in mid-train";
        EntityCoupleableRollingStock[] ends = closestEnds(t, m);
        double g = gap(ends[0], ends[1]);
        if (g > 120) return String.format(Locale.ROOT, "§cThe banker is %.0f m away - bring it within 120 m", g);
        for (EntityCoupleableRollingStock e : ends) for (CouplerType ct : CouplerType.values()) if (e.getCoupled(ct) == null) e.setCouplerEngaged(ct, false);
        JOINS.put(mover.getUUID(), new Object[]{target.getUUID(), w.getTotalWorldTime(), 0, g, who, Boolean.TRUE});
        Recorder.event(w, "BUFFER-UP started: " + Rails.name(mover) + " closing up on " + Rails.name(target) + String.format(Locale.ROOT, " (%.0f m)", g));
        return String.format(Locale.ROOT, "§eBuffering up: %s creeping at 3 km/h (%.0f m)", Rails.name(mover), g);
    }

    /** gap (m) between the nearest ends of two consists */
    public static double gapBetween(Locomotive a, Locomotive b) {
        List<EntityCoupleableRollingStock> t = ordered(a), m = ordered(b);
        if (t == null || m == null) return Double.MAX_VALUE;
        EntityCoupleableRollingStock[] e = closestEnds(t, m);
        return gap(e[0], e[1]);
    }

    /** lift the couplers between two neighbours (IR uncouples them) */
    public static void uncouple(EntityCoupleableRollingStock a, EntityCoupleableRollingStock b) {
        CouplerType ca = a.getCouplerFor(b), cb = b.getCouplerFor(a);
        if (ca != null) a.setCouplerEngaged(ca, false);
        if (cb != null) b.setCouplerEngaged(cb, false);
    }

    /** consist length over buffers (m) */
    public static double lengthM(List<EntityCoupleableRollingStock> part) {
        double l = 0;
        for (EntityCoupleableRollingStock s : part) { try { l += s.getDefinition().getLength(s.gauge); } catch (RuntimeException e) { l += 12; } }
        return l;
    }

    /** MU / brake control systems that can work together (the same rules as sim.MultiUnit) */
    public static boolean systemsCompatible(String a, String b) {
        if (a.equals("NONE") || b.equals("NONE")) return a.equals("NONE") && b.equals("NONE");   // two unfitted portions: hand signals, no train lines
        return a.equals("ANY") || b.equals("ANY") || a.equals(b);
    }

    /** #4 compatibility of two portions before they are joined: null = compatible, else why not */
    public static String compat(World w, List<EntityCoupleableRollingStock> a, List<EntityCoupleableRollingStock> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return "a portion has its locomotive in mid-train";
        double ga = a.get(0).gauge.value(), gb = b.get(0).gauge.value();
        if (Math.abs(ga - gb) > 0.01) return String.format(Locale.ROOT, "gauge %.3f m vs %.3f m", ga, gb);
        String sa = null, sb = null;
        for (EntityCoupleableRollingStock s : a) if (s instanceof Locomotive l) { sa = com.dogpound.railmap.sim.MultiUnit.system(w, l); break; }
        for (EntityCoupleableRollingStock s : b) if (s instanceof Locomotive l) { sb = com.dogpound.railmap.sim.MultiUnit.system(w, l); break; }
        if (sa != null && sb != null && !systemsCompatible(sa, sb)) return "control systems " + sa + " / " + sb + " don't work together";
        for (List<EntityCoupleableRollingStock> part : java.util.Arrays.asList(a, b))
            for (EntityCoupleableRollingStock s : part) { try { if (!s.getDefinition().hasPressureBrake()) return Rails.name(s) + " has no through air brake"; } catch (RuntimeException ignored) { } }
        return null;
    }

    /**
     * Move a driverless train's identity (line, orders, stops, tickets, earnings) onto another loco of it - a cab change
     * or a loco change. departing = the train leaves now: its stop is done (the next stop is taken, as depart() would).
     */
    public static RailwayData.AutoTrain transfer(World w, UUID from, Locomotive to, boolean departing) {
        RailwayData d = RailwayData.get(w);
        RailwayData.AutoTrain a = d.train(from);
        if (a == null || to.getUUID().equals(from)) return a;
        Long here = departing ? com.dogpound.railmap.auto.Dispatcher.nextStop(d, a) : null;
        Autopilot.release(w, from);
        RailwayData.AutoTrain n = d.setTrain(to.getUUID());
        n.label = a.label; n.mode = a.mode; n.line = a.line; n.home = a.home; n.index = a.index; n.direction = a.direction;
        n.dwellSeconds = a.dwellSeconds; n.maxKmh = a.maxKmh; n.calls.clear(); n.calls.addAll(a.calls);
        n.earned = a.earned; n.earnedLap = a.earnedLap; n.lastLap = a.lastLap; n.delivered = a.delivered; n.trips = a.trips; n.loadedAt = a.loadedAt;
        for (RailwayData.Call c : d.calls()) if (from.equals(c.train)) c.train = to.getUUID();   // tickets ride on
        d.removeTrain(from);
        if (here != null) {
            com.dogpound.railmap.auto.Dispatcher.advance(d, n, here);
            if (n.mode == RailwayData.Mode.LINE && n.index == 0 && n.calls.isEmpty()) { n.trips++; n.lastLap = n.earnedLap; n.earnedLap = 0; }
        }
        d.markDirty();
        return n;
    }

    public static void clear() { JOINS.clear(); }
    public static int activeJoins() { return JOINS.size(); }
}
