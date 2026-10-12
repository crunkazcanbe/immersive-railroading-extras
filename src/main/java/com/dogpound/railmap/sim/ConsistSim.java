package com.dogpound.railmap.sim;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock.CouplerType;
import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.entity.physics.PrideForces;
import cam72cam.immersiverailroading.entity.physics.chrono.ServerChronoState;
import cam72cam.immersiverailroading.wreck.WreckSim;
import cam72cam.immersiverailroading.wreck.Wrecks;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.*;

/**
 * Consist physics and drawbar forces (her ChatGPT list item 7). IR already moves every car as its own mass with its
 * own brakes, rolling / curve / air resistance and grade (Consist / SimulationState); the IR fork now also reports
 * the force in every coupler (PrideForces). Four times a second this:
 * <ul>
 *   <li>LIGHT and FULL: tracks each train's peak draft (tension) and buff (compression) force for the cab and console
 *       and warns the crew when a coupler is overloaded;</li>
 *   <li>FULL: a coupler loaded past its working limit fatigues (worn knuckles fail lower); past its breaking strength
 *       the knuckle breaks, the train parts, both locomotives' brake pipe vents (emergency) and the cut-off rear,
 *       having lost its brake pipe, has its air brakes go on by themselves - IR's own brake model does exactly that to
 *       pressure-braked cars with no locomotive. A broken knuckle stays open until it is replaced
 *       (/irextras knuckle, or a Maintenance Depot service);</li>
 *   <li>FULL: sustained buff force over what a car can take sideways (light car, sharp curve) squeezes it off the
 *       track through Wrecks.</li>
 * </ul>
 * Nothing happens to a train in its first seconds after loading (config physicsGraceSeconds).
 */
public final class ConsistSim {
    private ConsistSim() {}

    /** working limit / breaking strength of a new knuckle (AAR E: ~1.0 MN working, ~2.25 MN ultimate), newtons */
    static final double DRAFT_WORK = 1.0e6, DRAFT_BREAK = 2.25e6, BUFF_WORK = 2.5e6, BUFF_DERAIL = 1.4e6;

    private static final Map<UUID, Long> FIRST_SEEN = new HashMap<>();
    /** locomotives whose train parted: hold them (autopilot too) until this world time */
    private static final Map<UUID, Long> PARTED = new HashMap<>();
    private static final Map<String, Long> WARNED = new HashMap<>();

    public static void clear() {
        FIRST_SEEN.clear();
        PARTED.clear();
        WARNED.clear();
    }

    public static boolean holding(UUID loco, long now) {
        Long t = PARTED.get(loco);
        return t != null && t > now;
    }

    static int irTick(World mc) {
        return ServerChronoState.getState(cam72cam.mod.world.World.get(mc)).getServerTickID();
    }

    static double scale(EntityMoveableRollingStock s) {
        double g = s.gauge == null ? 1 : s.gauge.scale();
        return Math.max(0.05, g);
    }

    static String name(EntityMoveableRollingStock s) {
        if (s.tag != null && !s.tag.isEmpty()) return s.tag;
        return s.getDefinition() == null ? "car" : s.getDefinition().name();
    }

    /** the mean force (N) on one coupler now (+ draft / - buff), 0 when not simulated */
    public static double force(World mc, EntityMoveableRollingStock s, boolean front) {
        double f = PrideForces.mean(s.getUUID(), front, irTick(mc), 4);
        return Double.isNaN(f) ? 0 : f;
    }

    // ---- the pass ------------------------------------------------------------------------------------------------

    static void tick(World mc, SimData data, long now) {
        int mode = data.mode();
        PrideForces.enabled = mode > 0;
        if (mode == 0) return;
        cam72cam.mod.world.World w = cam72cam.mod.world.World.get(mc);
        int tickId = irTick(mc);
        if (now % 1200 == 0) PrideForces.prune(tickId);
        double grace = SimConfig.physicsGraceSeconds * 20.0;
        Map<UUID, EntityCoupleableRollingStock> byId = new HashMap<>();
        List<EntityCoupleableRollingStock> all = w.getEntities(EntityCoupleableRollingStock.class);
        for (EntityCoupleableRollingStock s : all) if (!s.isDead()) byId.put(s.getUUID(), s);
        FIRST_SEEN.keySet().retainAll(byId.keySet());

        for (EntityCoupleableRollingStock s : byId.values()) {
            long first = FIRST_SEEN.computeIfAbsent(s.getUUID(), k -> now);
            boolean settled = now - first >= grace;
            SimData.Car car = data.of(s.getUUID());
            for (CouplerType type : CouplerType.values()) {
                boolean front = type == CouplerType.FRONT;
                UUID oid = s.getCoupledUUID(type);
                if (oid == null) continue;
                EntityCoupleableRollingStock o = byId.get(oid);
                if (o != null && s.getUUID().compareTo(oid) > 0) continue;        // each link once
                float health = car.coupler[front ? 0 : 1];
                SimData.Car ocar = o == null ? null : data.of(oid);
                CouplerType ot = o == null ? null : o.getCouplerFor(s);
                float ohealth = ocar == null || ot == null ? 1f : ocar.coupler[ot == CouplerType.FRONT ? 0 : 1];
                float weakest = Math.min(health, ohealth);
                // a broken knuckle can't hold: keep it open until it is replaced
                if (weakest <= 0 && (s.isCouplerEngaged(type) || o != null && ot != null && o.isCouplerEngaged(ot))) {
                    s.setCouplerEngaged(type, false);
                    if (o != null && ot != null) o.setCouplerEngaged(ot, false);
                    warn(mc, s, "knuckle:" + s.getUUID(), now, "⚠ Broken knuckle between " + name(s) + " and " + (o == null ? "?" : name(o))
                            + " - it won't hold: /irextras knuckle (2 iron) or a depot");
                }
                double f = PrideForces.mean(s.getUUID(), front, tickId, 4);
                if (Double.isNaN(f)) continue;
                car.peakT = Math.max(car.peakT * 0.97, Math.max(0, f));
                car.peakC = Math.max(car.peakC * 0.97, Math.max(0, -f));
                double k = scale(s) * SimConfig.couplerStrength;

                // ---- draft (tension)
                if (f > DRAFT_WORK * k) {
                    warn(mc, s, "draft:" + s.getUUID(), now, String.format(Locale.ROOT,
                            "⚠ DRAWBAR OVERLOAD %.2f MN between %s and %s - ease the throttle / brake gently", f / 1e6, name(s), o == null ? "?" : name(o)));
                    if (mode == 2 && settled) {
                        double over = (f - DRAFT_WORK * k) / ((DRAFT_BREAK - DRAFT_WORK) * k);
                        float wear = (float) (over * over * 0.03);
                        car.coupler[front ? 0 : 1] = Math.max(0.01f, health - wear);
                        if (ocar != null && ot != null) ocar.coupler[ot == CouplerType.FRONT ? 0 : 1] = Math.max(0.01f, ohealth - wear);
                        data.markDirty();
                        double breakAt = DRAFT_BREAK * k * (0.55 + 0.45 * weakest);
                        if (f > breakAt) part(mc, data, s, type, o, f, now);
                    }
                }
                // ---- buff (compression)
                double c = -f;
                if (c > BUFF_WORK * k && mode == 2 && settled) {
                    double over = (c - BUFF_WORK * k) / (BUFF_WORK * k);
                    float wear = (float) Math.min(0.5, over * over * 0.05);
                    car.coupler[front ? 0 : 1] = Math.max(0.01f, health - wear);
                    if (ocar != null && ot != null) ocar.coupler[ot == CouplerType.FRONT ? 0 : 1] = Math.max(0.01f, ohealth - wear);
                    data.markDirty();
                }
                if (o != null && c > 0) {
                    EntityMoveableRollingStock lighter = s.getWeight() <= o.getWeight() ? s : o;
                    double lim = buffLimit(lighter);
                    SimData.Car lc = data.of(lighter.getUUID());
                    if (c > lim) {
                        lc.buffSustain += 0.25;
                        warn(mc, s, "buff:" + s.getUUID(), now, String.format(Locale.ROOT,
                                "⚠ HEAVY BUFF %.2f MN on %s - slack running in, brake earlier and gentler", c / 1e6, name(lighter)));
                        double kmh = Math.abs(lighter.getCurrentSpeed().metric());
                        if (mode == 2 && settled && lc.buffSustain >= 0.5 && kmh > 8 && lighter.wreck == 0) {
                            Wrecks.forceWreck(lighter, WreckSim.DERAILED, String.format(Locale.ROOT, "squeezed off the track by %.1f MN of buff force", c / 1e6));
                            incident(mc, lighter, "buff:" + lighter.getUUID(), String.format(Locale.ROOT,
                                    "BUFF DERAILMENT: %s squeezed off the track (%.1f MN in-train compression at %d km/h)", name(lighter), c / 1e6, Math.round(kmh)));
                            lc.buffSustain = 0;
                        }
                    } else lc.buffSustain = Math.max(0, lc.buffSustain - 0.25);
                }
            }
        }
    }

    /** what a car takes end-on before it climbs / jackknifes: heavier cars more, sharp curves much less */
    static double buffLimit(EntityMoveableRollingStock s) {
        double t = s.getWeight() / 1000.0;
        double massK = Math.max(0.3, Math.min(1.6, t / 55.0));
        double curve = 1 + Math.max(0, s.getAngle()) * 5;                // getAngle = degrees per metre between bogies
        return BUFF_DERAIL * massK / curve * scale(s) * SimConfig.buffStrength;
    }

    /** the knuckle broke: open both couplers, vent the brake pipe, tell everyone */
    private static void part(World mc, SimData data, EntityCoupleableRollingStock s, CouplerType type, EntityCoupleableRollingStock o, double f, long now) {
        List<EntityCoupleableRollingStock> train = new ArrayList<>(s.getTrain());
        SimData.Car car = data.of(s.getUUID());
        car.coupler[type == CouplerType.FRONT ? 0 : 1] = 0;
        s.setCouplerEngaged(type, false);
        if (o != null) {
            CouplerType ot = o.getCouplerFor(s);
            if (ot != null) { o.setCouplerEngaged(ot, false); data.of(o.getUUID()).coupler[ot == CouplerType.FRONT ? 0 : 1] = 0; }
        }
        data.markDirty();
        // the brake pipe parted with the train: emergency on every locomotive (the rear's own brakes apply by themselves)
        for (EntityCoupleableRollingStock c : train) {
            if (c instanceof Locomotive l) {
                l.setThrottle(0);
                l.setTrainBrake(1);
                PARTED.put(l.getUUID(), now + 20 * 60);
            }
        }
        incident(mc, s, "parted:" + s.getUUID(), String.format(Locale.ROOT,
                "TRAIN PARTED: knuckle broke between %s and %s at %.2f MN - emergency brake, rear portion braking on its own",
                name(s), o == null ? "?" : name(o), f / 1e6));
    }

    static void incident(World mc, EntityMoveableRollingStock s, String key, String text) {
        Vec3d p = s.getPosition();
        BlockPos at = new BlockPos(p.x, p.y, p.z);
        AlarmManager.get(mc).raise(mc, key, AlarmManager.Cat.INCIDENT, AlarmManager.Sev.CRITICAL, text, at, false);
        Recorder.event(mc, text);
        for (EntityPlayer pl : riders(s)) if (pl instanceof EntityPlayerMP mp) mp.sendMessage(new TextComponentString("§c⛔ " + text));
    }

    private static void warn(World mc, EntityCoupleableRollingStock s, String key, long now, String text) {
        Long last = WARNED.get(key);
        if (last != null && now - last < 100) return;
        WARNED.put(key, now);
        for (EntityPlayer pl : riders(s)) if (pl instanceof EntityPlayerMP mp) mp.sendStatusMessage(new TextComponentString("§6" + text), true);
    }

    static List<EntityPlayer> riders(EntityMoveableRollingStock stock) {
        List<EntityPlayer> out = new ArrayList<>();
        List<EntityMoveableRollingStock> cars = new ArrayList<>();
        if (stock instanceof EntityCoupleableRollingStock c) cars.addAll(c.getTrain());
        else cars.add(stock);
        for (EntityMoveableRollingStock car : cars)
            for (cam72cam.mod.entity.Entity e : car.getPassengers()) if (e.internal instanceof EntityPlayer p) out.add(p);
        return out;
    }

    // ---- the train as a whole, for the cab, the console and the diagnostic panel -----------------------------------

    public static final class Summary {
        public double tonnes, lengthM, gradePct, maxDraftN, maxBuffN, teAvailN, teUsedN, gradeN, brakedPct, brakeAppliedPct;
        public float worstCoupler = 1f;
        public int cars, locos;
        public String worstCouplerAt = "";
        public boolean stallRisk;
    }

    public static Summary summary(World mc, EntityCoupleableRollingStock lead) {
        Summary sm = new Summary();
        SimData data = SimData.get(mc);
        List<EntityCoupleableRollingStock> train = lead.getTrain();
        double massKg = 0, braking = 0, cyl = 0;
        EntityCoupleableRollingStock a = null, b = null;
        for (EntityCoupleableRollingStock s : train) {
            double m = s.getWeight();
            massKg += m;
            sm.lengthM += s.getDefinition().getLength(s.gauge);
            braking += s.getBrakingWeight() * Math.min(1.5, s.getBrakeSystemEfficiency());
            cyl += s.getBrakeCylinderPressure() * m;
            sm.cars++;
            if (s instanceof Locomotive l) {
                sm.locos++;
                try {
                    sm.teAvailN += l.getDefinition().getScriptedStartingTractionNewtons(l.gauge, l)
                            * cam72cam.immersiverailroading.Config.ConfigBalance.tractionMultiplier * l.adhesionCoefficient();
                    sm.teUsedN += Math.abs(l.getAppliedTractiveEffort(l.getRealSpeed()));
                } catch (RuntimeException ignored) { }
            }
            for (int side = 0; side < 2; side++) {
                double f = force(mc, s, side == 0);
                sm.maxDraftN = Math.max(sm.maxDraftN, f);
                sm.maxBuffN = Math.max(sm.maxBuffN, -f);
                if (s.getCoupledUUID(side == 0 ? CouplerType.FRONT : CouplerType.BACK) != null) {
                    SimData.Car c = data.peek(s.getUUID());
                    if (c != null && c.coupler[side] < sm.worstCoupler) { sm.worstCoupler = c.coupler[side]; sm.worstCouplerAt = name(s); }
                }
            }
            if (a == null) a = s;
            b = s;
        }
        sm.tonnes = massKg / 1000;
        sm.brakedPct = massKg > 0 ? braking / massKg * 100 : 0;
        sm.brakeAppliedPct = massKg > 0 ? cyl / massKg * 100 : 0;
        // the grade the train lies on: the height difference between its two ends, signed against the way it goes
        if (a != null && b != null && a != b) {
            Vec3d pa = a.getPosition(), pb = b.getPosition();
            double dx = pa.x - pb.x, dz = pa.z - pb.z, h = Math.sqrt(dx * dx + dz * dz);
            if (h > 2) {
                double g = (pa.y - pb.y) / h;
                double[] dir = heading(lead);
                if (dx * dir[0] + dz * dir[1] < 0) g = -g;         // the end it heads for is b
                sm.gradePct = g * 100;
            }
        } else if (a != null) {
            sm.gradePct = Math.tan(Math.toRadians(-a.getRotationPitch())) * 100;
        }
        sm.gradeN = massKg * 9.81 * sm.gradePct / 100 * cam72cam.immersiverailroading.Config.ConfigBalance.slopeMultiplier;
        double rolling = massKg * 9.81 * 0.0015;
        sm.stallRisk = sm.locos > 0 && sm.gradeN + rolling > sm.teAvailN * 0.95;
        return sm;
    }

    /** unit x,z the lead unit is heading (velocity, else where its reverser points) */
    static double[] heading(EntityMoveableRollingStock s) {
        Vec3d v = s.getVelocity();
        double l = Math.sqrt(v.x * v.x + v.z * v.z);
        if (l > 1e-4) return new double[]{v.x / l, v.z / l};
        float rev = s instanceof Locomotive loco && loco.getReverser() < 0 ? -1 : 1;
        Vec3d f = cam72cam.immersiverailroading.util.VecUtil.fromWrongYaw(rev, s.getRotationYaw());
        return new double[]{f.x, f.z};
    }

    /** one line for the cab / console */
    public static String line(Summary s) {
        return String.format(Locale.ROOT, "%,.0f t · %d m · %d cars · grade %+.1f%% · draft %s · buff %s · brake %d%%%s%s",
                s.tonnes, Math.round(s.lengthM), s.cars, s.gradePct, mn(s.maxDraftN), mn(s.maxBuffN), Math.round(s.brakedPct),
                s.worstCoupler < 0.6 ? String.format(Locale.ROOT, " · worn knuckle %d%% (%s)", Math.round(s.worstCoupler * 100), s.worstCouplerAt) : "",
                s.stallRisk ? " · STALL RISK" : "");
    }

    static String mn(double n) { return n >= 1e6 ? String.format(Locale.ROOT, "%.2f MN", n / 1e6) : String.format(Locale.ROOT, "%d kN", Math.round(n / 1000)); }

    // ---- repairs ---------------------------------------------------------------------------------------------------

    /** a new knuckle in every broken / worn coupler of stock within reach; returns how many */
    public static int replaceKnuckles(World mc, BlockPos at, double reach) {
        SimData data = SimData.get(mc);
        int n = 0;
        for (EntityCoupleableRollingStock s : cam72cam.mod.world.World.get(mc).getEntities(EntityCoupleableRollingStock.class)) {
            Vec3d p = s.getPosition();
            if (at.distanceSqToCenter(p.x, p.y, p.z) > reach * reach) continue;
            SimData.Car c = data.of(s.getUUID());
            for (int i = 0; i < 2; i++) if (c.coupler[i] < 1f) { c.coupler[i] = 1f; n++; }
        }
        data.markDirty();
        return n;
    }
}
