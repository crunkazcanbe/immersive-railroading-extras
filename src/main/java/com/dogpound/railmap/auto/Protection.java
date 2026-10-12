package com.dogpound.railmap.auto;

import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.tile.TileRail;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapConfig;
import com.dogpound.railmap.signal.TrackFollower;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Positive Train Control, the way American railroads have it: a computer riding along that
 * knows every signal and speed limit ahead, and takes the brakes away from a driver who is
 * about to run a red or overspeed.
 * <p>
 * For every locomotive a <em>person</em> is driving (driverless trains protect themselves),
 * twice a second: follow the track the way the switches lie, read the signals and signs facing
 * the train, and compare the train's speed with its braking curve. Over the curve by more than
 * {@link RailMapConfig#protectionMarginKmh} → a <b>penalty brake</b>: throttle off, brakes full,
 * held until the train is back under the curve. Reaching a Stop signal at speed → <b>emergency</b>.
 * The driver is told why, in the action bar.
 */
public final class Protection {
    private static final boolean IR = Loader.isModLoaded("immersiverailroading");

    private static final class State {
        double signKmh = Double.MAX_VALUE;
        boolean penalty;
        long lastWarn;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    public static void clear() {
        STATES.clear();
        DriverAids.clear();
        com.dogpound.railmap.sim.ConsistSim.clear();
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side.isClient() || !IR || !(RailMapConfig.trainProtection || DriverAids.active())) return;
        World world = e.world;
        long now = world.getTotalWorldTime();
        if (now % 10 != 5) return;
        RailwayData data = RailwayData.get(world);
        try {
            cam72cam.mod.world.World umc = cam72cam.mod.world.World.get(world);
            for (Locomotive loco : umc.getEntities(Locomotive.class)) {
                if (loco.isDead() || data.train(loco.getUUID()) != null) continue;
                check(world, umc, loco, now);
            }
        } catch (LinkageError | RuntimeException ex) {
            RailMap.LOG.warn("[irextras] train protection pass failed: {}", ex.toString());
        }
    }

    private static void check(World world, cam72cam.mod.world.World umc, Locomotive loco, long now) {
        boolean protect = RailMapConfig.trainProtection && RailMapConfig.protectManualTrains;
        double speed = Math.abs(loco.getCurrentSpeed().metric());
        State st = STATES.computeIfAbsent(loco.getUUID(), u -> new State());
        boolean standing = speed < 3;
        List<EntityPlayer> riders = Autopilot.riders(loco);
        if (!protect && riders.isEmpty()) return;                        // driver aids need a driver
        if (standing) {
            st.penalty = false;
            // still worth a look ahead for the cab display and the driver aids, if anyone's in the cab
            if (riders.isEmpty()) return;
        }
        Vec3d p = loco.getPosition();
        Vec3d v = loco.getVelocity();
        double vl = Math.sqrt(v.x * v.x + v.z * v.z);
        double dx, dz;
        if (vl >= 1e-4) {
            dx = v.x / vl;
            dz = v.z / vl;
        } else {
            // standing: look the way the reverser points
            Vec3d f = cam72cam.immersiverailroading.util.VecUtil.fromWrongYaw(loco.getReverser() < 0 ? -1 : 1, loco.getRotationYaw());
            dx = f.x;
            dz = f.z;
        }
        TileRail rail = TrackFollower.railUnder(umc, p.x, p.y, p.z);
        if (rail == null) return;

        double decel = RailMapConfig.autopilotBraking * 1.3;    // a driver may brake harder than the autopilot plans to
        double brakingDist = speed / 3.6 * speed / 3.6 / (2 * decel);
        Vec3i from = TrackPath.behind(rail, p.x, p.y, p.z, dx, dz);     // just past the back end of the piece we're on
        double budget = Math.max(200, brakingDist + 80);
        TrackPath path = TrackPath.ahead(umc, rail, from, budget);
        double cur = Math.max(0, path.distanceTo(p.x, p.y, p.z, 3.5));

        List<Wayside.Limit> limits = Wayside.read(world, path);
        double required = st.signKmh;
        String why = st.signKmh < Double.MAX_VALUE ? "speed limit" : null;
        boolean emergency = false;
        for (Wayside.Limit l : limits) {
            double dd = l.distance - cur;
            if (!l.stop && l.kmh < 1e6 && dd <= 0.5 && dd > -4) st.signKmh = l.kmh;
            if (dd < -1) continue;
            if (l.stop) {
                if (dd <= 1.5 && speed > 5) {
                    emergency = true;
                    why = l.what;
                    break;
                }
                double allow = Wayside.allowed(dd - 3, 0, decel);
                if (allow < required) { required = allow; why = l.what; }
            } else {
                double allow = Wayside.allowed(Math.max(0, dd), l.kmh, decel);
                if (allow < required) { required = allow; why = l.what; }
            }
        }

        // driver aids: vigilance, AWS, SPAD, advisory (they hold the brakes themselves when they must)
        String aidNote = null;
        if (DriverAids.active()) {
            try {
                aidNote = DriverAids.step(world, loco, path, cur, limits, speed, st.signKmh, decel, riders, now);
            } catch (RuntimeException ex) {
                RailMap.LOG.warn("[irextras] driver aids failed: {}", ex.toString());
            }
        }

        // Moving block: keep this train out of the back of the one in front (down to shunting speed, so you can couple)
        MovingBlock.Auth auth = null;
        boolean mbLimit = false;
        if (RailMapConfig.movingBlockManualTrains && protect) {
            double lag = speed / 3.6 * RailMapConfig.autopilotBrakeLagSeconds;   // IR's air brakes take a moment to bite
            auth = MovingBlock.compute(world, umc, loco, path, cur, speed, decel, lag, true, budget, now);
            double mb = Math.max(auth.permittedKmh, RailMapConfig.movingBlockShuntKmh);
            if (mb < required) {
                required = mb;
                why = "moving block - " + auth.limitedBy;
                mbLimit = true;
            }
        }
        com.dogpound.railmap.sim.CabData.plan(loco.getUUID(), limits, cur, path, "", -1);   // driver's display planning area
        if (RailMapConfig.cabDisplay)
            MovingBlock.sendCab(loco, auth, !protect ? "DRIVER AIDS" : auth != null ? "CBTC \u00b7 ATP" : "ATP", speed, required, st.signKmh, "", -1,
                    st.penalty ? "Protection holding the brakes" : aidNote != null ? aidNote : com.dogpound.railmap.safety.SigHooks.cabNote(loco.getUUID(), now));   // batch-3 #12: last balise telegram
        if (standing || !protect) return;

        // closing on a train ahead leaves no slack: step in at 2 km/h over, not the usual margin
        boolean over = speed > required + (mbLimit ? 2 : RailMapConfig.protectionMarginKmh);
        if (emergency || over) {
            loco.setThrottle(0);
            loco.setTrainBrake(1);
            if (!st.penalty || now - st.lastWarn > 60) {
                String msg = emergency
                        ? "⛔ TRAIN PROTECTION — EMERGENCY BRAKE: " + why
                        : String.format("⚠ TRAIN PROTECTION — penalty brake: %s (%d km/h, allowed %d)",
                                why == null ? "restriction ahead" : why, Math.round(speed), Math.round(required));
                for (EntityPlayer rider : Autopilot.riders(loco)) Autopilot.tell(rider, msg);
                st.lastWarn = now;
            }
            st.penalty = true;
        } else if (st.penalty && speed <= required - 2) {
            st.penalty = false;
            for (EntityPlayer rider : Autopilot.riders(loco)) {
                Autopilot.tell(rider, "✔ Train protection released — you have the train");
            }
        } else if (st.penalty) {
            loco.setThrottle(0);
            loco.setTrainBrake(1);
        }
    }

    /** For the board and OpenComputers: is protection holding this loco right now? */
    public static boolean penalised(UUID loco) {
        State s = STATES.get(loco);
        return s != null && s.penalty;
    }

    static List<EntityPlayer> ridersOf(Locomotive loco) {
        return Autopilot.riders(loco);
    }
}
