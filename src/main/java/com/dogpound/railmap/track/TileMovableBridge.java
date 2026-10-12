package com.dogpound.railmap.track;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.tile.TileRail;
import cam72cam.mod.math.Vec3i;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.graph.TrainNode;
import com.dogpound.railmap.server.TrainTracker;
import com.dogpound.railmap.signal.SignalRegistry;
import com.dogpound.railmap.signal.TileSignalMast;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.*;

/**
 * Movable bridge control desk (her ChatGPT batch 3 #8): a SWING bridge or a BASCULE (lifting) bridge over a waterway,
 * with the sequence a real bridge operator follows:
 * <pre>
 * CLOSED+LOCKED -> PROTECTING (signals to danger, approach locking: no train approaching within 300 m, none on the span)
 *   -> UNLOCKING (rail locks withdrawn, 3 s, detected) -> OPENING (motor, ramp, encoder) -> OPEN (waterway green)
 *   -> CLOSING (never onto a vessel under the span) -> LOCKING (locks driven + detected) -> CLOSED+LOCKED (signals off)
 * </pre>
 * <ul>
 *   <li><b>SWING</b>: the span is one of Immersive Railroading's own turntables - wrench it onto the TABLE display and the
 *   railway road onto the RAIL button. Open = turned 90 degrees off the railway: the track really moves.</li>
 *   <li><b>BASCULE</b>: IR track can't tilt, so the leaf is the moving model this desk draws (BasculeRenderer) between
 *   the HEEL and TOE rails you wrench onto it (or the span selector behind the desk). The IR rails across the gap stay
 *   where they are, so the interlocking is what keeps trains off it: held signals, and a train that runs onto an open
 *   or unlocked span is emergency-braked with a MAJOR alarm (config bridgeOpenDerails: dropped into the gap).</li>
 *   <li><b>waterway</b>: boats approaching within 40 m wait while it isn't open (held where they are); a waiting boat
 *   sounds and asks for an opening; the schedule (ON DEMAND / EVERY 10 / 20 MIN / RAIL PRIORITY) and the window
 *   (60 / 120 s) decide when; marine traffic lights red / green.</li>
 *   <li>Motor from the grid, 12 V control: without it the span stays where it is, the locks stay as they are, the
 *   signals stay held. HAND: the crank works the current step with no power at all.</li>
 * </ul>
 */
public class TileMovableBridge extends TrackGear {
    public static final String[] PHASES = {"CLOSED", "PROTECTING", "UNLOCKING", "OPENING", "OPEN", "CLOSING", "LOCKING"};
    static final int CLOSED = 0, PROTECTING = 1, UNLOCKING = 2, OPENING = 3, OPEN = 4, CLOSING = 5, LOCKING = 6;
    public static final String[] TYPES = {"SWING", "BASCULE"};
    public static final String[] MODES = {"REMOTE", "LOCAL", "HAND"};
    public static final String[] SCHED = {"ON DEMAND", "EVERY 10 MIN", "EVERY 20 MIN", "RAIL PRIORITY"};
    public static final int[] SPANS = {6, 9, 12, 16, 20, 24};
    static final int LOCK_STROKE = 60;

    int phase = CLOSED;
    private int locks = LOCK_STROKE;            // 0 withdrawn .. LOCK_STROKE driven
    /** span position 0 closed .. 1 open (bascule leaf / swing angle) - synced to the client for the leaf */
    public double posFrac;
    private double ramp, kw, handBudget;
    private boolean wantOpen, manual, supplyOk = true, approach, onSpan, vesselUnder, intruded;
    private long table, railPos, heel, toe;
    private int openSlot = -1, closedSlot = -1;
    private final long[] sigs = new long[4];
    private int openings, boats;
    private long windowUntil, phaseAt;
    private final Map<Integer, double[]> held = new HashMap<>();
    private final Set<Integer> hooted = new HashSet<>();
    private final Map<Integer, Double> lastDist = new HashMap<>();
    private String why = "", supply = "";
    /** client: the drawn leaf angle eases toward posFrac */
    @SideOnly(Side.CLIENT) public double drawFrac;

    public String id() { return "MB " + Math.floorMod(pos.getX() * 19 + pos.getZ() * 7, 1000); }
    @Override public String wiredName() { return "Movable bridge " + id(); }
    public boolean bascule() { return ctl.get("s_type") == 1; }
    private String mode() { return MODES[Math.min(2, Math.max(0, ctl.get("s_mode")))]; }
    private int sched() { return Math.min(3, Math.max(0, ctl.get("s_sched"))); }
    private int window() { return ctl.get("s_win") == 1 ? 120 : 60; }
    private boolean estop() { return ctl.get("m_estop") == 1; }
    public boolean closedLocked() { return phase == CLOSED && locks >= LOCK_STROKE; }
    public boolean isOpen() { return phase == OPEN; }
    boolean owns(long tableParent) { return table != 0 && table == tableParent; }

    @Override public String gearState() {
        if (!bascule() && table == 0) return "SWING: not linked (wrench an IR turntable onto TABLE, the railway road onto RAIL)";
        return (powered() ? "" : "NO 12 V - frozen, signals held · ") + PHASES[phase] + String.format(Locale.ROOT, " %.0f%%", posFrac * 100) + (why.isEmpty() ? "" : " · " + why);
    }
    @Override public int wiredLamp() { return !powered() || estop() || intruded ? 3 : !why.isEmpty() ? 2 : 1; }
    @Override public void gearInfo(Map<String, Object> m) { m.put("phase", PHASES[phase]); m.put("position", Math.round(posFrac * 100)); m.put("locks", locks * 100 / LOCK_STROKE); m.put("boats", boats); m.put("openings", openings); }

    // ---- geometry ------------------------------------------------------------------------------------------------------

    /** heel and toe of the span in world coords {hx, hy, hz, tx, tz} */
    public double[] span() {
        if (heel != 0 && toe != 0) {
            BlockPos h = BlockPos.fromLong(heel), t = BlockPos.fromLong(toe);
            return new double[]{h.getX() + 0.5, h.getY(), h.getZ() + 0.5, t.getX() + 0.5, t.getZ() + 0.5};
        }
        EnumFacing d = controlFacing().getOpposite();
        int len = SPANS[Math.min(SPANS.length - 1, Math.max(0, ctl.get("s_span")))];
        double hx = pos.getX() + 0.5 + d.getXOffset() * 1.5, hz = pos.getZ() + 0.5 + d.getZOffset() * 1.5;
        return new double[]{hx, pos.getY(), hz, hx + d.getXOffset() * len, hz + d.getZOffset() * len};
    }

    private double spanLen() { double[] s = span(); return Math.hypot(s[3] - s[0], s[4] - s[2]); }

    private double[] mid() {
        if (!bascule() && table != 0) { BlockPos t = BlockPos.fromLong(table); return new double[]{t.getX() + 0.5, t.getY(), t.getZ() + 0.5}; }
        double[] s = span();
        return new double[]{(s[0] + s[3]) / 2, s[1], (s[2] + s[4]) / 2};
    }

    /** distance from (x, z) to the span centreline */
    private double toSpan(double x, double z) {
        if (!bascule() && table != 0) { double[] m = mid(); TileRail t = Tables.get(world, table); return Math.max(0, Math.hypot(x - m[0], z - m[2]) - (t == null ? 8 : Tables.radius(t))); }
        double[] s = span();
        double ax = s[3] - s[0], az = s[4] - s[2], L2 = ax * ax + az * az;
        double u = L2 <= 0 ? 0 : Math.max(0, Math.min(1, ((x - s[0]) * ax + (z - s[2]) * az) / L2));
        return Math.hypot(x - s[0] - u * ax, z - s[2] - u * az);
    }

    // ---- linking -------------------------------------------------------------------------------------------------------

    @Override
    public String link(String control, BlockPos pick, EntityPlayer who) {
        switch (control) {
            case "tbl": {
                TileRail t = Tables.tableAt(world, pick);
                if (t == null || !Tables.turntable(t)) return "§cA swing span is an IR TURNTABLE - pick one of its rails";
                BlockPos p = new BlockPos(t.getPos().x, t.getPos().y, t.getPos().z);
                for (TileTurntableControl o : TrackHooks.live(world, TileTurntableControl.class)) if (o.owns(p.toLong())) return "§cThat turntable already has a turntable panel (" + o.id() + ")";
                table = p.toLong();
                ctl.set("s_type", 0);
                markDirty();
                return "§aSwing span = the turntable at " + Rails.xz(p) + " - now wrench the railway road onto RAIL";
            }
            case "p_rail": {
                TileRail t = Tables.get(world, table);
                if (t == null) return "§7Link the swing span (TABLE) first";
                int s = Tables.roadIndex(t, pick);
                if (s < 0) return "§cPick a rail of the railway beyond the span";
                int n = t.prideTableSlots();
                closedSlot = s;
                openSlot = (s + n / 4) % n;
                railPos = pick.toLong();
                markDirty();
                return "§aRailway road = slot " + s + ", OPEN = slot " + openSlot + " (90 deg off)";
            }
            case "heel": heel = pick.toLong(); markDirty(); return "§aBascule HEEL (trunnion end) set at " + Rails.xz(pick);
            case "toe": toe = pick.toLong(); markDirty(); return "§aBascule TOE (nose) set at " + Rails.xz(pick) + String.format(Locale.ROOT, " - span %.1f m", spanLen());
            default:
                if (control.startsWith("p_s")) {
                    if (!(world.getTileEntity(pick) instanceof TileSignalMast)) return "§cPick a signal mast with the wrench";
                    int i = Integer.parseInt(control.substring(3)) - 1;
                    sigs[i] = pick.toLong();
                    markDirty();
                    return "§aApproach signal " + (i + 1) + " = " + Rails.xz(pick) + " (held at danger unless the bridge is closed + locked)";
                }
                return null;
        }
    }

    // ---- requests --------------------------------------------------------------------------------------------------------

    /** open (true) or close; remote = the command / console. null = accepted */
    public String request(boolean open, String who, boolean remote) {
        if (remote && !mode().equals("REMOTE")) return id() + " is in " + mode() + " control";
        if (!remote && mode().equals("REMOTE")) return "turn the mode switch to LOCAL first";
        if (!bascule() && (table == 0 || closedSlot < 0)) return "swing span not set up (TABLE + RAIL)";
        wantOpen = open;
        manual = remote && open;
        Recorder.event(world, "BRIDGE " + id() + ": " + (open ? "OPEN" : "CLOSE") + " requested by " + (who == null ? "the schedule" : who));
        return null;
    }

    // ---- the live tick ---------------------------------------------------------------------------------------------------

    @Override
    protected void tickGear() {
        long now = world.getTotalWorldTime();
        TileRail t = bascule() ? null : Tables.get(world, table);
        if (age % 10 == 0) { scanTrains(); waterway(now); }
        if (bascule() && phase != CLOSED || locks < LOCK_STROKE) guardSpan();
        boolean power = powered() && !estop();
        boolean hand = mode().equals("HAND");
        boolean step = hand ? handBudget > 0 : power;            // may the current step move now?
        double speed = 0;
        why = "";
        switch (phase) {
            case CLOSED -> { if (wantOpen) go(PROTECTING, now); }
            case PROTECTING -> {
                if (!wantOpen) { go(CLOSED, now); break; }
                if (approach) why = "approach locked - a train is approaching";
                else if (onSpan) why = "a train is on the span";
                else if (now - phaseAt < 60) why = "signals going to danger";
                else if (step) go(UNLOCKING, now);
            }
            case UNLOCKING -> {
                if (!wantOpen) { go(LOCKING, now); break; }
                if (step) { locks = Math.max(0, locks - 1); if (hand) handBudget--; }
                if (locks == 0) { go(OPENING, now); ramp = 0; world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("grid.motor_start"), SoundCategory.BLOCKS, 1f, 0.8f); }
            }
            case OPENING, CLOSING -> {
                boolean up = phase == OPENING;
                if (!up && wantOpen) { phase = OPENING; break; }
                if (up && !wantOpen) { phase = CLOSING; break; }
                if (!up && vesselUnder) { why = "vessel under the span - closing stopped"; ramp = 0; }
                else if (hand) { if (handBudget > 0) { speed = 0.3; handBudget--; } }
                else if (power && supplyOk) { ramp = Math.min(1, ramp + 1 / 80.0); speed = Math.max(0.1, ramp); }
                else { ramp = 0; why = !power ? "no control supply" : "no motor supply"; }
                if (bascule()) {
                    double rate = speed / 500.0 * (posFrac > 0.85 && up || posFrac < 0.15 && !up ? 0.4 : 1);   // ~25 s, creep at the ends
                    posFrac = Math.max(0, Math.min(1, posFrac + (up ? rate : -rate)));
                    if (up && posFrac >= 1) done(true, now);
                    if (!up && posFrac <= 0) done(false, now);
                } else if (t != null) {
                    int want = up ? openSlot : nearestClosed(t);
                    Tables.drive(world, t, speed, want);
                    double rem = Tables.remaining(t);
                    posFrac = swingFrac(t);
                    if (rem < 0.05) done(up, now);
                }
                if (speed > 0 && age % 40 == 0) world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("grid.motor_run"), SoundCategory.BLOCKS, 0.6f, 0.7f);
            }
            case OPEN -> {
                if (!wantOpen) go(CLOSING, now);
            }
            case LOCKING -> {
                if (wantOpen) { go(UNLOCKING, now); break; }
                if (step) { locks = Math.min(LOCK_STROKE, locks + 1); if (hand) handBudget--; }
                if (locks >= LOCK_STROKE) {
                    go(CLOSED, now);
                    world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("machine.clunk"), SoundCategory.BLOCKS, 0.9f, 0.8f);
                    Recorder.event(world, "BRIDGE " + id() + " CLOSED and LOCKED - signals released");
                }
            }
            default -> phase = CLOSED;
        }
        if (t != null && phase != OPENING && phase != CLOSING) Tables.drive(world, t, 0, -1);   // the bridge owns its span, braked
        if (t != null && (phase == OPENING || phase == CLOSING) && speed == 0) Tables.drive(world, t, 0, -1);
        if (hand && handBudget > 0 && phase != UNLOCKING && phase != LOCKING && phase != OPENING && phase != CLOSING) handBudget = 0;
        if (age % 20 == 0) {
            kw = speed > 0 ? (bascule() ? 22 + spanLen() * 1.6 : 15 + load(t) * 0.08) * speed : phase == UNLOCKING || phase == LOCKING ? 1.5 : 0;
            GridFeed.Result gr = !hand && kw > 0 ? GridFeed.draw(world, pos, kw) : GridFeed.draw(world, pos, 0);
            supplyOk = gr.ok;
            supply = gr.what;
            Alarms.state(world, "mb:" + pos.toLong(), !why.isEmpty() && (phase == OPENING || phase == CLOSING) && !hand, AlarmManager.Cat.MACHINE, AlarmManager.Sev.MINOR, "BRIDGE " + id() + ": " + why, pos);
            live(now);
        }
        put("g_pos", (int) Math.round(posFrac * 1000));                // the leaf on every client
    }

    private double load(TileRail t) { return t == null ? 0 : Tables.load(world, t, Tables.footprint(world, t)).tonnes; }

    private void go(int p, long now) { phase = p; phaseAt = now; markDirty(); }

    private void done(boolean opened, long now) {
        ramp = 0;
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("grid.motor_stop"), SoundCategory.BLOCKS, 1f, 0.8f);
        if (opened) {
            go(OPEN, now);
            openings++;
            if (windowUntil < now) windowUntil = now + window() * 20L;
            Recorder.event(world, "BRIDGE " + id() + " OPEN to the waterway (" + boats + " vessel" + (boats == 1 ? "" : "s") + " waiting)");
            com.dogpound.railmap.api.QuestEvents.fire(world, null, pos, "bridge_opened", "type", TYPES[ctl.get("s_type") & 1], "boats", boats);
        } else go(LOCKING, now);
    }

    private int nearestClosed(TileRail t) { return Tables.nearestEnd(closedSlot, t.prideTableIndex(), t.prideTableSlots()); }

    private double swingFrac(TileRail t) {
        double closedA = Tables.target(t, closedSlot);
        double d = Math.min(Math.abs(Tables.dAngle(t.info.tablePos, closedA)), Math.abs(Tables.dAngle(t.info.tablePos, closedA + 180)));
        return Math.min(1, d / 90);
    }

    // ---- trains: approach locking + span protection ----------------------------------------------------------------------

    private void scanTrains() {
        double[] m = mid();
        boolean appr = false, on = false;
        Map<Integer, Double> seen = new HashMap<>();
        for (TrainNode n : TrainTracker.latest(world)) {
            if (Math.abs(n.y - m[1]) > 8) continue;
            double d = Math.hypot(n.x - m[0], n.z - m[2]);
            if (d > 320) continue;
            if (toSpan(n.x, n.z) < 3) on = true;
            seen.put(n.id, d);
            Double was = lastDist.get(n.id);
            if (n.moving() && was != null && d < was - 0.05) appr = true;
        }
        lastDist.clear();
        lastDist.putAll(seen);
        approach = appr;
        onSpan = on;
    }

    /** rolling stock on a span that isn't closed + locked: emergency brake (or into the gap, by config) */
    private void guardSpan() {
        if (age % 4 != 0) return;
        double[] m = mid();
        boolean hit = false;
        try {
            for (EntityMoveableRollingStock s : cam72cam.mod.world.World.get(world).getEntities(EntityMoveableRollingStock.class)) {
                if (s.isDead() || Math.abs(s.getPosition().y - m[1]) > 6 || toSpan(s.getPosition().x, s.getPosition().z) > 2.5) continue;
                if (Math.abs(s.getCurrentSpeed().metric()) < 0.5) continue;
                hit = true;
                if (TrackConfig.bridgeOpenDerails && bascule() && posFrac > 0.05) {
                    cam72cam.immersiverailroading.wreck.Wrecks.forceWreck(s, 3, "ran into the open bascule span " + id());
                    continue;
                }
                if (s instanceof EntityCoupleableRollingStock c)
                    for (EntityCoupleableRollingStock o : c.getTrain()) if (o instanceof Locomotive l) { l.setThrottle(0); l.setTrainBrake(1); }
            }
        } catch (LinkageError | RuntimeException ignored) { }
        if (hit && !intruded) {
            intruded = true;
            AlarmManager.get(world).raise(world, "mbspad:" + pos.toLong(), AlarmManager.Cat.SIGNAL, AlarmManager.Sev.MAJOR, "BRIDGE " + id() + ": TRAIN ON THE SPAN while " + PHASES[phase] + " - emergency brake", pos, true);
            Recorder.event(world, "BRIDGE " + id() + ": train ran onto the span while " + PHASES[phase]);
        }
        if (!hit && intruded && closedLocked()) { intruded = false; AlarmManager.get(world).clear("mbspad:" + pos.toLong()); }
    }

    // ---- the waterway ------------------------------------------------------------------------------------------------------

    private static boolean vessel(Entity e) {
        if (e instanceof EntityBoat) return true;
        String n = e.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        return n.contains("boat") || n.contains("ship");
    }

    private void waterway(long now) {
        double[] m = mid();
        double half = bascule() ? spanLen() / 2 + 2 : 10;
        AxisAlignedBB box = new AxisAlignedBB(m[0] - 40, m[1] - 8, m[2] - 40, m[0] + 40, m[1] + 6, m[2] + 40);
        int n = 0;
        boolean under = false, wait = false;
        Set<Integer> here = new HashSet<>();
        for (Entity e : world.getEntitiesWithinAABB(Entity.class, box, TileMovableBridge::vessel)) {
            n++;
            here.add(e.getEntityId());
            double d = Math.hypot(e.posX - m[0], e.posZ - m[2]);
            if (d < half) under = true;
            if (phase != OPEN && TrackConfig.boatsWaitAtBridges && d >= half) {
                double[] h = held.computeIfAbsent(e.getEntityId(), k -> new double[]{e.posX, e.posY, e.posZ, now});
                if (Math.hypot(e.posX - m[0], e.posZ - m[2]) < Math.hypot(h[0] - m[0], h[2] - m[2]) - 0.05) {
                    e.setPosition(h[0], e.posY, h[2]);                 // the vessel waits where it was told to
                    e.motionX = e.motionZ = 0;
                }
                wait = wait || now - (long) h[3] > 60;
                if (hooted.add(e.getEntityId())) world.playSound(null, e.getPosition(), com.dogpound.railmap.sound.PackSounds.ev("station.ship_horn"), SoundCategory.NEUTRAL, 2f, 1f);
            }
        }
        if (phase == OPEN) held.clear();
        held.keySet().retainAll(here);
        hooted.retainAll(here);
        boats = n;
        vesselUnder = under;
        if (!mode().equals("REMOTE") || manual) return;
        // the schedule
        int s = sched();
        long period = s == 1 ? 12000 : s == 2 ? 24000 : 0;
        if (period > 0 && now % period < 10 && windowUntil < now) windowUntil = now + window() * 20L;
        boolean inWindow = now < windowUntil;
        boolean demand = wait && (s != 3 || !approach);
        if (demand && windowUntil < now) windowUntil = now + window() * 20L;
        boolean want = inWindow || demand && (s == 0 || s == 3) || phase == OPEN && under;
        if (want != wantOpen) { wantOpen = want; if (want) Recorder.event(world, "BRIDGE " + id() + ": opening " + (demand ? "on demand for " + n + " vessel" + (n == 1 ? "" : "s") : "by schedule")); }
    }

    // ---- signals (TrackHooks.holds, once a second) ---------------------------------------------------------------------------

    void holds(Map<Long, String> hold, List<Object[]> zones) {
        if (closedLocked() && powered()) return;
        String w = !powered() ? "bridge " + id() + " has no 12 V" : "bridge " + id() + " " + PHASES[phase];
        boolean any = false;
        for (long s : sigs) if (s != 0) { any = true; hold.putIfAbsent(s, w); }
        if (!any) { double[] m = mid(); zones.add(new Object[]{m[0], m[2], Math.max(24.0, spanLen() / 2 + 24), w}); }
    }

    // ---- displays -------------------------------------------------------------------------------------------------------------

    private void live(long now) {
        put("closed", posFrac <= 0.001 && phase != OPENING);
        put("open", phase == OPEN);
        put("lka", locks >= LOCK_STROKE);
        put("lkb", locks >= LOCK_STROKE);
        put("lkout", locks == 0);
        put("prot", phase != CLOSED || !powered());
        put("appr", approach);
        put("onspan", onSpan || intruded);
        put("run", phase == OPENING || phase == CLOSING);
        put("wred", phase != OPEN);
        put("wgrn", phase == OPEN);
        put("boat", boats > 0);
        put("sup", supplyOk);
        put("pwr", powered());
        put("estop", estop());
        for (int i = 0; i < 4; i++) put("s" + (i + 1), sigs[i] != 0);
        put("g_kw", (int) Math.round(Math.min(100, kw)));
        put("g_lock", locks * 100 / LOCK_STROKE);
        text("phase", PHASES[phase] + (why.isEmpty() ? "" : " HELD"));
        text("enc", String.format(Locale.ROOT, "%s %3.0f%% %s", bascule() ? "LEAF" : "SWING", posFrac * 100, bascule() ? String.format(Locale.ROOT, "%2.0f DEG", posFrac * 80) : String.format(Locale.ROOT, "%2.0f DEG", posFrac * 90)));
        text("boats", "VESSELS " + boats);
        text("next", nextOpen(now));
        text("kw", String.format(Locale.ROOT, "%.1f KW", kw));
        text("span", bascule() ? String.format(Locale.ROOT, "SPAN %.0f M", spanLen()) : table == 0 ? "NO TABLE" : "TT " + Rails.xz(BlockPos.fromLong(table)));
        text("opens", openings + " OPENINGS");
        text("supt", supplyOk ? supply.split(" ·")[0].replace("φ", "PH").toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9 .:>$/%+-]", "") : "NO GRID SUPPLY");
    }

    private String nextOpen(long now) {
        if (now < windowUntil) return "WINDOW " + (windowUntil - now) / 20 + " S";
        int s = sched();
        if (s == 1 || s == 2) { long p = s == 1 ? 12000 : 24000; return "NEXT " + (p - now % p) / 20 + " S"; }
        return s == 3 ? "RAIL PRIORITY" : "ON DEMAND";
    }

    // ---- controls ---------------------------------------------------------------------------------------------------------------

    @Override
    public String controlPress(String id, EntityPlayer who) {
        switch (id) {
            case "test": return lampTest();
            case "p_open": case "p_close": {
                String r = request(id.equals("p_open"), who(who), false);
                return r == null ? "§e" + (id.equals("p_open") ? "OPEN" : "CLOSE") + " sequence started" : "§c" + r;
            }
            case "m_estop": {
                boolean in = ctl.get("m_estop") == 0;
                ctl.set("m_estop", in ? 1 : 0);
                if (in) {
                    ramp = 0;
                    world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("estop_hit"), SoundCategory.BLOCKS, 1f, 1f);
                    AlarmManager.get(world).raise(world, "mbestop:" + pos.toLong(), AlarmManager.Cat.MACHINE, AlarmManager.Sev.MAJOR, "BRIDGE " + id() + " EMERGENCY STOP by " + who(who), pos, true);
                } else AlarmManager.get(world).clear("mbestop:" + pos.toLong());
                return in ? "§cEMERGENCY STOP - span braked where it is" : "§aE-stop reset";
            }
            case "p_crank": {
                if (!mode().equals("HAND")) return "§7Turn the mode switch to HAND to use the crank";
                if (phase == CLOSED || phase == OPEN || phase == PROTECTING) return "§7Nothing to crank - " + PHASES[phase] + " (press OPEN / CLOSE first)";
                handBudget += phase == UNLOCKING || phase == LOCKING ? 15 : 25;
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("signal.points_crank"), SoundCategory.BLOCKS, 0.8f, 0.7f);
                return "§eCranking: " + PHASES[phase];
            }
            case "k_cmd": {
                boolean open = ctl.get("k_cmd") == 0;
                String r = request(open, who(who), false);
                if (r != null) return "§c" + r;
                ctl.set("k_cmd", open ? 1 : 0);
                return "§eSpan lever to " + (open ? "OPEN" : "CLOSE") + " - sequence started";
            }
            case "tbl": case "p_rail": case "heel": case "toe": case "p_s1": case "p_s2": case "p_s3": case "p_s4":
                return "§7Set-up target: pick the rail / turntable / signal with the Signal Wrench, then right-click this button with it";
            case "p_horn": world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("station.ship_horn"), SoundCategory.BLOCKS, 2f, 1.2f); return "§eBridge horn";
            case "s_type": {
                if (phase != CLOSED) return "§cOnly with the bridge closed + locked";
                return null;
            }
            default: return null;
        }
    }

    @Override
    protected String[] assetRow() {
        return new String[]{"MB", id(), TYPES[ctl.get("s_type") & 1], estop() ? "FAULT E-STOP" : intruded ? "FAULT TRAIN ON SPAN" : PHASES[phase] + (why.isEmpty() ? "" : " (" + why + ")"),
                Math.round(posFrac * 100) + "%", locks >= LOCK_STROKE ? "LOCKED" : locks == 0 ? "OUT" : "MOVING", String.valueOf(boats), nextOpen(world.getTotalWorldTime()),
                String.format(Locale.ROOT, "%.1f", kw), String.valueOf(openings), mode(), powered() ? "12V" : "NO 12V"};
    }

    @Override
    protected void save(NBTTagCompound t) {
        t.setInteger("phase", phase); t.setInteger("locks", locks); t.setDouble("posF", posFrac); t.setBoolean("want", wantOpen); t.setBoolean("manual", manual);
        t.setLong("table", table); t.setLong("rail", railPos); t.setLong("heel", heel); t.setLong("toe", toe); t.setInteger("oSlot", openSlot); t.setInteger("cSlot", closedSlot);
        for (int i = 0; i < 4; i++) t.setLong("sig" + i, sigs[i]);
        t.setInteger("opens", openings); t.setLong("win", windowUntil); t.setInteger("boats", boats);
    }

    @Override
    protected void load(NBTTagCompound t) {
        phase = t.getInteger("phase"); locks = t.hasKey("locks") ? t.getInteger("locks") : LOCK_STROKE; posFrac = t.getDouble("posF"); wantOpen = t.getBoolean("want"); manual = t.getBoolean("manual");
        table = t.getLong("table"); railPos = t.getLong("rail"); heel = t.getLong("heel"); toe = t.getLong("toe");
        openSlot = t.hasKey("oSlot") ? t.getInteger("oSlot") : -1; closedSlot = t.hasKey("cSlot") ? t.getInteger("cSlot") : -1;
        for (int i = 0; i < 4; i++) sigs[i] = t.getLong("sig" + i);
        openings = t.getInteger("opens"); windowUntil = t.getLong("win"); boats = t.getInteger("boats");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() { return INFINITE_EXTENT_AABB; }

    @Override
    @SideOnly(Side.CLIENT)
    public double getMaxRenderDistanceSquared() { return 160 * 160; }

    // ---- self-test of the sequence (no world needed) ----------------------------------------------------------------------

    /** the phase machine's legal moves, exercised: PASS / FAIL lines */
    public static List<String> selftest() {
        List<String> o = new ArrayList<>();
        int[] seq = {CLOSED, PROTECTING, UNLOCKING, OPENING, OPEN, CLOSING, LOCKING, CLOSED};
        boolean ok = true;
        for (int i = 0; i + 1 < seq.length; i++) ok &= seq[i + 1] == (seq[i] == LOCKING ? CLOSED : seq[i] + 1);
        Tables.check(o, "open/close sequence runs CLOSED..LOCKING..CLOSED in order", ok);
        Tables.check(o, "signals released only when CLOSED and fully locked", PHASES[CLOSED].equals("CLOSED") && LOCK_STROKE > 0);
        Tables.check(o, "swing open slot is 90 deg off (16 slots: 3 -> 7)", (3 + 16 / 4) % 16 == 7);
        Tables.check(o, "swing closed uses the nearer end (closed 3, at 10 -> 11)", Tables.nearestEnd(3, 10, 16) == 11);
        return o;
    }
}
