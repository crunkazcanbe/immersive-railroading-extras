package com.dogpound.railmap.safety;

import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.signal.TileSignalMast;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Signal Maintenance Isolation Box (her ChatGPT batch-3 #18): the technician's disconnection box with four slots, each
 * wrench-linked to a signal. Taking a signal out of use for work, and giving it back, follows an explicit procedure:
 * <ol>
 *   <li>REQUEST - a call goes to the signaller (Radio Dispatch Desk / console ANSWER CALL), and through the approval
 *   workflow when an administrator put this box under approval control;</li>
 *   <li>GRANTED once the signaller answered - turn the slot key to ISOL;</li>
 *   <li>ISOLATED - the signal is held at STOP ("ISOLATED for maintenance"), routes from or to it are refused;</li>
 *   <li>RESTORE runs the restoration tests for real, one a second: lamp proving (12 V, not burnt, lamp current),
 *   aspect proving (showing STOP while held, beside its track), detection (the approach re-surveyed), then the
 *   signaller is asked to accept the signal back;</li>
 *   <li>READY once the signaller accepted - only now does the key go back to NORMAL and the signal return to service.</li>
 * </ol>
 * A failed test drops the slot back to ISOLATED with the reason on its screen. The isolation itself is mechanical
 * (a key): with no 12 V the signal stays isolated, only the lamps and the calls go dead.
 */
public class TileIsolationBox extends SafetyTile {
    public static final int N = 4;
    public static final int NORMAL = 0, REQUESTED = 1, GRANTED = 2, ISOLATED = 3, TESTING = 4, AWAIT_ACK = 5, READY = 6;
    public static final String[] STATE = {"NORMAL", "REQUESTED", "GRANTED", "ISOLATED", "TESTING", "AWAIT ACCEPT", "READY"};
    static final String[] TEST = {"LAMP PROVING", "ASPECT PROVING", "DETECTION", "SIGNALLER ACCEPT"};

    static final class Slot {
        long signal, since;
        int state, step, comm;
        String holder = "", why = "";
    }

    final Slot[] slot = new Slot[N];
    { for (int i = 0; i < N; i++) slot[i] = new Slot(); }

    /**
     * The procedure as a pure function (self-tested): the next state for an event, or -1 = refused.
     * Events: req, cancel, answered, key_isol, key_normal, restore, tests_ok, test_fail, accepted.
     */
    static int next(int s, String ev, boolean signaller) {
        switch (ev) {
            case "req": return s == NORMAL ? (signaller ? REQUESTED : GRANTED) : -1;
            case "cancel": return s == REQUESTED || s == GRANTED ? NORMAL : -1;
            case "answered": return s == REQUESTED ? GRANTED : -1;
            case "key_isol": return s == GRANTED ? ISOLATED : -1;
            case "restore": return s == ISOLATED ? TESTING : -1;
            case "test_fail": return s == TESTING || s == AWAIT_ACK ? ISOLATED : -1;
            case "tests_ok": return s == TESTING ? (signaller ? AWAIT_ACK : READY) : -1;
            case "accepted": return s == AWAIT_ACK ? READY : -1;
            case "key_normal": return s == READY ? NORMAL : -1;
            default: return -1;
        }
    }

    /** this signal is out of use (isolated, under test or waiting to be accepted back) */
    public static boolean isolates(Slot s) { return s.signal != 0 && s.state >= ISOLATED; }

    @Override public String wiredName() { return "Signal isolation box"; }

    @Override public String gearState() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < N; i++) if (slot[i].signal != 0) b.append(b.length() == 0 ? "" : " · ").append(i + 1).append(" ").append(STATE[slot[i].state]);
        return b.length() == 0 ? "no signals linked" : b.toString();
    }
    @Override public int wiredLamp() { for (Slot s : slot) if (isolates(s)) return 2; return 1; }

    private TileSignalMast mast(Slot s) {
        if (s.signal == 0) return null;
        BlockPos p = BlockPos.fromLong(s.signal);
        return world.isBlockLoaded(p) && world.getTileEntity(p) instanceof TileSignalMast m ? m : null;
    }

    private String name(Slot s) { TileSignalMast m = mast(s); return m != null ? SignalSighting.name(m) : s.signal == 0 ? "-" : Geo.xz(s.signal); }

    @Override
    protected void serverTick() {
        if (age % 20 != 17) return;
        SafetyData d = SafetyData.get(world);
        for (int i = 0; i < N; i++) {
            Slot s = slot[i];
            if (s.state == REQUESTED && answered(d, s.comm)) { go(s, "answered"); note(s, "isolation of " + name(s) + " granted by the signaller"); }
            if (s.state == AWAIT_ACK && answered(d, s.comm)) { go(s, "accepted"); note(s, "signaller accepted " + name(s) + " back - key to NORMAL"); }
            if (s.state == TESTING) runTest(s);
            String n = String.valueOf(i + 1);
            put("l_req" + n, s.state == REQUESTED);
            put("l_grt" + n, s.state == GRANTED);
            put("l_iso" + n, isolates(s));
            put("l_rdy" + n, s.state == READY);
            for (int k = 0; k < 4; k++) put("t" + (k + 1) + n, s.state == TESTING && s.step == k ? 1 : s.state >= TESTING && s.step > k || s.state == READY ? 1 : 0);
            put("k_iso" + n, s.state >= ISOLATED ? 1 : 0);
            text("sig" + n, s.signal == 0 ? "NOT LINKED" : name(s).toUpperCase(Locale.ROOT));
            text("st" + n, s.signal == 0 ? "" : !s.why.isEmpty() && s.state == ISOLATED ? "FAIL " + s.why.toUpperCase(Locale.ROOT) : STATE[s.state] + (s.state >= ISOLATED ? " " + (world.getTotalWorldTime() - s.since) / 1200 + "MIN" : "") + (s.holder.isEmpty() ? "" : " " + s.holder.toUpperCase(Locale.ROOT)));
        }
        put("pwr", powered());
    }

    private static boolean answered(SafetyData d, int comm) { SafetyData.Comm c = d.comm(comm); return c != null && c.ack == 1; }

    private void go(Slot s, String ev) {
        int n = next(s.state, ev, SigConfig.isolationNeedsSignaller);
        if (n < 0) return;
        if (n == ISOLATED && s.state == GRANTED) s.since = world.getTotalWorldTime();
        s.state = n;
        markDirty();
    }

    private void note(Slot s, String text) {
        SafetyData.get(world).history(world, BlockPos.fromLong(s.signal), name(s), "signal", text);
        Recorder.event(world, "ISOLATION " + text);
    }

    /** one restoration test a second, each against the real signal */
    private void runTest(Slot s) {
        TileSignalMast m = mast(s);
        String fail = null;
        if (m == null) fail = "signal not loaded";
        else switch (s.step) {
            case 0:
                if (!m.term().powered()) fail = "no 12 V at the signal";
                else if (m.term().burnt) fail = "signal burnt out";
                else if (FaultLab.has(world, FaultLab.Kind.LAMP, m.getPos()) || m.lampFailed()) fail = "no lamp current";
                break;
            case 1:
                if (m.governedRail() == null) fail = "signal not beside its track";
                else if (!m.shownAspect().isStop()) fail = "shows " + m.shownAspect().label + " while held";
                break;
            case 2:
                try {
                    SignalSighting.Result r = SignalSighting.survey(world, m);
                    SignalSighting.store(world, r);
                    if (r.approach.isEmpty()) fail = "approach not detected: " + r.obstruction;
                } catch (LinkageError | RuntimeException e) { fail = "survey failed"; }
                break;
            default: break;
        }
        if (fail != null) {
            s.why = TEST[Math.min(3, s.step)] + ": " + fail;
            go(s, "test_fail");
            note(s, "restoration test FAILED for " + name(s) + " - " + s.why);
            world.playSound(null, pos, com.dogpound.railmap.ModSounds.BEEP_ERROR, SoundCategory.BLOCKS, 0.6f, 1.0f);
            return;
        }
        s.step++;
        if (s.step < 3) return;
        go(s, "tests_ok");
        if (s.state == AWAIT_ACK) {
            SafetyData.Comm c = SafetyData.get(world).log(world, "ISOLATION BOX " + pos.getX() + "," + pos.getZ(), "DISPATCH", "Signal " + name(s) + " tested - please ACCEPT it back into use (" + s.holder + ")", 1, 0);
            c.pos = s.signal;
            s.comm = c.id;
        }
        note(s, "restoration tests passed for " + name(s));
        world.playSound(null, pos, com.dogpound.railmap.ModSounds.BEEP_OK, SoundCategory.BLOCKS, 0.6f, 1.0f);
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (id.equals("test")) return lampTest();
        if (id.length() < 2 || !Character.isDigit(id.charAt(id.length() - 1))) return "§7" + gearState();
        int i = id.charAt(id.length() - 1) - '1';
        if (i < 0 || i >= N) return null;
        Slot s = slot[i];
        String act = id.substring(0, id.length() - 1);
        if (s.signal == 0 && !act.startsWith("l_")) return "§7Slot " + (i + 1) + " has no signal (Signal Wrench: pick the signal, then click this slot)";
        String nm = name(s);
        switch (act) {
            case "req": {
                if (s.state == REQUESTED || s.state == GRANTED) { go(s, "cancel"); s.holder = ""; note(s, "isolation request for " + nm + " cancelled by " + who(who)); return "§eRequest cancelled"; }
                if (s.state != NORMAL) return "§c" + nm + " is already " + STATE[s.state];
                if (!powered()) return "§cNo 12 V - the box can't call the signaller";
                String gate = com.dogpound.railmap.admin.Approvals.blocked(world, "sig_isolate", pos, who);
                if (gate != null) return gate;
                s.holder = who(who);
                s.why = "";
                go(s, "req");
                if (s.state == REQUESTED) {
                    SafetyData.Comm c = SafetyData.get(world).log(world, "ISOLATION BOX " + pos.getX() + "," + pos.getZ(), "DISPATCH", "Request to ISOLATE signal " + nm + " for maintenance (" + s.holder + ")", 1, 0);
                    c.pos = s.signal;
                    s.comm = c.id;
                    return "§eIsolation of " + nm + " requested - waiting for the signaller to answer";
                }
                note(s, "isolation of " + nm + " granted (no signaller needed)");
                return "§aGranted - turn the key to ISOL";
            }
            case "k_iso": {
                if (s.state >= ISOLATED) {
                    if (s.state != READY) return "§cRestoration incomplete (" + STATE[s.state] + (s.why.isEmpty() ? "" : " - " + s.why) + ") - press RESTORE and finish the tests first";
                    go(s, "key_normal");
                    note(s, nm + " restored to service by " + who(who) + " after " + (world.getTotalWorldTime() - s.since) / 1200 + " min");
                    if (com.dogpound.railmap.api.QuestEvents.active())
                        com.dogpound.railmap.api.QuestEvents.fire(world, who, pos, "signal_restored", "signal", nm, "minutes", (world.getTotalWorldTime() - s.since) / 1200);
                    s.holder = "";
                    ctl.set(id, 0);
                    controlsChanged();
                    return "§a" + nm + " back in service";
                }
                if (s.state != GRANTED) return "§cThe key is locked - " + (s.state == REQUESTED ? "the signaller hasn't granted the isolation yet" : "REQUEST the isolation first");
                go(s, "key_isol");
                note(s, nm + " ISOLATED for maintenance by " + s.holder);
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.RELAY, SoundCategory.BLOCKS, 0.7f, 1.0f);
                ctl.set(id, 1);
                controlsChanged();
                return "§c" + nm + " ISOLATED - held at STOP, routes over it refused";
            }
            case "rst": {
                if (s.state == TESTING) return "§7Tests running (" + TEST[Math.min(3, s.step)] + ")";
                if (s.state != ISOLATED) return "§cNothing to restore (" + STATE[s.state] + ")";
                if (!powered()) return "§cNo 12 V - the test set is dead";
                s.step = 0;
                s.why = "";
                go(s, "restore");
                return "§eRestoration tests started on " + nm;
            }
            default: return "§7" + nm + ": " + STATE[s.state];
        }
    }

    @Override
    public String link(String control, BlockPos pick, EntityPlayer who) {
        if (!(world.getTileEntity(pick) instanceof TileSignalMast)) return "§7Pick a signal";
        int i = control.isEmpty() ? -1 : control.charAt(control.length() - 1) - '1';
        if (i < 0 || i >= N) return "§7Click a slot (its button, key or screen)";
        Slot s = slot[i];
        if (s.state != NORMAL) return "§cSlot " + (i + 1) + " is " + STATE[s.state] + " - restore it first";
        for (Slot o : slot) if (o != s && o.signal == pick.toLong()) return "§cThat signal is already on another slot";
        s.signal = pick.toLong();
        controlsChanged();
        return "§aSlot " + (i + 1) + " = signal at " + pick.getX() + "," + pick.getZ();
    }

    @Override protected void save(NBTTagCompound t) {
        NBTTagList l = new NBTTagList();
        for (Slot s : slot) {
            NBTTagCompound c = new NBTTagCompound();
            c.setLong("sig", s.signal); c.setLong("since", s.since); c.setInteger("st", s.state); c.setInteger("step", s.step); c.setInteger("comm", s.comm);
            c.setString("who", s.holder); c.setString("why", s.why);
            l.appendTag(c);
        }
        t.setTag("slots", l);
    }

    @Override protected void load(NBTTagCompound t) {
        NBTTagList l = t.getTagList("slots", 10);
        for (int i = 0; i < N && i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            Slot s = slot[i];
            s.signal = c.getLong("sig"); s.since = c.getLong("since"); s.state = c.getInteger("st"); s.step = c.getInteger("step"); s.comm = c.getInteger("comm");
            s.holder = c.getString("who"); s.why = c.getString("why");
        }
    }
}
