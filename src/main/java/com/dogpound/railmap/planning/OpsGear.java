package com.dogpound.railmap.planning;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.entity.LocomotiveSteam;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.server.Electric;
import com.dogpound.railmap.server.RailProps;
import com.dogpound.railmap.track.Rails;
import com.dogpound.railmap.track.TrackGear;
import com.dogpound.railmap.track.TrackHooks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;
import java.util.function.Predicate;

/**
 * Station operations gear (her ChatGPT batch 3: #1 loco changeover, #2 banking, #4 coupling points, #5 turnback): a
 * lineside cabinet whose crew works the driverless trains that stop beside it, on IR's real couplers through
 * planning.SplitJoin. The autopilot asks {@link #departure} before a dwelling train leaves (any of these may hold it while
 * its work is in progress) and {@link #vetoReversal} before a train changes direction (the turnback panel). Nothing
 * automatic starts without 12 V; a train already held stays held. A hard time limit (PlanConfig.opsHardLimitMinutes)
 * releases any train held too long, with an alarm, so a fault can never strand the timetable for good.
 */
public abstract class OpsGear extends TrackGear {
    private static final Map<UUID, Long> HELD_SINCE = new HashMap<>();
    /** trains whose crew already changed ends here (a loco changeover / front join): no turnback needed */
    private static final Map<UUID, Long> TURNED = new HashMap<>();

    static void turned(UUID loco, long now) { TURNED.put(loco, now); }

    static boolean recentlyTurned(UUID loco, long now) { Long t = TURNED.get(loco); return t != null && now - t < 2400; }

    /** null = this gear does not hold the train; else why it stays (shown as the autopilot's status) */
    protected abstract String hold(RailwayData.AutoTrain a, Locomotive loco, long now);

    // ---- what the autopilot asks -------------------------------------------------------------------------------------

    public static String departure(World w, RailwayData.AutoTrain a, Locomotive loco, long now) {
        String h = null;
        for (OpsGear g : TrackHooks.live(w, OpsGear.class)) { h = g.hold(a, loco, now); if (h != null) break; }
        if (h == null) { HELD_SINCE.remove(a.loco); return null; }
        long since = HELD_SINCE.computeIfAbsent(a.loco, k -> now);
        if (now - since > PlanConfig.opsHardLimitMinutes * 1200L) {
            if (now - since < PlanConfig.opsHardLimitMinutes * 1200L + 20) {
                AlarmManager.get(w).raise(w, "opshold:" + a.loco, AlarmManager.Cat.AUTO, AlarmManager.Sev.MINOR,
                        "Station work held " + Autopilot.label(a, loco) + " over " + PlanConfig.opsHardLimitMinutes + " min (" + h + ") - released", null, false);
                Recorder.event(w, "OPS HOLD LIMIT: " + Autopilot.label(a, loco) + " released after " + PlanConfig.opsHardLimitMinutes + " min - " + h);
            }
            return null;
        }
        return h;
    }

    public static boolean vetoReversal(World w, RailwayData.AutoTrain a, Locomotive loco, long now) {
        for (TileTurnbackPanel t : TrackHooks.live(w, TileTurnbackPanel.class)) if (t.veto(a, loco, now)) return true;
        return false;
    }

    // ---- shared crew work ----------------------------------------------------------------------------------------------

    protected static final int[] RANGES = {40, 80, 120};

    protected int range() { return RANGES[Math.max(0, Math.min(2, ctl.get("s_range")))]; }

    protected boolean near(double x, double z, double r) { double dx = x - pos.getX() - 0.5, dz = z - pos.getZ() - 0.5; return dx * dx + dz * dz <= r * r; }

    protected boolean near(Vec3d p, double r) { return Math.abs(p.y - pos.getY()) < 16 && near(p.x, p.z, r); }

    /** driverless trains standing at a stop within range */
    protected List<RailwayData.AutoTrain> dwellingHere() {
        List<RailwayData.AutoTrain> out = new ArrayList<>();
        for (RailwayData.AutoTrain a : RailwayData.get(world).trains().values()) {
            Autopilot.Run r = Autopilot.run(a.loco);
            if (r == null || !r.dwelling) continue;
            BlockPos s = BlockPos.fromLong(r.dwellStation);
            Locomotive l = loco(a.loco);
            if (l != null && (near(s.getX() + 0.5, s.getZ() + 0.5, range()) || near(l.getPosition(), range()))) out.add(a);
        }
        return out;
    }

    protected Locomotive loco(UUID id) { return id == null ? null : Autopilot.find(world, id); }

    /** loose standing locomotives within range: not part of a train, not driverless, not a cab car */
    protected List<Locomotive> standby(Predicate<Locomotive> ok) {
        List<Locomotive> out = new ArrayList<>();
        RailwayData d = RailwayData.get(world);
        try {
            for (Locomotive l : cam72cam.mod.world.World.get(world).getEntities(Locomotive.class)) {
                if (l.isDead() || l.getDefinition() == null || l.getDefinition().isCabCar() || d.train(l.getUUID()) != null) continue;
                if (!near(l.getPosition(), range() + 40) || Math.abs(l.getCurrentSpeed().metric()) > 0.5 || l.getTrain().size() > 1) continue;
                if (ok.test(l)) out.add(l);
            }
        } catch (LinkageError | RuntimeException ignored) { }
        out.sort(Comparator.comparingDouble(l -> l.getPosition().distanceTo(new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5))));
        return out;
    }

    /** STEAM, ELECTRIC, DIESEL or CAB (a driving trailer) */
    public static String traction(World w, Locomotive l) {
        if (l.getDefinition() != null && l.getDefinition().isCabCar()) return "CAB";
        if (l instanceof LocomotiveSteam) return "STEAM";
        try { if (Electric.isElectric(l, RailProps.get(w).of(l.getUUID()))) return "ELECTRIC"; } catch (RuntimeException ignored) { }
        return "DIESEL";
    }

    public static double tonnes(List<EntityCoupleableRollingStock> part) { double t = 0; if (part != null) for (EntityCoupleableRollingStock s : part) t += Rails.tonnes(s); return t; }

    protected static String nm(Locomotive l) { return l == null ? "-" : Rails.name(l); }

    protected static String shortName(String s) { return s.length() > 12 ? s.substring(0, 12) : s; }

    protected String clock(long ticks) { long s = Math.max(0, ticks / 20); return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60); }

    protected void sound(String id, float vol) { com.dogpound.railmap.sound.PackSounds.play(world, pos, id, vol, 1.0f); }

    /** the tester's STEP: skip the current wait (crew walk, max dwell, banker wait...) */
    abstract String forceStep();

    // ---- /irextras plan ops ----------------------------------------------------------------------------------------------

    static void command(net.minecraft.command.ICommandSender s, World w, String[] a) {
        String op = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "list";
        switch (op) {
            case "selftest" -> { for (String l : selftest()) PlanCommand.say(s, l); }
            case "step" -> {
                if (a.length < 6) { PlanCommand.say(s, "§7/irextras plan ops step <x> <y> <z>"); return; }
                BlockPos p = new BlockPos(Integer.parseInt(a[3]), Integer.parseInt(a[4]), Integer.parseInt(a[5]));
                if (w.getTileEntity(p) instanceof OpsGear g) PlanCommand.say(s, "§e" + g.wiredName() + ": " + g.forceStep());
                else PlanCommand.say(s, "§cNo changeover / banking / coupling / turnback gear there");
            }
            default -> {
                List<OpsGear> all = TrackHooks.live(w, OpsGear.class);
                if (all.isEmpty()) PlanCommand.say(s, "§7No station operations gear loaded (loco changeover, banking board, coupling point, turnback panel)");
                for (OpsGear g : all) {
                    String[] r = g.assetRow();
                    PlanCommand.say(s, "§b" + r[0] + " §f" + String.join(" §8|§7 ", Arrays.copyOfRange(r, 1, r.length)) + " §8@ " + g.getPos().getX() + " " + g.getPos().getY() + " " + g.getPos().getZ());
                }
            }
        }
    }

    /** the pure logic, on made-up numbers - nothing in the world is touched */
    static List<String> selftest() {
        List<String> out = new ArrayList<>();
        int[] n = {0, 0};
        java.util.function.BiConsumer<String, Boolean> t = (what, ok) -> { n[ok ? 0 : 1]++; out.add((ok ? "§aPASS " : "§cFAIL ") + "§7" + what); };
        t.accept("turnaround 120 m train, 45 s cabs, 1.2 m/s = 145 s", Math.abs(turnSeconds(120, 45, 1.2) - 145) < 1e-6);
        t.accept("light engine turnaround = cab time only", Math.abs(turnSeconds(0, 45, 1.2) - 45) < 1e-6);
        double b = 1;
        for (int i = 0; i < 10; i++) b = backoff(b, 500, 350);
        t.accept("banker backs off under sustained overload, never below 20 %", b < 0.25 && b >= 0.2);
        double r = b;
        for (int i = 0; i < 100; i++) r = backoff(r, 100, 350);
        t.accept("banker power creeps back once the buff force drops", r == 1.0);
        t.accept("back-off holds steady between 80 % and 100 % of the limit", backoff(0.6, 300, 350) == 0.6);
        t.accept("banker notch = lead 0.8 x 50 % share", Math.abs(bankNotch(0.8f, 0.5, 1, 0) - 0.4f) < 1e-6);
        t.accept("banker shuts off while the train brakes", bankNotch(1f, 0.75, 1, 0.5f) == 0f);
        t.accept("MU systems: AAR-27 + UIC-13 incompatible", !SplitJoin.systemsCompatible("AAR-27", "UIC-13"));
        t.accept("MU systems: driving trailer (ANY) works with UIC-13", SplitJoin.systemsCompatible("ANY", "UIC-13"));
        t.accept("MU systems: steam (NONE) + diesel refused, steam + steam ok", !SplitJoin.systemsCompatible("NONE", "AAR-27") && SplitJoin.systemsCompatible("NONE", "NONE"));
        t.accept("stage tables: every gear has IDLE first and FAULT at 9", TileChangeover.STAGE[9].equals("FAULT") && TileBankingBoard.STAGE[9].equals("FAULT")
                && TileCouplingPoint.STAGE[9].equals("FAULT") && TileTurnbackPanel.STAGE[9].equals("FAULT") && TileTurnbackPanel.STAGE[0].equals("IDLE"));
        out.add((n[1] == 0 ? "§a" : "§c") + "ops selftest: " + n[0] + " passed, " + n[1] + " failed");
        return out;
    }

    // ---- the pure maths (ops selftest) ----------------------------------------------------------------------------------

    /** turnaround time (s): the driver walks the train end to end, shuts one cab down and opens the other */
    public static double turnSeconds(double lengthM, int cabSeconds, double walkMs) { return lengthM / Math.max(0.1, walkMs) + cabSeconds; }

    /**
     * The banker crew's back-off: buff (compression, kN positive) over the limit cuts the share 15 % a step (never under
     * 20 %), under 80 % of the limit it creeps back 2 % a step. Returns the new back-off factor 0.2..1.
     */
    public static double backoff(double backoff, double buffKn, double limitKn) {
        if (buffKn > limitKn) return Math.max(0.2, backoff * 0.85);
        if (buffKn < limitKn * 0.8) return Math.min(1.0, backoff + 0.02);
        return backoff;
    }

    /** the banker's notch: the lead's notch x the TE share x the back-off, nothing while the train brakes */
    public static float bankNotch(float leadNotch, double share, double backoff, float leadBrake) {
        if (leadBrake > 0.15f) return 0f;
        return (float) Math.max(0, Math.min(1, leadNotch * share * backoff));
    }
}
