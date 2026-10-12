package com.dogpound.railmap.track;

import cam72cam.immersiverailroading.library.SwitchState;
import cam72cam.immersiverailroading.tile.TileRail;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.Interlocking;
import com.dogpound.railmap.graph.TrainNode;
import com.dogpound.railmap.server.TrainTracker;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Intelligent switch machine (her ChatGPT list #14): an S700K-style point machine bolted to the sleepers beside a set
 * of points, wrench-linked to the IR switch. It is the only thing allowed to move those points once fitted:
 * <ul>
 *   <li>ELECTRIC (4 s stroke), HYDRAULIC (3 s, pump pressure) or PNEUMATIC (1.5 s, air pressure) drive; SINGLE or DUAL
 *       motors (dual = faster, and one motor can fail without losing the points);</li>
 *   <li>blade detection: NORMAL / REVERSE only once the stroke has finished and the blade is locked - while moving,
 *       failed, cranked or unpowered it reports NO DETECTION and the interlocking refuses routes over it;</li>
 *   <li>obstruction: anything in the switch (a dropped item, a mob, a player, a block, packed snow) stalls the blade
 *       mid-stroke - the motor current climbs, the clutch slips, the points stay where they were with no detection,
 *       and a FAILED POINTS alarm goes up until the obstruction is cleared and RESET pressed;</li>
 *   <li>weather (the railway weather model): frozen points (snow / ice packed in, no Points Heater cabinet heating them)
 *       won't move; the panel shows rail temperature, snow and frost risk, and heat-expanded rails;</li>
 *   <li>LOCAL control (refuses the signaller), and the hand crank: inserting it isolates the motor and breaks
 *       detection; four turns move the blades by hand, even with no power.</li>
 * </ul>
 * A failed machine reports the position the blade is ACTUALLY in, never the one it was asked for.
 */
public class TilePointMachine extends TrackGear {
    public static final String[] TYPES = {"ELECTRIC", "HYDRAULIC", "PNEUMATIC"};
    private long sw;                 // linked switch (TileRail pos), 0 = none
    /** batch-3 #6: a movable-point (swing-nose) diamond instead of a switch: the machine sets which road is made (1 = east-west, 2 = north-south) */
    private boolean diamond;
    private int diaAxis = 1;
    public boolean diamond() { return diamond; }
    /** the IR state this machine's blade is in: a diamond's road, else the switch's */
    private SwitchState irState(BlockPos sp) { return diamond ? (diaAxis == 2 ? SwitchState.TURN : SwitchState.STRAIGHT) : Interlocking.stateAt(world, sp); }
    private int detected;            // 0 no detection, 1 normal, 2 reverse
    private int stroke, strokeLen, strokeTo, stall;
    private boolean failed, obstructed;
    private final boolean[] motorBad = new boolean[2];
    private float amps, press = 100;
    private double railC = 15, snowMm, iceMm, airC = 15;
    private String failWhy = "";
    private int throwsN, fails, crankTurns, noDetSecs;

    public String id() { return "PTS " + Math.floorMod(pos.getX() * 17 + pos.getZ() * 29, 1000); }
    @Override public String wiredName() { return "Point machine " + id(); }
    public BlockPos switchPos() { return sw == 0 ? null : BlockPos.fromLong(sw); }
    private String type() { return TYPES[Math.min(2, Math.max(0, ctl.get("s_type")))]; }
    private boolean dual() { return ctl.get("s_motors") == 1; }
    private boolean local() { return ctl.get("s_mode") == 1; }
    private boolean crankIn() { return ctl.get("k_crank") == 1; }
    private boolean frostRisk() { return airC <= 0 && (snowMm > 5 || iceMm > 0.2); }

    static TilePointMachine on(List<TilePointMachine> ms, BlockPos switchPos) {
        long k = switchPos.toLong();
        for (TilePointMachine m : ms) if (m.sw == k) return m;
        return null;
    }

    private String posName(int d) { return d == 1 ? "NORMAL" : d == 2 ? "REVERSE" : "NO DETECTION"; }
    private static int code(SwitchState s) { return s == SwitchState.STRAIGHT ? 1 : s == SwitchState.TURN ? 2 : 0; }

    @Override public String gearState() {
        if (sw == 0) return "not linked to a switch (Signal Wrench: pick the points, click the POSITION display)";
        if (!powered()) return "NO 12 V - no detection, motor dead";
        return posName(detected) + (stroke > 0 ? " · MOVING" : "") + (failed ? " · FAILED: " + failWhy : "") + (crankIn() ? " · CRANK IN" : "") + (local() ? " · LOCAL" : "") + (frostRisk() ? " · FROST" : "");
    }
    @Override public int wiredLamp() { return !powered() || failed ? 3 : detected == 0 || motorBad[0] || motorBad[1] ? 2 : 1; }
    @Override public void gearInfo(Map<String, Object> m) {
        m.put("switch", sw == 0 ? "none" : Rails.xz(BlockPos.fromLong(sw))); m.put("detected", posName(detected)); m.put("type", type()); m.put("throws", throwsN);
        m.put("failed", failed); m.put("railC", Math.round(railC)); m.put("snowMm", Math.round(snowMm));
    }

    @Override
    public String link(String control, BlockPos pick, EntityPlayer who) {
        TileRail s = Rails.switchOf(world, pick);
        TileRail dia = s == null ? Rails.pieceAt(world, pick) : null;
        boolean isDia = dia != null && dia.info != null && dia.info.settings.type == cam72cam.immersiverailroading.library.TrackItems.CROSSING;
        if (isDia) s = dia;                                              // batch-3 #6: movable-point diamond
        if (s == null) return "§cThat isn't a set of points or a diamond crossing - pick a switch or a diamond with the Signal Wrench";
        BlockPos sp = Rails.pos(s);
        if (sp.distanceSq(pos) > 24 * 24) return "§cThose points are too far away - a point machine sits beside its switch (24 blocks)";
        for (TilePointMachine m : TrackHooks.live(world, TilePointMachine.class)) if (m != this && m.sw == sp.toLong()) return "§cThose points already have a machine (" + m.id() + " at " + Rails.xz(m.getPos()) + ")";
        sw = sp.toLong();
        diamond = isDia;
        detected = code(irState(sp));
        failed = obstructed = false;
        markDirty();
        return "§a" + id() + " now drives the " + (diamond ? "movable-point DIAMOND" : "points") + " at " + Rails.xz(sp) + " - " + (diamond ? "NORMAL = east-west road, REVERSE = north-south; the interlocking sets it for each route" : "they only move through it from now on");
    }

    // ---- the throw ---------------------------------------------------------------------------------------------------

    /** a request to move the points (null = stroke started / nothing to do; else why not, and nothing moves) */
    String requestThrow(SwitchState want, boolean remote, EntityPlayer who) {
        BlockPos sp = switchPos();
        if (sp == null) return null;
        SwitchState cur = irState(sp);
        if (cur == want && stroke == 0 && !failed) return null;          // already there
        if (!powered()) return "points " + id() + " have no 12 V - the motor can't run";
        if (crankIn()) return "points " + id() + ": hand crank inserted - motor isolated";
        if (remote && local()) return "points " + id() + " are in LOCAL control";
        if (failed) return "points " + id() + " FAILED (" + failWhy + ") - clear it and press RESET";
        if (stroke > 0) return "points " + id() + " are still moving";
        String wx = weatherFail(sp);
        if (wx != null) return wx;
        // motors
        int per10k = TrackConfig.pointMotorFailPer10k;
        if (per10k > 0) for (int i = 0; i < (dual() ? 2 : 1); i++)
            if (!motorBad[i] && world.rand.nextInt(10000) < per10k) {
                motorBad[i] = true;
                Recorder.event(world, "POINTS " + id() + ": motor " + (i + 1) + " burnt out on a throw");
                if (dual()) AlarmManager.get(world).raise(world, "pmmotor:" + pos.toLong(), AlarmManager.Cat.MACHINE, AlarmManager.Sev.MINOR, "POINTS " + id() + ": motor " + (i + 1) + " failed - running on one motor", pos, true);
            }
        boolean anyMotor = dual() ? !(motorBad[0] && motorBad[1]) : !motorBad[0];
        if (!anyMotor) { fail("motor failed", true); return "points " + id() + " FAILED - motor burnt out"; }
        if (type().equals("HYDRAULIC") && press < 40 || type().equals("PNEUMATIC") && press < 40) return "points " + id() + ": " + (type().equals("HYDRAULIC") ? "hydraulic" : "air") + " pressure low (" + Math.round(press) + "%)";
        String obs = obstruction(sp);
        double base = type().equals("PNEUMATIC") ? 30 : type().equals("HYDRAULIC") ? 60 : 80;
        if (dual() && !(motorBad[0] || motorBad[1])) base *= 0.8;
        strokeLen = (int) base;
        strokeTo = code(want);
        detected = 0;
        if (obs != null) {
            stall = strokeLen / 3;                                       // blade hits it a third of the way across
            stroke = strokeLen;
            obstructed = true;
            failWhy = "obstructed: " + obs;
            Recorder.event(world, "POINTS " + id() + " obstructed (" + obs + ") - blade " + (stall * 100 / strokeLen) + "% across, motor stalling");
            return "points " + id() + " OBSTRUCTED - " + obs;
        }
        stroke = strokeLen;
        stall = 0;
        if (type().equals("HYDRAULIC") || type().equals("PNEUMATIC")) press = Math.max(0, press - 18);
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("grid.contactor_close"), SoundCategory.BLOCKS, 0.5f, 1.0f);
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev(type().equals("PNEUMATIC") ? "signal.points_air" : type().equals("HYDRAULIC") ? "signal.points_hyd" : "signal.points"), SoundCategory.BLOCKS, 0.9f, 1.0f);
        markDirty();
        return null;
    }

    /** what is in the way of the blades (null = clear) */
    private String obstruction(BlockPos sp) {
        AxisAlignedBB box = new AxisAlignedBB(sp).grow(1.6, 0.4, 1.6).expand(0, 0.8, 0);
        for (Entity e : world.getEntitiesWithinAABB(Entity.class, box)) {
            if (e instanceof EntityItem i) return "debris (" + i.getItem().getDisplayName() + ") in the switch";
            if (e instanceof EntityPlayer p) return p.getName() + " standing in the switch";
            if (e instanceof EntityLivingBase l) return "a " + l.getName() + " in the switch";
        }
        for (BlockPos q : new BlockPos[]{sp, sp.up(), sp.north(), sp.south(), sp.east(), sp.west()}) {
            IBlockState s = world.getBlockState(q);
            if (s.getMaterial() == Material.SNOW || s.getMaterial() == Material.CRAFTED_SNOW) { if (airC <= 0) return "packed snow in the switch"; continue; }
            if (q.equals(sp) || s.getBlock().isAir(s, world, q) || Rails.pieceAt(world, q) != null) continue;
            if (q.getY() == sp.getY() + 1 && s.getMaterial().blocksMovement()) return s.getBlock().getLocalizedName() + " on the blades";
        }
        return null;
    }

    private void fail(String why, boolean alarm) {
        failed = true;
        failWhy = why;
        fails++;
        detected = 0;
        Recorder.event(world, "FAILED POINTS " + id() + " at " + Rails.xz(switchPos() == null ? pos : switchPos()) + ": " + why);
        if (alarm) AlarmManager.get(world).raise(world, "points:" + pos.toLong(), AlarmManager.Cat.SIGNAL, AlarmManager.Sev.MAJOR, "FAILED POINTS " + id() + ": " + why + " - no detection", pos, true);
        markDirty();
    }

    /** RouteCheck: null when the machine detects the blade where IR has it */
    String detectionFault() {
        if (!powered()) return "points " + id() + " have no 12 V - no detection";
        if (crankIn()) return "points " + id() + ": hand crank inserted - no detection";
        if (failed) return "points " + id() + " FAILED (" + failWhy + ") - no detection";
        if (stroke > 0) return "points " + id() + " still moving - no detection yet";
        if (detected == 0) return "points " + id() + " not detected in either position";
        BlockPos sp = switchPos();
        if (sp != null && code(irState(sp)) != detected) return "points " + id() + " detected " + posName(detected) + " - not where the route needs them";
        return null;
    }

    private boolean trainOver(BlockPos sp) {
        for (TrainNode t : TrainTracker.latest(world)) {
            double dx = t.x - sp.getX() - 0.5, dz = t.z - sp.getZ() - 0.5;
            if (dx * dx + dz * dz < 16 && Math.abs(t.y - sp.getY()) < 4) return true;
        }
        return false;
    }

    /** move the IR switch ourselves (local push or crank) */
    private boolean force(SwitchState want) {
        if (diamond) return true;                                        // a diamond's road is set when the stroke ends
        BlockPos sp = switchPos();
        TileRail s = sp == null ? null : Rails.switchOf(world, sp);
        if (s == null) return false;
        s.setSwitchForced(want);
        s.markDirty();
        return true;
    }

    // ---- the live tick -------------------------------------------------------------------------------------------------

    @Override
    protected void tickGear() {
        BlockPos sp = switchPos();
        if (stroke > 0 && powered()) {
            int done = strokeLen - stroke;
            if (stall > 0 && done >= stall) {                            // blade against the obstruction: current climbs
                amps = Math.min(12, amps + 0.6f);
                if (amps >= 11.5f) {                                     // clutch slips, overload trips: points failed
                    stroke = 0;
                    amps = 0;
                    fail(failWhy, true);
                    world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("machine.clunk"), SoundCategory.BLOCKS, 0.7f, 1.2f);
                }
            } else {
                stroke--;
                amps = done < 6 ? 7.5f : dual() ? 2.4f : 3.1f;
                if (stroke == 0) { detected = strokeTo; if (diamond) diaAxis = strokeTo; amps = 0; throwsN++; markDirty(); world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("signal.points_lock"), SoundCategory.BLOCKS, 0.9f, 1.0f); }
            }
        } else if (stroke > 0) { stroke = 0; amps = 0; }               // power lost mid-stroke: blade stops where it is
        if (age % 20 != 0) { putLive(); return; }
        // ---- once a second
        if (sp != null && world.isBlockLoaded(sp)) {
            int ir = code(irState(sp));
            TileRail still = diamond ? Rails.pieceAt(world, sp) : Rails.switchOf(world, sp);
            if (ir == 0 && still == null || diamond && still == null) { sw = 0; detected = 0; diamond = false; markDirty(); }
            else if (stroke == 0 && !failed && !crankIn() && powered()) detected = ir;   // blade proved where it lies
            if (!powered() || crankIn()) detected = 0;
        }
        weather(sp);
        if (type().equals("HYDRAULIC") || type().equals("PNEUMATIC")) press = Math.min(100, press + (powered() ? 6 : 0));
        // alarms
        if (sp != null && powered() && detected == 0 && !crankIn()) noDetSecs++; else noDetSecs = 0;
        AlarmManager am = AlarmManager.get(world);
        if (!failed) Alarms.state(world, "points:" + pos.toLong(), noDetSecs > 10, AlarmManager.Cat.SIGNAL, AlarmManager.Sev.MAJOR, "POINTS " + id() + ": no detection for " + noDetSecs + " s", pos);
        if (!motorBad[0] && !motorBad[1]) am.clear("pmmotor:" + pos.toLong());
        putLive();
    }

    /** the railway weather model: frozen / heat-bound points (null = fine). The weather owns freezing + Points Heaters. */
    private String weatherFail(BlockPos sp) {
        try { return com.dogpound.railmap.weather.Weather.pointsFail(world, sp); } catch (LinkageError | RuntimeException e) { return null; }
    }

    private void weather(BlockPos sp) {
        BlockPos at = sp == null ? pos : sp;
        try {
            railC = com.dogpound.railmap.weather.Weather.railC(world, at);
            airC = com.dogpound.railmap.weather.Weather.airC(world, at);
            snowMm = com.dogpound.railmap.weather.Weather.snowMm(world, at);
            iceMm = com.dogpound.railmap.weather.Weather.iceMm(world, at);
        } catch (LinkageError | RuntimeException e) { railC = airC = 15; snowMm = iceMm = 0; }
    }

    private void putLive() {
        put("nor", detected == 1);
        put("rev", detected == 2);
        put("nodet", sw != 0 && detected == 0);
        put("run", stroke > 0);
        put("fail", failed);
        put("obs", obstructed);
        put("ice", frostRisk());
        put("heat", railC >= 45);
        put("crank", crankIn());
        put("pwr", powered());
        put("m1", !motorBad[0]);
        put("m2", dual() && !motorBad[1]);
        put("g_amps", Math.round(amps));
        put("g_press", type().equals("ELECTRIC") ? 0 : Math.round(press));
        put("throws", throwsN);
        text("pos", sw == 0 ? "NOT LINKED" : stroke > 0 ? "MOVING " + (100 * (strokeLen - stroke) / Math.max(1, strokeLen)) + "%" : failed ? "FAILED" : posName(detected));
        text("sw", sw == 0 ? "--" : Rails.xz(BlockPos.fromLong(sw)));
        text("amps", String.format(Locale.ROOT, "%.1f A", amps));
        text("temp", "RAIL " + Math.round(railC) + "C SNOW " + Math.round(snowMm));
        text("strk", crankIn() ? "CRANK " + crankTurns + "/4" : String.format(Locale.ROOT, "%.1f S", (dual() ? 0.8 : 1) * (type().equals("PNEUMATIC") ? 30 : type().equals("HYDRAULIC") ? 60 : 80) / 20.0));
    }

    @Override protected boolean keepTextUnpowered() { return false; }

    // ---- the controls ------------------------------------------------------------------------------------------------

    @Override
    public String controlPress(String id, EntityPlayer who) {
        switch (id) {
            case "test": return lampTest();
            case "p_nor": case "p_rev": {
                if (sw == 0) return "§7Link the points first (Signal Wrench)";
                if (!local()) return "§7Turn the mode switch to LOCAL first (the signaller controls the points in REMOTE)";
                SwitchState want = id.equals("p_nor") ? SwitchState.STRAIGHT : SwitchState.TURN;
                if (trainOver(switchPos())) return "§cRefused - a train is over the points";
                String r = requestThrow(want, false, who);
                if (r != null) return "§c" + r;
                force(want);
                return "§e" + id() + ": moving to " + (want == SwitchState.STRAIGHT ? "NORMAL" : "REVERSE");
            }
            case "p_reset": {
                if (crankIn() && (motorBad[0] || motorBad[1])) { motorBad[0] = motorBad[1] = false; AlarmManager.get(world).clear("pmmotor:" + pos.toLong()); Recorder.event(world, "POINTS " + id() + ": motor(s) changed by " + who(who)); return "§aMotor changed (crank interlock held the supply off while you worked)"; }
                if (!failed) return "§7Nothing to reset - " + gearState();
                BlockPos sp = switchPos();
                if (obstructed && sp != null) { String o = obstruction(sp); if (o != null) return "§cStill obstructed: " + o; }
                if (motorBad[0] && (!dual() || motorBad[1])) return "§cThe motor is burnt out - insert the hand crank (isolates it) and press RESET to change it";
                failed = obstructed = false;
                failWhy = "";
                AlarmManager.get(world).clear("points:" + pos.toLong());
                Recorder.event(world, "POINTS " + id() + " reset by " + who(who));
                return "§aReset - detection restored once the blade is proved";
            }
            case "p_crank": {
                if (sw == 0) return "§7Link the points first (Signal Wrench)";
                if (!crankIn()) return "§7Insert the hand crank first (the CRANK key)";
                BlockPos sp = switchPos();
                if (trainOver(sp)) return "§cNot under a train!";
                String o = obstruction(sp);
                if (o != null) return "§cThe crank won't turn - " + o;
                String wx = weatherFail(sp);
                if (wx != null && wx.contains("FROZEN")) return "§cThe crank won't turn - " + wx;
                crankTurns++;
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("signal.points_crank"), SoundCategory.BLOCKS, 0.8f, 1.0f);
                if (crankTurns < 4) { markDirty(); return "§eCranking... " + crankTurns + "/4"; }
                crankTurns = 0;
                SwitchState cur = irState(sp);
                SwitchState to = cur == SwitchState.TURN ? SwitchState.STRAIGHT : SwitchState.TURN;
                force(to);
                if (diamond) diaAxis = code(to);
                failed = obstructed = false;
                Recorder.event(world, "POINTS " + id() + " hand-cranked to " + (to == SwitchState.STRAIGHT ? "NORMAL" : "REVERSE") + " by " + who(who));
                return "§aPoints cranked to " + (to == SwitchState.STRAIGHT ? "NORMAL" : "REVERSE") + " - take the crank out to restore detection";
            }
            case "k_crank": {
                ctl.set("k_crank", crankIn() ? 0 : 1);
                crankTurns = 0;
                if (crankIn()) { stroke = 0; amps = 0; detected = 0; }
                Recorder.event(world, "POINTS " + id() + ": hand crank " + (crankIn() ? "inserted" : "removed") + " by " + who(who));
                return crankIn() ? "§6Hand crank IN - motor isolated, detection broken" : "§aHand crank OUT - motor back, proving the blade";
            }
            default: return null;
        }
    }

    @Override
    protected String[] assetRow() {
        return new String[]{"PM", id(), sw == 0 ? "-" : (diamond ? "DIAMOND " : "") + Rails.xz(BlockPos.fromLong(sw)), type() + (dual() ? " x2" : ""), posName(detected), failed ? "FAILED " + failWhy : stroke > 0 ? "moving" : "ok",
                "rail " + Math.round(railC) + "C", frostRisk() ? "FROST" : "-", String.valueOf(throwsN), String.valueOf(fails), local() ? "LOCAL" : "REMOTE", powered() ? "12V" : "NO 12V", xyz()};
    }

    @Override
    protected void save(NBTTagCompound t) {
        t.setLong("sw", sw); t.setBoolean("dia", diamond); t.setInteger("diaAx", diaAxis); t.setInteger("det", detected); t.setInteger("stroke", stroke); t.setInteger("sLen", strokeLen); t.setInteger("sTo", strokeTo); t.setInteger("stall", stall);
        t.setBoolean("failed", failed); t.setBoolean("obs", obstructed); t.setBoolean("m1", motorBad[0]); t.setBoolean("m2", motorBad[1]);
        t.setFloat("press", press); t.setString("why", failWhy); t.setInteger("throws", throwsN); t.setInteger("fails", fails); t.setInteger("crank", crankTurns);
    }

    @Override
    protected void load(NBTTagCompound t) {
        sw = t.getLong("sw"); diamond = t.getBoolean("dia"); diaAxis = t.hasKey("diaAx") ? t.getInteger("diaAx") : 1; detected = t.getInteger("det"); stroke = t.getInteger("stroke"); strokeLen = t.getInteger("sLen"); strokeTo = t.getInteger("sTo"); stall = t.getInteger("stall");
        failed = t.getBoolean("failed"); obstructed = t.getBoolean("obs"); motorBad[0] = t.getBoolean("m1"); motorBad[1] = t.getBoolean("m2");
        press = t.hasKey("press") ? t.getFloat("press") : 100; failWhy = t.getString("why");
        throwsN = t.getInteger("throws"); fails = t.getInteger("fails"); crankTurns = t.getInteger("crank");
    }
}
