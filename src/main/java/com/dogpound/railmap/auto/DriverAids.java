package com.dogpound.railmap.auto;

import cam72cam.immersiverailroading.entity.Locomotive;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.server.StationData;
import com.dogpound.railmap.sim.ConsistSim;
import com.dogpound.railmap.sim.SimConfig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.*;

/**
 * Driver aids for trains a player drives (her ChatGPT list item 9), run by train protection twice a second on the same
 * look-ahead (signals, signs, stations along the track the way the switches lie):
 * <ul>
 *   <li><b>vigilance device</b> (DSD / DRA): no driver action (throttle, brake, reverser, independent brake, or the
 *       acknowledge key) for N seconds while moving = the cab buzzes; no acknowledgement within a few seconds = penalty
 *       brake, held until the train stands and the driver acknowledges;</li>
 *   <li><b>AWS</b>: passing the magnet ~180 m before a signal rings a bell for a clear aspect, sounds the horn for a
 *       restrictive one; the horn must be acknowledged (then the "sunflower" shows black-and-yellow) or the brakes go on;</li>
 *   <li><b>SPAD</b>: running past a signal at danger: emergency brake, critical alarm on the annunciator / console, an
 *       event in the recorder, held until stopped and acknowledged;</li>
 *   <li><b>driver advisory</b> (energy-efficient driving): from the next restriction / red signal / station and the
 *       train's own braking and coasting, the cab advises POWER, CRUISE, COAST or BRAKE and the speed to aim for.</li>
 * </ul>
 */
public final class DriverAids {
    private DriverAids() {}

    public static final int VIG_ALERT = 1, VIG_PENALTY = 1 << 1, AWS_HORN = 1 << 2, AWS_SUNFLOWER = 1 << 3, AWS_BELL = 1 << 4,
            AWS_PENALTY = 1 << 5, SPAD = 1 << 6,
            /** batch-3 #13 cab signal / balise warning sounding; brake demand for not acknowledging it; #11 tripped by a train stop */
            CAB_SIG = 1 << 7, CAB_PENALTY = 1 << 24, TRIPPED = 1 << 25;
    /** every bit that wants the acknowledge key now (warnings) / that holds the brakes (penalties) */
    public static final int WARNINGS = VIG_ALERT | AWS_HORN | CAB_SIG, PENALTIES = VIG_PENALTY | AWS_PENALTY | SPAD | CAB_PENALTY | TRIPPED;

    static final class State {
        float[] ctl;
        long lastAction;
        int vig;                       // 0 quiet, 1 alert, 2 penalty
        long vigAt;
        int aws;                       // 0 none, 1 horn sounding, 2 sunflower (acknowledged restrictive)
        long awsDeadline, bellUntil;
        boolean awsPenalty, spad;
        String spadWhat = "";
        final Map<BlockPos, double[]> sigs = new HashMap<>();      // signal -> {distance, stop?1:0} at the last look
        double lastCur = -1;
        String advice = "";
        double adviceKmh = -1;
        long seen;
        ConsistSim.Summary summary;
        // batch-3 #13 cab signalling: 0 quiet, 1 warning (acknowledge before cabDeadline), 2 brake demand
        int cab;
        long cabDeadline;
        String cabWhat = "";
        int cabRank = -1;                                          // continuous cab signal: 0 clear, 1 caution, 2 stop
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    public static void clear() { STATES.clear(); }

    static State state(UUID loco) { return STATES.get(loco); }

    /** cab bits for the display */
    public static int bits(UUID loco, long now) {
        int b = com.dogpound.railmap.safety.TripCocks.tripped(loco) ? TRIPPED : 0;
        State s = STATES.get(loco);
        if (s == null || now - s.seen > 40) return b;
        if (s.cab == 1) b |= CAB_SIG;
        if (s.cab == 2) b |= CAB_PENALTY;
        if (s.vig == 1) b |= VIG_ALERT;
        if (s.vig == 2) b |= VIG_PENALTY;
        if (s.aws == 1) b |= AWS_HORN;
        if (s.aws == 2) b |= AWS_SUNFLOWER;
        if (s.bellUntil > now) b |= AWS_BELL;
        if (s.awsPenalty) b |= AWS_PENALTY;
        if (s.spad) b |= SPAD;
        return b;
    }

    public static String advice(UUID loco) { State s = STATES.get(loco); return s == null ? "" : s.advice; }
    public static double adviceKmh(UUID loco) { State s = STATES.get(loco); return s == null ? -1 : s.adviceKmh; }
    public static ConsistSim.Summary summary(UUID loco) { State s = STATES.get(loco); return s == null ? null : s.summary; }

    public static boolean active() {
        return SimConfig.vigilanceSeconds > 0 || SimConfig.awsAckSeconds > 0 || SimConfig.spadDetection || SimConfig.driverAdvisory;
    }

    /** true while an aid is holding the brakes on this train */
    public static boolean braking(UUID loco) {
        State s = STATES.get(loco);
        return s != null && (s.vig == 2 || s.awsPenalty || s.spad || s.cab == 2);
    }

    /**
     * batch-3 #13: a restrictive balise telegram (or a cab signal downgrade) for a driven train: the cab alerter sounds
     * and the driver has {@link com.dogpound.railmap.safety.SigConfig#cabAckSeconds} to acknowledge.
     */
    public static void cabWarn(UUID loco, String what, long now) {
        double secs = com.dogpound.railmap.safety.SigConfig.cabAckSeconds;
        if (secs <= 0) return;
        State st = STATES.computeIfAbsent(loco, u -> new State());
        if (st.cab == 2) return;                                   // already braking for the last one
        st.cab = 1;
        st.cabDeadline = now + Math.round(secs * 20);
        st.cabWhat = what == null ? "" : what;
    }

    /** one line for /irextras sig cab */
    public static String cabStatus(UUID loco, long now) {
        State s = STATES.get(loco);
        if (s == null) return "no driver aids state (not driven, or driver aids off)";
        return (s.cab == 0 ? "cab signal quiet" : s.cab == 1 ? "WARNING - acknowledge within " + Math.max(0, (s.cabDeadline - now) / 20) + " s: " + s.cabWhat
                : "BRAKE DEMAND - not acknowledged: " + s.cabWhat) + " · cab aspect " + (s.cabRank < 0 ? "-" : new String[]{"CLEAR", "CAUTION", "STOP"}[s.cabRank])
                + (com.dogpound.railmap.safety.TripCocks.tripped(loco) ? " · TRIPPED by a train stop" : "");
    }

    /**
     * One look (twice a second) for a driven train. Returns a cab note, or null.
     *
     * @param limits    the signals / signs ahead from Wayside.read
     * @param cur       where the loco is along the path
     * @param lineKmh   the speed limit in force (Double.MAX_VALUE = none)
     */
    static String step(World world, Locomotive loco, TrackPath path, double cur, List<Wayside.Limit> limits, double speed,
                       double lineKmh, double decel, List<EntityPlayer> riders, long now) {
        State st = STATES.computeIfAbsent(loco.getUUID(), u -> new State());
        st.seen = now;
        boolean driven = !riders.isEmpty();
        float[] c = controls(loco);
        if (st.ctl == null) { st.ctl = c; st.lastAction = now; }
        if (!Arrays.equals(c, st.ctl)) st.lastAction = now;           // the driver did something
        if (st.vig == 1 && st.lastAction > st.vigAt) st.vig = 0;
        String note = null;

        try {
            st.summary = ConsistSim.summary(world, loco);
        } catch (RuntimeException ex) {
            st.summary = null;
        }

        // ---- vigilance
        if (SimConfig.vigilanceSeconds > 0 && driven && speed > 3) {
            if (st.vig == 0 && now - st.lastAction > SimConfig.vigilanceSeconds * 20L) {
                st.vig = 1;
                st.vigAt = now;
            } else if (st.vig == 1 && now - st.vigAt > SimConfig.vigilanceAckSeconds * 20L) {
                st.vig = 2;
                Recorder.event(world, label(loco) + ": vigilance not acknowledged - penalty brake at " + Math.round(speed) + " km/h");
                for (EntityPlayer p : riders) tell(p, "§c⛔ VIGILANCE - no response: PENALTY BRAKE. Stop, then press the acknowledge key");
            }
        } else if (st.vig == 1) st.vig = 0;
        if (speed <= 3 && st.vig != 2) st.lastAction = now;             // the timer runs while the train moves

        // ---- signals: AWS magnets and SPADs
        double moved = st.lastCur < 0 ? 0 : speed / 3.6 * 0.5;
        Map<BlockPos, double[]> nowSigs = new HashMap<>();
        for (Wayside.Limit l : limits) {
            if (!l.signal || l.where == null) continue;
            double dd = l.distance - cur;
            double[] was = nowSigs.get(l.where);
            if (was == null || dd < was[0]) nowSigs.put(l.where, new double[]{dd, l.stop ? 1 : 0, l.kmh});
        }
        if (SimConfig.awsAckSeconds > 0 && driven) {
            double mag = SimConfig.awsMagnetM;
            for (Map.Entry<BlockPos, double[]> e : nowSigs.entrySet()) {
                double[] prev = st.sigs.get(e.getKey());
                double dd = e.getValue()[0];
                if (prev == null || prev[0] <= mag || dd > mag || dd < 0) continue;      // crossing the magnet this look
                boolean restrictive = e.getValue()[1] > 0 || e.getValue()[2] < Math.min(lineKmh, 1e6);
                if (restrictive) {
                    st.aws = 1;
                    st.awsDeadline = now + Math.round(SimConfig.awsAckSeconds * 20);
                } else {
                    st.aws = 0;
                    st.bellUntil = now + 20;
                }
            }
            if (st.aws == 1 && now > st.awsDeadline && !st.awsPenalty) {
                st.awsPenalty = true;
                Recorder.event(world, label(loco) + ": AWS warning not acknowledged - brake demand");
                com.dogpound.railmap.safety.SigHooks.incident(world, riders, "AWS", label(loco) + ": AWS warning not acknowledged");
                for (EntityPlayer p : riders) tell(p, "§c⛔ AWS not acknowledged: BRAKE DEMAND. Stop, then acknowledge");
            }
        }
        if (SimConfig.spadDetection && !st.spad) {
            for (Map.Entry<BlockPos, double[]> e : st.sigs.entrySet()) {
                double[] prev = e.getValue();
                if (prev[1] < 1) continue;                               // it was not at danger
                double[] nw = nowSigs.get(e.getKey());
                boolean passed = nw == null ? prev[0] <= moved + 3 && moved > 0.05 : nw[0] < -0.5 && nw[1] > 0;
                if (!passed) continue;
                st.spad = true;
                st.spadWhat = signalName(world, e.getKey());
                String text = String.format(Locale.ROOT, "SPAD: %s passed %s at DANGER at %d km/h", label(loco), st.spadWhat, Math.round(speed));
                AlarmManager.get(world).raise(world, "spad:" + loco.getUUID(), AlarmManager.Cat.INCIDENT, AlarmManager.Sev.CRITICAL, text, e.getKey(), false);
                com.dogpound.railmap.safety.SigHooks.incident(world, riders, "SPAD", text);
                for (EntityPlayer p : riders) {
                    if (p instanceof EntityPlayerMP mp) mp.sendMessage(new TextComponentString("§4⛔ " + text + " - EMERGENCY BRAKE. Stop, contact the signaller, then acknowledge."));
                }
                break;
            }
        }
        st.sigs.clear();
        st.sigs.putAll(nowSigs);
        st.lastCur = cur;

        // ---- batch-3 #13 cab signalling: continuous cab aspect (config), balise warnings (cabWarn), acknowledgement
        if (driven && com.dogpound.railmap.safety.SigConfig.cabSignals) {
            int rank = 0;
            double near = 1500;
            for (double[] v : nowSigs.values()) if (v[0] >= 0 && v[0] < near) { near = v[0]; rank = v[1] > 0 ? 2 : v[2] < Math.min(lineKmh, 1e6) ? 1 : 0; }
            if (st.cabRank >= 0 && rank > st.cabRank) { st.cab = Math.max(st.cab, 1); if (st.cab == 1) { st.cabDeadline = now + Math.round(com.dogpound.railmap.safety.SigConfig.cabAckSeconds * 20); st.cabWhat = "cab signal " + new String[]{"CLEAR", "CAUTION", "STOP"}[rank]; } }
            st.cabRank = rank;
        }
        if (st.cab == 1 && com.dogpound.railmap.safety.SigConfig.cabAckSeconds <= 0) st.cab = 0;
        if (st.cab == 1 && now > st.cabDeadline && com.dogpound.railmap.safety.SigConfig.cabAckPenalty && driven) {
            st.cab = 2;
            Recorder.event(world, label(loco) + ": cab signal warning not acknowledged (" + st.cabWhat + ") - brake demand");
            com.dogpound.railmap.safety.SigHooks.incident(world, riders, "AWS", label(loco) + ": cab signal not acknowledged - " + st.cabWhat);
            for (EntityPlayer p : riders) tell(p, "§c⛔ CAB SIGNAL not acknowledged: BRAKE DEMAND. Stop, then acknowledge");
        }

        // ---- driver advisory
        if (SimConfig.driverAdvisory && driven) advise(world, st, loco, path, cur, limits, speed, lineKmh, decel);
        else { st.advice = ""; st.adviceKmh = -1; }

        // ---- hold the brakes for whatever demanded them
        boolean hold = st.vig == 2 || st.awsPenalty || st.spad || st.cab == 2;
        if (hold) {
            loco.setThrottle(0);
            loco.setTrainBrake(1);
            note = st.spad ? "SPAD - " + st.spadWhat : st.awsPenalty ? "AWS brake demand" : st.cab == 2 ? "Cab signal brake demand" : "Vigilance penalty brake";
        } else if (st.cab == 1) note = "CAB SIGNAL: " + st.cabWhat + " - acknowledge";
        st.ctl = controls(loco);                                          // our own brake demand is not "driver action"
        return note;
    }

    /** the acknowledge key / button (riders of a train): vigilance, AWS, and once stopped, penalties and SPAD */
    public static String ack(World world, EntityPlayer p) {
        String said = null;
        for (Locomotive loco : cam72cam.mod.world.World.get(world).getEntities(Locomotive.class)) {
            if (loco.isDead() || !Autopilot.riders(loco).contains(p)) continue;
            String trip = com.dogpound.railmap.safety.TripCocks.driverReset(world, loco, p.getName());   // batch-3 #11 trip valve
            if (trip != null) said = trip;
            State st = STATES.get(loco.getUUID());
            if (st == null) continue;
            long now = world.getTotalWorldTime();
            double speed = Math.abs(loco.getCurrentSpeed().metric());
            st.lastAction = now;
            if (st.vig == 1) { st.vig = 0; said = "§aVigilance acknowledged"; }
            if (st.aws == 1) { st.aws = 2; said = "§eAWS warning acknowledged - caution"; }
            if (st.cab == 1) { st.cab = 0; said = "§eCab signal acknowledged: " + st.cabWhat; }
            if (speed < 1) {
                if (st.cab == 2) { st.cab = 0; said = "§aCab signal brake demand reset"; }
                if (st.vig == 2) { st.vig = 0; said = "§aVigilance penalty reset - you have the train"; }
                if (st.awsPenalty) { st.awsPenalty = false; st.aws = 2; said = "§aAWS brake demand reset"; }
                if (st.spad) {
                    st.spad = false;
                    Recorder.event(world, label(loco) + ": SPAD reset by " + p.getName());
                    said = "§eSPAD reset by the driver - proceed only with the signaller's authority";
                }
            } else if (st.vig == 2 || st.awsPenalty || st.spad || st.cab == 2) said = "§cStop the train first";
            if (said == null) said = "§7Acknowledged";
        }
        return said == null ? "§7You are not in a driven train" : said;
    }

    // ---- advisory ----------------------------------------------------------------------------------------------------

    private static void advise(World world, State st, Locomotive loco, TrackPath path, double cur, List<Wayside.Limit> limits,
                               double speed, double lineKmh, double decel) {
        double v = speed / 3.6;
        double b = Math.max(0.2, decel * 0.75);                          // plan on a comfortable service brake
        double grade = st.summary == null ? 0 : st.summary.gradePct / 100;
        double coast = Math.max(0.005, 0.025 + 9.81 * grade);           // rolling + air drag, plus the grade
        double maxKmh = Math.min(lineKmh, loco.getDefinition().getMaxSpeed(loco.gauge).metric());
        List<double[]> targets = new ArrayList<>();                      // {distance, kmh}
        List<String> what = new ArrayList<>();
        for (Wayside.Limit l : limits) {
            double dd = l.distance - cur;
            if (dd < 0) continue;
            if (l.stop) { targets.add(new double[]{Math.max(0, dd - 5), 0}); what.add("stop " + shortWhat(l.what)); break; }
            if (l.kmh < maxKmh - 1) { targets.add(new double[]{dd, l.kmh}); what.add(Math.round(l.kmh) + " " + shortWhat(l.what)); }
        }
        // a station along the way: plan to stop at it
        try {
            StationData sd = StationData.get(world);
            double best = Double.MAX_VALUE;
            String name = null;
            for (Map.Entry<Long, String> e : sd.names().entrySet()) {
                BlockPos p = BlockPos.fromLong(e.getKey());
                double d = path.distanceTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 5);
                if (d < 0) continue;
                double dd = d - cur;
                if (dd > 8 && dd < 2500 && dd < best) { best = dd; name = e.getValue(); }
            }
            if (name != null) { targets.add(new double[]{best, 0}); what.add("stop " + name); }
        } catch (RuntimeException ignored) { }

        String adv = maxKmh < 1e6 && speed < maxKmh - 6 ? "POWER" : "CRUISE";
        double aim = maxKmh < 1e6 ? maxKmh : -1;
        String why = "";
        int rank = 0;
        for (int i = 0; i < targets.size(); i++) {
            double d = targets.get(i)[0], t = targets.get(i)[1] / 3.6;
            if (v <= t + 0.3) continue;
            double brakeDist = (v * v - t * t) / (2 * b);
            if (d - brakeDist <= v * 2.5) {
                if (rank < 3) { rank = 3; adv = "BRAKE"; aim = t * 3.6; why = what.get(i) + " " + Math.round(d) + " m"; }
                continue;
            }
            double xMax = b > coast ? (2 * b * d - v * v + t * t) / (2 * (b - coast)) : d;
            if (xMax < v * 45 && rank < 2) { rank = 2; adv = "COAST"; aim = t * 3.6; why = what.get(i) + " " + Math.round(d) + " m"; }
        }
        if (rank == 0 && !targets.isEmpty()) why = what.get(0) + " " + Math.round(targets.get(0)[0]) + " m";
        st.advice = adv + (aim >= 0 ? " " + Math.round(aim) : "") + (why.isEmpty() ? "" : " · " + why);
        st.adviceKmh = aim;
    }

    // ---- helpers -------------------------------------------------------------------------------------------------------

    static float[] controls(Locomotive l) {
        return new float[]{round(l.getThrottle()), round(l.getTrainBrakePos()), round(l.getReverser()), round(l.getIndependentBrake())};
    }

    private static float round(float f) { return Math.round(f * 50) / 50f; }

    static String shortWhat(String w) {
        if (w == null) return "";
        int i = w.indexOf(':');
        return (i > 0 ? w.substring(0, i) : w).trim();
    }

    static String signalName(World world, BlockPos p) {
        return "signal at " + p.getX() + " " + p.getY() + " " + p.getZ();
    }

    static String label(Locomotive l) {
        if (l.tag != null && !l.tag.isEmpty()) return l.tag;
        return l.getDefinition() == null ? "Train" : l.getDefinition().name();
    }

    static void tell(EntityPlayer p, String msg) {
        if (p instanceof EntityPlayerMP mp) mp.sendStatusMessage(new TextComponentString(msg), true);
    }
}
