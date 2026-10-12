package com.dogpound.railmap.planning;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.track.GridFeed;
import com.dogpound.railmap.track.Rails;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Automatic coupling point (her ChatGPT batch 3 #4): a platform cabinet with the automatic-coupler air plant that joins
 * and splits driverless units at the platform, the way multiple units are coupled at a real junction station.
 * <ul>
 *   <li>JOIN: a unit stopping here WAITS (up to the WAIT time) for its portion; when the portion stops behind it, four
 *   checks run - COMPAT (gauge, MU / control system, through air brake: SplitJoin.compat), LENGTH (both portions
 *   together against the platform: the selector, or the two platform ends picked with the Signal Wrench), BRAKE (a
 *   continuity test on each portion) and ORDER (REAR: the portion joins behind the waiting unit, which keeps leading;
 *   FRONT: it joins in front and the train is driven from the far end of the new portion - a cab is needed there). All
 *   green: the portion creeps on (SplitJoin.join), the whole train is brake tested, and it leaves as one. Any red: the
 *   join is refused, both run on alone (recorded, counted).</li>
 *   <li>SPLIT: a train stopping here leaves its rear N vehicles (SplitJoin.split: a rear portion with a loco becomes its
 *   own train, one without is secured).</li>
 * </ul>
 * The couplers are worked by compressed air: the compressor runs off a touching grid Power Cable (400 V - 11 kV,
 * 4 kW); under 5 bar nothing couples or uncouples. Control is 12 V - without it nothing automatic starts.
 */
public class TileCouplingPoint extends OpsGear {
    static final String[] STAGE = {"IDLE", "WAITING", "COUPLING", "BRAKE TEST", "DONE", "", "", "", "", "FAULT"};
    static final int[] PLAT = {60, 120, 180, 240, 320};
    static final int[] WAIT_MIN = {2, 5, 10};

    private int stage;
    private UUID waiting, arriving, farEnd;
    private long stageAt, waitAt, platA, platB;
    private int joins, splits, refusals, faults;
    private int checks;                       // bits: 1 compat ok, 2 length ok, 4 brake ok, 8 order ok; <<4 = that check failed
    private double air = 8, lenNow;
    private boolean airSupply;
    private String fault = "", why = "-", supply = "", waitName = "-", arrName = "-";
    private final Map<UUID, Long> handled = new HashMap<>();

    public String id() { return "CP " + Math.floorMod(pos.getX() * 5 + pos.getZ() * 17, 1000); }
    @Override public String wiredName() { return "Coupling point " + id(); }
    private boolean join() { return ctl.get("s_rule") == 0; }
    private boolean rearOrder() { return ctl.get("s_order") == 0; }
    private int cars() { return 1 + Math.max(0, Math.min(3, ctl.get("s_cars"))); }
    private long waitTicks() { return WAIT_MIN[Math.max(0, Math.min(2, ctl.get("s_wait")))] * 1200L; }
    private double platformM() {
        if (platA != 0 && platB != 0) return Math.sqrt(BlockPos.fromLong(platA).distanceSq(BlockPos.fromLong(platB)));
        return PLAT[Math.max(0, Math.min(4, ctl.get("s_plat")))];
    }
    private boolean active() { return stage >= 1 && stage <= 3; }

    @Override public String gearState() { return !powered() ? "NO 12 V" : (join() ? "JOIN " : "SPLIT ") + STAGE[stage] + (stage == 9 ? " · " + fault : "") + (air < 5 ? " · NO AIR" : ""); }
    @Override public int wiredLamp() { return !powered() || stage == 9 ? 3 : air < 5 ? 2 : 1; }

    @Override
    public String link(String control, BlockPos pick, EntityPlayer who) {
        if (Rails.pieceAt(world, pick) == null) return "§cPick a rail at the platform end with the Signal Wrench";
        if (control.equals("d_pa")) { platA = pick.toLong(); markDirty(); return "§aPlatform end A set - platform " + Math.round(platformM()) + " m"; }
        if (control.equals("d_pb")) { platB = pick.toLong(); markDirty(); return "§aPlatform end B set - platform " + Math.round(platformM()) + " m"; }
        return null;
    }

    @Override
    protected String hold(RailwayData.AutoTrain a, Locomotive loco, long now) {
        if (!active()) return null;
        if (a.loco.equals(waiting)) {
            if (stage == 1 && join()) return "coupling point: waiting for its portion (" + clock(waitTicks() - (now - waitAt)) + ")";
            return "coupling point: " + STAGE[stage] + (air < 5 ? " - no air for the couplers" : "");
        }
        if (a.loco.equals(arriving)) return "coupling point: joining";
        return null;
    }

    private void go(int s) { stage = s; stageAt = world.getTotalWorldTime(); markDirty(); }

    private void fault(String w) {
        fault = w; faults++;
        AlarmManager.get(world).raise(world, "cp:" + pos.toLong(), AlarmManager.Cat.AUTO, AlarmManager.Sev.MAJOR, "COUPLING POINT " + id() + ": " + w, pos, true);
        Recorder.event(world, "COUPLING POINT " + id() + " FAULT: " + w);
        sound("alarm.annunciator", 0.6f);
        go(9);
    }

    private void refuse(String w, long now) {
        why = w; refusals++;
        if (waiting != null) handled.put(waiting, now);
        if (arriving != null) handled.put(arriving, now);
        Recorder.event(world, "COUPLING POINT " + id() + ": join of " + arrName + " to " + waitName + " REFUSED - " + w);
        sound("ui.beep_error", 0.7f);
        waiting = arriving = null;
        go(0);
    }

    @Override
    protected void tickGear() {
        if (age % 20 != 0) return;
        long now = world.getTotalWorldTime();
        handled.values().removeIf(t -> now - t > 3600);
        // the coupler air plant: a 4 kW compressor on the grid, 2 bar a coupling, slow leak
        if (powered() && air < 9) {
            GridFeed.Result r = GridFeed.draw(world, pos, 4);
            airSupply = r.ok; supply = r.what;
            if (r.ok) air = Math.min(9.5, air + 0.25);
        } else if (powered()) { airSupply = GridFeed.draw(world, pos, 0).ok; }
        air = Math.max(0, air - 0.005);
        Locomotive w = loco(waiting), a = loco(arriving);
        switch (stage) {
            case 0, 4 -> {
                if (stage == 4 && now - stageAt < 200) break;
                if (stage == 4) go(0);
                if (!powered() || ctl.get("s_on") == 0) break;
                for (RailwayData.AutoTrain t : dwellingHere()) {
                    if (handled.containsKey(t.loco)) continue;
                    Locomotive l = loco(t.loco);
                    if (l == null) continue;
                    if (!join() && l.getTrain().size() <= cars()) continue;
                    waiting = t.loco; arriving = null; waitName = Autopilot.label(t, l); arrName = "-"; waitAt = now; checks = 0; why = "-";
                    go(1);
                    break;
                }
            }
            case 1 -> {
                if (w == null) { waiting = null; go(0); break; }
                if (!join()) {                                                 // SPLIT: staff walk up, then uncouple
                    if (now - stageAt < 200) break;
                    if (air < 5) { why = "no air"; break; }
                    String r = SplitJoin.split(world, w, cars(), "", "coupling point " + id());
                    handled.put(waiting, now);
                    if (r.startsWith("§a")) {
                        splits++; air -= 2; why = "split " + cars();
                        sound("air_release", 0.8f);
                        com.dogpound.railmap.api.QuestEvents.fire(world, null, pos, "auto_coupled", "op", "split", "train", waitName, "cars", cars());
                        go(4);
                    } else { why = r.replaceAll("§.", ""); refusals++; waiting = null; go(0); }
                    break;
                }
                if (now - waitAt > waitTicks()) {                                // portion late: the waiting unit runs alone
                    Recorder.event(world, "COUPLING POINT " + id() + ": " + waitName + " waited " + clock(waitTicks()) + " - no portion, runs alone");
                    handled.put(waiting, now); waiting = null; go(0); break;
                }
                for (RailwayData.AutoTrain t : dwellingHere()) {
                    if (t.loco.equals(waiting) || handled.containsKey(t.loco)) continue;
                    Locomotive l = loco(t.loco);
                    if (l == null || w.getTrain().contains(l)) continue;
                    arriving = t.loco; arrName = Autopilot.label(t, l);
                    String no = check(w, l);
                    if (no != null) { refuse(no, now); break; }
                    if (air < 5) { why = "waiting for air"; break; }
                    String r = SplitJoin.join(world, w, l, "coupling point " + id());
                    if (!r.startsWith("§e")) { refuse(r.replaceAll("§.", ""), now); break; }
                    air -= 2;
                    sound("cab.horn", 0.4f);
                    go(2);
                    break;
                }
            }
            case 2 -> {
                if (w == null || a == null) { fault("a unit went missing while coupling"); break; }
                if (w.getTrain().contains(a)) { sound("machine.clunk", 0.9f); go(3); }
                else if (!SplitJoin.joining(arriving)) fault("the portion did not couple (join abandoned)");
            }
            case 3 -> {
                if (w == null) { fault("the train went missing"); break; }
                Locomotive far = loco(farEnd);
                List<EntityCoupleableRollingStock> all = SplitJoin.ordered(rearOrder() || far == null ? w : far);
                if (all == null) { fault("the leading cab is in mid-train after the join"); break; }
                if (ctl.get("k_bypass") == 0) {
                    Object[] bt = SplitJoin.brakeTest(world, all);
                    if (!(boolean) bt[0]) { fault("brake test after joining: " + bt[1]); break; }
                }
                if (!rearOrder() && far != null) { SplitJoin.transfer(world, waiting, far, true); turned(far.getUUID(), now); }   // driven from the new front
                joins++;
                handled.put(waiting, now);
                if (far != null) handled.put(far.getUUID(), now);
                why = String.format(Locale.ROOT, "joined: %d vehicles, %.0f m", all.size(), SplitJoin.lengthM(all));
                Recorder.event(world, "COUPLING POINT " + id() + ": " + arrName + " joined " + (rearOrder() ? "behind " : "in front of ") + waitName + " - " + why);
                com.dogpound.railmap.api.QuestEvents.fire(world, null, pos, "auto_coupled", "op", "join", "train", waitName, "vehicles", all.size());
                sound("air_release", 0.7f);
                waiting = arriving = null;
                go(4);
            }
            default -> { }
        }
        live(now);
    }

    /** the four checks before a join (sets the lamps); null = all green */
    private String check(Locomotive w, Locomotive a) {
        checks = 0;
        List<EntityCoupleableRollingStock> ow = SplitJoin.ordered(w), oa = SplitJoin.ordered(a);
        String no = null;
        String c = SplitJoin.compat(world, ow, oa);
        checks |= c == null ? 1 : 16;
        if (c != null) no = "COMPAT: " + c;
        lenNow = (ow == null ? 0 : SplitJoin.lengthM(ow)) + (oa == null ? 0 : SplitJoin.lengthM(oa));
        boolean lenOk = lenNow <= platformM();
        checks |= lenOk ? 2 : 32;
        if (!lenOk && no == null) no = String.format(Locale.ROOT, "LENGTH: %.0f m won't fit the %.0f m platform", lenNow, platformM());
        boolean brk = true;
        if (ctl.get("k_bypass") == 0 && ow != null && oa != null) {
            Object[] b1 = SplitJoin.brakeTest(world, ow), b2 = SplitJoin.brakeTest(world, oa);
            brk = (boolean) b1[0] && (boolean) b2[0];
            if (!brk && no == null) no = "BRAKE: " + (!(boolean) b1[0] ? b1[1] : b2[1]);
        }
        checks |= brk ? 4 : 64;
        String ord = order(ow, oa);
        checks |= ord == null ? 8 : 128;
        if (ord != null && no == null) no = "ORDER: " + ord;
        return no;
    }

    private String order(List<EntityCoupleableRollingStock> ow, List<EntityCoupleableRollingStock> oa) {
        if (ow == null || oa == null) return "a cab is in mid-train";
        EntityCoupleableRollingStock wFront = ow.get(0), wRear = ow.get(ow.size() - 1), aA = oa.get(0), aB = oa.get(oa.size() - 1);
        double toRear = Math.min(d(wRear, aA), d(wRear, aB)), toFront = Math.min(d(wFront, aA), d(wFront, aB));
        boolean behind = toRear <= toFront;
        if (rearOrder() && !behind) return "the portion is at the front of the waiting unit - set ORDER to FRONT";
        if (!rearOrder()) {
            if (behind) return "the portion is behind the waiting unit - set ORDER to REAR";
            EntityCoupleableRollingStock far = d(wFront, aA) > d(wFront, aB) ? aA : aB;
            if (!(far instanceof Locomotive l)) return "no cab at the far end of the portion to drive from";
            farEnd = l.getUUID();
        } else farEnd = null;
        return null;
    }

    private static double d(EntityCoupleableRollingStock a, EntityCoupleableRollingStock b) { Vec3d p = a.getPosition(), q = b.getPosition(); return Math.hypot(p.x - q.x, p.z - q.z); }

    private void live(long now) {
        put("l_wait", stage == 1 && join());
        put("l_arr", arriving != null && active());
        put("l_cmp", (checks & 1) != 0); put("l_cmpf", (checks & 16) != 0);
        put("l_len", (checks & 2) != 0); put("l_lenf", (checks & 32) != 0);
        put("l_brk", (checks & 4) != 0); put("l_brkf", (checks & 64) != 0);
        put("l_ord", (checks & 8) != 0); put("l_ordf", (checks & 128) != 0);
        put("l_cpl", stage == 2);
        put("l_done", stage == 4);
        put("l_flt", stage == 9);
        put("l_air", air >= 5);
        put("l_sup", airSupply);
        put("l_pwr", powered());
        put("g_air", (int) Math.round(air * 10));
        put("d_joins", joins);
        put("d_splits", splits);
        text("d_stage", (join() ? "JOIN " : "SPLIT ") + (stage == 9 ? "FAULT" : STAGE[stage]));
        text("d_wait", shortName(waitName));
        text("d_arr", shortName(arrName));
        text("d_len", String.format(Locale.ROOT, "%.0f / %.0f M", lenNow, platformM()));
        text("d_why", why.length() > 22 ? why.substring(0, 22) : why);
        text("d_air", String.format(Locale.ROOT, "%.1f BAR %s", air, airSupply ? "" : "NO SUPPLY"));
        text("d_pa", platA == 0 ? "END A --" : "A " + Rails.xz(BlockPos.fromLong(platA)));
        text("d_pb", platB == 0 ? "END B --" : "B " + Rails.xz(BlockPos.fromLong(platB)));
        text("d_timer", stage == 1 && join() ? "WAIT " + clock(Math.max(0, waitTicks() - (now - waitAt))) : "WAIT " + clock(waitTicks()));
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        switch (id) {
            case "test": return lampTest();
            case "p_reset": {
                if (stage != 9) return "§7Nothing to reset - " + gearState();
                AlarmManager.get(world).clear("cp:" + pos.toLong());
                if (waiting != null) handled.put(waiting, world.getTotalWorldTime());
                waiting = arriving = null;
                Recorder.event(world, "COUPLING POINT " + id() + " reset by " + who(who));
                go(0);
                return "§aReset - trains released";
            }
            case "p_now": return "§e" + forceStep();
            default: return null;
        }
    }

    String forceStep() {
        if (stage == 1 && join()) { waitAt -= waitTicks(); return "wait expired - the unit runs alone"; }
        if (stage == 1) { stageAt -= 200; return "splitting now"; }
        return "stage " + STAGE[stage];
    }

    @Override
    protected String[] assetRow() {
        String compat = (checks & 16) != 0 ? "FAILED" : (checks & 1) != 0 ? "OK" : "-";
        String brake = (checks & 64) != 0 ? "FAILED" : (checks & 4) != 0 ? "OK" : "-";
        return new String[]{"CP", id(), (join() ? "JOIN " + (rearOrder() ? "REAR" : "FRONT") : "SPLIT " + cars()) + (ctl.get("s_on") == 0 ? " (OFF)" : ""), stage == 9 ? "FAULT " + fault : STAGE[stage] + (air < 5 ? " NO AIR" : ""),
                active() ? waitName : "-", String.valueOf(Math.round(lenNow)), String.valueOf(Math.round(platformM())), compat, brake, String.valueOf(joins), String.valueOf(splits), powered() ? "12V" : "NO 12V"};
    }

    @Override
    protected void save(NBTTagCompound t) {
        t.setInteger("stage", stage); if (waiting != null) t.setString("w", waiting.toString()); if (arriving != null) t.setString("a", arriving.toString()); if (farEnd != null) t.setString("far", farEnd.toString());
        t.setLong("sAt", stageAt); t.setLong("wAt", waitAt); t.setLong("pa", platA); t.setLong("pb", platB); t.setInteger("joins", joins); t.setInteger("splits", splits); t.setInteger("ref", refusals);
        t.setInteger("faults", faults); t.setInteger("checks", checks); t.setDouble("air", air); t.setString("fault", fault); t.setString("why", why); t.setString("wn", waitName); t.setString("an", arrName);
    }

    @Override
    protected void load(NBTTagCompound t) {
        stage = t.getInteger("stage"); waiting = TileChangeover.uuid(t.getString("w")); arriving = TileChangeover.uuid(t.getString("a")); farEnd = TileChangeover.uuid(t.getString("far"));
        stageAt = t.getLong("sAt"); waitAt = t.getLong("wAt"); platA = t.getLong("pa"); platB = t.getLong("pb"); joins = t.getInteger("joins"); splits = t.getInteger("splits"); refusals = t.getInteger("ref");
        faults = t.getInteger("faults"); checks = t.getInteger("checks"); air = t.hasKey("air") ? t.getDouble("air") : 8; fault = t.getString("fault"); why = t.hasKey("why") ? t.getString("why") : "-";
        waitName = t.hasKey("wn") ? t.getString("wn") : "-"; arrName = t.hasKey("an") ? t.getString("an") : "-";
    }
}
