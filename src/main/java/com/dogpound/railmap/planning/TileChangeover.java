package com.dogpound.railmap.planning;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.RailwayData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import java.util.*;

/**
 * Locomotive changeover point (her ChatGPT batch 3 #1): a station cabinet where a driverless train changes traction -
 * electric to diesel at the end of the wires, diesel to electric, to steam for a heritage branch, or a plain loco
 * relief (ANY). Terminus style, the way it is done at a stub-end station: the train stops with its loco at the buffers;
 * <ol>
 *   <li>STANDBY: the crew picks the nearest loose locomotive of the wanted traction standing within range;</li>
 *   <li>ATTACH: it creeps onto the outbound end of the train (SplitJoin.join - IR's own couplers); it has to come on at
 *   the far end from the old loco, else the move is refused (FAULT - wrong end);</li>
 *   <li>DETACH: the old loco's couplers are lifted, its hand brake goes on - it stays as the next standby;</li>
 *   <li>BRAKE TEST: the continuity test from the new loco (key BYPASS skips it - for testing only);</li>
 *   <li>RELEASE: the train's identity (line, orders, tickets) moves to the new loco and it departs the other way.</li>
 * </ol>
 * MAX DWELL: past it, the OVER-DWELL alarm goes up; with no standby loco by then the train leaves with its own loco.
 */
public class TileChangeover extends OpsGear {
    static final String[] STAGE = {"IDLE", "TRAIN IN", "ATTACHING", "DETACHING", "BRAKE TEST", "RELEASED", "", "", "", "FAULT"};
    static final String[] WANT = {"ANY", "ELECTRIC", "DIESEL", "STEAM"};
    static final int[] DWELL_MIN = {5, 10, 15, 20};

    private int stage;
    private UUID train, standby;
    private long inAt, stageAt;
    private int done, overDwell, faults;
    private boolean overRaised;
    private String fault = "", brakeTxt = "-", trainName = "-", oldName = "-", newName = "-";
    private final Map<UUID, Long> skip = new HashMap<>();

    public String id() { return "CO " + Math.floorMod(pos.getX() * 13 + pos.getZ() * 7, 1000); }
    @Override public String wiredName() { return "Loco changeover " + id(); }
    private int want() { return Math.max(0, Math.min(3, ctl.get("s_mode"))); }
    private long maxDwell() { return DWELL_MIN[Math.max(0, Math.min(3, ctl.get("s_dwell")))] * 1200L; }
    private boolean enabled() { return ctl.get("s_on") == 1; }
    private boolean active() { return stage >= 1 && stage <= 4; }

    @Override public String gearState() { return !powered() ? "NO 12 V" : STAGE[stage] + (active() ? " · " + trainName : "") + (stage == 9 ? " · " + fault : ""); }
    @Override public int wiredLamp() { return !powered() || stage == 9 ? 3 : overRaised ? 2 : 1; }

    @Override
    protected String hold(RailwayData.AutoTrain a, Locomotive loco, long now) {
        if (!a.loco.equals(train)) return null;
        if (active()) return "loco changeover: " + STAGE[stage] + (powered() ? "" : " (12 V lost - held)");
        if (stage == 9) return "loco changeover FAULT: " + fault + " (RESET at the cabinet)";
        return null;
    }

    private void go(int s) { stage = s; stageAt = world.getTotalWorldTime(); markDirty(); }

    private void fault(String why) {
        fault = why;
        faults++;
        go(9);
        AlarmManager.get(world).raise(world, "co:" + pos.toLong(), AlarmManager.Cat.AUTO, AlarmManager.Sev.MAJOR, "LOCO CHANGEOVER " + id() + ": " + why, pos, true);
        Recorder.event(world, "LOCO CHANGEOVER " + id() + " FAULT: " + why);
        sound("alarm.annunciator", 0.6f);
    }

    @Override
    protected void tickGear() {
        if (age % 20 != 0) { return; }
        long now = world.getTotalWorldTime();
        skip.values().removeIf(t -> now - t > 2400);
        Locomotive lead = loco(train), nl = loco(standby);
        switch (stage) {
            case 0, 5 -> {
                if (stage == 5 && now - stageAt < 200) break;
                if (stage == 5) go(0);
                if (!powered() || !enabled()) break;
                for (RailwayData.AutoTrain a : dwellingHere()) {
                    Locomotive l = loco(a.loco);
                    if (l == null || skip.containsKey(a.loco)) continue;
                    if (want() != 0 && traction(world, l).equals(WANT[want()])) continue;   // already the right traction
                    train = a.loco; standby = null; inAt = now; overRaised = false; brakeTxt = "-";
                    trainName = Autopilot.label(a, l); oldName = nm(l); newName = "-";
                    Recorder.event(world, "LOCO CHANGEOVER " + id() + ": " + trainName + " in for " + (want() == 0 ? "a loco change" : "change to " + WANT[want()]));
                    go(1);
                    break;
                }
            }
            case 1 -> {
                if (lead == null) { go(0); break; }
                if (!powered()) break;
                List<Locomotive> s = standby(l -> !l.getUUID().equals(train) && (want() == 0 || traction(world, l).equals(WANT[want()])));
                if (s.isEmpty()) {
                    newName = "NO STANDBY";
                    if (now - inAt > maxDwell()) {
                        Recorder.event(world, "LOCO CHANGEOVER " + id() + ": no standby " + WANT[want()] + " loco - " + trainName + " leaves with its own");
                        AlarmManager.get(world).raise(world, "co:none:" + pos.toLong(), AlarmManager.Cat.AUTO, AlarmManager.Sev.MINOR, "LOCO CHANGEOVER " + id() + ": no standby loco for " + trainName, pos, false);
                        skip.put(train, now);
                        go(0);
                    }
                    break;
                }
                Locomotive n = s.get(0);
                if (SplitJoin.ordered(lead) == null) { fault("the train's loco is in mid-train"); break; }
                String r = SplitJoin.join(world, lead, n, "changeover " + id());
                if (!r.startsWith("§e")) { fault(r.replaceAll("§.", "")); break; }
                standby = n.getUUID();
                newName = nm(n);
                sound("cab.horn", 0.5f);
                go(2);
            }
            case 2 -> {
                if (lead == null || nl == null) { fault("a loco went missing"); break; }
                if (lead.getTrain().contains(nl)) {
                    List<EntityCoupleableRollingStock> o = SplitJoin.ordered(nl);
                    if (o == null || o.get(o.size() - 1) != lead) { fault("standby coupled at the wrong end - it must come on at the outbound end"); break; }
                    SplitJoin.uncouple(lead, o.get(o.size() - 2));
                    lead.setHandBrake(1f);
                    nl.setTrainBrake(1f);
                    nl.setThrottle(0);
                    sound("machine.clunk", 0.8f);
                    Recorder.event(world, "LOCO CHANGEOVER " + id() + ": " + newName + " on, " + oldName + " uncoupled");
                    go(3);
                } else if (!SplitJoin.joining(standby)) fault("the standby loco did not couple (join abandoned)");
            }
            case 3 -> {
                if (nl == null) { fault("the new loco went missing"); break; }
                List<EntityCoupleableRollingStock> o = SplitJoin.ordered(nl);
                if (o != null && o.contains(lead)) { if (now - stageAt > 200) fault("the old loco won't uncouple"); break; }
                go(4);
            }
            case 4 -> {
                if (nl == null) { fault("the new loco went missing"); break; }
                if (ctl.get("k_bypass") == 0) {
                    Object[] bt = SplitJoin.brakeTest(world, SplitJoin.ordered(nl));
                    brakeTxt = (boolean) bt[0] ? "OK" : "FAIL";
                    if (!(boolean) bt[0]) { fault("brake test " + bt[1]); break; }
                    sound("air_release", 0.7f);
                } else brakeTxt = "BYPASS";
                if (!powered()) break;
                SplitJoin.transfer(world, train, nl, true);
                turned(nl.getUUID(), now);
                done++;
                AlarmManager.get(world).clear("co:" + pos.toLong());
                Recorder.event(world, "LOCO CHANGEOVER " + id() + " complete: " + trainName + " now behind " + newName + " (" + traction(world, nl) + "), dwell " + clock(now - inAt));
                com.dogpound.railmap.api.QuestEvents.fire(world, null, pos, "loco_changeover", "train", trainName, "traction", traction(world, nl), "seconds", (now - inAt) / 20);
                sound("cab.brake_release", 0.7f);
                skip.put(standby, now);
                train = standby;                                    // the hold now follows the new identity until it departs
                go(5);
            }
            default -> { }
        }
        if (active() && now - inAt > maxDwell() && !overRaised) {
            overRaised = true;
            overDwell++;
            AlarmManager.get(world).raise(world, "co:dwell:" + pos.toLong(), AlarmManager.Cat.AUTO, AlarmManager.Sev.MINOR, "LOCO CHANGEOVER " + id() + ": " + trainName + " over its max dwell (" + clock(maxDwell()) + ")", pos, false);
        }
        live(now);
    }

    private void live(long now) {
        put("l_in", stage >= 1 && stage <= 5);
        put("l_stby", standby != null && active());
        put("l_att", stage == 2);
        put("l_det", stage >= 3 && stage <= 5);
        put("l_bt", brakeTxt.equals("OK") || brakeTxt.equals("BYPASS"));
        put("l_btf", brakeTxt.equals("FAIL"));
        put("l_done", stage == 5);
        put("l_over", overRaised && active());
        put("l_flt", stage == 9);
        put("l_none", newName.equals("NO STANDBY") && stage == 1);
        put("l_pwr", powered());
        put("g_dwell", active() ? (int) Math.min(100, (now - inAt) * 100 / Math.max(1, maxDwell())) : 0);
        text("d_stage", stage == 9 ? "FAULT" : STAGE[stage]);
        text("d_train", active() || stage == 5 ? shortName(trainName) : "--");
        text("d_old", shortName(oldName));
        text("d_new", shortName(newName));
        text("d_time", active() ? "DWELL " + clock(now - inAt) : "MAX " + clock(maxDwell()));
        text("d_mode", "TO " + WANT[want()]);
        put("d_done", done);
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        switch (id) {
            case "test": return lampTest();
            case "p_reset": {
                if (stage != 9) return "§7Nothing to reset - " + gearState();
                if (train != null) skip.put(train, world.getTotalWorldTime());
                AlarmManager.get(world).clear("co:" + pos.toLong());
                Recorder.event(world, "LOCO CHANGEOVER " + id() + " reset by " + who(who));
                train = null; standby = null;
                go(0);
                return "§aReset - the train is released as it stands";
            }
            case "p_step": {                                      // the shunter's STEP: give up waiting now
                if (stage == 1) { inAt -= maxDwell(); return "§eMax dwell forced - the train leaves if no standby turns up"; }
                return "§7STEP only works while waiting for a standby loco";
            }
            default: return null;
        }
    }

    /** /irextras plan ops step: jump the timers */
    String forceStep() { if (stage == 1) { inAt -= maxDwell(); return "max dwell forced"; } return "stage " + STAGE[stage]; }

    @Override
    protected String[] assetRow() {
        long now = world.getTotalWorldTime();
        return new String[]{"CO", id(), "TO " + WANT[want()] + (enabled() ? "" : " (OFF)"), stage == 9 ? "FAULT " + fault : STAGE[stage], active() ? trainName : "-", oldName, newName, brakeTxt,
                active() ? String.valueOf((now - inAt) / 20) : "-", String.valueOf(done), String.valueOf(overDwell), powered() ? "12V" : "NO 12V"};
    }

    @Override
    protected void save(NBTTagCompound t) {
        t.setInteger("stage", stage); if (train != null) t.setString("train", train.toString()); if (standby != null) t.setString("stby", standby.toString());
        t.setLong("in", inAt); t.setLong("sAt", stageAt); t.setInteger("done", done); t.setInteger("over", overDwell); t.setInteger("faults", faults); t.setBoolean("overR", overRaised);
        t.setString("fault", fault); t.setString("bt", brakeTxt); t.setString("tn", trainName); t.setString("on", oldName); t.setString("nn", newName);
    }

    @Override
    protected void load(NBTTagCompound t) {
        stage = t.getInteger("stage"); train = uuid(t.getString("train")); standby = uuid(t.getString("stby"));
        inAt = t.getLong("in"); stageAt = t.getLong("sAt"); done = t.getInteger("done"); overDwell = t.getInteger("over"); faults = t.getInteger("faults"); overRaised = t.getBoolean("overR");
        fault = t.getString("fault"); brakeTxt = t.hasKey("bt") ? t.getString("bt") : "-"; trainName = t.hasKey("tn") ? t.getString("tn") : "-"; oldName = t.hasKey("on") ? t.getString("on") : "-"; newName = t.hasKey("nn") ? t.getString("nn") : "-";
    }

    static UUID uuid(String s) { try { return s == null || s.isEmpty() ? null : UUID.fromString(s); } catch (IllegalArgumentException e) { return null; } }

}
