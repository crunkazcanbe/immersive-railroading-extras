package com.dogpound.railmap.track;

import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.tile.TileRail;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.track.TrackData.Metric;
import com.dogpound.railmap.track.TrackData.Seg;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Track quality, geometry and deterioration (her ChatGPT list #13). Once a second every moving IR car wears the piece
 * of track under it. Damage per pass grows with the cube of the axle load (a 25 t-axle coal hopper does ~2x the damage
 * of a 20 t one and ~30x a 6 t-axle railcar), with speed, and with curvature for the rail. Each piece keeps:
 * top and line (geometry standard deviations), twist, gauge spread, rail head wear, rail surface (corrugation / RCF),
 * failed sleepers and ballast fouling, checked against alert / intervention / immediate-action limits. Past the
 * intervention limit the piece gets an automatic temporary speed restriction (TSR) that driverless trains brake for
 * (auto.Wayside) and drivers are held to.
 * <p>
 * Works trains are real IR stock with a tag: TAMPER (restores top / line / twist), GRINDER (rail surface), REGULATOR /
 * BALLAST / CLEANER (ballast), and TRC / INSPECT / RECORDING (a track recording car: inspects every piece it runs over).
 * Gangs fix spots with the Track Maintenance Kit; the Track Geometry Trolley inspects on foot.
 */
public final class TrackCondition {
    private static final boolean IR = Loader.isModLoaded("immersiverailroading");
    /** per loco: the piece it was last warned about (so the driver is told once per TSR) */
    private static final Map<UUID, Long> WARNED = new HashMap<>();
    /** per recording car: pieces recorded this run, last summary tick */
    private static final Map<UUID, int[]> TRC = new HashMap<>();

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side.isClient() || !IR) return;
        if (e.world.getTotalWorldTime() % 20 != 7) return;
        try {
            tick(e.world);
        } catch (LinkageError | RuntimeException ex) {
            RailMap.LOG.warn("[IR Extras] track condition tick failed: {}", ex.toString());
        }
    }

    private static void tick(World mc) {
        cam72cam.mod.world.World world = cam72cam.mod.world.World.get(mc);
        java.util.List<EntityRollingStock> all = world.getEntities(EntityRollingStock.class);
        if (all.isEmpty()) return;
        TrackData data = TrackData.get(mc);
        boolean dirty = false;
        long now = mc.getTotalWorldTime();
        for (EntityRollingStock s : all) {
            if (s.isDead()) continue;
            float kmh = 0;
            if (s instanceof EntityMoveableRollingStock m && m.getCurrentSpeed() != null) kmh = Math.abs((float) m.getCurrentSpeed().metric());
            Vec3d p = s.getPosition();
            TileRail piece = Rails.pieceAt(mc, p.x, p.y, p.z);
            if (piece == null) continue;
            BlockPos key = Rails.pos(piece);
            String tag = s.tag == null ? "" : s.tag.toUpperCase(Locale.ROOT);
            if (kmh > 0.3f) {
                Seg seg = data.seg(key);
                if (seg.lengthM == 0) { seg.lengthM = Rails.lengthM(piece); seg.radius = Rails.radius(piece); seg.gaugeMm = Rails.gaugeMm(piece); }
                seg.maxKmh = Math.max(seg.maxKmh, Math.round(kmh));
                double t = Rails.tonnes(s), axleT = t / Rails.axles(s), v = kmh / 3.6;
                seg.tonnes += t * v / Math.max(6, seg.lengthM);          // gross tonnes over this piece (fraction of a pass per second)
                if (TrackConfig.trackWear) wear(mc, seg, t * com.dogpound.railmap.asset.Design.trackFactor(s.getUUID()), axleT, kmh, key);   // rebuilt bogies curve harder / softer
                standardCheck(mc, seg, s, axleT, kmh, key);                                                // batch-3 #10: built for this traffic?
                if (kmh <= 12) dirty |= works(mc, seg, tag, kmh, s, now);
                dirty = true;
            }
            if (tag.contains("TRC") || tag.contains("INSPECT") || tag.contains("RECORDING")) {
                Seg seg = data.peek(key);
                if (seg == null) seg = data.seg(key);
                if (now - seg.inspectedAt > 100) { inspect(seg, Rails.name(s) + " (recording car)", now); recorded(mc, s, seg, key, now); dirty = true; }
            }
            if (s instanceof Locomotive loco && TrackConfig.trackSpeedRestrictions) enforce(mc, loco, key, kmh);
        }
        if (dirty) data.markDirty();
    }

    /** one second of a car on a piece */
    private static void wear(World mc, Seg seg, double t, double axleT, float kmh, BlockPos key) {
        double v = kmh / 3.6;
        TrackData.Standard st = seg.standard();                           // batch-3 #10: what the track was built to take
        double over = axleT > st.axleT ? Math.pow(axleT / st.axleT, 4) : 1;   // fourth-power law past the design axle load
        double fast = kmh > st.lineKmh ? 1 + (kmh / st.lineKmh - 1) * 3 : 1;   // dynamic loads past its line speed
        double gF = st.wear[0], rF = st.wear[1], sF = st.wear[2], bF = st.wear[3];
        double L = over * fast * (t / 80.0) * Math.pow(Math.max(2, axleT) / 20.0, 3) * (v / 22.0) * (0.6 + 0.4 * Math.pow(kmh / 100.0, 2)) * TrackConfig.trackWearRate;
        double curve = seg.radius > 0 ? 1 + 400.0 / seg.radius : 1;
        double wet = mc.isRainingAt(key.up(2)) ? 1.6 : 1;                 // wet beds pump fines up into the ballast
        double soft = 1 + seg.get(Metric.FOULING) / 60.0 + seg.get(Metric.SLEEPERS) / 50.0;   // fouled ballast / dead sleepers lose geometry faster
        seg.add(Metric.TOP, 0.00027 * L * soft * gF);
        seg.add(Metric.LINE, 0.00018 * L * soft * (curve > 1 ? 1.5 : 1) * gF);
        seg.add(Metric.TWIST, 0.0004 * L * soft * gF);
        seg.add(Metric.GAUGE, 0.00006 * L * (1 + seg.get(Metric.SLEEPERS) / 25.0) * curve * sF);
        seg.add(Metric.RAIL, 0.00005 * L * curve * rF * com.dogpound.railmap.ops6.Lubricators.rail(mc, key, curve));   // batch 6 E #23: greased gauge face
        seg.add(Metric.SURFACE, 0.003 * L * (kmh > 120 ? 1.8 : 1) * rF * (st == TrackData.Standard.METRO && curve > 1 ? 1.5 : 1));
        seg.add(Metric.SLEEPERS, 0.0012 * L * sF);
        if (bF > 0) seg.add(Metric.FOULING, 0.0016 * L * wet * bF);
    }

    /** per car: the piece it was last counted overloading / overspeeding (one count per pass, not per second) */
    private static final Map<UUID, Long> OVL = new HashMap<>(), OVS = new HashMap<>();

    /** batch-3 #10: axle load / speed against the piece's construction standard - counted per pass, alarmed */
    private static void standardCheck(World mc, Seg seg, EntityRollingStock s, double axleT, float kmh, BlockPos key) {
        TrackData.Standard st = seg.standard();
        if (axleT > st.axleT + 0.05) {
            Long was = OVL.put(s.getUUID(), key.toLong());
            if (was == null || was != key.toLong()) {
                seg.overloads++;
                AlarmManager.get(mc).raise(mc, "trackovl:" + key.toLong(), AlarmManager.Cat.SIGNAL, axleT > st.axleT * 1.25 ? AlarmManager.Sev.MAJOR : AlarmManager.Sev.MINOR,
                        String.format(Locale.ROOT, "AXLE OVERLOAD %s: %s %.1f t/axle on %s track (limit %.1f t) - wearing it out", Rails.xz(key), Rails.name(s), axleT, st.label.toLowerCase(Locale.ROOT), st.axleT), key, false);
            }
        } else OVL.remove(s.getUUID());
        if (kmh > st.lineKmh + 2) {
            Long was = OVS.put(s.getUUID(), key.toLong());
            if (was == null || was != key.toLong()) {
                seg.overspeeds++;
                if (seg.overspeeds % 10 == 1) Recorder.event(mc, String.format(Locale.ROOT, "OVERSPEED on %s track %s: %s at %d km/h (built for %d)", st.label.toLowerCase(Locale.ROOT), Rails.xz(key), Rails.name(s), Math.round(kmh), st.lineKmh));
            }
        } else OVS.remove(s.getUUID());
    }

    /** works trains (by IR stock tag) moving at working speed */
    private static boolean works(World mc, Seg seg, String tag, float kmh, EntityRollingStock s, long now) {
        String what = null;
        if (tag.contains("TAMPER")) {
            for (Metric m : new Metric[]{Metric.TOP, Metric.LINE, Metric.TWIST}) seg.set(m, m.fresh + (seg.get(m) - m.fresh) * 0.75);
            seg.add(Metric.FOULING, 0.2);                                    // tamping tines break a little ballast
            what = "TAMPED";
        } else if (tag.contains("GRINDER")) {
            seg.set(Metric.SURFACE, Math.max(0, seg.get(Metric.SURFACE) - 6));
            seg.add(Metric.RAIL, 0.01);                                      // grinding takes metal off the head
            what = "RAIL GROUND";
        } else if (tag.contains("REGULATOR") || tag.contains("BALLAST") || tag.contains("CLEANER")) {
            seg.set(Metric.FOULING, Math.max(Metric.FOULING.fresh, seg.get(Metric.FOULING) - 3));
            seg.set(Metric.TOP, Math.max(Metric.TOP.fresh, seg.get(Metric.TOP) - 0.02));   // consolidated shoulders hold the top
            what = "BALLAST CLEANED + REGULATED";
        }
        if (what == null) return false;
        if (now - seg.workedAt > 200 || !what.equals(seg.lastWork)) Recorder.event(mc, "TRACK WORK: " + what + " by " + Rails.name(s) + " at " + (int) s.getPosition().x + "," + (int) s.getPosition().z);
        seg.workedAt = now;
        seg.lastWork = what;
        return true;
    }

    /** an inspection: what the trolley / recording car measures (with instrument noise) is what the reports show */
    static void inspect(Seg seg, String by, long now) {
        seg.seen = new double[seg.v.length];
        java.util.Random r = new java.util.Random(now ^ Double.doubleToLongBits(seg.tonnes));
        for (int k = 0; k < seg.v.length; k++) seg.seen[k] = Math.max(0, seg.v[k] * (1 + (r.nextDouble() - 0.5) * 0.06));
        seg.inspectedAt = now;
        seg.inspectedBy = by;
    }

    private static void recorded(World mc, EntityRollingStock s, Seg seg, BlockPos key, long now) {
        int[] n = TRC.computeIfAbsent(s.getUUID(), k -> new int[]{0, 0, (int) (now & 0x7fffffff)});
        n[0]++;
        if (seg.band() >= 2) n[1]++;
        if (seg.band() >= 2) AlarmManager.get(mc).raise(mc, "trackdefect:" + key.toLong(), AlarmManager.Cat.SIGNAL, seg.band() == 3 ? AlarmManager.Sev.MAJOR : AlarmManager.Sev.MINOR,
                "TRACK DEFECT " + Rails.xz(key) + ": " + seg.worst().label + " " + fmt(seg.get(seg.worst())) + " " + seg.worst().unit + (seg.tsr() > 0 ? " - TSR " + seg.tsr() + " km/h" : ""), key, false);
        if ((int) (now & 0x7fffffff) - n[2] > 600) {
            n[2] = (int) (now & 0x7fffffff);
            tell(mc, s.getPosition(), "§b[TRC] " + Rails.name(s) + ": " + n[0] + " pieces recorded, " + n[1] + " over the intervention limit (console TRACK > Condition)");
        }
    }

    /** a manually driven loco over a TSR: tell the driver once, cut the throttle if well over */
    private static void enforce(World mc, Locomotive loco, BlockPos key, float kmh) {
        int lim = TrackHooks.tsrAt(mc, key);
        if (lim <= 0) { WARNED.remove(loco.getUUID()); return; }
        Long was = WARNED.get(loco.getUUID());
        if (was == null || was != key.toLong()) {
            WARNED.put(loco.getUUID(), key.toLong());
            if (kmh > lim) tell(mc, loco.getPosition(), "§6⚠ TSR " + lim + " km/h - " + TrackHooks.tsrWhy(mc, key) + " §7(" + Math.round(kmh) + " km/h)");
        }
        if (TrackConfig.tsrEnforceMarginKmh > 0 && kmh > lim + TrackConfig.tsrEnforceMarginKmh && loco.getThrottle() > 0) loco.setThrottle(0);
    }

    static void tell(World mc, Vec3d p, String msg) {
        for (EntityPlayer pl : mc.playerEntities)
            if (pl.getDistanceSq(p.x, p.y, p.z) < 40 * 40) pl.sendMessage(new TextComponentString(msg));
    }

    public static String fmt(double v) { return v >= 10 ? String.valueOf(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v); }

    /** one line per metric for chat / books: value, unit and the band it is in */
    public static String report(Seg s, boolean live) {
        double[] v = live || s.seen == null ? s.v : s.seen;
        StringBuilder b = new StringBuilder();
        for (Metric m : Metric.values()) {
            int band = m.band(v[m.ordinal()]);
            if (b.length() > 0) b.append("§8 · ");
            b.append(band == 3 ? "§c" : band == 2 ? "§6" : band == 1 ? "§e" : "§a").append(m.label).append(' ').append(fmt(v[m.ordinal()])).append(' ').append(m.unit);
        }
        return b.toString();
    }
}
