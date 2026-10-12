package com.dogpound.railmap.safety;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.signal.TileSignalMast;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Axle Counter Evaluator (her ChatGPT list #19): the lineside cabinet that turns head counts into section states, four
 * sections per cabinet, the way a real evaluator (ACE) works:
 * <ul>
 *   <li>each section is bounded by heads (Signal Wrench: pick a head, click the section's window; click again to flip
 *   which way counts IN, a third time to unlink). Axles in minus axles out = the count; 0 = CLEAR, more = OCCUPIED;</li>
 *   <li>a head fault, the evaluator losing 12 V, a count going below zero or a changed head layout make the section
 *   DISTURBED - it reads occupied until someone resets it;</li>
 *   <li>the supervised reset: reset key to PREP, press the section's RESET. The section stays occupied (white PREP
 *   lamp) until a train sweeps it - counts in, then out to zero - and only then shows CLEAR;</li>
 *   <li>DIRECT reset (key to DIRECT, RESET twice within 5 s) clears it at once, for when the line has been walked and
 *   seen clear; refused while wheels stand at a head. Every reset goes in the maintenance history and the recorder.</li>
 * </ul>
 * Link signals to a section (pick the signal, click the section) and they are held at STOP while it isn't CLEAR. The
 * interlocking also refuses routes over a section that isn't clear (RouteCheck). A disturbed section raises a standing
 * alarm and sounds the cabinet buzzer until ACK.
 */
public class TileAxleEvaluator extends SafetyTile {
    public static final int N = 4;
    public static final String[] STATE = {"CLEAR", "OCCUPIED", "DISTURBED", "PREP RESET", "not used"};

    public static final class Section {
        /** head pos -> +1 (moving the way its arrow points counts IN) or -1 */
        public final Map<Long, Integer> heads = new LinkedHashMap<>();
        final Map<Long, int[]> seen = new HashMap<>();          // head -> last up/down read
        public final List<Long> signals = new ArrayList<>();
        public int count, state = 4, inTotal, outTotal;
        public String why = "";
        boolean swept;
        long directArmed;
    }

    public final Section[] sec = new Section[N];
    private boolean wasPowered = true, buzzer;

    public TileAxleEvaluator() { for (int i = 0; i < N; i++) sec[i] = new Section(); }

    @Override public String wiredName() { return "Axle counter evaluator"; }

    @Override
    public String gearState() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < N; i++) if (sec[i].state != 4) b.append(b.length() == 0 ? "" : " · ").append(i + 1).append(": ").append(STATE[sec[i].state]).append(sec[i].state == 1 ? " " + sec[i].count : "");
        return !powered() ? "NO 12 V - all sections DISTURBED" : b.length() == 0 ? "no sections set up (wrench a head onto a section window)" : b.toString();
    }

    @Override public int wiredLamp() { if (!powered()) return 3; for (Section s : sec) if (s.state == 2) return 3; for (Section s : sec) if (s.state == 1 || s.state == 3) return 2; return 1; }

    @Override
    public void gearInfo(Map<String, Object> m) {
        for (int i = 0; i < N; i++) if (sec[i].state != 4) { m.put("section" + (i + 1), STATE[sec[i].state]); m.put("count" + (i + 1), sec[i].count); }
    }

    /** section i is safe to run into */
    public boolean clear(int i) { return powered() && sec[i].state == 0; }

    public String sectionLine(int i) {
        Section s = sec[i];
        return "Axle section " + (i + 1) + " (" + pos.getX() + "," + pos.getZ() + ") " + STATE[s.state] + (s.state == 1 || s.state == 3 ? " count " + s.count : "") + (s.state == 2 ? " - " + s.why : "");
    }

    @Override
    protected void onPowerChange(boolean on) {
        if (!on) for (int i = 0; i < N; i++) if (sec[i].state != 4) disturb(i, "evaluator lost its 12 V supply");
    }

    @Override
    protected void serverTick() {
        if (!powered()) { wasPowered = false; render(); return; }
        if (!wasPowered) { wasPowered = true; for (int i = 0; i < N; i++) if (sec[i].state != 4 && sec[i].state != 2) disturb(i, "evaluator restarted after a power loss"); }
        if (age % 2 == 0) for (int i = 0; i < N; i++) evaluate(i);
        if (buzzer && age % 30 == 0) world.playSound(null, pos, com.dogpound.railmap.ModSounds.BUZZER, SoundCategory.BLOCKS, 0.6f, 1.0f);
        render();
    }

    private void evaluate(int i) {
        Section s = sec[i];
        if (s.heads.isEmpty()) { s.state = 4; return; }
        int din = 0, dout = 0;
        String bad = null;
        for (Map.Entry<Long, Integer> e : s.heads.entrySet()) {
            BlockPos hp = BlockPos.fromLong(e.getKey());
            if (!world.isBlockLoaded(hp)) { bad = "head at " + hp.getX() + "," + hp.getZ() + " not loaded (no reports)"; s.seen.remove(e.getKey()); continue; }
            TileEntity t = world.getTileEntity(hp);
            if (!(t instanceof TileAxleHead h)) { bad = "head at " + hp.getX() + "," + hp.getZ() + " is missing"; s.seen.remove(e.getKey()); continue; }
            if (!h.healthy()) { bad = "head at " + hp.getX() + "," + hp.getZ() + " " + h.gearState(); s.seen.remove(e.getKey()); continue; }
            int[] was = s.seen.get(e.getKey());
            if (was == null) { s.seen.put(e.getKey(), new int[]{h.up, h.down}); continue; }   // just (re)connected: baseline, count nothing
            int du = h.up - was[0], dd = h.down - was[1];
            was[0] = h.up; was[1] = h.down;
            if (e.getValue() > 0) { din += du; dout += dd; } else { din += dd; dout += du; }
        }
        s.inTotal += din; s.outTotal += dout;
        s.count += din - dout;
        if (bad != null) {
            if (s.state != 2) disturb(i, bad);
            else s.why = bad;
            return;
        }
        if (s.count < 0 && s.state != 2) { disturb(i, "count went below zero (" + s.count + "): more axles out than in"); return; }
        switch (s.state) {
            case 2:
                if (!FailSafe.safe(world, FailSafe.AXLE_FAULT)) prep(i, "auto prep-reset (fail-safe mode)");
                break;
            case 3:
                if (din > 0) s.swept = true;
                if (s.swept && s.count == 0) {
                    s.state = 0;
                    s.why = "";
                    event(i, "swept clear by a train after the preparatory reset");
                    AlarmManager.get(world).clear(alarmKey(i));
                }
                break;
            case 0: if (s.count > 0) s.state = 1; break;
            case 1: if (s.count == 0) s.state = 0; break;
            default: break;
        }
    }

    private void disturb(int i, String why) {
        Section s = sec[i];
        s.state = 2;
        s.why = why;
        s.swept = false;
        buzzer = true;
        AlarmManager.get(world).raise(world, alarmKey(i), AlarmManager.Cat.SIGNAL, AlarmManager.Sev.MAJOR, "Axle counter section " + (i + 1) + " DISTURBED: " + why, pos, true);
        event(i, "DISTURBED - " + why);
        markDirty();
    }

    private void prep(int i, String by) {
        Section s = sec[i];
        s.state = 3;
        s.count = 0;
        s.swept = false;
        for (Long k : s.heads.keySet()) s.seen.remove(k);                    // baseline afresh
        event(i, "preparatory reset (" + by + ") - waiting for a train to sweep the section");
        markDirty();
    }

    private String alarmKey(int i) { return "axle:" + pos.toLong() + ":" + i; }

    private void event(int i, String text) {
        SafetyData.get(world).history(world, pos, wiredName(), "axle evaluator", "Section " + (i + 1) + ": " + text);
        Recorder.event(world, "AXLE COUNTER " + pos.getX() + "," + pos.getZ() + " section " + (i + 1) + ": " + text);
    }

    private void render() {
        boolean on = powered();
        for (int i = 0; i < N; i++) {
            Section s = sec[i];
            int n = i + 1;
            put("c" + n, s.state == 4 ? 0 : s.count);
            put("clr" + n, on && s.state == 0);
            put("occ" + n, on && s.state == 1);
            put("dis" + n, on && s.state == 2);
            put("prp" + n, on && s.state == 3);
            text("st" + n, s.state == 4 ? "--" : STATE[s.state] + (s.state == 1 ? " " + s.count + " AX" : ""));
        }
        int key = ctl.get("k_reset");
        put("pwr", on);
        put("buzz", on && buzzer);
        put("keyon", on && key != 0);
        text("msg", !on ? "" : key == 1 ? "RESET KEY: PREPARATORY" : key == 2 ? "RESET KEY: DIRECT - LINE MUST BE CLEAR" : buzzer ? "DISTURBANCE - ACK" : "NORMAL");
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (id.equals("test")) return lampTest();
        if (id.equals("k_reset")) return null;                               // default: turn the key
        if (!powered()) return "§cThe evaluator is dead - no 12 V";
        if (id.equals("ack")) { buzzer = false; AlarmManager.get(world).ack(-1, who(who)); return "§eDisturbance buzzer acknowledged"; }
        if (id.startsWith("pr")) return reset(Integer.parseInt(id.substring(2)) - 1, who);
        if (id.startsWith("st") || id.startsWith("c")) {
            int i = Integer.parseInt(id.replaceAll("[^0-9]", "")) - 1;
            if (i >= 0 && i < N) return "§7" + sectionLine(i) + " · " + sec[i].heads.size() + " head(s), " + sec[i].signals.size() + " signal(s) held · in " + sec[i].inTotal + " / out " + sec[i].outTotal;
        }
        return "";
    }

    private String reset(int i, EntityPlayer who) {
        if (i < 0 || i >= N) return "";
        Section s = sec[i];
        int key = ctl.get("k_reset");
        if (s.state == 4) return "§7Section " + (i + 1) + " has no heads";
        if (key == 0) return "§cReset refused - turn the reset key to PREP (supervised) or DIRECT first";
        for (Long k : s.heads.keySet()) {
            TileEntity t = world.isBlockLoaded(BlockPos.fromLong(k)) ? world.getTileEntity(BlockPos.fromLong(k)) : null;
            if (!(t instanceof TileAxleHead h) || !h.healthy()) return "§cReset refused - a head of this section is faulty: repair it first";
        }
        if (key == 1) {
            if (s.state == 0) return "§7Section " + (i + 1) + " is already CLEAR";
            prep(i, "by " + who(who));
            return "§ePreparatory reset on section " + (i + 1) + " - it stays occupied until a train has passed right through it";
        }
        long now = world.getTotalWorldTime();
        if (now - s.directArmed > 100) { s.directArmed = now; return "§6DIRECT RESET section " + (i + 1) + ": only if the line has been examined and is clear - press RESET again within 5 s"; }
        s.directArmed = 0;
        for (Long k : s.heads.keySet()) {
            BlockPos hp = BlockPos.fromLong(k);
            for (AxleWatch.Axle a : AxleWatch.axles(world))
                if (Math.abs(a.x - hp.getX() - 0.5) < 3 && Math.abs(a.z - hp.getZ() - 0.5) < 3) return "§cDirect reset refused - wheels are standing at a head of this section";
        }
        s.state = 0; s.count = 0; s.why = ""; s.swept = false;
        for (Long k : s.heads.keySet()) s.seen.remove(k);
        AlarmManager.get(world).clear(alarmKey(i));
        event(i, "DIRECT RESET by " + who(who) + " (line declared clear)");
        return "§aSection " + (i + 1) + " direct reset - CLEAR";
    }

    @Override
    public String link(String control, BlockPos pick, EntityPlayer who) {
        String num = control.replaceAll("[^0-9]", "");
        if (num.isEmpty()) return "§7Click a section's window (count or status) to link to that section";
        int i = Integer.parseInt(num) - 1;
        if (i < 0 || i >= N) return null;
        Section s = sec[i];
        TileEntity t = world.getTileEntity(pick);
        long k = pick.toLong();
        if (t instanceof TileAxleHead) {
            Integer was = s.heads.get(k);
            String r;
            if (was == null) { s.heads.put(k, 1); r = "§aHead linked to section " + (i + 1) + " - counts IN when moving the way its arrow points (click again to flip)"; }
            else if (was > 0) { s.heads.put(k, -1); r = "§aHead flipped - counts IN when moving against its arrow (click again to unlink)"; }
            else { s.heads.remove(k); r = "§7Head unlinked from section " + (i + 1); }
            s.seen.remove(k);
            if (!s.heads.isEmpty()) disturb(i, "head layout changed - reset needed");
            else { s.state = 4; AlarmManager.get(world).clear(alarmKey(i)); }
            controlsChanged();
            return r;
        }
        if (t instanceof TileSignalMast) {
            if (s.signals.remove(k)) { controlsChanged(); return "§7Signal no longer held by section " + (i + 1); }
            s.signals.add(k);
            controlsChanged();
            return "§aSignal held at STOP whenever section " + (i + 1) + " is not CLEAR";
        }
        return "§7Link an axle counter head or a signal to a section";
    }

    @Override
    protected void save(NBTTagCompound t) {
        NBTTagList l = new NBTTagList();
        for (Section s : sec) {
            NBTTagCompound c = new NBTTagCompound();
            long[] hs = new long[s.heads.size()];
            int[] sg = new int[s.heads.size()];
            int j = 0;
            for (Map.Entry<Long, Integer> e : s.heads.entrySet()) { hs[j] = e.getKey(); sg[j++] = e.getValue(); }
            c.setTag("h", longs(hs));
            c.setIntArray("hs", sg);
            long[] ss = new long[s.signals.size()];
            for (j = 0; j < ss.length; j++) ss[j] = s.signals.get(j);
            c.setTag("sig", longs(ss));
            c.setInteger("n", s.count); c.setByte("st", (byte) s.state); c.setString("why", s.why); c.setBoolean("sw", s.swept);
            c.setInteger("in", s.inTotal); c.setInteger("out", s.outTotal);
            NBTTagCompound seen = new NBTTagCompound();
            for (Map.Entry<Long, int[]> e : s.seen.entrySet()) seen.setIntArray(Long.toString(e.getKey()), e.getValue());
            c.setTag("seen", seen);
            l.appendTag(c);
        }
        t.setTag("sec", l);
        t.setBoolean("buzz", buzzer);
    }

    @Override
    protected void load(NBTTagCompound t) {
        NBTTagList l = t.getTagList("sec", 10);
        for (int i = 0; i < N && i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            Section s = new Section();
            long[] hs = unlongs(c.getTagList("h", 4));
            int[] sg = c.getIntArray("hs");
            for (int j = 0; j < hs.length; j++) s.heads.put(hs[j], j < sg.length ? sg[j] : 1);
            for (long v : unlongs(c.getTagList("sig", 4))) s.signals.add(v);
            s.count = c.getInteger("n"); s.state = c.getByte("st"); s.why = c.getString("why"); s.swept = c.getBoolean("sw");
            s.inTotal = c.getInteger("in"); s.outTotal = c.getInteger("out");
            NBTTagCompound seen = c.getCompoundTag("seen");
            for (String k : seen.getKeySet()) try { s.seen.put(Long.parseLong(k), seen.getIntArray(k)); } catch (NumberFormatException ignored) { }
            if (s.heads.isEmpty()) s.state = 4;
            sec[i] = s;
        }
        buzzer = t.getBoolean("buzz");
    }

    /** the fault lab: remember every section before a test, and put them back afterwards (a test leaves no disturbance) */
    public int[][] snapshot() {
        int[][] o = new int[N][];
        for (int i = 0; i < N; i++) o[i] = new int[]{sec[i].state, sec[i].count, sec[i].swept ? 1 : 0};
        return o;
    }

    public void restore(int[][] o) {
        for (int i = 0; i < N; i++) {
            Section s = sec[i];
            s.state = o[i][0]; s.count = o[i][1]; s.swept = o[i][2] == 1;
            s.seen.clear();
            if (s.state != 2) { s.why = ""; AlarmManager.get(world).clear(alarmKey(i)); }
        }
        buzzer = false;
        wasPowered = true;
        controlsChanged();
    }

    static NBTTagList longs(long[] a) {
        NBTTagList l = new NBTTagList();
        for (long v : a) l.appendTag(new net.minecraft.nbt.NBTTagLong(v));
        return l;
    }

    static long[] unlongs(NBTTagList l) {
        long[] a = new long[l.tagCount()];
        for (int i = 0; i < a.length; i++) a[i] = ((net.minecraft.nbt.NBTTagLong) l.get(i)).getLong();
        return a;
    }
}
