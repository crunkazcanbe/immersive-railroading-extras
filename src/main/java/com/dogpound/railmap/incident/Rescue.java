package com.dogpound.railmap.incident;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.block.TileMaintenanceDepot;
import com.dogpound.railmap.sim.ConsistSim;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.util.*;

/**
 * Train rescue and towing (her ChatGPT list #68). A disabled train asks for assistance; the rescue plan works out
 * what failed (traction, brakes, or damaged after a derailment), the tow speed limit for it, and checks every engine
 * on the railway: can it start the combined train on this gradient (tractive effort vs mass x grade + rolling
 * resistance, with a 30 % starting margin), and can the combined train stop (braked weight: a failed-brake train
 * adds none of its own)? The best fit is assigned; once it is coupled the tow is watched against its speed limit
 * (over it: alarm, and towEnforce shuts the rescue engine's throttle), and the rescue ends when the failed train is
 * stood clear of the incident - stabled in a siding, or in a depot if a Maintenance Depot is near.
 */
public final class Rescue {
    private Rescue() {}

    public static final class Cand {
        public UUID id;
        public String name = "";
        public double dist, teKn, needKn, brakeT, needBrakeT;
        public boolean ok;
        public String why = "";
    }

    public static final class Plan {
        public String kind = "TRACTION";
        public double tonnes, grade, brakedPct;
        public int towKmh;
        public final List<Cand> cands = new ArrayList<>();
        public List<String> lines() {
            List<String> l = new ArrayList<>();
            l.add("Failure: " + kind + " - tow limit " + towKmh + " km/h");
            l.add(String.format(Locale.ROOT, "Failed train %.0f t on %.1f %% grade, braked %.0f %%", tonnes, grade, brakedPct));
            switch (kind) {
                case "BRAKES": l.add("Brakes isolated: rescue engine brakes the whole train - couple on the DOWNHILL end, walking pace through points"); break;
                case "DAMAGED": l.add("Damaged vehicle: examine bogies + couplers first, no faster than walking pace over points"); break;
                default: l.add("Traction failure: couple, connect brake pipe, test brakes, then tow"); break;
            }
            for (int i = 0; i < Math.min(4, cands.size()); i++) {
                Cand c = cands.get(i);
                l.add(String.format(Locale.ROOT, "%s %s %.0f m: TE %.0f/%.0f kN, brake %.0f/%.0f t%s", c.ok ? "OK " : "NO ", c.name, c.dist, c.teKn, c.needKn, c.brakeT, c.needBrakeT,
                        c.why.isEmpty() ? "" : " - " + c.why));
            }
            if (cands.isEmpty()) l.add("No engine on the railway");
            return l;
        }
    }

    /** the disabled train's lead unit (a loco if there is one) */
    public static EntityCoupleableRollingStock failed(World w, IncidentLog.Rec r) {
        cam72cam.mod.world.World umc = cam72cam.mod.world.World.get(w);
        EntityCoupleableRollingStock any = null;
        for (String u : r.stock) {
            try {
                for (EntityCoupleableRollingStock s : umc.getEntities(EntityCoupleableRollingStock.class)) {
                    if (!s.getUUID().toString().equals(u)) continue;
                    if (s instanceof Locomotive) return s;
                    if (any == null) any = s;
                }
            } catch (RuntimeException ignored) { }
        }
        return any;
    }

    public static Plan plan(World w, IncidentLog.Rec r, EntityCoupleableRollingStock failed) {
        Plan p = new Plan();
        ConsistSim.Summary f = ConsistSim.summary(w, failed);
        p.tonnes = f.tonnes; p.grade = f.gradePct; p.brakedPct = f.brakedPct;
        if (r.type == IncidentLog.Type.WRECK) { p.kind = "DAMAGED"; p.towKmh = IncidentConfig.towDamagedKmh; }
        else if (f.brakedPct < IncidentConfig.brakeFailPct) { p.kind = "BRAKES"; p.towKmh = IncidentConfig.towBrakeKmh; }
        else { p.kind = "TRACTION"; p.towKmh = IncidentConfig.towTractionKmh; }
        Set<UUID> mine = new HashSet<>();
        for (EntityCoupleableRollingStock s : failed.getTrain()) mine.add(s.getUUID());
        Vec3d at = failed.getPosition();
        double slope = cam72cam.immersiverailroading.Config.ConfigBalance.slopeMultiplier;
        double grade = Math.max(Math.abs(f.gradePct), 0.5);                       // assume a ruling grade of at least 0.5 %
        for (Locomotive l : cam72cam.mod.world.World.get(w).getEntities(Locomotive.class)) {
            if (l.isDead() || mine.contains(l.getUUID()) || l.wreck != 0) continue;
            Vec3d q = l.getPosition();
            double d = Math.hypot(q.x - at.x, q.z - at.z);
            if (d > 3000) continue;
            ConsistSim.Summary s = ConsistSim.summary(w, l);
            Cand c = new Cand();
            c.id = l.getUUID();
            c.name = l.tag != null && !l.tag.isEmpty() ? l.tag : l.getDefinition() == null ? "engine" : l.getDefinition().name();
            c.dist = d;
            double tonnes = s.tonnes + f.tonnes;
            c.teKn = s.teAvailN / 1000;
            c.needKn = tonnes * 1000 * 9.81 * (grade / 100 * slope + 0.0015) * 1.3 / 1000;
            double ownBrake = s.brakedPct / 100 * s.tonnes, theirs = p.kind.equals("BRAKES") ? 0 : f.brakedPct / 100 * f.tonnes;
            c.brakeT = ownBrake + theirs;
            c.needBrakeT = tonnes * Math.min(0.9, 0.5 + grade * 0.05);
            if (s.locos == 0 || c.teKn <= 0) { c.ok = false; c.why = "no traction"; }
            else if (c.teKn < c.needKn) { c.ok = false; c.why = "not enough tractive effort"; }
            else if (c.brakeT < c.needBrakeT) { c.ok = false; c.why = "not enough brake for the combined train"; }
            else c.ok = true;
            p.cands.add(c);
        }
        p.cands.sort((a, b) -> a.ok != b.ok ? (a.ok ? -1 : 1) : Double.compare(a.dist, b.dist));
        return p;
    }

    /** assign the best compatible engine (or a named one) */
    public static String assign(World w, IncidentLog log, IncidentLog.Rec r, String who) {
        EntityCoupleableRollingStock f = failed(w, r);
        if (f == null) return "§7No failed train found for incident #" + r.id;
        Plan p = plan(w, r, f);
        r.plan.clear();
        r.plan.addAll(p.lines());
        r.failKind = p.kind;
        r.towKmh = p.towKmh;
        r.needs |= IncidentLog.RESCUE;
        Cand best = p.cands.isEmpty() || !p.cands.get(0).ok ? null : p.cands.get(0);
        if (best == null) { log.log(w, r, "RESCUE: no compatible engine (" + (p.cands.isEmpty() ? "none on the railway" : p.cands.get(0).why) + ")"); return "§cNo compatible rescue engine"; }
        r.rescue = best.id.toString();
        r.rescueName = best.name;
        r.assigned |= IncidentLog.RESCUE;
        log.log(w, r, "RESCUE ENGINE " + best.name + " assigned by " + who + String.format(Locale.ROOT, " (%.0f m away, TE %.0f kN for %.0f kN), %s failure, tow limit %d km/h",
                best.dist, best.teKn, best.needKn, p.kind.toLowerCase(Locale.ROOT), p.towKmh));
        log.stage(w, r, IncidentLog.Stage.RESPONDING, "rescue engine on its way");
        return "§eRescue engine " + best.name + " assigned - " + p.kind + " failure, tow at " + p.towKmh + " km/h";
    }

    /** once a second for an incident with a rescue engine: coupled? overspeed? clear of the site and stood? */
    static void watch(World w, IncidentLog log, IncidentLog.Rec r) {
        if (r.rescue.isEmpty() || r.stabled) return;
        EntityCoupleableRollingStock f = failed(w, r);
        if (f == null) return;
        boolean coupled = false;
        Locomotive rescuer = null;
        for (EntityCoupleableRollingStock s : f.getTrain()) if (s.getUUID().toString().equals(r.rescue)) { coupled = true; if (s instanceof Locomotive l) rescuer = l; }
        if (coupled && !r.coupled) {
            r.coupled = true;
            log.log(w, r, "RESCUE ENGINE COUPLED - brake test, then tow at " + r.towKmh + " km/h max");
            log.stage(w, r, IncidentLog.Stage.ON_SCENE, "rescue engine coupled");
            log.stage(w, r, IncidentLog.Stage.CONTAINED, "train secured");
            log.stage(w, r, IncidentLog.Stage.RECOVERY, "under tow");
        }
        if (!r.coupled) return;
        double kmh = Math.abs(f.getCurrentSpeed().metric());
        if (kmh > r.towKmh + 3) {
            AlarmManager.get(w).raise(w, "tow:" + r.id, AlarmManager.Cat.INCIDENT, AlarmManager.Sev.MAJOR,
                    String.format(Locale.ROOT, "TOW OVERSPEED incident #%d: %.0f km/h, limit %d", r.id, kmh, r.towKmh), new net.minecraft.util.math.BlockPos(f.getPosition().x, f.getPosition().y, f.getPosition().z), false);
            if (IncidentConfig.towEnforce && rescuer != null) { rescuer.setThrottle(0); rescuer.setTrainBrake(Math.max(rescuer.getTrainBrakePos(), 0.4f)); }
        }
        Vec3d p = f.getPosition();
        double away = Math.hypot(p.x - r.x, p.z - r.z);
        if (kmh < 0.5 && away > r.r + 16) {
            String where = "a siding";
            for (TileEntity te : w.loadedTileEntityList)
                if (te instanceof TileMaintenanceDepot && te.getPos().distanceSq(p.x, p.y, p.z) < 48 * 48) { where = "the depot at " + te.getPos().getX() + "," + te.getPos().getZ(); break; }
            r.stabled = true;
            log.log(w, r, "RESCUED TRAIN STABLED in " + where + String.format(Locale.ROOT, " (%.0f m from the site)", away));
        }
    }
}
