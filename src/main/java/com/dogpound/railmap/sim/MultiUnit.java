package com.dogpound.railmap.sim;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock.CouplerType;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.entity.LocomotiveDiesel;
import cam72cam.immersiverailroading.entity.LocomotiveSteam;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.server.Electric;
import com.dogpound.railmap.server.RailProps;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Multiple-unit operation and distributed power (her ChatGPT list item 106). Four times a second, per train with more
 * than one powered unit:
 * <ul>
 *   <li><b>the lead</b> is the unit somebody is driving (a player in its cab, else the autopilot's unit, else the last
 *       lead); its cab is the only one that commands;</li>
 *   <li><b>MU train lines</b>: units coupled straight to the lead (and to each other) through MU jumpers follow its
 *       throttle notch, reverser (turned round for a unit running backwards), independent brake and sanders, and send
 *       back their fault / alarm line (the lead's FAULT lamp lights for a fault anywhere in the consist). ENGINE START
 *       ALL / STOP ALL ride the engine-run train line, staggered 4 s apart like a real consist;</li>
 *   <li><b>distributed power</b>: compatible units further down the train (cars in between) are radio remotes. SYNC
 *       follows the lead, INDEPENDENT (behind the fence) holds its own notch set from the lead's display, IDLE gives no
 *       power. The radio has a range (config) and needs line of sight in tunnels; after the comm-loss time a remote
 *       drops to idle and the lead gets a DP COMM LOSS alarm;</li>
 *   <li><b>compatibility</b>: each unit has a control system (AAR-27 diesel, UIC-13 electric, EMU, ANY for a driving
 *       trailer, NONE for steam or an unfitted unit). A unit on a different system, or with its MU jumper / radio cut
 *       out, takes no orders: the display shows it as INCOMPATIBLE / CUT OUT.</li>
 * </ul>
 * It works through the fork's prideMuManaged / prideMuApply (IR's own copy-down-the-train stays off while a train is
 * managed here). Turn it off (config multipleUnit) and IR's behaviour is back.
 */
public final class MultiUnit {
    private MultiUnit() {}

    public static final String[] SYSTEMS = {"AAR-27", "UIC-13", "EMU", "NONE"};
    public static final int LEAD = 0, MU = 1, DP = 2, INCOMPAT = 3, CUT = 4;
    static final String[] ROLE = {"LEAD", "MU", "DP", "INCOMPATIBLE", "CUT OUT"};
    public static final String[] DP_MODE = {"SYNC", "INDEP", "IDLE"};

    /** one unit as the lead sees it */
    public static final class Unit {
        public UUID id;
        public String name = "";
        public int role, index;
        public boolean forward = true, comm = true;
        public double commLostSec;
    }

    /** one managed train, keyed by its lead */
    public static final class Group {
        public UUID lead;
        public String system = "";
        public final List<Unit> units = new ArrayList<>();
        public int faults;
        public boolean commLoss;
        long seen;
        /** units still to start on the engine-run train line, and when the next goes */
        final Deque<UUID> startQueue = new ArrayDeque<>();
        long nextStart;
    }

    private static final Map<UUID, Group> GROUPS = new HashMap<>();
    /** every unit's group, by unit id (lead included) */
    private static final Map<UUID, Group> BY_UNIT = new HashMap<>();
    private static final Map<UUID, Double> COMM_LOST = new HashMap<>();

    public static void clear() { GROUPS.clear(); BY_UNIT.clear(); COMM_LOST.clear(); }

    /** the group this unit runs in, or null (single unit / not managed) */
    public static Group group(UUID unit) { return BY_UNIT.get(unit); }

    /** a trailing unit of a managed consist (its own cab display / protection should stay quiet) */
    public static boolean isFollower(UUID unit) {
        Group g = BY_UNIT.get(unit);
        return g != null && !g.lead.equals(unit);
    }

    // ---- control systems ---------------------------------------------------------------------------------------------

    public static String system(World mc, Locomotive l) {
        SimData.Car c = SimData.get(mc).peek(l.getUUID());
        if (c != null && !c.muSystem.isEmpty()) return c.muSystem;
        if (l instanceof LocomotiveSteam) return "NONE";
        if (l.getDefinition().isCabCar()) return "ANY";
        boolean electric = Electric.isElectric(l, RailProps.get(mc).of(l.getUUID()));
        if (!l.getDefinition().muliUnitCapable && !SimConfig.muAllUnits) return "NONE";
        return electric ? "UIC-13" : "AAR-27";
    }

    static boolean compatible(String a, String b) {
        if (a.equals("NONE") || b.equals("NONE")) return false;
        return a.equals("ANY") || b.equals("ANY") || a.equals(b);
    }

    // ---- the pass ----------------------------------------------------------------------------------------------------

    static void tick(World mc, SimData data, long now, double dt) {
        cam72cam.mod.world.World w = cam72cam.mod.world.World.get(mc);
        List<Locomotive> all = w.getEntities(Locomotive.class);
        if (!SimConfig.multipleUnit) {
            if (!BY_UNIT.isEmpty()) { for (Locomotive l : all) l.prideMuManaged = false; clear(); }
            return;
        }
        Set<UUID> done = new HashSet<>();
        Map<UUID, Group> nextBy = new HashMap<>();
        for (Locomotive l : all) {
            if (l.isDead() || done.contains(l.getUUID()) || l.getDefinition() == null) continue;
            List<EntityCoupleableRollingStock> train = l.getTrain(false);
            List<Locomotive> locos = new ArrayList<>();
            for (EntityCoupleableRollingStock s : train) { done.add(s.getUUID()); if (s instanceof Locomotive x && !x.isDead()) locos.add(x); }
            if (locos.size() < 2) { l.prideMuManaged = false; continue; }
            Locomotive lead = pickLead(locos);
            Group g = GROUPS.get(lead.getUUID());
            if (g == null) {
                // the lead changed (or a new train): carry the queue over from the old group if there was one
                g = new Group();
                g.lead = lead.getUUID();
                for (Locomotive x : locos) { Group old = BY_UNIT.get(x.getUUID()); if (old != null) { g.startQueue.addAll(old.startQueue); break; } }
                GROUPS.put(g.lead, g);
            }
            g.seen = now;
            run(mc, data, g, lead, locos, now, dt);
            for (Locomotive x : locos) nextBy.put(x.getUUID(), g);
        }
        // units no longer in a managed train get IR's own behaviour back
        for (UUID id : BY_UNIT.keySet()) if (!nextBy.containsKey(id)) {
            for (Locomotive x : all) if (x.getUUID().equals(id)) x.prideMuManaged = false;
        }
        BY_UNIT.clear();
        BY_UNIT.putAll(nextBy);
        GROUPS.values().removeIf(g -> g.seen != now);
        COMM_LOST.keySet().retainAll(BY_UNIT.keySet());
    }

    private static Locomotive pickLead(List<Locomotive> locos) {
        Group prev = null;
        for (Locomotive x : locos) { prev = BY_UNIT.get(x.getUUID()); if (prev != null) break; }
        UUID was = prev == null ? null : prev.lead;
        // a player in a cab
        List<Locomotive> driven = new ArrayList<>();
        for (Locomotive x : locos)
            for (cam72cam.mod.entity.Entity e : x.getPassengers()) if (e.internal instanceof EntityPlayer) { driven.add(x); break; }
        for (Locomotive x : driven) if (x.getUUID().equals(was)) return x;
        if (!driven.isEmpty()) return driven.get(0);
        // the autopilot's unit
        for (Locomotive x : locos) {
            try { if (com.dogpound.railmap.auto.Autopilot.run(x.getUUID()) != null) return x; } catch (RuntimeException ignored) { }
        }
        for (Locomotive x : locos) if (x.getUUID().equals(was)) return x;
        SimData data = SimData.get(locos.get(0).getWorld().internal);
        for (Locomotive x : locos) { SimData.Car c = data.peek(x.getUUID()); if (c != null && c.muLead) return x; }
        for (Locomotive x : locos) if (!x.getDefinition().isCabCar() && Math.abs(x.getThrottle()) > 0.01f) return x;
        return locos.get(0);
    }

    private static void run(World mc, SimData data, Group g, Locomotive lead, List<Locomotive> locos, long now, double dt) {
        Map<UUID, Boolean> dir = new HashMap<>();
        for (EntityCoupleableRollingStock.DirectionalStock d : lead.getDirectionalTrain(false)) dir.put(d.stock.getUUID(), d.direction);
        // MU jumpers: loco-to-loco couplings reached from the lead
        Map<UUID, Locomotive> byId = new HashMap<>();
        for (Locomotive x : locos) byId.put(x.getUUID(), x);
        Set<UUID> cabled = new LinkedHashSet<>();
        Deque<Locomotive> q = new ArrayDeque<>();
        q.add(lead);
        cabled.add(lead.getUUID());
        while (!q.isEmpty()) {
            Locomotive x = q.poll();
            for (CouplerType t : CouplerType.values()) {
                UUID o = x.getCoupledUUID(t);
                if (o != null && byId.containsKey(o) && x.isCouplerEngaged(t) && cabled.add(o)) q.add(byId.get(o));
            }
        }
        String leadSys = system(mc, lead);
        String sys = leadSys;
        if (sys.equals("ANY")) for (Locomotive x : locos) { String s = system(mc, x); if (!s.equals("ANY") && !s.equals("NONE")) { sys = s; break; } }
        g.system = sys;
        g.units.clear();
        g.faults = 0;
        g.commLoss = false;
        SimData.Car lc = data.of(lead.getUUID());
        lead.prideMuManaged = true;
        boolean leadSanding = lead.isSanding || lc.sanding;
        Vec3d lp = lead.getPosition();
        int i = 0;
        for (Locomotive x : locos) {
            SimData.Car c = data.of(x.getUUID());
            if (!c.faults.isEmpty()) g.faults += c.faults.size();
            Unit u = new Unit();
            u.id = x.getUUID();
            u.name = EngineSim.label(x);
            u.index = ++i;
            u.forward = dir.getOrDefault(u.id, true);
            g.units.add(u);
            x.prideMuManaged = true;
            if (x == lead) { u.role = LEAD; continue; }
            String s = system(mc, x);
            if (c.muCut) { u.role = CUT; continue; }
            if (!compatible(sys, s)) { u.role = INCOMPAT; continue; }
            float rev = lead.getReverser() * (u.forward ? 1 : -1);
            if (cabled.contains(u.id)) {
                u.role = MU;
                x.prideMuApply(lead.getThrottle(), rev);
                trainLines(x, c, lead, leadSanding);
            } else {
                u.role = DP;
                Vec3d xp = x.getPosition();
                double dist = Math.sqrt(xp.distanceToSquared(lp));
                boolean sky = mc.canSeeSky(new BlockPos(xp.x, xp.y + 3, xp.z)) || mc.canSeeSky(new BlockPos(lp.x, lp.y + 3, lp.z));
                u.comm = dist <= SimConfig.dpRadioM && (sky || dist <= 250);
                double lost = u.comm ? 0 : COMM_LOST.getOrDefault(u.id, 0.0) + dt;
                COMM_LOST.put(u.id, lost);
                u.commLostSec = lost;
                String key = "dp:" + lead.getUUID() + ":" + u.id;
                if (!u.comm && lost >= SimConfig.dpCommLossSeconds) {
                    g.commLoss = true;
                    x.prideMuApply(0f, x.getReverser());             // comm loss: the remote drops to idle, keeps its reverser
                    if (lost - dt < SimConfig.dpCommLossSeconds) {
                        AlarmManager.get(mc).raise(mc, key, AlarmManager.Cat.MACHINE, AlarmManager.Sev.MAJOR,
                                EngineSim.label(lead) + ": DP COMM LOSS with " + u.name + " - remote at idle", new BlockPos(xp.x, xp.y, xp.z), true);
                        Recorder.event(mc, EngineSim.label(lead) + ": DP comm loss, remote " + u.name + " idled");
                    }
                    continue;
                }
                if (u.comm) AlarmManager.get(mc).clear(key);
                if (!u.comm) continue;                                  // holding its last order until the timer runs out
                float notch = c.dpMode == 1 ? c.dpNotch : c.dpMode == 2 ? 0f : lead.getThrottle();
                x.prideMuApply(notch, rev);
                trainLines(x, c, lead, leadSanding);
            }
        }
        // engine-run train line: staggered starts
        if (!g.startQueue.isEmpty() && now >= g.nextStart) {
            UUID id = g.startQueue.poll();
            for (Locomotive x : locos)
                if (x.getUUID().equals(id) && x instanceof LocomotiveDiesel d && !d.isTurnedOn()) {
                    d.setTurnedOn(true);
                    EngineSim.tell(lead, "§7MU: engine start " + EngineSim.label(d));
                }
            g.nextStart = now + 80;
        }
    }

    /** what the MU train lines carry besides the notch and reverser */
    private static void trainLines(Locomotive x, SimData.Car c, Locomotive lead, boolean sanding) {
        if (x.getDefinition().hasIndependentBrake() && lead.getDefinition().hasIndependentBrake())
            x.setIndependentBrake(lead.getIndependentBrake());
        if (sanding) c.sandHold = Math.max(c.sandHold, 0.6);
    }

    // ---- the driver's MU / DP functions (display soft keys, commands) --------------------------------------------------

    /** ENGINE START ALL / STOP ALL over the engine-run train line */
    public static String engines(World mc, Locomotive lead, boolean start) {
        Group g = BY_UNIT.get(lead.getUUID());
        if (g == null) return "§7Single unit - use its own engine switch";
        int n = 0;
        SimData data = SimData.get(mc);
        for (Unit u : g.units) {
            if (u.role != LEAD && u.role != MU && u.role != DP) continue;
            if (u.role == DP && !u.comm) continue;
            for (LocomotiveDiesel d : cam72cam.mod.world.World.get(mc).getEntities(LocomotiveDiesel.class)) {
                if (!d.getUUID().equals(u.id) || Electric.isElectric(d, RailProps.get(mc).of(d.getUUID()))) continue;
                if (start) {
                    if (!d.isTurnedOn() && !data.of(d.getUUID()).isolated && !g.startQueue.contains(u.id)) { g.startQueue.add(u.id); n++; }
                } else if (d.isTurnedOn() && u.role != LEAD) { d.setTurnedOn(false); n++; }
            }
        }
        if (start) g.nextStart = Math.min(g.nextStart, mc.getTotalWorldTime());
        return start ? (n == 0 ? "§7Every engine in the consist is running (or isolated)" : "§aMU engine start: " + n + " unit" + (n > 1 ? "s" : "") + ", 4 s apart")
                : (n == 0 ? "§7No trailing engine running" : "§eMU engine stop: " + n + " trailing unit" + (n > 1 ? "s" : ""));
    }

    /** step the DP mode of every remote (SYNC -> INDEP -> IDLE) */
    public static String dpMode(World mc, Locomotive lead) {
        Group g = BY_UNIT.get(lead.getUUID());
        if (g == null) return "§7No distributed power in this train";
        SimData data = SimData.get(mc);
        int mode = -1, n = 0;
        for (Unit u : g.units) if (u.role == DP) {
            SimData.Car c = data.of(u.id);
            if (mode < 0) mode = (c.dpMode + 1) % 3;
            c.dpMode = mode;
            if (mode == 1) c.dpNotch = lead.getThrottle();
            n++;
        }
        data.markDirty();
        return n == 0 ? "§7No distributed power in this train" : "§bDP " + DP_MODE[mode] + (mode == 1 ? " - remotes hold their own notch behind the fence (DP + / DP -)" : "");
    }

    /** INDEPENDENT remotes: notch up / down one step */
    public static String dpNotch(World mc, Locomotive lead, int step) {
        Group g = BY_UNIT.get(lead.getUUID());
        if (g == null) return "§7No distributed power in this train";
        SimData data = SimData.get(mc);
        int notches = lead instanceof LocomotiveDiesel d && d.getDefinition().getThrottleNotches() > 0 ? d.getDefinition().getThrottleNotches() : 8;
        float v = -1;
        for (Unit u : g.units) if (u.role == DP) {
            SimData.Car c = data.of(u.id);
            if (c.dpMode != 1) return "§7Set DP to INDEP (behind the fence) first";
            c.dpNotch = Math.max(0f, Math.min(1f, Math.round(c.dpNotch * notches + step) / (float) notches));
            v = c.dpNotch;
        }
        data.markDirty();
        return v < 0 ? "§7No distributed power in this train" : "§bDP remotes notch " + Math.round(v * notches) + "/" + notches;
    }

    /** cut a unit in / out of the MU (jumper or DP radio), by its position in the consist (1 = first) */
    public static String cut(World mc, Locomotive lead, int index) {
        Group g = BY_UNIT.get(lead.getUUID());
        if (g == null) return "§7Single unit";
        for (Unit u : g.units) if (u.index == index) {
            if (u.role == LEAD) return "§7That is the lead unit";
            SimData.Car c = SimData.get(mc).of(u.id);
            c.muCut = !c.muCut;
            SimData.get(mc).markDirty();
            return (c.muCut ? "§eUnit " + index + " " + u.name + " CUT OUT of the MU" : "§aUnit " + index + " " + u.name + " cut in");
        }
        return "§7No unit " + index;
    }

    // ---- readouts ----------------------------------------------------------------------------------------------------

    /** one line per unit for the driver's display / console: "1 LEAD 6012 RUN N5 88C" */
    public static List<String> lines(World mc, Locomotive lead) {
        List<String> out = new ArrayList<>();
        Group g = BY_UNIT.get(lead.getUUID());
        if (g == null) return out;
        SimData data = SimData.get(mc);
        for (Unit u : g.units) {
            SimData.Car c = data.peek(u.id);
            StringBuilder b = new StringBuilder();
            b.append(u.index).append(' ').append(ROLE[u.role]);
            if (u.role == DP) b.append(' ').append(DP_MODE[c == null ? 0 : c.dpMode]).append(u.comm ? "" : " NO COMM " + Math.round(u.commLostSec) + "s");
            b.append(' ').append(u.forward ? '>' : '<').append(' ').append(u.name);
            if (c != null) {
                if (c.phase == EngineSim.RUN) b.append(" RUN ").append(Math.round(c.rpm)).append("rpm");
                else if (c.aessStopped) b.append(" AESS");
                if (c.isolated) b.append(" ISO");
                if (c.derate < 1) b.append(" P").append(Math.round(c.derate * 100)).append('%');
                if (!c.faults.isEmpty()) b.append(" F:").append(c.faults.iterator().next().split(" ")[0]);
            }
            out.add(b.toString());
        }
        return out;
    }

    /** "3 units MU AAR-27 · DP SYNC" */
    public static String summary(UUID unit) {
        Group g = BY_UNIT.get(unit);
        if (g == null) return "";
        int mu = 0, dp = 0, bad = 0;
        for (Unit u : g.units) { if (u.role == MU) mu++; else if (u.role == DP) dp++; else if (u.role >= INCOMPAT) bad++; }
        return g.units.size() + " units " + g.system + (mu > 0 ? " · MU " + mu : "") + (dp > 0 ? " · DP " + dp : "") + (bad > 0 ? " · " + bad + " not answering" : "")
                + (g.commLoss ? " · DP COMM LOSS" : "");
    }

    /** /irextras mu [system <name>|cut <n>|dp|dp+|dp-|start|stop] */
    public static void command(World world, net.minecraft.command.ICommandSender sender, String[] args) {
        Locomotive l = TrainSim.nearestLoco(world, sender);
        if (l == null) { TrainSim.say(sender, "§7No locomotive within 16 blocks."); return; }
        Group g = BY_UNIT.get(l.getUUID());
        Locomotive lead = l;
        if (g != null) for (Locomotive x : cam72cam.mod.world.World.get(world).getEntities(Locomotive.class)) if (x.getUUID().equals(g.lead)) lead = x;
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "system": {
                SimData.Car c = SimData.get(world).of(l.getUUID());
                if (args.length > 2) {
                    String s = args[2].toUpperCase(Locale.ROOT);
                    c.muSystem = s.equals("AUTO") ? "" : s;
                    SimData.get(world).markDirty();
                }
                TrainSim.say(sender, "§b" + EngineSim.label(l) + " MU system: §f" + system(world, l) + (c.muSystem.isEmpty() ? " §7(auto)" : "") + " §8- AAR-27 | UIC-13 | EMU | NONE | ANY | AUTO");
                return;
            }
            case "cut": TrainSim.say(sender, cut(world, lead, args.length > 2 ? parse(args[2]) : 2)); return;
            case "dp": TrainSim.say(sender, dpMode(world, lead)); return;
            case "dp+": TrainSim.say(sender, dpNotch(world, lead, 1)); return;
            case "dp-": TrainSim.say(sender, dpNotch(world, lead, -1)); return;
            case "start": TrainSim.say(sender, engines(world, lead, true)); return;
            case "stop": TrainSim.say(sender, engines(world, lead, false)); return;
            default:
                if (g == null) { TrainSim.say(sender, "§7" + EngineSim.label(l) + " runs alone (MU system " + system(world, l) + ")"); return; }
                TrainSim.say(sender, "§bMU consist, lead " + EngineSim.label(lead) + ": §f" + summary(lead.getUUID()));
                for (String s : lines(world, lead)) TrainSim.say(sender, "§7" + s);
                TrainSim.say(sender, "§8/irextras mu system <AAR-27|UIC-13|EMU|NONE|ANY|AUTO> | cut <n> | dp | dp+ | dp- | start | stop");
        }
    }

    private static int parse(String s) { try { return Integer.parseInt(s); } catch (NumberFormatException e) { return -1; } }
}
