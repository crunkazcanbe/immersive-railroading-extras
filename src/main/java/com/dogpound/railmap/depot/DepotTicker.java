package com.dogpound.railmap.depot;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.entity.LocomotiveDiesel;
import cam72cam.immersiverailroading.entity.LocomotiveSteam;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.server.Electric;
import com.dogpound.railmap.server.RailProps;
import com.dogpound.railmap.server.WearData;
import com.dogpound.railmap.sim.SimConfig;
import com.dogpound.railmap.sim.SimData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.*;

/**
 * The depot organisation's clock (her ChatGPT list section 8). Once a second, for every loaded IR vehicle: operating
 * hours, fault count, the wear trend (#52), its condition and what that restricts (power, cooling, brake force, a speed
 * limit for the whole train), exam-plan due checks (#51), and how dirty / watered / full it is (#83). Every tick the
 * speed limits are held (throttle off over the limit, brakes if it keeps running away - like an overspeed governor).
 * Every five seconds the fleet manager: spares (#61), line allocation (#60), depot routing and night stabling (#59).
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class DepotTicker {
    private DepotTicker() {}

    private static final boolean IR = Loader.isModLoaded("immersiverailroading");
    /** the units seen this session (the EngineSim hook and the speed governor read them) */
    private static final Map<UUID, FleetData.Unit> LIVE = new HashMap<>();
    /** locomotive -> {train speed limit km/h}, and the reason */
    private static final Map<UUID, Float> CAP = new HashMap<>();
    private static final Map<UUID, String> CAP_WHY = new HashMap<>();
    private static final Map<UUID, Locomotive> LOCOS = new HashMap<>();

    public static FleetData.Unit live(UUID id) { return LIVE.get(id); }
    public static float trainCap(UUID loco) { return CAP.getOrDefault(loco, Float.MAX_VALUE); }
    public static String trainCapWhy(UUID loco) { return CAP_WHY.getOrDefault(loco, ""); }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side.isClient() || !IR) return;
        World w = e.world;
        long now = w.getTotalWorldTime();
        try {
            govern(w);
            if (now % 20 == 0) second(w, now);
            if (now % 100 == 40) Fleet.tick(w, now);
            com.dogpound.railmap.station.StationFacilities.tick(w, now);
        } catch (LinkageError | RuntimeException ex) {
            RailMap.LOG.warn("[IR Extras] depot tick failed: {}", ex.toString());
        }
    }

    /** every tick: hold each restricted train to its limit through its own controls */
    private static void govern(World w) {
        if (CAP.isEmpty()) return;
        for (Map.Entry<UUID, Float> c : CAP.entrySet()) {
            Locomotive l = LOCOS.get(c.getKey());
            if (l == null || l.isDead() || l.getWorld().internal != w || l.getCurrentSpeed() == null) continue;
            double kmh = Math.abs(l.getCurrentSpeed().metric());
            if (kmh > c.getValue() && l.getThrottle() > 0) l.setThrottle(0);
            if (kmh > c.getValue() + 5 && l.getTrainBrakePos() < 0.5f) l.setTrainBrake(0.5f);
        }
    }

    private static void second(World w, long now) {
        FleetData f = FleetData.get(w);
        WearData wd = WearData.get(w);
        SimData sd = SimData.get(w);
        RailProps props = RailProps.get(w);
        AlarmManager am = AlarmManager.get(w);
        cam72cam.mod.world.World cw = cam72cam.mod.world.World.get(w);
        List<EntityRollingStock> all = cw.getEntities(EntityRollingStock.class);
        boolean rul = now % 200 == 0, exams = now % 100 == 0;
        for (EntityRollingStock s : all) {
            if (s.isDead() || s.getDefinition() == null) continue;
            UUID id = s.getUUID();
            FleetData.Unit u = f.unit(id);
            LIVE.put(id, u);
            u.seenTick = now;
            u.name = name(s);
            u.def = s.getDefinitionID();
            u.loco = s instanceof Locomotive;
            u.steam = s instanceof LocomotiveSteam;
            u.electric = u.loco && Electric.isElectric(s, props.of(id));
            u.passenger = s.getDefinition().acceptsPassengers();
            WearData.Record r = wd.peek(id);
            if (r != null) u.km = r.km;
            float kmh = s instanceof EntityMoveableRollingStock m && m.getCurrentSpeed() != null ? Math.abs((float) m.getCurrentSpeed().metric()) : 0;
            boolean running = kmh > 0.5f || s instanceof LocomotiveDiesel d && d.isTurnedOn();
            if (running) u.hours += 20 / DepotClock.TICKS_PER_HOUR;
            if (u.loco) {
                SimData.Car c = sd.peek(id);
                if (c != null && !c.faultLog.isEmpty()) {
                    int h = c.faultLog.getFirst().hashCode();
                    if (h != u.faultSeen) { if (u.faultSeen != 0) u.faults++; u.faultSeen = h; }
                }
            }
            // wear trend: a sample every half km
            if (r != null && (u.trend.isEmpty() || u.km - u.trend.get(u.trend.size() - 1)[0] >= 0.5)) {
                float[] t = new float[6];
                t[0] = (float) u.km;
                for (int p = 0; p < 5; p++) t[p + 1] = r.wear[p];
                u.trend.add(t);
                while (u.trend.size() > 16) u.trend.remove(0);
            }
            if (rul || u.rul == null) Predict.rul(w, u, r, sd.peek(id));
            if (u.loco) Telemetry.sample(w, u, s, now);                         // her 48-next #5: telemetry history + trends
            Condition.work(u, r);
            levels(w, f, am, s, id, u, r);
            if (s instanceof EntityMoveableRollingStock m) {
                try { if (Math.abs(m.prideBrakeFactor - u.brakeK) > 0.005f) m.prideBrakeFactor = u.brakeK; } catch (LinkageError ignored) { }
            }
            if (s instanceof LocomotiveDiesel d && !SimConfig.engineManagement && !SimConfig.tractionMotorHeating) {
                float k = Math.round(u.derateK * 50) / 50f;
                if (Math.abs(d.prideDerate - k) > 0.01f) d.prideDerate = k;
                d.prideCooling = u.coolK;
            }
            if (s instanceof LocomotiveSteam st && u.derateK < 1 && st.getThrottle() > u.derateK) st.setThrottle(u.derateK);
            if (exams) exam(w, f, am, s, id, u, r);
            if (DepotConfig.servicing && u.passenger && kmh > 0.5f) Servicing.ride(w, u, s, kmh);
        }
        // the whole train is held to its most restricted vehicle
        CAP.clear(); CAP_WHY.clear(); LOCOS.clear();
        for (EntityRollingStock s : all) {
            if (!(s instanceof Locomotive l) || l.isDead()) continue;
            float cap = Float.MAX_VALUE;
            String why = "";
            try {
                for (EntityCoupleableRollingStock car : l.getTrain()) {
                    FleetData.Unit cu = LIVE.get(car.getUUID());
                    if (cu != null && cu.cap < cap) { cap = cu.cap; why = cu.name + ": " + cu.capWhy; }
                }
            } catch (RuntimeException ignored) { }
            if (cap < Float.MAX_VALUE) { CAP.put(l.getUUID(), cap); CAP_WHY.put(l.getUUID(), why); LOCOS.put(l.getUUID(), l); }
        }
        LIVE.values().removeIf(u -> now - u.seenTick > 200);
        Crew.tick(w, f, now);
        f.markDirty();
    }

    /** announce and alarm a part entering a new condition level */
    private static void levels(World w, FleetData f, AlarmManager am, EntityRollingStock s, UUID id, FleetData.Unit u, WearData.Record r) {
        if (r == null) return;
        for (WearData.Part p : WearData.Part.values()) {
            int i = p.ordinal(), lv = Condition.level(r.wear[i]);
            if (lv == u.level[i]) continue;
            int was = u.level[i];
            u.level[i] = lv;
            String key = "cond:" + id + ":" + i;
            if (lv >= Condition.CRITICAL && DepotConfig.conditionEffects) {
                String what = String.format(Locale.ROOT, "CONDITION %s: %s %s %d%%", Condition.NAMES[lv], u.name, p.label, Math.round(r.wear[i]));
                String eff = effect(p, lv);
                am.raise(w, key, AlarmManager.Cat.MACHINE, lv == Condition.FAILED ? AlarmManager.Sev.MAJOR : AlarmManager.Sev.MINOR, what + " - " + eff, pos(s), true);
                Recorder.event(w, what);
                tell(w, s, (lv == Condition.FAILED ? "§c✖ " : "§6⚠ ") + what + "§7 - " + eff);
                u.note(w, p.label + " " + Condition.NAMES[lv] + " (" + Math.round(r.wear[i]) + "%)");
                if (lv == Condition.CRITICAL && u.depot != 0) Jobs.raise(w, id, u.name, p == WearData.Part.WHEELS ? Jobs.WHEELS : Jobs.REPAIR, p == WearData.Part.WHEELS ? -1 : i, u.depot, "condition critical");
            } else {
                am.clear(key);
                if (lv == Condition.DEGRADED && was < lv && DepotConfig.conditionEffects)
                    tell(w, s, "§e" + u.name + ": " + p.label + " DEGRADED (" + Math.round(r.wear[i]) + "%) - " + effect(p, lv));
            }
        }
    }

    static String effect(WearData.Part p, int lv) {
        return switch (p) {
            case ENGINE -> new String[]{"normal", "10% less power", "30% less power, runs hot", "65% less power - engine failing"}[lv];
            case ELECTRICAL -> new String[]{"normal", "traction motors run hotter", "20% less power, motors hot", "75% less power - traction failing"}[lv];
            case BRAKES -> new String[]{"normal", "brakes 8% weaker", "brakes 20% weaker, " + DepotConfig.critBrakesKmh + " km/h", "brakes 40% weaker, " + DepotConfig.failBrakesKmh + " km/h"}[lv];
            case WHEELS -> new String[]{"normal", "worn treads", "restricted to " + DepotConfig.critWheelsKmh + " km/h", "restricted to " + DepotConfig.failWheelsKmh + " km/h"}[lv];
            case BEARINGS -> new String[]{"normal", "bearings warm", "hot box - " + DepotConfig.critBearingsKmh + " km/h", "bearing failing - " + DepotConfig.failBearingsKmh + " km/h"}[lv];
        };
    }

    /** the exam plan: km, operating hours, days, faults since the last exam */
    private static void exam(World w, FleetData f, AlarmManager am, EntityRollingStock s, UUID id, FleetData.Unit u, WearData.Record r) {
        if (u.examTick == 0) { u.examTick = w.getTotalWorldTime(); u.examKm = u.km; u.examHours = u.hours; u.examFaults = u.faults; return; }   // first seen: counted from now
        if (r != null && r.servicedAt > u.examTick) {                    // fully serviced at a Maintenance Depot: that is an exam too
            u.examTick = r.servicedAt; u.examKm = u.km; u.examHours = u.hours; u.examFaults = u.faults;
            u.note(w, "full service at a Maintenance Depot (counts as an exam)");
        }
        String due = Predict.due(w, f, u);
        if (due.isEmpty()) { if (u.dueAlarm) { u.dueAlarm = false; am.clear("exam:" + id); } return; }
        if (u.dueAlarm) return;
        u.dueAlarm = true;
        u.note(w, "EXAM DUE - " + due);
        if (DepotConfig.examAlarms) am.raise(w, "exam:" + id, AlarmManager.Cat.GENERAL, AlarmManager.Sev.INFO, "EXAM DUE: " + u.name + " - " + due, pos(s), true);
        tell(w, s, "§6🔧 EXAM DUE: §f" + u.name + "§7 - " + due);
    }

    static BlockPos pos(EntityRollingStock s) { Vec3d p = s.getPosition(); return new BlockPos(p.x, p.y, p.z); }

    static void tell(World w, EntityRollingStock s, String msg) {
        Vec3d p = s.getPosition();
        for (EntityPlayer pl : w.playerEntities)
            if (pl.getDistanceSq(p.x, p.y, p.z) < 96 * 96) pl.sendMessage(new TextComponentString(msg));
    }

    public static String name(EntityRollingStock s) {
        if (s.tag != null && !s.tag.isEmpty()) return s.tag;
        String n = s.getDefinition() == null ? "Vehicle" : s.getDefinition().name();
        return n + " " + String.format(Locale.ROOT, "%05d", Math.abs(s.getUUID().hashCode()) % 100000);
    }

    /** server stopping: nothing of this session survives */
    public static void clear() { LIVE.clear(); CAP.clear(); CAP_WHY.clear(); LOCOS.clear(); }
}
