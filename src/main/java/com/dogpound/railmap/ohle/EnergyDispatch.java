package com.dogpound.railmap.ohle;

import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.entity.LocomotiveDiesel;
import com.dogpound.railmap.RailMapConfig;
import com.dogpound.railmap.block.TilePowerPost;
import com.dogpound.railmap.server.Electric;
import com.dogpound.railmap.server.RailProps;
import com.dogpound.railmap.signal.BlockCatenary;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Energy-aware dispatch (her ChatGPT list #33). The traction energy ledger - every electric loco's kWh drawn, kWh its
 * electric brake gave back to the line, kWh burnt in its resistors, and what it cost at the time-of-day tariff - kept
 * per loco and per operating period, so two timetables can be compared (energy mark / energy compare, console).
 * <p>
 * And, when a driverless electric train's dwell is over, a last check before it leaves:
 * <ol>
 *   <li><b>battery trains</b>: standing at a live Charging Station with the battery under 60 %: charge first;</li>
 *   <li><b>peak shift</b> (switch on with energy peakshift): a freight on FULL LOAD waits out the peak tariff, up to a
 *   game hour;</li>
 *   <li><b>weak feeders</b>: another train on the same section is making a high-power start and the section is weak
 *   (one feeder, or a train there got less than it asked last second): wait, so the two starts don't coincide;</li>
 *   <li><b>regen</b>: a train on the same section is coasting at speed (about to brake): wait a few seconds and start
 *   on its regenerated energy. A train braking right now: go now.</li>
 * </ol>
 * Holds never exceed energyHoldSeconds (charging: 5 min); every hold and its reason is counted in the period.
 */
public final class EnergyDispatch {
    private EnergyDispatch() {}

    private static final Map<UUID, Long> HOLD = new HashMap<>();

    public static int hour(World w) { return (int) (((w.getWorldTime() + 6000) % 24000) / 1000); }

    /** 0 off-peak, 1 shoulder, 2 peak */
    public static int band(int h) { return h >= 7 && h < 10 || h >= 17 && h < 20 ? 2 : h >= 10 && h < 17 ? 1 : 0; }

    public static final String[] BANDS = {"off-peak", "shoulder", "peak"};

    /** coins per MWh now */
    public static int tariff(World w) {
        int[] t = RailMapConfig.energyTariff;
        int b = band(hour(w));
        return t == null || t.length <= b ? 0 : t[b];
    }

    /** once a second per electric loco (Electric): what it drew from the line, gave back, and burnt (kW) */
    public static void ledger(World w, UUID id, double kwIn, double kwRegen, double kwRes) {
        if (kwIn <= 0 && kwRegen <= 0 && kwRes <= 0) return;
        OhleData d = OhleData.get(w);
        OhleData.Pan p = d.pan(id);
        OhleData.Period per = d.period();
        if (per.start == 0) per.start = w.getTotalWorldTime();
        double in = kwIn / 3600, reg = kwRegen / 3600, res = kwRes / 3600, cost = in * tariff(w) / 1000.0;
        boolean peak = band(hour(w)) == 2;
        p.kwhIn += in; p.kwhRegen += reg; p.kwhRes += res; p.cost += cost; if (peak) p.kwhPeak += in;
        per.kwhIn += in; per.kwhRegen += reg; per.kwhRes += res; per.cost += cost; if (peak) per.kwhPeak += in;
        d.markDirty();
    }

    /** the section of the wire over a loco (null: none) */
    static Ohle.Section sectionOver(World w, LocomotiveDiesel l) {
        BlockPos at = new BlockPos(l.getPosition().x, l.getPosition().y, l.getPosition().z);
        for (int dy = 2; dy <= 7; dy++)
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos q = at.add(dx, dy, dz);
                    if (Ohle.kindAt(w, q) == BlockCatenary.Kind.WIRE) return Ohle.section(w, q);
                }
        return null;
    }

    /**
     * Autopilot: the dwell is over. Returns why the train should wait a little longer, or null to go now.
     * freight = the stop's order is FULL LOAD (OpenTTD-style freight).
     */
    public static String hold(World w, UUID id, Locomotive loco, boolean freight, long now) {
        try {
            String r = why(w, id, loco, freight, now);
            if (r != null) return r;
        } catch (RuntimeException | LinkageError ex) {
            com.dogpound.railmap.RailMap.LOG.debug("[irextras] energy dispatch: {}", ex.toString());
        }
        Long since = HOLD.remove(id);
        OhleData d = OhleData.get(w);
        OhleData.Period per = d.period();
        per.departures++;
        if (since != null && now - since >= 20) { per.held++; per.heldSeconds += (int) ((now - since) / 20); }
        d.markDirty();
        return null;
    }

    private static String why(World w, UUID id, Locomotive loco, boolean freight, long now) {
        if (!RailMapConfig.energyDispatch || !(loco instanceof LocomotiveDiesel me)) return null;
        RailProps props = RailProps.get(w);
        RailProps.Props pr = props.of(id);
        if (!Electric.isElectric(me, pr)) return null;
        long since = HOLD.computeIfAbsent(id, k -> now);
        long held = (now - since) / 20;
        BlockPos at = new BlockPos(me.getPosition().x, me.getPosition().y, me.getPosition().z);
        // 1: battery trains charge at a charger before leaving
        if (pr.battery < 0.6f && held < 300)
            for (TilePowerPost t : TilePowerPost.all(w.provider.getDimension()))
                if (t.kind() == TilePowerPost.Kind.CHARGER && t.live() && t.where().distanceSq(at) <= (double) t.reach() * t.reach())
                    return "charging battery " + Math.round(pr.battery * 100) + "% → 60% before leaving";
        // 2: freight waits out the peak tariff
        if (freight && OhleData.get(w).peakShift && band(hour(w)) == 2 && held < 50)
            return "freight held out of the PEAK tariff (" + tariff(w) + "/MWh)";
        if (held >= RailMapConfig.energyHoldSeconds) return null;
        Ohle.Section mine = sectionOver(w, me);
        if (mine == null) return null;
        boolean weak = mine.feeders.size() <= 1;
        LocomotiveDiesel starting = null, coasting = null;
        for (LocomotiveDiesel o : cam72cam.mod.world.World.get(w).getEntities(LocomotiveDiesel.class)) {
            if (o.isDead() || o.getUUID().equals(id)) continue;
            RailProps.Props op = props.peek(o.getUUID());
            if (op == null || !Electric.isElectric(o, op)) continue;
            double kmh = o.getCurrentSpeed() == null ? 0 : Math.abs(o.getCurrentSpeed().metric());
            if (o.getPosition().distanceToSquared(me.getPosition()) > 1500.0 * 1500 || sectionOver(w, o) != mine) continue;
            if (op.kwRegen > 50) return null;                                       // braking now: start on its energy
            if (op.supplied < 0.95f && op.kwDraw > 0) weak = true;
            if (kmh < 25 && op.kwDraw > 500) starting = o;
            if (kmh > 30 && Math.abs(o.getThrottle()) < 0.02f && o.getTrainBrakePos() < 0.02f) coasting = o;
        }
        if (starting != null && weak) return "feeder busy: " + name(starting) + " is starting on " + mine.name() + " (" + held + "/" + RailMapConfig.energyHoldSeconds + " s)";
        if (coasting != null && held < 6) return "waiting for " + name(coasting) + " to brake - starting on its regen";
        return null;
    }

    static String name(LocomotiveDiesel l) { return l.getDefinition() == null ? "a train" : l.getDefinition().name(); }

    public static void clear() { HOLD.clear(); }
}
