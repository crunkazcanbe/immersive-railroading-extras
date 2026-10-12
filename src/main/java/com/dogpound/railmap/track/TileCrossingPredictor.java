package com.dogpound.railmap.track;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.graph.TrainNode;
import com.dogpound.railmap.server.TrainTracker;
import com.dogpound.railmap.signal.TileCrossing;
import com.dogpound.railmap.signal.TileSignalMast;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.util.Constants;

import java.util.*;

/**
 * Level crossing predictor + road traffic controller (her ChatGPT list #17): the lineside equipment case for a level
 * crossing. Wrench the crossing lights / gates onto it (and the signals that protect the crossing). It then:
 * <ul>
 *   <li>works the crossings by PREDICTED arrival - constant warning time: a fast train starts them further out, a slow
 *       one closer in, so the road is never shut longer than the set warning time (20-45 s) before the train arrives;</li>
 *   <li>simulates the road: vehicle arrivals at the chosen traffic flow, queues building while the barriers are down and
 *       discharging after, vehicles counted, delay per vehicle, the queue length in metres;</li>
 *   <li>obstacle detection: a vehicle that stalls on the crossing holds the protecting signals at danger until it clears;</li>
 *   <li>barrier failure: barriers that don't come down (proved from the gates themselves) hold the protecting signals and
 *       raise a crossing failure alarm until a maintainer RESETs it;</li>
 *   <li>emergency vehicles: an ambulance at a closed crossing is announced; with no train close the crossing is kept up for
 *       it and the protecting signals held while it crosses;</li>
 *   <li>counters and a reliability report: activations, closures, longest closure, availability, MTBF.</li>
 * </ul>
 * Without 12 V it fails safe: every crossing it controls goes down.
 */
public class TileCrossingPredictor extends TrackGear {
    public static final int[] WT = {20, 25, 30, 35, 45};
    public static final int[] FLOW = {120, 400, 900, 1500};
    public static final String[] FLOWN = {"LOW", "MED", "HIGH", "ARTERIAL"};

    private final List<Long> crossings = new ArrayList<>(), signals = new ArrayList<>();
    private boolean active, wasActive, failed, emergWaiting;
    private int stall, emergHold, closedSecs, eta = -1, downSecs, failSince;
    private double queue, maxQueue, vehicles, delayVehSecs, lambdaCarry;
    private long activations, closures, closedTotal, longest, failures, stalls, emergencies, upSince = -1, failedSecs, ageSecs;
    private String failWhy = "";

    public String id() { return "LX " + Math.floorMod(pos.getX() * 11 + pos.getZ() * 23, 1000); }
    @Override public String wiredName() { return "Crossing predictor " + id(); }
    private int wt() { return WT[Math.min(4, Math.max(0, ctl.get("s_wt")))]; }
    private int flow() { return FLOW[Math.min(3, Math.max(0, ctl.get("s_flow")))]; }

    @Override public String gearState() { return crossings.isEmpty() ? "no crossings linked (Signal Wrench: pick a crossing, click the ETA display)" : !powered() ? "NO 12 V - crossings down (fail-safe)" : (active ? "WARNING" : "open") + (failed ? " · FAILED " + failWhy : "") + (stall > 0 ? " · VEHICLE ON CROSSING" : "") + String.format(Locale.ROOT, " · queue %.0f", queue); }
    @Override public int wiredLamp() { return !powered() || failed || stall > 0 ? 3 : active ? 2 : 1; }
    @Override public void gearInfo(Map<String, Object> m) { m.put("crossings", crossings.size()); m.put("protectingSignals", signals.size()); m.put("active", active); m.put("etaSec", eta); m.put("queue", Math.round(queue)); m.put("vehicles", Math.round(vehicles)); m.put("activations", activations); m.put("failed", failed); }

    @Override
    public String link(String control, BlockPos pick, EntityPlayer who) {
        TileEntity t = world.getTileEntity(pick);
        if (pick.distanceSq(pos) > 256 * 256) return "§cToo far from the predictor (256 blocks)";
        if (t instanceof TileCrossing) {
            if (crossings.contains(pick.toLong())) { crossings.remove(pick.toLong()); markDirty(); return "§eCrossing at " + Rails.xz(pick) + " unlinked"; }
            if (crossings.size() >= 8) return "§cA predictor works 8 crossing heads at most";
            crossings.add(pick.toLong()); markDirty();
            return "§aCrossing at " + Rails.xz(pick) + " now worked by " + id() + " (constant warning time " + wt() + " s)";
        }
        if (t instanceof TileSignalMast) {
            if (signals.contains(pick.toLong())) { signals.remove(pick.toLong()); markDirty(); return "§eProtecting signal unlinked"; }
            if (signals.size() >= 6) return "§cSix protecting signals at most";
            signals.add(pick.toLong()); markDirty();
            return "§aSignal at " + Rails.xz(pick) + " now protects " + id() + " - held at danger while the crossing isn't proved clear";
        }
        return "§cPick a CROSSING (lights / gate / cantilever) or a protecting SIGNAL with the Signal Wrench";
    }

    /** TrackHooks: our verdict for a crossing we work (null = not ours) */
    Boolean demand(BlockPos crossing) {
        if (!crossings.contains(crossing.toLong())) return null;
        if (!powered()) return true;                                     // fail-safe
        return active;
    }

    private BlockPos ref() { return crossings.isEmpty() ? pos : BlockPos.fromLong(crossings.get(0)); }

    @Override
    protected void tickGear() {
        if (age % 20 != 0) return;
        ageSecs++;
        long now = world.getTotalWorldTime();
        if (upSince < 0) upSince = now;
        crossings.removeIf(c -> world.isBlockLoaded(BlockPos.fromLong(c)) && !(world.getTileEntity(BlockPos.fromLong(c)) instanceof TileCrossing));
        predict();
        boolean closed = closedNow();
        // ---- road traffic (Poisson arrivals, 1 vehicle/s discharge with two lanes)
        double lam = flow() / 3600.0 + lambdaCarry;
        int arrivals = (int) lam;
        lambdaCarry = lam - arrivals;
        if (world.rand.nextDouble() < lambdaCarry) { arrivals++; lambdaCarry -= 1; if (lambdaCarry < 0) lambdaCarry = 0; }
        if (closed || stall > 0) {
            queue += arrivals;
            closedSecs++;
            closedTotal++;
        } else {
            double out = Math.min(queue, 1.0);
            queue -= out;
            vehicles += out + arrivals;
            if (closedSecs > 0) { longest = Math.max(longest, closedSecs); closedSecs = 0; }
        }
        maxQueue = Math.max(maxQueue, queue);
        delayVehSecs += queue;
        // ---- closing: occupancy, stalls, barrier failures
        if (active && !wasActive) {
            activations++;
            closures++;
            if (TrackConfig.crossingStalls && world.rand.nextDouble() < flow() / 1500.0 * 0.01) {
                stall = 20 + world.rand.nextInt(70);
                stalls++;
                Recorder.event(world, "CROSSING " + id() + ": vehicle stalled on the crossing - obstacle detected, protecting signals held");
                AlarmManager.get(world).raise(world, "lx:" + pos.toLong() + ":stall", AlarmManager.Cat.INCIDENT, AlarmManager.Sev.CRITICAL, "VEHICLE STALLED ON CROSSING " + id() + " - protecting signals held", pos, true);
            }
            if (TrackConfig.barrierFailPer1000 > 0 && world.rand.nextInt(1000) < TrackConfig.barrierFailPer1000) failBarriers("barriers failed to lower (motor / mechanism)");
            downSecs = 0;
        }
        if (active) {
            downSecs++;
            if (downSecs == 9 && !failed) for (TileCrossing c : linked()) if (c.kind() == TileCrossing.Kind.GATE && c.gateProgress(0) < 0.95f) { failBarriers("barrier at " + Rails.xz(c.getPos()) + " not proved down"); break; }
        }
        wasActive = active;
        if (stall > 0 && --stall == 0) { AlarmManager.get(world).clear("lx:" + pos.toLong() + ":stall"); Recorder.event(world, "CROSSING " + id() + ": stalled vehicle cleared"); }
        if (failed) failedSecs++;
        // ---- emergency road vehicles (about 1 an hour per 400 veh/h of traffic)
        if (!emergWaiting && world.rand.nextDouble() < flow() / 400.0 / 3600.0 * 4) { emergWaiting = closed; if (closed) { emergencies++; Recorder.event(world, "CROSSING " + id() + ": emergency vehicle waiting at the barriers"); } }
        if (emergWaiting && !closedNow()) emergWaiting = false;
        Alarms.state(world, "lx:" + pos.toLong() + ":emv", emergWaiting, AlarmManager.Cat.INCIDENT, AlarmManager.Sev.MINOR, "EMERGENCY VEHICLE WAITING at crossing " + id() + (eta > 60 || eta < 0 ? " - train far off: EMV PRIO gives it the road" : " - train " + eta + " s away"), pos);
        if (emergHold > 0 && --emergHold == 0) Recorder.event(world, "CROSSING " + id() + ": emergency vehicle priority ended");
        Alarms.state(world, "lx:" + pos.toLong() + ":q", queue > 40, AlarmManager.Cat.GENERAL, AlarmManager.Sev.INFO, "Road queue at crossing " + id() + ": " + Math.round(queue) + " vehicles (" + Math.round(queue * 7) + " m)", pos);
        live();
    }

    private List<TileCrossing> linked() {
        List<TileCrossing> l = new ArrayList<>();
        for (long c : crossings) { BlockPos p = BlockPos.fromLong(c); if (world.isBlockLoaded(p) && world.getTileEntity(p) instanceof TileCrossing x) l.add(x); }
        return l;
    }

    private boolean closedNow() { for (TileCrossing c : linked()) if (c.active()) return true; return active; }

    /** constant warning time: the nearest train's predicted arrival */
    private void predict() {
        BlockPos r = ref();
        double cx = r.getX() + 0.5, cz = r.getZ() + 0.5;
        int best = -1;
        boolean island = false;
        for (TrainNode t : TrainTracker.latest(world)) {
            double dx = t.x - cx, dz = t.z - cz;
            if (Math.abs(t.y - r.getY()) > 8) continue;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 2500) continue;
            if (d < 14) { island = true; best = 0; continue; }
            double hx = Math.cos(Math.toRadians(t.yaw)), hz = Math.sin(Math.toRadians(t.yaw));
            boolean toward = (-dx * hx - dz * hz) / d > 0.2;
            double v = Math.abs(t.speedKmh) / 3.6;
            if (!t.moving()) { if (d < 40) { island = true; best = 0; } continue; }
            if (!toward || v < 0.3) continue;
            int s = (int) Math.round(d / v);
            if (best < 0 || s < best) best = s;
        }
        eta = best;
        active = island || best >= 0 && best <= wt();
        if (emergHold > 0 && !island && (best < 0 || best > 40)) active = false;   // emergency vehicle priority: keep the road open
    }

    private void failBarriers(String why) {
        failed = true;
        failWhy = why;
        failures++;
        Recorder.event(world, "CROSSING " + id() + " FAILED: " + why);
        AlarmManager.get(world).raise(world, "lx:" + pos.toLong() + ":fail", AlarmManager.Cat.SIGNAL, AlarmManager.Sev.MAJOR, "LEVEL CROSSING FAILURE " + id() + ": " + why + " - protecting signals held", pos, true);
    }

    /** SafetyHolds: protecting signals (or the crossing's surroundings) at danger while it isn't proved */
    void holds(Map<Long, String> hold, List<Object[]> zones) {
        String why = failed ? "crossing " + id() + " failed: " + failWhy : stall > 0 ? "vehicle stalled on crossing " + id() : emergHold > 0 ? "emergency vehicle crossing at " + id() : !powered() && !crossings.isEmpty() ? "crossing " + id() + " controller has no 12 V" : null;
        if (why == null) return;
        if (signals.isEmpty()) { BlockPos r = ref(); zones.add(new Object[]{r.getX() + 0.5, r.getZ() + 0.5, 32.0, why}); }
        else for (long s : signals) hold.putIfAbsent(s, why);
    }

    private void live() {
        put("act", active);
        boolean anyDown = false, allUp = true;
        for (TileCrossing c : linked()) { if (c.gateProgress(0) > 0.95f || c.kind() != TileCrossing.Kind.GATE && c.active()) anyDown = true; if (c.active()) allUp = false; }
        put("down", anyDown);
        put("up", allUp && !crossings.isEmpty());
        put("occ", stall > 0);
        put("fail", failed);
        put("emerg", emergWaiting || emergHold > 0);
        put("train", eta >= 0);
        put("prot", failed || stall > 0 || emergHold > 0);
        put("pwr", powered());
        put("g_q", (int) Math.min(60, Math.round(queue)));
        text("eta", eta < 0 ? "ETA --" : eta == 0 ? "ON ISLAND" : "ETA " + eta + " S");
        text("wt", "WT " + wt() + " S " + FLOWN[Math.min(3, ctl.get("s_flow"))]);
        text("q", String.format(Locale.ROOT, "Q %d VEH %dM", Math.round(queue), Math.round(queue * 7)));
        text("veh", "VEH " + Math.round(vehicles));
        text("acts", "ACT " + activations);
        text("rel", String.format(Locale.ROOT, "AVAIL %.1f%%", availability()));
        text("avg", "AVG " + (closures == 0 ? 0 : closedTotal / closures) + " S MAX " + longest);
        text("xs", "LX " + crossings.size() + " SIG " + signals.size());
    }

    private double availability() { return ageSecs == 0 ? 100 : 100.0 * (1 - failedSecs / (double) ageSecs); }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        switch (id) {
            case "test": return lampTest();
            case "p_reset": {
                if (!failed) return "§7Nothing to reset";
                for (TileCrossing c : linked()) if (c.kind() == TileCrossing.Kind.GATE && c.active() && c.gateProgress(0) < 0.95f && downSecs > 8) return "§cA barrier is still not down - check the gate";
                failed = false;
                AlarmManager.get(world).clear("lx:" + pos.toLong() + ":fail");
                Recorder.event(world, "CROSSING " + id() + " reset by " + who(who));
                return "§aCrossing reset - protecting signals released";
            }
            case "p_emerg": {
                if (eta >= 0 && eta < 40) return "§cTrain " + eta + " s away - too close to give the road";
                emergHold = 30;
                emergWaiting = false;
                AlarmManager.get(world).clear("lx:" + pos.toLong() + ":emv");
                Recorder.event(world, "CROSSING " + id() + ": road given to an emergency vehicle by " + who(who) + " - protecting signals held 30 s");
                return "§eEmergency vehicle priority: crossing kept open, protecting signals at danger for 30 s";
            }
            case "p_rep": {
                if (who != null) for (String l : report()) who.sendMessage(new TextComponentString(l));
                return "§7Reliability report printed";
            }
            default: return null;
        }
    }

    List<String> report() {
        List<String> l = new ArrayList<>();
        double hours = ageSecs / 3600.0;
        l.add("§b== Crossing " + id() + " reliability report ==");
        l.add(String.format(Locale.ROOT, "§7Activations %d · average closure %d s · longest %d s · closed %.1f%% of the time", activations, closures == 0 ? 0 : closedTotal / closures, longest, ageSecs == 0 ? 0 : 100.0 * closedTotal / ageSecs));
        l.add(String.format(Locale.ROOT, "§7Road: %.0f vehicles · max queue %.0f (%.0f m) · average delay %.1f s per vehicle · flow %d veh/h", vehicles, maxQueue, maxQueue * 7, vehicles < 1 ? 0 : delayVehSecs / vehicles, flow()));
        l.add(String.format(Locale.ROOT, "§7Failures %d · stalled vehicles %d · emergency vehicles %d · availability %.2f%% · MTBF %s", failures, stalls, emergencies, availability(), failures == 0 ? "no failures" : String.format(Locale.ROOT, "%.1f h", hours / failures)));
        return l;
    }

    @Override
    protected String[] assetRow() {
        return new String[]{"LX", id(), crossings.size() + "/" + signals.size(), active ? "WARNING" : "open", eta < 0 ? "-" : String.valueOf(eta), String.valueOf(wt()), String.valueOf(Math.round(queue)),
                String.valueOf(Math.round(vehicles)), String.valueOf(activations), failed ? "FAILED" : stall > 0 ? "STALL" : "ok", String.format(Locale.ROOT, "%.1f", availability()), FLOWN[Math.min(3, ctl.get("s_flow"))], xyz()};
    }

    private static void writeL(NBTTagCompound t, String k, List<Long> l) { NBTTagList n = new NBTTagList(); for (long v : l) n.appendTag(new NBTTagLong(v)); t.setTag(k, n); }
    private static void readL(NBTTagCompound t, String k, List<Long> l) { l.clear(); NBTTagList n = t.getTagList(k, Constants.NBT.TAG_LONG); for (int i = 0; i < n.tagCount(); i++) l.add(((NBTTagLong) n.get(i)).getLong()); }

    @Override
    protected void save(NBTTagCompound t) {
        writeL(t, "xs", crossings); writeL(t, "sigs", signals);
        t.setBoolean("failed", failed); t.setString("why", failWhy); t.setDouble("q", queue); t.setDouble("maxQ", maxQueue); t.setDouble("veh", vehicles); t.setDouble("delay", delayVehSecs);
        t.setLong("acts", activations); t.setLong("clos", closures); t.setLong("closed", closedTotal); t.setLong("longest", longest); t.setLong("fails", failures); t.setLong("stalls", stalls);
        t.setLong("emv", emergencies); t.setLong("failedS", failedSecs); t.setLong("ageS", ageSecs);
    }

    @Override
    protected void load(NBTTagCompound t) {
        readL(t, "xs", crossings); readL(t, "sigs", signals);
        failed = t.getBoolean("failed"); failWhy = t.getString("why"); queue = t.getDouble("q"); maxQueue = t.getDouble("maxQ"); vehicles = t.getDouble("veh"); delayVehSecs = t.getDouble("delay");
        activations = t.getLong("acts"); closures = t.getLong("clos"); closedTotal = t.getLong("closed"); longest = t.getLong("longest"); failures = t.getLong("fails"); stalls = t.getLong("stalls");
        emergencies = t.getLong("emv"); failedSecs = t.getLong("failedS"); ageSecs = t.getLong("ageS");
    }
}
