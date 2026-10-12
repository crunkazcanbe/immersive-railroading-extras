package com.dogpound.railmap.safety;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.Interlocking;
import com.dogpound.railmap.program.ProgramData;
import com.dogpound.railmap.signal.TileSignalMast;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.*;

/**
 * Route-setting macros (her ChatGPT batch-3 list #19): sixteen stored sequences of routes (entrance signal > exit
 * signal) a signaller sets with one button - "up main through platform 2 and out to the junction". A macro is made by
 * RECORDING (key to REC on a Route Macro Panel, press a macro button, then set the routes the normal way - NX desk,
 * control desk, dispatcher board; every route the interlocking grants is added), or step by step with the Signal Wrench
 * (pick the entrance signal, click ENTRANCE, pick the exit, click EXIT). Played back two ways:
 * <ul>
 *   <li>SEQ - one route at a time through the real interlocking (every safety check applies); a refused step is
 *   retried each second for up to 30 s (the train in front clearing it), then the macro stops FAILED with the reason -
 *   the conflict visualizer shows what blocked it;</li>
 *   <li>ALL - all or nothing: every route is set in the same second; if any is refused the ones just set are cancelled
 *   again, so a half-set macro never stands.</li>
 * </ul>
 * CANCEL takes back the routes the macro's last run set. A macro can be triggered by an automation channel (rising
 * edge, ProgramData), and tells the program "macro:N:set" / "macro:N:failed".
 */
public final class Macros extends WorldSavedData {
    private static final String NAME = "irextras_macros";
    public static final int N = 16;
    public static final int SEQ = 0, ALL = 1;
    static final long RETRY_TICKS = 600;

    public static final class Macro {
        public String name = "";
        public final List<long[]> steps = new ArrayList<>();        // {entrance signal, exit signal}
        public int mode, channel, runs, fails;
        public long lastRun;
        public String last = "";
        boolean empty() { return steps.isEmpty(); }
    }

    /** one playback in progress (memory: a restart abandons it, the routes it set stay set) */
    static final class Run {
        int step;
        long stepStart, started;
        String who = "";
        final List<Long> mine = new ArrayList<>();
        String status = "";
    }

    public final Macro[] macros = new Macro[N];
    public final List<String> log = new ArrayList<>();

    /** live state, per world: running macros, the routes each macro's last run set, recording, channel edges */
    final Map<Integer, Run> running = new HashMap<>();
    final Map<Integer, List<Long>> lastSet = new HashMap<>();
    final Map<Integer, Integer> failedAt = new HashMap<>();
    int recording = -1;
    String recorder = "";
    private final boolean[] chWas = new boolean[N];

    public Macros() { this(NAME); }
    public Macros(String n) { super(n); for (int i = 0; i < N; i++) macros[i] = new Macro(); }

    public static Macros get(World w) {
        MapStorage s = w.getPerWorldStorage();
        Macros d = (Macros) s.getOrLoadData(Macros.class, NAME);
        if (d == null) { d = new Macros(); s.setData(NAME, d); }
        return d;
    }

    public Macro macro(int n) { return n >= 1 && n <= N ? macros[n - 1] : null; }
    public String label(int n) { Macro m = macro(n); return m == null ? "?" : m.name.isEmpty() ? "MACRO " + n : m.name; }

    void note(World w, String text) {
        log.add(0, Recorder.clock(w, w.getTotalWorldTime()) + " " + text);
        while (log.size() > 40) log.remove(log.size() - 1);
        Recorder.event(w, "MACRO " + text);
        markDirty();
    }

    /** 0 idle, 1 running, 2 set (every route of its last run still stands), 3 failed */
    public int state(World w, int n) {
        if (running.containsKey(n)) return 1;
        if (failedAt.containsKey(n)) return 3;
        List<Long> l = lastSet.get(n);
        if (l == null || l.isEmpty()) return 0;
        Set<Long> live = new HashSet<>();
        for (Interlocking.Route r : Interlocking.routes(w)) live.add(r.start.toLong());
        for (long s : l) if (!live.contains(s)) return 0;
        return 2;
    }

    public String status(int n) { Run r = running.get(n); return r != null ? r.status : macro(n) == null ? "" : macro(n).last; }
    public int stepOf(int n) { Run r = running.get(n); return r == null ? 0 : r.step + 1; }

    // ---- playback -------------------------------------------------------------------------------------------------
    public String play(World w, int n, String who) {
        Macro m = macro(n);
        if (m == null) return "§cNo macro " + n;
        if (m.empty()) return "§c" + label(n) + " has no routes - record it first (key to REC, press its button, set the routes)";
        if (running.containsKey(n)) return "§e" + label(n) + " is already running";
        if (recording == n) return "§c" + label(n) + " is being recorded - turn the key back first";
        failedAt.remove(n);
        Run r = new Run();
        r.who = who;
        r.started = r.stepStart = w.getTotalWorldTime();
        m.runs++;
        m.lastRun = r.started;
        markDirty();
        lastSet.put(n, r.mine);
        if (m.mode == ALL) {
            for (int i = 0; i < m.steps.size(); i++) {
                String res = setStep(w, m.steps.get(i), r);
                if (res != null) {
                    for (int k = r.mine.size() - 1; k >= 0; k--) Interlocking.cancelRoute(w, BlockPos.fromLong(r.mine.get(k)));
                    r.mine.clear();
                    return fail(w, n, m, i, res, who);
                }
            }
            return done(w, n, m, r);
        }
        running.put(n, r);
        r.status = "STEP 1/" + m.steps.size();
        note(w, label(n) + " started by " + who + " (" + m.steps.size() + " route(s), SEQ)");
        step(w, n);                                                     // first step at once
        return running.containsKey(n) ? "§e" + label(n) + " running - " + r.status : "§a" + label(n) + ": " + m.last;
    }

    /** null = this route stands now (set by us, or it already was); else the refusal */
    private String setStep(World w, long[] s, Run r) {
        BlockPos a = BlockPos.fromLong(s[0]), b = BlockPos.fromLong(s[1]);
        for (Interlocking.Route x : Interlocking.routes(w)) if (x.start.equals(a)) return x.end.equals(b) ? null : "a different route is already set from " + sig(w, a);
        if (!w.isBlockLoaded(a) || !w.isBlockLoaded(b)) return "signal " + sig(w, a) + " / " + sig(w, b) + " not loaded";
        String res = Interlocking.setRoute(w, a, b);
        if (res != null && res.startsWith("Route set")) { r.mine.add(s[0]); return null; }
        return res == null ? "refused" : res;
    }

    private void step(World w, int n) {
        Run r = running.get(n);
        Macro m = macro(n);
        if (r == null || m == null) return;
        long now = w.getTotalWorldTime();
        while (r.step < m.steps.size()) {
            String res = setStep(w, m.steps.get(r.step), r);
            if (res != null) {
                if (now - r.stepStart >= RETRY_TICKS) { running.remove(n); fail(w, n, m, r.step, res, r.who); return; }
                r.status = "STEP " + (r.step + 1) + "/" + m.steps.size() + " WAIT " + (RETRY_TICKS - (now - r.stepStart)) / 20 + "S";
                return;
            }
            r.step++;
            r.stepStart = now;
        }
        running.remove(n);
        done(w, n, m, r);
    }

    private String done(World w, int n, Macro m, Run r) {
        m.last = "SET " + m.steps.size() + " ROUTE" + (m.steps.size() == 1 ? "" : "S") + " IN " + Math.max(1, (w.getTotalWorldTime() - r.started) / 20) + "S";
        note(w, label(n) + " set: " + m.steps.size() + " route(s) by " + r.who);
        ProgramData.post(w, "macro:" + n + ":set");
        AlarmManager.get(w).clear("macro:" + n);
        if (com.dogpound.railmap.api.QuestEvents.active())
            com.dogpound.railmap.api.QuestEvents.fire(w, null, null, "macro_run", "macro", n, "routes", m.steps.size(), "mode", m.mode == ALL ? "ALL" : "SEQ");
        return "§a" + label(n) + ": " + m.last;
    }

    private String fail(World w, int n, Macro m, int step, String why, String who) {
        m.fails++;
        long[] s = m.steps.get(step);
        m.last = "FAILED STEP " + (step + 1) + ": " + sig(w, BlockPos.fromLong(s[0])) + " > " + sig(w, BlockPos.fromLong(s[1]));
        failedAt.put(n, step);
        note(w, label(n) + " FAILED at step " + (step + 1) + " (" + who + "): " + why);
        ProgramData.post(w, "macro:" + n + ":failed");
        AlarmManager.get(w).raise(w, "macro:" + n, AlarmManager.Cat.SIGNAL, AlarmManager.Sev.MINOR,
                "Route macro " + label(n) + " failed at step " + (step + 1) + ": " + why, BlockPos.fromLong(s[0]), false);
        markDirty();
        return "§c" + label(n) + " " + m.last + " - " + why;
    }

    /** take back the routes this macro's last run set (and stop it if it is running) */
    public String cancel(World w, int n, String who) {
        Run r = running.remove(n);
        List<Long> l = lastSet.remove(n);
        failedAt.remove(n);
        int k = 0;
        if (l != null) for (int i = l.size() - 1; i >= 0; i--) if (Interlocking.cancelRoute(w, BlockPos.fromLong(l.get(i))).startsWith("Route cancelled")) k++;
        AlarmManager.get(w).clear("macro:" + n);
        if (macro(n) != null) { macro(n).last = "CANCELLED " + k + " ROUTE" + (k == 1 ? "" : "S"); markDirty(); }
        note(w, label(n) + (r != null ? " stopped and " : " ") + "cancelled by " + who + ": " + k + " route(s) taken back");
        return "§e" + label(n) + ": " + k + " route(s) cancelled";
    }

    // ---- recording ------------------------------------------------------------------------------------------------
    public String record(World w, int n, String who) {
        Macro m = macro(n);
        if (m == null) return "§cNo macro " + n;
        if (running.containsKey(n)) return "§c" + label(n) + " is running";
        if (recording == n) return stopRecording(w);
        if (recording >= 0) stopRecording(w);
        recording = n;
        recorder = who;
        m.steps.clear();
        lastSet.remove(n);
        failedAt.remove(n);
        m.last = "RECORDING";
        markDirty();
        note(w, label(n) + ": recording by " + who);
        return "§c● REC §e" + label(n) + " - now set the routes in order (NX desk, control desk, board, wrench steps); press the button again to finish";
    }

    public String stopRecording(World w) {
        if (recording < 0) return "§7Not recording";
        int n = recording;
        recording = -1;
        Macro m = macro(n);
        m.last = m.steps.size() + " ROUTE" + (m.steps.size() == 1 ? "" : "S") + " STORED";
        markDirty();
        note(w, label(n) + ": recorded " + m.steps.size() + " route(s)");
        return "§a" + label(n) + " stored: " + m.steps.size() + " route(s)";
    }

    /** SigHooks.routeSet: a route the interlocking just granted - add it to the macro being recorded */
    void heard(World w, BlockPos start, BlockPos end) {
        if (recording < 0) return;
        Macro m = macro(recording);
        for (long[] s : m.steps) if (s[0] == start.toLong()) return;            // a run being replayed / the same route again
        if (m.steps.size() >= 24) return;
        m.steps.add(new long[]{start.toLong(), end.toLong()});
        m.last = "REC " + m.steps.size();
        markDirty();
    }

    public String addStep(World w, int n, BlockPos a, BlockPos b) {
        Macro m = macro(n);
        if (m == null) return "§cNo macro " + n;
        if (!(w.getTileEntity(a) instanceof TileSignalMast) || !(w.getTileEntity(b) instanceof TileSignalMast)) return "§cBoth ends of a route step must be signals";
        if (a.equals(b)) return "§cA route needs two different signals";
        if (m.steps.size() >= 24) return "§c" + label(n) + " is full (24 routes)";
        m.steps.add(new long[]{a.toLong(), b.toLong()});
        m.last = m.steps.size() + " ROUTE" + (m.steps.size() == 1 ? "" : "S") + " STORED";
        markDirty();
        return "§aStep " + m.steps.size() + " of " + label(n) + ": " + sig(w, a) + " > " + sig(w, b);
    }

    public String clear(World w, int n, String who) {
        Macro m = macro(n);
        if (m == null) return "§cNo macro " + n;
        if (running.containsKey(n)) return "§c" + label(n) + " is running";
        m.steps.clear();
        m.last = "EMPTY";
        markDirty();
        note(w, label(n) + " cleared by " + who);
        return "§e" + label(n) + " cleared";
    }

    // ---- once a second --------------------------------------------------------------------------------------------
    void tick(World w) {
        for (Integer n : new ArrayList<>(running.keySet())) step(w, n);
        ProgramData pd = null;
        for (int i = 0; i < N; i++) {
            Macro m = macros[i];
            if (m.channel <= 0) { chWas[i] = false; continue; }
            if (pd == null) pd = ProgramData.get(w);
            boolean on = pd.ch(m.channel);
            if (on && !chWas[i] && !running.containsKey(i + 1)) play(w, i + 1, "channel " + m.channel);
            chWas[i] = on;
        }
    }

    static String sig(World w, BlockPos p) {
        return w.isBlockLoaded(p) && w.getTileEntity(p) instanceof TileSignalMast m ? SignalSighting.name(m) : p.getX() + "," + p.getZ();
    }

    // ---- command --------------------------------------------------------------------------------------------------
    static final String USAGE = "macro <list|play n|cancel n|record n|stop|clear n|mode n seq|all|channel n ch|name n text|add n x1 y1 z1 x2 y2 z2|log>";

    static void command(ICommandSender s, World w, String[] a) {
        Macros d = get(w);
        String op = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "list";
        int n = a.length > 3 ? parse(a[3]) : 0;
        String who = s.getName();
        switch (op) {
            case "play": say(s, d.play(w, n, who)); return;
            case "cancel": say(s, d.cancel(w, n, who)); return;
            case "record": say(s, d.record(w, n, who)); return;
            case "stop": say(s, d.stopRecording(w)); return;
            case "clear": say(s, d.clear(w, n, who)); return;
            case "mode": {
                Macro m = d.macro(n);
                if (m == null || a.length < 5) { say(s, "/irextras sig macro mode <n> seq|all"); return; }
                m.mode = a[4].equalsIgnoreCase("all") ? ALL : SEQ;
                d.markDirty();
                say(s, "§a" + d.label(n) + ": " + (m.mode == ALL ? "ALL (all or nothing)" : "SEQ (one at a time, retried)"));
                return;
            }
            case "channel": {
                Macro m = d.macro(n);
                if (m == null || a.length < 5) { say(s, "/irextras sig macro channel <n> <0-64> (0 = none)"); return; }
                m.channel = Math.max(0, Math.min(ProgramData.CHANNELS, parse(a[4])));
                d.markDirty();
                say(s, "§a" + d.label(n) + (m.channel == 0 ? " has no trigger channel" : " runs when channel " + m.channel + " comes on"));
                return;
            }
            case "name": {
                Macro m = d.macro(n);
                if (m == null || a.length < 5) { say(s, "/irextras sig macro name <n> <text>"); return; }
                m.name = String.join(" ", Arrays.copyOfRange(a, 4, a.length)).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9 .:/>+-]", "");
                if (m.name.length() > 12) m.name = m.name.substring(0, 12);
                d.markDirty();
                say(s, "§aMacro " + n + " is now " + d.label(n));
                return;
            }
            case "add": {
                if (a.length < 10) { say(s, "/irextras sig macro add <n> <x1 y1 z1> <x2 y2 z2>"); return; }
                say(s, d.addStep(w, n, new BlockPos(parse(a[4]), parse(a[5]), parse(a[6])), new BlockPos(parse(a[7]), parse(a[8]), parse(a[9]))));
                return;
            }
            case "log": for (String l : d.log.subList(0, Math.min(12, d.log.size()))) say(s, "§7" + l); return;
            default:
                for (int i = 1; i <= N; i++) {
                    Macro m = d.macro(i);
                    if (m.empty() && m.name.isEmpty()) continue;
                    int st = d.state(w, i);
                    say(s, "§e" + i + " " + d.label(i) + "§r " + m.steps.size() + " route(s) " + (m.mode == ALL ? "ALL" : "SEQ") + (m.channel > 0 ? " ch" + m.channel : "")
                            + " · " + new String[]{"§7idle", "§brunning", "§aSET", "§cFAILED"}[st] + "§r · " + d.status(i));
                    for (int k = 0; k < m.steps.size(); k++) say(s, "   §7" + (k + 1) + ". " + sig(w, BlockPos.fromLong(m.steps.get(k)[0])) + " > " + sig(w, BlockPos.fromLong(m.steps.get(k)[1])));
                }
                if (d.recording >= 0) say(s, "§c● recording " + d.label(d.recording) + " (" + d.macro(d.recording).steps.size() + " so far)");
        }
    }

    /** self-test: the recording / step bookkeeping without a railway (no routes are set) */
    static List<String> selfTest() {
        List<String> out = new ArrayList<>();
        Macros d = new Macros("selftest");
        d.recording = 3;
        Macro m = d.macro(3);
        d.heard(null, BlockPos.fromLong(1), BlockPos.fromLong(2));
        d.heard(null, BlockPos.fromLong(1), BlockPos.fromLong(2));          // the same route again: not a new step
        d.recording = -1;
        d.heard(null, BlockPos.fromLong(5), BlockPos.fromLong(6));          // not recording: ignored
        boolean dupIgnored = m.steps.size() == 1;
        out.add((d.macro(0) == null && d.macro(17) == null && d.macro(16) != null ? "PASS" : "FAIL") + " macro slots 1-16");
        out.add((d.label(3).equals("MACRO 3") ? "PASS" : "FAIL") + " default label");
        m.name = "UP MAIN";
        out.add((d.label(3).equals("UP MAIN") ? "PASS" : "FAIL") + " named label");
        out.add((dupIgnored ? "PASS" : "FAIL") + " recording adds each granted route once, only while recording");
        NBTTagCompound t = d.writeToNBT(new NBTTagCompound());
        Macros e = new Macros("selftest2");
        e.readFromNBT(t);
        out.add((e.macro(3).steps.size() == 1 && e.macro(3).name.equals("UP MAIN") && e.macro(3).steps.get(0)[1] == 2 ? "PASS" : "FAIL") + " save / load round trip");
        out.add((e.state(null, 5) == 0 ? "PASS" : "FAIL") + " idle state with no run");
        return out;
    }

    private static int parse(String v) { try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return 0; } }
    private static void say(ICommandSender s, String m) { s.sendMessage(new TextComponentString(m)); }

    // ---- save -----------------------------------------------------------------------------------------------------
    @Override
    public void readFromNBT(NBTTagCompound t) {
        NBTTagList l = t.getTagList("m", 10);
        for (int i = 0; i < Math.min(N, l.tagCount()); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            Macro m = new Macro();
            m.name = c.getString("n"); m.mode = c.getInteger("mode"); m.channel = c.getInteger("ch"); m.runs = c.getInteger("runs"); m.fails = c.getInteger("fails");
            m.lastRun = c.getLong("lr"); m.last = c.getString("last");
            long[] st = readLongs(c.getTagList("s", 8));
            for (int k = 0; k + 1 < st.length; k += 2) m.steps.add(new long[]{st[k], st[k + 1]});
            macros[i] = m;
        }
        log.clear();
        NBTTagList g = t.getTagList("log", 8);
        for (int i = 0; i < g.tagCount(); i++) log.add(g.getStringTagAt(i));
    }

    private static long[] readLongs(NBTTagList l) {
        long[] r = new long[l.tagCount()];
        for (int i = 0; i < r.length; i++) try { r[i] = Long.parseLong(l.getStringTagAt(i)); } catch (NumberFormatException ignored) { }
        return r;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        NBTTagList l = new NBTTagList();
        for (Macro m : macros) {
            NBTTagCompound c = new NBTTagCompound();
            c.setString("n", m.name); c.setInteger("mode", m.mode); c.setInteger("ch", m.channel); c.setInteger("runs", m.runs); c.setInteger("fails", m.fails);
            c.setLong("lr", m.lastRun); c.setString("last", m.last);
            NBTTagList st = new NBTTagList();
            for (long[] s : m.steps) { st.appendTag(new NBTTagString(String.valueOf(s[0]))); st.appendTag(new NBTTagString(String.valueOf(s[1]))); }
            c.setTag("s", st);
            l.appendTag(c);
        }
        t.setTag("m", l);
        NBTTagList g = new NBTTagList();
        for (String s : log) g.appendTag(new NBTTagString(s));
        t.setTag("log", g);
        return t;
    }
}
