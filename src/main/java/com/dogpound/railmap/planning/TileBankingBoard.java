package com.dogpound.railmap.planning;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock.CouplerType;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.sim.ConsistSim;
import com.dogpound.railmap.sim.SimData;
import com.dogpound.railmap.track.Rails;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Banking board at the foot of a bank (her ChatGPT batch 3 #2): the banking engine crew's signal box. A heavy driverless
 * train stopping at the foot (a stop on its line within range) is held while a loose standing loco - the banker - comes
 * up behind it:
 * <ul>
 *   <li>COUPLED: the banker couples on (SplitJoin.join) and is cut off on the fly at the summit - the coupler is lifted
 *   while moving, US helper style;</li>
 *   <li>BUFFER: uncoupled banking (the Lickey way): it buffers up with its couplers open and simply drops away at the
 *   summit when it shuts off.</li>
 * </ul>
 * While banking the banker is cut out of the MU train lines (its own crew drives it: SimData muCut, restored after) and
 * works at the TE SHARE of the lead's notch. The crew watches the drawbar: buff force through the banker's coupler over
 * the limit (PlanConfig.bankingBuffLimitKn, from IR's own consist solver via PrideForces) cuts its power back; the
 * seconds over the limit are the coupler STRESS, alarmed. Past the summit (a rail linked with the Signal Wrench onto the
 * SUMMIT display) it shuts off, brakes, and returns to the foot as a driverless "SEND" move (signals, moving block),
 * where it is left standing for the next train. No 12 V = the board starts nothing (a banker already pushing keeps
 * following its train - its crew is on the engine).
 */
public class TileBankingBoard extends OpsGear {
    static final String[] STAGE = {"IDLE", "TRAIN WAITING", "ATTACHING", "BANKING", "DROPPING OFF", "RETURNING", "", "", "", "FAULT"};
    static final int[] SHARE = {25, 50, 75};
    static final int[] TONNES = {0, 500, 1000, 1500, 2000};
    static final int[] RANGES_B = {60, 100, 150};

    private int stage;
    private UUID train, banker;
    private long summit, foot, stageAt, inAt;
    private int assists, stressTicks, overTicks, faults, bankRev = 1;
    private boolean prevCut, overRaised;
    private double kn, backoff = 1, tonnesNow;
    private float notch;
    private String fault = "", trainName = "-", bankerName = "-";

    public String id() { return "BK " + Math.floorMod(pos.getX() * 11 + pos.getZ() * 3, 1000); }
    @Override public String wiredName() { return "Banking board " + id(); }
    @Override protected int range() { return RANGES_B[Math.max(0, Math.min(2, ctl.get("s_range")))]; }
    private boolean coupled() { return ctl.get("s_mode") == 0; }
    private double share() { return SHARE[Math.max(0, Math.min(2, ctl.get("s_share")))] / 100.0; }
    private int threshold() { return TONNES[Math.max(0, Math.min(4, ctl.get("s_tonnes")))]; }
    private BlockPos footPos() { return foot == 0 ? pos : BlockPos.fromLong(foot); }

    @Override public String gearState() { return !powered() ? "NO 12 V" : summit == 0 ? "no summit linked (Signal Wrench: pick the summit rail, click SUMMIT)" : STAGE[stage] + (stage == 9 ? " · " + fault : stage > 0 ? " · " + trainName : ""); }
    @Override public int wiredLamp() { return !powered() || stage == 9 ? 3 : overRaised ? 2 : 1; }

    @Override
    public String link(String control, BlockPos pick, EntityPlayer who) {
        if (Rails.pieceAt(world, pick) == null) return "§cPick a rail with the Signal Wrench";
        if (control.equals("d_summit") || control.equals("l_sum")) { summit = pick.toLong(); markDirty(); return "§aSummit set at " + Rails.xz(pick) + " - bankers drop off there"; }
        if (control.equals("d_foot")) { foot = pick.toLong(); markDirty(); return "§aBanker siding (foot) set at " + Rails.xz(pick) + " - bankers return there"; }
        return null;
    }

    @Override
    protected String hold(RailwayData.AutoTrain a, Locomotive loco, long now) {
        if (!a.loco.equals(train) || (stage != 1 && stage != 2)) return null;
        return "banking: " + (stage == 1 ? "waiting for the banker" : "banker coming on behind (" + bankerName + ")");
    }

    private void go(int s) { stage = s; stageAt = world.getTotalWorldTime(); markDirty(); }

    private void fault(String why) {
        fault = why; faults++;
        restoreMu();
        go(9);
        AlarmManager.get(world).raise(world, "bk:" + pos.toLong(), AlarmManager.Cat.AUTO, AlarmManager.Sev.MAJOR, "BANKING " + id() + ": " + why, pos, true);
        Recorder.event(world, "BANKING " + id() + " FAULT: " + why);
        sound("alarm.annunciator", 0.6f);
    }

    private void restoreMu() {
        if (banker == null) return;
        SimData.Car c = SimData.get(world).peek(banker);
        if (c != null && c.muCut) { c.muCut = prevCut; SimData.get(world).markDirty(); }
    }

    /** the banker is past the summit rail (it pushes from the rear: once it is there, the whole train is over) */
    private boolean atSummit(Locomotive b) {
        if (summit == 0 || b == null) return false;
        BlockPos s = BlockPos.fromLong(summit);
        Vec3d p = b.getPosition();
        return Math.hypot(p.x - s.getX() - 0.5, p.z - s.getZ() - 0.5) < 8 && Math.abs(p.y - s.getY()) < 6;
    }

    @Override
    protected void tickGear() {
        long now = world.getTotalWorldTime();
        if (stage == 3) { drive(now); }
        if (age % 20 != 0) return;
        Locomotive lead = loco(train), b = loco(banker);
        switch (stage) {
            case 0 -> {
                if (!powered() || ctl.get("s_on") == 0 || summit == 0) break;
                for (RailwayData.AutoTrain a : dwellingHere()) {
                    Locomotive l = loco(a.loco);
                    if (l == null) continue;
                    List<EntityCoupleableRollingStock> o = SplitJoin.ordered(l);
                    double t = tonnes(o == null ? l.getTrain() : o);
                    if (t < threshold() || o == null) continue;
                    if (now - stageAt < 400 && a.loco.equals(train)) continue;      // the train we just banked
                    train = a.loco; trainName = Autopilot.label(a, l); tonnesNow = t; inAt = now; banker = null; bankerName = "-";
                    overRaised = false; stressTicks = 0; backoff = 1;
                    go(1);
                    break;
                }
            }
            case 1 -> {
                if (lead == null) { go(0); break; }
                if (!powered()) break;
                List<Locomotive> s = standby(l -> !lead.getTrain().contains(l));
                if (s.isEmpty()) {
                    bankerName = "NO BANKER";
                    if (now - stageAt > 1200) { Recorder.event(world, "BANKING " + id() + ": no banker for " + trainName + " - it attacks the bank alone"); go(0); }
                    break;
                }
                Locomotive n = s.get(0);
                String r = coupled() ? SplitJoin.join(world, lead, n, "banking " + id()) : SplitJoin.buffer(world, lead, n, "banking " + id());
                if (!r.startsWith("§e")) { fault(r.replaceAll("§.", "")); break; }
                banker = n.getUUID(); bankerName = Rails.name(n);
                sound("cab.horn", 0.5f);
                go(2);
            }
            case 2 -> {
                if (lead == null || b == null) { fault("a loco went missing"); break; }
                boolean on = coupled() ? lead.getTrain().contains(b) : !SplitJoin.joining(banker) && SplitJoin.gapBetween(lead, b) < 1.2;
                if (on) {
                    bankRev = b.getReverser() >= 0 ? 1 : -1;               // the creep found which way is towards the train
                    SimData.Car c = SimData.get(world).of(banker);
                    prevCut = c.muCut;
                    c.muCut = true;                                        // its own crew: off the MU train lines
                    SimData.get(world).markDirty();
                    b.setTrainBrake(0); b.setIndependentBrake(0); b.setHandBrake(0);
                    Recorder.event(world, "BANKING " + id() + ": " + bankerName + (coupled() ? " coupled to " : " buffered up behind ") + trainName + String.format(Locale.ROOT, " (%.0f t) - right away", tonnesNow));
                    sound("cab.horn", 0.7f);                              // the banker's "ready" crow
                    go(3);
                } else if (!SplitJoin.joining(banker)) fault("the banker did not get on (" + (coupled() ? "join" : "buffer-up") + " abandoned)");
            }
            case 4 -> {                                                    // dropping off: stop, then send it home
                if (b == null) { go(0); break; }
                b.prideMuApply(0f, b.getReverser());
                if (now - stageAt > 30) b.setTrainBrake(1f);              // after the coupler is clear of the train
                if (Math.abs(b.getCurrentSpeed().metric()) < 0.5 && now - stageAt > 60) {
                    RailwayData d = RailwayData.get(world);
                    RailwayData.AutoTrain a = d.setTrain(banker);
                    a.mode = RailwayData.Mode.SEND; a.home = footPos().toLong(); a.label = "Banker " + id(); a.maxKmh = 40;
                    d.markDirty();
                    go(5);
                }
            }
            case 5 -> {
                Autopilot.Run r = banker == null ? null : Autopilot.run(banker);
                boolean home = b != null && near(b.getPosition(), 1) || b != null && Math.hypot(b.getPosition().x - footPos().getX(), b.getPosition().z - footPos().getZ()) < 14 && Math.abs(b.getCurrentSpeed().metric()) < 1;
                if (home || r != null && r.idle || now - stageAt > 12000) {
                    RailwayData.get(world).removeTrain(banker);
                    Autopilot.release(world, banker);
                    if (b != null) b.setHandBrake(1f);
                    if (now - stageAt > 12000) AlarmManager.get(world).raise(world, "bk:ret:" + pos.toLong(), AlarmManager.Cat.AUTO, AlarmManager.Sev.MINOR, "BANKING " + id() + ": " + bankerName + " did not get back to the foot", pos, false);
                    Recorder.event(world, "BANKING " + id() + ": " + bankerName + " back at the foot");
                    go(0);
                }
            }
            default -> { }
        }
        live(now);
    }

    /** every tick while banking: the banker crew's driving */
    private void drive(long now) {
        Locomotive lead = loco(train), b = loco(banker);
        if (b == null) { fault("the banker went missing"); return; }
        if (lead == null) { b.prideMuApply(0f, b.getReverser()); b.setTrainBrake(1f); restoreMu(); go(4); return; }
        double front = ConsistSim.force(world, b, true), rear = ConsistSim.force(world, b, false);
        double buff = -Math.min(Math.min(front, rear), 0);                // compression through its couplers / buffers (N)
        kn = buff / 1000.0;
        int limit = PlanConfig.bankingBuffLimitKn;
        if (kn > limit) { stressTicks++; overTicks++; } else overTicks = 0;
        if (now % 5 == 0) backoff = backoff(backoff, kn, limit);
        if (overTicks > 60 && !overRaised) {
            overRaised = true;
            AlarmManager.get(world).raise(world, "bk:stress:" + pos.toLong(), AlarmManager.Cat.MACHINE, AlarmManager.Sev.MAJOR,
                    String.format(Locale.ROOT, "BANKING %s: %.0f kN buff on %s - over the %d kN limit, banker backing off", id(), kn, bankerName, limit), pos, false);
            if (PlanConfig.bankingOverloadDerails) {
                for (CouplerType ct : CouplerType.values()) {
                    EntityCoupleableRollingStock nb = b.getCoupled(ct);
                    if (nb != null && Rails.tonnes(nb) < 30) { cam72cam.immersiverailroading.wreck.Wrecks.forceWreck(nb, 2, "squeezed out by the banker"); break; }
                }
            }
        }
        notch = bankNotch(lead.getThrottle(), share(), backoff, lead.getTrainBrakePos());
        b.prideMuApply(notch, bankRev);
        if (atSummit(b)) {
            b.prideMuApply(0f, bankRev);
            restoreMu();
            if (coupled()) {
                for (CouplerType ct : CouplerType.values()) { EntityCoupleableRollingStock nb = b.getCoupled(ct); if (nb != null) SplitJoin.uncouple(b, nb); }
                sound("machine.clunk", 0.8f);
            }
            assists++;
            Recorder.event(world, String.format(Locale.ROOT, "BANKING %s: %s dropped off %s at the summit (peak stress %d s)", id(), bankerName, trainName, stressTicks / 20));
            com.dogpound.railmap.api.QuestEvents.fire(world, null, pos, "banking_assist", "train", trainName, "tonnes", Math.round(tonnesNow), "mode", coupled() ? "COUPLED" : "BUFFER");
            AlarmManager.get(world).clear("bk:stress:" + pos.toLong());
            go(4);
        }
    }

    private void live(long now) {
        put("l_wait", stage == 1);
        put("l_bank", banker != null && stage >= 2 && stage <= 3);
        put("l_att", stage == 2);
        put("l_push", stage == 3);
        put("l_summit", stage == 4);
        put("l_ret", stage == 5);
        put("l_over", overRaised && stage == 3);
        put("l_none", "NO BANKER".equals(bankerName) && stage == 1);
        put("l_flt", stage == 9);
        put("l_sum", summit != 0);
        put("l_pwr", powered());
        put("g_kn", (int) Math.round(Math.min(600, kn)));
        put("g_notch", Math.round(notch * 100));
        Locomotive lead = loco(train);
        put("g_lead", stage == 3 && lead != null ? Math.round(lead.getThrottle() * 100) : 0);
        put("d_count", assists);
        text("d_stage", stage == 9 ? "FAULT" : STAGE[stage]);
        text("d_train", stage > 0 ? shortName(trainName) : "--");
        text("d_banker", shortName(bankerName));
        text("d_kn", String.format(Locale.ROOT, "%4.0f KN %3.0f%%", kn, backoff * 100));
        text("d_tonnes", String.format(Locale.ROOT, "%.0f T / %d T", tonnesNow, threshold()));
        text("d_summit", summit == 0 ? "NO SUMMIT" : "SUMMIT " + Rails.xz(BlockPos.fromLong(summit)));
        text("d_foot", "FOOT " + Rails.xz(footPos()));
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        switch (id) {
            case "test": return lampTest();
            case "p_reset": {
                if (stage != 9) return "§7Nothing to reset - " + gearState();
                AlarmManager.get(world).clear("bk:" + pos.toLong());
                if (banker != null) { Locomotive b = loco(banker); if (b != null) { b.prideMuApply(0f, b.getReverser()); b.setTrainBrake(1f); } }
                Recorder.event(world, "BANKING " + id() + " reset by " + who(who));
                train = null; banker = null; go(0);
                return "§aReset - the train is released";
            }
            case "p_off": {                                              // the signalman's DROP OFF: banker shuts off now
                if (stage != 3) return "§7Only while banking";
                Locomotive b = loco(banker);
                if (b != null) b.prideMuApply(0f, bankRev);
                restoreMu();
                go(4);
                return "§eBanker told to drop off";
            }
            default: return null;
        }
    }

    String forceStep() { if (stage == 1) { stageAt -= 1200; return "banker wait timed out"; } if (stage == 3) { controlPress("p_off", null); return "banker dropped off"; } return "stage " + STAGE[stage]; }

    @Override
    protected String[] assetRow() {
        return new String[]{"BK", id(), (coupled() ? "COUPLED" : "BUFFER") + (ctl.get("s_on") == 0 ? " (OFF)" : ""), stage == 9 ? "FAULT " + fault : STAGE[stage], stage > 0 ? trainName : "-", bankerName,
                SHARE[Math.max(0, Math.min(2, ctl.get("s_share")))] + "%", String.valueOf(Math.round(kn)), String.valueOf(assists), summit == 0 ? "NOT SET" : Rails.xz(BlockPos.fromLong(summit)),
                (stressTicks / 20) + " s", powered() ? "12V" : "NO 12V"};
    }

    @Override
    protected void save(NBTTagCompound t) {
        t.setInteger("stage", stage); if (train != null) t.setString("train", train.toString()); if (banker != null) t.setString("bk", banker.toString());
        t.setLong("summit", summit); t.setLong("foot", foot); t.setLong("sAt", stageAt); t.setLong("in", inAt); t.setInteger("assists", assists); t.setInteger("stress", stressTicks);
        t.setInteger("faults", faults); t.setInteger("rev", bankRev); t.setBoolean("prevCut", prevCut); t.setString("fault", fault); t.setString("tn", trainName); t.setString("bn", bankerName); t.setDouble("t", tonnesNow);
    }

    @Override
    protected void load(NBTTagCompound t) {
        stage = t.getInteger("stage"); train = TileChangeover.uuid(t.getString("train")); banker = TileChangeover.uuid(t.getString("bk"));
        summit = t.getLong("summit"); foot = t.getLong("foot"); stageAt = t.getLong("sAt"); inAt = t.getLong("in"); assists = t.getInteger("assists"); stressTicks = t.getInteger("stress");
        faults = t.getInteger("faults"); bankRev = t.hasKey("rev") ? t.getInteger("rev") : 1; prevCut = t.getBoolean("prevCut"); fault = t.getString("fault");
        trainName = t.hasKey("tn") ? t.getString("tn") : "-"; bankerName = t.hasKey("bn") ? t.getString("bn") : "-"; tonnesNow = t.getDouble("t");
    }
}
