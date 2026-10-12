package com.dogpound.railmap.program;

import cam72cam.immersiverailroading.library.SwitchState;
import cam72cam.mod.math.Vec3i;
import com.dogpound.railmap.auto.Interlocking;
import com.dogpound.railmap.signal.Aspect;
import com.dogpound.railmap.signal.SignalRegistry;
import com.dogpound.railmap.signal.TileSignalMast;
import com.dogpound.railmap.signal.TileTrackCircuit;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.*;

/**
 * The signal box's memory for one world: 64 wireless channels (with names), the program (rules),
 * and the alarm log. Desks, redstone interfaces and the Signal Box screen all read and write here.
 */
public final class ProgramData extends WorldSavedData {
    public static final String NAME = "irextras_program";
    public static final int CHANNELS = 64;

    public final boolean[] channel = new boolean[CHANNELS + 1];
    public final String[] channelName = new String[CHANNELS + 1];
    public final List<Rule> rules = new ArrayList<>();
    public final List<String> alarms = new ArrayList<>();
    public int nextId = 1;
    /** bumped on every change so open screens know to refresh */
    public int version;

    // ---- her ChatGPT list #98: variables, timers, event subscriptions, routines and a watchdog ----------------------
    /** program variables (numbers), set by SET_VAR, logic modules and the API; read by EXPR conditions */
    public final Map<String, Double> vars = new TreeMap<>();
    /** program timers: name -> {ends at (total world tick), length in ticks} */
    public final Map<String, long[]> timers = new TreeMap<>();
    /** the watchdog stopped the program: why (empty = running) */
    public String halted = "";
    public long haltedAt;
    /** events posted since the last pass (heard by EVENT conditions on the next pass) and the ones this pass hears */
    private Set<String> pending = new LinkedHashSet<>(), hearing = new LinkedHashSet<>();
    /** the last few events, for the VARS page and the console */
    public final List<String> recentEvents = new ArrayList<>();
    /** last pass: rule evaluations made and microseconds taken (the watchdog's meters) */
    public int lastSteps;
    public long lastMicros, worstMicros;
    private transient int steps;
    private transient long passStart;
    private final transient Map<Integer, Integer> errors = new HashMap<>();

    public ProgramData() { this(NAME); }
    public ProgramData(String n) { super(n); java.util.Arrays.fill(channelName, ""); }

    public static ProgramData get(World world) {
        MapStorage st = world.getPerWorldStorage();
        ProgramData d = (ProgramData) st.getOrLoadData(ProgramData.class, NAME);
        if (d == null) { d = new ProgramData(); st.setData(NAME, d); }
        return d;
    }

    public boolean ch(int n) { return n >= 1 && n <= CHANNELS && channel[n]; }

    public void setChannel(int n, boolean v) {
        if (n < 1 || n > CHANNELS || channel[n] == v) return;
        channel[n] = v;
        post("channel:" + n + ":" + (v ? "on" : "off"));
        changed();
    }

    // ---- variables, timers, events -----------------------------------------------------------------------------------
    public double var(String name) { Double v = vars.get(name); return v == null ? 0 : v; }

    public void setVar(String name, double v) {
        Double was = vars.get(name);
        if (was != null && was == v) return;
        vars.put(name, v);
        markDirty();                                    // no version bump: a counter ticking would resync every screen
    }

    public void startTimer(World w, String name, double seconds) {
        long len = Math.max(1, Math.round(seconds * 20));
        timers.put(name, new long[]{w.getTotalWorldTime() + len, len});
        changed();
    }

    public void stopTimer(String name) { if (timers.remove(name) != null) changed(); }

    public double timerLeft(World w, String name) {
        long[] t = timers.get(name);
        return t == null ? 0 : Math.max(0, (t[0] - w.getTotalWorldTime()) / 20.0);
    }

    public boolean timerDone(World w, String name) {
        long[] t = timers.get(name);
        return t != null && w.getTotalWorldTime() >= t[0];
    }

    /** an event for EVENT conditions ("alarm", "alarm:power", "arrive:Harbor", "channel:5:on", "logic:...", your own) */
    public void post(String event) {
        if (pending.size() < 256) pending.add(event.toLowerCase(Locale.ROOT));
        recentEvents.add(0, event);
        while (recentEvents.size() > 20) recentEvents.remove(recentEvents.size() - 1);
    }

    public static void post(World w, String event) { if (w != null && !w.isRemote) get(w).post(event); }

    /** did this pass hear an event matching the pattern ("arrive:*" matches every arrival) */
    public boolean heard(String pattern) {
        String p = pattern.trim().toLowerCase(Locale.ROOT);
        if (p.endsWith("*")) { String pre = p.substring(0, p.length() - 1); for (String e : hearing) if (e.startsWith(pre)) return true; return false; }
        return hearing.contains(p);
    }

    /** RESET on the VARS page / command: start the program again after the watchdog stopped it */
    public String resetWatchdog(World w, String who) {
        if (halted.isEmpty()) return "The program is running - nothing to reset";
        String why = halted;
        halted = "";
        errors.clear();
        com.dogpound.railmap.alarm.AlarmManager.get(w).clear("program:watchdog");
        com.dogpound.railmap.api.Audit.log(w, com.dogpound.railmap.api.Audit.Src.PROGRAM, who, null, "signal box program", "watchdog reset", "HALTED", "RUNNING", "was: " + why);
        changed();
        return "Program restarted (it had stopped: " + why + ")";
    }

    private static final class Watchdog extends RuntimeException {
        Watchdog(String m) { super(m, null, false, false); }
    }

    private void trip(World w, String why) {
        halted = why;
        haltedAt = w.getTotalWorldTime();
        com.dogpound.railmap.alarm.AlarmManager.get(w).raise(w, "program:watchdog", com.dogpound.railmap.alarm.AlarmManager.Cat.AUTO,
                com.dogpound.railmap.alarm.AlarmManager.Sev.MAJOR, "Program watchdog stopped the signal box program: " + why, null, true);
        com.dogpound.railmap.api.Audit.log(w, com.dogpound.railmap.api.Audit.Src.PROGRAM, "watchdog", null, "signal box program", "halt", "RUNNING", "HALTED", why);
        alarms.add(0, "WATCHDOG: " + why);
        changed();
    }

    public void changed() { version++; markDirty(); }

    public Rule rule(int id) {
        for (Rule r : rules) if (r.id == id) return r;
        return null;
    }

    public void alarm(World w, String text, BlockPos at) {
        com.dogpound.railmap.alarm.AlarmManager.fromText(w, text, at);
        long t = (w.getWorldTime() + 6000) % 24000;
        alarms.add(0, String.format("%02d:%02d  %s", t / 1000, (t % 1000) * 60 / 1000, text));
        while (alarms.size() > 50) alarms.remove(alarms.size() - 1);
        changed();
        for (EntityPlayer p : w.playerEntities)
            if (at == null || p.getDistanceSq(at) < 256 * 256) p.sendMessage(new TextComponentString("§c§l⚠ SIGNAL BOX ALARM §r§c" + text));
    }

    // ---- the engine: evaluated every half second by ProgramTicker ------------------------------------
    public void run(World w) {
        hearing = pending;
        pending = new LinkedHashSet<>();
        if (!halted.isEmpty()) return;
        steps = 0;
        passStart = System.nanoTime();
        try {
            runGroup(w, "", 0);
        } catch (Watchdog wd) {
            trip(w, wd.getMessage());
        }
        lastSteps = steps;
        lastMicros = (System.nanoTime() - passStart) / 1000;
        worstMicros = Math.max(worstMicros, lastMicros);
    }

    /** run one routine's rules (group "" = the main program); routines run their actions every time they are called */
    private void runGroup(World w, String group, int depth) {
        if (depth > com.dogpound.railmap.api.ApiConfig.programMaxDepth)
            throw new Watchdog("routines nested deeper than " + com.dogpound.railmap.api.ApiConfig.programMaxDepth + " (a CALL loop?) at '" + group + "'");
        boolean any = false;
        List<Rule> order = new ArrayList<>(rules);
        order.sort((a, b) -> Integer.compare(b.priority, a.priority));          // next-48 #41: priorities (stable sort keeps the order)
        for (Rule r : order) {
            if (!r.group.equalsIgnoreCase(group)) continue;
            any = true;
            if (!r.enabled) continue;
            if (++steps > com.dogpound.railmap.api.ApiConfig.programMaxSteps)
                throw new Watchdog("more than " + com.dogpound.railmap.api.ApiConfig.programMaxSteps + " steps in one pass (last: rule " + label(r) + ")");
            if ((steps & 15) == 0 && System.nanoTime() - passStart > com.dogpound.railmap.api.ApiConfig.programMaxMillis * 1_000_000L)
                throw new Watchdog("one pass took over " + com.dogpound.railmap.api.ApiConfig.programMaxMillis + " ms (last: rule " + label(r) + ")");
            boolean v;
            try {
                v = test(w, r, r.cond, r.condPos, r.condChannel, r.condText) && (r.cond2 == null || test(w, r, r.cond2, r.cond2Pos, r.cond2Channel, r.cond2Text));
            } catch (Watchdog wd) {
                throw wd;
            } catch (RuntimeException | LinkageError e) {
                ruleError(w, r, e.getMessage() == null ? e.toString() : e.getMessage());
                continue;
            }
            int now = v ? 1 : 0;
            boolean edge = now != r.last;
            r.last = now;
            Rule.Act a = v ? r.act : r.elseAct;
            long p = v ? r.actPos : r.elsePos, p2 = v ? r.actPos2 : r.elsePos2;
            int c = v ? r.actChannel : r.elseChannel;
            String text = v ? r.text : r.elseText;
            // a signal held by a WHEN with no ELSE goes back to the track when the WHEN stops being true
            if (!v && edge && a == Rule.Act.NOTHING && r.act.name().startsWith("SIGNAL")) { a = Rule.Act.SIGNAL_AUTO; p = r.actPos; }
            boolean level = a.name().startsWith("SIGNAL") || depth > 0 || v && (r.cond == Rule.Cond.EVENT || r.cond2 == Rule.Cond.EVENT);
            if (edge || level) {
                try {
                    act(w, r, a, p, p2, c, text, depth);
                    errors.remove(r.id);
                    if (a != Rule.Act.NOTHING && depth == 0) { r.fired++; r.remember(w.getTotalWorldTime(), (v ? "THEN " : "ELSE ") + a.label); }
                } catch (Watchdog wd) {
                    throw wd;
                } catch (RuntimeException | LinkageError e) {
                    ruleError(w, r, e.getMessage() == null ? e.toString() : e.getMessage());
                }
            }
        }
        if (!any && depth > 0) alarmOnce(w, "program:noroutine:" + group, "CALL to a routine nobody wrote: '" + group + "'");
    }

    /** a rule that keeps failing is switched off with an alarm (a bad expression must not fail forever) */
    private void ruleError(World w, Rule r, String why) {
        r.errors++;
        r.remember(w.getTotalWorldTime(), "ERROR " + why);
        if (r.errAct != Rule.Act.NOTHING && errors.getOrDefault(r.id, 0) == 0) {    // next-48 #41: ON ERROR handler, once per run of failures
            try { act(w, r, r.errAct, r.actPos, r.actPos2, r.actChannel, r.errText.replace("{error}", why), 1); }
            catch (Watchdog wd) { throw wd; }
            catch (RuntimeException | LinkageError ignored) { }
        }
        int n = errors.merge(r.id, 1, Integer::sum);
        if (n < com.dogpound.railmap.api.ApiConfig.ruleErrorLimit) return;
        r.enabled = false;
        errors.remove(r.id);
        alarm(w, "Rule " + label(r) + " switched off after " + n + " errors: " + why, null);
        com.dogpound.railmap.api.Audit.log(w, com.dogpound.railmap.api.Audit.Src.PROGRAM, "watchdog", null, "rule " + label(r), "disable", "ON", "OFF", why);
    }

    private final transient Set<String> once = new HashSet<>();
    private void alarmOnce(World w, String key, String text) { if (once.add(key)) alarm(w, text, null); }

    private boolean test(World w, Rule r, Rule.Cond c, long lp, int channel, String text) {
        switch (c) {
            case EXPR: {
                String[] err = new String[1];
                boolean v = com.dogpound.railmap.api.Expr.test(w, text, err);
                if (err[0] != null) throw new IllegalArgumentException("expression '" + text + "': " + err[0]);
                return v;
            }
            case EVENT: return !text.trim().isEmpty() && heard(text);
            case TIMER_DONE: return timerDone(w, text.trim());
            case VEHICLE_NEAR: return lp != 0 && com.dogpound.railmap.api.Transport.near(w, BlockPos.fromLong(lp), r.radius, false);
            default: return test(w, c, lp, channel, r.radius);
        }
    }

    static boolean test(World w, Rule.Cond c, long lp, int channel, int radius) {
        BlockPos p = BlockPos.fromLong(lp);
        switch (c) {
            case ALWAYS: return true;
            case CHANNEL_ON: return get(w).ch(channel);
            case CHANNEL_OFF: return !get(w).ch(channel);
            case DAYTIME: return w.isDaytime();
            case NIGHT: return !w.isDaytime();
            case REDSTONE: return lp != 0 && w.isBlockLoaded(p) && w.getRedstonePowerFromNeighbors(p) > 0;
            case OCCUPIED: case CLEAR: {
                boolean occ;
                TileEntity te = lp == 0 || !w.isBlockLoaded(p) ? null : w.getTileEntity(p);
                occ = te instanceof TileTrackCircuit tc ? tc.occupied() : trainNear(w, p, 3, false);
                return c == Rule.Cond.OCCUPIED ? occ : !occ;
            }
            case TRAIN_NEAR: return lp != 0 && trainNear(w, p, radius, false);
            case TRAIN_STOPPED: return lp != 0 && trainNear(w, p, radius, true);
            case SIGNAL_RED: case SIGNAL_PROCEED: {
                TileSignalMast m = mast(w, p);
                if (m == null) return false;
                boolean red = m.aspect() == Aspect.STOP || m.aspect() == Aspect.STOP_AND_PROCEED || m.aspect() == Aspect.DARK;
                return c == Rule.Cond.SIGNAL_RED ? red : !red;
            }
            case SWITCH_STRAIGHT: return lp != 0 && Interlocking.stateAt(w, p) == SwitchState.STRAIGHT;
            case SWITCH_TURN: return lp != 0 && Interlocking.stateAt(w, p) == SwitchState.TURN;
            case ROUTE_SET:
                for (Interlocking.Route r : Interlocking.routes(w)) if (r.start.equals(p)) return true;
                return false;
            default: return false;
        }
    }

    static boolean trainNear(World w, BlockPos p, int radius, boolean stopped) {
        cam72cam.mod.world.World u = cam72cam.mod.world.World.get(w);
        if (u == null) return false;
        for (cam72cam.immersiverailroading.entity.EntityRollingStock s : u.getEntities(cam72cam.immersiverailroading.entity.EntityRollingStock.class)) {
            cam72cam.mod.math.Vec3d q = s.getPosition();
            double dx = q.x - p.getX() - 0.5, dy = q.y - p.getY(), dz = q.z - p.getZ() - 0.5;
            if (dx * dx + dz * dz > (double) radius * radius || Math.abs(dy) > 8) continue;
            if (stopped && s instanceof cam72cam.immersiverailroading.entity.EntityMoveableRollingStock m && m.getCurrentSpeed() != null
                    && Math.abs(m.getCurrentSpeed().metric()) > 2) continue;
            return true;
        }
        return false;
    }

    /** a signal by the position picked on the map: the mast itself, or the rail it watches */
    public static TileSignalMast mast(World w, BlockPos p) {
        if (p == null || !w.isBlockLoaded(p)) return null;
        TileEntity te = w.getTileEntity(p);
        if (te instanceof TileSignalMast m) return m;
        return SignalRegistry.mastAt(w.provider.getDimension(), new Vec3i(p.getX(), p.getY(), p.getZ()));
    }

    private void act(World w, Rule r, Rule.Act a, long lp, long lp2, int c, String text, int depth) {
        BlockPos p = BlockPos.fromLong(lp);
        String owner = "program#" + r.id;
        switch (a) {
            case NOTHING: return;
            case SET_VAR: {
                String name = text.contains("=") ? text.substring(0, text.indexOf('=')).trim() : text.trim();
                double before = var(name);
                double v = com.dogpound.railmap.api.Expr.assign(w, text);
                if (before != v && depth == 0) com.dogpound.railmap.api.Audit.log(w, com.dogpound.railmap.api.Audit.Src.PROGRAM, owner, null, "variable " + name, "set", com.dogpound.railmap.api.Expr.fmt(before), com.dogpound.railmap.api.Expr.fmt(v), "ok");
                return;
            }
            case TIMER_START: {
                String[] k = text.trim().split("\\s+");
                double secs = k.length > 1 ? com.dogpound.railmap.api.Expr.sum(w, k[1]) : 10;
                startTimer(w, k[0], secs);
                return;
            }
            case TIMER_STOP: stopTimer(text.trim()); return;
            case CALL: runGroup(w, text.trim(), depth + 1); return;
            case EMIT: post(text.trim()); return;
            case PRESS: {
                TileEntity te = lp == 0 || !w.isBlockLoaded(p) ? null : w.getTileEntity(p);
                String id = text.trim();
                String res;
                if (te instanceof com.dogpound.railmap.controls.IControlled m)
                    res = com.dogpound.railmap.api.Audit.press(w, com.dogpound.railmap.api.Audit.Src.PROGRAM, owner, m, id, () -> com.dogpound.railmap.controls.IControlled.operate(m, id, null));
                else if (te instanceof com.dogpound.railmap.wire.IWiredMachine m) {
                    res = m.wiredPress(id, owner);
                    com.dogpound.railmap.api.Audit.log(w, com.dogpound.railmap.api.Audit.Src.PROGRAM, owner, p, m.wiredName(), "press " + id, "", "", res);
                } else res = "§cno machine with controls there";
                if (res != null && res.startsWith("§c")) alarm(w, "Rule '" + label(r) + "' could not press " + id + " at " + Rule.where(lp) + ": " + res.replaceAll("§.", ""), p);
                return;
            }
            case SIGNAL_STOP: case SIGNAL_RESTRICTING: case SIGNAL_APPROACH: case SIGNAL_CLEAR: case SIGNAL_AUTO: {
                TileSignalMast m = mast(w, p);
                if (m == null) return;
                switch (a) {
                    case SIGNAL_STOP -> m.setRelayControl(true, null);
                    case SIGNAL_RESTRICTING -> m.setRelayControl(false, Aspect.RESTRICTING);
                    case SIGNAL_APPROACH -> m.setRelayControl(false, Aspect.APPROACH);
                    case SIGNAL_CLEAR -> m.setRelayControl(false, Aspect.CLEAR);
                    default -> m.setRelayControl(false, null);
                }
                return;
            }
            case SWITCH_STRAIGHT: case SWITCH_TURN: {
                Vec3i v = new Vec3i(p.getX(), p.getY(), p.getZ());
                SwitchState was = Interlocking.stateAt(w, p);
                boolean ok = Interlocking.claim(w, v, a == Rule.Act.SWITCH_TURN ? SwitchState.TURN : SwitchState.STRAIGHT, owner);
                Interlocking.release(w, v, owner);
                com.dogpound.railmap.api.Audit.log(w, com.dogpound.railmap.api.Audit.Src.PROGRAM, owner, p, "switch", a.label, String.valueOf(was), String.valueOf(Interlocking.stateAt(w, p)), ok ? "ok" : "refused: occupied or locked");
                if (!ok) alarm(w, "Rule '" + label(r) + "' could not throw the switch at " + Rule.where(lp) + " (occupied or locked)", p);
                return;
            }
            case SWITCH_AUTO: Interlocking.setAuto(w, p); return;
            case CHANNEL_ON: case CHANNEL_OFF: {
                boolean was = ch(c);
                setChannel(c, a == Rule.Act.CHANNEL_ON);
                if (was != ch(c) && depth == 0) com.dogpound.railmap.api.Audit.log(w, com.dogpound.railmap.api.Audit.Src.PROGRAM, owner, null, "channel " + c, a.label, was ? "on" : "off", ch(c) ? "on" : "off", "ok");
                return;
            }
            case ROUTE: {
                String res = Interlocking.setRoute(w, p, BlockPos.fromLong(lp2));
                com.dogpound.railmap.api.Audit.log(w, com.dogpound.railmap.api.Audit.Src.PROGRAM, owner, p, "route", "set to " + Rule.where(lp2), "", "", res == null ? "ok" : res);
                if (res != null && !res.toLowerCase().contains("set")) alarm(w, "Route " + Rule.where(lp) + " -> " + Rule.where(lp2) + ": " + res, p);
                return;
            }
            case CANCEL_ROUTE: Interlocking.cancelRoute(w, p); return;
            case ANNOUNCE:
                for (EntityPlayer pl : w.playerEntities)
                    if (lp == 0 || pl.getDistanceSq(p) < 96 * 96) pl.sendMessage(new TextComponentString("§b📢 §f" + text));
                return;
            case ALARM: alarm(w, text.isEmpty() ? label(r) : text, lp == 0 ? null : p); return;
            default:
        }
    }

    private static String label(Rule r) { return r.name.isEmpty() ? "#" + r.id : r.name; }

    // ---- save ---------------------------------------------------------------------------------------
    @Override
    public void readFromNBT(NBTTagCompound t) {
        rules.clear();
        NBTTagList l = t.getTagList("rules", 10);
        for (int i = 0; i < l.tagCount(); i++) rules.add(Rule.read(l.getCompoundTagAt(i)));
        nextId = Math.max(1, t.getInteger("next"));
        int[] ch = t.getIntArray("ch");
        for (int i = 1; i <= CHANNELS; i++) channel[i] = i - 1 < ch.length && ch[i - 1] != 0;
        NBTTagList names = t.getTagList("names", 8);
        for (int i = 0; i < names.tagCount() && i + 1 <= CHANNELS; i++) channelName[i + 1] = names.getStringTagAt(i);
        alarms.clear();
        NBTTagList al = t.getTagList("alarms", 8);
        for (int i = 0; i < al.tagCount(); i++) alarms.add(al.getStringTagAt(i));
        vars.clear();
        NBTTagCompound vs = t.getCompoundTag("vars");
        for (String k : vs.getKeySet()) vars.put(k, vs.getDouble(k));
        timers.clear();
        NBTTagCompound ts = t.getCompoundTag("timers");
        for (String k : ts.getKeySet()) { long[] v = { ts.getCompoundTag(k).getLong("end"), ts.getCompoundTag(k).getLong("len") }; timers.put(k, v); }
        halted = t.getString("halted");
        haltedAt = t.getLong("haltedAt");
        lastSteps = t.getInteger("steps"); lastMicros = t.getLong("us"); worstMicros = t.getLong("worst");
        recentEvents.clear();
        NBTTagList ev = t.getTagList("events", 8);
        for (int i = 0; i < ev.tagCount(); i++) recentEvents.add(ev.getStringTagAt(i));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        NBTTagList l = new NBTTagList();
        for (Rule r : rules) l.appendTag(r.write());
        t.setTag("rules", l);
        t.setInteger("next", nextId);
        int[] ch = new int[CHANNELS];
        for (int i = 1; i <= CHANNELS; i++) ch[i - 1] = channel[i] ? 1 : 0;
        t.setIntArray("ch", ch);
        NBTTagList names = new NBTTagList();
        for (int i = 1; i <= CHANNELS; i++) names.appendTag(new NBTTagString(channelName[i] == null ? "" : channelName[i]));
        t.setTag("names", names);
        NBTTagList al = new NBTTagList();
        for (String s : alarms) al.appendTag(new NBTTagString(s));
        t.setTag("alarms", al);
        NBTTagCompound vs = new NBTTagCompound();
        for (Map.Entry<String, Double> e : vars.entrySet()) vs.setDouble(e.getKey(), e.getValue());
        t.setTag("vars", vs);
        NBTTagCompound ts = new NBTTagCompound();
        for (Map.Entry<String, long[]> e : timers.entrySet()) { NBTTagCompound x = new NBTTagCompound(); x.setLong("end", e.getValue()[0]); x.setLong("len", e.getValue()[1]); ts.setTag(e.getKey(), x); }
        t.setTag("timers", ts);
        t.setString("halted", halted);
        t.setLong("haltedAt", haltedAt);
        t.setInteger("steps", lastSteps); t.setLong("us", lastMicros); t.setLong("worst", worstMicros);   // for the client's VARS page
        NBTTagList ev = new NBTTagList();
        for (String e : recentEvents) ev.appendTag(new NBTTagString(e));
        t.setTag("events", ev);
        return t;
    }
}
