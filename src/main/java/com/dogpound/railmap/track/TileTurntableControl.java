package com.dogpound.railmap.track;

import cam72cam.immersiverailroading.tile.TileRail;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.graph.TrainNode;
import com.dogpound.railmap.server.TrainTracker;
import com.dogpound.railmap.signal.SignalRegistry;
import com.dogpound.railmap.signal.TileSignalMast;
import cam72cam.mod.math.Vec3i;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Turntable / traverser operator's panel (her ChatGPT batch 3 #3). It takes over one of Immersive Railroading's own
 * TURNTABLE or TRANSFERTABLE pieces - IR really turns / shifts the track and every vehicle standing on it - and adds
 * what a real one has around it:
 * <ul>
 *   <li><b>roads</b>: wrench a rail at the end of each exit road onto a ROAD button (up to 8); a turntable serves a road
 *   from either end of its bridge and always takes the shorter turn;</li>
 *   <li><b>the motor</b>: soft start, full speed, creep the last few degrees / the last metre, drawn from the grid
 *   (kW rises with the load and with an off-centre load), E-STOP brakes it where it is;</li>
 *   <li><b>alignment sensors + locking pins</b>: ALIGNED only once the bridge has stopped dead on a slot; the pins (2 s
 *   stroke, electric) go IN only when aligned and must be OUT before it can move;</li>
 *   <li><b>interlocks</b>: no move with pins in, stock moving on the table, a vehicle fouling the pit edge / still
 *   coupled off the table, an overload for the table's rating, a badly unbalanced load, or a train approaching
 *   within 60 m (approach locking); every signal near the pit is held at danger unless the table is aligned AND pinned,
 *   and then only signals on the aligned road(s) are released;</li>
 *   <li>REMOTE (the console / command), LOCAL (this panel) or HAND (the crank: each turn moves it a little, no power
 *   needed). No 12 V = it stops where it is and the pins stay put (fail safe).</li>
 * </ul>
 */
public class TileTurntableControl extends TrackGear {
    public static final String[] MODES = {"REMOTE", "LOCAL", "HAND"};
    public static final int[] RATING_T = {120, 250, 400};
    static final int PIN_STROKE = 40, ROADS = 8;

    private long table;                         // the IR table's parent TileRail position, 0 = none
    private final int[] road = new int[ROADS];  // slot / entry per road, -1 = not taught
    private final long[] roadPos = new long[ROADS];
    private int target = -1;                    // road being turned to (-1 = none / jog)
    private int pin;                            // 0 = out .. PIN_STROKE = in
    private double ramp, kw, amps, handBudget;
    private boolean supplyOk = true, moving, aligned, approach;
    private int moves, crank;
    private String why = "", operator = "", supply = "";
    private double[] fp;
    private Tables.Load load = new Tables.Load();
    private final Map<Integer, Double> lastDist = new HashMap<>();

    public TileTurntableControl() { Arrays.fill(road, -1); }

    public String id() { return "TT " + Math.floorMod(pos.getX() * 13 + pos.getZ() * 31, 1000); }
    @Override public String wiredName() { return "Turntable panel " + id(); }

    private String mode() { return MODES[Math.min(2, Math.max(0, ctl.get("s_mode")))]; }
    private int rating() { return RATING_T[Math.min(2, Math.max(0, ctl.get("s_rate")))]; }
    private boolean estop() { return ctl.get("m_estop") == 1; }
    public boolean pinnedIn() { return pin >= PIN_STROKE; }
    public boolean pinsOut() { return pin == 0; }
    TileRail table() { return Tables.get(world, table); }
    boolean owns(long tableParent) { return table != 0 && table == tableParent; }

    @Override public String gearState() {
        if (table == 0) return "not linked (Signal Wrench: pick a rail of an IR turntable / transfer table, click the TABLE display)";
        if (!powered()) return "NO 12 V - stopped, pins held";
        return (moving ? "MOVING" : aligned ? "ALIGNED" : "STOPPED") + (pinnedIn() ? " · PINNED" : "") + (estop() ? " · E-STOP" : "") + (why.isEmpty() ? "" : " · " + why);
    }
    @Override public int wiredLamp() { return !powered() || estop() ? 3 : !why.isEmpty() ? 2 : 1; }
    @Override public void gearInfo(Map<String, Object> m) { m.put("table", table == 0 ? "none" : Rails.xz(BlockPos.fromLong(table))); m.put("aligned", aligned); m.put("pinned", pinnedIn()); m.put("moves", moves); m.put("tonnes", Math.round(load.tonnes)); }

    // ---- linking -----------------------------------------------------------------------------------------------------

    @Override
    public String link(String control, BlockPos pick, EntityPlayer who) {
        if (control.equals("tbl")) {
            TileRail t = Tables.tableAt(world, pick);
            if (t == null) return "§cThat isn't an IR turntable or transfer table - pick one of its rails";
            BlockPos p = new BlockPos(t.getPos().x, t.getPos().y, t.getPos().z);
            if (p.distanceSq(pos) > 64 * 64) return "§cThat table is too far away (64 blocks)";
            for (TileTurntableControl o : TrackHooks.live(world, TileTurntableControl.class)) if (o != this && o.table == p.toLong()) return "§cThat table already has a panel (" + o.id() + ")";
            for (TileMovableBridge o : TrackHooks.live(world, TileMovableBridge.class)) if (o.owns(p.toLong())) return "§cThat table is a swing bridge span (" + o.id() + ")";
            table = p.toLong();
            fp = null;
            Arrays.fill(road, -1);
            target = -1;
            markDirty();
            return "§a" + id() + " now drives the " + (Tables.turntable(t) ? "turntable (" + t.prideTableSlots() + " slots)" : "transfer table (" + t.prideTableSlots() + " roads)") + " at " + Rails.xz(p) + " - teach its roads with the ROAD buttons";
        }
        if (control.startsWith("p_r")) {
            int n = Integer.parseInt(control.substring(3)) - 1;
            TileRail t = table();
            if (t == null) return "§7Link the table first (TABLE display)";
            int idx = Tables.roadIndex(t, pick);
            if (idx < 0) return "§cPick a rail at the END of an exit road, out past the pit";
            road[n] = idx;
            roadPos[n] = pick.toLong();
            markDirty();
            return "§aRoad " + (n + 1) + " = " + (Tables.turntable(t) ? "slot " + idx + String.format(Locale.ROOT, " (%.1f deg)", Tables.target(t, idx)) : "entry " + (idx + 1));
        }
        return null;
    }

    // ---- remote / panel requests --------------------------------------------------------------------------------------

    /** send the table to road n (0-based); null = started, else why not */
    public String request(int n, String who, boolean remote) {
        TileRail t = table();
        if (t == null) return "no table linked / loaded";
        if (n < 0 || n >= ROADS || road[n] < 0) return "road " + (n + 1) + " not taught";
        if (remote && !mode().equals("REMOTE")) return id() + " is in " + mode() + " control";
        if (!remote && mode().equals("REMOTE")) return "turn the mode switch to LOCAL first";
        String no = interlock(t, false);
        if (no != null && !mode().equals("HAND")) return no;
        int idx = Tables.turntable(t) ? Tables.nearestEnd(road[n], t.prideTableIndex(), t.prideTableSlots()) : road[n];
        target = n;
        operator = who == null ? "" : who;
        Tables.drive(world, t, 0, idx);
        if (Tables.remaining(t) > 0.01) {
            ramp = 0;
            world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("grid.motor_start"), SoundCategory.BLOCKS, 0.9f, 1.0f);
            Recorder.event(world, "TURNTABLE " + id() + ": turning to road " + (n + 1) + (who == null ? "" : " (" + who + ")"));
        }
        markDirty();
        return null;
    }

    /** null = the table may move now; else the interlock that stops it */
    private String interlock(TileRail t, boolean moveOnly) {
        if (!powered() && !mode().equals("HAND")) return "no 12 V control supply";
        if (estop()) return "EMERGENCY STOP pulled - reset it first";
        if (!pinsOut()) return "locking pins are " + (pinnedIn() ? "IN" : "moving") + " - withdraw them first";
        if (load.moving) return "stock is moving on the table";
        if (load.fouling) return "FOULING: " + load.foulWhy;
        if (load.tonnes > rating()) return String.format(Locale.ROOT, "OVERLOAD: %.0f t on a %d t table", load.tonnes, rating());
        if (load.offsetPct > 35) return String.format(Locale.ROOT, "UNBALANCED %.0f%% off centre - move the load to the middle", load.offsetPct);
        if (approach) return "approach locked - a train is approaching the table";
        return null;
    }

    // ---- the live tick ----------------------------------------------------------------------------------------------

    @Override
    protected void tickGear() {
        TileRail t = table();
        if (t == null) { moving = false; if (age % 20 == 0) live(null); return; }
        if (fp == null || age % 600 == 0) fp = Tables.footprint(world, t);
        if (age % 10 == 0) { load = Tables.load(world, t, fp); approach = approaching(t); }
        double rem = Tables.remaining(t);
        boolean wants = rem > 0.01;
        String no = wants ? interlock(t, true) : null;
        double speed = 0;
        if (mode().equals("HAND")) {
            if (wants && handBudget > 0 && pinsOut() && !load.moving && !load.fouling) { speed = 0.25; handBudget--; }
        } else if (wants && no == null && supplyOk) {
            ramp = Math.min(1, ramp + 1 / 60.0);                     // 3 s soft start
            double creep = Tables.turntable(t) ? 6 : 1.5;
            speed = Math.max(0.12, rem < creep ? Math.min(ramp, 0.25) : ramp);
        } else ramp = 0;
        Tables.drive(world, t, speed, -1);                             // the panel owns the table every tick
        boolean was = moving;
        moving = speed > 0 && wants;
        why = wants && !moving ? (mode().equals("HAND") ? "HAND - turn the crank" : no != null ? no : !supplyOk ? "no motor supply" : "") : "";
        if (was && !moving && !wants) arrived(t);
        aligned = !wants && slotOk(t);
        // pins: electric, 2 s stroke, only in when aligned
        boolean pinCmd = ctl.get("k_pins") == 1;
        if (powered()) {
            if (pinCmd && aligned && pin < PIN_STROKE) { pin++; if (pin == PIN_STROKE) world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("machine.clunk"), SoundCategory.BLOCKS, 0.8f, 0.9f); }
            if (!pinCmd && pin > 0 && !approach) pin--;
        }
        if (moving && age % 40 == 0) world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("grid.motor_run"), SoundCategory.BLOCKS, 0.5f, 0.8f + (float) ramp * 0.3f);
        // motor power, once a second, from the grid
        if (age % 20 == 0) {
            kw = moving ? (5.5 + 0.09 * load.tonnes * (1 + load.offsetPct / 50)) * Math.max(0.3, speed) : 0;
            GridFeed.Result r = wants && !mode().equals("HAND") ? GridFeed.draw(world, pos, Math.max(1, kw)) : GridFeed.draw(world, pos, 0);
            supplyOk = r.ok;
            supply = r.what;
            amps = kw * 1000 / (Math.sqrt(3) * 400 * 0.85);
            Alarms.state(world, "tt:" + pos.toLong(), wants && !moving && !mode().equals("HAND") && !why.isEmpty() && !estop(), AlarmManager.Cat.MACHINE, AlarmManager.Sev.MINOR, "TURNTABLE " + id() + " held: " + why, pos);
            live(t);
        }
    }

    private boolean slotOk(TileRail t) { return Tables.remaining(t) < 0.05; }

    private void arrived(TileRail t) {
        moves++;
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("grid.motor_stop"), SoundCategory.BLOCKS, 0.9f, 1.0f);
        int r = alignedRoad(t);
        Recorder.event(world, "TURNTABLE " + id() + " aligned" + (r >= 0 ? " to road " + (r + 1) : "") + String.format(Locale.ROOT, " (%.0f t on the table)", load.tonnes));
        EntityPlayer p = operator.isEmpty() ? null : world.getPlayerEntityByName(operator);
        com.dogpound.railmap.api.QuestEvents.fire(world, p, pos, "table_turned", "road", r + 1, "tonnes", Math.round(load.tonnes), "kind", Tables.turntable(t) ? "turntable" : "traverser");
        markDirty();
    }

    /** the taught road the table now serves (-1 = none) */
    public int alignedRoad(TileRail t) {
        int cur = t.prideTableIndex(), n = t.prideTableSlots();
        for (int i = 0; i < ROADS; i++) if (road[i] >= 0 && (road[i] == cur || Tables.turntable(t) && (road[i] + n / 2) % n == cur)) return i;
        return -1;
    }

    /** a train within 60 m of the pit edge closing on it (and not already on the table) */
    private boolean approaching(TileRail t) {
        double[] c = Tables.centre(t, fp);
        double r = Tables.turntable(t) ? Tables.radius(t) : Math.max(fp[2] - fp[0], fp[3] - fp[1]) / 2;
        boolean any = false;
        Map<Integer, Double> seen = new HashMap<>();
        for (TrainNode n : TrainTracker.latest(world)) {
            if (Math.abs(n.y - fp[4]) > 6) continue;
            double d = Math.hypot(n.x - c[0], n.z - c[1]);
            if (d > r + 60) continue;
            seen.put(n.id, d);
            Double was = lastDist.get(n.id);
            if (d > r + 1 && n.moving() && was != null && d < was - 0.05) any = true;
        }
        lastDist.clear();
        lastDist.putAll(seen);
        return any;
    }

    // ---- signals: the interlocking around the pit (TrackHooks.holds, once a second) ------------------------------------

    void holds(Map<Long, String> hold, List<Object[]> zones) {
        TileRail t = table();
        if (t == null || fp == null) return;
        double[] c = Tables.centre(t, fp);
        double reach = (Tables.turntable(t) ? Tables.radius(t) : Math.max(fp[2] - fp[0], fp[3] - fp[1]) / 2) + 24;
        boolean locked = aligned && pinnedIn() && powered() && !estop();
        int served = locked ? alignedRoad(t) : -1;
        String why = !powered() ? "turntable " + id() + " has no 12 V" : moving ? "turntable " + id() + " moving" : "turntable " + id() + " not aligned + pinned";
        for (TileSignalMast m : SignalRegistry.masts(world.provider.getDimension())) {
            Vec3i g = m.governedRail();
            if (g == null || Math.hypot(g.x + 0.5 - c[0], g.z + 0.5 - c[1]) > reach || Math.abs(g.y - fp[4]) > 6) continue;
            if (locked) {
                int near = -1;
                double nd = 1e9;
                boolean any = false;
                for (int i = 0; i < ROADS; i++) {
                    if (road[i] < 0) continue;
                    any = true;
                    BlockPos rp = BlockPos.fromLong(roadPos[i]);
                    double d = Math.hypot(rp.getX() - g.x, rp.getZ() - g.z);
                    if (d < nd) { nd = d; near = i; }
                }
                if (!any) continue;                                    // no roads taught: aligned + pinned clears all
                int n = t.prideTableSlots();
                boolean ok = near >= 0 && (near == served || road[near] >= 0 && served >= 0 && (road[near] == t.prideTableIndex() || Tables.turntable(t) && (road[near] + n / 2) % n == t.prideTableIndex()));
                if (ok) continue;
                hold.putIfAbsent(m.getPos().toLong(), "turntable " + id() + " set for another road");
            } else hold.putIfAbsent(m.getPos().toLong(), why);
        }
    }

    // ---- displays ------------------------------------------------------------------------------------------------------

    private void live(TileRail t) {
        int ar = t == null ? -1 : alignedRoad(t);
        for (int i = 0; i < ROADS; i++) {
            put("r" + (i + 1), aligned && ar == i);
            put("t" + (i + 1), road[i] >= 0);
        }
        put("aln", aligned);
        put("pin_in", pinnedIn());
        put("pin_out", pinsOut());
        put("run", moving);
        put("stock", !load.on.isEmpty());
        put("unbal", load.offsetPct > 15);
        put("foul", load.fouling);
        put("ovl", load.tonnes > rating());
        put("appr", approach);
        put("sup", supplyOk);
        put("pwr", powered());
        put("estop", estop());
        put("g_amps", (int) Math.round(Math.min(200, amps)));
        put("g_load", (int) Math.round(Math.min(150, load.tonnes * 100 / rating())));
        text("tbl", t == null ? (table == 0 ? "NOT LINKED" : "NOT LOADED") : (Tables.turntable(t) ? "TURNTABLE " : "TRAVERSER ") + Rails.xz(new BlockPos(t.getPos().x, 0, t.getPos().z)));
        text("pos", t == null ? "--" : Tables.turntable(t) ? String.format(Locale.ROOT, "%05.1f DEG", t.info.tablePos) : String.format(Locale.ROOT, "%5.2f M", t.info.tablePos));
        text("road", target < 0 ? "ROAD --" : "ROAD " + (target + 1) + (moving ? " >" : aligned ? " OK" : ""));
        text("kw", String.format(Locale.ROOT, "%.1f KW %.0f A", kw, amps));
        text("load", String.format(Locale.ROOT, "%.0f T %.0f%%", load.tonnes, load.offsetPct));
        text("state", !powered() ? "NO 12V" : estop() ? "E-STOP" : moving ? "MOVING" : !why.isEmpty() ? "HELD" : pinnedIn() ? "LOCKED" : aligned ? "ALIGNED" : "STOPPED");
        text("pins", pinnedIn() ? "PINS IN" : pinsOut() ? "PINS OUT" : "PINS " + pin * 100 / PIN_STROKE + "%");
        text("moves", moves + " MOVES");
        text("supt", supplyOk ? supply.split(" ·")[0].replace("φ", "PH").toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9 .:>$/%+-]", "") : "NO GRID SUPPLY");
    }

    // ---- the controls ------------------------------------------------------------------------------------------------

    @Override
    public String controlPress(String id, EntityPlayer who) {
        TileRail t = table();
        switch (id) {
            case "test": return lampTest();
            case "p_go": {
                if (target < 0) return "§7Pick a ROAD first";
                String r = request(target, who(who), false);
                return r == null ? "§eTurning to road " + (target + 1) : "§c" + r;
            }
            case "p_stop": {
                if (t == null) return "§7No table";
                Tables.drive(world, t, 0, Tables.turntable(t) ? nearestSlot(t) : (int) Math.round(t.info.tablePos / Math.max(1, t.info.settings.transfertableEntrySpacing)));
                Recorder.event(world, "TURNTABLE " + id() + " stopped by " + who(who));
                return "§eStopping on the nearest slot";
            }
            case "m_estop": {
                boolean in = ctl.get("m_estop") == 0;
                ctl.set("m_estop", in ? 1 : 0);
                if (in) {
                    ramp = 0;
                    world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("estop_hit"), SoundCategory.BLOCKS, 1f, 1f);
                    AlarmManager.get(world).raise(world, "ttestop:" + pos.toLong(), AlarmManager.Cat.MACHINE, AlarmManager.Sev.MAJOR, "TURNTABLE " + id() + " EMERGENCY STOP by " + who(who), pos, true);
                    Recorder.event(world, "TURNTABLE " + id() + " EMERGENCY STOP by " + who(who));
                } else AlarmManager.get(world).clear("ttestop:" + pos.toLong());
                return in ? "§cEMERGENCY STOP - motor braked" : "§aE-stop reset";
            }
            case "p_jogl": case "p_jogr": {
                if (t == null) return "§7No table";
                if (mode().equals("REMOTE")) return "§7Turn the mode switch to LOCAL first";
                String no = interlock(t, false);
                if (no != null && !mode().equals("HAND")) return "§c" + no;
                target = -1;
                Tables.drive(world, t, 0, t.prideTableIndex() + (id.equals("p_jogr") ? 1 : -1));
                return "§eJog " + (id.equals("p_jogr") ? "+" : "-") + "1 " + (Tables.turntable(t) ? "slot" : "road");
            }
            case "p_crank": {
                if (!mode().equals("HAND")) return "§7Turn the mode switch to HAND to use the crank";
                if (!pinsOut()) return "§cThe crank won't turn - pins are in";
                if (t == null) return "§7No table";
                if (Tables.remaining(t) < 0.01) return "§7Already there - pick a road or jog first";
                handBudget += 20;
                crank++;
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("signal.points_crank"), SoundCategory.BLOCKS, 0.8f, 0.8f);
                return "§eCranking... (" + crank + " turns)";
            }
            case "k_pins": {
                boolean in = ctl.get("k_pins") == 0;
                if (!powered()) return "§cNo 12 V - the pin drive is dead";
                if (in && !aligned) return "§cThe pins won't go in - the table isn't aligned on a slot";
                if (!in && approach) return "§cApproach locked - a train is coming, the pins stay in";
                ctl.set("k_pins", in ? 1 : 0);
                return in ? "§ePins driving IN" : "§ePins driving OUT";
            }
            default:
                if (id.startsWith("p_r")) {
                    int n = Integer.parseInt(id.substring(3)) - 1;
                    if (road[n] < 0) return "§7Road " + (n + 1) + " isn't taught - wrench a rail at the end of the road onto this button";
                    target = n;
                    markDirty();
                    return "§bRoad " + (n + 1) + " selected - press GO";
                }
                return null;
        }
    }

    private int nearestSlot(TileRail t) {
        int n = t.prideTableSlots();
        return Math.floorMod((int) Math.round(t.info.tablePos / Tables.step(n)), n);
    }

    @Override
    protected String[] assetRow() {
        TileRail t = table();
        return new String[]{"TT", id(), t == null ? "-" : Tables.turntable(t) ? "TURNTABLE" : "TRAVERSER", target < 0 ? "-" : "R" + (target + 1),
                t == null ? "-" : Tables.turntable(t) ? String.format(Locale.ROOT, "%.1f deg", t.info.tablePos) : String.format(Locale.ROOT, "%.1f m", t.info.tablePos),
                estop() ? "FAULT E-STOP" : moving ? "MOVING" : !why.isEmpty() ? "HELD " + why : aligned ? "ALIGNED" : "STOPPED",
                pinnedIn() ? "IN" : pinsOut() ? "OUT" : "MOVING", String.valueOf(Math.round(load.tonnes)), String.format(Locale.ROOT, "%.1f", kw),
                String.valueOf(moves), mode(), powered() ? "12V" : "NO 12V"};
    }

    @Override
    protected void save(NBTTagCompound t) {
        t.setLong("table", table); t.setIntArray("roads", road.clone());
        int[] hi = new int[ROADS], lo = new int[ROADS];
        for (int i = 0; i < ROADS; i++) { hi[i] = (int) (roadPos[i] >>> 32); lo[i] = (int) roadPos[i]; }
        t.setIntArray("rpHi", hi); t.setIntArray("rpLo", lo);
        t.setInteger("target", target); t.setInteger("pin", pin); t.setInteger("moves", moves); t.setString("op", operator);
    }

    @Override
    protected void load(NBTTagCompound t) {
        table = t.getLong("table");
        int[] r = t.getIntArray("roads");
        Arrays.fill(road, -1);
        for (int i = 0; i < Math.min(ROADS, r.length); i++) road[i] = r[i];
        int[] hi = t.getIntArray("rpHi"), lo = t.getIntArray("rpLo");
        for (int i = 0; i < Math.min(ROADS, Math.min(hi.length, lo.length)); i++) roadPos[i] = ((long) hi[i] << 32) | (lo[i] & 0xffffffffL);
        target = t.hasKey("target") ? t.getInteger("target") : -1; pin = t.getInteger("pin"); moves = t.getInteger("moves"); operator = t.getString("op");
        fp = null;
    }
}
