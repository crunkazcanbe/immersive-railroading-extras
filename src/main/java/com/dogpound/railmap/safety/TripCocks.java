package com.dogpound.railmap.safety;

import cam72cam.immersiverailroading.entity.Locomotive;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.ConsoleControl;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.*;

/**
 * What a raised train-stop arm does to a train (her batch-3 list #11): the arm strikes the train's trip cock, the brake
 * pipe vents - throttle off, emergency brake, held - until the train has stopped AND the trip valve has been reset
 * (the driver's acknowledge key once stood, or {@code /irextras sig trip reset}). Driverless trains are held on the
 * console and reset themselves after 10 s at a stand. Memory only: a restart lets the brakes off (like console holds).
 */
public final class TripCocks {
    private TripCocks() {}

    static final class Trip {
        final UUID loco;
        final String what, label;
        final BlockPos at;
        final long tick;
        final boolean driverless;
        long stoodSince;
        Trip(UUID loco, String label, String what, BlockPos at, long tick, boolean driverless) { this.loco = loco; this.label = label; this.what = what; this.at = at; this.tick = tick; this.driverless = driverless; }
    }

    private static final Map<UUID, Trip> TRIPPED = new HashMap<>();

    static boolean any() { return !TRIPPED.isEmpty(); }

    /** is this loco's brake vented by a train stop (the cab's TRIPPED lamp) */
    public static boolean tripped(UUID loco) { return TRIPPED.containsKey(loco); }

    static void tick(World w) { }

    static void fast(World w, List<SigA.Pass> passes, long now) {
        if (SigConfig.tripCockBrake) {
            List<TileTripCock> stops = SigTicker.tiles(w, TileTripCock.class);
            for (TileTripCock t : stops) {
                BlockPos p = t.getPos();
                for (SigA.Pass ps : passes) {
                    UUID id = ps.loco.getUUID();
                    // SCTS: a slow train approaching within 60 m drops the arm for itself
                    if (t.ctl.get("s_mode") == 1 && t.powered() && !t.isolated() && ps.speed > 0.5 && !TileTripCock.sctsTrips(ps.speed, t.scts())
                            && Geo.d2(ps.x, ps.z, p.getX() + 0.5, p.getZ() + 0.5) < 60 * 60 && Math.abs(ps.y - p.getY()) < 4) t.lowerFor(30);
                    if (TRIPPED.containsKey(id) || !t.armUp() || !ps.passed(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 3)) continue;
                    trip(w, t, ps.loco, ps.speed, now);
                }
            }
        }
        // hold the brakes of every tripped train until it stands and the valve is reset
        if (TRIPPED.isEmpty()) return;
        int dim = w.provider.getDimension();
        for (Iterator<Trip> it = TRIPPED.values().iterator(); it.hasNext(); ) {
            Trip tr = it.next();
            Locomotive l = null;
            for (SigA.Pass ps : passes) if (ps.loco.getUUID().equals(tr.loco)) { l = ps.loco; break; }
            if (l == null) continue;                                             // not loaded: stays tripped
            l.setThrottle(0);
            l.setTrainBrake(1);
            double speed = Math.abs(l.getCurrentSpeed().metric());
            if (speed < 1) { if (tr.stoodSince == 0) tr.stoodSince = now; } else tr.stoodSince = 0;
            if (tr.driverless) {
                ConsoleControl.hold(dim, tr.loco, true);
                if (tr.stoodSince > 0 && now - tr.stoodSince > 200) { it.remove(); release(w, tr, "auto reset after 10 s at a stand (driverless)"); }
            }
        }
    }

    private static void trip(World w, TileTripCock t, Locomotive l, double speed, long now) {
        String sig = t.ctl.get("s_mode") == 1 ? "speed trap " + t.scts() + " km/h" : "signal " + t.signalName() + " at danger";
        String label = SigA.label(l);
        boolean dl = SigA.driverless(w, l.getUUID());
        TRIPPED.put(l.getUUID(), new Trip(l.getUUID(), label, sig, t.getPos(), now, dl));
        l.setThrottle(0);
        l.setTrainBrake(1);
        if (dl) ConsoleControl.hold(w.provider.getDimension(), l.getUUID(), true);
        String text = String.format(Locale.ROOT, "TRAIN STOP TRIP: %s tripped at %d km/h by the train stop at %d,%d (%s)", label, Math.round(speed), t.getPos().getX(), t.getPos().getZ(), sig);
        t.tripped(label + " at " + Math.round(speed) + " km/h (" + sig + ")");
        AlarmManager.get(w).raise(w, "trip:" + l.getUUID(), AlarmManager.Cat.INCIDENT, AlarmManager.Sev.CRITICAL, text, t.getPos(), true);
        Recorder.event(w, text);
        w.playSound(null, t.getPos(), com.dogpound.railmap.ModSounds.BREAKER_TRIP, SoundCategory.BLOCKS, 1.0f, 0.7f);
        List<EntityPlayer> riders = SigA.riders(l);
        for (EntityPlayer p : riders) {
            p.sendMessage(new TextComponentString("§4⛔ " + text + " - EMERGENCY BRAKE. Stop, contact the signaller, then press the acknowledge key to reset the trip valve."));
            w.playSound(null, p.getPosition(), com.dogpound.railmap.ModSounds.ALARM_HORN, SoundCategory.PLAYERS, 1.0f, 0.9f);
        }
        SigHooks.incident(w, riders, "TRIP", text);
        com.dogpound.railmap.api.QuestEvents.fire(w, riders.isEmpty() ? null : riders.get(0), t.getPos(), "train_tripped", "speed", Math.round(speed), "mode", t.modeName(), "driverless", dl);
    }

    private static void release(World w, Trip tr, String how) {
        if (tr.driverless) ConsoleControl.hold(w.provider.getDimension(), tr.loco, false);
        AlarmManager.get(w).clear("trip:" + tr.loco);
        Recorder.event(w, "TRAIN STOP: trip valve of " + tr.label + " reset - " + how);
    }

    /** the cab acknowledge key (DriverAids.ack): resets the trip valve once the train stands; null = not tripped */
    public static String driverReset(World w, Locomotive l, String who) {
        Trip tr = TRIPPED.get(l.getUUID());
        if (tr == null) return null;
        if (Math.abs(l.getCurrentSpeed().metric()) >= 1) return "§cTripped by a train stop - stop the train first";
        TRIPPED.remove(l.getUUID());
        release(w, tr, "by the driver " + who);
        return "§eTrip valve reset - proceed only with the signaller's authority";
    }

    /** command / console reset: one train (uuid or label) or all; refused while it is still moving */
    static String commandReset(World w, String which, String who) {
        int n = 0, moving = 0;
        for (Iterator<Trip> it = TRIPPED.values().iterator(); it.hasNext(); ) {
            Trip tr = it.next();
            if (!which.equalsIgnoreCase("all") && !tr.loco.toString().equals(which) && !tr.label.equalsIgnoreCase(which)) continue;
            Locomotive l = SigA.loco(w, tr.loco);
            if (l != null && Math.abs(l.getCurrentSpeed().metric()) >= 1) { moving++; continue; }
            it.remove();
            release(w, tr, "by " + who);
            n++;
        }
        return n == 0 && moving == 0 ? "§7No tripped train" : "§aReset " + n + " trip valve(s)" + (moving > 0 ? " §c(" + moving + " still moving - stop first)" : "");
    }

    static List<String> trippedLines(World w) {
        List<String> l = new ArrayList<>();
        for (Trip tr : TRIPPED.values()) l.add(tr.label + " TRIPPED at " + tr.at.getX() + "," + tr.at.getZ() + " (" + tr.what + ") " + (w.getTotalWorldTime() - tr.tick) / 20 + " s ago");
        return l;
    }

    static List<String[]> trippedRows(World w) {
        List<String[]> l = new ArrayList<>();
        for (Trip tr : TRIPPED.values())
            l.add(new String[]{"a.trip", tr.loco.toString(), tr.label, tr.at.getX() + "," + tr.at.getZ(), tr.what, String.valueOf((w.getTotalWorldTime() - tr.tick) / 20), tr.driverless ? "1" : "0"});
        return l;
    }
}
